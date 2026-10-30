package com.createengines.engine.topology;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * Works out a firing order for an engine that is already built: which angle each crank throw should be set to and which
 * angle each cam lobe should be set to, so that the cylinders fire as evenly as the layout allows.
 * <p>
 * A cylinder fires at one point in the two crank revolutions of its cycle. Its piston reaches top dead centre at
 * {@code bore - throwAngle} (mod 360), where {@code bore} says which side of the throw it is bored on, and its cam lobe
 * picks which of the two revolutions it fires on: the lobe angle is half of the rotor angle it fires at. So the firing
 * time of a cylinder is set by exactly two things the player can change, its throw angle and its lobe angle, and the
 * planner searches those for the best spacing around the 720 degree cycle.
 * <p>
 * Pure Java, so it can be unit tested without a world.
 */
public final class FiringPlanner {

	/** Two crank revolutions. */
	public static final double CYCLE = 720;
	/** Throw angles are set in steps of this many degrees. */
	public static final double THROW_STEP = 15;
	/** Lobe angles are set in steps of this many cam degrees. */
	public static final double LOBE_STEP = 7.5;

	private static final int RESTARTS = 40;
	private static final int STEPS = 6000;
	/** Cost of a cylinder that shares a cam lobe with another but fires at a different time. */
	private static final double MISTIMED_COST = 0.3;
	/** Weight of the preference for neighbouring cylinders not to fire one straight after the other. */
	private static final double NEIGHBOUR_WEIGHT = 0.001;
	private static final int EXHAUSTIVE_LIMIT = 16;

	/** Which throw angles the planner may choose. */
	public enum Style {
		/** Any angle in 15 degree steps: the most even firing the layout allows. */
		SMOOTH(1),
		/** Throws a quarter turn apart, as in a cross-plane V8. */
		CROSS_PLANE(6),
		/** Throws a half turn apart only, as in a flat-plane four or a boxer. */
		FLAT_PLANE(12),
		/** Leave the throws where they are and only choose the cam lobes. */
		CAMS_ONLY(0);

		private final int stride;

		Style(int stride) {
			this.stride = stride;
		}

		/** Number of 15 degree steps between the angles this style may choose, or 0 if it chooses none. */
		public int stride() {
			return stride;
		}
	}

	/**
	 * One cylinder that works.
	 *
	 * @param crank index of the crank throw it sits on
	 * @param bore  crank angle, degrees, its top dead centre has with the throw at zero: TDC is {@code bore - throwAngle}
	 * @param lobe  index of the cam lobe that works it, shared by every cylinder with the same index
	 */
	public record Cylinder(int crank, double bore, int lobe) {
	}

	/**
	 * @param throwAngles each throw's angle, degrees
	 * @param fireAngles  the rotor angle, 0 to 720, each cylinder fires at
	 * @param lobeAngles  each lobe's angle, cam degrees; lobes that no cylinder uses keep their entry
	 * @param order       cylinder indices in firing order, starting from the first to fire after top dead centre zero
	 * @param deviation   the largest amount, degrees, any gap between firings is from an even spacing
	 * @param mistimed    cylinders that share a lobe with another but fire at a different time
	 */
	public record Plan(double[] throwAngles, double[] fireAngles, double[] lobeAngles, int[] order, double deviation,
					   int mistimed) {
		public boolean even() {
			return deviation < 0.5;
		}
	}

	private FiringPlanner() {
	}

	/**
	 * @param direction  +1 or -1, the way the crank turns
	 * @param currentThrows each throw's current angle, degrees
	 * @param lobes      each lobe's current angle, cam degrees
	 */
	public static Plan solve(Style style, int direction, double[] currentThrows, double[] lobes, List<Cylinder> cylinders) {
		int n = cylinders.size();
		int t = currentThrows.length;
		if (n == 0)
			return new Plan(currentThrows.clone(), new double[0], lobes.clone(), new int[0], 0, 0);

		int[] options = optionsFor(style);
		State best = null;
		if (style == Style.CAMS_ONLY && n <= EXHAUSTIVE_LIMIT) {
			int[] steps = new int[t];
			for (int i = 0; i < t; i++)
				steps[i] = angleStep(currentThrows[i]);
			boolean[] revolutions = new boolean[n];
			for (long mask = 0; mask < 1L << n; mask++) {
				for (int i = 0; i < n; i++)
					revolutions[i] = (mask >> i & 1) == 1;
				double cost = cost(cylinders, direction, steps, revolutions);
				if (best == null || cost < best.cost) {
					best = new State(steps.clone(), revolutions.clone(), cost);
					if (cost < 1e-12)
						break;
				}
			}
		} else {
			Random random = new Random(31L * n + 7L * t + style.ordinal());
			for (int restart = 0; restart < RESTARTS && (best == null || best.cost > 1e-9); restart++) {
				State candidate = anneal(style, options, direction, currentThrows, cylinders, random);
				if (best == null || candidate.cost < best.cost)
					best = candidate;
			}
		}

		double[] throwAngles = new double[t];
		for (int i = 0; i < t; i++)
			throwAngles[i] = best.steps[i] * THROW_STEP;
		double[] fire = new double[n];
		for (int i = 0; i < n; i++)
			fire[i] = fireAngle(cylinders.get(i), direction, best.steps, best.revolution[i]);

		double[] lobeAngles = lobes.clone();
		boolean[] seen = new boolean[lobes.length];
		int mistimed = 0;
		for (int i = 0; i < n; i++) {
			int lobe = cylinders.get(i)
				.lobe();
			if (lobe < 0 || lobe >= lobes.length)
				continue;
			if (!seen[lobe]) {
				seen[lobe] = true;
				lobeAngles[lobe] = fire[i] / 2;
			} else if (Math.abs(wrap(fire[i] - lobeAngles[lobe] * 2, CYCLE)) > 1e-6)
				mistimed++;
		}

		Integer[] order = new Integer[n];
		for (int i = 0; i < n; i++)
			order[i] = i;
		Arrays.sort(order, Comparator.comparingDouble(i -> fire[i]));
		int[] firingOrder = new int[n];
		for (int i = 0; i < n; i++)
			firingOrder[i] = order[i];
		return new Plan(throwAngles, fire, lobeAngles, firingOrder, deviation(fire), mistimed);
	}

	/** The gaps, degrees, between one firing and the next around the cycle, in firing order. */
	public static double[] intervals(double[] fireAngles) {
		int n = fireAngles.length;
		double[] sorted = fireAngles.clone();
		Arrays.sort(sorted);
		double[] gaps = new double[n];
		for (int i = 0; i < n; i++)
			gaps[i] = i + 1 < n ? sorted[i + 1] - sorted[i] : sorted[0] + CYCLE - sorted[n - 1];
		return gaps;
	}

	private static double deviation(double[] fire) {
		if (fire.length < 2)
			return 0;
		double ideal = CYCLE / fire.length;
		double worst = 0;
		for (double gap : intervals(fire))
			worst = Math.max(worst, Math.abs(gap - ideal));
		return worst;
	}

	// The search

	private record State(int[] steps, boolean[] revolution, double cost) {
	}

	private static int[] optionsFor(Style style) {
		if (style.stride() == 0)
			return new int[0];
		int count = (int) (360 / THROW_STEP);
		List<Integer> options = new ArrayList<>();
		for (int step = 0; step < count; step += style.stride())
			options.add(step);
		return options.stream()
			.mapToInt(Integer::intValue)
			.toArray();
	}

	private static int angleStep(double degrees) {
		int count = (int) (360 / THROW_STEP);
		return ((int) Math.round(degrees / THROW_STEP) % count + count) % count;
	}

	private static State anneal(Style style, int[] options, int direction, double[] current, List<Cylinder> cylinders,
								Random random) {
		int n = cylinders.size();
		int t = current.length;
		int[] steps = new int[t];
		for (int i = 0; i < t; i++)
			steps[i] = style == Style.CAMS_ONLY ? angleStep(current[i]) : options[random.nextInt(options.length)];
		boolean[] revolution = new boolean[n];
		for (int i = 0; i < n; i++)
			revolution[i] = random.nextBoolean();

		double cost = cost(cylinders, direction, steps, revolution);
		State best = new State(steps.clone(), revolution.clone(), cost);
		boolean movableThrows = style != Style.CAMS_ONLY && t > 0;
		for (int step = 0; step < STEPS && best.cost > 1e-9; step++) {
			double temperature = 0.05 * (1 - step / (double) STEPS) + 1e-6;
			int throwIndex = -1;
			int oldStep = 0;
			int flipped = -1;
			if (movableThrows && random.nextBoolean()) {
				throwIndex = random.nextInt(t);
				oldStep = steps[throwIndex];
				steps[throwIndex] = options[random.nextInt(options.length)];
			} else {
				flipped = random.nextInt(n);
				revolution[flipped] = !revolution[flipped];
			}
			double next = cost(cylinders, direction, steps, revolution);
			if (next <= cost || random.nextDouble() < Math.exp((cost - next) / temperature)) {
				cost = next;
				if (cost < best.cost)
					best = new State(steps.clone(), revolution.clone(), cost);
			} else if (throwIndex >= 0)
				steps[throwIndex] = oldStep;
			else
				revolution[flipped] = !revolution[flipped];
		}
		return best;
	}

	private static double fireAngle(Cylinder cylinder, int direction, int[] steps, boolean secondRevolution) {
		double tdc = cylinder.bore() - steps[cylinder.crank()] * THROW_STEP;
		return wrap(direction * tdc + (secondRevolution ? 360 : 0), CYCLE);
	}

	/** How bad a choice of throws and revolutions is: uneven spacing, cylinders that miss their shared lobe, neighbours in a row. */
	private static double cost(List<Cylinder> cylinders, int direction, int[] steps, boolean[] revolution) {
		int n = cylinders.size();
		double[] fire = new double[n];
		for (int i = 0; i < n; i++)
			fire[i] = fireAngle(cylinders.get(i), direction, steps, revolution[i]);

		double ideal = CYCLE / n;
		double cost = 0;
		for (double gap : intervals(fire)) {
			double off = (gap - ideal) / ideal;
			cost += off * off;
		}

		// Cylinders on one lobe have to fire together, because the lobe has one angle
		int[] first = new int[n];
		Arrays.fill(first, -1);
		for (int i = 0; i < n; i++) {
			int lobe = cylinders.get(i)
				.lobe();
			if (lobe < 0)
				continue;
			for (int j = 0; j < i; j++)
				if (cylinders.get(j)
					.lobe() == lobe) {
					if (Math.abs(wrap(fire[i] - fire[j], CYCLE)) > 1e-6)
						cost += MISTIMED_COST;
					break;
				}
		}

		if (n > 2) {
			Integer[] order = new Integer[n];
			for (int i = 0; i < n; i++)
				order[i] = i;
			Arrays.sort(order, Comparator.comparingDouble(i -> fire[i]));
			int[] rank = new int[n];
			for (int i = 0; i < n; i++)
				rank[order[i]] = i;
			for (int i = 0; i + 1 < n; i++) {
				int distance = Math.abs(rank[i] - rank[i + 1]);
				distance = Math.min(distance, n - distance);
				cost += NEIGHBOUR_WEIGHT / Math.max(1, distance);
			}
		}
		return cost;
	}

	private static double wrap(double degrees, double period) {
		double wrapped = degrees % period;
		return wrapped < 0 ? wrapped + period : wrapped;
	}
}
