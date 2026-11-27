# Sound, round two: sounding right in the game (plan, 2026-11-26)

**A plan for the author to choose from. All six steps, 0 to 5, are built (2026-11-26) and have had a full bench run
(step 5, below). None is signed off by ear.** The first round of the rework is recorded in
[ROADMAP-3.0.md](ROADMAP-3.0.md), "The sound rework". This plan picks up from there. Sound is one of the mod's biggest draws, and what
counts is how it sounds **in the game**: in a world, next to vanilla sounds, while the player walks around, with ten engines running.
A clean offline render isn't enough. Some steps regenerate sounds from scratch.

Sound is client-side only. No rating, sim number, saved key or sound ID moves, so `compat/behaviour.txt` and
`compat/trajectories.txt` stay untouched. Retuned sound is still an intended behaviour change, so the author picks which version gets
each step (2.4 is the sound rework; 2.5 has "look, sound and performance").

## Where the jank comes from (read from the code, 2026-11-26)

1. **The note is late.** Minecraft queues 4 stream buffers per streamed sound and tops them up once per client tick
   (`Channel.QUEUED_BUFFER_COUNT`, `SoundEngine.tick`). `EngineNotePcm.CHUNK` is a tenth of a second, so the note plays up to about
   0.4 s after the synth rendered it. `EngineNoteClock` keeps firings in step with the visuals, but a change in speed or load still
   arrives late. A blip of the throttle, a catch or a stall is heard after it is seen. Of all the problems here, this one is most
   likely to read as janky.
2. **Levels and pitches move in steps.** `EngineNoteSound.tick` sets the source's volume 20 times a second (fade, chorus share,
   casing, settling back), and `MachineLoopSound` does the same for the pitch and volume of the turbo whistle, supercharger whine,
   starter and intake roar. OpenAL jumps to each new value, so a spool or a fade can be heard as a staircase. Minecraft also clamps
   pitch to 0.5–2, which squashes a turbo's spool into one octave.
3. **Handovers change the character.** As the player moves, an engine passes between its own note, the shared room voice and the
   sample fallback. These sound different, come from different places and cross-fade at different rates (in over about 0.8 s, out over
   1.25 s). There is no hysteresis, so walking between two engines can swap them back and forth.
4. **One point at the crankshaft.** The note comes from the primary crankshaft, even when the exhaust outlets that the note is built
   from are blocks away. Minecraft's distance fall-off is linear to 16 blocks. Apart from the synth's own dulling, there is no air
   absorption, room or wall.
5. **The voice is a synth's voice.** Each firing strikes three biquad resonances per outlet, with noise, a swept-sine thump and a
   band-passed crack, at 22,050 Hz and soft-clipped with tanh. The rhythm and the build-dependence are right. The tone has hit its
   ceiling: there is no real exhaust pressure pulse, and no wave travelling down a pipe, so run length is a filter setting and not a
   resonance ("manifold length inaudible" came from this). Nothing sounds above 11 kHz, so the crack comes out dull.
6. **One-shots live apart from the note.** Pops, spit, catch, stall and failure sounds are separate Python-generated samples on their
   own sources, at their own levels, with random pitch. They don't share a tone with the engine that plays them. A catch and stall
   also play over the note instead of being the note.
7. **Only the author's ears check the game.** Every automatic check is an offline render. Dropouts, stalls, clicks at buffer joins,
   level against vanilla, and the 16-engine room are only found when the author happens to hear them.

## Known bug: the shared room voice outlives its engines (fixed)

Found by step 0's sound bench, 2026-11-26. **Fixed with step 1, the same day:** in a full bench run afterwards the `gone` phase was silent and no fault was found.
The room voice's own tick now works the scene out when no engine calls in. What follows is the bug as found. It is in the first round's shared "engine room" voice
(`content/EngineNoteSound`, its `Scene` and `Room`), which was not yet committed when the bench found it.

- **What is heard.** When every engine the room voice is playing stops calling in at once, the voice plays on with their last
  notes. Its distance fall-off is off (`Attenuation.NONE`), so it is heard just as loud from anywhere.
- **When.** From the code: the engines all stall or break, or are all removed or unloaded, as when the player travels away, with
  no other running engine left to call in. The bench has shown the removed case only; the others are not tried in game.
- **Cause.** Only a running engine's primary crankshaft calls `EngineNoteSound.ensurePlaying`, and only its `Scene.offer`
  calls `Scene.resolve`, which empties and fades the room's slots. With no caller, the slots keep their synthesizers at their last
  levels, so `EngineRoomMix.isSilent` stays false and `Room.tick` never ends the voice. Leaving the world ends it (`clear`).
- **Measured.** With all sixteen of the room's engines removed, -44 LUFS in one run and -45.7 in another, where an empty pad reads
  about -101 and one engine from 6 blocks about -47. In an earlier run, which then put the reference sounds after the room, it was
  still playing in them, several pads away.
- **Reproduce.** `tools/sound_bench.py --scenes room`: the report lists it under "Faults found" (the `gone` phase).
- **For the fix.** Something that runs whether or not an engine does, such as the room voice's own tick or a client tick, has to
  work the scene out, so engines that stopped calling in are forgotten. It needs a test, and the bench's `gone` phase silent.

## The plan, in order

Each step can ship on its own and is measured with step 0's capture, before and after.

### Step 0: Hear the game, not the renderer (first, small to medium)

**Built, 2026-11-26.** How to run it and how to read its figures: [SMOKE.md](SMOKE.md), "The sound bench". Where it differs from
the list below: volume and pitch stairs are judged mainly from the levels the game logs each tick (a recording of an engine
hides them); the recording is lined up by two claps and checked against the Mechanical Press; gaps are not judged where an
engine is quiet between firings. No recordings of real engines are chosen yet (decision 5): `--refs` takes them.

A repeatable in-game listening session, so every later change shows its numbers from the game itself.

- **Sound bench.** A smoke-harness world (`-PsmokeSound`, beside the existing smoke worlds) with reference engines on pads: a slow
  single, I4, crossplane and flat-plane V8, flat four, V-twin, I6 diesel, V12, rotary, turbo I4 and a 16-engine room. Each engine runs
  a script: idle, throttle sweep, blip, load step, stall, restart. Then the camera walks away from it and back.
- **Capture.** `tools/trailer/record.py` already records the game's own output through the sound card (`soundcard` loopback). The
  bench reuses that and also logs each firing flash and cam sighting with a timestamp.
- **Audit** (`tools/sound_audit.py`, reads the capture and the log):
  - dropouts (stream underruns, gaps of silence while an engine runs)
  - clicks (sample jumps and short high-frequency bursts that don't line up with a firing)
  - steps (volume or pitch changing in 50 ms stairs)
  - lag between each logged firing and the onset heard
  - loudness (LUFS) of each scene against vanilla and Create sounds recorded in the same world (furnace, minecart, Mechanical Press)
  - the 16-engine room no more than 6 dB above one engine
  - repetition (consecutive firings too alike)
- **For the ear.** One WAV per scene plus a report, and the same scenes next to reference recordings of real engines for A/B. The
  author's listening is still what signs off. The audit finds faults so the author's time isn't spent on them.

**The bench's gaps, filled 2026-11-26** (the author asked): scenes behind walls, indoors and on a moving structure, how
long a change waits in the stream's queue, what the sound costs, and a fault for stacked notes
([SMOKE.md](SMOKE.md), "The sound bench"). A heard throttle delay is still not measured: in an earlier run's recording the
blip barely moved the engines' speed, and their level did not follow it closely enough to time. The queue wait stands in for it: re-audited, the runs below read a median of 252 to 302 ms before step 1 and
102 to 149 ms after. The scenes added, run in game (runs `20261126-203957`, walls and indoors,
and `20261126-203728`, moving; an earlier try at the first was thrown out, as another sound got into the recording and the
timing check failed):

- *Walls:* one, two and three blocks of stone took 3.4, 7.9 and 10.4 dB off, each duller than the last, and it came back
  with the wall gone (4 dB under the start, where the engine was still slowing from a faster idle).
- *Indoors:* 0.3 dB under out of doors just before, so no louder. The bench's figures don't show the reflection itself.
- *Moving:* the structure was carried from 30 blocks off to 6 and back. The note on the ground ended, one started on the
  structure, and no two played at once. The pitch shift was not measured.
- *Cost:* the sound thread used 0.5 to 0.6% of one core with one note (not the cap's worth of voices), and the slowest
  look round the world took 2.6 ms.
- *Dropouts:* the walls and indoors scenes each ran dry once (36 to 38 ms), and a new note started at once, with the
  shared voice standing in. The underrun is still open.
- These runs recorded about 14 dB louder than `20261126-174506` on all three reference sounds, so compare levels within
  a run, or against its references, not across these. Where the notes below under
steps 1 and 4 say the bench lacks these, they speak of the bench before this.

### Step 1: Fix the delivery, same sound (small to medium)

**Built, 2026-11-26.** How it works: [DESIGN.md](DESIGN.md), "Delivery". Where it differs from the list below: the whistle
and whine keep the pitch range the blocks already ask for (only the clamp is gone; the turbo's range was widened later
that day, when the three blower sounds were remade), and are heard from the note's place;
up to four per engine. The note's place weighs openings by muffling only. **Still owed:** the author's listening.

**Bench, 2026-11-26** (full runs `20261126-163102` before and `20261126-174506` after; other work on the sound happened in
the checkout between them, so not every difference is step 1's). The new run had no problems and no faults.

- *Better:* the room is silent once its engines are gone; all sixteen of its engines fired, 0.3 dB above one.
- *Worse:* one underrun (`flat4`, 35 ms dry) where there were none, and the least audio queued fell from about 0.25 s to
  0.09 s. A stream that runs dry is dropped by the game, so the note was gone for about 2 s (the budget's rest) with the
  room voice standing in. That run's longest wait between reads was 232 ms.
- *Unexplained:* more short bursts the audit calls clicks in the V8 scenes (0 to 5 and 6) and as the room fills (its
  count is level only because the silent `gone` phase lost 20). Multi-cylinder idles read 1 to 2 dB louder; the
  reference sounds held within 0.1 dB.
- Not measured: how soon a throttle change is heard (the bench measures each firing's lag only, which stayed small).

**Those faults, looked into the same day:**

- *The dropped note* now comes back at once instead of after two seconds ([DESIGN.md](DESIGN.md), "Delivery"). The underrun
  itself can still happen. Not yet seen in a bench run.
- *The extra "clicks"* follow the level, not a fault in the sound: the earlier run's recording, turned up by 0, 1, 2 and 3 dB
  and audited again, gave 0, 2, 5 and 7 in `v8_cross` and 0, 2, 6 and 11 in `v8_flat`. Most found while the engine ran sit about 70 to 100 ms
  after a firing, just past what the audit excuses (as some did in the earlier run), at about the -45 dB it counts from.
  The audit is unchanged. This does not explain every burst by itself, and the count says nothing about the step 2 note.
- *The louder idles:* not proven. The likely cause is intended: the note moved from the crankshaft at one end of the engine
  to its exhaust openings, which are nearer the bench's listener, who stands by the engine's middle.

- **Latency.** Smaller chunks (about 50 ms, so about 0.2 s queued at most). Step 0's underrun count decides how small they can go,
  lag spikes included. The synth also looks ahead for the time still queued: rpm and load are extrapolated along their trend, so a
  blip starts close to when it is seen. `EngineNoteTimingTest` grows a case for a throttle step.
- **Smooth gain inside the stream.** The source volume is set once. Fade, chorus share, casing and settling back are applied per
  sample in the synth, so no 20 Hz stairs.
- **Spool and whine into the note.** When an engine has a note, its turbo whistle and supercharger whine are synthesized in the same
  stream: continuous pitch, no 0.5–2 clamp, no loop seam, no extra channel (Minecraft has only a handful of streaming channels, music
  and records included). The `turbo_whistle` and `supercharger_whine` loops stay registered as the fallback for engines without a note.
  The intake roar stays a sample at the intake mouth, as decided in round one.
- **Seamless handovers.** The room voice renders the engines it holds with their own `Voice`, so joining it changes the level, not the
  character. Note and room cross-fade at equal power over the same time. Who gets a note has hysteresis: a distance margin and a
  minimum hold time.
- **Sound from the exhaust.** The note's source sits at the loudness-weighted centre of the outlets, not at the crankshaft.

### Step 2: A new note, firing grains through a pipe (the regeneration, large)

**Built, 2026-11-26**, as recommended, with fully synthetic grains (no recordings) at 32 kHz. How it works:
[DESIGN.md](DESIGN.md), "Grains and pipes". Where it differs from the list below: the muffler is in the pipe's loop and
also keeps step 1's filter and level after it; the muffled level the author chose is unchanged; the "bed" swells twice a
crank turn, as a hum at crank speed itself would be below hearing at these speeds. Outlets, not run lengths, have pipes,
as before. **Still owed:** the author's listening (`build/sound-preview/step2`), and a bench run.

**Recommended: a hybrid of recorded-style grains and a modelled pipe.**

- **Grains, made offline** (`tools/engine_grains.py`, 44.1 kHz, built on `tools/sound_dsp.py`). Single firings, physically informed:
  the blow-down pulse when the exhaust valve opens (fast rise, chirped decay), combustion noise, and a diesel's knock. A set per
  cylinder size (3), ignition (spark, compression) and load (3), with about eight takes each, a few to 80 ms long. That is a few
  hundred kB of assets. If the author wants, they can be seeded from CC0 recordings of single firings, with each source and licence
  written in the docs.
- **The pipe, modelled in Java.** Each outlet's run is a digital waveguide: a delay line as long as the run in blocks, a lossy
  reflection at the open end, and the muffler as a filter inside the loop. Run length then becomes a real resonance with its comb,
  which the ear hears at once. Outlets that share a run share the waveguide. A grain is struck into its outlet's pipe on every firing,
  never the same take twice in a row, and varied in strength and timing as now. This replaces the fixed 86/172/345 Hz resonances.
- **Glue.** A low mechanical bed at crank order, and radiation from the tailpipe (the mouth passes highs and loses some lows).
- **Rate and cost.** 32 or 44.1 kHz, so the crack is crisp. `EngineNotePerfTest` sets the budget (three notes and the room voice
  within a set share of one core).
- **What stays.** Firing order, banks, outlets, `EngineSound` sync, `EngineChorus` rules, settling back, mufflers and casing. Only the
  sound source inside the synth changes.

Rejected alternatives:

- **Recorded engine loops cross-faded by rpm and load**, as racing games do. They can't follow the build (firing order, banks, pipe
  length), which is the point of the mod, and the licensing is hard.
- **Keep tuning the current synth.** It is cheap, but it stays at the ceiling of point 5.

### Step 3: One-shots join the engine (medium)

**Built, 2026-11-26.** How it works: [DESIGN.md](DESIGN.md), "One-shots in the note". Where it differs from the list below:

- A backfire or spit in the note goes out of the outlet that fires next, not the one whose flame set it off, and is heard
  up to about a quarter of a second after the flash, as the stream is made ahead of what is seen.
- The level pass was set against offline renders of the note. Nothing was measured in a capture from the game, and not
  against vanilla's sounds.
- Failure sounds are levelled, not remade from the grains.
- `engine_settle` stays registered, for a note lost part way through its stall.

**Still owed:** the author's listening (`build/sound-preview/step3`), and a bench run.

- **Regenerate** `exhaust_pop`, `exhaust_spit`, `engine_catch` and `engine_stall` from the same grains and pipe model, so a backfire
  from a long run sounds like that run. If the engine has a note, they are rendered inside its stream at the firing they belong to.
  Otherwise the sample plays.
- **Catch and stall become the note.** Cranking, the first ragged firings and the dying-off are played by the synth from the real rpm.
  Then the sound neither doubles up nor leaves a gap. The samples stay registered as the fallback.
- **Level pass.** Every one-shot (failures included) is levelled to a target loudness against the note and vanilla, measured in step
  0's capture. Random pitch stays within about 3%.
- **Asset rules in `SoundAssetsTest`.** Mono, no DC offset, at least 2 ms fade at each end, a peak ceiling.

### Step 4: The world around the sound (medium; each part only if step 0's profile says it is cheap)

**Built, 2026-11-26:** air, room and walls. How they work: [DESIGN.md](DESIGN.md), "The world around the note". Where it
differs from the list below:

- Moving engines get no shift of ours: Sable already gives every sound on a moving structure a Doppler shift.
- Step 0's bench profiles no CPU cost, so cost was measured by a unit test instead (`NoteSurroundingsTest`).
- The room is felt for by rays, and one reflection pattern serves every engine. Recorded sounds and loops get none of the
  three.

**Still owed:** the author's listening, and a bench run (its pads are out of doors, so it exercises the air only). No
renders were made for listening. Not tried on a moving structure.

- **Air.** A low-pass that closes gently with distance, on top of today's dulling. Far off, only the boom carries.
- **Room.** About once a second, the blocks around the listener are sampled. Indoors gets a short early reflection, outdoors stays
  dry.
- **Walls.** One ray per note per second from the player to the source. A wall in the way muffles the note.
- **Moving engines** (Aeronautics contraptions). A small Doppler-style pitch shift from relative speed.

### Step 5: Bearable in a base, re-checked in the game (small)

The 16-engine room and a long soak with the player inside it are captured and audited against round one's rules: the ceiling,
settling back, no repetition, and warnings never lowered. It is also checked against vanilla's level, so the mod doesn't drown out
the game.

**Built, 2026-11-26:** the bench's `soak` scene ([SMOKE.md](SMOKE.md), "The sound bench"). Warnings are not measured: the
capture could not tell one from the engines. Nothing in the many-engine rules touches a warning's sound.

**Full run `20261126-211117`** (with the deeper voice for big engines, [DESIGN.md](DESIGN.md), "Deep engines"): no
underruns, gaps, faults or problems in any scene.

- *The soak:* sixteen firing, 2.7 dB above one of them from the same spot (limit 6), 0.8 dB down once settled and 1.2 dB
  down after pacing through it. No second of it came back (0.665 at the closest, limit 0.97). Its loudest was 4.1 dB under
  the minecart. The room heard from outside was 0.9 dB under its one engine.
- *Level:* this recording is about 14 dB hotter than run `20261126-174506`, the minecart too, so compare engines with the
  references and not between runs. Against the minecart they moved by about 2 dB or less.
- *Clicks:* counted again with the recording turned down 14.2 dB to match, every scene has as few as the earlier run or
  fewer, but for `turbo_i4`: 219 where there were none. They come from the pre-ignition spits, which the note has played
  since step 3 where the earlier run played the recording about 107 times. Offline, a note without spits is flagged
  nowhere and the same note with them is. So they are the spit's own bright edge, not a fault in the stream. Whether it
  should be so sharp is for the author's ear.
- *Over budget:* the sound thread took 2.72% of a core in the soak, over the roadmap's 2% (six engines rendered at
  once; the room from outside took 1.97%). Fixed since, below.
- *Walls and rooms:* one, two and three walls read 3.3, 7.5 and 9.8 dB down, but with the walls gone the engine was still
  4.8 dB down, so much of that is the engine settling over the scene, not the wall. Indoors read 0.9 dB down with the
  same brightness: the bench shows next to nothing of the room's reflection.
- Not looked into: the rotary's lag figure (-241 ms; a rotary has no cam to keep time by).

**Back under budget, 2026-11-26** (the soak alone, `--scenes soak`, so no reference or room figures):

- *Cause:* between a slow engine's firings, filters in the note decayed into subnormal numbers, which are very slow to work
  out; two of an idling single's sat there 88% of the time. And `Math.tanh`, which Java works out in software, was clipping
  every sample of every note.
- *Fix:* filters drop to zero far below hearing (`Denormals`), the soft clip and the undertone's two sines are read from
  tables within a tenth of a 16-bit step (`NoteMath`), filter coefficients are worked out only when their input moves, and
  a boom nothing has struck is skipped. Everything but the tables left an offline render of idling singles bit-identical.
  `NoteMathTest` and `NoteSubnormalTest` hold it.
- *Measured:* run `20261126-214515` before, 2.81% of a core and 241 ns a sample; run `20261126-220258` after, 1.86% and 160
  ns. Offline, a single's note went from about 138 to 80 ns a sample.
- *Little room to spare:* the budget is over the whole scene, quiet start included. With all sixteen running, the phases
  read 1.95 to 2.18% (3.05 to 3.14% before), by the clock; the median read there cost about 115 ns a sample, and slow reads
  make up the difference. A run in between, on a busier machine, read 2.00% and was not to be trusted (its sync claps
  disagreed by 204 ms).
- The after run had one dry spell, all four streams at the same instant (the run before had one in one stream). Its
  cause was not looked into.

## Tests that come with it

| Step | New tests |
|---|---|
| 0 | `sound_audit.py` self-test on made-up faults (a gap, a click, a stair, a late onset); the bench world in the smoke harness |
| 1 | a throttle step heard within the queued time; no per-tick gain stairs in the PCM; handover hysteresis; spool pitch continuous beyond 2×; the room voice falls silent once its engines stop calling in (the known bug above) |
| 2 | waveguide pitch follows run length (stub to long run in semitones); grain takes never repeat back to back; perf budget; existing `EngineNoteVoiceTest` rules kept or moved by hand with the author's say-so |
| 3 | one-shots inside the stream land on their firing; catch and stall have no gap or overlap with the note; asset rules |
| 4 | occlusion and room cost under budget; filters stay finite |
| 5 | the 16-engine level limit and the soak, from the capture |

## What the author decides

1. **Step 2's approach**: the grain-and-pipe hybrid (recommended), or keep tuning the current synth.
2. **Real recordings**: fully synthetic grains, or seeded from CC0 recordings.
3. **Versions**: steps 0 and 1 are fixes to 2.4's rework. Steps 2 to 4 go in 2.4 or 2.5 (no new version numbers).
4. **Budget**: sample rate and CPU share for the note.
5. **References**: which real engines to compare against in step 0's A/B.

## Size and order

| Step | What | Size | Needs |
|---|---|---|---|
| 0 | in-game capture and audit | small to medium | none; do first |
| 1 | latency, smooth gain, spool in the stream, handovers, source at exhaust | small to medium | 0 to measure |
| 2 | grains and waveguide pipe | large | 0, 1; author's choice |
| 3 | one-shots from the same model, catch and stall in the note | medium | 2 |
| 4 | air, room, walls, Doppler | medium | 0's profile |
| 5 | base-scale check | small | 0 |
