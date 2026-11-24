package com.createengines.engine.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Exact edges of a cylinder's timing error and of a shared lobe's offset when the crank is not turning. */
class CylinderTimingEdgesTest {

	private static CylinderModel model(double tdc, double lobe) {
		return new CylinderModel(CylinderSpec.CRUDE, CamProfile.BASIC, tdc, new Camshaft(), lobe);
	}

	@Test
	void aTimingErrorOfExactlyHalfATurnIsReportedAsPlusHalfATurn() {
		assertEquals(180, model(180, 0).timingError(1), 1e-9);
	}

	@Test
	void aTimingErrorJustPastHalfATurnWrapsToTheOtherSide() {
		assertEquals(170, model(190, 0).timingError(1), 1e-9);
		assertEquals(-170, model(170, 0).timingError(1), 1e-9);
	}

	@Test
	void aStoppedCrankReadsTheReverseOffsetOfASharedLobe() {
		CylinderModel m = model(60, 0);
		m.setDirection(0);
		m.shareLobe(0);
		// forward shift 30, reverse shift 150: not turning forward, so the reverse one is used: (2 * 150 - 60) = 240, which is -120
		assertEquals(-120, m.timingError(1), 1e-9);
	}

	@Test
	void stoppingTheCrankMovesASharedLobeToTheReverseOffset() {
		CylinderModel m = model(60, 0);
		m.shareLobe(0);
		m.setDirection(0);
		assertEquals(-120, m.timingError(1), 1e-9);
	}
}
