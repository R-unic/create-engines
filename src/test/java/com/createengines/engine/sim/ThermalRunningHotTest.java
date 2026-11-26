package com.createengines.engine.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Degrees and bands, heat that follows the fuel burned, the heat of a dry engine, and what overheating costs (2.4). */
class ThermalRunningHotTest {

	private static final double EPS = 1e-12;

	@AfterEach
	void restore() {
		Tuning.overheatPenalty = 1;
		Tuning.engineTemperature = true;
	}

	@Test
	void aGaugeReadsAmbientAsTwentyDegreesAndWarmAsNinety() {
		assertEquals(20, Thermal.degrees(0));
		assertEquals(90, Thermal.degrees(Thermal.NORMAL));
		assertEquals(120, Thermal.degrees(Thermal.HOT));
		assertEquals(140, Thermal.degrees(Thermal.OVERHEATING));
		assertEquals(165, Thermal.degrees(Thermal.CRITICAL));
		assertEquals(20, Thermal.degrees(-3));
		assertEquals(20, Thermal.degrees(Double.NaN));
		assertEquals(20, Thermal.degrees(Double.POSITIVE_INFINITY));
	}

	@Test
	void eachBandStartsAtItsOwnTemperature() {
		double[] starts = {Thermal.COLD_BELOW, Thermal.COOL_NONE, Thermal.HOT, Thermal.OVERHEATING, Thermal.CRITICAL};
		Thermal.Band[] bands = Thermal.Band.values();
		assertEquals(Thermal.Band.COLD, Thermal.band(0));
		for (int i = 0; i < starts.length; i++) {
			assertEquals(bands[i], Thermal.band(Math.nextDown(starts[i])), "just under " + starts[i]);
			assertEquals(bands[i + 1], Thermal.band(starts[i]), "at " + starts[i]);
		}
		assertEquals(Wear.HOT, Thermal.HOT, 0);
		assertEquals(Wear.CRITICAL, Thermal.CRITICAL, 0);
	}

	@Test
	void overheatingRisesFromNothingToAllOfItBetweenTheTwoBandsAndNoFurther() {
		assertEquals(0, new Thermal(Thermal.OVERHEATING).overheat(), 0);
		assertEquals(0, new Thermal(0.7).overheat(), 0);
		assertEquals(0.5, new Thermal((Thermal.OVERHEATING + Thermal.CRITICAL) / 2).overheat(), 1e-9);
		assertEquals(1, new Thermal(Thermal.CRITICAL).overheat(), EPS);
		assertEquals(1, new Thermal(9).overheat(), 0);
		Tuning.overheatPenalty = 0.5;
		assertEquals(0.5, new Thermal(9).hotness(), EPS);
		Tuning.overheatPenalty = 0;
		assertEquals(0, new Thermal(9).hotness(), 0);
		Tuning.overheatPenalty = 1;
		Tuning.engineTemperature = false;
		assertEquals(0, new Thermal(9).hotness(), 0);
	}

	@Test
	void anOverheatingEngineBreathesAsItDid() {
		assertEquals(1, new Thermal(9).chargeFactor(), 0);
	}

	@Test
	void anEngineBelowOverheatingMakesExactlyWhatItAlwaysDid() {
		assertEquals(peak(FuelSpec.DIESEL, 0.9), peak(FuelSpec.DIESEL, Thermal.OVERHEATING), 0);
	}

	@Test
	void aCriticalEngineLosesNearAQuarterOfItsPeakAndNeverMoreWhateverItBurns() {
		for (FuelSpec fuel : new FuelSpec[] {FuelSpec.DIESEL, FuelSpec.GASOLINE, FuelSpec.ETHANOL, FuelSpec.PLANT_OIL}) {
			double sound = peak(fuel, Thermal.OVERHEATING);
			double half = 1 - peak(fuel, (Thermal.OVERHEATING + Thermal.CRITICAL) / 2) / sound;
			double critical = 1 - peak(fuel, Thermal.CRITICAL) / sound;
			assertTrue(critical > 0.18 && critical <= 0.25, fuel + " at critical: " + critical);
			assertTrue(half > 0.07 && half < critical, fuel + " half way: " + half);
			assertEquals(critical, 1 - peak(fuel, 2.5) / sound, 1e-9, "no worse past critical");
		}
	}

	@Test
	void withThePenaltyOffAHotEngineLosesNothing() {
		double sound = peak(FuelSpec.GASOLINE, Thermal.OVERHEATING);
		Tuning.overheatPenalty = 0;
		assertEquals(sound, peak(FuelSpec.GASOLINE, 2), 0);
	}

	@Test
	void fuelHeatCountsByItsHeatFactorAndANonsenseOneReadsAsUsual() {
		double usual = settle(FuelSpec.DIESEL, 1);
		assertTrue(settle(FuelSpec.DIESEL.withHeatFactor(2), 1) > usual + 0.1, "a hotter fuel runs hotter");
		assertTrue(settle(FuelSpec.DIESEL.withHeatFactor(0.25), 1) < usual - 0.05, "a cooler one cooler");
		assertEquals(1, FuelSpec.DIESEL.withHeatFactor(Double.NaN)
			.usable()
			.heatFactor(), 0);
		assertEquals(0, FuelSpec.DIESEL.withHeatFactor(-2)
			.usable()
			.heatFactor(), 0);
		assertEquals(FuelSpec.MAX_HEAT_FACTOR, FuelSpec.DIESEL.withHeatFactor(1e9)
			.usable()
			.heatFactor(), 0);
		assertTrue(FuelSpec.DIESEL.usable() == FuelSpec.DIESEL, "a sound fuel is left as it is");
	}

	@Test
	void moreFuelMakesMoreHeat() {
		assertTrue(settle(FuelSpec.DIESEL, 1) > settle(FuelSpec.DIESEL, 0.35) + 0.1);
	}

	@Test
	void aDryEngineMakesFarMoreFrictionHeatAndAnOiledOneNoneExtra() {
		assertEquals(1, Thermal.dryHeat(1), 0);
		assertEquals(1, Thermal.dryHeat(Wear.SEIZE_FILM), 0);
		assertEquals(1 + Thermal.DRY_HEAT / 2, Thermal.dryHeat(Wear.SEIZE_FILM / 2), 1e-9);
		assertEquals(1 + Thermal.DRY_HEAT, Thermal.dryHeat(0), EPS);
		assertEquals(1 + Thermal.DRY_HEAT, Thermal.dryHeat(-1), EPS);
		assertEquals(1, Thermal.dryHeat(Double.NaN), 0);
	}

	@Test
	void eachBandHasItsOwnGogglesLine() {
		for (Thermal.Band band : Thermal.Band.values())
			assertEquals("goggles.temperature." + band.name()
				.toLowerCase(java.util.Locale.ROOT), band.langKey());
	}

	@Test
	void aPackThatSetsANegativeHeatFactorIsToldSo() {
		assertEquals(List.of(), FuelCheck.problems(FuelSpec.DIESEL));
		List<String> problems = FuelCheck.problems(FuelSpec.DIESEL.withHeatFactor(-1));
		assertEquals(1, problems.size(), "" + problems);
		assertTrue(problems.get(0)
			.contains("heat_factor"), "" + problems);
	}

	/** One tick of a cold block with a heat source against it, to the last digit: every term of the heat going in and out. */
	@Test
	void aTickOfAColdPreheatedBlockMovesItByExactlyWhatGoesInAndOut() {
		Thermal thermal = new Thermal(0.15);
		thermal.setPreheat(HeatRecovery.SEETHE);
		double preheat = thermal.preheat();
		assertTrue(preheat > 0);
		double heat = 3;
		int n = 4;
		double dt = 0.05;
		double light = 1 - heat / (n * Thermal.FULL_LOAD_HEAT);
		double in = heat * Thermal.HEAT_SHARE * (1 + Thermal.WARMUP_ASSIST * thermal.cold() * light) + preheat * n;
		double expected = 0.15 + (in - Thermal.PASSIVE * n * 0.15 * 0.15) * dt / (Thermal.CAPACITY * n);
		thermal.update(heat, n, dt, CoolantSupply.NONE);
		assertEquals(expected, thermal.temperature(), 1e-15);
	}

	/** A source that is not a cylinder warms the block by its share of what leaves its exhaust, a second of it for each second. */
	@Test
	void aSourceThatIsNoCylinderStillWarmsTheBlockByItsExhaust() {
		Stove stove = new Stove();
		EngineSimulation sim = new EngineSimulation(four(FuelSpec.DIESEL, 0), 2.4, 0.09);
		sim.addSource(stove);
		sim.setThermalLive(true);
		sim.thermal()
			.setTemperature(0.5);
		Thermal expected = new Thermal(0.5);
		// The crank stands still, so there is no friction work and nothing burns: the stove's exhaust is all the heat there is
		expected.update(Thermal.heatIn(0, stove.heat / EngineSimulation.TICK, 0, 5), 5, EngineSimulation.TICK, CoolantSupply.NONE);
		sim.tickDriven(0, 1);
		assertEquals(expected.temperature(), sim.thermal()
			.temperature(), 1e-15);
		assertTrue(sim.thermal()
			.temperature() > 0.5, "and it warms it");
	}

	/** Burns nothing, turns nothing, and sends the same heat down its exhaust every tick. */
	private static final class Stove extends AbstractTorqueSource {
		final double heat = 5;

		@Override
		public double advance(double angle, double rotaryAngle, int direction, double delta, double dt) {
			return 0;
		}

		@Override
		public double availableWork() {
			return 0;
		}

		@Override
		public double exhaustHeatLastTick() {
			return heat;
		}
	}

	/** A boosted spark charge in an overheating block is read nearer its pre-ignition temperature, so it loses more to lighting early. */
	@Test
	void anOverheatingBlockBringsABoostedSparkChargeNearerLightingEarly() {
		double sound = early(0);
		double hot = early(1);
		assertTrue(hot > sound, "sound " + sound + ", hot " + hot);
	}

	/** The most of a burn any cylinder of a boosted gasoline four lost to lighting early at 192 RPM, its block this far into overheating. */
	private static double early(double hotness) {
		List<CylinderModel> models = four(FuelSpec.GASOLINE, 1);
		for (CylinderModel model : models)
			model.metering = 2;
		EngineSimulation sim = new EngineSimulation(models, 2.4, 0.09);
		Turbocharger turbo = new Turbocharger(models, models, 1);
		turbo.limit = Turbocharger.MAX_LIMIT;
		sim.addTurbo(turbo);
		sim.thermal()
			.setTemperature(Thermal.OVERHEATING + hotness * (Thermal.CRITICAL - Thermal.OVERHEATING));
		double most = 0;
		for (int i = 0; i < 1200; i++) {
			sim.tickDriven(EngineSimulation.rpmToOmega(192), 1);
			for (CylinderModel model : models)
				most = Math.max(most, model.lastCycle().preignition);
		}
		return most;
	}

	private static List<CylinderModel> four(FuelSpec fuel, double rack) {
		Camshaft cam = new Camshaft();
		List<CylinderModel> models = new ArrayList<>();
		for (int i = 0; i < 4; i++) {
			CylinderModel model = new CylinderModel(CylinderSpec.CRUDE, CamProfile.BASIC, (i * 180) % 360, cam, i * 90);
			model.fuelSupply = MixtureTest.unlimited(fuel);
			model.ignition = fuel.needsSpark() ? IgnitionSource.UNLIMITED : IgnitionSource.NONE;
			model.rack = rack;
			models.add(model);
		}
		for (CylinderModel model : models)
			model.resetGas(0);
		return models;
	}

	private static double peak(FuelSpec fuel, double temperature) {
		EngineSimulation sim = new EngineSimulation(four(fuel, 1), 2.4, 0.09);
		sim.thermal()
			.setTemperature(temperature);
		return java.util.Arrays.stream(Dynamometer.torqueCurve(sim))
			.max()
			.orElseThrow();
	}

	/** Where a four held at 128 RPM settles after five simulated minutes. */
	private static double settle(FuelSpec fuel, double rack) {
		EngineSimulation sim = new EngineSimulation(four(fuel, rack), 2.4, 0.09);
		sim.setThermalLive(true);
		for (int i = 0; i < 6000; i++)
			sim.tickDriven(EngineSimulation.rpmToOmega(128), 1);
		return sim.thermal()
			.temperature();
	}
}
