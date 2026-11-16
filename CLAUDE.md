# Create Engine Engineer

## Releases follow the stability contract

Every release (any `vX.Y.Z` tag, patch releases included) follows [docs/STABILITY.md](docs/STABILITY.md). It is not
optional and a hurry is not a reason to skip it.

- Every change: new tests with every feature, compatibility rules (no removed or renamed IDs, keys or saved data in 1.x),
  no quiet exceptions, honest reporting of what actually ran.
- Starting a release: copy `docs/releases/TEMPLATE.md` to `docs/releases/vX.Y.Z.md`, run the Part 2 gate in a private copy
  of the checkout, fill in the results, write the Part 3 hands-on list from the changelog for the author.
- Never tag, or ask for a tag, while a box in the record is open or unexplained. The publish workflow also refuses a
  release tag without a complete, signed record.
- The author does the Part 3 boxes and signs. Do not tick them on their behalf.
- Smoke harness details are in [docs/SMOKE.md](docs/SMOKE.md); compatibility rules in
  [docs/COMPATIBILITY.md](docs/COMPATIBILITY.md).
