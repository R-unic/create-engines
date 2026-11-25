package com.createengines.gametest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

import com.createengines.CreateEngines;
import com.createengines.config.CEConfig;
import com.createengines.content.battery.BatteryBank;
import com.createengines.content.battery.BatteryBlockEntity;
import com.createengines.engine.sim.BatteryChemistry;
import com.createengines.registry.CEBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * What it costs a server to know which batteries are one bank: a bank is walked once for all its batteries, and only a change to that
 * bank has it walked early. The walk counter is the whole game's, and batteries left by earlier tests go on walking theirs, so every
 * count here is taken with nothing else running between its two readings.
 */
@GameTestHolder(CreateEngines.MOD_ID)
@PrefixGameTestTemplate(false)
public class BatteryBankLookGameTests {

	private static final String BIG = "battery-bank-looks";
	private static final String CAP = "battery-bank-cap";
	private static final int DEFAULT_CAP = 4096;

	@AfterBatch(batch = CAP)
	public static void restoreCap(ServerLevel level) {
		GameTestConfig.save(CEConfig.MAX_BANK_BATTERIES, DEFAULT_CAP);
	}

	/** A bank ten along, ten high and two deep. */
	private static List<BlockPos> bigBank() {
		List<BlockPos> blocks = new ArrayList<>();
		for (int x = 2; x < 12; x++)
			for (int y = 2; y < 12; y++)
				for (int z = 2; z < 4; z++)
					blocks.add(new BlockPos(x, y, z));
		return blocks;
	}

	private static List<BatteryBlockEntity> batteries(GameTestHelper helper, List<BlockPos> blocks) {
		List<BatteryBlockEntity> found = new ArrayList<>(blocks.size());
		for (BlockPos pos : blocks)
			found.add(helper.getBlockEntity(pos));
		return found;
	}

	/** Has every battery ask for its bank, fails unless all are given the same one, and returns how many walks that took. */
	private static int walksForAllToAsk(GameTestHelper helper, List<BatteryBlockEntity> batteries) {
		int before = BatteryBank.walksForTests();
		List<BatteryBlockEntity> bank = batteries.getFirst()
			.bank();
		for (BatteryBlockEntity battery : batteries)
			helper.assertTrue(battery.bank() == bank, "every battery of a bank should be given the same one");
		helper.assertTrue(bank.size() == batteries.size(), "a bank of " + batteries.size() + " was found as " + bank.size());
		return BatteryBank.walksForTests() - before;
	}

	@GameTest(template = "empty_large", timeoutTicks = 400, batch = BIG)
	public static void aBigBankIsWalkedOnceARefreshForAllItsBatteries(GameTestHelper helper) {
		List<BlockPos> blocks = bigBank();
		for (BlockPos pos : blocks)
			helper.setBlock(pos, CEBlocks.BATTERY.getDefaultState());
		Set<List<BatteryBlockEntity>> seen = Collections.newSetFromMap(new IdentityHashMap<>());
		for (int tick = 30; tick < 230; tick++)
			helper.runAtTickTime(tick, () -> {
				List<BatteryBlockEntity> all = batteries(helper, blocks);
				int walks = walksForAllToAsk(helper, all);
				helper.assertTrue(walks <= 1, "two hundred batteries asking in one tick walked the bank " + walks + " times");
				seen.add(all.getFirst()
					.bank());
			});
		helper.runAtTickTime(230, () -> {
			// Two hundred ticks is ten refreshes, and one for where the first fell
			helper.assertTrue(seen.size() >= 9 && seen.size() <= 11, "the bank was walked " + seen.size() + " times in 200 ticks");
			List<BatteryBlockEntity> all = batteries(helper, blocks);
			for (BatteryBlockEntity battery : all)
				battery.charge(battery.tier()
					.capacity() / 2f);
			int half = BatteryChemistry.signal(0.5f);
			for (BatteryBlockEntity battery : all)
				helper.assertTrue(battery.bankSignal() == half, "every battery reads the whole bank's charge: " + battery.bankSignal());

			walksForAllToAsk(helper, all);
			BlockPos apart = new BlockPos(30, 2, 2);
			helper.setBlock(apart, CEBlocks.BATTERY.getDefaultState());
			helper.setBlock(apart, Blocks.AIR);
			int walks = walksForAllToAsk(helper, all);
			helper.assertTrue(walks == 0, "a battery placed and broken elsewhere had this bank walked " + walks + " times");

			BlockPos corner = blocks.getLast();
			helper.setBlock(corner, Blocks.AIR);
			List<BlockPos> rest = blocks.subList(0, blocks.size() - 1);
			walks = walksForAllToAsk(helper, batteries(helper, rest));
			helper.assertTrue(walks == 1, "breaking one of the bank should have it walked once: " + walks);
			helper.setBlock(corner, CEBlocks.BATTERY.getDefaultState());
			// The new battery has not looked yet: the first of the others to ask finds it and tells it with the rest
			walks = walksForAllToAsk(helper, batteries(helper, blocks));
			helper.assertTrue(walks == 1, "putting it back should have the bank walked once: " + walks);
			helper.succeed();
		});
	}

	/** The setting at its lowest, in the middle and at its highest, with one row of eight batteries each time. */
	@GameTest(template = "empty_large", timeoutTicks = 200, batch = CAP)
	public static void aBankBiggerThanTheLimitActsAsMoreThanOne(GameTestHelper helper) {
		List<BlockPos> row = new ArrayList<>();
		for (int x = 2; x < 10; x++)
			row.add(new BlockPos(x, 2, 2));
		try {
			for (int limit : new int[] {1, 5, 32768}) {
				GameTestConfig.save(CEConfig.MAX_BANK_BATTERIES, limit);
				// Built again for each limit: a limit changed under a standing bank is felt at its next walk, a second later
				for (BlockPos pos : row)
					helper.setBlock(pos, Blocks.AIR);
				for (BlockPos pos : row)
					helper.setBlock(pos, CEBlocks.BATTERY.getDefaultState());
				int expected = Math.min(limit, row.size());
				int biggest = 0;
				for (BatteryBlockEntity battery : batteries(helper, row)) {
					battery.charge(battery.tier()
						.capacity());
					List<BatteryBlockEntity> bank = battery.bank();
					helper.assertTrue(bank.size() <= expected, "limit " + limit + ": a bank of " + bank.size());
					helper.assertTrue(bank.contains(battery), "a battery is always in the bank it is given");
					biggest = Math.max(biggest, bank.size());
				}
				helper.assertTrue(biggest == expected, "limit " + limit + ": the biggest bank was " + biggest);
				for (BatteryBlockEntity battery : batteries(helper, row))
					helper.assertTrue(battery.bankSignal() == BatteryChemistry.signal(1f),
						"limit " + limit + ": a full bank reads full: " + battery.bankSignal());
			}
		} finally {
			GameTestConfig.save(CEConfig.MAX_BANK_BATTERIES, DEFAULT_CAP);
		}
		helper.succeed();
	}
}
