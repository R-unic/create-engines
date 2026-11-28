package com.createengines.gametest;

import static com.createengines.gametest.StressRigs.*;

import java.util.ArrayList;
import java.util.List;

import com.createengines.CreateEngines;
import com.createengines.content.camshaft.CamshaftBlockEntity;
import com.createengines.content.cylinder.CylinderBlock;
import com.createengines.content.head.CylinderHeadBlock;
import com.createengines.content.head.CylinderHeadBlockEntity;
import com.createengines.content.airflow.PortOpeningBlock;
import com.createengines.content.crankshaft.CrankshaftBlockEntity;
import com.createengines.content.starter.StarterBlock;
import com.createengines.engine.CrankGeometry;
import com.createengines.engine.EngineState;
import com.createengines.engine.EngineSummary;
import com.createengines.registry.CEBlocks;
import com.createengines.registry.CEFluids;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlock;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Long engines and many engines at once. */
@GameTestHolder(CreateEngines.MOD_ID)
@PrefixGameTestTemplate(false)
public class StressScaleGameTests {

	// ---------------------------------------------------------------------------------------------------------------
	// Engines under churn, in numbers and at size
	// ---------------------------------------------------------------------------------------------------------------

	private static BlockState axis(BlockState state, Axis axis) {
		return state.setValue(BlockStateProperties.AXIS, axis);
	}

	/** An engine with {@code count} cylinders in a line, {@code 3} blocks apart, all on one cam and one starter. */
	private static void buildLine(Level level, BlockPos origin, int count, boolean fuelled) {
		int pitch = 3;
		int lastThrow = 2 + pitch * (count - 1);
		int crankEnd = lastThrow + 1;
		java.util.function.BiConsumer<BlockPos, BlockState> set =
			(pos, state) -> level.setBlock(origin.offset(pos), state, Block.UPDATE_ALL);
		for (int x = 1; x <= crankEnd; x++)
			set.accept(new BlockPos(x, 2, 2), axis(CEBlocks.CRANKSHAFT.getDefaultState(), Axis.X));
		set.accept(new BlockPos(crankEnd + 1, 2, 2), axis(AllBlocks.SHAFT.getDefaultState(), Axis.X));
		set.accept(new BlockPos(crankEnd + 2, 2, 2), axis(AllBlocks.COGWHEEL.getDefaultState(), Axis.X));
		for (int x = 3; x <= crankEnd + 2; x++)
			set.accept(new BlockPos(x, 4, 3), axis(CEBlocks.CAMSHAFT.getDefaultState(), Axis.X));
		set.accept(new BlockPos(crankEnd + 2, 3, 3), axis(AllBlocks.LARGE_COGWHEEL.getDefaultState(), Axis.X));
		set.accept(new BlockPos(crankEnd + 3, 3, 3), axis(AllBlocks.ENCASED_CHAIN_DRIVE.getDefaultState(), Axis.X));
		set.accept(new BlockPos(crankEnd + 3, 4, 3), axis(AllBlocks.ENCASED_CHAIN_DRIVE.getDefaultState(), Axis.X));

		double tdc = CrankGeometry.topDeadCentreAngle(Axis.X, 0, Direction.UP);
		int lobe = (int) Math.round((((tdc / 2) % 180 + 180) % 180) / CamshaftBlockEntity.LOBE_ANGLE_STEP);
		for (int k = 0; k < count; k++) {
			int x = 2 + pitch * k;
			set.accept(new BlockPos(x, 2, 2), axis(CEBlocks.CRANK_THROW.getDefaultState(), Axis.X));
			set.accept(new BlockPos(x, 3, 2), CEBlocks.CYLINDER.getDefaultState()
				.setValue(CylinderBlock.FACING, Direction.UP)
				.setValue(CylinderBlock.HAS_PISTON, true)
				.setValue(CylinderBlock.HAS_ROD, true));
			set.accept(new BlockPos(x, 4, 2), CEBlocks.CYLINDER_HEAD.getDefaultState()
				.setValue(CylinderHeadBlock.FACING, Direction.UP)
				.setValue(CylinderHeadBlock.HAS_INJECTOR, true));
			set.accept(new BlockPos(x, 4, 1), CEBlocks.AIR_INTAKE.getDefaultState()
				.setValue(PortOpeningBlock.FACING, Direction.NORTH));
			set.accept(new BlockPos(x - 1, 4, 2), CEBlocks.EXHAUST_OUTLET.getDefaultState()
				.setValue(PortOpeningBlock.FACING, Direction.WEST));
			set.accept(new BlockPos(x, 4, 3), axis(CEBlocks.CAM_LOBE.getDefaultState(), Axis.X));
			((CamshaftBlockEntity) level.getBlockEntity(origin.offset(new BlockPos(x, 4, 3)))).lobeAngle.setValue(lobe);
		}
		set.accept(new BlockPos(1, 1, 2), CEBlocks.STARTER.getDefaultState()
			.setValue(StarterBlock.FACING, Direction.UP));
		set.accept(new BlockPos(1, 0, 2), AllBlocks.CREATIVE_MOTOR.getDefaultState()
			.setValue(CreativeMotorBlock.FACING, Direction.UP));
		set.accept(new BlockPos(0, 1, 2), Blocks.REDSTONE_BLOCK.defaultBlockState());
		((CreativeMotorBlockEntity) level.getBlockEntity(origin.offset(new BlockPos(1, 0, 2)))).generatedSpeed
			.setValue(TestEngine.MOTOR_RPM);
		if (fuelled)
			refill(level, origin.offset(new BlockPos(2, 4, 2)));
	}

	private static void refill(Level level, BlockPos head) {
		if (!(level.getBlockEntity(head) instanceof CylinderHeadBlockEntity be))
			return;
		be.tank()
			.fill(new FluidStack(CEFluids.DIESEL.get()
				.getSource(), CylinderHeadBlockEntity.CAPACITY), FluidAction.EXECUTE);
		be.oilTank()
			.fill(new FluidStack(CEFluids.ENGINE_OIL.get()
				.getSource(), CylinderHeadBlockEntity.CAPACITY), FluidAction.EXECUTE);
	}

	private static void runLongEngine(GameTestHelper helper, int cylinders) {
		BlockPos origin = helper.absolutePos(new BlockPos(2, 2, 20));
		Level level = helper.getLevel();
		long built = System.nanoTime();
		buildLine(level, origin, cylinders, true);
		BlockPos crank = new BlockPos(1, 2, 2);
		BlockPos head = new BlockPos(2, 4, 2);
		for (int tick = 10; tick <= 1100; tick += 10)
			helper.runAtTickTime(tick, () -> refill(level, origin.offset(head)));
		helper.runAtTickTime(500, () -> {
			CrankshaftBlockEntity be = (CrankshaftBlockEntity) level.getBlockEntity(origin.offset(crank));
			EngineSummary summary = be.summary();
			helper.assertTrue(summary != null, "The long crank built no engine");
			helper.assertTrue(summary.cylinders() == cylinders, "Expected " + cylinders + " cylinders, " + summary);
			helper.assertTrue(summary.firing() == cylinders, "Every cylinder should fire under the starter: " + summary);
			level.setBlock(origin.offset(new BlockPos(0, 1, 2)), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
		});
		helper.runAtTickTime(1000, () -> {
			CrankshaftBlockEntity be = (CrankshaftBlockEntity) level.getBlockEntity(origin.offset(crank));
			EngineSummary summary = be.summary();
			helper.assertTrue(summary != null && summary.state() == EngineState.RUNNING,
				"The " + cylinders + "-cylinder engine should run on its own: " + summary);
			double[] cost = cost(level, origin.offset(crank), 400);
			CreateEngines.LOGGER.info("{}-cylinder line: crank ticks cost {} ns and {} bytes", cylinders,
				cost == null ? -1 : (long) cost[0], cost == null ? -1 : (long) cost[1]);
			helper.assertTrue(cost != null && cost[0] < 2_000_000, "A running " + cylinders + "-cylinder crank tick takes "
				+ (cost == null ? -1 : (long) cost[0]) + " ns");
			helper.assertTrue(cost != null && cost[1] < 2_000_000, "A running " + cylinders
				+ "-cylinder crank tick allocates " + (cost == null ? -1 : (long) cost[1]) + " bytes");
			CreateEngines.LOGGER.info("{}-cylinder line: {} mspt server average, {}", cylinders,
				level.getServer()
					.getAverageTickTimeNanos() / 1e6,
				summary);
			helper.succeed();
		});
	}

	@GameTest(template = LARGE, timeoutTicks = 1100)
	public static void aTwelveCylinderLineRunsOnOneStarter(GameTestHelper helper) {
		runLongEngine(helper, 12);
	}

	@GameTest(template = LARGE, timeoutTicks = 1100)
	public static void aSingleCylinderLineBuiltTheLongWayRuns(GameTestHelper helper) {
		runLongEngine(helper, 1);
	}

	/** Sixteen separate engines starting and running together in one world. */
	@GameTest(template = LARGE, timeoutTicks = 1000)
	public static void sixteenEnginesRunAtOnce(GameTestHelper helper) {
		Level level = helper.getLevel();
		List<BlockPos> origins = new ArrayList<>();
		for (int i = 0; i < 4; i++)
			for (int j = 0; j < 4; j++) {
				BlockPos origin = helper.absolutePos(new BlockPos(1 + 11 * i, 0, 1 + 11 * j));
				origins.add(origin);
				TestEngine.build(level, origin, true);
			}
		helper.runAtTickTime(300, () -> {
			for (BlockPos origin : origins) {
				CrankshaftBlockEntity be = (CrankshaftBlockEntity) level.getBlockEntity(origin.offset(TestEngine.PRIMARY));
				EngineSummary summary = be.summary();
				helper.assertTrue(summary != null && summary.firing() == 1, "Engine at " + origin + " should fire: " + summary);
				level.setBlock(origin.offset(TestEngine.STARTER_POWER), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
			}
		});
		helper.runAtTickTime(700, () -> {
			int running = 0;
			for (BlockPos origin : origins) {
				CrankshaftBlockEntity be = (CrankshaftBlockEntity) level.getBlockEntity(origin.offset(TestEngine.PRIMARY));
				EngineSummary summary = be.summary();
				if (summary != null && summary.state() == EngineState.RUNNING)
					running++;
			}
			CreateEngines.LOGGER.info("Sixteen engines: {} running, {} mspt server average", running, level.getServer()
				.getAverageTickTimeNanos() / 1e6);
			helper.assertTrue(running == 16, running + " of 16 engines are running");
			helper.succeed();
		});
	}
}
