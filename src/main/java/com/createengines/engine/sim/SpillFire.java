package com.createengines.engine.sim;

/**
 * How a spill of fuel lying in the world catches and burns, by the fuel's {@link FuelSpec#flammability()}. Pure arithmetic with no
 * game types, so it is unit tested. Only fuel that has escaped (placed in the world as a fluid) is ever asked: a running or intact
 * engine's tanks do not go through here, and the {@code compat.fuelFire} and {@code compat.fuelFireByVolatility} settings turn it
 * off. Flammability 1 is exactly how every fuel burned before the figure existed.
 */
public final class SpillFire {

	/** Ticks between one block of a pool catching and the next, for flammability 1. */
	public static final int BASE_DELAY = 3;
	/** The fastest and slowest a pool spreads, whatever a pack writes for a flammability. */
	public static final int MIN_DELAY = 1;
	public static final int MAX_DELAY = 20;

	private SpillFire() {
	}

	/** A flammability the arithmetic can use: not a number, zero or negative counts as 1 (an ordinary fuel). */
	public static double usable(double flammability) {
		return Double.isNaN(flammability) || flammability <= 0 ? 1 : Math.min(flammability, 1000);
	}

	/** Ticks before a block that touched a flame, or a burning neighbour, tries to catch: shorter for a more flammable fuel. */
	public static int delay(double flammability) {
		return (int) Math.clamp(Math.round(BASE_DELAY / usable(flammability)), MIN_DELAY, MAX_DELAY);
	}

	/** The chance each try catches: 1 for a fuel that is ordinary or more flammable, less for a hard-to-light one. */
	public static double catchChance(double flammability) {
		return Math.min(1, usable(flammability));
	}
}
