package com.createengines.api;

/**
 * What an engine is doing, as the public API names it. <b>Frozen</b> at API 1.0 (see {@link ApiVersion}): nothing here is removed or changed before the next major version, and additions are minor versions. under its own version. The names match the engine's own states one for one (a unit test holds that).
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
