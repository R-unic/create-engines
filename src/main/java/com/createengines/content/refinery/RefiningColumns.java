package com.createengines.content.refinery;

import com.createengines.engine.sim.ColumnShape;
import com.createengines.engine.sim.ColumnShape.Part;
import com.createengines.engine.sim.Heat;
import com.createengines.registry.CEBlocks;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

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
		return Part.OTHER;
	}

	public static ColumnShape.Result scan(Level level, BlockPos pos) {
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		return ColumnShape.scan((x, y, z) -> partAt(level, cursor.set(x, y, z)), pos.getX(), pos.getY(), pos.getZ());
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
		for (int x = shape.x(); x < shape.x() + shape.width(); x++)
			for (int z = shape.z(); z < shape.z() + shape.width(); z++)
				for (int y = shape.y() + 1; y <= shape.y() + shape.height(); y++) {
					BlockPos at = new BlockPos(x, y, z);
					if (!level.isLoaded(at))
						continue;
					net.minecraft.world.level.block.state.BlockState state = level.getBlockState(at);
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
