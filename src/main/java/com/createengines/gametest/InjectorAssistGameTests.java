package com.createengines.gametest;

import java.util.ArrayList;
import java.util.List;

import com.createengines.CreateEngines;
import com.createengines.content.battery.BatteryBlockEntity;
import com.createengines.content.head.CylinderHeadBlock;
import com.createengines.engine.BatteryIgnition;
import com.createengines.engine.sim.InjectorAssist;
import com.createengines.engine.topology.EngineLayout;
import com.createengines.engine.topology.EngineScanner;
import com.createengines.registry.CEBlocks;
import com.createengines.registry.CEItems;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import com.createengines.content.battery.BatteryBank;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The electric injector assist: a part for a head that already has an injector, which spends battery charge (never a reserve's) to
 * make each shot a little better. Fitting it, removing it, what the scanner makes of it and where its charge comes from.
 */
@GameTestHolder(CreateEngines.MOD_ID)
@PrefixGameTestTemplate(false)
public class InjectorAssistGameTests {

	private static final String BATCH = "injector-assist";

	private static final BlockPos HEAD = new BlockPos(2, 1, 2);
	private static final BlockPos A = new BlockPos(6, 1, 1);
	private static final BlockPos B = new BlockPos(6, 1, 2);

	private static BlockState head(boolean injector, boolean plug, boolean assist) {
		return CEBlocks.CYLINDER_HEAD.getDefaultState()
			.setValue(CylinderHeadBlock.HAS_INJECTOR, injector)
			.setValue(CylinderHeadBlock.HAS_SPARK_PLUG, plug)
			.setValue(CylinderHeadBlock.HAS_ASSIST, assist);
	}

	private static BlockState stateAt(GameTestHelper helper, BlockPos pos) {
		return helper.getBlockState(pos);
	}

	private static Player survivalPlayer(GameTestHelper helper) {
		return helper.makeMockPlayer(GameType.SURVIVAL);
	}

	@GameTest(template = "empty", batch = BATCH)
	public static void anAssistWillNotFitAHeadWithNoInjector(GameTestHelper helper) {
		helper.setBlock(HEAD, head(false, false, false));
		Player player = survivalPlayer(helper);
		ItemStack assist = new ItemStack(CEItems.INJECTOR_ASSIST.get(), 2);
		player.setItemInHand(InteractionHand.MAIN_HAND, assist);
		helper.useBlock(HEAD, player);
		helper.assertTrue(!stateAt(helper, HEAD).getValue(CylinderHeadBlock.HAS_ASSIST), "The assist sits on an injector, and there is none");
		helper.assertTrue(assist.getCount() == 2, "so it stays in the hand: " + assist.getCount());
		helper.succeed();
	}

	@GameTest(template = "empty", batch = BATCH)
	public static void anAssistFitsAHeadThatHasAnInjectorAndCostsOne(GameTestHelper helper) {
		helper.setBlock(HEAD, head(true, false, false));
		Player player = survivalPlayer(helper);
		ItemStack assist = new ItemStack(CEItems.INJECTOR_ASSIST.get(), 2);
		player.setItemInHand(InteractionHand.MAIN_HAND, assist);
		helper.useBlock(HEAD, player);
		helper.assertTrue(stateAt(helper, HEAD).getValue(CylinderHeadBlock.HAS_ASSIST), "With an injector in, the assist goes on");
		helper.assertTrue(assist.getCount() == 1, "using up one: " + assist.getCount());
		helper.assertTrue(stateAt(helper, HEAD).getValue(CylinderHeadBlock.HAS_INJECTOR), "and the injector is still there");
		helper.succeed();
	}

	@GameTest(template = "empty", batch = BATCH)
	public static void aPrecisionInjectorTakesAnAssistToo(GameTestHelper helper) {
		helper.setBlock(HEAD, head(true, false, false).setValue(CylinderHeadBlock.FINE_INJECTOR, true));
		Player player = survivalPlayer(helper);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(CEItems.INJECTOR_ASSIST.get()));
		helper.useBlock(HEAD, player);
		BlockState state = stateAt(helper, HEAD);
		helper.assertTrue(state.getValue(CylinderHeadBlock.HAS_ASSIST) && state.getValue(CylinderHeadBlock.FINE_INJECTOR),
			"An assist on a precision injector leaves it precision: " + state);
		helper.succeed();
	}

	@GameTest(template = "empty", batch = BATCH)
	public static void theAssistComesOffBeforeThePlugAndTheInjector(GameTestHelper helper) {
		helper.setBlock(HEAD, head(true, true, true));
		Player player = survivalPlayer(helper);
		player.setShiftKeyDown(true);
		player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		helper.useBlock(HEAD, player);
		BlockState state = stateAt(helper, HEAD);
		helper.assertTrue(!state.getValue(CylinderHeadBlock.HAS_ASSIST) && state.getValue(CylinderHeadBlock.HAS_SPARK_PLUG)
			&& state.getValue(CylinderHeadBlock.HAS_INJECTOR), "The assist is taken off first: " + state);
		helper.assertTrue(player.getInventory().countItem(CEItems.INJECTOR_ASSIST.get()) == 1, "and handed back");
		// The part handed back lands in the hand that just clicked, and a click with an assist in it would fit it again
		player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		helper.useBlock(HEAD, player);
		// The plug that came off lands in the hand too, and a click with a plug in hand would fit it straight back
		player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		helper.useBlock(HEAD, player);
		state = stateAt(helper, HEAD);
		helper.assertTrue(!state.getValue(CylinderHeadBlock.HAS_INJECTOR) && !state.getValue(CylinderHeadBlock.HAS_SPARK_PLUG),
			"then the plug, then the injector: " + state);
		helper.succeed();
	}

	@GameTest(template = "empty", batch = BATCH)
	public static void aBrokenHeadDropsItsAssist(GameTestHelper helper) {
		helper.setBlock(HEAD, head(true, false, true));
		helper.setBlock(HEAD, Blocks.AIR);
		helper.assertItemEntityPresent(CEItems.INJECTOR_ASSIST.get(), HEAD, 3);
		helper.succeed();
	}

	@GameTest(template = "empty", batch = BATCH)
	public static void aSchematicOfAFittedHeadPricesTheAssist(GameTestHelper helper) {
		BlockState state = head(true, false, true);
		var needed = ((CylinderHeadBlock) state.getBlock()).getRequiredItems(state, null);
		boolean found = false;
		for (var required : needed.getRequiredItems())
			found |= required.stack.is(CEItems.INJECTOR_ASSIST.get());
		helper.assertTrue(found, "A fitted assist is a part a schematic must be given, as the injector is");
		helper.succeed();
	}

	private static EngineLayout scan(GameTestHelper helper) {
		EngineLayout layout = EngineScanner.scan(helper.getLevel(), helper.absolutePos(TestEngine.PRIMARY));
		if (layout == null || layout.cylinders().isEmpty())
			helper.fail("the test engine should scan to a layout with a cylinder");
		return layout;
	}

	@GameTest(template = "empty", batch = BATCH)
	public static void theScannerCountsAnAssistOnlyOnAnInjector(GameTestHelper helper) {
		TestEngine.buildSpark(helper.getLevel(), helper.absolutePos(BlockPos.ZERO), true);
		helper.assertTrue(!scan(helper).cylinders().get(0).injectorAssist(), "A head with no assist has none");
		helper.setBlock(TestEngine.HEAD, helper.getBlockState(TestEngine.HEAD)
			.setValue(CylinderHeadBlock.HAS_ASSIST, true));
		helper.assertTrue(scan(helper).cylinders().get(0).injectorAssist(), "Fitted, it is counted");
		helper.setBlock(TestEngine.HEAD, helper.getBlockState(TestEngine.HEAD)
			.setValue(CylinderHeadBlock.HAS_INJECTOR, false));
		helper.assertTrue(!scan(helper).cylinders().get(0).injectorAssist(),
			"A state with an assist and no injector (only a command makes one) gets no bonus");
		helper.succeed();
	}

	private static BatteryBlockEntity battery(GameTestHelper helper, BlockPos pos, float charge) {
		helper.setBlock(pos, CEBlocks.BATTERY.getDefaultState());
		BatteryBlockEntity battery = helper.getBlockEntity(pos);
		battery.charge(charge);
		return battery;
	}

	@GameTest(template = "empty", batch = BATCH)
	public static void anAssistedShotSpendsAHalfSparkFromTheBank(GameTestHelper helper) {
		BatteryBlockEntity a = battery(helper, A, 10);
		BatteryIgnition supply = new BatteryIgnition(helper.getLevel(), List.of(helper.absolutePos(A)));
		InjectorAssist assist = supply.assist();
		helper.assertTrue(assist.installed() && assist.powered(), "A charged bank can power an assist");
		helper.assertTrue(assist.fire(), "and a shot is assisted");
		helper.assertTrue(Math.abs(a.charge() - (10 - InjectorAssist.COST)) < 1e-3, "for " + InjectorAssist.COST + " sparks: " + a.charge());
		helper.succeed();
	}

	@GameTest(template = "empty", batch = BATCH)
	public static void aFlatBankLeavesTheShotUnassisted(GameTestHelper helper) {
		BatteryBlockEntity a = battery(helper, A, 0.2f);
		BatteryIgnition supply = new BatteryIgnition(helper.getLevel(), List.of(helper.absolutePos(A)));
		helper.assertTrue(!supply.assist().powered() && !supply.assist().fire(), "Under half a spark there is nothing to spare");
		helper.assertTrue(Math.abs(a.charge() - 0.2f) < 1e-3, "and none is taken: " + a.charge());
		helper.succeed();
	}

	@GameTest(template = "empty", batch = BATCH)
	public static void anAssistCannotTakeAReservesCharge(GameTestHelper helper) {
		BatteryBlockEntity open = battery(helper, A, 0);
		BatteryBlockEntity held = battery(helper, B, 50);
		held.setReserve(true);
		BatteryIgnition supply = new BatteryIgnition(helper.getLevel(), List.of(helper.absolutePos(A), helper.absolutePos(B)));
		helper.assertTrue(!supply.assist().powered(), "The only charge is in a reserve, which an assist may not use");
		helper.assertTrue(!supply.assist().fire() && Math.abs(held.charge() - 50) < 1e-3, "so it takes none: " + held.charge());
		// A spark plug can: the reserve is for exactly that
		helper.assertTrue(supply.powered() && supply.spark(), "while a spark still draws on it");
		helper.assertTrue(held.charge() < 50, "using some of it: " + held.charge());
		open.charge(10);
		helper.assertTrue(supply.assist().fire(), "With charge in an open battery the assist works again");
		helper.assertTrue(Math.abs(open.charge() - (10 - InjectorAssist.COST)) < 1e-3, "and takes it from there: " + open.charge());
		helper.succeed();
	}

	/** The reserve lines a battery's goggles would show, as lang keys (the goggle text itself loads client classes, so a server test cannot call it). */
	private static List<String> goggles(BatteryBlockEntity battery, boolean sneaking) {
		return com.createengines.engine.sim.ReserveLines.keys(battery.isReserve(), BatteryBank.reserveCount(battery.bank()), sneaking);
	}

	private static boolean has(List<String> lines, String key) {
		return lines.stream()
			.anyMatch(line -> line.contains(key));
	}

	@GameTest(template = "empty", batch = BATCH)
	public static void everyBatterysGogglesSayWhetherItIsAReserve(GameTestHelper helper) {
		BatteryBlockEntity plain = battery(helper, A, 50);
		List<String> lines = goggles(plain, false);
		helper.assertTrue(has(lines, "goggles.battery.reserve_off"), "A plain battery says its reserve is off: " + lines);
		helper.assertTrue(!has(lines, "goggles.battery.reserve_split"), "and a bank with no reserve has nothing to split: " + lines);
		helper.assertTrue(!has(lines, "goggles.battery.reserve_hint"), "The wrench hint waits for a sneaking player");
		plain.setReserve(true);
		lines = goggles(plain, false);
		helper.assertTrue(!has(lines, "goggles.battery.reserve_off") && lines.stream().anyMatch(line -> line.equals("goggles.battery.reserve")),
			"A reserve says it is one: " + lines);
		helper.assertTrue(has(lines, "goggles.battery.reserve_split"), "and says what the bank holds back: " + lines);
		helper.assertTrue(has(goggles(plain, true), "goggles.battery.reserve_hint"), "Sneaking adds how to change it");
		helper.succeed();
	}

	@GameTest(template = "empty", batch = BATCH)
	public static void aPlainBatteryBesideAReserveStillSaysItIsOff(GameTestHelper helper) {
		BatteryBlockEntity plain = battery(helper, A, 50);
		BatteryBlockEntity held = battery(helper, B, 50);
		held.setReserve(true);
		List<String> lines = goggles(plain, false);
		helper.assertTrue(has(lines, "goggles.battery.reserve_off"), "This one gives to everything: " + lines);
		helper.assertTrue(has(lines, "goggles.battery.reserve_split"), "and the bank line still says one of two is a reserve: " + lines);
		helper.succeed();
	}
}
