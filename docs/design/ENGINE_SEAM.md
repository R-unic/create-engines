# 2.0 design note: the engine-type seam

Written 2026-11-18 on branch `2.0`, after v1.4.0. Nothing here is built. This is the first platform piece
("Engine types as plug-ins", ROADMAP 2.0), and it is a design note because it needs the author's decisions before any
code. The refactor itself must leave `compat/behaviour.txt` and `compat/trajectories.txt` untouched.

## Decided 2026-11-18, and where it stands

**The author's ruling:** the framework is built so a non-cylinder engine (a turbine) is possible, but the mod ships no
turbine or other new engine type itself. Pack-makers and addons supply them; that is what the seam is for. So the
interface must carry a source that is not angle-driven, and the mod needs one test-only source to prove it, never a
registered one.

**Built (step 1, uncommitted):** `TorqueSource` (the seam) with `CylinderModel` implementing it, and
`EngineSimulation.addSource` / `sources()`. The sim's loops (step, end of tick, friction, oil wash, available torque,
sustaining, direction) now walk the sources; `cylinders()` is still only the cylinders. `TorqueSourceTest` drives the
crank with a constant-torque motor. The unit suite passed in a private copy, `compat/behaviour.txt` and
`compat/trajectories.txt` untouched. Not yet re-run: `./gradlew pitest` (the sim changed), the GameTests.
**Built (step 2, uncommitted):** the rotary chamber is its own drive. `CycleDrive` (package-private) has `PISTON` and
`RotaryDrive`; `CylinderModel` holds one in place of the `ported`, `angleScale` and `cycleOffset` fields, and the
`ported` tests in `pistonAngle`, `timed`, `cycleDegrees`, `isTimed` and `advance` are gone. `Combustion` still asks
`owner.ported()` (no ignition delay for a rotor), now answered by the drive. A rotary chamber is still a `CylinderModel`,
not a separate class: its gas, ports and burning are shared with the piston, so a full class split would copy them.
`CycleDriveTest` is new. Private copy: 1103 of 1104 unit tests pass (the one failure is another session's uncommitted
`GoggleOverlayMixin` importing client code, `ArchitectureRulesTest`), and all 313 GameTests pass. `compat/behaviour.txt`
and `compat/trajectories.txt` untouched. `pitest` not re-run.

**Built (step 4, registration, uncommitted until the commit that follows this line):** `api.EngineExtensions.register` /
`unregister` take an `api.EngineSourceProvider`, which `EngineController` asks whenever an engine is built and which
returns `TorqueSource`s for `sim.addSource`. `EngineExtensions.rebuild(level, pos)` rebuilds the engine at a block (keeping
motion, oil and heat) so a pack's own block can change what it supplies. A provider that throws or returns nulls is
reported (logged as an error, or to `reportFailuresTo` for a test) and skipped; the engine still builds. Preview API,
frozen at 2.0. Three GameTests in `PackToolsGameTests`: a source is on the crank and stepped, a failing provider is
skipped and reported, `rebuild` asks again. All 316 GameTests pass in a private copy. **Limits, by design for now:** an
added block is not a scanner member, so it does not trigger a rescan by itself (the pack calls `rebuild`), is not in
blueprints, and has no goggle lines; added sources get no boost (Induction reads cylinders only).

**Built (step 5, boost, goggle lines, rescan; committed with this line):** the limits above are closed. (1) *Boost:*
`TorqueSource.setIntake(pressure, temperature)` is called once a tick with the engine's air as a whole: the average of what
its cylinders draw, or, with no cylinders, what its blowers make in series (a turbo has no cylinder exhaust to spool it;
ports, parallel and intercooler settings stay a cylinder matter). Server boost strength and the forced-induction switch
apply. (2) *Goggles:* `TorqueSource.status()` returns `StatusLine`s (translation key from the pack's own lang file, up to 4
text args, a tone), capped at 8 across the engine and 96 characters each; they ride in `EngineSummary.sourceLines`, are
synced with it, and are drawn on any crankshaft block; an engine whose only drive is an added source is no longer
reported as having no cylinders. A source that throws from `status()` is reported like a failed provider and left out.
(3) *Rescan:* `EngineSourceProvider.watches(state)` and `reach()` (default: watch nothing, 2 blocks); placing or
breaking a watched block within reach of a crank segment rebuilds that engine at the end of the tick (`EngineTopology`
queues it, since the block is still there during a break event). A plain rescan would not do, because an engine whose
layout came back unchanged keeps its controller. Still not true: added blocks are not scanner members (so not in
blueprints), and a turbo or blower cannot be paired to an added source's own ports. Tests: `TorqueSourceTest`,
`StatusLineTest`, and two GameTests in `PackToolsGameTests`. Private copy: 318 GameTests pass, the log verifier is clean, pitest
170 live (the limit, unchanged), unit suite passes but for the other session's `ArchitectureRulesTest` failure.

**Built (step 3 and the rest of step 5, 2026-11-19, author's decision):** (1) *Narrowing.* `Breather extends TorqueSource` (intake and
exhaust pressure and intake temperature, set by the engine) is what `Induction`, `Turbocharger`, `Supercharger` and `Intercooler`
work on, in place of `CylinderModel`; `CylinderModel` implements it. 19 main files still name `CylinderModel`, because they are cylinder
internals (`Charge`, `Combustion`, `Ports`, `Soiling`, the block entities, the renderer); nothing on the boost path does. (2) *Pairing.*
`EngineSourceProvider.intakeAt` and `exhaustAt` put a `Breather` source on a port network (`EngineLayout.networkAt`, from the new
`portCells` map the scanner keeps), so turbos, blowers and intercoolers on it act on the source, and
`EngineSimulation.addAttachedSource` keeps it off the engine-wide average. (3) *Members.* A block a provider watches within reach of
the crankshaft is recorded in blueprints and accepted by the server's blueprint policy while that provider is registered
(`EngineProviders.claimedBlocks`, `claims`). The scanner itself is unchanged: no scanner-rule registry was built, because the
watch-and-rebuild hook already rescans an engine when such a block changes. Tests: `BreatherTest`, GameTests
`aSourceAttachedToAnIntakeNetworkBreathesTheBlowersBoost` and `aBlockAProviderClaimsIsRecordedInTheBlueprintAndPassesThePolicy` (each
failed when its wiring was removed). Not done: a source cannot be a turbo's own port block, and the dynamometer copy ignores added sources
(as before).

## What exists today (checked in the code)

- `EngineSimulation` (587 lines) takes a `List<CylinderModel>` and loops over it in four places that matter:
  `advance` (steps each cylinder and sums gas work), `endTick` (exhaust heat, coldness, induction), the oil wash and
  friction measuring, and the reset and restore methods.
- A rotary chamber is a `CylinderModel` built by `CylinderModel.rotary(...)`. The sim tells it apart with
  `cylinder.ported()` and gives it a second angle (`rotaryAngle`, 0 to 1080) and an `angleScale`.
  `Combustion` also branches on `owner.ported()`. So the "seam" today is an `if` on a flag.
- `CylinderModel` is `final` and is named in 19 files, 4 of which are game side (`EngineController`, the cylinder and
  rotor block entities, `EnginePoses`). `EngineController` builds the models, wires turbines and compressors, and
  keeps a parallel `List<@Nullable CylinderModel> models` indexed like the block entities.
- Induction (`Induction`, `Turbocharger`, `Supercharger`, `Intercooler`) and `Thermal` read the cylinder list directly
  (intake pressure and temperature written onto each model).

## What an addon would need to supply

1. **A torque source:** something that, given the crank angle, step and `dt`, returns gas work, and exposes the few
   things the rest of the engine reads (inertia, fuel burned this step, exhaust heat, intake pressure target, a fault
   list for the goggles).
2. **A scanner rule** that recognises its blocks and hands the controller a descriptor.
3. **Goggle lines and a status** for its parts.
4. **Its stats**, as data (the "Parts as data" item).

## Proposed shape (for the author to accept, change or reject)

An interface `TorqueSource` (sketch, names not final):

```java
public interface TorqueSource {
	CylinderSpec spec();                 // inertia, friction, displacement (generalised to `Spec` with parts as data)
	double step(CrankState crank, double delta, double dt);   // returns gas work
	void endTick();
	double exhaustHeatLastTick();
	void applyInduction(Intake air);     // pressure, temperature, coldness
	StatusLines status();
}
```

- `CylinderModel` becomes one implementation (`PistonSource`), and the rotary chamber a second (`RotarySource`), which
  removes the `ported()` branches from `EngineSimulation` and `Combustion`. This is the behaviour-preserving first
  commit and is checkable against the existing guards.
- `EngineSimulation` holds `List<TorqueSource>`. Code that needs cylinder-only things (camshaft sync, plug fouling,
  oil wash) asks the source through a narrow capability interface (`Timed`, `Foulable`, `OilWasher`) instead of casting.
- `EngineController`'s `models` list becomes a list of sources. Turbines and compressors stay a cylinder-only feature
  until a decision below says otherwise.
- The part stats data map (preview in 1.4) becomes the first reader of the same `Spec` that an addon's source uses.

## Order of work (each step green on the existing guards before the next)

1. Extract `TorqueSource` with `CylinderModel` as the only implementer. No behaviour change.
2. Split the rotary chamber out as its own source; delete the `ported()` branches.
3. Narrow the 19 files that name `CylinderModel` to the interface where they do not need cylinder detail.
4. A test-only second source (a fixed-torque "turbine") to prove the seam carries something that is not a cylinder.
5. Registry for sources and scanner rules, then the public API surface and its semver.

Steps 1 to 3 are refactors, covered by `compat/behaviour.txt`, `compat/trajectories.txt`, `PerfBudgetTest` (the gas
split already cost 2 to 4 percent, so this must not add more) and the mutation ratchet (re-run `pitest` after touching
`engine/sim`).

## Decisions needed from the author

1. **Is a non-cylinder engine (a turbine) really in scope for 2.0,** or is the seam only for new cylinder-like parts
   (a different rotary, a two-stroke)? A turbine has no piston angle, so `step` cannot be angle-driven and the crank
   loop changes. This is the largest cost driver.
2. **Do turbines and compressors (turbo and supercharger pairing) stay cylinder-only?** Letting a source be a turbine
   changes `Induction`.
3. **How far does an addon reach into the goggles and Ponder?** Goggle lines through a hook is cheap; Ponder is the
   addon's own.
4. **Does 2.0 freeze the `api` package at the same time,** or does the seam stay preview for one more release?
5. **Branch policy:** keep 2.0 on this branch until the breaking items are all decided, with 1.x fixes cherry-picked
   across, or rebase it on master regularly.

## Not decided here

Parts as data (stats loaded from a registry instead of `CylinderSpec` constants) shares the `Spec` type with this
seam and should be designed with it, but its file format and the migration of the 1.4 part stats data map are a
separate note.
