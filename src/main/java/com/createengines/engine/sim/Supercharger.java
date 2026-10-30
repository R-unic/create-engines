package com.createengines.engine.sim;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A compressor driven by a shaft, not by the exhaust. It makes boost from its own speed at once, with no lag and no
 * dependence on the fuel, so it works from the first turn of the crank and at low revs where a turbo has nothing to spin
 * on. What it costs is the power to turn it, which the owner charges to whatever drives the shaft; and it is a cruder
 * pump than a turbine compressor, so it heats the air more.
 * <p>
 * A bypass valve holds the boost at its limit, and can be forced open to blow all the boost off. Pure Java, like the
 * rest of this package.
 */
public final class Supercharger {

	/** Gauge boost made at the design speed, atmospheres. Boost goes with the square of the speed. */
	public static final double FULL_BOOST = 0.9;
	/** Shaft speed, RPM, at which the blower makes {@link #FULL_BOOST}. */
	public static final double DESIGN_RPM = 128;
	public static final double MAX_LIMIT = Turbocharger.MAX_LIMIT;
	public static final double DEFAULT_LIMIT = 0.5;

	/** Seconds the pressure takes to follow the shaft: the air has to fill the manifold. */
	private static final double RESPONSE = 0.2;
	/** A positive-displacement blower recirculates and heats more than a turbine compressor. */
	private static final double COMPRESSOR_EFFICIENCY = 0.55;
	private static final double HEAT_EXPONENT = 0.3 / 1.3;

	private final List<CylinderModel> compressor;

	/** Boost the bypass valve holds, atmospheres. */
	public double limit = DEFAULT_LIMIT;
	/** Forces the bypass valve open, dumping the boost. */
	public boolean dump;
	/** Speed of the shaft driving the blower, RPM. The owner sets it every tick, unless it follows the crank. */
	public double rpm;
	/** Shaft turns per crank turn, which a dynamometer run uses to work out the speed this blower would be driven at. */
	public double driveRatio = 1;
	/** The air can reach the cylinders without going through this blower, so its pressure is averaged with the other branches'. */
	public boolean parallel;

	private boolean followsCrank;
	private double boost;

	/** @param compressor cylinders that breathe the blower's air */
	public Supercharger(List<CylinderModel> compressor) {
		this.compressor = List.copyOf(compressor);
	}

	/** A blower set up the same way on copies of the cylinders, to test apart from the engine; it turns with the crank. */
	public Supercharger copyOnto(Map<CylinderModel, CylinderModel> copies) {
		List<CylinderModel> mapped = new ArrayList<>();
		for (CylinderModel cylinder : compressor) {
			CylinderModel copy = copies.get(cylinder);
			if (copy != null)
				mapped.add(copy);
		}
		Supercharger copy = new Supercharger(mapped);
		copy.limit = limit;
		copy.driveRatio = driveRatio;
		copy.parallel = parallel;
		copy.followsCrank = true;
		return copy;
	}

	public List<CylinderModel> compressor() {
		return compressor;
	}

	boolean followsCrank() {
		return followsCrank;
	}

	/** Shaft speed as a fraction of design speed. */
	public double speed() {
		return rpm / DESIGN_RPM;
	}

	/** Boost the blower would make at this speed if nothing were bled off. */
	public double potential() {
		return FULL_BOOST * speed() * speed();
	}

	/** Gauge pressure the blower is making, atmospheres. */
	public double boost() {
		return boost;
	}

	/** Share of the boost the bypass valve is bleeding off, 0 (shut) to 1 (all of it). */
	public double bypass() {
		double potential = potential();
		if (dump)
			return 1;
		return potential <= limit ? 0 : 1 - limit / potential;
	}

	/** Temperature of the air leaving the blower, ambient = 1. */
	public double chargeTemperature() {
		return 1 + (Math.pow(1 + boost, HEAT_EXPONENT) - 1) / COMPRESSOR_EFFICIENCY;
	}

	/** Follows the shaft for one tick. */
	void tick(double dt) {
		double target = dump ? 0 : Math.min(limit, potential());
		boost += (target - boost) * (1 - Math.exp(-dt / RESPONSE));
	}
}
