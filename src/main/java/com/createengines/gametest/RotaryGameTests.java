package com.createengines.gametest;

import static com.createengines.gametest.EngineRigs.*;

import com.createengines.CreateEngines;
import com.createengines.content.airflow.PortOpeningBlock;
import com.createengines.content.battery.BatteryBlockEntity;
import com.createengines.content.rotor.RotorHousingBlock;
import com.createengines.content.rotor.RotorHousingBlockEntity;
import com.createengines.content.transmission.Gears;
import com.createengines.content.transmission.TransmissionBlockEntity;
import com.createengines.content.tool.TimingToolItem;
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
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Rotary engines. Test engines as in {@link EngineGameTests}. */
@GameTestHolder(CreateEngines.MOD_ID)
@PrefixGameTestTemplate(false)
public class RotaryGameTests {

	/** A rotor housing is a crank link, head and cylinder in one: three chambers, no camshaft, nothing missing. */
	@GameTest(template = "empty", timeoutTicks = 100)
	public static void aRotorHousingScansAsThreeChambersAndNeedsNoCam(GameTestHelper helper) {
		TestEngine.buildRotary(helper.getLevel(), helper.absolutePos(BlockPos.ZERO), true);
		helper.runAtTickTime(20, () -> {
			EngineLayout layout = EngineScanner.scan(helper.getLevel(), helper.absolutePos(PRIMARY));
			helper.assertTrue(layout != null && layout.cylinders()
				.size() == 3, "A rotor makes three chambers: " + (layout == null ? null : layout.cylinders()));
			for (int i = 0; i < 3; i++) {
				EngineLayout.Cylinder chamber = layout.cylinders()
					.get(i);
				helper.assertTrue(chamber.rotary() && chamber.chamber() == i, "Chamber " + i + ": " + chamber);
				helper.assertTrue(chamber.faults() == 0, "No cam, head or throw to be missing: " + chamber.faults());
				helper.assertTrue(chamber.lobe() == null && chamber.camLine() < 0, "A rotor has no cam");
				helper.assertTrue(chamber.intake() >= 0 && chamber.exhaust() >= 0, "Its ports are found like a head's");
			}
			helper.assertTrue(layout.heads()
				.contains(helper.absolutePos(TestEngine.HOUSING)), "The housing is in the fuel gallery");
			helper.succeed();
		});
	}

	/** Cranked by the starter, a rotor on gasoline lights, runs on its own and keeps all three chambers firing. */
	@GameTest(template = "empty", timeoutTicks = 1000)
	public static void aRotaryEngineStartsAndRunsOnGasoline(GameTestHelper helper) {
		TestEngine.buildRotary(helper.getLevel(), helper.absolutePos(BlockPos.ZERO), true);
		helper.runAtTickTime(300, () -> {
			EngineSummary summary = summary(helper);
			helper.assertTrue(summary.firing() >= 1, "The rotor should fire under the starter: " + summary);
			BatteryBlockEntity battery = helper.getBlockEntity(TestEngine.BATTERY);
			helper.assertTrue(battery.charge() > 0, "Cranking should have charged the battery");
			helper.setBlock(STARTER_POWER, Blocks.AIR);
		});
		helper.runAtTickTime(800, () -> {
			EngineSummary summary = summary(helper);
			helper.assertTrue(summary.state() == EngineState.RUNNING, "The rotary engine should run on its own: " + summary);
			helper.assertTrue(summary.firing() == 3, "All three chambers should fire: " + summary);
			assertSounds(helper, false, 1);
			helper.assertTrue(EngineSummary.read(summary.write())
				.sound()
				.rotary(), "The client is not told the engine is a rotary: " + summary.write());
			RotorHousingBlockEntity housing = helper.getBlockEntity(TestEngine.HOUSING);
			for (int i = 0; i < 3; i++)
				helper.assertTrue(housing.chamber(i) != null && housing.chamber(i)
					.result() == CycleResult.FIRED, "Chamber " + i + ": " + housing.chamber(i));
			helper.assertTrue(housing.tank()
				.getFluidAmount() < 250, "The engine should have burned gasoline");
			helper.succeed();
		});
	}

	/**
	 * Reported by Banjo0300 on Discord (2026-11-20): a rotary housing kept breaking when next to another one as the
	 * engine started. Two housings end to end on one shaft are one kinetic network; neither may be destroyed while it
	 * cranks and lights, and both must still be housings at the end.
	 */
	@GameTest(template = "empty", timeoutTicks = 1000)
	public static void twoTouchingRotorHousingsSurviveStarting(GameTestHelper helper) {
		TestEngine.buildRotary(helper.getLevel(), helper.absolutePos(BlockPos.ZERO), true);
		TestEngine.addSecondRotor(helper.getLevel(), helper.absolutePos(BlockPos.ZERO));
		EveryTick.run(helper, () -> {
			helper.assertTrue(helper.getBlockState(TestEngine.HOUSING).is(CEBlocks.ROTOR_HOUSING.get()),
				"The first rotor housing broke: " + helper.getBlockState(TestEngine.HOUSING));
			helper.assertTrue(helper.getBlockState(TestEngine.HOUSING_2).is(CEBlocks.ROTOR_HOUSING.get()),
				"The second rotor housing broke: " + helper.getBlockState(TestEngine.HOUSING_2));
		});
		helper.runAtTickTime(300, () -> helper.setBlock(STARTER_POWER, Blocks.AIR));
		helper.runAtTickTime(800, () -> {
			EngineSummary summary = summary(helper);
			helper.assertTrue(summary.firing() >= 1, "The twin rotary should have lit: " + summary);
			helper.succeed();
		});
	}

	/** The Banjo0300 report again, with the second housing beside the first on a parallel shaft instead of in line. */
	@GameTest(template = "empty", timeoutTicks = 1000)
	public static void aRotorHousingBesideAnotherOnAParallelShaftSurvivesStarting(GameTestHelper helper) {
		rotorBesideRotor(helper, TestEngine.HOUSING.south(), net.minecraft.core.Direction.Axis.X);
	}

	/** The same, with the neighbouring housing turned across the shaft (axis Z, against the first's X). */
	@GameTest(template = "empty", timeoutTicks = 1000)
	public static void aRotorHousingAcrossAnotherSurvivesStarting(GameTestHelper helper) {
		rotorBesideRotor(helper, TestEngine.HOUSING_2, net.minecraft.core.Direction.Axis.Z);
	}

	/** The same, with the neighbouring housing standing on end (axis Y). */
	@GameTest(template = "empty", timeoutTicks = 1000)
	public static void aRotorHousingBesideAnotherOnEndSurvivesStarting(GameTestHelper helper) {
		rotorBesideRotor(helper, TestEngine.HOUSING_2, net.minecraft.core.Direction.Axis.Y);
	}

	/**
	 * Banjo0300's report once more, the way a player builds it: the rotary is already running under an exhaust manifold
	 * when a second housing goes in beside it, in line and then on a parallel shaft. Neither may break.
	 */
	@GameTest(template = "empty", timeoutTicks = 1500)
	public static void aRotorHousingPlacedBesideARunningRotaryDoesNotBreak(GameTestHelper helper) {
		TestEngine.buildRotary(helper.getLevel(), helper.absolutePos(BlockPos.ZERO), true);
		BlockPos pipeAt = TestEngine.ROTARY_EXHAUST.above();
		helper.setBlock(pipeAt, placePipe(helper, CEBlocks.EXHAUST_MANIFOLD.get(), pipeAt));
		BlockPos inLine = TestEngine.HOUSING_2;
		BlockPos beside = TestEngine.HOUSING.south();
		BlockState housing = TestEngine.axisX(CEBlocks.ROTOR_HOUSING.getDefaultState())
			.setValue(RotorHousingBlock.HAS_ROTOR, true)
			.setValue(RotorHousingBlock.HAS_INJECTOR, true)
			.setValue(RotorHousingBlock.HAS_SPARK_PLUG, true);
		helper.runAtTickTime(300, () -> helper.setBlock(STARTER_POWER, Blocks.AIR));
		helper.runAtTickTime(600, () -> {
			helper.assertTrue(summary(helper).state() == EngineState.RUNNING, "Should be running: " + summary(helper));
			helper.setBlock(beside, housing);
		});
		helper.runAtTickTime(800, () -> helper.setBlock(inLine, housing));
		EveryTick.run(helper, () -> {
			long t = helper.getTick();
			helper.assertTrue(helper.getBlockState(TestEngine.HOUSING).is(CEBlocks.ROTOR_HOUSING.get()),
				"The running housing broke at tick " + t);
			if (t > 600)
				helper.assertTrue(helper.getBlockState(beside).is(CEBlocks.ROTOR_HOUSING.get()),
					"The housing placed beside it broke at tick " + t);
			if (t > 800)
				helper.assertTrue(helper.getBlockState(inLine).is(CEBlocks.ROTOR_HOUSING.get()),
					"The housing placed in line broke at tick " + t);
		});
		helper.runAtTickTime(1200, helper::succeed);
	}

	/**
	 * Two rotary engines on one shaft with a transmission between them: each housing is the source of its own network,
	 * and the gearbox joins the two. Create breaks one end of a source conflict, so neither housing may be left to it.
	 */
	@GameTest(template = "empty", timeoutTicks = 1500)
	public static void twoRotariesEitherSideOfATransmissionSurviveStarting(GameTestHelper helper) {
		rotariesAcrossATransmission(helper, Gears.NEUTRAL);
	}

	@GameTest(template = "empty", timeoutTicks = 1500)
	public static void twoRotariesEitherSideOfATransmissionInFirstSurviveStarting(GameTestHelper helper) {
		rotariesAcrossATransmission(helper, Gears.FIRST);
	}

	@GameTest(template = "empty", timeoutTicks = 1500)
	public static void twoRotariesEitherSideOfATransmissionInReverseSurviveStarting(GameTestHelper helper) {
		rotariesAcrossATransmission(helper, Gears.REVERSE);
	}

	private static void rotariesAcrossATransmission(GameTestHelper helper, int gear) {
		TestEngine.buildRotary(helper.getLevel(), helper.absolutePos(BlockPos.ZERO), true);
		BlockPos box = TestEngine.HOUSING.east();
		BlockPos far = box.east();
		helper.setBlock(box, CEBlocks.TRANSMISSION.getDefaultState()
			.setValue(BlockStateProperties.AXIS, Axis.X));
		((TransmissionBlockEntity) helper.getBlockEntity(box)).panel.setValue(gear);
		helper.setBlock(far, TestEngine.axisX(CEBlocks.ROTOR_HOUSING.getDefaultState())
			.setValue(RotorHousingBlock.HAS_ROTOR, true)
			.setValue(RotorHousingBlock.HAS_INJECTOR, true)
			.setValue(RotorHousingBlock.HAS_SPARK_PLUG, true));
		helper.setBlock(far.north(), CEBlocks.AIR_INTAKE.getDefaultState()
			.setValue(com.createengines.content.airflow.PortOpeningBlock.FACING, Direction.NORTH));
		helper.setBlock(far.above(), CEBlocks.EXHAUST_OUTLET.getDefaultState()
			.setValue(com.createengines.content.airflow.PortOpeningBlock.FACING, Direction.UP));
		RotorHousingBlockEntity second = helper.getBlockEntity(far);
		second.tank()
			.fill(new FluidStack(CEFluids.GASOLINE.get()
				.getSource(), 250), FluidAction.EXECUTE);
		second.oilTank()
			.fill(new FluidStack(CEFluids.ENGINE_OIL.get()
				.getSource(), 250), FluidAction.EXECUTE);
		EveryTick.run(helper, () -> {
			long t = helper.getTick();
			helper.assertTrue(helper.getBlockState(TestEngine.HOUSING).is(CEBlocks.ROTOR_HOUSING.get()),
				"The first housing broke at tick " + t);
			helper.assertTrue(helper.getBlockState(far).is(CEBlocks.ROTOR_HOUSING.get()),
				"The housing past the transmission broke at tick " + t);
			helper.assertTrue(helper.getBlockState(box).is(CEBlocks.TRANSMISSION.get()),
				"The transmission broke at tick " + t);
		});
		helper.runAtTickTime(300, () -> helper.setBlock(STARTER_POWER, Blocks.AIR));
		helper.runAtTickTime(1200, helper::succeed);
	}

	/**
	 * Banjo0300's build as described: alternator and hand crank on one end of the shaft, two housings adjacent, then a
	 * transmission and a mechanical pump on the other end. Starting it must break nothing.
	 */
	@GameTest(template = "empty", timeoutTicks = 1500)
	public static void aTwinRotaryWithAlternatorHandCrankTransmissionAndPumpSurvivesStartingInNeutral(GameTestHelper helper) {
		twinRotaryWithDrivetrain(helper, Gears.NEUTRAL);
	}

	@GameTest(template = "empty", timeoutTicks = 1500)
	public static void aTwinRotaryWithAlternatorHandCrankTransmissionAndPumpSurvivesStartingInFirst(GameTestHelper helper) {
		twinRotaryWithDrivetrain(helper, Gears.FIRST);
	}

	@GameTest(template = "empty", timeoutTicks = 1500)
	public static void aTwinRotaryWithAlternatorHandCrankTransmissionAndPumpSurvivesStartingOnAuto(GameTestHelper helper) {
		twinRotaryWithDrivetrain(helper, Gears.AUTO);
	}

	@GameTest(template = "empty", timeoutTicks = 1500)
	public static void aTwinRotaryStartedByHandCrankWithTransmissionAndPumpSurvivesInFirst(GameTestHelper helper) {
		twinRotaryWithDrivetrain(helper, Gears.FIRST, true);
	}

	@GameTest(template = "empty", timeoutTicks = 1500)
	public static void aTwinRotaryCrankedByHandTheOtherWayWithTransmissionAndPumpSurvives(GameTestHelper helper) {
		twinRotaryWithDrivetrain(helper, Gears.NEUTRAL, true, true);
	}

	@GameTest(template = "empty", timeoutTicks = 1500)
	public static void aTwinRotaryStartedByHandCrankWithTransmissionAndPumpSurvivesInNeutral(GameTestHelper helper) {
		twinRotaryWithDrivetrain(helper, Gears.NEUTRAL, true);
	}

	private static void twinRotaryWithDrivetrain(GameTestHelper helper, int gear) {
		twinRotaryWithDrivetrain(helper, gear, false);
	}

	private static void twinRotaryWithDrivetrain(GameTestHelper helper, int gear, boolean byHand) {
		twinRotaryWithDrivetrain(helper, gear, byHand, false);
	}

	private static void twinRotaryWithDrivetrain(GameTestHelper helper, int gear, boolean byHand, boolean againstIt) {
		TestEngine.buildRotary(helper.getLevel(), helper.absolutePos(BlockPos.ZERO), true);
		TestEngine.addSecondRotor(helper.getLevel(), helper.absolutePos(BlockPos.ZERO));
		BlockPos crank = TestEngine.ALTERNATOR.west();
		BlockPos box = TestEngine.HOUSING_2.east();
		BlockPos pump = box.east();
		helper.setBlock(crank, AllBlocks.HAND_CRANK.getDefaultState()
			.setValue(com.simibubi.create.content.kinetics.base.DirectionalKineticBlock.FACING, Direction.WEST));
		helper.setBlock(box, CEBlocks.TRANSMISSION.getDefaultState()
			.setValue(BlockStateProperties.AXIS, Axis.X));
		((TransmissionBlockEntity) helper.getBlockEntity(box)).panel.setValue(gear);
		helper.setBlock(pump, AllBlocks.MECHANICAL_PUMP.getDefaultState()
			.setValue(com.simibubi.create.content.kinetics.base.DirectionalKineticBlock.FACING, Direction.EAST));
		BlockPos[] watched = {TestEngine.HOUSING, TestEngine.HOUSING_2, TestEngine.ALTERNATOR, TestEngine.PRIMARY, box, pump, crank};
		EveryTick.run(helper, () -> {
			for (BlockPos at : watched)
				helper.assertFalse(helper.getBlockState(at).isAir(), "Block at " + at + " broke at tick " + helper.getTick());
		});
		if (byHand) {
			// No motor and no redstone: the player turns the hand crank, and the starter engages because its shaft is turned by hand
			helper.setBlock(STARTER_POWER, Blocks.AIR);
			((com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity) helper.getBlockEntity(TestEngine.MOTOR))
				.generatedSpeed.setValue(0);
			EveryTick.run(helper, () -> {
				if (helper.getBlockEntity(crank) instanceof com.simibubi.create.content.kinetics.crank.HandCrankBlockEntity hand)
					hand.turn(againstIt);
				helper.assertTrue(!helper.getBlockState(TestEngine.HOUSING).isAir() && !helper.getBlockState(TestEngine.HOUSING_2).isAir(),
					"A housing broke at tick " + helper.getTick());
			});
			helper.runAtTickTime(1000, () -> helper.assertTrue(summary(helper).state() == EngineState.RUNNING,
				"Cranked by hand, the twin rotary should be running: " + summary(helper)));
		} else
			helper.runAtTickTime(300, () -> helper.setBlock(STARTER_POWER, Blocks.AIR));
		helper.runAtTickTime(1200, helper::succeed);
	}

	/**
	 * What Banjo0300's build does when a player cranks it once: no oil and no fuel anywhere, so the dry engine drags more
	 * than a hand crank can turn, the network is overstressed and the speed flickers. Create destroys a block whose
	 * rotation flickers too often, so nothing may break and the flicker score must stay far under its limit of 128.
	 */
	@GameTest(template = "empty", timeoutTicks = 3200)
	public static void aDryTwinRotaryCrankedOnceByHandDoesNotBreak(GameTestHelper helper) {
		dryTwinRotaryByHand(helper, 1000);
	}

	@GameTest(template = "empty", timeoutTicks = 3200)
	public static void aDryTwinRotaryCrankedNowAndThenByHandDoesNotBreak(GameTestHelper helper) {
		dryTwinRotaryByHand(helper, 25);
	}

	@GameTest(template = "empty", timeoutTicks = 3200)
	public static void aDryTwinRotaryCrankedOftenByHandDoesNotBreak(GameTestHelper helper) {
		dryTwinRotaryByHand(helper, 14);
	}

	@GameTest(template = "empty", timeoutTicks = 3200)
	public static void aDryTwinRotaryCrankedNonStopByHandDoesNotBreak(GameTestHelper helper) {
		dryTwinRotaryByHand(helper, 1);
	}

	private static void dryTwinRotaryByHand(GameTestHelper helper, int every) {
		TestEngine.buildRotary(helper.getLevel(), helper.absolutePos(BlockPos.ZERO), true);
		TestEngine.addSecondRotor(helper.getLevel(), helper.absolutePos(BlockPos.ZERO));
		BlockPos crank = TestEngine.ALTERNATOR.west();
		BlockPos box = TestEngine.HOUSING_2.east();
		BlockPos pump = box.east();
		helper.setBlock(crank, AllBlocks.HAND_CRANK.getDefaultState()
			.setValue(com.simibubi.create.content.kinetics.base.DirectionalKineticBlock.FACING, Direction.WEST));
		helper.setBlock(box, CEBlocks.TRANSMISSION.getDefaultState()
			.setValue(BlockStateProperties.AXIS, Axis.X));
		helper.setBlock(pump, AllBlocks.MECHANICAL_PUMP.getDefaultState()
			.setValue(com.simibubi.create.content.kinetics.base.DirectionalKineticBlock.FACING, Direction.EAST));
		helper.setBlock(STARTER_POWER, Blocks.AIR);
		((CreativeMotorBlockEntity) helper.getBlockEntity(TestEngine.MOTOR)).generatedSpeed.setValue(0);
		for (BlockPos housing : new BlockPos[] {TestEngine.HOUSING, TestEngine.HOUSING_2}) {
			RotorHousingBlockEntity be = helper.getBlockEntity(housing);
			be.tank().drain(1000, FluidAction.EXECUTE);
			be.oilTank().drain(1000, FluidAction.EXECUTE);
		}
		int[] worst = {0};
		EveryTick.run(helper, () -> {
			long t = helper.getTick();
			if (t >= 10 && (every == 1 || (t - 10) % every < 12) && helper.getBlockEntity(crank) instanceof com.simibubi.create.content.kinetics.crank.HandCrankBlockEntity hand)
				hand.turn(false);

			for (BlockPos at : new BlockPos[] {TestEngine.HOUSING, TestEngine.HOUSING_2, TestEngine.PRIMARY, TestEngine.ALTERNATOR, box, pump, crank}) {
				helper.assertFalse(helper.getBlockState(at).isAir(), "Block at " + at + " broke at tick " + t);
				helper.assertTrue(helper.getLevel().getBlockEntity(helper.absolutePos(at)) != null,
					"Block at " + at + " (" + helper.getBlockState(at) + ") lost its block entity at tick " + t);
				if (helper.getBlockEntity(at) instanceof com.simibubi.create.content.kinetics.base.KineticBlockEntity k)
					worst[0] = Math.max(worst[0], k.getFlickerScore());
			}
		});
		helper.runAtTickTime(3000, () -> {
			helper.assertTrue(worst[0] < 64, "The rotation flickered to " + worst[0] + " of Create's limit of 128");
			helper.succeed();
		});
	}

	/**
	 * Banjo0300's break as reproduced by hand: both housings filled to the brim with gasoline (and oiled) before the engine
	 * runs, then started. Neither may break on the way to running.
	 */
	@GameTest(template = "empty", timeoutTicks = 1500)
	public static void aTwinRotaryWithBothTanksBrimmingStartedByMotorSurvives(GameTestHelper helper) {
		brimmingTwinRotary(helper, false);
	}

	@GameTest(template = "empty", timeoutTicks = 1500)
	public static void aTwinRotaryWithBothTanksBrimmingStartedByHandSurvives(GameTestHelper helper) {
		brimmingTwinRotary(helper, true);
	}

	/** One housing brimming with gasoline and no oil in either, as in Banjo0300's dry engine once it was flooded and started. */
	@GameTest(template = "empty", timeoutTicks = 1500)
	public static void aDryTwinRotaryWithOneHousingFloodedWithGasolineStartedByMotorSurvives(GameTestHelper helper) {
		brimmingTwinRotary(helper, false, true);
	}

	@GameTest(template = "empty", timeoutTicks = 1500)
	public static void aDryTwinRotaryWithOneHousingFloodedWithGasolineStartedByHandSurvives(GameTestHelper helper) {
		brimmingTwinRotary(helper, true, true);
	}

	/**
	 * The line in the world where this was reproduced: hand crank, alternator, two housings, a neutral transmission, a
	 * shaft and a second hand crank, with only the housing beside the transmission full of gasoline and no oil anywhere.
	 * Cranked from either end; nothing may break.
	 */
	@GameTest(template = "empty", timeoutTicks = 1500)
	public static void aFloodedRotaryLineWithAHandCrankAtEachEndSurvivesCrankingFromTheAlternatorEnd(GameTestHelper helper) {
		crankAtEachEnd(helper, false);
	}

	@GameTest(template = "empty", timeoutTicks = 1500)
	public static void aFloodedRotaryLineWithAHandCrankAtEachEndSurvivesCrankingFromTheTransmissionEnd(GameTestHelper helper) {
		crankAtEachEnd(helper, true);
	}

	/** The same line, in the order it was done: the far crank turned first to fill the head, then the near crank to start. */
	@GameTest(template = "empty", timeoutTicks = 2000)
	public static void aRotaryLineCrankedAtTheFarEndThenTheNearEndSurvives(GameTestHelper helper) {
		crankAtEachEnd(helper, false, true);
	}

	/**
	 * Banjo0300's and the author's build block for block, read from the saved world where it was reproduced: hand crank,
	 * small cog, neutral transmission, two rotor housings, alternator, hand crank along one row; a fluid tank, pump and
	 * pipes below feeding the housing beside the transmission; intake manifolds above and exhaust manifolds beside; a
	 * battery by the second housing. The far crank is turned to pump gasoline in, then the near one to start it.
	 */
	@GameTest(template = "empty", timeoutTicks = 2400)
	public static void theWorldsRotaryLineWithPumpAndPipesSurvivesFillingThenStarting(GameTestHelper helper) {
		int y = 2, z = 2;
		BlockPos farCrank = new BlockPos(1, y, z), cog = new BlockPos(2, y, z), box = new BlockPos(3, y, z);
		BlockPos h1 = new BlockPos(4, y, z), h2 = new BlockPos(5, y, z), alt = new BlockPos(6, y, z), nearCrank = new BlockPos(7, y, z);
		BlockPos tank = new BlockPos(1, y - 1, z), pump = new BlockPos(2, y - 1, z);
		BlockPos pipe1 = new BlockPos(3, y - 1, z), pipe2 = new BlockPos(4, y - 1, z);
		BlockState housing = TestEngine.axisX(CEBlocks.ROTOR_HOUSING.getDefaultState())
			.setValue(RotorHousingBlock.HAS_ROTOR, true)
			.setValue(RotorHousingBlock.HAS_INJECTOR, true)
			.setValue(RotorHousingBlock.FINE_INJECTOR, true)
			.setValue(RotorHousingBlock.HAS_SPARK_PLUG, true);
		helper.setBlock(farCrank, AllBlocks.HAND_CRANK.getDefaultState()
			.setValue(com.simibubi.create.content.kinetics.base.DirectionalKineticBlock.FACING, Direction.WEST));
		helper.setBlock(cog, TestEngine.axisX(AllBlocks.COGWHEEL.getDefaultState()));
		helper.setBlock(box, CEBlocks.TRANSMISSION.getDefaultState().setValue(BlockStateProperties.AXIS, Axis.X));
		helper.setBlock(h1, housing);
		helper.setBlock(h2, housing);
		helper.setBlock(alt, TestEngine.axisX(CEBlocks.ALTERNATOR.getDefaultState()));
		helper.setBlock(nearCrank, AllBlocks.HAND_CRANK.getDefaultState()
			.setValue(com.simibubi.create.content.kinetics.base.DirectionalKineticBlock.FACING, Direction.EAST));
		helper.setBlock(tank, AllBlocks.FLUID_TANK.getDefaultState());
		helper.setBlock(pump, AllBlocks.MECHANICAL_PUMP.getDefaultState()
			.setValue(com.simibubi.create.content.kinetics.base.DirectionalKineticBlock.FACING, Direction.EAST));
		helper.setBlock(pipe1, AllBlocks.FLUID_PIPE.getDefaultState());
		helper.setBlock(pipe2, AllBlocks.FLUID_PIPE.getDefaultState());
		// Air in along the top, out along the side, battery beside the second housing
		helper.setBlock(new BlockPos(3, y + 1, z), CEBlocks.AIR_INTAKE.getDefaultState().setValue(PortOpeningBlock.FACING, Direction.WEST));
		for (BlockPos at : new BlockPos[] {new BlockPos(4, y + 1, z), new BlockPos(5, y + 1, z)})
			helper.setBlock(at, placePipe(helper, CEBlocks.INTAKE_MANIFOLD.get(), at));
		for (BlockPos at : new BlockPos[] {new BlockPos(4, y, z + 1), new BlockPos(5, y, z + 1)})
			helper.setBlock(at, placePipe(helper, CEBlocks.EXHAUST_MANIFOLD.get(), at));
		helper.setBlock(new BlockPos(6, y, z + 1), CEBlocks.EXHAUST_OUTLET.getDefaultState().setValue(PortOpeningBlock.FACING, Direction.EAST));
		helper.setBlock(new BlockPos(5, y, z - 1), CEBlocks.BATTERY.getDefaultState());
		((com.simibubi.create.content.fluids.tank.FluidTankBlockEntity) helper.getBlockEntity(tank)).getTankInventory()
			.fill(new FluidStack(CEFluids.GASOLINE.get().getSource(), 4000), FluidAction.EXECUTE);
		int[] worst = {0};
		BlockPos[] watched = {farCrank, cog, box, h1, h2, alt, nearCrank, pump, pipe1, pipe2};
		EveryTick.run(helper, () -> {
			long t = helper.getTick();
			BlockPos now = t < 400 ? farCrank : nearCrank;
			if (t >= 10 && (t < 350 || (t >= 450 && t < 1000)) && helper.getBlockEntity(now) instanceof com.simibubi.create.content.kinetics.crank.HandCrankBlockEntity hand)
				hand.turn(false);
			for (BlockPos at : watched) {
				helper.assertFalse(helper.getBlockState(at).isAir(), "Block at " + at + " broke at tick " + t);
				if (helper.getLevel().getBlockEntity(helper.absolutePos(at)) instanceof com.simibubi.create.content.kinetics.base.KineticBlockEntity k)
					worst[0] = Math.max(worst[0], k.getFlickerScore());
			}
		});
		helper.runAtTickTime(2000, () -> {
			helper.succeed();
		});
	}

	private static void crankAtEachEnd(GameTestHelper helper, boolean farEnd) {
		crankAtEachEnd(helper, farEnd, false);
	}

	private static void crankAtEachEnd(GameTestHelper helper, boolean farEnd, boolean farThenNear) {
		TestEngine.buildRotary(helper.getLevel(), helper.absolutePos(BlockPos.ZERO), true);
		TestEngine.addSecondRotor(helper.getLevel(), helper.absolutePos(BlockPos.ZERO));
		BlockPos nearCrank = TestEngine.ALTERNATOR.west();
		BlockPos box = TestEngine.HOUSING_2.east();
		BlockPos shaft = box.east();
		BlockPos farCrank = shaft.east();
		helper.setBlock(nearCrank, AllBlocks.HAND_CRANK.getDefaultState()
			.setValue(com.simibubi.create.content.kinetics.base.DirectionalKineticBlock.FACING, Direction.WEST));
		helper.setBlock(box, CEBlocks.TRANSMISSION.getDefaultState()
			.setValue(BlockStateProperties.AXIS, Axis.X));
		helper.setBlock(shaft, TestEngine.axisX(AllBlocks.SHAFT.getDefaultState()));
		helper.setBlock(farCrank, AllBlocks.HAND_CRANK.getDefaultState()
			.setValue(com.simibubi.create.content.kinetics.base.DirectionalKineticBlock.FACING, Direction.EAST));
		helper.setBlock(STARTER_POWER, Blocks.AIR);
		((CreativeMotorBlockEntity) helper.getBlockEntity(TestEngine.MOTOR)).generatedSpeed.setValue(0);
		for (BlockPos housing : new BlockPos[] {TestEngine.HOUSING, TestEngine.HOUSING_2}) {
			RotorHousingBlockEntity be = helper.getBlockEntity(housing);
			be.tank().drain(100000, FluidAction.EXECUTE);
			be.oilTank().drain(100000, FluidAction.EXECUTE);
			if (housing.equals(TestEngine.HOUSING_2) && !farThenNear)
				be.tank().fill(new FluidStack(CEFluids.GASOLINE.get().getSource(), be.tank().getCapacity()), FluidAction.EXECUTE);
		}
		if (farThenNear)
			helper.runAtTickTime(250, () -> {
				RotorHousingBlockEntity be = helper.getBlockEntity(TestEngine.HOUSING_2);
				be.tank().fill(new FluidStack(CEFluids.GASOLINE.get().getSource(), be.tank().getCapacity()), FluidAction.EXECUTE);
			});
		BlockPos turned = farEnd ? farCrank : nearCrank;
		int[] worst = {0};
		EveryTick.run(helper, () -> {
			long t = helper.getTick();
			BlockPos now = farThenNear ? (t < 300 ? farCrank : nearCrank) : turned;
			int from = farThenNear && t >= 300 ? 300 : 10;
			if (t >= from && t < (farThenNear ? (t < 300 ? 250 : 900) : 600) && helper.getBlockEntity(now) instanceof com.simibubi.create.content.kinetics.crank.HandCrankBlockEntity hand)
				hand.turn(false);
			for (BlockPos at : new BlockPos[] {TestEngine.HOUSING, TestEngine.HOUSING_2, TestEngine.PRIMARY, TestEngine.ALTERNATOR, box, shaft, nearCrank, farCrank}) {
				helper.assertFalse(helper.getBlockState(at).isAir(), "Block at " + at + " broke at tick " + t);
				if (helper.getLevel().getBlockEntity(helper.absolutePos(at)) instanceof com.simibubi.create.content.kinetics.base.KineticBlockEntity k)
					worst[0] = Math.max(worst[0], k.getFlickerScore());
			}
		});
		helper.runAtTickTime(1500, () -> {
			helper.succeed();
		});
	}

	private static void brimmingTwinRotary(GameTestHelper helper, boolean byHand) {
		brimmingTwinRotary(helper, byHand, false);
	}

	private static void brimmingTwinRotary(GameTestHelper helper, boolean byHand, boolean oneDry) {
		TestEngine.buildRotary(helper.getLevel(), helper.absolutePos(BlockPos.ZERO), true);
		TestEngine.addSecondRotor(helper.getLevel(), helper.absolutePos(BlockPos.ZERO));
		BlockPos crank = TestEngine.ALTERNATOR.west();
		BlockPos box = TestEngine.HOUSING_2.east();
		BlockPos pump = box.east();
		helper.setBlock(crank, AllBlocks.HAND_CRANK.getDefaultState()
			.setValue(com.simibubi.create.content.kinetics.base.DirectionalKineticBlock.FACING, Direction.WEST));
		helper.setBlock(box, CEBlocks.TRANSMISSION.getDefaultState()
			.setValue(BlockStateProperties.AXIS, Axis.X));
		helper.setBlock(pump, AllBlocks.MECHANICAL_PUMP.getDefaultState()
			.setValue(com.simibubi.create.content.kinetics.base.DirectionalKineticBlock.FACING, Direction.EAST));
		for (BlockPos housing : new BlockPos[] {TestEngine.HOUSING, TestEngine.HOUSING_2}) {
			RotorHousingBlockEntity be = helper.getBlockEntity(housing);
			if (oneDry) {
				be.tank().drain(100000, FluidAction.EXECUTE);
				be.oilTank().drain(100000, FluidAction.EXECUTE);
				if (housing.equals(TestEngine.HOUSING))
					be.tank().fill(new FluidStack(CEFluids.GASOLINE.get().getSource(), be.tank().getCapacity()), FluidAction.EXECUTE);
				continue;
			}
			be.tank().drain(100000, FluidAction.EXECUTE);
			be.tank().fill(new FluidStack(CEFluids.GASOLINE.get().getSource(), be.tank().getCapacity()), FluidAction.EXECUTE);
			be.oilTank().drain(100000, FluidAction.EXECUTE);
			be.oilTank().fill(new FluidStack(CEFluids.ENGINE_OIL.get().getSource(), be.oilTank().getCapacity()), FluidAction.EXECUTE);
		}
		if (byHand) {
			helper.setBlock(STARTER_POWER, Blocks.AIR);
			((CreativeMotorBlockEntity) helper.getBlockEntity(TestEngine.MOTOR)).generatedSpeed.setValue(0);
		} else
			helper.runAtTickTime(300, () -> helper.setBlock(STARTER_POWER, Blocks.AIR));
		int[] worst = {0};
		EveryTick.run(helper, () -> {
			long t = helper.getTick();
			if (byHand && t < 600 && helper.getBlockEntity(crank) instanceof com.simibubi.create.content.kinetics.crank.HandCrankBlockEntity hand)
				hand.turn(false);
			for (BlockPos at : new BlockPos[] {TestEngine.HOUSING, TestEngine.HOUSING_2, TestEngine.PRIMARY, TestEngine.ALTERNATOR, box, pump, crank}) {
				helper.assertFalse(helper.getBlockState(at).isAir(), "Block at " + at + " broke at tick " + t);
				if (helper.getLevel().getBlockEntity(helper.absolutePos(at)) instanceof com.simibubi.create.content.kinetics.base.KineticBlockEntity k)
					worst[0] = Math.max(worst[0], k.getFlickerScore());
			}
		});
		helper.runAtTickTime(1200, () -> {
			helper.succeed();
		});
	}

	private static void rotorBesideRotor(GameTestHelper helper, BlockPos at, net.minecraft.core.Direction.Axis axis) {
		TestEngine.buildRotary(helper.getLevel(), helper.absolutePos(BlockPos.ZERO), true);
		helper.setBlock(at, CEBlocks.ROTOR_HOUSING.getDefaultState()
			.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.AXIS, axis)
			.setValue(RotorHousingBlock.HAS_ROTOR, true)
			.setValue(RotorHousingBlock.HAS_INJECTOR, true)
			.setValue(RotorHousingBlock.HAS_SPARK_PLUG, true));
		EveryTick.run(helper, () -> {
			helper.assertTrue(helper.getBlockState(TestEngine.HOUSING).is(CEBlocks.ROTOR_HOUSING.get()),
				"The first rotor housing broke: " + helper.getBlockState(TestEngine.HOUSING));
			helper.assertTrue(helper.getBlockState(at).is(CEBlocks.ROTOR_HOUSING.get()),
				"The neighbouring rotor housing broke: " + helper.getBlockState(at));
		});
		helper.runAtTickTime(300, () -> helper.setBlock(STARTER_POWER, Blocks.AIR));
		helper.runAtTickTime(800, helper::succeed);
	}

	/** A rotor cannot light a charge by compression alone: with no plug it only turns over, and says why. */
	@GameTest(template = "empty", timeoutTicks = 500)
	public static void aRotorWithNoSparkPlugDoesNotLight(GameTestHelper helper) {
		TestEngine.buildRotary(helper.getLevel(), helper.absolutePos(BlockPos.ZERO), false);
		RotorHousingBlockEntity housing = helper.getBlockEntity(TestEngine.HOUSING);
		housing.tank()
			.drain(250, FluidAction.EXECUTE);
		housing.tank()
			.fill(new FluidStack(CEFluids.DIESEL.get()
				.getSource(), 250), FluidAction.EXECUTE);
		helper.runAtTickTime(400, () -> {
			helper.assertTrue(summary(helper).firing() == 0, "Diesel lit in a rotor with no plug: " + summary(helper));
			CylinderStatus chamber = housing.chamber(0);
			helper.assertTrue(chamber != null && CylinderFault.NO_PLUG.in(chamber.faults()),
				"The housing should say it has no plug: " + chamber);
			helper.succeed();
		});
	}

	/** A housing with no rotor in it is one disabled entry that drags nothing, and the engine reports no cylinders. */
	@GameTest(template = "empty", timeoutTicks = 100)
	public static void aRotorHousingWithNoRotorWorksNothing(GameTestHelper helper) {
		TestEngine.buildRotary(helper.getLevel(), helper.absolutePos(BlockPos.ZERO), true);
		helper.setBlock(TestEngine.HOUSING, helper.getBlockState(TestEngine.HOUSING)
			.setValue(RotorHousingBlock.HAS_ROTOR, false));
		helper.runAtTickTime(30, () -> {
			EngineLayout layout = EngineScanner.scan(helper.getLevel(), helper.absolutePos(PRIMARY));
			helper.assertTrue(layout != null && layout.cylinders()
				.size() == 1 && CylinderFault.NO_ROTOR.in(layout.cylinders()
					.getFirst()
					.faults()), "One disabled entry with no rotor: " + (layout == null ? null : layout.cylinders()));
			helper.assertTrue(summary(helper).cylinders() == 0, "Nothing can work: " + summary(helper));
			helper.succeed();
		});
	}

	/** The Timing Tool sets two rotors half a turn apart, whichever way they started, and leaves a cams-only plan alone. */
	@GameTest(template = "empty", timeoutTicks = 100)
	public static void theTimingToolSpacesTwoRotorsEvenly(GameTestHelper helper) {
		TestEngine.buildRotary(helper.getLevel(), helper.absolutePos(BlockPos.ZERO), true);
		TestEngine.addSecondRotor(helper.getLevel(), helper.absolutePos(BlockPos.ZERO));
		RotorHousingBlockEntity first = helper.getBlockEntity(TestEngine.HOUSING);
		RotorHousingBlockEntity second = helper.getBlockEntity(TestEngine.HOUSING_2);
		first.throwAngle.setValue(2);
		second.throwAngle.setValue(3);
		helper.runAtTickTime(30, () -> {
			TimingToolItem.Result kept = TimingToolItem.apply(helper.getLevel(), helper.absolutePos(PRIMARY),
				FiringPlanner.Style.CAMS_ONLY);
			helper.assertTrue(kept != null && kept.rotors() == 2 && !kept.rotorsEven(), "Cams only leaves rotors: " + kept);
			helper.assertTrue(first.throwAngle.getValue() == 2 && second.throwAngle.getValue() == 3,
				"Cams only should not move an eccentric");
			TimingToolItem.Result result = TimingToolItem.apply(helper.getLevel(), helper.absolutePos(PRIMARY),
				FiringPlanner.Style.SMOOTH);
			helper.assertTrue(result != null && result.plan() == null && result.rotors() == 2 && result.rotorsEven(),
				"Two rotors can always be spaced evenly: " + result);
			int apart = Math.floorMod(first.throwAngle.getValue() - second.throwAngle.getValue(), 24);
			helper.assertTrue(apart == 12, "Eccentrics should be half a turn apart, was " + apart + " steps");
			helper.succeed();
		});
	}

	/** The eccentric angle and the fuel metering are both scroll values: saving must keep them apart. */
	@GameTest(template = "empty", timeoutTicks = 20)
	public static void aRotorHousingSavesItsAngleAndItsMeteringSeparately(GameTestHelper helper) {
		TestEngine.buildRotary(helper.getLevel(), helper.absolutePos(BlockPos.ZERO), true);
		RotorHousingBlockEntity housing = helper.getBlockEntity(TestEngine.HOUSING);
		housing.throwAngle.setValue(5);
		housing.metering.setValue(7);
		net.minecraft.nbt.CompoundTag saved = housing.saveWithoutMetadata(helper.getLevel()
			.registryAccess());
		housing.throwAngle.setValue(1);
		housing.metering.setValue(1);
		housing.handleUpdateTag(saved, helper.getLevel()
			.registryAccess());
		helper.assertTrue(housing.throwAngle.getValue() == 5, "Eccentric angle: " + housing.throwAngle.getValue());
		helper.assertTrue(housing.metering.getValue() == 7, "Metering: " + housing.metering.getValue());
		helper.succeed();
	}
}
