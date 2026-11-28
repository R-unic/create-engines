package com.createengines.gametest;

import static com.createengines.gametest.EngineRigs.*;

import com.createengines.CreateEngines;
import com.createengines.content.airflow.PortOpeningBlock;
import com.createengines.content.transmission.Gears;
import com.createengines.content.transmission.TransmissionBlockEntity;
import com.createengines.engine.EngineState;
import com.createengines.engine.EngineSummary;
import com.createengines.registry.CEBlocks;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlock;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import com.simibubi.create.content.kinetics.speedController.SpeedControllerBlockEntity;
import com.simibubi.create.infrastructure.config.AllConfigs;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Shifting a transmission to neutral while engines stall, with sources and Speed Controllers about. Test engines as in {@link EngineGameTests}. */
@GameTestHolder(CreateEngines.MOD_ID)
@PrefixGameTestTemplate(false)
public class NeutralTransmissionGameTests {

	/**
	 * An engine on auto, carrying the presses, loses its exhaust and starts to stop; the panel is moved to neutral {@code
	 * after} ticks later, from the moment it chokes to long after it has stopped. Create breaks a split shaft that is
	 * re-attached to a network that disagrees with it, so the box has to survive the shift whenever it comes, and the engine
	 * has to start again with nothing on it.
	 */
	private static void checkNeutralWhileStalling(GameTestHelper helper, int presses, int after) {
		buildSingleCylinder(helper, true);
		helper.setBlock(PRESS, CEBlocks.TRANSMISSION.getDefaultState()
			.setValue(BlockStateProperties.AXIS, Axis.X));
		TransmissionBlockEntity box = helper.getBlockEntity(PRESS);
		box.panel.setValue(Gears.AUTO);
		helper.runAtTickTime(300, () -> helper.setBlock(STARTER_POWER, Blocks.AIR));
		helper.runAtTickTime(500, () -> placePresses(helper, presses));
		int choke = 2500;
		helper.runAtTickTime(choke, () -> helper.setBlock(EXHAUST, Blocks.AIR));
		helper.runAtTickTime(choke + after, () -> {
			helper.assertTrue(helper.getBlockState(PRESS).is(CEBlocks.TRANSMISSION.get()), "The transmission was destroyed before the shift");
			TransmissionBlockEntity be = helper.getBlockEntity(PRESS);
			be.panel.setValue(Gears.NEUTRAL);
		});
		int settled = choke + after + 1200;
		helper.runAtTickTime(settled, () -> {
			helper.assertTrue(helper.getBlockState(PRESS).is(CEBlocks.TRANSMISSION.get()),
				"The transmission was destroyed by the shift to neutral, " + after + " ticks into the stall");
			helper.assertTrue(summary(helper).state() == EngineState.STALLED, "It should have stopped: " + summary(helper));
			helper.setBlock(EXHAUST, CEBlocks.EXHAUST_OUTLET.getDefaultState()
				.setValue(PortOpeningBlock.FACING, Direction.WEST));
			helper.setBlock(STARTER_POWER, Blocks.REDSTONE_BLOCK);
		});
		helper.runAtTickTime(settled + 300, () -> helper.setBlock(STARTER_POWER, Blocks.AIR));
		helper.runAtTickTime(settled + 900, () -> {
			helper.assertTrue(helper.getBlockState(PRESS).is(CEBlocks.TRANSMISSION.get()), "The transmission was destroyed by the restart");
			TransmissionBlockEntity be = helper.getBlockEntity(PRESS);
			EngineSummary summary = summary(helper);
			helper.assertTrue(summary.state() == EngineState.RUNNING, "With the load off, the starter should get it going: " + summary);
			helper.assertTrue(be.gear() == Gears.NEUTRAL, "It should still be in neutral: " + be.gear());
			helper.succeed();
		});
	}

	@GameTest(template = "empty_large", timeoutTicks = 7000)
	public static void neutralAsTheExhaustChokesKeepsTheTransmission(GameTestHelper helper) {
		checkNeutralWhileStalling(helper, 3, 0);
	}

	@GameTest(template = "empty_large", timeoutTicks = 7000)
	public static void neutralJustAfterTheExhaustChokesKeepsTheTransmission(GameTestHelper helper) {
		checkNeutralWhileStalling(helper, 3, 5);
	}

	@GameTest(template = "empty_large", timeoutTicks = 7000)
	public static void neutralWhileACloggedEngineSlowsKeepsTheTransmission(GameTestHelper helper) {
		checkNeutralWhileStalling(helper, 3, 40);
	}

	@GameTest(template = "empty_large", timeoutTicks = 7000)
	public static void neutralWhileACloggedEngineIsAlmostStoppedKeepsTheTransmission(GameTestHelper helper) {
		checkNeutralWhileStalling(helper, 3, 150);
	}

	@GameTest(template = "empty_large", timeoutTicks = 7000)
	public static void neutralOnAStoppedEngineKeepsTheTransmission(GameTestHelper helper) {
		checkNeutralWhileStalling(helper, 3, 700);
	}

	@GameTest(template = "empty_large", timeoutTicks = 7000)
	public static void neutralWhileAnOverloadedEngineStallsKeepsTheTransmission(GameTestHelper helper) {
		checkNeutralWhileStalling(helper, scaledPresses(8), 20);
	}

	/**
	 * The transmission feeds a rotation speed controller (a large cogwheel on top of it carries the regulated speed) and the
	 * presses hang off that. Names every block of the run that is gone, so a break says which one Create destroyed.
	 */
	private static String missingFromControllerRun(GameTestHelper helper, int presses) {
		StringBuilder gone = new StringBuilder();
		if (!helper.getBlockState(PRESS).is(CEBlocks.TRANSMISSION.get()))
			gone.append(" transmission");
		if (!helper.getBlockState(PRESS.east()).is(AllBlocks.SHAFT.get()))
			gone.append(" input-shaft");
		if (!helper.getBlockState(PRESS.east(2)).is(AllBlocks.ROTATION_SPEED_CONTROLLER.get()))
			gone.append(" controller");
		if (!helper.getBlockState(PRESS.east(2).above()).is(AllBlocks.LARGE_COGWHEEL.get()))
			gone.append(" large-cog");
		if (!helper.getBlockState(PRESS.east(2).above().south()).is(AllBlocks.SHAFT.get()))
			gone.append(" output-shaft");
		for (int i = 0; i < presses; i++)
			if (!helper.getBlockState(PRESS.east(2).above().south(2 + i)).is(AllBlocks.MECHANICAL_PRESS.get()))
				gone.append(" press").append(i);
		return gone.toString();
	}

	private static void placeControllerRun(GameTestHelper helper, int target, int presses) {
		helper.setBlock(PRESS.east(), AllBlocks.SHAFT.getDefaultState()
			.setValue(BlockStateProperties.AXIS, Axis.X));
		helper.setBlock(PRESS.east(2), AllBlocks.ROTATION_SPEED_CONTROLLER.getDefaultState()
			.setValue(BlockStateProperties.HORIZONTAL_AXIS, Axis.X));
		// The cog sits across the controller's shaft; the regulated run leaves it sideways
		helper.setBlock(PRESS.east(2).above(), AllBlocks.LARGE_COGWHEEL.getDefaultState()
			.setValue(BlockStateProperties.AXIS, Axis.Z));
		helper.setBlock(PRESS.east(2).above().south(), AllBlocks.SHAFT.getDefaultState()
			.setValue(BlockStateProperties.AXIS, Axis.Z));
		for (int i = 0; i < presses; i++)
			helper.setBlock(PRESS.east(2).above().south(2 + i), AllBlocks.MECHANICAL_PRESS.getDefaultState()
				.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH));
		SpeedControllerBlockEntity controller = helper.getBlockEntity(PRESS.east(2));
		controller.targetSpeed.setValue(target);
	}

	private static void checkNeutralBehindAController(GameTestHelper helper, int target, int maxRpm, int presses) {
		checkNeutralBehindAController(helper, target, maxRpm, presses, -1);
	}

	/** {@code stallAfter} of 0 or more chokes the exhaust that many ticks before the shift, so the engine is stopping or stopped. */
	private static void checkNeutralBehindAController(GameTestHelper helper, int target, int maxRpm, int presses, int stallAfter) {
		int original = AllConfigs.server().kinetics.maxRotationSpeed.get();
		AllConfigs.server().kinetics.maxRotationSpeed.set(maxRpm);
		buildSingleCylinder(helper, true);
		helper.setBlock(PRESS, CEBlocks.TRANSMISSION.getDefaultState()
			.setValue(BlockStateProperties.AXIS, Axis.X));
		TransmissionBlockEntity box = helper.getBlockEntity(PRESS);
		box.panel.setValue(Gears.AUTO);
		helper.runAtTickTime(300, () -> helper.setBlock(STARTER_POWER, Blocks.AIR));
		helper.runAtTickTime(500, () -> placeControllerRun(helper, target, presses));
		helper.runAtTickTime(2500, () -> {
			String gone = missingFromControllerRun(helper, presses);
			helper.assertTrue(gone.isEmpty(), "Before the shift, gone:" + gone);
			TransmissionBlockEntity be = helper.getBlockEntity(PRESS);
			helper.assertTrue(be.gear() != Gears.NEUTRAL, "Auto should be in a gear: " + be.gear());
			KineticBlockEntity press = helper.getBlockEntity(PRESS.east(2).above().south(2));
			if (presses <= 1 && target <= 256)
				helper.assertTrue(Math.abs(press.getSpeed()) == target,
					"The controller should be holding the presses at " + target + ": " + press.getSpeed() + ", gear " + be.gear());
			if (stallAfter < 0)
				be.panel.setValue(Gears.NEUTRAL);
			else
				helper.setBlock(EXHAUST, Blocks.AIR);
		});
		if (stallAfter >= 0)
			helper.runAtTickTime(2500 + stallAfter, () -> {
				helper.assertTrue(missingFromControllerRun(helper, presses).isEmpty(), "Destroyed before the shift:" + missingFromControllerRun(helper, presses));
				TransmissionBlockEntity be = helper.getBlockEntity(PRESS);
				be.panel.setValue(Gears.NEUTRAL);
			});
		for (int tick = 2501; tick <= 2560 + stallAfter; tick += 3)
			helper.runAtTickTime(tick, () -> {
				String gone = missingFromControllerRun(helper, presses);
				if (!gone.isEmpty())
					AllConfigs.server().kinetics.maxRotationSpeed.set(original);
				helper.assertTrue(gone.isEmpty(), "After the shift to neutral, destroyed:" + gone);
			});
		helper.runAtTickTime(2700 + Math.max(stallAfter, 0), () -> {
			AllConfigs.server().kinetics.maxRotationSpeed.set(original);
			if (stallAfter >= 0) {
				helper.assertTrue(summary(helper).state() == EngineState.STALLED || summary(helper).state() == EngineState.RUNNING,
					"Unexpected state " + summary(helper));
				helper.assertTrue(missingFromControllerRun(helper, presses).isEmpty(), "Destroyed after the shift:" + missingFromControllerRun(helper, presses));
				helper.succeed();
				return;
			}
			EngineSummary summary = summary(helper);
			helper.assertTrue(summary.state() == EngineState.RUNNING, "The engine should be running with the load off: " + summary);
			helper.succeed();
		});
	}

	/** Moved from auto to neutral with a speed controller behind it, a transmission and everything after it stay standing. */
	@GameTest(template = "empty_large", timeoutTicks = 6000, batch = "speedController")
	public static void aTransmissionBehindASpeedControllerSurvivesAutoToNeutral(GameTestHelper helper) {
		checkNeutralBehindAController(helper, 200, 256, 1);
	}

	/**
	 * A controller holds its load at one speed whatever gear the box is in, so no gear can relieve it: here the presses
	 * overload the engine however the box shifts. Neutral still has to leave the box standing.
	 */
	@GameTest(template = "empty_large", timeoutTicks = 6000, batch = "speedController")
	public static void aTransmissionBehindAnOverloadingSpeedControllerSurvivesAutoToNeutral(GameTestHelper helper) {
		checkNeutralBehindAController(helper, 512, 512, 12);
	}

	@GameTest(template = "empty_large", timeoutTicks = 6000, batch = "speedController")
	public static void neutralBehindAControllerAsTheEngineChokesKeepsTheTransmission(GameTestHelper helper) {
		checkNeutralBehindAController(helper, 200, 256, 1, 2);
	}

	@GameTest(template = "empty_large", timeoutTicks = 6000, batch = "speedController")
	public static void neutralBehindAControllerWhileTheEngineSlowsKeepsTheTransmission(GameTestHelper helper) {
		checkNeutralBehindAController(helper, 200, 256, 1, 40);
	}

	@GameTest(template = "empty_large", timeoutTicks = 6000, batch = "speedController")
	public static void neutralBehindAControllerOnAStoppedEngineKeepsTheTransmission(GameTestHelper helper) {
		checkNeutralBehindAController(helper, 200, 256, 1, 700);
	}

	@GameTest(template = "empty_large", timeoutTicks = 6000, batch = "speedController")
	public static void neutralBehindAFastControllerWhileTheEngineSlowsKeepsTheTransmission(GameTestHelper helper) {
		checkNeutralBehindAController(helper, 512, 512, 3, 40);
	}

	private static String missingFromWheelRun(GameTestHelper helper) {
		StringBuilder gone = new StringBuilder();
		if (!helper.getBlockState(PRESS).is(CEBlocks.TRANSMISSION.get()))
			gone.append(" transmission");
		if (!helper.getBlockState(PRESS.east()).is(AllBlocks.SHAFT.get()))
			gone.append(" shaft1");
		if (!helper.getBlockState(PRESS.east(2)).is(AllBlocks.SHAFT.get()))
			gone.append(" shaft2");
		if (!helper.getBlockState(PRESS.east(3)).is(AllBlocks.CREATIVE_MOTOR.get()))
			gone.append(" motor");
		return gone.toString();
	}

	/**
	 * Something else drives the transmission's output side, as water wheels do: a motor (standing in for the wheels, whose
	 * flow sets their speed) turns the shaft beyond it at {@code motorRpm}, which may be negative. The box is moved from auto
	 * to neutral, optionally as the engine behind it chokes ({@code stallAfter} of 2 or more ticks), and has to stay
	 * standing, and the engine has to start again afterwards.
	 */
	private static void checkNeutralWithASourceOnTheOutput(GameTestHelper helper, int motorRpm, int stallAfter) {
		buildSingleCylinder(helper, true);
		helper.setBlock(PRESS, CEBlocks.TRANSMISSION.getDefaultState()
			.setValue(BlockStateProperties.AXIS, Axis.X));
		TransmissionBlockEntity box = helper.getBlockEntity(PRESS);
		box.panel.setValue(Gears.AUTO);
		helper.runAtTickTime(300, () -> helper.setBlock(STARTER_POWER, Blocks.AIR));
		helper.runAtTickTime(500, () -> {
			helper.setBlock(PRESS.east(), AllBlocks.SHAFT.getDefaultState()
				.setValue(BlockStateProperties.AXIS, Axis.X));
			helper.setBlock(PRESS.east(2), AllBlocks.SHAFT.getDefaultState()
				.setValue(BlockStateProperties.AXIS, Axis.X));
			helper.setBlock(PRESS.east(3), AllBlocks.CREATIVE_MOTOR.getDefaultState()
				.setValue(CreativeMotorBlock.FACING, Direction.WEST));
			CreativeMotorBlockEntity motor = helper.getBlockEntity(PRESS.east(3));
			motor.generatedSpeed.setValue(motorRpm);
		});
		helper.runAtTickTime(2500, () -> {
			helper.assertTrue(missingFromWheelRun(helper).isEmpty(), "Destroyed before the shift:" + missingFromWheelRun(helper));
			if (stallAfter >= 0)
				helper.setBlock(EXHAUST, Blocks.AIR);
		});
		int shift = 2500 + Math.max(stallAfter, 2);
		helper.runAtTickTime(shift, () -> {
			helper.assertTrue(missingFromWheelRun(helper).isEmpty(), "Destroyed before the shift:" + missingFromWheelRun(helper));
			TransmissionBlockEntity be = helper.getBlockEntity(PRESS);
			be.panel.setValue(Gears.NEUTRAL);
		});
		for (int tick = shift + 1; tick <= shift + 60; tick += 3)
			helper.runAtTickTime(tick, () -> helper.assertTrue(missingFromWheelRun(helper).isEmpty(),
				"With a " + motorRpm + " RPM source on the output, the shift to neutral destroyed:" + missingFromWheelRun(helper)));
		int restart = shift + 1200;
		helper.runAtTickTime(restart, () -> {
			helper.assertTrue(missingFromWheelRun(helper).isEmpty(), "Destroyed after the shift:" + missingFromWheelRun(helper));
			helper.setBlock(EXHAUST, CEBlocks.EXHAUST_OUTLET.getDefaultState()
				.setValue(PortOpeningBlock.FACING, Direction.WEST));
			helper.setBlock(STARTER_POWER, Blocks.REDSTONE_BLOCK);
		});
		helper.runAtTickTime(restart + 300, () -> helper.setBlock(STARTER_POWER, Blocks.AIR));
		for (int tick = restart + 1; tick <= restart + 900; tick += 20)
			helper.runAtTickTime(tick, () -> helper.assertTrue(missingFromWheelRun(helper).isEmpty(),
				"Starting the engine with a " + motorRpm + " RPM source on the output destroyed:" + missingFromWheelRun(helper)));
		helper.runAtTickTime(restart + 900, () -> {
			EngineSummary summary = summary(helper);
			helper.assertTrue(summary.state() == EngineState.RUNNING, "The engine should start with the load off: " + summary);
			helper.succeed();
		});
	}

	@GameTest(template = "empty_large", timeoutTicks = 7000)
	public static void neutralWithASlowSourceOnTheOutputKeepsTheTransmission(GameTestHelper helper) {
		checkNeutralWithASourceOnTheOutput(helper, 16, -1);
	}

	@GameTest(template = "empty_large", timeoutTicks = 7000)
	public static void neutralWithASlowReverseSourceOnTheOutputKeepsTheTransmission(GameTestHelper helper) {
		checkNeutralWithASourceOnTheOutput(helper, -16, -1);
	}

	@GameTest(template = "empty_large", timeoutTicks = 7000)
	public static void neutralWithAFastReverseSourceOnTheOutputKeepsTheTransmission(GameTestHelper helper) {
		checkNeutralWithASourceOnTheOutput(helper, -200, -1);
	}

	@GameTest(template = "empty_large", timeoutTicks = 7000)
	public static void neutralAsTheEngineChokesWithASourceOnTheOutputKeepsTheTransmission(GameTestHelper helper) {
		checkNeutralWithASourceOnTheOutput(helper, 16, 2);
	}

	@GameTest(template = "empty_large", timeoutTicks = 7000)
	public static void neutralOnAStoppedEngineWithASourceOnTheOutputKeepsTheTransmission(GameTestHelper helper) {
		checkNeutralWithASourceOnTheOutput(helper, 16, 700);
	}

	@GameTest(template = "empty_large", timeoutTicks = 7000)
	public static void neutralOnAStoppedEngineWithAReverseSourceOnTheOutputKeepsTheTransmission(GameTestHelper helper) {
		checkNeutralWithASourceOnTheOutput(helper, -16, 700);
	}

	/**
	 * Water wheels (a motor stands in for them) turn controller A at its own target, and A's large cog meshes with the large
	 * cog of controller B, which sits on the transmission's output line. B sets the speed of the cogs it shares with A, so the
	 * cog network is driven from two sides. Names the blocks of the whole rig that are gone.
	 */
	private static String missingFromTwoControllerRig(GameTestHelper helper) {
		StringBuilder gone = new StringBuilder();
		BlockPos b = PRESS.east(2);
		BlockPos a = PRESS.east(3).south();
		if (!helper.getBlockState(PRESS).is(CEBlocks.TRANSMISSION.get()))
			gone.append(" transmission");
		if (!helper.getBlockState(PRESS.east()).is(AllBlocks.SHAFT.get()))
			gone.append(" shaft1");
		if (!helper.getBlockState(b).is(AllBlocks.ROTATION_SPEED_CONTROLLER.get()))
			gone.append(" controllerB");
		if (!helper.getBlockState(b.above()).is(AllBlocks.LARGE_COGWHEEL.get()))
			gone.append(" cogB");
		if (!helper.getBlockState(b.above().south()).is(AllBlocks.SHAFT.get()))
			gone.append(" outputShaft");
		if (!helper.getBlockState(b.above().south(2)).is(AllBlocks.MECHANICAL_PRESS.get()))
			gone.append(" press");
		if (!helper.getBlockState(a).is(AllBlocks.ROTATION_SPEED_CONTROLLER.get()))
			gone.append(" controllerA");
		if (!helper.getBlockState(a.above()).is(AllBlocks.LARGE_COGWHEEL.get()))
			gone.append(" cogA");
		if (!helper.getBlockState(a.south(2)).is(AllBlocks.CREATIVE_MOTOR.get()))
			gone.append(" wheelMotor");
		if (!helper.getBlockState(a.south()).is(AllBlocks.SHAFT.get()))
			gone.append(" wheelShaft");
		return gone.toString();
	}

	private static void placeTwoControllerRig(GameTestHelper helper, int engineTarget, int wheelTarget, int wheelRpm) {
		BlockPos b = PRESS.east(2);
		// The wheels' controller sits a block beside and behind, turned across the engine's, so that its cog (turning on the
		// other axis, at the same height) meshes corner to corner with the engine controller's cog
		BlockPos a = PRESS.east(3).south();
		helper.setBlock(PRESS.east(), AllBlocks.SHAFT.getDefaultState()
			.setValue(BlockStateProperties.AXIS, Axis.X));
		helper.setBlock(b, AllBlocks.ROTATION_SPEED_CONTROLLER.getDefaultState()
			.setValue(BlockStateProperties.HORIZONTAL_AXIS, Axis.X));
		helper.setBlock(b.above(), AllBlocks.LARGE_COGWHEEL.getDefaultState()
			.setValue(BlockStateProperties.AXIS, Axis.Z));
		helper.setBlock(b.above().south(), AllBlocks.SHAFT.getDefaultState()
			.setValue(BlockStateProperties.AXIS, Axis.Z));
		helper.setBlock(b.above().south(2), AllBlocks.MECHANICAL_PRESS.getDefaultState()
			.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH));
		helper.setBlock(a, AllBlocks.ROTATION_SPEED_CONTROLLER.getDefaultState()
			.setValue(BlockStateProperties.HORIZONTAL_AXIS, Axis.Z));
		helper.setBlock(a.above(), AllBlocks.LARGE_COGWHEEL.getDefaultState()
			.setValue(BlockStateProperties.AXIS, Axis.X));
		helper.setBlock(a.south(), AllBlocks.SHAFT.getDefaultState()
			.setValue(BlockStateProperties.AXIS, Axis.Z));
		helper.setBlock(a.south(2), AllBlocks.CREATIVE_MOTOR.getDefaultState()
			.setValue(CreativeMotorBlock.FACING, Direction.NORTH));
		SpeedControllerBlockEntity engineSide = helper.getBlockEntity(b);
		engineSide.targetSpeed.setValue(engineTarget);
		SpeedControllerBlockEntity wheelSide = helper.getBlockEntity(a);
		wheelSide.targetSpeed.setValue(wheelTarget);
		CreativeMotorBlockEntity motor = helper.getBlockEntity(a.south(2));
		motor.generatedSpeed.setValue(wheelRpm);
	}

	/**
	 * The engine's controller on the transmission's line and the wheels' controller share their cogs. The box is moved to
	 * neutral (optionally as the engine chokes, {@code stallAfter} of 2 or more) or just left to stall, and nothing in the rig
	 * may be destroyed, before or after; the engine must start again with the load off.
	 */
	private static void checkTwoControllerRig(GameTestHelper helper, int stallAfter, boolean shift) {
		buildSingleCylinder(helper, true);
		helper.setBlock(PRESS, CEBlocks.TRANSMISSION.getDefaultState()
			.setValue(BlockStateProperties.AXIS, Axis.X));
		TransmissionBlockEntity box = helper.getBlockEntity(PRESS);
		box.panel.setValue(Gears.AUTO);
		helper.runAtTickTime(300, () -> helper.setBlock(STARTER_POWER, Blocks.AIR));
		helper.runAtTickTime(500, () -> placeTwoControllerRig(helper, 200, 30, 16));
		for (int tick = 520; tick <= 2480; tick += 40)
			helper.runAtTickTime(tick, () -> helper.assertTrue(missingFromTwoControllerRig(helper).isEmpty(),
				"The rig broke while the engine ran on auto:" + missingFromTwoControllerRig(helper)));
		helper.runAtTickTime(2490, () -> {
			KineticBlockEntity wheelCog = helper.getBlockEntity(PRESS.east(3).south().above());
			KineticBlockEntity engineCog = helper.getBlockEntity(PRESS.east(2).above());
			KineticBlockEntity press = helper.getBlockEntity(PRESS.east(2).above().south(2));
			KineticBlockEntity wheelController = helper.getBlockEntity(PRESS.east(3).south());
			KineticBlockEntity engineController = helper.getBlockEntity(PRESS.east(2));
			String speeds = "wheel controller " + wheelController.getSpeed() + ", wheel cog " + wheelCog.getSpeed()
				+ ", engine controller " + engineController.getSpeed() + ", engine cog " + engineCog.getSpeed()
				+ ", press " + press.getSpeed();
			helper.assertTrue(wheelCog.getSpeed() != 0 && engineCog.getSpeed() != 0 && press.getSpeed() != 0,
				"The rig should be turning before anything changes: " + speeds);
			helper.assertTrue(Math.abs(wheelCog.getSpeed()) == Math.abs(engineCog.getSpeed()),
				"The two cogs should be meshed and turn together: " + speeds);
		});
		helper.runAtTickTime(2500, () -> {
			helper.assertTrue(missingFromTwoControllerRig(helper).isEmpty(), "Destroyed before anything changed:" + missingFromTwoControllerRig(helper));
			if (stallAfter >= 0)
				helper.setBlock(EXHAUST, Blocks.AIR);
		});
		int shiftAt = 2500 + Math.max(stallAfter, 2);
		if (shift)
			helper.runAtTickTime(shiftAt, () -> {
				TransmissionBlockEntity be = helper.getBlockEntity(PRESS);
				be.panel.setValue(Gears.NEUTRAL);
			});
		int end = shiftAt + 1200;
		for (int tick = 2501; tick <= end; tick += 4)
			helper.runAtTickTime(tick, () -> helper.assertTrue(missingFromTwoControllerRig(helper).isEmpty(),
				"With the wheels on a second controller, destroyed:" + missingFromTwoControllerRig(helper)
					+ " (gear " + ((TransmissionBlockEntity) helper.getBlockEntity(PRESS)).gear() + ")"));
		helper.runAtTickTime(end + 1, () -> {
			helper.setBlock(EXHAUST, CEBlocks.EXHAUST_OUTLET.getDefaultState()
				.setValue(PortOpeningBlock.FACING, Direction.WEST));
			helper.setBlock(STARTER_POWER, Blocks.REDSTONE_BLOCK);
			TransmissionBlockEntity be = helper.getBlockEntity(PRESS);
			be.panel.setValue(Gears.NEUTRAL);
		});
		helper.runAtTickTime(end + 300, () -> helper.setBlock(STARTER_POWER, Blocks.AIR));
		for (int tick = end + 2; tick <= end + 900; tick += 20)
			helper.runAtTickTime(tick, () -> helper.assertTrue(missingFromTwoControllerRig(helper).isEmpty(),
				"Starting the engine destroyed:" + missingFromTwoControllerRig(helper)));
		helper.runAtTickTime(end + 900, () -> {
			EngineSummary summary = summary(helper);
			helper.assertTrue(summary.state() == EngineState.RUNNING, "The engine should start with the load off: " + summary);
			helper.succeed();
		});
	}

	@GameTest(template = "empty_large", timeoutTicks = 8000)
	public static void theRigWithWheelsOnASecondControllerSurvivesAutoToNeutral(GameTestHelper helper) {
		checkTwoControllerRig(helper, -1, true);
	}

	@GameTest(template = "empty_large", timeoutTicks = 8000)
	public static void theRigWithWheelsOnASecondControllerSurvivesAStall(GameTestHelper helper) {
		checkTwoControllerRig(helper, 2, false);
	}

	@GameTest(template = "empty_large", timeoutTicks = 8000)
	public static void theRigWithWheelsOnASecondControllerSurvivesNeutralOnAStoppedEngine(GameTestHelper helper) {
		checkTwoControllerRig(helper, 700, true);
	}

	/** The same with Create's speed limit raised to 512 and the controller set to run the presses at it. */
	@GameTest(template = "empty_large", timeoutTicks = 6000, batch = "speedController")
	public static void aTransmissionBehindAFastSpeedControllerSurvivesAutoToNeutral(GameTestHelper helper) {
		checkNeutralBehindAController(helper, 512, 512, 1);
	}
}
