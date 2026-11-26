package com.createengines.engine.sim;

/**
 * How hot an engine's block is, in units where ambient is 0 and a warm engine on a steady load is {@link #NORMAL}.
 * <p>
 * The cylinders put heat in, in proportion to the fuel they burn, and friction a little more; the block loses some to the
 * air in proportion to how hot it is, and a coolant supply carries away more of whatever is above its thermostat. A player
 * reads the temperature in degrees (see {@link #degrees}). Nothing here breaks an engine (the wear mode's heat damage is
 * {@link Wear}'s). The effects are soft: a cold engine drags (thick oil, tight fits) until it has warmed through, a cool one
 * breathes better, as denser air in the cylinders, and an overheating one gets less out of each burn.
 * <p>
 * Coolant is spent carrying heat, like water boiling off a hopper-cooled engine, so it is topped up from a tank. A
 * {@link Radiator} is the other way: a closed loop that sheds heat to the air.
 */
public final class Thermal {

	/** Temperature of a warm engine under steady load, with no coolant. Cooler than this earns the bonus. */
	public static final double NORMAL = 0.7;
	/** From here a block is hot: with the wear mode on, heat starts to wear parts far faster. */
	public static final double HOT = 1.0;
	/** From here a block is overheating: it warms the charge it breathes, which costs power (see {@link #overheat}). */
	public static final double OVERHEATING = 1.2;
	/** From here a block is critical: the overheating loss is at its most, and with the wear mode on heat alone breaks things. */
	public static final double CRITICAL = 1.45;
	/** The share of each burn's heat lost in a block at {@link #CRITICAL}, with the penalty at 1. */
	public static final double MAX_HOT_BURN_LOSS = 0.18;
	/** How much nearer its pre-ignition temperature a boosted spark charge is in a block at {@link #CRITICAL}, with the penalty at 1. */
	public static final double MAX_HOT_PREIGNITION = 0.3;
	/** The temperature a gauge shows for an ambient block, and how many degrees each unit of temperature is: warm reads 90 degrees. */
	private static final double AMBIENT_DEGREES = 20;
	private static final double DEGREES_PER_UNIT = 100;
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
	 * How much of the heat of the fuel burned in the cylinders goes into the block, in the block's own units: what does not leave as work
	 * or down the exhaust. More boost, more fuel and a fuel with a higher {@code heatFactor} all burn more, so heat follows the work
	 * asked of the engine; a lean or lightly fuelled one runs cooler. Sized so a crude engine near 100 RPM at full rack settles where it
	 * did before heat followed the fuel, a little under {@link #NORMAL}.
	 */
	static final double BURN_SHARE = 0.0804;
	/**
	 * How much of the power lost to friction warms the block, up to {@link #FRICTION_KNEE} a cylinder: what it always was, so an engine
	 * turning slowly, however lightly fuelled, warms through as it did.
	 */
	static final double FRICTION_HEAT = 0.35;
	/** Friction power a cylinder past which more of it warms the block far less: about what a crude cylinder loses near 120 RPM. */
	static final double FRICTION_KNEE = 2.5;
	/**
	 * The share of the friction past the knee that warms the block. Before 2.4 all of it weighed alike, so a fast engine's heat was
	 * nearly all friction, and a crude four ran as hot as a boosted twenty-four.
	 */
	static final double FAST_FRICTION_HEAT = 0.1;
	/** For a source that is not a cylinder and says only what leaves its exhaust: the share of that which warms its block, as it always did. */
	static final double EXHAUST_HEAT = 0.65;
	/** How many times more heat friction makes with no oil at all than on an oiled part, on top of the extra drag. */
	static final double DRY_HEAT = 8;

	/** What the block's temperature reads as, from coldest to hottest. */
	public enum Band {
		COLD, COOL, WARM, HOT, OVERHEATING, CRITICAL;

		public String langKey() {
			return "goggles.temperature." + name().toLowerCase(java.util.Locale.ROOT);
		}
	}

	/** The band a block at {@code temperature} reads as. */
	public static Band band(double temperature) {
		if (temperature < COLD_BELOW)
			return Band.COLD;
		if (temperature < COOL_NONE)
			return Band.COOL;
		if (temperature < HOT)
			return Band.WARM;
		if (temperature < OVERHEATING)
			return Band.HOT;
		return temperature < CRITICAL ? Band.OVERHEATING : Band.CRITICAL;
	}

	/** A temperature as the whole degrees Celsius a gauge shows: ambient is 20, a warm engine 90. Nonsense reads as ambient. */
	public static int degrees(double temperature) {
		return (int) Math.round(AMBIENT_DEGREES + DEGREES_PER_UNIT * (Double.isFinite(temperature) ? Math.max(0, temperature) : 0));
	}

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

	/**
	 * The heat per second that goes into the block.
	 *
	 * @param burnHeat      the heat of the fuel burned in the cylinders a second
	 * @param otherExhaust  heat leaving the exhaust of sources that are not cylinders, a second
	 * @param frictionPower the power lost to friction
	 * @param cylinders     how many cylinders share the friction, at least one
	 */
	public static double heatIn(double burnHeat, double otherExhaust, double frictionPower, int cylinders) {
		double knee = FRICTION_KNEE * Math.max(1, cylinders);
		return burnHeat * BURN_SHARE + otherExhaust * EXHAUST_HEAT + Math.min(frictionPower, knee) * FRICTION_HEAT
			+ Math.max(0, frictionPower - knee) * FAST_FRICTION_HEAT;
	}

	/**
	 * How many times the usual heat friction makes on an oil film this thin: 1 until the film falls below {@link Wear#SEIZE_FILM} (the
	 * goggles' "failing: dry oil"), then rising to {@link #DRY_HEAT} more with none, so an engine starved of oil overheats before it
	 * seizes. With the wear mode off this is only a warning; with it on it is the road to seizing.
	 */
	public static double dryHeat(double film) {
		if (!Double.isFinite(film))
			return 1;
		return 1 + DRY_HEAT * Math.clamp(1 - film / Wear.SEIZE_FILM, 0, 1);
	}

	/**
	 * How far into overheating the block is, 0 at {@link #OVERHEATING} or cooler to 1 at {@link #CRITICAL} and hotter. Nothing with engine
	 * temperature switched off.
	 */
	public double overheat() {
		return Tuning.engineTemperature ? Math.clamp((temperature - OVERHEATING) / (CRITICAL - OVERHEATING), 0, 1) : 0;
	}

	private double temperature;
	/** Coolant spent per second over the last tick, for goggles. */
	private double coolantRate;
	private double coolantQuality;
	/** How much of the cold a starter is choking out of the engine, 0 to 1 (see {@link #setCrankAssist}). */
	private double crankAssist;
	/** Heat per cylinder per second an outside heat source is putting in (see {@link #setPreheat}). */
	private double preheat;

	private Radiator.Supply radiators = Radiator.NONE;

	/** The radiators joined to the engine, {@link Radiator#NONE} for none. */
	public void setRadiators(Radiator.Supply radiators) {
		this.radiators = radiators;
	}

	/**
	 * Lets each radiator take its share of the heat.
	 *
	 * @param most the most heat per second they may take between them: what would bring the block to the thermostat this tick
	 * @return the heat per second they took
	 */
	private double radiate(double most, double dt) {
		int count = radiators.count();
		if (count == 0)
			return 0;
		double wanted = 0;
		for (int i = 0; i < count; i++)
			wanted += Radiator.shed(temperature, radiators.quality(i), radiators.airflow(i));
		double scale = wanted <= 0 ? 0 : Math.min(1, Math.max(0, most) / wanted);
		for (int i = 0; i < count; i++) {
			double quality = radiators.quality(i);
			double shed = Radiator.shed(temperature, quality, radiators.airflow(i)) * scale;
			radiators.ran(i, shed, Radiator.boiled(temperature, shed, quality, dt));
		}
		return wanted * scale;
	}

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
		double pan = 0;
		double quality = coolant.quality();
		if (above > 0 && quality > 0) {
			double carried = COOLANT_CONDUCTANCE * n * quality * above;
			double wanted = carried * COOLANT_PER_HEAT / quality * dt;
			double given = wanted <= 0 ? 0 : coolant.draw(wanted);
			double share = wanted <= 0 ? 0 : Math.min(1, given / wanted);
			pan = carried * share;
			out += pan;
			coolantRate = given / dt;
			coolantQuality = share > 0 ? quality : 0;
		}
		// Many radiators on a small block must not carry it below the thermostat inside one tick
		out += radiate(Math.max(0, above) * CAPACITY * n / dt - pan, dt);
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

	/**
	 * How hot an overheating block runs its cylinders, 0 not at all to 1 the most: {@link #overheat} scaled by
	 * {@code Tuning.overheatPenalty}, 0 turning it off. Each burn loses up to {@link #MAX_HOT_BURN_LOSS} of its heat to it, and a boosted
	 * spark charge is nearer lighting early. Nothing breaks or stops, and it goes as the block cools.
	 */
	public double hotness() {
		return overheat() * Tuning.overheatPenalty;
	}

	/** Multiplier on the temperature of the air the cylinders draw: a cool engine pulls in a denser charge. */
	public double chargeFactor() {
		return 1 - MAX_COOL_CHARGE * cool() * Tuning.coolingBonus;
	}
}
