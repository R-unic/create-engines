package com.createengines.content.alternator;

import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;

import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The shaft through the alternator, turning, for Ponder and for when Flywheel is off. Create's default renderer turns the
 * whole block state, which drew a second alternator spinning over the still one.
 */
public class AlternatorRenderer extends KineticBlockEntityRenderer<AlternatorBlockEntity> {

	public AlternatorRenderer(BlockEntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	protected SuperByteBuffer getRotatedModel(AlternatorBlockEntity be, BlockState state) {
		return CachedBuffers.block(shaft(getRotationAxisOf(be)));
	}
}
