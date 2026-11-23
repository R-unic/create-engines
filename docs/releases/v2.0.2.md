# Release vX.Y.Z sign-off

Copy this file to `docs/releases/vX.Y.Z.md` and fill it in. Rules are in [STABILITY.md](../STABILITY.md).
The publish workflow refuses a release tag unless this file exists, has no unticked `[ ]` box left, and is signed.
Tick a box with `[x]`. Replace a box that does not apply with `n/a` and a reason. A known exception goes in the
Exceptions section with a reason; it is never left as an unticked box.

Date:
Commit:
Previous release: vX.Y.Z
Built from private copy at:

## Part 2: machine gate (Claude)

- [ ] Unit tests: N passed
- [ ] GameTests: "All N required tests passed"
- [ ] Smoke audit: FAIL 0, WARN n (each listed under Exceptions or documented in the check), SKIP n (each explained)
- [ ] Log check clean (or exceptions listed)
- [ ] Mutation script re-run, or not needed because no audit changed (say which):
- [ ] Metrics copied and compared with the previous release:
      frame p50/p99 ms ___ / ___, heap MB ___, direct MB ___, 16-engine ms/tick ___ (better / same / worse, explained:)
- [ ] Previous release's fixture loads; this release's fixture added
- [ ] Manifest and baseline: N added, 0 changed, 0 removed
- [ ] Config from the previous version loads; every value at min and at max runs
- [ ] Create range tested (oldest ___, newest ___) and matches the jar metadata
- [ ] `runData` leaves no diff in `src/generated`
- [ ] Jar built, named correctly, version/changelog/tag agree, no development leftovers
- [ ] Dedicated server booted from the jar
- [ ] Soak: not yet automated (known exception) / 60-minute soak result: ___
- [ ] Changelog written from the real diff; every changed behaviour and new ID/key named
- [ ] Diff review done (tick/sound/render allocation, client classes in common code, saved data without defaults)
- [ ] No TODO, FIXME or debug code left; docs match the release

## Part 3: hands-on (author, target 10 minutes; actual ___ min)

- [ ] 3.1 Crafted the parts, small engine, large boosted engine, break and replace, new feature from its Ponder page
- [ ] 3.2 Sound, walk away and back, new blocks looked at, new Ponder pages played
- [ ] 3.3 Old world from the previous release opened and reloaded
- [ ] 3.4 If touched: fuel chain / server and second client / addons (each ticked or `n/a`):
- [ ] 3.5 New items, one line each (Claude writes these from the changelog):
  -

## Exceptions

None. (Or each exception with a reason.)

## Signed off

By:
