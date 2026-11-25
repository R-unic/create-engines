package com.createengines.gametest;

import com.createengines.CreateEngines;
import com.createengines.content.WearService;
import com.createengines.content.blackbox.BlackBoxBlockEntity;
import com.createengines.content.display.EngineReadout;
import com.createengines.registry.CEBlocks;
import com.createengines.registry.CEDisplaySources;
import com.createengines.content.crankshaft.CrankshaftBlockEntity;
import com.createengines.content.cylinder.CylinderBlock;
import com.createengines.content.cylinder.CylinderBlockEntity;
import com.createengines.content.head.CylinderHeadBlockEntity;
import com.createengines.engine.EngineState;
import com.createengines.engine.EngineSummary;
import com.createengines.engine.WearKeeper;
import com.createengines.engine.sim.Tuning;
import com.createengines.engine.sim.Wear;
import com.createengines.engine.topology.CylinderFault;
import com.createengines.registry.CEItems;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The opt-in wear mode on real blocks: nothing wears with it off, a running engine wears its pistons, rods and gaskets with it on, a
 * spent rod is thrown and a spent gasket blows, and what a player does about it (a Service Kit for light wear, a new part for the rest).
 * The roll a failure is decided by is forced in the failure tests, so none of them is a matter of luck.
 */
@GameTestHolder(CreateEngines.MOD_ID)
@PrefixGameTestTemplate(false)
public class WearGameTests {

	private static final String ON = "wear-on";
	private static final String OFF = "wear-off";
	private static final String PARTS = "wear-parts";
	// The film override is one setting for the whole server, so each test that sets it runs alone in its own batch, or it would run every other test dry too
	private static final String DRY = "wear-dry";
	private static final String OILED = "wear-oiled";

	private static final BlockPos PRIMARY = TestEngine.PRIMARY;
	private static final BlockPos CYLINDER = TestEngine.CYLINDER;
	private static final BlockPos HEAD = TestEngine.HEAD;

	private static void build(GameTestHelper helper) {
		TestEngine.build(helper.getLevel(), helper.absolutePos(BlockPos.ZERO), true);
	}

	private static CylinderBlockEntity cylinder(GameTestHelper helper) {
		return helper.getBlockEntity(CYLINDER);
	}

	private static CylinderHeadBlockEntity head(GameTestHelper helper) {
		return helper.getBlockEntity(HEAD);
	}

	private static EngineSummary summary(GameTestHelper helper) {
		CrankshaftBlockEntity crank = helper.getBlockEntity(PRIMARY);
		EngineSummary summary = crank.summary();
		if (summary == null)
			helper.fail("the crankshaft has no engine", PRIMARY);
		return summary;
	}

	private static Player player(GameTestHelper helper, ItemStack held) {
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		player.setItemInHand(InteractionHand.MAIN_HAND, held);
		return player;
	}

	@AfterBatch(batch = ON)
	public static void restoreTheModeAfterOn(ServerLevel level) {
		Tuning.wearMode = false;
		WearKeeper.roller = null;
		WearKeeper.filmOverride = null;
	}

	@AfterBatch(batch = DRY)
	public static void restoreTheModeAfterDry(ServerLevel level) {
		Tuning.wearMode = false;
		WearKeeper.filmOverride = null;
	}

	@AfterBatch(batch = OILED)
	public static void restoreTheModeAfterOiled(ServerLevel level) {
		Tuning.wearMode = false;
		WearKeeper.filmOverride = null;
	}

	@AfterBatch(batch = PARTS)
	public static void restoreTheModeAfterParts(ServerLevel level) {
		Tuning.wearMode = false;
	}

	// --- Off: nothing changes ---

	@GameTest(template = "empty", timeoutTicks = 500, batch = OFF)
	public static void withTheModeOffNothingWearsAndAFittedGasketDoesNotWear(GameTestHelper helper) {
		Tuning.wearMode = false;
		build(helper);
		head(helper).fit(Wear.Gasket.COPPER);
		helper.runAtTickTime(400, () -> {
			helper.assertTrue(summary(helper).state() != EngineState.STALLED, "The engine is turning: " + summary(helper));
			helper.assertTrue(cylinder(helper).pistonWear() == 0 && cylinder(helper).rodWear() == 0, "Nothing on the cylinder wore");
			helper.assertTrue(head(helper).gasketWear() == 0 && !head(helper).blown(), "and nor did the gasket");
			helper.succeed();
		});
	}

	// --- On: wear and failure ---

	@GameTest(template = "empty", timeoutTicks = 500, batch = ON)
	public static void withTheModeOnARunningEngineWearsItsParts(GameTestHelper helper) {
		Tuning.wearMode = true;
		build(helper);
		helper.runAtTickTime(450, () -> {
			helper.assertTrue(cylinder(helper).pistonWear() > 0, "The piston wore: " + cylinder(helper).pistonWear());
			helper.assertTrue(cylinder(helper).rodWear() > 0, "The rod wore: " + cylinder(helper).rodWear());
			helper.assertTrue(head(helper).gasketWear() > 0, "The gasket wore: " + head(helper).gasketWear());
			helper.assertTrue(cylinder(helper).pistonWear() < 0.01, "but a few seconds is nowhere near a life: " + cylinder(helper).pistonWear());
			helper.succeed();
		});
	}

	@GameTest(template = "empty", timeoutTicks = 600, batch = ON)
	public static void aSpentRodIsThrownLeavesItsWreckAndBreaksTheHead(GameTestHelper helper) {
		Tuning.wearMode = true;
		WearKeeper.roller = () -> 0;
		build(helper);
		cylinder(helper).addWear(0, 0.99);
		helper.runAtTickTime(500, () -> {
			var state = helper.getBlockState(CYLINDER);
			helper.assertTrue(!state.getValue(CylinderBlock.HAS_ROD) && !state.getValue(CylinderBlock.HAS_PISTON),
				"The rod went through, and the piston is lost with it: " + state);
			helper.assertTrue(cylinder(helper).thrown(), "The wreck is in the cylinder");
			helper.assertTrue(head(helper).broken(), "and the head is broken");
			helper.assertTrue(cylinder(helper).pistonWear() == 0 && cylinder(helper).rodWear() == 0, "A new part starts new");
			helper.assertTrue(summary(helper).firing() == 0, "The cylinder misfires: " + summary(helper));
			int faults = cylinder(helper).status().faults();
			helper.assertTrue(CylinderFault.THROWN_ROD.in(faults) && CylinderFault.BROKEN_HEAD.in(faults), "and says why: " + faults);
			WearKeeper.roller = null;
			helper.succeed();
		});
	}

	@GameTest(template = "empty", batch = PARTS)
	public static void aThrownRodsWreckIsClearedByAServiceKitAndNothingNewGoesInUntilThen(GameTestHelper helper) {
		Tuning.wearMode = true;
		build(helper);
		cylinder(helper).throwRod();
		helper.setBlock(CYLINDER, helper.getBlockState(CYLINDER)
			.setValue(CylinderBlock.HAS_PISTON, false)
			.setValue(CylinderBlock.HAS_ROD, false));
		helper.useBlock(CYLINDER, player(helper, new ItemStack(CEItems.CONNECTING_ROD.get())));
		helper.assertTrue(!helper.getBlockState(CYLINDER).getValue(CylinderBlock.HAS_ROD), "No rod goes in while the wreck is there");
		helper.useBlock(CYLINDER, player(helper, new ItemStack(CEItems.PISTON.get())));
		helper.assertTrue(!helper.getBlockState(CYLINDER).getValue(CylinderBlock.HAS_PISTON), "nor a piston");
		ItemStack kit = new ItemStack(CEItems.SERVICE_KIT.get());
		helper.useBlock(CYLINDER, player(helper, kit));
		helper.assertTrue(!cylinder(helper).thrown(), "A Service Kit clears the wreck");
		helper.assertTrue(kit.getDamageValue() == 1, "for one use of the kit: " + kit.getDamageValue());
		helper.useBlock(CYLINDER, player(helper, new ItemStack(CEItems.CONNECTING_ROD.get())));
		helper.assertTrue(helper.getBlockState(CYLINDER).getValue(CylinderBlock.HAS_ROD), "and then a new rod fits");
		helper.succeed();
	}

	@GameTest(template = "empty", batch = PARTS)
	public static void aBrokenHeadTakesNeitherAGasketNorAServiceKit(GameTestHelper helper) {
		Tuning.wearMode = true;
		build(helper);
		head(helper).breakHead();
		ItemStack gasket = new ItemStack(CEItems.RUBBER_GASKET.get());
		helper.useBlock(HEAD, player(helper, gasket));
		helper.assertTrue(head(helper).gasket() == Wear.Gasket.STOCK && gasket.getCount() == 1, "No gasket goes into a broken head");
		ItemStack kit = new ItemStack(CEItems.SERVICE_KIT.get());
		helper.useBlock(HEAD, player(helper, kit));
		helper.assertTrue(head(helper).broken() && kit.getDamageValue() == 0, "and a kit cannot mend it, nor is a use of it spent");
		helper.succeed();
	}

	@GameTest(template = "empty", batch = PARTS)
	public static void aServiceKitHasDurabilityAndBreaksOnItsLastUse(GameTestHelper helper) {
		Tuning.wearMode = true;
		build(helper);
		ItemStack kit = new ItemStack(CEItems.SERVICE_KIT.get());
		helper.assertTrue(kit.isDamageableItem() && kit.getMaxDamage() == com.createengines.content.WearService.KIT_USES, "A kit has uses: " + kit.getMaxDamage());
		kit.setDamageValue(kit.getMaxDamage() - 1);
		cylinder(helper).addWear(0.3, 0.3);
		Player player = player(helper, kit);
		helper.useBlock(CYLINDER, player);
		helper.assertTrue(player.getMainHandItem().isEmpty(), "The last use breaks it");
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 900, batch = DRY)
	public static void aPistonRunDryForTwentySecondsSeizesAndTheEngineSaysSo(GameTestHelper helper) {
		Tuning.wearMode = true;
		WearKeeper.filmOverride = () -> 0;
		build(helper);
		helper.runAtTickTime(800, () -> {
			helper.assertTrue(cylinder(helper).seized(), "The piston seized");
			helper.assertTrue(cylinder(helper).pistonWear() >= Wear.SEIZED_WEAR, "and is left badly worn: " + cylinder(helper).pistonWear());
			var state = helper.getBlockState(CYLINDER);
			helper.assertTrue(state.getValue(CylinderBlock.HAS_PISTON) && state.getValue(CylinderBlock.HAS_ROD), "Nothing is lost: the parts are still in");
			helper.assertTrue(summary(helper).firing() == 0, "The cylinder is locked: " + summary(helper));
			helper.assertTrue(CylinderFault.SEIZED.in(cylinder(helper).status().faults()), "and says why");
			WearKeeper.filmOverride = null;
			helper.succeed();
		});
	}

	@GameTest(template = "empty", timeoutTicks = 900, batch = OILED)
	public static void aWellOiledEngineNeverSeizesHoweverLongItRuns(GameTestHelper helper) {
		Tuning.wearMode = true;
		WearKeeper.filmOverride = () -> 1;
		build(helper);
		helper.runAtTickTime(800, () -> {
			helper.assertTrue(!cylinder(helper).seized(), "No seizure with oil");
			WearKeeper.filmOverride = null;
			helper.succeed();
		});
	}

	@GameTest(template = "empty", batch = PARTS)
	public static void aSeizedPistonIsFreedByAKitAndStaysWornAndANewPistonAlsoEndsIt(GameTestHelper helper) {
		Tuning.wearMode = true;
		build(helper);
		cylinder(helper).seize();
		helper.assertTrue(cylinder(helper).seized() && cylinder(helper).pistonWear() >= Wear.SEIZED_WEAR, "Seized and worn");
		ItemStack kit = new ItemStack(CEItems.SERVICE_KIT.get());
		helper.useBlock(CYLINDER, player(helper, kit));
		helper.assertTrue(!cylinder(helper).seized(), "A Service Kit frees it");
		helper.assertTrue(cylinder(helper).pistonWear() >= Wear.SEIZED_WEAR, "but the piston is as worn as it was left: " + cylinder(helper).pistonWear());
		helper.assertTrue(kit.getDamageValue() == 1, "for one use of the kit");

		cylinder(helper).seize();
		Player sneaking = player(helper, ItemStack.EMPTY);
		sneaking.setShiftKeyDown(true);
		helper.useBlock(CYLINDER, sneaking);
		sneaking.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		helper.useBlock(CYLINDER, sneaking);
		helper.assertTrue(!helper.getBlockState(CYLINDER).getValue(CylinderBlock.HAS_PISTON), "The seized piston comes out");
		helper.assertTrue(!cylinder(helper).seized() && cylinder(helper).pistonWear() == 0, "and takes the seizure and its wear with it");
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 600, batch = ON)
	public static void aPartThatIsNotWornEnoughIsNeverThrownEvenWithEveryRollAgainstIt(GameTestHelper helper) {
		Tuning.wearMode = true;
		WearKeeper.roller = () -> 0;
		build(helper);
		cylinder(helper).addWear(0.4, 0.4);
		head(helper).addGasketWear(0.4);
		helper.runAtTickTime(500, () -> {
			var state = helper.getBlockState(CYLINDER);
			helper.assertTrue(state.getValue(CylinderBlock.HAS_ROD) && state.getValue(CylinderBlock.HAS_PISTON), "Under half worn, nothing fails");
			helper.assertTrue(!head(helper).blown(), "and the gasket holds");
			WearKeeper.roller = null;
			helper.succeed();
		});
	}

	@GameTest(template = "empty", timeoutTicks = 600, batch = ON)
	public static void aSpentGasketBlowsAndStopsItsCylinder(GameTestHelper helper) {
		Tuning.wearMode = true;
		WearKeeper.roller = () -> 0;
		build(helper);
		head(helper).addGasketWear(0.99);
		helper.runAtTickTime(500, () -> {
			helper.assertTrue(head(helper).blown(), "The gasket blew");
			helper.assertTrue(summary(helper).firing() == 0, "and the only cylinder stopped firing: " + summary(helper));
			var status = cylinder(helper).status();
			helper.assertTrue(status != null && CylinderFault.BLOWN_GASKET.in(status.faults()), "The cylinder says why: " + status);
			var state = helper.getBlockState(CYLINDER);
			helper.assertTrue(state.getValue(CylinderBlock.HAS_ROD) && state.getValue(CylinderBlock.HAS_PISTON), "The piston and rod are untouched");
			WearKeeper.roller = null;
			helper.succeed();
		});
	}

	@GameTest(template = "empty", timeoutTicks = 600, batch = ON)
	public static void aBlownGasketStaysBlownWhenTheEngineIsBuiltAgain(GameTestHelper helper) {
		Tuning.wearMode = true;
		build(helper);
		head(helper).blow();
		helper.runAtTickTime(300, () -> {
			helper.assertTrue(summary(helper).firing() == 0, "A head that was already blown when the engine was built does not fire");
			helper.succeed();
		});
	}

	@GameTest(template = "empty", timeoutTicks = 800, batch = ON)
	public static void aNewGasketGetsABlownCylinderRunningAgain(GameTestHelper helper) {
		Tuning.wearMode = true;
		build(helper);
		head(helper).blow();
		helper.runAtTickTime(200, () -> {
			helper.assertTrue(summary(helper).firing() == 0, "Stopped while blown");
			ItemStack gasket = new ItemStack(CEItems.RUBBER_GASKET.get(), 2);
			helper.useBlock(HEAD, player(helper, gasket));
			helper.assertTrue(!head(helper).blown() && head(helper).gasket() == Wear.Gasket.RUBBER, "The new gasket is in: " + head(helper).gasket());
			helper.assertTrue(gasket.getCount() == 1, "and cost one: " + gasket.getCount());
		});
		helper.runAtTickTime(700, () -> {
			helper.assertTrue(summary(helper).firing() == 1, "and the cylinder fires again: " + summary(helper));
			helper.succeed();
		});
	}

	// --- Parts and items, with no engine running ---

	@GameTest(template = "empty", batch = PARTS)
	public static void aServiceKitSetsLightWearRightAndHeavyWearIsLeftAlone(GameTestHelper helper) {
		Tuning.wearMode = true;
		build(helper);
		cylinder(helper).addWear(0.3, 0.3);
		ItemStack kit = new ItemStack(CEItems.SERVICE_KIT.get());
		helper.useBlock(CYLINDER, player(helper, kit));
		helper.assertTrue(cylinder(helper).pistonWear() == Wear.REPAIRED_TO && cylinder(helper).rodWear() == Wear.REPAIRED_TO,
			"Light wear is set right: " + cylinder(helper).pistonWear());
		helper.assertTrue(kit.getDamageValue() == 1, "for one use of the kit: " + kit.getDamageValue());

		cylinder(helper).addWear(0.7, 0.7);
		helper.useBlock(CYLINDER, player(helper, kit));
		helper.assertTrue(cylinder(helper).pistonWear() > 0.7, "Heavy wear is not touched: " + cylinder(helper).pistonWear());
		helper.assertTrue(kit.getDamageValue() == 1, "and no use of the kit is spent on it: " + kit.getDamageValue());
		helper.succeed();
	}

	@GameTest(template = "empty", batch = PARTS)
	public static void aServiceKitSetsALightlyWornGasketRightButNotABlownOne(GameTestHelper helper) {
		Tuning.wearMode = true;
		build(helper);
		head(helper).addGasketWear(0.3);
		ItemStack kit = new ItemStack(CEItems.SERVICE_KIT.get());
		helper.useBlock(HEAD, player(helper, kit));
		helper.assertTrue(head(helper).gasketWear() == Wear.REPAIRED_TO, "A lightly worn gasket is set right: " + head(helper).gasketWear());
		head(helper).addGasketWear(0.2);
		head(helper).blow();
		helper.useBlock(HEAD, player(helper, kit));
		helper.assertTrue(head(helper).blown() && kit.getDamageValue() == 1, "A blown one needs a new gasket: " + kit.getDamageValue());
		helper.succeed();
	}

	@GameTest(template = "empty", batch = PARTS)
	public static void aNewGasketReplacesTheOldAndHandsItBack(GameTestHelper helper) {
		build(helper);
		head(helper).fit(Wear.Gasket.CARDBOARD);
		head(helper).addGasketWear(0.6);
		Player player = player(helper, new ItemStack(CEItems.COPPER_GASKET.get()));
		helper.useBlock(HEAD, player);
		helper.assertTrue(head(helper).gasket() == Wear.Gasket.COPPER && head(helper).gasketWear() == 0, "A new copper gasket is in, as new");
		helper.assertTrue(player.getInventory().countItem(CEItems.CARDBOARD_GASKET.get()) == 1, "and the old one came back");
		helper.succeed();
	}

	@GameTest(template = "empty", batch = PARTS)
	public static void aNewPistonAndRodStartNewWhateverTheOldOnesWore(GameTestHelper helper) {
		build(helper);
		cylinder(helper).addWear(0.8, 0.8);
		Player sneaking = player(helper, ItemStack.EMPTY);
		sneaking.setShiftKeyDown(true);
		helper.useBlock(CYLINDER, sneaking);
		helper.assertTrue(!helper.getBlockState(CYLINDER).getValue(CylinderBlock.HAS_ROD), "The rod comes out first");
		helper.assertTrue(cylinder(helper).rodWear() == 0 && cylinder(helper).pistonWear() > 0.7, "its wear gone with it, the piston's kept");
		// The rod that came out lands in the player's empty hand, and a sneak-use with a rod in it would put it straight back
		sneaking.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		helper.useBlock(CYLINDER, sneaking);
		helper.assertTrue(cylinder(helper).pistonWear() == 0, "then the piston's: " + cylinder(helper).pistonWear() + " in " + helper.getBlockState(CYLINDER));
		Player fitting = player(helper, new ItemStack(CEItems.CONNECTING_ROD.get()));
		helper.useBlock(CYLINDER, fitting);
		helper.assertTrue(helper.getBlockState(CYLINDER).getValue(CylinderBlock.HAS_ROD) && cylinder(helper).rodWear() == 0, "A fitted rod is new");
		helper.succeed();
	}

	@GameTest(template = "empty", batch = PARTS)
	public static void wearIsSavedWithThePartsAndAnUnwornPartSavesWhatItAlwaysDid(GameTestHelper helper) {
		build(helper);
		var registries = helper.getLevel().registryAccess();
		CompoundTag clean = head(helper).saveWithFullMetadata(registries);
		CompoundTag cleanCylinder = cylinder(helper).saveWithFullMetadata(registries);
		helper.assertTrue(!clean.contains("Gasket") && !clean.contains("GasketWear") && !clean.contains("Blown"),
			"A head that never wore saves no wear keys: " + clean);
		helper.assertTrue(!cleanCylinder.contains("PistonWear") && !cleanCylinder.contains("RodWear") && !cleanCylinder.contains("Thrown") && !cleanCylinder.contains("Seized"), "nor does a cylinder: " + cleanCylinder);
		helper.assertTrue(!clean.contains("Broken"), "and no head says it is broken");

		head(helper).fit(Wear.Gasket.RUBBER);
		head(helper).addGasketWear(0.4);
		head(helper).blow();
		cylinder(helper).addWear(0.25, 0.5);
		cylinder(helper).throwRod();
		CompoundTag headTag = head(helper).saveWithFullMetadata(registries);
		CompoundTag cylinderTag = cylinder(helper).saveWithFullMetadata(registries);

		helper.setBlock(HEAD, Blocks.AIR);
		helper.setBlock(HEAD, com.createengines.registry.CEBlocks.CYLINDER_HEAD.getDefaultState());
		head(helper).loadWithComponents(headTag, registries);
		cylinder(helper).loadWithComponents(cylinderTag, registries);
		helper.assertTrue(head(helper).gasket() == Wear.Gasket.RUBBER && Math.abs(head(helper).gasketWear() - 0.4) < 1e-9 && head(helper).blown(),
			"The gasket comes back as it was: " + head(helper).gasket() + " " + head(helper).gasketWear());
		helper.assertTrue(cylinder(helper).thrown(), "and so does a thrown rod's wreck");
		helper.succeed();
	}

	@GameTest(template = "empty", batch = PARTS)
	public static void aDamagedSaveCannotGiveAPartNaNOrNegativeWear(GameTestHelper helper) {
		build(helper);
		var registries = helper.getLevel().registryAccess();
		CompoundTag tag = cylinder(helper).saveWithFullMetadata(registries);
		tag.putDouble("PistonWear", Double.NaN);
		tag.putDouble("RodWear", -5);
		cylinder(helper).loadWithComponents(tag, registries);
		helper.assertTrue(cylinder(helper).pistonWear() == 0 && cylinder(helper).rodWear() == 0, "Nonsense reads as new");
		CompoundTag headTag = head(helper).saveWithFullMetadata(registries);
		headTag.putDouble("GasketWear", Double.POSITIVE_INFINITY);
		headTag.putInt("Gasket", 99);
		head(helper).loadWithComponents(headTag, registries);
		helper.assertTrue(head(helper).gasketWear() == 1 && head(helper).gasket() == Wear.Gasket.STOCK, "and an unknown gasket is the stock one");
		helper.succeed();
	}

	// --- Wear carried on the parts themselves ---

	/** Takes the first stack of {@code item} out of a player's inventory and returns it (an empty stack if there is none). */
	private static ItemStack takeFrom(Player player, net.minecraft.world.item.Item item) {
		var inventory = player.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++)
			if (inventory.getItem(i).is(item)) {
				ItemStack found = inventory.getItem(i).copy();
				inventory.setItem(i, ItemStack.EMPTY);
				return found;
			}
		return ItemStack.EMPTY;
	}

	@GameTest(template = "empty", batch = PARTS)
	public static void aWornPistonAndRodComeOutAsWornAsTheyWentInAndGoBackInTheSame(GameTestHelper helper) {
		build(helper);
		cylinder(helper).addWear(0.6, 0.4);
		Player sneaking = player(helper, ItemStack.EMPTY);
		sneaking.setShiftKeyDown(true);
		helper.useBlock(CYLINDER, sneaking);
		ItemStack rod = takeFrom(sneaking, CEItems.CONNECTING_ROD.get());
		helper.assertTrue(Math.abs(WearService.wearOf(rod) - 0.4) < 1e-9, "The rod comes out as worn as it was: " + WearService.wearOf(rod));
		helper.useBlock(CYLINDER, sneaking);
		ItemStack piston = takeFrom(sneaking, CEItems.PISTON.get());
		helper.assertTrue(Math.abs(WearService.wearOf(piston) - 0.6) < 1e-9, "and so does the piston: " + WearService.wearOf(piston));
		helper.assertTrue(cylinder(helper).pistonWear() == 0 && cylinder(helper).rodWear() == 0, "The block starts clean");

		helper.useBlock(CYLINDER, player(helper, rod));
		helper.useBlock(CYLINDER, player(helper, piston));
		helper.assertTrue(helper.getBlockState(CYLINDER).getValue(CylinderBlock.HAS_ROD) && helper.getBlockState(CYLINDER).getValue(CylinderBlock.HAS_PISTON),
			"Both are back in");
		helper.assertTrue(Math.abs(cylinder(helper).rodWear() - 0.4) < 1e-9 && Math.abs(cylinder(helper).pistonWear() - 0.6) < 1e-9,
			"as worn as they were: " + cylinder(helper).pistonWear() + " " + cylinder(helper).rodWear());
		helper.succeed();
	}

	@GameTest(template = "empty", batch = PARTS)
	public static void aNewPartAndAWornOneAreNotTheSameStackAndNoWearIsTheSameAsNew(GameTestHelper helper) {
		ItemStack worn = WearService.withWear(CEItems.PISTON.asStack(), 0.3);
		helper.assertTrue(!ItemStack.isSameItemSameComponents(worn, CEItems.PISTON.asStack()), "A worn piston does not stack with a new one");
		helper.assertTrue(WearService.wearOf(CEItems.PISTON.asStack()) == 0, "A piston with no data is a new one");
		helper.assertTrue(ItemStack.isSameItemSameComponents(WearService.withWear(worn.copy(), 0), CEItems.PISTON.asStack()),
			"and a part repaired to nothing is the same stack as a new one");
		helper.assertTrue(WearService.wearOf(WearService.withWear(CEItems.PISTON.asStack(), Double.NaN)) == 0, "Nonsense wear reads as new");
		helper.assertTrue(WearService.wearOf(WearService.withWear(CEItems.PISTON.asStack(), 7)) == 1, "and too much as spent");
		helper.succeed();
	}

	@GameTest(template = "empty", batch = PARTS)
	public static void aGasketReplacedComesBackWithItsWearAndABlownOneAsSpent(GameTestHelper helper) {
		Tuning.wearMode = true;
		build(helper);
		head(helper).fit(Wear.Gasket.RUBBER, 0.5);
		Player first = player(helper, WearService.withWear(CEItems.COPPER_GASKET.asStack(), 0.2));
		helper.useBlock(HEAD, first);
		helper.assertTrue(head(helper).gasket() == Wear.Gasket.COPPER && Math.abs(head(helper).gasketWear() - 0.2) < 1e-9,
			"The copper gasket goes in as worn as it was: " + head(helper).gasketWear());
		ItemStack rubber = takeFrom(first, CEItems.RUBBER_GASKET.get());
		helper.assertTrue(Math.abs(WearService.wearOf(rubber) - 0.5) < 1e-9, "and the rubber one comes back as worn as it was: " + WearService.wearOf(rubber));

		head(helper).blow();
		Player second = player(helper, CEItems.CARDBOARD_GASKET.asStack());
		helper.useBlock(HEAD, second);
		ItemStack copper = takeFrom(second, CEItems.COPPER_GASKET.get());
		helper.assertTrue(WearService.wearOf(copper) == 1, "A blown gasket comes back spent: " + WearService.wearOf(copper));
		helper.assertTrue(!head(helper).blown() && head(helper).gasket() == Wear.Gasket.CARDBOARD, "and the new one is in");
		helper.succeed();
	}

	@GameTest(template = "empty", batch = PARTS)
	public static void aKitSneakUsedOnAHeadTakesTheGasketOutAndTheHeadStopsUntilOneIsFitted(GameTestHelper helper) {
		Tuning.wearMode = true;
		build(helper);
		ItemStack kit = new ItemStack(CEItems.SERVICE_KIT.get());
		Player sneaking = player(helper, kit);
		sneaking.setShiftKeyDown(true);
		helper.useBlock(HEAD, sneaking);
		helper.assertTrue(!head(helper).blown() && !head(helper).hasGasketItem(), "A head with no gasket fitted of its own has nothing to take out");

		head(helper).fit(Wear.Gasket.COPPER, 0.3);
		helper.useBlock(HEAD, sneaking);
		ItemStack out = takeFrom(sneaking, CEItems.COPPER_GASKET.get());
		helper.assertTrue(Math.abs(WearService.wearOf(out) - 0.3) < 1e-9, "The gasket comes out with its wear: " + WearService.wearOf(out));
		helper.assertTrue(head(helper).blown() && !head(helper).hasGasketItem(), "and the head is without one, so it does not run");
		helper.assertTrue(kit.getDamageValue() == 0, "and the kit is not spent on it");

		helper.useBlock(HEAD, player(helper, out));
		helper.assertTrue(!head(helper).blown() && head(helper).gasket() == Wear.Gasket.COPPER && Math.abs(head(helper).gasketWear() - 0.3) < 1e-9,
			"Fitted again it is as worn as it was, and the head runs");
		helper.succeed();
	}

	@GameTest(template = "empty", batch = PARTS)
	public static void theStockGasketIsAnItemAndIsReadAsFittedOnlyOnceItHasBeenFitted(GameTestHelper helper) {
		build(helper);
		helper.assertTrue(WearService.gasketOf(CEItems.STOCK_GASKET.asStack()) == Wear.Gasket.STOCK, "A stock gasket is a stock gasket");
		helper.assertTrue(!head(helper).hasGasketItem(), "A head that has never been given one is read as holding the stock one, which is not an item");
		var registries = helper.getLevel().registryAccess();
		helper.assertTrue(!head(helper).saveWithFullMetadata(registries).contains("StockFitted"), "and saves nothing extra for it");
		head(helper).fit(Wear.Gasket.STOCK, 0.25);
		helper.assertTrue(head(helper).hasGasketItem(), "A fitted one is");
		helper.assertTrue(head(helper).saveWithFullMetadata(registries).contains("StockFitted"), "and saves that it was");
		ItemStack out = head(helper).removeGasket();
		helper.assertTrue(CEItems.STOCK_GASKET.isIn(out) && Math.abs(WearService.wearOf(out) - 0.25) < 1e-9, "It comes out as a stock gasket with its wear");
		helper.succeed();
	}

	@GameTest(template = "empty", batch = PARTS)
	public static void aKitSetsALightlyWornPartRightWhenItIsHeldInTheOtherHandAndLeavesHeavyWearAlone(GameTestHelper helper) {
		ItemStack kit = new ItemStack(CEItems.SERVICE_KIT.get());
		Player player = player(helper, kit);
		ItemStack worn = WearService.withWear(new ItemStack(CEItems.PISTON.get(), 2), 0.3);
		player.setItemInHand(InteractionHand.OFF_HAND, worn);
		helper.assertTrue(WearService.repairOtherHand(kit, player, InteractionHand.MAIN_HAND), "The kit sets a lightly worn part right");
		helper.assertTrue(kit.getDamageValue() == 1, "for one use");
		helper.assertTrue(player.getItemInHand(InteractionHand.OFF_HAND).getCount() == 1 && Math.abs(WearService.wearOf(player.getItemInHand(InteractionHand.OFF_HAND)) - 0.3) < 1e-9,
			"of one part of the stack, the other left as it was");
		boolean found = false;
		var inventory = player.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++)
			if (inventory.getItem(i).is(CEItems.PISTON.get()) && Math.abs(WearService.wearOf(inventory.getItem(i)) - Wear.REPAIRED_TO) < 1e-9)
				found = true;
		helper.assertTrue(found, "and the repaired one is in the inventory at the repaired wear");

		ItemStack heavy = WearService.withWear(new ItemStack(CEItems.PISTON.get()), 0.7);
		Player other = player(helper, new ItemStack(CEItems.SERVICE_KIT.get()));
		other.setItemInHand(InteractionHand.OFF_HAND, heavy);
		helper.assertTrue(!WearService.repairOtherHand(other.getMainHandItem(), other, InteractionHand.MAIN_HAND), "Heavy wear is not touched");
		helper.assertTrue(other.getMainHandItem().getDamageValue() == 0, "and costs no use");
		helper.succeed();
	}

	// --- The readouts: one wear total for the whole build ---

	@GameTest(template = "empty", timeoutTicks = 800, batch = ON)
	public static void theSummaryCarriesTheMeanWearOfEveryPartAndTheBlackBoxAndDisplayLinkShowIt(GameTestHelper helper) {
		Tuning.wearMode = true;
		build(helper);
		BlockPos box = new BlockPos(2, 2, 1);
		helper.setBlock(box, CEBlocks.BLACK_BOX.getDefaultState());
		// A piston at 0.6, a rod at 0.3 and a new gasket: the mean of the three is 0.3
		cylinder(helper).addWear(0.6, 0.3);
		helper.runAtTickTime(600, () -> {
			float total = summary(helper).wear();
			helper.assertTrue(Math.abs(total - 0.3f) < 0.03f, "The summary carries the mean of the three parts: " + total);
			helper.assertTrue(Math.abs(EngineReadout.wear(summary(helper)) - total) < 1e-6, "and a Display Link reads the same figure: " + EngineReadout.wear(summary(helper)));
			helper.assertTrue(CEDisplaySources.ENGINE_WEAR.get() != null, "There is a Display Link source for it");
			BlackBoxBlockEntity recorder = helper.getBlockEntity(box);
			var readout = recorder.readout();
			helper.assertTrue(readout != null && readout.hasWear(), "The Black Box has it on its tape: " + readout);
			helper.assertTrue(Math.abs(readout.wearNow() - 30) <= 3, "at about thirty percent: " + readout.wearNow());
			WearKeeper.roller = null;
			helper.succeed();
		});
	}

	@GameTest(template = "empty", timeoutTicks = 800, batch = ON)
	public static void aThrownRodAndABrokenHeadCountAsSpentPartsInTheTotal(GameTestHelper helper) {
		Tuning.wearMode = true;
		build(helper);
		cylinder(helper).throwRod();
		head(helper).breakHead();
		helper.runAtTickTime(300, () -> {
			// A lost piston, a lost rod and a lost gasket are three spent parts: nothing is made to look healthier for being gone
			helper.assertTrue(summary(helper).wear() > 0.99f, "A build that has lost its parts reads fully worn: " + summary(helper).wear());
			helper.succeed();
		});
	}

	@GameTest(template = "empty", timeoutTicks = 500, batch = OFF)
	public static void withTheModeOffThereIsNoWearTotalAndADisplayLinkReadsNothingWorn(GameTestHelper helper) {
		Tuning.wearMode = false;
		build(helper);
		BlockPos box = new BlockPos(2, 2, 1);
		helper.setBlock(box, CEBlocks.BLACK_BOX.getDefaultState());
		cylinder(helper).addWear(0.6, 0.6);
		helper.runAtTickTime(400, () -> {
			helper.assertTrue(summary(helper).wear() < 0, "The summary has no wear total: " + summary(helper).wear());
			helper.assertTrue(EngineReadout.wear(summary(helper)) == 0f, "so a Display Link reads nothing worn");
			BlackBoxBlockEntity recorder = helper.getBlockEntity(box);
			helper.assertTrue(recorder.readout() == null || !recorder.readout().hasWear(), "and the Black Box draws no wear line");
			helper.succeed();
		});
	}

	@GameTest(template = "empty", batch = PARTS)
	public static void theSummaryKeepsItsWearTotalThroughSavingAndOldSummariesHaveNone(GameTestHelper helper) {
		var clean = com.createengines.engine.EngineSummary.EMPTY.write();
		helper.assertTrue(!clean.contains("Wear"), "A summary with no total saves no key: " + clean);
		helper.assertTrue(com.createengines.engine.EngineSummary.read(clean).wear() < 0, "and reads back as none");
		clean.putFloat("Wear", 0.42f);
		var read = com.createengines.engine.EngineSummary.read(clean);
		helper.assertTrue(Math.abs(read.wear() - 0.42f) < 1e-6, "A saved total comes back: " + read.wear());
		helper.assertTrue(Math.abs(com.createengines.engine.EngineSummary.read(read.write()).wear() - 0.42f) < 1e-6, "and again after another save");
		clean.putFloat("Wear", 0.5f);
		helper.assertTrue(com.createengines.engine.EngineSummary.read(clean).differsFrom(read), "A changed total is worth syncing");
		helper.succeed();
	}

	// --- A fitted gasket seals the head with the wear mode off too ---

	private static final BlockPos SECOND = new BlockPos(14, 0, 0);

	private static float torqueOf(GameTestHelper helper, BlockPos offset) {
		CrankshaftBlockEntity crank = helper.getBlockEntity(PRIMARY.offset(offset));
		var summary = crank.summary();
		if (summary == null)
			helper.fail("no engine at " + offset, PRIMARY.offset(offset));
		return summary.availableTorque();
	}

	@GameTest(template = "empty_large", timeoutTicks = 1200, batch = OFF)
	public static void aFittedGasketGivesMorePowerWithTheModeOffAndTakingItOutGivesItBack(GameTestHelper helper) {
		Tuning.wearMode = false;
		TestEngine.build(helper.getLevel(), helper.absolutePos(BlockPos.ZERO), true);
		TestEngine.build(helper.getLevel(), helper.absolutePos(SECOND), true);
		CylinderHeadBlockEntity head = helper.getBlockEntity(HEAD.offset(SECOND));
		head.fit(Wear.Gasket.COPPER);
		helper.runAtTickTime(500, () -> {
			float plain = torqueOf(helper, BlockPos.ZERO);
			float sealed = torqueOf(helper, SECOND);
			helper.assertTrue(plain > 0 && sealed > plain * 1.02f, "The engine with a copper gasket can deliver more: " + sealed + " against " + plain);
			helper.assertTrue(sealed < plain * 1.12f, "by a few percent only: " + sealed + " against " + plain);
			helper.assertTrue(head.gasketWear() == 0 && !head.blown(), "and the gasket has not worn: nothing wears with the mode off");
			head.removeGasket();
		});
		helper.runAtTickTime(1000, () -> {
			float plain = torqueOf(helper, BlockPos.ZERO);
			float taken = torqueOf(helper, SECOND);
			helper.assertTrue(Math.abs(taken - plain) <= plain * 0.01f, "With the gasket out it is the same as the one that never had one: " + taken + " against " + plain);
			helper.succeed();
		});
	}

	@GameTest(template = "empty_large", timeoutTicks = 900, batch = ON)
	public static void aFittedGasketGivesMorePowerWithTheModeOnAndAHeadWithNoneFittedIsReadAsBefore(GameTestHelper helper) {
		Tuning.wearMode = true;
		TestEngine.build(helper.getLevel(), helper.absolutePos(BlockPos.ZERO), true);
		TestEngine.build(helper.getLevel(), helper.absolutePos(SECOND), true);
		CylinderHeadBlockEntity head = helper.getBlockEntity(HEAD.offset(SECOND));
		head.fit(Wear.Gasket.COPPER);
		helper.runAtTickTime(600, () -> {
			float plain = torqueOf(helper, BlockPos.ZERO);
			float sealed = torqueOf(helper, SECOND);
			helper.assertTrue(sealed > plain * 1.02f, "With the mode on too: " + sealed + " against " + plain);
			helper.succeed();
		});
	}

	// --- Schematics ---

	private static java.util.List<net.minecraft.world.item.Item> priced(GameTestHelper helper) {
		return com.simibubi.create.content.schematics.requirement.ItemRequirement.of(helper.getBlockState(HEAD), head(helper))
			.getRequiredItems()
			.stream()
			.map(requirement -> requirement.stack.getItem())
			.toList();
	}

	@GameTest(template = "empty", batch = PARTS)
	public static void aSchematicOfAHeadWithAGasketFittedPricesTheGasketAndOneWithoutDoesNot(GameTestHelper helper) {
		build(helper);
		for (var gasket : new net.minecraft.world.item.Item[] {CEItems.STOCK_GASKET.get(), CEItems.CARDBOARD_GASKET.get(), CEItems.RUBBER_GASKET.get(),
			CEItems.COPPER_GASKET.get()})
			helper.assertTrue(!priced(helper).contains(gasket), "A head with none fitted costs no " + gasket);

		head(helper).fit(Wear.Gasket.COPPER);
		helper.assertTrue(priced(helper).contains(CEItems.COPPER_GASKET.get()), "A copper gasket fitted is priced: " + priced(helper));
		helper.assertTrue(!priced(helper).contains(CEItems.RUBBER_GASKET.get()), "and only that one");
		head(helper).fit(Wear.Gasket.STOCK, 0.4);
		helper.assertTrue(priced(helper).contains(CEItems.STOCK_GASKET.get()) && !priced(helper).contains(CEItems.COPPER_GASKET.get()),
			"A stock gasket fitted is priced as a stock gasket: " + priced(helper));
		head(helper).removeGasket();
		helper.assertTrue(!priced(helper).contains(CEItems.STOCK_GASKET.get()), "A gasket taken out is priced no more");
		head(helper).fit(Wear.Gasket.RUBBER);
		head(helper).breakHead();
		helper.assertTrue(!priced(helper).contains(CEItems.RUBBER_GASKET.get()), "A broken head's gasket is lost with it, so it costs none");
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 900, batch = ON)
	public static void aBlackBoxRecordsAThrownRodAsAnEventOfItsOwn(GameTestHelper helper) {
		Tuning.wearMode = true;
		WearKeeper.roller = () -> 0;
		build(helper);
		BlockPos box = new BlockPos(2, 2, 1);
		helper.setBlock(box, CEBlocks.BLACK_BOX.getDefaultState());
		cylinder(helper).addWear(0, 0.99);
		helper.runAtTickTime(800, () -> {
			BlackBoxBlockEntity recorder = helper.getBlockEntity(box);
			var readout = recorder.readout();
			helper.assertTrue(readout != null && readout.events().stream()
				.anyMatch(event -> event.cause() == com.createengines.engine.FlightRecorder.Cause.ROD_THROWN && event.from() == event.to()),
				"The tape has the thrown rod as an event: " + (readout == null ? null : readout.events()));
			WearKeeper.roller = null;
			helper.succeed();
		});
	}
}
