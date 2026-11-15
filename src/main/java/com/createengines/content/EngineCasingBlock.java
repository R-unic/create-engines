package com.createengines.content;

import com.simibubi.create.content.decoration.encasing.CasingBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The Engine Casing blocks: plain, riveted and louvred plate. Like Create's own casings they are wrenchable (to pick
 * them up) and join the casing of their own kind beside them: their faces, and those of the parts the plain casing
 * clads, carry on across a run as one plate (see {@link CESpriteShifts}). Any of them takes a dye: use a dye on the
 * block to tint it, and a bucket of water to wash it back to bare steel. The colour is only for looks.
 */
public class EngineCasingBlock extends CasingBlock {

	public static final EnumProperty<CasingPaint> PAINT = EnumProperty.create("paint", CasingPaint.class);

	public EngineCasingBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(PAINT, CasingPaint.NONE));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
		super.createBlockStateDefinition(builder);
		builder.add(PAINT);
	}

	@Override
	protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
											  InteractionHand hand, BlockHitResult hit) {
		ItemInteractionResult result = CasingPaint.apply(stack, state, level, pos, player);
		return result == ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION
			? super.useItemOn(stack, state, level, pos, player, hand, hit) : result;
	}
}
