package com.createengines;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.createengines.api.EngineStatus;
import com.createengines.engine.EngineState;

/** The public status names are the engine's own state names, one for one, so a state can never go unnamed in the API. */
class EngineStatusTest {

	@Test
	void everyEngineStateHasTheSameNamedPublicStatus() {
		List<String> states = Arrays.stream(EngineState.values())
			.map(Enum::name)
			.toList();
		List<String> statuses = Arrays.stream(EngineStatus.values())
			.map(Enum::name)
			.toList();
		assertEquals(states, statuses);
	}
}
