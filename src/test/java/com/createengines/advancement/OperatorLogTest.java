package com.createengines.advancement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class OperatorLogTest {

	private static final UUID ALEX = UUID.randomUUID();
	private static final UUID BLAKE = UUID.randomUUID();

	@Test
	void nobodyGetsCreditForAMachineNobodyTouched() {
		assertNull(new OperatorLog().latest(List.of(1L, 2L)));
	}

	@Test
	void theLastPlayerToTouchAnyPartGetsTheCredit() {
		OperatorLog log = new OperatorLog();
		log.record(1, ALEX, 10);
		log.record(2, BLAKE, 20);
		assertEquals(BLAKE, log.latest(List.of(1L, 2L)));
		log.record(1, ALEX, 30);
		assertEquals(ALEX, log.latest(List.of(1L, 2L)));
	}

	@Test
	void onlyBlocksOfTheMachineCount() {
		OperatorLog log = new OperatorLog();
		log.record(1, ALEX, 10);
		log.record(99, BLAKE, 20);
		assertEquals(ALEX, log.latest(List.of(1L, 2L)));
	}

	@Test
	void aBrokenBlockStopsCounting() {
		OperatorLog log = new OperatorLog();
		log.record(1, ALEX, 10);
		log.record(2, BLAKE, 20);
		log.forget(2);
		assertEquals(ALEX, log.latest(List.of(1L, 2L)));
	}
}
