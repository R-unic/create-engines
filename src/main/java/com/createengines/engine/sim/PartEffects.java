package com.createengines.engine.sim;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * What each precision part does to a cylinder, as data. A cylinder is the crude one with the effects of whichever parts it
 * was built with multiplied in, so "precision" is not a single tier but four small entries (bore, head, piston, injector),
 * and a pack changes one of them, or an addon adds a part, by supplying a data file
 * ({@code data/create_engines/part_effects/<part>.json}, see {@link #parse}). The shipped files hold the built-in values,
 * and a test pins that they equal {@link #BUILT_IN}, so a world without a pack behaves exactly as before.
 * <p>
 * Plain Java: the game side reads the files and calls {@link #set}. Every effect is a scale (1 changes nothing) and is
 * blended by the server's precision bonus the way the built-in ones are.
 */
public final class PartEffects {

	/** The parts a cylinder can be built with. The names are the data file names. */
	public enum Part {
		BORE, HEAD, PISTON, INJECTOR;

		public String fileName() {
			return name().toLowerCase(Locale.ROOT);
		}
	}

	/**
	 * Scales a part puts on a cylinder's stats.
	 *
	 * @param breathing scale on both the intake and the exhaust valve flow
	 */
	public record Effect(double compression, double breathing, double ignitionDelay, double burn, double frictionStatic,
						 double frictionViscous, double inertia, double injectorFlow) {

		public static final Effect NONE = new Effect(1, 1, 1, 1, 1, 1, 1, 1);

		/** Smallest and largest scale an effect may carry, as for {@link PartStats}. */
		public static final double MIN = PartStats.MIN_SCALE;
		public static final double MAX = PartStats.MAX_SCALE;

		public Effect {
			for (double scale : new double[] { compression, breathing, ignitionDelay, burn, frictionStatic, frictionViscous,
				inertia, injectorFlow })
				if (!(scale >= MIN && scale <= MAX))
					throw new IllegalArgumentException("a part effect is a scale between " + MIN + " and " + MAX + ", not " + scale);
		}
	}

	/** The built-in parts, which are the {@code PRECISION_*} figures of {@link CylinderSpec}. */
	public static final Map<Part, Effect> BUILT_IN = builtIn();

	private static Map<Part, Effect> builtIn() {
		Map<Part, Effect> map = new EnumMap<>(Part.class);
		map.put(Part.BORE, new Effect(CylinderSpec.PRECISION_COMPRESSION, 1, 1, 1, CylinderSpec.PRECISION_BORE_STATIC,
			CylinderSpec.PRECISION_BORE_VISCOUS, 1, 1));
		map.put(Part.HEAD, new Effect(1, CylinderSpec.PRECISION_BREATHING, CylinderSpec.PRECISION_HEAD_DELAY, 1, 1, 1, 1, 1));
		map.put(Part.PISTON, new Effect(1, 1, 1, 1, CylinderSpec.PRECISION_PISTON_STATIC,
			CylinderSpec.PRECISION_PISTON_VISCOUS, CylinderSpec.PRECISION_PISTON_INERTIA, 1));
		map.put(Part.INJECTOR, new Effect(1, 1, CylinderSpec.PRECISION_INJECTOR_DELAY, CylinderSpec.PRECISION_INJECTOR_BURN,
			1, 1, 1, CylinderSpec.PRECISION_INJECTOR_FLOW));
		return Map.copyOf(map);
	}

	private static volatile Map<Part, Effect> current = BUILT_IN;

	private PartEffects() {
	}

	/** What the part does now: a pack's file for it, or the built-in values. */
	public static Effect of(Part part) {
		return current.getOrDefault(part, BUILT_IN.get(part));
	}

	/** Replaces the effects of the parts named; any part left out goes back to its built-in values. */
	public static void set(Map<Part, Effect> effects) {
		Map<Part, Effect> merged = new EnumMap<>(Part.class);
		merged.putAll(BUILT_IN);
		merged.putAll(effects);
		current = Map.copyOf(merged);
	}

	/** Back to the built-in values. */
	public static void reset() {
		current = BUILT_IN;
	}

	/** The data file's keys, each an optional scale. */
	public static final Set<String> KEYS = Set.of("compression_ratio", "breathing", "ignition_delay", "burn_duration",
		"friction_static", "friction_viscous", "inertia", "injector_flow");

	/**
	 * Reads a part effect from the numbers in a data file. Every key is optional (left out means 1) and the range is
	 * checked. A key this version does not know is refused, so a typo is found instead of silently ignored.
	 *
	 * @throws IllegalArgumentException with a message that says what is wrong
	 */
	public static Effect parse(Map<String, Double> values) {
		for (String key : values.keySet())
			if (!KEYS.contains(key))
				throw new IllegalArgumentException("unknown key \"" + key + "\"; the keys are " + new java.util.TreeSet<>(KEYS));
		return new Effect(get(values, "compression_ratio"), get(values, "breathing"), get(values, "ignition_delay"),
			get(values, "burn_duration"), get(values, "friction_static"), get(values, "friction_viscous"),
			get(values, "inertia"), get(values, "injector_flow"));
	}

	private static double get(Map<String, Double> values, String key) {
		Double value = values.get(key);
		return value == null ? 1 : value;
	}
}
