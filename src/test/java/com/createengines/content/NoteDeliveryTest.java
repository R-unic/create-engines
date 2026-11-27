package com.createengines.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * How the note is delivered (sound plan step 1): aimed ahead by its queue, its gains glided inside the stream, its blowers
 * carried in it, handed to and from the room voice at equal power, and heard from its exhaust. Pure Java.
 */
class NoteDeliveryTest {

	private static final int RATE = EngineNoteSynth.SAMPLE_RATE;
	private static final int CHUNK = EngineNotePcm.CHUNK;
	private static final long TICK = 50_000_000L;

	private static EngineNoteSynth.Params running(float rpm) {
		return new EngineNoteSynth.Params(rpm, new float[] {0, 180, 360, 540}, 1, 0.5f, 1);
	}

	// The queue

	@Test
	void atMostAFifthOfASecondIsEverQueued() {
		double queued = EngineNotePcm.samplesBefore(EngineNoteClock.PRIMED_CHUNKS) / (double) RATE;
		assertTrue(queued <= 0.2 + 1e-9, "Queued " + queued + " s");
		assertEquals(0.15, EngineNoteClock.LEAD, 0.001);
		// A tick tops the queue up, so a chunk must last at least as long as one
		assertTrue(CHUNK / (double) RATE >= 0.0499);
	}

	@Test
	void readsAverageExactlyATwentiethOfASecondSoTheyDoNotDriftAgainstTheTicks() {
		long total = 0;
		for (int n = 0; n < 1200; n++) {
			int length = EngineNotePcm.lengthOfRead(n);
			assertTrue(length <= EngineNotePcm.CHUNK && length >= EngineNotePcm.CHUNK - 1);
			total += length;
		}
		assertEquals(60L * RATE, total);
	}

	@Test
	void aStreamAskedForASecondGetsOneChunk() {
		assertEquals(CHUNK * 2, new EngineNotePcm((out, offset, count) -> {
		}).read(RATE * 2).remaining());
		assertEquals(RATE / 5, new EngineNotePcm((out, offset, count) -> {
		}, RATE / 10).read(RATE * 2).remaining(), "A sting keeps its longer chunk");
	}

	// Looking ahead

	@Test
	void aRisingFigureIsCarriedAlongItsTrend() {
		NoteLookahead ahead = new NoteLookahead();
		float last = 0;
		for (int tick = 0; tick < 10; tick++)
			last = ahead.ahead(300 + 10 * tick, tick * TICK, 0.2, 1000);
		// 200 a second, so 40 more a fifth of a second on
		assertEquals(390 + 40, last, 1);
	}

	@Test
	void aSingleStepSaysNothingAboutWhatComesNext() {
		NoteLookahead ahead = new NoteLookahead();
		for (int tick = 0; tick < 20; tick++) {
			float value = tick < 10 ? 300 : 600;
			assertEquals(value, ahead.ahead(value, tick * TICK, 0.2, 1000), 0.001);
		}
	}

	@Test
	void aTrendIsBoundedDroppedWhenItStopsAndStartedAgainWhenItTurns() {
		NoteLookahead ahead = new NoteLookahead();
		float last = 0;
		for (int tick = 0; tick < 6; tick++)
			last = ahead.ahead(300 + 100 * tick, tick * TICK, 0.2, 45);
		assertEquals(800 + 45, last, 0.001, "No further than the bound");
		assertEquals(700, ahead.ahead(700, 6 * TICK, 0.2, 45), 0.001, "A turn back is not carried on the old way");
		for (int tick = 7; tick < 10; tick++)
			last = ahead.ahead(700 - 20 * (tick - 6), tick * TICK, 0.2, 1000);
		assertTrue(last < 640, "The new trend, downward: " + last);
		assertEquals(640, ahead.ahead(640, 9 * TICK + 300_000_000L, 0.2, 1000), 0.001, "It has stopped");
	}

	@Test
	void anUnusableFigureLeavesNoTrend() {
		NoteLookahead ahead = new NoteLookahead();
		for (int tick = 0; tick < 5; tick++)
			ahead.ahead(tick, tick * TICK, 0.2, 100);
		assertTrue(Float.isNaN(ahead.ahead(Float.NaN, 5 * TICK, 0.2, 100)));
		assertEquals(9, ahead.ahead(9, 6 * TICK, 0.2, 100), 0.001);
	}

	// Gains inside the stream

	private static short[] throughMix(float[] gainEachChunk) {
		EngineNoteSynth synth = new EngineNoteSynth(11);
		synth.set(running(1500));
		EngineNoteMix mix = new EngineNoteMix(synth);
		short[] out = new short[gainEachChunk.length * CHUNK];
		for (int chunk = 0; chunk < gainEachChunk.length; chunk++) {
			mix.setGain(gainEachChunk[chunk]);
			mix.render(out, chunk * CHUNK, CHUNK);
		}
		return out;
	}

	@Test
	void aGainSetOnceATickMovesWithoutAStair() {
		float[] full = new float[60];
		float[] fading = new float[60];
		java.util.Arrays.fill(full, 1);
		for (int i = 0; i < fading.length; i++)
			fading[i] = i < 20 ? 1 : Math.max(0.2f, 1 - (i - 20) * 0.05f);
		short[] whole = throughMix(full);
		short[] faded = throughMix(fading);
		double before = Double.NaN;
		int at = -10;
		double most = 0;
		for (int i = 20 * CHUNK; i < whole.length; i++) {
			if (Math.abs(whole[i]) < 3000)
				continue;
			double share = faded[i] / (double) whole[i];
			if (i - at == 1)
				most = Math.max(most, Math.abs(share - before));
			before = share;
			at = i;
		}
		// A source volume moving once a tick would jump by the whole of the tick's 0.05 between two samples
		assertTrue(most < 0.003, "The gain jumped by " + most + " between samples");
		assertEquals(0.2, faded[faded.length - 1] == 0 ? 0.2 : faded[faded.length - 1] / (double) whole[whole.length - 1], 0.03);
	}

	@Test
	void aNewStreamStartsFromNothing() {
		short[] out = throughMix(new float[] {0, 0});
		for (short sample : out)
			assertEquals(0, sample);
	}

	// Blowers in the stream

	private static float[] whine(BoostWhine.Kind kind, float pitch, float seconds) {
		BoostWhine voice = new BoostWhine(kind, 3);
		float[] out = new float[(int) (seconds * RATE)];
		for (int i = 0; i < out.length; i++)
			out[i] = voice.next(pitch);
		return out;
	}

	private static double rms(float[] samples) {
		double sum = 0;
		for (float sample : samples)
			sum += sample * sample;
		return Math.sqrt(sum / samples.length);
	}

	/** How strong the tone at {@code hz} is. */
	private static double at(float[] samples, double hz) {
		double real = 0;
		double imaginary = 0;
		for (int i = 0; i < samples.length; i++) {
			double turn = 2 * Math.PI * hz * i / RATE;
			real += samples[i] * Math.cos(turn);
			imaginary += samples[i] * Math.sin(turn);
		}
		return 2 * Math.hypot(real, imaginary) / samples.length;
	}

	@Test
	void eachBlowerIsAsLoudAsItsRecording() {
		// The recordings, measured from tools/boost_sounds.py: a peak of 0.8 and an RMS of 0.32
		for (BoostWhine.Kind kind : BoostWhine.Kind.values()) {
			float[] sound = whine(kind, 1, 2);
			assertEquals(0.318, rms(sound), 0.035, kind.name());
			for (float sample : sound)
				assertTrue(Math.abs(sample) <= 0.95f, kind + " reached " + sample);
		}
	}

	@Test
	void aSpoolsPitchRunsPastTheGamesTwoOctaveClamp() {
		for (float pitch : new float[] {0.3f, 0.55f, 1, 2.4f, 3}) {
			float[] sound = whine(BoostWhine.Kind.TURBO, pitch, 1);
			double there = at(sound, 2000 * pitch);
			double beside = at(sound, 2000 * pitch * 1.13);
			assertTrue(there > 0.05 && there > 5 * beside, "At pitch " + pitch + ": " + there + " against " + beside);
		}
	}

	@Test
	void aBlowerStaysFiniteAtAnyPitch() {
		for (BoostWhine.Kind kind : BoostWhine.Kind.values())
			for (float pitch : new float[] {BoostWhine.LOWEST_PITCH, BoostWhine.HIGHEST_PITCH})
				for (float sample : whine(kind, pitch, 0.5f))
					assertTrue(Float.isFinite(sample) && Math.abs(sample) <= 1);
	}

	@Test
	void aCarriedBlowerSoundsAtItsOwnLevelWhateverTheNoteDoes() {
		EngineNoteMix mix = new EngineNoteMix(new EngineNoteSynth(5));
		mix.setWhine(0, BoostWhine.Kind.TURBO, 0.1f, 1);
		short[] out = new short[RATE];
		// The engine is silent and the note's gain nothing: a coasting turbo is still heard
		mix.render(out, 0, out.length);
		double sum = 0;
		for (int i = RATE / 2; i < RATE; i++)
			sum += Math.pow(out[i] / 32767.0, 2);
		assertEquals(0.0318, Math.sqrt(sum / (RATE / 2)), 0.005);
		mix.releaseWhine(0);
		mix.render(out, 0, out.length);
		assertEquals(0, out[out.length - 1]);
	}

	@Test
	void aHostileBlowerIsHeldInRange() {
		EngineNoteMix mix = new EngineNoteMix(new EngineNoteSynth(5));
		short[] out = new short[CHUNK];
		for (float bad : new float[] {Float.NaN, Float.POSITIVE_INFINITY, -4, 900}) {
			mix.setGain(bad);
			mix.setWhine(1, BoostWhine.Kind.SUPERCHARGER, bad, bad);
			for (int i = 0; i < 40; i++)
				mix.render(out, 0, out.length);
		}
		mix.releaseWhine(1);
		for (int i = 0; i < 40; i++)
			mix.render(out, 0, out.length);
		assertEquals(0, out[out.length - 1]);
	}

	@Test
	void theCarriedRangesAreTheRecordingsOwn() throws IOException {
		String sounds = Files.readString(Path.of("src/main/resources/assets/create_engines/sounds.json"));
		for (BoostWhine.Kind kind : BoostWhine.Kind.values()) {
			String event = kind == BoostWhine.Kind.TURBO ? "turbo_whistle" : "supercharger_whine";
			Matcher found = Pattern.compile("\"" + event + "\"\\s*:\\s*\\{.*?\"attenuation_distance\"\\s*:\\s*(\\d+)",
					Pattern.DOTALL)
				.matcher(sounds);
			assertTrue(found.find(), event);
			assertEquals(Integer.parseInt(found.group(1)), kind.range, event);
		}
	}

	// Handovers

	@Test
	void aCrossFadeKeepsTheEnginesPower() {
		for (float fade = 0; fade <= 1; fade += 1f / NoteHandover.CROSSFADE_TICKS) {
			double power = Math.pow(NoteHandover.noteGain(fade), 2) + Math.pow(NoteHandover.roomGain(fade), 2);
			assertEquals(1, power, 1e-5);
		}
		assertEquals(0, NoteHandover.noteGain(0), 1e-6);
		assertEquals(0, NoteHandover.roomGain(1), 1e-6);
	}

	@Test
	void aNoteComesAndGoesOverTheSameTime() {
		float fade = 0;
		int in = 0;
		while (fade < 1) {
			fade = NoteHandover.step(fade, true);
			in++;
		}
		int out = 0;
		while (fade > 0) {
			fade = NoteHandover.step(fade, false);
			out++;
		}
		assertEquals(NoteHandover.CROSSFADE_TICKS, in);
		assertEquals(in, out);
	}

	@Test
	void aNoteIsHeldAWhileAndNeverSwappedBetweenEnginesAboutAsFar() {
		assertFalse(NoteHandover.mayYield(NoteHandover.HOLD_TICKS - 1, 30, 2), "Too new to give up");
		assertTrue(NoteHandover.mayYield(NoteHandover.HOLD_TICKS, 30, 2));
		assertFalse(NoteHandover.mayYield(10_000, 10, 8), "About as far");
	}

	// The room voice

	@Test
	void whicheverCallsFirstWorksATickOutOnce() {
		TickOnce once = new TickOnce();
		assertTrue(once.due(5));
		assertFalse(once.due(5));
		assertTrue(once.due(6));
		once.reset();
		assertTrue(once.due(6));
	}

	/**
	 * The known bug: engines that all stopped calling in at once were played on. Worked out by the room voice's own tick,
	 * with nobody waiting, every slot fades, frees and falls silent.
	 */
	@Test
	void theRoomFallsSilentOnceNothingIsWaitingForIt() {
		EngineRoomSlots<Object> slots = new EngineRoomSlots<>();
		EngineRoomMix mix = new EngineRoomMix();
		slots.choose(List.of(new Object(), new Object(), new Object()));
		short[] out = new short[CHUNK];
		for (int s = 0; s < EngineRoomMix.SLOTS; s++) {
			EngineNoteSynth synth = new EngineNoteSynth(s + 1);
			synth.set(running(600));
			mix.set(s, synth, 0.8f);
		}
		for (int i = 0; i < 20; i++)
			mix.render(out, 0, CHUNK);
		assertFalse(mix.isSilent());
		for (int tick = 0; tick < EngineRoomSlots.FADE_TICKS + 1; tick++) {
			slots.choose(new ArrayList<>());
			for (int s = 0; s < EngineRoomMix.SLOTS; s++) {
				if (slots.get(s) == null || slots.fadeTick(s))
					mix.set(s, null, 0);
				else if (slots.isFading(s))
					mix.set(s, new EngineNoteSynth(s + 1), 0);
			}
			mix.render(out, 0, CHUNK);
		}
		assertEquals(0, slots.occupied());
		assertTrue(mix.isSilent());
		mix.render(out, 0, CHUNK);
		assertEquals(0, out[CHUNK - 1]);
	}

	@Test
	void anEngineAloneInTheRoomIsNotColoured() {
		assertEquals(0.5f, EngineRoomMix.limit(0.5f), 0);
		assertEquals(-0.8f, EngineRoomMix.limit(-0.8f), 1e-6);
		assertTrue(EngineRoomMix.limit(5) <= 1 && EngineRoomMix.limit(5) > 0.99f);
		assertTrue(EngineRoomMix.limit(0.9f) < 0.9f && EngineRoomMix.limit(0.9f) > 0.8f);
	}

	/** A synthesizer put into a stream already going waits behind its queue from its very first sample. */
	@Test
	void anEngineJoiningARunningStreamIsTimedBehindItsQueue() {
		EngineNoteClock clock = new EngineNoteClock();
		clock.joinRunningStream();
		long start = 7_000_000_000L;
		for (int chunk = 0; chunk < 40; chunk++) {
			long now = start + chunk * TICK;
			long heard = clock.heardAt((long) chunk * CHUNK, now);
			long expected = start + (long) (EngineNoteClock.LEAD * 1e9) + (long) ((long) chunk * CHUNK * 1e9 / RATE);
			assertEquals(expected * 1e-9, heard * 1e-9, 0.012, "Chunk " + chunk);
		}
	}

	// Where it is heard from

	@Test
	void theNoteIsHeardFromTheMiddleOfItsOpeningsWeighedByLoudness() {
		ExhaustCentre exhaust = new ExhaustCentre();
		assertNull(exhaust.centre(0));
		exhaust.report(1, 0, 2, 4, 1, 100);
		// Reported again by a second cylinder that shares it: still one opening
		exhaust.report(1, 0, 2, 4, 1, 100);
		exhaust.report(2, 10, 2, 4, 0.25f, 100);
		double[] centre = exhaust.centre(100);
		assertNotNull(centre);
		assertEquals(2, centre[0], 1e-6);
		assertEquals(2, centre[1], 1e-6);
		assertEquals(4, centre[2], 1e-6);
		exhaust.report(2, 10, 2, 4, 0.25f, 120);
		assertEquals(10, exhaust.centre(120)[0], 1e-6, "The one that stopped reporting is forgotten");
		assertNull(exhaust.centre(200));
	}

	@Test
	void aBrokenLoudnessDoesNotBreakTheCentre() {
		ExhaustCentre exhaust = new ExhaustCentre();
		exhaust.report(1, 1, 1, 1, Float.NaN, 0);
		assertNull(exhaust.centre(0));
		exhaust.report(2, 3, 3, 3, 1, 0);
		assertEquals(3, exhaust.centre(0)[0], 1e-6);
	}
}
