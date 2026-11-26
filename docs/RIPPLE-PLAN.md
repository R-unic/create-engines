# Plan: smoother speed ripple

Status: proposed 2026-11-26, nothing built. The author decides on step 1, because it moves the speed and capacity the
network sees (see "Compatibility").

## What goes wrong today

A rough engine (one whose speed ripple, `EngineSimulation.ripple()`, is above a few percent) shows its ripple as
**jumps**, not as a smooth surge:

1. **The network speed is a raw sample.** `EngineController.publishSpeed` sends `Math.round(sim.rpm())`, and `sim.rpm()`
   is the crank's speed at the instant the tick ends: somewhere on the ripple, not its middle. A tick (50 ms) is not
   locked to the firing cycle, so the sample lands at a different point of the surge every tick. That is aliasing: the
   speed seen from outside wanders up and down by up to half the ripple at a beat frequency that has nothing to do with
   the engine.
2. **The wander gets through in steps.** A new speed is sent only when it is off the last one by the `significant` band
   (4% of the speed) and `PUBLISH_INTERVAL` (10 ticks) has passed. An engine whose ripple is above that band therefore
   republishes a random-looking speed about twice a second, for ever, even at a steady throttle and load.
3. **Every republish is a visible jump.** Create redraws its shafts at `renderTime * speed`, so every shaft, cog and belt
   on the line jumps to a new angle. `RenderRotation.alignedAt` jumps the crank with them (up to 45 degrees) so they stay
   square. Pistons, rods and cams follow the crank, so the whole engine twitches on every publish.
4. **The same sample feeds other numbers.** The capacity (`ratedTorque(..., sim.rpm())` then `publishCapacity`), the
   goggles' RPM line, the Display Link speed, the Black Box speed trace and the sounds that read `summary.rpm()` all take
   the same sampled speed, so they flicker in the same way.

What is not wrong: the ripple figure itself (`ripple` is measured over a full cycle, from its min and max), and the
rating loss `Roughness` takes from it. Those stay.

## The fix, in three steps

### 1. Send the cycle's mean speed, not a sample (the fix that matters)

- `EngineSimulation` already works out the cycle's mean angular speed in `finishRotorCycle` (`radians / cycleTime`) and
  throws it away. Keep it in a field and add `meanRpm()`: the mean over the last full cycle, falling back to `rpm()`
  before the first cycle completes, while cranking, and when stopped. A getter only: no step, torque or trajectory
  changes, so `compat/behaviour.txt` and `compat/trajectories.txt` must not move (they are the check that it did not).
- A cycle at idle is several ticks long, so a mean that holds for a whole cycle would lag a fast throttle change by a
  cycle. Ease between cycle means instead of stepping: blend toward the newest mean with a time constant of about one
  cycle, so the curve is smooth and the lag is at most one cycle. Pure function, in its own small class
  (`engine/sim/MeanSpeed.java`), not added onto `EngineSimulation`, which is already on the giant-file list.
- `publishSpeed`, the capacity, `EngineSummary.rpm` (goggles, Display Link, Black Box, sounds) read the eased mean.
- **Not changed:** the governor (`governor.update(sim.rpm(), ...)`), the tuning readings and everything inside the
  simulation keep the instantaneous speed. They are control and physics, and moving them would change behaviour.
- With the ripple gone from the published speed, a steady rough engine falls inside the 4% band and stops republishing.
  The band and `PUBLISH_INTERVAL` stay as they are; they now only fire on real speed changes.

### 2. Make the jumps that remain small (drawing only)

A real speed change (throttle, load, spool-up) still republishes in 4% steps, and each step still jumps Create's shafts.
That is Create's drawing and we leave it. Our own parts can take it more gently:

- Check the worst case first: record the crank angle per frame through a spool-up with the existing smoke harness and
  measure the jump `RenderRotation.squareGap` takes on each speed change. If it is under a few degrees once step 1 is in,
  stop here.
- If it is not: on a speed change, spread the crank's catch-up over a few frames (a short ease, capped so it finishes
  before the next publish can come) instead of taking it in one frame. The class comment records that slow easing left
  the crank out of step with its shafts for a second or more, so the ease must be short (a handful of frames), not the
  `ALIGN_TICKS` drift nudge.

### 3. Optional: draw the ripple as a surge (the author's call)

`docs/DESIGN.md` 2.6 says ripple "shows up in diagnostics and visuals". After steps 1 and 2 it no longer shows on the
crank's motion at all, only in the bucking pistons (`EngineRock`). If the author wants the surge visible:

- Add a draw-only angle offset to the crank, a sine at the firing rate (`firing` impulses per cycle) whose size follows
  `summary.ripple()`, keyed to the crank's own angle so it keeps time with the firing and stops when the crank does.
  `SpringPackLag` already does exactly this for the Dual-Mass Flywheel's secondary; reuse its shape rather than writing
  a second one.
- Tiny by design: a ripple of 15% is a speed swing of plus or minus 7.5%, which over one firing interval is only a few
  degrees of angle. Cap it under the jump that `RenderRotation` would correct, so it never fights the alignment.
- Shafts beyond the crank do not surge (Create draws them); the crank's shaft-shaped ends must still meet them square,
  so the offset fades to zero at the crank's ends, or is applied to the throws and pistons only. Pick whichever looks
  right in game; this is the part to try before deciding.

## Compatibility

- Step 1 changes the speed and capacity a rough engine gives the network: today it wanders around the mean, after it
  sits on the mean. For a smooth engine (ripple under the 4% band) nothing visible changes. Within 2.x this needs the
  author's explicit say-so naming this change, or a config option that existing worlds do not have on (default off),
  per [COMPATIBILITY.md](COMPATIBILITY.md). Recommendation: ask for the say-so; the old behaviour is a sampling bug, and
  an option nobody would turn off is just more to test.
- Steps 2 and 3 are client drawing only: no saved data, no numbers, no IDs.
- If approved, record it under Exceptions in the release record for the version it ships in.

## Tests

- `MeanSpeedTest` (unit): a constant speed gives itself; a sine ripple of any size gives its mean, whatever tick phase it
  is sampled at; a step change in mean is followed within one cycle and never overshoots; stopped and cranking fall
  back to the instant speed; NaN and zero-length cycles do not leak.
- A sim test: a deliberately rough engine (one cylinder, no flywheel) at steady load, run for many cycles. The published
  speed sent each tick (through a test seam on `publishSpeed`'s target) changes no more than once after settling. Today
  it changes every `PUBLISH_INTERVAL`; that is the regression this pins.
- A GameTest: the same rough engine on a real network; Create's network speed is constant over 200 ticks once settled.
- `RenderRotationTest` additions for step 2 (the ease finishes in its frame budget, the crank ends square with Create's
  angle) and for step 3 (the offset is zero at zero ripple and when stopped, and never exceeds its cap).
- `behaviour.txt` and `trajectories.txt` unchanged after step 1: that is the proof the simulation was not touched.
- pitest over the new `engine/sim` class.

## Docs to move with it

- `docs/DESIGN.md` 2.6 ("Create sees a smoothed speed and capacity"): say how it is smoothed (cycle mean, eased).
- `docs/LISTING.md` changelog line; `README.md` only if it describes the published speed.
- Step 3 only: the Ponder or troubleshooting scene that shows roughness, if it describes how a rough engine looks.

## Order

Step 1, measured in game on a rough engine before and after (a single cylinder with no flywheel is the worst case), then
step 2 only if the measurement says so, then step 3 only if the author wants the surge drawn.
