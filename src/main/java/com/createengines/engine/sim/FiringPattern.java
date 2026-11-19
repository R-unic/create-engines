package com.createengines.engine.sim;

/**
 * The firing intervals of an engine put into words short enough for a goggle line. A 48-cylinder engine has 48
 * intervals, and listing them all ran the readout off the screen, so a long or uneven list is summarised by its range.
 * No Minecraft types here, so it is unit tested.
 */
public final class FiringPattern {

	/** Most intervals listed one by one. Past this the readout gives the range instead. */
	public static final int MAX_LISTED = 6;

	private FiringPattern() {
	}

	/** True when every interval is within a degree of the first. */
	public static boolean even(float[] intervals) {
		for (float interval : intervals)
			if (Math.abs(interval - intervals[0]) >= 1)
				return false;
		return true;
	}

	/** The intervals as a line of text: "N° apart, all even", the list when it is short, or the range when it is not. */
	public static String describe(float[] intervals) {
		if (intervals.length == 0)
			return "";
		if (even(intervals) && intervals.length > 2)
			return Math.round(intervals[0]) + "° apart, all even";
		if (intervals.length <= MAX_LISTED) {
			StringBuilder text = new StringBuilder();
			for (float interval : intervals) {
				if (!text.isEmpty())
					text.append(" / ");
				text.append(Math.round(interval))
					.append("°");
			}
			return text.toString();
		}
		float shortest = intervals[0];
		float longest = intervals[0];
		for (float interval : intervals) {
			shortest = Math.min(shortest, interval);
			longest = Math.max(longest, interval);
		}
		return "uneven, " + Math.round(shortest) + "° to " + Math.round(longest) + "°";
	}
}
