# Roadmap: 1.0.1 to 2.0.0

Written 2026-11-14, at 1.0.1. Brought up to date 2026-11-17, at 1.3.0 (shipped). This is a plan, not a promise: the
order is firm, the contents of each release are the current best guess, and nothing here has a date.

## Direction

Set by the author on 2026-11-14:

- **2.0.0 is the release where the mod becomes a platform:** parts, fuels and engine types as data, and a public API.
- **Engines that move** come through compatibility with Create Aeronautics, which already turns rotation into motion
  (see 1.4). No drive system of this mod's own, and no mixins into Create's trains.
- **1.x leans into** spectacle, a fuel industry, cosmetic build variety and blueprints.
- **Forgiving, with drama.** The drama comes from the consequences the mod already has. No new ways to fail.
- **Fuel industry means new ways to make the existing fuels.** No new fuels.
- **Reach:** play well with other Create addons, and give pack makers real tools. One Minecraft version and one loader.

## Rules every release keeps

1. **The 1.x promise.** Block, item and fluid IDs, config keys and ranges, advancement IDs and the fuel data-map format
   do not change before 2.0. A release that changes how an existing engine behaves says so in its changelog.
   Whatever a 1.x release adds joins the promise the day it ships: its IDs, its config keys and its file formats
   (column recipes, blueprints). The exception is anything marked **preview**, which may still change until 2.0:
   the `api` package, the KubeJS bindings and the part stats data map, all in 1.4.
2. **Every feature ships whole:** lang entries, and a Ponder page and goggle lines where there is something to
   explain or to read (an effect or a casing variant needs neither). **Every new feature gets new tests:** unit tests
   for anything in the simulation, GameTests for anything that touches the world. An advancement only where it
   marks something meaningful, never one per feature.
3. **Nothing destroys parts or items, and no release adds a new punishment.** Drama dresses up a consequence that
   already exists.
4. **No new tuning dial** unless it replaces an old one. Cosmetic parts never change an engine's rating.
5. **It looks like Create:** chunky models, textures built from Create's own.
6. New settings go through `Tuning`, with defaults that leave the existing unit tests and GameTests unchanged.
7. **Newer Create versions are supported as they come.** Every release is built and tested against the newest
   Create out for Minecraft 1.21.1, and the supported range grows to take it in. If a new Create cannot be supported
   side by side with the old one, the next release moves to the new one and its changelog says so.
8. **Old worlds keep loading.** Before each release a world saved on 1.0 is opened in the new build. A test holds
   the list of every ID and config key that 1.0 shipped, and fails the build if one goes missing.
9. **The stability contract.** No release is tagged until the checklist in [STABILITY.md](STABILITY.md) is signed off:
   tests with every change, the machine gate, and the author's hands-on pass (build an engine, soak, old world).

## At a glance

| Version | Name | What it is | Size | Status |
| --- | --- | --- | --- | --- |
| 1.0.x | Shakedown | Close the open boxes from the 1.0 checklist | Small | 1.0.1 shipped; some boxes still open (below) |
| 1.1 | Light and Noise | Spectacle, louder consequences, advancement declutter | Medium | Shipped as v1.1.0 |
| 1.2 | Dress Parts | Cosmetic build variety | Small | Shipped as v1.2.0 (v1.2.1 fixed the sound-thread memory leak) |
| 1.3 | Refinery | A refinery multiblock for the fuels that already exist | Large | Shipped as v1.3.0, 2026-11-17 |
| 1.4 | Drawing Office | Blueprints, pack-maker tools, Aeronautics and other addons | Large | **In progress** (see its section) |
| 2.0 | Open Block (released as "Open Throttle") | The platform, and the one breaking cleanup | Large | **Built, not tagged** (2026-11-21): the breaking cleanup, both crossovers, the port curve, altitude, the frozen API (1.1.0) and the dye cleanup, the platform (engine types, parts as data, an example addon), battery and part depth, headphones, kerosene and methanol, Jade and the other integrations; the Part 2 gate ran on 2026-11-21 (see `docs/releases/v2.0.0.md`); the hands-on boxes and the tag are the author's |

## 1.0.x Shakedown

Status 2026-11-17: 1.0.1 shipped. From the release records, the rule 8 test exists (`CompatManifestTest` and
`LegacyWorldGameTests`) and the newest Create patch was tested (6.0.11, see `docs/COMPAT.md`). The list below was not
ticked off item by item, so read each line as open unless a later section says it was done. The **Aeronautics
afternoon** is open, and 1.4 needs it first (see 1.4, stage 0).

- A dedicated server with two players: goggles, Display Link, Black Box, sump, starter and advancements all sync.
- Tick time with 20 or more running engines, and particle and sound load on a big engine at default `effectDensity`.
- Late-game output at a 512 RPM Create limit: a boosted V12 plus a heat-exchanger boiler.
- Hear the stall sound in a client run.
- Test on the newest Create patch inside `[6.0.10,6.1)` (rule 7).
- Write the test that rule 8 asks for: the list of 1.0's block, item, fluid and advancement IDs and config keys,
  checked on every build.
- Retake the screenshots and replace the CurseForge gallery copies by hand.
- Settle the question the checklist left open: a remap for Rev Limiters and Lambda Sensors in worlds from
  development builds, or none.
- Look at the things only tests have seen: connected casing, the newer Ponder scenes, an end-to-end boiler, muffler
  loudness.
- **One afternoon with Create Aeronautics installed:** build an engine on a simulated contraption and write down
  what works and what does not. Confirm on Aeronautics' own page which Create version it is built for. This sizes
  the 1.4 work early.

## 1.1 Light and Noise

**Shipped as v1.1.0.** What is below is the plan as written; the changelog in `docs/LISTING.md` is what shipped.

The release that makes a running engine worth standing next to. Nothing here changes a rating or adds a failure:
every item is sound, particles or motion on screen, and the simulation's numbers do not move.

**Spectacle**

- **Engines that rock (built, lighter than first written).** The roughness rating already exists as a number. Show it on
  the parts that already move: pistons and rods slap about in their bores by the rating, a pinch of dust shakes off the
  block, a flywheel calms both. The block itself does not shake.
- **Stack flame.** The crack of flame a rich engine already makes grows into a steady lick from an open exhaust at
  full load.
- **Pushrods.** A cam that sits beside the block, or in the valley of a V, shows its pushrods: one from each lobe up
  to a rocker on the head, rising and falling with the valve. A valley cam visibly works both banks from one shaft.
- **Smoke rings.** A slow single puffs rings from its stack at idle.
- **Intake roar (built, not yet heard in a client).** A sound at the intake mouths: the gulp of air being drawn in. It is a sound only, so no rating
  or airflow changes. It is **never constant**. It swells with throttle and boost and follows the engine's own
  pulses, so it is loudest in a hard pull and under a turbo or blower. It fades at idle and is silent when the
  engine is stopped or has no open intake mouth. It is positional, heard from the mouth and not from the block, and it
  is one voice per engine, not one per cylinder. The Muffler does not touch it, since the Muffler is for the exhaust.

**Louder consequences.** Every cause the goggles can already name gets a show of its own. The cost of each stays
exactly what it is today.

| Already happens | Today | Added |
| --- | --- | --- |
| Too rich | Black smoke, a crack of flame | Stack flame under load (built) |
| Too lean | Lost power | A spit of flame back through the intake mouth |
| Fouled plug | A dead cylinder, a limping note | A black puff from that cylinder's exhaust on each miss (built) |
| Running dry | Squeal and sparks | Smoke from the crankcase (built) |
| Flooded with water | The engine stops | A gulp, a burst of steam and a hard clunk |
| Exhaust blocked | The cylinder will not fire | Chuffing back out of the intake |
| Overloaded | A stall | One last belch of smoke (built); the block shudders with the engine rock |
| Gasoline lighting early under boost | A goggles line and lost power | A rattle and orange flashes in the exhaust |
| Cold block | White vapour, drag | Nothing added: the white vapour is the show |

The boost rattle is a sound for a loss that exists. Knock as a mechanic stays declined.

Trimmed on 2026-11-15, after the first rows were built and liked: the soot streaks, the labouring groan, the lugging
note and the stumbling cold note are dropped, so the drama stays a few clear cues and not a wall of effects. The
other rows (lean spit, flood, blocked exhaust, boost rattle) are built. Also dropped on 2026-11-15, from the sound
pass: walls that muffle, and a lumpier idle note on big cams. The intake roar stays, on the condition above.

**Advancement declutter.** The mod has 110 advancements, many of them one per part or per feature. Cut it down to
the ones that mark something a player would be proud of: a first running engine, each layout, each fuel, real power
and tuning milestones, and the track capstones. The rest lose their place in the tab but keep their IDs through 1.x,
so the stability promise holds and nothing that refers to them breaks. They are deleted for good in 2.0. The author
approves the keep list before anything is cut. One advancement is hidden first and looked at in a client, to see
that it leaves the tab and the ones below it stay attached, before the rest follow. The rule 8 test keeps every ID.

## 1.2 Dress Parts

**Shipped as v1.2.0.** Casing styles later also reached shafts and cogwheels (1.3.0), and the `casing_style` state on
pipes cost about 440 MB of heap at model load (see `docs/releases/v1.3.0.md`, Exceptions): cheaper pipe models are a 1.4
item.

Two players' engines should not look alike. Every part here is cosmetic.

- **Rain cap:** a hinged cap on an exhaust end that flaps with each exhaust pulse and drops shut when the engine stops.
  Built on 2026-11-15 as its own block that goes on the mouth of an exhaust outlet (and only there), with a flap
  drawn by a renderer. It is only for looks: it never seals the mouth and the exhaust leaves from under it.
- **Casing variants:** dyed casing in Create's palette, riveted and louvred plates, and a **window casing** that
  shows the crank turning behind glass. All join the existing connected-texture rules. Built on 2026-11-15: riveted
  and louvred plates (each its own block, joining its own kind), and a dye applied in place to any of the three (a
  dye tints it, a bucket of water washes it). It also works on casing clad onto a part (cylinders, cranks, cams,
  pipes, cased shafts and cogs). The window casing is built too (it was dropped and brought back by the author on
  2026-11-15): a glazed plate that is a block of its own, and that clads a crankshaft or camshaft part with the part
  still turning in view behind the glass.
- **Maker's Plate.** A brass plate fixed to an engine with a name, the builder and the figures from its last
  Dynamometer run stamped in. A blueprint (1.4) takes its dyno curve from the plate.
  **Built 2026-11-15:** named from the item's anvil name, the placer as builder, stamped by hand from the engine's
  finished curve. It keeps four figures (peak torque and peak power, each with its speed), not the whole curve, so a
  blueprint that wants the curve reads it from the engine or a Dynamometer.

## 1.3 Refinery

**Shipped as v1.3.0 on 2026-11-17**, including leaning columns (with no joint block, as decided), the Flare Stack, Soot
Trap, Jerry Can, the Basin goggle line, fluid-coloured sight windows and the JEI and EMI refining pages. The text below
is the plan, with the stage notes added as the stages were built. Where it says a joint piece, the shipped design has no
joint block: the sections draw their own arms. The Ponder page for the swap is in, and the price of the new ethanol is
in the changelog and `docs/REFINING_BALANCE.md`. **Known gaps:** diagonal leans in x and z at once, the JEI page was
checked by hand by the author in their own world on 2026-11-17 (works), but the machine row cannot run on this NeoForge, and the Create 6.0.10 builds 217 to 256 fail one
Transmission GameTest (the listing says to use build 257 or newer).

The same four fuels, made a better way. Plant oil, diesel, gasoline and ethanol stay exactly as they are in an
engine. What changes is how they are made: by refining in a column, as a continuous flow, instead of batches in a basin.

- **The Refining Column, a real multiblock.** Three parts that form one structure, the way Create's fluid tanks and
  boilers do:
  - a **Reboiler** at the bottom, sat on a Blaze Burner or an Exhaust Heat Exchanger, so a running engine can
    refine its own fuel. The feed is piped in here and the catalyst is loaded here;
  - **Column sections** stacked on it, one, two or three blocks wide. Height and width set how fast it works.
    **Columns can lean, at any width.** Two sections that touch only corner to corner, one at 0,0 and the next at
    1,1, join with a slanted connecting piece of their own, model and texture, the way a diagonal-fences mod joins
    fence posts. The column then really runs at 45 degrees, not as a staircase of blocks, and is still one structure.
    The slanted piece is solid: you can walk up it and you bump into it. It takes up world space. The block spaces
    the slant crosses must be empty, and the column fills them with joint pieces of its own when it forms. A joint
    piece is a real block with no item: it costs nothing in a schematic, turns and mirrors with one, and goes when
    the column is broken. If a block is in the way the sections do not join, and goggles name the block;
  - a **Condenser** on top, where the product is piped off. The Reboiler and the Condenser do not join diagonally:
    the first section sits straight on the Reboiler, and the Condenser sits straight on the last section.

  Sight windows in the sections show the feed boiling at the bottom and the product running down from the top, in
  the fluids' own colours. Goggles on any part give the rate, the heat and what is holding it back.
- **A flare stack:** the thin chimney with a flame on top that a real refinery burns its waste gas from. Here it is a
  cosmetic block whose flame burns only while the column is working, so you can see from a distance that the
  refinery is running.
- For framing and bracing a diagonal column, use Create's own girders: the mod adds no struts or casing of its own.
- **What it refines,** following the heat the basin recipes ask for today:

  | Feed | Product | Heat | Cost |
  | --- | --- | --- | --- |
  | Plant Oil | Diesel | Heated | Cheapest and quickest, with a little coal as catalyst |
  | Diesel | Gasoline | Heated | Middling, with a little blaze powder as catalyst |
  | Sugar Mash | Ethanol | Heated, and twice as much superheated, as today | The dear one: the most feed for each bucket and the slowest to come over, with nether wart as catalyst |

  Stage 2 built the structure: blocks `reboiler`, `column_section` and `condenser`. A column is a square of Reboilers
  1 to 3 blocks wide, one or more layers of sections of the same square on it (32 at most), and a layer of
  Condensers on top. The Reboiler at the lowest corner runs it. Feed goes into any Reboiler, product comes out of any
  Condenser, and catalyst goes into any Reboiler by hand, funnel or hopper. Tanks hold 1000 mB for each block of the
  base. Height in the balance table is the number of section layers.

  Stage 4 built the lean. After the first layer of sections (which sits straight on the Reboilers), a layer may sit one
  block to the side of the one below, along x or z, at any width, so a column climbs at 45 degrees and is still one
  structure. There is no joint block: the sections join by themselves, the way Create chutes do. A section with nothing
  straight above it and a section above and to one side grows a slanted wedge arm towards it, and the section that leans
  grows a brace back towards the one below. The arms are part of each section own model, follow the neighbours as they
  are placed and broken, and are drawn into the empty block above or below, so that block must be empty; if not, goggles
  name it. The arms are looks only (they have no collision of their own). A layer may not lean back the way the one below
  leaned, because both leans want the same space. The Condenser sits straight on the last section. Leans that move in x
  and z at once are not in 1.3.

  Stage 1 set the numbers (see [REFINING_BALANCE.md](REFINING_BALANCE.md)): diesel and gasoline give 1100 mB from
  1000 mB of feed and use a catalyst item per 1000 mB of feed; ethanol gives 170 mB heated and 340 mB superheated from
  1000 mB of mash.

- **Ethanol is the premium fuel.** It is the one that takes boost, so it costs more than diesel or gasoline, and
  from 1.3 more than it costs today. The basin's two ethanol recipes get weaker (today 250 mB of Sugar Mash gives
  50 mB heated and 100 mB superheated). The column's ethanol lands between the weakened basin and today's figures:
  better than the basin, dearer than now. Gasoline stays the cheap default. The changelog says so, since an ethanol
  plant built before 1.3 will make less.
- **Column recipes are data files,** like every Create recipe: one file names the feed, the product, the heat and
  the catalyst. A pack can add a route for its own fluid from 1.3 on, and the format joins the 1.x promise.
- **Catalysts.** The coal in today's diesel recipe and the blaze powder in today's gasoline recipe become catalysts:
  loaded into the column's base and used up slowly, not once per batch.

- **The chain stays the chain:** seeds to plant oil to diesel to gasoline, sugar crops to mash to ethanol. Biodiesel
  stays a basin mix, since it is a mix and not a distillation, and still gives Glycerin. Its recipe asks for less
  Ethanol in step with the price rise, so Glycerin, and the coolant and engine oil made from it, cost what they do
  today.
- **By-products stay the ones that exist.** Glycerin and Used Oil keep their loops. One addition: a **Soot Trap** on a
  rich engine's exhaust collects soot for black dye, which the dyed casing (1.2) uses.
- **Handling:** a **Jerry Can** (a few buckets of one fluid in one item). A tank with a window and a comparator
  signal is Create's Fluid Tank already, so the mod adds none.
- **Balance check before release.** The column makes diesel and gasoline cheaper, so power gets cheaper. Work out
  what a bucket of each fuel costs by each route, before and after. Run the tier engines from the 2026-11-01 balance
  pass on column fuel, and adjust the column's rates until the tiers sit where they were pinned. The same check
  sets the weakened basin ethanol figures. The numbers go in the changelog.

**Decided 2026-11-16:** no basin recipe is nerfed except ethanol, so a factory built before 1.3 makes the same amounts
of diesel and gasoline it makes today. The column is simply more efficient. **Leaning columns stay in 1.3 unless the
release slips; if it does, they move to 1.3.1** (they only add IDs, so nothing breaks). The catalyst goes in a slot on
the Reboiler that a funnel or hopper can feed. The catalyst for Sugar Mash is Nether Wart (chosen 2026-11-16), so ethanol asks for a Nether trip on top of being the dear fuel.

**The basin recipes stay, and are the worse way.** A basin still makes every fuel, so no existing factory stops, but
the column gives clearly more fuel from the same feed and heat, and gives it continuously. Diesel and gasoline in a
basin stay as they are; only the ethanol recipes are cut (above). Moving over is easy by design:

- The column's base goes on the Blaze Burner or heat exchanger the basin was sitting on, and takes the same pipes.
- It is built from the materials a player already has when they first make diesel.
- The player is told on Create's own Basin. Decided 2026-11-16: a small mixin adds a goggle line to a basin that is
  making one of these fuels, saying a column would do better and by how much. The Exhaust Heat Exchanger's goggles say
  nothing about refining. The mixin is one narrow injection that must fail soft: if a Create version changes the
  method, the line goes missing and nothing crashes (rule 7). The fuel tooltips name the column as the better route.
- A Ponder page shows the swap.

## 1.4 Drawing Office

**Status 2026-11-17: stages 1 to 6 and 8 are built and tested (none committed or released); stage 7 waits on the author's
Aeronautics afternoon; stage 9 is done as far as 1.x allows and the release record is written, awaiting the author's Part 3 and sign-off.** Nothing here has shipped. The order below is how
the work is cut up, and every stage is additive (no existing ID, config key or number moves), so each can land on its own.

| Stage | What | State |
| --- | --- | --- |
| 0 | The Aeronautics afternoon from 1.0.x: an engine on a simulated contraption, written down. Needs the author and the addon. It decides how big stage 7 is | **Started 2026-11-18**: a Precision V-twin on a wheeled contraption works (author, one test). Particles, sound, and save and reload fine on it. Propellers and other engines are still unwritten-down |
| 1 | **The blueprint format:** `engine/blueprint` (plain Java). A `Blueprint` of parts, each with a position, a block id, state properties and dials; the pasteable string `CEBP<version>:<body>`; hard limits; a policy the game side fills in. Unit tests only, no game | **Built 2026-11-17** |
| 2 | **Record:** a Blueprint item (`blueprint`, paper and blue dye): used on any block of a built engine while blank, it records the engine's layout and every dial through the scanner, and holds it as the `CEBP` string in the `blueprint` data component. The game side of the policy (this mod's blocks only, real state properties, real dial ranges) | **Built 2026-11-17** |
| 3 | **Apply:** one click copies the whole tune onto an identical engine, checked on the server | **Built 2026-11-17** |
| 4 | **The drafting sheet:** the screen (the plan seen from above, the parts, the Maker's Plate figures), a Ponder page, the item tooltip. Goggle lines do not apply: a blueprint is an item, not a block | **Built 2026-11-17** |
| 5 | **Build from it:** the Bank Builder lays the engine from the parts you carry; the blueprint exports as a Create schematic | **Built 2026-11-17** |
| 6 | **Share and hang:** paste in, copy out, server checks; the framed blueprint | **Built 2026-11-17** |
| 7 | **Aeronautics** compatibility and Ponder page (sized by stage 0), then the other addons | **Not started**: needs the addon, so it waits on stage 0 (the author's) |
| 8 | **Pack-maker tools:** JEI and EMI pages for fuels and parts, three config presets, the part stats data map, the `api` package with its events (the last two preview). KubeJS events and a global, and a CC: Tweaked peripheral, both read-only | **Built 2026-11-17** |
| 9 | Cheaper pipe models (the `casing_style` heap cost found in 1.3), then the release record | **Done as far as 1.x allows, 2026-11-17**: every state of a multipart block shares one model (about 108 MB of the 440 MB); the rest is block-state bookkeeping that needs fewer states, so it is a 2.0 item. Release record written (`docs/releases/v1.4.0.md`), Part 2 to be re-run on the final tree, Part 3 and sign-off are the author's |

**Stage 9, as far as it went (2026-11-17).** A block's dye is only a tint, yet each of its 17 values is a block state and vanilla
bakes a multipart model (a 64-way connection table of conditions) per state. `client.SharedCasingModels` (on `ModelEvent.ModifyBakingResult`)
points every painted state of a multipart block at its unpainted twin's baked model, so the copies are freed. Measured in the
client audit on the same harness as 1.3: settled heap after engines 1179 MB, against 1267 MB before (about 90 MB, not the
hoped 440 MB). The audit checks 925 painted multipart states share the model and draw the same quads. What remains is the
unpainted bake itself, 64 shapes by encased by three styles per pipe, each with about 450 conditions; sharing across styles is
impossible (the model differs), so the next step is a model that bakes once per shape (a custom loader) or fewer conditions per
state, and needs a profile and a look in game. The peak at model load is not reduced, only what stays.
**Second step, and what the profile showed (2026-11-17).** `SharedCasingModels` now points every state of each of this mod's
multipart blocks at one baked model for the block, not only the painted ones, since a multipart model reads the state it is
asked about. The audit checks that 1,719 sampled states draw exactly what they drew before (a hash of every quad's vertices,
sprite and tint, per face) and share the block's one model. Settled heap went 1179 to 1161 MB, so only about 18 MB more. A class
histogram of the running client (`jcmd GC.class_histogram`) shows why: the baked models were not the bulk of it. About 151,000
`BlockState` objects with their property tables, shape caches and occlusion slices (`SubShape` and `SliceShape`, 283,000 of them)
come to a few hundred MB, and a pipe has a state for every connection shape, encasing, casing style and dye. Cutting that means
fewer states, which means removing a state property, and `casing_style` and the dye are in the 1.x promise. So the 440 MB is
**not recoverable in 1.x**; it moves to 2.0 with the other state cleanups (candidates: fold the dye into a block entity or a
data component, and drop the per-state occlusion cache with `noOcclusion` on pipes after a look at light and culling).
The histogram also showed 54 million queued chunk-save tasks (`IOWorker`, 3.4 GB) in the audit's walking phase; that is the audit
or something in this mod saving chunks far too often, and it has not been looked into. Separately, the audit JVM
exited with code -1073740791 after the report was written and `verifyClientSmoke` passed; not investigated.

**Blueprint format, as built (stage 1).** A blueprint is a name, a builder and up to 4096 parts, each within 127 blocks of
the origin on every axis, so one is always small enough to paste. The string is the prefix `CEBP`, the format version, a
colon, then the body deflated and written as URL-safe base64; whitespace in a pasted string is ignored so a chat wrap does
not matter. A later release reads every format up to its own and refuses a newer one with a message that says so. The
decoder checks every size before using it (text length, unpacked size capped at 1 MiB so a small string cannot unpack
into a large one, every count and string length) and ends in a `BlueprintException` with a `Refusal` and never anything
else. `BlueprintFormatTest` freezes a string per shipped format and is never edited. What the format cannot know (which
blocks exist, which dials a block has and their ranges) comes in through `BlueprintPolicy`, which the game side
implements in stage 2; `BlueprintRules.check` applies it before any block is placed. Tests: `BlueprintCodecTest` (round
trip, order-independent output, every truncation, 3000 mutations and 3000 garbage strings, a decompression bomb,
limits), `BlueprintRulesTest`, `BlueprintFormatTest`, and the package is on `ArchitectureRulesTest`'s plain-Java list.

**Recording, as built (stage 2).** `content/blueprint`: `BlueprintRecorder` reads the engine the scanner finds from the clicked
block (`EngineLayout.members()`) into a `Blueprint`, positions relative to the primary crankshaft, each block's state
properties that differ from the block's default, and every dial. `EngineDials` is the one table of dials: block, name, range
and how to reach the value box, for crank throws and rotor housings (`throw_angle`), cam lobes (`lobe_angle`), heads and
rotors (`fuel_metering`), throttles (`idle`, `wide`), MAF sensors and governors (`target`), turbos (`limit`, `anti_lag`),
superchargers (`limit`), the transmission (`gear`) and the sump (`oil_exchange`, `signal_tank`). `EngineBlueprintPolicy` accepts
only registered `create_engines` blocks, properties and values the block really has, and dials in range. Only this mod's
blocks are recorded, and so are Create's own blocks the scanner counts as part of the engine (shafts, cogwheels and chain
drives that couple crank lines; checked 2026-11-17 against `BlueprintRecorder` and `EngineBlueprintPolicy`, which allow the
`create` namespace). The gearing from a crank to a camshaft is not part of what the scanner follows, so it is not recorded:
this is the one gap left in recording. Fluids, fuel and oil are not recorded. The new IDs join the 1.x promise at release: item `blueprint`, data
component `blueprint`, recipe `blueprint`; dial names and the `CEBP` format are frozen the same way. A sneak-use on a
recorded blueprint records over it; a plain use says what it holds. Tests (`BlueprintGameTests`): a built engine is recorded
block by block, every dial at its setting, recording changes nothing, a non-engine is refused, what is recorded passes the
server's rules and survives the string, the rules refuse foreign blocks, bad properties and out-of-range dials, the item data
round-trips, no block of this mod has a property a part cannot hold, and the guard that fails if `EngineDials` differs from
any real value box (checked by changing one range: it fails with the block and both ranges). The item data is also in
`CodecFuzzGameTests`. **Not done:** the item has not been used by a player in a game, so the sprite, tooltip, messages and
recipe have not been seen; Ponder page, goggle lines and the drafting sheet are stage 4.

**Stages 3 to 6 and 8, as built (2026-11-17).**

- **Apply (3).** `BlueprintApplier` decodes the item's string, runs `BlueprintRules.check`, records the engine as it stands and
  compares it with the blueprint (`engine.blueprint.BlueprintDiff`, plain Java). Only if every block of this mod is in the
  same place with the same block and state does it set the dials that differ, each through the block's own value box. If not,
  nothing changes and the first five differences are named (missing, extra, different block, different state). The engine has
  to be the same build, and a copy built turned a quarter, half or three quarters of a turn is recognised as the same build
  (`BlueprintTurner`, below). Use a recorded blueprint on an engine to apply it; sneak and use to record over it.
- **The sheet (4).** Format 2 (`CEBP2:`) adds the Maker's Plate figures to the string (format 1 still reads; a frozen string for
  each is tested). The recorder takes them from the first stamped plate fixed to the engine, and the blueprint's name from the
  anvil name, else the plate's, else "Engine". `client.BlueprintScreen` draws the plan (east right, south down, higher blocks
  over lower), the parts counted by block, and the figures; use the item in the air to read it. The Ponder page `blueprint`
  covers recording, reading, applying, building, sharing and the frame.
- **Build (5).** `BlueprintBuilder`: hold a Bank Builder with a recorded blueprint in the other hand and use it on a face; the
  engine's crankshaft goes against that face. The whole site is checked first (loaded and inside the world and border, free to
  build in, the player allowed by spawn protection and by the ordinary place event for every block, every part in the
  inventory by Create's own item requirement for each block) and nothing is placed or taken unless every check passes. Creative
  players pay nothing. A block with no item to pay for it (a cased shaft) cannot be built by hand in survival. The engine is turned
  by the way the player faces: facing north builds it as it was recorded, east a quarter turn clockwise, and so on, and the
  Bank Builder's outline preview (`client.BuildPreview`, green where free, red where something is in the way) shows the same
  turn before anything is placed. Create's own blocks on the crank line, and the cogwheels, chain drives and gearboxes that
  turn each camshaft and gear one crank line to another in a coupled engine (`engine.topology.EngineGearing`, found by walking
  Create's own connections from the crank to each cam line and each further crank line), are in a blueprint and are built and
  paid for like any other block; a line reachable only through a belt is left out and counted as skipped, since a belt is more
  than one block. (Found 2026-11-17 by the coupled-engine turn test: before this, a coupled engine's blueprint did not hold its
  coupling gears, so the copy was two separate engines.) With an Empty Schematic in the other hand, using the blueprint in the air writes `schematics/uploaded/<player>/engine_<name>.nbt`
  and swaps the Empty Schematic for a Create schematic of it: blocks and states only (a Schematicannon prices them through each
  block's requirement); dials are not in a schematic, so apply the blueprint after.
- **Share and hang (6).** A blank blueprint used in the air opens a box to paste into and a button that sends it to the server
  (`BlueprintImport`, one client-to-server packet, at most 30,000 characters, because that packet is cut at 32 KB). The server
  decodes it, runs every rule (this mod's blocks only, every state property and dial real and in range), stores its own
  re-encoding, and says why it refuses anything else. Sneak and use a recorded blueprint in the air copies its string to the
  clipboard. The Framed Blueprint (`framed_blueprint`, iron nuggets round paper) goes on any face; use a recorded blueprint on it to hang it, an
  empty hand reads it, sneak with an empty hand takes it back, and breaking it drops the frame and the blueprint. A renderer draws the plan and the
  figures on the paper. Cosmetic only.
  **Joined frames (added 2026-11-17):** framed blueprints that touch edge to edge on the same wall and together make a full
  rectangle (at most 8 by 8, none missing, all facing the same way) draw as one sheet: the plan, the name and the figures
  are scaled up across every frame, with paper laid over the rims and bolts between them. The picture is the blueprint in
  the top-left frame, or if that is empty the first one reading left to right and top to bottom. The other frames keep
  what they hold (and give it back when taken or broken) but show nothing of their own. A ragged group (an L, a hole), a
  gap, or a ninth frame in a row leaves every frame drawing alone, exactly as before. The grouping is `engine.blueprint.FrameSheet`
  (plain Java; `FrameSheetTest`), the world side asks it through `FramedBlueprintBlockEntity.sheet()` (looked up again at most
  once a second, since a client is not told when a neighbour changes), and the paper's right and down directions come from
  `FramedBlueprintBlock.paperRight` and `paperDown`, the same turn the renderer uses. No block state, ID or saved data
  changed; only what a row of existing frames looks like. GameTests: every one of the six faces joins a 2 by 2 and a lone, ragged or
  differently facing frame does not; a row falling apart; a sheet's frames still give their blueprints back.
- **Pack-maker tools (8).** Three config presets in the jar (`config_presets/casual.toml`, `standard.toml`, `expert.toml`):
  tables to copy over `[balance]` and `[features]`, every key an existing setting, `standard` held equal to the shipped defaults by
  a test. The part stats data map (**preview**, `data_maps/block/part_stats.json`, keyed by `create_engines:cylinder` or
  `create_engines:precision_cylinder`): optional scales from 0.1 to 10 on a cylinder's compression, intake and exhaust flow, fuel per shot,
  ignition delay, burn time, friction, inertia, displacement and injector flow; with no entry the spec object is returned unchanged, and rotor
  housings are not covered. The `api` package (**preview**): `EngineApi.viewAt`, a read-only `EngineView`, and two events on
  `NeoForge.EVENT_BUS`, `EngineStateChangeEvent` (a start is a change to RUNNING, a stall to STALLED) and `EngineMilestoneEvent`;
  a listener that throws is logged and never stops an engine. JEI and EMI pages for every fuel (energy, how it lights, cold start,
  boost tolerance) and for every installable part and the blocks it goes into. **KubeJS and CC: Tweaked (added 2026-11-17,
  `compat/kubejs`, `compat/computercraft`, docs/INTEGRATIONS.md):** both read-only, on the `api` package. A KubeJS plugin (named in
  `kubejs.plugins.txt`, so nothing in the mod names a KubeJS class) adds `CreateEnginesEvents.stateChanged` and `.milestone` for server
  scripts and a `CreateEngines.engineAt(level, pos)` global; a CC peripheral of type `create_engines_engine` sits on crankshafts,
  cylinders, heads and rotor housings and answers for the whole engine (`getEngine`, `getStatus`, `getRpm`, `getHealth` and the
  rest). Fuels and part stats are added with datapack files, which KubeJS already does. Both have a compatibility-matrix row
  (`runSmokeAuditKubejs`, `runSmokeAuditCc`) whose audit fails unless the other mod found this mod's plugin or peripheral: KubeJS
  must hand a script listener the events the engine code posts, CC must offer the peripheral on a running engine and read it. Setting
  throttle or rack from a computer is deliberately not built (the API is read-only); a real script and a real Lua program were
  not run by a player.
- **Turned builds (added 2026-11-17).** `BlueprintTurner` turns a blueprint by 0, 90, 180 or 270 degrees about the vertical:
  each block's place, each state through the block's own `rotate` and `mirror`, and each crank's throw angle (the pin's direction
  is carried through the turn and read back against the new axis, since a crank's rest direction depends on its axis). Cam lobe
  angles are timing values and are kept. An engine always turns the same way about its crank's positive axis, so a turn that
  would carry that direction onto the negative direction of the new axis (a half turn, and one quarter turn either way,
  depending on the axis) also mirrors the copy across a plane through the crank: it looks mirrored and runs as the same engine.
  The Bank Builder turns by the way the player faces (north is as recorded); applying finds the matching turn by trying all
  four, anchored on the engine's own primary crankshaft, which is not always the block the turned blueprint calls its origin.
  Tests: `aTurnedBuildScansAsTheSameEngine`, `aTurnedRotaryBuildScansAsTheSameEngine` (twin rotor, the second half a turn
  round) and `aTurnedCoupledBuildScansAsTheSameEngine` (two geared crank lines): all four turns each, same cylinders at the
  turned places with the same top dead centre, cam timing and faults, read from the real scanner; and
  `aBlueprintAppliesToATurnedCopyOfItsEngine`; also `aTurnedVEngineScansAsTheSameEngine` (a second cylinder leaning out
  sideways on the same throw), `aTurnedCounterRotatingBuildScansAsTheSameEngine` (a second line geared the other way) and
  `aTurnedPipeKeepsItsOpenEndsPointingTheSameWay`. The pipe test found a real defect: `ManifoldBlock` stored its open ends but
  had no `rotate` or `mirror`, so any turned or mirrored placement (a turned build, a vanilla structure, a Create schematic)
  left the pipe arms pointing the old way; it now turns them. The counter-rotating test showed that a segment's `sense` is
  relative to the first crank line the scan reaches, so a turn can change which line is first and then every sense and top dead
  centre changes sign together; the test accepts one such global flip and nothing else. Not covered: a turned pipe network
  scanned as an engine (the pipe test checks the states, not a built network).
- **Tests.** Unit: the codec, the rules, the diff, the file names, the part stats arithmetic, the status names. GameTests
  (`BlueprintGameTests`, `PackToolsGameTests`): recording, applying, refusing, building in creative and survival, protection vetoes,
  blocked and unloaded sites, the Bank Builder flow, the structure file, import (good, hostile, too long), the frame (hang, take,
  save and load, break, fall off), the config presets, the part stats format, the API events and view, and the table of dials
  against every real value box. Client audit: the sheet and the import dialog draw, every refusal has wording, every fuel has a page.
  **One existing test changed:** `anEngineInAnUnloadedChunkDoesNotPullItBackIn` judged at fixed ticks (60 and 100) whether the far chunk
  had finished generating and then unloaded, and failed depending on which other tests ran beside it; it now waits for each (up to
  600 and 300 ticks) before judging, and still asserts the same two things.

**Blueprints**

- Click an engine with a blank blueprint to record its layout and tune: firing order, cam angles, injector
  settings, every dial.
- The blueprint opens as a drafting sheet: a top-down drawing, a parts list, and the dyno curve if the engine has a
  Maker's Plate.
- **Apply** it to an identical engine to copy the whole tune in one click.
- **Build from it:** the Bank Builder reads a blueprint and lays the engine from parts you carry. It also exports as a
  Create schematic, priced correctly since 1.0.
- **Hang it:** a framed blueprint is a wall decoration that shows the drawing and the curve.
- **Share it:** export and import as a string. The string carries a format version, so a later release still
  reads an older blueprint.
- **Safe to import.** A pasted string is checked on the server before anything is built: a size limit, only this
  mod's blocks, every setting inside its range. A bad string is refused with a message. Building from a blueprint
  places blocks as the player, so claims and protection mods apply.

**Engines that move: first piece built 2026-11-18. Update 2026-11-19: the `runGameTestServerSable` run now starts (Aeronautics 1.3.2 and Sable 2.0.6 on NeoForge 21.1.228) and all GameTests pass in it, per the v2.0.0 record; the text below about it being unable to start is out of date.** `compat.sable.SableCompat` (Sable's own small companion library, compiled against, one bridge class) projects a block out of its moving structure's plot, and `EngineController` now uses the block's height in the world for the thin-air check and re-reads it every 20 ticks, so a climbing aircraft thins as it rises. Without Sable nothing changes. `SableGameTests` (assemble an engine into a structure at y 2000, check the reported height and density) and the `runGameTestServerSable` run exist, but **that run cannot start**: Aeronautics 1.2.1, the newest the build's NeoForge 21.1.219 accepts, is built for Create 6.0.10, and its Simulated mixin into `NixieTubeBlock.getPower` fails to apply on Create 6.0.11 (this build's floor since today); 1.3.2 asks for NeoForge 21.1.228 or newer. Running it needs a NeoForge bump to 21.1.228+, or the Aeronautics row stays red. **Finished 2026-11-20 as far as can be done without the author (sound and particles were confirmed working by the author on a moving vehicle):** (1) **Weights**: engine blocks have a mass on a moving structure, as plain data for Sable (`data/sable/physics_block_properties/create_engines_<group>.json`, each selecting one of four block tags `create_engines:weight/<group>`): cast flywheel 4.0 (Create's own flywheel is 4.0), cylinder, crankshaft, turbochargers, superchargers, heat exchanger, battery, heavy starter and transmission 2.0, precision cylinder, rotor housing, alternator and sump 1.5, pipework (manifolds, outlet, air intake, throttle, MAF sensor, governor, rain cap) 0.5; anything else is Sable's default 1.0. A rotor housing is lighter than a cylinder on purpose (power for weight is the choice a plane makes). The numbers are a first choice, not measured on an aircraft. `SableWeightsTest` (files parse, tags name real blocks, no block in two groups, the intended ordering) and the GameTests `engineBlocksWeighWhatTheirDataFilesSay` (Sable's own mass lookup returns the file's number) and `theWorldAroundAnEngineOnAStructureIsTheWorldWhereTheStructureIs` (both in `runGameTestServerSable`). No Java reads the weights, and nothing changes without Sable. (2) **The world around an engine is the world where its structure is**, not its plot: `SableCompat.worldBlockPos` (the block cell in the world, `isInPlotGrid` first so a block off a structure costs one check) now decides a battery's biome cold, the rain that steams off a hot head, and the cell Pollution of the Realms gas is put into. Without Sable these return the block's own cell. (3) **Scanner on a structure: tried, not verified.** The scanner reads blocks beside blocks, so it should not care that they are in a plot, and the author's V-twin on a wheeled contraption ran. A GameTest to prove it was written and dropped: `SubLevelAssemblyHelper.assembleBlocks` on a built engine leaves the main world empty but the structure's plot shows only the test's structure block, at tick 3 and at tick 200, and the cause was not found (reading the plot through the server level and through the plot's loaded chunks both gave the same). (4) **Ponder page** `moving_engine` (crankshaft, cast flywheel, turbocharger and rotor housing; it reuses the rain cap schematic and lifts the engine with a moving section). **Water check, built 2026-11-20 at the author's say:** an engine on a structure is under water when the world around the structure says so. `SableCompat.wetInWorld` and `fluidInWorldAt` project the neighbour cells out of the plot and read the real fluid; the scanner's wet check and `PortOpeningBlock.isFlooded` use them, so a submerged cylinder or head is `SUBMERGED` and a flooded mouth is flooded exactly as on the ground. Because no block update says a structure moved into the sea, `EngineController.watchWorldWater` re-reads every 20 ticks which cylinders, heads and outlets are wet and asks for a rescan when that changes (it is nothing off a structure). It is the existing rule, not a new failure. GameTest `aBlockOnAStructureSeesTheWaterOfTheWorldItIsIn` (water round and in the structure's world cell shows, gone it does not); the re-check and a whole engine sinking have not been run (see the scanner note above: an engine could not be assembled into a plot in a GameTest). Also not done: sound and particle positions as measured by anyone but the author, `getVelocityRelativeToAir` (ram air) and multiplayer.

**Engines that move: Create Aeronautics**

Mod listings say Aeronautics is out for 1.21.1 on NeoForge with Create 6.0.10 (to be confirmed on its own page in
1.0.x). It keeps a moving structure as real blocks, so an
engine on one is still an ordinary block entity on an ordinary kinetic network, and Aeronautics already supplies the
parts that turn rotation into motion. That is the system this mod would otherwise have to invent. So the work is to
make an Engine Engineer engine the obvious thing to bolt into an aircraft or a vehicle:

- Make everything that reads the world work on a moving structure: the engine scanner, the water check, intake and
  exhaust mouths, the heat exchanger.
- Sound and particles that follow the structure, with exhaust left behind as a trail and the note heard passing by.
- Sensible weights for engine blocks if Aeronautics lets an addon set them, so power for weight becomes a real
  choice: a rotary earns its place in a plane, a big diesel in a hauler.
- A Ponder page for an engine running on a moving structure. Aeronautics stays optional.
- **How it is built and tested.** The mod compiles against Aeronautics without requiring it, and every line that
  touches it sits behind a check that it is installed. The normal build and test run go without Aeronautics; a
  second test run goes with it. The README names the Aeronautics version that was tested.

Not verified: how much of this already works. The afternoon test in 1.0.x answers that.

**Pack-maker tools** (all additive)

- JEI and EMI pages for fuels (energy, ignition, cold start, boost tolerance) and for which part fits where. The
  refining routes already have pages in both (1.3.0, `client/compat`); the fuel and part pages are still to do.
- KubeJS bindings (preview): register fuels, override `Tuning`, listen for start, stall and milestone events.
- Part stats as an optional data map over the built-in values (preview), so a pack can retune a cylinder without
  code. 2.0's parts as data replaces it and still reads it.
- Three shipped config presets: Casual, Standard, Expert.
- An `api` package marked as a preview, frozen at 2.0.

**Other addons.** List which Create addons are live on 1.21.1 with Create 6, test with the fuel and electricity
addons on that list, and fix what breaks. A ComputerCraft peripheral on the crankshaft (read speed, load, boost; set
throttle and rack) lets players write their own engine management in Lua, without the mod adding a single dial.

## 2.0 Open Block

### The platform

**Status 2026-11-18 (branch `2.0-crossover`, merged with `2.0`):**

- **Engine types as plug-ins:** built by the other 2.0 session (TorqueSource seam, rotary on its own drive, provider registration with
  boost, goggle lines and rebuilds; see `docs/design/ENGINE_SEAM.md`). **Finished 2026-11-19 (author's decision):** the
  boost path is narrowed to `Breather` (19 files still name `CylinderModel`, all cylinder internals), a source can be attached to an intake
  or exhaust network so turbos, blowers and intercoolers act on it, and a provider's watched blocks are recorded in blueprints and accepted
  by the server. No scanner-rule registry: the watch-and-rebuild hook already rescans. See the end of `docs/design/ENGINE_SEAM.md`.
- **Frozen public API:** `api.ApiVersion` 1.1.0 (its own semver; 1.1 added `PartBlocks` and `EngineBuilding`, 2026-11-21), `ApiSurfaceTest` against `compat/api-surface.txt`, provider internals
  moved to `engine.EngineProviders`, `docs/API.md`. Behaviour is covered by the GameTests, not the surface file.
- **Parts as data, first piece:** the four precision parts (bore, head, piston, injector) are `part_effects/<part>.json`, read on every
  data reload into `engine.sim.PartEffects`; the shipped files equal the old constants (pinned by test) and the baselines did not move.
  The rotary chamber is retuned through the same `part_stats` map (key `create_engines:rotor_housing`, built 2026-11-19, `CEDataMaps.rotaryStats`).
  **Finished 2026-11-20:** cam profiles are data (`data/create_engines/cam_profiles/basic.json` and `rotary.json`, `engine.sim.CamProfiles`,
  `engine.CamProfileLoader`; the shipped files equal `CamProfile.BASIC` and `ROTARY`, pinned by `CamProfilesTest`, so the baselines did
  not move; the profile times the simulation on the server, the valves a client draws keep the built-in shape), and a third tier is a
  block of an addon's own that names a `part_effects` file of its own through the new `part_effect` block data map (`CEDataMaps.PART_EFFECT`,
  `Cylinder.effects`, `CylinderSpec.build(..., own)`, `PartEffects.ofCustom`); `OwnPartEffectsTest` and `OwnPartGameTests`. It needs an
  addon to register a block of the internal `CylinderBlock` or `CylinderHeadBlock` class (a data pack cannot make a block), and was
  first tried only through a test hook. **Since 2026-11-21 a real addon proves it**: `examples/racing-addon` is a separate Gradle project that builds
  against the finished jar, registers a Racing Cylinder block, names its effect through the data map and runs its own GameTests (a running engine with
  the racing cylinder delivers more than one with the plain cylinder; with the data map entry removed the two are equal, which was the control). It
  found one gap: the addon had to extend the internal `CylinderBlock` and name `CEBlockEntities.CYLINDER`. **Closed 2026-11-21 at the author's say:**
  `api.PartBlocks` (cylinder and head factories, `register`, `partEffect`) and `api.EngineBuilding.timeCamLobe` are API 1.1, the example uses only
  `com.createengines.api` and `ExampleAddonUsesOnlyTheApiTest` keeps it so. The rule going forward (the author's): an addon never needs an internal
  class; a need is a gap in the API. The new names are recorded in `compat/script-surface.txt`
  (the `part_effects` keys too, which were not before); the API version is 1.1.0 (1.0 never shipped on its own). The original
  text: not yet data: cam profiles, and a third tier named from a pack's own block (a pack's block would also
  need its own cylinder block class). The `part_stats` data map still works on top.
- **State cleanup (dye): built 2026-11-19, with a migration.** The dye (`paint`, 17 values) multiplied a pipe's states (64 connections
  x 2 encased x 4 styles x 17 dyes = 8,704 per pipe kind, a cylinder 6,528, and the crank, cam, cased shaft and cogwheel too). It now
  lives in the block entity of those parts (`PaintedBlockEntity`; the manifolds gained a block entity that holds only it), and the
  plain Engine Casing blocks keep it as state (a handful of states, and no block entity per wall block). The author chose to build the
  migration: `PaintMigration` reads a 1.x chunk's saved `paint` out of the raw palette when the chunk loads, before the game drops it,
  and writes it into the part's block entity data. Left out: parts inside a saved moving contraption and in saved structure files lose
  their tint. Still to check by eye: the tint in the plain, Flywheel, Sodium and contraption render paths.

- **Parts as data.** Cylinder, head, piston, injector and induction stats come from data. Crude and precision are
  two entries, and a pack or addon adds a third with a block, a model and a data file.
- **Engine types as plug-ins.** Today the simulation takes a list of cylinders, and a rotary chamber is the same
  cylinder model with other numbers. Turn that into a seam: an addon registers something that puts torque on the
  crank, a scanner rule for its blocks, and its goggle lines. An engine that is not a cylinder at all, such as a
  turbine, needs this seam built first; it does not exist yet.
- **Fuels stay data.** Fuel data maps and column recipes written for 1.x keep loading. Any field 2.0 adds is
  optional.
- **A frozen public API** under its own semver: read-only engine views, events, registries.
- **Pollution compatibility: Pollution of the Realms** (mod id `adpother`, queued 2026-11-16, **built 2026-11-18**: `compat.pollution`, `engine.sim.Emissions`,
  `EngineController.emitPollution`, `runGameTestServerPollution`; rates tuned 2026-11-19 and the engine hook covered by `PollutionGameTests`, see the decisions below). Engines
  put carbon and sulfur into the world through their exhaust outlets when that mod is installed.
  - *What was found.* The mod has no public API and no datapack hooks, but its classes can be compiled against.
    `Pollutants.BuiltIn.CARBON.get().pump(level, pos, steps)` puts gas density into one cell and returns how many steps
    went in; `canStateBePumped` says whether a cell takes any. It needs the ForgeEndertech library. The version
    checked is 9.1.14.0 for 1.21.1 NeoForge, from Modrinth's maven (`maven.modrinth:pollution-of-the-realms:<version id>`).
    Its other emitters (Create's steam boiler, blaze burners) are fixed by the mod itself, so nothing in it learns about
    our engines without code on our side.
  - *How it is built.* Same rule as Aeronautics: compile against it without requiring it, optional `adpother`
    dependency in the mod metadata, and one bridge class that is the only one to name its types, never loaded
    without the mod. The normal build and test run go without it; a second GameTest run puts both mods in.
  - *What an engine releases.* Carbon in proportion to fuel burned, sulfur in proportion to how dirty the fuel is
    (its soot factor, so crude is sulfurous and ethanol is not), and more carbon for rich-running soot that no Soot
    Trap caught. The gas goes into the cell in front of an open, unflooded exhaust outlet, a few cells further if a rain
    cap is in the way. Held fractions carry over so a slow engine still releases a step now and then. Nothing is read
    back into the simulation, so engine behaviour does not change.
  - *Settings.* An on/off switch and a rate multiplier under a new `compat` config group.
  - *Decided 2026-11-19 (author), built 2026-11-19.*
    - **Sulfur is its own optional fuel field.** A fuel data map may set `sulfur` (`FuelSpec.sulfur()`, read only by the
      emissions); when it is missing the value falls back to the soot factor, so every 1.x fuel map loads and releases what
      it would have. No built-in fuel sets one, because the soot factor is a fair stand-in for all four.
    - **The Soot Trap counts.** Soot a trap keeps out of the exhaust (`SootTrapBlockEntity.catchSoot` now returns what it
      kept; a full trap keeps none) is taken off the extra rich-running carbon, so a trap visibly cleans the air. It does not
      touch the base carbon from burning fuel or the sulfur, and nothing in the simulation reads it.
    - **Release rates are left to the builder (Claude's call), set 2026-11-19 by measuring.** The mod's gas cell holds only 3
      steps, and the first guess (0.02 carbon and 0.01 sulfur steps per mB) left about 2 steps in the air after 2400 ticks of a
      running one-cylinder diesel at about 145 RPM, with the outlet cell empty most of the time. Both were raised 25-fold, to
      `CARBON_PER_MB` 0.5 and `SULFUR_PER_MB` 0.25 (still 2 to 1): the same engine then adds about a carbon step every 40 ticks,
      and the cell at its outlet holds 1 to 3 steps, a visible haze that spreads and lingers. A bigger or faster engine burns
      proportionally more, and a full cell turns away what it cannot hold, so a bank saturates its surroundings rather than
      growing without limit. The `compat.pollutionRate` multiplier is the only dial. Chosen from one engine at one speed, not a
      sweep: tune again if packs report it is too thick or too thin, and re-run `measureTheReleaseRates`.
    - **A bug the measuring found.** The mod keeps one gas per cell, so sulfur pumped into the cell the carbon had just filled
      was refused and almost none reached the air (1 step to 49 of carbon). `PollutionBridge` now puts each gas into the first
      cell in reach that takes it and carries what a full cell cannot hold on to the next.
  - *Tests.* Unit tests for the emission numbers, the sulfur fallback and the trap arithmetic. `PollutionGameTests`, which
    only do anything in `runGameTestServerPollution`: a running diesel engine puts carbon and sulfur in the air, an ethanol one
    carbon only, a merely cranked one nothing, and one with `compat.pollution` off nothing. Each looks every 100 ticks and judges
    the most gas ever seen, because the gas drifts. Not covered: a Soot Trap's effect on a running engine (its arithmetic is
    unit tested and the controller wiring is two lines, but no GameTest runs a rich engine through a trap) and a shut or
    flooded outlet on a running engine (`CrossoverGameTests` covers the release call on one).

### The one breaking cleanup

2.0 is the only release allowed to break the 1.x promise, so everything that needs breaking goes here, with
registry aliases so existing worlds load. Each item needs the author's decision:

- The throttle body: keep, fold into the intake, or remove.
- ~~The Transmission~~: kept (2026-11-16), as the car-like gearbox beside Create's Rotation Speed Controller.
- The basin fuel recipes: kept through 1.x as the worse route, and reviewed here.
- **Built 2026-11-18.** Config keys regrouped once, after two rounds of growth. On first start 2.0 reads the old config file, carries
  each value over to its new key and logs what moved, so nobody's settings reset. Server: `[engine]` became `[calibration]` and
  `[limits]`, `[balance]` and `[features]` are unchanged, `[compat]` is new. Client: `[effects]` (`particles`, `density`) and
  `[volume]`. The 2.0 files are `create_engines-v2-server.toml` and `create_engines-v2-client.toml`, because NeoForge deletes keys a spec
  does not know before any code can read them; the 1.x files are read once and left as they were (`config.ConfigCarryOver`,
  `ConfigCarryOverTest`, a GameTest on the TOML reader). Not tried: a real world with a 1.x file in a client.
- **Advancement redesign, step 2: built 2026-11-18, see docs/ADVANCEMENTS.md.** Step 1 shipped in 1.x. 2.0 does the rest:
  rename the IDs and regroup the tab tree by track, delete the hidden advancements, and write the migration table (every 1.x ID,
  shown or hidden, maps to its new ID or IDs; old progress is credited silently, with no toast and no sting; a test checks
  every 1.x ID has an entry). **Built 2026-11-18, checked in the code on master 2026-11-19:** `AdvancementMigration` and `LegacyProgress`
exist, `compat/advancements-1x.txt` lists all 110 old IDs, `AdvancementMigrationTest` pins that each has an entry and lands on a real
row, and the generated tab has 66 files (root, 64 rows, `chief_engineer`). The hidden rows are gone. Not looked at by the author: the
tab layout, and that no toast plays for carried-over progress.
- Legacy manifold handling and other old-save code paths. **Looked at 2026-11-18 and left:** no manifold-specific legacy code was
  found; the one old-save path is `Gears.fromLegacy`, which turns a 1.0 transmission's four-gear positions into the thirteen now, and
  2.0 still has to open a 1.0 world, so it stays. Removing it is a decision for the author, not a cleanup.
- **Settled 2026-11-19 (author): the Create floor stays at 6.0.10 and is not revisited in 2.0.** (Tried 2026-11-18 and reverted the same day.) Dropping older Create versions: the build moved to Create 6.0.11-346, but
  Aeronautics 1.2.1 and 1.3.2 crash on 6.0.11, so the range is back to `[6.0.10,6.1)` and the build is on 6.0.10-281. Open until
  Aeronautics runs on a newer Create.
- The throttle body is **kept** (author, 2026-11-18). **Basin fuel recipes reviewed 2026-11-19 and left as they are.** The review is `docs/REFINING_BALANCE.md`, whose numbers
  `RefiningBalanceTest` and `ProgressionBalanceTest` hold: a basin still makes every fuel, it is the worse route (a column makes the same bucket
  from about a tenth less feed and a quarter of the catalyst), and the two ethanol recipes were already cut in 1.3.0. Changing a basin
  recipe in 2.0 would move what an existing factory makes, so nothing changes unless the author asks for a specific recipe.

### Port count: diminishing returns (breaking, 2.0 only)

**Built 2026-11-18** (`engine.sim.PortFlow`, used by `PortNetwork.flowFactor()`): `1 - e^(-2.3 * r)` for `r` openings per head, and an
opening touching another (corners included) counts half. One opening per head is about 90 percent, two about 99 percent, one for two
heads about 67 percent (1.x counted that as full). The dyno sweep (6 cylinders, intake and exhaust factor 0.2 to 1.0) gave peak torque
6.1, 7.6 and 7.9 at 0.2, 0.5 and 1.0, so the loss is mostly at the top of the rev range, not the peak; the constant 2.3 is a first choice
from that. Every real engine with fewer than about two openings per head now breathes a little less. The simulation baselines did not move
(they set the factor directly); the one GameTest that moved is under Exceptions in `docs/releases/v2.0.0.md`. The description below is the
original brief, and it misstates 1.x: full breathing needed half an opening per head, not two.

Queued 2026-11-16, author's decision: applies to every engine, held for 2.0 so 1.x builds keep their numbers.

- **Why.** `PortNetwork.flowFactor()` was `min(1, 2 * openings / heads)`, so full breathing, and the top of the rev range
  with it, needed (as briefed) two openings per head. The best engine is the one with the most mouths bolted on, which looks bad and is
  dull to build.
- **Direction.** Openings still matter, but each extra one per head adds much less than the last, and a head is close to
  full breathing with about one opening each. Crowding openings on the same pipe run is penalised, so a tidy layout beats a
  forest. The exact curve is chosen by measuring a dyno sweep (6 cylinders, 1 to 4 openings per head), not guessed.
- **Where.** `EngineLayout.PortNetwork.flowFactor()`; the result feeds `CylinderModel.intakeFactor` and `exhaustFactor` in
  `EngineController`.
- **Moves guarded numbers.** `compat/behaviour.txt` and `compat/trajectories.txt` are regenerated by hand in the 2.0
  change, and the diff is shown. It goes under Exceptions in the 2.0 release record. Needs new tests for the curve and a
  save of an old world to confirm it still loads.
- **Do not ship in 1.x**, not even behind a default-off option, unless the author says otherwise.

### Altitude-dependent intake (Aeronautics compat, breaking, 2.0 only)

**Built 2026-11-18** (`engine.sim.Altitude`, `EngineSimulation.setAmbientDensity`, `compat.altitudeThinning`, default on). Density is 1 up to
y 128, then falls by a factor of e every 1200 blocks to a floor of 0.2 (0.85 at the vanilla build limit). It starts every cylinder's
intake pressure, so a compressor multiplies what reaches it. The numbers are a first choice: the one measurement taken was a
four-cylinder crude engine losing 43 percent of its net peak torque at density 0.7. It uses the block's Y, not Aeronautics' altitude, and
applies in vanilla worlds too (answering the open question with the config switch). `compat/behaviour.txt` and
`compat/trajectories.txt` did not move: the simulation's default is sea level. In-world, an engine above y 128 is the only thing it changes.

Queued 2026-11-17, author's decision: held for 2.0 so 1.x builds keep their numbers.

- **Why.** For the Create Aeronautics compat, intake air should thin with altitude, so a naturally aspirated engine loses
  power as it climbs and turbos and superchargers at high altitude become a must, not an extra.
- **Direction.** The intake charge is scaled by an air-density factor from the engine's Y level (or the Aeronautics
  altitude where that mod is present). Forced induction offsets it through the existing boost stages in `applyInduction`.
  The curve and the altitude at which it starts to bite are chosen by measuring, not guessed.
- **Moves guarded numbers** for any engine above the start altitude, so `compat/behaviour.txt` and
  `compat/trajectories.txt` are regenerated by hand in the 2.0 change with the diff shown, and it goes under Exceptions in
  the 2.0 release record. Needs new tests for the curve and a check that an old world still loads.
- **Still to decide:** whether it applies in vanilla worlds without Aeronautics (build-height engines would suffer), or
  only when Aeronautics is loaded or a config option is on.
- **Do not ship in 1.x**, not even behind a default-off option, unless the author says otherwise.

### Battery depth (2.0)

Queued 2026-11-19, author's decision: for 2.0, not 1.x. Today a battery is one 200-spark bucket
(`BatteryBlockEntity.CAPACITY`): each plug firing draws one spark (`BatteryIgnition`), an alternator refills it, and the
goggles show a percentage. Five additions. Because this is 2.0, a new behaviour may be on by default; any that moves
guarded numbers (`compat/behaviour.txt`, `compat/trajectories.txt`) is regenerated by hand in the 2.0 change with the diff
shown, and listed under Exceptions in the 2.0 release record. Each item ships with its tests, lang entries, goggle lines
and a Ponder mention.

**Status 2026-11-19: all five built, on branch `2.0-battery-depth`** (author chose "all five" and cold-charge on by default). Details and the
numbers are in `docs/DESIGN.md` ("Battery depth") and the v2.0.0 record. One change from the text below: item 2's slow-crank half was built
and dropped, because a starter slowed by a flat battery stopped engines that start from the flat battery they are placed with from
catching (seven existing GameTests failed); cranking now only draws charge, which falls with the bank. Still to do: look at the lamp strip
in the game (the models were generated and tested, not looked at), and the author's approval of the numbers.

| # | What | Notes |
| --- | --- | --- |
| 1 | **Cold-weather charge.** A cold engine block makes each spark cost more charge, and a battery in a cold biome or beside an unheated block gives less of its capacity. A heat source (the block heater, the heat exchanger) is then worth fitting for the battery's sake too. | Reuses the block heat and `PREHEATED` already modelled. `SPARK_COST` is 1 today; whether the new cost is on by default is for the author to decide. |
| 2 | **Cranking draw.** The starter draws charge while it cranks, not only the plugs while they fire, so a weak battery cranks slowly and gives up partway: hand-crank it or charge it from another engine. | **Needs the author's call before it is built.** The Declined list rules out new failure modes, and rule 3 says no release adds a new punishment. A flat battery already stops a spark engine, so this is a gentler version of that consequence, but it is the item closest to the line. Check first how the starter and the alternator-on-the-crank-line cranking work today. |
| 3 | **Comparator output.** A battery reads out its charge level (0 to 15) to a comparator, like the sump, for low-charge alarms and auto-start logic. | New `getAnalogOutputSignal`. Comparator only, following the engine-management rule: no redstone input. |
| 4 | **Charge gauge.** The goggles gain a draw rate and an estimate of sparks left at the current rate, and the block shows its charge on the model (a gauge or a lamp strip), so it can be read without goggles. | Model and texture built from Create's own. The charge is already synced to the client (`SYNC_INTERVAL`), so no new data. |
| 5 | **Pooled battery banks.** Batteries that touch join into one bank, like Create's tanks: capacity is the sum, and charging and drawing share across it, so capacity scales with the size of the build. | A new connected-block structure, so the most testing: joining and splitting as blocks are placed and broken, save and reload, an engine touching any one member, chunk borders. A lone battery is a bank of one with today's numbers. A bank must record and rebuild through blueprints and the Bank Builder. |

Considered and left out for now: battery tiers (dry cell, large bank), jump leads, aging and sulphation, a tapered
alternator curve, and non-engine consumers. Aging and the taper change how existing batteries behave, which 2.0 allows,
so they could be added later if wanted.

### Part depth (2.0)

Queued 2026-11-19, author's decision: for 2.0. More to do and more to read on parts that already exist, plus one new
wearable. Nothing here adds a failure mode (the Declined list stands). Each item ships with its tests, lang entries, goggle
lines where there is something to read, and a Ponder mention where there is something to explain. Anything that moves
guarded numbers is regenerated by hand with the diff shown and listed under Exceptions in the 2.0 release record.

**Status 2026-11-19: items 1 to 4 built on branch `2.0-battery-depth`** (the same branch as battery depth; details in `docs/DESIGN.md`, "Part depth, first four
items"). Item 3 turned out to need only a readout: alternators already add up. Item 4 has no efficiency range to show, so it shows boost against the limit.
Items 7 and 8 are not started here; 6 is done, and 5 was done as particles only (see its row). **Update 2026-11-20:** item 8 (Auto shift profiles, the author approved the new dial in chat) and items 9 (Black Box replay) and 10 (gear and fault display sources) are now built on the
same work, branch renamed `2.0-part-depth` (see "Part depth, Black Box replay and the last two display fields" in `docs/DESIGN.md`). Item 7 (headphones) was built 2026-11-20 on the same branch (see the table and "Noise-cancelling headphones" in `docs/DESIGN.md`).

| # | What | Notes |
| --- | --- | --- |
| 1 | **Starter: hand-crank assist.** A stuck or flat engine can be turned over by an outside shaft (a water wheel, a windmill, a hand crank), so a dead battery is something to work around, not a dead end. | Check first what the starter accepts today (`StarterBlockEntity`) and how the alternator-on-the-crank-line cranking works; this may mostly be letting one more source count. Pairs with battery depth item 2. |
| 2 | **Alternator: output readout.** The goggles show charge made per second and the share of the engine's torque it is taking, not only whether it is loaded. | Information only; the numbers already exist in `AlternatorBlockEntity` (`CHARGE_PER_RPM`, the stress it adds). |
| 3 | **Alternator: several on one engine.** Alternators on the same engine or bank share the charging between them, and the readout says how each contributes. | An alternator already fills the batteries touching it and those of its engine. The work is how two or more split the load and what each reads out, and the test that the total stress is what the sum should be. Pairs with pooled battery banks. |
| 4 | **Turbo and supercharger: compressor readout.** The goggles show where on its efficiency range the compressor is working, so the limit and anti-lag dials can be set by reading, not by guessing. | Information only. Check what `TurbochargerBlockEntity` and `SuperchargerBlockEntity` already hold; if there is no efficiency figure, the readout is built from the numbers that exist, not invented ones. |
| 5 | **Turbo and supercharger: wastegate and blow-off parts.** **Changed 2026-11-20 at the author's say: no parts, particles only.** The turbocharger's blow-off now throws a burst and then a thinning jet of air (longer and fuller for more boost) instead of one puff, and the wastegate chuff, which had only a sound, gets a small grey puff off the turbine end. Turbocharger only; the supercharger is not changed. Built (`VentJet`, `VentJetTest`), not seen in game. | Cosmetic only: never changes an engine's rating (rule 4). No new blocks, items, models, textures or sounds; `CEClientConfig` particles and effect density still apply. |
| 6 | **Cast flywheel redesign.** A new look for the cast flywheel (`CastFlywheelBlock`): model, texture and the visual that spins it. **Status 2026-11-19: built and seen in the smoke screenshots (Ponder, in world, inventory icon).** `tools/generate_models.py` `cast_flywheel()` draws a thin spoked wheel with the existing textures, so no new texture was needed; the collision shape did not need to change. Ponder and Flywheel-off had never drawn the wheel (the fallback renderer drew only a shaft); `EnginePartRenderers.CastFlywheel` now does. Still to do: look at it in motion and with Flywheel off and Sodium, and the author's approval. | **Scope is the look, unless the author says otherwise.** The mass it adds and its place on the crank line stay as they are, since changing them moves existing engines' numbers. Chunky, built from Create's own textures (rule 5). A new shape means a new shape for the collision box, goggles and Ponder, and the same block ID stays. |
| 7 | **Noise-cancelling headphones.** A wearable that muffles everything the wearer hears from the world, not only this mod's sounds: quieter and duller, like hearing the world through ear defenders, never silent. **The game's music is not muffled** (author's decision 2026-11-19), and neither are menu and interface sounds: only sounds that come from the world. Worn like Create's goggles: in the head slot, and in a Curios slot when Curios is installed. **Built 2026-11-20, tuned by no ear yet:** `HeadphonesItem` (`create_engines:headphones`, in the head slot by use, or in Curios' `head` slot through the `curios:head` tag), `HeadphoneMuffle` (the sound hook, below) and `Muffle` (the rules, unit tested); a model drawn for the head slot, a shaped recipe (3 iron sheets, 2 brass sheets, 2 wool), a Ponder page, goggle-style tooltip lines, the Curios renderer, and tests (see "What was built" in `docs/DESIGN.md`). Two deviations from the plan below: playing sounds are changed through the sound engine's own table of playing sounds (`SoundEngine.instanceToChannel`, which hands each sound's handle, a safe hop to the sound thread that does nothing once the sound has ended) rather than a list of channels of this mod's own, and the no-filter fallback caps each world sound's loudness (`AL_MAX_GAIN`), which is a cut at the top and not a proportional one. The access transformer is three lines (`Channel.source`, `SoundEngine.instanceToChannel`, `SoundManager.soundEngine`). | A new item, so new IDs that join the promise at 2.0. Client-side only. **Muffled, not muted** (author's decision 2026-11-19): the sound is rolled off at the top (a low-pass filter) as well as turned down, and every sound, warnings and stings included, is still heard, just dulled. It covers all sounds, so it cannot sit only in this mod's own loop code (`EngineNoteSound`, `MachineLoopSound`, `LoopSource`); it needs a hook on the game's sound engine. **Checked 2026-11-19, feasible without a mixin.** Minecraft 1.21.1 never uses OpenAL's filter extension itself, but its OpenAL (LWJGL 3.3.3, OpenAL Soft 1.23.1) offers it: a standalone probe on the author's machine got `ALC_EXT_EFX`, made a low-pass filter, set its gain and high-frequency gain, attached it to a source as the direct filter and detached it, with no OpenAL errors. OpenAL has no listener-wide filter, so it is one filter attached to every source. The route: NeoForge's `PlaySoundSourceEvent` (sounds that load whole) and `PlayStreamingSourceEvent` (streamed ones: this mod's engine note, music) hand over the sound's `Channel` on the sound thread right after it starts, so a muffle is attached there, but only to sounds from the world. The event also gives the `SoundInstance`, whose sound category (`SoundSource`) decides: the Music category (the game's music) and Master (menu and interface clicks) are left alone, and so is Voice, so a voice-chat mod stays clear; Blocks (this mod's engine and machine sounds), Ambient, Weather, Hostile, Neutral and Players are muffled. **Records** (a jukebox or a note block) sit in the world, so they are muffled too unless the author says otherwise. A mod that plays its own music under another category would be muffled; the category list is the one place to change that. A list of live channels is kept so putting the headphones on or off changes sounds already playing (run on the sound thread, since the engine's own channels are only touched there; skip a channel once it stops, because Minecraft destroys the channel when a sound ends). `Channel` keeps its OpenAL source number private, so this needs an access transformer on that field (and on the sound engine's executor field, for the thread hop), a file the mod does not have yet; or reflection. A filter made on one OpenAL context is not valid on another, so it is made again when the sound engine restarts (resource reload, output device change). If the device lacks the extension, the headphones fall back to a volume cut only, and the goggle-style tooltip says so. **Still to check by ear:** the right filter numbers (the probe used 0.5 gain and 0.05 high-frequency gain, untuned), and that the streamed engine note and music muffle without clicks when the filter is attached mid-sound. Curios stays optional: the compatibility goes in `compat`, nothing in the mod names a Curios class when it is absent, and it gets a row in the compatibility matrix like KubeJS and CC. Needs a model, a texture and an armour or accessory render, a recipe, a Ponder page, and tests that a worn pair muffles and lowers every world sound, leaves music and interface sounds untouched, an unworn pair changes nothing, a muffled sound is never silent, and the Curios slot works. |
| 8 | **Transmission: Auto shift behaviour.** The Auto mode gets a shift profile (economy, normal and sport) that changes where it downshifts, climbs and how long it waits. **Approved by the author 2026-11-20 ("it can have a new dial"), built the same day:** `AutoGearbox.Profile` (economy 0.95/0.85/0.8, 15 and 20 ticks; normal, the original 0.9/0.8/0.7, 10 and 30; sport 0.75/0.6/0.5, 5 and 60), a second value box on the Transmission (`shiftProfile`, saved as `ShiftProfile`, default Normal), the dial `shift_profile` in `EngineDials`, a goggle line while on Auto and a Ponder text. Not seen in game: where the second value box sits on the face (2.5 voxels up, under the gear's), and how each profile feels. | Normal keeps exactly the old numbers (a test pins it against the old entry point), so existing builds shift the same and a box saved before profiles reads as Normal. Rule 4's exception for a new dial is the author's say-so above. Tests: `AutoGearboxTest` (profile ordering, each settles at every load, never hunts, own waits, dial mapping) and a GameTest for the default and the save keys. |
| 9 | **Black Box: replay.** Scrub back through a recorded run to see what the engine was doing before a stall: speed, load, faults. **Built 2026-11-20:** a Replay dial on the box (5 seconds a notch, the whole ten minutes) shows the state, speed, load, warmth and fault at that moment; the moment holds still while the tape runs on. The tape now also keeps the fault a second at a time. Not seen in game. | Information only, on the existing recorder (`BlackBoxBlockEntity`). Check how much it already keeps. |
| 10 | **Display Link: more fields.** More engine facts out to Create's displays. Today there are fill, horsepower, speed, status, the sump and, added 2026-11-19, boost, rated torque and battery charge. | Chosen from what `EngineReadout` already works out. **Built 2026-11-20:** Engine Fault (every crankshaft block) and Transmission Gear (the transmission), each a new display source with its own test and lang entry. |

### Electric power (2.1 and 2.2)

Queued 2026-11-23, author's decisions in chat. Batteries had no use beyond plugs and the starter, so the bank gets a real
consumer and a choice of size. **Row 1 is in 2.1; rows 2 and 3, the battery tiers and the Electric Motor, are for 2.2 (author, 2026-11-23).** New blocks and dials
only, so nothing existing moves. (2.0 had already shipped when this was queued.)

| # | What | Notes |
| --- | --- | --- |
| 1 | **Alternator charge-rate dial.** **Built 2026-11-23, not seen in game.** Four settings, 25/50/75/100% of full output; the charge made, the Forge Energy made and the stress on the shaft all scale with it, so a lower setting is a lighter load for a smaller charge. 100% is the default and what every alternator did before, a saved alternator with no key reads as 100%. Dial `charge_rate` in `EngineDials`. | `ChargeRate` (unit tested), `AlternatorBlockEntity.chargeRateDial`, a goggle line below 100%, a GameTest for half the stress and the saved key. |
| 2 | **Battery tiers (moved to 2.2 with the Electric Motor, author, 2026-11-23).** Built 2026-11-23, not seen in game, then taken out of 2.1: the **Dry Cell** (100 sparks, the cold keeps nothing back) and the **Large Battery** (800 sparks, the cold keeps back up to three quarters; recipes: dry cell is zinc ingot, copper sheet and redstone, large is three batteries and a brass casing). Both join into one bank with the plain battery when they touch. | The tier machinery stays in the tree with `PLAIN` only: `BatteryTier`, `BatteryChemistry.deliverableLimit(.., coldLoss)` (unit tested), `BatteryBlock.tier()`, `BatteryBlockEntity.capacity()/room()`, `BatteryBank`; the plain battery is exactly what it was (`BatteryTierTest` pins it). Removed block files are saved in `../create-engines-deferred/dry-cell/` and `/large-battery/` (blockstate, loot table, recipe, models, the old test files). **To return them:** add `DRY_CELL(100, 0)` and `LARGE(800, 0.75)` to `BatteryTier`, the blocks in `CEBlocks` and `CEBlockEntities`, the `dry_cell` and `large_battery` kinds in `tools/generate_models.py`, goggle lines, recipes, lang, manifest lines and tier tests. **Needed first:** a Ponder page that explains the tiers (the author found the existing scene does not: it only lists the blocks as components), and release exception 2.7. |
| 3 | **Electric Motor (2.2).** A standalone shaft source that draws sparks from the bank it touches and turns them into rotation. **Standalone: not a crank throw, no pistons, not on the crank line** (author's decision). Runs underwater (author's decision: "sure underwater is ok"; the rule against running engines underwater was for combustion). Speed set by a value box and also by a redstone setpoint, like the governor (author: "it can have both"). Hybrid use is allowed through Create's shaft network: the motor and a combustion engine's output can drive the same shafts, and an alternator on the engine charges the bank the motor draws from. | Each conversion loses some, so an engine, alternator and motor loop never makes energy. Needs tiers first, because a motor's draw would flatten a 200-spark battery in seconds. **The battery tiers (Dry Cell and Large Battery) ship in 2.2 with the motor**: see row 2. |

### Optimization pass

**Status 2026-11-19: partly done.** Built: no per-tick starter list or carried-motion capture when nothing changed, engine parts skip
posing outside the view frustum, camshaft lobes pose without a per-frame array. Everything else below, including animation level of
detail, sync-on-change, sound voice caps and the per-item measurements, is not done. **Measured 2026-11-19 (`PerfBudgetTest`, this machine):** the
simulation costs 0.021 to 0.106 ms a tick for a 16-cylinder turbo engine (64 to 4096 RPM) and 0.002 to 0.026 ms for a 4-cylinder one, with
no allocation on the hot path, and engine sync already runs every 10 ticks. **Server profile, 2026-11-19:** JFR on `runGameTestServer` (3632 server-thread samples, 346 tests). In the steady 16-engine crowd the
mod's wall time is 0.62 ms a server tick (0.04 ms an engine, about 1% of the 50 ms budget for sixteen), and within it `CylinderModel.step`
and `EngineSimulation.advance` are nearly all of it: the physics, already table-driven with no allocation. The rest of the mod's samples across
the whole run are start, stop and rebuild work that the tests cause (Create's rotation propagation behind `publishSpeed`, which is already
rate-limited; `CrankCoupling` at scan time; `governedAir` and `pilotLights` are tiny per-step methods whose share is sampling skew). Nothing to
change without changing the simulation's results, which the baselines forbid.

**Client profile, 2026-11-19:** Java Flight Recorder on `runSmokeAudit` (99 s, 1395 CPU samples, 15014 allocation samples; frame p50 8.35 /
p95 10.67 / p99 12.69 ms, 6681 frames). Shipped mod code is about 7% of the main thread's samples, 16% of the server thread's and 70% of the
sound thread's (which is 64 samples, under 2% of one core): the rest is Minecraft's own biome lookup, chunk access, lighting and
world generation. Inside the mod the largest item is `EngineNoteSynth.next` (37 samples, the engine note, on its own thread and required),
then `CylinderModel.step` and `TurbochargerRenderer.renderSafe` (8 each) and `CrankshaftBlockEntity.publishSpeed` (4). Allocation: the mod is
1.7% of what was sampled (0.27 of 16 GB); the largest items are a one-off class-load (`reportInduction`, 41 MB of ASM frames), Create's own
`updateGeneratedRotation` called from `publishSpeed` (BlockPos, 17 MB; it already skips when the speed is unchanged) and `SharedCasingModels.digest`
(13 MB, at model bake). **No change was made: nothing in the mod's own code is a cost worth the risk.** The remaining items (animation level of
detail, voice caps, sync deltas) have no profile behind them, so they are not built; if the author wants one anyway it is a taste decision.

Queued 2026-11-16. Make the mod cheaper to run on both sides, so large engine banks and busy servers cost less.
Behaviour does not change: the same engine produces the same torque, fuel use, heat and readouts before and after.

- **Culling (client).** Skip rendering and animation for engines out of view, behind walls or far away, and skip
  particle, sound and flame work nobody can see or hear.
- **Animation level of detail.** Pistons, cams, valves, crank throws and flywheels animate in full up close and with
  fewer moving parts, or none, at distance. Fast spinning parts stop being drawn per frame where the motion can no
  longer be told apart.
- **Eliminate redundant work.** Recompute only when something changed: cache the engine scan until a block in it
  changes, keep derived stats and goggle text until their inputs move, and stop re-sending data the client already
  has (sync only on change, not every tick).
- **Eliminate inefficiency.** Profile first, then fix what the profile shows: allocation in tick and render paths,
  repeated lookups, work done for engines that are stopped or idle, and anything that grows with engine count faster
  than it should.
- **More ideas, each to be proven by a profile before it is built:**
  - *Server tick.* Engines already publish and sync on intervals; check that stopped, idle and fully loaded engines all
    skip the per-tick sim work they do not need, that a steady-state engine (same speed, load and fuel) sleeps its
    publish and capacity checks, and that a bank of many engines does not rescan or resync as one.
  - *Scanner.* Rescan only when a block in the engine changed, and only the part that changed; avoid the full
    `EngineScanner` walk on chunk load, on neighbour updates that change nothing, and on Timing Tool use where the
    cached layout is still good. Chunk-aware lookups instead of repeated `getBlockState` and `getBlockEntity` walks.
  - *Simulation.* Keep the high-RPM step cap, lookup tables and incremental dyno as the model; look for per-cylinder
    allocation, boxing, repeated trig or curve evaluation that a table or a cached value covers, and cylinders that
    are identical and could share one calculation.
  - *Network.* Send deltas, not whole state; round and skip values that did not move enough to show; batch per
    engine instead of per block; keep goggle and readout data off the wire until someone looks.
  - *Sound.* Cap concurrent engine note voices and drop the farthest, pause synthesis for engines out of earshot,
    reuse buffers, and keep the streamed note off the render thread (the 1.2.0 out-of-memory fix is the standard to
    hold).
  - *Particles and effects.* Cap particles per engine and per frame, thin them with distance, skip exhaust, smoke
    and flame for engines out of view, and skip them for engines that are stopped.
  - *Renderers.* Batch the moving parts of one engine into as few draw calls as possible, cache baked models for
    parts that only rotate, avoid per-frame transform maths for parts that are out of sight, and make sure connected
    casing does not rebuild its model on every neighbour change.
  - *Chunk and memory cost.* Smaller block entity save data, no per-engine caches that outlive the engine, and no
    growth in the smoke harness's fleet leak trends.
  - *Startup and loading.* Texture and model count, data load time, and Ponder scene setup that runs before it is
    opened.
- **Measure, don't guess.** Each change comes with a before and after number from the smoke harness's fleet trends
  or a profiler run, and anything on a tick or sound path keeps its soak and hostile-input tests.
- **Rule:** the behaviour baseline and the legacy-world GameTests must pass unchanged. An optimization that needs a
  behaviour change is raised as a decision, not folded in.

### Getting there safely

Status 2026-11-19, checked against the code:

- ~~The last 1.x release logs a warning for everything 2.0 removes or renames.~~ **Dropped 2026-11-19 (author).** 1.4.0 shipped without it, and
  2.0 migrates advancements, config and dye itself.
- Before 2.0 ships, a world saved on 1.0 and one saved on the last 1.x are both opened in it. **Not done by hand**; the legacy-world
  GameTests cover the 1.0 to 1.3 fixtures, and the author reports opening one real 1.x world (not recorded which).
- The preview formats from 1.4 are frozen here. **Done 2026-11-19:** the `api` package (`ApiVersion` 1.1.0, `ApiSurfaceTest`, `docs/API.md`), and now the
  KubeJS names (the `CreateEngines` global, `engineAt`, the two script events and what an event gives a script) and the keys of the `fuel`
  and `part_stats` data maps, read from the source by `ScriptAndDataSurfaceTest` against `compat/script-surface.txt` (append only; a new name
  needs `SURFACE_REGEN=1` and a bump of `ApiVersion.MINOR`).

## Verification track (no release of its own)

Queued 2026-11-16. The goal is to trust a release with as little manual testing as possible. Each item lands as its own
piece of work, in whichever release has room, and none changes behaviour. What stays manual is taste: how it sounds,
looks and feels. Tests only guard against those changing by accident.

**Done 2026-11-16 (unit-test level, in `./gradlew check`, CI in `.github/workflows/verify.yml`):**

- Item 2, in part: `SimPropertyTest` (replay on another thread, no fuel means no power, fuel conserved, identical twins).
  `SimStressTest` already held the finite and bounded invariants. Step-size independence is `StepIndependenceTest`: the
  step policy is a parameter of the sim (`useStepSizes`, package-private, defaulting to the live constants) and the same
  engines are driven at the live steps, at the calibrated 4 degrees and at 1 degree. The gas work agrees to under half a
  percent of the engine's output at every speed from 256 to 8192 RPM; net torque and fuel rate agree up to 1024 RPM. **Not
  step independent, found by it:** above about 1500 RPM the per-cycle net torque and fuel rate move with the step (a 4
  cylinder spark engine's net torque at 2048 RPM is -4.3 at the live steps and -9.9 at 4 degrees; an 8 cylinder turbo
  diesel's fuel rate at 8192 RPM is 53.8, 24.0 and 15.3 at the live steps, 4 and 1 degree). Create's own limit is 256 RPM,
  so nothing shipped is near it, but the high-RPM optimization pass should look at the cycle bookkeeping.
- Item 5, the architecture rules, as `ArchitectureRulesTest` (reads imports, no ArchUnit): the sim and the smoke core
  import nothing from the game, and the 14 common classes that touch client code are a fixed list that may only shrink.
  Not done: the dedicated-server and packaged-jar boot.
- Item 7, the sim's share: `PerfBudgetTest`, baseline 0.075 ms per tick for 16 turbo cylinders, 0.019 ms for 4, zero
  bytes allocated; budgets are about 7x that. Not done: the fleet numbers from the smoke harness as build limits.
- Item 9, the sim's share: `ConfigMatrixTest` runs every `Tuning` setting at its config minimum and maximum, alone and
  in six combined patterns, and fails if the ranges in `CEConfig` or the set of settings drift from the matrix. Not
  done: the matrix through the GameTests and the smoke audit. **Widened 2026-11-19:** the sim-facing settings that are not
  `Tuning` fields (the inertias, bearing friction) run in
  `ConfigMatrixTest.inertiaAndFrictionAtTheConfigExtremesLeaveAnEngineSound`;
  `everyConfigSettingIsInTheMatrixOrNamedHere` fails for a setting added to `CEConfig` that is in neither the matrix nor a
  list saying where its extremes are run; and the GameTest `SmokeAuditGameTests.everyConfigSettingAtItsExtremesLeavesAnEngineSane`
  runs the stress scale, the inertias, bearing friction, the four scan limits and the alternator at both ends and in three
  combinations with an engine running (75 checks, all passing). Not done: the matrix through the client audit's fleet,
  whose checks (an engine that runs, a full tank) are not meant for a fuel rate of twenty times.
- Item 11: the `datagen-idempotence` job in `verify.yml`. It runs on every push and has caught a real stale file (1.3.0's advancement files, fixed in the commit after); it passed on master on 2026-11-17 and 2026-11-18.
- Item 12: JaCoCo on the unit tests, floor in `src/smoke/coverage-floor.txt` (was 15%, measured 15.4%; that is what CI's
  `check` holds). **Changed 2026-11-21 (author):** the unit ratchet now leaves out the test harness packages, as the merged one
  does, and the floor is 21% (measured 21.3%). With the harness counted it had fallen to 14.6% as GameTests were added (the harness
  was 37% of the instructions counted), which `build`, and so the publish workflow, would have refused; the harness is test code
  and no unit test can reach it. **Merged 2026-11-19:** the GameTest server and the client audit carry the JaCoCo agent too (limited to this
  mod's classes), and `./gradlew coverageMerged` (also a part of `smoke`) runs the unit tests, the GameTests and the audit,
  joins their data into `build/reports/jacoco/merged`, and holds a second floor, `src/smoke/coverage-floor-merged.txt`
  (84%, measured 84.4%). The measure leaves out the harness packages (GameTests, smoke audit, showcase), so it says how
  much of the mod runs: `engine/sim` 99.7%, `engine/topology` 98%, `ponder` 100%, `registry` 74%, `content/tool` 63%,
  `client` 62%. It is not run on GitHub (it needs a game window); the floors are raised by hand when they improve.
- Item 1: `./gradlew pitest` (about 6 minutes, not part of `check`) runs PIT through its command line, because the Gradle
  plugin does not work with Gradle 9. First report: 1854 mutants in `engine/sim`, 1521 killed, 294 survived, 23 reached
  by no test. The ratchet `src/smoke/mutation-ratchet.txt` was 317 and may only go down. It was 177 after the `*ExactTest` classes, `SimTrajectoryTest` and the refactor below, 170 at 2.0, then 134 (2,029 of 2,178 mutants killed, 93%) after `BoundaryEdgesTest`, which pins the exact edge of the comparisons in the stall speed, the step, the heat levels, `FuelCheck` and the oil. Most of what is left is mutants that cannot be killed: a `x * 1` against `x / 1` (`Roughness`, `FuelSpec.preignitionLoss` at their shipped constants) or a `<` against `<=` at a point where both sides of a continuous function agree (`Mixture`). The limit is now 130 after `SurvivorEdgesTest` (127 live measured on 2026-11-19, 2,110 of 2,252 killed; the gate swings by about 3 from run to run, so the limit keeps that much room; see the v2.0.0 record). `tools/pit_survivors.py` lists what is live by class and line. Survivors were clustered in
  `EngineSimulation` (81), `CylinderModel` (59), `ColumnShape` (33), `Thermal` (24) and `Governor` (20); killing them is
  the follow-up work. The slow fuzz tests are excluded from the mutant runs.
- Refactor of the simulation core (behaviour-preserving, guarded by `behaviour.txt`, `trajectories.txt`, `PerfBudgetTest`
  and the GameTests, all unchanged and green): `Induction` (turbos, blowers, intercoolers and how they set each
  cylinder's air) and `SwingTracker` came out of `EngineSimulation` (727 to 584 lines). `CylinderModel` (733 to 439
  lines) was split into `Charge` (the gas, shared by the rest), `Ports` (intake and exhaust exchange), `Combustion`
  (injection, ignition, burn) and `Soiling` (plug fouling and exhaust soot). Direct tests: `ExtractedPartsTest` and
  `CylinderStateTest`. **Cost:** the gas split makes the 16-cylinder step about 2 to 4 percent slower in
  `PerfBudgetTest` on this machine (0.083 against 0.081 ms at 1024 RPM, 0.102 against 0.098 ms at 4096 RPM, measured
  against the original in the same session); allocation per tick stayed at 0 bytes once the stage lists were walked by
  index and the parallel-stage maps reused. Accepted by the author on 2026-11-16 ("we'll optimize later"): it is about 0.004 ms a tick for sixteen
  cylinders, to be recovered in the 2.0 optimization pass, which should start from `PerfBudgetTest`'s numbers. Next cut if
  wanted: in `EngineSimulation`, the oil wash and friction measuring.
- Item 6: `EngineNoteConcurrencyTest`: 12 voices read while a game thread feeds them hostile state; a read allocates
  almost nothing; direct memory returns to its start after 9000 voices of churn; a voice replaced mid-read finishes.
  Put the 1.2.0 per-read `allocateDirect` back and two of the four fail. Not covered: the sim itself under threads
  (it is single-threaded by design), and the real sound engine.
  **Expanded 2026-11-19** to the state the sim and the logic around it share between threads: `SharedStateConcurrencyTest`
  (a config reload rewrites every `Tuning` field and a data reload swaps `PartEffects` while engines on other threads are
  built and run; the cylinder and cam tables, emptied and asked for by eight threads at once, must come back as one shared
  table; the same seeds on eight threads at once must give the one answer a single thread gives, which a shared scratch
  buffer would break), `StingGateConcurrencyTest` (twelve asks at the same instant let at most one goal and one challenge
  through) and, in `EngineNoteConcurrencyTest`, the engine note and the advancement stings rendered on eight threads at
  once, identical to rendered alone. Still not covered: the real sound engine, and the client's render thread against the
  server tick.
- Item 3: `MonkeyGameTests`, three seeds (plus `CE_MONKEY_SEED`), about 1,185 random actions each around two running
  engines (place, break, right-click with random items including the wrench, sneak, redstone, neighbour updates, random
  ticks, save and load round trips), then clear the area, assert no block entity is left and that a fresh engine still
  runs. A planted exception in the crankshaft's click handler failed all three seeds. Not covered: chunk unload (the
  eight chunk GameTests do that) and real players' packets.
- Item 8: `CodecFuzzGameTests`: the sting packet, both particle codecs, the jerry can fluid and the refining recipe
  round-trip to the same bytes, then take every truncation, 400 mutations and 400 garbage inputs each, and their JSON
  forms take 1,500 malformed documents each. A planted write/read mismatch produced 80 failures. Only this mod's own
  codecs are covered; Create's and NeoForge's are theirs. **Expanded 2026-11-19:** the fuel data map (its sync packet and its
  JSON), the part stats data map and the advancement trigger's JSON join the GameTest; `BlueprintCodecFuzzTest` fuzzes the
  blueprint text a player pastes or a client sends (every truncation, 3,000 damaged strings, garbage, damaged compressed
  bytes with a decompression bomb, and 6,000 damaged bodies that reach the part reader, each refused with a
  `BlueprintException` or read back cleanly); `HostileFuelTest` and `PartEffectsExtremesTest` follow the data past the
  codec into a running engine, which is where the two defects below were found. Hostile block-entity saves were already
  covered by `StressGameTests`.
  **Two defects found and fixed 2026-11-19:** a fuel with a non-finite number (or `air_per_mb` of 0, or `energy_per_mb`
  of the largest double) made an engine's figures NaN, and part effects whose compression scales multiply a cylinder's
  compression ratio below 1 (two parts at 0.1, three at 0.3, four at 0.5) made its gas work NaN. `FuelSpec.usable()` now
  stands between a pack's fuel and the sim (it replaces only the four figures that did this, returns every sound fuel
  itself, and leaves the record alone so `FuelCheck` still reports what the pack wrote), and `CylinderSpec.build` holds the
  ratio at `PartStats.MIN_COMPRESSION`, the floor a part_stats retuning already had. No shipped fuel or cylinder is touched;
  `compat/behaviour.txt` and `compat/trajectories.txt` did not move. Both are now plain assertions in `HostileFuelTest`,
  `FuelSpecUsableTest` and `PartEffectsExtremesTest`.
- Item 4: nine fixtures from the real releases (diesel, spark and rotary engines from 1.0.0, 1.1.0 and 1.2.0) in
  `src/test/resources/compat/world`, recorded by `scripts/record-corpus.ps1` from each tag's own source, and loaded by
  `LegacyWorldGameTests`. Each must load, keep every saved key and keep running. They are block and block-entity saves,
  not whole world folders, so region files, chunk and player data are not exercised. v1.0.0 fails one of its own
  GameTests (a transmission ticking loads an unloaded chunk), which the current tree does not.
- Item 5, the jar half: `PackagedJarTest` reads the built jar's bytecode for client references from common classes and
  checks the mixin config, filled-in metadata and resources. It found nothing beyond Registrate's renderer lambdas in
  `CEBlockEntities`, which are listed with the reason. A dedicated server booted from the jar is not done (the build
  makes no NeoForge server install); the GameTest server is the server-only run.
- Item 7: the GameTest fleet budget is 2 ms for 16 engines (measured 0.4 ms), five times the baseline. **Added
  2026-11-19:** the same crowd test measures what the server thread allocates per tick (45 KB for 16 engines, the whole
  tick) and fails above 256 KB; and the client audit's leak trends, which already failed the build, were far looser than
  anything measured (200 MB of heap growth, 48 MB of direct memory), so they are now 50 MB and 16 MB, set from two runs that
  grew the settled heap by 6.5 and 2.5 MB and shrank direct memory by 1.3 MB (the audit now reports the fitted growth as a
  metric). After changing these audit checks the ten-break mutation script (`scripts/smoke-mutations.ps1`) was not re-run.
- Item 9: the GameTest matrix also runs four combined patterns (all minimum, all maximum, two seeded mixes).
- Item 10, in part: `./gradlew compatMatrix` runs the client audit with Flywheel off and with Sodium 0.8.13; both
  green (see `docs/COMPAT.md`). The matrix has since grown rows for EMI, KubeJS and CC: Tweaked (all in `compatMatrix`), a
  JEI row run by hand (JEI wants a newer NeoForge than this build) and an Aeronautics row that is not in `compatMatrix`
  while it is red (`build.gradle` and `docs/COMPAT.md` say why); there is one supported Create version today.

**Not started:** 13 (goldens, set aside by request), and the 60-minute unattended soak of `STABILITY.md` 2.4. Still open
inside done items: a server booted from the jar (item 5), the config matrix through the client audit's fleet (item 9), the
PIT survivors that are not equivalent mutants (item 1, 134 live), and the engine figures that move with the step above 1500 RPM (item 2).

1. **Mutation testing (PIT) on `engine/sim`.** Small changes to the code (a flipped comparison, a dropped term) must make
   a test fail. Survivors show assertions that never bite. The sim is pure Java, so it is cheap to set up. Report first,
   then ratchet: the surviving-mutant count may go down, never up.
2. **Property and fuzz tests on the sim** (seeded, so every failure replays). Random engines, fuels, boost and tuning
   must keep these invariants: no NaN or infinity, torque and RPM stay in range, the same seed gives the same run, and
   results do not depend on step size beyond a tolerance.
3. **Monkey GameTest.** A seeded random run of place, break, right-click with random items, wrench, redstone and chunk
   unload against engine blocks. It asserts no exception, no stuck state and no leaked block entities.
4. **A world corpus for upgrades.** Real saves from 1.0, 1.1 and 1.2 (and each release after) are checked in and loaded
   in a dedicated-server GameTest. It backs up rule 8 with saves that were written by real builds, not synthesised
   ones.
5. **Dedicated-server and packaged-jar run, plus architecture rules.** Boot a dedicated server from the built jar, not
   the dev classpath, to catch client-only class loading and missing resources. ArchUnit rules: the sim imports nothing
   from `net.minecraft`, and common code never touches client classes.
6. **Concurrency tests.** Hammer the sound thread against the server tick and chunk unload with random timing, in the
   area of the 1.2.0 out-of-memory fix. Checks: no exception, no deadlock, bounded memory.
7. **Performance budgets.** The smoke harness's fleet numbers become limits that fail the build: milliseconds per tick
   per engine, allocated bytes per tick on the hot path, and the existing leak trends. Budgets are set from a measured
   baseline, and the optimization pass in 2.0 tightens them.
8. **Network and codec fuzz.** Every packet and block entity codec round-trips, then takes truncated and garbage
   payloads. A malformed packet is rejected and never crashes or stalls the server.
9. **Config matrix.** Every config value at its minimum, maximum and default runs through the smoke audit and the
   GameTests, alone and in a few combined extremes.
10. **Compatibility matrix.** The smoke client and server run against each combination below. The rows are the support
    promise: a mod is listed as supported only once its row is green.
    - **Create:** the oldest and the newest supported version (rule 7).
    - **Rendering:** Flywheel on and off, and **Sodium** (with Embeddium-compatible forks only if added to the list).
    - **Addons:** **Create Aeronautics** (the 1.4 work), plus the other Create addons the author picks, each in a
      world with engines running and the Ponder sweep. The list of addons is still to be chosen.
    - Checks per row: no log errors beyond the allow-list, models and Ponder pages render, engines run and power
      their networks, and a world saved without the mod opens with it and the other way round.
11. **Datagen idempotence.** Run `runData` in CI and fail if `git diff` on `src/generated` is not empty. Generated
    files can then never drift from the code that makes them or be committed half-updated.
12. **Coverage with a ratchet (JaCoCo).** Coverage is merged across the unit tests, GameTests and smoke audit. It is a
    map of code that no layer runs, to aim the other items at, not a goal in itself. The build fails only if the total
    drops below the last recorded number, which is raised by hand when it improves.
13. **Golden checks (visual and audio).** Stored reference images and measurements, compared with a tolerance, so a
    change nobody meant fails the build and a change somebody meant costs one command.
    - **Updating.** `./gradlew updateGoldens` rewrites the references. The git diff of the images and numbers is the
      review, and the commit is the approval. A failing run writes a side-by-side image or a numeric diff.
    - **Visual.** Fixed-camera shots of the casing and part models and a few Ponder frames, taken by the showcase
      harness. Compared per region with a tolerance, so anti-aliasing and driver noise pass and a change of shape or
      colour fails. Particles, animation frames and the surrounding world are left out.
    - **Audio, from the game as well as the synthesizer.** Two levels. The synth level renders scenarios directly
      (idle, load, boost, lean, fouled, limiter-free redline) and records loudness, peak, clipping and a coarse
      spectrum, never raw sample hashes, so a deliberate retune moves the numbers inside a band and a bug that clips or
      goes silent fails. The in-game level runs the smoke client with real engines and captures the PCM the sound
      engine is actually handed, which covers what the synth test cannot see: the streaming path, pitch and volume
      from live engine state, voice limits, and the click-free note. It is measured with the same figures.
    - **To settle when building it:** whether the capture point can be the final mix (an OpenAL loopback device in the
      smoke client) or has to be the stream hand-off before the mixer. The hand-off is the fallback and still catches
      the streaming and engine-state bugs. Determinism matters, so scenarios run on fixed engine states and a fixed
      tick count, and a flaky capture is fixed or dropped, never given a looser tolerance to make it pass.
    - Deliberate art changes update their goldens as part of the change.

## Not on this roadmap

**Declined, and not to be proposed again without a new reason:** knock as a mechanic, cooling blocks or radiators,
parallel throttles, snorkels or any way to run underwater, a power cost on the Muffler, parts that wear out or break
in the default game (**reopened 2026-11-23 as an opt-in mode only, placed in 2.3, see "Wear mode" in ROADMAP-3.0.md**),
new failure modes of any kind in the default game (runaway, blown gaskets, seizure, valve float; the same opt-in exception covers a thrown rod and worn gaskets), new fuels (kerosene and methanol were added in 2.0 at the author's say, and no more are wanted), mixins into Create's trains,
engine classes, record boards and dyno commissions, cylinders extended or stacked for more volume (one connecting rod
cannot serve two sizes of cylinder), heat glow on manifolds and turbos, exhaust tips other than the rain cap, intake
dress parts, a cosmetic accessory belt drive, valve covers (the head block is already the finished head, and its
faces are for manifolds).

**Engines on Create's own contraptions and trains.** Blocks on a Create contraption are not live block entities and
there is no kinetic network there, and a train takes no power input. Checked against the Create 6.0.10 jar on
2026-11-14: the only route to a train's speed is a mixin. A drive system of this mod's own for glued contraptions
would rebuild what Aeronautics already does, so it is only worth revisiting if the Aeronautics work in 1.4 fails.

**On hold:** variable valve timing (additive if it ever comes; needs a dyno sweep that shows it pays).

**Offered before and not taken:** Engine Panel, Load Bank, exhaust-fed fan blasting, gas turbine (the author, 2026-11-21: does not fit the mod's scope), two-stroke
cylinder (no benefit, same day), a see-through cylinder (the existing cylinders already show their insides). The 2.0 platform is what would
let a turbine or a two-stroke arrive as an addon instead of in the core.

**Not planned:** other loaders, other Minecraft versions.
