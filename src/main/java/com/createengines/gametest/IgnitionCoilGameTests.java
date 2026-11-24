package com.createengines.gametest;

import java.util.List;

import com.createengines.CreateEngines;
import com.createengines.content.battery.BatteryBlockEntity;
import com.createengines.content.head.CylinderHeadBlock;
import com.createengines.engine.BatteryIgnition;
import com.createengines.engine.sim.IgnitionCoil;
import com.createengines.engine.topology.EngineLayout;
import com.createengines.engine.topology.EngineScanner;
import com.createengines.registry.CEBlocks;
import com.createengines.registry.CEItems;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The high-energy ignition coil: a part for a head that already has a spark plug, which spends battery charge (never a reserve's)
 * on a hotter spark. Fitting it, removing it, what the scanner makes of it and where its charge comes from.
 */
@GameTestHolder(CreateEngines.MOD_ID)
@PrefixGameTestTemplate(false)
public class IgnitionCoilGameTests {

	private static final String BATCH = "ignition-coil";

	private static final BlockPos HEAD = new BlockPos(2, 1, 2);
	private static final BlockPos A = new BlockPos(6, 1, 1);
	private static final BlockPos B = new BlockPos(6, 1, 2);

	private static BlockState head(boolean injector, boolean plug, boolean coil) {
		return CEBlocks.CYLINDER_HEAD.getDefaultState()
			.setValue(CylinderHeadBlock.HAS_INJECTOR, injector)
			.setValue(CylinderHeadBlock.HAS_SPARK_PLUG, plug)
			.setValue(CylinderHeadBlock.HAS_COIL, coil);
	}

	private static Player survivalPlayer(GameTestHelper helper) {
		return helper.makeMockPlayer(GameType.SURVIVAL);
	}

	@GameTest(template = "empty", batch = BATCH)
	public static void aCoilWillNotFitAHeadWithNoPlug(GameTestHelper helper) {
		helper.setBlock(HEAD, head(true, false, false));
		Player player = survivalPlayer(helper);
		ItemStack coil = new ItemStack(CEItems.IGNITION_COIL.get(), 2);
		player.setItemInHand(InteractionHand.MAIN_HAND, coil);
		helper.useBlock(HEAD, player);
		helper.assertTrue(!helper.getBlockState(HEAD).getValue(CylinderHeadBlock.HAS_COIL), "The coil sits on a plug, and there is none");
		helper.assertTrue(coil.getCount() == 2, "so it stays in the hand: " + coil.getCount());
		helper.succeed();
	}

	@GameTest(template = "empty", batch = BATCH)
	public static void aCoilFitsAHeadThatHasAPlugAndCostsOne(GameTestHelper helper) {
		helper.setBlock(HEAD, head(false, true, false));
		Player player = survivalPlayer(helper);
		ItemStack coil = new ItemStack(CEItems.IGNITION_COIL.get(), 2);
		player.setItemInHand(InteractionHand.MAIN_HAND, coil);
		helper.useBlock(HEAD, player);
		BlockState state = helper.getBlockState(HEAD);
		helper.assertTrue(state.getValue(CylinderHeadBlock.HAS_COIL), "With a plug in, the coil goes on");
		helper.assertTrue(coil.getCount() == 1, "using up one: " + coil.getCount());
		helper.assertTrue(state.getValue(CylinderHeadBlock.HAS_SPARK_PLUG), "and the plug is still there");
		helper.succeed();
	}

	@GameTest(template = "empty", batch = BATCH)
	public static void aPrecisionHeadTakesACoilToo(GameTestHelper helper) {
		helper.setBlock(HEAD, CEBlocks.PRECISION_CYLINDER_HEAD.getDefaultState()
			.setValue(CylinderHeadBlock.HAS_SPARK_PLUG, true));
		Player player = survivalPlayer(helper);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(CEItems.IGNITION_COIL.get()));
		helper.useBlock(HEAD, player);
		helper.assertTrue(helper.getBlockState(HEAD).getValue(CylinderHeadBlock.HAS_COIL), "A coil goes on the precision head's plug");
		helper.succeed();
	}

	@GameTest(template = "empty", batch = BATCH)
	public static void theCoilComesOffBeforeThePlug(GameTestHelper helper) {
		helper.setBlock(HEAD, head(true, true, true));
		Player player = survivalPlayer(helper);
		player.setShiftKeyDown(true);
		player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		helper.useBlock(HEAD, player);
		BlockState state = helper.getBlockState(HEAD);
		helper.assertTrue(!state.getValue(CylinderHeadBlock.HAS_COIL) && state.getValue(CylinderHeadBlock.HAS_SPARK_PLUG),
			"The coil is taken off first: " + state);
		helper.assertTrue(player.getInventory().countItem(CEItems.IGNITION_COIL.get()) == 1, "and handed back");
		helper.succeed();
	}

	@GameTest(template = "empty", batch = BATCH)
	public static void aBrokenHeadDropsItsCoil(GameTestHelper helper) {
		helper.setBlock(HEAD, head(false, true, true));
		helper.setBlock(HEAD, Blocks.AIR);
		helper.assertItemEntityPresent(CEItems.IGNITION_COIL.get(), HEAD, 3);
		helper.succeed();
	}

	@GameTest(template = "empty", batch = BATCH)
	public static void aSchematicOfAFittedHeadPricesTheCoil(GameTestHelper helper) {
		BlockState state = head(false, true, true);
		var needed = ((CylinderHeadBlock) state.getBlock()).getRequiredItems(state, null);
		boolean found = false;
		for (var required : needed.getRequiredItems())
			found |= required.stack.is(CEItems.IGNITION_COIL.get());
		helper.assertTrue(found, "A fitted coil is a part a schematic must be given, as the plug is");
		helper.succeed();
	}

	private static EngineLayout scan(GameTestHelper helper) {
		EngineLayout layout = EngineScanner.scan(helper.getLevel(), helper.absolutePos(TestEngine.PRIMARY));
		if (layout == null || layout.cylinders().isEmpty())
			helper.fail("the test engine should scan to a layout with a cylinder");
		return layout;
	}

	@GameTest(template = "empty", batch = BATCH)
	public static void theScannerCountsACoilOnlyOnAPlug(GameTestHelper helper) {
		TestEngine.buildSpark(helper.getLevel(), helper.absolutePos(BlockPos.ZERO), true);
		helper.assertTrue(!scan(helper).cylinders().get(0).ignitionCoil(), "A head with no coil has none");
		helper.setBlock(TestEngine.HEAD, helper.getBlockState(TestEngine.HEAD)
			.setValue(CylinderHeadBlock.HAS_COIL, true));
		helper.assertTrue(scan(helper).cylinders().get(0).ignitionCoil(), "Fitted, it is counted");
		helper.setBlock(TestEngine.HEAD, helper.getBlockState(TestEngine.HEAD)
			.setValue(CylinderHeadBlock.HAS_SPARK_PLUG, false));
		helper.assertTrue(!scan(helper).cylinders().get(0).ignitionCoil(),
			"A state with a coil and no plug (only a command makes one) gets no bonus");
		helper.succeed();
	}

	private static BatteryBlockEntity battery(GameTestHelper helper, BlockPos pos, float charge) {
		helper.setBlock(pos, CEBlocks.BATTERY.getDefaultState());
		BatteryBlockEntity battery = helper.getBlockEntity(pos);
		battery.charge(charge);
		return battery;
	}

	@GameTest(template = "empty", batch = BATCH)
	public static void aCoiledSparkSpendsAHalfSparkMoreFromTheBank(GameTestHelper helper) {
		BatteryBlockEntity a = battery(helper, A, 10);
		BatteryIgnition supply = new BatteryIgnition(helper.getLevel(), List.of(helper.absolutePos(A)));
		IgnitionCoil coil = supply.coil();
		helper.assertTrue(coil.installed() && coil.powered(), "A charged bank can power a coil");
		helper.assertTrue(coil.fire(), "and a spark is coiled");
		helper.assertTrue(Math.abs(a.charge() - (10 - IgnitionCoil.COST)) < 1e-3, "for " + IgnitionCoil.COST + " sparks: " + a.charge());
		helper.succeed();
	}

	@GameTest(template = "empty", batch = BATCH)
	public static void aFlatBankLeavesTheSparkThePlugsOwn(GameTestHelper helper) {
		BatteryBlockEntity a = battery(helper, A, 0.2f);
		BatteryIgnition supply = new BatteryIgnition(helper.getLevel(), List.of(helper.absolutePos(A)));
		helper.assertTrue(!supply.coil().powered() && !supply.coil().fire(), "Under half a spark there is nothing to spare");
		helper.assertTrue(Math.abs(a.charge() - 0.2f) < 1e-3, "and none is taken: " + a.charge());
		helper.succeed();
	}

	@GameTest(template = "empty", batch = BATCH)
	public static void aCoilCannotTakeAReservesCharge(GameTestHelper helper) {
		BatteryBlockEntity open = battery(helper, A, 0);
		BatteryBlockEntity held = battery(helper, B, 50);
		held.setReserve(true);
		BatteryIgnition supply = new BatteryIgnition(helper.getLevel(), List.of(helper.absolutePos(A), helper.absolutePos(B)));
		helper.assertTrue(!supply.coil().powered(), "The only charge is in a reserve, which a coil may not use");
		helper.assertTrue(!supply.coil().fire() && Math.abs(held.charge() - 50) < 1e-3, "so it takes none: " + held.charge());
		helper.assertTrue(supply.powered() && supply.spark(), "while the plug's own spark still draws on it");
		open.charge(10);
		helper.assertTrue(supply.coil().fire(), "With charge in an open battery the coil works again");
		helper.assertTrue(Math.abs(open.charge() - (10 - IgnitionCoil.COST)) < 1e-3, "and takes it from there: " + open.charge());
		helper.succeed();
	}
}
