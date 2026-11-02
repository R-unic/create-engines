package com.createengines.engine;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.createengines.content.sump.SumpBlockEntity;
import com.createengines.engine.sim.FuelSpec;
import com.createengines.engine.sim.FuelSupply;
import com.createengines.registry.CEDataMaps;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;

/**
 * The pilot fuel in the oil pans touching an engine, for cylinders that light a main fuel with a pilot shot. A pilot
 * shot is a few hundredths of a millibucket, so the pans give whole millibuckets and the change is kept, as the other
 * supplies do.
 */
public final class SumpPilotSupply implements FuelSupply {

	private final Level level;
	private final List<BlockPos> sumps;
	private double credit;
	private @Nullable Fluid creditFluid;
	/** The pans are looked at once a tick: a tick is many simulation steps, and a cylinder asks on every one. */
	private long scannedTick = Long.MIN_VALUE;
	private @Nullable Fluid scanned;

	public SumpPilotSupply(Level level, List<BlockPos> sumps) {
		this.level = level;
		this.sumps = sumps;
	}

	private @Nullable SumpBlockEntity sump(BlockPos pos) {
		return level.isLoaded(pos) && level.getBlockEntity(pos) instanceof SumpBlockEntity sump ? sump : null;
	}

	/** The fluid the pilot would burn next, if any. */
	public @Nullable Fluid fluid() {
		if (credit > 0 && creditFluid != null)
			return creditFluid;
		long now = level.getGameTime();
		if (now != scannedTick) {
			scannedTick = now;
			scanned = scan();
		}
		return scanned;
	}

	private @Nullable Fluid scan() {
		for (BlockPos pos : sumps) {
			SumpBlockEntity sump = sump(pos);
			if (sump != null && CEDataMaps.isPilotFuel(sump.pilotTank()
				.getFluid()))
				return sump.pilotTank()
					.getFluid()
					.getFluid();
		}
		return null;
	}

	@Override
	public @Nullable FuelSpec fuel() {
		Fluid fluid = fluid();
		return fluid == null ? null : CEDataMaps.fuel(fluid);
	}

	@Override
	public double draw(double mb) {
		Fluid fluid = fluid();
		if (fluid == null)
			return 0;
		if (creditFluid != fluid) {
			credit = 0;
			creditFluid = fluid;
		}
		int needed = (int) Math.ceil(mb - credit);
		for (BlockPos pos : sumps) {
			if (needed <= 0)
				break;
			SumpBlockEntity sump = sump(pos);
			if (sump == null || sump.pilotTank()
				.getFluid()
				.getFluid() != fluid)
				continue;
			FluidStack drained = sump.pilotTank()
				.drain(needed, FluidAction.EXECUTE);
			credit += drained.getAmount();
			needed -= drained.getAmount();
		}
		double delivered = Math.min(mb, credit);
		credit -= delivered;
		return delivered;
	}
}
