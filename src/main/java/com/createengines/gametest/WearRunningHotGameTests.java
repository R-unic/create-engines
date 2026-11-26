package com.createengines.gametest;

import com.createengines.CreateEngines;
import com.createengines.content.WearService;
import com.createengines.content.crankshaft.CrankshaftBlockEntity;
import com.createengines.content.cylinder.CylinderBlock;
import com.createengines.content.cylinder.CylinderBlockEntity;
import com.createengines.content.head.CylinderHeadBlock;
import com.createengines.content.head.CylinderHeadBlockEntity;
import com.createengines.content.turbo.TurbochargerBlockEntity;
import com.createengines.engine.EngineSummary;
import com.createengines.engine.WearKeeper;
import com.createengines.engine.sim.Wear;
import com.createengines.engine.sim.WearFailures;
import com.createengines.engine.topology.CylinderFault;
import com.createengines.registry.CEBlocks;
import com.createengines.registry.CEComponents;
import com.createengines.registry.CEItems;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** The wear mode's second part (2.4) in the world: heat, the warped head, the three rods, spark plug wear and turbocharger wear. */
@GameTestHolder(CreateEngines.MOD_ID)
@PrefixGameTestTemplate(false)
public class WearRunningHotGameTests {

	// The temperature override and the forced roll are one setting for the whole server, so each test that uses one runs alone
	private static final String WARPS = "hot-warps";
	private static final String WARPS_OFF = "hot-warps-off";
	private static final String BLOWS = "hot-blows";
	private static final String SEIZES = "hot-seizes";
	private static final String PLUGS = "hot-plugs";
	private static final String PARTS = "hot-parts";

	private static final BlockPos CYLINDER = TestEngine.CYLINDER;
	private static final BlockPos HEAD = TestEngine.HEAD;
	private static final BlockPos AT = new BlockPos(4, 2, 3);

	private static void restore() {
		GameTestConfig.wearMode(false);
		WearKeeper.roller = null;
		WearKeeper.filmOverride = null;
		WearKeeper.temperatureOverride = null;
	}

	@AfterBatch(batch = WARPS)
	public static void afterWarps(ServerLevel level) {
		restore();
	}

	@AfterBatch(batch = WARPS_OFF)
	public static void afterWarpsOff(ServerLevel level) {
		restore();
	}

	@AfterBatch(batch = BLOWS)
	public static void afterBlows(ServerLevel level) {
		restore();
	}

	@AfterBatch(batch = SEIZES)
	public static void afterSeizes(ServerLevel level) {
		restore();
	}

	@AfterBatch(batch = PLUGS)
	public static void afterPlugs(ServerLevel level) {
		restore();
	}

	@AfterBatch(batch = PARTS)
	public static void afterParts(ServerLevel level) {
		restore();
	}

	private static CylinderBlockEntity cylinder(GameTestHelper helper) {
		return helper.getBlockEntity(CYLINDER);
	}

	private static CylinderHeadBlockEntity head(GameTestHelper helper) {
		return helper.getBlockEntity(HEAD);
	}

	private static EngineSummary summary(GameTestHelper helper) {
		CrankshaftBlockEntity crank = helper.getBlockEntity(TestEngine.PRIMARY);
		return crank.summary();
	}

	private static Player player(GameTestHelper helper, ItemStack held) {
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		player.setItemInHand(InteractionHand.MAIN_HAND, held);
		return player;
	}

	/** A running diesel with the mode on, held critically hot, where no roll ever comes up. */
	private static void buildHot(GameTestHelper helper, boolean wearMode) {
		GameTestConfig.wearMode(wearMode);
		WearKeeper.roller = () -> 0.999999999;
		WearKeeper.filmOverride = () -> 1;
		WearKeeper.temperatureOverride = () -> Wear.CRITICAL + Wear.CRITICAL_SPAN;
		TestEngine.build(helper.getLevel(), helper.absolutePos(BlockPos.ZERO), true);
		head(helper).setHeatSoak(Wear.WARP_AFTER - 3);
	}

	@GameTest(template = "empty", timeoutTicks = 600, batch = WARPS)
	public static void aHeadLeftPastCriticalWarpsAndItsCylinderStillFires(GameTestHelper helper) {
		buildHot(helper, true);
		helper.runAtTickTime(500, () -> {
			helper.assertTrue(head(helper).warped(), "the head warped, soak " + head(helper).heatSoak());
			int faults = cylinder(helper).status()
				.faults();
			helper.assertTrue(CylinderFault.WARPED_HEAD.in(faults), "and the cylinder says why: " + faults);
			helper.assertTrue(summary(helper).firing() == 1, "it leaks but still fires: " + summary(helper));
			helper.assertTrue(WearFailures.count(summary(helper).failures(), WearFailures.Kind.HEAD_WARPED) == 1,
				"counted for the Black Box: " + summary(helper).failures());
			var drops = Block.getDrops(helper.getBlockState(HEAD), helper.getLevel(), helper.absolutePos(HEAD), head(helper));
			helper.assertTrue(drops.isEmpty(), "a warped head is scrap: " + drops);
			helper.assertTrue(!head(helper).service(), "and no kit flattens it");
			helper.succeed();
		});
	}

	@GameTest(template = "empty", timeoutTicks = 500, batch = WARPS_OFF)
	public static void withTheModeOffNoHeadWarpsHoweverHot(GameTestHelper helper) {
		buildHot(helper, false);
		helper.runAtTickTime(400, () -> {
			helper.assertTrue(!head(helper).warped(), "nothing warps with the mode off");
			helper.assertTrue(cylinder(helper).pistonWear() == 0 && !cylinder(helper).seized() && !head(helper).blown(), "nor anything else");
			helper.succeed();
		});
	}

	@GameTest(template = "empty", timeoutTicks = 600, batch = BLOWS)
	public static void aSoundGasketCanBlowFromHeatAlone(GameTestHelper helper) {
		GameTestConfig.wearMode(true);
		// Low enough to beat the heat's chance, and no part is worn enough to have a chance of its own
		WearKeeper.roller = () -> 0;
		WearKeeper.filmOverride = () -> 1;
		WearKeeper.temperatureOverride = () -> Wear.CRITICAL;
		TestEngine.build(helper.getLevel(), helper.absolutePos(BlockPos.ZERO), true);
		helper.runAtTickTime(500, () -> {
			helper.assertTrue(head(helper).blown(), "blown, with the gasket worn only " + head(helper).gasketWear());
			helper.assertTrue(!cylinder(helper).thrown(), "and no sound rod was thrown");
			helper.succeed();
		});
	}

	@GameTest(template = "empty", timeoutTicks = 600, batch = SEIZES)
	public static void anOiledPistonSeizesWhenTheBlockStaysCritical(GameTestHelper helper) {
		buildHot(helper, true);
		head(helper).setHeatSoak(0);
		cylinder(helper).setDryTime(Wear.SEIZE_AFTER - 1);
		helper.runAtTickTime(500, () -> {
			helper.assertTrue(cylinder(helper).seized(), "seized from heat with good oil: " + cylinder(helper).dryTime());
			helper.succeed();
		});
	}

	@GameTest(template = "empty", timeoutTicks = 500, batch = PLUGS)
	public static void aRunningSparkEnginesPlugWears(GameTestHelper helper) {
		GameTestConfig.wearMode(true);
		TestEngine.buildSpark(helper.getLevel(), helper.absolutePos(BlockPos.ZERO), true);
		helper.runAtTickTime(450, () -> {
			helper.assertTrue(head(helper).plugWear() > 0 && head(helper).plugWear() < 0.01, "a little: " + head(helper).plugWear());
			helper.succeed();
		});
	}

	@GameTest(template = "empty", batch = PARTS)
	public static void aLightOrHeavyRodGoesInAndComesOutAsItself(GameTestHelper helper) {
		for (Wear.Rod kind : new Wear.Rod[] {Wear.Rod.LIGHT, Wear.Rod.HEAVY}) {
			helper.setBlock(AT, CEBlocks.CYLINDER.get()
				.defaultBlockState()
				.setValue(CylinderBlock.HAS_PISTON, true));
			helper.useBlock(AT, player(helper, WearService.withWear(WearService.itemFor(kind), 0.25)));
			CylinderBlockEntity body = helper.getBlockEntity(AT);
			helper.assertTrue(helper.getBlockState(AT)
				.getValue(CylinderBlock.HAS_ROD), kind + " went in");
			helper.assertTrue(body.rodKind() == kind && body.rodWear() == 0.25, "as itself and as worn: " + body.rodKind() + " " + body.rodWear());
			var priced = ((CylinderBlock) helper.getBlockState(AT)
				.getBlock()).getRequiredItems(helper.getBlockState(AT), body)
				.getRequiredItems()
				.stream()
				.map(required -> required.stack.getItem())
				.toList();
			helper.assertTrue(priced.contains(WearService.itemFor(kind)
				.getItem()) && !priced.contains(CEItems.CONNECTING_ROD.get()), "a schematic asks for the rod that is in: " + priced);
			Player taker = player(helper, ItemStack.EMPTY);
			taker.setShiftKeyDown(true);
			helper.useBlock(AT, taker);
			ItemStack out = ItemStack.EMPTY;
			for (int slot = 0; slot < taker.getInventory()
				.getContainerSize(); slot++) {
				ItemStack stack = taker.getInventory()
					.getItem(slot);
				if (WearService.rodOf(stack) != null)
					out = stack;
			}
			helper.assertTrue(WearService.rodOf(out) == kind && WearService.wearOf(out) == 0.25, "the same rod came out: " + out);
			helper.assertTrue(body.rodKind() == Wear.Rod.STANDARD, "and the cylinder forgot it");
			helper.setBlock(AT, Blocks.AIR);
		}
		helper.succeed();
	}

	@GameTest(template = "empty", batch = PARTS)
	public static void aPlugKeepsItsWearInAndOutOfAHeadAndAKitSetsALightOneRight(GameTestHelper helper) {
		GameTestConfig.wearMode(true);
		helper.setBlock(AT, CEBlocks.CYLINDER_HEAD.get()
			.defaultBlockState());
		helper.useBlock(AT, player(helper, WearService.withWear(CEItems.SPARK_PLUG.asStack(), 0.3)));
		CylinderHeadBlockEntity head = helper.getBlockEntity(AT);
		helper.assertTrue(helper.getBlockState(AT)
			.getValue(CylinderHeadBlock.HAS_SPARK_PLUG) && head.plugWear() == 0.3, "in, as worn: " + head.plugWear());
		helper.assertTrue(head.service() && head.plugWear() == Wear.REPAIRED_TO, "a kit sets it right: " + head.plugWear());
		Player taker = player(helper, ItemStack.EMPTY);
		taker.setShiftKeyDown(true);
		helper.useBlock(AT, taker);
		helper.assertTrue(taker.getInventory()
			.countItem(CEItems.SPARK_PLUG.get()) == 1 && head.plugWear() == 0, "and out, leaving the head with no plug wear");
		helper.succeed();
	}

	@GameTest(template = "empty", batch = PARTS)
	public static void aTurbochargerKeepsItsWearAndFailureAndMakesNoBoostFailed(GameTestHelper helper) {
		GameTestConfig.wearMode(true);
		for (var turbo : java.util.List.of(CEBlocks.TURBOCHARGER, CEBlocks.VARIABLE_TURBOCHARGER)) {
			helper.setBlock(AT, turbo.get()
				.defaultBlockState());
			TurbochargerBlockEntity be = helper.getBlockEntity(AT);
			be.addWear(0.6);
			helper.assertTrue(be.boostShare() < 1 && be.boostShare() > 0, "worn: " + be.boostShare());
			be.fail();
			helper.assertTrue(be.boostShare() == 0, "failed: " + be.boostShare());
			var drops = Block.getDrops(helper.getBlockState(AT), helper.getLevel(), helper.absolutePos(AT), be);
			helper.assertTrue(drops.size() == 1 && Double.valueOf(0.6)
				.equals(drops.get(0)
					.get(CEComponents.WEAR.get())) && Boolean.TRUE.equals(drops.get(0)
					.get(CEComponents.FAILED.get())), "the item keeps both: " + drops);
			helper.setBlock(AT, Blocks.AIR);
		}
		helper.succeed();
	}
}
