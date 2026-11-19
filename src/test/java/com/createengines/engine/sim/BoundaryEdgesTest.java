package com.createengines.engine.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Verification track item 1: tests that sit on the exact edge of a comparison, which is where the mutation run found the
 * simulation's assertions did not bite (a flipped {@code <} for {@code <=} changes nothing unless a value lands exactly
 * on the line). Each test names the edge it pins. None changes behaviour; every expected value is what the code gave
 * before the test was written, read off its own documentation (a speed that "counts as stopped" at 2 RPM, a limit that
 * is "unusual" outside 5 to 200).
 */
class BoundaryEdgesTest {

	private static final double STALL = EngineSimulation.rpmToOmega(2);
	private static final double MAX_OMEGA = EngineSimulation.rpmToOmega(256);

	@AfterEach
	void resetTuning() {
		Tuning.reset();
	}

	private static EngineSimulation engine(int cylinderCount, Camshaft cam) {
		List<CylinderModel> cylinders = new ArrayList<>();
		for (int i = 0; i < cylinderCount; i++) {
			double tdc = i * 720.0 / cylinderCount;
			CylinderModel c = new CylinderModel(CylinderSpec.CRUDE, CamProfile.BASIC, tdc, cam, tdc / 2 % 180);
			c.fuelSupply = new FuelSupply() {
				@Override
				public double draw(double mb) {
					return mb;
				}

				@Override
				public FuelSpec fuel() {
					return FuelSpec.DIESEL;
				}
			};
			cylinders.add(c);
		}
		return new EngineSimulation(cylinders, 2, 0.02, 0.003);
	}

	// ---- EngineSimulation: the line between turning and stopped ------------------------------------------------------

	@Test
	void aCrankAtExactlyTheStallSpeedIsStopped() {
		EngineSimulation engine = engine(1, new Camshaft());
		engine.restore(0, STALL, 1);
		assertTrue(engine.isStopped(), "2 RPM counts as stopped");
		engine.restore(0, Math.nextUp(STALL), 1);
		assertFalse(engine.isStopped(), "anything faster is turning");
	}

	@Test
	void aDrivenCrankAtExactlyTheStallSpeedDoesNotMove() {
		EngineSimulation engine = engine(2, new Camshaft());
		engine.tickDriven(STALL, 1);
		assertEquals(0, engine.angle(), 0, "no steps are taken at the stall speed");
		engine.tickDriven(Math.nextUp(STALL), 1);
		assertTrue(engine.angle() > 0, "a hair above it, the crank turns");
	}

	@Test
	void aCrankedEngineAtExactlyTheStallSpeedTakesNoStepsAndTakesTheClutchsDirection() {
		EngineSimulation engine = engine(2, new Camshaft());
		engine.restore(0, STALL, 1);
		engine.tickCranked(0, MAX_OMEGA, 0, 1);
		assertEquals(0, engine.angle(), 0, "no steps are taken at the stall speed");
		EngineSimulation other = engine(2, new Camshaft());
		other.restore(0, STALL, 1);
		other.tickCranked(0, MAX_OMEGA, EngineSimulation.rpmToOmega(32), -1);
		assertEquals(-1, other.direction(), "a crank at the stall speed is as good as stopped: the starter picks its way round");
	}

	@Test
	void aStoppedCrankTakesTheStartersDirection() {
		EngineSimulation engine = engine(2, new Camshaft());
		engine.restore(0, 0, 1);
		engine.tickCranked(0, MAX_OMEGA, EngineSimulation.rpmToOmega(32), -1);
		assertEquals(-1, engine.direction());
	}

	@Test
	void aTurningCrankKeepsItsWayWhateverTheStarterSays() {
		EngineSimulation engine = engine(2, new Camshaft());
		engine.restore(0, EngineSimulation.rpmToOmega(64), 1);
		engine.tickCranked(0, MAX_OMEGA, EngineSimulation.rpmToOmega(32), -1);
		assertEquals(1, engine.direction());
	}

	@Test
	void theRestoredStateIsReadBack() {
		Camshaft cam = new Camshaft();
		EngineSimulation engine = engine(2, cam);
		engine.restore(100, EngineSimulation.rpmToOmega(80), 1);
		assertEquals(100, engine.angle(), 1e-12);
		assertEquals(50, cam.angle, 1e-12, "a cam turns at half the crank, so it is set to half the angle");
		assertEquals(0.003, engine.baseViscousFriction(), 0, "the friction this engine was built with");
		assertEquals(List.of(cam), engine.camshafts());
		assertEquals(0.02, engine.baseFriction(), 0);
		assertEquals(2, engine.baseInertia(), 0);
	}

	@Test
	void aPhaseLockedCamIsHeldAtHalfTheCrankAngleWhateverItWasLeftAt() {
		Camshaft cam = new Camshaft();
		EngineSimulation engine = engine(2, cam);
		cam.angle = 77;
		double omega = EngineSimulation.rpmToOmega(64);
		engine.tickDriven(omega, 1);
		double degrees = Math.toDegrees(omega * EngineSimulation.TICK);
		assertEquals(degrees, engine.angle(), 1e-9);
		assertEquals(degrees / 2, cam.angle, 1e-6, "locked to the crank, the cam is told where it is on every step");
	}

	@Test
	void aCamOffTheFourStrokeRatioTurnsByItsOwnRatioNotTheCranks() {
		Camshaft cam = new Camshaft();
		cam.ratio = 0.25;
		EngineSimulation engine = engine(2, cam);
		assertFalse(cam.isPhaseLocked());
		double omega = EngineSimulation.rpmToOmega(64);
		engine.tickDriven(omega, 1);
		double degrees = Math.toDegrees(omega * EngineSimulation.TICK);
		assertEquals(0.25 * degrees, cam.angle, 1e-6, "the cam advances by its ratio times each step of the crank");
	}

	@Test
	void aSwingPredictedAtTheStallSpeedIsNothing() {
		EngineSimulation engine = engine(4, new Camshaft());
		engine.trackSwing();
		double omega = EngineSimulation.rpmToOmega(120);
		for (int i = 0; i < 60; i++)
			engine.tickDriven(omega, 1);
		engine.restore(0, omega, 1);
		assertTrue(engine.predictedRipple() > 0, "a driven engine's swing is measured, so there is something to predict");
		engine.restore(0, STALL, 1);
		assertEquals(0, engine.predictedRipple(), 0, "a stopped crank has no ripple to predict");
	}

	@Test
	void withOilDilutionOffTheOilIsWashedCleanAtTheEndOfACycle() {
		Tuning.oilDilution = false;
		EngineSimulation engine = engine(4, new Camshaft());
		engine.lubrication().setDilution(0.3);
		for (int i = 0; i < 80; i++)
			engine.tickCranked(0, MAX_OMEGA, EngineSimulation.rpmToOmega(120), 1);
		assertEquals(0, engine.lubrication().dilution(), 0, "no dilution means none is carried");
	}

	// ---- SimMath ----------------------------------------------------------------------------------------------------

	@Test
	void aMarkTouchingTheStartOfAStepIsNotCrossedAndOneTouchingItsEndIs() {
		double edge = 1e-9;
		assertFalse(SimMath.crosses(0, 10, edge), "a mark within rounding of where the step began belongs to the step before");
		assertTrue(SimMath.crosses(0, 10, Math.nextUp(edge)));
		assertTrue(SimMath.crosses(0, 10, 10 + edge), "a mark within rounding of where the step ends belongs to this step");
		assertFalse(SimMath.crosses(0, 10, Math.nextUp(10 + edge) + 1e-6));
	}

	// ---- FuelCheck: the limits of "unusual" --------------------------------------------------------------------------

	@Test
	void aFuelOnTheEdgeOfTheUsualRangeIsNotCalledUnusual() {
		assertEquals(List.of(), FuelCheck.unusual(new FuelSpec(FuelCheck.UNUSUAL_ENERGY_LOW, 1.5, 1.7)));
		assertEquals(List.of(), FuelCheck.unusual(new FuelSpec(FuelCheck.UNUSUAL_ENERGY_HIGH, 1.5, 1.7)));
		assertEquals(List.of(), FuelCheck.unusual(new FuelSpec(36, 1.5, FuelCheck.UNUSUAL_AIR_LOW)));
		assertEquals(List.of(), FuelCheck.unusual(new FuelSpec(36, 1.5, FuelCheck.UNUSUAL_AIR_HIGH)));
		assertEquals(1, FuelCheck.unusual(new FuelSpec(Math.nextDown(FuelCheck.UNUSUAL_ENERGY_LOW), 1.5, 1.7)).size());
		assertEquals(1, FuelCheck.unusual(new FuelSpec(Math.nextUp(FuelCheck.UNUSUAL_ENERGY_HIGH), 1.5, 1.7)).size());
		assertEquals(1, FuelCheck.unusual(new FuelSpec(36, 1.5, Math.nextDown(FuelCheck.UNUSUAL_AIR_LOW))).size());
		assertEquals(1, FuelCheck.unusual(new FuelSpec(36, 1.5, Math.nextUp(FuelCheck.UNUSUAL_AIR_HIGH))).size());
	}

	@Test
	void aFuelWithNoEnergyOrNoAirIsWrongNotUnusual() {
		// Those are for problems() to say; unusual() is about numbers that are allowed but far from every shipped fuel
		assertEquals(List.of(), FuelCheck.unusual(new FuelSpec(0, 1.5, 1.7)));
		assertEquals(List.of(), FuelCheck.unusual(new FuelSpec(36, 1.5, 0)));
		assertEquals(List.of(), FuelCheck.unusual(new FuelSpec(-3, 1.5, -2)));
	}

	// ---- Small rules, each at its threshold --------------------------------------------------------------------------

	@Test
	void anExchangerShowsEachHeatLevelAtExactlyItsThreshold() {
		assertEquals(HeatRecovery.SEETHE, HeatRecovery.level(HeatRecovery.SEETHING, HeatRecovery.NONE));
		assertEquals(HeatRecovery.KINDLE, HeatRecovery.level(HeatRecovery.KINDLED, HeatRecovery.NONE));
		assertEquals(HeatRecovery.SMOULDER, HeatRecovery.level(HeatRecovery.SMOULDERING, HeatRecovery.NONE));
		assertEquals(HeatRecovery.KINDLE, HeatRecovery.level(Math.nextDown(HeatRecovery.SEETHING), HeatRecovery.NONE));
		assertEquals(HeatRecovery.SMOULDER, HeatRecovery.level(Math.nextDown(HeatRecovery.KINDLED), HeatRecovery.NONE));
		assertEquals(HeatRecovery.NONE, HeatRecovery.level(Math.nextDown(HeatRecovery.SMOULDERING), HeatRecovery.NONE));
	}

	@Test
	void aTintNumberJustPastTheLastTintIsAmberNotAnError() {
		assertSame(WindowTint.AMBER, WindowTint.byOrdinal(WindowTint.values().length));
		assertSame(WindowTint.AMBER, WindowTint.byOrdinal(-1));
		assertSame(WindowTint.values()[WindowTint.values().length - 1], WindowTint.byOrdinal(WindowTint.values().length - 1));
	}

	@Test
	void aTrapGetsNothingFromARunWithNoHeads() {
		assertEquals(0, SootCollector.share(10, 1, 0, 1f, 1), 0, "no heads on the engine's runs at all");
		assertEquals(0, SootCollector.share(10, 0, 3, 1f, 1), 0);
		assertEquals(0, SootCollector.share(10, 1, 3, 1f, 0), 0);
		assertEquals(10 / 3d, SootCollector.share(10, 1, 3, 1f, 1), 1e-12);
	}

	@Test
	void aColumnThatGivesOnePercentMoreIsSaidToGiveOnePercentMore() {
		assertEquals(ColumnAdvantage.MIN_PERCENT, ColumnAdvantage.percentMore(1.01, 1));
		assertEquals(0, ColumnAdvantage.percentMore(1.004, 1), "less than the smallest whole percent says nothing");
	}

	@Test
	void aPlateTakesNoFiguresFromACurveWithNoPower() {
		assertNull(PlateFigures.of(new float[] {0}, new float[] {5}), "torque with no speed is no power");
		assertNull(PlateFigures.of(new float[] {100}, new float[] {0}));
		assertNotNull(PlateFigures.of(new float[] {100}, new float[] {5}));
	}

	// ---- Thermal: coolant that carries no heat is never asked for ---------------------------------------------------

	@Test
	void coolantOfNoQualityIsNotDrawnOnHoweverHotTheBlock() {
		Thermal thermal = new Thermal(Thermal.THERMOSTAT + 0.3);
		int[] draws = {0};
		CoolantSupply none = new CoolantSupply() {
			@Override
			public double draw(double mb) {
				draws[0]++;
				return mb;
			}

			@Override
			public double quality() {
				return 0;
			}
		};
		thermal.update(100, 4, EngineSimulation.TICK, none);
		assertEquals(0, draws[0], "a coolant that carries no heat is not worth the draw");
		assertTrue(Double.isFinite(thermal.temperature()));
	}

	// ---- Dynamometer: the rating counts the point it is asked about ---------------------------------------------------

	@Test
	void aRatingAtASpeedOnTheCurveCountsThatSpeedsPoint() {
		double[] curve = {10, 10, 100};
		double[] none = {};
		// 16, 32, 48 RPM are the first three points: asking at 48 includes the third, which is the biggest
		assertEquals(100, Dynamometer.peakTorque(curve, 48), 0);
		assertEquals(10, Dynamometer.peakTorque(curve, Math.nextDown(48.0)), 0);
		assertEquals(100, Dynamometer.ratedTorque(curve, none, 48), 1e-9);
		assertEquals(10, Dynamometer.ratedTorque(curve, none, Math.nextDown(48.0)), 1e-9);
		double[] ripples = {0, 0, 0.2};
		double rated = 100 * Roughness.smoothness(0.2);
		assertEquals(1 - rated / 100, Dynamometer.roughnessLoss(curve, ripples, 48), 1e-9);
		assertEquals(0, Dynamometer.roughnessLoss(curve, ripples, Math.nextDown(48.0)), 1e-9);
	}

	@Test
	void theCrawlSpeedPointIsLeftOutOfTheRoughnessJudgingFromTheCrawlFloorUp() {
		double[] curve = {100, 50};
		double[] ripples = {0.5, 0};
		// At exactly the crawl floor the first point (16 RPM, an engine at its roughest) is no longer the peak it is judged by
		assertEquals(0, Dynamometer.roughnessLoss(curve, ripples, Dynamometer.CRAWL_RPM), 1e-9);
		// Below the floor an engine is judged by it
		assertTrue(Dynamometer.roughnessLoss(curve, ripples, Math.nextDown(Dynamometer.CRAWL_RPM)) > 0.3);
	}

	// ---- Soiling and Lubrication --------------------------------------------------------------------------------------

	private static CycleStats cycle(double injected, double burned, int sparks) {
		CycleStats stats = new CycleStats();
		stats.fuelInjected = injected;
		stats.fuelBurned = burned;
		stats.sparks = sparks;
		stats.result = burned > 0 ? CycleResult.FIRED : CycleResult.NO_IGNITION;
		return stats;
	}

	private static Soiling fouled() {
		Soiling soiling = new Soiling();
		for (int i = 0; i < 3; i++)
			soiling.endCycle(cycle(1, 0.5, 1), true, FuelSpec.GASOLINE);
		assertTrue(soiling.fouling() > 0.1, "the plug starts out fouled: " + soiling.fouling());
		return soiling;
	}

	@Test
	void aPlateSparkingOnAChargeThatNeverLitScrubsItselfCleanerNotDirtier() {
		Soiling soiling = fouled();
		double before = soiling.fouling();
		soiling.endCycle(cycle(1, 0, 2), true, FuelSpec.GASOLINE);
		assertEquals(before - 0.04, soiling.fouling(), 1e-12, "a cycle that sparked and burned nothing scrubs the plug");
	}

	@Test
	void aCycleWithNothingInjectedLeavesThePlugAlone() {
		Soiling soiling = fouled();
		double before = soiling.fouling();
		soiling.endCycle(cycle(0, 0, 3), true, FuelSpec.GASOLINE);
		assertEquals(before, soiling.fouling(), 0, "sparks with no fuel in the cylinder say nothing about the plug");
	}

	@Test
	void aDrySumpOfOilThatGetsNothingKeepsItsCondition() {
		Lubrication oil = new Lubrication(0);
		oil.setCondition(0.4);
		oil.setDilution(0.3);
		oil.update(1, EngineSimulation.TICK, 4, mb -> 0);
		assertEquals(0.4, oil.condition(), 0, "no oil went in, so none is fresh");
		assertEquals(0.3, oil.dilution(), 0);
	}

	@Test
	void oilGoingIntoAnEngineExactlyAtTheDryLineIsAFreshCharge() {
		Lubrication oil = new Lubrication(0.02);
		oil.setCondition(0.4);
		oil.setDilution(0.3);
		oil.update(1, EngineSimulation.TICK, 4, mb -> mb);
		assertEquals(1, oil.condition(), 0, "an engine at the dry line takes the oil as a fresh charge");
		assertEquals(0, oil.dilution(), 0);
	}

	@Test
	void anEngineWithNoRevolutionsAsksForNoOilAndIsNotPoisonedByTheDivision() {
		Lubrication oil = new Lubrication(0.6);
		oil.update(0, EngineSimulation.TICK, 4, mb -> mb);
		assertEquals(0.6, oil.film(), 0, "no turning, no demand, no change");
	}

	@Test
	void oilAtTheDryLineDoesNotAgeBecauseThereIsNothingToWearOut() {
		Lubrication oil = new Lubrication(0.02);
		oil.age(1000, 1);
		assertEquals(1, oil.condition(), 0);
		Lubrication wet = new Lubrication(0.5);
		wet.age(1000, 1);
		assertTrue(wet.condition() < 1);
	}
}
