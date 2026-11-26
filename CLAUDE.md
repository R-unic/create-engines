# Create Engine Engineer

## Work cheaply

- Build and test with `python tools/ce_run.py` (skill `ce-verify`), not raw `gradlew` plus log searching. Iterate with
  `--tests`; run GameTests, pitest and smoke in the background and wait for the notice, with no sleep loops.
- Big file: `python tools/ce_outline.py NAME`, then read only the range. Library code: `python tools/ce_lib.py`, never
  `find` in `~/.gradle` or unzipping jars. Many edits: `python tools/ce_patch.py`, not a new patch script (skill
  `ce-code-tools`).
- Release gate: skill `ce-release-gate`.

## Commit often, keep files small

- Commit each finished, verified piece of work as soon as it is done, with its tests and docs, without waiting to be asked.
  Do not let several features pile up uncommitted. Stage only your own changes (other sessions may share this checkout).
  Pushing and tagging still wait for the author.
- Do not let files grow to giant sizes. New work goes in a new file or class split by responsibility, not onto the end of a
  long one. Past about 800 lines, split before adding more. The files that are already too long are listed in
  [docs/ROADMAP-3.0.md](docs/ROADMAP-3.0.md), "Splitting the giant files".

## Releases follow the stability contract

Every release (any `vX.Y.Z` tag, patch releases included) follows [docs/STABILITY.md](docs/STABILITY.md). Not optional; hurry
is no reason to skip it.

- Every change: new tests with every feature, compatibility rules (no removed or renamed IDs, keys or saved data within a major
  version: 2.x until 3.0),
  no quiet exceptions, honest reporting of what actually ran.
- Starting a release: copy `docs/releases/TEMPLATE.md` to `docs/releases/vX.Y.Z.md`, run the Part 2 gate in a private copy
  of the checkout, fill in the results, write the Part 3 hands-on list from the changelog for the author.
- Never tag, or ask for a tag, while a box in the record is open or unexplained. The publish workflow also refuses a
  release tag without a complete, signed record.
- The author does the Part 3 boxes and signs. Never tick them on their behalf.
- Details: [docs/SMOKE.md](docs/SMOKE.md) (smoke harness), [docs/COMPATIBILITY.md](docs/COMPATIBILITY.md) (compatibility rules).

## Docs keep up with the code

Docs drift unless they move in the same change as the code. Any change to behaviour, a number, a name, a setting, a
version or a file updates every page that says it, in the same commit:

- Before finishing, search `README.md` and `docs/` (not only the obvious page) for each name, number and behaviour the
  change touched, and fix every hit. `docs/LISTING.md` (the public page and changelog) and the README's feature list
  count. Release records and dated roadmap entries are history: add to them, never rewrite them.
- Say a figure once. Other pages link to the page that owns it (the API version is in `docs/API.md`, settings in
  `docs/CONFIG.md`, wear figures in `docs/WEAR.md`) rather than copy it, so there is one place to go stale.
- Generated docs (`docs/CONFIG.md` tables, `docs/WEAR.md`) are regenerated, not hand-edited; their tests
  (`ConfigDocsTest`, `WearTableTest`, `DocsReferencesTest`, `GuideDocsTest`, `AdvancementDocsTest`) must pass.
- A doc that drifted once gets a test where one is cheap (a generated table, a version read from the code).
- A doc nobody needs any more (a finished checklist, a superseded plan, an image nothing shows) is deleted, not left to rot.

## The trailer keeps up with the mod

A significant new block or item goes in the trailer ([docs/SMOKE.md](docs/SMOKE.md), "The trailer"): a pad and a shot in
`client/ShowcaseRun`, a caption in `tools/trailer/captions.txt`, a place in the cut in `tools/trailer/edit.txt`, and a line in `src/smoke/trailer-parts.txt` naming the shot.
`TrailerCoverageTest` fails a part with no line. Mark a part `skip` only with a real reason (inside another part, a variant,
a tool, a fluid); `later` is an open debt, not a way to pass the test. Captions say only what the mod does.

Each new minor or major version's trailer is composed afresh, so it does not look like the earlier ones: its own opening,
story, shot order, camera moves, edit and captions, not the last cut with new shots slotted in. It shows off what that
version adds.

Both forms, long and short, need a tight opening and a concise outro, tuned to each ([docs/SMOKE.md](docs/SMOKE.md),
"The trailer").

## When a test, snapshot or ratchet goes red

A red guard is a question: *did I change something I did not mean to?* Default answer: the code is wrong, so fix the code.
Never make a guard green by loosening it: no wider tolerance, bigger budget, new allow-list line, deleted assertion,
skipped test or lowered ratchet. A flaky check is fixed or dropped, never given a looser bound. A behaviour-preserving
refactor (including of `EngineSimulation` and `CylinderModel`) must leave `compat/behaviour.txt` and
`compat/trajectories.txt` untouched; if they move, the refactor changed behaviour.

## Genuinely intended behaviour changes

When the change is the point (balance pass, retuned sound, redrawn model):

1. **The author decides, not me.** Within a major version (2.x until 3.0) existing builds must run the same ([docs/COMPATIBILITY.md](docs/COMPATIBILITY.md)),
   so the change goes behind a new block, item or option existing builds do not use. A change that moves existing numbers
   is for 3.0, or needs the author's explicit say-so naming that change. A red test is never that say-so.
2. **Move the guards in the same change, by hand, and show the diff.** Guards: `compat/behaviour.txt`,
   `compat/trajectories.txt` (delete and regenerate with `COMPAT_REGEN=1`, which only adds missing entries),
   `compat/manifest.txt`, and engine fixtures in `compat/world/` (frozen once a version ships; retired only at a major version).
   Say in the commit and changelog what moved and by how much, from the real diff.
3. **Record it.** List the change under Exceptions in `docs/releases/vX.Y.Z.md`, with who approved it.
4. **Ratchets only move by hand, for a reason.** Coverage floor (`src/smoke/coverage-floor.txt`) and mutation limit
   (`src/smoke/mutation-ratchet.txt`, from `./gradlew pitest`) change only after measuring. Improvements are written in
   (floor up, survivor limit down). Moving them the other way is allowed only when the change deleted tested code, and the
   commit says so. Re-run `pitest` after changing `engine/sim`.
5. **New behaviour gets new tests**, and new guard entries are appended, never edited.
