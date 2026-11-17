package com.createengines.engine.sim;

import java.util.Arrays;

/**
 * Net energy the crank gains over a 720 degree cycle, less what an even torque would have given by the same angle:
 * the biggest excess minus the biggest shortfall is how far the energy swings. Fed each step of a driven engine by
 * {@link EngineSimulation}, which turns the result into a speed ripple ({@link EngineSimulation#predictedRipple()}).
 */
final class SwingTracker {

	private double[] angles = new double[128];
	private double[] energies = new double[128];
	private int count;
	private double totalAngle;
	private double totalEnergy;
	private double result;

	/** The swing of the last whole cycle driven, or 0 before one is complete. */
	double result() {
		return result;
	}

	void add(double work, double radians) {
		totalAngle += radians;
		totalEnergy += work;
		if (count == angles.length) {
			angles = Arrays.copyOf(angles, count * 2);
			energies = Arrays.copyOf(energies, count * 2);
		}
		angles[count] = totalAngle;
		energies[count] = totalEnergy;
		count++;
		if (totalAngle >= Math.toRadians(SimMath.CYCLE) - 1e-9) {
			double slope = totalEnergy / totalAngle;
			double low = 0;
			double high = 0;
			for (int i = 0; i < count; i++) {
				double excess = energies[i] - slope * angles[i];
				low = Math.min(low, excess);
				high = Math.max(high, excess);
			}
			result = high - low;
			count = 0;
			totalAngle = 0;
			totalEnergy = 0;
		}
	}
}
