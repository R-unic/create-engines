package com.createengines.engine.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Direct tests of the three parts taken out of {@link EngineSimulation} and {@link CylinderModel} so that each could be
 * tested on its own: {@link SwingTracker}, {@link Soiling} and {@link Induction}.
 */
class ExtractedPartsTest {

	@AfterEach
	void resetTuning() {
		Tuning.reset();
	}

	// ---- SwingTracker -----------------------------------------------------------------------------------------

	@Test
	void theSwingIsTheBiggestExcessMinusTheBiggestShortfallOfTheEnergyAgainstAnEvenTorque() {
		SwingTracker tracker = new SwingTracker();
		assertEquals(0, tracker.result(), 0);
		// Four half-turns of 180 degrees (pi radians) doing 3, 1, 1, 1 of work: an even torque would do 1.5 each, so the
		// energy runs 1.5, 1, 0.5, 0 ahead of it
		for (double work : new double[] {3, 1, 1, 1})
			tracker.add(work, Math.PI);
		assertEquals(1.5, tracker.result(), 1e-9);
	}

	@Test
	void anEvenTorqueDoesNotSwingAndTheResultStaysUntilTheNextCycleCompletes() {
		SwingTracker even = new SwingTracker();
		for (int i = 0; i < 4; i++)
			even.add(2, Math.PI);
		assertEquals(0, even.result(), 1e-9);

		SwingTracker tracker = new SwingTracker();
		for (double work : new double[] {3, 1, 1, 1})
			tracker.add(work, Math.PI);
		// A partly driven next cycle leaves the last result alone
		tracker.add(100, Math.PI);
		tracker.add(-100, Math.PI);
		assertEquals(1.5, tracker.result(), 1e-9);
		// Finishing it replaces it: the energy ran 100 ahead of an even torque (which does nothing here) and came back
		tracker.add(0, Math.PI);
		tracker.add(0, Math.PI);
		assertEquals(100, tracker.result(), 1e-9);
	}

	@Test
	void aShortfallCountsAsMuchAsAnExcess() {
		SwingTracker tracker = new SwingTracker();
		// Work 0, 0, 0, 6: the energy runs behind an even torque by 1.5, 3, 1.5 and then catches up
		for (double work : new double[] {0, 0, 0, 6})
			tracker.add(work, Math.PI);
		assertEquals(4.5, tracker.result(), 1e-9);
	}

	@Test
	void manyStepsAreKept() {
		SwingTracker tracker = new SwingTracker();
		int steps = 1000;
		for (int i = 0; i < steps; i++)
			tracker.add(i < steps / 2 ? 2 : 0, 4 * Math.PI / steps);
		// The first half does everything: the excess peaks at the half-way point at half the total
		assertEquals(500, tracker.result(), 1e-6);
	}

	// ---- Soiling ----------------------------------------------------------------------------------------------

	private static CycleStats cycle(CycleResult result, double injected, double burned, int sparks) {
		CycleStats stats = new CycleStats();
		stats.result = result;
		stats.fuelInjected = injected;
		stats.fuelBurned = burned;
		stats.sparks = sparks;
		return stats;
	}

	@Test
	void aRichBurnFoulsThePlugAndLeavesSootInTheExhaust() {
		Soiling soiling = new Soiling();
		FuelSpec fuel = FuelSpec.GASOLINE;
		soiling.endCycle(cycle(CycleResult.FIRED, 10, 5, 1), true, fuel);
		// Half the shot went unburned
		assertEquals(0.12 * 0.5 * fuel.sootFactor(), soiling.fouling(), 1e-12);
		assertEquals(SootCollector.sootFrom(10, 0.5, fuel.sootFactor()), soiling.takeSoot(), 1e-12);
		assertEquals(0, soiling.takeSoot(), 0);
		// A plug that is clean needs no scrubbing by a cleaner burn: it only falls by the burn-off
		soiling.endCycle(cycle(CycleResult.FIRED, 10, 10, 1), true, fuel);
		assertEquals(Math.max(0, 0.12 * 0.5 * fuel.sootFactor() - 0.12), soiling.fouling(), 1e-12);
	}

	@Test
	void aRichLimitWithAFouledPlugIsPulledIn() {
		Soiling soiling = new Soiling();
		assertEquals(CylinderModel.RICH_LIMIT, soiling.richLimit(), 0);
		for (int i = 0; i < 50; i++)
			soiling.endCycle(cycle(CycleResult.FIRED, 10, 0.1, 1), true, FuelSpec.GASOLINE);
		assertEquals(1, soiling.fouling(), 1e-12);
		assertEquals(CylinderModel.RICH_LIMIT - (CylinderModel.RICH_LIMIT - 1.4), soiling.richLimit(), 1e-12);
	}

	@Test
	void aPlugThatDidNotLightIsScrubbedByItsSparksOnly() {
		Soiling soiling = new Soiling();
		for (int i = 0; i < 20; i++)
			soiling.endCycle(cycle(CycleResult.FIRED, 10, 0.1, 1), true, FuelSpec.GASOLINE);
		double fouled = soiling.fouling();
		soiling.endCycle(cycle(CycleResult.TOO_RICH, 10, 0, 0), true, FuelSpec.GASOLINE);
		assertEquals(fouled, soiling.fouling(), 0);
		soiling.endCycle(cycle(CycleResult.TOO_RICH, 10, 0, 3), true, FuelSpec.GASOLINE);
		assertEquals(fouled - 0.04, soiling.fouling(), 1e-12);
	}

	@Test
	void aCylinderWithNoPlugOrNoFuelIsNeverFouled() {
		Soiling soiling = new Soiling();
		soiling.endCycle(cycle(CycleResult.FIRED, 10, 1, 0), false, FuelSpec.DIESEL);
		assertEquals(0, soiling.fouling(), 0);
		assertEquals(SootCollector.sootFrom(10, 0.9, FuelSpec.DIESEL.sootFactor()), soiling.takeSoot(), 1e-12);
		soiling.endCycle(cycle(CycleResult.NO_FUEL, 0, 0, 1), true, FuelSpec.GASOLINE);
		assertEquals(0, soiling.fouling(), 0);
		assertEquals(0, soiling.takeSoot(), 0);
		// With fouling switched off the plug is always clean
		Soiling dirty = new Soiling();
		dirty.endCycle(cycle(CycleResult.FIRED, 10, 5, 1), true, FuelSpec.GASOLINE);
		Tuning.sparkFouling = false;
		dirty.endCycle(cycle(CycleResult.FIRED, 10, 5, 1), true, FuelSpec.GASOLINE);
		assertEquals(0, dirty.fouling(), 0);
	}

	@Test
	void unknownFuelCountsAsOrdinarySootAndTheStoreIsCapped() {
		Soiling soiling = new Soiling();
		soiling.endCycle(cycle(CycleResult.FIRED, 10, 5, 1), true, null);
		assertEquals(0.06, soiling.fouling(), 1e-12);
		assertEquals(5, soiling.takeSoot(), 1e-12);
		for (int i = 0; i < 100; i++)
			soiling.endCycle(cycle(CycleResult.FIRED, 1000, 0, 0), false, null);
		assertEquals(Soiling.SOOT_CAP, soiling.takeSoot(), 0);
		Soiling copy = new Soiling();
		soiling.endCycle(cycle(CycleResult.FIRED, 10, 5, 1), true, null);
		copy.copyFrom(soiling);
		assertEquals(soiling.fouling(), copy.fouling(), 0);
		assertEquals(soiling.takeSoot(), copy.takeSoot(), 0);
	}

	// ---- Induction --------------------------------------------------------------------------------------------

	private static List<CylinderModel> twoCylinders() {
		Camshaft cam = new Camshaft();
		return List.of(new CylinderModel(CylinderSpec.CRUDE, CamProfile.BASIC, 0, cam, 0),
			new CylinderModel(CylinderSpec.CRUDE, CamProfile.BASIC, 360, cam, 90));
	}

	private static Supercharger blower(List<CylinderModel> on, double rpm, double limit) {
		Supercharger blower = new Supercharger(on);
		blower.rpm = rpm;
		blower.limit = limit;
		return blower;
	}

	@Test
	void withNoStagesACoolEngineStillDrawsADenserChargeAndNothingElseChanges() {
		List<CylinderModel> cylinders = twoCylinders();
		cylinders.get(0).intakePressure = 3;
		new Induction().tick(0, cylinders, 0.97);
		assertEquals(0.97, cylinders.get(0).intakeTemperature, 0);
		assertEquals(3, cylinders.get(0).intakePressure, 0);
	}

	@Test
	void stagesInSeriesMultiplyAndStagesInParallelAverage() {
		List<CylinderModel> cylinders = twoCylinders();
		Induction induction = new Induction();
		Supercharger a = blower(cylinders, 300, 0.6);
		Supercharger b = blower(cylinders, 300, 0.3);
		induction.superchargers.add(a);
		induction.superchargers.add(b);
		for (int i = 0; i < 100; i++)
			induction.tick(0, cylinders, 1);
		double pa = 1 + a.boost();
		double pb = 1 + b.boost();
		assertEquals(true, a.boost() > 0 && b.boost() > 0 && a.boost() != b.boost(), "the two blowers have to differ for this to mean anything");
		assertEquals(pa * pb, cylinders.get(0).intakePressure, 1e-12);
		assertEquals(pa * pb, cylinders.get(1).intakePressure, 1e-12);
		assertEquals(a.chargeTemperature() * b.chargeTemperature(), cylinders.get(0).intakeTemperature, 1e-12);
		assertEquals(1, cylinders.get(0).exhaustPressure, 0);

		a.parallel = true;
		b.parallel = true;
		induction.tick(0, cylinders, 1);
		pa = 1 + a.boost();
		pb = 1 + b.boost();
		assertEquals((pa + pb) / 2, cylinders.get(0).intakePressure, 1e-12);
		assertEquals((a.chargeTemperature() + b.chargeTemperature()) / 2, cylinders.get(0).intakeTemperature, 1e-12);

		// The cool engine's denser charge multiplies onto whatever the stages made
		induction.tick(0, cylinders, 0.9);
		assertEquals((a.chargeTemperature() + b.chargeTemperature()) / 2 * 0.9, cylinders.get(0).intakeTemperature, 1e-12);
	}

	@Test
	void aBlowerGearedToTheCrankFollowsItsSpeed() {
		List<CylinderModel> cylinders = twoCylinders();
		Induction induction = new Induction();
		Supercharger geared = new Supercharger(cylinders).copyOnto(java.util.Map.of(cylinders.get(0), cylinders.get(0),
			cylinders.get(1), cylinders.get(1)));
		geared.driveRatio = 2;
		induction.superchargers.add(geared);
		induction.tick(150, cylinders, 1);
		assertEquals(300, geared.rpm, 1e-12);
		induction.tick(80, cylinders, 1);
		assertEquals(160, geared.rpm, 1e-12);
	}

	@Test
	void withForcedInductionSwitchedOffTheStagesChangeNothing() {
		Tuning.forcedInduction = false;
		List<CylinderModel> cylinders = twoCylinders();
		Induction induction = new Induction();
		induction.superchargers.add(blower(cylinders, 300, 0.6));
		for (int i = 0; i < 50; i++)
			induction.tick(0, cylinders, 0.95);
		assertEquals(1, cylinders.get(0).intakePressure, 1e-12);
		assertEquals(0.95, cylinders.get(0).intakeTemperature, 1e-12);
	}

	// ---- Charge and Ports -------------------------------------------------------------------------------------

	private static Charge gas(double moles, double temperature, double fresh) {
		Charge charge = new Charge();
		charge.moles = moles;
		charge.temperature = temperature;
		charge.fresh = fresh;
		return charge;
	}

	@Test
	void aChargesPressureIsMolesTimesTemperatureOverVolume() {
		assertEquals(3, gas(2, 3, 0).pressure(2), 1e-12);
		assertEquals(0.5, gas(1, 1, 1).pressure(2), 1e-12);
		Charge copy = new Charge();
		copy.copyFrom(gas(4, 5, 0.25));
		assertEquals(4, copy.moles, 0);
		assertEquals(5, copy.temperature, 0);
		assertEquals(0.25, copy.fresh, 0);
	}

	@Test
	void theIntakeDrawsGasInTowardsItsPressureAndPushesBackWhatIsOver() {
		double ln2 = Math.log(2);
		// Halfway to an intake pressure of 2 from 1: 0.5 mole in, all of it fresh
		Charge charge = gas(1, 1, 0);
		new Ports().exchangeIntake(charge, 1, 1, ln2, 2, 1, 0);
		assertEquals(1.5, charge.moles, 1e-9);
		assertEquals(1.0 / 3, charge.fresh, 1e-9);
		assertEquals(1, charge.temperature, 1e-9);
		// An intake below the cylinder's pressure pushes gas out into the manifold instead
		Charge over = gas(1, 1, 0.4);
		Ports ports = new Ports();
		ports.exchangeIntake(over, 1, 1, ln2, 0.5, 1, 0);
		assertEquals(0.75, over.moles, 1e-9);
		assertEquals(0.4, over.fresh, 0);
		// And what was pushed back is the first thing drawn in again: nothing new comes from outside
		ports.exchangeIntake(over, 1, 1, 1e9, 1, 1, 0);
		assertEquals(1, over.moles, 1e-9);
		assertEquals(0.75 * 0.4 + 0.25 * 0.4, over.moles * over.fresh, 1e-9);
		// A fuel that boils off cools the charge it is mixed into, down to a floor of 0.6
		Charge cooled = gas(1, 1, 0);
		new Ports().exchangeIntake(cooled, 1, 1, ln2, 2, 1, 0.5);
		assertEquals((1 + 0.5 / 0.6 * 0.6) / (1 + 0.5 / 0.6), cooled.temperature, 1e-9);
		Charge floored = gas(1, 1, 0);
		new Ports().exchangeIntake(floored, 1, 1, ln2, 2, 1, 5);
		assertEquals((1 + 0.5 / 0.6 * 0.6) / (1 + 0.5 / 0.6), floored.temperature, 1e-9);
		// A closed valve does nothing
		Charge shut = gas(1, 1, 0);
		new Ports().exchangeIntake(shut, 1, 0, ln2, 2, 1, 0);
		assertEquals(1, shut.moles, 0);
	}

	@Test
	void theExhaustBlowsDownAndOnlyBurnedGasMakesHeatForATurbine() {
		double ln2 = Math.log(2);
		CycleStats burned = new CycleStats();
		burned.fuelBurned = 1;
		Ports hot = new Ports();
		Charge charge = gas(1, 2, 1);
		hot.exchangeExhaust(charge, 1, 1, ln2, 1, burned, new CycleStats());
		assertEquals(0.75, charge.moles, 1e-9);
		hot.endTick();
		assertEquals(0.25, hot.exhaustHeatLastTick(), 1e-9);
		// Compressed air that was never lit carries no heat
		Ports cold = new Ports();
		cold.exchangeExhaust(gas(1, 2, 1), 1, 1, ln2, 1, new CycleStats(), new CycleStats());
		cold.endTick();
		assertEquals(0, cold.exhaustHeatLastTick(), 0);
		// Burning in the last cycle counts too
		Ports lately = new Ports();
		lately.exchangeExhaust(gas(1, 2, 1), 1, 1, ln2, 1, new CycleStats(), burned);
		lately.endTick();
		assertEquals(0.25, lately.exhaustHeatLastTick(), 1e-9);
		// An exhaust pressure above the cylinder's lets gas back in, and it is spent gas, not fresh air
		Charge back = gas(1, 1, 1);
		new Ports().exchangeExhaust(back, 1, 1, ln2, 2, new CycleStats(), new CycleStats());
		assertEquals(1.5, back.moles, 1e-9);
		assertEquals(1.0 / 1.5, back.fresh, 1e-9);
		// Resetting and copying the ports
		Ports copy = new Ports();
		copy.copyFrom(hot);
		assertEquals(0.25, copy.exhaustHeatLastTick(), 1e-9);
		copy.reset();
		assertEquals(0, copy.exhaustHeatLastTick(), 0);
	}
}
