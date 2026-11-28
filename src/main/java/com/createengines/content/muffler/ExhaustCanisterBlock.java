package com.createengines.content.muffler;

import com.createengines.content.PartShapes;
import org.jetbrains.annotations.Nullable;

import com.createengines.content.airflow.InlineDuct;
import com.createengines.content.airflow.Pipes;
import com.createengines.content.airflow.PortKind;
import com.createengines.engine.topology.EngineTopology;
import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.equipment.wrench.IWrenchable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A canister in an exhaust run: the shape, the pipe ends and the wrench turn that the {@link MufflerBlock} and the
 * {@link SootTrapBlock} share. The exhaust pipes join its two ends along {@link #AXIS}. It costs the engine no power and
 * no back-pressure, whatever is in it.
 */
public abstract class ExhaustCanisterBlock extends Block implements InlineDuct, IWrenchable {

	public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.AXIS;

	protected ExhaustCanisterBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(AXIS, Direction.Axis.X));
	}

	@Override
	protected MapCodec<? extends Block> codec() {
		throw new UnsupportedOperationException();
	}

	/** Pipes join the two ends of the canister, which is where the exhaust goes in and out. */
	@Override
	public boolean acceptsPipe(BlockState state, Direction side, PortKind kind) {
		return kind == PortKind.EXHAUST && side.getAxis() == state.getValue(AXIS);
	}

	@Override
	public @Nullable PortKind kindFacingHead(BlockState state, Direction face) {
		return acceptsPipe(state, face, PortKind.EXHAUST) ? PortKind.EXHAUST : null;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(AXIS);
	}

	@Override
	public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(AXIS, context.getClickedFace()
			.getAxis());
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return PartShapes.EXHAUST_CANISTER[state.getValue(AXIS)
			.ordinal()];
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		return switch (rotation) {
			case COUNTERCLOCKWISE_90, CLOCKWISE_90 -> switch (state.getValue(AXIS)) {
				case X -> state.setValue(AXIS, Direction.Axis.Z);
				case Z -> state.setValue(AXIS, Direction.Axis.X);
				default -> state;
			};
			default -> state;
		};
	}

	/** A wrench turns the canister to the next axis, which cuts any pipe that no longer fits. */
	@Override
	public BlockState getRotatedBlockState(BlockState originalState, Direction targetedFace) {
		Direction.Axis[] all = Direction.Axis.values();
		return originalState.setValue(AXIS, all[(originalState.getValue(AXIS)
			.ordinal() + 1) % all.length]);
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
}
