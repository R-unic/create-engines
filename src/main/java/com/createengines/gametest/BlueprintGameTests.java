package com.createengines.gametest;

import static com.createengines.gametest.BlueprintRigs.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.createengines.CreateEngines;
import com.createengines.content.blueprint.BlueprintApplier;
import com.createengines.content.blueprint.BlueprintBuilder;
import com.createengines.content.blueprint.BlueprintSchematic;
import com.createengines.content.blueprint.BlueprintItem;
import com.createengines.content.blueprint.FramedBlueprintBlockEntity;
import com.createengines.content.blueprint.BlueprintRecorder;
import com.createengines.content.blueprint.EngineBlueprintPolicy;
import com.createengines.content.blueprint.EngineDials;
import com.createengines.content.blueprint.RecordedBlueprint;
import com.createengines.content.camshaft.CamshaftBlockEntity;
import com.createengines.content.crankshaft.CrankshaftBlockEntity;
import com.createengines.content.head.CylinderHeadBlockEntity;
import com.createengines.engine.blueprint.Blueprint;
import com.createengines.engine.blueprint.BlueprintCodec;
import com.createengines.engine.blueprint.BlueprintDiff;
import com.createengines.engine.blueprint.BlueprintException;
import com.createengines.engine.blueprint.BlueprintException.Refusal;
import com.createengines.engine.blueprint.BlueprintRules;
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

	/** Building against the top of a block lifts an engine that hangs below its crank so its lowest part rests on the block. */
	@GameTest(template = "empty")
	public static void anEngineHangingBelowItsCrankRestsOnTheGroundClicked(GameTestHelper helper) {
		List<Blueprint.Part> parts = List.of(part(0, 0, 0), part(0, -1, 0), part(0, -2, 0));
		Blueprint hanging = new Blueprint("t", "t", parts);
		BlockPos ground = new BlockPos(4, 1, 4);
		if (!BlueprintBuilder.originOn(hanging, ground, Direction.UP)
			.equals(ground.above(3)))
			helper.fail("the lowest part (2 below the crank) should rest on the ground: "
				+ BlueprintBuilder.originOn(hanging, ground, Direction.UP));
		Blueprint above = new Blueprint("t", "t", List.of(part(0, 0, 0), part(0, 1, 0)));
		if (!BlueprintBuilder.originOn(above, ground, Direction.UP)
			.equals(ground.above()))
			helper.fail("an engine above its crank should not be lifted");
		if (!BlueprintBuilder.originOn(hanging, ground, Direction.EAST)
			.equals(ground.east()))
			helper.fail("a side face should not lift the engine");
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

	@GameTest(template = "empty")
	public static void theTurnOfADrawingIsSavedAndAFrameSavedBeforeItIsUpright(GameTestHelper helper) throws BlueprintException {
		BlockPos at = wallFrame(helper);
		FramedBlueprintBlockEntity frame = helper.getBlockEntity(at);
		frame.hang(recordedTestEngineSmall(helper));
		wrench(helper, at);
		wrench(helper, at);
		wrench(helper, at);
		var registries = helper.getLevel()
			.registryAccess();
		net.minecraft.nbt.CompoundTag saved = frame.saveWithoutMetadata(registries);
		FramedBlueprintBlockEntity copy = new FramedBlueprintBlockEntity(frame.getType(), frame.getBlockPos(), frame.getBlockState());
		copy.loadWithComponents(saved, registries);
		if (copy.turn() != 3)
			helper.fail("the turn should survive a save and a load, but is " + copy.turn());
		// A frame saved by a version that could not turn its drawing has no Turn key
		saved.remove("Turn");
		copy.loadWithComponents(saved, registries);
		if (copy.turn() != 0)
			helper.fail("a frame saved without a turn should be upright, but is " + copy.turn());
		// Saved data may hold anything
		saved.putByte("Turn", (byte) 77);
		copy.loadWithComponents(saved, registries);
		if (copy.turn() != 1)
			helper.fail("a turn out of range should be read as the same turn in range, but is " + copy.turn());
		saved.putByte("Turn", (byte) -3);
		copy.loadWithComponents(saved, registries);
		if (copy.turn() != 1)
			helper.fail("a negative turn should be read in range, but is " + copy.turn());
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
}
