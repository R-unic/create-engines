package com.createengines.engine.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * The wear mode's rules. Wear is opt-in, so the first thing pinned is that a cylinder with none behaves exactly as it did before the mode
 * existed; the rest is the shape of the curves (monotonic, bounded, finite for any input) and the numbers the design promises.
 */
class WearTest {

	@AfterEach
	void resetTuning() {
		Tuning.reset();
	}

	@Test
	void theModeIsOffByDefault() {
		assertFalse(Tuning.wearMode);
	}

	@Test
	void aCylinderWithNoWearReadsAsSound() {
		assertEquals(1, Wear.NONE.seal());
		assertEquals(1, Wear.NONE.drag());
		assertEquals(1, Wear.pistonSeal(0));
		assertEquals(1, Wear.gasketSeal(Wear.Gasket.STOCK, 0));
		assertEquals(1, Wear.pistonDrag(0));
	}

	@Test
	void aModelWithNoWearStateKeepsTheNoneObject() {
		CylinderModel model = new CylinderModel(CylinderSpec.CRUDE, CamProfiles.basic(), 0, null, 0);
		assertSame(Wear.NONE, model.wear);
		assertSame(Wear.NONE, model.copyOnto(null).wear);
	}

	@Test
	void frictionIsUnchangedWithNoWearAndRisesWithIt() {
		CylinderModel model = new CylinderModel(CylinderSpec.CRUDE, CamProfiles.basic(), 0, null, 0);
		double plain = model.frictionAt(10, 1);
		assertEquals(CylinderSpec.CRUDE.frictionAt(10, 1), plain);
		model.wear = Wear.of(1, Wear.Gasket.STOCK, 0);
		assertTrue(model.frictionAt(10, 1) > plain);
		assertEquals(plain * (1 + Wear.PISTON_DRAG), model.frictionAt(10, 1), 1e-12);
	}

	@Test
	void wearGrowsWithSpeedHeatBoostAndNoOil() {
		double base = Wear.pistonWear(Wear.REFERENCE_RPM, Thermal.NORMAL, 1, 1);
		assertTrue(Wear.pistonWear(2 * Wear.REFERENCE_RPM, Thermal.NORMAL, 1, 1) > base, "speed");
		assertTrue(Wear.pistonWear(Wear.REFERENCE_RPM, Thermal.NORMAL + 0.3, 1, 1) > base, "heat");
		assertTrue(Wear.pistonWear(Wear.REFERENCE_RPM, Thermal.NORMAL, 0, 1) > base, "no oil");
		double gasket = Wear.gasketWear(Wear.Gasket.STOCK, Wear.REFERENCE_RPM, Thermal.NORMAL, 1, 1);
		assertTrue(Wear.gasketWear(Wear.Gasket.STOCK, Wear.REFERENCE_RPM, Thermal.NORMAL, 2, 1) > gasket, "boost");
		// A rod is hurt by speed and a dry engine, not by heat
		double rod = Wear.rodWear(Wear.REFERENCE_RPM, 1, 1);
		assertTrue(Wear.rodWear(2 * Wear.REFERENCE_RPM, 1, 1) > rod);
		assertTrue(Wear.rodWear(Wear.REFERENCE_RPM, 0, 1) > rod);
	}

	@Test
	void anEngineAtAReferenceSpeedWearsOutAPartInItsStatedLife() {
		assertEquals(1, Wear.pistonWear(Wear.REFERENCE_RPM, Thermal.NORMAL, 1, Wear.PISTON_LIFE), 1e-9);
		assertEquals(1, Wear.rodWear(Wear.REFERENCE_RPM, 1, Wear.ROD_LIFE), 1e-9);
		assertEquals(1, Wear.gasketWear(Wear.Gasket.STOCK, Wear.REFERENCE_RPM, Thermal.NORMAL, 1, Wear.GASKET_LIFE), 1e-9);
	}

	@Test
	void aStoppedOrIdlingEngineWearsLittleAndASlowOneStillWears() {
		assertEquals(0, Wear.pistonWear(0, Thermal.NORMAL, 1, 100));
		assertTrue(Wear.pistonWear(1, Thermal.NORMAL, 1, 100) > 0);
		assertTrue(Wear.pistonWear(10, Thermal.NORMAL, 1, 100) < Wear.pistonWear(Wear.REFERENCE_RPM, Thermal.NORMAL, 1, 100) / 10);
	}

	@Test
	void aRunawayEngineIsHardOnItsPartsButNotInstantlySpent() {
		double capped = Wear.speedFactor(Wear.SPEED_CAP * Wear.REFERENCE_RPM);
		assertEquals(capped, Wear.speedFactor(1e9));
		assertTrue(Wear.pistonWear(1e9, Thermal.NORMAL, 1, 1) < 1);
	}

	@Test
	void everyGasketTierOutlastsTheOneBelowIt() {
		Wear.Gasket[] tiers = Wear.Gasket.values();
		for (int i = 1; i < tiers.length; i++) {
			double lower = Wear.gasketWear(tiers[i - 1], 128, Thermal.NORMAL, 1.5, 1000);
			double higher = Wear.gasketWear(tiers[i], 128, Thermal.NORMAL, 1.5, 1000);
			assertTrue(higher < lower, tiers[i] + " should wear slower than " + tiers[i - 1]);
			assertTrue(Wear.gasketSeal(tiers[i], 1) >= Wear.gasketSeal(tiers[i - 1], 1), tiers[i] + " keeps its seal at least as well");
		}
	}

	@Test
	void aBetterGasketTakesBoostBetterAndAStockOneIsTheWeakest() {
		double boost = 2;
		assertTrue(Wear.boostFactor(boost, Wear.Gasket.COPPER) < Wear.boostFactor(boost, Wear.Gasket.CARDBOARD));
		assertTrue(Wear.boostFactor(boost, Wear.Gasket.CARDBOARD) < Wear.boostFactor(boost, Wear.Gasket.STOCK));
		// Unboosted air is the same for all of them
		for (Wear.Gasket gasket : Wear.Gasket.values())
			assertEquals(1, Wear.boostFactor(1, gasket));
	}

	@Test
	void unknownGasketNumbersAreStock() {
		assertSame(Wear.Gasket.STOCK, Wear.Gasket.byId(-1));
		assertSame(Wear.Gasket.STOCK, Wear.Gasket.byId(99));
		for (Wear.Gasket gasket : Wear.Gasket.values())
			assertSame(gasket, Wear.Gasket.byId(gasket.ordinal()));
	}

	@Test
	void effectsFallMonotonicallyAndStayBounded() {
		double sealBefore = 1;
		double dragBefore = 1;
		for (int i = 0; i <= 100; i++) {
			double w = i / 100d;
			double seal = Wear.pistonSeal(w) * Wear.gasketSeal(Wear.Gasket.STOCK, w);
			assertTrue(seal <= sealBefore + 1e-12, "seal never rises with wear");
			assertTrue(seal >= 1 - Wear.PISTON_SEAL_LOSS - Wear.GASKET_SEAL_LOSS - 1e-12, "and is bounded");
			double drag = Wear.pistonDrag(w);
			assertTrue(drag >= dragBefore - 1e-12 && drag <= 1 + Wear.PISTON_DRAG + 1e-12);
			sealBefore = seal;
			dragBefore = drag;
		}
	}

	@Test
	void aPartThatIsNotWornEnoughNeverFails() {
		for (int i = 0; i <= 10; i++)
			assertEquals(0, Wear.failureChance(i * 0.05, 10000, 100), "at wear " + i * 0.05);
	}

	@Test
	void failureRisesWithWearAndWithSpeedAndIsAProbability() {
		double previous = 0;
		for (int i = 1; i <= 9; i++) {
			double w = Wear.FAILURE_FROM + 0.05 * i;
			double chance = Wear.failureChance(w, 128, 1);
			assertTrue(chance > previous, "rises with wear");
			assertTrue(chance <= 1);
			previous = chance;
		}
		assertTrue(Wear.failureChance(0.9, 256, 1) > Wear.failureChance(0.9, 128, 1));
		assertEquals(1, Wear.failureChance(1, 256, 1e9));
	}

	@Test
	void aPartThatIsNearlyNewIsNotAtRiskEvenAtTheRedline() {
		// Over an hour at the redline, a part under half worn has no chance at all
		assertEquals(0, Wear.failureChance(0.4, 256, 3600));
		// And a part just over it risks very little in a second
		assertTrue(Wear.failureChance(Wear.FAILURE_FROM + 0.01, 256, 1) < 1e-6);
	}

	@Test
	void aRepairItemOnlyHelpsLightWear() {
		assertTrue(Wear.repairable(Wear.REPAIRABLE));
		assertFalse(Wear.repairable(Wear.REPAIRABLE + 0.01), "heavy wear needs the part replaced");
		assertFalse(Wear.repairable(0), "nothing to repair on a new part");
		assertFalse(Wear.repairable(Wear.REPAIRED_TO), "or one already as good as a repair leaves it");
		assertEquals(Wear.REPAIRED_TO, Wear.repaired(0.3));
		assertEquals(0.8, Wear.repaired(0.8), "heavy wear is left as it is");
		for (int i = 0; i <= 100; i++)
			assertTrue(Wear.repaired(i / 100d) <= i / 100d + 1e-12, "a repair never makes it worse");
	}

	@Test
	void nonsenseInputsStayFiniteAndInRange() {
		double[] odd = { Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -1, 0, 1e300 };
		for (double x : odd) {
			for (double y : odd) {
				for (Wear.Gasket gasket : Wear.Gasket.values()) {
					double[] results = { Wear.pistonWear(x, y, x, y), Wear.rodWear(x, y, x), Wear.gasketWear(gasket, x, y, x, y),
						Wear.failureChance(x, y, x), Wear.pistonSeal(x), Wear.gasketSeal(gasket, x), Wear.pistonDrag(x),
						Wear.repaired(x), Wear.clamp(x), Wear.speedFactor(x), Wear.heatFactor(x), Wear.dryFactor(x),
						Wear.boostFactor(x, gasket) };
					for (double r : results)
						assertTrue(!Double.isNaN(r) && r >= 0, "no NaN or negative: " + r + " for " + x + ", " + y);
				}
			}
		}
	}

	@Test
	void theStateOfAWornCylinderCombinesBothPartsAndNeverBeatsASoundOne() {
		Wear.State worn = Wear.of(0.8, Wear.Gasket.STOCK, 0.6);
		assertTrue(worn.seal() < 1);
		assertTrue(worn.drag() > 1);
		assertEquals(Wear.pistonSeal(0.8) * Wear.gasketSeal(Wear.Gasket.STOCK, 0.6), worn.seal(), 1e-12);
		Wear.State sound = Wear.of(0, Wear.Gasket.COPPER, 0);
		assertEquals(1, sound.seal());
		assertEquals(1, sound.drag());
	}

	@Test
	void aPrecisionPistonLastsLongerThanAStandardOneUnderTheSameConditions() {
		double standard = Wear.pistonWear(128, 0.3, 1, 60);
		double precision = Wear.pistonWear(128, 0.3, 1, 60, true);
		assertTrue(precision > 0 && precision < standard);
		assertEquals(standard / Wear.PRECISION_LIFE, precision, 1e-12);
		assertEquals(standard, Wear.pistonWear(128, 0.3, 1, 60, false), 0);
	}

	@Test
	void aPrecisionPistonStillWearsOutAndStaysWithinBounds() {
		assertTrue(Wear.PRECISION_LIFE > 1);
		assertEquals(1, Wear.pistonWear(128, 0.3, 1, 1e12, true), 0);
		assertEquals(0, Wear.pistonWear(Double.NaN, 0.3, 1, 60, true), 0);
		assertEquals(0, Wear.pistonWear(128, 0.3, 1, Double.NaN, true), 0);
	}

	@Test
	void theWearModesFaultsAreAppendedAfterTheOnesThatAlreadyShipped() {
		var faults = com.createengines.engine.topology.CylinderFault.values();
		// Each is pinned to its place after SUBMERGED, the last fault that shipped before the wear mode: a saved status holds these as bits, so
		// the order is part of what an old world reads. A fault added later goes after the last one here, and gets its own line.
		int base = com.createengines.engine.topology.CylinderFault.SUBMERGED.ordinal();
		assertEquals(com.createengines.engine.topology.CylinderFault.SUBMERGED, faults[base]);
		assertEquals(com.createengines.engine.topology.CylinderFault.BLOWN_GASKET, faults[base + 1]);
		assertEquals(com.createengines.engine.topology.CylinderFault.THROWN_ROD, faults[base + 2]);
		assertEquals(com.createengines.engine.topology.CylinderFault.BROKEN_HEAD, faults[base + 3]);
		assertEquals(com.createengines.engine.topology.CylinderFault.SEIZED, faults[base + 4]);
		assertEquals(com.createengines.engine.topology.CylinderFault.APEX_FAILED, faults[base + 5]);
		assertEquals(base + 6, faults.length, "nothing else has been added after them without being pinned here");
		assertTrue(com.createengines.engine.topology.CylinderFault.disables(com.createengines.engine.topology.CylinderFault.SEIZED.bit()));
		assertTrue(com.createengines.engine.topology.CylinderFault.disables(com.createengines.engine.topology.CylinderFault.THROWN_ROD.bit()));
		assertTrue(com.createengines.engine.topology.CylinderFault.disables(com.createengines.engine.topology.CylinderFault.BROKEN_HEAD.bit()));
	}

	@Test
	void runningDryBuildsUpTimeAndOilTakesItBackTwiceAsFast() {
		double t = 0;
		for (int i = 0; i < 5; i++)
			t = Wear.dryTime(t, 0.05, 128, 1, false);
		assertEquals(5, t, 1e-12);
		t = Wear.dryTime(t, 0.9, 128, 1, false);
		assertEquals(5 - Wear.SEIZE_RECOVERY, t, 1e-12);
		assertEquals(0, Wear.dryTime(1, 0.9, 128, 100, false), 0, "never below nothing");
	}

	@Test
	void aPistonSeizesOnlyAfterTheFullTimeRunningDryAndNeverBeforeOrWithOil() {
		double t = 0;
		for (int i = 0; i < Wear.SEIZE_AFTER - 1; i++)
			t = Wear.dryTime(t, 0, 128, 1, false);
		assertFalse(Wear.seizes(t), "one second short");
		assertTrue(Wear.warns(0, t), "but it has cried out by then");
		t = Wear.dryTime(t, 0, 128, 1, false);
		assertTrue(Wear.seizes(t));
		assertEquals(Wear.SEIZE_AFTER, Wear.dryTime(t, 0, 128, 1000, false), 0, "and the count stops at the seizing point");
		double oiled = 0;
		for (int i = 0; i < 100000; i++)
			oiled = Wear.dryTime(oiled, 1, 128, 1, false);
		assertFalse(Wear.seizes(oiled));
	}

	@Test
	void aStoppedEngineNeitherSeizesNorRecoversAndNonsenseChangesNothing() {
		assertEquals(7, Wear.dryTime(7, 0, 0, 1, false), 0);
		assertEquals(7, Wear.dryTime(7, 0, -5, 1, false), 0);
		assertEquals(7, Wear.dryTime(7, 0, 128, Double.NaN, false), 0);
		assertEquals(7, Wear.dryTime(7, 0, 128, -1, false), 0);
		assertEquals(7, Wear.dryTime(7, 0, 128, Double.POSITIVE_INFINITY, false), 0);
		assertEquals(0, Wear.dryTime(Double.NaN, 0, 0, 1, false), 0);
		assertEquals(0, Wear.dryTime(-3, 0, 0, 1, false), 0);
		// A film that is not a number reads as well oiled, so nothing is seized on a bad reading
		assertEquals(0, Wear.dryTime(0, Double.NaN, 128, 1, false), 0);
	}

	@Test
	void aSeizedCylinderDragsHarderThanAnyOtherAndANotConnectedOneStillDragsNothing() {
		var seized = com.createengines.engine.topology.CylinderFault.SEIZED.bit();
		assertEquals(Wear.SEIZED_DRAG, com.createengines.engine.topology.CylinderFault.dragOnCrank(seized));
		assertTrue(Wear.SEIZED_DRAG > 1);
		assertEquals(0, com.createengines.engine.topology.CylinderFault.dragOnCrank(seized | com.createengines.engine.topology.CylinderFault.NOT_ON_THROW.bit()));
		assertEquals(1, com.createengines.engine.topology.CylinderFault.dragOnCrank(0));
	}

	@Test
	void onlyAFailedSetOfSealsGripsTheCrankAndItAddsNoWeight() {
		for (com.createengines.engine.topology.CylinderFault fault : com.createengines.engine.topology.CylinderFault.values()) {
			boolean apex = fault == com.createengines.engine.topology.CylinderFault.APEX_FAILED;
			assertEquals(apex ? ApexSeals.WRECK_GRIP : 1, com.createengines.engine.topology.CylinderFault.gripOnCrank(fault.bit()), fault.name());
		}
		assertEquals(1, com.createengines.engine.topology.CylinderFault.gripOnCrank(0));
		assertEquals(1, com.createengines.engine.topology.CylinderFault.dragOnCrank(com.createengines.engine.topology.CylinderFault.APEX_FAILED.bit()));
	}

	/** Ticks a lone rotor with nothing burning takes to stop from {@code rpm}, with {@code flywheel} more inertia and its friction gripped this much. */
	private static int ticksToStop(double rpm, double flywheel, double grip) {
		double inertia = 3 * CylinderSpec.CRUDE.inertia() + 0.55 + flywheel;
		double friction = 3 * CylinderSpec.CRUDE.frictionStatic() * grip + 0.03;
		EngineSimulation sim = new EngineSimulation(java.util.List.of(), inertia, friction, 3 * CylinderSpec.CRUDE.frictionViscous());
		sim.restore(0, EngineSimulation.rpmToOmega(rpm), 1);
		int ticks = 0;
		while (!sim.isStopped() && ticks < 10_000) {
			sim.tickFree(0, EngineSimulation.rpmToOmega(256));
			ticks++;
		}
		return ticks;
	}

	@Test
	void aWreckedRotorStopsInSecondsWhereAnUnfiredOneCoasts() {
		assertTrue(ticksToStop(256, 0, 1) > 20 * 20, "unfired, it coasts: " + ticksToStop(256, 0, 1));
		assertTrue(ticksToStop(256, 0, ApexSeals.WRECK_GRIP) <= 4 * 20, "wrecked: " + ticksToStop(256, 0, ApexSeals.WRECK_GRIP));
		assertTrue(ticksToStop(256, 2.5, ApexSeals.WRECK_GRIP) <= 12 * 20,
			"wrecked, behind a flywheel: " + ticksToStop(256, 2.5, ApexSeals.WRECK_GRIP));
	}

	@Test
	void everyFittedGasketSealsBetterThanNoneAndABetterTierSealsBetter() {
		double none = Wear.fittedSeal(Wear.Gasket.STOCK, 0) - Wear.Gasket.STOCK.bonus;
		assertEquals(1, none, 1e-12, "a head with none fitted is read as before");
		double last = 1;
		for (Wear.Gasket tier : Wear.Gasket.values()) {
			assertTrue(tier.bonus > 0, tier + " does something");
			double seal = Wear.fittedSeal(tier, 0);
			assertTrue(seal > last, tier + " seals better than the one before: " + seal + " vs " + last);
			last = seal;
		}
		assertEquals(Wear.Gasket.values()[Wear.Gasket.values().length - 1], Wear.Gasket.COPPER);
	}

	@Test
	void theSealBonusIsASmallGainAndFadesToNothingAsTheGasketWears() {
		for (Wear.Gasket tier : Wear.Gasket.values()) {
			assertTrue(Wear.fittedSeal(tier, 0) < 1.1, tier + ": a gasket is a small gain, not a boost");
			assertEquals(1, Wear.fittedSeal(tier, 1), 1e-12, tier + " spent seals no better than none");
			assertTrue(Wear.fittedSeal(tier, 0.5) < Wear.fittedSeal(tier, 0.1));
			assertEquals(Wear.fittedSeal(tier, 0), Wear.fittedSeal(tier, -3), 0, "wear below nothing is none");
			assertEquals(1, Wear.fittedSeal(tier, Double.POSITIVE_INFINITY), 1e-12);
		}
	}

	@Test
	void theModeOffStateOfAFittedGasketIsItsNewSealAndNothingElse() {
		for (Wear.Gasket tier : Wear.Gasket.values()) {
			assertEquals(Wear.fittedSeal(tier, 0), Wear.fitted(tier).seal(), 0);
			assertEquals(1, Wear.fitted(tier).drag(), 0);
			assertTrue(Wear.fitted(tier) != Wear.NONE, tier + " is not the no-wear object, which is only for a head with none fitted");
			assertSame(Wear.fitted(tier), Wear.fitted(tier), "one shared object per tier, not one per cylinder per rebuild");
		}
	}

	@Test
	void aGasketCountedAsFittedAddsItsSealAndOneNotCountedDoesNot() {
		Wear.State with = Wear.of(0, Wear.Gasket.COPPER, 0, true);
		Wear.State without = Wear.of(0, Wear.Gasket.COPPER, 0, false);
		assertEquals(1, without.seal(), 0, "the three-argument form is what it always was");
		assertEquals(Wear.fittedSeal(Wear.Gasket.COPPER, 0), with.seal(), 1e-12);
		assertEquals(Wear.of(0, Wear.Gasket.COPPER, 0).seal(), without.seal(), 0);
		// Worn, a fitted gasket still loses power to its wear, over and above losing its bonus
		assertTrue(Wear.of(0, Wear.Gasket.COPPER, 0.8, true).seal() < Wear.of(0, Wear.Gasket.COPPER, 0, true).seal());
		assertTrue(Wear.of(0, Wear.Gasket.COPPER, 1, true).seal() < 1, "a spent one costs power, as it did");
	}

	// --- Exact figures and edges, so a changed comparison or operator is caught ---

	@Test
	void aGasketWearsByTheFormulaItIsDocumentedBy() {
		// Copper lasts six times a stock gasket's three hours; at the reference speed, warm, an hour at 1 bar over ambient (3 / hold 3 = +1 on the boost factor) is
		// 3600 / (10800 * 6) * 2
		double expected = 3600.0 / (Wear.GASKET_LIFE * 6) * 2;
		assertEquals(expected, Wear.gasketWear(Wear.Gasket.COPPER, Wear.REFERENCE_RPM, Thermal.NORMAL, 2, 3600), 1e-12);
		assertEquals(3600.0 / Wear.GASKET_LIFE, Wear.gasketWear(Wear.Gasket.STOCK, Wear.REFERENCE_RPM, Thermal.NORMAL, 1, 3600), 1e-12);
		// With every factor away from 1 (a speed of 1.5 times the reference gives 2.25, 0.2 hot gives 1.5, 1 bar on copper gives 2), so a changed operator shows
		double all = 3600.0 / (Wear.GASKET_LIFE * 6) * 2.25 * 1.5 * 2;
		assertEquals(all, Wear.gasketWear(Wear.Gasket.COPPER, 1.5 * Wear.REFERENCE_RPM, Thermal.NORMAL + 0.2, 2, 3600), 1e-12);
		assertEquals(0, Wear.gasketWear(Wear.Gasket.STOCK, Wear.REFERENCE_RPM, Thermal.NORMAL, 1, 0), 0);
		assertEquals(0, Wear.gasketWear(Wear.Gasket.STOCK, Wear.REFERENCE_RPM, Thermal.NORMAL, 1, -5), 0);
		assertEquals(0, Wear.gasketWear(Wear.Gasket.STOCK, Wear.REFERENCE_RPM, Thermal.NORMAL, 1, Double.NaN), 0);
	}

	@Test
	void aDryPistonCriesOutFromHalfWayAndCloserTogetherAfter() {
		double half = Wear.SEIZE_AFTER / 2;
		assertTrue(Wear.warns(half - 1, half), "on reaching half way exactly");
		assertFalse(Wear.warns(half, half + 1), "and not again for the same point");
		assertFalse(Wear.warns(0, half - 1e-9), "just short of it");
		assertFalse(Wear.warns(half + 1, half - 1), "not on the way back down");
		java.util.List<Double> at = new java.util.ArrayList<>();
		for (double t = 0; t < Wear.SEIZE_AFTER - 1; t++)
			if (Wear.warns(t, t + 1))
				at.add(t + 1);
		assertEquals(5, at.size(), "five cries before it seizes: " + at);
		for (int i = 2; i < at.size(); i++)
			assertTrue(at.get(i) - at.get(i - 1) < at.get(i - 1) - at.get(i - 2), "each sooner than the last: " + at);
	}

	@Test
	void aStandardPistonAtTheReferenceSpeedTakesTwoMinutesDry() {
		assertEquals(120, secondsToSeize(Wear.REFERENCE_RPM, false), 0);
	}

	@Test
	void aSlowerEngineTakesLongerToSeizeAndAFasterOneLessWithinLimits() {
		assertTrue(secondsToSeize(100, false) > secondsToSeize(128, false));
		assertTrue(secondsToSeize(192, false) < secondsToSeize(128, false));
		assertEquals(secondsToSeize(64, false), secondsToSeize(1, false), 0, "no slower than the slowest rate");
		assertEquals(secondsToSeize(256, false), secondsToSeize(4000, false), 0, "no faster than the fastest");
		assertEquals(240, secondsToSeize(32, false), 0);
		assertEquals(60, secondsToSeize(512, false), 0);
	}

	@Test
	void aForgedPistonRunsDryAsMuchLongerAsItWearsLonger() {
		for (double rpm : new double[] {32, 100, 128, 256})
			assertEquals(Wear.PRECISION_LIFE * secondsToSeize(rpm, false), secondsToSeize(rpm, true), 1, "at " + rpm);
		assertEquals(300, secondsToSeize(128, true), 0);
		// Oil clears a forged piston no slower than a standard one
		assertEquals(Wear.dryTime(50, 1, 128, 5, false), Wear.dryTime(50, 1, 128, 5, true), 0);
	}

	private static int secondsToSeize(double rpm, boolean precision) {
		double t = 0;
		int seconds = 0;
		while (!Wear.seizes(t) && seconds < 100000) {
			t = Wear.dryTime(t, 0, rpm, 1, precision);
			seconds++;
		}
		return seconds;
	}

	@Test
	void aPistonSeizesAtTheFullTimeExactlyAndNotBefore() {
		assertTrue(Wear.seizes(Wear.SEIZE_AFTER));
		assertFalse(Wear.seizes(Wear.SEIZE_AFTER - 1e-9));
		assertFalse(Wear.seizes(0));
	}

	@Test
	void anOilFilmExactlyAtTheLimitIsNotRunningDry() {
		// Below the figure adds time; at it and above it takes time off
		assertEquals(5 + 1, Wear.dryTime(5, Wear.SEIZE_FILM - 1e-9, 128, 1, false), 1e-9);
		assertEquals(5 - Wear.SEIZE_RECOVERY, Wear.dryTime(5, Wear.SEIZE_FILM, 128, 1, false), 1e-12);
		assertEquals(5 - Wear.SEIZE_RECOVERY, Wear.dryTime(5, Wear.SEIZE_FILM + 1e-9, 128, 1, false), 1e-6);
	}

	@Test
	void theDryCountHoldsItsRangeWhateverItStartsFrom() {
		assertEquals(0, Wear.dryTime(0, 1, 128, 1, false), 0, "from nothing, oiled, it stays at nothing");
		assertEquals(1, Wear.dryTime(0, 0, 128, 1, false), 0, "from nothing, dry, it gains the second");
		assertEquals(3, Wear.dryTime(3, 0, 128, 0, false), 0, "no time passing changes nothing");
		assertEquals(3, Wear.dryTime(3, 1, 128, 0, false), 0);
		assertEquals(0, Wear.dryTime(-3, 0, 0, 1, false), 0, "a count below nothing is nothing");
		assertEquals(0, Wear.dryTime(-3, 1, 128, 1, false), 0);
		assertEquals(Wear.SEIZE_AFTER, Wear.dryTime(Wear.SEIZE_AFTER + 50, 0, 128, 1, false), 0, "a count past the limit is held at it");
		assertEquals(3, Wear.dryTime(3, 0, 128, -4, false), 0, "a span of time below nothing is none");
		assertEquals(0, Wear.dryTime(Double.POSITIVE_INFINITY, 0, 128, 1, false), 1, "an infinite count is read as none and then gains the second");
		assertEquals(3 + Wear.SEIZE_SLOWEST, Wear.dryTime(3, 0, 1e-9, 1, false), 1e-12, "an engine barely turning still counts, at the slowest rate");
	}

	@Test
	void wearHasNoChanceOfFailingAtOrBelowTheThresholdAndRisesPastIt() {
		assertEquals(0, Wear.failureChance(Wear.FAILURE_FROM, 128, 60), 0);
		assertEquals(0, Wear.failureChance(Wear.FAILURE_FROM - 1e-9, 128, 60), 0);
		assertEquals(0, Wear.failureChance(0, 128, 60), 0);
		assertTrue(Wear.failureChance(Wear.FAILURE_FROM + 0.01, 128, 60) > 0);
		// The cube of how far past it, at the reference speed, per second: past = 0.5 at wear 0.75
		assertEquals(Wear.FAILURE_RATE * 0.125, Wear.failureChance(0.75, Wear.REFERENCE_RPM, 1), 1e-12);
		assertEquals(0, Wear.failureChance(0.9, 128, 0), 0, "no time passing is no chance");
		assertEquals(0, Wear.failureChance(0.9, 128, -3), 0, "a span of time below nothing is none");
		assertEquals(0, Wear.failureChance(0.9, 128, Double.NaN), 0);
		assertEquals(0, Wear.failureChance(0.9, 128, Double.POSITIVE_INFINITY), 0);
		assertEquals(1, Wear.failureChance(1, 4 * Wear.REFERENCE_RPM, 1e6), 0, "never above certain");
	}

	@Test
	void stepsOfWearAreBoundedAndATimeBelowNothingWearsNothing() {
		assertEquals(0, Wear.pistonWear(128, 0.3, 1, 0), 0);
		assertEquals(0, Wear.pistonWear(128, 0.3, 1, -10), 0);
		assertEquals(0, Wear.rodWear(128, 1, -10), 0);
		assertEquals(1, Wear.rodWear(128, 1, 1e12), 0);
		assertTrue(Wear.rodWear(128, 1, 1) > 0);
		assertEquals(0, Wear.rodWear(128, 1, Double.POSITIVE_INFINITY), 0);
	}

	@Test
	void clampKeepsWearInRangeAndReadsNonsenseAsNew() {
		assertEquals(0, Wear.clamp(0), 0);
		assertEquals(0.3, Wear.clamp(0.3), 0);
		assertEquals(1, Wear.clamp(1), 0);
		assertEquals(1, Wear.clamp(1.0000001), 0);
		assertEquals(0, Wear.clamp(-1e-9), 0);
		assertEquals(0, Wear.clamp(Double.NaN), 0);
		assertEquals(1, Wear.clamp(Double.POSITIVE_INFINITY), 0);
		assertEquals(0, Wear.clamp(Double.NEGATIVE_INFINITY), 0);
	}

	@Test
	void aSavedGasketNumberIsFoundAtBothEndsAndOutsideTheEndsIsStock() {
		assertEquals(Wear.Gasket.STOCK, Wear.Gasket.byId(0));
		assertEquals(Wear.Gasket.CARDBOARD, Wear.Gasket.byId(1));
		assertEquals(Wear.Gasket.COPPER, Wear.Gasket.byId(Wear.Gasket.values().length - 1));
		assertEquals(Wear.Gasket.STOCK, Wear.Gasket.byId(Wear.Gasket.values().length));
		assertEquals(Wear.Gasket.STOCK, Wear.Gasket.byId(-1));
		assertEquals(Wear.Gasket.STOCK, Wear.Gasket.byId(Integer.MAX_VALUE));
		assertEquals(Wear.Gasket.STOCK, Wear.Gasket.byId(Integer.MIN_VALUE));
	}
}
