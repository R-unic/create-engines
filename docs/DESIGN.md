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
| Unbroken lines | A crankshaft and a camshaft are each one unbroken line of their own blocks, which is what a plain Create Shaft cannot stand in for. A Shaft between two crank blocks on one axis (found by walking the shafts and flywheels past each end of the line, `EngineScanner.countFlywheels`) sets `EngineLayout.broken`: the engine goes inert like an unloaded one and reports `EngineState.BROKEN` until journals replace the shaft. A Shaft between two camshaft blocks (`EngineScanner.shaftBeyond`) marks that `CamLine` broken: the cylinders it served get `CylinderFault.BROKEN_CAM` and no cam, so they misfire. Shafts and flywheels at a line's ends are fine (flywheels add inertia); only a shaft with more of the line beyond it breaks it. Gearing between lines is unaffected (that is how coupled cranks work). GameTests `aShaftInTheCrankLineBreaksTheEngine` and `aShaftInTheCamshaftLeavesTheLobeWithNoCam`. |
| Head ports | Decided by what is attached: an intake pipe or Air Intake on any free face is the intake, likewise exhaust. A pipe only counts if it is open towards the head (see *Pipes* below). |
| Fuel injection | The Fuel Injector is a part installed in the head. Every head on a crankshaft shares one fuel gallery, so fuel piped into any one head feeds the whole engine. Fuel is piped into any head face. |
| Fuel rack | Redstone on any block of a camshaft line closes that line's rack (15 = fuel cut off), like an inline injection pump. A sensor's own output does not count (`Signals.bestNeighbourIgnoring`, `EngineInputs.readFuelRacks`): a MAF sensor that touches the camshaft used to close the rack it was trimming and starve the engine, so a sensor can sit anywhere. Other redstone still closes it. |
| Fuels | **Plant Oil** (compact seeds; rustic, harder to ignite), **Diesel** (heated mixing of Plant Oil with coal or charcoal; ignites readily, more energy) and **Gasoline** (heated mixing of Diesel with blaze powder; too hard to light by compression at any speed this engine turns, so it needs a spark plug). Properties come from the `create_engines:fuel` data map. |
| Pipes | **Intake and Exhaust Manifolds are pipes**: six `north/east/…` open-end properties, a core and an arm model per open end, a matching collision shape. A pipe opens towards every compatible neighbour when it is placed, and a block placed beside pipes later (head, opening) opens them towards itself. Pipes only ever *close* by themselves (an end that lost its neighbour); a Wrench cuts or restores the connection nearest the point clicked, so two runs can lie side by side without joining. Pipes of different kinds never join. An opening connects only through its back (opposite its mouth), to a pipe or straight to a head. The scanner follows open ends from each head to its openings; both ends of a join have to agree. Anything that implements `AirflowPort` and sits in a pipe run (a turbocharger, an intercooler) is part of the airflow without the pipes or the scanner knowing about it, and the pipe graph is the natural place for forced-induction pressure later. |
| Fuel metering | Each head's injector has a **Fuel Metering** value box (10% to 200% of the usual shot, 100% by default). The injector delivers that much whatever the engine is doing; nothing matches it to the air the cylinder drew in (the earlier air-matched "smoke limiter" is gone). Breathing falls with speed, so a setting is right at one speed, rich above it and lean below it. Rich fuel is wasted (smoke), soaks up heat the piston would have had (less power), and drowns a spark; lean is weak. The torque rating (the dynamometer) uses the metering you chose, so a badly tuned engine is rated lower. Goggles on a cylinder read the mixture (balanced / n× rich / n× lean). The camshaft redstone fuel rack still scales the shot on top. A MAF sensor on the intake replaces this setting: it sets the shot from the air instead (see *Engine management*). |
| Air-fuel ratio | The mixture has a sweet spot, **0.8 to 1.25 times balanced** (the band goggles call *balanced*), and `sim.Mixture` **rewards getting into it more than it punishes missing it**. *Payoff:* at the band's edges a charge burns as an ordinary one; towards exactly balanced the flame gets up to 15% faster and the fuel gives up to 20% more heat, so the same fuel makes noticeably more work (a balanced charge makes well over a fifth more work per mB than a lean one and about half as much again as a rich one). *Off the band* the flame slows a little and a little less of the fuel's energy is useful, but only to a modest floor (heat 0.85, flame speed 0.7 lean / 0.8 rich), so an untuned engine is merely ordinary. *Drawbacks of rich, beyond the fuel wasted as smoke, which stay but are gentle:* **plug fouling** (a spark plug gains soot each cycle in proportion to the unburned share of the fuel, 0.2 per cycle at most, and loses 0.08 each cycle it fires clean; soot pulls the richest mixture the plug can light from 1.8 down to 1.25 at full fouling, so a fouled engine misfires *FOULED* until the injector is leaned, after which the plug cleans itself; diesels have no plug to foul) and **oil thinning** (unburned fuel washes into the oil at up to 0.06 of its strength per rotor cycle, up to half its strength however well it is supplied, so friction climbs; it boils back out at 0.04 per clean cycle). *Drawbacks of lean:* weaker, slower burning and, in a spark engine, a misfire below 0.5. Because the dyno rates torque at the engine's own metering, tuning also moves the rating, and an engine in the band is rated higher. Goggles show plug soot per cylinder and oil thinning on the crank; the dyno measures the oil as it is without thinning it. Achievements: Dialed In, Fouled Up, Gas in the Oil. |
| Spark ignition | A **Spark Plug** is a part installed in the head (like the injector) and fires once per cycle at 10° before firing TDC. It lights the fuel waiting in the cylinder whatever its temperature, provided the mixture is between 0.5 and 1.8 times balanced; outside that the cylinder misfires *too lean* or *too rich*. A plug with no charge behind it misfires *no spark*. A cylinder with a plug can still compression-ignite a fuel that gets hot enough (diesel in a spark engine runs on, with a flat battery too). |
| Batteries and alternators | A **Battery** is a plain block that stores charge, counted in sparks (200 at most), and loses one per spark. Any battery touching a crankshaft block or a cylinder head belongs to that engine and supplies every plug on it. It makes no charge itself. An **Alternator** is a Create kinetic block with a shaft through it: while the shaft turns it makes 0.01 spark per RPM per tick (for 2 SU/RPM of stress, only while a battery has room) and shares that between every battery touching it and, if it touches the crankshaft or a head, every battery of that engine. On the crank's own shaft line, cranking charges the battery and the running engine keeps it topped up; with no alternator turning, the engine runs only until the battery is flat. A plug with no battery on the engine reports *no battery* in goggles. Spark charge is not exposed as FE (it only runs plugs). Separately, an alternator offers **Forge Energy** on every face: a buffer of 20 ticks of output that cables and machines can draw from, and which it also pushes into any neighbour that takes energy, at `alternatorEnergy` FE per RPM per tick (2 by default, so one stress unit is about one FE a tick; 0 turns it off). The buffer fills for nothing, but the shaft only carries 2 SU/RPM of stress while energy has left the buffer in the last half second, so an alternator with nothing attached costs nothing. The Mains Power advancement is for it. |
| Turbocharger | A **Turbocharger** is a directional block (`FACING` points along its shaft at the compressor; the turbine is the other end) that sits in an exhaust run and an intake run at once. It implements `AirflowPort`: intake pipes join its compressor face and its four sides, exhaust pipes its turbine face and its four sides (a side face takes whichever kind arrives). A head can be bolted straight to its compressor face (intake) or turbine face (exhaust), but not a side face, which needs a pipe to say which kind. The scanner floods through it like a pipe block and records it on both port networks, so `EngineLayout.turbos()` gives each one its intake and exhaust network. In the sim (`engine.sim.Turbocharger`, pure Java, ticked by `EngineSimulation`) the turbine takes the heat the cylinders send out of their exhaust valves (only cylinders that burned fuel, so a motored engine can't spin it), the compressor takes shaft power in proportion to speed cubed, and the wheel's energy is what is left over: boost builds with about a second of lag and dies with the fuel. Boost raises the intake pressure each cylinder draws on, and compressing the air heats it, giving some of the density back. The turbine holds the exhaust above ambient (0.5 bar per bar of boost), so the cylinders push against back-pressure. A wastegate opens as the boost passes its limit (value box, 0.1 to 1.5 bar, default 0.6); a redstone signal on the turbo forces it open (blow-off). The dynamometer runs copies of the turbos with a five-second spool before measuring, so the rated torque includes boost. More air only makes power if the injectors are turned up (Fuel Metering), and a hotter charge brings gasoline closer to pre-igniting: both fall out of the existing rules. Goggles show boost, wheel speed, wastegate, back-pressure and which side is not on an engine. |
| Stacked boost | Every stage of forced induction on an engine is applied together by `EngineSimulation.applyInduction` at the end of each tick, not by the stage itself, so two of them no longer overwrite each other. For each cylinder the compressors on its intake run in turn: pressure ratios (1 + boost) and temperature ratios multiply, so two turbos at 0.6 bar make 1.56 bar, not 0.6. That is for stages in **series**; see *Series and parallel* below. Intercoolers then act on the result, and every turbine on its exhaust run adds its back-pressure (they sum). What series buys is a boost one turbo cannot make, and what it costs is back-pressure, lag and the pumping loss that goes with it. Turbos on separate intake networks (one per bank) are independent. `TurboStatus.charge` is the boost on the air a turbo feeds, all stages and coolers counted, and goggles show it when it differs from that turbo's own. Advancements: Compound Interest (two turbos at 0.2 bar or more on one cylinder). |
| Series and parallel | The scanner tests every turbocharger and supercharger on a port network: it follows the pipes again as if that block were removed, and if no opening can still be reached from the heads, every bit of air goes through it and it is **in series**; otherwise the air can go round it and it is **in parallel** (`PortNetwork.parallel`). Intake and exhaust sides are judged separately. Parallel stages share the flow: the cylinder gets the *average* of their pressures and temperatures instead of the product, parallel turbines average their back-pressure instead of adding it, and parallel turbines split the exhaust heat while a series turbine gets all of it. Each turbo in a bank of *n* moves 1/*n* of the air (`Turbocharger.flow`): half the compressor work and a smaller wheel (inertia goes with the square of the flow), so a twin-turbo bank reaches about one big turbo's boost and gets there sooner, where a compound pair reaches a higher boost, later. |
| Supercharger | A **Supercharger** is a Create kinetic block (`RotatedPillarKineticBlock`, shaft through it along `AXIS`) and an `InlineDuct`: intake pipes join the four faces round the shaft, never the shaft's ends, and a head can be bolted to any of them. `engine.sim.Supercharger` makes `0.9 bar x (rpm / 128)^2` from the shaft speed alone, following it with a 0.2 s lag, held at the Boost Limit value box (default 0.5 bar, up to 1.5) by a bypass valve that a redstone signal opens fully. No exhaust and no wheel to spool, so it boosts a motored or stalled engine and from the first turn of the crank; the trade is stress. The block puts `4 SU/RPM x cylinders fed x (boost / 0.9)` on its own shaft (updated by the engine every ten ticks), so on the crank's own shaft line it comes out of the engine's output; driven by another source it costs the engine nothing. A blower is a cruder pump than a turbine compressor (efficiency 0.55 against 0.7), so it heats the air more. The dynamometer cannot read a shaft, so a blower copy turns with the crank at the ratio of shaft speed to crank speed last seen on the live engine (`Supercharger.driveRatio`, 1 until the engine has run), and the rating key includes that ratio rounded to a quarter. Achievements: Blown Away, Whine On, Twincharged (a turbo and a blower at 0.2 bar or more on one cylinder). |
| Intercooler | An **Intercooler** is a plain block with an `AXIS` (`InlineDuct`; intake pipes join its two ends). In the sim (`engine.sim.Intercooler`) it removes 60% of the compression heat the compressors put in (`T = 1 + (T - 1) x 0.4`) and costs 4% of the boost in pressure, after all the compressors whatever order the blocks sit in; two in a row each take their share of what is left. Cooler air is denser and further from pre-igniting. It is the answer to a blower's heat and to the turbo+blower stack. No block entity, so no goggles. Achievements: Chill Out, Cool Customer (a firing engine at 0.3 bar or more through one). |
| Inline ducts | `InlineDuct extends AirflowPort` is what the scanner treats as a pipe junction (the air goes in at one accepting face and out of another): the turbocharger, supercharger and intercooler implement it, and `kindFacingHead` says what a head bolted straight to a face sees. A new pipe-run part is a block that implements it plus, if the sim cares, a list on `PortNetwork` and `EngineLayout`. |
| Achievements | Advancements live in `advancement/CEAdvancements` as a table, one row per advancement: datagen (`runData`) turns the rows into the advancement JSON and the lang entries. A part gets a `has(...)` row (obtained). Something the engine or a block does gets a `Milestone` constant, fired where it happens (`Achievements.award(level, blocks, milestone)` credits only the engine's operator: the one player who last placed or used one of those blocks, tracked by `Operators`/`OperatorLog` and saved with the level) and a row that waits for it with `on(...)`. One trigger type, `create_engines:event`, carries the milestone name. The engine's own thresholds (`EngineMilestones`, pure Java and unit tested) are checked every ten ticks from `EngineReadouts.syncDiagnostics`, no more than once per milestone per ten seconds. **Every new feature adds rows here.** |
| Engine management | Opt-in, like the governor: with no MAF sensor fitted an engine behaves exactly as before, so a rich-running fault is still there to hit. A **MAF Sensor** is an inline intake pipe (`SensorBlock`, a `ManifoldBlock`, so it joins and scans like a throttle body). It sets each injector's shot from the air the cylinder drew in, whichever way that moves it: `CylinderModel.airMatch` (the sensor's Mixture Target, 80% to 125% of balanced, default 100%) makes the shot `air / airPerMb × target`, trimmed back when the air thins with speed and topped up when it could burn more than the injector was set to give. Fuel Metering therefore no longer applies on a network with a sensor. The most the injector can give is `CylinderModel.INJECTOR_CAPACITY` (2× the usual shot, Fuel Metering's top setting), so very heavy boost can still run leaner than the target. The rack still scales the matched shot (a closed rack is *no fuel*), and so does a compression engine's governor; a spark engine's throttle is already in the air, so its fuel trim is not applied twice. `CycleStats.mafCut` and `mafAdded` say how much of the shot the sensor held back or added. A cut to nothing for want of air reads *no air*, not *no fuel*. The leanest sensor on a network wins; the dyno rating uses the same matched shot. Its redstone output (and comparator output) is the size of the breath, 0 to 15 for 0 to 1.5 bores; goggles say whether it is cutting or adding. `Sensors` (pure Java) turns readings into signals and is unit tested with the sensor's two-way metering (`EngineManagementTest`, plus the `aMafSensorReadsAndTrimsAnEngine` and `aMafSensorTopsUpALeanEngine` GameTests). Recipe uses brass sheets and electron tubes. Ponder page: `maf_sensor`. Achievements: Weighing the Air, Penny Pincher, Topping Up. The lambda sensor and the rev limiter were removed: nothing in the simulation punishes overspeed or rewards reading the exhaust once the MAF sensor feeds both ways, and a governor holds a speed better than a fuel cut. |
| Lubrication | **Engine Oil** (heated mixing of Plant Oil with a slime ball) is piped into any cylinder head, into a second tank beside the fuel tank, and shared along touching heads like fuel. It lubricates the whole engine: crank blocks and every piston and rod. Turning uses it up (about 0.01 mB per part per crank revolution); while the supply keeps up the film builds to full in a few seconds, and without oil it thins over a few hundred revolutions. A dry part drags: friction doubles, and speed-dependent friction quadruples, so a dry engine tops out at roughly half the speed and has less torque to spare, but it still runs. A new engine starts dry. Goggles show the lubrication. Another mod's fluid in the `create_engines:lubricants` tag (Create: Petrochem's lubricant) oils as well as engine oil; an oil exchange burns it off, as it does a poor lubricant, so it never becomes used oil (author, 2026-11-27). |
| Oil sump | A **Sump** is an oil pan: a block with one engine-oil tank (1000 mB) and a fluid capability on every face, so Create pipes and pumps fill it and a bucket (right-click) fills or empties it. Touching any crankshaft block or cylinder head, it belongs to that engine (`EngineLayout.sumps()`), and the engine draws its lubrication oil from the pans before the heads' own oil tanks (`SumpOilSupply`). The point is face economy: Create pipes carry one fluid at a time, so a head used to need a fuel line and an oil line, two of its five free faces. Oil lubricates the whole engine anyway, so it now has one place to come in, away from the heads, and the head keeps only its fuel line. Heads still accept oil on any face, for engines built before the sump existed. Fluid only moves through pipes and pumps (or by hand); nothing pulls from adjacent tanks. |
| Pushrod cams | A Cam Lobe works a cylinder it touches at the **head or at the cylinder block** (head first, then the block's four side faces, skipping the bore axis; the lobe's axis must not point into the face). The block position is a pushrod cam: the cylinder's timing, cam line and goggle readouts are the same, and the head draws no valve stems because the stems are inside. The point is face economy for compact engines: a head already spends faces on its cylinder, its two neighbours in the gallery, fuel and the intake and exhaust, so a V12 built of dense banks had no face left for a camshaft. A cam beside the block leaves both for pipes. A lobe that serves several cylinders times the first with its angle and the rest at the same offset from their own top dead centres (see *Valley cam lobes*), so one camshaft in the valley of a V works both banks. A 90° V12 (six throws, two cylinders each) cannot fire at exactly even intervals, because the second cylinder of every throw is 90° from the first and 60° does not divide that; goggles show the intervals it does get. Overlong goggle lines wrap to the screen (`content/GoggleLines`), and Ponder has pages for firing order (`firing_order`), the compact V (`compact_v`) and the air-fuel recipe (`mixture`). |
| Governor | A **Governor** touches a crankshaft block or head (like a battery) and holds the crank at its Governed Speed (value box storing an exact RPM, 20 to 1000: the panel has a row of exact speeds up to 256 and a row in steps of 5 above that, so it can govern an engine under a raised Create RPM limit, and older saves, which held the setting in steps of 5, are converted when they load; the lowest on an engine governs). A redstone signal on the block commands a share of that setting: none leaves it alone, 1 to 15 asks for that fraction (`Governor.commanded`, never below 20 RPM), so a circuit can set an engine's speed; the lowest commanded speed governs and goggles say when redstone has turned it down. `sim.Governor` is a proportional controller with droop (full rack at 10% below the setting) and a slow reset that integrates the droop away, frozen while the rack is saturated so a start-up or an overload leaves no overshoot. Its output is a rack position, 0 to 1, put in `CylinderModel.governor`: a compression engine is governed on fuel alone, a spark engine on its air (throttle opening 8% to 100%) with the throttle linkage trimming the fuel, because a charge that is too lean will not light. It is deliberately outside the rating: the dynamometer copies leave it at 1, so a governor holds speed with the torque in hand and takes none away. The analog output is the rack position (15 wide open), which reads as a load meter through a comparator; with the rack at 100% the load is more than the engine can hold at that speed. The block is deliberately not a signal source: a block that powered its neighbours would feed its own output back in through any dust beside it as a command. It also burns less fuel than a racing engine when nothing loads it (`GovernorTest`). Achievements: Steady as She Goes, Cruise Control, Speed Dial. |
| Firing order tool | A **Timing Tool** (item) sets an engine's throws and cam lobes for the player. `engine.topology.FiringPlanner` (pure Java) takes each working cylinder's throw, bore offset and lobe and searches throw angles (15 degree steps) and the crank revolution each cylinder fires on by simulated annealing, minimising uneven spacing, then neighbours firing in a row, with a penalty for a cylinder sharing a lobe with another it fires at a different time. Four styles, changed by sneak-use: smooth (any angle), cross-plane (quarter turns), flat-plane (half turns) and cams only (throws left alone, lobes chosen exactly). It reports the firing order and spacing in the action bar, says when a layout cannot fire evenly (a 90 degree V12) or when a lobe is shared, and counts any cylinder it skipped. Achievements: Timing Light, Right on Time. |
| Compression parts and rings | A cylinder's `compression` state (and a rotor housing's) is stock, high or low, scaling the compression ratio by `CylinderSpec.HIGH_COMPRESSION` or `LOW_COMPRESSION` through the `high_compression` and `low_compression` part effects. Compression pistons also set `forged_piston`. A low compression piston with no plug judges lighting at the stock piston's temperature (`CylinderModel.lightingHeat`). Rotors run too cool to pre-ignite, so the two compression rotors carry their boost trade directly (`rotorEarlyLoss`, `rotorBoostGain`). `rings` (0 to 3) is block state too, each ring `CylinderSpec.RING_GAIN` more heat from a burn. All travel in the `PrecisionParts` bits. |
| Polluted intake air | Off by default (`compat.pollutedIntake`). `EngineAmbient.readIntakeAir` reads the gas in front of every intake opening (`PollutionCompat.gasAtMouth`, cached), `IntakeAir` turns it into a loss for each intake line, less behind an Air Filter (`PortNetwork.filtering`), and the mean scales the ambient density beside altitude. The Air Filter and the Intercooler share `IntakeCoreBlock`. The filter is always registered; its recipe and creative entry need the pollution mod. |
| Precision parts | Four parts that each improve the crude cylinder in one way, and add up (`CylinderSpec.build`, `PrecisionParts`): a **Precision Cylinder** block (machined liner: 1.2x compression, 0.7x static and 0.8x viscous wall friction), a **Precision Cylinder Head** block (four valves: 1.5x valve flow, 0.8x ignition delay), a **Forged Piston** item (0.7x inertia, 0.8x and 0.7x friction) and a **Precision Injector** item (0.8x ignition delay, 0.7x burn time). The two items install into the same slots as the ordinary piston and injector: `PartInstallation.Slot` sets the slot's own property and a second grade property beside it, so everything that asks only whether a piston or injector is fitted is untouched. Recipes consume Create's Precision Mechanism (pistons and injectors come two to a craft) and need the crude part first. On a straight-12 on gasoline each part is worth 3 to 30 percent of peak power (the head most, as it helps at speed) and all four about 60 percent, before a turbo. Achievements: Tighter Tolerances, Four Valves a Cylinder, Forged in Fire, Common Rail, Blueprinted. |
| Coupled crankshafts | Crank lines that Create's gearing holds at exactly the same speed are one engine. `EngineScanner` finds them by walking the kinetic connections out from the crank (`CrankCoupling`, which asks Create's `RotationPropagator`, by reflection, which blocks are connected and by what speed ratio, so cogwheels, gearboxes, chain drives, belts and bevels all couple, and so does an engine that is standing still). Any crank block at a ratio of exactly +1 or -1 within `maxCouplingWalk` kinetic blocks joins, up to `maxCoupledLines` lines. The lines are put in position order, and the first line's start is the primary: the only generator, with the other lines carrying rotation. Each line has a *sense*, +1 if the gearing turns it the way the first line turns and -1 if the other way, and a cylinder's top dead centre in the engine's frame is its sense times its own, so the simulation needs no knowledge of coupling at all: every cylinder of every line is on the one rotor by the usual formula. Drawing follows, a line being shown at the primary's angle times its sense (Create draws a shaft at time times speed, so no phase offset arises). The Timing Tool plans a reversed line as its mirror image, flipping the sign of both its bore offsets and its throw angles, and a starter turns the engine the way its line's sense gives. Batteries, oil pans, governors and starters touching any line belong to the engine; fuel and oil are one gallery. Goggles name the count. Achievements: In Step (four or more cylinders firing on coupled cranks), Hypercar Money (sixteen firing cylinders). |
| Rotary engines | A **Rotor Housing** is a Wankel: a link of the crankshaft line (it extends `CrankshaftBlock`, so it carries rotation, sets its **eccentric angle** in a value box like a throw, and is found by the same line walk) that is also its own cylinder and its own head. Right-click it with an **Engine Rotor**, a Fuel Injector (or Precision Injector) and a Spark Plug; sneak-right-click removes them. It is plumbed like a head: an intake pipe or Air Intake on one side, an exhaust on another (`AirflowPort`, the scanner counts it as a head on the pipe networks), fuel and oil piped in on any side, shared along the crank like a head's (`GalleryMember`, the interface the head and the housing both implement). There is **no camshaft and no valve**: the ports are cut in the housing, so `CamProfile.ROTARY` times them from the chamber's own cycle angle. The rotor turns at a third of the shaft and a chamber goes through two volume cycles per rotor turn, so a chamber's four-stroke cycle is **1080 shaft degrees** and its cycle angle is 2/3 of the shaft's (`CylinderModel.rotary`, `ROTARY_SCALE`); its three chambers are 360 shaft degrees apart, so a rotor fires **once a shaft turn, as often as two pistons**. The scanner turns one housing into three `EngineLayout.Cylinder` entries (`chamber` 0 to 2; -1 is a piston), so everything downstream (controller, dynamometer, turbos, intake and exhaust networks, MAF sensors, governor, goggles) sees three chambers and needed no change; a housing with no rotor is one disabled entry that drags nothing. Because 720 is not a multiple of 1080, `EngineSimulation` keeps a second angle (`rotaryAngle`, wrapped at 1080) that the chambers are timed from, and a rebuilt engine takes it from the engine it replaces so the chambers' gas stays with the right face. `CylinderSpec.ROTARY` is a chamber 0.65 of an ordinary cylinder's swept volume (`displacement`, a new spec field: gas, work and shot size scale with it), with lower compression (9.5), ports that breathe better than valves (32 against 20, so it keeps its torque at speed where a piston chokes), a slow burn (0.09 s against 0.06) and more wall friction but little inertia. A chamber **never compression-ignites** (`ported` chambers skip autoignition), so the housing needs a spark plug (`CylinderFault.NO_PLUG`) and a battery like any spark engine. `RotorEngine` numbers, on gasoline at 200% metering and a MAF sensor: one rotor peaks at about 2.2 cylinders' worth of power and is flat to 224 RPM (`RotaryEngineTest`). A housing has no cam, so the Timing Tool skips it (set the eccentric angles by hand: 180 degrees for two rotors, 120 for three) and a camshaft's redstone fuel rack does not reach it (use a Governor). The casing is an ordinary block model (open frame, so the rotor shows); `RotorHousingVisual` and `EnginePartRenderers.RotorHousing` draw the eccentric and the rotor, whose centre rides the eccentric and which spins at a third of its angle (`EnginePoses.rotor`, drawn with an exaggerated eccentricity). Bang and exhaust smoke follow the drawn shaft (`RotorHousingBlockEntity.tickClient`). Recipes: brass sheets, a copper casing and a crank throw for the housing, brass sheets and iron for the rotor. Achievements: Going in Circles, Triangle Offense, Valve? What Valve? (`ROTARY_ENGINE`), Apex Predator (`ROTARY_REVS`), Two Triangles (`TWIN_ROTORS`). |
| Cost against output | Output is linear in cylinders: about 3.6k SU a cylinder on diesel and 5k on gasoline at the best speed, 8.5k with precision parts, 12k with a turbo on top. A Create steam engine is 11 items (a gold sheet, an andesite alloy, a copper block) for 16k SU, with a boiler and a burner to feed it. A working cylinder with its crank throw, cam lobe and ports used to cost about 28 items, so to keep the investment honest the cylinder, cylinder head, piston, connecting rod and fuel injector are now crafted two at a time (about 20 items a cylinder). What a large engine buys is not cheaper power per item but power in one compact place with no water and no boiler, which scales to many steam engines' worth on one crank or on coupled ones. |
| Output against steam | The server config `stressPerTorque` is 100 (it was 40). A Create steam engine is 1024 SU/RPM at 16 RPM, 16384 SU, for a boiler and a heat source; combustion is meant to be well ahead of it for the cost of building and fuelling a large engine. `CombustionOutputTest` pins the dyno peak power at this calibration: a four-cylinder diesel is about one steam engine, a straight-12 on gasoline with a MAF sensor about four (roughly 60k SU), a full precision straight-12 about six and a turbo on top of that more than twelve. Refined fuel matters: gasoline is worth over a fifth more than diesel in the same engine. Advancements track it: Heavy Duty is 16384 SU (one steam engine), Steam Is for Kettles is 65536. Existing worlds keep the `stressPerTorque` they saved (40), and need the config value changed to see the new numbers. |
| Names | Display name "Create: Engine Engineer"; mod id `create_engines`; package `com.createengines`. |
| Commits | One commit per major feature. |

### Differences from the proposal worth knowing

- **Cranking is a speed floor, not a fixed speed.** Anything turning the crank (a starter, or another kinetic source on
  the crank's network) holds it at *at least* that speed; the engine may outrun it. This is what lets the engine pull
  away from a starter, and what made a released single cylinder behave correctly.
- **Starters share the crank, and a heavy one is stronger and warms a cold engine.** Every powered starter turning the
  crank the same way as the fastest one (and at least half its speed; a slower one would just be overrun by its clutch)
  pushes on it. The cranking effort is divided by the pushers' total *strength*, and each feels that as stress on its
  own input: a plain starter counts 1, the **Heavy Starter** (brass casing, one plain starter plus a large cogwheel in its
  recipe) counts 2.5, as if geared down, so one runs a big engine off a weak source. While a heavy starter cranks it also
  chokes a cold engine (`Thermal.setCrankAssist`): cold friction drag and the cold burn loss are not felt, held for 8 s
  after it lets go and fading over the next 3 s, so an engine can catch and settle. The "Cold Start" advancement is
  awarded when an engine below `Thermal.COLD_BELOW` catches with that assist on. Both live in `EngineController`'s
  starter block; the heavy starter shares the starter's block entity, renderer and visual.
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
  row above); without one the speed of everything the engine drives follows the load. A Create Rotation Speed
  Controller downstream steadies the output but not the engine's own speed.
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
- **Rotors are planned, with limits.** The Timing Tool sets a multi-rotor engine's eccentric angles (`RotorPlanner`), and rotary firing intervals are only listed in goggles, not checked for evenness by any milestone. A rotor's chamber is a sine of the shaft angle and a real Wankel's is not (its apex seals, side seals and port shapes are not modelled), and there is no apex-seal wear. Rotors cannot be mixed with piston cylinders on one housing, but a crankshaft line can hold both kinds of block.
- **Cam direction is ignored**: only the speed ratio's magnitude is checked, because Create gearing flips direction
  freely.
- **Only an exact one-to-one gearing couples crankshafts.** Cranks geared at any other ratio are still separate engines
  that both feed one network (the faster wins, as Create does for any two sources). Create's own blocks do not tell the
  engine scanner when they are placed, but a player placing or breaking one (a cogwheel, a shaft, a chain drive, any
  `IRotate` block) makes every engine within 64 blocks look again at once (`EngineTopology.gearingChanged`). Anything
  else that changes the gearing (a deployer, a piston, a redstone gearshift that reverses a coupled line) is found at the
  next rescan, at most five seconds later.
- Pipes are arms around a core, with a fitting of their own picked by which sides are open: a plain coupling for a straight run, a cast elbow for a bend, a tee across the run for a branch, and the bulged node for anything else (`core_*` models, chosen in `CEBlockstates.pipeBlockstate`). They are still boxes, not curved.
  The block models are generated by `tools/generate_models.py` from `tools/models/`; edit those and rerun rather than the JSON or the sheets.
  Animated parts (piston, rod, valve, crank, cam) keep the pivot and envelope the renderers expect, with detail inside it.
- **Old saves lose their manifolds' connections.** Manifolds used to be stateless; one placed before pipes existed has no
  open ends until a wrench opens them.
- **Spark timing is fixed.** A spark engine's timing is the cam lobe's angle plus a fixed advance; there is no
  distributor and no ignition-advance dial. There is also no knock: pre-ignition of gasoline at high speed just falls
  out of the compression temperature. (Knock as extra piston wear is approved for the opt-in wear mode, 2026-11-24; its rules
  are in `engine/sim/Knock`. `WearKeeper` adds the wear to each piston that fired early, and the cylinder pings on the client.)
- **No knock, no turbo damage.** Compressing the air heats it, and only an Intercooler cools it again; excess boost shows as pre-ignition in a spark engine and as waste in a rich one, and nothing breaks, however many stages are stacked. Series and parallel are told apart per stage by whether the air can go round it (see *Series and parallel*), not per cylinder: a stage on a side branch next to a clear path to an opening counts as parallel, and two stages on one branch that both have a bypass are treated as a bank and not as a pair in series. A supercharger's boost has no flow limit and no surge. Nitrous is not modelled; variable-geometry turbos and anti-lag are (see *Early game, flywheels, banks, dyno and turbo mechanics*).
- The turbocharger and sump have Ponder scenes and smoke-test screenshots (a real-world turbo and sump). The turbo's wheel speed in Ponder comes from NBT set by the scene, not an engine; a running turbo spinning in a real world has not been watched by hand.
- Series detection is covered by the twin-turbo GameTest (a compound pair must still multiply); detecting a parallel bank in a real built world has only the unit-tested simulation behind it, no GameTest or hand test.
- Not yet tested in-game by hand: wrenching pipes, placing a spark engine by a player, placing and wiring an engine by a player, multi-cylinder builds beyond the Ponder
  scenes, and the fluid pipe to head connection (the head exposes a fluid capability on every face).

### Early game, flywheels, banks, dyno and turbo mechanics (2026-10-31)

The mod's shape: a combustion engine is reachable early as a crude contraption (copper-age parts, forgiving of mistakes)
and is the end-game power source (precision parts, forced induction, big layouts). Rotary engines are a place in the
mix, not a better piston engine.

- **Soot is forgiving.** Plug fouling (`CylinderModel`) builds only from a rich *burn*, falls on every clean burn, and a
  plug that stops lighting scrubs itself a little each cycle it still sparks, so a flooded spark engine coughs and
  stutters instead of locking out. A fully sooted plug still lights up to about 1.4 times balanced.
- **Tuning is one click.** The Timing Tool has a tune mode (sneak-use cycles through the four timing styles and then
  *Tune fuel*). Used on a running engine it sets every injector (`EngineController.tuneFuel`) from the mixture each head
  last burned, aiming a little lean (`TUNE_MIXTURE` 0.9) because a cleaner burn speeds the engine up and it breathes
  less. Heads held by a MAF sensor or a closed rack are left alone. Goggles on a cylinder or rotor housing whose
  mixture is off also suggest a metering figure (`GalleryMember.balancedMetering`).
- **Roughness costs rating.** `EngineSimulation.trackSwing` measures, in the dynamometer's driven runs, how far the crank's
  energy swings over a cycle, and `predictedRipple` turns that into a speed ripple for the engine's real inertia. The
  rated torque is the best of `torque * Roughness.smoothness(ripple)` over the speeds at or below the current one
  (`Dynamometer.ratedTorque`): free below 4% ripple, then one-for-one to a cap of 40%. Mass, more cylinders and even
  firing all lower the ripple, and a faster engine surges less. Goggles on a crankshaft show the cost.
- **Flywheels.** The Create Flywheel (inertia 2.5, `flywheelInertia`) and the Cast Flywheel (iron, inertia 1.6,
  `castFlywheelInertia`) both count when on the crank's shaft line. Mass also costs spin-up and starter effort.
- **Dual-Mass Flywheel** (new in 2.1, a new block, nothing existing moved). Two masses on a spring pack, on the same shaft line.
  The primary mass (`dualMassPrimaryInertia`, 0.45) joins the crank's inertia like any flywheel, so it sets rev response, spool
  and starter load. The secondary mass (`dualMassSecondaryInertia`, 1.4) does not: it joins only the ripple the network sees,
  by `DualMass.isolation(rpm, dualMassResonance)`, a pure function that is 0 at or below the resonance (48 RPM) and rises
  smoothly to 1 at three times it. So it is never worse than a plain flywheel of its primary mass (the weak band loses the
  benefit, not more), it is the weaker part for a slow engine (on a single cylinder a Cast Flywheel of 1.0 smooths more at 32 RPM),
  and it wins for a fast, quick-revving or boosted engine (at 192 RPM its 1.85 total smooths more than 1.0 while the crank
  carries 0.45); `DualMassTest` pins both ends. `EngineSimulation.setIsolatedMass` takes the secondary mass and `predictedRipple` uses the inertia the
  ripple sees; the dynamometer's copy carries it, so the rating, goggles (a source line with the isolated share and the ripple
  seen), Roughness and the governor all read it through those two numbers and nothing else. It is the end-game flywheel: a mechanical
  crafter recipe of two Cast Flywheels (the two masses), a precision mechanism and six brass sheets (the dearest crank-line part), for the fast, boosted build at the top of the ladder.
  **Spring-pack dial** (author, 2026-11-23): each flywheel has one value box, nine settings, `spring_pack` in a blueprint (`SpringPack`,
  pure). The middle setting (4, "Standard") multiplies the secondary mass and the resonance by exactly 1, so an untouched flywheel is
  the part above. One step towards stiff multiplies by a fraction of 1.5 on the resonance and 1.25 on the secondary mass, one step
  towards soft by the inverse: the softest is 32 RPM and 1.12, the stiffest 72 RPM and 1.75. The two move together on purpose, so a soft
  pack helps earlier and less and a stiff one later and more; neither end is better at every speed (`SpringPackTest`), and
  there is no penalty or failure. An engine with several dual-mass flywheels has one resonance, the average of theirs weighted by the
  secondary mass each isolates (`EngineLayout.isolatedResonanceFactor`, exactly 1 with every dial at the middle). Turning the dial
  rescans the engine; the drawn lag follows the flywheel's own resonance; its goggles give the setting, resonance and mass.
- **Variable-geometry turbo** (`VariableTurbochargerBlock`, same block entity): vanes close when the exhaust heat is thin
  (`VANES_OPEN_HEAT`), catching up to half again as much of it, and restrict the exhaust between 0.7 times (open) and 1.3
  times (closed) a fixed turbine's back-pressure.
- **Anti-lag** (a 0 to 3 setting beside the boost limit): while every cylinder on the turbine is off the throttle (fuel
  cut, rack shut, throttle closed or governed back) and the engine is turning, it draws `ANTI_LAG_FUEL` mB per second per
  level from the fuel gallery and adds `ANTI_LAG_HEAT` per level to the turbine. The wastegate was already a boost limit
  dial plus a redstone dump.
- **Bank Builder** (`BankBuilderItem`): use on a crank throw to place a cylinder and head, and fit pistons, rods and
  injectors from the player's inventory, on the clicked face of every throw: inline (that face), vee (and the face a
  quarter turn round the crank), flat (and the opposite face), W (and both faces a quarter turn either side: three banks)
  or X (all four faces). The layout is saved on the item by its place in `Layout`, so new layouts are only appended. Pipes and fuel stay the builder's; spark plugs and the cam line are laid only if the builder carries them (see "Boiler heat, readouts, builder extras, muffler and casing").
  Sneak-use cycles the layouts and then between standard and precision parts: a precision build places precision cylinders, heads, forged pistons and precision injectors, and swaps them in for standard parts already on the throws, handing the old ones back (a worn piston with its wear). Blocked places and missing blocks are reported. It places no cams: a vee's valley camshaft (see *Valley cam lobes*) is added by hand.
- **Dynamometer block** (`DynoBlock`): set against any block of an engine, goggles draw its torque and power curves as
  bars, with peaks and the powerband, from the rating the engine itself uses. Redstone rises as the engine climbs to the
  speed of its peak power.
- **Rotary balance.** Pinned in `RotaryNicheTest`: a rotor is weak at low speed next to the cylinders it is worth at high
  speed, about half again thirstier than precision pistons, and runs out of breath sooner at high speed. Its recipe now
  needs a precision mechanism, the same cost as a precision cylinder, so a rotor is a mid-game compact option that
  precision pistons with boost still outclass.
- **Art.** See "Art" below; this pass's first textures have been replaced.

### Art (2026-11-08)

The models stay simple boxes in Create's manner, built by `tools/generate_models.py`, and use Create's own casings and shafts.
`tools/generate_textures.py` draws everything else, and its metals are built the way Create's are, from Create's colours: a dark
outer rim, a bright inner rim lit along the top and left, and an inside of broad low-contrast strokes (diagonal on brass and
copper, horizontal on iron and steel). Vanilla's copper, magma, lava, smooth stone and trapdoor are gone from the models, and water's
textures from the fluids, which now have a thin and a thick greyscale set tinted in `CEFluids`. The advancement tab has its
own tread plate. `tools/preview_models.py` renders models to a contact sheet without starting the game.

A version that baked per-face pixel art with ambient occlusion onto stepped voxel circles was tried first and thrown away on the
user's word: it looked like 3D pixel art rather than Create. Keep detail in the texture and silhouettes blocky. Do not add stepped
circles or per-pixel noise, and do not posterise to a few colours.

### Config surface, a refining chain and exhaust heat (2026-10-31)

- **Server config** (`CEConfig`, `[balance]` and `[features]`): `fuelConsumption`, `precisionBonus`, `boostStrength`,
  `rotaryPower`, `heatRecoveryOutput`, and switches for `forcedInduction`, `sparkFouling`, `oilDilution`,
  `rotaryEngines` and `heatRecovery`. The simulation is plain Java and never reads the spec (a unit test has no loaded
  config), so `CEConfig.onConfig` copies the values into `engine.sim.Tuning` on every load and reload and the sim reads
  those. Defaults are the shipped balance, pinned by `TuningTest`. Fuel consumption is charged in
  `GalleryFuelSupply.draw` (the tanks pay `mb * consumption`, the engine still burns `mb`), so it never changes power.
  `precisionBonus` blends each `PRECISION_*` factor toward 1 (`Tuning.blend`), `rotaryPower` scales a chamber's
  displacement and shot, `boostStrength` and the induction switch are applied in `EngineSimulation.applyInduction`,
  and a switched-off rotary housing carries the `ROTARY_DISABLED` fault, which says so in goggles. The saved
  `stressPerTorque` of an existing world is still its own.
- **Client config** (`CEClientConfig`): `engineParticles`, `bangVolume`, `effectDensity` (scales every `EffectRate`
  interval). Particle and sound code in the cylinder, rotor housing and turbo block entities reads it.
- **Fuel chain.** Plant oil (seeds, pressed) and the coal route to diesel are unchanged and stay the crude early game.
  New: Sugar Mash (sugar cane, beetroot or potatoes pressed with water), Ethanol (mash and an apple or any mushroom, heated, 250 mB to 50 mB),
  biodiesel (plant oil, 25 mB ethanol and bone meal, heated: 300 diesel against the coal route's 250, and 50 Glycerin),
  and a better engine oil from plant oil and glycerin. Ethanol is a spark fuel with the highest autoignition point
  (3.4 against gasoline's 2.7), so boost-heated charge will not light it early, but it carries 27 energy per mB against
  gasoline's 40. Gasoline is cracked from diesel with blaze powder in a heated basin, no longer superheated, so it costs no more to refine than
  diesel does. Ethanol is the dear one: 250 mB of mash and an apple or mushroom make only 50 mB (it was 100), and biodiesel takes 25 mB of it a batch, so the late-game precision boost builds that want its autoignition margin pay for it in crops. The ferments_mash item tag (apple, brown and red mushroom, c:mushrooms) lists what ferments it. Plant Oil also presses from two saplings (50 mB), alongside six seeds (100 mB), two sunflowers (50 mB), four cocoa beans (50 mB) or six dried kelp (50 mB).
- **Superheat payoff (2026-11-13).** Nothing in the fuel chain needs a superheated basin to work, but two recipes pay for
  one, which is the late-game reward for a big engine's Exhaust Heat Exchanger (about seven cylinders at speed):
  `mixing/ethanol_superheated` (250 mB mash and a ferment item make 70 mB of ethanol, twice the heated yield; the heated recipe now needs a sugar as well)
  and `mixing/engine_oil_recycled_superheated` (400 mB used oil and charcoal make 400 mB of engine oil, 1:1; the heated recipe now
  needs a clay ball as well and makes 250). **Why the extra sugar and clay ball:** Create's `BasinOperatingBlockEntity.getMatchingRecipes` ranks the
  matching recipes only by how many item ingredients they have, most first, and ignores heat; a heated recipe still
  matches over a superheated burner. A superheated recipe with no more items than its heated twin would tie or lose
  (the used-oil coking recipe has no items at all), so each superheated recipe carries one more item than the heated one
  it improves on. `FuelRecipeGameTests` pins this: the superheated recipe is the unique top match at a seething burner,
  absent at a kindled one, and the heated recipe still runs when the extra item is missing. Keep this in mind for any
  future heated/superheated pair: give the better one more items. **Exception, used oil recycling and ethanol (the author's choice):** here
  the superheated recipe has the fewer items (charcoal; the ferment item) and the heated one more (plus a clay ball; plus a sugar), so superheating is
  the easy way. The cost is that a clay ball or sugar left in a superheated basin makes the heated recipe run; the tests pin that too.
- **Exhaust heat exchanger** (`HeatExchangerBlock`, a sensor on an exhaust network). The engine hands it its network's
  share of `CylinderModel.exhaustHeatLastTick` per second (about 3 per cylinder at speed, split between every exchanger
  on the network); it smooths that over about a second, turns it into a level with `HeatRecovery` (smouldering 1.5,
  kindled 6, seething 20) and writes the level to the block's `BlazeBurnerBlock.HEAT_LEVEL` property. Create's basin reads
  the block two below it from that property every tick, so no mixin is needed: a basin on an exchanger runs heated recipes
  from about two cylinders at speed and superheated ones from about seven. Heat recovery costs the engine nothing.
- **Ponder pages.** `heat_exchanger` (the exchanger, a basin and mixer on top, the three heat levels) and `fuels` (the
  whole refining chain at a pressed, a heated and a superheated basin). `fuels` is also attached to the plant oil, diesel,
  gasoline, sugar mash, ethanol and glycerin buckets, so every block and item in the mod now has a page.

### Ethanol's place: the fuel boost cannot light early (2026-11-13)

A dyno sweep of a 12-precision engine found gasoline and ethanol exactly parallel at every boost (ethanol 89 percent of
gasoline's power from unboosted to full stack), because charge temperature never cost gasoline anything: ethanol's 3.4
autoignition point was a claim with no mechanic behind it. Three changes give it a real job without taking gasoline's.

- **Pre-ignition costs gasoline power, never the plug.** `FuelSpec.preignitionLoss(chargeTemp)`: a spark engine's charge
  that is over its fuel's autoignition point when it lights loses 1.0 of the burn's heat per ambient temperature of
  excess, capped at 35 percent (`CylinderModel.startBurning`, scaled by boost over `PREIGNITION_FULL_BOOST`, 0.5 atm).
  Scaling by boost is deliberate: a charge runs hotter with speed alone (a crude gasoline engine reaches 3.1 at 256 RPM
  unboosted), and that is not the boost's doing, so an unboosted engine never pre-ignites. Nothing fouls and nothing is
  damaged. `CycleStats.preignition` and `CylinderStatus.preignition` carry it to the goggles ("Charge lights early: N
  percent of the burn lost", sneak for the cure).
- **Ethanol cools the charge it boils into** (`FuelSpec.chargeCooling` 0.08 ambient temperatures at an equivalence of 1,
  `CylinderModel.evaporativeCooling`, set at each injection from that shot's equivalence and used by the next intake
  stroke): denser air, which closes most of ethanol's power gap, and a cooler charge further from gasoline's line.
- **Ethanol leaves no soot** (`FuelSpec.sootFactor` 0): a rich ethanol burn never fouls a plug, though a mixture too rich
  to light still will not light.

What the dyno shows now, 12 precision cylinders, best speed up to 256 RPM:

| Build | Gasoline | Ethanol | Winner |
|---|---|---|---|
| Unboosted | 101.8k | 97.1k | gasoline |
| One turbo | 179.0k | 172.9k | gasoline |
| Two turbos, no intercooler | 217.9k | 284.9k | ethanol, by 31 percent |
| Turbo and supercharger, no intercooler | 204.7k | 247.7k | ethanol, by 21 percent |
| Two turbos and an intercooler | 330.7k | 312.4k | gasoline |
| Two turbos, supercharger, intercooler | 256.9k | 246.5k | gasoline (ethanol is injector-bound) |

**Ethanol's pre-ignition point (2026-11-17).** The 3.4 above was too close to what a fast engine reaches without any
boost (a precision cylinder compresses to 3.2 at 512 RPM), so a precision V16 on twin turbos and an intercooler lost up
to 35 percent of its burn on ethanol, which is the build the fuel is for. Ethanol now has its own pre-ignition point,
`FuelSpec.preignitionTemp` (`ETHANOL_PREIGNITION`, 5.0; fuel data map `preignition_temperature`, optional and defaulting
to the autoignition point, so every other fuel is unchanged). Its autoignition point stays 3.4: that still decides
compression and pilot ignition and what the fuel tooltip says. Measured on one precision cylinder with twin turbos and an
intercooler (`EthanolBoostTest`): worst charge 3.9 at 512 RPM and 4.8 at 640 RPM, no loss on ethanol at either, against
35 percent lost on gasoline. A crude, unprecise engine pushed to 512 RPM still reaches 4.9 or more and can still lose
some on ethanol.

Gasoline stays the usual fuel: it is about 1.6 times cheaper by volume everywhere and wins wherever the charge stays
cool, which an intercooler does. Ethanol is the fuel for a hot boosted build, or one that cannot fit an intercooler.
The fuel data map gained optional `cold_sensitivity`, `charge_cooling` and `soot_factor`; before this change it dropped
each fuel's cold sensitivity (every shipped fuel read as 1, against the 0.8, 1.5 and 1.8 this document gives), so cold
starts now differ by fuel as documented. Advancement: Cool Under Pressure (`ETHANOL_BOOSTED`).

### Valley cam lobes (2026-11-01)

- **One lobe, several cylinders.** A Cam Lobe that touches more than one working cylinder (a lobe in the valley of a vee,
  against the cylinder block of each bank) now times all of them. The lobe's angle sets the *first* working cylinder it
  touches, in scan order (the same order the Timing Tool plans in); every other cylinder on it gets the same timing error
  as the first, firing the same distance from its own top dead centre, on whichever crank turn puts it within one turn
  after the first. Before this a shared lobe had one angle for everyone, so cylinders that did not reach top dead centre
  together were mistimed and each bank needed its own camshaft.
- **Where it lives.** `EngineScanner` records, per cylinder, `lobeLeaderTdc` (the first cylinder's top dead centre, NaN for
  the first or for a lobe with one cylinder). `CylinderModel.shareLobe` turns that into a forward and a reverse offset;
  `EngineSimulation.setDirection` tells every model which to use, because reversing the crank mirrors the offset like it
  mirrors all timing, so a running engine stays right either way. The animation reads the offset from
  `CylinderStatus.lobeShift` (the cylinder block entity and the head's valve stems).
- **Planning.** `FiringPlanner` treats a cylinder that shares a lobe as following its leader: it has no revolution of its
  own to choose, only the leader does. The old "mistimed" count is gone; a shared lobe cannot be mistimed, only forced
  to fire its cylinders within a turn of each other, so one lobe for a whole four cannot spread them evenly (a lobe per
  throw can, and a V8 with a lobe in each valley still fires every 90 degrees).
- **Feedback.** The lobe's goggles say it times the first cylinder and the others follow (when it works more than one),
  and the cylinders' own goggles show their timing error as before. Advancement: *Cam in the Valley*
  (`VALLEY_CAM`, a running engine where a shared lobe times firing cylinders that top out at different crank angles).
  The `compact_v` Ponder page now builds the V around one valley camshaft.
- **Not done.** A running engine that is reversed keeps working, but the lobe dials were set for the way it ran when
  timed. (The Bank Builder now lays the valley camshaft for a builder who carries one.)

### Balance and power-creep pass (2026-11-01)

Goal: a combustion engine is a viable, rewarding power source at every point of a Create game, and the top of the ladder
stays a top. `ProgressionBalanceTest` pins it in steam engines (16384 SU); `CombustionOutputTest` still pins the lead
over steam. Measured with the dynamometer, MAF-trimmed shots, Create's 256 RPM limit, `stressPerTorque` 100:

| Stage | Build (12 cylinders unless noted) | Power, steam engines | SU per mB/s |
|---|---|---|---|
| Andesite age | 1 crude cylinder, plant oil | 0.18 | 8.8k |
| | 6 crude cylinders, plant oil | 1.1 (a steam engine, with no nether trip) | 9.0k |
| | 12 crude, plant oil / diesel | 2.2 / 2.7 | 9.0k / 10.1k |
| Refining | 12 crude, gasoline | 3.7 | 12.2k |
| | 8 crude, ethanol (vs gasoline 2.5) | 2.2 | 7.2k |
| Brass age | 12 crude, gasoline, one turbo | 6.6 | 13.1k |
| Precision | 12 precision, gasoline | 6.2 | 13.4k |
| | + one turbo | 10.9 | 14.4k |
| End game | + second turbo and injector flow | about 18 | 14.9k |
| | + intercooler and supercharger on top | about 19 | 14.2k |
| | 16 cylinders, two turbos | about 25 | 13.7k |

What the pass found, and changed:

- **Ethanol was a dead end.** Injectors were sized in mB, and ethanol carries 27 energy per mB against gasoline's 40, so
  it needed 1.5 times the shot for the same air, hit the injector's limit at ambient pressure, and starved under any
  boost (one turbo: 69 percent of gasoline's power; two: it collapsed). `FuelSpec.injectorFlow()` (reference air per mB
  1.5, so 1 for gasoline, diesel and plant oil and 1.5 for ethanol) now scales both Fuel Metering's 100 percent and the
  injector's limit (`CylinderModel.usualShot`, `injectorLimit`). Ethanol now makes about 89 percent of gasoline's power at
  one turbo and 90 percent at two, burning more by volume, and costs no blaze powder or superheating. Datapack fuels get the
  same rule from their own `air_per_mb`. A hand-metered ethanol engine now wants roughly two thirds of the dial it did.
- **The precision injector was the weakest precision part** (4 percent alone, against 29 for the head, 12 for the bore
  and 6 for the piston) while the injector's limit was what capped boost: two turbos on crude injectors ran lean (0.72
  equivalence), and a 16-cylinder with four boost stages misfired lean at low speed. A precision injector now has
  `CylinderSpec.PRECISION_INJECTOR_FLOW` 1.3: the limit of its shot is 30 percent higher. Fuel Metering and every
  engine that is not short of fuel are unchanged (no existing tune moves); a MAF sensor and boost are what use it. Two
  turbos with precision injectors make 15 percent or more over two turbos on plain ones. This is the only change that
  raises a ceiling: a full-house 12 went from 15.8 to about 19 steam engines.
- **Boost stacking now flattens instead of falling off.** The combinations the advancements ask for (turbo, intercooler,
  supercharger, twin or compound turbos) are each a step up on their own. A fourth stage (two turbos, an intercooler and a
  supercharger) is worth about 15 percent less than three, because even a precision injector cannot feed it; the fuel
  economy of the full stack is also about 40 percent worse than one turbo's, so the ceiling is paid for at the pump.
- **Left alone on purpose.** Early engines are strong and cheap to run already (a crude 12 on plant oil burns about 4 mB/s,
  under 20 seeds a minute for 2.2 steam engines). Per input item, plant oil to diesel and gasoline are within
  about 20 percent of each other and ethanol (50 mB a batch of mash since 2026-11-10) is deliberately the dearest, so the fuel ladder buys power density, not economy. Turbo and supercharger are the best
  power per build cost and sit in the brass age; precision parts cost about three times a crude cylinder for 1.7 times the
  power and are the late polish. Rotary stays below a precision piston engine (four rotors against eight precision
  cylinders), and RPM is not a creep route: every build peaks at or below 256 and loses power past it, so raising Create's
  limit does not raise ratings. Knock still does nothing in the sim (gasoline and a fuel that never autoignites rate
  identically), as decided.
- **Not done.** Build cost was estimated by hand (iron-ingot equivalents) rather than a pinned table; a real playtest of
  boost builds with precision injectors and a MAF sensor is still wanted. Hand-metered engines cannot use the injector's
  extra 30 percent (the dial tops out at 200 percent); only a MAF sensor reaches it.

### Service port, oil condition, temperature, pilot injection and achievement tracks (2026-11-01)

Design rules agreed for this round (since changed: 2.3 added the opt-in wear mode, and 2.4 a soft power loss from overheating, in both
modes, and heat damage in the wear mode): nothing wears out or breaks (no part damage, no overheating failure); the only
penalties are soft and recoverable (a cold engine drags, spent oil drags a little, an uncooled engine simply earns no
bonus); anything an engine needs besides air and main fuel must arrive through one block, not through the heads' scarce
faces; and anything that needs doing regularly (an oil change) can be automated with Create pipes alone.

- **The Sump is the service port** (`SumpBlockEntity`). Four tanks behind one fluid capability on every face: Engine Oil
  (1000 mB), Used Oil (500, filled only from inside), Coolant (500: Engine Coolant or plain water) and Pilot Fuel (250: any
  fuel that autoignites at 2.0 or less, `CEDataMaps.isPilotFuel`). `fill` sorts a fluid into the right tank wherever it
  comes in; only Used Oil can be drained, by pipe or bucket, so a pump on the pan can never empty the oil or coolant. An
  engine finds its pans through `layout.sumps()`, as before. Existing pans keep their oil tank and gain the rest.
- **Oil condition** (`Lubrication`): a third figure beside film and dilution, 1 new to 0 spent. Turning spends it,
  `AGE_PER_REV` 1/3000 of a charge a revolution at stress 1; stress is 1 for a warm, clean engine, grows 1.5 per unit of
  temperature above normal and 2 per unit of fuel dilution, and an oil filter takes it times 0.5 (`Lubrication.stress`).
  Spent oil multiplies all friction by up to 1.25 (`drag`), never more, so it costs rating and nothing else. Fresh oil
  swapped in closes the gap by `1 - exp(-mB / 150)` and cleans dilution by the same share (`refresh`). A filter also
  halves ageing, which is all it does: it never clogs.
- **Oil exchange** (dial on the sump: off, 0.25, 1, 4 mB/s while the engine turns). `SumpBlockEntity.exchange` moves oil
  from the oil tank to the Used Oil tank; the engine calls it every tick and refreshes its condition by what moved. It
  stops when the used tank is full or the pan has no oil, so the used oil has to be piped somewhere. Settled condition at
  128 RPM is 0 with the exchange off, 0.57 on a trickle, 0.89 steady and 0.97 flushing, and a filter lifts those to 0.79, 0.95
  and 0.99 (`OilConditionTest`). The player chooses what
  happens to used oil: it is a poor fuel (22 energy, autoignition 1.85), heated with charcoal and recycled into oil (400
  to 250 mB), coked into charcoal (250 mB to one charcoal, which feeds the coal route to diesel), or let go from an open
  pipe. Plant oil with glycerin, already a recipe, is the slime-free oil, and glycerin also makes coolant, so one seed
  farm and the biodiesel chain supply fuel, oil and coolant.
- **Units a player reads** (2.4). Every temperature is in degrees Celsius with ambient at 20, and every amount of heat is in HU, heat
  units (HU/s for a rate), the way stress is in SU. One HU is one unit of the simulation's own heat, so nothing is scaled. The block's
  temperature converts as `20 + 100 x T` (`Thermal.degrees`). A charge or ignition temperature is absolute with ambient as 1, so it
  converts from kelvin, `293.15 x T - 273.15` (`Thermal.chargeDegrees`). Data maps, the add-on API and script values stay in the
  simulation's own units, so packs and scripts are unaffected. A new readout uses these two conversions and these unit names;
  `HeatUnitsTest` fails a shipped string in kelvin or with an unnamed heat rate.
- **Temperature** (`Thermal`). One figure for the whole block, 0 ambient, 0.7 a warm engine on a steady load. Heat in is the
  exhaust heat the cylinders already compute for the heat exchanger; heat out is `PASSIVE x T^2` per cylinder (so an engine
  settles at the square root of its heat, a crawling engine stays warm enough and a boosted one runs hot rather than
  white hot) plus coolant. Coolant carries `100 x quality x (T - 0.4)` heat per cylinder per second and is spent in
  proportion to the heat it carries (0.02 mB per unit at the best quality, twice that for water, which carries half as
  much per mB); the pan's coolant is used up like water boiling off (a Radiator is the closed loop, below). Quality is 1 for Engine Coolant and
  0.5 for water. The thermostat at 0.4 stops coolant from ever making an engine cold. A cold engine (below 0.3) drags up
  to 25 percent; an engine below 0.65 earns the cooling bonus, all of it by 0.45: its charge temperature drops by up to
  3 percent of ambient, which is about +9 percent net torque on a crude engine, +6.5 on precision parts and +4.5 with a
  turbo (`Thermal.MAX_COOL_CHARGE`, scaled by the `coolingBonus` config). The bonus is applied in
  `EngineSimulation.applyInduction` after the intercoolers. Heavier engines gain less (a flat-out boosted twelve earns
  about 4.5 percent on proper coolant and water barely helps), so the good coolant is for the big builds. The dyno copies
  the temperature it finds and holds it (`setThermalLive(false)`), so the rating includes both the cold drag and the bonus;
  the rating key rounds both to quarters so warming up re-rates only a few times. A bare `EngineSimulation` is warm and
  its thermal model is off, so every existing unit test is unchanged; the controller starts a new engine cold and turns it
  on, and `Motion` carries temperature and condition across rebuilds and saves.
- **Coolant and used oil are fluids** (`CEFluids.COOLANT`, `USED_OIL`), tinted water like the others.
- **Pilot injection** (`CylinderModel.pilotSupply`, a `FuelSupply` from `SumpPilotSupply`). A cylinder with no spark plug
  whose main fuel is still below its autoignition temperature after the ignition delay tries a pilot: if the pilot fuel
  autoignites at this temperature and the pans can give `PILOT_SHARE` (8 percent) of the shot, the charge lights. The burn
  is `PILOT_BURN_SLOWDOWN` (1.15) slower than a spark-lit one. Pilot fuel is drawn from the pans a whole millibucket at a
  time, with the fraction kept as credit, and the pans are scanned once a tick. A diesel pilot lights gasoline and ethanol
  from 32 RPM up (the speeds tested) and makes about the power of a spark engine (`PilotInjectionTest`). A cylinder with a plug always uses the
  plug; a rotor chamber never self-lights, so it still needs one. A pilot fuel that is itself hard to light (ethanol) lights
  nothing. The dyno copies a cylinder's pilot supply as unlimited.
- **Config.** Switches `oilAgeing`, `engineTemperature`, `pilotInjection` and balance `coolingBonus`
  (`Tuning.oilAgeing`, `engineTemperature`, `pilotInjection`, `coolingBonus`). Off, oil never ages, the block is always
  warm and a pilot lights nothing.
- **Goggles and Ponder.** A crankshaft block shows oil condition, whether a filter is fitted, the temperature in words, the
  coolant being spent and a note when a pilot shot is lighting the fuel; the sump shows its exchange, filter and every tank.
  The `sump` scene gained four pages (exchange, used oil and filter, coolant, pilot fuel) and `fuels` one (the loop).
- **Achievement tracks.** The tree keeps its shape and gains six capstones, one per track (`master_builder`,
  `power_plant`, `fine_tuner`, `full_driveline`, `closed_loop`, `service_record`) and `chief_engineer`, which waits for the
  union of their milestones. A capstone is a single advancement with one criterion per milestone, so the advancement screen
  shows the progress and no event hook is needed; a player who reached a milestone before the capstone existed is credited
  when they reach it again. New rows: `oil_exchange`, `used_oil`, `waste_oil`, `oil_filter`, `oil_kept_fresh`,
  `warmed_up`, `coolant_used`, `cool_running`, `dual_fuel`, `steam_herd` (8 steam engines) and `steam_titan` (16). The
  oil-trivia rows `engine_oil` (Oil Change), `well_oiled`, `dry_run` and `oil_spent` were later cut as redundant with the
  oil pan; `sump` now hangs from `diesel`. Milestones are in `EngineMilestones.reachedService` and the `STEAM_*` ladder,
  both unit tested.
- **Not done, and not verified.** The Minecraft-side code (sump, controller wiring, goggles, recipes, Ponder, advancements,
  lang) could not be built or run when this was written, because the build environment could not reach the NeoForge and
  Create Maven repositories; only the plain-Java simulation (`engine.sim`, the planners, `EngineMilestones`) was compiled and
  tested, with a hand-made harness (283 tests). Run `./gradlew build runGameTestServer` and the visual smoke test before
  relying on any of it, and run `./gradlew runData` once: the generated advancement and lang files were written by a script
  that reproduces the datagen output (it matched all 74 existing advancement files byte for byte), so datagen may reorder a
  few lines. The recipe JSON (coolant, recycling, coking, filter) follows Create's mixing format from the existing recipes
  but has not been loaded by the game. The V and coupled-crank reward for engines past eight cylinders was discussed and left
  out. The `anOilSumpOilsTheWholeEngine` GameTest now checks gasoline instead of water, which a pan takes as coolant.
- **Radiator (2.4).** `engine/sim/Radiator`: each one sheds `160 x quality x (T - 0.4) x airflow` HU/s from the whole block (not per
  cylinder), together never more than would bring the block to the thermostat in one tick. Airflow is 1 in still air, plus up to 1.5 from an
  Encased Fan blowing at it (all of it at 256 RPM) and up to 2 from the structure's speed (all of it at 20 blocks a second), capped at 3. It
  boils coolant (0.15 mB per HU at the best quality) only at `Wear.CRITICAL`, in both modes. Its basin heat is its smoothed shed
  times `heatRecoveryOutput`, with the exchanger's thresholds and its on switch, capped at heated, for basins and boilers alike. A heat exchanger does not count a radiator
  beside it as an outside heat source.


### Failure sounds (re-made 2026-11-27)

The wear mode's failures and the dry piston's warning are synthesized by `tools/failure_sounds.py`. A recording of a breaking
engine has no clean pitch: struck metal is many unrelated modes that die at once, weight is low noise, scraping is uneven
stick-slip through a few broad resonances, a leak is fluttering noise, and no gap repeats. Held or sweeping tones, long rings
and regular rattles are what made the first takes sound like a cartoon (`FailureSoundsTest` guards against the tones).

### Turbo and supercharger sounds, and torsional flex (2026-11-02)

- **Sounds** (reworked 2026-11-26). Synthesized by `tools/generate_sounds.py` (which falls back to ffmpeg's libvorbis
  when there is no libsndfile, and takes sound names to regenerate only those), these three from `tools/boost_sounds.py`.
  `turbo_whistle`, a one-second seamless loop: a core tone at 2 kHz that drifts a little, with bright overtones at 4 and
  6 kHz, a narrow band of noise that makes it breathe, a hollow note an octave down and a wide rush of air that reaches
  to the top (the high end is the first recording's, kept at the author's request). `supercharger_whine`, the same length,
  made to be heard for as long as a shaft turns: the lobes' tone at 520 Hz with soft overtones, a growl of copies 48 Hz
  either side, a low thrum, a soft whir of air and a trace of the drive gears' mesh near 1.4 kHz (the author preferred it
  to a close stack of overtones with no tone of its own). `turbo_blowoff`, three takes under the one sound ID, is air
  alone: a full rush, most of it between 400 and 1500 Hz with a hiss over it, cut off below 220 Hz (a thump at the bottom
  was heard as a kick drum, and a thin hiss alone as a shaker), that within a fifth of a second is fluttering as the
  compressor surges. The flutter is that one stream swelling and easing, soft at both ends and never shut off (hard,
  separate bursts were heard as parts striking). Under the air the wheel whines, a little: a thin tone falling from
  3.3 kHz toward 1.5 kHz, sagging and swelling with each surge, some 12 to 17 dB under the rush.
  The takes start at 18 to 25 swells a second and slow toward 10.5 to 14.
  `MachineLoopSound` (client only, like `StarterWhirSound`) plays a loop for any block entity that implements `LoopSource`:
  it fades in and out and takes its volume and pitch from `loopLevel()` and `loopPitch()` every tick. The turbo's loop level
  eases in from 6 percent of its wheel speed and its pitch is 0.4 plus 0.7 times that speed, up to 1.24
  (`Pitches.turboWhistle`); the supercharger's level is its shaft speed
  over 96 RPM (a third as loud when it feeds no engine) and its pitch 0.6 to 1.8 by 200 RPM. The blow-off plays when redstone
  holds the wastegate open, or when the engine lifts off the throttle (`TurboStatus.offThrottle`, at most once a second), with
  at least 0.25 bar on the gauge a moment before, louder for more boost. `BlowOffCrowd` (pure, one count for blow-offs and
  one for wastegate chuffs) keeps many valves from adding up: of those within 10 ticks and 16 blocks of each other, the
  first plays in full, the next two at 0.5 and 0.3, and the rest not at all (`BlowOffCrowdTest`). Anti-lag pops are the
  backfire (`afterfire`), quieter and higher, at half the rate of the particles. The client config `boostVolume` scales the
  whistle, whine and blow-off; `bangVolume` still scales the pops. The sounds are checked from spectrograms and tests
  (`BoostSoundsTest`). The author heard a first version of the rework and rejected parts of it; what followed it has not
  been heard. If one is off, adjust its constants and regenerate it by name. A change to a loop goes into `BoostWhine` too.
- **Torsional flex** (`engine.sim.Torsion`). The rating loses `0.025` per throw beyond eight on a crank line (it was 0.045 to at most 0.45 until 2026-11-02, when a V24 and V30
  were rated below a V16; now at most 0.35),
  and an engine with several lines loses the average of the lines' losses weighted by their throws, so a short line
  does not hide a long one. Throws, not cylinders, are counted, so a vee or flat (two cylinders to a throw) puts a V12 at six
  throws and a V16 at eight, both free, where a straight twelve (12 throws) loses 10 percent and a straight sixteen 20; a V24 loses 10 and a V30 17.5.
  Two coupled straight eights lose nothing. It is applied to the rated torque and the dynamometer curve in
  `EngineController`, as roughness is, and never to the running engine; a rotor's eccentric counts as a throw. The controller
  finds the lines by walking the layout's crank blocks, which the scanner lists line by line, and starting a new line
  wherever a block is not the next one along the axis. The server config `torsionalFlex` scales it (0 off, 2 doubles it,
  never more than 0.8 off). Goggles on a crankshaft show the loss and the longest line, with a hint when sneaking; a
  Ponder page was added to the V engine scene; an advancement, Stiff Upper Crank (twelve or more firing cylinders with no
  flex), joins the builder capstone. The sim-level balance tables (`CombustionOutputTest`, `ProgressionBalanceTest`) measure
  the engine without a layout, so they are unchanged; in play a straight twelve is rated a tenth lower than those tables
  say, which is the intent.


### Flight recorder and the engine note (2026-11-02)

- **Black Box** (`content.blackbox`, `engine.FlightRecorder`). A block set against any block of an engine, as the dynamometer
  is. It remembers the crank it found, and searches its faces again only when that crank no longer answers, when a block beside it
  changes, and every ten samples (a pipe broken further along changes neither). Once a second the server reads the engine's synced `EngineSummary` (from the crank it touches, or that crank's
  primary) and hands a `Sample` (state, speed, load, rated capacity, temperature, fuel, lubrication) to a pure-Java ring of
  600 samples, ten minutes. A change of state is logged as an event with the cause the figures before it point to: a stall
  with no fuel injected is `NO_FUEL`, with the oil gone `DRY`, with a load above capacity `OVERLOAD`, else `UNKNOWN`; going
  overloaded is `OVERLOAD`. The tape is saved with the block (so it survives a restart) and boiled down to 40 columns every
  other second for the goggles, which draw speed, load (against what the engine could give) and warmth (against the 0.7 a
  warm engine sits at) as rows of bars, a row of state shades, and the last few events, more when sneaking. Only the
  boiled-down readout goes to clients. It records only while it touches an engine, and a gap is not marked. An advancement,
  Black Box Thinking, is awarded when a running, coasting or overloaded engine is logged stalling. It was built for the
  questions "why is it cold" and "why did it stall", which the simulation knows and the player could not see.
- **Engine note** (`content.EngineNoteSynth`, `EngineNoteSound`). The primary crankshaft's client tick starts one streamed
  voice per engine (at most three near the player, within 40 blocks) that the sound engine fills from a synthesizer instead of
  a file: the sound instance overrides `getStream` (a NeoForge hook) to return a mono 22.05 kHz stream that renders a tenth of
  a second per read, and `sounds.json` marks `engine_note` as `stream`, the placeholder file named there never read. The
  synthesizer walks a 720 degree phase at the engine's speed and strikes three bandpass resonances (92, 205 and 470 Hz) at
  each crank angle in `EngineSummary.firingAngles`, with a short noise crack for the snap, so the pattern of the engine's
  own firing order is what is heard. Firings that did not light (the share of cylinders not firing) are an eighth as loud;
  load brightens the upper resonances and the crack; a coasting or cranking engine only thumps at about a quarter strength.
  Loudness is divided by the firing rate so a V16 at speed is not a wall. Speed and loudness are followed over about a
  tenth of a second, and the stream queues about three tenths ahead, so the note trails the engine by a few tenths. While the
  note plays the cylinders' (and rotor housings') recorded bangs are skipped: the bang is in the note, as a thump (a
  sine dropping from about 150 to 46 Hz) and a low rumble struck by the same firing as the resonances, so it lands with
  the note by construction. A sample played from the cylinder runs a few tenths of a second ahead of a stream and was
  never going to line up. The client config `engineNoteVolume` scales it (0 off, recorded bangs back at full). The recordings in `build/engine-notes` come from
  `EngineNoteSynthTest.rendersDemoRecordings`. The levels and spectra were measured, and the visual smoke run with the sound
  on showed the sound engine accepting the voice and pulling its stream with no errors, but the sound itself has not been
  listened to by the author of it. A new voice is given 20 ticks to start before it is judged declined (master volume at zero
  declines every one) and only then replaced, so a muted client does not build a synthesizer every tick.
- **Delivery (sound plan step 1, 2026-11-26).** This replaces the tenth-of-a-second reads and the queue figures above.
  - *The queue.* A note's stream is read a twentieth of a second at a time, reads sized so
    they do not drift against the ticks (`EngineNotePcm.lengthOfRead`; at step 1's 22.05 kHz that was 1103 and 1102
    samples), with 0.15 s queued ahead of what is being made. A
    hitch longer than that runs the stream dry, and the game then drops the note. One that had played for at least two
    seconds is replaced at once (`VoiceBudget.lost`), the room voice standing in meanwhile; a younger one, or one the sound
    engine declined, waits out the budget's two-second rest, so no engine starts more than one voice in that time. The
    sting keeps tenth-of-a-second reads. A read is now 1600 samples exactly, at the 32 kHz of the step 2 note.
  - *Looking ahead.* Speed and load are carried along their trend by the queue plus the synthesizer's own follow, about
    0.24 s (`EngineNoteDrive`, `NoteLookahead`): only after two moves the same way, at most 15% past the speed and 0.15
    past the load. A single step is not extrapolated.
  - *Gains in the stream.* The source's volume stays at `0.9 * engineNote`. The fade, casing, settling and the chorus's
    ceiling are glided sample by sample (`EngineNoteMix`). A faded note plays on four ticks so its queue is heard out.
  - *Blowers.* An engine with a note plays its turbos' whistles and superchargers' whine in the same stream (`BoostWhine`,
    the recordings' recipe made live, at their level; up to four, `NoteWhines`), at the level and hearing range each would
    have from where it stands, but from the note's place. Their loops cross-fade out and remain for engines without a
    note, and for any past four. The note plays the pitch the blocks ask for without holding it to 0.5 to 2.
    The turbo's client sync names its engine (`Engine`, never saved).
  - *Handovers.* A note and the room voice cross-fade at equal power over 16 ticks (`NoteHandover`), the room's copy
    seeded as the note is and timed as a latecomer to a running stream. A note is held at least 3 s before a nearer
    engine (by 6 blocks) may take it. The room mix is untouched below 0.8 of full scale.
  - *Where it is heard.* From the middle of the engine's exhaust openings, each weighed by its run's muffling
    (`ExhaustCentre`; a cylinder's status syncs `ExhaustMuffle` when above zero), or the crankshaft until they are known.
  - *The room's own tick* works the scene out when no engine calls in (`EngineRoomScene`, `TickOnce`), so the shared voice
    falls silent once its engines are gone.
  - Tests: `NoteDeliveryTest`, `EngineNoteTimingTest`, `SoundSyncGameTests`.
- **Grains and pipes (sound plan step 2, 2026-11-26).** This replaces the three fixed resonances, the made-up crack,
  knock, chuff and rumble, and the single pipe echo described elsewhere in this section. The note is made at 32 kHz.
  - *Grains.* `tools/engine_grains.py` synthesizes single firings into `assets/create_engines/engine_grains.bin` (176
    grains, 500 kB, nothing recorded): the blow-down's body by ignition, 3 cylinder sizes and 3 loads, the flame front's
    crack by size, and a diesel's double knock, 8 takes of each. `EngineGrains` reads them once. A firing strikes the
    same take of the two loads either side of its own, never the take its outlet struck last (`nextTake`). Strikes are
    overlapped into a ring (`GrainBus`), so a sample costs the same however many firings overlap.
  - *Pipes.* Each outlet is a waveguide (`ExhaustPipe`): a delay line with an inverting, low-passed return from the open
    end, tuned to `86 Hz * pitch(size, pipe)` and the outlet's detune, so a run rings that note and its odd harmonics.
    What comes round falls to about a third in 16 ms on every length (at most 0.88 a pass). The mouth takes some lows
    off. A muffler is in the loop (the return rolls off from 700 Hz and less comes back), and muffled outlets also go
    through the muffler filter and level as before (`NoteExit`).
  - *Beside the pipes.* Crack and knock do not ring the pipe; they fade with the firing rate, and with distance and
    casing, as before. Under it all are the thump of each firing, a faint noise that swells twice a crank turn, and the
    tone at the firing rate for fast engines. `NoteExit` holds the last stage: levelling, muffler filter, dulling.
  - *Level and cost.* On the 30 reference renders the note is within 1.3 dB of step 1's at every one, about 0.6 dB quieter
    on average. `EngineNotePerfTest` measured 136 to 246 ns a sample against its 600, at 32,000 samples a second.
  - Tests: `EngineGrainTest`, and every earlier note test unchanged.
- **One-shots in the note (sound plan step 3, 2026-11-26).**
  - *Backfire and spit.* An engine with a note plays them in its own stream (`NoteBangs`). A backfire is a big blow-down
    that opens over 6 ms, a soft crack, dull burning gas (130 to 1100 Hz, down the pipe and straight from the mouth) and a
    thud that drops from above the pipe's note to under it, then none, one or two weaker ones 55 to 155 ms apart. A spit is
    a small blow-down and one soft breath that darkens from 2600 to 650 Hz. Neither clips, and little of either is above
    3 kHz (retuned 2026-11-27: the author found them too sharp, wrong in character and late; the backfire had risen in
    0.3 ms into hard clipping and over half the spit was above 2 kHz). They have a pipe and a last stage of their own, so
    the note itself is sample for sample what it would be without them. One waits for the engine's next firing, for at most
    a tenth of a second, and rings that outlet's pipe with its muffler. Which cylinder's flame set it off is not followed:
    it goes out of whichever outlet fires next. The stream is made ahead of what is seen, so the bang is heard up to about
    a quarter of a second after it is asked for, and its flame and sparks wait for it: the sound thread says on the bang's
    cue (`NoteCue`) when it is heard, and `content.effects.HeldFlashes` shows them then, to within a tick, or after half a
    second if the note was lost. An engine without a note plays `exhaust_pop` or `exhaust_spit` at once, with its flash.
  - *A stall's end.* When the wind-down ends, the note rocks its own crank to rest, three blow-downs and a sigh, as hard as
    that kind of stall fetches up (`EngineNoteGesture.Kind.slam`, `sigh`). `engine_settle` plays only if the note was lost
    part way through. A note started on the same tick as a catch comes in at once, not faded, and the note is now started
    before the catch is looked for, so a catch does not play as a recording under a note that is just starting.
  - *Recordings.* The pop, spit, catch, stall and settle recordings are made from the same grains and a port of the pipe
    (`tools/engine_pipe.py`); `SoundAssetsTest` holds the generator's figures to the Java ones.
  - *Level pass.* Every recording that plays once has a loudness of its kind's own, a peak ceiling, no DC and a fade at
    each end (`tools/sound_levels.py`, which owns the figures). They were set against offline renders of the note, not
    against a capture from the game.
  - *Pitch.* A one-off strays at most 3% either way from its pitch (`content.effects.Pitches`). Pitches that tell you
    something (the take a catch or stall picks, how near a dry piston is to seizing) are as they were.
  - Tests: `NoteBangsTest`, `NoteBangShapeTest`, `HeldFlashesTest`, `SoundAssetsTest`.
- **The world around the note (sound plan step 4, 2026-11-26).** It works on what a note's stream and the room voice play,
  after everything else, so the synthesizer is as it was. Recorded sounds and loops are left to the game. In clear air
  within 8 blocks and out of doors, the stream is sample for sample what it was.
  - *Air.* A two-pole low-pass (`NoteAir`), from 12 kHz just past 8 blocks down to 700 Hz at the note's 32-block reach,
    on top of the synthesizer's own dulling with distance. It takes no level of its own.
  - *Walls.* Once a second for each engine, one line from the camera to where the engine is heard
    (`NoteSurroundings.walls`). The walls are the blocks' length of it that runs through full blocks, to the nearest whole
    one, so a one-block wall at a slant is one and a clipped corner none. The last 1.5 blocks at the engine's end and half
    a block at the listener's don't count. Leaves, anything smaller than a block and unloaded chunks let sound by. One,
    two and three or more walls put the filter at 1400, 750 and 450 Hz and pass 0.7, 0.55 and 0.45 of the level; the
    duller of the air's filter and the walls' is used. The change glides over about a quarter of a second. A blower's
    whine carried in a note is muffled with it.
  - *Room.* Once a second, 14 rays from the camera, 12 blocks long (`NoteSurroundings.room`). It is a room as far as most
    of them hit something and so do the five that point up, steeply enough that a pit's walls don't count as its roof.
    The reflection (`NoteEcho`) is two taps, the second 1.6 times as late and 0.6 as loud, low-passed at 3 kHz, with
    nothing fed back. The first comes after the rays' mean there-and-back time (8 to 50 ms) at up to 0.3 of the engine's
    level, and the whole is turned down by what they add, so indoors is no louder. Each stream has its own; a change of
    delay cross-fades.
  - *Moving engines.* Nothing of this mod's. Sable already sets a velocity on every sound placed on a moving structure,
    so the game's audio library shifts its pitch (`MovingSoundInstanceDelegate`); a second shift would double it. A
    structure's own blocks are not seen as walls. The room voice is now placed by where its engines are in the world, not
    by where their blocks are kept. None of this has been heard on a moving structure.
  - *Cost.* Measured by `NoteSurroundingsTest`, which holds the filters under 60 ns a sample and a second's looking (six
    engines and the room) under a millisecond.
  - Tests: `NoteSurroundingsTest`.
- **Deep engines (2026-11-26).** The author found no build that rumbled as a V8 should; the nearest was a W24 on very long
  manifolds. A V8 was voiced as eight small cylinders (higher pipes and thumps, a brighter crack), and at idle each
  firing died before the next.
  - *Size.* `EngineNoteVoicing.size` now takes a sixth off for each doubling of the cylinder count, not a quarter: 1 for
    a single, 0.83 a twin, 0.67 a four, 0.5 a V8, 0.33 for sixteen.
  - *Boom.* Each outlet has a slow resonance (`NoteBoom`) near 55 Hz, a little lower on a longer run, which every firing
    sets going and which falls to about a third in 70 ms. How much a build has is `EngineNoteVoicing.depth`: none up to
    two cylinders, half at four, all from eight. It is there to carry one firing to the next, so it goes steeply as
    firings pass about 28 a second.
  - *Lope.* A firing is as strong as its own outlet's wait for it, within 0.7 to 1.35 (`EngineNoteVoicing.lope`). An
    outlet that fires evenly leaves every firing at 1, so only banks that fire unevenly change, as on a crossplane V8 with
    a bank to each exhaust.
  - *Top end.* A deep engine's crack is up to 40% weaker.
  - *Measured offline*, idling at 80 to 240 RPM: a crossplane V8 on short manifolds has about 68 to 72% of its sound below
    120 Hz, where it had about 43%. Singles and twins have no boom, and a single is as it was. Fours and up are all
    lower than before. The sound bench has recorded it in game (step 5 of the sound plan); nobody has
    judged it by ear.
  - Tests: `EngineNoteDepthTest`, and every earlier note test unchanged.
- **Kinds of engine (2026-11-27).** The author found engines too alike and the note fake or synthetic: every build was the
  same pipe over the same thump. Two things were added, and nothing about timing or rhythm changed.
  - *Rush of gas* (`NoteFlow`). Each firing sends noise (350 to 2600 Hz) down its outlet's pipe, opening over 2.5 ms and
    falling to about a third in 9 to 22 ms by cylinder size, more under load. It is turned down as firings crowd.
  - *Resonances* (`NoteTimbre`, `NoteBody`). What leaves the pipes goes through two band-passes that do not move with the
    engine's speed, beside some of the sound as it was; the boom and the thump go round them. Where they sit, and how much
    rush and thump there is, goes by the kind of engine: by how many firings it lists (1, 2, 3, 4, 5, 6, 8, 10, 12, 16 and
    up), a burble for six or more firings whose banks fire unevenly (a crossplane V8), and a buzz for a rotary. A longer
    mean run moves both down and a stub up; unequal runs lower and dull them; compression ignition lowers both and rushes
    less. The table is in `NoteTimbre`.
  - *Rotary.* A twin rotary fires as an even four does, so the server now says when every cylinder of an engine is a rotor
    chamber (`EngineSound.rotary`, synced as `SoundRotary`, written only then; nothing saved changes).
  - *Measured offline* at 420 RPM, in the bands that carry sound: a crossplane and a flat-plane V8 were 0.1 dB apart and
    are about 7; an even four and a rotary went from 1.7 to about 6, a four and a six from 2.5 to about 5. A flat four on
    unequal runs is still close to an inline four (about 2). Level is within about 3 dB of before, and most builds are
    brighter above 1 kHz. Nobody has judged any of it by ear.
  - Tests: `NoteTimbreTest`, `EngineSoundTest`, `RotaryGameTests.aRotaryEngineStartsAndRunsOnGasoline`.
- **One continuous sound (2026-11-27).** The author found few of the kinds sounded good, the idling crossplane V8 best.
  Against openly licensed recordings of idling engines from Wikimedia Commons (two crossplane V8s, a flat-plane V8, a
  straight six, a flat four; used only to measure, none ship), the difference was the space between firings. A real
  engine's level there fell about 4 to 13 dB; ours fell about 40 to 70 dB above 250 Hz, a click and a clean ring each time.
  The crossplane V8 had the smallest fall and the least top, nearest the recordings.
  - *The system* (`NoteSystem`), between the pipes and the resonances, so the kind of engine colours all of it. Four
    all-pass chambers (4 to 17 ms) spread each pulse. A *roar*, uneven noise mostly 70 to 420 Hz, follows each pulse's
    power, falls with the time between firings (12 to 200 ms), and never goes under a tenth of the usual power. Two
    one-pole low-passes then take off the top. How much roar and where the top goes are in `NoteTimbre`.
  - *Crack and knock* go through a low-pass near 2.6 kHz. *Casing* now filters at 640 Hz instead of 1350 Hz, because the
    darker note had less top for it to take. A diesel's go through one near 1.4 kHz (`NoteRaps`).
  - *Heard bare.* The author heard the roar at low speeds as bitcrushed: sparse noise wavering about 70 times a second.
    Below 10 firings a second it is now at 0.35 of its strength, without its higher grain and nearly steady, coming in
    fully by 50 a second; its strength also moves no faster than about 6 ms. The rush of gas is turned down likewise.
  - *Variation.* Even a smooth engine at load now varies a fifth in strength from firing to firing and up to 0.7 ms in
    time, so its harmonics are less needle-sharp.
  - *Three kinds.* The author named the straight six, the crossplane V8 (far too quiet at speed) and the diesel six. The
    six's resonances came down from 520 Hz to 340. A burble keeps its deep voice at idle and opens between 16 and 42
    firings a second (`NoteOpening`, `NoteTimbre.opened`): the low resonance rises a little, the high one grows, and the
    deepest bass (`NoteBody`), the boom, the thump and the tone are thinned. A diesel has a low body of its own near
    155 Hz, more roar and more clatter, instead of only sitting lower. The six changed least: it still has more at 350 to 500 Hz than the recording, and darker, it
    could not be told from a four.
  - *Limits.* More roar than this hid the crack, the firing's timing and a muffled bank, which the note tests check.
  - *Next round.* The author found the six and the V12 still a bit bright, the crossplane V8 still too bassy, and the
    twin rotary like a diesel truck. The six's and the V12's resonances moved down, with lower top rolloffs and less roar:
    each is about 7 dB weaker above 1 kHz at 1100 RPM. The V8 loses more bass as it revs; at 420 RPM it can change
    little, because opened further there it can't be told from a flat-plane (`NoteTimbreTest`). Against two rotary
    recordings from Wikimedia Commons (an RX-7 and an RX-8), the rotary was noise from 250 Hz to 2 kHz. It now has much
    less roar and rush, resonances at 380 and 1700 Hz instead of 850 and 2100, and is about 7 dB weaker above 1 kHz. It
    still has less than the recordings from 60 to 250 Hz: given that, it could not be told from a four.
  - *Whistle.* The author then heard a high-middle whistle in the V12: at 1100 RPM one tone at 440 Hz, its fourth
    firing harmonic on a pipe overtone with the resonance moved to 480 beside it, held about half the note's power. The
    V12's resonances are now at 300 and 750 Hz with more heard dry. The tone is 5 dB weaker. Resonances that cut it
    further left the V12 mostly bass and much quieter to the ear, or too like a four. Above 1 kHz it is now about 5 dB
    weaker than before the round above.
  - *Measured offline.* At the idles rendered, multi-cylinder engines fall about 10 to 25 dB between 250 Hz and 4 kHz,
    a single and a twin more. The new stage costs 25 to 40 ns a sample by the run, about a quarter on top of
    a note; the in-game sound bench and soak have not been run on it. The author judged offline renders round by round;
    none of it has been heard in game.
  - Tests: `NoteSystemTest`; the earlier note tests as they were, none changed.
- **Revs heard as pitch (2026-11-27).** The author asked for revving to sound like revving, without sounding like one
  recording sped up. Engines mostly turn at 256 RPM at the most, so a four fires about 9 times a second there and a V12
  26: a rhythm, not a note. The pitch heard was that of the pipes and resonances, which stood still. Between idle and
  256 RPM the spectrum of an offline render did not move at all.
  - *The rise* (`NoteRev`). Pipes, booms, both resonances, the roar's band, the top rolloff and the thump move together,
    4 semitones for each doubling of speed from 64 to 256 RPM (about 6.7 from 80), and, as the author then asked, 2 for
    each doubling from 256 to 2048. Past 256 the boom and thump stay, as the engine's weight. Catches and stalls follow
    it too.
    Firing times, crack, knock, casing, mufflers and distance do not move. This supersedes the resonances standing still
    in *Resonances* above.
  - *Anchored at 256.* There the voice is exactly as tuned before. Anchored at idle instead, so that it only rose above
    that tuning, seven of the earlier note tests failed (the V8's low share, casing and distance dulling, and kinds told
    apart among them). So idles are now deeper.
  - *Uneven banks past 256.* They open up with speed already (*One continuous sound*), a V8 until about 630 RPM, and
    take up the rise past 256 only from there. Raised on top of the opening, or with the boom and thump rising past 256,
    `NoteTimbreTest` could no longer tell kinds apart at 420 RPM (3.8 to 3.9 dB where 4 is asked).
  - *Measured offline.* At 90 RPM the new voice lines up best 5.4 to 6.2 semitones under the old one for a four, a six
    and a crossplane V8 (6 asked for); the match is loose, as what stays still is in it too, and the V12's was unclear.
    At 256 RPM old and new line up unshifted. The slow single and four are mostly separate bangs, where the change is
    harder to see on a spectrogram than on a six or V12. Held at 512 and 1024 RPM against the voice as tuned before, the
    average frequency moved +0.4 and +4.0 semitones for the four, +6.2 and +4.1 for the six, +1.5 and +1.7 for the V12,
    and -0.4 and +0.7 for the crossplane V8: it moves unevenly there, as the firings' own tones stand still and the
    resonances pass over them. At 1024 the six has one tone at 308 Hz with 58% of its power (18% on its strongest
    before), which may be heard as a whine. Nobody has judged it by ear, in a render or in game; the sound bench and
    soak have not been run on it.
  - Tests: `NoteRevTest`; the earlier note tests as they were, none changed.
- **Sounds on moving structures (2026-11-26).** Sable plays any sound that starts on a moving structure inside a wrapper
  of its own, so `SoundManager.isActive` says no to the instance this mod made. Every note, loop and starter whir there
  was taken for dropped and started again over itself, about every second or two, until the game had no sound channel
  left. Whatever asks now goes through `MachineLoopSound.stillPlaying`, which asks about the wrapper
  (`SableCompat.playedAs`, `SableSoundBridge`). Test: `SoundStillPlayingTest`, which reads the source, as the unit
  tests have no game.
- **The build's voice (2.4 sound rework).** `EngineNoteSynth.Voice` says what the
  engine is built of: which exhaust outlet each firing goes out of (up to four, `MOST_OUTLETS`), each outlet's pipe length,
  spark or compression ignition and how much of it is precision parts. The valves make no sound (author, 2026-11-26).
  Each outlet rings its own three resonances, pitched by `pitch(size, pipe)` (1 at a middling cylinder on a middling run, the
  old 86/172/345 Hz) and set a little apart from the other outlets with a slightly different weight, so a bank's uneven rhythm is
  heard through the even rhythm of the whole: a crossplane V8 with a bank to each outlet stops repeating at each firing, and a
  flat-plane repeats every two (`EngineNoteVoiceTest`). Compression ignition adds a double rap near 2.7 kHz on each firing,
  loudest at idle and fading to 15% at speed. Each firing varies in strength and lands up to a few degrees late (never more than 6 ms,
  `MOST_LATE`), more below half load, with `Params.roughness` and with crude parts; seeded per engine, so the same build renders the same samples. The
  outlet count is worked out on the game thread in `set`, so the audio thread still allocates nothing. `Voice.PLAIN` (one
  middling outlet, spark, all precision) is the note as it was, for a summary that carries no sound
  (an older world). The server works the voice out as `engine.EngineSound` and syncs it in the summary (new keys `SoundOutlets`,
  `SoundPipes`, `SoundCompression`, `SoundPrecision`, `SoundEnclosure`, `SoundMuffles`, written only for an
  engine that has them): `EngineExhaust.computeSoundBase` gives each cylinder the outlet of its exhaust run (in the order cylinders meet
  them, a fifth run sharing the fourth outlet), each outlet the length of its run (`PortNetwork.run`, the blocks the gas
  goes through from a head to the nearest opening, averaged over the heads, through `EngineSound.pipeLength`: a lone opening
  is a stub, thirteen blocks a long run, by the logarithm of the blocks, so the first pipes move the pitch most; `pipeTone`
  rings a stub an octave above a middling run and a long run only 0.4 of an octave below it, about six semitones from one block
  to two, because a long run taken lower dropped under what is heard and left only grit) and its own muffling, compression ignition when
  most cylinders have no spark plug and the share of
  precision cylinders; `refreshEnclosure` looks every two seconds at how many of the cylinders' sides are Engine Casing. Each
  firing angle carries its outlet through the summary's sort. The client feeds `Params.roughness` from the summary's roughness
  (0.4 is fully rough) and `setDistance` from the listener (over the note's 32-block reach). Casing dulls the note (a low-pass from 9 kHz down to 1.6 kHz) and
  takes up to a quarter off its volume; distance takes the low-pass to 1.2 kHz and the crack and knock down to a fifth.
  A run's length is heard three ways: the outlet's resonances span over an octave from stub to long run, the thump of a
  firing out of it is pitched with them, and toward either end of the range one faint reflection comes back down the pipe (3 to
  12 ms, none at middling length, never fed round again: a repeating echo was heard as reverb). The intake roar stays the sampled loop heard from the intake mouth, which the note cannot place; the
  recorded bangs give way wherever the note is heard (`EngineNoteSound.heard`).
- **Keeping time.** The note runs at the drawn crank's speed, which Create rounds and republishes only on a 4% change, so
  it does not slip against the flashes; if the shaft is capped far below the engine it runs at the engine's speed and does
  not follow the cam. Each cam reading carries the time it was taken (`EngineNoteSynth.sight`), and `EngineNoteClock` works
  out when the samples being rendered will be heard. The sound engine reads four chunks at once when a stream starts, then
  one at the first tick after each has played, so a read can be late but never early: the clock dates the stream from its
  first read, keeps to the earliest answer after that (letting it slide at most 0.5 ms a read) and starts over after a
  stall or pause. An unlocked note takes up the engine's full speed before its first aim. The firing flash is checked once a
  tick, so it looks half a tick's travel ahead of the cam and lands either side of the firing, not only after it. Simulated
  offline against a perfect cam, a single's firings are heard within about 15 ms of it from the first one; the sound card's
  own delay is not in that figure, and nobody has judged it by ear. A reading moves the cycle half way, and only two in a row far off make it jump. A rotary has no cam to read and
  runs on its own clock.
- **Many engines (`EngineChorus`, the scene in `EngineNoteSound`).** Every running engine in earshot calls in each tick and the
  scene is worked out once a tick: the nearest three have their own note (a free voice goes to the nearest engine without
  one, and a note gives way to an engine at least 6 blocks nearer), the nearest three of the rest are
  mixed into one shared voice (`EngineRoomMix` in a streamed `EngineNoteSound.Room`, the game's fall-off off and each engine's
  reach in its own gain, placed at the loudness-weighted middle of its engines), and any further ones only make it fuller
  (`EngineChorus.fill`, as their powers would add, at most twice). `EngineChorus.ceiling` scales every engine so all of them
  together are at most 1.6 times (about 4 dB) the loudest alone. `EngineChorus.Settle` drifts an engine held within 3% of its
  speed and 0.06 of its load about 3 dB down over half a minute and back within a quarter of a second of a change; warnings
  are separate sounds and never lowered. A slot's engine is faded over three ticks before the slot is given to another. An
  engine in the shared voice is aimed at its cam's position as one with its own note is. The
  shared voice is the room's own stream, so it costs one more voice and up to three synthesizers on the sound thread. `EngineNoteVoiceTest.rendersTheReferenceSet` writes the roadmap's reference engines to
  `build/engine-notes/reference` (idle, mid, high); nobody has listened to them yet. A voice with every layer on costs about 150 ns
  a sample against `EngineNotePerfTest`'s 600.
- **Clicks and loudness.** The first note was a click track: a firing was one sample into resonances that rang for six
  thousandths of a second, so each was a spike (crest factor up to 15, a jump from sample to sample up to 13 times the
  level). A firing is now a pressure pulse smoothed over a thousandth of a second, into resonances at 86, 172 and 345 Hz
  that ring for about 14, 9 and 5 thousandths however the pipe is tuned (they were 20, 12
  and 7, and longer on a low pipe), with a lowpassed chuff for the rush of gas; the same measures are now a
  crest of 2 to 6 and a step under the level. `EngineNoteSynthTest.noFiringIsAClick` holds that. The whole note is about
  six decibels louder. Past about a hundred firings a second (a four at 3000 RPM, a V8 at 1500) the narrow resonances
  stopped lining up with the pulses and the note fell to half its level in a step, and then sounded thin; the firings
  themselves, softened, now come in as a tone at the firing rate, and a slow gain (0.3 s) holds the level across the
  range, only for fast engines so a slow engine's lope is untouched (`theNoteDoesNotChangeCharacterAtAnySpeed`). The
  turbo whistle (0.09) and
  supercharger whine (0.2, lowered again to 0.14 in the 2.4 rework) are quieter than before, because a tone in the kilohertz carries much further to the ear than
  a rumble of the same level.


### Transmission removed in 0.4.0, restored before 1.0 (2026-11-13)

- The **Transmission** (thirteen gears, reverse and Auto, a Create split shaft) was removed on 2026-11-13 and shipped
  that way in 0.4.0. A player then asked for it back: it is compact, and its Auto mode shifting for you was the part
  they missed. It came back whole (block, block entity, `AutoGearbox`, `Gears`, recipe, Ponder page, goggles, model,
  advancements and tests), as the same block with the same ID. Worlds that loaded on 0.4.0 lost the block, and nothing
  migrates it.
- **Why it was removed:** Create's Rotation Speed Controller sets the output speed whatever the engine does, so what
  the machines downstream see is stable. **Why it is back:** that does not shift by itself to suit the engine, and a
  controller is a bigger build than one block on the shaft.
- The **Governor** was removed in the same sweep and put back the same day. It is not redundant: it holds the engine's own
  speed by moving the fuel rack (or the throttle in a spark engine), which a Rotation Speed Controller after the crank
  does not do.
- **How to apply:** the Transmission stays. Full Driveline asks for every gear again (its parent is the Transmission's
  Gear Up).

### Livelier engines, and water (2026-11-09)

- **Water.** Engines never work underwater (the user's ruling: no snorkels). A cylinder, head or rotor housing with a
  fluid against any face is `SUBMERGED` (`EngineScanner.portFaults`), and the controller gives a submerged cylinder
  no intake or exhaust flow whatever its pipes reach, so it never fires and only drags. Separately, an opening whose
  mouth is in a fluid counts as shut (`PortOpeningBlock.isFlooded`, counted in `PortNetwork.flooded`): a network with
  no clear opening and at least one flooded one gives `INTAKE_FLOODED` or `EXHAUST_FLOODED` instead of the covered
  faults, so goggles say which it is. Cylinders, heads and housings rescan when a fluid arrives at or leaves a
  neighbour (`EngineTopology.fluidMoved`). An engine in a dry pocket walled off from the water
  runs, intake and exhaust mouths included, because nothing is against it. That is the 1.0 ruling (the user's, 2026-11-13):
  an engine works wherever its own faces are dry (`anEngineInADryPocketKeepsRunning`).
- **Blocks are wash-proof.** Every block is registered through `CEBlockstates.part`, which adds `forceSolidOn()`:
  crankshafts, cams and pipes have shapes too slight to count as solid, and flowing water used to break them and
  drop them as items. Found when the submerged GameTest's water took the crank journal out.
- One advancement, That's Not Air, for cranking an engine that is underwater either way. GameTests:
  `submergedCylinderNeverFiresWhateverItsPipesReach`, `sinkingARunningEngineStallsIt`,
  `floodedIntakeNeverFiresUntilItIsDrained`, `floodingTheExhaustStallsARunningEngine`, `waterWashesNoEngineBlockAway`.
- **Exhaust** (`content.effects.ExhaustLook`, pure and unit-tested, drawn by `EngineEffects.exhaust`). One look per
  exhaust stroke from the cylinder's last cycle and the engine's figures: clean haze that grows with load, black by how
  far past rich the mixture is, white while the block is below `Thermal.COLD_BELOW`, blue with oil dilution, a pale mist
  for unlit fuel, a darker thicker cloud for thirty ticks after catching. A rich engine working hard licks flame; unlit
  fuel into the pipe of a warm, otherwise firing engine pops three times in ten (`exhaust_pop`, on the bang volume: five takes,
  each a blow-down down the note's own pipe with burning gas and a thud, some with a smaller pop or two, made by
  `tools/exhaust_pop_sounds.py`; an engine with a note plays its own, see "One-shots in the
  note"). A charge boost lit early spits from the pipe with orange
  flashes (`exhaust_spit`, on the mechanical volume: a soft breath of burning gas and a small puff, from the same file);
  `preignition_knock` is only the wear mode's ping at a cylinder.
  Each firing leaves by one outlet picked at random and throws fewer puffs past four cylinders, so particles do not
  multiply with outlets or grow in step with cylinders.
- **Particles** (`CEParticles`, `client.particle`). `gas` takes a colour, size, opacity, lift and lifetime, swells
  and fades, and is drawn from four white puffs made by `tools/generate_textures.py` in the manner of vanilla's
  campfire smoke (vanilla's own are grey, so they cannot be tinted white, and its small smoke set shrinks to a speck).
  The game's particle shader discards anything fainter than 0.1, so no look starts below about 0.25.
  `exhaust_flame` and `spark` are drawn along their velocity (`StreakParticle`; the arithmetic is `FlameMotion`, pure
  and unit-tested). A flame tongue is four frames of its own that cool from pale yellow to a sooty brown and break into
  rags, drawn out into a jet while fast; a spark is a streak as long as it is fast, which bounces and skids off blocks,
  goes out in water and flickers at the end. `ExhaustFire` composes them: a lick of flame is a glow at the mouth,
  tongues at spread speeds and now and then an ember; a backfire is a flash, a cone of flame, three sparks
  and a dirty puff. The photosensitive setting drops all of it. Gas puffs sway more as they age.
- **Around the engine** (`EngineEffects`). A wisp of air drawn into an intake on each intake stroke; bubbles from any
  flooded mouth and off the wet faces of a submerged cylinder while the crank turns; rain boiling off a head above temperature 0.35, and a shimmer over a running one
  above 0.62 (so coolant shows); sparks off crank throws below 30 percent lubrication and off a starter's pinion; a
  burst of white air when a blow-off valve opens.
- **Machinery sounds** (`EngineAmbience`, kept by the primary crank on the client; levels in `MechanicalNoise`, pure).
  `engine_catch` on CRANKING or STALLED to RUNNING and `engine_stall` on a turning engine reaching STALLED, each at most
  once in three seconds; `bearing_squeal` (below 30 percent lubrication) as a `MachineLoopSound` loop (since 2.5.0 a grind with a short rough chirp each turn, not the held 2.45 kHz shriek it was); `metal_tick` at random for about twenty
  seconds after a warm engine stops, from a heat figure the client lets decay. All on the new `mechanicalVolume`
  setting. Recordings are synthesized by `tools/generate_sounds.py`, which can now write WAV previews (`--wav DIR`).
  The catch and the stall are synthesized by `tools/start_stall_sounds.py`: uneven firings of the note's grains down its
  pipe. `StartStallTake` (pure) picks the set, three takes each, and its pitch and loudness. A catch is
  `engine_catch_hard` (many coughs, a slow ragged catch) when the block is cold (below `Thermal.COLD_BELOW`) or the
  starter had turned it 3 s or more, else `engine_catch`. A stall is `engine_stall_lugged` (hard, slowing firings, a
  heavy stop) from OVERLOADED, `engine_stall_starved` (a long thin sputter) when the cause is no fuel or too lean, else
  `engine_stall`; a flooded engine uses the plain one, and water counts before overload. Pitch falls with cylinders,
  compression ignition and cold; loudness rises with cylinders, and a stall's with the speed it was last burning at.
  Those recordings have a fixed rhythm, so they play only for an engine without a note of its own. One with a note
  performs the take itself (`EngineNoteGesture`, pure), so the firings keep its speed and firing order: a catch changes
  how hard the first firings hit and which miss (for at most 1.5 s, or 2.5 s for a hard one, and at least one firing); a
  stall winds the note down evenly from the speed it was last playing (0.9 s, lugged 1.2 s, starved 1.7 s),
  then the note rocks the crank to rest itself (see "One-shots in the note").
- **Checking it.** `./gradlew runVisualSmokeTest -PsmokeEffects --offline` builds a cold, a rich, a flooded and a dry
  engine and screenshots each effect (about a minute and a half).

### Warm-up, cooling, health and troubleshooting (2026-11-10)

- **One temperature.** `Thermal.temperature` stays the single figure per engine; everything below reads it, nothing
  keeps a second one. `Thermal.isCooling` (stopped, still above `COOLING_FROM` 0.12) is the "cooling" state, exposed as
  `EngineSummary.cooling()` and shown in goggles in place of cold/cool/warm. `Thermal.glow` (0 at 0.5, 1 by 0.85)
  drives the red turbine wheel in `TurbochargerRenderer`, through `TurboStatus.heat`; the compressor stays dark, and
  exhaust pipes do not glow (they are plain block models). The cooling ticks (`EngineAmbience.cool`) now follow the real
  temperature through `Thermal.tickChance` instead of a decay the client kept itself, so they fade with the goggles,
  and a cooling head shimmers faintly down to 0.5.
- **Warm-up gameplay.** A cold block quenches the flame: `CylinderModel.coldness` (set each tick from `Thermal.cold()`)
  scales heat release by `Thermal.burnFactor`, up to `MAX_COLD_BURN_LOSS` (12 percent) times the fuel's new
  `FuelSpec.coldSensitivity`: gasoline 0.8, diesel 1, pressed oil 1.5, ethanol 1.8 (a datapack fuel is 1). It never
  stops an engine and is gone by the cold line, 0.3. Heat made at idle is helped while the block is cold
  (`WARMUP_ASSIST`, fading to nothing at a cylinder's full-load share). A heat source against an exhaust heat exchanger
  (`HeatExchangerBlockEntity.externalHeat`, read once a second from `BlazeBurnerBlock.getHeatLevelOf`, other exchangers
  ignored) puts `Thermal.setPreheat` heat into a stopped or running block: smouldering settles at 0.28, kindled at 0.42,
  seething at 0.56, never as warm as a running engine. No cooling system was added. A fuelled burner is what reaches
  kindled; a bare one is smouldering.
- **Why, in plain words.** `engine.Diagnosis` maps a cylinder's faults and last cycle to a `Cause` (build, water, no
  air, no cam, no fuel, no spark, no plug, no exhaust, no ignition, lean, rich, fouled) and rates the engine
  (`assess`): Healthy, Running rough or Failing, with the cause hitting most cylinders (ties to the more fundamental
  one), dry oil, uneven firing (the existing roughness loss, `ROUGH_LOSS` 5 percent) or a cold block. It is computed in
  `EngineReadouts.diagnose`, carried in `EngineSummary` and shown as one line and its cause under the engine state.
  A cylinder's goggles add a "Why:" line for a misfire. A cylinder left out of the simulation for a missing part counts
  as one that is not firing. A stopped engine with a cause is Failing: it cannot run as it is.
- **Dyno comparison.** `DynoBlockEntity` keeps the last whole curve it saw and, when the engine's curve starts being
  measured again, files it as the run to beat (saved with the block). `sim.RunComparison` (pure) reads peak torque, peak
  power, the speed of the power peak and the powerband width off the two curves; goggles show percent and absolute
  change, green for gains.
- **Loop sounds.** `LoopSource` gained `loopSpinUp`, `loopSpinDown` and `loopCoasts`. `MachineLoopSound` lags level
  and pitch behind the source by those rates, and a source that coasts keeps its loop alive until its own spin has run
  down, so a stopped supercharger or turbo winds down in pitch and volume for several seconds. The supercharger comes up
  slowly and coasts longest. Other loops are unchanged. The belt chirp on engagement was left out: it needs a recording.
- **Ponder.** Two pages, `troubleshooting_start` ("Why Won't It Start?", on the starter, battery, head and air intake)
  and `troubleshooting_running` ("When an Engine Runs Badly", on the crankshaft, cylinder and exhaust outlet), told on the
  spark structure in `TroubleshootingScenes`, in the same words as the goggles causes.
- **Advancements.** Block Heater (`PREHEATED`: an engine caught with a heat source already warming its block) and Slow
  Burn (`HARD_START`: caught stone cold on ethanol or pressed oil, then warmed through), both in the service track.
- **Checks.** Unit tests: `DiagnosisTest`, `RunComparisonTest`, and new cases in `ThermalTest` and
  `ServiceMilestonesTest`. GameTests: `aBlazeBurnerAgainstTheExchangerPreheatsTheEngine`,
  `anEngineWithNoIntakeReadsAsFailingForWantOfAir`.

### Superchargers brought level with turbos, and the Variable-Drive Supercharger (2026-11-13)

A supercharger used to lose to a turbo with anti-lag on nearly everything, with one niche (low load) to its name. The
rule now is that each blower class is worth what its turbo class is, for different reasons: **Supercharger = Turbocharger,
Variable-Drive Supercharger = Variable-Geometry Turbocharger.**

- **What each is good for.** The turbo's boost is free (no shaft to turn), peaks in the middle of the rev range, and
  scales with the engine's own output; it pays in lag, back-pressure, exhaust heat it must be fed and, with anti-lag,
  fuel. The blower's boost is there at once, needs no exhaust or fuel (cold starts, throttled or governed engines, load
  swings), adds no back-pressure, and keeps climbing at high revs and high boost where a turbine chokes the exhaust; it
  pays in stress on the shaft that turns it. The dynamometer is steady state, so it shows none of the turbo's lag.
- **Supercharger changes** (`engine.sim.Supercharger`): boost goes with shaft speed to the power 1.25, not 2 (a roots
  blower is a flow pump), so half the design speed makes about 40 percent of the boost and not 25; its compressor is as
  efficient as a turbo's (0.70, was 0.55, so it no longer heats the charge more); drag on its shaft is 2 stress per RPM
  per cylinder at full boost (was 4). The bypass valve is soft (see below).
- **Variable-Drive Supercharger** (`VariableSuperchargerBlock`, `Supercharger.variableDrive`). A Supercharger, a
  Precision Mechanism and a brass sheet, as the Variable-Geometry Turbocharger is made from a Turbocharger. Its variable-ratio
  drive overspeeds the rotors by up to 80 percent while the shaft is slow and is one to one from the design speed up
  (`rotorSpeed`, `overdrive`), so at half the design speed it makes about 0.58 bar where a plain blower makes 0.38; its
  tight rotors have a 0.73 compressor efficiency (a cooler charge than any turbo), drag 30 percent less on the shaft
  (`VARIABLE_DRAG`) and a pressure that follows the shaft in a tenth of a second, not a fifth. Goggles show the overdrive.
  Advancement: Overdrive (`VARIABLE_DRIVE`, 0.3 bar on a running engine with the drive overspeeding by at least a fifth),
  which is also part of the Power Plant capstone. Ponder: the supercharger page covers it.
- **How level are they** (`BoostParityTest`, 8 cylinders, net of the blower's shaft drag, same boost limit, steady-state
  peak power, percent against the matching turbo at 0.4 / 0.5 / 0.6 / 0.8 / 1.0 bar). Supercharger against turbocharger:
  crude cylinders minus 1.1, plus 0.3, minus 0.8, minus 2.9, minus 8.0; precision cylinders minus 2.4, minus 1.4, minus 0.2,
  plus 1.9, plus 5.2. Variable-drive against variable-geometry: crude minus 0.6, plus 0.7, plus 1.9, plus 1.4, minus 5.2;
  precision minus 1.1, plus 0.2, plus 1.4, plus 3.6, plus 7.2. The test pins 3.5 percent either way from 0.4 to 0.8 bar, that
  the blower out-runs the turbo at 224 RPM, and that the variable drive beats the plain blower at 64 RPM. At 1.0 bar the
  crude blower is short because its shaft, geared one to one to a crude engine's 96 RPM peak, is too slow to make a full
  bar: gear it up, which is what its value panel and the Ponder page tell the player to do.
- **The bypass valve is soft.** A turbo's wastegate opens as the boost climbs past the limit, so a turbo set to 0.5 bar
  settles near 0.57. A blower's bypass now does the same, by up to 0.05 bar (`VALVE_SOFTNESS`), which is what closed the
  gap at low limits; with a hard stop the blower trailed by 3 to 6 percent there. The limit on its value panel is
  therefore the pressure the valve starts to open at, not a hard ceiling. It is capped at `MAX_LIMIT` (1.5 bar).

### Boiler heat, readouts, builder extras, muffler and casing (2026-11-13)

- **Boiler heat** (`CECompat`). Create 6 reads a boiler's heaters through `BoilerHeater.REGISTRY`, keyed by block. The
  Exhaust Heat Exchanger already carries a blaze burner's `HEAT_LEVEL`, so it is registered with Create's own
  `BoilerHeater.BLAZE_BURNER`: kindled is one level of boiler, seething two, smouldering only passive heat. A fluid tank
  over a hot exchanger with Create steam engines on it adds their power to the engine's (a combined cycle). The exchanger
  pokes the tank (`FluidTankBlockEntity.updateBoilerTemperature`) when its level changes, because a boiler only looks at
  its heaters when told to. Nothing in the simulation changed, so no balance table moved; the new power is Create's own
  (steam engines, a boiler and its water) paid for with the exhaust the engine was throwing away, scaled by the
  existing `heatRecoveryOutput` and `heatRecovery` settings. About 20 HU/s makes a seething exchanger, so
  roughly seven cylinders at speed make two boiler levels per exchanger (by Create's own rules, up to two steam engines
  at full efficiency, if the boiler is big enough and has the water), and exchangers on one network split the heat. Advancement: Combined Cycle (`COMBINED_CYCLE`, a hot exchanger under a boiler that has engines on it and
  is taking heat). Not played end to end: the GameTest checks what Create is told, not a running boiler.
- **Readouts** (`content.display`, `CEDisplaySources`). Create's Display Link reads a block through a `DisplaySource`
  registered with `REGISTRATE.displaySource` and attached to the block with `DisplaySource.displaySource(...)`.
  Crankshaft, crank throw and rotor housing offer Engine Status (state, RPM, load, temperature and oil, a line each),
  Engine Speed (one RPM line for a nixie tube) and Engine Load, Temperature and Oil Condition (a percentage or progress
  bar, chosen the way Create's fill level source is). Load is the network's torque over what the engine can give
  (above 100 is overloaded); temperature is the block's heat over a warm engine's, so 100 is warm; oil is the
  condition, 100 new. Engine Boost (`+0.45 bar`, the highest boost any compressor puts on the intake, the figure the
  turbo goggles show) and Engine Rated Torque (`445 SU/RPM`, the goggles' rated torque) are one nixie-tube line each.
  Engine Fault (the health line's cause, a few words) is on the crankshaft blocks and Transmission Gear on the transmission (see "Part depth, Black Box replay and the last two display fields").
  A battery offers Battery Charge (`BatteryDisplaySource`, a percentage or progress bar like the fill sources, from
  the battery's charge over its capacity; it does not credit Instrument Panel). The oil sump offers Oil Sump Levels, one tank to a line. Every source refreshes every second
  (`EngineReadout.REFRESH_TICKS`). The figures come from the synced `EngineSummary`, so they cost the engine nothing.
  Advancement: Instrument Panel (`ENGINE_DISPLAYED`, a link reading a running engine). A Display Link has not been set up
  through its screen in play: the GameTest checks that every source is attached to the right blocks.
- **Sump comparator.** The sump has a second dial, above the Oil Exchange one, that picks which tank a comparator reads:
  Engine Oil, Used Oil, Coolant or Pilot Fuel. The signal is 0 for an empty tank and 1 to 15 as it fills, 15 only when
  full (`SumpBlockEntity.comparatorSignal`), refreshed every ten ticks, so a pump can top the oil up or an alarm can
  sound when it runs low. Advancement: Dipstick (`SUMP_METERED`, a comparator reading the pan of a running engine).
- **Only engine oil wears into used oil (2026-11-17).** The oil exchange used to turn anything the oil tank accepted, plant
  oil and the `c:plantoil`-style tags included, into Used Oil, and the recycling recipes turn Used Oil into Engine Oil. That
  let a poor lubricant skip the slime or glycerin that engine oil costs (about 0.6 engine oil per plant oil, 0.75
  superheated, so a gate bypass and not a free-oil loop). `SumpBlockEntity.exchange` now burns a poor lubricant off: it
  still leaves the pan and still counts as swapped, it adds nothing to the used tank, and it does not wait for room there.
  Engine oil is unchanged. A behaviour change for any build that piped used oil out of a plant-oil pan, approved by the
  author as an exploit fix: list it under Exceptions in the next release record. GameTests:
  `anExchangeTurnsEngineOilIntoUsedOil`, `anExchangeNeverTurnsPlantOilIntoUsedOil`.
- **Schematic pricing.** Fitted parts are block state flags (see `PartInstallation`), and Create prices a block by its
  item alone, so a Schematicannon used to build a finished cylinder, head or rotor housing with its pistons, rods,
  injectors, plugs and rotor for free. Those three blocks now implement Create's `SpecialBlockItemRequirement`
  (`PartInstallation.requirement`): the block plus every fitted part, precision grade respected. Every one of those,
  and each battery and turbocharger, is paid only with a new item (`NewPartRequirement`), as is a blueprint build; Create
  matches by item alone, so a worn one would come out new. A recipe that builds on a part that wears uses the ingredient
  type `create_engines:new_part` for the same reason, and the Bank Builder's precision swap carries a block's contents
  across (`BlockSwap`) instead of replacing it outright.
- **Bank Builder extras.** A builder that is carrying spark plugs fits one in each head it works on, and one carrying a
  Cam Lobe lays a cam line parallel to the crank: a lobe at each throw's offset and Camshaft blocks in the gaps so the
  line is unbroken (`BankBuilderItem.camOffsets`). The offset is the cell beside each cylinder block, on the side a quarter
  turn round the crank from the bore: a flat gets a line by each bank, and a vee, whose second bank is on that very side,
  gets one lobe in the valley that touches the block of both banks, which the shared-lobe timing already handles. A W
  gets a line in each of its two valleys, either side of the middle bank, and an X one in each of its four; a block
  touching two lobes takes the first the scanner finds (`EngineScanner.lobeBesideBlock`). The
  extras use the player's real inventory even when creative (a creative builder otherwise has everything and would
  put plugs in every diesel), and a creative player's stack is not used up. The line still has to be geared to half the
  crank's speed, and the Timing Tool times the lobes. Advancement: Cam Included (`CAMS_LAID`). (The Timing Tool already
  planned rotor housings; the "rotors are not planned" note above was out of date.)
- **Muffler** (`MufflerBlock`, `engine.sim.Muffling`). An inline exhaust duct like the intercooler: pipes join its two
  ends. It changes sound only: no power, no back-pressure, no simulation change at all. The scanner lists the mufflers on
  each exhaust `PortNetwork` and, as for turbos, whether the run can go round them (`parallel`). A run is fully muffled by
  one every path goes through and half muffled by one it can go round; the engine's `muffling` (in `EngineSummary`,
  synced) is the average of its exhaust runs weighted by the heads they serve, so one bank of a V muffled is half. The
  exhaust note takes it outlet by outlet (`EngineNoteSynth.Voice.muffle`, or `setMuffling` for an engine that sends none):
  the muffled part of each outlet is scaled by `EngineNoteSynth.MUFFLED_LEVEL` (higher than
  `Muffling.volume(1)`, so a slow muffled single is still heard) and goes through a two-pole low-pass at 500 Hz, beside
  the open part, and what the cylinders share (thump, crack) follows the outlet of the latest firing, so one muffled bank is
  heard as muffled firings between open ones.
  The client scales the cylinder and rotor bangs and the exhaust pops by `Muffling.volume` (a fully muffled
  engine is a fifth as loud); the mechanical sounds (squeal, catch and stall) and the turbo and blower are not
  the exhaust and are left alone. Goggles on a crankshaft say how much of the exhaust is muffled. Advancement: Hush Now
  (`MUFFLED`, a running engine at half muffled or more). The volume was calculated, not listened to.
- **Engine Casing.** A plain cube (riveted plate, steel top and bottom) with no block entity and no behaviour: it joins
  no pipe, belongs to no engine and costs the scanner nothing. It is there to hide the sprawl. Advancement: Under the Hood
  (`CLAD`, a running engine with four or more casing blocks against its cylinders).
  It is one riveted steel plate on every face with **connected textures** (`content.CESpriteShifts`: Create's omnidirectional
  sheet, 8 by 8 tiles, drawn by `tools/generate_textures.py` with `connected_index` copied from Create's `AllCTTypes` so
  the tiles sit where Create looks for them), so a run of casing and everything clad in it reads as one plate. It
  **covers more than the shaft line**: Create's own shafts, small and large cogwheels (`EngineCasedShaftBlock`,
  `EngineCasedCogwheelBlock`: Create's encased blocks with this casing, found through `EncasingRegistry`, so the casing is
  used on them like Create's; Create's models, retextured; their side textures are Create's recoloured by
  `tools/derive_cog_casing.py`; they drop the shaft or cog when broken, as Create's do), and it covers cylinders and plain
  intake and exhaust manifolds (not throttles or sensors) with the same `encased` state as the shaft parts: a clad cylinder
  or pipe is a plain casing cube, closed to the piston and rod, and keeps its block entity and connections. Create's
  cogwheel block entities stay Create's, so they look at neighbouring cogs while ticking and `StressWorldGameTests` skips them in
  the unloaded-chunk test. It **covers shaft parts**: use one on a crankshaft, crank throw, camshaft or cam lobe and the part gets an
  `encased` block state (`content.Encasing`). A cased part is the same block with the same block entity, so the engine
  scan, sim and visuals are untouched; only its render shape flips from the animated renderer to the block model
  `engine_casing/encased` (a casing with a recessed opening in each end, turned onto the axis by the blockstate), and its
  shape becomes a full cube. The moving part is still drawn inside it, so the shaft shows in the openings. A wrench takes
  the casing off and gives the item back; breaking a cased part drops the casing too. Advancement: Tucked In (`ENCASED`).
- **Fuel tooltips** (`client.FuelTooltips`). Any container with a fuel in it (buckets, other mods' tanks) lists the fuel's
  energy per mB, whether it lights by compression (autoignition at or below `CEDataMaps.PILOT_IGNITION_LIMIT`, which also
  makes it a pilot fuel) or needs a spark plug, how it takes a cold start, and how much thicker a shot it needs. They are
  read from the same data map and tags as the simulation, so a datapack fuel is described correctly. No advancement: it is
  an interface change, not something the engine does.
- **Checks.** Unit tests `MufflingTest` and `LooksMilestonesTest`. GameTests `aSchematicOfAFittedEnginePricesItsParts`,
  `aSumpComparatorReadsTheTankItIsSetTo`, `displayLinksCanReadAnEngineAndItsPan`, `aBoilerReadsTheHeatExchangerLikeABlazeBurner`,
  `aMufflerInTheExhaustRunMutesTheEngine`, `theBankBuilderLaysPlugsAndCamsOnlyIfItIsCarryingThem`,
  `theBankBuilderPutsAVeesLobeInItsValley` and `aCogPlacedBetweenTwoCranksCouplesThemAtOnce`. Three new Ponder pages
  (`muffler`, `engine_casing`, `engine_readouts`) and extra steps on the heat exchanger, sump and Bank Builder pages: their
  text is generated by datagen, but no scene has been watched in the game.

### 1.1 to 1.3: where the later releases put things (2026-11-17)

A map of what shipped after 1.0, by release, so the next reader can find the code. Numbers, test counts and the exact
list of IDs are in each release's record (`docs/releases/`) and changelog (`docs/LISTING.md`); this is only the shape.

- **1.1, light and noise.** Everything is client-side and reads the same synced `EngineSummary` the goggles read, so the
  simulation's numbers did not move. Engine rock is `content.effects.EngineRock` (from the roughness rating), pushrods are
  `content.PushrodGeometry` (one from each lobe to a rocker on the head, valley cams drive both banks), and the
  consequence effects (stack flame, fouled puff, lean spit, crankcase smoke, flood gulp, blocked-exhaust chuff, last
  belch) are particle and sound cues keyed off causes `Diagnosis.Cause` already names. `content.EffectRate` thins all of
  it by distance and by `effectDensity`. The advancement declutter hides rows by removing their display only: IDs and
  criteria stay (`CEAdvancements.SHOWN` is the list of what the tab shows; see `docs/ADVANCEMENTS.md`).
- **1.2, dress parts.** Cosmetic only. `content.CasingStyle` (plain, riveted, louvred) is the `casing_style` block state on
  casing, clad parts and pipes; window casing is a block of its own that clads shaft parts. `content.CasingPaint` and
  `CasingTint` apply and draw dye (a water bucket washes it). `content.raincap` is the Rain Cap, a block placed only on an
  exhaust outlet, flapped by a renderer from the exhaust pulses. `content.plate` is the Maker's Plate: it stores a name,
  the builder and four figures (`engine.sim.PlateFigures`: peak torque and peak power, each with its speed) taken from the
  finished dyno curve. It does not keep the curve, so 1.4's blueprint reads the curve from the engine or a Dynamometer.
  **Cost found in 1.3:** `casing_style` on the five pipe blocks triples their block states, each with the 64-way connection
  multipart, which added about 440 MB to settled heap at model load (`docs/releases/v1.3.0.md`, Exceptions). Cheaper pipe
  models are on the 1.4 list.
- **1.3, the Refinery.** The numbers live in `engine.sim` as plain Java and are tested without Minecraft:
  `ColumnShape` finds the column from any part (a square of Reboilers 1 to 3 wide, layers of Column Sections, a layer of
  Condensers, at most 32 section layers, a `Fault` for why it did not form), `RefiningColumn` does one column's work a tick
  (0.25 mB a tick for each block of the body, times the route's speed), `RefiningRoute` and `Heat` describe a route and
  the heat it needs, `ColumnAdvantage` is how much better a column does than a basin (what the Basin goggle line says),
  `WindowTint` picks the sight-window colour and `TankSignal` the comparator reading. The world side is
  `content.refinery`: `ColumnPortBlock` and `ColumnPortBlockEntity` (one block entity type, `column_port`, for the
  Reboiler and the Condenser; the Reboiler at the lowest corner is the controller and holds the feed tank, the product
  tank, the catalyst slot and the `RefiningColumn`, and the other ports delegate to it through the fluid and item
  capabilities), `RefiningColumns` (heat from the block under the base, through Create's `BlazeBurnerBlock.getHeatLevelOf`),
  `ColumnSectionBlock` (sight windows, and the lean arms), `FlareStackBlock`, `RefiningRecipe` (the data recipe type
  `create_engines:refining`; the hottest route the base satisfies wins) and `BasinHints` with the one mixin
  (`mixin.BasinGoggleMixin`, which fails soft if Create changes the method). The controller rescans the shape every ten
  ticks, bounded; nothing hooks `onPlace`. **A lean has no joint block.** A layer after the first may sit one block aside
  along x or z; `ColumnSectionBlock` computes `lean_up` and `lean_down` from its diagonal neighbours and draws a wedge and a
  brace into the empty block above or below, as Create's chutes join. The arms are looks only (no collision), and a block in
  the way is a `LEAN_BLOCKED` fault that goggles name. Diagonal leans in x and z at once are not supported. Balance:
  `docs/REFINING_BALANCE.md`. Also in 1.3: the Soot Trap (`content.muffler.SootTrapBlock`, sharing `ExhaustCanisterBlock` with
  the Muffler; `engine.sim.SootCollector`, 50 mB of unburned fuel is one Soot, 64 at most), the Jerry Can
  (`content.jerrycan`, the `can_fluid` data component and a fluid item capability), the JEI and EMI refining pages
  (`client.compat`, found through the viewers' own annotations and never named outside those packages;
  `RecipeViewerCompatTest` fails if they are), `engine.GhostRotation` (brings a network that turns with no running
  generator to rest, every 20 ticks per crank or alternator) and `engine.CasingTally`.
- **1.4, the Drawing Office (built, unreleased).** Plain Java in `engine.blueprint`: `Blueprint` (parts with position, block id,
  non-default state and dials, plus the plate figures from format 2), `BlueprintCodec` (the `CEBP<version>:` string, deflated
  base64url, every size checked before it is used, only `BlueprintException` ever escapes), `BlueprintRules` (shape, then the
  game's rules through `BlueprintPolicy`), `BlueprintDiff` (same build or not, which dials differ), `BlueprintNames`. Game side in
  `content.blueprint`: `EngineDials` is the one table of every value-box dial and its range (a GameTest fails if it differs from
  a real value box), `BlueprintRecorder` reads `EngineLayout.members()`, `BlueprintApplier`, `BlueprintBuilder` (all-or-nothing, as
  the player, through the place event), `BlueprintSchematic` (a Create schematic file), `BlueprintImport` (the one packet and its
  checks), `BlueprintItem`, and the Framed Blueprint block and block entity. Client: `BlueprintScreen`, `BlueprintImportScreen`,
  `FramedBlueprintRenderer`. The common code reaches the screens only through `BlueprintViewer` hooks the client sets. The
  `api` package (`EngineApi`, `EngineView`, two events posted from `EngineController` through a guarded `EngineEvents`) and
  `PartStats` with the `part_stats` data map (applied where `EngineController` builds a cylinder's spec, one line, identity when no
  pack names it) were previews in 1.4 and are frozen at 2.0 (docs/API.md). The recipe viewer fuel and part pages are in `client.compat`, with `ViewerEntries` shared by both
  viewers. Details and the reasons for each limit are in `docs/ROADMAP.md`, 1.4.
- **Guards added along the way:** the compatibility guards in `docs/COMPATIBILITY.md`, the stability contract in
  `docs/STABILITY.md`, the smoke harness in `docs/SMOKE.md`, and the architecture rules, config matrix, property tests and
  performance budgets that `./gradlew check` runs.

### Battery depth (2.0, 2026-11-19)

The author's roadmap item "Battery depth": five additions to the battery, all for 2.0. A plain battery is still 200 sparks, drawn
one per plug firing and refilled by an alternator; what changed is below. The numbers are in `BatteryChemistry` (no Minecraft types,
`BatteryChemistryTest`); `BatteryBlockEntity`, `BatteryBank`, `BatteryIgnition` and `EngineController` apply them.

- **Banks.** Batteries that touch are one bank (`BatteryBank`): capacity is the sum, charging and drawing are shared out in proportion
  (a draw in proportion to what each can deliver, a charge in proportion to the room each has), so a bank runs down and fills evenly.
  Nothing about a bank is stored: it is found by walking over touching batteries. One walk serves the whole bank for a second
  (`BatteryBank.look`), and a battery placed or removed marks only the walks of the batteries it touches as out of date. The walk stops
  at `limits.maxBankBatteries`. An engine or alternator touching one member reaches all of them:
  the scanner adds the whole bank to `layout.batteries()` and to the engine's members, so a blueprint records it and a change to any
  member rescans the engine. A member in an unloaded chunk is left out until it loads rather than stopping the engine. A lone battery
  is a bank of one, with the numbers it always had.
- **Cold.** A battery's coldness is its biome's temperature (nothing at 0.5 and above, all of it at 0 and below) less the warmth of a
  heat source beside it (a blaze burner, heat exchanger or lit campfire, by Create's heat level: 0.4, 0.8, 1 of the cold taken off),
  looked at every two seconds. A cold battery keeps its charge but delivers at most half of its capacity when as cold as it gets, and
  gets it back when warmed. A cold engine (`Thermal.felt`, which a warming starter reduces) makes each spark cost up to twice as much.
  `features.batteryCold` turns both off.
- **Cranking draw.** A starter turning a spark engine over (an engine with a working spark plug and a battery; compression engines are
  not touched) takes 0.15 sparks a tick from the bank, up to twice that in a cold engine, falling in proportion once the bank is under
  10% and to nothing from an empty one. The starter's goggles say when the batteries are that low. **The roadmap also said a weak battery
  should crank slowly and give up partway; that part was built and dropped.** The starter is driven by a shaft, and a spark engine
  started from the flat battery it is placed with (an alternator on the crank line charges it by cranking) did not catch at a reduced
  cranking speed: seven existing GameTests failed (two gasoline engines, a rotary, two ethanol engines, the flooded plug and the drained battery),
  the rotary one still cranking at 50 RPM after five seconds. A starter that gave up at an empty bank would have left every such engine
  dead from the moment it was built. The draw falls with the bank for the same reason: a draw that did not would take all of the little an
  alternator makes in the ticks its shaft is not overstressed, and the engine would never start. `features.crankingDraw` turns it off.
- **Comparator.** `getAnalogOutputSignal` on every battery reads its whole bank, 0 only when empty and 15 only when full. The battery
  tells its comparators when the reading changes (checked every half second). Output only: no redstone input.
- **Gauge.** The goggles show the bank's charge and size, the rate it is gaining or losing and the time that gives, and the cold.
  On the block, four lamps on each face of the lid light by the battery's own charge (`BatteryBlock.CHARGE`, 0 to 4, set by the block
  entity with a small hysteresis so a boundary does not flicker; the block is the same block, so nothing about its ID or saved data
  changes, and a world saved without the property loads with it at 0 and fixes itself within half a second).
  The block entity's lamp-strip changes do not count as placing or removing it (`BatteryBlock.onPlace`/`onRemove` ignore a
  state change of the same block), which would otherwise rebuild the engine each time.
- **Display Link.** Battery Charge reads the bank, not the single battery.
- **Saved data.** `BatteryBlockEntity` gained `Cold` (read back clamped to 0 to 1); `Charge` is now clamped to 0 to 200 and a NaN
  reads as 0. The starter gained `BatteryWeak`. Nothing was removed or renamed.

### Battery tiers (moved to 2.2, 2026-11-23)

**No tier block ships in 2.1.** The Dry Cell (100 sparks, the cold does not touch it) and the Large Battery (800 sparks, three quarters lost to the cold)
were built and then moved to 2.2 (author, 2026-11-23); the Electric Motor that was to be their consumer was dropped the same day. What stays in the tree is the
machinery, with one tier: `BatteryTier` (no Minecraft types, `BatteryTierTest`) holds `PLAIN` only, `BatteryBlock` takes a tier, and `BatteryBlockEntity`
reads its capacity and cold loss from its block. The plain battery is exactly what it was (200 sparks, half lost to the cold; `BatteryTierTest` pins it),
so no existing engine or world changes. The removed blocks' files are saved in `../create-engines-deferred/` (`dry-cell`, `large-battery`).

What the tiers did, for when they return:

- **Banks.** Every tier is a `BatteryBlock`, so tiers join when they touch, and a bank's capacity is the sum of its members' own. Charging shares out by each
  member's room and drawing by what each can deliver.
- **Per battery.** The lamp strip, the goggles' percent and `chargeFraction` use that battery's own capacity; the comparator and the goggles' bank line read
  the whole bank.
- **Saved data.** The same keys. A saved charge is held to the capacity of the block it loads into.
- **Not done, and needed before they return:** a Ponder page that actually explains the tiers (the existing battery scene only names the blocks as components
  and says nothing about size, cold or banks: the author, 2026-11-23, "the ponder pages don't really explain the battery tiers"), and an in-game look at
  both blocks.

### Alternator charge rate (2.1, 2026-11-23)

A value box on the alternator (any face the shaft does not run out of) sets how much of its full output it is allowed: 25, 50, 75 or
100% (`ChargeRate`, unit tested). The charge it makes, the Forge Energy it makes and the stress it puts on the shaft (2 SU a RPM at
100%) all scale with the setting, so a lower setting is a lighter load for a smaller charge: a marginal engine can carry a light
alternator. The share of the engine's rated torque in the goggles follows it, and the goggles say when it is below 100%. The dial
changes the stress at once, not at the next change of charging. 100% is the default and is exactly what an alternator did before:
a saved alternator with no `ChargeRateSetting` key reads as 100%, and a damaged value is held to the dial's range. The blueprint
dial is `charge_rate`. Nothing was removed or renamed.

### Part depth, first four items (2.0, 2026-11-19)

From the roadmap's "Part depth (2.0)": the items that needed no new block and no author call. Each is listed with what was checked first.

- **Alternator readout (item 2).** The goggles say how fast it is putting charge into batteries (`goggles.alternator.rate`, sparks a second, a one-second
  smoothing of what `BatteryBank.fill` actually took) and what share of the engine's rated torque its stress takes (`goggles.alternator.share`:
  its 2 SU a RPM over the crankshaft's `publishedCapacity`, shown only while it is charging or supplying). Information only: nothing it does changed.
- **Several alternators (item 3).** Checked first: they already add up, each filling the bank in proportion to the room and each putting its own 2 SU a RPM on
  the shaft only while it has something to fill, so the total stress is the sum (`theStressOfTwoChargingAlternatorsIsTheSumOfTheirs`). Nothing needed to be
  split. What was missing was the readout: the engine now tells each alternator which alternators it has (`attachToEngine` gained the list), and each says
  how many there are and what share of the charge made is its own (`goggles.alternator.fellows`).
- **Compressor readout (item 4).** Checked first: there is no efficiency figure to show (the turbocharger's 0.7 is one constant, not a range), so none was
  invented. The new line is made of two figures the block already holds: boost against the limit set on it (`Turbocharger.limitPercent`, "Working at
  83% of its boost limit", gold at 95 and over), on both turbochargers and superchargers, so the limit dial can be set by reading.
- **Hand-crank assist (item 1).** Checked first: a crank line already turns from outside (`drivenFromOutside`) and an alternator on it charges a battery by
  turning, so a flat battery is no dead end once something turns the crank; what stopped a person doing it was that the starter wants a redstone signal.
  A starter whose shaft is turned by a Create hand crank (`source` is a `HandCrankBlockEntity`) now engages without one, and says so in its goggles. A
  starter on a motor or a water wheel still needs redstone, as every existing build has it. It is the only one-more-source change: water wheels and
  windmills already drive the crank line directly.

Not started here: item 5 (wastegate and blow-off parts; later done as particles only, below), item 7 (headphones, a sound-engine hook and an access transformer), item 8
(Auto shift profile, needed the author's call on a new dial; **approved and built 2026-11-20**, see the v2.0.0 record and the ROADMAP row) and item 9 (Black Box replay). Item 6 (cast flywheel) was done earlier; item 10 is partly done.
Items 9 and 10 were finished next, below.

### Part depth, Black Box replay and the last two display fields (2.0, 2026-11-20)

- **Display Link: gear and fault (item 10, finished).** Two more sources, each one line (`SingleLineDisplaySource`, so a board or a nixie tube
  both take it, with a label if wanted). *Engine Fault* is on every crankshaft block and shows the one thing the health line blames, as a few words
  (`Diagnosis.Cause.displayKey`, `display.engine.fault.*`: "No spark", "Dry oil", "Cold block"; "No fault" when healthy; "No engine" off a built
  engine). It reads `EngineSummary.cause`, the same cause the goggles' health line gives, so the two cannot disagree; the goggles' long advice
  strings stayed as they were. *Transmission Gear* is on the transmission block ("Gear 7", "Neutral", "Reverse"; "(auto)" after a numbered gear
  the block picked itself). It shows the gear in use, not the panel setting, so on auto a board shows what the engine is really pulling in. The
  roadmap's "gear" could have been read off a crankshaft, but the gear lives on the transmission (a crank line can have none, or several in a
  network), so the source is on the box. Nothing in the simulation changed.
- **Black Box replay (item 9).** Checked first: the tape already keeps speed, load, warmth and the state of every second for ten minutes, so scrubbing
  needed no new sampling, only a way to point at one second of it. The one thing it did not keep was the fault the engine had at the time, so
  `FlightRecorder.Sample` gained `fault` (the health line's cause), one byte a second in the ring (`Faults` in the saved tape, additive: a tape
  saved before reads as blaming nothing, a damaged fault number is clamped). The box gained a **Replay dial** (a value box on its side faces, 0 live
  and each notch `FlightRecorder.REPLAY_STEP` = 5 seconds further back, 120 notches for the whole tape). Turning it pins a **moment** by its
  sample number (`FlightRecorder.sampleBack` and `momentAt`), so the figures stay on one second while the tape runs on and the "seconds ago" grows;
  a moment that falls off the oldest end holds at the oldest sample. The server works out the moment and sends it with the readout (`Moment` in
  the client packet; the tape itself still never goes to clients). Goggles then show "Replaying 2 min ago", the state, speed, load and warmth then,
  and the fault then. The dial is also in the blueprint dial table (`EngineDials`, name `replay`), which the table-of-dials GameTest requires of any
  dial on a block; the pinned moment is not saved, so a loaded block pins its dial's distance back from the newest sample again. Information only: no
  engine number reads it. The dial is a viewing aid and not a tuning setting (rule 4 of the Declined list is about tuning dials).
- **Blow-off particles (item 5, changed to particles only at the author's say).** A fitted part was proposed and declined. The turbocharger's blow-off
  (`TurbochargerBlockEntity.ventCharge`) used to be one burst of 5 to 11 white puffs; it is now a burst of 8 to 17 followed by a jet off the compressor end that
  thins out over 6 to 18 ticks (`ventJet`, numbers in the pure `VentJet`: more boost, more puffs and longer, never more than 1.5 bar's worth), and the wastegate chuff
  (opening to hold the limit, which had only a sound) puffs 2 to 4 soft grey particles off the turbine end (`wastegateChuff`). Client only, gated by the particles
  setting, and the jet and chuff are thinned by the effect density setting. No sound, block or item changed, and the supercharger is untouched. Not seen in game.
- **Tests.** `FlightRecorderTest` (six: moments, edges, pinning, falling off the tape, saved faults with an old and a damaged tape, the dial covering the
  tape), `DiagnosisTest` (display keys), `GearsTest` (display keys); GameTests in `PartDepthGameTests` (sources offered on the right blocks, the gear line on
  manual and auto, a live gearbox, the fault line for a missing battery, and a recorder replay that holds a moment, saves and goes back to live).
  `compat/manifest.txt` gained 2 `reg:displaySource` and 5 `nbt:BlackBoxBlockEntity` lines (Ago, Fault, Faults, Moment, State), appended in order.
  `compat/behaviour.txt` and `compat/trajectories.txt` untouched.

---

### Noise-cancelling headphones (item 7, 2026-11-20)

What was built, against the plan in `docs/ROADMAP.md` (Part depth, item 7, which has the author's decisions: muffled not muted, music and interface
sounds untouched, records muffled, worn like Create's goggles). **Heard by no one yet: the filter numbers are the probe's untuned ones, and whether a
streamed sound clicks when the filter goes on mid-sound is still to be listened for.**

- **The item.** `HeadphonesItem` (`create_engines:headphones`, stack of one) is `Equipable` for the head slot, so using it puts it on (and it can be swapped
  in the inventory like a helmet). The model is `block/headphones/item`, drawn in `tools/models/precision.py` `headphones()` to sit on the 10-pixel head the
  game draws a head-slot item at (a steel band over the crown, a padded cup over each ear, a brass plate and a red lamp on the left cup), with a `head`
  display transform and the usual inventory, ground and hand ones. It uses the mod's own metal textures and vanilla grey wool: no new texture file.
  Recipe: 3 iron sheets, 2 brass sheets and 2 wool (any colour), `data/create_engines/recipe/headphones.json`. Not balanced against anything.
- **Wearing.** `HeadphoneWear.isWorn(entity)` is true when the head slot holds the headphones, or, with Curios installed, any Curios slot does.
  Curios is fitted by data: `data/curios/tags/item/head.json` lists the item in `curios:head`, which is all Curios needs to offer its head slot. The
  Curios API is named in one class (`compat.curios.CuriosBridge`) and the renderer in one (`client.compat.CuriosClient`); `CuriosCompat.installed()`
  guards both, and a failure of the Curios call turns the Curios count off for the session with a warning (as `SableCompat` does). The renderer draws
  the item as the game draws a head-slot item, on the model's head. The build compiles against the Curios API (`curios_version`, compile only) and
  `neoforge.mods.toml` lists Curios as an optional dependency.
- **The muffle.** `client.HeadphoneMuffle`. While worn, every sound from the world has an OpenAL low-pass filter attached as its source's direct filter
  (`ALC_EXT_EFX`, one filter object per OpenAL context, made on first use and again after `SoundEngineLoadEvent`). A sound that starts while they are
  worn gets it from `PlaySoundSourceEvent` and `PlayStreamingSourceEvent` (the sound thread, right after setup); putting them on or off walks
  `SoundEngine.instanceToChannel` on the client thread, as the engine's own category volume change does, and changes each source through its handle
  (`ChannelHandle.execute`, which runs on the sound thread and does nothing once the sound has ended, so a source Minecraft has destroyed is never
  touched). The change is noticed by a client tick comparing `HeadphoneWear.isWorn(player)` with the last answer. `Muffle` (plain Java, unit tested)
  holds the rules: master (menu and interface), music and voice are exempt, everything else (records, weather, blocks, hostile, neutral, players, ambient)
  is muffled; 0.5 overall and 0.05 at the top (the probe's numbers). **It never mutes:** the gains are above zero by test. A sound card without the
  extension, or one that errors, gets `AL_MAX_GAIN` capped at 0.35 on each world source instead (a cut at the loud end, not a proportional one), and
  the tooltip says "cannot filter sound". After every change the source's OpenAL error is read and counted, so an error is never left for the sound
  engine's own check to report as its own.
- **Access transformer.** `META-INF/accesstransformer.cfg`, three lines, the first the mod has: `Channel.source` (the OpenAL source number, private),
  `SoundEngine.instanceToChannel` and `SoundManager.soundEngine`. The headphones need no mixin.
- **The sound stop race** (`client.mixin.SoundStopRaceMixin`, 2026-11-27, not part of the headphones). Minecraft's `SoundEngine.stopAll` queues
  `Channel::stop` on the sound thread for every playing sound, then deletes the same sources itself without waiting, so a queued stop can reach a deleted
  source (`OpenAlUtil: Stop: Invalid name parameter`). The mixin empties `instanceToChannel` as `stopAll` begins, so nothing is queued;
  `channelAccess.clear()` still stops and deletes every source. It fails soft (`defaultRequire` 0), and the client audit's thread churn fails if it did not run.
- **Not done, on purpose.** No config option (the numbers are constants until someone has heard them); no advancement (the tab is crowded); no
  cosmetic variants; the tooltip's filter line is only known after the first muffled sound has played.
- **Tests.** `MuffleTest` (7); GameTests `HeadphonesGameTests` (5: worn only on the head, equippable in the head slot, in `curios:head`, craftable, and
  every `SoundSource` the game has is decided by `Muffle`, so a new vanilla category stops the build until someone chooses); in the client audit
  (`HeadphonesAudit`, fleet phase) the headphones are put on the server's player with engines running, a picture is kept of them on the head, and every
  playing source is checked: every world sound has had the muffle put on it, no exempt one has, and no call left an OpenAL error; then the same after
  taking them off; in the Curios compatibility row (`runSmokeAuditCurios`) the same through a Curios head slot with the game's own head slot empty.
  **What that does not prove:** OpenAL cannot be asked what filter a source has (reading `AL_DIRECT_FILTER` back raised an invalid-enum error in the first
  run, which is why the muffled sources are kept in a set as they are changed), so the audit shows the calls were made on every source and the card took
  each without an error, not what is heard. The first audit run found the hook was right and the read-back was wrong; no run has heard it.

### Joined batteries, a turnable blueprint frame and two model fixes (2.0, 2026-11-20)

Four things the author asked for after looking at the tree in game.

- **Batteries join, as tanks and item vaults do.** Batteries that touch were already one bank (`BatteryBank`); now they also *look* like one.
  `BatteryBlock` keeps five boolean block state properties, `north`, `east`, `south`, `west` and `up` (vanilla's own, `BlockStateProperties`),
  true where another battery touches that side. There is no `down`: nothing in the model depends on it. They are set by `getStateForPlacement`
  and `updateShape` from the neighbour's block alone, and a battery saved before they existed looks at its neighbours itself in the lamp-strip
  check that runs every ten ticks (`BatteryBlock.joined`, which asks only about loaded neighbours, since asking for the block of an unloaded
  chunk loads it). **No rule about shape**, unlike a tank or a vault: any run, wall, L, ring or block of batteries joins wherever two of them
  touch, diagonals do not. Nothing about the bank changed: it is still found by walking over touching batteries, and the joins change no
  number. The collision shape fills out to the edge of the block on a joined side (`BatteryBlock.shapeOf`).
  *Models.* `tools/generate_models.py` writes one shell for each of the 32 ways the sides and the top can be joined (`battery/block_jNESWU`,
  a 1 for each joined side). On a joined side the body, band and lid run to the block's edge and leave out the faces that look into the
  neighbour; on a joined top the body runs up to the next battery, the terminals, caps and vents are left off (they stay on top of the stack)
  and the lid ring stays, so each cell of a stack still reads as a cell. The hold-down straps are only on a side that is not joined. The body is
  cut round the band and the lid (three lengths) so no face of one lies in the plane of a face of another. The lamp strip is a separate model per
  side (`block_dark_<side>`, `block_gauge_<stage>_<side>`), laid over the shell by the blockstate only on a side that is not joined, so a
  wall of batteries has its lamps on the outer faces only. The blockstate is `CEBlockstates.batteryBlockstate`, a multipart of the 32 shells and the
  lamp pieces. **Blueprints do not record the joins or the lamp strip** (`BatteryBlock.isOnlyForLooks`): they depend on what stands next to a
  battery, which a blueprint does not carry, and a recorded join would make an engine built beside another battery a different build.
- **The purple on the dark lamps.** The dark lamp cells named a texture (`#dark`) that the model never defined, so they drew the purple and
  black of a missing texture. The generator now defines it (`create_engines:block/screen`). `BatteryModelsTest` checks that every face of every
  battery model names a texture the model defines.
- **Z-fighting on the cast flywheel's shaft.** The wheel model carries a full-length shaft so it turns with the axle; its end caps lay in the
  planes of the collars in the block model (y 0 and y 16) and fought them. The shaft has no cap faces now (they are inside the collars).
  `CastFlywheelModelTest` checks the two models against each other, which the earlier check of each on its own could not.
- **A wrench turns the drawing on a framed blueprint.** A quarter turn each use (`PaperTurn`, plain Java): the whole drawing, plan and text,
  turns about the middle of the sheet, and a drawing on its side is laid out across a sheet as if the sheet were turned too (a three by two
  sheet lays a two by three drawing out and then turns it). The turn is saved by the frame that draws the sheet, as `Turn` (a byte; a frame
  saved before has none and is upright, and a value out of range is read in range). A wrench on any frame of a joined sheet turns the drawing of
  the sheet's source frame (`FramedBlueprintBlockEntity.sheetSource`). A frame with nothing to show is wrenched as it always was. Taking the
  blueprint out sets the turn back to upright, and it is not part of the blueprint item.

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
  downstream behave normally. Create's maximum rotation speed (default 256 RPM, a server setting) is a hard ceiling, and
  we recommend raising it (see §5.1). Engines are "slow and heavy" by Minecraft standards at the default; that is the
  right feel for Create.
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
  **How it is smoothed:** the speed sent to the network, and the speed the capacity, goggles, Display Link, Black Box,
  dynamometer's current-speed mark and sounds read, is the mean over the last whole firing cycle
  (`EngineSimulation.meanRpm`), ramped to each new mean over that cycle's length (`engine.sim.MeanSpeed`), so it is
  smooth and at most a cycle behind. Before a first cycle, while a starter or another source holds the crank, and when
  stopped, it is the instant speed. A tick is not locked to the firing cycle, so the instant speed at its end would land
  somewhere else on the ripple each time and a rough engine would keep sending new speeds. The governor, the tuning
  readings and everything inside the simulation still use the instant speed.
  **How it shows:** `CrankSurge` draws the crank's throws, and so their pistons, a few degrees (4 at most) ahead and
  behind once for each firing, more with more ripple. The plain crank segments, whose ends meet Create's shafts, and the
  cams do not surge.

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
steps = ceil(|ω|·Δt / dθmax)       // dθmax = 4° up to ~640 RPM (~19 sub-steps at 256), then grows to cap a tick at 64 steps, never past 16°
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

Cost: about 20 sub-steps × 12 cylinders × a few dozen flops is negligible. Energy-based integration stays stable at
large crank-angle steps and handles stalling mid-tick naturally.

**RPM scale.** `balance.rpmScale` is applied only where the engine meets its shaft (`OutputGearing`, `EngineController`):
published speed ×scale, SU per RPM ÷scale, shaft speeds read back ÷scale, and a supercharger on the engine's network has
its stress per RPM ÷scale. `engine/sim` never sees it, so saved motion and the behaviour guards are unaffected.

**Running above Create's default 256 RPM limit.** The limit is a server setting (`kinetics.maxRotationSpeed` in
`create-server.toml`), and we recommend raising it: nothing in the mod assumes 256. The simulation is kept cheap so that
a tick costs roughly the same whatever the speed:

- Sub-steps are 4° (the calibrated resolution) up to about 640 RPM. Faster, they widen so a tick is at most 64 steps, up
  to a ceiling of 16°. Gas exchange and burning are timed in seconds, so a fast engine changes little over a few more
  degrees: torque measured with 16° steps agrees with 4° steps to within about 0.6% from 1000 RPM up (but not below
  roughly 500 RPM, where combustion timing is sensitive to the step, which is why 4° is kept there).
- Slider-crank volume, its adiabatic heating factor and each valve's lift curve are read from tables (0.125° spacing,
  linear interpolation, exact to a few parts in a million) built once per cylinder shape, not recomputed with
  `sin`/`cos`/`sqrt`/`pow` at every step. Valve flow uses a series for `1 − e^−x` when `x` is small, and friction is summed
  once per tick instead of once per step. An eight-cylinder engine ticks in ~10 µs at 256 RPM, ~40 µs at 4096 and ~90 µs at
  8192, where it took ~30, ~415 and ~840 µs before.
- The dynamometer measures up to the configured limit (points every 16 RPM to 256, then 320, 384, 448, 512, 640, 768,
  1024, 1536, 2048, 3072, 4096, 6144, 8192), and measures one speed per tick, slowest first, instead of the whole curve in
  one tick. A rebuilt engine keeps its old curve until the new one is whole.
- On the client, each cylinder's bang and exhaust puff is limited to once every 3 and 2 ticks (invisible at Create speeds,
  where a cylinder fires about twice a second), and a cam that turns more than half a cycle between frames counts every
  firing as passed instead of silencing the effects, which used to stop them above about 600 RPM at 30 fps. If KE goes negative partway through, the crank stops at
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
  **Lining up with Create's shafts:** a crank's ends are shaft-shaped, and the real shafts and cogs joined to it are
  drawn at Create's angle, so two things have to agree. Create adds a fixed phase to every shaft on a line (22.5° on a
  line whose two other coordinates sum to an even number, 0 on the next one over); `CrankGeometry.createShaftPhase` is
  that rule and `EnginePoses.drawnShaft` adds it to the crank, its pistons and a rotor's eccentric, the sim being
  untouched. And since the real shafts jump at every speed change where the accumulated angle does not,
  `RenderRotation.alignedAt` brings the crank back to Create's angle, to the nearest quarter turn (a shaft looks the
  same every 90°). The jump can be up to 45° however small the change of speed, so on a change the crank catches up in
  even shares over 3 ticks (`CATCH_UP_TICKS`): not all in one frame, which twitched the whole engine, and not slowly,
  which left it visibly out of step with its shafts. Any drift between changes is eased out by at most half a degree a
  tick. Camshafts follow the crank's angle and are not aligned on their own.

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
| Later | Refined fuels (gasoline), magneto + spark plugs, higher-compression and high-RPM parts, forced induction (built, see section 0), other engine types. | — |

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

### Kerosene (2.0, 2026-11-20)

The fuel the author asked for on 2026-11-17 ("kerosene soon"), added while engines are not yet maxed out: the cut between diesel and gasoline.
No existing fuel, recipe or number changed.

- **The fuel.** `create_engines:kerosene` (bucket, tag `c:kerosene`, sight-window tint added last in `WindowTint` so older tints keep their numbers).
  `FuelSpec.KEROSENE`: 38 energy a mB (diesel 36, gasoline 40), autoignition 2.2 (past the 2.0 pilot limit, so a spark fuel that a diesel pilot shot cannot
  light), 1.55 air, cold sensitivity 1.1 (a little slower to warm than gasoline's 0.8), no charge cooling, usual soot and sulfur. Its pre-ignition point is
  3.6: gasoline's charge lights early past 2.7 and ethanol's past 5.0, so kerosene is the fuel for a boosted build that has outgrown gasoline and has no
  ethanol chain. It is the all-rounder, not a power step: it is less energy to the mB than gasoline.
- **No data map entry.** `fuel.json` is pinned by hash in `compat/manifest.txt`, so kerosene is not added to it. It comes in through `c:kerosene`
  (`CEDataMaps.fuelForTags`), the route another mod's fuel takes, and `/ce fuels` reports it "from the tag #c:kerosene". A pack can still give it a data map
  entry of its own to tune it (a data map entry wins).
- **Making it.** Refining column: 1000 mB of diesel, heated base, glowstone dust as the catalyst (1000 mB to the item), speed 0.85, 1100 mB out, the same
  ten percent over a basin that every other route has. Basin: 250 diesel and glowstone dust make 250 kerosene over a heated blaze burner.
- **A column now follows its catalyst.** Diesel already had a route (gasoline, blaze powder), and a column picked its route by feed and heat alone, so a second
  one would have been a tie. `RefiningRecipe.find` takes the item in the catalyst slot: if some route takes it, only those routes are looked at; an empty
  slot or an item no route takes changes nothing, and a tie in heat and yield goes to the recipe whose name sorts first, which keeps an empty slot over diesel
  on the gasoline route, as it was. The column re-picks when the item in the slot changes, and an empty slot keeps the route it was running (a column that
  uses its last glowstone does not flip to gasoline). Every feed that had one route at a heat runs it exactly as before.
- **Tests.** `KeroseneFuelTest` (ordering of energy, ignition and pre-ignition against diesel, gasoline and ethanol; spark only; power between diesel and
  gasoline; the tint added last), GameTests `dieselGoesToKeroseneWithGlowstoneAndToGasolineOtherwise`, `aColumnOfDieselWithGlowstoneMakesKerosene`,
  `aColumnOfDieselWithBlazePowderStillMakesGasoline`, `aSparkEngineRunsOnKeroseneAndItNeverPreignitesUnboosted`, and kerosene added to the tag, report, route,
  basin-hint and client-audit lists. `compat/behaviour.txt` gained the `run:KEROSENE` lines and `compat/manifest.txt` four entries (all appended, none edited).
- **Not seen:** the bucket and the fluid in a world, the sight windows glowing its tint, the Ponder text (the last line of the Fuels page), and how a boosted
  build feels on it. The colours (`0xFFE3B66A`, window `0xFFEDC98A`) and the glowstone catalyst are first choices.

### Methanol (2.0, 2026-11-21)

The fuel the author parked on 2026-11-17 ("methanol, later") and asked to ship in 2.0 on 2026-11-21, with a column recipe as well as a basin one. Not crop
dependent, and clearly not ethanol. No existing fuel, recipe or number changed.

- **The fuel.** `create_engines:methanol` (bucket, tag `c:methanol`, sight-window tint added last in `WindowTint`). `FuelSpec.METHANOL`: 22 energy a mB (the
  least of any fuel; ethanol is 27), autoignition 3.8 (a spark fuel, harder to light than ethanol's 3.4), 0.8 air (so `injectorFlow()` is 1.875, ethanol's is
  1.5: the same engine needs far more of it through the injector), cold sensitivity 1.9 (the worst, ethanol is 1.8), charge cooling 0.17 (ethanol 0.08), no soot,
  no sulfur, pre-ignition 6.5 (ethanol 5.0). **Tuned by measuring**, not by feel: on the crude 12-cylinder dyno rig of `MethanolFuelTest`, methanol makes 3.2%
  more peak power than ethanol unboosted and 4.2% more on one turbo, within about 1% of gasoline either way (charge cooling 0.14 gave 0%, 0.25 gave 13%, 0.4
  gave 41%: cooling is the lever, and the value is the one that is a clear edge and not a power step). Its higher pre-ignition point is headroom for builds
  that push ethanol's limit; no shipped build reaches ethanol's 5.0, so that half is margin, not what separates the two.
- **No data map entry.** Same as kerosene: `fuel.json` is pinned, so it comes in through `c:methanol` and `/ce fuels` reports it "from the tag #c:methanol".
- **Making it.** Superheated only (a Seething burner, as over a big engine's heat exchanger): logs (`#minecraft:logs_that_burn`, so no nether stems) and
  water. Basin: 250 mB of water and a log make 60 mB. Column: 1000 mB of water with a log in the slot, one log to each 250 mB, 264 mB out at speed 0.5 (the basin's
  60 per 250 with the usual ten percent over). A heated base makes nothing.
- **Two small widenings the column route brings** (listed as an exception in the release record, awaiting approval): a column now accepts water as feed
  (`RefiningRecipe.takesFeed`), though with no log in the slot it makes nothing and uses none, and its catalyst slot now accepts logs. `BasinHints` skips water,
  so a basin of water does not show a "a column makes more methanol" line.
- **Tests.** `MethanolFuelTest` (least energy, spark only, ignition and pre-ignition and cooling against every other fuel, clean burn, injector flow, the
  measured power bands unboosted and boosted, the tint added last), GameTests `aSparkEngineRunsOnMethanolAndItNeverPreignitesUnboosted`,
  `methanolComesFromWaterAndLogsSuperheatedInAColumnOrABasin`, `aColumnOfWaterWithLogsMakesMethanolOverASuperheatedBase`,
  `aColumnOfWaterWithNoLogMakesNothingAndKeepsItsWater`, and methanol added to the tag, report, route, basin-hint and client-audit lists.
- **Not seen:** the bucket and the fluid in a world, the sight windows, the Ponder text (the last line of the Fuels page), how a boosted build feels.

---

## 14. Decisions

All resolved in review; see the table in §0.
