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
- The Declined list in [ROADMAP.md](ROADMAP.md) ("Not on this roadmap") stands: no new failure modes or wear, no new fuels, no
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
| 2.2 | Proving Ground | Verification debts: goldens, soak, server-from-jar, the step-size finding | Medium |
| 2.3 | Open Garage | Reach: API 1.2, energy and probe mods, the next Create, a second example addon | Medium |
| 2.4 | Paint and Polish | Look, sound and performance, only where the 2.0.x pass found something | Small to medium |
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
- It costs more (a precision mechanism and brass for the spring pack; recipe to be priced with `RefiningBalanceTest`-style
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
3. **The recipe needs a precision mechanism**, so the part sits with the precision parts.
4. **Nothing about shifting changes.** It is only a flywheel.

### Around it (small, same release)

- **Flywheel readout for every flywheel.** The Cast Flywheel's goggles show only its mass today. It gains the ripple it removes
  and what an engine's roughness loss is, so choosing between the two parts is a reading, not a guess. Information only; no
  number moves.
- **Roughness on the Display Link.** A display source for an engine's smoothness (the share of the rating kept), next to fault
  and gear. New source, its own test and lang entry.

### Fuel character (proposed)

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

No behaviour change: this is verification work that the 2.0 record left open. Each item lands on its own and goes into whichever
release has room.

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
  display source. Each is a new name in `compat/api-surface.txt` and `compat/script-surface.txt` and a bump of `ApiVersion.MINOR`.
- **A second example addon** in `examples/`, this time of a part that is not a cylinder (a flywheel or a crank-line part), to prove
  the same route for a second kind of block.
- **Forge Energy input for engines** and **WTHIT / The One Probe**, both listed as "not done" in the 2.0 record. Each gets a row in
  [COMPAT.md](COMPAT.md) and the compatibility matrix, and is called supported only once its row is green.
- **The next Create.** Move the supported range when Create and Aeronautics allow (2.0.x settles whether they do now); each Create
  version in range is built and tested against (rule 7), and if two cannot sit side by side the release says so.
- **Pack tools**, as asked for: check [PACKS.md](PACKS.md) against what pack makers ask for in the feedback channel (the
  `triage-feedback` skill reads it) before choosing anything here. Nothing is built from a guess.

## 2.4 Paint and Polish

Held until 2.0.x has looked at what 2.0 added. Contents are whatever that pass finds, plus these, each only if a profile or an eye
asks for it:

- **Sound.** Retune what the 2.0.x listen finds (headphone filter numbers, methanol, kerosene, the turbo jet), and the voice cap
  and out-of-earshot pause from the optimization list. The 1.2.0 out-of-memory fix is still the standard to hold.
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
- **No second flywheel dial.** The part's behaviour comes from its mass split and the shipped curve, not from a value box.

## Open questions for the author

All questions are settled (2026-11-21): the four flywheel decisions (see 2.1), the order above, a 3.0 headline still to be
chosen (see 3.0), and the numbering rule (see "Direction").
