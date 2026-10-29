package com.createengines.engine.sim;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

/**
 * Measures an engine's torque curve the way a real one is rated: copies of its cylinders are driven at a series of
 * fixed speeds with every injector fully open, and the net torque they make at each speed is recorded.
 * <p>
 * An engine under load slows down until its torque matches the load, so what it can carry is the peak of this
 * curve at or below its current speed, not whatever it happens to make at the speed it is running.
 */
public final class Dynamometer {

	/** Speeds the curve is measured at, RPM. */
	public static final double[] SPEEDS = {16, 32, 48, 64, 80, 96, 112, 128, 144, 160, 176, 192, 208, 224, 240, 256};
	/** Rotor cycles to run at each speed: the first settles the fresh gas, the last is measured. */
	private static final int CYCLES = 2;

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
		List<CylinderModel> copies = new ArrayList<>();
		for (CylinderModel cylinder : engine.cylinders()) {
			Camshaft live = cylinder.camshaft();
			Camshaft camshaft = live == null ? null : camshafts.computeIfAbsent(live, c -> new Camshaft());
			CylinderModel copy = cylinder.copyOnto(camshaft);
			copy.rack = 1;
			FuelSpec fuel = cylinder.fuelSupply.fuel();
			copy.fuelSupply = fuel != null && live != null && live.isPhaseLocked() ? unlimited(fuel) : FuelSupply.NONE;
			copies.add(copy);
		}
		EngineSimulation dyno =
			new EngineSimulation(copies, engine.baseInertia(), engine.baseFriction(), engine.baseViscousFriction());
		dyno.lubrication()
			.setFilm(engine.lubrication()
				.film());
		dyno.restore(0, 0, engine.direction());
		for (CylinderModel copy : copies)
			copy.resetGas(copy.pistonAngle(0, engine.direction()));

		double omega = EngineSimulation.rpmToOmega(rpm);
		double degreesPerTick = Math.toDegrees(omega * EngineSimulation.TICK);
		int ticks = (int) Math.ceil(CYCLES * SimMath.CYCLE / degreesPerTick) + 1;
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
