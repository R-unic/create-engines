# Stability contract

Every release follows this. It exists because 1.2.0 shipped a sound-thread out-of-memory bug that took FPS to zero near
engines and killed Minecraft's sound until reload. Nothing in the contract is new work for its own sake: each line is
there because something got through, or could.

The contract has four parts, in the order they happen:

1. **Every change** (done by Claude, unasked, as the code is written).
2. **Release gate** (done by Claude and the machine, before a tag).
3. **Hands-on pass** (done by the author, in a real game, before a tag).
4. **Ship and watch** (the tag and the days after).

A release is not tagged until every box in parts 2 and 3 is ticked in the sign-off record (end of this file), and any
box that cannot be ticked is written down as an exception with a reason. An unticked box with no reason is a blocked
release.

Related documents: [COMPATIBILITY.md](COMPATIBILITY.md) (the old-world and old-factory rules), [SMOKE.md](SMOKE.md) (the
machine layers), [ROADMAP.md](ROADMAP.md) (the rules every release keeps, and the queued verification track).

---

## Part 1. Every change (Claude, automatic)

These apply to every commit, not just at release time. The author does not need to ask for them.

### 1.1 Tests come with the feature

- New simulation behaviour gets **unit tests**. New world behaviour gets **GameTests**. Written in the same change, not
  after.
- Anything that runs on a game-owned thread (sound, render) or every tick gets **soak tests** (thousands of iterations,
  no per-call allocation, buffers reused), **hostile-input tests** (NaN, infinity, zero, huge, empty, wrong type) and a
  **recovery check** (it keeps working after bad input). Such logic stays pure Java so the unit tests can run it.
- A new failure class we were bitten by gets the check that would have caught it **first**, in a layer that can fail the
  build, with a unit test that the check fires and a line in `scripts/smoke-mutations.ps1`. Then the fix.
- An advancement is added only where it marks something meaningful, never one per feature.

### 1.2 Old worlds and old factories are protected

- No registry ID, block state property, NBT key, config key, recipe result, advancement ID or data-map format is renamed
  or removed within a major version (1.x, now 2.x until 3.0). New saved keys have a default that reproduces the old behaviour when the key is missing.
- Anything that changes how an existing engine behaves is a behaviour change, not a fix. Within a major version it goes behind a
  new block, item or config option that existing builds do not use, or it waits for the next major. If a fix truly must
  move numbers, stop and ask the author first; their approval names the change and is recorded as an exception.
- New blocks, items and saved keys are appended to `compat/manifest.txt` in the same commit
  (`COMPAT_REGEN=1 ./gradlew test --offline`). Existing lines are never edited or deleted.
- A failing compatibility test is a question ("does this break an old world?"), answered by changing the code, never
  the test.

### 1.3 Every feature ships whole

- Lang entries; a Ponder page and goggle lines where there is something to explain or read; tooltips.
- A config setting goes through `Tuning` with a default that leaves the existing tests unchanged, and is tested at both
  ends of its range.
- It looks like Create: chunky models, textures built from Create's own.

### 1.4 No quiet exceptions

- A new smoke `WARN` is either fixed or turned into a documented exception in the check, with a reason. It is never left
  to be ignored.
- A new entry in `src/smoke/log-allowlist.txt` states why. A log line nobody can explain is a bug.
- A test that fails intermittently is fixed or deleted. It is never retried until it passes, and its tolerance is never
  loosened to make it pass.
- GameTests are run at the **shipped default config**, in a private copy of the checkout (see 2.1), not against whatever
  `run/config` holds.

### 1.5 Honest reporting

- Claude reports what ran and what did not. "Compiled" is not "tested", "tests passed" is not "works in game", and a
  green `smoke` is not "looks right". A check that was skipped is said to be skipped.
- `runGameTestServer` counts only if the log says "All N required tests passed". Exit code 0 is not enough (another
  session's server can hold the world lock and the task still succeeds).

---

## Part 2. Release gate (Claude and the machine)

Run from a **private copy** of the checkout, so no other Gradle run or development client shares `run/` and `build/`:

```
robocopy <checkout> <copy> /MIR /XD run build .gradle .git
cd <copy>
./gradlew smoke --offline --continue > smoke.log 2>&1
```

### 2.1 Machine checks (all must be green, no unexplained WARN)

- [ ] `./gradlew test --offline`: all unit tests, including `CompatManifestTest` and `BehaviourBaselineTest`.
- [ ] `./gradlew runGameTestServer --offline`: "All N required tests passed", including `LegacyWorldGameTests`, the
      lifecycle sweep, the 16-engine fleet and every tuning setting at both ends.
- [ ] `./gradlew runSmokeAudit --offline`: every model, sprite, translation, sound and particle; the whole Ponder sweep;
      the fleet run with heap, direct memory, voices and frame-time trends; the walk-away-and-back.
- [ ] Log check: no ERROR, no stack trace, no `OutOfMemoryError`, no missing model, texture, sound or recipe message,
      no crash report, nothing not on the allowlist.
- [ ] `./gradlew pitest --offline`: live mutants (survived plus no coverage) within `src/smoke/mutation-ratchet.txt`. The
      result may come from PIT's incremental history: mutants in classes whose code and covering tests are unchanged
      since the history was written keep their earlier result. The record states how many results were reused, or that
      the run was fresh. Delete the history file whenever pitest's settings, its test exclusions or the PIT version change.
      `partEffectsSwappedFromAnotherThreadNeverBreaksAnEngine` is left out of pitest, because it kills a few mutants
      only by luck and made the count swing by about 3. It still runs as a unit test. Both rules approved by the author
      on 2026-11-25.
- [ ] `./scripts/smoke-mutations.ps1` re-run if any audit or checker changed since the last release. Every `EXPECT` line
      must still turn red.
- [ ] Every WARN in the reports is read. Each is fixed, or is already a documented exception. (The supercharged spark
      engine after a 3000-block walk was fixed 2026-11-16 and is a hard check in the fleet.)
- [ ] **Metrics compared.** The `METRICS` block (frame time p50 and p99, heap after GC, direct memory, particles, cost of
      16 engines) is compared to the last release's. A worse number is explained or fixed. The numbers are copied into
      the sign-off record so the next release has something to compare to.

### 2.2 Compatibility checks

- [ ] A fixture saved by the **previous shipped version** (`engine_1_N.snbt` and any world saves in the corpus) loads in
      this build with every saved key intact. The fixture for the version being released is added now and never
      regenerated afterwards.
- [ ] The manifest and behaviour baseline diffs for this release contain **additions only**. Any changed or removed line
      is explained in the changelog or reverted.
- [ ] Config: a config file written by the previous version loads with no error and no changed behaviour. A file with
      every value at its minimum, and at its maximum, loads and runs (no crash, no hang, no NaN).
- [ ] Supported Create range: built and tested against the newest Create for Minecraft 1.21.1 and the oldest the
      version range claims. The range in the jar metadata matches what was tested.

### 2.3 Build and packaging checks

- [ ] `./gradlew runData --offline` leaves `git diff src/generated` empty (generated files match the code that makes
      them, and none are half-committed). Delete `src/generated` in the private copy first: datagen skips a file whose
      output matches its `.cache` entry, so a plain re-run cannot show a stale file (1.3.0's did not, and CI's
      `datagen-idempotence` job, which starts from a fresh checkout, caught it).
- [ ] `./gradlew build --offline` produces `create_engines-VERSION-MCVERSION.jar`. The version in `gradle.properties`,
      the jar name, the changelog heading and the tag all agree.
- [ ] **The jar, not the dev classpath, is what gets run.** `./gradlew jarServer --offline` boots a dedicated server from
      the built jar twice on the supported NeoForge and Create ([SMOKE.md](SMOKE.md), "The jar server"). The client is
      started from the same jar by the author in 3.1. A dev classpath hides
      client classes loaded on a server and resources missing from the jar.
- [ ] The jar contains no development leftovers (no `run/` data, no test classes, no scratch files) and nothing
      unexpectedly large.

### 2.4 (removed)

The 60-minute unattended soak was dropped by the author on 2026-11-25. It was never built. The one-minute fleet run in 2.1 is
the leak check. The number is kept so that older release records still point somewhere.

### 2.5 Review checks

- [ ] The changelog is written from the **real diff** (`git diff <last tag>..HEAD`, including data and lang), not from
      commit names. Every changed existing behaviour is named. Every new ID, config key and file format is listed
      (they join the compatibility promise the day the release ships).
- [ ] A diff review for the usual suspects: anything allocating on a tick, sound or render path; anything iterating all
      loaded blocks; anything calling into client classes from common code; saved data read without a default.
- [ ] No open `TODO`, `FIXME`, debug logging or debug keybind left in the shipped code.
- [ ] Docs match the release: every page under `docs/` and the README that the diff touches says what the code now does
      (search the docs for each changed name, number and behaviour, not only the pages listed here); `docs/SMOKE.md`
      numbers (tests, storyboards, mutations), `docs/ADVANCEMENTS.md`, `docs/COMPATIBILITY.md` known gaps, the README's
      feature list and versions, and this file's checklist if the process changed.

### 2.6 Sound bench (only when the release changes the sound system somewhat significantly)

Decided by the diff since the last tag. It applies when the release changes how sound is made, delivered or mixed: the engine
note's synthesis, the streams and their threading or timing, the voice budget, the shared room voice, walls, rooms, air or
moving structures, when engine sounds start, follow or stop, or a reworking of many sounds or their levels at once. It does
not apply to a small, contained change, such as one new or replaced one-shot, one sound's volume, subtitles, or a refactor
away from the sound code. When in doubt, Claude says what changed and asks the author. If it does not apply, the record
says `n/a` and names the sound changes there were, or says there were none.

- [ ] The sound bench ([SMOKE.md](SMOKE.md), "The sound bench") run on the release's code, every scene, plus `--moving` if
      the change can affect a moving structure. The report is trusted (no "Do not trust this run"), every item under
      **Faults found** is fixed or listed under Exceptions, and its figures are compared with the last bench run
      (`--compare`), a worse one explained or fixed. It needs the author's machine with its speakers on and nothing else
      playing, so Claude agrees a time with the author before starting it.

---

## Part 3. Hands-on pass (the author, about 10 minutes)

Only what a machine cannot judge, or what the machine cannot yet do. Run from the **built jar** in a **clean instance**
(supported Create, Flywheel and NeoForge only), at the shipped default config. Each item has a pass condition; "looks
fine" is not one. Anything odd is written down even if it does not look like a bug.

Churn and hostile-play runs are **not** done by hand. They run in the Part 2 gate, so
the author's time goes on seeing and hearing.

### 3.1 Build an engine (every release, about 4 minutes)

Fresh world, no prior settings.

- [ ] Craft the parts for one engine from the recipe book alone. Every recipe resolves and the names read sensibly.
- [ ] Build a **small engine**, fuel it, start it. Pass: it runs a Create mill or drill, goggles read sensibly, and it
      stops cleanly when the fuel runs out.
- [ ] Build **one large boosted engine**. Pass: starts, reaches its rated output, holds under load, no lag spike.
- [ ] Break each part with a wrench and replace it. Pass: nothing lost, nothing keeps running as a ghost.
- [ ] Use **this release's new feature** the way a player would, starting from its Ponder page.

### 3.2 Sound and look (every release, about 3 minutes)

- [ ] Listen to idle, load, redline, boost and shutdown on the engine just built. Pass: no clicks, no silence where
      there should be sound, no sound left behind after it stops.
- [ ] Walk 100 blocks away and back. Pass: sound fades and returns, the engine is still running.
- [ ] Look at each new or changed block from all sides, by day and in the dark. Pass: no pink, no missing faces, no
      z-fighting, reads as Create.
- [ ] Open and play to the end every Ponder page this release added or changed.

### 3.3 Old world (every release, about 2 minutes)

- [ ] Open **one real world saved on the previous release**, with something running in it. Pass: loads with no error,
      the factory is intact, and goggle output matches what it showed before the update.
- [ ] Quit to the title screen and reload. Pass: engines come back as left, or cleanly stopped, never frozen.

### 3.4 Only when the release touches it (about 1 minute each, skip otherwise)

- [ ] **Fuel, refining or piping changed:** run crude through the real chain to a running engine; a wrong fuel gives the
      documented consequence, not a crash.
- [ ] **Networking, screens or sync changed:** join a dedicated server from the jar with a second client (or the same
      machine twice) and check goggle readouts and Display Link agree on both.
- [ ] **A supported addon or Sodium is listed:** start with it installed, engines running, and see no error.

### 3.5 New items for this release

Claude writes one hands-on line per new block, behaviour or setting that a machine could not assert (for example, "the
fouled-plug sound under boost"), taken from the changelog and added to the sign-off record. If something new needs no
hands-on line, the record says why. If this list would push the pass past about 15 minutes, that is a sign the release is
too big or a check is missing, and Claude says so.

---

## Part 4. Ship and watch

### 4.1 Shipping

- [ ] The author merges the PR (it is the author's merge, not an agent's).
- [ ] Tag `vX.Y.Z` on the merged commit. The tag runs `publish.yml` (Modrinth and CurseForge) and `gallery.yml`.
- [ ] Confirm the workflow succeeded on **both** sites. **Do not rerun a partial success**: `fail-mode: skip` would upload
      twice to the site that worked.
- [ ] `gh release create` with the jar from the run's artifacts and the changelog section.
- [ ] CurseForge gallery images replaced by hand if the screenshots changed. CurseForge holds new files for review, so
      a file not yet public is not a failed upload.
- [ ] Download the **published** jar from the site and check it is the file that was tested (same size, same hash as
      the build artifact), and boot it once.

### 4.2 Watching

- [ ] For the first week, read new comments and issues on both sites and GitHub. A report of a crash, a lag spike, a
      sound failure or a changed factory is treated as a regression until shown otherwise.
- [ ] A reported regression gets, in this order: a failing test that reproduces it, then the fix, then a patch release
      that follows this same contract. If the cause is a gap in the harness, the harness is fixed too (rule 1.1, "a new
      failure class").
- [ ] If a release breaks old worlds or factories and a fix will not land the same day, say so on the listing and
      advise players to hold on the previous version.

### 4.3 Patch releases

A patch release (`X.Y.Z` with Z above 0) follows the whole contract. The only permitted shortcut is in Part 3: only the items its
diff could affect need a hands-on run, plus 3.3 (old world) and any item a reported bug touches. Parts 1 and 2 are
never shortened, and a hotfix cannot skip the old-world check.

---

## What this contract cannot promise

Said plainly so nobody thinks a tick in a box means more than it does.

- Other GPUs, drivers, operating systems, Java versions and mod combinations beyond the supported rows were not run.
- The one-minute fleet run does not find a slow leak. Players will run bigger and longer than we do.
- Sound that is technically correct can still be unpleasant. Only the hands-on pass catches that.
- Ten minutes by hand cannot find what needs a long session or a second player. Those are only partly covered, by the
  GameTests and the server boot, which only see what they were written to see.
- A green gate means nothing is broken in ways we know how to look for. The 1.2.0 bug was one nobody knew to look for.
  This is why every escaped bug adds a check (rule 1.1).

## Items that depend on the verification track

Some gate items above are done by hand today because the automation is only queued in the roadmap's verification track.
When one lands, move its item out of Part 3 into Part 2 so it ticks automatically and the hands-on pass gets shorter:

| Today | Automated by (roadmap item) |
|---|---|
| A second client joining that server (3.4) | not automated: `jarServer` (item 5) boots the server but nothing joins it |
| Old-world check with real saves (3.3) | 4, world corpus |
| Config at min and max (2.2) | 9, config matrix |
| Supported Create and addon rows (2.2, 3.4) | 10, compatibility matrix |
| Datagen has no diff (2.3) | 11, datagen idempotence |
| Metrics compared by eye (2.1) | 7, performance budgets |
| Hostile play and churn | 3, monkey GameTest |
| Network or sound-thread races | 6, concurrency tests; 8, network and codec fuzz |
| Test quality (1.1) | 1, mutation testing; 2, property tests |

---

## Sign-off record and enforcement

Each release has a signed record at `docs/releases/vX.Y.Z.md`, copied from [releases/TEMPLATE.md](releases/TEMPLATE.md).
It is committed before the tag, so the record is part of what was released.

How the contract is kept to:

- **The publish workflow refuses a release tag** (`vX.Y.Z`, not alpha, beta or rc) unless that file exists, has no
  unticked `[ ]` box, and has a name under "Signed off". A skipped box is either ticked, marked `n/a` with a reason, or
  listed under Exceptions with a reason. Prototype and pre-release builds are not gated.
- **Claude starts every release by copying the template**, works down Part 2, writes the Part 3 list for the author from
  the changelog, and does not tag, or ask the author to merge for a tag, with boxes open. The project's `CLAUDE.md`
  tells every session this.
- **The author signs.** Claude fills in the machine results; the Part 3 boxes and the "Signed off" name are the
  author's.
- **Changing the contract is a reviewed change.** A new escaped bug adds a check (rule 1.1) and, if it needs one, a box
  in the template, in the same change.
