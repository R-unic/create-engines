package com.createengines.engine.sim;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A compressor driven by the engine's own exhaust. The turbine takes the heat the cylinders send out of their exhaust
 * valves, the compressor takes shaft power in proportion to the cube of its speed, and whatever is left spins the
 * assembly up or down. So the boost is not set by anything: it builds with a lag as the engine makes heat, and it
 * dies when the fuel does.
 * <p>
 * The boost is gauge pressure in atmospheres. It raises the pressure the intake valve draws on, which packs more air
 * into each cylinder (and so more of the fuel can burn, and the mixture leans out), but compressing the air heats it,
 * which gives some of that density back. The turbine is not free either: it holds the exhaust above ambient, so the
 * cylinders push against a back-pressure that rises with the boost. A wastegate dumps exhaust round the turbine to hold
 * the boost at its limit, and can be forced open to blow all the boost off.
 * <p>
 * Pure Java, like the rest of this package.
 */
public final class Turbocharger {

	/** Gauge boost the compressor makes at its design speed, atmospheres. */
	public static final double FULL_BOOST = 1.2;
	/** The strongest boost the wastegate can be set to hold. */
	public static final double MAX_LIMIT = 1.5;
	public static final double DEFAULT_LIMIT = 0.6;

	/** Turbine and compressor wheel inertia: sets how long the spool takes, about a second to reach working speed. */
	private static final double INERTIA = 40;
	/** Shaft power the compressor takes at design speed, in the units of {@link CylinderModel} exhaust heat per second. */
	private static final double COMPRESSOR_POWER = 30;
	private static final double BEARING_DRAG = 0.05;
	/** How far above its limit the boost has to climb before the wastegate is fully open. */
	private static final double WASTEGATE_BAND = 0.1;
	/** Share of the isentropic temperature rise that the compressor actually puts into the air. */
	private static final double COMPRESSOR_EFFICIENCY = 0.7;
	private static final double HEAT_EXPONENT = 0.3 / 1.3;
	/** Exhaust back-pressure the turbine adds, per atmosphere of boost, with the wastegate shut. */
	private static final double BACK_PRESSURE = 0.5;

	private final List<CylinderModel> turbine;
	private final List<CylinderModel> compressor;
	private final double share;

	/** Boost the wastegate holds, atmospheres. */
	public double limit = DEFAULT_LIMIT;
	/** Forces the wastegate wide open, dumping the boost. */
	public boolean dump;

	private double energy;
	private double bypass;

	/**
	 * @param turbine    cylinders whose exhaust runs through the turbine
	 * @param compressor cylinders that breathe the compressor's air
	 * @param share      how much of the exhaust heat is this turbo's, when several turbines sit on one exhaust
	 */
	public Turbocharger(List<CylinderModel> turbine, List<CylinderModel> compressor, double share) {
		this.turbine = List.copyOf(turbine);
		this.compressor = List.copyOf(compressor);
		this.share = share;
	}

	/** A turbo set up the same way on copies of the cylinders, to test apart from the engine; it starts at rest. */
	public Turbocharger copyOnto(Map<CylinderModel, CylinderModel> copies) {
		Turbocharger copy = new Turbocharger(mapped(turbine, copies), mapped(compressor, copies), share);
		copy.limit = limit;
		return copy;
	}

	private static List<CylinderModel> mapped(List<CylinderModel> cylinders, Map<CylinderModel, CylinderModel> copies) {
		List<CylinderModel> result = new ArrayList<>();
		for (CylinderModel cylinder : cylinders) {
			CylinderModel copy = copies.get(cylinder);
			if (copy != null)
				result.add(copy);
		}
		return result;
	}

	public List<CylinderModel> turbine() {
		return turbine;
	}

	public List<CylinderModel> compressor() {
		return compressor;
	}

	/** Wheel speed as a fraction of design speed. */
	public double spool() {
		return Math.sqrt(2 * energy / INERTIA);
	}

	/** Gauge pressure the compressor is making, atmospheres. */
	public double boost() {
		double spool = spool();
		return FULL_BOOST * spool * spool;
	}

	/** Share of the exhaust the wastegate is sending round the turbine, 0 (shut) to 1 (open). */
	public double wastegate() {
		return bypass;
	}

	/** The pressure the exhaust blows down into because the turbine is in the way. */
	public double backPressure() {
		return 1 + BACK_PRESSURE * boost() * (1 - bypass);
	}

	/** Temperature of the air leaving the compressor, ambient = 1. */
	public double chargeTemperature() {
		return 1 + (Math.pow(1 + boost(), HEAT_EXPONENT) - 1) / COMPRESSOR_EFFICIENCY;
	}

	/** Exhaust heat per second the turbine is being fed, from the last tick. */
	public double heatRate(double dt) {
		double heat = 0;
		for (CylinderModel cylinder : turbine)
			heat += cylinder.exhaustHeatLastTick();
		return heat * share / dt;
	}

	/** Takes over another turbo's wheel speed, e.g. when the engine is rebuilt around it. */
	public void copyStateFrom(Turbocharger other) {
		energy = other.energy;
		bypass = other.bypass;
	}

	/** Spools for one tick on the heat the cylinders sent out during it, then sets the pressures they breathe against. */
	void tick(double dt) {
		double power = heatRate(dt);
		bypass = dump ? 1 : Math.max(0, Math.min(1, (boost() - limit) / WASTEGATE_BAND));
		double spool = spool();
		double drain = COMPRESSOR_POWER * spool * spool * spool + BEARING_DRAG * spool;
		energy = Math.max(0, energy + (power * (1 - bypass) - drain) * dt);

		double pressure = 1 + boost();
		double temperature = chargeTemperature();
		double back = backPressure();
		for (CylinderModel cylinder : compressor) {
			cylinder.intakePressure = pressure;
			cylinder.intakeTemperature = temperature;
		}
		for (CylinderModel cylinder : turbine)
			cylinder.exhaustPressure = back;
	}
}
