# The 2.4.0 trailer: plan

The composition the author picked for 2.4 "Running Hot" (author, 2026-11-26). Not built yet. [SMOKE.md](SMOKE.md), "The trailer",
has the harness and the rules every composition keeps; this page is only what is new for this cut. Once it is cut and posted, the
composition part of SMOKE.md is rewritten to describe it and this page is deleted.

## What the author chose

- **Story: the sound rework and the finished wear and parts system are the draw.** The trailer is mostly about hearing engines
  and wearing them out, with **a brief heat section**. It is not a tour from one cylinder to a factory, as the 2.3 cut was.
- **Long form opens on steam, then the title.** A close shot of a critically hot block steaming, its goggles reading Critical, and a
  gasket blowing red, in game sound only; then a hard cut to the title.
- **The vertical Short opens on a pull-back from an exhaust mouth.** Tight on a barking exhaust opening (the note is heard from there
  now) and pulling back fast to show the whole engine running. No title card.
- **Extras:**
  - **Clear the wear debts.** A wear pad shows the four gaskets, the Service Kit and Apex Seals, so their `later` lines in
    `src/smoke/trailer-parts.txt` name a shot.
  - **Light against heavy rods.** Close on the crank throws: a light rod's engine surges visibly with each firing, a heavy one runs
    smooth. The rods' lines stop being `skip`.
  - **The Radiator's heat uses.** An Encased Fan blowing at a radiator, and a basin on top running a heated recipe.

Not picked: sound through walls and indoors as its own shot; the "one engine pushed hot" story; split-screen pairs.

## The long cut

About 200 seconds, inside the 240-second budget. Lengths are whole half bars (multiples of 1.5 s). New shots are in **bold**.

| Chapter | Pieces (seconds) | Notes |
| --- | --- | --- |
| Cold open | **heat_critical** 4.5 (cold, engine) | Steam, goggles at Critical, a red gasket blow-out. |
| Title | title_sequence 6 | |
| Hear it (main) | cold_start 4.5, **exhaust_mouth** 4.5, exhaust_stub 4.5, exhaust_long 4.5, **v8_crossplane** 4.5, **v8_flatplane** 4.5, muffler_off 3, muffler_on 3, **muffler_bank** 4.5, **diesel_knock** 3, inline6_turbo 4.5, blown_i6_blower 3, **far_boom** 4.5, **stall_load** 4.5, headphones_on 3 | All `engine`. The catch's coughs; the note from the pipe; pipe length; crossplane burble against a flat-plane; one bank muffled; a diesel's knock at idle; the turbo's whistle climbing as it spools; the supercharger's whine; the camera drawing away until only the boom carries; a stall by load thudding to a stop. About 64 s. |
| Wear it out | **rods_light_heavy** 6, **wear_parts** 4.5, **wear_gasket_blown** 3, **wear_apex** 3, **wear_turbo** 3, **gasket_saw** 3, gasket_line 6 | The two rods; the gasket tiers and a Service Kit; failures readable from range; a belt sawn into rubber gaskets; the copper gasket's sequenced assembly. About 28 s. |
| Running hot (brief) | running_hot 4.5, running_cooled 4.5, **radiator_fan_basin** 4.5 | Steam and lost power; radiators fitted; a fan on one, a basin heated from it. |
| New builds | **bank_wx** 6, rotary_orbit 3 | First person, the Bank Builder lays a W, then an X. |
| Everything else | The other existing shots, 1.5 each, `quiet` | A fast montage of what earlier versions added (layouts, tuning, instruments, electrics, service bay, casings, factory, car, fuels, refinery), under one caption. About 40 s. |
| Finale | night_pull 10.5 (engine), factory_wide 7.5 | Revs and lift-offs at dusk; the calls to action come in over factory_wide, with no separate tail (SMOKE.md's outro rule). |

Every existing shot must stay in `edit.txt` exactly once (`TrailerCoverageTest`), so the older ones go in the montage rather than
being left out. A shot that no longer earns its place is removed from `ShowcaseRun`, and its parts' lines are pointed at another shot.

Captions for the new shots (one line, true, plain ASCII; to be checked against the shots):

- heat_critical: Run it past critical and the gasket lets go.
- exhaust_mouth: Every note is made from the engine's own exhaust.
- v8_crossplane: A crossplane V8, one exhaust a bank, burbles.
- v8_flatplane: A flat-plane V8 does not.
- muffler_bank: Muffle one bank and only its firings go dull.
- diesel_knock: No spark plug: compression ignition knocks at idle.
- far_boom: Far off, the crack fades and the boom carries.
- stall_load: Stalled by its load, it thuds to a stop.
- rods_light_heavy: Light rods rev sooner and run rougher; heavy ones smoother.
- wear_parts: Four gaskets, a Service Kit, and parts that wear.
- wear_gasket_blown: Wear mode: a blown gasket shows from across the room.
- wear_apex: A rotary's apex seals spring out when they fail.
- wear_turbo: A worn turbo loses boost, and can fail outright.
- gasket_saw: A Mechanical Saw cuts a belt into rubber gaskets.
- radiator_fan_basin: Blow a fan at a radiator; heat a basin on top.
- bank_wx: The Bank Builder now lays W and X engines.

Chapters: Hear it, Wear it out, Running hot, New builds, Everything else, After dark. Thumbnail: `heat_critical`.

## The vertical Short

About 64 seconds, inside the 90-second limit. It ends on the end piece over `far_boom`, which loops back into the opening's close
shot of the exhaust.

```
exhaust_mouth      4.5   engine            | Every note / from the pipe
exhaust_stub       3     engine            | Short pipe / barks
exhaust_long       3     engine            | Long pipe / booms
v8_crossplane      4.5   engine   flash    | Crossplane / burble
muffler_bank       4.5   engine            | Muffle / one bank
inline6_turbo      4.5   engine            | Turbo / spools up
rods_light_heavy   4.5   fit zoom=1.6 flash | Light rods / or heavy
wear_gasket_blown  4.5   flash             | Run it / to failure
gasket_line        4.5                     | Press a / copper gasket
running_hot        4.5                     | Too / hot?
radiator_fan_basin 4.5                     | Fit a / radiator
bank_wx            4.5   fit      flash    | Now build / a W or X
night_pull         6     engine   flash    | Lift off. / Bang
far_boom           7.5   fit      end
```

Vertical thumbnail: `exhaust_mouth`. Teaser (`short_shots`): exhaust_mouth "Hear every pipe", v8_crossplane "Crossplane burble",
wear_gasket_blown "Wear it out", bank_wx "Build a W or X".

## Work it needs

- **New pads and shots** in `client/ShowcaseRunFeatures` (or a new class beside it; `ShowcaseRun` is on the giant-files list): the
  16 new shots above, with the shot list reordered to the new cut so the light still only moves from day to dusk.
- **Wear mode on** in the trailer's private game folder (`TRAILER_CONFIG` in `record.py`). Failures cannot be waited for, so the
  harness sets the wear and failed state by reflection, as `running_hot` already holds its block at 190 degrees. The failure and its
  look and sound are the mod's own; SMOKE.md says what the harness forced.
- **Camera moves:** a fast pull-back for `exhaust_mouth`, a long dolly away for `far_boom`, and a close, slow shot of the crank throws
  for `rods_light_heavy`.
- **Captions, edits and parts:** `captions.txt`, `edit.txt`, `vertical.txt`; in `trailer-parts.txt` the gaskets, Service Kit, Apex
  Seals, both new rods and the Radiator name their new shots.

## Before recording

- The sound rework is the author's to tune by ear and is not signed off ([ROADMAP-3.0.md](ROADMAP-3.0.md), 2.4 "The sound rework"),
  and the blower sounds are still being listened to. The engine shots are recorded after that, or recorded again.
- The author watches both cuts before they are posted.
