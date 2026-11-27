package com.createengines.engine.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Exact values found unpinned by the mutation run of 2.4.0's release gate. */
class ReleaseEdgesTest {

	/** A worn piston and gasket say nothing about the rod, so the cylinder swings what a standard rod does. */
	@Test
	void wornPartsAloneLeaveTheCranksInertiaAndSpeedFrictionAlone() {
		Wear.State worn = Wear.of(0.5, Wear.Gasket.STOCK, 0.5);
		assertEquals(1, worn.mass());
		assertEquals(1, worn.viscous());

		CylinderModel cylinder = new CylinderModel(CylinderSpec.CRUDE, CamProfile.BASIC, 0, new Camshaft(), 0);
		double sound = cylinder.inertia();
		cylinder.wear = worn;
		assertEquals(sound, cylinder.inertia(), 1e-12);
		cylinder.wear = Wear.withRod(worn, Wear.Rod.HEAVY);
		assertEquals(sound * 1.5, cylinder.inertia(), 1e-12);
		cylinder.wear = Wear.withRod(worn, Wear.Rod.LIGHT);
		assertEquals(sound * 0.7, cylinder.inertia(), 1e-12);
	}

	@Test
	void aCamProfileAskedForByKindIsTheOneInUse() {
		assertSame(CamProfiles.basic(), CamProfiles.of(CamProfiles.Kind.BASIC));
		assertSame(CamProfiles.rotary(), CamProfiles.of(CamProfiles.Kind.ROTARY));
	}

	/** A cold block at half a full load gets half the warm-up assist on top of its exhaust heat. */
	@Test
	void aColdBlockGetsAShareOfTheWarmUpAssistForALightLoad() {
		Thermal thermal = new Thermal(0);
		double heat = Thermal.FULL_LOAD_HEAT / 2;
		double dt = 0.05;
		thermal.update(heat, 1, dt, CoolantSupply.NONE);
		double expected = heat * Thermal.HEAT_SHARE * (1 + Thermal.WARMUP_ASSIST * 0.5) * dt / Thermal.CAPACITY;
		assertEquals(expected, thermal.temperature(), 1e-12);
	}

	/** A variable turbo with a bigger share of the flow needs that much more exhaust heat to open its vanes. */
	@Test
	void vanesOpenInProportionToTheTurbosShareOfTheFlow() {
		FuelSupply diesel = MixtureTest.unlimited(FuelSpec.DIESEL);
		CylinderModel cylinder = new CylinderModel(CylinderSpec.CRUDE, CamProfile.BASIC, 0, new Camshaft(), 0);
		cylinder.fuelSupply = diesel;
		EngineSimulation engine = new EngineSimulation(java.util.List.of(cylinder), 1, 0.02);
		cylinder.resetGas(cylinder.pistonAngle(0, 1));
		Turbocharger turbo = new Turbocharger(java.util.List.of(cylinder), java.util.List.of(cylinder), 1);
		turbo.variableGeometry = true;
		turbo.flow = 2;
		engine.addTurbo(turbo);

		int partlyOpen = 0;
		for (int i = 0; i < 200; i++) {
			engine.tickDriven(EngineSimulation.rpmToOmega(64), 1);
			double expected = 1 - Math.min(1, turbo.heatRate(0.05) / (15 * 2));
			assertEquals(expected, turbo.vanes(), 1e-9, "tick " + i);
			if (expected > 0.01 && expected < 0.99)
				partlyOpen++;
		}
		assertTrue(partlyOpen > 0, "the vanes were never part open, so the share of the flow was not tested");
	}
}
