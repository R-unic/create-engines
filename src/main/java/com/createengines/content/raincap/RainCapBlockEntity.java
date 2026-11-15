package com.createengines.content.raincap;

import com.createengines.client.RainCaps;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/** Holds nothing: it exists so the cap can have a renderer for its flap, which {@link RainCaps} drives. */
public class RainCapBlockEntity extends BlockEntity {

	public RainCapBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	@Override
	public void setRemoved() {
		super.setRemoved();
		if (level != null && level.isClientSide)
			RainCaps.forget(worldPosition);
	}
}
