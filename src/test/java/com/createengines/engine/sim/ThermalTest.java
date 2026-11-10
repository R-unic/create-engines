package com.createengines.engine.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ThermalTest {

	/** A coolant tank: finite, of a given quality. */
	private static final class Tank implements CoolantSupply {
		double mb;
		final double quality;
		double drawn;

		Tank(double mb, double quality) {
			this.mb = mb;
			this.quality = quality;
		}

		@Override
		public double draw(double amount) {
			double given = Math.min(mb, amount);
			mb -= given;
			drawn += given;
			return given;
		}

		@Override
		public double quality() {
			return mb > 0 ? quality : 0;
		}
	}

	@AfterEach
	void resetTuning() {
		Tuning.reset();
	}

	/** Temperature an engine of {@code cylinders} settles at, each sending {@code perCylinder} heat out per second. */
	private static double settle(double perCylinder, int cylinders, CoolantSupply coolant) {
		Thermal thermal = new Thermal(0);
		for (int i = 0; i < 20 * 300; i++)
			thermal.update(perCylinder * cylinders, cylinders, 0.05, coolant);
		return thermal.temperature();
	}

	@Test
	void anEngineWarmsUpFromColdInSecondsNotMinutes() {
		Thermal thermal = new Thermal(0);
		double second = 0;
		for (int i = 1; i <= 20 * 30; i++) {
			thermal.update(2.5 * 4, 4, 0.05, CoolantSupply.NONE);
			if (second == 0 && thermal.temperature() > Thermal.COLD_BELOW)
				second = i / 20d;
		}
		assertTrue(second > 0 && second < 15, "a crude four stops being cold after " + second + " s");
		assertTrue(thermal.temperature() > 0.55, "and is warm after half a minute: " + thermal.temperature());
	}

	@Test
	void aColdEngineDragsAndAWarmOneDoesNot() {
		assertEquals(1 + Thermal.MAX_COLD_FRICTION, new Thermal(0).drag(), 1e-9);
		assertEquals(1, new Thermal(Thermal.NORMAL).drag(), 1e-12);
		assertEquals(1, new Thermal(Thermal.COLD_BELOW).drag(), 1e-12);
		assertTrue(new Thermal(0.15).drag() > 1 && new Thermal(0.15).drag() < 1 + Thermal.MAX_COLD_FRICTION);
	}

	@Test
	void withoutCoolantAnEngineSettlesWithTheSquareRootOfItsHeat() {
		assertEquals(Thermal.NORMAL, settle(2.5, 8, CoolantSupply.NONE), 0.01, "a steady crude engine is at normal");
		double light = settle(1.0, 8, CoolantSupply.NONE);
		double heavy = settle(8.0, 8, CoolantSupply.NONE);
		assertTrue(light < Thermal.NORMAL && light > Thermal.COLD_BELOW, "light running is cooler but not cold: " + light);
		assertTrue(heavy > 1.0, "a boosted engine runs hot: " + heavy);
		// The size of the engine does not change where it settles, only how much it takes to heat
		assertEquals(settle(2.5, 2, CoolantSupply.NONE), settle(2.5, 16, CoolantSupply.NONE), 0.01);
	}

	@Test
	void anUncooledEngineEarnsNoBonusAtNormalHeatOrAbove() {
		assertEquals(0, new Thermal(Thermal.NORMAL).cool());
		assertEquals(0, new Thermal(1.4).cool());
		assertEquals(1, new Thermal(0.42).cool(), 1e-9);
		assertEquals(1, new Thermal(0).chargeFactor() < 1 ? 0 : 1, "a cold engine has not earned the bonus either");
		assertEquals(1 - Thermal.MAX_COOL_CHARGE, new Thermal(0.42).chargeFactor(), 1e-9);
	}

	@Test
	void coolantHoldsAnEngineCoolWhateverItsLoadAndNeverColdWithAThermostat() {
		for (double heat : new double[] {0.5, 1.6, 2.5, 4.6, 8, 14}) {
			double cooled = settle(heat, 12, new Tank(1e9, 1));
			double bare = settle(heat, 12, CoolantSupply.NONE);
			assertTrue(cooled <= bare + 1e-9, "coolant never warms an engine at " + heat);
			assertTrue(cooled >= Math.min(bare, Thermal.THERMOSTAT) - 1e-9, "the thermostat keeps it from running cold at " + heat);
		}
		assertTrue(settle(8, 12, new Tank(1e9, 1)) < Thermal.COOL_NONE - 0.1, "a boosted engine is held cool");
		assertTrue(settle(14, 12, new Tank(1e9, 1)) < Thermal.COOL_NONE, "even a flat-out one");
	}

	@Test
	void plainWaterCoolsWorseThanProperCoolantAndTheBestHeavyEnginesNeedTheGoodStuff() {
		double water = settle(14, 12, new Tank(1e9, 0.5));
		double coolant = settle(14, 12, new Tank(1e9, 1));
		double bare = settle(14, 12, CoolantSupply.NONE);
		assertTrue(coolant < water && water < bare, coolant + " " + water + " " + bare);
		assertTrue(new Thermal(coolant).cool() > new Thermal(water).cool(), "proper coolant earns more of the bonus");
		assertTrue(new Thermal(settle(2.5, 12, new Tank(1e9, 0.5))).cool() > 0.99, "but water is enough for a modest engine");
	}

	@Test
	void coolantIsSpentCarryingHeatAndRunsOutWithoutHurtingAnything() {
		Tank small = new Tank(150, 1);
		Thermal thermal = new Thermal(0.4);
		double early = 0;
		for (int i = 0; i < 20 * 400; i++) {
			thermal.update(8 * 12, 12, 0.05, small);
			if (i == 20 * 3)
				early = thermal.temperature();
		}
		assertEquals(150, small.drawn, 1e-6, "all of it is used");
		assertTrue(early < 0.6, "it held the engine cool while it lasted: " + early);
		assertEquals(settle(8, 12, CoolantSupply.NONE), thermal.temperature(), 0.01, "afterwards the engine is simply uncooled");
		assertEquals(0, thermal.coolantRate());
	}

	/** Coolant spent over {@code seconds} by {@code cylinders} each sending {@code perCylinder} heat a second out of the exhaust. */
	private static double spentOver(double seconds, double perCylinder, int cylinders) {
		Tank endless = new Tank(1e9, 1);
		Thermal thermal = new Thermal(Thermal.NORMAL);
		for (int i = 0; i < seconds * 20; i++)
			thermal.update(perCylinder * cylinders, cylinders, 0.05, endless);
		return endless.drawn;
	}

	@Test
	void aPanOfCoolantDoesNotLastForeverOnARunningEngine() {
		double hour = spentOver(3600, 2.0, 1);
		assertTrue(hour > 500, "even one gently running cylinder empties a 500 mB pan within the hour: " + hour);
		double halfHour = spentOver(1800, 2.0, 1);
		assertTrue(halfHour < 500, "but a small engine gets a good while out of it: " + halfHour);
		double sixteen = spentOver(300, 2.0, 16);
		assertTrue(sixteen > 500, "a sixteen is through a pan in five minutes: " + sixteen);
	}

	@Test
	void coolantUseGrowsWithTheHeatItCarries() {
		Tank light = new Tank(1e9, 1);
		Tank heavy = new Tank(1e9, 1);
		settle(2.5, 12, light);
		settle(14, 12, heavy);
		assertTrue(heavy.drawn > 4 * light.drawn, light.drawn + " against " + heavy.drawn);
	}

	@Test
	void theServerCanSwitchTemperatureOffOrScaleTheBonus() {
		Thermal cold = new Thermal(0);
		Thermal cool = new Thermal(0.42);
		Tuning.coolingBonus = 0.5;
		assertEquals(1 - Thermal.MAX_COOL_CHARGE / 2, cool.chargeFactor(), 1e-9);
		Tuning.engineTemperature = false;
		assertEquals(1, cold.drag());
		assertEquals(1, cool.chargeFactor());
	}

	/** Net torque of four crude diesels driven at 128 RPM with the block held at {@code temperature}. */
	private static double torqueAt(double temperature) {
		List<CylinderModel> models = new ArrayList<>();
		Camshaft cam = new Camshaft();
		for (int i = 0; i < 4; i++) {
			double fire = i * 180;
			CylinderModel model = new CylinderModel(CylinderSpec.CRUDE, CamProfile.BASIC, fire % 360, cam, fire / 2);
			model.fuelSupply = MixtureTest.unlimited(FuelSpec.DIESEL);
			model.airMatch = 1;
			models.add(model);
		}
		EngineSimulation sim = new EngineSimulation(models, 2.4, 0.09);
		sim.thermal().setTemperature(temperature);
		for (CylinderModel model : models)
			model.resetGas(0);
		for (int i = 0; i < 20 * 20; i++)
			sim.tickDriven(EngineSimulation.rpmToOmega(128), 1);
		return sim.netTorque();
	}

	@Test
	void aCoolEngineMakesSeveralPercentMorePowerAndAColdOneLessThanAWarmOne() {
		double warm = torqueAt(Thermal.NORMAL);
		double cool = torqueAt(0.45);
		double cold = torqueAt(0);
		assertTrue(cool > warm * 1.03 && cool < warm * 1.15, "cool " + cool + " against warm " + warm);
		assertTrue(cold < warm * 0.995, "cold " + cold + " against warm " + warm);
	}

	@Test
	void aLiveEngineWarmsUpAndCoolantTakesItBelowWhereItWouldSettle() {
		double bare = liveEngineTemperature(CoolantSupply.NONE);
		double cooled = liveEngineTemperature(new Tank(1e9, 1));
		assertTrue(bare > Thermal.COOL_NONE, "an uncooled engine at speed is warm: " + bare);
		assertTrue(cooled < bare - 0.1, "cooled " + cooled + " against " + bare);
	}

	/** Temperature a sixteen-cylinder crude engine settles at, turned at {@code rpm} with the fuel rack at {@code rack}. */
	private static double sixteenAt(double rpm, double rack) {
		List<CylinderModel> models = new ArrayList<>();
		Camshaft cam = new Camshaft();
		for (int i = 0; i < 16; i++) {
			double fire = i * 720d / 16;
			CylinderModel model = new CylinderModel(CylinderSpec.CRUDE, CamProfile.BASIC, fire % 360, cam, fire / 2);
			model.fuelSupply = MixtureTest.unlimited(FuelSpec.DIESEL);
			model.rack = rack;
			models.add(model);
		}
		EngineSimulation sim = new EngineSimulation(models, 2.4 * 4, 0.09);
		sim.thermal().setTemperature(0);
		sim.setThermalLive(true);
		sim.setCoolant(CoolantSupply.NONE);
		for (CylinderModel model : models)
			model.resetGas(0);
		for (int i = 0; i < 20 * 300; i++)
			sim.tickDriven(EngineSimulation.rpmToOmega(rpm), 1);
		return sim.thermal().temperature();
	}

	@Test
	void aLightlyFuelledEngineStillWarmsThroughFromItsOwnFriction() {
		double starved = sixteenAt(67, 0.25);
		assertTrue(starved > Thermal.COLD_BELOW + 0.04, "a big engine turning slowly on little fuel is not stuck cold: " + starved);
		assertTrue(starved < sixteenAt(67, 1), "but it is still cooler than one burning a full shot");
	}

	@Test
	void frictionHeatLeavesTheFullThrottleCalibrationWhereItWas() {
		assertEquals(Thermal.NORMAL, settle(2.5, 8, CoolantSupply.NONE), 0.01);
		assertEquals(2.5, Thermal.heatIn(2.5, 2.5), 1e-12, "equal exhaust and friction heat add up to the same");
	}

	@Test
	void aStoppedEngineGivesUpItsHeatSlowlyAndStopsCooling() {
		Thermal thermal = new Thermal(Thermal.NORMAL);
		assertTrue(Thermal.isCooling(thermal.temperature(), false));
		assertTrue(!Thermal.isCooling(thermal.temperature(), true), "a turning engine is running, not cooling");
		double first = 0;
		double half = 0;
		for (int i = 1; i <= 20 * 600; i++) {
			thermal.update(0, 4, 0.05, CoolantSupply.NONE);
			if (half == 0 && thermal.temperature() < Thermal.NORMAL / 2)
				half = i / 20d;
			if (first == 0 && !Thermal.isCooling(thermal.temperature(), false))
				first = i / 20d;
		}
		assertTrue(half > 5 && half < 40, "it halves in " + half + " s");
		assertTrue(first > half && first < 600, "and has finished cooling after " + first + " s");
	}

	@Test
	void theTicksOfAContractingBlockGetSparserAsItCools() {
		assertEquals(0, Thermal.tickChance(0.05));
		double hot = Thermal.tickChance(Thermal.NORMAL);
		double warm = Thermal.tickChance(0.3);
		double nearly = Thermal.tickChance(Thermal.COOLING_FROM);
		assertTrue(hot > warm && warm > nearly && nearly > 0, hot + " " + warm + " " + nearly);
	}

	@Test
	void exhaustPartsGlowOnlyWhenTheBlockIsHot() {
		assertEquals(0, Thermal.glow(0.3));
		assertEquals(0, Thermal.glow(Thermal.GLOW_FROM));
		assertEquals(1, Thermal.glow(Thermal.GLOW_FROM + Thermal.GLOW_SPAN), 1e-9);
		assertTrue(Thermal.glow(0.7) > 0 && Thermal.glow(0.7) < 1);
		assertEquals(1, Thermal.glow(2));
	}

	@Test
	void aColdBlockBurnsFuelWorseAndSomeFuelsWorseThanOthers() {
		assertEquals(1, Thermal.burnFactor(0, 1.8), 1e-12);
		double gasoline = Thermal.burnFactor(1, FuelSpec.GASOLINE.coldSensitivity());
		double diesel = Thermal.burnFactor(1, FuelSpec.DIESEL.coldSensitivity());
		double oil = Thermal.burnFactor(1, FuelSpec.PLANT_OIL.coldSensitivity());
		double ethanol = Thermal.burnFactor(1, FuelSpec.ETHANOL.coldSensitivity());
		assertTrue(ethanol < oil && oil < diesel && diesel < gasoline && gasoline < 1,
			ethanol + " " + oil + " " + diesel + " " + gasoline);
		assertTrue(ethanol > 0.75, "cold costs a power, it does not stop the engine: " + ethanol);
		assertEquals(1, new FuelSpec(30, 2, 1.5).coldSensitivity(), "a datapack fuel is an ordinary one");
	}

	@Test
	void aColdEngineWarmsAtIdleAtLeastAsFastAsItWouldUnderLoad() {
		Thermal idle = new Thermal(0);
		Thermal bare = new Thermal(0.29);
		idle.update(0.6 * 4, 4, 0.05, CoolantSupply.NONE);
		double idleGain = idle.temperature();
		bare.update(0.6 * 4, 4, 0.05, CoolantSupply.NONE);
		double nearWarmGain = bare.temperature() - 0.29;
		assertTrue(idleGain > nearWarmGain, "the choke helps a cold block most: " + idleGain + " against " + nearWarmGain);
		// A warm engine gets no help
		Thermal warm = new Thermal(Thermal.NORMAL);
		Thermal reference = new Thermal(Thermal.NORMAL);
		warm.update(0.6 * 4, 4, 0.05, CoolantSupply.NONE);
		reference.update(0.6 * 4, 4, 0.05, CoolantSupply.NONE);
		assertEquals(reference.temperature(), warm.temperature(), 1e-12);
	}

	@Test
	void aHeatSourceTouchingTheEngineHoldsItWarmerWhenStoppedTheHotterTheSource() {
		double previous = -1;
		for (int level = HeatRecovery.NONE; level <= HeatRecovery.SEETHE; level++) {
			Thermal thermal = new Thermal(0);
			thermal.setPreheat(level);
			for (int i = 0; i < 20 * 900; i++)
				thermal.update(0, 4, 0.05, CoolantSupply.NONE);
			assertTrue(thermal.temperature() > previous, "level " + level + " should be warmer: " + thermal.temperature());
			previous = thermal.temperature();
			if (level == HeatRecovery.NONE)
				assertEquals(0, thermal.temperature(), 1e-9);
			if (level == HeatRecovery.SEETHE)
				assertTrue(thermal.temperature() < Thermal.NORMAL, "preheat never matches a running engine");
			if (level == HeatRecovery.KINDLE)
				assertTrue(thermal.temperature() > Thermal.COLD_BELOW, "a kindled source takes the cold off: " + thermal.temperature());
		}
	}

	private static double liveEngineTemperature(CoolantSupply coolant) {
		List<CylinderModel> models = new ArrayList<>();
		Camshaft cam = new Camshaft();
		for (int i = 0; i < 4; i++) {
			double fire = i * 180;
			CylinderModel model = new CylinderModel(CylinderSpec.CRUDE, CamProfile.BASIC, fire % 360, cam, fire / 2);
			model.fuelSupply = MixtureTest.unlimited(FuelSpec.DIESEL);
			model.airMatch = 1;
			models.add(model);
		}
		EngineSimulation sim = new EngineSimulation(models, 2.4, 0.09);
		sim.thermal().setTemperature(0);
		sim.setThermalLive(true);
		sim.setCoolant(coolant);
		for (CylinderModel model : models)
			model.resetGas(0);
		for (int i = 0; i < 20 * 120; i++)
			sim.tickDriven(EngineSimulation.rpmToOmega(160), 1);
		return sim.thermal().temperature();
	}
}
