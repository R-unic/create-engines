package com.createengines.gametest;

import com.createengines.CreateEngines;
import com.createengines.content.WearService;
import com.createengines.content.crankshaft.CrankshaftBlockEntity;
import com.createengines.content.cylinder.CylinderBlock;
import com.createengines.content.cylinder.CylinderBlockEntity;
import com.createengines.content.head.CylinderHeadBlockEntity;
import com.createengines.content.rotor.RotorHousingBlockEntity;
import com.createengines.engine.EngineSummary;
import com.createengines.engine.WearKeeper;
import com.createengines.registry.CEItems;

import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Switching the wear mode off on a world that has worn and failed parts: nothing stays stopped or shows its wear, and what was saved
 * comes back when the mode is switched on again. The mode is one setting for the whole server, so each test has a batch of its own.
 */
@GameTestHolder(CreateEngines.MOD_ID)
@PrefixGameTestTemplate(false)
public class WearToggleGameTests {

	private static final String HEAD_AND_SEIZURE = "wear-toggle-head";
	private static final String WRECK = "wear-toggle-wreck";
	private static final String ROTOR = "wear-toggle-rotor";
	private static final String ITEMS = "wear-toggle-items";
	private static final String SWITCH_TICK = "wear-toggle-tick";
	private static final String PLAYERS = "wear-toggle-players";

	@AfterBatch(batch = PLAYERS)
	public static void restoreAfterPlayers(ServerLevel level) {
		GameTestConfig.wearMode(false);
	}

	@AfterBatch(batch = SWITCH_TICK)
	public static void restoreAfterSwitchTick(ServerLevel level) {
		WearKeeper.filmOverride = null;
		GameTestConfig.wearMode(false);
	}

	@AfterBatch(batch = HEAD_AND_SEIZURE)
	public static void restoreAfterHead(ServerLevel level) {
		GameTestConfig.wearMode(false);
	}

	@AfterBatch(batch = WRECK)
	public static void restoreAfterWreck(ServerLevel level) {
		GameTestConfig.wearMode(false);
	}

	@AfterBatch(batch = ROTOR)
	public static void restoreAfterRotor(ServerLevel level) {
		GameTestConfig.wearMode(false);
	}

	@AfterBatch(batch = ITEMS)
	public static void restoreAfterItems(ServerLevel level) {
		GameTestConfig.wearMode(false);
	}

	private static EngineSummary summary(GameTestHelper helper) {
		CrankshaftBlockEntity crank = helper.getBlockEntity(TestEngine.PRIMARY);
		EngineSummary summary = crank.summary();
		if (summary == null)
			helper.fail("the crankshaft has no engine", TestEngine.PRIMARY);
		return summary;
	}

	private static Player player(GameTestHelper helper, ItemStack held) {
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		player.setItemInHand(InteractionHand.MAIN_HAND, held);
		return player;
	}

	@GameTest(template = "empty", timeoutTicks = 1600, batch = HEAD_AND_SEIZURE)
	public static void switchingTheModeOffRunsABlownAndSeizedCylinderAndSwitchingItOnStopsItAgain(GameTestHelper helper) {
		GameTestConfig.wearMode(true);
		TestEngine.build(helper.getLevel(), helper.absolutePos(net.minecraft.core.BlockPos.ZERO), true);
		CylinderBlockEntity cylinder = helper.getBlockEntity(TestEngine.CYLINDER);
		CylinderHeadBlockEntity head = helper.getBlockEntity(TestEngine.HEAD);
		cylinder.seize();
		head.blow();
		helper.runAtTickTime(200, () -> {
			helper.assertTrue(summary(helper).firing() == 0, "Stopped with the mode on: " + summary(helper));
			GameTestConfig.wearMode(false);
		});
		helper.runAtTickTime(800, () -> {
			helper.assertTrue(summary(helper).firing() == 1, "Fires with the mode off: " + summary(helper));
			helper.assertTrue(summary(helper).failures() == 0 && summary(helper).wear() < 0, "and reports no failure or wear: " + summary(helper));
			helper.assertTrue(!cylinder.showsSeized() && !head.showsBlown(), "Neither failure shows");
			helper.assertTrue(cylinder.seized() && head.blown() && cylinder.pistonWear() > 0, "Both are still saved, wear and all");
			GameTestConfig.wearMode(true);
		});
		helper.runAtTickTime(1100, () -> {
			helper.assertTrue(summary(helper).firing() == 0 && summary(helper).failures() != 0, "Stopped again with the mode back on: " + summary(helper));
			helper.succeed();
		});
	}

	@GameTest(template = "empty", batch = WRECK)
	public static void withTheModeOffNewPartsGoInOverAThrownRodsWreckAndTheHeadWorks(GameTestHelper helper) {
		GameTestConfig.wearMode(true);
		TestEngine.build(helper.getLevel(), helper.absolutePos(net.minecraft.core.BlockPos.ZERO), true);
		CylinderBlockEntity cylinder = helper.getBlockEntity(TestEngine.CYLINDER);
		CylinderHeadBlockEntity head = helper.getBlockEntity(TestEngine.HEAD);
		cylinder.throwRod();
		head.breakHead();
		helper.setBlock(TestEngine.CYLINDER, helper.getBlockState(TestEngine.CYLINDER)
			.setValue(CylinderBlock.HAS_PISTON, false)
			.setValue(CylinderBlock.FORGED_PISTON, false)
			.setValue(CylinderBlock.HAS_ROD, false));
		GameTestConfig.wearMode(false);
		helper.assertTrue(!cylinder.showsThrown() && !head.showsBroken(), "Neither the wreck nor the broken head shows");
		helper.useBlock(TestEngine.CYLINDER, player(helper, new ItemStack(CEItems.CONNECTING_ROD.get())));
		helper.assertTrue(helper.getBlockState(TestEngine.CYLINDER).getValue(CylinderBlock.HAS_ROD), "A rod goes in");
		helper.useBlock(TestEngine.CYLINDER, player(helper, CEItems.PISTON.asStack()));
		helper.assertTrue(helper.getBlockState(TestEngine.CYLINDER).getValue(CylinderBlock.HAS_PISTON), "and a piston");
		ItemStack gasket = new ItemStack(CEItems.RUBBER_GASKET.get());
		helper.useBlock(TestEngine.HEAD, player(helper, gasket));
		helper.assertTrue(gasket.isEmpty(), "The head takes a gasket");
		GameTestConfig.wearMode(true);
		helper.assertTrue(!cylinder.showsThrown(), "The wreck the parts went in over does not come back");
		helper.assertTrue(!head.showsBroken(), "Nor does the break the gasket went in over");
		helper.succeed();
	}

	/**
	 * The old controller is ticked past two wearing steps right after the switch, before its replacement is built. The film override is
	 * read once by every wearing step, so it counts them.
	 */
	@GameTest(template = "empty", timeoutTicks = 400, batch = SWITCH_TICK)
	public static void theTickThatSeesTheModeSwitchedOnWearsNothingAndTheRebuiltEngineHasTheSavedFault(GameTestHelper helper) {
		GameTestConfig.wearMode(false);
		TestEngine.build(helper.getLevel(), helper.absolutePos(net.minecraft.core.BlockPos.ZERO), true);
		CylinderBlockEntity cylinder = helper.getBlockEntity(TestEngine.CYLINDER);
		cylinder.seize();
		helper.runAtTickTime(100, () -> {
			CrankshaftBlockEntity crank = helper.getBlockEntity(TestEngine.PRIMARY);
			var old = crank.controller();
			helper.assertTrue(old != null && summary(helper).failures() == 0, "No failure counts with the mode off: " + summary(helper));
			GameTestConfig.wearMode(true);
			int[] steps = new int[1];
			WearKeeper.filmOverride = () -> {
				steps[0]++;
				return 1;
			};
			try {
				for (int tick = 0; tick < 2 * WearKeeper.INTERVAL; tick++)
					old.tick();
			} finally {
				WearKeeper.filmOverride = null;
			}
			helper.assertTrue(steps[0] == 0, "An engine built with the mode off ran " + steps[0] + " wearing steps with it on");
			helper.runAfterDelay(1, () -> {
				helper.assertTrue(crank.controller() != old, "It is built again on the tick that saw the switch");
				helper.runAfterDelay(12, () -> {
					helper.assertTrue(summary(helper).failures() != 0 && summary(helper).firing() == 0,
						"and the new one has the saved seizure: " + summary(helper));
					helper.succeed();
				});
			});
		});
	}

	/** NeoForge sends the config only to a player who is joining, so the server has to say it again after a switch. */
	@GameTest(template = "empty", batch = PLAYERS)
	public static void theServerTellsItsPlayersWhenTheModeIsSwitched(GameTestHelper helper) {
		GameTestConfig.wearMode(true);
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(Boolean.TRUE.equals(com.createengines.config.WearModeSync.told()), "Told it is on");
			GameTestConfig.wearMode(false);
			helper.runAfterDelay(2, () -> {
				helper.assertTrue(Boolean.FALSE.equals(com.createengines.config.WearModeSync.told()), "Told it is off");
				helper.succeed();
			});
		});
	}

	@GameTest(template = "empty", timeoutTicks = 1200, batch = ROTOR)
	public static void switchingTheModeOffRunsARotorWithFailedSeals(GameTestHelper helper) {
		GameTestConfig.wearMode(true);
		TestEngine.buildRotary(helper.getLevel(), helper.absolutePos(net.minecraft.core.BlockPos.ZERO), true);
		RotorHousingBlockEntity housing = helper.getBlockEntity(TestEngine.HOUSING);
		housing.addApexWear(0.9);
		housing.failApex();
		helper.runAtTickTime(200, () -> {
			helper.assertTrue(summary(helper).firing() == 0, "Stopped with the mode on: " + summary(helper));
			GameTestConfig.wearMode(false);
		});
		helper.runAtTickTime(1100, () -> {
			helper.assertTrue(summary(helper).firing() >= 1, "Fires with the mode off: " + summary(helper));
			helper.assertTrue(!housing.showsApexFailed() && housing.apexFailed(), "The failure is saved and does not show");
			helper.succeed();
		});
	}

	@GameTest(template = "empty", batch = ITEMS)
	public static void aWornPartShowsNoWearWithTheModeOffAndKeepsIt(GameTestHelper helper) {
		ItemStack piston = WearService.withWear(CEItems.PISTON.asStack(), 0.6);
		GameTestConfig.wearMode(false);
		helper.assertTrue(!piston.getItem().isBarVisible(piston), "No wear bar with the mode off");
		helper.assertTrue(WearService.wearOf(piston) == 0.6, "The wear stays on the part");
		GameTestConfig.wearMode(true);
		helper.assertTrue(piston.getItem().isBarVisible(piston), "and shows again with the mode on");
		helper.succeed();
	}
}
