package com.createengines.compat.sable;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;

/**
 * Sable (mod id {@value #MOD_ID}), the physics engine under Create Aeronautics: a moving structure is kept as real blocks in a
 * "plot" far from where it is drawn and where it is, so a block's own coordinates say nothing about where it is in the world.
 * Anything that asks "where is this engine" for the air, the sky or the sea has to project the position out of the plot first.
 * Optional and one way. Sable's types are named only in {@link SableBridge}, which is never loaded without the mod.
 */
public final class SableCompat {

	public static final String MOD_ID = "sable";

	private static final Logger LOGGER = LogUtils.getLogger();

	private static boolean broken;

	private SableCompat() {
	}

	public static boolean installed() {
		return !broken && ModList.get() != null && ModList.get()
			.isLoaded(MOD_ID);
	}

	/** Where {@code pos} really is in the world: itself, unless a moving structure holds it. A failure turns this off. */
	public static Vec3 worldPosition(Level level, BlockPos pos) {
		Vec3 centre = Vec3.atCenterOf(pos);
		if (!installed())
			return centre;
		try {
			return SableBridge.project(level, centre);
		} catch (LinkageError | RuntimeException e) {
			broken = true;
			LOGGER.warn("Sable did not answer where a block is; engines will use the block's own height this session", e);
			return centre;
		}
	}

	/**
	 * The block cell of the world that the block at {@code pos} is in right now: {@code pos} itself unless a moving structure holds
	 * it. Used for what the world around an engine decides (the biome's cold, the rain, where the exhaust goes), none of which
	 * exists in the plot. Without Sable, or off a structure, this is {@code pos} and costs a check only.
	 */
	public static BlockPos worldBlockPos(Level level, BlockPos pos) {
		if (!installed())
			return pos;
		try {
			if (!SableBridge.inPlot(level, pos))
				return pos;
		} catch (LinkageError | RuntimeException e) {
			broken = true;
			LOGGER.warn("Sable did not answer whether a block is on a moving structure; engines will use block positions as they are this session", e);
			return pos;
		}
		return BlockPos.containing(worldPosition(level, pos));
	}

	/**
	 * Whether the block at {@code pos}, which is on a moving structure, has a fluid of the real world against any face. A plot has
	 * no sea, so an engine's own checks for water (which read the blocks beside it) would never see the one the structure is in.
	 * False off a structure, and without Sable, so the ordinary check is all that applies there.
	 */
	public static boolean wetInWorld(Level level, BlockPos pos) {
		if (!onStructure(level, pos))
			return false;
		for (net.minecraft.core.Direction side : net.minecraft.core.Direction.values())
			if (fluidInWorldAt(level, pos.relative(side)))
				return true;
		return false;
	}

	/** Whether the cell that {@code pos} (on a moving structure) is in, in the world, holds a fluid. False off a structure. */
	public static boolean fluidInWorldAt(Level level, BlockPos pos) {
		if (!onStructure(level, pos))
			return false;
		BlockPos cell = worldBlockPos(level, pos);
		return level.isLoaded(cell) && !level.getFluidState(cell)
			.isEmpty();
	}

	/** Whether {@code pos} is in a moving structure's plot. */
	public static boolean onStructure(Level level, BlockPos pos) {
		if (!installed())
			return false;
		try {
			return SableBridge.inPlot(level, pos);
		} catch (LinkageError | RuntimeException e) {
			broken = true;
			LOGGER.warn("Sable did not answer whether a block is on a moving structure; engines will use block positions as they are this session", e);
			return false;
		}
	}

	/** The height in the world of the block at {@code pos}, which is {@code pos.getY()} off a moving structure. */
	public static double worldY(Level level, BlockPos pos) {
		return worldPosition(level, pos).y;
	}
}
