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
| `flywheelInertia` | `1.5` | 0.0 to 100.0 | Rotational inertia a Create Flywheel adds when coaxial with the crank. |
| `castFlywheelInertia` | `1.0` | 0.0 to 100.0 | Rotational inertia a Cast Flywheel adds when coaxial with the crank. |
| `bearingFriction` | `0.01` | 0.0 to 10.0 | Friction torque per crankshaft block. |

### `[limits]`

How far the engine scanner looks. Bigger builds need bigger limits and cost more to scan.

| Setting | Default | Range | What it does |
|---|---|---|---|
| `maxCrankSegments` | `64` | 1 to 1024 | Longest crankshaft line the engine scanner will follow. |
| `maxManifoldBlocks` | `256` | 1 to 4096 | Largest intake or exhaust manifold network the scanner will follow. |
| `maxCoupledLines` | `8` | 1 to 64 | Most crankshafts that can be geared together, one to one, into a single engine. 1 turns coupled crankshafts off: each crank line is then an engine of its own. |
| `maxCouplingWalk` | `512` | 1 to 8192 | Most kinetic blocks the scanner looks through, from a crankshaft, for other crankshafts geared to it. |

### `[balance]`

Balance multipliers. 1.0 is the shipped balance.

| Setting | Default | Range | What it does |
|---|---|---|---|
| `fuelConsumption` | `1.0` | 0.05 to 20.0 | Fuel drawn from the tanks for each unit of fuel an engine burns. Above 1 makes every engine thirstier, below 1 thriftier; the engine's power is unchanged. |
| `precisionBonus` | `1.0` | 0.0 to 2.0 | How far each precision part (cylinder, head, forged piston, injector) moves a cylinder away from the crude one. 0 makes them cosmetic, 2 doubles their effect. |
| `boostStrength` | `1.0` | 0.0 to 2.0 | Scales the boost made by every turbocharger and supercharger. |
| `rotaryPower` | `1.0` | 0.25 to 2.0 | Scales a Wankel rotor chamber's swept volume, and so its power and thirst. Rotary engines are tuned as a niche (compact and smooth but thirsty, weak at low speed); raising this shifts that balance. |
| `heatRecoveryOutput` | `1.0` | 0.1 to 10.0 | Scales the heat an Exhaust Heat Exchanger takes from an engine's exhaust. Create's basins need a heated or superheated source below them, and the exchanger gets there only with enough exhaust heat, so lower values need a bigger engine. |
| `coolingBonus` | `1.0` | 0.0 to 3.0 | Scales how much better a well cooled engine breathes: about 6 to 9 percent more power at 1.0. 0 removes the bonus. Cold engines drag and uncooled ones earn nothing either way, and nothing ever overheats or wears out. |
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

### `[compat]`

Other mods, and how the world around an engine answers it.

| Setting | Default | Range | What it does |
|---|---|---|---|
| `altitudeThinning` | `true` | on or off | The air thins with height: past sea level an engine's intake charge falls, a little at first and more steeply near the build limit, so a naturally aspirated engine loses power as it climbs and a turbocharger or supercharger earns its place. Boost stages offset it. Off, the air is the same at every height. Nothing else about an engine changes, and it never destroys anything. |
| `pollution` | `true` | on or off | With Pollution of the Realms installed, engines put carbon and sulfur into the air through an open exhaust outlet in proportion to the fuel they burn. Without that mod this does nothing. Off, they stay clean. Engine behaviour is never read back from it. |
| `pollutionRate` | `1.0` | 0.0 to 10.0 | Scales how much gas an engine puts out, against the default. 0 is the same as turning pollution off. |
<!-- /generated:server-settings -->

`[meta] carriedOver` is a bookkeeping flag: it is set once the 1.x file has been read (see "From 1.x" below). Leave it alone.

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

2.0 regrouped the server file once. The 1.x file (`create_engines-server.toml`) is left where it is; the first time the 2.0 file
loads it reads the 1.x one beside it, copies every value across under its new name, writes what moved and what it left alone
to the log, and sets `carriedOver` so a value you change later is never overwritten. Names and meanings did not change, only
where they live:

| 1.x key | 2.x key |
|---|---|
| `engine.stressPerTorque`, `journalInertia`, `throwInertia`, `flywheelInertia`, `castFlywheelInertia`, `bearingFriction` | `calibration.` the same name |
| `engine.maxCrankSegments`, `maxManifoldBlocks`, `maxCoupledLines`, `maxCouplingWalk` | `limits.` the same name |
| `balance.*` and `features.*` | unchanged |
| client `effects.engineParticles`, `effects.effectDensity` | `effects.particles`, `effects.density` |
| client `effects.bangVolume`, `engineNoteVolume`, `mechanicalVolume`, `boostVolume` | `volume.bang`, `volume.engineNote`, `volume.mechanical`, `volume.boost` |

A modpack built on 1.x that ships a `defaultconfigs/create_engines-server.toml` should ship the `-v2-` file instead; the old
name is read only to carry values over into a world that has no 2.0 file.
