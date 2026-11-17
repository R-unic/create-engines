package com.createengines.engine.sim;

import org.jetbrains.annotations.Nullable;

/**
 * What a cylinder's cycles leave behind: soot on the spark plug, and soot in the exhaust for a soot trap to collect.
 * Moved out of {@link CylinderModel}, which hands this each finished cycle.
 */
final class Soiling {

	/** Soot a plug gains per cycle that burns rich, times the share of that cycle's fuel the air couldn't burn. */
	private static final double FOULING_RATE = 0.12;
	/** Soot a plug loses each cycle it fires on a mixture that burns clean. */
	private static final double FOULING_BURN_OFF = 0.12;
	/**
	 * Soot a plug sparks off each cycle the charge was too rich to light. Soot only builds from a rich burn, and a plug
	 * that has stopped lighting is hot and dry again a few cycles later, so rich running makes an engine stutter instead
	 * of locking it out until the mixture is fixed by hand.
	 */
	private static final double FOULING_SCRUB = 0.04;
	/**
	 * How far a completely fouled plug pulls the rich limit in. It can still light a mixture a little richer than the
	 * balanced band, so a beginner's flooded engine coughs rather than refusing to fire.
	 */
	private static final double FOULED_RICH_LOSS = CylinderModel.RICH_LIMIT - 1.4;
	/** Most soot a model holds before it is drained, mB. */
	static final double SOOT_CAP = 1000;

	/** Soot on the spark plug, 0 (clean) to 1: rich running builds it, a mixture that burns clean cleans it off. */
	private double fouling;
	/**
	 * Fuel, in mB, that left the exhaust as soot since a soot trap last asked: what rich cycles failed to burn, scaled by
	 * how sooty the fuel is. Whoever owns the model drains it with {@link #takeSoot()}; it is capped so that nobody
	 * listening never lets it grow without limit.
	 */
	private double sootPending;

	/** Soot on the spark plug from 0 to 1. */
	double fouling() {
		return fouling;
	}

	/** The richest mixture the plug can still light: a fouled plug cannot light what a clean one could. */
	double richLimit() {
		return CylinderModel.RICH_LIMIT - FOULED_RICH_LOSS * fouling;
	}

	/** Soot the exhaust carried out since the last call, in mB of unburned fuel; empties the count. */
	double takeSoot() {
		double soot = sootPending;
		sootPending = 0;
		return soot;
	}

	void copyFrom(Soiling other) {
		fouling = other.fouling;
		sootPending = other.sootPending;
	}

	/**
	 * Takes in a finished cycle.
	 *
	 * @param current     the cycle's figures, before they are filed as the last cycle
	 * @param sparkPlug   whether the cylinder has a spark plug to foul
	 * @param burningFuel the fuel the cycle burned, null if it burned none
	 */
	void endCycle(CycleStats current, boolean sparkPlug, @Nullable FuelSpec burningFuel) {
		if (current.result.fired() && current.fuelInjected > 0) {
			double unburnedShare = current.unburnedFraction();
			if (unburnedShare > Mixture.RICH_UNBURNED)
				sootPending = Math.min(SOOT_CAP, sootPending + SootCollector.sootFrom(current.fuelInjected,
					unburnedShare, burningFuel == null ? 1 : burningFuel.sootFactor()));
		}

		if (sparkPlug && current.fuelInjected > 0) {
			double unburned = current.unburnedFraction();
			if (!Tuning.sparkFouling)
				fouling = 0;
			else if (current.fuelBurned <= 0) {
				if (current.sparks > 0)
					fouling = Math.max(0, fouling - FOULING_SCRUB);
			} else if (unburned > Mixture.RICH_UNBURNED)
				fouling = Math.min(1, fouling + FOULING_RATE * unburned * (burningFuel == null ? 1 : burningFuel.sootFactor()));
			else
				fouling = Math.max(0, fouling - FOULING_BURN_OFF);
		}
	}
}
