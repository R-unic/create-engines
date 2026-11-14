package com.createengines.content;

import java.util.List;
import java.util.function.Supplier;

import com.createengines.advancement.Achievements;
import com.createengines.advancement.Milestone;
import com.createengines.registry.CEBlockEntities;
import com.simibubi.create.content.kinetics.simpleRelays.SimpleKineticBlockEntity;
import com.simibubi.create.content.kinetics.simpleRelays.encased.EncasedCogwheelBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** A Create cogwheel, small or large, in Engine Casing: Create's encased cog with this mod's casing and block entity. */
public class EngineCasedCogwheelBlock extends EncasedCogwheelBlock {

	public EngineCasedCogwheelBlock(Properties properties, boolean large, Supplier<Block> casing) {
		super(properties, large, casing);
	}

	@Override
	public void handleEncasing(BlockState state, Level level, BlockPos pos, ItemStack heldItem, Player player,
							   InteractionHand hand, BlockHitResult ray) {
		super.handleEncasing(state, level, pos, heldItem, player, hand, ray);
		if (!level.isClientSide)
			Achievements.award(level, List.of(pos), Milestone.ENCASED);
	}

	@Override
	public BlockEntityType<? extends SimpleKineticBlockEntity> getBlockEntityType() {
		return isLarge ? CEBlockEntities.ENCASED_LARGE_COGWHEEL.get() : CEBlockEntities.ENCASED_COGWHEEL.get();
	}
}
