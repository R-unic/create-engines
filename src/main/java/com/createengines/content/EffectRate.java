package com.createengines.content;

import net.minecraft.world.level.Level;

/**
 * Caps how often one effect is played, so a fast engine isn't a wall of sound and smoke. An engine's bangs and puffs
 * come once a cycle, which is a few a second at Create's usual speeds but hundreds a second per cylinder once it is
 * allowed to turn thousands of RPM: far more than the sound engine will play or the eye can tell apart, and each
 * costs a sound instance and a handful of particles.
 * <p>
 * Counted in game ticks, so the same however fast the client draws frames.
 */
public final class EffectRate {

	/** Ticks between bangs from one cylinder: the most a cylinder is heard to fire is about seven times a second. */
	public static final int BANG_INTERVAL = 3;
	/** Ticks between puffs of exhaust smoke from one cylinder. */
	public static final int SMOKE_INTERVAL = 2;

	private final int interval;
	private long last = Long.MIN_VALUE / 2;

	public EffectRate(int interval) {
		this.interval = interval;
	}

	/** Whether the effect may play now; if it may, this counts as it playing. */
	public boolean allow(Level level) {
		long now = level.getGameTime();
		if (now - last < interval)
			return false;
		last = now;
		return true;
	}
}
