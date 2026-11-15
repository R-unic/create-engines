package com.createengines.content;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** The colour an Engine Casing block has been dyed: none (bare steel) or one of the sixteen dyes. */
public enum CasingPaint implements StringRepresentable {
	NONE("none"), WHITE("white"), ORANGE("orange"), MAGENTA("magenta"), LIGHT_BLUE("light_blue"), YELLOW("yellow"),
	LIME("lime"), PINK("pink"), GRAY("gray"), LIGHT_GRAY("light_gray"), CYAN("cyan"), PURPLE("purple"), BLUE("blue"),
	BROWN("brown"), GREEN("green"), RED("red"), BLACK("black");

	private final String name;

	CasingPaint(String name) {
		this.name = name;
	}

	public static CasingPaint of(DyeColor dye) {
		return values()[dye.getId() + 1];
	}

	/**
	 * A dye, or a bucket of water, used on a block that carries {@link EngineCasingBlock#PAINT}: the dye tints it and is
	 * used up outside creative, the water washes it and is kept. Passes for anything else, and for paint that is
	 * already on.
	 */
	public static ItemInteractionResult apply(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player) {
		CasingPaint paint;
		if (stack.getItem() instanceof DyeItem dye)
			paint = of(dye.getDyeColor());
		else if (stack.is(Items.WATER_BUCKET))
			paint = NONE;
		else
			return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
		if (!state.hasProperty(EngineCasingBlock.PAINT) || state.getValue(EngineCasingBlock.PAINT) == paint)
			return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
		if (!level.isClientSide) {
			level.setBlock(pos, state.setValue(EngineCasingBlock.PAINT, paint), Block.UPDATE_ALL);
			level.playSound(null, pos, paint == NONE ? SoundEvents.BUCKET_EMPTY : SoundEvents.DYE_USE,
				SoundSource.BLOCKS, 1, 1);
			if (paint != NONE && !player.isCreative())
				stack.shrink(1);
		}
		return ItemInteractionResult.sidedSuccess(level.isClientSide);
	}

	/** The block tint, or white for bare steel. */
	public int tint() {
		return this == NONE ? 0xFFFFFF : CasingTint.rgb(ordinal() - 1);
	}

	@Override
	public String getSerializedName() {
		return name;
	}
}
