package com.createengines.gametest;

import com.createengines.CreateEngines;
import com.createengines.content.alternator.AlternatorBlockEntity;
import com.createengines.content.blackbox.BlackBoxBlockEntity;
import com.createengines.content.crankshaft.CrankshaftBlockEntity;
import com.createengines.content.display.EngineReadout;
import com.createengines.content.display.TransmissionGearDisplaySource;
import com.createengines.content.starter.StarterBlock;
import com.createengines.content.starter.StarterBlockEntity;
import com.createengines.content.transmission.Gears;
import com.createengines.content.transmission.TransmissionBlockEntity;
import com.createengines.engine.Diagnosis;
import com.createengines.engine.EngineState;
import com.createengines.engine.EngineSummary;
import com.createengines.engine.FlightRecorder;
import com.createengines.registry.CEBlocks;
import com.createengines.registry.CEDisplaySources;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.api.behaviour.display.DisplaySource;
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.content.kinetics.crank.HandCrankBlockEntity;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlock;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Part depth: a starter turned by a hand crank engages with no redstone, an alternator reads out what it makes and what share
 * of the engine's torque it takes, and several alternators on one engine say what each contributes while their stress adds up.
 * In their own batch so that the default batch's groups of 50 are not moved by these.
 */
@GameTestHolder(CreateEngines.MOD_ID)
@PrefixGameTestTemplate(false)
public class PartDepthGameTests {

	private static final String BATCH = "part-depth";

	@GameTest(template = "empty", timeoutTicks = 200, batch = BATCH)
	public static void aStarterTurnedByAHandCrankEngagesWithoutRedstoneAndOneOnAMotorDoesNot(GameTestHelper helper) {
		// Hand crank under a starter, no redstone anywhere near it
		BlockPos byHand = new BlockPos(1, 2, 1);
		helper.setBlock(byHand.below(), AllBlocks.HAND_CRANK.getDefaultState()
			.setValue(DirectionalKineticBlock.FACING, Direction.DOWN));
		helper.setBlock(byHand, CEBlocks.STARTER.getDefaultState()
			.setValue(StarterBlock.FACING, Direction.UP));
		// A motor under another starter, no redstone either
		BlockPos byMotor = new BlockPos(5, 2, 1);
		helper.setBlock(byMotor.below(), AllBlocks.CREATIVE_MOTOR.getDefaultState()
			.setValue(CreativeMotorBlock.FACING, Direction.UP));
		helper.setBlock(byMotor, CEBlocks.STARTER.getDefaultState()
			.setValue(StarterBlock.FACING, Direction.UP));
		((CreativeMotorBlockEntity) helper.getBlockEntity(byMotor.below())).generatedSpeed.setValue(40);
		EveryTick.run(helper, () -> ((HandCrankBlockEntity) helper.getBlockEntity(byHand.below())).turn(false));
		helper.succeedWhen(() -> {
			StarterBlockEntity hand = helper.getBlockEntity(byHand);
			StarterBlockEntity motor = helper.getBlockEntity(byMotor);
			helper.assertTrue(motor.getSpeed() != 0, "The motor should be turning its starter");
			helper.assertTrue(!motor.isHandCranked() && !motor.wantsToCrank(), "A starter on a motor still needs redstone");
			helper.assertTrue(hand.getSpeed() != 0, "The hand crank should be turning its starter");
			helper.assertTrue(hand.isHandCranked(), "The starter knows its shaft is turned by hand");
			helper.assertTrue(hand.wantsToCrank(), "so it engages without redstone");
		});
	}

	@GameTest(template = "empty", timeoutTicks = 200, batch = BATCH)
	public static void aStarterOnAHandCrankStillStopsWhenNobodyTurnsIt(GameTestHelper helper) {
		BlockPos byHand = new BlockPos(1, 2, 1);
		helper.setBlock(byHand.below(), AllBlocks.HAND_CRANK.getDefaultState()
			.setValue(DirectionalKineticBlock.FACING, Direction.DOWN));
		helper.setBlock(byHand, CEBlocks.STARTER.getDefaultState()
			.setValue(StarterBlock.FACING, Direction.UP));
		helper.runAtTickTime(60, () -> {
			StarterBlockEntity hand = helper.getBlockEntity(byHand);
			helper.assertTrue(!hand.wantsToCrank(), "Nobody is turning the crank, so the starter is not pushing");
			helper.succeed();
		});
	}

	@GameTest(template = "empty", timeoutTicks = 800, batch = BATCH)
	public static void anAlternatorReadsWhatItMakesAndItsShareOfTheEnginesTorque(GameTestHelper helper) {
		TestEngine.buildSpark(helper.getLevel(), helper.absolutePos(BlockPos.ZERO), true);
		helper.runAtTickTime(300, () -> helper.setBlock(TestEngine.STARTER_POWER, Blocks.AIR));
		boolean[] charged = {false};
		helper.succeedWhen(() -> {
			AlternatorBlockEntity alternator = helper.getBlockEntity(TestEngine.ALTERNATOR);
			CrankshaftBlockEntity crank = helper.getBlockEntity(TestEngine.PRIMARY);
			charged[0] |= alternator.chargeRate() > 0.05f;
			helper.assertTrue(charged[0], "While it fills the flat battery it should read out a charge rate");
			helper.assertTrue(crank.publishedCapacity() > 0 && alternator.engineShare() > 0,
				"With the engine running it should read a share of its torque: " + alternator.engineShare());
			float expected = Math.min(1, 2 / crank.publishedCapacity());
			helper.assertTrue(Math.abs(alternator.engineShare() - expected) < 0.05f,
				"The share is its two SU a RPM over what the engine publishes: " + alternator.engineShare() + " against " + expected);
			helper.assertTrue(alternator.fellowCount() == 1, "A lone alternator is the only one: " + alternator.fellowCount());
		});
	}

	@GameTest(template = "empty", timeoutTicks = 800, batch = BATCH)
	public static void twoAlternatorsOnOneEngineEachReadTheirPartOfTheCharge(GameTestHelper helper) {
		TestEngine.buildSpark(helper.getLevel(), helper.absolutePos(BlockPos.ZERO), true);
		// Where the plain shaft was, between the crank and the cogwheel
		BlockPos second = new BlockPos(4, 2, 2);
		helper.setBlock(second, TestEngine.axisX(CEBlocks.ALTERNATOR.getDefaultState()));
		helper.runAtTickTime(300, () -> helper.setBlock(TestEngine.STARTER_POWER, Blocks.AIR));
		boolean[] bothCharged = {false};
		helper.succeedWhen(() -> {
			AlternatorBlockEntity first = helper.getBlockEntity(TestEngine.ALTERNATOR);
			AlternatorBlockEntity other = helper.getBlockEntity(second);
			helper.assertTrue(first.fellowCount() == 2 && other.fellowCount() == 2,
				"Each knows there are two on the engine: " + first.fellowCount() + " and " + other.fellowCount());
			bothCharged[0] |= first.chargeRate() > 0.05f && other.chargeRate() > 0.05f;
			helper.assertTrue(bothCharged[0], "Both should have been charging together at some point");
			helper.assertTrue(first.fellowRate() > 0 && Math.abs(first.fellowRate() - other.fellowRate()) < 0.3f,
				"and both read the same total: " + first.fellowRate() + " and " + other.fellowRate());
			helper.assertTrue(first.engineShare() > 0 && other.engineShare() > 0, "each takes a share of the engine's torque");
		});
	}

	/** The stress on a shaft with two alternators charging is the sum of theirs: twice one's. */
	@GameTest(template = "empty", timeoutTicks = 300, batch = BATCH)
	public static void theStressOfTwoChargingAlternatorsIsTheSumOfTheirs(GameTestHelper helper) {
		helper.setBlock(new BlockPos(0, 2, 1), AllBlocks.CREATIVE_MOTOR.getDefaultState()
			.setValue(CreativeMotorBlock.FACING, Direction.EAST));
		helper.setBlock(new BlockPos(1, 2, 1), TestEngine.axisX(AllBlocks.SHAFT.getDefaultState()));
		BlockPos firstAt = new BlockPos(2, 2, 1);
		BlockPos secondAt = new BlockPos(3, 2, 1);
		helper.setBlock(firstAt, TestEngine.axisX(CEBlocks.ALTERNATOR.getDefaultState()));
		helper.setBlock(secondAt, TestEngine.axisX(CEBlocks.ALTERNATOR.getDefaultState()));
		// A battery above each: they are diagonal to each other, so two banks of one
		helper.setBlock(firstAt.above(), CEBlocks.BATTERY.getDefaultState());
		helper.setBlock(secondAt.above(), CEBlocks.BATTERY.getDefaultState());
		((CreativeMotorBlockEntity) helper.getBlockEntity(new BlockPos(0, 2, 1))).generatedSpeed.setValue(40);
		float[] both = {0};
		helper.runAtTickTime(80, () -> {
			AlternatorBlockEntity first = helper.getBlockEntity(firstAt);
			helper.assertTrue(first.getSpeed() != 0, "The motor should be turning the alternators");
			both[0] = first.getOrCreateNetwork()
				.calculateStress();
			helper.assertTrue(both[0] > 0, "Two charging alternators put stress on the shaft: " + both[0]);
			// Take the second one's battery away: only the first has anything to charge
			helper.setBlock(secondAt.above(), Blocks.AIR);
		});
		helper.runAtTickTime(200, () -> {
			AlternatorBlockEntity first = helper.getBlockEntity(firstAt);
			float one = first.getOrCreateNetwork()
				.calculateStress();
			helper.assertTrue(one > 0 && Math.abs(both[0] - 2 * one) < 0.02f * both[0],
				"Two alternators charging take twice what one does: " + both[0] + " against " + one);
			helper.succeed();
		});
	}

	@GameTest(template = "empty", timeoutTicks = 260, batch = BATCH)
	public static void anAlternatorSetToHalfTakesHalfTheStressAndSavesItsDialApartFromEverythingElse(GameTestHelper helper) {
		helper.setBlock(new BlockPos(0, 2, 1), AllBlocks.CREATIVE_MOTOR.getDefaultState()
			.setValue(CreativeMotorBlock.FACING, Direction.EAST));
		helper.setBlock(new BlockPos(1, 2, 1), TestEngine.axisX(AllBlocks.SHAFT.getDefaultState()));
		BlockPos at = new BlockPos(2, 2, 1);
		helper.setBlock(at, TestEngine.axisX(CEBlocks.ALTERNATOR.getDefaultState()));
		helper.setBlock(at.above(), CEBlocks.BATTERY.getDefaultState());
		((CreativeMotorBlockEntity) helper.getBlockEntity(new BlockPos(0, 2, 1))).generatedSpeed.setValue(40);
		AlternatorBlockEntity box = helper.getBlockEntity(at);
		helper.assertTrue(box.chargeRateDial.getValue() == 3, "A new alternator is at full output");
		helper.assertTrue(box.outputShare() == 1, "and takes its whole load");
		float[] full = {0};
		helper.runAtTickTime(80, () -> {
			AlternatorBlockEntity alternator = helper.getBlockEntity(at);
			full[0] = alternator.getOrCreateNetwork()
				.calculateStress();
			helper.assertTrue(full[0] > 0, "A charging alternator puts stress on the shaft: " + full[0]);
			alternator.chargeRateDial.setValue(1);
		});
		helper.runAtTickTime(160, () -> {
			AlternatorBlockEntity alternator = helper.getBlockEntity(at);
			float half = alternator.getOrCreateNetwork()
				.calculateStress();
			helper.assertTrue(half > 0 && Math.abs(half - full[0] / 2) < 0.02f * full[0],
				"At half the dial it takes half the stress: " + half + " against " + full[0]);
			net.minecraft.nbt.CompoundTag tag = alternator.saveWithFullMetadata(helper.getLevel()
				.registryAccess());
			helper.assertTrue(tag.getInt("ChargeRateSetting") == 1, "The setting saves under its own key");
			alternator.chargeRateDial.setValue(3);
			alternator.loadWithComponents(tag, helper.getLevel()
				.registryAccess());
			helper.assertTrue(alternator.chargeRateDial.getValue() == 1, "and reads back as it was: " + alternator.chargeRateDial.getValue());
			net.minecraft.nbt.CompoundTag old = tag.copy();
			old.remove("ChargeRateSetting");
			alternator.loadWithComponents(old, helper.getLevel()
				.registryAccess());
			helper.assertTrue(alternator.chargeRateDial.getValue() == 3, "An alternator saved before the dial reads as full output");
			net.minecraft.nbt.CompoundTag wild = tag.copy();
			wild.putInt("ChargeRateSetting", 99);
			alternator.loadWithComponents(wild, helper.getLevel()
				.registryAccess());
			helper.assertTrue(alternator.chargeRateDial.getValue() == 3, "A damaged setting is held to the dial's range");
			helper.succeed();
		});
	}

	/** The gear and the fault are one Display Link source each: the gear on a transmission, the fault on any crankshaft block. */
	@GameTest(template = "empty", batch = BATCH)
	public static void aTransmissionOffersItsGearAndACrankshaftItsFaultToADisplayLink(GameTestHelper helper) {
		TestEngine.build(helper.getLevel(), helper.absolutePos(BlockPos.ZERO), true);
		BlockPos gearbox = new BlockPos(1, 5, 1);
		helper.setBlock(gearbox, CEBlocks.TRANSMISSION.getDefaultState()
			.setValue(BlockStateProperties.AXIS, Direction.Axis.X));
		helper.assertTrue(DisplaySource.getAll(helper.getLevel(), helper.absolutePos(gearbox))
			.contains(CEDisplaySources.TRANSMISSION_GEAR.get()), "A transmission should offer its gear");
		for (BlockPos crank : java.util.List.of(TestEngine.PRIMARY, TestEngine.THROW))
			helper.assertTrue(DisplaySource.getAll(helper.getLevel(), helper.absolutePos(crank))
				.contains(CEDisplaySources.ENGINE_FAULT.get()), "A crankshaft block should offer the fault: " + crank);
		helper.succeed();
	}

	private static String keyOf(net.minecraft.network.chat.Component line) {
		// The mod's lang keys carry the mod id in front
		String key = ((TranslatableContents) line.getContents()).getKey();
		return key.startsWith(CreateEngines.MOD_ID + ".") ? key.substring(CreateEngines.MOD_ID.length() + 1) : key;
	}

	@GameTest(template = "empty", batch = BATCH)
	public static void theGearLineNamesNeutralReverseAndANumberedGearAndSaysWhenItIsAuto(GameTestHelper helper) {
		helper.assertTrue(keyOf(TransmissionGearDisplaySource.line(Gears.NEUTRAL, false))
			.equals("display.transmission.neutral"), "Neutral is named");
		helper.assertTrue(keyOf(TransmissionGearDisplaySource.line(Gears.REVERSE, false))
			.equals("display.transmission.reverse"), "Reverse is named");
		var seventh = TransmissionGearDisplaySource.line(7, false);
		helper.assertTrue(keyOf(seventh).equals("display.transmission.gear")
			&& ((TranslatableContents) seventh.getContents()).getArgs()[0].equals("7") && seventh.getSiblings()
				.isEmpty(), "A numbered gear says its number and nothing more on manual");
		var auto = TransmissionGearDisplaySource.line(7, true);
		helper.assertTrue(auto.getSiblings()
			.size() == 1 && keyOf(auto.getSiblings()
				.get(0))
				.equals("display.transmission.auto"), "On auto the gear in use is followed by (auto)");
		helper.assertTrue(TransmissionGearDisplaySource.line(Gears.NEUTRAL, true)
			.getSiblings()
			.isEmpty(), "Neutral and reverse are never the auto pick, so they carry no mark");
		helper.succeed();
	}

	/** A live gearbox: what the link would show is the gear it is in. */
	@GameTest(template = "empty", timeoutTicks = 200, batch = BATCH)
	public static void aGearboxLineFollowsTheGearItIsIn(GameTestHelper helper) {
		helper.setBlock(new BlockPos(0, 1, 1), AllBlocks.CREATIVE_MOTOR.getDefaultState()
			.setValue(CreativeMotorBlock.FACING, Direction.EAST));
		((CreativeMotorBlockEntity) helper.getBlockEntity(new BlockPos(0, 1, 1))).generatedSpeed.setValue(64);
		BlockPos at = new BlockPos(1, 1, 1);
		helper.setBlock(at, CEBlocks.TRANSMISSION.getDefaultState()
			.setValue(BlockStateProperties.AXIS, Direction.Axis.X));
		helper.setBlock(new BlockPos(2, 1, 1), AllBlocks.SHAFT.getDefaultState()
			.setValue(BlockStateProperties.AXIS, Direction.Axis.X));
		((TransmissionBlockEntity) helper.getBlockEntity(at)).panel.setValue(7);
		boolean[] manualChecked = {false};
		helper.succeedWhen(() -> {
			TransmissionBlockEntity box = helper.getBlockEntity(at);
			if (!manualChecked[0]) {
				var line = TransmissionGearDisplaySource.line(box.gear(), box.isAuto());
				helper.assertTrue(box.gear() == 7 && keyOf(line).equals("display.transmission.gear")
					&& ((TranslatableContents) line.getContents()).getArgs()[0].equals("7"), "It should read gear 7: " + box.gear());
				manualChecked[0] = true;
				box.panel.setValue(Gears.AUTO);
			}
			helper.assertTrue(box.isAuto(), "and know when it is on auto");
		});
	}

	/**
	 * The gear dial reads the gear a redstone signal picked, on the server and in what the client is sent, and goes back to
	 * the hand setting when the signal goes. The setting itself is never overwritten.
	 */
	@GameTest(template = "empty", batch = BATCH)
	public static void theGearDialReadsTheGearARedstoneSignalPicks(GameTestHelper helper) {
		BlockPos at = new BlockPos(1, 1, 1);
		BlockPos source = new BlockPos(1, 2, 1);
		helper.setBlock(at, CEBlocks.TRANSMISSION.getDefaultState()
			.setValue(BlockStateProperties.AXIS, Direction.Axis.X));
		TransmissionBlockEntity box = helper.getBlockEntity(at);
		box.panel.setValue(7);
		helper.assertTrue(box.panel.formatValue().equals("7"), "By hand it reads 7: " + box.panel.formatValue());

		helper.setBlock(source, net.minecraft.world.level.block.Blocks.REDSTONE_BLOCK);
		helper.assertTrue(box.panel.formatValue().equals("A"), "Full strength reads auto: " + box.panel.formatValue());
		helper.assertTrue(box.isAuto(), "and the box is on auto");
		helper.assertTrue(box.panel.getValue() == 7, "and the hand setting is kept: " + box.panel.getValue());
		helper.assertTrue(clientDial(helper, box).equals("A"), "and the client is told: " + clientDial(helper, box));

		// A signal beats an auto panel too, and the goggles and display must not call that gear automatic
		box.panel.setValue(Gears.AUTO);
		helper.assertTrue(box.isAuto(), "Still auto under full strength");

		helper.setBlock(source, net.minecraft.world.level.block.Blocks.AIR);
		box.panel.setValue(7);
		helper.assertTrue(box.panel.formatValue().equals("7"), "With no signal it reads 7 again: " + box.panel.formatValue());
		helper.assertTrue(clientDial(helper, box).equals("7"), "on the client too: " + clientDial(helper, box));
		helper.assertTrue(!box.saveWithFullMetadata(helper.getLevel().registryAccess())
			.contains("SignalSetting"), "The signal is not part of the save");
		helper.succeed();
	}

	/** What the gear dial reads on a copy of the box built from the packet a client would get. */
	private static String clientDial(GameTestHelper helper, TransmissionBlockEntity box) {
		var copy = new TransmissionBlockEntity(box.getType(), box.getBlockPos(), box.getBlockState());
		copy.handleUpdateTag(box.getUpdateTag(helper.getLevel()
			.registryAccess()), helper.getLevel()
				.registryAccess());
		return copy.panel.formatValue();
	}

	/** A new box is on the normal profile, and the profile dial never disturbs the gear dial or its own save. */
	@GameTest(template = "empty", batch = BATCH)
	public static void aGearboxDefaultsToTheNormalShiftProfileAndSavesItApartFromTheGear(GameTestHelper helper) {
		BlockPos at = new BlockPos(1, 1, 1);
		helper.setBlock(at, CEBlocks.TRANSMISSION.getDefaultState()
			.setValue(BlockStateProperties.AXIS, Direction.Axis.X));
		TransmissionBlockEntity box = helper.getBlockEntity(at);
		helper.assertTrue(box.profile() == com.createengines.content.transmission.AutoGearbox.Profile.NORMAL,
			"A new transmission is on the normal profile");
		box.panel.setValue(9);
		box.shiftProfile.setValue(2);
		var tag = box.saveWithFullMetadata(helper.getLevel().registryAccess());
		helper.assertTrue(tag.getInt("ShiftProfile") == 2, "The profile saves under its own key");
		helper.assertTrue(tag.getInt("ScrollValue") == 9, "and the shared key still holds the gear: " + tag.getInt("ScrollValue"));
		box.panel.setValue(3);
		box.shiftProfile.setValue(0);
		box.loadWithComponents(tag, helper.getLevel().registryAccess());
		helper.assertTrue(box.panel.getValue() == 9, "The gear reads back as 9: " + box.panel.getValue());
		helper.assertTrue(box.shiftProfile.getValue() == 2, "The profile reads back as 2: " + box.shiftProfile.getValue());
		// A box saved before profiles existed has no key and reads as normal; a damaged value is held in range
		var old = tag.copy();
		old.remove("ShiftProfile");
		box.loadWithComponents(old, helper.getLevel().registryAccess());
		helper.assertTrue(box.profile() == com.createengines.content.transmission.AutoGearbox.Profile.NORMAL,
			"An old save reads as normal");
		var wild = tag.copy();
		wild.putInt("ShiftProfile", 99);
		box.loadWithComponents(wild, helper.getLevel().registryAccess());
		helper.assertTrue(box.shiftProfile.getValue() == 2, "A damaged profile is held to the last one: " + box.shiftProfile.getValue());
		helper.succeed();
	}

	@GameTest(template = "empty", batch = BATCH)
	public static void theFaultLineIsTheCauseTheHealthLineBlamesOrNoEngine(GameTestHelper helper) {
		helper.assertTrue(EngineReadout.faultKey(null)
			.equals("display.engine.none"), "No engine reads no engine");
		helper.assertTrue(EngineReadout.faultKey(EngineSummary.EMPTY)
			.equals("display.engine.fault.none"), "A healthy summary reads no fault");
		var incomplete = new EngineSummary(EngineState.INCOMPLETE, 0, 0, 0, 0, 0, 0, 0, 0, 0, false, new float[0]);
		helper.assertTrue(EngineReadout.faultKey(incomplete)
			.equals("display.engine.none"), "An unfinished build is no engine");
		var noSpark = new EngineSummary(EngineState.STALLED, 0, 0, 0, 0, 0, 0, 1, 1, 1, false, new float[0], 1, 0, 0, 1, 0, 0,
			false, false, 0, 0, Diagnosis.Health.FAILING, Diagnosis.Cause.NO_SPARK, 0, 0, java.util.List.of());
		helper.assertTrue(EngineReadout.faultKey(noSpark)
			.equals("display.engine.fault.no_spark"), "A named cause is shown by its short name");
		helper.succeed();
	}

	/** A spark engine with no battery is told it has no spark, by the same cause the goggles give. */
	@GameTest(template = "empty", timeoutTicks = 400, batch = BATCH)
	public static void aSparkEngineWithNoBatteryReadsNoSparkOnItsFaultLine(GameTestHelper helper) {
		TestEngine.buildSpark(helper.getLevel(), helper.absolutePos(BlockPos.ZERO), false);
		helper.succeedWhen(() -> {
			CrankshaftBlockEntity crank = helper.getBlockEntity(TestEngine.PRIMARY);
			EngineSummary summary = crank.engineSummary();
			helper.assertTrue(summary != null && summary.cause() == Diagnosis.Cause.NO_SPARK,
				"The engine should blame the missing spark: " + (summary == null ? null : summary.cause()));
			helper.assertTrue(EngineReadout.faultKey(summary)
				.equals("display.engine.fault.no_spark"), "and the fault line should say so");
		});
	}

	/**
	 * The recorder replay: a dial on the box sets a moment on the tape, held where it was set while the tape runs on,
	 * with what the engine was doing and what was wrong with it then. Back on live it shows nothing.
	 */
	@GameTest(template = "empty", timeoutTicks = 1300, batch = BATCH)
	public static void aFlightRecorderReplaysAnEarlierMomentAndHoldsIt(GameTestHelper helper) {
		TestEngine.build(helper.getLevel(), helper.absolutePos(BlockPos.ZERO), false);
		BlockPos box = new BlockPos(2, 2, 1);
		helper.setBlock(box, CEBlocks.BLACK_BOX.getDefaultState());
		helper.runAtTickTime(300, () -> helper.setBlock(TestEngine.STARTER_POWER, Blocks.AIR));
		FlightRecorder.Moment[] first = {null};
		helper.runAtTickTime(700, () -> {
			BlackBoxBlockEntity be = helper.getBlockEntity(box);
			helper.assertTrue(be.moment() == null && be.replaySeconds() == 0, "Live it shows no replay");
			be.replay.setValue(2);
			helper.assertTrue(be.replaySeconds() == 2 * FlightRecorder.REPLAY_STEP, "Two notches is ten seconds back");
			first[0] = be.moment();
			helper.assertTrue(first[0] != null && first[0].secondsAgo() == 10, "The replay is ten seconds back: " + first[0]);
			helper.assertTrue(first[0].state() == EngineState.STALLED || first[0].state() == EngineState.COASTING,
				"An unfuelled engine was spinning down or stalled by then: " + first[0]);
			helper.assertTrue(first[0].fault() == Diagnosis.Cause.NO_FUEL,
				"and the engine was blaming its fuel: " + first[0].fault());
		});
		helper.runAtTickTime(900, () -> {
			BlackBoxBlockEntity be = helper.getBlockEntity(box);
			FlightRecorder.Moment later = be.moment();
			helper.assertTrue(later != null && later.secondsAgo() > first[0].secondsAgo(),
				"The moment is held while the tape runs on, so it is further back now: " + later);
			helper.assertTrue(later.state() == first[0].state() && later.rpm() == first[0].rpm() && later.fault() == first[0].fault(),
				"and it is the same moment: " + later + " against " + first[0]);
			// It survives being saved with the tape, the faults included
			var tag = be.saveWithFullMetadata(helper.getLevel()
				.registryAccess());
			be.loadWithComponents(tag, helper.getLevel()
				.registryAccess());
			helper.assertTrue(be.recorder()
				.momentAt(be.recorder()
					.sampleBack(10))
				.fault() == Diagnosis.Cause.NO_FUEL, "The faults come back from a saved tape");
			be.replay.setValue(0);
			helper.assertTrue(be.moment() == null, "Back on live there is nothing to replay");
			helper.succeed();
		});
	}
}
