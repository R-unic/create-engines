package com.createengines.engine.sim;

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
 */
public record CylinderSpec(double compressionRatio, double rodRatio, double intakeFlow, double exhaustFlow,
						   double maxFuelPerShot, double ignitionDelay, double burnDuration, double frictionStatic,
						   double frictionViscous, double inertia) {

	/** Andesite-age cylinder: low compression, slow breathing, slow combustion, lots of friction. */
	public static final CylinderSpec CRUDE = new CylinderSpec(12, 4 / 14d, 20, 20, 0.5, 0.02, 0.06, 0.08, 0.01, 0.15);

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
		if (precisionBore) {
			compression *= PRECISION_COMPRESSION;
			frictionStatic *= PRECISION_BORE_STATIC;
			frictionViscous *= PRECISION_BORE_VISCOUS;
		}
		if (precisionHead) {
			intake *= PRECISION_BREATHING;
			exhaust *= PRECISION_BREATHING;
			delay *= PRECISION_HEAD_DELAY;
		}
		if (precisionPiston) {
			inertia *= PRECISION_PISTON_INERTIA;
			frictionStatic *= PRECISION_PISTON_STATIC;
			frictionViscous *= PRECISION_PISTON_VISCOUS;
		}
		if (precisionInjector) {
			delay *= PRECISION_INJECTOR_DELAY;
			burn *= PRECISION_INJECTOR_BURN;
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
		return clearanceVolume() + stroke;
	}

	/** Friction torque of one cylinder turning at {@code omega}, with its parts oiled to {@code film}. */
	public double frictionAt(double omega, double film) {
		return frictionStatic * Lubrication.staticFactor(film) + frictionViscous * omega * Lubrication.viscousFactor(film);
	}
}
