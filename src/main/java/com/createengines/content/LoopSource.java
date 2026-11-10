package com.createengines.content;

import com.createengines.config.CEClientConfig;

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

	/**
	 * Share of the way to a louder or higher figure that the loop's own spin closes each tick, 0 to 1: small for a heavy
	 * rotor that is slow to come up to speed. The sound follows its source through this, so it lags the machine.
	 */
	default float loopSpinUp() {
		return 0.06f;
	}

	/** The same going down: smaller than {@link #loopSpinUp()} for a rotor that runs on after it is let go. */
	default float loopSpinDown() {
		return 0.06f;
	}

	/**
	 * Whether the loop keeps sounding while it spins down after the source has gone quiet, instead of fading out as soon as
	 * the source asks for silence. A blower or turbo coasts to a stop; a rattle just stops.
	 */
	default boolean loopCoasts() {
		return false;
	}

	/** The player's volume setting for this kind of sound, read as the loop plays; at 0 the loop is not started. */
	default float loopVolume() {
		return CEClientConfig.boostVolume();
	}
}
