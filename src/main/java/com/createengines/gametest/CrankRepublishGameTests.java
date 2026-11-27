package com.createengines.gametest;

import com.createengines.CreateEngines;
import com.createengines.content.crankshaft.CrankshaftBlockEntity;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** A running engine whose crankshaft Create has stopped, as it does when it moves one into a moving structure. */
@GameTestHolder(CreateEngines.MOD_ID)
@PrefixGameTestTemplate(false)
public class CrankRepublishGameTests {

	/**
	 * The engine had last sent Create a speed a little off the one it turns at now: too little to be worth sending again, and
	 * not equal to it either. Its shaft must still be turned again within a couple of seconds.
	 */
	@GameTest(template = "empty", timeoutTicks = 700)
	public static void aZeroedCrankTurnsAgainThoughTheSpeedLastSentIsALittleOff(GameTestHelper helper) {
		TestEngine.build(helper.getLevel(), helper.absolutePos(net.minecraft.core.BlockPos.ZERO), true);
		helper.runAtTickTime(100, () -> helper.setBlock(TestEngine.STARTER_POWER, Blocks.AIR));
		float[] offBy = new float[1];
		helper.runAtTickTime(500, () -> {
			CrankshaftBlockEntity crank = helper.getBlockEntity(TestEngine.PRIMARY);
			float sent = crank.getGeneratedSpeed();
			helper.assertTrue(Math.abs(sent) > 40, "the engine should be running, fast enough that half an rpm is within 4%: " + sent);
			// Half an rpm off: the engine only ever means to send whole numbers, so it can never match this by chance
			offBy[0] = sent + 0.5f * Math.signum(sent);
			crank.publishSpeed(offBy[0]);
			crank.detachKinetics();
			crank.setSpeed(0);
			crank.setNetwork(null);
			helper.assertTrue(crank.getSpeed() == 0, "the shaft should now stand still");
		});
		helper.runAtTickTime(545, () -> {
			CrankshaftBlockEntity crank = helper.getBlockEntity(TestEngine.PRIMARY);
			helper.assertTrue(crank.getSpeed() != 0, "a running engine's shaft was left standing; last sent "
				+ crank.getGeneratedSpeed());
			// A new speed sent in the meantime would have turned the shaft by itself and shown nothing
			helper.assertTrue(crank.getGeneratedSpeed() == offBy[0], "the engine's speed moved too far for this to show anything: "
				+ crank.getGeneratedSpeed() + " from " + offBy[0]);
			helper.succeed();
		});
	}
}
