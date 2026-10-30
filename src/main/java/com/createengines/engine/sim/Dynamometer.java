package com.createengines.engine.sim;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

/**
 * Measures an engine's torque curve the way a real one is rated: copies of its cylinders are driven at a series of
 * fixed speeds with every injector's rack fully open (and still set to the metering the player chose), and the net torque they make at each speed is recorded.
 * <p>
 * An engine under load slows down until its torque matches the load, so what it can carry is the peak of this
 * curve at or below its current speed, not whatever it happens to make at the speed it is running.
 */
public final class Dynamometer {

	/** Speeds the curve is measured at, RPM. */
	public static final double[] SPEEDS = {16, 32, 48, 64, 80, 96, 112, 128, 144, 160, 176, 192, 208, 224, 240, 256};
	/** Rotor cycles to run at each speed: the first settles the fresh gas, the last is measured. */
	private static final int CYCLES = 2;
	/** Extra ticks to let a turbo spool up to the boost this speed gives, about five seconds. */
	private static final int TURBO_WARMUP_TICKS = 100;

	private Dynamometer() {
	}

	/**
	 * Net torque at each of {@link #SPEEDS}. Cylinders that have fuel and a cam geared 1:2 are run as they are
	 * built, with their racks fully open and unlimited fuel. The rest are turned over without fuel, because they
	 * still cost pumping and friction.
	 */
	public static double[] torqueCurve(EngineSimulation engine) {
		double[] curve = new double[SPEEDS.length];
		for (int i = 0; i < SPEEDS.length; i++)
			curve[i] = measure(engine, SPEEDS[i]);
		return curve;
	}

	/** The most torque the curve offers at or below {@code rpm}, never less than zero. */
	public static double peakTorque(double[] curve, double rpm) {
		double peak = 0;
		for (int i = 0; i < SPEEDS.length && (i == 0 || SPEEDS[i] <= rpm); i++)
			peak = Math.max(peak, curve[i]);
		return peak;
	}

	private static double measure(EngineSimulation engine, double rpm) {
		Map<Camshaft, Camshaft> camshafts = new IdentityHashMap<>();
		Map<CylinderModel, CylinderModel> copyOf = new IdentityHashMap<>();
		List<CylinderModel> copies = new ArrayList<>();
		for (CylinderModel cylinder : engine.cylinders()) {
			Camshaft live = cylinder.camshaft();
			Camshaft camshaft = live == null ? null : camshafts.computeIfAbsent(live, c -> new Camshaft());
			CylinderModel copy = cylinder.copyOnto(camshaft);
			copyOf.put(cylinder, copy);
			copy.rack = 1;
			copy.ignition = cylinder.ignition.installed() && cylinder.ignition.powered() ? IgnitionSource.UNLIMITED
				: IgnitionSource.NONE;
			FuelSpec fuel = cylinder.fuelSupply.fuel();
			copy.fuelSupply = fuel != null && live != null && live.isPhaseLocked() ? unlimited(fuel) : FuelSupply.NONE;
			copies.add(copy);
		}
		EngineSimulation dyno =
			new EngineSimulation(copies, engine.baseInertia(), engine.baseFriction(), engine.baseViscousFriction());
		// Turbos start at rest and build the boost this speed gives; the live engine's boost and dump don't carry over
		for (Turbocharger turbo : engine.turbos())
			dyno.addTurbo(turbo.copyOnto(copyOf));
		// A blower turns with the crank at the ratio it was last seen driven at, whatever is really turning it now
		for (Supercharger blower : engine.superchargers())
			dyno.addSupercharger(blower.copyOnto(copyOf));
		for (Intercooler cooler : engine.intercoolers())
			dyno.addIntercooler(cooler.copyOnto(copyOf));
		dyno.lubrication()
			.setFilm(engine.lubrication()
				.film());
		dyno.lubrication()
			.setDilution(engine.lubrication()
				.dilution());
		dyno.setWashesOil(false);
		dyno.restore(0, 0, engine.direction());
		for (CylinderModel copy : copies)
			copy.resetGas(copy.pistonAngle(0, engine.direction()));

		double omega = EngineSimulation.rpmToOmega(rpm);
		double degreesPerTick = Math.toDegrees(omega * EngineSimulation.TICK);
		int ticks = (int) Math.ceil(CYCLES * SimMath.CYCLE / degreesPerTick) + 1
			+ (engine.turbos().isEmpty() ? 0 : TURBO_WARMUP_TICKS);
		for (int t = 0; t < ticks; t++)
			dyno.tickDriven(omega, engine.direction());
		return dyno.netTorque();
	}

	private static FuelSupply unlimited(FuelSpec fuel) {
		return new FuelSupply() {
			@Override
			public double draw(double mb) {
				return mb;
			}

			@Override
			public @Nullable FuelSpec fuel() {
				return fuel;
			}
		};
	}
}
