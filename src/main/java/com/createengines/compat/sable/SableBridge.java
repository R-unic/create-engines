package com.createengines.compat.sable;

import dev.ryanhcode.sable.companion.SableCompanion;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** The only class that names Sable's types; {@link SableCompat} reaches it only once the mod is known to be there. */
final class SableBridge {

	private SableBridge() {
	}

	static Vec3 project(Level level, Vec3 position) {
		return SableCompanion.INSTANCE.projectOutOfSubLevel(level, position);
	}

	/** Whether {@code pos} lies in the plot grid, where moving structures are kept, and so is not where it is drawn. */
	static boolean inPlot(Level level, BlockPos pos) {
		return SableCompanion.INSTANCE.isInPlotGrid(level, pos);
	}
}
