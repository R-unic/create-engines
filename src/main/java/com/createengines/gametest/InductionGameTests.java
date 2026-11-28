package com.createengines.gametest;

import static com.createengines.gametest.EngineRigs.*;

import com.createengines.CreateEngines;
import com.createengines.content.airflow.ManifoldBlock;
import com.createengines.content.airflow.PortOpeningBlock;
import com.createengines.content.intercooler.IntercoolerBlock;
import com.createengines.content.supercharger.SuperchargerBlock;
import com.createengines.content.supercharger.SuperchargerBlockEntity;
import com.createengines.content.turbo.TurbochargerBlock;
import com.createengines.content.turbo.TurbochargerBlockEntity;
import com.createengines.engine.TurboStatus;
import com.createengines.content.airflow.PortKind;
import com.createengines.engine.EngineState;
import com.createengines.engine.EngineSummary;
import com.createengines.engine.topology.EngineLayout;
import com.createengines.engine.topology.EngineScanner;
import com.createengines.registry.CEBlocks;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlock;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Turbochargers, superchargers, intercooler and anti-lag. Test engines as in {@link EngineGameTests}. */
@GameTestHolder(CreateEngines.MOD_ID)
@PrefixGameTestTemplate(false)
public class InductionGameTests {

	/**
	 * The exhaust blows straight into a turbo's turbine; its compressor draws through an air intake and feeds the head
	 * along an intake pipe. A running engine spools it and is boosted, and a redstone signal dumps the boost.
	 */
	@GameTest(template = "empty", timeoutTicks = 1700)
	public static void aTurbochargerBoostsAnEngineFromItsOwnExhaust(GameTestHelper helper) {
		buildSingleCylinder(helper, true);
		buildTurbo(helper);
		BlockPos turbo = EXHAUST;
		helper.runAtTickTime(300, () -> helper.setBlock(STARTER_POWER, Blocks.AIR));
		helper.runAtTickTime(900, () -> {
			EngineSummary summary = summary(helper);
			helper.assertTrue(summary.state() == EngineState.RUNNING, "Engine should run through the turbo: " + summary + ", cylinder " + cylinder(helper));
			TurbochargerBlockEntity be = helper.getBlockEntity(turbo);
			TurboStatus status = be.status();
			helper.assertTrue(status != null, "The turbo should belong to the engine");
			helper.assertTrue(status.compressor() && status.turbine(),
				"Both of its sides should be on the engine: " + status);
			helper.assertTrue(status.boost() > 0.08f, "A firing engine should spool the turbo: " + status);
			helper.assertTrue(status.backPressure() > 1.03f, "The turbine should hold the exhaust up: " + status);
			helper.assertTrue(status.outlets()
				.equals(java.util.List.of(helper.absolutePos(turbo.south()))),
				"The turbo should know where its exhaust comes out: " + status);
			// A redstone signal next to the turbo dumps its boost
			helper.setBlock(turbo.below(), Blocks.REDSTONE_BLOCK);
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(helper.getTick() > 1100, "Boost not dumped yet");
			TurbochargerBlockEntity be = helper.getBlockEntity(turbo);
			helper.assertTrue(be.status() != null && be.status().boost() < 0.05f,
				"Dumping should blow the boost off: " + be.status());
		});
	}

	/** A turbo with nothing leading to it from an engine belongs to none. */
	@GameTest(template = "empty")
	public static void aLoneTurbochargerBelongsToNoEngine(GameTestHelper helper) {
		BlockPos pos = new BlockPos(1, 1, 1);
		helper.setBlock(pos, CEBlocks.TURBOCHARGER.getDefaultState()
			.setValue(TurbochargerBlock.FACING, Direction.UP));
		TurbochargerBlockEntity be = helper.getBlockEntity(pos);
		helper.assertTrue(be.status() == null, "Nothing should have told it about an engine");
		helper.succeed();
	}

	/** A turbo takes intake pipes at its compressor face and sides, exhaust pipes at its turbine face and sides. */
	@GameTest(template = "empty")
	public static void aTurbochargerTakesTheRightPipesOnTheRightFaces(GameTestHelper helper) {
		BlockPos pos = new BlockPos(1, 1, 1);
		helper.setBlock(pos, CEBlocks.TURBOCHARGER.getDefaultState()
			.setValue(TurbochargerBlock.FACING, Direction.UP));
		BlockState state = helper.getBlockState(pos);
		TurbochargerBlock block = CEBlocks.TURBOCHARGER.get();
		helper.assertTrue(block.acceptsPipe(state, Direction.UP, PortKind.INTAKE), "Compressor face takes intake");
		helper.assertFalse(block.acceptsPipe(state, Direction.UP, PortKind.EXHAUST), "Compressor face refuses exhaust");
		helper.assertTrue(block.acceptsPipe(state, Direction.DOWN, PortKind.EXHAUST), "Turbine face takes exhaust");
		helper.assertFalse(block.acceptsPipe(state, Direction.DOWN, PortKind.INTAKE), "Turbine face refuses intake");
		for (Direction side : Direction.Plane.HORIZONTAL)
			helper.assertTrue(block.acceptsPipe(state, side, PortKind.INTAKE) && block.acceptsPipe(state, side, PortKind.EXHAUST),
				"Side faces take either kind");
		// A pipe placed beside it opens towards it, only if it is the right kind for that face
		BlockPos above = pos.above();
		helper.setBlock(above, placePipe(helper, CEBlocks.EXHAUST_MANIFOLD.get(), above));
		helper.assertFalse(helper.getBlockState(above)
			.getValue(ManifoldBlock.CONNECTIONS.get(Direction.DOWN)), "An exhaust pipe must not join the compressor face");
		helper.setBlock(above, Blocks.AIR);
		helper.setBlock(above, placePipe(helper, CEBlocks.INTAKE_MANIFOLD.get(), above));
		helper.assertTrue(helper.getBlockState(above)
			.getValue(ManifoldBlock.CONNECTIONS.get(Direction.DOWN)), "An intake pipe should join the compressor face");
		helper.succeed();
	}

	/** Turbo with its turbine bolted to the head's exhaust and its compressor feeding the head's intake by a pipe. */
	private static void buildTurbo(GameTestHelper helper) {
		BlockPos turbo = EXHAUST;
		helper.setBlock(turbo, CEBlocks.TURBOCHARGER.getDefaultState()
			.setValue(TurbochargerBlock.FACING, Direction.WEST));
		// Air in at the top, exhaust out of the side; the turbo's side faces take either kind
		helper.setBlock(turbo.above(), CEBlocks.AIR_INTAKE.getDefaultState()
			.setValue(PortOpeningBlock.FACING, Direction.UP));
		helper.setBlock(turbo.south(), CEBlocks.EXHAUST_OUTLET.getDefaultState()
			.setValue(PortOpeningBlock.FACING, Direction.SOUTH));
		BlockPos headPipe = TestEngine.AIR_INTAKE;
		helper.setBlock(headPipe, Blocks.AIR);
		helper.setBlock(headPipe, placePipe(helper, CEBlocks.INTAKE_MANIFOLD.get(), headPipe));
		BlockPos turboPipe = turbo.north();
		helper.setBlock(turboPipe, placePipe(helper, CEBlocks.INTAKE_MANIFOLD.get(), turboPipe));
	}

	/**
	 * A second turbo whose turbine sits on the first's south side, so both are on the one exhaust run, and whose
	 * compressor joins the first's by intake pipes above them, so both are on the one intake run.
	 */
	private static void buildTwinTurbo(GameTestHelper helper) {
		BlockPos first = EXHAUST;
		BlockPos second = first.south();
		helper.setBlock(first, CEBlocks.TURBOCHARGER.getDefaultState()
			.setValue(TurbochargerBlock.FACING, Direction.WEST));
		helper.setBlock(second, CEBlocks.TURBOCHARGER.getDefaultState()
			.setValue(TurbochargerBlock.FACING, Direction.SOUTH));
		helper.setBlock(second.south(), CEBlocks.AIR_INTAKE.getDefaultState()
			.setValue(PortOpeningBlock.FACING, Direction.SOUTH));
		helper.setBlock(second.below(), CEBlocks.EXHAUST_OUTLET.getDefaultState()
			.setValue(PortOpeningBlock.FACING, Direction.DOWN));
		BlockPos headPipe = TestEngine.AIR_INTAKE;
		helper.setBlock(headPipe, Blocks.AIR);
		for (BlockPos pipe : new BlockPos[] {headPipe, first.north(), second.above(), first.above()})
			helper.setBlock(pipe, placePipe(helper, CEBlocks.INTAKE_MANIFOLD.get(), pipe));
	}

	/**
	 * Two turbochargers on the one exhaust run and the one intake run. Both are spun by the shared exhaust, and the
	 * cylinder breathes through both compressors in turn, so it is boosted by more than either makes alone.
	 */
	@GameTest(template = "empty", timeoutTicks = 1700)
	public static void twoTurbochargersOnOneIntakeStackTheirBoost(GameTestHelper helper) {
		buildSingleCylinder(helper, true);
		buildTwinTurbo(helper);
		helper.runAtTickTime(300, () -> helper.setBlock(STARTER_POWER, Blocks.AIR));
		helper.runAtTickTime(1100, () -> {
			EngineSummary summary = summary(helper);
			helper.assertTrue(summary.state() == EngineState.RUNNING,
				"Engine should run through both turbos: " + summary + ", cylinder " + cylinder(helper));
			TurbochargerBlockEntity first = helper.getBlockEntity(EXHAUST);
			TurbochargerBlockEntity second = helper.getBlockEntity(EXHAUST.south());
			helper.assertTrue(first.status() != null && second.status() != null, "Both turbos should belong to the engine");
			for (TurbochargerBlockEntity turbo : new TurbochargerBlockEntity[] {first, second}) {
				TurboStatus status = turbo.status();
				helper.assertTrue(status.compressor() && status.turbine(), "Both sides on the engine: " + status);
				helper.assertTrue(status.boost() > 0.04f, "A shared exhaust should still spool each turbo: " + status);
			}
			float strongest = Math.max(first.status().boost(), second.status().boost());
			helper.assertTrue(first.status().charge() > 1.5f * strongest,
				"The cylinder should breathe both compressors in turn: " + first.status() + " " + second.status());
			helper.succeed();
		});
	}

	/** A supercharger takes intake pipes on the faces round its shaft and nothing on the shaft's ends. */
	@GameTest(template = "empty")
	public static void aSuperchargerTakesIntakePipesAroundItsShaft(GameTestHelper helper) {
		BlockPos pos = new BlockPos(1, 1, 1);
		helper.setBlock(pos, CEBlocks.SUPERCHARGER.getDefaultState()
			.setValue(BlockStateProperties.AXIS, Axis.Y));
		BlockState state = helper.getBlockState(pos);
		SuperchargerBlock block = CEBlocks.SUPERCHARGER.get();
		for (Direction side : Direction.Plane.HORIZONTAL) {
			helper.assertTrue(block.acceptsPipe(state, side, PortKind.INTAKE), "Round the shaft takes intake");
			helper.assertFalse(block.acceptsPipe(state, side, PortKind.EXHAUST), "A blower has no exhaust side");
		}
		helper.assertFalse(block.acceptsPipe(state, Direction.UP, PortKind.INTAKE), "The shaft's end takes no pipe");
		helper.assertFalse(block.acceptsPipe(state, Direction.DOWN, PortKind.INTAKE), "The shaft's end takes no pipe");
		helper.succeed();
	}

	/**
	 * A creative motor spins a supercharger bolted to the head's east face, with an air intake on its far face. The
	 * blower boosts at once, tells the engine it feeds its cylinder, and puts stress on its own shaft.
	 */
	@GameTest(template = "empty", timeoutTicks = 1700)
	public static void aSuperchargerBoostsTheEngineItFeeds(GameTestHelper helper) {
		buildSingleCylinder(helper, true);
		BlockPos blower = HEAD.east();
		helper.setBlock(TestEngine.AIR_INTAKE, Blocks.AIR);
		helper.setBlock(blower.east(), CEBlocks.AIR_INTAKE.getDefaultState()
			.setValue(PortOpeningBlock.FACING, Direction.EAST));
		helper.setBlock(blower, CEBlocks.SUPERCHARGER.getDefaultState()
			.setValue(BlockStateProperties.AXIS, Axis.Y));
		helper.setBlock(blower.below(), AllBlocks.CREATIVE_MOTOR.getDefaultState()
			.setValue(CreativeMotorBlock.FACING, Direction.UP));
		CreativeMotorBlockEntity motor = helper.getBlockEntity(blower.below());
		motor.generatedSpeed.setValue(128);
		helper.runAtTickTime(300, () -> helper.setBlock(STARTER_POWER, Blocks.AIR));
		helper.runAtTickTime(900, () -> {
			EngineSummary summary = summary(helper);
			helper.assertTrue(summary.state() == EngineState.RUNNING,
				"Engine should run through the supercharger: " + summary + ", cylinder " + cylinder(helper));
			SuperchargerBlockEntity be = helper.getBlockEntity(blower);
			helper.assertTrue(be.onEngine(), "The blower should feed the engine's cylinder");
			helper.assertTrue(Math.abs(be.shaftRpm() - 128) < 1, "Its shaft should turn at the motor's speed: " + be.shaftRpm());
			helper.assertTrue(be.boost() > 0.4f, "128 RPM should make the boost its bypass allows: " + be.boost());
			helper.assertTrue(be.calculateStressApplied() > 0, "Turning the blower should cost stress");
			helper.succeed();
		});
	}

	/**
	 * The same rig with a variable-drive supercharger on a shaft turning at half the design speed: its drive overspeeds
	 * the rotors, so it makes the boost its bypass allows where a plain blower at that speed would fall short of it.
	 */
	@GameTest(template = "empty", timeoutTicks = 1700)
	public static void aVariableDriveSuperchargerBoostsOnASlowShaft(GameTestHelper helper) {
		buildSingleCylinder(helper, true);
		BlockPos blower = HEAD.east();
		helper.setBlock(TestEngine.AIR_INTAKE, Blocks.AIR);
		helper.setBlock(blower.east(), CEBlocks.AIR_INTAKE.getDefaultState()
			.setValue(PortOpeningBlock.FACING, Direction.EAST));
		helper.setBlock(blower, CEBlocks.VARIABLE_SUPERCHARGER.getDefaultState()
			.setValue(BlockStateProperties.AXIS, Axis.Y));
		helper.setBlock(blower.below(), AllBlocks.CREATIVE_MOTOR.getDefaultState()
			.setValue(CreativeMotorBlock.FACING, Direction.UP));
		CreativeMotorBlockEntity motor = helper.getBlockEntity(blower.below());
		motor.generatedSpeed.setValue(64);
		helper.runAtTickTime(300, () -> helper.setBlock(STARTER_POWER, Blocks.AIR));
		helper.runAtTickTime(900, () -> {
			EngineSummary summary = summary(helper);
			helper.assertTrue(summary.state() == EngineState.RUNNING,
				"Engine should run through the variable-drive supercharger: " + summary + ", cylinder " + cylinder(helper));
			SuperchargerBlockEntity be = helper.getBlockEntity(blower);
			helper.assertTrue(be.variableDrive(), "It should be a variable-drive blower");
			helper.assertTrue(be.onEngine(), "The blower should feed the engine's cylinder");
			helper.assertTrue(Math.abs(be.shaftRpm() - 64) < 1, "Its shaft should turn at the motor's speed: " + be.shaftRpm());
			helper.assertTrue(be.boost() > 0.45f, "Half the design speed should still make the boost its bypass allows: " + be.boost());
			helper.assertTrue(be.calculateStressApplied() > 0, "Turning the blower should cost stress");
			helper.succeed();
		});
	}

	/** An intercooler in the intake run is a junction like a pipe: air goes in at one end and out of the other. */
	@GameTest(template = "empty", timeoutTicks = 1700)
	public static void anIntercoolerInTheIntakeRunJoinsTheEngine(GameTestHelper helper) {
		buildSingleCylinder(helper, true);
		BlockPos cooler = HEAD.east();
		helper.setBlock(TestEngine.AIR_INTAKE, Blocks.AIR);
		helper.setBlock(cooler.east(), CEBlocks.AIR_INTAKE.getDefaultState()
			.setValue(PortOpeningBlock.FACING, Direction.EAST));
		helper.setBlock(cooler, CEBlocks.INTERCOOLER.getDefaultState()
			.setValue(IntercoolerBlock.AXIS, Axis.X));
		helper.runAtTickTime(300, () -> helper.setBlock(STARTER_POWER, Blocks.AIR));
		helper.runAtTickTime(900, () -> {
			EngineSummary summary = summary(helper);
			helper.assertTrue(summary.state() == EngineState.RUNNING,
				"Engine should breathe through the intercooler: " + summary + ", cylinder " + cylinder(helper));
			EngineLayout layout = EngineScanner.scan(helper.getLevel(), helper.absolutePos(PRIMARY));
			helper.assertTrue(layout != null && layout.intercoolers().size() == 1,
				"The scanner should find the intercooler in the intake run: " + layout);
			helper.succeed();
		});
	}

	/** The boost limit and the anti-lag setting are both scroll values: saving must keep them apart. */
	@GameTest(template = "empty")
	public static void aTurbochargerSavesItsBoostLimitAndAntiLagSeparately(GameTestHelper helper) {
		BlockPos pos = new BlockPos(1, 1, 1);
		helper.setBlock(pos, CEBlocks.TURBOCHARGER.getDefaultState()
			.setValue(TurbochargerBlock.FACING, Direction.UP));
		TurbochargerBlockEntity turbo = helper.getBlockEntity(pos);
		turbo.limit.setValue(9);
		turbo.antiLag.setValue(2);
		net.minecraft.nbt.CompoundTag saved = turbo.saveWithoutMetadata(helper.getLevel()
			.registryAccess());
		turbo.limit.setValue(3);
		turbo.antiLag.setValue(0);
		turbo.handleUpdateTag(saved, helper.getLevel()
			.registryAccess());
		helper.assertTrue(turbo.limit.getValue() == 9, "Boost limit: " + turbo.limit.getValue());
		helper.assertTrue(turbo.antiLag.getValue() == 2, "Anti-lag: " + turbo.antiLag.getValue());
		helper.succeed();
	}

	/** Create sends a player's setting to the value box with the matching network id: the two must not share one. */
	@GameTest(template = "empty")
	public static void aTurbochargersTwoValuesHaveTheirOwnNetworkIds(GameTestHelper helper) {
		BlockPos plain = new BlockPos(1, 1, 1);
		BlockPos variable = new BlockPos(3, 1, 1);
		helper.setBlock(plain, CEBlocks.TURBOCHARGER.getDefaultState()
			.setValue(TurbochargerBlock.FACING, Direction.UP));
		helper.setBlock(variable, CEBlocks.VARIABLE_TURBOCHARGER.getDefaultState()
			.setValue(TurbochargerBlock.FACING, Direction.UP));
		for (BlockPos pos : new BlockPos[] {plain, variable}) {
			TurbochargerBlockEntity turbo = helper.getBlockEntity(pos);
			helper.assertTrue(turbo.limit.netId() != turbo.antiLag.netId(),
				"The boost limit and anti-lag share network id " + turbo.limit.netId() + " at " + pos);
			helper.assertTrue(!turbo.limit.getClipboardKey()
				.equals(turbo.antiLag.getClipboardKey()), "And the same clipboard key at " + pos);
		}
		helper.succeed();
	}

	/**
	 * Create sends a player's setting to the first value box whose network id matches, so two dials on a block that share
	 * one make the second dial change the first. It has happened on the turbocharger, the throttle and the transmission.
	 */
	@GameTest(template = "empty")
	public static void everyDialOnABlockHasItsOwnNetworkId(GameTestHelper helper) {
		BlockPos pos = new BlockPos(1, 1, 1);
		for (var block : java.util.List.of(CEBlocks.TRANSMISSION.getDefaultState(), CEBlocks.THROTTLE.getDefaultState(),
			CEBlocks.SUMP.getDefaultState())) {
			helper.setBlock(pos, block);
			com.simibubi.create.foundation.blockEntity.SmartBlockEntity be = helper.getBlockEntity(pos);
			java.util.Set<Integer> ids = new java.util.HashSet<>();
			java.util.Set<String> keys = new java.util.HashSet<>();
			int dials = 0;
			for (var behaviour : be.getAllBehaviours())
				if (behaviour instanceof com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour dial) {
					dials++;
					ids.add(dial.netId());
					keys.add(dial.getClipboardKey());
				}
			helper.assertTrue(dials >= 2, block.getBlock() + " has " + dials + " dials");
			helper.assertTrue(ids.size() == dials, block.getBlock() + " has dials sharing a network id: " + ids);
			helper.assertTrue(keys.size() == dials, block.getBlock() + " has dials sharing a clipboard key: " + keys);
		}
		helper.succeed();
	}

	/** A variable-geometry turbo runs the engine, with its vanes closed on a thin exhaust. */
	@GameTest(template = "empty", timeoutTicks = 1700)
	public static void aVariableTurbochargerClosesItsVanesOnAThinExhaust(GameTestHelper helper) {
		buildSingleCylinder(helper, true);
		buildTurbo(helper);
		helper.setBlock(EXHAUST, CEBlocks.VARIABLE_TURBOCHARGER.getDefaultState()
			.setValue(TurbochargerBlock.FACING, Direction.WEST));
		helper.runAtTickTime(300, () -> helper.setBlock(STARTER_POWER, Blocks.AIR));
		helper.runAtTickTime(1100, () -> {
			EngineSummary summary = summary(helper);
			helper.assertTrue(summary.state() == EngineState.RUNNING, "Engine should run through the turbo: " + summary);
			TurbochargerBlockEntity be = helper.getBlockEntity(EXHAUST);
			helper.assertTrue(be.status() != null && be.status().vanes() > 0.15f, // 2.0 port curve: one opening per head is 90 percent breathing, vanes 0.177 (was above 0.2); see v2.0.0 Exceptions

				"One cylinder exhaust is thin, so the vanes close: " + be.status());
			helper.succeed();
		});
	}

	/** With anti-lag set, a turbo burns fuel in the exhaust when the engine rack is closed. */
	@GameTest(template = "empty", timeoutTicks = 1700)
	public static void antiLagBurnsFuelOnceTheRackIsClosed(GameTestHelper helper) {
		buildSingleCylinder(helper, true);
		buildTurbo(helper);
		TurbochargerBlockEntity turbo = helper.getBlockEntity(EXHAUST);
		turbo.antiLag.setValue(3);
		helper.runAtTickTime(300, () -> helper.setBlock(STARTER_POWER, Blocks.AIR));
		helper.runAtTickTime(900, () -> {
			helper.assertTrue(summary(helper).state() == EngineState.RUNNING, "Running first: " + summary(helper));
			helper.assertTrue(turbo.status() != null && !turbo.status().antiLag(), "On the throttle it does nothing");
			// Redstone on the camshaft closes the fuel rack
			helper.setBlock(LOBE.above(), Blocks.REDSTONE_BLOCK);
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(helper.getTick() > 920, "Rack not shut yet");
			helper.assertTrue(turbo.status() != null && turbo.status().antiLag(),
				"The turbo should be burning fuel to stay spooled: " + turbo.status());
		});
	}
}
