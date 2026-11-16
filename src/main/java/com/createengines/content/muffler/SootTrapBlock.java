package com.createengines.content.muffler;

import com.createengines.registry.CEBlockEntities;
import com.simibubi.create.foundation.block.IBE;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A soot trap: a canister with a filter in an exhaust run that catches the soot a rich engine would send out of the
 * pipe. It costs the engine nothing and does not quiet it (that is the muffler's job). The soot waits in the trap as
 * lumps; an empty hand takes them out, and a hopper or funnel can too. A rich engine wastes fuel to make them, and a
 * clean one makes none. Black dye is made from soot.
 */
public class SootTrapBlock extends ExhaustCanisterBlock implements IBE<SootTrapBlockEntity> {

	public SootTrapBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
											   BlockHitResult hit) {
		if (!(level.getBlockEntity(pos) instanceof SootTrapBlockEntity trap))
			return InteractionResult.PASS;
		if (level.isClientSide)
			return trap.stored() > 0 ? InteractionResult.SUCCESS : InteractionResult.PASS;
		var taken = trap.takeAll();
		if (taken.isEmpty())
			return InteractionResult.PASS;
		player.getInventory()
			.placeItemBackInInventory(taken);
		return InteractionResult.SUCCESS;
	}

	@Override
	protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
		if (!level.isClientSide && !state.is(newState.getBlock())
			&& level.getBlockEntity(pos) instanceof SootTrapBlockEntity trap)
			trap.dropContents();
		super.onRemove(state, level, pos, newState, movedByPiston);
		if (!state.is(newState.getBlock()))
			IBE.onRemove(state, level, pos, newState);
	}

	@Override
	public Class<SootTrapBlockEntity> getBlockEntityClass() {
		return SootTrapBlockEntity.class;
	}

	@Override
	public BlockEntityType<? extends SootTrapBlockEntity> getBlockEntityType() {
		return CEBlockEntities.SOOT_TRAP.get();
	}
}
