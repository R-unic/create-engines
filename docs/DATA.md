# Data reference

Every data file a data pack, a mod's resources or a script can supply to Create: Engine Engineer: what it is called, where it
goes, every key, its range and what the key does. Settings in the TOML config files are in [CONFIG.md](CONFIG.md); how to do
common jobs with these files is in [PACKS.md](PACKS.md).

Names, keys, defaults and ranges of the `fuel`, `part_stats` and `part_effect` data maps and the `part_effects` and `cam_profiles` files are **frozen at 2.0**
(see [API.md](API.md) for the API's version): a key is never removed or renamed in 2.x, only added. A test
(`ScriptAndDataSurfaceTest`) fails the build if one changes, and `GuideDocsTest` fails it if this page stops naming one.

| File | Goes in | Purpose |
|---|---|---|
| [`fuel`](#fuels-the-fuel-data-map) data map | `data/create_engines/data_maps/fluid/fuel.json` | which fluids an engine burns, and how |
| [fuel tags](#fuel-tags) | `data/c/tags/fluid/<name>.json` | the shortcut: a fluid in a fuel tag is a fuel with that fuel's numbers |
| [`part_effects`](#part-effects-the-four-precision-parts) | `data/create_engines/part_effects/<part>.json` | what each precision part does to a cylinder |
| [`part_effect`](#a-part-of-your-own-the-part-effect-data-map) data map | `data/create_engines/data_maps/block/part_effect.json` | names the effect file an addon's own cylinder or head block adds |
| [`flywheel`](#a-flywheel-of-your-own-the-flywheel-data-map) data map | `data/create_engines/data_maps/block/flywheel.json` | gives an addon's own flywheel block its masses (added in 2.1, API 1.2) |
| [`cam_profiles`](#cam-profiles-when-the-valves-open) | `data/create_engines/cam_profiles/<kind>.json` | when the valves of a piston cylinder, and the ports of a rotor, open and shut |
| [`part_stats`](#part-stats-retuning-a-whole-kind-of-cylinder) data map | `data/create_engines/data_maps/block/part_stats.json` | scales over a whole kind of cylinder or rotor chamber |
| [refining recipes](#refining-recipes) | `data/<namespace>/recipe/refining/<name>.json` | a route a refining column can run |
| [other tags](#other-tags-and-recipes) | `data/create_engines/tags/...` | what lubricates badly, what ferments |

## How data maps merge

`fuel` and `part_stats` are NeoForge **data maps**, so any number of data packs can contribute to the same file path and the
entries are merged by key. The file has a top-level object with these fields (NeoForge's own format, not this mod's):

| Field | Meaning |
|---|---|
| `values` | an object from a registry id (`"create_engines:diesel"`) **or a tag** (`"#c:gasoline"`) to that entry |
| `replace` | optional, default `false`. `true` throws away every entry earlier packs gave this file |
| `remove` | optional list of ids (or `#tags`) whose entries are deleted |

An entry may also be written `{ "value": { ... }, "replace": true }` to replace just that one key's earlier entry. NeoForge's
`neoforge:conditions` work on an entry, so a pack can say "only if this mod is loaded".

Which pack wins when two give the same key is decided by NeoForge (pack order), exactly as for other data maps. `part_effects` is
not a data map: one file per part, and the file from the highest pack replaces the rest whole.

## Fuels: the `fuel` data map

A fluid is an engine fuel if it has an entry in this data map, **or** is in one of the fuel tags (below). An entry wins over a
tag, so a pack can retune any fluid, including one of this mod's. The shipped file is
`data/create_engines/data_maps/fluid/fuel.json`; this is its whole content in shape:

```json
{
  "values": {
    "mymod:rocket_fuel": {
      "energy_per_mb": 48,
      "autoignition_temperature": 3.0,
      "air_per_mb": 1.3,
      "cold_sensitivity": 0.8
    }
  }
}
```

All numbers are in the simulation's own units, in which **ambient pressure and temperature are 1** and an ordinary cylinder
sweeps a volume of 1. The game shows a charge temperature `T` from these files in degrees Celsius, as `293.15 x T - 273.15`, so 1 reads
20 degrees and gasoline's 2.7 reads 518.

| Key | Required | Default | What it does |
|---|---|---|---|
| `energy_per_mb` | yes | | Energy released by burning 1 mB. This is the fuel's strength: engine power rises with it, and so does fuel economy (less volume for the same work). Must be above 0 |
| `autoignition_temperature` | yes | | Charge temperature that lights the fuel by compression alone, ambient = 1. Diesel (1.55) and plant oil (1.7) are low and light by compression; gasoline (2.7), kerosene (2.2) and ethanol (3.4) are high and need a spark plug. A fuel at or below `2.0` also counts as a **pilot fuel**: a compression engine can inject it as a small shot to light a charge that compression alone could not. Must be above 0 |
| `air_per_mb` | yes | | Fresh charge consumed by burning 1 mB; a full cylinder of ambient air is about 1. Sets the air-fuel balance, and injectors are sized from it (a fuel that needs less than 1.5 per mB gets a proportionally larger shot). Must be above 0 |
| `cold_sensitivity` | no | `1.0` | How much a cold engine block hurts this fuel's burn: 1 is usual, gasoline is 0.8 (best), ethanol 1.8 and plant oil 0.9 (the tolerant one; it was 1.5 before 2.1) |
| `charge_cooling` | no | `0.0` | How far evaporating the fuel cools the charge it is mixed into, in ambient temperatures, at a balanced mixture. A cooler charge is denser, so the cylinder draws more air. Ethanol is 0.08; the others cool nothing |
| `soot_factor` | no | `1.0` | How much of a rich burn's unburned fuel ends up as soot on a spark plug and in the exhaust. 0 burns clean however rich it runs (ethanol) |
| `preignition_temperature` | no | `0.0` | Charge temperature past which a **boosted spark engine** lights the charge early and loses power (it costs power only; nothing is damaged). `0` (or leaving it out) uses the autoignition temperature. Ethanol sets 5.0, so it survives boost that gasoline does not |
| `flammability` | no | `1.0` | How readily a **spill of the fuel lying in the world** catches and how fast a pool burns off once lit, with the server's `fuelFireByVolatility` on: 1 is how every fuel burned before 2.1, above 1 lights surely and burns off in a quick wave (gasoline and methanol 2.0, ethanol 1.5), below 1 is slow to catch and slow to spread (diesel, kerosene and plant oil 0.5). It never touches an engine's tank or a running engine. Must be above 0 |
| `heat_factor` | no | `1.0` | How much heat burning the fuel puts into the engine's block, against the usual for the energy it releases: above 1 runs an engine hotter, below 1 cooler. Every built-in fuel is 1. From 2.4; held between 0 and 10, and anything that is not a number reads as 1 |
| `sulfur` | no | the soot factor | How sulfurous the exhaust is, for the **Pollution of the Realms** compat only; nothing in the simulation reads it. 1 is usual, 0 is none |

**The shipped fuels** (these are the entries in the jar; a pack that gives the same key replaces them):

| Fluid | `energy_per_mb` | `autoignition_temperature` | `air_per_mb` | Others |
|---|---|---|---|---|
| `create_engines:plant_oil` | 30 | 1.7 | 1.6 | `cold_sensitivity` 0.9, `soot_factor` 1.3, `flammability` 0.5 |
| `create_engines:diesel` | 36 | 1.55 | 1.7 | `flammability` 0.5 |
| `create_engines:gasoline` | 40 | 2.7 | 1.5 | `cold_sensitivity` 0.8, `flammability` 2.0 |
| `create_engines:used_oil` | 22 | 1.85 | 1.6 | `flammability` 0.5 |
| `create_engines:ethanol` | 27 | 3.4 | 1.0 | `cold_sensitivity` 1.8, `charge_cooling` 0.08, `soot_factor` 0, `preignition_temperature` 5.0, `flammability` 1.5 |

Numbers outside what the shipped fuels cover work, but `/ce fuels` marks a fuel with `?` when the energy or air figure is
far outside them (a slipped decimal place) and with `!` when a number cannot be right (zero air, a negative energy). The
server's log carries the same `!` warnings when it starts. A fuel flagged `!` is **still used as written**; the check only tells you.

### Fuel tags

A fluid in one of these tags is a fuel with the shipped numbers of that kind, with no data map entry needed. They are the
common `c:` fluid tags other mods already use, so another mod's diesel usually runs an engine without any pack:

| Tag | Counts as |
|---|---|
| `c:gasoline` | gasoline |
| `c:diesel` | diesel |
| `c:biodiesel` | biodiesel (33 energy, 1.55 autoignition, 1.7 air, `soot_factor` 0.5, `sulfur` 0, `flammability` 0.5): the clean diesel, with less energy to the mB. A fluid in both `c:diesel` and `c:biodiesel`, as Create: Diesel Generators' biodiesel is, burns as biodiesel. Before 2.1 it burned as diesel |
| `c:ethanol` | ethanol |
| `c:kerosene` | kerosene (38 energy, 2.2 autoignition, 1.55 air, `cold_sensitivity` 1.1, `preignition_temperature` 3.6; the mod's own Kerosene has no data map entry and runs on this) |
| `c:methanol` | methanol (22 energy, 3.8 autoignition, 0.8 air, `cold_sensitivity` 1.9, `charge_cooling` 0.17, `soot_factor` 0, `preignition_temperature` 6.5; the mod's own Methanol has no data map entry and runs on this) |
| `c:naphtha` | naphtha (37 energy, 2.9 autoignition, 1.45 air, `cold_sensitivity` 0.7; a spark fuel just under gasoline, there for Create: The Factory Must Grow's naphtha; this mod makes none) |
| `c:plantoil` | plant oil |

To make a fluid a fuel by tag, add it to the tag (the file merges; do not set `replace`):

```json
{ "values": [ "mymod:diesel", { "id": "mymod:flowing_diesel", "required": false } ] }
```

at `data/c/tags/fluid/diesel.json`. To give it its **own** numbers, use the data map instead; if a fluid has both, the data map wins.

## Part effects: the four precision parts

A cylinder is the crude one with the effects of the parts it was built with **multiplied in**. There are four parts, one file
each: `bore` (the Precision Cylinder's bore), `head` (the Precision Cylinder Head), `piston` (the Forged Piston) and `injector`
(the Precision Injector). The mod ships all four as `data/create_engines/part_effects/<part>.json` with its built-in values,
and a test pins them to the code, so a pack changes a part by providing the **same path**.

Every key is an optional **scale**: `1` changes nothing, and each is between **0.1 and 10**. A key a file leaves out is 1. A
key this version does not know is refused, so a typo is found instead of ignored.

| Key | Scales |
|---|---|
| `compression_ratio` | the cylinder's compression ratio |
| `breathing` | intake and exhaust valve flow together |
| `ignition_delay` | the time from injection to ignition (below 1 lights sooner) |
| `burn_duration` | how long a shot takes to burn (below 1 burns faster) |
| `friction_static` | friction that does not depend on speed |
| `friction_viscous` | friction that grows with speed |
| `inertia` | the rotating and reciprocating inertia the cylinder adds to the crank |
| `injector_flow` | how much more than the usual most the injector can deliver (matters under boost) |

**The built-in values** (what the shipped files hold):

| Part | Keys it sets |
|---|---|
| `bore` | `compression_ratio` 1.2, `friction_static` 0.7, `friction_viscous` 0.8 |
| `head` | `breathing` 1.5, `ignition_delay` 0.8 |
| `piston` | `inertia` 0.7, `friction_static` 0.8, `friction_viscous` 0.7 |
| `injector` | `ignition_delay` 0.8, `burn_duration` 0.7, `injector_flow` 1.3 |

**The blend rule.** The server's `precisionBonus` setting (default 1) decides how far each effect applies:
`applied = 1 + (scale - 1) * precisionBonus`. At 0 the parts do nothing; at 2 each does twice what its file says. A file's own
numbers are therefore the effect *at the default setting*.

A file that is broken (not a JSON object, a value that is not a number or is out of range, an unknown key) is **logged and that
part keeps its built-in values**; nothing stops a world loading. `/ce parts` lists the files read at the last reload and
which part is using a pack's values. The files reload with the data packs (`/reload`); an engine already built keeps the values
it was built with until it next rebuilds. Tooltips, Ponder pages and the JEI and EMI pages still show the built-in figures.

### A part of your own: the part effect data map

A third tier of cylinder, or a fifth kind of part, is a block of your own that names a `part_effects` file of your own. The block
is a Cylinder or a Cylinder Head: a data pack cannot make one, an addon registers a block of this mod's
`CylinderBlock` or `CylinderHeadBlock` class under its own name (with its own model and texture), and a data pack or the addon's
own resources then points it at a file:

```json
{ "values": { "mymod:racing_cylinder": { "effect": "mymod:racing_bore" } } }
```

at `data/create_engines/data_maps/block/part_effect.json` (the one key, `effect`, is required and is the id of the file), and the effect itself in `data/mymod/part_effects/racing_bore.json`,
with the same keys, ranges and blend rule as the four above. It is multiplied in **after** the built-in parts, so a racing
cylinder that is also bored and forged has all of them. A cylinder block and a head that name the same file count it once. A
block that names a file that was not read has no effect, and a part effect file of a namespace other than `create_engines` that no
block names does nothing. Any block of this mod that names one would change engines built before 2.0, so a test holds that none
does. `/ce parts` lists the files read.

### A flywheel of your own: the flywheel data map

Added in 2.1 (API 1.2). A flywheel that is a pack's or an addon's own is a block made with `PartBlocks.flywheel` (a data pack cannot
make a block), with its own model, and its masses are data:

```json
{ "values": { "mymod:racing_flywheel": { "primary_inertia": 0.6, "secondary_inertia": 2.0 } } }
```

at `data/create_engines/data_maps/block/flywheel.json`.

| Key | Default | Range | What it does |
|---|---|---|---|
| `primary_inertia` | required | 0 to 100 | joins the crank's inertia like any flywheel: it sets rev response, spool and starter load (the Cast Flywheel's is 1.0) |
| `secondary_inertia` | 0 | 0 to 100 | sits behind a spring pack and only smooths the ripple the network sees, above the resonance, as the Dual-Mass Flywheel's secondary mass does |

With no `secondary_inertia` it is a plain flywheel of its primary mass. The resonance of the spring pack is the server's
`dualMassResonance` setting, the same for every part with a secondary mass; a flywheel has none of its own. A value outside its range
is refused when the data pack loads. A flywheel block that no entry names adds nothing to the engine. The block's model is
modelled along the Y axis and turned onto its real axis by its block state, like any rotated block; the mod turns it with the shaft.
No block of this mod names an entry, so a test holds that nothing built before 2.1 can change.

## Cam profiles: when the valves open

Each cylinder's valves open and shut at fixed angles of its cycle, and a rotor's ports at the angles of its own. The mod ships them
as `data/create_engines/cam_profiles/basic.json` (every piston cylinder) and `rotary.json` (every rotor), holding the built-in
values, and a test pins them to the code. A pack changes one by providing the **same path**. Angles are in cycle degrees,
0 up to (not including) 720, where 0 starts the intake stroke and 360 is firing top dead centre.

| Key | Is |
|---|---|
| `intake_open` | the angle at which the intake valve (or port) opens |
| `intake_close` | the angle at which it shuts |
| `exhaust_open` | the angle at which the exhaust valve (or port) opens |
| `exhaust_close` | the angle at which it shuts |
| `injection` | the angle at which the injector fires |
| `spark` | the angle at which a spark plug fires |

Every key is optional and a key a file leaves out keeps the built-in angle (basic: 710, 220, 500, 10, 345 and 350; rotary: 700, 230, 500,
20, 340 and 350). Each valve must be open for between 40 and 400 degrees. A file that is broken (not a JSON object, a value that is
not a number, an angle out of range, a valve open too short or too long, an unknown key) is **logged and that profile keeps its
built-in angles**. The profile times the simulation, which is on the server: the valves drawn on a client keep the built-in shape.
`/ce parts` lists the files read and whether a profile is the pack's. The files reload with the data packs; an engine already running
keeps the profile it was built with until it next rebuilds.

## Part stats: retuning a whole kind of cylinder

Where `part_effects` changes what a *part* does, `part_stats` changes a whole kind of cylinder: scales over the stats that a
cylinder block's tier gives it, applied **after** the part effects. It is a data map at
`data/create_engines/data_maps/block/part_stats.json`, keyed by block:

| Key | Retunes |
|---|---|
| `create_engines:cylinder` | cylinders built with the plain Cylinder block |
| `create_engines:precision_cylinder` | cylinders built with the Precision Cylinder block |
| `create_engines:rotor_housing` | a Wankel rotor chamber, applied after its injector and the server's `rotaryPower` setting |

```json
{
  "values": {
    "create_engines:rotor_housing": { "displacement": 1.2, "max_fuel_per_shot": 1.2 }
  }
}
```

Every key is an optional scale, **0.1 to 10**, and 1 changes nothing; an entry names only what it changes. A value out of
range, or text where a number belongs, makes that entry fail to load (NeoForge logs it) and the cylinder is left alone. With no
entry at all a cylinder is exactly as before.

| Key | Scales |
|---|---|
| `compression_ratio` | the compression ratio (the result never falls below 1.5; below that an engine does not run) |
| `intake_flow` | how freely the intake valve breathes |
| `exhaust_flow` | how freely the exhaust valve breathes |
| `max_fuel_per_shot` | the most fuel one shot can hold |
| `ignition_delay` | the time from injection to ignition |
| `burn_duration` | how long a shot takes to burn |
| `friction_static` | friction that does not depend on speed |
| `friction_viscous` | friction that grows with speed |
| `inertia` | the rotating and reciprocating inertia |
| `displacement` | swept volume: gas and work scale with it. **Scale `max_fuel_per_shot` to match**, or the bigger cylinder is starved |
| `injector_flow` | how much more than the usual most the injector can deliver |

`part_stats` entries are not synced to clients and only the server's simulation reads them.

## Refining recipes

A **refining column** runs *routes*: a feed fluid goes in, a product comes out, at a heat. A route is a recipe of type
`create_engines:refining` in `data/<namespace>/recipe/refining/`. The mod ships these (see that folder for the working examples):

```json
{
  "type": "create_engines:refining",
  "feed": { "fluid": "create_engines:plant_oil", "amount": 1000 },
  "product": { "id": "create_engines:diesel", "amount": 1100 },
  "heat": "heated",
  "speed": 1.0,
  "catalyst": { "ingredient": { "tag": "minecraft:coals" }, "feed_per_item": 1000 }
}
```

| Field | Required | Default | Meaning |
|---|---|---|---|
| `feed` | yes | | `{ "fluid": id, "amount": mB }`, or `{ "tag": "namespace:fluid_tag", "amount": mB }` to take any fluid in a tag. With `product` it gives the **ratio** (here 1000 mB makes 1100 mB), however fast the column runs |
| `product` | yes | | `{ "id": fluid, "amount": mB }` |
| `heat` | no | `heated` | `none`, `heated` or `superheated`: the heat of the base the column needs, named as Create's heat conditions are (a kindled Blaze Burner is `heated`, a seething one `superheated`). A hotter base can always run a cooler route |
| `speed` | no | `1.0` | How fast this route runs against the column's top rate, 0.01 to 100 |
| `catalyst` | no | none | `{ "ingredient": <item ingredient>, "feed_per_item": n }`: one item is used up for every `feed_per_item` mB of feed (a whole number above 0) |

When several routes take the same feed, the column runs the one that asks for the most heat the base can supply, since a hotter
base is what that route is for. Routes show in JEI and EMI. The recipe format joined the 1.x promise the day it shipped: a field
may be added but never changed or removed.

Replace a route like any recipe, by providing a file at the same path; remove one with a script (see
[PACKS.md](PACKS.md#4-refining-routes)).

## Other tags and recipes

| File | What it is |
|---|---|
| `data/create_engines/tags/fluid/poor_lubricants.json` | fluids an engine's oil pan accepts as **oil enough in a pinch** (poorly: they lubricate, but badly). The shipped tag holds plant oil and the common plant-oil fluid tags from other mods; add a fluid to make it a poor lubricant |
| `data/create_engines/tags/item/ferments_mash.json` | items that ferment sugar mash into ethanol in the mod's Create mixing recipes (the shipped tag holds apples and mushrooms) |
| `data/neoforge/data_maps/item/furnace_fuels.json` | the burn time of soot as a furnace fuel (200 ticks); a vanilla data map |
| `data/create_engines/recipe/mixing`, `compacting` | the mod's own recipes for **Create's** mixing and compacting (plant oil and sugar mash, biodiesel, engine oil (slime, glycerin, magma cream or clay), ethanol, soot) |
| `data/create_engines/recipe/*.json` | crafting and the other recipes of the mod's blocks and items |

All of these are ordinary data pack content: replace or remove them the way you would any mod's recipes. In 2.x the mod's own
recipes and tags are covered by its compatibility guard (`compat/manifest.txt`), so they only change by a decision of the
author; yours are yours.
