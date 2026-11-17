package com.createengines.engine.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.createengines.engine.sim.RefiningColumn.Limit;
import com.createengines.engine.sim.RefiningColumn.Step;

/**
 * Hand-traced ticks of {@link RefiningColumn}: every limit at its exact edge, the carried rate, the product remainder and
 * the catalyst credit. A 3-wide, 4-tall column passes 0.25 * 9 * 4 = 9 mB of feed a tick at a route's full speed.
 */
class RefiningColumnExactTest {

	private static final RefiningRoute TEN_FOR_TEN = new RefiningRoute(10, 10, Heat.HEATED, 0, 1);

	private static Step tick(RefiningColumn column, RefiningRoute route, Heat heat, int feed, int room, int catalyst) {
		return column.tick(route, 3, 4, heat, feed, room, catalyst);
	}

	@Test
	void aColumnPassesAQuarterOfAMillibucketPerBlockOfItsBody() {
		assertEquals(0.25, RefiningColumn.ratePerTick(1, 1), 1e-12);
		assertEquals(3, RefiningColumn.ratePerTick(2, 3), 1e-12);
		assertEquals(2.25, RefiningColumn.ratePerTick(3, 1), 1e-12);
		assertEquals(9, RefiningColumn.ratePerTick(3, 4), 1e-12);
		assertThrows(IllegalArgumentException.class, () -> RefiningColumn.ratePerTick(0, 1));
		assertThrows(IllegalArgumentException.class, () -> RefiningColumn.ratePerTick(4, 1));
		assertThrows(IllegalArgumentException.class, () -> RefiningColumn.ratePerTick(1, 0));
	}

	@Test
	void aFreeRunningTickUsesTheWholeRate() {
		RefiningColumn column = new RefiningColumn();
		assertEquals(new Step(9, 9, 0, Limit.NONE), tick(column, TEN_FOR_TEN, Heat.HEATED, 1000, 1000, 0));
		// A route that runs at half speed passes half of that, and the rest of a millibucket waits
		RefiningColumn slow = new RefiningColumn();
		RefiningRoute half = new RefiningRoute(10, 10, Heat.HEATED, 0, 0.5);
		assertEquals(new Step(4, 4, 0, Limit.NONE), tick(slow, half, Heat.HEATED, 1000, 1000, 0));
		assertEquals(new Step(5, 5, 0, Limit.NONE), tick(slow, half, Heat.HEATED, 1000, 1000, 0));
	}

	@Test
	void aColdBaseDoesNothingAndForgetsTheCarry() {
		RefiningColumn column = new RefiningColumn();
		RefiningRoute superheated = new RefiningRoute(10, 10, Heat.SUPERHEATED, 0, 1);
		assertEquals(new Step(0, 0, 0, Limit.COLD), tick(column, superheated, Heat.HEATED, 1000, 1000, 0));
		assertEquals(new Step(0, 0, 0, Limit.COLD), tick(column, TEN_FOR_TEN, Heat.NONE, 1000, 1000, 0));
		assertEquals(new Step(9, 9, 0, Limit.NONE), tick(column, superheated, Heat.SUPERHEATED, 1000, 1000, 0));
		// Heat beyond what the route asks for is enough
		assertEquals(Limit.NONE, tick(column, TEN_FOR_TEN, Heat.SUPERHEATED, 1000, 1000, 0).limit());
	}

	@Test
	void feedAvailableIsALimitOnlyBelowTheRate() {
		RefiningColumn column = new RefiningColumn();
		assertEquals(new Step(0, 0, 0, Limit.NO_FEED), tick(column, TEN_FOR_TEN, Heat.HEATED, 0, 1000, 0));
		assertEquals(new Step(0, 0, 0, Limit.NO_FEED), tick(column, TEN_FOR_TEN, Heat.HEATED, -3, 1000, 0));
		assertEquals(new Step(1, 1, 0, Limit.NO_FEED), tick(column, TEN_FOR_TEN, Heat.HEATED, 1, 1000, 0));
		// Feed exactly equal to the rate is not a shortage
		assertEquals(new Step(9, 9, 0, Limit.NONE), tick(new RefiningColumn(), TEN_FOR_TEN, Heat.HEATED, 9, 1000, 0));
		assertEquals(new Step(8, 8, 0, Limit.NO_FEED), tick(new RefiningColumn(), TEN_FOR_TEN, Heat.HEATED, 8, 1000, 0));
	}

	@Test
	void aShortfallIsBankedForTheNextTickButOnlyUpToOneMillibucket() {
		RefiningColumn column = new RefiningColumn();
		// 5 of 9 used: 4 mB of rate went unused, but only 1 is kept
		assertEquals(new Step(5, 5, 0, Limit.NO_FEED), tick(column, TEN_FOR_TEN, Heat.HEATED, 5, 1000, 0));
		assertEquals(new Step(10, 10, 0, Limit.NONE), tick(column, TEN_FOR_TEN, Heat.HEATED, 1000, 1000, 0));
		// A fraction left over is banked as it is
		RefiningColumn fraction = new RefiningColumn();
		RefiningRoute twoThirds = new RefiningRoute(10, 10, Heat.HEATED, 0, 0.5);
		fraction.tick(twoThirds, 3, 4, Heat.HEATED, 1000, 1000, 0);
		assertEquals(5, fraction.tick(twoThirds, 3, 4, Heat.HEATED, 1000, 1000, 0).feedUsed());
	}

	@Test
	void theProductTankRoomIsALimitOnlyBelowTheRate() {
		assertEquals(new Step(0, 0, 0, Limit.PRODUCT_FULL), tick(new RefiningColumn(), TEN_FOR_TEN, Heat.HEATED, 1000, 0, 0));
		assertEquals(new Step(0, 0, 0, Limit.PRODUCT_FULL), tick(new RefiningColumn(), TEN_FOR_TEN, Heat.HEATED, 1000, -2, 0));
		assertEquals(new Step(3, 3, 0, Limit.PRODUCT_FULL), tick(new RefiningColumn(), TEN_FOR_TEN, Heat.HEATED, 1000, 3, 0));
		assertEquals(new Step(9, 9, 0, Limit.NONE), tick(new RefiningColumn(), TEN_FOR_TEN, Heat.HEATED, 1000, 9, 0));
		assertEquals(new Step(8, 8, 0, Limit.PRODUCT_FULL), tick(new RefiningColumn(), TEN_FOR_TEN, Heat.HEATED, 1000, 8, 0));
	}

	@Test
	void aRatioThatDoesNotDivideKeepsItsRemainderAndRespectsTheRoomWithIt() {
		RefiningColumn column = new RefiningColumn();
		RefiningRoute twoForThree = new RefiningRoute(3, 2, Heat.HEATED, 0, 1);
		assertEquals(new Step(2, 1, 0, Limit.NO_FEED), tick(column, twoForThree, Heat.HEATED, 2, 1000, 0));
		assertEquals(new Step(2, 1, 0, Limit.NO_FEED), tick(column, twoForThree, Heat.HEATED, 2, 1000, 0));
		// The remainder is now 2: one more mB of feed makes (1 * 2 + 2) / 3 = 1 mB, and two would make 2, over the room of 1
		assertEquals(new Step(1, 1, 0, Limit.PRODUCT_FULL), tick(column, twoForThree, Heat.HEATED, 1000, 1, 0));
		// A whole batch of 3 makes 2 exactly, with nothing left over
		RefiningColumn whole = new RefiningColumn();
		assertEquals(new Step(3, 2, 0, Limit.NO_FEED), tick(whole, twoForThree, Heat.HEATED, 3, 1000, 0));
		assertEquals(new Step(6, 4, 0, Limit.NO_FEED), tick(whole, twoForThree, Heat.HEATED, 6, 1000, 0));
	}

	@Test
	void aCatalystIsSpentByTheItemAndCreditCarriesOver() {
		RefiningRoute catalysed = new RefiningRoute(10, 10, Heat.HEATED, 4, 1);
		RefiningColumn column = new RefiningColumn();
		assertEquals(new Step(0, 0, 0, Limit.NO_CATALYST), tick(column, catalysed, Heat.HEATED, 1000, 1000, 0));
		assertEquals(0, column.catalystCredit());
		// Rate 9 needs 3 items (12 mB of service), leaving 3 mB of credit
		assertEquals(new Step(9, 9, 3, Limit.NONE), tick(column, catalysed, Heat.HEATED, 1000, 1000, 5));
		assertEquals(3, column.catalystCredit());
		// The next 9 needs 6 more mB: 2 items, leaving 3 + 8 - 9 = 2
		assertEquals(new Step(9, 9, 2, Limit.NONE), tick(column, catalysed, Heat.HEATED, 1000, 1000, 5));
		assertEquals(2, column.catalystCredit());
		// With no items left, only the credit runs
		assertEquals(new Step(2, 2, 0, Limit.NO_CATALYST), tick(column, catalysed, Heat.HEATED, 1000, 1000, 0));
		assertEquals(0, column.catalystCredit());
		assertEquals(new Step(0, 0, 0, Limit.NO_CATALYST), tick(column, catalysed, Heat.HEATED, 1000, 1000, 0));
	}

	@Test
	void resettingForgetsTheCarryTheRemainderAndTheCredit() {
		RefiningColumn column = new RefiningColumn();
		RefiningRoute catalysed = new RefiningRoute(3, 2, Heat.HEATED, 4, 1);
		tick(column, catalysed, Heat.HEATED, 2, 1000, 5);
		assertEquals(true, column.catalystCredit() > 0);
		column.reset();
		assertEquals(0, column.catalystCredit());
		// Fresh: with 2 mB of feed, no remainder and no banked rate
		assertEquals(new Step(2, 1, 1, Limit.NO_FEED), tick(column, catalysed, Heat.HEATED, 2, 1000, 5));
	}
}
