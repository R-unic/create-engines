package com.createengines.content;

import org.slf4j.Logger;
import org.slf4j.helpers.MessageFormatter;

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

	/** Set once a frame by the client, which is the only code that may name the camera. */
	private static volatile Vec3 cameraPosition = Vec3.ZERO;
	private static volatile org.joml.Vector3f cameraLook = new org.joml.Vector3f(0, 0, 1);
	private static BlockPos followed;
	private static long lastLog;
	private static boolean reported;
	private static int frames;
	private static int skipped;
	private static int posed;
	private static int changed;

	private AnimationDiagnostic() {
	}

	public static void camera(Vec3 position, org.joml.Vector3f look) {
		cameraPosition = position;
		cameraLook = look;
	}

	/** One frame of the cylinder at {@code pos}: whether it was drawn, and the figures behind that. Does nothing unless switched on. */
	public static void frame(Level level, BlockPos pos, BlockPos visualPos, boolean plotVisible, boolean visible,
							 float renderTime, double crankSpeed, double crankDegrees, Object pose, Object lastPose) {
		if (!ENABLED)
			return;
		String report = record(System.nanoTime(), level, pos, visualPos, plotVisible, visible, renderTime, crankSpeed, crankDegrees, pose,
			lastPose, cameraPosition, cameraLook);
		if (report != null)
			LOGGER.info(report);
	}

	/**
	 * Counts one frame, and once {@link #PERIOD_NANOS} has passed since the last report returns the report line (and starts counting
	 * again); null in between, and for any cylinder but the one followed. The clock and the camera are arguments so that a test can
	 * drive it without the diagnostic being switched on.
	 */
	public static synchronized String record(long now, Level level, BlockPos pos, BlockPos visualPos, boolean plotVisible, boolean visible,
											 float renderTime, double crankSpeed, double crankDegrees, Object pose, Object lastPose, Vec3 camera,
											 org.joml.Vector3f look) {
		if (followed == null)
			followed = pos.immutable();
		if (!followed.equals(pos))
			return null;
		frames++;
		if (!visible)
			skipped++;
		else {
			posed++;
			if (!pose.equals(lastPose))
				changed++;
		}
		if (reported && now - lastLog < PERIOD_NANOS)
			return null;
		reported = true;
		lastLog = now;
		boolean onStructure = SableCompat.onStructure(level, pos);
		Vec3 world = SableCompat.worldPosition(level, pos);
		String report = MessageFormatter.arrayFormat("[animation diagnostic] cylinder {} frames={} skippedOutOfView={} posed={} poseChanged={} onStructure={} "
			+ "flywheelVisible={} structureVisible={} visualPos={} world=({}, {}, {}) renderTime={} crankSpeed={} crankDegrees={} camera=({}, {}, {}) "
			+ "distance={} offAxisDegrees={}", new Object[] {pos, frames, skipped, posed, changed, onStructure, plotVisible, visible, visualPos,
				round(world.x), round(world.y), round(world.z), renderTime, crankSpeed, round(crankDegrees), round(camera.x), round(camera.y),
				round(camera.z), round(camera.distanceTo(world)), round(offAxis(look, camera, world))})
			.getMessage();
		frames = 0;
		skipped = 0;
		posed = 0;
		changed = 0;
		return report;
	}

	/** Forgets which cylinder is followed and what was counted, for tests. */
	public static synchronized void reset() {
		followed = null;
		reported = false;
		frames = 0;
		skipped = 0;
		posed = 0;
		changed = 0;
	}

	/** The angle between where the camera looks and the direction of the part from it, in degrees. */
	static double offAxis(org.joml.Vector3f look, Vec3 camera, Vec3 world) {
		Vec3 to = world.subtract(camera).normalize();
		return Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, to.x * look.x + to.y * look.y + to.z * look.z))));
	}

	private static double round(double v) {
		return Math.round(v * 100) / 100.0;
	}
}
