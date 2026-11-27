# Configuration reference

Every setting the mod has, with its default and range. The tables below are **generated from the source** and a test
(`ConfigDocsTest`) fails if they drift, so what is here is what the mod reads. For *how* to use them (shipping defaults in a
modpack, presets, checking what the game is really using) see [PACKS.md](PACKS.md); for the data files a pack can supply
(fuels, part effects, refining routes) see [DATA.md](DATA.md).

## Where the files are

| File | Where | What it is |
|---|---|---|
| `create_engines-v2-server.toml` | `serverconfig/` inside each **world** | The balance of the world: how engines behave. One file per world, so two worlds can differ. |
| `create_engines-v2-client.toml` | `config/` | A player's own effects: particles and sound volumes. Costs one player's frame rate and nobody else's. |

A modpack that wants its own starting values for new worlds puts a copy of the server file in the game folder's
**`defaultconfigs/`** (the same file name). NeoForge copies it into a world's `serverconfig/` when that world has no file yet,
so it never overwrites a world that already exists. `config/` and `defaultconfigs/` are the two folders a pack ships.

The file is created with every setting and its comment the first time the mod loads. Delete a line to put that setting back to
its default. **A setting the game does not understand (a typo, a value outside its range) is replaced by its default** when
NeoForge loads the file, so after editing run `/ce config`: it prints the values the game is using, and marks each one that
differs from the default with a star.

## Server settings

What the settings do, in groups. **Turning a feature off never destroys blocks**: the machines just stop doing that job. The
three presets (below) are only values of the `[balance]` and `[features]` groups.

<!-- generated:server-settings -->
### `[calibration]`

Units and inertias. The simulation's numbers are converted to Create's here.

| Setting | Default | Range | What it does |
|---|---|---|---|
| `stressPerTorque` | `78.7` | 1.0 to 10000.0 | Stress Capacity (SU per RPM) produced by one unit of simulated engine torque. A crude single cylinder makes roughly 0.4 units at moderate speed. Calibrated so a tuned straight-12 on gasoline (about 60k SU at its best speed) is several times a Create steam engine (16k SU), which it costs far more to build and fuel. |
| `journalInertia` | `0.15` | 0.0 to 100.0 | Rotational inertia of a crankshaft journal block. |
| `throwInertia` | `0.2` | 0.0 to 100.0 | Rotational inertia of a crank throw block. |
| `flywheelInertia` | `2.5` | 0.0 to 100.0 | Rotational inertia a Create Flywheel adds when coaxial with the crank. |
| `castFlywheelInertia` | `1.6` | 0.0 to 100.0 | Rotational inertia a Cast Flywheel adds when coaxial with the crank. |
| `dualMassPrimaryInertia` | `0.45` | 0.0 to 100.0 | Rotational inertia of a Dual-Mass Flywheel's primary mass, bolted to the crank. It sets rev response, spool and starter load, like any flywheel. |
| `dualMassSecondaryInertia` | `1.4` | 0.0 to 100.0 | Rotational inertia of a Dual-Mass Flywheel's secondary mass, behind its spring pack. It smooths the speed ripple the network sees but does not slow the crank, and only above the resonance. |
| `dualMassResonance` | `48.0` | 1.0 to 100000.0 | Crank speed (RPM) at which a Dual-Mass Flywheel's spring pack resonates. The secondary mass does nothing at or below it and counts in full from three times it. |
| `bearingFriction` | `0.01` | 0.0 to 10.0 | Friction torque per crankshaft block. |

### `[limits]`

How far the engine scanner looks. Bigger builds need bigger limits and cost more to scan.

| Setting | Default | Range | What it does |
|---|---|---|---|
| `maxCrankSegments` | `64` | 1 to 1024 | Longest crankshaft line the engine scanner will follow. |
| `maxManifoldBlocks` | `256` | 1 to 4096 | Largest intake or exhaust manifold network the scanner will follow. |
| `maxCoupledLines` | `8` | 1 to 64 | Most crankshafts that can be geared together, one to one, into a single engine. 1 turns coupled crankshafts off: each crank line is then an engine of its own. |
| `maxCouplingWalk` | `512` | 1 to 8192 | Most kinetic blocks the scanner looks through, from a crankshaft, for other crankshafts geared to it. |
| `maxBankBatteries` | `4096` | 1 to 32768 | Most touching batteries that act as one bank. A bigger bank acts as more than one. |
| `throttleIdleMin` | `5` | 5 to 100 | Smallest idle stop a Throttle Body's value box offers, as a percent open (a multiple of 5). Raise it to stop players closing the valve down to a crack. |
| `throttleIdleMax` | `100` | 5 to 100 | Largest idle stop a Throttle Body's value box offers, as a percent open (a multiple of 5). Never below throttleIdleMin: if it is, the minimum is used. |
| `throttleWideMin` | `100` | 5 to 100 | Smallest wide-open bore a Throttle Body's value box offers, as a percent of the plain intake (a multiple of 5). 100 is the plain intake; lower lets a throttle body pinch the intake even at full signal. |
| `throttleWideMax` | `150` | 100 to 150 | Largest wide-open bore a Throttle Body's value box offers, as a percent of the plain intake (a multiple of 5). 150 is the most the simulation allows; lower it to forbid oversized bores. Never below throttleWideMin. |

### `[balance]`

Balance multipliers. 1.0 is the shipped balance.

| Setting | Default | Range | What it does |
|---|---|---|---|
| `fuelConsumption` | `1.0` | 0.05 to 20.0 | Fuel drawn from the tanks for each unit of fuel an engine burns. Above 1 makes every engine thirstier, below 1 thriftier; the engine's power is unchanged. |
| `precisionBonus` | `1.0` | 0.0 to 2.0 | How far each precision part (cylinder, head, forged piston, injector) moves a cylinder away from the crude one. 0 makes them cosmetic, 2 doubles their effect. |
| `boostStrength` | `1.0` | 0.0 to 2.0 | Scales the boost made by every turbocharger and supercharger. |
| `rotaryPower` | `1.0` | 0.25 to 2.0 | Scales a Wankel rotor chamber's swept volume, and so its power and thirst. Rotary engines are tuned as a niche (compact and smooth but thirsty, weak at low speed); raising this shifts that balance. |
| `heatRecoveryOutput` | `1.0` | 0.1 to 10.0 | Scales the heat an Exhaust Heat Exchanger takes from an engine's exhaust. Create's basins need a heated or superheated source below them, and the exchanger gets there only with enough exhaust heat, so lower values need a bigger engine. |
| `coolingBonus` | `1.0` | 0.0 to 3.0 | Scales how much better a well cooled engine breathes: about 6 to 9 percent more power at 1.0. 0 removes the bonus. Cold engines drag and uncooled ones earn nothing either way. Nothing breaks or wears out from heat outside the wear mode (wearMode). |
| `overheatPenalty` | `1.0` | 0.0 to 1.0 | Scales the power an overheating engine loses: from 140 degrees each burn gets less done, up to the most it loses (about a quarter) at 165 degrees, and it comes back as the engine cools. Nothing breaks or stops. 0 turns the loss off. |
| `torsionalFlex` | `1.0` | 0.0 to 2.0 | Scales the rating an engine loses to a long crankshaft: past 8 throws on one unbroken crank line it falls by 2.5 percent a throw at 1.0. A vee or flat has half as many throws as cylinders, and cranks geared together one to one are separate lines. 0 turns it off. |
| `alternatorEnergy` | `2.0` | 0.0 to 100.0 | Forge Energy an Alternator makes each tick for every RPM of its shaft, while something is drawing it. It puts 2 stress units on the shaft for each RPM while it does, so the default of 2 makes one stress unit worth about one FE a tick. 0 turns FE output off; spark charging is unaffected. |

### `[features]`

| Setting | Default | Range | What it does |
|---|---|---|---|
| `forcedInduction` | `true` | on or off | Turbochargers and superchargers make boost (and turbines add back-pressure). |
| `sparkFouling` | `true` | on or off | A spark plug fouls with soot when its engine runs rich. |
| `oilDilution` | `true` | on or off | Unburned fuel thins an engine's oil when it runs rich. |
| `oilAgeing` | `true` | on or off | Engine oil goes off with use and drags a little more until it is changed. Off, oil stays new for ever and the oil exchange setting does nothing useful. |
| `engineTemperature` | `true` | on or off | Engines warm up and cool down: a cold engine drags, a cooled one breathes better. |
| `batteryCold` | `true` | on or off | A battery in a cold biome gives less of its charge, and a cold engine makes each spark cost more of it. A heat source beside the battery, or a warm engine, takes the cold away. Off, charge is the same in any weather. |
| `crankingDraw` | `true` | on or off | A starter draws charge from the engine's batteries while it turns a spark engine over, and turns it more slowly as they run down. Compression engines, and spark engines with no battery, are not affected. Off, cranking is free. |
| `pilotInjection` | `true` | on or off | A compression engine can inject a pilot shot of diesel (from an oil pan's pilot tank) to light gasoline or ethanol that compression alone could not. |
| `rotaryEngines` | `true` | on or off | Rotor housings run. Off, they sit inert and say so in the goggles. |
| `heatRecovery` | `true` | on or off | Exhaust Heat Exchangers take heat from the exhaust and warm basins. |
| `meterAllHeads` | `false` | on or off | Setting the fuel metering of one cylinder head (or rotor housing) sets every other injector of that engine to the same figure. Each head keeps its own dial and can still be changed on its own afterwards; heads held by a MAF sensor or a closed fuel rack are left alone. Off, each head is set by hand as before. |
| `wearMode` | `false` | on or off | Wear and tear. On, pistons, connecting rods and head gaskets wear with use (faster with speed, heat, boost and no oil), a worn piston or gasket costs power, and a badly worn rod can be thrown or a gasket blown, which stops that cylinder until the part is replaced. A spark engine that knocks (boost lighting the charge early) wears its pistons faster, and can be heard. A rotor housing's apex seals wear the same way and a failed set stops the rotor. Batteries wear too: deep discharge, and drawing on them in the cold, slowly reduce what they hold, but never to nothing. Light wear is set right with a Service Kit. Off, nothing wears, no gasket is needed, and every engine runs exactly as it did before the mode existed (a gasket fitted to a head still seals it, on or off). |

### `[compat]`

Other mods, and how the world around an engine answers it.

| Setting | Default | Range | What it does |
|---|---|---|---|
| `altitudeThinning` | `true` | on or off | The air thins with height: past sea level an engine's intake charge falls, a little at first and more steeply near the build limit, so a naturally aspirated engine loses power as it climbs and a turbocharger or supercharger earns its place. Boost stages offset it. Off, the air is the same at every height. Nothing else about an engine changes, and it never destroys anything. |
| `sableAirPressure` | `true` | on or off | With Sable (Create Aeronautics) installed, the air an engine breathes is Sable's own air pressure at the engine's height, so it follows the dimension's pressure curve, datapacks included. Off, or without Sable, the height curve below is used instead. |
| `fuelFire` | `true` | on or off | Liquid fuel lying in the world burns: fire, lava or burning fuel beside a pool lights it, and it burns off in a wave through the pool, leaving fire where it was. Off, fuel on the ground only lies there as before. |
| `fuelFireByVolatility` | `true` | on or off | A spill's fire follows the fuel: gasoline and methanol light surely and burn off in a quick wave, ethanol a little slower, and diesel, biodiesel, kerosene and plant oil are slow to catch and spread. Only fuel lying in the world is affected, never an engine's tank or a running engine. Off, every spilled fuel burns alike. Needs fuelFire. |
| `altitudeStart` | `128.0` | -64.0 to 100000.0 | The height in blocks up to which the air is as thick as at sea level. Used when Sable's pressure is not. |
| `altitudeScale` | `1200.0` | 1.0 to 100000.0 | Blocks of climb over which the air thins by a factor of e. Smaller is steeper. Used when Sable's pressure is not. |
| `altitudeFloor` | `0.25` | 0.01 to 1.0 | The thinnest air an engine is given, as a share of sea level, whichever source says it. Raise it to keep engines running high up, lower it to starve them; the default 0.25 is about the ceiling of a World War II piston engine. |
| `pollution` | `true` | on or off | With Pollution of the Realms installed, engines put carbon and sulfur into the air through an open exhaust outlet in proportion to the fuel they burn. Without that mod this does nothing. Off, they stay clean. Engine behaviour is never read back from it. |
| `pollutionRate` | `0.25` | 0.0 to 10.0 | Scales how much gas an engine puts out. 1 is the full amount the engine would release; the default is a quarter of that. 0 is the same as turning pollution off. |
| `carbonRate` | `1.0` | 0.0 to 10.0 | Scales the carbon an engine puts out, on top of pollutionRate. 0 means no carbon, only sulfur. |
| `sulfurRate` | `1.0` | 0.0 to 10.0 | Scales the sulfur an engine puts out, on top of pollutionRate. 0 means no sulfur, only carbon. |
| `pollutionAreaCap` | `0` | 0 to 100000 | An outlet stops releasing while the air in the 7 by 7 by 7 block cube in front of it already holds this many steps of gas (carbon and sulfur together; one block holds 3 of a gas at most). It starts again as the gas thins or spreads, so a factory stays hazy rather than growing unlivable. 0, the default, turns the cap off; 150 is a starting guess (about 15 percent of the cube's capacity), not a tuned figure. Each outlet reads its air every 3 seconds and counts what it has released since. |
| `pollutionEngineCeiling` | `0.0` | 0.0 to 100000.0 | The most steps of each gas one engine may release per minute of running, however much it burns. 0 turns the ceiling off. |
<!-- /generated:server-settings -->

### `[meta]`

Both files, server and client, end with this group. It is the mod's own bookkeeping, not a setting to tune, so it is not in
the generated tables.

| Setting | Default | Range | What it does |
|---|---|---|---|
| `carriedOver` | `false` | on or off | The mod sets it to `true` the first time the file loads, after looking for the 1.x file beside it (see "From 1.x" below), whether or not there was one. Leave it alone. If it is set back to `false`, or the line is deleted, the next load reads a 1.x file that is still there again, and its values replace the matching ones you have now. |

### Things that go together

- `stressPerTorque` converts the simulation's torque to Create's stress units, and is the one number that decides how
  strong every engine is next to a Create steam engine (16 000 SU). Change it to rebalance *all* engines at once; change
  `rotaryPower` or `boostStrength` to move only one kind.
- `maxCrankSegments`, `maxManifoldBlocks`, `maxCoupledLines` and `maxCouplingWalk` bound how much the scanner looks at when an
  engine is built or changes. Raising them lets bigger builds work and makes each rebuild cost more.
- `fuelConsumption` changes thirst, not power. `precisionBonus` at 0 makes the precision parts cosmetic; at 2 each does twice
  what it does by default (see the blend rule in [DATA.md](DATA.md)).
- Create itself caps rotation at 256 RPM unless you raise `maxRotationSpeed` in `create-server.toml`; engines hit that ceiling.
  The README recommends 512.

## Presets

Three presets ship in the jar (`config_presets/casual.toml`, `standard.toml` and `expert.toml`). A preset only sets values of
`[balance]` and `[features]` and adds no setting of its own; `standard` is exactly the shipped defaults (a test holds that).

<!-- generated:presets -->
| `[balance]` setting | casual | standard | expert |
|---|---|---|---|
| `fuelConsumption` | `0.6` | `1.0` | `1.5` |
| `precisionBonus` | `1.0` | `1.0` | `1.25` |
| `boostStrength` | `1.0` | `1.0` | `1.0` |
| `rotaryPower` | `1.0` | `1.0` | `1.0` |
| `heatRecoveryOutput` | `1.5` | `1.0` | `0.75` |
| `coolingBonus` | `1.5` | `1.0` | `1.0` |
| `torsionalFlex` | `0.0` | `1.0` | `1.5` |
| `alternatorEnergy` | `2.0` | `2.0` | `2.0` |

| `[features]` setting | casual | standard | expert |
|---|---|---|---|
| `forcedInduction` | `true` | `true` | `true` |
| `sparkFouling` | `false` | `true` | `true` |
| `oilDilution` | `false` | `true` | `true` |
| `oilAgeing` | `false` | `true` | `true` |
| `engineTemperature` | `true` | `true` | `true` |
| `batteryCold` | `false` | `true` | `true` |
| `crankingDraw` | `false` | `true` | `true` |
| `pilotInjection` | `true` | `true` | `true` |
| `rotaryEngines` | `true` | `true` | `true` |
| `heatRecovery` | `true` | `true` | `true` |
<!-- /generated:presets -->

Apply one with **`/ce preset <name>`** (an admin command: it rewrites the world's server config), or copy those two tables over
the ones in the file by hand, or ship them in `defaultconfigs/`. A value a preset names that is outside a setting's range is
refused, never clamped, and the command says which.

## Client settings

<!-- generated:client-settings -->
### `[effects]`

| Setting | Default | Range | What it does |
|---|---|---|---|
| `particles` | `true` | on or off | Flame, sparks, smoke, steam and the gas drawn through a cylinder. Off, engines run without particles. |
| `density` | `1.0` | 0.1 to 4.0 | How often each cylinder may bang and puff, against the default. Below 1 thins the effects out (kind to frame rate on a big engine), above 1 allows more. |
| `photosensitive` | `false` | on or off | Photosensitive mode. Takes out everything that flashes or flickers: the flash and fireball of a firing, flame and sparks at the exhaust and intake, and the glowing gas and flame tongues drawn inside each cylinder. Smoke, steam, sound and the rest of the effects are unchanged. |

### `[volume]`

| Setting | Default | Range | What it does |
|---|---|---|---|
| `bang` | `1.0` | 0.0 to 2.0 | Volume of a cylinder's firing bang. 0 mutes it. |
| `engineNote` | `1.0` | 0.0 to 2.0 | Volume of the engine's exhaust note, made up from its speed and firing order as it runs. 0 mutes it, and each cylinder's bang plays at full volume instead. |
| `mechanical` | `1.0` | 0.0 to 2.0 | Volume of an engine's own noises besides its exhaust: the tick of its valve gear, the squeal of dry bearings, the cough as it catches and dies, and hot metal ticking as it cools. 0 mutes them. |
| `boost` | `1.0` | 0.0 to 2.0 | Volume of turbocharger whistle and blow-off and the supercharger whine. 0 mutes them. |
<!-- /generated:client-settings -->

## From 1.x

2.0 regrouped the config files once. Each 1.x file (`create_engines-server.toml` and `create_engines-client.toml`) is left
where it is; the first time its 2.0 file loads it reads the 1.x one beside it, copies every value across under its new name,
writes what moved and what it left alone to the log, and sets [`carriedOver`](#meta) so a value you change later is not
overwritten. Meanings did not change, only where the settings live and, for some client ones, their names:

| 1.x key | 2.x key |
|---|---|
| `engine.stressPerTorque`, `journalInertia`, `throwInertia`, `flywheelInertia`, `castFlywheelInertia`, `bearingFriction` | `calibration.` the same name |
| `engine.maxCrankSegments`, `maxManifoldBlocks`, `maxCoupledLines`, `maxCouplingWalk` | `limits.` the same name |
| `balance.*` and `features.*` | unchanged |
| client `effects.engineParticles`, `effects.effectDensity` | `effects.particles`, `effects.density` |
| client `effects.bangVolume`, `engineNoteVolume`, `mechanicalVolume`, `boostVolume` | `volume.bang`, `volume.engineNote`, `volume.mechanical`, `volume.boost` |

A modpack built on 1.x that ships a `defaultconfigs/create_engines-server.toml` should ship the `-v2-` file instead; the old
name is read only to carry values over into a world that has no 2.0 file.
