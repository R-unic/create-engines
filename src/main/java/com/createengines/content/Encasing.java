package com.createengines.content;

import java.util.List;

import com.createengines.advancement.Achievements;
import com.createengines.advancement.Milestone;

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
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Engine casing built round a crankshaft, crank throw, camshaft or cam lobe, the way Create's andesite casing goes
 * round a shaft: the block keeps working exactly as before, but shows a casing with the shaft ends poking out. Use
 * an Engine Casing on the part to fit it, and a wrench to take it off again.
 */
public final class Encasing {

	public static final BooleanProperty ENCASED = BooleanProperty.create("encased");
	/**
	 * A crank or cam part clad in Window Casing instead of plain: it is clad as well (the part is hidden by the same
	 * rules), but the casing has a pane of glass in each side, so the part turning inside shows. Only the crankshaft and
	 * camshaft families have it.
	 */
	public static final BooleanProperty WINDOWED = BooleanProperty.create("windowed");
	/**
	 * Which plate a clad part wears: plain, riveted or louvred (the window plate is {@link #WINDOWED}). Every part that
	 * can be clad carries it; a part saved before it existed loads as plain.
	 */
	public static final EnumProperty<CasingStyle> STYLE = EnumProperty.create("casing_style", CasingStyle.class,
		CasingStyle.PLAIN, CasingStyle.RIVETED, CasingStyle.LOUVRED);
	/** The dye on a clad part's casing, the same property Engine Casing blocks carry. */
	public static final EnumProperty<CasingPaint> PAINT = EngineCasingBlock.PAINT;

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
		if (state.getValue(ENCASED))
			return CasingPaint.apply(stack, state, level, pos, player);
		CasingStyle style = CasingStyle.ofItem(stack);
		if (style == null || style == CasingStyle.WINDOW && !canHaveWindow(state) || !state.hasProperty(STYLE))
			return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
		if (!level.isClientSide) {
			BlockState cased = state.setValue(ENCASED, true);
			if (style == CasingStyle.WINDOW)
				cased = cased.setValue(WINDOWED, true);
			else
				cased = cased.setValue(STYLE, style);
			level.setBlock(pos, cased, Block.UPDATE_ALL);
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
			BlockState bare = state.setValue(ENCASED, false)
				.setValue(PAINT, CasingPaint.NONE)
				.setValue(STYLE, CasingStyle.PLAIN);
			level.setBlock(pos, state.hasProperty(WINDOWED) ? bare.setValue(WINDOWED, false) : bare, Block.UPDATE_ALL);
			level.playSound(null, pos, state.getSoundType()
				.getBreakSound(), net.minecraft.sounds.SoundSource.BLOCKS, 0.8f, 1.1f);
			Player player = context.getPlayer();
			if (player == null || !player.isCreative())
				if (player != null)
					player.getInventory()
						.placeItemBackInInventory(casingStack(state));
				else
					Block.popResource(level, pos, casingStack(state));
		}
		return InteractionResult.sidedSuccess(level.isClientSide);
	}

	/** A cased part that is broken gives its casing back. */
	public static void dropOnRemove(BlockState state, Level level, BlockPos pos, BlockState newState) {
		if (!level.isClientSide && state.getValue(ENCASED) && !state.is(newState.getBlock()))
			Block.popResource(level, pos, casingStack(state));
	}

	/** Only the crank and cam parts take a window; a rotor housing is a crank part that draws its own way. */
	public static boolean canHaveWindow(BlockState state) {
		return state.hasProperty(WINDOWED) && !(state.getBlock() instanceof com.createengines.content.rotor.RotorHousingBlock);
	}

	/** The style a clad part is in: window if it has a pane of glass, else its plate. */
	public static CasingStyle styleOf(BlockState state) {
		if (state.hasProperty(WINDOWED) && state.getValue(WINDOWED))
			return CasingStyle.WINDOW;
		return state.hasProperty(STYLE) ? state.getValue(STYLE) : CasingStyle.PLAIN;
	}

	/** The casing a clad part gives back: the one it was clad in. */
	public static ItemStack casingStack(BlockState state) {
		return styleOf(state).casingStack();
	}
}
