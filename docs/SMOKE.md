# Smoke testing

The smoke harness exists to answer one question without a person looking: **is this build fit to ship?** It is layered,
because each layer catches a different kind of failure, and none is trusted on its own say-so: every layer ends in a check
that can fail the build, and the harness itself is tested by breaking the mod on purpose (see "Proof it can fail").

```
./gradlew smoke --offline      # unit tests, GameTests, client audit; each verified; fails if any layer found a problem
```

The smoke run is Part 2 of the release checklist in [STABILITY.md](STABILITY.md), which also covers what a person must
check by hand.

Run it in a private copy of the checkout (see "Running it"): other Gradle runs and a development client share `run/` and
`build/` and corrupt each other's results.

## What each layer catches

| Layer | Command | Time | Catches |
|---|---|---|---|
| Unit tests | `./gradlew test` | seconds | simulation maths, parsers, sound synthesis, **the harness's own logic** (the tests in the `smoke` package) |
| GameTests | `./gradlew runGameTestServer` (verified by `verifyGameTests`) | ~1 min | engine behaviour; **every block, item, fluid, recipe, loot table and advancement complete; every block entity placed, ticked, saved, loaded, fed garbage and broken in every sampled state; 16 engines at once under a tick budget; engines whose chunks unload, load staggered, stop ticking, or straddle a border; every tuning and config setting at both ends of its range (a setting left out of the sweep fails it); the lifecycle sweep again with the wear mode on; NaN, infinities and the ends of each number type in place of every saved number, on single blocks and on running diesel, spark and rotary engines** |
| Client audit | `./gradlew runSmokeAudit` (verified by `verifyClientSmoke`) | ~2 min | **every item's tooltip with the wear mode both ways; every goggle and hover readout of the fleet and the gallery; every block state's model and sprites, every item model, translation, sound, particle sheet and Flywheel partial; all 128 Ponder storyboards played to their end; six engines, a refinery, a turbo and a supercharger run for a minute with heap, direct memory, particles, sound voices and frame times tracked; the player walking 3000 blocks away and back; screenshots judged by pixels; then about a minute of thread churn (below)** |
| Jar server | `./gradlew jarServer` (not part of `smoke`, see "The jar server") | 2-10 min | **a real NeoForge dedicated server booted from the built jar with Create alone: every block the jar has a blockstate for is placed and left ticking, the three newest engine fixtures are built from console commands and must turn, and a second boot must find the same blocks and engines; both logs are scanned** |
| Log check | automatic after each run above | seconds | errors the game does not call failures: missing model/texture/sound/recipe messages, stack traces, `OutOfMemoryError`, stale classes, anything logged at ERROR, a crash report |
| Visual smoke | `./gradlew runVisualSmokeTest [-PsmokeEffects ...]` | 2-10 min | the original picture-taking runs, for a person (or Claude) to *look* at: composition, colour, whether it reads as Create. Their log is scanned too, as warnings only |

The first four are assertions. The last is evidence for a human. A green `smoke` says nothing about whether the pictures
look good; it says nothing is broken in ways a machine can name.

### Thread churn

The audit's `threads` phase (`ThreadChurnAudit`) is for what no unit test reaches: the real sound engine and its thread,
and the render thread working while the integrated server ticks on its own. Beside running engines the client reloads the
sound engine, stops and pauses its sounds, reloads the resource packs and throws away what it has drawn, while the server
thread breaks and rebuilds two of the engines and runs `/reload`. An engine note has to come back within 100 ticks of each
sound break, and the engines, both threads, the voice counts and direct memory have to be sound afterwards. An exception on
any thread fails the log check. A pass does not prove there is no race: it gives one many chances to happen. Nothing is
judged by ear.

## The jar server

`./gradlew jarServer` needs a NeoForge server install, which it never downloads. Make one once: run the NeoForge installer
with `--installServer` into a folder, and accept the EULA in that folder yourself. The task looks in
`../ce-server-test/server` unless `-PserverInstall=FOLDER` says otherwise, and takes the Create jar from the install's
`mods` folder or the `dl` folder beside it. It copies the libraries and `eula.txt` into `run/jar-server` and runs there, on
a free local port, so the install's own world and mods are not touched. The report is `run/jar-server/report/report.txt`,
and each boot's log is kept in `run/jar-server/server/logs`. A server that does not stop within 120 seconds fails the run,
and its thread dump is saved. CI has no install, so this does not run there. No client joins and no player is simulated.

## Reading a result

* The verify step prints the merged report (the game's checks plus the log scan) and exits non-zero on any `FAIL`.
* The game's own report is `run/smoke-client/smoke/client/report.txt`; GameTest reports are in
  `run/gametest/smoke/gametest-<name>/report.txt`. Each has a table by category, every `FAIL`, `WARN` and `SKIP`, and a
  `METRICS` block (frame times, heap, particles, cost of 16 engines) to compare between runs.
* Screenshots the audit judged are kept in `run/smoke-client/smoke/client/shots/`.
* **FAIL** fails the build. Something is wrong, or the harness cannot tell.
* **WARN** does not. Probably wrong but possibly deliberate. Read them; a WARN you decide is fine should become a
  documented exception in the check, not a habit of ignoring warnings.
* **SKIP** means a check could not run, and says why. A skipped check proves nothing.
* A run with no `result.properties`, or `completed=false`, fails: a crash, a hang and a client that never reached the world
  all look like that. The audit saves its findings after every phase, so a crash still reports what it found before it
  (a missing particle sprite crashes the game the moment one spawns; the report names the missing description, not just
  the crash).
* Every run starts by deleting the previous run's report, log and crash reports, so a run that dies at launch cannot be
  judged by its predecessor's results.

## Checks that run without a game

`./gradlew check` also runs the architecture rules, the config matrix, the property tests and the performance budgets
(all unit tests), then the JaCoCo coverage ratchet. `./gradlew coverageMerged` is the same measure across the unit tests, the GameTests and the client audit together (about
7 minutes, needs a game window, part of `smoke`), with its own floor in `src/smoke/coverage-floor-merged.txt` and a report in
`build/reports/jacoco/merged`. The floor is `src/smoke/coverage-floor.txt`: raise it by hand when
coverage improves, never lower it to make a build pass. Both ratchets measure the mod itself and leave the test harness (the
GameTests, the smoke audit, the showcase and visual smoke classes) out: it is test code no unit test runs, and counting it made
the unit floor fall every time a GameTest was added (0.1459 against 0.15 at 2.0, with the mod's own code at 0.2133, so the
floor was set to 0.21). The first run needs the network once, to fetch the JaCoCo agent.
`.github/workflows/verify.yml` runs these on every push and pull request, plus a job that fails if `runData` changes
anything under `src/generated`.

## Proof it can fail

A harness that has only ever passed has proved nothing. `scripts/smoke-mutations.ps1` breaks a *copy* of the mod in
known ways and lists what must turn red. The first ten rows below were verified 2026-11-16, the next two on 2026-11-18 (the
client set, in a private copy: every EXPECT line appeared among the failures), and the last two (the `threads` and `jar`
sets) on 2026-11-25, each caught by the check named:

| Break | Caught by |
|---|---|
| a block's loot table deleted | `[data] block sump has a loot table` |
| a block entity that chokes on type-confused saved data (passes on its own saves) | `[lifecycle] battery ... mutation: hostile data` |
| a recipe ingredient that matches nothing (an empty tag) | `[data] recipe ... has no empty ingredient` |
| engines made 0.7 ms slower per tick | `[perf] 16 engines cost under 2 ms a tick` |
| a translation key removed | `[lang] block sump has a translation` |
| a model texture that does not exist | `[models] block sump` (exact model walk) **and** `[render] gallery_0 has no missing-texture pink` (pixels, independently) |
| a Ponder storyboard that throws | `[ponder] sump plays` (two pages: it is shared with the oil filter) |
| a sound event missing from `sounds.json` | `[sound] event exhaust_pop is in sounds.json` |
| a direct buffer allocated and kept on every sound read (the 1.2.0 bug, made worse) | `[perf] direct memory does not climb` |
| a particle description missing (crashes the game) | `[particles] gas has a particle description` + `the run did not complete` |
| the multipart models no longer shared (1.4.0: every pipe state holds its own copy again, about 440 MB) | `[models] every multipart state of the mod shares its block's one baked model` |
| a model face whose texture coordinates run past the edge of its sprite (the 1.4.0 purple rockers and pushrods) | `[models] partial model ROCKER keeps its texture coordinates inside its sprites` (the baked quads), and `ModelUvAssetsTest` for the model files |
| an engine note the sound engine dropped is never replaced | `[threads] an engine note comes back after the sound engine reloaded` |
| a package the server needs left out of the jar | `[server] the run stopped` (`jarServer`) |

Building this found real holes in the harness itself, all fixed: an empty tag does not look empty (vanilla swaps in a
barrier named "Empty Tag", so a naive check passes it), a crash lost every finding made before it, and a run that died
at launch was judged by the previous run's stale report. If a mutation stops being caught, a check has been weakened:
fix the check, not the list. Re-run it after changing any audit.

The script also makes four breaks that **have not been verified** yet, for the checks added 2026-11-25: a loader that throws on
a NaN, a `Tuning` field left out of the sweep, an untranslated line in an item tooltip, and a goggle readout that claims
lines it did not write. Run the `server` and `client` sets, then add them to the table.

## Found and fixed: the engine that stayed dead

The client audit found that after the player walked 3000 blocks away and back, the supercharged spark engine at the far
end of the fleet (across a chunk border) was stalled and stayed stalled. Nine server-side chunk GameTests passed, because
they unload an engine whole. The cause, found by logging the server's view of that engine through the walk: the
crankshaft's chunk stayed loaded while the far half's did not, so the engine's layout went *incomplete*, and an incomplete
engine had its crank speed zeroed every tick (`EngineController.tick`). When the far chunk returned, the engine was rebuilt
from that zeroed motion, and a stalled engine never restarts itself. An engine unloaded whole kept its saved speed and
recovered, so only engines split by a chunk edge died.

The fix: an incomplete engine still stops, but remembers the speed it had (`heldOmega`, returned by `motion()`), so it picks
up again when the missing part loads, as an engine unloaded whole does. The fleet check is a hard check again. A server
GameTest could not reproduce it: a forced chunk keeps its neighbours loaded, so the far half never unloads there. The
client audit is the guard.

## Why these methods

* **Exact checks over pictures.** A missing texture is found by walking every quad of every baked model and comparing its
  sprite to the missing-texture sprite, per block state. That cannot be missed the way a magenta corner of a screenshot
  can. The pixel checks (`PixelChecks`) are a backstop for what the model walk cannot see.
* **Texture coordinates too.** The sprite check cannot see a face that has the right sprite and the wrong coordinates: a
  face that runs off the edge of its sprite shows the sprite next to it in the atlas, which is stray colour or purple and
  black in one pack and fine in another (1.4.0: pushrods, rockers and head plugs, found by a player, not by the audit, which
  passed with it on screen). So the audit also measures every quad's coordinates against its own sprite (`UvBounds`), for
  blocks, items and the Flywheel partial models, and `ModelUvAssetsTest` does the same for the model files with no game.
  The allowance is half a pixel; fix a failure in `tools/generate_models.py` (`WRAP_UV`, `WRAP_MIN_OVERRUN`) or give the
  face a `uv`, never by widening it. The pixel check missed this one too: a shaded missing texture is dark purple, not magenta.
* **Trends over snapshots.** The 1.2.0 sound crash was a slow leak of direct memory. No screenshot shows that; a series of
  `BufferPoolMXBean` readings with a climbing fit does (`PerfSampler.climbing`). Heap is sampled after a GC so garbage is
  not mistaken for a leak; frame times are judged by percentiles because a stutter lives in p99, not the mean.
* **Hostile input.** Block entities are loaded from empty tags, from tags whose every value is the wrong type, and from
  tags with unexpected keys, because a world saved by another version or edited by a person looks like that.
* **Bad numbers, then ticked.** Text where a number should be is read back as zero, so it proves little. A NaN has the
  right type: a loader with no clamp takes it, it spreads through the simulation, and it is saved into the world. So
  `HostileSaves` replaces every number (Create's own kinetic keys excepted), loads it into the placed block, ticks it, and
  requires that nothing throws and nothing that is not finite is saved back.
* **Readouts are read.** A goggle readout runs only when somebody looks, and one that claims lines it did not write
  crashes the client (the Cast Flywheel, 2.1.0). `ReadoutAudit` reads every one the client holds, and every item tooltip,
  including wear values no one meant to put on a part.
* **Both ends of every setting.** Players edit the config file; the value nobody tried is the one somebody types. The
  sweeps check their own coverage against `Tuning`'s fields and `CEConfig.values()`, because by 2.3 the tuning sweep had
  quietly fallen nine settings behind, the wear mode among them.
* **Lifecycle, not just life.** Engines are built, run, unloaded with their chunk, reloaded, walked away from and returned
  to. Bugs cluster at those transitions, and the finding above was one.
* **The harness is tested.** `LogScannerTest`, `PixelChecksTest`, `PerfSamplerTest` and `SmokeReportAndVerifyTest` prove
  the checkers flag what they should and stay quiet about what they should, and the mutation script proves the whole
  pipeline end to end.

## Running it

```
# in a private copy, so nothing else shares its run/ and build/ folders
robocopy <checkout> <copy> /MIR /XD run build .gradle .git
cd <copy>
./gradlew smoke --offline --continue > smoke.log 2>&1
```

`--continue` lets every layer run even if an earlier one fails. Individually: `./gradlew test`,
`./gradlew runGameTestServer`, `./gradlew runSmokeAudit` (each verified automatically). The GameTest server and the audit
now use their own folders (`run/gametest`, `run/smoke-client`), so they start from shipped default settings and never share
a log or config with a development client. The client audit opens a real window for about two minutes.

**A copy that is reused must have its test world cleared.** `robocopy /MIR /XD run ...` leaves `run/` alone, and the GameTest world in
`run/gametest/world` keeps its entities between runs. The stress tests (`StressGameTests`) drop hundreds of thousands of items in the
chunks they use (a casing for every state of every clad block, a piston and rod for every state of a cylinder), so a second run in the same
copy loads the first run's pile, and a third the two before it: the heap climbs to the JVM's limit mid-run, the log gets an
`OutOfMemoryError` from `entity-deserializer` and "Saving oversized chunk" warnings, `verifyGameTests` fails on it, and the server then hangs
after "All N required tests passed" until Gradle's 20-minute timeout kills it. Nothing is wrong with the code when this happens (the same tests
in a fresh copy pass with a peak under 2 GB). Delete `run/gametest/world` before each gate run in a reused copy, or make a fresh copy
for the gate; the release gate always uses a fresh one.

To re-prove the harness: make a *separate* copy, run `./scripts/smoke-mutations.ps1 -Copy <copy> -Set server|client|crash|threads|jar`,
run the Gradle task it names, and check every `EXPECT` line appears among the failures. It refuses to run in a folder with
`.git`.

## The sound bench

Step 0 of [SOUND-PLAN.md](SOUND-PLAN.md): the game's own sound, recorded and audited, so a change to the sound shows its figures
from the game and not only from an offline render. It is not part of `./gradlew smoke`: it needs a machine with speakers that
nothing else is using. The release gate asks for it only when a release changes the sound system somewhat significantly
([STABILITY.md](STABILITY.md), 2.6).

```
/c/Python312/python.exe tools/sound_bench.py                 # everything, about 30 minutes
/c/Python312/python.exe tools/sound_bench.py --scenes v12    # one scene or a few, while iterating
/c/Python312/python.exe tools/sound_bench.py --moving        # the moving structure only, with Create Aeronautics loaded
python tools/sound_audit.py run/sound-bench/<date-time>      # audit a finished run again, without the game
python tools/sound_audit.py --self-test                      # the audit finds made-up faults and passes clean sound
```

It needs `soundcard`, `numpy` and `scipy`. The runner copies the checkout to `create-engines-soundbench`, runs
`gradlew runVisualSmokeTest -PsmokeSound` there and records what the default speakers play. Keep them on and everything else
quiet, and never run it beside a trailer recording: each would record the other. All sound categories but blocks are turned
off in that copy.

* **Scenes** (`client/ShowcaseRunSoundBench`; `--scenes` takes these names): `single` (diesel), `i4`, `v8_cross`, `v8_flat`,
  `flat4`, `vtwin`, `i6_diesel`, `v12`, `rotary`, `turbo_i4`, `reference` (silence, a furnace, a minecart, a Mechanical
  Press), `walls` and `indoors` (below) and, last, `room` (one single, then fifteen more round it, heard from the same spot,
  then all of them removed).
  Engines have open exhausts and are heard from 6 blocks. Each runs one script: start, idle, throttle sweep, blip, a load
  step (a clutch engages Mechanical Presses, fewer for the small engines), stall (its air is taken away), restart, then the
  listener walks to 34 blocks and back. The rotary idles with its throttle wider open: at the others' idle stop it runs too
  lean to light.
* **The soak** (`soak`, `client/ShowcaseRunSoundBenchSoak`, about 4 minutes): the listener stands among sixteen singles.
  One runs alone first, then all sixteen: as they come up, after time to settle, while the listener paces through them,
  and standing again for a minute. The audit's faults: the crowd more than 6 dB above the one from the same spot,
  getting louder as it settles or while left alone (over 1.5 dB), or a second of it played again almost sample for
  sample (correlation over 0.97, a loop). It reports the loudness against the loudest of the three reference sounds but
  sets no limit on it. It does not play a warning: warnings are separate sounds that none of the many-engine rules
  touch, and the capture could not tell one from the engines well enough to judge its level.
* **Where it is heard** (`client/ShowcaseRunSoundBenchPlaces`): one idling inline four each. `walls` listens from 12 blocks
  with nothing in the way, then through one, two and three blocks of stone, then in the open again. `indoors` listens from
  6 blocks out of doors, shut in a stone room with the engine, and out of doors again. `moving` is only run by `--moving`
  (gradle `runSoundBenchAeronautics`, with Create Aeronautics and Sable): the engine is lifted into a moving structure,
  held beside the listener, then carried 24 blocks to each side at 8 blocks a second. It has no reference sounds, so its
  timing is not checked against the Mechanical Press.
* **The log** (`events.jsonl`) has each phase, each firing as it is drawn, cam sightings, the volume and pitch each tickable
  engine sound is set to, each read of a note stream, and the engine's speed and load each tick. The mod reports them
  through `content/SoundProbe`, which does nothing unless the bench is listening. It also has how long each stretch of a
  stream took to make, and each look round the world for walls or a room.
* **The audit** (`report.md`, `report.json`, and `wav/<scene>.wav`) reports stream underruns and the least audio a stream
  had queued; silent gaps while an engine runs; short bursts above 6 kHz that no firing explains; volume or pitch jumping from
  one tick to the next (from the log) and 50 ms level stairs (from the recording); how long after each firing drawn it is
  heard; loudness against the three reference sounds; the room of sixteen against its one engine (limit 6 dB); and how
  alike one firing is to the next. It also reports **how late a change is heard** (how long audio waits in a stream's queue:
  worked out from the log, not heard, and without the synthesizer's follow, the sound card or the look-ahead), **what the
  sound costs** (the sound thread's share of one core against the roadmap's 2%, by the clock, and the game thread's looks
  round the world), and for `walls`, `indoors` and `moving` each phase's loudness and brightness (the middle of its
  spectrum) against the scene's first. Under **Faults found** it names the room still sounding after its engines are gone
  ([a bug since fixed](SOUND-PLAN.md#known-bug-the-shared-room-voice-outlives-its-engines-fixed)), and any scene of one
  engine that played two of its notes at once for over a second (how stacked copies on a moving structure would show).
  The note's and the room voice's source volumes do not move: their fades are inside the stream, so only the recording can
  show a stair in them.
  `--compare <earlier run>` puts that run's figures beside the new ones.
* **A/B with real engines:** `--refs <folder>` with `<scene>.wav` recordings writes `ab/<scene>.wav`: the real one, a second
  of silence, the game's, at the same loudness. No recordings are in the repository; which engines to compare with is the
  author's choice.

Read the figures for what they are. Lag is measured against a clap the game plays through its own sound, so 0 ms means as late
as any other sound the game plays at once, not the delay to the ear. A firing's onset is only looked for within 0.45 of the gap
to the next, so on a busy engine a lag is known only to within that window (shown beside it), and firings closer than 90 ms
are not measured at all. The recorder's clock and the game's drift apart a little over a long run (the report says by how much
between its two claps), which bounds how far to trust a difference of a few tens of milliseconds.

The click, stair and likeness figures are of everything heard, not of the note alone. A gap is not
judged where an engine is next to silent between firings, as a slow single is: there only the stream log can show a dropout.

A report that starts with "Do not trust this run" is not evidence. It says so when the recorder lost audio or filled holes with
zeros, the two sync claps disagree, a Mechanical Press (played at once) does not measure near 0 ms late, the empty pad is not
silent, or a step never reached its goal (an engine that did not start). The author's listening is what signs off.

## The trailer

The mod's trailer is made by a harness, so it can be cut again when the mod changes. It is a development aid, not a check, and is
kept out of the jar and the coverage count like the rest of the picture-taking code.

Each new minor or major version gets a trailer composed differently from the earlier ones (opening, story, shot order,
camera moves, edit, captions), not the last cut with new shots slotted in, and it shows off that version's new features. The
harness stays; the composition below describes the current cut and is rewritten with the next one.

Every composition keeps viewers through its first and last seconds, each form in its own way:

- **The opening (first 3 to 5 seconds).** The short form must start in the middle of fast, high-speed machinery: no title card,
  intro beat or slow build before it. The long form may start with a short intro card; keep it brief and get to intense
  motion, fast machinery or a dramatic feature reveal inside those seconds.
- **The outro.** In both forms, keep the calls to action (download links, release date, what to watch next) concise and work
  them in just before the visuals end, so they are seen before viewers drop off at the end screen. In the short form they lie
  over moving footage. The long form leads into them smoothly, with no long, separate tail. The AI notice stays.

The current cuts predate these two rules and are not yet checked against them; they apply from the next composition. The
2.4.0 composition the author picked is planned in [TRAILER-2.4.md](TRAILER-2.4.md).

```
python tools/trailer/record.py              # plays the game, records screen and sound, cuts the videos
python tools/trailer/record.py --post-only  # cuts the last recordings again (new captions or music)
```

- **What it records.** `ShowcaseRun` with `-PshowcaseSet=trailer` builds walled pads (the five plain engines; a
  pad with the battery sizes, an ignition coil and an injector assist on every head; a blown inline six; the test-bed diesel with its
  instruments; a diesel driving a row of Create machines, two presses stamping iron and a fan smoking beef over a campfire; a refining column with its flare
  stack; and the later pads below), runs them, goes fullscreen and flies a camera past them for about 222 seconds of footage, smoothly, every frame. The shots are the
  `Shot` records in that class, and every length is a whole number of bars of the music (three seconds) so the cuts land on the beat.
  The run leaves one still from the middle of each shot in `run/showcase-shaders/screenshots`, to check the framing without watching
  it, and `-PtrailerShots=a,b` keeps only the named shots, so a new pad can be looked at in a short run.
- **Framing.** No camera distance is typed in. Each `Shot` gives a box round its subject, in blocks from the pad's corner, with
  the angles it is seen from and how much of the frame it should take up at the start and at the end (more at the end is a push
  in, less is a pull back). `client/ShowcaseRunFraming` works out where the camera stands so the whole box lies inside the safe
  area: clear of the letterbox and the caption, a little above the middle (`TrailerFramingTest`). The run films with one 60 degree
  lens, whatever the game's setting is. A shot whose camera a courtyard wall pulls more than 0.75 of a block off its framing
  logs a warning (`held inside the walls`): look at its still. The shot list is in the order of the cut, so the light only moves
  on from day to dusk.
- **The wheeled car.** With Create Aeronautics (its Offroad wheels) and Sable in the run, pad 11 has a turbocharged precision diesel
  inline eight with one driven Offroad wheel (the crank line runs out of the east end through a gearbox, a shaft down and a second
  gearbox into a wheel mount), three more wheels that roll free on a casing frame, and no walls. The pad's blocks are lifted into a
  moving structure by `ShowcaseRunAero` when `rover_departs` begins; the car drives off east (about 14 blocks in twelve seconds) and
  the camera follows its world position, eased between server ticks so it does not step. Loose items from the Bank Builder are swept
  up. The shots `rover_departs` and `rover_wheels` are in the tour only for a run that has Sable, so a trailer cut from the plain run
  has none. `record.py` loads Aeronautics by default (`runShowcaseShaders -PwithAeronautics`, or `runShowcaseAeronautics` with
  `--no-shaders`); `--no-aeronautics` leaves it, and the shots, out. Lessons for changing the car: a wheel mount costs 16 stress per
  rpm and takes drive from the block behind it; a large cogwheel meshes a small one diagonally; blocks that touch nothing are split off
  into structures of their own when it is lifted.
- **Gameplay clip.** `grow_engine` is seen through the player's own eyes, standing still (creative mode, the Bank
  Builder in hand). One engine grows on a bare pad in six steps, two seconds apart, each the Bank Builder's work on a fresh crank line:
  one cylinder, an inline four, a V8, a V16, the V16 in precision parts, and the V16 boosted. It is driven by `Cue`s (seconds into
  a shot) in `ShowcaseRun.defineCues`; the other shots are a spectator's camera. The engine is rebuilt, not swapped, so no part items
  are dropped on the floor (any that are get discarded).
- **The later pads and what is real in them.** Bays 15 to 17 (past the car's drive) hold the casing styles (four styles of cased
  shaft and cogs between casing pillars, a Maker's Plate, the player dyeing the pillars), a precision twin-turbo V16 that stands cold
  until a cue gives its heavy starter power (then the player, in first person, puts on headphones), and a service bay. The service bay and
  the refinery are built from Create's own machines and only the heat exchangers' heat, and the stock a tank or barrel starts with, is the
  harness's: used oil goes from the pan by a pump to a basin under a mixer (driven from the engine's crank, through a shaft, a gearbox and a
  cogwheel the mixer meshes with), charcoal and clay come from a barrel through a pair of funnels (an extracting funnel above an inserting
  one: a wall funnel only drops its item into its own cell), and a second pump returns the clean oil to the pan; a pump fills a spout from a
  tank, jerry cans ride a belt to an item drain, a pump takes the fuel into the engine's tank and another into a rail of pipes over the
  heads, which are not refilled by the harness. The refinery has a pump from a plant-oil tank, a barrel of coal through the same funnel
  pair, and two pumps to product tanks on its top. Pumps (and the mixer, and the belt) are turned by Create motors with a cogwheel beside
  them, as every starter here has a motor; `logPlumbing` writes the pumps' speeds and every tank to the log at the end of a run. The
  used-oil pump's motor is switched on at tick 600, after the charcoal and clay have arrived: with only 250 mB of used oil and no
  charcoal or clay the basin cokes it, which is the mod's own recipe. The toasts and chat the first-person clips earn are cleared
  every frame, and this mod's advancement sting and the game's toast sounds are dropped from the run's sound.
- **The pads of `client/ShowcaseRunFeatures`** (bays 19 to 23): a boosted gasoline six with no coolant, which `running_hot` shows
  steaming and `running_cooled` cools by fitting two full radiators (the harness holds its block at 190 degrees until then, by
  reflection, as a free-running engine does not get that hot in a few seconds; the steam and the cooling are the mod's); a diesel
  four with a variable-geometry turbo and a MAF sensor; a gasoline four whose exhaust is rebuilt at the start of `muffler_off`,
  `muffler_on`, `exhaust_stub` and `exhaust_long`, so the same engine is heard four ways; a belt where two Deployers and two
  Presses make Copper Gaskets from Rubber ones; and a twin-turbo gasoline V8 with anti-lag, which in `night_pull`
  idles, has its throttles opened by their idle stops and revs, then is lifted off twice with redstone on its camshaft. As that
  shot begins the harness warms the V8 once and, by reflection, gives it the mod's own crank inertia back in place of the
  recorder's five times (see "A steady crank" below), or it would be too sluggish to be seen revving.
- **Sound and walls.** Only the courtyard the camera is in is heard (sounds from other pads are cancelled as they start and stopped when
  the camera moves on). Engines are also kept to the courtyard by where their crankshaft is, because the shared "engine room"
  voice has no place of its own to filter by; during the car's shots only the engine on no courtyard is heard. The camera is held
  inside the walls (see Framing).
- **How it is recorded.** `record.py` copies the project to a private folder beside it (as every client run should be), starts
  `ffmpeg` on the whole screen when the game logs `Showcase trailer ready`, and stops it after the last shot. The game runs with
  Iris, Sodium and a shader pack (`runShowcaseShaders`; the pack is the first .zip in `tools/trailer/shaderpacks`, which is not
  committed; `--no-shaders` runs the game without them). At the same moment it records what the speakers play (WASAPI loopback, the
  `soundcard` package), which is the game's engine sound: keep the speakers on and nothing else playing, and leave the machine alone
  and other windows off the game while it runs. ffmpeg comes from `winget install Gyan.FFmpeg`; the graphics card's encoder is used
  when it has one.
- **Keeping sound and picture together.** The two recorders do not start at the same instant and the speakers lag the screen, so the
  game makes a sync mark in the lead-in: the screen goes white and an anvil clap plays, at one wall-clock moment it writes to
  `trailer_sync.json`. `record.py` finds the flash in the picture and the clap in the sound and measures how far each is from the first
  shot, and prints both next to what the recorders said. Without a mark (or if one is not found) it falls back to the recorders' times.
- **A steady crank.** The showcase engines turn at 40 to 70 rpm, where a light crank's speed ripples by several percent each cycle and the
  network shows it as shafts and presses that surge and jump. `record.py` gives the trailer's private game folder five times the
  inertia on the crank blocks and flywheels (`TRAILER_CONFIG`; a ripple goes as one over the inertia). It touches nothing in the mod.
- **How it is cut.** `post.py` takes the order and the length of every piece of the full cut from `tools/trailer/edit.txt`
  (`<shot> <seconds> [at=] [speed=] [engine] [quiet] [cold]`; every piece a whole number of half bars, 1.5 s at the music's 80
  beats a minute, so the cuts fall on the beat). It renders each piece on its own: its stretch of the recording (sped up when the
  line says so), a light grade (a slight S-curve, a hint of warm highlights and cool shadows, a faint vignette), a 2.2:1 letterbox, and a caption at
  the lower left that slides in, with its chapter's name over it in gold (`chapter <shot>` lines of `captions.txt`; a `quiet`
  piece keeps the caption before it). A chapter starts with a white flash and a hit. The full cut opens cold on the single
  cylinder with only the game's sound, slams the title (the mod's round icon, `docs/images/icon.png`, the "CREATE" lettering
  `docs/images/create_lettering.png`, placed by `tools/create_lettering.py`, and the gold wordmark `docs/images/wordmark.png`)
  over the title shot with a flash and a boom as the music comes in, and ends with a riser into the last shot, a fade through black
  and an end card (the version line from `gradle.properties`, "Links in the description" and the AI notice, `outro_notice`) with a
  sting. The 16:9 thumbnail uses the same title; `tools/listing_art.py` makes the golden listing tile `docs/images/listing_gold.png`.
  All the text is drawn in Minecraft's own font by `mcfont.py`, from the game's font bitmap.
- **The sound.** Mixed in numpy: the game's sound of each piece (sped pieces keep their pitch), brought to one level; the music
  from the title on, which drops to 40% under an `engine` piece while the engines come up; and the hits (`tools/trailer/sfx.py`,
  synthesised: an impact, a riser, a sting). The mix is brought to -14 LUFS, a true peak of -2 dB, by a two-pass loudnorm. The
  music is the first file in `tools/trailer/music/` if there is one, otherwise a backing track that `tools/trailer/music.py`
  synthesises (original, so nothing to license; a placeholder for a real track; 80 beats a minute, a bar of three seconds).
- **The cuts.** The full trailer, a teaser (a title card, four three-second shots named by `short_shots` in `captions.txt` with a
  few big words each, and the end card) and a vertical cut for Shorts, each with a `.srt` of its captions, a thumbnail
  from the shot `thumbnail:` names, and a 9:16 thumbnail for Shorts from the shot `vertical_thumbnail:` names
  (`vertical_thumbnail_at:` seconds in, with `vertical_title:` and `vertical_sub:` for its words).
- **The vertical cut.** Its own edit, `tools/trailer/vertical.txt` (the file says what each option does), cut for a feed where a
  viewer decides in the first second: no title card and no 16:9 band at the start, but a full-frame cold start (`cold_start`:
  a still engine cranked until it catches) with big words on the first frame that say it is Minecraft, every piece
  at least three seconds and the whole cut at most ninety, the words clear of the bottom fifth and the right edge where the app draws its own buttons. A `fit` piece
  shows the whole 16:9 picture in a band on a blurred copy of itself (`zoom=` for a small subject), the others a 9:16 window
  (`x=` moves it). The AI notice is in it twice: `vertical_ai_tag` at the top from the first frame, for a viewer who leaves
  early, and on the end piece the full `outro_notice` with `ai_docs` under it, pointing to [AI.md](AI.md). The end piece lies
  over footage and does not fade, so the Short loops cleanly. It writes `run/trailer/description_vertical.txt` too, with the AI
  notice and the address of AI.md (`ai_docs_url`) in its first lines; the full description carries that address as well.
  `TrailerCoverageTest` checks the edit names real shots on the beat, has words on every piece and ends on the end piece, and
  that the AI lines are there and AI.md exists.
- **The gameplay clips.** The first-person shots keep the player's hand and the item in it; the recorder hides the hotbar, the
  crosshair and the picked item's name (`ShowcaseRun.onRenderGuiLayer`).
- **The video description.** Every full cut also writes `run/trailer/description.txt` (`post.py`, so `--post-only` too): the pitch, the links, chapters from the `chapter <shot>` lines (a chapter under ten seconds long is dropped, as YouTube would ignore it), the version line and the AI notice with the address of [AI.md](AI.md). The words, links and chapters are in `captions.txt`; the Modrinth and CurseForge addresses are not there yet (post.py does not guess them).
- **Output.** `run/trailer/trailer.mp4`, `trailer_teaser.mp4`, `trailer_vertical.mp4`, `thumbnail.png`, `thumbnail_vertical.png`, `description.txt` and `description_vertical.txt`, with the raw recordings and the shot timings beside them.
- **Keeping it current.** `src/smoke/trailer-parts.txt` has a line for every block and item saying which shot shows it, or why none
  does. `TrailerCoverageTest` fails when a new part has no line, when a line names a shot that does not exist, when a shot has no
  caption, and when a shot is missing from `edit.txt`, is there twice, or has a piece that is not a whole number of half bars. A significant new part goes on a pad (or a new one) in `ShowcaseRun`, gets a shot, a caption and a line in `edit.txt`, and its line names the
  shot. A `later` line is a part that is significant and not in the trailer yet: a debt, kept in the open.
- **Length budget.** The full trailer may run up to 240 seconds (title shot and end card included). It is a
  limit for the author and for whoever adds shots, not something a test enforces: when new parts would push it past, trim or shorten
  shots rather than let it grow.
- **What it cannot see.** The test cannot tell that a part is really on screen in its shot, and nobody has heard the synthesised music
  played through speakers. A person watches the cut before it is posted.

## The gallery pictures

`python tools/gallery.py` takes the mod page's engine pictures again with the trailer's shader pack: it runs `runShowcaseShaders`
for the `engines` and `accessories` sets in a private copy (`create-engines-gallery`), keeps every still in `run/gallery/all`, and
puts the ones `tools/gallery_shots.txt` names in `run/gallery/pick` under the gallery's file names. `--apply` copies the picks over
`docs/images`; the git diff of the images is the review. Every picture is kept under 2,000,000 bytes (the sites' 2 MB limit): `gallery.py` shrinks a bigger still (optimised PNG, then 256 colours, then smaller), and `GalleryImageSizeTest` fails a picture in `docs/images` that is over. It opens a real window, so do not run it while recording the trailer.
Pictures from other runs (Ponder, goggles, management, coupled, new parts) are not in the table and are left alone.

## The log allowlist

`src/smoke/log-allowlist.txt` lists log lines that are known harmless. **Every entry must say why.** A line you cannot
explain is a bug. A line starting `warn:` is a *known intermittent* message: still reported, as a warning, so it stays
visible without making the build flaky (the one entry today is an OpenAL "Invalid name parameter" at shutdown that
appeared in 1 of 3 runs). Review the list whenever Create, NeoForge or Minecraft is updated and delete entries whose
reason no longer holds.

## Adding to it

* New block, item, fluid, recipe, sound or particle: nothing to do. The audits walk the registries, so new content is
  covered the moment it registers; a gap (no loot table, no translation, no item model) fails on its own. A block that is
  *meant* to have no item or no Ponder page goes in `ServerAudit.NO_ITEM` / `PonderAudit.NO_PAGE_NEEDED` with a reason.
* New setting: add it to the tuning or config sweep in `SmokeAuditGameTests`; the sweep fails and names it until you do.
* New goggle readout or item tooltip: nothing to do. A readout that only appears in a state the fleet and gallery never
  reach (a failed part, say) is not read; put that state in the fleet or give it its own GameTest.
* New block entity: it is swept automatically. If its save is legitimately not stable across a load, add the changing key
  to `ServerAudit.VOLATILE_KEYS` with a reason.
* New engine behaviour worth guarding: add a GameTest as before. Add a scenario to `SmokeAuditGameTests` only if it is
  about scale, time or lifecycle rather than one rule.
* New sound event: the fleet must make it play, or it shows as "never started"; add its trigger to the fleet in
  `SmokeAudit.fleet`.
  One documented exception: sounds that fire by chance by design (`CHANCE_SOUNDS` in `SmokeAudit`: the cooling tick, the
  pre-ignition knock, the flare whoosh) are started by hand at the end of the run if the fleet did not, and a check fails
  if the sound engine never hears them. A sound that is not random must still be started by the fleet. The whistle
  needs the camera within 48 blocks of the turbo, so the run stands there while the wheel is up to speed.
* A new class of failure you were bitten by: write the check that would have caught it first, in a layer that can fail
  the build, add a unit test that the check fires (and a line to the mutation script), then fix the bug.

## What it cannot see

* Whether anything *looks right* or reads as Create. Pictures are kept for a human; the pixel checks only catch the
  broken, the blank and the frozen.
* Audio quality. The audit proves every sound resolves, starts, and does not leak voices; not that it sounds good.
  [The sound bench](#the-sound-bench) finds faults in what the game plays, but not whether it sounds good either.
* Multiplayer, other mods, other GPUs, Linux and macOS. Everything here runs one machine's dev environment, single
  player, with Create and Flywheel only.
* Behaviour over a *real* long session (hours). The fleet runs about a minute; slower leaks need a longer soak.
* A dedicated server with real players. `jarServer` boots one from the built jar and runs engines on it, but no client
  joins, so nothing sent to a client from a dedicated server is exercised.
* Save and reload of a whole world through the title screen. Block entities are save-and-load round-tripped, and chunks are
  unloaded and loaded, but the world is never closed and reopened from disk in one run.
