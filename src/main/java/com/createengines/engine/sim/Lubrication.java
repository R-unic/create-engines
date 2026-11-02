package com.createengines.engine.sim;

/**
 * How well oiled an engine's moving parts are.
 * <p>
 * Every bearing, piston and rod needs a film of oil. Turning uses some of it up, in proportion to how far the parts
 * turn, and the engine draws it back from its {@link OilSupply}. While the supply keeps up the film builds to full;
 * when it doesn't, the film thins as the engine turns, and is gone after a few hundred revolutions. A dry engine isn't
 * stopped, but metal on metal drags: friction is higher, and it rises far faster with speed than it does with oil, so
 * a dry engine tops out at a fraction of its speed and has far less torque to spare.
 */
public final class Lubrication {

	/** Oil one moving part uses per crank revolution at full film, mB. */
	public static final double OIL_PER_PART_REV = 0.01;
	/** Film gained per second while the supply keeps up. */
	private static final double RISE_PER_SECOND = 0.25;
	/** Film lost per revolution with no oil to replace it. */
	private static final double DECAY_PER_REV = 1 / 500d;
	/** Extra speed-independent friction, as a multiple of normal, for a completely dry part. */
	private static final double DRY_STATIC = 1;
	/** Extra speed-dependent friction, as a multiple of normal, for a completely dry part. */
	private static final double DRY_VISCOUS = 3;

	/** Most the oil can be thinned by fuel washing past the rings: it still lubricates, at half strength. */
	public static final double MAX_DILUTION = 0.5;

	/** Condition lost per crank revolution at ordinary stress: a full change of oil is spent in about 3000 turns. */
	public static final double AGE_PER_REV = 1 / 3000d;
	/**
	 * Friction added to a completely spent oil, as a share of normal. Spent oil never stops an engine or harms it, it only
	 * drags a little: fresh oil takes the drag away again.
	 */
	public static final double MAX_AGED_FRICTION = 0.25;
	/** How much oil makes up an engine's charge, mB: changing this much of it in one go restores most of the condition. */
	public static final double CHARGE_VOLUME = 150;
	/** A filter makes the oil age this much more slowly, and cleans fuel out of it this much faster. */
	public static final double FILTER_AGEING = 0.5;
	public static final double FILTER_CLEANING = 2;

	private double film;
	private double dilution;
	private double condition = 1;

	public Lubrication(double film) {
		this.film = Math.clamp(film, 0, 1);
	}

	/** Oil film from 0 (dry) to 1 (fully filmed), before fuel thins it. */
	public double film() {
		return film;
	}

	public void setFilm(double film) {
		this.film = Math.clamp(film, 0, 1);
	}

	/** How much of the oil's strength unburned fuel has taken, 0 to {@link #MAX_DILUTION}. */
	public double dilution() {
		return dilution;
	}

	public void setDilution(double dilution) {
		this.dilution = Math.clamp(dilution, 0, MAX_DILUTION);
	}

	/** Unburned fuel washing the film off the cylinder walls and into the oil. */
	public void dilute(double amount) {
		setDilution(dilution + amount);
	}

	/** Fuel boiling back out of the oil once the engine burns what it is given. */
	public void boilOff(double amount) {
		setDilution(dilution - amount);
	}

	/** How fresh the oil is, from 0 (spent) to 1 (new). Heat, fuel and hard running spend it; changing the oil restores it. */
	public double condition() {
		return condition;
	}

	public void setCondition(double condition) {
		this.condition = Math.clamp(condition, 0, 1);
	}

	/**
	 * Spends some of the oil's condition.
	 *
	 * @param revolutions crank revolutions turned
	 * @param stress      how hard the oil is worked: 1 for a warm engine burning cleanly, more when it runs hot or the oil
	 *                    is thinned by fuel, less with a filter
	 */
	public void age(double revolutions, double stress) {
		if (!Tuning.oilAgeing) {
			condition = 1;
			return;
		}
		setCondition(condition - revolutions * AGE_PER_REV * stress);
	}

	/** How hard the oil is being worked at this temperature (0 ambient, 1 normal running): hot oil and thinned oil age faster. */
	public double stress(double temperature, boolean filtered) {
		double stress = 1 + 1.5 * Math.max(0, temperature - Thermal.NORMAL) + 2 * dilution;
		return filtered ? stress * FILTER_AGEING : stress;
	}

	/**
	 * Swaps {@code mb} of the oil for fresh: the spent share leaves and new oil takes its place, so the condition closes on
	 * new by a share of the gap that grows with how much was changed. Fresh oil is clean, so fuel in it goes too.
	 */
	public void refresh(double mb) {
		if (mb <= 0)
			return;
		double share = 1 - Math.exp(-mb / CHARGE_VOLUME);
		setCondition(condition + (1 - condition) * share);
		setDilution(dilution * (1 - share));
	}

	/** Multiplier on all friction from the oil's condition: 1 when new, up to {@link #MAX_AGED_FRICTION} more when spent. */
	public double drag() {
		return 1 + MAX_AGED_FRICTION * (1 - condition);
	}

	/** What the parts actually feel: the film, less the strength fuel has taken from it. */
	public double effective() {
		return film * (1 - dilution);
	}

	/** Multiplier on speed-independent friction at this film. */
	public static double staticFactor(double film) {
		return 1 + DRY_STATIC * (1 - film);
	}

	/** Multiplier on speed-dependent friction at this film. */
	public static double viscousFactor(double film) {
		return 1 + DRY_VISCOUS * (1 - film);
	}

	/**
	 * Uses up oil for the turning done this tick and draws it back from {@code supply}.
	 *
	 * @param revolutions crank revolutions this tick
	 * @param seconds     length of the tick
	 * @param parts       how many moving parts need oil
	 */
	public void update(double revolutions, double seconds, double parts, OilSupply supply) {
		double demand = parts * OIL_PER_PART_REV * revolutions;
		if (demand <= 0)
			return;
		double satisfied = Math.min(1, supply.draw(demand) / demand);
		if (film < satisfied)
			film = Math.min(satisfied, film + RISE_PER_SECOND * seconds);
		else
			film = Math.max(satisfied, film - DECAY_PER_REV * revolutions);
	}
}
