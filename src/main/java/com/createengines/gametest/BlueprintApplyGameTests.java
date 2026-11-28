package com.createengines.gametest;

import static com.createengines.gametest.BlueprintRigs.*;

import java.util.List;
import java.util.Map;

import com.createengines.CreateEngines;
import com.createengines.content.blueprint.BlueprintApplier;
import com.createengines.content.blueprint.BlueprintRecorder;
import com.createengines.content.blueprint.RecordedBlueprint;
import com.createengines.content.plate.MakerPlateBlockEntity;
import com.createengines.engine.sim.PlateFigures;
import com.createengines.engine.blueprint.Blueprint;
import com.createengines.engine.blueprint.BlueprintCodec;
import com.createengines.engine.blueprint.BlueprintException;
import com.createengines.registry.CEBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Applying a blueprint's settings to an engine that is already built. */
@GameTestHolder(CreateEngines.MOD_ID)
@PrefixGameTestTemplate(false)
public class BlueprintApplyGameTests {

	@GameTest(template = "empty_large")
	public static void applyingCopiesEveryDialOntoAnIdenticalEngine(GameTestHelper helper) throws BlueprintException {
		buildPair(helper);
		RecordedBlueprint recorded = recordFirst(helper);
		int[] before = settingsOfSecond(helper);
		if (before[0] == 14 && before[1] == 6 && before[2] == 9)
			helper.fail("the second engine should start with other settings: " + java.util.Arrays.toString(before));
		BlueprintApplier.Result result = BlueprintApplier.apply(helper.getLevel(), helper.absolutePos(second(TestEngine.HEAD)), recorded);
		if (result.status() != BlueprintApplier.Status.APPLIED || result.changed() < 3)
			helper.fail("applying gave " + result);
		int[] after = settingsOfSecond(helper);
		if (after[0] != 14 || after[1] != 6 || after[2] != 9)
			helper.fail("the dials are " + java.util.Arrays.toString(after) + ", expected 14, 6, 9");
		BlueprintApplier.Result again = BlueprintApplier.apply(helper.getLevel(), helper.absolutePos(second(TestEngine.CYLINDER)), recorded);
		if (again.status() != BlueprintApplier.Status.ALREADY_SET || again.changed() != 0)
			helper.fail("a second application should find nothing to do: " + again);
		helper.succeed();
	}

	/** A blueprint recorded before Create's blocks were recorded (none of them in it) still applies to its own engine. */
	@GameTest(template = "empty_large")
	public static void aBlueprintWithoutCreateBlocksStillAppliesToAnEngineThatHasThem(GameTestHelper helper) throws BlueprintException {
		buildPair(helper);
		BlueprintRecorder.Result result = BlueprintRecorder.record(helper.getLevel(), helper.absolutePos(TestEngine.HEAD), "Old", "t");
		Blueprint old = new Blueprint("Old", "t", result.blueprint()
			.parts()
			.stream()
			.filter(p -> !p.block()
				.startsWith("create:"))
			.toList());
		if (old.parts()
			.size() >= result.blueprint()
				.parts()
				.size())
			helper.fail("the test engine should have a Create block in it");
		BlueprintApplier.Result applied = BlueprintApplier.apply(helper.getLevel(), helper.absolutePos(second(TestEngine.HEAD)), old);
		if (applied.status() != BlueprintApplier.Status.APPLIED)
			helper.fail("an older blueprint was not applied: " + applied);
		helper.succeed();
	}

	/** A blueprint that does hold Create's blocks is a different build when the engine lacks one of them. */
	@GameTest(template = "empty_large")
	public static void aBlueprintWithCreateBlocksNeedsThemOnTheEngine(GameTestHelper helper) throws BlueprintException {
		buildPair(helper);
		RecordedBlueprint recorded = recordFirst(helper);
		Blueprint full = BlueprintCodec.decode(recorded.text());
		Blueprint.Part create = full.parts()
			.stream()
			.filter(p -> p.block()
				.startsWith("create:"))
			.findFirst()
			.orElse(null);
		if (create == null)
			helper.fail("the recording holds no Create block");
		BlockPos at = second(TestEngine.PRIMARY).offset(create.x(), create.y(), create.z());
		helper.getLevel()
			.setBlock(helper.absolutePos(at), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
		BlueprintApplier.Result result = BlueprintApplier.apply(helper.getLevel(), helper.absolutePos(second(TestEngine.HEAD)), recorded);
		if (result.status() == BlueprintApplier.Status.APPLIED || result.status() == BlueprintApplier.Status.ALREADY_SET)
			helper.fail("an engine without its Create block was accepted: " + result);
		helper.succeed();
	}

	@GameTest(template = "empty_large")
	public static void anEngineWithAnExtraBlockIsADifferentBuild(GameTestHelper helper) throws BlueprintException {
		buildPair(helper);
		RecordedBlueprint recorded = recordFirst(helper);
		// One more crankshaft block on the end of the second engine's line
		helper.getLevel()
			.setBlock(helper.absolutePos(second(new BlockPos(0, 2, 2))), TestEngine.axisX(CEBlocks.CRANKSHAFT.getDefaultState()), Block.UPDATE_ALL);
		int[] before = settingsOfSecond(helper);
		BlueprintApplier.Result result = BlueprintApplier.apply(helper.getLevel(), helper.absolutePos(second(TestEngine.HEAD)), recorded);
		if (result.status() != BlueprintApplier.Status.DIFFERENT || result.mismatches()
			.isEmpty())
			helper.fail("a longer crankshaft should be a different build: " + result);
		if (!java.util.Arrays.equals(before, settingsOfSecond(helper)))
			helper.fail("a refused blueprint still changed a dial");
		helper.succeed();
	}

	@GameTest(template = "empty_large")
	public static void aDamagedOrHostileBlueprintChangesNothing(GameTestHelper helper) throws BlueprintException {
		buildPair(helper);
		int[] before = settingsOfSecond(helper);
		BlockPos click = helper.absolutePos(second(TestEngine.HEAD));
		String[] texts = {
			"", "hello", "CEBP1:!!!", "CEBP1:AAAA", "CEBP9:AAAA",
			BlueprintCodec.encode(new Blueprint("evil", "", List.of(
				new Blueprint.Part(0, 0, 0, "create_engines:cylinder_head", Map.of(), Map.of("fuel_metering", 9999))))),
			BlueprintCodec.encode(new Blueprint("evil", "", List.of(
				new Blueprint.Part(0, 0, 0, "minecraft:tnt", Map.of(), Map.of()))))
		};
		for (String text : texts) {
			BlueprintApplier.Result result = BlueprintApplier.apply(helper.getLevel(), click, new RecordedBlueprint(text, "x", 1));
			if (result.status() != BlueprintApplier.Status.REFUSED)
				helper.fail("text " + text + " should be refused, got " + result);
		}
		if (!java.util.Arrays.equals(before, settingsOfSecond(helper)))
			helper.fail("a hostile blueprint changed a dial");
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void applyingToSomethingThatIsNotAnEngineSaysSo(GameTestHelper helper) throws BlueprintException {
		helper.setBlock(new BlockPos(2, 2, 2), Blocks.STONE);
		RecordedBlueprint recorded = new RecordedBlueprint(BlueprintCodec.encode(new Blueprint("x", "", List.of(
			new Blueprint.Part(0, 0, 0, "create_engines:crankshaft", Map.of("axis", "x"), Map.of())))), "x", 1);
		BlueprintApplier.Result result = BlueprintApplier.apply(helper.getLevel(), helper.absolutePos(new BlockPos(2, 2, 2)), recorded);
		if (result.status() != BlueprintApplier.Status.NO_ENGINE)
			helper.fail("stone should be no engine: " + result);
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void aStampedMakersPlateGivesTheBlueprintItsFiguresAndName(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		TestEngine.build(level, helper.absolutePos(BlockPos.ZERO), true);
		BlockPos at = TestEngine.PRIMARY.north();
		helper.setBlock(at, CEBlocks.MAKER_PLATE.getDefaultState()
			.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING, Direction.NORTH));
		MakerPlateBlockEntity plate = helper.getBlockEntity(at);
		plate.setNameAndBuilder("Rocket", "me");
		BlockPos click = helper.absolutePos(TestEngine.HEAD);

		BlueprintRecorder.Result unstamped = BlueprintRecorder.record(level, click, "", "t");
		if (!unstamped.ok() || unstamped.blueprint()
			.figures() != null)
			helper.fail("an unstamped plate should give no figures: " + unstamped);
		if (!"Rocket".equals(unstamped.blueprint()
			.name()))
			helper.fail("an unnamed blueprint should take the plate's name: " + unstamped.blueprint()
				.name());

		PlateFigures figures = new PlateFigures(88.8f, 120, 4321.5f, 240);
		plate.stamp(figures);
		BlueprintRecorder.Result stamped = BlueprintRecorder.record(level, click, "Named", "t");
		if (!figures.equals(stamped.blueprint()
			.figures()))
			helper.fail("the plate's figures were not recorded: " + stamped.blueprint()
				.figures());
		if (!"Named".equals(stamped.blueprint()
			.name()))
			helper.fail("a name given on an anvil should win over the plate's: " + stamped.blueprint()
				.name());
		if (stamped.blueprint()
			.parts()
			.stream()
			.anyMatch(p -> p.block()
				.endsWith(":maker_plate")))
			helper.fail("the plate is not part of the engine's build");
		BlueprintRecorder.Result bare = BlueprintRecorder.record(level, click, "", "t");
		if (!figures.equals(bare.blueprint()
			.figures()))
			helper.fail("the figures should be recorded whatever the name");
		helper.succeed();
	}
}
