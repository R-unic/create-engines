# Create Engine Engineer

## Releases follow the stability contract

Every release (any `vX.Y.Z` tag, patch releases included) follows [docs/STABILITY.md](docs/STABILITY.md). Not optional; hurry
is no reason to skip it.

- Every change: new tests with every feature, compatibility rules (no removed or renamed IDs, keys or saved data in 1.x),
  no quiet exceptions, honest reporting of what actually ran.
- Starting a release: copy `docs/releases/TEMPLATE.md` to `docs/releases/vX.Y.Z.md`, run the Part 2 gate in a private copy
  of the checkout, fill in the results, write the Part 3 hands-on list from the changelog for the author.
- Never tag, or ask for a tag, while a box in the record is open or unexplained. The publish workflow also refuses a
  release tag without a complete, signed record.
- The author does the Part 3 boxes and signs. Never tick them on their behalf.
- Details: [docs/SMOKE.md](docs/SMOKE.md) (smoke harness), [docs/COMPATIBILITY.md](docs/COMPATIBILITY.md) (compatibility rules).

## The trailer keeps up with the mod

A significant new block or item goes in the trailer ([docs/SMOKE.md](docs/SMOKE.md), "The trailer"): a pad and a shot in
`client/ShowcaseRun`, a caption in `tools/trailer/captions.txt`, and a line in `src/smoke/trailer-parts.txt` naming the shot.
`TrailerCoverageTest` fails a part with no line. Mark a part `skip` only with a real reason (inside another part, a variant,
a tool, a fluid); `later` is an open debt, not a way to pass the test. Captions say only what the mod does.

## When a test, snapshot or ratchet goes red

A red guard is a question: *did I change something I did not mean to?* Default answer: the code is wrong, so fix the code.
Never make a guard green by loosening it: no wider tolerance, bigger budget, new allow-list line, deleted assertion,
skipped test or lowered ratchet. A flaky check is fixed or dropped, never given a looser bound. A behaviour-preserving
refactor (including of `EngineSimulation` and `CylinderModel`) must leave `compat/behaviour.txt` and
`compat/trajectories.txt` untouched; if they move, the refactor changed behaviour.

## Genuinely intended behaviour changes

When the change is the point (balance pass, retuned sound, redrawn model):

1. **The author decides, not me.** In 1.x existing builds must run the same ([docs/COMPATIBILITY.md](docs/COMPATIBILITY.md)),
   so the change goes behind a new block, item or option existing builds do not use. A change that moves existing numbers
   is for 2.0, or needs the author's explicit say-so naming that change. A red test is never that say-so.
2. **Move the guards in the same change, by hand, and show the diff.** Guards: `compat/behaviour.txt`,
   `compat/trajectories.txt` (delete and regenerate with `COMPAT_REGEN=1`, which only adds missing entries),
   `compat/manifest.txt`, engine fixtures in `compat/world/` (frozen once a version ships; retired only at 2.0), and the
   goldens below. Say in the commit and changelog what moved and by how much, from the real diff.
3. **Record it.** List the change under Exceptions in `docs/releases/vX.Y.Z.md`, with who approved it.
4. **Ratchets only move by hand, for a reason.** Coverage floor (`src/smoke/coverage-floor.txt`) and mutation limit
   (`src/smoke/mutation-ratchet.txt`, from `./gradlew pitest`) change only after measuring. Improvements are written in
   (floor up, survivor limit down). Moving them the other way is allowed only when the change deleted tested code, and the
   commit says so. Re-run `pitest` after changing `engine/sim`.
5. **New behaviour gets new tests**, and new guard entries are appended, never edited.

## Goldens (item 13 of the verification track, not built yet)

Visual and audio goldens, once built, follow the same rules. A diff nobody meant is a bug. A diff somebody meant is updated
with `./gradlew updateGoldens`; the git diff of images and numbers is the review and the commit is the approval. Any
deliberate art or sound change updates its goldens in the same change. Never widen a tolerance to pass; a flaky capture is
fixed or dropped. When built, put the exact commands in [docs/SMOKE.md](docs/SMOKE.md) and keep this section true.
