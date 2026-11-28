package com.createengines.gametest;

import com.createengines.CreateEngines;
import com.createengines.api.EngineExtensions;
import com.createengines.content.crankshaft.CrankshaftBlockEntity;
import com.createengines.engine.EngineProviders;
import com.createengines.engine.sim.TorqueSource;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** The example in docs/ADDONS.md ({@link ExampleAssist}) does what the guide says it does, in a real engine. */
@GameTestHolder(CreateEngines.MOD_ID)
@PrefixGameTestTemplate(false)
public class AddonExampleGameTests {

	private static int sources(GameTestHelper helper) {
		return helper.getLevel()
			.getBlockEntity(helper.absolutePos(TestEngine.PRIMARY)) instanceof CrankshaftBlockEntity crank && crank.controller() != null
				? crank.controller()
					.sources()
					.size()
				: -1;
	}

	/** Each of these registers a provider every engine in the world is asked about, so each runs alone in its own batch. */
	@GameTest(template = "empty", timeoutTicks = 600, batch = "addon-example-join")
	public static void theExampleAssistMotorJoinsAnEngineWhenPlacedAndLeavesWhenBroken(GameTestHelper helper) {
		ExampleAssist.AssistProvider provider = new ExampleAssist.AssistProvider(Blocks.GOLD_BLOCK);
		EngineExtensions.register(provider);
		TestEngine.build(helper.getLevel(), helper.absolutePos(BlockPos.ZERO), true);
		BlockPos motor = TestEngine.THROW.below();
		int[] phase = {0};
		EveryTick.run(helper, () -> {
			int count = sources(helper);
			if (count < 0)
				return;
			var level = helper.getLevel();
			if (phase[0] == 0) {
				// One cylinder, built before the motor was there
				helper.assertTrue(count == 1, "only the cylinder before the motor: " + count);
				phase[0] = 1;
				helper.setBlock(motor, Blocks.GOLD_BLOCK);
				EngineProviders.blockChanged(level, helper.absolutePos(motor), Blocks.GOLD_BLOCK.defaultBlockState());
			} else if (phase[0] == 1 && count == 2) {
				TorqueSource added = ((CrankshaftBlockEntity) level.getBlockEntity(helper.absolutePos(TestEngine.PRIMARY))).controller()
					.sources()
					.get(1);
				helper.assertTrue(added instanceof ExampleAssist.AssistMotor, "the added source is the example's: " + added);
				helper.assertTrue(Math.abs(added.availableWork() - ExampleAssist.TORQUE_PER_MOTOR * Math.toRadians(720)) < 1e-9,
					"one motor, one motor's work: " + added.availableWork());
				helper.assertTrue(!added.fired(), "an assist does not make the engine count as running");
				phase[0] = 2;
				helper.setBlock(motor, Blocks.AIR);
				// A broken block is asked about by what it was
				EngineProviders.blockChanged(level, helper.absolutePos(motor), Blocks.GOLD_BLOCK.defaultBlockState());
			} else if (phase[0] == 2 && count == 1) {
				EngineExtensions.unregister(provider);
				helper.succeed();
			}
		});
		helper.runAtTickTime(580, () -> {
			EngineExtensions.unregister(provider);
			helper.fail("the example never reached phase 2 (phase " + phase[0] + ", sources " + sources(helper) + ")");
		});
	}

	@GameTest(template = "empty", timeoutTicks = 800, batch = "addon-example-goggles")
	public static void theExampleAssistMotorAddsAGoggleLineToARunningEngine(GameTestHelper helper) {
		ExampleAssist.AssistProvider provider = new ExampleAssist.AssistProvider(Blocks.GOLD_BLOCK);
		EngineExtensions.register(provider);
		TestEngine.build(helper.getLevel(), helper.absolutePos(BlockPos.ZERO), true);
		helper.setBlock(TestEngine.THROW.below(), Blocks.GOLD_BLOCK);
		EngineProviders.blockChanged(helper.getLevel(), helper.absolutePos(TestEngine.THROW.below()),
			Blocks.GOLD_BLOCK.defaultBlockState());
		helper.runAtTickTime(300, () -> helper.setBlock(TestEngine.STARTER_POWER, Blocks.AIR));
		EveryTick.run(helper, () -> {
			if (!(helper.getLevel()
				.getBlockEntity(helper.absolutePos(TestEngine.PRIMARY)) instanceof CrankshaftBlockEntity crank) || crank.summary() == null
				|| crank.summary()
					.sourceLines()
					.isEmpty())
				return;
			EngineExtensions.unregister(provider);
			var line = crank.summary()
				.sourceLines()
				.get(0);
			helper.assertTrue(line.key()
				.equals("example_addon.assist") && line.args()
					.equals(java.util.List.of("1")), "the goggle line names the motor count: " + line);
			helper.succeed();
		});
		helper.runAtTickTime(780, () -> {
			EngineExtensions.unregister(provider);
			helper.fail("the engine never reported the example's goggle line");
		});
	}
}
