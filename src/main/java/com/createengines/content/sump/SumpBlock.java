package com.createengines.content.sump;

import com.createengines.engine.topology.EngineTopology;
import com.createengines.registry.CEBlockEntities;
import com.simibubi.create.foundation.block.IBE;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.fluids.FluidUtil;

/**
 * The engine's oil pan. Set it against a crankshaft block or a cylinder head and it supplies oil to that whole
 * engine, so the heads' faces are left for the intake, exhaust and fuel. Fill it by hand with a bucket of Engine Oil,
 * or pipe oil into any face.
 */
public class SumpBlock extends Block implements IBE<SumpBlockEntity> {

	public SumpBlock(Properties properties) {
		super(properties);
	}

	/** A bucket (or any fluid container) fills or empties the pan. */
	@Override
	protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
											  Player player, InteractionHand hand, BlockHitResult hit) {
		if (FluidUtil.getFluidHandler(stack)
			.isEmpty())
			return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
		if (!level.isClientSide)
			FluidUtil.interactWithFluidHandler(player, hand, level, pos, hit.getDirection());
		return ItemInteractionResult.sidedSuccess(level.isClientSide);
	}

	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
		super.onPlace(state, level, pos, oldState, movedByPiston);
		EngineTopology.markDirty(level, pos);
	}

	@Override
	protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
		IBE.onRemove(state, level, pos, newState);
		EngineTopology.markDirty(level, pos);
	}

	@Override
	public Class<SumpBlockEntity> getBlockEntityClass() {
		return SumpBlockEntity.class;
	}

	@Override
	public BlockEntityType<? extends SumpBlockEntity> getBlockEntityType() {
		return CEBlockEntities.SUMP.get();
	}
}
