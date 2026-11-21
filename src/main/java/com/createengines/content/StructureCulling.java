package com.createengines.content;

import org.joml.FrustumIntersection;

import com.createengines.compat.sable.SableCompat;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Whether an engine part is in view, for the visuals that are posed on the CPU only while they can be seen.
 * <p>
 * Flywheel tests the part's own position against the camera's frustum. A part on a moving structure (Sable, so Create
 * Aeronautics) is kept in the structure's plot, millions of blocks from where it is drawn, so that test said "out of view"
 * nearly every frame and the pistons, rods and cams froze while Create's shaft, turned in a shader, kept turning. On a
 * structure the part is tested where it really is.
 */
public final class StructureCulling {

	/** Further than half a block's diagonal: a structure turns, and the part is posed a little ahead of the test. */
	private static final float RADIUS = 1.2f;

	private StructureCulling() {
	}

	/**
	 * @param plotVisible what Flywheel's own test says, which is right everywhere but on a moving structure
	 * @param pos the block entity's position
	 * @param visualPos that position relative to Flywheel's render origin, which is what the frustum is in
	 */
	public static boolean visible(Level level, BlockPos pos, BlockPos visualPos, FrustumIntersection frustum, boolean plotVisible) {
		if (level == null || !SableCompat.onStructure(level, pos))
			return plotVisible;
		Vec3 world = SableCompat.worldPosition(level, pos);
		return frustum.testSphere((float) (world.x - (pos.getX() - visualPos.getX())), (float) (world.y - (pos.getY() - visualPos.getY())),
			(float) (world.z - (pos.getZ() - visualPos.getZ())), RADIUS);
	}
}
