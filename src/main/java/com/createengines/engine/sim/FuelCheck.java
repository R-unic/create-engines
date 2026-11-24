package com.createengines.engine.sim;

import java.util.ArrayList;
import java.util.List;

/**
 * Looks over a fuel a pack supplied (a {@code fuel} data map entry) for numbers that cannot be right. The data map's format
 * only asks for numbers, and a typo such as an air figure of 0 or a negative energy would run an engine on nonsense instead
 * of failing, so {@code /ce fuels} and the server's start-up log report what is wrong with a fuel by the key a pack maker
 * wrote. This reads a fuel and changes nothing: a fuel that fails is still used as written.
 * <p>
 * Plain Java, so it is tested without a game.
 */
public final class FuelCheck {

	/** The most and least a fuel's energy per mB is expected to be; the shipped fuels run from 22 to 40. */
	public static final double UNUSUAL_ENERGY_LOW = 5;
	public static final double UNUSUAL_ENERGY_HIGH = 200;
	/** The same for the fresh charge a mB burns; the shipped fuels run from 1.0 to 1.7. */
	public static final double UNUSUAL_AIR_LOW = 0.2;
	public static final double UNUSUAL_AIR_HIGH = 10;

	private FuelCheck() {
	}

	/** What is wrong enough that the fuel will misbehave: a message for each, naming the data map key. Empty for a sound fuel. */
	public static List<String> problems(FuelSpec fuel) {
		List<String> out = new ArrayList<>();
		above(out, "energy_per_mb", fuel.energyPerMb(), "it is the energy a mB releases, and an engine cannot run on none");
		above(out, "autoignition_temperature", fuel.autoignitionTemp(), "it is a charge temperature, ambient = 1");
		above(out, "air_per_mb", fuel.airPerMb(), "the injectors are sized by dividing by it");
		notBelow(out, "cold_sensitivity", fuel.coldSensitivity());
		notBelow(out, "charge_cooling", fuel.chargeCooling());
		notBelow(out, "soot_factor", fuel.sootFactor());
		above(out, "preignition_temperature", fuel.preignitionTemp(), "it is a charge temperature, ambient = 1");
		notBelow(out, "sulfur", fuel.sulfur());
		above(out, "flammability", fuel.flammability(), "a spill's lighting and burn-off speed are divided by it");
		return out;
	}

	/** Numbers that are allowed but far from every shipped fuel, which is usually a slip of a decimal place. Empty for most fuels. */
	public static List<String> unusual(FuelSpec fuel) {
		List<String> out = new ArrayList<>();
		if (fuel.energyPerMb() > 0 && (fuel.energyPerMb() < UNUSUAL_ENERGY_LOW || fuel.energyPerMb() > UNUSUAL_ENERGY_HIGH))
			out.add("energy_per_mb " + fuel.energyPerMb() + " is far from the shipped fuels (22 to 40)");
		if (fuel.airPerMb() > 0 && (fuel.airPerMb() < UNUSUAL_AIR_LOW || fuel.airPerMb() > UNUSUAL_AIR_HIGH))
			out.add("air_per_mb " + fuel.airPerMb() + " is far from the shipped fuels (1.0 to 1.7)");
		return out;
	}

	private static void above(List<String> out, String key, double value, String why) {
		// A comparison with NaN is false, so this also catches a value that is not a number
		if (!(value > 0) || Double.isInfinite(value))
			out.add(key + " must be a number above 0, not " + value + " (" + why + ")");
	}

	private static void notBelow(List<String> out, String key, double value) {
		if (!(value >= 0) || Double.isInfinite(value))
			out.add(key + " must be a number that is not below 0, not " + value);
	}
}
