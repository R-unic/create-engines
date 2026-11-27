package com.createengines.content;

import com.createengines.compat.sable.SableCompat;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Asks a level the questions {@link NoteSurroundings} has about it. A full block stops sound, glass included; leaves,
 * anything smaller than a block (an engine's own parts among them) and chunks not loaded let it by. The blocks a moving
 * structure is made of are kept away from where they are drawn, so they are not seen: only the world's own walls muffle
 * an engine on one.
 */
final class NoteWorldProbe {

	private NoteWorldProbe() {
	}

	private static NoteSurroundings.Blocks blocks(Level level) {
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		return (x, y, z) -> {
			at.set(x, y, z);
			if (!level.isLoaded(at))
				return false;
			BlockState state = level.getBlockState(at);
			return !state.isAir() && !state.is(BlockTags.LEAVES) && state.isCollisionShapeFullBlock(level, at);
		};
	}

	/** The walls between a listener at {@code ear} and an engine heard from {@code source}. */
	static int walls(Level level, Vec3 ear, Vec3 source) {
		Vec3 from = SableCompat.worldPosition(level, source);
		long began = SoundProbe.active() ? System.nanoTime() : 0;
		int walls = NoteSurroundings.walls(blocks(level), ear.x, ear.y, ear.z, from.x, from.y, from.z);
		if (began != 0)
			SoundProbe.looked(System.nanoTime() - began);
		return walls;
	}

	static NoteSurroundings.Room room(Level level, Vec3 ear) {
		long began = SoundProbe.active() ? System.nanoTime() : 0;
		NoteSurroundings.Room room = NoteSurroundings.room(blocks(level), ear.x, ear.y, ear.z);
		if (began != 0)
			SoundProbe.looked(System.nanoTime() - began);
		return room;
	}
}
