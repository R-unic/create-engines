package com.createengines.engine.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Exact figures for the parts of {@link CylinderModel} that its running tests only ever see in aggregate. */
class CylinderModelExactTest {

	@AfterEach
	void resetTuning() {
		Tuning.reset();
	}

	private static CylinderModel cylinder(double tdc, double lobe) {
		return new CylinderModel(CylinderSpec.CRUDE, CamProfile.BASIC, tdc, new Camshaft(), lobe);
	}

	@Test
	void theTimingErrorIsTheDistanceFromTopDeadCentreWithinHalfATurn() {
		// error = 2 * lobe - direction * tdc, brought into (-180, 180]
		assertEquals(0, cylinder(90, 45).timingError(1), 1e-9);
		assertEquals(110, cylinder(90, 100).timingError(1), 1e-9);
		assertEquals(-170, cylinder(90, 140).timingError(1), 1e-9);
		assertEquals(180, cylinder(20, 100).timingError(1), 1e-9);
		assertEquals(180, cylinder(180, 0).timingError(1), 1e-9);
		assertEquals(170, cylinder(20, 95).timingError(1), 1e-9);
		assertEquals(-90, cylinder(0, 135).timingError(1), 1e-9);
		assertEquals(90, cylinder(540, 135).timingError(1), 1e-9);
		// Reversing the crank flips the sign of the top dead centre's part
		assertEquals(2 * 45 + 90, cylinder(90, 45).timingError(-1), 1e-9);
	}

	@Test
	void aCylinderSharingALobeIsShiftedByHalfTheTopDeadCentreGapAndMirroredWhenReversed() {
		CylinderModel follower = cylinder(180, 10);
		follower.shareLobe(0);
		// forward: wrap(180 - 0, 360) / 2 = 90
		assertEquals(90, follower.lobeShift(), 1e-9);
		assertEquals(100, follower.lobeAngle(), 1e-9);
		follower.setDirection(-1);
		// reverse: wrap(0 - 180, 360) / 2 = 90 as well for a gap of exactly half a turn
		assertEquals(90, follower.lobeShift(), 1e-9);
		CylinderModel other = cylinder(90, 0);
		other.shareLobe(0);
		assertEquals(45, other.lobeShift(), 1e-9);
		other.setDirection(-1);
		assertEquals(135, other.lobeShift(), 1e-9);
		assertEquals(135, other.lobeAngle(), 1e-9);
		other.setDirection(-1);
		assertEquals(135, other.lobeShift(), 1e-9);
		other.setDirection(1);
		assertEquals(45, other.lobeShift(), 1e-9);
		assertEquals(2 * other.lobeAngle(), other.firingRotorAngle(), 1e-9);
		assertEquals(123, cylinder(123, 0).tdcCrankAngle(), 0);
	}

	@Test
	void onlyACylinderWithACamOrPortsIsTimed() {
		assertFalse(new CylinderModel(CylinderSpec.CRUDE, CamProfile.BASIC, 0, null, 0).isTimed());
		assertTrue(cylinder(0, 0).isTimed());
		CylinderModel rotary = CylinderModel.rotary(CylinderSpec.buildRotary(false), CamProfile.ROTARY, 0, 0);
		assertTrue(rotary.isTimed());
		assertTrue(rotary.ported());
		assertFalse(cylinder(0, 0).ported());
	}

	private static CylinderModel running() {
		CylinderModel c = cylinder(0, 0);
		c.fuelSupply = new FuelSupply() {
			@Override
			public double draw(double mb) {
				return mb;
			}

			@Override
			public FuelSpec fuel() {
				return FuelSpec.DIESEL;
			}
		};
		return c;
	}

	@Test
	void copyingTheStateOfARunningCylinderCopiesItsCycleFigures() {
		EngineSimulation engine = new EngineSimulation(java.util.List.of(running()), 2, 0.02);
		for (int i = 0; i < 200; i++)
			engine.tickDriven(EngineSimulation.rpmToOmega(100), 1);
		CylinderModel original = engine.cylinders().get(0);
		CycleStats shown = original.lastCycle();
		assertTrue(shown.work != 0 || shown.fuelInjected != 0, "the cylinder has to have done something for this to mean anything");

		CylinderModel copy = running();
		copy.copyStateFrom(original);
		assertNotSame(original.lastCycle(), copy.lastCycle());
		assertEquals(shown.work, copy.lastCycle().work, 0);
		assertEquals(shown.fuelInjected, copy.lastCycle().fuelInjected, 0);
		assertEquals(shown.fuelBurned, copy.lastCycle().fuelBurned, 0);
		assertEquals(shown.peakPressure, copy.lastCycle().peakPressure, 0);
		assertEquals(shown.compressionTemp, copy.lastCycle().compressionTemp, 0);
		// The in-progress cycle was copied too: ticking both the same way keeps them in step
		EngineSimulation a = new EngineSimulation(java.util.List.of(original), 2, 0.02);
		EngineSimulation b = new EngineSimulation(java.util.List.of(copy), 2, 0.02);
		a.restore(engine.angle(), engine.omega(), 1);
		b.restore(engine.angle(), engine.omega(), 1);
		for (int i = 0; i < 40; i++) {
			a.tickDriven(EngineSimulation.rpmToOmega(100), 1);
			b.tickDriven(EngineSimulation.rpmToOmega(100), 1);
		}
		assertEquals(a.cylinders().get(0).lastCycle().work, b.cylinders().get(0).lastCycle().work, 1e-9);
		assertEquals(a.netTorque(), b.netTorque(), 1e-9);
	}
}
