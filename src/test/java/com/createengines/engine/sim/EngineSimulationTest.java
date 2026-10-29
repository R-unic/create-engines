package com.createengines.engine.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class EngineSimulationTest {

	private static final double MAX_OMEGA = EngineSimulation.rpmToOmega(256);
	private static final double CRANK_RPM = 32;
	private static final double JOURNAL_INERTIA = 0.15;
	private static final double FLYWHEEL_INERTIA = 1;

	static final class Tank implements FuelSupply {
		final FuelSpec fuel;
		double amount;
		double used;

		Tank(double amount) {
			this(amount, FuelSpec.PLANT_OIL);
		}

		Tank(double amount, FuelSpec fuel) {
			this.amount = amount;
			this.fuel = fuel;
		}

		@Override
		public double draw(double mb) {
			double delivered = Math.min(mb, amount);
			amount -= delivered;
			used += delivered;
			return delivered;
		}

		@Override
		public FuelSpec fuel() {
			return amount > 0 ? fuel : null;
		}
	}

	/** A correctly timed cylinder whose piston reaches TDC at {@code tdcAngle}, firing on revolution 0 or 1. */
	private static CylinderModel cylinder(double tdcAngle, int revolution, Camshaft cam, FuelSupply fuel) {
		return cylinderWithLobe(tdcAngle, tdcAngle / 2 + 180 * revolution, cam, fuel);
	}

	private static CylinderModel cylinderWithLobe(double tdcAngle, double lobe, Camshaft cam,
												  FuelSupply fuel) {
		CylinderModel cylinder = new CylinderModel(CylinderSpec.CRUDE, CamProfile.BASIC, tdcAngle, cam, lobe);
		cylinder.fuelSupply = fuel;
		return cylinder;
	}

	private static CylinderModel single(FuelSupply fuel) {
		return cylinder(0, 0, new Camshaft(), fuel);
	}

	private static EngineSimulation engine(double extraInertia, CylinderModel... cylinders) {
		return new EngineSimulation(List.of(cylinders), 3 * JOURNAL_INERTIA + extraInertia, 0.02);
	}

	/** Turns the engine with a starter that it can outrun, like a real one-way starter clutch. */
	private static void crank(EngineSimulation engine, double seconds) {
		for (int i = 0; i < seconds * 20; i++)
			engine.tickCranked(0, MAX_OMEGA, EngineSimulation.rpmToOmega(CRANK_RPM), 1);
	}

	private static void drive(EngineSimulation engine, double rpm, double seconds) {
		for (int i = 0; i < seconds * 20; i++)
			engine.tickDriven(EngineSimulation.rpmToOmega(rpm), 1);
	}

	private static void run(EngineSimulation engine, double seconds, double load) {
		for (int i = 0; i < seconds * 20; i++)
			engine.tickFree(load, MAX_OMEGA);
	}

	@Test
	void singleCylinderCatchesAndRunsAfterCranking() {
		Tank tank = new Tank(1000);
		EngineSimulation engine = engine(0, single(tank));
		crank(engine, 10);
		assertTrue(engine.isSustaining(), "should be firing on its own after cranking");
		assertEquals(CycleResult.FIRED, engine.cylinders().getFirst().lastCycle().result);

		run(engine, 20, 0);
		assertFalse(engine.isStopped());
		assertTrue(engine.rpm() > CRANK_RPM, "unloaded engine should rev above cranking speed, got " + engine.rpm());
		assertTrue(tank.used > 0);
	}

	@Test
	void starterSuppliesTorqueOnlyUntilTheEngineOutrunsIt() {
		EngineSimulation dead = engine(FLYWHEEL_INERTIA, single(new Tank(0)));
		crank(dead, 5);
		double cranking = 0;
		for (int i = 0; i < 100; i++) {
			dead.tickCranked(0, MAX_OMEGA, EngineSimulation.rpmToOmega(CRANK_RPM), 1);
			cranking += dead.crankingTorque();
		}
		assertTrue(cranking > 0, "a dead engine has to be dragged round");

		EngineSimulation live = engine(FLYWHEEL_INERTIA, single(new Tank(1000)));
		crank(live, 20);
		assertTrue(live.rpm() > CRANK_RPM + 5, "a running engine should pull away from the starter");
		assertEquals(0, live.crankingTorque(), 1e-9);
	}

	@Test
	void eitherCamRevolutionWorks() {
		EngineSimulation engine = engine(0, cylinder(0, 1, new Camshaft(), new Tank(1000)));
		crank(engine, 10);
		assertTrue(engine.isSustaining());
	}

	@Test
	void runsOutOfFuelAndStalls() {
		Tank tank = new Tank(4);
		EngineSimulation engine = engine(0, single(tank));
		crank(engine, 10);
		run(engine, 60, 0);
		assertTrue(engine.isStopped());
		assertEquals(0, tank.amount, 1e-9);
	}

	@Test
	void noFuelNeverFires() {
		EngineSimulation engine = engine(0, single(new Tank(0)));
		crank(engine, 10);
		assertFalse(engine.isSustaining());
		assertEquals(CycleResult.NO_FUEL, engine.cylinders().getFirst().lastCycle().result);
		run(engine, 60, 0);
		assertTrue(engine.isStopped());
	}

	@Test
	void missingIntakeMeansNoCombustion() {
		CylinderModel cylinder = single(new Tank(1000));
		cylinder.intakeFactor = 0;
		EngineSimulation engine = engine(0, cylinder);
		crank(engine, 10);
		assertFalse(engine.isSustaining());
		CycleResult result = cylinder.lastCycle().result;
		assertTrue(result == CycleResult.NO_AIR || result == CycleResult.NO_IGNITION, "got " + result);
	}

	@Test
	void blockedExhaustPreventsRunning() {
		CylinderModel cylinder = single(new Tank(1000));
		cylinder.exhaustFactor = 0;
		EngineSimulation engine = engine(0, cylinder);
		crank(engine, 10);
		run(engine, 60, 0);
		assertTrue(engine.isStopped(), "an engine that can't exhaust shouldn't keep running");
	}

	@Test
	void noCamshaftMeansNoBreathingOrInjection() {
		CylinderModel cylinder = cylinderWithLobe(0, 0, null, new Tank(1000));
		EngineSimulation engine = engine(0, cylinder);
		crank(engine, 10);
		assertFalse(engine.isSustaining());
		assertEquals(CycleResult.NO_FUEL, cylinder.lastCycle().result);
	}

	@Test
	void camAtCrankSpeedPreventsRunning() {
		Camshaft cam = new Camshaft();
		cam.ratio = 1;
		EngineSimulation engine = engine(0, cylinder(0, 0, cam, new Tank(1000)));
		crank(engine, 10);
		run(engine, 60, 0);
		assertTrue(engine.isStopped(), "valves opening every revolution should kill compression");
	}

	@Test
	void badlyRetardedCamPreventsRunning() {
		// 90 cam degrees = 180 crank degrees out: the valves open on the wrong strokes.
		EngineSimulation engine = engine(0, cylinderWithLobe(0, 90, new Camshaft(), new Tank(1000)));
		crank(engine, 10);
		run(engine, 60, 0);
		assertTrue(engine.isStopped());
	}

	@Test
	void slightlyWrongCamCostsPower() {
		double[] torque = new double[2];
		double[] lobes = {0, 7.5};
		for (int i = 0; i < 2; i++) {
			EngineSimulation engine = engine(FLYWHEEL_INERTIA, cylinderWithLobe(0, lobes[i], new Camshaft(),
				new Tank(1000)));
			drive(engine, 64, 10);
			torque[i] = engine.netTorque();
		}
		assertTrue(torque[1] < torque[0], "15° crank late " + torque[1] + " should be below " + torque[0]);
		assertTrue(torque[1] > 0, "but should still run");
	}

	@Test
	void timingErrorReportsCrankDegrees() {
		CylinderModel right = cylinder(90, 0, new Camshaft(), FuelSupply.NONE);
		CylinderModel otherRevolution = cylinder(90, 1, new Camshaft(), FuelSupply.NONE);
		CylinderModel late = cylinderWithLobe(90, 45 + 15, new Camshaft(), FuelSupply.NONE);
		assertEquals(0, right.timingError(1), 1e-9);
		assertEquals(0, otherRevolution.timingError(1), 1e-9);
		assertEquals(30, late.timingError(1), 1e-9);
		assertEquals(180, Math.abs(right.timingError(-1)), 1e-9, "timed for one direction, wrong for the other");
	}

	@Test
	void fuelIsOnlyUsedByInjectionEvents() {
		Tank tank = new Tank(1000);
		EngineSimulation engine = engine(0, single(tank));
		crank(engine, 10);
		run(engine, 10, 0);
		double used = tank.used;
		run(engine, 30, 50);
		assertTrue(engine.isStopped());
		double afterStall = tank.used;
		run(engine, 30, 0);
		assertEquals(afterStall, tank.used, 1e-9);
		assertTrue(used > 0);
	}

	@Test
	void excessiveLoadStalls() {
		EngineSimulation engine = engine(FLYWHEEL_INERTIA, single(new Tank(1000)));
		crank(engine, 10);
		run(engine, 10, 0);
		double available = engine.availableTorque();
		assertTrue(available > 0);
		run(engine, 20, available * 3);
		assertTrue(engine.isStopped());
	}

	@Test
	void moderateLoadSettlesBelowFreeSpeed() {
		EngineSimulation free = engine(FLYWHEEL_INERTIA, single(new Tank(10000)));
		crank(free, 10);
		run(free, 30, 0);

		EngineSimulation loaded = engine(FLYWHEEL_INERTIA, single(new Tank(10000)));
		crank(loaded, 10);
		run(loaded, 30, free.availableTorque() * 0.5);

		assertFalse(loaded.isStopped());
		assertTrue(loaded.rpm() < free.rpm(), loaded.rpm() + " should be below " + free.rpm());
	}

	@Test
	void flywheelReducesRipple() {
		EngineSimulation bare = engine(0, single(new Tank(1000)));
		EngineSimulation heavy = engine(FLYWHEEL_INERTIA * 3, single(new Tank(1000)));
		// Run until both settle at the same free-running speed, so only inertia differs.
		for (EngineSimulation engine : List.of(bare, heavy)) {
			crank(engine, 10);
			run(engine, 240, 0);
		}
		assertEquals(bare.rpm(), heavy.rpm(), bare.rpm() * 0.1);
		assertTrue(heavy.ripple() < bare.ripple(), heavy.ripple() + " should be below " + bare.ripple());
	}

	@Test
	void moreCylindersMoreTorqueAndFuel() {
		Tank oneTank = new Tank(10000);
		EngineSimulation one = engine(FLYWHEEL_INERTIA, single(oneTank));
		Tank twinTank = new Tank(10000);
		Camshaft cam = new Camshaft();
		EngineSimulation twin = engine(FLYWHEEL_INERTIA, cylinder(0, 0, cam, twinTank), cylinder(0, 1, cam, twinTank));
		drive(one, 96, 5);
		drive(twin, 96, 5);
		oneTank.used = 0;
		twinTank.used = 0;
		drive(one, 96, 10);
		drive(twin, 96, 10);
		assertTrue(twin.netTorque() > one.netTorque() * 1.5, twin.netTorque() + " vs " + one.netTorque());
		assertTrue(twinTank.used > oneTank.used * 1.5);
	}

	@Test
	void alternatingFiringIsSmootherThanSimultaneous() {
		// Two cylinders on the same crank throw. Lobes at the same angle fire both together; moving one lobe by
		// 180 cam degrees fires them a revolution apart.
		Camshaft samePhaseCam = new Camshaft();
		EngineSimulation together = engine(0, cylinder(0, 0, samePhaseCam, new Tank(1000)),
			cylinder(0, 0, samePhaseCam, new Tank(1000)));
		Camshaft splitCam = new Camshaft();
		EngineSimulation alternating = engine(0, cylinder(0, 0, splitCam, new Tank(1000)),
			cylinder(0, 1, splitCam, new Tank(1000)));
		for (EngineSimulation engine : List.of(together, alternating)) {
			crank(engine, 10);
			run(engine, 15, 0);
		}
		assertTrue(alternating.ripple() < together.ripple(),
			alternating.ripple() + " should be below " + together.ripple());
	}

	@Test
	void dieselStartsWhereSeedOilWillNot() {
		// A leaky low-compression cylinder: the charge gets hot enough for diesel but not for plant oil.
		CylinderSpec lowCompression = new CylinderSpec(6, 4 / 14d, 20, 20, 0.5, 0.02, 0.06, 0.08, 0.01, 0.2);
		CylinderModel oil = new CylinderModel(lowCompression, CamProfile.BASIC, 0, new Camshaft(), 0);
		oil.fuelSupply = new Tank(1000, FuelSpec.PLANT_OIL);
		CylinderModel diesel = new CylinderModel(lowCompression, CamProfile.BASIC, 0, new Camshaft(), 0);
		diesel.fuelSupply = new Tank(1000, FuelSpec.DIESEL);
		EngineSimulation oilEngine = engine(0, oil);
		EngineSimulation dieselEngine = engine(0, diesel);
		crank(oilEngine, 10);
		crank(dieselEngine, 10);
		assertEquals(CycleResult.NO_IGNITION, oil.lastCycle().result);
		assertEquals(CycleResult.FIRED, diesel.lastCycle().result);
	}

	/** Not an assertion: prints the numbers used to calibrate torque to Stress Capacity. */
	@Test
	void calibrationReport() {
		for (FuelSpec fuel : List.of(FuelSpec.PLANT_OIL, FuelSpec.DIESEL)) {
			CylinderModel cylinder = cylinder(0, 0, new Camshaft(), new Tank(100000, fuel));
			EngineSimulation engine = engine(FLYWHEEL_INERTIA, cylinder);
			crank(engine, 10);
			run(engine, 30, 0);
			System.out.printf("%s free-running: %.1f RPM, available %.3f, ripple %.3f, fuel %.4f mB/s%n", fuel,
				engine.rpm(), engine.availableTorque(), engine.ripple(), engine.fuelRate());
			for (double rpm : new double[] {16, 32, 64, 128, 192, 256}) {
				drive(engine, rpm, 5);
				CycleStats last = cylinder.lastCycle();
				System.out.printf(
					"%4.0f RPM: net %.3f avail %.3f work %.2f Tcomp %.2f peak p %.1f burned %.3f/%.3f %s%n", rpm,
					engine.netTorque(), engine.availableTorque(), last.work, last.compressionTemp, last.peakPressure,
					last.fuelBurned, last.fuelInjected, last.result);
			}
		}
	}
}
