package com.createengines.content.effects;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.createengines.engine.EngineState;

class EngineCuesTest {

	@Test
	void anEngineStarvedOfAirStallsAfterCoasting() {
		assertEquals(EngineCues.STALL, EngineCues.forChange(EngineState.COASTING, EngineState.STALLED));
	}

	@Test
	void aRunningOrOverloadedEngineThatDiesStalls() {
		assertEquals(EngineCues.STALL, EngineCues.forChange(EngineState.RUNNING, EngineState.STALLED));
		assertEquals(EngineCues.STALL, EngineCues.forChange(EngineState.OVERLOADED, EngineState.STALLED));
	}

	@Test
	void anEngineThatNeverRanDoesNotStallLoudly() {
		assertEquals(EngineCues.NONE, EngineCues.forChange(EngineState.CRANKING, EngineState.STALLED));
		assertEquals(EngineCues.NONE, EngineCues.forChange(EngineState.INCOMPLETE, EngineState.STALLED));
	}

	@Test
	void catchingFromCrankingOrAStall() {
		assertEquals(EngineCues.CATCH, EngineCues.forChange(EngineState.CRANKING, EngineState.RUNNING));
		assertEquals(EngineCues.CATCH, EngineCues.forChange(EngineState.STALLED, EngineState.OVERLOADED));
	}

	@Test
	void noChangeAndFirstSightMakeNoSound() {
		assertEquals(EngineCues.NONE, EngineCues.forChange(null, EngineState.STALLED));
		assertEquals(EngineCues.NONE, EngineCues.forChange(EngineState.RUNNING, EngineState.RUNNING));
	}
}
