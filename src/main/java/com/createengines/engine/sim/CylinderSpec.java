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
