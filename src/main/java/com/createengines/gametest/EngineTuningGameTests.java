package com.createengines.gametest;

import static com.createengines.gametest.EngineRigs.*;

import com.createengines.CreateEngines;
import com.createengines.content.airflow.HeatExchangerBlockEntity;
import com.createengines.content.airflow.MafSensorBlockEntity;
import com.createengines.content.airflow.ManifoldBlock;
import com.createengines.content.airflow.PortOpeningBlock;
import com.createengines.content.airflow.ThrottleBlockEntity;
import com.createengines.content.camshaft.CamshaftBlockEntity;
import com.createengines.content.crankshaft.CrankshaftBlockEntity;
import com.createengines.content.cylinder.CylinderBlock;
import com.createengines.content.cylinder.CylinderBlockEntity;
import com.createengines.content.head.CylinderHeadBlock;
import com.createengines.content.head.CylinderHeadBlockEntity;
import com.createengines.content.governor.GovernorBlockEntity;
import com.createengines.content.tool.TimingToolItem;
import com.createengines.engine.Signals;
import com.createengines.engine.CylinderStatus;
import com.createengines.engine.EngineState;
import com.createengines.engine.EngineSummary;
import com.createengines.engine.sim.CycleResult;
import com.createengines.engine.topology.EngineLayout;
import com.createengines.engine.topology.EngineScanner;
import com.createengines.engine.topology.CylinderFault;
import com.createengines.engine.topology.FiringPlanner;
import com.createengines.registry.CEBlocks;
import com.createengines.registry.CEFluids;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Pipes, throttle, MAF sensor, heat exchanger, governor, Timing Tool and precision parts. Test engines as in {@link EngineGameTests}. */
@GameTestHolder(CreateEngines.MOD_ID)
@PrefixGameTestTemplate(false)
public class EngineTuningGameTests {

	/** An intake pipe joins a head to an opening: the engine breathes through it, and cutting it chokes the engine. */
	@GameTest(template = "empty", timeoutTicks = 1200)
	public static void aPipeCarriesTheIntakeAndCuttingItChokesTheEngine(GameTestHelper helper) {
		buildSingleCylinder(helper, true);
		BlockPos pipe = TestEngine.AIR_INTAKE;
		helper.setBlock(pipe, Blocks.AIR);
		// Placed as a player would: it opens towards the head beside it
		BlockState placed = placePipe(helper, CEBlocks.INTAKE_MANIFOLD.get(), pipe);
		helper.assertTrue(placed.getValue(ManifoldBlock.CONNECTIONS.get(Direction.SOUTH)),
			"A pipe placed beside a head should open towards it");
		helper.assertFalse(placed.getValue(ManifoldBlock.CONNECTIONS.get(Direction.WEST)),
			"Nothing is there yet to open towards");
		helper.setBlock(pipe, placed);
		// An opening placed against the pipe opens the pipe towards itself
		helper.setBlock(pipe.west(), CEBlocks.AIR_INTAKE.getDefaultState()
			.setValue(PortOpeningBlock.FACING, Direction.WEST));
		helper.assertTrue(helper.getBlockState(pipe)
			.getValue(ManifoldBlock.CONNECTIONS.get(Direction.WEST)), "The pipe should open towards the new opening");
		helper.runAtTickTime(300, () -> {
			helper.setBlock(STARTER_POWER, Blocks.AIR);
			helper.assertFalse(CylinderFault.NO_INTAKE.in(cylinder(helper).faults()),
				"The head should breathe through the pipe: " + cylinder(helper));
			helper.assertFalse(CylinderFault.INTAKE_BLOCKED.in(cylinder(helper).faults()),
				"The pipe leads to an open air intake: " + cylinder(helper));
			helper.assertTrue(summary(helper).firing() == 1, "Engine should fire through the pipe: " + summary(helper));
		});
		helper.runAtTickTime(500, () -> {
			helper.assertTrue(summary(helper).state() == EngineState.RUNNING, "Engine should be running first");
			// What a wrench does: close the pipe's end towards the head
			helper.setBlock(pipe, helper.getBlockState(pipe)
				.setValue(ManifoldBlock.CONNECTIONS.get(Direction.SOUTH), false));
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(helper.getTick() > 520, "Pipe not cut yet");
			helper.assertTrue(CylinderFault.NO_INTAKE.in(cylinder(helper).faults()),
				"A cut pipe should leave the head with no intake: " + cylinder(helper));
			CrankshaftBlockEntity crank = helper.getBlockEntity(PRIMARY);
			helper.assertTrue(crank.getSpeed() == 0, "Engine with no intake should stall, speed " + crank.getSpeed());
		});
	}

	/** A throttle body in the intake line holds an engine back without flooding it, and opening it frees it again. */
	@GameTest(template = "empty", timeoutTicks = 1800)
	public static void aThrottleHoldsTheEngineBackUntilItIsOpened(GameTestHelper helper) {
		buildSingleCylinder(helper, true);
		BlockPos body = TestEngine.AIR_INTAKE;
		helper.setBlock(body, Blocks.AIR);
		helper.setBlock(body, placePipe(helper, CEBlocks.THROTTLE.get(), body));
		helper.setBlock(body.west(), CEBlocks.AIR_INTAKE.getDefaultState()
			.setValue(PortOpeningBlock.FACING, Direction.WEST));
		ThrottleBlockEntity throttle = helper.getBlockEntity(body);
		throttle.idle.setValue(ThrottleBlockEntity.MAX_IDLE);
		float[] rpm = new float[2];
		float[] mixture = new float[1];
		helper.runAtTickTime(300, () -> helper.setBlock(STARTER_POWER, Blocks.AIR));
		helper.runAtTickTime(800, () -> {
			CylinderStatus status = cylinder(helper);
			helper.assertTrue(status.result() == CycleResult.FIRED, "Engine should run through the throttle: " + status);
			rpm[0] = summary(helper).rpm();
			mixture[0] = status.mixture();
			throttle.idle.setValue(ThrottleBlockEntity.DEFAULT_IDLE);
		});
		helper.runAtTickTime(1300, () -> {
			CylinderStatus status = cylinder(helper);
			helper.assertTrue(status.result() == CycleResult.FIRED, "Throttled engine should still fire: " + status);
			rpm[1] = summary(helper).rpm();
			helper.assertTrue(rpm[1] < rpm[0] * 0.9f,
				"Closing the throttle should hold the engine back: " + rpm[1] + " RPM vs " + rpm[0]);
			helper.assertTrue(status.mixture() < mixture[0] * 1.1f,
				"The linkage should keep the throttled engine from flooding: " + status.mixture() + " vs " + mixture[0]);
			throttle.idle.setValue(ThrottleBlockEntity.MAX_IDLE);
		});
		helper.runAtTickTime(1750, () -> {
			float opened = summary(helper).rpm();
			helper.assertTrue(opened > rpm[1] * 1.1f,
				"Opening it again should free the engine: " + opened + " RPM vs " + rpm[1]);
			helper.succeed();
		});
	}

	/** With a full redstone signal a bore opened past the plain intake lets an engine breathe, and so turn, faster. */
	@GameTest(template = "empty", timeoutTicks = 1800)
	public static void aWideBoreLetsAnEngineTurnFaster(GameTestHelper helper) {
		buildSingleCylinder(helper, true);
		BlockPos body = TestEngine.AIR_INTAKE;
		helper.setBlock(body, Blocks.AIR);
		helper.setBlock(body, placePipe(helper, CEBlocks.THROTTLE.get(), body));
		helper.setBlock(body.west(), CEBlocks.AIR_INTAKE.getDefaultState()
			.setValue(PortOpeningBlock.FACING, Direction.WEST));
		helper.setBlock(body.above(), Blocks.REDSTONE_BLOCK.defaultBlockState());
		ThrottleBlockEntity throttle = helper.getBlockEntity(body);
		float[] rpm = new float[1];
		helper.runAtTickTime(300, () -> helper.setBlock(STARTER_POWER, Blocks.AIR));
		helper.runAtTickTime(900, () -> {
			helper.assertTrue(throttle.opening() == 1, "A full signal opens the plain bore: " + throttle.opening());
			rpm[0] = summary(helper).rpm();
			throttle.wide.setValue(ThrottleBlockEntity.MAX_WIDE);
		});
		helper.runAtTickTime(1700, () -> {
			helper.assertTrue(throttle.opening() > 1.4, "A full signal opens the wide bore: " + throttle.opening());
			float wide = summary(helper).rpm();
			helper.assertTrue(wide > rpm[0] * 1.03f,
				"A wider bore should let the engine turn faster: " + wide + " RPM vs " + rpm[0]);
			helper.succeed();
		});
	}

	/** A throttle body's idle stop and wide-open bore are two scroll values on one block; each keeps its own setting when saved. */
	@GameTest(template = "empty")
	public static void aThrottleKeepsItsIdleStopAndItsBoreApart(GameTestHelper helper) {
		BlockPos at = new BlockPos(2, 1, 2);
		helper.setBlock(at, placePipe(helper, CEBlocks.THROTTLE.get(), at));
		ThrottleBlockEntity throttle = helper.getBlockEntity(at);
		throttle.idle.setValue(7);
		throttle.wide.setValue(26);
		var registries = helper.getLevel()
			.registryAccess();
		for (boolean clientPacket : new boolean[] {false, true}) {
			CompoundTag saved = clientPacket ? throttle.getUpdateTag(registries) : throttle.saveWithFullMetadata(registries);
			throttle.idle.setValue(ThrottleBlockEntity.DEFAULT_IDLE);
			throttle.wide.setValue(ThrottleBlockEntity.MIN_WIDE);
			throttle.loadWithComponents(saved, registries);
			helper.assertTrue(throttle.idle.getValue() == 7, "The idle stop should come back as set: " + throttle.idle.getValue());
			helper.assertTrue(throttle.wide.getValue() == 26, "The bore should come back as set: " + throttle.wide.getValue());
			throttle.idle.setValue(7);
			throttle.wide.setValue(26);
		}
		helper.succeed();
	}

	/**
	 * A MAF sensor in the intake. The injector is set to the largest shot it allows, which would run this engine rich at
	 * speed; the MAF sensor holds it to the air the cylinder breathed, so it burns clean, and the sensor belongs to the
	 * engine and puts what it reads out as redstone.
	 */
	@GameTest(template = "empty", timeoutTicks = 1300)
	public static void aMafSensorReadsAndTrimsAnEngine(GameTestHelper helper) {
		buildSingleCylinder(helper, true);
		CylinderHeadBlockEntity head = helper.getBlockEntity(HEAD);
		head.metering.setValue(CylinderHeadBlockEntity.MAX_METERING);
		BlockPos intake = TestEngine.AIR_INTAKE;
		helper.setBlock(intake, Blocks.AIR);
		helper.setBlock(intake, placePipe(helper, CEBlocks.MAF_SENSOR.get(), intake));
		helper.setBlock(intake.west(), CEBlocks.AIR_INTAKE.getDefaultState()
			.setValue(PortOpeningBlock.FACING, Direction.WEST));
		helper.runAtTickTime(300, () -> helper.setBlock(STARTER_POWER, Blocks.AIR));
		helper.runAtTickTime(900, () -> {
			CylinderStatus status = cylinder(helper);
			helper.assertTrue(status.result() == CycleResult.FIRED,
				"Engine should run through the sensor: " + status + ", " + summary(helper));
			helper.assertFalse(status.rich(), "The MAF sensor should keep a double shot from running rich: " + status);
			helper.assertTrue(status.mixture() < 1.3f, "The shot should follow the air: " + status);

			MafSensorBlockEntity maf = helper.getBlockEntity(intake);
			helper.assertTrue(maf.onEngine(), "The MAF sensor should belong to the engine");
			helper.assertTrue(maf.signal() > 0, "It should put out a signal for the air the engine breathes");
			helper.assertTrue(helper.getLevel()
				.getSignal(helper.absolutePos(intake), Direction.NORTH) == maf.signal(),
				"Its signal should reach the blocks around it");

			helper.succeed();
		});
	}

	/**
	 * A MAF sensor puts its reading out as redstone on every side. Beside a camshaft that signal used to close the fuel
	 * rack (1 - signal/15), so the engine ran as lean as the signal was strong while the sensor read it as balanced. A
	 * sensor's output no longer counts for the rack; a redstone block there still does.
	 */
	@GameTest(template = "empty", timeoutTicks = 1300)
	public static void aMafSensorBesideTheCamshaftDoesNotCloseTheRack(GameTestHelper helper) {
		buildSingleCylinder(helper, true);
		BlockPos sensor = new BlockPos(3, 4, 2);
		BlockPos cam = new BlockPos(3, 4, 3);
		helper.setBlock(TestEngine.AIR_INTAKE, Blocks.AIR);
		helper.setBlock(sensor, placePipe(helper, CEBlocks.MAF_SENSOR.get(), sensor));
		helper.setBlock(sensor.east(), CEBlocks.AIR_INTAKE.getDefaultState()
			.setValue(PortOpeningBlock.FACING, Direction.EAST));
		helper.runAtTickTime(300, () -> helper.setBlock(STARTER_POWER, Blocks.AIR));
		helper.runAtTickTime(900, () -> {
			MafSensorBlockEntity maf = helper.getBlockEntity(sensor);
			helper.assertTrue(maf.onEngine(), "The sensor should be on the engine's intake: " + summary(helper));
			helper.assertTrue(maf.signal() > 0, "It should be putting out a signal");
			helper.assertTrue(Signals.bestNeighbour(helper.getLevel(), helper.absolutePos(cam)) > 0,
				"The camshaft should have the sensor's signal beside it, or this proves nothing");
			CylinderStatus status = cylinder(helper);
			helper.assertTrue(status.result() == CycleResult.FIRED,
				"The engine should run with the sensor beside the camshaft: " + status + ", " + summary(helper));
			helper.assertTrue(status.mixture() > 0.6f && status.mixture() < 1.3f,
				"The sensor's signal must not starve the engine: " + status);
			helper.succeed();
		});
	}

	/**
	 * The injector is set to the smallest shot it allows, which would starve this engine. A MAF sensor in the intake
	 * tops the shot up to what the air can burn, so the engine fires on a balanced mixture, and says it is adding fuel.
	 */
	@GameTest(template = "empty", timeoutTicks = 1300)
	public static void aMafSensorTopsUpALeanEngine(GameTestHelper helper) {
		buildSingleCylinder(helper, true);
		CylinderHeadBlockEntity head = helper.getBlockEntity(HEAD);
		head.metering.setValue(CylinderHeadBlockEntity.MIN_METERING);
		BlockPos intake = TestEngine.AIR_INTAKE;
		helper.setBlock(intake, Blocks.AIR);
		helper.setBlock(intake, placePipe(helper, CEBlocks.MAF_SENSOR.get(), intake));
		helper.setBlock(intake.west(), CEBlocks.AIR_INTAKE.getDefaultState()
			.setValue(PortOpeningBlock.FACING, Direction.WEST));
		helper.runAtTickTime(300, () -> helper.setBlock(STARTER_POWER, Blocks.AIR));
		helper.runAtTickTime(900, () -> {
			CylinderStatus status = cylinder(helper);
			helper.assertTrue(status.result() == CycleResult.FIRED,
				"The sensor should feed a starved engine: " + status + ", " + summary(helper));
			helper.assertTrue(status.mixture() > 0.6f && status.mixture() < 1.3f,
				"The shot should follow the air, not the setting: " + status);
			helper.succeed();
		});
	}

	/**
	 * An exhaust heat exchanger in the exhaust of a running cylinder takes the exhaust's heat, shows it as a blaze burner
	 * heat level on its own block (which is where a basin above it looks), and goes cold again when the server's config
	 * switches heat recovery off.
	 */
	@GameTest(template = "empty", timeoutTicks = 1300)
	public static void anExhaustHeatExchangerWarmsItsBlock(GameTestHelper helper) {
		buildSingleCylinder(helper, true);
		helper.setBlock(EXHAUST, Blocks.AIR);
		helper.setBlock(EXHAUST, placePipe(helper, CEBlocks.HEAT_EXCHANGER.get(), EXHAUST));
		helper.setBlock(EXHAUST.above(), CEBlocks.EXHAUST_OUTLET.getDefaultState()
			.setValue(PortOpeningBlock.FACING, Direction.UP));
		helper.runAtTickTime(300, () -> helper.setBlock(STARTER_POWER, Blocks.AIR));
		helper.runAtTickTime(900, () -> {
			HeatExchangerBlockEntity exchanger = helper.getBlockEntity(EXHAUST);
			helper.assertTrue(exchanger.onEngine(), "The exchanger should belong to the engine");
			helper.assertTrue(exchanger.heat() > 0.5f, "A firing cylinder should heat the exhaust: " + exchanger.heat() + ", " + summary(helper) + ", " + cylinder(helper));
			int shown = helper.getBlockState(EXHAUST)
				.getValue(com.simibubi.create.content.processing.burner.BlazeBurnerBlock.HEAT_LEVEL)
				.ordinal();
			helper.assertTrue(exchanger.heatLevel() == com.createengines.engine.sim.HeatRecovery.level(exchanger.heat(),
				exchanger.heatLevel()), "Its level should follow its heat");
			helper.assertTrue((shown > 0) == (exchanger.heatLevel() > 0),
				"The block should carry the heat level a basin reads: " + shown + " against " + exchanger.heatLevel());
			helper.assertTrue(exchanger.signal() > 0, "Its heat should come out as redstone");
			GameTestConfig.heatRecovery(false);
		});
		helper.runAtTickTime(1000, () -> {
			try {
				HeatExchangerBlockEntity exchanger = helper.getBlockEntity(EXHAUST);
				helper.assertTrue(exchanger.heatLevel() == 0 && exchanger.signal() == 0,
					"With heat recovery off the exchanger should be cold: " + exchanger.heatLevel());
				helper.assertTrue(helper.getBlockState(EXHAUST)
					.getValue(com.simibubi.create.content.processing.burner.BlazeBurnerBlock.HEAT_LEVEL)
					== com.simibubi.create.content.processing.burner.BlazeBurnerBlock.HeatLevel.NONE,
					"And so should its block");
			} finally {
				GameTestConfig.heatRecovery(true);
			}
			helper.succeed();
		});
	}

	/**
	 * A blaze burner against the exhaust heat exchanger warms the block of an engine that has never run, through the heat
	 * level on the burner's block, and the engine still reads as stalled.
	 */
	@GameTest(template = "empty", timeoutTicks = 1000)
	public static void aBlazeBurnerAgainstTheExchangerPreheatsTheEngine(GameTestHelper helper) {
		buildSingleCylinder(helper, true);
		helper.setBlock(EXHAUST, Blocks.AIR);
		helper.setBlock(EXHAUST, placePipe(helper, CEBlocks.HEAT_EXCHANGER.get(), EXHAUST));
		helper.setBlock(EXHAUST.above(), CEBlocks.EXHAUST_OUTLET.getDefaultState()
			.setValue(PortOpeningBlock.FACING, Direction.UP));
		helper.setBlock(EXHAUST.west(), com.simibubi.create.AllBlocks.BLAZE_BURNER.getDefaultState()
			.setValue(com.simibubi.create.content.processing.burner.BlazeBurnerBlock.HEAT_LEVEL,
				com.simibubi.create.content.processing.burner.BlazeBurnerBlock.HeatLevel.SEETHING));
		helper.setBlock(STARTER_POWER, Blocks.AIR);
		helper.runAtTickTime(900, () -> {
			EngineSummary summary = summary(helper);
			helper.assertTrue(summary.state() == EngineState.STALLED, "It should not be running: " + summary);
			helper.assertTrue(summary.temperature() > 0.1f, "A burner against the exchanger should warm it: " + summary);
			helper.assertTrue(summary.cooling() == (summary.temperature() >= com.createengines.engine.sim.Thermal.COOLING_FROM),
				"A warm, stopped engine is cooling down: " + summary);
			helper.succeed();
		});
	}

	/** An engine whose intake has been taken away reads as failing, with no air as the cause, in plain terms. */
	@GameTest(template = "empty", timeoutTicks = 400)
	public static void anEngineWithNoIntakeReadsAsFailingForWantOfAir(GameTestHelper helper) {
		buildSingleCylinder(helper, true);
		helper.setBlock(TestEngine.AIR_INTAKE, Blocks.AIR);
		helper.runAtTickTime(200, () -> {
			EngineSummary summary = summary(helper);
			helper.assertTrue(summary.health() == com.createengines.engine.Diagnosis.Health.FAILING
				&& summary.cause() == com.createengines.engine.Diagnosis.Cause.NO_AIR,
				"With no intake the engine should read as failing for want of air: " + summary);
			helper.succeed();
		});
	}

	/**
	 * The fuel consumption setting changes what the tanks pay for the same burn: the gallery hands the engine what it
	 * asked for and takes three times as much from the tank at 3, half as much at 0.5. Done within one tick, because
	 * the setting is global and other tests' engines are running.
	 */
	@GameTest(template = "empty", timeoutTicks = 20)
	public static void theFuelConsumptionSettingScalesWhatTheTanksPay(GameTestHelper helper) {
		buildSingleCylinder(helper, true);
		CylinderHeadBlockEntity head = helper.getBlockEntity(HEAD);
		com.createengines.engine.GalleryFuelSupply supply = new com.createengines.engine.GalleryFuelSupply(
			helper.getLevel(), java.util.List.of(helper.absolutePos(HEAD)));
		try {
			int before = head.tank()
				.getFluidAmount();
			com.createengines.engine.sim.Tuning.fuelConsumption = 3 * com.createengines.engine.sim.CylinderSpec.SPEED_GAIN;
			double delivered = supply.draw(10);
			helper.assertTrue(Math.abs(delivered - 10) < 1e-9, "The engine should still get what it asked for: " + delivered);
			helper.assertTrue(before - head.tank()
				.getFluidAmount() == 30, "At 3 the tank should pay 30 mB for 10: " + (before - head.tank()
					.getFluidAmount()));
			before = head.tank()
				.getFluidAmount();
			com.createengines.engine.sim.Tuning.fuelConsumption = 0.5 * com.createengines.engine.sim.CylinderSpec.SPEED_GAIN;
			delivered = supply.draw(10);
			helper.assertTrue(Math.abs(delivered - 10) < 1e-9, "Nor should it get less at a thrifty setting: " + delivered);
			helper.assertTrue(before - head.tank()
				.getFluidAmount() == 5, "At 0.5 the tank should pay 5 mB for 10: " + (before - head.tank()
					.getFluidAmount()));
		} finally {
			com.createengines.engine.sim.Tuning.fuelConsumption = 1;
		}
		helper.succeed();
	}

	/**
	 * With rotary engines switched off in the config a housing sits inert and says so. Scanned within one tick, because
	 * the setting is global and other tests are scanning rotors.
	 */
	@GameTest(template = "empty", timeoutTicks = 20)
	public static void switchingRotaryEnginesOffLeavesAHousingInert(GameTestHelper helper) {
		TestEngine.buildRotary(helper.getLevel(), helper.absolutePos(BlockPos.ZERO), true);
		try {
			com.createengines.engine.sim.Tuning.rotaryEngines = false;
			EngineLayout layout = EngineScanner.scan(helper.getLevel(), helper.absolutePos(PRIMARY));
			helper.assertTrue(layout != null && layout.cylinders()
				.size() == 1 && CylinderFault.ROTARY_DISABLED.in(layout.cylinders()
					.getFirst()
					.faults()) && CylinderFault.disables(layout.cylinders()
						.getFirst()
						.faults()),
				"One disabled entry: " + (layout == null ? null : layout.cylinders()));
		} finally {
			com.createengines.engine.sim.Tuning.rotaryEngines = true;
		}
		helper.succeed();
	}

	/**
	 * A governor against the crankshaft holds the engine at its setting: left to itself the single cylinder would rev
	 * well past 80, but with the rack governed it settles there with the rack part-closed to do it.
	 */
	@GameTest(template = "empty", timeoutTicks = 1500)
	public static void aGovernorHoldsAnEngineAtItsSetSpeed(GameTestHelper helper) {
		buildSingleCylinder(helper, true);
		BlockPos at = new BlockPos(3, 3, 2);
		helper.setBlock(at, CEBlocks.GOVERNOR.getDefaultState());
		GovernorBlockEntity governor = helper.getBlockEntity(at);
		int target = 80;
		governor.target.setValue(target);
		float[] peak = new float[1];
		helper.runAtTickTime(300, () -> helper.setBlock(STARTER_POWER, Blocks.AIR));
		helper.succeedWhen(() -> {
			EngineSummary summary = summary(helper);
			if (helper.getTick() > 450)
				peak[0] = Math.max(peak[0], summary.rpm());
			helper.assertTrue(helper.getTick() > 1200, "Watching the governor");
			helper.assertTrue(summary.state() == EngineState.RUNNING, "The governed engine should run: " + summary);
			helper.assertTrue(Math.abs(summary.rpm() - target) <= 6, "It should hold its setting: " + summary);
			helper.assertTrue(peak[0] <= target * 1.12f, "It should never run away, peak " + peak[0] + " RPM");
			helper.assertTrue(governor.signal() > 0 && governor.signal() < 15,
				"Holding the speed takes part of the rack, not all or none: " + governor.signal());
		});
	}

	/**
	 * A governor reads the redstone signal at its block as a command, and does not power its neighbours itself (so its
	 * own output can never come back as a command): next to a block of redstone it asks for all of its setting.
	 */
	@GameTest(template = "empty", timeoutTicks = 100)
	public static void aGovernorTakesARedstoneCommandAndPowersNothing(GameTestHelper helper) {
		buildSingleCylinder(helper, true);
		BlockPos at = new BlockPos(3, 3, 2);
		helper.setBlock(at, CEBlocks.GOVERNOR.getDefaultState());
		GovernorBlockEntity governor = helper.getBlockEntity(at);
		governor.target.setValue(80);
		helper.assertTrue(governor.command() == 0, "Nothing powers it yet: " + governor.command());
		helper.assertTrue(Math.abs(governor.governedSpeed() - 80) < 1e-9, "No signal leaves the setting: " + governor.governedSpeed());
		helper.assertFalse(helper.getBlockState(at)
			.isSignalSource(), "It reaches comparators only");
		helper.setBlock(at.above(), Blocks.REDSTONE_BLOCK);
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(governor.command() == 15, "A block of redstone is a full command: " + governor.command());
			helper.assertTrue(Math.abs(governor.governedSpeed() - 80) < 1e-9, "A full command asks for the whole setting");
			helper.succeed();
		});
	}

	/**
	 * Two cylinders on throws set the same way fire together, which is as uneven as two can be. The timing tool sets
	 * the second throw (or lobe) so they fire half a cycle apart, and the engine then runs on it.
	 */
	@GameTest(template = "empty", timeoutTicks = 900)
	public static void theTimingToolSpreadsTwoCylindersEvenly(GameTestHelper helper) {
		buildSingleCylinder(helper, true);
		BlockPos head2 = new BlockPos(4, 4, 2);
		BlockPos lobe2 = new BlockPos(4, 4, 3);
		BlockPos cylinder2 = new BlockPos(4, 3, 2);
		helper.setBlock(new BlockPos(4, 2, 2), CEBlocks.CRANK_THROW.getDefaultState()
			.setValue(BlockStateProperties.AXIS, Axis.X));
		helper.setBlock(cylinder2, CEBlocks.CYLINDER.getDefaultState()
			.setValue(CylinderBlock.FACING, Direction.UP)
			.setValue(CylinderBlock.HAS_PISTON, true)
			.setValue(CylinderBlock.HAS_ROD, true));
		helper.setBlock(head2, CEBlocks.CYLINDER_HEAD.getDefaultState()
			.setValue(CylinderHeadBlock.FACING, Direction.UP)
			.setValue(CylinderHeadBlock.HAS_INJECTOR, true));
		helper.setBlock(new BlockPos(4, 4, 1), CEBlocks.AIR_INTAKE.getDefaultState()
			.setValue(PortOpeningBlock.FACING, Direction.NORTH));
		helper.setBlock(new BlockPos(5, 4, 2), CEBlocks.EXHAUST_OUTLET.getDefaultState()
			.setValue(PortOpeningBlock.FACING, Direction.EAST));
		helper.setBlock(lobe2, CEBlocks.CAM_LOBE.getDefaultState()
			.setValue(BlockStateProperties.AXIS, Axis.X));
		// A lobe angle that suits neither cylinder
		CamshaftBlockEntity cam2 = helper.getBlockEntity(lobe2);
		cam2.lobeAngle.setValue(7);

		helper.runAtTickTime(2, () -> {
			TimingToolItem.Result result =
				TimingToolItem.apply(helper.getLevel(), helper.absolutePos(PRIMARY), FiringPlanner.Style.SMOOTH);
			helper.assertTrue(result != null && result.plan() != null, "The tool should find the engine's cylinders");
			helper.assertTrue(result.plan()
				.even(), "Two cylinders can always be spaced evenly: " + result.plan());
		});
		helper.runAtTickTime(300, () -> {
			EngineSummary summary = summary(helper);
			helper.assertTrue(summary.firing() == 2, "Both timed cylinders should fire: " + summary);
			float[] intervals = summary.firingIntervals();
			helper.assertTrue(intervals.length == 2 && Math.abs(intervals[0] - 360) < 1 && Math.abs(intervals[1] - 360) < 1,
				"They should fire half a cycle apart: " + java.util.Arrays.toString(intervals));
			CylinderBlockEntity second = helper.getBlockEntity(cylinder2);
			helper.assertTrue(second.status() != null && Math.abs(second.status()
				.timingError()) < 1, "The second cylinder should be timed: " + second.status());
			helper.assertTrue(Math.abs(cylinder(helper).timingError()) < 1,
				"The first cylinder should be timed: " + cylinder(helper));
			helper.succeed();
		});
	}

	/**
	 * Precision parts change what an engine is rated for: the same cylinder rebuilt with a precision bore, head,
	 * forged piston and precision injector carries clearly more than it did.
	 */
	@GameTest(template = "empty", timeoutTicks = 800)
	public static void precisionPartsRaiseAnEnginesRating(GameTestHelper helper) {
		buildSingleCylinder(helper, true);
		float[] crude = new float[1];
		helper.runAtTickTime(200, () -> {
			CrankshaftBlockEntity crank = helper.getBlockEntity(PRIMARY);
			crude[0] = crank.publishedCapacity();
			helper.assertTrue(crude[0] > 0, "The crude engine should be rated");
			helper.assertTrue(summary(helper).sound()
				.precision() == 0, "Crude parts sound crude: " + summary(helper).sound()
					.precision());
		});
		helper.runAtTickTime(210, () -> {
			helper.setBlock(CYLINDER, CEBlocks.PRECISION_CYLINDER.getDefaultState()
				.setValue(CylinderBlock.FACING, Direction.UP)
				.setValue(CylinderBlock.HAS_PISTON, true)
				.setValue(CylinderBlock.FORGED_PISTON, true)
				.setValue(CylinderBlock.HAS_ROD, true));
			helper.setBlock(HEAD, CEBlocks.PRECISION_CYLINDER_HEAD.getDefaultState()
				.setValue(CylinderHeadBlock.FACING, Direction.UP)
				.setValue(CylinderHeadBlock.HAS_INJECTOR, true)
				.setValue(CylinderHeadBlock.FINE_INJECTOR, true));
			CylinderHeadBlockEntity head = helper.getBlockEntity(HEAD);
			head.oilTank()
				.fill(new FluidStack(CEFluids.ENGINE_OIL.get()
					.getSource(), 250), FluidAction.EXECUTE);
			head.tank()
				.fill(new FluidStack(CEFluids.DIESEL.get()
					.getSource(), 250), FluidAction.EXECUTE);
		});
		helper.runAtTickTime(600, () -> {
			CrankshaftBlockEntity crank = helper.getBlockEntity(PRIMARY);
			helper.assertTrue(crank.publishedCapacity() > crude[0] * 1.15f,
				"Precision parts should raise the rating from " + crude[0] + ": " + crank.publishedCapacity());
			helper.assertTrue(summary(helper).sound()
				.precision() == 1, "The sound should follow the parts: " + summary(helper).sound()
					.precision());
			helper.succeed();
		});
	}
}
