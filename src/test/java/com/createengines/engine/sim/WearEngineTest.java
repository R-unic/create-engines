package com.createengines.engine.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * What wear does to an engine, measured on the dyno: nothing when there is none (to the last bit), less torque the more there is, and
 * never more than a sound engine.
 */
class WearEngineTest {

	private static double[] curve(Wear.State wear, FuelSpec fuel) {
		Camshaft cam = new Camshaft();
		List<CylinderModel> models = new ArrayList<>();
		for (int i = 0; i < 4; i++) {
			double fire = i * 180;
			CylinderModel model = new CylinderModel(CylinderSpec.CRUDE, CamProfile.BASIC, fire % 360, cam, fire / 2);
			model.fuelSupply = MixtureTest.unlimited(fuel);
			model.ignition = IgnitionSource.UNLIMITED;
			model.wear = wear;
			models.add(model);
		}
		EngineSimulation sim = new EngineSimulation(models, models.size() * 0.35 + 1, (models.size() * 2 + 1) * 0.01);
		for (CylinderModel model : models)
			model.resetGas(0);
		return Dynamometer.torqueCurve(sim);
	}

	private static double peak(double[] curve) {
		double best = 0;
		for (double t : curve)
			best = Math.max(best, t);
		return best;
	}

	@Test
	void aSoundStateIsBitForBitTheEngineWithNoWear() {
		for (FuelSpec fuel : new FuelSpec[] {FuelSpec.GASOLINE, FuelSpec.DIESEL}) {
			double[] none = curve(Wear.NONE, fuel);
			double[] sound = curve(Wear.of(0, Wear.Gasket.STOCK, 0), fuel);
			assertEquals(none.length, sound.length);
			for (int i = 0; i < none.length; i++)
				assertEquals(Double.doubleToLongBits(none[i]), Double.doubleToLongBits(sound[i]), fuel + " at speed " + i);
		}
	}

	@Test
	void moreWearMeansLessTorqueAndNeverMoreThanASoundEngine() {
		for (FuelSpec fuel : new FuelSpec[] {FuelSpec.GASOLINE, FuelSpec.DIESEL}) {
			double sound = peak(curve(Wear.NONE, fuel));
			double light = peak(curve(Wear.of(0.3, Wear.Gasket.STOCK, 0.3), fuel));
			double heavy = peak(curve(Wear.of(0.7, Wear.Gasket.STOCK, 0.7), fuel));
			double spent = peak(curve(Wear.of(1, Wear.Gasket.STOCK, 1), fuel));
			assertTrue(sound > light && light > heavy && heavy > spent, fuel + ": " + sound + " > " + light + " > " + heavy + " > " + spent);
			assertTrue(spent > 0.25 * sound, fuel + ": a spent engine is weak, not dead: " + spent + " of " + sound);
			// A part is not at risk until half worn, and by then the engine has said so; light wear is felt a little, not a lot
			double atRisk = peak(curve(Wear.of(Wear.FAILURE_FROM, Wear.Gasket.STOCK, Wear.FAILURE_FROM), fuel));
			assertTrue(atRisk < 0.97 * sound && atRisk > 0.6 * sound, fuel + ": at the failure threshold " + atRisk + " of " + sound);
			assertTrue(light > 0.9 * sound, fuel + ": light wear costs under a tenth: " + light + " of " + sound);
		}
	}

	@Test
	void aWornGasketCostsPowerAndABetterOneCostsLess() {
		double stock = peak(curve(Wear.of(0, Wear.Gasket.STOCK, 0.8), FuelSpec.GASOLINE));
		double copper = peak(curve(Wear.of(0, Wear.Gasket.COPPER, 0.8), FuelSpec.GASOLINE));
		double sound = peak(curve(Wear.NONE, FuelSpec.GASOLINE));
		assertTrue(stock < copper && copper < sound, stock + " < " + copper + " < " + sound);
	}

	@Test
	void aWornPistonAloneDragsTheEngineDown() {
		Wear.State dragOnly = new Wear.State() {
			@Override
			public double seal() {
				return 1;
			}

			@Override
			public double drag() {
				return 1.5;
			}
		};
		assertTrue(peak(curve(dragOnly, FuelSpec.GASOLINE)) < peak(curve(Wear.NONE, FuelSpec.GASOLINE)));
	}

	@Test
	void aFittedGasketGivesMorePowerWithTheModeOffAndABetterOneMore() {
		for (FuelSpec fuel : new FuelSpec[] {FuelSpec.GASOLINE, FuelSpec.DIESEL}) {
			double none = peak(curve(Wear.NONE, fuel));
			double last = none;
			for (Wear.Gasket tier : Wear.Gasket.values()) {
				double fitted = peak(curve(Wear.fitted(tier), fuel));
				assertTrue(fitted > last, fuel + " " + tier + " " + fitted + " should beat " + last);
				last = fitted;
			}
			double copper = peak(curve(Wear.fitted(Wear.Gasket.COPPER), fuel));
			assertTrue(copper > 1.02 * none, fuel + ": a copper gasket is worth a few percent: " + copper + " over " + none);
			assertTrue(copper < 1.12 * none, fuel + ": and only a few: " + copper + " over " + none);
		}
	}

	@Test
	void aHeadWithNoGasketFittedIsExactlyTheEngineBeforeGasketsExisted() {
		for (FuelSpec fuel : new FuelSpec[] {FuelSpec.GASOLINE, FuelSpec.DIESEL}) {
			double[] none = curve(Wear.NONE, fuel);
			double[] unfitted = curve(Wear.of(0, Wear.Gasket.STOCK, 0, false), fuel);
			for (int i = 0; i < none.length; i++)
				assertEquals(Double.doubleToLongBits(none[i]), Double.doubleToLongBits(unfitted[i]), fuel + " at speed " + i);
		}
	}

	@Test
	void aWearingGasketKeepsItsBonusOnlyWhileItIsGoodAndNeverBeatsANewOne() {
		double newest = peak(curve(Wear.of(0, Wear.Gasket.COPPER, 0, true), FuelSpec.GASOLINE));
		double aged = peak(curve(Wear.of(0, Wear.Gasket.COPPER, 0.5, true), FuelSpec.GASOLINE));
		double none = peak(curve(Wear.NONE, FuelSpec.GASOLINE));
		assertTrue(newest > aged, newest + " > " + aged);
		assertTrue(aged > none * 0.9, "half worn, copper is still near a head with none: " + aged + " vs " + none);
	}
}
