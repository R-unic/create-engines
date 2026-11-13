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
| Fuel rack | Redstone on any block of a camshaft line closes that line's rack (15 = fuel cut off), like an inline injection pump. |
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
| Achievements | Advancements live in `advancement/CEAdvancements` as a table, one row per advancement: datagen (`runData`) turns the rows into the advancement JSON and the lang entries. A part gets a `has(...)` row (obtained). Something the engine or a block does gets a `Milestone` constant, fired where it happens (`Achievements.award(level, blocks, milestone)` credits only the engine's operator: the one player who last placed or used one of those blocks, tracked by `Operators`/`OperatorLog` and saved with the level) and a row that waits for it with `on(...)`. One trigger type, `create_engines:event`, carries the milestone name. The engine's own thresholds (`EngineMilestones`, pure Java and unit tested) are checked every ten ticks from `EngineController.syncDiagnostics`, no more than once per milestone per ten seconds. **Every new feature adds rows here.** |
| Engine management | Opt-in, like the governor: with no MAF sensor fitted an engine behaves exactly as before, so a rich-running fault is still there to hit. A **MAF Sensor** is an inline intake pipe (`SensorBlock`, a `ManifoldBlock`, so it joins and scans like a throttle body). It sets each injector's shot from the air the cylinder drew in, whichever way that moves it: `CylinderModel.airMatch` (the sensor's Mixture Target, 80% to 125% of balanced, default 100%) makes the shot `air / airPerMb × target`, trimmed back when the air thins with speed and topped up when it could burn more than the injector was set to give. Fuel Metering therefore no longer applies on a network with a sensor. The most the injector can give is `CylinderModel.INJECTOR_CAPACITY` (2× the usual shot, Fuel Metering's top setting), so very heavy boost can still run leaner than the target. The rack still scales the matched shot (a closed rack is *no fuel*), and so does a compression engine's governor; a spark engine's throttle is already in the air, so its fuel trim is not applied twice. `CycleStats.mafCut` and `mafAdded` say how much of the shot the sensor held back or added. A cut to nothing for want of air reads *no air*, not *no fuel*. The leanest sensor on a network wins; the dyno rating uses the same matched shot. Its redstone output (and comparator output) is the size of the breath, 0 to 15 for 0 to 1.5 bores; goggles say whether it is cutting or adding. `Sensors` (pure Java) turns readings into signals and is unit tested with the sensor's two-way metering (`EngineManagementTest`, plus the `aMafSensorReadsAndTrimsAnEngine` and `aMafSensorTopsUpALeanEngine` GameTests). Recipe uses brass sheets and electron tubes. Ponder page: `maf_sensor`. Achievements: Weighing the Air, Penny Pincher, Topping Up. The lambda sensor and the rev limiter were removed: nothing in the simulation punishes overspeed or rewards reading the exhaust once the MAF sensor feeds both ways, and a governor holds a speed better than a fuel cut. |
| Lubrication | **Engine Oil** (heated mixing of Plant Oil with a slime ball) is piped into any cylinder head, into a second tank beside the fuel tank, and shared along touching heads like fuel. It lubricates the whole engine: crank blocks and every piston and rod. Turning uses it up (about 0.01 mB per part per crank revolution); while the supply keeps up the film builds to full in a few seconds, and without oil it thins over a few hundred revolutions. A dry part drags: friction doubles, and speed-dependent friction quadruples, so a dry engine tops out at roughly half the speed and has less torque to spare, but it still runs. A new engine starts dry. Goggles show the lubrication. |
| Oil sump | A **Sump** is an oil pan: a block with one engine-oil tank (1000 mB) and a fluid capability on every face, so Create pipes and pumps fill it and a bucket (right-click) fills or empties it. Touching any crankshaft block or cylinder head, it belongs to that engine (`EngineLayout.sumps()`), and the engine draws its lubrication oil from the pans before the heads' own oil tanks (`SumpOilSupply`). The point is face economy: Create pipes carry one fluid at a time, so a head used to need a fuel line and an oil line, two of its five free faces. Oil lubricates the whole engine anyway, so it now has one place to come in, away from the heads, and the head keeps only its fuel line. Heads still accept oil on any face, for engines built before the sump existed. Fluid only moves through pipes and pumps (or by hand); nothing pulls from adjacent tanks. |
| Pushrod cams | A Cam Lobe works a cylinder it touches at the **head or at the cylinder block** (head first, then the block's four side faces, skipping the bore axis; the lobe's axis must not point into the face). The block position is a pushrod cam: the cylinder's timing, cam line and goggle readouts are the same, and the head draws no valve stems because the stems are inside. The point is face economy for compact engines: a head already spends faces on its cylinder, its two neighbours in the gallery, fuel and the intake and exhaust, so a V12 built of dense banks had no face left for a camshaft. A cam beside the block leaves both for pipes. A lobe that serves several cylinders times the first with its angle and the rest at the same offset from their own top dead centres (see *Valley cam lobes*), so one camshaft in the valley of a V works both banks. A 90° V12 (six throws, two cylinders each) cannot fire at exactly even intervals, because the second cylinder of every throw is 90° from the first and 60° does not divide that; goggles show the intervals it does get. Overlong goggle lines wrap to the screen (`content/GoggleLines`), and Ponder has pages for firing order (`firing_order`), the compact V (`compact_v`) and the air-fuel recipe (`mixture`). |
| Governor | A **Governor** touches a crankshaft block or head (like a battery) and holds the crank at its Governed Speed (value box storing an exact RPM, 20 to 1000: the panel has a row of exact speeds up to 256 and a row in steps of 5 above that, so it can govern an engine under a raised Create RPM limit, and older saves, which held the setting in steps of 5, are converted when they load; the lowest on an engine governs). A redstone signal on the block commands a share of that setting: none leaves it alone, 1 to 15 asks for that fraction (`Governor.commanded`, never below 20 RPM), so a circuit can set an engine's speed; the lowest commanded speed governs and goggles say when redstone has turned it down. `sim.Governor` is a proportional controller with droop (full rack at 10% below the setting) and a slow reset that integrates the droop away, frozen while the rack is saturated so a start-up or an overload leaves no overshoot. Its output is a rack position, 0 to 1, put in `CylinderModel.governor`: a compression engine is governed on fuel alone, a spark engine on its air (throttle opening 8% to 100%) with the throttle linkage trimming the fuel, because a charge that is too lean will not light. It is deliberately outside the rating: the dynamometer copies leave it at 1, so a governor holds speed with the torque in hand and takes none away. The analog output is the rack position (15 wide open), which reads as a load meter through a comparator; with the rack at 100% the load is more than the engine can hold at that speed. The block is deliberately not a signal source: a block that powered its neighbours would feed its own output back in through any dust beside it as a command. It also burns less fuel than a racing engine when nothing loads it (`GovernorTest`). Achievements: Steady as She Goes, Cruise Control, Speed Dial. |
| Firing order tool | A **Timing Tool** (item) sets an engine's throws and cam lobes for the player. `engine.topology.FiringPlanner` (pure Java) takes each working cylinder's throw, bore offset and lobe and searches throw angles (15 degree steps) and the crank revolution each cylinder fires on by simulated annealing, minimising uneven spacing, then neighbours firing in a row, with a penalty for a cylinder sharing a lobe with another it fires at a different time. Four styles, changed by sneak-use: smooth (any angle), cross-plane (quarter turns), flat-plane (half turns) and cams only (throws left alone, lobes chosen exactly). It reports the firing order and spacing in the action bar, says when a layout cannot fire evenly (a 90 degree V12) or when a lobe is shared, and counts any cylinder it skipped. Achievements: Timing Light, Right on Time. |
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
- Pipes are arms around a core, with a fitting of their own picked by which sides are open: a plain coupling for a straight run, a cast elbow for a bend, a tee across the run for a branch, and the bulged node for anything else (`core_*` models, chosen in `CEBlocks.pipeBlockstate`). They are still boxes, not curved.
  The block models are generated by `tools/generate_models.py`; edit it and rerun rather than the JSON or the sheets.
  Animated parts (piston, rod, valve, crank, cam) keep the pivot and envelope the renderers expect, with detail inside it.
- **Old saves lose their manifolds' connections.** Manifolds used to be stateless; one placed before pipes existed has no
  open ends until a wrench opens them.
- **Spark timing is fixed.** A spark engine's timing is the cam lobe's angle plus a fixed advance; there is no
  distributor and no ignition-advance dial. There is also no knock: pre-ignition of gasoline at high speed just falls
  out of the compression temperature.
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
- **Flywheels.** The Create Flywheel (inertia 1.5, `flywheelInertia`) and the Cast Flywheel (iron, inertia 1.0,
  `castFlywheelInertia`) both count when on the crank's shaft line. Mass also costs spin-up and starter effort.
- **Variable-geometry turbo** (`VariableTurbochargerBlock`, same block entity): vanes close when the exhaust heat is thin
  (`VANES_OPEN_HEAT`), catching up to half again as much of it, and restrict the exhaust between 0.7 times (open) and 1.3
  times (closed) a fixed turbine's back-pressure.
- **Anti-lag** (a 0 to 3 setting beside the boost limit): while every cylinder on the turbine is off the throttle (fuel
  cut, rack shut, throttle closed or governed back) and the engine is turning, it draws `ANTI_LAG_FUEL` mB per second per
  level from the fuel gallery and adds `ANTI_LAG_HEAT` per level to the turbine. The wastegate was already a boost limit
  dial plus a redstone dump.
- **Bank Builder** (`BankBuilderItem`): use on a crank throw to place a cylinder and head, and fit pistons, rods and
  injectors from the player's inventory, on the clicked face of every throw: inline (that face), vee (and the face a
  quarter turn round the crank) or flat (and the opposite face). Pipes and fuel stay the builder's; spark plugs and the cam line are laid only if the builder carries them (see "Boiler heat, readouts, builder extras, muffler and casing").
  Sneak-use cycles the layouts and then between standard and precision parts: a precision build places precision cylinders, heads, forged pistons and precision injectors, and swaps them in for standard parts already on the throws, handing the old ones back. Blocked places and missing blocks are reported. It places no cams: a vee's valley camshaft (see *Valley cam lobes*) is added by hand.
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
  diesel does. Ethanol is the dear one: 250 mB of mash and an apple or mushroom make only 50 mB (it was 100), and biodiesel takes 25 mB of it a batch, so the late-game precision boost builds that want its autoignition margin pay for it in crops. The ferments_mash item tag (apple, brown and red mushroom, c:mushrooms) lists what ferments it. Plant Oil also presses from two saplings (50 mB), alongside six seeds (100 mB).
- **Superheat payoff (2026-11-13).** Nothing in the fuel chain needs a superheated basin to work, but two recipes pay for
  one, which is the late-game reward for a big engine's Exhaust Heat Exchanger (about seven cylinders at speed):
  `mixing/ethanol_superheated` (250 mB mash, a ferment item and a sugar make 100 mB of ethanol, twice the heated yield)
  and `mixing/engine_oil_recycled_superheated` (400 mB used oil, charcoal and a clay ball make 300 mB of engine oil
  against 250). **Why the extra sugar and clay ball:** Create's `BasinOperatingBlockEntity.getMatchingRecipes` ranks the
  matching recipes only by how many item ingredients they have, most first, and ignores heat; a heated recipe still
  matches over a superheated burner. A superheated recipe with no more items than its heated twin would tie or lose
  (the used-oil coking recipe has no items at all), so each superheated recipe carries one more item than the heated one
  it improves on. `FuelRecipeGameTests` pins this: the superheated recipe is the unique top match at a seething burner,
  absent at a kindled one, and the heated recipe still runs when the extra item is missing. Keep this in mind for any
  future heated/superheated pair: give the better one more items.
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

Design rules agreed for this round: nothing wears out or breaks (no part damage, no overheating failure); the only
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
- **Temperature** (`Thermal`). One figure for the whole block, 0 ambient, 0.7 a warm engine on a steady load. Heat in is the
  exhaust heat the cylinders already compute for the heat exchanger; heat out is `PASSIVE x T^2` per cylinder (so an engine
  settles at the square root of its heat, a crawling engine stays warm enough and a boosted one runs hot rather than
  white hot) plus coolant. Coolant carries `100 x quality x (T - 0.4)` heat per cylinder per second and is spent in
  proportion to the heat it carries (0.02 mB per unit at the best quality, twice that for water, which carries half as
  much per mB); there is no radiator, the coolant is used up like water boiling off. Quality is 1 for Engine Coolant and
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


### Turbo and supercharger sounds, and torsional flex (2026-11-02)

- **Sounds.** Three new recordings, synthesized by `tools/generate_sounds.py` (which now falls back to ffmpeg's libvorbis
  when there is no libsndfile, and takes sound names to regenerate only those): `turbo_whistle` and `supercharger_whine`,
  one-second seamless loops, and `turbo_blowoff`, a one-shot with a valve thump, a hiss and a compressor-surge flutter.
  `MachineLoopSound` (client only, like `StarterWhirSound`) plays a loop for any block entity that implements `LoopSource`:
  it fades in and out and takes its volume and pitch from `loopLevel()` and `loopPitch()` every tick. The turbo's loop level
  is its wheel speed above 4 percent and its pitch runs from 0.55 to about 1.8; the supercharger's level is its shaft speed
  over 96 RPM (a third as loud when it feeds no engine) and its pitch 0.6 to 1.8 by 200 RPM. The blow-off plays when redstone
  holds the wastegate open, or when the engine lifts off the throttle (`TurboStatus.offThrottle`, at most once a second), with
  at least 0.25 bar on the gauge a moment before, louder for more boost; anti-lag pops
  reuse the cylinder bang, quieter and higher, at half the rate of the particles. The client config `boostVolume` scales the
  whistle, whine and blow-off; `bangVolume` still scales the pops. The recordings were analyzed (levels, spectrum, loop seam)
  but not listened to; if one is off, adjust its constants in the generator and regenerate it by name.
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
  is. Once a second the server reads the engine's synced `EngineSummary` (from the crank it touches, or that crank's
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
  tenth of a second, and the stream queues four tenths ahead, so the note trails the engine by a few tenths. While the
  note plays the cylinders' (and rotor housings') recorded bangs are skipped: the bang is in the note, as a thump (a
  sine dropping from about 150 to 46 Hz) and a low rumble struck by the same firing as the resonances, so it lands with
  the note by construction. A sample played from the cylinder runs a few tenths of a second ahead of a stream and was
  never going to line up. The client config `engineNoteVolume` scales it (0 off, recorded bangs back at full). The recordings in `build/engine-notes` come from
  `EngineNoteSynthTest.rendersDemoRecordings`. The levels and spectra were measured, and the visual smoke run with the sound
  on showed the sound engine accepting the voice and pulling its stream with no errors, but the sound itself has not been
  listened to by the author of it. A new voice is given 20 ticks to start before it is judged declined (master volume at zero
  declines every one) and only then replaced, so a muted client does not build a synthesizer every tick.
- **Clicks and loudness.** The first note was a click track: a firing was one sample into resonances that rang for six
  thousandths of a second, so each was a spike (crest factor up to 15, a jump from sample to sample up to 13 times the
  level). A firing is now a pressure pulse smoothed over a thousandth of a second, into resonances at 86, 172 and 345 Hz
  that ring for about 20, 12 and 7 thousandths, with a lowpassed chuff for the rush of gas; the same measures are now a
  crest of 2 to 6 and a step under the level. `EngineNoteSynthTest.noFiringIsAClick` holds that. The whole note is about
  six decibels louder. Past about a hundred firings a second (a four at 3000 RPM, a V8 at 1500) the narrow resonances
  stopped lining up with the pulses and the note fell to half its level in a step, and then sounded thin; the firings
  themselves, softened, now come in as a tone at the firing rate, and a slow gain (0.3 s) holds the level across the
  range, only for fast engines so a slow engine's lope is untouched (`theNoteDoesNotChangeCharacterAtAnySpeed`). The
  turbo whistle (0.09) and
  supercharger whine (0.2) are quieter than before, because a tone in the kilohertz carries much further to the ear than
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
- **Blocks are wash-proof.** Every block is registered through `CEBlocks.part`, which adds `forceSolidOn()`:
  crankshafts, cams and pipes have shapes too slight to count as solid, and flowing water used to break them and
  drop them as items. Found when the submerged GameTest's water took the crank journal out.
- One advancement, That's Not Air, for cranking an engine that is underwater either way. GameTests:
  `submergedCylinderNeverFiresWhateverItsPipesReach`, `sinkingARunningEngineStallsIt`,
  `floodedIntakeNeverFiresUntilItIsDrained`, `floodingTheExhaustStallsARunningEngine`, `waterWashesNoEngineBlockAway`.
- **Exhaust** (`content.effects.ExhaustLook`, pure and unit-tested, drawn by `EngineEffects.exhaust`). One look per
  exhaust stroke from the cylinder's last cycle and the engine's figures: clean haze that grows with load, black by how
  far past rich the mixture is, white while the block is below `Thermal.COLD_BELOW`, blue with oil dilution, a pale mist
  for unlit fuel, a darker thicker cloud for thirty ticks after catching. A rich engine working hard licks flame; unlit
  fuel into the pipe of a warm, otherwise firing engine pops three times in ten (`exhaust_pop`, on the bang volume).
  Each firing leaves by one outlet picked at random and throws fewer puffs past four cylinders, so particles do not
  multiply with outlets or grow in step with cylinders.
- **Particles** (`CEParticles`, `client.particle`). `gas` takes a colour, size, opacity, lift and lifetime, swells
  and fades, and is drawn from four white puffs made by `tools/generate_textures.py` in the manner of vanilla's
  campfire smoke (vanilla's own are grey, so they cannot be tinted white, and its small smoke set shrinks to a speck).
  The game's particle shader discards anything fainter than 0.1, so no look starts below about 0.25.
  `exhaust_flame` is vanilla's flame, short-lived and aimed; `spark` is one bright pixel that falls and cools.
- **Around the engine** (`EngineEffects`). A wisp of air drawn into an intake on each intake stroke; bubbles from any
  flooded mouth and off the wet faces of a submerged cylinder while the crank turns; rain boiling off a head above temperature 0.35, and a shimmer over a running one
  above 0.62 (so coolant shows); sparks off crank throws below 30 percent lubrication and off a starter's pinion; a
  burst of white air when a blow-off valve opens.
- **Machinery sounds** (`EngineAmbience`, kept by the primary crank on the client; levels in `MechanicalNoise`, pure).
  `engine_catch` on CRANKING or STALLED to RUNNING and `engine_stall` on a turning engine reaching STALLED, each at most
  once in three seconds; `engine_clatter` (valve gear, pitched with the crank, louder rough or dry) and
  `bearing_squeal` (below 30 percent lubrication) as `MachineLoopSound` loops; `metal_tick` at random for about twenty
  seconds after a warm engine stops, from a heat figure the client lets decay. All on the new `mechanicalVolume`
  setting. Recordings are synthesized by `tools/generate_sounds.py`, which can now write WAV previews (`--wav DIR`).
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
  `EngineController.diagnose`, carried in `EngineSummary` and shown as one line and its cause under the engine state.
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
  existing `heatRecoveryOutput` and `heatRecovery` settings. About 20 heat per second makes a seething exchanger, so
  roughly seven cylinders at speed make two boiler levels per exchanger (by Create's own rules, up to two steam engines
  at full efficiency, if the boiler is big enough and has the water), and exchangers on one network split the heat. Advancement: Combined Cycle (`COMBINED_CYCLE`, a hot exchanger under a boiler that has engines on it and
  is taking heat). Not played end to end: the GameTest checks what Create is told, not a running boiler.
- **Readouts** (`content.display`, `CEDisplaySources`). Create's Display Link reads a block through a `DisplaySource`
  registered with `REGISTRATE.displaySource` and attached to the block with `DisplaySource.displaySource(...)`.
  Crankshaft, crank throw and rotor housing offer Engine Status (state, RPM, load, temperature and oil, a line each),
  Engine Speed (one RPM line for a nixie tube) and Engine Load, Temperature and Oil Condition (a percentage or progress
  bar, chosen the way Create's fill level source is). Load is the network's torque over what the engine can give
  (above 100 is overloaded); temperature is the block's heat over a warm engine's, so 100 is warm; oil is the
  condition, 100 new. The oil sump offers Oil Sump Levels, one tank to a line. Every source refreshes every second
  (`EngineReadout.REFRESH_TICKS`). The figures come from the synced `EngineSummary`, so they cost the engine nothing.
  Advancement: Instrument Panel (`ENGINE_DISPLAYED`, a link reading a running engine). A Display Link has not been set up
  through its screen in play: the GameTest checks that every source is attached to the right blocks.
- **Sump comparator.** The sump has a second dial, above the Oil Exchange one, that picks which tank a comparator reads:
  Engine Oil, Used Oil, Coolant or Pilot Fuel. The signal is 0 for an empty tank and 1 to 15 as it fills, 15 only when
  full (`SumpBlockEntity.comparatorSignal`), refreshed every ten ticks, so a pump can top the oil up or an alarm can
  sound when it runs low. Advancement: Dipstick (`SUMP_METERED`, a comparator reading the pan of a running engine).
- **Schematic pricing.** Fitted parts are block state flags (see `PartInstallation`), and Create prices a block by its
  item alone, so a Schematicannon used to build a finished cylinder, head or rotor housing with its pistons, rods,
  injectors, plugs and rotor for free. Those three blocks now implement Create's `SpecialBlockItemRequirement`
  (`PartInstallation.requirement`): the block plus every fitted part, precision grade respected.
- **Bank Builder extras.** A builder that is carrying spark plugs fits one in each head it works on, and one carrying a
  Cam Lobe lays a cam line parallel to the crank: a lobe at each throw's offset and Camshaft blocks in the gaps so the
  line is unbroken (`BankBuilderItem.camOffsets`). The offset is the cell beside each cylinder block, on the side a quarter
  turn round the crank from the bore: a flat gets a line by each bank, and a vee, whose second bank is on that very side,
  gets one lobe in the valley that touches the block of both banks, which the shared-lobe timing already handles. The
  extras use the player's real inventory even when creative (a creative builder otherwise has everything and would
  put plugs in every diesel), and a creative player's stack is not used up. The line still has to be geared to half the
  crank's speed, and the Timing Tool times the lobes. Advancement: Cam Included (`CAMS_LAID`). (The Timing Tool already
  planned rotor housings; the "rotors are not planned" note above was out of date.)
- **Muffler** (`MufflerBlock`, `engine.sim.Muffling`). An inline exhaust duct like the intercooler: pipes join its two
  ends. It changes sound only: no power, no back-pressure, no simulation change at all. The scanner lists the mufflers on
  each exhaust `PortNetwork` and, as for turbos, whether the run can go round them (`parallel`). A run is fully muffled by
  one every path goes through and half muffled by one it can go round; the engine's `muffling` (in `EngineSummary`,
  synced) is the average of its exhaust runs weighted by the heads they serve, so one bank of a V muffled is half. The
  client scales the exhaust note, the cylinder and rotor bangs and the exhaust pops by `Muffling.volume` (a fully muffled
  engine is a fifth as loud); the mechanical sounds (clatter, squeal, catch and stall) and the turbo and blower are not
  the exhaust and are left alone. Goggles on a crankshaft say how much of the exhaust is muffled. Advancement: Hush Now
  (`MUFFLED`, a running engine at half muffled or more). The volume was calculated, not listened to.
- **Engine Casing.** A plain cube (riveted plate, steel top and bottom) with no block entity and no behaviour: it joins
  no pipe, belongs to no engine and costs the scanner nothing. It is there to hide the sprawl. Advancement: Under the Hood
  (`CLAD`, a running engine with four or more casing blocks against its cylinders).
  Since the casing rework it is drawn as a framed panel (steel beams and brass corner bolts round recessed riveted
  plate), and it **covers shaft parts**: use one on a crankshaft, crank throw, camshaft or cam lobe and the part gets an
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

---

## 14. Decisions

All resolved in review; see the table in §0.
