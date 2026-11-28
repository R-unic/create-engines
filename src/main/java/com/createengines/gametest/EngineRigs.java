package com.createengines.gametest;

import java.util.Arrays;

import com.createengines.engine.EngineSound;
import com.createengines.config.CEConfig;
import com.createengines.content.airflow.ManifoldBlock;
import com.createengines.content.crankshaft.CrankshaftBlockEntity;
import com.createengines.content.cylinder.CylinderBlockEntity;
import com.createengines.engine.CylinderStatus;
import com.createengines.engine.EngineSummary;
import com.simibubi.create.AllBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** Positions, builders and readers shared by the engine GameTests. */
final class EngineRigs {

	private EngineRigs() {
	}

	static final BlockPos PRIMARY = TestEngine.PRIMARY;
	static final BlockPos CYLINDER = TestEngine.CYLINDER;
	static final BlockPos HEAD = TestEngine.HEAD;
	static final BlockPos LOBE = TestEngine.LOBE;
	static final BlockPos EXHAUST = TestEngine.EXHAUST;
	static final BlockPos STARTER = TestEngine.STARTER;
	static final BlockPos STARTER_POWER = TestEngine.STARTER_POWER;
	/** First of a row of presses on the crank's output shaft, beyond its cogwheel. */
	static final BlockPos PRESS = new BlockPos(6, 2, 2);

	static void buildSingleCylinder(GameTestHelper helper, boolean fuelled) {
		TestEngine.build(helper.getLevel(), helper.absolutePos(BlockPos.ZERO), fuelled);
	}

	/** That the engine tells the client this about itself, for its sound, with an outlet for every firing it lists. */
	static void assertSounds(GameTestHelper helper, boolean compression, int outlets) {
		EngineSummary summary = summary(helper);
		EngineSound sound = summary.sound();
		helper.assertTrue(sound.compression() == compression, "Compression ignition should be " + compression);
		helper.assertTrue(sound.outlets().length == summary.firingAngles().length,
			"An outlet for every firing: " + Arrays.toString(sound.outlets()) + " for " + Arrays.toString(summary.firingAngles()));
		helper.assertTrue(sound.pipes().length == outlets && Arrays.stream(sound.outlets())
			.distinct()
			.count() == outlets, "Should go out of " + outlets + " outlets: " + Arrays.toString(sound.outlets()) + ", pipes "
				+ Arrays.toString(sound.pipes()));
	}

	static EngineSummary summary(GameTestHelper helper) {
		CrankshaftBlockEntity crank = helper.getBlockEntity(PRIMARY);
		EngineSummary summary = crank.summary();
		if (summary == null)
			helper.fail("Crankshaft has no engine (topology did not build one)", PRIMARY);
		return summary;
	}

	static CylinderStatus cylinder(GameTestHelper helper) {
		CylinderBlockEntity cylinder = helper.getBlockEntity(CYLINDER);
		CylinderStatus status = cylinder.status();
		if (status == null)
			helper.fail("Cylinder has no engine status", CYLINDER);
		return status;
	}

	static void placePresses(GameTestHelper helper) {
		placePresses(helper, 3);
	}

	/**
	 * How many presses overload the single cylinder when four did at a stress scale of 40 per unit of torque, the scale
	 * these tests were first calibrated at. The engine's rating follows the config, so the load has to as well. A row of
	 * that many presses is longer than the small template, so these tests use the large one.
	 */
	static int scaledPresses(int calibrated) {
		return (int) Math.ceil(calibrated * CEConfig.STRESS_PER_TORQUE.get() / 40.0 * 1.1);
	}

	static void placePresses(GameTestHelper helper, int presses) {
		for (int i = 0; i < presses; i++)
			helper.setBlock(PRESS.east(1 + i), AllBlocks.MECHANICAL_PRESS.getDefaultState()
				.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST));
	}

	static BlockState alongX(BlockState state) {
		return state.setValue(BlockStateProperties.AXIS, Axis.X);
	}

	static void cog(GameTestHelper helper, BlockPos pos) {
		helper.setBlock(pos, alongX(AllBlocks.COGWHEEL.getDefaultState()));
	}

	static BlockState placePipe(GameTestHelper helper, ManifoldBlock block, BlockPos pos) {
		BlockPos absolute = helper.absolutePos(pos);
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(absolute), Direction.UP, absolute, false);
		BlockPlaceContext context = new BlockPlaceContext(helper.makeMockPlayer(GameType.CREATIVE), InteractionHand.MAIN_HAND,
			new ItemStack(block), hit);
		return block.getStateForPlacement(context);
	}
}
