package com.createengines.gametest;

import com.createengines.CreateEngines;
import com.createengines.content.refinery.ColumnPortBlockEntity;
import com.createengines.registry.CEBlocks;
import com.createengines.registry.CEFluids;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** The refining column as a structure: it forms from its parts, takes feed and catalyst, and makes product. */
@GameTestHolder(CreateEngines.MOD_ID)
@PrefixGameTestTemplate(false)
public class RefiningColumnGameTests {

	private static final int BASE_Y = 2;

	/** A column {@code width} wide from (1, 1, 1): a heater, the Reboilers, two layers of sections and the Condensers. */
	private static void build(GameTestHelper helper, int width, BlazeBurnerBlock.HeatLevel heat) {
		for (int x = 1; x <= width; x++)
			for (int z = 1; z <= width; z++) {
				helper.setBlock(new BlockPos(x, 1, z), CEBlocks.HEAT_EXCHANGER.getDefaultState()
					.setValue(BlazeBurnerBlock.HEAT_LEVEL, heat));
				helper.setBlock(new BlockPos(x, BASE_Y, z), CEBlocks.REBOILER.getDefaultState());
				helper.setBlock(new BlockPos(x, BASE_Y + 1, z), CEBlocks.COLUMN_SECTION.getDefaultState());
				helper.setBlock(new BlockPos(x, BASE_Y + 2, z), CEBlocks.COLUMN_SECTION.getDefaultState());
				helper.setBlock(new BlockPos(x, BASE_Y + 3, z), CEBlocks.CONDENSER.getDefaultState());
			}
	}

	private static IFluidHandler fluids(GameTestHelper helper, BlockPos pos) {
		return helper.getLevel()
			.getCapability(Capabilities.FluidHandler.BLOCK, helper.absolutePos(pos), Direction.UP);
	}

	private static ColumnPortBlockEntity port(GameTestHelper helper, BlockPos pos) {
		return (ColumnPortBlockEntity) helper.getBlockEntity(pos);
	}

	private static FluidStack of(Object fluid, int amount) {
		return new FluidStack((Fluid) fluid, amount);
	}

	private static int drainAll(IFluidHandler handler) {
		return handler.getFluidInTank(0)
			.getAmount();
	}

	@GameTest(template = "empty", timeoutTicks = 400)
	public static void aNarrowColumnRefinesPlantOilToDiesel(GameTestHelper helper) {
		build(helper, 1, BlazeBurnerBlock.HeatLevel.KINDLED);
		BlockPos reboiler = new BlockPos(1, BASE_Y, 1);
		BlockPos condenser = new BlockPos(1, BASE_Y + 3, 1);
		helper.runAfterDelay(15, () -> {
			if (fluids(helper, reboiler).fill(of(CEFluids.PLANT_OIL.getSource(), 500), IFluidHandler.FluidAction.EXECUTE) != 500)
				helper.fail("the Reboiler should take plant oil as feed");
			var items = helper.getLevel()
				.getCapability(Capabilities.ItemHandler.BLOCK, helper.absolutePos(reboiler), Direction.UP);
			if (items == null || !items.insertItem(0, new ItemStack(Items.COAL, 4), false)
				.isEmpty())
				helper.fail("the Reboiler should take coal as catalyst");
		});
		helper.succeedWhen(() -> {
			FluidStack made = fluids(helper, condenser).getFluidInTank(0);
			helper.assertTrue(made.getFluid() == CEFluids.DIESEL.getSource() && made.getAmount() >= 20,
				"the Condenser should hold diesel, holds " + made);
			ColumnPortBlockEntity controller = port(helper, reboiler);
			helper.assertTrue(controller.feedTank()
				.getFluidAmount() < 500, "the feed should be going down");
			helper.assertTrue(controller.catalystStack()
				.getCount() <= 4, "catalyst is used by the column");
		});
	}

	@GameTest(template = "empty_large", timeoutTicks = 400)
	public static void theFlareAndTheWindowsBurnOnlyWhileTheColumnWorks(GameTestHelper helper) {
		build(helper, 1, BlazeBurnerBlock.HeatLevel.KINDLED);
		BlockPos reboiler = new BlockPos(1, BASE_Y, 1);
		BlockPos section = new BlockPos(1, BASE_Y + 1, 1);
		BlockPos flare = new BlockPos(1, BASE_Y + 4, 1);
		helper.setBlock(flare, CEBlocks.FLARE_STACK.getDefaultState());
		helper.runAfterDelay(15, () -> {
			fluids(helper, reboiler).fill(of(CEFluids.PLANT_OIL.getSource(), 300), IFluidHandler.FluidAction.EXECUTE);
			helper.getLevel()
				.getCapability(Capabilities.ItemHandler.BLOCK, helper.absolutePos(reboiler), Direction.UP)
				.insertItem(0, new ItemStack(Items.COAL, 4), false);
			helper.assertBlockProperty(flare, com.createengines.content.refinery.FlareStackBlock.LIT, false);
		});
		helper.runAfterDelay(60, () -> {
			helper.assertBlockProperty(flare, com.createengines.content.refinery.FlareStackBlock.LIT, true);
			helper.assertBlockProperty(section, com.createengines.content.refinery.ColumnSectionBlock.ACTIVE, true);
			// Break the Condenser: the column stops existing, so the windows go dark
			helper.destroyBlock(new BlockPos(1, BASE_Y + 3, 1));
		});
		helper.runAfterDelay(120, () -> {
			helper.assertBlockProperty(flare, com.createengines.content.refinery.FlareStackBlock.LIT, false);
			helper.assertBlockProperty(section, com.createengines.content.refinery.ColumnSectionBlock.ACTIVE, false);
			helper.succeed();
		});
	}

	@GameTest(template = "empty_large", timeoutTicks = 400)
	public static void theWindowsGlowTheColourOfTheFluidInThem(GameTestHelper helper) {
		build(helper, 1, BlazeBurnerBlock.HeatLevel.KINDLED);
		BlockPos reboiler = new BlockPos(1, BASE_Y, 1);
		BlockPos lower = new BlockPos(1, BASE_Y + 1, 1);
		BlockPos upper = new BlockPos(1, BASE_Y + 2, 1);
		helper.runAfterDelay(15, () -> {
			fluids(helper, reboiler).fill(of(CEFluids.PLANT_OIL.getSource(), 300), IFluidHandler.FluidAction.EXECUTE);
			helper.getLevel()
				.getCapability(Capabilities.ItemHandler.BLOCK, helper.absolutePos(reboiler), Direction.UP)
				.insertItem(0, new ItemStack(Items.COAL, 4), false);
		});
		helper.runAfterDelay(60, () -> {
			helper.assertBlockProperty(lower, com.createengines.content.refinery.ColumnSectionBlock.TINT,
				com.createengines.engine.sim.WindowTint.PLANT_OIL.ordinal());
			helper.assertBlockProperty(upper, com.createengines.content.refinery.ColumnSectionBlock.TINT,
				com.createengines.engine.sim.WindowTint.DIESEL.ordinal());
			// Break the Condenser: the column stops existing and the windows go back to the plain tint
			helper.destroyBlock(new BlockPos(1, BASE_Y + 3, 1));
		});
		helper.runAfterDelay(120, () -> {
			helper.assertBlockProperty(lower, com.createengines.content.refinery.ColumnSectionBlock.TINT, 0);
			helper.assertBlockProperty(upper, com.createengines.content.refinery.ColumnSectionBlock.TINT, 0);
			helper.succeed();
		});
	}

	@GameTest(template = "empty", timeoutTicks = 400)
	public static void comparatorsReadTheFeedOffReboilersAndTheProductOffCondensers(GameTestHelper helper) {
		build(helper, 1, BlazeBurnerBlock.HeatLevel.NONE);
		BlockPos reboiler = new BlockPos(1, BASE_Y, 1);
		BlockPos condenser = new BlockPos(1, BASE_Y + 3, 1);
		helper.runAfterDelay(15, () -> {
			int empty = helper.getLevel()
				.getBlockState(helper.absolutePos(reboiler))
				.getAnalogOutputSignal(helper.getLevel(), helper.absolutePos(reboiler));
			if (empty != 0)
				helper.fail("an empty Reboiler should read 0, reads " + empty);
			fluids(helper, reboiler).fill(of(CEFluids.PLANT_OIL.getSource(), 500), IFluidHandler.FluidAction.EXECUTE);
		});
		helper.runAfterDelay(30, () -> {
			int feed = helper.getLevel()
				.getBlockState(helper.absolutePos(reboiler))
				.getAnalogOutputSignal(helper.getLevel(), helper.absolutePos(reboiler));
			int product = helper.getLevel()
				.getBlockState(helper.absolutePos(condenser))
				.getAnalogOutputSignal(helper.getLevel(), helper.absolutePos(condenser));
			if (feed < 7 || feed > 9)
				helper.fail("a Reboiler half full should read about 8, reads " + feed);
			if (product != 0)
				helper.fail("the Condenser holds nothing, so it should read 0, reads " + product);
			helper.succeed();
		});
	}

	@GameTest(template = "empty", timeoutTicks = 200)
	public static void aColumnWithTheWrongPartsGoggleItsFault(GameTestHelper helper) {
		build(helper, 1, BlazeBurnerBlock.HeatLevel.KINDLED);
		helper.setBlock(new BlockPos(1, BASE_Y + 3, 1), Blocks.AIR.defaultBlockState());
		helper.runAfterDelay(40, () -> {
			var shape = port(helper, new BlockPos(1, BASE_Y, 1)).shape();
			helper.assertTrue(shape.fault() == com.createengines.engine.sim.ColumnShape.Fault.NO_CONDENSER,
				"with no Condenser the fault should say so, it says " + shape.fault());
			helper.succeed();
		});
	}

	@GameTest(template = "empty", timeoutTicks = 400)
	public static void aWideColumnSharesOneTankAcrossAllItsPorts(GameTestHelper helper) {
		build(helper, 2, BlazeBurnerBlock.HeatLevel.KINDLED);
		BlockPos controllerPos = new BlockPos(1, BASE_Y, 1);
		BlockPos farReboiler = new BlockPos(2, BASE_Y, 2);
		BlockPos farCondenser = new BlockPos(2, BASE_Y + 3, 2);
		helper.runAfterDelay(15, () -> {
			if (fluids(helper, farReboiler).fill(of(CEFluids.PLANT_OIL.getSource(), 800), IFluidHandler.FluidAction.EXECUTE) != 800)
				helper.fail("a Reboiler away from the controller should still take feed");
			var items = helper.getLevel()
				.getCapability(Capabilities.ItemHandler.BLOCK, helper.absolutePos(farReboiler), Direction.UP);
			if (items == null || !items.insertItem(0, new ItemStack(Items.COAL, 4), false)
				.isEmpty())
				helper.fail("a Reboiler away from the controller should take catalyst");
		});
		helper.succeedWhen(() -> {
			ColumnPortBlockEntity controller = port(helper, controllerPos);
			helper.assertTrue(controller.isController(), "the corner Reboiler should run the column");
			helper.assertTrue(controller.shape()
				.width() == 2 && controller.shape()
					.height() == 2, "the column should read as 2 wide and 2 tall");
			helper.assertTrue(!port(helper, farReboiler).isController(), "only the corner Reboiler runs the column");
			helper.assertTrue(controller.feedTank()
				.getCapacity() == 4000, "a 2 wide column holds 4000 mB of feed");
			helper.assertTrue(drainAll(fluids(helper, farCondenser)) >= 40, "the far Condenser should show the product");
		});
	}

	@GameTest(template = "empty", timeoutTicks = 200)
	public static void theCondenserGivesProductAndTheReboilerOnlyTakesFeed(GameTestHelper helper) {
		build(helper, 1, BlazeBurnerBlock.HeatLevel.KINDLED);
		BlockPos reboiler = new BlockPos(1, BASE_Y, 1);
		BlockPos condenser = new BlockPos(1, BASE_Y + 3, 1);
		helper.runAfterDelay(15, () -> {
			IFluidHandler in = fluids(helper, reboiler);
			in.fill(of(CEFluids.PLANT_OIL.getSource(), 300), IFluidHandler.FluidAction.EXECUTE);
			if (!in.drain(300, IFluidHandler.FluidAction.EXECUTE)
				.isEmpty())
				helper.fail("a pump on the Reboiler must not pull the feed back out");
			if (fluids(helper, condenser).fill(of(CEFluids.PLANT_OIL.getSource(), 100), IFluidHandler.FluidAction.EXECUTE) != 0)
				helper.fail("nothing goes into the Condenser");
			if (in.fill(of(CEFluids.GASOLINE.getSource(), 100), IFluidHandler.FluidAction.SIMULATE) != 0)
				helper.fail("the Reboiler should refuse a fluid no route takes as feed");
			helper.succeed();
		});
	}

	@GameTest(template = "empty", timeoutTicks = 300)
	public static void aColdBaseMakesNothing(GameTestHelper helper) {
		build(helper, 1, BlazeBurnerBlock.HeatLevel.NONE);
		BlockPos reboiler = new BlockPos(1, BASE_Y, 1);
		helper.runAfterDelay(15, () -> {
			fluids(helper, reboiler).fill(of(CEFluids.PLANT_OIL.getSource(), 500), IFluidHandler.FluidAction.EXECUTE);
			helper.getLevel()
				.getCapability(Capabilities.ItemHandler.BLOCK, helper.absolutePos(reboiler), Direction.UP)
				.insertItem(0, new ItemStack(Items.COAL, 4), false);
		});
		helper.runAfterDelay(200, () -> {
			ColumnPortBlockEntity controller = port(helper, reboiler);
			helper.assertTrue(controller.productTank()
				.isEmpty(), "a cold column should make nothing");
			helper.assertTrue(controller.feedTank()
				.getFluidAmount() == 500, "a cold column should keep its feed");
			helper.assertTrue(controller.limit() == com.createengines.engine.sim.RefiningColumn.Limit.COLD,
				"it should say it is cold, it says " + controller.limit());
			helper.succeed();
		});
	}

	@GameTest(template = "empty", timeoutTicks = 300)
	public static void noCatalystHoldsTheColumnBack(GameTestHelper helper) {
		build(helper, 1, BlazeBurnerBlock.HeatLevel.KINDLED);
		BlockPos reboiler = new BlockPos(1, BASE_Y, 1);
		helper.runAfterDelay(15, () -> fluids(helper, reboiler)
			.fill(of(CEFluids.PLANT_OIL.getSource(), 500), IFluidHandler.FluidAction.EXECUTE));
		helper.runAfterDelay(200, () -> {
			ColumnPortBlockEntity controller = port(helper, reboiler);
			helper.assertTrue(controller.productTank()
				.isEmpty(), "no catalyst, no diesel");
			helper.assertTrue(controller.limit() == com.createengines.engine.sim.RefiningColumn.Limit.NO_CATALYST,
				"it should say the catalyst has run out, it says " + controller.limit());
			helper.succeed();
		});
	}

	@GameTest(template = "empty", timeoutTicks = 200)
	public static void breakingAColumnSpillsItsCatalyst(GameTestHelper helper) {
		build(helper, 1, BlazeBurnerBlock.HeatLevel.KINDLED);
		BlockPos reboiler = new BlockPos(1, BASE_Y, 1);
		helper.runAfterDelay(15, () -> helper.getLevel()
			.getCapability(Capabilities.ItemHandler.BLOCK, helper.absolutePos(reboiler), Direction.UP)
			.insertItem(0, new ItemStack(Items.COAL, 3), false));
		helper.runAfterDelay(30, () -> {
			helper.destroyBlock(reboiler);
			helper.assertItemEntityCountIs(Items.COAL, reboiler, 2, 3);
			helper.succeed();
		});
	}

	@GameTest(template = "empty", timeoutTicks = 300)
	public static void aCatalystOfAnotherRouteIsNamedAsTheWrongOne(GameTestHelper helper) {
		build(helper, 1, BlazeBurnerBlock.HeatLevel.KINDLED);
		BlockPos reboiler = new BlockPos(1, BASE_Y, 1);
		helper.runAfterDelay(15, () -> {
			fluids(helper, reboiler).fill(of(CEFluids.PLANT_OIL.getSource(), 500), IFluidHandler.FluidAction.EXECUTE);
			// Nether wart is the ethanol catalyst: it is a valid item for the slot but not for plant oil
			helper.getLevel()
				.getCapability(Capabilities.ItemHandler.BLOCK, helper.absolutePos(reboiler), Direction.UP)
				.insertItem(0, new ItemStack(Items.NETHER_WART, 4), false);
		});
		helper.runAfterDelay(200, () -> {
			ColumnPortBlockEntity controller = port(helper, reboiler);
			helper.assertTrue(controller.productTank()
				.isEmpty(), "the wrong catalyst makes nothing");
			helper.assertTrue(controller.limit() == com.createengines.engine.sim.RefiningColumn.Limit.NO_CATALYST,
				"it should be held back for catalyst, it says " + controller.limit());
			helper.assertTrue(controller.wrongCatalyst(), "it should say the catalyst loaded is the wrong one");
			helper.succeed();
		});
	}

	@GameTest(template = "empty", timeoutTicks = 300)
	public static void theRightCatalystIsNeverCalledWrong(GameTestHelper helper) {
		build(helper, 1, BlazeBurnerBlock.HeatLevel.KINDLED);
		BlockPos reboiler = new BlockPos(1, BASE_Y, 1);
		helper.runAfterDelay(15, () -> {
			fluids(helper, reboiler).fill(of(CEFluids.PLANT_OIL.getSource(), 500), IFluidHandler.FluidAction.EXECUTE);
			helper.getLevel()
				.getCapability(Capabilities.ItemHandler.BLOCK, helper.absolutePos(reboiler), Direction.UP)
				.insertItem(0, new ItemStack(Items.COAL, 4), false);
		});
		helper.runAfterDelay(100, () -> {
			ColumnPortBlockEntity controller = port(helper, reboiler);
			helper.assertTrue(!controller.wrongCatalyst(), "coal is the plant oil catalyst");
			helper.assertTrue(controller.limit() != com.createengines.engine.sim.RefiningColumn.Limit.NO_CATALYST,
				"holding coal it should not be held back for catalyst, it says " + controller.limit());
			helper.succeed();
		});
	}

	@GameTest(template = "empty", timeoutTicks = 300)
	public static void growingAColumnKeepsItsCatalyst(GameTestHelper helper) {
		// A 1 wide column at (2, 2) takes catalyst, then the other three columns of a 2 wide square are built around it:
		// the controller moves to the corner (1, 1) and the catalyst must go with it
		for (int y = 0; y < 4; y++)
			helper.setBlock(new BlockPos(2, BASE_Y + y, 2), y == 0 ? CEBlocks.REBOILER.getDefaultState()
				: y == 3 ? CEBlocks.CONDENSER.getDefaultState() : CEBlocks.COLUMN_SECTION.getDefaultState());
		BlockPos old = new BlockPos(2, BASE_Y, 2);
		BlockPos corner = new BlockPos(1, BASE_Y, 1);
		helper.runAfterDelay(40, () -> {
			if (!port(helper, old).isController())
				helper.fail("the lone column should be formed and running by now");
			helper.getLevel()
				.getCapability(Capabilities.ItemHandler.BLOCK, helper.absolutePos(old), Direction.UP)
				.insertItem(0, new ItemStack(Items.COAL, 5), false);
			for (int x = 1; x <= 2; x++)
				for (int z = 1; z <= 2; z++)
					if (x != 2 || z != 2)
						for (int y = 0; y < 4; y++)
							helper.setBlock(new BlockPos(x, BASE_Y + y, z), y == 0 ? CEBlocks.REBOILER.getDefaultState()
								: y == 3 ? CEBlocks.CONDENSER.getDefaultState() : CEBlocks.COLUMN_SECTION.getDefaultState());
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(port(helper, corner).isController(), "the corner Reboiler should now run the column");
			helper.assertTrue(port(helper, corner).catalystStack()
				.getCount() == 5, "the controller should hold the 5 coal, it holds " + port(helper, corner).catalystStack());
			helper.assertTrue(port(helper, old).catalystStack()
				.isEmpty(), "the old Reboiler should have none left");
		});
	}

	@GameTest(template = "empty", timeoutTicks = 200)
	public static void handingCatalystToAReboilerLoadsItAndAFullSlotRefusesMore(GameTestHelper helper) {
		build(helper, 1, BlazeBurnerBlock.HeatLevel.KINDLED);
		BlockPos reboiler = new BlockPos(1, BASE_Y, 1);
		helper.runAfterDelay(15, () -> {
			var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
			player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.COAL, 10));
			helper.useBlock(reboiler, player);
			helper.assertTrue(port(helper, reboiler).catalystStack()
				.getCount() == 10, "the 10 coal should be in the slot");
			helper.assertTrue(player.getMainHandItem()
				.isEmpty(), "and out of the hand");
			player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
				new ItemStack(Items.COAL, ColumnPortBlockEntity.CATALYST_SLOT));
			helper.useBlock(reboiler, player);
			helper.assertTrue(port(helper, reboiler).catalystStack()
				.getCount() == ColumnPortBlockEntity.CATALYST_SLOT, "the slot fills to its limit");
			helper.assertTrue(player.getMainHandItem()
				.getCount() == 10, "what did not fit stays in the hand, it holds " + player.getMainHandItem());
			helper.succeed();
		});
	}
}
