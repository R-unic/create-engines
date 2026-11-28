package com.createengines.gametest;

import static com.createengines.gametest.EngineRigs.*;

import com.createengines.CreateEngines;
import com.createengines.content.crankshaft.CrankshaftBlockEntity;
import com.createengines.engine.EngineState;
import com.createengines.engine.EngineSummary;
import com.createengines.engine.topology.CylinderFault;
import com.createengines.registry.CEBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Flooded intakes and exhausts, submerged engines and dry pockets. Test engines as in {@link EngineGameTests}. */
@GameTestHolder(CreateEngines.MOD_ID)
@PrefixGameTestTemplate(false)
public class EngineWaterGameTests {

	/** Fuel with no intake: no fresh air ever gets in, so it never fires, not even on the air it was built with. */
	@GameTest(template = "empty", timeoutTicks = 700)
	public static void cylinderWithoutIntakeNeverFires(GameTestHelper helper) {
		buildSingleCylinder(helper, true);
		helper.setBlock(TestEngine.AIR_INTAKE, Blocks.AIR);
		for (int tick = 20; tick <= 600; tick += 20)
			helper.runAtTickTime(tick, () -> {
				EngineSummary summary = summary(helper);
				helper.assertTrue(summary.firing() == 0, "Cylinder with no intake fired: " + summary);
				CrankshaftBlockEntity crank = helper.getBlockEntity(PRIMARY);
				helper.assertTrue(crank.publishedCapacity() == 0, "Engine shouldn't provide stress capacity");
			});
		helper.runAtTickTime(650, () -> {
			helper.assertTrue(CylinderFault.NO_INTAKE.in(cylinder(helper).faults()),
				"Cylinder should report its missing intake");
			helper.succeed();
		});
	}

	/** Water held against an opening's mouth by glass on the sides it would run off to. */
	private static void flood(GameTestHelper helper, BlockPos mouth) {
		for (Direction side : new Direction[] {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.DOWN})
			if (helper.getBlockState(mouth.relative(side))
				.isAir())
				helper.setBlock(mouth.relative(side), Blocks.GLASS);
		helper.setBlock(mouth, Blocks.WATER);
	}

	/**
	 * An air intake that opens underwater draws no air, so nothing in the cylinder can burn however long the starter
	 * turns it, and the cylinder says the intake is flooded rather than covered. Drained, the engine starts.
	 */
	@GameTest(template = "empty", timeoutTicks = 1500)
	public static void floodedIntakeNeverFiresUntilItIsDrained(GameTestHelper helper) {
		buildSingleCylinder(helper, true);
		BlockPos mouth = TestEngine.AIR_INTAKE.north();
		flood(helper, mouth);
		for (int tick = 20; tick <= 400; tick += 20)
			helper.runAtTickTime(tick, () -> {
				EngineSummary summary = summary(helper);
				helper.assertTrue(summary.firing() == 0, "Cylinder with its intake underwater fired: " + summary);
				CrankshaftBlockEntity crank = helper.getBlockEntity(PRIMARY);
				helper.assertTrue(crank.publishedCapacity() == 0, "Engine shouldn't provide stress capacity");
			});
		helper.runAtTickTime(420, () -> {
			int faults = cylinder(helper).faults();
			helper.assertTrue(CylinderFault.INTAKE_FLOODED.in(faults), "Cylinder should report its flooded intake");
			helper.assertFalse(CylinderFault.INTAKE_BLOCKED.in(faults), "A flooded intake is not a covered one");
			helper.setBlock(mouth, Blocks.AIR);
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(helper.getTick() > 440, "Intake not drained yet");
			helper.assertFalse(CylinderFault.intakeShut(cylinder(helper).faults()),
				"A drained intake should be open again: " + cylinder(helper));
			helper.assertTrue(summary(helper).firing() == 1, "Engine should fire once it has air: " + summary(helper));
		});
	}

	/**
	 * No block of the mod is washed away by water flowing onto it: every one counts as solid, though a shaft or a pipe
	 * has too slight a shape to be so unasked. A crankshaft journal with a spring poured on it is still there.
	 */
	@GameTest(template = "empty", timeoutTicks = 200)
	public static void waterWashesNoEngineBlockAway(GameTestHelper helper) {
		for (Block block : net.minecraft.core.registries.BuiltInRegistries.BLOCK)
			if (net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block)
				.getNamespace()
				.equals(CreateEngines.MOD_ID) && !(block instanceof net.minecraft.world.level.block.LiquidBlock))
				helper.assertTrue(block.defaultBlockState()
					.blocksMotion(), block + " would be washed away by flowing water");
		BlockPos journal = new BlockPos(2, 2, 2);
		helper.setBlock(journal, CEBlocks.CRANKSHAFT.getDefaultState());
		helper.setBlock(journal.above(), Blocks.WATER);
		helper.runAtTickTime(100, () -> {
			helper.assertBlockPresent(CEBlocks.CRANKSHAFT.get(), journal);
			helper.setBlock(journal.above(), Blocks.AIR);
			helper.succeed();
		});
	}

	/**
	 * Water against the cylinder itself: the engine is underwater, and it never fires although its intake and exhaust
	 * both open in clear air. The cylinder says it is submerged, not that a port is flooded. Drained, the engine starts.
	 */
	@GameTest(template = "empty", timeoutTicks = 1500)
	public static void submergedCylinderNeverFiresWhateverItsPipesReach(GameTestHelper helper) {
		buildSingleCylinder(helper, true);
		BlockPos beside = TestEngine.CYLINDER.east();
		flood(helper, beside);
		for (int tick = 20; tick <= 400; tick += 20)
			helper.runAtTickTime(tick, () -> {
				EngineSummary summary = summary(helper);
				helper.assertTrue(summary.firing() == 0, "Cylinder sitting in water fired: " + summary);
				CrankshaftBlockEntity crank = helper.getBlockEntity(PRIMARY);
				helper.assertTrue(crank.publishedCapacity() == 0, "Engine shouldn't provide stress capacity");
			});
		helper.runAtTickTime(420, () -> {
			int faults = cylinder(helper).faults();
			helper.assertTrue(CylinderFault.SUBMERGED.in(faults), "Cylinder should report that it is underwater");
			helper.assertFalse(CylinderFault.INTAKE_FLOODED.in(faults) || CylinderFault.EXHAUST_FLOODED.in(faults),
				"Its intake and exhaust open in clear air");
			helper.setBlock(beside, Blocks.AIR);
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(helper.getTick() > 440, "Not drained yet");
			helper.assertFalse(CylinderFault.underwater(cylinder(helper).faults()),
				"A drained engine is no longer underwater: " + cylinder(helper));
			helper.assertTrue(summary(helper).firing() == 1, "Engine should fire once it is dry: " + summary(helper));
		});
	}

	/**
	 * Water against the head of a running engine stops it, though the head's intake and exhaust stay clear: an engine
	 * that sinks dies.
	 */
	@GameTest(template = "empty", timeoutTicks = 3000)
	public static void sinkingARunningEngineStallsIt(GameTestHelper helper) {
		buildSingleCylinder(helper, true);
		helper.runAtTickTime(300, () -> helper.setBlock(STARTER_POWER, Blocks.AIR));
		helper.runAtTickTime(500, () -> {
			helper.assertTrue(summary(helper).state() == EngineState.RUNNING, "Engine should be running first");
			flood(helper, TestEngine.HEAD.east());
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(helper.getTick() > 520, "Not sunk yet");
			helper.assertTrue(CylinderFault.SUBMERGED.in(cylinder(helper).faults()),
				"Cylinder should report that it is underwater: " + cylinder(helper));
			CrankshaftBlockEntity crank = helper.getBlockEntity(PRIMARY);
			helper.assertTrue(crank.getSpeed() == 0, "An engine underwater should stall, speed " + crank.getSpeed());
		});
	}

	/**
	 * An engine in a dry pocket under water keeps running: water a block away, behind glass, touches nothing of it. Only
	 * fluid against a face counts, and that is the ruling for 1.0.
	 */
	@GameTest(template = "empty", timeoutTicks = 3000)
	public static void anEngineInADryPocketKeepsRunning(GameTestHelper helper) {
		buildSingleCylinder(helper, true);
		helper.runAtTickTime(300, () -> helper.setBlock(STARTER_POWER, Blocks.AIR));
		helper.runAtTickTime(500, () -> {
			helper.assertTrue(summary(helper).state() == EngineState.RUNNING, "Engine should be running first");
			flood(helper, TestEngine.HEAD.east().east());
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(helper.getTick() > 700, "Not long enough in the pocket yet");
			helper.assertFalse(CylinderFault.underwater(cylinder(helper).faults()),
				"Water a block away touches nothing: " + cylinder(helper));
			helper.assertTrue(summary(helper).state() == EngineState.RUNNING, "Engine should still run: " + summary(helper));
		});
	}

	/** Water over the exhaust outlet of a running engine: the gas cannot get out against it, and the engine chokes. */
	@GameTest(template = "empty", timeoutTicks = 3000)
	public static void floodingTheExhaustStallsARunningEngine(GameTestHelper helper) {
		buildSingleCylinder(helper, true);
		helper.runAtTickTime(300, () -> helper.setBlock(STARTER_POWER, Blocks.AIR));
		helper.runAtTickTime(500, () -> {
			helper.assertTrue(summary(helper).state() == EngineState.RUNNING, "Engine should be running first");
			flood(helper, EXHAUST.west());
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(helper.getTick() > 520, "Exhaust not flooded yet");
			helper.assertTrue(CylinderFault.EXHAUST_FLOODED.in(cylinder(helper).faults()),
				"Cylinder should report its flooded exhaust: " + cylinder(helper));
			CrankshaftBlockEntity crank = helper.getBlockEntity(PRIMARY);
			helper.assertTrue(crank.getSpeed() == 0, "Engine that can't exhaust should stall, speed " + crank.getSpeed());
		});
	}
}
