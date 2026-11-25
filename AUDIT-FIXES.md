# Audit fixes (throwaway: delete before commit)

Audit of the uncommitted 2.3.0 wear-mode work on `release-2.3.0`, 2026-11-25. Every fix gets a test (CLAUDE.md).
None of these should move `compat/behaviour.txt` or `compat/trajectories.txt` (wear mode is off by default); if one does, stop.
Re-run `./gradlew pitest` after touching `engine/sim` (item 4).

## Bugs

- [x] **1. A new piston inherits the old one's dry time.**
  `dryTime` moved from `WearKeeper.dry[]` (reset on every rebuild) to `CylinderBlockEntity` (survives rebuilds, on purpose).
  But nothing zeroes it when the piston changes, so a fresh piston fitted into a bore that was 90% dry seizes
  almost at once. Before this change the swap rebuilt the engine and reset it.
  - Fix: in `WearService.partsChanged` (`content/WearService.java`, the `if (piston)` branch), call `body.setDryTime(0)`.
    Or do it in `CylinderBlockEntity.resetPiston` (`content/cylinder/CylinderBlockEntity.java:191`) before its early return.
  - Test: GameTest that runs a cylinder dry past the first warning, swaps the piston by hand, and asserts `dryTime() == 0`.

- [x] **2. A broken head hidden by wear mode off still takes a gasket, and loses it later.**
  With the mode off, `showsBroken()` is false, so `WearService.onHead` and `BankBuilderItem` (`content/tool/BankBuilderItem.java:396`)
  fit a gasket. Switch the mode back on: the head is broken again, `CylinderHeadBlock.onRemove` skips popping the
  gasket, and it is gone. The cylinder already handles this case with `dropHiddenWreck()`; the head has no equivalent.
  - Fix: add `CylinderHeadBlockEntity.dropHiddenBreak()` (clears `broken` when `!Tuning.wearMode`, `setChanged`, `sendData`)
    and call it where a gasket goes in with the mode off (`head.fit(...)` path in `WearService.onHead`, and the Bank Builder).
  - Test: in `WearHandEdgeGameTests`, break a head, mode off, fit a gasket, mode on, break the head out, assert the gasket drops.

- [x] **3. One tick runs with the wrong wear state after the mode is toggled.**
  `EngineController.tick` (`engine/EngineController.java:604`) queues the rebuild and then carries on into `wearTick`
  with the old `wearFaults` (mode just on: `Map.of()`, so saved failures are ignored for that tick).
  - Fix: `return` right after the `rebuildNear` call.
  - Test: extend `WearToggleGameTests`: a saved seized cylinder, mode switched on, assert its fault shows on the next tick and no wear step ran with it missing.
  - (Checked: `CrankshaftBlockEntity.rebuildEngine` makes a new controller, so the call does not repeat forever.)

## Performance

- [x] **4. Batteries send a packet every 0.1% of wear, even where the goggles show whole percent.**
  `BatteryBlockEntity.wearBy` (`content/battery/BatteryBlockEntity.java:125`) now resyncs on `BatteryWear.shownStep`
  (tenths of a percent), 50x more often than the old 5% step. Above 10% `percentText` shows whole numbers, so 9 of 10 of
  those packets change nothing on screen. A big bank wearing together multiplies it.
  - Fix: make `shownStep` (`engine/sim/BatteryWear.java`) count tenths below 10% and whole percents above, so it moves exactly when
    `percentText` does.
  - Test: `BatteryWearTest`: over a fine sweep 0..1, `shownStep` changes iff `percentText` changes.

- [x] **5. Toggling the mode makes every engine scan every engine.**
  Each controller calls `EngineTopology.rebuildNear(level, primary, 0)` (`engine/topology/EngineTopology.java:93`), which walks
  every layout and segment: O(engines^2 x segments) in that one tick. A spike, not a steady cost, but big on a large server.
  - Fix: add `EngineTopology.rebuild(Level, BlockPos primary)` that just does `topology.rebuilds.add(primary)`, and call that.

- [x] **6. `WearKeeper.total` can run every tick, boxing as it goes.**
  `wearTick` recomputes while `wearTotal < 0`, and `total()` returns `WearTotal.NONE` (-1) when it finds no loaded part,
  so an engine whose parts are unloaded rebuilds an `ArrayList<Double>` and looks up every block entity each tick.
  - Fix: track "computed this interval" with its own flag instead of `wearTotal >= 0`; fill a `double[]` sized
    `3 * cylinders + batteries` and pass the used length, no boxing.
  - Test: unit test that `total` of an engine with nothing loaded is computed once per interval (count lookups via a fake level, or test the flag logic directly).

- [x] **7. The transmission reads its neighbours' redstone every auto interval.**
  `TransmissionBlockEntity.tick` (`content/transmission/TransmissionBlockEntity.java:211`) calls `isAuto()`, which calls `signalNow()`
  (six neighbour reads), though `updateGear` already caches `signalSetting` on neighbour change.
  - Fix: server-side `isAuto()` uses `settingUnder(signalSetting)`. Only if every signal change goes through `updateGear`; check that first.
  - Test: existing `redstoneOverridesTheTransmissionPanel` and the auto-shift GameTests must stay green unchanged.

## Server performance (whole mod, not just the wear diff)

Ordered by how badly they scale. Every server tick path was read; the engine's own syncing (`CylinderStatus`,
`EngineSummary`, camshaft status, `publishSpeed`/`publishCapacity`) is already change-gated and is fine.

- [x] **9. Battery banks cost O(n^2) a second, and any battery anywhere resets every bank.**
  Each battery re-floods its whole bank every `BANK_REFRESH` (20 ticks) in `BatteryBlockEntity.bank()`
  (`content/battery/BatteryBlockEntity.java:241`), via `BatteryBank.around` (`content/battery/BatteryBank.java:30`),
  so a bank of n does n floods of n (200 batteries: 40,000 BE lookups plus HashSet churn a second). The flood has no size cap.
  On top of that, `BatteryBlock.TOPOLOGY` (`content/battery/BatteryBlock.java:179,188`) is one global counter:
  placing or breaking a battery anywhere in the world makes every bank re-flood on its next look.
  - Fix: the battery that floods hands the same `List` and timestamp to every member it found (one flood per bank per
    refresh); make the counter per level, or bump a per-bank generation. Cap the flood (config limit, like `maxManifoldBlocks`).
  - Test: GameTest with a big bank (e.g. 10x10x2) asserting the flood count per refresh is 1 (a counter in `BatteryBank`),
    and that `bankSignal` and alternator charging are unchanged.

- [x] **10. Every engine re-walks its Create gear network every 5 seconds.**
  `EngineTopology.tick` revalidates each engine every `REVALIDATE_INTERVAL` (100 ticks). A rescan runs
  `EngineScanner.addCoupledLines` → `CrankCoupling.find` (`engine/topology/CrankCoupling.java:64`), a breadth-first walk of up to
  `maxCouplingWalk` = 512 kinetic block entities through Create's `getConnectedNeighbours` by reflective `Method.invoke`.
  An engine driving a factory walks the factory: 100 engines is about 10,000 neighbour computations a second. Any
  `IRotate` placed within `GEARING_REACH` = 64 blocks also forces a rescan of every engine in reach.
  - Fix (cheap, no behaviour change): `MethodHandle`s instead of `Method.invoke`; reuse the `ratio` map/queue.
  - Fix (bigger): keep each layout's coupling result and redo the walk only when `gearingChanged` touched it, or every Nth
    revalidation. **Behaviour question for the author:** Create clutches and gearshifts change coupling without a block
    placement, so a slower walk means a coupled engine notices a clutch later. Don't do this part without their say.
  - Test: an existing coupled-crank GameTest must pass unchanged; add one that flips a clutch and checks the engine splits within the interval chosen.

- [x] **11. With the pollution mod installed, each exhaust outlet reads 343 blocks every half second.**
  `PollutionCompat.release` (`compat/pollution/PollutionCompat.java:57`) calls `PollutionBridge.airHolds`
  (`compat/pollution/PollutionBridge.java:39`), a 7x7x7 cube, for every outlet on every emission (`SYNC_INTERVAL` = 10 ticks).
  100 engines with 2 outlets: ~137,000 block reads a second.
  - Fix: cache the cap check per outlet for a few seconds (the cap is a ceiling, not a precise meter), or release once a second
    with the summed steps.
  - Test: the pollution GameTests (cap reached → nothing released) must pass unchanged.

- [x] **12. A Black Box searches for its engine from scratch every second.**
  `BlackBoxBlockEntity.findSummary` (`content/blackbox/BlackBoxBlockEntity.java:166`) runs `EngineScanner.crankSeeds` on all six
  faces each `INTERVAL`; a face touching a manifold floods up to `maxManifoldBlocks` = 256 blocks.
  - Fix: remember the primary it found; search again only when that crank is gone or has no summary.
  - Test: existing recorder GameTests; add one that breaks the engine and rebuilds it beside the box, and the box finds it again.

- [x] **13. Revalidating an unchanged engine still rebuilds its member index.**
  `EngineTopology.tick` retires and re-adds every member in `enginesByMember` (new `HashSet`s each time) even when the
  rescanned layout equals the old one. GC churn on every engine every 5 seconds.
  - Fix: when the rescanned layout `equals` the retired one, put the old index entries back untouched.
  - Test: topology unit/GameTest coverage must stay green; no behaviour change expected.

- [x] **14. Check: head glow may flap on an engine that fires on and off.** `EngineController.glow` (`engine/EngineController.java:1776`)
  sets `LIT` with `UPDATE_ALL` on every head whenever `state()` flips between RUNNING and COASTING (any cylinder fired last
  cycle or not), checked every 10 ticks. A single-cylinder engine held by a governor, or a rough idle, could flip it
  twice a second: block updates and light recalculation per head. Measure first (count `EngineGlow.set` changes on a
  governed one-cylinder engine); if it flaps, hold the glow on for a second or two after the last firing.

## Check, not yet a finding

- [x] **8. Is `Tuning.wearMode` right on a dedicated-server client?** It is a SERVER config; the new `shows*()`, item bars and goggle
  lines all read it on the client. NeoForge syncs server configs, but confirm `CEConfig`'s bake runs on the client
  for the synced copy, and that `Tuning.reset` (`engine/sim/Tuning.java:91`) doesn't leave it stale after leaving one server for another.
  If it isn't synced, every wreck and wear line is invisible in multiplayer.

## Looked at, fine

Black Box failure filter (failure events are the only `from == to` ones), `pullSeizedPiston` kit-before-piston order,
`PlateFigures` surrogate trim, `Gears.fromSignal` remap (approved, Exception in `docs/releases/v2.3.0.md`), `Wear.warns`.

## Outcome (session "Audit fixes", 2026-11-25)

- 1-7, 9, 11-13: fixed. 1 also covers the Bank Builder (its forged swap kept the old wear and dry time and returned the piston as new).
  2 also resets `stockFitted` when a head breaks, and one WearToggle assertion now expects the new behaviour.
- 8: checked in NeoForge 21.1.228 code, not on a live server. A synced server config fires `Reloading` on the client, which bakes `Tuning`.
  NeoForge sends it at login only, so a change made mid-session does not reach players already connected. Not changed.
- 10: cheap part done. The bigger part (walking less often) NOT done: it needs the author.
- 14: measured, not changed. Governed at 48 RPM: 37 glow switches in 2400 ticks, none within 20 ticks of the one before.
  Free, governed at 80, lean at metering 3: 0 switches. Governed at 20 and metering 2 stalled.
