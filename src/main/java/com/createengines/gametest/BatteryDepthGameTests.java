package com.createengines.gametest;

import java.util.List;

import com.createengines.CreateEngines;
import com.createengines.content.battery.BatteryBank;
import com.createengines.content.battery.BatteryBlock;
import com.createengines.content.battery.BatteryBlockEntity;
import com.createengines.content.starter.StarterBlockEntity;
import com.createengines.engine.BatteryIgnition;
import com.createengines.engine.sim.BatteryChemistry;
import com.createengines.engine.sim.Tuning;
import com.createengines.engine.topology.EngineLayout;
import com.createengines.engine.topology.EngineScanner;
import com.createengines.registry.CEBlocks;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ComparatorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.ComparatorBlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Battery depth: batteries that touch are one bank, a cold battery gives less and a cold engine makes sparks cost more, a
 * comparator reads a bank's charge and the lamp strip on its lid shows it, and a starter draws on the batteries (and
 * draws less as they run down) for a spark engine only. Every test here is in its own batch: the default batch is cut into groups of 50 and
 * adding tests to it moved two older tests that register a provider for the same block into one group, where they see each other.
 */
@GameTestHolder(CreateEngines.MOD_ID)
@PrefixGameTestTemplate(false)
public class BatteryDepthGameTests {

	private static final BlockPos A = new BlockPos(6, 1, 1);
	private static final BlockPos B = new BlockPos(6, 1, 2);
	private static final BlockPos C = new BlockPos(6, 1, 3);
	/** Apart from A, B and C by a block of air. */
	private static final BlockPos FAR = new BlockPos(6, 1, 5);

	private static BatteryBlockEntity battery(GameTestHelper helper, BlockPos pos) {
		return helper.getBlockEntity(pos);
	}

	private static BatteryBlockEntity place(GameTestHelper helper, BlockPos pos, float charge) {
		helper.setBlock(pos, CEBlocks.BATTERY.getDefaultState());
		BatteryBlockEntity battery = battery(helper, pos);
		battery.charge(charge);
		return battery;
	}

	private static BlockState stateAt(GameTestHelper helper, BlockPos pos) {
		return helper.getBlockState(pos);
	}

	private static boolean joined(GameTestHelper helper, BlockPos pos, net.minecraft.world.level.block.state.properties.BooleanProperty side) {
		return stateAt(helper, pos).getValue(side);
	}

	@GameTest(template = "empty", batch = "battery-depth")
	public static void batteriesThatTouchLookJoinedOnExactlyTheTouchingSidesAndNotDiagonally(GameTestHelper helper) {
		BlockPos stackTop = A.above();
		BlockPos diagonal = A.east().south(2);
		place(helper, A, 0);
		place(helper, B, 0);
		place(helper, stackTop, 0);
		place(helper, diagonal, 0);
		if (!joined(helper, A, BatteryBlock.SOUTH) || !joined(helper, A, BatteryBlock.UP) || joined(helper, A, BatteryBlock.NORTH)
			|| joined(helper, A, BatteryBlock.EAST) || joined(helper, A, BatteryBlock.WEST))
			helper.fail("the first battery should be joined to the one south of it and the one above, and nothing else: " + stateAt(helper, A));
		if (!joined(helper, B, BatteryBlock.NORTH) || joined(helper, B, BatteryBlock.SOUTH) || joined(helper, B, BatteryBlock.UP))
			helper.fail("the second battery should be joined to the one north of it: " + stateAt(helper, B));
		BatteryBlockEntity top = battery(helper, stackTop);
		if (top.bank().size() != 3)
			helper.fail("the three that touch should still be one bank, with the diagonal one apart: " + top.bank().size());
		for (net.minecraft.world.level.block.state.properties.BooleanProperty side : new net.minecraft.world.level.block.state.properties.BooleanProperty[] {BatteryBlock.NORTH, BatteryBlock.EAST, BatteryBlock.SOUTH, BatteryBlock.WEST,
			BatteryBlock.UP})
			if (joined(helper, diagonal, side))
				helper.fail("a battery that only touches at a corner is joined to nothing: " + stateAt(helper, diagonal));
		// The outline runs out to the edge on a joined side only
		var shape = stateAt(helper, A).getShape(helper.getLevel(), helper.absolutePos(A));
		if (shape.max(Direction.Axis.Z) != 1.0 || shape.min(Direction.Axis.Z) != 0.125 || shape.min(Direction.Axis.X) != 0.125
			|| shape.max(Direction.Axis.X) != 0.875)
			helper.fail("the outline should reach the edge on the south, which is joined, and be set in elsewhere: " + shape);
		// Taking one away lets the others go
		helper.setBlock(B, Blocks.AIR);
		if (joined(helper, A, BatteryBlock.SOUTH))
			helper.fail("a battery whose neighbour was broken should no longer be joined to it");
		helper.setBlock(stackTop, Blocks.AIR);
		if (joined(helper, A, BatteryBlock.UP))
			helper.fail("a battery whose neighbour on top was broken should no longer be joined up");
		helper.succeed();
	}

	@GameTest(template = "empty", batch = "battery-depth")
	public static void aBatteryPlacedAgainstAnotherIsJoinedAtOnceAndNotToOtherBlocks(GameTestHelper helper) {
		place(helper, A, 0);
		helper.setBlock(B.east(), Blocks.STONE);
		helper.setBlock(B.west(), CEBlocks.ALTERNATOR.getDefaultState());
		BlockState placed = ((BatteryBlock) CEBlocks.BATTERY.get()).getStateForPlacement(
			new net.minecraft.world.item.context.BlockPlaceContext(helper.getLevel(), null, net.minecraft.world.InteractionHand.MAIN_HAND,
				CEBlocks.BATTERY.asStack(), new net.minecraft.world.phys.BlockHitResult(
					net.minecraft.world.phys.Vec3.atCenterOf(helper.absolutePos(B)), Direction.UP, helper.absolutePos(B), false)));
		if (placed == null || !placed.getValue(BatteryBlock.NORTH))
			helper.fail("a battery placed against another should come out joined to it: " + placed);
		if (placed.getValue(BatteryBlock.EAST) || placed.getValue(BatteryBlock.WEST))
			helper.fail("stone and an alternator are not batteries: " + placed);
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 100, batch = "battery-depth")
	public static void aBatterySavedBeforeTheyJoinedLooksAtItsNeighboursAndSettles(GameTestHelper helper) {
		// Placed with no neighbour updates, as a world saved by an earlier version loads them: neither knows of the other
		for (BlockPos pos : new BlockPos[] {A, B})
			helper.getLevel()
				.setBlock(helper.absolutePos(pos), CEBlocks.BATTERY.getDefaultState(),
					net.minecraft.world.level.block.Block.UPDATE_CLIENTS | net.minecraft.world.level.block.Block.UPDATE_KNOWN_SHAPE);
		if (joined(helper, A, BatteryBlock.SOUTH))
			helper.fail("the test needs a battery that has not yet noticed its neighbour");
		helper.succeedWhen(() -> {
			if (!joined(helper, A, BatteryBlock.SOUTH) || !joined(helper, B, BatteryBlock.NORTH))
				helper.fail("the batteries should have noticed each other: " + stateAt(helper, A) + " " + stateAt(helper, B));
		});
	}

	@GameTest(template = "empty", timeoutTicks = 100, batch = "battery-depth")
	public static void joiningLeavesTheLampStripAndTheBankAsTheyWere(GameTestHelper helper) {
		BatteryBlockEntity a = place(helper, A, BatteryBlockEntity.CAPACITY);
		place(helper, B, 0);
		helper.succeedWhen(() -> {
			BlockState state = stateAt(helper, A);
			if (state.getValue(BatteryBlock.CHARGE) != BatteryChemistry.GAUGE_STAGES)
				helper.fail("the full battery should show a full lamp strip, which the join must not change: " + state);
			if (!state.getValue(BatteryBlock.SOUTH))
				helper.fail("the lamp strip changing the block must keep the join: " + state);
			if (stateAt(helper, B).getValue(BatteryBlock.CHARGE) != 0)
				helper.fail("the empty battery should keep a dark strip: " + stateAt(helper, B));
			if (a.bank().size() != 2)
				helper.fail("the bank is the two of them");
		});
	}

	@GameTest(template = "empty", batch = "battery-depth")
	public static void batteriesThatTouchFormOneBankAndBreakingTheMiddleSplitsIt(GameTestHelper helper) {
		BatteryBlockEntity a = place(helper, A, 50);
		BatteryBlockEntity b = place(helper, B, 100);
		BatteryBlockEntity c = place(helper, C, 150);
		BatteryBlockEntity far = place(helper, FAR, 10);
		List<BatteryBlockEntity> bank = a.bank();
		helper.assertTrue(bank.size() == 3 && bank.containsAll(List.of(a, b, c)), "Three touching batteries are one bank: " + bank.size());
		helper.assertTrue(c.bank().size() == 3, "Any member finds the whole bank, from the far end too");
		helper.assertTrue(far.bank().equals(List.of(far)), "A battery with a gap round it is a bank of one");
		helper.assertTrue(BatteryBank.capacity(bank) == 3 * BatteryBlockEntity.CAPACITY, "Capacity is the sum of the members'");
		helper.assertTrue(BatteryBank.charge(bank) == 300, "Charge is the sum of the members': " + BatteryBank.charge(bank));
		helper.setBlock(B, Blocks.AIR);
		helper.assertTrue(a.bank().equals(List.of(a)), "Breaking the middle leaves the first end alone: " + a.bank().size());
		helper.assertTrue(c.bank().equals(List.of(c)), "and the other end alone: " + c.bank().size());
		helper.setBlock(B, CEBlocks.BATTERY.getDefaultState());
		helper.assertTrue(a.bank().size() == 3, "Putting a battery back in the gap joins the two again: " + a.bank().size());
		helper.succeed();
	}

	@GameTest(template = "empty", batch = "battery-depth")
	public static void aBankDrawsAndChargesInProportionSoItRunsDownAndFillsEvenly(GameTestHelper helper) {
		BatteryBlockEntity a = place(helper, A, 100);
		BatteryBlockEntity b = place(helper, B, 50);
		List<BatteryBlockEntity> bank = a.bank();
		float taken = BatteryBank.drawUpTo(bank, 30);
		helper.assertTrue(Math.abs(taken - 30) < 1e-3, "It gives what was asked when it has it: " + taken);
		helper.assertTrue(Math.abs(a.charge() - 80) < 1e-3 && Math.abs(b.charge() - 40) < 1e-3,
			"Each member gives in proportion to what it holds (2 to 1 here): " + a.charge() + " and " + b.charge());
		float tooMuch = BatteryBank.drawUpTo(bank, 500);
		helper.assertTrue(Math.abs(tooMuch - 120) < 1e-3 && a.charge() < 1e-3 && b.charge() < 1e-3,
			"Asked for more than it holds it gives all there is and no more: " + tooMuch);
		helper.assertTrue(!BatteryBank.drawAll(bank, 1), "drawAll takes nothing from a flat bank");
		float put = BatteryBank.fill(bank, 100);
		helper.assertTrue(Math.abs(put - 100) < 1e-3 && Math.abs(a.charge() - 50) < 1e-3 && Math.abs(b.charge() - 50) < 1e-3,
			"Charging splits in proportion to the room: " + a.charge() + " and " + b.charge());
		a.charge(BatteryBlockEntity.CAPACITY);
		float next = BatteryBank.fill(bank, 40);
		helper.assertTrue(Math.abs(next - 40) < 1e-3 && Math.abs(b.charge() - 90) < 1e-3,
			"A full member takes nothing and the one with room takes it all: " + b.charge());
		helper.assertTrue(BatteryBank.fill(bank, -5) == 0 && BatteryBank.drawUpTo(bank, -5) == 0, "Nothing negative moves");
		helper.succeed();
	}

	@GameTest(template = "empty_large", batch = "battery-depth")
	public static void anEngineTouchingOneBatteryReachesTheWholeBank(GameTestHelper helper) {
		TestEngine.buildSpark(helper.getLevel(), helper.absolutePos(BlockPos.ZERO), true);
		BlockPos second = TestEngine.BATTERY.relative(Direction.NORTH);
		BlockPos third = second.relative(Direction.WEST);
		helper.setBlock(second, CEBlocks.BATTERY.getDefaultState());
		helper.setBlock(third, CEBlocks.BATTERY.getDefaultState());
		helper.runAtTickTime(2, () -> {
			EngineLayout layout = EngineScanner.scan(helper.getLevel(), helper.absolutePos(TestEngine.PRIMARY));
			helper.assertTrue(layout != null, "The engine should scan");
			List<BlockPos> batteries = layout.batteries();
			helper.assertTrue(batteries.size() == 3, "The engine touches one battery and reaches its two bank mates: " + batteries);
			helper.assertTrue(batteries.contains(helper.absolutePos(third)), "including the one that touches only another battery");
			helper.assertTrue(layout.members().contains(helper.absolutePos(third)),
				"and the far one is a member, so a blueprint records it and the engine rescans when it changes");
			helper.succeed();
		});
	}

	@GameTest(template = "empty", batch = "battery-cold")
	public static void aColdBatteryKeepsItsChargeButGivesHalfAndAHeatSourceBesideItGivesItBack(GameTestHelper helper) {
		BatteryBlockEntity.biomeTemperatureOverride = 0f;
		BatteryBlockEntity battery = place(helper, A, BatteryBlockEntity.CAPACITY);
		helper.runAtTickTime(45, () -> {
			helper.assertTrue(battery.coldness() > 0.99f, "A battery in a freezing biome is as cold as it gets: " + battery.coldness());
			helper.assertTrue(Math.abs(battery.deliverable() - 100) < 1e-3, "It can give only half of its capacity: " + battery.deliverable());
			helper.assertTrue(battery.charge() == BatteryBlockEntity.CAPACITY, "The charge is all still there");
			float drawn = battery.draw(150);
			helper.assertTrue(Math.abs(drawn - 100) < 1e-3, "Asked for 150 it gives 100: " + drawn);
			helper.assertTrue(Math.abs(battery.charge() - 100) < 1e-3, "and what it gave is gone: " + battery.charge());
			battery.charge(100);
			helper.setBlock(A.east(), AllBlocks.BLAZE_BURNER.getDefaultState()
				.setValue(BlazeBurnerBlock.HEAT_LEVEL, BlazeBurnerBlock.HeatLevel.SEETHING));
		});
		helper.runAtTickTime(100, () -> {
			// A burner with no fuel settles at smouldering whatever it was placed as, so what is asserted is that it warms and gives some back
			helper.assertTrue(battery.coldness() < 0.7f, "A blaze burner beside it takes some of the cold off: " + battery.coldness());
			helper.assertTrue(battery.deliverable() > 120, "and gives some of the capacity back: " + battery.deliverable());
			BatteryBlockEntity.biomeTemperatureOverride = null;
			helper.succeed();
		});
	}

	@GameTest(template = "empty", batch = "battery-cold-off")
	public static void withBatteryColdOffAColdBatteryGivesEverything(GameTestHelper helper) {
		BatteryBlockEntity.biomeTemperatureOverride = 0f;
		boolean before = Tuning.batteryCold;
		Tuning.batteryCold = false;
		BatteryBlockEntity battery = place(helper, A, BatteryBlockEntity.CAPACITY);
		helper.runAtTickTime(45, () -> {
			helper.assertTrue(Math.abs(battery.deliverable() - 200) < 1e-3, "With the feature off the cold keeps nothing back: " + battery.deliverable());
			Tuning.batteryCold = before;
			BatteryBlockEntity.biomeTemperatureOverride = null;
			helper.succeed();
		});
	}

	@GameTest(template = "empty", batch = "battery-depth")
	public static void aColdEngineMakesASparkCostMoreOfTheBank(GameTestHelper helper) {
		BatteryBlockEntity battery = place(helper, A, 100);
		BatteryIgnition ignition = new BatteryIgnition(helper.getLevel(), List.of(helper.absolutePos(A)));
		ignition.setEngineCold(0);
		helper.assertTrue(ignition.spark(), "A warm engine's plug fires");
		helper.assertTrue(Math.abs(battery.charge() - 99) < 1e-3, "and costs one spark: " + battery.charge());
		ignition.setEngineCold(1);
		helper.assertTrue(ignition.spark(), "A cold engine's plug fires");
		helper.assertTrue(Math.abs(battery.charge() - 97) < 1e-3, "and costs two: " + battery.charge());
		battery.draw(battery.charge() - 1.5f);
		helper.assertTrue(!ignition.powered(), "With less than a cold spark's worth left the plug has no power");
		helper.assertTrue(!ignition.spark() && Math.abs(battery.charge() - 1.5f) < 1e-3, "and it takes nothing when it cannot fire: " + battery.charge());
		ignition.setEngineCold(0);
		helper.assertTrue(ignition.powered() && ignition.spark(), "but a warm engine can still use what is left");
		helper.succeed();
	}

	@GameTest(template = "empty", batch = "battery-depth")
	public static void aBankPowersAPlugThatNoSingleBatteryCouldCover(GameTestHelper helper) {
		BatteryBlockEntity a = place(helper, A, 0.6f);
		BatteryBlockEntity b = place(helper, B, 0.6f);
		BatteryIgnition ignition = new BatteryIgnition(helper.getLevel(), List.of(helper.absolutePos(A), helper.absolutePos(B)));
		ignition.setEngineCold(0);
		helper.assertTrue(ignition.powered() && ignition.spark(), "Two batteries with 0.6 each give one spark between them");
		helper.assertTrue(Math.abs(a.charge() - 0.1f) < 1e-3 && Math.abs(b.charge() - 0.1f) < 1e-3,
			"taken evenly from both: " + a.charge() + " and " + b.charge());
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 200, batch = "battery-depth")
	public static void aComparatorReadsTheChargeOfTheWholeBank(GameTestHelper helper) {
		BatteryBlockEntity a = place(helper, A, 0);
		BatteryBlockEntity b = place(helper, B, 0);
		// A vanilla comparator's facing is the side it reads from, so this one faces west to read the battery
		BlockPos comparator = A.east();
		helper.setBlock(comparator.below(), Blocks.STONE);
		helper.setBlock(comparator, Blocks.COMPARATOR.defaultBlockState()
			.setValue(ComparatorBlock.FACING, Direction.WEST));
		int[] phase = {0};
		helper.succeedWhen(() -> {
			ComparatorBlockEntity reading = helper.getBlockEntity(comparator);
			int signal = helper.getBlockState(A)
				.getAnalogOutputSignal(helper.getLevel(), helper.absolutePos(A));
			if (phase[0] == 0) {
				helper.assertTrue(signal == 0 && reading.getOutputSignal() == 0, "A flat bank reads 0: " + signal);
				a.charge(BatteryBlockEntity.CAPACITY);
				phase[0] = 1;
			}
			if (phase[0] == 1) {
				helper.assertTrue(signal == BatteryChemistry.signal(0.5) && helper.getBlockState(B)
					.getAnalogOutputSignal(helper.getLevel(), helper.absolutePos(B)) == signal,
					"One of two charged is half a bank on either battery: " + signal);
				helper.assertTrue(reading.getOutputSignal() == signal, "The comparator beside it follows: " + reading.getOutputSignal());
				b.charge(BatteryBlockEntity.CAPACITY);
				phase[0] = 2;
			}
			helper.assertTrue(signal == 15 && reading.getOutputSignal() == 15, "A full bank reads 15: " + signal + " / " + reading.getOutputSignal());
		});
	}

	@GameTest(template = "empty", timeoutTicks = 200, batch = "battery-depth")
	public static void theLampStripOnTheLidFollowsTheBatterysOwnCharge(GameTestHelper helper) {
		BatteryBlockEntity battery = place(helper, A, 0);
		int[] phase = {0};
		helper.succeedWhen(() -> {
			int shown = helper.getBlockState(A)
				.getValue(BatteryBlock.CHARGE);
			if (phase[0] == 0) {
				helper.assertTrue(shown == 0, "A flat battery's lamps are all dark: " + shown);
				battery.charge(BatteryBlockEntity.CAPACITY / 2);
				phase[0] = 1;
			}
			if (phase[0] == 1) {
				helper.assertTrue(shown == 2, "Half charged lights two of the four lamps: " + shown);
				battery.charge(BatteryBlockEntity.CAPACITY);
				phase[0] = 2;
			}
			if (phase[0] == 2) {
				helper.assertTrue(shown == BatteryChemistry.GAUGE_STAGES, "Full lights all four: " + shown);
				battery.draw(BatteryBlockEntity.CAPACITY);
				phase[0] = 3;
			}
			helper.assertTrue(shown == 0, "Drained, they go dark again: " + shown);
		});
	}

	@GameTest(template = "empty", batch = "battery-depth")
	public static void changingTheLampStripDoesNotRebuildTheEngineOrSplitTheBank(GameTestHelper helper) {
		BatteryBlockEntity a = place(helper, A, 0);
		BatteryBlockEntity b = place(helper, B, 0);
		a.bank();
		int topology = BatteryBlock.topologyForTests();
		helper.setBlock(A, helper.getBlockState(A)
			.setValue(BatteryBlock.CHARGE, 3));
		helper.assertTrue(BatteryBlock.topologyForTests() == topology, "A lamp strip change is not a battery placed or removed");
		helper.assertTrue(helper.getBlockEntity(A) == a && a.bank().size() == 2 && a.bank().contains(b),
			"The battery keeps its block entity and its bank");
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 400, batch = "battery-depth")
	public static void aStarterDrawsOnTheBatteriesOfASparkEngineAsItCranks(GameTestHelper helper) {
		TestEngine.buildSpark(helper.getLevel(), helper.absolutePos(BlockPos.ZERO), true);
		// Nothing to burn and nothing to charge with: only the starter can be taking charge
		var head = (com.createengines.content.head.CylinderHeadBlockEntity) helper.getBlockEntity(TestEngine.HEAD);
		head.tank()
			.drain(1000, net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
		helper.setBlock(TestEngine.ALTERNATOR, Blocks.AIR);
		BatteryBlockEntity battery = battery(helper, TestEngine.BATTERY);
		battery.charge(BatteryBlockEntity.CAPACITY);
		helper.runAtTickTime(150, () -> {
			// The plug takes a spark a cycle as well (under 15 of them here); the starter takes the rest
			helper.assertTrue(battery.charge() < BatteryBlockEntity.CAPACITY - 20,
				"Cranking for a few seconds should have taken charge: " + battery.charge());
			helper.assertTrue(battery.charge() > 100, "but only a little of it: " + battery.charge());
			helper.succeed();
		});
	}

	@GameTest(template = "empty", timeoutTicks = 400, batch = "battery-crank-off")
	public static void withCrankingDrawOffACrankingStarterTakesNothing(GameTestHelper helper) {
		boolean before = Tuning.crankingDraw;
		Tuning.crankingDraw = false;
		TestEngine.buildSpark(helper.getLevel(), helper.absolutePos(BlockPos.ZERO), true);
		var head = (com.createengines.content.head.CylinderHeadBlockEntity) helper.getBlockEntity(TestEngine.HEAD);
		head.tank()
			.drain(1000, net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
		helper.setBlock(TestEngine.ALTERNATOR, Blocks.AIR);
		BatteryBlockEntity battery = battery(helper, TestEngine.BATTERY);
		battery.charge(BatteryBlockEntity.CAPACITY);
		helper.runAtTickTime(150, () -> {
			// The plug still takes a spark each cycle; only the starter's draw is off
			helper.assertTrue(battery.charge() > BatteryBlockEntity.CAPACITY - 15, "With the feature off cranking is free: " + battery.charge());
			Tuning.crankingDraw = before;
			helper.succeed();
		});
	}

	@GameTest(template = "empty", timeoutTicks = 400, batch = "battery-depth")
	public static void aCompressionEngineStarterNeverDrawsOnABatteryBesideIt(GameTestHelper helper) {
		TestEngine.build(helper.getLevel(), helper.absolutePos(BlockPos.ZERO), false);
		helper.setBlock(TestEngine.BATTERY, CEBlocks.BATTERY.getDefaultState());
		BatteryBlockEntity battery = battery(helper, TestEngine.BATTERY);
		battery.charge(BatteryBlockEntity.CAPACITY);
		helper.runAtTickTime(150, () -> {
			helper.assertTrue(battery.charge() == BatteryBlockEntity.CAPACITY,
				"A compression engine has no plugs to feed, so cranking it takes no charge: " + battery.charge());
			helper.succeed();
		});
	}

	@GameTest(template = "empty", timeoutTicks = 400, batch = "battery-depth")
	public static void aFlatBankDoesNotSlowTheStarterButTheStarterSaysItIsNearlyFlat(GameTestHelper helper) {
		TestEngine.buildSpark(helper.getLevel(), helper.absolutePos(BlockPos.ZERO), true);
		helper.setBlock(TestEngine.ALTERNATOR, Blocks.AIR);
		var head = (com.createengines.content.head.CylinderHeadBlockEntity) helper.getBlockEntity(TestEngine.HEAD);
		head.tank()
			.drain(1000, net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
		helper.runAtTickTime(100, () -> {
			StarterBlockEntity starter = helper.getBlockEntity(TestEngine.STARTER);
			helper.assertTrue(starter.isBatteryWeak(), "With a flat battery the starter says the batteries are nearly flat");
			var crank = (com.createengines.content.crankshaft.CrankshaftBlockEntity) helper.getBlockEntity(TestEngine.PRIMARY);
			double rpm = Math.abs(crank.getSpeed());
			helper.assertTrue(Math.abs(rpm - TestEngine.MOTOR_RPM) < 1,
				"but it turns the engine as fast as ever, so an engine that catches at that speed still does: " + rpm + " RPM");
			helper.succeed();
		});
	}

	@GameTest(template = "empty", timeoutTicks = 200, batch = "battery-depth")
	public static void aBatterysChargeRateAndTheTimeLeftFollowWhatIsBeingTaken(GameTestHelper helper) {
		BatteryBlockEntity battery = place(helper, A, BatteryBlockEntity.CAPACITY);
		helper.runAtTickTime(2, () -> {
			helper.assertTrue(Math.abs(battery.rate()) < 1e-3, "A battery nothing touches is not changing: " + battery.rate());
		});
		for (int tick = 3; tick < 100; tick++)
			helper.runAtTickTime(tick, () -> battery.draw(1));
		helper.runAtTickTime(100, () -> {
			helper.assertTrue(battery.rate() < -0.5f && battery.rate() > -1.5f,
				"Taking a spark a tick reads as about -1 a tick: " + battery.rate());
			helper.succeed();
		});
	}

	@GameTest(template = "empty_large", timeoutTicks = 100, batch = "battery-depth")
	public static void aBlueprintRecordsTheWholeBankButNotWhatItsLampsShow(GameTestHelper helper) {
		TestEngine.buildSpark(helper.getLevel(), helper.absolutePos(BlockPos.ZERO), true);
		BlockPos second = TestEngine.BATTERY.relative(Direction.NORTH);
		BlockPos third = second.relative(Direction.WEST);
		battery(helper, TestEngine.BATTERY).charge(BatteryBlockEntity.CAPACITY);
		place(helper, second, BatteryBlockEntity.CAPACITY);
		place(helper, third, BatteryBlockEntity.CAPACITY);
		// The lamps light within half a second; the blueprint is taken after that
		helper.runAtTickTime(30, () -> {
			helper.assertTrue(helper.getBlockState(third)
				.getValue(BatteryBlock.CHARGE) == BatteryChemistry.GAUGE_STAGES, "The lamps are lit by now");
			var result = com.createengines.content.blueprint.BlueprintRecorder.record(helper.getLevel(), helper.absolutePos(TestEngine.HEAD), "n", "t");
			helper.assertTrue(result.ok(), "recording failed: " + result.failure());
			var batteries = result.blueprint()
				.parts()
				.stream()
				.filter(part -> part.block()
					.equals("create_engines:battery"))
				.toList();
			helper.assertTrue(batteries.size() == 3, "The whole bank is recorded, including the battery that touches only another: " + batteries.size());
			helper.assertTrue(batteries.stream()
				.allMatch(part -> part.state()
					.isEmpty()), "A lit lamp strip is not part of the blueprint: " + batteries);
			helper.succeed();
		});
	}

	@AfterBatch(batch = "battery-cold")
	public static void clearTheColdBiome(ServerLevel level) {
		BatteryBlockEntity.biomeTemperatureOverride = null;
	}

	@AfterBatch(batch = "battery-cold-off")
	public static void restoreBatteryCold(ServerLevel level) {
		BatteryBlockEntity.biomeTemperatureOverride = null;
		Tuning.batteryCold = true;
	}

	@AfterBatch(batch = "battery-crank-off")
	public static void restoreCrankingDraw(ServerLevel level) {
		Tuning.crankingDraw = true;
	}
}
