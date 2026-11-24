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
 * Battery capacity as the tier machinery reports it: the plain battery is 200, as it always was. The smaller and larger tiers are for 2.2, and these
 * tests keep the bank, the saved charge, the lamp strip and the joins across chunk borders under test meanwhile. They join into banks with each other, so a bank
 * adds up whatever its members are. In their own batches, as the other battery tests are: the cold ones set a temperature for the whole world.
 */
@GameTestHolder(CreateEngines.MOD_ID)
@PrefixGameTestTemplate(false)
public class BatteryTierGameTests {

	private static final String BATCH = "battery-tiers";
	private static final String COLD = "battery-tiers-cold";

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
	public static void theBatteryHoldsItsOwnCapacityAndKnowsItsTier(GameTestHelper helper) {
		BatteryBlockEntity plain = place(helper, A, CEBlocks.BATTERY, 0);
		helper.assertTrue(plain.tier() == BatteryTier.PLAIN && plain.capacity() == 200, "The plain battery is 200 as ever: " + plain.capacity());
		helper.assertTrue(plain.charge(500) == 200 && plain.isFull(), "The plain one takes only its 200");
		helper.succeed();
	}

	@GameTest(template = "empty", batch = BATCH)
	public static void aBankAddsUpAndSharesByRoomAndByCharge(GameTestHelper helper) {
		BatteryBlockEntity dry = place(helper, A, CEBlocks.BATTERY, 0);
		BatteryBlockEntity plain = place(helper, B, CEBlocks.BATTERY, 0);
		BatteryBlockEntity second = place(helper, C, CEBlocks.BATTERY, 0);
		List<BatteryBlockEntity> bank = plain.bank();
		helper.assertTrue(bank.size() == 3 && bank.containsAll(List.of(dry, plain, second)), "Batteries of different tiers that touch are one bank: " + bank.size());
		helper.assertTrue(BatteryBank.capacity(bank) == 600, "Capacity is the sum of each member's own: " + BatteryBank.capacity(bank));
		float put = BatteryBank.fill(bank, 300);
		helper.assertTrue(Math.abs(put - 300) < 1e-3, "Half the bank's room goes in: " + put);
		helper.assertTrue(Math.abs(dry.charge() - 100) < 1e-3 && Math.abs(plain.charge() - 100) < 1e-3 && Math.abs(second.charge() - 100) < 1e-3,
			"each takes in proportion to its room: " + dry.charge() + ", " + plain.charge() + ", " + second.charge());
		float taken = BatteryBank.drawUpTo(bank, 150);
		helper.assertTrue(Math.abs(taken - 150) < 1e-3 && Math.abs(dry.charge() - 50) < 1e-3 && Math.abs(plain.charge() - 50) < 1e-3
			&& Math.abs(second.charge() - 50) < 1e-3, "and gives in proportion to what it can deliver: " + dry.charge() + ", " + plain.charge() + ", " + second.charge());
		float over = BatteryBank.fill(bank, 5000);
		helper.assertTrue(Math.abs(over - 450) < 1e-3 && dry.isFull() && plain.isFull() && second.isFull(), "Filling past full stops at the sum of the rooms: " + over);
		helper.succeed();
	}

	@GameTest(template = "empty", batch = BATCH)
	public static void aSavedChargeIsHeldToTheCapacityOfTheBlockItIsLoadedInto(GameTestHelper helper) {
		BatteryBlockEntity plain = place(helper, A.east(4), CEBlocks.BATTERY, 0);
		CompoundTag tag = new CompoundTag();
		tag.putFloat("Charge", 700);
		plain.loadWithComponents(tag, helper.getLevel()
			.registryAccess());
		helper.assertTrue(plain.charge() == 200, "A plain battery is still held to 200: " + plain.charge());
		tag.putFloat("Charge", Float.NaN);
		plain.loadWithComponents(tag, helper.getLevel()
			.registryAccess());
		helper.assertTrue(plain.charge() == 0, "A charge that is not a number reads as 0 on every tier");
		helper.succeed();
	}

	@GameTest(template = "empty", batch = BATCH)
	public static void aBatteryReadsItsOwnFractionOnTheLampStripAndTheComparator(GameTestHelper helper) {
		BatteryBlockEntity plain = place(helper, A, CEBlocks.BATTERY, 100);
		helper.assertTrue(Math.abs(plain.chargeFraction() - 0.5f) < 1e-6, "100 of 200 is half: " + plain.chargeFraction());
		helper.assertTrue(plain.bankSignal() == BatteryChemistry.signal(0.5), "The comparator reads half: " + plain.bankSignal());
		helper.runAtTickTime(25, () -> {
			int stage = helper.getBlockState(A)
				.getValue(BatteryBlock.CHARGE);
			helper.assertTrue(stage == BatteryChemistry.gaugeStage(0.5), "The lamps show half of the plain battery's own capacity: " + stage);
			helper.succeed();
		});
	}

	/** Whether every battery in the area shows a join on each side that has a battery against it, and none on a side that has not. */
	private static String badJoin(GameTestHelper helper, int x0, int x1, int z0, int z1, int y0, int y1) {
		for (int x = x0; x <= x1; x++)
			for (int y = y0; y <= y1; y++)
				for (int z = z0; z <= z1; z++) {
					BlockPos at = new BlockPos(x, y, z);
					if (!(helper.getBlockState(at).getBlock() instanceof BatteryBlock))
						continue;
					for (Direction side : new Direction[] {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST, Direction.UP}) {
						boolean shown = helper.getBlockState(at)
							.getValue(BatteryBlock.joinFor(side));
						boolean there = helper.getBlockState(at.relative(side))
							.getBlock() instanceof BatteryBlock;
						if (shown != there)
							return at + " " + side + ": shows " + shown + " but a battery is " + (there ? "there" : "not there");
					}
				}
		return null;
	}

	@GameTest(template = "empty_large", batch = BATCH)
	public static void batteriesPlacedInARowAndABlockAreJoinedOnBothSidesOfEveryContact(GameTestHelper helper) {
		// A row of 6 along x, a 3 by 3 block beside it, and a column on top of the row's first: every way a bank is built
		for (int x = 2; x < 8; x++)
			helper.setBlock(new BlockPos(x, 1, 2), CEBlocks.BATTERY.getDefaultState());
		for (int x = 2; x < 5; x++)
			for (int z = 5; z < 8; z++)
				helper.setBlock(new BlockPos(x, 1, z), CEBlocks.BATTERY.getDefaultState());
		helper.setBlock(new BlockPos(2, 2, 2), CEBlocks.BATTERY.getDefaultState());
		helper.setBlock(new BlockPos(2, 3, 2), CEBlocks.BATTERY.getDefaultState());
		// One placed with no neighbour update at all, as a paste may do it: it has to put itself right
		helper.getLevel()
			.setBlock(helper.absolutePos(new BlockPos(8, 1, 2)), CEBlocks.BATTERY.getDefaultState(), 2);
		BatteryBlockEntity rowFirst = helper.getBlockEntity(new BlockPos(2, 1, 2));
		BatteryBlockEntity blockFirst = helper.getBlockEntity(new BlockPos(2, 1, 5));
		helper.assertTrue(rowFirst.bank().size() == 6 + 2 + 1, "The row, the two on top of it and the one placed at its end are one bank of nine: " + rowFirst.bank().size());
		helper.assertTrue(blockFirst.bank().size() == 9, "and the 3 by 3 block, which does not touch them, is another: " + blockFirst.bank().size());
		String early = badJoin(helper, 1, 9, 1, 8, 1, 3);
		helper.assertTrue(early == null || early.startsWith(helper.absolutePos(new BlockPos(8, 1, 2)) + " "), "Joins right after placing: " + early);
		helper.runAtTickTime(60, () -> {
			String late = badJoin(helper, 1, 9, 1, 8, 1, 3);
			helper.assertTrue(late == null, "Every join agrees with its neighbours a second or two on: " + late);
			helper.succeed();
		});
	}

	/** The x, relative to the test, of the nearest chunk border east of the structure's origin (a multiple of 16 in the world). */
	private static int chunkBorderX(GameTestHelper helper) {
		int origin = helper.absolutePos(BlockPos.ZERO)
			.getX();
		return Math.floorDiv(origin, 16) * 16 + 16 - origin;
	}

	/** A row of 14 batteries (the length of the bank in the author's screenshot, which was of Large Batteries) laid across a chunk border, in the given order of x offsets from the border. */
	private static void layRowAcrossTheBorder(GameTestHelper helper, int border, int z, int flags, int... offsets) {
		for (int offset : offsets)
			helper.getLevel()
				.setBlock(helper.absolutePos(new BlockPos(border + offset, 1, z)), CEBlocks.BATTERY.getDefaultState(), flags);
	}

	/** Takes the rows back out: they reach past the structure into the world, where nothing else would ever clear them. Quietly, with no updates. */
	private static void clearRows(GameTestHelper helper, int border, int z0, int z1) {
		for (int x = border - 8; x <= border + 7; x++)
			for (int y = 1; y <= 2; y++)
				for (int z = z0; z <= z1; z++)
					helper.getLevel()
						.setBlock(helper.absolutePos(new BlockPos(x, y, z)), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
	}

	@GameTest(template = "empty_large", batch = BATCH)
	public static void batteriesInARowAcrossAChunkBorderAreJoinedOnBothSidesOfEveryContactWhateverTheOrder(GameTestHelper helper) {
		int border = chunkBorderX(helper);
		int z = 2;
		// Left to right, right to left, and from both ends into the middle with the border block last: each leaves a different pair last to join
		layRowAcrossTheBorder(helper, border, z, 3, -7, -6, -5, -4, -3, -2, -1, 0, 1, 2, 3, 4, 5, 6);
		layRowAcrossTheBorder(helper, border, z + 2, 3, 6, 5, 4, 3, 2, 1, 0, -1, -2, -3, -4, -5, -6, -7);
		layRowAcrossTheBorder(helper, border, z + 4, 3, -7, 6, -6, 5, -5, 4, -4, 3, -3, 2, -2, 1, -1, 0);
		BlockPos first = helper.absolutePos(new BlockPos(border - 7, 1, z));
		helper.assertTrue(helper.getLevel()
			.getChunk(first).getPos().x != helper.getLevel()
				.getChunk(first.east(13)).getPos().x, "The row really crosses a chunk border");
		BatteryBlockEntity head = helper.getBlockEntity(new BlockPos(border - 7, 1, z));
		helper.assertTrue(head.bank().size() == 14, "The row is one bank of 14: " + head.bank().size());
		String early = badJoin(helper, border - 8, border + 7, z - 1, z + 5, 0, 2);
		helper.assertTrue(early == null, "Joins right after placing, across the border: " + early);
		helper.runAtTickTime(60, () -> {
			String late = badJoin(helper, border - 8, border + 7, z - 1, z + 5, 0, 2);
			clearRows(helper, border, z - 1, z + 5);
			helper.assertTrue(late == null, "Joins a second or two on, across the border: " + late);
			helper.succeed();
		});
	}

	@GameTest(template = "empty_large", batch = BATCH)
	public static void batteriesPastedAcrossAChunkBorderWithNoNeighbourUpdatePutThemselvesRight(GameTestHelper helper) {
		int border = chunkBorderX(helper);
		// Flag 2 only: no neighbour or shape updates at all, as a schematic or a contraption put back may place them
		layRowAcrossTheBorder(helper, border, 2, 2, -7, -6, -5, -4, -3, -2, -1, 0, 1, 2, 3, 4, 5, 6);
		helper.runAtTickTime(80, () -> {
			String late = badJoin(helper, border - 8, border + 7, 1, 3, 0, 2);
			clearRows(helper, border, 1, 3);
			helper.assertTrue(late == null, "Every join across the border put itself right: " + late);
			helper.succeed();
		});
	}

	@GameTest(template = "empty_large", batch = BATCH)
	public static void anEngineTouchingABatteryReachesTheWholeBank(GameTestHelper helper) {
		TestEngine.buildSpark(helper.getLevel(), helper.absolutePos(BlockPos.ZERO), true);
		BlockPos second = TestEngine.BATTERY.relative(Direction.NORTH);
		BlockPos third = second.relative(Direction.WEST);
		place(helper, second, CEBlocks.BATTERY, 0);
		place(helper, third, CEBlocks.BATTERY, 0);
		helper.runAtTickTime(2, () -> {
			EngineLayout layout = EngineScanner.scan(helper.getLevel(), helper.absolutePos(TestEngine.PRIMARY));
			helper.assertTrue(layout != null, "The engine should scan");
			helper.assertTrue(layout.batteries()
				.size() == 3, "The engine reaches its battery and the two joined to it: " + layout.batteries());
			helper.assertTrue(layout.batteries()
				.contains(helper.absolutePos(third)), "including the one that touches only the second battery");
			helper.succeed();
		});
	}

	@GameTest(template = "empty", batch = COLD)
	public static void theColdKeepsBackHalfOfAPlainBattery(GameTestHelper helper) {
		BatteryBlockEntity.biomeTemperatureOverride = 0f;
		BatteryBlockEntity plain = place(helper, A, CEBlocks.BATTERY, 200);
		helper.runAtTickTime(45, () -> {
			helper.assertTrue(plain.coldness() > 0.99f, "The air is as cold as it gets: " + plain.coldness());
			helper.assertTrue(Math.abs(plain.deliverable() - 100) < 1e-3, "The plain battery gives half, as ever: " + plain.deliverable());
			BatteryBlockEntity.biomeTemperatureOverride = null;
			helper.succeed();
		});
	}
}
