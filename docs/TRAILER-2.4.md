# The 2.4.0 trailer: plan

The composition the author picked for 2.4 "Running Hot" (author, 2026-11-26). Built on 2026-11-27: the pads, shots, cues,
captions and both edits are in. The new shots were checked in the game the same day, from stills and the log. **Nothing has
been recorded or cut, and `stall_load` does not work yet.** [SMOKE.md](SMOKE.md), "The trailer",
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

## Where the build differs from the plan above

The edits themselves (`tools/trailer/edit.txt`, `vertical.txt`) now own the order and the lengths; the tables above are the plan.

- The long cut plans to 184.5 s (181.5 s without the Aeronautics car), under the "about 200" above.
- `headphones_on` is filmed by the turbocharged six: the V16 it stood by is now cold until the montage's `heavy_starter`.
- `rods_light_heavy` is one engine and one camera, with the rods changed half-way, not two engines side by side.
- The title shot is in daylight (it was golden hour), so that the light still only moves on.
- In the vertical cut, the rods, the blown gasket, the hot engine and the last piece are also marked `engine`, to be heard.
- The montage's one caption is "And all that came before, from one cylinder up." (over `first_rises`).

## Open: the stall

`stall_load` does not stall the diesel, so its caption ("Stalled by its load, it thuds to a stop.") is not true of it. The mod
offers Create no more stress than the engine can carry: a bigger load only marks the network overstressed, and the engine runs
on, unloaded (the GameTest `overloadOverstressesWithoutStalling`). A load Create accepts did not stop it either, with the
throttle shut to its lowest as the clutch took up. The mod's heavy "lugged" stall sound plays when an engine that is
overstressed dies of something else. The shot, its caption and its line in both edits wait for the author's choice. Do not
record it as it is.

## The check of 2026-11-27

Seen in stills or the log, in a run with nothing recorded: the goggles reading Critical (188 degrees) and the first gasket
blowing; the second gasket, the apex seals and the turbo failing with their particles; the rotary running again afterwards; the
rods changing (the log's speed ripple goes from 5.6% to 4.3%, so the difference may be hard to see or hear); a belt cut into two
Rubber Gaskets; the fan counted as fan air and the basin making brass; the W and the X laid in first person; the open mouth on
the pull-back's first frame and the engine small at the end of the long dolly; and the V8 firing on all eight about five seconds
after each re-timing.

## Before recording

- Settle the stall (above).
- Not checked: anything heard, any movement between the stills, and whether each caption reads true over its piece. The pieces
  that start later than at first (the rebuilt exhausts, the flat-plane V8, the muffled bank) have not been looked at there.
- The sound rework is the author's to tune by ear and is not signed off ([ROADMAP-3.0.md](ROADMAP-3.0.md), 2.4 "The sound rework"),
  and the blower sounds are still being listened to. The engine shots are recorded after that, or recorded again.
- The author watches both cuts before they are posted.
