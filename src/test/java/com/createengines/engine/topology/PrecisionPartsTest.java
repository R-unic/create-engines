package com.createengines.engine.topology;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.createengines.engine.sim.CylinderSpec;
import com.createengines.engine.sim.Tuning;

class PrecisionPartsTest {

	@AfterEach
	void resetTuning() {
		Tuning.reset();
	}

	@Test
	void thePartsAreSeparateBitsAndAllIsTheirUnion() {
		int[] parts = {PrecisionParts.BORE, PrecisionParts.HEAD, PrecisionParts.PISTON, PrecisionParts.INJECTOR};
		int union = 0;
		for (int part : parts) {
			assertEquals(1, Integer.bitCount(part));
			assertEquals(0, union & part);
			union |= part;
		}
		assertEquals(union, PrecisionParts.ALL);
	}

	@Test
	void hasChecksOnePartAndCountIgnoresBitsOutsideTheFour() {
		int fitted = PrecisionParts.BORE | PrecisionParts.PISTON;
		assertTrue(PrecisionParts.has(fitted, PrecisionParts.BORE));
		assertTrue(PrecisionParts.has(fitted, PrecisionParts.PISTON));
		assertFalse(PrecisionParts.has(fitted, PrecisionParts.HEAD));
		assertEquals(0, PrecisionParts.count(0));
		assertEquals(2, PrecisionParts.count(fitted));
		assertEquals(4, PrecisionParts.count(PrecisionParts.ALL));
		assertEquals(4, PrecisionParts.count(~0));
	}

	@Test
	void aCylinderWithNoPartsIsTheCrudeOne() {
		assertEquals(CylinderSpec.CRUDE, PrecisionParts.spec(0));
	}

	@Test
	void theSpecFollowsTheFittedParts() {
		assertEquals(CylinderSpec.build(true, false, true, false),
			PrecisionParts.spec(PrecisionParts.BORE | PrecisionParts.PISTON));
		assertEquals(CylinderSpec.build(true, true, true, true), PrecisionParts.spec(PrecisionParts.ALL));
		assertTrue(PrecisionParts.spec(PrecisionParts.ALL).compressionRatio() > CylinderSpec.CRUDE.compressionRatio());
	}

	@Test
	void onlyTheInjectorMattersToARotor() {
		assertSame(CylinderSpec.ROTARY, PrecisionParts.rotarySpec(0));
		assertSame(CylinderSpec.ROTARY, PrecisionParts.rotarySpec(PrecisionParts.BORE | PrecisionParts.HEAD | PrecisionParts.PISTON));
		assertEquals(CylinderSpec.buildRotary(true), PrecisionParts.rotarySpec(PrecisionParts.INJECTOR));
		assertEquals(CylinderSpec.buildRotary(true), PrecisionParts.rotarySpec(PrecisionParts.ALL));
	}
}
