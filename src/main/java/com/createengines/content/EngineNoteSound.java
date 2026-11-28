package com.createengines.content;

import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;
import java.util.concurrent.CompletableFuture;

import javax.sound.sampled.AudioFormat;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import com.createengines.config.CEClientConfig;
import com.createengines.content.crankshaft.CrankshaftBlockEntity;
import com.createengines.content.effects.StartStallTake;
import com.createengines.engine.CylinderStatus;
import com.createengines.engine.EngineSound;
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
import net.minecraft.world.phys.Vec3;

/**
 * An engine's exhaust note: one streamed voice per engine, made by {@link EngineNoteSynth} from the engine's speed, firing
 * order and build (its {@link EngineSound}) instead of played from a recording. The sound engine pulls samples from
 * {@link Stream} a twentieth of a second at a time, so the note is heard about a seventh of a second after it is made, and
 * is made that far ahead along the engine's trend ({@link EngineNoteDrive}). Its fades and the chorus's share are applied
 * in the stream, sample by sample ({@link EngineNoteMix}), along with its turbos' whistles and superchargers' whine
 * ({@link NoteWhines}); the source's own volume stays put. It is heard from its exhaust openings. Client only; the crankshaft reaches it from a client-side check, so a dedicated
 * server never loads this class.
 * <p>
 * Every running engine in earshot is heard (see {@link EngineChorus}): the nearest few get a note of their own, the
 * nearest of the rest are mixed into one shared "engine room" voice ({@link EngineRoomScene}), and those past that only
 * make the room fuller. All of them together are held under a ceiling, and an engine held steady settles a little quieter.
 */
public final class EngineNoteSound extends AbstractTickableSoundInstance {

	private static final Logger LOGGER = LogUtils.getLogger();
	private static final Map<BlockPos, EngineNoteSound> PLAYING = new HashMap<>();
	/** Streamed voices are scarce (the sound engine has only a handful besides music), so only the first few engines get one. */
	private static final int MAX_VOICES = EngineChorus.NOTE_VOICES;
	private static final VoiceBudget BUDGET = new VoiceBudget(MAX_VOICES);
	/** Beyond this the note is inaudible anyway, and holding a voice would only keep it from an engine that is heard. */
	private static final double HEARING_RANGE = EngineChorus.REACH + 4;
	/** Ticks a faded note plays on for, so what is already queued has been heard before the source is cut. */
	private static final int DRAIN_TICKS = (int) Math.ceil(EngineNoteSynth.OUTPUT_LATENCY * 20) + 1;
	private static final float LOUDEST = 0.9f;
	/** What a backfire's and a spit's recordings play at with the volume settings at 1: a bang in the note at full strength. */
	private static final float POP_LOUDNESS = 0.7f;
	private static final float SPIT_LOUDNESS = 0.3f;
	/** A roughness of 0.4 (the most the simulation reports) is a fully rough note. */
	private static final float ROUGHEST = 0.4f;
	/** A fully clad engine is this much quieter as well as duller. */
	private static final float CLAD_QUIETER = 0.25f;

	/** Which engines may be heard, by where their primary crankshaft is: all of them, unless {@link #hearOnly} narrows it. */
	private static Predicate<BlockPos> audibleEngines = at -> true;

	private final CrankshaftBlockEntity crank;
	private final EngineNoteSynth synth;
	private final EngineNoteMix mix;
	private final NoteWhines whines;
	private final EngineNoteDrive drive = new EngineNoteDrive();
	private final long startedAt;
	private float fade;
	private float gain;
	private int drained;
	private int ticks;
	/** Whether the room voice is playing this engine too while the note comes in or goes out, the two crossfading. */
	private boolean shared;
	private volatile Stream stream;
	private final StreamStallWatch stall = new StreamStallWatch();
	/** The cam's place in the cycle as a cylinder last reported it this tick, or NaN, and when it did. */
	private double reportedPhase = Double.NaN;
	private long reportedAt;
	/** Set when a nearer engine needs this voice: the note fades out and its engine joins the shared voice. */
	private boolean yielding;

	private EngineNoteSound(CrankshaftBlockEntity crank) {
		super(CESounds.ENGINE_NOTE.get(), SoundSource.BLOCKS, SoundInstance.createUnseededRandom());
		this.crank = crank;
		this.startedAt = crank.getLevel().getGameTime();
		this.synth = new EngineNoteSynth(crank.getBlockPos()
			.hashCode());
		this.mix = new EngineNoteMix(synth);
		this.whines = new NoteWhines(mix);
		this.shared = EngineRoomScene.inSlot(crank.getBlockPos());
		looping = true;
		delay = 0;
		// The fade is in the stream, which starts from nothing, so the source's volume does not move
		volume = LOUDEST * CEClientConfig.engineNoteVolume();
		Vec3 from = moveToExhaust();
		// Set before the stream is first read, so a note starting far off starts dull
		mix.setAir(NoteAir.cutoff(distance(from)));
	}

	private Vec3 moveToExhaust() {
		Vec3 from = EngineRoomScene.source(crank.getBlockPos());
		x = from.x;
		y = from.y;
		z = from.z;
		return from;
	}

	/**
	 * Called each tick by every running engine's primary crankshaft: puts it in this tick's scene, and starts its own note
	 * unless it already has one or the voices are taken. An instance the sound engine declined to start (master volume at
	 * zero, no free channel) never ticks to finish itself, so a dead one is replaced.
	 */
	public static void ensurePlaying(CrankshaftBlockEntity crank) {
		if (CEClientConfig.engineNoteVolume() <= 0)
			return;
		BlockPos at = crank.getBlockPos();
		if (!audibleEngines.test(at))
			return;
		long now = crank.getLevel().getGameTime();
		EngineRoomScene.offer(crank, now);
		SoundManager sounds = Minecraft.getInstance()
			.getSoundManager();
		EngineNoteSound current = PLAYING.get(at);
		// A new instance gets a moment to start; one the sound engine declined (master volume at zero, no free streaming
		// channel) is only tried again after that, not every tick
		if (current != null && (!VoiceClock.settled(now, current.startedAt) || !current.isStopped() && MachineLoopSound.stillPlaying(sounds, current)))
			return;
		PLAYING.values()
			.removeIf(note -> {
				boolean dropped = VoiceClock.settled(now, note.startedAt) && (note.isStopped() || !MachineLoopSound.stillPlaying(sounds, note));
				if (dropped) {
					LOGGER.debug("Engine note at {} dropped by the sound engine (stopped {}), {} ticks after it started",
						note.crank.getBlockPos(), note.isStopped(), now - note.startedAt);
					note.lost(now);
				}
				return dropped;
			});
		BUDGET.prune(now);
		// A free voice goes to the nearest engine without one, not to whichever happens to ask first
		if (voicesFull() || !near(crank) || EngineRoomScene.nearerWaiting(at))
			return;
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
		EngineSummary summary = crank.summary();
		if (summary != null)
			// What the note is made of, so a report that one engine sounds wrong can be read from the log
			LOGGER.debug("Engine note started at {}: {} cylinders, {} firings listed, {} rpm (drawn {}), pipes {}, muffled {}",
				at, summary.cylinders(), summary.firingAngles().length, summary.rpm(), crank.getTheoreticalSpeed(),
				java.util.Arrays.toString(summary.sound().pipes()), java.util.Arrays.toString(summary.sound().muffles()));
	}

	/**
	 * Keeps every other engine out of the notes and the shared voice, which has one place and no fall-off of its own, so it
	 * cannot be told apart by where it plays. For the showcase, which films one courtyard at a time.
	 */
	public static void hearOnly(Predicate<BlockPos> engines) {
		audibleEngines = engines;
	}

	/** Tells the budget this note was lost to the sound engine; one that had been playing is replaced at once. */
	private void lost(long now) {
		Stream made = stream;
		BUDGET.lost(crank.getBlockPos(), now, now - startedAt, made != null && made.reads > 0);
	}

	/** Where the engine whose primary crankshaft is at {@code at} is in its 720 degree cycle now, from its cam. */
	public static void reportPhase(BlockPos at, double cycleDegrees) {
		SoundProbe.sighting(at, cycleDegrees);
		EngineNoteSound note = PLAYING.get(at);
		if (note != null) {
			note.reportedPhase = cycleDegrees;
			note.reportedAt = System.nanoTime();
		}
		// While the two cross-fade, the room voice's copy keeps step as well
		EngineRoomScene.reportPhase(at, cycleDegrees);
	}

	/** The exhaust openings of one of an engine's cylinders, which its note is heard from; reported as it ticks. */
	public static void reportOpenings(CylinderStatus cylinder) {
		if (Minecraft.getInstance().level == null || cylinder.exhaustOutlets()
			.isEmpty())
			return;
		long now = Minecraft.getInstance().level.getGameTime();
		float loudness = Muffling.volume(cylinder.exhaustMuffle());
		for (BlockPos opening : cylinder.exhaustOutlets())
			EngineRoomScene.reportOpening(cylinder.primary(), opening, loudness, now);
	}

	/**
	 * Has the note of the engine at {@code primary} carry a turbo's whistle, in place of its loop.
	 *
	 * @return false if there is no note to carry it, and the loop should play
	 */
	public static boolean carryTurbo(@Nullable BlockPos primary, LoopSource turbo, BlockPos at, float loudest) {
		return carry(primary, new NoteWhines.Blower(turbo, BoostWhine.Kind.TURBO, at, loudest));
	}

	/** The same for a supercharger's whine. */
	public static boolean carrySupercharger(@Nullable BlockPos primary, LoopSource blower, BlockPos at, float loudest) {
		return carry(primary, new NoteWhines.Blower(blower, BoostWhine.Kind.SUPERCHARGER, at, loudest));
	}

	private static boolean carry(@Nullable BlockPos primary, NoteWhines.Blower blower) {
		if (primary == null || CEClientConfig.engineNoteVolume() <= 0)
			return false;
		EngineNoteSound note = PLAYING.get(primary);
		return note != null && !note.yielding && isPlayingFor(primary) && note.whines.take(blower);
	}

	/** Whether any note is playing {@code source}, so its loop is to give way. */
	static boolean carries(LoopSource source) {
		for (EngineNoteSound note : PLAYING.values())
			if (!note.isStopped() && note.whines.carries(source))
				return true;
		return false;
	}

	/**
	 * Has the note of the engine whose primary crankshaft is at {@code at} play its catch or stall, so the firings keep the
	 * engine's own timing.
	 *
	 * @return -1 if this engine has no note to play it with, and the recording should play instead; else for a stall the
	 *         ticks until its crank is heard to come to rest, and 0 for a catch
	 */
	public static int perform(BlockPos at, StartStallTake.Kind kind) {
		EngineNoteSound note = PLAYING.get(at);
		if (note == null || note.yielding || CEClientConfig.engineNoteVolume() <= 0 || !isPlayingFor(at))
			return -1;
		EngineNoteGesture.Kind gesture = switch (kind) {
			case CATCH -> EngineNoteGesture.Kind.CATCH;
			case HARD_CATCH -> EngineNoteGesture.Kind.HARD_CATCH;
			case STALL -> EngineNoteGesture.Kind.STALL;
			case LUGGED_STALL -> EngineNoteGesture.Kind.LUGGED_STALL;
			case STARVED_STALL -> EngineNoteGesture.Kind.STARVED_STALL;
		};
		// A stall's summary may list nothing any more; the note still has what it was last playing
		if (!gesture.dies() && !hasFirings(note.crank))
			return -1;
		float from = note.synth.perform(gesture);
		if (from < 0)
			return -1;
		// A note only just started for the catch would fade it away: it comes in at once
		if (!gesture.dies() && !note.shared && note.ticks == 0)
			note.fade = 1;
		return gesture.dies() ? EngineNoteGesture.restTicks(gesture, from) : 0;
	}

	/** Drops a stall still winding down, for an engine that has been turned again. */
	public static void endStall(BlockPos at) {
		EngineNoteSound note = PLAYING.get(at);
		if (note != null && (note.synth.dying() || note.synth.resting()))
			note.synth.endGesture();
	}

	/**
	 * Has the note of the engine whose primary crankshaft is at {@code primary} play a backfire out of the pipe it next fires
	 * down. {@code loudness} is what the recording would have played at, the player's volume setting and any muffler in it.
	 *
	 * @return the cue that says when it is heard, or null if there is no note to play it and the recording should play
	 *         instead
	 */
	public static NoteCue pop(@Nullable BlockPos primary, float loudness, float pitch) {
		return bang(primary, NoteBangs.Kind.POP, loudness / POP_LOUDNESS, pitch);
	}

	/** The same for the spit of a charge that boost lit early. */
	public static NoteCue spit(@Nullable BlockPos primary, float loudness, float pitch) {
		return bang(primary, NoteBangs.Kind.SPIT, loudness / SPIT_LOUDNESS, pitch);
	}

	private static NoteCue bang(@Nullable BlockPos primary, NoteBangs.Kind kind, float loudness, float pitch) {
		float setting = CEClientConfig.engineNoteVolume();
		EngineNoteSound note = primary == null ? null : PLAYING.get(primary);
		if (note == null || note.yielding || setting <= 0 || !isPlayingFor(primary) || !hasFirings(note.crank))
			return null;
		// The stream is played at the note's own volume setting, which the bang's loudness must not be multiplied by again
		return note.synth.bang(kind, loudness / setting, pitch);
	}

	/** Forgets every voice, for when the player leaves a world: the sound engine has already dropped them. */
	public static void clear() {
		PLAYING.clear();
		BUDGET.clear();
		EngineRoomScene.clear();
	}

	/** Whether the engine whose primary crankshaft is at {@code at} has its note playing, so its bangs can give way to it. */
	public static boolean isPlayingFor(BlockPos at) {
		EngineNoteSound note = PLAYING.get(at);
		return note != null && !note.isStopped() && MachineLoopSound.stillPlaying(Minecraft.getInstance()
			.getSoundManager(), note);
	}

	/**
	 * Whether the engine whose primary crankshaft is at {@code at} is heard through the notes: its own, or the shared voice it
	 * is mixed into or makes fuller. Its recorded bangs then give way, as the note has them. False with the
	 * note turned off, so the bangs come back as that setting promises.
	 */
	public static boolean heard(BlockPos at) {
		if (CEClientConfig.engineNoteVolume() <= 0)
			return false;
		// A note with no firings to play (an engine that has listed none) must not take its bangs away
		EngineNoteSound note = PLAYING.get(at);
		return isPlayingFor(at) ? hasFirings(note.crank) : EngineRoomScene.inRoom(at);
	}

	// What the scene asks of the notes

	static boolean voicesFull() {
		return PLAYING.size() >= MAX_VOICES;
	}

	static Collection<EngineNoteSound> notes() {
		return PLAYING.values();
	}

	static boolean anyYielding() {
		for (EngineNoteSound note : PLAYING.values())
			if (note.yielding)
				return true;
		return false;
	}

	/** The share of its level the room voice plays an engine with a note at: some while the two cross-fade, else none. */
	static float roomShare(BlockPos at) {
		EngineNoteSound note = PLAYING.get(at);
		return note != null && note.shared ? NoteHandover.roomGain(note.fade) : 0;
	}

	BlockPos at() {
		return crank.getBlockPos();
	}

	long age(long now) {
		return now - startedAt;
	}

	/** Gives the voice up: the note crosses over to the shared voice and ends. */
	void giveWay() {
		yielding = true;
		shared = true;
	}

	static boolean hasFirings(CrankshaftBlockEntity crank) {
		EngineSummary summary = crank.summary();
		return summary != null && summary.firingAngles().length > 0;
	}

	/** How far the player is from {@code from}, in blocks. */
	static double distance(Vec3 from) {
		Player player = Minecraft.getInstance().player;
		return player == null ? Double.POSITIVE_INFINITY : Math.sqrt(player.distanceToSqr(from));
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
		Stream made = new Stream(new EngineNotePcm(mix));
		stream = made;
		return CompletableFuture.completedFuture(made);
	}

	@Override
	public void tick() {
		EngineSummary summary = crank.isRemoved() ? null : crank.summary();
		EngineNoteSynth.Params wanted = summary == null ? EngineNoteSynth.Params.SILENT : params(summary);
		BlockPos at = crank.getBlockPos();
		Vec3 from = moveToExhaust();
		double away = distance(from);
		synth.setDistance((float) (away / EngineChorus.REACH));
		mix.setAir(NoteAir.cutoff(away));
		if (summary == null) {
			// A broken-up engine has no stall left to play
			synth.endGesture();
			synth.set(wanted);
		} else {
			drive.drive(synth, crank, summary, reportedPhase, reportedAt);
			reportedPhase = Double.NaN;
		}
		volume = LOUDEST * CEClientConfig.engineNoteVolume();
		boolean near = near(crank);
		boolean ending = synth.dying() || synth.resting();
		boolean audible = !yielding && (ending || wanted.combustion() > 0 && wanted.rpm() > 1) && near;
		// A blower coasting down after its engine has stopped keeps the note, and so its stream, going
		boolean whining = whines.tick(volume, NoteWhines.reach(from, (float) EngineChorus.REACH), yielding);
		boolean wantedOn = audible || !yielding && whining && near;
		fade = NoteHandover.step(fade, wantedOn);
		if (fade >= 1 && !yielding)
			shared = false;
		gain = NoteHandover.noteGain(fade) * casingVolume(summary) * EngineRoomScene.share(at);
		mix.setGain(gain);
		SoundProbe.level("note", this, volume, pitch);
		drained = fade <= 0 && !wantedOn && !whining ? drained + 1 : 0;
		if (drained > DRAIN_TICKS) {
			// Once per note, so cheap: says why an engine that was playing went quiet
			LOGGER.debug("Engine note at {} ended: {}, rpm {}, combustion {}, in range {}", at,
				summary == null ? "no summary" : "state " + summary.state(), wanted.rpm(), wanted.combustion(), near);
			if (PLAYING.remove(at, this))
				BUDGET.finished(at, crank.getLevel() == null ? 0 : crank.getLevel().getGameTime());
			stop();
			return;
		}
		// A stream the sound engine has stopped reading is silent while still "active": end it so a fresh one replaces it
		Stream current = stream;
		if (current != null && stall.tick(current.reads, System.nanoTime())) {
			LOGGER.debug("Engine note at {} stalled: no stream reads for {} ticks, restarting it", at, StreamStallWatch.STALLED_TICKS);
			if (PLAYING.remove(at, this))
				lost(crank.getLevel() == null ? 0 : crank.getLevel().getGameTime());
			stop();
			return;
		}
		// Once a second, so cheap: shows a note that is alive but silent
		if (++ticks % 20 == 0 && gain < 0.02f)
			LOGGER.debug("Engine note at {} is quiet: gain {}, fade {}, state {}, rpm {}, combustion {}, angles {}", at, gain,
				fade, summary == null ? "none" : summary.state(), wanted.rpm(), wanted.combustion(), wanted.firingAngles().length);
	}

	/** How loud the engine's build lets it be: its mufflers, and casing round it. What the chorus weighs it by. */
	static float buildVolume(EngineSummary summary) {
		return summary == null ? 0 : Muffling.volume(summary.muffling()) * casingVolume(summary);
	}

	/** What casing alone takes off a note's volume. The mufflers are in the note itself, as they work on its exhaust alone. */
	private static float casingVolume(EngineSummary summary) {
		return summary == null ? 0 : 1 - CLAD_QUIETER * summary.sound()
			.enclosure();
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
		float load = load(summary);
		return new EngineNoteSynth.Params(Math.abs(summary.rpm()), summary.firingAngles(), share, load, combustion,
			EngineNoteVoicing.size(summary.cylinders()),sharpness(load, summary.boost()), Mth.clamp(summary.roughness() / ROUGHEST, 0, 1),
			EngineNoteSynth.Voice.of(summary.sound()));
	}

	static float load(EngineSummary summary) {
		return summary.availableTorque() > 0 ? Mth.clamp(summary.loadTorque() / summary.availableTorque(), 0, 1.3f) : 0;
	}

	/** An idling, naturally aspirated engine cracks softly; load and boost make each firing hit harder. */
	static float sharpness(float load, float boost) {
		return Mth.clamp(0.3f + 0.35f * Math.min(load, 1f) + 0.5f * Mth.clamp(boost, 0, 1), 0, 1);
	}

	/**
	 * The shared "engine room" voice: one streamed sound for the engines past the nearest few, mixed by {@link EngineRoomMix} at
	 * the gains the scene sets. Each engine's distance is in its own gain, so the sound engine's own fall-off
	 * is turned off and the voice sits where most of the room's sound comes from, only for direction. Client only.
	 */
	static final class Room extends AbstractTickableSoundInstance {

		private static final float LOUDEST = 0.9f;
		/** Ticks with every slot empty and faded before the voice ends itself. */
		private static final int IDLE_TICKS = 40;

		final EngineRoomMix mix = new EngineRoomMix();
		private volatile Stream stream;
		private final StreamStallWatch stall = new StreamStallWatch();
		private final long startedAt;
		private int idle;

		private Room() {
			super(CESounds.ENGINE_NOTE.get(), SoundSource.BLOCKS, SoundInstance.createUnseededRandom());
			looping = true;
			delay = 0;
			// Every gain in the room is set in the stream, so the source's stays put
			volume = LOUDEST * CEClientConfig.engineNoteVolume();
			attenuation = SoundInstance.Attenuation.NONE;
			startedAt = Minecraft.getInstance().level == null ? 0 : Minecraft.getInstance().level.getGameTime();
		}

		/** Starts the shared voice, or returns null if the sound engine would not take it. */
		static @Nullable Room start() {
			Room room = new Room();
			try {
				Minecraft.getInstance()
					.getSoundManager()
					.play(room);
			} catch (RuntimeException e) {
				LOGGER.warn("The shared engine voice could not start", e);
				return null;
			}
			LOGGER.debug("Shared engine voice started");
			return room;
		}

		/** Whether the voice is still going: started (or still being given its moment to start) and not stopped. */
		boolean alive() {
			if (isStopped())
				return false;
			long now = Minecraft.getInstance().level == null ? 0 : Minecraft.getInstance().level.getGameTime();
			return !VoiceClock.settled(now, startedAt) || MachineLoopSound.stillPlaying(Minecraft.getInstance()
				.getSoundManager(), this);
		}

		void moveTo(double x, double y, double z) {
			this.x = x;
			this.y = y;
			this.z = z;
		}

		void end() {
			stop();
		}

		@Override
		public boolean canStartSilent() {
			return true;
		}

		@Override
		public CompletableFuture<AudioStream> getStream(SoundBufferLibrary soundBuffers, Sound sound, boolean looping) {
			Stream made = new Stream(new EngineNotePcm(mix));
			stream = made;
			return CompletableFuture.completedFuture(made);
		}

		@Override
		public void tick() {
			// With no engine left calling in, nothing else would work the scene out and empty the slots
			if (Minecraft.getInstance().level != null)
				EngineRoomScene.update(Minecraft.getInstance().level.getGameTime());
			volume = LOUDEST * CEClientConfig.engineNoteVolume();
			SoundProbe.level("room", this, volume, pitch);
			idle = mix.isSilent() ? idle + 1 : 0;
			Stream current = stream;
			if (idle > IDLE_TICKS || volume <= 0 || current != null && stall.tick(current.reads, System.nanoTime())) {
				LOGGER.debug("Shared engine voice ended (idle {}, volume {})", idle, volume);
				stop();
			}
		}
	}

	/** The synthesizer as the sound engine reads it: mono, 16 bit, a chunk ({@link EngineNotePcm#CHUNK}) at a time. */
	static final class Stream implements AudioStream {

		private static final AudioFormat FORMAT = new AudioFormat(EngineNoteSynth.SAMPLE_RATE, 16, 1, true, false);

		private final EngineNotePcm pcm;
		volatile int reads;

		Stream(EngineNotePcm pcm) {
			this.pcm = pcm;
		}

		@Override
		public AudioFormat getFormat() {
			return FORMAT;
		}

		@Override
		public ByteBuffer read(int size) {
			int read = reads++;
			if (read == 0)
				LOGGER.debug("Engine note stream first read: {} samples asked for", size / 2);
			ByteBuffer made = pcm.read(Math.min(size, EngineNotePcm.lengthOfRead(read) * 2));
			SoundProbe.streamRead(this, made.remaining() / 2);
			return made;
		}

		@Override
		public void close() {
		}
	}
}
