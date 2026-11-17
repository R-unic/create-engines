# Compatibility matrix

The rows below are the support promise (docs/ROADMAP.md, Verification track item 10). A mod is listed as supported only
once its row is green. A row is green when the client audit passes under it with no failed check and a clean log
(the same verifier and log allow-list as the main smoke run), and the GameTest and unit layers pass on the same build.

```
./gradlew compatMatrix --offline     # every row that has a run configuration, each verified
```

Run it in a private copy of the checkout (see docs/SMOKE.md "Running it"). Each row has its own game folder
(`run/smoke-client-<row>`), so settings and logs never leak between rows.

| Row | How it is run | Status |
|---|---|---|
| Baseline: Create 6.0.10, Flywheel default | `runSmokeAudit` | see docs/releases for each release's record |
| Flywheel off | `runSmokeAuditFlywheelOff` (seeds `flywheel-client.toml` with `backend = "OFF"`) | see "Last recorded results" |
| Sodium 0.8.13 | `runSmokeAuditSodium` (Sodium on the early classpath, as its jar requires) | see "Last recorded results" |
| Oldest and newest supported Create | the supported range is `[6.0.10, 6.1)`, so the oldest and newest are both 6.0.10 today; add a row when the range widens | n/a |
| Create Aeronautics (1.4 work) | not built yet | **open: needs the addon and the 1.4 work** |
| Other Create addons | the list is the author's to choose | **open: no addons chosen** |

What a row checks, beyond the audit's own sweep: no log errors beyond the allow-list, every model and all 88 Ponder
pages render, engines run and power their networks, and the fleet leaks nothing.

Not yet automated for any row: a world saved without the mod opening with it, and a world saved with the mod opening
without it (the second is a Minecraft behaviour, the first is covered by the legacy-world GameTests).

## Last recorded results

Run 2026-11-16, private copy, Create 6.0.10, NeoForge 21.1.219:

| Row | Checks passed | Failures | Warnings |
|---|---|---|---|
| Flywheel off (`backend = "flywheel:off"`; the value `OFF` is rejected by Flywheel and silently falls back to the default, which the first attempt did not notice; after changing the value, search the row's log for "not a valid resource location", which is not yet an automated check) | 963 | 0 | 2 (a Ponder page without a storyboard for the Maker's Plate; one sound event the fleet did not trigger) |
| Sodium 0.8.13 (loaded, confirmed in the log) | 964 | 0 | 1 (the same Ponder page) |

The warnings are the audit's existing ones and are not specific to a row. Both rows are green, so Flywheel off and Sodium 0.8.13 are supported on this build.
