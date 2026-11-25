# Brief: make the machine gate faster

Throwaway handoff doc. Do not commit it.

## Goal

`python tools/ce_run.py gate --copy NAME` takes about **38 min**. Target: **about 15 min or less**, with the same tests and
the same guard numbers. Follow CLAUDE.md throughout. In particular: never make a guard pass by loosening it, and report
only what actually ran, with numbers.

## Where the time goes (2.3.0 gate, logs in `../create-engines-gate230-logs`, `../create-engines-gate230-r3`)

| Step | Time | Notes |
|---|---|---|
| pitest | 16.4 min | 2,736 mutants, `--threads 4`, `--timeoutConst 10000`. 26 TIMED_OUT, 11 MEMORY_ERROR, 11 RUN_ERROR; each restarts a JVM. Last count: killed 2595, survived 125, no coverage 1, limit 129. |
| smoke | 6.2 min | Unit tests (~30 s), the main GameTest server (611 tests), the client audit, coverage |
| Pollution, Sable, TFMG, Energy rows | 3.3-4.4 min each | Each re-runs all 611 GameTests on its own server: ~25 s startup, ~167 s of tests |
| runData, check, build | ~0.6 min | Already cheap; leave them alone |

`defaultBatch:5` takes **50 s** because one slow test holds up 49 others. That happens on every server, five times per
gate. `smoke-config:0` and `smoke-tuning:0` are single-test batches of about 10 s each.

Machine: 6 cores, 16 GB RAM. Each Gradle daemon is `-Xmx3G`. Two lanes side by side is the realistic limit.

## Decisions (made by the author, 2026-11-25; do not reopen them)

1. **Compatibility rows:** keep all four, each still running all 611 tests. Gain time only by running them in parallel lanes.
2. **pitest result reuse:** use PIT incremental history (`--historyInputLocation` / `--historyOutputLocation`).
3. **The luck test:** exclude `partEffectsSwappedFromAnotherThreadNeverBreaksAnEngine` from pitest. It still runs as a
   normal unit test.
4. **pitest tuning:** thread count and timeout may change only if **both** hold:
   - killed, survived and no-coverage counts match the reference run exactly, across two runs
   - the run is measurably faster

   Otherwise keep the current values.

## Work, in order

Work on a new branch in its own git worktree. Do not touch the `release-2.3.0` checkout or its release record. Measure
each step against the timing table from step 1.

1. **Timing table.** Make `ce_run.py gate` print a final table: time per step, and for each GameTest step, server startup
   vs tests and the 5 slowest batches, parsed from `latest.log` ("Running test batch" lines and the "All N required
   tests passed" line).
   *Done when* one gate run prints it.
2. **The 50 s straggler.** Find the slow test in `defaultBatch:5` and give it its own batch, so the other 49 stop waiting
   for it. Also move the 10 s single-test batches (`smoke-config`, `smoke-tuning`) so they run alongside other tests.
   Do not shorten or weaken any test. Batch names are not saved data, but check that nothing relies on them:
   `SmokeVerify`, `src/smoke/log-allowlist.txt`, and the docs.
   *Done when* all 611 tests still pass on every row and the batch time per server drops by about 40 s.
3. **pitest, reference first.** In a private copy, run pitest twice as it is today, minus the luck test (decision 3).
   Write both sets of counts down: this is the reference.
   - If live mutants (survived + no coverage) exceed 129, **stop and ask the author**. Do not raise
     `src/smoke/mutation-ratchet.txt`.
   - If the count is lower, lower the ratchet by hand in the same commit, citing both runs.
4. **pitest, the crashes.** Find the 26 TIMED_OUT and 22 MEMORY_ERROR / RUN_ERROR mutants (see `mutations.xml` and the
   minion warnings in the log) and fix the cause in the tests, the way `SharedStateConcurrencyTest` was fixed
   (memory `project-mutation-gate`). Never cap PIT's heap: it hides about 25 mutants.
   *Done when* the counts equal the reference and there are fewer minion restarts.
5. **pitest, tuning (decision 4).**
   - Try `--threads 6`, then a smaller `--timeoutConst` with PIT's `--timeoutFactor`.
   - Keep a setting only if two runs match the reference counts exactly and are faster. A shorter timeout can wrongly
     count a slow survivor as killed, so an exact match is required.
   - Record the times and counts for every setting you tried, including the ones you dropped.
6. **pitest, incremental history (decision 2).**
   - Keep the history file outside the mirrored copies, so it survives between gates (for example
     `../create-engines-pit-history/history.bin`). Pass it in only for the gate.
   - Add `ce_run.py gate --fresh-pit`, which ignores the history and does a full run.
   - Print how many results were reused (`PIT: ... reused N`) so the record can say so.
   - Check it: a run with no changes reuses everything and gives the same counts. Then change one `engine/sim` class and
     check that only that class's mutants run again.
   - The rule is already written (STABILITY.md 2.1 pitest box, and the pitest line in `docs/releases/TEMPLATE.md`).
     Make the code match it: delete the history file whenever pitest's settings, its test exclusions or the PIT version
     change. A hash of those, stored next to the file, can do this automatically.
7. **Two lanes.** In `ce_run.py gate`, run two copies at once:
   - Lane A (`--copy NAME`): smoke, check, Pollution, Sable, build, data
   - Lane B (`NAME-b`): pitest, TFMG, Energy

   Measure peak RAM first. If 16 GB is not enough, move a row between lanes rather than lowering any heap.

   Keep these unchanged:
   - one log per step
   - the PASS/FAIL line per step
   - the final `RESULT` line, with the same step names
   - a failed step does not stop the later ones

   runData still compares against the checkout from an emptied `src/generated`.
8. **Optional:** one Gradle invocation per lane with `--continue`, instead of one per step. Do it only if per-step logs
   and verdicts survive. Skip it if it gets messy.

9. **Docs that describe the code.** The policy docs are done; do not change them. With the code, update the docs that
   describe how the gate runs:
   - `.claude/skills/ce-release-gate/SKILL.md`: lanes, `--fresh-pit`, the timing table
   - `tools/ce_run.py` docstring
   - `docs/SMOKE.md`: a short pitest paragraph under "Checks that run without a game" covering the history file, the
     excluded test and the batch change
   - the GameTest count or batch names, if any doc quotes them

## Final check

Run the full gate twice on the branch: once with `--fresh-pit` and once using the history. Report wall time, the timing
table, every row's GameTest count, and pitest counts with the number reused. Compare everything against the 2.3.0 numbers
above. Leave `compat/behaviour.txt` and `compat/trajectories.txt` untouched. Changing either one means something went
wrong.

## Things to know

- `../create-engines-gate230*` folders are old gate copies: read their logs, do not mirror over them.
- A forced full unit run needs `test --rerun --no-build-cache`.
- `DocsReferencesTest` can fail in a private copy because of other sessions' test names. That is not a fault of this work.
- Run long tasks in the background and wait for the notice; do not poll with sleep.
