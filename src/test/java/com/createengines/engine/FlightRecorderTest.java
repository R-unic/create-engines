package com.createengines.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.createengines.engine.FlightRecorder.Cause;
import com.createengines.engine.FlightRecorder.Readout;
import com.createengines.engine.FlightRecorder.Sample;

/** The recorder keeps ten minutes, boils them down for a screen, and says why an engine changed state. */
class FlightRecorderTest {

	private static Sample running(float rpm) {
		return new Sample(EngineState.RUNNING, rpm, 2, 4, 0.7f, 30, 1);
	}

	private static Sample stalled(float fuel) {
		return new Sample(EngineState.STALLED, 0, 0, 4, 0.6f, fuel, 1);
	}

	@Test
	void anEmptyRecorderReadsEmpty() {
		FlightRecorder recorder = new FlightRecorder();
		assertTrue(recorder.readout()
			.isEmpty());
		assertFalse(recorder.caughtAStall());
	}

	@Test
	void aShortRecordingHasAColumnForEachSample() {
		FlightRecorder recorder = new FlightRecorder();
		for (int i = 0; i < 10; i++)
			recorder.record(running(100 + i));
		Readout readout = recorder.readout();
		assertEquals(10, readout.rpm().length);
		assertEquals(100, readout.rpm()[0]);
		assertEquals(109, readout.rpm()[9]);
		assertEquals(10, readout.span());
	}

	@Test
	void aLongRecordingIsBoiledDownAndKeepsOnlyTenMinutes() {
		FlightRecorder recorder = new FlightRecorder();
		for (int i = 0; i < FlightRecorder.CAPACITY * 2; i++)
			recorder.record(running(i < FlightRecorder.CAPACITY ? 50 : 200));
		Readout readout = recorder.readout();
		assertEquals(FlightRecorder.COLUMNS, readout.rpm().length);
		assertEquals(FlightRecorder.CAPACITY, readout.span());
		for (int rpm : readout.rpm())
			assertEquals(200, rpm, "Only the last ten minutes are held, and all of them were at 200");
	}

	@Test
	void loadIsAShareOfWhatTheEngineCouldGive() {
		FlightRecorder recorder = new FlightRecorder();
		recorder.record(new Sample(EngineState.RUNNING, 100, 1, 4, 0.7f, 30, 1));
		assertEquals(25, recorder.readout()
			.load()[0]);
	}

	@Test
	void warmthIsAShareOfAWarmEngine() {
		FlightRecorder recorder = new FlightRecorder();
		recorder.record(new Sample(EngineState.RUNNING, 100, 1, 4, 0.35f, 30, 1));
		assertEquals(50, recorder.readout()
			.warmth()[0]);
	}

	@Test
	void anEngineThatRunsOutOfFuelStallsForWantOfIt() {
		FlightRecorder recorder = new FlightRecorder();
		recorder.record(new Sample(EngineState.RUNNING, 100, 2, 4, 0.7f, 0, 1));
		recorder.record(stalled(0));
		var events = recorder.readout()
			.events();
		assertEquals(1, events.size());
		assertEquals(EngineState.RUNNING, events.get(0)
			.from());
		assertEquals(EngineState.STALLED, events.get(0)
			.to());
		assertEquals(Cause.NO_FUEL, events.get(0)
			.cause());
		assertTrue(recorder.caughtAStall());
	}

	@Test
	void aHeavyLoadStallsAnEngineThatWasFed() {
		FlightRecorder recorder = new FlightRecorder();
		recorder.record(new Sample(EngineState.RUNNING, 100, 9, 4, 0.7f, 30, 1));
		recorder.record(stalled(30));
		assertEquals(Cause.OVERLOAD, recorder.readout()
			.events()
			.get(0)
			.cause());
	}

	@Test
	void anUnexplainedStallSaysSo() {
		FlightRecorder recorder = new FlightRecorder();
		recorder.record(running(100));
		recorder.record(stalled(30));
		assertEquals(Cause.UNKNOWN, recorder.readout()
			.events()
			.get(0)
			.cause());
	}

	@Test
	void aSteadyStateIsNotAnEvent() {
		FlightRecorder recorder = new FlightRecorder();
		for (int i = 0; i < 50; i++)
			recorder.record(running(100));
		assertTrue(recorder.readout()
			.events()
			.isEmpty());
	}

	@Test
	void eventsSayHowLongAgoTheyHappened() {
		FlightRecorder recorder = new FlightRecorder();
		recorder.record(running(100));
		recorder.record(stalled(0));
		for (int i = 0; i < 9; i++)
			recorder.record(stalled(0));
		assertEquals(10, recorder.readout()
			.events()
			.get(0)
			.secondsAgo());
	}

	@Test
	void anEventThatScrolledOffTheTapeIsForgotten() {
		FlightRecorder recorder = new FlightRecorder();
		recorder.record(running(100));
		recorder.record(stalled(0));
		for (int i = 0; i < FlightRecorder.CAPACITY + 5; i++)
			recorder.record(stalled(0));
		assertTrue(recorder.readout()
			.events()
			.isEmpty());
	}

	@Test
	void aRecordingSurvivesBeingSaved() {
		FlightRecorder recorder = new FlightRecorder();
		for (int i = 0; i < 100; i++)
			recorder.record(running(100 + i));
		recorder.record(stalled(0));
		FlightRecorder loaded = new FlightRecorder();
		loaded.restore(recorder.save());
		Readout before = recorder.readout();
		Readout after = loaded.readout();
		assertEquals(before.span(), after.span());
		assertFalse(before.differsFrom(after));
		assertEquals(before.events()
			.size(), after.events()
			.size());
		assertTrue(loaded.caughtAStall());

		loaded.record(stalled(0));
		assertEquals(before.span() + 1, loaded.readout()
			.span());
	}

	@Test
	void aReadoutSurvivesTheWire() {
		FlightRecorder recorder = new FlightRecorder();
		for (int i = 0; i < 30; i++)
			recorder.record(running(100 + i));
		recorder.record(stalled(0));
		Readout sent = recorder.readout();
		Readout received = Readout.of(sent.span(), sent.rpm(), sent.load(), sent.warmth(), sent.states(),
			sent.flatEvents());
		assertFalse(sent.differsFrom(received));
		assertEquals(sent.events()
			.get(0)
			.cause(),
			received.events()
				.get(0)
				.cause());
	}

	private static Sample faulty(float rpm, Diagnosis.Cause fault) {
		return new Sample(EngineState.RUNNING, rpm, 2, 4, 0.35f, 30, 1, fault);
	}

	@Test
	void aMomentReadsWhatTheEngineWasDoingAtThatSecond() {
		FlightRecorder recorder = new FlightRecorder();
		recorder.record(running(100));
		recorder.record(faulty(200, Diagnosis.Cause.NO_SPARK));
		recorder.record(running(300));
		FlightRecorder.Moment then = recorder.momentAt(recorder.sampleBack(1));
		assertEquals(1, then.secondsAgo());
		assertEquals(200, then.rpm());
		assertEquals(50, then.load(), "Two asked of four available is 50 percent");
		assertEquals(50, then.warmth(), "0.35 against the 0.7 of a warm engine is half");
		assertEquals(EngineState.RUNNING, then.state());
		assertEquals(Diagnosis.Cause.NO_SPARK, then.fault());
		assertEquals(Diagnosis.Cause.NONE, recorder.momentAt(recorder.sampleBack(0)).fault());
	}

	@Test
	void anEmptyTapeHasNoMomentAndAPastTheEndOneHoldsAtTheEnds() {
		FlightRecorder recorder = new FlightRecorder();
		assertEquals(null, recorder.momentAt(recorder.sampleBack(10)));
		for (int i = 0; i < 5; i++)
			recorder.record(running(100 + i));
		assertEquals(100, recorder.momentAt(recorder.sampleBack(500)).rpm(), "Further back than the tape holds is the oldest");
		assertEquals(104, recorder.momentAt(recorder.sampleBack(-5)).rpm(), "A negative distance is the newest");
		assertEquals(104, recorder.momentAt(recorder.total() + 50).rpm());
	}

	@Test
	void aMomentPinnedByItsNumberStaysPutWhileTheTapeRunsOn() {
		FlightRecorder recorder = new FlightRecorder();
		for (int i = 0; i < 20; i++)
			recorder.record(running(100 + i));
		long pin = recorder.sampleBack(10);
		int rpm = recorder.momentAt(pin).rpm();
		for (int i = 0; i < 7; i++)
			recorder.record(running(500));
		assertEquals(rpm, recorder.momentAt(pin).rpm(), "The pinned moment is the same one");
		assertEquals(17, recorder.momentAt(pin).secondsAgo(), "and it is further back now");
	}

	@Test
	void aMomentThatFellOffTheTapeHoldsAtItsStart() {
		FlightRecorder recorder = new FlightRecorder();
		recorder.record(running(1));
		long pin = recorder.sampleBack(0);
		for (int i = 0; i < FlightRecorder.CAPACITY + 10; i++)
			recorder.record(running(9));
		assertEquals(9, recorder.momentAt(pin).rpm());
		assertEquals(FlightRecorder.CAPACITY - 1, recorder.momentAt(pin).secondsAgo());
	}

	@Test
	void theFaultsSurviveBeingSavedAndATapeWithoutThemBlamesNothing() {
		FlightRecorder recorder = new FlightRecorder();
		recorder.record(faulty(100, Diagnosis.Cause.DRY_OIL));
		recorder.record(running(110));
		FlightRecorder loaded = new FlightRecorder();
		loaded.restore(recorder.save());
		assertEquals(Diagnosis.Cause.DRY_OIL, loaded.momentAt(loaded.sampleBack(1)).fault());
		FlightRecorder.Saved saved = recorder.save();
		FlightRecorder old = new FlightRecorder();
		old.restore(new FlightRecorder.Saved(saved.rpm(), saved.load(), saved.warmth(), saved.states(), saved.changes()));
		assertEquals(Diagnosis.Cause.NONE, old.momentAt(old.sampleBack(1)).fault(), "a tape saved before faults were kept");
		assertEquals(100, old.momentAt(old.sampleBack(1)).rpm());
		// A fault number from a newer or damaged save is kept in range
		FlightRecorder damaged = new FlightRecorder();
		damaged.restore(new FlightRecorder.Saved(saved.rpm(), saved.load(), saved.warmth(), saved.states(), saved.changes(),
			new int[] {999, -4}));
		assertEquals(Diagnosis.Cause.values()[Diagnosis.Cause.values().length - 1], damaged.momentAt(0).fault());
		assertEquals(Diagnosis.Cause.NONE, damaged.momentAt(1).fault());
	}

	@Test
	void theReplayDialCoversTheWholeTapeInWholeSteps() {
		assertEquals(0, FlightRecorder.CAPACITY % FlightRecorder.REPLAY_STEP);
		assertEquals(FlightRecorder.CAPACITY, FlightRecorder.REPLAY_STEPS * FlightRecorder.REPLAY_STEP);
	}
}
