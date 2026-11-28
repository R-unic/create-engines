package com.createengines.content;

/**
 * The cracks and knocks of a note, which do not ring its pipes: those out of open outlets and those through mufflers.
 * They still come down the same long exhaust, which takes the glassy edge off them, and more off a diesel's knock:
 * left high, it was a thin tick laid over what sounded like a petrol engine. Pure Java; the sound thread alone uses it.
 */
final class NoteRaps {

	/** How far each follows a sample: a one-pole low-pass near 2.6 kHz, and near 1.4 kHz for compression ignition. */
	private static final float SPARK_EDGE = 0.4f;
	private static final float DIESEL_EDGE = 0.24f;

	private final GrainBus openBus = new GrainBus();
	private final GrainBus shutBus = new GrainBus();
	private float edge = SPARK_EDGE;
	private float open;
	private float shut;

	void ignition(boolean compression) {
		edge = compression ? DIESEL_EDGE : SPARK_EDGE;
	}

	/** A crack or a knock, {@code muffled} of it (0 to 1) through the muffler. */
	void strike(float[] grain, float gain, float muffled) {
		openBus.strike(grain, gain * (1 - muffled));
		shutBus.strike(grain, gain * muffled);
	}

	/** The next sample out of the open outlets; once a sample, as {@link #shut} is. */
	float open() {
		open = Denormals.flush(open + edge * (openBus.take() - open));
		return open;
	}

	/** The next sample through the mufflers. */
	float shut() {
		shut = Denormals.flush(shut + edge * (shutBus.take() - shut));
		return shut;
	}

	boolean idle() {
		return openBus.idle() && shutBus.idle();
	}

	void clear() {
		openBus.clear();
		shutBus.clear();
		open = shut = 0;
	}
}
