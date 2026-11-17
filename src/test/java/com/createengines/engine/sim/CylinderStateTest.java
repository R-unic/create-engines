package com.createengines.engine.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * What a {@link CylinderModel} carries from step to step, checked where it matters: taken over by another model while a
 * charge is burning, and wiped by a reset. Written after mutation testing showed the copy and reset calls could be
 * removed without any test noticing.
 */
class CylinderStateTest {

	private static final double SLOW = EngineSimulation.rpmToOmega(16);

	@AfterEach
	void resetTuning() {
		Tuning.reset();
	}

	private static CylinderModel diesel() {
		CylinderModel c = new CylinderModel(CylinderSpec.CRUDE, CamProfile.BASIC, 0, new Camshaft(), 0);
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

	/** Turns the engine a little at a time until the cylinder is part way through burning its charge. */
	private static EngineSimulation burningMidCycle(CylinderModel cylinder) {
		EngineSimulation engine = new EngineSimulation(List.of(cylinder), 2, 0.02);
		for (int tick = 0; tick < 20000; tick++) {
			engine.tickDriven(SLOW, 1);
			CycleStats now = cylinder.currentCycle();
			if (now.fuelBurned > 0 && now.fuelBurned < now.fuelInjected - 1e-9)
				return engine;
		}
		throw new AssertionError("the cylinder never reached the middle of a burn");
	}

	@Test
	void aCopyOfACylinderMidBurnKeepsBurningTheSameWay() {
		CylinderModel original = diesel();
		EngineSimulation a = burningMidCycle(original);
		CylinderModel copy = diesel();
		copy.copyStateFrom(original);
		EngineSimulation b = new EngineSimulation(List.of(copy), 2, 0.02);
		b.restore(a.angle(), a.omega(), 1);

		assertEquals(original.currentCycle().fuelBurned, copy.currentCycle().fuelBurned, 0);
		assertEquals(original.currentCycle().fuelInjected, copy.currentCycle().fuelInjected, 0);
		assertEquals(original.lastCycle().fuelBurned, copy.lastCycle().fuelBurned, 0);
		assertEquals(original.lastCycle().work, copy.lastCycle().work, 0);
		for (int i = 0; i < 400; i++) {
			a.tickDriven(SLOW, 1);
			b.tickDriven(SLOW, 1);
			assertEquals(original.currentCycle().fuelBurned, copy.currentCycle().fuelBurned, 1e-12, "tick " + i);
			assertEquals(original.lastCycle().work, copy.lastCycle().work, 1e-12, "tick " + i);
		}
		assertTrue(original.lastCycle().fuelBurned > 0);
	}

	@Test
	void resettingTheGasEmptiesThePortsAndStopsABurn() {
		CylinderModel cylinder = diesel();
		EngineSimulation engine = burningMidCycle(cylinder);
		// Finish a tick or two so the exhaust has made some heat to report
		for (int i = 0; i < 400 && cylinder.exhaustHeatLastTick() <= 0; i++)
			engine.tickDriven(SLOW, 1);
		assertTrue(cylinder.exhaustHeatLastTick() > 0, "the cylinder has to have sent heat out of its exhaust first");

		cylinder.resetGas(0);
		assertEquals(0, cylinder.exhaustHeatLastTick(), 0);
		// The burn was stopped: with nothing waiting to burn, the rest of the stroke burns no more fuel
		double burnedBefore = cylinder.currentCycle().fuelBurned;
		CylinderModel fresh = cylinder;
		for (int i = 0; i < 3; i++)
			fresh.step(0, 1, 360, 1, 1e-3);
		assertEquals(burnedBefore, cylinder.currentCycle().fuelBurned, 0);
	}
}
