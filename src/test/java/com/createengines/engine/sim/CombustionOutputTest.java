package com.createengines.engine.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.createengines.engine.EngineMilestones;

/**
 * Pins what an engine is worth next to a Create steam engine, so the balance cannot drift unnoticed: a large combustion
 * engine has to be several steam engines' worth, or it is not worth the fuel, the plumbing and the tuning.
 */
class CombustionOutputTest {

	/** The server config default, SU per unit of simulated torque. */
	private static final double STRESS_PER_TORQUE = 78.7;
	private static final double STEAM_ENGINE = EngineMilestones.STEAM_ENGINE;

	/** An in-line engine of {@code cylinders}, evenly fired, on a MAF-trimmed 200% shot. Returns the most power it makes, SU. */
	private static double peakPower(int cylinders, CylinderSpec spec, FuelSpec fuel, boolean spark, boolean turbo) {
		Camshaft cam = new Camshaft();
		List<CylinderModel> models = new ArrayList<>();
		double step = 720d / cylinders;
		for (int i = 0; i < cylinders; i++) {
			double fire = i * step;
			CylinderModel model = new CylinderModel(spec, CamProfile.BASIC, fire % 360, cam, fire / 2);
			model.fuelSupply = MixtureTest.unlimited(fuel);
			model.metering = 2;
			model.airMatch = 1;
			model.ignition = spark ? IgnitionSource.UNLIMITED : IgnitionSource.NONE;
			models.add(model);
		}
		EngineSimulation sim = new EngineSimulation(models, cylinders * 0.35 + 1, (cylinders * 2 + 1) * 0.01);
		for (CylinderModel model : models)
			model.resetGas(0);
		if (turbo) {
			Turbocharger unit = new Turbocharger(models, models, 1);
			unit.limit = 0.6;
			sim.addTurbo(unit);
		}
		double[] curve = Dynamometer.torqueCurve(sim);
		double best = 0;
		for (int i = 0; i < curve.length; i++)
			best = Math.max(best, curve[i] * Dynamometer.SPEEDS[i] * STRESS_PER_TORQUE);
		return best;
	}

	@Test
	void aStraightTwelveOnGasolineIsSeveralSteamEngines() {
		double power = peakPower(12, CylinderSpec.CRUDE, FuelSpec.GASOLINE, true, false);
		assertTrue(power > 3 * STEAM_ENGINE, "straight-12 on gasoline: " + power + " SU against " + STEAM_ENGINE);
	}

	@Test
	void aFourCylinderDieselIsAboutOneSteamEngine() {
		double power = peakPower(4, CylinderSpec.CRUDE, FuelSpec.DIESEL, false, false);
		assertTrue(power > 0.6 * STEAM_ENGINE && power < 1.6 * STEAM_ENGINE, "four-cylinder diesel: " + power);
	}

	@Test
	void gasolineIsWorthRefining() {
		double diesel = peakPower(12, CylinderSpec.CRUDE, FuelSpec.DIESEL, false, false);
		double gasoline = peakPower(12, CylinderSpec.CRUDE, FuelSpec.GASOLINE, true, false);
		assertTrue(gasoline > diesel * 1.2, "gasoline " + gasoline + " against diesel " + diesel);
	}

	@Test
	void everyPrecisionPartHelpsOnItsOwn() {
		double crude = peakPower(12, CylinderSpec.CRUDE, FuelSpec.GASOLINE, true, false);
		boolean[][] parts = {{true, false, false, false}, {false, true, false, false}, {false, false, true, false},
			{false, false, false, true}};
		for (boolean[] part : parts) {
			CylinderSpec spec = CylinderSpec.build(part[0], part[1], part[2], part[3]);
			double power = peakPower(12, spec, FuelSpec.GASOLINE, true, false);
			assertTrue(power > crude * 1.03, "part " + java.util.Arrays.toString(part) + " made " + power + " against " + crude);
		}
	}

	@Test
	void precisionPartsAddUpToWellOverHalfAgain() {
		double crude = peakPower(12, CylinderSpec.CRUDE, FuelSpec.GASOLINE, true, false);
		double all = peakPower(12, CylinderSpec.build(true, true, true, true), FuelSpec.GASOLINE, true, false);
		assertTrue(all > crude * 1.5, "all precision " + all + " against " + crude);
	}

	@Test
	void aTurbochargerMultipliesWhatPrecisionPartsMake() {
		CylinderSpec precision = CylinderSpec.build(true, true, true, true);
		double plain = peakPower(12, precision, FuelSpec.GASOLINE, true, false);
		double boosted = peakPower(12, precision, FuelSpec.GASOLINE, true, true);
		assertTrue(boosted > plain * 1.4, "boosted " + boosted + " against " + plain);
		assertTrue(boosted > 8 * STEAM_ENGINE, "a full-house engine is many steam engines: " + boosted);
	}

	@Test
	void theCrudeSpecIsWhatABuildWithNoPrecisionPartsGets() {
		CylinderSpec none = CylinderSpec.build(false, false, false, false);
		assertEquals(CylinderSpec.CRUDE, none);
	}
}
