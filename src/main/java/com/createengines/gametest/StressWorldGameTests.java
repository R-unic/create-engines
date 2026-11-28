package com.createengines.gametest;

import static com.createengines.gametest.StressRigs.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.createengines.CreateEngines;
import com.createengines.content.crankshaft.CrankshaftBlockEntity;
import com.createengines.engine.EngineState;
import com.createengines.engine.EngineSummary;
import com.createengines.registry.CEFluids;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Unloaded chunks, idle cost, random tearing up, save and reload, weather. */
@GameTestHolder(CreateEngines.MOD_ID)
@PrefixGameTestTemplate(false)
public class StressWorldGameTests {

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
	@GameTest(template = LARGE, timeoutTicks = 2500)
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
		int[] formedAt = {-1};
		// Forming and unloading both wait on the chunk system (a far chunk and its neighbours are generated on other threads),
		// so each is waited for up to a generous limit instead of being judged at a fixed tick. What the test judges, that the
		// engine formed and that the chunk is not loaded again afterwards, is unchanged.
		final int formLimit = 600;
		final int unloadLimit = 300;
		EveryTick.run(helper, () -> {
			int now = tick[0]++;
			if (formedAt[0] < 0) {
				CrankshaftBlockEntity crank = (CrankshaftBlockEntity) level.getBlockEntity(origin.offset(TestEngine.PRIMARY));
				if (crank != null && crank.summary() != null) {
					formedAt[0] = now;
					level.setChunkForced(cx, cz, false);
				} else if (now >= formLimit) {
					net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(listener);
					level.setChunkForced(cx, cz, false);
					helper.fail("The engine in the forced chunk never formed in " + formLimit + " ticks");
				}
				return;
			}
			if (unloadedAt[0] < 0 && level.getChunkSource()
				.getChunkNow(cx, cz) == null) {
				unloadedAt[0] = now;
				baseline[0] = loads[0];
			}
			if (unloadedAt[0] < 0 && now >= formedAt[0] + unloadLimit) {
				net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(listener);
				helper.fail("The unforced chunk never unloaded in " + unloadLimit + " ticks, so the test cannot judge");
			}
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
		EveryTick.run(helper, () -> {
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
