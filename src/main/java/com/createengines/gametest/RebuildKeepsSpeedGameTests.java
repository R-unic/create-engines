package com.createengines.gametest;

import com.createengines.CreateEngines;
import com.createengines.content.battery.BatteryBlockEntity;
import com.createengines.content.crankshaft.CrankshaftBlockEntity;
import com.createengines.registry.CEBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * An engine rebuilt while it runs (a battery added to its bank, say) carries on at the speed it had. Two identical rotary
 * engines run side by side; only one has a battery added, and the two must stay together afterwards. A rotor's three chambers
 * share one housing block, so a rebuild that matched old state to new by block alone gave all three the first chamber's gas
 * and the engine lost speed it should have kept.
 */
@GameTestHolder(CreateEngines.MOD_ID)
@PrefixGameTestTemplate(false)
public class RebuildKeepsSpeedGameTests {

	/** Far enough from the first engine that the two never touch. */
	private static final BlockPos APART = new BlockPos(0, 0, 12);
	private static final int EXTEND_AT = 600;

	private static double rpm(GameTestHelper helper, BlockPos offset) {
		CrankshaftBlockEntity crank = helper.getBlockEntity(TestEngine.PRIMARY.offset(offset));
		helper.assertTrue(crank.summary() != null, "The engine at " + offset + " should have a summary");
		return crank.summary()
			.rpm();
	}

	@GameTest(template = "empty_large", timeoutTicks = 900, batch = "rebuild-keeps-speed")
	public static void aRotaryKeepsItsSpeedWhenABatteryIsAddedToItsBank(GameTestHelper helper) {
		for (BlockPos offset : new BlockPos[] {BlockPos.ZERO, APART}) {
			TestEngine.buildRotary(helper.getLevel(), helper.absolutePos(offset), true);
			helper.setBlock(TestEngine.ALTERNATOR.offset(offset), Blocks.AIR);
			BatteryBlockEntity battery = helper.getBlockEntity(TestEngine.BATTERY.offset(offset));
			battery.charge(BatteryBlockEntity.CAPACITY);
			helper.runAtTickTime(300, () -> helper.setBlock(TestEngine.STARTER_POWER.offset(offset), Blocks.AIR));
		}
		helper.runAtTickTime(EXTEND_AT, () -> {
			helper.assertTrue(rpm(helper, BlockPos.ZERO) > 100, "Both engines should be running first");
			helper.setBlock(TestEngine.BATTERY.west()
				.offset(APART), CEBlocks.BATTERY.getDefaultState());
		});
		for (int tick = EXTEND_AT + 20; tick <= EXTEND_AT + 100; tick += 10) {
			int at = tick;
			helper.runAtTickTime(tick, () -> {
				double control = rpm(helper, BlockPos.ZERO);
				double extended = rpm(helper, APART);
				helper.assertTrue(Math.abs(extended - control) <= control * 0.01,
					"At tick " + at + " the engine with a battery added turns at " + extended + " where the same engine without one turns at "
						+ control);
				if (at == EXTEND_AT + 100)
					helper.succeed();
			});
		}
	}
}
