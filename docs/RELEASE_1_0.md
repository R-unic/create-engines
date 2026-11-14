# 1.0 release checklist

1.0 is a stability promise more than a feature drop. Status as of 2026-11-14 (1.0.1 prepared: version bumped, changelog in `docs/LISTING.md`; tag `v1.0.1` to publish).

## 1. Stability audit

- [x] Unit tests pass (`./gradlew test --offline`).
- [x] GameTests pass: "All 116 required tests passed" (`./gradlew runGameTestServer --offline`).
- [x] Config keys and ranges reviewed; README now lists every key group, including `alternatorEnergy`.
- [x] Removed blocks inventoried (below).
- [x] **Freeze the promise.** Block, item and fluid IDs, config keys and ranges, advancement IDs and the fuel data-map
      format stay stable through 1.x. The README and the 1.0.0 changelog now use the same list (the README had left out
      ranges and advancement IDs).
- [x] **Old-world load test (done).** Open a world saved on 0.1, 0.2, 0.3 and 0.4 (an engine, a Transmission if the world has
      one, a Governor) in the 1.0 build. Look for missing-block warnings in the log, block entities that fail to load and
      engines that behave differently. Block entities read their NBT with defaulting getters, so a crash is not
      expected, but it has not been tried.
- [x] **Black Box tape size.** Worked out from the code, not measured in a world. Saved: 600 samples (`CAPACITY`) in
      four int arrays plus at most 24 state changes of four ints, so about 9.6 KB + 0.4 KB = 10 KB of NBT per full box
      before the region file compresses it. Synced to clients: the readout is boiled down to 40 columns (`COLUMNS`) in
      four arrays plus the same events, about 1 KB. Both are bounded by constants, so a hundred full boxes cost about
      1 MB on disk and nothing extra per sync. No cap needed.
- [ ] **Dedicated server and two players.** Sync of goggle readouts, Display Link, Black Box, sump, starter and
      advancements; client-only code (sounds, particles, flywheel visuals) must not load on the server.
      Static check done 2026-11-13: the only `net.minecraft.client` import in common code is `GoggleLines`, reached only
      from goggle tooltip methods the server never calls; `CEBlocks` and `CEFluids` import only datagen and
      client-extension types. Still open: an actual dedicated server with two players.

### Removed blocks

| Block | Where it lived | Released? |
|---|---|---|
| Transmission | Removed in 0.4.0 (c0d360e), restored before 1.0 | Missing from 0.4.0 only; 0.1.0 through 0.3.0 had it. Placed ones vanished when loaded on 0.4.0 |
| Governor | Removed in c0d360e, restored in d06a643 before 0.4.0 | Back in 0.4.0, nothing lost |
| Rev Limiter | Added 1c74db2, removed 845efe3 | Development builds only: neither is in the v0.1.0 tag or any later tag |
| Lambda Sensor | Added 1c74db2, removed 845efe3 | Development builds only |

Worlds made on a development build between those commits may still hold a Rev Limiter or Lambda Sensor. They vanish on
load like the Transmission does. There is no remap; decide before 1.0 whether to add one or leave it.

## 2. Things nobody has verified

- [x] **Listened to the six newer sounds** (author, 2026-11-13: all good): catch, stall, exhaust pop, metal tick, clatter loop, squeal loop. They had been
      only checked numerically. Loudness constants are at the top of `EngineAmbience`.
- [ ] **The stall sound** never fired in a client run (an engine starved of air coasts for over 15 s). The trigger
      (Running, Overloaded or Coasting into Stalled) is now `EngineCues.forChange`, unit-tested in `EngineCuesTest`, so
      the logic is covered. Hearing it in a client run is still open.
- [x] **Dry-pocket case decided: keep it.** An engine walled off from water inside a flooded area runs, with its intake
      and exhaust mouths in the pocket too. Locked by the `anEngineInADryPocketKeepsRunning` GameTest; recorded in
      DESIGN.md.

## 3. Balance and performance

- [ ] Late-game output at the recommended 512 RPM Create limit: a boosted V12 plus a heat-exchanger boiler.
- [ ] Tick time with 20 or more running engines in loaded chunks.
- [ ] Particle and sound load at the default `effectDensity` on a big engine.

## 4. Scope

- [x] **Variable valve timing is not in 1.0.** It stays on hold and is not ruled out: it can come in a later 1.x release
      if it is built additively. Cooling blocks, parallel throttles and knock were declined and stay out.

## 5. Release polish

- [x] Set `mod_version` to `1.0.0` in `gradle.properties`.
- [ ] Move the "Changelog for 1.0.0" draft in `docs/LISTING.md` into the published listing.
- [ ] Retake the screenshots (`run/screenshots` after smoke runs) and replace the CurseForge gallery copies by hand (Modrinth is synced by `gallery.yml` on the tag).
- [x] **"Built with AI" note kept and reworded** (README and `docs/LISTING.md`): it is still a young mod, but nobody should
      have to expect things to break any more.
- [x] **License changed to MIT** (`LICENSE`, `mod_license` in `gradle.properties`, README, `docs/LISTING.md`), so modpacks
      and contributors need no permission.
- [ ] Try one newer Create patch release inside the `[6.0.10,6.1)` range, or narrow the range.
- [ ] Tag `v1.0.0` (the workflow publishes to Modrinth and CurseForge), then `gh release create` with the jar; CurseForge
      holds new files for review.
