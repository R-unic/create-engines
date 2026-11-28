package com.createengines.client;

import static com.createengines.client.VisualSmokeTest.*;

import java.util.ArrayList;
import java.util.List;

import com.createengines.CreateEngines;
import com.createengines.content.CasingPaint;
import com.createengines.content.EngineCasingBlock;
import com.createengines.content.airflow.PortOpeningBlock;
import com.createengines.content.cylinder.CylinderBlock;
import com.createengines.content.governor.GovernorBlockEntity;
import com.createengines.content.tool.TimingToolItem;
import com.createengines.engine.topology.FiringPlanner;
import com.createengines.content.head.CylinderHeadBlock;
import com.createengines.content.head.CylinderHeadBlockEntity;
import com.createengines.engine.CrankGeometry;
import com.createengines.gametest.TestEngine;
import com.createengines.registry.CECasingBlocks;
import com.createengines.registry.CEBlocks;
import com.createengines.registry.CEFluids;
import com.createengines.registry.CEItems;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/** Scenes of the newer blocks, casings and rain cap for the visual smoke test. Part of {@link VisualSmokeTest}, and like it kept out of the jar. */
final class VisualSmokeTestScenes {

	private VisualSmokeTestScenes() {
	}

	/**
	 * A muffler in the test engine's exhaust, engine casing round its cylinder, and an oil pan with a comparator and a lamp
	 * against its crank, then the goggles on the muffled crank and pan, and every Ponder page that is new or gained steps;
	 * then quits.
	 */
	static void newBlocks(int start) {
		STEPS.add(new Step(start, () -> {
			Minecraft.getInstance().options.hideGui = true;
			server(server -> {
				var level = server.overworld();
				// The outlet moves out a block and a muffler takes its place against the head
				level.setBlock(origin.offset(TestEngine.EXHAUST.west()), CEBlocks.EXHAUST_OUTLET.getDefaultState()
					.setValue(PortOpeningBlock.FACING, Direction.WEST), 3);
				level.setBlock(origin.offset(TestEngine.EXHAUST), CEBlocks.MUFFLER.getDefaultState()
					.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.AXIS,
						Direction.Axis.X), 3);
				// Casing round the cylinder and the east side of the head, leaving the pipes' faces clear
				for (BlockPos beside : List.of(new BlockPos(1, 3, 2), new BlockPos(3, 3, 2), new BlockPos(2, 3, 1),
					new BlockPos(2, 3, 3), new BlockPos(3, 4, 2)))
					level.setBlock(origin.offset(beside), CECasingBlocks.ENGINE_CASING.getDefaultState(), 3);
				// An oil pan under the crank with oil in it, read by a comparator that lights a lamp
				BlockPos pan = origin.offset(3, 1, 2);
				level.setBlock(pan, CEBlocks.SUMP.getDefaultState(), 3);
				level.setBlock(origin.offset(4, 0, 2), Blocks.STONE.defaultBlockState(), 3);
				level.setBlock(origin.offset(4, 1, 2), Blocks.COMPARATOR.defaultBlockState()
					.setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING, Direction.WEST), 3);
				level.setBlock(origin.offset(5, 1, 2), Blocks.REDSTONE_LAMP.defaultBlockState(), 3);
				if (level.getBlockEntity(pan) instanceof com.createengines.content.sump.SumpBlockEntity sump)
					sump.tank()
						.fill(new FluidStack(CEFluids.ENGINE_OIL.get()
							.getSource(), 500), IFluidHandler.FluidAction.EXECUTE);
			});
		}));
		frames("muffler", start + 60, -2.0, 5.0, -0.5, -60, 22);
		frames("casing", start + 100, 2.5, 5.2, -2.5, 0, 24);
		frames("pan_comparator", start + 140, 4.5, 3.2, -1.5, 0, 28);
		STEPS.add(new Step(start + 180, () -> view(-2.5, 6.5, -1.5, -55, 37)));
		STEPS.add(new Step(start + 195, () -> shot("blocks_overview")));
		STEPS.add(new Step(start + 200, () -> Minecraft.getInstance().options.hideGui = false));
		goggles("muffled_crank", start + 205, 1.5, 2.0, 0.4, 0, 28);
		goggles("sump", start + 235, 3.5, 2.0, 0.4, 0, 28);
		// The pages that are new, and those that gained steps
		ponder("refinery", start + 300, CEBlocks.REBOILER::asStack, 80, 200, 380, 560, 700, 860);
		// The reboiler's second page; its first is the column itself
		ponderScene("refinery_lean", start + 1300, CEBlocks.REBOILER::asStack, 1, 100, 250, 400, 560);
		ponder("soot_trap", start + 2100, CEBlocks.SOOT_TRAP::asStack, 100, 250, 400, 580);
		ponder("jerry_can", start + 2900, CEItems.JERRY_CAN::asStack, 100, 250, 400);
		STEPS.add(new Step(start + 3700, () -> Minecraft.getInstance()
			.stop()));
	}

	static final BlockPos REFINERY_OFFSET = new BlockPos(0, 0, 14);

	/**
	 * A 2x2 column over a seething burner fed plant oil, a flare stack on its condenser, and a basin of plant oil on
	 * a burner beside it; logs the basin's goggle lines ("REFINERY SMOKE") then takes pictures. Then the run ends.
	 */
	static void refinery(int start) {
		STEPS.add(new Step(start, () -> server(server -> {
			var level = server.overworld();
			BlockPos base = origin.offset(REFINERY_OFFSET);
			for (int x = 0; x < 2; x++)
				for (int z = 0; z < 2; z++) {
					level.setBlock(base.offset(x, 0, z), CEBlocks.HEAT_EXCHANGER.getDefaultState()
						.setValue(com.simibubi.create.content.processing.burner.BlazeBurnerBlock.HEAT_LEVEL,
							com.simibubi.create.content.processing.burner.BlazeBurnerBlock.HeatLevel.KINDLED), 3);
					level.setBlock(base.offset(x, 1, z), CEBlocks.REBOILER.getDefaultState(), 3);
					level.setBlock(base.offset(x, 2, z), CEBlocks.COLUMN_SECTION.getDefaultState(), 3);
					level.setBlock(base.offset(x, 3, z), CEBlocks.COLUMN_SECTION.getDefaultState(), 3);
					level.setBlock(base.offset(x, 4, z), CEBlocks.CONDENSER.getDefaultState(), 3);
				}
			level.setBlock(base.offset(0, 5, 0), CEBlocks.FLARE_STACK.getDefaultState(), 3);
			// A leaning column of its own, west of the first: straight for two layers, then a step east and up
			BlockPos lean = base.offset(-6, 0, 0);
			level.setBlock(lean, CEBlocks.HEAT_EXCHANGER.getDefaultState(), 3);
			level.setBlock(lean.above(), CEBlocks.REBOILER.getDefaultState(), 3);
			int[] layerX = {0, 0, 1, 1, 2};
			for (int i = 0; i < layerX.length; i++)
				level.setBlock(lean.offset(layerX[i], 2 + i, 0), CEBlocks.COLUMN_SECTION.getDefaultState(), 3);
			level.setBlock(lean.offset(2, 2 + layerX.length, 0), CEBlocks.CONDENSER.getDefaultState(), 3);
			BlockPos basin = base.offset(5, 1, 0);
			level.setBlock(basin.below(), com.simibubi.create.AllBlocks.BLAZE_BURNER.getDefaultState()
				.setValue(com.simibubi.create.content.processing.burner.BlazeBurnerBlock.HEAT_LEVEL,
					com.simibubi.create.content.processing.burner.BlazeBurnerBlock.HeatLevel.KINDLED), 3);
			level.setBlock(basin, com.simibubi.create.AllBlocks.BASIN.getDefaultState(), 3);
		})));
		STEPS.add(new Step(start + 40, () -> server(server -> {
			var level = server.overworld();
			BlockPos base = origin.offset(REFINERY_OFFSET);
			var feed = level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK,
				base.above(), Direction.UP);
			if (feed != null)
				feed.fill(new FluidStack(CEFluids.PLANT_OIL.get()
					.getSource(), 1000), IFluidHandler.FluidAction.EXECUTE);
			var coal = level.getBlockEntity(base.above());
			if (coal instanceof com.createengines.content.refinery.ColumnPortBlockEntity port)
				port.insertCatalyst(new ItemStack(net.minecraft.world.item.Items.COAL, 16));
			if (level.getBlockEntity(base.offset(5, 1, 0)) instanceof com.simibubi.create.content.processing.basin.BasinBlockEntity basin)
				basin.getTanks()
					.getFirst()
					.getPrimaryHandler()
					.fill(new FluidStack(CEFluids.PLANT_OIL.get()
						.getSource(), 500), IFluidHandler.FluidAction.EXECUTE);
		})));
		STEPS.add(new Step(start + 110, () -> server(server -> {
			if (server.overworld()
				.getBlockEntity(origin.offset(REFINERY_OFFSET)
					.above()) instanceof com.createengines.content.refinery.ColumnPortBlockEntity port)
				CreateEngines.LOGGER.info("REFINERY SMOKE column {} heat {} rate {} catalyst {}", port.limit(), port.heat(),
					port.rate(), port.catalystStack());
		})));
		// The client's own goggle text for the basin, which is where the mixin must have applied
		STEPS.add(new Step(start + 120, () -> {
			var level = Minecraft.getInstance().level;
			BlockPos basin = origin.offset(REFINERY_OFFSET)
				.offset(5, 1, 0);
			if (level != null && level.getBlockEntity(basin) instanceof com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation goggles) {
				List<net.minecraft.network.chat.Component> lines = new ArrayList<>();
				goggles.addToGoggleTooltip(lines, false);
				boolean hinted = lines.stream()
					.anyMatch(l -> l.getString()
						.contains("refining column would make"));
				CreateEngines.LOGGER.info("REFINERY SMOKE basin goggles {} lines, hint {}: {}", lines.size(),
					hinted ? "PRESENT" : "MISSING", lines.stream()
						.map(net.minecraft.network.chat.Component::getString)
						.toList());
				if (!hinted)
					CreateEngines.LOGGER.error("REFINERY SMOKE FAIL: the basin goggles carry no column hint");
			} else
				CreateEngines.LOGGER.error("REFINERY SMOKE FAIL: no basin on the client at {}", basin);
		}));
		frames("refinery_column", start + 160, 3.0, 6.0, 9.0, 0, 18);
		frames("refinery_top", start + 200, 6.0, 8.0, 10.0, 45, 30);
		frames("refinery_basin", start + 240, 8.5, 4.0, 12.0, 20, 25);
		frames("refinery_seam_top", start + 280, 1.0, 5.0, 11.0, 0, 15);
		frames("refinery_seam_low", start + 320, 1.0, 2.0, 11.0, 0, 10);
		frames("refinery_overhead", start + 360, 1.0, 9.0, 14.6, 0, 85);
		STEPS.add(new Step(start + 410, () -> server(server -> {
			var level = server.overworld();
			BlockPos spot = origin.offset(REFINERY_OFFSET)
				.offset(9, 1, 0);
			level.setBlock(spot, CEBlocks.SOOT_TRAP.getDefaultState(), 3);
			level.setBlock(spot.offset(3, 0, 0), Blocks.STONE.defaultBlockState(), 3);
			level.setBlock(spot.offset(5, 0, 0), Blocks.STONE.defaultBlockState(), 3);
			command(server, "summon minecraft:item_frame " + (spot.getX() + 3) + " " + spot.getY() + " "
				+ (spot.getZ() - 1) + " {Facing:2b,Invulnerable:1b,Item:{id:\"create_engines:soot\",count:1}}");
			command(server, "summon minecraft:item_frame " + (spot.getX() + 5) + " " + spot.getY() + " "
				+ (spot.getZ() - 1) + " {Facing:2b,Invulnerable:1b,Item:{id:\"create_engines:jerry_can\",count:1}}");
		})));
		frames("refinery_trap_and_items", start + 440, 11.5, 3.2, 10.5, 0, 12);
		frames("refinery_lean_side", start + 470, -5.0, 5.0, 8.5, 0, 6);
		frames("refinery_lean_close", start + 500, -5.0, 4.5, 11.0, 0, 8);
		frames("refinery_below", start + 540, 4.0, 0.6, 12.0, 25, -25);
		STEPS.add(new Step(start + 620, () -> Minecraft.getInstance()
			.stop()));
	}

	/** The firing order, compact V and mixture pages, from {@code start}; then quits. */
	static void newScenes(int start) {
		ponderScene("firing_order", start, CEBlocks.CAM_LOBE::asStack, 1, 120, 400, 600, 780, 960, 1350, 1560);
		ponderScene("compact_v", start + 1700, CEBlocks.CAM_LOBE::asStack, 2, 250, 450, 700);
		ponder("mixture", start + 2500, CEItems.FUEL_INJECTOR::asStack, 500, 750, 900, 1000);
		ponder("supercharger", start + 3600, CEBlocks.SUPERCHARGER::asStack, 250, 600, 900, 1250, 1600);
		management(start + 5400);
	}

	/** Where the engine-management engine stands, relative to the first. */
	static final BlockPos MANAGEMENT_OFFSET = new BlockPos(36, 0, 0);

	/**
	 * A fresh engine with a MAF sensor in its intake and the injector turned up so the sensor has something to cut; then
	 * its Ponder page, and the end of the run.
	 */
	static void management(int start) {
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
	static final BlockPos PRECISION_OFFSET = new BlockPos(72, 0, 0);

	/**
	 * A fresh engine built from precision parts under a governor, then the pages for the governor, the timing tool and
	 * the precision parts, and the end of the run.
	 */
	static void precision(int start) {
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
					be.target.setValue(80);
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
	static final BlockPos COUPLED_OFFSET = new BlockPos(96, 0, 0);

	/** Two crank lines geared together, timed as one engine, then the page for coupled crankshafts and the end of the run. */
	static void coupled(int start) {
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
	static final BlockPos ROTARY_OFFSET = new BlockPos(108, 0, 0);

	/**
	 * A bare rotor housing turned slowly by a motor on its west end, filmed down its open east end and from the side, to
	 * look at how the rotor sits and moves in it.
	 */
	static void rotaryBench(int start) {
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
	static void rotary(int start) {
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
	static void newParts(int start) {
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

	/**
	 * The first engine with its camshaft moved down a layer to sit against the cylinder block (as the pushrod GameTest
	 * does), so its valves are worked through pushrods and rockers. Photographed along the cam's axis, where the rods and
	 * rockers are in profile, and from the cam's side, over a whole turn of the cam; then the run ends.
	 */
	static void pushrods(int start) {
		STEPS.add(new Step(start, () -> server(server -> {
			var level = server.overworld();
			for (int x = 2; x <= 5; x++)
				level.setBlock(origin.offset(x, 4, 3), Blocks.AIR.defaultBlockState(), 3);
			level.setBlock(origin.offset(6, 3, 3), Blocks.AIR.defaultBlockState(), 3);
			level.setBlock(origin.offset(6, 4, 3), Blocks.AIR.defaultBlockState(), 3);
			BlockPos lobe = origin.offset(2, 3, 3);
			level.setBlock(lobe, TestEngine.axisX(CEBlocks.CAM_LOBE.getDefaultState()), 3);
			for (int x = 3; x <= 4; x++)
				level.setBlock(origin.offset(x, 3, 3), TestEngine.axisX(CEBlocks.CAMSHAFT.getDefaultState()), 3);
			double tdc = CrankGeometry.topDeadCentreAngle(net.minecraft.core.Direction.Axis.X, 0, Direction.UP);
			if (level.getBlockEntity(lobe) instanceof com.createengines.content.camshaft.CamshaftBlockEntity cam)
				cam.lobeAngle.setValue((int) Math.round(((tdc / 2) % 180 + 180) % 180
					/ com.createengines.content.camshaft.CamshaftBlockEntity.LOBE_ANGLE_STEP));
		})));
		STEPS.add(new Step(start + 200, () -> server(server -> server.overworld()
			.removeBlock(origin.offset(TestEngine.STARTER_POWER), false))));
		STEPS.add(new Step(start + 330, () -> view(-0.8, 3.9, 3.3, -90, 0)));
		for (int i = 0; i < 8; i++) {
			int frame = i;
			STEPS.add(new Step(start + 345 + i * 3, () -> shot("pushrods_axis_" + frame)));
		}
		STEPS.add(new Step(start + 380, () -> view(2.5, 4.6, 5.6, 180, 15)));
		for (int i = 0; i < 8; i++) {
			int frame = i;
			STEPS.add(new Step(start + 395 + i * 3, () -> shot("pushrods_side_" + frame)));
		}
		STEPS.add(new Step(start + 440, () -> Minecraft.getInstance()
			.stop()));
	}

	/**
	 * A wall of Engine Casing, three plates high (plain, riveted, louvred) and nine dyes wide (none and eight colours),
	 * photographed face on; then the run ends. It shows that the plates join, that the tint reaches them and that the
	 * rivets and slats survive it.
	 */
	static void casings(int start) {
		var paints = List.of(CasingPaint.NONE, CasingPaint.WHITE, CasingPaint.ORANGE, CasingPaint.YELLOW,
			CasingPaint.LIME, CasingPaint.CYAN, CasingPaint.BLUE, CasingPaint.PURPLE, CasingPaint.BLACK);
		var plates = List.of(CECasingBlocks.ENGINE_CASING, CECasingBlocks.ENGINE_RIVETED_CASING, CECasingBlocks.ENGINE_LOUVRED_CASING);
		STEPS.add(new Step(start, () -> server(server -> {
			var level = server.overworld();
			for (int row = 0; row < plates.size(); row++)
				for (int column = 0; column < paints.size(); column++)
					level.setBlock(origin.offset(column, 6 + row, 0), plates.get(row)
						.getDefaultState()
						.setValue(EngineCasingBlock.PAINT, paints.get(column)), 3);
			// And below them, casing clad onto a part, in a dye each (the shaft parts run along the wall)
			var clad = com.createengines.content.Encasing.ENCASED;
			dyed(level, origin.offset(0, 5, 0), CEBlocks.CYLINDER.getDefaultState()
				.setValue(clad, true), CasingPaint.RED);
			dyed(level, origin.offset(1, 5, 0), TestEngine.axisX(CEBlocks.CRANKSHAFT.getDefaultState())
				.setValue(clad, true), CasingPaint.BLUE);
			dyed(level, origin.offset(2, 5, 0), TestEngine.axisX(CEBlocks.CAMSHAFT.getDefaultState())
				.setValue(clad, true), CasingPaint.LIME);
			dyed(level, origin.offset(3, 5, 0), CEBlocks.INTAKE_MANIFOLD.getDefaultState()
				.setValue(clad, true), CasingPaint.YELLOW);
			dyed(level, origin.offset(4, 5, 0), TestEngine.axisX(CECasingBlocks.ENGINE_CASED_SHAFT.getDefaultState()),
				CasingPaint.PURPLE);
			dyed(level, origin.offset(5, 5, 0), TestEngine.axisX(CECasingBlocks.ENGINE_CASED_COGWHEEL.getDefaultState()),
				CasingPaint.ORANGE);
			dyed(level, origin.offset(6, 5, 0), TestEngine.axisX(CECasingBlocks.ENGINE_CASED_LARGE_COGWHEEL.getDefaultState()),
				CasingPaint.CYAN);
		})));
		STEPS.add(new Step(start + 60, () -> view(4.5, 6.5, 7, 180, 0)));
		for (int i = 0; i < 3; i++) {
			int frame = i;
			STEPS.add(new Step(start + 90 + i * 5, () -> shot("casings_" + frame)));
		}
		STEPS.add(new Step(start + 120, () -> Minecraft.getInstance()
			.stop()));
	}

	/** Places a part and dyes its casing, which a clad part holds in its block entity. */
	static void dyed(net.minecraft.server.level.ServerLevel level, net.minecraft.core.BlockPos pos,
		net.minecraft.world.level.block.state.BlockState state, CasingPaint paint) {
		level.setBlock(pos, state, 3);
		if (level.getBlockEntity(pos) instanceof com.createengines.content.PaintedBlockEntity held)
			held.setCasingPaint(paint);
	}

	/**
	 * A rain cap on the first engine's exhaust outlet, photographed close up from the end and from the side over a run of
	 * ticks, so some frames catch the flap lifted by a pulse; then the run ends.
	 */
	static void rainCap(int start) {
		STEPS.add(new Step(start, () -> server(server -> server.overworld()
			.setBlock(origin.offset(TestEngine.EXHAUST.west()), CEBlocks.RAIN_CAP.getDefaultState()
				.setValue(com.createengines.content.raincap.RainCapBlock.FACING, Direction.WEST), 3))));
		STEPS.add(new Step(start + 200, () -> server(server -> server.overworld()
			.removeBlock(origin.offset(TestEngine.STARTER_POWER), false))));
		STEPS.add(new Step(start + 330, () -> view(-1.2, 4.9, 2.5, -90, 10)));
		for (int i = 0; i < 10; i++) {
			int frame = i;
			STEPS.add(new Step(start + 345 + i * 2, () -> shot("raincap_end_" + frame)));
		}
		STEPS.add(new Step(start + 380, () -> view(0.5, 4.7, 4.3, 180, 5)));
		for (int i = 0; i < 10; i++) {
			int frame = i;
			STEPS.add(new Step(start + 395 + i * 2, () -> shot("raincap_side_" + frame)));
		}
		// Then its Ponder page, which should show the flap working
		ponder("rain_cap", start + 440, CEBlocks.RAIN_CAP::asStack, 100, 250, 335, 350, 366, 372, 378, 384, 390, 396, 560, 700);
		STEPS.add(new Step(start + 1200, () -> Minecraft.getInstance()
			.stop()));
	}

	/**
	 * Window casing on the running first engine's crank journals and its throw, photographed from the front over a turn of
	 * the crank, and a loose block of it beside them; then the run ends.
	 */
	static void windowCasing(int start) {
		STEPS.add(new Step(start, () -> server(server -> {
			var level = server.overworld();
			for (BlockPos part : List.of(TestEngine.PRIMARY, TestEngine.THROW, new BlockPos(3, 2, 2)))
				level.setBlock(origin.offset(part), level.getBlockState(origin.offset(part))
					.setValue(com.createengines.content.Encasing.ENCASED, true)
					.setValue(com.createengines.content.Encasing.WINDOWED, true), 3);
			level.setBlock(origin.offset(4, 2, 3), CECasingBlocks.ENGINE_WINDOW_CASING.getDefaultState(), 3);
			// A maker's plate on the cylinder's front, named and stamped
			BlockPos plate = origin.offset(2, 3, 3);
			level.setBlock(plate, CEBlocks.MAKER_PLATE.getDefaultState()
				.setValue(com.createengines.content.plate.MakerPlateBlock.FACING, Direction.SOUTH), 3);
			if (level.getBlockEntity(plate) instanceof com.createengines.content.plate.MakerPlateBlockEntity be) {
				be.setNameAndBuilder("Old Faithful the Third, Mk II", "Riley");
				be.stamp(new com.createengines.engine.sim.PlateFigures(24.5f, 96, 2400f, 128));
			}
		})));
		STEPS.add(new Step(start + 200, () -> server(server -> server.overworld()
			.removeBlock(origin.offset(TestEngine.STARTER_POWER), false))));
		STEPS.add(new Step(start + 330, () -> view(2.5, 3.6, 5.2, 180, 22)));
		for (int i = 0; i < 6; i++) {
			int frame = i;
			STEPS.add(new Step(start + 345 + i * 4, () -> shot("window_" + frame)));
		}
		STEPS.add(new Step(start + 360, () -> view(2.5, 3.6, 4.0, 180, 8)));
		STEPS.add(new Step(start + 370, () -> shot("plate_0")));
		STEPS.add(new Step(start + 380, () -> view(-1.2, 3.2, 2.5, -90, 15)));
		STEPS.add(new Step(start + 395, () -> shot("window_end")));
		STEPS.add(new Step(start + 430, () -> Minecraft.getInstance()
			.stop()));
	}
}
