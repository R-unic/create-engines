package com.createengines.content.headphones;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * What the noise-cancelling headphones do to a sound, and which sounds they leave alone. Plain Java with no game types, so
 * the rules are unit tested; the client hook ({@code HeadphoneMuffle}) turns them into OpenAL calls.
 * <p>
 * The headphones muffle, they never mute: a muffled sound is rolled off at the top (a low-pass filter) and turned down, and
 * every sound the wearer could hear is still heard. Only sounds that come from the world are muffled. The game's music, the
 * menu and interface clicks and a voice-chat mod's voices are not, so what decides it is the sound's category
 * ({@code SoundSource#getName()}): the three in {@link #EXEMPT} are left alone and every other one is muffled.
 * {@link #KNOWN} lists every category the game has, each with an explicit decision, so a category added to the game stops a
 * test until someone decides it (a muffled new category would otherwise be a silent default).
 */
public final class Muffle {

	/** Categories the headphones never touch: music, menu and interface sounds, and voices. */
	public static final Set<String> EXEMPT = Set.of("master", "music", "voice");

	/**
	 * Every sound category the game has (1.21.1), by name: music, interface and voices are left alone, the rest are the
	 * world. Records sit in the world (a jukebox, a note block), so they are muffled.
	 */
	public static final List<String> KNOWN = List.of("master", "music", "record", "weather", "block", "hostile", "neutral",
		"player", "ambient", "voice");

	/** Overall level of a muffled sound, against 1 for a sound that is not. */
	public static final float GAIN = 0.5f;
	/**
	 * Level of the highest frequencies of a muffled sound: well under {@link #GAIN}, so the sound is duller and not only
	 * quieter. Untuned: the author's ear sets these (docs/ROADMAP.md).
	 */
	public static final float GAIN_HF = 0.05f;
	/**
	 * With no low-pass filter on the sound card (the OpenAL extension is missing), the headphones fall back to capping each
	 * world sound's loudness at this, which is a volume cut only.
	 */
	public static final float FALLBACK_MAX_GAIN = 0.35f;

	private Muffle() {
	}

	/** Whether a sound of this category (the name {@code SoundSource#getName()} gives) is muffled while headphones are worn. */
	public static boolean muffles(String category) {
		return !EXEMPT.contains(category.toLowerCase(Locale.ROOT));
	}

	/** The overall level of a sound from the world: {@link #GAIN} worn, 1 not. Never zero. */
	public static float gain(boolean worn) {
		return worn ? GAIN : 1f;
	}

	/** The level of its high frequencies: {@link #GAIN_HF} worn, 1 not. Never zero, so a muffled sound is never silent. */
	public static float highFrequencyGain(boolean worn) {
		return worn ? GAIN_HF : 1f;
	}

	/** The cap on a sound's loudness when there is no filter: {@link #FALLBACK_MAX_GAIN} worn, 1 (no cap) not. */
	public static float fallbackMaxGain(boolean worn) {
		return worn ? FALLBACK_MAX_GAIN : 1f;
	}
}
