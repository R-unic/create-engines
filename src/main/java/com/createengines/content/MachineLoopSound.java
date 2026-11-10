package com.createengines.content;

import java.util.IdentityHashMap;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;

/**
 * A seamless loop that follows a machine: one instance per source, faded in when the machine starts working, bent in
 * pitch and volume as its {@link LoopSource} says, and faded out and stopped when it winds down. Used for the whistle of
 * a spooling turbo, the whine of a supercharger, and an engine's valve gear and dry bearings. Client only; callers reach it from a client-side check, so a
 * dedicated server never loads this class.
 */
public final class MachineLoopSound extends AbstractTickableSoundInstance {

	private static final Map<Object, MachineLoopSound> PLAYING = new IdentityHashMap<>();
	private static final float FADE_IN = 0.1f;
	private static final float FADE_OUT = 0.08f;
	/** Below this the loop is wanted silent. */
	private static final float AUDIBLE = 0.02f;
	private final Object owner;
	private final LoopSource source;
	private final float loudest;
	/** How far the loop has faded in, 0 (silent) to 1. */
	private float fade;
	/** The level and pitch the loop is at now, on their way to what the source asks for. */
	private float level;
	private float currentPitch;

	private MachineLoopSound(Object owner, BlockPos at, SoundEvent sound, float loudest, LoopSource source) {
		super(sound, SoundSource.BLOCKS, SoundInstance.createUnseededRandom());
		this.owner = owner;
		this.source = source;
		this.loudest = loudest;
		this.level = Mth.clamp(source.loopLevel(), 0, 1);
		this.currentPitch = source.loopPitch();
		looping = true;
		delay = 0;
		volume = 0;
		x = at.getX() + 0.5;
		y = at.getY() + 0.5;
		z = at.getZ() + 0.5;
	}

	/**
	 * Starts {@code owner}'s loop unless it is already going. An instance the sound engine declined to start (master
	 * volume at zero, no free channel) never ticks to finish itself, so a dead one is replaced.
	 */
	public static void ensurePlaying(Object owner, BlockPos at, SoundEvent sound, float loudest, LoopSource source) {
		if (source.loopVolume() <= 0)
			return;
		SoundManager sounds = Minecraft.getInstance()
			.getSoundManager();
		MachineLoopSound current = PLAYING.get(owner);
		if (current != null && !current.isStopped() && sounds.isActive(current))
			return;
		MachineLoopSound loop = new MachineLoopSound(owner, at, sound, loudest, source);
		PLAYING.put(owner, loop);
		sounds.play(loop);
	}

	/** The loop fades in from nothing, which the sound engine would otherwise skip as inaudible. */
	@Override
	public boolean canStartSilent() {
		return true;
	}

	@Override
	public void tick() {
		if (source.isRemoved()) {
			finish();
			return;
		}
		float wanted = Mth.clamp(source.loopLevel(), 0, 1);
		// A spinning part keeps turning after it is let go: it is working while it is asked to, and, for a source that
		// coasts, until its own spin has run down
		boolean working = wanted > AUDIBLE || source.loopCoasts() && level > AUDIBLE;
		fade = Mth.clamp(fade + (working ? FADE_IN : -FADE_OUT), 0, 1);
		if (fade <= 0 && !working) {
			finish();
			return;
		}
		level += (wanted - level) * (wanted > level ? source.loopSpinUp() : source.loopSpinDown());
		float wantedPitch = source.loopPitch();
		currentPitch += (wantedPitch - currentPitch) * (wantedPitch > currentPitch ? source.loopSpinUp() : source.loopSpinDown());
		volume = loudest * source.loopVolume() * level * fade;
		pitch = Mth.clamp(currentPitch, 0.5f, 2f);
	}

	private void finish() {
		PLAYING.remove(owner, this);
		stop();
	}
}
