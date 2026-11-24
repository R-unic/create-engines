package com.createengines.engine.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

/**
 * The high-energy ignition coil: a bonus that is only ever there when it was paid for. A plug with no coil, or one the bank cannot
 * spare the charge for, must spark exactly as it always did.
 */
class IgnitionCoilTest {

	/** A coil that counts how often it was asked for charge, and can be told to have none to give. */
	private static final class CountingCoil implements IgnitionCoil {
		final AtomicInteger fired = new AtomicInteger();
		final boolean charged;

		CountingCoil(boolean charged) {
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

	private static List<CylinderModel> engine(int cylinders, FuelSpec fuel, IgnitionSource ignition, IgnitionCoil coil) {
		Camshaft cam = new Camshaft();
		List<CylinderModel> models = new ArrayList<>();
		double step = 720d / cylinders;
		for (int i = 0; i < cylinders; i++) {
			double fire = i * step;
			CylinderModel model = new CylinderModel(CylinderSpec.CRUDE, CamProfile.BASIC, fire % 360, cam, fire / 2);
			model.fuelSupply = MixtureTest.unlimited(fuel);
			model.ignition = ignition;
			model.coil = coil;
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

	private static double[] curve(IgnitionCoil coil, FuelSpec fuel) {
		return Dynamometer.torqueCurve(simulation(engine(4, fuel, IgnitionSource.UNLIMITED, coil)));
	}

	@Test
	void theNumbersAreWhatTheyAreMeantToBe() {
		assertTrue(IgnitionCoil.COST > 0 && IgnitionCoil.COST < 1, "a coiled spark costs less than another spark: " + IgnitionCoil.COST);
		assertTrue(IgnitionCoil.LEAN < 1 && IgnitionCoil.LEAN > 0.5, "lights a leaner mixture, not any mixture: " + IgnitionCoil.LEAN);
		assertTrue(IgnitionCoil.FOULING < 1 && IgnitionCoil.FOULING > 0, "a hot spark helps with soot, it does not ignore it");
		assertTrue(IgnitionCoil.BURN < 1 && IgnitionCoil.BURN > 0.5, "burns faster, not instantly: " + IgnitionCoil.BURN);
	}

	@Test
	void anUncoiledBurnIsExactlyTheBurnItAlwaysWasAndACoiledOneIsFaster() {
		for (double value : new double[] {0, 1e-9, 0.06, 1, 1e6})
			assertEquals(Double.doubleToLongBits(value), Double.doubleToLongBits(IgnitionCoil.burn(value, false)));
		assertEquals(0.06 * IgnitionCoil.BURN, IgnitionCoil.burn(0.06, true), 1e-15);
	}

	@Test
	void aPoweredCoilMakesMoreTorqueThanNoneOnAnyFuelThatBurns() {
		for (FuelSpec fuel : new FuelSpec[] {FuelSpec.GASOLINE, FuelSpec.METHANOL}) {
			double none = 0, coiled = 0;
			for (double t : curve(IgnitionCoil.NONE, fuel))
				none = Math.max(none, t);
			for (double t : curve(IgnitionCoil.UNLIMITED, fuel))
				coiled = Math.max(coiled, t);
			assertTrue(coiled > none, "a coil's faster burn is felt on a healthy engine: " + coiled + " against " + none);
		}
	}

	@Test
	void anUncoiledSparkIsExactlyTheSparkItAlwaysWas() {
		for (double value : new double[] {0, 1e-9, 0.02, 0.4, 0.5, 1, 12.5, 1e6}) {
			assertEquals(Double.doubleToLongBits(value), Double.doubleToLongBits(IgnitionCoil.leanLimit(value, false)));
			assertEquals(Double.doubleToLongBits(value), Double.doubleToLongBits(IgnitionCoil.fouledLoss(value, false)));
		}
	}

	@Test
	void aCoiledSparkLightsALeanerMixtureAndShrugsOffMoreSoot() {
		assertEquals(0.5 * IgnitionCoil.LEAN, IgnitionCoil.leanLimit(0.5, true), 1e-15);
		assertEquals(0.4 * IgnitionCoil.FOULING, IgnitionCoil.fouledLoss(0.4, true), 1e-15);
		assertTrue(IgnitionCoil.leanLimit(0.5, true) < 0.5);
		assertTrue(IgnitionCoil.fouledLoss(0.4, true) < 0.4);
	}

	@Test
	void soilingMovesTheRichLimitLessWhenCoiledAndNotAtAllWhenClean() {
		Soiling dirty = new Soiling();
		dirtyUp(dirty);
		assertTrue(dirty.fouling() > 0, "the plug is fouled for this test");
		assertEquals(dirty.richLimit(), dirty.richLimit(false), 0, "uncoiled is the limit it always was");
		assertTrue(dirty.richLimit(true) > dirty.richLimit(false), "a hotter spark lights a richer mixture through the soot");
		assertTrue(dirty.richLimit(true) <= CylinderModel.RICH_LIMIT, "but never past the limit a clean plug has");
		Soiling clean = new Soiling();
		assertEquals(clean.richLimit(false), clean.richLimit(true), 0, "a clean plug has nothing to burn off, coil or not");
	}

	/** Soot is a private figure the sim builds up from rich cycles; the test sets it directly to look at what it does to the limit. */
	private static void dirtyUp(Soiling soiling) {
		try {
			java.lang.reflect.Field fouling = Soiling.class.getDeclaredField("fouling");
			fouling.setAccessible(true);
			fouling.setDouble(soiling, 0.8);
		} catch (ReflectiveOperationException e) {
			throw new AssertionError(e);
		}
	}

	@Test
	void noCoilFittedIsTheSameAsNeverHavingOne() {
		Camshaft cam = new Camshaft();
		List<CylinderModel> untouched = new ArrayList<>();
		for (int i = 0; i < 4; i++) {
			double fire = i * 180;
			CylinderModel model = new CylinderModel(CylinderSpec.CRUDE, CamProfile.BASIC, fire % 360, cam, fire / 2);
			model.fuelSupply = MixtureTest.unlimited(FuelSpec.GASOLINE);
			model.ignition = IgnitionSource.UNLIMITED;
			untouched.add(model);
		}
		double[] never = Dynamometer.torqueCurve(simulation(untouched));
		double[] none = curve(IgnitionCoil.NONE, FuelSpec.GASOLINE);
		assertEquals(never.length, none.length);
		for (int i = 0; i < never.length; i++)
			assertEquals(Double.doubleToLongBits(never[i]), Double.doubleToLongBits(none[i]), "speed " + i);
	}

	@Test
	void aCoilWithNoChargeToSpareChangesNothing() {
		double[] none = curve(IgnitionCoil.NONE, FuelSpec.GASOLINE);
		double[] flat = curve(new CountingCoil(false), FuelSpec.GASOLINE);
		for (int i = 0; i < none.length; i++)
			assertEquals(Double.doubleToLongBits(none[i]), Double.doubleToLongBits(flat[i]),
				"a flat coil leaves the torque at speed " + i + " as it was");
	}

	@Test
	void aCoilNeverMakesAPlainPlugDoLess() {
		for (FuelSpec fuel : new FuelSpec[] {FuelSpec.GASOLINE, FuelSpec.ETHANOL}) {
			double[] none = curve(IgnitionCoil.NONE, fuel);
			double[] coiled = curve(IgnitionCoil.UNLIMITED, fuel);
			for (int i = 0; i < none.length; i++)
				assertTrue(coiled[i] >= none[i] - 1e-12, "a coil costs charge, never torque: speed " + i + " " + coiled[i] + " against " + none[i]);
		}
	}

	@Test
	void aCoilIsAskedOncePerSparkThatHasFuelToLight() {
		CountingCoil coil = new CountingCoil(true);
		List<CylinderModel> models = engine(1, FuelSpec.GASOLINE, IgnitionSource.UNLIMITED, coil);
		EngineSimulation sim = simulation(models);
		sim.restore(0, 0, 1);
		for (int t = 0; t < 400; t++)
			sim.tickDriven(EngineSimulation.rpmToOmega(120), 1);
		assertTrue(coil.fired.get() > 0, "an engine that runs lights a charge, and the coil is asked");
		CylinderModel model = models.get(0);
		assertTrue(model.lastCycle().coiledSparks <= 1, "one coiled spark to a cycle");
		assertEquals(0, model.lastCycle().deadCoils);

		// No fuel at all: nothing to light, so nothing to spend charge on
		CountingCoil idle = new CountingCoil(true);
		List<CylinderModel> dry = engine(1, FuelSpec.GASOLINE, IgnitionSource.UNLIMITED, idle);
		dry.get(0).fuelSupply = FuelSupply.NONE;
		EngineSimulation dryRun = simulation(dry);
		dryRun.restore(0, 0, 1);
		for (int t = 0; t < 400; t++)
			dryRun.tickDriven(EngineSimulation.rpmToOmega(120), 1);
		assertEquals(0, idle.fired.get(), "a dry cylinder spends no charge on its spark");
	}

	@Test
	void aFlatCoilIsCountedAsDeadNotAsCoiled() {
		CountingCoil flat = new CountingCoil(false);
		List<CylinderModel> models = engine(1, FuelSpec.GASOLINE, IgnitionSource.UNLIMITED, flat);
		EngineSimulation sim = simulation(models);
		sim.restore(0, 0, 1);
		for (int t = 0; t < 400; t++)
			sim.tickDriven(EngineSimulation.rpmToOmega(120), 1);
		CylinderModel model = models.get(0);
		assertEquals(0, model.lastCycle().coiledSparks);
		assertTrue(model.lastCycle().deadCoils >= 1 || flat.fired.get() >= 1, "it was asked and could not give");
	}

	@Test
	void aCompressionEngineNeverAsksForACoil() {
		CountingCoil coil = new CountingCoil(true);
		List<CylinderModel> models = engine(1, FuelSpec.DIESEL, IgnitionSource.NONE, coil);
		EngineSimulation sim = simulation(models);
		sim.restore(0, 0, 1);
		for (int t = 0; t < 400; t++)
			sim.tickDriven(EngineSimulation.rpmToOmega(120), 1);
		assertEquals(0, coil.fired.get(), "no plug, no spark, nothing for a coil to add to");
	}
}
