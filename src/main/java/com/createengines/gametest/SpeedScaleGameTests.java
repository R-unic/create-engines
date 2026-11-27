package com.createengines.gametest;

import com.createengines.CreateEngines;
import com.createengines.config.CEConfig;
import com.createengines.content.crankshaft.CrankshaftBlockEntity;
import com.createengines.engine.EngineController;
import com.simibubi.create.infrastructure.config.AllConfigs;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** {@code balance.rpmScale}: a faster shaft carrying the same SU from the same engine. */
@GameTestHolder(CreateEngines.MOD_ID)
@PrefixGameTestTemplate(false)
public class SpeedScaleGameTests {

	/**
	 * One engine, settled at 1 and then at 2 with Create's limit doubled: the shaft turns twice as fast with half the
	 * capacity per RPM, and the dynamometer's curve is the same one stretched, so its SU at each point is unchanged.
	 */
	@GameTest(template = "empty", batch = "rpm-scale", timeoutTicks = 1600)
	public static void doublingTheScaleDoublesTheSpeedAndKeepsTheSu(GameTestHelper helper) {
		int originalMax = AllConfigs.server().kinetics.maxRotationSpeed.get();
		double originalScale = CEConfig.RPM_SCALE.get();
		AllConfigs.server().kinetics.maxRotationSpeed.set(256);
		CEConfig.RPM_SCALE.set(1.0);
		Runnable restore = () -> {
			AllConfigs.server().kinetics.maxRotationSpeed.set(originalMax);
			CEConfig.RPM_SCALE.set(originalScale);
		};
		TestEngine.build(helper.getLevel(), helper.absolutePos(BlockPos.ZERO), true);
		helper.runAtTickTime(100, () -> helper.setBlock(TestEngine.STARTER_POWER, Blocks.AIR));
		float[] speed = new float[1];
		float[] capacity = new float[1];
		EngineController.Curve[] before = new EngineController.Curve[1];
		helper.runAtTickTime(600, () -> {
			CrankshaftBlockEntity crank = helper.getBlockEntity(TestEngine.PRIMARY);
			speed[0] = Math.abs(crank.getGeneratedSpeed());
			capacity[0] = crank.publishedCapacity();
			before[0] = crank.controller()
				.curve();
			check(helper, restore, speed[0] > 40 && capacity[0] > 0 && before[0].complete(),
				"the engine should be running and measured: " + speed[0] + " rpm, " + capacity[0]);
			AllConfigs.server().kinetics.maxRotationSpeed.set(512);
			CEConfig.RPM_SCALE.set(2.0);
		});
		helper.runAtTickTime(1200, () -> {
			CrankshaftBlockEntity crank = helper.getBlockEntity(TestEngine.PRIMARY);
			float fastSpeed = Math.abs(crank.getGeneratedSpeed());
			float fastCapacity = crank.publishedCapacity();
			EngineController.Curve after = crank.controller()
				.curve();
			// What is sent to Create follows the engine in steps of 4 or 5 percent
			check(helper, restore, Math.abs(fastSpeed / speed[0] - 2) < 0.1, "speed " + speed[0] + " then " + fastSpeed);
			double su = speed[0] * capacity[0];
			double fastSu = fastSpeed * fastCapacity;
			check(helper, restore, Math.abs(fastSu / su - 1) < 0.1, "SU " + su + " then " + fastSu);
			// The curve as it is now and, in the same tick, as 1 shows it: the engine warming up cannot come between them
			AllConfigs.server().kinetics.maxRotationSpeed.set(256);
			CEConfig.RPM_SCALE.set(1.0);
			EngineController.Curve plain = crank.controller()
				.curve();
			check(helper, restore, after.rpm().length == plain.rpm().length && after.rpm().length == before[0].rpm().length,
				"the same engine speeds are measured: " + before[0].rpm().length + ", " + after.rpm().length);
			for (int i = 0; i < after.rpm().length; i++) {
				check(helper, restore, after.rpm()[i] == 2 * plain.rpm()[i], "curve speed " + i);
				double was = plain.rpm()[i] * plain.torque()[i];
				double is = after.rpm()[i] * after.torque()[i];
				check(helper, restore, Math.abs(is - was) <= 0.0001 * was, "curve SU at " + i + ": " + was + " then " + is);
			}
			check(helper, restore, Math.abs(crank.engineSummary()
				.rpm() / fastSpeed - 1) < 0.05, "the goggles show the shaft's speed");
			restore.run();
			helper.succeed();
		});
	}

	/** Puts the shared configs back before a failed assertion ends the test. */
	private static void check(GameTestHelper helper, Runnable restore, boolean passed, String message) {
		if (!passed)
			restore.run();
		helper.assertTrue(passed, message);
	}
}
