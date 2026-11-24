package com.createengines.content.fuel;

import com.createengines.engine.sim.FuelSpec;
import com.createengines.engine.sim.SpillFire;
import com.createengines.engine.sim.Tuning;
import com.createengines.registry.CEDataMaps;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluids;

/**
 * Liquid fuel lying in the world. Fire, lava or burning fuel beside it lights it, and it burns off in a wave through the
 * pool, a few ticks per block, leaving fire where it was.
 */
public class FuelLiquidBlock extends LiquidBlock {

	/** Ticks between one block of a pool catching and the next, for a fuel of flammability 1 (see {@link SpillFire#delay}). */
	public static final int SPREAD_DELAY = SpillFire.BASE_DELAY;

	public FuelLiquidBlock(FlowingFluid fluid, Properties properties) {
		super(fluid, properties);
	}

	/** Whether this block is a flame or something hot enough to light fuel beside it. */
	public static boolean isIgnitionSource(BlockState state) {
		return state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE) || state.is(Blocks.LAVA) || state.is(Blocks.MAGMA_BLOCK)
			|| state.is(Blocks.CAMPFIRE) && state.getValue(net.minecraft.world.level.block.CampfireBlock.LIT)
			|| state.getFluidState()
				.is(Fluids.LAVA) || state.getFluidState()
					.is(Fluids.FLOWING_LAVA);
	}

	private static boolean burns(BlockState state) {
		return state.getBlock() instanceof FuelLiquidBlock && CEDataMaps.fuel(state.getFluidState()
			.getType()) != null;
	}

	/**
	 * How flammable the fuel in this block is, 1 (every fuel alike, as in 2.0.1) unless {@code compat.fuelFireByVolatility} is on.
	 * Only fuel lying in the world is ever read: nothing here touches an engine's tank or a running engine.
	 */
	private static double flammability(BlockState state) {
		if (!Tuning.fuelFireByVolatility)
			return 1;
		FuelSpec fuel = CEDataMaps.fuel(state.getFluidState()
			.getType());
		return fuel == null ? 1 : fuel.flammability();
	}

	/** Ticks before a block of this fuel next to a flame tries to catch: short for gasoline, long for diesel. */
	private static int spreadDelay(BlockState state) {
		return SpillFire.delay(flammability(state));
	}

	private boolean touchesIgnition(Level level, BlockPos pos) {
		if (!Tuning.fuelFire)
			return false;
		for (Direction direction : Direction.values())
			if (isIgnitionSource(level.getBlockState(pos.relative(direction))))
				return true;
		return false;
	}

	@Override
	public boolean isRandomlyTicking(BlockState state) {
		return true;
	}

	@Override
	public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		super.randomTick(state, level, pos, random);
		if (touchesIgnition(level, pos))
			level.scheduleTick(pos, this, spreadDelay(state));
	}

	@Override
	public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
		super.onPlace(state, level, pos, oldState, movedByPiston);
		wake(level, pos);
	}

	@Override
	public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean moving) {
		super.neighborChanged(state, level, pos, block, fromPos, moving);
		wake(level, pos);
	}

	private void wake(Level level, BlockPos pos) {
		if (!level.isClientSide && touchesIgnition(level, pos) && !level.getBlockTicks()
			.hasScheduledTick(pos, this))
			level.scheduleTick(pos, this, spreadDelay(level.getBlockState(pos)));
	}

	@Override
	public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		// Ticks are only scheduled for a block that touched a flame, or that a burning neighbour lit. The flame
		// itself may be gone by now (fire cannot stand on falling liquid), so do not ask for one again here.
		if (!Tuning.fuelFire || !burns(state))
			return;
		// A hard-to-light fuel (diesel, kerosene, plant oil) does not always catch: it tries again after its own, longer delay
		// while a flame is still beside it, so it lights, but later and with gaps, while gasoline lights at once
		double chance = SpillFire.catchChance(flammability(state));
		if (chance < 1 && random.nextDouble() >= chance) {
			if (touchesIgnition(level, pos))
				level.scheduleTick(pos, this, spreadDelay(state));
			return;
		}
		burnAway(level, pos);
	}

	/** Turns this block of fuel to flame, and lights any fuel next to it. */
	private static void burnAway(ServerLevel level, BlockPos pos) {
		BlockState fire = BaseFireBlock.getState(level, pos);
		// Fire cannot stand on falling liquid, so there it is placed without a neighbour update: it stays as a flame
		// until its own first tick takes it away, instead of vanishing the instant it is set.
		level.setBlock(pos, fire, fire.canSurvive(level, pos) ? Block.UPDATE_ALL : Block.UPDATE_CLIENTS);
		BlockPos above = pos.above();
		if (level.getBlockState(above)
			.isAir() && Blocks.FIRE.defaultBlockState()
				.canSurvive(level, above))
			level.setBlock(above, BaseFireBlock.getState(level, above), Block.UPDATE_ALL);
		for (Direction direction : Direction.values()) {
			BlockPos next = pos.relative(direction);
			BlockState neighbour = level.getBlockState(next);
			if (burns(neighbour))
				level.scheduleTick(next, neighbour.getBlock(), spreadDelay(neighbour));
		}
	}
}
