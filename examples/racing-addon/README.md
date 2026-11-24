# Racing addon: an example addon for Create: Engine Engineer

A complete, separate mod that adds a **Racing Cylinder** and a **Racing Flywheel** to the engines of Create: Engine Engineer. It is
here to show an addon author how it is done and to prove that it works from outside the mod: it is built against the finished jar,
not the source.

What it contains:

| File | What it is |
| --- | --- |
| `src/main/java/example/racing/RacingAddon.java` | registers the block and tells the mod's cylinder block entity type to accept it |
| `src/main/resources/data/create_engines/data_maps/block/part_effect.json` | names the block's part effect |
| `src/main/resources/data/racing_addon/part_effects/racing_bore.json` | the effect itself: higher compression, quicker burn, less static friction, a heavier piston |
| `src/main/resources/data/create_engines/data_maps/block/flywheel.json` | gives the Racing Flywheel its masses (API 1.2) |
| `src/main/resources/assets/racing_addon/blockstates/racing_flywheel.json` | the flywheel's model, modelled along Y and turned onto each axis by its block state |
| `src/main/java/example/racing/RacingGameTests.java` | runs an engine with the Racing Cylinder beside one with the plain cylinder, and checks the racing one can deliver more; runs an engine with the Racing Flywheel beside a plain one, and checks the flywheel's mass slows the revs |

The guides are [docs/ADDONS.md](../../docs/ADDONS.md), "A cylinder of your own" and "A flywheel of your own".

## Running its GameTests

Build the mod's jar once, then run the addon's tests. From the repository root:

```bash
./gradlew jar
./gradlew -p examples/racing-addon runGameTestServer
```

Look for `All 3 required tests passed` in the output. The game does not fail the build when a test fails, so search the log for it
every time. To test against a different jar, pass `-Pce_jar=<path to the create_engines jar>`.

The build is not part of the mod's own build (it is not in the root `settings.gradle`), so it never ships and the mod's tests do not
run it.

## What it uses of the mod

Only `com.createengines.api` (`PartBlocks` to make and register the block, `EngineBuilding` to time a cam lobe in its test,
`EngineApi` to read the engines), plus the registry ids, the data map and the data files, which are frozen. That is checked by
`ExampleAddonUsesOnlyTheApiTest` in the mod's own tests, so the example keeps working between versions. When this example first
ran against the mod it had to extend two internal classes; those became `PartBlocks` and `EngineBuilding` in API 1.1.
