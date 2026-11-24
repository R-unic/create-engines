package com.createengines.engine.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

/**
 * The electric injector assist: a bonus that is only ever there when it was paid for. A cylinder with no assist, or one the bank
 * cannot spare the charge for, must run exactly as it always did.
 */
class InjectorAssistTest {

	/** An assist that counts how often it was asked for charge, and can be told to have none to give. */
	private static final class CountingAssist implements InjectorAssist {
		final AtomicInteger fired = new AtomicInteger();
		final boolean charged;

		CountingAssist(boolean charged) {
			this.charged = charged;
		}

		@Override
		public boolean installed() {
			return true;
		}

		@Override
		public boolean powered() {
			return charged;
		}

		@Override
		public boolean fire() {
			fired.incrementAndGet();
			return charged;
		}
	}

	private static List<CylinderModel> engine(int cylinders, FuelSpec fuel, boolean spark, InjectorAssist assist) {
		Camshaft cam = new Camshaft();
		List<CylinderModel> models = new ArrayList<>();
		double step = 720d / cylinders;
		for (int i = 0; i < cylinders; i++) {
			double fire = i * step;
			CylinderModel model = new CylinderModel(CylinderSpec.CRUDE, CamProfile.BASIC, fire % 360, cam, fire / 2);
			model.fuelSupply = MixtureTest.unlimited(fuel);
			model.ignition = spark ? IgnitionSource.UNLIMITED : IgnitionSource.NONE;
			model.assist = assist;
			models.add(model);
		}
		return models;
	}

	private static EngineSimulation simulation(List<CylinderModel> models) {
		EngineSimulation sim = new EngineSimulation(models, models.size() * 0.35 + 1, (models.size() * 2 + 1) * 0.01);
		for (CylinderModel model : models)
			model.resetGas(0);
		return sim;
	}

	private static double[] curve(InjectorAssist assist, FuelSpec fuel, boolean spark) {
		return Dynamometer.torqueCurve(simulation(engine(4, fuel, spark, assist)));
	}

	private static double peak(double[] curve) {
		double best = 0;
		for (double torque : curve)
			best = Math.max(best, torque);
		return best;
	}

	@Test
	void theNumbersAreWhatTheyAreMeantToBe() {
		assertTrue(InjectorAssist.COST > 0 && InjectorAssist.COST < 1, "an assisted shot costs less than a spark: " + InjectorAssist.COST);
		assertTrue(InjectorAssist.DELAY < 1 && InjectorAssist.DELAY > 0.5, "lights sooner, not instantly");
		assertTrue(InjectorAssist.BURN < 1 && InjectorAssist.BURN > 0.5, "burns faster, not instantly");
		// Intended change (author, 2026-11-24): the assist's extra flow is what a boosted MAF engine on a thin fuel is capped by,
		// so it now passes half again as much, more than a precision injector's own gain
		assertTrue(InjectorAssist.FLOW >= 1.5 && InjectorAssist.FLOW <= 2, "a real gain at the injector's limit: " + InjectorAssist.FLOW);
		// Still quicker to light and burn than nothing, but not past the precision injector it sits beside
		assertTrue(InjectorAssist.DELAY > CylinderSpec.PRECISION_INJECTOR_DELAY);
		assertTrue(InjectorAssist.BURN > CylinderSpec.PRECISION_INJECTOR_BURN);
	}

	@Test
	void anUnassistedShotIsExactlyTheShotItAlwaysWas() {
		for (double value : new double[] {0, 1e-9, 0.02, 0.0472, 1, 12.5, 1e6}) {
			assertEquals(Double.doubleToLongBits(value), Double.doubleToLongBits(InjectorAssist.delay(value, false)));
			assertEquals(Double.doubleToLongBits(value), Double.doubleToLongBits(InjectorAssist.burn(value, false)));
			assertEquals(Double.doubleToLongBits(value), Double.doubleToLongBits(InjectorAssist.limit(value, false)));
		}
	}

	@Test
	void anAssistedShotLightsSoonerBurnsFasterAndPassesMore() {
		assertEquals(0.02 * InjectorAssist.DELAY, InjectorAssist.delay(0.02, true), 1e-15);
		assertEquals(0.06 * InjectorAssist.BURN, InjectorAssist.burn(0.06, true), 1e-15);
		assertEquals(10 * InjectorAssist.FLOW, InjectorAssist.limit(10, true), 1e-15);
		assertTrue(InjectorAssist.delay(0.02, true) < 0.02);
		assertTrue(InjectorAssist.burn(0.06, true) < 0.06);
		assertTrue(InjectorAssist.limit(10, true) > 10);
	}

	@Test
	void noAssistFittedIsTheSameAsNeverHavingOne() {
		double[] never = Dynamometer.torqueCurve(simulation(engineWithoutTouchingAssist()));
		double[] none = curve(InjectorAssist.NONE, FuelSpec.GASOLINE, true);
		assertEquals(never.length, none.length);
		for (int i = 0; i < never.length; i++)
			assertEquals(Double.doubleToLongBits(never[i]), Double.doubleToLongBits(none[i]), "speed " + i);
	}

	private static List<CylinderModel> engineWithoutTouchingAssist() {
		Camshaft cam = new Camshaft();
		List<CylinderModel> models = new ArrayList<>();
		for (int i = 0; i < 4; i++) {
			double fire = i * 180;
			CylinderModel model = new CylinderModel(CylinderSpec.CRUDE, CamProfile.BASIC, fire % 360, cam, fire / 2);
			model.fuelSupply = MixtureTest.unlimited(FuelSpec.GASOLINE);
			model.ignition = IgnitionSource.UNLIMITED;
			models.add(model);
		}
		return models;
	}

	@Test
	void anAssistWithNoChargeToSpareChangesNothing() {
		double[] none = curve(InjectorAssist.NONE, FuelSpec.GASOLINE, true);
		double[] flat = curve(new CountingAssist(false), FuelSpec.GASOLINE, true);
		for (int i = 0; i < none.length; i++)
			assertEquals(Double.doubleToLongBits(none[i]), Double.doubleToLongBits(flat[i]),
				"a flat assist leaves the torque at speed " + i + " as it was");
	}

	@Test
	void aPoweredAssistMakesMoreTorqueThanNone() {
		double[] none = curve(InjectorAssist.NONE, FuelSpec.GASOLINE, true);
		double[] assisted = curve(InjectorAssist.UNLIMITED, FuelSpec.GASOLINE, true);
		assertTrue(peak(assisted) > peak(none), "assisted peak " + peak(assisted) + " against " + peak(none));
		// A modest bonus, not a second precision injector: well under what the precision injector adds on its own
		double precision = peak(Dynamometer.torqueCurve(simulationOf(CylinderSpec.build(false, false, false, true))));
		assertTrue(peak(assisted) - peak(none) < precision - peak(none) + 1e-9,
			"the assist adds " + (peak(assisted) - peak(none)) + ", a precision injector " + (precision - peak(none)));
	}

	private static EngineSimulation simulationOf(CylinderSpec spec) {
		Camshaft cam = new Camshaft();
		List<CylinderModel> models = new ArrayList<>();
		for (int i = 0; i < 4; i++) {
			double fire = i * 180;
			CylinderModel model = new CylinderModel(spec, CamProfile.BASIC, fire % 360, cam, fire / 2);
			model.fuelSupply = MixtureTest.unlimited(FuelSpec.GASOLINE);
			model.ignition = IgnitionSource.UNLIMITED;
			models.add(model);
		}
		return simulation(models);
	}

	/** A boosted methanol engine on a MAF sensor, with an injector too small for the air it is fed: the case the assist is for. */
	private static double[] boostedMethanol(InjectorAssist assist) {
		List<CylinderModel> models = engine(4, FuelSpec.METHANOL, true, assist);
		for (CylinderModel model : models) {
			model.airMatch = 1;
			model.intakePressure = 2.5;
		}
		return Dynamometer.torqueCurve(simulation(models));
	}

	@Test
	void anAssistLiftsABoostedMethanolEngineThatTheInjectorCaps() {
		double none = peak(boostedMethanol(InjectorAssist.NONE));
		double assisted = peak(boostedMethanol(InjectorAssist.UNLIMITED));
		assertTrue(assisted > none * 1.05, "boosted methanol with an assist " + assisted + " against " + none + ": at least 5% more");
	}

	@Test
	void aCompressionEngineGainsToo() {
		double[] none = curve(InjectorAssist.NONE, FuelSpec.DIESEL, false);
		double[] assisted = curve(InjectorAssist.UNLIMITED, FuelSpec.DIESEL, false);
		assertTrue(peak(assisted) > peak(none), "diesel assisted " + peak(assisted) + " against " + peak(none));
	}

	@Test
	void anAssistIsAskedOnceForEachShotAndNeverWithoutFuel() {
		CountingAssist assist = new CountingAssist(true);
		List<CylinderModel> models = engine(1, FuelSpec.GASOLINE, true, assist);
		EngineSimulation sim = simulation(models);
		sim.restore(0, 0, 1);
		for (int t = 0; t < 400; t++)
			sim.tickDriven(EngineSimulation.rpmToOmega(120), 1);
		int shots = assist.fired.get();
		assertTrue(shots > 0, "an engine that runs injects, and the assist is asked");
		CylinderModel model = models.get(0);
		assertTrue(model.lastCycle().assistedShots <= 1, "one assisted shot to a cycle");
		assertEquals(0, model.lastCycle().deadAssists);

		// No fuel at all: nothing to inject, so nothing to spend charge on
		CountingAssist idle = new CountingAssist(true);
		List<CylinderModel> dry = engine(1, FuelSpec.GASOLINE, true, idle);
		dry.get(0).fuelSupply = FuelSupply.NONE;
		EngineSimulation dryRun = simulation(dry);
		dryRun.restore(0, 0, 1);
		for (int t = 0; t < 400; t++)
			dryRun.tickDriven(EngineSimulation.rpmToOmega(120), 1);
		assertEquals(0, idle.fired.get(), "a dry injector spends no charge");
	}

	@Test
	void aFlatAssistIsCountedAsDeadNotAsAssisted() {
		CountingAssist flat = new CountingAssist(false);
		List<CylinderModel> models = engine(1, FuelSpec.GASOLINE, true, flat);
		EngineSimulation sim = simulation(models);
		sim.restore(0, 0, 1);
		for (int t = 0; t < 400; t++)
			sim.tickDriven(EngineSimulation.rpmToOmega(120), 1);
		CylinderModel model = models.get(0);
		assertEquals(0, model.lastCycle().assistedShots);
		assertTrue(model.lastCycle().deadAssists >= 1 || flat.fired.get() >= 1, "it was asked and could not give");
	}
}
