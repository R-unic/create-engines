package com.createengines.gametest;

import java.util.List;

import com.createengines.CreateEngines;
import com.createengines.content.battery.BatteryBank;
import com.createengines.content.battery.BatteryBlock;
import com.createengines.content.battery.BatteryBlockEntity;
import com.createengines.engine.sim.BatteryChemistry;
import com.createengines.engine.sim.BatteryTier;
import com.createengines.engine.topology.EngineLayout;
import com.createengines.engine.topology.EngineScanner;
import com.createengines.registry.CEBlocks;
import com.tterrag.registrate.util.entry.BlockEntry;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The Dry Cell (100 sparks, the cold does not touch it) and the Large Battery (800 sparks, the cold holds back up to three quarters)
 * beside the plain battery (200, as it always was). They join into one bank with each other and with a plain battery, so a bank
 * adds up whatever its members are. In their own batches: the cold ones set a temperature for the whole world.
 */
@GameTestHolder(CreateEngines.MOD_ID)
@PrefixGameTestTemplate(false)
public class BatteryTierBlocksGameTests {

	private static final String BATCH = "battery-tier-blocks";
	private static final String COLD = "battery-tier-blocks-cold";

	private static final BlockPos A = new BlockPos(6, 1, 1);
	private static final BlockPos B = new BlockPos(6, 1, 2);
	private static final BlockPos C = new BlockPos(6, 1, 3);

	private static BatteryBlockEntity place(GameTestHelper helper, BlockPos pos, BlockEntry<BatteryBlock> block, float charge) {
		helper.setBlock(pos, block.getDefaultState());
		BatteryBlockEntity battery = helper.getBlockEntity(pos);
		battery.charge(charge);
		return battery;
	}

	@AfterBatch(batch = COLD)
	public static void restoreTheTemperature(ServerLevel level) {
		BatteryBlockEntity.biomeTemperatureOverride = null;
	}

	@GameTest(template = "empty", batch = BATCH)
	public static void eachTierHoldsItsOwnCapacityAndTheBlocksKnowTheirTier(GameTestHelper helper) {
		BatteryBlockEntity plain = place(helper, A, CEBlocks.BATTERY, 0);
		BatteryBlockEntity dry = place(helper, A.east(2), CEBlocks.DRY_CELL, 0);
		BatteryBlockEntity large = place(helper, A.east(4), CEBlocks.LARGE_BATTERY, 0);
		helper.assertTrue(plain.tier() == BatteryTier.PLAIN && plain.capacity() == 200, "The plain battery is 200 as ever: " + plain.capacity());
		helper.assertTrue(dry.tier() == BatteryTier.DRY_CELL && dry.capacity() == 100, "A dry cell is 100: " + dry.capacity());
		helper.assertTrue(large.tier() == BatteryTier.LARGE && large.capacity() == 800, "A large battery is 800: " + large.capacity());
		helper.assertTrue(dry.charge(500) == 100 && dry.isFull() && dry.room() == 0, "A dry cell takes only its 100");
		helper.assertTrue(large.charge(5000) == 800 && large.isFull(), "A large battery takes only its 800");
		helper.succeed();
	}

	@GameTest(template = "empty", batch = BATCH)
	public static void aBankOfMixedTiersAddsUpAndSharesByRoomAndByCharge(GameTestHelper helper) {
		BatteryBlockEntity dry = place(helper, A, CEBlocks.DRY_CELL, 0);
		BatteryBlockEntity plain = place(helper, B, CEBlocks.BATTERY, 0);
		BatteryBlockEntity large = place(helper, C, CEBlocks.LARGE_BATTERY, 0);
		List<BatteryBlockEntity> bank = plain.bank();
		helper.assertTrue(bank.size() == 3 && bank.containsAll(List.of(dry, plain, large)), "Batteries of different tiers that touch are one bank: " + bank.size());
		helper.assertTrue(BatteryBank.capacity(bank) == 1100, "Capacity is the sum of each member's own: " + BatteryBank.capacity(bank));
		float put = BatteryBank.fill(bank, 550);
		helper.assertTrue(Math.abs(put - 550) < 1e-3, "Half the bank's room goes in: " + put);
		helper.assertTrue(Math.abs(dry.charge() - 50) < 1e-3 && Math.abs(plain.charge() - 100) < 1e-3 && Math.abs(large.charge() - 400) < 1e-3,
			"each takes in proportion to its room: " + dry.charge() + ", " + plain.charge() + ", " + large.charge());
		float taken = BatteryBank.drawUpTo(bank, 275);
		helper.assertTrue(Math.abs(taken - 275) < 1e-3 && Math.abs(dry.charge() - 25) < 1e-3 && Math.abs(plain.charge() - 50) < 1e-3
			&& Math.abs(large.charge() - 200) < 1e-3, "and gives in proportion to what it can deliver: " + dry.charge() + ", " + plain.charge() + ", " + large.charge());
		float over = BatteryBank.fill(bank, 5000);
		helper.assertTrue(Math.abs(over - 825) < 1e-3 && dry.isFull() && plain.isFull() && large.isFull(), "Filling past full stops at the sum of the rooms: " + over);
		helper.succeed();
	}

	@GameTest(template = "empty", batch = BATCH)
	public static void aSavedChargeIsHeldToTheCapacityOfTheBlockItIsLoadedInto(GameTestHelper helper) {
		BatteryBlockEntity dry = place(helper, A, CEBlocks.DRY_CELL, 0);
		BatteryBlockEntity large = place(helper, A.east(2), CEBlocks.LARGE_BATTERY, 0);
		CompoundTag tag = new CompoundTag();
		tag.putFloat("Charge", 700);
		dry.loadWithComponents(tag, helper.getLevel()
			.registryAccess());
		large.loadWithComponents(tag, helper.getLevel()
			.registryAccess());
		helper.assertTrue(dry.charge() == 100, "A dry cell cannot hold 700: " + dry.charge());
		helper.assertTrue(large.charge() == 700, "A large battery can: " + large.charge());
		tag.putFloat("Charge", 5000);
		large.loadWithComponents(tag, helper.getLevel()
			.registryAccess());
		helper.assertTrue(large.charge() == 800, "but not 5000: " + large.charge());
		helper.succeed();
	}

	@GameTest(template = "empty", batch = BATCH)
	public static void aTierReadsItsOwnFractionOnTheLampStripAndTheComparator(GameTestHelper helper) {
		BatteryBlockEntity dry = place(helper, A, CEBlocks.DRY_CELL, 100);
		BatteryBlockEntity large = place(helper, A.east(2), CEBlocks.LARGE_BATTERY, 200);
		helper.assertTrue(dry.bankSignal() == BatteryChemistry.MAX_SIGNAL, "A full dry cell reads full whatever its size: " + dry.bankSignal());
		helper.assertTrue(Math.abs(large.chargeFraction() - 0.25f) < 1e-6, "200 of 800 is a quarter: " + large.chargeFraction());
		helper.runAtTickTime(25, () -> {
			helper.assertTrue(helper.getBlockState(A)
				.getValue(BatteryBlock.CHARGE) == BatteryChemistry.GAUGE_STAGES, "The lamps show all of a full dry cell");
			helper.assertTrue(helper.getBlockState(A.east(2))
				.getValue(BatteryBlock.CHARGE) == BatteryChemistry.gaugeStage(0.25), "and a quarter of the large battery's own capacity");
			helper.succeed();
		});
	}

	@GameTest(template = "empty_large", batch = BATCH)
	public static void anEngineTouchingATierReachesTheMixedBank(GameTestHelper helper) {
		TestEngine.buildSpark(helper.getLevel(), helper.absolutePos(BlockPos.ZERO), true);
		BlockPos second = TestEngine.BATTERY.relative(Direction.NORTH);
		BlockPos third = second.relative(Direction.WEST);
		place(helper, second, CEBlocks.LARGE_BATTERY, 0);
		place(helper, third, CEBlocks.DRY_CELL, 0);
		helper.runAtTickTime(2, () -> {
			EngineLayout layout = EngineScanner.scan(helper.getLevel(), helper.absolutePos(TestEngine.PRIMARY));
			helper.assertTrue(layout != null, "The engine should scan");
			helper.assertTrue(layout.batteries()
				.size() == 3, "The engine reaches its battery and the large battery and dry cell joined to it: " + layout.batteries());
			helper.assertTrue(layout.batteries()
				.contains(helper.absolutePos(third)), "including the dry cell that touches only the large battery");
			helper.succeed();
		});
	}

	@GameTest(template = "empty", batch = COLD)
	public static void theColdKeepsNothingFromADryCellAndMostOfALargeBatteryBack(GameTestHelper helper) {
		BatteryBlockEntity.biomeTemperatureOverride = 0f;
		BatteryBlockEntity dry = place(helper, A, CEBlocks.DRY_CELL, 100);
		BatteryBlockEntity large = place(helper, A.east(2), CEBlocks.LARGE_BATTERY, 800);
		helper.runAtTickTime(45, () -> {
			helper.assertTrue(dry.coldness() > 0.99f, "The air is as cold as it gets: " + dry.coldness());
			helper.assertTrue(Math.abs(dry.deliverable() - 100) < 1e-3, "A dry cell still gives all of its 100: " + dry.deliverable());
			helper.assertTrue(Math.abs(large.deliverable() - 200) < 1e-3, "A large battery gives a quarter of its 800: " + large.deliverable());
			BatteryBlockEntity.biomeTemperatureOverride = null;
			helper.succeed();
		});
	}
}
