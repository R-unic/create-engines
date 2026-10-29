package com.createengines.engine;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;

/**
 * Angle conventions shared by the simulation (server) and the visuals (client), so that what the topology computes
 * and what the renderer draws agree.
 * <p>
 * Rotating parts are modelled along +Y with their offset feature (crank pin, cam nose) pointing +Z. Placed on an
 * axis, the model is turned so +Y lies along the axis' positive direction, then spun about that axis by the part's
 * angle (right-handed, as Create spins shafts).
 */
public final class CrankGeometry {

	/** Crank throw radius and connecting rod length, in blocks. Match the crank_throw and rod models. */
	public static final float THROW_RADIUS = 4 / 16f;
	public static final float ROD_LENGTH = 14 / 16f;

	private CrankGeometry() {
	}

	public static Vector3f axisVector(Axis axis) {
		return new Vector3f(Direction.get(Direction.AxisDirection.POSITIVE, axis).step());
	}

	/** Rotation taking a part modelled along +Y onto {@code axis}. */
	public static Quaternionf modelToAxis(Axis axis) {
		return new Quaternionf().rotationTo(new Vector3f(0, 1, 0), axisVector(axis));
	}

	/** Spin of {@code degrees} about the positive direction of {@code axis}. */
	public static Quaternionf spin(Axis axis, float degrees) {
		Vector3f a = axisVector(axis);
		return new Quaternionf().rotationAxis((float) Math.toRadians(degrees), a.x, a.y, a.z);
	}

	/** Direction the offset feature points when the part's angle is zero. */
	public static Vector3f restDirection(Axis axis) {
		return modelToAxis(axis).transform(new Vector3f(0, 0, 1));
	}

	/** Direction the offset feature points at the given part angle. */
	public static Vector3f featureDirection(Axis axis, float degrees) {
		return spin(axis, degrees).transform(restDirection(axis));
	}

	/** Signed angle, in degrees, from {@code from} to {@code to} about the positive direction of {@code axis}. */
	public static double signedAngle(Axis axis, Vector3f from, Vector3f to) {
		Vector3f a = axisVector(axis);
		Vector3f cross = new Vector3f(from).cross(to);
		return Math.toDegrees(Math.atan2(cross.dot(a), from.dot(to)));
	}

	/**
	 * Crank angle (the rotation of the crankshaft itself, before throw offsets) at which a piston in a cylinder bored
	 * along {@code bore} is at top dead centre on a throw set to {@code throwAngle}.
	 */
	public static double topDeadCentreAngle(Axis axis, double throwAngle, Direction bore) {
		Vector3f boreVector = new Vector3f(bore.step());
		return signedAngle(axis, restDirection(axis), boreVector) - throwAngle;
	}

	/** Create's own shaft angle convention: degrees turned at a given render time. */
	public static float renderAngle(float renderTime, float speed) {
		return (renderTime * speed * 3f / 10) % 360;
	}
}
