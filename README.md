# Create: Engine Engineer

An addon for [Create](https://github.com/Creators-of-Create/Create) that lets you build working internal combustion
engines out of individual parts and turn them into Create rotational force. Start with a crude single cylinder, finish
with a boosted, precision-built V12.

Minecraft 1.21.1 · NeoForge 21.1.219 · Create 6.0.10

> [!WARNING]
> **This mod is vibe coded.** Nearly all of the code, models, Ponder scenes and docs were written by an AI
> (Claude) while directed by a human. It has tests and in-game smoke tests, but expect rough edges, odd balance and
> bugs. Treat it as an experiment, not a polished release, and don't be surprised if worlds or save data break between
> versions.

## Preview

![A coupled two-crankshaft engine](docs/images/engine_coupled_overview.png)

| | |
|---|---|
| ![Engine with management sensors](docs/images/engine_management_overview.png) | ![Engine built from precision parts](docs/images/engine_precision_overview.png) |
| Intake and exhaust management | Precision parts |
| ![Flywheel, dynamometer and turbos](docs/images/engine_new_parts.png) | ![Goggle readout on a firing cylinder](docs/images/engine_goggles_cylinder.png) |
| Flywheel, dynamometer and turbos | Diagnostics with Engineer's Goggles |

## Features

**Build an engine**
- Crankshafts, throws, cylinders, heads, pistons, rods, camshafts and valves, assembled block by block; cylinders fire
  in order and the crank animates with them
- Inline, V, flat and rotary (Wankel) layouts, with coupled crankshafts for bigger engines
- A **Bank Builder** that lays out a whole inline, V or flat bank in one click, and a **Timing Tool** that sets the
  firing order for you

**Feed and tune it**
- Air, fuel and exhaust plumbing with pipes, throttle bodies and fuel metering; diesel, plant oil and gasoline with
  spark ignition
- Oil film and lubrication, sumps, starters, batteries and alternators
- A forgiving early game: spark plug soot clears itself, goggles suggest fuel settings, and the Timing Tool can tune
  every injector in one click
- Governors, rev limiters, MAF and lambda sensors for the late game

**Make it powerful**
- Turbochargers (stacked, twin, variable-geometry, with anti-lag), superchargers and intercoolers
- Precision cylinders, heads, pistons and injectors
- Flywheels that matter: a rough engine is rated for less, and rotating mass smooths it out
- A transmission, and a **Dynamometer** that draws an engine's torque and power curves

**Learn it**
- Engineer's Goggles diagnostics on every part, Ponder scenes for every part, and advancements that reward actually
  using what you build

| | |
|---|---|
| ![Bank Builder Ponder scene](docs/images/engine_ponder_bank_builder_800.png) | ![Firing order Ponder scene](docs/images/engine_ponder_firing_order_960.png) |
| ![Rotary engine Ponder scene](docs/images/engine_ponder_rotor_560.png) | ![Vanes and anti-lag Ponder scene](docs/images/engine_ponder_turbo_tuning_650.png) |

## Recommended: raise Create's RPM limit

Create caps rotation at 256 RPM by default, and engines hit that ceiling. We recommend raising it: in
`serverconfig/create-server.toml` (or the world's `serverconfig` folder) under `[kinetics]`, set `maxRotationSpeed` to
something like 1024 or higher. The engine simulation is built to stay cheap at speed, and its torque rating follows the
limit you set. Everything else on the kinetic network still obeys Create's own rules.

## Building

```bash
./gradlew build
```

Other useful tasks: `./gradlew test` (simulation unit tests), `./gradlew runGameTestServer` (in-world GameTests) and
`./gradlew runVisualSmokeTest` (builds an engine and saves screenshots to `run/screenshots/`).

Design notes and how the simulation works are in [docs/DESIGN.md](docs/DESIGN.md).

## License

All Rights Reserved.
