package com.createengines.gametest;

import java.util.List;

import com.createengines.CreateEngines;
import com.createengines.content.PartCompression;
import com.createengines.content.WearService;
import com.createengines.content.blueprint.BlueprintBuilder;
import com.createengines.content.crankshaft.CrankshaftBlockEntity;
import com.createengines.engine.EngineState;
import com.createengines.engine.EngineSummary;
import com.createengines.content.cylinder.CylinderBlock;
import com.createengines.content.cylinder.CylinderBlockEntity;
import com.createengines.content.rotor.RotorHousingBlock;
import com.createengines.registry.CEBlocks;
import com.createengines.registry.CEItems;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** High and low compression pistons and rotors, and piston rings, as a player handles them. */
@GameTestHolder(CreateEngines.MOD_ID)
@PrefixGameTestTemplate(false)
public class PistonVariantGameTests {

	private static final String BATCH = "piston-variants";
	private static final BlockPos AT = new BlockPos(4, 2, 3);

	private static Player holding(GameTestHelper helper, ItemStack stack) {
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		player.setItemInHand(InteractionHand.MAIN_HAND, stack);
		return player;
	}

	private static int carried(Player player, Item item) {
		int count = 0;
		for (int slot = 0; slot < player.getInventory()
			.getContainerSize(); slot++)
			if (player.getInventory()
				.getItem(slot)
				.is(item))
				count += player.getInventory()
					.getItem(slot)
					.getCount();
		return count;
	}

	@GameTest(template = "empty", batch = BATCH)
	public static void eachCompressionPistonGoesInAndComesOutAsItself(GameTestHelper helper) {
		for (var entry : List.of(CEItems.HIGH_COMPRESSION_PISTON, CEItems.LOW_COMPRESSION_PISTON)) {
			PartCompression wanted = entry == CEItems.HIGH_COMPRESSION_PISTON ? PartCompression.HIGH : PartCompression.LOW;
			helper.setBlock(AT, CEBlocks.CYLINDER.getDefaultState());
			Player player = holding(helper, entry.asStack());
			helper.useBlock(AT, player);
			var fitted = helper.getBlockState(AT);
			helper.assertTrue(fitted.getValue(CylinderBlock.HAS_PISTON) && fitted.getValue(CylinderBlock.FORGED_PISTON)
				&& fitted.getValue(CylinderBlock.COMPRESSION) == wanted, "fitted as " + fitted);
			helper.assertTrue(player.getMainHandItem()
				.isEmpty(), "the piston left the hand");
			player.setShiftKeyDown(true);
			helper.useBlock(AT, player);
			var emptied = helper.getBlockState(AT);
			helper.assertTrue(!emptied.getValue(CylinderBlock.HAS_PISTON) && !emptied.getValue(CylinderBlock.FORGED_PISTON)
				&& emptied.getValue(CylinderBlock.COMPRESSION) == PartCompression.STANDARD, "left as " + emptied);
			helper.assertTrue(carried(player, entry.get()) == 1 && carried(player, CEItems.FORGED_PISTON.get()) == 0,
				"the same piston comes back");
		}
		helper.succeed();
	}

	@GameTest(template = "empty", batch = BATCH)
	public static void aPistonTakesThreeRingsAndGivesThemBackBeforeItComesOut(GameTestHelper helper) {
		helper.setBlock(AT, CEBlocks.CYLINDER.getDefaultState());
		Player player = holding(helper, new ItemStack(CEItems.PISTON_RING.get(), 5));
		helper.useBlock(AT, player);
		helper.assertTrue(helper.getBlockState(AT)
			.getValue(CylinderBlock.RINGS) == 0 && player.getMainHandItem()
			.getCount() == 5, "no ring goes in without a piston");
		helper.setBlock(AT, CEBlocks.CYLINDER.getDefaultState()
			.setValue(CylinderBlock.HAS_PISTON, true)
			.setValue(CylinderBlock.HAS_ROD, true));
		for (int i = 0; i < 4; i++)
			helper.useBlock(AT, player);
		helper.assertTrue(helper.getBlockState(AT)
			.getValue(CylinderBlock.RINGS) == 3 && player.getMainHandItem()
			.getCount() == 2, "three rings and no more");
		player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		player.setShiftKeyDown(true);
		helper.useBlock(AT, player);
		helper.assertTrue(!helper.getBlockState(AT)
			.getValue(CylinderBlock.HAS_ROD) && helper.getBlockState(AT)
			.getValue(CylinderBlock.RINGS) == 3, "the rod comes out first");
		for (int left = 2; left >= 0; left--) {
			// A ring goes back to the first free slot, which is the hand: empty it again, keeping what came out
			ItemStack held = player.getMainHandItem()
				.copy();
			player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			if (!held.isEmpty())
				player.getInventory()
					.setItem(9 + left, held);
			helper.useBlock(AT, player);
			helper.assertTrue(helper.getBlockState(AT)
				.getValue(CylinderBlock.RINGS) == left && helper.getBlockState(AT)
				.getValue(CylinderBlock.HAS_PISTON), "ring by ring, then " + left);
		}
		helper.assertTrue(carried(player, CEItems.PISTON_RING.get()) >= 1, "the rings came back");
		helper.succeed();
	}

	@GameTest(template = "empty", batch = BATCH)
	public static void breakingACylinderDropsItsCompressionPistonWornAndItsRings(GameTestHelper helper) {
		helper.setBlock(AT, CEBlocks.CYLINDER.getDefaultState()
			.setValue(CylinderBlock.HAS_PISTON, true)
			.setValue(CylinderBlock.FORGED_PISTON, true)
			.setValue(CylinderBlock.COMPRESSION, PartCompression.LOW)
			.setValue(CylinderBlock.RINGS, 2));
		CylinderBlockEntity body = helper.getBlockEntity(AT);
		body.fitPiston(0.4);
		helper.getLevel()
			.destroyBlock(helper.absolutePos(AT), false);
		double wear = -1;
		int rings = 0;
		for (ItemEntity entity : helper.getLevel()
			.getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(AT)).inflate(2))) {
			ItemStack stack = entity.getItem();
			if (CEItems.LOW_COMPRESSION_PISTON.isIn(stack))
				wear = WearService.wearOf(stack);
			if (CEItems.PISTON_RING.isIn(stack))
				rings += stack.getCount();
			entity.discard();
		}
		helper.assertTrue(Math.abs(wear - 0.4) < 1e-9, "the low compression piston dropped at " + wear);
		helper.assertTrue(rings == 2, "rings dropped: " + rings);
		helper.succeed();
	}

	@GameTest(template = "empty", batch = BATCH)
	public static void aBuildFromAPlanPaysForThePistonFittedAndEveryRing(GameTestHelper helper) {
		var state = CEBlocks.CYLINDER.getDefaultState()
			.setValue(CylinderBlock.HAS_PISTON, true)
			.setValue(CylinderBlock.FORGED_PISTON, true)
			.setValue(CylinderBlock.COMPRESSION, PartCompression.HIGH)
			.setValue(CylinderBlock.RINGS, 3);
		var cost = BlueprintBuilder.cost(state);
		helper.assertTrue(cost.stream()
			.anyMatch(CEItems.HIGH_COMPRESSION_PISTON::isIn), "costs its piston: " + cost);
		helper.assertTrue(cost.stream()
			.noneMatch(CEItems.FORGED_PISTON::isIn), "and no forged one: " + cost);
		helper.assertTrue(cost.stream()
			.filter(CEItems.PISTON_RING::isIn)
			.mapToInt(ItemStack::getCount)
			.sum() == 3, "costs three rings: " + cost);
		var rotor = BlueprintBuilder.cost(CEBlocks.ROTOR_HOUSING.getDefaultState()
			.setValue(RotorHousingBlock.HAS_ROTOR, true)
			.setValue(RotorHousingBlock.COMPRESSION, PartCompression.LOW));
		helper.assertTrue(rotor.stream()
			.anyMatch(CEItems.LOW_COMPRESSION_ROTOR::isIn) && rotor.stream()
			.noneMatch(CEItems.ROTOR::isIn), "a housing costs the rotor fitted: " + rotor);
		helper.succeed();
	}

	@GameTest(template = "empty", batch = BATCH)
	public static void eachCompressionRotorGoesInAndComesOutAsItself(GameTestHelper helper) {
		for (var entry : List.of(CEItems.HIGH_COMPRESSION_ROTOR, CEItems.LOW_COMPRESSION_ROTOR)) {
			helper.setBlock(AT, CEBlocks.ROTOR_HOUSING.getDefaultState());
			Player player = holding(helper, entry.asStack());
			helper.useBlock(AT, player);
			var fitted = helper.getBlockState(AT);
			helper.assertTrue(fitted.getValue(RotorHousingBlock.HAS_ROTOR) && fitted.getValue(RotorHousingBlock.COMPRESSION)
				== (entry == CEItems.HIGH_COMPRESSION_ROTOR ? PartCompression.HIGH : PartCompression.LOW), "fitted as " + fitted);
			player.setShiftKeyDown(true);
			helper.useBlock(AT, player);
			helper.assertTrue(!helper.getBlockState(AT)
				.getValue(RotorHousingBlock.HAS_ROTOR) && helper.getBlockState(AT)
				.getValue(RotorHousingBlock.COMPRESSION) == PartCompression.STANDARD, "the housing is empty and stock again");
			helper.assertTrue(carried(player, entry.get()) == 1 && carried(player, CEItems.ROTOR.get()) == 0, "the same rotor comes back");
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 900)
	public static void aDieselOnALowCompressionPistonStartsAndRunsWithNoPlug(GameTestHelper helper) {
		runsOnItsOwn(helper, PartCompression.LOW);
	}

	@GameTest(template = "empty", timeoutTicks = 900)
	public static void aRingedHighCompressionEngineStartsAndRuns(GameTestHelper helper) {
		runsOnItsOwn(helper, PartCompression.HIGH);
	}

	/** The one cylinder test engine, which lights diesel by compression, refitted with this piston and three rings. */
	private static void runsOnItsOwn(GameTestHelper helper, PartCompression compression) {
		TestEngine.build(helper.getLevel(), helper.absolutePos(BlockPos.ZERO), true);
		helper.setBlock(TestEngine.CYLINDER, helper.getBlockState(TestEngine.CYLINDER)
			.setValue(CylinderBlock.FORGED_PISTON, true)
			.setValue(CylinderBlock.COMPRESSION, compression)
			.setValue(CylinderBlock.RINGS, 3));
		helper.runAtTickTime(300, () -> helper.setBlock(TestEngine.STARTER_POWER, Blocks.AIR));
		helper.runAtTickTime(700, () -> {
			CrankshaftBlockEntity crank = helper.getBlockEntity(TestEngine.PRIMARY);
			EngineSummary summary = crank.summary();
			helper.assertTrue(summary != null && summary.state() == EngineState.RUNNING && summary.firing() == 1,
				"should run on its own: " + summary);
			helper.succeed();
		});
	}

	@GameTest(template = "empty", batch = BATCH)
	public static void everyNewPartHasARecipeTheGameCanRead(GameTestHelper helper) {
		for (String name : new String[] {"high_compression_piston", "low_compression_piston", "high_compression_rotor", "low_compression_rotor",
			"piston_ring"}) {
			var recipe = helper.getLevel()
				.getRecipeManager()
				.byKey(ResourceLocation.fromNamespaceAndPath(CreateEngines.MOD_ID, name));
			helper.assertTrue(recipe.isPresent(), "no recipe loaded for " + name);
		}
		helper.succeed();
	}
}
