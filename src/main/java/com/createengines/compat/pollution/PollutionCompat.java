package com.createengines.compat.pollution;

import java.util.function.ToLongFunction;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import com.createengines.config.CEConfig;
import com.createengines.engine.sim.IntakeAir;
import com.createengines.content.airflow.PortOpeningBlock;
import com.mojang.logging.LogUtils;

import net.createmod.catnip.data.WorldAttached;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.ModList;

/**
 * Pollution of the Realms (mod id {@value #MOD_ID}): engines put carbon and sulfur into the air through an open exhaust
 * outlet. Optional. With polluted intake air turned on (it is off by default), engines also breathe what is in the air at their
 * intakes; otherwise nothing is read back, and an engine runs the same with or without the mod. The mod's types are named only in
 * {@link PollutionBridge}.
 */
public final class PollutionCompat {

	public static final String MOD_ID = "adpother";

	private static final Logger LOGGER = LogUtils.getLogger();

	private static boolean broken;

	/** The area cap's last reading at each outlet, by level (and dropped with it). */
	private static final WorldAttached<AreaCapCache<BlockPos>> AIR = new WorldAttached<>(level -> new AreaCapCache<>());

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

	/** The gas round each intake mouth, as {@link #AIR} is for outlets, and apart from it so neither disturbs the other's reading. */
	private static final WorldAttached<AreaCapCache<BlockPos>> BREATHED = new WorldAttached<>(level -> new AreaCapCache<>());

	/** Stands in for the gas in the air when a GameTest runs without the pollution mod; null in a running game. */
	private static volatile @Nullable ToLongFunction<BlockPos> standIn;

	/** For GameTests only: the steps of gas round a mouth, in place of the mod's own, or null to go back to the mod. */
	public static void standInForTests(@Nullable ToLongFunction<BlockPos> held) {
		standIn = held;
	}

	/**
	 * Whether the air an engine breathes counts. Off unless the server turns it on, and nothing is read without the mod. It does not
	 * need engines to be releasing gas of their own: the air may be fouled by anything.
	 */
	public static boolean pollutedIntake() {
		return CEConfig.POLLUTED_INTAKE.get() && (standIn != null || !broken && installed());
	}

	/** The steps of gas that count as the dirtiest air: the area cap where the server sets one, so one number is not two. */
	public static long dirtiestAir() {
		int cap = CEConfig.POLLUTION_AREA_CAP.get();
		return cap > 0 ? cap : IntakeAir.DEFAULT_FULL;
	}

	/** Steps of carbon and sulfur in the air in front of an intake opening, read every few seconds. A failure turns the integration off. */
	public static long gasAtMouth(Level level, BlockPos opening) {
		if (!(level instanceof ServerLevel))
			return 0;
		ToLongFunction<BlockPos> test = standIn;
		if (test != null)
			return test.applyAsLong(opening);
		BlockState state = level.getBlockState(opening);
		BlockPos mouth = state.getBlock() instanceof PortOpeningBlock ? opening.relative(state.getValue(PortOpeningBlock.FACING)) : opening;
		try {
			return BREATHED.get(level)
				.held(opening.immutable(), level.getGameTime(), () -> PollutionBridge.airHolds(level, mouth));
		} catch (LinkageError | RuntimeException e) {
			broken = true;
			LOGGER.warn("Pollution of the Realms did not tell us what the air holds; engines will breathe it as clean this session", e);
			return 0;
		}
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
			int cap = CEConfig.POLLUTION_AREA_CAP.get();
			if (cap > 0 && AIR.get(level)
				.full(outlet.immutable(), level.getGameTime(), cap, () -> PollutionBridge.airHolds(level, outlet.relative(facing))))
				return;
			PollutionBridge.release(level, outlet, facing, carbon, sulfur);
			if (cap > 0)
				AIR.get(level)
					.added(outlet, (long) Math.max(0, carbon) + Math.max(0, sulfur));
		} catch (LinkageError | RuntimeException e) {
			broken = true;
			LOGGER.warn("Pollution of the Realms did not take our gas; engines will not release any this session", e);
		}
	}
}
