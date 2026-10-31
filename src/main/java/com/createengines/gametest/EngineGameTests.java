package com.createengines.gametest;

import java.util.Collection;
import java.util.function.BiConsumer;

import com.createengines.CreateEngines;
import com.createengines.content.airflow.LambdaSensorBlockEntity;
import com.createengines.content.airflow.MafSensorBlockEntity;
import com.createengines.content.airflow.ManifoldBlock;
import com.createengines.content.airflow.PortOpeningBlock;
import com.createengines.content.airflow.ThrottleBlockEntity;
import com.createengines.content.battery.BatteryBlockEntity;
import com.createengines.content.camshaft.CamshaftBlockEntity;
import com.createengines.content.crankshaft.CrankshaftBlockEntity;
import com.createengines.content.cylinder.CylinderBlock;
import com.createengines.content.cylinder.CylinderBlockEntity;
import com.createengines.content.head.CylinderHeadBlock;
import com.createengines.content.head.CylinderHeadBlockEntity;
import com.createengines.content.rotor.RotorHousingBlock;
import com.createengines.content.rotor.RotorHousingBlockEntity;
import com.createengines.content.governor.GovernorBlockEntity;
import com.createengines.content.limiter.RevLimiterBlockEntity;
import com.createengines.content.starter.StarterBlock;
import com.createengines.content.sump.SumpBlockEntity;
import com.createengines.content.transmission.Gears;
import com.createengines.content.transmission.TransmissionBlockEntity;
import com.createengines.content.dyno.DynoBlockEntity;
import com.createengines.content.tool.BankBuilderItem;
import com.createengines.content.tool.TimingToolItem;
import com.createengines.content.intercooler.IntercoolerBlock;
import com.createengines.content.supercharger.SuperchargerBlock;
import com.createengines.content.supercharger.SuperchargerBlockEntity;
import com.createengines.content.turbo.TurbochargerBlock;
import com.createengines.content.turbo.TurbochargerBlockEntity;
import com.createengines.engine.TurboStatus;
import com.createengines.content.airflow.PortKind;
import com.createengines.engine.CrankGeometry;
import com.createengines.engine.CylinderStatus;
import com.createengines.engine.EngineState;
import com.createengines.engine.EngineSummary;
import com.createengines.engine.sim.CycleResult;
import com.createengines.engine.topology.EngineLayout;
import com.createengines.engine.topology.EngineScanner;
import com.createengines.engine.topology.CylinderFault;
import com.createengines.engine.topology.FiringPlanner;
import com.createengines.registry.CEBlocks;
import com.createengines.registry.CECreativeTabs;
import com.createengines.registry.CEFluids;
import com.simibubi.create.AllBlocks;
import com.tterrag.registrate.util.entry.RegistryEntry;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlock;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
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
	/** First of a row of presses on the crank's output shaft, beyond its cogwheel. */
	private static final BlockPos PRESS = new BlockPos(6, 2, 2);

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

	/**
	 * Fuel piped into one head runs every cylinder on the crankshaft, even one whose head touches nothing. The test
	 * engine gets a second throw out at the end of the crank, with its own head, intake, exhaust and cam lobe, and only
	 * the first head is fed.
	 */
	@GameTest(template = "empty", timeoutTicks = 900)
	public static void fuelInOneHeadRunsEveryCylinderOnTheShaft(GameTestHelper helper) {
		buildSingleCylinder(helper, true);
		BlockPos head2 = new BlockPos(4, 4, 2);
		BlockPos lobe2 = new BlockPos(4, 4, 3);
		helper.setBlock(new BlockPos(4, 2, 2), CEBlocks.CRANK_THROW.getDefaultState()
			.setValue(BlockStateProperties.AXIS, Axis.X));
		helper.setBlock(new BlockPos(4, 3, 2), CEBlocks.CYLINDER.getDefaultState()
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
		CamshaftBlockEntity cam = helper.getBlockEntity(LOBE);
		CamshaftBlockEntity cam2 = helper.getBlockEntity(lobe2);
		cam2.lobeAngle.setValue(cam.lobeAngle.getValue());

		helper.runAtTickTime(300, () -> {
			EngineSummary summary = summary(helper);
			helper.assertTrue(summary.firing() == 2, "Both cylinders should fire on the one head's fuel: " + summary);
			CylinderHeadBlockEntity fed = helper.getBlockEntity(HEAD);
			CylinderHeadBlockEntity unfed = helper.getBlockEntity(head2);
			helper.assertTrue(unfed.tank()
				.isEmpty(), "The second head was never filled");
			helper.assertTrue(fed.tank()
				.getFluidAmount() < 250, "Both cylinders should have drawn on the fed head");
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

	/** Each item should be listed once in our tab and in search; a duplicate crashes the creative inventory. */
	@GameTest(template = "empty")
	public static void creativeTabListsEachItemOnce(GameTestHelper helper) {
		CreativeModeTabs.tryRebuildTabContents(helper.getLevel()
			.enabledFeatures(), true, helper.getLevel()
				.registryAccess());
		CreativeModeTab search = helper.getLevel()
			.registryAccess()
			.registryOrThrow(Registries.CREATIVE_MODE_TAB)
			.getOrThrow(CreativeModeTabs.SEARCH);
		for (RegistryEntry<Item, Item> entry : CreateEngines.REGISTRATE.getAll(Registries.ITEM)) {
			assertListedOnce(helper, CECreativeTabs.MAIN.get()
				.getDisplayItems(), entry, "our tab");
			assertListedOnce(helper, search.getDisplayItems(), entry, "search");
		}
		helper.succeed();
	}

	private static void assertListedOnce(GameTestHelper helper, Collection<ItemStack> listed,
		RegistryEntry<Item, Item> entry, String tab) {
		long count = listed.stream()
			.filter(stack -> stack.is(entry.get()))
			.count();
		helper.assertTrue(count == 1, "Creative " + tab + " should list " + entry.getId() + " once, found " + count);
	}

	/** Two presses are within the engine's rating: it slows into its torque band and keeps them turning. */
	@GameTest(template = "empty", timeoutTicks = 1400)
	public static void heavyLoadLugsTheEngineDown(GameTestHelper helper) {
		runWithPresses(helper, 2);
		helper.runAtTickTime(1200, () -> {
			CrankshaftBlockEntity crank = helper.getBlockEntity(PRIMARY);
			EngineSummary summary = summary(helper);
			helper.assertTrue(summary.state() == EngineState.RUNNING, "Engine should carry two presses: " + summary);
			helper.assertFalse(crank.isOverStressed(), "Two presses are within the engine's rating");
			helper.assertTrue(summary.rpm() < 130, "Engine should have slowed under load: " + summary);
			KineticBlockEntity press = helper.getBlockEntity(PRESS);
			helper.assertTrue(press.getSpeed() != 0, "Presses should be turning");
			helper.succeed();
		});
	}

	/** Four presses are more than it's rated for: Create overstresses the network, and the engine keeps running. */
	@GameTest(template = "empty", timeoutTicks = 1400)
	public static void overloadOverstressesWithoutStalling(GameTestHelper helper) {
		runWithPresses(helper, 4);
		helper.runAtTickTime(1000, () -> {
			CrankshaftBlockEntity crank = helper.getBlockEntity(PRIMARY);
			EngineSummary summary = summary(helper);
			helper.assertTrue(crank.isOverStressed(), "Four presses exceed the engine's rating");
			helper.assertTrue(summary.state() == EngineState.OVERLOADED && summary.rpm() > 50,
				"Engine should keep running while overstressed: " + summary);
			helper.succeed();
		});
	}

	/**
	 * A crank, throw and starter driven at 16 RPM by a creative motor, with or without a headless cylinder (piston and
	 * rod, but nothing to make it work) on the throw.
	 */
	private static void buildCrankOnly(GameTestHelper helper, boolean headlessCylinder) {
		BlockPos origin = helper.absolutePos(BlockPos.ZERO);
		BiConsumer<BlockPos, BlockState> set = (pos, state) -> helper.getLevel()
			.setBlock(origin.offset(pos), state, Block.UPDATE_ALL);
		set.accept(PRIMARY, CEBlocks.CRANKSHAFT.getDefaultState()
			.setValue(BlockStateProperties.AXIS, Axis.X));
		set.accept(TestEngine.THROW, CEBlocks.CRANK_THROW.getDefaultState()
			.setValue(BlockStateProperties.AXIS, Axis.X));
		set.accept(new BlockPos(3, 2, 2), CEBlocks.CRANKSHAFT.getDefaultState()
			.setValue(BlockStateProperties.AXIS, Axis.X));
		if (headlessCylinder)
			set.accept(CYLINDER, CEBlocks.CYLINDER.getDefaultState()
				.setValue(CylinderBlock.FACING, Direction.UP)
				.setValue(CylinderBlock.HAS_PISTON, true)
				.setValue(CylinderBlock.HAS_ROD, true));
		set.accept(STARTER, CEBlocks.STARTER.getDefaultState()
			.setValue(StarterBlock.FACING, Direction.UP));
		set.accept(MOTOR, AllBlocks.CREATIVE_MOTOR.getDefaultState()
			.setValue(CreativeMotorBlock.FACING, Direction.UP));
		set.accept(STARTER_POWER, Blocks.REDSTONE_BLOCK.defaultBlockState());
		CreativeMotorBlockEntity motor = helper.getBlockEntity(MOTOR);
		motor.generatedSpeed.setValue(16);
	}

	/** Without a working cylinder there is nothing to run on: the starter turns the crank, and when it lets go it stops. */
	private static void checkDeadCrankCoastsToAStop(GameTestHelper helper, boolean headlessCylinder) {
		buildCrankOnly(helper, headlessCylinder);
		helper.runAtTickTime(100, () -> {
			EngineSummary summary = summary(helper);
			helper.assertTrue(summary.state() == EngineState.CRANKING, "Starter should be cranking it: " + summary);
			helper.assertTrue(summary.cylinders() == 0, "No cylinder can work without a head: " + summary);
			helper.setBlock(STARTER_POWER, Blocks.AIR);
		});
		helper.runAtTickTime(110, () -> {
			EngineSummary summary = summary(helper);
			helper.assertFalse(summary.state() == EngineState.RUNNING,
				"Nothing is firing, so it can't be running: " + summary);
		});
		// It must not go on turning for ever
		helper.succeedWhen(() -> {
			helper.assertTrue(helper.getTick() > 120, "Starter not released yet");
			CrankshaftBlockEntity crank = helper.getBlockEntity(PRIMARY);
			helper.assertTrue(crank.getSpeed() == 0,
				"Crank with no working cylinder should stop, speed " + crank.getSpeed());
			helper.assertTrue(summary(helper).state() == EngineState.STALLED, "Engine should be stalled");
		});
	}

	@GameTest(template = "empty", timeoutTicks = 900)
	public static void bareCrankStopsWhenTheStarterLetsGo(GameTestHelper helper) {
		checkDeadCrankCoastsToAStop(helper, false);
	}

	@GameTest(template = "empty", timeoutTicks = 900)
	public static void headlessCylinderStopsWhenTheStarterLetsGo(GameTestHelper helper) {
		checkDeadCrankCoastsToAStop(helper, true);
	}

	/** Fuel with no intake: no fresh air ever gets in, so it never fires, not even on the air it was built with. */
	@GameTest(template = "empty", timeoutTicks = 700)
	public static void cylinderWithoutIntakeNeverFires(GameTestHelper helper) {
		buildSingleCylinder(helper, true);
		helper.setBlock(TestEngine.AIR_INTAKE, Blocks.AIR);
		for (int tick = 20; tick <= 600; tick += 20)
			helper.runAtTickTime(tick, () -> {
				EngineSummary summary = summary(helper);
				helper.assertTrue(summary.firing() == 0, "Cylinder with no intake fired: " + summary);
				CrankshaftBlockEntity crank = helper.getBlockEntity(PRIMARY);
				helper.assertTrue(crank.publishedCapacity() == 0, "Engine shouldn't provide stress capacity");
			});
		helper.runAtTickTime(650, () -> {
			helper.assertTrue(CylinderFault.NO_INTAKE.in(cylinder(helper).faults()),
				"Cylinder should report its missing intake");
			helper.succeed();
		});
	}

	/**
	 * A cam beside the cylinder block works the valves through pushrods, so the head's faces are left free. The test
	 * engine's camshaft is moved down a layer to sit against the cylinder, and its large cogwheel then drives it
	 * directly.
	 */
	@GameTest(template = "empty", timeoutTicks = 900)
	public static void aCamBesideTheCylinderBlockTimesTheEngine(GameTestHelper helper) {
		buildSingleCylinder(helper, true);
		for (int x = 2; x <= 5; x++)
			helper.setBlock(new BlockPos(x, 4, 3), Blocks.AIR);
		helper.setBlock(new BlockPos(6, 3, 3), Blocks.AIR);
		helper.setBlock(new BlockPos(6, 4, 3), Blocks.AIR);
		BlockPos lobe = new BlockPos(2, 3, 3);
		helper.setBlock(lobe, CEBlocks.CAM_LOBE.getDefaultState()
			.setValue(BlockStateProperties.AXIS, Axis.X));
		for (int x = 3; x <= 4; x++)
			helper.setBlock(new BlockPos(x, 3, 3), CEBlocks.CAMSHAFT.getDefaultState()
				.setValue(BlockStateProperties.AXIS, Axis.X));
		double tdc = CrankGeometry.topDeadCentreAngle(Axis.X, 0, Direction.UP);
		CamshaftBlockEntity cam = helper.getBlockEntity(lobe);
		cam.lobeAngle.setValue((int) Math.round(((tdc / 2) % 180 + 180) % 180 / CamshaftBlockEntity.LOBE_ANGLE_STEP));

		helper.runAtTickTime(300, () -> {
			CylinderStatus status = cylinder(helper);
			helper.assertTrue(!CylinderFault.NO_CAM.in(status.faults()), "Cam beside the cylinder should count: " + status);
			helper.assertTrue(helper.absolutePos(lobe)
				.equals(status.lobe()), "Cylinder should use the lobe beside it: " + status);
			helper.assertTrue(summary(helper).firing() == 1,
				"Cylinder should fire on the pushrod cam, summary " + summary(helper) + ", cylinder " + status);
			helper.assertTrue(Math.abs(status.timingError()) < 1, "Cam timing should be right: " + status);
			helper.succeed();
		});
	}

	/** Air with no injector: pumping air round costs power, it can't make any. */
	@GameTest(template = "empty", timeoutTicks = 700)
	public static void cylinderWithoutInjectorNeverFires(GameTestHelper helper) {
		buildSingleCylinder(helper, true);
		helper.setBlock(HEAD, CEBlocks.CYLINDER_HEAD.getDefaultState()
			.setValue(CylinderHeadBlock.FACING, Direction.UP)
			.setValue(CylinderHeadBlock.HAS_INJECTOR, false));
		for (int tick = 20; tick <= 600; tick += 20)
			helper.runAtTickTime(tick, () -> {
				EngineSummary summary = summary(helper);
				helper.assertTrue(summary.firing() == 0, "Cylinder with no injector fired: " + summary);
				CrankshaftBlockEntity crank = helper.getBlockEntity(PRIMARY);
				helper.assertTrue(crank.publishedCapacity() == 0, "Engine shouldn't provide stress capacity");
			});
		helper.runAtTickTime(650, () -> {
			helper.assertTrue(CylinderFault.NO_INJECTOR.in(cylinder(helper).faults()),
				"Cylinder should report its missing injector");
			helper.succeed();
		});
	}

	/** A new engine is dry. Oil piped into its head builds the film, and turning the engine uses the oil up. */
	@GameTest(template = "empty", timeoutTicks = 900)
	public static void engineOilBuildsTheFilmAndIsUsedUp(GameTestHelper helper) {
		TestEngine.build(helper.getLevel(), helper.absolutePos(BlockPos.ZERO), true, false);
		helper.runAtTickTime(100, () -> {
			EngineSummary summary = summary(helper);
			helper.assertTrue(summary.lubrication() < 0.05, "An engine with no oil should be dry: " + summary);
			CylinderHeadBlockEntity head = helper.getBlockEntity(HEAD);
			helper.assertTrue(head.fluidCapability()
				.fill(new FluidStack(CEFluids.ENGINE_OIL.get()
					.getSource(), 250), FluidAction.EXECUTE) == 250, "The head should take engine oil from a pipe");
			helper.assertTrue(head.fluidCapability()
				.fill(new FluidStack(Fluids.WATER, 250), FluidAction.EXECUTE) == 0, "It shouldn't take water");
		});
		helper.runAtTickTime(500, () -> {
			EngineSummary summary = summary(helper);
			helper.assertTrue(summary.lubrication() > 0.95, "Oil should have built the film: " + summary);
			CylinderHeadBlockEntity head = helper.getBlockEntity(HEAD);
			helper.assertTrue(head.oilTank()
				.getFluidAmount() < 250, "Turning the engine should use oil");
			helper.assertTrue(head.oilTank()
				.getFluidAmount() > 200, "But not very much of it");
			helper.succeed();
		});
	}

	/** An oil sump under the crankshaft oils the whole engine, without a pipe to the head, and is used up slowly. */
	@GameTest(template = "empty", timeoutTicks = 900)
	public static void anOilSumpOilsTheWholeEngine(GameTestHelper helper) {
		TestEngine.build(helper.getLevel(), helper.absolutePos(BlockPos.ZERO), true, false);
		BlockPos sump = new BlockPos(3, 1, 2);
		helper.setBlock(sump, CEBlocks.SUMP.getDefaultState());
		helper.runAtTickTime(100, () -> {
			helper.assertTrue(summary(helper).lubrication() < 0.05, "An engine with no oil should be dry");
			SumpBlockEntity pan = helper.getBlockEntity(sump);
			helper.assertTrue(pan.fluidCapability()
				.fill(new FluidStack(CEFluids.ENGINE_OIL.get()
					.getSource(), 500), FluidAction.EXECUTE) == 500, "The sump should take engine oil from a pipe");
			helper.assertTrue(pan.fluidCapability()
				.fill(new FluidStack(Fluids.WATER, 500), FluidAction.EXECUTE) == 0, "It shouldn't take water");
		});
		helper.runAtTickTime(500, () -> {
			EngineSummary summary = summary(helper);
			helper.assertTrue(summary.lubrication() > 0.95, "The sump should have built the film: " + summary);
			SumpBlockEntity pan = helper.getBlockEntity(sump);
			helper.assertTrue(pan.tank()
				.getFluidAmount() < 500, "Turning the engine should use the sump's oil");
			helper.assertTrue(pan.tank()
				.getFluidAmount() > 400, "But not very much of it");
			CylinderHeadBlockEntity head = helper.getBlockEntity(HEAD);
			helper.assertTrue(head.oilTank()
				.isEmpty(), "The head's own oil tank was never needed");
			helper.succeed();
		});
	}

	/** A sump that touches no engine supplies nothing, and says so. */
	@GameTest(template = "empty")
	public static void aLoneSumpBelongsToNoEngine(GameTestHelper helper) {
		BlockPos sump = new BlockPos(1, 1, 1);
		helper.setBlock(sump, CEBlocks.SUMP.getDefaultState());
		SumpBlockEntity pan = helper.getBlockEntity(sump);
		helper.assertTrue(pan.fluidCapability() != null, "Its capability should exist for pipes to find");
		helper.succeed();
	}

	/** Oil is what lets an engine reach its speed: the same engine, dry, tops out far lower. */
	@GameTest(template = "empty", timeoutTicks = 1300)
	public static void dryEngineRunsSlowerThanAnOiledOne(GameTestHelper helper) {
		TestEngine.build(helper.getLevel(), helper.absolutePos(BlockPos.ZERO), true, false);
		helper.runAtTickTime(300, () -> helper.setBlock(STARTER_POWER, Blocks.AIR));
		helper.runAtTickTime(1200, () -> {
			EngineSummary summary = summary(helper);
			helper.assertTrue(summary.state() == EngineState.RUNNING, "A dry engine still runs: " + summary);
			helper.assertTrue(summary.rpm() < 100,
				"A dry engine should top out well below an oiled one's 120+: " + summary);
			helper.succeed();
		});
	}

	/** An alternator on the crank's own shaft charges the battery by cranking, which lets a spark engine start. */
	@GameTest(template = "empty", timeoutTicks = 1000)
	public static void sparkEngineStartsOnGasolineAndChargesItsBattery(GameTestHelper helper) {
		TestEngine.buildSpark(helper.getLevel(), helper.absolutePos(BlockPos.ZERO), true);
		helper.runAtTickTime(300, () -> {
			EngineSummary summary = summary(helper);
			helper.assertTrue(summary.firing() == 1,
				"Cylinder should fire from the plug under the starter, summary " + summary + ", cylinder " + cylinder(helper));
			BatteryBlockEntity battery = helper.getBlockEntity(TestEngine.BATTERY);
			helper.assertTrue(battery.charge() > 0, "Cranking should have charged the battery through the alternator");
			helper.setBlock(STARTER_POWER, Blocks.AIR);
		});
		helper.runAtTickTime(800, () -> {
			EngineSummary summary = summary(helper);
			helper.assertTrue(summary.state() == EngineState.RUNNING, "Spark engine should run on its own: " + summary);
			helper.assertTrue(cylinder(helper).result() == CycleResult.FIRED, "Cylinder should fire: " + cylinder(helper));
			CylinderHeadBlockEntity head = helper.getBlockEntity(HEAD);
			helper.assertTrue(head.tank()
				.getFluidAmount() < 250, "Engine should have burned gasoline");
			BatteryBlockEntity battery = helper.getBlockEntity(TestEngine.BATTERY);
			helper.assertTrue(battery.charge() > 20, "The turning engine should keep its battery charged");
			helper.succeed();
		});
	}

	/** Gasoline won't light by compression alone: without a plug the engine only turns over. */
	@GameTest(template = "empty", timeoutTicks = 700)
	public static void gasolineWithoutASparkPlugDoesNotLight(GameTestHelper helper) {
		buildSingleCylinder(helper, false);
		CylinderHeadBlockEntity head = helper.getBlockEntity(HEAD);
		head.tank()
			.fill(new FluidStack(CEFluids.GASOLINE.get()
				.getSource(), 250), FluidAction.EXECUTE);
		for (int tick = 100; tick <= 600; tick += 100)
			helper.runAtTickTime(tick, () -> helper.assertTrue(summary(helper).firing() == 0,
				"Gasoline lit with no spark plug: " + cylinder(helper)));
		helper.runAtTickTime(650, () -> {
			helper.assertTrue(cylinder(helper).result() == CycleResult.NO_IGNITION,
				"Fuel should be left unlit: " + cylinder(helper));
			helper.succeed();
		});
	}

	/** A spark engine flooded with fuel soots up its plug, and the cylinder reports it. */
	@GameTest(template = "empty", timeoutTicks = 1300)
	public static void aFloodedSparkEngineFoulsItsPlug(GameTestHelper helper) {
		TestEngine.buildSpark(helper.getLevel(), helper.absolutePos(BlockPos.ZERO), true);
		CylinderHeadBlockEntity head = helper.getBlockEntity(HEAD);
		head.metering.setValue(CylinderHeadBlockEntity.MAX_METERING);
		helper.runAtTickTime(300, () -> helper.setBlock(STARTER_POWER, Blocks.AIR));
		helper.runAtTickTime(1200, () -> {
			CylinderStatus status = cylinder(helper);
			helper.assertTrue(status.fouling() > 0.3f, "A flooded engine should soot its plug: " + status);
			helper.assertTrue(summary(helper).thinning() > 0, "Unburned fuel should thin the oil: " + summary(helper));
			helper.succeed();
		});
	}

	/** A plug with no battery on the engine is reported, and never fires. */
	@GameTest(template = "empty", timeoutTicks = 700)
	public static void sparkPlugWithoutABatteryNeverFires(GameTestHelper helper) {
		TestEngine.buildSpark(helper.getLevel(), helper.absolutePos(BlockPos.ZERO), false);
		for (int tick = 100; tick <= 600; tick += 100)
			helper.runAtTickTime(tick, () -> helper.assertTrue(summary(helper).firing() == 0,
				"Cylinder fired with no battery: " + cylinder(helper)));
		helper.runAtTickTime(650, () -> {
			helper.assertTrue(CylinderFault.NO_BATTERY.in(cylinder(helper).faults()),
				"Cylinder should report its missing battery: " + cylinder(helper));
			helper.assertTrue(cylinder(helper).result() == CycleResult.NO_SPARK,
				"Cylinder should report no spark: " + cylinder(helper));
			helper.succeed();
		});
	}

	/** A spark engine runs only while its battery holds charge: drain one the engine cannot recharge and it quits. */
	@GameTest(template = "empty", timeoutTicks = 1500)
	public static void sparkEngineStopsWhenItsBatteryIsDrained(GameTestHelper helper) {
		TestEngine.buildSpark(helper.getLevel(), helper.absolutePos(BlockPos.ZERO), true);
		helper.runAtTickTime(300, () -> helper.setBlock(STARTER_POWER, Blocks.AIR));
		helper.runAtTickTime(600, () -> {
			helper.assertTrue(summary(helper).state() == EngineState.RUNNING, "Engine should be running first");
			BatteryBlockEntity battery = helper.getBlockEntity(TestEngine.BATTERY);
			battery.draw(BatteryBlockEntity.CAPACITY);
			// Take the alternator away so nothing can charge it again
			helper.setBlock(TestEngine.ALTERNATOR, Blocks.AIR);
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(helper.getTick() > 620, "Battery not drained yet");
			helper.assertTrue(summary(helper).firing() == 0, "Nothing should fire without a spark: " + summary(helper));
			CrankshaftBlockEntity crank = helper.getBlockEntity(PRIMARY);
			helper.assertTrue(crank.getSpeed() == 0, "Engine should have stopped, speed " + crank.getSpeed());
		});
	}

	/** Too much fuel for the air is a fault you can see: the cylinder reports rich, and a hand on the dial fixes it. */
	@GameTest(template = "empty", timeoutTicks = 1500)
	public static void aRichMeteringSettingRunsRichAndTurningItDownCuresIt(GameTestHelper helper) {
		buildSingleCylinder(helper, true);
		CylinderHeadBlockEntity head = helper.getBlockEntity(HEAD);
		head.metering.setValue(CylinderHeadBlockEntity.MAX_METERING);
		helper.runAtTickTime(300, () -> helper.setBlock(STARTER_POWER, Blocks.AIR));
		helper.runAtTickTime(900, () -> {
			CylinderStatus status = cylinder(helper);
			helper.assertTrue(status.result() == CycleResult.FIRED, "Rich engine should still fire: " + status);
			helper.assertTrue(status.rich(), "Twice the fuel should run rich: " + status);
			helper.assertTrue(status.mixture() > 1.25f, "Mixture should read rich: " + status);
			head.metering.setValue(CylinderHeadBlockEntity.DEFAULT_METERING / 2);
		});
		helper.runAtTickTime(1400, () -> {
			CylinderStatus status = cylinder(helper);
			helper.assertFalse(status.rich(), "Half the fuel should clear it: " + status);
			helper.assertTrue(status.result() == CycleResult.FIRED, "Cylinder should be firing: " + status);
			helper.succeed();
		});
	}

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

	/**
	 * A MAF sensor in the intake and a lambda sensor in the exhaust. The injector is set to the largest shot it allows,
	 * which would run this engine rich at speed; the MAF sensor holds it to the air the cylinder breathed, so it burns
	 * clean, and both sensors belong to the engine and put what they read out as redstone.
	 */
	@GameTest(template = "empty", timeoutTicks = 1300)
	public static void sensorsReadAndTrimAnEngine(GameTestHelper helper) {
		buildSingleCylinder(helper, true);
		CylinderHeadBlockEntity head = helper.getBlockEntity(HEAD);
		head.metering.setValue(CylinderHeadBlockEntity.MAX_METERING);
		BlockPos intake = TestEngine.AIR_INTAKE;
		helper.setBlock(intake, Blocks.AIR);
		helper.setBlock(intake, placePipe(helper, CEBlocks.MAF_SENSOR.get(), intake));
		helper.setBlock(intake.west(), CEBlocks.AIR_INTAKE.getDefaultState()
			.setValue(PortOpeningBlock.FACING, Direction.WEST));
		helper.setBlock(EXHAUST, Blocks.AIR);
		helper.setBlock(EXHAUST, placePipe(helper, CEBlocks.LAMBDA_SENSOR.get(), EXHAUST));
		helper.setBlock(EXHAUST.above(), CEBlocks.EXHAUST_OUTLET.getDefaultState()
			.setValue(PortOpeningBlock.FACING, Direction.UP));
		helper.runAtTickTime(300, () -> helper.setBlock(STARTER_POWER, Blocks.AIR));
		helper.runAtTickTime(900, () -> {
			CylinderStatus status = cylinder(helper);
			helper.assertTrue(status.result() == CycleResult.FIRED,
				"Engine should run through both sensors: " + status + ", " + summary(helper));
			helper.assertFalse(status.rich(), "The MAF sensor should keep a double shot from running rich: " + status);
			helper.assertTrue(status.mixture() < 1.3f, "The shot should follow the air: " + status);

			MafSensorBlockEntity maf = helper.getBlockEntity(intake);
			helper.assertTrue(maf.onEngine(), "The MAF sensor should belong to the engine");
			helper.assertTrue(maf.signal() > 0, "It should put out a signal for the air the engine breathes");
			helper.assertTrue(helper.getLevel()
				.getSignal(helper.absolutePos(intake), Direction.NORTH) == maf.signal(),
				"Its signal should reach the blocks around it");

			LambdaSensorBlockEntity lambda = helper.getBlockEntity(EXHAUST);
			helper.assertTrue(lambda.onEngine(), "The lambda sensor should belong to the engine");
			helper.assertFalse(Float.isNaN(lambda.mixture()), "It should read what the firing cylinder burned");
			helper.assertTrue(lambda.mixture() < 1.3f, "A trimmed engine should read balanced or lean: " + lambda.mixture());
			helper.assertTrue(lambda.signal() > 0 && lambda.signal() < 11,
				"A balanced mixture reads near the middle: " + lambda.signal());
			helper.succeed();
		});
	}

	/**
	 * A rev limiter against the crankshaft holds the injector shut when the crank reaches its setting, so an engine that
	 * would rev well past it climbs to it, loses a cycle's fuel, and gets it back once it has slowed.
	 */
	@GameTest(template = "empty", timeoutTicks = 1500)
	public static void aRevLimiterBouncesAnEngineOffItsLimit(GameTestHelper helper) {
		buildSingleCylinder(helper, true);
		BlockPos at = new BlockPos(3, 3, 2);
		helper.setBlock(at, CEBlocks.REV_LIMITER.getDefaultState());
		RevLimiterBlockEntity limiter = helper.getBlockEntity(at);
		int limit = 80;
		limiter.limit.setValue(limit / RevLimiterBlockEntity.LIMIT_STEP);
		float[] peak = new float[1];
		// Whether fuel was cut at the limit, and whether it came back afterwards
		boolean[] seen = new boolean[2];
		helper.runAtTickTime(300, () -> helper.setBlock(STARTER_POWER, Blocks.AIR));
		helper.succeedWhen(() -> {
			helper.assertTrue(helper.getTick() > 400, "Still starting");
			EngineSummary summary = summary(helper);
			CycleResult result = cylinder(helper).result();
			peak[0] = Math.max(peak[0], summary.rpm());
			seen[0] |= result == CycleResult.FUEL_CUT;
			seen[1] |= seen[0] && result == CycleResult.FIRED;
			helper.assertTrue(helper.getTick() > 1100, "Watching the limiter");
			helper.assertTrue(seen[0], "The limiter should have cut the fuel, peak " + peak[0] + " RPM: " + summary);
			helper.assertTrue(seen[1], "Fuel should have come back once the engine slowed: " + summary);
			helper.assertTrue(peak[0] > limit * 0.7f && peak[0] <= limit * 1.15f,
				"The engine should climb to its limit and no further, peak " + peak[0] + " RPM");
			helper.assertTrue(summary.rpm() > 0, "The limiter should not stall the engine: " + summary);
			helper.assertTrue(limiter.signal() > 0, "The limiter should put out a signal for how near the limit it is");
		});
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
		governor.target.setValue(target / GovernorBlockEntity.TARGET_STEP);
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
			helper.succeed();
		});
	}

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
		int[] gears = {Gears.FIRST, 2, 3, Gears.FOURTH, Gears.NEUTRAL, Gears.REVERSE};
		float[] speeds = {16, 25.6f, 40, 64, 0, -21.333f};
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

	/** A redstone signal selects the gear over the panel, and the panel takes over again without one. */
	@GameTest(template = "empty", timeoutTicks = 200)
	public static void redstoneOverridesTheTransmissionPanel(GameTestHelper helper) {
		buildTransmission(helper, Gears.FIRST);
		BlockPos power = new BlockPos(1, 2, 1);
		helper.runAtTickTime(20, () -> helper.assertTrue(Math.abs(outputSpeed(helper) - 16) < 0.1f,
			"The panel's first gear should be in use: " + outputSpeed(helper)));
		helper.runAtTickTime(25, () -> helper.setBlock(power, Blocks.REDSTONE_BLOCK));
		helper.runAtTickTime(45, () -> helper.assertTrue(outputSpeed(helper) < -20,
			"A strong signal selects reverse: " + outputSpeed(helper)));
		helper.runAtTickTime(50, () -> helper.setBlock(power, Blocks.AIR));
		helper.runAtTickTime(70, () -> {
			helper.assertTrue(Math.abs(outputSpeed(helper) - 16) < 0.1f,
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

	private static BlockState alongX(BlockState state) {
		return state.setValue(BlockStateProperties.AXIS, Axis.X);
	}

	private static void cog(GameTestHelper helper, BlockPos pos) {
		helper.setBlock(pos, alongX(AllBlocks.COGWHEEL.getDefaultState()));
	}

	/**
	 * A second crank line, three blocks from the first and geared to it by a row of three small cogwheels at the west end
	 * (which turns it the same way), is part of the same engine. It carries a cylinder of its own, worked by a lobe on the
	 * first line's camshaft, and the engine times both cylinders half a cycle apart and runs on them.
	 */
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
			RotorHousingBlockEntity housing = helper.getBlockEntity(TestEngine.HOUSING);
			for (int i = 0; i < 3; i++)
				helper.assertTrue(housing.chamber(i) != null && housing.chamber(i)
					.result() == CycleResult.FIRED, "Chamber " + i + ": " + housing.chamber(i));
			helper.assertTrue(housing.tank()
				.getFluidAmount() < 250, "The engine should have burned gasoline");
			helper.succeed();
		});
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

	// New in the early-game, flywheel, turbo, bank and dynamometer pass

	/** A Cast Flywheel on the crank's shaft line is counted as rotating mass by the scanner. */
	@GameTest(template = "empty")
	public static void aCastFlywheelOnTheCrankLineAddsItsInertia(GameTestHelper helper) {
		buildSingleCylinder(helper, true);
		EngineLayout bare = EngineScanner.scan(helper.getLevel(), helper.absolutePos(PRIMARY));
		helper.assertTrue(bare != null && bare.flywheelInertia() == 0, "No flywheel yet: " + bare);
		helper.setBlock(new BlockPos(0, 2, 2), CEBlocks.CAST_FLYWHEEL.getDefaultState()
			.setValue(BlockStateProperties.AXIS, Axis.X));
		EngineLayout layout = EngineScanner.scan(helper.getLevel(), helper.absolutePos(PRIMARY));
		helper.assertTrue(layout != null
			&& Math.abs(layout.flywheelInertia() - com.createengines.config.CEConfig.CAST_FLYWHEEL_INERTIA.get()) < 1e-9,
			"The cast flywheel should add its inertia: " + (layout == null ? null : layout.flywheelInertia()));
		helper.succeed();
	}

	/** The Timing Tool's tune mode sets a flooded injector back to a balanced mixture. */
	@GameTest(template = "empty", timeoutTicks = 1700)
	public static void tuningAFloodedEngineBringsTheMixtureBack(GameTestHelper helper) {
		buildSingleCylinder(helper, true);
		CylinderHeadBlockEntity head = helper.getBlockEntity(HEAD);
		head.metering.setValue(CylinderHeadBlockEntity.MAX_METERING);
		helper.runAtTickTime(300, () -> helper.setBlock(STARTER_POWER, Blocks.AIR));
		helper.runAtTickTime(900, () -> {
			helper.assertTrue(cylinder(helper).rich(), "Twice the fuel should run rich first: " + cylinder(helper));
			var tuning = TimingToolItem.tuneFuel(helper.getLevel(), helper.absolutePos(HEAD));
			helper.assertTrue(tuning != null && tuning.tuned() == 1, "One injector should be tuned: " + tuning);
			helper.assertTrue(head.metering.getValue() < CylinderHeadBlockEntity.MAX_METERING,
				"Tuning should turn the shot down: " + head.metering.getValue());
		});
		helper.runAtTickTime(1500, () -> {
			CylinderStatus status = cylinder(helper);
			helper.assertTrue(status.mixture() > 0.5f && status.mixture() < 1.3f, "Mixture after tuning: " + status);
			helper.succeed();
		});
	}

	/** Tuning a stopped engine does nothing, and says so. */
	@GameTest(template = "empty")
	public static void tuningAStoppedEngineChangesNothing(GameTestHelper helper) {
		buildSingleCylinder(helper, true);
		helper.runAtTickTime(20, () -> {
			var tuning = TimingToolItem.tuneFuel(helper.getLevel(), helper.absolutePos(HEAD));
			helper.assertTrue(tuning != null && tuning.tuned() == 0, "Nothing to tune yet: " + tuning);
			helper.succeed();
		});
	}

	/** The Bank Builder lays a cylinder and a head on the chosen faces of every throw. */
	@GameTest(template = "empty")
	public static void theBankBuilderLaysOutAFlatAndAVeeBank(GameTestHelper helper) {
		for (int x = 1; x <= 5; x++)
			helper.setBlock(new BlockPos(x, 3, 3), (x % 2 == 0 ? CEBlocks.CRANK_THROW : CEBlocks.CRANKSHAFT).getDefaultState()
				.setValue(BlockStateProperties.AXIS, Axis.X));
		var player = helper.makeMockPlayer(GameType.CREATIVE);
		var flat = BankBuilderItem.build(helper.getLevel(), helper.absolutePos(new BlockPos(2, 3, 3)), Direction.UP,
			BankBuilderItem.Layout.FLAT, player, true);
		helper.assertTrue(flat.throwsFound() == 2 && flat.cylinders() == 4 && flat.heads() == 4, "A flat bank: " + flat);
		for (int x : new int[] {2, 4}) {
			helper.assertBlockState(new BlockPos(x, 4, 3), s -> s.getBlock() instanceof CylinderBlock
				&& s.getValue(CylinderBlock.FACING) == Direction.UP, () -> "Cylinder up on throw " + x);
			helper.assertBlockState(new BlockPos(x, 2, 3), s -> s.getBlock() instanceof CylinderBlock
				&& s.getValue(CylinderBlock.FACING) == Direction.DOWN, () -> "Cylinder down on throw " + x);
			helper.assertBlockState(new BlockPos(x, 5, 3), s -> s.getBlock() instanceof CylinderHeadBlock, () -> "Head up");
			helper.assertBlockState(new BlockPos(x, 1, 3), s -> s.getBlock() instanceof CylinderHeadBlock, () -> "Head down");
			helper.assertBlockState(new BlockPos(x, 4, 3),
				s -> s.getValue(CylinderBlock.HAS_PISTON) && s.getValue(CylinderBlock.HAS_ROD),
				() -> "A creative builder fits the piston and rod");
		}
		// Asking again is harmless: what is built is only filled in
		var again = BankBuilderItem.build(helper.getLevel(), helper.absolutePos(new BlockPos(2, 3, 3)), Direction.UP,
			BankBuilderItem.Layout.FLAT, player, true);
		helper.assertTrue(again.cylinders() == 0 && again.heads() == 0, "Nothing new to place: " + again);

		// A vee puts the second bank a quarter turn round
		var vee = BankBuilderItem.build(helper.getLevel(), helper.absolutePos(new BlockPos(2, 3, 3)), Direction.NORTH,
			BankBuilderItem.Layout.VEE, player, true);
		helper.assertTrue(vee.cylinders() >= 2, "Vee placed its new cylinders: " + vee);
		BlockPos second = new BlockPos(2, 3, 3).relative(Direction.NORTH.getClockWise(Axis.X));
		helper.assertBlockState(second, s -> s.getBlock() instanceof CylinderBlock, () -> "Vee second bank at " + second);
		helper.succeed();
	}

	/** Out of parts, a survival builder places only what it has the blocks for. */
	@GameTest(template = "empty")
	public static void theBankBuilderReportsWhatItIsMissing(GameTestHelper helper) {
		for (int x = 1; x <= 3; x++)
			helper.setBlock(new BlockPos(x, 3, 3), (x == 2 ? CEBlocks.CRANK_THROW : CEBlocks.CRANKSHAFT).getDefaultState()
				.setValue(BlockStateProperties.AXIS, Axis.X));
		var player = helper.makeMockPlayer(GameType.SURVIVAL);
		var result = BankBuilderItem.build(helper.getLevel(), helper.absolutePos(new BlockPos(2, 3, 3)), Direction.UP,
			BankBuilderItem.Layout.INLINE, player, false);
		helper.assertTrue(result.cylinders() == 0 && result.missing() == 1, "An empty inventory builds nothing: " + result);
		player.getInventory()
			.add(new ItemStack(CEBlocks.CYLINDER.get(), 1));
		player.getInventory()
			.add(new ItemStack(CEBlocks.CYLINDER_HEAD.get(), 1));
		var next = BankBuilderItem.build(helper.getLevel(), helper.absolutePos(new BlockPos(2, 3, 3)), Direction.UP,
			BankBuilderItem.Layout.INLINE, player, false);
		helper.assertTrue(next.cylinders() == 1 && next.heads() == 1 && next.missing() == 0,
			"With a cylinder and a head: " + next);
		helper.succeed();
	}

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
			helper.assertTrue(be.status() != null && be.status().vanes() > 0.2f,
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

	private static BlockState placePipe(GameTestHelper helper, ManifoldBlock block, BlockPos pos) {
		BlockPos absolute = helper.absolutePos(pos);
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(absolute), Direction.UP, absolute, false);
		BlockPlaceContext context = new BlockPlaceContext(helper.makeMockPlayer(GameType.CREATIVE), InteractionHand.MAIN_HAND,
			new ItemStack(block), hit);
		return block.getStateForPlacement(context);
	}

	/**
	 * The engine under load is tuned for it. It lugs down to low speed, where the default shot is lean: the charge
	 * burns slowly and badly, and the engine is rated for far less than it could carry on a bigger shot.
	 */
	private static void runWithPresses(GameTestHelper helper, int presses) {
		buildSingleCylinder(helper, true);
		CylinderHeadBlockEntity head = helper.getBlockEntity(HEAD);
		head.metering.setValue(CylinderHeadBlockEntity.DEFAULT_METERING * 14 / 10);
		helper.runAtTickTime(300, () -> helper.setBlock(STARTER_POWER, Blocks.AIR));
		helper.runAtTickTime(500, () -> {
			for (int i = 0; i < presses; i++)
				helper.setBlock(PRESS.east(i), AllBlocks.MECHANICAL_PRESS.getDefaultState()
					.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST));
		});
	}
}
