package com.createengines.gametest;

import java.util.Collection;
import java.util.function.BiConsumer;

import com.createengines.CreateEngines;
import com.createengines.content.airflow.HeatExchangerBlockEntity;
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
import com.createengines.content.starter.StarterBlock;
import com.createengines.content.starter.StarterBlockEntity;
import com.createengines.content.sump.SumpBlockEntity;
import com.createengines.content.transmission.Gears;
import com.createengines.content.transmission.TransmissionBlockEntity;
import com.createengines.content.blackbox.BlackBoxBlockEntity;
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
import com.createengines.registry.CEItems;
import com.simibubi.create.AllBlocks;
import com.tterrag.registrate.util.entry.RegistryEntry;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlock;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
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

	/** Once the redstone drops, the starter lets go of its input: a weak source such as a hand crank must not stay loaded. */
	@GameTest(template = "empty", timeoutTicks = 900)
	public static void starterReleasesItsInputWhenTheSignalDrops(GameTestHelper helper) {
		buildSingleCylinder(helper, false);
		helper.runAtTickTime(200, () -> {
			com.createengines.content.starter.StarterBlockEntity starter = helper.getBlockEntity(STARTER);
			helper.assertTrue(starter.calculateStressApplied() > 0, "Cranking should load the starter first");
			helper.setBlock(STARTER_POWER, Blocks.AIR);
		});
		helper.runAtTickTime(300, () -> {
			com.createengines.content.starter.StarterBlockEntity starter = helper.getBlockEntity(STARTER);
			helper.assertTrue(starter.calculateStressApplied() == 0,
				"A starter without a signal should put no stress on its input: " + starter.calculateStressApplied());
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

	/**
	 * A schematic cannon prices a block by its item alone unless the block says otherwise. Parts fitted into a cylinder,
	 * head or rotor housing are block state flags, so without a requirement of our own a finished engine would be built
	 * with its pistons, rods, injectors, plugs and rotors for free.
	 */
	@GameTest(template = "empty")
	public static void aSchematicOfAFittedEnginePricesItsParts(GameTestHelper helper) {
		BlockState cylinder = CEBlocks.CYLINDER.getDefaultState()
			.setValue(CylinderBlock.HAS_PISTON, true)
			.setValue(CylinderBlock.FORGED_PISTON, true)
			.setValue(CylinderBlock.HAS_ROD, true);
		assertPriced(helper, cylinder, CEBlocks.CYLINDER.asItem(), CEItems.FORGED_PISTON.get(),
			CEItems.CONNECTING_ROD.get());
		assertNotPriced(helper, cylinder, CEItems.PISTON.get());

		BlockState head = CEBlocks.CYLINDER_HEAD.getDefaultState()
			.setValue(CylinderHeadBlock.HAS_INJECTOR, true)
			.setValue(CylinderHeadBlock.HAS_SPARK_PLUG, true);
		assertPriced(helper, head, CEBlocks.CYLINDER_HEAD.asItem(), CEItems.FUEL_INJECTOR.get(),
			CEItems.SPARK_PLUG.get());
		assertNotPriced(helper, head, CEItems.PRECISION_INJECTOR.get());

		BlockState housing = CEBlocks.ROTOR_HOUSING.getDefaultState()
			.setValue(RotorHousingBlock.HAS_ROTOR, true)
			.setValue(RotorHousingBlock.HAS_INJECTOR, true)
			.setValue(RotorHousingBlock.FINE_INJECTOR, true);
		assertPriced(helper, housing, CEBlocks.ROTOR_HOUSING.asItem(), CEItems.ROTOR.get(),
			CEItems.PRECISION_INJECTOR.get());
		assertNotPriced(helper, housing, CEItems.SPARK_PLUG.get());

		// A bare housing costs the block and nothing else
		BlockState bare = CEBlocks.CYLINDER.getDefaultState();
		helper.assertTrue(com.simibubi.create.content.schematics.requirement.ItemRequirement.of(bare, null)
			.getRequiredItems()
			.size() == 1, "A bare cylinder should cost only itself");
		helper.succeed();
	}

	/** A comparator against an oil pan reads the tank its dial names: nothing when empty, 15 only when full. */
	@GameTest(template = "empty")
	public static void aSumpComparatorReadsTheTankItIsSetTo(GameTestHelper helper) {
		BlockPos sump = new BlockPos(3, 1, 2);
		helper.setBlock(sump, CEBlocks.SUMP.getDefaultState());
		SumpBlockEntity pan = helper.getBlockEntity(sump);
		BlockState state = helper.getBlockState(sump);
		BlockPos absolute = helper.absolutePos(sump);
		helper.assertTrue(state.getAnalogOutputSignal(helper.getLevel(), absolute) == 0, "An empty pan reads nothing");

		pan.fluidCapability()
			.fill(new FluidStack(CEFluids.ENGINE_OIL.get()
				.getSource(), 500), FluidAction.EXECUTE);
		int half = state.getAnalogOutputSignal(helper.getLevel(), absolute);
		helper.assertTrue(half > 1 && half < 15, "A half-full oil tank should read in between, read " + half);
		pan.fluidCapability()
			.fill(new FluidStack(CEFluids.ENGINE_OIL.get()
				.getSource(), 500), FluidAction.EXECUTE);
		helper.assertTrue(state.getAnalogOutputSignal(helper.getLevel(), absolute) == 15, "A full oil tank reads 15");

		// The dial moves the reading to another tank, which is still empty
		pan.signalTank.setValue(2);
		helper.assertTrue(state.getAnalogOutputSignal(helper.getLevel(), absolute) == 0,
			"Set to coolant it should read the empty coolant tank");
		pan.fluidCapability()
			.fill(new FluidStack(CEFluids.COOLANT.get()
				.getSource(), 500), FluidAction.EXECUTE);
		helper.assertTrue(state.getAnalogOutputSignal(helper.getLevel(), absolute) == 15,
			"Set to coolant it should read the full coolant tank");
		helper.succeed();
	}

	/**
	 * A vanilla comparator beside an oil pan reads the tank its dial names, so a lamp it feeds lights once the pan holds oil.
	 * A comparator's facing is its input side, so it faces the pan and the lamp sits behind it.
	 */
	@GameTest(template = "empty", timeoutTicks = 200)
	public static void aComparatorBesideAnOilPanLightsALampOnceItHoldsOil(GameTestHelper helper) {
		BlockPos pan = new BlockPos(2, 1, 2);
		BlockPos lamp = new BlockPos(4, 1, 2);
		helper.setBlock(pan, CEBlocks.SUMP.getDefaultState());
		helper.setBlock(new BlockPos(3, 0, 2), Blocks.STONE);
		helper.setBlock(new BlockPos(3, 1, 2), Blocks.COMPARATOR.defaultBlockState()
			.setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING, Direction.WEST));
		helper.setBlock(lamp, Blocks.REDSTONE_LAMP);
		helper.runAtTickTime(5, () -> {
			helper.assertBlockProperty(lamp, BlockStateProperties.LIT, false);
			SumpBlockEntity sump = helper.getBlockEntity(pan);
			sump.fluidCapability()
				.fill(new FluidStack(CEFluids.ENGINE_OIL.get()
					.getSource(), 500), FluidAction.EXECUTE);
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(helper.getTick() > 5, "Not filled yet");
			helper.assertBlockProperty(lamp, BlockStateProperties.LIT, true);
		});
	}

	/** Every crankshaft block offers the engine's readouts to a Display Link, and the oil pan its levels. */
	@GameTest(template = "empty")
	public static void displayLinksCanReadAnEngineAndItsPan(GameTestHelper helper) {
		buildSingleCylinder(helper, true);
		BlockPos sump = new BlockPos(3, 1, 2);
		helper.setBlock(sump, CEBlocks.SUMP.getDefaultState());
		var onCrank = com.simibubi.create.api.behaviour.display.DisplaySource.getAll(helper.getLevel(),
			helper.absolutePos(PRIMARY));
		for (var source : java.util.List.of(com.createengines.registry.CEDisplaySources.ENGINE_STATUS,
			com.createengines.registry.CEDisplaySources.ENGINE_SPEED, com.createengines.registry.CEDisplaySources.ENGINE_LOAD,
			com.createengines.registry.CEDisplaySources.ENGINE_TEMPERATURE,
			com.createengines.registry.CEDisplaySources.ENGINE_OIL))
			helper.assertTrue(onCrank.contains(source.get()), "A crankshaft should offer " + source.getId());
		helper.assertTrue(com.simibubi.create.api.behaviour.display.DisplaySource
			.getAll(helper.getLevel(), helper.absolutePos(sump))
			.contains(com.createengines.registry.CEDisplaySources.SUMP_LEVELS.get()), "An oil pan should offer its levels");

		// The figures are shares of what the engine can give: flat out is 1, overloaded is more
		var overloaded = new EngineSummary(EngineState.OVERLOADED, 128f, 100f, 150f, 0f, 0f, 1, 1, 1f, 1f, false,
			new float[0]);
		helper.assertTrue(Math.abs(com.createengines.content.display.EngineReadout.load(overloaded) - 1.5f) < 1e-4,
			"150 asked of 100 available is a load of 1.5");
		var idle = new EngineSummary(EngineState.STALLED, 0f, 100f, 0f, 0f, 0f, 0, 1, 1f, 1f, false, new float[0]);
		helper.assertTrue(com.createengines.content.display.EngineReadout.load(idle) == 0f, "Nothing asked is a load of 0");
		helper.succeed();
	}

	/** A boiler reads an exhaust heat exchanger as it reads a blaze burner: kindled is one level, seething two, cold none. */
	@GameTest(template = "empty")
	public static void aBoilerReadsTheHeatExchangerLikeABlazeBurner(GameTestHelper helper) {
		BlockPos pos = new BlockPos(2, 1, 2);
		BlockPos absolute = helper.absolutePos(pos);
		for (var level : java.util.Map.of(com.simibubi.create.content.processing.burner.BlazeBurnerBlock.HeatLevel.NONE, -1f,
			com.simibubi.create.content.processing.burner.BlazeBurnerBlock.HeatLevel.KINDLED, 1f,
			com.simibubi.create.content.processing.burner.BlazeBurnerBlock.HeatLevel.SEETHING, 2f)
			.entrySet()) {
			BlockState state = CEBlocks.HEAT_EXCHANGER.getDefaultState()
				.setValue(com.simibubi.create.content.processing.burner.BlazeBurnerBlock.HEAT_LEVEL, level.getKey());
			float heat = com.simibubi.create.api.boiler.BoilerHeater.findHeat(helper.getLevel(), absolute, state);
			helper.assertTrue(heat == level.getValue(), "A " + level.getKey() + " exchanger should give a boiler "
				+ level.getValue() + " but gives " + heat);
		}
		helper.succeed();
	}

	/**
	 * A muffler in an exhaust run mutes the engine, and nothing else: the exhaust still has its way out. The engine has
	 * no muffler to begin with and all of its exhaust goes through the one set in front of its outlet.
	 */
	@GameTest(template = "empty", timeoutTicks = 200)
	public static void aMufflerInTheExhaustRunMutesTheEngine(GameTestHelper helper) {
		buildSingleCylinder(helper, true);
		helper.runAtTickTime(60, () -> {
			helper.assertTrue(summary(helper).muffling() == 0f, "An engine with no muffler is not muffled: " + summary(helper));
			// The outlet moves out one block and a muffler takes its place against the head
			helper.setBlock(TestEngine.EXHAUST.west(), CEBlocks.EXHAUST_OUTLET.getDefaultState()
				.setValue(com.createengines.content.airflow.PortOpeningBlock.FACING, Direction.WEST));
			helper.setBlock(TestEngine.EXHAUST, CEBlocks.MUFFLER.getDefaultState()
				.setValue(BlockStateProperties.AXIS, Axis.X));
		});
		helper.runAtTickTime(120, () -> {
			EngineSummary summary = summary(helper);
			helper.assertTrue(summary.muffling() == 1f, "All of its exhaust goes through the muffler: " + summary);
			helper.assertTrue(!cylinder(helper).exhaustOutlets()
				.isEmpty(), "The exhaust still reaches an outlet through the muffler");
			helper.succeed();
		});
	}

	private static void assertPriced(GameTestHelper helper, BlockState state, Item... items) {
		var needed = com.simibubi.create.content.schematics.requirement.ItemRequirement.of(state, null)
			.getRequiredItems();
		for (Item item : items)
			helper.assertTrue(needed.stream()
				.anyMatch(requirement -> requirement.stack.is(item)),
				state + " should cost " + item + ", costs " + needed.stream()
					.map(requirement -> requirement.stack.getItem())
					.toList());
	}

	private static void assertNotPriced(GameTestHelper helper, BlockState state, Item item) {
		helper.assertTrue(com.simibubi.create.content.schematics.requirement.ItemRequirement.of(state, null)
			.getRequiredItems()
			.stream()
			.noneMatch(requirement -> requirement.stack.is(item)), state + " should not cost " + item);
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

	/** Water held against an opening's mouth by glass on the sides it would run off to. */
	private static void flood(GameTestHelper helper, BlockPos mouth) {
		for (Direction side : new Direction[] {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.DOWN})
			if (helper.getBlockState(mouth.relative(side))
				.isAir())
				helper.setBlock(mouth.relative(side), Blocks.GLASS);
		helper.setBlock(mouth, Blocks.WATER);
	}

	/**
	 * An air intake that opens underwater draws no air, so nothing in the cylinder can burn however long the starter
	 * turns it, and the cylinder says the intake is flooded rather than covered. Drained, the engine starts.
	 */
	@GameTest(template = "empty", timeoutTicks = 1500)
	public static void floodedIntakeNeverFiresUntilItIsDrained(GameTestHelper helper) {
		buildSingleCylinder(helper, true);
		BlockPos mouth = TestEngine.AIR_INTAKE.north();
		flood(helper, mouth);
		for (int tick = 20; tick <= 400; tick += 20)
			helper.runAtTickTime(tick, () -> {
				EngineSummary summary = summary(helper);
				helper.assertTrue(summary.firing() == 0, "Cylinder with its intake underwater fired: " + summary);
				CrankshaftBlockEntity crank = helper.getBlockEntity(PRIMARY);
				helper.assertTrue(crank.publishedCapacity() == 0, "Engine shouldn't provide stress capacity");
			});
		helper.runAtTickTime(420, () -> {
			int faults = cylinder(helper).faults();
			helper.assertTrue(CylinderFault.INTAKE_FLOODED.in(faults), "Cylinder should report its flooded intake");
			helper.assertFalse(CylinderFault.INTAKE_BLOCKED.in(faults), "A flooded intake is not a covered one");
			helper.setBlock(mouth, Blocks.AIR);
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(helper.getTick() > 440, "Intake not drained yet");
			helper.assertFalse(CylinderFault.intakeShut(cylinder(helper).faults()),
				"A drained intake should be open again: " + cylinder(helper));
			helper.assertTrue(summary(helper).firing() == 1, "Engine should fire once it has air: " + summary(helper));
		});
	}

	/**
	 * No block of the mod is washed away by water flowing onto it: every one counts as solid, though a shaft or a pipe
	 * has too slight a shape to be so unasked. A crankshaft journal with a spring poured on it is still there.
	 */
	@GameTest(template = "empty", timeoutTicks = 200)
	public static void waterWashesNoEngineBlockAway(GameTestHelper helper) {
		for (Block block : net.minecraft.core.registries.BuiltInRegistries.BLOCK)
			if (net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block)
				.getNamespace()
				.equals(CreateEngines.MOD_ID) && !(block instanceof net.minecraft.world.level.block.LiquidBlock))
				helper.assertTrue(block.defaultBlockState()
					.blocksMotion(), block + " would be washed away by flowing water");
		BlockPos journal = new BlockPos(2, 2, 2);
		helper.setBlock(journal, CEBlocks.CRANKSHAFT.getDefaultState());
		helper.setBlock(journal.above(), Blocks.WATER);
		helper.runAtTickTime(100, () -> {
			helper.assertBlockPresent(CEBlocks.CRANKSHAFT.get(), journal);
			helper.setBlock(journal.above(), Blocks.AIR);
			helper.succeed();
		});
	}

	/**
	 * Water against the cylinder itself: the engine is underwater, and it never fires although its intake and exhaust
	 * both open in clear air. The cylinder says it is submerged, not that a port is flooded. Drained, the engine starts.
	 */
	@GameTest(template = "empty", timeoutTicks = 1500)
	public static void submergedCylinderNeverFiresWhateverItsPipesReach(GameTestHelper helper) {
		buildSingleCylinder(helper, true);
		BlockPos beside = TestEngine.CYLINDER.east();
		flood(helper, beside);
		for (int tick = 20; tick <= 400; tick += 20)
			helper.runAtTickTime(tick, () -> {
				EngineSummary summary = summary(helper);
				helper.assertTrue(summary.firing() == 0, "Cylinder sitting in water fired: " + summary);
				CrankshaftBlockEntity crank = helper.getBlockEntity(PRIMARY);
				helper.assertTrue(crank.publishedCapacity() == 0, "Engine shouldn't provide stress capacity");
			});
		helper.runAtTickTime(420, () -> {
			int faults = cylinder(helper).faults();
			helper.assertTrue(CylinderFault.SUBMERGED.in(faults), "Cylinder should report that it is underwater");
			helper.assertFalse(CylinderFault.INTAKE_FLOODED.in(faults) || CylinderFault.EXHAUST_FLOODED.in(faults),
				"Its intake and exhaust open in clear air");
			helper.setBlock(beside, Blocks.AIR);
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(helper.getTick() > 440, "Not drained yet");
			helper.assertFalse(CylinderFault.underwater(cylinder(helper).faults()),
				"A drained engine is no longer underwater: " + cylinder(helper));
			helper.assertTrue(summary(helper).firing() == 1, "Engine should fire once it is dry: " + summary(helper));
		});
	}

	/**
	 * Water against the head of a running engine stops it, though the head's intake and exhaust stay clear: an engine
	 * that sinks dies.
	 */
	@GameTest(template = "empty", timeoutTicks = 3000)
	public static void sinkingARunningEngineStallsIt(GameTestHelper helper) {
		buildSingleCylinder(helper, true);
		helper.runAtTickTime(300, () -> helper.setBlock(STARTER_POWER, Blocks.AIR));
		helper.runAtTickTime(500, () -> {
			helper.assertTrue(summary(helper).state() == EngineState.RUNNING, "Engine should be running first");
			flood(helper, TestEngine.HEAD.east());
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(helper.getTick() > 520, "Not sunk yet");
			helper.assertTrue(CylinderFault.SUBMERGED.in(cylinder(helper).faults()),
				"Cylinder should report that it is underwater: " + cylinder(helper));
			CrankshaftBlockEntity crank = helper.getBlockEntity(PRIMARY);
			helper.assertTrue(crank.getSpeed() == 0, "An engine underwater should stall, speed " + crank.getSpeed());
		});
	}

	/**
	 * An engine in a dry pocket under water keeps running: water a block away, behind glass, touches nothing of it. Only
	 * fluid against a face counts, and that is the ruling for 1.0.
	 */
	@GameTest(template = "empty", timeoutTicks = 3000)
	public static void anEngineInADryPocketKeepsRunning(GameTestHelper helper) {
		buildSingleCylinder(helper, true);
		helper.runAtTickTime(300, () -> helper.setBlock(STARTER_POWER, Blocks.AIR));
		helper.runAtTickTime(500, () -> {
			helper.assertTrue(summary(helper).state() == EngineState.RUNNING, "Engine should be running first");
			flood(helper, TestEngine.HEAD.east().east());
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(helper.getTick() > 700, "Not long enough in the pocket yet");
			helper.assertFalse(CylinderFault.underwater(cylinder(helper).faults()),
				"Water a block away touches nothing: " + cylinder(helper));
			helper.assertTrue(summary(helper).state() == EngineState.RUNNING, "Engine should still run: " + summary(helper));
		});
	}

	/** Water over the exhaust outlet of a running engine: the gas cannot get out against it, and the engine chokes. */
	@GameTest(template = "empty", timeoutTicks = 3000)
	public static void floodingTheExhaustStallsARunningEngine(GameTestHelper helper) {
		buildSingleCylinder(helper, true);
		helper.runAtTickTime(300, () -> helper.setBlock(STARTER_POWER, Blocks.AIR));
		helper.runAtTickTime(500, () -> {
			helper.assertTrue(summary(helper).state() == EngineState.RUNNING, "Engine should be running first");
			flood(helper, EXHAUST.west());
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(helper.getTick() > 520, "Exhaust not flooded yet");
			helper.assertTrue(CylinderFault.EXHAUST_FLOODED.in(cylinder(helper).faults()),
				"Cylinder should report its flooded exhaust: " + cylinder(helper));
			CrankshaftBlockEntity crank = helper.getBlockEntity(PRIMARY);
			helper.assertTrue(crank.getSpeed() == 0, "Engine that can't exhaust should stall, speed " + crank.getSpeed());
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

	/**
	 * A cam in the valley of a vee: a second cylinder on the same throw, a quarter turn from the first, and the one lobe
	 * between the two cylinder blocks working both. The lobe is set for the first cylinder only; the second has to follow
	 * its own piston, so both are on time.
	 */
	@GameTest(template = "empty", timeoutTicks = 900)
	public static void aValleyLobeTimesBothBanksOfAVee(GameTestHelper helper) {
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

		BlockPos second = new BlockPos(2, 2, 3);
		helper.setBlock(second, CEBlocks.CYLINDER.getDefaultState()
			.setValue(CylinderBlock.FACING, Direction.SOUTH)
			.setValue(CylinderBlock.HAS_PISTON, true)
			.setValue(CylinderBlock.HAS_ROD, true));
		helper.setBlock(new BlockPos(2, 2, 4), CEBlocks.CYLINDER_HEAD.getDefaultState()
			.setValue(CylinderHeadBlock.FACING, Direction.SOUTH)
			.setValue(CylinderHeadBlock.HAS_INJECTOR, true));
		helper.setBlock(new BlockPos(1, 2, 4), CEBlocks.AIR_INTAKE.getDefaultState()
			.setValue(PortOpeningBlock.FACING, Direction.WEST));
		helper.setBlock(new BlockPos(3, 2, 4), CEBlocks.EXHAUST_OUTLET.getDefaultState()
			.setValue(PortOpeningBlock.FACING, Direction.EAST));

		helper.runAtTickTime(400, () -> {
			CylinderStatus first = cylinder(helper);
			CylinderBlockEntity other = helper.getBlockEntity(second);
			CylinderStatus follower = other.status();
			helper.assertTrue(follower != null, "The second cylinder should be part of the engine");
			helper.assertTrue(first.lobe() != null && first.lobe()
				.equals(follower.lobe()), "One lobe should work both cylinders: " + first + " and " + follower);
			helper.assertTrue(Math.abs(first.timingError()) < 1, "The first is timed by the lobe's angle: " + first);
			helper.assertTrue(Math.abs(follower.timingError()) < 1,
				"The second follows its own piston, so it is on time too: " + follower);
			helper.assertTrue(Math.abs(follower.lobeShift()) > 1 && first.lobeShift() == 0,
				"Only the second is shifted round the lobe: " + first.lobeShift() + ", " + follower.lobeShift());
			helper.assertTrue(summary(helper).firing() == 2, "Both banks should fire: " + summary(helper));
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
				.fill(new FluidStack(CEFluids.GASOLINE.get()
					.getSource(), 500), FluidAction.EXECUTE) == 0, "It shouldn't take a fuel that is no pilot fuel");
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

	/** Coolant is spent carrying heat, so a pan filled once runs down while the engine works and has to be fed. */
	@GameTest(template = "empty", timeoutTicks = 1700)
	public static void aRunningEngineSpendsTheCoolantInItsPan(GameTestHelper helper) {
		TestEngine.build(helper.getLevel(), helper.absolutePos(BlockPos.ZERO), true, false);
		BlockPos sump = new BlockPos(3, 1, 2);
		helper.setBlock(sump, CEBlocks.SUMP.getDefaultState());
		helper.runAtTickTime(20, () -> {
			SumpBlockEntity pan = helper.getBlockEntity(sump);
			pan.fluidCapability()
				.fill(new FluidStack(CEFluids.COOLANT.get()
					.getSource(), 500), FluidAction.EXECUTE);
			pan.fluidCapability()
				.fill(new FluidStack(CEFluids.ENGINE_OIL.get()
					.getSource(), 500), FluidAction.EXECUTE);
		});
		helper.runAtTickTime(300, () -> helper.setBlock(STARTER_POWER, Blocks.AIR));
		int[] atStart = new int[1];
		helper.runAtTickTime(400, () -> atStart[0] = ((SumpBlockEntity) helper.getBlockEntity(sump)).coolantTank()
			.getFluidAmount());
		helper.runAtTickTime(1600, () -> {
			SumpBlockEntity pan = helper.getBlockEntity(sump);
			int left = pan.coolantTank()
				.getFluidAmount();
			helper.assertTrue(summary(helper).state() == EngineState.RUNNING, "The engine should be running: " + summary(helper));
			helper.assertTrue(atStart[0] - left >= 10,
				"A minute of running should use up coolant, but the pan went from " + atStart[0] + " to " + left + " mB");
			helper.assertTrue(left > 0, "And not all of it in a minute");
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
			helper.assertTrue(summary.rpm() < 127,
				"A dry engine should top out well below an oiled one's 150+: " + summary);
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
			com.createengines.engine.sim.Tuning.heatRecoveryEnabled = false;
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
				com.createengines.engine.sim.Tuning.heatRecoveryEnabled = true;
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

	/** A redstone signal selects the gear over the panel, and the panel takes over again without one. */
	@GameTest(template = "empty", timeoutTicks = 200)
	public static void redstoneOverridesTheTransmissionPanel(GameTestHelper helper) {
		buildTransmission(helper, Gears.FIRST);
		BlockPos power = new BlockPos(1, 2, 1);
		helper.runAtTickTime(20, () -> helper.assertTrue(Math.abs(outputSpeed(helper) - 5.333f) < 0.1f,
			"The panel's first gear should be in use: " + outputSpeed(helper)));
		helper.runAtTickTime(25, () -> helper.setBlock(power, Blocks.REDSTONE_BLOCK));
		helper.runAtTickTime(45, () -> helper.assertTrue(outputSpeed(helper) < -20,
			"A strong signal selects reverse: " + outputSpeed(helper)));
		helper.runAtTickTime(50, () -> helper.setBlock(power, Blocks.AIR));
		helper.runAtTickTime(70, () -> {
			helper.assertTrue(Math.abs(outputSpeed(helper) - 5.333f) < 0.1f,
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

	private static void placePresses(GameTestHelper helper) {
		for (int i = 0; i < 3; i++)
			helper.setBlock(PRESS.east(1 + i), AllBlocks.MECHANICAL_PRESS.getDefaultState()
				.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST));
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
	@GameTest(template = "empty", timeoutTicks = 4000)
	public static void anAutoTransmissionDownshiftsOutOfAnOverload(GameTestHelper helper) {
		buildSingleCylinder(helper, true);
		helper.setBlock(PRESS, CEBlocks.TRANSMISSION.getDefaultState()
			.setValue(BlockStateProperties.AXIS, Axis.X));
		TransmissionBlockEntity box = helper.getBlockEntity(PRESS);
		box.panel.setValue(Gears.TOP);
		helper.runAtTickTime(300, () -> helper.setBlock(STARTER_POWER, Blocks.AIR));
		helper.runAtTickTime(500, () -> placePresses(helper));
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

	/** A precision build places precision parts, and swaps them in for standard ones, handing the old ones back. */
	@GameTest(template = "empty")
	public static void thePrecisionBankBuilderSwapsInPrecisionParts(GameTestHelper helper) {
		for (int x = 1; x <= 3; x++)
			helper.setBlock(new BlockPos(x, 3, 3), (x == 2 ? CEBlocks.CRANK_THROW : CEBlocks.CRANKSHAFT).getDefaultState()
				.setValue(BlockStateProperties.AXIS, Axis.X));
		BlockPos throwPos = new BlockPos(2, 3, 3);
		var creative = helper.makeMockPlayer(GameType.CREATIVE);
		BankBuilderItem.build(helper.getLevel(), helper.absolutePos(throwPos), Direction.UP,
			BankBuilderItem.Layout.INLINE, creative, true);
		helper.assertBlockState(new BlockPos(2, 4, 3), s -> s.getBlock() == CEBlocks.CYLINDER.get(),
			() -> "A standard build places the standard cylinder");

		var player = helper.makeMockPlayer(GameType.SURVIVAL);
		for (var block : new Block[] {CEBlocks.PRECISION_CYLINDER.get(), CEBlocks.PRECISION_CYLINDER_HEAD.get()})
			player.getInventory()
				.add(new ItemStack(block, 1));
		player.getInventory()
			.add(CEItems.FORGED_PISTON.asStack());
		player.getInventory()
			.add(CEItems.PRECISION_INJECTOR.asStack());
		var result = BankBuilderItem.build(helper.getLevel(), helper.absolutePos(throwPos), Direction.UP,
			BankBuilderItem.Layout.INLINE, true, player, false);
		helper.assertTrue(result.upgraded() == 4, "Cylinder, head, piston and injector swapped: " + result);
		helper.assertBlockState(new BlockPos(2, 4, 3), s -> s.getBlock() == CEBlocks.PRECISION_CYLINDER.get()
			&& s.getValue(CylinderBlock.FORGED_PISTON) && s.getValue(CylinderBlock.HAS_ROD),
			() -> "The cylinder is precision with a forged piston and its rod kept");
		helper.assertBlockState(new BlockPos(2, 5, 3), s -> s.getBlock() == CEBlocks.PRECISION_CYLINDER_HEAD.get()
			&& s.getValue(CylinderHeadBlock.FINE_INJECTOR), () -> "The head is precision with a precision injector");
		helper.assertTrue(player.getInventory()
			.contains(new ItemStack(CEBlocks.CYLINDER.get())), "The standard cylinder came back");
		helper.assertTrue(player.getInventory()
			.contains(CEItems.PISTON.asStack()), "And so did the standard piston");
		helper.succeed();
	}

	/**
	 * A builder carrying spark plugs fits them in the heads, and one carrying a camshaft and cam lobes lays a cam line
	 * beside the bank; a creative builder with none of either does neither, so a diesel stays a diesel. An inline bank
	 * gets a lobe beside each cylinder block and camshaft blocks in the gaps, and a vee a lobe in its valley.
	 */
	@GameTest(template = "empty")
	public static void theBankBuilderLaysPlugsAndCamsOnlyIfItIsCarryingThem(GameTestHelper helper) {
		for (int x = 1; x <= 5; x++)
			helper.setBlock(new BlockPos(x, 3, 3), (x == 2 || x == 4 ? CEBlocks.CRANK_THROW : CEBlocks.CRANKSHAFT)
				.getDefaultState()
				.setValue(BlockStateProperties.AXIS, Axis.X));
		Direction beside = Direction.UP.getClockWise(Axis.X);
		var creative = helper.makeMockPlayer(GameType.CREATIVE);

		var plain = BankBuilderItem.build(helper.getLevel(), helper.absolutePos(new BlockPos(2, 3, 3)), Direction.UP,
			BankBuilderItem.Layout.INLINE, creative, true);
		helper.assertTrue(plain.cylinders() == 2 && plain.camBlocks() == 0, "Nothing carried, no cams laid: " + plain);
		helper.assertBlockState(new BlockPos(2, 5, 3), s -> s.getBlock() instanceof CylinderHeadBlock
			&& !s.getValue(CylinderHeadBlock.HAS_SPARK_PLUG), () -> "A diesel head has no plug");

		creative.getInventory()
			.add(new ItemStack(CEItems.SPARK_PLUG.get(), 2));
		creative.getInventory()
			.add(new ItemStack(CEBlocks.CAM_LOBE.get(), 2));
		creative.getInventory()
			.add(new ItemStack(CEBlocks.CAMSHAFT.get(), 1));
		var cammed = BankBuilderItem.build(helper.getLevel(), helper.absolutePos(new BlockPos(2, 3, 3)), Direction.UP,
			BankBuilderItem.Layout.INLINE, creative, true);
		helper.assertTrue(cammed.camBlocks() == 3, "Two lobes and the camshaft between them: " + cammed);
		for (int x : new int[] {2, 4}) {
			helper.assertBlockState(new BlockPos(x, 5, 3), s -> s.getValue(CylinderHeadBlock.HAS_SPARK_PLUG),
				() -> "The carried plugs were fitted");
			helper.assertBlockState(new BlockPos(x, 3, 3).relative(Direction.UP)
				.relative(beside), s -> s.getBlock() == CEBlocks.CAM_LOBE.get() && s.getValue(BlockStateProperties.AXIS) == Axis.X,
				() -> "A lobe beside the cylinder block at x=" + x);
		}
		helper.assertBlockState(new BlockPos(3, 3, 3).relative(Direction.UP)
			.relative(beside), s -> s.getBlock() == CEBlocks.CAMSHAFT.get(), () -> "A camshaft block joins the lobes");
		helper.succeed();
	}

	/** A vee has its one lobe in the valley, which touches the cylinder block of both banks. */
	@GameTest(template = "empty")
	public static void theBankBuilderPutsAVeesLobeInItsValley(GameTestHelper helper) {
		for (int x = 1; x <= 5; x++)
			helper.setBlock(new BlockPos(x, 3, 3), (x == 2 || x == 4 ? CEBlocks.CRANK_THROW : CEBlocks.CRANKSHAFT)
				.getDefaultState()
				.setValue(BlockStateProperties.AXIS, Axis.X));
		Direction beside = Direction.UP.getClockWise(Axis.X);
		var creative = helper.makeMockPlayer(GameType.CREATIVE);
		creative.getInventory()
			.add(new ItemStack(CEBlocks.CAM_LOBE.get(), 2));
		creative.getInventory()
			.add(new ItemStack(CEBlocks.CAMSHAFT.get(), 1));
		var vee = BankBuilderItem.build(helper.getLevel(), helper.absolutePos(new BlockPos(2, 3, 3)), Direction.UP,
			BankBuilderItem.Layout.VEE, creative, true);
		helper.assertTrue(vee.cylinders() == 4 && vee.camBlocks() == 3, "Two banks and one cam line: " + vee);
		for (int x : new int[] {2, 4}) {
			BlockPos valley = new BlockPos(x, 3, 3).relative(Direction.UP)
				.relative(beside);
			helper.assertBlockState(valley, s -> s.getBlock() == CEBlocks.CAM_LOBE.get(), () -> "A lobe in the valley at x=" + x);
			helper.assertBlockState(new BlockPos(x, 3, 3).relative(Direction.UP), s -> s.getBlock() instanceof CylinderBlock,
				() -> "It touches the first bank at x=" + x);
			helper.assertBlockState(new BlockPos(x, 3, 3).relative(beside), s -> s.getBlock() instanceof CylinderBlock,
				() -> "And the second at x=" + x);
		}
		helper.succeed();
	}

	/**
	 * Placing a cogwheel that gears two cranks together couples them at once. Create's blocks do not tell the engine
	 * scanner they were placed, so without the hook the coupling waited for the next revalidation, up to five seconds on.
	 */
	@GameTest(template = "empty", timeoutTicks = 200)
	public static void aCogPlacedBetweenTwoCranksCouplesThemAtOnce(GameTestHelper helper) {
		buildSingleCylinder(helper, true);
		TestEngine.addCoupledLine(helper.getLevel(), helper.absolutePos(BlockPos.ZERO));
		BlockPos gap = new BlockPos(0, 2, 3);
		helper.setBlock(gap, Blocks.AIR);
		helper.runAtTickTime(20, () -> {
			EngineLayout apart = EngineScanner.scan(helper.getLevel(), helper.absolutePos(PRIMARY));
			helper.assertTrue(apart != null && apart.lines() == 1, "Without the middle cog the lines are separate");
			BlockState cog = alongX(AllBlocks.COGWHEEL.getDefaultState());
			helper.setBlock(gap, cog);
			com.createengines.engine.topology.EngineTopology.gearingChanged(helper.getLevel(), helper.absolutePos(gap), cog);
		});
		// Four ticks later, far inside the revalidation interval: the engine has been rescanned and has both lines
		helper.runAtTickTime(24, () -> {
			CrankshaftBlockEntity first = helper.getBlockEntity(PRIMARY);
			helper.assertTrue(first.controller() != null && first.controller()
				.layout()
				.lines() == 2, "The engine should already have both lines: "
					+ (first.controller() == null ? null : first.controller()
						.layout()
						.lines()));
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

	/** A fluid in a common fuel tag runs an engine with no datapack; our own fuels sit in those tags for other mods' machines. */
	@GameTest(template = "empty")
	public static void fuelsFromOtherModsRunByTheirCommonTags(GameTestHelper helper) {
		var tags = new Object() {
			boolean has(net.minecraft.world.level.material.Fluid fluid, net.minecraft.tags.TagKey<net.minecraft.world.level.material.Fluid> tag) {
				return fluid.builtInRegistryHolder()
					.is(tag);
			}
		};
		helper.assertTrue(tags.has(CEFluids.DIESEL.get(), com.createengines.registry.CEDataMaps.DIESEL), "Our diesel is c:diesel");
		helper.assertTrue(tags.has(CEFluids.GASOLINE.get(), com.createengines.registry.CEDataMaps.GASOLINE), "Our gasoline is c:gasoline");
		helper.assertTrue(tags.has(CEFluids.ETHANOL.get(), com.createengines.registry.CEDataMaps.ETHANOL), "Our ethanol is c:ethanol");
		helper.assertTrue(tags.has(CEFluids.PLANT_OIL.get(), com.createengines.registry.CEDataMaps.PLANT_OIL),
			"Our plant oil is c:plantoil");
		helper.assertTrue(com.createengines.registry.CEDataMaps.fuelForTags(t -> t.equals(com.createengines.registry.CEDataMaps.GASOLINE))
			== com.createengines.engine.sim.FuelSpec.GASOLINE, "Another mod's gasoline burns as gasoline");
		helper.assertTrue(com.createengines.registry.CEDataMaps.fuelForTags(t -> t.equals(com.createengines.registry.CEDataMaps.BIODIESEL))
			== com.createengines.engine.sim.FuelSpec.DIESEL, "Biodiesel burns as diesel");
		helper.assertTrue(com.createengines.registry.CEDataMaps.fuelForTags(t -> t.equals(com.createengines.registry.CEDataMaps.ETHANOL))
			== com.createengines.engine.sim.FuelSpec.ETHANOL, "Ethanol burns as ethanol");
		helper.assertTrue(com.createengines.registry.CEDataMaps.fuelForTags(t -> t.equals(com.createengines.registry.CEDataMaps.PLANT_OIL))
			== com.createengines.engine.sim.FuelSpec.PLANT_OIL, "Plant oil burns as plant oil");
		helper.assertTrue(com.createengines.registry.CEDataMaps.fuelForTags(t -> false) == null, "A fluid in no fuel tag is no fuel");
		helper.assertTrue(com.createengines.registry.CEDataMaps.fuel(net.minecraft.world.level.material.Fluids.WATER) == null,
			"Water is no fuel");
		helper.assertTrue(com.createengines.registry.CEDataMaps.fuel(CEFluids.COOLANT.get()) == null, "Nor is coolant");
		var diesel = com.createengines.registry.CEDataMaps.fuel(CEFluids.DIESEL.get());
		helper.assertTrue(diesel != null && diesel.energyPerMb() == 36 && diesel.autoignitionTemp() == 1.55,
			"A data map entry still wins over the tag: " + diesel);
		helper.succeed();
	}

	/** An alternator on a turning shaft offers Forge Energy, and only feels stress for it while something draws it. */
	@GameTest(template = "empty", timeoutTicks = 400)
	public static void anAlternatorMakesForgeEnergyForWhatDrawsIt(GameTestHelper helper) {
		BlockPos motor = new BlockPos(1, 1, 1);
		BlockPos alternator = new BlockPos(2, 1, 1);
		helper.setBlock(motor, com.simibubi.create.AllBlocks.CREATIVE_MOTOR.getDefaultState()
			.setValue(CreativeMotorBlock.FACING, Direction.EAST));
		CreativeMotorBlockEntity drive = helper.getBlockEntity(motor);
		drive.generatedSpeed.setValue(64);
		helper.setBlock(alternator, CEBlocks.ALTERNATOR.getDefaultState()
			.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.AXIS, Direction.Axis.X));
		int[] taken = new int[1];
		helper.runAtTickTime(80, () -> {
			com.createengines.content.alternator.AlternatorBlockEntity be = helper.getBlockEntity(alternator);
			helper.assertTrue(Math.abs(be.getSpeed()) > 0, "The alternator should be turning: " + be.getSpeed());
			var energy = be.energyOutput();
			helper.assertTrue(energy.canExtract() && !energy.canReceive(), "It gives energy and takes none");
			helper.assertTrue(energy.receiveEnergy(1000, false) == 0, "Nothing can be put in");
			helper.assertTrue(energy.getEnergyStored() > 0, "The buffer should have filled from the shaft");
			helper.assertTrue(be.calculateStressApplied() == 0, "Nothing is drawing it yet, so the shaft feels nothing");
		});
		for (int tick = 90; tick < 110; tick++)
			helper.runAtTickTime(tick, () -> {
				com.createengines.content.alternator.AlternatorBlockEntity be = helper.getBlockEntity(alternator);
				taken[0] += be.energyOutput()
					.extractEnergy(100000, false);
			});
		helper.runAtTickTime(112, () -> {
			com.createengines.content.alternator.AlternatorBlockEntity be = helper.getBlockEntity(alternator);
			helper.assertTrue(taken[0] > 1000, "A machine drawing every tick should get a steady supply: " + taken[0]);
			helper.assertTrue(be.calculateStressApplied() > 0, "And the shaft should feel the load");
		});
		helper.runAtTickTime(200, () -> {
			com.createengines.content.alternator.AlternatorBlockEntity be = helper.getBlockEntity(alternator);
			helper.assertTrue(be.calculateStressApplied() == 0, "With nothing drawing, the shaft is let go again");
			helper.succeed();
		});
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

	/** A flight recorder set against an engine keeps its speed, and logs the start. */
	@GameTest(template = "empty", timeoutTicks = 1100)
	public static void aFlightRecorderLogsAnEnginesLife(GameTestHelper helper) {
		buildSingleCylinder(helper, true);
		BlockPos box = new BlockPos(2, 2, 1);
		helper.setBlock(box, CEBlocks.BLACK_BOX.getDefaultState());
		helper.runAtTickTime(300, () -> helper.setBlock(STARTER_POWER, Blocks.AIR));
		helper.runAtTickTime(1000, () -> {
			BlackBoxBlockEntity be = helper.getBlockEntity(box);
			var readout = be.readout();
			helper.assertTrue(be.engineFound(), "It should have found the engine");
			helper.assertTrue(readout != null && !readout.isEmpty() && readout.span() >= 20,
				"It should have most of the run on tape: " + readout);
			int fastest = 0;
			for (int rpm : readout.rpm())
				fastest = Math.max(fastest, rpm);
			helper.assertTrue(fastest > 20, "And the engine turned: " + fastest);
			helper.assertTrue(readout.events()
				.stream()
				.anyMatch(event -> event.to() == EngineState.RUNNING), "Cranking to running should be an event: " + readout.events());
			helper.succeed();
		});
	}

	/** An engine that will not start is logged as stalling for want of fuel. */
	@GameTest(template = "empty", timeoutTicks = 1100)
	public static void aFlightRecorderSaysWhyAnEngineStalled(GameTestHelper helper) {
		buildSingleCylinder(helper, false);
		BlockPos box = new BlockPos(2, 2, 1);
		helper.setBlock(box, CEBlocks.BLACK_BOX.getDefaultState());
		helper.runAtTickTime(300, () -> helper.setBlock(STARTER_POWER, Blocks.AIR));
		helper.runAtTickTime(1000, () -> {
			BlackBoxBlockEntity be = helper.getBlockEntity(box);
			var events = be.readout()
				.events();
			helper.assertTrue(events.stream()
				.anyMatch(event -> event.to() == EngineState.STALLED
					&& event.cause() == com.createengines.engine.FlightRecorder.Cause.NO_FUEL),
				"An unfuelled engine stalls for want of fuel: " + events);
			helper.succeed();
		});
	}

	/** Alone, a flight recorder has nothing to record. */
	@GameTest(template = "empty")
	public static void aFlightRecorderAloneRecordsNothing(GameTestHelper helper) {
		BlockPos box = new BlockPos(1, 1, 1);
		helper.setBlock(box, CEBlocks.BLACK_BOX.getDefaultState());
		helper.runAtTickTime(100, () -> {
			BlackBoxBlockEntity be = helper.getBlockEntity(box);
			helper.assertTrue(!be.engineFound() && be.recorder()
				.size() == 0, "No engine, no tape");
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

	private static BlockState heavyStarter() {
		return CEBlocks.HEAVY_STARTER.getDefaultState()
			.setValue(StarterBlock.FACING, Direction.UP);
	}

	@GameTest(template = "empty", timeoutTicks = 1200)
	public static void heavyStarterPutsLessStressOnItsInput(GameTestHelper helper) {
		buildSingleCylinder(helper, false);
		float[] plain = new float[1];
		helper.runAtTickTime(300, () -> {
			StarterBlockEntity starter = helper.getBlockEntity(STARTER);
			plain[0] = starter.calculateStressApplied();
			helper.assertTrue(plain[0] > 0, "Cranking should stress the starter's input");
			helper.setBlock(STARTER, heavyStarter());
		});
		helper.runAtTickTime(800, () -> {
			StarterBlockEntity starter = helper.getBlockEntity(STARTER);
			float heavy = starter.calculateStressApplied();
			helper.assertTrue(heavy > 0 && heavy < plain[0] * 0.6f,
				"Heavy starter should gear the effort down: plain " + plain[0] + ", heavy " + heavy);
			helper.succeed();
		});
	}

	@GameTest(template = "empty", timeoutTicks = 1200)
	public static void startersTurningTheSameWayShareTheEffort(GameTestHelper helper) {
		buildSingleCylinder(helper, false);
		float[] alone = new float[1];
		helper.runAtTickTime(300, () -> {
			alone[0] = ((StarterBlockEntity) helper.getBlockEntity(STARTER)).calculateStressApplied();
			helper.assertTrue(alone[0] > 0, "A lone starter should feel the whole effort");
			// A second starter on the next crank block along, with its own motor and redstone
			helper.setBlock(new BlockPos(3, 1, 2), CEBlocks.STARTER.getDefaultState()
				.setValue(StarterBlock.FACING, Direction.UP));
			helper.setBlock(new BlockPos(3, 0, 2), AllBlocks.CREATIVE_MOTOR.getDefaultState()
				.setValue(CreativeMotorBlock.FACING, Direction.UP));
			((CreativeMotorBlockEntity) helper.getBlockEntity(new BlockPos(3, 0, 2))).generatedSpeed.setValue(TestEngine.MOTOR_RPM);
			helper.setBlock(new BlockPos(4, 1, 2), Blocks.REDSTONE_BLOCK.defaultBlockState());
		});
		helper.runAtTickTime(800, () -> {
			StarterBlockEntity first = helper.getBlockEntity(STARTER);
			StarterBlockEntity second = helper.getBlockEntity(new BlockPos(3, 1, 2));
			float shared = first.calculateStressApplied();
			helper.assertTrue(shared > 0 && shared < alone[0] * 0.7f,
				"Each starter should feel about half the effort: alone " + alone[0] + ", shared " + shared);
			helper.assertTrue(Math.abs(second.calculateStressApplied() - shared) < shared * 0.35f,
				"Equal starters should carry equal shares");
			helper.succeed();
		});
	}

	@GameTest(template = "empty", timeoutTicks = 1200)
	public static void heavyStarterChokesAColdEngineThenLetsGo(GameTestHelper helper) {
		buildSingleCylinder(helper, false);
		helper.setBlock(STARTER, heavyStarter());
		helper.runAtTickTime(200, () -> {
			CrankshaftBlockEntity crank = helper.getBlockEntity(PRIMARY);
			helper.assertTrue(summary(helper).temperature() < 0.3f, "A new engine should be cold: " + summary(helper));
			helper.assertTrue(crank.controller()
				.crankAssist() > 0.99, "The heavy starter should be choking the cold engine");
			helper.setBlock(STARTER_POWER, Blocks.AIR);
		});
		helper.runAtTickTime(600, () -> {
			CrankshaftBlockEntity crank = helper.getBlockEntity(PRIMARY);
			helper.assertTrue(crank.controller()
				.crankAssist() == 0, "The choke should let go once the starter has");
			helper.succeed();
		});
	}

	@GameTest(template = "empty", timeoutTicks = 900)
	public static void plainStarterDoesNotChokeAColdEngine(GameTestHelper helper) {
		buildSingleCylinder(helper, false);
		helper.runAtTickTime(200, () -> {
			CrankshaftBlockEntity crank = helper.getBlockEntity(PRIMARY);
			helper.assertTrue(crank.controller()
				.crankAssist() == 0, "A plain starter should leave the cold alone");
			helper.succeed();
		});
	}

	@GameTest(template = "empty", timeoutTicks = 1200)
	public static void heavyStarterStartsAnEngine(GameTestHelper helper) {
		buildSingleCylinder(helper, true);
		helper.setBlock(STARTER, heavyStarter());
		helper.runAtTickTime(300, () -> helper.setBlock(STARTER_POWER, Blocks.AIR));
		helper.runAtTickTime(600, () -> {
			helper.assertTrue(summary(helper).state() == EngineState.RUNNING,
				"A fuelled engine should catch and run on a heavy starter: " + summary(helper));
			helper.succeed();
		});
	}
}
