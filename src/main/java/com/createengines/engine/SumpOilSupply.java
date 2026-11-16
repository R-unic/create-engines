package com.createengines.engine;

import java.util.List;

import com.createengines.content.sump.SumpBlockEntity;
import com.createengines.engine.sim.OilSupply;
import com.createengines.registry.CEFluids;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;

/**
 * The oil pans touching an engine. As with the head galleries, the engine draws fractions of a millibucket; the pans
 * give whole millibuckets and the change is kept.
 */
public final class SumpOilSupply implements OilSupply {

	private final Level level;
	private final List<BlockPos> sumps;
	private double credit;
	private double lubricity = 1;

	@Override
	public double lubricity() {
		return lubricity;
	}

	public SumpOilSupply(Level level, List<BlockPos> sumps) {
		this.level = level;
		this.sumps = sumps;
	}

	@Override
	public double draw(double mb) {
		int needed = (int) Math.ceil(mb - credit);
		for (BlockPos pos : sumps) {
			if (needed <= 0)
				break;
			if (!level.isLoaded(pos) || !(level.getBlockEntity(pos) instanceof SumpBlockEntity sump))
				continue;
			FluidStack drained = sump.tank()
				.drain(needed, FluidAction.EXECUTE);
			credit += drained.getAmount();
			needed -= drained.getAmount();
			if (!drained.isEmpty())
				lubricity = CEFluids.lubricity(drained);
		}
		double delivered = Math.min(mb, credit);
		credit -= delivered;
		return delivered;
	}
}
