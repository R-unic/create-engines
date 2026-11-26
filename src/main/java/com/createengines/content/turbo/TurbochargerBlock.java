package com.createengines.content.turbo;

import com.createengines.content.PartShapes;
import org.jetbrains.annotations.Nullable;

import com.createengines.content.airflow.AirflowPort;
import com.createengines.content.airflow.InlineDuct;
import com.createengines.content.airflow.Pipes;
import com.createengines.content.airflow.PortKind;
import com.createengines.engine.topology.EngineTopology;
import com.createengines.registry.CEBlockEntities;
import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.block.IBE;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * An exhaust-driven compressor that sits in both an intake run and an exhaust run. {@link #FACING} points along its
 * shaft towards the compressor; the turbine is on the other end. Pipes join it as they join anything that is an
 * {@link AirflowPort}: an intake pipe on its compressor face or on any side, an exhaust pipe on its turbine face or on
 * any side (a side face takes whichever kind arrives, since a neighbour can only be one). The engine scanner follows
 * pipes through it, so everything the intake run feeds is boosted and everything the exhaust run drains drives it.
 */
public class TurbochargerBlock extends DirectionalBlock implements InlineDuct, IWrenchable, IBE<TurbochargerBlockEntity> {

	public static final DirectionProperty FACING = DirectionalBlock.FACING;

	private static final VoxelShape[] SHAPES = PartShapes.TURBOCHARGER;

	public TurbochargerBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(FACING, Direction.UP));
	}

	/** Whether the turbine has a ring of variable vanes. */
	public boolean variableGeometry() {
		return false;
	}

	/** A Service Kit sets right a turbo's light wear (the wear mode's). */
	@Override
	protected net.minecraft.world.ItemInteractionResult useItemOn(net.minecraft.world.item.ItemStack stack, BlockState state, Level level,
		BlockPos pos, net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand,
		net.minecraft.world.phys.BlockHitResult hit) {
		net.minecraft.world.ItemInteractionResult worn = com.createengines.content.WearService.onTurbo(stack, level, pos, player, hand);
		return worn != net.minecraft.world.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION ? worn
			: super.useItemOn(stack, state, level, pos, player, hand, hit);
	}

	@Override
	public boolean acceptsPipe(BlockState state, Direction side, PortKind kind) {
		Direction facing = state.getValue(FACING);
		if (side.getAxis() != facing.getAxis())
			return true;
		return kind == PortKind.INTAKE ? side == facing : side == facing.getOpposite();
	}

	@Override
	public @Nullable PortKind kindFacingHead(BlockState state, Direction face) {
		Direction facing = state.getValue(FACING);
		if (face == facing)
			return PortKind.INTAKE;
		return face == facing.getOpposite() ? PortKind.EXHAUST : null;
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
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPES[state.getValue(FACING)
			.ordinal()];
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
	}

	@Override
	protected BlockState mirror(BlockState state, Mirror mirror) {
		return state.rotate(mirror.getRotation(state.getValue(FACING)));
	}

	/** A wrench turns the compressor to the next face, which cuts any pipe that no longer fits. */
	@Override
	public BlockState getRotatedBlockState(BlockState originalState, Direction targetedFace) {
		Direction[] all = Direction.values();
		return originalState.setValue(FACING, all[(originalState.getValue(FACING)
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

	@Override
	public Class<TurbochargerBlockEntity> getBlockEntityClass() {
		return TurbochargerBlockEntity.class;
	}

	@Override
	public BlockEntityType<? extends TurbochargerBlockEntity> getBlockEntityType() {
		return CEBlockEntities.TURBOCHARGER.get();
	}
}
