package com.createengines.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class EngineNoteGestureTest {

	private static final int RATE = EngineNoteSynth.SAMPLE_RATE;

	private static EngineNoteSynth.Params running(float rpm, float... angles) {
		return new EngineNoteSynth.Params(rpm, angles, 1, 0.5f, 1);
	}

	/** A single turning steadily, heard for a moment so its speed has caught up. */
	private static EngineNoteSynth settled(float rpm) {
		EngineNoteSynth synth = new EngineNoteSynth(7);
		synth.set(running(rpm, 10));
		for (int i = 0; i < RATE * 2; i++)
			synth.next();
		return synth;
	}

	/** Seconds into the next {@code seconds} at which the cycle passes the single's firing. */
	private static List<Double> firings(EngineNoteSynth synth, double seconds) {
		List<Double> times = new ArrayList<>();
		double before = synth.cycleDegrees();
		for (int i = 0; i < seconds * RATE; i++) {
			synth.next();
			double now = synth.cycleDegrees();
			double travelled = (now - before + 720) % 720;
			if (travelled > 0 && (10 - before + 720) % 720 < travelled)
				times.add(i / (double) RATE);
			before = now;
		}
		return times;
	}

	@Test
	void aCatchLeavesTheTimingAlone() {
		EngineNoteSynth plain = settled(240);
		EngineNoteSynth caught = settled(240);
		assertEquals(0, caught.perform(EngineNoteGesture.Kind.HARD_CATCH));
		assertEquals(firings(plain, 4), firings(caught, 4));
	}

	@Test
	void aStallStartsAtTheEnginesOwnGapAndStretchesUntilItStops() {
		EngineNoteSynth synth = settled(1200);
		assertEquals(1200, synth.perform(EngineNoteGesture.Kind.STALL));
		// The game thread says the engine has stopped, which must not cut the stall short
		synth.set(EngineNoteSynth.Params.SILENT);
		List<Double> times = firings(synth, 2);
		assertTrue(times.size() >= 3, times.toString());
		double own = 120.0 / 1200;
		double last = own * 0.9;
		for (int i = 1; i < times.size(); i++) {
			double gap = times.get(i) - times.get(i - 1);
			assertTrue(gap > last, times.toString());
			last = gap;
		}
		assertTrue(times.get(1) - times.get(0) < own * 1.35, times.toString());
		assertTrue(times.get(times.size() - 1) < EngineNoteGesture.Kind.STALL.windSeconds(), times.toString());
		assertFalse(synth.dying());
	}

	@Test
	void theNoteIsQuietAfterAStall() {
		EngineNoteSynth synth = settled(600);
		synth.perform(EngineNoteGesture.Kind.LUGGED_STALL);
		for (int i = 0; i < RATE * 3; i++)
			synth.next();
		assertTrue(synth.isSilent());
	}

	@Test
	void aStallNeedsAnEngineThatWasTurning() {
		assertEquals(-1, new EngineNoteSynth(1).perform(EngineNoteGesture.Kind.STALL));
		EngineNoteSynth once = settled(600);
		once.perform(EngineNoteGesture.Kind.STALL);
		once.endGesture();
		assertEquals(-1, once.perform(EngineNoteGesture.Kind.STALL), "one stall for each time it was turning");
	}

	@Test
	void theCrankComesToRestWhenTheNoteHasWoundDown() {
		int lag = EngineNoteGesture.restTicks(EngineNoteGesture.Kind.STALL, 2);
		assertEquals(lag + 18, EngineNoteGesture.restTicks(EngineNoteGesture.Kind.STALL, 600));
		assertEquals(lag + 34, EngineNoteGesture.restTicks(EngineNoteGesture.Kind.STARVED_STALL, 600));
	}

	@Test
	void anEngineTurnedAgainDropsTheStall() {
		EngineNoteSynth synth = settled(600);
		synth.perform(EngineNoteGesture.Kind.STALL);
		synth.endGesture();
		assertFalse(synth.dying());
		synth.set(running(300, 10));
		for (int i = 0; i < RATE * 3; i++)
			synth.next();
		assertEquals(5, firings(synth, 2).size(), 1);
	}

	@Test
	void aCatchColoursAtLeastOneFiringOfASlowEngineAndThenEnds() {
		EngineNoteGesture gesture = new EngineNoteGesture(EngineNoteGesture.Kind.CATCH, 0);
		for (int i = 0; i < RATE * 4; i++)
			gesture.advance();
		assertFalse(gesture.over(), "no firing yet");
		assertTrue(gesture.strength() > 1);
		assertTrue(gesture.over());
	}

	@Test
	void aCatchOfAFastEngineEndsWithItsPattern() {
		EngineNoteGesture gesture = new EngineNoteGesture(EngineNoteGesture.Kind.HARD_CATCH, 0);
		int fired = 0;
		int missed = 0;
		while (!gesture.over()) {
			if (gesture.strength() == EngineNoteGesture.MISS)
				missed++;
			fired++;
		}
		assertEquals(9, fired);
		assertEquals(3, missed);
	}

	@Test
	void aStallWeakensAsItSlows() {
		EngineNoteGesture gesture = new EngineNoteGesture(EngineNoteGesture.Kind.STALL, 600);
		float first = gesture.strength();
		for (int i = 0; i < RATE * 0.8; i++)
			gesture.advance();
		assertTrue(gesture.rpm() < 100);
		// The pattern comes round to the same step again, weaker
		for (int i = 0; i < 5; i++)
			gesture.strength();
		assertTrue(gesture.strength() < first * 0.7f);
	}
}
