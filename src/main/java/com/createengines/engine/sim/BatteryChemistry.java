package com.createengines.engine.sim;

/**
 * What a battery's charge is worth, as plain numbers: how a cold battery gives less, how a cold engine makes each spark
 * cost more, how a starter's draw falls as a bank runs down, and the levels a comparator and a gauge show. No Minecraft types
 * here, so it is unit tested; {@code BatteryBlockEntity}, {@code BatteryIgnition} and {@code EngineController} apply it.
 * <p>
 * Charge is counted in sparks, as everywhere else: a warm engine's plug takes {@link #SPARK_COST} of it every time it fires.
 */
public final class BatteryChemistry {

	/** Sparks one battery holds. */
	public static final float CAPACITY = 200;
	/** Charge a plug takes for one spark in a warm engine. */
	public static final float SPARK_COST = 1;
	/** How much more a spark costs in an engine as cold as it gets: 1 doubles it. See {@link #sparkCost}. */
	public static final double COLD_SPARK_COST = 1;
	/** The share of its capacity a battery cannot deliver when it is as cold as it gets. The charge is still in it; warming it gives it back. */
	public static final double MAX_COLD_LOSS = 0.5;
	/** Biomes at or above this temperature are not cold to a battery; vanilla's plains are 0.8 and a snowy plain is 0. */
	public static final double COMFORTABLE_BIOME = 0.5;
	/** Charge a starter takes each tick it is turning an engine over, in a warm engine, from a bank above {@link #SAG_BELOW}. */
	public static final double CRANK_DRAW = 0.15;
	/**
	 * Below this share of capacity (20 sparks of 200) a bank is low: a starter draws less from it, in proportion, down to nothing
	 * from an empty one, and says so in its goggles. The starter is not slowed: it is driven by a shaft, and an engine that
	 * catches at its full cranking speed would not at a lower one.
	 */
	public static final double SAG_BELOW = 0.10;
	/** How far past the edge of a stage the charge must go before the lamp strip moves to the next. */
	public static final double GAUGE_HOLD = 1.0 / 64;
	/** The most a comparator reads. */
	public static final int MAX_SIGNAL = 15;
	/** Stages of the lamp strip on a battery's side: 0 (flat) to this (full). */
	public static final int GAUGE_STAGES = 4;

	/** How much of a heat source's warmth a battery beside it feels, by Create heat level: none, smouldering, kindled, seething. */
	private static final double[] WARMTH = {0, 0.4, 0.8, 1};

	private BatteryChemistry() {
	}

	/** The charge one spark takes from a battery when the engine is {@code cold} (0 warm, 1 as cold as it gets): 1 warm, up to 2 as cold as it gets. */
	public static double sparkCost(double cold, boolean enabled) {
		return enabled ? SPARK_COST * (1 + COLD_SPARK_COST * Math.clamp(cold, 0, 1)) : SPARK_COST;
	}

	/** How cold the air round a battery is, 0 to 1, from its biome's temperature: 0 from {@link #COMFORTABLE_BIOME} up, 1 at freezing and below. */
	public static double ambientCold(double biomeTemperature) {
		if (Double.isNaN(biomeTemperature))
			return 0;
		return Math.clamp((COMFORTABLE_BIOME - biomeTemperature) / COMFORTABLE_BIOME, 0, 1);
	}

	/** The share of the cold a heat source beside a battery takes off it, 0 to 1, for a Create heat level 0 (none) to 3 (seething). */
	public static double warmth(int heatLevel) {
		return WARMTH[Math.clamp(heatLevel, 0, WARMTH.length - 1)];
	}

	/** How cold a battery is: its air's cold, less what a heat source beside it makes up. */
	public static double coldness(double biomeTemperature, int heatLevel) {
		return ambientCold(biomeTemperature) * (1 - warmth(heatLevel));
	}

	/** The most charge a battery of {@code capacity} can give while it is {@code cold}: its capacity less the share the cold keeps. */
	public static double deliverableLimit(double capacity, double cold, boolean enabled) {
		return deliverableLimit(capacity, cold, enabled, MAX_COLD_LOSS);
	}

	/** The same for a battery that loses {@code coldLoss} (0 to 1) of its capacity when as cold as it gets: see {@link BatteryTier#coldLoss()}. */
	public static double deliverableLimit(double capacity, double cold, boolean enabled, double coldLoss) {
		return enabled ? capacity * (1 - Math.clamp(coldLoss, 0, 1) * Math.clamp(cold, 0, 1)) : capacity;
	}

	/** The charge a battery holding {@code charge} can actually give now. */
	public static double deliverable(double charge, double capacity, double cold, boolean enabled) {
		return Math.max(0, Math.min(charge, deliverableLimit(capacity, cold, enabled)));
	}

	/** The charge a battery of a tier that loses {@code coldLoss} to the cold, holding {@code charge}, can actually give now. */
	public static double deliverable(double charge, double capacity, double cold, boolean enabled, double coldLoss) {
		return Math.max(0, Math.min(charge, deliverableLimit(capacity, cold, enabled, coldLoss)));
	}

	/** Whether a bank holding {@code deliverableShare} of its capacity is low enough to hold a starter back: below {@link #SAG_BELOW}, or not a number. */
	public static boolean isLow(double deliverableShare) {
		return !(deliverableShare >= SAG_BELOW);
	}

	/**
	 * The charge a starter takes this tick, in an engine {@code cold} (0 to 1) from a bank holding {@code deliverableShare} of its
	 * capacity: more to turn a cold engine over, and less as the bank runs down, to nothing from an empty one, as a starter draws
	 * only the current there is to give. That is what lets an alternator on the crank line fill a flat bank by cranking: the
	 * draw can never take all of what little it makes, since it falls with the bank.
	 */
	public static double crankDraw(double cold, double deliverableShare) {
		if (Double.isNaN(deliverableShare))
			return 0;
		return CRANK_DRAW * (1 + Math.clamp(cold, 0, 1)) * Math.clamp(deliverableShare / SAG_BELOW, 0, 1);
	}

	/** A comparator's reading for a bank holding {@code share} of its capacity: 0 only when it is empty, 15 only when it is full. */
	public static int signal(double share) {
		if (!(share > 0))
			return 0;
		if (share >= 1)
			return MAX_SIGNAL;
		return Math.clamp((int) Math.floor(share * (MAX_SIGNAL - 1)) + 1, 1, MAX_SIGNAL - 1);
	}

	/** The lamp strip stage for a battery holding {@code share} of its capacity: 0 only when it is empty, {@link #GAUGE_STAGES} only when it is full. */
	public static int gaugeStage(double share) {
		if (!(share > 0))
			return 0;
		if (share >= 1)
			return GAUGE_STAGES;
		return Math.clamp((int) Math.floor(share * (GAUGE_STAGES - 1)) + 1, 1, GAUGE_STAGES - 1);
	}

	/**
	 * The stage to show when the lamp strip is at {@code current} and the charge is {@code share}: the stage {@link #gaugeStage(double)}
	 * gives, except that the strip stays where it is while the charge is within {@link #GAUGE_HOLD} of that stage's edge, so a
	 * battery drawing one spark at a time on a boundary does not flicker.
	 */
	public static int gaugeStage(double share, int current) {
		int stage = gaugeStage(share);
		if (stage == current || Math.abs(stage - current) != 1 || current <= 0 || current >= GAUGE_STAGES || stage <= 0
			|| stage >= GAUGE_STAGES)
			return stage;
		// The edge between two neighbouring stages sits where the lower one ends
		double edge = (double) Math.min(stage, current) / (GAUGE_STAGES - 1);
		return Math.abs(share - edge) < GAUGE_HOLD ? current : stage;
	}

	/** Ticks the charge lasts at {@code perTick} a tick, or {@link Double#POSITIVE_INFINITY} when nothing is being drawn. */
	public static double ticksLeft(double charge, double perTick) {
		if (!(perTick > 1e-9))
			return Double.POSITIVE_INFINITY;
		return Math.max(0, charge) / perTick;
	}
}
