package com.createengines.content;

import java.util.List;

import com.createengines.advancement.Achievements;
import com.createengines.advancement.Milestone;
import com.createengines.registry.CEBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Engine casing built round a crankshaft, crank throw, camshaft or cam lobe, the way Create's andesite casing goes
 * round a shaft: the block keeps working exactly as before, but shows a casing with the shaft ends poking out. Use
 * an Engine Casing on the part to fit it, and a wrench to take it off again.
 */
public final class Encasing {

	public static final BooleanProperty ENCASED = BooleanProperty.create("encased");

	private Encasing() {
	}

	public static VoxelShape shape(BlockState state, VoxelShape bare) {
		return state.getValue(ENCASED) ? Shapes.block() : bare;
	}

	/** A cased part is drawn from its block model, a bare one by its animated renderer alone. */
	public static RenderShape renderShape(BlockState state) {
		return state.getValue(ENCASED) ? RenderShape.MODEL : RenderShape.ENTITYBLOCK_ANIMATED;
	}

	public static ItemInteractionResult encase(ItemStack stack, BlockState state, Level level, BlockPos pos,
											   Player player) {
		if (state.getValue(ENCASED) || !stack.is(CEBlocks.ENGINE_CASING.asItem()))
			return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
		if (!level.isClientSide) {
			level.setBlock(pos, state.setValue(ENCASED, true), Block.UPDATE_ALL);
			level.playSound(null, pos, state.getSoundType()
				.getPlaceSound(), net.minecraft.sounds.SoundSource.BLOCKS, 1, 0.8f);
			if (!player.isCreative())
				stack.shrink(1);
			Achievements.award(level, List.of(pos), Milestone.ENCASED);
		}
		return ItemInteractionResult.sidedSuccess(level.isClientSide);
	}

	/** Takes the casing off a cased part, or returns null for a bare one so the caller carries on as usual. */
	public static InteractionResult uncase(BlockState state, UseOnContext context) {
		if (!state.getValue(ENCASED))
			return null;
		Level level = context.getLevel();
		BlockPos pos = context.getClickedPos();
		if (!level.isClientSide) {
			level.setBlock(pos, state.setValue(ENCASED, false), Block.UPDATE_ALL);
			level.playSound(null, pos, state.getSoundType()
				.getBreakSound(), net.minecraft.sounds.SoundSource.BLOCKS, 0.8f, 1.1f);
			Player player = context.getPlayer();
			if (player == null || !player.isCreative())
				if (player != null)
					player.getInventory()
						.placeItemBackInInventory(CEBlocks.ENGINE_CASING.asStack());
				else
					Block.popResource(level, pos, CEBlocks.ENGINE_CASING.asStack());
		}
		return InteractionResult.sidedSuccess(level.isClientSide);
	}

	/** A cased part that is broken gives its casing back. */
	public static void dropOnRemove(BlockState state, Level level, BlockPos pos, BlockState newState) {
		if (!level.isClientSide && state.getValue(ENCASED) && !state.is(newState.getBlock()))
			Block.popResource(level, pos, CEBlocks.ENGINE_CASING.asStack());
	}
}
