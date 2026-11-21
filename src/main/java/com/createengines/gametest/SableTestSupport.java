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
	record Held(BlockPos inPlot, BlockPos plotMax, double structureY) {
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
			// Not the test's own structure block, which is in the box and not touching the engine: as the anchor it would be a
			// structure of its own and the engine, joined to nothing, would be split off into another one on the next tick
			if (!level.getBlockState(pos)
				.is(Blocks.AIR) && !level.getBlockState(pos)
					.is(Blocks.STRUCTURE_BLOCK))
				blocks.add(pos.immutable());
		BoundingBox3ic bounds = BoundingBox3i.from(blocks);
		ServerSubLevel sub = SubLevelAssemblyHelper.assembleBlocks(level, blocks.get(0), blocks, bounds);
		sub.logicalPose()
			.position()
			.y = worldY;
		BoundingBox3ic plot = sub.getPlot()
			.getBoundingBox();
		return new Held(new BlockPos(plot.minX(), plot.minY(), plot.minZ()), new BlockPos(plot.maxX(), plot.maxY(), plot.maxZ()), worldY);
	}

	/** The first block of {@code block} in the structure's plot, or null: where an assembled engine's parts ended up. */
	static BlockPos find(ServerLevel level, Held held, net.minecraft.world.level.block.Block block) {
		for (BlockPos pos : BlockPos.betweenClosed(held.inPlot(), held.plotMax()))
			if (level.getBlockState(pos)
				.is(block))
				return pos.immutable();
		return null;
	}

	/** What the plot holds, block by block, for a failure message: a structure that came out empty says so. */
	static String contents(ServerLevel level, Held held) {
		java.util.Map<String, Integer> counts = new java.util.TreeMap<>();
		for (BlockPos pos : BlockPos.betweenClosed(held.inPlot(), held.plotMax())) {
			var state = level.getBlockState(pos);
			if (!state.isAir())
				counts.merge(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock())
					.toString(), 1, Integer::sum);
		}
		return counts + " between " + held.inPlot() + " and " + held.plotMax();
	}
}
