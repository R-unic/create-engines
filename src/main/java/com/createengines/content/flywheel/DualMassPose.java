package com.createengines.content.flywheel;

import org.jetbrains.annotations.Nullable;

import com.createengines.config.CEConfig;
import com.createengines.content.EnginePoses;
import com.createengines.content.crankshaft.CrankshaftBlockEntity;
import com.createengines.engine.EngineSummary;
import com.createengines.engine.LoadedBlocks;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.level.Level;

/**
 * Where a Dual-Mass Flywheel's two discs are drawn: the primary with the shaft, the secondary behind it by
 * {@link SpringPackLag}, which is worked out from the engine the flywheel is on (its load, its ripple and where the crank is
 * in its firing cycle). Shared by the Flywheel visual and the fallback renderer, so both show the same thing. Draw only: it
 * reads the engine's synced figures and the resonance setting and changes nothing.
 */
public final class DualMassPose {

	/** The resonance drawn when the setting cannot be read (the client has not been sent the server's configuration). */
	private static final double DEFAULT_RESONANCE = 48.0;
	/** How far along its shaft a flywheel looks for the crankshaft it sits on, in blocks. */
	private static final int REACH = 16;

	private DualMassPose() {
	}

	/** The server's resonance setting, or the default where it is not loaded (a Ponder scene outside any world). */
	static double resonance() {
		try {
			return CEConfig.DUAL_MASS_RESONANCE.get();
		} catch (RuntimeException notLoaded) {
			return DEFAULT_RESONANCE;
		}
	}

	/** The primary disc's angle about the shaft, in degrees. */
	public static float primary(CastFlywheelBlockEntity be, Axis axis) {
		return (float) Math.toDegrees(KineticBlockEntityRenderer.getAngleForBe(be, be.getBlockPos(), axis));
	}

	/** The secondary disc's angle, given the primary's. */
	public static float secondary(CastFlywheelBlockEntity be, float primaryDegrees, float renderTime) {
		return (float) SpringPackLag.secondaryAngle(primaryDegrees, be.getSpeed(), twist(be, renderTime));
	}

	private static double twist(CastFlywheelBlockEntity be, float renderTime) {
		double load = 0, ripple = 0, phase = 0;
		CrankshaftBlockEntity crank = crankOf(be);
		if (crank != null) {
			EngineSummary summary = crank.engineSummary();
			if (summary != null) {
				load = summary.availableTorque() > 0 ? summary.loadTorque() / summary.availableTorque() : 0;
				ripple = summary.ripple();
				// One pulse for each cylinder every two turns of the crank
				phase = EnginePoses.drawnShaft(crank, renderTime) / 720 * summary.cylinders();
			}
		}
		// Its own dial moves where this flywheel's springs resonate, so the lag follows the setting
		return SpringPackLag.twist(be.getSpeed(), resonance() * be.resonanceFactor(), load, ripple, phase);
	}

	/** The crankshaft this flywheel sits on, along its shaft, found once and remembered until it goes. */
	private static @Nullable CrankshaftBlockEntity crankOf(CastFlywheelBlockEntity be) {
		Level level = be.getLevel();
		if (level == null)
			return null;
		if (be.crankPos != null && LoadedBlocks.blockEntity(level, be.crankPos) instanceof CrankshaftBlockEntity known)
			return known;
		be.crankPos = null;
		Axis axis = be.getBlockState()
			.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.AXIS);
		for (int i = 1; i <= REACH; i++)
			for (Direction way : new Direction[] {Direction.get(Direction.AxisDirection.POSITIVE, axis),
				Direction.get(Direction.AxisDirection.NEGATIVE, axis)}) {
				BlockPos at = be.getBlockPos()
					.relative(way, i);
				if (LoadedBlocks.blockEntity(level, at) instanceof CrankshaftBlockEntity found) {
					be.crankPos = at;
					return found;
				}
			}
		return null;
	}
}
