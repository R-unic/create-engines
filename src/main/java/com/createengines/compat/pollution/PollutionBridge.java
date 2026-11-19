package com.createengines.compat.pollution;

import com.endertech.minecraft.mods.adpother.blocks.Pollutant;
import com.endertech.minecraft.mods.adpother.init.Pollutants;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

/**
 * The only class that names Pollution of the Realms' types. It is never loaded unless that mod is installed, and only
 * {@link PollutionCompat} reaches it, so the rest of the mod runs without the mod on the classpath. Compiled against the mod's
 * own classes (it has no API of its own); a change in them shows as an error caught in {@link PollutionCompat}, which turns
 * the integration off and logs once rather than hurting an engine.
 */
final class PollutionBridge {

	/** How far past the mouth the gas may go to find a cell that takes it, for a cap or a bend in the way. */
	private static final int REACH = 4;

	private PollutionBridge() {
	}

	/** Puts steps of carbon and sulfur into the first cell in front of {@code outlet} that takes gas. */
	static void release(Level level, BlockPos outlet, Direction facing, int carbon, int sulfur) {
		BlockPos cell = outlet.relative(facing);
		for (int reach = 0; reach < REACH; reach++, cell = cell.relative(facing)) {
			Pollutant<?> probe = Pollutants.BuiltIn.CARBON.get();
			if (!probe.canStateBePumped(level.getBlockState(cell)))
				continue;
			if (carbon > 0)
				probe.pump(level, cell, carbon);
			if (sulfur > 0)
				Pollutants.BuiltIn.SULFUR.get()
					.pump(level, cell, sulfur);
			return;
		}
	}
}
