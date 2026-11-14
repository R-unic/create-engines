<p align="center"><img src="docs/images/icon.png" alt="Create: Engine Engineer" width="160"></p>

# Create: Engine Engineer

An addon for [Create](https://github.com/Creators-of-Create/Create) that lets you build working internal combustion
engines out of individual parts and turn them into Create rotational force. Start with a crude single cylinder, finish
with a boosted, precision-built V12.

Minecraft 1.21.1 · NeoForge 21.1.219 · Create 6.0.10

> [!NOTE]
> **Built with AI.** Nearly all of the code, models, textures, sounds, Ponder scenes and docs were written by an AI
> (Claude) at my direction. I have played, tested and reworked almost all of it over many passes, and it ships with
> unit tests and in-world GameTests. It is still a young mod, but you shouldn't have to expect things to break. If something
> does, please report it on the issue tracker. From 1.0, block, item and fluid IDs, config keys and ranges, advancement IDs and the fuel
> data-map format stay stable within 1.x, and a release that changes how an existing engine behaves says so in its
> changelog.

## Preview

![A coupled two-crankshaft engine](docs/images/engine_coupled_overview.png)

| | |
|---|---|
| ![Engine with management sensors](docs/images/engine_management_overview.png) | ![Engine built from precision parts](docs/images/engine_precision_overview.png) |
| Intake and exhaust management | Precision parts |
| ![Flywheel, dynamometer and turbos](docs/images/engine_new_parts.png) | ![Goggle readout on a firing cylinder](docs/images/engine_goggles_cylinder.png) |
| Flywheel, dynamometer and turbos | Diagnostics with Engineer's Goggles |

## Features

**Build an engine**
- Crankshafts, throws, cylinders, heads, pistons, rods, camshafts and valves, assembled block by block; cylinders fire
  in order and the crank animates with them
- Inline, V, flat and rotary (Wankel) layouts, with coupled crankshafts for bigger engines
- **Torsional flex**: a crank twists under load, so past eight throws on one unbroken crankshaft an engine's rating
  falls by 2.5 percent a throw, to at most 35 percent. A straight twelve loses about a tenth; a V12 or flat twelve (six throws) loses nothing,
  and cranks geared together one to one count as separate lines. It takes from the rating only, never harms anything
- Cam lobes that sit beside the cylinder block, so a V can run one camshaft in its valley and keep every head face
  free for pipes
- A **Bank Builder** that lays out a whole inline, V or flat bank in one click (it fits the spark plugs you carry, and
  lays a cam line beside the bank if you carry a camshaft and cam lobes), and a **Timing Tool** that sets the
  firing order for you

**Feed and tune it**
- Air, fuel and exhaust plumbing with pipes, throttle bodies (a pedal that can also open past the plain intake, to help a big engine turn faster) and fuel metering; diesel, plant oil, gasoline and ethanol
  with spark ignition
- A craftable fuel chain: seeds to plant oil to diesel, sugar crops to ethanol, and biodiesel with glycerin on the
  side, all in Create basins. The loop closes: glycerin goes into engine oil and coolant, and used oil is burned as fuel or recycled into oil or charcoal
- An **Exhaust Heat Exchanger** that turns engine exhaust into basin heat, so an engine can cook its own fuel, and
  into boiler heat, so the exhaust can make steam for Create steam engines on top of the engine's own power
- Oil film and lubrication, starters, batteries and alternators
- Forge Energy out of an alternator, and other mods' fuels (anything tagged `c:diesel`, `c:gasoline`, `c:ethanol` or `c:plantoil`, or given an entry in the fuel data map) in the engines
- An **oil sump that is the engine's service port**: one block against the engine takes its oil, its coolant and its
  pilot fuel from any face, so the cylinder heads keep their faces for air and fuel
- **Oil that goes off**: a spent charge drags a little more (never stops or damages anything). Set the sump's Oil
  Exchange dial and a running engine changes its own oil, pushing out **Used Oil** that you burn, refine, coke into
  charcoal or let go. An Oil Filter slows the ageing and cleans fuel out of the oil
- **Engine temperature**: a cold engine drags until it warms through, and a cooled one breathes better (about 4 to 9
  percent more power). Coolant, or just water, goes into the same sump and is spent carrying heat away, so there is no
  radiator to fit. Nothing ever overheats or wears out
- **Warm-up and cooling**: a cold block burns fuel a little worse (ethanol and pressed oil most, gasoline least) and
  warms quickest at idle. A Blaze Burner against the heat exchanger preheats it. A stopped engine ticks, shimmers and
  its turbine wheel glows red as it cools, and goggles call it Cooling down
- **Plain-language diagnosis**: goggles name an engine Healthy, Running rough or Failing with the one cause to fix
  first, say why a cylinder misfired, and the Dynamometer shows how a new run compares with the last. Two Ponder
  pages cover why an engine will not start and why it runs badly
- **Dual-fuel pilot injection**: pipe diesel into the sump's pilot tank and a compression engine with no spark plug
  lights gasoline or ethanol with a small pilot shot
- A forgiving early game: spark plug soot clears itself, goggles suggest fuel settings, and the Timing Tool can tune
  every injector in one click
- Governors and MAF sensors for the late game
- **Engine readouts**: a Display Link set against a crankshaft shows an engine's state, speed, load, temperature and
  oil on a display board or nixie tubes, and a comparator reads any of an oil sump's tanks, so a pump or an alarm can
  keep the oil topped up
- Fuel buckets say what they are on hover: their energy, whether they light by compression or need a spark plug, and
  how they take a cold start (other mods' fuels too)

**Make it powerful**
- Turbochargers (stacked, twin, variable-geometry, with anti-lag), superchargers (plain and variable-drive) and intercoolers, with the sound to
  match: a turbo whistle that climbs with the wheel, a blow-off flutter when redstone dumps the boost or the throttle lifts, anti-lag
  crackle, and a supercharger whine that rises with its shaft
- Precision cylinders, heads, pistons and injectors
- Flywheels that matter: a rough engine is rated for less, and rotating mass smooths it out
- A 13-gear transmission with an Auto mode that shifts to suit the engine, and a **Dynamometer** that draws an engine's torque and power curves
- A **Black Box** flight recorder that keeps ten minutes of an engine's speed, load and warmth and says why it stalled
- An exhaust note made up from the engine's speed and firing order, so a V-twin lopes, a six hums and a dead cylinder limps
- A **Muffler** that quiets the note, the bangs and the pops without costing the engine any power, and **Engine Casing**
  that joins into one plate and clads cylinders, pipes, cams, and Create's own shafts and cogwheels to hide the sprawl
- Exhaust that tells the truth: a thin haze when the mixture is right, black when it is rich, white vapour until the
  block warms, a blue cast when fuel has thinned the oil, and a crack of flame when unburned fuel meets a hot pipe.
  Engines cough as they catch and knock as they die, tick as they cool, squeal and spark when they run dry, draw the
  air in at their intakes and steam in the rain
- Nothing burns underwater: water against a cylinder, a head or a rotor housing stops the engine wherever its pipes
  lead, and so does an intake or exhaust that opens in it. A flood stops an engine; it does not wash it away

**Learn it**
- Engineer's Goggles diagnostics on every part, Ponder scenes for every part, and advancements that reward actually
  using what you build, grouped into tracks (builder, power, tuning, driveline, fuel, service) that each end in a
  capstone, and a Chief Engineer advancement for finishing them all

| | |
|---|---|
| ![Bank Builder Ponder scene](docs/images/engine_ponder_bank_builder_800.png) | ![Firing order Ponder scene](docs/images/engine_ponder_firing_order_960.png) |
| ![Rotary engine Ponder scene](docs/images/engine_ponder_rotor_560.png) | ![Vanes and anti-lag Ponder scene](docs/images/engine_ponder_turbo_tuning_650.png) |

## Configuration

The server config (`serverconfig/create_engines-server.toml` in the world) carries the engine calibration (`[engine]`:
`stressPerTorque`, the inertia and friction values, and the limits on how far the scanner follows a crankshaft, a manifold
or a coupling) plus
`[balance]` multipliers (`fuelConsumption`, `precisionBonus`, `boostStrength`, `rotaryPower`, `heatRecoveryOutput`, `coolingBonus`, `torsionalFlex`, `alternatorEnergy`)
and `[features]` switches (`forcedInduction`, `sparkFouling`, `oilDilution`, `oilAgeing`, `engineTemperature`,
`pilotInjection`, `rotaryEngines`, `heatRecovery`), so a
modpack can rebalance without code. Turning a feature off never removes blocks; they stop doing that job. The client
config (`engineParticles`, `bangVolume`, `engineNoteVolume`, `mechanicalVolume`, `boostVolume`, `effectDensity`) thins out engine sound and particles on a big engine.
Fuel properties are a datapack data map (`data_maps/fluid/fuel.json`), so other mods' fluids can be made into fuels.

## Recommended: raise Create's RPM limit

Create caps rotation at 256 RPM by default, and engines hit that ceiling. We recommend raising it: in
`serverconfig/create-server.toml` (or the world's `serverconfig` folder) under `[kinetics]`, set `maxRotationSpeed` to
512 (higher works too). The engine simulation is built to stay cheap at speed, and its torque rating follows the
limit you set. Everything else on the kinetic network still obeys Create's own rules.

## Building

```bash
./gradlew build
```

Other useful tasks: `./gradlew test` (simulation unit tests), `./gradlew runGameTestServer` (in-world GameTests) and
`./gradlew runVisualSmokeTest` (builds an engine and saves screenshots to `run/screenshots/`).

Design notes and how the simulation works are in [docs/DESIGN.md](docs/DESIGN.md). The 1.0 release checklist is in
[docs/RELEASE_1_0.md](docs/RELEASE_1_0.md).

## License

[MIT](LICENSE). Use it, change it, ship it in modpacks, no permission needed.
