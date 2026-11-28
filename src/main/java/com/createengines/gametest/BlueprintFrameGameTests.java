package com.createengines.gametest;

import static com.createengines.gametest.BlueprintRigs.*;

import com.createengines.CreateEngines;
import com.createengines.content.blueprint.BlueprintItem;
import com.createengines.content.blueprint.FramedBlueprintBlock;
import com.createengines.content.blueprint.FramedBlueprintBlockEntity;
import com.createengines.content.blueprint.RecordedBlueprint;
import com.createengines.engine.blueprint.BlueprintException;
import com.createengines.engine.blueprint.FrameSheet;
import com.createengines.registry.CEBlocks;
import com.createengines.registry.CEComponents;
import com.createengines.registry.CEItems;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Framed blueprints: hanging, turning, joining and saving. */
@GameTestHolder(CreateEngines.MOD_ID)
@PrefixGameTestTemplate(false)
public class BlueprintFrameGameTests {

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

	@GameTest(template = "empty")
	public static void aWrenchTurnsTheDrawingOnAFrameAQuarterAtATimeAndAnEmptyOneIsLeftAlone(GameTestHelper helper) throws BlueprintException {
		BlockPos at = wallFrame(helper);
		FramedBlueprintBlockEntity frame = helper.getBlockEntity(at);
		Direction facing = helper.getBlockState(at)
			.getValue(FramedBlueprintBlock.FACING);
		wrench(helper, at);
		if (frame.turn() != 0)
			helper.fail("an empty frame has no drawing to turn");
		frame.hang(recordedTestEngineSmall(helper));
		for (int expected : new int[] {1, 2, 3, 0, 1}) {
			var result = wrench(helper, at);
			if (!result.consumesAction())
				helper.fail("the wrench should be used on a frame that holds a drawing: " + result);
			if (frame.turn() != expected)
				helper.fail("the drawing should be turned " + expected + " quarters, but is " + frame.turn());
		}
		if (helper.getBlockState(at)
			.getValue(FramedBlueprintBlock.FACING) != facing)
			helper.fail("turning the drawing must not turn the frame itself");
		// Taking the blueprint out sends the turn with it: the next one hung is upright
		frame.take();
		if (frame.turn() != 0)
			helper.fail("an emptied frame should be upright again");
		helper.succeed();
	}

	@GameTest(template = "empty_large")
	public static void aWrenchOnAnyFrameOfASheetTurnsTheDrawingOfTheFrameThatShowsIt(GameTestHelper helper) throws BlueprintException {
		BlockPos origin = new BlockPos(8, 6, 8);
		Direction facing = Direction.NORTH;
		for (int u = 0; u < 2; u++)
			hangFrame(helper, frameCell(origin, facing, u, 0), facing);
		BlockPos left = frameCell(origin, facing, 0, 0);
		BlockPos right = frameCell(origin, facing, 1, 0);
		// The blueprint is in the right-hand frame, which is the source of the sheet: the other draws nothing
		((FramedBlueprintBlockEntity) helper.getBlockEntity(right)).hang(recordedTestEngineSmall(helper));
		wrench(helper, left);
		if (((FramedBlueprintBlockEntity) helper.getBlockEntity(right)).turn() != 1)
			helper.fail("a wrench on the empty half of a sheet should turn the drawing of the half that holds it");
		if (((FramedBlueprintBlockEntity) helper.getBlockEntity(left)).turn() != 0)
			helper.fail("the frame that holds nothing has no turn of its own");
		wrench(helper, right);
		if (((FramedBlueprintBlockEntity) helper.getBlockEntity(right)).turn() != 2)
			helper.fail("a wrench on the frame that holds the drawing turns it too");
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

	private static int joins(GameTestHelper helper, BlockPos at) {
		BlockState s = helper.getBlockState(at);
		return (s.getValue(FramedBlueprintBlock.JOIN_DOWN) ? 1 : 0) | (s.getValue(FramedBlueprintBlock.JOIN_LEFT) ? 2 : 0)
			| (s.getValue(FramedBlueprintBlock.JOIN_UP) ? 4 : 0) | (s.getValue(FramedBlueprintBlock.JOIN_RIGHT) ? 8 : 0);
	}

	@GameTest(template = "empty_large", timeoutTicks = 400)
	public static void joinedFramesRunTheirBlocksTogetherOnExactlyTheJoinedSides(GameTestHelper helper) {
		BlockPos origin = new BlockPos(8, 6, 8);
		var run = helper.startSequence();
		for (Direction facing : Direction.values()) {
			run.thenExecute(() -> {
				for (int x = 4; x <= 12; x++)
					for (int y = 2; y <= 10; y++)
						for (int z = 4; z <= 12; z++)
							helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
				for (int u = 0; u < 2; u++)
					for (int v = 0; v < 2; v++)
						hangFrame(helper, frameCell(origin, facing, u, v), facing);
			})
				.thenExecuteAfter(3, () -> {
					// bit 1 is the side below on the paper, 2 the left, 4 above, 8 the right
					for (int u = 0; u < 2; u++)
						for (int v = 0; v < 2; v++) {
							int mask = joins(helper, frameCell(origin, facing, u, v));
							int expected = (u == 0 ? 8 : 2) | (v == 0 ? 1 : 4);
							if (mask != expected)
								helper.fail(facing + ": frame (" + u + "," + v + ") joins " + mask + " but should join " + expected);
						}
					// a third frame in the row makes a ragged group: nothing is joined any more
					hangFrame(helper, frameCell(origin, facing, 2, 0), facing);
				})
				.thenExecuteAfter(3, () -> {
					for (int u = 0; u < 3; u++)
						if (joins(helper, frameCell(origin, facing, u, 0)) != 0)
							helper.fail(facing + ": a ragged group should be drawn as separate frames");
					// taking it away joins the four again
					helper.setBlock(frameCell(origin, facing, 2, 0), Blocks.AIR);
				})
				.thenExecuteAfter(3, () -> {
					if (joins(helper, frameCell(origin, facing, 0, 0)) == 0)
						helper.fail(facing + ": removing the odd frame should join the square again");
					// and taking one of the four leaves three in an L, which stand alone
					helper.setBlock(frameCell(origin, facing, 1, 1), Blocks.AIR);
				})
				.thenExecuteAfter(3, () -> {
					for (int[] cell : new int[][] { { 0, 0 }, { 1, 0 }, { 0, 1 } })
						if (joins(helper, frameCell(origin, facing, cell[0], cell[1])) != 0)
							helper.fail(facing + ": three frames in an L should stand alone");
				});
		}
		run.thenSucceed();
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
