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
| 2.0 | Open Block | The platform, and the one breaking cleanup | Large | **In progress** on branch `2.0`: the breaking cleanup and the two crossovers are built (see their sections); the platform items are not |

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

**Engines that move: first piece built 2026-11-18, and blocked from running.** `compat.sable.SableCompat` (Sable's own small companion library, compiled against, one bridge class) projects a block out of its moving structure's plot, and `EngineController` now uses the block's height in the world for the thin-air check and re-reads it every 20 ticks, so a climbing aircraft thins as it rises. Without Sable nothing changes. `SableGameTests` (assemble an engine into a structure at y 2000, check the reported height and density) and the `runGameTestServerSable` run exist, but **that run cannot start**: Aeronautics 1.2.1, the newest the build's NeoForge 21.1.219 accepts, is built for Create 6.0.10, and its Simulated mixin into `NixieTubeBlock.getPower` fails to apply on Create 6.0.11 (this build's floor since today); 1.3.2 asks for NeoForge 21.1.228 or newer. Running it needs a NeoForge bump to 21.1.228+, or the Aeronautics row stays red. Not done: weights, sound and particle positions, scanner and water check on a structure, the Ponder page. None of it has been seen running.

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
  boost, goggle lines and rebuilds; see `docs/design/ENGINE_SEAM.md`). Not built: narrowing the 19 files that name `CylinderModel`
  (step 3), a registry for scanner rules.
- **Frozen public API:** `api.ApiVersion` 1.0.0 (its own semver), `ApiSurfaceTest` against `compat/api-surface.txt`, provider internals
  moved to `engine.EngineProviders`, `docs/API.md`. Behaviour is covered by the GameTests, not the surface file.
- **Parts as data, first piece:** the four precision parts (bore, head, piston, injector) are `part_effects/<part>.json`, read on every
  data reload into `engine.sim.PartEffects`; the shipped files equal the old constants (pinned by test) and the baselines did not move.
  Not yet data: the rotary chamber's stats, cam profiles, and a third tier named from a pack's own block (a pack's block would also
  need its own cylinder block class). The `part_stats` data map still works on top.
- **State cleanup (dye): not started, and it needs a decision.** The dye (`paint`, 17 values) multiplies a pipe's states: a pipe has
  64 connections x 2 encased x 4 styles x 17 dyes = 8,704 states (two pipe kinds), a cylinder 6,528, and the crank, cam, cased shaft and
  cogwheel carry it too, so folding it into a block entity or data component cuts those 17-fold (the roadmap's 440 MB of block-state
  bookkeeping is mostly this). What it costs: a block entity on pipes (they have none), tint read from it in the block colour handler
  (Flywheel and Sodium paths to check by eye), and the saved dye. A saved block state's unknown property is dropped on load, so an
  existing world's paint is lost unless a chunk-NBT hook reads the old palette before that; that hook needs a 1.x fixture with painted
  blocks to test. **Decision for the author: accept losing existing paint on the upgrade (one changelog line, simplest), or build the
  migration.**

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
  `EngineController.emitPollution`, `runGameTestServerPollution`; the release rates are untuned and the engine-level hook has no GameTest, only the outlet release does). Engines
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
  - *Still to decide.* Whether a fuel gets its own optional `sulfur` field in the fuel data map, instead of
    borrowing the soot factor. The release rates are untuned, because the mod's units were not worked out; they are
    set with the second GameTest run, against how fast gas builds up around a running engine. Whether the Soot Trap's
    caught soot should count for anything on the mod's side.
  - *Tests.* Unit tests for the emission numbers, and a GameTest that runs only with the mod and checks gas appears
    at a running engine's outlet and not at a stopped one or a shut outlet.

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
  every 1.x ID has an entry). Nothing of step 2 is started, and no 1.x release should rename or delete an advancement.
- Legacy manifold handling and other old-save code paths. **Looked at 2026-11-18 and left:** no manifold-specific legacy code was
  found; the one old-save path is `Gears.fromLegacy`, which turns a 1.0 transmission's four-gear positions into the thirteen now, and
  2.0 still has to open a 1.0 world, so it stays. Removing it is a decision for the author, not a cleanup.
- **Done 2026-11-18.** Support for older Create versions carried through 1.x under rule 7 is dropped, if a newer one is out: the
  build is on Create 6.0.11-346 (reverted the same day: Aeronautics needs 6.0.10, range back to `[6.0.10,6.1)`). Unit tests and the 317 GameTests passed on it.
- The throttle body is **kept** (author, 2026-11-18). The basin fuel recipes are left as they are; reviewing them is still open.

### Intake and exhaust port model redesign

Queued 2026-11-16. Redraw the models of the intake and exhaust ports (the mouths on the head, and the muffler) so they read
better and match the finished look of the rest of the engine.

- **Models and textures only.** Same block IDs, same block states, same behaviour, same ports and connections, so
  existing worlds load unchanged and nothing in the simulation moves. If a redesign needs a new state or a different
  collision shape, it is raised as a decision first.
- **Art direction holds:** chunky, reads as Create, textures built from Create's own pieces (see the art direction
  rule). No 3D pixel art.
- **This is a redraw, not new parts.** It does not reopen the declined list: no intake dress parts and no exhaust tips
  other than the rain cap. The intake roar and the exhaust note keep coming from the same positions, so the redesign
  must not move the mouths.
- **In scope: the head ports and the muffler.** Manifolds are not part of this redesign and keep their current models.
- **Still to decide before work starts:** whether the casing connected-texture clad still wraps the redrawn parts.
- Checked with the showcase screenshot harness, the Ponder sweep and the smoke audit's client checks.

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

### Optimization pass

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

- The last 1.x release logs a warning for everything 2.0 removes or renames.
- Before 2.0 ships, a world saved on 1.0 and one saved on the last 1.x are both opened in it.
- The preview formats from 1.4 (the `api` package, KubeJS bindings, part stats) are frozen here.

## Verification track (no release of its own)

Queued 2026-11-16. The goal is to trust a release with as little manual testing as possible. Each item lands as its own
piece of work, in whichever release has room, and none changes behaviour. What stays manual is taste: how it sounds,
looks and feels. Tests only guard against those changing by accident.

**Done 2026-11-16 (unit-test level, in `./gradlew check`, CI in `.github/workflows/verify.yml`):**

- Item 2, in part: `SimPropertyTest` (replay on another thread, no fuel means no power, fuel conserved, identical twins).
  `SimStressTest` already held the finite and bounded invariants. Not done: step-size independence, which needs the
  step size to be a parameter of the sim.
- Item 5, the architecture rules, as `ArchitectureRulesTest` (reads imports, no ArchUnit): the sim and the smoke core
  import nothing from the game, and the 14 common classes that touch client code are a fixed list that may only shrink.
  Not done: the dedicated-server and packaged-jar boot.
- Item 7, the sim's share: `PerfBudgetTest`, baseline 0.075 ms per tick for 16 turbo cylinders, 0.019 ms for 4, zero
  bytes allocated; budgets are about 7x that. Not done: the fleet numbers from the smoke harness as build limits.
- Item 9, the sim's share: `ConfigMatrixTest` runs every `Tuning` setting at its config minimum and maximum, alone and
  in six combined patterns, and fails if the ranges in `CEConfig` or the set of settings drift from the matrix. Not
  done: the matrix through the GameTests and the smoke audit.
- Item 11: the `datagen-idempotence` job in `verify.yml`. Not yet run on GitHub, and `runData` has not been run in CI.
- Item 12, in part: JaCoCo on the unit tests, floor in `src/smoke/coverage-floor.txt` (15%, measured 15.4%). Merging the
  GameTest and smoke audit coverage is not done.
- Item 1: `./gradlew pitest` (about 6 minutes, not part of `check`) runs PIT through its command line, because the Gradle
  plugin does not work with Gradle 9. First report: 1854 mutants in `engine/sim`, 1521 killed, 294 survived, 23 reached
  by no test. The ratchet `src/smoke/mutation-ratchet.txt` was 317 and may only go down. It is now 177 (1,721 of 1,898 killed, 91%), after the `*ExactTest` classes, `SimTrajectoryTest` and the refactor below. Survivors were clustered in
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
- Item 3: `MonkeyGameTests`, three seeds (plus `CE_MONKEY_SEED`), about 1,185 random actions each around two running
  engines (place, break, right-click with random items including the wrench, sneak, redstone, neighbour updates, random
  ticks, save and load round trips), then clear the area, assert no block entity is left and that a fresh engine still
  runs. A planted exception in the crankshaft's click handler failed all three seeds. Not covered: chunk unload (the
  eight chunk GameTests do that) and real players' packets.
- Item 8: `CodecFuzzGameTests`: the sting packet, both particle codecs, the jerry can fluid and the refining recipe
  round-trip to the same bytes, then take every truncation, 400 mutations and 400 garbage inputs each, and their JSON
  forms take 1,500 malformed documents each. A planted write/read mismatch produced 80 failures. Only this mod's own
  codecs are covered; Create's and NeoForge's are theirs.
- Item 4: nine fixtures from the real releases (diesel, spark and rotary engines from 1.0.0, 1.1.0 and 1.2.0) in
  `src/test/resources/compat/world`, recorded by `scripts/record-corpus.ps1` from each tag's own source, and loaded by
  `LegacyWorldGameTests`. Each must load, keep every saved key and keep running. They are block and block-entity saves,
  not whole world folders, so region files, chunk and player data are not exercised. v1.0.0 fails one of its own
  GameTests (a transmission ticking loads an unloaded chunk), which the current tree does not.
- Item 5, the jar half: `PackagedJarTest` reads the built jar's bytecode for client references from common classes and
  checks the mixin config, filled-in metadata and resources. It found nothing beyond Registrate's renderer lambdas in
  `CEBlockEntities`, which are listed with the reason. A dedicated server booted from the jar is not done (the build
  makes no NeoForge server install); the GameTest server is the server-only run.
- Item 7: the GameTest fleet budget is 2 ms for 16 engines (measured 0.4 ms), five times the baseline.
- Item 9: the GameTest matrix also runs four combined patterns (all minimum, all maximum, two seeded mixes).
- Item 10, in part: `./gradlew compatMatrix` runs the client audit with Flywheel off and with Sodium 0.8.13; both
  green (see `docs/COMPAT.md`). Open: Create Aeronautics and other addons, which need the author's list and the 1.4
  work; there is one supported Create version today.

**Not started:** 13 (goldens, set aside by request). Still open inside done items: step-size independence (item 2),
fleet leak and allocation limits as build failures (item 7), merged GameTest and audit coverage (item 12), a server
booted from the jar (item 5), and killing PIT survivors (item 1).

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
    - The planned port-model redraw and other deliberate art changes update their goldens as part of the change.

## Not on this roadmap

**Declined, and not to be proposed again without a new reason:** knock as a mechanic, cooling blocks or radiators,
parallel throttles, snorkels or any way to run underwater, a power cost on the Muffler, parts that wear out or break,
new failure modes of any kind (runaway, blown gaskets, seizure, valve float), new fuels, mixins into Create's trains,
engine classes, record boards and dyno commissions, cylinders extended or stacked for more volume (one connecting rod
cannot serve two sizes of cylinder), heat glow on manifolds and turbos, exhaust tips other than the rain cap, intake
dress parts, a cosmetic accessory belt drive, valve covers (the head block is already the finished head, and its
faces are for manifolds).

**Engines on Create's own contraptions and trains.** Blocks on a Create contraption are not live block entities and
there is no kinetic network there, and a train takes no power input. Checked against the Create 6.0.10 jar on
2026-11-14: the only route to a train's speed is a mixin. A drive system of this mod's own for glued contraptions
would rebuild what Aeronautics already does, so it is only worth revisiting if the Aeronautics work in 1.4 fails.

**On hold:** variable valve timing (additive if it ever comes; needs a dyno sweep that shows it pays).

**Offered before and not taken:** Engine Panel, Load Bank, exhaust-fed fan blasting, gas turbine, two-stroke
cylinder, a see-through cylinder (the existing cylinders already show their insides). The 2.0 platform is what would
let a turbine or a two-stroke arrive as an addon instead of in the core.

**Not planned:** other loaders, other Minecraft versions.
