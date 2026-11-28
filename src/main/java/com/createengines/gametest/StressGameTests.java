package com.createengines.gametest;

import static com.createengines.gametest.StressRigs.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.createengines.CreateEngines;
import com.createengines.engine.LoadedBlocks;
import com.createengines.registry.CEBlocks;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
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
	/**
	 * {@code LoadedBlocks.blockEntity} answers from one chunk lookup and must give what {@code Level.getBlockEntity} gives
	 * for anything loaded, nothing for air, nothing outside the world's height, and nothing for a chunk that is not loaded,
	 * without loading it.
	 */
	@GameTest(template = LARGE, timeoutTicks = 40)
	public static void loadedBlocksFindsPartsWithoutLoadingChunks(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos origin = helper.absolutePos(BlockPos.ZERO);
		TestEngine.build(level, origin, true);
		List<String> problems = new ArrayList<>();
		int found = 0;
		for (int x = 0; x < 6; x++)
			for (int y = 0; y < 6; y++)
				for (int z = 0; z < 6; z++) {
					BlockPos pos = origin.offset(x, y, z);
					BlockEntity expected = level.getBlockEntity(pos);
					if (LoadedBlocks.blockEntity(level, pos) != expected)
						problems.add("at " + pos.toShortString() + " got " + LoadedBlocks.blockEntity(level, pos) + ", expected " + expected);
					if (expected != null)
						found++;
				}
		if (found < 3)
			problems.add("only " + found + " block entities in the engine, the check proved nothing");
		if (LoadedBlocks.blockEntity(level, origin.offset(TestEngine.PRIMARY)) == null)
			problems.add("the crankshaft was not found");
		if (LoadedBlocks.blockEntity(level, origin.offset(20, 10, 20)) != null)
			problems.add("air has a block entity");
		if (LoadedBlocks.blockEntity(level, new BlockPos(origin.getX(), level.getMaxBuildHeight() + 5, origin.getZ())) != null)
			problems.add("a position above the world has a block entity");
		if (LoadedBlocks.blockEntity(level, new BlockPos(origin.getX(), level.getMinBuildHeight() - 5, origin.getZ())) != null)
			problems.add("a position below the world has a block entity");
		BlockPos far = new BlockPos(origin.getX() + 2_000_000, 64, origin.getZ() + 2_000_000);
		if (level.hasChunkAt(far))
			problems.add("the far chunk was already loaded before the test");
		if (LoadedBlocks.blockEntity(level, far) != null)
			problems.add("a block entity came from a chunk that is not loaded");
		if (level.hasChunkAt(far))
			problems.add("looking up a part in an unloaded chunk loaded it");
		report(helper, problems);
		helper.succeed();
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
}
