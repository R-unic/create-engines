package com.createengines.gametest;

import com.createengines.CreateEngines;
import com.createengines.compat.sable.SableCompat;
import com.createengines.engine.sim.Altitude;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Engines on a moving structure (Create Aeronautics, through Sable). They run only when Sable is in the run
 * ({@code runGameTestServerSable}); without it each test passes at once, as there is nothing to check.
 */
@GameTestHolder(CreateEngines.MOD_ID)
@PrefixGameTestTemplate(false)
public class SableGameTests {

	@GameTest(template = "empty", timeoutTicks = 200)
	public static void aBlockOnAMovingStructureIsAskedForItsHeightInTheWorldNotItsPlots(GameTestHelper helper) {
		if (!SableCompat.installed()) {
			helper.succeed();
			return;
		}
		BlockPos origin = helper.absolutePos(BlockPos.ZERO);
		TestEngine.build(helper.getLevel(), origin, true);
		SableTestSupport.Held held = SableTestSupport.assemble(helper.getLevel(), origin, origin.offset(7, 5, 5), 2000);
		double y = SableCompat.worldY(helper.getLevel(), held.inPlot());
		if (Math.abs(y - held.structureY()) > 64)
			helper.fail("a block in a structure at y 2000 was reported at " + y + " (its plot height is " + held.inPlot()
				.getY() + ")");
		if (Altitude.density(y) > 0.3)
			helper.fail("at y 2000 the air is thin, got density " + Altitude.density(y));
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void aBlockOnTheGroundIsWhereItIs(GameTestHelper helper) {
		if (!SableCompat.installed()) {
			helper.succeed();
			return;
		}
		BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
		if (Math.abs(SableCompat.worldY(helper.getLevel(), pos) - (pos.getY() + 0.5)) > 1e-6)
			helper.fail("a block that is not on a moving structure must keep its own height");
		helper.succeed();
	}
}
