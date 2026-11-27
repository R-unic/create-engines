package com.createengines.content.effects;

import java.util.ArrayList;
import java.util.List;

/**
 * Keeps many blow-off valves opening together from adding up to a roar. Of those that open close together in time and
 * place, as all of one engine's do when it lifts off the throttle, the first is heard in full, the next two more softly
 * and any more not at all; between them they come to little more than one. One that opens somewhere else, or a moment
 * later, is heard in full. Sound only: every valve is still seen to open. Pure Java, so it is tested.
 */
public final class BlowOffCrowd {

	/** Openings this many ticks apart or more are heard as separate ones. */
	public static final int WINDOW = 10;
	/** Valves this many blocks apart or more are heard as separate ones. */
	public static final double NEAR = 16;
	private static final float[] SHARES = {1f, 0.5f, 0.3f};

	private record Heard(long tick, double x, double y, double z) {
	}

	private final List<Heard> recent = new ArrayList<>();

	/**
	 * A valve opening at {@code tick} at the given place: the share of its own volume to play it at, 0 for not at all. One
	 * that is to be played is remembered.
	 */
	public float share(long tick, double x, double y, double z) {
		// A tick before one remembered is another world's clock
		recent.removeIf(one -> tick < one.tick() || tick - one.tick() >= WINDOW);
		int others = 0;
		for (Heard one : recent) {
			double dx = one.x() - x;
			double dy = one.y() - y;
			double dz = one.z() - z;
			if (dx * dx + dy * dy + dz * dz < NEAR * NEAR)
				others++;
		}
		if (others >= SHARES.length)
			return 0;
		recent.add(new Heard(tick, x, y, z));
		return SHARES[others];
	}
}
