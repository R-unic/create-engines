package com.createengines.gametest;

import com.createengines.CreateEngines;
import com.createengines.content.refinery.ColumnJointBlock;
import com.createengines.content.refinery.ColumnPortBlockEntity;
import com.createengines.content.refinery.ColumnSectionBlock;
import com.createengines.engine.sim.ColumnShape;
import com.createengines.registry.CEBlocks;
import com.createengines.registry.CEFluids;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import com.simibubi.create.content.schematics.requirement.ItemRequirement;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * A refining column that leans: a layer one block to the side of the one below, joined by slanted joint pieces the
 * column places when it forms and takes away when it breaks.
 */
@GameTestHolder(CreateEngines.MOD_ID)
@PrefixGameTestTemplate(false)
public class LeaningColumnGameTests {

	private static final int Y = 2;
	private static final BlockPos REBOILER = new BlockPos(1, Y, 1);

	private static FluidStack of(Object fluid, int amount) {
		return new FluidStack((Fluid) fluid, amount);
	}

	/**
	 * A 1-wide column: heater, Reboiler, then sections with the x of each layer in {@code xs}, then the Condenser over the
	 * last. The layer i sits at height Y + 1 + i.
	 */
	private static void build(GameTestHelper helper, int... xs) {
		helper.setBlock(new BlockPos(1, 1, 1), CEBlocks.HEAT_EXCHANGER.getDefaultState()
			.setValue(BlazeBurnerBlock.HEAT_LEVEL, BlazeBurnerBlock.HeatLevel.KINDLED));
		helper.setBlock(REBOILER, CEBlocks.REBOILER.getDefaultState());
		for (int i = 0; i < xs.length; i++)
			helper.setBlock(new BlockPos(xs[i], Y + 1 + i, 1), CEBlocks.COLUMN_SECTION.getDefaultState());
		helper.setBlock(new BlockPos(xs[xs.length - 1], Y + 1 + xs.length, 1), CEBlocks.CONDENSER.getDefaultState());
	}

	private static ColumnPortBlockEntity port(GameTestHelper helper, BlockPos pos) {
		return (ColumnPortBlockEntity) helper.getBlockEntity(pos);
	}

	private static void assertJoint(GameTestHelper helper, BlockPos pos, boolean upper, Direction facing) {
		var state = helper.getBlockState(pos);
		if (!state.is(CEBlocks.COLUMN_JOINT.get()))
			helper.fail("expected a joint at " + pos + ", found " + state, pos);
		if (state.getValue(ColumnJointBlock.UPPER) != upper || state.getValue(ColumnJointBlock.FACING) != facing)
			helper.fail("joint at " + pos + " should be " + (upper ? "upper" : "lower") + " facing " + facing + ", is " + state, pos);
	}

	@GameTest(template = "empty_large", timeoutTicks = 200)
	public static void aLeaningColumnFormsAndPlacesItsJoints(GameTestHelper helper) {
		// Layers x: 1, 1, 2, 2 (so it leans east between the second and third)
		build(helper, 1, 1, 2, 2);
		helper.runAfterDelay(40, () -> {
			var shape = port(helper, REBOILER).shape();
			if (!shape.formed())
				helper.fail("the leaning column should form, fault " + shape.fault());
			if (shape.height() != 4 || !shape.leans())
				helper.fail("expected 4 layers and a lean, got " + shape);
			// Lower joint beside the second section at its level, upper joint above it and beside the third
			assertJoint(helper, new BlockPos(2, Y + 2, 1), false, Direction.EAST);
			assertJoint(helper, new BlockPos(1, Y + 3, 1), true, Direction.EAST);
			helper.succeed();
		});
	}

	@GameTest(template = "empty_large", timeoutTicks = 200)
	public static void everyPartOfALeaningColumnFindsTheController(GameTestHelper helper) {
		build(helper, 1, 1, 2, 2);
		helper.runAfterDelay(60, () -> {
			// The Condenser sits over the last, overhanging layer: it must find the Reboiler through the joint
			var condenser = port(helper, new BlockPos(2, Y + 5, 1));
			if (condenser.controller() == null)
				helper.fail("the Condenser of a leaning column should find its controller, fault " + condenser.shape()
					.fault());
			if (!condenser.shape()
				.equals(port(helper, REBOILER).shape()))
				helper.fail("the Condenser should read the same column as the Reboiler");
			helper.succeed();
		});
	}

	@GameTest(template = "empty_large", timeoutTicks = 500)
	public static void aLeaningColumnRefinesAndLightsItsWindowsOnTheLeanedLayers(GameTestHelper helper) {
		build(helper, 1, 1, 2, 2);
		BlockPos leaned = new BlockPos(2, Y + 3, 1);
		BlockPos condenser = new BlockPos(2, Y + 5, 1);
		helper.runAfterDelay(30, () -> {
			helper.getLevel()
				.getCapability(Capabilities.FluidHandler.BLOCK, helper.absolutePos(REBOILER), Direction.UP)
				.fill(of(CEFluids.PLANT_OIL.getSource(), 500), IFluidHandler.FluidAction.EXECUTE);
			helper.getLevel()
				.getCapability(Capabilities.ItemHandler.BLOCK, helper.absolutePos(REBOILER), Direction.UP)
				.insertItem(0, new ItemStack(Items.COAL, 4), false);
		});
		helper.succeedWhen(() -> {
			FluidStack made = helper.getLevel()
				.getCapability(Capabilities.FluidHandler.BLOCK, helper.absolutePos(condenser), Direction.UP)
				.getFluidInTank(0);
			helper.assertTrue(made.getFluid() == CEFluids.DIESEL.getSource() && made.getAmount() >= 20,
				"the Condenser of a leaning column should hold diesel, holds " + made);
			helper.assertBlockProperty(leaned, ColumnSectionBlock.ACTIVE, true);
		});
	}

	@GameTest(template = "empty_large", timeoutTicks = 300)
	public static void breakingASectionRemovesTheJointsAndTheColumn(GameTestHelper helper) {
		build(helper, 1, 1, 2, 2);
		helper.runAfterDelay(40, () -> helper.destroyBlock(new BlockPos(2, Y + 4, 1)));
		helper.runAfterDelay(120, () -> {
			helper.assertBlockNotPresent(CEBlocks.COLUMN_JOINT.get(), new BlockPos(2, Y + 2, 1));
			helper.assertBlockNotPresent(CEBlocks.COLUMN_JOINT.get(), new BlockPos(1, Y + 3, 1));
			if (port(helper, REBOILER).shape()
				.formed())
				helper.fail("a column with a section gone is not formed");
			helper.succeed();
		});
	}

	@GameTest(template = "empty_large", timeoutTicks = 300)
	public static void breakingTheReboilerRemovesTheJoints(GameTestHelper helper) {
		build(helper, 1, 1, 2, 2);
		helper.runAfterDelay(40, () -> helper.destroyBlock(REBOILER));
		helper.runAfterDelay(80, () -> {
			helper.assertBlockNotPresent(CEBlocks.COLUMN_JOINT.get(), new BlockPos(2, Y + 2, 1));
			helper.assertBlockNotPresent(CEBlocks.COLUMN_JOINT.get(), new BlockPos(1, Y + 3, 1));
			helper.succeed();
		});
	}

	@GameTest(template = "empty_large", timeoutTicks = 300)
	public static void aBlockInTheWayStopsTheColumnAndGogglesNameIt(GameTestHelper helper) {
		helper.setBlock(new BlockPos(2, Y + 2, 1), Blocks.STONE.defaultBlockState());
		build(helper, 1, 1, 2, 2);
		helper.runAfterDelay(40, () -> {
			var shape = port(helper, REBOILER).shape();
			if (shape.fault() != ColumnShape.Fault.LEAN_BLOCKED)
				helper.fail("a block in the way should block the lean, fault is " + shape.fault());
			BlockPos blocked = new BlockPos(shape.blockedX(), shape.blockedY(), shape.blockedZ());
			if (!blocked.equals(helper.absolutePos(new BlockPos(2, Y + 2, 1))))
				helper.fail("the fault should name the stone at (2, " + (Y + 2) + ", 1), names " + blocked);
			helper.setBlock(new BlockPos(2, Y + 2, 1), Blocks.AIR);
		});
		helper.runAfterDelay(100, () -> {
			if (!port(helper, REBOILER).shape()
				.formed())
				helper.fail("clearing the block should let the column form, fault " + port(helper, REBOILER).shape()
					.fault());
			assertJoint(helper, new BlockPos(2, Y + 2, 1), false, Direction.EAST);
			helper.succeed();
		});
	}

	@GameTest(template = "empty_large", timeoutTicks = 300)
	public static void aJointKnockedOutIsPutBack(GameTestHelper helper) {
		build(helper, 1, 1, 2, 2);
		helper.runAfterDelay(40, () -> helper.setBlock(new BlockPos(2, Y + 2, 1), Blocks.AIR));
		helper.runAfterDelay(100, () -> {
			assertJoint(helper, new BlockPos(2, Y + 2, 1), false, Direction.EAST);
			helper.succeed();
		});
	}

	@GameTest(template = "empty_large", timeoutTicks = 200)
	public static void aJointIsFreeInASchematicDropsNothingAndPicksAsASection(GameTestHelper helper) {
		build(helper, 1, 1, 2, 2);
		helper.runAfterDelay(40, () -> {
			BlockPos joint = new BlockPos(2, Y + 2, 1);
			var state = helper.getBlockState(joint);
			if (!ItemRequirement.of(state, null)
				.getRequiredItems()
				.isEmpty())
				helper.fail("a joint should cost nothing in a schematic");
			var drops = net.minecraft.world.level.block.Block.getDrops(state, helper.getLevel(), helper.absolutePos(joint), null);
			if (!drops.isEmpty())
				helper.fail("a joint drops nothing, drops " + drops);
			ItemStack picked = state.getBlock()
				.getCloneItemStack(helper.getLevel(), helper.absolutePos(joint), state);
			if (!picked.is(CEBlocks.COLUMN_SECTION.asItem()))
				helper.fail("middle-clicking a joint should give a Column Section, gives " + picked);
			helper.succeed();
		});
	}

	@GameTest(template = "empty_large", timeoutTicks = 200)
	public static void jointsTurnAndMirrorWithASchematic(GameTestHelper helper) {
		var state = CEBlocks.COLUMN_JOINT.getDefaultState()
			.setValue(ColumnJointBlock.FACING, Direction.EAST);
		var turned = state.rotate(net.minecraft.world.level.block.Rotation.CLOCKWISE_90);
		if (turned.getValue(ColumnJointBlock.FACING) != Direction.SOUTH)
			helper.fail("rotating east by 90 clockwise should face south, faces " + turned.getValue(ColumnJointBlock.FACING));
		var mirrored = state.mirror(net.minecraft.world.level.block.Mirror.LEFT_RIGHT);
		if (mirrored.getValue(ColumnJointBlock.FACING) != Direction.EAST)
			helper.fail("mirroring left to right keeps an east joint east (it flips north and south)");
		var north = state.setValue(ColumnJointBlock.FACING, Direction.NORTH)
			.mirror(net.minecraft.world.level.block.Mirror.LEFT_RIGHT);
		if (north.getValue(ColumnJointBlock.FACING) != Direction.SOUTH)
			helper.fail("mirroring a north joint should face it south");
		helper.succeed();
	}

	@GameTest(template = "empty_large", timeoutTicks = 200)
	public static void aJointWhoseSectionsAreGoneLetsGoOnItsNextTick(GameTestHelper helper) {
		BlockPos at = new BlockPos(3, 3, 3);
		// A lone lower joint with nothing to hang from
		helper.setBlock(at, CEBlocks.COLUMN_JOINT.getDefaultState()
			.setValue(ColumnJointBlock.FACING, Direction.EAST));
		helper.runAfterDelay(2, () -> {
			var state = helper.getBlockState(at);
			if (state.is(CEBlocks.COLUMN_JOINT.get()) && ColumnJointBlock.supported(helper.getLevel(), helper.absolutePos(at), state))
				helper.fail("a lone joint cannot be supported");
			var level = helper.getLevel();
			if (state.is(CEBlocks.COLUMN_JOINT.get()))
				state.randomTick(level, helper.absolutePos(at), level.random);
			helper.assertBlockNotPresent(CEBlocks.COLUMN_JOINT.get(), at);
			helper.succeed();
		});
	}

	@GameTest(template = "empty_large", timeoutTicks = 200)
	public static void damagedLeanDataInASaveCannotBreakAColumnPart(GameTestHelper helper) {
		build(helper, 1, 1, 2, 2);
		helper.runAfterDelay(40, () -> {
			var reboiler = port(helper, REBOILER);
			CompoundTag tag = reboiler.saveWithFullMetadata(helper.getLevel()
				.registryAccess());
			tag.putIntArray("Lean", new int[] {Integer.MAX_VALUE, Integer.MIN_VALUE, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5,
				5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5,
				5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5});
			tag.putInt("Width", 99);
			tag.putInt("Height", 99);
			reboiler.loadWithComponents(tag, helper.getLevel()
				.registryAccess());
			// Reading must not hang or throw, and the next scan puts the real shape back
			reboiler.shape()
				.joints();
		});
		helper.runAfterDelay(100, () -> {
			var shape = port(helper, REBOILER).shape();
			if (!shape.formed() || shape.height() != 4)
				helper.fail("the next scan should correct a damaged save, shape " + shape);
			helper.succeed();
		});
	}
}
