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

	/** Speeds the curve is measured at, RPM, up to Create's usual limit of 256. */
	public static final double[] SPEEDS = {16, 32, 48, 64, 80, 96, 112, 128, 144, 160, 176, 192, 208, 224, 240, 256};
	/**
	 * {@link #SPEEDS} and then coarser steps beyond them, for an engine on a network allowed to turn faster than Create's
	 * usual limit (it is a setting): such an engine changes less for each RPM, so fewer points follow it.
	 */
	private static final double[] ALL_SPEEDS = {16, 32, 48, 64, 80, 96, 112, 128, 144, 160, 176, 192, 208, 224, 240, 256,
		320, 384, 448, 512, 640, 768, 1024, 1536, 2048, 3072, 4096, 6144, 8192};
	/** The fastest speed measured when no limit is given: Create's usual limit. */
	public static final double DEFAULT_MAX_RPM = 256;
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
		return torqueCurve(engine, DEFAULT_MAX_RPM);
	}

	/**
	 * As {@link #torqueCurve(EngineSimulation)}, measured only up to {@code maxRpm}: an engine is never asked for torque
	 * faster than its network is allowed to turn, so the speeds beyond are left at zero rather than paid for.
	 */
	public static double[] torqueCurve(EngineSimulation engine, double maxRpm) {
		Measurement measurement = new Measurement(engine, maxRpm);
		while (!measurement.advance()) {
		}
		return measurement.curve();
	}

	/**
	 * A torque curve measured one speed at a time, slowest first. Running every speed at once is the most expensive thing
	 * an engine does (a whole engine is run through two or more cycles at each of a couple of dozen speeds, and a
	 * turbocharged one also waits for its boost to build), so an engine in play takes it a speed a tick instead, and the
	 * speeds it is running at now are known first.
	 */
	public static final class Measurement {

		private final EngineSimulation engine;
		private final double[] curve;
		private final double[] ripples;
		private final int points;
		private int next;

		public Measurement(EngineSimulation engine, double maxRpm) {
			this.engine = engine;
			int count = 1;
			while (count < ALL_SPEEDS.length && ALL_SPEEDS[count] <= maxRpm)
				count++;
			points = count;
			// Up to the usual limit the curve is the one with a point every 16 RPM, however far it is asked to go
			curve = new double[Math.max(points, SPEEDS.length)];
			ripples = new double[curve.length];
		}

		/** Measures the next speed. Returns whether the curve is now complete. */
		public boolean advance() {
			if (next < points) {
				double[] point = measure(engine, ALL_SPEEDS[next]);
				curve[next] = point[0];
				ripples[next] = point[1];
				next++;
			}
			return next >= points;
		}

		/** The curve so far: speeds not yet measured read zero. */
		public double[] curve() {
			return curve;
		}

		/** The speed ripple the engine would have free at each speed of {@link #curve()}, for {@link Roughness}. */
		public double[] ripples() {
			return ripples;
		}
	}

	/**
	 * What the engine can be rated for at or below {@code rpm}: its best torque there, each speed's held back by the
	 * {@link Roughness roughness} it would turn with. A rough engine is rated by a speed where it runs smoother, not by
	 * its biggest and bumpiest pulse.
	 */
	public static double ratedTorque(double[] curve, double[] ripples, double rpm) {
		double best = 0;
		for (int i = 0; i < curve.length && i < ALL_SPEEDS.length && (i == 0 || ALL_SPEEDS[i] <= rpm); i++)
			best = Math.max(best, curve[i] * Roughness.smoothness(i < ripples.length ? ripples[i] : 0));
		return best;
	}

	/** The share of the peak torque at or below {@code rpm} that roughness takes off the rating. */
	public static double roughnessLoss(double[] curve, double[] ripples, double rpm) {
		double peak = peakTorque(curve, rpm);
		return peak <= 0 ? 0 : Math.max(0, 1 - ratedTorque(curve, ripples, rpm) / peak);
	}

	/** The speed, RPM, the curve's {@code index}th point is measured at. */
	public static double speed(int index) {
		return ALL_SPEEDS[index];
	}

	/** How many points a curve measured up to {@code maxRpm} has. */
	public static int pointCount(double maxRpm) {
		int count = 1;
		while (count < ALL_SPEEDS.length && ALL_SPEEDS[count] <= maxRpm)
			count++;
		return count;
	}

	/** The most torque the curve offers at or below {@code rpm}, never less than zero. */
	public static double peakTorque(double[] curve, double rpm) {
		double peak = 0;
		for (int i = 0; i < curve.length && i < ALL_SPEEDS.length && (i == 0 || ALL_SPEEDS[i] <= rpm); i++)
			peak = Math.max(peak, curve[i]);
		return peak;
	}

	/** Net torque and free-running speed ripple at {@code rpm}. */
	private static double[] measure(EngineSimulation engine, double rpm) {
		Map<Camshaft, Camshaft> camshafts = new IdentityHashMap<>();
		Map<CylinderModel, CylinderModel> copyOf = new IdentityHashMap<>();
		List<CylinderModel> copies = new ArrayList<>();
		boolean ported = false;
		for (CylinderModel cylinder : engine.cylinders()) {
			Camshaft live = cylinder.camshaft();
			Camshaft camshaft = live == null ? null : camshafts.computeIfAbsent(live, c -> new Camshaft());
			CylinderModel copy = cylinder.copyOnto(camshaft);
			copyOf.put(cylinder, copy);
			copy.rack = 1;
			copy.ignition = cylinder.ignition.installed() && cylinder.ignition.powered() ? IgnitionSource.UNLIMITED
				: IgnitionSource.NONE;
			FuelSpec fuel = cylinder.fuelSupply.fuel();
			copy.fuelSupply = fuel != null && cylinder.isTimed() ? unlimited(fuel) : FuelSupply.NONE;
			ported |= cylinder.ported();
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
		dyno.trackSwing();
		dyno.restore(0, 0, engine.direction());
		for (CylinderModel copy : copies)
			copy.resetGas(copy.pistonAngle(0, engine.direction()));

		double omega = EngineSimulation.rpmToOmega(rpm);
		double degreesPerTick = Math.toDegrees(omega * EngineSimulation.TICK);
		// A rotary chamber's cycle is a turn and a half longer, so its first has to be spent filling and settling too
		int ticks = (int) Math.ceil((CYCLES + (ported ? 1 : 0)) * SimMath.CYCLE / degreesPerTick) + 1
			+ (engine.turbos().isEmpty() ? 0 : TURBO_WARMUP_TICKS);
		for (int t = 0; t < ticks; t++)
			dyno.tickDriven(omega, engine.direction());
		return new double[] {dyno.netTorque(), dyno.predictedRipple()};
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
