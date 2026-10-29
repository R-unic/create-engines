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
import com.createengines.engine.CrankGeometry;
import com.createengines.engine.sim.CamProfile;
import com.createengines.engine.sim.CylinderModel;

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
		return CrankGeometry.renderAngle(renderTime, crank.getTheoreticalSpeed()) + (float) crank.throwAngle();
	}

	public static float camAngle(CamshaftBlockEntity cam, float renderTime) {
		return CrankGeometry.renderAngle(renderTime, cam.getTheoreticalSpeed()) + (float) cam.lobeAngle();
	}

	/** Piston and connecting rod, from the crank throw's angle by slider-crank geometry. */
	public static CylinderPose cylinder(CylinderBlockEntity cylinder, float renderTime) {
		BlockState state = cylinder.getBlockState();
		Vector3f bore = new Vector3f(state.getValue(CylinderBlock.FACING)
			.step());
		CrankshaftBlockEntity crank = cylinder.crankThrow();

		Vector3f pin;
		if (crank != null) {
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
		return new CylinderPose(piston, rod, state.getValue(CylinderBlock.HAS_PISTON),
			state.getValue(CylinderBlock.HAS_ROD) && crank != null);
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
			float travelled = Math.abs(CrankGeometry.renderAngle(renderTime, lobe.getTheoreticalSpeed()));
			double cycle = CylinderModel.camCycleAngle(travelled, lobe.lobeAngle());
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
