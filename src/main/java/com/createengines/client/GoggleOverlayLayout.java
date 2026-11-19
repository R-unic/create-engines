package com.createengines.client;

/**
 * Where Create's goggle readout may start vertically. Create keeps its bottom 20 GUI pixels above the screen edge, which
 * is inside the survival status bars (hunger starts 39 pixels up, air bubbles 49), so a tall readout is drawn under
 * them. Our readouts are tall, so the margin is raised to clear the bars.
 */
public final class GoggleOverlayLayout {

	/** Extra margin over Create's 20: bottom of the readout ends 56 pixels up, clearing hunger and air bubbles. */
	public static final int EXTRA_BOTTOM_MARGIN = 36;
	/** The readout is never pushed above this, so a very tall one on a small screen still starts on screen. */
	public static final int TOP_LIMIT = 8;

	private GoggleOverlayLayout() {
	}

	/**
	 * Replaces Create's {@code Math.min(y, screenHeight - tooltipHeight - 20)}. {@code createLimit} is that second
	 * argument. The result is never lower than the raised limit and never above Create's own answer, and when the
	 * raised limit would push the readout off the top it falls back to Create's answer.
	 */
	public static int clampTop(int y, int createLimit) {
		int create = Math.min(y, createLimit);
		int raised = createLimit - EXTRA_BOTTOM_MARGIN;
		if (raised < TOP_LIMIT)
			return create;
		return Math.min(create, raised);
	}
}
