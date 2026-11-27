package com.createengines.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.createengines.engine.sim.Tuning;

/** When connected players are told the wear mode, and what they do with what they are told. */
class WearModeLinkTest {

	private final boolean before = Tuning.wearMode;

	@AfterEach
	void restore() {
		Tuning.wearMode = before;
	}

	@Test
	void thePlayersAreToldFirstAndThenOnlyOfASwitch() {
		WearModeLink link = new WearModeLink();
		assertNull(link.told());
		assertTrue(link.due(false), "nothing has been said yet");
		assertFalse(link.due(false));
		assertTrue(link.due(true), "switched on");
		assertFalse(link.due(true));
		assertEquals(Boolean.TRUE, link.told());
		assertTrue(link.due(false), "and off again");
		assertEquals(Boolean.FALSE, link.told());
	}

	@Test
	void aPlayerOnAnotherMachineTakesTheServersWord() {
		Tuning.wearMode = false;
		WearModeLink.receive(true, false);
		assertTrue(Tuning.wearMode);
		WearModeLink.receive(false, false);
		assertFalse(Tuning.wearMode);
	}

	@Test
	void theHostKeepsTheSettingItsServerAlreadyShares() {
		Tuning.wearMode = true;
		WearModeLink.receive(false, true);
		assertTrue(Tuning.wearMode, "a message still on its way cannot undo a later switch");
	}
}
