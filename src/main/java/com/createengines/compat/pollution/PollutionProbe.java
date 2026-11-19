package com.createengines.compat.pollution;

import com.endertech.minecraft.mods.adpother.init.Pollutants;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Reads back how much carbon and sulfur sit in the world, for the GameTests that tune and check the release rates. Names the
 * pollution mod's types like {@link PollutionBridge}, so it is only ever called when that mod is installed, and nothing the
 * engines do reads from it.
 */
public final class PollutionProbe {

	private PollutionProbe() {
	}

	/** Steps of carbon one cell can hold, in the mod's own units. */
	public static int carbonCapacity() {
		return Pollutants.BuiltIn.CARBON.get()
			.getPollutionCapacity();
	}

	/** Steps of carbon in one cell. */
	public static int carbonAt(Level level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		return Pollutants.BuiltIn.CARBON.get()
			.getCarriedPollutionAmount(state);
	}

	/** Steps of sulfur in one cell. */
	public static int sulfurAt(Level level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		return Pollutants.BuiltIn.SULFUR.get()
			.getCarriedPollutionAmount(state);
	}

	/** Total steps of carbon in the cube of the given radius round {@code centre}. */
	public static long carbonAround(Level level, BlockPos centre, int radius) {
		long total = 0;
		for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-radius, -radius, -radius), centre.offset(radius, radius, radius)))
			total += carbonAt(level, pos);
		return total;
	}

	/** Total steps of sulfur in the cube of the given radius round {@code centre}. */
	public static long sulfurAround(Level level, BlockPos centre, int radius) {
		long total = 0;
		for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-radius, -radius, -radius), centre.offset(radius, radius, radius)))
			total += sulfurAt(level, pos);
		return total;
	}

	/** Puts steps of carbon straight into one cell, for tests that watch how long a gas lasts. Returns how many went in. */
	public static int pumpCarbon(Level level, BlockPos pos, int steps) {
		return Pollutants.BuiltIn.CARBON.get()
			.pump(level, pos, steps);
	}

	/** Puts steps of sulfur straight into one cell. Returns how many went in. */
	public static int pumpSulfur(Level level, BlockPos pos, int steps) {
		return Pollutants.BuiltIn.SULFUR.get()
			.pump(level, pos, steps);
	}

	// What each outlet has put into the air, counted only while a test asks for it, so a running game keeps no record at all

	private static volatile boolean counting;
	private static final java.util.concurrent.ConcurrentHashMap<BlockPos, long[]> RELEASED = new java.util.concurrent.ConcurrentHashMap<>();

	/** Starts counting the steps each outlet is offered to put into the air, and forgets earlier counts. */
	public static void startCounting() {
		RELEASED.clear();
		counting = true;
	}

	/** Stops counting and forgets the counts. */
	public static void stopCounting() {
		counting = false;
		RELEASED.clear();
	}

	/** Steps of carbon the outlet at {@code outlet} was offered to release since {@link #startCounting()}. A cell that is full turns some away, so this is what the engine made, not what reached the air. */
	public static long carbonReleased(BlockPos outlet) {
		long[] steps = RELEASED.get(outlet.immutable());
		return steps == null ? 0 : steps[0];
	}

	/** Steps of sulfur the outlet at {@code outlet} was offered to release since {@link #startCounting()}. A cell that is full turns some away, so this is what the engine made, not what reached the air. */
	public static long sulfurReleased(BlockPos outlet) {
		long[] steps = RELEASED.get(outlet.immutable());
		return steps == null ? 0 : steps[1];
	}

	static void record(BlockPos outlet, boolean carbon, int steps) {
		if (counting && steps > 0)
			RELEASED.computeIfAbsent(outlet.immutable(), key -> new long[2])[carbon ? 0 : 1] += steps;
	}
}
