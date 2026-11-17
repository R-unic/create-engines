package com.createengines.gametest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.createengines.CreateEngines;
import com.createengines.content.blueprint.BlueprintApplier;
import com.createengines.content.blueprint.BlueprintBuilder;
import com.createengines.content.blueprint.BlueprintSchematic;
import com.createengines.content.blueprint.BlueprintImport;
import com.createengines.content.blueprint.BlueprintItem;
import com.createengines.content.blueprint.FramedBlueprintBlock;
import com.createengines.content.blueprint.FramedBlueprintBlockEntity;
import com.createengines.content.blueprint.BlueprintRecorder;
import com.createengines.content.blueprint.EngineBlueprintPolicy;
import com.createengines.content.blueprint.EngineDials;
import com.createengines.content.blueprint.RecordedBlueprint;
import com.createengines.content.camshaft.CamshaftBlockEntity;
import com.createengines.content.plate.MakerPlateBlockEntity;
import com.createengines.engine.sim.PlateFigures;
import com.createengines.content.crankshaft.CrankshaftBlockEntity;
import com.createengines.content.head.CylinderHeadBlockEntity;
import com.createengines.engine.blueprint.Blueprint;
import com.createengines.engine.blueprint.BlueprintCodec;
import com.createengines.engine.blueprint.BlueprintDiff;
import com.createengines.engine.blueprint.BlueprintException;
import com.createengines.engine.blueprint.BlueprintException.Refusal;
import com.createengines.engine.blueprint.BlueprintRules;
import com.createengines.engine.blueprint.FrameSheet;
import com.createengines.content.airflow.PortOpeningBlock;
import com.createengines.content.cylinder.CylinderBlock;
import com.createengines.registry.CEBlocks;
import com.createengines.registry.CEComponents;
import com.createengines.registry.CEItems;
import com.mojang.serialization.JsonOps;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Recording an engine into a blueprint: what is read, that nothing is changed, that what is recorded passes the server's
 * own rules, and that the table of dials stays true to the value boxes the blocks really have.
 */
@GameTestHolder(CreateEngines.MOD_ID)
@PrefixGameTestTemplate(false)
public class BlueprintGameTests {

	private static BlueprintRecorder.Result recordTestEngine(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		TestEngine.build(level, helper.absolutePos(BlockPos.ZERO), true);
		return BlueprintRecorder.record(level, helper.absolutePos(TestEngine.HEAD), "Little One", "tester");
	}

	private static Map<String, Blueprint.Part> byId(Blueprint blueprint) {
		Map<String, Blueprint.Part> found = new HashMap<>();
		for (Blueprint.Part part : blueprint.parts())
			found.putIfAbsent(part.block(), part);
		return found;
	}

	@GameTest(template = "empty")
	public static void aBuiltEngineIsRecordedBlockByBlock(GameTestHelper helper) {
		BlueprintRecorder.Result result = recordTestEngine(helper);
		if (!result.ok())
			helper.fail("recording failed: " + result.failure() + " " + result.detail());
		Blueprint bp = result.blueprint();
		Map<String, Blueprint.Part> parts = byId(bp);
		for (String id : List.of("crankshaft", "crank_throw", "cylinder", "cylinder_head", "cam_lobe", "camshaft"))
			if (!parts.containsKey(CreateEngines.MOD_ID + ":" + id))
				helper.fail("the blueprint has no " + id + ": " + parts.keySet());
		// Positions are offsets from the primary crankshaft
		BlockPos origin = TestEngine.PRIMARY;
		Blueprint.Part cylinder = parts.get(CreateEngines.MOD_ID + ":cylinder");
		BlockPos expected = TestEngine.CYLINDER.subtract(origin);
		if (cylinder.x() != expected.getX() || cylinder.y() != expected.getY() || cylinder.z() != expected.getZ())
			helper.fail("the cylinder is at " + cylinder.x() + "," + cylinder.y() + "," + cylinder.z() + ", expected " + expected);
		Blueprint.Part crank = bp.parts()
			.stream()
			.filter(p -> p.x() == 0 && p.y() == 0 && p.z() == 0)
			.findFirst()
			.orElse(null);
		if (crank == null || !crank.block()
			.endsWith(":crankshaft"))
			helper.fail("the primary crankshaft is not at the origin");
		if (!"x".equals(crank.state()
			.get("axis")))
			helper.fail("the crankshaft's axis was not recorded: " + crank.state());
		if (!cylinder.state()
			.getOrDefault("piston", "")
			.equals("true"))
			helper.fail("the cylinder's fitted piston was not recorded: " + cylinder.state());
		// Create's own blocks the scanner counts as part of the engine (here the cogwheel on the crankshaft line) are recorded
		// too; only another mod's blocks are left out and counted.
		if (bp.parts()
			.stream()
			.noneMatch(p -> p.block()
				.startsWith("create:")))
			helper.fail("Create's blocks in the engine were not recorded: " + parts.keySet());
		if (result.skipped() != 0)
			helper.fail("nothing but Create's and this mod's blocks is in the test engine, yet " + result.skipped() + " were left out");
		helper.succeed();
	}

	/** The gearing that turns the camshaft (a large cogwheel and two chain drives in the test engine) is recorded, at its offsets. */
	@GameTest(template = "empty")
	public static void theGearingToTheCamshaftIsRecorded(GameTestHelper helper) {
		BlueprintRecorder.Result result = recordTestEngine(helper);
		if (!result.ok())
			helper.fail("recording failed: " + result.failure());
		BlockPos origin = TestEngine.PRIMARY;
		for (BlockPos gear : List.of(new BlockPos(5, 3, 3), new BlockPos(6, 3, 3), new BlockPos(6, 4, 3))) {
			BlockPos offset = gear.subtract(origin);
			boolean found = result.blueprint()
				.parts()
				.stream()
				.anyMatch(p -> p.x() == offset.getX() && p.y() == offset.getY() && p.z() == offset.getZ()
					&& p.block()
						.startsWith("create:"));
			if (!found)
				helper.fail("the cam gearing block at " + gear + " is not in the blueprint");
		}
		if (result.skipped() != 0)
			helper.fail("a cam line was left out as unlinked: " + result.skipped());
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void everyDialOnTheEngineIsRecordedAtItsSetting(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		TestEngine.build(level, helper.absolutePos(BlockPos.ZERO), true);
		CylinderHeadBlockEntity head = helper.getBlockEntity(TestEngine.HEAD);
		head.metering.setValue(14);
		CrankshaftBlockEntity throwEntity = helper.getBlockEntity(TestEngine.THROW);
		throwEntity.throwAngle.setValue(6);
		CamshaftBlockEntity lobe = helper.getBlockEntity(TestEngine.LOBE);
		int lobeSetting = lobe.lobeAngle.getValue();
		BlueprintRecorder.Result result = BlueprintRecorder.record(level, helper.absolutePos(TestEngine.CYLINDER), "d", "t");
		if (!result.ok())
			helper.fail("recording failed: " + result.failure());
		Map<String, Blueprint.Part> parts = byId(result.blueprint());
		expectDial(helper, parts, "cylinder_head", "fuel_metering", 14);
		expectDial(helper, parts, "crank_throw", "throw_angle", 6);
		expectDial(helper, parts, "cam_lobe", "lobe_angle", lobeSetting);
		helper.succeed();
	}

	private static void expectDial(GameTestHelper helper, Map<String, Blueprint.Part> parts, String block, String dial, int value) {
		Blueprint.Part part = parts.get(CreateEngines.MOD_ID + ":" + block);
		if (part == null)
			helper.fail("no " + block + " in the blueprint");
		Integer got = part.dials()
			.get(dial);
		if (got == null || got != value)
			helper.fail(block + " " + dial + " was recorded as " + got + ", the block says " + value);
	}

	@GameTest(template = "empty")
	public static void recordingChangesNothingInTheWorld(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		TestEngine.build(level, helper.absolutePos(BlockPos.ZERO), true);
		List<BlockState> before = snapshot(helper);
		CylinderHeadBlockEntity head = helper.getBlockEntity(TestEngine.HEAD);
		int metering = head.metering.getValue();
		int fuel = head.tank()
			.getFluidAmount();
		for (int i = 0; i < 3; i++)
			BlueprintRecorder.record(level, helper.absolutePos(TestEngine.HEAD), "x", "y");
		if (!before.equals(snapshot(helper)))
			helper.fail("a block state changed while recording");
		if (head.metering.getValue() != metering || head.tank()
			.getFluidAmount() != fuel)
			helper.fail("a dial or tank changed while recording");
		helper.succeed();
	}

	private static List<BlockState> snapshot(GameTestHelper helper) {
		List<BlockState> states = new ArrayList<>();
		for (int x = 0; x < 8; x++)
			for (int y = 0; y < 6; y++)
				for (int z = 0; z < 6; z++)
					states.add(helper.getLevel()
						.getBlockState(helper.absolutePos(new BlockPos(x, y, z))));
		return states;
	}

	@GameTest(template = "empty")
	public static void somethingThatIsNotAnEngineIsRefused(GameTestHelper helper) {
		helper.setBlock(new BlockPos(2, 2, 2), Blocks.STONE);
		BlueprintRecorder.Result result = BlueprintRecorder.record(helper.getLevel(), helper.absolutePos(new BlockPos(2, 2, 2)),
			"x", "y");
		if (result.ok() || result.failure() != BlueprintRecorder.Failure.NO_ENGINE)
			helper.fail("stone recorded as " + result);
		BlueprintRecorder.Result air = BlueprintRecorder.record(helper.getLevel(), helper.absolutePos(new BlockPos(4, 4, 4)),
			"x", "y");
		if (air.ok())
			helper.fail("air recorded");
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void whatIsRecordedPassesTheServersOwnRulesAndSurvivesTheString(GameTestHelper helper) {
		BlueprintRecorder.Result result = recordTestEngine(helper);
		if (!result.ok())
			helper.fail("recording failed: " + result.failure());
		try {
			String text = BlueprintCodec.encode(result.blueprint());
			Blueprint read = BlueprintCodec.decode(text);
			BlueprintRules.check(read, EngineBlueprintPolicy.INSTANCE);
			if (read.parts()
				.size() != result.blueprint()
					.parts()
					.size())
				helper.fail("the string lost parts");
			if (!read.name()
				.equals("Little One") || !read.builder()
					.equals("tester"))
				helper.fail("name or builder lost: " + read.name() + " / " + read.builder());
		} catch (BlueprintException e) {
			helper.fail("the engine's own blueprint was refused: " + e.getMessage());
		}
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void theServersRulesRefuseWhatNoEngineHas(GameTestHelper helper) {
		String head = CreateEngines.MOD_ID + ":cylinder_head";
		refused(helper, Refusal.FOREIGN_BLOCK, new Blueprint.Part(0, 0, 0, "minecraft:tnt"));
		refused(helper, Refusal.FOREIGN_BLOCK, new Blueprint.Part(0, 0, 0, "create:not_a_block"));
		refused(helper, Refusal.FOREIGN_BLOCK, new Blueprint.Part(0, 0, 0, "createaddition:connector"));
		refused(helper, Refusal.FOREIGN_BLOCK, new Blueprint.Part(0, 0, 0, CreateEngines.MOD_ID + ":not_a_block"));
		refused(helper, Refusal.UNKNOWN_STATE, new Blueprint.Part(0, 0, 0, "create:shaft", Map.of("axis", "w"), Map.of()));
		try {
			BlueprintRules.check(new Blueprint("ok", "", List.of(
				new Blueprint.Part(0, 0, 0, "create:shaft", Map.of("axis", "x"), Map.of()))), EngineBlueprintPolicy.INSTANCE);
		} catch (BlueprintException e) {
			helper.fail("a Create shaft was refused: " + e.getMessage());
		}
		refused(helper, Refusal.UNKNOWN_STATE, new Blueprint.Part(0, 0, 0, head, Map.of("no_such_property", "x"), Map.of()));
		refused(helper, Refusal.UNKNOWN_STATE, new Blueprint.Part(0, 0, 0, head, Map.of("facing", "sideways"), Map.of()));
		refused(helper, Refusal.BAD_DIAL, new Blueprint.Part(0, 0, 0, head, Map.of(), Map.of("fuel_metering", 21)));
		refused(helper, Refusal.BAD_DIAL, new Blueprint.Part(0, 0, 0, head, Map.of(), Map.of("fuel_metering", 0)));
		refused(helper, Refusal.BAD_DIAL, new Blueprint.Part(0, 0, 0, head, Map.of(), Map.of("throw_angle", 3)));
		refused(helper, Refusal.BAD_DIAL, new Blueprint.Part(0, 0, 0, CreateEngines.MOD_ID + ":cylinder", Map.of(),
			Map.of("fuel_metering", 5)));
		try {
			BlueprintRules.check(new Blueprint("ok", "", List.of(
				new Blueprint.Part(0, 0, 0, head, Map.of("facing", "up"), Map.of("fuel_metering", 20)))),
				EngineBlueprintPolicy.INSTANCE);
		} catch (BlueprintException e) {
			helper.fail("a plain head with the largest metering was refused: " + e.getMessage());
		}
		helper.succeed();
	}

	private static void refused(GameTestHelper helper, Refusal expected, Blueprint.Part part) {
		try {
			BlueprintRules.check(new Blueprint("t", "", List.of(part)), EngineBlueprintPolicy.INSTANCE);
		} catch (BlueprintException e) {
			if (e.refusal() != expected)
				helper.fail(part + " was refused as " + e.refusal() + ", expected " + expected);
			return;
		}
		helper.fail(part + " was accepted");
	}

	/**
	 * The guard on the table of dials: every block of this mod with a value box is listed with exactly the dials it has,
	 * and each listed range is the one the value box really clamps to. Adding a dial or moving a range without the table
	 * goes red here.
	 */
	@GameTest(template = "empty_large")
	public static void theTableOfDialsMatchesEveryValueBoxItDescribes(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		List<String> problems = new ArrayList<>();
		int x = 2;
		int listedDials = 0;
		for (Block block : BuiltInRegistries.BLOCK) {
			String id = BuiltInRegistries.BLOCK.getKey(block)
				.toString();
			if (!id.startsWith(CreateEngines.MOD_ID + ":") || !(block instanceof EntityBlock))
				continue;
			BlockPos pos = helper.absolutePos(new BlockPos(x, 4, 2 + (x / 40) * 4));
			x = (x + 2) % 44;
			level.setBlock(pos, block.defaultBlockState(), Block.UPDATE_ALL);
			BlockEntity entity = level.getBlockEntity(pos);
			if (!(entity instanceof SmartBlockEntity smart))
				continue;
			List<ScrollValueBehaviour> real = new ArrayList<>();
			for (BlockEntityBehaviour behaviour : smart.getAllBehaviours())
				if (behaviour instanceof ScrollValueBehaviour value)
					real.add(value);
			List<ScrollValueBehaviour> found = new ArrayList<>();
			for (EngineDials.Dial dial : EngineDials.of(id)) {
				ScrollValueBehaviour behaviour = dial.on(entity);
				if (behaviour == null) {
					problems.add(id + " lists " + dial.name() + " but its block entity has none");
					continue;
				}
				found.add(behaviour);
				listedDials++;
				int original = behaviour.getValue();
				behaviour.setValue(Integer.MIN_VALUE);
				int low = behaviour.getValue();
				behaviour.setValue(Integer.MAX_VALUE);
				int high = behaviour.getValue();
				behaviour.setValue(original);
				if (low != dial.min() || high != dial.max())
					problems.add(id + " " + dial.name() + " is listed as " + dial.min() + ".." + dial.max() + " but the block takes "
						+ low + ".." + high);
			}
			if (real.size() != found.size())
				problems.add(id + " has " + real.size() + " value boxes and the table lists " + found.size());
		}
		int expected = 0;
		for (String listed : EngineDials.blocks())
			expected += EngineDials.of(listed)
				.size();
		if (listedDials != expected)
			problems.add("checked " + listedDials + " dials but the table lists " + expected);
		for (String listed : EngineDials.blocks())
			if (!BuiltInRegistries.BLOCK.containsKey(net.minecraft.resources.ResourceLocation.parse(listed)))
				problems.add("the table lists a block that does not exist: " + listed);
		if (!problems.isEmpty())
			helper.fail(String.join("; ", problems));
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void everyBlockThatAnEngineIsMadeOfFitsInAPart(GameTestHelper helper) {
		// A blueprint part holds at most 16 changed properties; check every block of this mod can stay inside that.
		List<String> problems = new ArrayList<>();
		for (Block block : BuiltInRegistries.BLOCK) {
			String id = BuiltInRegistries.BLOCK.getKey(block)
				.toString();
			if (!id.startsWith(CreateEngines.MOD_ID + ":"))
				continue;
			int count = block.getStateDefinition()
				.getProperties()
				.size();
			if (count > Blueprint.Limits.MAX_ENTRIES)
				problems.add(id + " has " + count + " state properties");
			for (var property : block.getStateDefinition()
				.getProperties())
				if (!property.getName()
					.matches("[a-z0-9_]+") || property.getName()
						.length() > Blueprint.Limits.MAX_KEY)
					problems.add(id + " property '" + property.getName() + "' cannot be written in a blueprint");
		}
		if (!problems.isEmpty())
			helper.fail(String.join("; ", problems));
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void aBlankBlueprintHoldsNothingAndARecordedOneKeepsItsEngine(GameTestHelper helper) {
		ItemStack stack = new ItemStack(CEItems.BLUEPRINT.get());
		if (BlueprintItem.recorded(stack) != null)
			helper.fail("a new blueprint should be blank");
		BlueprintRecorder.Result result = recordTestEngine(helper);
		try {
			String text = BlueprintCodec.encode(result.blueprint());
			stack.set(CEComponents.BLUEPRINT.get(), new RecordedBlueprint(text, "Little One", result.blueprint()
				.parts()
				.size()));
			var encoded = RecordedBlueprint.CODEC.encodeStart(JsonOps.INSTANCE, BlueprintItem.recorded(stack))
				.getOrThrow();
			var decoded = RecordedBlueprint.CODEC.parse(JsonOps.INSTANCE, encoded)
				.getOrThrow();
			if (!decoded.equals(BlueprintItem.recorded(stack)))
				helper.fail("the item data changed on a save round trip");
			Blueprint again = BlueprintCodec.decode(decoded.text());
			if (again.parts()
				.size() != decoded.parts())
				helper.fail("the saved part count does not match the string");
		} catch (BlueprintException e) {
			helper.fail("encode or decode failed: " + e.getMessage());
		}
		helper.succeed();
	}

	// Applying

	private static final BlockPos SECOND = new BlockPos(0, 0, 12);

	/** Two copies of the test engine, far enough apart to be separate engines. The first is tuned, the second left alone. */
	private static void buildPair(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		TestEngine.build(level, helper.absolutePos(BlockPos.ZERO), true);
		TestEngine.build(level, helper.absolutePos(SECOND), true);
		((CylinderHeadBlockEntity) helper.getBlockEntity(TestEngine.HEAD)).metering.setValue(14);
		((CrankshaftBlockEntity) helper.getBlockEntity(TestEngine.THROW)).throwAngle.setValue(6);
		((CamshaftBlockEntity) helper.getBlockEntity(TestEngine.LOBE)).lobeAngle.setValue(9);
	}

	private static RecordedBlueprint recordFirst(GameTestHelper helper) throws BlueprintException {
		BlueprintRecorder.Result result = BlueprintRecorder.record(helper.getLevel(), helper.absolutePos(TestEngine.HEAD), "Pair", "t");
		if (!result.ok())
			helper.fail("recording failed: " + result.failure());
		return new RecordedBlueprint(BlueprintCodec.encode(result.blueprint()), "Pair", result.blueprint()
			.parts()
			.size());
	}

	private static BlockPos second(BlockPos inFirst) {
		return inFirst.offset(SECOND);
	}

	private static int[] settingsOfSecond(GameTestHelper helper) {
		return new int[] { ((CylinderHeadBlockEntity) helper.getBlockEntity(second(TestEngine.HEAD))).metering.getValue(),
			((CrankshaftBlockEntity) helper.getBlockEntity(second(TestEngine.THROW))).throwAngle.getValue(),
			((CamshaftBlockEntity) helper.getBlockEntity(second(TestEngine.LOBE))).lobeAngle.getValue() };
	}

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
	public static void aDifferentBuildIsRefusedAndNothingIsChanged(GameTestHelper helper) throws BlueprintException {
		buildPair(helper);
		RecordedBlueprint recorded = recordFirst(helper);
		helper.getLevel()
			.setBlock(helper.absolutePos(second(TestEngine.CYLINDER)), CEBlocks.CYLINDER.getDefaultState()
				.setValue(CylinderBlock.FACING, Direction.UP)
				.setValue(CylinderBlock.HAS_PISTON, false)
				.setValue(CylinderBlock.HAS_ROD, true), Block.UPDATE_ALL);
		int[] before = settingsOfSecond(helper);
		BlueprintApplier.Result result = BlueprintApplier.apply(helper.getLevel(), helper.absolutePos(second(TestEngine.HEAD)), recorded);
		if (result.status() != BlueprintApplier.Status.DIFFERENT || result.mismatches()
			.isEmpty())
			helper.fail("a cylinder without its piston should be a different build: " + result);
		if (result.mismatches()
			.get(0)
			.kind() != BlueprintDiff.Kind.STATE || !result.mismatches()
				.get(0)
				.detail()
				.contains("piston"))
			helper.fail("the difference should name the piston: " + result.mismatches());
		if (!java.util.Arrays.equals(before, settingsOfSecond(helper)))
			helper.fail("a refused blueprint still changed a dial");
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

	// Building

	/** Where the third engine's crankshaft goes: clear space beside the two the pair tests use. */
	private static final BlockPos SITE = new BlockPos(20, 2, 2);

	private static RecordedBlueprint recordedTestEngine(GameTestHelper helper) throws BlueprintException {
		ServerLevel level = helper.getLevel();
		TestEngine.build(level, helper.absolutePos(BlockPos.ZERO), true);
		((CylinderHeadBlockEntity) helper.getBlockEntity(TestEngine.HEAD)).metering.setValue(14);
		((CrankshaftBlockEntity) helper.getBlockEntity(TestEngine.THROW)).throwAngle.setValue(6);
		BlueprintRecorder.Result result = BlueprintRecorder.record(level, helper.absolutePos(TestEngine.HEAD), "Site", "t");
		if (!result.ok())
			helper.fail("recording failed: " + result.failure());
		return new RecordedBlueprint(BlueprintCodec.encode(result.blueprint()), "Site", result.blueprint()
			.parts()
			.size());
	}

	private static int blocksAt(GameTestHelper helper, RecordedBlueprint recorded) throws BlueprintException {
		int found = 0;
		for (Blueprint.Part part : BlueprintCodec.decode(recorded.text())
			.parts())
			if (!helper.getLevel()
				.getBlockState(helper.absolutePos(SITE.offset(part.x(), part.y(), part.z())))
				.isAir())
				found++;
		return found;
	}

	/** Puts into the player's inventory exactly what building the blueprint costs. */
	private static void giveWhatItCosts(Player player, RecordedBlueprint recorded) throws BlueprintException {
		for (Blueprint.Part part : BlueprintCodec.decode(recorded.text())
			.parts())
			for (ItemStack stack : BlueprintBuilder.cost(BlueprintBuilder.stateOf(part)))
				player.getInventory()
					.add(stack.copy());
	}

	private static int carriedItems(Player player) {
		int total = 0;
		for (int slot = 0; slot < player.getInventory()
			.getContainerSize(); slot++)
			total += player.getInventory()
				.getItem(slot)
				.getCount();
		return total;
	}

	/** Where the four turned copies go, far enough apart that none touches another. */
	private static final BlockPos[] TURN_SITES = {new BlockPos(12, 2, 12), new BlockPos(34, 2, 12), new BlockPos(12, 2, 34),
		new BlockPos(34, 2, 34)};

	private static double angleGap(double a, double b) {
		return Math.abs(((a - b) % 360 + 540) % 360 - 180);
	}

	/**
	 * An engine built from a blueprint turned any of the four ways, with the mirror a turn sometimes needs, scans as the same
	 * engine: the same cylinders at the turned places, each with the same top-dead-centre angle and cam timing and no fault.
	 */
	@GameTest(template = "empty_large", timeoutTicks = 400)
	public static void aTurnedBuildScansAsTheSameEngine(GameTestHelper helper) throws BlueprintException {
		recordedTestEngine(helper);
		turnedCopiesScanAlike(helper, TestEngine.HEAD);
	}

	/** The twin-rotor engine, whose second rotor sits half a turn round: its throw angles are the sharpest test of the turn. */
	@GameTest(template = "empty_large", timeoutTicks = 400)
	public static void aTurnedRotaryBuildScansAsTheSameEngine(GameTestHelper helper) throws BlueprintException {
		TestEngine.buildRotary(helper.getLevel(), helper.absolutePos(BlockPos.ZERO), true);
		TestEngine.addSecondRotor(helper.getLevel(), helper.absolutePos(BlockPos.ZERO));
		turnedCopiesScanAlike(helper, TestEngine.HOUSING);
	}

	/** Two crank lines geared together, the second turning the same way: one engine on two parallel axes. */
	@GameTest(template = "empty_large", timeoutTicks = 400)
	public static void aTurnedCoupledBuildScansAsTheSameEngine(GameTestHelper helper) throws BlueprintException {
		TestEngine.build(helper.getLevel(), helper.absolutePos(BlockPos.ZERO), true);
		TestEngine.addCoupledLine(helper.getLevel(), helper.absolutePos(BlockPos.ZERO));
		turnedCopiesScanAlike(helper, TestEngine.HEAD);
	}

	private static void turnedCopiesScanAlike(GameTestHelper helper, BlockPos click) throws BlueprintException {
		ServerLevel level = helper.getLevel();
		BlueprintRecorder.Result result = BlueprintRecorder.record(level, helper.absolutePos(click), "Turn", "t");
		if (!result.ok())
			helper.fail("recording failed: " + result.failure());
		Blueprint plain = result.blueprint();
		RecordedBlueprint recorded = new RecordedBlueprint(BlueprintCodec.encode(plain), "Turn", plain.parts()
			.size());
		BlockPos primary = result.origin();
		var original = com.createengines.engine.topology.EngineScanner.scan(level, primary);
		if (original == null || original.incomplete())
			helper.fail("the original engine did not scan");
		Player player = helper.makeMockPlayer(net.minecraft.world.level.GameType.CREATIVE);
		for (int k = 0; k < 4; k++) {
			net.minecraft.world.level.block.Rotation rotation = com.createengines.content.blueprint.BlueprintTurner.TURNS.get(k);
			net.minecraft.world.level.block.Mirror mirror = com.createengines.content.blueprint.BlueprintTurner.mirrorFor(plain, rotation);
			BlockPos site = helper.absolutePos(TURN_SITES[k]);
			BlueprintBuilder.Result built = BlueprintBuilder.build(player, level, site, recorded, rotation);
			if (built.status() != BlueprintBuilder.Status.BUILT)
				helper.fail(rotation + " (" + mirror + "): building gave " + built);
			var layout = com.createengines.engine.topology.EngineScanner.scan(level, site);
			if (layout == null || layout.incomplete() || layout.broken())
				helper.fail(rotation + ": the turned engine did not scan whole");
			if (layout.cylinders().size() != original.cylinders().size())
				helper.fail(rotation + ": " + layout.cylinders().size() + " cylinders, expected " + original.cylinders().size());
			for (var cylinder : original.cylinders()) {
				BlockPos rel = cylinder.pos().subtract(primary);
				int[] at = com.createengines.content.blueprint.BlueprintTurner.place(rel.getX(), rel.getZ(), rotation, mirror);
				BlockPos expected = site.offset(at[0], rel.getY(), at[1]);
				var twin = layout.cylinders().stream().filter(c -> c.pos().equals(expected)).findFirst().orElse(null);
				if (twin == null)
					helper.fail(rotation + " (" + mirror + "): no cylinder at the turned place " + expected);
				if (angleGap(twin.tdcAngle(), cylinder.tdcAngle()) > 1e-3)
					helper.fail(rotation + " (" + mirror + "): top dead centre " + twin.tdcAngle() + ", expected " + cylinder.tdcAngle());
				if (angleGap(twin.lobeAngle(), cylinder.lobeAngle()) > 1e-3)
					helper.fail(rotation + " (" + mirror + "): cam timing " + twin.lobeAngle() + ", expected " + cylinder.lobeAngle());
				if (twin.faults() != cylinder.faults() || twin.sparkPlug() != cylinder.sparkPlug() || twin.sense() != cylinder.sense())
					helper.fail(rotation + " (" + mirror + "): faults " + twin.faults() + " expected " + cylinder.faults());
			}
		}
		helper.succeed();
	}

	/** A blueprint applies to a copy of its engine that was built turned, and sets the dials there. */
	@GameTest(template = "empty_large", timeoutTicks = 400)
	public static void aBlueprintAppliesToATurnedCopyOfItsEngine(GameTestHelper helper) throws BlueprintException {
		RecordedBlueprint recorded = recordedTestEngine(helper);
		ServerLevel level = helper.getLevel();
		Blueprint plain = BlueprintCodec.decode(recorded.text());
		Player player = helper.makeMockPlayer(net.minecraft.world.level.GameType.CREATIVE);
		BlockPos head = TestEngine.HEAD.subtract(TestEngine.PRIMARY);
		for (int k = 1; k < 4; k++) {
			net.minecraft.world.level.block.Rotation rotation = com.createengines.content.blueprint.BlueprintTurner.TURNS.get(k);
			net.minecraft.world.level.block.Mirror mirror = com.createengines.content.blueprint.BlueprintTurner.mirrorFor(plain, rotation);
			BlockPos site = helper.absolutePos(TURN_SITES[k]);
			BlueprintBuilder.build(player, level, site, recorded, rotation);
			int[] at = com.createengines.content.blueprint.BlueprintTurner.place(head.getX(), head.getZ(), rotation, mirror);
			BlockPos turnedHead = site.offset(at[0], head.getY(), at[1]);
			if (!(level.getBlockEntity(turnedHead) instanceof CylinderHeadBlockEntity entity))
				helper.fail(rotation + ": no cylinder head at the turned place " + turnedHead);
			else {
				entity.metering.setValue(3);
				BlueprintApplier.Result result = BlueprintApplier.apply(level, turnedHead, recorded);
				if (result.status() != BlueprintApplier.Status.APPLIED || entity.metering.getValue() != 14)
					helper.fail(rotation + " (" + mirror + "): applying gave " + result + " and the dial is " + entity.metering.getValue());
			}
		}
		helper.succeed();
	}

	@GameTest(template = "empty_large")
	public static void aBlueprintBuildsTheSameEngineInCreativeMode(GameTestHelper helper) throws BlueprintException {
		RecordedBlueprint recorded = recordedTestEngine(helper);
		Player player = helper.makeMockPlayer(net.minecraft.world.level.GameType.CREATIVE);
		BlueprintBuilder.Result result = BlueprintBuilder.build(player, helper.getLevel(), helper.absolutePos(SITE), recorded);
		if (result.status() != BlueprintBuilder.Status.BUILT || result.placed() != recorded.parts())
			helper.fail("building gave " + result + ", expected " + recorded.parts() + " parts");
		BlueprintRecorder.Result built = BlueprintRecorder.record(helper.getLevel(), helper.absolutePos(SITE.offset(TestEngine.HEAD
			.subtract(TestEngine.PRIMARY))), "", "");
		if (!built.ok())
			helper.fail("the new engine could not be recorded: " + built.failure());
		Blueprint wanted = BlueprintCodec.decode(recorded.text());
		var differences = BlueprintDiff.compare(wanted, built.blueprint(), 5);
		if (!differences.isEmpty())
			helper.fail("the new engine is not the same build: " + differences);
		var changes = BlueprintDiff.dialChanges(wanted, built.blueprint());
		if (!changes.isEmpty())
			helper.fail("the new engine's dials differ: " + changes);
		helper.succeed();
	}

	@GameTest(template = "empty_large")
	public static void aSurvivalPlayerNeedsEveryPartAndBuildsNothingWithoutThem(GameTestHelper helper) throws BlueprintException {
		RecordedBlueprint recorded = recordedTestEngine(helper);
		Player player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
		BlueprintBuilder.Result result = BlueprintBuilder.build(player, helper.getLevel(), helper.absolutePos(SITE), recorded);
		if (result.status() != BlueprintBuilder.Status.MISSING || result.lines()
			.isEmpty())
			helper.fail("an empty inventory should be missing parts: " + result);
		if (blocksAt(helper, recorded) != 0)
			helper.fail("a refused build placed blocks");
		// One part short is still refused, and takes nothing
		giveWhatItCosts(player, recorded);
		int carried = carriedItems(player);
		for (int slot = 0; slot < player.getInventory()
			.getContainerSize(); slot++)
			if (!player.getInventory()
				.getItem(slot)
				.isEmpty()) {
				player.getInventory()
					.getItem(slot)
					.shrink(1);
				break;
			}
		BlueprintBuilder.Result short1 = BlueprintBuilder.build(player, helper.getLevel(), helper.absolutePos(SITE), recorded);
		if (short1.status() != BlueprintBuilder.Status.MISSING)
			helper.fail("one part short should still be refused: " + short1);
		if (carriedItems(player) != carried - 1 || blocksAt(helper, recorded) != 0)
			helper.fail("a refused build took items or placed blocks");
		helper.succeed();
	}

	@GameTest(template = "empty_large")
	public static void aSurvivalPlayerWithTheRightPartsBuildsAndSpendsThem(GameTestHelper helper) throws BlueprintException {
		RecordedBlueprint recorded = recordedTestEngine(helper);
		Player player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
		giveWhatItCosts(player, recorded);
		BlueprintBuilder.Result result = BlueprintBuilder.build(player, helper.getLevel(), helper.absolutePos(SITE), recorded);
		if (result.status() != BlueprintBuilder.Status.BUILT)
			helper.fail("building gave " + result);
		if (carriedItems(player) != 0)
			helper.fail("every part should be spent, " + carriedItems(player) + " items are left");
		if (blocksAt(helper, recorded) != recorded.parts())
			helper.fail("not every block was placed");
		helper.succeed();
	}

	@GameTest(template = "empty_large")
	public static void aBuildIsRefusedWhenSomethingIsInTheWay(GameTestHelper helper) throws BlueprintException {
		RecordedBlueprint recorded = recordedTestEngine(helper);
		helper.getLevel()
			.setBlock(helper.absolutePos(SITE.offset(TestEngine.CYLINDER.subtract(TestEngine.PRIMARY))), Blocks.STONE.defaultBlockState(),
				Block.UPDATE_ALL);
		Player player = helper.makeMockPlayer(net.minecraft.world.level.GameType.CREATIVE);
		BlueprintBuilder.Result result = BlueprintBuilder.build(player, helper.getLevel(), helper.absolutePos(SITE), recorded);
		if (result.status() != BlueprintBuilder.Status.BLOCKED || result.lines()
			.size() != 1)
			helper.fail("a stone in the way should block it, naming it: " + result);
		if (blocksAt(helper, recorded) != 1)
			helper.fail("a blocked build still placed blocks (only the stone should be there)");
		helper.succeed();
	}

	@GameTest(template = "empty_large")
	public static void aProtectionModThatCancelsPlacingStopsTheBuild(GameTestHelper helper) throws BlueprintException {
		RecordedBlueprint recorded = recordedTestEngine(helper);
		Player player = helper.makeMockPlayer(net.minecraft.world.level.GameType.CREATIVE);
		java.util.function.Consumer<net.neoforged.neoforge.event.level.BlockEvent.EntityPlaceEvent> veto = event -> event.setCanceled(true);
		net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(veto);
		BlueprintBuilder.Result result;
		try {
			result = BlueprintBuilder.build(player, helper.getLevel(), helper.absolutePos(SITE), recorded);
		} finally {
			net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(veto);
		}
		if (result.status() != BlueprintBuilder.Status.PROTECTED)
			helper.fail("a vetoed build should be protected: " + result);
		if (blocksAt(helper, recorded) != 0)
			helper.fail("a vetoed build still placed blocks");
		helper.succeed();
	}

	@GameTest(template = "empty_large")
	public static void aBuildOutsideTheWorldOrFromADamagedBlueprintIsRefused(GameTestHelper helper) throws BlueprintException {
		RecordedBlueprint recorded = recordedTestEngine(helper);
		Player player = helper.makeMockPlayer(net.minecraft.world.level.GameType.CREATIVE);
		BlueprintBuilder.Result high = BlueprintBuilder.build(player, helper.getLevel(), new BlockPos(0, 100_000, 0), recorded);
		if (high.status() != BlueprintBuilder.Status.UNLOADED)
			helper.fail("a site above the world should be unloaded: " + high);
		BlueprintBuilder.Result far = BlueprintBuilder.build(player, helper.getLevel(), new BlockPos(100_000_000, 80, 0), recorded);
		if (far.status() != BlueprintBuilder.Status.UNLOADED)
			helper.fail("a site beyond the border should be unloaded: " + far);
		BlueprintBuilder.Result damaged = BlueprintBuilder.build(player, helper.getLevel(), helper.absolutePos(SITE),
			new RecordedBlueprint("CEBP2:garbage", "x", 1));
		if (damaged.status() != BlueprintBuilder.Status.REFUSED)
			helper.fail("a damaged blueprint should be refused: " + damaged);
		helper.succeed();
	}

	@GameTest(template = "empty_large")
	public static void theBankBuilderWithABlueprintInTheOtherHandBuildsIt(GameTestHelper helper) throws BlueprintException {
		RecordedBlueprint recorded = recordedTestEngine(helper);
		Player player = helper.makeMockPlayer(net.minecraft.world.level.GameType.CREATIVE);
		// Facing north builds the engine as it was recorded; any other way turns it (see the turned-build tests)
		player.setYRot(180f);
		ItemStack plan = new ItemStack(CEItems.BLUEPRINT.get());
		plan.set(CEComponents.BLUEPRINT.get(), recorded);
		player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, plan);
		ItemStack builder = new ItemStack(CEItems.BANK_BUILDER.get());
		player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, builder);
		// Click the east face of a block just west of the site: the crankshaft goes against the face that was clicked
		BlockPos wall = helper.absolutePos(SITE.west());
		helper.getLevel()
			.setBlock(wall, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
		var hit = new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(wall), Direction.EAST, wall, false);
		builder.useOn(new net.minecraft.world.item.context.UseOnContext(player, net.minecraft.world.InteractionHand.MAIN_HAND, hit));
		if (blocksAt(helper, recorded) != recorded.parts())
			helper.fail("the Bank Builder did not lay out the blueprint: " + blocksAt(helper, recorded) + " of " + recorded.parts());
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void aBlankBlueprintUsedOnAnEngineRecordsItAndUseAgainAppliesIt(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		TestEngine.build(level, helper.absolutePos(BlockPos.ZERO), true);
		((CylinderHeadBlockEntity) helper.getBlockEntity(TestEngine.HEAD)).metering.setValue(7);
		Player player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
		ItemStack stack = new ItemStack(CEItems.BLUEPRINT.get());
		player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, stack);
		BlockPos head = helper.absolutePos(TestEngine.HEAD);
		var hit = new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(head), Direction.UP, head, false);
		net.minecraft.world.InteractionResult recorded = stack.useOn(new net.minecraft.world.item.context.UseOnContext(player,
			net.minecraft.world.InteractionHand.MAIN_HAND, hit));
		if (!recorded.consumesAction() || BlueprintItem.recorded(stack) == null)
			helper.fail("using a blank blueprint on an engine should record it: " + recorded);
		((CylinderHeadBlockEntity) helper.getBlockEntity(TestEngine.HEAD)).metering.setValue(15);
		stack.useOn(new net.minecraft.world.item.context.UseOnContext(player, net.minecraft.world.InteractionHand.MAIN_HAND, hit));
		int metering = ((CylinderHeadBlockEntity) helper.getBlockEntity(TestEngine.HEAD)).metering.getValue();
		if (metering != 7)
			helper.fail("using it again should have set the dial back to 7, it is " + metering);
		helper.succeed();
	}

	@GameTest(template = "empty_large")
	public static void aBlueprintBecomesAStructureFileThatLoadsBack(GameTestHelper helper) throws Exception {
		RecordedBlueprint recorded = recordedTestEngine(helper);
		Blueprint blueprint = BlueprintCodec.decode(recorded.text());
		var tag = BlueprintSchematic.structure(blueprint);
		if (tag == null)
			helper.fail("the engine's blueprint has no structure");
		var template = new net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate();
		template.load(helper.getLevel()
			.holderLookup(net.minecraft.core.registries.Registries.BLOCK), tag);
		var size = template.getSize();
		int minX = 99, maxX = -99, minY = 99, maxY = -99, minZ = 99, maxZ = -99;
		for (Blueprint.Part part : blueprint.parts()) {
			minX = Math.min(minX, part.x());
			maxX = Math.max(maxX, part.x());
			minY = Math.min(minY, part.y());
			maxY = Math.max(maxY, part.y());
			minZ = Math.min(minZ, part.z());
			maxZ = Math.max(maxZ, part.z());
		}
		if (size.getX() != maxX - minX + 1 || size.getY() != maxY - minY + 1 || size.getZ() != maxZ - minZ + 1)
			helper.fail("the schematic is " + size + ", expected " + (maxX - minX + 1) + "x" + (maxY - minY + 1) + "x" + (maxZ - minZ + 1));
		int blocks = template.save(new net.minecraft.nbt.CompoundTag())
			.getList("blocks", 10)
			.size();
		if (blocks != blueprint.parts()
			.size())
			helper.fail("the schematic holds " + blocks + " blocks, the blueprint has " + blueprint.parts()
				.size());
		java.nio.file.Path dir = java.nio.file.Files.createTempDirectory("ce-schematic");
		try {
			String first = BlueprintSchematic.write(blueprint, dir);
			String second = BlueprintSchematic.write(blueprint, dir);
			if (!"engine_site.nbt".equals(first) || !"engine_site_2.nbt".equals(second))
				helper.fail("file names were " + first + " and " + second);
			var read = net.minecraft.nbt.NbtIo.readCompressed(dir.resolve(first), net.minecraft.nbt.NbtAccounter.unlimitedHeap());
			if (!read.equals(tag))
				helper.fail("the file does not hold what was written");
		} finally {
			try (var files = java.nio.file.Files.list(dir)) {
				for (var file : files.toList())
					java.nio.file.Files.deleteIfExists(file);
			}
			java.nio.file.Files.deleteIfExists(dir);
		}
		helper.succeed();
	}

	// Sharing and hanging

	@GameTest(template = "empty")
	public static void anImportedStringIsCheckedAndStoredAsTheServersOwnEncoding(GameTestHelper helper) throws BlueprintException {
		RecordedBlueprint recorded = recordedTestEngineSmall(helper);
		ItemStack blank = new ItemStack(CEItems.BLUEPRINT.get());
		BlueprintImport.Result result = BlueprintImport.importInto(blank, "  " + recorded.text() + "\n");
		if (result.outcome() != BlueprintImport.Outcome.IMPORTED || result.parts() != recorded.parts())
			helper.fail("a good string should import: " + result);
		RecordedBlueprint stored = BlueprintItem.recorded(blank);
		if (stored == null || stored.parts() != recorded.parts())
			helper.fail("the blueprint was not stored: " + stored);
		if (!BlueprintCodec.decode(stored.text())
			.parts()
			.equals(BlueprintCodec.decode(recorded.text())
				.parts()))
			helper.fail("the stored blueprint is not the one that was sent");
		// An already recorded sheet takes a new one in its place
		BlueprintImport.Result again = BlueprintImport.importInto(blank, BlueprintCodec.encode(new Blueprint("Other", "", List.of(
			new Blueprint.Part(0, 0, 0, "create_engines:crankshaft", Map.of("axis", "x"), Map.of())))));
		if (again.outcome() != BlueprintImport.Outcome.IMPORTED || !"Other".equals(BlueprintItem.recorded(blank)
			.name()))
			helper.fail("a second import should replace the first: " + again);
		helper.succeed();
	}

	private static RecordedBlueprint recordedTestEngineSmall(GameTestHelper helper) throws BlueprintException {
		TestEngine.build(helper.getLevel(), helper.absolutePos(BlockPos.ZERO), true);
		BlueprintRecorder.Result result = BlueprintRecorder.record(helper.getLevel(), helper.absolutePos(TestEngine.HEAD), "Shared", "t");
		return new RecordedBlueprint(BlueprintCodec.encode(result.blueprint()), "Shared", result.blueprint()
			.parts()
			.size());
	}

	@GameTest(template = "empty")
	public static void aHostileOrBrokenImportIsRefusedAndTheSheetIsUntouched(GameTestHelper helper) throws BlueprintException {
		ItemStack blank = new ItemStack(CEItems.BLUEPRINT.get());
		RecordedBlueprint kept = recordedTestEngineSmall(helper);
		blank.set(CEComponents.BLUEPRINT.get(), kept);
		String[] texts = {
			"", "hello", "CEBP2:!!!", "CEBP9:AAAA", "CEBP2:" + "A".repeat(2000),
			BlueprintCodec.encode(new Blueprint("evil", "", List.of(new Blueprint.Part(0, 0, 0, "minecraft:tnt")))),
			BlueprintCodec.encode(new Blueprint("evil", "", List.of(
				new Blueprint.Part(0, 0, 0, "create_engines:cylinder_head", Map.of(), Map.of("fuel_metering", 500))))),
			BlueprintCodec.encode(new Blueprint("evil", "", List.of(
				new Blueprint.Part(0, 0, 0, "create_engines:cylinder_head", Map.of("facing", "diagonal"), Map.of())))),
			BlueprintCodec.encode(new Blueprint("evil", "", List.of(new Blueprint.Part(0, 0, 0, "createaddition:rod")))),
			BlueprintCodec.encode(new Blueprint("evil", "", List.of(
				new Blueprint.Part(0, 0, 0, "create:shaft", Map.of("axis", "w"), Map.of()))))
		};
		for (String text : texts) {
			BlueprintImport.Result result = BlueprintImport.importInto(blank, text);
			if (result.outcome() != BlueprintImport.Outcome.REFUSED)
				helper.fail("text " + text.substring(0, Math.min(40, text.length())) + " should be refused, got " + result);
			if (!kept.equals(BlueprintItem.recorded(blank)))
				helper.fail("a refused import changed the sheet");
		}
		if (BlueprintImport.importInto(blank, "A".repeat(BlueprintImport.MAX_TEXT + 1))
			.outcome() != BlueprintImport.Outcome.TOO_LONG)
			helper.fail("an over-long string should be refused for its length");
		if (BlueprintImport.importInto(new ItemStack(net.minecraft.world.item.Items.PAPER), kept.text())
			.outcome() != BlueprintImport.Outcome.NOT_HOLDING_ONE)
			helper.fail("only a blueprint takes an import");
		if (BlueprintImport.importInto(blank, null)
			.outcome() != BlueprintImport.Outcome.TOO_LONG)
			helper.fail("no text should not crash");
		helper.succeed();
	}

	private static BlockPos wallFrame(GameTestHelper helper) {
		BlockPos wall = new BlockPos(8, 2, 4);
		helper.setBlock(wall, Blocks.STONE);
		BlockPos at = wall.north();
		helper.setBlock(at, CEBlocks.FRAMED_BLUEPRINT.getDefaultState()
			.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING, Direction.NORTH));
		return at;
	}

	private static net.minecraft.world.phys.BlockHitResult hitOf(GameTestHelper helper, BlockPos at) {
		return new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(helper.absolutePos(at)), Direction.NORTH,
			helper.absolutePos(at), false);
	}

	@GameTest(template = "empty")
	public static void aFrameTakesARecordedBlueprintAndGivesItBack(GameTestHelper helper) throws BlueprintException {
		BlockPos at = wallFrame(helper);
		FramedBlueprintBlockEntity frame = helper.getBlockEntity(at);
		RecordedBlueprint recorded = recordedTestEngineSmall(helper);
		Player player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
		ItemStack blank = new ItemStack(CEItems.BLUEPRINT.get());
		player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, blank);
		var blankResult = helper.getLevel()
			.getBlockState(helper.absolutePos(at))
			.useItemOn(blank, helper.getLevel(), player, net.minecraft.world.InteractionHand.MAIN_HAND, hitOf(helper, at));
		if (frame.plan() != null || blankResult.consumesAction())
			helper.fail("a blank blueprint should not be hung: " + blankResult);

		ItemStack stack = new ItemStack(CEItems.BLUEPRINT.get());
		stack.set(CEComponents.BLUEPRINT.get(), recorded);
		player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, stack);
		helper.getLevel()
			.getBlockState(helper.absolutePos(at))
			.useItemOn(stack, helper.getLevel(), player, net.minecraft.world.InteractionHand.MAIN_HAND, hitOf(helper, at));
		if (!recorded.equals(frame.plan()) || frame.drawing() == null)
			helper.fail("the blueprint was not hung and decoded: " + frame.plan());
		var sorted = frame.partsByHeight();
		if (sorted.size() != frame.drawing().parts().size())
			helper.fail("the frame should keep every part for drawing: " + sorted.size() + " of " + frame.drawing().parts().size());
		for (int i = 1; i < sorted.size(); i++)
			if (sorted.get(i - 1).y() > sorted.get(i).y())
				helper.fail("the parts should be kept lowest first so higher blocks draw over lower");
		if (!stack.isEmpty())
			helper.fail("a survival player's blueprint should be used up when hung");

		// A frame that is full does not take another
		ItemStack second = new ItemStack(CEItems.BLUEPRINT.get());
		second.set(CEComponents.BLUEPRINT.get(), new RecordedBlueprint("CEBP2:x", "Second", 1));
		player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, second);
		helper.getLevel()
			.getBlockState(helper.absolutePos(at))
			.useItemOn(second, helper.getLevel(), player, net.minecraft.world.InteractionHand.MAIN_HAND, hitOf(helper, at));
		if (!recorded.equals(frame.plan()) || second.isEmpty())
			helper.fail("a full frame should keep what it has and keep the offered sheet");

		// Sneak with an empty hand takes it back
		player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		player.setShiftKeyDown(true);
		helper.getLevel()
			.getBlockState(helper.absolutePos(at))
			.useWithoutItem(helper.getLevel(), player, hitOf(helper, at));
		if (frame.plan() != null)
			helper.fail("the frame should be empty after the blueprint was taken");
		if (!frame.partsByHeight().isEmpty())
			helper.fail("an empty frame should have nothing to draw");
		boolean found = false;
		for (int slot = 0; slot < player.getInventory()
			.getContainerSize(); slot++)
			if (recorded.equals(BlueprintItem.recorded(player.getInventory()
				.getItem(slot))))
				found = true;
		if (!found)
			helper.fail("the blueprint did not come back to the player intact");
		helper.succeed();
	}

	private static BlockState frameFacing(Direction facing) {
		return CEBlocks.FRAMED_BLUEPRINT.getDefaultState()
			.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING, facing);
	}

	/** Frames at (u, v) of a wall facing {@code facing}, u along the paper's reading direction and v down it. */
	private static BlockPos frameCell(BlockPos origin, Direction facing, int u, int v) {
		return origin.relative(FramedBlueprintBlock.paperRight(facing), u)
			.relative(FramedBlueprintBlock.paperDown(facing), v);
	}

	private static void hangFrame(GameTestHelper helper, BlockPos cell, Direction facing) {
		helper.setBlock(cell.relative(facing.getOpposite()), Blocks.STONE);
		helper.setBlock(cell, frameFacing(facing));
	}

	@GameTest(template = "empty_large")
	public static void framesSideBySideOnAnyFaceJoinIntoOneSheetShowingTheFirstBlueprint(GameTestHelper helper) throws BlueprintException {
		RecordedBlueprint recorded = recordedTestEngineSmall(helper);
		for (Direction facing : Direction.values()) {
			BlockPos origin = new BlockPos(8, 6, 8);
			for (int x = 4; x <= 12; x++)
				for (int y = 2; y <= 10; y++)
					for (int z = 4; z <= 12; z++)
						helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
			for (int u = 0; u < 2; u++)
				for (int v = 0; v < 2; v++)
					hangFrame(helper, frameCell(origin, facing, u, v), facing);
			((FramedBlueprintBlockEntity) helper.getBlockEntity(frameCell(origin, facing, 1, 1))).hang(recorded);
			for (int u = 0; u < 2; u++)
				for (int v = 0; v < 2; v++) {
					FramedBlueprintBlockEntity be = helper.getBlockEntity(frameCell(origin, facing, u, v));
					FrameSheet.Sheet sheet = be.refreshSheet();
					FrameSheet.Sheet want = new FrameSheet.Sheet(-u, -v, 2, 2, new FrameSheet.Cell(1 - u, 1 - v));
					if (!want.equals(sheet))
						helper.fail(facing + ": frame (" + u + "," + v + ") sees " + sheet + " but should see " + want);
				}
			// a frame two cells along does not touch, so it stays alone
			BlockPos apart = frameCell(origin, facing, 3, 0);
			hangFrame(helper, apart, facing);
			FramedBlueprintBlockEntity lone = helper.getBlockEntity(apart);
			if (lone.refreshSheet()
				.joined())
				helper.fail(facing + ": a frame with a gap beside it should not join");
			// the group is now a ragged six, not a rectangle, so every frame stands alone again
			BlockPos extra = frameCell(origin, facing, 2, 0);
			hangFrame(helper, extra, facing);
			FramedBlueprintBlockEntity oneOfTheFour = helper.getBlockEntity(frameCell(origin, facing, 0, 0));
			if (oneOfTheFour.refreshSheet()
				.joined())
				helper.fail(facing + ": a ragged group should not join");
		}
		helper.succeed();
	}

	@GameTest(template = "empty_large")
	public static void framesOnDifferentFacesOrBrokenOutOfARectangleDoNotJoin(GameTestHelper helper) throws BlueprintException {
		Direction facing = Direction.NORTH;
		BlockPos origin = new BlockPos(8, 6, 8);
		hangFrame(helper, frameCell(origin, facing, 0, 0), facing);
		hangFrame(helper, frameCell(origin, facing, 1, 0), facing);
		FramedBlueprintBlockEntity left = helper.getBlockEntity(frameCell(origin, facing, 0, 0));
		if (!left.refreshSheet()
			.joined())
			helper.fail("two frames side by side should join");
		// a frame facing another way in the next cell is a different wall
		helper.setBlock(frameCell(origin, facing, 1, 0), Blocks.AIR);
		helper.setBlock(frameCell(origin, facing, 1, 0).relative(Direction.UP), Blocks.STONE);
		helper.setBlock(frameCell(origin, facing, 1, 0), frameFacing(Direction.DOWN));
		if (left.refreshSheet()
			.joined())
			helper.fail("a frame facing another way should not join");
		// taking a frame out of the middle of three leaves two alone
		hangFrame(helper, frameCell(origin, facing, 1, 0), facing);
		hangFrame(helper, frameCell(origin, facing, 2, 0), facing);
		if (left.refreshSheet()
			.width() != 3)
			helper.fail("three in a row should be one sheet");
		helper.setBlock(frameCell(origin, facing, 1, 0), Blocks.AIR);
		if (left.refreshSheet()
			.joined())
			helper.fail("a broken-up row should fall apart into separate frames");
		helper.succeed();
	}

	@GameTest(template = "empty_large")
	public static void aSheetWithNothingHungDrawsNothingAndTheBlueprintsInAJoinedSheetComeBackOut(GameTestHelper helper)
		throws BlueprintException {
		Direction facing = Direction.NORTH;
		BlockPos origin = new BlockPos(8, 6, 8);
		hangFrame(helper, frameCell(origin, facing, 0, 0), facing);
		hangFrame(helper, frameCell(origin, facing, 1, 0), facing);
		FramedBlueprintBlockEntity left = helper.getBlockEntity(frameCell(origin, facing, 0, 0));
		FramedBlueprintBlockEntity right = helper.getBlockEntity(frameCell(origin, facing, 1, 0));
		if (left.refreshSheet()
			.source() != null)
			helper.fail("a sheet with nothing hung has nothing to draw");
		RecordedBlueprint recorded = recordedTestEngineSmall(helper);
		right.hang(recorded);
		right.refreshSheet();
		left.hang(recorded);
		// Both hold one: the left (first) frame is the source, and the right one still keeps its own blueprint
		if (!new FrameSheet.Cell(0, 0).equals(left.refreshSheet()
			.source()))
			helper.fail("the first frame's blueprint should be the one shown");
		if (!recorded.equals(right.plan()) || !recorded.equals(right.take()
			.get(CEComponents.BLUEPRINT.get())))
			helper.fail("a frame that is part of a sheet should still give its blueprint back");
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void aHungBlueprintSurvivesSavingAndBadSavedDataIsAnEmptyFrame(GameTestHelper helper) throws BlueprintException {
		BlockPos at = wallFrame(helper);
		FramedBlueprintBlockEntity frame = helper.getBlockEntity(at);
		RecordedBlueprint recorded = recordedTestEngineSmall(helper);
		frame.hang(recorded);
		var access = helper.getLevel()
			.registryAccess();
		net.minecraft.nbt.CompoundTag saved = frame.saveWithFullMetadata(access);
		frame.take();
		frame.loadWithComponents(saved.copy(), access);
		if (!recorded.equals(frame.plan()) || frame.drawing() == null)
			helper.fail("the blueprint did not survive a save and load");

		net.minecraft.nbt.CompoundTag bad = saved.copy();
		bad.putString("Plan", "not a compound");
		frame.loadWithComponents(bad, access);
		if (frame.plan() != null)
			helper.fail("unreadable saved data should be an empty frame");
		net.minecraft.nbt.CompoundTag broken = saved.copy();
		net.minecraft.nbt.CompoundTag plan = new net.minecraft.nbt.CompoundTag();
		plan.putString("text", "CEBP2:garbage");
		plan.putString("name", "x");
		plan.putInt("parts", 1);
		broken.put("Plan", plan);
		frame.loadWithComponents(broken, access);
		if (frame.drawing() != null)
			helper.fail("a damaged blueprint should have no drawing");
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void aBrokenFrameDropsItselfAndTheBlueprintInIt(GameTestHelper helper) throws BlueprintException {
		BlockPos at = wallFrame(helper);
		FramedBlueprintBlockEntity frame = helper.getBlockEntity(at);
		RecordedBlueprint recorded = recordedTestEngineSmall(helper);
		frame.hang(recorded);
		helper.getLevel()
			.destroyBlock(helper.absolutePos(at), true);
		boolean blueprint = false;
		boolean frameItem = false;
		for (var item : helper.getLevel()
			.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(helper.absolutePos(at))
				.inflate(3))) {
			if (recorded.equals(BlueprintItem.recorded(item.getItem())))
				blueprint = true;
			if (item.getItem()
				.is(CEBlocks.FRAMED_BLUEPRINT.asItem()))
				frameItem = true;
		}
		if (!blueprint || !frameItem)
			helper.fail("a broken frame should drop the frame (" + frameItem + ") and the blueprint (" + blueprint + ")");
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void aFrameOnABlockThatIsBrokenFallsOff(GameTestHelper helper) {
		BlockPos at = wallFrame(helper);
		// The neighbour update runs as the block goes, so the frame is gone by the time setBlock returns
		helper.setBlock(at.south(), Blocks.AIR);
		if (!helper.getLevel()
			.getBlockState(helper.absolutePos(at))
			.isAir())
			helper.fail("a frame with nothing behind it should drop");
		helper.succeed();
	}
}
