package com.createengines.client;

import static com.createengines.client.ShowcaseRun.*;
import static com.createengines.client.ShowcaseRunBuild.*;

import java.util.ArrayList;
import java.util.List;

import com.createengines.content.airflow.PortOpeningBlock;
import com.createengines.content.starter.StarterBlock;
import com.createengines.content.tool.BankBuilderItem;
import com.createengines.gametest.TestEngine;
import com.createengines.registry.CECasingBlocks;
import com.createengines.registry.CEBlocks;
import com.createengines.registry.CEFluids;
import com.createengines.registry.CEItems;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlock;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/** The trailer's casing, pumping, service, refinery and factory pads, and their stock. Part of {@link ShowcaseRun}, and like it kept out of the jar. */
final class ShowcaseRunWorks {

	private ShowcaseRunWorks() {
	}

	// -----------------------------------------------------------------------------------------------------------------
	// The trailer's later pads: the casing styles, the big block with its heavy starter, the service bay
	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * The four casing styles on one shaft line: a Create motor turns, in each style, a cased shaft, a cased cogwheel, a large
	 * one and another cased shaft, between two pillars of that style's casing (a dye can be used on those, which the clip does)
	 * with a Maker's Plate on the first.
	 */
	static void casing(ServerLevel level, BlockPos at) {
		dais(level, at, 19);
		set(level, at, 0, CY, CZ, AllBlocks.CREATIVE_MOTOR.getDefaultState()
			.setValue(CreativeMotorBlock.FACING, Direction.EAST));
		if (level.getBlockEntity(at.offset(0, CY, CZ)) instanceof CreativeMotorBlockEntity motor)
			motor.generatedSpeed.setValue(32);
		set(level, at, 1, CY, CZ, axisX(AllBlocks.SHAFT.getDefaultState()));
		Block[][] styles = {
			{ CECasingBlocks.ENGINE_CASING.get(), CECasingBlocks.ENGINE_CASED_SHAFT.get(), CECasingBlocks.ENGINE_CASED_COGWHEEL.get(),
				CECasingBlocks.ENGINE_CASED_LARGE_COGWHEEL.get() },
			{ CECasingBlocks.ENGINE_RIVETED_CASING.get(), CECasingBlocks.ENGINE_RIVETED_CASED_SHAFT.get(),
				CECasingBlocks.ENGINE_RIVETED_CASED_COGWHEEL.get(), CECasingBlocks.ENGINE_RIVETED_CASED_LARGE_COGWHEEL.get() },
			{ CECasingBlocks.ENGINE_LOUVRED_CASING.get(), CECasingBlocks.ENGINE_LOUVRED_CASED_SHAFT.get(),
				CECasingBlocks.ENGINE_LOUVRED_CASED_COGWHEEL.get(), CECasingBlocks.ENGINE_LOUVRED_CASED_LARGE_COGWHEEL.get() },
			{ CECasingBlocks.ENGINE_WINDOW_CASING.get(), CECasingBlocks.ENGINE_WINDOW_CASED_SHAFT.get(),
				CECasingBlocks.ENGINE_WINDOW_CASED_COGWHEEL.get(), CECasingBlocks.ENGINE_WINDOW_CASED_LARGE_COGWHEEL.get() } };
		for (int s = 0; s < styles.length; s++) {
			int x = 2 + 4 * s;
			Block[] style = styles[s];
			for (int pillar : new int[] { x, x + 3 }) {
				set(level, at, pillar, CY, CZ, axisX(style[1].defaultBlockState()));
				for (int y : new int[] { CY - 2, CY - 1, CY + 1, CY + 2 })
					set(level, at, pillar, y, CZ, style[0].defaultBlockState());
			}
			// An encased cogwheel only passes the drive along its axis through the shaft stubs it is given (Create sets them when a
			// cogwheel is placed beside a shaft), so both ends of both are on or the line stops at the first cogwheel
			set(level, at, x + 1, CY, CZ, throughShaft(axisX(style[2].defaultBlockState())));
			set(level, at, x + 2, CY, CZ, throughShaft(axisX(style[3].defaultBlockState())));
		}
		set(level, at, 2, CY + 1, CZ - 1, CEBlocks.MAKER_PLATE.getDefaultState()
			.setValue(com.createengines.content.plate.MakerPlateBlock.FACING, Direction.NORTH));
	}

	/** An encased cogwheel with a shaft stub out of both ends. */
	static BlockState throughShaft(BlockState cogwheel) {
		return cogwheel.setValue(com.simibubi.create.content.kinetics.simpleRelays.encased.EncasedCogwheelBlock.TOP_SHAFT, true)
			.setValue(com.simibubi.create.content.kinetics.simpleRelays.encased.EncasedCogwheelBlock.BOTTOM_SHAFT, true);
	}

	/** The pillar blocks of {@link #casing} that the dye clip paints: x along the line, one above the shaft. */
	static BlockPos casingPillar(int x, int above) {
		return pad(CASING_BAY).offset(x, CY + above, CZ);
	}

	/**
	 * A precision turbocharged V16 with the heavy starter under its first journal. It is built without its redstone block, so
	 * it stands cold until a cue lays one (see {@code defineCues}), and the heavy starter turns it over until it catches.
	 */
	static void bigBlock(ServerLevel level, ServerPlayer player, BlockPos at) {
		coldBank(level, player, at, BankBuilderItem.Layout.VEE, 8, new Kit(Fuel.GASOLINE, 2, false, 0, false, true, false));
	}

	/** A precision engine that has never turned, on a Heavy Starter: a shot's cue cranks it. */
	static void coldBank(ServerLevel level, ServerPlayer player, BlockPos at, BankBuilderItem.Layout layout, int count,
		Kit kit) {
		bank(level, player, at, layout, count, kit, STAGE_STRUCTURE | STAGE_BANK | STAGE_FITTINGS, true);
		set(level, at, 1, CY - 1, CZ, CEBlocks.HEAVY_STARTER.getDefaultState()
			.setValue(StarterBlock.FACING, Direction.UP));
	}

	// -----------------------------------------------------------------------------------------------------------------
	// Plumbing: real Create pumps, pipes, funnels, belts and tanks. Only a heat exchanger's heat, and the stock a tank or a barrel
	// starts with and is topped up from, are put there by the harness.
	// -----------------------------------------------------------------------------------------------------------------

	static BlockState axisState(Block block, Axis axis) {
		return block.defaultBlockState()
			.setValue(BlockStateProperties.AXIS, axis);
	}

	/** A Mechanical Pump at the cell, pushing out of its front ({@code flow}) and pulling on its back. */
	static final List<BlockPos> PUMPS = new ArrayList<>();

	static void pump(ServerLevel level, BlockPos at, int x, int y, int z, Direction flow) {
		PUMPS.add(at.offset(x, y, z));
		set(level, at, x, y, z, AllBlocks.MECHANICAL_PUMP.getDefaultState()
			.setValue(com.simibubi.create.content.kinetics.base.DirectionalKineticBlock.FACING, flow));
	}

	/**
	 * Turns something with a cogwheel beside it: a small cogwheel on {@code axis} at the cell (a pump on the same axis beside it
	 * meshes with it), and a Create motor behind it on that axis, on the {@code motorSide}, driving it at {@code rpm}.
	 */
	static void drive(ServerLevel level, BlockPos at, int x, int y, int z, Axis axis, Direction motorSide, int rpm) {
		set(level, at, x, y, z, axisState(AllBlocks.COGWHEEL.get(), axis));
		BlockPos motorAt = at.offset(x, y, z)
			.relative(motorSide);
		level.setBlock(motorAt, AllBlocks.CREATIVE_MOTOR.getDefaultState()
			.setValue(CreativeMotorBlock.FACING, motorSide.getOpposite()), Block.UPDATE_ALL);
		if (level.getBlockEntity(motorAt) instanceof CreativeMotorBlockEntity motor)
			motor.generatedSpeed.setValue(rpm);
	}

	/** Fluid pipes at the cells, each joined to the pipes, pumps and tanks beside it. */
	static void pipes(ServerLevel level, BlockPos at, int[]... cells) {
		var block = AllBlocks.FLUID_PIPE.get();
		for (int[] cell : cells)
			level.setBlock(at.offset(cell[0], cell[1], cell[2]), block.defaultBlockState(), Block.UPDATE_ALL);
		for (int[] cell : cells) {
			BlockPos pos = at.offset(cell[0], cell[1], cell[2]);
			BlockState state = block.updateBlockState(level.getBlockState(pos), Direction.UP, null, level, pos);
			level.setBlock(pos, state, Block.UPDATE_ALL);
			com.simibubi.create.content.fluids.FluidPropagator.propagateChangedPipe(level, pos, state);
		}
	}

	/** A Create fluid tank {@code height} blocks tall, the cell its lowest block (stacked tanks join into one). */
	static void tank(ServerLevel level, BlockPos at, int x, int y, int z, int height) {
		for (int i = 0; i < height; i++)
			set(level, at, x, y + i, z, AllBlocks.FLUID_TANK.getDefaultState());
	}

	static void stockTank(ServerLevel level, BlockPos pos, FluidStack fluid) {
		var handler = level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK, pos, Direction.UP);
		if (handler != null)
			handler.fill(fluid, IFluidHandler.FluidAction.EXECUTE);
	}

	/** A barrel with {@code stock} on offer, topped up to it again whenever it runs low. */
	static void stockBarrel(ServerLevel level, BlockPos pos, ItemStack... stock) {
		if (!(level.getBlockEntity(pos) instanceof net.minecraft.world.level.block.entity.BarrelBlockEntity barrel))
			return;
		for (int slot = 0; slot < stock.length && slot < barrel.getContainerSize(); slot++)
			if (barrel.getItem(slot)
				.isEmpty())
				barrel.setItem(slot, stock[slot].copy());
	}

	/**
	 * An Andesite Funnel on the side of whatever is behind it, its open face towards {@code towards}. An extracting one pulls
	 * items out of what is behind it and throws them out of its open face; an inserting one takes whatever lands in it into
	 * what is behind it.
	 */
	static void funnel(ServerLevel level, BlockPos at, int x, int y, int z, Direction towards, boolean extracting) {
		set(level, at, x, y, z, AllBlocks.ANDESITE_FUNNEL.getDefaultState()
			.setValue(com.simibubi.create.content.logistics.funnel.AbstractDirectionalFunnelBlock.FACING, towards)
			.setValue(com.simibubi.create.content.logistics.funnel.FunnelBlock.EXTRACTING, extracting));
	}

	/**
	 * A barrel's items into a machine, with two Andesite Funnels: an inserting one on the machine's face (the cell
	 * {@code (x, y, z)}, open towards {@code outward}) and an extracting one directly above it that pulls items out of a barrel
	 * on its {@code barrelSide} and lets them drop into the funnel below (a wall funnel throws its items into its own cell, so
	 * they fall straight down). The barrel stands on a block of casing. Returns the barrel's position.
	 */
	static BlockPos feedItems(ServerLevel level, BlockPos at, int x, int y, int z, Direction outward, Direction barrelSide) {
		funnel(level, at, x, y, z, outward, false);
		funnel(level, at, x, y + 1, z, barrelSide.getOpposite(), true);
		int bx = x + barrelSide.getStepX();
		int bz = z + barrelSide.getStepZ();
		set(level, at, bx, y, bz, AllBlocks.ANDESITE_CASING.getDefaultState());
		set(level, at, bx, y + 1, bz, Blocks.BARREL.defaultBlockState());
		return at.offset(bx, y + 1, bz);
	}

	static Runnable serviceUsedOilDrive;
	static BlockPos servicePan;
	static BlockPos serviceBasin;
	static BlockPos serviceStockTank;
	static BlockPos serviceBarrel;
	static BlockPos serviceBeltStart;
	static BlockPos serviceEnd;
	static BlockPos serviceEngineTank;
	static final List<BlockPos> SERVICE_HEADS = new ArrayList<>();

	/**
	 * A diesel inline four with an oil pan against its first journal, and, beside it, two small working lines. Everything is a
	 * real Create machine and the harness only keeps the stock topped up:
	 * <ul>
	 * <li>Oil exchange: the crank's near end runs through gearboxes into a Mechanical Mixer over a basin on a heat exchanger. A
	 * pump takes the pan's used oil to the basin, a funnel feeds charcoal and clay from a barrel, and a second pump returns
	 * the clean engine oil to the pan.</li>
	 * <li>Fuel: a spout filled by a pump from a tank fills jerry cans on a belt, the belt takes them to an item drain, a
	 * pump moves the fuel it drains into the engine's tank, and a last pump feeds it along a rail of pipes into every head.
	 * The empty cans are taken from the end of the belt and put back at its start, as a worker would.</li>
	 * </ul>
	 */
	static void service(ServerLevel level, ServerPlayer player, BlockPos at) {
		int before = TANKS.size();
		bank(level, player, at, BankBuilderItem.Layout.INLINE, 4, Kit.plain(Fuel.DIESEL));
		// The heads take their fuel from the engine's own tank, not from the harness
		SERVICE_HEADS.clear();
		for (Tank tank : TANKS.subList(before, TANKS.size()))
			SERVICE_HEADS.add(tank.pos());
		TANKS.subList(before, TANKS.size())
			.clear();
		int north = CZ - 1;
		// The oil pan, against the first journal's north face
		servicePan = at.offset(1, CY, north);
		level.setBlock(servicePan, CEBlocks.SUMP.getDefaultState(), Block.UPDATE_ALL);
		if (level.getBlockEntity(servicePan) instanceof com.createengines.content.sump.SumpBlockEntity sump) {
			// Oil that has been changed for a while: some of it used and waiting in the pan's used tank
			sump.tank()
				.fill(new FluidStack(CEFluids.ENGINE_OIL.get()
					.getSource(), 600), IFluidHandler.FluidAction.EXECUTE);
			sump.usedTank()
				.fill(new FluidStack(CEFluids.USED_OIL.get()
					.getSource(), 500), IFluidHandler.FluidAction.EXECUTE);
			sump.exchange.setValue(3);
		}

		// A radiator, full, against the third journal's north face, two blocks along from the pan
		BlockPos radiator = at.offset(3, CY, north);
		level.setBlock(radiator, CEBlocks.RADIATOR.getDefaultState(), Block.UPDATE_ALL);
		if (level.getBlockEntity(radiator) instanceof com.createengines.content.radiator.RadiatorBlockEntity loop)
			loop.tank()
				.fill(new FluidStack(CEFluids.COOLANT.get()
					.getSource(), 1000), IFluidHandler.FluidAction.EXECUTE);

		// The mixer: the crank's near end into a Create shaft (the crankshaft drives one, as on the wheeled car), a gearbox, and up
		// from that a cogwheel that the mixer meshes with: a mixer takes its rotation from a cog beside it, not from a shaft
		set(level, at, 0, CY, CZ, axisX(AllBlocks.SHAFT.getDefaultState()));
		set(level, at, -1, CY, CZ, axisState(AllBlocks.GEARBOX.get(), Axis.Z));
		set(level, at, -1, CY + 1, CZ, axisState(AllBlocks.COGWHEEL.get(), Axis.Y));
		set(level, at, -2, CY + 1, CZ, AllBlocks.MECHANICAL_MIXER.getDefaultState());
		set(level, at, -2, CY - 3, CZ, AllBlocks.ANDESITE_CASING.getDefaultState());
		set(level, at, -2, CY - 2, CZ, CEBlocks.HEAT_EXCHANGER.getDefaultState()
			.setValue(com.simibubi.create.content.processing.burner.BlazeBurnerBlock.HEAT_LEVEL,
				com.simibubi.create.content.processing.burner.BlazeBurnerBlock.HeatLevel.KINDLED));
		set(level, at, -2, CY - 1, CZ, AllBlocks.BASIN.getDefaultState());
		serviceBasin = at.offset(-2, CY - 1, CZ);
		// Used oil: out of the pan's west face, along the north side and down, through a pump, into the basin's west face
		pump(level, at, -3, CY - 1, CZ, Direction.EAST);
		// Its motor is switched on a little later (see schedule), once the charcoal and the clay have reached the basin: with
		// 250 mB of used oil and no charcoal and clay yet, the basin would coke it, not clean it
		serviceUsedOilDrive = () -> drive(level, at, -3, CY - 2, CZ, Axis.X, Direction.WEST, 64);
		pipes(level, at, new int[] { 0, CY, north }, new int[] { -1, CY, north }, new int[] { -2, CY, north },
			new int[] { -3, CY, north }, new int[] { -4, CY, north }, new int[] { -4, CY, CZ }, new int[] { -4, CY - 1, CZ });
		// Clean oil: out of the basin's north face through a pump, along and up into the pan's north face
		pump(level, at, -2, CY - 1, north, Direction.NORTH);
		drive(level, at, -3, CY - 1, north, Axis.Z, Direction.NORTH, 64);
		pipes(level, at, new int[] { -2, CY - 1, north - 1 }, new int[] { -1, CY - 1, north - 1 },
			new int[] { 0, CY - 1, north - 1 }, new int[] { 1, CY - 1, north - 1 }, new int[] { 1, CY, north - 1 });
		// Charcoal and a clay ball for the recipe, from a barrel, through a pair of funnels into the basin's south face
		serviceBarrel = feedItems(level, at, -2, CY - 1, CZ + 1, Direction.SOUTH, Direction.WEST);
		// Thirteen crafts' worth of charcoal and clay, in the barrel once
		ItemStack[] stock = new ItemStack[26];
		for (int i = 0; i < stock.length; i++)
			stock[i] = new ItemStack(i % 2 == 0 ? net.minecraft.world.item.Items.CHARCOAL : net.minecraft.world.item.Items.CLAY_BALL);
		stockBarrel(level, serviceBarrel, stock);

		// The fuel line along the engine's south side, y = 0: a belt from a motor-driven pulley at the west end to another at
		// the east end, a spout over it, an item drain after it
		int south = CZ + 4;
		set(level, at, -6, 0, south, axisState(AllBlocks.SHAFT.get(), Axis.Z));
		set(level, at, 2, 0, south, axisState(AllBlocks.SHAFT.get(), Axis.Z));
		level.setBlock(at.offset(-6, 0, south + 1), AllBlocks.CREATIVE_MOTOR.getDefaultState()
			.setValue(CreativeMotorBlock.FACING, Direction.NORTH), Block.UPDATE_ALL);
		if (level.getBlockEntity(at.offset(-6, 0, south + 1)) instanceof CreativeMotorBlockEntity motor)
			motor.generatedSpeed.setValue(64);
		com.simibubi.create.content.kinetics.belt.item.BeltConnectorItem.createBelts(level, at.offset(-6, 0, south),
			at.offset(2, 0, south));
		serviceBeltStart = at.offset(-5, 0, south);
		// A tank of diesel over the spout, a pump between them
		tank(level, at, -3, CY + 1, south, 1);
		serviceStockTank = at.offset(-3, CY + 1, south);
		pump(level, at, -3, CY, south, Direction.DOWN);
		drive(level, at, -4, CY, south, Axis.Y, Direction.UP, 64);
		set(level, at, -3, CY - 1, south, AllBlocks.SPOUT.getDefaultState());
		for (int y = 0; y <= CY + 1; y++)
			set(level, at, -3, y, south - 1, AllBlocks.ANDESITE_CASING.getDefaultState());
		// The drain at the belt's end, empties caught on a depot beyond it, a pump into the engine's tank
		set(level, at, 3, 0, south, AllBlocks.ITEM_DRAIN.getDefaultState());
		set(level, at, 4, 0, south, AllBlocks.DEPOT.getDefaultState());
		serviceEnd = at.offset(4, 0, south);
		pump(level, at, 3, 0, south - 1, Direction.NORTH);
		drive(level, at, 3, 1, south - 1, Axis.Z, Direction.SOUTH, 64);
		tank(level, at, 3, 0, south - 2, 4);
		serviceEngineTank = at.offset(3, 0, south - 2);
		// ... and from that tank a pump up and a rail of pipes over the heads, which take the fuel from their top faces
		pump(level, at, 3, 4, south - 2, Direction.UP);
		drive(level, at, 4, 4, south - 2, Axis.Y, Direction.UP, 64);
		pipes(level, at, new int[] { 3, 5, south - 2 }, new int[] { 3, 6, south - 2 }, new int[] { 3, 6, south - 3 },
			new int[] { 3, 6, CZ },
			new int[] { 2, 6, CZ }, new int[] { 4, 6, CZ }, new int[] { 5, 6, CZ });
	}

	/**
	 * The stock the service bay works from, and the one thing a worker would do: the diesel tank over the spout and the
	 * engine's own tank start with fuel, the barrel is kept stocked with charcoal and clay, an empty can is put at the start
	 * of the belt now and then, and the empties that have gathered at its end are taken away.
	 */
	static boolean serviceStarted;
	static int serviceCans;

	static void serviceStock(ServerLevel level) {
		if (serviceStockTank == null)
			return;
		boolean first = !serviceStarted;
		serviceStarted = true;
		stockTank(level, serviceStockTank, new FluidStack(CEFluids.DIESEL.get()
			.getSource(), 4000));
		if (first)
			stockTank(level, serviceEngineTank, new FluidStack(CEFluids.DIESEL.get()
				.getSource(), 3000));
		var depot = depotAt(level, serviceEnd);
		if (depot != null && !depot.getHeldItemStack()
			.isEmpty())
			depot.removeHeldItem();
		BlockPos start = serviceBeltStart.above();
		if (++serviceCans % 5 == 1 && level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(start)
			.inflate(0.4))
			.isEmpty()) {
			net.minecraft.world.entity.item.ItemEntity can = new net.minecraft.world.entity.item.ItemEntity(level, start.getX() + 0.5,
				start.getY() + 0.1, start.getZ() + 0.5, new ItemStack(CEItems.JERRY_CAN.get()), 0, 0, 0);
			can.setPickUpDelay(32767);
			level.addFreshEntity(can);
		}
	}

	static com.simibubi.create.content.logistics.depot.DepotBehaviour depotAt(ServerLevel level, BlockPos pos) {
		return level.getBlockEntity(pos) instanceof com.simibubi.create.content.logistics.depot.DepotBlockEntity depot
			? depot.getBehaviour(com.simibubi.create.content.logistics.depot.DepotBehaviour.TYPE) : null;
	}

	/** A rain cap on the mouth of every exhaust outlet on a pad's engine, which flaps with each exhaust pulse. */
	static void rainCaps(ServerLevel level, BlockPos at) {
		for (int x = -1; x <= 16; x++)
			for (int y = 0; y <= CY + 8; y++)
				for (int z = CZ - 4; z <= CZ + 4; z++) {
					BlockPos pos = at.offset(x, y, z);
					BlockState state = level.getBlockState(pos);
					if (!state.is(CEBlocks.EXHAUST_OUTLET.get()))
						continue;
					Direction face = state.getValue(PortOpeningBlock.FACING);
					if (level.getBlockState(pos.relative(face))
						.isAir())
						level.setBlock(pos.relative(face), CEBlocks.RAIN_CAP.getDefaultState()
							.setValue(com.createengines.content.raincap.RainCapBlock.FACING, face), Block.UPDATE_ALL);
				}
	}

	static BlockPos starterPower(Scene scene) {
		return scene.banked() ? new BlockPos(0, CY - 1, CZ) : TestEngine.STARTER_POWER;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// Building
	// -----------------------------------------------------------------------------------------------------------------

	static Block block(String id, Block fallback) {
		return BuiltInRegistries.BLOCK.getOptional(ResourceLocation.parse(id))
			.orElse(fallback);
	}

	/**
	 * A diesel inline four with a dual-mass flywheel, whose crank runs west out of its near end through a transmission into a row
	 * of Create machines on one shaft line: two
	 * presses side by side, each over a depot with iron on it, and an encased fan blowing west at the end. The presses really
	 * stamp (the iron is put back on the depots from {@link #fuelUp()}), so the row reads as a working factory. A mixer would
	 * not do: it takes its power from above, not from a shaft along the line.
	 */
	static void factory(ServerLevel level, ServerPlayer player, BlockPos at) {
		bank(level, player, at, BankBuilderItem.Layout.INLINE, 4, Kit.plain(Fuel.DIESEL));
		BlockState shaft = axisX(AllBlocks.SHAFT.getDefaultState());
		BlockState press = AllBlocks.MECHANICAL_PRESS.getDefaultState()
			.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST);
		FACTORY_DEPOTS.clear();
		// The crank's near end runs into a transmission on its auto setting, which shifts for itself, and the far end carries a
		// dual-mass flywheel where the plain engines have a cast one
		set(level, at, 0, CY, CZ, CEBlocks.TRANSMISSION.getDefaultState()
			.setValue(BlockStateProperties.AXIS, Axis.X));
		if (level.getBlockEntity(at.offset(0, CY, CZ)) instanceof com.createengines.content.transmission.TransmissionBlockEntity box)
			box.panel.setValue(com.createengines.content.transmission.Gears.AUTO);
		set(level, at, 4 + 1 + 4, CY, CZ, axisX(CEBlocks.DUAL_MASS_FLYWHEEL.getDefaultState()));
		for (int x = -1; x >= -2; x--) {
			set(level, at, x, CY, CZ, press);
			set(level, at, x, CY - 3, CZ, AllBlocks.ANDESITE_CASING.getDefaultState());
			set(level, at, x, CY - 2, CZ, AllBlocks.DEPOT.getDefaultState());
			set(level, at, x, CY - 1, CZ, Blocks.AIR.defaultBlockState());
			FACTORY_DEPOTS.add(at.offset(x, CY - 2, CZ));
		}
		// A powered gearshift reverses the drive for the fan: the crank turns this line the way that makes a fan at its end draw
		// air in, and the fan has to blow
		set(level, at, -3, CY, CZ, AllBlocks.GEARSHIFT.getDefaultState()
			.setValue(BlockStateProperties.AXIS, Axis.X));
		set(level, at, -3, CY + 1, CZ, Blocks.REDSTONE_BLOCK.defaultBlockState());
		// The fan blows west through a lit campfire onto a depot of raw beef, which it smokes: an empty fan blows at nothing
		set(level, at, -4, CY, CZ, AllBlocks.ENCASED_FAN.getDefaultState()
			.setValue(BlockStateProperties.FACING, Direction.WEST));
		set(level, at, -5, CY - 1, CZ, AllBlocks.ANDESITE_CASING.getDefaultState());
		set(level, at, -5, CY, CZ, Blocks.CAMPFIRE.defaultBlockState()
			.setValue(net.minecraft.world.level.block.CampfireBlock.LIT, true));
		for (int y = CY - 3; y <= CY - 1; y++)
			set(level, at, -6, y, CZ, AllBlocks.ANDESITE_CASING.getDefaultState());
		set(level, at, -6, CY, CZ, AllBlocks.DEPOT.getDefaultState());
		smokerDepot = at.offset(-6, CY, CZ);
		feedDepots(level);
	}

	/** The depot the factory's fan smokes beef on. */
	static BlockPos smokerDepot;

	/** The depots under the factory's presses. */
	static final List<BlockPos> FACTORY_DEPOTS = new ArrayList<>();

	/** Puts an iron ingot on each depot that is empty or holds what the press made, so the presses never run out of work. */
	static void feedDepots(ServerLevel level) {
		// The smoker's beef goes back when it is eaten up, not when it is half cooked, so it is never reset mid-cook
		if (smokerDepot != null && level.getBlockEntity(smokerDepot) instanceof com.simibubi.create.content.logistics.depot.DepotBlockEntity smoker) {
			var behaviour = smoker.getBehaviour(com.simibubi.create.content.logistics.depot.DepotBehaviour.TYPE);
			if (behaviour != null && behaviour.getHeldItemStack()
				.is(net.minecraft.world.item.Items.COOKED_BEEF) || behaviour != null && behaviour.getHeldItemStack()
				.isEmpty())
				behaviour.setHeldItem(new com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack(
					new ItemStack(net.minecraft.world.item.Items.BEEF)));
		}
		for (BlockPos pos : FACTORY_DEPOTS)
			if (level.getBlockEntity(pos) instanceof com.simibubi.create.content.logistics.depot.DepotBlockEntity depot) {
				var behaviour = depot.getBehaviour(com.simibubi.create.content.logistics.depot.DepotBehaviour.TYPE);
				if (behaviour != null && !behaviour.getHeldItemStack()
					.is(net.minecraft.world.item.Items.IRON_INGOT))
					behaviour.setHeldItem(new com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack(
						new ItemStack(net.minecraft.world.item.Items.IRON_INGOT)));
			}
	}

	/** Where the refining column's south-west corner stands, relative to its pad. */
	static final int REFINERY_X = 6;
	static final int REFINERY_Z = 3;
	/** The Reboiler that feeds the column, which {@link #fuelUp()} keeps supplied (null until it is built). */
	static BlockPos refineryFeed;
	static BlockPos refineryStockTank;
	static BlockPos refineryBarrel;
	static BlockPos refineryProductTank;

	/** The refinery's stock: plant oil in the tank its pump draws from (the barrel of coal is stocked once, when it is built). */
	static void refineryStock(ServerLevel level) {
		if (refineryStockTank == null)
			return;
		stockTank(level, refineryStockTank, new FluidStack(CEFluids.PLANT_OIL.get()
			.getSource(), 2000));
	}
	static final List<BlockPos> REFINERY_CONDENSERS = new ArrayList<>();

	/**
	 * A refining column: a heated base of Heat Exchangers, Reboilers, three layers of Column Sections, Condensers on top and a
	 * Flare Stack above. It is fed plant oil and coal, and its product is drawn off, from {@link #fuelUp()}.
	 */
	static void refinery(ServerLevel level, BlockPos at) {
		REFINERY_CONDENSERS.clear();
		for (int x = REFINERY_X; x < REFINERY_X + 3; x++)
			for (int z = REFINERY_Z; z < REFINERY_Z + 3; z++) {
				set(level, at, x, 0, z, CEBlocks.HEAT_EXCHANGER.getDefaultState()
					.setValue(com.simibubi.create.content.processing.burner.BlazeBurnerBlock.HEAT_LEVEL,
						com.simibubi.create.content.processing.burner.BlazeBurnerBlock.HeatLevel.KINDLED));
				set(level, at, x, 1, z, CEBlocks.REBOILER.getDefaultState());
				for (int y = 2; y <= 4; y++)
					set(level, at, x, y, z, CEBlocks.COLUMN_SECTION.getDefaultState());
				set(level, at, x, 5, z, CEBlocks.CONDENSER.getDefaultState());
				REFINERY_CONDENSERS.add(at.offset(x, 5, z));
			}
		set(level, at, REFINERY_X + 1, 6, REFINERY_Z + 1, CEBlocks.FLARE_STACK.getDefaultState());
		refineryFeed = at.offset(REFINERY_X, 1, REFINERY_Z);
		// Feed: a tank of plant oil, and a pump into the controller Reboiler's north face. Catalyst: a barrel of coal and an
		// pair of funnels into its west face. Product: a pump over the corner Condenser into a tank that stands on it.
		tank(level, at, REFINERY_X, 1, REFINERY_Z - 2, 2);
		refineryStockTank = at.offset(REFINERY_X, 1, REFINERY_Z - 2);
		pump(level, at, REFINERY_X, 1, REFINERY_Z - 1, Direction.SOUTH);
		drive(level, at, REFINERY_X - 1, 1, REFINERY_Z - 1, Axis.Z, Direction.NORTH, 64);
		refineryBarrel = feedItems(level, at, REFINERY_X - 1, 1, REFINERY_Z, Direction.WEST, Direction.SOUTH);
		stockBarrel(level, refineryBarrel, new ItemStack(net.minecraft.world.item.Items.COAL, 64));
		pump(level, at, REFINERY_X, 6, REFINERY_Z, Direction.UP);
		drive(level, at, REFINERY_X - 1, 6, REFINERY_Z, Axis.Y, Direction.UP, 64);
		tank(level, at, REFINERY_X, 7, REFINERY_Z, 2);
		refineryProductTank = at.offset(REFINERY_X, 7, REFINERY_Z);
		pump(level, at, REFINERY_X + 2, 6, REFINERY_Z, Direction.UP);
		drive(level, at, REFINERY_X + 3, 6, REFINERY_Z, Axis.Y, Direction.UP, 64);
		tank(level, at, REFINERY_X + 2, 7, REFINERY_Z, 2);
	}
}
