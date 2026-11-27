package com.createengines.engine.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/** The second part of the wear mode (2.4): heat, load, the three connecting rods, and what a worn plug costs. */
class WearRunningHotTest {

	private static final double EPS = 1e-12;
	private static final double RPM = Wear.REFERENCE_RPM;
	private static final double[] NONSENSE = {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY};

	@Test
	void heatWearsAsItAlwaysDidUpToHotAndFarFasterPastIt() {
		assertEquals(1, Wear.heatFactor(0), EPS);
		assertEquals(1, Wear.heatFactor(Thermal.NORMAL), EPS);
		assertEquals(1 + 2.5 * (Wear.HOT - Thermal.NORMAL), Wear.heatFactor(Wear.HOT), EPS);
		double gentle = Wear.heatFactor(Wear.HOT) - Wear.heatFactor(Wear.HOT - 0.1);
		double steep = Wear.heatFactor(Wear.HOT + 0.1) - Wear.heatFactor(Wear.HOT);
		assertEquals(gentle + Wear.HOT_RISE * 0.1, steep, 1e-9);
	}

	@Test
	void aRodFeelsHeatAndLoadAndAWarmHalfLoadedOneWearsAsItDidBefore() {
		double old = Wear.speedFactor(RPM) / Wear.ROD_LIFE;
		assertEquals(old, Wear.rodWear(RPM, 1, 1), 1e-15);
		assertTrue(Wear.rodWear(RPM, Wear.HOT + 0.2, 1, 0.5, 1) > 2 * old, "hot");
		assertTrue(Wear.rodWear(RPM, Thermal.NORMAL, 1, 1, 1) > old, "lugging");
		assertTrue(Wear.rodWear(RPM, Thermal.NORMAL, 1, 0, 1) < old, "spinning free");
		assertEquals(Wear.LOAD_FULL * old, Wear.rodWear(RPM, Thermal.NORMAL, 1, 7, 1), 1e-15, "load is held at all there is");
		for (double bad : NONSENSE)
			assertEquals(old, Wear.rodWear(RPM, Thermal.NORMAL, 1, bad, 1), 1e-15, "a load that is no number is half");
	}

	@Test
	void aLightRodWearsFastUnderLoadAndAHeavyOneTakesIt() {
		double light = Wear.rodWear(Wear.Rod.LIGHT, RPM, Thermal.NORMAL, 1, 1, 100);
		double standard = Wear.rodWear(Wear.Rod.STANDARD, RPM, Thermal.NORMAL, 1, 1, 100);
		double heavy = Wear.rodWear(Wear.Rod.HEAVY, RPM, Thermal.NORMAL, 1, 1, 100);
		assertTrue(light > standard && standard > heavy, light + " " + standard + " " + heavy);
		assertEquals(Wear.Rod.STANDARD, Wear.Rod.byId(0));
		assertEquals(Wear.Rod.LIGHT, Wear.Rod.byId(1));
		assertEquals(Wear.Rod.HEAVY, Wear.Rod.byId(2));
		assertEquals(Wear.Rod.STANDARD, Wear.Rod.byId(-1));
		assertEquals(Wear.Rod.STANDARD, Wear.Rod.byId(3));
	}

	@Test
	void aStandardRodASoundPlugAndNoGasketLeaveTheStateExactlyAsItWas() {
		assertSame(Wear.NONE, Wear.withRod(Wear.NONE, Wear.Rod.STANDARD));
		assertSame(Wear.NONE, Wear.withPlug(Wear.NONE, 0));
		assertSame(Wear.NONE, Wear.withPlug(Wear.NONE, Double.NaN));
	}

	@Test
	void theStatesStackAndEachKeepsTheOthers() {
		Wear.State worn = Wear.of(0.5, Wear.Gasket.RUBBER, 0.2, true);
		Wear.State all = Wear.warped(Wear.withPlug(Wear.withRod(worn, Wear.Rod.HEAVY), 0.8));
		assertEquals(worn.seal() * (1 - Wear.WARP_SEAL_LOSS), all.seal(), EPS);
		assertEquals(worn.drag(), all.drag(), EPS);
		assertEquals(Wear.Rod.HEAVY.viscous, all.viscous(), EPS);
		assertEquals(PlugWear.misfireChance(0.8), all.misfire(), EPS);
		assertEquals(PlugWear.leanScale(0.8), all.lean(), EPS);
	}

	/** A rod changes only the friction that grows with speed, so its share of the difference rises with speed and is nothing standing. */
	@Test
	void aRodChangesOnlyTheFrictionThatGrowsWithSpeed() {
		CylinderModel standard = model(Wear.NONE, 0);
		CylinderModel light = model(Wear.withRod(Wear.NONE, Wear.Rod.LIGHT), 0);
		CylinderModel heavy = model(Wear.withRod(Wear.NONE, Wear.Rod.HEAVY), 0);
		assertEquals(standard.frictionStatic(), light.frictionStatic(), EPS);
		assertEquals(standard.frictionStatic(), heavy.frictionStatic(), EPS);
		assertEquals(standard.frictionAt(0, 1), light.frictionAt(0, 1), EPS);
		double slow = standard.frictionAt(5, 1) - light.frictionAt(5, 1);
		double fast = standard.frictionAt(50, 1) - light.frictionAt(50, 1);
		assertTrue(slow > 0 && fast > slow, "a light rod saves more the faster it goes");
		assertEquals(standard.frictionViscous() * Wear.Rod.HEAVY.viscous, heavy.frictionViscous(), EPS);
		assertTrue(heavy.frictionAt(50, 1) > standard.frictionAt(50, 1));
	}

	@Test
	void onTheDynoALightRodIsWorthALittleAtSpeedAndAHeavyOneCostsALittle() {
		double[] light = curve(Wear.withRod(Wear.NONE, Wear.Rod.LIGHT));
		double[] standard = curve(Wear.NONE);
		double[] heavy = curve(Wear.withRod(Wear.NONE, Wear.Rod.HEAVY));
		double gain = 0;
		double peak = java.util.Arrays.stream(standard)
			.max()
			.orElseThrow();
		for (int i = 0; i < standard.length; i++) {
			if (!(standard[i] > 0))
				continue;
			assertTrue(light[i] >= standard[i] && heavy[i] <= standard[i], "point " + i);
			// Only where the engine is making real torque: near the top, where it runs out, any share of nearly nothing is large
			if (standard[i] >= peak / 2)
				gain = Math.max(gain, light[i] / standard[i] - 1);
		}
		assertTrue(gain > 0.001 && gain < 0.05, "a little, and not a new engine: " + gain);
	}

	@Test
	void aHeavyRodAddsMassToTheCrankAndALightOneTakesItAway() {
		double standard = model(Wear.NONE, 0).inertia();
		assertEquals(CylinderSpec.CRUDE.inertia(), standard, 0, "a standard rod is exactly what it always was");
		assertEquals(standard * 0.7, model(Wear.withRod(Wear.NONE, Wear.Rod.LIGHT), 0).inertia(), EPS);
		assertEquals(standard * 1.5, model(Wear.withRod(Wear.NONE, Wear.Rod.HEAVY), 0).inertia(), EPS);
	}

	/** A worn plug and a warped head are laid over the rod, and a copy for the dyno keeps its mass. */
	@Test
	void aRodsMassSurvivesOtherWearAndTheDynosCopy() {
		Wear.State all = Wear.warped(Wear.withPlug(Wear.withRod(Wear.NONE, Wear.Rod.HEAVY), 0.8));
		assertEquals(Wear.Rod.HEAVY.mass, all.mass(), EPS);
		CylinderModel heavy = model(all, 0);
		assertEquals(heavy.inertia(), heavy.copyOnto(new Camshaft())
			.inertia(), 0);
	}

	/** With the wear mode off a heavy rod is a trade, not only a cost: its engine surges less, and a light one's more. */
	@Test
	void aHeavyRodRunsSmootherAndALightOneRougher() {
		assertTrue(crank(Wear.Rod.HEAVY).inertia() > crank(Wear.Rod.STANDARD).inertia());
		double light = ripple(Wear.Rod.LIGHT);
		double standard = ripple(Wear.Rod.STANDARD);
		double heavy = ripple(Wear.Rod.HEAVY);
		assertTrue(heavy < standard && standard < light, heavy + " " + standard + " " + light);
		assertTrue(Roughness.smoothness(heavy) >= Roughness.smoothness(standard));
	}

	/** A single cylinder on a bare crank with this rod, run at 128 RPM until its speed swing is measured. */
	private static EngineSimulation crank(Wear.Rod rod) {
		CylinderModel model = model(Wear.withRod(Wear.NONE, rod), 0);
		model.fuelSupply = MixtureTest.unlimited(FuelSpec.GASOLINE);
		model.ignition = IgnitionSource.UNLIMITED;
		EngineSimulation sim = new EngineSimulation(List.of(model), 0.35, 0.03);
		model.resetGas(0);
		return sim;
	}

	private static double ripple(Wear.Rod rod) {
		EngineSimulation sim = crank(rod);
		sim.trackSwing();
		for (int i = 0; i < 200; i++)
			sim.tickDriven(EngineSimulation.rpmToOmega(128), 1);
		return sim.predictedRipple();
	}

	@Test
	void aWornPlugMissesItsShareOfSparksEvenlyAndASoundOneNone() {
		CylinderModel model = model(Wear.NONE, 0);
		int missed = 0;
		for (int i = 0; i < 1000; i++)
			missed += model.missesSpark(0.25) ? 1 : 0;
		assertEquals(250, missed, 1);
		for (int i = 0; i < 100; i++)
			assertFalse(model.missesSpark(0));
	}

	@Test
	void aSoundGasketOnlyBlowsFromHeatPastCritical() {
		assertEquals(0, Wear.heatBlowChance(Math.nextDown(Wear.CRITICAL), 100), 0);
		double at = Wear.heatBlowChance(Wear.CRITICAL, 1);
		assertEquals(Wear.HEAT_BLOW_RATE, at, EPS);
		assertEquals(2 * at, Wear.heatBlowChance(Wear.CRITICAL + Wear.CRITICAL_SPAN, 1), EPS);
		assertEquals(2 * at, Wear.heatBlowChance(100, 1), EPS);
		assertEquals(1, Wear.heatBlowChance(100, 1e12), 0);
		assertEquals(0, Wear.heatBlowChance(2, -1), 0);
		for (double bad : NONSENSE) {
			assertEquals(0, Wear.heatBlowChance(bad, 1), 0);
			assertEquals(0, Wear.heatBlowChance(2, bad), 0);
		}
	}

	@Test
	void aHeadWarpsOnlyAfterSoakingPastCriticalAndTheSoakFallsAwayBelowIt() {
		double soak = 0;
		for (int i = 0; i < Wear.WARP_AFTER - 1; i++)
			soak = Wear.heatSoak(soak, Wear.CRITICAL, 1);
		assertFalse(Wear.warps(soak));
		assertTrue(Wear.warps(Wear.heatSoak(soak, Wear.CRITICAL, 1)));
		assertEquals(10 - 3, Wear.heatSoak(10, Wear.CRITICAL - 0.01, 3), EPS);
		assertEquals(0, Wear.heatSoak(2, 0, 100), 0);
		assertEquals(20, Wear.heatSoak(0, Wear.CRITICAL + Wear.CRITICAL_SPAN, 10), EPS, "twice as fast at the far end");
		assertEquals(Wear.WARP_AFTER, Wear.heatSoak(0, 5, 1e12), 0);
		assertEquals(7, Wear.heatSoak(7, 2, Double.NaN), 0);
		assertEquals(7, Wear.heatSoak(7, 2, -4), EPS);
		assertEquals(0, Wear.heatSoak(Double.NaN, 0, 1), 0);
	}

	@Test
	void anOiledPistonSeizesFromHeatAloneAtHalfThePaceOfADryOne() {
		assertEquals(0, Wear.dryTime(0, 1, Math.nextDown(Wear.CRITICAL), RPM, 10, false), 0);
		assertEquals(10 * Wear.HEAT_SEIZE_SHARE, Wear.dryTime(0, 1, Wear.CRITICAL, RPM, 10, false), EPS);
		assertEquals(10 * (1 + Wear.HEAT_SEIZE_SHARE), Wear.dryTime(0, 0, Wear.CRITICAL, RPM, 10, false), EPS, "dry and hot together");
		assertEquals(10 * Wear.HEAT_SEIZE_SHARE / Wear.PRECISION_LIFE, Wear.dryTime(0, 1, Wear.CRITICAL, RPM, 10, true), EPS);
		assertEquals(Wear.dryTime(5, 1, RPM, 1, false), Wear.dryTime(5, 1, Double.NaN, RPM, 1, false), 0, "a heat that is no number is warm");
	}

	@Test
	void aWornPartFailsAsItDidInShapeButIsAWarningAndNotACertainty() {
		// The 2.4 retune: at 0.8 wear and 256 RPM a warm part was certain to fail within a minute before
		double minute = Wear.failureChance(0.8, 256, 60);
		assertTrue(minute > 0.02 && minute < 0.15, "" + minute);
		assertEquals(minute, Wear.failureChance(0.8, 256, Wear.HOT, 60), EPS, "no worse until past hot");
		assertEquals(3 * minute, Wear.failureChance(0.8, 256, Wear.CRITICAL, 60), 1e-9);
		assertEquals(6 * minute, Wear.failureChance(0.8, 256, Wear.CRITICAL + Wear.CRITICAL_SPAN, 60), 1e-9);
		assertEquals(Wear.failureChance(0.8, Wear.SPEED_CAP * RPM, 60), Wear.failureChance(0.8, 1e9, 60), 0, "speed is capped");
		for (double bad : NONSENSE)
			assertEquals(Wear.failureChance(0.8, 256, 60), Wear.failureChance(0.8, 256, bad, 60), 0);
	}

	@Test
	void aWornTurboMakesLessBoostAndAFailedOneNone() {
		Turbocharger turbo = new Turbocharger(List.of(), List.of(), 1);
		turbo.output = TurboWear.boostShare(1);
		Turbocharger copy = turbo.copyOnto(java.util.Map.of());
		assertEquals(turbo.output, copy.output, 0, "the dyno's copy is as worn");
	}

	/** A rod's difference is exactly its share of the friction that grows with speed, on this oil film and at this speed, and no more. */
	@Test
	void aRodsDifferenceIsExactlyItsShareOfTheSpeedFriction() {
		CylinderModel standard = model(Wear.NONE, 0);
		for (Wear.Rod rod : new Wear.Rod[] {Wear.Rod.LIGHT, Wear.Rod.HEAVY})
			for (double omega : new double[] {3, 40})
				for (double film : new double[] {0.3, 1}) {
					double expected = (rod.viscous - 1) * CylinderSpec.CRUDE.frictionViscous() * omega * Lubrication.viscousFactor(film);
					assertEquals(expected, model(Wear.withRod(Wear.NONE, rod), 0).frictionAt(omega, film) - standard.frictionAt(omega, film), 1e-9,
						rod + " at " + omega + " on " + film);
				}
	}

	@Test
	void aPlugMissesOnTheSparkThatMakesUpAWholeMiss() {
		CylinderModel model = model(Wear.NONE, 0);
		for (int round = 0; round < 3; round++) {
			assertFalse(model.missesSpark(0.25));
			assertFalse(model.missesSpark(0.25));
			assertFalse(model.missesSpark(0.25));
			assertTrue(model.missesSpark(0.25), "four quarters are one whole, in round " + round);
		}
	}

	@Test
	void aPlugThatMissesEverySparkLeavesNothingBurningAndTheLeanLimitIsTheWornPlugsTimesTheSoundOnes() {
		double sound = peak(plug(0, 1));
		assertTrue(sound > 0, "the engine runs: " + sound);
		assertTrue(peak(plug(1, 1)) < 0, "every spark missed: it only drags");
		// A limit a hundred times as high is one no mixture reaches, and a hundredth of it is one every mixture passes
		assertTrue(peak(plug(0, 100)) < 0, "nothing it is given is rich enough to light");
		assertEquals(sound, peak(plug(0, 0.01)), 1e-9, "and a lower limit than the sound one changes nothing here");
	}

	@Test
	void aSoundStateReadsASoundPlugAndAFittedGasketToo() {
		for (Wear.State state : new Wear.State[] {Wear.NONE, Wear.fitted(Wear.Gasket.COPPER), Wear.of(0.5, Wear.Gasket.STOCK, 0.5)}) {
			assertEquals(1, state.lean(), 0);
			assertEquals(0, state.misfire(), 0);
			assertEquals(1, state.viscous(), 0);
		}
		assertEquals(Wear.LOAD_FREE, Wear.loadFactor(0), EPS);
		assertEquals(1, Wear.loadFactor(Wear.REFERENCE_LOAD), EPS);
		assertEquals(Wear.LOAD_FULL, Wear.loadFactor(1), EPS);
	}

	/** A plug state that misses this share of its sparks and multiplies the leanest mixture it lights by this much, on a sound cylinder. */
	private static Wear.State plug(double misfire, double lean) {
		return new Wear.State() {
			@Override
			public double seal() {
				return 1;
			}

			@Override
			public double drag() {
				return 1;
			}

			@Override
			public double misfire() {
				return misfire;
			}

			@Override
			public double lean() {
				return lean;
			}
		};
	}

	private static double peak(Wear.State wear) {
		return java.util.Arrays.stream(curve(wear))
			.max()
			.orElseThrow();
	}

	@Test
	void theLaterFailureKindsPackAboveTheFirstFourWhichReadAsTheyDid() {
		long packed = WearFailures.pack(1, 2, 3, 127, 5, 6);
		for (WearFailures.Kind kind : WearFailures.Kind.values())
			assertEquals(new int[] {1, 2, 3, 127, 5, 6}[kind.ordinal()], WearFailures.count(packed, kind), kind.name());
		// A figure saved when this was an int: the same low four bytes
		assertEquals(1 | 2 << 8 | 3 << 16 | 4 << 24, WearFailures.pack(1, 2, 3, 4));
	}

	private static CylinderModel model(Wear.State wear, double fire) {
		CylinderModel model = new CylinderModel(CylinderSpec.CRUDE, CamProfile.BASIC, fire % 360, new Camshaft(), fire / 2);
		model.wear = wear;
		return model;
	}

	private static double[] curve(Wear.State wear) {
		Camshaft cam = new Camshaft();
		List<CylinderModel> models = new ArrayList<>();
		for (int i = 0; i < 4; i++) {
			double fire = i * 180;
			CylinderModel model = new CylinderModel(CylinderSpec.CRUDE, CamProfile.BASIC, fire % 360, cam, fire / 2);
			model.fuelSupply = MixtureTest.unlimited(FuelSpec.GASOLINE);
			model.ignition = IgnitionSource.UNLIMITED;
			model.wear = wear;
			models.add(model);
		}
		EngineSimulation sim = new EngineSimulation(models, models.size() * 0.35 + 1, (models.size() * 2 + 1) * 0.01);
		for (CylinderModel model : models)
			model.resetGas(0);
		return Dynamometer.torqueCurve(sim);
	}
}
