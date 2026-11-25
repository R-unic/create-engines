package com.createengines.gametest;

import java.util.List;

import com.createengines.CreateEngines;
import com.createengines.content.WearRepairRecipes;
import com.createengines.content.WearService;
import com.createengines.content.WornPartItem;
import com.createengines.content.battery.BatteryBlockEntity;
import com.createengines.content.cylinder.CylinderBlock;
import com.createengines.content.cylinder.CylinderBlockEntity;
import com.createengines.content.head.CylinderHeadBlockEntity;
import com.createengines.content.rotor.RotorHousingBlockEntity;
import com.createengines.content.tool.BankBuilderItem;
import com.createengines.engine.WearKeeper;
import com.createengines.engine.sim.Wear;
import com.createengines.engine.sim.WearFailures;
import com.createengines.engine.topology.CylinderFault;
import com.createengines.registry.CEBlocks;
import com.createengines.registry.CEItems;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The awkward corners of what a player does about wear: stacks, creative mode, the other hand, parts that are already as new, parts
 * knocked out by breaking the block, and calls repeated on a part that has already failed. Everything happens within one tick, so no
 * engine runs and nothing wears while a test looks.
 */
@GameTestHolder(CreateEngines.MOD_ID)
@PrefixGameTestTemplate(false)
public class WearHandEdgeGameTests {

	private static final String BATCH = "wear-hand-edges";
	private static final BlockPos AT = new BlockPos(4, 2, 3);

	@AfterBatch(batch = BATCH)
	public static void restore(ServerLevel level) {
		GameTestConfig.wearMode(false);
	}

	private static Player player(GameTestHelper helper, GameType mode, ItemStack main, ItemStack off) {
		Player player = helper.makeMockPlayer(mode);
		player.setItemInHand(InteractionHand.MAIN_HAND, main);
		player.setItemInHand(InteractionHand.OFF_HAND, off);
		return player;
	}

	private static ItemStack kit() {
		return new ItemStack(CEItems.SERVICE_KIT.get());
	}

	private static ItemStack wornPistons(int count, double wear) {
		return WearService.withWear(new ItemStack(CEItems.PISTON.get(), count), wear);
	}

	private static boolean about(double a, double b) {
		return Math.abs(a - b) < 1e-9;
	}

	/** How many items in {@code player}'s inventory are pistons worn exactly this much. */
	private static int pistonsAt(Player player, double wear) {
		int count = 0;
		for (int slot = 0; slot < player.getInventory()
			.getContainerSize(); slot++) {
			ItemStack stack = player.getInventory()
				.getItem(slot);
			if (CEItems.PISTON.isIn(stack) && about(WearService.wearOf(stack), wear))
				count += stack.getCount();
		}
		return count;
	}

	@GameTest(template = "empty", batch = BATCH)
	public static void aKitRepairsExactlyOneOfAStackOfWornPartsForOneUse(GameTestHelper helper) {
		GameTestConfig.wearMode(true);
		ItemStack kit = kit();
		Player player = player(helper, GameType.SURVIVAL, kit, wornPistons(5, 0.3));
		helper.assertTrue(WearService.repairOtherHand(kit, player, InteractionHand.MAIN_HAND), "it repairs");
		helper.assertTrue(pistonsAt(player, Wear.REPAIRED_TO) == 1, "one repaired: " + pistonsAt(player, Wear.REPAIRED_TO));
		helper.assertTrue(pistonsAt(player, 0.3) == 4, "four as they were: " + pistonsAt(player, 0.3));
		helper.assertTrue(kit.getDamageValue() == 1, "one use: " + kit.getDamageValue());
		helper.succeed();
	}

	@GameTest(template = "empty", batch = BATCH)
	public static void aKitInTheOffHandRepairsThePartInTheMainHand(GameTestHelper helper) {
		GameTestConfig.wearMode(true);
		ItemStack kit = kit();
		Player player = player(helper, GameType.SURVIVAL, wornPistons(1, 0.2), kit);
		helper.assertTrue(WearService.repairOtherHand(kit, player, InteractionHand.OFF_HAND), "it repairs");
		helper.assertTrue(about(WearService.wearOf(player.getMainHandItem()), Wear.REPAIRED_TO), "in the hand it was in");
		helper.assertTrue(kit.getDamageValue() == 1, "one use");
		helper.succeed();
	}

	@GameTest(template = "empty", batch = BATCH)
	public static void aKitLeavesAloneWhatItCannotRepairAndKeepsItsUses(GameTestHelper helper) {
		GameTestConfig.wearMode(true);
		ItemStack[] others = {ItemStack.EMPTY, CEItems.PISTON.asStack(), wornPistons(1, Wear.REPAIRED_TO),
			wornPistons(1, Math.nextUp(Wear.REPAIRABLE)), wornPistons(1, 1), new ItemStack(Items.IRON_PICKAXE)};
		for (ItemStack other : others) {
			ItemStack kit = kit();
			ItemStack was = other.copy();
			Player player = player(helper, GameType.SURVIVAL, kit, other);
			helper.assertTrue(!WearService.repairOtherHand(kit, player, InteractionHand.MAIN_HAND), "nothing to do with " + was);
			helper.assertTrue(kit.getDamageValue() == 0, "no use spent on " + was);
			helper.assertTrue(ItemStack.matches(was, player.getOffhandItem()), was + " became " + player.getOffhandItem());
		}
		helper.succeed();
	}

	@GameTest(template = "empty", batch = BATCH)
	public static void aCreativePlayersKitNeverWearsAndTheirGasketIsNotUsedUp(GameTestHelper helper) {
		GameTestConfig.wearMode(true);
		ItemStack kit = kit();
		Player creative = player(helper, GameType.CREATIVE, kit, wornPistons(1, 0.3));
		helper.assertTrue(WearService.repairOtherHand(kit, creative, InteractionHand.MAIN_HAND), "it repairs");
		helper.assertTrue(kit.getDamageValue() == 0, "and costs nothing");

		TestEngine.build(helper.getLevel(), helper.absolutePos(BlockPos.ZERO), false);
		ItemStack gasket = new ItemStack(CEItems.COPPER_GASKET.get(), 3);
		helper.useBlock(TestEngine.HEAD, player(helper, GameType.CREATIVE, gasket, ItemStack.EMPTY));
		CylinderHeadBlockEntity head = helper.getBlockEntity(TestEngine.HEAD);
		helper.assertTrue(head.gasket() == Wear.Gasket.COPPER && gasket.getCount() == 3, "fitted, and the stack kept: " + gasket);
		helper.succeed();
	}

	@GameTest(template = "empty", batch = BATCH)
	public static void everyGasketTierIsItsOwnItemAndNothingElseIsAGasket(GameTestHelper helper) {
		for (Wear.Gasket tier : Wear.Gasket.values()) {
			ItemStack item = WearService.itemFor(tier);
			helper.assertTrue(WearService.gasketOf(item) == tier, tier + " read as " + WearService.gasketOf(item));
			helper.assertTrue(item.getItem() instanceof WornPartItem, tier + " wears");
		}
		for (ItemStack other : new ItemStack[] {ItemStack.EMPTY, CEItems.PISTON.asStack(), kit(), new ItemStack(Items.PAPER),
			CEItems.APEX_SEALS.asStack()})
			helper.assertTrue(WearService.gasketOf(other) == null, other + " is no gasket");
		helper.succeed();
	}

	@GameTest(template = "empty", batch = BATCH)
	public static void anyWearWrittenOnAStackReadsBackAsAShare(GameTestHelper helper) {
		double[] written = {Double.NaN, Double.NEGATIVE_INFINITY, -3, -0.0, 0, Double.MIN_VALUE, 0.5, 1, 1.0000001, 99,
			Double.POSITIVE_INFINITY};
		for (double wear : written) {
			ItemStack stack = WearService.withWear(CEItems.PISTON.asStack(), wear);
			double read = WearService.wearOf(stack);
			helper.assertTrue(read >= 0 && read <= 1, wear + " reads " + read);
			if (read == 0)
				helper.assertTrue(ItemStack.isSameItemSameComponents(stack, CEItems.PISTON.asStack()), wear + " left data on a new part");
			var access = helper.getLevel()
				.registryAccess();
			ItemStack saved = ItemStack.parse(access, stack.save(access))
				.orElseThrow();
			helper.assertTrue(ItemStack.matches(stack, saved), wear + " changed across a save: " + WearService.wearOf(saved));
		}
		helper.succeed();
	}

	@GameTest(template = "empty", batch = BATCH)
	public static void theWearBarRunsFromFullToEmptyAndNeverOutsideTheSlot(GameTestHelper helper) {
		GameTestConfig.wearMode(true);
		int last = 13;
		for (int i = 1; i <= 100; i++) {
			ItemStack part = wornPistons(1, i / 100.0);
			int width = part.getBarWidth();
			helper.assertTrue(width >= 0 && width <= last, "width " + width + " at " + i);
			helper.assertTrue(part.isBarVisible(), "bar shown at " + i);
			part.getBarColor();
			last = width;
		}
		helper.assertTrue(last == 0, "empty when spent");
		helper.assertTrue(!CEItems.PISTON.asStack()
			.isBarVisible(), "a new part has no bar");
		helper.succeed();
	}

	@GameTest(template = "empty", batch = BATCH)
	public static void theDeployerRepairIsOfferedExactlyInsideTheRepairBand(GameTestHelper helper) {
		ItemStack tool = kit();
		helper.assertTrue(!WearRepairRecipes.repairs(wornPistons(1, Wear.REPAIRED_TO), tool), "nothing at the repaired level");
		helper.assertTrue(WearRepairRecipes.repairs(wornPistons(1, Math.nextUp(Wear.REPAIRED_TO)), tool), "just above it");
		helper.assertTrue(WearRepairRecipes.repairs(wornPistons(1, Wear.REPAIRABLE), tool), "at the limit");
		helper.assertTrue(!WearRepairRecipes.repairs(wornPistons(1, Math.nextUp(Wear.REPAIRABLE)), tool), "just past it");
		helper.assertTrue(!WearRepairRecipes.repairs(wornPistons(2, 0.3), tool), "never two at once");
		helper.assertTrue(!WearRepairRecipes.repairs(ItemStack.EMPTY, tool), "nor nothing");
		helper.assertTrue(!WearRepairRecipes.repairs(wornPistons(1, 0.3), CEItems.PISTON.asStack()), "nor without a kit");
		ItemStack worn = wornPistons(1, 0.3);
		WearRepairRecipes.repair(worn, tool);
		helper.assertTrue(about(WearService.wearOf(worn), 0.3) && tool.getDamageValue() == 0, "offering a recipe changes nothing");
		helper.succeed();
	}

	/** Breaking the cylinder must not be a free repair: the piston and rod that fall out are as worn as they were inside. */
	@GameTest(template = "empty", batch = BATCH)
	public static void breakingACylinderDropsItsPartsAsWornAsTheyWere(GameTestHelper helper) {
		GameTestConfig.wearMode(true);
		helper.setBlock(AT, CEBlocks.CYLINDER.getDefaultState()
			.setValue(CylinderBlock.HAS_PISTON, true)
			.setValue(CylinderBlock.FORGED_PISTON, true)
			.setValue(CylinderBlock.HAS_ROD, true));
		CylinderBlockEntity body = helper.getBlockEntity(AT);
		body.fitPiston(0.4);
		body.fitRod(0.7);
		helper.getLevel()
			.destroyBlock(helper.absolutePos(AT), false);
		List<ItemEntity> dropped = helper.getLevel()
			.getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(AT)).inflate(2));
		double piston = -1, rod = -1;
		for (ItemEntity entity : dropped) {
			ItemStack stack = entity.getItem();
			if (CEItems.FORGED_PISTON.isIn(stack))
				piston = WearService.wearOf(stack);
			if (CEItems.CONNECTING_ROD.isIn(stack))
				rod = WearService.wearOf(stack);
			entity.discard();
		}
		helper.assertTrue(about(piston, 0.4), "forged piston dropped at " + piston);
		helper.assertTrue(about(rod, 0.7), "rod dropped at " + rod);
		helper.succeed();
	}

	@GameTest(template = "empty", batch = BATCH)
	public static void wearAddedInAnyAmountStaysAShareAndClientsAreToldOncePerStep(GameTestHelper helper) {
		helper.setBlock(AT, CEBlocks.CYLINDER.getDefaultState()
			.setValue(CylinderBlock.HAS_PISTON, true)
			.setValue(CylinderBlock.HAS_ROD, true));
		CylinderBlockEntity body = helper.getBlockEntity(AT);
		int told = 0;
		for (int i = 0; i < 10000; i++)
			if (body.addWear(1e-4, 0))
				told++;
		helper.assertTrue(told >= 19 && told <= 21, "a slowly wearing piston told clients " + told + " times");
		body.addWear(50, 50);
		helper.assertTrue(body.pistonWear() == 1 && body.rodWear() == 1, "held at spent");
		helper.assertTrue(!body.addWear(50, 50), "a spent part has nothing new to tell");
		body.addWear(-50, -50);
		helper.assertTrue(body.pistonWear() == 0 && body.rodWear() == 0, "held at new");
		helper.succeed();
	}

	@GameTest(template = "empty", batch = BATCH)
	public static void failingAnAlreadyFailedPartAgainChangesNothing(GameTestHelper helper) {
		GameTestConfig.wearMode(true);
		TestEngine.build(helper.getLevel(), helper.absolutePos(BlockPos.ZERO), false);
		CylinderBlockEntity cylinder = helper.getBlockEntity(TestEngine.CYLINDER);
		CylinderHeadBlockEntity head = helper.getBlockEntity(TestEngine.HEAD);
		helper.assertTrue(!cylinder.unseize(), "nothing seized to free");
		cylinder.seize();
		cylinder.setDryTime(40);
		cylinder.seize();
		helper.assertTrue(cylinder.dryTime() == 40, "a second seizure does not restart the count");
		helper.assertTrue(cylinder.pistonWear() >= Wear.SEIZED_WEAR, "scored");
		helper.assertTrue(!cylinder.service(), "a kit cannot free it in place");
		helper.assertTrue(cylinder.unseize() && !cylinder.unseize(), "freed once");

		head.blow();
		head.blow();
		helper.assertTrue(head.blown() && !head.service(), "a blown gasket is not serviced");
		head.breakHead();
		helper.assertTrue(head.broken() && !head.blown(), "breaking takes the gasket with it");
		head.breakHead();
		helper.assertTrue(head.broken(), "still broken");
		helper.succeed();
	}

	@GameTest(template = "empty", batch = BATCH)
	public static void aGasketTakenOutTwiceOnlyComesOutOnce(GameTestHelper helper) {
		GameTestConfig.wearMode(true);
		TestEngine.build(helper.getLevel(), helper.absolutePos(BlockPos.ZERO), false);
		CylinderHeadBlockEntity head = helper.getBlockEntity(TestEngine.HEAD);
		helper.assertTrue(head.removeGasket()
			.isEmpty(), "a head with none fitted gives none");
		head.fit(Wear.Gasket.RUBBER, 0.25);
		ItemStack out = head.removeGasket();
		helper.assertTrue(WearService.gasketOf(out) == Wear.Gasket.RUBBER && about(WearService.wearOf(out), 0.25), "out as it was: " + out);
		helper.assertTrue(head.removeGasket()
			.isEmpty(), "and only once: no second gasket out of an empty head");
		helper.succeed();
	}

	@GameTest(template = "empty", batch = BATCH)
	public static void fittingTheSameWornGasketAgainDoesNotUseOneUp(GameTestHelper helper) {
		GameTestConfig.wearMode(true);
		TestEngine.build(helper.getLevel(), helper.absolutePos(BlockPos.ZERO), false);
		CylinderHeadBlockEntity head = helper.getBlockEntity(TestEngine.HEAD);
		head.fit(Wear.Gasket.RUBBER, 0.2);
		for (double offered : new double[] {0.2, 0.6, 1}) {
			ItemStack worse = WearService.withWear(CEItems.RUBBER_GASKET.asStack(), offered);
			helper.useBlock(TestEngine.HEAD, player(helper, GameType.SURVIVAL, worse, ItemStack.EMPTY));
			helper.assertTrue(worse.getCount() == 1 && about(head.gasketWear(), 0.2), "no swap for one worn " + offered);
		}
		helper.succeed();
	}

	@GameTest(template = "empty", batch = BATCH)
	public static void apexSealsAreOnlyUsedUpWhenThereIsSomethingToReplace(GameTestHelper helper) {
		GameTestConfig.wearMode(true);
		TestEngine.buildRotary(helper.getLevel(), helper.absolutePos(BlockPos.ZERO), true);
		RotorHousingBlockEntity housing = helper.getBlockEntity(TestEngine.HOUSING);
		ItemStack seals = CEItems.APEX_SEALS.asStack(2);
		Player player = player(helper, GameType.SURVIVAL, seals, ItemStack.EMPTY);
		helper.useBlock(TestEngine.HOUSING, player);
		helper.assertTrue(player.getMainHandItem()
			.getCount() == 2, "new seals stay in the hand");
		ItemStack kit = kit();
		helper.useBlock(TestEngine.HOUSING, player(helper, GameType.SURVIVAL, kit, ItemStack.EMPTY));
		helper.assertTrue(kit.getDamageValue() == 0, "and a kit finds nothing to do");
		housing.failApex();
		helper.assertTrue(!housing.service(), "failed seals are not serviced");
		helper.useBlock(TestEngine.HOUSING, player);
		helper.assertTrue(player.getMainHandItem()
			.getCount() == 1 && !housing.apexFailed(), "failed ones are replaced for one set");
		helper.succeed();
	}

	@GameTest(template = "empty", batch = BATCH)
	public static void aBatteryKitUseIsSpentOnlyOnLightWear(GameTestHelper helper) {
		GameTestConfig.wearMode(true);
		helper.setBlock(AT, CEBlocks.BATTERY.getDefaultState());
		BatteryBlockEntity battery = helper.getBlockEntity(AT);
		var access = helper.getLevel()
			.registryAccess();
		double[] saved = {0, Wear.REPAIRED_TO, 0.3, 0.9, Double.NaN, -4, 40};
		for (double wear : saved) {
			CompoundTag tag = battery.saveWithoutMetadata(access);
			tag.putDouble("Wear", wear);
			battery.loadWithComponents(tag, access);
			double before = battery.wear();
			helper.assertTrue(before >= 0 && before <= 1, "saved " + wear + " read " + before);
			helper.assertTrue(battery.charge() <= battery.capacity() + 1e-3 || before > 0, "charge within what it holds");
			ItemStack kit = kit();
			helper.useBlock(AT, player(helper, GameType.SURVIVAL, kit, ItemStack.EMPTY));
			boolean repairable = Wear.repairable(before);
			helper.assertTrue(kit.getDamageValue() == (repairable ? 1 : 0), "uses spent at " + before + ": " + kit.getDamageValue());
			helper.assertTrue(about(battery.wear(), repairable ? Wear.REPAIRED_TO : before), "left at " + battery.wear());
		}
		helper.succeed();
	}

	/** Breaks the head with the mode off and returns the tier of the gasket item that came out, or null for none; the drops are cleared. */
	private static Wear.Gasket breakOutGasket(GameTestHelper helper, BlockPos at) {
		helper.getLevel()
			.destroyBlock(helper.absolutePos(at), false);
		Wear.Gasket found = null;
		int gaskets = 0;
		for (ItemEntity entity : helper.getLevel()
			.getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(at)).inflate(2))) {
			Wear.Gasket tier = WearService.gasketOf(entity.getItem());
			if (tier != null) {
				found = tier;
				gaskets += entity.getItem()
					.getCount();
			}
			entity.discard();
		}
		helper.assertTrue(gaskets <= 1, "more than one gasket came out: " + gaskets);
		return found;
	}

	@GameTest(template = "empty", batch = BATCH)
	public static void aGasketFittedOverAHiddenBreakIsStillThereWhenTheModeComesBack(GameTestHelper helper) {
		GameTestConfig.wearMode(true);
		TestEngine.build(helper.getLevel(), helper.absolutePos(BlockPos.ZERO), false);
		CylinderHeadBlockEntity head = helper.getBlockEntity(TestEngine.HEAD);
		head.breakHead();
		GameTestConfig.wearMode(false);
		helper.useBlock(TestEngine.HEAD, player(helper, GameType.SURVIVAL, CEItems.RUBBER_GASKET.asStack(), ItemStack.EMPTY));
		GameTestConfig.wearMode(true);
		helper.assertTrue(!head.broken() && head.gasket() == Wear.Gasket.RUBBER, "the head the gasket went into is whole");
		Wear.Gasket out = breakOutGasket(helper, TestEngine.HEAD);
		helper.assertTrue(out == Wear.Gasket.RUBBER, "breaking it out should drop the gasket, dropped " + out);
		helper.succeed();
	}

	@GameTest(template = "empty", batch = BATCH)
	public static void aStockGasketLostToARodDoesNotComeBackWithTheModeOff(GameTestHelper helper) {
		GameTestConfig.wearMode(true);
		TestEngine.build(helper.getLevel(), helper.absolutePos(BlockPos.ZERO), false);
		CylinderHeadBlockEntity head = helper.getBlockEntity(TestEngine.HEAD);
		head.fit(Wear.Gasket.STOCK, 0.4);
		head.breakHead();
		GameTestConfig.wearMode(false);
		helper.assertTrue(!head.hasGasketItem() && head.gasketStack()
			.isEmpty(), "the rod took the gasket: " + head.gasketStack());
		Wear.Gasket out = breakOutGasket(helper, TestEngine.HEAD);
		helper.assertTrue(out == null, "no gasket comes out of the wreck, dropped " + out);
		helper.succeed();
	}

	@GameTest(template = "empty", batch = BATCH)
	public static void theBankBuilderGasketingAHiddenBreakMakesTheHeadWhole(GameTestHelper helper) {
		GameTestConfig.wearMode(true);
		TestEngine.build(helper.getLevel(), helper.absolutePos(BlockPos.ZERO), false);
		CylinderHeadBlockEntity head = helper.getBlockEntity(TestEngine.HEAD);
		head.breakHead();
		GameTestConfig.wearMode(false);
		Player builder = player(helper, GameType.SURVIVAL, ItemStack.EMPTY, ItemStack.EMPTY);
		builder.getInventory()
			.add(CEItems.COPPER_GASKET.asStack());
		BankBuilderItem.build(helper.getLevel(), helper.absolutePos(TestEngine.THROW), Direction.UP, BankBuilderItem.Layout.INLINE,
			builder, false);
		GameTestConfig.wearMode(true);
		helper.assertTrue(head.gasket() == Wear.Gasket.COPPER && !head.broken(), "gasketed and whole: " + head.gasket() + " " + head.broken());
		helper.succeed();
	}

	/** Dry past the first warning, which is half way to seizing, then the piston is swapped by hand: the new one starts afresh. */
	@GameTest(template = "empty", batch = BATCH)
	public static void aPistonSwappedByHandDoesNotInheritTheOldOnesDryTime(GameTestHelper helper) {
		GameTestConfig.wearMode(true);
		helper.setBlock(AT, CEBlocks.CYLINDER.getDefaultState()
			.setValue(CylinderBlock.HAS_PISTON, true)
			.setValue(CylinderBlock.HAS_ROD, true));
		CylinderBlockEntity body = helper.getBlockEntity(AT);
		body.setDryTime(0.9 * Wear.SEIZE_AFTER);
		Player hand = player(helper, GameType.SURVIVAL, ItemStack.EMPTY, ItemStack.EMPTY);
		// Parts come out to a sneaking empty hand: the rod first, then the piston
		hand.setShiftKeyDown(true);
		for (int take = 0; take < 2; take++) {
			hand.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			helper.useBlock(AT, hand);
		}
		helper.assertTrue(!helper.getBlockState(AT)
			.getValue(CylinderBlock.HAS_PISTON), "the piston is out");
		helper.assertTrue(body.dryTime() == 0, "its dry time goes with it: " + body.dryTime());
		body.setDryTime(0.9 * Wear.SEIZE_AFTER);
		helper.useBlock(AT, player(helper, GameType.SURVIVAL, CEItems.PISTON.asStack(), ItemStack.EMPTY));
		helper.assertTrue(helper.getBlockState(AT)
			.getValue(CylinderBlock.HAS_PISTON) && body.dryTime() == 0, "a new piston starts afresh: " + body.dryTime());
		helper.succeed();
	}

	@GameTest(template = "empty", batch = BATCH)
	public static void theBankBuildersForgedSwapKeepsTheOldPistonsWearOnThePistonItGivesBack(GameTestHelper helper) {
		GameTestConfig.wearMode(true);
		TestEngine.build(helper.getLevel(), helper.absolutePos(BlockPos.ZERO), false);
		CylinderBlockEntity body = helper.getBlockEntity(TestEngine.CYLINDER);
		body.fitPiston(0.6);
		body.setDryTime(0.9 * Wear.SEIZE_AFTER);
		Player builder = player(helper, GameType.SURVIVAL, ItemStack.EMPTY, ItemStack.EMPTY);
		builder.getInventory()
			.add(CEItems.FORGED_PISTON.asStack());
		BankBuilderItem.build(helper.getLevel(), helper.absolutePos(TestEngine.THROW), Direction.UP, BankBuilderItem.Layout.INLINE, true,
			builder, false);
		helper.assertTrue(helper.getBlockState(TestEngine.CYLINDER)
			.getValue(CylinderBlock.FORGED_PISTON), "the forged piston is in");
		helper.assertTrue(body.pistonWear() == 0 && body.dryTime() == 0,
			"it is new and has not run dry: " + body.pistonWear() + " " + body.dryTime());
		helper.assertTrue(pistonsAt(builder, 0.6) == 1, "the old piston comes back as worn as it was");
		helper.succeed();
	}

	@GameTest(template = "empty", batch = BATCH)
	public static void aRodThroughTheHeadCountsAsOneFailureNotTwo(GameTestHelper helper) {
		BlockPos a = BlockPos.ZERO, b = a.above(), c = b.above();
		int packed = WearKeeper.failureCounts(java.util.Map.of(
			a, CylinderFault.THROWN_ROD.bit() | CylinderFault.BLOWN_GASKET.bit(),
			b, CylinderFault.BLOWN_GASKET.bit() | CylinderFault.SEIZED.bit(),
			c, CylinderFault.APEX_FAILED.bit() | CylinderFault.NO_PISTON.bit()));
		helper.assertTrue(WearFailures.count(packed, WearFailures.Kind.ROD_THROWN) == 1, "one rod");
		helper.assertTrue(WearFailures.count(packed, WearFailures.Kind.GASKET_BLOWN) == 1, "one gasket, the other went with the rod");
		helper.assertTrue(WearFailures.count(packed, WearFailures.Kind.PISTON_SEIZED) == 1, "one seizure");
		helper.assertTrue(WearFailures.count(packed, WearFailures.Kind.APEX_FAILED) == 1, "one set of seals");
		helper.assertTrue(WearKeeper.failureCounts(java.util.Map.of()) == 0, "nothing failed");
		helper.succeed();
	}
}
