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
		assertEquals(CycleResult.NO_AIR, result);
	}

	/**
	 * Cranks a cylinder that can never fire, checking it never does and returns the energy the starter had to supply:
	 * a cylinder that isn't burning anything can only be a drag.
	 */
	private static double crankWithoutCombustion(CylinderModel cylinder, double seconds) {
		EngineSimulation engine = engine(FLYWHEEL_INERTIA, cylinder);
		double supplied = 0;
		for (int i = 0; i < seconds * 20; i++) {
			engine.tickCranked(0, MAX_OMEGA, EngineSimulation.rpmToOmega(CRANK_RPM), 1);
			supplied += engine.crankingTorque();
			assertFalse(cylinder.lastCycle().result.fired(), "should never fire, tick " + i);
			assertFalse(engine.isSustaining(), "should never carry itself, tick " + i);
		}
		return supplied;
	}

	@Test
	void missingIntakeNeverFiresEvenOnItsFirstCycles() {
		CylinderModel cylinder = single(new Tank(1000));
		cylinder.intakeFactor = 0;
		assertTrue(crankWithoutCombustion(cylinder, 60) > 0, "starter has to drag a cylinder that can't breathe");
		assertEquals(CycleResult.NO_AIR, cylinder.lastCycle().result);
	}

	@Test
	void cylinderWithoutFuelOnlyCostsPower() {
		assertTrue(crankWithoutCombustion(single(new Tank(0)), 60) > 0, "pumping air with no fuel can't add energy");
	}

	@Test
	void motoringTorqueIsAlwaysNegative() {
		for (boolean intake : new boolean[] {true, false}) {
			CylinderModel cylinder = single(new Tank(0));
			cylinder.intakeFactor = intake ? 1 : 0;
			EngineSimulation engine = engine(FLYWHEEL_INERTIA, cylinder);
			for (double rpm : Dynamometer.SPEEDS) {
				drive(engine, rpm, 20);
				assertTrue(engine.netTorque() < 0,
					"motoring at " + rpm + " RPM (intake " + intake + ") made torque " + engine.netTorque());
			}
		}
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
	void blockingTheExhaustOfARunningEngineStallsIt() {
		CylinderModel cylinder = single(new Tank(1000));
		EngineSimulation engine = engine(0, cylinder);
		crank(engine, 10);
		run(engine, 20, 0);
		assertFalse(engine.isStopped(), "should be running before the exhaust is blocked");
		cylinder.exhaustFactor = 0;
		run(engine, 60, 0);
		assertTrue(engine.isStopped(), "burned gas pushed back into the intake should choke it");
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
	void aSharedLobeGivesEveryCylinderOnItTheFirstOnesTimingError() {
		// A vee on one throw: pistons top out at 90 and 180 crank degrees, and the lobe is set for the first
		CylinderModel first = cylinderWithLobe(90, 45, new Camshaft(), FuelSupply.NONE);
		CylinderModel second = cylinderWithLobe(180, 45, new Camshaft(), FuelSupply.NONE);
		second.shareLobe(90);
		assertEquals(0, first.timingError(1), 1e-9);
		assertEquals(0, second.timingError(1), 1e-9, "on time at its own top dead centre too");
		assertEquals(90, SimMath.wrap(second.firingRotorAngle() - first.firingRotorAngle()), 1e-9,
			"it fires as far after the first as its piston tops out after the first's");
		assertEquals(second.lobeAngle() - 45, second.lobeShift(), 1e-9);

		CylinderModel late = cylinderWithLobe(90, 45 + 15, new Camshaft(), FuelSupply.NONE);
		CylinderModel follower = cylinderWithLobe(180, 45 + 15, new Camshaft(), FuelSupply.NONE);
		follower.shareLobe(90);
		assertEquals(late.timingError(1), follower.timingError(1), 1e-9, "a lobe set late is late for both");

		second.setDirection(-1);
		assertEquals(first.timingError(-1), second.timingError(-1), 1e-9, "and the same the other way round");
	}

	@Test
	void aSharedLobeRunsLikeLobesSetOneByOne() {
		Camshaft cam = new Camshaft();
		CylinderModel first = cylinderWithLobe(90, 45, cam, new Tank(1000));
		CylinderModel second = cylinderWithLobe(180, 45, cam, new Tank(1000));
		second.shareLobe(90);
		EngineSimulation shared = engine(FLYWHEEL_INERTIA, first, second);
		drive(shared, 64, 10);

		Camshaft apart = new Camshaft();
		EngineSimulation separate = engine(FLYWHEEL_INERTIA, cylinder(90, 0, apart, new Tank(1000)),
			cylinder(180, 0, apart, new Tank(1000)));
		drive(separate, 64, 10);
		assertTrue(separate.netTorque() > 0, "the control should run");
		assertEquals(separate.netTorque(), shared.netTorque(), 1e-6);
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
		run(engine, 20, available * 8);
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
	void engineCarriesAnyLoadBelowItsRatedPeak() {
		EngineSimulation engine = engine(FLYWHEEL_INERTIA, single(new Tank(100000)));
		crank(engine, 10);
		run(engine, 30, 0);
		double freeRpm = engine.rpm();
		double[] curve = Dynamometer.torqueCurve(engine);
		double peak = Dynamometer.peakTorque(curve, freeRpm);
		System.out.printf("free %.0f rpm, curve %s, peak %.3f%n", freeRpm, java.util.Arrays.toString(curve), peak);
		assertTrue(peak > 0);

		// The engine lugs down into its torque band rather than stalling
		run(engine, 60, peak * 0.9);
		assertFalse(engine.isStopped(), "Engine should carry 90% of its rated peak");
		assertTrue(engine.rpm() < freeRpm, engine.rpm() + " should be below free speed " + freeRpm);

		// The dyno holds speed steady, so it rates a slow-burning lean charge a little under what a free-running engine
		// manages: its speed dips at top dead centre, where a late burn does the most work. Well beyond that it stalls.
		run(engine, 60, peak * 2);
		assertTrue(engine.isStopped(), "Engine can't carry far more than its rated peak");
	}

	@Test
	void unfuelledEngineIsRatedAtNothing() {
		EngineSimulation engine = engine(FLYWHEEL_INERTIA, single(new Tank(0)));
		crank(engine, 5);
		assertEquals(0, Dynamometer.peakTorque(Dynamometer.torqueCurve(engine), 256));
	}

	@Test
	void miscamshaftedEngineIsRatedAtNothing() {
		Camshaft cam = new Camshaft();
		cam.ratio = 1;
		EngineSimulation engine = engine(FLYWHEEL_INERTIA, cylinder(0, 0, cam, new Tank(1000)));
		assertEquals(0, Dynamometer.peakTorque(Dynamometer.torqueCurve(engine), 256));
	}

	private static EngineSimulation settledAt(double film) {
		EngineSimulation engine = engine(FLYWHEEL_INERTIA, single(new Tank(100000)));
		engine.lubrication()
			.setFilm(film);
		crank(engine, 15);
		run(engine, 60, 0);
		return engine;
	}

	@Test
	void dryEngineTopsOutFarSlowerThanAnOiledOne() {
		EngineSimulation oiled = settledAt(1);
		EngineSimulation dry = settledAt(0);
		System.out.printf("oiled %.0f RPM, dry %.0f RPM%n", oiled.rpm(), dry.rpm());
		assertFalse(oiled.isStopped());
		assertTrue(dry.rpm() < oiled.rpm() * 0.7, "dry " + dry.rpm() + " should be well below oiled " + oiled.rpm());
	}

	@Test
	void dryEngineHasLessTorqueToSpare() {
		double[] oiled = Dynamometer.torqueCurve(settledAt(1));
		double[] dry = Dynamometer.torqueCurve(settledAt(0));
		double oiledPeak = Dynamometer.peakTorque(oiled, 256);
		double dryPeak = Dynamometer.peakTorque(dry, 256);
		assertTrue(dryPeak < oiledPeak, "dry " + dryPeak + " should be below oiled " + oiledPeak);
		for (int i = 0; i < Dynamometer.SPEEDS.length; i++)
			assertTrue(dry[i] < oiled[i], "dry torque at " + Dynamometer.SPEEDS[i] + " RPM should be lower");
	}

	@Test
	void frictionFallsAsTheFilmBuilds() {
		EngineSimulation engine = engine(0);
		engine.restore(0, EngineSimulation.rpmToOmega(100), 1);
		double previous = Double.MAX_VALUE;
		for (double film : new double[] {0, 0.25, 0.5, 0.75, 1}) {
			engine.lubrication()
				.setFilm(film);
			assertTrue(engine.frictionTorque() < previous, "friction should fall as the film builds");
			previous = engine.frictionTorque();
		}
	}

	@Test
	void filmBuildsWhileOilIsSuppliedAndThinsWhenItIsNot() {
		Lubrication film = new Lubrication(0);
		OilSupply plenty = mb -> mb;
		for (int i = 0; i < 20 * 5; i++)
			film.update(0.05, 0.05, 4, plenty);
		assertEquals(1, film.film(), 1e-9, "should be fully filmed after a few seconds");

		// 300 revolutions with no oil at all is most of the way to dry
		for (int i = 0; i < 300; i++)
			film.update(1, 0.05, 4, OilSupply.NONE);
		assertTrue(film.film() < 0.5, "should be thinning, got " + film.film());
		for (int i = 0; i < 300; i++)
			film.update(1, 0.05, 4, OilSupply.NONE);
		assertEquals(0, film.film(), 1e-9);
	}

	@Test
	void filmHoldsAtWhatASupplyThatCannotKeepUpDelivers() {
		Lubrication film = new Lubrication(1);
		OilSupply half = mb -> mb / 2;
		for (int i = 0; i < 20000; i++)
			film.update(0.1, 0.05, 4, half);
		assertEquals(0.5, film.film(), 1e-9);
	}

	@Test
	void aStoppedEngineKeepsItsFilm() {
		Lubrication film = new Lubrication(0.8);
		film.update(0, 0.05, 4, OilSupply.NONE);
		assertEquals(0.8, film.film(), 1e-9);
	}

	@Test
	void pistonsAndRodsWithoutAHeadStillDragTheCrank() {
		// A crank with nothing working on it coasts a long way. Pistons and rods moving in headless cylinders stop it.
		CylinderSpec idle = CylinderSpec.CRUDE;
		double[] coastTime = new double[2];
		for (int i = 0; i < 2; i++) {
			EngineSimulation engine = new EngineSimulation(List.of(), 3 * JOURNAL_INERTIA + i * idle.inertia(),
				0.03 + i * idle.frictionStatic(), i * idle.frictionViscous());
			engine.restore(0, EngineSimulation.rpmToOmega(16), 1);
			while (!engine.isStopped() && coastTime[i] < 600) {
				engine.tickFree(0, MAX_OMEGA);
				coastTime[i] += EngineSimulation.TICK;
			}
		}
		assertTrue(coastTime[1] < coastTime[0] / 2, "dragged " + coastTime[1] + " s vs bare " + coastTime[0] + " s");
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
		CylinderSpec lowCompression = new CylinderSpec(7, 4 / 14d, 20, 20, 0.5, 0.02, 0.06, 0.08, 0.01, 0.2);
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
