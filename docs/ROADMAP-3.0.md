# Roadmap: 2.0.1 to 3.0.0

Written 2026-11-21, after v2.0.0 was tagged. It continues [ROADMAP.md](ROADMAP.md) (1.0.1 to 2.0.0), which stays as the record
of how 2.0 was built. This is a plan, not a promise: the order is firm, the contents of each release are the current best
guess, and nothing here has a date. **Draft for the author's review.** The only item the author has asked for by name is the
dual-mass flywheel; everything else is proposed, and each proposal says what needs the author's decision.

## Direction

- **2.x is additive, like 1.x was.** [COMPATIBILITY.md](COMPATIBILITY.md) applies again with the 2.0 baseline as the floor: no
  removed or renamed IDs, keys or saved data, and a factory built on 2.0 runs the same on every 2.x. New behaviour comes as a new
  block, item or option that existing builds do not use. Anything that moves existing numbers waits for 3.0.
- **3.0 is the second and last planned breaking release** (see the end). Everything that needs breaking is collected on a list
  as 2.x goes, not discovered at the end.
- **Numbering (the author, 2026-11-21).** A release of bug fixes and config options only, like 2.0.1, gets a patch number. Anything
  that adds a block, item, system or API name gets a minor bump.
- **The 2.0 platform gets used.** Where a feature can be data, an API piece or an addon-sized change, it is built that way first.
- **Debts first.** 2.0 was tagged with five listed exceptions and nine open hands-on boxes (the author resolved them as
  exceptions on 2026-11-21). 2.0.x pays down what can be paid by test, and the rest is looked at by eye before 2.1 piles on.
- The Declined list in [ROADMAP.md](ROADMAP.md) ("Not on this roadmap") stands: no new failure modes or wear in the default game (an opt-in wear mode is allowed, and is all of 2.3), no new fuels, no
  knock outside that mode (approved for it 2026-11-24), cooling blocks, snorkels, turbines or two-strokes, no mixins into Create's trains. Nothing below breaks it.

## Rules every release keeps

Rules 1 to 9 of [ROADMAP.md](ROADMAP.md) carry over unchanged, with "the 1.x promise" read as "the 2.x promise" (API, script
and data-map surfaces are frozen as of 2.0; the API has its own semver, and a new name bumps its minor). In short: every
feature ships whole with new tests, no new punishment, no new tuning dial unless it replaces one, it looks like Create, settings
go through `Tuning`, old worlds keep loading, and [STABILITY.md](STABILITY.md) is signed off before any tag. A red guard is a
question, not an obstacle ([CLAUDE.md](../CLAUDE.md)).

## At a glance

| Version | Working title | What it is | Size |
| --- | --- | --- | --- |
| 2.0.x | Shakedown II | Pay down 2.0's listed exceptions and unseen parts; no features | Small |
| 2.1 | Twin Mass | **Dual-mass flywheel**, the readouts that make a flywheel choice legible, and sharper niches for the existing fuels | Medium to large |
| 2.2 | Proving Ground | Verification debts: goldens, soak, server-from-jar, the step-size finding; **the battery tiers**, then **charge priority, electric injector assist and ignition coils**, with electrolysis to be decided (author, 2026-11-23) | Medium |
| 2.3 | Wear and Tear | **The opt-in wear mode and nothing else** (author, 2026-11-24): pistons, rods, gaskets (stock and three tiers), rotary apex seals and batteries wear, precision parts wear slower, a rod can be thrown (a flag, a misfire, a broken head, with spectacle) and a gasket can blow, light wear is repaired by an automatable, durable Service Kit and the rest by replacing the part, worn parts keep their wear as items, and the Black Box and Display Link report each part and a total; off by default, nothing runs with it off, and gaskets and seals are not required with it off | Medium to large |
| 2.4 | Open Garage | Reach: API 1.2, energy and probe mods, the next Create, a second example addon; **piston rings**, **polluted intake air**, **high- and low-compression piston variants**, **rotor variants (a precision rotor among them) with an exhaust niche**, and **nitrous oxide** (liquid, a vaporiser and a gas in the intake manifolds) (author, 2026-11-23), a **W layout for the Bank Builder** (author, 2026-11-24), then **look, sound and performance** only where the 2.0.x pass found something (all of it was 2.3 until the author moved it, 2026-11-24) | Medium to large |
| 3.0 | Clean Slate | The second breaking cleanup: drop 1.x migrations, regenerate baselines, API 2.0 | Large |

**Shipped so far (tags):** 2.0.0 (2026-11-21), 2.0.1 and 2.0.2 (2026-11-22), 2.1.0 (2026-11-23) and 2.2.0 (2026-11-24). 2.3 is built in the working tree and not yet
released; 2.4 and 3.0 are not started. Each shipped release has its sign-off record in [releases](releases), which lists what the machine gate did not see in a running
game and what the author's Part 3 pass covered; the "built, not seen in game" notes below are the state at the time of building, kept for the record.

2.2, 2.3 and 2.4 are independent of 2.1 and of each other; they can swap places or be cut, and 2.1 does not wait on either.

## 2.0.x Shakedown II

The point is to find out what is wrong with 2.0 before building on it. Nothing here adds a feature; a fix that moves a guarded
number is listed as an exception with the author's name on it, as always.

**Status: shipped as v2.0.1 and v2.0.2 (2026-11-22).** What those two records leave ticked by the author or named as an exception is settled there; anything they list as still
owed stays owed (the unattended soak and the unseen-by-eye items among them).

- **Look at what only tests have seen** (the 3.1 to 3.9 boxes of the v2.0.0 record): the advancement tab layout and that no
  toast plays for carried-over progress, thin air above y 128, the pollution amounts, headphones by ear (`Muffle.GAIN` 0.5 and
  `GAIN_HF` 0.05 are untuned), joined batteries and the lamp strip, the flywheel shaft at every angle, kerosene and methanol, the
  Auto shift profiles, the Replay dial, the turbo blow-off jet. Each either gets ticked by the author or becomes a named fix.
- **Dye tint in all four render paths** (plain, Flywheel, Sodium, contraption), still "to check by eye" since 2.0's state cleanup.
- **Pin the advancement paths** (Exception 2.2): extend `CompatManifestTest` to scan the generated `<track>/<row>` files, so a
  later rename is caught.
- **A real 1.x client config in a real client** (Exception 2.3), once, by hand.
- **The Soot Trap on a running rich engine**: the one pollution GameTest that was never written.
- **Aeronautics client row** (red in the compatibility matrix) and the Create floor: re-try the newest Create against the newest
  Aeronautics and Sable, and move the supported range only if both run (rule 7).
- **Mutation ratchet**: the gate swings by about 3 from run to run; re-measure on a quiet machine and write any improvement in.
- Carry any bug the above finds as a fix release (2.0.2, ...) rather than holding it for 2.1.

**Gate:** the 2.0.1 release record carries these as boxes, and the author ticks the hands-on ones.

## 2.1 Twin Mass

### Dual-mass flywheel (requested by the author)

**Status: shipped in v2.1.0 (tagged 2026-11-23).** Block `create_engines:dual_mass_flywheel`, `DualMass` (the isolation curve, pure),
`EngineSimulation.setIsolatedMass`, scanner and layout (`isolatedInertia`), three config settings (primary 0.45, secondary 1.4,
resonance 48 RPM), model by `tools/generate_models.py`, recipe, Ponder text on the flywheel page, a goggles line, Sable weight.
Tests: `DualMassTest`, a property in `SimPropertyTest`, `DualMassFlywheelModelTest`, the config-extremes case in
`ConfigMatrixTest`, three GameTests. The v2.0.0 fixtures (`engine_v2_0_0_*.snbt`) and their `LegacyWorldGameTests` cases exist.
**Spring-pack dial (2026-11-23, the author; built):** one value box on the flywheel, nine settings, the middle one exactly the numbers above;
`SpringPack` (pure), `CastFlywheelBlockEntity.springPack`, `EngineLayout.isolatedResonanceFactor`, blueprint dial `spring_pack`. Tests:
`SpringPackTest`, `theSpringPackDialMovesTheSecondaryMassAndTheResonance`.
**Not done:** a `pitest` run for the new `engine/sim` code (including `SpringPack`), an in-game look at the model and the dial's value box, and the casing styles. The numbers are a first pass and are the author's to tune.

**What it is.** A second flywheel block that sits on the crank line like the Cast Flywheel but is built as two masses joined by a
spring pack: a *primary* mass bolted to the crank and a *secondary* mass on the output side. In a real car it exists so the
engine can have a light, responsive crank and still deliver a steady torque to the drivetrain. That is the whole design brief:
**a flywheel that smooths what the network sees without slowing the engine down as much as a heavy one does.**

**How it maps onto the simulation.** Today a flywheel is one number, `layout.flywheelInertia()`, added to the crank's inertia
(`EngineController` line 314, `EngineSimulation.inertia`). That one mass does two jobs at once: it slows how fast the engine
revs, and it takes the pulses out of the speed (`predictedRipple = swing / (I * omega^2)`, which `Roughness` turns into a lost
share of the stress rating). A dual-mass part separates the two jobs:

- The **primary** mass joins the crank's inertia like any flywheel. It is what sets rev response, spool and starter load.
- The **secondary** mass does not join the crank. It joins only the ripple the network sees, by a factor that depends on
  speed: near zero in a low band around the spring pack's resonance (so at slow speeds it is just its primary mass, a plain
  flywheel), and rising to full above it (so at cruise it smooths like the whole mass). The shape follows how a real isolator
  behaves: no help below the resonance, and growing help above about 1.4 times it.

**Why it is not strictly better than the Cast Flywheel** (rule 4: no dial, no new failure; the balance comes from the part):

- It never does *worse* than a plain flywheel of its primary mass. The weak band is a loss of the benefit, not a penalty, so
  there is no new way to fail.
- At low speed, where a slow diesel lives and where a hand-cranked or stalling engine sits, it is the weaker part. A big cast
  flywheel stays the right part for a slow, heavy, low-revving engine.
- It is the end-game niche (author, 2026-11-23): a mechanical crafter recipe of two Cast Flywheels, a precision mechanism and six brass sheets, the dearest
  crank-line part, so it is the last thing a big fast build adds, not a first upgrade over the Cast Flywheel (recipe to be priced with `RefiningBalanceTest`-style
  numbers), and a primary mass equal to a small flywheel is all the crank has to carry.
- The niche is a fast, few-cylinder or boosted engine that wants quick throttle and turbo response *and* a smooth network. That
  is the build the Cast Flywheel punishes today, and it is a different progression step, not power creep.

**Compatibility.** A new block with a new ID, joined to the 2.x promise on the day it ships. Nothing existing changes: the Cast
Flywheel keeps its inertia and its ripple behaviour, `compat/behaviour.txt` and `compat/trajectories.txt` are not touched (new
entries are appended for engines that use the new part), the manifest gets appended lines, and a 2.0 world loads and runs the
same. A new fixture, `engine_2_1.snbt`, is recorded from the v2.0.0 source.

**What it touches (to check before building):**

- `EngineScanner` and `EngineLayout`: the layout keeps one number today; it needs the primary and secondary inertia and the
  band, so a blueprint, the Bank Builder and a coupled-crank engine (crank lines geared 1:1 are one engine) all carry both.
- `EngineSimulation`: `inertia` (primary) is unchanged in meaning. `predictedRipple` gains the secondary share with the
  speed-dependent factor. **The sim stays free of game imports** (`ArchitectureRulesTest`); the factor is a pure function of
  RPM, like `PortFlow`, so it gets unit tests and a dyno sweep of its own.
- `Roughness`, the stall and idle logic, the governor and the transmission's Auto shift: all read the crank's inertia or the
  ripple, and each needs a test that a dual-mass flywheel does not change them except through those two numbers.
- `EngineMilestones` (the "flywheel" advancement checks `flywheelInertia() > 0`): the new part must count, and no new
  advancement is added for it.
- Goggles, Display Link, Black Box and the engine note: the ripple and the isolation read out; the note's roughness follows the
  ripple the network sees only if it already does today (check, do not change).
- Aeronautics and Sable: it is an ordinary crank-line part and needs no special case, but is added to the structure GameTests.
- Rotary and steam engines: say in the Ponder page and goggles whether each takes it (it should work on any crank line that has
  a flywheel slot today; check `RotorHousing` and the steam engines' own inertia before promising).

**Built like the rest of 2.x:**

- Model and texture generated from Create's own (rule 5): two chunky discs, a ring of springs between them in brass and iron;
  `tools/generate_models.py`, shown in the Ponder page, the inventory icon and the in-world smoke shots. Casing styles follow
  whatever the Cast Flywheel does today (not checked). It uses the same collision as the Cast Flywheel unless the model needs more.
- Config through `Tuning` / `CEConfig` (two inertias and the resonance, defaults that are the shipped numbers), and
  `ConfigMatrixTest` runs them at their extremes.
- Lang, a Ponder page ("Flywheels: one mass or two"), goggle lines (primary and secondary mass, where it is isolating, the ripple
  it removes), Jade and the other integrations through the existing readout, and a Display Link field if the author wants one.
- **Tests (new, unasked as always):** unit tests for the isolation curve (never below the plain flywheel of the primary mass,
  zero secondary equals a Cast Flywheel, rises with speed, finite everywhere including NaN and zero RPM inputs), a property test
  added to `SimPropertyTest`, a dyno sweep that shows where it pays and where the Cast Flywheel wins, GameTests (a running engine
  with the new part is smoother than the same engine with a Cast Flywheel of the same primary mass, revs faster than with one of
  the same total mass, saves and reloads, blueprint round trip, break and replace, chunk unload), a `LegacyWorldGameTests` case
  for the new fixture, and `pitest` re-run because `engine/sim` changes (the ratchet only moves by hand, down).

**Decided by the author, 2026-11-21:**

1. **One block**, with the primary and secondary masses built in. No connected two-block structure.
2. **The numbers come from a dyno sweep** against the Cast Flywheel, shown to the author as a table before they are written in.
3. **The recipe is end-game**: a mechanical crafter recipe (two Cast Flywheels, a precision mechanism, six brass sheets).
4. **Nothing about shifting changes.** It is only a flywheel.

### Around it (small, same release)

- **Timing Tool for rotors: already built (found 2026-11-23, nothing to do).** `TimingToolItem.timeRotors` and `RotorPlanner` have phased
  rotor eccentrics since the rotary engines shipped, and `RotaryEngineTest` pins the angles; the text below was written from a wrong reading.
- **Rotary weight check: done 2026-11-23, already right, no change.** A housing weighs 1.5 (medium) against a cylinder's 2.0 (heavy), and
  fires 0.65 of a cylinder's swept volume a shaft turn against 0.5, so about 1.7 times the firing per unit of mass. No weight moved;
  `SableWeightsTest.aRotorHousingMakesMoreFiringPerKilogramThanACylinder` pins the ratio. No goggles line was added.
- **(Original text, Timing Tool for rotors; stale.)** (author, 2026-11-23). The Timing Tool skips rotor housings, so a multi-rotor engine's eccentrics are
  set by hand (180 degrees for two, 120 for three). Let it phase them the way it phases throws: a new action, so a build that never
  uses it is untouched, and the dials stay editable (no lock, as with tune mode). A test that a twin and a triple-rotor build phased
  by the tool match the by-hand angles `RotaryEngineTest` pins, and that nothing changes without the tool. It also makes bigger
  rotary layouts easy to build, but no new layout is proposed here.
- **Rotary weight check** (author, 2026-11-23; check first). The roadmap says a rotary earns its place in a plane. Compare the
  Rotor Housing's Aeronautics weight with a piston engine of the same power (the 2.0 weights are data). If it is already right, only
  a goggles or tooltip line is needed. If it is not, a change moves existing contraptions' weight, so it comes as a new key with
  today's default, or it is a 3.0 item.
- **Flywheel readout for every flywheel.** The Cast Flywheel's goggles show only its mass today. It gains the ripple it removes
  and what an engine's roughness loss is, so choosing between the two parts is a reading, not a guess. Information only; no
  number moves.
- **Flywheel readout, built 2026-11-23.** An engine with any flywheel on its line (Cast or Dual-Mass) and a running crank gets a
  goggles line from `EngineController.flywheelLine`: the ripple it has, the ripple it would have without the flywheel mass
  (ripple scales as one over the inertia), and the share of the rating that keeps (`Roughness.loss` of the two). Information
  only. Test: `aFlywheelOnARunningEngineReportsWhatItIsWorth`.
- **Roughness on the Display Link, built 2026-11-23.** `engine_smoothness` (`EngineReadout.smoothness`, one minus the goggles'
  rough-running loss), a percent or bar source on every crankshaft block like load and oil. New manifest line
  `reg:displaySource:engine_smoothness`. Test: `displayLinksCanReadAnEngineAndItsPan`.
- **Metering for all heads, built 2026-11-23 (the author's answer: "a toggleable config option that, when on, sets metering for all
  cylinders on an engine when you change the metering via dial for one cylinder").** Not the Timing Tool mode proposed below: server
  setting `features.meterAllHeads` (off by default). With it on, a player's change to one head's or rotor housing's metering dial writes the same
  figure to every other head of that engine (`MeteringFollow`, `EngineController.setAllMetering`); heads held by a MAF sensor or a closed rack are
  skipped, the Timing Tool's tune mode still sets each head separately (it runs with the follow switched off), and no dial is ever locked.
  A flat figure, not a scaled one. Tests: `MeteringFollowGameTests` (on sets the rest and a later change follows, off leaves each head, a
  head of another engine is untouched), the `ConfigMatrixTest` switch. Not done: a held-head GameTest (a head under a MAF sensor), and a look in game.
  Original proposal follows.
- **Metering for all heads at once (proposed 2026-11-22, the author).** An opt-in way to set one metering figure across every
  head of an engine, for builders who do not want to dial each head. **Per-head dials stay the source of truth**: heads differ
  in the air they draw (port networks, throttles at different openings, a turbo on one bank only, precision injectors, ethanol's
  1.5 times injector flow, rotor housings), so a single figure runs some heads rich and others lean, and Tune fuel is right only
  at the speed it was run. Rules for building it: it is a one-time *apply* that writes each head's own dial and leaves every
  dial editable afterwards, never a lock; it skips heads held by a MAF sensor or a closed rack, as `tuneFuel` does; nothing saved
  changes, and an existing engine is never rewritten unless the player applies it (rule 3). **Not decided:** where it lives (a
  Timing Tool mode, a `Tuning` option, or a goggles action) and whether it applies a flat figure or scales each head's current
  value by a share. Tests: applying then reloading keeps per-head values, skipped heads stay untouched, a 2.0 world is unchanged
  until the action is used, and a mixed-fuel or boosted-bank engine ends up with different figures per head where it should.

- **Spring pack animation (2026-11-23, the author; built, unseen in game).** The dual-mass flywheel's secondary disc is drawn a
  little behind the primary through the resonance and falls back into step as RPM rises, so the isolation can be seen. **Render
  only**: no rating changes, no sound, no shudder or other penalty (rule 4), and the sim and the server are untouched.
  Built as `SpringPackLag` (a pure function), `DualMassPose` (finds the crankshaft the flywheel sits on along its shaft and reads
  its synced figures), a visual and a fallback renderer that draw two partial models, `dual_mass_flywheel/primary` and
  `/secondary`, which `tools/generate_models.py` cuts from the one wheel (the secondary is its wheel, stripe, spokes and lugs).
  **What moves it, so it shows from the first turn to the redline:** the load (wind-up of 3 to 8 degrees; a turning engine is
  always at least `PRELOAD` of it), the firing pulses (a swing of up to 8 degrees either side at the firing rate, larger with
  the ripple, 35% of it below the resonance and all of it once the secondary is isolated) and 3 degrees extra passing through the
  resonance: up to 19 degrees behind and 8 ahead. Two earlier versions were invisible: the first moved it 2.5 degrees only
  below three times the resonance; the second took its size from the load and the ripple, which a probe of a running diesel
  showed to be tiny by design (load 0, ripple 0.03: about 1 degree of swing), so it now has floors. The lugs stop short of the
  spring ring (18.3 out against 18.5) so the secondary can swing without a lug passing through a spring.
  Tests: `SpringPackLagTest`, `DualMassSplitModelTest`. The client audit (every model and Flywheel partial loads, 99 Ponder pages)
  passes with it. **Found on the way:** the Dual-Mass Flywheel's model used 90 degree element rotations, which the game refuses
  (it allows 45), so the whole block's model failed to load; the generator now builds the quarter-turned pieces as boxes with x and
  z swapped (`quarter()` in `dual_mass_flywheel()`). Not done: an in-game look at the wheel and the lag.
- **A flywheel defined by data (2026-11-23, the author; moved up from 2.3; built, API 1.2).** An addon makes a block with
  `PartBlocks.flywheel`, registers it with `PartBlocks.register` as for a cylinder, and gives it masses in a new `flywheel` block
  data map (`primary_inertia`, and an optional `secondary_inertia`, each 0 to 100 like the server's flywheel settings; `FlywheelSpec`
  holds the range, `CEDataMaps.FlywheelProperties` the codec). Its model is the block's own, drawn upright and turned with the
  shaft by a new block entity type, `data_flywheel`, which has none of this mod's blocks and takes the addon's. The scanner reads
  the entry the way it reads the two shipped flywheels; a flywheel block with no entry adds nothing. **No block of this mod is
  one**, so nothing built before 2.1 can change (`OwnPartGameTests` checks every block). **A change from the plan above: no
  resonance of its own.** The spring pack's resonance stays the one server setting, `dualMassResonance`; a per-part value needs
  the layout to carry a weighted resonance through every blueprint, coupled crank and test, for a case nobody has asked for.
  The Cast and Dual-Mass flywheels are not data entries. New names: `PartBlocks.flywheel` (`compat/api-surface.txt`), `data
  flywheel primary_inertia` and `secondary_inertia` (`compat/script-surface.txt`), `reg:blockEntity:data_flywheel`
  (`compat/manifest.txt`), `ApiVersion.MINOR` 1 to 2. Docs: ADDONS.md, DATA.md, API.md. Tests: `FlywheelSpecTest`, the surface
  tests, `OwnPartGameTests`, and the example addon's Racing Flywheel with its GameTest, which passes (all 3 of the example's tests) against
  the built jar: an engine with the Racing Flywheel revs up more slowly than a plain one and runs. Not done: the Racing
  Flywheel's model, and the data flywheel turning in the world, have not been seen in game.

### Fuel character (built 2026-11-23, shipped in v2.1.0)

**Status: items 1 to 4 built at the author's say-so and shipped in v2.1.0, with the "Choosing a Fuel" Ponder page.** The author answered in chat: all
four items, and "Change it now (exception)" for biodiesel and for plant oil instead of the opt-in routes proposed below, and "Spills only, on
by default" for fire. So **existing numbers did move**, recorded as Exception 2.9 in [the v2.1.0 record](releases/v2.1.0.md):

- Biodiesel is its own fuel (`FuelSpec.BIODIESEL`: 33 energy, soot 0.5, sulfur 0), where it burned as diesel before.
- Plant oil's cold sensitivity 1.5 to 0.9, soot and sulfur 1 to 1.3. **A fuel-level tolerance for a poor mixture was not built**: the
  simulation has no per-fuel mixture tolerance, and adding one is a larger change than the item named.
- Spills burn by fuel: new `FuelSpec.flammability` (data key `flammability`, default 1), `SpillFire` (pure), `FuelLiquidBlock`, setting
  `fuelFireByVolatility` (on). Gasoline and methanol 2.0, ethanol 1.5, diesel class 0.5. Never touches a tank or a running engine.
- Cold item: the cold readout already existed in the fuel tooltip; it now also says dirty or cleaner exhaust and how a spill burns.
**Ponder page built:** "Choosing a Fuel" (`FuelScenes.choosingAFuel`, on the plant oil, diesel, gasoline, kerosene, ethanol and methanol buckets; text over a
running engine on the rain cap schematic, so no new schematic); the client audit plays it (1179 checks, 0 warn, 0 fail). Not seen by eye.
**Not done:** a Display or goggles fuel comparison beyond the tooltip, a dyno table of the numbers
(they are a first choice for the author to tune), and a look in game. The text below is the original proposal.

### Fuel character (the original proposal)

**Proposed 2026-11-22, in 2.1 because it is the release with player-facing features; the items are the author's pick from a longer list, the details are not yet decided.** This is not new
fuels: the Declined list stands, and so does the author's 2026-11-21 answer (no more fuels beyond methanol, no gases, nitrous,
heavy fuel oil, turbine or two-stroke), **except that the author reversed "no gases, nitrous" on 2026-11-23**: nitrous oxide is
now proposed in 2.4 (see "Nitrous oxide" there). It gives the seven fuels that exist (plant oil, diesel, gasoline, ethanol, biodiesel,
kerosene, methanol) a clearer reason to be picked, the way 1.x gave ethanol its boost niche. The ethanol distillery, which was
also on the list, is not included here.

Two constraints decide how each item can be built, and each item says which one bites:

- **No existing number moves in 2.x.** A factory on 2.0 runs the same, so a fuel's current behaviour cannot be retuned. A new
  behaviour comes as a new optional key in the fuel data map whose default is today's value, or as something that only a new
  block or option uses. Anything that can only work by changing an existing fuel's numbers is a 3.0 candidate (see the end).
- **No new punishment** (rule 4 and the Declined list: no new failure modes or wear). A niche must be a reason to pick a fuel, not
  a new way to lose. Where an item below could read as a penalty, it is flagged and needs the author's decision.

1. **Volatility in the world (fire behaviour).** Spilled or leaking fuel behaves by how volatile it is: gasoline and methanol
   ignite readily and spread, diesel, biodiesel and kerosene are hard to light and safe to store. A first step shipped in 2.0.1
   (`content/fuel/`, `FuelFireGameTests`, switch `compat.fuelFire`, on by the author's choice): all fuels burn alike when lit, so
   the per-fuel difference below is still to do.
   **Needs the author's decision:** a fire that spreads is a new way to lose a build. It only passes rule 4 if it is limited to
   fuel that has already escaped (a spill the player made) and never touches a running, intact engine, and if the config can turn
   it off. Without that it does not ship.
2. **Cold behaviour as an axis.** Uses the `cold_sensitivity` key the fuel data map already has. Kerosene and the alcohols read
   differently in cold biomes: kerosene stays easy to start at altitude and in snow, the alcohols start harder cold and pair with
   the flywheel and tune mode. **Constraint:** the existing fuels' cold values are baselines, so only the new reading (goggles and
   tooltip) and new data keys with today's defaults ship in 2.x; an actual retune of an existing fuel is a 3.0 item.
   **Needs the author's decision:** biodiesel gelling in the cold is a punishment; it is not proposed unless the author asks.
3. **Plant oil as the runs-on-anything fuel.** Plant oil keeps its role as the fuel that needs no processing and can be fed
   straight from a farm, and gains a reason to stay in use after diesel: it is tolerant (a poor mixture or a cold engine does not
   cost it what it costs the others) in exchange for being dirty and low in energy. **Constraint:** plant oil's present numbers
   are guarded, so the tolerance is expressed as new keys defaulting to today's values and enabled by a data-map entry or an
   option, or it waits for 3.0. Not decided: which of those two.
4. **Biodiesel as the clean diesel.** Lower soot and a lower pollution share than diesel, set against its glycerin chain and its
   lower energy. **Constraint:** the same as plant oil; `sootFactor` and the pollution multiplier already exist as data keys, so
   this is likely a data and tooltip change if the current values are not already where the author wants them. Not checked.

**Before building:** read `FuelSpec` and the fuel data map and record, for each of the seven fuels, which of cold, volatility,
soot, pollution and charge cooling are already set to something other than the default. Some of this niche may already exist and
be unlabelled, in which case the work is the goggles and tooltip lines and a Ponder page ("Choosing a fuel"), not new numbers.

**Built like the rest of 2.x:** a fuel comparison in goggles and the fuel tooltip, lang, the Ponder page, config through `Tuning`
for anything with a default, and no new tuning dial unless it replaces one (rule 3). A fuel's numbers are measured by dyno the way
methanol was (`BoostParityTest`-style) and shown to the author as a table before they are written in; `compat/behaviour.txt` and
`compat/trajectories.txt` are not touched, and new entries are appended only for new behaviour.

**Tests (new, unasked as always):** a unit test per new key proving the default reproduces today's numbers exactly for every
existing fuel, data-map parsing and rejection of bad values, a property test that no fuel with the new keys produces a
non-finite or negative result, a `ConfigMatrixTest` row at the extremes, GameTests for each world behaviour (a spill of each fuel
behaves as stated, an intact running engine is unaffected, save and reload, chunk unload), and a `LegacyWorldGameTests` case
showing a 2.0 world with each fuel runs the same. `pitest` is re-run if `engine/sim` changes.

## 2.2 Proving Ground

Mostly verification work that the 2.0 record left open, which changes no behaviour. Each item lands on its own and goes into whichever
release has room.

**Added 2026-11-23 (the author): the battery tiers (Dry Cell and Large Battery).** They were built and then moved out of 2.1. These are new blocks, so they
do not change any existing build; release exception 2.7 (the battery seam) has to be explained before they return, and the tiers need a Ponder page that
explains them (the current scene only names the blocks). 2.1 is the flywheel and roughness release and carries no battery block.

**Dropped 2026-11-23 (the author): the Electric Motor.** It was built (a standalone shaft source drawing from the bank) and then removed before any release: a player with an
electricity mod already has a better motor, and ours drew only on this mod's own batteries. Nothing saved depends on it. The batteries' consumers are the spark plugs
and the starters; the starter's goggles now say when it is spending battery charge (`StarterBlockEntity.isDrawingCharge`), and its Ponder page says the same.
**Starters turn from the batteries (2026-11-24, the author):** on a spark engine with charge in its batteries, a starter with nothing on its input turns the engine over at 48 RPM (`StarterBlockEntity.ELECTRIC_RPM`) when it has a redstone signal, spending charge; a flat bank or a compression engine still needs rotation. A starter that has rotation behaves as before. The Starter, Heavy Starter and battery Ponder pages explain it; GameTests in `BatteryDepthGameTests`.

**Status: the battery tiers shipped in v2.2.0 (tagged 2026-11-24).**
- **Battery tiers back:** `BatteryTier` gains `DRY_CELL(100, 0)` and `LARGE(800, 0.75)`; blocks `dry_cell` and `large_battery` (`BatteryBlock` with a tier, the same join and
  lamp-strip blockstate, 53 models each from `tools/generate_models.py`, recipes: dry cell is zinc ingot, copper sheet and redstone; large is three batteries and a brass casing), weights
  (dry cell medium, large battery heavy), a goggles line (`This one holds N sparks`) on every battery, and a Ponder page "Batteries: Three Sizes" (`BatteryScenes.batterySizes`) that
  explains the tiers (the part that was owed). Tests: `BatteryTierTest` (three new), `BatteryTierBlocksGameTests` (capacity, mixed bank, saved charge, lamps, engine reaches a mixed bank, cold).
- **Guards:** `compat/manifest.txt` appended (the tiers, a starter's `DrawingCharge` key, and 2.1 saved keys that were not yet in it), `compat/behaviour.txt` 67 BIODIESEL lines appended (a 2.1 fuel that had no baseline), no line changed.

- **Goldens** (item 13 of the verification track, set aside until now): the visual set (fixed-camera shots of every casing and part
  model, plus a few Ponder frames, compared per region with a tolerance) and the audio set (synth-level scenarios recording
  loudness, peak, clipping and a coarse spectrum). `./gradlew updateGoldens`; the commit is the approval; no tolerance is ever
  widened to make one pass. The commands go in [SMOKE.md](SMOKE.md) the day they exist, and [CLAUDE.md](../CLAUDE.md)'s "Goldens"
  section is made true.
- **The 60-minute unattended soak** (STABILITY 2.4, Exception 2.4): automate it, so nobody has to run it by hand and the
  exception stops being needed.
- **A server booted from the jar** (verification item 5): needs a NeoForge server install in the build.
- **The step-size finding.** Above about 1500 RPM the per-cycle net torque and fuel rate move with the step (`StepIndependenceTest`).
  Create's limit is 256 RPM so nothing shipped is near it, but a raised limit is the recommendation in the high-RPM notes. Fixing
  the cycle bookkeeping moves guarded numbers only above that speed; if it moves any baseline it is a 3.0 item, otherwise it ships
  here with the diff shown.
- **The config matrix through the client audit's fleet** (item 9) and the **PIT survivors that are not equivalent mutants**
  (killing tests, ratchet down by hand).
- **The ten-break mutation script** (`scripts/smoke-mutations.ps1`) re-run after the leak-trend change that was never checked.

### Battery depth and electric consumers (author, 2026-11-23; charge priority built, the rest proposed)

Chosen by the author from a list of ideas for deeper batteries and more uses for charge besides turning it back into rotation (the
Electric Motor stays dropped, see above). Every item is a new block, item or an option an existing build does not use (rule 1), and
none adds a failure mode (rule 4). Each gets tests, lang entries, a goggle line and a Ponder page. Decisions so far, not yet a design:

- **Charge priority.** A bank can reserve charge for the starter and ignition so a heavy consumer cannot flatten it. The first idea is
  a per-battery priority the player sets by wrench (or a Battery Monitor setting), with the lowest priority drawn from first and the
  reserve left for the starter. It must read the same as today for a bank nobody has set: a bank with no priority drains as it does now.
  The rule goes in `engine/sim/BatteryChemistry` or `BatteryBank` (pure, unit tested), not in a block entity.
  **Built 2026-11-23, shipped in v2.2.0.** Two levels, not a number: a battery is a reserve or it is not. The wrench (no sneak) toggles it
  (`BatteryBlock.onWrenched`; sneaking still picks the battery up) and an action-bar line says which. A reserve is kept for **essential** draws (a spark plug,
  a starter) and is closed to **optional** ones (today only another mod's Forge Energy wire; the electric injector will be one). An essential draw takes from
  the open batteries first and the reserve for what is left; an alternator's charge fills a reserve first. With no reserve set, every draw and fill is the
  even share it always was (`BatteryBank` keeps an allocation-free path for that, since a spark is drawn many times a tick). The rule is
  `engine/sim/ChargePriority` (pure); the saved key is `Reserve`, written only when set, so an untouched battery saves what it always did; goggles say
  how many of the bank are reserves. Tests: `ChargePriorityTest`, `BatteryPriorityGameTests`. **Ponder written 2026-11-24** (a wrench step on "Batteries: Three Sizes"). **Open:** there is
  no visible mark on the block (a lamp or band colour would need new models), and a bank set entirely to reserve gives the starter and plugs what it always did
  but gives another mod's wire nothing.
- **Electric injector assist.** Spending charge improves fuel metering: with power the injector holds its flow more precisely, without
  power it falls back to exactly today's flow, so an engine with no battery near it is unchanged. It is a bonus, never a requirement. Still
  to settle: whether it is a new injector block, an upgrade item for the existing precision injector, or a part that sits beside it;
  what it improves (flow accuracy, a wider lean window, cleaner burn); and the per-tick draw.
- **Ignition upgrades (high-energy coils).** A coil on a spark plug that spends more charge per fire for a stronger spark: a wider
  window for lean and boosted spark fuels, fewer misfires. It is a spark-quality stat and a charge sink, new items only. It has to
  be reconciled with the spark-fuel plug rule (2026-11-23) and must not let a plain plug do less than it does now.
  **Built 2026-11-24, shipped in v2.2.0.** One item, the Ignition Coil, which sits on a head's spark plug (it will not go on
  a head with no plug, and says so) and comes off before the plug. Each spark that has fuel to light draws `IgnitionCoil.COST` (half a
  spark) more from the bank, as an optional use like the injector assist: a reserve battery is closed to it, so a coil can never leave a
  plain spark without its charge. A coiled spark lights a leaner mixture (the lean limit times `LEAN`, 0.7), burns a little faster (`BURN`, 0.9) and loses less of the rich limit
  to a fouled plug (`FOULING`, 0.4), which is the fewer-misfires part; it does not touch pre-ignition, which is the charge's temperature and
  not the spark's. An uncoiled or unpowered spark takes exactly the old code path (the limits are returned unchanged, bit for bit), the
  dyno rates a coil only when the bank can spare the charge, and the scanner counts a coil only with a plug. The rule is `engine/sim/IgnitionCoil`
  (pure); the new block state `coil` defaults false, so no saved head changes. Tests: `IgnitionCoilTest`, `IgnitionCoilGameTests`.
  **Ponder written 2026-11-24** ("Spending Charge: Injector Assist and Ignition Coil", also covers the assist). **Open:** the models are generated but unseen (a coil on the standard and the precision plug, in
  all three head combinations), and rotor housings have no coil (a rotor has no head block to put one on).
- **Battery wear** belongs to the opt-in wear mode in 2.3 (2.3, see below), not here: deep discharge and repeated cold cycles reduce a
  battery's capacity, only with the mode on.

**Electrolysis (author: "could be really cool"; a thought, not a plan).** Splitting water with charge is the biggest of the ideas and
the one that can break rules, so it needs a decision before any design. The tension: the Declined list says no more fuels, and
hydrogen would be one; a battery loop that makes more energy than it spends must be impossible. Ways it could fit, each answering
that:
1. **Power-to-fuel.** An Electrolyser turns water and charge into an input for the existing methanol chain (hydrogen to the column),
   so charge becomes methanol, an existing fuel, and no new fuel exists. Lossy by design, so engine to alternator to electrolyser to
   engine never gains energy. This is the cleanest fit and gives the batteries a real end-game sink. It touches the methanol
   water-feed exception that is still pending.
2. **An additive and not a fuel.** Hydrogen or oxygen fed into the intake as a supplement that widens a spark fuel's lean window or
   cools a boosted charge, in the niche of the ethanol charge-cooling work. No new fuel, but a new fluid and a new intake input.
3. **A pure sink and a showpiece.** The block only turns charge into visible gas and bubbles in a basin, a use for a surplus bank with
   no gameplay output. Cheap and safe, but it gives nothing back.
Open questions for the author: which of the three (or none); whether a fluid is made at all (it would be the first the mod invents
that is not a fuel); the efficiency that keeps any loop a net loss (to be fixed by test, with a `BatteryEnergy`-style balance check);
the draw it asks of the Large Battery; and whether it waits for the energy-mod bridge (Forge Energy) in 2.4, since an electrolyser
fed from another mod's power is the same temptation that got the motor dropped.

## 2.3 Wear and Tear

**The whole of 2.3 (the author, 2026-11-24): the opt-in wear mode, and nothing else.** Everything else this release held (API 1.2,
energy and probe mods, the next Create, the second example addon, piston and rotor variants, nitrous oxide, polluted intake air, the W
layout, look and sound) moved to 2.4 below. **Gaskets stay**, because they are a part that wears; **piston rings go to 2.4** with the
other upgrades, since they never wear and need no mode. Wear is one system with a lot of surface (parts, items, readouts, failures,
saved data), and it has to be tested to the standard of the release record before anything is stacked on it.

**Status (2026-11-24): the core is built and tested, unreleased.** See "Build status" at the end of this section for what exists and what is open.

### The design (author, 2026-11-23; opt-in)

Requested by SilverThorn on Discord ("optional damage and wear and tear", "engine upgrades"); the author reopened the Declined
entry on 2026-11-23 and placed it here. **Opt-in only: a config switch, off by default**, so no existing build, world or number
changes (rule 1). With it off, nothing below runs. Decisions of 2026-11-23 and, where marked, **2026-11-24 (the author, which win
over anything in "Build status" that says otherwise)**:

- **What wears:** pistons, connecting rods and the new gaskets, and on a rotary the **apex seals** (author, 2026-11-23; **in 2.3,
  2026-11-24**): the part rotaries are known for losing, standing in for the piston rings and rod a rotor housing does not have. They
  follow the same rules (opt-in, replaced or lightly repaired by item, soak-tested failure path), and a sealed-out chamber stops like a
  failed cylinder does. **Like gaskets, apex seals are not required with the mode off:** a rotor housing without them runs as it always
  did, and they are only a part, with a stock set standing in, when the mode is on. The rotary's wear curve is measured separately by
  dyno, not copied from the piston's. Wear grows with RPM and faster with heat, no oil and over-revving;
  gaskets also wear with boost (cylinder pressure). Worse wear means worse performance and a failure chance that starts near zero
  and rises with RPM.
- **Precision parts wear slower** (author, 2026-11-24): every precision part (the Forged Piston and the precision parts in the other
  slots) lasts longer than its standard version, by a stated factor tested and pinned like the rest. It is part of what precision is for.
- **Batteries wear too** (author, 2026-11-23; **confirmed and in 2.3, 2026-11-24**): deep discharge and repeated cold cycles slowly
  reduce a battery's capacity, and **each battery wears differently, according to its niche** (the tiers differ in what they are for,
  so what hurts each one differs too; the per-battery rules are still to be written, see "Open"). Same rules as the rest: opt-in,
  nothing runs with the mode off, saved charge is untouched until wear exists, a repair or replacement restores it, and the wear curve
  is tested and then pinned. Not a failure: a worn battery holds less and never stops working.
- **Failures** (author: gaskets can fail too). **A thrown rod** (2026-11-24) sets a **thrown flag** on the cylinder, with the **broken
  rod left inside** (drawn, see Spectacle). The cylinder then **misfires**: it cannot compress or light fuel, so it simply stops
  contributing, with no separate rule. The thrown flag is cleared by a **Service Kit**, which removes the wreck; the kit does not give
  anything back: **the rod and the piston are both lost and cannot be recovered as items**, and a new piston and rod are then fitted.
  **The cylinder head always breaks when a rod does** (author, 2026-11-24): it becomes a broken head, drawn with the rod poking out of it, which the
  kit does not repair; it has to be replaced with a new head, and a gasket fitted under it was lost with it (a gasket is only dropped when taken out on purpose).
  **A blown gasket** stops that cylinder until it is replaced and **leaks particles** for as long as it stays blown.
- **Oil-starvation seizure** (proposed by Claude and taken by the author, 2026-11-24; **built 2026-11-24, not seen in game**): a piston that runs dry
  seizes. A running engine whose oil film stays below 0.15 (the same figure as the goggles' "failing: dry oil" reading, so the readout warns first) counts
  up the seconds a cylinder has run dry; oil brings the count down twice as fast, a stopped engine neither gains nor loses; at **20 seconds** the piston
  seizes, and half way (10 seconds) the engine squeals. A seized cylinder is **locked** (new fault `SEIZED`, appended after `BROKEN_HEAD`; it drags on
  the crank 4 times a plain cylinder, so a small engine with one may stall), keeps all its parts, and its piston is left **at least 0.75 worn**. A
  **Service Kit frees it** but the piston stays as worn as the seizure left it, so it is replaced soon; **taking the piston out** also ends the seizure,
  and a new piston starts new. Deterministic, no roll (the count is the warning), the count is not saved (an engine rebuilt starts clean), the flag is
  saved only while set (`Seized`). All numbers are first-pass (`Wear.SEIZE_*`). Tests: `WearTest` (the count, the threshold, nonsense in, drag), and
  GameTests run dry, run oiled, and freed by a kit or a new piston, each in its own batch because the film override is global.
- **Repair** (author: some damage with items, most by replacing): light wear can be set right with a Service Kit; most damage, and every
  failure, is cured by replacing the part. **The Service Kit has durability** (2026-11-24; the author left its recipe and balance to
  the release) and **must be automatable** (below).
- **Gaskets** in three tiers (cardboard, rubber, copper) applied to a cylinder head, sealing leaks for higher cylinder pressure.
  New items, so new IDs, joined to the 2.x promise on the day they ship. **The stock gasket is an item too** (2026-11-24) and the
  weakest tier. **A fitted gasket seals the head with the wear mode on or off** (the author, 2026-11-24: "we won't have standard gaskets that do
  nothing with wear off"; built the same day, first-pass numbers): a new gasket keeps this much more of each burn's heat than a head with none
  fitted, **stock 1%, cardboard 2.5%, rubber 4%, copper 6%** (`Wear.Gasket.bonus`; a copper gasket is worth a few percent on the dyno, pinned
  between 2% and 12% in `WearEngineTest`), so a better gasket is more power, not just a longer life. With the mode on the bonus fades as the gasket wears and a worn
  one loses power besides; with it off nothing wears, so the bonus is the new gasket's. **Only wear and the requirement belong to the mode:** a head with
  **no gasket fitted runs exactly as it always did** (old builds are untouched, `WearEngineTest` pins it to the last bit), and with the mode on is read as
  holding the stock gasket, which wears but gives no bonus, so turning the mode on never strands an existing engine. Fitting or taking out a gasket rebuilds
  the engine to read it. **A gasket taken out is dropped as an item, and carries its wear and a durability, so it can be
  repaired** with a Service Kit (the same light-wear rule as in the engine) and fitted again. **The Bank Builder builds gaskets too**,
  from what it carries, as it does pistons and rods.
  **Piston rings moved to 2.4** (author, 2026-11-24): up to three, each a small power gain, no wear, and they work with the mode off, so they are not wear.
- **Worn parts carry their wear as items, and the kit can be automated** (author, 2026-11-24): a piston, rod, forged piston, gasket or
  apex seal taken out of an engine keeps its wear on the item (a worn stack shows it as a damage bar; a stack with no wear data reads as
  new, so every existing item stays valid), so a factory can take worn parts out, repair them with the kit, and put them back. That asks
  for three things, each its own test: the cylinder, head and rotor housing accept their parts through an ordinary use of an item on
  the block (so a Create Deployer can refit them), the kit works on a worn part held or lying on a belt or in a basin by a Create recipe
  or Deployer, and the kit's use is spent from its durability the same way each time.
- **Readouts** (author, 2026-11-24): goggles show each part. The **Black Box and the Display Link both report wear**, each part and
  **a single total wear percentage for the whole build** (every wearing part of the engine, batteries included, folded into one
  figure: **a plain mean**, author, 2026-11-24, tested and pinned). A thrown rod and a
  blown gasket are also events in the Black Box's record.

- **Knock** (approved by the author, 2026-11-24; it was on the Declined list): a spark engine whose charge lights early wears its
  pistons faster and can be heard, with the wear mode on only. It never breaks a part by itself. **Built 2026-11-24 (not seen or
  heard in game):** `WearKeeper` adds `Knock.pistonWear` to each piston that fired early (a Forged Piston's longer life applies), the
  cylinder pings on the client from 7% of the burn lost (the pre-ignition knock sound, played high; no sound of its own) and goggles say
  so from above 4%. A rotor has no piston and does not knock. The numbers are in [WEAR.md](WEAR.md). The spark plug and turbocharger
  rules (`PlugWear`, `TurboWear`) are **still not read by any engine**.

### Spectacle when a rod is thrown (author, 2026-11-24: options 1, 2, 3 and 6)

Four effects, all only with the mode on:
1. **Sound sequence:** a loud metallic bang, a clatter, then the sputter of the misfiring cylinder. The bang is vanilla's explosion sound
   today; this is a sound of our own, so it is a new asset (and a new line in the sound audit and the soak test).
2. **A burst of debris:** sparks, oil spray and dark smoke thrown from the cylinder in one burst, then oil dripping from the wreck for as
   long as the flag is set.
3. **The wreck is visible, and the head breaks:** the broken rod is drawn **poking out through the cylinder head**, which is now a
   broken head, so the dead cylinder is plain from across a room. New models for the broken rod and the broken head, and a client audit
   that they draw.
6. **Fire:** a short flame at the cylinder where there is fuel, **tied to the existing `fuelFire` setting**, so a server that turned fire off
   gets none and no block is set alight where fire is off.
Not taken: the flying rod, the crank jolt, and camera shake and a warning.

### Build status (2026-11-24, unreleased, not seen in game)

**Built, with tests.** `features.wearMode` (off by default; `Tuning.wearMode`, `CEConfig.WEAR_MODE`, a `ConfigMatrixTest` switch, a CONFIG.md row,
`tuning:wearMode false` appended to `compat/behaviour.txt`). Everything below does nothing with it off, and an engine that has it off runs
bit for bit as before (`WearEngineTest.aSoundStateIsBitForBitTheEngineWithNoWear`, `WearGameTests`).

- **The rules, pure:** `engine/sim/Wear` (wear per second for a piston, rod and gasket from speed, heat, oil film and boost; what a worn piston and
  gasket cost; the failure chance; the repair rule; the gasket tiers `STOCK`, `CARDBOARD`, `RUBBER`, `COPPER`). `CylinderModel.wear` applies two
  effects: a share of each burn's heat is lost (`Combustion`, a leak past the rings and the gasket) and the cylinder drags harder
  (`frictionStatic`, `frictionViscous`, `frictionAt`). First-pass numbers, the author's to tune, none yet shown as a dyno table: a piston lasts 5 hours
  at 128 RPM warm and oiled, a rod 8, a stock gasket 3; wear is the square of speed (a floor of 2% when idling, capped at 4 times); heat adds 2.5 per
  unit over normal, a dry engine up to 5 times, boost up to 3 per bar over ambient divided by what the gasket holds. A piston at wear 1 keeps 0.8 of its
  burn and drags 1.6 times, a stock gasket keeps 0.7; nothing can fail below 0.5 wear, and with both parts at 0.3 the dyno peak is down about 6%, at 0.5 about 18%, at 0.7 about 34% and spent about 65% (bounds pinned in `WearEngineTest`).
- **Where it lives:** the piston's and rod's wear in `CylinderBlockEntity` (`PistonWear`, `RodWear`), the gasket's tier, wear and blown flag in
  `CylinderHeadBlockEntity` (`Gasket`, `GasketWear`, `Blown`), each saved only once it differs from a part that never wore (a damaged save reads as new;
  `WearGameTests` checks both). The parts own the wear, so it survives the engine being rebuilt. `WearKeeper` (engine) reads it into the models, wears
  it once a second, and rolls for failures; `EngineController.wearTick` calls it, and puts the models back if the mode is switched off.
- **Failures:** a rod worse than half worn can be thrown (`Wear.failureChance`, rising with the cube of the wear past 0.5 and with speed): the cylinder block
  loses its piston **and** rod and keeps a thrown flag, and the head breaks (see "A thrown rod is a flag" below, which replaces the first version of this). Every wear
  change calls `EngineTopology.wearChanged`, which rebuilds the engine around it, because an engine whose layout is unchanged keeps its controller and would never re-read the
  wear faults (the bug behind the first blown-gasket failures). A gasket the same way blows: new fault `CylinderFault.BLOWN_GASKET` (stops the cylinder, appended after `SUBMERGED`), set by the controller from the head's
  flag and not by the scanner, which stays free of block entities. Tests force the roll through `WearKeeper.roller`.
- **What the player does:** a head with no gasket has the **stock** one, the weakest, so turning the mode on never leaves an existing engine without; three new
  items `cardboard_gasket`, `rubber_gasket`, `copper_gasket` are fitted by right-clicking the head and **replace** whatever was there (a blown one is not
  given back, a sound one is), each tier lasting longer and holding more boost; the **Service Kit** (`service_kit`, a flat item) sets light wear (0.35 or
  less) on a piston, rod or gasket back to 0.1 and does nothing for heavy wear or a blown gasket. A new piston or rod starts new (`WearService.partsChanged`).
  Goggles show each part's wear, the gasket tier, and a blown gasket; sneaking says whether a kit would help. Lang, four textures
  (`tools/generate_wear_textures.py`), four recipes, and the manifest lines (recipes, nbt keys, items) are in.
- **Tests:** `WearTest` (curves, bounds, NaN and infinity, tiers), `WearEngineTest` (the dyno), the config matrix, and `WearGameTests` (off changes nothing, on wears,
  a rod is thrown, a gasket blows and stops its cylinder and stays blown when the engine is rebuilt, a new gasket gets it firing again, the kit, replacement
  resets wear, save and load, damaged saves).

**Settled by the author on 2026-11-24, and in places different from what is built above.** What was "decided without the author" there is now
answered: the piston goes with a thrown rod (confirmed, and now neither is recoverable); the stock gasket is an item, required only with the mode on; one
failure each for rod and gasket and none for the piston stays; **the kit is one item for all parts and has durability**. Where the build disagrees with the
design section, the design section is the target and the build is to be changed:

- **A thrown rod is a flag, not missing parts. Built 2026-11-24 (not seen in game).** The cylinder loses its piston and rod for good, keeps a **thrown flag**
  (`CylinderBlockEntity.thrown`, saved only while set) and the head breaks (`CylinderHeadBlockEntity.broken`, saved only while set; the gasket under it is lost).
  Two new faults, `THROWN_ROD` and `BROKEN_HEAD`, appended after `BLOWN_GASKET`, stop the cylinder, so it misfires. `WearKeeper.wearFaults` gathers the three
  wear faults by cylinder for the controller. A Service Kit clears the wreck (and nothing is given back); no piston or rod goes in until it has; a broken head takes
  neither gasket nor kit, drops no head item when broken, and is replaced. Spectacle built: bang, clatter and a second metal sound (vanilla sounds for now, our own
  asset still to do), a burst of sparks, lava drops and oil-black smoke, a flame only with `fuelFire` on and fuel in the head, and from then on the broken head
  smokes and drips on the client; a blown gasket leaks smoke the same way. Open: the visible wreck model (the rod poking out of a broken head) is not drawn yet.
- **Precision slower. Built:** `Wear.PRECISION_LIFE` = 2.5 (first-pass), applied to the Forged Piston (the only precision part that wears; a rod and a gasket have no precision version).
- **The Service Kit has durability. Built:** 24 uses (`WearService.KIT_USES`), spent with `hurtAndBreak` by whoever uses it, a deployer's fake player included.
- **Worn parts keep their wear as item data. Built 2026-11-24 (not seen in game).** A data component `create_engines:wear` (0 to 1, absent on a new part, so every part from
  before the mode is still new) rides on the Engine Piston, Forged Piston, Connecting Rod and every gasket (`WornPartItem`, same ids, a bar for the life left and a
  tooltip line). A part taken out of a cylinder (by hand, or dropped when the cylinder is broken up) comes out as worn as it was, and a worn one fitted goes in as worn
  as it was (`PartInstallation.Dresser`, `WearService.dress` and `partsChanged(..., carried)`). A worn and a new part do not stack together; a part repaired to nothing
  is the same stack as a new one. The Bank Builder only ever uses unworn parts, because it could otherwise hand back a new one for a worn one.
- **A removed gasket is dropped, with its wear, and is repairable. Built.** Fitting a gasket returns the one that was in as an item with its wear (a blown one as spent,
  wear 1); a gasket that is fitted comes out with the head when it is broken up (a broken head's is lost); and **sneaking with a Service Kit on a head takes the gasket
  out** (no use of the kit), leaving the head **without a gasket, which stops its cylinder** (it reuses the blown-gasket fault, now worded "blown or missing") so taking one out
  is never a free new stock gasket. A gasket that is no better than the one in is not swapped for it.
- **The stock gasket is an item. Built:** `stock_gasket` (two paper), the plainest tier. A head that has never been given one is still read as holding the stock one
  (so turning the mode on strands no engine), which is not an item; `CylinderHeadBlockEntity.hasGasketItem` says which, saved only for the stock tier (`StockFitted`).
- **The kit repairs a held part. Built:** a Service Kit with a lightly worn part in the other hand sets one part of the stack right (to 0.1) for one use, used in the air or
  on a block with nothing to mend. **Bank Builder gaskets. Built:** with the mode on it fits the best new gasket it carries to a head with none of its own.
- **Repairing on belts, in basins and on depots. Built 2026-11-24, tested on a real powered Deployer.** A Create Deployer holding a Service Kit sets a lightly worn
  part (a piston, rod or gasket, a single part, wear above 0.1 and up to 0.35) right to 0.1 as it passes on a belt, sits in a basin or rests on a depot, the way it applies a
  casing, and spends one use of the kit's durability. It is not a recipe type of our own and nothing is registered with the recipe manager: Create's
  `DeployerRecipeSearchEvent` lets us hand the Deployer a recipe made on the spot from the very part in front of it (`WearRepairRecipes`), because Create's recipes
  match by item and cannot say "wear between a tenth and a third". Nothing is offered for a new part, a badly worn one, a stack of more than one (one use would
  otherwise repair the lot), or any other tool. The kit is deliberately not marked "kept": Create leaves a kept tool untouched and only spends the durability of one that
  is not. `WearRepairGameTests`: the recipe offered and refused for each case, and a powered Deployer over a depot repairing a piston and spending one use. A depot and a belt
  hand a Deployer an item through the same belt-processing behaviour, so the depot run covers the belt's path, but **no GameTest builds a belt**; a belt run by eye is still
  the author's. A Deployer on a cylinder or head is a use-on-block like a casing and spends the kit the same way (tested with a mock player). **Not built:** a
  machine taking a part out of an engine (a Deployer does not sneak), and the repair showing in JEI or EMI.

**Open, in this order of how much they matter:**
1. **The numbers**: the lives above are guesses and nothing is pinned as final. **The author's table is [WEAR.md](WEAR.md)** (written 2026-11-24 by `WearTableTest` from the real rules, and
   the test fails if the file stops matching them): hours to wear out by speed and conditions for each part, what a gasket is worth new, half worn and spent, power and drag against wear,
   and the chance of a failure in a minute. **What it shows, for the author to judge:** wear has a long, gentle middle and a steep end. A piston at 128 RPM warm lasts 5 hours to wear 1,
   but failure chance in a minute is 7.7% at wear 0.7, 26% at 0.8 and 61% at 0.9, so a part fails somewhere between 0.7 and 0.85 in practice, long before it is spent, and above
   about 190 RPM a part at 0.8 wear is certain to fail within the minute. The part is "at risk" for the last quarter of its life, which is not much warning (the goggles go red from
   0.5 and a Display Link or Black Box shows the build's mean). Whether that is the right shape is the author's call; the curve is `Wear.failureChance` (`FAILURE_RATE`, the cube).
   Also new: the precision parts' wear factor, the Service Kit's durability and cost (**the author left the recipe and balance to the release**, checked
   against the gaskets' and the other precision parts' recipes), and a gasket's durability.
2. **The changes in the list above** (thrown flag and misfire, broken head, dropped gasket, wear on items, kit durability and automation, stock gasket item,
   Bank Builder gaskets, precision slower), each with new tests and, for the thrown flag, the soak test of the failure path.
3. **Battery wear: built 2026-11-24 (first pass, not seen in game).** `engine/sim/BatteryWear` (pure) and `BatteryBlockEntity` (`Wear`, saved only once above 0).
   **Every draw** wears a battery (`BatteryBlockEntity.draw`, which the starter, the plugs and another mod's wire all go through): a full warm discharge from a good charge takes
   `1 / cycles` of its life, **more from a nearly flat battery** (the stress rises evenly from 1 at 20% charge to `deepStress` empty) and **more in the cold**
   (`1 + coldStress` at the coldest; follows `batteryCold`, so a server with the cold off has no cold wear). **A worn battery holds less and never stops working**: at wear 1 it
   holds half (`MAX_FADE` 0.5), and what it held above the new capacity is gone. **Each tier wears for its own reasons** (first-pass numbers, the author's to tune):
   plain 1500 cycles, deep ×3, cold +2 (the all-rounder); dry cell 800 cycles, deep ×4, **cold 0** (made for the cold, but cheap and hates a deep discharge);
   large battery 3000 cycles, deep ×2, cold +3 (lasts longest, but a large bank left outdoors in the snow wears fastest). A Service Kit used on a battery sets light wear
   (0.35 or less) back to 0.1; heavy wear is cured by a new battery. With the mode off, `capacity()` is exactly the tier's, nothing wears and nothing is saved (`WearBatteryRotaryGameTests`).
   Goggles: "Battery wear: N%, holds M% of what it did new". Tests: `BatteryWearTest` (curves, tiers, bounds), `WearBatteryRotaryGameTests` (off, on, deep vs shallow, spent
   holds half and works, kit, save and damaged save, the cold wears a plain battery and not a dry cell). **Open:** a battery does not yet carry its wear as item data (break and
   place it and it is new; the same open piece as pistons, rods and gaskets), no wear while sitting flat (only draws wear), the readouts (Black Box and Display Link)
   and the total, a Ponder page and the tooltip, and the numbers are not measured against play.
4. **Rotary apex seals: built 2026-11-24 (first pass, not seen in game).** `engine/sim/ApexSeals` (pure), `RotorHousingBlockEntity` (`ApexWear`, `ApexFailed`, saved only
   once set) and `WearKeeper` (one set per housing: it wears and rolls for failure once per interval on the housing's first chamber, and all three chambers read the
   same wear). The curve is the rotary's own: a reference shaft speed of 192 RPM (not 128), a life of 4 hours there, **heat 4 per unit over normal** (the piston's is 2.5)
   and **no oil up to 7 times** (the piston's is 5), because the seals live on the oil film. A worn set costs every chamber its burn (`SEAL_LOSS` 0.35 × wear²; leaks do
   not bind, so no extra drag). **On the dyno a rotor keeps 96% of its peak torque at wear 0.3, 90% at 0.5 (where failure starts), 80% at 0.7 and 59% spent**
   (pinned in `ApexSealsTest`). **A failed set** (`CylinderFault.APEX_FAILED`, appended after `SEIZED`) **stops all three chambers** until a new set is fitted: the new
   item `apex_seals` (one precision mechanism, brass and iron sheets), right-clicked on the housing, replaces whatever set is there, worn or failed. A Service Kit sets light wear right.
   **Like gaskets, seals are not required with the mode off:** a housing with none fitted runs as it always did and the stock set is not an item. Goggles show the wear and the
   failure. Tests: `ApexSealsTest`, `WearBatteryRotaryGameTests` (off, on, a spent set fails and stops the rotor, a new set restarts it, kit, save and damaged save).
   **Open:** (the spectacle is built, item 5) the replaced set is not given back (no wear on items yet), no precision variant (a rotor housing has none),
   the trailer line is `later`, the readouts, and the numbers are not measured against play.
5. **Wreck visuals and sounds. Built 2026-11-24; the visuals were seen in the game (`-PsmokeWear`), the sounds were not heard.** Each failure now looks and sounds like itself:
   - **Thrown rod:** `cylinder_head/wreck` is laid over the broken head (a split in the top plate, a flap of it thrown up, the broken rod standing out of the hole leaning, with its
     torn big end and a glowing break, bolts and shards flung about, an oil stain) and `cylinder/wreck` lies in the bore (the rod folded in two, shards of the piston). A broken head
     smoulders and now and then puffs heavy smoke, and a thrown cylinder leaks a little oil smoke.
   - **Blown gasket:** `cylinder_head/blown_gasket` has the gasket squeezed out and drooping from the joint on **all four sides**, with a soot streak up each, so a pipe on one face
     does not hide it (the first version had only a north side and the engine's own air intake covered it). It leaks smoke as before.
   - **Seized piston:** `cylinder/seized_ring` is drawn at the piston's own pose: the skirt blackened, a wide ring of heat colour round the crown, and glowing weld seams; a seized
     cylinder keeps smoking.
   - **Drawing:** one Flywheel instance each on `CylinderVisual` and `CylinderHeadVisual` (laid once when the state changes, not every frame) and the same through the fallback
     renderers, using `EnginePoses.facingFrame` to stand the upright models on whichever way a block faces. Models come from `tools/generate_models.py` (`wear_wrecks`).
   - **Sounds**, synthesised in `tools/generate_sounds.py` and encoded only for these six (every other recording untouched): `rod_thrown1..2` (a flat crack, the clang of the cast
     head splitting, a grind of the wreck, the ring of the casting; 1.7 s), `gasket_blow1..2` (a hard pop and hot gas hissing out, sputtering; 1.3 s) and `piston_seize1..2` (a rising
     dry shriek cut off by a clunk and a low shudder; 1.4 s), registered in `CESounds` and `sounds.json`. The warning squeal half way to seizing is the existing `bearing_squeal`.
     Checked by the numbers (levels, no clipping, the envelope in time) and by `SoundAssetsTest`, not by ear: **they need listening to**.
   - **Failed apex seals. Built 2026-11-24 (not seen or heard in game):** sounds of their own (`apex_fail1..2`: a snap, the loose strip clattering round the chamber, the
     wheeze of blow-by; 1.6 s), a burst of sparks and smoke, `rotor_housing/wreck` (sprung seal strips out between the posts on all four sides, soot on both rims, clear of
     the rotor's sweep, `ApexWreckAssetsTest`), drawn until a new set is fitted, and a housing that keeps smoking. The explosion stand-in is gone. No fire: the author's
     fire was for the thrown rod. `./gradlew runVisualSmokeTest -PsmokeWear` now photographs a failed rotor too.
   - **Not yet:** spark plug and turbo failures have no wreck or sound because they are not wired into an engine (knock breaks nothing, so has no wreck). Open: a first look at the in-game wreck colours (the head's top and the soot are the first guess).
   - **Also open from "seen in game":** the goggles lines, the gasket and kit textures (flat items, first pass), and the four spectacle effects' timing.
6. **Readouts. Built 2026-11-24 (not seen in game).** `EngineSummary` carries a new `wear` figure (the **plain mean** of every part that wears: each fitted piston and rod,
   each head's gasket, each rotor housing's apex seals and each battery, 0 new to 1 spent; a thrown rod's piston and rod, and a blown, missing or broken head's gasket, count
   as spent, and a cylinder with no piston fitted counts nothing for it; -1 and no saved key with the mode off), worked out by `WearKeeper.total` once a second from the
   controller. The **Display Link** has an `engine_wear` source (a percentage or a bar, like oil and smoothness; 0 with the mode off). The **Black Box** keeps it on its tape
   (a new `Wear` array in the tape, the readout, the replay moment and the saved block; an old tape has none and draws no line), shows a **Wear** bar line and the figure in its
   goggles, and a replay line for the build's wear at the moment the dial is on. Tests: `WearTotalTest` (the mean, nonsense, percent), `FlightRecorderWearTest` (the tape, save
   and restore, the wire, old tapes, damaged saves), and GameTests for the summary, the Black Box tape, the Display Link figure, the mode off and the thrown-rod case.
   **Failure events on the Black Box. Built 2026-11-24 (not seen in game):** `EngineSummary` carries `failures`, the number of rods thrown, gaskets blown, pistons seized and sets of apex
   seals failed packed a byte each (`WearFailures`, counted by `WearKeeper.failureCounts` when the engine is built; a thrown rod's broken head is the same failure and not two). The tape notes each
   rise as an event of its own, `ROD_THROWN`, `GASKET_BLOWN`, `PISTON_SEIZED` or `APEX_FAILED` (appended to `FlightRecorder.Cause`, so older tapes read), with the state before and after the same,
   and the goggles show it in red as "N s ago: a connecting rod was thrown". A failure that was already there when a saved tape is loaded is not reported again, and a part put right is no event.
   Tests: `WearFailuresTest`, `FlightRecorderWearTest`, and a GameTest that throws a rod and reads it back from a Black Box. **Not built:** a per-part readout on a Display Link, and seeing it in
   game. Open: the colours of the line (red from half, gold from a third) are a first guess.
7. **Blueprints and schematics. Built 2026-11-24 (schematic priced, blueprint decided).** A **schematic** of a head with a gasket fitted now prices that gasket (`CylinderHeadBlock.getRequiredItems`,
   which reads the block entity: a gasket is block entity data, so Create priced it at nothing), a head with none fitted costs none, and a broken head's gasket is lost with it so it costs none; a
   GameTest pins each. The block entity data a schematic prints still carries the gasket's wear, so a printed head is as worn as the one that was copied, for the price of a new gasket (harmless:
   it is never better than new). A **blueprint** does not record a gasket and a head it rebuilds has none fitted, which is the same as a head nobody ever fitted one to and costs nothing; the Bank
   Builder or a player fits one after. Nothing is carried by a blueprint that was not before. Open: nothing, unless the author wants blueprints to record gaskets too.
7b. **Ponder. Built 2026-11-24 (not seen by eye).** "Wear and Tear" (`WearScenes.wearAndTear`, text over a running engine on the rain cap schematic, on the Service Kit, all four gaskets, the piston,
   the forged piston and the connecting rod): what wears and why, what a fitted gasket is worth with the mode on or off, the readouts, the kit and the Deployer, what a blown gasket and a
   thrown rod leave behind, and seizure. The client audit plays it. **Not done:** a page for batteries and apex seals (the other session's), and item tooltips beyond the gasket's bonus and a
   worn part's percentage.
8. **Answered by the author on 2026-11-24:** (a) a head always breaks when a rod does (above); (c) the total wear percentage is **a plain mean** of every wearing
   part of the build, batteries included, and is pinned by a test; (b) the kit works as a **use-on-item action, and a Create Deployer applies it the way it
   applies casings** (no recipe type of our own): a Deployer holding the kit uses it on a worn part, as it does on a casing, and the same use refits parts
   to cylinders and heads. Nothing is left open here.
9. **The gate, run 2026-11-24 on a fresh copy of the tree (not the release gate: the tree is still moving).** `./gradlew smoke` passed: **599 GameTests**, the client audit **1326 passed, 0 warn,
   0 fail, 1 skip** (the battery-join skip every release has had), and the unit tests. It first failed on two audit warnings, both fixed rather than waved through: the three
   failure sounds were never started in the fleet (they are fired by a mode that is off by default, and a fleet that wore parts out on purpose would fail every "engines are running" check, so
   `SmokeAudit` starts them by hand with the other sounds it cannot rely on, which proves the events and files play on a real sound card; their triggers are tested by `WearGameTests`), and the
   `apex_seals` item had no Ponder page (it is now on "Wear and Tear"). It also failed once on an **out-of-memory error in the GameTest server followed by a hang**, which was **not a code
   fault**: a reused private copy keeps `run/gametest/world`, and `StressGameTests` drops about 600,000 items into it every run, so each run loads the last runs' pile (the same tests in a
   fresh copy pass with a heap peak under 2 GB). The lesson is in [SMOKE.md](SMOKE.md) ("A copy that is reused must have its test world cleared"). **`pitest` failed** on its ratchet:
   168 live mutants against the limit of 129 (v2.2.0 was 124); 13 of the new survivors were in `Wear` and `WearFailures` and have been killed or restructured away (an equivalent boundary check is
   removed, not tolerated), and the rest are in the other session's new classes. The limit is not moved. **Still to run:** `pitest` again once those are down, the Sable and Aeronautics matrix rows
   with the mode on, and the release gate itself. The trailer has `later` lines in `trailer-parts.txt` for the wear items; the changelog, README and release record are not written (they wait
   for the other session's plug, turbo and knock work and for the author's say on the failure curve).

## 2.4 Open Garage

Reach, in the order the author's direction names (other Create addons and pack makers). Everything is optional and compiled
against without being required.

- **API 1.2**, from what the example addon and real addons find missing. The rule stands: an addon never needs an internal class, and
  a need is a gap in the API. Candidates already known: reading and subscribing to an engine's readout and its ripple (so the
  dual-mass flywheel is visible to addons), registering a crank-line part (a flywheel of an addon's own, through data), and a
  display source (the data-defined flywheel moved to 2.1 and is already API 1.2: `PartBlocks.flywheel`). Each is a new name in `compat/api-surface.txt` and `compat/script-surface.txt` and a bump of `ApiVersion.MINOR`.
- **A second example addon** in `examples/`, this time of a part that is not a cylinder (a flywheel or a crank-line part), to prove
  the same route for a second kind of block.
- **Forge Energy input for engines** and **WTHIT / The One Probe**, both listed as "not done" in the 2.0 record. Each gets a row in
  [COMPAT.md](COMPAT.md) and the compatibility matrix, and is called supported only once its row is green.
- **The next Create.** Move the supported range when Create and Aeronautics allow (2.0.x settles whether they do now); each Create
  version in range is built and tested against (rule 7), and if two cannot sit side by side the release says so.
- **Pack tools**, as asked for: check [PACKS.md](PACKS.md) against what pack makers ask for in the feedback channel (the
  `triage-feedback` skill reads it) before choosing anything here. Nothing is built from a guess.

### Piston variants: high and low compression (author, 2026-11-23; proposed)

**Chosen by the author on 2026-11-23, from a list of end-game power ideas, and placed in 2.3 (not 2.1) by the author the same day; moved to 2.4 on 2026-11-24 when 2.3 became the wear release. Nothing is built; the details below are a first design.**
The end-game power path is one chain (air, boost, fuel, oil), so more power today means a longer chain. This adds a choice instead:
two piston variants that trade naturally aspirated (NA) output against boost headroom.

- **High-compression pistons:** more efficiency and NA torque, a smaller pre-ignition margin under boost. Paired with ethanol (which
  resists pre-ignition) this is the end-game NA build.
- **Low-compression pistons:** less NA output, a higher boost ceiling, and gasoline becomes a workable boosted fuel. This is the
  twin-turbo build.
- **The stock piston is today's numbers.** Both variants are new items, so no 2.0 factory changes (rule: no existing number moves
  in 2.x). They are items, not a dial (rule 3).

**What already exists to build on:** pre-ignition is already modelled. `Combustion` loses a share of the burn
(`FuelSpec.preignitionLoss`, capped at `PREIGNITION_MAX_LOSS`) when boost heats the charge past the fuel's autoignition point, and
only on an engine with a plug fitted. Compression ratio most plausibly acts on the same charge temperature (`compressionTemp`), so
the variants shift one existing quantity and add no new failure mode. Not yet checked in the code: where the charge temperature is
computed, and whether a per-cylinder compression factor fits there without touching the guarded baselines.

**Decided by the author on 2026-11-23 (all four proposals below accepted as written); the checks that remain are in the code:**
1. **How a piston is fitted.** Look at how precision parts are fitted today and follow it, so the player learns one gesture.
2. **Compression-ignition engines.** Diesel, kerosene and plant oil are lit by the heat of compression, with a pilot shot for the
   hard-to-light fuels. A compression ratio that changes that heat changes how easily they light. High compression helping them is a
   plain gain; low compression making them harder to light would be a new way to lose, so the proposal is that **low compression has no
   effect on a compression-lit engine's lighting** and only high compression gives it a bonus. Accepted.
3. **Which engines.** Piston engines only. Rotor housings and steam engines have no piston to swap and are left out (rotors get
   their own variants, see "Rotary depth" below).
4. **The numbers.** Measured by dyno and shown to the author as a table before anything is written in (`BoostParityTest` style), as
   methanol and the boost classes were. No number is guessed.

**Built like the rest of 2.x:** goggles show a cylinder's piston and the pre-ignition margin it leaves, the item tooltips state the
trade, lang, a Ponder page that sits next to "Choosing a fuel", config through `Tuning`, a blueprint round-trip (a blueprint of an
engine with variant pistons keeps them; a 2.0 blueprint loads unchanged), and a data-map or API route if parts are data.

**Tests (new, unasked as always):** a unit test that the stock piston reproduces today's numbers exactly for every fuel and class,
one that high compression raises NA output and lowers the pre-ignition margin and low compression does the reverse, a property test that no
combination produces a non-finite or negative result, a `ConfigMatrixTest` row, a GameTest for each variant fitted, saved and reloaded,
and a `LegacyWorldGameTests` case showing a 2.0 world runs the same. `pitest` is re-run, since `engine/sim` changes.

### Nitrous oxide: a liquid, a vaporiser and a gas in the intake (author, 2026-11-23; proposed)

**Requested by the author on 2026-11-23 and placed in 2.3 with the piston variants (both now 2.4), which it depends on for its trade-off. Nothing is built; this is a first design.**
It **reverses the 2026-11-21 answer** quoted under "Fuel character" ("no gases, nitrous"), by the author's own say-so. It is not a fuel (nothing in it burns by
itself; it carries oxygen), so the Declined entry "new fuels" is not what it touches. It is the first fluid the mod invents that is not a fuel, and the first gas
(see the open questions). It is the end-game power item the "high and low compression" list was missing: a short, strong, supplied burst of power instead of a longer chain.

- **Liquid nitrous oxide** is a Create fluid with a bucket, stored in any tank or fluid pipe as the fuels are. Where it comes from is still to settle (see below). It
  does nothing alone: spilled or stored, it is inert and never burns, so it adds no fire and no new way to lose a build.
- **Frost on the tank** (author, 2026-11-23; proposed). A tank holding liquid nitrous oxide grows a layer of frost, and the Vaporiser frosts over while it is
  boiling liquid off, so cold reads at a glance. It is **cosmetic only**: no rating, no gameplay effect, and nothing saved (the look is derived from what the tank holds,
  so a world without the mod loses nothing). **How it is built is the open part,** because the tank is Create's block, not ours: the likeliest route is a client-side
  overlay drawn over a Create fluid tank whose fluid is nitrous (a render hook that reads the tank, with no mixin into Create and no change to its data), and a plain
  frost texture state on our own Vaporiser. **Decided by the author on 2026-11-23:** frost **builds up over a few seconds** (and melts back the same way when the tank is
  emptied), with **frost particles** and a **very subtle crackle**. It **must work with Flywheel and Sodium at the bare minimum** (the plain and contraption paths
  are wanted too, and a path that cannot draw it falls back to nothing and is listed, never left broken). Still to settle: the exact build-up time, the particle
  look, how quiet the crackle is (heard by the author and tuned by ear, on the sound-thread rules that fixed the 1.2.0 out-of-memory bug: capped voices, nothing
  out of earshot), and that none of it costs frames on a large tank bank (a cap, with a client profile before any claim). Tests: the overlay appears only for nitrous,
  never for another fluid, and a tank without the mod's fluid draws exactly as before; the build-up is a pure function of time and contents (unit tested, bounded,
  never non-finite); the client audit and the compatibility matrix rows for Flywheel and Sodium stay green with frost on screen; a soak test of the crackle.
- **The Nitrous Vaporiser** is a new block that takes liquid nitrous oxide on a fluid face and turns it into a gas that flows through the **intake manifolds**
  it is attached to. It sits on an intake network the way the intercooler does: a new inline part, found by `EngineScanner` beside `network.intercoolers()`, which
  gives an `EngineLayout.Inline` and a pure `engine/sim` class (`Nitrous`) for the numbers. The gas is a flow and not a stored quantity: while the vaporiser is
  enabled and has liquid, every cylinder downstream of it on that network breathes nitrous, and a manifold holds nothing (it still never ticks). A redstone signal
  switches it (the one control, as the governor's setpoint is), and it draws liquid only while a cylinder downstream is firing.
- **What the gas does** (a first reading, to be measured): more oxygen per intake stroke lets a cylinder burn more fuel per cycle, so power rises by a factor
  while the injector feeds enough fuel to use it (a lean engine gains little, which keeps it a fuel-system decision). The liquid boiling off also cools the charge,
  the way ethanol's charge cooling and the intercooler already do. Nitrous therefore stacks with boost, and must read the same pre-ignition model the piston variants use:
  **high compression plus nitrous is the aggressive build; low compression is where it is safest.** This is the link to the piston variants.
- **No new punishment (rule 4).** Nitrous only helps. The cost is the liquid it uses up, and the pre-ignition margin it takes from the engine, which is the model
  that already exists (`Combustion` loses a share of the burn, never the engine). Nothing here can break a part; if the opt-in wear mode is on, extra cylinder
  pressure wears the gaskets faster, and with it off nothing wears.
- **Existing builds are untouched (rule 1).** A cylinder with no vaporiser upstream breathes as it does today. `compat/behaviour.txt` and `compat/trajectories.txt`
  are not touched; new entries are appended for the vaporiser only.

**Decisions needed from the author before any design:**
1. **Where liquid nitrous oxide comes from. Decided by the author on 2026-11-23: a basin recipe**, made by the player in the way the refinery makes fuels, and not
   found or looted. Still to settle: the inputs (the first idea is ammonium or nitrate items plus heat, using only items and fluids that already exist, so no new
   ingredient chain is added), the yield, and the cost, which is checked against the refinery numbers so a bucket is not cheaper than the power it gives.
2. **How strong, and for how long.** The power factor, the oxygen it adds, and the draw per second. Measured by dyno and shown to the author as a table first
   (`BoostParityTest` style); none of it is guessed. It must not make boosted engines of the lower classes obsolete (the progression rule in the vision: the end-game
   is reached by a chain, and nitrous is a burst, not a replacement).
3. **One vaporiser or tiers,** and whether the vaporiser also takes **gaseous input from a pipe** for packs with other gas mods. The proposal: one block, liquid only, no
   second dial, and the redstone switch as its only control.
4. **The first non-fuel fluid.** Whether a Ponder page and the goggle line are enough to explain that a fluid exists that no engine burns, or whether it needs
   its own tank readout (the fuel tooltip comparison does not apply).

**Built like the rest of 2.x:** the fluid, the bucket and the vaporiser each get lang entries, a goggle line (what the vaporiser feeds, whether it has liquid, the
power it adds and the pre-ignition margin it uses), recipes, weights, a model from the generator scripts, a Ponder page that sits next to "Choosing a Fuel" and the
piston variants page, config through `Tuning` for anything with a default (and no new dial unless it replaces one), the data-map or API route if parts are data, a
blueprint round-trip (a blueprint with a vaporiser keeps it; a 2.0 blueprint loads unchanged), and casing for the vaporiser if the other intake parts have it.

**Tests (new, unasked as always):** `Nitrous` unit tests (no vaporiser or no liquid reproduces today's numbers exactly for every fuel and class; power rises and the margin falls with gas,
and a lean cylinder gains less than a rich one), a property test that no input produces a non-finite or negative result, a `ConfigMatrixTest` row, GameTests (liquid
in, vaporiser on, a cylinder downstream gains; a cylinder on another network does not; the redstone switch; the liquid runs out and the engine falls back to normal; save and
reload with the gas on; chunk unload), a `LegacyWorldGameTests` case showing a 2.0 world with intake manifolds runs the same, a soak test of the draw on the tick path, and `pitest`
re-run, since `engine/sim` changes. The fluid joins `compat/manifest.txt` when it ships.

### Rotary depth: rotor variants and an exhaust niche (author, 2026-11-23; proposed)

**The author accepted these on 2026-11-23, from a list of rotary ideas; nothing is built.** (Port variants, the first idea on that
list, were not taken. Apex seals are under Wear mode below, the Timing Tool for rotors and the weight check are in 2.1 "Around it",
and the rotary's sound and look are under "Look, sound and performance" at the end of 2.4.) A rotary today is one block with one set of numbers: a Rotor Housing on the crank line,
three ported chambers, spark only, with its rotor fitted by hand and its port timing in `cam_profiles/rotary.json`.

- **Rotor variants.** The counterpart to the piston variants above, and built on the same compression factor so the work is done
  once. A **lightweight rotor** has less inertia: a higher redline and quicker response. A **high-compression rotor** gives more NA
  power and less boost headroom, using the pre-ignition model the pistons use. The stock rotor stays today's numbers. New items, a
  fitted choice and not a dial (rule 3), and pistons' rule on compression-lit engines is moot here: a ported chamber never
  compression-ignites, so a rotary has no compression-lit case to protect.
- **Precision rotor** (author, 2026-11-24; proposed). The rotary's counterpart to the forged piston, so a rotor has a precision grade as
  a cylinder does. A machined rotor with ground apex and side seals: **lighter and with less drag**, the forged piston's two gains
  (`PRECISION_PISTON_INERTIA`, `_STATIC`, `_VISCOUS`), and no change to compression, so it never touches the pre-ignition margin. It is
  fitted the way a forged piston is (the same gesture, rule: one way to fit a part): a new item, a second state property beside
  `HAS_ROTOR` on the housing (as `FINE_INJECTOR` sits beside `HAS_INJECTOR`), a `Slot` in `RotorHousingBlock.PARTS`, and a precision
  bit in `PrecisionParts` that `rotarySpec` reads next to the injector. Built in the same pass as the lightweight and high-compression
  rotors, since all three are one slot with a grade. **Recipe:** one precision mechanism and brass sheets, dearer than the plain rotor
  and cheaper than a precision cylinder, checked against the other precision recipes so a rotary stays below a precision piston engine.
  **Not guessed:** the three scales are measured on the dyno and shown to the author as a table first (`BoostParityTest` style); and
  whether it stacks with the lightweight rotor or replaces it (a housing holds one rotor, so the proposal is that it replaces it:
  the precision rotor is the lightweight rotor's finished form and the two are not both offered, which the author decides). The
  stock rotor stays today's numbers exactly. **Tests:** the stock rotor is unchanged in `RotaryEngineTest`, the precision rotor
  lowers inertia and drag and moves nothing else, the blueprint keeps it, a schematic prices it, goggles name it, and it is
  saved and reloaded.
- **Exhaust energy niche.** A rotary dumps a lot of hot exhaust, so on a rotary a turbo spools faster and a heat exchanger yields
  more. **Constraint:** the existing housing's numbers are guarded, so the niche rides on the new rotor items, or on new
  `part_stats` keys for `create_engines:rotor_housing` whose defaults are today's values. Making every existing housing hotter would
  move existing builds, so it is a 3.0 candidate. The turbo and exchanger numbers are measured by dyno and shown to the author as a
  table first (`BoostParityTest` style); none is guessed.

**Built like the rest of 2.x:** goggles show the fitted rotor and what it changes, item tooltips state the trade, lang, a Ponder
mention on the rotary page, blueprint round-trip for the fitted rotor (a 2.0 blueprint loads unchanged), config through `Tuning`.
**Tests (new, unasked as always):** the stock rotor reproduces today's numbers exactly (`RotaryEngineTest`'s balance pins stay put),
each variant moves the quantity it says and no other, a property test for finite and non-negative results, a `ConfigMatrixTest`
row, a GameTest for each rotor fitted, saved and reloaded, a `LegacyWorldGameTests` case with a 2.0 rotary, and `pitest` re-run
since `engine/sim` changes.

### Polluted intake air (author, 2026-11-23; opt-in)

Suggested by SilverThorn on Discord ("pollution of the realms chunk pollution effecting engine air", marked "not needed but could
be cool"); the author accepted it for 2.3 (now 2.4). Today the Pollution of the Realms compat only writes: engines put carbon and sulfur into
the air and nothing is read back into an engine. This is the other direction. **Opt-in only: a config switch, off by default**, so
no existing build or world changes (rule 1), and nothing is read without the pollution mod installed.

- **The idea:** the carbon and sulfur held by the air an engine breathes lower the quality of its intake, so an engine in a heavily
  polluted area loses some air and so some power, and a clean area behaves as today. The engine's own exhaust can then foul its own
  air in a closed workshop, which is a reason to vent it away or to place an intake in clean air.
- **Reuses the area read** the outlet cap already does (the 7 by 7 by 7 cube, `PollutionProbe`), taken at the intake, not the
  chunk total the pollution mod counts.
- **Still to settle** (author): how much power the worst air costs and what scale of gas counts as "worst" (set against
  `pollutionAreaCap` so one number is not two); whether carbon and sulfur act differently; the goggle line that shows intake quality; and the cost of the probe on the tick
  (read once a second, cached, never every step, with a soak test like the other tick paths). Tests: off means identical to
  today (`compat/behaviour.txt` untouched), the loss is monotonic in pollution and bounded, the mod absent changes nothing.
- **Air filter** (author, 2026-11-23: for Pollution of the Realms packs only). A new intake part that cleans the air an engine
  breathes, so a player can answer polluted air with a part, not only by venting. **It is meant for packs with the pollution mod, so
  it is only obtainable and only does anything there.** Do this without making the block's ID depend on the mod: a block registered
  only when another mod is present vanishes from a world when that mod is removed, which breaks the compatibility promise (no
  removed IDs, saved data kept). So the filter is always registered, but its recipe and its creative-tab entry are gated on the
  pollution mod being loaded (a recipe condition, as other compat items do), and with the mod absent or the intake switch off a
  placed one is a plain inert block that loses nothing. How well it cleans, whether it clogs and is replaced (which would be wear,
  so only with the wear mode on) are still to settle. **Recipe (author):** it uses wool or paper, and charcoal or coal (a filter
  medium and a carbon bed); the rest is copper or copper sheets, as for the other intake parts (author). The exact shape and counts are still to settle, and are
  checked against the other intake recipes so the filter is not cheaper or dearer than they are. Needs tests that it is inert and unobtainable without the
  mod, that the part and its saved data survive the mod being removed, and a Ponder page and goggle line.

### W layout for the Bank Builder (author, 2026-11-24; proposed)

A fourth layout beside inline, vee and flat: **W**, three banks on one crank line (a W12, or a W24 of eight throws with three
cylinders each). Additive: a new `Layout` value appended after `FLAT` (check first how the `Layout` tag is stored, so a builder
saved on 2.x keeps its layout), so no existing build changes (rule 1).

- **Shape.** On the block grid a throw has four faces a quarter turn apart, so the W is three of them in a row: the clicked face
  and the two beside it (bores at 0, 90 and 180 degrees, like a flat with a vee's third bank). `faces` returns three, `camOffsets`
  gives one cam line in each of the two valleys (or one line per bank, decided with the author), and the preview, the missing-parts
  list and the blueprint build all read the same list, so they stay in step.
- **Firing, worked out (the author asked whether a W24 can fire evenly).** Not with every throw carrying all three banks. Fire
  angles are `bore - throw` modulo 360, with the revolution free per cylinder. A 24-cylinder even order needs one firing every 30
  degrees, which is 12 points modulo 360, each used twice. Bores 0, 90 and 180 are multiples of 30, so the lattice is reachable,
  but a throw's three cylinders always land in the same one of three residue classes (the multiples of 90 apart) and take three of
  that class's four points. Each class needs eight cylinders, and eight is not a multiple of three, so eight three-cylinder throws
  cannot do it, whatever the throw angles. The same counting rules out an even W12 on four throws (a 90 degree bank cannot sit on a
  60 degree lattice), which matches the existing "a 90 degree V12 cannot be even" test.
  What does work: **nine throws** with one two-cylinder throw per residue class (a bank left out of three of the throws, which is a
  stepped W, as real staggered W engines are), and a **four-bank X** of six throws with four cylinders each. The planner needs no
  change to find either, since it already searches throw angles and revolutions for any cylinder list; the work is the layout
  and a test per shape.
- **Tests (new with the feature).** `FiringPlannerTest`: the eight-throw W24 is as close as it gets and not `even()` (pin the
  deviation, so a planner change that improves it is noticed), the nine-throw stepped W24 is even, and the X24 is even. Builder
  tests: three faces, the cam offsets, a W built in each facing, blocked and missing parts counted, and a blueprint round trip.
  A Ponder step on the Bank Builder page and the firing-order page saying which W shapes fire evenly and which do not, with the
  arithmetic left to the tooltip.
- **For the author:** whether the W builds the stepped nine-throw form by default (even, but uneven-looking) or the plain
  eight-throw form (tidy, not even), and whether to offer the X at the same time. Default to the stepped form if there is no say.

### Look, sound and performance (was 2.4 Paint and Polish; merged into 2.3 by the author on 2026-11-23, then moved with the rest to 2.4 on 2026-11-24)

Held until 2.0.x has looked at what 2.0 added. Contents are whatever that pass finds, plus these, each only if a profile or an eye
asks for it:

- **Sound.** Retune what the 2.0.x listen finds (headphone filter numbers, methanol, kerosene, the turbo jet), and the voice cap
  and out-of-earshot pause from the optimization list. The 1.2.0 out-of-memory fix is still the standard to hold.
- **Rotary sound and look** (author, 2026-11-23; check first). A rotary should not sound like a piston engine: no valvetrain, a
  smooth rising note at high RPM, a burble at idle, flame at the exhaust. Check what the Rotor Housing already does in the engine
  note, the stack flame and the particles, and build only the gap. The 1.2.0 sound-thread out-of-memory fix is the standard to hold,
  and anything heard is the author's to tune by ear. Cosmetic: no rating changes.
- **Render.** Animation level of detail and batching, only with a client profile that shows a cost (the 2.0 profile found none, so
  nothing is built on speculation). Flywheel off, Sodium and Aeronautics rows stay green.
- **Cosmetics.** More casing variants and dye options in the dress-parts line, and the dual-mass flywheel in each casing style if
  2.1 did not already. Cosmetic parts never change a rating (rule 4).
- **Blueprints.** Whatever the dual-mass flywheel and the 2.x parts show the format missing; a blueprint saved on 2.0 must keep
  loading (the format is part of the promise).

## 3.0 Clean Slate

The one planned breaking release after 2.0. Like 2.0, it is the only place the promise may be broken, so everything lands in one
reviewed change with the author's decision on each line. **Candidates, collected as 2.x goes (none decided):**

- **Drop 1.x migrations.** `PaintMigration`, `ConfigCarryOver`, `AdvancementMigration`, `Gears.fromLegacy` and the 1.x fixtures in
  `compat/world/` exist only to open 1.x worlds. 3.0 can say "open a 2.x world first" and delete them. The author's call: keeping
  `Gears.fromLegacy` was already deemed a decision, not a cleanup.
- **Regenerate the baselines** (`behaviour.txt`, `trajectories.txt`) once, with every balance and step-size change that 2.x had
  to hold back, and list each under Exceptions.
- **API 2.0.** Anything frozen in 2.0 that 2.x showed to be wrong. Prefer a deprecation in 2.x and a removal here.
- **Register cleanup.** Any alias, hidden item or compatibility shim 2.x kept only for 2.0 saves.
- **The Create floor.** Move to whatever Create is current (rule 7) and drop the older range if the two cannot coexist.
- **Fuel retunes held back from 2.1.** Any change to an existing fuel's cold, soot, pollution or tolerance numbers that 2.1 could
  not make additively (plant oil and biodiesel above all), listed under Exceptions with the dyno table.
- **Revisit what stayed on hold:** variable valve timing (only if a dyno sweep shows it pays), the throttle body decision, the basin
  recipes against the refinery numbers, and the dual-mass flywheel's numbers after a year of players.

**3.0 gets a headline feature, not yet chosen** (the author, 2026-11-21: "it should", but none is named yet). It goes here as its
own section once the author picks it, and it is picked early enough that 2.x can lay any groundwork it needs (for example an API
piece or a data format) without breaking anything. Until then 3.0 is only the cleanup above.

## Not on this roadmap

The Declined list in [ROADMAP.md](ROADMAP.md) stands. Nothing new is added to it. Two things to *not* assume from this draft:

- **The dual-mass flywheel is not a clutch.** A part that disengages the crank from the network is a different feature with its own
  consequence (a stalling or slipping drivetrain) and would need the author's say against rule 3. It is offered here only as a
  possible later idea, and only if the transmission work asks for it.
- **One flywheel dial, and only one.** This draft said no value box; the author reversed that on 2026-11-23 and the Dual-Mass
  Flywheel has a spring-pack dial (see [DESIGN.md](DESIGN.md)). It is a single dial that moves the resonance and the secondary
  mass together, so it is a trade and not a free number. No second dial, no clutch.

## Open questions for the author

All questions are settled (2026-11-21): the four flywheel decisions (see 2.1), the order above, a 3.0 headline still to be
chosen (see 3.0), and the numbering rule (see "Direction").
