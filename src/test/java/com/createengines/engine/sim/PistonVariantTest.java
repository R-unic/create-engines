package com.createengines.engine.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.createengines.engine.topology.PrecisionParts;

import org.junit.jupiter.api.Test;

/**
 * High and low compression pistons and rotors, and piston rings. The trade is the point: high compression wins without boost
 * and loses under heavy boost on gasoline, low compression the reverse, and neither changes anything for a build without one.
 * The dynamometer figures are printed, so a retune shows what it moved.
 */
class PistonVariantTest {

	private static final int FORGED = PrecisionParts.ALL;
	private static final int HIGH = FORGED | PrecisionParts.HIGH_COMPRESSION;
	private static final int LOW = FORGED | PrecisionParts.LOW_COMPRESSION;
	/** Boost, atmospheres over ambient, past which gasoline in a stock engine has plainly begun to light early. */
	private static final double HEAVY_BOOST = 1.5;

	private static List<CylinderModel> bank(int parts, FuelSpec fuel, Camshaft cam) {
		List<CylinderModel> models = new ArrayList<>();
		for (int i = 0; i < 4; i++) {
			double fire = i * 180;
			CylinderModel model = new CylinderModel(PrecisionParts.spec(parts), CamProfile.BASIC, fire % 360, cam, fire / 2);
			model.fuelSupply = MixtureTest.unlimited(fuel);
			model.metering = 2;
			model.airMatch = 1;
			if (fuel.needsSpark())
				model.ignition = IgnitionSource.UNLIMITED;
			model.ringSeal = PrecisionParts.ringSeal(parts);
			model.lightingHeat = PrecisionParts.lightingHeat(parts);
			models.add(model);
		}
		return models;
	}

	/** Peak power in SU of a four cylinder engine of these parts, with one turbo set to {@code boost}, or none at 0. */
	private static double peak(int parts, FuelSpec fuel, double boost) {
		List<CylinderModel> models = bank(parts, fuel, new Camshaft());
		EngineSimulation sim = new EngineSimulation(models, 4 * 0.35 + 1, 9 * 0.01);
		for (CylinderModel model : models)
			model.resetGas(0);
		if (boost > 0) {
			Turbocharger turbo = new Turbocharger(models, models, 1);
			turbo.limit = boost;
			sim.addTurbo(turbo);
		}
		return best(Dynamometer.torqueCurve(sim));
	}

	private static double best(double[] torque) {
		double best = 0;
		for (int i = 0; i < torque.length; i++)
			best = Math.max(best, torque[i] * Dynamometer.SPEEDS[i] * 78.7);
		assertTrue(Double.isFinite(best) && best > 0, "an engine makes finite power: " + best);
		return best;
	}

	private static double rotaryPeak(int parts, double boost) {
		Camshaft eccentric = new Camshaft();
		List<CylinderModel> models = new ArrayList<>();
		for (int chamber = 0; chamber < 3; chamber++) {
			CylinderModel model = CylinderModel.rotary(PrecisionParts.rotarySpec(parts), CamProfiles.rotary(), 0, chamber);
			model.fuelSupply = MixtureTest.unlimited(FuelSpec.GASOLINE);
			model.metering = 2;
			model.airMatch = 1;
			model.ignition = IgnitionSource.UNLIMITED;
			model.rotorEarlyLoss = PrecisionParts.rotorEarlyLoss(parts);
			model.rotorBoostGain = PrecisionParts.rotorBoostGain(parts);
			models.add(model);
		}
		EngineSimulation sim = new EngineSimulation(models, 1.5, 0.05);
		// A blower rather than a turbo: one rotor's exhaust does not spin a turbine hard enough to reach the limit
		if (boost > 0) {
			Supercharger blower = new Supercharger(models);
			blower.rpm = Supercharger.DESIGN_RPM;
			blower.limit = boost;
			sim.addSupercharger(blower);
		}
		return best(Dynamometer.torqueCurve(sim));
	}

	private static double change(double now, double before) {
		return now / before - 1;
	}

	@Test
	void highCompressionWinsWithoutBoostAndLosesUnderHeavyBoostOnGasoline() {
		for (FuelSpec fuel : new FuelSpec[] {FuelSpec.GASOLINE, FuelSpec.DIESEL, FuelSpec.ETHANOL}) {
			double stock = peak(FORGED, fuel, 0);
			assertTrue(change(peak(HIGH, fuel, 0), stock) > 0.025, "high compression is worth having without boost");
			assertTrue(change(peak(LOW, fuel, 0), stock) < -0.03, "low compression costs without boost");
		}
		double stock = peak(FORGED, FuelSpec.GASOLINE, HEAVY_BOOST);
		double high = change(peak(HIGH, FuelSpec.GASOLINE, HEAVY_BOOST), stock);
		double low = change(peak(LOW, FuelSpec.GASOLINE, HEAVY_BOOST), stock);
		assertTrue(low > 0.03, "low compression is the heavy boost build on gasoline: " + low);
		assertTrue(high < -0.03, "high compression pays for it under heavy boost on gasoline: " + high);
		double ethanol = peak(FORGED, FuelSpec.ETHANOL, HEAVY_BOOST);
		assertTrue(peak(HIGH, FuelSpec.ETHANOL, HEAVY_BOOST) > ethanol, "ethanol holds out, so high compression still pays");
	}

	@Test
	void rotorsTradeTheSameWay() {
		int high = PrecisionParts.INJECTOR | PrecisionParts.HIGH_COMPRESSION;
		int low = PrecisionParts.INJECTOR | PrecisionParts.LOW_COMPRESSION;
		double stock = rotaryPeak(PrecisionParts.INJECTOR, 0);
		assertTrue(rotaryPeak(high, 0) > stock * 1.02);
		assertTrue(rotaryPeak(low, 0) < stock * 0.97);
		double boosted = rotaryPeak(PrecisionParts.INJECTOR, HEAVY_BOOST);
		double highBoosted = change(rotaryPeak(high, HEAVY_BOOST), boosted);
		double lowBoosted = change(rotaryPeak(low, HEAVY_BOOST), boosted);
		assertTrue(lowBoosted > 0.03, "the low compression rotor is the heavy boost build: " + lowBoosted);
		assertTrue(highBoosted < -0.03, "the high compression rotor pays for it under heavy boost: " + highBoosted);
		assertEquals(0.0, PrecisionParts.rotorEarlyLoss(PrecisionParts.INJECTOR));
		assertEquals(0.0, PrecisionParts.rotorBoostGain(PrecisionParts.INJECTOR));
	}

	@Test
	void eachRingAddsALittleAndThreeStayModest() {
		double last = peak(FORGED, FuelSpec.GASOLINE, 0);
		double none = last;
		for (int rings = 1; rings <= CylinderSpec.MAX_RINGS; rings++) {
			double now = peak(PrecisionParts.withRings(FORGED, rings), FuelSpec.GASOLINE, 0);
			assertTrue(now > last * 1.005, "ring " + rings + " adds power: " + change(now, last));
			last = now;
		}
		assertTrue(change(last, none) < 0.06, "three rings are an upgrade, not another engine: " + change(last, none));
	}

	@Test
	void aStockPistonAndRotorAreExactlyAsBefore() {
		for (int parts = 0; parts <= PrecisionParts.ALL; parts++) {
			CylinderSpec before = CylinderSpec.build(PrecisionParts.has(parts, PrecisionParts.BORE), PrecisionParts.has(parts, PrecisionParts.HEAD),
				PrecisionParts.has(parts, PrecisionParts.PISTON), PrecisionParts.has(parts, PrecisionParts.INJECTOR));
			assertEquals(before, PrecisionParts.spec(parts));
			assertEquals(1.0, PrecisionParts.ringSeal(parts));
			assertEquals(1.0, PrecisionParts.lightingHeat(parts));
			assertNull(PrecisionParts.compression(parts));
			assertEquals(0, PrecisionParts.rings(parts));
		}
		assertEquals(CylinderSpec.buildRotary(false), PrecisionParts.rotarySpec(0));
		assertEquals(CylinderSpec.buildRotary(true), PrecisionParts.rotarySpec(PrecisionParts.INJECTOR));
	}

	@Test
	void compressionMovesTheCompressionRatioAndNothingElse() {
		CylinderSpec stock = PrecisionParts.spec(FORGED);
		CylinderSpec high = PrecisionParts.spec(HIGH);
		CylinderSpec low = PrecisionParts.spec(LOW);
		assertEquals(stock.compressionRatio() * CylinderSpec.HIGH_COMPRESSION, high.compressionRatio(), 1e-9);
		assertEquals(stock.compressionRatio() * CylinderSpec.LOW_COMPRESSION, low.compressionRatio(), 1e-9);
		for (CylinderSpec other : new CylinderSpec[] {high, low}) {
			assertEquals(stock.inertia(), other.inertia());
			assertEquals(stock.frictionStatic(), other.frictionStatic());
			assertEquals(stock.frictionViscous(), other.frictionViscous());
			assertEquals(stock.maxFuelPerShot(), other.maxFuelPerShot());
			assertEquals(stock.intakeFlow(), other.intakeFlow());
		}
	}

	@Test
	void ringBitsStandApartFromTheOtherPartsAndStayInRange() {
		for (int parts : new int[] {0, FORGED, HIGH, LOW}) {
			for (int rings = -1; rings <= 5; rings++) {
				int bits = PrecisionParts.withRings(parts, rings);
				assertEquals(Math.clamp(rings, 0, CylinderSpec.MAX_RINGS), PrecisionParts.rings(bits));
				assertEquals(PrecisionParts.count(parts), PrecisionParts.count(bits));
				assertEquals(PrecisionParts.compression(parts), PrecisionParts.compression(bits));
			}
		}
	}

	/** A diesel with no plug on a low compression piston judges lighting by the stock piston's temperature. */
	@Test
	void lowCompressionLightsADieselAsEarlyAsAForgedPiston() {
		double stock = CylinderSpec.build(true, true, true, true).compressionRatio();
		double low = PrecisionParts.spec(LOW).compressionRatio();
		double squeezedStock = Math.pow(stock, CylinderModel.HEATING_EXPONENT);
		double squeezedLow = Math.pow(low, CylinderModel.HEATING_EXPONENT) * PrecisionParts.lightingHeat(LOW);
		assertEquals(squeezedStock, squeezedLow, 1e-9);
		assertEquals(1.0, PrecisionParts.lightingHeat(HIGH), "high compression keeps its own, hotter charge");
	}

	@Test
	void aTestCopyKeepsTheRingsAndTheLighting() {
		CylinderModel model = bank(PrecisionParts.withRings(LOW, 2), FuelSpec.DIESEL, new Camshaft()).get(0);
		CylinderModel copy = model.copyOnto(new Camshaft());
		assertEquals(model.ringSeal, copy.ringSeal);
		assertEquals(model.lightingHeat, copy.lightingHeat);
		assertTrue(copy.ringSeal > 1 && copy.lightingHeat > 1);
	}

	@Test
	void printTheDynoTable() {
		StringBuilder table = new StringBuilder("Peak power against the forged piston (4 cylinders, every precision part, one turbo)\n");
		for (Object[] run : new Object[][] {{"gasoline", FuelSpec.GASOLINE, 0d}, {"gasoline", FuelSpec.GASOLINE, 0.6},
			{"gasoline", FuelSpec.GASOLINE, 1.0}, {"gasoline", FuelSpec.GASOLINE, 1.5}, {"diesel", FuelSpec.DIESEL, 0d},
			{"ethanol", FuelSpec.ETHANOL, 0d}, {"ethanol", FuelSpec.ETHANOL, 1.5}}) {
			FuelSpec fuel = (FuelSpec) run[1];
			double boost = (Double) run[2];
			double stock = peak(FORGED, fuel, boost);
			table.append(String.format(Locale.ROOT, "%s boost %.1f: high %+.1f%%, low %+.1f%%%n", run[0], boost,
				100 * change(peak(HIGH, fuel, boost), stock), 100 * change(peak(LOW, fuel, boost), stock)));
		}
		for (double boost : new double[] {0, 1.0, HEAVY_BOOST}) {
			double stock = rotaryPeak(PrecisionParts.INJECTOR, boost);
			table.append(String.format(Locale.ROOT, "rotor, gasoline, blower %.1f: high %+.1f%%, low %+.1f%%%n", boost,
				100 * change(rotaryPeak(PrecisionParts.INJECTOR | PrecisionParts.HIGH_COMPRESSION, boost), stock),
				100 * change(rotaryPeak(PrecisionParts.INJECTOR | PrecisionParts.LOW_COMPRESSION, boost), stock)));
		}
		double none = peak(FORGED, FuelSpec.GASOLINE, 0);
		for (int rings = 1; rings <= 3; rings++)
			table.append(String.format(Locale.ROOT, "%d ring(s): %+.1f%%%n", rings,
				100 * change(peak(PrecisionParts.withRings(FORGED, rings), FuelSpec.GASOLINE, 0), none)));
		System.out.println(table);
		try {
			java.nio.file.Files.createDirectories(java.nio.file.Path.of("build/art"));
			java.nio.file.Files.writeString(java.nio.file.Path.of("build/art/table.txt"), table);
		} catch (java.io.IOException e) {
			throw new java.io.UncheckedIOException(e);
		}
	}
}
