package com.createengines.content.starter;

import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;

import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.block.state.BlockState;

/** The starter's input shaft, for Ponder and for when Flywheel is off. */
public class StarterRenderer extends KineticBlockEntityRenderer<StarterBlockEntity> {

	public StarterRenderer(BlockEntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	protected SuperByteBuffer getRotatedModel(StarterBlockEntity be, BlockState state) {
		return CachedBuffers.partialFacing(AllPartialModels.SHAFT_HALF, state, state.getValue(StarterBlock.FACING)
			.getOpposite());
	}
}
