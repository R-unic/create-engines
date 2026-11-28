package com.createengines.client;

import static com.createengines.client.ShowcaseRun.*;
import static com.createengines.client.ShowcaseRunWorks.*;
import static com.createengines.client.ShowcaseRunRover.*;

import java.util.ArrayList;
import java.util.List;

import com.createengines.CreateEngines;
import com.createengines.content.airflow.ManifoldBlock;
import com.createengines.content.airflow.PortOpeningBlock;
import com.createengines.content.airflow.Pipes;
import com.createengines.content.head.CylinderHeadBlockEntity;
import com.createengines.content.rotor.RotorHousingBlockEntity;
import com.createengines.content.starter.StarterBlock;
import com.createengines.content.tool.BankBuilderItem;
import com.createengines.gametest.TestEngine;
import com.createengines.registry.CECasingBlocks;
import com.createengines.registry.CEBlocks;
import com.createengines.registry.CEFluids;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlock;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/** Floors, engines, pipes and fuel for the showcase's pads. Part of {@link ShowcaseRun}, and like it kept out of the jar. */
final class ShowcaseRunBuild {

	private ShowcaseRunBuild() {
	}

	static final int PAD_X0 = -10;
	static final int PAD_X1 = 24;
	static final int PAD_Z0 = -10;
	static final int PAD_Z1 = 18;
	static final int WALL = 8;

	/**
	 * A courtyard over the grass: a dark tiled floor with a copper line round it, walls of deepslate brick with a wooden
	 * pillar every six blocks and a lantern on each, so every view has a backdrop instead of the horizon.
	 */
	static void floor(ServerLevel level, BlockPos corner, boolean walled) {
		BlockState tiles = Blocks.DEEPSLATE_TILES.defaultBlockState();
		BlockState polished = Blocks.POLISHED_DEEPSLATE.defaultBlockState();
		BlockState dark = Blocks.POLISHED_BLACKSTONE.defaultBlockState();
		BlockState line = Blocks.WAXED_CUT_COPPER.defaultBlockState();
		BlockState base = Blocks.POLISHED_DEEPSLATE.defaultBlockState();
		BlockState bricks = Blocks.STONE_BRICKS.defaultBlockState();
		BlockState mossy = Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
		BlockState cracked = Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
		BlockState band = Blocks.DEEPSLATE_BRICKS.defaultBlockState();
		BlockState cap = Blocks.STONE_BRICK_SLAB.defaultBlockState();
		BlockState pillar = Blocks.STRIPPED_DARK_OAK_LOG.defaultBlockState();
		BlockState copper = Blocks.WAXED_CUT_COPPER.defaultBlockState();
		BlockState lantern = Blocks.LANTERN.defaultBlockState();
		for (int x = PAD_X0; x <= PAD_X1; x++)
			for (int z = PAD_Z0; z <= PAD_Z1; z++) {
				boolean border = x == PAD_X0 || x == PAD_X1 || z == PAD_Z0 || z == PAD_Z1;
				boolean inner = !border && (x == PAD_X0 + 1 || x == PAD_X1 - 1 || z == PAD_Z0 + 1 || z == PAD_Z1 - 1);
				// A dark tiled floor: a chequer of tiles and polished slate, with the odd blackstone slab so it is not a grid
				int scatter = Math.floorMod(x * 31 + z * 17, 11);
				BlockState floor = inner ? line : scatter == 0 ? dark : (x + z) % 2 == 0 ? tiles : polished;
				level.setBlock(corner.offset(x, -1, z), border && walled ? bricks : floor, Block.UPDATE_CLIENTS);
				if (!border || !walled)
					continue;
				boolean post = ((x - PAD_X0) % 6 == 0 && (z == PAD_Z0 || z == PAD_Z1))
					|| ((z - PAD_Z0) % 6 == 0 && (x == PAD_X0 || x == PAD_X1));
				for (int y = 0; y < WALL; y++) {
					int hash = Math.floorMod(x * 7 + z * 13 + y * 5, 17);
					// A plinth of slate, weathered brick above it, a darker band under the cap, a copper capital on each pillar
					BlockState wall = y < 2 ? base : y == WALL - 2 ? band : hash == 0 ? mossy : hash == 1 ? cracked : bricks;
					BlockState here = post ? (y == WALL - 1 ? copper : pillar) : wall;
					level.setBlock(corner.offset(x, y, z), here, Block.UPDATE_CLIENTS);
				}
				level.setBlock(corner.offset(x, WALL, z), post ? lantern : cap, Block.UPDATE_CLIENTS);
			}
	}

	/** A Creative Motor at {@code pos} turning at {@code rpm}. */
	static void motor(ServerLevel level, BlockPos pos, Direction facing, int rpm) {
		level.setBlock(pos, AllBlocks.CREATIVE_MOTOR.getDefaultState()
			.setValue(CreativeMotorBlock.FACING, facing), Block.UPDATE_ALL);
		if (level.getBlockEntity(pos) instanceof CreativeMotorBlockEntity motor)
			motor.generatedSpeed.setValue(rpm);
	}

	/** A one-block platform under a banked engine, so the engine and its starter stand on something. */
	static void dais(ServerLevel level, BlockPos at, int length) {
		BlockState stone = Blocks.POLISHED_ANDESITE.defaultBlockState();
		for (int x = -1; x <= length; x++)
			for (int z = CZ - 2; z <= CZ + 2; z++)
				level.setBlock(at.offset(x, 0, z), stone, Block.UPDATE_CLIENTS);
	}

	/** The crude single-cylinder diesel with a flywheel on its free end. */
	static void firstEngine(ServerLevel level, BlockPos at) {
		TestEngine.build(level, at, false);
		set(level, at, 0, 2, 2, axisX(CEBlocks.CAST_FLYWHEEL.getDefaultState()));
		register(at.offset(TestEngine.HEAD), Fuel.DIESEL);
	}

	/** A twin-rotor engine on gasoline. */
	static void rotary(ServerLevel level, BlockPos at) {
		TestEngine.buildRotary(level, at, true);
		TestEngine.addSecondRotor(level, at);
		set(level, at, 6, 2, 2, axisX(CEBlocks.CAST_FLYWHEEL.getDefaultState()));
		register(at.offset(TestEngine.HOUSING), Fuel.GASOLINE);
		register(at.offset(TestEngine.HOUSING_2), Fuel.GASOLINE);
	}

	/**
	 * A gasoline inline four with the electrical parts: an ignition coil on each head's plug and an injector assist on each
	 * head's injector, and above the starter's battery a Large Battery and a Dry Cell, which join it into one bank. The
	 * batteries start part full so their charge lamps show; the alternator keeps them going.
	 */
	static void electric(ServerLevel level, ServerPlayer player, BlockPos at) {
		int before = TANKS.size();
		bank(level, player, at, BankBuilderItem.Layout.INLINE, 4, Kit.plain(Fuel.GASOLINE));
		for (Tank tank : TANKS.subList(before, TANKS.size())) {
			BlockState head = level.getBlockState(tank.pos());
			if (head.hasProperty(com.createengines.content.head.CylinderHeadBlock.HAS_COIL))
				level.setBlock(tank.pos(), head.setValue(com.createengines.content.head.CylinderHeadBlock.HAS_ASSIST, true)
					.setValue(com.createengines.content.head.CylinderHeadBlock.HAS_COIL, true), Block.UPDATE_ALL);
		}
		set(level, at, 1, CY + 2, CZ, CEBlocks.LARGE_BATTERY.getDefaultState());
		set(level, at, 1, CY + 3, CZ, CEBlocks.DRY_CELL.getDefaultState());
		for (int y = 1; y <= 3; y++)
			if (level.getBlockEntity(at.offset(1, CY + y, CZ)) instanceof com.createengines.content.battery.BatteryBlockEntity battery)
				battery.charge(battery.capacity() * 0.35f);
	}

	/**
	 * A banked engine laid out by the Bank Builder: a crank along X with {@code count} throws side by side, a cam line
	 * driven from the crank's far end at half its speed, an intake pipe on one side of each bank and an exhaust pipe on
	 * the other, a flywheel on the end, and a starter underneath. The {@link Kit} says what else it wears: a turbo on the
	 * exhaust's east end (its compressor breathing from the east, its air coming back along the near side), a
	 * motor-driven blower with its intercooler and air intake at the intake's west end, or a plain throttle, muffler and
	 * outlet; and the instruments that go on a test-bed engine.
	 */
	static void bank(ServerLevel level, ServerPlayer player, BlockPos at, BankBuilderItem.Layout layout, int count,
		Kit kit) {
		bank(level, player, at, layout, count, kit, STAGE_ALL, false);
	}

	/** The parts of {@link #bank}, so a clip can lay an engine down in front of the camera one stage at a time. */
	static final int STAGE_STRUCTURE = 1;
	static final int STAGE_BANK = 2;
	static final int STAGE_FITTINGS = 4;
	static final int STAGE_POWER = 8;
	static final int STAGE_ALL = 15;

	static void bank(ServerLevel level, ServerPlayer player, BlockPos at, BankBuilderItem.Layout layout, int count,
		Kit kit, int stages, boolean precision) {
		Fuel fuel = kit.fuel();
		boolean spark = fuel == Fuel.GASOLINE;
		int lastThrow = count + 1;
		if ((stages & STAGE_STRUCTURE) != 0) {
		dais(level, at, lastThrow + 5);
		// Engine casing legs under the far journal, to match the starter and motor under the near one
		for (int y = 1; y < CY; y++)
			set(level, at, lastThrow + 1, y, CZ, CECasingBlocks.ENGINE_CASING.getDefaultState());

		// The crank line: a journal at each end of the throws, then a shaft, the small cog that drives the cam, a flywheel
		set(level, at, 1, CY, CZ, axisX(CEBlocks.CRANKSHAFT.getDefaultState()));
		for (int x = 2; x <= lastThrow; x++)
			set(level, at, x, CY, CZ, axisX(CEBlocks.CRANK_THROW.getDefaultState()));
		set(level, at, lastThrow + 1, CY, CZ, axisX(CEBlocks.CRANKSHAFT.getDefaultState()));
		set(level, at, lastThrow + 2, CY, CZ, axisX(AllBlocks.SHAFT.getDefaultState()));
		set(level, at, lastThrow + 3, CY, CZ, axisX(AllBlocks.COGWHEEL.getDefaultState()));
		set(level, at, lastThrow + 4, CY, CZ, axisX(CEBlocks.CAST_FLYWHEEL.getDefaultState()));

		// A starter under the first journal, a motor under that, a redstone block to engage it
		set(level, at, 1, CY - 1, CZ, CEBlocks.STARTER.getDefaultState()
			.setValue(StarterBlock.FACING, Direction.UP));
		set(level, at, 1, CY - 2, CZ, AllBlocks.CREATIVE_MOTOR.getDefaultState()
			.setValue(CreativeMotorBlock.FACING, Direction.UP));
		if (level.getBlockEntity(at.offset(1, CY - 2, CZ)) instanceof CreativeMotorBlockEntity motor)
			motor.generatedSpeed.setValue(TestEngine.MOTOR_RPM);
		if (stages == STAGE_ALL)
			set(level, at, 0, CY - 1, CZ, Blocks.REDSTONE_BLOCK.defaultBlockState());
		if (spark) {
			set(level, at, 0, CY, CZ, axisX(CEBlocks.ALTERNATOR.getDefaultState()));
			set(level, at, 1, CY + 1, CZ, CEBlocks.BATTERY.getDefaultState());
		}
		}

		if ((stages & STAGE_BANK) != 0) {
		// The cylinders, heads, plugs and cam lobes, as the Bank Builder lays them
		var result = BankBuilderItem.build(level, at.offset(2, CY, CZ), Direction.UP, layout, precision, player, true);
		CreateEngines.LOGGER.info("Showcase bank {} at {}: {}", layout, at, result);

		// The cam line is carried on from its last lobe to a large cog meshing with the small one on the crank
		for (BlockPos beside : BankBuilderItem.camOffsets(layout, Axis.X, Direction.UP)) {
			int y = CY + beside.getY();
			int z = CZ + beside.getZ();
			for (int x = lastThrow + 1; x <= lastThrow + 2; x++)
				set(level, at, x, y, z, axisX(CEBlocks.CAMSHAFT.getDefaultState()));
			set(level, at, lastThrow + 3, y, z, axisX(AllBlocks.LARGE_COGWHEEL.getDefaultState()));
		}
		}

		if ((stages & STAGE_FITTINGS) == 0)
			return;
		// Pipes and fittings on each bank
		int bankIndex = 0;
		for (Direction bore : BankBuilderItem.faces(layout, Axis.X, Direction.UP)) {
			boolean turbo = kit.turbos() == 2 || (kit.turbos() == 1 && bankIndex == 0);
			boolean blower = kit.blowers() == 2 || (kit.blowers() == 1 && bankIndex == 0);
			bankIndex++;
			Direction intake = intakeSide(bore);
			Direction exhaust = intake.getOpposite();
			List<BlockPos> intakeRow = new ArrayList<>();
			List<BlockPos> exhaustRow = new ArrayList<>();
			for (int x = 2; x <= lastThrow; x++) {
				BlockPos head = at.offset(x, CY, CZ)
					.relative(bore, 2);
				register(head, fuel);
				// Two blowers over-lean the default shot until nothing lights, so every head runs a rich one
				if (kit.blowers() == 2 && level.getBlockEntity(head) instanceof CylinderHeadBlockEntity be)
					be.metering.setValue(CylinderHeadBlockEntity.MAX_METERING);
				intakeRow.add(head.relative(intake));
				exhaustRow.add(head.relative(exhaust));
			}
			BlockPos intakeFirst = intakeRow.get(0);
			BlockPos exhaustLast = exhaustRow.get(exhaustRow.size() - 1);

			if (turbo) {
				// Exhaust runs into the turbine on the row's east end and out of a stack; the compressor breathes from the
				// east, and its air goes back along the near side, through an intercooler or a throttle, into the intake
				BlockPos turboPos = exhaustLast.east();
				Block turboBlock = kit.variableTurbo() ? CEBlocks.VARIABLE_TURBOCHARGER.get() : CEBlocks.TURBOCHARGER.get();
				level.setBlock(turboPos, turboBlock.defaultBlockState()
					.setValue(com.createengines.content.turbo.TurbochargerBlock.FACING, Direction.EAST), Block.UPDATE_ALL);
				level.setBlock(turboPos.east(), CEBlocks.AIR_INTAKE.getDefaultState()
					.setValue(PortOpeningBlock.FACING, Direction.EAST), Block.UPDATE_ALL);
				Direction stack = bore == Direction.DOWN ? exhaust : bore;
				level.setBlock(turboPos.relative(stack), CEBlocks.EXHAUST_OUTLET.getDefaultState()
					.setValue(PortOpeningBlock.FACING, stack), Block.UPDATE_ALL);
				BlockPos feed = turboPos.relative(intake);
				if (kit.intercooler())
					level.setBlock(feed, CEBlocks.INTERCOOLER.getDefaultState()
						.setValue(com.createengines.content.intercooler.IntercoolerBlock.AXIS, intake.getAxis()), Block.UPDATE_ALL);
				else
					level.setBlock(feed, throttleState(exhaust, intake), Block.UPDATE_ALL);
				pipe(level, feed.relative(intake), CEBlocks.INTAKE_MANIFOLD.get());
			} else {
				// Gas out through a muffler and an outlet at the east end
				BlockPos muffler = exhaustLast.east();
				level.setBlock(muffler, axisX(CEBlocks.MUFFLER.getDefaultState()), Block.UPDATE_ALL);
				level.setBlock(muffler.east(), CEBlocks.EXHAUST_OUTLET.getDefaultState()
					.setValue(PortOpeningBlock.FACING, Direction.EAST), Block.UPDATE_ALL);
			}

			if (blower) {
				// A blower on a shaft beside the intake's west end, a motor on its shaft, then the cooler and the air intake
				BlockPos west = intakeFirst.west();
				intakeRow.add(0, west);
				BlockPos blow = west.relative(intake);
				Block blowBlock = kit.variableBlower() ? CEBlocks.VARIABLE_SUPERCHARGER.get() : CEBlocks.SUPERCHARGER.get();
				level.setBlock(blow, axisX(blowBlock.defaultBlockState()), Block.UPDATE_ALL);
				level.setBlock(blow.west(), AllBlocks.CREATIVE_MOTOR.getDefaultState()
					.setValue(CreativeMotorBlock.FACING, Direction.EAST), Block.UPDATE_ALL);
				if (level.getBlockEntity(blow.west()) instanceof CreativeMotorBlockEntity drive)
					drive.generatedSpeed.setValue(kit.blowers() == 2 ? 64 : 96);
				BlockPos next = blow.relative(intake);
				if (kit.intercooler()) {
					level.setBlock(next, CEBlocks.INTERCOOLER.getDefaultState()
						.setValue(com.createengines.content.intercooler.IntercoolerBlock.AXIS, intake.getAxis()), Block.UPDATE_ALL);
					next = next.relative(intake);
				}
				level.setBlock(next, CEBlocks.AIR_INTAKE.getDefaultState()
					.setValue(PortOpeningBlock.FACING, intake), Block.UPDATE_ALL);
			} else if (!turbo) {
				// Air in through a throttle at the west end
				BlockPos throttle = intakeFirst.west();
				level.setBlock(throttle, throttleState(Direction.EAST, Direction.WEST), Block.UPDATE_ALL);
				level.setBlock(throttle.west(), CEBlocks.AIR_INTAKE.getDefaultState()
					.setValue(PortOpeningBlock.FACING, Direction.WEST), Block.UPDATE_ALL);
			}
			for (BlockPos pos : intakeRow)
				pipe(level, pos, CEBlocks.INTAKE_MANIFOLD.get());
			for (BlockPos pos : exhaustRow)
				pipe(level, pos, CEBlocks.EXHAUST_MANIFOLD.get());
		}

		if (kit.instruments()) {
			// A second flywheel on the near end, a dynamometer on the first journal, a governor on the last, a black box
			// beside it, and an oil sump in place of the last leg
			set(level, at, 0, CY, CZ, axisX(CEBlocks.CAST_FLYWHEEL.getDefaultState()));
			set(level, at, 1, CY + 1, CZ, CEBlocks.DYNO.getDefaultState());
			BlockPos governor = at.offset(lastThrow + 1, CY + 1, CZ);
			level.setBlock(governor, CEBlocks.GOVERNOR.getDefaultState(), Block.UPDATE_ALL);
			if (level.getBlockEntity(governor) instanceof com.createengines.content.governor.GovernorBlockEntity be)
				be.target.setValue(80);
			set(level, at, lastThrow + 1, CY, CZ + 1, CEBlocks.BLACK_BOX.getDefaultState());
			BlockPos pan = at.offset(lastThrow + 1, CY - 1, CZ);
			level.setBlock(pan, CEBlocks.SUMP.getDefaultState(), Block.UPDATE_ALL);
			if (level.getBlockEntity(pan) instanceof com.createengines.content.sump.SumpBlockEntity sump)
				sump.tank()
					.fill(new FluidStack(CEFluids.ENGINE_OIL.get()
						.getSource(), 500), IFluidHandler.FluidAction.EXECUTE);
		}
	}

	/** The side of a bank's heads that takes the intake pipe: the other is exhaust. */
	static Direction intakeSide(Direction bore) {
		for (Direction side : new Direction[] {Direction.NORTH, Direction.UP, Direction.SOUTH, Direction.DOWN})
			if (side.getAxis() != bore.getAxis())
				return side;
		throw new IllegalStateException();
	}

	static BlockState throttleState(Direction one, Direction other) {
		BlockState state = CEBlocks.THROTTLE.getDefaultState();
		return state.setValue(ManifoldBlock.CONNECTIONS.get(one), true)
			.setValue(ManifoldBlock.CONNECTIONS.get(other), true);
	}

	/** Places a pipe joined to everything round it that takes one. */
	static void pipe(ServerLevel level, BlockPos pos, ManifoldBlock block) {
		BlockState state = block.defaultBlockState();
		for (Direction side : Direction.values())
			if (Pipes.accepts(level.getBlockState(pos.relative(side)), side.getOpposite(), block.kind()))
				state = state.setValue(ManifoldBlock.CONNECTIONS.get(side), true);
		level.setBlock(pos, state, Block.UPDATE_ALL);
	}

	static void fuelUp() {
		server(server -> {
			ServerLevel level = server.overworld();
			if (aero())
				clearDrops(level);
			for (Tank tank : TANKS) {
				FluidStack fuel = new FluidStack(tank.fuel() == Fuel.DIESEL ? CEFluids.DIESEL.get()
					.getSource()
					: CEFluids.GASOLINE.get()
						.getSource(),
					CylinderHeadBlockEntity.CAPACITY);
				FluidStack oil = new FluidStack(CEFluids.ENGINE_OIL.get()
					.getSource(), CylinderHeadBlockEntity.CAPACITY);
				if (level.getBlockEntity(tank.pos()) instanceof CylinderHeadBlockEntity head) {
					head.tank()
						.fill(fuel, IFluidHandler.FluidAction.EXECUTE);
					head.oilTank()
						.fill(oil, IFluidHandler.FluidAction.EXECUTE);
				} else if (level.getBlockEntity(tank.pos()) instanceof RotorHousingBlockEntity housing) {
					housing.tank()
						.fill(fuel, IFluidHandler.FluidAction.EXECUTE);
					housing.oilTank()
						.fill(oil, IFluidHandler.FluidAction.EXECUTE);
				}
			}
			feedDepots(level);
			serviceStock(level);
			refineryStock(level);
			ShowcaseRunFeatures.stock(level);
			if (TRAILER)
				ShowcaseRunFuels.fill(level, pad(FUEL_BAY), CZ);
			// (the refinery and the service bay are fed by their own pumps and funnels: see refinery() and serviceStock())
		});
	}

	static String basinTanks(IFluidHandler basin) {
		StringBuilder out = new StringBuilder();
		for (int i = 0; i < basin.getTanks(); i++)
			out.append(basin.getFluidInTank(i)
				.getAmount())
				.append(' ')
				.append(basin.getFluidInTank(i)
					.getFluid()
					.getFluidType()
					.getDescriptionId())
				.append(i + 1 < basin.getTanks() ? " | " : "");
		return out.toString();
	}
}
