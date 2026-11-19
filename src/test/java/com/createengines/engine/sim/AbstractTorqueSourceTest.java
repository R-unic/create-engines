package com.createengines.engine.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * The base class an addon extends to add a source to an engine: it has to give the quiet answer to every question the
 * crank asks, work out friction the way a cylinder does, remember what the engine tells it, and carry a source that
 * only says what work it does.
 */
class AbstractTorqueSourceTest {

	private static final double MAX_OMEGA = EngineSimulation.rpmToOmega(256);

	@AfterEach
	void resetTuning() {
		Tuning.reset();
	}

	/** The smallest useful source: a constant torque while it is switched on, which also says it is firing. */
	private static class Motor extends AbstractTorqueSource {
		final double torque;
		boolean on = true;

		Motor(double torque, double inertia, double frictionStatic, double frictionViscous) {
			super(inertia, frictionStatic, frictionViscous);
			this.torque = torque;
		}

		@Override
		public double advance(double angle, double rotaryAngle, int direction, double delta, double dt) {
			return on ? torque * Math.toRadians(delta) : 0;
		}

		@Override
		public double availableWork() {
			return on ? torque * Math.toRadians(SimMath.CYCLE) : 0;
		}

		@Override
		public boolean fired() {
			return on;
		}

		double airPressureSeen() {
			return airPressure();
		}

		double airTemperatureSeen() {
			return airTemperature();
		}

		double coldnessSeen() {
			return coldness();
		}

		int directionSeen() {
			return direction();
		}
	}

	private static EngineSimulation bare(double rpm) {
		EngineSimulation sim = new EngineSimulation(List.of(), 1, 0);
		sim.restore(0, EngineSimulation.rpmToOmega(rpm), 1);
		return sim;
	}

	private static void run(EngineSimulation sim, int ticks) {
		for (int i = 0; i < ticks; i++)
			sim.tickFree(0, MAX_OMEGA);
	}

	@Test
	void aSourceThatOnlySaysWhatWorkItDoesAnswersEverythingElseQuietly() {
		AbstractTorqueSource quiet = new AbstractTorqueSource() {
			@Override
			public double advance(double angle, double rotaryAngle, int direction, double delta, double dt) {
				return 0;
			}

			@Override
			public double availableWork() {
				return 0;
			}
		};
		assertEquals(0, quiet.inertia(), 0);
		assertEquals(0, quiet.frictionStatic(), 0);
		assertEquals(0, quiet.frictionViscous(), 0);
		assertEquals(0, quiet.frictionAt(100, 1), 0);
		assertEquals(0, quiet.fuelUsedLastStep(), 0);
		assertEquals(0, quiet.exhaustHeatLastTick(), 0);
		assertTrue(quiet.unburnedFraction() < 0, "no fuel taken in, so it counts neither for nor against the oil");
		assertFalse(quiet.fired());
		assertEquals(List.of(), quiet.status());
		quiet.endTick();
	}

	@Test
	void frictionIsWorkedOutTheWayACylindersIs() {
		Motor motor = new Motor(0, 0.3, 0.08, 0.01);
		assertEquals(0.3, motor.inertia(), 0);
		assertEquals(0.08, motor.frictionStatic(), 0);
		assertEquals(0.01, motor.frictionViscous(), 0);
		CylinderSpec spec = new CylinderSpec(12, 0.2, 20, 20, 0.5, 0.02, 0.06, 0.08, 0.01, 0.3);
		for (double film : new double[] { 0, 0.4, 1 })
			for (double omega : new double[] { 0, 10, 80 })
				assertEquals(spec.frictionAt(omega, film), motor.frictionAt(omega, film), 1e-12,
					"film " + film + " omega " + omega);
		assertTrue(motor.frictionAt(50, 0.2) > motor.frictionAt(50, 1), "thin oil drags more");
	}

	@Test
	void whatTheEngineTellsTheSourceIsKeptAndStartsAtTheQuietValues() {
		Motor motor = new Motor(0, 0, 0, 0);
		assertEquals(1, motor.airPressureSeen(), 0);
		assertEquals(1, motor.airTemperatureSeen(), 0);
		assertEquals(0, motor.coldnessSeen(), 0);
		assertEquals(1, motor.directionSeen());
		motor.setIntake(1.8, 1.2);
		motor.setColdness(0.4);
		motor.setDirection(-1);
		assertEquals(1.8, motor.airPressureSeen(), 0);
		assertEquals(1.2, motor.airTemperatureSeen(), 0);
		assertEquals(0.4, motor.coldnessSeen(), 0);
		assertEquals(-1, motor.directionSeen());
	}

	@Test
	void aSourceOnTheCrankIsToldTheEnginesAirAndColdnessEveryTick() {
		EngineSimulation sim = bare(30);
		int[] intakeCalls = {0};
		Motor motor = new Motor(0, 0, 0, 0) {
			@Override
			public void setIntake(double pressure, double temperature) {
				intakeCalls[0]++;
				super.setIntake(pressure, temperature);
			}
		};
		sim.addSource(motor);
		run(sim, 5);
		assertEquals(5, intakeCalls[0], "once a tick, as the engine-wide average");
		assertEquals(1, motor.directionSeen());
	}

	@Test
	void aConstantTorqueSourceSpinsTheCrankUpAndCountsAsSustainingIt() {
		EngineSimulation sim = bare(60);
		Motor motor = new Motor(0.5, 0.2, 0.01, 0.001);
		sim.addSource(motor);
		double before = sim.rpm();
		run(sim, 100);
		assertTrue(sim.rpm() > before, "work on the crank spins it up: " + before + " to " + sim.rpm());
		assertTrue(sim.isSustaining(), "a source that says it fired keeps the engine sustaining itself");
		assertEquals(0, motor.fuelUsedLastStep(), 0);
	}

	@Test
	void theSameSourceSwitchedOffIsNotSustainingAndSlowsDown() {
		EngineSimulation sim = bare(60);
		Motor motor = new Motor(0.5, 0.2, 0.05, 0.002);
		motor.on = false;
		sim.addSource(motor);
		double before = sim.rpm();
		run(sim, 40);
		assertFalse(sim.isSustaining());
		assertTrue(sim.rpm() < before, "its friction slows the crank: " + before + " to " + sim.rpm());
	}

	@Test
	void theSourcesInertiaJoinsTheCranks() {
		EngineSimulation sim = bare(30);
		double base = sim.inertia();
		sim.addSource(new Motor(0, 0.7, 0, 0));
		assertEquals(base + 0.7, sim.inertia(), 1e-12);
	}
}
