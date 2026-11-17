package com.createengines.api;

/**
 * What an engine is doing, as the public API names it. <b>Preview:</b> the API may still change until 2.0, where it is
 * frozen under its own version. The names match the engine's own states one for one (a unit test holds that).
 */
public enum EngineStatus {
	STALLED,
	CRANKING,
	COASTING,
	RUNNING,
	OVERLOADED,
	/** Part of the engine is in an unloaded chunk, so it is not run. */
	INCOMPLETE,
	/** A plain shaft splits the crankshaft, so there is no single engine to run. */
	BROKEN
}
