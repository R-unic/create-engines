package com.createengines.gametest;

import java.util.List;

import com.createengines.CreateEngines;
import com.createengines.content.WearService;
import com.createengines.engine.sim.Wear;
import com.createengines.registry.CEItems;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.content.kinetics.deployer.DeployerBlock;
import com.simibubi.create.content.kinetics.deployer.DeployerBlockEntity;
import com.simibubi.create.content.kinetics.deployer.ItemApplicationRecipe;
import com.simibubi.create.content.logistics.depot.DepotBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.recipe.RecipeApplier;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * A Create Deployer holding a Service Kit sets a lightly worn part right on a belt, a depot or in a basin (see {@code WearRepairRecipes}):
 * first what recipe the Deployer is offered for what, then a powered Deployer over a depot doing it for real. A depot and a belt hand a
 * Deployer an item through the same processing behaviour, so the depot run covers the belt's path.
 */
@GameTestHolder(CreateEngines.MOD_ID)
@PrefixGameTestTemplate(false)
public class WearRepairGameTests {

	private static final String BATCH = "wear-belt";
	private static final BlockPos DEPLOYER = new BlockPos(4, 3, 3);
	private static final BlockPos DEPOT = new BlockPos(4, 1, 3);

	private static DeployerBlockEntity placeDeployer(GameTestHelper helper) {
		helper.setBlock(DEPLOYER, AllBlocks.DEPLOYER.getDefaultState()
			.setValue(DeployerBlock.FACING, Direction.DOWN));
		return helper.getBlockEntity(DEPLOYER);
	}

	/** Gives the Deployer an item to hold through its own item handler, as an arm or a funnel would; it needs a tick to have its fake player. */
	private static void hold(GameTestHelper helper, DeployerBlockEntity deployer, ItemStack stack) {
		var handler = helper.getLevel()
			.getCapability(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, helper.absolutePos(DEPLOYER), null);
		helper.assertTrue(handler != null, "the Deployer has an item handler");
		// Empty what it holds first, so one test can change the tool
		for (int slot = 0; slot < handler.getSlots(); slot++)
			handler.extractItem(slot, 64, false);
		ItemStack left = handler.insertItem(0, stack, false);
		helper.assertTrue(left.isEmpty(), "the Deployer took the " + stack + ": " + left);
	}

	/** What the Deployer is holding: its fake player's main hand. */
	private static ItemStack held(DeployerBlockEntity deployer) {
		return deployer.getPlayer()
			.getMainHandItem();
	}

	@GameTest(template = "empty", batch = BATCH)
	public static void aDeployerHoldingAKitIsOfferedARepairForALightlyWornPartOnly(GameTestHelper helper) {
		DeployerBlockEntity deployer = placeDeployer(helper);
		helper.runAfterDelay(5, () -> offered(helper, deployer));
	}

	private static void offered(GameTestHelper helper, DeployerBlockEntity deployer) {
		hold(helper, deployer, new ItemStack(CEItems.SERVICE_KIT.get()));

		ItemStack worn = WearService.withWear(CEItems.PISTON.asStack(), 0.3);
		RecipeHolder<?> offered = deployer.getRecipe(worn);
		helper.assertTrue(offered != null && offered.value() instanceof ItemApplicationRecipe, "A lightly worn piston is offered a repair: " + offered);
		ItemApplicationRecipe recipe = (ItemApplicationRecipe) offered.value();
		helper.assertTrue(!recipe.shouldKeepHeldItem(), "that spends a use of the kit's durability (Create leaves a kept tool alone)");
		List<ItemStack> out = RecipeApplier.applyRecipeOn(helper.getLevel(), worn, recipe, true);
		helper.assertTrue(out.size() == 1 && CEItems.PISTON.isIn(out.get(0)) && Math.abs(WearService.wearOf(out.get(0)) - Wear.REPAIRED_TO) < 1e-9,
			"and the result is the same piston at the repaired wear: " + out);

		helper.assertTrue(deployer.getRecipe(CEItems.PISTON.asStack()) == null, "A new part is offered nothing");
		helper.assertTrue(deployer.getRecipe(WearService.withWear(CEItems.PISTON.asStack(), 0.7)) == null, "nor is a badly worn one");
		ItemStack two = WearService.withWear(new ItemStack(CEItems.PISTON.get(), 2), 0.3);
		helper.assertTrue(deployer.getRecipe(two) == null, "nor a stack of two, which one use of the kit would otherwise repair at once");
		helper.assertTrue(deployer.getRecipe(WearService.withWear(CEItems.COPPER_GASKET.asStack(), 0.2)) != null, "A gasket is repaired the same way");
		helper.assertTrue(deployer.getRecipe(new ItemStack(Items.IRON_INGOT)) == null, "and an ordinary item is not touched");

		hold(helper, deployer, new ItemStack(Items.STICK));
		helper.assertTrue(deployer.getRecipe(worn) == null, "A Deployer holding anything but a kit offers no repair");
		helper.succeed();
	}

	@GameTest(template = "empty", batch = BATCH, timeoutTicks = 600)
	public static void aPoweredDeployerSetsAWornPartOnADepotRightAndSpendsAUseOfTheKit(GameTestHelper helper) {
		helper.setBlock(DEPOT, AllBlocks.DEPOT.getDefaultState());
		DeployerBlockEntity deployer = placeDeployer(helper);
		DepotBehaviour depot = BlockEntityBehaviour.get(helper.getBlockEntity(DEPOT), DepotBehaviour.TYPE);
		helper.runAfterDelay(5, () -> {
			hold(helper, deployer, new ItemStack(CEItems.SERVICE_KIT.get()));
			depot.setHeldItem(new TransportedItemStack(WearService.withWear(CEItems.PISTON.asStack(), 0.3)));
			deployer.setSpeed(32);
		});

		helper.runAtTickTime(500, () -> {
			ItemStack result = depot.getHeldItemStack();
			helper.assertTrue(CEItems.PISTON.isIn(result), "The piston is still on the depot: " + result);
			helper.assertTrue(Math.abs(WearService.wearOf(result) - Wear.REPAIRED_TO) < 1e-9, "set right, to " + WearService.wearOf(result));
			ItemStack kit = held(deployer);
			helper.assertTrue(CEItems.SERVICE_KIT.isIn(kit) && kit.getDamageValue() == 1, "and the Deployer still holds the kit, with one use spent: " + kit + " " + kit.getDamageValue());
			helper.succeed();
		});
	}
}
