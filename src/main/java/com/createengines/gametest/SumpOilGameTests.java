package com.createengines.gametest;

import static com.createengines.gametest.EngineRigs.*;

import com.createengines.CreateEngines;
import com.createengines.content.head.CylinderHeadBlockEntity;
import com.createengines.content.sump.SumpBlockEntity;
import com.createengines.engine.EngineState;
import com.createengines.engine.EngineSummary;
import com.createengines.registry.CEBlocks;
import com.createengines.registry.CEFluids;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Engine oil, oil pans, sumps and their comparators and coolant. Test engines as in {@link EngineGameTests}. */
@GameTestHolder(CreateEngines.MOD_ID)
@PrefixGameTestTemplate(false)
public class SumpOilGameTests {

	/** Plant oil is a poor man's engine oil: the oil tanks take it, rate it below engine oil, and still refuse fuel. */
	@GameTest(template = "empty")
	public static void plantOilIsAPoorMansEngineOil(GameTestHelper helper) {
		BlockPos sump = new BlockPos(3, 1, 2);
		helper.setBlock(sump, CEBlocks.SUMP.getDefaultState());
		SumpBlockEntity pan = helper.getBlockEntity(sump);
		FluidStack plant = new FluidStack(CEFluids.PLANT_OIL.get()
			.getSource(), 200);
		FluidStack engine = new FluidStack(CEFluids.ENGINE_OIL.get()
			.getSource(), 200);
		FluidStack diesel = new FluidStack(CEFluids.DIESEL.get()
			.getSource(), 200);

		helper.assertTrue(CEFluids.lubricity(engine) == 1, "Engine oil lubricates in full");
		helper.assertTrue(CEFluids.lubricity(plant) > 0 && CEFluids.lubricity(plant) < 1,
			"Plant oil lubricates, but worse");
		helper.assertTrue(CEFluids.lubricity(diesel) == 0, "Fuel does not lubricate");

		helper.assertTrue(pan.tank()
			.fill(diesel, FluidAction.SIMULATE) == 0, "An oil tank refuses fuel");
		helper.assertTrue(pan.tank()
			.fill(plant, FluidAction.EXECUTE) == 200, "An oil tank takes plant oil");
		helper.succeed();
	}

	/** A comparator against an oil pan reads the tank its dial names: nothing when empty, 15 only when full. */
	@GameTest(template = "empty")
	public static void aSumpComparatorReadsTheTankItIsSetTo(GameTestHelper helper) {
		BlockPos sump = new BlockPos(3, 1, 2);
		helper.setBlock(sump, CEBlocks.SUMP.getDefaultState());
		SumpBlockEntity pan = helper.getBlockEntity(sump);
		BlockState state = helper.getBlockState(sump);
		BlockPos absolute = helper.absolutePos(sump);
		helper.assertTrue(state.getAnalogOutputSignal(helper.getLevel(), absolute) == 0, "An empty pan reads nothing");

		pan.fluidCapability()
			.fill(new FluidStack(CEFluids.ENGINE_OIL.get()
				.getSource(), 500), FluidAction.EXECUTE);
		int half = state.getAnalogOutputSignal(helper.getLevel(), absolute);
		helper.assertTrue(half > 1 && half < 15, "A half-full oil tank should read in between, read " + half);
		pan.fluidCapability()
			.fill(new FluidStack(CEFluids.ENGINE_OIL.get()
				.getSource(), 500), FluidAction.EXECUTE);
		helper.assertTrue(state.getAnalogOutputSignal(helper.getLevel(), absolute) == 15, "A full oil tank reads 15");

		// The dial moves the reading to another tank, which is still empty
		pan.signalTank.setValue(2);
		helper.assertTrue(state.getAnalogOutputSignal(helper.getLevel(), absolute) == 0,
			"Set to coolant it should read the empty coolant tank");
		pan.fluidCapability()
			.fill(new FluidStack(CEFluids.COOLANT.get()
				.getSource(), 500), FluidAction.EXECUTE);
		helper.assertTrue(state.getAnalogOutputSignal(helper.getLevel(), absolute) == 15,
			"Set to coolant it should read the full coolant tank");
		helper.succeed();
	}

	/** Engine oil swapped out of the pan becomes used oil, which is what the recycling recipes take. */
	@GameTest(template = "empty")
	public static void anExchangeTurnsEngineOilIntoUsedOil(GameTestHelper helper) {
		BlockPos sump = new BlockPos(3, 1, 2);
		helper.setBlock(sump, CEBlocks.SUMP.getDefaultState());
		SumpBlockEntity pan = helper.getBlockEntity(sump);
		pan.exchange.setValue(2);
		pan.tank()
			.fill(new FluidStack(CEFluids.ENGINE_OIL.get()
				.getSource(), 400), FluidAction.EXECUTE);
		double moved = pan.exchange(1000);
		helper.assertTrue(moved > 0, "Engine oil should be swapped out");
		helper.assertTrue(CEFluids.isUsedOil(pan.usedTank()
			.getFluid()) && pan.usedTank()
				.getFluidAmount() == (int) moved,
			"What was swapped out should be in the used tank, got " + pan.usedTank()
				.getFluid());
		helper.succeed();
	}

	/**
	 * Plant oil swapped out of the pan is burned off, not turned into used oil: used oil recycles into engine oil, which
	 * would let a poor lubricant skip the slime and glycerin that engine oil costs. It still counts as swapped, and it
	 * does not wait for room in the used tank.
	 */
	@GameTest(template = "empty")
	public static void anExchangeNeverTurnsPlantOilIntoUsedOil(GameTestHelper helper) {
		BlockPos sump = new BlockPos(3, 1, 2);
		helper.setBlock(sump, CEBlocks.SUMP.getDefaultState());
		SumpBlockEntity pan = helper.getBlockEntity(sump);
		pan.exchange.setValue(2);
		pan.tank()
			.fill(new FluidStack(CEFluids.PLANT_OIL.get()
				.getSource(), 400), FluidAction.EXECUTE);
		pan.usedTank()
			.fill(new FluidStack(CEFluids.USED_OIL.get()
				.getSource(), pan.usedTank()
					.getCapacity()),
				FluidAction.EXECUTE);
		int before = pan.usedTank()
			.getFluidAmount();
		double moved = pan.exchange(1000);
		helper.assertTrue(moved > 0, "Plant oil should still be swapped out, even with the used tank full");
		helper.assertTrue(pan.usedTank()
			.getFluidAmount() == before, "Plant oil must not add anything to the used tank");
		helper.assertTrue(pan.tank()
			.getFluidAmount() == 400 - (int) moved, "The swapped plant oil should leave the pan");
		pan.usedTank()
			.drain(before, FluidAction.EXECUTE);
		pan.tank()
			.fill(new FluidStack(CEFluids.PLANT_OIL.get()
				.getSource(), 400), FluidAction.EXECUTE);
		pan.exchange(1000);
		helper.assertTrue(pan.usedTank()
			.isEmpty(), "An empty used tank stays empty when plant oil is swapped");
		helper.succeed();
	}

	/**
	 * A vanilla comparator beside an oil pan reads the tank its dial names, so a lamp it feeds lights once the pan holds oil.
	 * A comparator's facing is its input side, so it faces the pan and the lamp sits behind it.
	 */
	@GameTest(template = "empty", timeoutTicks = 200)
	public static void aComparatorBesideAnOilPanLightsALampOnceItHoldsOil(GameTestHelper helper) {
		BlockPos pan = new BlockPos(2, 1, 2);
		BlockPos lamp = new BlockPos(4, 1, 2);
		helper.setBlock(pan, CEBlocks.SUMP.getDefaultState());
		helper.setBlock(new BlockPos(3, 0, 2), Blocks.STONE);
		helper.setBlock(new BlockPos(3, 1, 2), Blocks.COMPARATOR.defaultBlockState()
			.setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING, Direction.WEST));
		helper.setBlock(lamp, Blocks.REDSTONE_LAMP);
		helper.runAtTickTime(5, () -> {
			helper.assertBlockProperty(lamp, BlockStateProperties.LIT, false);
			SumpBlockEntity sump = helper.getBlockEntity(pan);
			sump.fluidCapability()
				.fill(new FluidStack(CEFluids.ENGINE_OIL.get()
					.getSource(), 500), FluidAction.EXECUTE);
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(helper.getTick() > 5, "Not filled yet");
			helper.assertBlockProperty(lamp, BlockStateProperties.LIT, true);
		});
	}

	/** A new engine is dry. Oil piped into its head builds the film, and turning the engine uses the oil up. */
	@GameTest(template = "empty", timeoutTicks = 900)
	public static void engineOilBuildsTheFilmAndIsUsedUp(GameTestHelper helper) {
		TestEngine.build(helper.getLevel(), helper.absolutePos(BlockPos.ZERO), true, false);
		helper.runAtTickTime(100, () -> {
			EngineSummary summary = summary(helper);
			helper.assertTrue(summary.lubrication() < 0.05, "An engine with no oil should be dry: " + summary);
			CylinderHeadBlockEntity head = helper.getBlockEntity(HEAD);
			helper.assertTrue(head.fluidCapability()
				.fill(new FluidStack(CEFluids.ENGINE_OIL.get()
					.getSource(), 250), FluidAction.EXECUTE) == 250, "The head should take engine oil from a pipe");
			helper.assertTrue(head.fluidCapability()
				.fill(new FluidStack(Fluids.WATER, 250), FluidAction.EXECUTE) == 0, "It shouldn't take water");
		});
		helper.runAtTickTime(500, () -> {
			EngineSummary summary = summary(helper);
			helper.assertTrue(summary.lubrication() > 0.95, "Oil should have built the film: " + summary);
			CylinderHeadBlockEntity head = helper.getBlockEntity(HEAD);
			helper.assertTrue(head.oilTank()
				.getFluidAmount() < 250, "Turning the engine should use oil");
			helper.assertTrue(head.oilTank()
				.getFluidAmount() > 200, "But not very much of it");
			helper.succeed();
		});
	}

	/** An oil sump under the crankshaft oils the whole engine, without a pipe to the head, and is used up slowly. */
	@GameTest(template = "empty", timeoutTicks = 900)
	public static void anOilSumpOilsTheWholeEngine(GameTestHelper helper) {
		TestEngine.build(helper.getLevel(), helper.absolutePos(BlockPos.ZERO), true, false);
		BlockPos sump = new BlockPos(3, 1, 2);
		helper.setBlock(sump, CEBlocks.SUMP.getDefaultState());
		helper.runAtTickTime(100, () -> {
			helper.assertTrue(summary(helper).lubrication() < 0.05, "An engine with no oil should be dry");
			SumpBlockEntity pan = helper.getBlockEntity(sump);
			helper.assertTrue(pan.fluidCapability()
				.fill(new FluidStack(CEFluids.ENGINE_OIL.get()
					.getSource(), 500), FluidAction.EXECUTE) == 500, "The sump should take engine oil from a pipe");
			helper.assertTrue(pan.fluidCapability()
				.fill(new FluidStack(CEFluids.GASOLINE.get()
					.getSource(), 500), FluidAction.EXECUTE) == 0, "It shouldn't take a fuel that is no pilot fuel");
		});
		helper.runAtTickTime(500, () -> {
			EngineSummary summary = summary(helper);
			helper.assertTrue(summary.lubrication() > 0.95, "The sump should have built the film: " + summary);
			SumpBlockEntity pan = helper.getBlockEntity(sump);
			helper.assertTrue(pan.tank()
				.getFluidAmount() < 500, "Turning the engine should use the sump's oil");
			helper.assertTrue(pan.tank()
				.getFluidAmount() > 400, "But not very much of it");
			CylinderHeadBlockEntity head = helper.getBlockEntity(HEAD);
			helper.assertTrue(head.oilTank()
				.isEmpty(), "The head's own oil tank was never needed");
			helper.succeed();
		});
	}

	/** Coolant is spent carrying heat, so a pan filled once runs down while the engine works and has to be fed. */
	@GameTest(template = "empty", timeoutTicks = 1700)
	public static void aRunningEngineSpendsTheCoolantInItsPan(GameTestHelper helper) {
		TestEngine.build(helper.getLevel(), helper.absolutePos(BlockPos.ZERO), true, false);
		BlockPos sump = new BlockPos(3, 1, 2);
		helper.setBlock(sump, CEBlocks.SUMP.getDefaultState());
		helper.runAtTickTime(20, () -> {
			SumpBlockEntity pan = helper.getBlockEntity(sump);
			pan.fluidCapability()
				.fill(new FluidStack(CEFluids.COOLANT.get()
					.getSource(), 500), FluidAction.EXECUTE);
			pan.fluidCapability()
				.fill(new FluidStack(CEFluids.ENGINE_OIL.get()
					.getSource(), 500), FluidAction.EXECUTE);
		});
		helper.runAtTickTime(300, () -> helper.setBlock(STARTER_POWER, Blocks.AIR));
		int[] atStart = new int[1];
		helper.runAtTickTime(400, () -> atStart[0] = ((SumpBlockEntity) helper.getBlockEntity(sump)).coolantTank()
			.getFluidAmount());
		helper.runAtTickTime(1600, () -> {
			SumpBlockEntity pan = helper.getBlockEntity(sump);
			int left = pan.coolantTank()
				.getFluidAmount();
			helper.assertTrue(summary(helper).state() == EngineState.RUNNING, "The engine should be running: " + summary(helper));
			helper.assertTrue(atStart[0] - left >= 10,
				"A minute of running should use up coolant, but the pan went from " + atStart[0] + " to " + left + " mB");
			helper.assertTrue(left > 0, "And not all of it in a minute");
			helper.succeed();
		});
	}

	/** A sump that touches no engine supplies nothing, and says so. */
	@GameTest(template = "empty")
	public static void aLoneSumpBelongsToNoEngine(GameTestHelper helper) {
		BlockPos sump = new BlockPos(1, 1, 1);
		helper.setBlock(sump, CEBlocks.SUMP.getDefaultState());
		SumpBlockEntity pan = helper.getBlockEntity(sump);
		helper.assertTrue(pan.fluidCapability() != null, "Its capability should exist for pipes to find");
		helper.succeed();
	}

	/** Oil is what lets an engine reach its speed: the same engine, dry, tops out far lower. */
	@GameTest(template = "empty", timeoutTicks = 1300)
	public static void dryEngineRunsSlowerThanAnOiledOne(GameTestHelper helper) {
		TestEngine.build(helper.getLevel(), helper.absolutePos(BlockPos.ZERO), true, false);
		helper.runAtTickTime(300, () -> helper.setBlock(STARTER_POWER, Blocks.AIR));
		helper.runAtTickTime(1200, () -> {
			EngineSummary summary = summary(helper);
			helper.assertTrue(summary.state() == EngineState.RUNNING, "A dry engine still runs: " + summary);
			helper.assertTrue(summary.rpm() < 127,
				"A dry engine should top out well below an oiled one's 150+: " + summary);
			helper.succeed();
		});
	}
}
