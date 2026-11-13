# Release listing (CurseForge / Modrinth)

Copy-paste material for the project pages. Not shipped in the jar.

## Basics

| Field | Value |
|---|---|
| Name | Create: Engine Engineer |
| Slug | create-engine-engineer (check it is free on both sites) |
| Icon | `docs/images/icon.png` (512x512). The jar carries a 256x256 copy as `logo.png` |
| Summary (CurseForge, 250 max) | Build working combustion engines from individual parts, from a crude single cylinder to a boosted precision V12, and turn them into Create rotation. |
| Short description (Modrinth, 256 max) | Same as the summary. |
| Categories | Technology, plus the Create addon tag where the site offers one |
| Game version | 1.21.1 |
| Loader | NeoForge |
| Dependencies | Create 6.0.10 (required) |
| Environment | Client and server (required on both) |
| License | All Rights Reserved |
| Source | https://github.com/R-unic/create-engines |
| Issues | https://github.com/R-unic/create-engines/issues |
| Release type | Beta (0.x), switch to Release at 1.0 |
| AI disclosure | Yes. The notice is the first paragraph of the description below |

Image links in the description use `raw.githubusercontent.com`, so they only work once the repository (with
`docs/images`) is pushed to GitHub. CurseForge's editor wants rich text: paste the description into its source (HTML)
view after converting it, or retype the headings and lists. Modrinth takes the Markdown as it is.

## Description

```markdown
> **Built with AI.** Nearly all of the code, models, textures, sounds, Ponder scenes and docs were written by an AI
> (Claude) at my direction. I have played, tested and reworked almost all of it over many passes, and it ships with
> unit tests and in-world GameTests. It is still a young mod, so you may find rough edges or odd balance. Please report
> them on the [issue tracker](https://github.com/R-unic/create-engines/issues), and back up a world before adding the
> mod to it. Until 1.0, a new version may change how an existing engine behaves.

# Create: Engine Engineer

An addon for **Create** that lets you build working internal combustion engines out of individual parts and turn them
into Create rotational force. Start with a crude single cylinder, finish with a boosted, precision-built V12.

Nothing here is a single "engine block". You place the crankshaft, the throws, the cylinders, heads, pistons, rods,
camshafts and valves, and the layout you build decides whether the engine runs and how well. Cylinders fire in order
and the crank animates with them.

![A coupled two-crankshaft engine](https://raw.githubusercontent.com/R-unic/create-engines/master/docs/images/engine_coupled_overview.png)

## Build an engine

- Inline, V, flat and rotary (Wankel) layouts, with coupled crankshafts for bigger engines
- Cam lobes that sit beside the cylinder block, so a V can run one camshaft in its valley and keep every head face free
  for pipes
- A **Bank Builder** that lays out a whole inline, V or flat bank in one click, and a **Timing Tool** that sets the
  firing order for you
- **Torsional flex**: a crank twists under load, so past eight throws on one unbroken crankshaft an engine's rating
  falls a little for each extra throw, to at most 35 percent. A V12 or flat twelve loses nothing. It only lowers the
  rating and never harms anything

## Feed and tune it

- Air, fuel and exhaust plumbing with pipes, throttle bodies and fuel metering. Burns diesel, plant oil, gasoline and
  ethanol, with spark ignition where the fuel needs it
- A craftable fuel chain, all in Create basins: seeds to plant oil to diesel, sugar crops to ethanol, and biodiesel with
  glycerin on the side. The loop closes: glycerin goes into engine oil and coolant, and used oil is burned as fuel or
  recycled into oil or charcoal
- An **Exhaust Heat Exchanger** that turns engine exhaust into basin heat, so an engine can cook its own fuel
- Oil film and lubrication, starters, batteries and alternators, with Forge Energy output
- An **oil sump that is the engine's service port**: one block against the engine takes its oil, coolant and pilot fuel
  from any face, so the cylinder heads keep their faces for air and fuel
- **Oil that goes off**: set the sump's Oil Exchange dial and a running engine changes its own oil, pushing out Used
  Oil that you burn, refine, coke into charcoal or let go. An Oil Filter slows the ageing
- **Engine temperature**: a cold engine drags until it warms through, and a cooled one breathes better. Coolant, or
  just water, goes into the same sump, so there is no radiator to fit. Nothing ever overheats or wears out
- **Warm-up and cooling**: a cold block burns fuel a little worse, and a Blaze Burner against the heat exchanger
  preheats it. A stopped engine ticks and its turbine wheel glows red as it cools
- **Plain-language diagnosis**: goggles name an engine Healthy, Running rough or Failing with the one cause to fix first,
  and the Dynamometer compares a new run with the last
- **Dual-fuel pilot injection**: pipe diesel into the sump's pilot tank and a compression engine with no spark plug
  lights gasoline or ethanol with a small pilot shot
- A forgiving early game: spark plug soot clears itself, goggles suggest fuel settings, and the Timing Tool can tune
  every injector in one click
- MAF sensors for the late game
- Other mods' fuels work too: anything tagged `c:diesel`, `c:gasoline`, `c:ethanol` or `c:plantoil`, or given an entry
  in the fuel data map

![Engine with management sensors](https://raw.githubusercontent.com/R-unic/create-engines/master/docs/images/engine_management_overview.png)

## Make it powerful

- Turbochargers (stacked, twin, variable-geometry, with anti-lag), superchargers and intercoolers, with the sound to
  match: a turbo whistle that climbs with the wheel, a blow-off flutter when redstone dumps the boost, anti-lag
  crackle, and a supercharger whine that rises with its shaft
- Precision cylinders, heads, pistons and injectors
- Flywheels that matter: a rough engine is rated for less, and rotating mass smooths it out
- A **Dynamometer** that draws an engine's torque and power curves
- A **Black Box** flight recorder that keeps ten minutes of an engine's speed, load and warmth and says why it stalled
- Engines that look and sound alive: exhaust colour that reads the mixture, flame from a hot pipe, a cough on starting,
  ticking as they cool, and a clatter or squeal when they run dry. Nothing runs underwater
- An exhaust note made up from the engine's speed and firing order, so a V-twin lopes, a six hums and a dead cylinder
  limps

![Flywheel, dynamometer and turbos](https://raw.githubusercontent.com/R-unic/create-engines/master/docs/images/engine_new_parts.png)

## Learn it

Every part has Engineer's Goggles diagnostics and a Ponder scene. Advancements reward actually using what you build,
grouped into tracks (builder, power, tuning, driveline, fuel, service) that each end in a capstone, with a Chief
Engineer advancement for finishing them all.

![Goggle readout on a firing cylinder](https://raw.githubusercontent.com/R-unic/create-engines/master/docs/images/engine_goggles_cylinder.png)
![Firing order Ponder scene](https://raw.githubusercontent.com/R-unic/create-engines/master/docs/images/engine_ponder_firing_order_960.png)

## Recommended: raise Create's RPM limit

Create caps rotation at 256 RPM by default, and engines hit that ceiling. Raise it: in
`serverconfig/create-server.toml` under `[kinetics]`, set `maxRotationSpeed` to 1024 or higher. The engine simulation is
built to stay cheap at speed, and its torque rating follows the limit you set.

## For modpack makers

The server config (`serverconfig/create_engines-server.toml`) has `[balance]` multipliers and `[features]` switches, so
a pack can rebalance without code. Turning a feature off never removes blocks; they stop doing that job. The client
config thins out engine sound and particles on a big engine. Fuel properties are a datapack data map
(`data_maps/fluid/fuel.json`), so other mods' fluids can be made into fuels.

## Requirements

- Minecraft 1.21.1, NeoForge 21.1.219 or newer
- Create 6.0.10 (all of its own requirements, Flywheel and Ponder, come with it)
- Required on both the client and the server

## Links

- [Source and issue tracker](https://github.com/R-unic/create-engines)
- [Design notes](https://github.com/R-unic/create-engines/blob/master/docs/DESIGN.md), how the simulation works
```

## Changelog for 0.4.0

Create's Rotation Speed Controller holds a shaft at a set speed and trades speed for torque at any ratio, and it keeps
what an engine drives steady however the engine's own speed moves. The Governor and the Transmission did the same job
worse, so both are gone.
- Removed: the Governor, the Transmission and their recipes, Ponder pages and advancements (Steady as She Goes, Speed
  Dial, Cruise Control, Gear Up, Reverse Engineering, Gearhead and Let It Shift). Any in an existing world disappear when
  it loads. Put a Rotation Speed Controller after the crankshaft instead.
- Full Driveline now asks for a charged battery and a flywheel that smooths the engine, and Fine Tuner no longer asks for
  a governed engine.
- A burning engine glows faintly: heads and rotor housings give off a little light while the charge fires.
- Engines at the edge of loaded chunks no longer pull in chunks nobody is near.
- Fuel recipes: plant oil takes four seeds or two saplings (100 mB and 50 mB), ethanol ferments from an apple or any
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
