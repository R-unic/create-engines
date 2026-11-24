package com.createengines.gametest;

import com.createengines.CreateEngines;
import com.createengines.content.head.CylinderHeadBlockEntity;
import com.createengines.content.tool.BankBuilderItem;
import com.createengines.engine.sim.Tuning;
import com.createengines.registry.CEBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** The {@code features.meterAllHeads} setting: one head's metering dial is copied to the others of its engine, and only when on. */
@GameTestHolder(CreateEngines.MOD_ID)
@PrefixGameTestTemplate(false)
public class MeteringFollowGameTests {

	private static final BlockPos[] HEADS = {new BlockPos(2, 5, 3), new BlockPos(4, 5, 3), new BlockPos(2, 1, 3), new BlockPos(4, 1, 3)};

	/** A crank line with two throws and a flat bank of four cylinders and heads on it. */
	private static void buildFlatFour(GameTestHelper helper) {
		for (int x = 1; x <= 5; x++)
			helper.setBlock(new BlockPos(x, 3, 3), (x % 2 == 0 ? CEBlocks.CRANK_THROW : CEBlocks.CRANKSHAFT).getDefaultState()
				.setValue(BlockStateProperties.AXIS, Axis.X));
		BankBuilderItem.build(helper.getLevel(), helper.absolutePos(new BlockPos(2, 3, 3)), Direction.UP,
			BankBuilderItem.Layout.FLAT, helper.makeMockPlayer(GameType.CREATIVE), true);
	}

	private static int metering(GameTestHelper helper, BlockPos head) {
		CylinderHeadBlockEntity entity = helper.getBlockEntity(head);
		return entity.metering.getValue();
	}

	@GameTest(template = "empty", timeoutTicks = 100, batch = "meter-all-heads")
	public static void withTheSettingOnOneHeadsMeteringSetsTheRest(GameTestHelper helper) {
		buildFlatFour(helper);
		helper.runAtTickTime(20, () -> {
			Tuning.meterAllHeads = true;
			try {
				((CylinderHeadBlockEntity) helper.getBlockEntity(HEADS[0])).metering.setValue(15);
				for (BlockPos head : HEADS)
					helper.assertTrue(metering(helper, head) == 15, "Every head should read 15, " + head + " reads " + metering(helper, head));
				// Each head is still its own dial: one can be set apart, and the others follow it in turn
				((CylinderHeadBlockEntity) helper.getBlockEntity(HEADS[3])).metering.setValue(5);
				for (BlockPos head : HEADS)
					helper.assertTrue(metering(helper, head) == 5, "Setting another head sets them all again: " + head);
			} finally {
				Tuning.meterAllHeads = false;
			}
			helper.succeed();
		});
	}

	@GameTest(template = "empty", timeoutTicks = 100, batch = "meter-all-heads")
	public static void withTheSettingOffEveryHeadKeepsItsOwnMetering(GameTestHelper helper) {
		buildFlatFour(helper);
		helper.runAtTickTime(20, () -> {
			helper.assertFalse(Tuning.meterAllHeads, "Off by default");
			((CylinderHeadBlockEntity) helper.getBlockEntity(HEADS[0])).metering.setValue(15);
			helper.assertTrue(metering(helper, HEADS[0]) == 15, "The head set reads 15");
			for (int i = 1; i < HEADS.length; i++)
				helper.assertTrue(metering(helper, HEADS[i]) == CylinderHeadBlockEntity.DEFAULT_METERING,
					"Head " + i + " is untouched: " + metering(helper, HEADS[i]));
			helper.succeed();
		});
	}

	@GameTest(template = "empty", timeoutTicks = 100, batch = "meter-all-heads")
	public static void aHeadOfAnotherEngineIsNotTouched(GameTestHelper helper) {
		buildFlatFour(helper);
		helper.runAtTickTime(20, () -> {
			Tuning.meterAllHeads = true;
			try {
				// A head that does not belong to the engine is not touched by a change on one that does
				helper.setBlock(new BlockPos(8, 2, 3), CEBlocks.CYLINDER_HEAD.getDefaultState());
				((CylinderHeadBlockEntity) helper.getBlockEntity(HEADS[0])).metering.setValue(12);
				helper.assertTrue(metering(helper, new BlockPos(8, 2, 3)) == CylinderHeadBlockEntity.DEFAULT_METERING,
					"A head of another engine keeps its own figure");
			} finally {
				Tuning.meterAllHeads = false;
			}
			helper.succeed();
		});
	}
}
