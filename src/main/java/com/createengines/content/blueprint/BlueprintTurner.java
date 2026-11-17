package com.createengines.content.blueprint;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import com.createengines.content.crankshaft.CrankshaftBlockEntity;
import com.createengines.engine.CrankGeometry;
import com.createengines.engine.blueprint.Blueprint;

import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Turns a blueprint a quarter, a half or three quarters of a turn about the vertical, so an engine can be built, or an
 * engine built that way round recognised, facing any way. Everything that lives in the blueprint turns with it: each block's
 * place, each block state (an axis, a facing, a pipe's connections, through the block's own rotate and mirror), and the
 * throw angles of the cranks.
 * <p>
 * A throw angle is measured about its crankshaft's positive axis, from a rest direction that depends on the axis. After a turn
 * the axis may be a different one and the angle has to be worked out again, so that every crank pin still points the way it
 * did relative to its cylinder: the pin's direction is turned with the engine and read back against the new axis. Cam lobe
 * angles are timing values in the engine's own frame (the scanner times a lobe from its dial, not from where it points), so
 * they are kept as they are.
 * <p>
 * An engine always turns the same way round about its crank's positive axis. A turn that carries the crank's positive
 * direction onto the negative direction of the new axis would make the copy run the other way, which is a different
 * engine, so the copy is also mirrored across a plane through the crank. Looks aside, the mirrored engine is the same
 * engine.
 */
public final class BlueprintTurner {

	private BlueprintTurner() {
	}

	/** The four turns, quarter turns clockwise seen from above: none, 90, 180 and 270. */
	public static final List<Rotation> TURNS = List.of(Rotation.NONE, Rotation.CLOCKWISE_90, Rotation.CLOCKWISE_180,
		Rotation.COUNTERCLOCKWISE_90);

	/** Quarter turns that take a blueprint, as recorded, to a build made by a player looking {@code facing}: north is as recorded. */
	public static Rotation forFacing(Direction facing) {
		return switch (facing) {
			case EAST -> Rotation.CLOCKWISE_90;
			case SOUTH -> Rotation.CLOCKWISE_180;
			case WEST -> Rotation.COUNTERCLOCKWISE_90;
			default -> Rotation.NONE;
		};
	}

	/**
	 * The mirror this turn needs for the blueprint's engine (the one that keeps a crank turning the same way round), or
	 * {@link Mirror#NONE}. Decided from the primary crankshaft, which the blueprint holds at its origin.
	 */
	public static Mirror mirrorFor(Blueprint blueprint, Rotation rotation) {
		Axis axis = crankAxis(blueprint);
		if (axis == null || axis == Axis.Y)
			return Mirror.NONE;
		Direction positive = Direction.get(Direction.AxisDirection.POSITIVE, axis);
		boolean flipped = rotation.rotate(positive)
			.getAxisDirection() == Direction.AxisDirection.NEGATIVE;
		if (!flipped)
			return Mirror.NONE;
		// A plane that holds the crank axis: along x the plane is y-x (flip z), along z it is y-z (flip x)
		return axis == Axis.X ? Mirror.LEFT_RIGHT : Mirror.FRONT_BACK;
	}

	private static @Nullable Axis crankAxis(Blueprint blueprint) {
		for (Blueprint.Part part : blueprint.parts())
			if (part.x() == 0 && part.y() == 0 && part.z() == 0) {
				BlockState state = BlueprintBuilder.stateOf(part);
				return state == null ? null : state.getOptionalValue(BlockStateProperties.AXIS)
					.orElse(null);
			}
		return null;
	}

	/** The blueprint turned, mirrored when the turn needs it. Null if a part cannot be turned (its block or state is unknown). */
	public static @Nullable Blueprint turn(Blueprint blueprint, Rotation rotation) {
		return turn(blueprint, rotation, mirrorFor(blueprint, rotation));
	}

	/** The blueprint mirrored then turned. Null if a part cannot be turned. */
	public static @Nullable Blueprint turn(Blueprint blueprint, Rotation rotation, Mirror mirror) {
		if (rotation == Rotation.NONE && mirror == Mirror.NONE)
			return blueprint;
		List<Blueprint.Part> parts = new ArrayList<>(blueprint.parts()
			.size());
		for (Blueprint.Part part : blueprint.parts()) {
			BlockState state = BlueprintBuilder.stateOf(part);
			if (state == null)
				return null;
			BlockState turned = state.mirror(mirror)
				.rotate(rotation);
			int[] at = place(part.x(), part.z(), rotation, mirror);
			Map<String, Integer> dials = part.dials();
			// A rotor housing's mark is the rest direction of its own axis, so its angle is the same whichever way it faces;
			// only a crank throw, whose pin points at a cylinder, has to be worked out again
			if (part.block()
				.endsWith(":crank_throw") && part.dials()
					.containsKey("throw_angle")) {
				Integer angle = throwAngle(part, state, turned, rotation, mirror);
				if (angle == null)
					return null;
				dials = new TreeMap<>(dials);
				dials.put("throw_angle", angle);
			}
			parts.add(new Blueprint.Part(at[0], part.y(), at[1], part.block(), BlueprintRecorder.changedProperties(turned), dials));
		}
		return new Blueprint(blueprint.name(), blueprint.builder(), parts, blueprint.figures());
	}

	/** Where a block at {@code x, z} from the origin goes: mirrored first, then turned. */
	public static int[] place(int x, int z, Rotation rotation, Mirror mirror) {
		if (mirror == Mirror.LEFT_RIGHT)
			z = -z;
		else if (mirror == Mirror.FRONT_BACK)
			x = -x;
		return switch (rotation) {
			case CLOCKWISE_90 -> new int[] {-z, x};
			case CLOCKWISE_180 -> new int[] {-x, -z};
			case COUNTERCLOCKWISE_90 -> new int[] {z, -x};
			default -> new int[] {x, z};
		};
	}

	/**
	 * The throw angle dial after the turn: the crank pin's direction is carried through the same mirror and turn as the block
	 * and read against the new axis. Null if the result is not a whole number of steps (it is always, for quarter turns).
	 */
	private static @Nullable Integer throwAngle(Blueprint.Part part, BlockState before, BlockState after, Rotation rotation, Mirror mirror) {
		Axis from = before.getOptionalValue(BlockStateProperties.AXIS)
			.orElse(null);
		Axis to = after.getOptionalValue(BlockStateProperties.AXIS)
			.orElse(null);
		if (from == null || to == null)
			return null;
		double degrees = part.dials()
			.get("throw_angle") * CrankshaftBlockEntity.THROW_ANGLE_STEP;
		Vector3f pin = CrankGeometry.featureDirection(from, (float) degrees);
		if (mirror == Mirror.LEFT_RIGHT)
			pin.z = -pin.z;
		else if (mirror == Mirror.FRONT_BACK)
			pin.x = -pin.x;
		pin = switch (rotation) {
			case CLOCKWISE_90 -> new Vector3f(-pin.z, pin.y, pin.x);
			case CLOCKWISE_180 -> new Vector3f(-pin.x, pin.y, -pin.z);
			case COUNTERCLOCKWISE_90 -> new Vector3f(pin.z, pin.y, -pin.x);
			default -> pin;
		};
		double angle = CrankGeometry.signedAngle(to, CrankGeometry.restDirection(to), pin);
		double steps = ((angle % 360) + 360) % 360 / CrankshaftBlockEntity.THROW_ANGLE_STEP;
		long whole = Math.round(steps);
		if (Math.abs(steps - whole) > 1e-3)
			return null;
		return (int) (whole % (360 / CrankshaftBlockEntity.THROW_ANGLE_STEP));
	}
}
