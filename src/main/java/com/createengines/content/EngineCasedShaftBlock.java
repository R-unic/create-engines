package com.createengines.content;

import java.util.List;
import java.util.function.Supplier;

import com.createengines.advancement.Achievements;
import com.createengines.advancement.Milestone;
import com.createengines.registry.CEBlockEntities;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.simpleRelays.encased.EncasedShaftBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** A Create shaft in Engine Casing: Create's encased shaft, with this mod's casing and block entity type. */
public class EngineCasedShaftBlock extends EncasedShaftBlock {

	public EngineCasedShaftBlock(Properties properties, Supplier<Block> casing) {
		super(properties, casing);
	}

	@Override
	public void handleEncasing(BlockState state, Level level, BlockPos pos, ItemStack heldItem, Player player,
							   InteractionHand hand, BlockHitResult ray) {
		super.handleEncasing(state, level, pos, heldItem, player, hand, ray);
		if (!level.isClientSide)
			Achievements.award(level, List.of(pos), Milestone.ENCASED);
	}

	/** A dye tints the casing and a bucket of water washes it; anything else is left to Create. */
	@Override
	protected net.minecraft.world.ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
		BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
		net.minecraft.world.ItemInteractionResult result = CasingPaint.apply(stack, state, level, pos, player);
		return result == net.minecraft.world.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION
			? super.useItemOn(stack, state, level, pos, player, hand, hit) : result;
	}

	@Override
	public BlockEntityType<? extends KineticBlockEntity> getBlockEntityType() {
		return CEBlockEntities.ENCASED_SHAFT.get();
	}
}
