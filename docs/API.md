# Public API (frozen at 2.0, API version 1.0.0)

The API has its own version, `com.createengines.api.ApiVersion`, which follows semantic versioning and is not the mod's number.

- **Major:** something an addon could call or implement is removed or changed. Only with a new major version of the mod.
- **Minor:** something is added. Code written for an older minor still compiles and runs.
- **Patch:** no change to the surface.

## What is in it

- `com.createengines.api`: `EngineApi` (read an engine), `EngineView`, `EngineStatus`, the events (`EngineStateChangeEvent`,
  `EngineMilestoneEvent`, posted on `NeoForge.EVENT_BUS`), and the extension point (`EngineExtensions`, `EngineSourceProvider`,
  `EngineBuild`).
- Two engine types a provider returns: `com.createengines.engine.sim.TorqueSource` and `StatusLine`.
- Data a pack can supply, with their formats frozen the same way: `data_maps/fluid/fuel.json` (fuels),
  `data_maps/block/part_stats.json` (scales over a built-in cylinder, still read) and `part_effects/<part>.json` (below).

Everything else (`engine.*`, `content.*`, the registries, the config classes) is internal and may change in any release.
Internals that used to sit on public classes were moved out in 2.0 (`engine.EngineProviders`).

## How it is kept frozen

`src/test/resources/compat/api-surface.txt` lists every public declaration of those types, read from the source by
`ApiSurfaceTest`. A line that disappears or changes fails the build, which is a major change. A new declaration also fails until it
is recorded with `API_REGEN=1 ./gradlew test --tests "*ApiSurfaceTest"` and `ApiVersion.MINOR` is raised; the header line of the
file records the version it was taken at. Behaviour is not covered by this file: the GameTests in `PackToolsGameTests` are.

## Parts as data

A cylinder is the crude one with the effects of the parts it was built with multiplied in. `data/create_engines/part_effects/`
holds one file per part (`bore`, `head`, `piston`, `injector`), each a JSON object of optional scales (1 changes nothing, range
0.1 to 10): `compression_ratio`, `breathing` (intake and exhaust valve flow), `ignition_delay`, `burn_duration`,
`friction_static`, `friction_viscous`, `inertia`, `injector_flow`. The mod ships the built-in values as these files
(`PartEffectsTest` pins them to the constants), so a pack changes a part by providing the same path. The server's precision bonus
still blends every effect. A broken file is logged and that part keeps its built-in values; an unknown key is refused so a typo
is found. Reloaded with the datapacks; engines already built keep what they were built with until they next rebuild. Tooltips,
Ponder pages and the JEI and EMI pages still show the built-in figures.

Not yet data: the rotary chamber's own stats, the cam profiles, and a way to name a third tier from a block of a pack's own.
