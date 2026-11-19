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

## Checked how

Each mod has a compatibility-matrix row (`runSmokeAuditKubejs`, `runSmokeAuditCc`, see COMPAT.md): the audit runs with the mod
loaded and fails unless the other mod found this one's plugin or peripheral. For KubeJS the audit posts the public events and
requires a script listener to receive them; for CC: Tweaked it asks the world for the peripheral on a running engine's
crankshaft and reads the engine through it. What has **not** been checked is a real script or a real computer program run by a
player: the rows prove the wiring, not the wording of the examples above.
