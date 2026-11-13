package com.createengines.engine.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Fuzzes the whole simulation with random engines, random inputs and absurd inputs, and checks that nothing ever goes
 * non-finite, escapes its physical bounds, asks a supply for something nonsensical or becomes nondeterministic, and that
 * a tick costs about the same however fast the crank turns.
 */
class SimStressTest {

	private static final double MAX_OMEGA = EngineSimulation.rpmToOmega(256);

	@AfterEach
	void resetTuning() {
		Tuning.reset();
	}

	/** A supply that checks what it is asked for, and misbehaves the way a real tank does: runs dry, swaps fuel, flickers. */
	static final class FuzzSupply implements FuelSupply {
		final Random random;
		final FuelSpec[] fuels = {FuelSpec.PLANT_OIL, FuelSpec.DIESEL, FuelSpec.GASOLINE, FuelSpec.ETHANOL};
		FuelSpec fuel;
		double amount;
		final boolean flicker;
		final boolean unlimited;
		double drawn;

		FuzzSupply(Random random) {
			this.random = random;
			this.fuel = fuels[random.nextInt(fuels.length)];
			this.amount = random.nextInt(4) == 0 ? random.nextInt(200) : 1e9;
			this.unlimited = random.nextInt(3) == 0;
			this.flicker = random.nextInt(5) == 0;
		}

		@Override
		public double draw(double mb) {
			assertTrue(Double.isFinite(mb) && mb >= 0, "asked a supply for " + mb);
			double delivered = unlimited ? mb : Math.min(mb, amount);
			amount -= unlimited ? 0 : delivered;
			drawn += delivered;
			return delivered;
		}

		@Override
		public FuelSpec fuel() {
			if (random.nextInt(2000) == 0)
				fuel = fuels[random.nextInt(fuels.length)];
			if (flicker && random.nextInt(4) == 0)
				return null;
			return amount > 0 ? fuel : null;
		}
	}

	static final class FlakyIgnition implements IgnitionSource {
		final Random random;

		FlakyIgnition(Random random) {
			this.random = random;
		}

		@Override
		public boolean installed() {
			return true;
		}

		@Override
		public boolean powered() {
			return random.nextInt(8) != 0;
		}

		@Override
		public boolean spark() {
			return random.nextInt(8) != 0;
		}
	}

	static final class Rig {
		final EngineSimulation engine;
		final List<CylinderModel> cylinders = new ArrayList<>();
		final String description;
		final Random random;

		Rig(long seed) {
			random = new Random(seed);
			int count = 1 + random.nextInt(16);
			boolean rotary = random.nextInt(6) == 0;
			List<Camshaft> cams = new ArrayList<>();
			for (int i = 0; i < 1 + random.nextInt(3); i++)
				cams.add(new Camshaft());
			for (int i = 0; i < count; i++) {
				CylinderSpec spec = CylinderSpec.build(random.nextBoolean(), random.nextBoolean(), random.nextBoolean(),
					random.nextBoolean());
				CylinderModel cylinder;
				if (rotary) {
					cylinder = CylinderModel.rotary(CylinderSpec.buildRotary(random.nextBoolean()), CamProfile.ROTARY,
						random.nextInt(8) * 45, i % 3);
				} else {
					cylinder = new CylinderModel(spec, CamProfile.BASIC, random.nextInt(8) * 90 + random.nextInt(3) * 0.37,
						cams.get(random.nextInt(cams.size())), random.nextDouble() * 180);
				}
				cylinder.fuelSupply = new FuzzSupply(random);
				if (random.nextInt(3) == 0)
					cylinder.pilotSupply = new FuzzSupply(random);
				if (random.nextBoolean())
					cylinder.ignition = random.nextBoolean() ? IgnitionSource.UNLIMITED : new FlakyIgnition(random);
				cylinder.rack = random.nextDouble();
				cylinder.metering = 0.1 + random.nextDouble() * 1.9;
				cylinder.fuelTrim = 0.5 + random.nextDouble();
				cylinder.intakeFactor = random.nextDouble();
				cylinder.exhaustFactor = random.nextDouble();
				cylinder.governor = random.nextDouble();
				cylinder.airMatch = random.nextInt(3) == 0 ? random.nextDouble() * 1.5 : 0;
				cylinders.add(cylinder);
			}
			double inertia = 0.1 + random.nextDouble() * 5;
			double friction = random.nextDouble() * 0.2;
			engine = new EngineSimulation(cylinders, inertia, friction, random.nextDouble() * 0.01);
			for (CylinderModel cylinder : cylinders)
				cylinder.resetGas(cylinder.pistonAngle(0, 1));
			int turbos = random.nextInt(4);
			for (int i = 0; i < turbos; i++) {
				Turbocharger turbo = new Turbocharger(subset(), subset(), random.nextDouble());
				turbo.limit = random.nextDouble() * Turbocharger.MAX_LIMIT;
				turbo.dump = random.nextInt(4) == 0;
				turbo.variableGeometry = random.nextBoolean();
				turbo.antiLag = random.nextInt(Turbocharger.MAX_ANTI_LAG + 1);
				turbo.offThrottle = random.nextBoolean();
				turbo.parallelCompressor = random.nextBoolean();
				turbo.parallelTurbine = random.nextBoolean();
				turbo.antiLagFuel = new FuzzSupply(random);
				engine.addTurbo(turbo);
			}
			for (int i = 0; i < random.nextInt(3); i++) {
				Supercharger blower = new Supercharger(subset());
				blower.limit = random.nextDouble() * Supercharger.MAX_LIMIT;
				blower.rpm = random.nextDouble() * 400;
				blower.driveRatio = random.nextDouble() * 4;
				blower.dump = random.nextInt(4) == 0;
				blower.parallel = random.nextBoolean();
				engine.addSupercharger(blower);
			}
			for (int i = 0; i < random.nextInt(3); i++)
				engine.addIntercooler(new Intercooler(subset()));
			engine.setThermalLive(random.nextBoolean());
			engine.thermal().setTemperature(random.nextDouble());
			engine.lubrication().setFilm(random.nextDouble());
			engine.lubrication().setDilution(random.nextDouble() * Lubrication.MAX_DILUTION);
			engine.lubrication().setCondition(random.nextDouble());
			engine.setWashesOil(random.nextBoolean());
			if (random.nextInt(3) == 0)
				engine.trackSwing();
			description = "seed " + seed + ": " + count + (rotary ? " rotor chambers" : " cylinders") + ", "
				+ engine.turbos().size() + " turbos, " + engine.superchargers().size() + " blowers, "
				+ engine.intercoolers().size() + " intercoolers";
		}

		private List<CylinderModel> subset() {
			List<CylinderModel> out = new ArrayList<>();
			for (CylinderModel cylinder : cylinders)
				if (random.nextBoolean())
					out.add(cylinder);
			if (out.isEmpty())
				out.add(cylinders.get(random.nextInt(cylinders.size())));
			return out;
		}
	}

	private static void finite(String what, double value, String context) {
		assertTrue(Double.isFinite(value), what + " is " + value + " (" + context + ")");
	}

	private static void check(Rig rig, double maxOmega, boolean driven, int tick) {
		EngineSimulation e = rig.engine;
		String ctx = rig.description + ", tick " + tick;
		finite("omega", e.omega(), ctx);
		finite("angle", e.angle(), ctx);
		finite("rotaryAngle", e.rotaryAngle(), ctx);
		finite("rpm", e.rpm(), ctx);
		finite("netTorque", e.netTorque(), ctx);
		finite("indicatedTorque", e.indicatedTorque(), ctx);
		finite("ripple", e.ripple(), ctx);
		finite("fuelRate", e.fuelRate(), ctx);
		finite("availableTorque", e.availableTorque(), ctx);
		finite("crankingTorque", e.crankingTorque(), ctx);
		finite("friction", e.frictionTorque(), ctx);
		finite("film", e.lubrication().film(), ctx);
		finite("dilution", e.lubrication().dilution(), ctx);
		finite("condition", e.lubrication().condition(), ctx);
		finite("temperature", e.thermal().temperature(), ctx);
		assertTrue(e.omega() >= 0, "negative omega " + e.omega() + " (" + ctx + ")");
		if (!driven)
			assertTrue(e.omega() <= maxOmega * (1 + 1e-9), "omega " + e.omega() + " over ceiling " + maxOmega + " (" + ctx + ")");
		assertTrue(e.angle() >= 0 && e.angle() < SimMath.CYCLE + 1e-9, "angle " + e.angle() + " (" + ctx + ")");
		assertTrue(e.lubrication().film() >= -1e-9 && e.lubrication().film() <= 1 + 1e-9, "film " + e.lubrication().film() + " (" + ctx + ")");
		assertTrue(e.lubrication().dilution() >= -1e-9 && e.lubrication().dilution() <= Lubrication.MAX_DILUTION + 1e-9,
			"dilution " + e.lubrication().dilution() + " (" + ctx + ")");
		assertTrue(e.lubrication().condition() >= -1e-9 && e.lubrication().condition() <= 1 + 1e-9,
			"condition " + e.lubrication().condition() + " (" + ctx + ")");
		for (CylinderModel c : e.cylinders()) {
			CycleStats s = c.lastCycle();
			finite("work", s.work, ctx);
			finite("fuelInjected", s.fuelInjected, ctx);
			finite("fuelBurned", s.fuelBurned, ctx);
			finite("peakPressure", s.peakPressure, ctx);
			finite("compressionTemp", s.compressionTemp, ctx);
			finite("intakePressure", c.intakePressure, ctx);
			finite("intakeTemperature", c.intakeTemperature, ctx);
			finite("exhaustPressure", c.exhaustPressure, ctx);
			finite("exhaustHeat", c.exhaustHeatLastTick(), ctx);
			assertTrue(s.fuelBurned <= s.fuelInjected + 1e-6, "burned more than injected (" + ctx + ")");
			assertTrue(s.fuelInjected >= 0 && s.fuelBurned >= 0, "negative fuel (" + ctx + ")");
		}
		for (Turbocharger t : e.turbos()) {
			finite("spool", t.spool(), ctx);
			finite("boost", t.boost(), ctx);
			finite("backPressure", t.backPressure(), ctx);
			assertTrue(t.boost() >= -1e-9 && t.boost() <= Turbocharger.MAX_LIMIT + 1e-6, "turbo boost " + t.boost() + " (" + ctx + ")");
		}
		for (Supercharger b : e.superchargers()) {
			finite("blower boost", b.boost(), ctx);
			assertTrue(b.boost() >= -1e-9 && b.boost() <= Supercharger.MAX_LIMIT + 1e-6, "blower boost " + b.boost() + " (" + ctx + ")");
		}
	}

	@Test
	void randomEnginesUnderRandomDrivingStayFiniteAndBounded() {
		for (int seed = 0; seed < 250; seed++) {
			Tuning.reset();
			Rig rig = new Rig(seed);
			Random r = new Random(seed * 31L + 7);
			Tuning.forcedInduction = r.nextInt(4) != 0;
			Tuning.sparkFouling = r.nextBoolean();
			Tuning.oilDilution = r.nextBoolean();
			Tuning.oilAgeing = r.nextBoolean();
			Tuning.engineTemperature = r.nextInt(4) != 0;
			Tuning.pilotInjection = r.nextBoolean();
			Tuning.heatRecoveryEnabled = r.nextBoolean();
			double maxOmega = EngineSimulation.rpmToOmega(r.nextBoolean() ? 256 : 16 + r.nextInt(2000));
			int tick = 0;
			for (int segment = 0; segment < 8; segment++) {
				int mode = r.nextInt(3);
				int length = 20 + r.nextInt(120);
				double load = r.nextInt(4) == 0 ? 0 : (r.nextDouble() - 0.2) * r.nextInt(40);
				double floor = EngineSimulation.rpmToOmega(r.nextInt(5) * 16);
				int direction = r.nextBoolean() ? 1 : -1;
				double drivenRpm = r.nextDouble() * 300;
				for (int i = 0; i < length; i++, tick++) {
					switch (mode) {
						case 0 -> rig.engine.tickFree(load, maxOmega);
						case 1 -> rig.engine.tickCranked(load, maxOmega, floor, direction);
						default -> rig.engine.tickDriven(EngineSimulation.rpmToOmega(drivenRpm), direction);
					}
					check(rig, maxOmega, mode == 2, tick);
					if (r.nextInt(150) == 0)
						rig.engine.ageOil(r.nextBoolean());
					if (r.nextInt(300) == 0)
						rig.engine.lubrication().refresh(r.nextDouble() * 500);
					if (r.nextInt(300) == 0)
						rig.engine.setDirection(r.nextBoolean() ? 1 : -1);
					if (r.nextInt(400) == 0)
						rig.engine.restore(r.nextDouble() * 2000 - 600, r.nextDouble() * 20, r.nextBoolean() ? 1 : -1);
				}
			}
		}
	}

	@Test
	void absurdInputsDoNotProduceNonFiniteState() {
		double[] loads = {0, 1e-12, 1, 1e3, 1e9, -1, -1e3, -1e9};
		double[] ceilings = {0, 1e-6, 1, EngineSimulation.rpmToOmega(256), EngineSimulation.rpmToOmega(65536)};
		for (double load : loads)
			for (double ceiling : ceilings) {
				Rig rig = new Rig(99);
				rig.engine.restore(0, 5, 1);
				for (int i = 0; i < 100; i++) {
					rig.engine.tickFree(load, ceiling);
					check(rig, ceiling, false, i);
				}
				for (int i = 0; i < 100; i++) {
					rig.engine.tickCranked(load, ceiling, Math.min(ceiling, EngineSimulation.rpmToOmega(65536)), -1);
					check(rig, ceiling, false, 100 + i);
				}
			}
		for (double rpm : new double[] {0, 1e-9, 1, 1e4, 65536}) {
			Rig rig = new Rig(100);
			for (int i = 0; i < 60; i++) {
				rig.engine.tickDriven(EngineSimulation.rpmToOmega(rpm), i % 2 == 0 ? 1 : -1);
				check(rig, Double.MAX_VALUE, true, i);
			}
		}
	}

	@Test
	void extremeEngineConstantsStayFinite() {
		double[][] constants = {{1e-6, 0}, {1e-6, 5}, {1e9, 0}, {1e9, 1e3}, {0.5, 1e6}, {1e-3, 1e-9}};
		for (double[] c : constants) {
			List<CylinderModel> cylinders = new ArrayList<>();
			CylinderModel cylinder = new CylinderModel(CylinderSpec.CRUDE, CamProfile.BASIC, 0, new Camshaft(), 0);
			cylinder.fuelSupply = new FuzzSupply(new Random(1));
			cylinders.add(cylinder);
			EngineSimulation engine = new EngineSimulation(cylinders, c[0], c[1]);
			for (int i = 0; i < 200; i++) {
				engine.tickCranked(0, MAX_OMEGA, EngineSimulation.rpmToOmega(32), 1);
				assertTrue(Double.isFinite(engine.omega()) && engine.omega() <= MAX_OMEGA * (1 + 1e-9),
					"inertia " + c[0] + " friction " + c[1] + " omega " + engine.omega());
				assertTrue(Double.isFinite(engine.netTorque()), "net torque, inertia " + c[0] + " friction " + c[1]);
			}
		}
	}

	@Test
	void noCylindersIsHarmless() {
		EngineSimulation engine = new EngineSimulation(List.of(), 1, 0.02);
		for (int i = 0; i < 100; i++) {
			engine.tickCranked(0, MAX_OMEGA, EngineSimulation.rpmToOmega(32), 1);
			engine.tickFree(1, MAX_OMEGA);
			engine.tickDriven(EngineSimulation.rpmToOmega(64), 1);
			assertTrue(Double.isFinite(engine.omega()) && Double.isFinite(engine.netTorque()));
		}
		assertFalse(engine.isSustaining());
		EngineSimulation zeroInertia = new EngineSimulation(List.of(), 0, 0);
		for (int i = 0; i < 10; i++) {
			zeroInertia.tickCranked(0, MAX_OMEGA, EngineSimulation.rpmToOmega(32), 1);
			assertTrue(Double.isFinite(zeroInertia.omega()), "zero-inertia empty crank: omega " + zeroInertia.omega());
		}
	}

	@Test
	void sameSeedGivesBitIdenticalRuns() {
		for (int seed = 0; seed < 25; seed++) {
			double[] first = trace(seed);
			double[] second = trace(seed);
			assertEquals(first.length, second.length);
			for (int i = 0; i < first.length; i++)
				assertEquals(Double.doubleToLongBits(first[i]), Double.doubleToLongBits(second[i]),
					"seed " + seed + " diverged at sample " + i);
		}
	}

	private static double[] trace(int seed) {
		Tuning.reset();
		Rig rig = new Rig(seed);
		double[] out = new double[300 * 3];
		for (int i = 0; i < 300; i++) {
			rig.engine.tickCranked(0.5, MAX_OMEGA, EngineSimulation.rpmToOmega(i < 100 ? 32 : 0), 1);
			out[i * 3] = rig.engine.omega();
			out[i * 3 + 1] = rig.engine.netTorque();
			out[i * 3 + 2] = rig.engine.thermal().temperature();
		}
		return out;
	}

	/** A tick's cost must not climb with speed or blow up with cylinder count. */
	@Test
	void tickCostStaysFlatAcrossSpeedAndScalesWithCylinders() {
		double[] rpms = {16, 64, 256, 1024, 4096};
		int[] sizes = {1, 4, 8, 16};
		StringBuilder report = new StringBuilder("\nns/tick by rpm (columns) and cylinders (rows):\n");
		double[][] cost = new double[sizes.length][rpms.length];
		for (int s = 0; s < sizes.length; s++) {
			for (int p = 0; p < rpms.length; p++) {
				List<CylinderModel> cylinders = new ArrayList<>();
				Camshaft cam = new Camshaft();
				for (int i = 0; i < sizes[s]; i++) {
					double tdc = i * 720.0 / sizes[s];
					CylinderModel c = new CylinderModel(CylinderSpec.CRUDE, CamProfile.BASIC, tdc, cam, tdc / 2 % 180);
					c.fuelSupply = new FuzzSupply(new Random(i));
					cylinders.add(c);
				}
				EngineSimulation engine = new EngineSimulation(cylinders, 2, 0.02);
				engine.addTurbo(new Turbocharger(cylinders, cylinders, 1));
				engine.setThermalLive(true);
				double omega = EngineSimulation.rpmToOmega(rpms[p]);
				for (int i = 0; i < 200; i++)
					engine.tickDriven(omega, 1);
				int ticks = 400;
				long start = System.nanoTime();
				for (int i = 0; i < ticks; i++)
					engine.tickDriven(omega, 1);
				cost[s][p] = (System.nanoTime() - start) / (double) ticks;
			}
			report.append(String.format("%2d cyl:", sizes[s]));
			for (double v : cost[s])
				report.append(String.format(" %9.0f", v));
			report.append('\n');
		}
		System.out.print(report);
		for (int s = 0; s < sizes.length; s++) {
			assertTrue(cost[s][3] < cost[s][1] * 6 + 200_000, "tick cost climbs with speed for " + sizes[s] + " cyl: " + report);
			for (int p = 0; p < rpms.length; p++)
				assertTrue(cost[s][p] < 4_000_000, sizes[s] + " cyl at " + rpms[p] + " rpm costs " + cost[s][p] + " ns/tick");
		}
	}

	@Test
	void dynamometerCurvesAreFiniteForRandomEngines() {
		long worst = 0;
		for (int seed = 0; seed < 40; seed++) {
			Tuning.reset();
			Rig rig = new Rig(1000 + seed);
			rig.engine.tickCranked(0, MAX_OMEGA, EngineSimulation.rpmToOmega(48), 1);
			long start = System.nanoTime();
			double[] curve = Dynamometer.torqueCurve(rig.engine);
			long took = System.nanoTime() - start;
			worst = Math.max(worst, took);
			assertEquals(Dynamometer.pointCount(Dynamometer.DEFAULT_MAX_RPM), curve.length, rig.description);
			for (double torque : curve)
				finite("dyno torque", torque, rig.description);
			double[] limited = Dynamometer.torqueCurve(rig.engine, 8 + seed * 5);
			for (double torque : limited)
				finite("limited dyno torque", torque, rig.description);
		}
		System.out.println("slowest full torque curve: " + worst / 1_000_000 + " ms");
		assertTrue(worst < 30_000_000_000L, "a torque curve took " + worst / 1_000_000 + " ms");
	}

	@Test
	void governorSurvivesAbsurdInputs() {
		Governor governor = new Governor();
		double[] values = {0, 1e-9, 1, 20, 256, 1e6, -5};
		for (double rpm : values)
			for (double target : values)
				for (double dt : new double[] {0, 0.05, 1, 100}) {
					governor.seed(0.5, rpm, target);
					double out = governor.update(rpm, target, dt);
					assertTrue(Double.isFinite(out), "governor gave " + out + " for rpm " + rpm + " target " + target + " dt " + dt);
					assertTrue(out >= -1e-9 && out <= 1 + 1e-9, "governor out of range: " + out);
					assertTrue(Governor.opening(out) >= 0);
					assertTrue(Governor.signal(out) >= 0 && Governor.signal(out) <= 15);
				}
		for (int s = 0; s <= 15; s++)
			assertTrue(Double.isFinite(Governor.commanded(s * 16, s)));
	}

	@Test
	void copiesAreIndependentOfTheirSource() {
		Rig rig = new Rig(555);
		for (int i = 0; i < 200; i++)
			rig.engine.tickCranked(0, MAX_OMEGA, EngineSimulation.rpmToOmega(48), 1);
		double omega = rig.engine.omega();
		double temperature = rig.engine.thermal().temperature();
		double film = rig.engine.lubrication().film();
		Dynamometer.torqueCurve(rig.engine);
		assertEquals(omega, rig.engine.omega(), 0, "a dyno run moved the live crank");
		assertEquals(temperature, rig.engine.thermal().temperature(), 0, "a dyno run changed the live temperature");
		assertEquals(film, rig.engine.lubrication().film(), 0, "a dyno run used the live oil");
	}
}
