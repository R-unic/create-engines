# Ports and backports

What to expect if you play Create: Engine Engineer on anything other than **Minecraft 1.21.1, NeoForge, Create 6.0.10**.
That build is the main one. It is the only one that is released today, and every promise in the rest of the docs is about it.

This page is written ahead of the first port, so each claim below says whether it is **true now**, **the plan**, or **not
known yet**. A port page on Modrinth or CurseForge links here and says which row of the status table it is.

## Status

| Build | State |
|---|---|
| 1.21.1, NeoForge, Create 6.0.10 | **Released.** The main build |
| 1.20.1, Forge, Create 6.0.8 | **Spike runs (`forge1201/`).** One simulated cylinder turns a shaft on a Create network in a GameTest, and the port reads the main build's own `fuel.json` (parity-tested against the simulation's fuels), the part-effect, cam-profile and part-stats files, and burns diesel from a Create fluid tank; nothing else is ported. First port to try. The shared simulation module is done (below). Create 6.0.8 and Ponder for Forge 1.20.1 are on the Create maven, so the build can resolve them |
| 1.20.1, Fabric, Create Fabric 6.0.8.1 | **Not started.** Decided only after the Forge build runs |
| 1.21.1, Fabric | **Not planned.** No official Create 6 for it |
| Newer Minecraft versions | **Not planned yet** |

A row moves to "Released" only when its record in `docs/releases/` is complete and signed, like any release
(docs/STABILITY.md). A port that is not on that list is a test build and says so on its page.

## What is the same on every build (true now)

The engine simulation (`sim/`, package `com.createengines.engine.sim`) is its own Gradle module of plain Java with no
Minecraft, loader or Create classes in it. Every build is made from the same copy, so:

- The numbers are the same: torque, boost, heat, fuel burn, firing order, thin air and cold. The
  baseline files `compat/behaviour.txt` and `compat/trajectories.txt` are checked against the shared module, and a port
  runs the same checks.
- A fuel, part or config value means the same thing on a port as on the main build.
- The simulation's unit tests need no game. In a first check, 939 of the 941 that cover it ran on a plain Java project
  with no Minecraft on the classpath (the other two read the config source file).

This is checked by `ArchitectureRulesTest`, which fails if anything in `sim/` imports the game. It is the reason a port
is a smaller job than a rewrite.

## What may differ (the plan, not yet checked on a build)

These follow from the Minecraft and loader version, not from the engines. Each one is listed here so a port's page can
say which of them it has:

- **Data maps.** Fuels, part stats and other parts-as-data use NeoForge data maps, which 1.20.1 does not have. A
  port is expected to read the same files through another mechanism, or fall back to the fluid tags only. `/ce fuels`
  will say which.
- **Saved worlds do not move between builds.** A world saved on 1.21.1 cannot be opened on 1.20.1, and the mod makes no
  attempt to convert it. Blueprints shared as text are the one thing meant to cross, and only between builds that say
  so.
- **Items and recipes** use each version's own formats. Recipes, advancements and Ponder pages should match the main
  build, but anything that cannot be done on the older version is dropped, and the port's changelog lists each one.
- **Mod IDs and config files** stay the same, so a pack can use the same `create_engines-v2-*.toml` files.
- **Sounds and models** are the same files, redrawn only where the older renderer needs it.

## Compatibility rows (what a player should expect)

The main build's rows are in [COMPAT.md](COMPAT.md). A port does not have to pass all of them to be released, but it
must say which it passed, which it failed and which it did not run. The rule is the same as for the main build: a row
is green only after the run, and "not run" is never written as "works".

| Row | Expectation on a port |
|---|---|
| Unit tests and the shared simulation | **Must pass.** A port with a red simulation row is not released |
| GameTests of the engines, fuels and recipes | **Must pass** on the port's own build |
| Client audit (models, Ponder, running fleet, leaks) | **Must pass** on the port's own build |
| Create Aeronautics and Sable | **Not known.** Whether a 1.20.1 build of either exists has not been checked. The thin-air rule works without them |
| Pollution of the Realms, Create: Diesel Generators, Create: The Factory Must Grow | **Run if a build for the port's version exists.** Listed as "not run" otherwise |
| JEI, EMI, KubeJS, CC: Tweaked, Jade, Curios | **Run if a build for the port's version exists.** A row that is red is listed with its cause, and the mod's own features keep working without it |
| Sodium, Iris, Sound Physics, AmbientSounds | Same. A row that is red is listed, not hidden |

If a row is red or not run, the port's description says so in plain words near the top, and the record in
`docs/releases/` has the cause. A port that fails a "Must pass" row is a test build, not a release.

## What a port does not promise

- That its release dates follow the main build's. The main build comes first.
- That every feature of the main build is in the port at the same time. A feature that is missing is named in the changelog
  with the reason.
- That a bug seen on the main build is fixed on a port the same day, or the reverse.
- Support for a pack that mixes builds. Use one build per world and server.

## Reporting a problem on a port

Say which build you run (Minecraft version, loader, Create version, the mod's version) and run `/ce dump`, which
writes the mods, API, config, fuels and parts to one file. A port issue is tagged with the build it was seen on.

## How the work is laid out

- `sim/`: the shared simulation. Plain Java 21, no game. The main build and every port depend on it.
- The root project: the NeoForge 1.21.1 mod. It packs `sim/` into its jar.
- A port, when it starts, is another module that depends on `sim/` and holds only what the loader and the Minecraft
  version force to differ. The aim is that the engines, fuels, parts and numbers are never written twice.

Guards that pin behaviour (`compat/behaviour.txt`, `compat/trajectories.txt`, the manifest, the world fixtures) stay
single and shared. A port may add entries for itself and never edits an existing one.

## Forge 1.20.1: what is left (surveyed 2026-11-24, nothing built yet)

This is a survey of the 1.21.1 source, not a port. Counts are files in `src/main/java` (plus `sim/`) that mention each
thing, so they say how far a change spreads, not how long it takes. The survey is what the "may differ" list above
rests on.

**Toolchain (checked):**
- Create 6.0.8 for 1.20.1 is built on Forge 47.1.33 with Flywheel 1.0.6-beta-266, Ponder 1.0.91 and Registrate
  MC1.20-1.3.3; all of them are on the Create maven. The 1.21.1 build uses Flywheel 1.0.6, Ponder 1.0.82 and Registrate
  MC1.21-1.3.0+67.
- ModDevGradle has a legacy plugin for Forge 1.20.1 (`net.neoforged.moddev.legacyforge`), so the same build style
  can be used. It reobfuscates the jar and its `modImplementation` configurations do not pull transitive dependencies.
- **Java 17.** Minecraft 1.20.1 runs on Java 17, and the simulation is built for 21. A first compile of `sim/` with
  `--release 17` failed only on `Math.clamp`, a Java 21 method: 33 uses in 13 files. A port needs `sim/` to build for
  17, which means replacing those calls with a helper that returns exactly the same numbers (including at the edges).
  The baseline files are the check. This is the first job, and it changes the main build too.

**Minecraft 1.20.1 against 1.21.1 (files affected):**
- Block entities save and load with a registry lookup argument in 1.21 (29 files); 1.20.1 has none.
- Block use is split into `useItemOn` and `useWithoutItem` in 1.21 (21 files); 1.20.1 has one `use`.
- Blocks declare a `MapCodec` in 1.21 (13 files); 1.20.1 has none.
- Recipes use `RecipeInput` and the 1.21 serializers (7 files). Item data components (6 files) become item NBT.
- Networking uses `StreamCodec` payloads in 1.21 (8 files); 1.20.1 uses a `SimpleChannel`.
- `ResourceLocation.fromNamespaceAndPath` and `parse` (11 files) are `new ResourceLocation(...)` in 1.20.1.
- **Data folders were renamed in 1.21.** The mod's data uses the singular names (`recipe`, `structure`, `advancement`,
  `loot_table`, and the tag folders); 1.20.1 wants the plural ones. This is done by the build, not by hand, so one set
  of source files makes both. Recipe and advancement JSON also differ in shape.
- Structure files (Ponder scenes and the world fixtures) are 1.21 NBT. They need regenerating or checking on 1.20.1
  before any Ponder page or fixture is trusted there.

**Forge 47 against NeoForge 21.1 (files affected):**
- Capabilities, fluid handlers and energy: the 1.21 capability API is used in 11 files and `IFluidHandler` in 25;
  1.20.1 uses `getCapability` with `LazyOptional`.
- `ModConfigSpec` is `ForgeConfigSpec` (9 files). The event bus and mod-loading packages are renamed (about 30 files). Registry
  registration moves from `DeferredHolder` to `RegistryObject` (6 files).
- **Data maps do not exist** (1 registry file plus `data/create_engines/data_maps`). Fuels, part stats and other
  data-driven values need another route; see "What may differ".
- The GameTest framework is a different API (41 files, 82 imports), so the 541 GameTests have to be carried across by
  hand. Of everything in this list, this is the one that decides whether the port can claim the same guarantees.
- The access transformer (4 lines) and the two mixin files must be checked against 1.20.1's names.

**Create and addons:**
- 144 files import Create. Create 6.0.8 is close to 6.0.10 in shape but has not been compared; the compile will show it.
- Flywheel (15 files) and Ponder (13 files) are on 1.20.1 builds, older than the ones the main build uses.
- Optional mods: Create: The Factory Must Grow (1.0.2c, Forge 1.20.1), Create: Diesel Generators (1.3.5, Forge 1.20.1),
  and Pollution of the Realms (lists 1.20.1) exist for 1.20.1 on their Modrinth pages. Curios, JEI and CC: Tweaked have
  1.20.1 builds that Create itself pins (5.3.1, 15.20.0.106, 1.105.0). KubeJS, Jade and EMI were not checked.
  Create Aeronautics and Sable: no official 1.20.1 build was found, so that row is "not run" and the compat code
  (35 files mention Sable or Aeronautics) would need to load without them.

**What a first Forge build must show before it is called a test build:** `sim/` building on Java 17 with the baseline
files untouched; the mod loading and an engine running with a Create network on it; then the GameTests, in the order
engines, fuels, parts, recipes.
