package com.createengines.gametest;

import java.util.ArrayList;
import java.util.List;

import com.createengines.CreateEngines;
import com.createengines.content.airflow.PortKind;
import com.createengines.content.airflow.PortOpeningBlock;
import com.createengines.content.muffler.ExhaustCanisterBlock;
import com.createengines.content.muffler.SootTrapBlockEntity;
import com.createengines.content.plate.MakerPlateBlock;
import com.createengines.content.plate.MakerPlateBlockEntity;
import com.createengines.content.raincap.RainCapBlock;
import com.createengines.content.refinery.FlareStackBlock;
import com.createengines.engine.sim.PlateFigures;
import com.createengines.engine.sim.SootCollector;
import com.createengines.registry.CEBlocks;
import com.createengines.registry.CEItems;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The small blocks that hang off an engine (rain cap, maker's plate, soot trap, muffler, flare stack) in every direction they can be
 * put, losing what holds them up, and being asked odd things.
 */
@GameTestHolder(CreateEngines.MOD_ID)
@PrefixGameTestTemplate(false)
public class SmallBlockEdgeGameTests {

	private static final BlockPos MIDDLE = new BlockPos(4, 2, 3);

	private static int droppedAround(GameTestHelper helper, BlockPos pos, net.minecraft.world.item.Item item) {
		int count = 0;
		for (ItemEntity entity : helper.getLevel()
			.getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(pos)).inflate(1.5)))
			if (entity.getItem()
				.is(item)) {
				count += entity.getItem()
					.getCount();
				entity.discard();
			}
		return count;
	}

	// --- Rain cap ---

	@GameTest(template = "empty")
	public static void aRainCapFitsOnlyTheMatchingExhaustMouthInAllSixDirections(GameTestHelper helper) {
		BlockPos at = helper.absolutePos(MIDDLE);
		for (Direction outletFaces : Direction.values()) {
			BlockPos mouth = MIDDLE.relative(outletFaces);
			helper.setBlock(MIDDLE, CEBlocks.EXHAUST_OUTLET.getDefaultState()
				.setValue(PortOpeningBlock.FACING, outletFaces));
			for (Direction cap : Direction.values())
				helper.assertTrue(RainCapBlock.sitsOnOutlet(helper.getLevel(), helper.absolutePos(mouth), cap) == (cap == outletFaces),
					"outlet " + outletFaces + ", cap " + cap);
			// A cap one block round the side of the outlet is on no mouth
			for (Direction side : Direction.values())
				if (side != outletFaces)
					helper.assertTrue(!RainCapBlock.sitsOnOutlet(helper.getLevel(), at.relative(side), outletFaces),
						"outlet " + outletFaces + ", cap beside it " + side);
			helper.setBlock(MIDDLE, Blocks.AIR);
		}
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void turningTheOutletAwayDropsItsRainCapOnce(GameTestHelper helper) {
		BlockPos cap = MIDDLE.above();
		helper.setBlock(MIDDLE, CEBlocks.EXHAUST_OUTLET.getDefaultState()
			.setValue(PortOpeningBlock.FACING, Direction.UP));
		helper.setBlock(cap, CEBlocks.RAIN_CAP.getDefaultState()
			.setValue(RainCapBlock.FACING, Direction.UP));
		helper.setBlock(MIDDLE, CEBlocks.EXHAUST_OUTLET.getDefaultState()
			.setValue(PortOpeningBlock.FACING, Direction.EAST));
		helper.runAfterDelay(3, () -> {
			helper.assertTrue(helper.getBlockState(cap)
				.isAir(), "the cap has nothing to sit on");
			int dropped = droppedAround(helper, cap, CEBlocks.RAIN_CAP.asItem());
			helper.assertTrue(dropped == 1, "one cap dropped, not " + dropped);
			helper.succeed();
		});
	}

	// --- Maker's plate ---

	@GameTest(template = "empty")
	public static void aPlateScrewedToAnySideGoesWhenItsSupportBecomesWater(GameTestHelper helper) {
		for (Direction facing : Direction.values()) {
			BlockPos plate = MIDDLE.relative(facing);
			helper.setBlock(MIDDLE, Blocks.STONE);
			BlockState state = CEBlocks.MAKER_PLATE.getDefaultState()
				.setValue(MakerPlateBlock.FACING, facing);
			helper.setBlock(plate, state);
			helper.assertTrue(state.canSurvive(helper.getLevel(), helper.absolutePos(plate)), "holds facing " + facing);
			for (BlockState nothing : new BlockState[] {Blocks.AIR.defaultBlockState(), Blocks.WATER.defaultBlockState(),
				Blocks.SHORT_GRASS.defaultBlockState()}) {
				helper.getLevel()
					.setBlock(helper.absolutePos(MIDDLE), nothing, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
				helper.assertTrue(!state.canSurvive(helper.getLevel(), helper.absolutePos(plate)), nothing + " holds a plate " + facing);
			}
			helper.getLevel()
				.setBlock(helper.absolutePos(plate), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
			helper.getLevel()
				.setBlock(helper.absolutePos(MIDDLE), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
		}
		helper.succeed();
	}

	private static MakerPlateBlockEntity plate(GameTestHelper helper) {
		helper.setBlock(MIDDLE.south(), Blocks.STONE);
		helper.setBlock(MIDDLE, CEBlocks.MAKER_PLATE.getDefaultState()
			.setValue(MakerPlateBlock.FACING, Direction.NORTH));
		return helper.getBlockEntity(MIDDLE);
	}

	@GameTest(template = "empty")
	public static void aPlateCutsALongNameShortAndNamesNobodyForAMachine(GameTestHelper helper) {
		MakerPlateBlockEntity plate = plate(helper);
		ItemStack named = CEBlocks.MAKER_PLATE.asStack();
		named.set(DataComponents.CUSTOM_NAME, Component.literal("  The   Longest Engine Name Anyone Ever Typed  "));
		CEBlocks.MAKER_PLATE.get()
			.setPlacedBy(helper.getLevel(), helper.absolutePos(MIDDLE), helper.getBlockState(MIDDLE), null, named);
		helper.assertTrue(plate.name()
			.length() <= MakerPlateBlockEntity.NAME_LIMIT && plate.name()
			.startsWith("The Longest"), "'" + plate.name() + "'");
		helper.assertTrue(plate.builder()
			.isEmpty(), "no builder without a placer");
		CEBlocks.MAKER_PLATE.get()
			.setPlacedBy(helper.getLevel(), helper.absolutePos(MIDDLE), helper.getBlockState(MIDDLE), null, CEBlocks.MAKER_PLATE.asStack());
		helper.assertTrue(plate.name()
			.isEmpty(), "an unrenamed plate has no name, not the item's");
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void aDamagedPlateSaveReadsAsBlankOrClean(GameTestHelper helper) {
		MakerPlateBlockEntity plate = plate(helper);
		var access = helper.getLevel()
			.registryAccess();
		int[][] figures = {{}, {1}, {1, 2, 3}, {1, 2, 3, 4, 5}, {Integer.MIN_VALUE, -1, -1, -1},
			{Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE}};
		for (int[] packed : figures) {
			CompoundTag tag = new CompoundTag();
			tag.putString("Name", "x\u0000\n".repeat(5000));
			tag.putString("Builder", "y".repeat(30000));
			tag.putIntArray("Figures", packed);
			plate.loadWithComponents(tag, access);
			helper.assertTrue(plate.name()
				.length() <= MakerPlateBlockEntity.NAME_LIMIT && plate.builder()
				.length() <= MakerPlateBlockEntity.NAME_LIMIT, "names cut");
			helper.assertTrue(plate.figures()
				.peakPower() >= 0, "figures sane");
		}
		CompoundTag wrongTypes = new CompoundTag();
		wrongTypes.putInt("Name", 4);
		wrongTypes.putString("Figures", "lots");
		plate.loadWithComponents(wrongTypes, access);
		helper.assertTrue(plate.name()
			.isEmpty() && plate.figures()
			.equals(PlateFigures.NONE), "wrong types read as blank");
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void thePlatesShortNumbersAreRightEitherSideOfEachBreak(GameTestHelper helper) {
		String[][] expected = {{"0", "0"}, {"999", "999"}, {"1000", "1.00k"}, {"1250", "1.25k"}, {"9990", "9.99k"},
			{"10000", "10.0k"}, {"123456", "123.5k"}};
		for (String[] pair : expected) {
			String got = MakerPlateBlockEntity.compact(Float.parseFloat(pair[0]));
			helper.assertTrue(got.equals(pair[1]), pair[0] + " shown as " + got);
		}
		helper.succeed();
	}

	// --- Soot trap ---

	@GameTest(template = "empty")
	public static void aSootTrapsItemSlotRefusesEveryItemAndEveryOtherSlot(GameTestHelper helper) {
		helper.setBlock(MIDDLE, CEBlocks.SOOT_TRAP.getDefaultState());
		SootTrapBlockEntity trap = helper.getBlockEntity(MIDDLE);
		var items = helper.getLevel()
			.getCapability(Capabilities.ItemHandler.BLOCK, helper.absolutePos(MIDDLE), null);
		helper.assertTrue(items.getStackInSlot(0)
			.isEmpty() && items.extractItem(0, 64, false)
			.isEmpty(), "an empty trap gives nothing");
		trap.catchSoot(SootCollector.MB_PER_SOOT * 7);
		ItemStack soot = CEItems.SOOT.asStack(3);
		helper.assertTrue(!items.isItemValid(0, soot) && items.insertItem(0, soot, false)
			.getCount() == 3, "not even its own soot goes back");
		for (int slot : new int[] {-1, 1, 64})
			helper.assertTrue(items.extractItem(slot, 5, false)
				.isEmpty(), "slot " + slot);
		helper.assertTrue(items.extractItem(0, 0, false)
			.isEmpty() && items.extractItem(0, Integer.MIN_VALUE, false)
			.isEmpty() && trap.stored() == 7, "asking for none takes none");
		helper.assertTrue(items.extractItem(0, Integer.MAX_VALUE, false)
			.getCount() == 7 && trap.stored() == 0, "asking for everything takes what there is");
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void aSootTrapIgnoresNonsenseAmountsOfSoot(GameTestHelper helper) {
		helper.setBlock(MIDDLE, CEBlocks.SOOT_TRAP.getDefaultState());
		SootTrapBlockEntity trap = helper.getBlockEntity(MIDDLE);
		for (double mb : new double[] {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -500, 0}) {
			double kept = trap.catchSoot(mb);
			helper.assertTrue(kept == 0 && trap.stored() == 0, mb + " kept " + kept + ", holds " + trap.stored());
		}
		trap.catchSoot(SootCollector.MB_PER_SOOT);
		helper.assertTrue(trap.stored() == 1, "and still works");
		helper.assertTrue(trap.takeAll()
			.getCount() == 1 && trap.takeAll()
			.isEmpty(), "taken once");
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void turningAFilledSootTrapKeepsItsSoot(GameTestHelper helper) {
		helper.setBlock(MIDDLE, CEBlocks.SOOT_TRAP.getDefaultState());
		SootTrapBlockEntity trap = helper.getBlockEntity(MIDDLE);
		trap.catchSoot(SootCollector.MB_PER_SOOT * 9);
		for (Direction.Axis axis : Direction.Axis.values()) {
			helper.setBlock(MIDDLE, helper.getBlockState(MIDDLE)
				.setValue(ExhaustCanisterBlock.AXIS, axis));
			SootTrapBlockEntity now = helper.getBlockEntity(MIDDLE);
			helper.assertTrue(now.stored() == 9, "holds " + now.stored() + " after turning to " + axis);
		}
		helper.assertTrue(droppedAround(helper, MIDDLE, CEItems.SOOT.get()) == 0, "and spills none");
		helper.succeed();
	}

	// --- Muffler and soot trap as pipe fittings ---

	@GameTest(template = "empty")
	public static void anExhaustCanisterTakesExhaustPipesOnItsTwoEndsOnly(GameTestHelper helper) {
		for (Block block : new Block[] {CEBlocks.MUFFLER.get(), CEBlocks.SOOT_TRAP.get()}) {
			ExhaustCanisterBlock canister = (ExhaustCanisterBlock) block;
			for (Direction.Axis axis : Direction.Axis.values()) {
				BlockState state = block.defaultBlockState()
					.setValue(ExhaustCanisterBlock.AXIS, axis);
				int ends = 0;
				for (Direction side : Direction.values()) {
					helper.assertTrue(!canister.acceptsPipe(state, side, PortKind.INTAKE), block + " takes intake pipe " + side);
					helper.assertTrue(canister.acceptsPipe(state, side, PortKind.EXHAUST) == (side.getAxis() == axis),
						block + " " + axis + " side " + side);
					helper.assertTrue((canister.kindFacingHead(state, side) == PortKind.EXHAUST) == (side.getAxis() == axis),
						block + " facing a head " + side);
					if (canister.acceptsPipe(state, side, PortKind.EXHAUST))
						ends++;
				}
				helper.assertTrue(ends == 2, "two ends, not " + ends);
			}
		}
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void aWrenchTakesACanisterThroughAllThreeAxesAndBack(GameTestHelper helper) {
		for (Block block : new Block[] {CEBlocks.MUFFLER.get(), CEBlocks.SOOT_TRAP.get()}) {
			ExhaustCanisterBlock canister = (ExhaustCanisterBlock) block;
			for (Direction face : Direction.values()) {
				BlockState state = block.defaultBlockState();
				var seen = java.util.EnumSet.noneOf(Direction.Axis.class);
				for (int turn = 0; turn < 3; turn++) {
					seen.add(state.getValue(BlockStateProperties.AXIS));
					state = canister.getRotatedBlockState(state, face);
				}
				helper.assertTrue(seen.size() == 3 && state == block.defaultBlockState(), block + " from face " + face + ": " + seen);
			}
			BlockState upright = block.defaultBlockState()
				.setValue(BlockStateProperties.AXIS, Direction.Axis.Y);
			for (Rotation rotation : Rotation.values())
				helper.assertTrue(upright.rotate(rotation) == upright, "an upright canister stays upright");
		}
		helper.succeed();
	}

	// --- Flare stack ---

	@GameTest(template = "empty", timeoutTicks = 100)
	public static void aTallPileOfFlareStacksOnNothingStaysOut(GameTestHelper helper) {
		BlockPos base = new BlockPos(4, 0, 3);
		for (int y = 1; y <= 5; y++)
			helper.setBlock(base.above(y), CEBlocks.FLARE_STACK.getDefaultState()
				.setValue(FlareStackBlock.LIT, true));
		helper.runAfterDelay(30, () -> {
			for (int y = 1; y <= 5; y++)
				helper.assertBlockProperty(base.above(y), FlareStackBlock.LIT, false);
			helper.succeed();
		});
	}

	@GameTest(template = "empty", timeoutTicks = 100)
	public static void aFlareStackOnACondenserWithNoColumnStaysOut(GameTestHelper helper) {
		helper.setBlock(MIDDLE, CEBlocks.CONDENSER.getDefaultState());
		helper.setBlock(MIDDLE.above(), CEBlocks.FLARE_STACK.getDefaultState()
			.setValue(FlareStackBlock.LIT, true));
		helper.runAfterDelay(30, () -> {
			helper.assertBlockProperty(MIDDLE.above(), FlareStackBlock.LIT, false);
			helper.succeed();
		});
	}

	// --- Every block ---

	/** A block that drops nothing when broken is a block a player loses. */
	@GameTest(template = "empty")
	public static void everyBlockOfTheModGivesSomethingBackWhenBroken(GameTestHelper helper) {
		List<String> nothing = new ArrayList<>();
		int checked = 0;
		for (Block block : BuiltInRegistries.BLOCK) {
			if (!BuiltInRegistries.BLOCK.getKey(block)
				.getNamespace()
				.equals(CreateEngines.MOD_ID) || block instanceof LiquidBlock)
				continue;
			checked++;
			helper.setBlock(MIDDLE, Blocks.AIR);
			BlockPos pos = helper.absolutePos(MIDDLE);
			// Set without shape updates, so a part that needs a support is still there to be asked
			helper.getLevel()
				.setBlock(pos, block.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
			BlockState placed = helper.getLevel()
				.getBlockState(pos);
			List<ItemStack> drops = Block.getDrops(placed, helper.getLevel(), pos, helper.getLevel()
				.getBlockEntity(pos), helper.makeMockPlayer(GameType.SURVIVAL), new ItemStack(Items.NETHERITE_PICKAXE));
			if (drops.stream()
				.allMatch(ItemStack::isEmpty))
				nothing.add(BuiltInRegistries.BLOCK.getKey(block)
					.getPath());
			helper.getLevel()
				.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
		}
		helper.assertTrue(checked > 40, "only " + checked + " blocks looked at");
		helper.assertTrue(nothing.isEmpty(), "these drop nothing: " + nothing);
		helper.succeed();
	}

	/** An item is lost from a world or a chest if it cannot be written out and read back. */
	@GameTest(template = "empty")
	public static void everyItemOfTheModSurvivesASave(GameTestHelper helper) {
		var access = helper.getLevel()
			.registryAccess();
		List<String> lost = new ArrayList<>();
		for (var item : BuiltInRegistries.ITEM) {
			var id = BuiltInRegistries.ITEM.getKey(item);
			if (!id.getNamespace()
				.equals(CreateEngines.MOD_ID))
				continue;
			ItemStack stack = new ItemStack(item);
			stack.setCount(stack.getMaxStackSize());
			ItemStack back = ItemStack.parse(access, stack.save(access))
				.orElse(ItemStack.EMPTY);
			if (!ItemStack.matches(stack, back))
				lost.add(id.getPath());
		}
		helper.assertTrue(lost.isEmpty(), "changed across a save: " + lost);
		helper.succeed();
	}
}
