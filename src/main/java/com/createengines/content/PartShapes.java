package com.createengines.content;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The outline and collision shapes of parts whose model is not a plain box, built from {@link PartBoxes}: the same
 * boxes as the model, so the box you aim at is the part you see (a wide base plate and a thin stack, not one cube
 * around both). {@code HitboxFitTest} holds the boxes against the models.
 */
public final class PartShapes {

	private PartShapes() {
	}

	/** A box in pixels, like {@code Block.box}, without needing the block class. */
	public static VoxelShape box(double x1, double y1, double z1, double x2, double y2, double z2) {
		return Shapes.box(x1 / 16, y1 / 16, z1 / 16, x2 / 16, y2 / 16, z2 / 16);
	}

	/** Boxes given as {@code {x1, y1, z1, x2, y2, z2}} in pixels, joined into one shape. */
	public static VoxelShape union(double[][] boxes) {
		VoxelShape shape = Shapes.empty();
		for (double[] b : boxes)
			shape = Shapes.or(shape, box(b[0], b[1], b[2], b[3], b[4], b[5]));
		return shape.optimize();
	}

	/** One shape per facing, indexed by {@link Direction#ordinal()}, from boxes drawn facing up. */
	private static VoxelShape[] directional(double[][] up) {
		VoxelShape[] shapes = new VoxelShape[6];
		for (Direction facing : Direction.values())
			shapes[facing.ordinal()] = union(PartBoxes.directional(facing.ordinal(), up));
		return shapes;
	}

	/** One shape per axis, indexed by {@link Direction.Axis#ordinal()}, from boxes drawn along Y. */
	private static VoxelShape[] axial(double[][] y) {
		VoxelShape[] shapes = new VoxelShape[3];
		for (int axis = 0; axis < 3; axis++)
			shapes[axis] = union(PartBoxes.axial(axis, y));
		return shapes;
	}

	public static final VoxelShape[] ALTERNATOR = axial(PartBoxes.ALTERNATOR);
	public static final VoxelShape BLACK_BOX = union(PartBoxes.BLACK_BOX);
	public static final VoxelShape GOVERNOR = union(PartBoxes.GOVERNOR);
	public static final VoxelShape FLARE_STACK = union(PartBoxes.FLARE_STACK);
	public static final VoxelShape[] STARTER = directional(PartBoxes.STARTER);
	public static final VoxelShape[] AIR_INTAKE = directional(PartBoxes.AIR_INTAKE);
	public static final VoxelShape[] EXHAUST_OUTLET = directional(PartBoxes.EXHAUST_OUTLET);
	public static final VoxelShape[] TURBOCHARGER = directional(PartBoxes.TURBOCHARGER);
	public static final VoxelShape[] CAM_LOBE = axial(PartBoxes.CAM_LOBE);
	public static final VoxelShape[] CRANK_THROW = axial(PartBoxes.CRANK_THROW);
	public static final VoxelShape THROTTLE_BODY = union(PartBoxes.THROTTLE_BODY);
	public static final VoxelShape MAF_BODY = union(PartBoxes.MAF_BODY);
	public static final VoxelShape EXCHANGER_BODY = union(PartBoxes.EXCHANGER_BODY);
}
