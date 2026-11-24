# Roadmap: 2.0.1 to 3.0.0

Written 2026-11-21, after v2.0.0 was tagged. It continues [ROADMAP.md](ROADMAP.md) (1.0.1 to 2.0.0), which stays as the record
of how 2.0 was built. This is a plan, not a promise: the order is firm, the contents of each release are the current best
guess, and nothing here has a date. **Draft for the author's review.** The only item the author has asked for by name is the
dual-mass flywheel; everything else is proposed, and each proposal says what needs the author's decision.

## Direction

- **2.x is additive, like 1.x was.** [COMPATIBILITY.md](COMPATIBILITY.md) applies again with the 2.0 baseline as the floor: no
  removed or renamed IDs, keys or saved data, and a factory built on 2.0 runs the same on every 2.x. New behaviour comes as a new
  block, item or option that existing builds do not use. Anything that moves existing numbers waits for 3.0.
- **3.0 is the second and last planned breaking release** (see the end). Everything that needs breaking is collected on a list
  as 2.x goes, not discovered at the end.
- **Numbering (the author, 2026-11-21).** A release of bug fixes and config options only, like 2.0.1, gets a patch number. Anything
  that adds a block, item, system or API name gets a minor bump.
- **The 2.0 platform gets used.** Where a feature can be data, an API piece or an addon-sized change, it is built that way first.
- **Debts first.** 2.0 was tagged with five listed exceptions and nine open hands-on boxes (the author resolved them as
  exceptions on 2026-11-21). 2.0.x pays down what can be paid by test, and the rest is looked at by eye before 2.1 piles on.
- The Declined list in [ROADMAP.md](ROADMAP.md) ("Not on this roadmap") stands: no new failure modes or wear in the default game (an opt-in wear mode is allowed, see 2.3), no new fuels, no
  knock, cooling blocks, snorkels, turbines or two-strokes, no mixins into Create's trains. Nothing below breaks it.

## Rules every release keeps

Rules 1 to 9 of [ROADMAP.md](ROADMAP.md) carry over unchanged, with "the 1.x promise" read as "the 2.x promise" (API, script
and data-map surfaces are frozen as of 2.0; the API has its own semver, and a new name bumps its minor). In short: every
feature ships whole with new tests, no new punishment, no new tuning dial unless it replaces one, it looks like Create, settings
go through `Tuning`, old worlds keep loading, and [STABILITY.md](STABILITY.md) is signed off before any tag. A red guard is a
question, not an obstacle ([CLAUDE.md](../CLAUDE.md)).

## At a glance

| Version | Working title | What it is | Size |
| --- | --- | --- | --- |
| 2.0.x | Shakedown II | Pay down 2.0's listed exceptions and unseen parts; no features | Small |
| 2.1 | Twin Mass | **Dual-mass flywheel**, the readouts that make a flywheel choice legible, and sharper niches for the existing fuels | Medium to large |
| 2.2 | Proving Ground | Verification debts: goldens, soak, server-from-jar, the step-size finding; **the Electric Motor and the battery tiers** (author, 2026-11-23) | Medium |
| 2.3 | Open Garage | Reach: API 1.2, energy and probe mods, the next Create, a second example addon; **opt-in wear mode with gaskets and piston rings**, **polluted intake air**, **high- and low-compression piston variants**, and **rotor variants with an exhaust niche** (author, 2026-11-23), then **look, sound and performance** only where the 2.0.x pass found something (was 2.4, merged by the author 2026-11-23) | Medium to large |
| 3.0 | Clean Slate | The second breaking cleanup: drop 1.x migrations, regenerate baselines, API 2.0 | Large |

2.2 and 2.3 are independent of 2.1 and of each other; they can swap places or be cut, and 2.1 does not wait on either.

## 2.0.x Shakedown II

The point is to find out what is wrong with 2.0 before building on it. Nothing here adds a feature; a fix that moves a guarded
number is listed as an exception with the author's name on it, as always.

- **Look at what only tests have seen** (the 3.1 to 3.9 boxes of the v2.0.0 record): the advancement tab layout and that no
  toast plays for carried-over progress, thin air above y 128, the pollution amounts, headphones by ear (`Muffle.GAIN` 0.5 and
  `GAIN_HF` 0.05 are untuned), joined batteries and the lamp strip, the flywheel shaft at every angle, kerosene and methanol, the
  Auto shift profiles, the Replay dial, the turbo blow-off jet. Each either gets ticked by the author or becomes a named fix.
- **Dye tint in all four render paths** (plain, Flywheel, Sodium, contraption), still "to check by eye" since 2.0's state cleanup.
- **Pin the advancement paths** (Exception 2.2): extend `CompatManifestTest` to scan the generated `<track>/<row>` files, so a
  later rename is caught.
- **A real 1.x client config in a real client** (Exception 2.3), once, by hand.
- **The Soot Trap on a running rich engine**: the one pollution GameTest that was never written.
- **Aeronautics client row** (red in the compatibility matrix) and the Create floor: re-try the newest Create against the newest
  Aeronautics and Sable, and move the supported range only if both run (rule 7).
- **Mutation ratchet**: the gate swings by about 3 from run to run; re-measure on a quiet machine and write any improvement in.
- Carry any bug the above finds as a fix release (2.0.2, ...) rather than holding it for 2.1.

**Gate:** the 2.0.1 release record carries these as boxes, and the author ticks the hands-on ones.

## 2.1 Twin Mass

### Dual-mass flywheel (requested by the author)

**Status (2026-11-23): built, unreleased.** Block `create_engines:dual_mass_flywheel`, `DualMass` (the isolation curve, pure),
`EngineSimulation.setIsolatedMass`, scanner and layout (`isolatedInertia`), three config settings (primary 0.45, secondary 1.4,
resonance 48 RPM), model by `tools/generate_models.py`, recipe, Ponder text on the flywheel page, a goggles line, Sable weight.
Tests: `DualMassTest`, a property in `SimPropertyTest`, `DualMassFlywheelModelTest`, the config-extremes case in
`ConfigMatrixTest`, three GameTests. The v2.0.0 fixtures (`engine_v2_0_0_*.snbt`) and their `LegacyWorldGameTests` cases exist.
**Spring-pack dial (2026-11-23, the author; built):** one value box on the flywheel, nine settings, the middle one exactly the numbers above;
`SpringPack` (pure), `CastFlywheelBlockEntity.springPack`, `EngineLayout.isolatedResonanceFactor`, blueprint dial `spring_pack`. Tests:
`SpringPackTest`, `theSpringPackDialMovesTheSecondaryMassAndTheResonance`.
**Not done:** a `pitest` run for the new `engine/sim` code (including `SpringPack`), an in-game look at the model and the dial's value box, and the casing styles. The numbers are a first pass and are the author's to tune.

**What it is.** A second flywheel block that sits on the crank line like the Cast Flywheel but is built as two masses joined by a
spring pack: a *primary* mass bolted to the crank and a *secondary* mass on the output side. In a real car it exists so the
engine can have a light, responsive crank and still deliver a steady torque to the drivetrain. That is the whole design brief:
**a flywheel that smooths what the network sees without slowing the engine down as much as a heavy one does.**

**How it maps onto the simulation.** Today a flywheel is one number, `layout.flywheelInertia()`, added to the crank's inertia
(`EngineController` line 314, `EngineSimulation.inertia`). That one mass does two jobs at once: it slows how fast the engine
revs, and it takes the pulses out of the speed (`predictedRipple = swing / (I * omega^2)`, which `Roughness` turns into a lost
share of the stress rating). A dual-mass part separates the two jobs:

- The **primary** mass joins the crank's inertia like any flywheel. It is what sets rev response, spool and starter load.
- The **secondary** mass does not join the crank. It joins only the ripple the network sees, by a factor that depends on
  speed: near zero in a low band around the spring pack's resonance (so at slow speeds it is just its primary mass, a plain
  flywheel), and rising to full above it (so at cruise it smooths like the whole mass). The shape follows how a real isolator
  behaves: no help below the resonance, and growing help above about 1.4 times it.

**Why it is not strictly better than the Cast Flywheel** (rule 4: no dial, no new failure; the balance comes from the part):

- It never does *worse* than a plain flywheel of its primary mass. The weak band is a loss of the benefit, not a penalty, so
  there is no new way to fail.
- At low speed, where a slow diesel lives and where a hand-cranked or stalling engine sits, it is the weaker part. A big cast
  flywheel stays the right part for a slow, heavy, low-revving engine.
- It is the end-game niche (author, 2026-11-23): a mechanical crafter recipe of two Cast Flywheels, a precision mechanism and six brass sheets, the dearest
  crank-line part, so it is the last thing a big fast build adds, not a first upgrade over the Cast Flywheel (recipe to be priced with `RefiningBalanceTest`-style
  numbers), and a primary mass equal to a small flywheel is all the crank has to carry.
- The niche is a fast, few-cylinder or boosted engine that wants quick throttle and turbo response *and* a smooth network. That
  is the build the Cast Flywheel punishes today, and it is a different progression step, not power creep.

**Compatibility.** A new block with a new ID, joined to the 2.x promise on the day it ships. Nothing existing changes: the Cast
Flywheel keeps its inertia and its ripple behaviour, `compat/behaviour.txt` and `compat/trajectories.txt` are not touched (new
entries are appended for engines that use the new part), the manifest gets appended lines, and a 2.0 world loads and runs the
same. A new fixture, `engine_2_1.snbt`, is recorded from the v2.0.0 source.

**What it touches (to check before building):**

- `EngineScanner` and `EngineLayout`: the layout keeps one number today; it needs the primary and secondary inertia and the
  band, so a blueprint, the Bank Builder and a coupled-crank engine (crank lines geared 1:1 are one engine) all carry both.
- `EngineSimulation`: `inertia` (primary) is unchanged in meaning. `predictedRipple` gains the secondary share with the
  speed-dependent factor. **The sim stays free of game imports** (`ArchitectureRulesTest`); the factor is a pure function of
  RPM, like `PortFlow`, so it gets unit tests and a dyno sweep of its own.
- `Roughness`, the stall and idle logic, the governor and the transmission's Auto shift: all read the crank's inertia or the
  ripple, and each needs a test that a dual-mass flywheel does not change them except through those two numbers.
- `EngineMilestones` (the "flywheel" advancement checks `flywheelInertia() > 0`): the new part must count, and no new
  advancement is added for it.
- Goggles, Display Link, Black Box and the engine note: the ripple and the isolation read out; the note's roughness follows the
  ripple the network sees only if it already does today (check, do not change).
- Aeronautics and Sable: it is an ordinary crank-line part and needs no special case, but is added to the structure GameTests.
- Rotary and steam engines: say in the Ponder page and goggles whether each takes it (it should work on any crank line that has
  a flywheel slot today; check `RotorHousing` and the steam engines' own inertia before promising).

**Built like the rest of 2.x:**

- Model and texture generated from Create's own (rule 5): two chunky discs, a ring of springs between them in brass and iron;
  `tools/generate_models.py`, shown in the Ponder page, the inventory icon and the in-world smoke shots. Casing styles follow
  whatever the Cast Flywheel does today (not checked). It uses the same collision as the Cast Flywheel unless the model needs more.
- Config through `Tuning` / `CEConfig` (two inertias and the resonance, defaults that are the shipped numbers), and
  `ConfigMatrixTest` runs them at their extremes.
- Lang, a Ponder page ("Flywheels: one mass or two"), goggle lines (primary and secondary mass, where it is isolating, the ripple
  it removes), Jade and the other integrations through the existing readout, and a Display Link field if the author wants one.
- **Tests (new, unasked as always):** unit tests for the isolation curve (never below the plain flywheel of the primary mass,
  zero secondary equals a Cast Flywheel, rises with speed, finite everywhere including NaN and zero RPM inputs), a property test
  added to `SimPropertyTest`, a dyno sweep that shows where it pays and where the Cast Flywheel wins, GameTests (a running engine
  with the new part is smoother than the same engine with a Cast Flywheel of the same primary mass, revs faster than with one of
  the same total mass, saves and reloads, blueprint round trip, break and replace, chunk unload), a `LegacyWorldGameTests` case
  for the new fixture, and `pitest` re-run because `engine/sim` changes (the ratchet only moves by hand, down).

**Decided by the author, 2026-11-21:**

1. **One block**, with the primary and secondary masses built in. No connected two-block structure.
2. **The numbers come from a dyno sweep** against the Cast Flywheel, shown to the author as a table before they are written in.
3. **The recipe is end-game**: a mechanical crafter recipe (two Cast Flywheels, a precision mechanism, six brass sheets).
4. **Nothing about shifting changes.** It is only a flywheel.

### Around it (small, same release)

- **Timing Tool for rotors: already built (found 2026-11-23, nothing to do).** `TimingToolItem.timeRotors` and `RotorPlanner` have phased
  rotor eccentrics since the rotary engines shipped, and `RotaryEngineTest` pins the angles; the text below was written from a wrong reading.
- **Rotary weight check: done 2026-11-23, already right, no change.** A housing weighs 1.5 (medium) against a cylinder's 2.0 (heavy), and
  fires 0.65 of a cylinder's swept volume a shaft turn against 0.5, so about 1.7 times the firing per unit of mass. No weight moved;
  `SableWeightsTest.aRotorHousingMakesMoreFiringPerKilogramThanACylinder` pins the ratio. No goggles line was added.
- **(Original text, Timing Tool for rotors; stale.)** (author, 2026-11-23). The Timing Tool skips rotor housings, so a multi-rotor engine's eccentrics are
  set by hand (180 degrees for two, 120 for three). Let it phase them the way it phases throws: a new action, so a build that never
  uses it is untouched, and the dials stay editable (no lock, as with tune mode). A test that a twin and a triple-rotor build phased
  by the tool match the by-hand angles `RotaryEngineTest` pins, and that nothing changes without the tool. It also makes bigger
  rotary layouts easy to build, but no new layout is proposed here.
- **Rotary weight check** (author, 2026-11-23; check first). The roadmap says a rotary earns its place in a plane. Compare the
  Rotor Housing's Aeronautics weight with a piston engine of the same power (the 2.0 weights are data). If it is already right, only
  a goggles or tooltip line is needed. If it is not, a change moves existing contraptions' weight, so it comes as a new key with
  today's default, or it is a 3.0 item.
- **Flywheel readout for every flywheel.** The Cast Flywheel's goggles show only its mass today. It gains the ripple it removes
  and what an engine's roughness loss is, so choosing between the two parts is a reading, not a guess. Information only; no
  number moves.
- **Flywheel readout, built 2026-11-23.** An engine with any flywheel on its line (Cast or Dual-Mass) and a running crank gets a
  goggles line from `EngineController.flywheelLine`: the ripple it has, the ripple it would have without the flywheel mass
  (ripple scales as one over the inertia), and the share of the rating that keeps (`Roughness.loss` of the two). Information
  only. Test: `aFlywheelOnARunningEngineReportsWhatItIsWorth`.
- **Roughness on the Display Link, built 2026-11-23.** `engine_smoothness` (`EngineReadout.smoothness`, one minus the goggles'
  rough-running loss), a percent or bar source on every crankshaft block like load and oil. New manifest line
  `reg:displaySource:engine_smoothness`. Test: `displayLinksCanReadAnEngineAndItsPan`.
- **Metering for all heads, built 2026-11-23 (the author's answer: "a toggleable config option that, when on, sets metering for all
  cylinders on an engine when you change the metering via dial for one cylinder").** Not the Timing Tool mode proposed below: server
  setting `features.meterAllHeads` (off by default). With it on, a player's change to one head's or rotor housing's metering dial writes the same
  figure to every other head of that engine (`MeteringFollow`, `EngineController.setAllMetering`); heads held by a MAF sensor or a closed rack are
  skipped, the Timing Tool's tune mode still sets each head separately (it runs with the follow switched off), and no dial is ever locked.
  A flat figure, not a scaled one. Tests: `MeteringFollowGameTests` (on sets the rest and a later change follows, off leaves each head, a
  head of another engine is untouched), the `ConfigMatrixTest` switch. Not done: a held-head GameTest (a head under a MAF sensor), and a look in game.
  Original proposal follows.
- **Metering for all heads at once (proposed 2026-11-22, the author).** An opt-in way to set one metering figure across every
  head of an engine, for builders who do not want to dial each head. **Per-head dials stay the source of truth**: heads differ
  in the air they draw (port networks, throttles at different openings, a turbo on one bank only, precision injectors, ethanol's
  1.5 times injector flow, rotor housings), so a single figure runs some heads rich and others lean, and Tune fuel is right only
  at the speed it was run. Rules for building it: it is a one-time *apply* that writes each head's own dial and leaves every
  dial editable afterwards, never a lock; it skips heads held by a MAF sensor or a closed rack, as `tuneFuel` does; nothing saved
  changes, and an existing engine is never rewritten unless the player applies it (rule 3). **Not decided:** where it lives (a
  Timing Tool mode, a `Tuning` option, or a goggles action) and whether it applies a flat figure or scales each head's current
  value by a share. Tests: applying then reloading keeps per-head values, skipped heads stay untouched, a 2.0 world is unchanged
  until the action is used, and a mixed-fuel or boosted-bank engine ends up with different figures per head where it should.

- **Spring pack animation (2026-11-23, the author; built, unseen in game).** The dual-mass flywheel's secondary disc is drawn a
  little behind the primary through the resonance and falls back into step as RPM rises, so the isolation can be seen. **Render
  only**: no rating changes, no sound, no shudder or other penalty (rule 4), and the sim and the server are untouched.
  Built as `SpringPackLag` (a pure function), `DualMassPose` (finds the crankshaft the flywheel sits on along its shaft and reads
  its synced figures), a visual and a fallback renderer that draw two partial models, `dual_mass_flywheel/primary` and
  `/secondary`, which `tools/generate_models.py` cuts from the one wheel (the secondary is its wheel, stripe, spokes and lugs).
  **What moves it, so it shows from the first turn to the redline:** the load (wind-up of 3 to 8 degrees; a turning engine is
  always at least `PRELOAD` of it), the firing pulses (a swing of up to 8 degrees either side at the firing rate, larger with
  the ripple, 35% of it below the resonance and all of it once the secondary is isolated) and 3 degrees extra passing through the
  resonance: up to 19 degrees behind and 8 ahead. Two earlier versions were invisible: the first moved it 2.5 degrees only
  below three times the resonance; the second took its size from the load and the ripple, which a probe of a running diesel
  showed to be tiny by design (load 0, ripple 0.03: about 1 degree of swing), so it now has floors. The lugs stop short of the
  spring ring (18.3 out against 18.5) so the secondary can swing without a lug passing through a spring.
  Tests: `SpringPackLagTest`, `DualMassSplitModelTest`. The client audit (every model and Flywheel partial loads, 99 Ponder pages)
  passes with it. **Found on the way:** the Dual-Mass Flywheel's model used 90 degree element rotations, which the game refuses
  (it allows 45), so the whole block's model failed to load; the generator now builds the quarter-turned pieces as boxes with x and
  z swapped (`quarter()` in `dual_mass_flywheel()`). Not done: an in-game look at the wheel and the lag.
- **A flywheel defined by data (2026-11-23, the author; moved up from 2.3; built, API 1.2).** An addon makes a block with
  `PartBlocks.flywheel`, registers it with `PartBlocks.register` as for a cylinder, and gives it masses in a new `flywheel` block
  data map (`primary_inertia`, and an optional `secondary_inertia`, each 0 to 100 like the server's flywheel settings; `FlywheelSpec`
  holds the range, `CEDataMaps.FlywheelProperties` the codec). Its model is the block's own, drawn upright and turned with the
  shaft by a new block entity type, `data_flywheel`, which has none of this mod's blocks and takes the addon's. The scanner reads
  the entry the way it reads the two shipped flywheels; a flywheel block with no entry adds nothing. **No block of this mod is
  one**, so nothing built before 2.1 can change (`OwnPartGameTests` checks every block). **A change from the plan above: no
  resonance of its own.** The spring pack's resonance stays the one server setting, `dualMassResonance`; a per-part value needs
  the layout to carry a weighted resonance through every blueprint, coupled crank and test, for a case nobody has asked for.
  The Cast and Dual-Mass flywheels are not data entries. New names: `PartBlocks.flywheel` (`compat/api-surface.txt`), `data
  flywheel primary_inertia` and `secondary_inertia` (`compat/script-surface.txt`), `reg:blockEntity:data_flywheel`
  (`compat/manifest.txt`), `ApiVersion.MINOR` 1 to 2. Docs: ADDONS.md, DATA.md, API.md. Tests: `FlywheelSpecTest`, the surface
  tests, `OwnPartGameTests`, and the example addon's Racing Flywheel with its GameTest, which passes (all 3 of the example's tests) against
  the built jar: an engine with the Racing Flywheel revs up more slowly than a plain one and runs. Not done: the Racing
  Flywheel's model, and the data flywheel turning in the world, have not been seen in game.

### Fuel character (built 2026-11-23, unreleased)

**Status (2026-11-23): items 1 to 4 built at the author's say-so, not seen in game, no Ponder page yet.** The author answered in chat: all
four items, and "Change it now (exception)" for biodiesel and for plant oil instead of the opt-in routes proposed below, and "Spills only, on
by default" for fire. So **existing numbers did move**, recorded as Exception 2.9 in [the v2.1.0 record](releases/v2.1.0.md):

- Biodiesel is its own fuel (`FuelSpec.BIODIESEL`: 33 energy, soot 0.5, sulfur 0), where it burned as diesel before.
- Plant oil's cold sensitivity 1.5 to 0.9, soot and sulfur 1 to 1.3. **A fuel-level tolerance for a poor mixture was not built**: the
  simulation has no per-fuel mixture tolerance, and adding one is a larger change than the item named.
- Spills burn by fuel: new `FuelSpec.flammability` (data key `flammability`, default 1), `SpillFire` (pure), `FuelLiquidBlock`, setting
  `fuelFireByVolatility` (on). Gasoline and methanol 2.0, ethanol 1.5, diesel class 0.5. Never touches a tank or a running engine.
- Cold item: the cold readout already existed in the fuel tooltip; it now also says dirty or cleaner exhaust and how a spill burns.
**Ponder page built:** "Choosing a Fuel" (`FuelScenes.choosingAFuel`, on the plant oil, diesel, gasoline, kerosene, ethanol and methanol buckets; text over a
running engine on the rain cap schematic, so no new schematic); the client audit plays it (1179 checks, 0 warn, 0 fail). Not seen by eye.
**Not done:** a Display or goggles fuel comparison beyond the tooltip, a dyno table of the numbers
(they are a first choice for the author to tune), and a look in game. The text below is the original proposal.

### Fuel character (the original proposal)

**Proposed 2026-11-22, in 2.1 because it is the release with player-facing features; the items are the author's pick from a longer list, the details are not yet decided.** This is not new
fuels: the Declined list stands, and so does the author's 2026-11-21 answer (no more fuels beyond methanol, no gases, nitrous,
heavy fuel oil, turbine or two-stroke). It gives the seven fuels that exist (plant oil, diesel, gasoline, ethanol, biodiesel,
kerosene, methanol) a clearer reason to be picked, the way 1.x gave ethanol its boost niche. The ethanol distillery, which was
also on the list, is not included here.

Two constraints decide how each item can be built, and each item says which one bites:

- **No existing number moves in 2.x.** A factory on 2.0 runs the same, so a fuel's current behaviour cannot be retuned. A new
  behaviour comes as a new optional key in the fuel data map whose default is today's value, or as something that only a new
  block or option uses. Anything that can only work by changing an existing fuel's numbers is a 3.0 candidate (see the end).
- **No new punishment** (rule 4 and the Declined list: no new failure modes or wear). A niche must be a reason to pick a fuel, not
  a new way to lose. Where an item below could read as a penalty, it is flagged and needs the author's decision.

1. **Volatility in the world (fire behaviour).** Spilled or leaking fuel behaves by how volatile it is: gasoline and methanol
   ignite readily and spread, diesel, biodiesel and kerosene are hard to light and safe to store. A first step shipped in 2.0.1
   (`content/fuel/`, `FuelFireGameTests`, switch `compat.fuelFire`, on by the author's choice): all fuels burn alike when lit, so
   the per-fuel difference below is still to do.
   **Needs the author's decision:** a fire that spreads is a new way to lose a build. It only passes rule 4 if it is limited to
   fuel that has already escaped (a spill the player made) and never touches a running, intact engine, and if the config can turn
   it off. Without that it does not ship.
2. **Cold behaviour as an axis.** Uses the `cold_sensitivity` key the fuel data map already has. Kerosene and the alcohols read
   differently in cold biomes: kerosene stays easy to start at altitude and in snow, the alcohols start harder cold and pair with
   the flywheel and tune mode. **Constraint:** the existing fuels' cold values are baselines, so only the new reading (goggles and
   tooltip) and new data keys with today's defaults ship in 2.x; an actual retune of an existing fuel is a 3.0 item.
   **Needs the author's decision:** biodiesel gelling in the cold is a punishment; it is not proposed unless the author asks.
3. **Plant oil as the runs-on-anything fuel.** Plant oil keeps its role as the fuel that needs no processing and can be fed
   straight from a farm, and gains a reason to stay in use after diesel: it is tolerant (a poor mixture or a cold engine does not
   cost it what it costs the others) in exchange for being dirty and low in energy. **Constraint:** plant oil's present numbers
   are guarded, so the tolerance is expressed as new keys defaulting to today's values and enabled by a data-map entry or an
   option, or it waits for 3.0. Not decided: which of those two.
4. **Biodiesel as the clean diesel.** Lower soot and a lower pollution share than diesel, set against its glycerin chain and its
   lower energy. **Constraint:** the same as plant oil; `sootFactor` and the pollution multiplier already exist as data keys, so
   this is likely a data and tooltip change if the current values are not already where the author wants them. Not checked.

**Before building:** read `FuelSpec` and the fuel data map and record, for each of the seven fuels, which of cold, volatility,
soot, pollution and charge cooling are already set to something other than the default. Some of this niche may already exist and
be unlabelled, in which case the work is the goggles and tooltip lines and a Ponder page ("Choosing a fuel"), not new numbers.

**Built like the rest of 2.x:** a fuel comparison in goggles and the fuel tooltip, lang, the Ponder page, config through `Tuning`
for anything with a default, and no new tuning dial unless it replaces one (rule 3). A fuel's numbers are measured by dyno the way
methanol was (`BoostParityTest`-style) and shown to the author as a table before they are written in; `compat/behaviour.txt` and
`compat/trajectories.txt` are not touched, and new entries are appended only for new behaviour.

**Tests (new, unasked as always):** a unit test per new key proving the default reproduces today's numbers exactly for every
existing fuel, data-map parsing and rejection of bad values, a property test that no fuel with the new keys produces a
non-finite or negative result, a `ConfigMatrixTest` row at the extremes, GameTests for each world behaviour (a spill of each fuel
behaves as stated, an intact running engine is unaffected, save and reload, chunk unload), and a `LegacyWorldGameTests` case
showing a 2.0 world with each fuel runs the same. `pitest` is re-run if `engine/sim` changes.

## 2.2 Proving Ground

Mostly verification work that the 2.0 record left open, which changes no behaviour. Each item lands on its own and goes into whichever
release has room.

**Added 2026-11-23 (the author): the Electric Motor, with the battery tiers (Dry Cell and Large Battery).** The motor is row 3 of the 2.1 table in [ROADMAP.md](ROADMAP.md)
("Part depth" follow-ups): a standalone shaft source fed from the battery bank, with a redstone setpoint. The Dry Cell (100 sparks) and the Large Battery (800 sparks),
both built and then moved out of 2.1, come with it, because a motor flattens a 200-spark battery in seconds. These are new blocks, so they
do not change any existing build; release exception 2.7 (the battery seam) has to be explained before they return, and the tiers need a Ponder page that
explains them (the current scene only names the blocks). 2.1 is the flywheel and roughness release and carries no battery block.

- **Goldens** (item 13 of the verification track, set aside until now): the visual set (fixed-camera shots of every casing and part
  model, plus a few Ponder frames, compared per region with a tolerance) and the audio set (synth-level scenarios recording
  loudness, peak, clipping and a coarse spectrum). `./gradlew updateGoldens`; the commit is the approval; no tolerance is ever
  widened to make one pass. The commands go in [SMOKE.md](SMOKE.md) the day they exist, and [CLAUDE.md](../CLAUDE.md)'s "Goldens"
  section is made true.
- **The 60-minute unattended soak** (STABILITY 2.4, Exception 2.4): automate it, so nobody has to run it by hand and the
  exception stops being needed.
- **A server booted from the jar** (verification item 5): needs a NeoForge server install in the build.
- **The step-size finding.** Above about 1500 RPM the per-cycle net torque and fuel rate move with the step (`StepIndependenceTest`).
  Create's limit is 256 RPM so nothing shipped is near it, but a raised limit is the recommendation in the high-RPM notes. Fixing
  the cycle bookkeeping moves guarded numbers only above that speed; if it moves any baseline it is a 3.0 item, otherwise it ships
  here with the diff shown.
- **The config matrix through the client audit's fleet** (item 9) and the **PIT survivors that are not equivalent mutants**
  (killing tests, ratchet down by hand).
- **The ten-break mutation script** (`scripts/smoke-mutations.ps1`) re-run after the leak-trend change that was never checked.

## 2.3 Open Garage

Reach, in the order the author's direction names (other Create addons and pack makers). Everything is optional and compiled
against without being required.

- **API 1.2**, from what the example addon and real addons find missing. The rule stands: an addon never needs an internal class, and
  a need is a gap in the API. Candidates already known: reading and subscribing to an engine's readout and its ripple (so the
  dual-mass flywheel is visible to addons), registering a crank-line part (a flywheel of an addon's own, through data), and a
  display source (the data-defined flywheel moved to 2.1 and is already API 1.2: `PartBlocks.flywheel`). Each is a new name in `compat/api-surface.txt` and `compat/script-surface.txt` and a bump of `ApiVersion.MINOR`.
- **A second example addon** in `examples/`, this time of a part that is not a cylinder (a flywheel or a crank-line part), to prove
  the same route for a second kind of block.
- **Forge Energy input for engines** and **WTHIT / The One Probe**, both listed as "not done" in the 2.0 record. Each gets a row in
  [COMPAT.md](COMPAT.md) and the compatibility matrix, and is called supported only once its row is green.
- **The next Create.** Move the supported range when Create and Aeronautics allow (2.0.x settles whether they do now); each Create
  version in range is built and tested against (rule 7), and if two cannot sit side by side the release says so.
- **Pack tools**, as asked for: check [PACKS.md](PACKS.md) against what pack makers ask for in the feedback channel (the
  `triage-feedback` skill reads it) before choosing anything here. Nothing is built from a guess.

### Piston variants: high and low compression (author, 2026-11-23; proposed)

**Chosen by the author on 2026-11-23, from a list of end-game power ideas, and placed in 2.3 (not 2.1) by the author the same day. Nothing is built; the details below are a first design.**
The end-game power path is one chain (air, boost, fuel, oil), so more power today means a longer chain. This adds a choice instead:
two piston variants that trade naturally aspirated (NA) output against boost headroom.

- **High-compression pistons:** more efficiency and NA torque, a smaller pre-ignition margin under boost. Paired with ethanol (which
  resists pre-ignition) this is the end-game NA build.
- **Low-compression pistons:** less NA output, a higher boost ceiling, and gasoline becomes a workable boosted fuel. This is the
  twin-turbo build.
- **The stock piston is today's numbers.** Both variants are new items, so no 2.0 factory changes (rule: no existing number moves
  in 2.x). They are items, not a dial (rule 3).

**What already exists to build on:** pre-ignition is already modelled. `Combustion` loses a share of the burn
(`FuelSpec.preignitionLoss`, capped at `PREIGNITION_MAX_LOSS`) when boost heats the charge past the fuel's autoignition point, and
only on an engine with a plug fitted. Compression ratio most plausibly acts on the same charge temperature (`compressionTemp`), so
the variants shift one existing quantity and add no new failure mode. Not yet checked in the code: where the charge temperature is
computed, and whether a per-cylinder compression factor fits there without touching the guarded baselines.

**Decided by the author on 2026-11-23 (all four proposals below accepted as written); the checks that remain are in the code:**
1. **How a piston is fitted.** Look at how precision parts are fitted today and follow it, so the player learns one gesture.
2. **Compression-ignition engines.** Diesel, kerosene and plant oil are lit by the heat of compression, with a pilot shot for the
   hard-to-light fuels. A compression ratio that changes that heat changes how easily they light. High compression helping them is a
   plain gain; low compression making them harder to light would be a new way to lose, so the proposal is that **low compression has no
   effect on a compression-lit engine's lighting** and only high compression gives it a bonus. Accepted.
3. **Which engines.** Piston engines only. Rotor housings and steam engines have no piston to swap and are left out (rotors get
   their own variants, see "Rotary depth" below).
4. **The numbers.** Measured by dyno and shown to the author as a table before anything is written in (`BoostParityTest` style), as
   methanol and the boost classes were. No number is guessed.

**Built like the rest of 2.x:** goggles show a cylinder's piston and the pre-ignition margin it leaves, the item tooltips state the
trade, lang, a Ponder page that sits next to "Choosing a fuel", config through `Tuning`, a blueprint round-trip (a blueprint of an
engine with variant pistons keeps them; a 2.0 blueprint loads unchanged), and a data-map or API route if parts are data.

**Tests (new, unasked as always):** a unit test that the stock piston reproduces today's numbers exactly for every fuel and class,
one that high compression raises NA output and lowers the pre-ignition margin and low compression does the reverse, a property test that no
combination produces a non-finite or negative result, a `ConfigMatrixTest` row, a GameTest for each variant fitted, saved and reloaded,
and a `LegacyWorldGameTests` case showing a 2.0 world runs the same. `pitest` is re-run, since `engine/sim` changes.

### Rotary depth: rotor variants and an exhaust niche (author, 2026-11-23; proposed)

**The author accepted these on 2026-11-23, from a list of rotary ideas; nothing is built.** (Port variants, the first idea on that
list, were not taken. Apex seals are under Wear mode below, the Timing Tool for rotors and the weight check are in 2.1 "Around it",
and the rotary's sound and look are under "Look, sound and performance" at the end of 2.3.) A rotary today is one block with one set of numbers: a Rotor Housing on the crank line,
three ported chambers, spark only, with its rotor fitted by hand and its port timing in `cam_profiles/rotary.json`.

- **Rotor variants.** The counterpart to the piston variants above, and built on the same compression factor so the work is done
  once. A **lightweight rotor** has less inertia: a higher redline and quicker response. A **high-compression rotor** gives more NA
  power and less boost headroom, using the pre-ignition model the pistons use. The stock rotor stays today's numbers. New items, a
  fitted choice and not a dial (rule 3), and pistons' rule on compression-lit engines is moot here: a ported chamber never
  compression-ignites, so a rotary has no compression-lit case to protect.
- **Exhaust energy niche.** A rotary dumps a lot of hot exhaust, so on a rotary a turbo spools faster and a heat exchanger yields
  more. **Constraint:** the existing housing's numbers are guarded, so the niche rides on the new rotor items, or on new
  `part_stats` keys for `create_engines:rotor_housing` whose defaults are today's values. Making every existing housing hotter would
  move existing builds, so it is a 3.0 candidate. The turbo and exchanger numbers are measured by dyno and shown to the author as a
  table first (`BoostParityTest` style); none is guessed.

**Built like the rest of 2.x:** goggles show the fitted rotor and what it changes, item tooltips state the trade, lang, a Ponder
mention on the rotary page, blueprint round-trip for the fitted rotor (a 2.0 blueprint loads unchanged), config through `Tuning`.
**Tests (new, unasked as always):** the stock rotor reproduces today's numbers exactly (`RotaryEngineTest`'s balance pins stay put),
each variant moves the quantity it says and no other, a property test for finite and non-negative results, a `ConfigMatrixTest`
row, a GameTest for each rotor fitted, saved and reloaded, a `LegacyWorldGameTests` case with a 2.0 rotary, and `pitest` re-run
since `engine/sim` changes.

### Polluted intake air (author, 2026-11-23; opt-in)

Suggested by SilverThorn on Discord ("pollution of the realms chunk pollution effecting engine air", marked "not needed but could
be cool"); the author accepted it for 2.3. Today the Pollution of the Realms compat only writes: engines put carbon and sulfur into
the air and nothing is read back into an engine. This is the other direction. **Opt-in only: a config switch, off by default**, so
no existing build or world changes (rule 1), and nothing is read without the pollution mod installed.

- **The idea:** the carbon and sulfur held by the air an engine breathes lower the quality of its intake, so an engine in a heavily
  polluted area loses some air and so some power, and a clean area behaves as today. The engine's own exhaust can then foul its own
  air in a closed workshop, which is a reason to vent it away or to place an intake in clean air.
- **Reuses the area read** the outlet cap already does (the 7 by 7 by 7 cube, `PollutionProbe`), taken at the intake, not the
  chunk total the pollution mod counts.
- **Still to settle** (author): how much power the worst air costs and what scale of gas counts as "worst" (set against
  `pollutionAreaCap` so one number is not two); whether carbon and sulfur act differently; the goggle line that shows intake quality; and the cost of the probe on the tick
  (read once a second, cached, never every step, with a soak test like the other tick paths). Tests: off means identical to
  today (`compat/behaviour.txt` untouched), the loss is monotonic in pollution and bounded, the mod absent changes nothing.
- **Air filter** (author, 2026-11-23: for Pollution of the Realms packs only). A new intake part that cleans the air an engine
  breathes, so a player can answer polluted air with a part, not only by venting. **It is meant for packs with the pollution mod, so
  it is only obtainable and only does anything there.** Do this without making the block's ID depend on the mod: a block registered
  only when another mod is present vanishes from a world when that mod is removed, which breaks the compatibility promise (no
  removed IDs, saved data kept). So the filter is always registered, but its recipe and its creative-tab entry are gated on the
  pollution mod being loaded (a recipe condition, as other compat items do), and with the mod absent or the intake switch off a
  placed one is a plain inert block that loses nothing. How well it cleans, whether it clogs and is replaced (which would be wear,
  so only with the wear mode on) are still to settle. **Recipe (author):** it uses wool or paper, and charcoal or coal (a filter
  medium and a carbon bed); the rest is copper or copper sheets, as for the other intake parts (author). The exact shape and counts are still to settle, and are
  checked against the other intake recipes so the filter is not cheaper or dearer than they are. Needs tests that it is inert and unobtainable without the
  mod, that the part and its saved data survive the mod being removed, and a Ponder page and goggle line.

### Wear mode (author, 2026-11-23; opt-in)

Requested by SilverThorn on Discord ("optional damage and wear and tear", "engine upgrades"); the author reopened the Declined
entry on 2026-11-23 and placed it here. **Opt-in only: a config switch, off by default**, so no existing build, world or number
changes (rule 1). With it off, nothing below runs. Decisions so far, not yet a design:

- **What wears:** pistons, connecting rods and the new gaskets, and on a rotary the **apex seals** (author, 2026-11-23): the part
  rotaries are known for losing, standing in for the piston rings and rod a rotor housing does not have. They follow the same
  rules (opt-in, replaced or lightly repaired by item, soak-tested failure path), and a sealed-out chamber stops like a failed
  cylinder does. The rotary's wear curve is measured separately by dyno, not copied from the piston's. Wear grows with RPM and faster with heat, no oil and over-revving;
  gaskets also wear with boost (cylinder pressure). Worse wear means worse performance and a failure chance that starts near zero
  and rises with RPM.
- **Failures** (author: gaskets can fail too): a rod can be thrown, with a loud bang, and a gasket can blow. Either stops that
  cylinder until it is repaired.
- **Repair** (author: some damage with items, most by replacing): light wear can be set right with an item; most damage, and every
  failure, is cured by replacing the part (the piston and rod of a thrown rod, or the gasket).
- **Gaskets** in three tiers (cardboard, rubber, copper) applied to a cylinder head, sealing leaks for higher cylinder pressure.
  **Piston rings** (up to three, each a small power gain) are a plain upgrade and need no wear; they work with the mode off.
  New items, so new IDs, allowed in 1.x terms and joined to the promise at 2.0.
- **Still to settle** (author): which light damage the repair item covers and what it costs; the exact wear and failure curves
  (tested, then pinned); whether a worn part shows on goggles and the Black Box; and the upgrade recipes. Each gets tests, lang
  entries, a Ponder page and a goggle line, and the failure paths get soak tests like the sound and tick paths.

### Look, sound and performance (was 2.4 Paint and Polish; merged into 2.3 by the author, 2026-11-23)

Held until 2.0.x has looked at what 2.0 added. Contents are whatever that pass finds, plus these, each only if a profile or an eye
asks for it:

- **Sound.** Retune what the 2.0.x listen finds (headphone filter numbers, methanol, kerosene, the turbo jet), and the voice cap
  and out-of-earshot pause from the optimization list. The 1.2.0 out-of-memory fix is still the standard to hold.
- **Rotary sound and look** (author, 2026-11-23; check first). A rotary should not sound like a piston engine: no valvetrain, a
  smooth rising note at high RPM, a burble at idle, flame at the exhaust. Check what the Rotor Housing already does in the engine
  note, the stack flame and the particles, and build only the gap. The 1.2.0 sound-thread out-of-memory fix is the standard to hold,
  and anything heard is the author's to tune by ear. Cosmetic: no rating changes.
- **Render.** Animation level of detail and batching, only with a client profile that shows a cost (the 2.0 profile found none, so
  nothing is built on speculation). Flywheel off, Sodium and Aeronautics rows stay green.
- **Cosmetics.** More casing variants and dye options in the dress-parts line, and the dual-mass flywheel in each casing style if
  2.1 did not already. Cosmetic parts never change a rating (rule 4).
- **Blueprints.** Whatever the dual-mass flywheel and the 2.x parts show the format missing; a blueprint saved on 2.0 must keep
  loading (the format is part of the promise).

## 3.0 Clean Slate

The one planned breaking release after 2.0. Like 2.0, it is the only place the promise may be broken, so everything lands in one
reviewed change with the author's decision on each line. **Candidates, collected as 2.x goes (none decided):**

- **Drop 1.x migrations.** `PaintMigration`, `ConfigCarryOver`, `AdvancementMigration`, `Gears.fromLegacy` and the 1.x fixtures in
  `compat/world/` exist only to open 1.x worlds. 3.0 can say "open a 2.x world first" and delete them. The author's call: keeping
  `Gears.fromLegacy` was already deemed a decision, not a cleanup.
- **Regenerate the baselines** (`behaviour.txt`, `trajectories.txt`) once, with every balance and step-size change that 2.x had
  to hold back, and list each under Exceptions.
- **API 2.0.** Anything frozen in 2.0 that 2.x showed to be wrong. Prefer a deprecation in 2.x and a removal here.
- **Register cleanup.** Any alias, hidden item or compatibility shim 2.x kept only for 2.0 saves.
- **The Create floor.** Move to whatever Create is current (rule 7) and drop the older range if the two cannot coexist.
- **Fuel retunes held back from 2.1.** Any change to an existing fuel's cold, soot, pollution or tolerance numbers that 2.1 could
  not make additively (plant oil and biodiesel above all), listed under Exceptions with the dyno table.
- **Revisit what stayed on hold:** variable valve timing (only if a dyno sweep shows it pays), the throttle body decision, the basin
  recipes against the refinery numbers, and the dual-mass flywheel's numbers after a year of players.

**3.0 gets a headline feature, not yet chosen** (the author, 2026-11-21: "it should", but none is named yet). It goes here as its
own section once the author picks it, and it is picked early enough that 2.x can lay any groundwork it needs (for example an API
piece or a data format) without breaking anything. Until then 3.0 is only the cleanup above.

## Not on this roadmap

The Declined list in [ROADMAP.md](ROADMAP.md) stands. Nothing new is added to it. Two things to *not* assume from this draft:

- **The dual-mass flywheel is not a clutch.** A part that disengages the crank from the network is a different feature with its own
  consequence (a stalling or slipping drivetrain) and would need the author's say against rule 3. It is offered here only as a
  possible later idea, and only if the transmission work asks for it.
- **One flywheel dial, and only one.** This draft said no value box; the author reversed that on 2026-11-23 and the Dual-Mass
  Flywheel has a spring-pack dial (see [DESIGN.md](DESIGN.md)). It is a single dial that moves the resonance and the secondary
  mass together, so it is a trade and not a free number. No second dial, no clutch.

## Open questions for the author

All questions are settled (2026-11-21): the four flywheel decisions (see 2.1), the order above, a 3.0 headline still to be
chosen (see 3.0), and the numbering rule (see "Direction").
