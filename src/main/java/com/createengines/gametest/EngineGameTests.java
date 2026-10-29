package com.createengines.gametest;

import com.createengines.CreateEngines;
import com.createengines.content.airflow.PortOpeningBlock;
import com.createengines.content.camshaft.CamshaftBlockEntity;
import com.createengines.content.crankshaft.CrankshaftBlockEntity;
import com.createengines.content.cylinder.CylinderBlock;
import com.createengines.content.cylinder.CylinderBlockEntity;
import com.createengines.content.head.CylinderHeadBlock;
import com.createengines.content.head.CylinderHeadBlockEntity;
import com.createengines.content.starter.StarterBlock;
import com.createengines.engine.CrankGeometry;
import com.createengines.engine.CylinderStatus;
import com.createengines.engine.EngineState;
import com.createengines.engine.EngineSummary;
import com.createengines.engine.sim.CycleResult;
import com.createengines.engine.topology.CylinderFault;
import com.createengines.registry.CEBlocks;
import com.createengines.registry.CEFluids;
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
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Builds real engines in a server world and checks they behave. Run with {@code ./gradlew runGameTestServer}.
 * <p>
 * The test engine is a single cylinder on a crankshaft along X, started by a starter driven by a creative motor.
 * Its camshaft sits beside the cylinder head and is geared to half crank speed the Create way: a small cogwheel on
 * the crank driving a large one, then a pair of encased chain drives up to the camshaft.
 */
@GameTestHolder(CreateEngines.MOD_ID)
@PrefixGameTestTemplate(false)
public class EngineGameTests {

	private static final BlockPos PRIMARY = TestEngine.PRIMARY;
	private static final BlockPos CYLINDER = TestEngine.CYLINDER;
	private static final BlockPos HEAD = TestEngine.HEAD;
	private static final BlockPos LOBE = TestEngine.LOBE;
	private static final BlockPos EXHAUST = TestEngine.EXHAUST;
	private static final BlockPos STARTER = TestEngine.STARTER;
	private static final BlockPos MOTOR = TestEngine.MOTOR;
	private static final BlockPos STARTER_POWER = TestEngine.STARTER_POWER;

	private static void buildSingleCylinder(GameTestHelper helper, boolean fuelled) {
		TestEngine.build(helper.getLevel(), helper.absolutePos(BlockPos.ZERO), fuelled);
	}

	private static EngineSummary summary(GameTestHelper helper) {
		CrankshaftBlockEntity crank = helper.getBlockEntity(PRIMARY);
		EngineSummary summary = crank.summary();
		if (summary == null)
			helper.fail("Crankshaft has no engine (topology did not build one)", PRIMARY);
		return summary;
	}

	private static CylinderStatus cylinder(GameTestHelper helper) {
		CylinderBlockEntity cylinder = helper.getBlockEntity(CYLINDER);
		CylinderStatus status = cylinder.status();
		if (status == null)
			helper.fail("Cylinder has no engine status", CYLINDER);
		return status;
	}

	@GameTest(template = "empty", timeoutTicks = 900)
	public static void singleCylinderStartsAndRunsOnItsOwn(GameTestHelper helper) {
		buildSingleCylinder(helper, true);

		// Let the starter crank it, then release the starter
		helper.runAtTickTime(300, () -> {
			EngineSummary summary = summary(helper);
			helper.assertTrue(summary.firing() == 1,
				"Cylinder should be firing under the starter, summary " + summary + ", cylinder " + cylinder(helper));
			helper.setBlock(STARTER_POWER, Blocks.AIR);
		});

		helper.runAtTickTime(700, () -> {
			CrankshaftBlockEntity crank = helper.getBlockEntity(PRIMARY);
			EngineSummary summary = summary(helper);
			CylinderStatus status = cylinder(helper);
			helper.assertTrue(summary.state() == EngineState.RUNNING, "Engine should run on its own: " + summary);
			helper.assertTrue(Math.abs(crank.getSpeed()) >= 20,
				"Crank should be turning in Create, speed " + crank.getSpeed());
			helper.assertTrue(crank.publishedCapacity() > 0, "Engine should provide stress capacity");
			helper.assertTrue(status.result() == CycleResult.FIRED, "Cylinder should fire: " + status);
			helper.assertTrue(Math.abs(status.timingError()) < 1, "Cam timing should be right: " + status);
			CamshaftBlockEntity cam = helper.getBlockEntity(LOBE);
			helper.assertTrue(Math.abs(Math.abs(cam.getTheoreticalSpeed()) * 2 - Math.abs(crank.getTheoreticalSpeed())) < 1e-3,
				"Camshaft should turn at half crank speed");
			CylinderHeadBlockEntity head = helper.getBlockEntity(HEAD);
			helper.assertTrue(head.tank()
				.getFluidAmount() < 250, "Engine should have used fuel");
			helper.succeed();
		});
	}

	@GameTest(template = "empty", timeoutTicks = 900)
	public static void unfuelledEngineDoesNotRun(GameTestHelper helper) {
		buildSingleCylinder(helper, false);
		helper.runAtTickTime(300, () -> {
			EngineSummary summary = summary(helper);
			helper.assertTrue(summary.state() == EngineState.CRANKING, "Starter should be cranking it: " + summary);
			helper.assertTrue(cylinder(helper).result() == CycleResult.NO_FUEL,
				"Cylinder should report no fuel: " + cylinder(helper));
			helper.setBlock(STARTER_POWER, Blocks.AIR);
		});
		helper.runAtTickTime(800, () -> {
			CrankshaftBlockEntity crank = helper.getBlockEntity(PRIMARY);
			helper.assertTrue(crank.getSpeed() == 0, "Engine without fuel should stop, speed " + crank.getSpeed());
			helper.assertTrue(summary(helper).state() == EngineState.STALLED, "Engine should be stalled");
			helper.succeed();
		});
	}

	@GameTest(template = "empty", timeoutTicks = 3000)
	public static void removingTheExhaustStallsARunningEngine(GameTestHelper helper) {
		buildSingleCylinder(helper, true);
		helper.runAtTickTime(300, () -> helper.setBlock(STARTER_POWER, Blocks.AIR));
		helper.runAtTickTime(500, () -> {
			helper.assertTrue(summary(helper).state() == EngineState.RUNNING, "Engine should be running first");
			helper.setBlock(EXHAUST, Blocks.AIR);
		});
		// Burned gas has nowhere to go but back into the intake: the engine chokes and coasts to a stop
		helper.succeedWhen(() -> {
			helper.assertTrue(helper.getTick() > 520, "Exhaust not removed yet");
			helper.assertTrue(CylinderFault.NO_EXHAUST.in(cylinder(helper).faults()),
				"Cylinder should report its missing exhaust: " + cylinder(helper));
			CrankshaftBlockEntity crank = helper.getBlockEntity(PRIMARY);
			helper.assertTrue(crank.getSpeed() == 0, "Engine that can't exhaust should stall, speed " + crank.getSpeed());
		});
	}

	@GameTest(template = "empty", timeoutTicks = 900)
	public static void starterLoadsItsInputNetwork(GameTestHelper helper) {
		buildSingleCylinder(helper, false);
		helper.runAtTickTime(200, () -> {
			CreativeMotorBlockEntity motor = helper.getBlockEntity(MOTOR);
			helper.assertTrue(motor.getSpeed() != 0, "Motor should be turning the starter");
			com.createengines.content.starter.StarterBlockEntity starter = helper.getBlockEntity(STARTER);
			helper.assertTrue(starter.calculateStressApplied() > 0,
				"Cranking a dead engine should put stress on the starter's input");
			helper.succeed();
		});
	}
}
