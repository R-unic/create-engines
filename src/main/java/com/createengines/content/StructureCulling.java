package com.createengines.content;

import org.joml.FrustumIntersection;

import com.createengines.compat.sable.SableCompat;

import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
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
	 * The block entity's visual is in an embedding when the part is on a structure, and the embedding's render origin is the plot, so
	 * {@code visualPos} is relative to the plot and says nothing about where the part is relative to the camera; the frustum is
	 * relative to the main render origin. Where the origin is not known (no Flywheel on this level) Flywheel's own answer stands.
	 *
	 * @param plotVisible what Flywheel's own test says, which is right everywhere but on a moving structure
	 * @param pos the block entity's position
	 */
	public static boolean visible(Level level, BlockPos pos, FrustumIntersection frustum, boolean plotVisible) {
		if (level == null || !SableCompat.onStructure(level, pos))
			return plotVisible;
		VisualizationManager manager = VisualizationManager.get(level);
		if (manager == null)
			return plotVisible;
		return visible(level, pos, manager.renderOrigin(), frustum, plotVisible);
	}

	/**
	 * As above, with the main render origin (the one the frustum is relative to) given. This is not the visual's own
	 * origin, which for a part on a structure is the plot.
	 */
	public static boolean visible(Level level, BlockPos pos, Vec3i renderOrigin, FrustumIntersection frustum, boolean plotVisible) {
		if (level == null || !SableCompat.onStructure(level, pos))
			return plotVisible;
		Vec3 world = SableCompat.worldPosition(level, pos);
		return frustum.testSphere((float) (world.x - renderOrigin.getX()), (float) (world.y - renderOrigin.getY()),
			(float) (world.z - renderOrigin.getZ()), RADIUS);
	}
}
