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

	private record Request(Kind kind, float strength, float pitch) {
	}

	private static final int RATE = EngineNoteSynth.SAMPLE_RATE;
	/** The longest a bang waits for a firing to go out with. */
	static final int MOST_WAIT = RATE / 10;
	/** From what leaves the pipe to full scale, as the note scales its own. */
	private static final float SCALE = 0.07f;
	private static final float POP_BODY = 270f;
	private static final float POP_CRACK = 220f;
	private static final float POP_FLAME = 20f;
	private static final float SPIT_BODY = 60f;
	private static final float SPIT_JET = 0.9f;
	private static final float POP_FIZZ = 0.05f;
	private static final float SPIT_FIZZ = 0.12f;
	private static final float ROCK_BODY = 170f;
	private static final float SIGH = 3f;

	/** When the crank, stopped, rocks back off compression and settles, and how hard each time. */
	private static final int[] ROCK_AT = {0, (int) (0.15 * RATE), (int) (0.25 * RATE)};
	private static final float[] ROCK_FORCE = {0.5f, 0.26f, 0.11f};

	/** The flame rolling out of the mouth swells to its most this long after the crack. */
	private static final double FLAME_PEAK = 0.016 * RATE;
	private static final float FLAME_POLE = pole(950);
	private static final float FLAME_FLOOR_POLE = pole(110);
	private static final float JET_FLOOR_POLE = pole(500);
	/** The spit's jet starts bright and darkens. */
	private static final float JET_FIRST = pole(7500);
	private static final float JET_LAST = pole(1300);
	private static final float JET_DARKENS = fall(0.035);
	private static final float JET_FALL = fall(0.045);
	private static final float FIZZ_FLOOR_POLE = pole(3200);
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

	private int flameAge = -1;
	private float flame;
	private float flameLow;
	private float flameFloor;
	private float jet;
	private float jetBright;
	private float jetLow;
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
	/** Whether anything has been started that has not rung out. */
	private boolean sounding;

	/** Asks for a bang, from the game thread. Of two that have not yet gone off, the stronger is kept. */
	void ask(Kind kind, float strength, float pitch) {
		if (!(strength > 0) || !Float.isFinite(strength) || !Float.isFinite(pitch))
			return;
		Request wanted = new Request(kind, Math.min(strength, 2), Math.clamp(pitch, 0.5f, 2));
		asked.accumulateAndGet(wanted, (held, next) -> held != null && held.strength > next.strength ? held : next);
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
		tune(bang.pitch);
		take = EngineNoteSynth.nextTake(take, unit());
		float strength = bang.strength;
		fizzIn = 0;
		if (bang.kind == Kind.POP) {
			int top = EngineGrains.SIZES - 1;
			blows.strike(grains.body(false, top, EngineGrains.LOADS - 1, take), strength * POP_BODY);
			raps.strike(grains.crack(1, take), strength * POP_CRACK);
			flame = strength * POP_FLAME;
			flameAge = 0;
			fizz = strength * POP_FIZZ;
		} else {
			blows.strike(grains.body(false, 0, 1, take), strength * SPIT_BODY);
			jet = strength * SPIT_JET;
			jetBright = 1;
			fizz = strength * SPIT_FIZZ;
		}
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
		if (flameAge >= 0) {
			double age = flameAge++ / FLAME_PEAK;
			float swell = (float) (age * Math.exp(1 - age));
			flameLow += FLAME_POLE * (white() - flameLow);
			flameFloor += FLAME_FLOOR_POLE * (flameLow - flameFloor);
			into += (flameLow - flameFloor) * swell * flame;
			if (age > 1 && swell < 0.001f)
				flameAge = -1;
		}
		float out = pipe.next(into) * SCALE + past * SCALE;
		if (jet > SILENT) {
			float cutoff = JET_LAST + (JET_FIRST - JET_LAST) * jetBright;
			jetBright *= JET_DARKENS;
			jetLow += cutoff * (white() - jetLow);
			jetFloor += JET_FLOOR_POLE * (jetLow - jetFloor);
			// It opens over a few thousandths of a second, as the brightness is just starting to go
			out += (jetLow - jetFloor) * jet * Math.min(1f, (1 - jetBright) * 8);
			jet *= JET_FALL;
		} else
			jet = 0;
		if (fizz > SILENT) {
			fizzIn += FIZZ_RISE * (1 - fizzIn);
			float noise = white();
			fizzFloor += FIZZ_FLOOR_POLE * (noise - fizzFloor);
			out += (noise - fizzFloor) * fizz * fizzIn;
			fizz *= FIZZ_FALL;
		} else
			fizz = 0;
		tail = Math.max(Math.abs(out), tail * 0.999f);
		if (blows.idle() && raps.idle() && flameAge < 0 && jet == 0 && fizz == 0 && rest < 0 && pipe.isSilent()
			&& tail < SILENT) {
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
		flameAge = rest = -1;
		flame = flameLow = flameFloor = jet = jetBright = jetLow = jetFloor = fizz = fizzIn = fizzFloor = 0;
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
