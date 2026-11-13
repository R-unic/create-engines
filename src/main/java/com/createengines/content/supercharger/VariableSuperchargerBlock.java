package com.createengines.content.supercharger;

/**
 * A supercharger with a variable-ratio drive and rotors cut to a close clearance: it overspeeds its rotors while the
 * shaft is slow, so it makes boost sooner, and it takes less power to turn and heats the air less (see
 * {@link com.createengines.engine.sim.Supercharger}). Otherwise it is a supercharger in every respect.
 */
public class VariableSuperchargerBlock extends SuperchargerBlock {

	public VariableSuperchargerBlock(Properties properties) {
		super(properties);
	}

	@Override
	public boolean variableDrive() {
		return true;
	}
}
