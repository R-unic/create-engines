# Public API (frozen at 2.0, API version 1.1.0)

The API has its own version, `com.createengines.api.ApiVersion`, which follows semantic versioning and is not the mod's number.

- **Major:** something an addon could call or implement is removed or changed. Only with a new major version of the mod.
- **Minor:** something is added. Code written for an older minor still compiles and runs.
- **Patch:** no change to the surface.

## What is in it

- `com.createengines.api`: `EngineApi` (read an engine), `EngineView`, `EngineStatus`, the events (`EngineStateChangeEvent`,
  `EngineMilestoneEvent`, posted on `NeoForge.EVENT_BUS`), and the extension point (`EngineExtensions`, `EngineSourceProvider`,
  `EngineBuild`).
- The engine types a provider returns: `com.createengines.engine.sim.TorqueSource`, `AbstractTorqueSource` (a base class that
  answers everything but the work a source does), `Breather` (a source that breathes through a port network) and `StatusLine`.
- **Added in 1.1:** `PartBlocks` (make and register a cylinder or head block of your own, and read which part effect a block names) and
  `EngineBuilding` (time a cam lobe from code). They exist so that an addon names nothing of this mod outside `com.createengines.api`:
  anything an addon needs that is not here is a gap in the API to be filled with a new public surface, never an import of an internal class.
- `ApiVersion.MAJOR`, `MINOR`, `PATCH` and `VERSION` are read at run time, not baked into an addon's jar when it is compiled, and
  `ApiVersion.isAtLeast(major, minor)` tells an addon whether a later minor version's feature is there.
- Data a pack can supply, with their formats frozen the same way: `data_maps/fluid/fuel.json` (fuels),
  `data_maps/block/part_stats.json` (scales over a built-in cylinder, still read) and `part_effects/<part>.json` (below).

Guides: [ADDONS.md](ADDONS.md) is how to write an addon (with an example that is built and run with the mod), and
[PACKS.md](PACKS.md), [DATA.md](DATA.md) and [CONFIG.md](CONFIG.md) are for pack makers. `/ce api` in game shows the API version
and every provider registered.

Everything else (`engine.*`, `content.*`, the registries, the config classes) is internal and may change in any release.
Internals that used to sit on public classes were moved out in 2.0 (`engine.EngineProviders`).

## How it is kept frozen

`src/test/resources/compat/api-surface.txt` lists every public declaration of those types, read from the source by
`ApiSurfaceTest`. A line that disappears or changes fails the build, which is a major change. A new declaration also fails until it
is recorded with `API_REGEN=1 ./gradlew test --tests "*ApiSurfaceTest"` and `ApiVersion.MINOR` is raised; the header line of the
file records the version it was taken at. Behaviour is not covered by this file: the GameTests in `PackToolsGameTests` are.

## Script and data names

The names a KubeJS script and a pack's data maps use are guarded the same way by `ScriptAndDataSurfaceTest` against
`src/test/resources/compat/script-surface.txt` (the `CreateEnginesEvents` group and its two events with their fields, the
`CreateEngines` global, and the keys, defaults and ranges of the `fuel` and `part_stats` data maps). Removing or changing a line
is a major change; adding one is a minor change (`SURFACE_REGEN=1`, raise `ApiVersion.MINOR`). The CC: Tweaked peripheral's
method names are not in that file.

## Sources that breathe, and parts that are engine members

A source that implements `Breather` and is attached to a port sits in an intake or exhaust network the way a cylinder does.
`EngineSourceProvider.intakeAt(source)` and `exhaustAt(source)` return a block in or touching one of the engine's intake or exhaust pipes
or openings (null for none). The engine then puts the source in the lists of every turbocharger, supercharger and intercooler on
that network: compressors set its intake pressure and temperature (series stages multiply, parallel ones average, intercoolers cool,
the server's boost strength and thin air apply), turbines on its exhaust network are spun by its exhaust heat and hold its exhaust
up. An attached source is not also given the engine-wide average through `TorqueSource.setIntake`; one with no port still is. It does
not count in the network's opening and head figures, so an addon's mouth neither helps nor hurts a cylinder's breathing.

A block a provider watches (`watches`) within its `reach` of the crankshaft is also a member for blueprints: it is recorded with its
id and block state (no dials), and the server accepts that block id in a pasted blueprint while the provider is registered. Any
other mod's block is still refused. Placing it needs the addon installed, as with any block.

## Parts as data

A cylinder is the crude one with the effects of the parts it was built with multiplied in. `data/create_engines/part_effects/`
holds one file per part (`bore`, `head`, `piston`, `injector`), each a JSON object of optional scales (1 changes nothing, range
0.1 to 10): `compression_ratio`, `breathing` (intake and exhaust valve flow), `ignition_delay`, `burn_duration`,
`friction_static`, `friction_viscous`, `inertia`, `injector_flow`. The mod ships the built-in values as these files
(`PartEffectsTest` pins them to the constants), so a pack changes a part by providing the same path. The server's precision bonus
still blends every effect. A broken file is logged and that part keeps its built-in values; an unknown key is refused so a typo
is found. Reloaded with the datapacks; engines already built keep what they were built with until they next rebuild. Tooltips,
Ponder pages and the JEI and EMI pages still show the built-in figures.

The rotary chamber is retuned through `part_stats` too: give `create_engines:rotor_housing` an entry (the same eleven scales, applied to
the built-in chamber after its injector and the server's rotary power setting).

Not yet data: the cam profiles (their timing is fixed in code), and a way to name a third tier from a block of a pack's own.
