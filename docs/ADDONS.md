# Addon author's guide

For people writing a Java mod that adds something to Create: Engine Engineer: a turbine, a two-stroke, an electric assist, a
block that rides an engine's crank. Pack makers who only need data and config want [PACKS.md](PACKS.md) instead.

The mod gives addons two things, both frozen at 2.0 as **API 1.1.0** (the rules are in [API.md](API.md)):

1. **Read an engine** and **listen to it**: `EngineApi`, `EngineView`, and two events on `NeoForge.EVENT_BUS`.
2. **Put something on an engine's crank**: write a `TorqueSource`, return it from an `EngineSourceProvider`, and the engine
   steps it, rubs it, heats it, shows it in the goggles and can feed it boosted air.

Everything not named in [API.md](API.md) is internal and may change in any release, 2.x included. That is a promise about the
parts you can rely on, not a suggestion to avoid the rest: see "What you can rely on" below.

## Start here

### 1. Depend on the mod

The jar is the API: there is no separate API artifact. The packages you may use are `com.createengines.api` and, for the types a
provider returns, `com.createengines.engine.sim.TorqueSource`, `AbstractTorqueSource`, `Breather` and `StatusLine`.

For a build, any Maven that serves the mod's files works. Placeholders are in angle brackets; take the real values from the
mod's page:

```groovy
repositories {
    // Modrinth: maven.modrinth:<project slug>:<version id>
    maven { url = 'https://api.modrinth.com/maven'; content { includeGroup 'maven.modrinth' } }
    // CurseForge: curse.maven:<any-name>-<project id>:<file id>
    maven { url = 'https://www.cursemaven.com'; content { includeGroup 'curse.maven' } }
}
dependencies {
    compileOnly "maven.modrinth:<project slug>:<version id>"
    // or: compileOnly files('libs/create_engines-2.0.0-1.21.1.jar')
    // and, to run the game with it in your dev environment: runtimeOnly the same dependency
}
```

Declare the dependency in your `neoforge.mods.toml` so the game orders the mods and refuses a mismatch:

```toml
[[dependencies.your_mod_id]]
modId = "create_engines"
type = "required"                 # or "optional" if you can run without it (see below)
versionRange = "[2.0,3)"          # the 2.x line: the API is stable across it
ordering = "AFTER"
side = "BOTH"
```

This mod targets Minecraft 1.21.1 on NeoForge and needs Create 6.0.10 or newer (not 6.1).

### 2. A provider and a source

An **`EngineSourceProvider`** is asked, on the server thread, whenever an engine is built or rebuilt: "does this engine need
anything from you?" It looks at the world around the crankshaft, which is its own business, and returns the sources to add (new
ones each time). A **`TorqueSource`** is the thing on the crank: the engine tells it the crank angle and the time, and it says
how much work it did.

`AbstractTorqueSource` gives every question the quiet answer, so a source says only what it does to the crank. The example below
is a complete addon feature: a block that adds a little steady torque to any engine it touches. It is the file
`src/main/java/com/createengines/gametest/ExampleAssist.java`, which is built with the mod and run by a GameTest, and a test makes
this page match it, so it compiles and works as shown.

<!-- example:src/main/java/com/createengines/gametest/ExampleAssist.java -->
```java
/** Torque each assist motor puts on the crank, in the simulation's units. A crude single cylinder makes about 0.4. */
static final double TORQUE_PER_MOTOR = 0.15;

/** Adds an {@link AssistMotor} to every engine that has one of {@code motor} touching its crankshaft. */
public static final class AssistProvider implements EngineSourceProvider {
    private final Block motor;

    public AssistProvider(Block motor) {
        this.motor = motor;
    }

    @Override
    public List<TorqueSource> sourcesFor(Level level, EngineBuild build) {
        int motors = 0;
        for (BlockPos segment : build.segments())
            for (Direction side : Direction.values())
                if (level.getBlockState(segment.relative(side)).is(motor))
                    motors++;
        // A new source each time: the engine drops the old ones whenever it is rebuilt
        return motors == 0 ? List.of() : List.of(new AssistMotor(motors));
    }

    /** Placing or breaking one of our blocks beside an engine makes the engine ask again; nothing else to call. */
    @Override
    public boolean watches(BlockState state) {
        return state.is(motor);
    }

    /** Touching means one block from the crankshaft. */
    @Override
    public int reach() {
        return 1;
    }
}

/** The motors of one engine, as one source. */
static final class AssistMotor extends AbstractTorqueSource {
    private final int motors;
    private final double torque;

    AssistMotor(int motors) {
        // Weight and drag: a little inertia and friction for each motor
        super(0.1 * motors, 0.02 * motors, 0.0005 * motors);
        this.motors = motors;
        this.torque = TORQUE_PER_MOTOR * motors;
    }

    /** Work is torque times the angle turned, and it is what the engine's energy balance adds up. */
    @Override
    public double advance(double angle, double rotaryAngle, int direction, double delta, double dt) {
        return torque * Math.toRadians(delta);
    }

    /** What it would do in a whole 720 degree cycle, which is how the goggles' torque figure is worked out. */
    @Override
    public double availableWork() {
        return torque * Math.toRadians(720);
    }

    /**
     * Left false on purpose: {@code fired()} is what makes an engine count as running under its own power, and a motor
     * that assists a cylinder should not make a dead engine look alive.
     */
    @Override
    public boolean fired() {
        return false;
    }

    /** A line for the goggles on the crankshaft. The key is looked up in the addon's own lang file. */
    @Override
    public List<StatusLine> status() {
        return List.of(StatusLine.of("example_addon.assist", StatusLine.Tone.NOTE, String.valueOf(motors)));
    }
}
```
<!-- /example -->

Register it once at startup:

```java
@Mod("your_mod_id")
public class YourMod {
    public YourMod(IEventBus modBus) {
        modBus.addListener((FMLCommonSetupEvent event) -> event.enqueueWork(() ->
            EngineExtensions.register(new AssistProvider(YourBlocks.ASSIST_MOTOR.get()))));
    }
}
```

Your block can be an ordinary block. The engine only needs to be told when one is placed or broken next to it, and
`watches`/`reach` do that for you.

## How an engine uses a source

The engine is a rotating crank with an energy balance, stepped many times a tick in small crank-angle steps (4 to 16 degrees,
so a step is shorter at low speed and longer at high speed). For every step, for every source:

1. `advance(angle, rotaryAngle, direction, delta, dt)` is called. You return the **work** you did on the crank over that step, in
   the simulation's units, negative if you took energy (a compression stroke does). Work is torque times the angle in radians.
2. `fuelUsedLastStep()` is added to the engine's fuel rate.

The crank's speed follows from `½ I ω²` changing by the work done, minus friction and load. Once a tick the engine calls
`endTick()`, reads `exhaustHeatLastTick()` (which warms the block and spins turbochargers on the exhaust), then tells the source
how cold the block is (`setColdness`), which way the crank turns (`setDirection`) and, for a source that is not attached to a
port, the air it breathes (`setIntake(pressure, temperature)`).

**Scale.** The units are the simulation's own: ambient pressure and temperature are 1, and one crude cylinder makes a mean
torque of roughly **0.4** at moderate speed. The server's `stressPerTorque` setting turns torque into Create stress (about
78.7 SU per RPM for each unit by default), so the example's 0.15 per motor is about 12 SU per RPM per motor. How the simulation is
calibrated is in [DESIGN.md](DESIGN.md); the quickest way to find the number you want is to run your source in a dev client and
read the engine's torque in the goggles.

### The contract

| Method | What you give |
|---|---|
| `inertia()`, `frictionStatic()`, `frictionViscous()`, `frictionAt(omega, film)` | the weight and drag you add. `AbstractTorqueSource` computes `frictionAt` from the other two the way a cylinder does, so thin oil makes it drag more |
| `advance(...)` | the work done in the step. **Runs for every source on every step**: do not allocate, do not lock, and never touch the world |
| `availableWork()` | the work you would do over a whole 720 degree cycle with everything wide open. It is how the engine reports the torque it *could* deliver |
| `fuelUsedLastStep()` | fuel taken in during the last `advance`, never negative; 0 if you burn none |
| `unburnedFraction()` | the share of the fuel the last cycle took in and did not burn, 0 to 1; **negative if you took none**, so you count neither for nor against the oil |
| `fired()` | whether the last cycle lit its charge. It is what makes an engine count as **running under its own power**; an engine that nothing says has fired is cranking or coasting, not running. Return true only for a source that really does what a cylinder does |
| `exhaustHeatLastTick()` | heat put into the exhaust in the last whole tick, in the units of the engine's thermal model. 0 for most |
| `endTick()` | close the tick's accounting |
| `setColdness`, `setDirection`, `setIntake` | what the engine tells you (all optional to use; `AbstractTorqueSource` keeps them for you) |
| `status()` | goggle lines (below), read on the server about once a second |

A source is stepped from **one thread** (the server thread, from the engine's tick) so it needs no locking. If you display
something to the client, put it in `status()`, not a field the client reads.

## Providers

```java
List<TorqueSource> sourcesFor(Level level, EngineBuild build)   // required
default boolean watches(BlockState state)                       // false
default int reach()                                             // 2
default BlockPos intakeAt(TorqueSource source)                  // null
default BlockPos exhaustAt(TorqueSource source)                 // null
```

- **When you are asked.** When an engine is built or rebuilt, and when `EngineExtensions.rebuild(level, pos)` is called for a
  block of the engine. Nothing persists between builds: the sources you gave an old build are dropped with it, so **never share a
  source between builds** and keep any state worth keeping in your own block entity, where you read it again in `sourcesFor`.
  An engine keeps its speed, oil and heat across a rebuild.
- **`EngineBuild`** tells you the `primary` crankshaft block (the engine's name), the `axis` it runs along, every `segments`
  position in order and every block of the engine the mod found, `members`.
- **`watches` and `reach`.** If a block you watch is placed or broken within `reach` blocks (along each axis) of any crank
  segment, that engine is rebuilt at the end of the tick and you are asked again. You never have to call `rebuild` for your own
  blocks. A block you do **not** watch changes nothing. Keep `reach` small; the blueprint recorder looks out at most 8.
- **Failure.** A provider that throws or returns a null source is logged and skipped, and the engine builds without it. A source
  whose `status()` throws is left out of the goggles, and reported the same way. Neither ever stops an engine.
- **Registering late.** Register in `FMLCommonSetupEvent`'s `enqueueWork`. A provider registered later is picked up when an
  engine next rebuilds.
- `EngineExtensions.unregister(provider)` removes it; it applies to no engine built afterwards.

## Sources that breathe

A source that implements **`Breather`** and is attached to a port sits in an engine's intake or exhaust network the way a cylinder
does. Return a block that is in, or touches, one of the engine's intake pipes or openings from `intakeAt(source)` (and the
exhaust equivalent from `exhaustAt`), and the engine puts the source in the lists of every turbocharger, supercharger and
intercooler on that network: compressors set the source's intake pressure and temperature (series stages multiply, parallel ones
average, intercoolers cool, the server's boost strength and thin air apply), and turbines on its exhaust network are spun by its
exhaust heat and held up by its back-pressure. An attached source is **not** also handed the engine-wide average through
`setIntake`; one with no port still is. It does not count in the network's opening and head figures, so your mouth neither helps
nor hurts a cylinder's breathing.

`Breather` adds six methods the engine sets once a tick before it steps you: read `intakePressure()` and `intakeTemperature()`
when you work out what you burn, and set the exhaust pressure you blow down into. All pressures are in atmospheres (1 is none)
and the temperature is relative to ambient. Their behaviour is covered by `BreatherTest` and the GameTest
`aSourceAttachedToAnIntakeNetworkBreathesTheBlowersBoost` in this repository, which are the best worked example.

## Goggle lines

`status()` returns `StatusLine`s for the Engineer's Goggles readout on the crankshaft:

```java
StatusLine.of("your_mod.turbine.spool", StatusLine.Tone.WARN, "80")
```

The key is a **translation key from your own lang file** (`"your_mod.turbine.spool": "Spool %s%%"`); the arguments fill its `%s` slots;
a key nothing translates is shown as it is. Tones are `NOTE` (grey), `GOOD` (green), `WARN` (gold) and `BAD` (red). Return a short
cached list: **at most 8 lines are shown across all of an engine's sources**, 4 arguments to a line and 96 characters to a key or
argument (anything past that is cut). They are synced to every client that has the engine in view.

## Blueprints

A block your provider `watches` within its `reach` of the crankshaft is also a **member of the engine's blueprint**: the Blueprint
recorder records it with its id and block state (no dials), and the server accepts that block id in a pasted blueprint while your
provider is registered. Any other mod's block is still refused. Placing it needs your mod installed, as with any block.

## Reading and listening

```java
Optional<EngineView> view = EngineApi.viewAt(level, pos);   // any block of the engine; empty if not an engine yet
```

`EngineView` is a record: `status`, `rpm`, `availableTorque`, `loadTorque`, `cylinders`, `firing`, `temperature`, `oilCondition`,
`boost`, `health` and `cause`. It is a read-only snapshot and changes nothing.

Two events are posted on `NeoForge.EVENT_BUS`, on the server thread, and cannot be cancelled:

```java
NeoForge.EVENT_BUS.addListener((EngineStateChangeEvent e) -> {
    if (e.started()) { /* e.level(), e.primary() */ }
    if (e.stalled()) { /* ... */ }
});
NeoForge.EVENT_BUS.addListener((EngineMilestoneEvent e) -> {
    if (e.milestone().equals("engine_running")) { /* ... */ }
});
```

A listener that throws is logged and ignored; it never stops an engine's tick. Milestone names are lower-case with underscores,
new ones may be added (ignore a name you do not know), and one may be posted again for the same milestone after a rebuild or a
reload.

## A cylinder of your own

A third tier of cylinder (or a head) is a block, a model and two data files, and no simulation code:

1. Make the block with `PartBlocks.cylinder(properties)` (or `PartBlocks.cylinderHead(properties)`), register it with the game under
   your own id with your own model and texture, and tell the mod about it with `PartBlocks.register(modBus, () -> yourBlock)`. The
   scanner treats it as it treats ours: it takes a crank throw, a head, ports and a cam lobe the same way. `PartBlocks` (API 1.1) is
   all you name from this mod, so nothing depends on a class that may change.
2. Write `data/<yourmod>/part_effects/<name>.json`: the same keys and ranges as the four precision parts in [DATA.md](DATA.md).
3. Point the block at it in `data/create_engines/data_maps/block/part_effect.json`:
   `{ "values": { "yourmod:racing_cylinder": { "effect": "yourmod:racing_bore" } } }`.

The effect is multiplied in after the precision parts (the server's `precisionBonus` blends it as it blends them), so a racing
cylinder that also has a forged piston has both. A block that is not a
cylinder or a head at all needs the source provider route in "A provider and a source", which can add a whole new engine type. The
timing of the valves can be changed for every engine, not for one block, with `data/create_engines/cam_profiles/` (DATA.md).
This path is tested twice: in this repository with the effect named through the data map's test hook (`OwnPartGameTests`), and by a real addon that depends on the finished jar and nothing else, [examples/racing-addon](../examples/racing-addon): a Racing Cylinder block, its data map entry and its part effect, with GameTests that run an engine with it beside an engine with the plain cylinder and check the racing bore can deliver more. Copy it to start your own. Running it is in its README. A test (`ExampleAddonUsesOnlyTheApiTest`) fails if it ever imports anything of this mod outside `com.createengines.api`: if an addon needs an internal class, the API is missing something, and the answer is a new public surface.

## A flywheel of your own

Added in API 1.2. A flywheel is a block, a model and one data entry, and no simulation code:

1. Make the block with `PartBlocks.flywheel(properties)`, register it with the game under your own id with your own model, and tell the
   mod about it with `PartBlocks.register(modBus, () -> yourBlock)`, the same call as for a cylinder. It sits on the crank line
   like a Cast Flywheel: either end of the crankshaft, or beyond a plain shaft from it.
2. Model it along the Y axis and turn it with a blockstate for `axis=x`, `axis=y` and `axis=z`, as the example does. The mod turns the
   model with the shaft.
3. Give it masses in `data/create_engines/data_maps/block/flywheel.json`:
   `{ "values": { "yourmod:racing_flywheel": { "primary_inertia": 0.6, "secondary_inertia": 2.0 } } }`. The primary mass joins the
   crank; the optional secondary mass smooths the ripple above the spring pack's resonance, as the Dual-Mass Flywheel's does (see
   [DATA.md](DATA.md)). A pack can retune it by supplying the same file.

A flywheel with no entry adds nothing. The example addon's Racing Flywheel does all of this, with a GameTest that runs an engine with it.

## What you can rely on

| | |
|---|---|
| **Frozen** (API 1.2.0, in 2.x only added to) | `com.createengines.api.*`, `engine.sim.TorqueSource`, `AbstractTorqueSource`, `Breather`, `StatusLine`; the `fuel`, `part_stats`, `part_effect` and `flywheel` data maps and the `part_effects` and `cam_profiles` data formats; the KubeJS and CC names |
| **Internal** | everything else: `engine.*`, `content.*`, the registries and the config classes. They may change in any release |

`ApiSurfaceTest` reads every public declaration of the frozen types from the source and fails the build if one disappears or
changes. A new one has to be recorded and `ApiVersion.MINOR` raised. So **code written for an older minor keeps compiling and
running**, and a major version change only comes with a new major version of the mod.

### Versions at run time

`ApiVersion.MAJOR`, `MINOR` and `PATCH` are read at run time (they are deliberately not compile-time constants, so your jar does
not carry the version you built against). To use something added in a later minor version without requiring it:

```java
if (ApiVersion.isAtLeast(1, 2)) {
    // a method added in API 1.2
}
```

`isAtLeast(major, minor)` is true for the same major and a minor that is not older. The mod's own version (2.x) and the API
version (1.x) are separate numbers: your `versionRange` in `neoforge.mods.toml` is on the mod. `/ce api` in game shows the API version and every provider registered.

### An optional dependency

If your mod can run without this one, mark the dependency `optional`, and put every reference to `com.createengines` in classes you
only load when `ModList.get().isLoaded("create_engines")` is true; a class that mentions one of ours in its signatures is loaded when
the JVM links it, so keep the touching code in its own class, as this mod's own CC: Tweaked, KubeJS and Pollution compat do (`compat/`).

## Testing an addon

- **Unit tests**: `TorqueSource` and `AbstractTorqueSource` are plain Java (no Minecraft types), so a source can be tested without a
  game by putting it on an `EngineSimulation` (`addSource`) and stepping it with `tickFree`: see `AbstractTorqueSourceTest` here. The
  simulation classes are not part of the frozen API, so a test that uses them may need updating between releases.
- **GameTests**: build a small engine, register your provider and check the engine's source list. The test helpers in this
  repository's `gametest` package (`TestEngine`, `PackToolsGameTests`, `AddonExampleGameTests`) are written for exactly this and
  are the pattern to copy.
- **A whole addon, built and tested outside this repository**: [examples/racing-addon](../examples/racing-addon) is a separate Gradle project that loads the mod's jar the way a modpack does, registers a block and a data file and runs GameTests of its own against a real engine. It shows how to build a running engine in a test from the registry, Create's blocks and the API alone (a creative motor, a starter, a cam lobe timed with `EngineBuilding.timeCamLobe`, fuel and oil through the head's fluid capability), which the helpers in this repository's `gametest` package are not shipped to do.
- **In game**: `/ce api` lists your provider by class, and the goggles on the crankshaft show your `status()` lines.

## Checklist

- [ ] The dependency is declared in `neoforge.mods.toml` with a `[2.0,3)` range.
- [ ] `sourcesFor` returns **new** sources each call and never the same instance twice.
- [ ] `advance` allocates nothing and never reads the world, a block entity or a config.
- [ ] `fired()` is false unless the source really lights a charge by itself.
- [ ] `watches` is true for exactly your blocks, and `reach` is as small as it can be.
- [ ] State you must keep is in your block entity, read again in `sourcesFor`.
- [ ] `status()` returns a short cached list with keys that are in your lang file.
- [ ] Nothing outside the frozen types is imported, or every use of an internal one has a fallback.
- [ ] A GameTest builds an engine with your block beside it and checks the source arrives and leaves.
