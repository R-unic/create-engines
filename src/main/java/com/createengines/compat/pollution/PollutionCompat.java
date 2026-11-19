package com.createengines.compat.pollution;

import org.slf4j.Logger;

import com.createengines.config.CEConfig;
import com.createengines.content.airflow.PortOpeningBlock;
import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.ModList;

/**
 * Pollution of the Realms (mod id {@value #MOD_ID}): engines put carbon and sulfur into the air through an open exhaust
 * outlet. Optional, and one way: nothing is read back, so an engine runs the same with or without it. The mod's types are
 * named only in {@link PollutionBridge}.
 */
public final class PollutionCompat {

	public static final String MOD_ID = "adpother";

	private static final Logger LOGGER = LogUtils.getLogger();

	private static boolean broken;

	private PollutionCompat() {
	}

	/** Whether the mod is installed and the server has not switched this off. */
	public static boolean active() {
		return !broken && CEConfig.POLLUTION.get() && CEConfig.POLLUTION_RATE.get() > 0 && installed();
	}

	public static boolean installed() {
		return ModList.get() != null && ModList.get()
			.isLoaded(MOD_ID);
	}

	/** Releases the gas through one outlet, if it is open and neither under water nor facing it. A failure turns the integration off. */
	public static void release(Level level, BlockPos outlet, int carbon, int sulfur) {
		if (!(level instanceof ServerLevel) || (carbon <= 0 && sulfur <= 0))
			return;
		BlockState state = level.getBlockState(outlet);
		if (!(state.getBlock() instanceof PortOpeningBlock) || !state.getFluidState()
			.isEmpty())
			return;
		Direction facing = state.getValue(PortOpeningBlock.FACING);
		// A mouth with fluid in front of it is as shut as one under it: no gas goes through the water to the air beyond
		if (!level.getFluidState(outlet.relative(facing))
			.isEmpty())
			return;
		try {
			PollutionBridge.release(level, outlet, facing, carbon, sulfur);
		} catch (LinkageError | RuntimeException e) {
			broken = true;
			LOGGER.warn("Pollution of the Realms did not take our gas; engines will not release any this session", e);
		}
	}
}
