package com.createengines.content.effects;

import org.jetbrains.annotations.Nullable;

import com.createengines.engine.Diagnosis;
import com.createengines.engine.EngineState;

/** Which one-shot sound an engine's change of state calls for, kept apart from the sound engine so it can be tested. */
public enum EngineCues {
	NONE, CATCH, STALL,
	/** The engine died because water got in: a gulp, a burst of steam and a hard clunk. */
	FLOOD,
	/** The engine died because the load beat it: one last belch of smoke. */
	OVERLOAD;

	/** As {@link #forChange(EngineState, EngineState)}, but a stall is told apart by what the engine says killed it. */
	public static EngineCues forChange(@Nullable EngineState from, EngineState to, Diagnosis.Cause cause) {
		EngineCues cue = forChange(from, to);
		if (cue != STALL)
			return cue;
		if (cause == Diagnosis.Cause.WATER)
			return FLOOD;
		return from == EngineState.OVERLOADED ? OVERLOAD : STALL;
	}

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
