package com.createengines.gametest;

import static com.createengines.gametest.EngineRigs.*;
import static com.createengines.gametest.EngineGameTests.setLever;

import com.createengines.CreateEngines;
import com.createengines.content.airflow.PortOpeningBlock;
import com.createengines.content.crankshaft.CrankshaftBlockEntity;
import com.createengines.content.transmission.Gears;
import com.createengines.content.transmission.TransmissionBlockEntity;
import com.createengines.engine.EngineState;
import com.createengines.engine.EngineSummary;
import com.createengines.registry.CEBlocks;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlock;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** The transmission's gears, its redstone control and its automatic mode. Test engines as in {@link EngineGameTests}. */
@GameTestHolder(CreateEngines.MOD_ID)
@PrefixGameTestTemplate(false)
public class TransmissionGameTests {

	/** A creative motor, a transmission and a shaft in a line along X. */
	private static void buildTransmission(GameTestHelper helper, int gear) {
		helper.setBlock(new BlockPos(0, 1, 1), AllBlocks.CREATIVE_MOTOR.getDefaultState()
			.setValue(CreativeMotorBlock.FACING, Direction.EAST));
		CreativeMotorBlockEntity motor = helper.getBlockEntity(new BlockPos(0, 1, 1));
		motor.generatedSpeed.setValue(64);
		helper.setBlock(new BlockPos(1, 1, 1), CEBlocks.TRANSMISSION.getDefaultState()
			.setValue(BlockStateProperties.AXIS, Axis.X));
		helper.setBlock(new BlockPos(2, 1, 1), AllBlocks.SHAFT.getDefaultState()
			.setValue(BlockStateProperties.AXIS, Axis.X));
		TransmissionBlockEntity transmission = helper.getBlockEntity(new BlockPos(1, 1, 1));
		transmission.panel.setValue(gear);
	}

	private static float outputSpeed(GameTestHelper helper) {
		KineticBlockEntity shaft = helper.getBlockEntity(new BlockPos(2, 1, 1));
		return shaft.getSpeed();
	}

	/** Each gear turns the output at its own fraction of the input, neutral lets nothing through, reverse turns it back. */
	@GameTest(template = "empty", timeoutTicks = 200)
	public static void aTransmissionTurnsItsOutputAtTheSelectedGear(GameTestHelper helper) {
		buildTransmission(helper, Gears.FIRST);
		BlockPos at = new BlockPos(1, 1, 1);
		int[] gears = {Gears.FIRST, 2, 7, Gears.TOP, Gears.NEUTRAL, Gears.REVERSE};
		float[] speeds = {5.333f, 6.557f, 18.497f, 64, 0, -21.333f};
		for (int i = 0; i < gears.length; i++) {
			int gear = gears[i];
			float speed = speeds[i];
			helper.runAtTickTime(20 + i * 20, () -> {
				TransmissionBlockEntity transmission = helper.getBlockEntity(at);
				transmission.panel.setValue(gear);
			});
			helper.runAtTickTime(32 + i * 20, () -> helper.assertTrue(Math.abs(outputSpeed(helper) - speed) < 0.1f,
				"Gear " + gear + " should turn the output at " + speed + " RPM, not " + outputSpeed(helper)));
		}
		helper.runAtTickTime(150, helper::succeed);
	}

	/** A redstone signal selects a setting over the panel, auto included, and the panel takes over again without one. */
	@GameTest(template = "empty", timeoutTicks = 200)
	public static void redstoneOverridesTheTransmissionPanel(GameTestHelper helper) {
		buildTransmission(helper, Gears.FIRST);
		BlockPos at = new BlockPos(1, 1, 1);
		BlockPos power = new BlockPos(1, 2, 1);
		helper.runAtTickTime(20, () -> helper.assertTrue(Math.abs(outputSpeed(helper) - 5.333f) < 0.1f,
			"The panel's first gear should be in use: " + outputSpeed(helper)));
		helper.runAtTickTime(25, () -> setLever(helper, power, 7));
		helper.runAtTickTime(45, () -> helper.assertTrue(Math.abs(outputSpeed(helper) - 18.497f) < 0.1f,
			"Strength 7 selects seventh: " + outputSpeed(helper)));
		helper.runAtTickTime(50, () -> setLever(helper, power, 14));
		helper.runAtTickTime(70, () -> helper.assertTrue(Math.abs(outputSpeed(helper) + 21.333f) < 0.1f,
			"Strength 14 selects reverse: " + outputSpeed(helper)));
		helper.runAtTickTime(75, () -> setLever(helper, power, 15));
		helper.runAtTickTime(95, () -> {
			TransmissionBlockEntity box = helper.getBlockEntity(at);
			helper.assertTrue(box.isAuto(), "Full strength puts it on auto");
			// Out of reverse, and with no engine to read, auto starts in first and stays there
			helper.assertTrue(box.gear() == Gears.FIRST, "Auto from reverse starts in first: " + box.gear());
			helper.assertTrue(box.panel.getValue() == Gears.FIRST, "The panel is left alone: " + box.panel.getValue());
		});
		helper.runAtTickTime(100, () -> setLever(helper, power, 1));
		helper.runAtTickTime(120, () -> {
			TransmissionBlockEntity box = helper.getBlockEntity(at);
			helper.assertTrue(!box.isAuto() && box.gear() == Gears.FIRST, "Strength 1 is first, by signal: " + box.gear());
			box.panel.setValue(7);
		});
		helper.runAtTickTime(125, () -> helper.setBlock(power, Blocks.AIR));
		helper.runAtTickTime(145, () -> {
			helper.assertTrue(Math.abs(outputSpeed(helper) - 18.497f) < 0.1f,
				"Without a signal the panel decides again: " + outputSpeed(helper));
			helper.succeed();
		});
	}

	/** In a low gear a crank carries a load that overstresses it direct: the speed-for-torque trade the block is for. */
	@GameTest(template = "empty", timeoutTicks = 2000)
	public static void aLowGearLetsAnEngineCarryWhatItCannotDirect(GameTestHelper helper) {
		buildSingleCylinder(helper, true);
		// The press row hangs off a transmission after the cam drive, in first gear
		helper.setBlock(PRESS, CEBlocks.TRANSMISSION.getDefaultState()
			.setValue(BlockStateProperties.AXIS, Axis.X));
		TransmissionBlockEntity box = helper.getBlockEntity(PRESS);
		box.panel.setValue(Gears.FIRST);
		helper.runAtTickTime(300, () -> helper.setBlock(STARTER_POWER, Blocks.AIR));
		helper.runAtTickTime(500, () -> {
			for (int i = 0; i < 3; i++)
				helper.setBlock(PRESS.east(1 + i), AllBlocks.MECHANICAL_PRESS.getDefaultState()
					.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST));
		});
		helper.runAtTickTime(1500, () -> {
			CrankshaftBlockEntity crank = helper.getBlockEntity(PRIMARY);
			helper.assertFalse(crank.isOverStressed(),
				"Three presses overstress this engine direct, but first gear should carry them");
			KineticBlockEntity press = helper.getBlockEntity(PRESS.east());
			helper.assertTrue(press.getSpeed() != 0, "The presses should turn behind the transmission");
			helper.succeed();
		});
	}

	/** In neutral a transmission passes no load back: presses that would overstress the engine direct leave it alone. */
	@GameTest(template = "empty", timeoutTicks = 2000)
	public static void aNeutralTransmissionCannotOverstressTheEngine(GameTestHelper helper) {
		buildSingleCylinder(helper, true);
		helper.setBlock(PRESS, CEBlocks.TRANSMISSION.getDefaultState()
			.setValue(BlockStateProperties.AXIS, Axis.X));
		TransmissionBlockEntity box = helper.getBlockEntity(PRESS);
		box.panel.setValue(Gears.NEUTRAL);
		helper.runAtTickTime(300, () -> helper.setBlock(STARTER_POWER, Blocks.AIR));
		for (int tick = 500; tick <= 1500; tick += 250) {
			boolean last = tick == 1500;
			boolean first = tick == 500;
			helper.runAtTickTime(tick, () -> {
				if (first)
					placePresses(helper);
				CrankshaftBlockEntity crank = helper.getBlockEntity(PRIMARY);
				helper.assertFalse(crank.isOverStressed(), "A transmission in neutral carries no load");
				KineticBlockEntity press = helper.getBlockEntity(PRESS.east());
				helper.assertTrue(press.getSpeed() == 0, "Nothing should turn behind a neutral transmission: " + press.getSpeed());
				if (last)
					helper.succeed();
			});
		}
	}

	/** Moved from neutral to auto under load, a transmission picks a gear the engine can carry and drives the presses. */
	@GameTest(template = "empty", timeoutTicks = 4000)
	public static void aTransmissionShiftsFromNeutralToAuto(GameTestHelper helper) {
		buildSingleCylinder(helper, true);
		helper.setBlock(PRESS, CEBlocks.TRANSMISSION.getDefaultState()
			.setValue(BlockStateProperties.AXIS, Axis.X));
		TransmissionBlockEntity box = helper.getBlockEntity(PRESS);
		box.panel.setValue(Gears.NEUTRAL);
		helper.runAtTickTime(300, () -> helper.setBlock(STARTER_POWER, Blocks.AIR));
		helper.runAtTickTime(500, () -> placePresses(helper));
		helper.runAtTickTime(1200, () -> {
			helper.assertTrue(box.gear() == Gears.NEUTRAL, "It should still be in neutral: " + box.gear());
			box.panel.setValue(Gears.AUTO);
		});
		helper.runAtTickTime(1300, () -> {
			helper.assertTrue(box.isAuto(), "The panel should be on auto");
			helper.assertTrue(box.gear() != Gears.NEUTRAL, "Auto should have taken it out of neutral: " + box.gear());
		});
		helper.runAtTickTime(3600, () -> {
			CrankshaftBlockEntity crank = helper.getBlockEntity(PRIMARY);
			helper.assertFalse(crank.isOverStressed(), "Auto should carry the presses without overstressing: gear " + box.gear());
			KineticBlockEntity press = helper.getBlockEntity(PRESS.east());
			helper.assertTrue(press.getSpeed() != 0, "The presses should turn once auto is driving them");
			helper.succeed();
		});
	}

	/**
	 * Taken off auto and put in neutral while carrying the presses, a transmission lets go of the load and the engine
	 * keeps running; and an engine that then stops starts again with nothing on it.
	 */
	@GameTest(template = "empty", timeoutTicks = 6000)
	public static void aTransmissionMovedFromAutoToNeutralLeavesTheEngineRunningAndStartable(GameTestHelper helper) {
		buildSingleCylinder(helper, true);
		helper.setBlock(PRESS, CEBlocks.TRANSMISSION.getDefaultState()
			.setValue(BlockStateProperties.AXIS, Axis.X));
		TransmissionBlockEntity box = helper.getBlockEntity(PRESS);
		box.panel.setValue(Gears.AUTO);
		helper.runAtTickTime(300, () -> helper.setBlock(STARTER_POWER, Blocks.AIR));
		helper.runAtTickTime(500, () -> placePresses(helper));
		helper.runAtTickTime(2500, () -> {
			helper.assertTrue(box.gear() != Gears.NEUTRAL, "Auto should be driving the presses: " + box.gear());
			box.panel.setValue(Gears.NEUTRAL);
		});
		helper.runAtTickTime(2600, () -> {
			helper.assertTrue(box.gear() == Gears.NEUTRAL, "It should be in neutral: " + box.gear());
			helper.assertFalse(box.isAuto(), "It should not still be on auto");
			EngineSummary summary = summary(helper);
			helper.assertTrue(summary.state() == EngineState.RUNNING, "Letting go of the load should not stall it: " + summary);
			KineticBlockEntity press = helper.getBlockEntity(PRESS.east());
			helper.assertTrue(press.getSpeed() == 0, "Nothing should turn behind neutral: " + press.getSpeed());
			// Starve it, as a stall would, and give it a moment to stop
			helper.setBlock(EXHAUST, Blocks.AIR);
		});
		helper.runAtTickTime(3600, () -> {
			helper.assertTrue(summary(helper).state() == EngineState.STALLED, "It should have stopped: " + summary(helper));
			helper.setBlock(EXHAUST, CEBlocks.EXHAUST_OUTLET.getDefaultState()
				.setValue(PortOpeningBlock.FACING, Direction.WEST));
			helper.setBlock(STARTER_POWER, Blocks.REDSTONE_BLOCK);
		});
		helper.runAtTickTime(3900, () -> helper.setBlock(STARTER_POWER, Blocks.AIR));
		helper.runAtTickTime(4500, () -> {
			EngineSummary summary = summary(helper);
			helper.assertTrue(summary.state() == EngineState.RUNNING, "With the load off, the starter should get it going: " + summary);
			helper.assertTrue(box.gear() == Gears.NEUTRAL, "It should still be in neutral: " + box.gear());
			helper.succeed();
		});
	}

	/**
	 * Scrolled down from auto to neutral a notch at a time, a transmission passes through reverse and every forward gear on
	 * the way. Create breaks a split shaft whose output is told to turn against a network that is still turning, so the box
	 * has to come through the walk down intact, at any pace the notches arrive.
	 */
	@GameTest(template = "empty", timeoutTicks = 6000)
	public static void aTransmissionScrolledFromAutoDownToNeutralIsNotDestroyed(GameTestHelper helper) {
		buildSingleCylinder(helper, true);
		helper.setBlock(PRESS, CEBlocks.TRANSMISSION.getDefaultState()
			.setValue(BlockStateProperties.AXIS, Axis.X));
		TransmissionBlockEntity box = helper.getBlockEntity(PRESS);
		box.panel.setValue(Gears.AUTO);
		helper.runAtTickTime(300, () -> helper.setBlock(STARTER_POWER, Blocks.AIR));
		helper.runAtTickTime(500, () -> placePresses(helper));
		int start = 2500;
		int spacing = 3;
		for (int notch = 0; notch <= Gears.AUTO; notch++) {
			int setting = Gears.AUTO - notch;
			helper.runAtTickTime(start + notch * spacing, () -> {
				helper.assertTrue(helper.getBlockState(PRESS).is(CEBlocks.TRANSMISSION.get()),
					"The transmission was destroyed on the way down, before setting " + setting);
				TransmissionBlockEntity be = helper.getBlockEntity(PRESS);
				be.panel.setValue(setting);
			});
		}
		helper.runAtTickTime(start + (Gears.AUTO + 1) * spacing + 100, () -> {
			helper.assertTrue(helper.getBlockState(PRESS).is(CEBlocks.TRANSMISSION.get()),
				"The transmission should still be there");
			TransmissionBlockEntity be = helper.getBlockEntity(PRESS);
			helper.assertTrue(be.gear() == Gears.NEUTRAL, "It should end in neutral: " + be.gear());
			EngineSummary summary = summary(helper);
			helper.assertTrue(summary.state() == EngineState.RUNNING, "The engine should be running with the load off: " + summary);
			helper.succeed();
		});
	}

	/** On auto, a transmission that starts in first gear climbs until the engine is carrying about as much as it can. */
	@GameTest(template = "empty", timeoutTicks = 4000)
	public static void anAutoTransmissionClimbsToWhatTheEngineCanPull(GameTestHelper helper) {
		buildSingleCylinder(helper, true);
		helper.setBlock(PRESS, CEBlocks.TRANSMISSION.getDefaultState()
			.setValue(BlockStateProperties.AXIS, Axis.X));
		TransmissionBlockEntity box = helper.getBlockEntity(PRESS);
		box.panel.setValue(Gears.AUTO);
		helper.runAtTickTime(300, () -> helper.setBlock(STARTER_POWER, Blocks.AIR));
		helper.runAtTickTime(500, () -> placePresses(helper));
		helper.runAtTickTime(3600, () -> {
			CrankshaftBlockEntity crank = helper.getBlockEntity(PRIMARY);
			helper.assertFalse(crank.isOverStressed(), "Auto should have found a gear that carries the presses");
			helper.assertTrue(box.gear() > Gears.FIRST, "With room to spare it should have climbed out of first: " + box.gear());
			helper.assertTrue(box.gear() < Gears.TOP, "Three presses overstress this engine direct, so not to the top: " + box.gear());
			KineticBlockEntity press = helper.getBlockEntity(PRESS.east());
			helper.assertTrue(press.getSpeed() != 0, "The presses should turn behind the transmission");
			helper.succeed();
		});
	}

	/** Put on auto while overstressed in a gear that is too tall, a transmission drops to one the engine can carry. */
	@GameTest(template = "empty_large", timeoutTicks = 4000)
	public static void anAutoTransmissionDownshiftsOutOfAnOverload(GameTestHelper helper) {
		buildSingleCylinder(helper, true);
		helper.setBlock(PRESS, CEBlocks.TRANSMISSION.getDefaultState()
			.setValue(BlockStateProperties.AXIS, Axis.X));
		TransmissionBlockEntity box = helper.getBlockEntity(PRESS);
		box.panel.setValue(Gears.TOP);
		helper.runAtTickTime(300, () -> helper.setBlock(STARTER_POWER, Blocks.AIR));
		helper.runAtTickTime(500, () -> placePresses(helper, scaledPresses(3)));
		helper.runAtTickTime(1500, () -> {
			CrankshaftBlockEntity crank = helper.getBlockEntity(PRIMARY);
			helper.assertTrue(crank.isOverStressed(), "In the top gear three presses should overstress the engine");
			box.panel.setValue(Gears.AUTO);
		});
		helper.runAtTickTime(3600, () -> {
			CrankshaftBlockEntity crank = helper.getBlockEntity(PRIMARY);
			helper.assertFalse(crank.isOverStressed(), "Auto should have dropped to a gear the engine can carry: gear " + box.gear());
			helper.assertTrue(box.gear() < Gears.TOP, "It should have left the top gear: " + box.gear());
			helper.succeed();
		});
	}

	/** Already driving on auto in the top gear, a transmission drops when a load arrives that it cannot carry there. */
	@GameTest(template = "empty_large", timeoutTicks = 4000)
	public static void anAutoTransmissionDownshiftsWhenALoadArrives(GameTestHelper helper) {
		buildSingleCylinder(helper, true);
		helper.setBlock(PRESS, CEBlocks.TRANSMISSION.getDefaultState()
			.setValue(BlockStateProperties.AXIS, Axis.X));
		TransmissionBlockEntity box = helper.getBlockEntity(PRESS);
		box.panel.setValue(Gears.AUTO);
		helper.runAtTickTime(300, () -> helper.setBlock(STARTER_POWER, Blocks.AIR));
		// A shaft behind it, so there is an output to turn while it climbs with nothing to drive
		helper.runAtTickTime(500, () -> helper.setBlock(PRESS.east(), AllBlocks.SHAFT.getDefaultState()
			.setValue(BlockStateProperties.AXIS, Axis.X)));
		helper.runAtTickTime(1200, () -> {
			helper.assertTrue(box.gear() == Gears.TOP, "With no load auto should be in the top gear: " + box.gear());
			placePresses(helper, scaledPresses(3));
		});
		helper.runAtTickTime(3600, () -> {
			CrankshaftBlockEntity crank = helper.getBlockEntity(PRIMARY);
			helper.assertFalse(crank.isOverStressed(), "Auto should have dropped to a gear the engine can carry: gear " + box.gear());
			helper.assertTrue(box.gear() < Gears.TOP, "It should have left the top gear: " + box.gear());
			KineticBlockEntity press = helper.getBlockEntity(PRESS.east());
			helper.assertTrue(press.getSpeed() != 0, "The presses should turn behind the transmission");
			helper.succeed();
		});
	}
}
