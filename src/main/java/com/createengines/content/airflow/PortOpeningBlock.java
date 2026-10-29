package com.createengines.content.airflow;

import org.jetbrains.annotations.Nullable;

import com.createengines.engine.topology.EngineTopology;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;

import com.mojang.serialization.MapCodec;

/**
 * Where an intake or exhaust network meets the open air. Its mouth ({@link #FACING}) must not be covered by a solid
 * face, or nothing can flow through it. The back of it, opposite the mouth, is what connects: to a pipe, or straight
 * to a cylinder head.
 */
public class PortOpeningBlock extends DirectionalBlock implements AirflowPort {

	public static final DirectionProperty FACING = DirectionalBlock.FACING;

	private final PortKind kind;

	public PortOpeningBlock(Properties properties, PortKind kind) {
		super(properties);
		this.kind = kind;
		registerDefaultState(defaultBlockState().setValue(FACING, Direction.UP));
	}

	public PortKind kind() {
		return kind;
	}

	@Override
	public boolean acceptsPipe(BlockState state, Direction side, PortKind kind) {
		return kind == this.kind && side == state.getValue(FACING)
			.getOpposite();
	}

	/** Whether air can get in or out of this opening's mouth. */
	public static boolean isOpen(BlockGetter level, BlockPos pos, BlockState state) {
		Direction mouth = state.getValue(FACING);
		BlockPos front = pos.relative(mouth);
		return !level.getBlockState(front)
			.isFaceSturdy(level, front, mouth.getOpposite());
	}

	@Override
	protected MapCodec<? extends DirectionalBlock> codec() {
		throw new UnsupportedOperationException();
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING, context.getClickedFace());
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
	}

	@Override
	protected BlockState mirror(BlockState state, Mirror mirror) {
		return state.rotate(mirror.getRotation(state.getValue(FACING)));
	}

	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
		super.onPlace(state, level, pos, oldState, movedByPiston);
		if (!oldState.is(this))
			Pipes.attachNeighbours(level, pos, state);
		EngineTopology.markDirty(level, pos);
	}

	@Override
	protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
		super.onRemove(state, level, pos, newState, movedByPiston);
		EngineTopology.markDirty(level, pos);
	}

	@Override
	protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
								   BlockPos neighborPos, boolean movedByPiston) {
		super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
		if (neighborPos.equals(pos.relative(state.getValue(FACING))))
			EngineTopology.markDirty(level, pos);
	}
}
