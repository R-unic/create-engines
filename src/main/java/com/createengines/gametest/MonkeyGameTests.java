package com.createengines.gametest;

import static com.createengines.gametest.TestEngine.STARTER_POWER;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.createengines.CreateEngines;
import com.createengines.content.crankshaft.CrankshaftBlockEntity;
import com.createengines.engine.EngineState;
import com.createengines.engine.EngineSummary;
import com.createengines.smoke.audit.ServerAudit;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Verification track item 3: a seeded monkey. For a few hundred ticks it places this mod's blocks (in sampled states) and
 * other things at random, breaks blocks, right-clicks them with random items including Create's wrench, sneaks, toggles
 * redstone, pokes neighbours, fires random ticks and round-trips block entities through their saved data, around two
 * running engines. Then it clears everything.
 * <p>
 * Asserted: no action throws; nothing is left behind (no block entity in the area once it is cleared); and a fresh engine
 * built afterwards still starts and runs, so no global state was wedged. The seed is in every failure message, and
 * {@code CE_MONKEY_SEED} adds one more seed to the usual three.
 */
@GameTestHolder(CreateEngines.MOD_ID)
@PrefixGameTestTemplate(false)
public class MonkeyGameTests {

	private static final int SPAN = 30;
	private static final int FLOOR = 1;
	private static final int CEILING = 12;
	private static final int MONKEY_TICKS = 400;
	private static final int ACTIONS_PER_TICK = 3;

	private static List<Block> ourBlocks;
	private static List<net.minecraft.world.item.Item> items;

	private static synchronized void prepare() {
		if (ourBlocks != null)
			return;
		ourBlocks = ServerAudit.blocks();
		List<net.minecraft.world.item.Item> list = new ArrayList<>(ServerAudit.items());
		list.add(AllItems.WRENCH.get());
		list.add(Items.WATER_BUCKET);
		list.add(Items.LAVA_BUCKET);
		list.add(Items.BUCKET);
		list.add(Items.REDSTONE);
		list.add(Items.STICK);
		list.add(AllBlocks.SHAFT.asItem());
		list.add(AllBlocks.COGWHEEL.asItem());
		list.add(AllBlocks.LARGE_COGWHEEL.asItem());
		list.add(Items.DIAMOND_PICKAXE);
		list.add(Items.AIR);
		items = list;
	}

	/** Everything one run needs, so the scheduled lambdas stay short. */
	private static final class Run {
		final long seed;
		final Random random;
		final ServerLevel level;
		final GameTestHelper helper;
		final List<String> problems = new ArrayList<>();
		final List<String> log = new ArrayList<>();
		final List<BlockPos> placed = new ArrayList<>();
		final net.minecraft.world.entity.player.Player player;
		final BlockPos origin;
		int actions;

		Run(GameTestHelper helper, long seed) {
			this.helper = helper;
			this.seed = seed;
			this.random = new Random(seed);
			this.level = helper.getLevel();
			this.player = helper.makeMockPlayer(GameType.SURVIVAL);
			this.origin = helper.absolutePos(BlockPos.ZERO);
		}

		BlockPos randomPos() {
			return origin.offset(random.nextInt(SPAN), FLOOR + random.nextInt(CEILING - FLOOR), random.nextInt(SPAN));
		}

		BlockPos knownOrRandom() {
			return !placed.isEmpty() && random.nextInt(4) != 0 ? placed.get(random.nextInt(placed.size())) : randomPos();
		}

		boolean inside(BlockPos pos) {
			BlockPos rel = pos.subtract(origin);
			return rel.getX() >= 0 && rel.getX() < SPAN && rel.getZ() >= 0 && rel.getZ() < SPAN && rel.getY() >= 0 && rel.getY() <= CEILING;
		}

		void act() {
			actions++;
			int kind = random.nextInt(10);
			BlockPos pos = knownOrRandom();
			String what = "action " + actions + " kind " + kind + " at " + helper.relativePos(pos).toShortString();
			try {
				switch (kind) {
					case 0, 1 -> place(pos);
					case 2 -> level.destroyBlock(pos, random.nextBoolean());
					case 3, 4, 5 -> click(pos);
					case 6 -> redstone(pos);
					case 7 -> poke(pos);
					case 8 -> roundTrip(pos);
					default -> placeOther(pos);
				}
			} catch (Throwable t) {
				problems.add(what + " threw " + t);
				CreateEngines.LOGGER.error("Monkey seed {} {}", seed, what, t);
			}
		}

		void place(BlockPos pos) {
			Block block = ourBlocks.get(random.nextInt(ourBlocks.size()));
			List<BlockState> states = ServerAudit.sampleStates(block);
			level.setBlock(pos, states.get(random.nextInt(states.size())), 3);
			placed.add(pos);
		}

		void placeOther(BlockPos pos) {
			Block[] others = {Blocks.STONE, Blocks.REDSTONE_BLOCK, Blocks.WATER, Blocks.LAVA, Blocks.CHEST, Blocks.HOPPER, Blocks.PISTON,
				Blocks.LEVER, Blocks.OBSERVER, Blocks.TNT, AllBlocks.SHAFT.get(), AllBlocks.COGWHEEL.get(), AllBlocks.LARGE_COGWHEEL.get(),
				AllBlocks.FLUID_PIPE.get(), AllBlocks.FLUID_TANK.get()};
			level.setBlock(pos, others[random.nextInt(others.length)].defaultBlockState(), 3);
			placed.add(pos);
		}

		void click(BlockPos pos) {
			BlockState state = level.getBlockState(pos);
			ItemStack stack = new ItemStack(items.get(random.nextInt(items.size())), 1 + random.nextInt(3));
			player.setItemInHand(InteractionHand.MAIN_HAND, stack);
			player.setShiftKeyDown(random.nextInt(3) == 0);
			Direction face = Direction.values()[random.nextInt(6)];
			Vec3 at = Vec3.atCenterOf(pos).add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
			BlockHitResult hit = new BlockHitResult(at, face, pos, false);
			var result = state.useItemOn(stack, level, player, InteractionHand.MAIN_HAND, hit);
			if (result.result() == InteractionResult.PASS || result == net.minecraft.world.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION)
				state.useWithoutItem(level, player, hit);
			if (random.nextInt(4) == 0)
				state.attack(level, pos, player);
			player.setShiftKeyDown(false);
		}

		void redstone(BlockPos pos) {
			BlockPos next = pos.relative(Direction.values()[random.nextInt(6)]);
			if (!inside(next))
				return;
			if (level.getBlockState(next).is(Blocks.REDSTONE_BLOCK))
				level.removeBlock(next, false);
			else if (level.getBlockState(next).isAir())
				level.setBlock(next, Blocks.REDSTONE_BLOCK.defaultBlockState(), 3);
		}

		void poke(BlockPos pos) {
			BlockState state = level.getBlockState(pos);
			if (random.nextBoolean())
				level.updateNeighborsAt(pos, state.getBlock());
			else
				state.randomTick(level, pos, level.random);
		}

		void roundTrip(BlockPos pos) {
			BlockEntity entity = level.getBlockEntity(pos);
			if (entity == null)
				return;
			var access = level.registryAccess();
			var saved = entity.saveWithFullMetadata(access);
			BlockEntity copy = BlockEntity.loadStatic(pos, level.getBlockState(pos), saved, access);
			if (copy != null)
				entity.loadWithComponents(copy.saveWithFullMetadata(access), access);
		}

		/** Takes everything out of the area, items included, a few times so spilled fluid goes too. */
		void clear() {
			for (int x = 0; x < SPAN; x++)
				for (int y = 0; y <= CEILING; y++)
					for (int z = 0; z < SPAN; z++) {
						BlockPos pos = origin.offset(x, y, z);
						if (!level.getBlockState(pos).isAir())
							level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
					}
			for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, new AABB(origin).inflate(SPAN + 2, CEILING + 4, SPAN + 2)))
				item.discard();
		}

		int leftoverBlockEntities() {
			int count = 0;
			for (int cx = origin.getX() >> 4; cx <= (origin.getX() + SPAN) >> 4; cx++)
				for (int cz = origin.getZ() >> 4; cz <= (origin.getZ() + SPAN) >> 4; cz++) {
					LevelChunk chunk = level.getChunk(cx, cz);
					for (BlockPos at : new ArrayList<>(chunk.getBlockEntities().keySet()))
						if (inside(at))
							count++;
				}
			return count;
		}
	}

	private static void monkey(GameTestHelper helper, long seed) {
		prepare();
		Run run = new Run(helper, seed);
		ServerLevel level = run.level;
		BlockPos spark = helper.absolutePos(new BlockPos(4, 1, 4));
		BlockPos diesel = helper.absolutePos(new BlockPos(18, 1, 4));
		TestEngine.buildSpark(level, spark, true);
		TestEngine.build(level, diesel, true);
		// The engines themselves are targets too, not only what the monkey places: most clicks should land on parts that matter
		for (BlockPos base : List.of(spark, diesel))
			for (int x = 0; x < 9; x++)
				for (int y = 0; y < 7; y++)
					for (int z = 0; z < 6; z++)
						if (!level.getBlockState(base.offset(x, y, z)).isAir())
							run.placed.add(base.offset(x, y, z));
		for (int t = 5; t < MONKEY_TICKS; t++)
			helper.runAtTickTime(t, () -> {
				for (int i = 0; i < ACTIONS_PER_TICK; i++)
					run.act();
			});
		int cleanup = MONKEY_TICKS + 10;
		helper.runAtTickTime(cleanup, run::clear);
		helper.runAtTickTime(cleanup + 30, run::clear);
		helper.runAtTickTime(cleanup + 60, run::clear);
		helper.runAtTickTime(cleanup + 90, () -> {
			run.clear();
			int left = run.leftoverBlockEntities();
			if (left != 0)
				run.problems.add(left + " block entities are still in the area after everything in it was removed");
		});
		// The canary: a new engine in clear ground must still start and run
		BlockPos fresh = helper.absolutePos(new BlockPos(36, 1, 36));
		helper.runAtTickTime(cleanup + 100, () -> TestEngine.build(level, fresh, true));
		helper.runAtTickTime(cleanup + 400, () -> level.removeBlock(fresh.offset(STARTER_POWER), false));
		helper.runAtTickTime(cleanup + 600, () -> {
			if (level.getBlockEntity(fresh.offset(TestEngine.PRIMARY)) instanceof CrankshaftBlockEntity crank) {
				EngineSummary summary = crank.summary();
				if (summary == null || summary.state() != EngineState.RUNNING)
					run.problems.add("a fresh engine built after the monkey is " + (summary == null ? "not built" : summary.state())
						+ ", not running");
			} else
				run.problems.add("a fresh engine built after the monkey has no crankshaft");
			if (run.problems.isEmpty()) {
				CreateEngines.LOGGER.info("Monkey seed {}: {} actions, nothing wrong", seed, run.actions);
				helper.succeed();
			} else {
				StringBuilder message = new StringBuilder("monkey seed " + seed + ": " + run.problems.size() + " problems after "
					+ run.actions + " actions:");
				for (int i = 0; i < Math.min(10, run.problems.size()); i++)
					message.append("\n  ").append(run.problems.get(i));
				helper.fail(message.toString());
			}
		});
	}

	@GameTest(template = "empty_large", timeoutTicks = 2600, batch = "smoke-monkey")
	public static void aMonkeyWithSeedOneCannotBreakAnEngine(GameTestHelper helper) {
		monkey(helper, 1);
	}

	@GameTest(template = "empty_large", timeoutTicks = 2600, batch = "smoke-monkey")
	public static void aMonkeyWithSeedTwoCannotBreakAnEngine(GameTestHelper helper) {
		monkey(helper, 2);
	}

	@GameTest(template = "empty_large", timeoutTicks = 2600, batch = "smoke-monkey")
	public static void aMonkeyWithSeedThreeCannotBreakAnEngine(GameTestHelper helper) {
		monkey(helper, 3);
	}

	/** Runs one more seed when {@code CE_MONKEY_SEED} is set, to chase a failure or go looking for one. */
	@GameTest(template = "empty_large", timeoutTicks = 2600, batch = "smoke-monkey")
	public static void aMonkeyWithTheSeedFromTheEnvironmentCannotBreakAnEngine(GameTestHelper helper) {
		String seed = System.getenv("CE_MONKEY_SEED");
		if (seed == null) {
			helper.succeed();
			return;
		}
		monkey(helper, Long.parseLong(seed.trim()));
	}
}
