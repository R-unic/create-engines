package com.createengines.api;

/**
 * A read-only look at one engine. <b>Preview:</b> the API may still change until 2.0.
 *
 * @param status          what the engine is doing
 * @param rpm             crank speed
 * @param availableTorque stress capacity per RPM the engine could deliver at full fuel
 * @param loadTorque      stress per RPM the network is asking for
 * @param cylinders       working cylinders (or rotor chambers)
 * @param firing          how many fired in the last cycle
 * @param temperature     0 ambient, 0.7 a warm engine on a steady load
 * @param oilCondition    0 spent, 1 new
 * @param boost           the highest boost of any turbocharger or supercharger on it, 0 with none
 * @param health          the engine's overall condition: {@code healthy}, {@code rough} or {@code failing}
 * @param cause           the one thing most responsible for it, a lower case name, or {@code none}
 */
public record EngineView(EngineStatus status, float rpm, float availableTorque, float loadTorque, int cylinders, int firing,
						 float temperature, float oilCondition, float boost, String health, String cause) {
}
