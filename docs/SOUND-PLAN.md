# Sound, round two: sounding right in the game (plan, 2026-11-26)

**A plan for the author to choose from. Nothing in it is built.** The first round of the rework is recorded in
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

## The plan, in order

Each step can ship on its own and is measured with step 0's capture, before and after.

### Step 0: Hear the game, not the renderer (first, small to medium)

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

### Step 1: Fix the delivery, same sound (small to medium)

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

- **Regenerate** `exhaust_pop`, `exhaust_spit`, `engine_catch` and `engine_stall` from the same grains and pipe model, so a backfire
  from a long run sounds like that run. If the engine has a note, they are rendered inside its stream at the firing they belong to.
  Otherwise the sample plays.
- **Catch and stall become the note.** Cranking, the first ragged firings and the dying-off are played by the synth from the real rpm.
  Then the sound neither doubles up nor leaves a gap. The samples stay registered as the fallback.
- **Level pass.** Every one-shot (failures included) is levelled to a target loudness against the note and vanilla, measured in step
  0's capture. Random pitch stays within about 3%.
- **Asset rules in `SoundAssetsTest`.** Mono, no DC offset, at least 2 ms fade at each end, a peak ceiling.

### Step 4: The world around the sound (medium; each part only if step 0's profile says it is cheap)

- **Air.** A low-pass that closes gently with distance, on top of today's dulling. Far off, only the boom carries.
- **Room.** About once a second, the blocks around the listener are sampled. Indoors gets a short early reflection, outdoors stays
  dry.
- **Walls.** One ray per note per second from the player to the source. A wall in the way muffles the note.
- **Moving engines** (Aeronautics contraptions). A small Doppler-style pitch shift from relative speed.

### Step 5: Bearable in a base, re-checked in the game (small)

The 16-engine room and a long soak with the player inside it are captured and audited against round one's rules: the ceiling,
settling back, no repetition, and warnings never lowered. It is also checked against vanilla's level, so the mod doesn't drown out
the game.

## Tests that come with it

| Step | New tests |
|---|---|
| 0 | `sound_audit.py` self-test on made-up faults (a gap, a click, a stair, a late onset); the bench world in the smoke harness |
| 1 | a throttle step heard within the queued time; no per-tick gain stairs in the PCM; handover hysteresis; spool pitch continuous beyond 2× |
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
