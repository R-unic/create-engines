package com.createengines.content.effects;

/**
 * The look of a turbocharger dumping its boost, as numbers: a burst when the valve opens, then a jet that thins out over
 * the next few ticks ("psssht" rather than one puff), longer and fuller for more boost; and the small puff of the wastegate
 * swinging open to hold the limit. Pure arithmetic so it can be tested, with the particles themselves left to the block.
 * Particles only: nothing here is read by the simulation.
 */
public final class VentJet {

	/** Boost beyond which a bigger charge looks no different. */
	public static final float MAX_BOOST = 1.5f;
	public static final int MIN_TICKS = 6;
	public static final int MAX_TICKS = 18;

	private VentJet() {
	}

	private static float clamped(float boost) {
		return Float.isNaN(boost) ? 0f : Math.clamp(boost, 0f, MAX_BOOST);
	}

	/** Puffs in the burst at the moment the valve opens: 8 for none to 17 at the most. */
	public static int burst(float boost) {
		return 8 + Math.round(6 * clamped(boost));
	}

	/** Ticks the jet carries on after the burst: 6 for a little boost to 18 for a lot. */
	public static int duration(float boost) {
		return Math.clamp(Math.round(6 + 8 * clamped(boost)), MIN_TICKS, MAX_TICKS);
	}

	/** How much of the jet is left, 1 when it starts to 0 when it is over. */
	public static float strength(int remaining, int total) {
		return total <= 0 ? 0f : Math.clamp(remaining / (float) total, 0f, 1f);
	}

	/** Puffs to throw this tick: two while the jet is strong, one as it dies away, none when it is over. */
	public static int perTick(int remaining, int total) {
		if (remaining <= 0 || total <= 0)
			return 0;
		return strength(remaining, total) > 0.5f ? 2 : 1;
	}

	/** Puffs in the small chuff of the wastegate opening to hold the limit: 2 to 4. */
	public static int chuff(float boost) {
		return 2 + Math.round(2 * Math.min(1f, clamped(boost)));
	}
}
