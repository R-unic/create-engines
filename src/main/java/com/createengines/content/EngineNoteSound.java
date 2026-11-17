package com.createengines.content;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import javax.sound.sampled.AudioFormat;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import com.createengines.config.CEClientConfig;
import com.createengines.content.crankshaft.CrankshaftBlockEntity;
import com.createengines.engine.EngineState;
import com.createengines.engine.EngineSummary;
import com.createengines.engine.sim.Muffling;
import com.createengines.registry.CESounds;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * An engine's exhaust note: one streamed voice per engine, made by {@link EngineNoteSynth} from the engine's speed and
 * firing order instead of played from a recording. The sound engine pulls samples from {@link Stream} a tenth of a second
 * at a time, so the note trails what the engine is doing by a few tenths of a second, about as much as the engine's own
 * speed does. Client only; the crankshaft reaches it from a client-side check, so a dedicated server never loads this
 * class.
 */
public final class EngineNoteSound extends AbstractTickableSoundInstance {

	private static final Logger LOGGER = LogUtils.getLogger();
	private static final Map<BlockPos, EngineNoteSound> PLAYING = new HashMap<>();
	/** Streamed voices are scarce (the sound engine has only a handful besides music), so only the first few engines get one. */
	private static final int MAX_VOICES = 3;
	private static final VoiceBudget BUDGET = new VoiceBudget(MAX_VOICES);
	/** Beyond this the note is inaudible anyway. */
	private static final double HEARING_RANGE = 40;
	private static final float FADE_IN = 0.06f;
	private static final float FADE_OUT = 0.04f;
	private static final float LOUDEST = 0.9f;

	private final CrankshaftBlockEntity crank;
	private final EngineNoteSynth synth;
	private final long startedAt;
	private float fade;
	private int ticks;
	/** The cam's place in the cycle as a cylinder last reported it this tick, or NaN. */
	private double reportedPhase = Double.NaN;

	private EngineNoteSound(CrankshaftBlockEntity crank) {
		super(CESounds.ENGINE_NOTE.get(), SoundSource.BLOCKS, SoundInstance.createUnseededRandom());
		this.crank = crank;
		this.startedAt = crank.getLevel().getGameTime();
		this.synth = new EngineNoteSynth(crank.getBlockPos()
			.hashCode());
		looping = true;
		delay = 0;
		volume = 0;
		BlockPos at = crank.getBlockPos();
		x = at.getX() + 0.5;
		y = at.getY() + 0.5;
		z = at.getZ() + 0.5;
	}

	/**
	 * Starts this engine's note unless it is already going. An instance the sound engine declined to start (master volume
	 * at zero, no free channel) never ticks to finish itself, so a dead one is replaced.
	 */
	public static void ensurePlaying(CrankshaftBlockEntity crank) {
		if (CEClientConfig.engineNoteVolume() <= 0)
			return;
		BlockPos at = crank.getBlockPos();
		SoundManager sounds = Minecraft.getInstance()
			.getSoundManager();
		EngineNoteSound current = PLAYING.get(at);
		long now = crank.getLevel().getGameTime();
		// A new instance gets a moment to start; one the sound engine declined (master volume at zero, no free streaming
		// channel) is only tried again after that, not every tick
		if (current != null && (!VoiceClock.settled(now, current.startedAt) || !current.isStopped() && sounds.isActive(current)))
			return;
		PLAYING.values()
			.removeIf(note -> {
				boolean dropped = VoiceClock.settled(now, note.startedAt) && (note.isStopped() || !sounds.isActive(note));
				if (dropped) {
					LOGGER.debug("Engine note at {} dropped by the sound engine (stopped {}), {} ticks after it started",
						note.crank.getBlockPos(), note.isStopped(), now - note.startedAt);
					BUDGET.finished(note.crank.getBlockPos(), now);
				}
				return dropped;
			});
		BUDGET.prune(now);
		if (PLAYING.size() >= MAX_VOICES || !near(crank))
			return;
		// An engine whose state flaps must not start and drop a streamed voice every few ticks
		if (current != null)
			BUDGET.finished(at, now);
		if (!BUDGET.tryStart(at, now, PLAYING.size() - (PLAYING.containsKey(at) ? 1 : 0)))
			return;
		EngineNoteSound note = new EngineNoteSound(crank);
		PLAYING.put(at, note);
		try {
			sounds.play(note);
		} catch (RuntimeException e) {
			PLAYING.remove(at, note);
			BUDGET.finished(at, now);
			LOGGER.warn("Engine note at {} could not start", at, e);
			return;
		}
		LOGGER.debug("Engine note started at {}", at);
	}

	/** Where the engine whose primary crankshaft is at {@code at} is in its 720 degree cycle now, from its cam. */
	public static void reportPhase(BlockPos at, double cycleDegrees) {
		EngineNoteSound note = PLAYING.get(at);
		if (note != null)
			note.reportedPhase = cycleDegrees;
	}

	/** Forgets every voice, for when the player leaves a world: the sound engine has already dropped them. */
	public static void clear() {
		PLAYING.clear();
		BUDGET.clear();
	}

	/** Whether the engine whose primary crankshaft is at {@code at} has its note playing, so its bangs can give way to it. */
	public static boolean isPlayingFor(BlockPos at) {
		EngineNoteSound note = PLAYING.get(at);
		return note != null && !note.isStopped() && Minecraft.getInstance()
			.getSoundManager()
			.isActive(note);
	}

	private static boolean near(CrankshaftBlockEntity crank) {
		Player player = Minecraft.getInstance().player;
		return player != null && player.distanceToSqr(crank.getBlockPos()
			.getCenter()) <= HEARING_RANGE * HEARING_RANGE;
	}

	/** The note fades in from nothing, which the sound engine would otherwise skip as inaudible. */
	@Override
	public boolean canStartSilent() {
		return true;
	}

	/** The sound engine asks for the audio here instead of reading a file. */
	@Override
	public CompletableFuture<AudioStream> getStream(SoundBufferLibrary soundBuffers, Sound sound, boolean looping) {
		return CompletableFuture.completedFuture(new Stream(synth));
	}

	@Override
	public void tick() {
		EngineSummary summary = crank.isRemoved() ? null : crank.summary();
		EngineNoteSynth.Params wanted = summary == null ? EngineNoteSynth.Params.SILENT : params(summary);
		synth.set(wanted);
		if (summary != null && !Double.isNaN(reportedPhase)) {
			// What is rendered now is heard a few tenths of a second on, so aim at where the cam will be then
			synth.lockTo(reportedPhase + wanted.rpm() * 6.0 * EngineNoteSynth.OUTPUT_LATENCY);
			reportedPhase = Double.NaN;
		}
		boolean audible = wanted.combustion() > 0 && wanted.rpm() > 1 && near(crank);
		fade = Mth.clamp(fade + (audible ? FADE_IN : -FADE_OUT), 0, 1);
		if (fade <= 0 && !audible) {
			// Once per note, so cheap: says why an engine that was playing went quiet
			LOGGER.debug("Engine note at {} ended: {}, rpm {}, combustion {}, in range {}", crank.getBlockPos(),
				summary == null ? "no summary" : "state " + summary.state(), wanted.rpm(), wanted.combustion(), near(crank));
			if (PLAYING.remove(crank.getBlockPos(), this))
				BUDGET.finished(crank.getBlockPos(), crank.getLevel() == null ? 0 : crank.getLevel().getGameTime());
			stop();
			return;
		}
		volume = LOUDEST * CEClientConfig.engineNoteVolume() * fade
			* Muffling.volume(summary == null ? 0 : summary.muffling());
		// Once a second, so cheap: shows a note that is alive but silent
		if (++ticks % 20 == 0 && volume < 0.02f)
			LOGGER.debug("Engine note at {} is quiet: volume {}, fade {}, state {}, rpm {}, combustion {}, angles {}",
				crank.getBlockPos(), volume, fade, summary == null ? "none" : summary.state(), wanted.rpm(), wanted.combustion(),
				wanted.firingAngles().length);
	}

	/** How much of the note is burning in each state: a coasting or cranking engine only pumps air. */
	static EngineNoteSynth.Params params(EngineSummary summary) {
		EngineState state = summary.state();
		float combustion = switch (state) {
			case RUNNING -> 1f;
			case OVERLOADED -> 0.85f;
			case COASTING -> 0.22f;
			case CRANKING -> 0.3f;
			default -> 0f;
		};
		boolean burning = state == EngineState.RUNNING || state == EngineState.OVERLOADED;
		float share = burning && summary.cylinders() > 0 ? Mth.clamp(summary.firing() / (float) summary.cylinders(), 0, 1) : 1;
		float load = summary.availableTorque() > 0 ? Mth.clamp(summary.loadTorque() / summary.availableTorque(), 0, 1.3f) : 0;
		return new EngineNoteSynth.Params(Math.abs(summary.rpm()), summary.firingAngles(), share, load, combustion,
			size(summary.cylinders()), sharpness(load, summary.boost()));
	}

	/** One cylinder is a big slow thumper at 1, sixteen are a tight small-bore buzz at 0, halving the size at each doubling. */
	static float size(int cylinders) {
		return Mth.clamp(1 - (float) (Math.log(Math.max(1, cylinders)) / Math.log(2)) / 4f, 0, 1);
	}

	/** An idling, naturally aspirated engine cracks softly; load and boost make each firing hit harder. */
	static float sharpness(float load, float boost) {
		return Mth.clamp(0.3f + 0.35f * Math.min(load, 1f) + 0.5f * Mth.clamp(boost, 0, 1), 0, 1);
	}

	/** The synthesizer as the sound engine reads it: mono, 16 bit, a tenth of a second at a time. */
	private static final class Stream implements AudioStream {

		private static final AudioFormat FORMAT = new AudioFormat(EngineNoteSynth.SAMPLE_RATE, 16, 1, true, false);

		private final EngineNotePcm pcm;
		private int reads;

		private Stream(EngineNoteSynth synth) {
			this.pcm = new EngineNotePcm(synth);
		}

		@Override
		public AudioFormat getFormat() {
			return FORMAT;
		}

		@Override
		public ByteBuffer read(int size) {
			if (reads++ == 0)
				LOGGER.debug("Engine note stream first read: {} samples asked for", size / 2);
			return pcm.read(size);
		}

		@Override
		public void close() {
		}
	}
}
