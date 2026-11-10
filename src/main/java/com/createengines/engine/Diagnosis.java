package com.createengines.engine;

import java.util.Locale;
import java.util.Map;

import com.createengines.engine.sim.CycleResult;
import com.createengines.engine.topology.CylinderFault;

/**
 * Why an engine or a cylinder is not doing its job, in the terms a player can act on. One place turns the scanner's
 * {@link CylinderFault faults} and the simulation's {@link CycleResult cycle results} into a plain cause, so a cylinder's
 * goggles, the engine's health line and the Ponder pages all say the same thing. Pure Java.
 */
public final class Diagnosis {

	/** What is wrong, most fundamental first: a cause earlier in the list hides the ones after it on the same cylinder. */
	public enum Cause {
		NONE,
		/** A part is missing or in the wrong place, or rotary engines are switched off. */
		BUILD,
		/** The engine, or one of its air or exhaust openings, is under a fluid. */
		WATER,
		/** No air can reach the cylinder. */
		NO_AIR,
		/** The cam cannot open the valves. */
		NO_CAM,
		/** The cylinder is injecting nothing, or has nothing to inject. */
		NO_FUEL,
		/** The plug has no battery behind it, or got no current. */
		NO_SPARK,
		/** A rotor housing with no plug. */
		NO_PLUG,
		/** The burned gas cannot get out. */
		NO_EXHAUST,
		/** Fuel went in, the charge was never hot enough to light. */
		NO_IGNITION,
		TOO_LEAN,
		TOO_RICH,
		/** A plug caked with soot. */
		FOULED,
		/** The oil is gone or the bearings are running dry. */
		DRY_OIL,
		/** The cylinders fire unevenly: the speed surges and the rating suffers. */
		UNEVEN,
		/** The block has not warmed through. */
		COLD;

		public String langKey() {
			return "goggles.cause." + name().toLowerCase(Locale.ROOT);
		}
	}

	/** How the engine is running overall. */
	public enum Health {
		HEALTHY,
		ROUGH,
		FAILING;

		public String langKey() {
			return "goggles.health." + name().toLowerCase(Locale.ROOT);
		}
	}

	/** An engine's health and the one thing most responsible for it. */
	public record Report(Health health, Cause cause) {

		public static final Report HEALTHY = new Report(Health.HEALTHY, Cause.NONE);
	}

	/** Share of the rating roughness takes off at which an engine counts as rough. */
	public static final double ROUGH_LOSS = 0.05;
	/** Oil film below which the bearings are running dry. */
	public static final double DRY_BELOW = 0.4;
	/** Oil film below which the engine is failing. */
	public static final double SEIZING_BELOW = 0.15;
	/** Cold (0 warm, 1 stone cold) beyond which a running engine is called rough. */
	public static final double COLD_ROUGH = 0.5;

	private Diagnosis() {
	}

	/**
	 * What stops a cylinder with these {@code faults} and this last cycle from firing, or {@link Cause#NONE}. Faults come
	 * first because they hold whatever the last cycle was; a cylinder that fired is fine.
	 */
	public static Cause ofCylinder(int faults, CycleResult result) {
		if (CylinderFault.disables(faults) || CylinderFault.ROTARY_DISABLED.in(faults))
			return Cause.BUILD;
		if (CylinderFault.underwater(faults))
			return Cause.WATER;
		if (CylinderFault.intakeShut(faults))
			return Cause.NO_AIR;
		if (CylinderFault.NO_CAM.in(faults) || CylinderFault.BROKEN_CAM.in(faults))
			return Cause.NO_CAM;
		if (CylinderFault.NO_INJECTOR.in(faults))
			return Cause.NO_FUEL;
		if (CylinderFault.NO_BATTERY.in(faults))
			return Cause.NO_SPARK;
		if (CylinderFault.NO_PLUG.in(faults))
			return Cause.NO_PLUG;
		if (CylinderFault.exhaustShut(faults))
			return Cause.NO_EXHAUST;
		return switch (result) {
			case NO_FUEL -> Cause.NO_FUEL;
			case NO_AIR -> Cause.NO_AIR;
			case NO_SPARK -> Cause.NO_SPARK;
			case NO_IGNITION -> Cause.NO_IGNITION;
			case TOO_LEAN -> Cause.TOO_LEAN;
			case TOO_RICH -> Cause.TOO_RICH;
			case FOULED -> Cause.FOULED;
			case FIRED, NONE -> Cause.NONE;
		};
	}

	/**
	 * Rates a whole engine.
	 *
	 * @param running   the crank is turning under its own power or is being cranked
	 * @param working   cylinders that are part of the simulation
	 * @param firing    cylinders that fired on their last cycle
	 * @param causes    for each cause, how many working cylinders it is stopping (as {@link #ofCylinder})
	 * @param roughness share of the rating that uneven running takes off
	 * @param oil       how well oiled the moving parts are, 0 to 1
	 * @param cold      how cold the block is, 0 to 1
	 */
	public static Report assess(boolean running, int working, int firing, Map<Cause, Integer> causes, double roughness,
								double oil, double cold) {
		Cause worst = Cause.NONE;
		int most = 0;
		for (Map.Entry<Cause, Integer> entry : causes.entrySet()) {
			if (entry.getKey() == Cause.NONE)
				continue;
			boolean earlier = entry.getValue() == most && entry.getKey().ordinal() < worst.ordinal();
			if (entry.getValue() > most || earlier && most > 0) {
				most = entry.getValue();
				worst = entry.getKey();
			}
		}
		if (working == 0)
			return new Report(Health.FAILING, Cause.BUILD);
		double idle = 1 - (double) firing / working;
		if (running && oil < SEIZING_BELOW)
			return new Report(Health.FAILING, Cause.DRY_OIL);
		if (worst != Cause.NONE) {
			// A stopped engine with something stopping a cylinder cannot run as it is; a running one is only as bad as the
			// share of its cylinders that are not firing
			if (!running || idle >= 0.5)
				return new Report(Health.FAILING, worst);
			if (idle > 0)
				return new Report(Health.ROUGH, worst);
		}
		if (running && oil < DRY_BELOW)
			return new Report(Health.ROUGH, Cause.DRY_OIL);
		if (running && roughness >= ROUGH_LOSS)
			return new Report(Health.ROUGH, Cause.UNEVEN);
		if (running && cold >= COLD_ROUGH)
			return new Report(Health.ROUGH, Cause.COLD);
		return Report.HEALTHY;
	}
}
