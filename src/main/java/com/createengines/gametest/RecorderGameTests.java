package com.createengines.gametest;

import static com.createengines.gametest.EngineRigs.*;

import com.createengines.CreateEngines;
import com.createengines.content.blackbox.BlackBoxBlockEntity;
import com.createengines.content.dyno.DynoBlockEntity;
import com.createengines.engine.EngineState;
import com.createengines.registry.CEBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** The dynamometer and the flight recorder. Test engines as in {@link EngineGameTests}. */
@GameTestHolder(CreateEngines.MOD_ID)
@PrefixGameTestTemplate(false)
public class RecorderGameTests {

	/** A dynamometer against the engine shows its measured curve. */
	@GameTest(template = "empty", timeoutTicks = 900)
	public static void aDynamometerShowsTheEnginesCurve(GameTestHelper helper) {
		buildSingleCylinder(helper, true);
		BlockPos dyno = new BlockPos(2, 2, 1);
		helper.setBlock(dyno, CEBlocks.DYNO.getDefaultState());
		helper.runAtTickTime(300, () -> helper.setBlock(STARTER_POWER, Blocks.AIR));
		helper.runAtTickTime(800, () -> {
			DynoBlockEntity be = helper.getBlockEntity(dyno);
			var readout = be.readout();
			helper.assertTrue(readout != null && readout.rpm().length >= 16, "The dyno should have a curve: " + readout);
			helper.assertTrue(readout.complete(), "The curve should be finished by now");
			helper.assertTrue(readout.torque()[readout.peakTorque()] > 0, "A running engine makes torque");
			helper.assertTrue(readout.current() > 0, "And the dyno knows its speed: " + readout.current());
			helper.succeed();
		});
	}

	/** A flight recorder set against an engine keeps its speed, and logs the start. */
	@GameTest(template = "empty", timeoutTicks = 1100)
	public static void aFlightRecorderLogsAnEnginesLife(GameTestHelper helper) {
		buildSingleCylinder(helper, true);
		BlockPos box = new BlockPos(2, 2, 1);
		helper.setBlock(box, CEBlocks.BLACK_BOX.getDefaultState());
		helper.runAtTickTime(300, () -> helper.setBlock(STARTER_POWER, Blocks.AIR));
		helper.runAtTickTime(1000, () -> {
			BlackBoxBlockEntity be = helper.getBlockEntity(box);
			var readout = be.readout();
			helper.assertTrue(be.engineFound(), "It should have found the engine");
			helper.assertTrue(readout != null && !readout.isEmpty() && readout.span() >= 20,
				"It should have most of the run on tape: " + readout);
			int fastest = 0;
			for (int rpm : readout.rpm())
				fastest = Math.max(fastest, rpm);
			helper.assertTrue(fastest > 20, "And the engine turned: " + fastest);
			helper.assertTrue(readout.events()
				.stream()
				.anyMatch(event -> event.to() == EngineState.RUNNING), "Cranking to running should be an event: " + readout.events());
			helper.succeed();
		});
	}

	/** An engine that will not start is logged as stalling for want of fuel. */
	@GameTest(template = "empty", timeoutTicks = 1100)
	public static void aFlightRecorderSaysWhyAnEngineStalled(GameTestHelper helper) {
		buildSingleCylinder(helper, false);
		BlockPos box = new BlockPos(2, 2, 1);
		helper.setBlock(box, CEBlocks.BLACK_BOX.getDefaultState());
		helper.runAtTickTime(300, () -> helper.setBlock(STARTER_POWER, Blocks.AIR));
		helper.runAtTickTime(1000, () -> {
			BlackBoxBlockEntity be = helper.getBlockEntity(box);
			var events = be.readout()
				.events();
			helper.assertTrue(events.stream()
				.anyMatch(event -> event.to() == EngineState.STALLED
					&& event.cause() == com.createengines.engine.FlightRecorder.Cause.NO_FUEL),
				"An unfuelled engine stalls for want of fuel: " + events);
			helper.succeed();
		});
	}

	/** Alone, a flight recorder has nothing to record. */
	@GameTest(template = "empty")
	public static void aFlightRecorderAloneRecordsNothing(GameTestHelper helper) {
		BlockPos box = new BlockPos(1, 1, 1);
		helper.setBlock(box, CEBlocks.BLACK_BOX.getDefaultState());
		helper.runAtTickTime(100, () -> {
			BlackBoxBlockEntity be = helper.getBlockEntity(box);
			helper.assertTrue(!be.engineFound() && be.recorder()
				.size() == 0, "No engine, no tape");
			helper.succeed();
		});
	}

	/** Alone, a dynamometer reads nothing. */
	@GameTest(template = "empty")
	public static void aDynamometerAloneReadsNothing(GameTestHelper helper) {
		BlockPos dyno = new BlockPos(1, 1, 1);
		helper.setBlock(dyno, CEBlocks.DYNO.getDefaultState());
		helper.runAtTickTime(40, () -> {
			DynoBlockEntity be = helper.getBlockEntity(dyno);
			helper.assertTrue(be.readout() == null && be.signal() == 0, "No engine, no figures");
			helper.succeed();
		});
	}
}
