# Smoke testing

The smoke harness exists to answer one question without a person looking: **is this build fit to ship?** It is layered,
because each layer catches a different kind of failure, and none of them is trusted on its own say-so: every layer
ends in a check that can fail the build.

```
./gradlew smoke --offline          # everything below, in order; fails if any layer found a problem
```

Run it in a private copy of the checkout (see "Running it" below): other Gradle runs and a development client share
`run/` and `build/` and will corrupt each other's results.

## What each layer catches

| Layer | Command | Time | Catches |
|---|---|---|---|
| Unit tests | `./gradlew test` | seconds | simulation maths, parsers, sound synthesis, **the harness's own logic** |
| GameTests | `./gradlew runGameTestServer verifyGameTests` | ~1 min | engine behaviour, **every block/item/fluid/recipe/loot/advancement complete, every block entity placed-ticked-saved-loaded-garbled-broken in every sampled state, 16 engines at once under a tick budget, a running engine's chunk unloading and loading again, every tuning setting at both ends of its range** |
| Client audit | `./gradlew runSmokeAudit` | ~4 min | **every block state's model and sprites, every item model, every translation, sound, particle sheet and Flywheel partial; every Ponder page played to its end; a fleet of engines run for a minute with heap, direct memory, particles, sound voices and frame times tracked; walking away from the engines and back; screenshots judged by pixels** |
| Log check | automatic after each run | seconds | errors the game does not call failures: missing model/texture/sound/recipe messages, stack traces, `OutOfMemoryError`, stale classes, anything logged at ERROR |
| Visual smoke | `./gradlew runVisualSmokeTest [-PsmokeEffects ...]` | 2-10 min | the original picture-taking runs, for a person (or Claude) to *look* at: composition, colour, whether it reads as Create |

The first four are assertions. The last is evidence for a human. A green `smoke` says nothing about whether the
pictures look good; it says nothing is broken in ways a machine can name.

## Reading a result

`runSmokeAudit` writes `run/smoke-client/smoke/client/report.txt`; the verify step prints the merged report
(game checks plus log scan) and exits non-zero on any `FAIL`. The report has a table by category, every `FAIL`,
every `WARN`, and a `METRICS` block (frame times, heap, particles, ticks per engine) to compare between runs.
GameTest reports land in `run/gametest/smoke/gametest-<name>/report.txt`.

* **FAIL** fails the build. It means something is wrong or the harness cannot tell.
* **WARN** does not. It means probably wrong but possibly deliberate (a block with no mining tag, a save that is not
  byte-stable across a load). Read them; a WARN you decide is fine should become a documented exception in the check,
  not a habit of ignoring warnings.
* **SKIP** means a check could not run (and says why). A skipped check proves nothing.
* A run with no `result.properties`, or `completed=false`, fails: a crash, a hang and a client that never reached the
  world all look like that.

## Why these methods

* **Exact checks over pictures.** A missing texture is found by walking every quad of every baked model and comparing
  its sprite to the missing-texture sprite, per block state. That cannot be missed the way a magenta corner of a
  screenshot can. The pixel checks (`PixelChecks`) are a backstop for what the model walk cannot see, not the main tool.
* **Trends over snapshots.** The 1.2.0 sound crash was a slow leak of direct memory. No screenshot shows that; a series
  of `BufferPoolMXBean` readings with a climbing fit does (`PerfSampler.climbing`). Heap is sampled after a GC so
  garbage is not mistaken for a leak; frame times are judged by percentiles because a stutter lives in p99, not the mean.
* **Hostile input.** Block entities are loaded from empty tags, from tags whose every value is the wrong type, and from
  tags with unexpected keys, because a world saved by another version or edited by a person looks like that.
* **Both ends of every setting.** Players edit the config file; the value nobody tried is the one somebody types.
* **Lifecycle, not just life.** Engines are built, run, unloaded with their chunk, reloaded, walked away from and
  returned to. Bugs cluster at those transitions.
* **The harness is tested.** `LogScannerTest`, `PixelChecksTest`, `PerfSamplerTest` and `SmokeReportAndVerifyTest`
  prove the checkers flag what they should and stay quiet about what they should. An unchecked checker is how a
  harness ends up green while the build is broken. Known weak spot: see "What it cannot see".

## Running it

```
# in a private copy, so nothing else shares its run/ and build/ folders
robocopy <checkout> <copy> /MIR /XD run build .gradle .git
cd <copy>
./gradlew smoke --offline > smoke.log 2>&1
```

Individually: `./gradlew test`, `./gradlew runGameTestServer` (verified automatically), `./gradlew runSmokeAudit`
(verified automatically). A task that fails verification names the failing checks; the full report is on disk.

## The log allowlist

`src/smoke/log-allowlist.txt` lists log lines that are known harmless. **Every entry must say why.** A line you cannot
explain is a bug. Review the list whenever Create, NeoForge or Minecraft is updated and delete entries whose reason
no longer holds.

## Adding to it

* New block, item, fluid, recipe, sound or particle: nothing to do. The audits walk the registries, so new content is
  covered the moment it registers; a gap (no loot table, no translation, no item model) fails on its own.
* New block entity: it is swept automatically. If its save is legitimately not stable across a load, add the changing
  key to `ServerAudit.VOLATILE_KEYS` with a reason.
* New engine behaviour worth guarding: add a GameTest as before; add a scenario to `SmokeAuditGameTests` only if it
  is about scale, time or lifecycle rather than one rule.
* A new class of failure you were bitten by: write the check that would have caught it first, in a layer that can
  fail the build, and add a unit test that the check fires. Then fix the bug.

## What it cannot see

* Whether anything *looks right* or reads as Create. Pictures are kept in `run/smoke-client/smoke/client/shots/` for
  a human; the pixel checks only catch the broken, the blank and the frozen.
* Audio quality. The audit proves every sound resolves, starts, and does not leak voices; not that it sounds good.
* Multiplayer, other mods, other GPUs, Linux and macOS. Everything here runs one machine's dev environment, single
  player, with Create and Flywheel only.
* Behaviour under a *real* long session (hours). The fleet runs about a minute; slower leaks need a longer soak.
