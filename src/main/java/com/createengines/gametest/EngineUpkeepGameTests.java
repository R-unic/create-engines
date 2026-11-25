package com.createengines.gametest;

import com.createengines.CreateEngines;
import com.createengines.content.blackbox.BlackBoxBlockEntity;
import com.createengines.content.crankshaft.CrankshaftBlockEntity;
import com.createengines.engine.EngineController;
import com.createengines.registry.CEBlocks;
import com.simibubi.create.AllBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * What a server does to keep engines up to date without being told: the periodic look at each engine, the gearing it finds then,
 * and the Black Box remembering which engine it records. None of these engines burns any fuel.
 */
@GameTestHolder(CreateEngines.MOD_ID)
@PrefixGameTestTemplate(false)
public class EngineUpkeepGameTests {

	/** The longest an engine goes between two looks at itself ({@code EngineTopology}'s revalidation), with a little to spare. */
	private static final int REVALIDATED_WITHIN = 105;

	private static EngineController controller(GameTestHelper helper, BlockPos primary) {
		CrankshaftBlockEntity crank = helper.getBlockEntity(primary);
		if (crank.controller() == null)
			helper.fail("no engine at the crankshaft", primary);
		return crank.controller();
	}

	/** Builds the test engine and takes the starter away at once, so that nothing turns. */
	private static void buildStill(GameTestHelper helper, BlockPos origin) {
		TestEngine.build(helper.getLevel(), helper.absolutePos(origin), true);
		helper.setBlock(origin.offset(TestEngine.STARTER_POWER), Blocks.AIR);
	}

	@GameTest(template = "empty", timeoutTicks = 400)
	public static void aRevalidatedEngineStillHearsFromItsBlocksAtOnce(GameTestHelper helper) {
		buildStill(helper, BlockPos.ZERO);
		EngineController[] built = new EngineController[1];
		helper.runAtTickTime(20, () -> built[0] = controller(helper, TestEngine.PRIMARY));
		helper.runAtTickTime(20 + 2 * REVALIDATED_WITHIN, () -> {
			helper.assertTrue(controller(helper, TestEngine.PRIMARY) == built[0], "an unchanged engine keeps its controller");
			// The outlet is only the engine's through its member index: a missing entry would leave this to the next revalidation
			helper.setBlock(TestEngine.EXHAUST, Blocks.AIR);
		});
		helper.runAtTickTime(20 + 2 * REVALIDATED_WITHIN + 2, () -> {
			helper.assertTrue(controller(helper, TestEngine.PRIMARY) != built[0], "breaking its outlet should be noticed at once");
			helper.succeed();
		});
	}

	/**
	 * A Create clutch changes what is geared to what without a block being placed or broken, so only the periodic look sees it. Two
	 * crank lines geared together through a clutch on the second one's end. Create's clutch only parts a shaft that something is
	 * turning, so the starter cranks this one (with no fuel) all the way through.
	 */
	@GameTest(template = "empty_large", timeoutTicks = 600)
	public static void aClutchSplitsACoupledEngineAndJoinsItAgainWithinTheRevalidationInterval(GameTestHelper helper) {
		BlockPos origin = new BlockPos(4, 0, 4);
		TestEngine.build(helper.getLevel(), helper.absolutePos(origin), false);
		TestEngine.addCoupledLine(helper.getLevel(), helper.absolutePos(origin));
		// addCoupledLine gears the lines with cogwheels at x 0; they move one block out here, to leave room for the clutch
		for (int z = 2; z <= 4; z++) {
			helper.setBlock(origin.offset(0, 2, z), Blocks.AIR);
			helper.setBlock(origin.offset(-1, 2, z), TestEngine.axisX(AllBlocks.COGWHEEL.getDefaultState()));
		}
		helper.setBlock(origin.offset(0, 2, 2), TestEngine.axisX(AllBlocks.SHAFT.getDefaultState()));
		helper.setBlock(origin.offset(0, 2, 4), TestEngine.axisX(AllBlocks.CLUTCH.getDefaultState()));
		BlockPos primary = origin.offset(TestEngine.PRIMARY);
		BlockPos lever = origin.offset(0, 3, 4);
		helper.runAtTickTime(100, () -> {
			helper.assertTrue(controller(helper, primary).layout().lines() == 2, "the two lines should be one engine through the clutch");
			CrankshaftBlockEntity second = helper.getBlockEntity(origin.offset(1, 2, 4));
			helper.assertTrue(second.getSpeed() != 0, "the starter should be turning the second line through the clutch");
			helper.setBlock(lever, Blocks.REDSTONE_BLOCK);
		});
		helper.runAtTickTime(100 + REVALIDATED_WITHIN, () -> {
			helper.assertTrue(controller(helper, primary).layout().lines() == 1, "a powered clutch should have split them");
			helper.setBlock(lever, Blocks.AIR);
		});
		helper.runAtTickTime(100 + 2 * REVALIDATED_WITHIN, () -> {
			helper.assertTrue(controller(helper, primary).layout().lines() == 2, "and letting it go should have joined them again");
			helper.succeed();
		});
	}

	@GameTest(template = "empty", timeoutTicks = 400)
	public static void aBlackBoxRemembersItsEngineAndFindsOneBuiltAgainBesideIt(GameTestHelper helper) {
		buildStill(helper, BlockPos.ZERO);
		BlockPos at = TestEngine.THROW.north();
		helper.setBlock(at, CEBlocks.BLACK_BOX.getDefaultState());
		int[] searches = new int[1];
		helper.runAtTickTime(50, () -> {
			BlackBoxBlockEntity box = helper.getBlockEntity(at);
			helper.assertTrue(box.engineFound(), "the box should have found the engine");
			searches[0] = box.searchesForTests();
		});
		helper.runAtTickTime(150, () -> {
			BlackBoxBlockEntity box = helper.getBlockEntity(at);
			helper.assertTrue(box.engineFound(), "and still be recording it");
			helper.assertTrue(box.searchesForTests() - searches[0] <= 1,
				"five samples should cost one search at most: " + (box.searchesForTests() - searches[0]));
			for (int x = 1; x <= 3; x++)
				helper.setBlock(new BlockPos(x, 2, 2), Blocks.AIR);
		});
		helper.runAtTickTime(200, () -> {
			helper.assertTrue(!((BlackBoxBlockEntity) helper.getBlockEntity(at)).engineFound(), "with no crank there is no engine");
			buildStill(helper, BlockPos.ZERO);
		});
		helper.runAtTickTime(260, () -> {
			helper.assertTrue(((BlackBoxBlockEntity) helper.getBlockEntity(at)).engineFound(), "the engine built again should be found");
			helper.succeed();
		});
	}

	/** The box touches only the far end of a bent intake: breaking a pipe further along changes nothing beside the box. */
	@GameTest(template = "empty", timeoutTicks = 600)
	public static void aBlackBoxStopsRecordingAnEngineItNoLongerTouches(GameTestHelper helper) {
		buildStill(helper, BlockPos.ZERO);
		TestEngine.addBentIntake(helper.getLevel(), helper.absolutePos(BlockPos.ZERO));
		BlockPos at = new BlockPos(0, 4, 0);
		helper.setBlock(at, CEBlocks.BLACK_BOX.getDefaultState());
		helper.runAtTickTime(60, () -> {
			helper.assertTrue(((BlackBoxBlockEntity) helper.getBlockEntity(at)).engineFound(), "found along the intake");
			helper.setBlock(new BlockPos(2, 4, 0), Blocks.AIR);
		});
		// Ten samples, a second apart, is the longest it trusts what it found, and one more for the sample it lands on
		helper.runAtTickTime(60 + 230, () -> {
			helper.assertTrue(!((BlackBoxBlockEntity) helper.getBlockEntity(at)).engineFound(), "the broken intake leads nowhere");
			helper.succeed();
		});
	}
}
