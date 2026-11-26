package com.createengines.gametest;

import java.util.List;

import com.createengines.CreateEngines;
import com.createengines.content.battery.BatteryBlockEntity;
import com.createengines.content.cylinder.CylinderBlockEntity;
import com.createengines.content.rotor.RotorHousingBlockEntity;
import com.createengines.registry.CEBlocks;
import com.createengines.registry.CEComponents;
import com.createengines.registry.CEItems;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Breaking a block and placing it again is no repair: a battery's wear, a rotor housing's apex seals (a failed set too) and a thrown rod's
 * wreck leave on the block's item. Wear is saved whether or not the mode is on, so these run with it off, and nothing here ticks an engine.
 */
@GameTestHolder(CreateEngines.MOD_ID)
@PrefixGameTestTemplate(false)
public class WearBreakGameTests {

	private static final BlockPos AT = new BlockPos(4, 2, 3);

	/** What breaking the block at {@link #AT} drops of the block itself, which is then cleared away. */
	private static ItemStack breakIt(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos pos = helper.absolutePos(AT);
		BlockState state = level.getBlockState(pos);
		List<ItemStack> drops = Block.getDrops(state, level, pos, level.getBlockEntity(pos));
		helper.assertTrue(drops.size() == 1, "the block drops itself: " + drops);
		level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
		return drops.get(0);
	}

	/** Places a block item at {@link #AT}, the way a player does, and returns the block entity it made. */
	private static BlockEntity place(GameTestHelper helper, ItemStack stack) {
		ServerLevel level = helper.getLevel();
		BlockPos floor = helper.absolutePos(AT.below());
		level.setBlockAndUpdate(floor, Blocks.STONE.defaultBlockState());
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		player.setItemInHand(InteractionHand.MAIN_HAND, stack);
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(floor), Direction.UP, floor, false);
		((BlockItem) stack.getItem()).place(new BlockPlaceContext(player, InteractionHand.MAIN_HAND, stack, hit));
		BlockEntity placed = level.getBlockEntity(helper.absolutePos(AT));
		helper.assertTrue(placed != null, "it was placed");
		return placed;
	}

	private static void load(GameTestHelper helper, CompoundTag extra) {
		ServerLevel level = helper.getLevel();
		BlockEntity be = level.getBlockEntity(helper.absolutePos(AT));
		CompoundTag tag = be.saveWithoutMetadata(level.registryAccess());
		tag.merge(extra);
		be.loadWithComponents(tag, level.registryAccess());
	}

	@GameTest(template = "empty")
	public static void aWornBatteryBrokenAndPlacedAgainIsStillWorn(GameTestHelper helper) {
		for (var battery : List.of(CEBlocks.BATTERY, CEBlocks.DRY_CELL, CEBlocks.LARGE_BATTERY)) {
			helper.setBlock(AT, battery.get()
				.defaultBlockState());
			CompoundTag worn = new CompoundTag();
			worn.putDouble("Wear", 0.75);
			load(helper, worn);
			ItemStack dropped = breakIt(helper);
			helper.assertTrue(Double.valueOf(0.75)
				.equals(dropped.get(CEComponents.WEAR.get())), "the item keeps the wear: " + dropped.getComponentsPatch());
			BatteryBlockEntity placed = (BatteryBlockEntity) place(helper, dropped);
			helper.assertTrue(placed.wear() == 0.75, "and placed again it is as worn: " + placed.wear());
			helper.setBlock(AT, Blocks.AIR);
		}
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void aBatteryThatNeverWoreDropsAPlainItemAndPlacesAsNew(GameTestHelper helper) {
		helper.setBlock(AT, CEBlocks.BATTERY.get()
			.defaultBlockState());
		ItemStack dropped = breakIt(helper);
		helper.assertTrue(ItemStack.isSameItemSameComponents(dropped, CEBlocks.BATTERY.asStack()),
			"stacks with a new one: " + dropped.getComponentsPatch());
		BatteryBlockEntity placed = (BatteryBlockEntity) place(helper, dropped);
		helper.assertTrue(placed.wear() == 0, "new");
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void aRotorHousingKeepsItsSealsWearThroughBeingBroken(GameTestHelper helper) {
		helper.setBlock(AT, CEBlocks.ROTOR_HOUSING.get()
			.defaultBlockState());
		((RotorHousingBlockEntity) helper.getBlockEntity(AT)).addApexWear(0.6);
		RotorHousingBlockEntity placed = (RotorHousingBlockEntity) place(helper, breakIt(helper));
		helper.assertTrue(placed.apexWear() == 0.6 && !placed.apexFailed(), "as worn: " + placed.apexWear());
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void aFailedSetOfSealsIsStillFailedAfterTheHousingIsBrokenAndPlaced(GameTestHelper helper) {
		helper.setBlock(AT, CEBlocks.ROTOR_HOUSING.get()
			.defaultBlockState());
		RotorHousingBlockEntity housing = (RotorHousingBlockEntity) helper.getBlockEntity(AT);
		housing.addApexWear(0.9);
		housing.failApex();
		RotorHousingBlockEntity placed = (RotorHousingBlockEntity) place(helper, breakIt(helper));
		helper.assertTrue(placed.apexFailed() && placed.apexWear() == 0.9, "still failed and as worn: " + placed.apexWear());
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void aThrownRodsWreckIsStillThereAfterTheCylinderIsBrokenAndPlaced(GameTestHelper helper) {
		for (var cylinder : List.of(CEBlocks.CYLINDER, CEBlocks.PRECISION_CYLINDER)) {
			helper.setBlock(AT, cylinder.get()
				.defaultBlockState());
			((CylinderBlockEntity) helper.getBlockEntity(AT)).throwRod();
			ItemStack dropped = breakIt(helper);
			CylinderBlockEntity placed = (CylinderBlockEntity) place(helper, dropped);
			helper.assertTrue(placed.thrown(), "the wreck is still in it: " + dropped.getComponentsPatch());
			helper.setBlock(AT, Blocks.AIR);
		}
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void aSoundCylinderDropsAPlainItemWhateverItsPartsWore(GameTestHelper helper) {
		helper.setBlock(AT, CEBlocks.CYLINDER.get()
			.defaultBlockState());
		((CylinderBlockEntity) helper.getBlockEntity(AT)).addWear(0.5, 0.5);
		// The parts' wear leaves on the parts, not on the block: it must stack with a new cylinder
		ItemStack dropped = breakIt(helper);
		helper.assertTrue(ItemStack.isSameItemSameComponents(dropped, CEBlocks.CYLINDER.asStack()), "plain: " + dropped.getComponentsPatch());
		helper.assertTrue(!dropped.is(CEItems.PISTON.get()), "the block itself");
		helper.succeed();
	}
}
