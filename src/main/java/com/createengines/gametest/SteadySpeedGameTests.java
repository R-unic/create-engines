package com.createengines.gametest;

import com.createengines.CreateEngines;
import com.createengines.content.crankshaft.CrankshaftBlockEntity;
import com.createengines.content.head.CylinderHeadBlockEntity;
import com.createengines.engine.EngineState;
import com.createengines.engine.EngineSummary;
import com.simibubi.create.AllBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * A rough engine, one cylinder with no flywheel lugged by two presses, at a steady load. Its network speed is the mean of
 * its surge and not wherever the crank is on it as a tick ends, so once settled it holds still.
 */
@GameTestHolder(CreateEngines.MOD_ID)
@PrefixGameTestTemplate(false)
public class SteadySpeedGameTests {

	private static final BlockPos PRESS = new BlockPos(6, 2, 2);
	private static final int SETTLED = 1200;
	private static final int WATCHED = 200;

	@GameTest(template = "empty", timeoutTicks = 1500, batch = "steady-speed")
	public static void aRoughEngineGivesItsNetworkASteadySpeed(GameTestHelper helper) {
		TestEngine.build(helper.getLevel(), helper.absolutePos(BlockPos.ZERO), true);
		CylinderHeadBlockEntity head = helper.getBlockEntity(TestEngine.HEAD);
		head.metering.setValue(CylinderHeadBlockEntity.DEFAULT_METERING * 14 / 10);
		helper.runAtTickTime(300, () -> helper.setBlock(TestEngine.STARTER_POWER, Blocks.AIR));
		helper.runAtTickTime(500, () -> {
			for (int i = 0; i < 2; i++)
				helper.setBlock(PRESS.east(i), AllBlocks.MECHANICAL_PRESS.getDefaultState()
					.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST));
		});
		float[] first = new float[1];
		for (int tick = SETTLED; tick <= SETTLED + WATCHED; tick++) {
			int at = tick;
			helper.runAtTickTime(tick, () -> {
				CrankshaftBlockEntity crank = helper.getBlockEntity(TestEngine.PRIMARY);
				float speed = crank.getSpeed();
				if (at == SETTLED) {
					EngineSummary summary = crank.summary();
					helper.assertTrue(summary != null && summary.state() == EngineState.RUNNING && summary.ripple() > 0.04f,
						"The engine should be running, and rough: " + summary);
					first[0] = speed;
				}
				helper.assertTrue(speed != 0 && speed == first[0],
					"The network speed moved at tick " + at + ": " + first[0] + " then " + speed);
				if (at == SETTLED + WATCHED)
					helper.succeed();
			});
		}
	}
}
