package com.createengines.engine.topology;

/** A block entity that shows information about the engine it belongs to. */
public interface EngineMember {

	/** The block is no longer part of any engine: forget what it was showing. */
	void detachFromEngine();
}
