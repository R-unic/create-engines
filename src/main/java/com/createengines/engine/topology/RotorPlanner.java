package com.createengines.engine.topology;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Sets the eccentric angles of an engine's Wankel rotors so that the engine fires as evenly as it can.
 * <p>
 * A rotor fires once a turn of the shaft, so twice in the 720 degree cycle the piston planner works in, 360 degrees
 * apart. All that the player can change about when is the angle of the housing's eccentric, in 15 degree steps, so the
 * planner chooses a <i>phase</i> for each rotor (the shaft angle, mod 360, it first fires at) and lets the pistons'
 * firings, which are fixed by now, fall where they fall. Pure Java, so it can be tested without a world.
 */
public final class RotorPlanner {

	private static final double CYCLE = 720;
	/** Eccentric angles are set in steps of this many degrees. */
	public static final double STEP = FiringPlanner.THROW_STEP;
	private static final int EXHAUSTIVE_LIMIT = 3;

	/**
	 * @param phases    the phase each rotor fires at, degrees mod 360, in the order they were given
	 * @param deviation the largest amount, degrees, any gap between the engine's firings is from an even spacing
	 */
	public record Plan(double[] phases, double deviation) {
		public boolean even() {
			return deviation < 0.5;
		}
	}

	private RotorPlanner() {
	}

	/**
	 * The eccentric angle that makes a rotor fire at {@code phase}. A housing's top dead centre is its eccentric angle
	 * mirrored, in the engine's frame, and a rotor fires half a turn of the shaft after it; a crank that gearing or its
	 * direction turns the other way mirrors both.
	 */
	public static double eccentricFor(double phase, int sense, int direction) {
		return wrap(-sense * direction * (phase - 180));
	}

	/** The phase a rotor with eccentric angle {@code eccentric} fires at. The inverse of {@link #eccentricFor}. */
	public static double phaseOf(double eccentric, int sense, int direction) {
		return wrap(180 - sense * direction * eccentric);
	}

	/** The largest amount, degrees, a gap between the firings is from even, with the rotors firing at {@code phases}. */
	public static double deviation(double[] fixed, double[] phases) {
		double[] all = Arrays.copyOf(fixed, fixed.length + 2 * phases.length);
		for (int i = 0; i < phases.length; i++) {
			all[fixed.length + 2 * i] = wrap(phases[i]);
			all[fixed.length + 2 * i + 1] = wrap(phases[i]) + 360;
		}
		int n = all.length;
		if (n < 2)
			return 0;
		for (int i = 0; i < n; i++)
			all[i] = ((all[i] % CYCLE) + CYCLE) % CYCLE;
		Arrays.sort(all);
		double worst = 0;
		for (int i = 0; i < n; i++) {
			double gap = all[(i + 1) % n] - all[i];
			if (gap <= 0)
				gap += CYCLE;
			worst = Math.max(worst, Math.abs(gap - CYCLE / n));
		}
		return worst;
	}

	/**
	 * @param stride  which phases the style allows, in 15 degree steps (0 leaves the rotors where they are)
	 * @param fixed   the angles in the 720 degree cycle that the engine's other cylinders fire at
	 * @param current each rotor's phase now, which is kept if {@code stride} is 0
	 */
	public static Plan solve(int stride, double[] fixed, double[] current) {
		int rotors = current.length;
		if (stride <= 0 || rotors == 0)
			return new Plan(current.clone(), deviation(fixed, current));
		List<Double> candidates = new ArrayList<>();
		for (int step = 0; step * STEP < 360; step += stride)
			candidates.add(step * STEP);

		double[] best = new double[rotors];
		if (rotors <= EXHAUSTIVE_LIMIT) {
			double bestCost = Double.MAX_VALUE;
			int[] pick = new int[rotors];
			long total = (long) Math.pow(candidates.size(), rotors);
			double[] trial = new double[rotors];
			for (long index = 0; index < total; index++) {
				long rest = index;
				for (int i = rotors - 1; i >= 0; i--) {
					pick[i] = (int) (rest % candidates.size());
					rest /= candidates.size();
				}
				for (int i = 0; i < rotors; i++)
					trial[i] = candidates.get(pick[i]);
				double cost = deviation(fixed, trial);
				if (cost < bestCost - 1e-9) {
					bestCost = cost;
					best = trial.clone();
				}
			}
		} else {
			// Start from the ideal even spread, on the nearest steps the style allows, and let each rotor have a few goes
			// at moving to where it fits best
			double[] placed = new double[rotors];
			for (int i = 0; i < rotors; i++) {
				double ideal = 360d * i / rotors;
				double nearest = candidates.get(0);
				for (double candidate : candidates)
					if (Math.abs(candidate - ideal) < Math.abs(nearest - ideal))
						nearest = candidate;
				placed[i] = nearest;
			}
			for (int pass = 0; pass < 3; pass++)
				for (int i = 0; i < rotors; i++)
					placed[i] = bestFor(candidates, fixed, placed, i);
			best = placed;
		}
		return new Plan(best, deviation(fixed, best));
	}

	private static double bestFor(List<Double> candidates, double[] fixed, double[] phases, int index) {
		double bestPhase = phases[index];
		double bestCost = Double.MAX_VALUE;
		double[] trial = phases.clone();
		for (double candidate : candidates) {
			trial[index] = candidate;
			double cost = deviation(fixed, trial);
			if (cost < bestCost - 1e-9) {
				bestCost = cost;
				bestPhase = candidate;
			}
		}
		return bestPhase;
	}

	private static double wrap(double angle) {
		return ((angle % 360) + 360) % 360;
	}
}
