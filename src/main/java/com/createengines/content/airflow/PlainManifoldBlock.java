package com.createengines.content.airflow;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A manifold with nothing on it: the only kind Engine Casing goes over, so the only one that has a block entity, to hold the
 * dye of that casing. A throttle or a sensor has its own block entity and is not clad.
 */
public class PlainManifoldBlock extends ManifoldBlock implements EntityBlock {

	public PlainManifoldBlock(Properties properties, PortKind kind) {
		super(properties, kind);
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new ManifoldBlockEntity(com.createengines.registry.CEBlockEntities.MANIFOLD.get(), pos, state);
	}
}
