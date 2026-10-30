package com.createengines.engine;

import java.util.Locale;

public enum EngineState {
	STALLED,
	CRANKING,
	COASTING,
	RUNNING,
	OVERLOADED,
	INCOMPLETE,
	/** A plain shaft splits the crankshaft, so there is no single engine to run. */
	BROKEN;

	public String langKey() {
		return "goggles.state." + name().toLowerCase(Locale.ROOT);
	}
}
