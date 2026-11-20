package com.createengines.engine.sim;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * The two cam profiles an engine runs on, as data: the piston engine's valve lobes and the rotary's ports. They are the
 * {@link CamProfile#BASIC} and {@link CamProfile#ROTARY} constants unless a data pack supplies
 * {@code data/create_engines/cam_profiles/basic.json} or {@code rotary.json}, which the loader hands to {@link #set}. The shipped
 * files hold the built-in angles and a test pins that they equal the constants, so a world with no pack behaves exactly as before.
 * <p>
 * Plain Java, as {@link PartEffects} is. The profile times the simulation only: the valves drawn on a client keep the built-in
 * shape, since a client does not read server data packs.
 */
public final class CamProfiles {

	/** The profiles that can be replaced. The names are the data file names. */
	public enum Kind {
		BASIC(CamProfile.BASIC), ROTARY(CamProfile.ROTARY);

		private final CamProfile builtIn;

		Kind(CamProfile builtIn) {
			this.builtIn = builtIn;
		}

		public String fileName() {
			return name().toLowerCase(Locale.ROOT);
		}

		public CamProfile builtIn() {
			return builtIn;
		}
	}

	/** The data file's keys, each an optional angle in cycle degrees; a key left out keeps the built-in angle. */
	public static final Set<String> KEYS = Set.of("intake_open", "intake_close", "exhaust_open", "exhaust_close", "injection",
		"spark");

	/** Shortest and longest a valve (or port) may stay open, in cycle degrees: a profile outside this has no sound lift curve. */
	public static final double MIN_DURATION = 40;
	public static final double MAX_DURATION = 400;

	private static volatile Map<Kind, CamProfile> current = builtIns();

	private CamProfiles() {
	}

	private static Map<Kind, CamProfile> builtIns() {
		Map<Kind, CamProfile> map = new EnumMap<>(Kind.class);
		for (Kind kind : Kind.values())
			map.put(kind, kind.builtIn());
		return Map.copyOf(map);
	}

	/** The profile a piston cylinder's lobe is cut to now. */
	public static CamProfile basic() {
		return current.get(Kind.BASIC);
	}

	/** The profile a rotor's ports are cut to now. */
	public static CamProfile rotary() {
		return current.get(Kind.ROTARY);
	}

	/** The profile for a kind. */
	public static CamProfile of(Kind kind) {
		return current.get(kind);
	}

	/** Replaces the profiles named; any left out goes back to its built-in angles. */
	public static void set(Map<Kind, CamProfile> profiles) {
		Map<Kind, CamProfile> merged = new EnumMap<>(Kind.class);
		merged.putAll(builtIns());
		merged.putAll(profiles);
		current = Map.copyOf(merged);
	}

	/** Back to the built-in profiles. */
	public static void reset() {
		current = builtIns();
	}

	/**
	 * Reads a profile from the angles in a data file, each optional (left out keeps the angle of the built-in profile of that
	 * kind). A key this version does not know is refused, so a typo is found and not ignored; every angle must be in 0 up to
	 * 720, and each valve open for between {@link #MIN_DURATION} and {@link #MAX_DURATION} degrees.
	 *
	 * @throws IllegalArgumentException with a message that says what is wrong
	 */
	public static CamProfile parse(Kind kind, Map<String, Double> values) {
		for (String key : values.keySet())
			if (!KEYS.contains(key))
				throw new IllegalArgumentException("unknown key \"" + key + "\"; the keys are " + new TreeSet<>(KEYS));
		CamProfile base = kind.builtIn();
		CamProfile profile = new CamProfile(get(values, "intake_open", base.intakeOpen()), get(values, "intake_close", base.intakeClose()),
			get(values, "exhaust_open", base.exhaustOpen()), get(values, "exhaust_close", base.exhaustClose()),
			get(values, "injection", base.injection()), get(values, "spark", base.spark()));
		duration("intake", profile.intakeOpen(), profile.intakeClose());
		duration("exhaust", profile.exhaustOpen(), profile.exhaustClose());
		return profile;
	}

	private static double get(Map<String, Double> values, String key, double fallback) {
		Double value = values.get(key);
		if (value == null)
			return fallback;
		if (!(value >= 0 && value < 720))
			throw new IllegalArgumentException("\"" + key + "\" is an angle in cycle degrees from 0 up to 720, not " + value);
		return value;
	}

	private static void duration(String valve, double open, double close) {
		double duration = SimMath.wrap(close - open);
		if (!(duration >= MIN_DURATION && duration <= MAX_DURATION))
			throw new IllegalArgumentException("the " + valve + " would be open for " + Math.round(duration) + " degrees; it must be between "
				+ (int) MIN_DURATION + " and " + (int) MAX_DURATION);
	}
}
