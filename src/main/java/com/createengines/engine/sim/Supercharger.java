package com.createengines.engine.sim;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A compressor driven by a shaft, not by the exhaust. It makes boost from its own speed at once, with no lag and no
 * dependence on the fuel, so it works from the first turn of the crank and at low revs where a turbo has nothing to spin
 * on. What it costs is the power to turn it, which the owner charges to whatever drives the shaft. That is the whole of
 * its bill: its compressor is as good as a turbo's, it adds no back-pressure, and a turbo's own bill (lag, the exhaust
 * heat it lives on, back-pressure, anti-lag fuel) is the blower's gain. Net of its drag it makes about what a turbo does
 * at the same limit: the turbo ahead in the middle of the rev range, where its boost is free, the blower ahead at high
 * revs and high boost, where a turbine chokes the exhaust.
 * <p>
 * A roots blower moves air in proportion to its speed, so its boost climbs with the speed to the power of one and a
 * quarter: at half its design speed it already makes about 40 percent of its full boost, where a turbine compressor
 * (which goes with the square) would make a quarter.
 * <p>
 * A variable-drive blower has a variable-ratio drive and rotors cut to a close clearance. The drive overspeeds the rotors
 * while the shaft is slow and eases back to one to one as it reaches the design speed, so at half that speed it makes
 * about 0.58 bar where a plain blower makes 0.38; and the tight rotors leak less air back round, so the charge leaves
 * cooler than a turbo's or a plain blower's, the blower takes less power to turn, and the pressure follows the shaft faster.
 * <p>
 * A bypass valve holds the boost at its limit (a little over it, as a wastegate does), and can be forced open to blow all the boost off. Pure Java, like the
 * rest of this package.
 */
public final class Supercharger {

	/** Gauge boost made at the design speed, atmospheres. Boost goes with the speed to the power {@link #SPEED_EXPONENT}. */
	public static final double FULL_BOOST = 0.9;
	/** Shaft speed, RPM, at which the blower makes {@link #FULL_BOOST}. */
	public static final double DESIGN_RPM = 128 * CylinderSpec.SPEED_GAIN;
	public static final double MAX_LIMIT = Turbocharger.MAX_LIMIT;
	public static final double DEFAULT_LIMIT = 0.5;
	/** Boost goes with the shaft speed to this power: a roots blower is a flow pump, not a centrifugal wheel. */
	public static final double SPEED_EXPONENT = 1.25;
	/** How much faster than the shaft a variable-drive blower's rotors turn when the shaft is at rest, as a share of its speed. */
	public static final double OVERDRIVE = 0.8;
	/** Share of a plain blower's drag on its shaft that a variable-drive blower's tight rotors take. */
	public static final double VARIABLE_DRAG = 0.7;

	/** How far above its limit the bypass valve lets the boost settle, atmospheres, once the blower could make well over it. */
	private static final double VALVE_SOFTNESS = 0.05;
	/** How far the blower's potential has to climb past the limit for the valve to have opened that far. */
	private static final double VALVE_BAND = 0.1;
	/** Seconds the pressure takes to follow the shaft: the air has to fill the manifold. */
	private static final double RESPONSE = 0.2;
	private static final double VARIABLE_RESPONSE = 0.1;
	/** A blower's compressor is as efficient as a turbo's; what it pays is shaft power, not heat. */
	private static final double COMPRESSOR_EFFICIENCY = 0.70;
	/** Close rotor clearances leave little to recirculate, so the air is cooler than a turbine compressor puts out. */
	private static final double VARIABLE_EFFICIENCY = 0.73;
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
	/** A variable-drive blower: variable-ratio drive, tight rotors. */
	public boolean variableDrive;

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
		copy.variableDrive = variableDrive;
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

	/**
	 * How fast the rotors turn as a fraction of design speed. A plain blower's turn with the shaft; a variable-drive blower's
	 * drive overspeeds them while the shaft is slow, by {@link #OVERDRIVE} at rest, and is one to one from the design
	 * speed up.
	 */
	public double rotorSpeed() {
		double speed = speed();
		return variableDrive ? speed * (1 + OVERDRIVE * Math.max(0, 1 - speed)) : speed;
	}

	/** How much the variable drive is overspeeding the rotors, 0 (one to one) up to {@link #OVERDRIVE}; always 0 on a plain blower. */
	public double overdrive() {
		return variableDrive ? OVERDRIVE * Math.max(0, 1 - speed()) : 0;
	}

	/** Boost the blower would make at this speed if nothing were bled off. */
	public double potential() {
		return FULL_BOOST * Math.pow(rotorSpeed(), SPEED_EXPONENT);
	}

	/** Share of a plain blower's drag on the shaft that this one takes. */
	public double dragFactor() {
		return variableDrive ? VARIABLE_DRAG : 1;
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
		return potential <= limit ? 0 : 1 - held(potential) / potential;
	}

	/**
	 * The boost the bypass valve settles at when the blower could make {@code potential}: the limit, plus a little. The
	 * valve opens as the pressure climbs past the limit rather than all at once, as a turbo's wastegate does, so a blower
	 * set to a limit makes about what a turbo set to it does.
	 */
	private double held(double potential) {
		if (potential <= limit)
			return potential;
		return Math.min(MAX_LIMIT, limit + VALVE_SOFTNESS * Math.min(1, (potential - limit) / VALVE_BAND));
	}

	/** Temperature of the air leaving the blower, ambient = 1. */
	public double chargeTemperature() {
		return 1 + (Math.pow(1 + boost, HEAT_EXPONENT) - 1) / (variableDrive ? VARIABLE_EFFICIENCY : COMPRESSOR_EFFICIENCY);
	}

	/** Follows the shaft for one tick. */
	void tick(double dt) {
		double target = dump ? 0 : held(potential());
		boost += (target - boost) * (1 - Math.exp(-dt / (variableDrive ? VARIABLE_RESPONSE : RESPONSE)));
	}
}
