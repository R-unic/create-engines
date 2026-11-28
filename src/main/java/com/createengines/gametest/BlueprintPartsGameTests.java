package com.createengines.gametest;

import static com.createengines.gametest.BlueprintRigs.*;

import java.util.List;

import com.createengines.CreateEngines;
import com.createengines.content.blueprint.BlueprintApplier;
import com.createengines.content.blueprint.BlueprintRecorder;
import com.createengines.content.blueprint.RecordedBlueprint;
import com.createengines.engine.blueprint.Blueprint;
import com.createengines.engine.blueprint.BlueprintCodec;
import com.createengines.engine.blueprint.BlueprintException;
import com.createengines.registry.CEBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** The fluid system, Black Box and transmission recorded with an engine. */
@GameTestHolder(CreateEngines.MOD_ID)
@PrefixGameTestTemplate(false)
public class BlueprintPartsGameTests {

	// ---- The fluid system and the Black Box ----

	/** Two pipes and a tank stacked above the test engine's head at {@code shift}, the pipes open to each other, the head and the tank. */
	private static void pipeFuelIn(GameTestHelper helper, BlockPos shift) {
		BlockState pipe = com.simibubi.create.AllBlocks.FLUID_PIPE.getDefaultState()
			.setValue(net.minecraft.world.level.block.PipeBlock.UP, true)
			.setValue(net.minecraft.world.level.block.PipeBlock.DOWN, true);
		helper.setBlock(TestEngine.HEAD.above()
			.offset(shift), pipe);
		helper.setBlock(TestEngine.HEAD.above(2)
			.offset(shift), pipe);
		helper.setBlock(TestEngine.HEAD.above(3)
			.offset(shift), com.simibubi.create.AllBlocks.FLUID_TANK.getDefaultState());
	}

	private static List<Blueprint.Part> fluidParts(Blueprint blueprint) {
		return blueprint.parts()
			.stream()
			.filter(p -> com.createengines.content.blueprint.EngineAttachments.isFluidSystemId(p.block()))
			.toList();
	}

	/** The pipes that carry fuel to the heads, and the tank they run to, are part of the engine's blueprint, at their offsets. */
	@GameTest(template = "empty")
	public static void theFluidSystemIsRecordedWithTheEngine(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		TestEngine.build(level, helper.absolutePos(BlockPos.ZERO), true);
		pipeFuelIn(helper, BlockPos.ZERO);
		BlueprintRecorder.Result result = BlueprintRecorder.record(level, helper.absolutePos(TestEngine.HEAD), "Piped", "t");
		if (!result.ok())
			helper.fail("recording failed: " + result.failure());
		List<Blueprint.Part> fluids = fluidParts(result.blueprint());
		BlockPos origin = TestEngine.PRIMARY;
		for (BlockPos at : List.of(TestEngine.HEAD.above(), TestEngine.HEAD.above(2), TestEngine.HEAD.above(3))) {
			BlockPos offset = at.subtract(origin);
			if (fluids.stream()
				.noneMatch(p -> p.x() == offset.getX() && p.y() == offset.getY() && p.z() == offset.getZ()))
				helper.fail("the fluid block at " + at + " is not in the blueprint: " + fluids);
		}
		if (result.skipped() != 0)
			helper.fail("nothing was left out of the piped test engine, yet " + result.skipped() + " were");
		helper.succeed();
	}

	/** A pipe that is not open towards the engine, and a tank no pipe reaches, are not the engine's fluid system. */
	@GameTest(template = "empty")
	public static void aPipeNotOpenToTheEngineIsNotRecorded(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		TestEngine.build(level, helper.absolutePos(BlockPos.ZERO), true);
		// A pipe with a gap of air between it and the head (Create joins any pipe set against a head, so it must not touch),
		// and a tank three blocks off with nothing between
		helper.setBlock(TestEngine.HEAD.above(2), com.simibubi.create.AllBlocks.FLUID_PIPE.getDefaultState());
		helper.setBlock(TestEngine.HEAD.east(3), com.simibubi.create.AllBlocks.FLUID_TANK.getDefaultState());
		BlueprintRecorder.Result result = BlueprintRecorder.record(level, helper.absolutePos(TestEngine.HEAD), "Loose", "t");
		if (!result.ok())
			helper.fail("recording failed: " + result.failure());
		List<Blueprint.Part> fluids = fluidParts(result.blueprint());
		if (!fluids.isEmpty())
			helper.fail("fluid blocks the engine is not piped to were recorded: " + fluids);
		helper.succeed();
	}

	/** A pipe run into a tank farm stops at the cap and the engine still records. */
	@GameTest(template = "empty_large", timeoutTicks = 200)
	public static void aHugeTankStopsAtTheCapAndTheEngineStillRecords(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		TestEngine.build(level, helper.absolutePos(BlockPos.ZERO), true);
		BlockState pipe = com.simibubi.create.AllBlocks.FLUID_PIPE.getDefaultState()
			.setValue(net.minecraft.world.level.block.PipeBlock.UP, true)
			.setValue(net.minecraft.world.level.block.PipeBlock.DOWN, true);
		helper.setBlock(TestEngine.HEAD.above(), pipe);
		int cap = com.createengines.content.blueprint.EngineAttachments.MAX_FLUID_BLOCKS;
		// A block of tank cells, many more than the cap holds
		for (int i = 0; i < cap + 40; i++)
			helper.setBlock(TestEngine.HEAD.above(2)
				.offset(i % 8, i / 64, (i / 8) % 8), com.simibubi.create.AllBlocks.FLUID_TANK.getDefaultState());
		BlueprintRecorder.Result result = BlueprintRecorder.record(level, helper.absolutePos(TestEngine.HEAD), "Farm", "t");
		if (!result.ok())
			helper.fail("an engine with a big tank on its pipes was not recorded: " + result.failure());
		if (fluidParts(result.blueprint())
			.size() > cap)
			helper.fail("the fluid system went past the cap: " + fluidParts(result.blueprint())
				.size());
		helper.succeed();
	}

	/** A blueprint recorded before fluids were recorded still applies to its own engine once pipes have been added. */
	@GameTest(template = "empty_large")
	public static void aBlueprintWithoutFluidsStillAppliesToAnEngineThatIsPiped(GameTestHelper helper) throws BlueprintException {
		buildPair(helper);
		RecordedBlueprint recorded = recordFirst(helper);
		pipeFuelIn(helper, SECOND);
		BlueprintApplier.Result applied = BlueprintApplier.apply(helper.getLevel(), helper.absolutePos(second(TestEngine.HEAD)), recorded);
		if (applied.status() != BlueprintApplier.Status.APPLIED)
			helper.fail("a blueprint with no fluid blocks was not applied to a piped engine: " + applied);
		helper.succeed();
	}

	/** A blueprint that holds the fluid system needs it on the engine it is applied to, and applies where it is built the same. */
	@GameTest(template = "empty_large")
	public static void aBlueprintWithFluidsNeedsThePipesOnTheEngine(GameTestHelper helper) throws BlueprintException {
		buildPair(helper);
		pipeFuelIn(helper, BlockPos.ZERO);
		pipeFuelIn(helper, SECOND);
		RecordedBlueprint recorded = recordFirst(helper);
		if (fluidParts(BlueprintCodec.decode(recorded.text()))
			.isEmpty())
			helper.fail("the recording holds no fluid block");
		BlueprintApplier.Result same = BlueprintApplier.apply(helper.getLevel(), helper.absolutePos(second(TestEngine.HEAD)), recorded);
		if (same.status() != BlueprintApplier.Status.APPLIED)
			helper.fail("an identically piped engine was not accepted: " + same);
		helper.setBlock(TestEngine.HEAD.above(3)
			.offset(SECOND), Blocks.AIR);
		BlueprintApplier.Result missing = BlueprintApplier.apply(helper.getLevel(), helper.absolutePos(second(TestEngine.HEAD)), recorded);
		if (missing.status() != BlueprintApplier.Status.DIFFERENT)
			helper.fail("an engine missing its tank was accepted: " + missing);
		helper.succeed();
	}

	/** A Black Box set against the engine is recorded, with its replay dial. */
	@GameTest(template = "empty")
	public static void aBlackBoxAgainstTheEngineIsRecorded(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		TestEngine.build(level, helper.absolutePos(BlockPos.ZERO), true);
		BlockPos box = TestEngine.HEAD.east();
		helper.setBlock(box, CEBlocks.BLACK_BOX.getDefaultState());
		BlueprintRecorder.Result result = BlueprintRecorder.record(level, helper.absolutePos(TestEngine.HEAD), "Boxed", "t");
		if (!result.ok())
			helper.fail("recording failed: " + result.failure());
		BlockPos offset = box.subtract(TestEngine.PRIMARY);
		Blueprint.Part found = result.blueprint()
			.parts()
			.stream()
			.filter(p -> p.x() == offset.getX() && p.y() == offset.getY() && p.z() == offset.getZ())
			.findFirst()
			.orElse(null);
		if (found == null || !found.block()
			.equals(CreateEngines.MOD_ID + ":black_box"))
			helper.fail("the Black Box beside the head is not in the blueprint: " + found);
		if (!found.dials()
			.containsKey("replay"))
			helper.fail("the Black Box's replay dial was not recorded: " + found.dials());
		helper.succeed();
	}

	/** A transmission on the end of the engine's shaft is recorded with its gear. */
	@GameTest(template = "empty")
	public static void aTransmissionOnTheShaftIsRecordedWithItsGear(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		TestEngine.build(level, helper.absolutePos(BlockPos.ZERO), true);
		BlockPos at = new BlockPos(5, 2, 2);
		helper.setBlock(at, TestEngine.axisX(CEBlocks.TRANSMISSION.getDefaultState()));
		BlueprintRecorder.Result result = BlueprintRecorder.record(level, helper.absolutePos(TestEngine.HEAD), "Geared", "t");
		if (!result.ok())
			helper.fail("recording failed: " + result.failure());
		BlockPos offset = at.subtract(TestEngine.PRIMARY);
		Blueprint.Part found = result.blueprint()
			.parts()
			.stream()
			.filter(p -> p.x() == offset.getX() && p.y() == offset.getY() && p.z() == offset.getZ())
			.findFirst()
			.orElse(null);
		if (found == null || !found.block()
			.equals(CreateEngines.MOD_ID + ":transmission"))
			helper.fail("the transmission on the shaft is not in the blueprint: " + found);
		if (!found.dials()
			.containsKey("gear"))
			helper.fail("the transmission's gear was not recorded: " + found.dials());
		helper.succeed();
	}

	/** A blueprint recorded before transmissions were recorded still applies to an engine that has one. */
	@GameTest(template = "empty")
	public static void aBlueprintWithoutATransmissionStillAppliesToAnEngineThatHasOne(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		TestEngine.build(level, helper.absolutePos(BlockPos.ZERO), true);
		helper.setBlock(new BlockPos(5, 2, 2), TestEngine.axisX(CEBlocks.TRANSMISSION.getDefaultState()));
		BlueprintRecorder.Result result = BlueprintRecorder.record(level, helper.absolutePos(TestEngine.HEAD), "Geared", "t");
		if (!result.ok())
			helper.fail("recording failed: " + result.failure());
		Blueprint old = new Blueprint("Old", "t", result.blueprint()
			.parts()
			.stream()
			.filter(p -> !p.block()
				.equals(CreateEngines.MOD_ID + ":transmission"))
			.toList());
		BlueprintApplier.Result applied = BlueprintApplier.apply(level, helper.absolutePos(TestEngine.HEAD), old);
		if (applied.status() == BlueprintApplier.Status.DIFFERENT || applied.status() == BlueprintApplier.Status.REFUSED)
			helper.fail("a blueprint with no transmission was not applied to an engine with one: " + applied);
		helper.succeed();
	}

	/** An older blueprint, with no Black Box in it, still applies to an engine that has one beside it. */
	@GameTest(template = "empty_large")
	public static void aBlueprintWithoutABlackBoxStillAppliesToAnEngineThatHasOne(GameTestHelper helper) throws BlueprintException {
		buildPair(helper);
		RecordedBlueprint recorded = recordFirst(helper);
		helper.setBlock(TestEngine.HEAD.east()
			.offset(SECOND), CEBlocks.BLACK_BOX.getDefaultState());
		BlueprintApplier.Result applied = BlueprintApplier.apply(helper.getLevel(), helper.absolutePos(second(TestEngine.HEAD)), recorded);
		if (applied.status() != BlueprintApplier.Status.APPLIED)
			helper.fail("a blueprint with no Black Box was not applied to an engine with one: " + applied);
		helper.succeed();
	}
}
