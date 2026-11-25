# Compatibility rules

Standing rules for every release within a major version. They held for 1.x from 1.3 on; 2.0 was the one breaking release
(advancement ids and config files migrated then; see docs/ROADMAP.md, 2.0). They hold again for every 2.x release until 3.0, with
2.0 as the floor. Wherever this file, STABILITY.md or CLAUDE.md says "1.x", read "the current major version".

1. **A new version never breaks an old world.** Every block, item, fluid, block state, saved key and recipe an earlier
   version of the same major put in a world is still there and still loads. 1.x worlds still load
   too, through 2.0's migration.
2. **A factory built today works the same in every later version of the same major.** Power, fuel burn, boost, flow rates
   and recipe results for existing builds do not change. Balance changes, new behaviour and fixes that move numbers wait for
   the next major, or ship behind a new block, item or config option that existing builds do not use (default off or
   legacy). The only other way is the author's explicit approval naming the change, recorded as an exception in that
   release's record (2.3.0's transmission redstone mapping is one).

1.3 was the 1.x baseline: it changed how factories behave compared with 1.2, the last time a minor release did.

## What guards it

| Guard | Where | Catches |
| --- | --- | --- |
| `CompatManifestTest` | `src/test/java/.../compat`, `src/test/resources/compat/manifest.txt` | A registry id, block state property, enum value, saved-data key or tag file removed; a recipe, loot table or data map edited |
| `BehaviourBaselineTest` | `engine/sim`, `compat/behaviour.txt` | Torque curves of 250 builds (every fuel, crude/precision parts, 1 and 6 cylinders, every boost) and the `Tuning` defaults changing |
| `SimTrajectoryTest` | `engine/sim`, `compat/trajectories.txt` | 40 seeded random engines (every part, boost, direction change, restore) running differently tick by tick, which the torque curves alone can miss |
| `LegacyWorldGameTests` | `gametest`, `compat/world/*.snbt` | A running engine saved by 1.0.0, 1.1.0, 1.2.0, 1.2.1, 1.3.0 or 2.0.0 (recorded from those releases by `scripts/record-corpus.ps1`; diesel, spark, rotary), or by the 1.3 and 1.4 baseline engines, failing to load or losing saved keys, or any block entity failing to load an empty save |

## Working with them

- A failure is a question: *does this break an old world or a built factory?* If yes, change the code, not the test.
- Never edit or delete an existing manifest/baseline line within a major version. Additions are fine:
  `COMPAT_REGEN=1 ./gradlew test --offline` appends new entries and never rewrites existing ones.
- New blocks, items and saved keys appear as "new entries not yet recorded"; append them in the same commit.
- Reading a saved key that is missing must fall back to the old behaviour (see `GovernorBlockEntity` and
  `ExactRpm` for the pattern).
- A new fixture is added for each shipped version and loaded by a test beside the old ones (STABILITY.md 2.2). Fixtures from
  shipped versions are never regenerated. **Gap:** none was added for 2.0.1, 2.0.2, 2.1.0, 2.2.0 or 2.3.0; each release record
  lists that as an exception (2.14 in 2.3.0), and the newest fixture is still 2.0.0's.
- The 1.3 baseline shipped with v1.3.0 and is frozen: its fixture (`engine_1_3.snbt`) and its lines in `behaviour.txt`,
  `trajectories.txt` and `manifest.txt` are never edited. 1.4's own fixture is recorded from the v1.3.0 source with
  `scripts/record-corpus.ps1 v1.3.0` and kept beside it.
- At a major version the baselines may be regenerated and old fixtures retired, in one reviewed change. 2.0 did neither:
  the 1.x fixtures still load and are still tested.

## Known gaps

- The behaviour baseline covers the simulation, not Create stress or recipe chains end to end; the manifest pins recipe
  text but not what Create does with it.
- `LegacyWorldGameTests` loads a diesel, a spark and a rotary engine saved by each of 1.0.0, 1.1.0, 1.2.0, 1.2.1, 1.3.0 and
  2.0.0, one 1.3 and one 1.4 engine, and empty saves for every block entity. Nothing saved by 2.0.1 or later is loaded (see
  above), so keys added since 2.0.0 (batteries, the Dual-Mass Flywheel, wear) are covered only by their own GameTests and the
  manifest. Other builds (refining column, dyno) are only covered by the empty-save test and the saved-key manifest.
