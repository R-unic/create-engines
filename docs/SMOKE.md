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
| Unit tests | `./gradlew test` | seconds | simulation maths, parsers, sound synthesis, **the harness's own logic** (46 tests) |
| GameTests | `./gradlew runGameTestServer` (verified by `verifyGameTests`) | ~1 min | engine behaviour; **every block, item, fluid, recipe, loot table and advancement complete; every block entity placed, ticked, saved, loaded, fed garbage and broken in every sampled state; 16 engines at once under a tick budget; engines whose chunks unload, load staggered, stop ticking, or straddle a border; every tuning setting at both ends of its range** |
| Client audit | `./gradlew runSmokeAudit` (verified by `verifyClientSmoke`) | ~2 min | **every block state's model and sprites, every item model, translation, sound, particle sheet and Flywheel partial; all 99 Ponder storyboards played to their end; six engines, a refinery, a turbo and a supercharger run for a minute with heap, direct memory, particles, sound voices and frame times tracked; the player walking 3000 blocks away and back; screenshots judged by pixels** |
| Log check | automatic after each run above | seconds | errors the game does not call failures: missing model/texture/sound/recipe messages, stack traces, `OutOfMemoryError`, stale classes, anything logged at ERROR, a crash report |
| Visual smoke | `./gradlew runVisualSmokeTest [-PsmokeEffects ...]` | 2-10 min | the original picture-taking runs, for a person (or Claude) to *look* at: composition, colour, whether it reads as Create. Their log is scanned too, as warnings only |

The first four are assertions. The last is evidence for a human. A green `smoke` says nothing about whether the pictures
look good; it says nothing is broken in ways a machine can name.

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

A harness that has only ever passed has proved nothing. `scripts/smoke-mutations.ps1` breaks a *copy* of the mod in twelve
known ways and lists what must turn red. The first ten were verified 2026-11-16 and the last two on 2026-11-18 (the client
set, in a private copy: every EXPECT line appeared among the failures), each caught by the check named:

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

Building this found real holes in the harness itself, all fixed: an empty tag does not look empty (vanilla swaps in a
barrier named "Empty Tag", so a naive check passes it), a crash lost every finding made before it, and a run that died
at launch was judged by the previous run's stale report. If a mutation stops being caught, a check has been weakened:
fix the check, not the list. Re-run it after changing any audit.

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
* **Both ends of every setting.** Players edit the config file; the value nobody tried is the one somebody types.
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

To re-prove the harness: make a *separate* copy, run `./scripts/smoke-mutations.ps1 -Copy <copy> -Set server|client|crash`,
run the Gradle task it names, and check every `EXPECT` line appears among the failures. It refuses to run in a folder with
`.git`.

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
* Multiplayer, other mods, other GPUs, Linux and macOS. Everything here runs one machine's dev environment, single
  player, with Create and Flywheel only.
* Behaviour over a *real* long session (hours). The fleet runs about a minute; slower leaks need a longer soak.
* A real dedicated server. The GameTest server loads the mod's common code on a server-only JVM, which catches client
  classes leaking into common code, but it does not run `runServer` with real players and the real tick loop.
* Save and reload of a whole world through the title screen. Block entities are save-and-load round-tripped, and chunks are
  unloaded and loaded, but the world is never closed and reopened from disk in one run.
