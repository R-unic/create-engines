package com.createengines.content;

/**
 * Something that hums while it works, for {@link MachineLoopSound}: a block entity that says how loud and how high its
 * loop should be, tick by tick. Pure Java, so a block entity can implement it on a dedicated server without loading any
 * client class.
 */
public interface LoopSource {

	/** Whether the thing making the sound has gone, which ends the loop at once. */
	boolean isRemoved();

	/** How loud the loop is wanted, 0 (silent: it fades out and stops) to 1 (as loud as it gets). */
	float loopLevel();

	/** The loop's pitch: 1 plays the recording as it is. Kept between 0.5 and 2 by the sound. */
	float loopPitch();
}
