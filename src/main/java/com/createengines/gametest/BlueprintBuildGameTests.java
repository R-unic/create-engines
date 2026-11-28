package com.createengines.gametest;

import static com.createengines.gametest.BlueprintRigs.*;

import java.util.List;
import java.util.Map;

import com.createengines.CreateEngines;
import com.createengines.content.blueprint.BlueprintBuilder;
import com.createengines.content.blueprint.BlueprintImport;
import com.createengines.content.blueprint.BlueprintItem;
import com.createengines.content.blueprint.BlueprintRecorder;
import com.createengines.content.blueprint.RecordedBlueprint;
import com.createengines.content.head.CylinderHeadBlockEntity;
import com.createengines.engine.blueprint.Blueprint;
import com.createengines.engine.blueprint.BlueprintCodec;
import com.createengines.engine.blueprint.BlueprintDiff;
import com.createengines.engine.blueprint.BlueprintException;
import com.createengines.registry.CEComponents;
import com.createengines.registry.CEItems;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Building from a blueprint, structure files and importing strings. */
@GameTestHolder(CreateEngines.MOD_ID)
@PrefixGameTestTemplate(false)
public class BlueprintBuildGameTests {

	// Building

	/** Where the third engine's crankshaft goes: clear space beside the two the pair tests use. */
	private static final BlockPos SITE = new BlockPos(20, 2, 2);

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
}
