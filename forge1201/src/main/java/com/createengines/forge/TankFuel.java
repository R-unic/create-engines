package com.createengines.forge;

import org.jetbrains.annotations.Nullable;

import com.createengines.engine.sim.FuelSpec;
import com.createengines.engine.sim.FuelSupply;

import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Fuel from the fluid tanks beside a block: any neighbour with a fluid handler (a Create tank, a pipe, another mod's
 * tank), taking only fluids a pack makes a fuel ({@link FuelData}). The 1.21.1 build asks for the same thing through
 * NeoForge's capability lookup; here it is Forge's {@code getCapability}. The simulation asks for fractions of a
 * millibucket, so whole millibuckets are drawn and the rest kept until it is used.
 */
public final class TankFuel implements FuelSupply {

	private final Level level;
	private final BlockPos pos;
	private double held;
	private @Nullable FuelSpec heldFuel;

	public TankFuel(Level level, BlockPos pos) {
		this.level = level;
		this.pos = pos;
	}

	@Override
	public double draw(double mb) {
		if (held < mb)
			refill();
		double delivered = Math.min(mb, held);
		held -= delivered;
		return delivered;
	}

	@Override
	public @Nullable FuelSpec fuel() {
		if (held <= 0)
			refill();
		return held > 0 ? heldFuel : null;
	}

	/** Takes a millibucket or so from the first neighbouring tank that holds a fuel. */
	private void refill() {
		for (Direction side : Direction.values()) {
			BlockEntity neighbour = level.getBlockEntity(pos.relative(side));
			if (neighbour == null)
				continue;
			IFluidHandler handler = neighbour.getCapability(ForgeCapabilities.FLUID_HANDLER, side.getOpposite())
				.orElse(null);
			if (handler == null)
				continue;
			FluidStack offered = handler.drain(1, IFluidHandler.FluidAction.SIMULATE);
			if (offered.isEmpty())
				continue;
			FuelSpec fuel = FuelData.of(ForgeRegistries.FLUIDS.getKey(offered.getFluid()));
			if (fuel == null)
				continue;
			if (held > 0 && fuel != heldFuel)
				continue; // finish what is held before another fuel is taken
			FluidStack taken = handler.drain(new FluidStack(offered.getFluid(), 4), IFluidHandler.FluidAction.EXECUTE);
			if (taken.isEmpty())
				continue;
			held += taken.getAmount();
			heldFuel = fuel;
			return;
		}
	}
}
