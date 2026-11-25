package com.createengines.gametest;

import com.createengines.CreateEngines;
import com.createengines.content.battery.BatteryBlockEntity;
import com.createengines.content.crankshaft.CrankshaftBlockEntity;
import com.createengines.content.rotor.RotorHousingBlockEntity;
import com.createengines.engine.EngineState;
import com.createengines.engine.EngineSummary;
import com.createengines.engine.WearKeeper;
import com.createengines.engine.sim.BatteryTier;
import com.createengines.engine.sim.BatteryWear;
import com.createengines.engine.sim.Tuning;
import com.createengines.engine.sim.Wear;
import com.createengines.engine.topology.CylinderFault;
import com.createengines.registry.CEBlocks;
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
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The opt-in wear mode on batteries and rotor housings' apex seals (the rest of the mode is in {@link WearGameTests}): nothing wears with
 * it off, wear shows with it on, a worn battery holds less and keeps working, a spent set of seals stops its rotor and a new set starts it
 * again, a Service Kit sets light wear right, and what is saved is exactly what it always was until something has worn.
 */
@GameTestHolder(CreateEngines.MOD_ID)
@PrefixGameTestTemplate(false)
public class WearBatteryRotaryGameTests {

	private static final String OFF = "wear-battery-off";
	private static final String ON = "wear-battery-on";
	private static final String APEX_ON = "wear-apex-on";
	private static final String APEX_OFF = "wear-apex-off";
	// Alone, so that no other test finishing can take the forced roll away while this one still relies on it
	private static final String APEX_STOPPED = "wear-apex-stopped";

	private static final BlockPos A = new BlockPos(6, 1, 1);
	private static final BlockPos B = new BlockPos(6, 1, 3);
	private static final BlockPos C = new BlockPos(6, 1, 5);

	@AfterBatch(batch = ON)
	public static void restoreTheModeAfterOn(ServerLevel level) {
		GameTestConfig.wearMode(false);
	}

	@AfterBatch(batch = APEX_ON)
	public static void restoreTheModeAfterApex(ServerLevel level) {
		GameTestConfig.wearMode(false);
		WearKeeper.roller = null;
	}

	@AfterBatch(batch = APEX_STOPPED)
	public static void restoreTheModeAfterApexStopped(ServerLevel level) {
		GameTestConfig.wearMode(false);
		WearKeeper.roller = null;
	}

	private static BatteryBlockEntity battery(GameTestHelper helper, BlockPos pos, net.minecraft.world.level.block.state.BlockState block, float charge) {
		helper.setBlock(pos, block);
		BatteryBlockEntity battery = helper.getBlockEntity(pos);
		battery.charge(charge);
		return battery;
	}

	private static Player player(GameTestHelper helper, ItemStack held) {
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		player.setItemInHand(InteractionHand.MAIN_HAND, held);
		return player;
	}

	private static RotorHousingBlockEntity housing(GameTestHelper helper) {
		return helper.getBlockEntity(TestEngine.HOUSING);
	}

	private static EngineSummary summary(GameTestHelper helper) {
		CrankshaftBlockEntity crank = helper.getBlockEntity(TestEngine.PRIMARY);
		EngineSummary summary = crank.summary();
		if (summary == null)
			helper.fail("the crankshaft has no engine", TestEngine.PRIMARY);
		return summary;
	}

	// --- Batteries ---

	@GameTest(template = "empty", batch = OFF)
	public static void withTheModeOffABatteryNeverWearsHoweverDeepItIsDrawn(GameTestHelper helper) {
		GameTestConfig.wearMode(false);
		BatteryBlockEntity plain = battery(helper, A, CEBlocks.BATTERY.getDefaultState(), 200);
		for (int i = 0; i < 40; i++)
			plain.draw(5);
		helper.assertTrue(plain.charge() == 0, "It was drawn flat: " + plain.charge());
		helper.assertTrue(plain.wear() == 0 && plain.capacity() == 200, "and did not wear: " + plain.wear() + ", holds " + plain.capacity());
		helper.assertTrue(!plain.saveWithFullMetadata(helper.getLevel()
			.registryAccess())
			.contains("Wear"), "A battery that never wore saves no wear key");
		helper.succeed();
	}

	@GameTest(template = "empty", batch = OFF)
	public static void withTheModeOffASavedWearChangesNothingAboutWhatABatteryHolds(GameTestHelper helper) {
		GameTestConfig.wearMode(false);
		BatteryBlockEntity plain = battery(helper, A, CEBlocks.BATTERY.getDefaultState(), 0);
		var registries = helper.getLevel()
			.registryAccess();
		CompoundTag tag = plain.saveWithFullMetadata(registries);
		tag.putDouble("Wear", 1);
		tag.putFloat("Charge", 200);
		plain.loadWithComponents(tag, registries);
		helper.assertTrue(plain.capacity() == 200 && plain.charge() == 200, "A world that wore with the mode on holds everything with it off: " + plain.capacity());
		helper.succeed();
	}

	@GameTest(template = "empty", batch = ON)
	public static void withTheModeOnDrawingOnABatteryWearsItAndAShallowDrawWearsLessThanADeepOne(GameTestHelper helper) {
		GameTestConfig.wearMode(true);
		BatteryBlockEntity shallow = battery(helper, A, CEBlocks.BATTERY.getDefaultState(), 200);
		BatteryBlockEntity deep = battery(helper, B, CEBlocks.BATTERY.getDefaultState(), 10);
		shallow.draw(10);
		deep.draw(10);
		helper.assertTrue(shallow.wear() > 0, "A draw wears a battery: " + shallow.wear());
		helper.assertTrue(Math.abs(shallow.wear() - BatteryWear.drawWear(BatteryTier.PLAIN, 10, 1, 0)) < 1e-12,
			"by exactly what the rules say: " + shallow.wear());
		helper.assertTrue(deep.wear() > shallow.wear(), "and the same draw from a nearly flat one wears it more: " + deep.wear() + " > " + shallow.wear());
		helper.succeed();
	}

	@GameTest(template = "empty", batch = ON)
	public static void aSpentBatteryHoldsHalfAndStillWorks(GameTestHelper helper) {
		GameTestConfig.wearMode(true);
		BatteryBlockEntity plain = battery(helper, A, CEBlocks.BATTERY.getDefaultState(), 200);
		var registries = helper.getLevel()
			.registryAccess();
		CompoundTag tag = plain.saveWithFullMetadata(registries);
		tag.putDouble("Wear", 1);
		plain.loadWithComponents(tag, registries);
		helper.assertTrue(plain.capacity() == 100, "A spent battery holds half: " + plain.capacity());
		helper.assertTrue(plain.charge() == 100, "and what it held above that is gone: " + plain.charge());
		helper.assertTrue(plain.draw(30) == 30 && plain.charge() == 70, "but it still gives charge");
		helper.assertTrue(plain.charge(500) == 30 && plain.isFull(), "and takes it, up to what it now holds: " + plain.charge());
		helper.succeed();
	}

	@GameTest(template = "empty", batch = ON)
	public static void aServiceKitSetsALightlyWornBatteryRightAndLeavesAHeavyOneAlone(GameTestHelper helper) {
		GameTestConfig.wearMode(true);
		BatteryBlockEntity plain = battery(helper, A, CEBlocks.BATTERY.getDefaultState(), 200);
		var registries = helper.getLevel()
			.registryAccess();
		CompoundTag tag = plain.saveWithFullMetadata(registries);
		tag.putDouble("Wear", 0.3);
		plain.loadWithComponents(tag, registries);
		ItemStack kit = new ItemStack(CEItems.SERVICE_KIT.get());
		helper.useBlock(A, player(helper, kit));
		helper.assertTrue(plain.wear() == Wear.REPAIRED_TO, "Light wear is set right: " + plain.wear());
		helper.assertTrue(kit.getDamageValue() == 1, "for one use of the kit: " + kit.getDamageValue());

		tag.putDouble("Wear", 0.7);
		plain.loadWithComponents(tag, registries);
		helper.useBlock(A, player(helper, kit));
		helper.assertTrue(plain.wear() == 0.7 && kit.getDamageValue() == 1, "Heavy wear is not touched, and costs no use: " + plain.wear());
		helper.succeed();
	}

	@GameTest(template = "empty", batch = ON)
	public static void batteryWearIsSavedAndADamagedSaveReadsAsNewOrSpent(GameTestHelper helper) {
		GameTestConfig.wearMode(true);
		BatteryBlockEntity dry = battery(helper, A, CEBlocks.DRY_CELL.getDefaultState(), 100);
		dry.draw(100);
		double worn = dry.wear();
		helper.assertTrue(worn > 0, "The dry cell wore: " + worn);
		var registries = helper.getLevel()
			.registryAccess();
		CompoundTag tag = dry.saveWithFullMetadata(registries);
		helper.assertTrue(tag.contains("Wear"), "and saved it");
		BatteryBlockEntity again = battery(helper, C, CEBlocks.DRY_CELL.getDefaultState(), 0);
		again.loadWithComponents(tag, registries);
		helper.assertTrue(Math.abs(again.wear() - worn) < 1e-12, "It comes back as it was: " + again.wear());
		tag.putDouble("Wear", Double.NaN);
		again.loadWithComponents(tag, registries);
		helper.assertTrue(again.wear() == 0, "Not a number reads as new");
		tag.putDouble("Wear", -3);
		again.loadWithComponents(tag, registries);
		helper.assertTrue(again.wear() == 0, "and so does a negative");
		tag.putDouble("Wear", Double.POSITIVE_INFINITY);
		again.loadWithComponents(tag, registries);
		helper.assertTrue(again.wear() == 1 && again.capacity() == 50, "Infinity is spent, which is half of 100: " + again.capacity());
		helper.succeed();
	}

	@GameTest(template = "empty", batch = ON)
	public static void aDryCellIsNotWornByTheColdButAPlainBatteryIs(GameTestHelper helper) {
		GameTestConfig.wearMode(true);
		Tuning.batteryCold = true;
		BatteryBlockEntity.biomeTemperatureOverride = 0f;
		BatteryBlockEntity plain = battery(helper, A, CEBlocks.BATTERY.getDefaultState(), 100);
		BatteryBlockEntity dry = battery(helper, B, CEBlocks.DRY_CELL.getDefaultState(), 50);
		helper.runAtTickTime(60, () -> {
			helper.assertTrue(plain.coldness() > 0.9 && dry.coldness() > 0.9, "Both are in the cold: " + plain.coldness());
			float plainBefore = (float) plain.wear();
			plain.draw(10);
			dry.draw(5);
			double cold = BatteryWear.drawWear(BatteryTier.PLAIN, 10, 0.5, plain.coldness());
			helper.assertTrue(plain.wear() - plainBefore > BatteryWear.drawWear(BatteryTier.PLAIN, 10, 0.5, 0) * 2.5,
				"A plain battery drawn in the cold wears about three times as fast: " + (plain.wear() - plainBefore) + " vs " + cold);
			helper.assertTrue(Math.abs(dry.wear() - BatteryWear.drawWear(BatteryTier.DRY_CELL, 5, 0.5, 0)) < 1e-12,
				"but the cold does nothing to a dry cell: " + dry.wear());
			BatteryBlockEntity.biomeTemperatureOverride = null;
			helper.succeed();
		});
	}

	@AfterBatch(batch = OFF)
	public static void restoreTheTemperature(ServerLevel level) {
		BatteryBlockEntity.biomeTemperatureOverride = null;
	}

	// --- Apex seals ---

	@GameTest(template = "empty", timeoutTicks = 600, batch = APEX_OFF)
	public static void withTheModeOffARotorsSealsNeverWear(GameTestHelper helper) {
		GameTestConfig.wearMode(false);
		TestEngine.buildRotary(helper.getLevel(), helper.absolutePos(BlockPos.ZERO), true);
		helper.runAtTickTime(450, () -> {
			helper.assertTrue(summary(helper).firing() >= 1, "The rotor is firing: " + summary(helper));
			helper.assertTrue(housing(helper).apexWear() == 0 && !housing(helper).apexFailed(), "and its seals did not wear");
			helper.assertTrue(!housing(helper).saveWithFullMetadata(helper.getLevel()
				.registryAccess())
				.contains("ApexWear"), "A housing that never wore saves no wear key");
			helper.succeed();
		});
	}

	@GameTest(template = "empty", timeoutTicks = 600, batch = APEX_ON)
	public static void withTheModeOnARunningRotorWearsItsSeals(GameTestHelper helper) {
		GameTestConfig.wearMode(true);
		TestEngine.buildRotary(helper.getLevel(), helper.absolutePos(BlockPos.ZERO), true);
		helper.runAtTickTime(450, () -> {
			helper.assertTrue(housing(helper).apexWear() > 0, "The seals wore: " + housing(helper).apexWear());
			helper.assertTrue(housing(helper).apexWear() < 0.01, "but a few seconds is nowhere near a life: " + housing(helper).apexWear());
			helper.succeed();
		});
	}

	@GameTest(template = "empty", timeoutTicks = 700, batch = APEX_ON)
	public static void aSpentSetOfSealsFailsAndStopsAllThreeChambers(GameTestHelper helper) {
		GameTestConfig.wearMode(true);
		WearKeeper.roller = () -> 0;
		TestEngine.buildRotary(helper.getLevel(), helper.absolutePos(BlockPos.ZERO), true);
		housing(helper).addApexWear(0.99);
		helper.runAtTickTime(600, () -> {
			helper.assertTrue(housing(helper).apexFailed(), "The seals failed");
			helper.assertTrue(summary(helper).firing() == 0, "and no chamber fires: " + summary(helper));
			var chamber = housing(helper).chamber(0);
			helper.assertTrue(chamber != null && CylinderFault.APEX_FAILED.in(chamber.faults()), "The housing says why: " + chamber);
			var registries = helper.getLevel()
				.registryAccess();
			helper.assertTrue(housing(helper).getUpdateTag(registries)
				.getBoolean("ApexFailed"), "and clients are told, so the wreck is drawn");
			housing(helper).fitSeals();
			helper.assertTrue(!housing(helper).getUpdateTag(registries)
				.contains("ApexFailed"), "and told again when a new set takes the wreck away");
			WearKeeper.roller = null;
			helper.succeed();
		});
	}

	@GameTest(template = "empty", timeoutTicks = 800, batch = APEX_ON)
	public static void aRunningRotorWhoseSealsFailStallsWithinSeconds(GameTestHelper helper) {
		GameTestConfig.wearMode(true);
		TestEngine.buildRotary(helper.getLevel(), helper.absolutePos(BlockPos.ZERO), true);
		// The starter lets go, and the rotor is left running on its own
		helper.runAtTickTime(450, () -> helper.setBlock(TestEngine.STARTER_POWER, net.minecraft.world.level.block.Blocks.AIR));
		helper.runAtTickTime(550, () -> {
			helper.assertTrue(summary(helper).firing() >= 1 && summary(helper).rpm() > 20, "It runs on its own: " + summary(helper));
			housing(helper).failApex();
		});
		helper.runAtTickTime(650, () -> {
			helper.assertTrue(summary(helper).state() == EngineState.STALLED && summary(helper).rpm() == 0, "and has stopped: " + summary(helper));
			helper.succeed();
		});
	}

	@GameTest(template = "empty", timeoutTicks = 500, batch = APEX_STOPPED)
	public static void aStoppedRotorsSpentSealsHoldUntilItTurns(GameTestHelper helper) {
		GameTestConfig.wearMode(true);
		WearKeeper.roller = () -> 0;
		TestEngine.buildRotary(helper.getLevel(), helper.absolutePos(BlockPos.ZERO), true);
		// The starter is not engaged, so the engine stands still
		helper.setBlock(TestEngine.STARTER_POWER, net.minecraft.world.level.block.Blocks.AIR);
		housing(helper).addApexWear(0.99);
		helper.runAtTickTime(200, () -> {
			helper.assertTrue(!housing(helper).apexFailed(), "No set of seals fails standing still: " + summary(helper));
			helper.setBlock(TestEngine.STARTER_POWER, net.minecraft.world.level.block.Blocks.REDSTONE_BLOCK);
		});
		helper.runAtTickTime(450, () -> {
			helper.assertTrue(housing(helper).apexFailed(), "Once it turns, the spent set goes: " + summary(helper));
			helper.succeed();
		});
	}

	@GameTest(template = "empty", timeoutTicks = 1100, batch = APEX_ON)
	public static void aNewSetOfSealsGetsAFailedRotorRunningAgain(GameTestHelper helper) {
		GameTestConfig.wearMode(true);
		TestEngine.buildRotary(helper.getLevel(), helper.absolutePos(BlockPos.ZERO), true);
		housing(helper).addApexWear(0.9);
		housing(helper).failApex();
		helper.runAtTickTime(200, () -> {
			helper.assertTrue(summary(helper).firing() == 0, "Stopped while the seals are failed: " + summary(helper));
			ItemStack seals = new ItemStack(CEItems.APEX_SEALS.get(), 2);
			helper.useBlock(TestEngine.HOUSING, player(helper, seals));
			helper.assertTrue(!housing(helper).apexFailed() && housing(helper).apexWear() == 0, "The new set is in, as new");
			helper.assertTrue(seals.getCount() == 1, "and cost one: " + seals.getCount());
		});
		helper.runAtTickTime(1000, () -> {
			helper.assertTrue(summary(helper).firing() >= 1, "and the rotor fires again: " + summary(helper));
			helper.succeed();
		});
	}

	@GameTest(template = "empty", batch = APEX_ON)
	public static void aServiceKitSetsLightlyWornSealsRightButNotAFailedSet(GameTestHelper helper) {
		GameTestConfig.wearMode(true);
		TestEngine.buildRotary(helper.getLevel(), helper.absolutePos(BlockPos.ZERO), true);
		housing(helper).addApexWear(0.3);
		ItemStack kit = new ItemStack(CEItems.SERVICE_KIT.get());
		helper.useBlock(TestEngine.HOUSING, player(helper, kit));
		helper.assertTrue(housing(helper).apexWear() == Wear.REPAIRED_TO, "Light wear is set right: " + housing(helper).apexWear());
		helper.assertTrue(kit.getDamageValue() == 1, "for one use of the kit: " + kit.getDamageValue());
		housing(helper).addApexWear(0.5);
		helper.useBlock(TestEngine.HOUSING, player(helper, kit));
		helper.assertTrue(kit.getDamageValue() == 1, "Heavy wear is not touched, and costs no use");
		housing(helper).failApex();
		helper.useBlock(TestEngine.HOUSING, player(helper, kit));
		helper.assertTrue(housing(helper).apexFailed() && kit.getDamageValue() == 1, "and a failed set needs a new one");
		helper.succeed();
	}

	@GameTest(template = "empty", batch = APEX_ON)
	public static void sealWearIsSavedAndADamagedSaveReadsAsNew(GameTestHelper helper) {
		TestEngine.buildRotary(helper.getLevel(), helper.absolutePos(BlockPos.ZERO), true);
		var registries = helper.getLevel()
			.registryAccess();
		housing(helper).addApexWear(0.4);
		housing(helper).failApex();
		CompoundTag tag = housing(helper).saveWithFullMetadata(registries);
		helper.assertTrue(tag.contains("ApexWear") && tag.contains("ApexFailed"), "Worn and failed seals are saved");
		housing(helper).fitSeals();
		helper.assertTrue(housing(helper).apexWear() == 0 && !housing(helper).apexFailed(), "A new set clears both");
		housing(helper).loadWithComponents(tag, registries);
		helper.assertTrue(Math.abs(housing(helper).apexWear() - 0.4) < 1e-9 && housing(helper).apexFailed(), "and they come back as they were");
		tag.putDouble("ApexWear", Double.NaN);
		housing(helper).loadWithComponents(tag, registries);
		helper.assertTrue(housing(helper).apexWear() == 0, "Not a number reads as new");
		helper.succeed();
	}
}
