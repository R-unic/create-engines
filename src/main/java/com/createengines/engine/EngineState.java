package com.createengines.engine;

import java.util.Locale;

public enum EngineState {
	STALLED,
	CRANKING,
	COASTING,
	RUNNING,
	OVERLOADED,
	INCOMPLETE;

	public String langKey() {
		return "goggles.state." + name().toLowerCase(Locale.ROOT);
	}
}
