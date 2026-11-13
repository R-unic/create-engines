package com.createengines.content.effects;

import org.jetbrains.annotations.Nullable;

import com.createengines.engine.EngineState;

/** Which one-shot sound an engine's change of state calls for, kept apart from the sound engine so it can be tested. */
public enum EngineCues {
	NONE, CATCH, STALL;

	public static EngineCues forChange(@Nullable EngineState from, EngineState to) {
		if (from == null || from == to)
			return NONE;
		boolean burning = to == EngineState.RUNNING || to == EngineState.OVERLOADED;
		if (burning && (from == EngineState.CRANKING || from == EngineState.STALLED))
			return CATCH;
		if (to == EngineState.STALLED && (from == EngineState.RUNNING || from == EngineState.OVERLOADED
			|| from == EngineState.COASTING))
			return STALL;
		return NONE;
	}
}
