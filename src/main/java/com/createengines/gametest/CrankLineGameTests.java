package com.createengines.gametest;

import static com.createengines.gametest.EngineRigs.*;

import com.createengines.CreateEngines;
import com.createengines.config.CEConfig;
import com.createengines.content.airflow.ManifoldBlock;
import com.createengines.content.airflow.PortOpeningBlock;
import com.createengines.content.EnginePoses;
import com.createengines.content.crankshaft.CrankshaftBlockEntity;
import com.createengines.content.cylinder.CylinderBlock;
import com.createengines.content.cylinder.CylinderBlockEntity;
import com.createengines.content.head.CylinderHeadBlockEntity;
import com.createengines.content.starter.StarterBlockEntity;
import com.createengines.content.tool.TimingToolItem;
import com.createengines.engine.CrankGeometry;
import com.createengines.engine.EngineState;
import com.createengines.engine.EngineSummary;
import com.createengines.engine.topology.EngineLayout;
import com.createengines.engine.topology.EngineScanner;
import com.createengines.engine.topology.CylinderFault;
import com.createengines.engine.topology.FiringPlanner;
import com.createengines.content.alternator.AlternatorBlockEntity;
import com.createengines.registry.CEBlocks;
import com.createengines.registry.CEFluids;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityVisual;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Manifolds, broken crank and cam lines, and coupled cranks. Test engines as in {@link EngineGameTests}. */
@GameTestHolder(CreateEngines.MOD_ID)
@PrefixGameTestTemplate(false)
public class CrankLineGameTests {

	/** An exhaust manifold joined to a head, with an outlet on the end of it, carries the exhaust and the engine runs. */
	@GameTest(template = "empty", timeoutTicks = 900)
	public static void anExhaustManifoldCarriesTheExhaustFromAHead(GameTestHelper helper) {
		buildSingleCylinder(helper, true);
		BlockPos pipe = EXHAUST;
		helper.setBlock(pipe, Blocks.AIR);
		// Placed as a player would, against the head, then an outlet placed against the pipe
		BlockState placed = placePipe(helper, CEBlocks.EXHAUST_MANIFOLD.get(), pipe);
		helper.assertTrue(placed.getValue(ManifoldBlock.CONNECTIONS.get(Direction.EAST)),
			"An exhaust pipe placed beside a head should open towards it");
		helper.setBlock(pipe, placed);
		helper.setBlock(pipe.above(), CEBlocks.EXHAUST_OUTLET.getDefaultState()
			.setValue(PortOpeningBlock.FACING, Direction.UP));
		helper.runAtTickTime(300, () -> helper.setBlock(STARTER_POWER, Blocks.AIR));
		helper.succeedWhen(() -> {
			helper.assertTrue(helper.getTick() > 600, "Engine not running yet");
			helper.assertFalse(CylinderFault.NO_EXHAUST.in(cylinder(helper).faults())
				|| CylinderFault.EXHAUST_BLOCKED.in(cylinder(helper).faults()),
				"The head should exhaust through the pipe: " + cylinder(helper));
			helper.assertTrue(summary(helper).state() == EngineState.RUNNING, "Engine should run: " + summary(helper));
		});
	}

	/** Two runs of the same kind of pipe side by side join, unless the wrench has cut them apart. */
	@GameTest(template = "empty")
	public static void pipesJoinTheirNeighboursAndLetGo(GameTestHelper helper) {
		BlockPos first = new BlockPos(1, 1, 1);
		BlockPos second = first.east();
		helper.setBlock(first, placePipe(helper, CEBlocks.EXHAUST_MANIFOLD.get(), first));
		helper.setBlock(second, placePipe(helper, CEBlocks.EXHAUST_MANIFOLD.get(), second));
		helper.assertTrue(helper.getBlockState(first)
			.getValue(ManifoldBlock.CONNECTIONS.get(Direction.EAST)), "The earlier pipe should open towards the new one");
		helper.assertTrue(helper.getBlockState(second)
			.getValue(ManifoldBlock.CONNECTIONS.get(Direction.WEST)), "The new pipe should open towards the old one");

		BlockPos other = first.south();
		helper.setBlock(other, placePipe(helper, CEBlocks.INTAKE_MANIFOLD.get(), other));
		helper.assertFalse(helper.getBlockState(first)
			.getValue(ManifoldBlock.CONNECTIONS.get(Direction.SOUTH)), "Intake and exhaust pipes must not join");

		// Remove the neighbour: the open end closes behind it
		helper.setBlock(second, Blocks.AIR);
		helper.assertFalse(helper.getBlockState(first)
			.getValue(ManifoldBlock.CONNECTIONS.get(Direction.EAST)), "A pipe should close an end that lost its neighbour");
		helper.succeed();
	}

	/**
	 * A crankshaft is one unbroken line. With a plain Shaft in the middle of it the blocks either side are not one
	 * crankshaft, however the shaft carries their rotation, so the engine reports itself broken until journals replace it.
	 */
	@GameTest(template = "empty", timeoutTicks = 400)
	public static void aShaftInTheCrankLineBreaksTheEngine(GameTestHelper helper) {
		buildSingleCylinder(helper, true);
		BlockPos shaft = new BlockPos(4, 2, 2);
		// The crank is at 1 to 3, then a shaft at 4 and a cogwheel at 5: a crank block in place of the cogwheel is past the gap
		helper.setBlock(new BlockPos(5, 2, 2), alongX(CEBlocks.CRANKSHAFT.getDefaultState()));

		helper.runAtTickTime(2, () -> {
			EngineLayout layout = EngineScanner.scan(helper.getLevel(), helper.absolutePos(PRIMARY));
			helper.assertTrue(layout != null && layout.broken(), "A shaft between crank blocks should break the line");
		});
		helper.runAtTickTime(150, () -> {
			helper.assertTrue(summary(helper).state() == EngineState.BROKEN, "The engine should say it is broken: " + summary(helper));
			helper.setBlock(shaft, alongX(CEBlocks.CRANKSHAFT.getDefaultState()));
		});
		helper.runAtTickTime(160, () -> {
			EngineLayout layout = EngineScanner.scan(helper.getLevel(), helper.absolutePos(PRIMARY));
			helper.assertTrue(layout != null && !layout.broken(), "Journals all the way along are an unbroken crankshaft");
		});
		helper.runAtTickTime(300, () -> {
			helper.assertTrue(summary(helper).state() != EngineState.BROKEN, "Mended, it should no longer be broken: " + summary(helper));
			helper.succeed();
		});
	}

	/** A flywheel is part of the engine's shaft, so an alternator beyond it belongs to the engine and charges from it. */
	@GameTest(template = "empty", timeoutTicks = 400)
	public static void anAlternatorBeyondAFlywheelBelongsToTheEngine(GameTestHelper helper) {
		buildSingleCylinder(helper, true);
		BlockPos alternator = new BlockPos(4, 3, 2);
		helper.setBlock(new BlockPos(4, 2, 2), alongX(AllBlocks.FLYWHEEL.getDefaultState()));
		helper.setBlock(alternator, alongX(CEBlocks.ALTERNATOR.getDefaultState()));

		helper.runAtTickTime(2, () -> {
			EngineLayout layout = EngineScanner.scan(helper.getLevel(), helper.absolutePos(PRIMARY));
			helper.assertTrue(layout != null, "The engine should scan");
			helper.assertTrue(layout.alternators().contains(helper.absolutePos(alternator)),
				"An alternator touching the flywheel should be one of the engine's: " + layout.alternators());
			helper.succeed();
		});
	}

	/** A split engine turns over nothing, so a starter that was cranking it must let go of its input network. */
	@GameTest(template = "empty", timeoutTicks = 500)
	public static void starterLetsGoWhenTheCrankIsBroken(GameTestHelper helper) {
		buildSingleCylinder(helper, false);
		helper.runAtTickTime(200, () -> {
			StarterBlockEntity starter = helper.getBlockEntity(STARTER);
			helper.assertTrue(starter.calculateStressApplied() > 0, "Cranking should load the starter first");
			// A crank block past the shaft at 4 splits the line, with the starter still powered
			helper.setBlock(new BlockPos(5, 2, 2), alongX(CEBlocks.CRANKSHAFT.getDefaultState()));
		});
		helper.runAtTickTime(400, () -> {
			EngineLayout layout = EngineScanner.scan(helper.getLevel(), helper.absolutePos(PRIMARY));
			helper.assertTrue(layout != null && layout.broken(), "The line should be broken");
			StarterBlockEntity starter = helper.getBlockEntity(STARTER);
			helper.assertTrue(starter.calculateStressApplied() == 0,
				"A starter on a broken engine should put no stress on its input: " + starter.calculateStressApplied());
			helper.assertFalse(starter.isTurning(), "A starter on a broken engine is not turning anything over");
			helper.succeed();
		});
	}

	/** A camshaft is one unbroken line too: a plain Shaft in it leaves the lobe cut off, and its cylinder with no cam. */
	@GameTest(template = "empty", timeoutTicks = 200)
	public static void aShaftInTheCamshaftLeavesTheLobeWithNoCam(GameTestHelper helper) {
		buildSingleCylinder(helper, true);
		helper.runAtTickTime(2, () -> {
			EngineLayout whole = EngineScanner.scan(helper.getLevel(), helper.absolutePos(PRIMARY));
			helper.assertTrue(whole != null && whole.cylinders()
				.getFirst()
				.camLine() >= 0 && !CylinderFault.BROKEN_CAM.in(whole.cylinders()
					.getFirst()
					.faults()), "The camshaft starts out whole");

			// The lobe is at x 2 with camshaft blocks from 3 to 5: a shaft at 4 cuts the lobe off from the block at 5
			helper.setBlock(new BlockPos(4, 4, 3), alongX(AllBlocks.SHAFT.getDefaultState()));
			EngineLayout split = EngineScanner.scan(helper.getLevel(), helper.absolutePos(PRIMARY));
			EngineLayout.Cylinder cylinder = split.cylinders()
				.getFirst();
			helper.assertTrue(CylinderFault.BROKEN_CAM.in(cylinder.faults()) && cylinder.camLine() == -1 && cylinder.lobe() == null,
				"A shaft in the camshaft should leave the cylinder with no cam: " + cylinder);

			helper.setBlock(new BlockPos(4, 4, 3), alongX(CEBlocks.CAMSHAFT.getDefaultState()));
			EngineLayout mended = EngineScanner.scan(helper.getLevel(), helper.absolutePos(PRIMARY));
			helper.assertTrue(mended.cylinders()
				.getFirst()
				.camLine() >= 0, "Camshaft blocks all the way along should be one camshaft again");
			helper.succeed();
		});
	}

	/**
	 * Create turns the shafts and small cogs on a line by a phase that alternates between neighbouring lines. A crank's ends
	 * are shaft-shaped, so on every one of those lines it must be drawn at the same angle as a real shaft and a real cogwheel
	 * joined to it (a gap of a fraction of a turn shows as a crank that does not line up), and a throw is drawn that much past
	 * the shaft by its own angle.
	 */
	@GameTest(template = "empty", timeoutTicks = 20)
	public static void aCrankIsDrawnInStepWithTheShaftsAndCogsOnItsLine(GameTestHelper helper) {
		BlockState shaft = alongX(AllBlocks.SHAFT.getDefaultState());
		BlockState cogwheel = alongX(AllBlocks.COGWHEEL.getDefaultState());
		// Lines along X through all four parities of the other two coordinates; the last is a throw set to 30 degrees
		BlockPos[] cranks = { new BlockPos(2, 1, 1), new BlockPos(2, 1, 2), new BlockPos(2, 2, 1), new BlockPos(2, 2, 2) };
		for (BlockPos crank : cranks) {
			helper.setBlock(crank.west(), shaft);
			helper.setBlock(crank, alongX(crank.equals(cranks[3]) ? CEBlocks.CRANK_THROW.getDefaultState()
				: CEBlocks.CRANKSHAFT.getDefaultState()));
			helper.setBlock(crank.east(), cogwheel);
		}
		((CrankshaftBlockEntity) helper.getBlockEntity(cranks[3])).throwAngle.setValue(2);

		helper.runAtTickTime(2, () -> {
			boolean offset = false;
			boolean plain = false;
			for (BlockPos crank : cranks) {
				double throwTurn = crank.equals(cranks[3]) ? 2 * CrankshaftBlockEntity.THROW_ANGLE_STEP : 0;
				double shaftPhase = KineticBlockEntityVisual.rotationOffset(shaft, Axis.X, helper.absolutePos(crank.west()));
				double cogPhase = KineticBlockEntityVisual.rotationOffset(cogwheel, Axis.X, helper.absolutePos(crank.east()));
				double drawn = EnginePoses.crankAngle(helper.getBlockEntity(crank), 0f) - throwTurn;
				helper.assertTrue(Math.abs(Mth.wrapDegrees(drawn - shaftPhase)) < 1e-3,
					"The crank at " + crank + " is drawn at " + drawn + " and the shaft beside it at " + shaftPhase);
				helper.assertTrue(Math.abs(Mth.wrapDegrees(drawn - cogPhase)) < 1e-3,
					"The crank at " + crank + " is drawn at " + drawn + " and the cogwheel beside it at " + cogPhase);
				offset |= shaftPhase != 0;
				plain |= shaftPhase == 0;
			}
			helper.assertTrue(offset && plain, "The four lines should include both phases Create uses");
			helper.succeed();
		});
	}

	/**
	 * A second crank line, three blocks from the first and geared to it by a row of three small cogwheels at the west end
	 * (which turns it the same way), is part of the same engine. It carries a cylinder of its own, worked by a lobe on the
	 * first line's camshaft, and the engine times both cylinders half a cycle apart and runs on them.
	 */
	/**
	 * Two engines on one kinetic network (here two crank lines the coupling is told to leave apart) each see the other's
	 * output as something driving them. They must not hold each other up: with no fuel and no outside drive, once the
	 * starter lets go both must run down and stop, and a restart must not bring the pair back to a steady speed.
	 */
	@GameTest(template = "empty", timeoutTicks = 1500)
	public static void twoEnginesOnOneNetworkDoNotHoldEachOtherUp(GameTestHelper helper) {
		int coupledLines = CEConfig.MAX_COUPLED_LINES.get();
		GameTestConfig.save(CEConfig.MAX_COUPLED_LINES, 1);
		buildSingleCylinder(helper, false);
		TestEngine.addCoupledLine(helper.getLevel(), helper.absolutePos(BlockPos.ZERO));
		BlockPos secondCrank = new BlockPos(1, 2, 4);

		helper.runAtTickTime(250, () -> {
			CrankshaftBlockEntity second = helper.getBlockEntity(secondCrank);
			helper.assertTrue(second.getTheoreticalSpeed() != 0, "The starter should be turning the geared line too");
		});
		helper.runAtTickTime(300, () -> helper.setBlock(STARTER_POWER, Blocks.AIR));
		helper.runAtTickTime(1100, () -> {
			GameTestConfig.save(CEConfig.MAX_COUPLED_LINES, coupledLines);
			CrankshaftBlockEntity first = helper.getBlockEntity(PRIMARY);
			CrankshaftBlockEntity second = helper.getBlockEntity(secondCrank);
			helper.assertTrue(first.getGeneratedSpeed() == 0 && second.getGeneratedSpeed() == 0,
				"Neither engine should still be publishing a speed: " + first.getGeneratedSpeed() + ", " + second.getGeneratedSpeed());
			helper.assertTrue(first.getTheoreticalSpeed() == 0 && second.getTheoreticalSpeed() == 0,
				"The pair should have run down: " + first.getTheoreticalSpeed() + ", " + second.getTheoreticalSpeed());
			helper.succeed();
		});
	}

	/**
	 * A coupled engine whose two crank lines are geared together at both ends, so the gearing is a closed ring, run on fuel
	 * and then starved. A ring of gearing is something Create can leave turning with nothing driving it; the engine must
	 * still come to rest.
	 */
	@GameTest(template = "empty", timeoutTicks = 2600)
	public static void aCoupledEngineGearedInARingStopsWhenItStalls(GameTestHelper helper) {
		buildSingleCylinder(helper, true);
		TestEngine.addCoupledLine(helper.getLevel(), helper.absolutePos(BlockPos.ZERO));
		BlockPos secondCrank = new BlockPos(1, 2, 4);
		helper.setBlock(new BlockPos(4, 2, 4), alongX(AllBlocks.SHAFT.getDefaultState()));
		helper.setBlock(new BlockPos(5, 2, 4), alongX(AllBlocks.COGWHEEL.getDefaultState()));
		helper.setBlock(new BlockPos(5, 2, 3), alongX(AllBlocks.COGWHEEL.getDefaultState()));
		CylinderHeadBlockEntity head2 = helper.getBlockEntity(TestEngine.HEAD_2);
		head2.oilTank()
			.fill(new FluidStack(CEFluids.ENGINE_OIL.get()
				.getSource(), 250), FluidAction.EXECUTE);
		head2.tank()
			.fill(new FluidStack(CEFluids.DIESEL.get()
				.getSource(), 250), FluidAction.EXECUTE);
		helper.runAtTickTime(300, () -> helper.setBlock(STARTER_POWER, Blocks.AIR));
		helper.runAtTickTime(900, () -> {
			for (BlockPos head : new BlockPos[] { TestEngine.HEAD, TestEngine.HEAD_2 })
				((CylinderHeadBlockEntity) helper.getBlockEntity(head)).tank()
					.drain(Integer.MAX_VALUE, FluidAction.EXECUTE);
		});
		helper.runAtTickTime(2500, () -> {
			CrankshaftBlockEntity first = helper.getBlockEntity(PRIMARY);
			CrankshaftBlockEntity second = helper.getBlockEntity(secondCrank);
			helper.assertTrue(first.getTheoreticalSpeed() == 0 && second.getTheoreticalSpeed() == 0,
				"A stalled engine should not still turn: " + first.getTheoreticalSpeed() + ", " + second.getTheoreticalSpeed());
			helper.succeed();
		});
	}

	/**
	 * A network that is turning with no generator behind it (as a ring of gearing can be left by Create, every block following
	 * the next) comes to rest: the engine's blocks check for it and release it. The state is forged here by making a motor's
	 * network forget the motor, which is what leaves its blocks turning on nothing.
	 */
	@GameTest(template = "empty", timeoutTicks = 600)
	public static void aNetworkTurningWithNoGeneratorIsBroughtToRest(GameTestHelper helper) {
		helper.setBlock(new BlockPos(0, 2, 1), AllBlocks.CREATIVE_MOTOR.getDefaultState()
			.setValue(com.simibubi.create.content.kinetics.motor.CreativeMotorBlock.FACING, Direction.EAST));
		helper.setBlock(new BlockPos(1, 2, 1), alongX(AllBlocks.SHAFT.getDefaultState()));
		helper.setBlock(new BlockPos(2, 2, 1), alongX(AllBlocks.SHAFT.getDefaultState()));
		BlockPos alternator = new BlockPos(3, 2, 1);
		helper.setBlock(alternator, TestEngine.axisX(CEBlocks.ALTERNATOR.getDefaultState()));
		((com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity) helper.getBlockEntity(new BlockPos(0, 2, 1))).generatedSpeed.setValue(40);
		helper.runAtTickTime(50, () -> {
			AlternatorBlockEntity be = helper.getBlockEntity(alternator);
			helper.assertTrue(be.getTheoreticalSpeed() != 0, "The motor should be turning the alternator");
			be.getOrCreateNetwork().sources.clear();
		});
		helper.runAtTickTime(300, () -> {
			AlternatorBlockEntity be = helper.getBlockEntity(alternator);
			helper.assertTrue(be.getTheoreticalSpeed() == 0,
				"A network with no generator should have been brought to rest: " + be.getTheoreticalSpeed());
			helper.succeed();
		});
	}

	/**
	 * A crank that is not its engine's primary must not generate, even if it was saved still publishing a speed and capacity
	 * (an engine it once drove, or one that was running when the world was saved): left alone it keeps the network turning
	 * and supplying stress after the engine that owns it has stalled.
	 */
	@GameTest(template = "empty", timeoutTicks = 1200)
	public static void aCoupledCrankLeftPublishingIsMadeToStop(GameTestHelper helper) {
		buildSingleCylinder(helper, true);
		TestEngine.addCoupledLine(helper.getLevel(), helper.absolutePos(BlockPos.ZERO));
		helper.runAtTickTime(300, () -> helper.setBlock(STARTER_POWER, Blocks.AIR));
		helper.runAtTickTime(500, () -> {
			CrankshaftBlockEntity other = helper.getBlockEntity(TestEngine.THROW_2);
			helper.assertTrue(helper.absolutePos(PRIMARY)
				.equals(other.primaryPos()), "The second line should belong to the first's engine");
			other.publishCapacity(5000);
			other.publishSpeed(200);
			helper.assertTrue(other.getGeneratedSpeed() == 200, "The stale publish should have been applied");
		});
		helper.runAtTickTime(900, () -> {
			CrankshaftBlockEntity other = helper.getBlockEntity(TestEngine.THROW_2);
			helper.assertTrue(other.getGeneratedSpeed() == 0 && other.publishedCapacity() == 0,
				"Only the primary generates: " + other.getGeneratedSpeed() + ", " + other.publishedCapacity());
			helper.succeed();
		});
	}

	@GameTest(template = "empty", timeoutTicks = 1200)
	public static void crankshaftsGearedTogetherAreOneEngine(GameTestHelper helper) {
		buildSingleCylinder(helper, true);
		TestEngine.addCoupledLine(helper.getLevel(), helper.absolutePos(BlockPos.ZERO));
		BlockPos throw2 = TestEngine.THROW_2;
		BlockPos cylinder2 = TestEngine.CYLINDER_2;

		helper.runAtTickTime(2, () -> {
			EngineLayout layout = EngineScanner.scan(helper.getLevel(), helper.absolutePos(throw2));
			helper.assertTrue(layout != null && layout.lines() == 2,
				"Two crank lines geared one to one should scan as one engine: " + (layout == null ? null : layout.lines()));
			helper.assertTrue(layout.primary()
				.equals(helper.absolutePos(PRIMARY)), "The first line by position should lead: " + layout.primary());
			helper.assertTrue(layout.cylinders()
				.size() == 2, "Both cylinders belong to the engine");
			helper.assertTrue(layout.segments()
				.stream()
				.allMatch(s -> s.sense() == 1), "A row of three cogs turns the second line the same way");
			TimingToolItem.Result result =
				TimingToolItem.apply(helper.getLevel(), helper.absolutePos(throw2), FiringPlanner.Style.SMOOTH);
			helper.assertTrue(result != null && result.plan() != null && result.plan()
				.even(), "The tool should time both lines' cylinders together");
		});
		helper.runAtTickTime(300, () -> helper.setBlock(STARTER_POWER, Blocks.AIR));
		helper.runAtTickTime(800, () -> {
			EngineSummary summary = summary(helper);
			helper.assertTrue(summary.lines() == 2, "The engine should know it has two crankshafts: " + summary);
			helper.assertTrue(summary.state() == EngineState.RUNNING && summary.firing() == 2,
				"Both cylinders should fire: " + summary + ", second " + ((CylinderBlockEntity) helper.getBlockEntity(cylinder2)).status());
			float[] intervals = summary.firingIntervals();
			helper.assertTrue(intervals.length == 2 && Math.abs(intervals[0] - 360) < 1,
				"They should fire half a cycle apart: " + java.util.Arrays.toString(intervals));
			CylinderBlockEntity second = helper.getBlockEntity(cylinder2);
			helper.assertTrue(second.status() != null && Math.abs(second.status()
				.timingError()) < 1, "The second line's cylinder should be timed: " + second.status());
			CrankshaftBlockEntity first = helper.getBlockEntity(PRIMARY);
			CrankshaftBlockEntity other = helper.getBlockEntity(throw2);
			helper.assertTrue(helper.absolutePos(PRIMARY)
				.equals(other.primaryPos()), "The second line should report the first as its primary");
			helper.assertTrue(other.publishedCapacity() == 0 && other.getGeneratedSpeed() == 0,
				"Only the primary generates: " + other.publishedCapacity());
			helper.assertTrue(Math.abs(other.getTheoreticalSpeed() - first.getTheoreticalSpeed()) < 1e-3
				&& first.getTheoreticalSpeed() != 0, "The gearing should turn both at one speed");
			helper.succeed();
		});
	}

	/**
	 * Two cogwheels turn the second crank line the other way. Its throws and cylinders are then the mirror image in the
	 * engine's frame, and what is drawn follows suit.
	 */
	@GameTest(template = "empty", timeoutTicks = 900)
	public static void aCrankLineGearedTheOtherWayIsMirrored(GameTestHelper helper) {
		buildSingleCylinder(helper, true);
		for (int x = 2; x <= 4; x++)
			helper.setBlock(new BlockPos(x, 2, 1), alongX(x == 3 ? CEBlocks.CRANK_THROW.getDefaultState()
				: CEBlocks.CRANKSHAFT.getDefaultState()));
		BlockPos throw2 = new BlockPos(3, 2, 1);
		helper.setBlock(new BlockPos(3, 3, 1), CEBlocks.CYLINDER.getDefaultState()
			.setValue(CylinderBlock.FACING, Direction.UP)
			.setValue(CylinderBlock.HAS_PISTON, true)
			.setValue(CylinderBlock.HAS_ROD, true));
		CrankshaftBlockEntity throwEntity = helper.getBlockEntity(throw2);
		throwEntity.throwAngle.setValue(3);
		// The first line already ends in a cogwheel; a second beside it turns the new line the other way
		cog(helper, new BlockPos(5, 2, 1));

		helper.runAtTickTime(2, () -> {
			EngineLayout layout = EngineScanner.scan(helper.getLevel(), helper.absolutePos(PRIMARY));
			helper.assertTrue(layout != null && layout.lines() == 2, "The cogs should couple the lines");
			EngineLayout.Segment mirrored = layout.segments()
				.stream()
				.filter(s -> s.pos()
					.equals(helper.absolutePos(throw2)))
				.findFirst()
				.orElseThrow();
			helper.assertTrue(mirrored.sense() == -1, "Two cogs reverse the line: " + mirrored);
			EngineLayout.Cylinder second = layout.cylinders()
				.stream()
				.filter(c -> c.pos()
					.equals(helper.absolutePos(new BlockPos(3, 3, 1))))
				.findFirst()
				.orElseThrow();
			double own = CrankGeometry.topDeadCentreAngle(Axis.X, 3 * CrankshaftBlockEntity.THROW_ANGLE_STEP, Direction.UP);
			helper.assertTrue(second.sense() == -1 && Math.abs(second.tdcAngle() + own) < 1e-6,
				"Top dead centre should be mirrored in the engine's frame: " + second.tdcAngle() + " against " + own);
		});
		helper.runAtTickTime(300, () -> helper.setBlock(STARTER_POWER, Blocks.AIR));
		helper.runAtTickTime(700, () -> {
			CrankshaftBlockEntity first = helper.getBlockEntity(PRIMARY);
			CrankshaftBlockEntity other = helper.getBlockEntity(throw2);
			helper.assertTrue(summary(helper).state() == EngineState.RUNNING, "The engine should run: " + summary(helper));
			helper.assertTrue(first.getTheoreticalSpeed() != 0 && Math.abs(first.getTheoreticalSpeed() + other.getTheoreticalSpeed()) < 1e-3,
				"The second line should turn the opposite way at the same speed: " + first.getTheoreticalSpeed() + " and "
					+ other.getTheoreticalSpeed());
			double drawn = first.rotation(100f);
			helper.assertTrue(Math.abs(other.rotation(100f) + drawn) < 1e-3,
				"The second line should be drawn as the first's mirror image");
			helper.succeed();
		});
	}
}
