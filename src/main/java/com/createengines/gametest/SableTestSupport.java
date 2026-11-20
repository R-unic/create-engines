package com.createengines.gametest;

import java.util.ArrayList;
import java.util.List;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

/** The only GameTest class that names Sable's types, so the tests load without the mod. Used by {@link SableGameTests}. */
final class SableTestSupport {

	/** A block inside a moving structure, and where the structure is. */
	record Held(BlockPos inPlot, double structureY) {
	}

	private SableTestSupport() {
	}

	/** The mass Sable gives a block, as it would use it for the structure's weight. */
	static double mass(ServerLevel level, BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
		return dev.ryanhcode.sable.physics.config.block_properties.PhysicsBlockPropertyHelper.getMass(level, pos, state);
	}

	/** Lifts every block in the box into a moving structure and puts that structure at {@code worldY}. */
	static Held assemble(ServerLevel level, BlockPos min, BlockPos max, double worldY) {
		List<BlockPos> blocks = new ArrayList<>();
		for (BlockPos pos : BlockPos.betweenClosed(min, max))
			if (!level.getBlockState(pos)
				.is(Blocks.AIR))
				blocks.add(pos.immutable());
		BoundingBox3ic bounds = BoundingBox3i.from(blocks);
		ServerSubLevel sub = SubLevelAssemblyHelper.assembleBlocks(level, blocks.get(0), blocks, bounds);
		sub.logicalPose()
			.position()
			.y = worldY;
		BoundingBox3ic plot = sub.getPlot()
			.getBoundingBox();
		return new Held(new BlockPos(plot.minX(), plot.minY(), plot.minZ()), worldY);
	}
}
