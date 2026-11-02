package com.createengines.engine;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.createengines.content.sump.SumpBlockEntity;
import com.createengines.engine.sim.CoolantSupply;
import com.createengines.registry.CEFluids;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;

/**
 * The coolant in the oil pans touching an engine. The engine spends fractions of a millibucket; the pans give whole
 * millibuckets and the change is kept. The coolant on hand is the first pan's that has any, and only pans holding the
 * same fluid are drawn on with it, so water and proper coolant are never mixed up.
 */
public final class SumpCoolantSupply implements CoolantSupply {

	private final Level level;
	private final List<BlockPos> sumps;
	private double credit;

	public SumpCoolantSupply(Level level, List<BlockPos> sumps) {
		this.level = level;
		this.sumps = sumps;
	}

	private @Nullable SumpBlockEntity sump(BlockPos pos) {
		return level.isLoaded(pos) && level.getBlockEntity(pos) instanceof SumpBlockEntity sump ? sump : null;
	}

	/** The coolant tank's stack in the first pan that has any, or empty. */
	private FluidStack available() {
		for (BlockPos pos : sumps) {
			SumpBlockEntity sump = sump(pos);
			if (sump != null && CEFluids.isCoolant(sump.coolantTank()
				.getFluid()))
				return sump.coolantTank()
					.getFluid();
		}
		return FluidStack.EMPTY;
	}

	@Override
	public double quality() {
		return CEFluids.coolingQuality(available());
	}

	@Override
	public double draw(double mb) {
		FluidStack fluid = available();
		if (fluid.isEmpty())
			return 0;
		int needed = (int) Math.ceil(mb - credit);
		for (BlockPos pos : sumps) {
			if (needed <= 0)
				break;
			SumpBlockEntity sump = sump(pos);
			if (sump == null || !FluidStack.isSameFluid(sump.coolantTank()
				.getFluid(), fluid))
				continue;
			FluidStack drained = sump.coolantTank()
				.drain(needed, FluidAction.EXECUTE);
			credit += drained.getAmount();
			needed -= drained.getAmount();
		}
		double delivered = Math.min(mb, credit);
		credit -= delivered;
		return delivered;
	}
}
