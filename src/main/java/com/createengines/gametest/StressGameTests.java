package com.createengines.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;


import com.createengines.CreateEngines;
import com.createengines.content.camshaft.CamshaftBlockEntity;
import com.createengines.content.cylinder.CylinderBlock;
import com.createengines.content.head.CylinderHeadBlock;
import com.createengines.content.head.CylinderHeadBlockEntity;
import com.createengines.content.airflow.PortOpeningBlock;
import com.createengines.content.crankshaft.CrankshaftBlockEntity;
import com.createengines.content.starter.StarterBlock;
import com.createengines.engine.CrankGeometry;
import com.createengines.engine.EngineState;
import com.createengines.engine.EngineSummary;
import com.createengines.registry.CEBlocks;
import com.createengines.registry.CEFluids;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlock;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.ShortTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Stress tests for every functional block: every block state against the whole block API, every block entity against
 * damaged and extreme saves and every setting at every value, fluid tanks against hostile callers, engines torn apart
 * and rebuilt at random while they run, and many large engines running at once. Run with
 * {@code ./gradlew runGameTestServer}.
 */
@GameTestHolder(CreateEngines.MOD_ID)
@PrefixGameTestTemplate(false)
public class StressGameTests {

	/** A 48 x 20 x 48 empty room, big enough for sixteen engines side by side. */
	private static final String LARGE = "empty_large";

	private static List<Block> engineBlocks() {
		List<Block> blocks = new ArrayList<>();
		for (Block block : BuiltInRegistries.BLOCK)
			if (BuiltInRegistries.BLOCK.getKey(block)
				.getNamespace()
				.equals(CreateEngines.MOD_ID))
				blocks.add(block);
		return blocks;
	}

	/** Runs {@code action}, adding a line to {@code problems} if it throws. */
	private static void guard(List<String> problems, String what, Runnable action) {
		try {
			action.run();
		} catch (Throwable t) {
			StackTraceElement[] trace = t.getStackTrace();
			String where = trace.length > 0 ? " at " + trace[0] : "";
			problems.add(what + ": " + t + where);
		}
	}

	private static void report(GameTestHelper helper, List<String> problems) {
		if (!problems.isEmpty()) {
			String text = String.join("\n  ", problems.subList(0, Math.min(40, problems.size())));
			CreateEngines.LOGGER.error("Stress test found {} problems:\n  {}", problems.size(), text);
			helper.fail(problems.size() + " problems, first: " + problems.getFirst());
		}
	}

	// ---------------------------------------------------------------------------------------------------------------
	// Every block state
	// ---------------------------------------------------------------------------------------------------------------

	/**
	 * Places every state of every block (with a block entity where it has one) and asks it everything the game asks a
	 * block: shapes, rotation and mirroring (four turns must come back round), shape updates from every side and every
	 * kind of neighbour, comparator output, loot, pick-block and removal.
	 */
	@GameTest(template = LARGE, timeoutTicks = 400)
	public static void everyBlockStateAnswersTheWholeBlockApi(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos pos = helper.absolutePos(new BlockPos(24, 6, 24));
		List<String> problems = new ArrayList<>();
		int states = 0;
		for (Block block : engineBlocks()) {
			for (BlockState state : block.getStateDefinition()
				.getPossibleStates()) {
				states++;
				String id = BuiltInRegistries.BLOCK.getKey(block) + " " + state;
				guard(problems, id, () -> {
					level.setBlock(pos, state, Block.UPDATE_ALL);
					BlockState placed = level.getBlockState(pos);
					if (placed != state && !placed.isAir())
						problems.add(id + ": placed as " + placed);
					if (state.hasBlockEntity() && level.getBlockEntity(pos) == null)
						problems.add(id + ": has a block entity but none was created");
					state.getShape(level, pos);
					state.getCollisionShape(level, pos);
					state.getVisualShape(level, pos, CollisionContext.empty());
					state.getBlockSupportShape(level, pos);
					state.getLightEmission();
					state.getDestroySpeed(level, pos);
					state.canSurvive(level, pos);
					state.getRenderShape();
					if (state.hasAnalogOutputSignal()) {
						int signal = state.getAnalogOutputSignal(level, pos);
						if (signal < 0 || signal > 15)
							problems.add(id + ": comparator output " + signal);
					}
					BlockState turned = state;
					for (int i = 0; i < 4; i++)
						turned = turned.rotate(Rotation.CLOCKWISE_90);
					if (turned != state)
						problems.add(id + ": four quarter turns give " + turned);
					if (state.rotate(Rotation.CLOCKWISE_180) != state.rotate(Rotation.CLOCKWISE_90)
						.rotate(Rotation.CLOCKWISE_90))
						problems.add(id + ": 180 is not two quarter turns");
					if (state.rotate(Rotation.COUNTERCLOCKWISE_90)
						.rotate(Rotation.CLOCKWISE_90) != state)
						problems.add(id + ": a turn back does not undo a turn");
					for (Mirror mirror : Mirror.values())
						if (state.mirror(mirror)
							.mirror(mirror) != state)
							problems.add(id + ": mirror " + mirror + " twice is " + state.mirror(mirror)
								.mirror(mirror));
					for (Direction direction : Direction.values())
						for (BlockState neighbour : new BlockState[] {Blocks.AIR.defaultBlockState(),
							Blocks.STONE.defaultBlockState(), Blocks.WATER.defaultBlockState(),
							CEBlocks.CYLINDER_HEAD.getDefaultState(), CEBlocks.CRANKSHAFT.getDefaultState(),
							AllBlocks.SHAFT.getDefaultState()})
							state.updateShape(direction, neighbour, level, pos, pos.relative(direction));
					state.handleNeighborChanged(level, pos, Blocks.REDSTONE_BLOCK, pos.above(), false);
					state.handleNeighborChanged(level, pos, Blocks.AIR, pos.below(), true);
					block.getCloneItemStack(level, pos, state);
					Block.getDrops(state, level, pos, level.getBlockEntity(pos));
					if (state.isRandomlyTicking())
						state.randomTick(level, pos, RandomSource.create(1));
					level.removeBlock(pos, false);
				});
				level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
			}
		}
		CreateEngines.LOGGER.info("Stress test walked {} block states of {} blocks", states, engineBlocks().size());
		report(helper, problems);
		helper.succeed();
	}

	// ---------------------------------------------------------------------------------------------------------------
	// Saves and settings
	// ---------------------------------------------------------------------------------------------------------------

	private static Tag mutate(Tag tag, Random random, int mode) {
		if (tag instanceof CompoundTag compound) {
			CompoundTag out = new CompoundTag();
			for (String key : compound.getAllKeys()) {
				if (mode == 3 && random.nextInt(3) == 0)
					continue; // a field missing, as in a save from an older version
				out.put(key, mutate(compound.get(key), random, mode));
			}
			return out;
		}
		if (tag instanceof ListTag list) {
			if (mode == 3 && random.nextInt(3) == 0)
				return new ListTag();
			ListTag out = new ListTag();
			for (Tag element : list)
				out.add(mutate(element, random, mode));
			return out;
		}
		int pick = random.nextInt(4);
		return switch (mode) {
			case 0 -> {
				if (tag instanceof IntTag)
					yield IntTag.valueOf(new int[] {Integer.MIN_VALUE, Integer.MAX_VALUE, -1, 0, 1 << 20}[pick]);
				if (tag instanceof LongTag)
					yield LongTag.valueOf(new long[] {Long.MIN_VALUE, Long.MAX_VALUE, -1, 0}[pick]);
				if (tag instanceof ShortTag)
					yield ShortTag.valueOf(new short[] {Short.MIN_VALUE, Short.MAX_VALUE, -1, 0}[pick]);
				if (tag instanceof ByteTag)
					yield ByteTag.valueOf(new byte[] {Byte.MIN_VALUE, Byte.MAX_VALUE, -1, 0}[pick]);
				if (tag instanceof DoubleTag)
					yield DoubleTag.valueOf(new double[] {1e300, -1e300, 0, 1e-300}[pick]);
				if (tag instanceof FloatTag)
					yield FloatTag.valueOf(new float[] {Float.MAX_VALUE, -Float.MAX_VALUE, 0, Float.MIN_VALUE}[pick]);
				yield tag;
			}
			case 1 -> {
				if (tag instanceof DoubleTag)
					yield DoubleTag.valueOf(new double[] {Double.NaN, Double.POSITIVE_INFINITY,
						Double.NEGATIVE_INFINITY, -0.0}[pick]);
				if (tag instanceof FloatTag)
					yield FloatTag.valueOf(new float[] {Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY,
						-0f}[pick]);
				yield tag;
			}
			case 2 -> {
				if (tag instanceof IntTag i)
					yield IntTag.valueOf(-Math.abs(i.getAsInt()) - 1);
				if (tag instanceof DoubleTag d)
					yield DoubleTag.valueOf(-Math.abs(d.getAsDouble()) - 1);
				if (tag instanceof FloatTag f)
					yield FloatTag.valueOf(-Math.abs(f.getAsFloat()) - 1);
				if (tag instanceof LongTag l)
					yield LongTag.valueOf(-Math.abs(l.getAsLong()) - 1);
				yield tag;
			}
			case 4 -> {
				// Wrong types, as from a corrupted or hand-edited save
				if (tag instanceof StringTag)
					yield IntTag.valueOf(7);
				if (tag instanceof IntTag || tag instanceof DoubleTag || tag instanceof LongTag)
					yield StringTag.valueOf("not a number");
				yield tag;
			}
			default -> tag;
		};
	}

	private static CompoundTag withoutForce(CompoundTag tag) {
		CompoundTag copy = tag.copy();
		stripForce(copy);
		return copy;
	}

	private static void stripForce(Tag tag) {
		if (tag instanceof CompoundTag compound) {
			compound.remove("Force");
			for (String key : compound.getAllKeys())
				stripForce(compound.get(key));
		} else if (tag instanceof ListTag list)
			for (Tag element : list)
				stripForce(element);
	}

	/** One of every block with a block entity, at {@code pos} each with its own state. */
	private static List<BlockState> blockEntityStates() {
		List<BlockState> states = new ArrayList<>();
		for (Block block : engineBlocks())
			if (block instanceof EntityBlock)
				states.add(block.defaultBlockState());
		return states;
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static void tickBlockEntity(Level level, BlockPos pos, int times) {
		BlockEntity be = level.getBlockEntity(pos);
		if (be == null)
			return;
		BlockState state = level.getBlockState(pos);
		if (!(state.getBlock() instanceof EntityBlock entityBlock))
			return;
		BlockEntityTicker ticker = entityBlock.getTicker(level, state, be.getType());
		for (int i = 0; i < times; i++)
			if (ticker != null)
				ticker.tick(level, pos, state, be);
	}

	/**
	 * Loads every block entity from an empty save, from its own save, from saves with extreme numbers, with NaN and
	 * infinities, with negative numbers, with fields missing and with the wrong types, and ticks it on what it loaded.
	 * An honest save must come back byte for byte.
	 */
	@GameTest(template = LARGE, timeoutTicks = 400)
	public static void blockEntitiesSurviveDamagedAndExtremeSaves(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		HolderLookup.Provider access = level.registryAccess();
		List<String> problems = new ArrayList<>();
		int x = 4;
		List<BlockPos> placed = new ArrayList<>();
		for (BlockState state : blockEntityStates()) {
			BlockPos pos = helper.absolutePos(new BlockPos(x, 6, 24));
			x += 2;
			placed.add(pos);
			String id = BuiltInRegistries.BLOCK.getKey(state.getBlock())
				.toString();
			level.setBlock(pos, state, Block.UPDATE_ALL);
			BlockEntity be = level.getBlockEntity(pos);
			if (be == null) {
				problems.add(id + ": no block entity");
				continue;
			}
			guard(problems, id + " fresh tick", () -> tickBlockEntity(level, pos, 30));
			CompoundTag honest = be.saveWithFullMetadata(access);
			guard(problems, id + " round trip", () -> {
				be.loadWithComponents(honest.copy(), access);
				CompoundTag again = be.saveWithFullMetadata(access);
				// Create animated tank levels carry a one-shot "Force" flag that only the first save has
				if (!withoutForce(again).equals(withoutForce(honest)))
					problems.add(id + ": a saved block entity does not save the same after loading\n    before " + honest
						+ "\n    after  " + again);
			});
			guard(problems, id + " update tag", () -> {
				CompoundTag update = be.getUpdateTag(access);
				be.handleUpdateTag(update, access);
			});
			guard(problems, id + " empty load", () -> {
				be.loadWithComponents(new CompoundTag(), access);
				tickBlockEntity(level, pos, 10);
			});
			for (int mode = 0; mode <= 4; mode++)
				for (int seed = 0; seed < 8; seed++) {
					int m = mode, s = seed;
					guard(problems, id + " load mode " + m + " seed " + s, () -> {
						Random random = new Random(s * 977L + m);
						CompoundTag broken = (CompoundTag) mutate(honest, random, m);
						be.loadWithComponents(broken, access);
						be.getUpdateTag(access);
						be.saveWithFullMetadata(access);
						tickBlockEntity(level, pos, 5);
					});
				}
		}
		// Whatever state it ended up in, taking it apart must not throw either
		for (BlockPos pos : placed)
			guard(problems, "removing " + level.getBlockState(pos), () -> level.removeBlock(pos, false));
		report(helper, problems);
		helper.succeed();
	}

	/** Every setting of every block set to every value an input could carry, and each block ticked on it. */
	@GameTest(template = LARGE, timeoutTicks = 400)
	public static void everySettingTakesEveryValue(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		List<String> problems = new ArrayList<>();
		int x = 4;
		List<BlockPos> placed = new ArrayList<>();
		int settings = 0;
		for (BlockState state : blockEntityStates()) {
			BlockPos pos = helper.absolutePos(new BlockPos(x, 6, 24));
			x += 2;
			placed.add(pos);
			level.setBlock(pos, state, Block.UPDATE_ALL);
			if (!(level.getBlockEntity(pos) instanceof SmartBlockEntity be))
				continue;
			String id = BuiltInRegistries.BLOCK.getKey(state.getBlock())
				.toString();
			for (BlockEntityBehaviour behaviour : be.getAllBehaviours()) {
				if (!(behaviour instanceof ScrollValueBehaviour value))
					continue;
				settings++;
				value.setValue(Integer.MIN_VALUE);
				int low = value.getValue();
				value.setValue(Integer.MAX_VALUE);
				int high = value.getValue();
				if (low == Integer.MIN_VALUE || high == Integer.MAX_VALUE)
					problems.add(id + " setting " + value.getType() + " is not clamped: " + low + ".." + high);
				int[] tries = {Integer.MIN_VALUE, -1000, -1, 0, 1, low, high, low + (high - low) / 2, high + 1, 100000,
					Integer.MAX_VALUE};
				for (int attempt : tries)
					guard(problems, id + " setting " + value.getType() + " to " + attempt, () -> {
						value.setValue(attempt);
						int stored = value.getValue();
						if (stored < low || stored > high)
							problems.add(id + " setting " + value.getType() + ": " + attempt + " was stored as " + stored
								+ ", outside " + low + ".." + high);
						tickBlockEntity(level, pos, 3);
						be.saveWithFullMetadata(level.registryAccess());
					});
			}
		}
		CreateEngines.LOGGER.info("Stress test set {} settings to extreme values", settings);
		for (BlockPos pos : placed)
			level.removeBlock(pos, false);
		report(helper, problems);
		helper.succeed();
	}

	// ---------------------------------------------------------------------------------------------------------------
	// Fluid tanks
	// ---------------------------------------------------------------------------------------------------------------

	private static int total(IFluidHandler handler) {
		int sum = 0;
		for (int tank = 0; tank < handler.getTanks(); tank++)
			sum += handler.getFluidInTank(tank)
				.getAmount();
		return sum;
	}

	private static long capacity(IFluidHandler handler) {
		long sum = 0;
		for (int tank = 0; tank < handler.getTanks(); tank++)
			sum += handler.getTankCapacity(tank);
		return sum;
	}

	/**
	 * Fills and drains every tank-bearing block through its fluid capability, from every side, with every fluid there is
	 * and amounts from negative to Integer.MAX_VALUE, and checks the books: what a simulated fill reports is what the real
	 * one does, a fill never takes more than offered, nothing is ever more than full or below empty, and what comes out is
	 * what left.
	 */
	@GameTest(template = LARGE, timeoutTicks = 600)
	public static void fluidTanksKeepTheirBooksUnderHostileCallers(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		List<String> problems = new ArrayList<>();
		List<Fluid> fluids = new ArrayList<>();
		for (Fluid fluid : BuiltInRegistries.FLUID)
			if (fluid.isSource(fluid.defaultFluidState()) && fluid != Fluids.EMPTY)
				fluids.add(fluid);
		int x = 4;
		List<BlockPos> placed = new ArrayList<>();
		Random random = new Random(42);
		int operations = 0;
		for (Block block : engineBlocks()) {
			BlockPos pos = helper.absolutePos(new BlockPos(x, 6, 24));
			level.setBlock(pos, block.defaultBlockState(), Block.UPDATE_ALL);
			boolean any = false;
			for (Direction side : new Direction[] {null, Direction.UP, Direction.DOWN, Direction.NORTH, Direction.SOUTH,
				Direction.EAST, Direction.WEST}) {
				IFluidHandler handler = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, side);
				if (handler == null)
					continue;
				any = true;
				String id = BuiltInRegistries.BLOCK.getKey(block) + " from " + side;
				for (int i = 0; i < 400; i++) {
					operations++;
					Fluid fluid = fluids.get(random.nextInt(fluids.size()));
					int amount = switch (random.nextInt(8)) {
						case 0 -> Integer.MAX_VALUE;
						case 1 -> -random.nextInt(1000) - 1;
						case 2 -> 0;
						case 3 -> 1;
						default -> random.nextInt(600);
					};
					int index = i;
					guard(problems, id + " op " + index, () -> {
						int before = total(handler);
						FluidStack offer = new FluidStack(fluid, Math.max(0, amount));
						if (random.nextInt(10) == 0)
							offer = FluidStack.EMPTY;
						if (random.nextBoolean()) {
							int simulated = handler.fill(offer, FluidAction.SIMULATE);
							if (total(handler) != before)
								problems.add(id + ": a simulated fill changed the contents");
							int real = handler.fill(offer, FluidAction.EXECUTE);
							if (simulated != real)
								problems.add(id + ": simulated fill said " + simulated + ", real fill took " + real);
							if (real < 0 || real > offer.getAmount())
								problems.add(id + ": fill of " + offer.getAmount() + " reported " + real);
							if (total(handler) != before + real)
								problems.add(id + ": filled " + real + " but contents went " + before + " -> "
									+ total(handler));
						} else {
							int want = amount < 0 ? -amount : amount;
							FluidStack simulated = handler.drain(new FluidStack(fluid, want), FluidAction.SIMULATE);
							if (total(handler) != before)
								problems.add(id + ": a simulated drain changed the contents");
							FluidStack real = handler.drain(new FluidStack(fluid, want), FluidAction.EXECUTE);
							if (real.getAmount() > want)
								problems.add(id + ": drained " + real.getAmount() + " of " + want);
							if (!real.isEmpty() && real.getFluid() != fluid)
								problems.add(id + ": asked for " + fluid + " got " + real.getFluid());
							if (simulated.getAmount() != real.getAmount())
								problems.add(id + ": simulated drain " + simulated.getAmount() + " real "
									+ real.getAmount());
							if (total(handler) != before - real.getAmount())
								problems.add(id + ": drained " + real.getAmount() + " but contents went " + before
									+ " -> " + total(handler));
							handler.drain(random.nextInt(300), FluidAction.EXECUTE);
						}
						int now = total(handler);
						if (now < 0 || now > capacity(handler))
							problems.add(id + ": contents " + now + " outside 0.." + capacity(handler));
						for (int tank = 0; tank < handler.getTanks(); tank++) {
							FluidStack inside = handler.getFluidInTank(tank);
							if (inside.getAmount() > handler.getTankCapacity(tank))
								problems.add(id + ": tank " + tank + " holds " + inside.getAmount() + " of "
									+ handler.getTankCapacity(tank));
							if (!inside.isEmpty() && !handler.isFluidValid(tank, inside))
								problems.add(id + ": tank " + tank + " holds " + inside.getFluid()
									+ " which it says it does not accept");
						}
						if (index % 25 == 0)
							tickBlockEntity(level, pos, 4);
					});
				}
			}
			if (any) {
				x += 2;
				placed.add(pos);
			} else
				level.removeBlock(pos, false);
		}
		CreateEngines.LOGGER.info("Stress test ran {} fluid operations on {} tank blocks", operations, placed.size());
		for (BlockPos pos : placed)
			level.removeBlock(pos, false);
		report(helper, problems);
		helper.succeed();
	}

	// ---------------------------------------------------------------------------------------------------------------
	// Engines under churn, in numbers and at size
	// ---------------------------------------------------------------------------------------------------------------

	private static BlockState axis(BlockState state, Axis axis) {
		return state.setValue(BlockStateProperties.AXIS, axis);
	}

	/** An engine with {@code count} cylinders in a line, {@code 3} blocks apart, all on one cam and one starter. */
	private static void buildLine(Level level, BlockPos origin, int count, boolean fuelled) {
		int pitch = 3;
		int lastThrow = 2 + pitch * (count - 1);
		int crankEnd = lastThrow + 1;
		java.util.function.BiConsumer<BlockPos, BlockState> set =
			(pos, state) -> level.setBlock(origin.offset(pos), state, Block.UPDATE_ALL);
		for (int x = 1; x <= crankEnd; x++)
			set.accept(new BlockPos(x, 2, 2), axis(CEBlocks.CRANKSHAFT.getDefaultState(), Axis.X));
		set.accept(new BlockPos(crankEnd + 1, 2, 2), axis(AllBlocks.SHAFT.getDefaultState(), Axis.X));
		set.accept(new BlockPos(crankEnd + 2, 2, 2), axis(AllBlocks.COGWHEEL.getDefaultState(), Axis.X));
		for (int x = 3; x <= crankEnd + 2; x++)
			set.accept(new BlockPos(x, 4, 3), axis(CEBlocks.CAMSHAFT.getDefaultState(), Axis.X));
		set.accept(new BlockPos(crankEnd + 2, 3, 3), axis(AllBlocks.LARGE_COGWHEEL.getDefaultState(), Axis.X));
		set.accept(new BlockPos(crankEnd + 3, 3, 3), axis(AllBlocks.ENCASED_CHAIN_DRIVE.getDefaultState(), Axis.X));
		set.accept(new BlockPos(crankEnd + 3, 4, 3), axis(AllBlocks.ENCASED_CHAIN_DRIVE.getDefaultState(), Axis.X));

		double tdc = CrankGeometry.topDeadCentreAngle(Axis.X, 0, Direction.UP);
		int lobe = (int) Math.round((((tdc / 2) % 180 + 180) % 180) / CamshaftBlockEntity.LOBE_ANGLE_STEP);
		for (int k = 0; k < count; k++) {
			int x = 2 + pitch * k;
			set.accept(new BlockPos(x, 2, 2), axis(CEBlocks.CRANK_THROW.getDefaultState(), Axis.X));
			set.accept(new BlockPos(x, 3, 2), CEBlocks.CYLINDER.getDefaultState()
				.setValue(CylinderBlock.FACING, Direction.UP)
				.setValue(CylinderBlock.HAS_PISTON, true)
				.setValue(CylinderBlock.HAS_ROD, true));
			set.accept(new BlockPos(x, 4, 2), CEBlocks.CYLINDER_HEAD.getDefaultState()
				.setValue(CylinderHeadBlock.FACING, Direction.UP)
				.setValue(CylinderHeadBlock.HAS_INJECTOR, true));
			set.accept(new BlockPos(x, 4, 1), CEBlocks.AIR_INTAKE.getDefaultState()
				.setValue(PortOpeningBlock.FACING, Direction.NORTH));
			set.accept(new BlockPos(x - 1, 4, 2), CEBlocks.EXHAUST_OUTLET.getDefaultState()
				.setValue(PortOpeningBlock.FACING, Direction.WEST));
			set.accept(new BlockPos(x, 4, 3), axis(CEBlocks.CAM_LOBE.getDefaultState(), Axis.X));
			((CamshaftBlockEntity) level.getBlockEntity(origin.offset(new BlockPos(x, 4, 3)))).lobeAngle.setValue(lobe);
		}
		set.accept(new BlockPos(1, 1, 2), CEBlocks.STARTER.getDefaultState()
			.setValue(StarterBlock.FACING, Direction.UP));
		set.accept(new BlockPos(1, 0, 2), AllBlocks.CREATIVE_MOTOR.getDefaultState()
			.setValue(CreativeMotorBlock.FACING, Direction.UP));
		set.accept(new BlockPos(0, 1, 2), Blocks.REDSTONE_BLOCK.defaultBlockState());
		((CreativeMotorBlockEntity) level.getBlockEntity(origin.offset(new BlockPos(1, 0, 2)))).generatedSpeed
			.setValue(TestEngine.MOTOR_RPM);
		if (fuelled)
			refill(level, origin.offset(new BlockPos(2, 4, 2)));
	}

	private static void refill(Level level, BlockPos head) {
		if (!(level.getBlockEntity(head) instanceof CylinderHeadBlockEntity be))
			return;
		be.tank()
			.fill(new FluidStack(CEFluids.DIESEL.get()
				.getSource(), CylinderHeadBlockEntity.CAPACITY), FluidAction.EXECUTE);
		be.oilTank()
			.fill(new FluidStack(CEFluids.ENGINE_OIL.get()
				.getSource(), CylinderHeadBlockEntity.CAPACITY), FluidAction.EXECUTE);
	}

	private static void runLongEngine(GameTestHelper helper, int cylinders) {
		BlockPos origin = helper.absolutePos(new BlockPos(2, 2, 20));
		Level level = helper.getLevel();
		long built = System.nanoTime();
		buildLine(level, origin, cylinders, true);
		BlockPos crank = new BlockPos(1, 2, 2);
		BlockPos head = new BlockPos(2, 4, 2);
		for (int tick = 10; tick <= 1100; tick += 10)
			helper.runAtTickTime(tick, () -> refill(level, origin.offset(head)));
		helper.runAtTickTime(500, () -> {
			CrankshaftBlockEntity be = (CrankshaftBlockEntity) level.getBlockEntity(origin.offset(crank));
			EngineSummary summary = be.summary();
			helper.assertTrue(summary != null, "The long crank built no engine");
			helper.assertTrue(summary.cylinders() == cylinders, "Expected " + cylinders + " cylinders, " + summary);
			helper.assertTrue(summary.firing() == cylinders, "Every cylinder should fire under the starter: " + summary);
			level.setBlock(origin.offset(new BlockPos(0, 1, 2)), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
		});
		helper.runAtTickTime(1000, () -> {
			CrankshaftBlockEntity be = (CrankshaftBlockEntity) level.getBlockEntity(origin.offset(crank));
			EngineSummary summary = be.summary();
			helper.assertTrue(summary != null && summary.state() == EngineState.RUNNING,
				"The " + cylinders + "-cylinder engine should run on its own: " + summary);
			double[] cost = cost(level, origin.offset(crank), 400);
			CreateEngines.LOGGER.info("{}-cylinder line: crank ticks cost {} ns and {} bytes", cylinders,
				cost == null ? -1 : (long) cost[0], cost == null ? -1 : (long) cost[1]);
			helper.assertTrue(cost != null && cost[0] < 2_000_000, "A running " + cylinders + "-cylinder crank tick takes "
				+ (cost == null ? -1 : (long) cost[0]) + " ns");
			helper.assertTrue(cost != null && cost[1] < 2_000_000, "A running " + cylinders
				+ "-cylinder crank tick allocates " + (cost == null ? -1 : (long) cost[1]) + " bytes");
			CreateEngines.LOGGER.info("{}-cylinder line: {} mspt server average, {}", cylinders,
				level.getServer()
					.getAverageTickTimeNanos() / 1e6,
				summary);
			helper.succeed();
		});
	}

	@GameTest(template = LARGE, timeoutTicks = 1100)
	public static void aTwelveCylinderLineRunsOnOneStarter(GameTestHelper helper) {
		runLongEngine(helper, 12);
	}

	@GameTest(template = LARGE, timeoutTicks = 1100)
	public static void aSingleCylinderLineBuiltTheLongWayRuns(GameTestHelper helper) {
		runLongEngine(helper, 1);
	}

	/** Sixteen separate engines starting and running together in one world. */
	@GameTest(template = LARGE, timeoutTicks = 1000)
	public static void sixteenEnginesRunAtOnce(GameTestHelper helper) {
		Level level = helper.getLevel();
		List<BlockPos> origins = new ArrayList<>();
		for (int i = 0; i < 4; i++)
			for (int j = 0; j < 4; j++) {
				BlockPos origin = helper.absolutePos(new BlockPos(1 + 11 * i, 0, 1 + 11 * j));
				origins.add(origin);
				TestEngine.build(level, origin, true);
			}
		helper.runAtTickTime(300, () -> {
			for (BlockPos origin : origins) {
				CrankshaftBlockEntity be = (CrankshaftBlockEntity) level.getBlockEntity(origin.offset(TestEngine.PRIMARY));
				EngineSummary summary = be.summary();
				helper.assertTrue(summary != null && summary.firing() == 1, "Engine at " + origin + " should fire: " + summary);
				level.setBlock(origin.offset(TestEngine.STARTER_POWER), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
			}
		});
		helper.runAtTickTime(700, () -> {
			int running = 0;
			for (BlockPos origin : origins) {
				CrankshaftBlockEntity be = (CrankshaftBlockEntity) level.getBlockEntity(origin.offset(TestEngine.PRIMARY));
				EngineSummary summary = be.summary();
				if (summary != null && summary.state() == EngineState.RUNNING)
					running++;
			}
			CreateEngines.LOGGER.info("Sixteen engines: {} running, {} mspt server average", running, level.getServer()
				.getAverageTickTimeNanos() / 1e6);
			helper.assertTrue(running == 16, running + " of 16 engines are running");
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------------------------------------------------------
	// Unloaded chunks
	// ---------------------------------------------------------------------------------------------------------------

	/**
	 * Every state of every block, placed on the corner of a chunk whose neighbours are not loaded, then ticked, told its
	 * neighbours changed, shape-updated and scanned for the engine it belongs to. A bare lookup of a block in an unloaded
	 * chunk loads it, from disk or by generating it, in the middle of the server tick; so none of these may touch
	 * a neighbouring chunk.
	 */
	@GameTest(template = LARGE, timeoutTicks = 1200)
	@SuppressWarnings({"unchecked", "rawtypes"})
	public static void noBlockLoadsAChunkThatIsNotLoaded(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		List<String> problems = new ArrayList<>();
		int blockIndex = 0;
		for (Block block : engineBlocks()) {
			// Create's own encased cogwheel, which looks at the cogs beside and diagonal to it whenever it ticks, as every Create
			// cog does; only its casing is ours
			if (block instanceof com.simibubi.create.content.kinetics.simpleRelays.encased.EncasedCogwheelBlock)
				continue;
			int cx = 60000 + 6 * blockIndex++;
			int cz = 60000;
			level.getChunk(cx, cz);
			int[][] neighbours = {{1, 0}, {0, 1}, {1, 1}, {-1, 0}, {0, -1}, {-1, -1}};
			boolean clean = true;
			for (int[] n : neighbours)
				if (level.getChunkSource().getChunkNow(cx + n[0], cz + n[1]) != null)
					clean = false;
			if (!clean) {
				CreateEngines.LOGGER.warn("Neighbour chunks of {} were already loaded, skipping {}", cx, block);
				continue;
			}
			String id = BuiltInRegistries.BLOCK.getKey(block)
				.getPath();
			// The corner touching three unloaded chunks, and the opposite corner touching three more
			BlockPos[] corners = {new BlockPos(cx * 16 + 15, 80, cz * 16 + 15), new BlockPos(cx * 16, 80, cz * 16)};
			boolean offended = false;
			for (BlockState state : block.getStateDefinition()
				.getPossibleStates()) {
				if (offended)
					break;
				for (BlockPos pos : corners) {
					if (offended)
						break;
					List<String> names = new ArrayList<>();
					List<Runnable> operations = new ArrayList<>();
					names.add("placing it");
					operations.add(() -> level.getChunkAt(pos).setBlockState(pos, state, false));
					names.add("ticking it");
					operations.add(() -> {
						BlockEntity be = level.getBlockEntity(pos);
						if (be != null && state.getBlock() instanceof EntityBlock entityBlock) {
							BlockEntityTicker ticker = entityBlock.getTicker(level, state, be.getType());
							if (ticker != null)
								for (int i = 0; i < 40; i++)
									ticker.tick(level, pos, state, be);
						}
					});
					names.add("scanning for its engine");
					operations.add(() -> com.createengines.engine.topology.EngineScanner.crankSeeds(level, pos));
					names.add("scanning its engine");
					operations.add(() -> com.createengines.engine.topology.EngineScanner.scan(level, pos));
					names.add("reading its comparator output");
					operations.add(() -> {
						if (state.hasAnalogOutputSignal())
							state.getAnalogOutputSignal(level, pos);
					});
					names.add("removing it");
					operations.add(() -> level.getChunkAt(pos).setBlockState(pos, Blocks.AIR.defaultBlockState(), false));
					boolean judged = false;
					for (int i = 0; i < operations.size() && !offended; i++) {
						String name = names.get(i);
						try {
							operations.get(i)
								.run();
						} catch (Throwable t) {
							problems.add(id + " " + state + ": " + name + " threw " + t);
							offended = true;
						}
						if (i == 0) {
							// Placing may look one block over, which every ticking chunk has loaded; only judge from here on
							boolean touched = false;
							for (int[] n : neighbours)
								touched |= level.getChunkSource()
									.getChunkNow(cx + n[0], cz + n[1]) != null;
							if (touched)
								break;
							continue;
						}
						if (i == operations.size() - 1)
							continue;
						for (int[] n : neighbours)
							if (level.getChunkSource()
								.getChunkNow(cx + n[0], cz + n[1]) != null) {
								problems.add(id + " " + state + " at " + (pos.getX() & 15) + "," + (pos.getZ() & 15)
									+ ": " + name + " loaded the chunk at offset " + n[0] + "," + n[1]);
								offended = true;
								break;
							}
					}
				}
			}
		}
		report(helper, problems);
		helper.succeed();
	}

	/**
	 * An engine in a chunk that has been let go of must stay let go of: nothing the mod does for an engine it can no
	 * longer see may load the chunk it is in again. Builds an engine in a forced chunk, lets it run long enough to be
	 * scanned, unforces the chunk, waits for it to unload, and counts how often it is loaded over the next 700 ticks.
	 */
	@GameTest(template = LARGE, timeoutTicks = 1500)
	public static void anEngineInAnUnloadedChunkDoesNotPullItBackIn(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		int cx = 70000, cz = 70000;
		BlockPos origin = new BlockPos(cx * 16 + 4, 80, cz * 16 + 4);
		int[] loads = {0};
		java.util.function.Consumer<net.neoforged.neoforge.event.level.ChunkEvent.Load> listener = event -> {
			if (event.getLevel() == level && event.getChunk()
				.getPos().x == cx && event.getChunk()
					.getPos().z == cz)
				loads[0]++;
		};
		net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(listener);
		level.setChunkForced(cx, cz, true);
		level.getChunk(cx, cz);
		TestEngine.build(level, origin, true);
		int[] unloadedAt = {-1};
		int[] baseline = {0};
		int[] tick = {0};
		helper.onEachTick(() -> {
			int now = tick[0]++;
			if (now == 60) {
				CrankshaftBlockEntity crank = (CrankshaftBlockEntity) level.getBlockEntity(origin.offset(TestEngine.PRIMARY));
				helper.assertTrue(crank != null && crank.summary() != null, "The engine in the forced chunk never formed");
				level.setChunkForced(cx, cz, false);
			}
			if (now > 60 && unloadedAt[0] < 0 && level.getChunkSource()
				.getChunkNow(cx, cz) == null) {
				unloadedAt[0] = now;
				baseline[0] = loads[0];
			}
			if (now == 100)
				helper.assertTrue(unloadedAt[0] >= 0, "The unforced chunk never unloaded, so the test cannot judge");
			if (unloadedAt[0] >= 0 && now == unloadedAt[0] + 700) {
				net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(listener);
				int again = loads[0] - baseline[0];
				CreateEngines.LOGGER.info("Chunk {},{} unloaded at tick {}, loaded {} more times in the next 700 ticks",
					cx, cz, unloadedAt[0], again);
				helper.assertTrue(again == 0, "The chunk of an unloaded engine was loaded again " + again
					+ " times in 700 ticks with nobody near it");
				helper.succeed();
			}
		});
	}

	// ---------------------------------------------------------------------------------------------------------------
	// Cost
	// ---------------------------------------------------------------------------------------------------------------

	private static final com.sun.management.ThreadMXBean THREADS =
		(com.sun.management.ThreadMXBean) java.lang.management.ManagementFactory.getThreadMXBean();

	/** Mean nanoseconds and bytes allocated by one tick of the block entity at {@code pos}, over {@code ticks} ticks. */
	@SuppressWarnings({"unchecked", "rawtypes"})
	private static double[] cost(Level level, BlockPos pos, int ticks) {
		BlockEntity be = level.getBlockEntity(pos);
		BlockState state = level.getBlockState(pos);
		if (be == null || !(state.getBlock() instanceof EntityBlock entityBlock))
			return null;
		BlockEntityTicker ticker = entityBlock.getTicker(level, state, be.getType());
		if (ticker == null)
			return null;
		for (int i = 0; i < 50; i++)
			ticker.tick(level, pos, state, be);
		long id = Thread.currentThread()
			.threadId();
		long bytes = THREADS.getThreadAllocatedBytes(id);
		long start = System.nanoTime();
		for (int i = 0; i < ticks; i++)
			ticker.tick(level, pos, state, be);
		long time = System.nanoTime() - start;
		bytes = THREADS.getThreadAllocatedBytes(id) - bytes;
		return new double[] {time / (double) ticks, bytes / (double) ticks};
	}

	/** A block left alone in the world must cost next to nothing to tick, and must not churn memory doing it. */
	@GameTest(template = LARGE, timeoutTicks = 600)
	public static void idleBlocksCostNextToNothing(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		List<String> problems = new ArrayList<>();
		StringBuilder table = new StringBuilder("Idle block entity cost per tick:");
		int x = 4;
		for (BlockState state : blockEntityStates()) {
			BlockPos pos = helper.absolutePos(new BlockPos(x, 6, 24));
			x += 2;
			level.setBlock(pos, state, Block.UPDATE_ALL);
			double[] cost = cost(level, pos, 2000);
			String id = BuiltInRegistries.BLOCK.getKey(state.getBlock())
				.getPath();
			if (cost == null)
				continue;
			table.append(String.format("%n  %-22s %8.0f ns %8.0f bytes", id, cost[0], cost[1]));
			if (cost[0] > 100_000)
				problems.add(id + " takes " + (long) cost[0] + " ns a tick just sitting there");
			if (cost[1] > 20_000)
				problems.add(id + " allocates " + (long) cost[1] + " bytes a tick just sitting there");
			level.removeBlock(pos, false);
		}
		CreateEngines.LOGGER.info(table.toString());
		report(helper, problems);
		helper.succeed();
	}

	// ---------------------------------------------------------------------------------------------------------------
	// Churn
	// ---------------------------------------------------------------------------------------------------------------

	private static BlockState randomState(Random random, List<Block> blocks) {
		return switch (random.nextInt(8)) {
			case 0 -> Blocks.AIR.defaultBlockState();
			case 1 -> Blocks.WATER.defaultBlockState();
			case 2 -> Blocks.LAVA.defaultBlockState();
			case 3 -> Blocks.REDSTONE_BLOCK.defaultBlockState();
			case 4 -> AllBlocks.SHAFT.getDefaultState();
			case 5 -> AllBlocks.COGWHEEL.getDefaultState();
			default -> {
				Block block = blocks.get(random.nextInt(blocks.size()));
				List<BlockState> possible = block.getStateDefinition()
					.getPossibleStates();
				yield possible.get(random.nextInt(possible.size()));
			}
		};
	}

	/**
	 * Tears a running engine up at random: replaces blocks anywhere in and around it with random engine parts, water,
	 * lava, shafts, air and redstone, pours random fluids into whatever tanks it finds, reloads block entities from
	 * mangled saves, and flips every setting, for 600 ticks. Then clears the room and builds the engine again, which has
	 * to start and run: nothing a wrecked engine left behind may spoil the next one.
	 */
	private static void churn(GameTestHelper helper, long seed) {
		ServerLevel level = helper.getLevel();
		Random random = new Random(seed);
		List<Block> blocks = engineBlocks();
		BlockPos origin = helper.absolutePos(new BlockPos(20, 4, 20));
		TestEngine.build(level, origin, true);
		List<String> problems = new ArrayList<>();
		int[] ticks = {0};
		helper.onEachTick(() -> {
			if (ticks[0]++ >= 600)
				return;
			int actions = random.nextInt(3);
			for (int a = 0; a < actions; a++) {
				BlockPos target = origin.offset(random.nextInt(11) - 2, random.nextInt(8) - 2, random.nextInt(9) - 2);
				guard(problems, "action at " + target.subtract(origin), () -> {
					switch (random.nextInt(6)) {
						case 0, 1, 2 -> level.setBlock(target, randomState(random, blocks), Block.UPDATE_ALL);
						case 3 -> level.removeBlock(target, random.nextBoolean());
						case 4 -> {
							for (Direction side : new Direction[] {null, Direction.UP, Direction.NORTH}) {
								IFluidHandler handler = level.getCapability(Capabilities.FluidHandler.BLOCK, target, side);
								if (handler != null)
									handler.fill(new FluidStack(random.nextBoolean() ? CEFluids.DIESEL.get()
										.getSource() : Fluids.WATER, 1 + random.nextInt(300)), FluidAction.EXECUTE);
							}
						}
						default -> {
							BlockEntity be = level.getBlockEntity(target);
							if (be != null) {
								CompoundTag honest = be.saveWithFullMetadata(level.registryAccess());
								be.loadWithComponents((CompoundTag) mutate(honest, random, random.nextInt(5)),
									level.registryAccess());
							}
						}
					}
				});
			}
			if (ticks[0] % 15 == 0 && level.getBlockEntity(origin.offset(TestEngine.MOTOR)) instanceof CreativeMotorBlockEntity motor)
				motor.generatedSpeed.setValue(random.nextInt(257) - 128);
		});
		helper.runAtTickTime(650, () -> {
			report(helper, problems);
			// Clear everything the churn could have touched, then start over
			for (int x = -3; x <= 9; x++)
				for (int y = -3; y <= 7; y++)
					for (int z = -3; z <= 7; z++)
						level.setBlock(origin.offset(x, y, z), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
			TestEngine.build(level, origin, true);
		});
		helper.runAtTickTime(950, () -> {
			CrankshaftBlockEntity crank = (CrankshaftBlockEntity) level.getBlockEntity(origin.offset(TestEngine.PRIMARY));
			EngineSummary summary = crank == null ? null : crank.summary();
			helper.assertTrue(summary != null && summary.firing() == 1,
				"After the wreck was cleared the new engine should crank and fire: " + summary);
			level.setBlock(origin.offset(TestEngine.STARTER_POWER), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
		});
		helper.runAtTickTime(1300, () -> {
			CrankshaftBlockEntity crank = (CrankshaftBlockEntity) level.getBlockEntity(origin.offset(TestEngine.PRIMARY));
			EngineSummary summary = crank == null ? null : crank.summary();
			helper.assertTrue(summary != null && summary.state() == EngineState.RUNNING,
				"After the wreck was cleared the new engine should run: " + summary);
			helper.succeed();
		});
	}

	@GameTest(template = LARGE, timeoutTicks = 1400)
	public static void anEngineTornUpAtRandomRebuildsCleanlySeedOne(GameTestHelper helper) {
		churn(helper, 1);
	}

	@GameTest(template = LARGE, timeoutTicks = 1400)
	public static void anEngineTornUpAtRandomRebuildsCleanlySeedTwo(GameTestHelper helper) {
		churn(helper, 2);
	}

	@GameTest(template = LARGE, timeoutTicks = 1400)
	public static void anEngineTornUpAtRandomRebuildsCleanlySeedThree(GameTestHelper helper) {
		churn(helper, 3);
	}

	/**
	 * A running engine written out and read back in, block by block, the way a chunk is saved and loaded: it must pick
	 * up where it left off, without a starter.
	 */
	@GameTest(template = LARGE, timeoutTicks = 1500)
	public static void aRunningEngineCarriesOnAcrossASaveAndReload(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos origin = helper.absolutePos(new BlockPos(20, 4, 20));
		TestEngine.build(level, origin, true);
		helper.runAtTickTime(300, () -> level.setBlock(origin.offset(TestEngine.STARTER_POWER),
			Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL));
		helper.runAtTickTime(700, () -> {
			CrankshaftBlockEntity crank = (CrankshaftBlockEntity) level.getBlockEntity(origin.offset(TestEngine.PRIMARY));
			EngineSummary summary = crank.summary();
			helper.assertTrue(summary != null && summary.state() == EngineState.RUNNING, "Not running before the save: " + summary);
			java.util.Map<BlockPos, BlockState> states = new java.util.LinkedHashMap<>();
			java.util.Map<BlockPos, CompoundTag> tags = new java.util.HashMap<>();
			for (int x = -1; x <= 8; x++)
				for (int y = -1; y <= 6; y++)
					for (int z = -1; z <= 5; z++) {
						BlockPos pos = origin.offset(x, y, z);
						BlockState state = level.getBlockState(pos);
						if (state.isAir())
							continue;
						states.put(pos, state);
						BlockEntity be = level.getBlockEntity(pos);
						if (be != null)
							tags.put(pos, be.saveWithFullMetadata(level.registryAccess()));
					}
			for (BlockPos pos : states.keySet())
				level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
			for (var entry : states.entrySet())
				level.setBlock(entry.getKey(), entry.getValue(), Block.UPDATE_ALL);
			for (var entry : tags.entrySet()) {
				BlockEntity be = level.getBlockEntity(entry.getKey());
				if (be != null)
					be.loadWithComponents(entry.getValue(), level.registryAccess());
			}
		});
		helper.runAtTickTime(1200, () -> {
			CrankshaftBlockEntity crank = (CrankshaftBlockEntity) level.getBlockEntity(origin.offset(TestEngine.PRIMARY));
			EngineSummary summary = crank.summary();
			helper.assertTrue(summary != null && summary.state() == EngineState.RUNNING,
				"The engine did not carry on after the reload: " + summary);
			helper.assertTrue(Math.abs(crank.getSpeed()) >= 20, "Crank speed after the reload is " + crank.getSpeed());
			helper.succeed();
		});
	}

	@GameTest(template = LARGE, timeoutTicks = 1400)
	public static void anEngineTornUpAtRandomRebuildsCleanlySeedFour(GameTestHelper helper) {
		churn(helper, 4);
	}

	@GameTest(template = LARGE, timeoutTicks = 1400)
	public static void anEngineTornUpAtRandomRebuildsCleanlySeedFive(GameTestHelper helper) {
		churn(helper, 5);
	}

	@GameTest(template = LARGE, timeoutTicks = 1400)
	public static void anEngineTornUpAtRandomRebuildsCleanlySeedSix(GameTestHelper helper) {
		churn(helper, 6);
	}

	// ---------------------------------------------------------------------------------------------------------------
	// Environment
	// ---------------------------------------------------------------------------------------------------------------

	/** Rain, thunder and the dead of night make no difference to an engine in the open. */
	@GameTest(template = LARGE, timeoutTicks = 1000)
	public static void weatherAndNightDoNotStopAnEngine(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos origin = helper.absolutePos(new BlockPos(20, 10, 20));
		TestEngine.build(level, origin, true);
		level.setWeatherParameters(0, 6000, true, true);
		level.setDayTime(18000);
		helper.runAtTickTime(300, () -> level.setBlock(origin.offset(TestEngine.STARTER_POWER), Blocks.AIR.defaultBlockState(),
			Block.UPDATE_ALL));
		helper.runAtTickTime(800, () -> {
			CrankshaftBlockEntity crank = (CrankshaftBlockEntity) level.getBlockEntity(origin.offset(TestEngine.PRIMARY));
			EngineSummary summary = crank == null ? null : crank.summary();
			level.setWeatherParameters(6000, 0, false, false);
			helper.assertTrue(summary != null && summary.state() == EngineState.RUNNING,
				"A thunderstorm at night should not stop an engine: " + summary);
			helper.succeed();
		});
	}
}
