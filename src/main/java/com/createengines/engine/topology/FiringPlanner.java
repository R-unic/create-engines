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
 * A lobe that works several cylinders (a cam in the valley of a vee) times the first of them by its angle and the others
 * at the same offset from their own top dead centres, on whichever turn puts them within one turn after the first. They
 * have no revolution of their own to choose: where they fire follows from where the first does.
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
	 */
	public record Plan(double[] throwAngles, double[] fireAngles, double[] lobeAngles, int[] order, double deviation) {
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
			return new Plan(currentThrows.clone(), new double[0], lobes.clone(), new int[0], 0);

		int[] leaders = leaders(cylinders);
		int[] free = freeCylinders(leaders);
		int[] options = optionsFor(style);
		State best = null;
		if (style == Style.CAMS_ONLY && n <= EXHAUSTIVE_LIMIT) {
			int[] steps = new int[t];
			for (int i = 0; i < t; i++)
				steps[i] = angleStep(currentThrows[i]);
			boolean[] revolutions = new boolean[n];
			for (long mask = 0; mask < 1L << free.length; mask++) {
				for (int k = 0; k < free.length; k++)
					revolutions[free[k]] = (mask >> k & 1) == 1;
				double cost = cost(cylinders, leaders, direction, steps, revolutions);
				if (best == null || cost < best.cost) {
					best = new State(steps.clone(), revolutions.clone(), cost);
					if (cost < 1e-12)
						break;
				}
			}
		} else {
			Random random = new Random(31L * n + 7L * t + style.ordinal());
			for (int restart = 0; restart < RESTARTS && (best == null || best.cost > 1e-9); restart++) {
				State candidate = anneal(style, options, direction, currentThrows, cylinders, leaders, free, random);
				if (best == null || candidate.cost < best.cost)
					best = candidate;
			}
		}

		double[] throwAngles = new double[t];
		for (int i = 0; i < t; i++)
			throwAngles[i] = best.steps[i] * THROW_STEP;
		double[] fire = fires(cylinders, leaders, direction, best.steps, best.revolution);

		double[] lobeAngles = lobes.clone();
		for (int i : free) {
			int lobe = cylinders.get(i)
				.lobe();
			if (lobe >= 0 && lobe < lobes.length)
				lobeAngles[lobe] = fire[i] / 2;
		}

		Integer[] order = new Integer[n];
		for (int i = 0; i < n; i++)
			order[i] = i;
		Arrays.sort(order, Comparator.comparingDouble(i -> fire[i]));
		int[] firingOrder = new int[n];
		for (int i = 0; i < n; i++)
			firingOrder[i] = order[i];
		return new Plan(throwAngles, fire, lobeAngles, firingOrder, deviation(fire));
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

	/**
	 * For each cylinder, the first cylinder on the same lobe, which is the one the lobe's angle times; a cylinder that
	 * is the first on its lobe, or has no lobe, is its own.
	 */
	private static int[] leaders(List<Cylinder> cylinders) {
		int n = cylinders.size();
		int[] leaders = new int[n];
		for (int i = 0; i < n; i++) {
			leaders[i] = i;
			int lobe = cylinders.get(i)
				.lobe();
			for (int j = 0; lobe >= 0 && j < i; j++)
				if (cylinders.get(j)
					.lobe() == lobe) {
					leaders[i] = j;
					break;
				}
		}
		return leaders;
	}

	/** The cylinders that choose their own revolution: every one that leads its lobe. */
	private static int[] freeCylinders(int[] leaders) {
		return java.util.stream.IntStream.range(0, leaders.length)
			.filter(i -> leaders[i] == i)
			.toArray();
	}

	private static State anneal(Style style, int[] options, int direction, double[] current, List<Cylinder> cylinders,
								int[] leaders, int[] free, Random random) {
		int n = cylinders.size();
		int t = current.length;
		int[] steps = new int[t];
		for (int i = 0; i < t; i++)
			steps[i] = style == Style.CAMS_ONLY ? angleStep(current[i]) : options[random.nextInt(options.length)];
		boolean[] revolution = new boolean[n];
		for (int i = 0; i < n; i++)
			revolution[i] = random.nextBoolean();

		double cost = cost(cylinders, leaders, direction, steps, revolution);
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
				flipped = free[random.nextInt(free.length)];
				revolution[flipped] = !revolution[flipped];
			}
			double next = cost(cylinders, leaders, direction, steps, revolution);
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

	private static double topDeadCentre(Cylinder cylinder, int[] steps) {
		return cylinder.bore() - steps[cylinder.crank()] * THROW_STEP;
	}

	/**
	 * The rotor angle each cylinder fires at. A cylinder that leads its lobe picks its revolution; one that shares a lobe
	 * fires as far after its leader as its top dead centre is after the leader's, within one crank turn.
	 */
	private static double[] fires(List<Cylinder> cylinders, int[] leaders, int direction, int[] steps,
								  boolean[] revolution) {
		int n = cylinders.size();
		double[] fire = new double[n];
		for (int i = 0; i < n; i++) {
			Cylinder cylinder = cylinders.get(i);
			double tdc = topDeadCentre(cylinder, steps);
			if (leaders[i] == i)
				fire[i] = wrap(direction * tdc + (revolution[i] ? 360 : 0), CYCLE);
			else
				fire[i] = wrap(fire[leaders[i]]
					+ wrap(direction * (tdc - topDeadCentre(cylinders.get(leaders[i]), steps)), 360), CYCLE);
		}
		return fire;
	}

	/** How bad a choice of throws and revolutions is: uneven spacing, and neighbours firing in a row. */
	private static double cost(List<Cylinder> cylinders, int[] leaders, int direction, int[] steps,
							   boolean[] revolution) {
		int n = cylinders.size();
		double[] fire = fires(cylinders, leaders, direction, steps, revolution);

		double ideal = CYCLE / n;
		double cost = 0;
		for (double gap : intervals(fire)) {
			double off = (gap - ideal) / ideal;
			cost += off * off;
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
