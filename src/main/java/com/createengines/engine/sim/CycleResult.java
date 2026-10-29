package com.createengines.engine.sim;

/** What happened in one cylinder's last complete four-stroke cycle. Derived from the simulation, never assigned. */
public enum CycleResult {
	/** The cylinder has not completed a cycle yet. */
	NONE,
	/** Fuel ignited and burned. */
	FIRED,
	/** Nothing was injected: empty injector, fuel cut off, or no injector. */
	NO_FUEL,
	/** Fuel was injected but the charge never got hot enough to ignite it. */
	NO_IGNITION,
	/** Fuel was injected into a cylinder with no fresh air in it. */
	NO_AIR;

	public boolean fired() {
		return this == FIRED;
	}
}
