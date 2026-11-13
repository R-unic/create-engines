package com.createengines.engine;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Block entity lookups that stay inside the chunks that are already loaded. A bare {@code Level.getBlockEntity} loads
 * the chunk it asks about, so an engine, a camshaft or a cable of parts that reaches across the edge of the loaded
 * area would keep pulling in chunks nobody is near, one lookup a tick.
 */
public final class LoadedBlocks {

	private LoadedBlocks() {
	}

	/** The block entity at {@code pos}, or null if there is none or its chunk is not loaded. */
	public static @Nullable BlockEntity blockEntity(Level level, BlockPos pos) {
		return level.isLoaded(pos) ? level.getBlockEntity(pos) : null;
	}
}
