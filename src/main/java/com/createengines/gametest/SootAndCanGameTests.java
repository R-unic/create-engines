package com.createengines.gametest;

import com.createengines.CreateEngines;
import com.createengines.content.airflow.PortOpeningBlock;
import com.createengines.content.head.CylinderHeadBlockEntity;
import com.createengines.content.jerrycan.JerryCanItem;
import com.createengines.content.muffler.SootTrapBlockEntity;
import com.createengines.engine.sim.SootCollector;
import com.createengines.registry.CEBlocks;
import com.createengines.registry.CEFluids;
import com.createengines.registry.CEItems;
import com.createengines.content.crankshaft.CrankshaftBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** The Soot Trap on a rich engine's exhaust, the Soot item, and the Jerry Can. */
@GameTestHolder(CreateEngines.MOD_ID)
@PrefixGameTestTemplate(false)
public class SootAndCanGameTests {

	private static final BlockPos TRAP = new BlockPos(2, 2, 2);

	private static FluidStack fs(Object fluid, int amount) {
		return new FluidStack((Fluid) fluid, amount);
	}

	private static SootTrapBlockEntity placeTrap(GameTestHelper helper, BlockPos pos) {
		helper.setBlock(pos, CEBlocks.SOOT_TRAP.getDefaultState()
			.setValue(BlockStateProperties.AXIS, Axis.X));
		return helper.getBlockEntity(pos);
	}

	private static void preload(GameTestHelper helper, SootTrapBlockEntity trap, int stored, double gathered) {
		CompoundTag tag = new CompoundTag();
		tag.putInt("Stored", stored);
		tag.putDouble("Gathered", gathered);
		trap.loadWithComponents(tag, helper.getLevel()
			.registryAccess());
	}

	@GameTest(template = "empty")
	public static void aTrapTurnsUnburnedFuelIntoLumpsAndGivesThemUp(GameTestHelper helper) {
		SootTrapBlockEntity trap = placeTrap(helper, TRAP);
		trap.catchSoot(SootCollector.MB_PER_SOOT * 3 + 10);
		if (trap.stored() != 3)
			helper.fail("expected 3 lumps, got " + trap.stored());
		ItemStack taken = trap.takeAll();
		if (!taken.is(CEItems.SOOT.get()) || taken.getCount() != 3 || trap.stored() != 0)
			helper.fail("takeAll gave " + taken + " and left " + trap.stored());
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void aHopperCanTakeSootButNotPutAnythingIn(GameTestHelper helper) {
		SootTrapBlockEntity trap = placeTrap(helper, TRAP);
		trap.catchSoot(SootCollector.MB_PER_SOOT * 5);
		var items = helper.getLevel()
			.getCapability(Capabilities.ItemHandler.BLOCK, helper.absolutePos(TRAP), null);
		if (items == null)
			helper.fail("a trap should have an item handler");
		if (!items.insertItem(0, new ItemStack(Items.DIRT, 4), false)
			.is(Items.DIRT))
			helper.fail("nothing may be put in");
		if (!items.extractItem(0, -3, false)
			.isEmpty() || trap.stored() != 5)
			helper.fail("a negative extract must give nothing");
		ItemStack out = items.extractItem(0, 2, false);
		if (out.getCount() != 2 || trap.stored() != 3)
			helper.fail("extract gave " + out + ", left " + trap.stored());
		ItemStack peek = items.extractItem(0, 99, true);
		if (peek.getCount() != 3 || trap.stored() != 3)
			helper.fail("a simulated extract must not take: " + peek + ", left " + trap.stored());
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void aFullTrapLetsTheRestThroughAndABrokenOneSpillsItsSoot(GameTestHelper helper) {
		SootTrapBlockEntity trap = placeTrap(helper, TRAP);
		trap.catchSoot(1e9);
		if (!trap.full() || trap.stored() != SootCollector.CAPACITY)
			helper.fail("trap should be full, holds " + trap.stored());
		trap.catchSoot(1e9);
		if (trap.stored() != SootCollector.CAPACITY)
			helper.fail("a full trap must not overfill");
		helper.setBlock(TRAP, Blocks.AIR);
		helper.runAfterDelay(2, () -> {
			var items = helper.getLevel()
				.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
					new net.minecraft.world.phys.AABB(helper.absolutePos(TRAP)).inflate(3));
			int count = items.stream()
				.filter(e -> e.getItem()
					.is(CEItems.SOOT.get()))
				.mapToInt(e -> e.getItem()
					.getCount())
				.sum();
			if (count != SootCollector.CAPACITY)
				helper.fail("breaking the trap should spill 64 soot, found " + count);
			helper.succeed();
		});
	}

	@GameTest(template = "empty")
	public static void aDamagedTrapSaveCannotOverfillOrPoisonIt(GameTestHelper helper) {
		SootTrapBlockEntity trap = placeTrap(helper, TRAP);
		preload(helper, trap, Integer.MAX_VALUE, Double.NaN);
		if (trap.stored() != SootCollector.CAPACITY)
			helper.fail("stored should clamp to capacity, got " + trap.stored());
		preload(helper, trap, -50, Double.POSITIVE_INFINITY);
		if (trap.stored() != 0)
			helper.fail("negative stored should clamp to 0, got " + trap.stored());
		trap.catchSoot(SootCollector.MB_PER_SOOT);
		if (trap.stored() != 1)
			helper.fail("a trap with a repaired save should still work, holds " + trap.stored());
		helper.succeed();
	}

	/**
	 * The whole path: a rich engine with a trap in its exhaust run fills it, with the engine still breathing out of
	 * its outlet. The trap is seeded to a hair under a lump so the test does not have to burn fifty mB.
	 */
	@GameTest(template = "empty", timeoutTicks = 1500)
	public static void aRichEngineFillsASootTrapInItsExhaust(GameTestHelper helper) {
		TestEngine.build(helper.getLevel(), helper.absolutePos(BlockPos.ZERO), true);
		CylinderHeadBlockEntity head = helper.getBlockEntity(TestEngine.HEAD);
		head.metering.setValue(CylinderHeadBlockEntity.MAX_METERING);
		helper.setBlock(TestEngine.EXHAUST.west(), CEBlocks.EXHAUST_OUTLET.getDefaultState()
			.setValue(PortOpeningBlock.FACING, Direction.WEST));
		SootTrapBlockEntity trap = placeTrap(helper, TestEngine.EXHAUST);
		preload(helper, trap, 0, SootCollector.MB_PER_SOOT - 0.01);
		helper.runAtTickTime(300, () -> helper.setBlock(TestEngine.STARTER_POWER, Blocks.AIR));
		helper.runAtTickTime(1300, () -> {
			if (trap.stored() < 1)
				helper.fail("a rich engine should have made at least a lump, trap holds " + trap.stored());
			CrankshaftBlockEntity crank = helper.getBlockEntity(TestEngine.PRIMARY);
			if (crank.summary() == null)
				helper.fail("the engine should still exist with a trap in its exhaust");
			if (crank.summary()
				.muffling() != 0f)
				helper.fail("a trap does not muffle: " + crank.summary());
			helper.succeed();
		});
	}

	@GameTest(template = "empty", timeoutTicks = 1500)
	public static void aTunedEngineMakesNoSootForATrap(GameTestHelper helper) {
		TestEngine.build(helper.getLevel(), helper.absolutePos(BlockPos.ZERO), true);
		helper.setBlock(TestEngine.EXHAUST.west(), CEBlocks.EXHAUST_OUTLET.getDefaultState()
			.setValue(PortOpeningBlock.FACING, Direction.WEST));
		SootTrapBlockEntity trap = placeTrap(helper, TestEngine.EXHAUST);
		helper.runAtTickTime(300, () -> helper.setBlock(TestEngine.STARTER_POWER, Blocks.AIR));
		helper.runAtTickTime(1300, () -> {
			if (trap.stored() != 0)
				helper.fail("a tuned engine should leave no soot, trap holds " + trap.stored());
			helper.succeed();
		});
	}

	@GameTest(template = "empty")
	public static void sootGrindsIntoBlackDye(GameTestHelper helper) {
		var recipe = helper.getLevel()
			.getRecipeManager()
			.byKey(CreateEngines.asResource("black_dye_from_soot"));
		if (recipe.isEmpty())
			helper.fail("black dye from soot should be a recipe");
		helper.succeed();
	}

	private static IFluidHandlerItem can(FluidStack content) {
		ItemStack stack = new ItemStack(CEItems.JERRY_CAN.get());
		IFluidHandlerItem handler = stack.getCapability(Capabilities.FluidHandler.ITEM);
		if (handler == null)
			throw new AssertionError("a jerry can should have a fluid handler");
		if (!content.isEmpty())
			handler.fill(content, IFluidHandler.FluidAction.EXECUTE);
		return handler;
	}

	@GameTest(template = "empty")
	public static void aJerryCanHoldsFourBucketsOfOneFluid(GameTestHelper helper) {
		IFluidHandlerItem can = can(FluidStack.EMPTY);
		Fluid diesel = CEFluids.DIESEL.getSource();
		Fluid gasoline = CEFluids.GASOLINE.getSource();
		if (can.fill(fs(diesel, 10_000), IFluidHandler.FluidAction.EXECUTE) != JerryCanItem.CAPACITY)
			helper.fail("a can should take exactly its capacity");
		if (can.fill(fs(gasoline, 1000), IFluidHandler.FluidAction.EXECUTE) != 0)
			helper.fail("a can full of diesel takes no gasoline");
		FluidStack out = can.drain(1500, IFluidHandler.FluidAction.EXECUTE);
		if (out.getAmount() != 1500 || out.getFluid() != diesel)
			helper.fail("drain gave " + out);
		if (JerryCanItem.contents(can.getContainer())
			.getAmount() != 2500)
			helper.fail("the can should hold 2500 mB");
		if (can.drain(99_999, IFluidHandler.FluidAction.EXECUTE)
			.getAmount() != 2500)
			helper.fail("drain cannot give more than is in it");
		if (!JerryCanItem.contents(can.getContainer())
			.isEmpty())
			helper.fail("an emptied can holds nothing");
		// An emptied can takes a different fluid again
		if (can.fill(fs(gasoline, 800), IFluidHandler.FluidAction.EXECUTE) != 800)
			helper.fail("an empty can takes any fluid");
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void aJerryCanFillsAndEmptiesATankByTheFluidUtilPath(GameTestHelper helper) {
		BlockPos at = new BlockPos(2, 2, 2);
		helper.setBlock(at, CEBlocks.REBOILER.getDefaultState());
		var tank = helper.getLevel()
			.getCapability(Capabilities.FluidHandler.BLOCK, helper.absolutePos(at), Direction.UP);
		IFluidHandlerItem can = can(fs(CEFluids.PLANT_OIL.getSource(), 3000));
		int moved = net.neoforged.neoforge.fluids.FluidUtil.tryFluidTransfer(tank, can, 4000, true)
			.getAmount();
		if (moved != 1000 && moved != 3000)
			helper.fail("the can should pour what the reboiler tank takes, poured " + moved);
		if (JerryCanItem.contents(can.getContainer())
			.getAmount() != 3000 - moved)
			helper.fail("the can should keep the rest");
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void aDamagedOverfullCanReadsAsAtMostItsCapacity(GameTestHelper helper) {
		ItemStack stack = new ItemStack(CEItems.JERRY_CAN.get());
		stack.set(com.createengines.registry.CEComponents.CAN_FLUID,
			net.neoforged.neoforge.fluids.SimpleFluidContent.copyOf(fs(CEFluids.DIESEL.getSource(), 90_000)));
		if (JerryCanItem.contents(stack)
			.getAmount() != JerryCanItem.CAPACITY)
			helper.fail("an over-full can must read as its capacity");
		int bar = ((JerryCanItem) stack.getItem()).getBarWidth(stack);
		if (bar < 0 || bar > 13)
			helper.fail("the bar must stay on the scale, was " + bar);
		helper.succeed();
	}
}
