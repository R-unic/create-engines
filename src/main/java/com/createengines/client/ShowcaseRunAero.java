package com.createengines.client;

import java.util.ArrayList;
import java.util.List;

import com.createengines.CreateEngines;
import com.createengines.registry.CEBlocks;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * The trailer's wheeled car: an engine built on a pad is lifted into a moving structure (Create Aeronautics, through Sable) and
 * drives away on its own wheel (Offroad, bundled with Aeronautics). The only trailer class that names Sable's types, so the run loads
 * without the mod; used only when {@code SableCompat.installed()}. Development aid, kept out of the jar like {@link ShowcaseRun}.
 */
final class ShowcaseRunAero {

	private static ServerSubLevel craft;
	private static int ticks;

	private ShowcaseRunAero() {
	}

	/** Starts publishing the car's place in the world every server tick, for the trailer's camera to follow. */
	static void listen() {
		NeoForge.EVENT_BUS.addListener(ShowcaseRunAero::onServerTick);
	}

	/**
	 * Lifts every block in the box into one structure, where it stands, and returns the cylinder heads' new places (in the
	 * structure's plot) so the trailer can keep feeding them.
	 */
	static List<BlockPos> assemble(ServerLevel level, BlockPos min, BlockPos max) {
		List<BlockPos> blocks = new ArrayList<>();
		for (BlockPos pos : BlockPos.betweenClosed(min, max))
			if (!level.getBlockState(pos)
				.is(Blocks.AIR))
				blocks.add(pos.immutable());
		BoundingBox3ic bounds = BoundingBox3i.from(blocks);
		craft = SubLevelAssemblyHelper.assembleBlocks(level, blocks.get(0), blocks, bounds);
		BoundingBox3ic plot = craft.getPlot()
			.getBoundingBox();
		List<BlockPos> heads = new ArrayList<>();
		for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(plot.minX(), plot.minY(), plot.minZ()),
			new BlockPos(plot.maxX(), plot.maxY(), plot.maxZ())))
			if (level.getBlockState(pos)
				.is(CEBlocks.CYLINDER_HEAD.get()))
				heads.add(pos.immutable());
		// A structure that is only part of the car (blocks that touch nothing are split off) shows as few blocks and heads here
		CreateEngines.LOGGER.info("Showcase aero: {} blocks in a structure at {}, {} heads", blocks.size(), position(), heads.size());
		return heads;
	}

	/** Where the structure is in the world, {x, y, z}, or null before there is one. A new array each call. */
	static double[] position() {
		if (craft == null)
			return null;
		var at = craft.logicalPose()
			.position();
		return new double[] { at.x, at.y, at.z };
	}

	private static void onServerTick(ServerTickEvent.Post event) {
		double[] at = position();
		ShowcaseRun.aeroAt = at;
		// One line a second, to tell a car that drives from one that sits
		if (at != null && ++ticks % 20 == 0)
			CreateEngines.LOGGER.info("Showcase aero at {} {} {}", Math.round(at[0] * 10) / 10.0, Math.round(at[1] * 10) / 10.0,
				Math.round(at[2] * 10) / 10.0);
	}
}
