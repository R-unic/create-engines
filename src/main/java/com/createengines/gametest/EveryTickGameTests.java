package com.createengines.gametest;

import com.createengines.CreateEngines;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** {@link EveryTick} must miss no tick and double none: tests that watch or hold something every tick rely on it. */
@GameTestHolder(CreateEngines.MOD_ID)
@PrefixGameTestTemplate(false)
public class EveryTickGameTests {

	@GameTest(template = "empty", timeoutTicks = 200)
	public static void aTaskRunsOnceOnEveryTick(GameTestHelper helper) {
		long first = helper.getTick();
		long[] runs = {0};
		EveryTick.run(helper, () -> {
			long now = helper.getTick();
			helper.assertTrue(now == first + runs[0], "Run " + runs[0] + " came at tick " + now);
			runs[0]++;
			if (runs[0] == 100)
				helper.succeed();
		});
	}
}
