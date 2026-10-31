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
 * Turbos in series on the same intake run compound: the air passes through each compressor in turn, so their pressure
 * ratios multiply and their temperature rises multiply, and every turbine in series on an exhaust run adds its own
 * back-pressure; what that buys is a higher boost than one turbo can make, at the price of back-pressure and lag. Turbos
 * side by side (the air can go round either) share the flow instead: the cylinder gets their average pressure, each
 * turbine splits the exhaust heat, and each wheel is a smaller one, so the pair makes about one turbo's boost sooner.
 * <p>
 * A variable-geometry turbo has a ring of vanes in front of its turbine. They close when the exhaust is thin, which
 * speeds the gas up onto the wheel so it spools sooner and catches more of the heat (at the price of a more restrictive
 * turbine), and open as the flow grows, which leaves the exhaust a freer path than a fixed turbine's. Anti-lag keeps
 * a turbo spinning when the engine is off the throttle, by burning fuel in the exhaust ahead of the turbine so that the
 * boost is waiting when the throttle opens again; it costs that fuel.
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
	/** Exhaust heat per second, per unit of flow share, at which variable vanes are fully open: any more and they gain nothing. */
	private static final double VANES_OPEN_HEAT = 15;
	/** Extra share of the exhaust heat the turbine catches with its vanes fully closed. */
	private static final double VANE_POWER_GAIN = 0.5;
	/** How restrictive the turbine is, against a fixed one's 1, with the vanes fully open and fully closed. */
	private static final double VANES_OPEN_RESTRICTION = 0.7;
	private static final double VANES_CLOSED_RESTRICTION = 1.3;
	/** Exhaust heat per second per level of anti-lag that burning fuel ahead of the turbine adds. */
	public static final double ANTI_LAG_HEAT = 4;
	/** Fuel burned in the exhaust per second per level of anti-lag, mB. */
	public static final double ANTI_LAG_FUEL = 0.12;
	/** The strongest anti-lag setting. */
	public static final int MAX_ANTI_LAG = 3;

	private final List<CylinderModel> turbine;
	private final List<CylinderModel> compressor;
	private final double share;

	/** Boost the wastegate holds, atmospheres. */
	public double limit = DEFAULT_LIMIT;
	/** Forces the wastegate wide open, dumping the boost. */
	public boolean dump;
	/** The turbine has a ring of variable vanes. */
	public boolean variableGeometry;
	/** Anti-lag setting, 0 (off) to {@link #MAX_ANTI_LAG}. It only works while {@link #offThrottle}. */
	public int antiLag;
	/** The engine is off the throttle: its fuel is cut or throttled shut, so its exhaust has little heat of its own. */
	public boolean offThrottle;
	/** Where the anti-lag fuel comes from; none, and anti-lag does nothing. */
	public FuelSupply antiLagFuel = FuelSupply.NONE;
	/** The intake air can bypass the compressor, so its pressure is averaged with the other branches' rather than compounded. */
	public boolean parallelCompressor;
	/** The exhaust can bypass the turbine, so its back-pressure is averaged with the other branches' rather than added. */
	public boolean parallelTurbine;
	/**
	 * Share of the intake air this compressor moves: 1 in series, 1/n in a bank of n in parallel. A compressor moving a
	 * share does that share of the work, and is a smaller wheel (inertia falls with the square of the flow, since a
	 * wheel's mass falls faster than its capacity), so the bank reaches the same boost as one turbo but spools sooner.
	 */
	public double flow = 1;

	private double energy;
	private double bypass;
	private double vanes;
	private boolean antiLagging;

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
		copy.variableGeometry = variableGeometry;
		copy.parallelCompressor = parallelCompressor;
		copy.parallelTurbine = parallelTurbine;
		copy.flow = flow;
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
		return Math.sqrt(2 * energy / (INERTIA * flow * flow));
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
		double restriction = variableGeometry
			? VANES_OPEN_RESTRICTION + (VANES_CLOSED_RESTRICTION - VANES_OPEN_RESTRICTION) * vanes : 1;
		return 1 + BACK_PRESSURE * boost() * (1 - bypass) * restriction;
	}

	/** How far the variable vanes are closed, 0 (open) to 1; always 0 on a fixed turbo. */
	public double vanes() {
		return vanes;
	}

	/** Whether fuel is being burned in the exhaust to hold the spool up off the throttle. */
	public boolean antiLagging() {
		return antiLagging;
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
		vanes = other.vanes;
	}

	/**
	 * Spools for one tick on the heat the cylinders sent out during it. The engine then puts this turbo's pressures
	 * together with every other stage's, so that two turbos on one run add up instead of overwriting each other.
	 */
	void tick(double dt) {
		double power = heatRate(dt);
		// Closed vanes catch more of a thin exhaust; a full flow needs them open
		vanes = variableGeometry ? 1 - Math.min(1, power / (VANES_OPEN_HEAT * flow)) : 0;
		power *= 1 + VANE_POWER_GAIN * vanes;
		antiLagging = false;
		if (antiLag > 0 && offThrottle) {
			double wanted = ANTI_LAG_FUEL * antiLag * dt;
			double burned = antiLagFuel.draw(wanted);
			if (burned > 0) {
				antiLagging = true;
				power += ANTI_LAG_HEAT * antiLag * burned / wanted;
			}
		}
		bypass = dump ? 1 : Math.max(0, Math.min(1, (boost() - limit) / WASTEGATE_BAND));
		double spool = spool();
		double drain = flow * (COMPRESSOR_POWER * spool * spool * spool + BEARING_DRAG * spool);
		energy = Math.max(0, energy + (power * (1 - bypass) - drain) * dt);

	}
}
