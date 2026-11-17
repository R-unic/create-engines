# Release listing (CurseForge / Modrinth)

Copy-paste material for the project pages. Not shipped in the jar.

## Basics

| Field | Value |
|---|---|
| Name | Create: Engine Engineer |
| Slug | create-engine-engineer (check it is free on both sites) |
| Icon | `docs/images/icon.png` (512x512). The jar carries a 256x256 copy as `logo.png` |
| Summary (CurseForge, 250 max) | Build real engines piece by piece: V8s, flat sixes, Wankels, twin-turbos. Tune them, hear them roar, and turn their power into Create rotation. |
| Short description (Modrinth, 256 max) | Same as the summary. |
| Categories | Technology, plus the Create addon tag where the site offers one |
| Game version | 1.21.1 |
| Loader | NeoForge |
| Dependencies | Create 6.0.10 (required) |
| Environment | Client and server (required on both) |
| License | MIT |
| Source | https://github.com/R-unic/create-engines |
| Issues | https://github.com/R-unic/create-engines/issues |
| Release type | Beta (0.x), switch to Release at 1.0 |
| AI disclosure | Yes. The "About this mod" section of the description below, plus the site's own AI-content field where it has one |

Image links in the description use `raw.githubusercontent.com`, so they only work once the repository (with
`docs/images`) is pushed to GitHub. CurseForge's editor wants rich text: paste the description into its source (HTML)
view after converting it, or retype the headings and lists. Modrinth takes the Markdown as it is.

## Description

```markdown
# Create: Engine Engineer

### Build a real engine, one part at a time, and hear it run.

![A V8 from the exhaust side, running](https://raw.githubusercontent.com/R-unic/create-engines/master/docs/images/engine_showcase_v8_exhaust.png)

An addon for **Create** with working internal combustion engines. Place the crankshaft, cylinders, heads, pistons, cams
and valves yourself, pipe in air and fuel, and the engine you built fires in order, sounds like what it is, and drives
your Create machines.

> 🔊 **It has a voice.** The exhaust note is made from the engine's speed and firing order, so a V-twin lopes, a six
> hums and a dead cylinder limps. Turbos whistle, blow off and crackle. Turn your sound up.

---

## ⚡ At a glance

| | |
|---|---|
| **Layouts** | Inline, V, flat and Wankel, with coupled crankshafts up to a V12 |
| **Fuels** | Diesel, plant oil, gasoline, ethanol and biodiesel, plus other mods' fuels |
| **Power** | Turbos, superchargers, intercoolers, flywheels and a 13-gear transmission |
| **Tools** | Bank Builder, Timing Tool, Dynamometer, Black Box recorder, Engineer's Goggles |
| **Learning** | A Ponder scene for every part, and advancement tracks |
| **Needs** | Minecraft 1.21.1, NeoForge, Create 6.0.10 (required on client and server) |

---

## 🛣️ From one cylinder to a V12

| Stage | What you build |
|---|---|
| **Start** | A single diesel cylinder and a flywheel. The early game is forgiving, and nothing ever overheats or wears out |
| **Grow** | An inline six or a V8, laid out in one click with the Bank Builder, fed by your own plant-oil and ethanol chain |
| **Tune** | Turbos, superchargers, precision parts, a transmission, and the Dynamometer to prove it worked |
| **Master** | Coupled-crank V12s, Wankel rotaries, a Refinery column, and engines that heat your boilers and basins |

---

## 🔧 Build it

- **Real layouts:** inline, V, flat and rotary (Wankel), with coupled crankshafts for bigger engines
- **Bank Builder:** lays out a whole inline, V or flat bank in one click (it fits the spark plugs you carry, and lays a
  cam line beside the bank if you carry a camshaft and cam lobes)
- **Timing Tool:** sets the firing order for you, and can tune every injector in one click
- **Cams beside the block:** a V can run one camshaft in its valley and keep every head face free for pipes
- **Torsional flex:** past eight throws on one unbroken crankshaft an engine's rating falls a little for each extra
  throw, to at most 35 percent. A V12 or flat twelve loses nothing, and nothing is ever harmed

## ⛽ Feed it

- **A fuel chain in Create basins:** seeds to plant oil to diesel, sugar crops to ethanol, and biodiesel with glycerin on
  the side. The loop closes: glycerin goes into engine oil and coolant, and used oil is burned, refined, or recycled
  into oil or charcoal
- **Refining column:** a Refinery of Reboilers, Column Sections and Condensers that makes fuel more efficiently than a
  basin
- **Air, fuel and exhaust plumbing:** pipes, throttle bodies and fuel metering, with spark ignition where the fuel
  needs it
- **Oil sump:** one block against the engine takes its oil, coolant and pilot fuel from any face
- **Oil that goes off:** a running engine can change its own oil, pushing out Used Oil. An Oil Filter slows the ageing
- **Dual-fuel pilot injection:** a compression engine with no spark plug lights gasoline or ethanol with a small pilot
  shot of diesel
- **Exhaust Heat Exchanger:** turns exhaust into basin heat, so an engine can cook its own fuel, and into boiler heat,
  so the exhaust can make steam for Create steam engines
- **Other mods' fuels work:** anything tagged `c:diesel`, `c:gasoline`, `c:ethanol` or `c:plantoil`, or given an entry
  in the fuel data map
- Starters, batteries and alternators, with Forge Energy output

## 🚀 Make it powerful

- **Forced induction:** stacked, twin and variable-geometry turbochargers (with anti-lag), plain and variable-drive
  superchargers, and intercoolers
- **Precision parts:** precision cylinders, heads, pistons and injectors
- **Flywheels that matter:** a rough engine is rated for less, and rotating mass smooths it out
- **A 13-gear transmission** with an Auto mode that shifts to suit the engine
- **Dynamometer:** draws an engine's torque and power curves, and compares a new run with the last
- **Governors and MAF sensors** for the late game
- **Black Box:** a flight recorder that keeps ten minutes of speed, load and warmth and says why an engine stalled
- **Engine temperature:** a cold engine drags until it warms through, and a cooled one breathes better. Water or coolant
  goes into the same sump, so there is no radiator to fit

## 👂 Hear it, see it

- Exhaust colour that reads the mixture, flame from a hot pipe, a cough on starting, and ticking as it cools
- A turbo whistle that climbs with the wheel, a blow-off flutter when redstone dumps the boost or the throttle lifts,
  anti-lag crackle, and a supercharger whine that rises with its shaft
- A clatter or squeal when an engine runs dry. Nothing runs underwater
- **Muffler:** quiets the note, the bangs and the pops without costing any power
- **Engine Casing:** joins into one plate and clads cylinders, pipes, cams, and Create's own shafts and cogwheels to
  hide the sprawl, in plain, riveted, louvred and window styles, and any dye
- **Engine readouts:** a Display Link set against a crankshaft shows state, speed, load, temperature and oil on a
  display board or nixie tubes, and a comparator reads any of an oil sump's tanks

## 🎓 Learn it

- **Engineer's Goggles** name an engine Healthy, Running rough or Failing, with the one cause to fix first
- **Ponder scenes** for every part
- **Advancements** that reward actually using what you build, grouped into tracks (builder, power, tuning, driveline,
  fuel, service) that each end in a capstone, with a Chief Engineer advancement for finishing them all
- **Hover tooltips** on fuel buckets say what they are: energy, whether they light by compression or need a spark plug,
  and how they take a cold start

---

## 📈 Recommended: raise Create's RPM limit

Create caps rotation at 256 RPM by default, and engines hit that ceiling. Raise it: in
`serverconfig/create-server.toml` under `[kinetics]`, set `maxRotationSpeed` to 512 (higher works too). The engine
simulation is built to stay cheap at speed, and its torque rating follows the limit you set.

## 📦 For modpack makers

The server config (`serverconfig/create_engines-server.toml`) has `[balance]` multipliers and `[features]` switches, so
a pack can rebalance without code. Turning a feature off never removes blocks; they stop doing that job. The client
config thins out engine sound and particles on a big engine. Fuel properties are a datapack data map
(`data_maps/fluid/fuel.json`), so other mods' fluids can be made into fuels, and refining routes are a data recipe
type, `create_engines:refining`.

## ✅ Requirements

- Minecraft 1.21.1, NeoForge 21.1.219 or newer
- Create 6.0.10 (all of its own requirements, Flywheel and Ponder, come with it)
- Required on both the client and the server

## About this mod

Nearly all of the code, models, textures, sounds, Ponder scenes and docs were written by an AI (Claude) at my
direction. I have played, tested and reworked almost all of it over many passes, and it ships with unit tests and
in-world GameTests. If something breaks, please report it on the
[issue tracker](https://github.com/R-unic/create-engines/issues). Block, item and fluid IDs, config keys and ranges,
advancement IDs and the fuel data-map format stay stable within 1.x, and a release that changes how an existing engine
behaves says so in its changelog.

## 🔗 Links

- [Discord](https://discord.gg/QYVpy2dqGE), for help, showcases and suggestions
- [Source and issue tracker](https://github.com/R-unic/create-engines)
- [Design notes](https://github.com/R-unic/create-engines/blob/master/docs/DESIGN.md), how the simulation works
```

## Changelog for 1.3.0

The Refinery: a column that turns feed into fuel more efficiently than a basin. Existing engines, ratings, IDs and
config keys are unchanged, and diesel and gasoline from a basin make exactly what they did. The one exception is
basin ethanol, which gets weaker (see below).
- Refining column: a square of Reboilers (1 to 3 wide) on a heat source, layers of Column Sections, and a layer of
  Condensers on top. Pipe feed into any Reboiler and take product from any Condenser. The heat under the base
  picks the route, and a wider or taller column runs faster. Plant oil to diesel and diesel to gasoline use about a
  tenth less feed than a basin and a quarter of the catalyst (coal, blaze powder); ethanol from sugar mash takes
  Nether Wart. A funnel or hopper can feed catalyst. Sight windows glow while it works and goggles name what is
  stopping it.
- Routes are a data recipe type, `create_engines:refining`, so packs can add their own.
- Leaning columns: a layer may sit one block to the side of the one below. Sections join like Create chutes, with no
  joint block.
- The sight windows glow the colour of the fluid in the column: the lower layers show the feed, the upper layers the
  product. A comparator on a Reboiler reads the feed level and on a Condenser the product level.
- Flare Stack: burns on a working Condenser, with a soft whoosh now and then.
- Soot Trap: catches the soot a rich engine would send out of the exhaust (50 mB of unburned fuel is one Soot, up to 64).
  Empty it by hand, hopper or funnel. Soot grinds to black dye. A caught lump makes a small clink.
- Jerry Can: four buckets of one fluid in an item, for fuel that has to go where there is no pipe. Its bar takes the
  colour of the fluid, and sneak-use on a block pours one bucket onto the ground as a source block.
- Basin ethanol is weaker: 35 mB per 250 (heated) and 70 mB per 250 (superheated), and biodiesel asks for 17 mB of
  ethanol instead of 25, so Glycerin and what is made from it cost what they did. A column is the better way to
  make ethanol.
- Goggles on a Create Basin say when a column would make more of a fuel from what it holds, and fuel buckets say
  when a column makes them.
- Ponder pages for the refining column, leaning columns, the Soot Trap and the Jerry Can.

## Changelog for 1.2.1

Stability fix for 1.2.0. No rating, recipe, ID, config key or simulation number changes.
- Fixed the frame rate collapsing near running engines and Minecraft's sound engine dying until the game was restarted:
  the synthesized engine note allocated native memory ten times a second per engine and could run the JVM out of it.
  It now reuses one buffer.
- A bad number from an engine can no longer silence its note for good, and a failing synthesizer gives silence
  instead of an error on the sound thread.
- Leaving a world and joining another no longer leaves stale engine voices that block new engines from sounding, and
  no longer keeps the old world's machines in memory.

## Changelog for 1.2.0

Dress Parts: two builds no longer have to look alike. Everything here is cosmetic: no rating, recipe of an existing
item, ID, config key or simulation number changes, and worlds from 1.1.x load as they are.
- Casing plates: Riveted Engine Casing (a rivet in each corner) and Louvred Engine Casing (slatted like a vent) join
  the plain one. Each connects to its own kind. Riveted and louvred are shapeless recipes: casing plus an iron nugget,
  or casing plus iron bars.
- Window Engine Casing: a glazed plate. Used on a crankshaft, crank throw, camshaft or cam lobe it clads the part and
  you still see it turning through the glass. Shapeless: a casing plus a glass pane.
- Dyed casing: use any dye on any casing block, or on casing clad onto a part (cylinders, cranks, cams, manifolds,
  cased shafts and cogwheels) to tint it in Create's sixteen colours. A bucket of water washes it back to bare steel.
  A dye is used up outside creative mode; a broken dyed block drops plain casing.
- Rain Cap: a hinged cap that goes on the mouth of an exhaust outlet. It kicks open with each exhaust pulse and
  drops shut when the engine stops. It never seals the mouth: the exhaust leaves from under it. Has a Ponder page.
- Maker's Plate: a brass plate screwed to the side of an engine. Name it on an anvil before placing it and the name is
  stamped on, with the placer as the builder. Use it with an empty hand to stamp the engine's peak torque and peak
  power, each with its speed, taken from the same finished curve the Dynamometer shows. Goggles read the plate.

## Changelog for 1.1.0

Light and Noise: a running engine is now worth standing next to, and the things that go wrong now show it. Nothing here
changes a rating or adds a failure; the simulation's numbers do not move. Worlds from 1.0.x load as they are.
- Pushrods: a cam beside the block, or in the valley of a V, shows a pushrod from each lobe up to a brass rocker on the
  head, rising and falling with the valve.
- Engines rock: pistons and rods slap about in their bores by the roughness rating, with a pinch of dust shaken off.
  A flywheel calms both. The block itself does not shake.
- Intake roar: a gulping sound at the open intake mouth that swells with throttle and boost. One voice per engine,
  silent at rest, untouched by the Muffler.
- Smoke rings: a slow single puffs rings from its stack at idle.
- Louder consequences: a rich engine at full load holds a steady stack flame; a fouled plug puffs black smoke on each
  miss; a too-lean charge spits flame back through the intake; a dry engine smokes from the crankcase; a blocked
  exhaust chuffs back out of the intake; gasoline lighting early under boost rattles and flashes orange; a flooded
  engine gulps, steams and clunks; an overloaded engine gives one last belch as it stalls.
- Advancement declutter: the tab shows 54 advancements instead of 110. The rest keep their IDs and criteria, so
  nothing that refers to them breaks, but no longer appear.

## Changelog for 1.0.1

Casing that looks like casing, a few engine-behaviour fixes and livelier fire and air. No IDs, config keys or
advancements change, and worlds from 1.0.0 load as they are.
- Engine Casing now uses connected textures: a run of casing, and everything clad in it, reads as one riveted plate.
- Casing clads more. Besides crankshafts, throws, camshafts and cam lobes, it now covers cylinders, plain intake and
  exhaust manifolds, and Create's own shafts, cogwheels and large cogwheels (use it on them as you would Create's
  casings; they drop the shaft or cogwheel when broken). Throttles and sensors stay uncovered.
- Cam lobes are drawn as separate intake, exhaust and injector lobes, each turning to its own valve timing, so you can
  watch a valve open as its lobe comes round.
- Wrenching a casing off: sneak with a wrench to remove the casing and keep the part inside, instead of breaking it.
- The turbo wastegate chuffs when it swings open to hold the boost limit.
- Fixed: a stopped engine kept firing its effects on the client, and a crank zeroed by a rotation conflict now asks to
  turn again instead of staying still.
- Fixed: the slowest dyno point set the roughness loss peak, so a smooth even-firing engine could read about 18% rough at
  speed. The rating itself is unchanged, but smooth engines now read as smooth.
- Effects: fire in rotor housings and air drawn into intakes are drawn with new glowing particles, and intakes pull in a
  curling vortex of pale mist.
- Docs: the Modrinth summary and description are now pushed from this file on a release tag.

## Changelog for 1.0.0

The first stable release. Nothing in the engine's behaviour changes from 0.4.0; what changes is the promise.
- Stable from here through 1.x: block, item and fluid IDs, config keys and ranges, advancement IDs and the fuel data-map
  format. A release that changes how an existing engine behaves will say so here.
- The Transmission is back. It was removed in 0.4.0, and a player asked for it: it is compact, and its Auto mode shifts
  for you, which a Rotation Speed Controller does not. It is the same block with the same ID, recipe and advancements
  (Gear Up, Reverse Engineering, Gearhead, Let It Shift), and Full Driveline asks for every gear again. One placed before
  0.4.0 and lost on load does not come back by itself; place a new one.
- The Rev Limiter and Lambda Sensor existed only in development builds before 0.1.0. A world from one of those builds
  loses them on load too.
- Docs: the README lists every server config key group, including `alternatorEnergy`.

## Changelog for 0.4.0

Create's Rotation Speed Controller holds a shaft at a set speed and trades speed for torque at any ratio, and it keeps
what an engine drives steady however the engine's own speed moves. The Transmission did the same job worse, so it is
gone.
- Removed: the Transmission and its recipe, Ponder page and advancements (Gear Up, Reverse Engineering, Gearhead and Let
  It Shift). Any in an existing world disappear when it loads. Put a Rotation Speed Controller after the crankshaft
  instead. The Governor stays: it holds the engine's own speed, which a Rotation Speed Controller downstream does not.
- Full Driveline no longer asks for every gear: it wants a charged battery, a flywheel that smooths the engine and a
  governor under redstone.
- New: an exhaust heat exchanger under a fluid tank heats a boiler like a blaze burner (kindled is one level, seething
  two), so exhaust can run Create steam engines. Advancement: Combined Cycle.
- New: Display Link sources for engines (Engine Status, Speed, Load, Temperature, Oil Condition) on any crankshaft
  block, and Oil Sump Levels on a sump. A sump also gives a comparator signal for the tank its new dial names, so a pump
  or alarm can watch the oil. Advancements: Instrument Panel, Dipstick.
- New: the Muffler quiets an engine's exhaust note, bangs and pops and costs it no power. Engine Casing is plain cladding
  for hiding the sprawl. Advancements: Hush Now, Under the Hood.
- New: fuel buckets show their energy, how they ignite and how they take a cold start when hovered.
- The Bank Builder fits spark plugs and lays a cam line beside the bank (in the valley of a vee) when you carry them.
  Advancement: Cam Included. A cogwheel placed or broken between cranks now couples or uncouples them at once.
- Fixed: a schematic cannon built a finished cylinder, head or rotor housing with its pistons, rods, injectors, plugs
  and rotor for free. It now asks for them.
- A burning engine glows faintly: heads and rotor housings give off a little light while the charge fires.
- Engines at the edge of loaded chunks no longer pull in chunks nobody is near, and an engine whose chunk has unloaded no longer pulls it back in.
- Raise Create's maxRotationSpeed to 512 (higher works too) to let engines run past 256 RPM.
- Fuel recipes: plant oil takes six seeds or two saplings (100 mB and 50 mB), ethanol ferments from an apple or any
  mushroom and makes 50 mB a batch, biodiesel needs 25 mB of ethanol, and gasoline needs only a Blaze Burner's heat.

## Changelog for 0.3.0

Livelier engines. Exhaust tells the truth: a thin haze when the mixture is right, black when rich, white vapour until the
block warms, a blue cast when fuel has thinned the oil, and a crack of flame when unburned fuel meets a hot pipe. Engines
cough as they catch, knock as they die, tick as they cool, squeal and spark when they run dry, and draw air in at their
intakes. New recordings and white puff and spark particles back all of it.
- Warm-up and cooling: a cold block burns fuel a little worse and warms quickest at idle. A Blaze Burner against the heat
  exchanger preheats it. A stopped engine ticks and shimmers, and its turbine wheel glows red as it cools.
- Plain-language diagnosis: goggles name an engine Healthy, Running rough or Failing with the one cause to fix first, and
  say why a cylinder misfired. The Dynamometer shows how a new run compares with the last. Two new Ponder pages cover why
  an engine will not start and why it runs badly.
- Nothing burns underwater. Water against a cylinder, head or rotor housing stops the engine wherever its pipes lead, and a
  flooded intake or exhaust shuts as well. Engine parts are no longer washed away by flowing water.
- New advancements: Hard Start, Preheated and Flooded.
- New client settings: `engineNoteVolume` and `mechanicalVolume`.

## Changelog for 0.2.0

New art. The mod's metals are repainted to match Create's, with the same rims and strokes in Create's own colours. Vanilla's
copper, magma, lava, smooth stone and trapdoor are gone from the models, the fluids have their own thin and thick textures in
place of tinted water, and hot metal on a heat exchanger gives its own light. The advancement tab has a tread plate background.

## Changelog for 0.1.0

First public release. Everything in the description above.

## Publishing

Uploads are automatic, from `.github/workflows/publish.yml`. It builds with the tests, then uploads the jar to
Modrinth and CurseForge. The changelog is the `## Changelog for X.Y.Z` section of this file when one matches the tag,
otherwise the commit subjects since the last tag.

| Do this | You get |
|---|---|
| `git tag v0.2.0 && git push origin v0.2.0` | a release |
| `git tag v0.2.0-beta.1 && git push origin v0.2.0-beta.1` | a beta (`-alpha` makes an alpha, `-rc` a beta) |
| push to the `prototype` branch, or run the workflow by hand | an alpha named `<mod_version>-prototype.<run number>` |

On a release tag, `.github/workflows/gallery.yml` also pushes the summary and the description above to the Modrinth page
(`scripts/sync-modrinth-description.sh`) and replaces the gallery. This needs the token's "Write projects" scope. CurseForge
has no API for either, so its description and gallery are still pasted in by hand.

The version in the jar comes from the tag, so `mod_version` in `gradle.properties` only needs bumping for the
prototype names.

One-time setup in the GitHub repository (Settings, Secrets and variables, Actions):

- Secret `MODRINTH_TOKEN`: a Modrinth personal access token with the "Create versions" scope
  (modrinth.com/settings/pats).
- Secret `CURSEFORGE_TOKEN`: a CurseForge API token (authors.curseforge.com/account/api-tokens).
- Variable `CURSEFORGE_ID`: the numeric project ID, on the CurseForge project page under About Project.
- Variable `MODRINTH_ID` is optional; it defaults to the slug `create-engine-engineer`.

Push the repository first so the description's image links resolve. Both sites review a project before it goes
public, and Modrinth may not accept uploads until its first review passes. While a Modrinth project is unapproved, the slug
cannot be looked up without the token's "Read projects" scope: add that scope, or set `MODRINTH_ID` to the project's ID.

A failed upload does not stop the other site, but the run still fails. Rerunning it uploads again to any site that
succeeded, so when one site worked, upload to the other by hand with the jar from the run's artifacts.
