# Roadmap: 1.0.1 to 2.0.0

Written 2026-11-14, at 1.0.1. This is a plan, not a promise: the order is firm, the contents of each release are
the current best guess, and nothing here has a date.

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

| Version | Name | What it is | Size |
|---|---|---|---|
| 1.0.x | Shakedown | Close the open boxes from the 1.0 checklist | Small |
| 1.1 | Light and Noise | Spectacle, louder consequences, advancement declutter | Medium |
| 1.2 | Dress Parts | Cosmetic build variety | Small |
| 1.3 | Refinery | A refinery multiblock for the fuels that already exist | Large |
| 1.4 | Drawing Office | Blueprints, pack-maker tools, Aeronautics and other addons | Large |
| 2.0 | Open Block | The platform, and the one breaking cleanup | Large |

## 1.0.x Shakedown

No new features: the boxes still open in [RELEASE_1_0.md](RELEASE_1_0.md), and two more looks at what exists.

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
|---|---|---|
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
  |---|---|---|---|
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
- JEI and EMI pages for fuels (energy, ignition, cold start, boost tolerance) and for which part fits where.
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

- **Parts as data.** Cylinder, head, piston, injector and induction stats come from data. Crude and precision are
  two entries, and a pack or addon adds a third with a block, a model and a data file.
- **Engine types as plug-ins.** Today the simulation takes a list of cylinders, and a rotary chamber is the same
  cylinder model with other numbers. Turn that into a seam: an addon registers something that puts torque on the
  crank, a scanner rule for its blocks, and its goggle lines. An engine that is not a cylinder at all, such as a
  turbine, needs this seam built first; it does not exist yet.
- **Fuels stay data.** Fuel data maps and column recipes written for 1.x keep loading. Any field 2.0 adds is
  optional.
- **A frozen public API** under its own semver: read-only engine views, events, registries.

### The one breaking cleanup

2.0 is the only release allowed to break the 1.x promise, so everything that needs breaking goes here, with
registry aliases so existing worlds load. Each item needs the author's decision:

- The throttle body: keep, fold into the intake, or remove.
- ~~The Transmission~~: kept (2026-11-16), as the car-like gearbox beside Create's Rotation Speed Controller.
- The basin fuel recipes: kept through 1.x as the worse route, and reviewed here.
- Config keys regrouped once, after two rounds of growth. On first start 2.0 reads the old config file, carries
  each value over to its new key and logs what moved, so nobody's settings reset.
- **Advancement redesign, step 2 (2.0 owns this).** Step 1 shipped in 1.x (see docs/ADVANCEMENTS.md). 2.0 does the rest:
  rename the IDs and regroup the tab tree by track, delete the hidden advancements, and write the migration table (every 1.x ID,
  shown or hidden, maps to its new ID or IDs; old progress is credited silently, with no toast and no sting; a test checks
  every 1.x ID has an entry). Nothing of step 2 is started, and no 1.x release should rename or delete an advancement.
- Legacy manifold handling and other old-save code paths.
- Support for older Create versions carried through 1.x under rule 7 is dropped, if a newer one is out.

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
