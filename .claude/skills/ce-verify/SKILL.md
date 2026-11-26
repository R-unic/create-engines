---
name: ce-verify
description: Build, test and verify Create Engines (unit tests, GameTests, runData, pitest, smoke) and diagnose a red or odd run. Use before running any Gradle task in this repo, and when a test fails, hangs, or passes suspiciously.
---

# Verify

Use `tools/ce_run.py`. It adds `--offline` and `--rerun`, keeps the full output in a log file, and prints only the verdict
and failures. Do not run `gradlew` by hand and then grep the log.

| Need | Command |
|---|---|
| One unit test class while iterating | `python tools/ce_run.py unit --tests "*WearTest"` |
| Does it compile | `python tools/ce_run.py compile` |
| All unit tests | `python tools/ce_run.py unit` |
| GameTests (shipped config, clean world) | `python tools/ce_run.py gametest --copy NAME` |
| Generated files | `python tools/ce_run.py data` |
| Mutation gate, after changing `engine/sim` | `python tools/ce_run.py pitest --copy NAME`, then `python tools/pit_survivors.py ../create-engines-NAME/build/reports/pitest/mutations.xml --lines` |
| Dedicated server from the built jar | `python tools/ce_run.py jarServer --copy NAME` |
| Everything unattended | `python tools/ce_run.py smoke --copy NAME` |
| Re-read the last results | `python tools/ce_run.py parse gametest --copy NAME` |

Python is `/c/Python315/python.exe` from Git Bash (plain `python3` there is the Store stub).

## Spend less time

- Iterate with `--tests` (one per call: a repeated `--tests` keeps only the last). Run the full unit and GameTest suites once, when the change is finished, not after each edit.
- Chain steps in one call: `unit gametest --copy NAME`.
- Anything with `gametest`, `pitest` or `smoke` goes in the background (`run_in_background: true`). You are told when it
  exits. Never wait with `sleep` or `until grep` loops.
- Use one `--copy` name for the whole session. A reused copy keeps its build cache.
- `--copy` mirrors the whole checkout, including other sessions' unfinished edits. A failure in a file you did not touch
  may be theirs: check `git diff --stat` and `ListAgents`.

## When a run is red or strange

A red guard follows CLAUDE.md: the default answer is that the code is wrong. Rule out these environment causes only with
evidence, and never rerun until it passes.

| Symptom | Cause |
|---|---|
| `NoClassDefFoundError` for our class, "Unable to delete directory build/..." | another Gradle run shares `build/`: use `--copy` |
| GameTests "never finished" | crash, or another server holds the world lock |
| `OutOfMemoryError`, hang after "All N required tests passed" | stale `run/gametest/world` (`--copy` deletes it) |
| Many unrelated config tests red | a broken config sweep left extreme values in `run/gametest/config` (`--copy` deletes it) |
| A mid-test config value reverts | the config file watcher: save the value and restore it on disk too |
| Client `NoSuchMethodError` | it was running during a rebuild: restart it |

Do not kill another session's Java process. A leftover `runServer` JVM (ports 25565/25599) shares `run/config` with a
GameTest server and rewrites the server toml in a loop, flipping `Tuning` toggles mid-test: use a private copy. A running
`runClient` does the same to a smoke run's client toml (harmless).

## Writing GameTests

- The `empty` template is 9x7x6 with a barrier ring: blocks facing outward read as covered, and long rows spill into the
  neighbouring test. Use `empty_large` (48x20x48) for anything long.
- Mock players from `makeMockServerPlayerInLevel` make Create throw "sync_edge_group may not be sent to the client"
  intermittently: do not test advancement awards with them.
- The unit test classpath has no Minecraft: keep sim, recorder and synth classes free of `net.minecraft` imports.

## In-game looks and sound

A green run says nothing about looks or sound. Short client runs, each opens a window (add `--offline`):
`runVisualSmokeTest -PsmokeScenes` (about 8 min), `-PsmokeEffects` (about 90 s, search the log for "SOUND SMOKE"),
`-PsmokeWear` (about 7 min;
  needs `wearMode = true` in that checkout's `run/config` server toml, or no damage is drawn). See `docs/SMOKE.md`. Report anything not looked at or listened to as unverified.

- `-PsmokeScenes` ends with `VisualSmokeTest.newBlocks`. A vanilla comparator's `FACING` is its input side. A Ponder speech
  bubble can hide what a step is about: check the neighbouring frames. Crop `-PsmokeEffects` screenshots with Pillow before
  judging them.
- Sound: `SoundAssetsTest`, plus `soundCheck`/`soundReport` in `VisualSmokeTest.effects` (the report lists events never
  started).
