package com.createengines.content;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * The blowers one engine's note is carrying: each turbo's whistle and supercharger's whine that would otherwise loop on
 * its own, played in the note's stream ({@link EngineNoteMix}) at the level it would be heard at from where it stands.
 * Each follows its {@link LoopSource} as {@link MachineLoopSound} does, with the same spin up and down. Client thread only.
 */
final class NoteWhines {

	/** Below this a blower is wanted silent. */
	private static final float AUDIBLE = 0.02f;
	/** Heard from nearer its edge than this share of the note's reach, the note is too faint to carry anything else. */
	private static final float FAINTEST_NOTE = 0.2f;

	/**
	 * A blower to carry.
	 *
	 * @param at      where it stands
	 * @param loudest its loop's volume at its loudest
	 */
	record Blower(LoopSource source, BoostWhine.Kind kind, BlockPos at, float loudest) {
	}

	private static final class Carried {
		Blower blower;
		float level;
		float pitch;
		/** How far it has crossed over from its loop to the note, 0 to 1. */
		float fade;
		/** Whether the last tick found it can be carried; not until one has. */
		boolean working;
	}

	private final Carried[] carried = new Carried[EngineNoteMix.WHINES];
	private final EngineNoteMix mix;

	NoteWhines(EngineNoteMix mix) {
		this.mix = mix;
	}

	/** Takes the blower on if there is room, or says it already has it; false leaves it to its own loop. */
	boolean take(Blower blower) {
		int free = -1;
		for (int s = 0; s < carried.length; s++) {
			if (carried[s] == null)
				free = free < 0 ? s : free;
			else if (carried[s].blower.source() == blower.source())
				return carried[s].working;
		}
		if (free < 0)
			return false;
		Carried made = new Carried();
		made.blower = blower;
		made.level = LoopSanity.finite(blower.source()
			.loopLevel(), 0, 1, 0);
		made.pitch = LoopSanity.finite(blower.source()
			.loopPitch(), BoostWhine.LOWEST_PITCH, BoostWhine.HIGHEST_PITCH, 1);
		carried[free] = made;
		return true;
	}

	/** Whether {@code source} is being played here, and so its own loop is to give way. */
	boolean carries(LoopSource source) {
		for (Carried one : carried)
			if (one != null && one.blower.source() == source)
				return one.working;
		return false;
	}

	/**
	 * One tick.
	 *
	 * @param volume     the note's source volume
	 * @param noteReach  how loud the note's source is where the listener is, 0 to 1
	 * @param yielding   whether the note is on its way out, so its blowers go back to their loops
	 * @return whether any is still to be heard, so the note must not end yet
	 */
	boolean tick(float volume, float noteReach, boolean yielding) {
		boolean any = false;
		for (int s = 0; s < carried.length; s++) {
			Carried one = carried[s];
			if (one == null)
				continue;
			LoopSource source = one.blower.source();
			boolean gone = source.isRemoved();
			float wanted = gone ? 0 : LoopSanity.finite(source.loopLevel(), 0, 1, 0);
			one.working = !gone && !yielding && volume > 0 && noteReach >= FAINTEST_NOTE && source.loopVolume() > 0
				&& near(one.blower.at()) && (wanted > AUDIBLE || source.loopCoasts() && one.level > AUDIBLE);
			one.fade = NoteHandover.step(one.fade, one.working);
			if (one.fade <= 0 && !one.working) {
				mix.releaseWhine(s);
				carried[s] = null;
				continue;
			}
			float spinsUp = gone ? 0 : source.loopSpinUp();
			float spinsDown = gone ? 0 : source.loopSpinDown();
			one.level += (wanted - one.level) * (wanted > one.level ? spinsUp : spinsDown);
			if (!gone) {
				float pitch = LoopSanity.finite(source.loopPitch(), BoostWhine.LOWEST_PITCH, BoostWhine.HIGHEST_PITCH, 1);
				one.pitch += (pitch - one.pitch) * (pitch > one.pitch ? spinsUp : spinsDown);
			}
			float asLoop = gone ? 0 : Mth.clamp(one.blower.loudest() * source.loopVolume() * one.level, 0, 1)
				* reach(one.blower.at()
					.getCenter(), one.blower.kind().range);
			// The note's own volume and fall-off will come on top, so they are divided out
			float level = volume > 0 && noteReach >= FAINTEST_NOTE ? asLoop / (volume * noteReach) : 0;
			mix.setWhine(s, one.blower.kind(), level * NoteHandover.noteGain(one.fade), one.pitch);
			any = true;
		}
		return any;
	}

	/** Whether a blower standing at {@code at} is near enough to the player to be worth playing. */
	private static boolean near(BlockPos at) {
		return LoopSanity.inRange(Math.pow(EngineNoteSound.distance(at.getCenter()), 2));
	}

	/** How loud a sound with an attenuation distance of {@code range} is where the listener is, as the game fades it. */
	static float reach(Vec3 from, float range) {
		return (float) Math.clamp(1 - EngineNoteSound.distance(from) / range, 0, 1);
	}
}
