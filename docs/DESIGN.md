# Create: Engine Engineer — Architecture Proposal (Milestone 1)

Status: **proposal, awaiting review**. Nothing below is implemented yet beyond the empty mod scaffold.

Targets: Minecraft 1.21.1, NeoForge 21.1.219+, Create 6.0.10 (build `6.0.10-281`), Flywheel 1.0.6, Ponder 1.0.82,
Registrate `MC1.21-1.3.0+67` (the exact versions Create 6.0.10 pins in its own `gradle.properties`).

Claims about Create internals below were checked against the `mc1.21.1-6.0.10` tag of Create's source, specifically
`GeneratingKineticBlockEntity`, `KineticNetwork`, `PoweredShaftBlockEntity`, `SteamEngineVisual`,
`KineticBlockEntityRenderer` and `BlockStressValues`. Anything marked *(verify)* is from memory and must be checked
before relying on it.

---

## 1. The one idea everything hangs on

An engine is a **rotor** (crankshaft + anything rigidly coaxial with it) with **inertia**, acted on by **torque
sources** (cylinders) and **torque sinks** (friction, the Create network's load). Each cylinder is a tiny
**pressure–volume model** whose volume is set by crank angle and whose gas is moved by valves and heated by burning fuel.

The torque a cylinder puts on the crank is just virtual work:

```
τ_cyl(θ) = (p(θ) − p_atm) · dV/dθ
```

That single rule, plus four more, produces every behavior on the wish list without special-case penalties:

| Rule | Consequence that falls out of it |
|---|---|
| **R1 Kinematics.** Piston position, and therefore `V(θ)`, come from slider-crank geometry using the cylinder's phase on the crank. | Phase offsets, firing order and V/boxer/inline layouts all come from geometry. |
| **R2 Sealed gas is polytropic.** With both valves shut, `p·Vⁿ = const` and `T·Vⁿ⁻¹ = const` (n ≈ 1.3). | Compression costs torque and expansion returns it. Valves left open during compression lose the charge. |
| **R3 Open valves relax pressure toward the port's pressure** at a rate set by valve flow × elapsed *time*. | Missing intake gives no charge. A blocked exhaust leaves residual gas and back-pressure. Breathing gets worse at high RPM, so a natural RPM ceiling appears. |
| **R4 Fuel burns only if the charge is hot enough when injected**, after an ignition delay measured in *time*, and only as far as fresh air allows. | Compression ignition needs real compression. Ignition timing drifts later as RPM rises. Rich mixtures waste fuel (black smoke). No air means no burn. |
| **R5 Rotor energy balance.** `½Iω²` changes by (cylinder work − friction work − load work) over each crank-angle step. | RPM emerges from torque against load. Uneven firing makes ω ripple, and a flywheel (more I) smooths it. Overload decelerates the engine into a stall. Too little inertia stalls on the compression stroke. |

What goes wrong for each failure on the wish list, under these rules:

- **Wrong valve timing:** the intake closes late and charge is pushed back out (R2/R3), so compression and power fall.
- **Badly wrong timing:** too little compression for ignition (R4), so the engine won't run.
- **Wrong injection timing:** too early, pressure rises before TDC and fights the piston (negative ∮p dV); too late, there's little expansion left. Efficiency peaks in between.
- **Missing intake:** no gas enters (R3), so there is no burn and only pumping loss.
- **Blocked or missing exhaust:** residual gas displaces fresh charge and the exhaust stroke compresses trapped gas, so power drops or the engine won't run.
- **Insufficient fuel:** no heat release, so torque falls below friction plus load and the engine stalls (R5).
- **More cylinders:** more torque pulses and more injection events, so more output and more fuel.
- **Poor firing intervals:** clumped torque pulses give a large ω ripple. This is measured, not assigned.
- **Flywheel:** a larger I gives smaller ripple and carries the crank through compression.
- **Excessive load:** `τ_load > τ_engine`, so ω falls until stall.

The model is an "air-standard cycle" toy, not thermodynamics: normalized units (`p_atm = 1`, `T_ambient = 1`,
displacement = 1), and a handful of tunable constants per component tier. It is deliberately simple enough to explain
in a Ponder scene.

---

## 2. Create kinetic integration

### 2.1 Units: Create's stress model is already torque × speed

From `KineticNetwork`:

```java
getActualCapacityOf(be) = sources.get(be) * |be.getGeneratedSpeed()|
getActualStressOf(be)   = members.get(be) * |be.getTheoreticalSpeed()|
```

So a block's *per-RPM* stress value is a torque, and SU is power. That gives a clean mapping:

- **Engine torque (internal) → `calculateAddedStressCapacity()`** (SU per RPM), with one calibration constant.
- **Crank RPM = Create RPM, 1:1.** The crank, the shafts coming off it, and the piston animation all agree, and gears
  downstream behave normally. Create's default 256 RPM cap is a hard ceiling. Engines are "slow and heavy" by Minecraft
  standards; that is the right feel for Create.
- **Load torque at the crank = `stress / |speed|`**, where `stress` is the network total our BE receives via
  `updateFromNetwork`. Because every consumer's demand is `impact × its own |speed|`, gearing up downstream
  automatically multiplies the load torque the crank feels. **This is why transmissions will be meaningful later
  with no extra rules.** A gearbox really does trade speed for torque at the crank.

### 2.2 Which block is the Create generator

All crankshaft segments (journals and throws) are `KineticBlockEntity`s, so Create propagates and renders rotation
through them and shafts or gears can attach to the crank's ends. Exactly one segment per engine, the **primary**, is a
`GeneratingKineticBlockEntity` that reports non-zero `getGeneratedSpeed()` and capacity. The primary is elected
deterministically (for example, the lowest position along the axis) at topology rebuild. The primary also hosts the
simulation state and ticks it.

This mirrors Create's own steam engine. `SteamEngineBlockEntity` doesn't generate itself; it drives a
`PoweredShaftBlockEntity` (a `GeneratingKineticBlockEntity`) and calls `updateGeneratedRotation()` when its output
changes.

### 2.3 Speed changes are expensive; quantize them

`GeneratingKineticBlockEntity.applyNewSpeed` does `detachKinetics(); setSpeed(speed); attachKinetics();` on every speed
change, which re-propagates the whole network. The internal ω is continuous, but the published Create speed:

- changes at most once every N ticks (config, default 10),
- only when it differs from the published value by at least `max(1 RPM, 4%)` (hysteresis),
- is an integer RPM.

Capacity updates (`notifyStressCapacityChange` → `KineticNetwork.updateCapacityFor`) are cheap (a sum plus a sync), but
we still rate-limit them to every ~5 ticks and only on meaningful change. Create's steam engine does something similar:
it snaps output to 16/32/48/64 RPM and divides capacity so SU stays constant.

### 2.4 What "capacity" means

If capacity reported the engine's *current* net torque, then at steady state capacity = load exactly, and any
roughness would make the network flicker in and out of overstress. Instead:

> **Reported capacity = net torque the engine could produce at its current RPM with the fuel rack fully open**, estimated
> from the last full cycle (work per unit fuel × max fuel, limited by trapped air).

Consequences:

- Load above what the engine is *currently* fuelled for, but within full-rack capacity: the network keeps running, the
  engine bogs down, and it stalls if the load stays. This is the "excessive load causes RPM to fall" behavior.
- Load above even full-rack capacity: Create overstresses (jams) immediately, and the engine decelerates and stalls.
- A future mechanical governor just moves the rack to hold a target speed. The engine then behaves like a classic Create
  generator with a torque curve.

This is the one deliberate judgement call in the integration; flagging it for review.

### 2.5 Starting, and direction of rotation

No custom starter in M1. **Turn the crankshaft with any Create source**: a Hand Crank, or a water wheel through a
clutch. Engine states:

```
STALLED ──(crank driven ≥ cranking speed)──▶ CRANKING ──(combustion sustains ω)──▶ RUNNING
   ▲                                             │                                   │
   └───────────────(ω → 0 / no fuel / overload)──┴───────────────────────────────────┘
```

- **CRANKING:** the primary reports generated speed 0 and is a normal member driven by the external source. The sim
  takes ω from the network speed. The primary reports compression and friction drag as `calculateStressApplied()`, so a
  big engine genuinely needs a bigger starter than one hand crank. This is emergent, not a rule.
- **RUNNING:** the primary reports its own speed. Create's existing rules (verified in `applyNewSpeed` and
  `setSource`/`reActivateSource`) handle the handover. While the external source is faster, the engine stays
  overpowered. When the external source stops, `reActivateSource` makes the engine the source.
- **Direction** is whatever direction it was cranked in. Real diesels can run backwards. Opposite-direction sources hit
  Create's normal speed-conflict break, which is consistent with Create.

### 2.6 What we explicitly do *not* do

- We don't replace or wrap `KineticNetwork` or `RotationPropagator`.
- We don't push sub-tick torque ripple into Create. Create sees a smoothed speed and capacity. Ripple shows up in
  diagnostics and visuals (and later in wear and noise).

---

## 3. Block and component architecture (M1)

The design is based on component networks, not a fixed multiblock. Every block below is useful in many arrangements;
none of them has a fixed position relative to a "controller".

| # | Component | Form | Role and rules |
|---|---|---|---|
| 1 | **Crankshaft** | block, axis pillar | Main journal and bearing. Carries rotation like a shaft. Shafts and gears may connect at the ends of the crank line. |
| 2 | **Crank Throw** | block, axis pillar + **throw angle** | Offset crank pin. Throw angle is set in 15° steps with Create's in-world value box (scroll with wrench), visible as the pin's rotation. It can only be changed while the crank is stopped. Any of the 4 faces perpendicular to the axis may carry a cylinder, so several cylinders can share one throw (V and boxer layouts). |
| 3 | **Cylinder** | block, directional (faces the throw) | Bore. It needs an installed **Piston** and **Connecting Rod** (items, right-click to install, wrench to remove, both rendered). It must sit directly on a throw face, with its axis perpendicular to the crank axis. |
| 4 | **Cylinder Head** | block, on the cylinder's outer face | Seals the bore. Two side faces are the **intake port** and **exhaust port** (wrench rotates them). In M1 it contains a fixed-profile valve gear and a 2-position **timing mark** (see §5.3). |
| 5 | **Fuel Injector** | block, on the head's outer face | Small fluid tank fed by Create pipes. It injects at a fixed angle before TDC (M1). **Redstone sets the fuel rack**: unpowered = idle rack, 15 = full. |
| 6 | **Intake Manifold** / **Exhaust Manifold** | blocks, pipe-like connectivity | Duct networks. One manifold may serve many cylinders. Two block types so you can't cross-connect them by accident and can read them at a glance. |
| 7 | **Air Intake** | block | Opening of an intake network. It needs a non-solid block in front of its mouth. |
| 8 | **Exhaust Outlet** | block | Opening of an exhaust network. It needs an unobstructed mouth. Smoke particles come out here. |
| — | **Create Flywheel** *(reuse)* | Create's existing block | Adds inertia when it is coaxial with the crank line, connected directly or through plain shafts. *(verify that Create 6's flywheel has no behavior of its own that we'd conflict with)* |
| — | **Create Hand Crank** *(reuse)* | Create's existing block | Starter in M1. |
| — | **Fuel: Plant Oil** | fluid | Compacting seeds and similar in a basin with a Mechanical Press. Both are andesite-age, and Rudolf Diesel's demo engine ran on peanut oil. |

**Why are the piston and rod installed items, not blocks?** Real crank geometry is sub-block. With a 4 px throw radius
(8 px stroke) and a ~13 px rod, a whole slider-crank fits in throw block + cylinder block, which is exactly how Create's
steam engine fits its linkage. Separate rod blocks would force a one-block air gap between crank and bore, make compact
engines impossible, and add no decision for the player. As installed parts they remain inspectable (rendered, listed by
goggles), required (cylinder diagnostics say *"no connecting rod"*), and upgradeable later (material tiers, longer
rods).

A minimal single-cylinder engine (crank axis along X):

```
            [Injector] ← Create pipe from a fuel tank
[Air Intake][  Head  ][Exhaust Outlet]
            [Cylinder]
[Hand Crank]-[Crankshaft][Crank Throw][Crankshaft]-[Shaft]→ load
                                      [Flywheel] (coaxial, optional but helpful)
```

An inline-4 is four throws in a row with cylinders on top, a shared intake manifold along one side of the heads, and a
shared exhaust manifold along the other. A 90° V-twin is one throw with cylinders on its top and side faces. A boxer is
throws with cylinders on top and bottom. None of these are named or detected; they are just what you built.

---

## 4. Engine network representation

### 4.1 Layers

```
World blocks / BEs ──scan──▶ EngineLayout (immutable snapshot) ──build──▶ EngineModel (pure Java sim)
        ▲                                                                       │
        └──────────── KineticCoupling (Create speed/capacity/load) ◀────────────┘
```

- **`EngineLayout`** (topology output, immutable). Holds the crank axis and ordered segments, each throw's angle, and
  each cylinder (position, bank direction, throw ref, installed parts, head ref, injector ref, intake network id,
  exhaust network id). It also holds port networks (openings, member count) and coaxial inertia contributors, plus a
  list of **layout faults** (cylinder not on a throw, cylinder axis parallel to crank, etc.).
- **`EngineModel`** (simulation input and state). Holds `Rotor` (θ over 0–720°, ω, I) and a list of `CylinderModel`
  (phase offset, geometry, `ValveActuator`, `InjectionSource`, `GasState`, `PortConditions`). It is a plain Java object
  with **no Minecraft imports**, so it can be unit tested with JUnit (single cylinder runs, stalls without fuel, ripple
  falls with a flywheel, and so on).
- **`KineticCoupling`**. The only code that talks to Create. It reads load from the primary BE's network stress and
  writes generated speed and capacity with quantization and rate limits.

### 4.2 Phase offsets from geometry

Angles are measured around the crank axis in a fixed right-handed frame.

- Throw angle `φ_t` comes from the block's value box.
- Bank angle `α_c` is the direction from the crank axis to the cylinder: one of four 90° steps, from the face it sits on.
- Top dead centre for cylinder *c* is when the pin points up its bore: `θ + φ_t = α_c`.
- So the cylinder's crank angle is `θ_c = θ + φ_t − α_c`, and its 4-stroke cycle angle is
  `ψ_c = (Θ + φ_t − α_c + 360°·k_c) mod 720°`. Here Θ is the rotor's 720° angle and `k_c ∈ {0,1}` picks which revolution
  is the compression stroke.

`k_c` is what the camshaft physically decides. In M1 it comes from the head's timing mark (§5.3); in M2 it comes from
cam lobe angles. Firing order is *never* a number the player types. It is the sorted set of `ψ_c` offsets, which goggles
display as firing intervals (for example `180° / 180° / 180° / 180°`).

### 4.3 Extensibility seams (kept narrow on purpose)

- `ValveActuator`: lift(ψ) for intake and exhaust. M1 is `FixedValveProfile`; M2 adds `CamDrivenValves`, which reads
  real lobe angles off a camshaft line.
- `InjectionSource`: when and how much fuel. M1 is `FixedInjection`; later an injection pump cam and adjustable timing.
- `IgnitionSource`: M1 is compression ignition (rule R4). Later, spark from a magneto driven off the crank (this keeps
  spark ignition mechanical, with no electricity mod needed).
- Cycle length is a **cylinder** property (720° for four-stroke), not a global constant, so two-strokes (360°, ported)
  are a new `CylinderModel` variant, not a rewrite.
- `Rotor` doesn't know what a cylinder is. It sums torque contributors. A future turbine stage, Wankel rotor or steam
  piston (the same p-V model with a pressure-source valve) is another contributor on the same rotor and the same
  `KineticCoupling`.

That is the whole abstraction budget. There is no generic "prime mover" framework, no component registry and no
plugin API until a second engine type actually exists.

---

## 5. Simulation model

### 5.1 Per tick (server, on the primary BE)

```
Δt = 1/20 s
steps = ceil(|ω|·Δt / 5°)          // fixed max crank-angle step, ~16 sub-steps at 256 RPM
for each sub-step (dθ, dt = dθ/ω):
    for each cylinder:
        advance GasState over [ψ, ψ+dθ]:
            valves open?   → relax n,T toward port state at rate k_flow·lift·dt     (R3)
            sealed?        → polytropic update to new V                              (R2)
            ψ crosses injection angle → queue fuel (from injector accumulator)
            ignition delay (in seconds) elapsed & T_compressed ≥ T_autoignite(fuel) → burn
                burned = min(fuel, freshAir/AFR); T += burned·LHV/(n·c_v)            (R4)
        work_c = p̄·ΔV                                                               (R1)
    ΔKE = Σ work_c·k_torque − (τ_friction(ω) + τ_load)·dθ                            (R5)
    KE += ΔKE; if KE ≤ 0 → ω = 0, STALLED (unless CRANKING); else ω = √(2KE/I)
    Θ += dθ
record per-cycle stats: work per cylinder, fired/misfire reason, peak p, ω_min/ω_max, fuel used
```

Cost: about 16 sub-steps × 12 cylinders × a few dozen flops is negligible. Energy-based integration stays stable at
large crank-angle steps and handles stalling mid-tick naturally. If KE goes negative partway through, the crank stops at
that angle, typically just before a compression TDC, which is exactly where real engines stall.

### 5.2 Constants (per tier, in config, not datapacks yet)

Per component tier: displacement, compression ratio, rod/throw ratio, valve flow coefficient, injector max fuel per
shot, ignition delay (seconds), friction (`c₀ + c₁ω` per cylinder and per journal), inertia per crank segment, and the
torque-to-SU calibration constant. Andesite-age parts get low compression, slow breathing, long ignition delay and high
friction, so they are crude, slow, smoky and inefficient, but they work.

Fuel properties go in a **NeoForge Data Map on `Registries.FLUID`** (`energy_per_mb`, `autoignition_temp`,
`stoich_afr`, and later `octane`). Any fluid from any mod can become a fuel with a datapack, and diesel vs. gasoline
later is just more entries plus an ignition-type check.

### 5.3 M1 valve and injection timing (stand-in for camshafts)

The M1 head uses a fixed valve profile, roughly: intake open 0°→210°, exhaust open 500°→730°, plus a small overlap.
Injection is fixed at ~15° BTDC. The **timing mark** (wrench toggle, rendered as a notch) is `k_c`: whether this
cylinder compresses on the even or odd revolution. It is the stand-in for camshaft phase. With all marks equal, a flat
180° inline-4 fires its cylinders in pairs (rough). Flipping two marks gives even 180° intervals (smooth). That teaches
firing order before camshafts exist. In M2 the mark goes away because the camshaft's lobe angles decide `k_c`, valve
timing and injection timing.

### 5.4 Fuel accounting

The injector holds Create fluid in a `SmartFluidTankBehaviour` tank. Each injection event draws `rack × maxShot`
from a milli-mB accumulator, which pulls whole mB from the tank. Fuel is consumed **per injection event**, so a stalled
engine uses nothing. Injected-but-unburned fuel (misfire, or more fuel than the air can burn) is wasted and shows as
smoke at the exhaust outlet. Starting calibration (tunable): about 0.5 mB per shot, so one crude cylinder at ~64 RPM
uses about 1 bucket per hour.

### 5.5 Intake and exhaust networks (M1 fidelity)

Each port network has openings (Air Intake or Exhaust Outlet) and member cylinders. M1 flow rule: a port's effective
flow coefficient = `base × min(1, openings·capacity / cylinders)`, or 0 if there is no unobstructed opening. That plugs
straight into R3. Manifold pressure dynamics (needed for forced induction and turbos) come later. The port network is
already the object that will hold that state.

Considered and rejected: using Create fluid pipes for air and exhaust. Air isn't a Create fluid, and we only need
topology plus a flow coefficient, not fluid transfer.

---

## 6. Topology discovery and caching

- Every component BE implements `EngineComponent` (role, connection faces, and a transient `EngineHandle` reference).
- **Dirty marking, no polling.** `onPlace`, `onRemove`, `neighborChanged` (only for openings, which care about what is
  in front of them), wrench rotation, value-box change and part install/remove all call
  `EngineTopology.markDirty(level, pos)`.
- **Deferred, deduplicated rebuild.** Dirty positions collect in a per-level set and are resolved once at
  `LevelTickEvent.Post`. Each dirty position finds its crank line (through its cached handle, or a short local search),
  and each affected engine rebuilds **once** no matter how many blocks changed that tick.
- **Bounded BFS** from the crank line: segments along the axis → cylinders on throw faces → heads → injectors and port
  networks (flood through manifold blocks) → coaxial flywheels through plain shafts. There are hard limits in config
  (for example 64 crank segments and 512 components). Hitting an unloaded chunk marks the layout *incomplete*, and the
  engine won't run, with a diagnostic.
- **State survives rebuilds.** Θ, ω and per-cylinder gas state are carried over, keyed by cylinder position, so adding
  a manifold to a running engine doesn't reset it. Topology is never saved; it is rebuilt on chunk load. The primary
  BE's NBT stores only sim state (Θ, ω, run state, accumulators).
- **Two crank lines geared together** are two engines feeding one Create network. Create already sums capacities.
  Proper coupled-crank engines (shared Θ) are a later feature; the layout scanner is where that would go.

---

## 7. Synchronization

- **Speed:** Create already syncs kinetic speed to clients. We add nothing.
- **Per-cylinder status** (fired last cycle, misfire reason, rich/lean flag) is a few bytes on each cylinder BE, sent
  with Create's `SmartBlockEntity.sendData()` **only when it changes**.
- **Engine numbers** for goggles (internal RPM, torque, capacity, load, fuel rate, ripple) are on the primary BE, sent at
  most once per second and only when they change meaningfully.
- **No per-tick packets.** Animation and exhaust timing are derived client-side (§8).

---

## 8. Rendering

The pattern is Create's own `SteamEngineVisual`: an `AbstractBlockEntityVisual` + `SimpleDynamicVisual` that poses
piston, linkage and connector `TransformedInstance`s every frame from the shaft's render angle, using slider-crank math.

- **Crank segments:** Flywheel `RotatingInstance` (Create's `SingleAxisRotatingVisual` style) with our partial models.
  A throw's angle is applied as a static rotation offset. Every segment in an engine must return the **same**
  `getRotationAngleOffset`, otherwise Create's per-position offsets (meant for gear meshing) would twist the crank
  visually. `PoweredShaftBlockEntity` overrides this method for the same reason.
- **Piston + connecting rod:** `CylinderVisual implements SimpleDynamicVisual`. Each frame it computes crank angle with
  the same formula as `KineticBlockEntityRenderer.getAngleForBe` (`renderTime × speed × 0.3 + offset`) plus the cylinder's
  phase, then poses the rod and piston.
- **Exhaust effects:** client-side. When a cylinder's render-derived ψ crosses exhaust-open *and* its synced status says
  it fired, a puff is emitted at that cylinder's exhaust outlet. Black smoke means rich, faint white means misfire. No
  packets are needed, and the puffs line up with the visible pistons.
- **Fallback:** a `KineticBlockEntityRenderer`-style BER for when Flywheel visualization is unavailable (Create does the
  same).
- **Known cosmetic quirk:** Create's angle is `time × speed`, so every published speed change makes the visual angle
  jump. That's acceptable at our quantization. If it looks bad on engines, engine parts can switch to a client-side
  *accumulated* angle shared by all parts of one engine, and only downstream Create shafts would jump.

The server sim's Θ and the client's render angle are **not** the same number, by design. The server is authoritative
for torque and fuel; the client is authoritative for what looks right. The status flags tie them together qualitatively.

---

## 9. Diagnostics

Diagnostics are attached to the component, through Create's `IHaveGoggleInformation` and `CreateLang` builders. Every
fault is a **reason enum produced by the simulation or the layout scan**, never a separate checker.

| Look at… | Shows |
|---|---|
| Any crank segment | Run state (Stalled / Cranking / Running / Overloaded), RPM (internal and published), net torque, capacity (SU at current speed), load torque, inertia, **speed ripple %**, firing intervals, layout faults. |
| Cylinder | Phase offset, cycle position marker, last-cycle result (*fired* / *misfire: no fuel* / *no ignition: compression too low* / *no fresh air* / *exhaust blocked*), work per cycle, peak pressure, missing parts. |
| Injector | Fuel level, fuel type, consumption (mB/min), rack position, *rich: unburned fuel %*. |
| Head / openings | Which port network it's on, whether that network has an opening, flow share, *obstructed*. |

Visible diagnostics come before text: no puff means no fire, black smoke means rich, an idle crank means stalled, and a
red-tinted value means the reading is out of range. Ponder scenes (later) teach the four strokes, phase from throw angle,
and reading the goggles.

---

## 10. Initial progression

| Stage | Unlocks | Materials |
|---|---|---|
| **Andesite age (M1)** | Crankshaft, throw, cylinder, head (fixed timing), mechanical injector, manifolds, intake and outlet, piston, rod. Plant Oil fuel. | Andesite alloy, iron, copper. Basin + press for fuel. |
| Early (M2) | Camshaft, cam lobes, valve assemblies, so real timing control, overtaking fixed heads. Injection pump timing. | Same tier; precision isn't required to *have* cams. |
| Brass | Precision parts: tighter tolerances (lower friction, higher compression), adjustable injection timing, mechanical governor. | Brass, precision mechanism. |
| Later | Refined fuels (gasoline), magneto + spark plugs, higher-compression and high-RPM parts, forced induction (port networks gain pressure), transmissions, other engine types. | — |

The fundamental mechanic is never brass-gated; brass makes engines *better*, not *possible*.

---

## 11. Proposed package structure

```
com.createengines
├── CreateEngines / CreateEnginesClient     mod entry points
├── registry/        CEBlocks, CEBlockEntities, CEItems, CEFluids, CEPartialModels, CEDataMaps, CECreativeTab
├── config/          CEConfig (server calibration constants, client effects)
├── engine/
│   ├── sim/         ★ pure Java, no Minecraft imports, JUnit-tested
│   │                Rotor, EngineModel, CylinderModel, GasState, ValveActuator, FixedValveProfile,
│   │                InjectionSource, FixedInjection, FuelSpec, CycleStats, MisfireReason
│   ├── topology/    EngineComponent, EngineLayout, LayoutScanner, PortNetworkScanner, EngineTopology (dirty set)
│   ├── kinetics/    KineticCoupling (quantization, load torque, capacity, run-state handover)
│   └── diagnostics/ goggle text builders, status enums ↔ lang keys
├── content/
│   ├── crankshaft/  CrankshaftBlock, CrankThrowBlock, CrankshaftBlockEntity (primary hosts sim), visual, renderer
│   ├── cylinder/    CylinderBlock(+BE), CylinderVisual, PistonItem, ConnectingRodItem
│   ├── head/        CylinderHeadBlock(+BE)
│   ├── injector/    FuelInjectorBlock(+BE)
│   ├── airflow/     ManifoldBlock (intake/exhaust variants), AirIntakeBlock, ExhaustOutletBlock
│   └── fuel/        Plant Oil fluid + compacting recipe
└── ponder/          (later)
```

---

## 12. Create and NeoForge abstractions we reuse (not reinvent)

| Need | Reuse |
|---|---|
| Rotation, propagation, stress | `KineticBlockEntity`, `GeneratingKineticBlockEntity` (`getGeneratedSpeed`, `calculateAddedStressCapacity`, `updateGeneratedRotation`, `notifyStressCapacityChange`), `KineticNetwork`, `RotatedPillarKineticBlock` / `IRotate` *(verify exact block base class for axis pillars)* |
| Generator-attached-to-shaft pattern, speed quantization | `PoweredShaftBlockEntity` / `SteamEngineBlockEntity` (as a reference, not a superclass) |
| Starter | Create `HandCrankBlock`, or any Create source |
| Inertia element | Create `FlywheelBlock` |
| BE plumbing, sync, in-world config | `SmartBlockEntity` + `BlockEntityBehaviour`, `ScrollValueBehaviour` (throw angle value box), `SmartFluidTankBehaviour` (injector), `sendData()` |
| Wrench interactions | `IWrenchable` |
| Fuel transport | Create fluid pipes and pumps → injector `IFluidHandler` capability (NeoForge `RegisterCapabilitiesEvent`) |
| Fuel production | Create basin compacting recipe (Mechanical Press) |
| Goggles and tooltips | `IHaveGoggleInformation` (`com.simibubi.create.api.equipment.goggles`), `CreateLang` / `LangBuilder.forGoggles`, `BlockStressValues.RPM` (`GeneratedRpm(…, mayGenerateLess = true)`) for item tooltips |
| Rendering | Flywheel `TransformedInstance`, `SimpleDynamicVisual`, `RotatingInstance` / `SingleAxisRotatingVisual`, `PartialModel`, `AnimationTickHolder`, `KineticBlockEntityRenderer.getAngleForBe` |
| Registration and datagen | `CreateRegistrate` |
| Teaching | Ponder `PonderPlugin` (later) |
| Fuel properties | NeoForge Data Maps |
| Calibration | NeoForge `ModConfigSpec` |

---

## 13. Milestone 1 build order

1. **Sim core + tests** (no Minecraft). A single cylinder cranked at 40 RPM catches and settles; no fuel → stall;
   no intake → no fire; blocked exhaust → weak or no fire; early injection → less work; flywheel → less ripple;
   load > torque → stall; two cylinders at 360° vs 0° offsets → different ripple.
2. **Blocks + topology** with placeholder models: crank, throw, cylinder (with part install), head, injector, manifolds,
   openings. `EngineLayout` and goggle readout of layout faults, with no sim yet.
3. **Kinetic coupling**: CRANKING → RUNNING with a hand crank, capacity and load, quantized speed. *One cylinder makes
   SU.*
4. **Fuel**: Plant Oil, compacting recipe, injector tank, per-event consumption.
5. **Visuals**: crank rotation, piston and rod, exhaust puffs keyed to firing.
6. **Multi-cylinder**: throw angles, bank directions, timing marks, firing-interval readout.
7. **Diagnostics pass** across all components.

M2 starts with camshafts, cam lobes and real valve actuation replacing `FixedValveProfile`.

---

## 14. Decisions I'd like confirmed

1. **Piston and connecting rod as installed items** (rendered, required, upgradeable) rather than blocks. §3 explains why.
2. **Crank RPM = Create RPM, 1:1**, capped by Create's max speed.
3. **Capacity = full-rack available torque** at the current RPM (§2.4), so a partly fuelled, overloaded engine bogs down
   instead of jamming.
4. **Hand crank (or any Create source) as the M1 starter**, with rotation direction set by cranking.
5. **Timing mark on the head** as M1's stand-in for camshaft phase.
6. **Throw angle in 15° steps** through Create's value box (covers 90°, 120° and 180° cranks; not 72° five-cylinder cranks).
7. **Naming:** mod id `create_engines`, package `com.createengines`, display name "Create: Engines". These are
   placeholders and cheap to change now, expensive later.
