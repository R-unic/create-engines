package com.createengines.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.createengines.engine.FlightRecorder.Cause;
import com.createengines.engine.FlightRecorder.Sample;

/** What the recorder says about a change of state, for each state the engine can change to, and when it caught a stall. */
class FlightRecorderCauseTest {

	private static Sample sample(EngineState state, float load, float capacity, float fuel, float lubrication) {
		return new Sample(state, 100, load, capacity, 0.6f, fuel, lubrication);
	}

	@Test
	void anEngineWithNoOilThatStallsIsPutDownToItBeforeLoad() {
		Sample before = sample(EngineState.RUNNING, 8, 4, 30, 0.01f);
		assertEquals(Cause.DRY, FlightRecorder.cause(before, sample(EngineState.STALLED, 0, 4, 30, 0.01f)));
	}

	@Test
	void noFuelIsTheFirstThingChecked() {
		Sample dryAndLoaded = sample(EngineState.RUNNING, 8, 4, 0, 0.01f);
		assertEquals(Cause.NO_FUEL, FlightRecorder.cause(dryAndLoaded, sample(EngineState.STALLED, 0, 4, 0, 0.01f)));
	}

	@Test
	void coastingBlamesTheFuelLineOrNothingInParticular() {
		assertEquals(Cause.NO_FUEL, FlightRecorder.cause(sample(EngineState.RUNNING, 1, 4, 0, 1),
			sample(EngineState.COASTING, 0, 4, 0, 1)));
		assertEquals(Cause.UNKNOWN, FlightRecorder.cause(sample(EngineState.RUNNING, 1, 4, 30, 1),
			sample(EngineState.COASTING, 0, 4, 30, 1)));
	}

	@Test
	void overloadingIsAlwaysAnOverload() {
		assertEquals(Cause.OVERLOAD, FlightRecorder.cause(sample(EngineState.RUNNING, 1, 4, 30, 1),
			sample(EngineState.OVERLOADED, 9, 4, 30, 1)));
	}

	@Test
	void startingAndRunningAreNotFaults() {
		assertEquals(Cause.NONE, FlightRecorder.cause(sample(EngineState.STALLED, 0, 4, 30, 1),
			sample(EngineState.CRANKING, 0, 4, 30, 1)));
		assertEquals(Cause.NONE, FlightRecorder.cause(sample(EngineState.CRANKING, 0, 4, 30, 1),
			sample(EngineState.RUNNING, 1, 4, 30, 1)));
	}

	@Test
	void everyCauseHasALowerCaseLangKey() {
		for (Cause cause : Cause.values())
			assertEquals("goggles.recorder.cause." + cause.name().toLowerCase(java.util.Locale.ROOT), cause.langKey());
	}

	private static boolean caught(EngineState from, EngineState to) {
		FlightRecorder recorder = new FlightRecorder();
		recorder.record(sample(from, 1, 4, 30, 1));
		recorder.record(sample(to, 0, 4, 30, 1));
		return recorder.caughtAStall();
	}

	@Test
	void aStallIsCaughtOnlyFromAnEngineThatWasTurning() {
		assertTrue(caught(EngineState.RUNNING, EngineState.STALLED));
		assertTrue(caught(EngineState.OVERLOADED, EngineState.STALLED));
		assertTrue(caught(EngineState.COASTING, EngineState.STALLED));
		assertFalse(caught(EngineState.CRANKING, EngineState.STALLED), "an engine that never caught did not stall");
		assertFalse(caught(EngineState.RUNNING, EngineState.COASTING));
	}
}
