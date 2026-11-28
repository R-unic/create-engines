package com.createengines.gametest;

import static com.createengines.gametest.EngineRigs.*;

import java.util.List;

import com.createengines.CreateEngines;
import com.createengines.content.battery.BatteryBlockEntity;
import com.createengines.content.crankshaft.CrankshaftBlockEntity;
import com.createengines.content.head.CylinderHeadBlockEntity;
import com.createengines.engine.CylinderStatus;
import com.createengines.engine.EngineState;
import com.createengines.engine.EngineSummary;
import com.createengines.engine.sim.CycleResult;
import com.createengines.engine.topology.EngineLayout;
import com.createengines.engine.topology.EngineScanner;
import com.createengines.engine.topology.CylinderFault;
import com.createengines.registry.CEBlocks;
import com.createengines.registry.CEFluids;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlock;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Spark ignition, plugs, batteries and the fuels engines run on. Test engines as in {@link EngineGameTests}. */
@GameTestHolder(CreateEngines.MOD_ID)
@PrefixGameTestTemplate(false)
public class FuelSparkGameTests {

	/** An alternator on the crank's own shaft charges the battery by cranking, which lets a spark engine start. */
	@GameTest(template = "empty", timeoutTicks = 1000)
	public static void sparkEngineStartsOnGasolineAndChargesItsBattery(GameTestHelper helper) {
		TestEngine.buildSpark(helper.getLevel(), helper.absolutePos(BlockPos.ZERO), true);
		helper.runAtTickTime(300, () -> {
			EngineSummary summary = summary(helper);
			helper.assertTrue(summary.firing() == 1,
				"Cylinder should fire from the plug under the starter, summary " + summary + ", cylinder " + cylinder(helper));
			BatteryBlockEntity battery = helper.getBlockEntity(TestEngine.BATTERY);
			helper.assertTrue(battery.charge() > 0, "Cranking should have charged the battery through the alternator");
			helper.setBlock(STARTER_POWER, Blocks.AIR);
		});
		helper.runAtTickTime(800, () -> {
			EngineSummary summary = summary(helper);
			helper.assertTrue(summary.state() == EngineState.RUNNING, "Spark engine should run on its own: " + summary);
			helper.assertTrue(cylinder(helper).result() == CycleResult.FIRED, "Cylinder should fire: " + cylinder(helper));
			CylinderHeadBlockEntity head = helper.getBlockEntity(HEAD);
			helper.assertTrue(head.tank()
				.getFluidAmount() < 250, "Engine should have burned gasoline");
			BatteryBlockEntity battery = helper.getBlockEntity(TestEngine.BATTERY);
			helper.assertTrue(battery.charge() > 20, "The turning engine should keep its battery charged");
			assertSounds(helper, false, 1);
			helper.succeed();
		});
	}

	/** Gasoline won't light by compression alone: without a plug the engine only turns over. */
	@GameTest(template = "empty", timeoutTicks = 700)
	public static void gasolineWithoutASparkPlugDoesNotLight(GameTestHelper helper) {
		buildSingleCylinder(helper, false);
		CylinderHeadBlockEntity head = helper.getBlockEntity(HEAD);
		head.tank()
			.fill(new FluidStack(CEFluids.GASOLINE.get()
				.getSource(), 250), FluidAction.EXECUTE);
		for (int tick = 100; tick <= 600; tick += 100)
			helper.runAtTickTime(tick, () -> helper.assertTrue(summary(helper).firing() == 0,
				"Gasoline lit with no spark plug: " + cylinder(helper)));
		helper.runAtTickTime(650, () -> {
			helper.assertTrue(cylinder(helper).result() == CycleResult.NO_IGNITION,
				"Fuel should be left unlit: " + cylinder(helper));
			helper.succeed();
		});
	}

	/** A spark engine flooded with fuel soots up its plug, and the cylinder reports it. */
	@GameTest(template = "empty", timeoutTicks = 1300)
	public static void aFloodedSparkEngineFoulsItsPlug(GameTestHelper helper) {
		TestEngine.buildSpark(helper.getLevel(), helper.absolutePos(BlockPos.ZERO), true);
		CylinderHeadBlockEntity head = helper.getBlockEntity(HEAD);
		head.metering.setValue(CylinderHeadBlockEntity.MAX_METERING);
		helper.runAtTickTime(300, () -> helper.setBlock(STARTER_POWER, Blocks.AIR));
		helper.runAtTickTime(1200, () -> {
			CylinderStatus status = cylinder(helper);
			helper.assertTrue(status.fouling() > 0.3f, "A flooded engine should soot its plug: " + status);
			helper.assertTrue(summary(helper).thinning() > 0, "Unburned fuel should thin the oil: " + summary(helper));
			helper.succeed();
		});
	}

	/** A plug with no battery on the engine is reported, and never fires. */
	@GameTest(template = "empty", timeoutTicks = 700)
	public static void sparkPlugWithoutABatteryNeverFires(GameTestHelper helper) {
		TestEngine.buildSpark(helper.getLevel(), helper.absolutePos(BlockPos.ZERO), false);
		for (int tick = 100; tick <= 600; tick += 100)
			helper.runAtTickTime(tick, () -> helper.assertTrue(summary(helper).firing() == 0,
				"Cylinder fired with no battery: " + cylinder(helper)));
		helper.runAtTickTime(650, () -> {
			helper.assertTrue(CylinderFault.NO_BATTERY.in(cylinder(helper).faults()),
				"Cylinder should report its missing battery: " + cylinder(helper));
			helper.assertTrue(cylinder(helper).result() == CycleResult.NO_SPARK,
				"Cylinder should report no spark: " + cylinder(helper));
			helper.succeed();
		});
	}

	/** Alternators touching each other form a chain: ones past the first still count as the engine's, down to the end of the chain. */
	@GameTest(template = "empty", timeoutTicks = 20)
	public static void alternatorsTouchingEachOtherAllChargeTheEngine(GameTestHelper helper) {
		TestEngine.buildSpark(helper.getLevel(), helper.absolutePos(BlockPos.ZERO), true);
		BlockPos second = TestEngine.ALTERNATOR.west();
		BlockPos third = second.west();
		BlockPos apart = third.west().west();
		helper.setBlock(second, TestEngine.axisX(CEBlocks.ALTERNATOR.getDefaultState()));
		helper.setBlock(third, TestEngine.axisX(CEBlocks.ALTERNATOR.getDefaultState()));
		helper.setBlock(apart, TestEngine.axisX(CEBlocks.ALTERNATOR.getDefaultState()));
		EngineLayout layout = EngineScanner.scan(helper.getLevel(), helper.absolutePos(PRIMARY));
		helper.assertTrue(layout != null, "The engine should scan");
		List<BlockPos> found = layout.alternators();
		helper.assertTrue(found.contains(helper.absolutePos(TestEngine.ALTERNATOR)), "The first alternator: " + found);
		helper.assertTrue(found.contains(helper.absolutePos(second)), "An alternator touching the first: " + found);
		helper.assertTrue(found.contains(helper.absolutePos(third)), "One touching that one: " + found);
		helper.assertFalse(found.contains(helper.absolutePos(apart)), "One with a gap is not connected: " + found);
		helper.succeed();
	}

	/** A spark engine runs only while its battery holds charge: drain one the engine cannot recharge and it quits. */
	@GameTest(template = "empty", timeoutTicks = 1500)
	public static void sparkEngineStopsWhenItsBatteryIsDrained(GameTestHelper helper) {
		TestEngine.buildSpark(helper.getLevel(), helper.absolutePos(BlockPos.ZERO), true);
		helper.runAtTickTime(300, () -> helper.setBlock(STARTER_POWER, Blocks.AIR));
		helper.runAtTickTime(600, () -> {
			helper.assertTrue(summary(helper).state() == EngineState.RUNNING, "Engine should be running first");
			BatteryBlockEntity battery = helper.getBlockEntity(TestEngine.BATTERY);
			battery.draw(BatteryBlockEntity.CAPACITY);
			// Take the alternator away so nothing can charge it again
			helper.setBlock(TestEngine.ALTERNATOR, Blocks.AIR);
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(helper.getTick() > 620, "Battery not drained yet");
			helper.assertTrue(summary(helper).firing() == 0, "Nothing should fire without a spark: " + summary(helper));
			CrankshaftBlockEntity crank = helper.getBlockEntity(PRIMARY);
			helper.assertTrue(crank.getSpeed() == 0, "Engine should have stopped, speed " + crank.getSpeed());
		});
	}

	/** Too much fuel for the air is a fault you can see: the cylinder reports rich, and a hand on the dial fixes it. */
	@GameTest(template = "empty", timeoutTicks = 1500)
	public static void aRichMeteringSettingRunsRichAndTurningItDownCuresIt(GameTestHelper helper) {
		buildSingleCylinder(helper, true);
		CylinderHeadBlockEntity head = helper.getBlockEntity(HEAD);
		head.metering.setValue(CylinderHeadBlockEntity.MAX_METERING);
		helper.runAtTickTime(300, () -> helper.setBlock(STARTER_POWER, Blocks.AIR));
		helper.runAtTickTime(900, () -> {
			CylinderStatus status = cylinder(helper);
			helper.assertTrue(status.result() == CycleResult.FIRED, "Rich engine should still fire: " + status);
			helper.assertTrue(status.rich(), "Twice the fuel should run rich: " + status);
			helper.assertTrue(status.mixture() > 1.25f, "Mixture should read rich: " + status);
			head.metering.setValue(CylinderHeadBlockEntity.DEFAULT_METERING / 2);
		});
		helper.runAtTickTime(1400, () -> {
			CylinderStatus status = cylinder(helper);
			helper.assertFalse(status.rich(), "Half the fuel should clear it: " + status);
			helper.assertTrue(status.result() == CycleResult.FIRED, "Cylinder should be firing: " + status);
			helper.succeed();
		});
	}

	/** A fluid in a common fuel tag runs an engine with no datapack; our own fuels sit in those tags for other mods' machines. */
	@GameTest(template = "empty")
	public static void fuelsFromOtherModsRunByTheirCommonTags(GameTestHelper helper) {
		var tags = new Object() {
			boolean has(net.minecraft.world.level.material.Fluid fluid, net.minecraft.tags.TagKey<net.minecraft.world.level.material.Fluid> tag) {
				return fluid.builtInRegistryHolder()
					.is(tag);
			}
		};
		helper.assertTrue(tags.has(CEFluids.DIESEL.get(), com.createengines.registry.CEDataMaps.DIESEL), "Our diesel is c:diesel");
		helper.assertTrue(tags.has(CEFluids.GASOLINE.get(), com.createengines.registry.CEDataMaps.GASOLINE), "Our gasoline is c:gasoline");
		helper.assertTrue(tags.has(CEFluids.ETHANOL.get(), com.createengines.registry.CEDataMaps.ETHANOL), "Our ethanol is c:ethanol");
		helper.assertTrue(tags.has(CEFluids.PLANT_OIL.get(), com.createengines.registry.CEDataMaps.PLANT_OIL),
			"Our plant oil is c:plantoil");
		helper.assertTrue(tags.has(CEFluids.KEROSENE.get(), com.createengines.registry.CEDataMaps.KEROSENE), "Our kerosene is c:kerosene");
		helper.assertTrue(com.createengines.registry.CEDataMaps.fuelForTags(t -> t.equals(com.createengines.registry.CEDataMaps.KEROSENE))
			== com.createengines.engine.sim.FuelSpec.KEROSENE, "Another mod's kerosene burns as kerosene");
		var kerosene = com.createengines.registry.CEDataMaps.fuel(CEFluids.KEROSENE.get());
		helper.assertTrue(kerosene == com.createengines.engine.sim.FuelSpec.KEROSENE,
			"Kerosene has no data map entry (the shipped fuel.json is pinned): it burns as its tag says: " + kerosene);
		helper.assertTrue(tags.has(CEFluids.METHANOL.get(), com.createengines.registry.CEDataMaps.METHANOL), "Our methanol is c:methanol");
		helper.assertTrue(com.createengines.registry.CEDataMaps.fuelForTags(t -> t.equals(com.createengines.registry.CEDataMaps.METHANOL))
			== com.createengines.engine.sim.FuelSpec.METHANOL, "Another mod's methanol burns as methanol");
		helper.assertTrue(com.createengines.registry.CEDataMaps.fuel(CEFluids.METHANOL.get()) == com.createengines.engine.sim.FuelSpec.METHANOL,
			"Methanol has no data map entry (the shipped fuel.json is pinned): it burns as its tag says");
		helper.assertTrue(com.createengines.registry.CEDataMaps.fuelForTags(t -> t.equals(com.createengines.registry.CEDataMaps.GASOLINE))
			== com.createengines.engine.sim.FuelSpec.GASOLINE, "Another mod's gasoline burns as gasoline");
		helper.assertTrue(com.createengines.registry.CEDataMaps.fuelForTags(t -> t.equals(com.createengines.registry.CEDataMaps.BIODIESEL))
			== com.createengines.engine.sim.FuelSpec.BIODIESEL,
			"Biodiesel burns as biodiesel, the clean diesel (2.1; it burned as diesel before, release Exception 2.9)");
		helper.assertTrue(com.createengines.registry.CEDataMaps.fuelForTags(t -> t.equals(com.createengines.registry.CEDataMaps.DIESEL))
			== com.createengines.engine.sim.FuelSpec.DIESEL, "A fluid in c:diesel only still burns as diesel");
		helper.assertTrue(com.createengines.registry.CEDataMaps.fuelForTags(t -> t.equals(com.createengines.registry.CEDataMaps.DIESEL)
			|| t.equals(com.createengines.registry.CEDataMaps.BIODIESEL)) == com.createengines.engine.sim.FuelSpec.BIODIESEL,
			"Diesel Generators' biodiesel is in both tags and burns as biodiesel");
		// The shipped data entries and the constants a tag falls back on say the same thing, so a fuel burns alike by either route
		for (var pair : java.util.List.of(
			java.util.Map.entry(CEFluids.DIESEL.get(), com.createengines.engine.sim.FuelSpec.DIESEL),
			java.util.Map.entry(CEFluids.GASOLINE.get(), com.createengines.engine.sim.FuelSpec.GASOLINE),
			java.util.Map.entry(CEFluids.ETHANOL.get(), com.createengines.engine.sim.FuelSpec.ETHANOL),
			java.util.Map.entry(CEFluids.PLANT_OIL.get(), com.createengines.engine.sim.FuelSpec.PLANT_OIL)))
			helper.assertTrue(pair.getValue().equals(com.createengines.registry.CEDataMaps.fuel(pair.getKey())),
				"The shipped data entry for " + pair.getKey() + " should equal its constant: " + com.createengines.registry.CEDataMaps.fuel(pair.getKey())
					+ " against " + pair.getValue());
		helper.assertTrue(com.createengines.registry.CEDataMaps.fuelForTags(t -> t.equals(com.createengines.registry.CEDataMaps.ETHANOL))
			== com.createengines.engine.sim.FuelSpec.ETHANOL, "Ethanol burns as ethanol");
		helper.assertTrue(com.createengines.registry.CEDataMaps.fuelForTags(t -> t.equals(com.createengines.registry.CEDataMaps.PLANT_OIL))
			== com.createengines.engine.sim.FuelSpec.PLANT_OIL, "Plant oil burns as plant oil");
		helper.assertTrue(com.createengines.registry.CEDataMaps.fuelForTags(t -> false) == null, "A fluid in no fuel tag is no fuel");
		helper.assertTrue(com.createengines.registry.CEDataMaps.fuel(net.minecraft.world.level.material.Fluids.WATER) == null,
			"Water is no fuel");
		helper.assertTrue(com.createengines.registry.CEDataMaps.fuel(CEFluids.COOLANT.get()) == null, "Nor is coolant");
		var diesel = com.createengines.registry.CEDataMaps.fuel(CEFluids.DIESEL.get());
		helper.assertTrue(diesel != null && diesel.energyPerMb() == 36 && diesel.autoignitionTemp() == 1.55,
			"A data map entry still wins over the tag: " + diesel);
		var ethanol = com.createengines.registry.CEDataMaps.fuel(CEFluids.ETHANOL.get());
		helper.assertTrue(ethanol != null && ethanol.autoignitionTemp() == 3.4
			&& ethanol.preignitionTemp() == com.createengines.engine.sim.FuelSpec.ETHANOL_PREIGNITION,
			"Ethanol's data entry names its own pre-ignition point: " + ethanol);
		var gasoline = com.createengines.registry.CEDataMaps.fuel(CEFluids.GASOLINE.get());
		helper.assertTrue(gasoline != null && gasoline.preignitionTemp() == gasoline.autoignitionTemp(),
			"A fuel that names none pre-ignites at its autoignition point: " + gasoline);
		helper.succeed();
	}

	/** An alternator on a turning shaft offers Forge Energy, and only feels stress for it while something draws it. */
	@GameTest(template = "empty", timeoutTicks = 400)
	public static void anAlternatorMakesForgeEnergyForWhatDrawsIt(GameTestHelper helper) {
		BlockPos motor = new BlockPos(1, 1, 1);
		BlockPos alternator = new BlockPos(2, 1, 1);
		helper.setBlock(motor, com.simibubi.create.AllBlocks.CREATIVE_MOTOR.getDefaultState()
			.setValue(CreativeMotorBlock.FACING, Direction.EAST));
		CreativeMotorBlockEntity drive = helper.getBlockEntity(motor);
		drive.generatedSpeed.setValue(64);
		helper.setBlock(alternator, CEBlocks.ALTERNATOR.getDefaultState()
			.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.AXIS, Direction.Axis.X));
		int[] taken = new int[1];
		helper.runAtTickTime(80, () -> {
			com.createengines.content.alternator.AlternatorBlockEntity be = helper.getBlockEntity(alternator);
			helper.assertTrue(Math.abs(be.getSpeed()) > 0, "The alternator should be turning: " + be.getSpeed());
			var energy = be.energyOutput();
			helper.assertTrue(energy.canExtract() && !energy.canReceive(), "It gives energy and takes none");
			helper.assertTrue(energy.receiveEnergy(1000, false) == 0, "Nothing can be put in");
			helper.assertTrue(energy.getEnergyStored() > 0, "The buffer should have filled from the shaft");
			helper.assertTrue(be.calculateStressApplied() == 0, "Nothing is drawing it yet, so the shaft feels nothing");
		});
		for (int tick = 90; tick < 110; tick++)
			helper.runAtTickTime(tick, () -> {
				com.createengines.content.alternator.AlternatorBlockEntity be = helper.getBlockEntity(alternator);
				taken[0] += be.energyOutput()
					.extractEnergy(100000, false);
			});
		helper.runAtTickTime(112, () -> {
			com.createengines.content.alternator.AlternatorBlockEntity be = helper.getBlockEntity(alternator);
			helper.assertTrue(taken[0] > 1000, "A machine drawing every tick should get a steady supply: " + taken[0]);
			helper.assertTrue(be.calculateStressApplied() > 0, "And the shaft should feel the load");
		});
		helper.runAtTickTime(200, () -> {
			com.createengines.content.alternator.AlternatorBlockEntity be = helper.getBlockEntity(alternator);
			helper.assertTrue(be.calculateStressApplied() == 0, "With nothing drawing, the shaft is let go again");
			helper.succeed();
		});
	}
}
