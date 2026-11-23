package com.createengines.content;

import org.slf4j.Logger;

import com.createengines.compat.sable.SableCompat;
import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A log for finding out why a part is not animating, off unless the game is started with
 * {@code -Dcreateengines.animationDiagnostic=true}. It follows one cylinder (the first to draw) and, once a second, says how many
 * frames its visual was asked to update, how many it skipped as out of view, and what the visibility tests and the pose said.
 * Client only; nothing here changes what is drawn.
 */
public final class AnimationDiagnostic {

	public static final boolean ENABLED = Boolean.getBoolean("createengines.animationDiagnostic");

	private static final Logger LOGGER = LogUtils.getLogger();
	private static final long PERIOD_NANOS = 1_000_000_000L;

	private static BlockPos followed;
	private static long lastLog;
	private static int frames;
	private static int skipped;
	private static int posed;
	private static int changed;

	private AnimationDiagnostic() {
	}

	/** One frame of the cylinder at {@code pos}: whether it was drawn, and the figures behind that. */
	public static void frame(Level level, BlockPos pos, BlockPos visualPos, boolean plotVisible, boolean visible,
							 float renderTime, double crankSpeed, double crankDegrees, Object pose, Object lastPose) {
		if (!ENABLED)
			return;
		synchronized (AnimationDiagnostic.class) {
			if (followed == null)
				followed = pos.immutable();
			if (!followed.equals(pos))
				return;
			frames++;
			if (!visible)
				skipped++;
			else {
				posed++;
				if (!pose.equals(lastPose))
					changed++;
			}
			long now = System.nanoTime();
			if (now - lastLog < PERIOD_NANOS)
				return;
			lastLog = now;
			boolean onStructure = SableCompat.onStructure(level, pos);
			Vec3 world = SableCompat.worldPosition(level, pos);
			LOGGER.info("[animation diagnostic] cylinder {} frames={} skippedOutOfView={} posed={} poseChanged={} onStructure={} "
					+ "flywheelVisible={} structureVisible={} visualPos={} world=({}, {}, {}) renderTime={} crankSpeed={} crankDegrees={}",
				pos, frames, skipped, posed, changed, onStructure, plotVisible, visible, visualPos, round(world.x), round(world.y),
				round(world.z), renderTime, crankSpeed, round(crankDegrees));
			frames = 0;
			skipped = 0;
			posed = 0;
			changed = 0;
		}
	}

	private static double round(double v) {
		return Math.round(v * 100) / 100.0;
	}
}
