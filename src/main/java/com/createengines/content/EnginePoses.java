package com.createengines.content;

import org.jetbrains.annotations.Nullable;
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

	public static float crankAngle(CrankshaftBlockEntity crank, float renderTime) {
		return (float) ((crank.rotation(renderTime) + crank.throwAngle()) % 360);
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
		float angle = (float) ((housing.rotation(renderTime) + housing.throwAngle()) % (3 * 360));
		Vector3f pin = CrankGeometry.featureDirection(axis, angle)
			.mul(ROTOR_ECCENTRICITY);
		return new Matrix4f().translate(0.5f, 0.5f, 0.5f)
			.translate(pin)
			.rotate(CrankGeometry.spin(axis, angle / 3))
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

	private static Matrix4f stem(Vector3f normal, Vector3f camAxis, float offset, float lift) {
		Vector3f at = new Vector3f(0.5f).add(new Vector3f(normal).mul(0.5f - lift * MAX_LIFT))
			.add(new Vector3f(camAxis).mul(offset));
		return new Matrix4f().translate(at)
			.rotate(new Quaternionf().rotationTo(UP, normal))
			.translate(-0.5f, 0, -0.5f);
	}
}
