package com.createengines.engine.sim;

/** Where an engine's oil comes from. Implemented by the oil tanks of its cylinder heads. */
public interface OilSupply {

	OilSupply NONE = mb -> 0;

	/** Removes up to {@code mb} of oil and returns how much was actually delivered. */
	double draw(double mb);
}
