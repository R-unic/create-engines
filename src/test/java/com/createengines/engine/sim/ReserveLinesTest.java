package com.createengines.engine.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

/** The reserve lines a battery's goggles show: which, and in what order. */
class ReserveLinesTest {

	@Test
	void aPlainBatteryInABankWithNoReserveSaysOnlyThatItIsOff() {
		assertEquals(List.of("goggles.battery.reserve_off"), ReserveLines.keys(false, 0, false));
	}

	@Test
	void aReserveSaysSoAndWhatTheBankHoldsBack() {
		assertEquals(List.of("goggles.battery.reserve", "goggles.battery.reserve_split"), ReserveLines.keys(true, 1, false));
	}

	@Test
	void aPlainBatteryBesideAReserveStillSaysItIsOffAndShowsTheSplit() {
		assertEquals(List.of("goggles.battery.reserve_off", "goggles.battery.reserve_split"), ReserveLines.keys(false, 1, false));
	}

	@Test
	void sneakingAddsHowToChangeIt() {
		assertEquals(List.of("goggles.battery.reserve", "goggles.battery.reserve_split", "goggles.battery.reserve_hint"),
			ReserveLines.keys(true, 2, true));
		assertEquals(List.of("goggles.battery.reserve_off", "goggles.battery.reserve_hint"), ReserveLines.keys(false, 0, true));
	}
}
