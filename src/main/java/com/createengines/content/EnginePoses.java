package com.createengines.content;

import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.createengines.content.camshaft.CamLobeBlock;
import com.createengines.content.camshaft.CamshaftBlockEntity;
import com.createengines.content.crankshaft.CrankshaftBlockEntity;
import com.createengines.content.cylinder.CylinderBlock;
import com.createengines.content.cylinder.CylinderBlockEntity;
import com.createengines.content.head.CylinderHeadBlock;
import com.createengines.content.head.CylinderHeadBlockEntity;
import com.createengines.content.rotor.RotorHousingBlockEntity;
import com.createengines.content.effects.EngineRock;
import com.createengines.engine.CrankGeometry;
import com.createengines.engine.sim.CamProfile;
import com.createengines.engine.sim.CylinderModel;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Where each moving engine part is drawn at a given moment, as a transform from its block's corner. Shared by the
 * Flywheel visuals and the fallback block entity renderers (used in Ponder and when Flywheel is off), so both draw
 * the same mechanism.
 */
public final class EnginePoses {

	private static final Vector3f UP = new Vector3f(0, 1, 0);
	private static final float STEM_SPACING = 3 / 16f;
	private static final float MAX_LIFT = 1.5f / 16;
	/** How far a rotor's centre is from the shaft, in blocks. Match the eccentric model's journal. */
	private static final float ROTOR_ECCENTRICITY = 1.5f / 16;

	public record CylinderPose(Matrix4f piston, Matrix4f rod, boolean showPiston, boolean showRod) {
	}

	public record ValvePose(Matrix4f intake, Matrix4f exhaust) {
	}

	private EnginePoses() {
	}

	/** A part modelled along +Y, turned onto {@code axis} and spun by {@code degrees} about it. */
	public static Matrix4f rotatingPart(Axis axis, float degrees) {
		return new Matrix4f().translate(0.5f, 0.5f, 0.5f)
			.rotate(CrankGeometry.spin(axis, degrees))
			.rotate(CrankGeometry.modelToAxis(axis))
			.translate(-0.5f, -0.5f, -0.5f);
	}

	/**
	 * How far this crank block's shaft is drawn as turned, in degrees and not wrapped: the engine's turning, the throw
	 * angle, and the phase Create gives the shafts and cogs on this block's line, so that one joined to the crank (the
	 * crank's ends are shaft-shaped) is drawn in step with it and not at a fixed angle to it.
	 */
	public static double drawnShaft(CrankshaftBlockEntity crank, float renderTime) {
		Axis axis = crank.getBlockState()
			.getValue(BlockStateProperties.AXIS);
		return crank.rotation(renderTime) + crank.throwAngle() + CrankGeometry.createShaftPhase(axis, crank.getBlockPos());
	}

	public static float crankAngle(CrankshaftBlockEntity crank, float renderTime) {
		return (float) (drawnShaft(crank, renderTime) % 360);
	}

	public static float camAngle(CamshaftBlockEntity cam, float renderTime) {
		return (float) ((cam.rotation(renderTime) + cam.lobeAngle()) % 360);
	}

	/**
	 * Angles of a lobe set's three lobes about the cam's axis, in degrees: each is turned so that its nose is on the
	 * far side of the shaft from the follower it works (the cylinder's head, or its block through pushrods) exactly when
	 * that follower's lift peaks in the simulation's cycle, and so that a larger lobe angle means the nose gets there later. A cam turning the other
	 * way reaches every mark in reverse order, so the offsets between lobes mirror with it.
	 */
	public record LobeAngles(float intake, float exhaust, float injector) {
	}

	public static LobeAngles lobeAngles(CamshaftBlockEntity cam, float renderTime) {
		Axis axis = cam.getBlockState()
			.getValue(BlockStateProperties.AXIS);
		double turned = cam.rotation(renderTime);
		// The simulation reads the cam as a distance turned, whichever way it turns
		double sense = turned < 0 ? -1 : 1;
		double travelled = Math.abs(turned % 360);

		double toward = 0;
		double shift = 0;
		Level level = cam.getLevel();
		if (level != null) {
			Direction follower = null;
			for (Direction face : Direction.values()) {
				if (face.getAxis() == axis)
					continue;
				BlockPos at = cam.getBlockPos()
					.relative(face);
				BlockState state = level.getBlockState(at);
				BlockPos cylinderPos;
				if (state.getBlock() instanceof CylinderHeadBlock)
					cylinderPos = at.relative(state.getValue(CylinderHeadBlock.FACING)
						.getOpposite());
				else if (state.getBlock() instanceof CylinderBlock)
					cylinderPos = at;
				else
					continue;
				if (!(level.getBlockEntity(cylinderPos) instanceof CylinderBlockEntity cylinder)
					|| cylinder.status() == null || !cam.getBlockPos()
						.equals(cylinder.status()
							.lobe()))
					continue;
				// One lobe shared by cylinders is drawn for the one it times without a shift
				double lobeShift = cylinder.status()
					.lobeShift();
				if (follower == null || Math.abs(lobeShift) < Math.abs(shift)) {
					follower = face;
					shift = lobeShift;
				}
			}
			if (follower != null)
				toward = CrankGeometry.signedAngle(axis, CrankGeometry.restDirection(axis), new Vector3f(follower.step()));
		}

		double base = travelled - cam.lobeAngle() - shift;
		CamProfile profile = CamProfile.BASIC;
		return new LobeAngles(lobeAngle(toward, sense, base, liftPeak(profile.intakeOpen(), profile.intakeClose())),
			lobeAngle(toward, sense, base, liftPeak(profile.exhaustOpen(), profile.exhaustClose())),
			lobeAngle(toward, sense, base, profile.injection()));
	}

	/** Cycle angle at the middle of a valve's lift. */
	private static double liftPeak(double open, double close) {
		double duration = ((close - open) % 720 + 720) % 720;
		return open + duration / 2;
	}

	/** The cam is at cycle {@code peak} when it has turned {@code (peak - 360) / 2} past the lobe angle. */
	private static float lobeAngle(double toward, double sense, double base, double peak) {
		// A half turn on: seen in game, the nose was away from the follower while its valve was open
		return (float) (toward + 180 + sense * (base - (peak - 360) / 2)) % 360;
	}

	/**
	 * A Wankel rotor: its centre rides the eccentric, and it turns on it at a third of the shaft's speed (the housing's
	 * gears hold it to that). Drawn with a larger eccentricity than a real one has, so the orbit shows.
	 */
	public static Matrix4f rotor(RotorHousingBlockEntity housing, float renderTime) {
		Axis axis = housing.getBlockState()
			.getValue(BlockStateProperties.AXIS);
		// The rotor is three turns of the shaft round, not one, so its angle must not wrap with the shaft's: a rotor a third
		// of the way round from where it was is a different picture, whatever the three corners look like
		float angle = (float) (drawnShaft(housing, renderTime) % (3 * 360));
		Vector3f pin = CrankGeometry.featureDirection(axis, angle)
			.mul(ROTOR_ECCENTRICITY);
		return new Matrix4f().translate(0.5f, 0.5f, 0.5f)
			.translate(pin)
			.rotate(CrankGeometry.spin(axis, angle / 3))
			.rotate(CrankGeometry.modelToAxis(axis))
			.translate(-0.5f, -0.5f, -0.5f);
	}

	/**
	 * A model made for a block that points up, laid on a block that points along {@code facing}: turned about the block's centre. A wreck is
	 * modelled upright on its head or cylinder, and this stands it on whichever way the block is built.
	 */
	public static Matrix4f facingFrame(Direction facing) {
		return new Matrix4f().translate(0.5f, 0.5f, 0.5f)
			.rotate(new Quaternionf().rotationTo(UP, new Vector3f(facing.step())))
			.translate(-0.5f, -0.5f, -0.5f);
	}

	/** A model made for a block whose shaft stands upright, laid on one whose shaft lies along {@code axis}. It does not turn. */
	public static Matrix4f axisFrame(Axis axis) {
		return new Matrix4f().translate(0.5f, 0.5f, 0.5f)
			.rotate(CrankGeometry.modelToAxis(axis))
			.translate(-0.5f, -0.5f, -0.5f);
	}

	/** Piston and connecting rod, from the crank throw's angle by slider-crank geometry. */
	public static CylinderPose cylinder(CylinderBlockEntity cylinder, float renderTime) {
		BlockState state = cylinder.getBlockState();
		Vector3f bore = new Vector3f(state.getValue(CylinderBlock.FACING)
			.step());
		CrankshaftBlockEntity crank = cylinder.crankThrow();
		// Only the connecting rod ties the piston to the crank: without one it sits where it was dropped
		boolean connected = crank != null && state.getValue(CylinderBlock.HAS_ROD);

		Vector3f pin;
		if (connected) {
			Axis axis = crank.getBlockState()
				.getValue(BlockStateProperties.AXIS);
			pin = CrankGeometry.featureDirection(axis, crankAngle(crank, renderTime))
				.mul(CrankGeometry.THROW_RADIUS);
		} else
			pin = new Vector3f(bore).mul(CrankGeometry.THROW_RADIUS);

		float along = pin.dot(bore);
		Vector3f across = new Vector3f(pin).sub(new Vector3f(bore).mul(along));
		float rodLength = CrankGeometry.ROD_LENGTH;
		float wristDistance = along + (float) Math.sqrt(rodLength * rodLength - across.lengthSquared());

		Vector3f throwCentre = new Vector3f(0.5f).sub(bore);
		Vector3f pinPos = new Vector3f(throwCentre).add(pin);
		Vector3f wrist = new Vector3f(throwCentre).add(new Vector3f(bore).mul(wristDistance));
		Vector3f rodDirection = new Vector3f(wrist).sub(pinPos)
			.normalize();

		Matrix4f piston = new Matrix4f().translate(wrist)
			.rotate(new Quaternionf().rotationTo(UP, bore))
			.translate(-0.5f, 0, -0.5f);
		Matrix4f rod = new Matrix4f().translate(pinPos)
			.rotate(new Quaternionf().rotationTo(UP, rodDirection))
			.translate(-0.5f, 0, -0.5f);
		// A rough engine's pistons slap about across the bore, by how rough its rating says it is
		if (connected && crank.engineSummary() != null) {
			float[] stray = EngineRock.stray(crank.engineSummary()
				.roughness(), crankAngle(crank, renderTime), (int) cylinder.getBlockPos()
					.asLong());
			if (stray[0] != 0 || stray[1] != 0) {
				Vector3f first = new Vector3f(bore).cross(Math.abs(bore.y) > 0.5f ? new Vector3f(1, 0, 0) : UP)
					.normalize();
				Vector3f second = new Vector3f(bore).cross(first);
				Vector3f aside = first.mul(stray[0])
					.add(second.mul(stray[1]));
				piston = new Matrix4f().translate(aside)
					.mul(piston);
				rod = new Matrix4f().translate(aside)
					.mul(rod);
			}
		}
		return new CylinderPose(piston, rod, state.getValue(CylinderBlock.HAS_PISTON), connected);
	}

	/** Valve stems on the face touching a cam lobe, pushed in by the same cam profile the simulation uses. */
	public static @Nullable ValvePose valves(CylinderHeadBlockEntity head, float renderTime) {
		Level level = head.getLevel();
		if (level == null)
			return null;
		Direction cylinderSide = head.getBlockState()
			.getValue(CylinderHeadBlock.FACING)
			.getOpposite();
		for (Direction face : Direction.values()) {
			if (face == cylinderSide)
				continue;
			BlockState neighbour = level.getBlockState(head.getBlockPos()
				.relative(face));
			if (!(neighbour.getBlock() instanceof CamLobeBlock)
				|| neighbour.getValue(BlockStateProperties.AXIS) == face.getAxis())
				continue;
			if (!(level.getBlockEntity(head.getBlockPos()
				.relative(face)) instanceof CamshaftBlockEntity lobe))
				continue;
			float travelled = (float) Math.abs(lobe.rotation(renderTime) % 360);
			// A lobe shared with other cylinders times this one at an offset from the angle set on it
			double shift = level.getBlockEntity(head.getBlockPos()
				.relative(cylinderSide)) instanceof CylinderBlockEntity cylinder && cylinder.status() != null
					? cylinder.status()
						.lobeShift()
					: 0;
			double cycle = CylinderModel.camCycleAngle(travelled, lobe.lobeAngle() + shift);
			Vector3f camAxis = CrankGeometry.axisVector(neighbour.getValue(BlockStateProperties.AXIS));
			Vector3f normal = new Vector3f(face.step());
			return new ValvePose(stem(normal, camAxis, STEM_SPACING, (float) CamProfile.BASIC.intakeLift(cycle)),
				stem(normal, camAxis, -STEM_SPACING, (float) CamProfile.BASIC.exhaustLift(cycle)));
		}
		return null;
	}

	/** The pushrods and rockers of a cylinder whose cam works its valves from beside its block, each pair posed for the lift. */
	public record PushrodPose(Matrix4f intakeRod, Matrix4f intakeRocker, Matrix4f exhaustRod, Matrix4f exhaustRocker) {
	}

	/**
	 * A pushrod from each lobe of a cam beside the cylinder block, up past the block to a rocker on the head, rising and
	 * falling with the valve it works: by the same cam profile and timing the simulation and {@link #valves} use. Null
	 * when the cylinder's cam is not beside its block (a cam at the head works the valves directly), when the cam runs
	 * along the bore, or when something stands in the space beside the head that the rods and rockers need.
	 */
	public static @Nullable PushrodPose pushrods(CylinderBlockEntity cylinder, float renderTime) {
		Level level = cylinder.getLevel();
		var status = cylinder.status();
		if (level == null || status == null || status.lobe() == null)
			return null;
		Direction bore = cylinder.getBlockState()
			.getValue(CylinderBlock.FACING);
		BlockPos lobePos = status.lobe();
		BlockPos away = lobePos.subtract(cylinder.getBlockPos());
		Direction side = Direction.fromDelta(away.getX(), away.getY(), away.getZ());
		if (side == null || side.getAxis() == bore.getAxis())
			return null;
		BlockState lobeState = level.getBlockState(lobePos);
		if (!(lobeState.getBlock() instanceof CamLobeBlock) || !(level.getBlockEntity(lobePos) instanceof CamshaftBlockEntity lobe))
			return null;
		Axis camAxis = lobeState.getValue(BlockStateProperties.AXIS);
		if (camAxis == bore.getAxis() || !level.getBlockState(cylinder.getBlockPos()
			.relative(bore)
			.relative(side))
			.isAir())
			return null;

		float travelled = (float) Math.abs(lobe.rotation(renderTime) % 360);
		double cycle = CylinderModel.camCycleAngle(travelled, lobe.lobeAngle() + status.lobeShift());

		// The models are drawn with +Y along the bore and +Z towards the cam, so +X is bore cross side
		Vector3f y = new Vector3f(bore.step());
		Vector3f z = new Vector3f(side.step());
		Vector3f x = new Vector3f(y).cross(z);
		int alignment = x.dot(CrankGeometry.axisVector(camAxis)) >= 0 ? 1 : -1;
		Matrix4f frame = new Matrix4f().translate(0.5f, 0.5f, 0.5f)
			.mul(new Matrix4f().set(new Matrix3f(x, y, z)))
			.translate(-0.5f, -0.5f, -0.5f);
		double intake = CamProfile.BASIC.intakeLift(cycle);
		double exhaust = CamProfile.BASIC.exhaustLift(cycle);
		return new PushrodPose(rod(frame, PushrodGeometry.INTAKE_AT, alignment, intake),
			rocker(frame, PushrodGeometry.INTAKE_AT, alignment, intake),
			rod(frame, PushrodGeometry.EXHAUST_AT, alignment, exhaust),
			rocker(frame, PushrodGeometry.EXHAUST_AT, alignment, exhaust));
	}

	private static Matrix4f rod(Matrix4f frame, double lobeAt, int alignment, double lift) {
		return new Matrix4f(frame).translate(PushrodGeometry.across(lobeAt, alignment), PushrodGeometry.rise(lift), 0);
	}

	/** The rocker sits on the head, a block along the bore, and tips about its pin inside the head (y 4, z 14 pixels). */
	private static Matrix4f rocker(Matrix4f frame, double lobeAt, int alignment, double lift) {
		return new Matrix4f(frame).translate(PushrodGeometry.across(lobeAt, alignment), 1, 0)
			.translate(0, 4 / 16f, 14 / 16f)
			.rotateX((float) Math.toRadians(PushrodGeometry.rockerAngle(lift)))
			.translate(0, -4 / 16f, -14 / 16f);
	}

	private static Matrix4f stem(Vector3f normal, Vector3f camAxis, float offset, float lift) {
		Vector3f at = new Vector3f(0.5f).add(new Vector3f(normal).mul(0.5f - lift * MAX_LIFT))
			.add(new Vector3f(camAxis).mul(offset));
		return new Matrix4f().translate(at)
			.rotate(new Quaternionf().rotationTo(UP, normal))
			.translate(-0.5f, 0, -0.5f);
	}
}
