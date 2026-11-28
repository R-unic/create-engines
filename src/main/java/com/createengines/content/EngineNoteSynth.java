package com.createengines.content;

import com.createengines.engine.EngineSound;
import com.createengines.engine.sim.Muffling;

/**
 * An engine's exhaust note, made up as it is heard instead of played from a loop. Every firing the engine's firing order
 * puts in its 720 degree cycle strikes a grain, a single firing synthesized beforehand ({@link EngineGrains}), into the
 * modelled pipe of the outlet it goes out of ({@link ExhaustPipe}), so an even straight six hums at one pitch, a V-twin
 * potato-potatoes, a four with a dead cylinder limps, and a rotary buzzes, all from the same code and nothing recorded.
 * <p>
 * Each firing also sends a rush of gas down its pipe ({@link NoteFlow}), and what leaves the pipes goes through the
 * resonances of the kind of engine it is ({@link NoteTimbre}). Under that are a low thump on each firing, a faint
 * mechanical bed that turns with the crank and, where firings run together, a tone at their rate.
 * <p>
 * What the engine is built of shapes it ({@link Voice}): each exhaust outlet rings its own pipe, tuned by the run's length and
 * the cylinders' size, so the firings an outlet gets, even or uneven, are heard as that outlet's rhythm; compression-ignition
 * fuels knock; and no two firings are quite alike, least of all at idle or on a rough, crude engine. A plain voice is one
 * outlet of middling length, the note as it was before the build shaped it.
 * <p>
 * Pure Java with no Minecraft in it, so the numbers can be tested and rendered to a file. The game thread hands over
 * what the engine is doing with {@link #set}; the audio thread asks for samples with {@link #render} and follows the
 * speed and loudness smoothly between the two.
 */
public final class EngineNoteSynth {

	public static final int SAMPLE_RATE = 32000;

	/**
	 * What the engine is doing.
	 *
	 * @param rpm          crankshaft speed
	 * @param firingAngles where in the 720 degree cycle each cylinder fires, sorted
	 * @param firingShare  the share of firings that light, 0 to 1: the rest are misfires and make a faint puff
	 * @param load         how hard it is working against what it could do, 0 to about 1.3
	 * @param combustion   how much of the note is burning: 1 for a running engine, a quarter for a coasting or cranking one
	 *                     (only air being pumped), 0 for silence
		 * @param size         how big each cylinder sounds, 0 to 1: a big one thumps lower and its crack hangs on longer
		 * @param sharpness    how hard each firing cracks, 0 to 1: boost, a hot engine and a hard-worked one crack harder
	 */
	public record Params(float rpm, float[] firingAngles, float firingShare, float load, float combustion, float size,
							 float sharpness, float roughness, Voice voice) {

			/** An engine of middling size and sharpness. */
			public Params(float rpm, float[] firingAngles, float firingShare, float load, float combustion) {
				this(rpm, firingAngles, firingShare, load, combustion, 0.5f, 0.5f);
			}

			/** An engine that runs smoothly, with a plain voice. */
			public Params(float rpm, float[] firingAngles, float firingShare, float load, float combustion, float size,
						  float sharpness) {
				this(rpm, firingAngles, firingShare, load, combustion, size, sharpness, 0, Voice.PLAIN);
			}

		public static final Params SILENT = new Params(0, new float[0], 1, 0, 0);

		/** The same engine at another speed. */
		Params atRpm(float speed) {
			return new Params(speed, firingAngles, firingShare, load, combustion, size, sharpness, roughness, voice);
		}

		/** The same engine working harder or less hard. */
		Params atLoad(float worked) {
			return new Params(rpm, firingAngles, firingShare, worked, combustion, size, sharpness, roughness, voice);
		}
	}

	/** How the fuel is lit: a spark, or the heat of compression, which knocks. */
	public enum Ignition {
		SPARK, COMPRESSION
	}

	/**
	 * What the engine is built of, as far as it changes the sound. The game works it out from the build; it changes only when
	 * the build does.
	 *
	 * @param outlet     for each firing angle, in the same order, which exhaust outlet it goes out of, 0 to {@link #MOST_OUTLETS}
	 *                   - 1. Banks that share an exhaust share an outlet. Empty: every firing goes out of outlet 0
	 * @param pipeLength for each outlet, how long its exhaust run is, 0 (a stub) to 1 (a long run): a longer one rings lower
	 * @param ignition   spark or compression
	 * @param precision  how much of the engine is precision parts, 0 (all crude) to 1: crude parts fire less evenly
	 * @param enclosure  how much of the engine is clad in casing, 0 to 1: a clad engine is heard dulled, its crack muffled
	 * @param muffle     for each outlet, how much of its gas goes through a muffler, 0 to 1. Empty: every outlet is muffled as
	 *                   much as {@link #setMuffling} says
	 * @param rotary     rotor housings rather than cylinders: it buzzes
	 */
	public record Voice(int[] outlet, float[] pipeLength, Ignition ignition, float precision, float enclosure,
						float[] muffle, boolean rotary) {

		/** A piston engine's voice. */
		public Voice(int[] outlet, float[] pipeLength, Ignition ignition, float precision, float enclosure,
					 float[] muffle) {
			this(outlet, pipeLength, ignition, precision, enclosure, muffle, false);
		}

		/** A voice whose outlets are all muffled alike, as {@link #setMuffling} says. */
		public Voice(int[] outlet, float[] pipeLength, Ignition ignition, float precision, float enclosure) {
			this(outlet, pipeLength, ignition, precision, enclosure, new float[0]);
		}

		/** One outlet of middling length, a smooth spark engine: the note before the build shaped it. */
		public static final Voice PLAIN = new Voice(new int[0], new float[0], Ignition.SPARK, 1f, 0f);

		/** The voice for what the server says the engine is built of; plain for an engine it said nothing about. */
		static Voice of(EngineSound sound) {
			if (sound == null || sound == EngineSound.NONE)
				return PLAIN;
			return new Voice(sound.outlets(), sound.pipes(), sound.compression() ? Ignition.COMPRESSION : Ignition.SPARK,
				sound.precision(), sound.enclosure(), sound.muffles(), sound.rotary());
		}

		/** A voice for an engine with no casing round it. */
		public Voice(int[] outlet, float[] pipeLength, Ignition ignition, float precision) {
			this(outlet, pipeLength, ignition, precision, 0f);
		}

		/** Whether two voices ring the same pipes, so the resonances need no retuning. */
		boolean tunedLike(Voice other) {
			return other != null && java.util.Arrays.equals(pipeLength, other.pipeLength);
		}

		/** How many outlets ring pipes: one more than the highest any firing goes out of. */
		int outlets() {
			int most = 0;
			for (int o : outlet)
				most = Math.max(most, o);
			return most + 1;
		}

		float pipe(int outlet) {
			return outlet < pipeLength.length ? pipeLength[outlet] : 0.5f;
		}
	}

	/** At most this many outlets ring pipes of their own. */
	public static final int MOST_OUTLETS = 4;

	private static final double CYCLE = 720;

	/** Where a middling pipe rings, in hertz, before the cylinders' size and the run's length move it. */
	private static final double PIPE_HZ = 86;
	/** How hard the grains strike the pipes, and how loud the crack and the knock are beside them. */
	private static final float BODY_WEIGHT = 110f;
	private static final float CRACK_WEIGHT = 100f;
	private static final float CRACK_FLOOR = 0.2f;
	private static final float KNOCK_WEIGHT = 120f;
	/** Firings a second at which the crack has faded to half: on each of a hundred a second it would only buzz. */
	private static final float CRACK_FADE_AT = 20;
	/** Firings a second at which one thump has not quite died before the next starts. */
	private static final float THUMPS_BEFORE_PILE_UP = 16;
	private static final float TONE_FROM = 40;
	/** Per-sample share of the way to the asked-for speed and loudness: about a tenth of a second. */
	private static final float FOLLOW = (float) (1 / (0.0907 * SAMPLE_RATE));
	/** Where a middling outlet's boom sits, how loud it is beside the pipes, and the firing rate that thins it. */
	private static final double BOOM_HZ = 55;
	private static final float BOOM_WEIGHT = 10f;
	private static final float BOOMS_BEFORE_PILE_UP = 28f;
	/** How much of a deep engine's crack is taken away. */
	private static final float DEEP_SOFTER = 0.4f;
	/** Overall level, set so a running engine sits around a fifth of full scale. */
	private static final float LEVEL = 0.035f;

	/**
	 * Each outlet's pipe is a little apart from the others, as no two exhaust runs are alike, and the nearer outlets are heard a
	 * little louder: what lets the uneven rhythm of one bank be heard through the even rhythm of the whole.
	 */
	private static final float[] OUTLET_DETUNE = {1f, 1.047f, 0.958f, 1.083f};
	private static final float[] OUTLET_WEIGHT = {1f, 0.8f, 0.9f, 0.72f};

	private final EngineGrains grains = EngineGrains.bank();
	/** Each outlet's pipe, and the grains still being struck into it. */
	private final ExhaustPipe[] pipes = new ExhaustPipe[MOST_OUTLETS];
	private final GrainBus[] blows = new GrainBus[MOST_OUTLETS];
	/** Each outlet's low boom, which a big engine has. */
	private final NoteBoom[] booms = new NoteBoom[MOST_OUTLETS];
	private final NoteRaps raps = new NoteRaps();
	/** The take last struck of each kind, so the next is another. */
	private final int[] blowTake = new int[MOST_OUTLETS];
	private int crackTake;
	private int knockTake;
	private int outlets = 1;
	private int[] outletOf = new int[0];
	private Voice tunedVoice;
	private float tunedSize = Float.NaN;
	/** The closest two firings come, for the jitter. */
	private float closestFirings = (float) CYCLE;
	private double nextJitter;
	private float jitterDegrees;
	private float strengthSpread = 0.1f;

	/**
	 * What the game thread last asked for, with what follows from it worked out there (the closest two
	 * firings), so the audio thread allocates nothing. One object, so the audio thread never sees the parts of two.
	 */
	private record Prepared(Params params, float closest, int outlets, float[] lope, float depth, NoteTimbre timbre) {
	}

	private volatile Prepared prepared = prepare(Params.SILENT);
	/** The kind of engine the note is heard as, kept from the last that listed its firings. */
	private NoteTimbre timbre = prepared.timbre();
	/** That tone as it sounds at the present speed. */
	private final NoteOpening opening = new NoteOpening();
	private NoteTimbre heardAs = timbre;
	private final NoteBody openBody = new NoteBody();
	private final NoteBody shutBody = new NoteBody();
	private final NoteFlow flow = new NoteFlow();
	private final NoteSystem openSystem = new NoteSystem(0x6C8E9CF5);
	private final NoteSystem shutSystem = new NoteSystem(0x3AD7B15B);
	private float flowShare = 1;
	/** The last of them the audio thread took up. */
	private Prepared taken;
	private float[] angles = new float[0];

	private double phase;
	private int nextEvent;
	private float rpm;
	private float load;
	private float combustion;
	private final NoteUndertone under = new NoteUndertone();
	/** The one-offs played in this note's own stream: backfires, spits and a stopped crank rocking to rest. */
	private final NoteBangs bangs = new NoteBangs();
	private final NoteExit exit = new NoteExit();
	private int random;

	public EngineNoteSynth(int seed) {
		random = seed == 0 ? 0x2545F491 : seed;
		for (int o = 0; o < MOST_OUTLETS; o++) {
			pipes[o] = new ExhaustPipe();
			blows[o] = new GrainBus();
			booms[o] = new NoteBoom();
		}
		tune(0.5f, Voice.PLAIN);
	}

	/**
	 * How much lower or higher a pipe rings than the middling one: a bigger cylinder and a longer run ring lower. 1 for a
	 * cylinder of middling size on a run of middling length, the pitch the note had before the build shaped it.
	 */
	static double pitch(float size, float pipe) {
		return (1.3 - 0.6 * size) * pipeTone(pipe);
	}

	/**
	 * Octaves a stub rings above a middling run, and a long run below it. Far less room downward than upward: taken much
	 * lower than this, a long run's boom drops under what is heard and leaves only the grit above it.
	 */
	private static final double STUB_OCTAVES = 1;
	private static final double LONG_OCTAVES = 0.4;

	/** How much higher (above 1) or lower a run rings than a middling one. */
	private static double pipeTone(double pipe) {
		return Math.pow(2, 2 * (0.5 - pipe) * (pipe < 0.5 ? STUB_OCTAVES : LONG_OCTAVES));
	}

	/** Tunes each outlet's pipe for the cylinders' size and the outlet's run; only when either changes. */
	private void tune(float size, Voice voice) {
		tunedSize = size;
		tunedVoice = voice;
		flow.sized(size);
		for (int o = 0; o < MOST_OUTLETS; o++) {
			pipes[o].tune(PIPE_HZ * pitch(size, voice.pipe(o)) * OUTLET_DETUNE[o]);
			booms[o].tune(boomNote(voice.pipe(o)) * OUTLET_DETUNE[o] * deep());
		}
	}

	/** Where the revs have the voice ({@link NoteRev}). */
	private float revved = 1;

	/** The boom and the thump follow the revs only up to the usual limit: past it they stay, as the engine's weight. */
	private float deep() {
		return Math.min(1, revved);
	}

	/** Where an outlet's pipe rings now, in hertz. */
	double pipeNote(int outlet) {
		return pipes[outlet].note();
	}

	/** Moves everything that rings to where it does at these revs. */
	private void rev(float times) {
		if (times == revved)
			return;
		revved = times;
		for (int o = 0; o < MOST_OUTLETS; o++) {
			pipes[o].bend(times);
			booms[o].tune(boomNote(tunedVoice.pipe(o)) * OUTLET_DETUNE[o] * deep());
		}
		openBody.bend(times);
		shutBody.bend(times);
		openSystem.bend(times);
		shutSystem.bend(times);
	}

	/** Where an outlet's boom sits, in hertz: a longer run takes it a little lower, but never out of hearing. */
	static double boomNote(float pipe) {
		return BOOM_HZ * Math.pow(pipeTone(pipe), 0.35);
	}

	/**
	 * About how long after a sample is rendered it is heard: it waits behind the three other chunks
	 * ({@link EngineNotePcm#CHUNK}) queued with the sound engine. The game thread aims the cycle this far ahead of the engine, so a firing is heard as the engine fires.
	 */
	public static final double OUTPUT_LATENCY = EngineNoteClock.LEAD;
	/** Per-sample share of a phase error closed: about a fifth of a second, so the correction is a glide and never a click. */
	private static final double LOCK_FOLLOW = 1 / (0.2 * SAMPLE_RATE);
	/** The most the cycle may be sped up or slowed by catching up, as a share of its own speed; it never runs backwards. */
	private static final double LOCK_SLEW = 0.35;
	/** An error this large is a different place in the cycle, not drift, and the cycle jumps there. */
	private static final double LOCK_SNAP = 180;

	private volatile double lockTarget = Double.NaN;
	private boolean locked;
	private double correction;

	/**
	 * Called by the game thread with where the engine's cycle will be, in degrees, at the moment the sound now being
	 * rendered is heard. The synthesizer keeps its own clock, which would otherwise wander against the engine's real
	 * crank (a speed it follows a tenth of a second late, the stream's own delay, a start at any angle) so firings were
	 * heard off from the flames and puffs that follow the cam. It closes the gap smoothly and snaps only on the first
	 * call or a large error.
	 */
	public void lockTo(double cycleDegrees) {
		if (Double.isFinite(cycleDegrees))
			lockTarget = ((cycleDegrees % CYCLE) + CYCLE) % CYCLE;
	}

	/** Where the cam was, and when: what the game thread saw, aimed ahead by the audio thread when it takes it up. */
	private record Sighting(double degrees, long atNanos) {
	}

	private volatile Sighting sighting;
	private final EngineNoteClock clock = new EngineNoteClock();
	private long rendered;
	/** Sightings in a row that put the cycle somewhere else altogether. */
	private int astray;
	/** The share of the way to what a sighting says, so one read late or between frames moves the cycle only part way. */
	private static final double TRUST = 0.5;

	/**
	 * Called by the game thread with where the engine's cam was, in cycle degrees, at {@code atNanos}. The audio thread
	 * works out how long after that the sample it is rendering is heard, and aims there at the engine's speed.
	 */
	public void sight(double cycleDegrees, long atNanos) {
		if (Double.isFinite(cycleDegrees))
			sighting = new Sighting(cycleDegrees, atNanos);
	}

	/** For a synthesizer put into a stream already going; on the audio thread, before its first sample. */
	void joinRunningStream() {
		clock.joinRunningStream();
	}

	/** Takes up what the game thread last saw or asked for; on the audio thread, before each block of samples. */
	void applyLock(long nowNanos) {
		applyLock();
		long heard = clock.heardAt(rendered, nowNanos);
		blockSample = rendered;
		blockHeard = heard;
		Sighting seen = sighting;
		// A stall winds the cycle down itself: the cam has already stopped, or is about to
		if (dying())
			return;
		// Until it has locked, the speed has not caught up from standstill, and aiming ahead with part of it lands short
		if (seen != null && !locked)
			rpm = prepared.params().rpm;
		if (seen == null || rpm < 1)
			return;
		sighting = null;
		double target = seen.degrees + rpm * 6.0 * (heard - seen.atNanos) * 1e-9;
		target = (target % CYCLE + CYCLE) % CYCLE;
		double error = ((target - phase) % CYCLE + CYCLE * 1.5) % CYCLE - CYCLE / 2;
		if (!locked)
			jump(target);
		else if (Math.abs(error) <= LOCK_SNAP) {
			astray = 0;
			correction = error * TRUST;
		} else if (++astray >= 2)
			// One wild reading is a hitch; two are a different place in the cycle
			jump(target);
	}

	/** The first sample of the block being rendered, and when it is heard. */
	private long blockSample;
	private long blockHeard;

	/** Says when a bang that went off with the sample just rendered is heard. */
	private void cueStarted() {
		NoteCue cue = bangs.takeStarted();
		if (cue != null)
			cue.heardAt(blockHeard + (long) ((rendered - blockSample) * 1e9 / SAMPLE_RATE));
	}

	/** Where in its 720 degree cycle the next sample is. */
	double cycleDegrees() {
		return phase;
	}

	private void jump(double target) {
		astray = 0;
		locked = true;
		phase = target;
		correction = 0;
		nextEvent = firstAfter(angles, phase);
		nextJitter = drawJitter(nextEvent);
	}

	/** Takes up a new aim from the game thread; on the audio thread, once per block of samples. */
	void applyLock() {
		double target = lockTarget;
		if (Double.isNaN(target))
			return;
		lockTarget = Double.NaN;
		double error = ((target - phase) % CYCLE + CYCLE * 1.5) % CYCLE - CYCLE / 2;
		if (!locked || Math.abs(error) > LOCK_SNAP)
			jump(target);
		else
			correction = error;
	}

	/** Called by the game thread; takes effect over the next tenth of a second. Ignored while a stall is being played out. */
	public void set(Params next) {
		if (dying())
			return;
		Prepared made = prepare(NoteInput.clean(next));
		prepared = made;
		if (made.params.combustion > 0 && made.params.rpm > 1 && made.params.firingAngles.length > 0)
			lastTurning = made;
	}

	private static Prepared prepare(Params clean) {
		int ringing = Math.min(MOST_OUTLETS, clean.voice.outlets());
		float[] lope = EngineNoteVoicing.lope(clean.firingAngles, clean.voice.outlet());
		return new Prepared(clean, NoteInput.closest(clean.firingAngles), ringing, lope,
			EngineNoteVoicing.depth(clean.firingAngles.length), NoteTimbre.of(lope, NoteInput.meanRun(clean.voice, ringing),
				NoteInput.unequalRuns(clean.voice, ringing), clean.voice.ignition() == Ignition.COMPRESSION,
				clean.voice.rotary()));
	}

	/** The catch or stall being played, if any. */
	private volatile EngineNoteGesture gesture;
	/** What was last asked for while the engine turned, which a stall winds down from. */
	private Prepared lastTurning;
	/** How much of the note burns while a stall winds down, whatever little the engine was burning as it stopped. */
	private static final float DYING_COMBUSTION = 0.85f;

	/**
	 * Plays a catch or a stall with the note's own firings, from the game thread.
	 * A stall winds down from the speed the note was last playing, so there is no jump where it begins.
	 *
	 * @return the speed a stall winds down from, 0 for a catch, or -1 for a stall of a note with nothing turning to wind
	 *         down
	 */
	float perform(EngineNoteGesture.Kind kind) {
		float from = 0;
		if (kind.dies()) {
			Prepared held = lastTurning;
			if (held == null)
				return -1;
			lastTurning = null;
			gesture = null;
			prepared = held;
			from = held.params.rpm;
		}
		gesture = new EngineNoteGesture(kind, from);
		return from;
	}

	/** Drops whatever catch or stall is playing, for an engine that has been turned again. */
	void endGesture() {
		gesture = null;
		bangs.dropRest();
	}

	/** Plays a backfire or a spit in this note, from the game thread: see {@link NoteBangs}. */
	NoteCue bang(NoteBangs.Kind kind, float strength, float pitch) {
		return bangs.ask(kind, strength, pitch);
	}

	/** Whether a stalled engine's crank is still rocking to rest. */
	boolean resting() {
		return bangs.resting();
	}

	/** Whether a stall is still winding down, and so the note has not finished. */
	boolean dying() {
		EngineNoteGesture current = gesture;
		return current != null && current.kind.dies();
	}

	private volatile float wantedFar;
	private float far;

	/**
	 * How far the listener is, 0 (beside it) to 1 (at the edge of hearing), from the game thread. Far off, the crack and the
	 * knock fade first and the note is heard duller: only the boom of the exhaust carries.
	 */
	public void setDistance(float share) {
		wantedFar = Float.isFinite(share) ? Math.clamp(share, 0f, 1f) : 0f;
	}

	private volatile float wantedMuffling;
	/** How muffled each outlet is, following what is asked. */
	private final float[] muffled = new float[MOST_OUTLETS];
	/** The outlet of the latest firing, and how muffled what every cylinder shares is now. */
	private int firingOutlet;
	private float firingMuffle;
	private float mostMuffled;
	private static final float MUFFLE_SWITCH = (float) (1 - Math.exp(-1 / (0.002 * SAMPLE_RATE)));
	/**
	 * How much of the exhaust goes through a muffler, 0 to 1, from the game thread, for a voice that does not say outlet by
	 * outlet ({@link Voice#muffle}). A muffler works on the exhaust alone: it
	 * is heard quieter and duller, its crack and knock going first.
	 */
	public void setMuffling(float share) {
		wantedMuffling = Float.isFinite(share) ? Math.clamp(share, 0f, 1f) : 0f;
	}

	/** Whether there is nothing left to hear: the engine is quiet and the pipe has stopped ringing. */
	public boolean isSilent() {
		for (int o = 0; o < MOST_OUTLETS; o++)
			if (!blows[o].idle() || o < outlets && !(pipes[o].isSilent() && booms[o].isSilent()))
				return false;
		return prepared.params().combustion <= 0 && combustion < 0.001f && raps.idle()
			&& openSystem.isSilent() && shutSystem.isSilent()
			&& under.isSilent() && flow.isStill() && bangs.idle();
	}

	/** Fills {@code out[offset, offset + count)} with 16-bit samples. */
	public void render(short[] out, int offset, int count) {
		applyLock(System.nanoTime());
		for (int i = 0; i < count; i++)
			out[offset + i] = (short) Math.round(Math.clamp(next(), -1f, 1f) * 30000);
	}

	/** One sample, -1 to 1. */
	public float next() {
		rendered++;
		EngineNoteGesture playing = gesture;
		if (playing != null) {
			playing.advance();
			if (playing.over()) {
				if (playing.kind.dies()) {
					// The crank has stopped: quiet at once, and not back up to speed before the game thread next says so
					prepared = prepare(Params.SILENT);
					rpm = combustion = 0;
					locked = false;
					correction = 0;
					bangs.rest(playing.kind.slam, playing.kind.sigh);
				}
				if (gesture == playing)
					gesture = null;
				playing = null;
			}
		}
		Prepared state = prepared;
		Params p = state.params();
		rpm += (p.rpm - rpm) * FOLLOW;
		load += (p.load - load) * FOLLOW;
		combustion += (p.combustion - combustion) * FOLLOW;
		if (playing != null && playing.kind.dies()) {
			rpm = playing.rpm();
			combustion = DYING_COMBUSTION;
		}
		if (state != taken) {
			taken = state;
			outletOf = p.voice.outlet();
			outlets = state.outlets();
			if (p.firingAngles.length > 0)
				timbre = state.timbre();
			closestFirings = state.closest();
			// Each sync brings a new array, mostly with the same firings in it: finding the place again would skip one still waiting
			if (!java.util.Arrays.equals(p.firingAngles, angles)) {
				angles = p.firingAngles;
				nextEvent = firstAfter(angles, phase);
				nextJitter = drawJitter(nextEvent);
			}
			if (p.size != tunedSize || !p.voice.tunedLike(tunedVoice))
				tune(p.size, p.voice);
		}

		double advance = rpm * 6.0 / SAMPLE_RATE;
		double trim = Math.clamp(correction * LOCK_FOLLOW, -LOCK_SLEW * advance, LOCK_SLEW * advance);
		correction -= trim;
		phase += advance + trim;
		fireDue(p);
		if (phase >= CYCLE) {
			phase %= CYCLE;
			nextEvent = 0;
			nextJitter = drawJitter(0);
			fireDue(p);
		}

		float piped = 0;
		float pipedShut = 0;
		double boomed = 0;
		double boomedShut = 0;
		boolean breathing = !flow.isStill();
		if (breathing)
			flow.advance();
		float breath = timbre.flow() * flowShare;
		// Each outlet's grains and rush of gas go through its own pipe
		for (int o = 0; o < outlets; o++) {
			float boom = booms[o].isStill() ? 0 : booms[o].next();
			float into = blows[o].take();
			if (breathing)
				into += flow.next(o) * breath;
			float mouth = pipes[o].next(into) * OUTLET_WEIGHT[o];
			double under55 = boom * BOOM_WEIGHT * boomShare * OUTLET_WEIGHT[o];
			piped += mouth * (1 - muffled[o]);
			pipedShut += mouth * muffled[o];
			boomed += under55 * (1 - muffled[o]);
			boomedShut += under55 * muffled[o];
		}
		// The boom is under the resonances, not through them: they would take it away
		double body = openBody.next(openSystem.next(piped, heardAs), heardAs) + boomed;
		double behind = mostMuffled > 0.001f
			? shutBody.next(shutSystem.next(pipedShut, heardAs), heardAs) + boomedShut
			: pipedShut + boomedShut;

		if ((slowTick++ & (SLOW_EVERY - 1)) == 0)
			refreshSlow();
		float shared = under.next(noise(), phase, rpm, combustion, pileUp * heardAs.thump(),
			toneShare * (1 - heardAs.thin()));

		// Far off or behind casing, the sharp parts go first
		float sharp = highShare;
		// What every cylinder shares is muffled as the outlet of the latest firing is, so a muffled bank is heard as muffled
		// firings between open ones
		firingMuffle += MUFFLE_SWITCH * (muffled[firingOutlet] - firingMuffle);
		float open = (float) (body + shared * (1 - firingMuffle) + raps.open() * sharp) * scale * 2;
		float shut = (float) (behind + shared * firingMuffle + raps.shut() * sharp) * scale * 2;
		// Only a fast engine is levelled, so the lope of a slow one is left alone, a stopping engine is not turned up, and a
		// note of a pipe the firing rate happens to hit is turned down
		float out = exit.next(open, shut, toneShare * Math.min(1f, combustion * 2), mostMuffled > 0.001f);
		if (!Float.isFinite(out)) {
			reset();
			return 0;
		}
		if (bangs.idle())
			return out;
		out = EngineRoomMix.limit(out + bangs.next());
		cueStarted();
		return out;
	}

	/**
	 * How the note's mix depends on the firing rate. The rate follows the speed over about a tenth of a second, so these
	 * (two square roots, a power and divisions) are worked out once every {@link #SLOW_EVERY} samples, about 0.7 ms, instead
	 * of for every sample.
	 */
	private static final int SLOW_EVERY = 16;
	/** Per-slow-step shares of the way distance (about a fifth of a second) and muffling (about a fourteenth) move. */
	private static final float SLOW_GLIDE = (float) (SLOW_EVERY / (0.18 * SAMPLE_RATE));
	private static final float MUFFLE_GLIDE = (float) (SLOW_EVERY / (0.073 * SAMPLE_RATE));
	private int slowTick;
	private float pileUp = 1;
	private float crackShare = 1;
	private float toneShare;
	private float scale = LEVEL;
	private float boomShare;

	private void refreshSlow() {
		rev(NoteRev.of(rpm,
			timbre.burbles() ? Math.max(NoteRev.FULL_RPM, NoteOpening.openedBy(angles.length)) : NoteRev.FULL_RPM));
		float eventsPerSecond = angles.length * rpm / 120f;
		float pile = eventsPerSecond / THUMPS_BEFORE_PILE_UP;
		pileUp = 1 / (float) Math.sqrt(1 + pile * pile);
		float fade = eventsPerSecond / CRACK_FADE_AT;
		crackShare = 1 / (1 + fade * fade);
		heardAs = opening.at(timbre, eventsPerSecond);
		openSystem.pace(eventsPerSecond);
		shutSystem.pace(eventsPerSecond);
		float rate = eventsPerSecond * eventsPerSecond;
		toneShare = rate / (rate + TONE_FROM * TONE_FROM);
		scale = LEVEL / (float) Math.pow(1 + 0.03f * eventsPerSecond, 0.7);
		flowShare = Math.min(1f, BARE_BREATH + eventsPerSecond / BREATHS_COVERED_AT)
			/ (float) Math.sqrt(1 + eventsPerSecond / BREATHS_BEFORE_PILE_UP);
		// The boom is there to carry from one firing to the next. Where they come close enough to carry themselves it goes,
		// steeply, or it would ring on what differs between them and blur the engine's rhythm
		float crowd = eventsPerSecond / BOOMS_BEFORE_PILE_UP;
		boomShare = taken == null ? 0 : taken.depth() * (1 - heardAs.thin()) / (1 + crowd * crowd * crowd * crowd);
		float knocks = eventsPerSecond / KNOCK_FADE_AT;
		knockShare = Math.max(KNOCK_FLOOR, 1 / (1 + knocks * knocks));
		// No two firings alike: a little apart in time and strength, more at idle, on a rough engine and with crude parts
		Params p = prepared.params();
		raps.ignition(p.voice().ignition() == Ignition.COMPRESSION);
		// Idle is below half load; a working engine fires as evenly as its parts allow
		float idle = Math.max(0f, 1 - 2 * load);
		float crude = 1 - p.voice().precision();
		// Capped in time as well as in degrees: a slow engine's few degrees are long enough to be heard as bad timing
		float mostLate = rpm * 6 * MOST_LATE;
		float scatter = rpm * 6 * SCATTER;
		jitterDegrees = Math.min(Math.min(0.3f * closestFirings, mostLate),
			scatter + 1.5f * idle + 8 * p.roughness() + 2 * crude);
		strengthSpread = Math.min(0.6f, 0.2f + 0.15f * idle + 0.35f * p.roughness() + 0.1f * crude);
		// Distance follows over about a fifth of a second (this runs every 16 samples)
		far += (wantedFar - far) * SLOW_GLIDE;
		float[] asked = p.voice().muffle();
		float most = 0;
		for (int o = 0; o < MOST_OUTLETS; o++) {
			muffled[o] += ((asked.length > o ? asked[o] : wantedMuffling) - muffled[o]) * MUFFLE_GLIDE;
			if (o < outlets)
				most = Math.max(most, muffled[o]);
		}
		mostMuffled = most;
		for (int o = 0; o < MOST_OUTLETS; o++)
			pipes[o].muffle(muffled[o]);
		float clad = p.voice().enclosure();
		highShare = (1 - 0.8f * far) * (1 - 0.7f * clad);
		exit.dulledBy(clad, far);
		bangs.dulledBy(clad, far);
	}

	/** Firings a second at which the rushes of gas have begun to run together, and each is turned down. */
	private static final float BREATHS_BEFORE_PILE_UP = 60;
	/** How much of a rush is heard with firings far apart, where it is bare, and about how often covers it. */
	private static final float BARE_BREATH = 0.4f;
	private static final float BREATHS_COVERED_AT = 80;

	/** How far apart in time, in seconds, even a smooth engine's firings fall from where the cam puts them. */
	private static final float SCATTER = 0.0007f;

	/** The latest a firing's variation may make it, in seconds: too little to be heard against the flash it belongs to. */
	static final float MOST_LATE = 0.006f;

	private float knockShare = 1;
	/** How much of the crack and knock is heard, 0 to 1. */
	private float highShare = 1;

	/** Firings a second at which the knock has faded to half, and the share of it a fast diesel keeps. */
	private static final float KNOCK_FADE_AT = 40;
	private static final float KNOCK_FLOOR = 0.15f;

	/** Back to a quiet, stopped engine, if the arithmetic ever goes wrong. */
	private void reset() {
		for (int o = 0; o < MOST_OUTLETS; o++) {
			pipes[o].clear();
			blows[o].clear();
			booms[o].clear();
		}
		raps.clear();
		nextJitter = 0;
		exit.clear();
		firingMuffle = 0;
		firingOutlet = 0;
		phase = 0;
		nextEvent = 0;
		gesture = null;
		locked = false;
		correction = 0;
		rpm = load = combustion = 0;
		under.clear();
		flow.clear();
		openBody.clear();
		openSystem.clear();
		shutSystem.clear();
		shutBody.clear();
		bangs.clear();
	}

	/** Strikes every firing whose crank angle the cycle has now reached. */
	private void fireDue(Params p) {
		while (nextEvent < angles.length && angles[nextEvent] + nextJitter <= phase) {
			fire(nextEvent, p);
			nextEvent++;
			nextJitter = drawJitter(nextEvent);
		}
	}

	/** How late the firing at {@code index} lands, in degrees: never so late it passes the next one or the end of the cycle. */
	private double drawJitter(int index) {
		if (index >= angles.length || jitterDegrees <= 0)
			return 0;
		double most = Math.min(jitterDegrees, CYCLE - 0.01 - angles[index]);
		return Math.max(0, most) * (noise() + 1) * 0.5;
	}

	private void fire(int index, Params p) {
		firingOutlet = index < outletOf.length ? outletOf[index] : 0;
		bangs.fired(pipes[firingOutlet].note(), muffled[firingOutlet]);
		// Up to strengthSpread weaker than the cylinder's best, a fifth on a smooth engine at load
		float strength = cylinderGain(index) * (1 + 0.15f - strengthSpread * 0.5f * (noise() + 1));
		float[] lope = taken.lope();
		if (index < lope.length)
			strength *= lope[index];
		float depth = taken.depth();
		boolean lit = (noise() + 1) * 0.5f < p.firingShare;
		EngineNoteGesture playing = gesture;
		if (playing != null) {
			float share = playing.strength();
			if (share == EngineNoteGesture.MISS)
				lit = false;
			else
				strength *= share;
		}
		float burn = combustion * (lit ? 1f : 0.18f);
		if (burn <= 0)
			return;
		under.pressure(strength * burn);
		boolean compression = p.voice().ignition() == Ignition.COMPRESSION;
		strikeBlow(firingOutlet, compression, p.size, lit ? p.load() : 0, strength * burn * BODY_WEIGHT);
		// A hard-worked cylinder empties with more of a rush than an idling one
		flow.breathe(firingOutlet, strength * burn * BODY_WEIGHT * (lit ? 0.55f + 0.45f * Math.min(1f, p.load()) : 0.3f));
		if (depth > 0)
			booms[firingOutlet].strike(strength * burn);
		if (lit && compression) {
			// A diesel knocks hardest at idle and light load, where the charge lights all at once
			float knock = strength * burn * (0.6f + 0.6f * (1 - Math.min(1f, p.load())));
			knockTake = nextTake(knockTake, unitNoise());
			raps.strike(grains.knock(knockTake), knock * knockShare * KNOCK_WEIGHT, muffled[firingOutlet]);
		}
		if (lit) {
			// A bigger cylinder thumps lower, and each has its own pitch within that
			// A long run takes it lower, a stub higher
			float run = (float) Math.sqrt(pipeTone(p.voice().pipe(firingOutlet)));
			under.thump((0.92f + 0.16f * (cylinderGain(index) - 0.88f) / 0.24f) * (1.22f - 0.44f * p.size) * run
				* deep());
			// A small, hot cylinder cracks harder than a big, cool one, and the grains by size crack higher
			float crack = strength * (CRACK_FLOOR + (1 - CRACK_FLOOR) * p.sharpness) * (0.7f + 0.3f * Math.min(1f, p.load)) * burn;
			crackTake = nextTake(crackTake, unitNoise());
			raps.strike(grains.crack(sizeSet(p.size), crackTake), crack * crackShare * CRACK_WEIGHT * (1 - DEEP_SOFTER * depth),
				muffled[firingOutlet]);
		}
	}

	/** Which of the grains' cylinder sizes is nearest. */
	private static int sizeSet(float size) {
		return Math.round(size * (EngineGrains.SIZES - 1));
	}

	/** The body of a firing into its outlet's pipe: the same take of the two loads either side, so load moves it smoothly. */
	private void strikeBlow(int outlet, boolean compression, float size, float load, float gain) {
		int take = blowTake[outlet] = nextTake(blowTake[outlet], unitNoise());
		float place = Math.min(1f, load) * (EngineGrains.LOADS - 1);
		int under = Math.min((int) place, EngineGrains.LOADS - 2);
		float over = place - under;
		blows[outlet].strike(grains.body(compression, sizeSet(size), under, take), gain * (1 - over));
		blows[outlet].strike(grains.body(compression, sizeSet(size), under + 1, take), gain * over);
	}

	/** A take other than {@code previous}, each of the others as likely, for {@code pick} from 0 to 1. */
	static int nextTake(int previous, float pick) {
		int step = Math.min(EngineGrains.TAKES - 2, (int) (pick * (EngineGrains.TAKES - 1)));
		return (previous + 1 + Math.max(0, step)) % EngineGrains.TAKES;
	}

	private float unitNoise() {
		return Math.clamp((noise() + 1) * 0.5f, 0f, 1f);
	}

	/** A little difference between cylinders, the same each time for the same one. */
	private static float cylinderGain(int index) {
		int hash = index * 0x9E3779B1;
		hash ^= hash >>> 15;
		return 0.88f + 0.24f * ((hash & 0xFFFF) / 65535f);
	}

	private static int firstAfter(float[] angles, double phase) {
		int index = 0;
		while (index < angles.length && angles[index] <= phase)
			index++;
		return index;
	}

	/** White noise, -1 to 1. */
	private float noise() {
		random ^= random << 13;
		random ^= random >>> 17;
		random ^= random << 5;
		return random / (float) Integer.MAX_VALUE;
	}
}
