package com.createengines.engine.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * A source that is not a cylinder can sit in an intake or exhaust network the way a cylinder does: the engine's
 * superchargers, turbochargers and intercoolers set its air, and it is not also handed the engine-wide average.
 */
class BreatherTest {

	private static final double MAX_OMEGA = EngineSimulation.rpmToOmega(256);

	@AfterEach
	void resetTuning() {
		Tuning.reset();
	}

	/** A source that burns nothing, adds no torque and records the air it is given. */
	private static final class Lung implements Breather {
		double intakePressure = -1;
		double intakeTemperature = -1;
		double exhaustPressure = -1;
		double heat;
		int averageCalls;

		@Override
		public double inertia() {
			return 0;
		}

		@Override
		public double frictionStatic() {
			return 0;
		}

		@Override
		public double frictionViscous() {
			return 0;
		}

		@Override
		public double frictionAt(double omega, double film) {
			return 0;
		}

		@Override
		public double advance(double angle, double rotaryAngle, int direction, double delta, double dt) {
			return 0;
		}

		@Override
		public double fuelUsedLastStep() {
			return 0;
		}

		@Override
		public void endTick() {
		}

		@Override
		public double exhaustHeatLastTick() {
			return heat;
		}

		@Override
		public void setColdness(double coldness) {
		}

		@Override
		public void setDirection(int direction) {
		}

		@Override
		public double unburnedFraction() {
			return -1;
		}

		@Override
		public boolean fired() {
			return false;
		}

		@Override
		public double availableWork() {
			return 0;
		}

		@Override
		public void setIntake(double pressure, double temperature) {
			averageCalls++;
		}

		@Override
		public double intakePressure() {
			return intakePressure;
		}

		@Override
		public void setIntakePressure(double pressure) {
			intakePressure = pressure;
		}

		@Override
		public double intakeTemperature() {
			return intakeTemperature;
		}

		@Override
		public void setIntakeTemperature(double temperature) {
			intakeTemperature = temperature;
		}

		@Override
		public double exhaustPressure() {
			return exhaustPressure;
		}

		@Override
		public void setExhaustPressure(double pressure) {
			exhaustPressure = pressure;
		}
	}

	private static EngineSimulation bare() {
		EngineSimulation sim = new EngineSimulation(List.of(), 1, 0);
		sim.restore(0, EngineSimulation.rpmToOmega(30), 1);
		return sim;
	}

	private static void run(EngineSimulation sim, int ticks) {
		for (int i = 0; i < ticks; i++)
			sim.tickFree(0, MAX_OMEGA);
	}

	private static Supercharger blowerOn(Lung lung) {
		Supercharger blower = new Supercharger(List.of(lung));
		blower.rpm = Supercharger.DESIGN_RPM;
		return blower;
	}

	@Test
	void anAttachedSourceBreathesWhatTheBlowerOnItsNetworkMakes() {
		EngineSimulation sim = bare();
		Lung lung = new Lung();
		sim.addAttachedSource(lung);
		Supercharger blower = blowerOn(lung);
		sim.addSupercharger(blower);
		run(sim, 400);
		assertTrue(blower.boost() > 0.1, "the blower made boost: " + blower.boost());
		assertEquals(1 + blower.boost(), lung.intakePressure, 1e-9);
		assertEquals(blower.chargeTemperature(), lung.intakeTemperature, 1e-9);
		assertTrue(lung.intakeTemperature > 1, "compressing heats the air");
		assertEquals(1, lung.exhaustPressure, 0);
		assertEquals(0, lung.averageCalls, "it is not also given the engine's average");
	}

	@Test
	void aSourceThatIsNotAttachedKeepsGettingTheEngineWideAirAndNothingMore() {
		EngineSimulation sim = bare();
		Lung attached = new Lung();
		Lung loose = new Lung();
		sim.addAttachedSource(attached);
		sim.addSource(loose);
		sim.addSupercharger(blowerOn(attached));
		run(sim, 50);
		assertTrue(loose.averageCalls > 0, "the average still reaches a source with no port");
		assertEquals(-1, loose.intakePressure, 0, "and its pressure is not written");
	}

	@Test
	void anIntercoolerOnTheSameNetworkCoolsIt() {
		EngineSimulation hot = bare();
		Lung plain = new Lung();
		hot.addAttachedSource(plain);
		hot.addSupercharger(blowerOn(plain));
		EngineSimulation cooled = bare();
		Lung cold = new Lung();
		cooled.addAttachedSource(cold);
		cooled.addSupercharger(blowerOn(cold));
		cooled.addIntercooler(new Intercooler(List.of(cold)));
		run(hot, 400);
		run(cooled, 400);
		assertTrue(cold.intakeTemperature < plain.intakeTemperature, cold.intakeTemperature + " against " + plain.intakeTemperature);
		assertTrue(cold.intakePressure < plain.intakePressure, "the intercooler's restriction costs some boost");
		double expected = 1 + (plain.intakeTemperature - 1) * (1 - Intercooler.EFFECTIVENESS);
		assertEquals(expected, cold.intakeTemperature, 1e-9);
	}

	@Test
	void aTurbineSourceSpoolsATurboAndIsHeldUpByIt() {
		EngineSimulation sim = bare();
		Lung hotExhaust = new Lung();
		hotExhaust.heat = 5;
		Lung breathes = new Lung();
		sim.addAttachedSource(hotExhaust);
		sim.addAttachedSource(breathes);
		Turbocharger turbo = new Turbocharger(List.of(hotExhaust), List.of(breathes), 1);
		sim.addTurbo(turbo);
		run(sim, 600);
		assertTrue(turbo.boost() > 0, "its exhaust heat spun the turbine: " + turbo.boost());
		assertEquals(1 + turbo.boost(), breathes.intakePressure, 1e-9, "the compressor feeds the other source");
		assertEquals(turbo.backPressure(), hotExhaust.exhaustPressure, 1e-9, "and the turbine holds up this one's exhaust");
	}

	@Test
	void withNoCompressorsAnAttachedSourceStillBreathesThickAirAndThinAirAtAltitude() {
		EngineSimulation sim = bare();
		Lung lung = new Lung();
		sim.addAttachedSource(lung);
		run(sim, 5);
		assertEquals(1, lung.intakePressure, 0);
		sim.setAmbientDensity(0.5);
		run(sim, 5);
		assertEquals(0.5, lung.intakePressure, 1e-12);
	}

	@Test
	void anAttachedSourceIsOnTheCrankWithItsInertia() {
		EngineSimulation sim = bare();
		double base = sim.inertia();
		Lung lung = new Lung();
		sim.addAttachedSource(lung);
		assertTrue(sim.sources().contains(lung), "it is stepped like any source");
		assertEquals(base + lung.inertia(), sim.inertia(), 1e-12);
	}

	@Test
	void aCoolBlockGivesAnAttachedSourceADenserChargeWithOrWithoutCompressors() {
		EngineSimulation plain = bare();
		Lung open = new Lung();
		plain.addAttachedSource(open);
		plain.thermal().setTemperature(0.5);
		run(plain, 1);
		double cool = plain.thermal().chargeFactor();
		assertTrue(cool < 1, "an engine inside the cooling band has a bonus: " + cool);
		assertEquals(cool, open.intakeTemperature, 1e-12);

		EngineSimulation boosted = bare();
		Lung lung = new Lung();
		boosted.addAttachedSource(lung);
		Supercharger blower = blowerOn(lung);
		boosted.addSupercharger(blower);
		boosted.thermal().setTemperature(0.5);
		run(boosted, 400);
		assertEquals(blower.chargeTemperature() * boosted.thermal().chargeFactor(), lung.intakeTemperature, 1e-9);
	}

	@Test
	void parallelStagesShareASourceTheWayTheyShareACylinder() {
		EngineSimulation sim = bare();
		Lung lung = new Lung();
		sim.addAttachedSource(lung);
		Supercharger a = blowerOn(lung);
		Supercharger b = blowerOn(lung);
		a.parallel = true;
		b.parallel = true;
		sim.addSupercharger(a);
		sim.addSupercharger(b);
		run(sim, 400);
		assertEquals(1 + (a.boost() + b.boost()) / 2, lung.intakePressure, 1e-9);
	}

	@Test
	void aCopyOfAStageMapsItsSourcesAndDropsOnesWithNoCopy() {
		Lung one = new Lung();
		Lung two = new Lung();
		Lung copyOfOne = new Lung();
		Map<Breather, Breather> copies = new IdentityHashMap<>();
		copies.put(one, copyOfOne);
		Supercharger blower = new Supercharger(List.of(one, two));
		assertEquals(List.of(copyOfOne), blower.copyOnto(copies).compressor());
		assertEquals(List.of(copyOfOne), new Intercooler(List.of(one, two)).copyOnto(copies).charge());
		assertEquals(List.of(copyOfOne), new Turbocharger(List.of(one), List.of(one, two), 1).copyOnto(copies).turbine());
	}
}
