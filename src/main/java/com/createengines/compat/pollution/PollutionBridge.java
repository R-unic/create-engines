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

	/** Blocks each side of the cell in front of the mouth that the area cap looks at: a cube 7 across. */
	static final int CAP_RADIUS = 3;

	private PollutionBridge() {
	}

	/**
	 * Puts steps of carbon and sulfur into the cells in front of {@code outlet}. A cell holds one gas, so each gas goes into the
	 * first cell that takes it, and what a full cell cannot hold carries on to the next one in reach.
	 */
	static void release(Level level, BlockPos outlet, Direction facing, int carbon, int sulfur) {
		PollutionProbe.record(outlet, true, carbon);
		PollutionProbe.record(outlet, false, sulfur);
		pump(Pollutants.BuiltIn.CARBON.get(), level, outlet, facing, carbon);
		pump(Pollutants.BuiltIn.SULFUR.get(), level, outlet, facing, sulfur);
	}

	/** Steps of carbon and sulfur together in the cube of {@value #CAP_RADIUS} blocks round the cell in front of an outlet. */
	static long airHolds(Level level, BlockPos mouth) {
		BlockPos centre = com.createengines.compat.sable.SableCompat.worldBlockPos(level, mouth);
		long total = 0;
		for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-CAP_RADIUS, -CAP_RADIUS, -CAP_RADIUS),
			centre.offset(CAP_RADIUS, CAP_RADIUS, CAP_RADIUS))) {
			if (!level.isLoaded(pos))
				continue;
			var state = level.getBlockState(pos);
			if (state.is(net.minecraft.world.level.block.Blocks.AIR))
				continue;
			total += Pollutants.BuiltIn.CARBON.get().getCarriedPollutionAmount(state)
				+ Pollutants.BuiltIn.SULFUR.get().getCarriedPollutionAmount(state);
		}
		return total;
	}

	private static void pump(Pollutant<?> gas, Level level, BlockPos outlet, Direction facing, int steps) {
		BlockPos along = outlet.relative(facing);
		for (int reach = 0; reach < REACH && steps > 0; reach++, along = along.relative(facing)) {
			// An outlet on a moving structure (Sable) is in a plot, where no one breathes: the gas goes into the world cell the
			// mouth points at. Everywhere else this is the cell itself.
			BlockPos cell = com.createengines.compat.sable.SableCompat.worldBlockPos(level, along);
			if (!level.isLoaded(cell) || !gas.canStateBePumped(level.getBlockState(cell)))
				continue;
			steps -= Math.max(0, gas.pump(level, cell, steps));
		}
	}
}
