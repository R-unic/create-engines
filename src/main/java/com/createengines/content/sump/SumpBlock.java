package com.createengines.content.sump;

import com.createengines.engine.topology.EngineTopology;
import com.createengines.registry.CEBlockEntities;
import com.createengines.registry.CEItems;
import com.simibubi.create.foundation.block.IBE;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
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
 * The engine's oil pan and service port. Set it against a crankshaft block or a cylinder head and it supplies oil to that
 * whole engine, and takes its coolant and its pilot fuel too, so the heads' faces are left for the intake, exhaust and
 * fuel. Fill it by hand with a bucket, or pipe fluids into any face; it sorts them out. An oil filter fits in it by
 * right-click, and comes out again by sneak-right-click with an empty hand. See {@link SumpBlockEntity}.
 */
public class SumpBlock extends Block implements IBE<SumpBlockEntity> {

	public SumpBlock(Properties properties) {
		super(properties);
	}

	/** A bucket (or any fluid container) fills the pan or takes its used oil; an oil filter fits in it. */
	@Override
	protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
											  Player player, InteractionHand hand, BlockHitResult hit) {
		if (CEItems.OIL_FILTER.isIn(stack)) {
			if (!(level.getBlockEntity(pos) instanceof SumpBlockEntity sump) || sump.filtered())
				return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
			if (!level.isClientSide) {
				sump.setFiltered(true);
				if (!player.isCreative())
					stack.shrink(1);
				level.playSound(null, pos, SoundEvents.NETHERITE_BLOCK_PLACE, SoundSource.BLOCKS, 0.6f, 1.4f);
			}
			return ItemInteractionResult.sidedSuccess(level.isClientSide);
		}
		if (FluidUtil.getFluidHandler(stack)
			.isEmpty())
			return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
		if (!level.isClientSide)
			FluidUtil.interactWithFluidHandler(player, hand, level, pos, hit.getDirection());
		return ItemInteractionResult.sidedSuccess(level.isClientSide);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
											   BlockHitResult hit) {
		if (!player.isShiftKeyDown() || !(level.getBlockEntity(pos) instanceof SumpBlockEntity sump)
			|| !sump.filtered())
			return InteractionResult.PASS;
		if (!level.isClientSide) {
			sump.setFiltered(false);
			if (!player.isCreative())
				player.getInventory()
					.placeItemBackInInventory(CEItems.OIL_FILTER.asStack());
			level.playSound(null, pos, SoundEvents.NETHERITE_BLOCK_BREAK, SoundSource.BLOCKS, 0.6f, 1.4f);
		}
		return InteractionResult.sidedSuccess(level.isClientSide);
	}

	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
		super.onPlace(state, level, pos, oldState, movedByPiston);
		EngineTopology.markDirty(level, pos);
	}

	@Override
	protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
		if (!level.isClientSide && !state.is(newState.getBlock())
			&& level.getBlockEntity(pos) instanceof SumpBlockEntity sump && sump.filtered())
			Block.popResource(level, pos, CEItems.OIL_FILTER.asStack());
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
