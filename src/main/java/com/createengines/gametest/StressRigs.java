package com.createengines.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.createengines.CreateEngines;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.state.BlockState;

/** Helpers shared by the stress GameTests. */
final class StressRigs {

	private StressRigs() {
	}

	/** A 48 x 20 x 48 empty room, big enough for sixteen engines side by side. */
	static final String LARGE = "empty_large";

	static List<Block> engineBlocks() {
		List<Block> blocks = new ArrayList<>();
		for (Block block : BuiltInRegistries.BLOCK)
			if (BuiltInRegistries.BLOCK.getKey(block)
				.getNamespace()
				.equals(CreateEngines.MOD_ID))
				blocks.add(block);
		return blocks;
	}

	/** Runs {@code action}, adding a line to {@code problems} if it throws. */
	static void guard(List<String> problems, String what, Runnable action) {
		try {
			action.run();
		} catch (Throwable t) {
			StackTraceElement[] trace = t.getStackTrace();
			String where = trace.length > 0 ? " at " + trace[0] : "";
			problems.add(what + ": " + t + where);
		}
	}

	static void report(GameTestHelper helper, List<String> problems) {
		if (!problems.isEmpty()) {
			String text = String.join("\n  ", problems.subList(0, Math.min(40, problems.size())));
			CreateEngines.LOGGER.error("Stress test found {} problems:\n  {}", problems.size(), text);
			helper.fail(problems.size() + " problems, first: " + problems.getFirst());
		}
	}

	// ---------------------------------------------------------------------------------------------------------------
	// Saves and settings
	// ---------------------------------------------------------------------------------------------------------------

	static Tag mutate(Tag tag, Random random, int mode) {
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

	/** One of every block with a block entity, at {@code pos} each with its own state. */
	static List<BlockState> blockEntityStates() {
		List<BlockState> states = new ArrayList<>();
		for (Block block : engineBlocks())
			if (block instanceof EntityBlock)
				states.add(block.defaultBlockState());
		return states;
	}

	// ---------------------------------------------------------------------------------------------------------------
	// Cost
	// ---------------------------------------------------------------------------------------------------------------

	static final com.sun.management.ThreadMXBean THREADS =
		(com.sun.management.ThreadMXBean) java.lang.management.ManagementFactory.getThreadMXBean();

	/** Mean nanoseconds and bytes allocated by one tick of the block entity at {@code pos}, over {@code ticks} ticks. */
	@SuppressWarnings({"unchecked", "rawtypes"})
	static double[] cost(Level level, BlockPos pos, int ticks) {
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
}
