package com.createengines.content;

import java.util.concurrent.atomic.AtomicReference;

/**
 * The one-offs an engine note plays itself, so they are made of the same grains and ring the same pipe as its firings: a
 * backfire, the spit of a charge that boost lit early, and the crank rocking to rest at the end of a stall. A backfire or a
 * spit waits for the engine's next firing and goes out of that firing's outlet, so one from a long run sounds like that run;
 * with nothing firing it goes at once. They have a pipe and a last stage of their own, so a bang does not turn the note
 * down. The game thread asks; the sound thread does the rest, allocating nothing. Pure Java, so it is tested.
 */
final class NoteBangs {

	enum Kind {
		POP, SPIT
	}

	private record Request(Kind kind, float strength, float pitch, NoteCue cue) {
	}

	private static final int RATE = EngineNoteSynth.SAMPLE_RATE;
	/** The longest a bang waits for a firing to go out with. */
	static final int MOST_WAIT = RATE / 10;
	/** From what leaves the pipe to full scale, as the note scales its own. */
	private static final float SCALE = 0.07f;
	/** A backfire: the blow-down into the pipe, a soft edge on it, the burning gas and the thud of it going off. */
	private static final float POP_BODY = 100f;
	private static final float POP_CRACK = 28f;
	private static final float POP_FLAME = 4.5f;
	private static final float POP_THUD = 0.6f;
	/** How much of the burning gas is heard straight from the mouth, beside what goes down the pipe. */
	private static final float POP_MOUTH = 0.28f;
	/** A spit: a small blow-down and a soft breath of burning gas. */
	private static final float SPIT_BODY = 75f;
	private static final float SPIT_JET = 2.2f;
	private static final float POP_FIZZ = 0.006f;
	private static final float SPIT_FIZZ = 0.012f;
	private static final float ROCK_BODY = 170f;
	private static final float SIGH = 3f;

	/** When the crank, stopped, rocks back off compression and settles, and how hard each time. */
	private static final int[] ROCK_AT = {0, (int) (0.15 * RATE), (int) (0.25 * RATE)};
	private static final float[] ROCK_FORCE = {0.5f, 0.26f, 0.11f};

	/**
	 * The burning gas of a backfire: dull noise that takes a few thousandths of a second to open, so it lands as a bang
	 * and not as a click, and is gone in about a tenth of a second.
	 */
	private static final float FLAME_POLE = pole(1100);
	private static final float FLAME_FLOOR_POLE = pole(130);
	private static final float FLAME_RISE = pole(65);
	private static final float FLAME_FALL = fall(0.032);
	/** The thud of the gas going off at the mouth drops from above the pipe's note to under it. */
	private static final double THUD_LOW = 0.6;
	private static final double THUD_DROP = 0.7;
	private static final float THUD_DROPS = fall(0.014);
	private static final float THUD_RISE = pole(55);
	private static final float THUD_FALL = fall(0.045);
	/** A backfire often goes off again: how long after, in samples, and how hard beside the first. */
	private static final int[] AGAIN_AFTER = {(int) (0.055 * RATE), (int) (0.065 * RATE)};
	private static final int AGAIN_SPREAD = (int) (0.045 * RATE);
	private static final float[] AGAIN_FORCE = {0.45f, 0.25f};
	/** How long a bang's blow-down takes to open fully: on its own edge it is a click. */
	private static final float OPENS = 1 / (0.006f * RATE);
	private static final float JET_FLOOR_POLE = pole(220);
	/** The spit's breath starts fairly bright, far under a hiss, and darkens. */
	private static final float JET_FIRST = pole(2600);
	private static final float JET_LAST = pole(650);
	private static final float JET_DARKENS = fall(0.03);
	private static final float JET_RISE = pole(32);
	private static final float JET_FALL = fall(0.045);
	private static final float FIZZ_FLOOR_POLE = pole(2200);
	private static final float FIZZ_FALL = fall(0.085);
	private static final float FIZZ_RISE = pole(8);
	private static final float SIGH_POLE = pole(900);
	private static final float SIGH_FALL = fall(0.22);
	private static final float SILENT = 0.0005f;

	private static float pole(double hz) {
		return (float) (1 - Math.exp(-2 * Math.PI * hz / RATE));
	}

	private static float fall(double seconds) {
		return (float) Math.exp(-1 / (seconds * RATE));
	}

	private final EngineGrains grains = EngineGrains.bank();
	private final ExhaustPipe pipe = new ExhaustPipe();
	private final GrainBus blows = new GrainBus();
	private final GrainBus raps = new GrainBus();
	private final NoteExit exit = new NoteExit();
	private final AtomicReference<Request> asked = new AtomicReference<>();
	private int waited;
	private double lastNote = 86;
	private float lastMuffle;
	/** The cue of a bang that has just gone off, until the synthesizer has said when it is heard. */
	private NoteCue started;

	/** How hard the burning gas is being driven, and how loud it is as it follows. */
	/** How far open the latest blow-down is, 0 to 1. */
	private float opened;
	private float flameDrive;
	private float flame;
	private float flameLow;
	private float flameSoft;
	private float flameFloor;
	private float thudDrive;
	private float thud;
	private float thudHigh;
	private double thudPhase;
	private double thudNote;
	/** Samples until the backfire goes off again, how many more times it does, and how many it already has. */
	private int againIn;
	private int againLeft;
	private int againDone;
	private float againStrength;
	private float jetDrive;
	private float jet;
	private float jetBright;
	private float jetLow;
	private float jetSoft;
	private float jetFloor;
	private float fizz;
	private float fizzIn;
	private float fizzFloor;
	/** Samples into the crank's coming to rest, or -1. The sound thread's own, but read by the game thread. */
	private volatile int rest = -1;
	/** Set by the game thread to have the sound thread drop the rest: only that thread may move it on. */
	private volatile boolean dropped;
	private float slam;
	private float sigh;
	private float sighLow;
	private float tail;
	private int take;
	private int random = 0x5F3759DF;
	/** Whether anything has been started that has not rung out. A bang's blow-down opens from nothing. */
	private boolean sounding;

	/**
	 * Asks for a bang, from the game thread. Of two that have not yet gone off, the stronger is kept, and both have the
	 * one cue.
	 *
	 * @return the cue that says when it is heard, or null for a bang that makes no sense
	 */
	NoteCue ask(Kind kind, float strength, float pitch) {
		if (!(strength > 0) || !Float.isFinite(strength) || !Float.isFinite(pitch))
			return null;
		Request wanted = new Request(kind, Math.min(strength, 2), Math.clamp(pitch, 0.5f, 2), new NoteCue());
		return asked.accumulateAndGet(wanted, (held, next) -> {
			if (held == null)
				return next;
			return held.strength > next.strength ? held
				: new Request(next.kind, next.strength, next.pitch, held.cue);
		}).cue;
	}

	/** The cue of a bang that has gone off since this was last asked, or null; on the sound thread. */
	NoteCue takeStarted() {
		NoteCue cue = started;
		started = null;
		return cue;
	}

	/** The engine fired out of a pipe ringing at {@code note} hertz: a bang that has been waiting goes with it. */
	void fired(double note, float muffle) {
		lastNote = note;
		lastMuffle = muffle;
		Request held = asked.get();
		if (held != null && asked.compareAndSet(held, null))
			start(held);
	}

	/** The crank has come to a stop: it rocks off compression as hard as {@code slam}, and the pipe sighs out. */
	void rest(float slam, float sigh) {
		this.slam = slam;
		this.sigh = sigh;
		sighLow = 0;
		dropped = false;
		rest = 0;
		tune(1);
	}

	/** Drops the rest of a stall's end, for an engine turned again; from the game thread. */
	void dropRest() {
		if (rest >= 0)
			dropped = true;
	}

	/** Whether the crank is still rocking to rest. */
	boolean resting() {
		return rest >= 0 && !dropped;
	}

	/** Whether there is nothing to hear and nothing waiting. */
	boolean idle() {
		return !sounding && rest < 0 && asked.get() == null;
	}

	/** How much casing and distance dull what is heard, as for the note. */
	void dulledBy(float clad, float far) {
		exit.dulledBy(clad, far);
	}

	private void tune(float pitch) {
		pipe.tune(lastNote * pitch);
		pipe.muffle(lastMuffle);
		sounding = true;
		tail = 1;
	}

	private void start(Request bang) {
		waited = 0;
		started = bang.cue;
		tune(bang.pitch);
		float strength = bang.strength;
		fizzIn = 0;
		opened = 0;
		if (bang.kind == Kind.POP) {
			goOff(strength);
			// None, one or two more on its heels, so no two backfires are alike
			againLeft = Math.min(AGAIN_FORCE.length, (int) (unit() * (AGAIN_FORCE.length + 1)));
			againDone = 0;
			againStrength = strength;
			againIn = againLeft > 0 ? AGAIN_AFTER[0] + (int) (unit() * AGAIN_SPREAD) : 0;
			fizz = strength * POP_FIZZ;
		} else {
			take = EngineNoteSynth.nextTake(take, unit());
			blows.strike(grains.body(false, 1, 0, take), strength * SPIT_BODY);
			jetDrive = Math.max(jetDrive, strength * SPIT_JET);
			jetBright = 1;
			fizz = strength * SPIT_FIZZ;
		}
	}

	/** One charge going off in the pipe. */
	private void goOff(float strength) {
		opened = 0;
		take = EngineNoteSynth.nextTake(take, unit());
		blows.strike(grains.body(false, EngineGrains.SIZES - 1, EngineGrains.LOADS - 1, take), strength * POP_BODY);
		raps.strike(grains.crack(EngineGrains.SIZES - 1, take), strength * POP_CRACK);
		flameDrive += strength * POP_FLAME;
		thudDrive += strength * POP_THUD;
		thudHigh = 1;
		thudNote = pipe.note();
	}

	/** The next sample, -1 to 1, to be added to the note. */
	float next() {
		if (dropped) {
			dropped = false;
			rest = -1;
			sigh = 0;
		}
		if (asked.get() != null && ++waited >= MOST_WAIT)
			fired(lastNote, lastMuffle);
		if (!sounding && rest < 0)
			return 0;
		float into = blows.take();
		float past = raps.take();
		if (opened < 1) {
			opened = Math.min(1, opened + OPENS);
			float edge = opened * opened * (3 - 2 * opened);
			into *= edge;
			past *= edge;
		}
		if (rest >= 0) {
			for (int i = 0; i < ROCK_AT.length; i++)
				if (rest == ROCK_AT[i]) {
					take = EngineNoteSynth.nextTake(take, unit());
					blows.strike(grains.body(false, EngineGrains.SIZES - 1, 0, take), slam * ROCK_FORCE[i] * ROCK_BODY);
				}
			sighLow += SIGH_POLE * (white() - sighLow);
			// The sigh swells in with the first rock, so it never starts on an edge
			into += sighLow * sigh * SIGH * Math.min(1f, rest / (0.1f * RATE));
			if (++rest > ROCK_AT[ROCK_AT.length - 1]) {
				sigh *= SIGH_FALL;
				if (sigh < SILENT)
					rest = -1;
			}
		}
		if (againLeft > 0 && --againIn <= 0) {
			goOff(againStrength * AGAIN_FORCE[againDone++]);
			againIn = --againLeft > 0 ? AGAIN_AFTER[againDone] + (int) (unit() * AGAIN_SPREAD) : 0;
		}
		float mouth = 0;
		if (flameDrive > SILENT || flame > SILENT) {
			flameDrive *= FLAME_FALL;
			flame += FLAME_RISE * (flameDrive - flame);
			// Two poles, so little of it is left up where a noise turns into a hiss
			flameLow += FLAME_POLE * (white() - flameLow);
			flameSoft += FLAME_POLE * (flameLow - flameSoft);
			flameFloor += FLAME_FLOOR_POLE * (flameSoft - flameFloor);
			float gas = (flameSoft - flameFloor) * flame;
			into += gas;
			mouth = gas * POP_MOUTH;
		} else
			flameDrive = flame = 0;
		float out = pipe.next(into) * SCALE + past * SCALE + mouth;
		if (thudDrive > SILENT || thud > SILENT) {
			thudDrive *= THUD_FALL;
			thud += THUD_RISE * (thudDrive - thud);
			thudHigh = Denormals.flush(thudHigh * THUD_DROPS);
			thudPhase += 2 * Math.PI * thudNote * (THUD_LOW + THUD_DROP * thudHigh) / RATE;
			if (thudPhase > 2 * Math.PI)
				thudPhase -= 2 * Math.PI;
			out += NoteMath.sin(thudPhase) * thud;
		} else
			thudDrive = thud = 0;
		if (jetDrive > SILENT || jet > SILENT) {
			jetDrive *= JET_FALL;
			jet += JET_RISE * (jetDrive - jet);
			float cutoff = JET_LAST + (JET_FIRST - JET_LAST) * jetBright;
			jetBright = Denormals.flush(jetBright * JET_DARKENS);
			jetLow += cutoff * (white() - jetLow);
			jetSoft += cutoff * (jetLow - jetSoft);
			jetFloor += JET_FLOOR_POLE * (jetSoft - jetFloor);
			out += (jetSoft - jetFloor) * jet;
		} else
			jetDrive = jet = 0;
		if (fizz > SILENT) {
			fizzIn += FIZZ_RISE * (1 - fizzIn);
			float noise = white();
			fizzFloor += FIZZ_FLOOR_POLE * (noise - fizzFloor);
			out += (noise - fizzFloor) * fizz * fizzIn;
			fizz *= FIZZ_FALL;
		} else
			fizz = 0;
		tail = Math.max(Math.abs(out), tail * 0.999f);
		if (blows.idle() && raps.idle() && againLeft == 0 && flame == 0 && thud == 0 && jet == 0 && fizz == 0 && rest < 0
			&& pipe.isSilent() && tail < SILENT) {
			sounding = false;
			pipe.clear();
			exit.clear();
			return 0;
		}
		float heard = exit.next(out, 0, 0, false);
		if (!Float.isFinite(heard)) {
			clear();
			return 0;
		}
		return heard;
	}

	void clear() {
		asked.set(null);
		pipe.clear();
		blows.clear();
		raps.clear();
		exit.clear();
		rest = -1;
		started = null;
		againIn = againLeft = againDone = 0;
		opened = 1;
		flameDrive = flame = flameLow = flameSoft = flameFloor = thudDrive = thud = thudHigh = againStrength = 0;
		thudPhase = thudNote = 0;
		jetDrive = jet = jetBright = jetLow = jetSoft = jetFloor = fizz = fizzIn = fizzFloor = 0;
		sigh = sighLow = tail = 0;
		waited = 0;
		sounding = false;
	}

	private float unit() {
		return Math.clamp((white() + 1) * 0.5f, 0f, 1f);
	}

	/** White noise, -1 to 1, from its own sequence, so a bang leaves the note's own firings as they were. */
	private float white() {
		random ^= random << 13;
		random ^= random >>> 17;
		random ^= random << 5;
		return random / (float) Integer.MAX_VALUE;
	}
}
