package com.createengines.engine.sim;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.createengines.engine.sim.ChargePriority.Use;

/** Charge priority ({@link ChargePriority}): a reserve is kept for the starter and the plugs, and with none set a bank is shared as it always was. */
class ChargePriorityTest {

	private static final float EPS = 1e-4f;
	private static final boolean[] NONE = {false, false, false};

	private static float sum(float[] values) {
		float total = 0;
		for (float value : values)
			total += value;
		return total;
	}

	@Test
	void withNoReserveEveryUseGivesTheEvenShareABankAlwaysGave() {
		float[] deliverable = {100, 50, 50};
		for (Use use : Use.values()) {
			float[] amounts = ChargePriority.split(deliverable, NONE, 100, use);
			// Each gives in proportion to what it can deliver: half of 100 sparks from a bank of 200 is half of each
			assertArrayEquals(new float[] {50, 25, 25}, amounts, EPS, use.name());
			assertEquals(200, ChargePriority.available(deliverable, NONE, use), EPS);
		}
	}

	@Test
	void anOptionalDrawNeverReachesAReserve() {
		float[] deliverable = {100, 100, 100};
		boolean[] reserved = {false, true, false};
		assertEquals(200, ChargePriority.available(deliverable, reserved, Use.OPTIONAL), EPS);
		float[] amounts = ChargePriority.split(deliverable, reserved, 500, Use.OPTIONAL);
		assertArrayEquals(new float[] {100, 0, 100}, amounts, EPS, "asking for more than there is takes all of the open batteries and none of the reserve");
	}

	@Test
	void anEssentialDrawTakesTheOpenBatteriesFirstAndTheReserveForWhatIsLeft() {
		float[] deliverable = {100, 100, 100};
		boolean[] reserved = {false, true, false};
		assertEquals(300, ChargePriority.available(deliverable, reserved, Use.ESSENTIAL), EPS);
		assertArrayEquals(new float[] {50, 0, 50}, ChargePriority.split(deliverable, reserved, 100, Use.ESSENTIAL), EPS,
			"a small draw leaves the reserve alone");
		assertArrayEquals(new float[] {100, 50, 100}, ChargePriority.split(deliverable, reserved, 250, Use.ESSENTIAL), EPS,
			"a larger one empties the open batteries, then takes the rest from the reserve");
		assertArrayEquals(new float[] {100, 100, 100}, ChargePriority.split(deliverable, reserved, 9999, Use.ESSENTIAL), EPS);
	}

	@Test
	void aBankThatIsAllReserveGivesToEverythingItIsAskedForAsItAlwaysDid() {
		// A whole bank set to reserve has no open battery to keep it from, so an essential draw is the even share
		float[] deliverable = {100, 50};
		boolean[] reserved = {true, true};
		assertArrayEquals(new float[] {40, 20}, ChargePriority.split(deliverable, reserved, 60, Use.ESSENTIAL), EPS);
		assertEquals(0, ChargePriority.available(deliverable, reserved, Use.OPTIONAL), EPS, "but an optional draw gets nothing from it");
		assertEquals(0, sum(ChargePriority.split(deliverable, reserved, 60, Use.OPTIONAL)), EPS);
	}

	@Test
	void amountsAddUpToTheSmallerOfWhatIsWantedAndWhatThereIs() {
		float[] deliverable = {30, 70, 10, 90};
		boolean[] reserved = {false, true, true, false};
		for (Use use : Use.values())
			for (float wanted : new float[] {0.5f, 40, 120, 199, 200, 5000}) {
				float expected = Math.min(wanted, ChargePriority.available(deliverable, reserved, use));
				assertEquals(expected, sum(ChargePriority.split(deliverable, reserved, wanted, use)), 1e-3f, use + " " + wanted);
			}
	}

	@Test
	void noMemberIsAskedForMoreThanItCanGive() {
		float[] deliverable = {5, 0, 80, 12};
		boolean[] reserved = {true, false, false, true};
		for (Use use : Use.values())
			for (float wanted : new float[] {1, 50, 97, 98, 1000}) {
				float[] amounts = ChargePriority.split(deliverable, reserved, wanted, use);
				for (int i = 0; i < amounts.length; i++)
					assertEquals(true, amounts[i] <= deliverable[i] + EPS && amounts[i] >= 0, use + " " + wanted + " member " + i);
			}
	}

	@Test
	void nothingWantedOrNothingThereGivesNothing() {
		assertEquals(0, sum(ChargePriority.split(new float[] {10, 10}, new boolean[] {false, false}, 0, Use.ESSENTIAL)), 0);
		assertEquals(0, sum(ChargePriority.split(new float[] {10, 10}, new boolean[] {false, false}, -5, Use.ESSENTIAL)), 0);
		assertEquals(0, sum(ChargePriority.split(new float[] {10, 10}, new boolean[] {false, false}, Float.NaN, Use.ESSENTIAL)), 0);
		assertEquals(0, sum(ChargePriority.split(new float[] {0, 0}, new boolean[] {false, true}, 5, Use.ESSENTIAL)), 0);
		assertEquals(0, ChargePriority.split(new float[0], new boolean[0], 5, Use.OPTIONAL).length);
	}

	@Test
	void fillingWithNoReserveSharesByRoomAsItAlwaysDid() {
		float[] amounts = ChargePriority.fillSplit(new float[] {100, 50, 50}, NONE, 100);
		assertArrayEquals(new float[] {50, 25, 25}, amounts, EPS);
	}

	@Test
	void aReserveFillsFirst() {
		float[] room = {100, 60, 100};
		boolean[] reserved = {false, true, false};
		assertArrayEquals(new float[] {0, 40, 0}, ChargePriority.fillSplit(room, reserved, 40), EPS, "charge goes into the reserve until it is full");
		assertArrayEquals(new float[] {50, 60, 50}, ChargePriority.fillSplit(room, reserved, 160), EPS, "and the rest is shared by room among the others");
		assertEquals(260, sum(ChargePriority.fillSplit(room, reserved, 9999)), EPS, "more than the room in all fills everything");
	}

	@Test
	void fillingWithNoRoomOrNoChargeAddsNothing() {
		assertEquals(0, sum(ChargePriority.fillSplit(new float[] {0, 0}, new boolean[] {true, false}, 50)), 0);
		assertEquals(0, sum(ChargePriority.fillSplit(new float[] {10, 10}, new boolean[] {true, false}, 0)), 0);
		assertEquals(0, sum(ChargePriority.fillSplit(new float[] {10, 10}, new boolean[] {true, false}, Float.NaN)), 0);
	}
}
