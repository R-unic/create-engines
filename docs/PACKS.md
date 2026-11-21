# Pack maker's guide

For people putting Create: Engine Engineer into a modpack, a server or a datapack. You do not need to write Java for any of
this. The other two pages are the references: **[CONFIG.md](CONFIG.md)** for every setting and **[DATA.md](DATA.md)** for every
data file. If you are writing a mod that adds to engines, read [ADDONS.md](ADDONS.md) instead.

## What you can change

| You want to | Use | Where it is described |
|---|---|---|
| Make every engine thirstier, stronger, cleaner or more demanding | the server config, or a preset | [CONFIG.md](CONFIG.md) |
| Turn a feature off (turbos, oil ageing, rotary engines, heat recovery...) | `[features]` in the server config | [CONFIG.md](CONFIG.md) |
| Make another mod's fluid an engine fuel | a fuel tag or the `fuel` data map | [DATA.md](DATA.md#fuels-the-fuel-data-map) |
| Change what a precision part does | `part_effects` | [DATA.md](DATA.md#part-effects-the-four-precision-parts) |
| Retune a whole kind of cylinder, or the rotary chamber | the `part_stats` data map | [DATA.md](DATA.md#part-stats-retuning-a-whole-kind-of-cylinder) |
| Change how fuels are refined | `create_engines:refining` recipes | [DATA.md](DATA.md#refining-recipes) |
| Change recipes of the mod's blocks | ordinary recipe overrides | [DATA.md](DATA.md#other-tags-and-recipes) |
| React to an engine starting, stalling or reaching a milestone | KubeJS events | [INTEGRATIONS.md](INTEGRATIONS.md) |
| Read an engine from a computer | the CC: Tweaked peripheral | [INTEGRATIONS.md](INTEGRATIONS.md) |

What you cannot change from data (so far): a cam's timing profile, and the tier of a cylinder block made by another mod. A mod
can add those; see [ADDONS.md](ADDONS.md).

## Where the files go

```
yourpack/
  defaultconfigs/
    create_engines-v2-server.toml          starting balance for new worlds
  config/
    create_engines-v2-client.toml          (a player's own; rarely shipped)
  kubejs/                                  or any datapack folder / zip
    data/
      create_engines/
        data_maps/fluid/fuel.json          fuels
        data_maps/block/part_stats.json    retuned cylinders
        part_effects/bore.json             one file per precision part
        recipe/refining/my_route.json      a refining route
      c/
        tags/fluid/diesel.json             "this fluid is diesel"
```

Data files go in any data pack: a world's `datapacks/` folder, a mod's `data/` folder, or KubeJS's `kubejs/data/`. They load
with `/reload`. Config files do not: change them and run `/ce config` to see what the game has read.

## Five common jobs

### 1. Rebalance all engines

The quickest way is a **preset**. Three ship: `casual` (thrifty, nothing to look after), `standard` (exactly the defaults) and
`expert` (thirstier, long cranks cost rating). On a running world:

```
/ce preset expert
```

That sets the `[balance]` and `[features]` settings, saves the file and applies them at once. To ship it, copy the two tables
from `config_presets/expert.toml` inside the mod's jar into `defaultconfigs/create_engines-v2-server.toml`, or run the command
once in a test world and copy the resulting `serverconfig/create_engines-v2-server.toml`.

To change one dial, edit the file (or `defaultconfigs/`). The ones packs reach for first:

- `balance.fuelConsumption`: thirst, with power unchanged.
- `calibration.stressPerTorque`: how strong **every** engine is next to Create's own machines.
- `balance.precisionBonus`: 0 makes precision parts cosmetic.
- `features.*`: turn a mechanic off. It never removes blocks.

### 2. Make another mod's fluid an engine fuel

Easiest: see whether it already is. Run `/ce fuels`. Many mods tag their diesel, gasoline, ethanol and plant oil with the common
`c:` tags, which an engine accepts with no pack. If it is missing, add it to the tag it should be in
(`data/c/tags/fluid/diesel.json`, `{ "values": ["mymod:diesel"] }`), and it burns as diesel does.

If it should burn **differently**, give it its own numbers in the data map, `data/create_engines/data_maps/fluid/fuel.json`:

```json
{
  "values": {
    "mymod:rocket_fuel": {
      "energy_per_mb": 48,
      "autoignition_temperature": 3.0,
      "air_per_mb": 1.3
    }
  }
}
```

A fuel with a low `autoignition_temperature` (about 2 or below) lights by compression and runs a diesel engine; a higher one needs a
spark plug. Compare your numbers with the shipped fuels in [DATA.md](DATA.md#fuels-the-fuel-data-map): `energy_per_mb` between 22 and
40, `air_per_mb` between 1 and 1.7. Then run `/ce fuels` and look for your fluid; the `!` and `?` lines say what is wrong.

Remember a fluid in the tag **and** the data map takes the data map's numbers, and that `replace: true` throws away other packs'
entries for the whole file.

### 3. Change what the parts do

Make precision parts matter more by editing a `part_effects` file; the shipped ones hold the built-in values, so copy one and change it:

```json
{ "compression_ratio": 1.35, "friction_static": 0.6, "friction_viscous": 0.7 }
```

at `data/create_engines/part_effects/bore.json`. Every key is a scale between 0.1 and 10. A key left out is 1 (no change). The
`precisionBonus` setting then blends the effect: at the default 1 the file is what you get. Use `part_stats` instead to retune a
**whole kind** of cylinder (every plain cylinder, or the rotary chamber) rather than a part.

After `/reload`, `/ce parts` shows which parts use your numbers and lists any file the game refused and why. Engines already
built keep the old numbers until they rebuild (break and replace any block of them, or reload the world).

### 4. Refining routes

Refining columns run routes: `data/<ns>/recipe/refining/<name>.json`. Add one with a data pack file or a script, replace a shipped
one by using its path, and remove one with a script:

```js
ServerEvents.recipes(event => {
  // add: kerosene from diesel at a heated base
  event.custom({
    type: 'create_engines:refining',
    feed: { fluid: 'create_engines:diesel', amount: 1000 },
    product: { id: 'mymod:kerosene', amount: 800 },
    heat: 'heated',
    speed: 0.6
  })
  // remove one route, or all of them
  event.remove({ id: 'create_engines:refining/gasoline' })
  // event.remove({ type: 'create_engines:refining' })
})
```

When several routes take the same feed at the heat of the base, a column goes the way of the **catalyst in its slot**: the shipped
diesel routes are blaze powder to gasoline and glowstone dust to kerosene, and a pack's route with its own catalyst item works the
same way. A slot that is empty, or holds an item none of them takes, runs the route with the better yield, and on a tie the one whose
recipe name sorts first (so diesel over an empty slot is still the gasoline route). A column that runs out of catalyst keeps its route.

The fields are in [DATA.md](DATA.md#refining-recipes). JEI and EMI list the routes, so a route you add is visible to players.
(The script above uses KubeJS's ordinary recipe functions; this mod's CI loads KubeJS but has not run these exact lines.)

### 5. Script around engines

KubeJS sees two events and one global (`CreateEnginesEvents.stateChanged`, `CreateEnginesEvents.milestone` and
`CreateEngines.engineAt(level, pos)`), all read-only; see [INTEGRATIONS.md](INTEGRATIONS.md). A CC: Tweaked computer can read an
engine through the `create_engines_engine` peripheral. Neither can change an engine, so neither can break a world.

KubeJS can also write data pack files from a script, if you prefer to keep everything in one place; the file only has to end
up at the paths above. See KubeJS's own documentation for its data-pack functions: this mod does not ship any script helpers for data.

## Checking your work: `/ce`

`/ce` (permission level 2, "game master") answers what a pack maker would otherwise dig through logs for. It changes nothing
except `/ce preset`, which needs level 3.

| Command | Tells you |
|---|---|
| `/ce help` | the list below, in game |
| `/ce config` | every server setting with the value in use; a `*` marks each that differs from the default, and shows the default |
| `/ce fuels` | every fluid an engine will burn, where its numbers come from (its data map entry or which fuel tag), the numbers, and `!` / `?` lines for anything that cannot be right or is far from the shipped fuels |
| `/ce parts` | what each precision part is doing (built-in values or a pack's), each `part_effects` file read at the last reload and whether it was used, and the `part_stats` entry of each kind of cylinder |
| `/ce api` | the API version and every addon provider registered, by class |
| `/ce preset <name>` | sets `[balance]` and `[features]` to `casual`, `standard` or `expert` and saves; with no name, lists them |

When the server starts it also logs one warning for each fuel in a data pack with an impossible number. It is silent when there
are none.

## When it does not work

| Symptom | Likely cause |
|---|---|
| A fluid does not run an engine | `/ce fuels` does not list it: it is in no fuel tag and has no data map entry. The data map file is at the wrong path (check `data_maps/fluid/fuel.json`), the id is misspelt, or a required key is missing (the log says why NeoForge refused the file) |
| A config change does nothing | the world has its own `serverconfig/` copy; `defaultconfigs/` only seeds worlds that have none. Run `/ce config` to see what is in use |
| A config value came back to its default | it was outside the range (the tables in [CONFIG.md](CONFIG.md) give each range); NeoForge corrects it when it loads the file |
| A `part_effects` file seems ignored | `/ce parts` shows `NOT used` and the reason: a value outside 0.1 to 10, a key this version does not know, or not a number |
| New part values do nothing to an engine | engines keep what they were built with until they rebuild |
| `part_stats` retunes the wrong cylinders | the key is a **block** (`create_engines:cylinder` is the plain one, `create_engines:precision_cylinder` the precision one) |
| A bigger `displacement` made the engine weak | scale `max_fuel_per_shot` by the same amount, or the bigger cylinder is starved of fuel |
| `/ce` is an unknown command | your permission level is below 2 (game master). `/ce preset` needs level 3 |

## What is promised

From 2.0 the formats in [DATA.md](DATA.md) and the public API are frozen: a key, a file name or a setting a pack uses is never removed or renamed
in 2.x, only added. A pack that works on 2.0 works on every 2.x. The rules and the tests behind them are in
[COMPATIBILITY.md](COMPATIBILITY.md) and [API.md](API.md).
