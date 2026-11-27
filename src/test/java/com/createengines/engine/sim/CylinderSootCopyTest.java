package com.createengines.engine.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/** A cylinder whose engine is rebuilt round it, as when a ring goes on, stays as sooted as it was. */
class CylinderSootCopyTest {

	/** A spark cylinder run far too rich, which soots its plug and leaves soot to blow out. */
	private static CylinderModel sooted() {
		CylinderModel model = new CylinderModel(CylinderSpec.CRUDE, CamProfile.BASIC, 0, new Camshaft(), 0);
		model.fuelSupply = MixtureTest.unlimited(FuelSpec.GASOLINE);
		model.ignition = IgnitionSource.UNLIMITED;
		model.metering = 2;
		EngineSimulation sim = new EngineSimulation(List.of(model), 1.35, 0.03);
		model.resetGas(0);
		for (int tick = 0; tick < 600; tick++)
			sim.tickDriven(EngineSimulation.rpmToOmega(160), 1);
		return model;
	}

	@Test
	void aCopyOfACylindersStateKeepsItsSoot() {
		CylinderModel original = sooted();
		assertTrue(original.fouling() > 0, "running this rich soots the plug: " + original.fouling());
		CylinderModel rebuilt = new CylinderModel(CylinderSpec.CRUDE, CamProfile.BASIC, 0, new Camshaft(), 0);
		assertEquals(0.0, rebuilt.fouling());
		rebuilt.copyStateFrom(original);
		assertEquals(original.fouling(), rebuilt.fouling());
		double blownOut = original.takeSoot();
		assertTrue(blownOut > 0, "and leaves soot to blow out");
		assertEquals(blownOut, rebuilt.takeSoot());
	}
}
