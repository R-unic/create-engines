package com.createengines.client;

import java.util.ArrayList;
import java.util.List;

import org.joml.Quaterniond;
import org.joml.Vector3d;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;

/**
 * The only part of the sound bench that names Sable's types, so the bench loads without the mod: lifts the pad's engine
 * into a moving structure and carries it where the script says. Server thread only.
 */
final class ShowcaseRunSoundBenchSable {

	/** How far the structure is lifted clear of the ground, in blocks. */
	private static final double LIFT = 1;
	private static final double SECONDS_A_TICK = 0.05;

	private static ServerSubLevel structure;
	private static Vector3d home;
	private static Quaterniond facing;
	private static double carried;

	private ShowcaseRunSoundBenchSable() {
	}

	static void assemble(ServerLevel level, ShowcaseRunSoundBench.Rig rig) {
		BlockPos pad = ShowcaseRunSoundBench.pad();
		List<BlockPos> blocks = new ArrayList<>();
		for (BlockPos pos : ShowcaseRunSoundBench.blocks(pad, rig.length()))
			if (!level.getBlockState(pos)
				.isAir())
				blocks.add(pos);
		BoundingBox3ic bounds = BoundingBox3i.from(blocks);
		structure = SubLevelAssemblyHelper.assembleBlocks(level, blocks.get(0), blocks, bounds);
		BoundingBox3ic plot = structure.getPlot()
			.getBoundingBox();
		// The blocks keep their places beside each other, so the pad's corner moves by what the lowest corner moved
		ShowcaseRunSoundBench.movedTo(pad.offset(plot.minX() - bounds.minX(), plot.minY() - bounds.minY(),
			plot.minZ() - bounds.minZ()));
		home = new Vector3d(structure.logicalPose()
			.position()).add(0, LIFT, 0);
		facing = new Quaterniond(structure.logicalPose()
			.orientation());
		carried = 0;
	}

	/** Puts the structure {@code along} blocks east of where it was lifted, moving at the speed that got it there. */
	static void carry(double along) {
		if (structure == null)
			return;
		RigidBodyHandle body = RigidBodyHandle.of(structure);
		body.teleport(new Vector3d(home).add(along, 0, 0), facing);
		// Set the speed outright, so gravity never builds up and the sound engine is told how fast it really goes
		Vector3d speed = new Vector3d((along - carried) / SECONDS_A_TICK, 0, 0).sub(body.getLinearVelocity(new Vector3d()));
		body.addLinearAndAngularVelocity(speed, body.getAngularVelocity(new Vector3d())
			.negate());
		carried = along;
	}
}
