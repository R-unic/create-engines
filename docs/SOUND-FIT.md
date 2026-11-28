# Fitting the references: choppiness and clipping

Audit of 2026-11-27, on the `open-garage` working tree. Nothing in the sound was changed. How the note works now is in
[DESIGN.md](DESIGN.md); the bench that records the game is in [SMOKE.md](SMOKE.md), "The sound bench".

The rule this audit holds the sound to: **engine audio never sounds choppy and never clips.** Today it can do both.

All figures are measured, not heard. Renders are of the synthesizer offline; the bench run quoted is an older one.

## 1. What was found

1. **At the speeds the game runs, the note is a row of separate bangs.** With default settings an engine turns at up to
   256 RPM. Firings a second are `cylinders × RPM / 120`, so a single at 256 fires 2 times a second and an inline four at
   32 fires once. Between those firings our level falls to nearly nothing. Real engines beating just as slowly do not.
2. **Earlier tuning was heard at speeds the game does not reach.** The offline harness of the last rounds rendered
   "idle", "mid" and "high" at 150, 420 and 1100 RPM. Only a pack or a setting allows the last two.
3. **The engine's own stream can hard-clip.** The synthesizer stays under full scale, but blower whine and room echo are
   added after it and cut off flat at the 16-bit limit.
4. **The stream has run dry in the game.** The last bench soak (2026-11-26, before the continuous note) logged
   4 underruns of 72 to 128 ms.

## 2. Measurements

**Fall** is the spread of a 20 ms loudness envelope (95th less 5th percentile, above 30 Hz). **Quiet** is the share of
time more than 25 dB under the loud parts. A small fall and no quiet time is a continuous sound.

### Ours, at game speeds, full load

| Engine | RPM | Firings/s | Fall dB | Quiet |
|---|---|---|---|---|
| single | 32 | 0.5 | 143 | 86% |
| single | 256 | 2.1 | 47 | 66% |
| inline four | 32 | 1.1 | 67 | 75% |
| inline four | 64 | 2.1 | 47 | 61% |
| inline four | 128 | 4.3 | 28 | 19% |
| inline four | 256 | 8.5 | 21 | 0% |
| V-twin | 256 | 4.3 | 35 | 30% |
| crossplane V8 | 32 | 2.1 | 49 | 55% |
| crossplane V8 | 64 | 4.3 | 29 | 16% |
| crossplane V8 | 256 | 17 | 11 | 0% |
| V12 | 256 | 25.6 | 6 | 0% |

Load makes almost no difference. Diesel, six and rotary follow the same line: the firing rate decides it.

### Real engines

New slow references from Wikimedia Commons (CC BY 4.0), used only to measure; none ship. Beats a second is estimated
from the envelope and may be off by a factor of two on a lumpy engine.

| Recording ("WWS …") | Beats/s | Fall dB | Quiet |
|---|---|---|---|
| Stationary engine | 1.4 | 20 | 0% |
| Witte combustion engine | 5.3 | 16 | 0% |
| Lanz Bulldog hot-bulb tractor, in operation | 5.7 | 14 | 0% |
| Hot-bulb engine, exhaust pipe | 6.5 | 16 | 0% |
| Ursus C-328 tractor engine | 6.7 | 9 | 0% |
| Seffle boat engine | 7.1 | 10 | 0% |
| Ursus C-45 tractor | 7.4 | 10 | 0% |
| Perkun combustion engine | 8.5 | 10 | 0% |
| Ruston diesel | 13 | 10 | 0% |

Car idles measured the same way (two crossplane V8s, a 458, an E46 six, a WRX flat four) fall 3 to 10 dB with no quiet
time. Also fetched: a Model A hot-bulb boat engine and a Maserati exhaust. A John Deere tractor and a police motorbike
were refused by Wikimedia's rate limit.

These are outdoor and engine-room recordings, so distance, reflections and background fill some of their gaps. Treat
the figures as what a listener expects, not as the engine alone.

### Where the gap is, by band

Fall in dB (95th less 10th percentile):

| | 30–120 Hz | 120–400 | 400–1500 | 1500–6000 |
|---|---|---|---|---|
| Lanz Bulldog | 18 | 17 | 9 | 12 |
| Ursus C-45 | 13 | 9 | 6 | 5 |
| Stationary engine | 14 | 19 | 17 | 20 |
| our single, 256 | **58** | **40** | **34** | **48** |
| our inline four, 128 | 26 | 29 | 25 | **42** |
| our inline four, 256 | 20 | 19 | 17 | 30 |
| our crossplane V8, 256 | 9 | 12 | 11 | 23 |

Slow engines are short everywhere, most in the top band. That top band is where a real engine's machinery is: valves,
injectors, gears, chain.

### Clipping

One engine at 256 RPM, V8, full load, 20 s, with backfires, through its whole stream:

| Blower level | Room echo | Samples at the 16-bit limit |
|---|---|---|
| none | none | 0 |
| none | full | 83 |
| 0.1 | none | 56 |
| 0.1 | full | 167 |
| 0.3 | none | 1217 (0.19%) |
| 0.6 | none | 5465 (0.85%) |

A blower beside the listener asks for about 0.1 to 0.15; one much nearer than its engine asks for more.

## 3. Audit by part

| Part | State | Choppy or clipping risk |
|---|---|---|
| Firing grains, pipes, resonances (`EngineNoteSynth`, `ExhaustPipe`) | Continuous only from about 15 firings/s | **Choppy** below |
| Roar and chambers (`NoteSystem`) | Follows the recent signal, release capped at 0.2 s | **Choppy**: dies between slow firings |
| Mechanical bed (`NoteUndertone`) | Weight 0.02, scaled by RPM/200 | Too faint to fill anything, faintest when most needed |
| Level and soft clip (`NoteExit`) | `tanh`; up to 1.8% of samples are well into it | Saturation, not flat clipping. A fully muffled voice ends in a hard clamp (rare) |
| Stream mix (`EngineNoteMix`) | Clamps the note, then adds whine, then air and echo | **Hard clipping**, measured above |
| Shared room voice (`EngineRoomMix`) | Soft limiter above 0.8 | Good: the pattern to copy |
| Bangs (`NoteBangs`) | Soft limiter only while a bang sounds | A step of up to a few percent as it switches on or off, on a loud note |
| Stream (`EngineNotePcm`, four chunks of 50 ms) | A tick longer than about 150 ms empties it | **Dropout**, then a restart |
| Several streams at once | Gains held by `EngineChorus.ceiling` | Sum in the game's mixer **not measured** |
| Blower voices (`BoostWhine`) | Bounded by `tanh`, fades in and out | Only through the stream mix |
| Loops (starter, whistle, whine, squeal, intake roar, clatter) | Peaks 0.66 to 0.85, seams small, volume ramps | None found. `engine_clatter` is registered and nothing plays it (the ID stays) |
| Recorded one-shots | No file has a flat-topped sample | Peaks **over full scale between samples** (up to 1.27) can clip when resampled: `head_warp1`/`2`, `rod_thrown1`/`2`, `turbo_fail1`/`2`, `apex_fail1`/`2`, `seize_warning1`/`2` |
| `cylinder_fire` (fallback with the note off) | Peaks 0.86 to 0.93, 0.55 s each | Many engines can stack them; not measured |
| Advancement sting (`StingSynth`) | Level 0.55 with fades | Not rendered here |

## 4. How to fit the references

In the order I would build them. Each keeps firings on the crank's time, so flashes and sound still agree.

### 4.1 Give a turning engine its other events

A four-stroke does something every half turn, not only when it fires. Per cylinder, per 720°:

| When | Event | Sound |
|---|---|---|
| power | firing (what we have) | |
| exhaust stroke | the piston pushes the rest out | a long soft breath down the same pipe |
| intake stroke | the cylinder gulps | a low, dull puff at the intake |
| compression | compression chuff | a swell that stops at the next firing |
| both valves | seating | short dull taps, 1.5 to 6 kHz |

All are tied to the cam, so they stay in time and tell engines apart (a V-twin's gulps lope like its firings). A single
at 256 RPM goes from 2 events a second to about 8 to 12. This is the largest gain, above all in the top band.

### 4.2 A floor that follows the revs, not the last bang

While the crank turns there is a steady bed: gear and chain mesh (a few quiet tones that follow RPM), bearing and oil
rumble below 150 Hz, and gas moving in the pipes. Its level hangs from the engine's usual firing level (about 15 to
20 dB under the peaks), so it holds however long the gap is. Fade it with `combustion` and RPM so a stall still ends
in silence.

Keep it **steady and dull**. The author heard sparse wavering noise at low speed as "bitcrushed", so no flutter on the
bed, and no white top.

### 4.3 Let slow firings ring longer

Below about 8 firings a second, lengthen the roar's release past 0.2 s, the boom, and the thump (now 36 ms), in
proportion to the gap. Lanz-like engines carry most of their gap between 30 and 400 Hz. Do not lengthen the pipes'
ring: a long ring was already heard as reverb.

### 4.4 Never clip

- One limiter, last, in every stream: after whine, air, echo and bangs. `EngineRoomMix.limit` already leaves anything
  under 0.8 untouched, so apply it always rather than only while a bang plays.
- Mix in floats and convert to 16 bits once.
- Replace the muffled path's `Math.clamp` with the same limiter.
- Have the generator scripts under `tools/` hold every recording to a true peak of −1 dBFS.
- Measure the sum of several streams on the bench, and lower `LOUDEST` only if it clips.

### 4.5 Never drop out

First find what the bench's underruns coincide with. If they are real game hitches, a fifth queued chunk buys 50 ms
and costs 50 ms of throttle lag. The author should choose that trade.

### 4.6 Only if 4.1 to 4.3 are not enough

Map shaft speed to a faster "sound speed" so a slow engine fires more often than it turns. It would fit car recordings
best, but flashes and bangs would no longer match, and a single would stop sounding like a single. **The author's
decision**; I do not recommend it yet.

Even with all of the above, an engine below about 64 RPM is a slow machine: it should sound like one turning over, with
distinct beats over a continuous mechanism, not like a car idling.

## 5. Targets and tests

Proposed, for the author to accept or change:

| Measure | Target while the crank turns |
|---|---|
| Fall, 5 firings/s and up | at most 20 dB |
| Fall, under 5 firings/s | at most 25 dB |
| Quiet time | none |
| Samples at the 16-bit limit, any stream, anything added | 0 |
| True peak of a shipped recording | at most −1 dBFS |
| Stream underruns in a bench run | 0 |

- New unit tests render at **32, 64, 128 and 256 RPM** and check the first four. Several fail today, so they land with
  the fix.
- Add fall, quiet time and rail samples to `tools/sound_audit.py`, so the bench reports them.
- Render listening clips at game speeds only.
- The existing distinctness and muffling tests limit how much fill is possible. If one goes red, the fill is wrong, not
  the test. A bed that hides the firing order is too loud.

## 6. Cost and rules

- The sound thread was at 1.86% of a core against a 2% budget before the continuous note added about a quarter to its
  cost. More events and a bed will not fit without measuring: reuse grains, make the bed one filter for the whole
  engine, and run the soak.
- Existing engines would sound different, so this needs an entry under Exceptions in the release record, with the
  author's approval.
- `EngineNoteSynth` is already over 800 lines. The new events and the bed go in files of their own.

## Not done in this audit

- Nothing was listened to.
- The sound bench was not run (it plays for about 30 minutes through the speakers).
- The sum of several voices, the sting and stacked `cylinder_fire` were not measured.
- No change was prototyped, so section 4's gains are expected, not shown.

## Sources

- Recordings: Wikimedia Commons, the "WWS" (Work With Sounds) series and the car clips named above.
- [Pulse-train resonator engine synthesis](https://arxiv.org/html/2603.09391) (preprint): noise excitation and a
  minimum idle gain keep the gaps filled.
- [Idle noise of a four-cylinder diesel](https://saemobilus.sae.org/papers/noise-generating-mechanism-idling-a-four-cylinder-line-diesel-engine-2003-01-1720)
  (SAE 2003-01-1720, from its summary): mechanical noise, mostly the valve train, dominates at idle.
