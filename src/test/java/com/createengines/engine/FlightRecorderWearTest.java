package com.createengines.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.createengines.engine.FlightRecorder.Readout;
import com.createengines.engine.FlightRecorder.Sample;

/** The wear total on the Black Box's tape: recorded a sample at a time, boiled down, saved, sent, and read back from tapes that never had it. */
class FlightRecorderWearTest {

	private static Sample sample(float wear) {
		return new Sample(EngineState.RUNNING, 100, 50, 100, 0.5f, 1, 1, Diagnosis.Cause.NONE, wear);
	}

	private static FlightRecorder recorderWith(float... wears) {
		FlightRecorder recorder = new FlightRecorder();
		for (float wear : wears)
			recorder.record(sample(wear));
		return recorder;
	}

	@Test
	void aTapeWithNoWearTotalShowsNoWearLine() {
		FlightRecorder recorder = recorderWith(-1f, -1f, -1f);
		Readout readout = recorder.readout();
		assertFalse(readout.hasWear());
		assertEquals(-1, readout.wearNow());
		for (int w : readout.wear())
			assertEquals(-1, w);
	}

	@Test
	void theWearTotalIsKeptAsAWholePercentAndTheNewestIsTheOneShown() {
		FlightRecorder recorder = recorderWith(0.10f, 0.20f, 0.375f);
		Readout readout = recorder.readout();
		assertTrue(readout.hasWear());
		assertEquals(38, readout.wearNow(), "0.375 reads as 38%");
		assertEquals(10, readout.wear()[0]);
	}

	@Test
	void aColumnThatMixesSamplesWithAndWithoutATotalAveragesOnlyThoseWithOne() {
		FlightRecorder recorder = new FlightRecorder();
		for (int i = 0; i < 400; i++)
			recorder.record(sample(i % 2 == 0 ? 0.4f : -1f));
		Readout readout = recorder.readout();
		for (int w : readout.wear())
			assertEquals(40, w);
	}

	@Test
	void aWearTotalSurvivesBeingSavedAndRestored() {
		FlightRecorder recorder = recorderWith(0.1f, 0.25f, 0.5f);
		FlightRecorder restored = new FlightRecorder();
		restored.restore(recorder.save());
		assertEquals(recorder.readout().wearNow(), restored.readout().wearNow());
		assertEquals(50, restored.readout().wearNow());
		assertEquals(50, restored.momentAt(restored.total() - 1).wear());
	}

	@Test
	void aTapeSavedBeforeTheWearTotalWasKeptHasNone() {
		FlightRecorder recorder = recorderWith(0.3f, 0.3f);
		FlightRecorder.Saved saved = recorder.save();
		FlightRecorder old = new FlightRecorder();
		old.restore(new FlightRecorder.Saved(saved.rpm(), saved.load(), saved.warmth(), saved.states(), saved.changes(), saved.faults()));
		assertFalse(old.readout().hasWear());
		assertEquals(-1, old.momentAt(old.total() - 1).wear());
		FlightRecorder older = new FlightRecorder();
		older.restore(new FlightRecorder.Saved(saved.rpm(), saved.load(), saved.warmth(), saved.states(), saved.changes()));
		assertFalse(older.readout().hasWear());
	}

	@Test
	void aDamagedSavedWearReadsAsNoneOrIsHeldTo100() {
		FlightRecorder recorder = recorderWith(0.3f, 0.3f, 0.3f);
		FlightRecorder.Saved saved = recorder.save();
		FlightRecorder damaged = new FlightRecorder();
		damaged.restore(new FlightRecorder.Saved(saved.rpm(), saved.load(), saved.warmth(), saved.states(), saved.changes(),
			saved.faults(), new int[] {-50, 9999, 30}));
		Readout readout = damaged.readout();
		assertEquals(-1, readout.wear()[0]);
		assertEquals(100, readout.wear()[1]);
		assertEquals(30, readout.wear()[2]);
	}

	@Test
	void aReadoutKeepsItsWearColumnsAcrossTheWireAndOneFromBeforeHasNoneToShow() {
		Readout readout = recorderWith(0.2f, 0.4f, 0.6f).readout();
		Readout sent = Readout.of(readout.span(), readout.rpm(), readout.load(), readout.warmth(), readout.states(), readout.flatEvents(),
			readout.wear());
		assertEquals(readout.wearNow(), sent.wearNow());
		assertFalse(sent.differsFrom(readout));
		Readout old = Readout.of(readout.span(), readout.rpm(), readout.load(), readout.warmth(), readout.states(), readout.flatEvents());
		assertFalse(old.hasWear());
		assertEquals(-1, old.wearNow());
	}

	@Test
	void aChangeInTheWearTotalIsWorthSendingButNoChangeIsNot() {
		Readout a = recorderWith(0.2f, 0.4f).readout();
		Readout b = recorderWith(0.2f, 0.5f).readout();
		assertTrue(b.differsFrom(a));
		assertFalse(a.differsFrom(recorderWith(0.2f, 0.4f).readout()));
	}

	@Test
	void aMomentFromBeforeTheWearTotalHasNone() {
		FlightRecorder.Moment moment = new FlightRecorder.Moment(3, EngineState.RUNNING, 100, 50, 80, Diagnosis.Cause.NONE);
		assertEquals(-1, moment.wear());
	}

	// --- Failures as events ---

	private static Sample failing(long failures) {
		return new Sample(EngineState.RUNNING, 100, 50, 100, 0.5f, 1, 1, Diagnosis.Cause.NONE, 0.2f, failures);
	}

	private static java.util.List<FlightRecorder.Event> failureEvents(FlightRecorder recorder) {
		return recorder.readout().events().stream().filter(e -> e.from() == e.to()).toList();
	}

	@Test
	void aPartThatFailsIsAnEventNamingWhatBrokeAtTheStateTheEngineWasIn() {
		FlightRecorder recorder = new FlightRecorder();
		recorder.record(failing(0));
		recorder.record(failing(0));
		recorder.record(failing(com.createengines.engine.sim.WearFailures.pack(1, 0, 0, 0)));
		recorder.record(failing(com.createengines.engine.sim.WearFailures.pack(1, 1, 0, 0)));
		recorder.record(failing(com.createengines.engine.sim.WearFailures.pack(1, 1, 1, 1)));
		var events = failureEvents(recorder);
		assertEquals(4, events.size(), events.toString());
		assertEquals(FlightRecorder.Cause.ROD_THROWN, events.get(0).cause());
		assertEquals(FlightRecorder.Cause.GASKET_BLOWN, events.get(1).cause());
		assertEquals(FlightRecorder.Cause.PISTON_SEIZED, events.get(2).cause());
		assertEquals(FlightRecorder.Cause.APEX_FAILED, events.get(3).cause());
		for (var event : events)
			assertEquals(EngineState.RUNNING, event.from());
	}

	@Test
	void aSteadyCountIsNotAnEventAndNeitherIsAPartBeingReplaced() {
		FlightRecorder recorder = new FlightRecorder();
		long one = com.createengines.engine.sim.WearFailures.pack(1, 0, 0, 0);
		recorder.record(failing(one));
		recorder.record(failing(one));
		recorder.record(failing(one));
		recorder.record(failing(0));
		assertTrue(failureEvents(recorder).isEmpty(), "A failure that was there when the tape began, and one that was put right, are not events");
	}

	@Test
	void aSampleWithNoFailureReadingNeverMakesAnEvent() {
		FlightRecorder recorder = new FlightRecorder();
		recorder.record(sample(0.1f));
		recorder.record(failing(com.createengines.engine.sim.WearFailures.pack(3, 3, 3, 3)));
		recorder.record(sample(0.1f));
		assertTrue(failureEvents(recorder).isEmpty());
	}

	@Test
	void aFailureEventSurvivesBeingSavedRestoredAndSent() {
		FlightRecorder recorder = new FlightRecorder();
		recorder.record(failing(0));
		recorder.record(failing(com.createengines.engine.sim.WearFailures.pack(0, 1, 0, 0)));
		recorder.record(failing(com.createengines.engine.sim.WearFailures.pack(0, 1, 0, 0)));
		FlightRecorder restored = new FlightRecorder();
		restored.restore(recorder.save());
		assertEquals(1, failureEvents(restored).size());
		assertEquals(FlightRecorder.Cause.GASKET_BLOWN, failureEvents(restored).get(0).cause());
		Readout sent = recorder.readout();
		Readout received = Readout.of(sent.span(), sent.rpm(), sent.load(), sent.warmth(), sent.states(), sent.flatEvents(), sent.wear());
		assertEquals(FlightRecorder.Cause.GASKET_BLOWN, received.events().get(0).cause());
		// A restored tape has no reading to compare the next sample with, so a failure that is still there is not reported again
		restored.record(failing(com.createengines.engine.sim.WearFailures.pack(0, 1, 0, 0)));
		assertEquals(1, failureEvents(restored).size());
	}

	@Test
	void theFailureCausesAreAppendedAfterTheOnesThatAlreadyShipped() {
		var causes = FlightRecorder.Cause.values();
		assertEquals(FlightRecorder.Cause.DRY, causes[4]);
		assertEquals(FlightRecorder.Cause.ROD_THROWN, causes[5]);
		assertEquals(FlightRecorder.Cause.APEX_FAILED, causes[8]);
		assertEquals(FlightRecorder.Cause.HEAD_WARPED, causes[9]);
		assertEquals(FlightRecorder.Cause.TURBO_FAILED, causes[causes.length - 1]);
		for (var kind : com.createengines.engine.sim.WearFailures.Kind.values())
			assertEquals(kind.name(), causes[FlightRecorder.Cause.ROD_THROWN.ordinal() + kind.ordinal()].name(),
				"a recorded failure's cause is found by the kind's position, so the two lists must stay in the same order");
	}
}
