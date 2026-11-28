package com.createengines.gametest;

import static com.createengines.gametest.EngineRigs.*;

import java.util.Collection;
import java.util.List;
import java.util.function.BiConsumer;

import net.minecraft.world.level.block.state.properties.AttachFace;

import com.createengines.CreateEngines;
import com.createengines.content.airflow.PortOpeningBlock;
import com.createengines.content.battery.BatteryBlockEntity;
import com.createengines.content.camshaft.CamshaftBlockEntity;
import com.createengines.content.crankshaft.CrankshaftBlockEntity;
import com.createengines.content.cylinder.CylinderBlock;
import com.createengines.content.cylinder.CylinderBlockEntity;
import com.createengines.content.head.CylinderHeadBlock;
import com.createengines.content.head.CylinderHeadBlockEntity;
import com.createengines.content.rotor.RotorHousingBlock;
import com.createengines.content.starter.StarterBlock;
import com.createengines.content.sump.SumpBlockEntity;
import com.createengines.engine.CrankGeometry;
import com.createengines.engine.CylinderStatus;
import com.createengines.engine.EngineState;
import com.createengines.engine.EngineSummary;
import com.createengines.engine.sim.CycleResult;
import com.createengines.engine.topology.CylinderFault;
import com.createengines.registry.CEBlocks;
import com.createengines.registry.CECreativeTabs;
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
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
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
public class EngineGameTests {	private static final BlockPos MOTOR = TestEngine.MOTOR;

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

	/** A starter runs on redstone, so a button or lever has to be able to go straight on it, whichever way it faces. */
	@GameTest(template = "empty")
	public static void buttonsAndLeversMountOnAStarter(GameTestHelper helper) {
		BlockPos pos = new BlockPos(2, 2, 2);
		for (net.minecraft.world.level.block.Block starter : List.of(CEBlocks.STARTER.get(), CEBlocks.HEAVY_STARTER.get()))
			for (Direction facing : Direction.values()) {
				helper.setBlock(pos, starter.defaultBlockState()
					.setValue(StarterBlock.FACING, facing));
				for (Direction side : Direction.values())
					for (net.minecraft.world.level.block.Block mounted : List.of(Blocks.STONE_BUTTON, Blocks.LEVER)) {
						BlockState state = mounted.defaultBlockState()
							.setValue(BlockStateProperties.ATTACH_FACE, side == Direction.UP ? AttachFace.FLOOR
								: side == Direction.DOWN ? AttachFace.CEILING : AttachFace.WALL);
						if (side.getAxis()
							.isHorizontal())
							state = state.setValue(BlockStateProperties.HORIZONTAL_FACING, side);
						helper.assertTrue(state.canSurvive(helper.getLevel(), helper.absolutePos(pos.relative(side))),
							mounted + " should mount on the " + side + " side of " + starter + " facing " + facing);
					}
			}
		helper.succeed();
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
			// The air filter is offered only in a game with Pollution of the Realms, where it does something and can be made
			if (entry.get() == CEItems.INCOMPLETE_COPPER_GASKET.get() || entry.get() == CEBlocks.AIR_FILTER.asItem()
				&& !com.createengines.compat.pollution.PollutionCompat.installed()) {
				assertNotListed(helper, CECreativeTabs.MAIN.get()
					.getDisplayItems(), entry, "our tab");
				assertNotListed(helper, search.getDisplayItems(), entry, "search");
				continue;
			}
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

	/** The pan's two dials keep their own values through a save and through the packet that shows them to a player. */
	@GameTest(template = "empty")
	public static void aSumpKeepsBothDialsApartWhenSaved(GameTestHelper helper) {
		BlockPos sump = new BlockPos(3, 1, 2);
		helper.setBlock(sump, CEBlocks.SUMP.getDefaultState());
		SumpBlockEntity pan = helper.getBlockEntity(sump);
		var registries = helper.getLevel()
			.registryAccess();
		pan.exchange.setValue(2);
		pan.signalTank.setValue(3);
		for (boolean packet : new boolean[] {false, true}) {
			CompoundTag saved = packet ? pan.getUpdateTag(registries) : pan.saveWithFullMetadata(registries);
			pan.exchange.value = 0;
			pan.signalTank.value = 0;
			pan.loadWithComponents(saved, registries);
			helper.assertTrue(pan.exchange.getValue() == 2 && pan.signalTank.getValue() == 3,
				"Both dials should come back as set (packet=" + packet + "), got " + pan.exchange.getValue() + " and "
					+ pan.signalTank.getValue());
		}
		// A pan saved before the dials had keys of their own holds one shared value
		CompoundTag legacy = pan.saveWithFullMetadata(registries);
		legacy.remove("OilExchange");
		legacy.remove("SumpSignal");
		legacy.putInt("ScrollValue", 1);
		pan.loadWithComponents(legacy, registries);
		helper.assertTrue(pan.exchange.getValue() == 1 && pan.signalTank.getValue() == 1,
			"An old save still loads its shared value");
		helper.succeed();
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
			com.createengines.registry.CEDisplaySources.ENGINE_OIL, com.createengines.registry.CEDisplaySources.ENGINE_SMOOTHNESS))
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
		// Smoothness is what rough running leaves of the rating: the goggles' loss turned round
		var rough = new EngineSummary(EngineState.RUNNING, 128f, 100f, 50f, 0f, 0f, 1, 1, 1f, 1f, false, new float[0], 1, 0, 0.25f,
			1, 0, 0, false, false, 0, 0, com.createengines.engine.Diagnosis.Health.HEALTHY,
			com.createengines.engine.Diagnosis.Cause.NONE, 0, 0, java.util.List.of());
		helper.assertTrue(Math.abs(com.createengines.content.display.EngineReadout.smoothness(rough) - 0.75f) < 1e-4,
			"A quarter of the rating lost is a smoothness of 0.75");
		helper.assertTrue(com.createengines.content.display.EngineReadout.smoothness(idle) == 1f, "No roughness is a smoothness of 1");
		helper.succeed();
	}

	/**
	 * A Display Link can read an engine's boost and rated torque off any crankshaft block and a battery's charge off the
	 * battery, with the same figures and units as the goggles.
	 */
	@GameTest(template = "empty")
	public static void displayLinksCanReadBoostTorqueAndBatteryCharge(GameTestHelper helper) {
		buildSingleCylinder(helper, true);
		BlockPos batteryPos = new BlockPos(3, 1, 2);
		helper.setBlock(batteryPos, CEBlocks.BATTERY.getDefaultState());
		var onCrank = com.simibubi.create.api.behaviour.display.DisplaySource.getAll(helper.getLevel(),
			helper.absolutePos(PRIMARY));
		for (var source : java.util.List.of(com.createengines.registry.CEDisplaySources.ENGINE_BOOST,
			com.createengines.registry.CEDisplaySources.ENGINE_TORQUE))
			helper.assertTrue(onCrank.contains(source.get()), "A crankshaft should offer " + source.getId());
		helper.assertTrue(com.simibubi.create.api.behaviour.display.DisplaySource
			.getAll(helper.getLevel(), helper.absolutePos(batteryPos))
			.contains(com.createengines.registry.CEDisplaySources.BATTERY_CHARGE.get()), "A battery should offer its charge");

		// Boost reads as the goggles do, in bar over atmosphere, and never negative
		helper.assertTrue("0.46".equals(com.createengines.content.display.EngineReadout.bar(0.456f)),
			"A boost of 0.456 reads +0.46 bar");
		helper.assertTrue("0.00".equals(com.createengines.content.display.EngineReadout.bar(-0.1f)), "Boost does not go below 0");
		// Torque reads as the goggles do: one decimal below 100 SU per RPM, whole numbers from there
		helper.assertTrue("445".equals(com.createengines.content.display.EngineReadout.torque(445.2f)),
			"A torque of 445.2 reads 445");
		helper.assertTrue("12.5".equals(com.createengines.content.display.EngineReadout.torque(12.5f)),
			"A torque of 12.5 reads 12.5");
		helper.assertTrue("0.0".equals(com.createengines.content.display.EngineReadout.torque(-3f)), "Torque does not go below 0");

		// A battery's fill is its charge as a share of its capacity; anything that is not a battery reads empty
		BatteryBlockEntity battery = helper.getBlockEntity(batteryPos);
		helper.assertTrue(com.createengines.content.display.BatteryDisplaySource.fill(battery) == 0f, "A new battery is flat");
		battery.charge(BatteryBlockEntity.CAPACITY / 2);
		helper.assertTrue(Math.abs(com.createengines.content.display.BatteryDisplaySource.fill(battery) - 0.5f) < 1e-4,
			"A half charged battery reads 0.5");
		battery.charge(BatteryBlockEntity.CAPACITY);
		helper.assertTrue(com.createengines.content.display.BatteryDisplaySource.fill(battery) == 1f, "A full battery reads 1");
		helper.assertTrue(com.createengines.content.display.BatteryDisplaySource.fill(null) == 0f, "No block entity reads empty");
		helper.assertTrue(com.createengines.content.display.BatteryDisplaySource.fill(helper.getBlockEntity(PRIMARY)) == 0f,
			"A block entity that is not a battery reads empty");
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

	/**
	 * The engine tells the client what it is built of, for its sound (the 2.4 sound rework): an outlet for every firing, the
	 * run's length (longer once the exhaust runs through a muffler), and casing
	 * beside the cylinder as part of it clad. Only the sound: nothing here changes how it runs.
	 */
	@GameTest(template = "empty", timeoutTicks = 300)
	public static void theEngineSyncsWhatItIsBuiltOfForItsSound(GameTestHelper helper) {
		buildSingleCylinder(helper, true);
		float[] stub = new float[1];
		helper.runAtTickTime(60, () -> {
			EngineSummary summary = summary(helper);
			com.createengines.engine.EngineSound sound = summary.sound();
			helper.assertTrue(sound.outlets().length == summary.firingAngles().length,
				"Every firing angle has an outlet: " + java.util.Arrays.toString(sound.outlets()) + " for "
					+ java.util.Arrays.toString(summary.firingAngles()));
			helper.assertTrue(sound.pipes().length == 1, "One exhaust run is one outlet: " + java.util.Arrays.toString(sound.pipes()));
			helper.assertTrue(sound.enclosure() == 0, "Nothing is clad yet: " + sound.enclosure());
			stub[0] = sound.pipes()[0];
			// A longer run, through a muffler, and a casing block beside the cylinder
			helper.setBlock(TestEngine.EXHAUST.west(), CEBlocks.EXHAUST_OUTLET.getDefaultState()
				.setValue(com.createengines.content.airflow.PortOpeningBlock.FACING, Direction.WEST));
			helper.setBlock(TestEngine.EXHAUST, CEBlocks.MUFFLER.getDefaultState()
				.setValue(BlockStateProperties.AXIS, Axis.X));
			helper.setBlock(TestEngine.CYLINDER.east(), CEBlocks.ENGINE_CASING.getDefaultState());
		});
		helper.runAtTickTime(200, () -> {
			com.createengines.engine.EngineSound sound = summary(helper).sound();
			helper.assertTrue(sound.pipes().length == 1 && sound.pipes()[0] > stub[0],
				"The run through the muffler is longer: " + stub[0] + " to " + java.util.Arrays.toString(sound.pipes()));
			helper.assertTrue(sound.muffles().length == 1 && sound.muffles()[0] == 1,
				"The muffled run's own outlet is muffled: " + java.util.Arrays.toString(sound.muffles()));
			helper.assertTrue(sound.enclosure() > 0, "A casing block beside the cylinder clads part of it: " + sound.enclosure());
			// The sync keeps it, and an older world's summary (without it) reads as a plain note
			EngineSummary synced = EngineSummary.read(summary(helper).write());
			helper.assertTrue(synced.sound()
				.sameAs(sound), "The summary's sync keeps the sound: " + synced.sound());
			net.minecraft.nbt.CompoundTag older = summary(helper).write();
			for (String key : java.util.List.of("SoundOutlets", "SoundPipes", "SoundCompression", "SoundPrecision",
				"SoundEnclosure", "SoundMuffles"))
				older.remove(key);
			helper.assertTrue(EngineSummary.read(older)
				.sound() == com.createengines.engine.EngineSound.NONE, "A summary without it is a plain note");
			// A summary that differs only in how it sounds is still sent to the client
			helper.assertTrue(!synced.differsFrom(EngineSummary.read(summary(helper).write())),
				"The same summary read twice should not differ: " + synced);
			net.minecraft.nbt.CompoundTag duller = summary(helper).write();
			duller.putFloat("SoundEnclosure", 1f);
			helper.assertTrue(EngineSummary.read(duller)
				.differsFrom(synced), "A change of sound alone should be synced");
			helper.setBlock(TestEngine.CYLINDER.east(), Blocks.AIR);
		});
		helper.runAtTickTime(250, () -> {
			helper.assertTrue(summary(helper).sound()
				.enclosure() == 0, "With the casing gone nothing is clad: " + summary(helper).sound()
					.enclosure());
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

	private static void assertNotListed(GameTestHelper helper, Collection<ItemStack> items,
		RegistryEntry<Item, Item> entry, String where) {
		helper.assertTrue(items.stream()
			.noneMatch(stack -> stack.is(entry.get())), entry.getId() + " should not be listed in " + where);
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
	@GameTest(template = "empty_large", timeoutTicks = 1400)
	public static void overloadOverstressesWithoutStalling(GameTestHelper helper) {
		runWithPresses(helper, scaledPresses(4));
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
			assertSounds(helper, true, 1);
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
			// One cam for both banks, and each bank out of its own exhaust
			assertSounds(helper, true, 2);
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

	/** A Create analog lever at the given strength, standing on the block below it. */
	static void setLever(GameTestHelper helper, BlockPos at, int strength) {
		if (!helper.getBlockState(at)
			.is(AllBlocks.ANALOG_LEVER.get()))
			helper.setBlock(at, AllBlocks.ANALOG_LEVER.getDefaultState()
				.setValue(BlockStateProperties.ATTACH_FACE, net.minecraft.world.level.block.state.properties.AttachFace.FLOOR));
		var lever = helper.getLevel()
			.getBlockEntity(helper.absolutePos(at));
		var tag = lever.saveWithFullMetadata(helper.getLevel()
			.registryAccess());
		tag.putInt("State", strength);
		lever.loadWithComponents(tag, helper.getLevel()
			.registryAccess());
		helper.getLevel()
			.updateNeighborsAt(helper.absolutePos(at), AllBlocks.ANALOG_LEVER.get());
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
