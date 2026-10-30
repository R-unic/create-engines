package com.createengines.engine;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.createengines.content.head.CylinderHeadBlockEntity;
import com.createengines.engine.sim.OilSupply;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

/**
 * The oil gallery shared by every cylinder head on a crankshaft, which feeds the whole engine. The engine draws
 * fractions of a millibucket; the gallery takes whole millibuckets from whichever head tanks hold oil and keeps the
 * change.
 */
public final class GalleryOilSupply implements OilSupply {

	private final Level level;
	private final List<BlockPos> heads;
	private double credit;

	public GalleryOilSupply(Level level, List<BlockPos> heads) {
		this.level = level;
		this.heads = heads;
	}

	private @Nullable FluidTank tank(BlockPos pos) {
		return level.isLoaded(pos) && level.getBlockEntity(pos) instanceof CylinderHeadBlockEntity head
			? head.oilTank() : null;
	}

	@Override
	public double draw(double mb) {
		int needed = (int) Math.ceil(mb - credit);
		for (BlockPos pos : heads) {
			if (needed <= 0)
				break;
			FluidTank tank = tank(pos);
			if (tank == null)
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
