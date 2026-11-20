package com.createengines.content.effects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The blow-off jet and the wastegate chuff: bigger for more boost, never unbounded, and over in a bounded time. */
class VentJetTest {

	@Test
	void theBurstAndTheJetGrowWithBoostAndStopGrowing() {
		assertEquals(8, VentJet.burst(0f));
		assertEquals(17, VentJet.burst(VentJet.MAX_BOOST));
		assertEquals(VentJet.burst(VentJet.MAX_BOOST), VentJet.burst(9f), "A huge boost looks like the biggest one");
		assertEquals(VentJet.MIN_TICKS, VentJet.duration(0f));
		assertEquals(VentJet.MAX_TICKS, VentJet.duration(VentJet.MAX_BOOST));
		assertEquals(VentJet.MAX_TICKS, VentJet.duration(50f));
		for (float boost = 0; boost < 1.5f; boost += 0.05f) {
			assertTrue(VentJet.burst(boost + 0.05f) >= VentJet.burst(boost), "A burst never shrinks as boost rises: " + boost);
			assertTrue(VentJet.duration(boost + 0.05f) >= VentJet.duration(boost), "Nor does the jet: " + boost);
		}
	}

	@Test
	void anOddBoostIsKeptToSomethingDrawable() {
		assertEquals(VentJet.burst(0f), VentJet.burst(-3f));
		assertEquals(VentJet.burst(0f), VentJet.burst(Float.NaN));
		assertEquals(VentJet.MIN_TICKS, VentJet.duration(Float.NaN));
		assertEquals(VentJet.chuff(0f), VentJet.chuff(Float.NaN));
	}

	@Test
	void theJetThinsOutAndEnds() {
		int total = VentJet.duration(1f);
		assertEquals(1f, VentJet.strength(total, total), 1e-6);
		assertEquals(0f, VentJet.strength(0, total), 1e-6);
		assertEquals(0f, VentJet.strength(5, 0), "No jet has no strength");
		assertEquals(1f, VentJet.strength(total * 3, total), "Never more than the start");
		assertEquals(2, VentJet.perTick(total, total), "Strong at first");
		assertEquals(1, VentJet.perTick(1, total), "Thin at the end");
		assertEquals(0, VentJet.perTick(0, total), "Nothing once it is over");
		assertEquals(0, VentJet.perTick(-4, total));
		assertEquals(0, VentJet.perTick(3, 0));
		int thrown = 0;
		for (int left = total; left > 0; left--)
			thrown += VentJet.perTick(left, total);
		assertTrue(thrown > total && thrown <= 2 * total, "A jet throws between one and two puffs a tick: " + thrown);
	}

	@Test
	void theWastegateChuffIsSmallerThanTheBlowOff() {
		assertEquals(2, VentJet.chuff(0f));
		assertEquals(4, VentJet.chuff(1f));
		assertEquals(4, VentJet.chuff(VentJet.MAX_BOOST), "Capped at four");
		assertTrue(VentJet.chuff(VentJet.MAX_BOOST) < VentJet.burst(0f), "Even the biggest chuff is under the smallest blow-off burst");
	}
}
