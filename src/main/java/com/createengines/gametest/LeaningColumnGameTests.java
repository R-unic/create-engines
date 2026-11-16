package com.createengines.gametest;

import com.createengines.CreateEngines;
import com.createengines.content.refinery.ColumnPortBlockEntity;
import com.createengines.content.refinery.ColumnSectionBlock;
import com.createengines.content.refinery.ColumnSectionBlock.Lean;
import com.createengines.engine.sim.ColumnShape;
import com.createengines.registry.CEBlocks;
import com.createengines.registry.CEFluids;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;

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
 * A refining column that leans: a layer one block to the side of the one below. The sections join by themselves, the
 * way Create's chutes do: there is no joint block, each section grows its own arm towards its diagonal neighbour.
 */
@GameTestHolder(CreateEngines.MOD_ID)
@PrefixGameTestTemplate(false)
public class LeaningColumnGameTests {

	private static final int Y = 2;
	private static final BlockPos REBOILER = new BlockPos(1, Y, 1);
	/** The section that stands under the lean, and the one that leans, in a column built with layers x 1, 1, 2, 2. */
	private static final BlockPos LOWER = new BlockPos(1, Y + 2, 1);
	private static final BlockPos UPPER = new BlockPos(2, Y + 3, 1);

	private static FluidStack of(Object fluid, int amount) {
		return new FluidStack((Fluid) fluid, amount);
	}

	/**
	 * A 1-wide column: heater, Reboiler, then sections with the x of each layer in {@code xs}, then the Condenser over the
	 * last. Layer i sits at height Y + 1 + i.
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

	/** The state a player would place at {@code pos}: the arms its neighbours call for (what getStateForPlacement gives). */
	private static net.minecraft.world.level.block.state.BlockState placed(GameTestHelper helper, BlockPos pos) {
		return ColumnSectionBlock.withArms(helper.getLevel(), helper.absolutePos(pos), CEBlocks.COLUMN_SECTION.getDefaultState());
	}

	private static void assertArms(GameTestHelper helper, BlockPos pos, Lean up, Lean down) {
		var state = helper.getBlockState(pos);
		if (!state.is(CEBlocks.COLUMN_SECTION.get()))
			helper.fail("expected a section at " + pos + ", found " + state, pos);
		if (state.getValue(ColumnSectionBlock.LEAN_UP) != up || state.getValue(ColumnSectionBlock.LEAN_DOWN) != down)
			helper.fail("section at " + pos + " should have arms up " + up + " and down " + down + ", has " + state, pos);
	}

	@GameTest(template = "empty_large", timeoutTicks = 200)
	public static void aLeaningColumnFormsAndItsSectionsJoinThemselves(GameTestHelper helper) {
		build(helper, 1, 1, 2, 2);
		helper.runAfterDelay(40, () -> {
			var shape = port(helper, REBOILER).shape();
			if (!shape.formed())
				helper.fail("the leaning column should form, fault " + shape.fault());
			if (shape.height() != 4 || !shape.leans())
				helper.fail("expected 4 layers and a lean, got " + shape);
			assertArms(helper, LOWER, Lean.EAST, Lean.NONE);
			assertArms(helper, UPPER, Lean.NONE, Lean.WEST);
			// The straight sections have no arms, and no block was put in the spaces the arms are drawn into
			assertArms(helper, new BlockPos(1, Y + 1, 1), Lean.NONE, Lean.NONE);
			assertArms(helper, new BlockPos(2, Y + 4, 1), Lean.NONE, Lean.NONE);
			helper.assertBlockNotPresent(CEBlocks.COLUMN_SECTION.get(), new BlockPos(1, Y + 3, 1));
			helper.assertBlockNotPresent(CEBlocks.COLUMN_SECTION.get(), new BlockPos(2, Y + 2, 1));
			helper.succeed();
		});
	}

	@GameTest(template = "empty_large", timeoutTicks = 200)
	public static void theArmsAppearAndGoWithTheNeighbourAtAnyPlacingOrder(GameTestHelper helper) {
		// Place the leaning layer first and the lower one after: the lower one must still grow its arm
		helper.setBlock(UPPER, placed(helper, UPPER));
		assertArms(helper, UPPER, Lean.NONE, Lean.NONE);
		helper.setBlock(LOWER, placed(helper, LOWER));
		assertArms(helper, LOWER, Lean.EAST, Lean.NONE);
		assertArms(helper, UPPER, Lean.NONE, Lean.WEST);
		// Take the lower one away: the upper loses its arm
		helper.setBlock(LOWER, Blocks.AIR);
		assertArms(helper, UPPER, Lean.NONE, Lean.NONE);
		// A section straight above takes the arm away from the diagonal one
		helper.setBlock(LOWER, placed(helper, LOWER));
		helper.setBlock(LOWER.above(), placed(helper, LOWER.above()));
		assertArms(helper, LOWER, Lean.NONE, Lean.NONE);
		helper.succeed();
	}

	@GameTest(template = "empty_large", timeoutTicks = 200)
	public static void everyPartOfALeaningColumnFindsTheController(GameTestHelper helper) {
		build(helper, 1, 1, 2, 2);
		helper.runAfterDelay(60, () -> {
			// The Condenser sits over the last, overhanging layer: it must find the Reboiler across the lean
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
			helper.assertBlockProperty(UPPER, ColumnSectionBlock.ACTIVE, true);
			helper.assertBlockProperty(UPPER, ColumnSectionBlock.LEAN_DOWN, Lean.WEST);
		});
	}

	@GameTest(template = "empty_large", timeoutTicks = 300)
	public static void breakingASectionUnformsTheColumnAndTheArmsGo(GameTestHelper helper) {
		build(helper, 1, 1, 2, 2);
		helper.runAfterDelay(40, () -> helper.destroyBlock(new BlockPos(2, Y + 4, 1)));
		helper.runAfterDelay(120, () -> {
			if (port(helper, REBOILER).shape()
				.formed())
				helper.fail("a column with a section gone is not formed");
			helper.destroyBlock(UPPER);
		});
		helper.runAfterDelay(140, () -> {
			assertArms(helper, LOWER, Lean.NONE, Lean.NONE);
			helper.succeed();
		});
	}

	@GameTest(template = "empty_large", timeoutTicks = 300)
	public static void aBlockInTheWayStopsTheColumnAndGogglesNameIt(GameTestHelper helper) {
		// The brace under the leaning layer is drawn into this block
		BlockPos brace = new BlockPos(2, Y + 2, 1);
		helper.setBlock(brace, Blocks.STONE.defaultBlockState());
		build(helper, 1, 1, 2, 2);
		helper.runAfterDelay(40, () -> {
			var shape = port(helper, REBOILER).shape();
			if (shape.fault() != ColumnShape.Fault.LEAN_BLOCKED)
				helper.fail("a block in the way should block the lean, fault is " + shape.fault());
			BlockPos blocked = new BlockPos(shape.blockedX(), shape.blockedY(), shape.blockedZ());
			if (!blocked.equals(helper.absolutePos(brace)))
				helper.fail("the fault should name the stone at " + brace + ", names " + blocked);
			helper.setBlock(brace, Blocks.AIR);
		});
		helper.runAfterDelay(100, () -> {
			if (!port(helper, REBOILER).shape()
				.formed())
				helper.fail("clearing the block should let the column form, fault " + port(helper, REBOILER).shape()
					.fault());
			helper.succeed();
		});
	}

	@GameTest(template = "empty_large", timeoutTicks = 100)
	public static void armsTurnAndMirrorWithASchematic(GameTestHelper helper) {
		var state = CEBlocks.COLUMN_SECTION.getDefaultState()
			.setValue(ColumnSectionBlock.LEAN_UP, Lean.EAST)
			.setValue(ColumnSectionBlock.LEAN_DOWN, Lean.NORTH);
		var turned = state.rotate(net.minecraft.world.level.block.Rotation.CLOCKWISE_90);
		if (turned.getValue(ColumnSectionBlock.LEAN_UP) != Lean.SOUTH || turned.getValue(ColumnSectionBlock.LEAN_DOWN) != Lean.EAST)
			helper.fail("a 90 degree turn should take east to south and north to east, gives " + turned);
		var mirrored = state.mirror(net.minecraft.world.level.block.Mirror.LEFT_RIGHT);
		if (mirrored.getValue(ColumnSectionBlock.LEAN_UP) != Lean.EAST || mirrored.getValue(ColumnSectionBlock.LEAN_DOWN) != Lean.SOUTH)
			helper.fail("a left-right mirror keeps east and flips north to south, gives " + mirrored);
		helper.succeed();
	}

	@GameTest(template = "empty_large", timeoutTicks = 200)
	public static void damagedLeanDataInASaveCannotBreakAColumnPart(GameTestHelper helper) {
		build(helper, 1, 1, 2, 2);
		helper.runAfterDelay(40, () -> {
			var reboiler = port(helper, REBOILER);
			CompoundTag tag = reboiler.saveWithFullMetadata(helper.getLevel()
				.registryAccess());
			int[] wild = new int[200];
			java.util.Arrays.fill(wild, 5);
			wild[0] = Integer.MAX_VALUE;
			wild[1] = Integer.MIN_VALUE;
			tag.putIntArray("Lean", wild);
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
