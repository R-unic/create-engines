# Release listing (CurseForge / Modrinth)

Copy-paste material for the project pages. Not shipped in the jar.

## Basics

| Field | Value |
|---|---|
| Name | Create: Engine Engineer |
| Slug | create-engine-engineer (check it is free on both sites) |
| Icon | `docs/images/icon.png` (512x512). The jar carries a 256x256 copy as `logo.png` |
| Summary (CurseForge, 250 max) | Build real engines piece by piece: V8s, flat sixes, Wankels, twin-turbos. Tune them, hear them roar, and turn their power into Create rotation. |
| Short description (Modrinth, 256 max) | Same as the summary. |
| Categories | Technology, plus the Create addon tag where the site offers one |
| Game version | 1.21.1 |
| Loader | NeoForge |
| Dependencies | Create 6.0.10 (required). Pollution of the Realms (optional; list it as an optional dependency where the site has one). JEI, EMI, KubeJS, CC: Tweaked and Create Aeronautics are integrations, not dependencies |
| Environment | Client and server (required on both) |
| License | MIT |
| Source | https://github.com/R-unic/create-engines |
| Issues | https://github.com/R-unic/create-engines/issues |
| Release type | Release (stable since 1.0; use Beta or Alpha only for tagged pre-releases, see Publishing) |
| AI disclosure | Yes. The "About this mod" section of the description below, plus the site's own AI-content field where it has one |

Image links in the description use `raw.githubusercontent.com`, so they only work once the repository (with
`docs/images`) is pushed to GitHub. CurseForge's editor wants rich text: paste the description into its source (HTML)
view after converting it, or retype the headings and lists. Modrinth takes the Markdown as it is.

## Description

```markdown
# Create: Engine Engineer

### Build a real engine, one part at a time, and hear it run.

![A V8 from the exhaust side, running](https://raw.githubusercontent.com/R-unic/create-engines/master/docs/images/engine_showcase_v8_exhaust.png)

An addon for **Create** with working internal combustion engines. Place the crankshaft, cylinders, heads, pistons, cams
and valves yourself, pipe in air and fuel, and the engine you built fires in order, sounds like what it is, and drives
your Create machines.

> 🔊 **It has a voice.** The exhaust note is made from the engine's speed and firing order, so a V-twin lopes, a six
> hums and a dead cylinder limps. Turbos whistle, blow off and crackle. Turn your sound up.

---

## ⚡ At a glance

| | |
|---|---|
| **Layouts** | Inline, V, flat and Wankel, with coupled crankshafts up to a V12 |
| **Fuels** | Diesel, plant oil, gasoline, kerosene, ethanol, methanol and biodiesel, plus other mods' fuels |
| **Power** | Turbos, superchargers, intercoolers, flywheels and a 13-gear transmission |
| **Tools** | Bank Builder, Timing Tool, Blueprints, Dynamometer, Black Box recorder, Engineer's Goggles |
| **Learning** | A Ponder scene for every part, and advancement tracks |
| **Works with** | Create Aeronautics (thin air), Pollution of the Realms, JEI, EMI, KubeJS, CC: Tweaked |
| **For packs and addons** | Config presets, data-driven parts and fuels, a `/ce` command, and a frozen public API |
| **Needs** | Minecraft 1.21.1, NeoForge, Create 6.0.10 (required on client and server) |

---

## 🛣️ From one cylinder to a V12

| Stage | What you build |
|---|---|
| **Start** | A single diesel cylinder and a flywheel. The early game is forgiving, and nothing ever overheats or wears out |
| **Grow** | An inline six or a V8, laid out in one click with the Bank Builder, fed by your own plant-oil and ethanol chain |
| **Tune** | Turbos, superchargers, precision parts, a transmission, and the Dynamometer to prove it worked |
| **Master** | Coupled-crank V12s, Wankel rotaries, a Refinery column, and engines that heat your boilers and basins |

---

## 🔧 Build it

- **Real layouts:** inline, V, flat and rotary (Wankel), with coupled crankshafts for bigger engines
- **Bank Builder:** lays out a whole inline, V or flat bank in one click (it fits the spark plugs you carry, and lays a
  cam line beside the bank if you carry a camshaft and cam lobes)
- **Timing Tool:** sets the firing order for you, and can tune every injector in one click
- **Blueprints:** record a built engine's layout and every dial, copy the whole tune onto another engine of the same
  build, build it from the parts you carry with the Bank Builder, export it as a Create schematic, share it as text, or hang
  it on a wall in a Framed Blueprint
- **Cams beside the block:** a V can run one camshaft in its valley and keep every head face free for pipes
- **Breathing with diminishing returns:** each extra intake or exhaust opening on a head adds less than the last, and
  openings that touch count half, so a tidy layout beats a forest of mouths
- **Torsional flex:** past eight throws on one unbroken crankshaft an engine's rating falls a little for each extra
  throw, to at most 35 percent. A V12 or flat twelve loses nothing, and nothing is ever harmed

## ⛽ Feed it

- **A fuel chain in Create basins:** seeds to plant oil to diesel, sugar crops to ethanol, and biodiesel with glycerin on
  the side. The loop closes: glycerin goes into engine oil and coolant, and used oil is burned, refined, or recycled
  into oil or charcoal
- **Refining column:** a Refinery of Reboilers, Column Sections and Condensers that makes fuel more efficiently than a
  basin
- **Air, fuel and exhaust plumbing:** pipes, throttle bodies and fuel metering, with spark ignition where the fuel
  needs it
- **Oil sump:** one block against the engine takes its oil, coolant and pilot fuel from any face
- **Oil that goes off:** a running engine can change its own oil, pushing out Used Oil. An Oil Filter slows the ageing
- **Dual-fuel pilot injection:** a compression engine with no spark plug lights gasoline or ethanol with a small pilot
  shot of diesel
- **Exhaust Heat Exchanger:** turns exhaust into basin heat, so an engine can cook its own fuel, and into boiler heat,
  so the exhaust can make steam for Create steam engines
- **Other mods' fuels work:** anything tagged `c:diesel`, `c:gasoline`, `c:ethanol`, `c:kerosene`, `c:methanol` or `c:plantoil`, or given an entry
  in the fuel data map
- Starters, batteries and alternators, with Forge Energy output

## 🚀 Make it powerful

- **Forced induction:** stacked, twin and variable-geometry turbochargers (with anti-lag), plain and variable-drive
  superchargers, and intercoolers
- **Precision parts:** precision cylinders, heads, pistons and injectors
- **Thin air:** above y 128 the air thins, so a naturally aspirated engine loses power as it climbs and a turbo or
  supercharger earns its place. Made for Create Aeronautics aircraft; engines at or below y 128 are unaffected, and a
  config switch turns it off
- **Flywheels that matter:** a rough engine is rated for less, and rotating mass smooths it out
- **A 13-gear transmission** with an Auto mode that shifts to suit the engine
- **Dynamometer:** draws an engine's torque and power curves, and compares a new run with the last
- **Governors and MAF sensors** for the late game
- **Black Box:** a flight recorder that keeps ten minutes of speed, load and warmth and says why an engine stalled
- **Engine temperature:** a cold engine drags until it warms through, and a cooled one breathes better. Water or coolant
  goes into the same sump, so there is no radiator to fit

## 👂 Hear it, see it

- Exhaust colour that reads the mixture, flame from a hot pipe, a cough on starting, and ticking as it cools
- A turbo whistle that climbs with the wheel, a blow-off flutter when redstone dumps the boost or the throttle lifts,
  anti-lag crackle, and a supercharger whine that rises with its shaft
- A clatter or squeal when an engine runs dry. Nothing runs underwater
- **Muffler:** quiets the note, the bangs and the pops without costing any power
- **Engine Casing:** joins into one plate and clads cylinders, pipes, cams, and Create's own shafts and cogwheels to
  hide the sprawl, in plain, riveted, louvred and window styles, and any dye
- **Engine readouts:** a Display Link set against a crankshaft shows state, speed, power, load, temperature, oil, boost,
  rated torque and the engine's fault on a display board or nixie tubes, one set against a battery shows its charge, one
  set against a transmission shows its gear, and a comparator reads any of an oil sump's tanks

## 🎓 Learn it

- **Engineer's Goggles** name an engine Healthy, Running rough or Failing, with the one cause to fix first
- **Ponder scenes** for every part
- **Advancements** that reward actually using what you build, grouped into tracks (builder, power, tuning, driveline,
  fuel, service) that each end in a capstone, with a Chief Engineer advancement for finishing them all
- **Hover tooltips** on fuel buckets say what they are: energy, whether they light by compression or need a spark plug,
  and how they take a cold start

---

## 📈 Recommended: raise Create's RPM limit

Create caps rotation at 256 RPM by default, and engines hit that ceiling. Raise it: in
`serverconfig/create-server.toml` under `[kinetics]`, set `maxRotationSpeed` to 512 (higher works too). The engine
simulation is built to stay cheap at speed, and its torque rating follows the limit you set.

## 📦 For modpack makers

The server config (`serverconfig/create_engines-v2-server.toml`) has `[balance]` multipliers, `[features]` switches and
`[compat]` settings, so a pack can rebalance without code. Turning a feature off never removes blocks; they stop doing that
job. Three presets ship in the jar (casual, standard, expert), and `/ce preset <name>` applies one. The client config
(`create_engines-v2-client.toml`) thins out engine sound and particles on a big engine. A 1.x config is read once and carried
over, and the log says what moved.

Everything else is data: fuel properties are a datapack data map (`data_maps/fluid/fuel.json`), so other mods' fluids can be
made into fuels; the four precision parts are `part_effects/<part>.json`; a cylinder or the rotary chamber can be retuned with
the `part_stats` data map; and refining routes are a data recipe type, `create_engines:refining`. In game, **`/ce`** (game
master) shows what the config and the data packs are really doing: `/ce config`, `/ce fuels`, `/ce parts` and `/ce api`.
The guides are in the repository: [PACKS.md](https://github.com/R-unic/create-engines/blob/master/docs/PACKS.md),
[CONFIG.md](https://github.com/R-unic/create-engines/blob/master/docs/CONFIG.md) and
[DATA.md](https://github.com/R-unic/create-engines/blob/master/docs/DATA.md).

## 🧩 For addon authors

A public API under its own version (1.0.0, frozen at 2.0): read any engine (`EngineApi.viewAt`), listen to its state changes
and milestones, and register an `EngineSourceProvider` with a `TorqueSource` to put another kind of engine on a crankshaft,
with boost, goggle lines and blueprint support. A working example is built and tested with the mod. See
[ADDONS.md](https://github.com/R-unic/create-engines/blob/master/docs/ADDONS.md) and
[API.md](https://github.com/R-unic/create-engines/blob/master/docs/API.md).

## 🤝 Works with

All optional; none is needed to play.

- **Create Aeronautics:** the thin-air rule is for it. The mod's tests run alongside Aeronautics 1.3.2 and Sable 2.0.6, and
  one engine on one wheeled contraption has been tried by hand. Other contraptions are untried, so treat it as early
- **Pollution of the Realms:** engines put carbon and sulfur into the air through an open exhaust outlet, more when they run
  rich and less with a Soot Trap fitted. Nothing is read back into the engine, and a setting turns it off
- **KubeJS** and **CC: Tweaked:** read-only access to an engine's state, for scripts and computers
- **JEI** and **EMI:** every fuel, refining route and engine part

## ✅ Requirements

- Minecraft 1.21.1, NeoForge 21.1.219 or newer
- Create 6.0.10 (all of its own requirements, Flywheel and Ponder, come with it). Use Create 6.0.10 build 257 or newer: tested on 6.0.10-257 to 6.0.10-281 and on 6.0.11-346 (tested only: 6.0.11 is not recommended, because Create Aeronautics crashes on it, so stay on 6.0.10). On the older 6.0.10 builds (217 to 256) the mod loads and runs, but in our tests the Transmission moved from Neutral to Auto did not drive the machines behind it, so update Create if you use the Transmission.
- Required on both the client and the server

## About this mod

Nearly all of the code, models, textures, sounds, Ponder scenes and docs were written by an AI (Claude) at my
direction. I have played, tested and reworked almost all of it over many passes, and it ships with unit tests and
in-world GameTests. If something breaks, please report it on the
[issue tracker](https://github.com/R-unic/create-engines/issues). Block, item and fluid IDs, config keys and ranges,
advancement IDs, the data-map formats and the public API stay stable within a major version. 2.0 was the one release that
renamed things (advancement IDs, config files); it carries your advancements, config and casing dye across, and a release
that changes how an existing engine behaves says so in its changelog.

## 🔗 Links

- [Discord](https://discord.gg/QYVpy2dqGE), for help, showcases and suggestions
- [Source and issue tracker](https://github.com/R-unic/create-engines)
- [Design notes](https://github.com/R-unic/create-engines/blob/master/docs/DESIGN.md), how the simulation works
```

## Changelog for 2.0.0

Open Block: the mod becomes a platform (a public API, engine types from other mods, parts as data), and 2.0 is the one
release that breaks the 1.x promise, so the cleanup that needed breaking is done here. Every world, config and advancement
from 1.x carries over. Five things change how something that already exists behaves or is named, each below: advancement IDs,
config files and groups, how many intake and exhaust openings an engine needs, thin air above y 128, and where casing dye is
stored. Everything else is new.
- Breaking, advancements: the 110 advancement IDs of 1.x are gone. The tab is regrouped into tracks and each row is
  `<track>/<row>` (for example `builder/vee_engine`; the tab root is `root` and `chief_engineer` is unchanged): 64 rows plus the
  root. The 46 hidden advancements are deleted, and `driveline/every_gear` is now shown. A player's progress carries over by
  itself, silently (no toast, chat line or sting), the first time they load in 2.0. A datapack, command or other mod that names an
  old ID breaks.
- Breaking, config: the server file is now `create_engines-v2-server.toml` and the client file `create_engines-v2-client.toml`.
  Server `[engine]` is split into `[calibration]` (`stressPerTorque`, the inertias, `bearingFriction`) and `[limits]` (how far the
  scanner follows a crankshaft, a manifold or a coupling); `[balance]` and `[features]` are unchanged and `[compat]` is new.
  Client `[effects]` keeps `particles` and `density` (were `engineParticles` and `effectDensity`) and a new `[volume]` holds
  `bang`, `engineNote`, `mechanical` and `boost` (were `bangVolume`, `engineNoteVolume`, `mechanicalVolume` and `boostVolume`).
  The first time a 2.0 file loads it reads the 1.x file beside it, copies every value across and logs what moved; the 1.x files
  are left as they were. A pack's `defaultconfigs/create_engines-server.toml` no longer applies: ship the `-v2-` file. The
  presets still copy over.
- Changed behaviour, breathing: an engine's intake and exhaust openings now have diminishing returns (`1 - e^(-2.3 r)` for `r`
  openings per head), and an opening that touches another counts half. One opening per head breathes at about 90 percent (was
  100), two at about 99, and one opening for two heads at about 67 (was 100). Every engine with fewer than about two openings
  per head makes a little less power, mostly at the top of the rev range (a six-cylinder sweep: peak torque 6.1, 7.6 and 7.9
  with the openings at 0.2, 0.5 and 1.0). The constant is a first choice.
- Changed behaviour, thin air: above y 128 an engine's intake charge falls with height, by a factor of e every 1200 blocks, to a
  floor of 0.2 (about 0.85 at the build limit), so a naturally aspirated engine loses power as it climbs and boost offsets it.
  It uses the block's height, in any world. Engines at or below y 128 are exactly as they were. `compat.altitudeThinning` turns it
  off. The numbers are a first choice (one measurement: a four-cylinder crude engine lost 43 percent of its peak torque at a density
  of 0.7).
- Changed, casing dye: the dye of a clad cylinder, crankshaft, crank throw, rotor housing, camshaft, cam lobe, manifold, cased
  shaft and cased cogwheel (all four casing styles) is no longer a block state but is kept in the part's block entity. A pipe has
  17 times fewer block states, which is what costs memory. A world saved in 1.x keeps its dye: it is read out of the saved chunk
  when the chunk loads. A part inside a saved moving contraption, and parts in saved structure or schematic files, lose their
  tint (the part itself works as before). The manifolds gained a block entity that holds only the dye. Plain Engine Casing
  blocks keep the dye as a state, as before.
- The platform, engine types: a mod can register an `EngineSourceProvider` with a `TorqueSource` and put its own kind of engine on a
  crankshaft, with a scanner watch on its blocks, goggle lines, and a rebuild when a watched block changes. The rotary chamber now
  runs on the same seam, with no change in how it behaves. A source can be attached to an intake or exhaust network (`Breather`) so
  turbochargers, superchargers and intercoolers act on it. A block a provider claims within reach of the crankshaft is recorded in
  blueprints, and the server accepts it in a pasted blueprint while the provider is installed (never another mod's block
  otherwise). `AbstractTorqueSource` is a base class that answers everything but the work a source does.
- The platform, frozen API: `com.createengines.api` is version 1.0.0 under its own semantic version (`ApiVersion`, read at run time
  so an addon sees the installed version, `isAtLeast` for later features). A guard test holds every public declaration, and
  another holds the names a KubeJS script uses (the `CreateEnginesEvents` events, the `CreateEngines` global and the fuel and
  `part_stats` keys). Methods that were internals (`reportFailuresTo`, `reportSourceFailure`, `blockChanged`, `sourcesFor`) moved
  from `api.EngineExtensions` to `engine.EngineProviders`; they were for the mod and its tests. The 1.4 preview parts are frozen
  as they were.
- The platform, parts as data: the four precision parts (bore, head, piston, injector) are `part_effects/<part>.json`, each a set
  of optional scales from 0.1 to 10 (`compression_ratio`, `breathing`, `ignition_delay`, `burn_duration`, `friction_static`,
  `friction_viscous`, `inertia`, `injector_flow`). The shipped files hold the old numbers exactly. A pack changes a part by providing
  the same path; a broken file is logged and the part keeps its built-in values. The rotary chamber is retuned through the
  `part_stats` data map with an entry for `create_engines:rotor_housing`; with no entry nothing changes. Cam profiles are not
  data yet, and a pack cannot yet name a third tier from a block of its own.
- Pack makers: `/ce` (game master) with `help`, `config`, `fuels`, `parts` and `api`, and `/ce preset <casual|standard|expert>` (admin)
  to apply a preset and save it. A data-pack fuel with an impossible number is warned about at start-up (the fuel is still used as
  written). New guides in the repository: `PACKS.md`, `CONFIG.md` (every setting with default and range, checked against the source by a test),
  `DATA.md` and `ADDONS.md` (its example addon is built and run with the mod).
- Create Aeronautics: the thin-air rule above is the compat. The GameTests run with Aeronautics 1.3.2 and Sable 2.0.6. By hand (on
  Aeronautics 1.2.1), one precision V-twin on one wheeled contraption ran, drew correctly and survived a save and reload. Other
  engine types, propellers and flying contraptions, assembling with the engine running and multiplayer are not tried, and the
  client audit with Aeronautics loaded is not green yet.
- Pollution of the Realms (optional, mod id `adpother`, tested with 9.1.14.0): with it installed, a running engine puts carbon in
  proportion to the fuel it burns, and sulfur in proportion to how dirty the fuel is, into the air in front of an open, unflooded
  exhaust outlet. Rich-running soot that no Soot Trap caught adds carbon; a Soot Trap takes it off, so a trap visibly cleans the air.
  `compat.pollution` (on or off) and `compat.pollutionRate` (0 to 10) control it. The fuel data map gains an optional `sulfur`
  (missing means the fuel's soot factor, so every 1.x fuel map loads and releases what it would have). Nothing in the engine reads
  any of it. The rates (0.5 carbon and 0.25 sulfur steps per mB) were chosen from one diesel engine at one speed; tell us if it is
  too thick or too thin. Without the mod, none of this loads.
- Fix: an engine coasting under water no longer lets exhaust gas out through a mouth with fluid in front of it (with Pollution of
  the Realms installed).
- Faster: a starter with nothing to do no longer builds a list every tick, engine parts outside the camera's view are not posed, and
  camshaft lobes no longer allocate an array every frame. In a profile of 16 engines on a server, the mod took about 0.6 ms a tick.
- Visual: the precision cylinder head's spark plug, injector and injector-plug models are redrawn (the plug a pixel and a half taller,
  an injector terminal cap) so they no longer z-fight with the cam covers and bolts. Nothing else about the parts changes.
- Under the hood: the NeoForge minimum stays 21.1.219 (the build uses 21.1.228 for Aeronautics 1.3.2 and Sable) and Create stays
  `[6.0.10,6.1)` on build 6.0.10-281. Create 6.0.11 is not recommended, because Create Aeronautics crashes on it. A client-only
  goggle mixin moved to its own mixin config. New tests cover the advancement migration, the config carry-over, the altitude and
  port curves, emissions, the dye migration (a real saved chunk rewritten to the 1.x layout and loaded), the API and script surface,
  the addon example, `/ce`, and a fuzzed blueprint decoder.
- Known limits: JEI has not run in an automated game on this build (it needs NeoForge 21.1.238); a real 1.x world has been opened
  by hand, a real 1.x client config has not.
- New IDs and keys (they join the 2.x promise): the advancement IDs `<track>/<row>` in the tab above, block entity `manifold`, the
  command `/ce`, config keys `compat.altitudeThinning`, `compat.pollution`, `compat.pollutionRate`, `calibration.*`, `limits.*`,
  `volume.*`, `effects.particles`, `effects.density` and `meta.carriedOver` (in both files), the files `create_engines-v2-server.toml`
  and `create_engines-v2-client.toml`, the data folder `part_effects/` with `bore`, `head`, `piston` and `injector`, the
  `part_stats` key `create_engines:rotor_housing`, the optional fuel field `sulfur`, the optional dependency `adpother`, a mixin
  config `create_engines.client.mixins.json`, and API version 1.0.0. Saved key: `Paint` in the block entity of each part that holds a dye.

## Changelog for 1.4.0

The Drawing Office: blueprints for engines, and tools for pack makers. IDs and config keys already in 1.3 are unchanged.
Two things change how something that already exists behaves, each named below: an oil sump no longer turns plant oil
into used oil, and a refining column keeps and reports its catalyst better. Everything else is new.
- Blueprint (item `blueprint`, paper and blue dye): use it on any block of a built engine to record the whole layout and
  every dial (throw and lobe angles, fuel metering, idle and wide throttle, MAF and governor targets, turbo and
  supercharger limits, the transmission gear, the sump's oil exchange and signal tank) as a pasteable string, `CEBP2:...`.
  The Maker's Plate figures and the engine's name ride along. The shafts, cogwheels, chain drives and gearboxes that turn each
  camshaft or gear one crank line to another are recorded with the engine; a line reachable only through a belt is left out
  and counted as skipped. Fluids, fuel and oil are not recorded.
  Use a recorded blueprint in the air to read it as a drafting sheet (the plan from above, the parts counted, the figures),
  sneak and use to record over it.
- Apply: use a recorded blueprint on an engine built the same way to set every dial that differs, checked on the server
  first. A copy built turned a quarter, half or three quarters of a turn is recognised as the same build. If the build
  differs it changes nothing and names the first five differences.
- Build from it: hold a Bank Builder with a recorded blueprint in the other hand and use it on a face. The whole site is
  checked first (loaded, free, protection and every part in your inventory) and nothing is placed unless all of it passes;
  creative players pay nothing. The engine is built turned by the way you face (north is as recorded), and an outline
  preview shows it before anything is placed, green where it is free and red where something is in the way. A turn that would
  make a crank run the other way is built mirrored across the crank, which is the same engine. With an Empty Schematic in the other hand, using the blueprint in the air writes a Create
  schematic of the engine's blocks (dials are not in a schematic; apply the blueprint after).
- Share: a blank blueprint used in the air opens a box to paste a string into; the server decodes it, checks every rule
  (this mod's blocks only, every state and dial real and in range, at most 4096 parts, 30,000 characters) and refuses bad
  ones with a reason. Sneak and use a recorded one in the air copies its string. The string carries a format version, so a
  later release reads this one.
- Framed Blueprint (block `framed_blueprint`, iron nuggets round paper): hang a recorded blueprint on any wall, floor or
  ceiling; it draws the plan and the plate figures on the paper. Frames that touch edge to edge and make a full
  rectangle, up to 8 by 8, draw one enlarged picture across the whole sheet (the top-left frame's blueprint, or the first one
  reading left to right and down); a ragged group or a gap leaves every frame drawing alone. Empty hand reads it, sneak with
  an empty hand takes it back, and breaking it drops the frame and the blueprint.
- Ponder page for the blueprint (recording, reading, applying, building, sharing and the frame).
- For pack makers: three config presets in the jar (`config_presets/casual.toml`, `standard.toml`, `expert.toml`; tables to
  copy over `[balance]` and `[features]`, `standard` equal to the shipped defaults); a **preview** part stats data map
  (`data_maps/block/part_stats.json`, keyed by `create_engines:cylinder` or `create_engines:precision_cylinder`, scales from 0.1
  to 10 on a cylinder's compression, flows, fuel per shot, ignition delay, burn time, friction, inertia, displacement and injector
  flow; with no entry nothing changes); and a **preview** `api` package (`EngineApi.viewAt`, a read-only `EngineView`, and the
  events `EngineStateChangeEvent` and `EngineMilestoneEvent` on `NeoForge.EVENT_BUS`; a listener that throws is logged and never
  stops an engine). Preview parts may still change before 2.0.
- Script and computer integrations (both read-only, both optional, neither mod is needed): KubeJS server scripts get
  `CreateEnginesEvents.stateChanged` (`level`, `pos`, `from`, `to`, `started`, `stalled`) and `CreateEnginesEvents.milestone`
  (`level`, `pos`, `milestone`), and a `CreateEngines.engineAt(level, pos)` global that returns the read-only engine view.
  CC: Tweaked computers get a `create_engines_engine` peripheral on a crankshaft, cylinder, cylinder head or rotor housing
  that answers for the whole engine (`getEngine`, `getStatus`, `getRpm`, `getAvailableTorque`, `getLoadTorque`, `getCylinders`,
  `getFiring`, `getTemperature`, `getOilCondition`, `getBoost`, `getHealth`, `getCause`; nil when the block is not part of a
  built engine). Tested against KubeJS 2101.7.2 and CC: Tweaked 1.120.2 (a test listener and a test peripheral call, not a
  player's script or program); see `docs/INTEGRATIONS.md`. Preview, like the `api` package under them.
- Fix: pushrods, rockers, valves, pistons, connecting rods, the plugs and injectors on cylinder heads and rotor housings,
  the oil sump and the variable turbocharger could show a purple and black patch (or stray colours from a neighbouring texture),
  depending on what else was loaded. Parts drawn partly outside their block had texture coordinates running off the edge of
  their texture; they now stay inside it. Nothing else about them changes.
- Fix: intake manifolds and pipes now turn and mirror with a structure, a schematic or a turned blueprint build; before, their
  connection arms stayed pointing the old way when the block was rotated. Placed pipes in existing worlds are not touched.
- JEI and EMI show every fuel (energy, how it lights, cold start, boost tolerance) and every installable part with the
  blocks it goes into. EMI ran in testing; JEI has not run in a game (it needs a newer NeoForge than this build).
- Refining column: putting catalyst in a Reboiler by hand now plays a sound and shows what it holds (or says the slot is full
  or holds a different catalyst); a catalyst that is not the route's shows "wrong catalyst" in the goggles instead of "no
  catalyst"; and when a column grows so that its controller moves to another corner, the catalyst in the old controller
  is passed to the new one (or dropped) instead of being left in a block nothing reads. New saved key `WrongCatalyst` on the
  column port (missing reads as false). An empty hand on a Reboiler now takes the catalyst back out (up to a stack), and
  a hopper or funnel can now pull catalyst out of it; before, the slot only took catalyst in and never gave it back.
- Changed behaviour: an Oil Sump's oil exchange only wears engine oil into used oil. Plant oil and the other poor lubricants
  burn off instead, so they can no longer be turned into recyclable used oil, and they do not need room in the used tank.
  Engine oil exchanges exactly as before.
- Memory: every state of one of this mod's multipart blocks (the pipes above all) now shares one baked model, about 108 MB less
  settled heap in our audit (1269 to 1161 MB), checked by comparing every quad a sample of states draws before and after. Most of
  the 440 MB that casing styles cost on pipes in 1.3 remains: it is the block states themselves (a pipe has one for every
  connection shape, casing, style and dye), and cutting them needs a state property removed, which waits for 2.0.
- Known limit: a Transmission shifting to Neutral while a fast source (about 200 RPM forward) drives its output side, or
  while the engine is slowing with a 64 RPM source on the output, is not supported: Create can break the shaft beside the
  transmission before the shift completes. Slow sources (16 RPM either way), a 200 RPM reverse source, a stalled engine and
  two controllers are tested and work.
- Under the hood: the new `engine.blueprint` package is plain Java with unit tests, including a fuzzed decoder and frozen
  strings for each format version; the audits cover the drafting sheet and import screen; the world corpus gains the
  engines 1.3.0 saved and the ones 1.4 saves.
- New IDs and keys (they join the 1.x promise): item `blueprint`, block `framed_blueprint` and its block entity, data component
  `blueprint`, recipes `blueprint` and `framed_blueprint`, loot table for `framed_blueprint`, data map `create_engines:part_stats`
  (preview), the CC peripheral type `create_engines_engine` and its method names (preview), the KubeJS event group
  `CreateEnginesEvents` with `stateChanged` and `milestone`, and the global `CreateEngines` (preview), the client-to-server packet `create_engines:import_blueprint`, the string format `CEBP1:` and `CEBP2:` and every
  dial name above, the files `config_presets/*.toml`, a Ponder page `blueprint`, block state property `facing` on the frame.
  Saved keys: `Plan` on the framed blueprint; `WrongCatalyst` on the column port. Config keys: none added.

## Changelog for 1.3.0

The Refinery: a column that turns feed into fuel more efficiently than a basin. IDs and config keys are unchanged, and
diesel and gasoline from a basin make exactly what they did. Three things change how something that already exists
behaves, and each is named below: basin ethanol gets weaker, ethanol on a boosted spark engine pre-ignites later (more
power at the top of the curve), and two engines on one kinetic network no longer hold each other turning.
- Refining column: a square of Reboilers (1 to 3 wide) on a heat source, layers of Column Sections, and a layer of
  Condensers on top. Pipe feed into any Reboiler and take product from any Condenser. The heat under the base
  picks the route, and a wider or taller column runs faster. Plant oil to diesel and diesel to gasoline use about a
  tenth less feed than a basin and a quarter of the catalyst (coal, blaze powder); ethanol from sugar mash takes
  Nether Wart. A funnel or hopper can feed catalyst. Sight windows glow while it works and goggles name what is
  stopping it.
- Routes are a data recipe type, `create_engines:refining`, so packs can add their own.
- JEI and EMI show every refining route as a page (tested running with EMI; the JEI page is not yet tested running): the feed (each fluid of a tag in turn), the catalyst and how much of
  it is used, the product, the heat the base needs, the speed and the feed-to-product ratio, with the Reboiler, Column
  Section and Condenser as the blocks that run it. Neither is required; with neither installed nothing changes.
- Leaning columns: a layer may sit one block to the side of the one below. Sections join like Create chutes, with no
  joint block.
- The sight windows glow the colour of the fluid in the column: the lower layers show the feed, the upper layers the
  product. A comparator on a Reboiler reads the feed level and on a Condenser the product level.
- Flare Stack: burns on a working Condenser, with a soft whoosh now and then.
- Soot Trap: catches the soot a rich engine would send out of the exhaust (50 mB of unburned fuel is one Soot, up to 64).
  Empty it by hand, hopper or funnel. Soot grinds to black dye, and also makes gray dye, gunpowder (heated basin mixing, with sugar) and coal (basin compacting over a blaze burner), and burns as a weak furnace fuel. A caught lump makes a small clink.
- Jerry Can: four buckets of one fluid in an item, for fuel that has to go where there is no pipe. Its bar takes the
  colour of the fluid, and sneak-use on a block pours one bucket onto the ground as a source block.
- Basin ethanol is weaker: 35 mB per 250 (heated) and 70 mB per 250 (superheated), and biodiesel asks for 17 mB of
  ethanol instead of 25, so Glycerin and what is made from it cost what they did. A column is the better way to
  make ethanol.
- Goggles on a Create Basin say when a column would make more of a fuel from what it holds, and fuel buckets say
  when a column makes them.
- Ponder pages for the refining column, leaning columns, the Soot Trap, the Jerry Can and the Maker's Plate (which had none).
- Plant oil, and any fluid in the new `create_engines:poor_lubricants` tag, now lubricates an engine at 0.6 of engine
  oil's strength. Engine oil is unchanged.
- Fix: an engine split by an unloaded chunk (crankshaft chunk loaded, far half not) used to have its crank speed zeroed
  every tick and came back stalled for good. It now keeps its speed and carries on when the chunk loads again, as an
  engine unloaded whole already did.
- Fix: an engine could lose its whole exhaust note, and the bangs that give way to it, when one cylinder's firing angle
  rounded up to the full 720 degree cycle on its way to the client (any angle from 719.95 up). 1.2.1 made the note
  refuse such an angle by going silent; the angles are now wrapped into the cycle and kept in firing order before they
  are sent and when they are read.
- Sound: the cylinder firing sound is redesigned (a sharp crack per firing that fades as firings merge into a tone, with
  engine size and sharpness, load and boost, shaping it), and the engine clatter sample is regenerated. The note is now
  locked to the cam's place in the 720 degree cycle (it used to keep its own clock, a tenth of a second behind), so its
  firings land with the exhaust flames and puffs instead of drifting against them; an engine whose cam is not geared 1:2
  to its crank, and rotary engines, have no such place and are unchanged. Machine and
  starter loops wait out the sound engine's settle delay, fade out beyond 48 blocks, ignore bad pitch and volume values,
  and drop finished instances instead of keeping them until you leave the world.
- Advancements: each one earned plays a short synthesised sting. Eighteen minor ones (such as Bottom End, Bore Ambition, Turn the Key and Oil Pan) show a toast without a line in chat; every other shown one still announces. Ten that were hidden before are now shown (Smoke Signals, Fouled Up, Gas in the Oil, Overworked, Under the Hood, Psssht, Gear Up, Always Fresh, Cool Running, Block Heater). Six keep their IDs and criteria but sit under a new parent in the tab (Let It Shift, Gearhead, Full Driveline and Reverse Engineering under Gear Up, Tucked In under Under the Hood, Gas in the Oil under Smoke Signals). Chief Engineer now also hands over an engraved Maker's Plate named Chief Engineer, from the new loot table `create_engines:advancement/chief_engineer`.
- Ethanol under boost: ethanol now starts to light early under boost at its own temperature (5.0), where it used to
  use its autoignition point (3.4), which compression and pilot ignition still use. Only boosted spark engines burning
  ethanol are affected, and only at the top of the speed range: the last few of the sixteen measured speeds gain
  torque (a one-cylinder crude build goes from 0.26 to 0.49 at its top speed, a six-cylinder one from 1.21 to 2.08). Gasoline,
  diesel, biodiesel and every other fuel and speed are exactly as they were. A fuel can set its own point with the new
  optional `preignition_temperature` field in the fuel data map; leaving it out means its autoignition point, as before.
- Fix: two engines on one kinetic network no longer keep each other turning with no fuel. An engine holds its crank to
  the network's speed only when something other than an engine (a motor, a wheel, a hand crank) is driving it. An engine
  split by a missing chunk also stops its starter cranking while it is split.
- Fix: a network of coupled engines could go on turning for good with nothing driving it. When two crank lines are
  geared into a ring (shafts or chain drives at both ends), Create can leave every block following its neighbour with no
  generator left in the ring: the engines read stalled while the whole network kept turning, and a saved world brought it
  back after a restart. Every second, a crank or alternator now looks at its network; if it is turning and no generator in
  it publishes a speed, on two looks in a row, the whole network is brought to rest. It does nothing if any generator is
  running, if a source that is not a Create generator is present, or if a neighbouring chunk of any block is not loaded.
  A crank that is not its engine's primary also stops publishing a speed and stress capacity, so a stale value saved on a
  coupled line cannot keep driving the network.
- Fix: the two dials on an Oil Sump (oil exchange and comparator signal) shared one save key and overwrote each other, so
  the screen could show the other dial's value. Each has its own key now; a sump saved before this still loads, and both
  dials read the old shared key when they have none of their own.
- Casing styles on shafts and cogwheels: Riveted, Louvred and Window Engine Casing can now clad a Create shaft, cogwheel
  and large cogwheel, nine new blocks (`engine_<style>_cased_shaft`, `_cased_cogwheel` and `_cased_large_cogwheel`),
  and connect to their own style.
- Sound: the cooling tick of a stopped hot engine is higher and brighter (its three samples retuned from about 1.8 to 2.6 kHz up to 2.5 to 3.6 kHz, and played at 1.0 to 1.5 pitch instead of 0.75 to 1.35). A new pre-ignition knock sound set replaces the metal tick on a rattling exhaust. The exhaust backfire sound (`exhaust_pop`, three samples) is rebuilt from layered noise: a hard-driven burst weighted in the low mids, a few close reflections and a crackle of tiny pops, with no fixed tones, so it no longer sounds synthetic (sound files only; the event and when it plays are unchanged). Engine notes and machine
  loops share a budget so a flapping engine cannot start and drop voices every few ticks: at most 16 machine loops at
  once, a rest of two seconds after a voice ends, and at most four starts a second. The quiet loops (clatter, squeal,
  intake roar) start at a level of 0.05 instead of 0.02.
- Rendering: the gas drawn inside each cylinder is cut with fewer sides and rings from 16 blocks away, and is not drawn
  beyond 40 blocks, where it is a smudge in a bore. Stopped engines no longer re-pose and re-upload their shafts, crank
  throws, cam lobes, valves, pistons and rods every frame, and a cylinder without pushrods stops posing four hidden
  parts every frame. Heat shimmer, rock dust, intake draught and exhaust effects are not built when no player is within
  40 blocks (48 for exhaust).
- Goggles: several lines are shorter (power, torsional flex, muffled, roughness, cold, cool, warm, cooling down,
  coasting, overloaded). They say the same things.
- Saves: a damaged engine save with infinity or NaN in its saved motion is read as zero, instead of an engine that never
  stalls or settles. The Soot item model is now a flat item sprite.
- Under the hood: `EngineSimulation` and `CylinderModel` are split into smaller parts with no change in behaviour, and
  the layout-derived muffling and soot-trap runs are cached. New tests cover compatibility with every earlier release's
  saved engines, exact simulation values, fuzzed network and save data, and engines run with Sodium and without Flywheel.
- New IDs and keys (they join the 1.x promise): blocks `reboiler`, `column_section`, `condenser`, `flare_stack`, `soot_trap` and the nine `engine_<riveted|louvred|window>_cased_<shaft|cogwheel|large_cogwheel>`; items `jerry_can` and `soot`; block entities `column_port` and `soot_trap`; sound events `advancement_sting`, `flare_whoosh`, `preignition_knock` and `soot_catch`; recipe type `create_engines:refining` with the routes `diesel`, `gasoline`, `ethanol` and `ethanol_superheated`; data component `can_fluid`; fluid tag `create_engines:poor_lubricants`; loot table `create_engines:advancement/chief_engineer`; recipes `reboiler`, `column_section`, `condenser`, `flare_stack`, `soot_trap`, `jerry_can`, `black_dye_from_soot`, `gray_dye_from_soot`, `gunpowder_from_soot` and `coal_from_soot`; block state properties `casing_style` (plain, riveted, louvred, on every clad part), and `active`, `tint`, `lean_up` and `lean_down` on the column section and `lit` on the flare stack; fuel data-map field `preignition_temperature`; a mixin config `create_engines.mixins.json` (the Basin goggle line, which fails soft if Create changes). Saved keys: `Width`, `Height`, `Fault`, `BaseX/Y/Z`, `BlockedX/Y/Z`, `Lean`, `Heat`, `Limit`, `Rate`, `Catalyst` and `Size` on the column port; `Stored` and `Gathered` on the soot trap; `OilExchange` and `SumpSignal` on the oil sump (an older sump save still loads through the old shared key). Config keys: none added.

## Changelog for 1.2.1

Stability fix for 1.2.0. No rating, recipe, ID, config key or simulation number changes.
- Fixed the frame rate collapsing near running engines and Minecraft's sound engine dying until the game was restarted:
  the synthesized engine note allocated native memory ten times a second per engine and could run the JVM out of it.
  It now reuses one buffer.
- A bad number from an engine can no longer silence its note for good, and a failing synthesizer gives silence
  instead of an error on the sound thread.
- Leaving a world and joining another no longer leaves stale engine voices that block new engines from sounding, and
  no longer keeps the old world's machines in memory.

## Changelog for 1.2.0

Dress Parts: two builds no longer have to look alike. Everything here is cosmetic: no rating, recipe of an existing
item, ID, config key or simulation number changes, and worlds from 1.1.x load as they are.
- Casing plates: Riveted Engine Casing (a rivet in each corner) and Louvred Engine Casing (slatted like a vent) join
  the plain one. Each connects to its own kind. Riveted and louvred are shapeless recipes: casing plus an iron nugget,
  or casing plus iron bars.
- Window Engine Casing: a glazed plate. Used on a crankshaft, crank throw, camshaft or cam lobe it clads the part and
  you still see it turning through the glass. Shapeless: a casing plus a glass pane.
- Dyed casing: use any dye on any casing block, or on casing clad onto a part (cylinders, cranks, cams, manifolds,
  cased shafts and cogwheels) to tint it in Create's sixteen colours. A bucket of water washes it back to bare steel.
  A dye is used up outside creative mode; a broken dyed block drops plain casing.
- Rain Cap: a hinged cap that goes on the mouth of an exhaust outlet. It kicks open with each exhaust pulse and
  drops shut when the engine stops. It never seals the mouth: the exhaust leaves from under it. Has a Ponder page.
- Maker's Plate: a brass plate screwed to the side of an engine. Name it on an anvil before placing it and the name is
  stamped on, with the placer as the builder. Use it with an empty hand to stamp the engine's peak torque and peak
  power, each with its speed, taken from the same finished curve the Dynamometer shows. Goggles read the plate.

## Changelog for 1.1.0

Light and Noise: a running engine is now worth standing next to, and the things that go wrong now show it. Nothing here
changes a rating or adds a failure; the simulation's numbers do not move. Worlds from 1.0.x load as they are.
- Pushrods: a cam beside the block, or in the valley of a V, shows a pushrod from each lobe up to a brass rocker on the
  head, rising and falling with the valve.
- Engines rock: pistons and rods slap about in their bores by the roughness rating, with a pinch of dust shaken off.
  A flywheel calms both. The block itself does not shake.
- Intake roar: a gulping sound at the open intake mouth that swells with throttle and boost. One voice per engine,
  silent at rest, untouched by the Muffler.
- Smoke rings: a slow single puffs rings from its stack at idle.
- Louder consequences: a rich engine at full load holds a steady stack flame; a fouled plug puffs black smoke on each
  miss; a too-lean charge spits flame back through the intake; a dry engine smokes from the crankcase; a blocked
  exhaust chuffs back out of the intake; gasoline lighting early under boost rattles and flashes orange; a flooded
  engine gulps, steams and clunks; an overloaded engine gives one last belch as it stalls.
- Advancement declutter: the tab shows 54 advancements instead of 110. The rest keep their IDs and criteria, so
  nothing that refers to them breaks, but no longer appear.

## Changelog for 1.0.1

Casing that looks like casing, a few engine-behaviour fixes and livelier fire and air. No IDs, config keys or
advancements change, and worlds from 1.0.0 load as they are.
- Engine Casing now uses connected textures: a run of casing, and everything clad in it, reads as one riveted plate.
- Casing clads more. Besides crankshafts, throws, camshafts and cam lobes, it now covers cylinders, plain intake and
  exhaust manifolds, and Create's own shafts, cogwheels and large cogwheels (use it on them as you would Create's
  casings; they drop the shaft or cogwheel when broken). Throttles and sensors stay uncovered.
- Cam lobes are drawn as separate intake, exhaust and injector lobes, each turning to its own valve timing, so you can
  watch a valve open as its lobe comes round.
- Wrenching a casing off: sneak with a wrench to remove the casing and keep the part inside, instead of breaking it.
- The turbo wastegate chuffs when it swings open to hold the boost limit.
- Fixed: a stopped engine kept firing its effects on the client, and a crank zeroed by a rotation conflict now asks to
  turn again instead of staying still.
- Fixed: the slowest dyno point set the roughness loss peak, so a smooth even-firing engine could read about 18% rough at
  speed. The rating itself is unchanged, but smooth engines now read as smooth.
- Effects: fire in rotor housings and air drawn into intakes are drawn with new glowing particles, and intakes pull in a
  curling vortex of pale mist.
- Docs: the Modrinth summary and description are now pushed from this file on a release tag.

## Changelog for 1.0.0

The first stable release. Nothing in the engine's behaviour changes from 0.4.0; what changes is the promise.
- Stable from here through 1.x: block, item and fluid IDs, config keys and ranges, advancement IDs and the fuel data-map
  format. A release that changes how an existing engine behaves will say so here.
- The Transmission is back. It was removed in 0.4.0, and a player asked for it: it is compact, and its Auto mode shifts
  for you, which a Rotation Speed Controller does not. It is the same block with the same ID, recipe and advancements
  (Gear Up, Reverse Engineering, Gearhead, Let It Shift), and Full Driveline asks for every gear again. One placed before
  0.4.0 and lost on load does not come back by itself; place a new one.
- The Rev Limiter and Lambda Sensor existed only in development builds before 0.1.0. A world from one of those builds
  loses them on load too.
- Docs: the README lists every server config key group, including `alternatorEnergy`.

## Changelog for 0.4.0

Create's Rotation Speed Controller holds a shaft at a set speed and trades speed for torque at any ratio, and it keeps
what an engine drives steady however the engine's own speed moves. The Transmission did the same job worse, so it is
gone.
- Removed: the Transmission and its recipe, Ponder page and advancements (Gear Up, Reverse Engineering, Gearhead and Let
  It Shift). Any in an existing world disappear when it loads. Put a Rotation Speed Controller after the crankshaft
  instead. The Governor stays: it holds the engine's own speed, which a Rotation Speed Controller downstream does not.
- Full Driveline no longer asks for every gear: it wants a charged battery, a flywheel that smooths the engine and a
  governor under redstone.
- New: an exhaust heat exchanger under a fluid tank heats a boiler like a blaze burner (kindled is one level, seething
  two), so exhaust can run Create steam engines. Advancement: Combined Cycle.
- New: Display Link sources for engines (Engine Status, Speed, Load, Temperature, Oil Condition) on any crankshaft
  block, and Oil Sump Levels on a sump. A sump also gives a comparator signal for the tank its new dial names, so a pump
  or alarm can watch the oil. Advancements: Instrument Panel, Dipstick.
- New: the Muffler quiets an engine's exhaust note, bangs and pops and costs it no power. Engine Casing is plain cladding
  for hiding the sprawl. Advancements: Hush Now, Under the Hood.
- New: fuel buckets show their energy, how they ignite and how they take a cold start when hovered.
- The Bank Builder fits spark plugs and lays a cam line beside the bank (in the valley of a vee) when you carry them.
  Advancement: Cam Included. A cogwheel placed or broken between cranks now couples or uncouples them at once.
- Fixed: a schematic cannon built a finished cylinder, head or rotor housing with its pistons, rods, injectors, plugs
  and rotor for free. It now asks for them.
- A burning engine glows faintly: heads and rotor housings give off a little light while the charge fires.
- Engines at the edge of loaded chunks no longer pull in chunks nobody is near, and an engine whose chunk has unloaded no longer pulls it back in.
- Raise Create's maxRotationSpeed to 512 (higher works too) to let engines run past 256 RPM.
- Fuel recipes: plant oil takes six seeds or two saplings (100 mB and 50 mB), ethanol ferments from an apple or any
  mushroom and makes 50 mB a batch, biodiesel needs 25 mB of ethanol, and gasoline needs only a Blaze Burner's heat.

## Changelog for 0.3.0

Livelier engines. Exhaust tells the truth: a thin haze when the mixture is right, black when rich, white vapour until the
block warms, a blue cast when fuel has thinned the oil, and a crack of flame when unburned fuel meets a hot pipe. Engines
cough as they catch, knock as they die, tick as they cool, squeal and spark when they run dry, and draw air in at their
intakes. New recordings and white puff and spark particles back all of it.
- Warm-up and cooling: a cold block burns fuel a little worse and warms quickest at idle. A Blaze Burner against the heat
  exchanger preheats it. A stopped engine ticks and shimmers, and its turbine wheel glows red as it cools.
- Plain-language diagnosis: goggles name an engine Healthy, Running rough or Failing with the one cause to fix first, and
  say why a cylinder misfired. The Dynamometer shows how a new run compares with the last. Two new Ponder pages cover why
  an engine will not start and why it runs badly.
- Nothing burns underwater. Water against a cylinder, head or rotor housing stops the engine wherever its pipes lead, and a
  flooded intake or exhaust shuts as well. Engine parts are no longer washed away by flowing water.
- New advancements: Hard Start, Preheated and Flooded.
- New client settings: `engineNoteVolume` and `mechanicalVolume`.

## Changelog for 0.2.0

New art. The mod's metals are repainted to match Create's, with the same rims and strokes in Create's own colours. Vanilla's
copper, magma, lava, smooth stone and trapdoor are gone from the models, the fluids have their own thin and thick textures in
place of tinted water, and hot metal on a heat exchanger gives its own light. The advancement tab has a tread plate background.

## Changelog for 0.1.0

First public release. Everything in the description above.

## Publishing

Uploads are automatic, from `.github/workflows/publish.yml`. It builds with the tests, then uploads the jar to
Modrinth and CurseForge. The changelog is the `## Changelog for X.Y.Z` section of this file when one matches the tag,
otherwise the commit subjects since the last tag.

| Do this | You get |
|---|---|
| `git tag v0.2.0 && git push origin v0.2.0` | a release |
| `git tag v0.2.0-beta.1 && git push origin v0.2.0-beta.1` | a beta (`-alpha` makes an alpha, `-rc` a beta) |
| push to the `prototype` branch, or run the workflow by hand | an alpha named `<mod_version>-prototype.<run number>` |

On a release tag, `.github/workflows/gallery.yml` also pushes the summary and the description above to the Modrinth page
(`scripts/sync-modrinth-description.sh`) and replaces the gallery. This needs the token's "Write projects" scope. CurseForge
has no API for either, so its description and gallery are still pasted in by hand.

The version in the jar comes from the tag, so `mod_version` in `gradle.properties` only needs bumping for the
prototype names.

One-time setup in the GitHub repository (Settings, Secrets and variables, Actions):

- Secret `MODRINTH_TOKEN`: a Modrinth personal access token with the "Create versions" scope
  (modrinth.com/settings/pats).
- Secret `CURSEFORGE_TOKEN`: a CurseForge API token (authors.curseforge.com/account/api-tokens).
- Variable `CURSEFORGE_ID`: the numeric project ID, on the CurseForge project page under About Project.
- Variable `MODRINTH_ID` is optional; it defaults to the slug `create-engine-engineer`.

Push the repository first so the description's image links resolve. Both sites review a project before it goes
public, and Modrinth may not accept uploads until its first review passes. While a Modrinth project is unapproved, the slug
cannot be looked up without the token's "Read projects" scope: add that scope, or set `MODRINTH_ID` to the project's ID.

A failed upload does not stop the other site, but the run still fails. Rerunning it uploads again to any site that
succeeded, so when one site worked, upload to the other by hand with the jar from the run's artifacts.
