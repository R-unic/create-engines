package com.createengines.content.refinery;

import com.createengines.engine.sim.ColumnShape.Part;
import com.createengines.registry.CEBlockEntities;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
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
 * The two ends of a refining column: the {@link Part#REBOILER} that takes the feed and the catalyst on its base, and the
 * {@link Part#CONDENSER} the product leaves from on top. Both are the one block entity, which finds the column they
 * belong to; see {@link ColumnPortBlockEntity}.
 */
public class ColumnPortBlock extends Block implements IWrenchable, IBE<ColumnPortBlockEntity> {

	private final Part part;

	public ColumnPortBlock(Properties properties, Part part) {
		super(properties);
		this.part = part;
	}

	public Part part() {
		return part;
	}

	/** A bucket fills a Reboiler or empties a Condenser; a catalyst item goes into the Reboiler's slot by hand. */
	@Override
	protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
											  InteractionHand hand, BlockHitResult hit) {
		if (part == Part.REBOILER && level.getBlockEntity(pos) instanceof ColumnPortBlockEntity port
			&& RefiningRecipe.takesCatalyst(level, stack)) {
			if (!level.isClientSide) {
				ItemStack left = port.insertCatalyst(stack.copy());
				int moved = stack.getCount() - left.getCount();
				if (moved > 0 && !player.isCreative())
					stack.shrink(moved);
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

	/** A comparator reads the column's feed tank off a Reboiler and its product tank off a Condenser. */
	@Override
	protected boolean hasAnalogOutputSignal(BlockState state) {
		return true;
	}

	@Override
	protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
		return level.getBlockEntity(pos) instanceof ColumnPortBlockEntity port ? port.comparatorSignal() : 0;
	}

	@Override
	protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
		if (!level.isClientSide && !state.is(newState.getBlock())
			&& level.getBlockEntity(pos) instanceof ColumnPortBlockEntity port)
			port.dropCatalyst();
		IBE.onRemove(state, level, pos, newState);
	}

	@Override
	public Class<ColumnPortBlockEntity> getBlockEntityClass() {
		return ColumnPortBlockEntity.class;
	}

	@Override
	public BlockEntityType<? extends ColumnPortBlockEntity> getBlockEntityType() {
		return CEBlockEntities.COLUMN_PORT.get();
	}
}
