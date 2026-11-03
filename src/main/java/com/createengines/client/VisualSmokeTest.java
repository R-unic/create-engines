package com.createengines.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import com.createengines.CreateEngines;
import com.createengines.content.EnginePoses;
import com.createengines.content.airflow.ManifoldBlock;
import com.createengines.content.airflow.PortOpeningBlock;
import com.createengines.content.crankshaft.CrankshaftBlockEntity;
import com.createengines.content.cylinder.CylinderBlock;
import com.createengines.content.governor.GovernorBlockEntity;
import com.createengines.content.tool.TimingToolItem;
import com.createengines.engine.topology.FiringPlanner;
import com.createengines.content.head.CylinderHeadBlock;
import com.createengines.content.head.CylinderHeadBlockEntity;
import com.createengines.engine.CrankGeometry;
import com.createengines.gametest.TestEngine;
import com.createengines.registry.CEBlocks;
import com.createengines.registry.CEFluids;
import com.createengines.registry.CEItems;

import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.gui.ScreenOpener;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Development aid, off unless the {@code visualSmokeTest} run is used: creates a flat creative world, builds the
 * single-cylinder {@link TestEngine}, takes screenshots from fixed viewpoints while it cranks and
 * runs (some wearing Engineer's Goggles), then quits. Screenshots land in {@code run/screenshots}.
 */
public final class VisualSmokeTest {

	private static final String PROPERTY = "create_engines.visualSmokeTest";
	/** Run only the newest Ponder pages (gradle {@code -PsmokeScenes}), for a short check of those. */
	private static final String SCENES_ONLY = "create_engines.visualSmokeTest.scenes";

	/** Run only the twin-rotor engine (gradle {@code -PsmokeRotary}), for a short look at the rotary parts. */
	private static final String ROTARY_ONLY = "create_engines.visualSmokeTest.rotary";

	/** Run only the flywheel, dynamometer, variable turbo and bank builder (gradle {@code -PsmokeNewParts}). */
	private static final String NEW_PARTS_ONLY = "create_engines.visualSmokeTest.newParts";

	private record Step(int tick, Runnable action) {
	}

	/** Where the spark engine stands, relative to the first. */
	private static final BlockPos SPARK_OFFSET = new BlockPos(12, 0, 0);

	private static final List<Step> STEPS = new ArrayList<>();
	private static boolean worldRequested;
	private static int ticksInWorld = -1;
	private static BlockPos origin = BlockPos.ZERO;

	private VisualSmokeTest() {
	}

	public static void register() {
		if (!Boolean.getBoolean(PROPERTY))
			return;
		CreateEngines.LOGGER.info("Visual smoke test enabled");
		NeoForge.EVENT_BUS.addListener(VisualSmokeTest::onClientTick);
		NeoForge.EVENT_BUS.addListener(VisualSmokeTest::onRenderFrame);
		schedule();
	}

	private static void schedule() {
		STEPS.add(new Step(40, () -> {
			Minecraft.getInstance().options.hideGui = true;
			server(server -> {
				var level = server.overworld();
				int y = level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, new BlockPos(0, 0, 3))
					.getY();
				origin = new BlockPos(0, y, 3);
				command(server, "gamerule sendCommandFeedback false");
				command(server, "gamerule doDaylightCycle false");
				command(server, "gamerule doMobSpawning false");
				command(server, "time set noon");
				TestEngine.build(level, origin, true);
			});
		}));

		if (Boolean.getBoolean(ROTARY_ONLY)) {
			rotaryBench(100);
			return;
		}

		if (Boolean.getBoolean(NEW_PARTS_ONLY)) {
			newParts(100);
			return;
		}

		if (Boolean.getBoolean(SCENES_ONLY)) {
			// Goggle lines that used to run off the screen, then the new pages
			STEPS.add(new Step(100, () -> {
				Minecraft.getInstance().options.hideGui = false;
				server(server -> command(server, "item replace entity @a armor.head with create:goggles"));
			}));
			goggles("head", 110, 4.5, 4.0, 1.0, 45, 0);
			goggles("crankshaft", 130, 1.5, 2.0, 0.4, 0, 28);
			goggles("cylinder", 150, 2.5, 2.0, -0.5, 0, 2.3f);
			ponderScene("compact_v", 200, CEBlocks.CAM_LOBE::asStack, 2, 900, 1150, 1400);
			ponder("heat_exchanger", 1700, CEBlocks.HEAT_EXCHANGER::asStack, 400, 800, 1200);
			ponderScene("fuels", 3000, CEBlocks.HEAT_EXCHANGER::asStack, 1, 400, 900, 1400);
			ponder("supercharger", 4500, CEBlocks.SUPERCHARGER::asStack, 250, 600, 900, 1250, 1600);
			ponder("black_box", 6300, CEBlocks.BLACK_BOX::asStack, 250, 600, 950);
			management(7500);
			return;
		}

		// Cranking on the starter: from the intake side, then from the camshaft side
		frames("cranking_front", 100, 2.5, 2.0, -1.2, 0, 9.5f);
		frames("cranking_back", 140, 2.5, 2.0, 6.2, 180, 9.5f);
		STEPS.add(new Step(180, () -> view(-2.5, 6.5, -1.5, -55, 37)));
		STEPS.add(new Step(195, () -> shot("overview_cranking")));

		// Release the starter; the engine should carry on by itself
		STEPS.add(new Step(260, () -> server(server -> server.overworld()
			.removeBlock(origin.offset(TestEngine.STARTER_POWER), false))));
		frames("running_front", 400, 2.5, 2.0, -1.2, 0, 9.5f);
		frames("running_back", 440, 2.5, 2.0, 6.2, 180, 9.5f);
		frames("valves", 480, -0.5, 4.6, 4.5, -116.6f, 19.7f);
		// Looking straight down the crank axis, so the throw turns in the picture plane
		frames("along_crank", 500, -1.5, 2.0, 2.5, -90, 8.8f);
		STEPS.add(new Step(525, () -> view(-2.5, 6.5, -1.5, -55, 37)));
		STEPS.add(new Step(538, () -> shot("overview_running")));

		// Goggles on each component
		STEPS.add(new Step(540, () -> {
			Minecraft.getInstance().options.hideGui = false;
			server(server -> command(server, "item replace entity @a armor.head with create:goggles"));
		}));
		goggles("crankshaft", 545, 1.5, 2.0, 0.4, 0, 28);
		goggles("cylinder", 565, 2.5, 2.0, -0.5, 0, 2.3f);
		goggles("cam_lobe", 585, 2.5, 6.5, 1.6, 0, 59.5f);
		goggles("head", 605, 4.5, 4.0, 1.0, 45, 0);
		goggles("starter", 625, 1.5, 0.0, 0.0, 0, 2.7f);
		// Ponder scenes, which draw engine parts with the fallback renderers rather than Flywheel
		ponder("crankshaft", 660, CEBlocks.CRANK_THROW::asStack, 160, 420);
		ponder("cylinder", 1120, CEBlocks.CYLINDER::asStack, 300, 600);
		ponder("camshaft", 1780, CEBlocks.CAM_LOBE::asStack, 300, 560);
		ponder("airflow", 2400, CEBlocks.AIR_INTAKE::asStack, 340, 450);
		ponder("starter", 2900, CEBlocks.STARTER::asStack, 220, 420);
		// Buckets in the hotbar: each fluid layer should wear its own colour, not the grey of the water texture
		STEPS.add(new Step(3340, () -> {
			Minecraft.getInstance().options.hideGui = false;
			server(server -> {
				for (String bucket : List.of("plant_oil_bucket", "diesel_bucket", "engine_oil_bucket"))
					command(server, "give @a create_engines:" + bucket);
			});
			view(2.5, 2.0, -0.5, 0, 2.3f);
		}));
		STEPS.add(new Step(3365, () -> shot("buckets")));

		// A piston with no connecting rod isn't tied to the crank, so it must stay put while the crank turns
		STEPS.add(new Step(3370, () -> {
			Minecraft.getInstance().options.hideGui = true;
			server(server -> {
				var level = server.overworld();
				BlockPos cylinder = origin.offset(TestEngine.CYLINDER);
				level.setBlock(cylinder, level.getBlockState(cylinder)
					.setValue(CylinderBlock.HAS_ROD, false), 3);
				level.setBlock(origin.offset(TestEngine.STARTER_POWER), Blocks.REDSTONE_BLOCK.defaultBlockState(), 3);
			});
		}));
		frames("no_rod", 3400, 2.5, 2.0, -0.5, 0, 2.3f);
		ponder("lubrication", 3450, () -> CEFluids.ENGINE_OIL.getBucket()
			.orElseThrow()
			.getDefaultInstance(), 150, 330, 560, 700, 880);

		// A spark engine beside it: gasoline, a spark plug, a battery on the crank's shaft line, and real pipes
		// leading to its intake and exhaust openings
		STEPS.add(new Step(4400, () -> {
			Minecraft.getInstance().options.hideGui = true;
			server(server -> {
				var level = server.overworld();
				BlockPos spark = origin.offset(SPARK_OFFSET);
				TestEngine.buildSpark(level, spark, true);
				level.removeBlock(spark.offset(TestEngine.AIR_INTAKE), false);
				level.removeBlock(spark.offset(TestEngine.EXHAUST), false);
				BlockPos intake = spark.offset(TestEngine.AIR_INTAKE);
				level.setBlock(intake.north(), pipe(CEBlocks.INTAKE_MANIFOLD.get(), Direction.SOUTH, Direction.WEST), 3);
				level.setBlock(intake.north()
					.west(), CEBlocks.AIR_INTAKE.getDefaultState()
						.setValue(PortOpeningBlock.FACING, Direction.WEST),
					3);
				level.setBlock(intake, pipe(CEBlocks.THROTTLE.get(), Direction.SOUTH, Direction.NORTH), 3);
				BlockPos exhaust = spark.offset(TestEngine.EXHAUST);
				level.setBlock(exhaust.above(2), CEBlocks.EXHAUST_OUTLET.getDefaultState()
					.setValue(PortOpeningBlock.FACING, Direction.UP), 3);
				level.setBlock(exhaust.above(), pipe(CEBlocks.EXHAUST_MANIFOLD.get(), Direction.UP, Direction.DOWN), 3);
				level.setBlock(exhaust, pipe(CEBlocks.EXHAUST_MANIFOLD.get(), Direction.EAST, Direction.UP), 3);
			});
		}));
		STEPS.add(new Step(4700, () -> server(server -> server.overworld()
			.removeBlock(origin.offset(SPARK_OFFSET)
				.offset(TestEngine.STARTER_POWER), false))));
		frames("spark_front", 4900, SPARK_OFFSET.getX() + 2.5, 2.0, -1.2, 0, 9.5f);
		frames("spark_pipes", 4950, SPARK_OFFSET.getX() - 1.5, 6.0, -2.5, -35, 32);
		STEPS.add(new Step(5000, () -> view(SPARK_OFFSET.getX() - 2.5, 6.5, -1.5, -55, 37)));
		STEPS.add(new Step(5015, () -> shot("spark_overview")));
		STEPS.add(new Step(5020, () -> Minecraft.getInstance().options.hideGui = false));
		goggles("spark_battery", 5025, SPARK_OFFSET.getX() + 1.5, 3.0, 0.4, 0, 28);
		goggles("spark_head", 5045, SPARK_OFFSET.getX() + 4.5, 4.0, 1.0, 45, 0);
		goggles("spark_cylinder", 5065, SPARK_OFFSET.getX() + 2.5, 2.0, -0.5, 0, 2.3f);
		ponder("spark", 5100, CEBlocks.ALTERNATOR::asStack, 250, 450, 650);
		ponder("throttle", 5900, CEBlocks.THROTTLE::asStack, 250, 650, 1000);
		ponder("mixture", 7050, CEItems.FUEL_INJECTOR::asStack, 250, 650);
		// Blocks added later, standing in the world: a turbo, an oil sump and a motor-driven transmission
		STEPS.add(new Step(7720, () -> {
			Minecraft.getInstance().options.hideGui = true;
			server(server -> {
				var level = server.overworld();
				BlockPos base = origin.offset(24, 0, 0);
				var x = net.minecraft.world.level.block.state.properties.BlockStateProperties.AXIS;
				level.setBlock(base.offset(0, 1, 0), CEBlocks.TURBOCHARGER.getDefaultState()
					.setValue(com.createengines.content.turbo.TurbochargerBlock.FACING, Direction.WEST), 3);
				level.setBlock(base.offset(2, 1, 0), CEBlocks.SUMP.getDefaultState(), 3);
				level.setBlock(base.offset(0, 1, 3), com.simibubi.create.AllBlocks.CREATIVE_MOTOR.getDefaultState()
					.setValue(com.simibubi.create.content.kinetics.motor.CreativeMotorBlock.FACING, Direction.EAST), 3);
				if (level.getBlockEntity(base.offset(0, 1, 3)) instanceof com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity motor)
					motor.generatedSpeed.setValue(64);
				level.setBlock(base.offset(1, 1, 3), com.simibubi.create.AllBlocks.SHAFT.getDefaultState()
					.setValue(x, Direction.Axis.X), 3);
				level.setBlock(base.offset(2, 1, 3), CEBlocks.TRANSMISSION.getDefaultState()
					.setValue(x, Direction.Axis.X), 3);
				level.setBlock(base.offset(3, 1, 3), com.simibubi.create.AllBlocks.SHAFT.getDefaultState()
					.setValue(x, Direction.Axis.X), 3);
			});
		}));
		frames("turbo_sump", 7740, 24 + 1.5, 2.6, -2.5, 0, 18);
		frames("transmission", 7790, 24 + 2.5, 2.6, 0.2, 0, 25);
		// The new Ponder scenes
		ponder("turbocharger", 7850, CEBlocks.TURBOCHARGER::asStack, 250, 650, 1000);
		ponder("transmission", 8900, CEBlocks.TRANSMISSION::asStack, 250, 650, 900);
		ponder("sump", 9850, CEBlocks.SUMP::asStack, 250, 450, 650);
		newScenes(10600);
	}

	/** The firing order, compact V and mixture pages, from {@code start}; then quits. */
	private static void newScenes(int start) {
		ponderScene("firing_order", start, CEBlocks.CAM_LOBE::asStack, 1, 120, 400, 600, 780, 960, 1350, 1560);
		ponderScene("compact_v", start + 1700, CEBlocks.CAM_LOBE::asStack, 2, 250, 450, 700);
		ponder("mixture", start + 2500, CEItems.FUEL_INJECTOR::asStack, 500, 750, 900, 1000);
		ponder("supercharger", start + 3600, CEBlocks.SUPERCHARGER::asStack, 250, 600, 900, 1250, 1600);
		management(start + 5400);
	}

	/** Where the engine-management engine stands, relative to the first. */
	private static final BlockPos MANAGEMENT_OFFSET = new BlockPos(36, 0, 0);

	/**
	 * A fresh engine with a MAF sensor in its intake and the injector turned up so the sensor has something to cut; then
	 * its Ponder page, and the end of the run.
	 */
	private static void management(int start) {
		int dx = MANAGEMENT_OFFSET.getX();
		STEPS.add(new Step(start, () -> {
			Minecraft.getInstance().options.hideGui = true;
			server(server -> {
				var level = server.overworld();
				BlockPos at = origin.offset(MANAGEMENT_OFFSET);
				TestEngine.build(level, at, true);
				BlockPos intake = at.offset(TestEngine.AIR_INTAKE);
				level.removeBlock(intake, false);
				level.setBlock(intake, pipe(CEBlocks.MAF_SENSOR.get(), Direction.SOUTH, Direction.WEST), 3);
				level.setBlock(intake.west(), CEBlocks.AIR_INTAKE.getDefaultState()
					.setValue(PortOpeningBlock.FACING, Direction.WEST), 3);
				if (level.getBlockEntity(at.offset(TestEngine.HEAD)) instanceof CylinderHeadBlockEntity head)
					head.metering.setValue(CylinderHeadBlockEntity.MAX_METERING);
			});
		}));
		STEPS.add(new Step(start + 250, () -> server(server -> server.overworld()
			.removeBlock(origin.offset(MANAGEMENT_OFFSET)
				.offset(TestEngine.STARTER_POWER), false))));
		STEPS.add(new Step(start + 520, () -> view(dx - 2.5, 6.5, -1.5, -55, 37)));
		STEPS.add(new Step(start + 535, () -> shot("management_overview")));
		STEPS.add(new Step(start + 540, () -> {
			Minecraft.getInstance().options.hideGui = false;
			server(server -> command(server, "item replace entity @a armor.head with create:goggles"));
		}));
		goggles("maf", start + 560, dx + 2.5, 5.0, -1.0, 0, 11);
		goggles("management_cylinder", start + 650, dx + 2.5, 2.0, -0.5, 0, 2.3f);
		ponder("maf_sensor", start + 700, CEBlocks.MAF_SENSOR::asStack, 250, 650, 1000);
		precision(start + 1800);
	}

	/** Where the precision engine stands, relative to the first. */
	private static final BlockPos PRECISION_OFFSET = new BlockPos(72, 0, 0);

	/**
	 * A fresh engine built from precision parts under a governor, then the pages for the governor, the timing tool and
	 * the precision parts, and the end of the run.
	 */
	private static void precision(int start) {
		int dx = PRECISION_OFFSET.getX();
		STEPS.add(new Step(start, () -> {
			Minecraft.getInstance().options.hideGui = true;
			server(server -> {
				var level = server.overworld();
				BlockPos at = origin.offset(PRECISION_OFFSET);
				TestEngine.build(level, at, true);
				level.setBlock(at.offset(TestEngine.CYLINDER), CEBlocks.PRECISION_CYLINDER.getDefaultState()
					.setValue(CylinderBlock.FACING, Direction.UP)
					.setValue(CylinderBlock.HAS_PISTON, true)
					.setValue(CylinderBlock.FORGED_PISTON, true)
					.setValue(CylinderBlock.HAS_ROD, true), 3);
				level.setBlock(at.offset(TestEngine.HEAD), CEBlocks.PRECISION_CYLINDER_HEAD.getDefaultState()
					.setValue(CylinderHeadBlock.FACING, Direction.UP)
					.setValue(CylinderHeadBlock.HAS_INJECTOR, true)
					.setValue(CylinderHeadBlock.FINE_INJECTOR, true), 3);
				if (level.getBlockEntity(at.offset(TestEngine.HEAD)) instanceof CylinderHeadBlockEntity head) {
					head.tank()
						.fill(new FluidStack(CEFluids.DIESEL.get()
							.getSource(), 250), IFluidHandler.FluidAction.EXECUTE);
					head.oilTank()
						.fill(new FluidStack(CEFluids.ENGINE_OIL.get()
							.getSource(), 250), IFluidHandler.FluidAction.EXECUTE);
				}
				BlockPos governor = at.offset(3, 3, 2);
				level.setBlock(governor, CEBlocks.GOVERNOR.getDefaultState(), 3);
				if (level.getBlockEntity(governor) instanceof GovernorBlockEntity be)
					be.target.setValue(80 / GovernorBlockEntity.TARGET_STEP);
			});
		}));
		STEPS.add(new Step(start + 250, () -> server(server -> server.overworld()
			.removeBlock(origin.offset(PRECISION_OFFSET)
				.offset(TestEngine.STARTER_POWER), false))));
		STEPS.add(new Step(start + 520, () -> view(dx - 2.5, 6.5, -1.5, -55, 37)));
		STEPS.add(new Step(start + 535, () -> shot("precision_overview")));
		STEPS.add(new Step(start + 540, () -> {
			Minecraft.getInstance().options.hideGui = false;
			server(server -> command(server, "item replace entity @a armor.head with create:goggles"));
		}));
		goggles("governor", start + 560, dx + 3.5, 4.5, -0.5, 0, 18);
		goggles("precision_head", start + 590, dx + 2.5, 5.0, -1.0, 0, 11);
		goggles("precision_cylinder", start + 620, dx + 2.5, 2.0, -0.5, 0, 2.3f);
		ponder("governor", start + 700, CEBlocks.GOVERNOR::asStack, 250, 550, 800);
		ponder("timing_tool", start + 1700, CEItems.TIMING_TOOL::asStack, 250, 550, 800);
		ponder("precision_parts", start + 2700, CEBlocks.PRECISION_CYLINDER::asStack, 250, 550, 800);
		coupled(start + 3700);
	}

	/** Where the coupled engine stands, relative to the first. */
	private static final BlockPos COUPLED_OFFSET = new BlockPos(96, 0, 0);

	/** Two crank lines geared together, timed as one engine, then the page for coupled crankshafts and the end of the run. */
	private static void coupled(int start) {
		int dx = COUPLED_OFFSET.getX();
		STEPS.add(new Step(start, () -> {
			Minecraft.getInstance().options.hideGui = true;
			server(server -> {
				var level = server.overworld();
				BlockPos at = origin.offset(COUPLED_OFFSET);
				TestEngine.build(level, at, true);
				TestEngine.addCoupledLine(level, at);
				if (level.getBlockEntity(at.offset(TestEngine.HEAD)) instanceof CylinderHeadBlockEntity head)
					head.oilTank()
						.fill(new FluidStack(CEFluids.ENGINE_OIL.get()
							.getSource(), 250), IFluidHandler.FluidAction.EXECUTE);
			});
		}));
		// The new line is found on the next rescan; time both lines once it has been
		STEPS.add(new Step(start + 140, () -> server(server -> TimingToolItem.apply(server.overworld(),
			origin.offset(COUPLED_OFFSET)
				.offset(TestEngine.THROW_2),
			FiringPlanner.Style.SMOOTH))));
		STEPS.add(new Step(start + 250, () -> server(server -> server.overworld()
			.removeBlock(origin.offset(COUPLED_OFFSET)
				.offset(TestEngine.STARTER_POWER), false))));
		STEPS.add(new Step(start + 520, () -> view(dx - 2.5, 6.5, -1.5, -55, 37)));
		STEPS.add(new Step(start + 535, () -> shot("coupled_overview")));
		STEPS.add(new Step(start + 540, () -> {
			Minecraft.getInstance().options.hideGui = false;
			server(server -> command(server, "item replace entity @a armor.head with create:goggles"));
		}));
		goggles("coupled", start + 560, dx + 2.5, 4.0, 7.0, 180, 31);
		ponderScene("coupled_cranks", start + 700, CEBlocks.CRANKSHAFT::asStack, 1, 250, 550, 800, 1100);
		STEPS.add(new Step(start + 2100, () -> Minecraft.getInstance()
			.stop()));
	}

	/** Where the twin-rotor engine stands, relative to the first. */
	private static final BlockPos ROTARY_OFFSET = new BlockPos(108, 0, 0);

	/**
	 * A bare rotor housing turned slowly by a motor on its west end, filmed down its open east end and from the side, to
	 * look at how the rotor sits and moves in it.
	 */
	private static void rotaryBench(int start) {
		int dx = ROTARY_OFFSET.getX() + 20;
		STEPS.add(new Step(start, () -> {
			Minecraft.getInstance().options.hideGui = true;
			server(server -> {
				var level = server.overworld();
				BlockPos at = origin.offset(dx, 0, 0);
				level.setBlock(at.offset(2, 2, 2), com.createengines.registry.CEBlocks.ROTOR_HOUSING.getDefaultState()
					.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.AXIS,
						net.minecraft.core.Direction.Axis.X)
					.setValue(com.createengines.content.rotor.RotorHousingBlock.HAS_ROTOR, true), 3);
				level.setBlock(at.offset(1, 2, 2), com.simibubi.create.AllBlocks.CREATIVE_MOTOR.getDefaultState()
					.setValue(com.simibubi.create.content.kinetics.motor.CreativeMotorBlock.FACING,
						net.minecraft.core.Direction.EAST), 3);
				if (level.getBlockEntity(at.offset(1, 2, 2)) instanceof com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity motor)
					motor.generatedSpeed.setValue(8);
			});
		}));
		for (int i = 0; i < 8; i++) {
			int frame = i;
			STEPS.add(new Step(start + 60 + i * 14, () -> view(dx + 4.0, 1.0, 2.5, 90, 0)));
			STEPS.add(new Step(start + 70 + i * 14, () -> shot("bench_end_" + frame)));
		}
		STEPS.add(new Step(start + 60 + 8 * 14, () -> view(dx + 2.5, 1.0, 4.6, 180, 0)));
		for (int i = 0; i < 4; i++) {
			int frame = i;
			STEPS.add(new Step(start + 70 + 8 * 14 + i * 14, () -> shot("bench_side_" + frame)));
		}
		STEPS.add(new Step(start + 300, () -> Minecraft.getInstance()
			.stop()));
	}

	/** A twin-rotor engine, running, from the side, from above and through goggles; then the end of the run. */
	private static void rotary(int start) {
		int dx = ROTARY_OFFSET.getX();
		STEPS.add(new Step(start, () -> {
			Minecraft.getInstance().options.hideGui = true;
			server(server -> {
				var level = server.overworld();
				BlockPos at = origin.offset(ROTARY_OFFSET);
				TestEngine.buildRotary(level, at, true);
				TestEngine.addSecondRotor(level, at);
			});
		}));
		// The second rotor is found on the next rescan; release the starter once both are running
		STEPS.add(new Step(start + 300, () -> server(server -> server.overworld()
			.removeBlock(origin.offset(ROTARY_OFFSET)
				.offset(TestEngine.STARTER_POWER), false))));
		STEPS.add(new Step(start + 400, () -> view(dx - 2.5, 6.5, -1.5, -55, 37)));
		STEPS.add(new Step(start + 415, () -> shot("rotary_overview")));
		frames("rotary_side", start + 440, dx + 3.0, 2.5, 5.0, 180, 3);
		frames("rotary_slant", start + 480, dx + 5.5, 4.2, 5.0, 160, 25);
		STEPS.add(new Step(start + 520, () -> {
			Minecraft.getInstance().options.hideGui = false;
			server(server -> command(server, "item replace entity @a armor.head with create:goggles"));
		}));
		goggles("rotor", start + 540, dx + 2.5, 2.5, 4.5, 180, 8);
		// The new items in the hotbar, beside a cylinder head to compare with
		STEPS.add(new Step(start + 570, () -> server(server -> {
			command(server, "item replace entity @a hotbar.0 with create_engines:rotor_housing");
			command(server, "item replace entity @a hotbar.1 with create_engines:rotor");
			command(server, "item replace entity @a hotbar.2 with create_engines:cylinder_head");
		})));
		STEPS.add(new Step(start + 580, () -> shot("rotary_hotbar")));
		ponder("rotor", start + 600, CEBlocks.ROTOR_HOUSING::asStack, 100, 250, 400, 560, 760, 900, 1020);
		STEPS.add(new Step(start + 1700, () -> Minecraft.getInstance()
			.stop()));
	}

	/** The flywheel, dynamometer and variable turbo beside the test engine, a goggle readout, the new items and pages. */
	private static void newParts(int start) {
		STEPS.add(new Step(start, () -> {
			Minecraft.getInstance().options.hideGui = true;
			server(server -> {
				var level = server.overworld();
				level.setBlock(origin.offset(0, 2, 2), CEBlocks.CAST_FLYWHEEL.getDefaultState()
					.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.AXIS,
						net.minecraft.core.Direction.Axis.X), 3);
				level.setBlock(origin.offset(2, 2, 1), CEBlocks.DYNO.getDefaultState(), 3);
				level.setBlock(origin.offset(4, 0, 0), CEBlocks.VARIABLE_TURBOCHARGER.getDefaultState()
					.setValue(com.createengines.content.turbo.TurbochargerBlock.FACING, Direction.UP), 3);
				level.setBlock(origin.offset(6, 0, 0), CEBlocks.TURBOCHARGER.getDefaultState()
					.setValue(com.createengines.content.turbo.TurbochargerBlock.FACING, Direction.UP), 3);
			});
		}));
		STEPS.add(new Step(start + 300, () -> server(server -> server.overworld()
			.removeBlock(origin.offset(TestEngine.STARTER_POWER), false))));
		STEPS.add(new Step(start + 500, () -> view(-2.5, 6.5, -1.5, -55, 37)));
		STEPS.add(new Step(start + 515, () -> shot("newparts_overview")));
		frames("newparts_front", start + 540, 3.5, 2.5, -2.5, 0, 12);
		frames("newparts_flywheel", start + 580, -2.0, 3.0, 0.5, -45, 18);
		frames("newparts_turbos", start + 620, 5.0, 2.5, -2.5, 0, 14);
		STEPS.add(new Step(start + 660, () -> {
			Minecraft.getInstance().options.hideGui = false;
			server(server -> {
				command(server, "item replace entity @a armor.head with create:goggles");
				command(server, "item replace entity @a hotbar.0 with create_engines:cast_flywheel");
				command(server, "item replace entity @a hotbar.1 with create_engines:dyno");
				command(server, "item replace entity @a hotbar.2 with create_engines:variable_turbocharger");
				command(server, "item replace entity @a hotbar.3 with create_engines:bank_builder");
				command(server, "item replace entity @a hotbar.4 with create_engines:timing_tool");
			});
		}));
		goggles("dyno", start + 680, 2.5, 3.0, -1.2, 0, 25);
		STEPS.add(new Step(start + 720, () -> shot("newparts_hotbar")));
		ponder("flywheel", start + 740, CEBlocks.CAST_FLYWHEEL::asStack, 150, 450, 800);
		ponder("bank_builder", start + 1800, CEItems.BANK_BUILDER::asStack, 150, 450, 800, 1100);
		ponder("dyno", start + 3100, CEBlocks.DYNO::asStack, 250, 600, 950);
		ponder("turbo_tuning", start + 4300, CEBlocks.VARIABLE_TURBOCHARGER::asStack, 250, 650, 1000, 1350);
		STEPS.add(new Step(start + 5800, () -> Minecraft.getInstance()
			.stop()));
	}

	private static BlockState pipe(Block block, Direction... open) {
		BlockState state = block.defaultBlockState();
		for (Direction direction : open)
			state = state.setValue(ManifoldBlock.CONNECTIONS.get(direction), true);
		return state;
	}

	/** Four screenshots two ticks apart from one viewpoint, to see things move. */
	private static void frames(String name, int tick, double x, double y, double z, float yaw, float pitch) {
		STEPS.add(new Step(tick, () -> view(x, y, z, yaw, pitch)));
		for (int i = 0; i < 4; i++) {
			int frame = i;
			STEPS.add(new Step(tick + 15 + i * 2, () -> shot(name + "_" + frame)));
		}
	}

	private static void ponder(String name, int tick, Supplier<ItemStack> component, int... shotsAfter) {
		STEPS.add(new Step(tick, () -> ScreenOpener.open(PonderUI.of(component.get()))));
		for (int after : shotsAfter)
			STEPS.add(new Step(tick + after, () -> shot("ponder_" + name + "_" + after)));
		STEPS.add(new Step(tick + shotsAfter[shotsAfter.length - 1] + 5, () -> Minecraft.getInstance()
			.setScreen(null)));
	}

	/** As {@link #ponder}, for the {@code scene}th page of a component with several. */
	private static void ponderScene(String name, int tick, Supplier<ItemStack> component, int scene, int... shotsAfter) {
		STEPS.add(new Step(tick, () -> {
			PonderUI ui = PonderUI.of(component.get());
			try {
				var scenes = PonderUI.class.getDeclaredField("scenes");
				scenes.setAccessible(true);
				Collections.swap((List<?>) scenes.get(ui), 0, scene);
			} catch (ReflectiveOperationException | RuntimeException e) {
				CreateEngines.LOGGER.error("Could not pick the {} Ponder scene", name, e);
			}
			ScreenOpener.open(ui);
		}));
		for (int after : shotsAfter)
			STEPS.add(new Step(tick + after, () -> shot("ponder_" + name + "_" + after)));
		STEPS.add(new Step(tick + shotsAfter[shotsAfter.length - 1] + 5, () -> Minecraft.getInstance()
			.setScreen(null)));
	}

	private static void goggles(String name, int tick, double x, double y, double z, float yaw, float pitch) {
		STEPS.add(new Step(tick, () -> view(x, y, z, yaw, pitch)));
		STEPS.add(new Step(tick + 15, () -> shot("goggles_" + name)));
	}

	// Animation smoothness: while the engine cranks, fires and speeds up, compare each frame's drawn crank angle with
	// how far its speed says it should have turned since the last frame
	private static final int MEASURE_FROM = 60;
	private static final int MEASURE_TO = 520;
	private static float lastTime = Float.NaN;
	private static float lastAngle;
	private static float lastCreateAngle;
	private static float worstJump;
	private static float worstCreateJump;
	private static int speedChanges;
	private static float lastSpeed;

	private static void onRenderFrame(RenderFrameEvent.Post event) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null || ticksInWorld < MEASURE_FROM)
			return;
		if (ticksInWorld > MEASURE_TO) {
			if (!Float.isNaN(lastTime)) {
				CreateEngines.LOGGER.info(
					"Smoke test smoothness: worst crank jump {} degrees beyond its speed over {} speed changes; "
						+ "time x speed would have jumped {} degrees", worstJump, speedChanges, worstCreateJump);
				lastTime = Float.NaN;
			}
			return;
		}
		if (!(minecraft.level.getBlockEntity(origin.offset(TestEngine.THROW)) instanceof CrankshaftBlockEntity crank))
			return;
		float time = AnimationTickHolder.getRenderTime(minecraft.level);
		float speed = crank.getTheoreticalSpeed();
		float angle = EnginePoses.crankAngle(crank, time);
		float createAngle = CrankGeometry.renderAngle(time, speed);
		if (!Float.isNaN(lastTime) && time > lastTime) {
			float expected = (time - lastTime) * speed * 3 / 10;
			float jump = Math.abs(Mth.wrapDegrees(angle - lastAngle - expected));
			worstJump = Math.max(worstJump, jump);
			worstCreateJump = Math.max(worstCreateJump, Math.abs(Mth.wrapDegrees(createAngle - lastCreateAngle - expected)));
			if (speed != lastSpeed)
				speedChanges++;
		}
		lastTime = time;
		lastAngle = angle;
		lastCreateAngle = createAngle;
		lastSpeed = speed;
	}

	private static void onClientTick(ClientTickEvent.Post event) {
		Minecraft minecraft = Minecraft.getInstance();
		if (!worldRequested) {
			if (minecraft.screen instanceof TitleScreen || minecraft.screen instanceof AccessibilityOnboardingScreen) {
				worldRequested = true;
				minecraft.options.pauseOnLostFocus = false;
				createWorld(minecraft);
			}
			return;
		}
		if (minecraft.player == null || minecraft.level == null || minecraft.getSingleplayerServer() == null)
			return;
		ticksInWorld++;
		for (Step step : STEPS)
			if (step.tick() == ticksInWorld)
				step.action()
					.run();
	}

	private static void createWorld(Minecraft minecraft) {
		LevelSettings settings = new LevelSettings("engine_smoke_" + System.currentTimeMillis(), GameType.CREATIVE,
			false, Difficulty.PEACEFUL, true, new GameRules(), WorldDataConfiguration.DEFAULT);
		WorldOptions options = new WorldOptions(0, false, false);
		minecraft.createWorldOpenFlows()
			.createFreshLevel(settings.levelName(), settings, options, registries -> registries
				.registryOrThrow(Registries.WORLD_PRESET)
				.getHolderOrThrow(WorldPresets.FLAT)
				.value()
				.createWorldDimensions(), minecraft.screen);
	}

	/** Stands the camera on an invisible barrier at a position relative to the test structure's origin. */
	private static void view(double x, double y, double z, float yaw, float pitch) {
		double wx = origin.getX() + x;
		double wy = origin.getY() + y;
		double wz = origin.getZ() + z;
		server(server -> {
			command(server, String.format(java.util.Locale.ROOT, "setblock %d %d %d minecraft:barrier",
				(int) Math.floor(wx), (int) Math.floor(wy) - 1, (int) Math.floor(wz)));
			command(server, String.format(java.util.Locale.ROOT, "tp @a %.2f %.2f %.2f %.1f %.1f", wx, wy, wz, yaw,
				pitch));
		});
	}

	private static void shot(String name) {
		Minecraft minecraft = Minecraft.getInstance();
		Screenshot.grab(minecraft.gameDirectory, "engine_" + name + ".png", minecraft.getMainRenderTarget(),
			message -> CreateEngines.LOGGER.info("Smoke test screenshot: {}", message.getString()));
	}

	private static void server(java.util.function.Consumer<MinecraftServer> action) {
		MinecraftServer server = Minecraft.getInstance()
			.getSingleplayerServer();
		if (server != null)
			server.execute(() -> action.accept(server));
	}

	private static void command(MinecraftServer server, String command) {
		server.getCommands()
			.performPrefixedCommand(server.createCommandSourceStack(), command);
	}
}
