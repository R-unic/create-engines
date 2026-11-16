package com.createengines.content.muffler;

/**
 * A muffler: a baffled canister in an exhaust run that takes the bark out of the engine. It quiets the exhaust note,
 * the bangs and the pops of the engine whose exhaust goes through it, and does nothing else: it costs the engine no
 * power and no back-pressure.
 * <p>
 * An engine is as quiet as its exhaust runs are muffled (see {@link com.createengines.engine.sim.Muffling}): a muffler
 * every path goes through quiets all of it, and one the exhaust can go round only half.
 */
public class MufflerBlock extends ExhaustCanisterBlock {

	public MufflerBlock(Properties properties) {
		super(properties);
	}
}
