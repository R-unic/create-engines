package com.createengines.client;

import static com.createengines.client.ShowcaseRun.CY;
import static com.createengines.client.ShowcaseRun.CZ;
import static com.createengines.client.ShowcaseRun.axisX;
import static com.createengines.client.ShowcaseRun.pad;
import static com.createengines.client.ShowcaseRun.set;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import com.createengines.CreateEngines;
import com.createengines.content.airflow.ManifoldBlock;
import com.createengines.content.airflow.PortOpeningBlock;
import com.createengines.content.airflow.ThrottleBlockEntity;
import com.createengines.content.crankshaft.CrankshaftBlockEntity;
import com.createengines.content.tool.BankBuilderItem;
import com.createengines.content.turbo.TurbochargerBlockEntity;
import com.createengines.engine.EngineController;
import com.createengines.engine.sim.EngineSimulation;
import com.createengines.engine.sim.Turbocharger;
import com.createengines.registry.CEBlocks;
import com.createengines.registry.CEFluids;
import com.createengines.registry.CEItems;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.kinetics.base.DirectionalAxisKineticBlock;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlock;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/**
 * More of the trailer's pads and what happens on them (see {@link ShowcaseRun}, which holds the shot list): the engine that
 * runs hot until radiators are fitted, the variable turbocharger and MAF sensor, the engine whose exhaust is rebuilt between
 * shots to be heard, the Copper Gasket line, and the night pull. The engines, machines, particles and sounds are the mod's
 * and Create's own. What is staged: the harness holds the hot engine's block temperature up while its shot runs, as a showcase
 * engine running free does not make enough heat to overheat in a few seconds; and as the night pull begins it warms that
 * engine once and gives it the mod's own crank inertia back (see {@code stockCrank}). Everything else, the cooling and the rev
 * included, is the simulation's.
 */
final class ShowcaseRunFeatures {

	static final int HEAT_BAY = 19;
	static final int TUNING_BAY = 20;
	static final int SOUND_BAY = 21;
	static final int GASKET_BAY = 22;
	static final int NIGHT_BAY = 23;
	static final int HOOK_BAY = 24;

	/** The block temperature the hot engine is held at, in the simulation's units: 190 degrees, well past critical. */
	private static final double HOT_BLOCK = 1.7;
	/** The block temperature the night engine is warmed to as its shot begins: 95 degrees. */
	private static final double WARM_BLOCK = 0.75;
	private static final int HOT_THROWS = 6;
	private static final int SOUND_THROWS = 4;
	private static final int NIGHT_THROWS = 4;
	/** How many times the shipped crank inertia the trailer's config has (TRAILER_CONFIG in tools/trailer/record.py). */
	private static final double TRAILER_CRANK_WEIGHT = 5;
	/** The night engine's idle stop, in steps of five percent: low enough to rev up from, and still counted as on the throttle. */
	private static final int NIGHT_IDLE = 3;
	/** How far east the long exhaust runs before its opening, in pipes. */
	private static final int LONG_PIPES = 9;

	private static final int GASKET_FROM = 0;
	private static final int GASKET_TO = 10;

	private ShowcaseRunFeatures() {
	}

	/** What the sound pad's exhaust ends in. */
	private enum Exhaust {
		/** A short pipe and an opening. */
		STRAIGHT,
		/** The same with a Muffler in place of the last pipe. */
		MUFFLED,
		/** An opening straight on every head, with no pipe. */
		STUB,
		/** A long run of pipe to the opening. */
		LONG
	}

	private static final List<BlockPos> DEPLOYERS = new ArrayList<>();
	private static BlockPos gasketBelt;
	private static BlockPos gasketEnd;

	static void build(ServerLevel level, ServerPlayer player) {
		attempt("the hot engine", () -> ShowcaseRun.bank(level, player, pad(HEAT_BAY), BankBuilderItem.Layout.INLINE, HOT_THROWS,
			new ShowcaseRun.Kit(ShowcaseRun.Fuel.GASOLINE, 1, false, 0, false, false, false)));
		attempt("the tuning pad", () -> tuning(level, player, pad(TUNING_BAY)));
		attempt("the sound pad", () -> {
			ShowcaseRun.bank(level, player, pad(SOUND_BAY), BankBuilderItem.Layout.INLINE, SOUND_THROWS,
				ShowcaseRun.Kit.plain(ShowcaseRun.Fuel.GASOLINE));
			exhaust(level, Exhaust.STRAIGHT);
		});
		attempt("the gasket line", () -> gasketLine(level, pad(GASKET_BAY)));
		attempt("the night engine", () -> night(level, player, pad(NIGHT_BAY)));
		attempt("the cold start", () -> coldStart(level, player, pad(HOOK_BAY)));
	}

	/**
	 * A cold turbocharged inline four on a Heavy Starter, its stack open: nothing on it turns until the starter's cue. An
	 * inline, so its crank and rods are in the open under the cylinders and the camera sees them start.
	 */
	private static void coldStart(ServerLevel level, ServerPlayer player, BlockPos at) {
		ShowcaseRun.coldBank(level, player, at, BankBuilderItem.Layout.INLINE, NIGHT_THROWS,
			new ShowcaseRun.Kit(ShowcaseRun.Fuel.GASOLINE, 1, false, 0, false, false, false));
	}

	private static void hookStarter(ServerLevel level, boolean on) {
		level.setBlock(pad(HOOK_BAY).offset(1, CY - 1, CZ - 1),
			on ? Blocks.REDSTONE_BLOCK.defaultBlockState() : Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
	}

	/** A pad that cannot be built is logged and the others still stand, so one mistake costs one shot, not the run. */
	private static void attempt(String what, Runnable build) {
		try {
			build.run();
		} catch (RuntimeException e) {
			CreateEngines.LOGGER.error("Showcase: {} failed to build", what, e);
		}
	}

	/** A diesel four with a variable-geometry turbocharger, and a MAF sensor in the intake pipe on the turbo's way in. */
	private static void tuning(ServerLevel level, ServerPlayer player, BlockPos at) {
		ShowcaseRun.bank(level, player, at, BankBuilderItem.Layout.INLINE, 4,
			new ShowcaseRun.Kit(ShowcaseRun.Fuel.DIESEL, 1, true, 0, false, false, false));
		// The last pipe between the throttle off the compressor and the intake row (an inline's intake is on its north side)
		ShowcaseRun.pipe(level, at.offset(6, CY + 2, CZ - 1), (ManifoldBlock) CEBlocks.MAF_SENSOR.get());
	}

	/** Rebuilds the sound pad's exhaust, everything east of the last head. */
	private static void exhaust(ServerLevel level, Exhaust shape) {
		BlockPos at = pad(SOUND_BAY);
		int lastThrow = SOUND_THROWS + 1;
		int y = CY + 2;
		int z = CZ + 1;
		for (int x = 2; x <= lastThrow + LONG_PIPES + 2; x++)
			set(level, at, x, y, z, Blocks.AIR.defaultBlockState());
		if (shape == Exhaust.STUB) {
			for (int x = 2; x <= lastThrow; x++)
				set(level, at, x, y, z, CEBlocks.EXHAUST_OUTLET.getDefaultState()
					.setValue(PortOpeningBlock.FACING, Direction.SOUTH));
			return;
		}
		int pipes = switch (shape) {
			case STUB -> throw new IllegalStateException("built above");
			case STRAIGHT, MUFFLED -> 1;
			case LONG -> LONG_PIPES;
		};
		int outlet = lastThrow + pipes + 1;
		set(level, at, outlet, y, z, CEBlocks.EXHAUST_OUTLET.getDefaultState()
			.setValue(PortOpeningBlock.FACING, Direction.EAST));
		if (shape == Exhaust.MUFFLED)
			set(level, at, lastThrow + 1, y, z, axisX(CEBlocks.MUFFLER.getDefaultState()));
		// The row over the heads, and the run past them
		for (int x = 2; x < outlet; x++)
			if (shape != Exhaust.MUFFLED || x != lastThrow + 1)
				ShowcaseRun.pipe(level, at.offset(x, y, z), CEBlocks.EXHAUST_MANIFOLD.get());
		// Each joins up again to whatever is beside it now
		for (int x = 2; x < outlet; x++)
			if (level.getBlockState(at.offset(x, y, z))
				.is(CEBlocks.EXHAUST_MANIFOLD.get()))
				ShowcaseRun.pipe(level, at.offset(x, y, z), CEBlocks.EXHAUST_MANIFOLD.get());
	}

	/**
	 * The Copper Gasket's sequenced assembly on a belt: Rubber Gaskets go on at the west end, and a Deployer with copper
	 * sheets, a Press, a second Deployer and a second Press stand over it, turned by one shaft line from a motor.
	 */
	private static void gasketLine(ServerLevel level, BlockPos at) {
		DEPLOYERS.clear();
		ShowcaseRun.dais(level, at, GASKET_TO + 2);
		BlockState shaftZ = AllBlocks.SHAFT.getDefaultState()
			.setValue(BlockStateProperties.AXIS, Axis.Z);
		set(level, at, GASKET_FROM, 1, CZ, shaftZ);
		set(level, at, GASKET_TO, 1, CZ, shaftZ);
		motor(level, at.offset(GASKET_FROM, 1, CZ + 1), Direction.NORTH, 24);
		com.simibubi.create.content.kinetics.belt.item.BeltConnectorItem.createBelts(level, at.offset(GASKET_FROM, 1, CZ),
			at.offset(GASKET_TO, 1, CZ));
		set(level, at, GASKET_TO + 1, 1, CZ, AllBlocks.DEPOT.getDefaultState());
		gasketBelt = at.offset(GASKET_FROM + 1, 2, CZ);
		gasketEnd = at.offset(GASKET_TO + 1, 1, CZ);

		BlockState deployer = AllBlocks.DEPLOYER.getDefaultState()
			.setValue(com.simibubi.create.content.kinetics.base.DirectionalKineticBlock.FACING, Direction.DOWN)
			.setValue(DirectionalAxisKineticBlock.AXIS_ALONG_FIRST_COORDINATE, true);
		BlockState press = AllBlocks.MECHANICAL_PRESS.getDefaultState()
			.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST);
		motor(level, at.offset(1, 3, CZ), Direction.EAST, 128);
		for (int x = 2; x <= 8; x++) {
			boolean machine = x % 2 == 0;
			boolean deploys = x == 2 || x == 6;
			set(level, at, x, 3, CZ, !machine ? axisX(AllBlocks.SHAFT.getDefaultState()) : deploys ? deployer : press);
			if (deploys)
				DEPLOYERS.add(at.offset(x, 3, CZ));
		}
	}

	private static void motor(ServerLevel level, BlockPos pos, Direction facing, int rpm) {
		level.setBlock(pos, AllBlocks.CREATIVE_MOTOR.getDefaultState()
			.setValue(CreativeMotorBlock.FACING, facing), Block.UPDATE_ALL);
		if (level.getBlockEntity(pos) instanceof CreativeMotorBlockEntity motor)
			motor.generatedSpeed.setValue(rpm);
	}

	/**
	 * A warm, free-running twin-turbo gasoline V8 with anti-lag on and no flywheel, so that it revs. Its throttles sit at their
	 * idle stop until the shot opens them, and its lift-off is redstone on the camshaft, the mod's own fuel shut-off (see
	 * {@link #defineCues}). No load and no rich fuel: either holds it to a slow, uneven crawl.
	 */
	private static void night(ServerLevel level, ServerPlayer player, BlockPos at) {
		ShowcaseRun.bank(level, player, at, BankBuilderItem.Layout.VEE, NIGHT_THROWS,
			new ShowcaseRun.Kit(ShowcaseRun.Fuel.GASOLINE, 2, false, 0, false, false, false));
		set(level, at, NIGHT_THROWS + 5, CY, CZ, Blocks.AIR.defaultBlockState());
		for (BlockEntity part : nightParts(level))
			if (part instanceof TurbochargerBlockEntity turbo)
				turbo.antiLag.setValue(Turbocharger.MAX_ANTI_LAG);
		throttles(level, false);
	}

	private static List<BlockEntity> nightParts(ServerLevel level) {
		List<BlockEntity> parts = new ArrayList<>();
		BlockPos at = pad(NIGHT_BAY);
		for (BlockPos pos : BlockPos.betweenClosed(at.offset(0, 0, CZ - 5), at.offset(NIGHT_THROWS + 6, CY + 6, CZ + 5))) {
			BlockEntity part = level.getBlockEntity(pos);
			if (part != null)
				parts.add(part);
		}
		return parts;
	}

	/**
	 * Opens the night engine's throttles all the way, or puts them back to idle, by their idle stop: a redstone block beside a
	 * throttle on this engine would reach the camshaft too, and cut the fuel.
	 */
	private static void throttles(ServerLevel level, boolean open) {
		for (BlockEntity part : nightParts(level))
			if (part instanceof ThrottleBlockEntity valve) {
				valve.wide.setValue(ThrottleBlockEntity.maxWide());
				valve.idle.setValue(open ? ThrottleBlockEntity.maxIdle() : NIGHT_IDLE);
			}
	}

	/** The gasket line's stock: copper sheets in each Deployer's hand, a Rubber Gasket onto the belt, the finished ones taken. */
	static void stock(ServerLevel level) {
		if (gasketBelt == null)
			return;
		for (BlockPos pos : DEPLOYERS) {
			var hand = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, Direction.UP);
			if (hand == null)
				continue;
			ItemStack rest = new ItemStack(AllItems.COPPER_SHEET.get(), 16);
			for (int slot = 0; slot < hand.getSlots() && !rest.isEmpty(); slot++)
				rest = hand.insertItem(slot, rest, false);
		}
		if (level.getBlockEntity(gasketEnd) instanceof com.simibubi.create.content.logistics.depot.DepotBlockEntity depot) {
			var held = depot.getBehaviour(com.simibubi.create.content.logistics.depot.DepotBehaviour.TYPE);
			if (held != null && !held.getHeldItemStack()
				.isEmpty())
				held.removeHeldItem();
		}
		ItemEntity gasket = new ItemEntity(level, gasketBelt.getX() + 0.5, gasketBelt.getY() + 0.1, gasketBelt.getZ() + 0.5,
			new ItemStack(CEItems.RUBBER_GASKET.get()), 0, 0, 0);
		gasket.setPickUpDelay(32767);
		level.addFreshEntity(gasket);
	}

	static void defineCues(java.util.function.BiConsumer<String, Cued> cue) {
		// The hot engine: its block is held past critical while the first shot runs, so it steams all the way through
		for (int quarter = 0; quarter < 24; quarter++)
			cue.accept("running_hot", new Cued(quarter * 0.25, level -> heat(level, HEAT_BAY, HOT_BLOCK)));
		// ... and still is when the next begins; then two full radiators go on, and the simulation cools it by itself
		for (int quarter = 0; quarter < 8; quarter++)
			cue.accept("running_cooled", new Cued(quarter * 0.25, level -> heat(level, HEAT_BAY, HOT_BLOCK)));
		cue.accept("running_cooled", new Cued(2.0, level -> radiator(level, 2)));
		cue.accept("running_cooled", new Cued(2.5, level -> radiator(level, 4)));

		cue.accept("muffler_off", new Cued(0, level -> exhaust(level, Exhaust.STRAIGHT)));
		cue.accept("muffler_on", new Cued(0, level -> exhaust(level, Exhaust.MUFFLED)));
		cue.accept("exhaust_stub", new Cued(0, level -> exhaust(level, Exhaust.STUB)));
		cue.accept("exhaust_long", new Cued(0, level -> exhaust(level, Exhaust.LONG)));
		// What the note is being made of, so a take whose two runs sound alike can be told from the log
		for (String shot : List.of("exhaust_stub", "exhaust_long"))
			cue.accept(shot, new Cued(3, level -> {
				if (level.getBlockEntity(pad(SOUND_BAY).offset(1, CY, CZ)) instanceof CrankshaftBlockEntity crank
					&& crank.summary() != null)
					CreateEngines.LOGGER.info("Showcase {}: pipe lengths {}", shot, java.util.Arrays.toString(crank.summary()
						.sound()
						.pipes()));
			}));

		// The vertical cut's opening: the starter turns a cold V8 over until it catches
		cue.accept("cold_start", new Cued(0.2, level -> hookStarter(level, true)));
		cue.accept("cold_start", new Cued(1.8, level -> hookStarter(level, false)));

		// The night pull: warm and idling, the throttles open and it revs, then two lifts off, each long enough for the valve to
		// open and the anti-lag to bang, and back on
		cue.accept("night_pull", new Cued(0, level -> {
			heat(level, NIGHT_BAY, WARM_BLOCK);
			stockCrank(level);
		}));
		cue.accept("night_pull", new Cued(1.5, level -> throttles(level, true)));
		cue.accept("night_pull", new Cued(7.0, level -> lift(level, true)));
		cue.accept("night_pull", new Cued(8.25, level -> lift(level, false)));
		cue.accept("night_pull", new Cued(9.5, level -> lift(level, true)));
		cue.accept("night_pull", new Cued(10.5, level -> lift(level, false)));
	}

	/** Something done to the world {@code at} seconds into a shot. */
	record Cued(double at, java.util.function.Consumer<ServerLevel> action) {
	}

	private static void heat(ServerLevel level, int bay, double temperature) {
		EngineSimulation sim = simulation(level, bay);
		if (sim != null)
			sim.thermal()
				.setTemperature(temperature);
	}

	/**
	 * Gives the night engine the weight of crank the mod ships with. tools/trailer/record.py makes every showcase crank
	 * {@link #TRAILER_CRANK_WEIGHT} times as heavy, which steadies the slow engines of the other shots and would leave this one
	 * too sluggish to be seen revving. A cylinder's own inertia is not part of that, so it is left as it is.
	 */
	private static void stockCrank(ServerLevel level) {
		EngineSimulation sim = simulation(level, NIGHT_BAY);
		if (sim == null)
			return;
		double parts = sim.sources()
			.stream()
			.mapToDouble(com.createengines.engine.sim.TorqueSource::inertia)
			.sum();
		double before = sim.inertia();
		field(EngineSimulation.class, "inertia", sim, parts + (before - parts) / TRAILER_CRANK_WEIGHT);
		CreateEngines.LOGGER.info("Showcase night engine: inertia {} to {}", before, sim.inertia());
	}

	/** The engine on a pad's first journal, or null. */
	private static EngineSimulation simulation(ServerLevel level, int bay) {
		if (!(level.getBlockEntity(pad(bay).offset(1, CY, CZ)) instanceof CrankshaftBlockEntity crank)
			|| crank.controller() == null)
			return null;
		try {
			// The engine has no way in for these from outside, and the mod should not grow one for a film
			Field sim = EngineController.class.getDeclaredField("sim");
			sim.setAccessible(true);
			return (EngineSimulation) sim.get(crank.controller());
		} catch (ReflectiveOperationException e) {
			CreateEngines.LOGGER.error("Showcase: could not reach the engine on bay {}", bay, e);
			return null;
		}
	}

	private static void field(Class<?> owner, String name, Object target, double value) {
		try {
			Field field = owner.getDeclaredField(name);
			field.setAccessible(true);
			field.setDouble(target, value);
		} catch (ReflectiveOperationException e) {
			CreateEngines.LOGGER.error("Showcase: could not set {}", name, e);
		}
	}

	private static void radiator(ServerLevel level, int x) {
		BlockPos pos = pad(HEAT_BAY).offset(x, CY, CZ - 1);
		level.setBlock(pos, CEBlocks.RADIATOR.getDefaultState(), Block.UPDATE_ALL);
		if (level.getBlockEntity(pos) instanceof com.createengines.content.radiator.RadiatorBlockEntity loop)
			loop.tank()
				.fill(new FluidStack(CEFluids.COOLANT.get()
					.getSource(), 1000), IFluidHandler.FluidAction.EXECUTE);
		level.playSound(null, pos, net.minecraft.sounds.SoundEvents.NETHERITE_BLOCK_PLACE,
			net.minecraft.sounds.SoundSource.BLOCKS, 0.8f, 1.0f);
	}

	/** Redstone on the camshaft line shuts its rack; taking it away gives the fuel back. */
	private static void lift(ServerLevel level, boolean off) {
		BlockPos beside = BankBuilderItem.camOffsets(BankBuilderItem.Layout.VEE, Axis.X, Direction.UP)
			.get(0);
		BlockPos pos = pad(NIGHT_BAY).offset(NIGHT_THROWS + 2, CY + beside.getY() - 1, CZ + beside.getZ());
		level.setBlock(pos, off ? Blocks.REDSTONE_BLOCK.defaultBlockState() : Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
		if (level.getBlockEntity(pad(NIGHT_BAY).offset(1, CY, CZ)) instanceof CrankshaftBlockEntity crank)
			CreateEngines.LOGGER.info("Showcase night engine, {}: {}", off ? "lifting off" : "back on", crank.summary());
	}
}
