package com.createengines.engine.sim;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.createengines.engine.EngineMilestones;

/**
 * The shape of the whole progression, one stage at a time, in Create steam engines (16384 SU each): what an engine is
 * worth when a player first builds one, what each step up buys, and where the ceiling is. {@link CombustionOutputTest}
 * pins how far ahead of steam an engine is; this pins that every step along the way is a step up, that no upgrade is a
 * trap, and that the top of the ladder stays a top.
 * <p>
 * The stages, with the Create progression they are meant to sit beside:
 * <ul>
 *     <li><b>Andesite age</b>: crude cylinders on plant oil. Iron, copper and seeds only, no nether: the strongest power
 *     there is before a blaze burner, scaling with how many cylinders are built.</li>
 *     <li><b>Refining</b>: diesel, then gasoline or ethanol, each a clear gain over the last for a fuel that costs a
 *     little more to make.</li>
 *     <li><b>Brass age</b>: a turbocharger or a supercharger, the cheapest big gain on the ladder.</li>
 *     <li><b>Precision age</b>: precision parts, dear but steady, and the injector that feeds heavy boost.</li>
 *     <li><b>End game</b>: stacked boost on a big precision engine, bounded by thirst and by what an injector can feed.</li>
 * </ul>
 */
class ProgressionBalanceTest {

	private static final double SU_PER_TORQUE = 100;
	private static final double STEAM = EngineMilestones.STEAM_ENGINE;
	private static final CylinderSpec PRECISE = CylinderSpec.build(true, true, true, true);
	/** Ethanol as shipped in {@code data_maps/fluid/fuel.json}. */
	private static final FuelSpec ETHANOL = new FuelSpec(27, 3.4, 1.0);

	/** What an engine does at its best speed under Create's usual 256 RPM limit. */
	private record Run(double power, double rpm, double fuel) {

		double steamEngines() {
			return power / STEAM;
		}

		/** Fuel burned for each steam engine's worth of power, mB per second. */
		double thirst() {
			return fuel / steamEngines();
		}

		/** Work per fuel: SU of power for each mB per second. */
		double economy() {
			return power / fuel;
		}
	}

	/** Builds an in-line engine of {@code cylinders}, evenly fired, on the air-matched shot the dynamometer is run with. */
	private static final class Build {
		int cylinders = 12;
		CylinderSpec spec = CylinderSpec.CRUDE;
		FuelSpec fuel = FuelSpec.GASOLINE;
		boolean spark = true;
		int turbos;
		boolean blower;
		boolean intercooler;

		Build cylinders(int count) {
			cylinders = count;
			return this;
		}

		Build precise() {
			spec = PRECISE;
			return this;
		}

		Build precise(boolean bore, boolean head, boolean piston, boolean injector) {
			spec = CylinderSpec.build(bore, head, piston, injector);
			return this;
		}

		Build fuel(FuelSpec f, boolean sparked) {
			fuel = f;
			spark = sparked;
			return this;
		}

		Build turbos(int count) {
			turbos = count;
			return this;
		}

		Build blower() {
			blower = true;
			return this;
		}

		Build intercooler() {
			intercooler = true;
			return this;
		}

		Run run() {
			Camshaft cam = new Camshaft();
			List<CylinderModel> models = new ArrayList<>();
			double step = 720d / cylinders;
			for (int i = 0; i < cylinders; i++) {
				double fire = i * step;
				CylinderModel model = new CylinderModel(spec, CamProfile.BASIC, fire % 360, cam, fire / 2);
				model.fuelSupply = MixtureTest.unlimited(fuel);
				model.metering = 2;
				model.airMatch = 1;
				model.ignition = spark ? IgnitionSource.UNLIMITED : IgnitionSource.NONE;
				models.add(model);
			}
			EngineSimulation sim =
				new EngineSimulation(models, cylinders * 0.35 + 1, (cylinders * 2 + 1) * 0.01);
			for (CylinderModel model : models)
				model.resetGas(0);
			for (int t = 0; t < turbos; t++) {
				Turbocharger unit = new Turbocharger(models, models, 1);
				unit.limit = Turbocharger.DEFAULT_LIMIT;
				sim.addTurbo(unit);
			}
			if (blower) {
				Supercharger unit = new Supercharger(models);
				unit.rpm = Supercharger.DESIGN_RPM;
				sim.addSupercharger(unit);
			}
			if (intercooler)
				sim.addIntercooler(new Intercooler(models));
			return measure(sim);
		}
	}

	private static Run measure(EngineSimulation sim) {
		double[] curve = Dynamometer.torqueCurve(sim);
		double best = 0;
		double bestRpm = 0;
		for (int i = 0; i < curve.length; i++) {
			double power = curve[i] * Dynamometer.SPEEDS[i] * SU_PER_TORQUE;
			if (power > best) {
				best = power;
				bestRpm = Dynamometer.SPEEDS[i];
			}
		}
		for (CylinderModel c : sim.cylinders())
			c.resetGas(c.pistonAngle(0, 1));
		for (int t = 0; t < 600; t++)
			sim.tickDriven(EngineSimulation.rpmToOmega(bestRpm), 1);
		return new Run(best, bestRpm, sim.fuelRate());
	}

	private static Run plantOil(int cylinders) {
		return new Build().cylinders(cylinders).fuel(FuelSpec.PLANT_OIL, false).run();
	}

	// ---- Andesite age -------------------------------------------------------------------------------------------

	@Test
	void aSingleCrudeCylinderIsWorthBuilding() {
		Run one = plantOil(1);
		assertTrue(one.steamEngines() > 0.12, "one crude cylinder: " + one.steamEngines() + " steam engines");
	}

	@Test
	void sixCrudeCylindersOnPlantOilMatchASteamEngineWithoutTheNether() {
		Run six = plantOil(6);
		assertTrue(six.steamEngines() > 1, "six crude cylinders on plant oil: " + six.steamEngines());
	}

	@Test
	void everyCrudeCylinderAddsTheSameAgain() {
		double one = plantOil(1).power();
		double four = plantOil(4).power();
		double sixteen = plantOil(16).power();
		assertTrue(four > 3.8 * one && four < 4.2 * one, "four cylinders: " + four + " against one " + one);
		assertTrue(sixteen > 15 * one && sixteen < 17 * one, "sixteen cylinders: " + sixteen + " against one " + one);
	}

	@Test
	void plantOilAloneStaysAnEarlyGameCeiling() {
		Run sixteen = plantOil(16);
		assertTrue(sixteen.steamEngines() < 4, "sixteen crude cylinders on plant oil: " + sixteen.steamEngines());
	}

	@Test
	void earlyPowerIsCheapToFuel() {
		// A steam engine's worth of crude plant oil engine should burn well under two mB a second: about 9 seeds a minute
		Run twelve = plantOil(12);
		assertTrue(twelve.thirst() < 2, "mB per second for each steam engine's worth: " + twelve.thirst());
	}

	// ---- Refining -----------------------------------------------------------------------------------------------

	@Test
	void eachStepUpTheFuelLadderIsAClearGain() {
		Run oil = plantOil(8);
		Run diesel = new Build().cylinders(8).fuel(FuelSpec.DIESEL, false).run();
		Run gasoline = new Build().cylinders(8).run();
		assertTrue(diesel.power() > 1.15 * oil.power(), "diesel " + diesel.power() + " against plant oil " + oil.power());
		assertTrue(gasoline.power() > 1.25 * diesel.power(), "gasoline " + gasoline.power() + " against diesel " + diesel.power());
		assertTrue(diesel.economy() > oil.economy() && gasoline.economy() > diesel.economy(),
			"each fuel should also go further per mB");
	}

	@Test
	void ethanolIsAGoodSparkFuelNotADeadEnd() {
		Run gasoline = new Build().cylinders(8).run();
		Run ethanol = new Build().cylinders(8).fuel(ETHANOL, true).run();
		Run diesel = new Build().cylinders(8).fuel(FuelSpec.DIESEL, false).run();
		assertTrue(ethanol.power() > 0.8 * gasoline.power(), "ethanol " + ethanol.power() + " against gasoline " + gasoline.power());
		assertTrue(ethanol.power() > diesel.power(), "a spark fuel from sugar should beat diesel: " + ethanol.power());
		// What it gives up is volume: it is a thin fuel
		assertTrue(ethanol.fuel() > 1.2 * gasoline.fuel(), "ethanol should be thirstier by volume: " + ethanol.fuel());
	}

	@Test
	void ethanolKeepsUpWithGasolineOnceBoostArrives() {
		Run gasoline = new Build().precise().turbos(1).run();
		Run ethanol = new Build().precise().fuel(ETHANOL, true).turbos(1).run();
		assertTrue(ethanol.power() > 0.85 * gasoline.power(),
			"boosted ethanol " + ethanol.power() + " against gasoline " + gasoline.power());
	}

	@Test
	void aThinFuelNeedsABiggerShotButADenseOneDoesNot() {
		assertTrue(ETHANOL.injectorFlow() > 1.4 && ETHANOL.injectorFlow() < 1.6);
		assertTrue(FuelSpec.GASOLINE.injectorFlow() == 1 && FuelSpec.DIESEL.injectorFlow() == 1
			&& FuelSpec.PLANT_OIL.injectorFlow() == 1);
	}

	// ---- Brass age ----------------------------------------------------------------------------------------------

	@Test
	void aTurbochargerIsTheBiggestGainForTheLeastBuilding() {
		Run plain = new Build().run();
		Run turbo = new Build().turbos(1).run();
		Run precise = new Build().precise().run();
		assertTrue(turbo.power() > 1.5 * plain.power(), "a turbo: " + turbo.power() + " against " + plain.power());
		// Cheap boost on crude cylinders gets you to about what a whole set of precision parts does
		assertTrue(turbo.power() > 0.9 * precise.power(), "turbo " + turbo.power() + " against precision " + precise.power());
	}

	@Test
	void aSuperchargerHelpsWithoutWaitingForExhaust() {
		Run plain = new Build().run();
		Run blown = new Build().blower().run();
		assertTrue(blown.power() > 1.3 * plain.power(), "a supercharger: " + blown.power() + " against " + plain.power());
	}

	// ---- Precision age ------------------------------------------------------------------------------------------

	@Test
	void everyPrecisionPartPaysForItselfAlone() {
		Run crude = new Build().run();
		boolean[][] parts = {{true, false, false, false}, {false, true, false, false}, {false, false, true, false},
			{false, false, false, true}};
		for (boolean[] part : parts) {
			Run run = new Build().precise(part[0], part[1], part[2], part[3]).run();
			assertTrue(run.power() > 1.03 * crude.power(),
				"part " + java.util.Arrays.toString(part) + " made " + run.power() + " against " + crude.power());
		}
	}

	@Test
	void thePrecisionInjectorIsWhatFeedsHeavyBoost() {
		// Two turbos want more fuel than a plain injector can give; the wider nozzle is worth a good deal there
		Run plain = new Build().turbos(2).run();
		Run wide = new Build().precise(false, false, false, true).turbos(2).run();
		assertTrue(wide.power() > 1.15 * plain.power(), "wide injector " + wide.power() + " against plain " + plain.power());
		// Where nothing is short of fuel it is a small gain, so it has to be the boost that earns it
		Run plainNa = new Build().run();
		Run wideNa = new Build().precise(false, false, false, true).run();
		assertTrue(wideNa.power() < 1.15 * plainNa.power(), "a wide injector alone: " + wideNa.power());
	}

	@Test
	void precisionPartsStackWithBoostRatherThanReplacingIt() {
		Run crude = new Build().turbos(1).run();
		Run precise = new Build().precise().turbos(1).run();
		assertTrue(precise.power() > 1.4 * crude.power(), "precision with a turbo " + precise.power() + " against crude " + crude.power());
	}

	// ---- The way up has no traps ---------------------------------------------------------------------------------

	@Test
	void everyStepOnTheStandardLadderRaisesPower() {
		Build build = new Build().fuel(FuelSpec.PLANT_OIL, false);
		double last = build.run().power();
		String[] steps = {"diesel", "gasoline", "precision", "one turbo", "an intercooler", "a second turbo"};
		for (String step : steps) {
			switch (step) {
				case "diesel" -> build.fuel(FuelSpec.DIESEL, false);
				case "gasoline" -> build.fuel(FuelSpec.GASOLINE, true);
				case "precision" -> build.precise();
				case "one turbo" -> build.turbos(1);
				case "an intercooler" -> build.intercooler();
				default -> build.turbos(2);
			}
			double power = build.run().power();
			assertTrue(power > last, "after " + step + " the engine made " + power + ", not more than " + last);
			last = power;
		}
	}

	/** Every combination of boost an advancement asks for is a step up from the engine it was built on. */
	@Test
	void addingBoostToAPrecisionEngineNeverMakesItWeaker() {
		for (int cylinders : new int[] {8, 12, 16}) {
			Build base = new Build().cylinders(cylinders).precise();
			double plain = base.run().power();
			double oneTurbo = new Build().cylinders(cylinders).precise().turbos(1).run().power();
			double cooled = new Build().cylinders(cylinders).precise().turbos(1).intercooler().run().power();
			double twinCharged = new Build().cylinders(cylinders).precise().turbos(1).blower().run().power();
			double twinChargedCooled = new Build().cylinders(cylinders).precise().turbos(1).blower().intercooler().run().power();
			double twoTurbos = new Build().cylinders(cylinders).precise().turbos(2).run().power();
			double twoTurbosCooled = new Build().cylinders(cylinders).precise().turbos(2).intercooler().run().power();
			String at = cylinders + " cylinders: ";
			assertTrue(oneTurbo > plain, at + "a turbo " + oneTurbo + " after " + plain);
			assertTrue(cooled >= oneTurbo, at + "an intercooler " + cooled + " after " + oneTurbo);
			assertTrue(twinCharged > oneTurbo, at + "a supercharger " + twinCharged + " after " + oneTurbo);
			assertTrue(twinChargedCooled >= 0.99 * twinCharged, at + "an intercooler on both " + twinChargedCooled + " after " + twinCharged);
			assertTrue(twoTurbos > oneTurbo, at + "a second turbo " + twoTurbos + " after " + oneTurbo);
			assertTrue(twoTurbosCooled >= 0.99 * twoTurbos, at + "an intercooler on two turbos " + twoTurbosCooled + " after " + twoTurbos);
		}
	}

	/** Past what the injectors can feed, another stage of boost buys nothing, but it costs a sixth at most, never the engine. */
	@Test
	void overStackingBoostFlattensOutInsteadOfFallingOff() {
		for (int cylinders : new int[] {8, 12, 16}) {
			double three = new Build().cylinders(cylinders).precise().turbos(2).intercooler().run().power();
			double four = new Build().cylinders(cylinders).precise().turbos(2).blower().intercooler().run().power();
			assertTrue(four > 0.8 * three, cylinders + " cylinders: four stages " + four + " against three " + three);
		}
	}

	@Test
	void moreCylindersMeansMorePowerAtEveryStage() {
		for (int boost = 0; boost <= 2; boost++) {
			double last = 0;
			for (int cylinders : new int[] {4, 8, 12, 16}) {
				double power = new Build().cylinders(cylinders).precise().turbos(boost).run().power();
				assertTrue(power > last, cylinders + " cylinders with " + boost + " turbos made " + power + " after " + last);
				last = power;
			}
		}
	}

	// ---- The ceiling -------------------------------------------------------------------------------------------

	@Test
	void aFullHouseTwelveIsMagnificentButBounded() {
		Run run = new Build().precise().turbos(2).blower().intercooler().run();
		assertTrue(run.steamEngines() > 14, "a full-house straight-12: " + run.steamEngines() + " steam engines");
		assertTrue(run.steamEngines() < 24, "a full-house straight-12: " + run.steamEngines() + " steam engines");
	}

	@Test
	void aSixteenCylinderTwinTurboIsTheLargestSingleCrankshaft() {
		Run run = new Build().cylinders(16).precise().turbos(2).blower().intercooler().run();
		assertTrue(run.steamEngines() < 30, "the largest single crank: " + run.steamEngines() + " steam engines");
	}

	@Test
	void stackingBoostCostsMoreFuelThanItReturns() {
		Run one = new Build().precise().turbos(1).run();
		Run stacked = new Build().precise().turbos(2).blower().intercooler().run();
		assertTrue(stacked.power() > one.power(), "stacking should still pay: " + stacked.power());
		assertTrue(stacked.economy() < 0.75 * one.economy(),
			"stacked boost " + stacked.economy() + " SU per mB/s against one turbo " + one.economy());
	}

	@Test
	void theBestEconomyIsAModestlyBoostedPrecisionEngine() {
		Run crude = new Build().run();
		Run precise = new Build().precise().run();
		Run turbo = new Build().precise().turbos(1).run();
		Run stacked = new Build().precise().turbos(2).blower().intercooler().run();
		assertTrue(precise.economy() > crude.economy(), "precision should go further per mB than crude");
		assertTrue(turbo.economy() >= 0.98 * precise.economy(), "one turbo should not cost economy");
		assertTrue(stacked.economy() < turbo.economy(), "the full stack is the thirstiest");
	}

	@Test
	void aRotaryStaysBelowThePistonEngineOfTheSameWeightClass() {
		CylinderSpec rotary = CylinderSpec.buildRotary(false);
		List<CylinderModel> models = new ArrayList<>();
		for (int r = 0; r < 4; r++)
			for (int chamber = 0; chamber < 3; chamber++) {
				CylinderModel model = CylinderModel.rotary(rotary, CamProfile.ROTARY, 90d * r, chamber);
				model.fuelSupply = MixtureTest.unlimited(FuelSpec.GASOLINE);
				model.metering = 2;
				model.airMatch = 1;
				model.ignition = IgnitionSource.UNLIMITED;
				models.add(model);
			}
		EngineSimulation sim = new EngineSimulation(models, 4 * 0.8 + 1.0, (4 * 3 + 1) * 0.01);
		sim.restore(0, 0, 1);
		for (CylinderModel model : models)
			model.resetGas(model.pistonAngle(0, 1));
		Run fourRotors = measure(sim);
		Run eightPrecision = new Build().cylinders(8).precise().run();
		assertTrue(fourRotors.power() < eightPrecision.power(),
			"four rotors " + fourRotors.power() + " against eight precision cylinders " + eightPrecision.power());
	}
}
