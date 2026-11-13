package com.createengines.engine.sim;

/**
 * How hot an engine's block is, in units where ambient is 0 and a warm engine on a steady load is {@link #NORMAL}.
 * <p>
 * The cylinders put heat in, in proportion to what leaves their exhaust valves; the block loses some to the air in
 * proportion to how hot it is, and a coolant supply carries away more of whatever is above its thermostat. Nothing here
 * can hurt an engine. There are two soft effects instead: a cold engine drags (thick oil, tight fits) until it has
 * warmed through, and a cool one breathes better, as denser air in the cylinders. A hot engine is simply not cool.
 * <p>
 * Coolant is spent carrying heat, like water boiling off a hopper-cooled engine, so it is topped up from a tank and
 * needs no radiator.
 */
public final class Thermal {

	/** Temperature of a warm engine under steady load, with no coolant. Cooler than this earns the bonus. */
	public static final double NORMAL = 0.7;
	/** Below this the engine is cold and drags. */
	public static final double COLD_BELOW = 0.3;
	/** Friction added to a completely cold engine, as a share of normal. */
	public static final double MAX_COLD_FRICTION = 0.25;
	/** Coolant does nothing below this temperature, so a cooled engine still warms up, and never runs cold. */
	public static final double THERMOSTAT = 0.4;
	/** The temperature a cooled engine settles at or below, and where the whole bonus is earned. */
	public static final double COOL_FULL = 0.45;
	/** Warmer than this earns nothing: an engine on a steady load with no coolant is at {@link #NORMAL}, a little above. */
	public static final double COOL_NONE = 0.65;
	/** Most the charge's temperature drops, as a share of ambient, in a thoroughly cool engine. */
	public static final double MAX_COOL_CHARGE = 0.03;

	/**
	 * Heat lost to the air per second per cylinder per unit of temperature squared: a hotter block sheds heat faster than
	 * a cooler one, so the temperature an engine settles at rises with the square root of the heat it makes, and a slow
	 * engine is not left stone cold nor a fast one white hot. Sized so a cylinder sending 2.5 out of its exhaust (a crude
	 * engine at 100 RPM or so) settles at {@link #NORMAL}.
	 */
	static final double PASSIVE = 2.5 / (NORMAL * NORMAL);
	/** Heat it takes to warm one cylinder's share of the block by one unit. */
	static final double CAPACITY = 60;
	/** Heat the best coolant carries off per second per cylinder per unit above the thermostat. */
	static final double COOLANT_CONDUCTANCE = 100;
	/**
	 * Coolant spent per unit of heat it carries, mB, at the best quality. Sized so a cooled engine burns through coolant at
	 * a fifth or so of the rate it burns fuel: one cylinder spends a pan's 500 mB in about half an hour, and a big engine
	 * in minutes, so the pan has to be fed and not filled once.
	 */
	static final double COOLANT_PER_HEAT = 0.15;
	/** Heat in per second per cylinder, for each unit of heat the exhaust carries out per second. */
	static final double HEAT_SHARE = 1;

	/**
	 * Share of the heat that goes into the block which comes from friction rather than the exhaust. Rubbing parts warm an
	 * engine whatever it burns, so a lightly fuelled one still warms through; the share is traded against the exhaust's, and
	 * friction and exhaust heat are of the same size at the speeds {@link #PASSIVE} was sized for, so a full-throttle
	 * engine settles where it always did.
	 */
	static final double FRICTION_SHARE = 0.35;

	/** Share of a fuel's heat release a stone-cold block quenches, for a fuel with a {@link FuelSpec#coldSensitivity()} of 1. */
	public static final double MAX_COLD_BURN_LOSS = 0.12;
	/** Below this a stopped block has given up its heat: nothing ticks, shimmers or glows any more. */
	public static final double COOLING_FROM = 0.12;
	/** Temperature at which exhaust parts begin to glow dull red, and the span over which the glow comes up to full. */
	public static final double GLOW_FROM = 0.5;
	public static final double GLOW_SPAN = 0.35;
	/** Extra heat a cold block makes at idle, as a share of what it was making: a choke, in effect. */
	public static final double WARMUP_ASSIST = 0.8;
	/** Heat per cylinder per second at which the warm-up assist has faded out: about a cylinder's share at full load. */
	static final double FULL_LOAD_HEAT = 2.5;
	/** Heat per cylinder per second a heat source touching the engine puts in, by heat level (none, smouldering, kindled, seething). */
	private static final double[] PREHEAT = {0, 0.4, 0.9, 1.6};

	/** The heat per second that goes into the block, from what leaves the exhaust and the power lost to friction. */
	public static double heatIn(double exhaustHeat, double frictionPower) {
		return exhaustHeat * (1 - FRICTION_SHARE) + frictionPower * FRICTION_SHARE;
	}

	private double temperature;
	/** Coolant spent per second over the last tick, for goggles. */
	private double coolantRate;
	private double coolantQuality;
	/** How much of the cold a starter is choking out of the engine, 0 to 1 (see {@link #setCrankAssist}). */
	private double crankAssist;
	/** Heat per cylinder per second an outside heat source is putting in (see {@link #setPreheat}). */
	private double preheat;

	/** How much of a fuel's burn a block this cold costs: 1 when warm. */
	public static double burnFactor(double cold, double sensitivity) {
		return Math.max(0.5, 1 - MAX_COLD_BURN_LOSS * cold * sensitivity);
	}

	/** Whether a block at {@code temperature} that is not turning is still giving up heat, which goggles call cooling. */
	public static boolean isCooling(double temperature, boolean turning) {
		return !turning && temperature >= COOLING_FROM;
	}

	/** How red-hot exhaust parts look at {@code temperature}, 0 (dark metal) to 1. */
	public static double glow(double temperature) {
		return Math.clamp((temperature - GLOW_FROM) / GLOW_SPAN, 0, 1);
	}

	/**
	 * Chance per tick of a cooling block ticking as the metal contracts: it is quick while the block is hot, and sparser the
	 * closer to cold it gets. Zero once it has cooled.
	 */
	public static double tickChance(double temperature) {
		if (temperature < COOLING_FROM)
			return 0;
		return 0.004 + 0.045 * Math.clamp((temperature - COOLING_FROM) / (NORMAL - COOLING_FROM), 0, 1);
	}

	/** A starter that warms cold engines is cranking, or has just stopped: that share of the cold is not felt. */
	public void setCrankAssist(double assist) {
		crankAssist = Math.clamp(assist, 0, 1);
	}

	public double crankAssist() {
		return crankAssist;
	}

	/** Heat level of a heat source touching the engine, {@link HeatRecovery#NONE} to {@link HeatRecovery#SEETHE}. */
	public void setPreheat(int level) {
		preheat = PREHEAT[Math.clamp(level, 0, PREHEAT.length - 1)];
	}

	/** Heat per cylinder per second an outside source is putting in now. */
	public double preheat() {
		return preheat;
	}

	public Thermal(double temperature) {
		this.temperature = Math.max(0, temperature);
	}

	public double temperature() {
		return temperature;
	}

	public void setTemperature(double temperature) {
		this.temperature = Math.max(0, temperature);
	}

	/** Coolant being spent now, mB per second. */
	public double coolantRate() {
		return coolantRate;
	}

	/** How well the coolant in use carries heat: 0 when there is none (or it has run out). */
	public double coolantQuality() {
		return coolantQuality;
	}

	/**
	 * Advances by {@code dt} seconds.
	 *
	 * @param exhaustHeat heat leaving the exhaust valves per second, summed over every cylinder
	 * @param cylinders   how many cylinders (and so how much block) share it, at least one
	 */
	public void update(double exhaustHeat, int cylinders, double dt, CoolantSupply coolant) {
		int n = Math.max(1, cylinders);
		double light = Math.clamp(1 - exhaustHeat / (n * FULL_LOAD_HEAT), 0, 1);
		double in = exhaustHeat * HEAT_SHARE * (1 + WARMUP_ASSIST * cold() * light) + preheat * n;
		double out = PASSIVE * n * temperature * temperature;
		coolantRate = 0;
		coolantQuality = 0;
		double above = temperature - THERMOSTAT;
		double quality = coolant.quality();
		if (above > 0 && quality > 0) {
			double carried = COOLANT_CONDUCTANCE * n * quality * above;
			double wanted = carried * COOLANT_PER_HEAT / quality * dt;
			double given = wanted <= 0 ? 0 : coolant.draw(wanted);
			double share = wanted <= 0 ? 0 : Math.min(1, given / wanted);
			out += carried * share;
			coolantRate = given / dt;
			coolantQuality = share > 0 ? quality : 0;
		}
		temperature = Math.max(0, temperature + (in - out) * dt / (CAPACITY * n));
	}

	/** How cold the engine is, 0 (warm) to 1 (as cold as it gets). */
	public double cold() {
		return Tuning.engineTemperature ? Math.clamp((COLD_BELOW - temperature) / COLD_BELOW, 0, 1) : 0;
	}

	/** How cold the engine behaves: {@link #cold()}, less whatever a starter is choking out of it. */
	public double felt() {
		return cold() * (1 - crankAssist);
	}

	/**
	 * How much of the cooling bonus the engine has earned, 0 to 1: nothing at {@link #COOL_NONE} or warmer, all of it at
	 * {@link #COOL_FULL}. A cold engine has not earned it either, so it fades out between the point where it stops dragging and the thermostat.
	 */
	public double cool() {
		if (!Tuning.engineTemperature)
			return 0;
		double cooled = Math.clamp((COOL_NONE - temperature) / (COOL_NONE - COOL_FULL), 0, 1);
		double warmed = Math.clamp((temperature - COLD_BELOW) / (THERMOSTAT - COLD_BELOW), 0, 1);
		return cooled * warmed;
	}

	/** Multiplier on all friction: a cold engine drags. */
	public double drag() {
		return 1 + MAX_COLD_FRICTION * felt();
	}

	/** Multiplier on the temperature of the air the cylinders draw: a cool engine pulls in a denser charge. */
	public double chargeFactor() {
		return 1 - MAX_COOL_CHARGE * cool() * Tuning.coolingBonus;
	}
}
