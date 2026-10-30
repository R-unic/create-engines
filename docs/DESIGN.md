# Create: Engine Engineer — Architecture (Milestone 1)

Status: **Milestone 1 vertical slice implemented.** §0 describes what was built and wins wherever it differs from the
original proposal in §1–§14, which is kept for its reasoning.

Targets: Minecraft 1.21.1, NeoForge 21.1.219+, Create 6.0.10 (build `6.0.10-281`), Flywheel 1.0.6, Ponder 1.0.82,
Registrate `MC1.21-1.3.0+67` (the exact versions Create 6.0.10 pins in its own `gradle.properties`).

Claims about Create internals below were checked against the `mc1.21.1-6.0.10` tag of Create's source, specifically
`GeneratingKineticBlockEntity`, `KineticNetwork`, `PoweredShaftBlockEntity`, `SteamEngineVisual`,
`KineticBlockEntityRenderer` and `BlockStressValues`. Anything marked *(verify)* is from memory and must be checked
before relying on it.

---

## 0. Milestone 1 as built

### What works, and how it was checked

| Behaviour | Verified by |
|---|---|
| Five-rule cylinder model; crank energy balance; starting, fuel starvation, missing intake, blocked exhaust, missing or mis-geared cam, mistiming, overload, flywheel smoothing, firing order, plant oil vs diesel ignition; cylinders that can't burn fuel (no intake, no fuel) never fire and only cost power; oil film building, thinning and slowing a dry engine | 34 JUnit tests (`./gradlew test`) on the pure-Java `engine.sim` package, including the dynamometer rating |
| A single-cylinder engine built from blocks in a real world: the starter cranks it, it fires, runs on its own after release, provides Create stress capacity, keeps its cam at exactly half crank speed with timing on time, burns fuel; without fuel it stalls; removing the exhaust chokes it; the starter loads its input network; two presses within its rating slow it into its torque band and keep turning; three presses overstress the network while the engine keeps running; every item is listed once in the creative tabs | 13 GameTests (`./gradlew runGameTestServer`), including cranks that stop when the starter lets go, misfiring cylinders that stay silent, and oil feeding and being used up |
| Crank, throw, piston, connecting rod, cam lobe and valve stems animate together; exhaust smoke follows the cam; goggle diagnostics; all five Ponder scenes render and play; the crank's drawn angle never jumps beyond what its speed explains, across every speed change while starting | Visual smoke test (`./gradlew runVisualSmokeTest`): builds the engine in a fresh flat world and saves screenshots to `run/screenshots/`, then quits |

### Decisions taken in review

| Question | Decision |
|---|---|
| Pistons and connecting rods | Parts installed into the cylinder (right-click; sneak-right-click with an empty hand removes). Block state properties, so installing one rescans the engine. |
| Crank speed | Crank RPM = Create RPM, 1:1. |
| Stress capacity | Rated torque: 90% of the peak of the engine's dynamometer torque curve at or below its current RPM (see §2.4, revised after play-testing). |
| Starting | A **Starter** block: faces a crankshaft block, takes rotation into its back from any Create source, engages while redstone-powered, and drives the crank through a one-way clutch the engine outruns. It is not kinetically connected to the crank; cranking effort is stress on its own input. A Hand Crank on the crank line also works. |
| Valve timing | **Camshafts are in Milestone 1** (the timing-mark stand-in was dropped). A Camshaft line must turn at half crank speed via any gearing; a Cam Lobe touching a head (or its cylinder block, see *Pushrod cams*) works its valves *and* its unit injector. Lobe angle (7.5° steps, value panel) sets timing and which crank revolution the cylinder fires on. Wrong ratio, wrong angle or no cam fall out of the simulation. Cam phase is locked to the crank whenever the ratio is exactly 1:2. |
| Throw angle | 15° steps through the value panel. |
| Head ports | Decided by what is attached: an intake pipe or Air Intake on any free face is the intake, likewise exhaust. A pipe only counts if it is open towards the head (see *Pipes* below). |
| Fuel injection | The Fuel Injector is a part installed in the head. Every head on a crankshaft shares one fuel gallery, so fuel piped into any one head feeds the whole engine. Fuel is piped into any head face. |
| Fuel rack | Redstone on any block of a camshaft line closes that line's rack (15 = fuel cut off), like an inline injection pump. |
| Fuels | **Plant Oil** (compact seeds; rustic, harder to ignite), **Diesel** (heated mixing of Plant Oil with coal or charcoal; ignites readily, more energy) and **Gasoline** (superheated mixing of Diesel with blaze powder; too hard to light by compression at any speed this engine turns, so it needs a spark plug). Properties come from the `create_engines:fuel` data map. |
| Pipes | **Intake and Exhaust Manifolds are pipes**: six `north/east/…` open-end properties, a core and an arm model per open end, a matching collision shape. A pipe opens towards every compatible neighbour when it is placed, and a block placed beside pipes later (head, opening) opens them towards itself. Pipes only ever *close* by themselves (an end that lost its neighbour); a Wrench cuts or restores the connection nearest the point clicked, so two runs can lie side by side without joining. Pipes of different kinds never join. An opening connects only through its back (opposite its mouth), to a pipe or straight to a head. The scanner follows open ends from each head to its openings; both ends of a join have to agree. Anything that implements `AirflowPort` and sits in a pipe run (a turbocharger, an intercooler) is part of the airflow without the pipes or the scanner knowing about it, and the pipe graph is the natural place for forced-induction pressure later. |
| Fuel metering | Each head's injector has a **Fuel Metering** value box (10% to 200% of the usual shot, 100% by default). The injector delivers that much whatever the engine is doing; nothing matches it to the air the cylinder drew in (the earlier air-matched "smoke limiter" is gone). Breathing falls with speed, so a setting is right at one speed, rich above it and lean below it. Rich fuel is wasted (smoke), soaks up heat the piston would have had (less power), and drowns a spark; lean is weak. The torque rating (the dynamometer) uses the metering you chose, so a badly tuned engine is rated lower. Goggles on a cylinder read the mixture (balanced / n× rich / n× lean). The camshaft redstone fuel rack still scales the shot on top. |
| Air-fuel ratio | The mixture has a sweet spot, **0.8 to 1.25 times balanced** (the band goggles call *balanced*), and `sim.Mixture` **rewards getting into it more than it punishes missing it**. *Payoff:* at the band's edges a charge burns as an ordinary one; towards exactly balanced the flame gets up to 15% faster and the fuel gives up to 20% more heat, so the same fuel makes noticeably more work (a balanced charge makes well over a fifth more work per mB than a lean one and about half as much again as a rich one). *Off the band* the flame slows a little and a little less of the fuel's energy is useful, but only to a modest floor (heat 0.85, flame speed 0.7 lean / 0.8 rich), so an untuned engine is merely ordinary. *Drawbacks of rich, beyond the fuel wasted as smoke, which stay but are gentle:* **plug fouling** (a spark plug gains soot each cycle in proportion to the unburned share of the fuel, 0.2 per cycle at most, and loses 0.08 each cycle it fires clean; soot pulls the richest mixture the plug can light from 1.8 down to 1.25 at full fouling, so a fouled engine misfires *FOULED* until the injector is leaned, after which the plug cleans itself; diesels have no plug to foul) and **oil thinning** (unburned fuel washes into the oil at up to 0.06 of its strength per rotor cycle, up to half its strength however well it is supplied, so friction climbs; it boils back out at 0.04 per clean cycle). *Drawbacks of lean:* weaker, slower burning and, in a spark engine, a misfire below 0.5. Because the dyno rates torque at the engine's own metering, tuning also moves the rating, and an engine in the band is rated higher. Goggles show plug soot per cylinder and oil thinning on the crank; the dyno measures the oil as it is without thinning it. Achievements: Dialed In, Fouled Up, Gas in the Oil. |
| Spark ignition | A **Spark Plug** is a part installed in the head (like the injector) and fires once per cycle at 10° before firing TDC. It lights the fuel waiting in the cylinder whatever its temperature, provided the mixture is between 0.5 and 1.8 times balanced; outside that the cylinder misfires *too lean* or *too rich*. A plug with no charge behind it misfires *no spark*. A cylinder with a plug can still compression-ignite a fuel that gets hot enough (diesel in a spark engine runs on, with a flat battery too). |
| Batteries and alternators | A **Battery** is a plain block that stores charge, counted in sparks (200 at most), and loses one per spark. Any battery touching a crankshaft block or a cylinder head belongs to that engine and supplies every plug on it. It makes no charge itself. An **Alternator** is a Create kinetic block with a shaft through it: while the shaft turns it makes 0.01 spark per RPM per tick (for 2 SU/RPM of stress, only while a battery has room) and shares that between every battery touching it and, if it touches the crankshaft or a head, every battery of that engine. On the crank's own shaft line, cranking charges the battery and the running engine keeps it topped up; with no alternator turning, the engine runs only until the battery is flat. A plug with no battery on the engine reports *no battery* in goggles. Charge is not exposed as FE, and Create's electric systems are not integrated. |
| Turbocharger | A **Turbocharger** is a directional block (`FACING` points along its shaft at the compressor; the turbine is the other end) that sits in an exhaust run and an intake run at once. It implements `AirflowPort`: intake pipes join its compressor face and its four sides, exhaust pipes its turbine face and its four sides (a side face takes whichever kind arrives). A head can be bolted straight to its compressor face (intake) or turbine face (exhaust), but not a side face, which needs a pipe to say which kind. The scanner floods through it like a pipe block and records it on both port networks, so `EngineLayout.turbos()` gives each one its intake and exhaust network. In the sim (`engine.sim.Turbocharger`, pure Java, ticked by `EngineSimulation`) the turbine takes the heat the cylinders send out of their exhaust valves (only cylinders that burned fuel, so a motored engine can't spin it), the compressor takes shaft power in proportion to speed cubed, and the wheel's energy is what is left over: boost builds with about a second of lag and dies with the fuel. Boost raises the intake pressure each cylinder draws on, and compressing the air heats it, giving some of the density back. The turbine holds the exhaust above ambient (0.5 bar per bar of boost), so the cylinders push against back-pressure. A wastegate opens as the boost passes its limit (value box, 0.1 to 1.5 bar, default 0.6); a redstone signal on the turbo forces it open (blow-off). The dynamometer runs copies of the turbos with a five-second spool before measuring, so the rated torque includes boost. More air only makes power if the injectors are turned up (Fuel Metering), and a hotter charge brings gasoline closer to pre-igniting: both fall out of the existing rules. Goggles show boost, wheel speed, wastegate, back-pressure and which side is not on an engine. |
| Transmission | A **Transmission** is a Create `SplitShaftBlockEntity`, like the gearshift and clutch, so the kinetic network on both sides behaves as Create's own does. Gears: neutral (output is cut), first to fourth at 4, 2.5, 1.6 and 1 turns of input per turn of output, and reverse at 3 backwards. The gear is a value panel; a redstone signal overrides it (1 neutral, 2 to 5 first to fourth, 6 and above reverse). Shifting detaches the block from the network, changes the gear and re-adds it next tick, as Create's gearshift does. No engine rule was needed: the load an engine feels is network stress over crank speed, so a load behind a low gear asks the crank for 1/ratio of the torque and an engine carries what it could not direct. Shifting while carrying drive, reverse, and using every gear count towards achievements. |
| Achievements | Advancements live in `advancement/CEAdvancements` as a table, one row per advancement: datagen (`runData`) turns the rows into the advancement JSON and the lang entries. A part gets a `has(...)` row (obtained). Something the engine or a block does gets a `Milestone` constant, fired where it happens (`Achievements.award(level, blocks, milestone)` credits only the engine's operator: the one player who last placed or used one of those blocks, tracked by `Operators`/`OperatorLog` and saved with the level; a transmission also records whoever sets its gear) and a row that waits for it with `on(...)`. One trigger type, `create_engines:event`, carries the milestone name. The engine's own thresholds (`EngineMilestones`, pure Java and unit tested) are checked every ten ticks from `EngineController.syncDiagnostics`, no more than once per milestone per ten seconds. **Every new feature adds rows here.** |
| Engine management | Three blocks that do what the player used to do by hand, opt-in: with none fitted an engine behaves exactly as before, so a rich-running fault is still there to hit. A **MAF Sensor** is an inline intake pipe (`SensorBlock`, a `ManifoldBlock`, so it joins and scans like a throttle body). It caps each injector's shot at the air the cylinder drew in: `CylinderModel.airMatch` (the sensor's Mixture Target, 80% to 125% of balanced, default 100%) limits the shot to `air / airPerMb × target`. It is a cap, never an addition, so Fuel Metering stays the most a cylinder gets: set it high for slow running and the sensor trims it back as breathing falls with speed, rises with boost or closes with the throttle. A cut to nothing for want of air reads *no air*, not *no fuel*. The leanest sensor on a network wins; the dyno rating includes the cap. Its redstone output (and comparator output) is the size of the breath, 0 to 15 for 0 to 1.5 bores. A **Lambda Sensor** is the exhaust-side twin: a pure sensor whose output is the mean mixture of the cylinders that fired into its network (8 balanced, 6 to 9 across the band, 15 at 2× rich). It changes nothing itself, but feeds a comparator and a camshaft's fuel rack for a redstone loop of the player's own. A **Rev Limiter** touches a crank block or head like a battery; the lowest limit on an engine governs (value box, 20 to 250 RPM in 5s). `RevLimiter` cuts every injector (`CylinderModel.fuelCut`, reported as `CycleResult.FUEL_CUT`, shown gold in goggles) at the limit and releases at 95% of it, so the engine bounces off. Its output is rpm over limit, 15 at the limit. Sensors and the limiter read the sim every tick through `EngineController.readSensors/readLimiters`; `Sensors` (pure Java) turns readings into signals and is unit tested with the cap and the limiter (`EngineManagementTest`, plus `sensorsReadAndTrimAnEngine` and `aRevLimiterBouncesAnEngineOffItsLimit` GameTests). Recipes use brass sheets and electron tubes. Ponder pages: `maf_sensor`, `lambda_sensor`, `rev_limiter`. Achievements: Weighing the Air, Penny Pincher, Breath Test, Right on Lambda, Know Your Limits, Bouncing Off the Limiter. |
| Lubrication | **Engine Oil** (heated mixing of Plant Oil with a slime ball) is piped into any cylinder head, into a second tank beside the fuel tank, and shared along touching heads like fuel. It lubricates the whole engine: crank blocks and every piston and rod. Turning uses it up (about 0.01 mB per part per crank revolution); while the supply keeps up the film builds to full in a few seconds, and without oil it thins over a few hundred revolutions. A dry part drags: friction doubles, and speed-dependent friction quadruples, so a dry engine tops out at roughly half the speed and has less torque to spare, but it still runs. A new engine starts dry. Goggles show the lubrication. |
| Oil sump | A **Sump** is an oil pan: a block with one engine-oil tank (1000 mB) and a fluid capability on every face, so Create pipes and pumps fill it and a bucket (right-click) fills or empties it. Touching any crankshaft block or cylinder head, it belongs to that engine (`EngineLayout.sumps()`), and the engine draws its lubrication oil from the pans before the heads' own oil tanks (`SumpOilSupply`). The point is face economy: Create pipes carry one fluid at a time, so a head used to need a fuel line and an oil line, two of its five free faces. Oil lubricates the whole engine anyway, so it now has one place to come in, away from the heads, and the head keeps only its fuel line. Heads still accept oil on any face, for engines built before the sump existed. Fluid only moves through pipes and pumps (or by hand); nothing pulls from adjacent tanks. |
| Pushrod cams | A Cam Lobe works a cylinder it touches at the **head or at the cylinder block** (head first, then the block's four side faces, skipping the bore axis; the lobe's axis must not point into the face). The block position is a pushrod cam: the cylinder's timing, cam line and goggle readouts are the same, and the head draws no valve stems because the stems are inside. The point is face economy for compact engines: a head already spends faces on its cylinder, its two neighbours in the gallery, fuel and the intake and exhaust, so a V12 built of dense banks had no face left for a camshaft. A cam beside the block leaves both for pipes. A lobe serves every cylinder touching it with *one* lobe angle, so a lobe in the valley of a V (touching both cylinders of a throw) cannot time both unless the two reach top dead centre together; give each bank its own camshaft. A 90° V12 (six throws, two cylinders each) cannot fire at exactly even intervals, because the second cylinder of every throw is 90° from the first and 60° does not divide that; goggles show the intervals it does get. Overlong goggle lines wrap to the screen (`content/GoggleLines`), and Ponder has pages for firing order (`firing_order`), the compact V (`compact_v`) and the air-fuel recipe (`mixture`). |
| Governor | A **Governor** touches a crankshaft block or head (like a rev limiter) and holds the crank at its Governed Speed (value box, 20 to 250 RPM in 5s; the lowest on an engine governs). `sim.Governor` is a proportional controller with droop (full rack at 10% below the setting) and a slow reset that integrates the droop away, frozen while the rack is saturated so a start-up or an overload leaves no overshoot. Its output is a rack position, 0 to 1, put in `CylinderModel.governor`: a compression engine is governed on fuel alone, a spark engine on its air (throttle opening 8% to 100%) with the throttle linkage trimming the fuel, because a charge that is too lean will not light. It is deliberately outside the rating: the dynamometer copies leave it at 1, so a governor holds speed with the torque in hand and takes none away. Redstone output is the rack position (15 wide open), which reads as a load meter; with the rack at 100% the load is more than the engine can hold at that speed. Achievements: Steady as She Goes, Cruise Control. |
| Firing order tool | A **Timing Tool** (item) sets an engine's throws and cam lobes for the player. `engine.topology.FiringPlanner` (pure Java) takes each working cylinder's throw, bore offset and lobe and searches throw angles (15 degree steps) and the crank revolution each cylinder fires on by simulated annealing, minimising uneven spacing, then neighbours firing in a row, with a penalty for a cylinder sharing a lobe with another it fires at a different time. Four styles, changed by sneak-use: smooth (any angle), cross-plane (quarter turns), flat-plane (half turns) and cams only (throws left alone, lobes chosen exactly). It reports the firing order and spacing in the action bar, says when a layout cannot fire evenly (a 90 degree V12) or when a lobe is shared, and counts any cylinder it skipped. Achievements: Timing Light, Right on Time. |
| Precision parts | Four parts that each improve the crude cylinder in one way, and add up (`CylinderSpec.build`, `PrecisionParts`): a **Precision Cylinder** block (machined liner: 1.2x compression, 0.7x static and 0.8x viscous wall friction), a **Precision Cylinder Head** block (four valves: 1.5x valve flow, 0.8x ignition delay), a **Forged Piston** item (0.7x inertia, 0.8x and 0.7x friction) and a **Precision Injector** item (0.8x ignition delay, 0.7x burn time). The two items install into the same slots as the ordinary piston and injector: `PartInstallation.Slot` sets the slot's own property and a second grade property beside it, so everything that asks only whether a piston or injector is fitted is untouched. Recipes consume Create's Precision Mechanism (pistons and injectors come two to a craft) and need the crude part first. On a straight-12 on gasoline each part is worth 3 to 30 percent of peak power (the head most, as it helps at speed) and all four about 60 percent, before a turbo. Achievements: Tighter Tolerances, Four Valves a Cylinder, Forged in Fire, Common Rail, Blueprinted. |
| Coupled crankshafts | Crank lines that Create's gearing holds at exactly the same speed are one engine. `EngineScanner` finds them by walking the kinetic connections out from the crank (`CrankCoupling`, which asks Create's `RotationPropagator`, by reflection, which blocks are connected and by what speed ratio, so cogwheels, gearboxes, chain drives, belts and bevels all couple, and so does an engine that is standing still). Any crank block at a ratio of exactly +1 or -1 within `maxCouplingWalk` kinetic blocks joins, up to `maxCoupledLines` lines. The lines are put in position order, and the first line's start is the primary: the only generator, with the other lines carrying rotation. Each line has a *sense*, +1 if the gearing turns it the way the first line turns and -1 if the other way, and a cylinder's top dead centre in the engine's frame is its sense times its own, so the simulation needs no knowledge of coupling at all: every cylinder of every line is on the one rotor by the usual formula. Drawing follows, a line being shown at the primary's angle times its sense (Create draws a shaft at time times speed, so no phase offset arises). The Timing Tool plans a reversed line as its mirror image, flipping the sign of both its bore offsets and its throw angles, and a starter turns the engine the way its line's sense gives. Batteries, oil pans, governors, limiters and starters touching any line belong to the engine; fuel and oil are one gallery. Goggles name the count. Achievements: In Step (four or more cylinders firing on coupled cranks), Hypercar Money (sixteen firing cylinders). |
| Cost against output | Output is linear in cylinders: about 3.6k SU a cylinder on diesel and 5k on gasoline at the best speed, 8.5k with precision parts, 12k with a turbo on top. A Create steam engine is 11 items (a gold sheet, an andesite alloy, a copper block) for 16k SU, with a boiler and a burner to feed it. A working cylinder with its crank throw, cam lobe and ports used to cost about 28 items, so to keep the investment honest the cylinder, cylinder head, piston, connecting rod and fuel injector are now crafted two at a time (about 20 items a cylinder). What a large engine buys is not cheaper power per item but power in one compact place with no water and no boiler, which scales to many steam engines' worth on one crank or on coupled ones. |
| Output against steam | The server config `stressPerTorque` is 100 (it was 40). A Create steam engine is 1024 SU/RPM at 16 RPM, 16384 SU, for a boiler and a heat source; combustion is meant to be well ahead of it for the cost of building and fuelling a large engine. `CombustionOutputTest` pins the dyno peak power at this calibration: a four-cylinder diesel is about one steam engine, a straight-12 on gasoline with a MAF sensor about four (roughly 60k SU), a full precision straight-12 about six and a turbo on top of that more than twelve. Refined fuel matters: gasoline is worth over a fifth more than diesel in the same engine. Advancements track it: Heavy Duty is 16384 SU (one steam engine), Steam Is for Kettles is 65536. Existing worlds keep the `stressPerTorque` they saved (40), and need the config value changed to see the new numbers. |
| Names | Display name "Create: Engine Engineer"; mod id `create_engines`; package `com.createengines`. |
| Commits | One commit per major feature. |

### Differences from the proposal worth knowing

- **Cranking is a speed floor, not a fixed speed.** Anything turning the crank (a starter, or another kinetic source on
  the crank's network) holds it at *at least* that speed; the engine may outrun it. This is what lets the engine pull
  away from a starter, and what made a released single cylinder behave correctly.
- **A cylinder that can't burn fuel makes no power.** A new cylinder holds stale gas, not fresh air, so one with no
  intake can't fire on the air it was built with; and the exhaust port only gives back hot gas if the cylinder has
  burned fuel recently, so pumping air can't be turned into heat. A starter holding the crank above its own speed
  because of pumping ripple is just its one-way clutch overrunning; it is not power.
- **A piston and rod with no head still drag the crank.** Cylinders left out of the simulation for want of a head still
  add their inertia and friction (and need oil), so a crank with nothing working on it coasts to a stop within a
  couple of seconds instead of spinning on. An engine with nothing firing reads *Coasting*, not *Running*.
- **A piston with no connecting rod doesn't move.** Only the rod ties it to the crank.
- **The intake manifold remembers what was pushed into it.** Gas forced back out through the intake valve is
  breathed in again first. Without this, an engine with no exhaust kept running by exhausting through its intake.
- **Engine rebuilds keep cylinder gas state**, and an unchanged layout keeps its controller. The periodic safety-net
  rescan (every 100 ticks) once reset every cylinder mid-stroke and stalled the engine.
- **Fallback renderers exist for every moving part** (`EnginePartRenderers`), sharing poses with the Flywheel visuals
  through `EnginePoses`. Ponder needs them, and so do worlds with Flywheel switched off.
- Calibration: 1 unit of simulated torque = 100 SU/RPM (`stressPerTorque` server config, raised from 40 so that large
  engines outclass steam: see *Output against steam*). A crude single cylinder makes roughly 40 SU/RPM at moderate
  speed, so about 2500 SU at 64 RPM.

### Known gaps and follow-ups

- **Speed depends on load unless a governor holds it.** Unloaded, a crude engine revs until its torque falls to
  friction (about 150 to 170 RPM), and under load it slows into its torque band. A Governor holds a set speed (see its
  row above); without one the speed of everything the engine drives follows the load.
- **Lubrication is one figure for the whole engine.** There is no oil pump, filter or per-part wear: an engine is
  oiled or it isn't. Nothing shows the oil in a Ponder scene yet.
- **The default metering (100%) runs rich above about 100 RPM** on diesel (black smoke, less power, and now thinned oil:
  see the *Air-fuel ratio* row). That is deliberate: tuning it is the point of the dial. A spark engine on the default floods above about 150 RPM and then pre-ignites by
  compression around 190, so it has a narrower usable band than a diesel.
- **A throttle trims air and, by a fixed linkage, fuel.** A throttle body is an intake pipe with a valve in it: it
  scales the intake flow of every head on its network (the most closed one wins when several sit in series), so air
  per cycle and work fall as it closes, and below about 15% opening a spinning engine loses more to pumping than it
  makes. Its opening is the idle stop at no redstone signal, rising to wide open at 15, read every tick. Its linkage
  scales every injector's shot by `Throttle.fuelTrim(opening)`, a curve fitted to how air falls with opening at about
  64 RPM. That is a fixed curve, not the air the cylinder really breathed (which also falls with speed), so this is not
  the automatic mixture matching removed above: a throttled engine is no longer flooded by closing the valve, but a
  mixture tuned for one speed still drifts rich above it. The rack (redstone on the camshaft) still cuts fuel alone.
  Parallel throttles on one network are not modelled: the network takes the minimum.
- **Cam direction is ignored**: only the speed ratio's magnitude is checked, because Create gearing flips direction
  freely.
- **Only an exact one-to-one gearing couples crankshafts.** Cranks geared at any other ratio are still separate engines
  that both feed one network (the faster wins, as Create does for any two sources). Create's own blocks do not tell the
  engine scanner when they are placed, so a new coupling, or a broken one, is found at the next rescan, at most five
  seconds later. A redstone gearshift that reverses a coupled line is likewise not noticed until then.
- Placeholder art: models reuse Create and vanilla textures. Pipes have no bend or T pieces of their own (arms around a core).
  The block models are generated by `tools/generate_models.py` (boxes only); edit it and rerun rather than the JSON.
  Animated parts (piston, rod, valve, crank, cam) keep the pivot and envelope the renderers expect, with detail inside it.
- **Old saves lose their manifolds' connections.** Manifolds used to be stateless; one placed before pipes existed has no
  open ends until a wrench opens them.
- **Spark timing is fixed.** A spark engine's timing is the cam lobe's angle plus a fixed advance; there is no
  distributor and no ignition-advance dial. There is also no knock: pre-ignition of gasoline at high speed just falls
  out of the compression temperature.
- **No intercooler, no knock, no turbo damage.** Compressing the air heats it and nothing cools it again; excess boost shows as pre-ignition in a spark engine and as waste in a rich one, and nothing breaks. Two turbos feeding one cylinder overwrite each other's pressures (the last ticked wins); several turbines on one exhaust network split its heat evenly. The transmission has no clutch slip and no synchro, and its gears are fixed at four forward and one reverse.
- The turbocharger, transmission and sump have Ponder scenes and smoke-test screenshots (a real-world turbo, sump and motor-driven transmission). The turbo's wheel speed in Ponder comes from NBT set by the scene, not an engine; a running turbo spinning in a real world has not been watched by hand.
- Not yet tested in-game by hand: wrenching pipes, placing a spark engine by a player, placing and wiring an engine by a player, multi-cylinder builds beyond the Ponder
  scenes, and the fluid pipe to head connection (the head exposes a fluid capability on every face).

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

> **Revised after play-testing.** The rule below put a loaded engine exactly on the overstress line: it slows until its
> torque equals the load, so "torque at the current RPM" equals the load, and any ripple overstressed the network and
> stopped everything on it. As built, capacity is the engine's **rated torque**: `engine.sim.Dynamometer` drives copies
> of its cylinders at fixed speeds from 16 to 256 RPM with racks fully open and records net torque at each, and the
> rating is 90% of the curve's peak at or below the current RPM. The curve is re-measured only when a cylinder's fuel
> or cam lock changes. A load within the rating is carried by lugging down; a load above it overstresses the network,
> and because an overstressed network has stopped everything on it, the engine then feels no load and keeps running.

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
- **Two crank lines geared one to one** are one engine with one rotor (see *Coupled crankshafts* in the feature table).
  Geared at any other ratio they are two engines feeding one Create network, and Create already sums capacities.

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
  **As built:** play-testing showed the jumps clearly, so engine parts use `RenderRotation`, an accumulated angle. The
  primary crankshaft owns it, the other crank blocks follow it, and phase-locked camshafts take exactly half of it.

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
| Brass | Precision parts: tighter tolerances (lower friction, higher compression), adjustable injection timing, mechanical governor. Built: the governor (brass casing) and the precision cylinder, head, piston and injector (precision mechanisms). | Brass, precision mechanism. |
| Later | Refined fuels (gasoline), magneto + spark plugs, higher-compression and high-RPM parts, forced induction (built, see section 0), transmissions (built, see section 0), other engine types. | — |

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

## 14. Decisions

All resolved in review; see the table in §0.
