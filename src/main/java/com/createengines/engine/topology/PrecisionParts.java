package com.createengines.engine.topology;

import org.jetbrains.annotations.Nullable;

import com.createengines.engine.sim.CylinderModel;
import com.createengines.engine.sim.CylinderSpec;
import com.createengines.engine.sim.PartEffects;
import com.createengines.engine.sim.Tuning;

/**
 * Which precision parts a cylinder is built from, as bits. Each part improves the crude cylinder in one way and they
 * add up, so the scanner just notes which are fitted and the controller builds the cylinder's spec from them.
 */
public final class PrecisionParts {

	/** A precision cylinder block: machined liner, higher compression, less wall friction. */
	public static final int BORE = 1;
	/** A precision cylinder head: four valves and a pent roof. */
	public static final int HEAD = 2;
	/** A forged piston: lighter, with less drag. */
	public static final int PISTON = 4;
	/** A precision injector: finer atomisation, so the charge lights sooner and burns faster. */
	public static final int INJECTOR = 8;
	public static final int ALL = BORE | HEAD | PISTON | INJECTOR;

	/** A high compression piston or rotor. Not a precision part of its own, so outside {@link #ALL}. */
	public static final int HIGH_COMPRESSION = 16;
	/** A low compression piston or rotor. */
	public static final int LOW_COMPRESSION = 32;
	/** Where the number of piston rings, 0 to {@link CylinderSpec#MAX_RINGS}, sits in the same bits. */
	private static final int RING_SHIFT = 6;
	private static final int RING_MASK = 3 << RING_SHIFT;

	/** These bits with {@code rings} piston rings in them. */
	public static int withRings(int parts, int rings) {
		return parts & ~RING_MASK | Math.clamp(rings, 0, CylinderSpec.MAX_RINGS) << RING_SHIFT;
	}

	public static int rings(int parts) {
		return (parts & RING_MASK) >> RING_SHIFT;
	}

	/** The share of a burn's heat the piston rings keep: exactly 1 with none. */
	public static double ringSeal(int parts) {
		return 1 + CylinderSpec.RING_GAIN * rings(parts);
	}

	/** What a high compression rotor loses to pre-ignition under boost: 0 for any other rotor. */
	public static double rotorEarlyLoss(int parts) {
		return has(parts, HIGH_COMPRESSION) ? CylinderSpec.HIGH_ROTOR_EARLY_LOSS * Tuning.precisionBonus : 0;
	}

	/** What a low compression rotor gains under boost: 0 for any other rotor. */
	public static double rotorBoostGain(int parts) {
		return has(parts, LOW_COMPRESSION) ? CylinderSpec.LOW_ROTOR_BOOST_GAIN * Tuning.precisionBonus : 0;
	}

	/** The compression part among these, or null for a stock piston or rotor. */
	public static PartEffects.@Nullable Part compression(int parts) {
		if (has(parts, HIGH_COMPRESSION))
			return PartEffects.Part.HIGH_COMPRESSION;
		return has(parts, LOW_COMPRESSION) ? PartEffects.Part.LOW_COMPRESSION : null;
	}

	/**
	 * What a cylinder with no plug judges lighting by, as a scale on its charge temperature. A low compression piston runs a
	 * cooler charge, which must not make a compression-lit engine harder to start: this gives it back the stock piston's
	 * temperature for lighting alone. Exactly 1 for every other piston.
	 */
	public static double lightingHeat(int parts) {
		if (!has(parts, LOW_COMPRESSION))
			return 1;
		double scale = Tuning.blend(PartEffects.of(PartEffects.Part.LOW_COMPRESSION).compression(), Tuning.precisionBonus);
		return scale < 1 ? Math.pow(1 / scale, CylinderModel.HEATING_EXPONENT) : 1;
	}

	private PrecisionParts() {
	}

	public static boolean has(int parts, int part) {
		return (parts & part) != 0;
	}

	/** How many of the four precision parts are fitted. */
	public static int count(int parts) {
		return Integer.bitCount(parts & ALL);
	}

	/** The physical description of a rotary chamber built from these parts; only the injector applies to a rotor. */
	public static CylinderSpec rotarySpec(int parts) {
		return CylinderSpec.buildRotary(has(parts, INJECTOR), compression(parts));
	}

	/** The physical description of a cylinder built from these parts. */
	public static CylinderSpec spec(int parts) {
		return spec(parts, java.util.List.of());
	}

	/** As {@link #spec(int)}, with the part effects of a pack's or an addon's own blocks added (ids of {@code part_effects} files). */
	public static CylinderSpec spec(int parts, java.util.List<String> own) {
		return CylinderSpec.build(has(parts, BORE), has(parts, HEAD), has(parts, PISTON), has(parts, INJECTOR), compression(parts), own);
	}
}
