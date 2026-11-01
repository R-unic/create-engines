package com.createengines.engine.sim;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Physical description of one cylinder tier.
 * <p>
 * The simulation works in normalized units: ambient pressure = 1, ambient temperature = 1, swept volume = 1.
 * Rates that are "per second" are deliberately time-based rather than angle-based, so that breathing and
 * combustion get worse as RPM rises without any explicit RPM penalty.
 *
 * @param compressionRatio    (swept + clearance) / clearance
 * @param rodRatio            crank throw radius / connecting rod length
 * @param intakeFlow          pressure relaxation rate through a fully open intake valve, 1/s
 * @param exhaustFlow         pressure relaxation rate through a fully open exhaust valve, 1/s
 * @param maxFuelPerShot      fuel injected per cycle at full rack, mB
 * @param ignitionDelay       time between injection and autoignition, s
 * @param burnDuration        time to burn a full shot once ignited, s
 * @param frictionStatic      friction torque per cylinder that does not depend on speed
 * @param frictionViscous     friction torque per cylinder per rad/s
 * @param inertia             effective rotating/reciprocating inertia this cylinder adds to the crank
 * @param displacement        swept volume, in the normalized units where an ordinary cylinder's is 1; gas, work and the
 *                            fuel it can burn all scale with it, so set {@code maxFuelPerShot} to match
 */
public record CylinderSpec(double compressionRatio, double rodRatio, double intakeFlow, double exhaustFlow,
						   double maxFuelPerShot, double ignitionDelay, double burnDuration, double frictionStatic,
						   double frictionViscous, double inertia, double displacement) {

	/** A cylinder of the usual swept volume, 1. */
	public CylinderSpec(double compressionRatio, double rodRatio, double intakeFlow, double exhaustFlow,
						double maxFuelPerShot, double ignitionDelay, double burnDuration, double frictionStatic,
						double frictionViscous, double inertia) {
		this(compressionRatio, rodRatio, intakeFlow, exhaustFlow, maxFuelPerShot, ignitionDelay, burnDuration,
			frictionStatic, frictionViscous, inertia, 1);
	}

	/** Andesite-age cylinder: low compression, slow breathing, slow combustion, lots of friction. */
	public static final CylinderSpec CRUDE = new CylinderSpec(12, 4 / 14d, 20, 20, 0.5, 0.02, 0.06, 0.08, 0.01, 0.15);

	/**
	 * Swept volume of a rotor's chamber against an ordinary cylinder's. A rotor fires once a turn of the shaft to a
	 * cylinder's once in two, so this puts one rotor at a little over two cylinders' worth of power.
	 */
	private static final double ROTARY_DISPLACEMENT = 0.65;

	/**
	 * One chamber of a Wankel rotor. A rotor has three, and the housing shares what they burn between them, so each is a
	 * little lighter than a piston. Compared with a crude cylinder it has:
	 * <ul>
	 *     <li>lower compression (a rotor cannot be squeezed as hard before the seals leak);</li>
	 *     <li>ports that breathe better than valves (it keeps pace at speed);</li>
	 *     <li>a long, thin chamber that burns slowly (the flame has a long way to travel);</li>
	 *     <li>more wall friction (the seals scrape the housing all the way round) but little inertia.</li>
	 * </ul>
	 * Its volume is a plain sine of the cycle angle, so the rod ratio is next to nothing.
	 */
	public static final CylinderSpec ROTARY =
		new CylinderSpec(9.5, 1e-4, 32, 32, 0.5 * ROTARY_DISPLACEMENT, 0.02, 0.09, 0.05, 0.006, 0.07, ROTARY_DISPLACEMENT);

	/**
	 * A rotary chamber with a precision injector fitted: it lights sooner and burns faster, as in a piston engine. The
	 * server's rotary power setting scales the chamber's swept volume and the fuel it can burn.
	 */
	public static CylinderSpec buildRotary(boolean precisionInjector) {
		double power = Tuning.rotaryPower;
		if (!precisionInjector && power == 1)
			return ROTARY;
		double delay = precisionInjector ? Tuning.blend(PRECISION_INJECTOR_DELAY, Tuning.precisionBonus) : 1;
		double burn = precisionInjector ? Tuning.blend(PRECISION_INJECTOR_BURN, Tuning.precisionBonus) : 1;
		return new CylinderSpec(ROTARY.compressionRatio, ROTARY.rodRatio, ROTARY.intakeFlow, ROTARY.exhaustFlow,
			ROTARY.maxFuelPerShot * power, ROTARY.ignitionDelay * delay, ROTARY.burnDuration * burn, ROTARY.frictionStatic,
			ROTARY.frictionViscous, ROTARY.inertia, ROTARY.displacement * power);
	}

	/**
	 * A cylinder built from parts of the given grades. Every precision part improves one aspect of the crude cylinder,
	 * so each is worth fitting alone and they add up:
	 * <ul>
	 *     <li>the <b>bore</b> is a machined liner: higher compression and less wall friction;</li>
	 *     <li>the <b>head</b> has four valves and a pent roof: it breathes better and lights faster;</li>
	 *     <li>the <b>piston</b> is forged and light: less inertia and less drag;</li>
	 *     <li>the <b>injector</b> atomises the fuel finely: it ignites sooner and burns faster.</li>
	 * </ul>
	 */
	public static CylinderSpec build(boolean precisionBore, boolean precisionHead, boolean precisionPiston,
									 boolean precisionInjector) {
		double compression = CRUDE.compressionRatio;
		double intake = CRUDE.intakeFlow;
		double exhaust = CRUDE.exhaustFlow;
		double delay = CRUDE.ignitionDelay;
		double burn = CRUDE.burnDuration;
		double frictionStatic = CRUDE.frictionStatic;
		double frictionViscous = CRUDE.frictionViscous;
		double inertia = CRUDE.inertia;
		double bonus = Tuning.precisionBonus;
		if (precisionBore) {
			compression *= Tuning.blend(PRECISION_COMPRESSION, bonus);
			frictionStatic *= Tuning.blend(PRECISION_BORE_STATIC, bonus);
			frictionViscous *= Tuning.blend(PRECISION_BORE_VISCOUS, bonus);
		}
		if (precisionHead) {
			intake *= Tuning.blend(PRECISION_BREATHING, bonus);
			exhaust *= Tuning.blend(PRECISION_BREATHING, bonus);
			delay *= Tuning.blend(PRECISION_HEAD_DELAY, bonus);
		}
		if (precisionPiston) {
			inertia *= Tuning.blend(PRECISION_PISTON_INERTIA, bonus);
			frictionStatic *= Tuning.blend(PRECISION_PISTON_STATIC, bonus);
			frictionViscous *= Tuning.blend(PRECISION_PISTON_VISCOUS, bonus);
		}
		if (precisionInjector) {
			delay *= Tuning.blend(PRECISION_INJECTOR_DELAY, bonus);
			burn *= Tuning.blend(PRECISION_INJECTOR_BURN, bonus);
		}
		return new CylinderSpec(compression, CRUDE.rodRatio, intake, exhaust, CRUDE.maxFuelPerShot, delay, burn,
			frictionStatic, frictionViscous, inertia);
	}

	public static final double PRECISION_COMPRESSION = 1.2;
	public static final double PRECISION_BORE_STATIC = 0.7;
	public static final double PRECISION_BORE_VISCOUS = 0.8;
	public static final double PRECISION_BREATHING = 1.5;
	public static final double PRECISION_HEAD_DELAY = 0.8;
	public static final double PRECISION_PISTON_INERTIA = 0.7;
	public static final double PRECISION_PISTON_STATIC = 0.8;
	public static final double PRECISION_PISTON_VISCOUS = 0.7;
	public static final double PRECISION_INJECTOR_DELAY = 0.8;
	public static final double PRECISION_INJECTOR_BURN = 0.7;

	public double clearanceVolume() {
		return 1 / (compressionRatio - 1);
	}

	/** Cylinder volume at a crank angle measured from top dead centre, in degrees (slider-crank kinematics). */
	public double volumeAt(double crankDegrees) {
		double theta = Math.toRadians(crankDegrees);
		double sin = Math.sin(theta);
		double rod = (1 - Math.sqrt(1 - rodRatio * rodRatio * sin * sin)) / rodRatio;
		double stroke = 0.5 * ((1 - Math.cos(theta)) + rod);
		return displacement * (clearanceVolume() + stroke);
	}

	/** The tables for this shape of cylinder, built the first time one is asked for and shared by every cylinder of it. */
	public Geometry geometry() {
		return GEOMETRIES.computeIfAbsent(this, Geometry::new);
	}

	private static final Map<CylinderSpec, Geometry> GEOMETRIES = new ConcurrentHashMap<>();

	/**
	 * {@link #volumeAt} and the heating a volume change gives gas, read from tables. A running engine asks for them
	 * thousands of times a tick and they only depend on the crank angle, so they are worked out once for each shape of
	 * cylinder. The tables are fine enough that reading between two entries is exact to a few parts in a million.
	 */
	public static final class Geometry {

		/** Table entries per degree. */
		private static final int PER_DEGREE = 8;
		private static final int SIZE = 360 * PER_DEGREE;

		/** Volume at each angle through a revolution, with two entries past the end that repeat the start. */
		private final double[] volume = new double[SIZE + 2];
		/** Volume to the power {@code GAMMA - 1} at each of them: a gas squeezed adiabatically heats by the ratio of these. */
		private final double[] heat = new double[SIZE + 2];

		private Geometry(CylinderSpec spec) {
			for (int i = 0; i < volume.length; i++) {
				volume[i] = spec.volumeAt((double) i / PER_DEGREE);
				heat[i] = Math.pow(volume[i], CylinderModel.GAMMA - 1);
			}
		}

		/** Cylinder volume at a crank angle past top dead centre, in degrees. */
		public double volume(double crankDegrees) {
			return read(volume, crankDegrees);
		}

		/** The volume raised to the power {@code GAMMA - 1}; the temperature a gas has times this stays put as it is compressed. */
		public double heat(double crankDegrees) {
			return read(heat, crankDegrees);
		}

		private static double read(double[] table, double crankDegrees) {
			double at = SimMath.wrap(crankDegrees, 360) * PER_DEGREE;
			int index = (int) at;
			double low = table[index];
			return low + (table[index + 1] - low) * (at - index);
		}
	}

	/** Friction torque of one cylinder turning at {@code omega}, with its parts oiled to {@code film}. */
	public double frictionAt(double omega, double film) {
		return frictionStatic * Lubrication.staticFactor(film) + frictionViscous * omega * Lubrication.viscousFactor(film);
	}
}
