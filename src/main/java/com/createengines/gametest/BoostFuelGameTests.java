package com.createengines.gametest;

import com.createengines.CreateEngines;
import com.createengines.content.airflow.PortOpeningBlock;
import com.createengines.content.cylinder.CylinderBlockEntity;
import com.createengines.content.head.CylinderHeadBlock;
import com.createengines.content.head.CylinderHeadBlockEntity;
import com.createengines.content.crankshaft.CrankshaftBlockEntity;
import com.createengines.engine.CylinderStatus;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Fuel behaviour under boost: gasoline pre-ignites when the charge is pushed past its autoignition point, ethanol does
 * not, and neither does an engine that breathes at atmospheric pressure.
 */
@GameTestHolder(CreateEngines.MOD_ID)
@PrefixGameTestTemplate(false)
public class BoostFuelGameTests {

	private static final BlockPos PRIMARY = TestEngine.PRIMARY;
	private static final BlockPos CYLINDER = TestEngine.CYLINDER;
	private static final BlockPos HEAD = TestEngine.HEAD;
	private static final BlockPos BLOWER = HEAD.east();

	/** A spark engine on the given fuel, optionally with a supercharger on its intake spun at the given speed. */
	private static void build(GameTestHelper helper, boolean ethanol, boolean blower, int blowerRpm) {
		TestEngine.buildSpark(helper.getLevel(), helper.absolutePos(BlockPos.ZERO), true);
		if (ethanol) {
			// Ethanol is injector-bound: it needs a fine injector to keep up under boost
			helper.setBlock(HEAD, CEBlocks.PRECISION_CYLINDER_HEAD.getDefaultState()
				.setValue(CylinderHeadBlock.FACING, Direction.UP)
				.setValue(CylinderHeadBlock.HAS_INJECTOR, true)
				.setValue(CylinderHeadBlock.FINE_INJECTOR, true)
				.setValue(CylinderHeadBlock.HAS_SPARK_PLUG, true));
			CylinderHeadBlockEntity head = helper.getBlockEntity(HEAD);
			head.oilTank()
				.fill(new FluidStack(CEFluids.ENGINE_OIL.get()
					.getSource(), 250), FluidAction.EXECUTE);
			head.tank()
				.fill(new FluidStack(CEFluids.ETHANOL.get()
					.getSource(), 250), FluidAction.EXECUTE);
		}
		if (blower) {
			helper.setBlock(TestEngine.AIR_INTAKE, Blocks.AIR);
			helper.setBlock(BLOWER.east(), CEBlocks.AIR_INTAKE.getDefaultState()
				.setValue(PortOpeningBlock.FACING, Direction.EAST));
			helper.setBlock(BLOWER, CEBlocks.SUPERCHARGER.getDefaultState()
				.setValue(BlockStateProperties.AXIS, Axis.Y));
			helper.setBlock(BLOWER.below(), AllBlocks.CREATIVE_MOTOR.getDefaultState()
				.setValue(CreativeMotorBlock.FACING, Direction.UP));
			CreativeMotorBlockEntity motor = helper.getBlockEntity(BLOWER.below());
			motor.generatedSpeed.setValue(blowerRpm);
		}
		helper.runAtTickTime(300, () -> helper.setBlock(TestEngine.STARTER_POWER, Blocks.AIR));
	}

	private static CylinderStatus cylinder(GameTestHelper helper) {
		CylinderBlockEntity cylinder = helper.getBlockEntity(CYLINDER);
		CylinderStatus status = cylinder.status();
		if (status == null)
			helper.fail("Cylinder has no engine status", CYLINDER);
		return status;
	}

	private static EngineSummary summary(GameTestHelper helper) {
		CrankshaftBlockEntity crank = helper.getBlockEntity(PRIMARY);
		EngineSummary summary = crank.summary();
		if (summary == null)
			helper.fail("Crankshaft has no engine", PRIMARY);
		return summary;
	}

	/** Gasoline at atmospheric pressure never pre-ignites, however long the engine runs. */
	@GameTest(template = "empty", timeoutTicks = 1200)
	public static void unboostedGasolineNeverPreignites(GameTestHelper helper) {
		build(helper, false, false, 0);
		helper.runAtTickTime(900, () -> {
			helper.assertTrue(summary(helper).state() == EngineState.RUNNING, "Engine should run: " + summary(helper));
			helper.assertTrue(cylinder(helper).preignition() == 0f,
				"Without boost there is nothing to pre-ignite: " + cylinder(helper));
			helper.succeed();
		});
	}

	/** Pushed hard by a supercharger, gasoline's charge passes its autoignition point and part of the burn is lost. */
	@GameTest(template = "empty", timeoutTicks = 1200)
	public static void boostedGasolinePreignites(GameTestHelper helper) {
		build(helper, false, true, 256);
		helper.runAtTickTime(900, () -> {
			helper.assertTrue(summary(helper).state() == EngineState.RUNNING,
				"A boosted gasoline engine still runs: " + summary(helper) + ", cylinder " + cylinder(helper));
			helper.assertTrue(cylinder(helper).preignition() > 0f,
				"Boost should push gasoline past its autoignition point: " + cylinder(helper));
			helper.succeed();
		});
	}

	/** Ethanol takes the same boost without losing any of its burn: its charge cooling keeps it under the limit. */
	@GameTest(template = "empty", timeoutTicks = 1200)
	public static void boostedEthanolDoesNotPreignite(GameTestHelper helper) {
		build(helper, true, true, 256);
		helper.runAtTickTime(900, () -> {
			helper.assertTrue(summary(helper).state() == EngineState.RUNNING,
				"A boosted ethanol engine should run: " + summary(helper) + ", cylinder " + cylinder(helper));
			helper.assertTrue(cylinder(helper).preignition() == 0f,
				"Ethanol should stay under its autoignition point at the same boost: " + cylinder(helper));
			helper.succeed();
		});
	}

	/** Ethanol burns clean: even a flooded intake leaves no soot on the plug. */
	@GameTest(template = "empty", timeoutTicks = 1200)
	public static void ethanolLeavesNoSootOnThePlug(GameTestHelper helper) {
		build(helper, true, false, 0);
		helper.runAtTickTime(900, () -> {
			helper.assertTrue(summary(helper).state() == EngineState.RUNNING, "Engine should run on ethanol: " + summary(helper));
			helper.assertTrue(cylinder(helper).fouling() == 0f, "Ethanol should not foul the plug: " + cylinder(helper));
			helper.succeed();
		});
	}
}
