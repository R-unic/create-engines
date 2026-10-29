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

	private double film;

	public Lubrication(double film) {
		this.film = Math.clamp(film, 0, 1);
	}

	/** Oil film from 0 (dry) to 1 (fully filmed). */
	public double film() {
		return film;
	}

	public void setFilm(double film) {
		this.film = Math.clamp(film, 0, 1);
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
