package com.createengines.content.airflow;

import com.createengines.engine.topology.EngineTopology;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** A duct block. Connected manifold blocks of the same kind form one intake or exhaust network. */
public class ManifoldBlock extends Block {

	private final PortKind kind;

	public ManifoldBlock(Properties properties, PortKind kind) {
		super(properties);
		this.kind = kind;
	}

	public PortKind kind() {
		return kind;
	}

	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
		super.onPlace(state, level, pos, oldState, movedByPiston);
		EngineTopology.markDirty(level, pos);
	}

	@Override
	protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
		super.onRemove(state, level, pos, newState, movedByPiston);
		EngineTopology.markDirty(level, pos);
	}
}
