# Compatibility rules

Standing rules, from 1.3 on, until a major release (2.0):

1. **A new version never breaks an old world.** Every block, item, fluid, block state, saved key and recipe an earlier
   1.x version put in a world is still there and still loads.
2. **A factory built today works the same in every later 1.x version.** Power, fuel burn, boost, flow rates and recipe
   results for existing builds do not change. Balance changes, new behaviour and fixes that move numbers wait for 2.0,
   or ship behind a new block, item or config option that existing builds do not use (default off or legacy).

1.3 is the baseline. It changed how factories behave compared with 1.2; that was the last time a minor release did.

## What guards it

| Guard | Where | Catches |
| --- | --- | --- |
| `CompatManifestTest` | `src/test/java/.../compat`, `src/test/resources/compat/manifest.txt` | A registry id, block state property, enum value, saved-data key or tag file removed; a recipe, loot table or data map edited |
| `BehaviourBaselineTest` | `engine/sim`, `compat/behaviour.txt` | Torque curves of 250 builds (every fuel, crude/precision parts, 1 and 6 cylinders, every boost) and the `Tuning` defaults changing |
| `SimTrajectoryTest` | `engine/sim`, `compat/trajectories.txt` | 40 seeded random engines (every part, boost, direction change, restore) running differently tick by tick, which the torque curves alone can miss |
| `LegacyWorldGameTests` | `gametest`, `compat/world/*.snbt` | A running engine saved by 1.0.0, 1.1.0, 1.2.0 (recorded from those releases by `scripts/record-corpus.ps1`; diesel, spark, rotary) or by the 1.3 baseline failing to load, losing saved keys, or any block entity failing to load an empty save |

## Working with them

- A failure is a question: *does this break an old world or a built factory?* If yes, change the code, not the test.
- Never edit or delete an existing manifest/baseline line in 1.x. Additions are fine:
  `COMPAT_REGEN=1 ./gradlew test --offline` appends new entries and never rewrites existing ones.
- New blocks, items and saved keys appear as "new entries not yet recorded"; append them in the same commit.
- Reading a saved key that is missing must fall back to the old behaviour (see `GovernorBlockEntity` and
  `ExactRpm` for the pattern).
- A new fixture is added for each shipped version (`engine_1_4.snbt`, ...) and loaded by a test beside the old one.
  Fixtures from shipped versions are never regenerated.
- The 1.3 baseline may still be topped up while 1.3 is unreleased; once it ships it is frozen.
- At 2.0 the baselines are deliberately regenerated and the old fixtures are retired in one reviewed change.

## Known gaps

- The behaviour baseline covers the simulation, not Create stress or recipe chains end to end; the manifest pins recipe
  text but not what Create does with it.
- `LegacyWorldGameTests` loads one engine plus empty saves; other builds (refining column, rotary, dyno) are only
  covered by the empty-save test and the saved-key manifest.
