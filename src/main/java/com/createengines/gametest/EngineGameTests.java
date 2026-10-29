package com.createengines.gametest;

import java.util.Collection;
import java.util.function.BiConsumer;

import com.createengines.CreateEngines;
import com.createengines.content.airflow.ManifoldBlock;
import com.createengines.content.airflow.PortOpeningBlock;
import com.createengines.content.battery.BatteryBlockEntity;
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

	/** Three presses are more than it's rated for: Create overstresses the network, and the engine keeps running. */
	@GameTest(template = "empty", timeoutTicks = 1400)
	public static void overloadOverstressesWithoutStalling(GameTestHelper helper) {
		runWithPresses(helper, 3);
		helper.runAtTickTime(1000, () -> {
			CrankshaftBlockEntity crank = helper.getBlockEntity(PRIMARY);
			EngineSummary summary = summary(helper);
			helper.assertTrue(crank.isOverStressed(), "Three presses exceed the engine's rating");
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

	private static BlockState placePipe(GameTestHelper helper, ManifoldBlock block, BlockPos pos) {
		BlockPos absolute = helper.absolutePos(pos);
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(absolute), Direction.UP, absolute, false);
		BlockPlaceContext context = new BlockPlaceContext(helper.makeMockPlayer(GameType.CREATIVE), InteractionHand.MAIN_HAND,
			new ItemStack(block), hit);
		return block.getStateForPlacement(context);
	}

	private static void runWithPresses(GameTestHelper helper, int presses) {
		buildSingleCylinder(helper, true);
		helper.runAtTickTime(300, () -> helper.setBlock(STARTER_POWER, Blocks.AIR));
		helper.runAtTickTime(500, () -> {
			for (int i = 0; i < presses; i++)
				helper.setBlock(PRESS.east(i), AllBlocks.MECHANICAL_PRESS.getDefaultState()
					.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST));
		});
	}
}
