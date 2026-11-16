package com.createengines.content;

import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;

import javax.sound.sampled.AudioFormat;

import com.createengines.advancement.StingGate;
import com.createengines.config.CEClientConfig;
import com.createengines.registry.CESounds;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.sounds.SoundSource;

/**
 * The sting for a goal or a challenge: one short streamed voice, made by {@link StingSynth}, heard everywhere at once
 * and not from any place in the world. It is the engine note's quieter cousin and shares its volume setting. Client
 * only; the server sends a packet and the packet's handler is the only thing that reaches this class.
 */
public final class StingSound extends AbstractTickableSoundInstance {

	private static final StingGate GATE = new StingGate();

	private final StingSynth synth;
	private int ticksLeft;

	private StingSound(boolean challenge) {
		super(CESounds.ADVANCEMENT_STING.get(), SoundSource.MASTER, SoundInstance.createUnseededRandom());
		this.synth = new StingSynth(challenge);
		this.ticksLeft = (int) (StingSynth.seconds(challenge) * 20) + 10;
		relative = true;
		attenuation = Attenuation.NONE;
		looping = false;
		delay = 0;
		volume = Math.min(1f, CEClientConfig.engineNoteVolume());
	}

	/** Plays the sting unless the engine note is muted or one played a moment ago. */
	public static void play(boolean challenge) {
		if (CEClientConfig.engineNoteVolume() <= 0 || !GATE.allow(System.currentTimeMillis(), challenge))
			return;
		Minecraft.getInstance()
			.getSoundManager()
			.play(new StingSound(challenge));
	}

	@Override
	public CompletableFuture<AudioStream> getStream(SoundBufferLibrary soundBuffers, Sound sound, boolean looping) {
		return CompletableFuture.completedFuture(new Stream(synth));
	}

	@Override
	public void tick() {
		if (--ticksLeft <= 0)
			stop();
	}

	/** The synthesizer as the sound engine reads it: mono, 16 bit, a tenth of a second at a time. */
	private static final class Stream implements AudioStream {

		private static final AudioFormat FORMAT = new AudioFormat(StingSynth.SAMPLE_RATE, 16, 1, true, false);

		private final EngineNotePcm pcm;

		private Stream(StingSynth synth) {
			this.pcm = new EngineNotePcm(synth::render);
		}

		@Override
		public AudioFormat getFormat() {
			return FORMAT;
		}

		@Override
		public ByteBuffer read(int size) {
			return pcm.read(size);
		}

		@Override
		public void close() {
		}
	}
}
