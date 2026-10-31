package com.createengines.engine;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.createengines.content.head.GalleryMember;
import com.createengines.engine.sim.FuelSpec;
import com.createengines.engine.sim.FuelSupply;
import com.createengines.registry.CEDataMaps;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

/**
 * The fuel gallery shared by every cylinder head on a crankshaft. Injectors draw fractions of a millibucket; the
 * gallery takes whole millibuckets from whichever head tanks hold fuel and keeps the change.
 */
public final class GalleryFuelSupply implements FuelSupply {

	private final Level level;
	private final List<BlockPos> heads;
	private double credit;
	private @Nullable Fluid creditFluid;

	public GalleryFuelSupply(Level level, List<BlockPos> heads) {
		this.level = level;
		this.heads = heads;
	}

	private @Nullable FluidTank tank(BlockPos pos) {
		return level.isLoaded(pos) && level.getBlockEntity(pos) instanceof GalleryMember head
			? head.tank() : null;
	}

	/** The fluid the engine is burning, or would burn next, if any. */
	public @Nullable Fluid fluid() {
		return availableFluid();
	}

	private @Nullable Fluid availableFluid() {
		if (credit > 0 && creditFluid != null)
			return creditFluid;
		for (BlockPos pos : heads) {
			FluidTank tank = tank(pos);
			if (tank != null && CEDataMaps.isFuel(tank.getFluid()))
				return tank.getFluid()
					.getFluid();
		}
		return null;
	}

	@Override
	public @Nullable FuelSpec fuel() {
		Fluid fluid = availableFluid();
		return fluid == null ? null : CEDataMaps.fuel(fluid);
	}

	@Override
	public double draw(double mb) {
		Fluid fluid = availableFluid();
		if (fluid == null)
			return 0;
		if (creditFluid != fluid) {
			credit = 0;
			creditFluid = fluid;
		}
		int needed = (int) Math.ceil(mb - credit);
		for (BlockPos pos : heads) {
			if (needed <= 0)
				break;
			FluidTank tank = tank(pos);
			if (tank == null || tank.getFluid()
				.getFluid() != fluid)
				continue;
			FluidStack drained = tank.drain(needed, FluidAction.EXECUTE);
			credit += drained.getAmount();
			needed -= drained.getAmount();
		}
		double delivered = Math.min(mb, credit);
		credit -= delivered;
		return delivered;
	}
}
