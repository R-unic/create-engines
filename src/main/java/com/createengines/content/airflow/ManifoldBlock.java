package com.createengines.content.airflow;

import java.util.EnumMap;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import com.createengines.content.Encasing;
import com.createengines.engine.topology.EngineTopology;
import com.simibubi.create.content.equipment.wrench.IWrenchable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A length of intake or exhaust pipe. It runs out of each face it is {@link #CONNECTIONS connected} through, like a
 * vehicle's exhaust: from a head, through a bend or a join, to an {@link PortOpeningBlock opening}.
 * <p>
 * A pipe connects to everything beside it that will take it when it is placed, and anything placed beside it later
 * opens it towards itself. A wrench cuts or restores the connection nearest the point clicked, so two runs can lie
 * side by side without joining, and the pipe's own connection state is kept on the pipe, not recomputed.
 */
public class ManifoldBlock extends Block implements AirflowPort, IWrenchable {

	/** Open ends, by direction. */
	public static final Map<Direction, BooleanProperty> CONNECTIONS = connections();

	private static final VoxelShape CORE = Block.box(5, 5, 5, 11, 11, 11);
	private static final Map<Direction, VoxelShape> ARMS = new EnumMap<>(Direction.class);
	private static final VoxelShape[] SHAPES = new VoxelShape[1 << 6];

	static {
		ARMS.put(Direction.UP, Shapes.or(Block.box(5, 11, 5, 11, 14, 11), Block.box(4, 14, 4, 12, 16, 12)));
		ARMS.put(Direction.DOWN, Shapes.or(Block.box(5, 2, 5, 11, 5, 11), Block.box(4, 0, 4, 12, 2, 12)));
		ARMS.put(Direction.NORTH, Shapes.or(Block.box(5, 5, 2, 11, 11, 5), Block.box(4, 4, 0, 12, 12, 2)));
		ARMS.put(Direction.SOUTH, Shapes.or(Block.box(5, 5, 11, 11, 11, 14), Block.box(4, 4, 14, 12, 12, 16)));
		ARMS.put(Direction.EAST, Shapes.or(Block.box(11, 5, 5, 14, 11, 11), Block.box(14, 4, 4, 16, 12, 12)));
		ARMS.put(Direction.WEST, Shapes.or(Block.box(2, 5, 5, 5, 11, 11), Block.box(0, 4, 4, 2, 12, 12)));
	}

	private final PortKind kind;

	public ManifoldBlock(Properties properties, PortKind kind) {
		super(properties);
		this.kind = kind;
		BlockState state = defaultBlockState();
		for (BooleanProperty property : CONNECTIONS.values())
			state = state.setValue(property, false);
		registerDefaultState(state.setValue(Encasing.ENCASED, false));
	}

	private static Map<Direction, BooleanProperty> connections() {
		Map<Direction, BooleanProperty> map = new EnumMap<>(Direction.class);
		map.put(Direction.NORTH, BlockStateProperties.NORTH);
		map.put(Direction.EAST, BlockStateProperties.EAST);
		map.put(Direction.SOUTH, BlockStateProperties.SOUTH);
		map.put(Direction.WEST, BlockStateProperties.WEST);
		map.put(Direction.UP, BlockStateProperties.UP);
		map.put(Direction.DOWN, BlockStateProperties.DOWN);
		return map;
	}

	public PortKind kind() {
		return kind;
	}

	@Override
	public boolean acceptsPipe(BlockState state, Direction side, PortKind kind) {
		return kind == this.kind;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		CONNECTIONS.values()
			.forEach(builder::add);
		builder.add(Encasing.ENCASED);
	}

	@Override
	public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
		BlockState state = defaultBlockState();
		for (Direction direction : Direction.values()) {
			BlockState next = context.getLevel()
				.getBlockState(context.getClickedPos()
					.relative(direction));
			if (Pipes.accepts(next, direction.getOpposite(), kind))
				state = state.setValue(CONNECTIONS.get(direction), true);
		}
		return state;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		int mask = 0;
		for (Direction direction : Direction.values())
			if (state.getValue(CONNECTIONS.get(direction)))
				mask |= 1 << direction.ordinal();
		VoxelShape shape = SHAPES[mask];
		if (shape == null) {
			shape = CORE;
			for (Direction direction : Direction.values())
				if ((mask & 1 << direction.ordinal()) != 0)
					shape = Shapes.or(shape, ARMS.get(direction));
			SHAPES[mask] = shape;
		}
		return Encasing.shape(state, shape);
	}

	/** Engine Casing goes over a plain manifold, not over a throttle or a sensor, which have a face to show. */
	@Override
	protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
											  Player player, InteractionHand hand, BlockHitResult hit) {
		if (getClass() != ManifoldBlock.class)
			return super.useItemOn(stack, state, level, pos, player, hand, hit);
		ItemInteractionResult result = Encasing.encase(stack, state, level, pos, player);
		return result == ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION
			? super.useItemOn(stack, state, level, pos, player, hand, hit) : result;
	}

	/** Sneaking with the wrench takes the casing off too, instead of breaking the pipe inside it. */
	@Override
	public InteractionResult onSneakWrenched(BlockState state, UseOnContext context) {
		InteractionResult result = Encasing.uncase(state, context);
		return result != null ? result : IWrenchable.super.onSneakWrenched(state, context);
	}

	@Override
	protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbourState,
									 LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
		BooleanProperty property = CONNECTIONS.get(direction);
		// Pipes only ever close here: an end that has lost what it joined. Opening one is up to whoever arrives.
		if (state.getValue(property) && !Pipes.accepts(neighbourState, direction.getOpposite(), kind))
			return state.setValue(property, false);
		return state;
	}

	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
		super.onPlace(state, level, pos, oldState, movedByPiston);
		if (!level.isClientSide && !oldState.is(this))
			for (Direction direction : Direction.values()) {
				if (!state.getValue(CONNECTIONS.get(direction)))
					continue;
				BlockPos at = pos.relative(direction);
				BlockState pipe = level.getBlockState(at);
				BooleanProperty back = CONNECTIONS.get(direction.getOpposite());
				if (pipe.getBlock() instanceof ManifoldBlock && !pipe.getValue(back))
					level.setBlock(at, pipe.setValue(back, true), Block.UPDATE_ALL);
			}
		EngineTopology.markDirty(level, pos);
	}

	@Override
	protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
		Encasing.dropOnRemove(state, level, pos, newState);
		super.onRemove(state, level, pos, newState, movedByPiston);
		EngineTopology.markDirty(level, pos);
	}

	/** Cuts or restores the connection on whichever side of the pipe was clicked. */
	@Override
	public InteractionResult onWrenched(BlockState state, UseOnContext context) {
		InteractionResult uncased = Encasing.uncase(state, context);
		if (uncased != null)
			return uncased;
		Level level = context.getLevel();
		BlockPos pos = context.getClickedPos();
		Vec3 offset = context.getClickLocation()
			.subtract(Vec3.atCenterOf(pos));
		Direction side = Direction.getNearest(offset.x, offset.y, offset.z);
		BooleanProperty property = CONNECTIONS.get(side);
		BlockPos at = pos.relative(side);
		BlockState next = level.getBlockState(at);
		boolean open = state.getValue(property);
		if (!open && !Pipes.accepts(next, side.getOpposite(), kind))
			return InteractionResult.FAIL;
		if (!level.isClientSide) {
			level.setBlock(pos, state.setValue(property, !open), Block.UPDATE_ALL);
			if (next.getBlock() instanceof ManifoldBlock) {
				BooleanProperty back = CONNECTIONS.get(side.getOpposite());
				if (next.getValue(back) == open)
					level.setBlock(at, next.setValue(back, !open), Block.UPDATE_ALL);
			}
			IWrenchable.playRotateSound(level, pos);
		}
		return InteractionResult.sidedSuccess(level.isClientSide);
	}
}
