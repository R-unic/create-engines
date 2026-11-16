# Advancement redesign (draft, before 2.0)

Status: **approved 2026-11-16. Step 1 (the 1.x half) is built. Step 2 (renamed IDs, the migration table, deleting the hidden rows, regrouping the tab tree) is 2.0's responsibility, listed in docs/ROADMAP.md under 2.0, and is not started.**

Where things stand: the 1.1 declutter is done. `CEAdvancements.SHOWN` lists 56 advancements in the tab. The other 54 keep their IDs and
criteria but have no display, so the stability promise holds. The redesign starts from that and decides what the tab is *for*.

## The goal

An advancement is a small celebration the player has earned. It should leave them content: "I made that, and it worked." Not
a checklist, not a tutorial, and never a telling-off.

## Principles

1. **Mistakes are part of the fun.** Discovering an error earns an advancement too: the moment the player thinks "I messed
   something up, but here's a cool advancement!". Stall Out, That's Not Air, Fouled Up and the like stay in the tab, worded
   as a wink and never as a telling-off, and they must name the thing the player just learned. The mod's drama stays forgiving.
2. **Every shown advancement is a moment.** Something the player can see, hear or feel in the world when it fires: a first
   idle, a first turbo whistle, a twelve coming up to speed. No "own the item" rows; no rows that restate a recipe.
3. **There is always a next rung in reach.** Each track is a ladder of five to seven rungs, each one a little harder than the last
   and reachable by building something, not by grinding. The description of a rung ends by naming what the next one asks for.
4. **Pace the first hour.** A new player gets a rung every few minutes for the first ten or so (crankshaft, first cylinder,
   starter, "It's Alive!", first fuel, first exhaust, first sump), then the tracks open and set their own pace.
5. **Toasts everywhere, chat for the ones that matter.** Every shown advancement gets the toast. Most also announce in chat.
   Only the insignificant rows (small stepping stones and minor discoveries) stay chat-quiet, so a chat line still means
   something. Each row gets an `announce` flag in the table, and the keep list marks which are quiet.
   Goals and challenges also get a short synthesized engine-note sting (approved): a quick rev-up through the existing sound
   path, quieter than the engine, never repeated within a minute.
6. **One keepsake, at the very top.** The moment is the reward for everything else. Chief Engineer alone hands over a
   keepsake, cosmetic only, so nothing can disturb the progression vision. Proposed: an engraved Maker's Plate (the block
   already exists) carrying the player's name and a Chief Engineer mark. The author picks the item.
7. **Progress is visible.** Capstones keep one criterion per milestone, so the screen shows how far along the track the player is.

## Tracks and keep list (draft for approval)

66 rows shown, up from 56: none of the earlier shown rows left, and ten were added. 44 stay hidden (and are
migrated, then deleted, in 2.0). ● announces in chat, ○ is toast-only (an insignificant stepping stone or minor discovery).
**NEW** is a row that is hidden today. 😬 marks a mistake row. ✦ marks a goal or challenge that gets the sting.
IDs below are today's; 2.0 may rename them.

| Track | Rows |
|---|---|
| **First Light** (root `engineer`) | crankshaft ○, cylinder ○, starter ○, alive ● ✦, warmed_up ○, sump ○, stalled ● 😬, flooded ● 😬 |
| **Builder** | camshaft ○, pipework ○, four_cylinders ●, eight_cylinders ● ✦, twelve_cylinders ● ✦, sixteen_cylinders ● ✦, vee_engine ● ✦, flat_engine ● ✦, coupled_cranks ● ✦, valley_cam ● ✦, rotary_engine ● ✦, bank_builder ○, engine_casing ○ **NEW**, **master_builder** ● ✦ |
| **Fuel** | diesel ●, spark_plug ○, gasoline ●, ethanol ●, ethanol_boost ● ✦, dual_fuel ● ✦, heat_exchanger ●, combined_cycle ● ✦, smoke_signals ● 😬 **NEW**, fouled_plug ○ 😬 **NEW**, thinned_oil ○ 😬 **NEW**, **closed_loop** ● ✦ |
| **Power** | heavy_duty ● ✦, redline ● ✦, steam_beater ● ✦, steam_titan ● ✦, overloaded ● 😬 **NEW**, turbocharger ●, big_boost ● ✦, blow_off ○ **NEW**, twin_turbo ● ✦, supercharger ●, twincharged ● ✦, anti_lag ● ✦, precision_engine ● ✦, **power_plant** ● ✦ |
| **Tuning** | dialed_in ● ✦, governed ● ✦, engine_display ○, stampede ● ✦, dyno_run ●, **fine_tuner** ● ✦ |
| **Driveline** | transmission ○ **NEW**, alternator ○, flywheel ○, every_gear ● ✦, **full_driveline** ● ✦ |
| **Service** | oil_kept_fresh ● ✦ **NEW**, cool_running ● ✦ **NEW**, preheated ○ **NEW**, **service_record** ● ✦ |
| **Top** | **chief_engineer** ● ✦ + keepsake |

Notes on the choices:

- The six mistake rows (stalled, flooded, smoke_signals, fouled_plug, thinned_oil, overloaded) are each worded as a wink and name
  what the player just learned (rich runs foul plugs and thin the oil, no air underwater, more torque than rating stalls).
- The six capstones feed Chief Engineer. Instruments (display, stampede, dyno) sit under Tuning rather than a track of their own,
  because a track with no capstone would be a dead end.
- Hidden on purpose, still earned and migrated: the "obtain" rows (cylinder_head, sugar_mash, glycerin, used_oil), the
  Timing Tool, MAF, governor, precision part and oil rows, the extra rotary rows, the sub-rungs that only feed a capstone, and
  the Auto and Reverse rows.
- **Transmission: kept** (decided). It is the car-like part (13 gears, reverse, Auto), which the Rotation Speed Controller does not
  give. Its first-shift row is shown quietly, and Every Gear is a goal with the sting. The 2.0 question in the roadmap is closed.
- The quiet rows are the ones a server would otherwise fill chat with in a new player's first ten minutes.

## What changes in the code

- `CEAdvancements.SHOWN` is regrouped by track, and each row carries an `announce` flag, off only for insignificant rows. The mistake rows stay shown.
- A row's type (goal or challenge) decides the sting, so it is not a new column.
- The sting reuses the engine-note synthesis; it needs the stability tests that guard the sound thread (soak and hostile input).
- Tests: every shown row has a title, a description and a path to the root; every ID from 1.0 still
  loads (rule 8); capstone criteria all resolve.
- Datagen: `./gradlew runData --offline`.

## 2.0 and IDs

Decided: the hidden advancements are deleted in 2.0, and 2.0 is where IDs are allowed to change, so the redesign can rename,
merge and regroup rows freely. **Old progress is migrated** (decided): a player who earned an old row is credited on the new
row that replaces it, silently, with no toast and no sting, so nobody is told about something they already did. The map from old
ID to new ID(s) lives in one table, and a test checks that every 1.x ID (shown or hidden) has an entry. Merged rows credit the new
row when any of its old rows was earned; capstones credit per milestone, as they do now.

## Decisions for the author

- Approve the principles, the track list and the keep list (the 56 shown rows, plus or minus any you name), and mark which are
  chat-quiet.

## Built in step 1 (1.x)

- `CEAdvancements.SHOWN` holds the 66 rows, and `QUIET` the 18 that are toast only (crankshaft, cylinder, starter, warmed_up,
  sump, camshaft, pipework, bank_builder, engine_casing, blow_off, thinned_oil, fouled_plug, engine_display, alternator,
  flywheel, preheated, spark_plug, transmission). Every other shown row announces in chat.
- The sting: `StingSynth` (an engine winding up, from the same note synthesizer the engines use), `StingSound` (client voice),
  `AdvancementSting` (server: an earn event on a goal or challenge that shows a toast sends one small packet) and `StingGate`
  (one a minute, a challenge may follow a goal). It follows the engine note volume setting, so zero mutes it.
- The keepsake: Chief Engineer's reward is a loot table that gives a Maker's Plate named "Chief Engineer"; whoever places it is
  stamped as the builder.
- Tests: `AdvancementTabTest` (quiet and loud rows, mistakes shown, the keepsake only on Chief Engineer), `StingSynthTest`
  (bounded, fades, deterministic whatever the read size, one reused buffer, hostile read sizes) and `StingGateTest`.
- Not done: the tab layout still hangs from today's parents; regrouping the tree by track is part of the 2.0 renaming, and the
  sting has not been heard in a running client.
