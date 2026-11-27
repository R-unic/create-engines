package com.createengines.gametest;

import com.createengines.CreateEngines;
import com.createengines.compat.pollution.PollutionCompat;
import com.createengines.config.CEConfig;
import com.createengines.content.airfilter.AirFilterBlock;
import com.createengines.content.airflow.PortOpeningBlock;
import com.createengines.content.crankshaft.CrankshaftBlockEntity;
import com.createengines.engine.EngineState;
import com.createengines.engine.EngineSummary;
import com.createengines.engine.sim.IntakeAir;
import com.createengines.engine.topology.EngineLayout;
import com.createengines.engine.topology.EngineScanner;
import com.createengines.registry.CEBlocks;
import com.createengines.registry.CECreativeTabs;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Polluted intake air and the air filter. These run without Pollution of the Realms: a stand-in says what the air at each
 * mouth holds, so what is tested is everything between that reading and the engine. The reading itself is the same cube the
 * outlet's area cap reads, which PollutionGameTests covers with the mod loaded.
 */
@GameTestHolder(CreateEngines.MOD_ID)
@PrefixGameTestTemplate(false)
public class AirFilterGameTests {

	private static final String OFF = "intake-air-off";
	private static final String DIRTY = "intake-air-dirty";
	private static final String FILTERED = "intake-air-filtered";

	@AfterBatch(batch = OFF)
	public static void afterOff(ServerLevel level) {
		restore();
	}

	@AfterBatch(batch = DIRTY)
	public static void afterDirty(ServerLevel level) {
		restore();
	}

	@AfterBatch(batch = FILTERED)
	public static void afterFiltered(ServerLevel level) {
		restore();
	}

	private static void restore() {
		CEConfig.POLLUTED_INTAKE.set(false);
		PollutionCompat.standInForTests(null);
	}

	/** The one cylinder test engine with its air intake moved a block out, and this block in the gap, or a pipe-like core. */
	private static BlockPos buildWithCore(GameTestHelper helper, net.minecraft.world.level.block.state.BlockState core) {
		TestEngine.build(helper.getLevel(), helper.absolutePos(BlockPos.ZERO), true);
		BlockPos gap = TestEngine.HEAD.east();
		helper.setBlock(TestEngine.AIR_INTAKE, Blocks.AIR);
		helper.setBlock(gap.east(), CEBlocks.AIR_INTAKE.getDefaultState()
			.setValue(PortOpeningBlock.FACING, Direction.EAST));
		helper.setBlock(gap, core);
		return gap;
	}

	private static EngineSummary summary(GameTestHelper helper) {
		CrankshaftBlockEntity crank = helper.getBlockEntity(TestEngine.PRIMARY);
		EngineSummary summary = crank.summary();
		if (summary == null)
			helper.fail("no engine", TestEngine.PRIMARY);
		return summary;
	}

	@GameTest(template = "empty", timeoutTicks = 900, batch = OFF)
	public static void withPollutedIntakeOffTheDirtiestAirCostsNothing(GameTestHelper helper) {
		PollutionCompat.standInForTests(opening -> Long.MAX_VALUE);
		TestEngine.build(helper.getLevel(), helper.absolutePos(BlockPos.ZERO), true);
		helper.runAtTickTime(300, () -> helper.setBlock(TestEngine.STARTER_POWER, Blocks.AIR));
		helper.runAtTickTime(700, () -> {
			EngineSummary summary = summary(helper);
			helper.assertTrue(summary.intakeDirt() == 0 && summary.state() == EngineState.RUNNING, "clean and running: " + summary);
			helper.succeed();
		});
	}

	@GameTest(template = "empty", timeoutTicks = 1000, batch = DIRTY)
	public static void dirtyAirCostsAnEngineAndItClearsWhenTheAirDoes(GameTestHelper helper) {
		long[] held = {Long.MAX_VALUE};
		PollutionCompat.standInForTests(opening -> held[0]);
		CEConfig.POLLUTED_INTAKE.set(true);
		TestEngine.build(helper.getLevel(), helper.absolutePos(BlockPos.ZERO), true);
		helper.runAtTickTime(300, () -> helper.setBlock(TestEngine.STARTER_POWER, Blocks.AIR));
		helper.runAtTickTime(700, () -> {
			EngineSummary summary = summary(helper);
			helper.assertTrue(Math.abs(summary.intakeDirt() - IntakeAir.MAX_LOSS) < 0.006, "loses the most there is: " + summary);
			helper.assertTrue(summary.state() == EngineState.RUNNING, "and still runs: " + summary);
			held[0] = 0;
		});
		helper.runAtTickTime(800, () -> {
			helper.assertTrue(summary(helper).intakeDirt() == 0, "clean air again: " + summary(helper));
			helper.succeed();
		});
	}

	@GameTest(template = "empty", timeoutTicks = 1700, batch = FILTERED)
	public static void anAirFilterJoinsTheIntakeAndTakesMostOfTheLossBack(GameTestHelper helper) {
		PollutionCompat.standInForTests(opening -> Long.MAX_VALUE);
		CEConfig.POLLUTED_INTAKE.set(true);
		buildWithCore(helper, CEBlocks.AIR_FILTER.getDefaultState()
			.setValue(AirFilterBlock.AXIS, Axis.X));
		helper.runAtTickTime(300, () -> helper.setBlock(TestEngine.STARTER_POWER, Blocks.AIR));
		helper.runAtTickTime(900, () -> {
			EngineSummary summary = summary(helper);
			helper.assertTrue(summary.state() == EngineState.RUNNING, "breathes through the filter: " + summary);
			helper.assertTrue(Math.abs(summary.intakeDirt() - IntakeAir.MAX_LOSS * IntakeAir.FILTERED_SHARE) < 0.006,
				"only what gets past the filter: " + summary);
			EngineLayout layout = EngineScanner.scan(helper.getLevel(), helper.absolutePos(TestEngine.PRIMARY));
			helper.assertTrue(layout != null && layout.intercoolers()
				.isEmpty() && layout.ports()
				.stream()
				.anyMatch(network -> network.filtering() == IntakeAir.Filter.INLINE), "a filter and no intercooler: " + layout);
			helper.succeed();
		});
	}

	@GameTest(template = "empty", batch = OFF)
	public static void withoutThePollutionModTheFilterCannotBeMadeOrPickedOut(GameTestHelper helper) {
		if (PollutionCompat.installed()) {
			helper.succeed();
			return;
		}
		helper.assertTrue(helper.getLevel()
			.getRecipeManager()
			.byKey(ResourceLocation.fromNamespaceAndPath(CreateEngines.MOD_ID, "air_filter"))
			.isEmpty(), "no recipe without the mod");
		helper.assertTrue(!CECreativeTabs.isListed(CEBlocks.AIR_FILTER.asItem()), "not in the creative tab");
		helper.assertTrue(CECreativeTabs.isListed(CEBlocks.INTERCOOLER.asItem()), "other parts still are");
		helper.setBlock(new BlockPos(2, 2, 2), CEBlocks.AIR_FILTER.getDefaultState());
		helper.assertBlockPresent(CEBlocks.AIR_FILTER.get(), new BlockPos(2, 2, 2));
		helper.succeed();
	}
}
