package com.createengines.content.refinery;

import com.createengines.engine.sim.ColumnShape;
import com.createengines.engine.sim.ColumnShape.Part;
import com.createengines.engine.sim.Heat;
import com.createengines.registry.CEBlocks;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Reads the world for {@link ColumnShape}: which block is which part, and how hot the base is. */
public final class RefiningColumns {

	private RefiningColumns() {
	}

	/** The part of a column at {@code pos}; blocks in unloaded chunks count as nothing, so a scan never loads one. */
	public static Part partAt(Level level, BlockPos pos) {
		if (!level.isLoaded(pos))
			return Part.OTHER;
		Block block = level.getBlockState(pos)
			.getBlock();
		if (block == CEBlocks.COLUMN_SECTION.get())
			return Part.SECTION;
		if (block instanceof ColumnPortBlock port)
			return port.part();
		if (block instanceof ColumnJointBlock)
			return Part.JOINT;
		return Part.OTHER;
	}

	public static ColumnShape.Result scan(Level level, BlockPos pos) {
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		ColumnShape.World world = new ColumnShape.World() {
			@Override
			public Part at(int x, int y, int z) {
				return partAt(level, cursor.set(x, y, z));
			}

			@Override
			public boolean clear(int x, int y, int z) {
				cursor.set(x, y, z);
				return level.isLoaded(cursor) && level.isInWorldBounds(cursor) && level.getBlockState(cursor)
					.canBeReplaced();
			}

			@Override
			public int jointLink(int x, int y, int z) {
				cursor.set(x, y, z);
				BlockState state = level.getBlockState(cursor);
				return state.getBlock() instanceof ColumnJointBlock ? ColumnJointBlock.link(state) : -1;
			}
		};
		return ColumnShape.scan(world, pos.getX(), pos.getY(), pos.getZ());
	}

	/**
	 * Fills the blocks a leaning column needs with joint pieces, and puts right any that were knocked out or turned.
	 * A block that is not clear is left alone (the next scan would not have formed the column if one were in the way).
	 */
	public static void placeJoints(Level level, ColumnShape.Result shape) {
		if (!shape.formed())
			return;
		for (ColumnShape.Joint joint : shape.joints()) {
			BlockPos at = new BlockPos(joint.x(), joint.y(), joint.z());
			if (!level.isLoaded(at))
				continue;
			BlockState want = ColumnJointBlock.stateFor(joint);
			BlockState have = level.getBlockState(at);
			if (have.equals(want))
				continue;
			if (have.getBlock() instanceof ColumnJointBlock || have.canBeReplaced())
				level.setBlock(at, want, 3);
		}
	}

	/** Takes away the joint pieces of a column that came apart. Only joints are removed, whatever else is there. */
	public static void removeJoints(Level level, ColumnShape.Result shape) {
		for (ColumnShape.Joint joint : shape.joints()) {
			BlockPos at = new BlockPos(joint.x(), joint.y(), joint.z());
			if (level.isLoaded(at) && level.getBlockState(at)
				.getBlock() instanceof ColumnJointBlock)
				level.removeBlock(at, false);
		}
	}

	/** The hottest source under the base's footprint. Blaze Burners and Exhaust Heat Exchangers both read as heat levels. */
	public static Heat heatUnder(Level level, ColumnShape.Result shape) {
		Heat best = Heat.NONE;
		for (int x = shape.x(); x < shape.x() + shape.width(); x++)
			for (int z = shape.z(); z < shape.z() + shape.width(); z++) {
				BlockPos below = new BlockPos(x, shape.y() - 1, z);
				if (!level.isLoaded(below))
					continue;
				Heat here = heatOf(BlazeBurnerBlock.getHeatLevelOf(level.getBlockState(below)));
				if (here.ordinal() > best.ordinal())
					best = here;
			}
		return best;
	}

	/** Lights or darkens the sight windows of a column body. */
	public static void showWork(Level level, ColumnShape.Result shape, boolean working) {
		if (!shape.formed())
			return;
		for (int layer = 1; layer <= shape.height(); layer++)
			for (int x = shape.layerX(layer); x < shape.layerX(layer) + shape.width(); x++)
				for (int z = shape.layerZ(layer); z < shape.layerZ(layer) + shape.width(); z++) {
					BlockPos at = new BlockPos(x, shape.y() + layer, z);
					if (!level.isLoaded(at))
						continue;
					BlockState state = level.getBlockState(at);
					if (state.getBlock() instanceof ColumnSectionBlock && state.getValue(ColumnSectionBlock.ACTIVE) != working)
						level.setBlock(at, state.setValue(ColumnSectionBlock.ACTIVE, working), 2);
				}
	}

	static Heat heatOf(BlazeBurnerBlock.HeatLevel level) {
		return switch (level) {
			case SEETHING -> Heat.SUPERHEATED;
			case KINDLED, FADING -> Heat.HEATED;
			default -> Heat.NONE;
		};
	}
}
