package com.createengines.engine.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * The 2.0 engine-type seam: a {@link TorqueSource} that is not a cylinder drives, loads and heats the crank like any
 * other. The source here is a bare constant-torque motor, the simplest thing a pack could add.
 */
class TorqueSourceTest {

	private static final double MAX_OMEGA = EngineSimulation.rpmToOmega(256);

	@AfterEach
	void resetTuning() {
		Tuning.reset();
	}

	/** A motor that pushes the crank with a fixed torque and records what the crank tells it. */
	private static final class Motor implements TorqueSource {
		final double torque;
		final double inertia;
		final double friction;
		final double heat;
		boolean fires;
		int steps;
		int ticksClosed;
		int lastDirection;
		double lastColdness = -1;
		double lastAngle = -1;
		double lastRotaryAngle = -1;
		double fuelPerStep;
		double lastPressure = -1;
		double lastTemperature = -1;
		int intakeCalls;

		Motor(double torque, double inertia, double friction, double heat) {
			this.torque = torque;
			this.inertia = inertia;
			this.friction = friction;
			this.heat = heat;
		}

		@Override
		public double inertia() {
			return inertia;
		}

		@Override
		public double frictionStatic() {
			return friction;
		}

		@Override
		public double frictionViscous() {
			return 0;
		}

		@Override
		public double frictionAt(double omega, double film) {
			return friction;
		}

		@Override
		public double advance(double angle, double rotaryAngle, int direction, double delta, double dt) {
			steps++;
			lastAngle = angle;
			lastRotaryAngle = rotaryAngle;
			lastDirection = direction;
			return torque * Math.toRadians(delta);
		}

		@Override
		public double fuelUsedLastStep() {
			return fuelPerStep;
		}

		@Override
		public void endTick() {
			ticksClosed++;
		}

		@Override
		public double exhaustHeatLastTick() {
			return heat;
		}

		@Override
		public void setColdness(double coldness) {
			lastColdness = coldness;
		}

		@Override
		public void setDirection(int direction) {
			lastDirection = direction;
		}

		@Override
		public double unburnedFraction() {
			return -1;
		}

		@Override
		public boolean fired() {
			return fires;
		}

		@Override
		public void setIntake(double pressure, double temperature) {
			intakeCalls++;
			lastPressure = pressure;
			lastTemperature = temperature;
		}

		@Override
		public double availableWork() {
			return torque * Math.toRadians(SimMath.CYCLE);
		}
	}

	private static EngineSimulation bare() {
		return new EngineSimulation(List.of(), 1, 0);
	}

	private static void run(EngineSimulation sim, int ticks) {
		for (int i = 0; i < ticks; i++)
			sim.tickFree(0, MAX_OMEGA);
	}

	@Test
	void aSourceThatIsNotACylinderSpinsTheCrankUp() {
		EngineSimulation sim = bare();
		Motor motor = new Motor(5, 0, 0, 0);
		sim.addSource(motor);
		sim.restore(0, EngineSimulation.rpmToOmega(20), 1);
		double before = sim.omega();
		run(sim, 20);
		assertTrue(sim.omega() > before, "the motor adds energy every step");
		assertTrue(motor.steps > 20, "it is stepped many times a tick: " + motor.steps);
	}

	@Test
	void whatItPushesWithIsWhatTheEngineSpeedsUpBy() {
		// With no load and no friction the crank gains exactly the energy the motor puts in: dE = T * dtheta
		EngineSimulation sim = bare();
		sim.addSource(new Motor(4, 0, 0, 0));
		double w0 = EngineSimulation.rpmToOmega(30);
		sim.restore(0, w0, 1);
		sim.tickFree(0, MAX_OMEGA);
		double revolutions = sim.revolutionsLastTick();
		double energy = 4 * revolutions * 2 * Math.PI;
		double expected = Math.sqrt(w0 * w0 + 2 * energy / sim.inertia());
		assertEquals(expected, sim.omega(), expected * 1e-6);
	}

	@Test
	void itsInertiaJoinsTheCranks() {
		EngineSimulation sim = bare();
		double base = sim.inertia();
		sim.addSource(new Motor(0, 2.5, 0, 0));
		assertEquals(base + 2.5, sim.inertia(), 1e-12);
	}

	@Test
	void itsFrictionIsCountedInTheFrictionTorqueAndTheTorqueItCouldDeliver() {
		EngineSimulation sim = bare();
		Motor motor = new Motor(6, 0, 1.5, 0);
		sim.addSource(motor);
		sim.restore(0, EngineSimulation.rpmToOmega(60), 1);
		double plain = new EngineSimulation(List.of(), 1, 0).frictionTorque();
		assertEquals(plain + 1.5, sim.frictionTorque(), 1e-9);
		run(sim, 60);
		assertEquals(6 - sim.frictionTorque(), sim.availableTorque(), 1e-9);
	}

	@Test
	void theSourceIsToldTheAngleTheDirectionAndTheEndOfEveryTick() {
		EngineSimulation sim = bare();
		Motor motor = new Motor(1, 0, 0, 0);
		sim.addSource(motor);
		sim.restore(0, EngineSimulation.rpmToOmega(40), -1);
		run(sim, 5);
		assertEquals(5, motor.ticksClosed);
		assertEquals(-1, motor.lastDirection);
		assertTrue(motor.lastAngle >= 0 && motor.lastAngle < 720, "angle " + motor.lastAngle);
		assertTrue(motor.lastRotaryAngle >= 0 && motor.lastRotaryAngle < CylinderModel.ROTARY_CYCLE,
			"rotary angle " + motor.lastRotaryAngle);
		assertTrue(motor.lastColdness >= 0, "it is told how cold the block is every tick");
	}

	@Test
	void aSourceThatFiredKeepsTheEngineSustainingAndOneThatDidNotDoesNot() {
		for (boolean fires : new boolean[] { false, true }) {
			EngineSimulation sim = bare();
			Motor motor = new Motor(5, 0, 0, 0);
			motor.fires = fires;
			sim.addSource(motor);
			sim.restore(0, EngineSimulation.rpmToOmega(60), 1);
			run(sim, 80);
			assertEquals(fires, sim.isSustaining(), "fires=" + fires);
		}
	}

	@Test
	void theFuelASourceTakesCountsTowardTheFuelRate() {
		EngineSimulation withFuel = bare();
		Motor motor = new Motor(5, 0, 0, 0);
		motor.fuelPerStep = 0.01;
		withFuel.addSource(motor);
		EngineSimulation without = bare();
		without.addSource(new Motor(5, 0, 0, 0));
		withFuel.restore(0, EngineSimulation.rpmToOmega(60), 1);
		without.restore(0, EngineSimulation.rpmToOmega(60), 1);
		run(withFuel, 80);
		run(without, 80);
		assertTrue(withFuel.fuelRate() > 0, "fuel rate " + withFuel.fuelRate());
		assertEquals(0, without.fuelRate(), 0);
	}

	@Test
	void anAddedSourceIsNotACylinder() {
		EngineSimulation sim = bare();
		Motor motor = new Motor(1, 0, 0, 0);
		sim.addSource(motor);
		assertTrue(sim.cylinders().isEmpty(), "cylinders() stays the cylinders");
		assertEquals(1, sim.sources().size());
		assertSame(motor, sim.sources().get(0));
		assertThrows(UnsupportedOperationException.class, () -> sim.sources().clear());
	}

	@Test
	void cylindersAreSourcesToo() {
		CylinderModel cylinder = new CylinderModel(CylinderSpec.CRUDE, CamProfile.BASIC, 0, null, 0);
		EngineSimulation sim = new EngineSimulation(List.of(cylinder), 1, 0.1);
		sim.addSource(new Motor(1, 0, 0, 0));
		assertEquals(2, sim.sources().size());
		assertSame(cylinder, sim.sources().get(0));
		assertSame(cylinder, sim.cylinders().get(0));
		assertFalse(sim.cylinders().contains(sim.sources().get(1)));
	}

	@Test
	void aSourceOnAnEngineWithNoCylindersBreathesWhatTheBlowerMakes() {
		EngineSimulation sim = bare();
		Motor motor = new Motor(1, 0, 0, 0);
		sim.addSource(motor);
		Supercharger blower = new Supercharger(List.of());
		blower.rpm = Supercharger.DESIGN_RPM;
		blower.limit = Supercharger.MAX_LIMIT;
		sim.addSupercharger(blower);
		sim.restore(0, EngineSimulation.rpmToOmega(40), 1);
		run(sim, 200);
		assertTrue(blower.boost() > 0.2, "the blower built boost: " + blower.boost());
		assertEquals(1 + blower.boost(), motor.lastPressure, 1e-9);
		assertEquals(blower.chargeTemperature(), motor.lastTemperature, 1e-9);
	}

	@Test
	void theServersBoostStrengthScalesWhatASourceBreathes() {
		EngineSimulation sim = bare();
		Motor motor = new Motor(1, 0, 0, 0);
		sim.addSource(motor);
		Supercharger blower = new Supercharger(List.of());
		blower.rpm = Supercharger.DESIGN_RPM;
		blower.limit = Supercharger.MAX_LIMIT;
		sim.addSupercharger(blower);
		Tuning.boostStrength = 2;
		sim.restore(0, EngineSimulation.rpmToOmega(40), 1);
		run(sim, 200);
		assertEquals(1 + blower.boost() * 2, motor.lastPressure, 1e-9);
		assertEquals(Tuning.blend(blower.chargeTemperature(), 2), motor.lastTemperature, 1e-9);
		Tuning.forcedInduction = false;
		run(sim, 1);
		assertEquals(1, motor.lastPressure, 1e-9, "the server can switch forced induction off");
	}

	@Test
	void aColdBlockGivesASourceDenserAirToo() {
		EngineSimulation sim = bare();
		Motor motor = new Motor(1, 0, 0, 0);
		sim.addSource(motor);
		Supercharger blower = new Supercharger(List.of());
		blower.rpm = Supercharger.DESIGN_RPM;
		blower.limit = Supercharger.MAX_LIMIT;
		sim.addSupercharger(blower);
		sim.thermal().setTemperature((Thermal.COLD_BELOW + Thermal.COOL_NONE) / 2);
		double cool = sim.thermal().chargeFactor();
		assertTrue(cool < 1, "the block is cool enough to matter: " + cool);
		sim.restore(0, EngineSimulation.rpmToOmega(40), 1);
		run(sim, 200);
		assertEquals(blower.chargeTemperature() * cool, motor.lastTemperature, 1e-9);
	}

	@Test
	void aSourceAlongsideCylindersBreathesTheirAverageAir() {
		CylinderModel one = new CylinderModel(CylinderSpec.CRUDE, CamProfile.BASIC, 0, null, 0);
		CylinderModel two = new CylinderModel(CylinderSpec.CRUDE, CamProfile.BASIC, 0, null, 0);
		EngineSimulation sim = new EngineSimulation(List.of(one, two), 1, 0.1);
		Supercharger blower = new Supercharger(List.of(one));
		blower.rpm = Supercharger.DESIGN_RPM;
		blower.limit = Supercharger.MAX_LIMIT;
		sim.addSupercharger(blower);
		Motor motor = new Motor(1, 0, 0, 0);
		sim.addSource(motor);
		sim.restore(0, EngineSimulation.rpmToOmega(40), 1);
		run(sim, 200);
		assertTrue(one.intakePressure > two.intakePressure, "only the first cylinder is boosted");
		assertEquals((one.intakePressure + two.intakePressure) / 2, motor.lastPressure, 1e-9);
		assertEquals((one.intakeTemperature + two.intakeTemperature) / 2, motor.lastTemperature, 1e-9);
	}

	@Test
	void anEngineWithOnlyCylindersNeverCallsForTheIntakeOfSources() {
		CylinderModel cylinder = new CylinderModel(CylinderSpec.CRUDE, CamProfile.BASIC, 0, null, 0);
		EngineSimulation sim = new EngineSimulation(List.of(cylinder), 1, 0.1);
		sim.restore(0, EngineSimulation.rpmToOmega(40), 1);
		run(sim, 5);
		assertEquals(1, sim.sources().size());
	}

	@Test
	void theIntakeIsSetOnceATickNotOnceAStep() {
		EngineSimulation sim = bare();
		Motor motor = new Motor(1, 0, 0, 0);
		sim.addSource(motor);
		sim.restore(0, EngineSimulation.rpmToOmega(40), 1);
		run(sim, 7);
		assertEquals(7, motor.intakeCalls);
		assertEquals(1, motor.lastPressure, 1e-9, "no compressors: atmospheric");
	}

	@Test
	void aSourceSaysNothingToTheGogglesUnlessItOverridesStatus() {
		assertTrue(new Motor(1, 0, 0, 0).status().isEmpty());
	}

	@Test
	void aNullSourceIsRefused() {
		assertThrows(NullPointerException.class, () -> bare().addSource(null));
	}
}
