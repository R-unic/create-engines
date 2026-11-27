package com.createengines.content;

import java.util.HashMap;
import java.util.Map;

/**
 * Where an engine's note is heard from: the middle of its exhaust openings, each weighed by how loud it is, so the sound
 * comes from the pipe ends and a muffled one pulls it less than an open one. The cylinders report their openings as they
 * tick; one that stops reporting is forgotten. Positions are plain numbers, so this is pure Java and tested.
 */
final class ExhaustCentre {

	/** Ticks an opening is remembered after it was last reported. */
	private static final int FORGET_TICKS = 10;

	private static final class Opening {
		double x;
		double y;
		double z;
		float loudness;
		long seen;
	}

	private final Map<Long, Opening> openings = new HashMap<>();

	/**
	 * The opening {@code key} stands at {@code x, y, z} and is {@code loudness} loud now; reported by any cylinder it
	 * serves, so one shared by several counts once.
	 */
	void report(long key, double x, double y, double z, float loudness, long now) {
		Opening opening = openings.computeIfAbsent(key, $ -> new Opening());
		opening.x = x;
		opening.y = y;
		opening.z = z;
		opening.loudness = Float.isFinite(loudness) ? Math.max(0, loudness) : 0;
		opening.seen = now;
	}

	/** The middle of the openings heard of lately, as {x, y, z}, or null if there are none to be heard. */
	double[] centre(long now) {
		openings.values()
			.removeIf(opening -> now - opening.seen > FORGET_TICKS || now < opening.seen);
		double weight = 0;
		double x = 0;
		double y = 0;
		double z = 0;
		for (Opening opening : openings.values()) {
			weight += opening.loudness;
			x += opening.loudness * opening.x;
			y += opening.loudness * opening.y;
			z += opening.loudness * opening.z;
		}
		return weight > 0 ? new double[] {x / weight, y / weight, z / weight} : null;
	}
}
