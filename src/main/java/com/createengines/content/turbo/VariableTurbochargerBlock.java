package com.createengines.content.turbo;

/**
 * A turbocharger with a ring of variable vanes in front of its turbine: it spools sooner than a fixed one and leaves the
 * exhaust a freer path at full flow (see {@link com.createengines.engine.sim.Turbocharger}). Otherwise it is a
 * turbocharger in every respect.
 */
public class VariableTurbochargerBlock extends TurbochargerBlock {

	public VariableTurbochargerBlock(Properties properties) {
		super(properties);
	}

	@Override
	public boolean variableGeometry() {
		return true;
	}
}
