# Scripts and computers

Create: Engine Engineer can be watched from a ComputerCraft computer and from KubeJS server scripts. Both are **read-only**:
they look at an engine and tell you what it is doing. Neither can change an engine, and neither is needed to play. Without the
other mod nothing here loads. Both sit on the public API (`com.createengines.api`, frozen at 2.0, see API.md), so they report
the same thing the Display Link and the advancement checks do.

Changing engines rather than watching them is done with data and config ([PACKS.md](PACKS.md)) or, in Java, with the
extension point ([ADDONS.md](ADDONS.md)).

## CC: Tweaked

Wire a computer (or a wired modem, or put one next to it) to a **crankshaft, cylinder, cylinder head or rotor housing**. Each is a
peripheral of type `create_engines_engine`, and every method answers for the **whole engine** the block belongs to. A method
returns `nil` when the block is not part of a built engine or the engine has not reported yet.

| Method | Returns |
|---|---|
| `getEngine()` | a table of everything below |
| `getStatus()` | `stalled`, `cranking`, `coasting`, `running`, `overloaded`, `incomplete` or `broken` |
| `getRpm()` | crank speed |
| `getAvailableTorque()` | stress capacity per RPM at full fuel |
| `getLoadTorque()` | stress per RPM the network asks for |
| `getCylinders()` | working cylinders (or rotor chambers) |
| `getFiring()` | cylinders that fired in the last cycle |
| `getTemperature()` | 0 is ambient, 0.7 a warm engine on a steady load |
| `getOilCondition()` | 0 spent, 1 new |
| `getBoost()` | highest boost of any turbocharger or supercharger, 0 with none |
| `getHealth()` | `healthy`, `rough` or `failing` |
| `getCause()` | the one thing most responsible for the health, or `none` |

```lua
local engine = peripheral.find("create_engines_engine")
while true do
  local e = engine.getEngine()
  if e and e.health == "failing" then
    redstone.setOutput("top", true) -- ring a bell, cut the fuel from outside
  end
  sleep(1)
end
```

## KubeJS

Server scripts get two events and one global (KubeJS 7, Minecraft 1.21.1).

```js
CreateEnginesEvents.stateChanged(event => {
  if (event.stalled) console.log(`engine at ${event.pos} stalled`)
})

CreateEnginesEvents.milestone(event => {
  if (event.milestone == 'engine_running') console.log('first engine running')
})

// A read-only look at the engine a block belongs to, or null
let view = CreateEngines.engineAt(level, block.pos)
if (view) console.log(view.status() + ' at ' + view.rpm() + ' rpm')
```

`stateChanged` has `level`, `pos` (the engine's primary crankshaft), `from` and `to` (lower case state names), `started` and
`stalled`. `milestone` has `level`, `pos` and `milestone`; a script should ignore a milestone name it does not know, since new
ones may be added. Neither event can be cancelled. A script that throws is logged and never stops an engine's tick.

## Jade

With [Jade](https://modrinth.com/mod/jade) installed, looking at a **crankshaft, cylinder, cylinder head or rotor housing** shows
the engine's state on the overlay: running, stalled and so on, the speed, the boost when there is any, and the health when it is
rough or failing. It says what the goggles say, in the same words, and works for anyone looking, with or without goggles. The
engine is read through the same read-only API as the peripheral and the KubeJS global. Jade's settings screen lists it as
"Engine state" under Create: Engine Engineer, where it can be switched off. Jade finds the plugin itself; nothing here loads
without it. The overlay is Jade's, so WTHIT and The One Probe are not covered: they have their own plugin systems, not Jade's.

## Energy: other mods' machines

The **Alternator** already makes Forge Energy (FE): it fills a small buffer from its shaft, offers it on every face and pushes it
into any neighbour that takes it. The shaft only feels stress while energy is really being drawn. That is the whole integration:
any mod's energy block, cable or machine that offers the NeoForge energy capability takes it, with no code of ours. The rate is
the `alternatorEnergy` setting (FE per RPM per tick). Nothing goes the other way: no other mod can feed energy into an engine
or the Alternator. See "Checked how" for which mods were actually tried.

## Sound and shader mods

Iris and Sodium, Sound Physics Remastered and AmbientSounds need no integration code. They are rows in the compatibility matrix
(COMPAT.md) so that the engine models, particles and sounds are known to start and draw with each loaded.

Of the energy mods, **Create Crafts & Additions 1.6.0** is tested (its Modular Accumulator and Electric Motor are charged by an
alternator in a GameTest). Mekanism's energy cube and Create: New Age were tried and are not claimed: see COMPAT.md. Flux Networks
could not be tried. Other mods that offer Forge Energy should work the same way but are untested.

## Checked how

Each mod has a compatibility-matrix row (`runSmokeAuditKubejs`, `runSmokeAuditCc`, see COMPAT.md): the audit runs with the mod
loaded and fails unless the other mod found this one's plugin or peripheral. For KubeJS the audit posts the public events and
requires a script listener to receive them; for CC: Tweaked it asks the world for the peripheral on a running engine's
crankshaft and reads the engine through it. What has **not** been checked is a real script or a real computer program run by a
player: the rows prove the wiring, not the wording of the examples above.
