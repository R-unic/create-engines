---
name: ce-release-gate
description: Run the Create Engines release gate and fill in the sign-off record. Use when preparing, gating or checking a release (any vX.Y.Z), per docs/STABILITY.md.
---

# Release gate

`docs/STABILITY.md` is the authority. This is the order of work; if the two differ, follow STABILITY.md and fix this file.

1. Copy `docs/releases/TEMPLATE.md` to `docs/releases/vX.Y.Z.md`, or continue the one that exists: fill only open boxes and
   check whether code changed since the recorded results (if so, they are stale).
2. In the background: `python tools/ce_run.py gate --copy gateXYZ --logs <scratchpad>/gate`
   It runs smoke, check, the Pollution, Sable, TFMG and energy GameTests, build, jarServer (needs the server install, docs/SMOKE.md), pitest, then runData from an emptied
   `src/generated` and compares the result with the checkout. A failed step does not stop the later ones. Read the printed
   verdicts; open a log only for a failed step. Rerun single failed steps by name, not the whole gate.
   Do not edit the checkout while the gate runs, and note the commit or dirty state it ran on.
3. Not covered by the script. Do each, or record why not:
   - `scripts/smoke-mutations.ps1`, if any audit or checker changed
   - every WARN read; METRICS compared with the last release's record (`report.txt`, see `docs/SMOKE.md`)
   - 2.2: previous version's fixture added, manifest and behaviour baseline diffs additions only, old and extreme configs,
     Create version range, `compatMatrix` rows as `docs/COMPAT.md` asks
   - 2.3: version, jar name, changelog heading and tag agree; jar contents
   - 2.5: changelog from `git diff <last tag>..HEAD`, review for tick/render allocations and client classes in common
     code, no TODO or debug left, docs numbers updated
4. Record what actually ran, with numbers. List every exception with who approved it.
5. Write the Part 3 hands-on list from the changelog. Never tick Part 3 or sign for the author.
6. Never tag, or ask for a tag, while a box is open or unexplained. Push and publish only when asked (committing follows CLAUDE.md).
