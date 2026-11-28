package com.createengines.gametest;

import com.createengines.CreateEngines;
import com.createengines.content.CreativeService;
import com.createengines.content.WearRepairRecipes;
import com.createengines.content.WearService;
import com.createengines.content.battery.BatteryBlockEntity;
import com.createengines.content.cylinder.CylinderBlockEntity;
import com.createengines.content.head.CylinderHeadBlockEntity;
import com.createengines.content.rotor.RotorHousingBlockEntity;
import com.createengines.content.turbo.TurbochargerBlockEntity;
import com.createengines.engine.sim.Wear;
import com.createengines.registry.CEBlocks;
import com.createengines.registry.CEComponents;
import com.createengines.registry.CEItems;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The creative Service Kit: it mends any wear and any failure, costs nothing and has no recipe. Everything happens within one tick, so
 * nothing wears while a test looks.
 */
@GameTestHolder(CreateEngines.MOD_ID)
@PrefixGameTestTemplate(false)
public class WearCreativeKitGameTests {

	private static final String BATCH = "wear-creative-kit";
	private static final BlockPos AT = new BlockPos(4, 2, 3);

	@AfterBatch(batch = BATCH)
	public static void restore(ServerLevel level) {
		GameTestConfig.wearMode(false);
	}

	private static ItemStack kit() {
		return new ItemStack(CEItems.CREATIVE_SERVICE_KIT.get());
	}

	// A survival player on purpose: the kit must cost nothing by itself, not because creative mode spares it
	private static Player player(GameTestHelper helper, ItemStack main, ItemStack off) {
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		player.setItemInHand(InteractionHand.MAIN_HAND, main);
		player.setItemInHand(InteractionHand.OFF_HAND, off);
		return player;
	}

	private static void use(GameTestHelper helper, BlockPos at) {
		Player player = player(helper, kit(), ItemStack.EMPTY);
		helper.useBlock(at, player);
		ItemStack held = player.getMainHandItem();
		helper.assertTrue(CreativeService.isKit(held) && held.getCount() == 1 && !held.isDamageableItem(), "the kit is not used up: " + held);
	}

	@GameTest(template = "empty", batch = BATCH)
	public static void itMendsASpentOrSeizedCylinderAndAThrownRodsWreck(GameTestHelper helper) {
		GameTestConfig.wearMode(true);
		TestEngine.build(helper.getLevel(), helper.absolutePos(BlockPos.ZERO), false);
		CylinderBlockEntity cylinder = helper.getBlockEntity(TestEngine.CYLINDER);
		cylinder.addWear(1, 1);
		use(helper, TestEngine.CYLINDER);
		helper.assertTrue(cylinder.pistonWear() == 0 && cylinder.rodWear() == 0, "new: " + cylinder.pistonWear() + " " + cylinder.rodWear());
		cylinder.seize();
		use(helper, TestEngine.CYLINDER);
		helper.assertTrue(!cylinder.seized() && cylinder.pistonWear() == 0, "freed where it is, as new");
		cylinder.throwRod();
		use(helper, TestEngine.CYLINDER);
		helper.assertTrue(!cylinder.thrown(), "the wreck is cleared");
		helper.succeed();
	}

	@GameTest(template = "empty", batch = BATCH)
	public static void itMendsABlownBrokenAndWarpedHead(GameTestHelper helper) {
		GameTestConfig.wearMode(true);
		TestEngine.build(helper.getLevel(), helper.absolutePos(BlockPos.ZERO), false);
		CylinderHeadBlockEntity head = helper.getBlockEntity(TestEngine.HEAD);
		head.fit(Wear.Gasket.COPPER, 0.9);
		head.blow();
		head.warp();
		head.addPlugWear(0.8);
		use(helper, TestEngine.HEAD);
		helper.assertTrue(!head.blown() && !head.warped() && head.gasketWear() == 0 && head.plugWear() == 0, "all new");
		helper.assertTrue(head.gasket() == Wear.Gasket.COPPER, "and the same gasket: " + head.gasket());
		head.breakHead();
		use(helper, TestEngine.HEAD);
		helper.assertTrue(!head.broken(), "a broken head is whole again");
		helper.succeed();
	}

	@GameTest(template = "empty", batch = BATCH)
	public static void sneakingItOnAHeadStillTakesTheGasketOut(GameTestHelper helper) {
		GameTestConfig.wearMode(true);
		TestEngine.build(helper.getLevel(), helper.absolutePos(BlockPos.ZERO), false);
		CylinderHeadBlockEntity head = helper.getBlockEntity(TestEngine.HEAD);
		head.fit(Wear.Gasket.RUBBER, 0.5);
		Player player = player(helper, kit(), ItemStack.EMPTY);
		player.setShiftKeyDown(true);
		helper.useBlock(TestEngine.HEAD, player);
		helper.assertTrue(!head.hasGasketItem(), "out: " + head.gasketStack());
		helper.succeed();
	}

	@GameTest(template = "empty", batch = BATCH)
	public static void itMendsFailedApexSeals(GameTestHelper helper) {
		GameTestConfig.wearMode(true);
		TestEngine.buildRotary(helper.getLevel(), helper.absolutePos(BlockPos.ZERO), true);
		RotorHousingBlockEntity housing = helper.getBlockEntity(TestEngine.HOUSING);
		housing.addApexWear(1);
		housing.addPlugWear(0.9);
		housing.failApex();
		use(helper, TestEngine.HOUSING);
		helper.assertTrue(!housing.apexFailed() && housing.apexWear() == 0 && housing.plugWear() == 0, "all new");
		helper.succeed();
	}

	@GameTest(template = "empty", batch = BATCH)
	public static void itMendsAFailedTurboAndASpentBattery(GameTestHelper helper) {
		GameTestConfig.wearMode(true);
		var access = helper.getLevel()
			.registryAccess();
		helper.setBlock(AT, CEBlocks.TURBOCHARGER.getDefaultState());
		TurbochargerBlockEntity turbo = helper.getBlockEntity(AT);
		turbo.restore();
		CompoundTag tag = turbo.saveWithoutMetadata(access);
		tag.putDouble("Wear", 1);
		tag.putBoolean("WearFailed", true);
		turbo.loadWithComponents(tag, access);
		helper.assertTrue(turbo.failed(), "failed to start with");
		use(helper, AT);
		helper.assertTrue(!turbo.failed() && turbo.wear() == 0, "the turbo is new");

		helper.setBlock(AT, CEBlocks.BATTERY.getDefaultState());
		BatteryBlockEntity battery = helper.getBlockEntity(AT);
		tag = battery.saveWithoutMetadata(access);
		tag.putDouble("Wear", 0.9);
		battery.loadWithComponents(tag, access);
		use(helper, AT);
		helper.assertTrue(battery.wear() == 0, "the battery is new: " + battery.wear());
		helper.succeed();
	}

	@GameTest(template = "empty", batch = BATCH)
	public static void itMendsAWholeStackInTheOtherHand(GameTestHelper helper) {
		ItemStack pistons = WearService.withWear(new ItemStack(CEItems.PISTON.get(), 5), 1);
		pistons.set(CEComponents.FAILED.get(), true);
		ItemStack kit = kit();
		Player player = player(helper, kit, pistons);
		helper.assertTrue(CreativeService.mendOtherHand(player, InteractionHand.MAIN_HAND), "it mends");
		helper.assertTrue(player.getOffhandItem()
			.getCount() == 5 && ItemStack.isSameItemSameComponents(player.getOffhandItem(), CEItems.PISTON.asStack()),
			"five new pistons: " + player.getOffhandItem());
		helper.assertTrue(!CreativeService.mendOtherHand(player, InteractionHand.MAIN_HAND), "nothing left to do");
		helper.succeed();
	}

	@GameTest(template = "empty", batch = BATCH)
	public static void aDeployerIsOfferedAnyWornPartAndTheRecipeKeepsTheKit(GameTestHelper helper) {
		ItemStack worn = WearService.withWear(CEItems.PISTON.asStack(), 1);
		helper.assertTrue(WearRepairRecipes.repairs(worn, kit()), "spent parts too");
		helper.assertTrue(!WearRepairRecipes.repairs(CEItems.PISTON.asStack(), kit()), "not a new one");
		var recipe = WearRepairRecipes.repair(worn, kit())
			.value();
		helper.assertTrue(recipe.shouldKeepHeldItem(), "the kit stays with the Deployer");
		ItemStack out = recipe.getResultItem(helper.getLevel()
			.registryAccess());
		helper.assertTrue(WearService.isNew(out), "new: " + out);
		helper.succeed();
	}

	@GameTest(template = "empty", batch = BATCH)
	public static void nothingCraftsIt(GameTestHelper helper) {
		var level = helper.getLevel();
		boolean crafted = level.getRecipeManager()
			.getRecipes()
			.stream()
			.anyMatch(holder -> CreativeService.isKit(holder.value()
				.getResultItem(level.registryAccess())));
		helper.assertTrue(!crafted, "no recipe makes " + BuiltInRegistries.ITEM.getKey(CEItems.CREATIVE_SERVICE_KIT.get()));
		helper.succeed();
	}
}
