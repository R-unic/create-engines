package com.createengines.content.raincap;

import org.jetbrains.annotations.Nullable;

import com.createengines.content.airflow.PortKind;
import com.createengines.content.airflow.PortOpeningBlock;
import com.createengines.registry.CEBlockEntities;
import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.block.IBE;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A hinged cap over the end of an exhaust outlet, for looks only. It is placed on the outlet's mouth, flaps with each
 * exhaust pulse that leaves the pipe, and drops shut when the engine stops (see {@link RainCapRenderer}). It is no
 * seal: the exhaust leaves from under it as before, and it is not a full face, so it never counts as covering the
 * mouth. It needs the outlet it sits on, and is gone with it.
 */
public class RainCapBlock extends DirectionalBlock implements IWrenchable, IBE<RainCapBlockEntity> {

	public static final DirectionProperty FACING = DirectionalBlock.FACING;

	/** The hood's footprint on the mouth, the way it faces. */
	private static final VoxelShape[] SHAPES = new VoxelShape[6];

	static {
		SHAPES[Direction.UP.ordinal()] = Block.box(3.5, 0, 3.5, 12.5, 3, 12.5);
		SHAPES[Direction.DOWN.ordinal()] = Block.box(3.5, 13, 3.5, 12.5, 16, 12.5);
		SHAPES[Direction.NORTH.ordinal()] = Block.box(3.5, 3.5, 13, 12.5, 12.5, 16);
		SHAPES[Direction.SOUTH.ordinal()] = Block.box(3.5, 3.5, 0, 12.5, 12.5, 3);
		SHAPES[Direction.EAST.ordinal()] = Block.box(0, 3.5, 3.5, 3, 12.5, 12.5);
		SHAPES[Direction.WEST.ordinal()] = Block.box(13, 3.5, 3.5, 16, 12.5, 12.5);
	}

	public RainCapBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(FACING, Direction.UP));
	}

	/** Whether {@code pos} faces the mouth of an exhaust outlet that points the same way as {@code facing}. */
	public static boolean sitsOnOutlet(LevelReader level, BlockPos pos, Direction facing) {
		BlockState behind = level.getBlockState(pos.relative(facing.getOpposite()));
		return behind.getBlock() instanceof PortOpeningBlock outlet && outlet.kind() == PortKind.EXHAUST
			&& behind.getValue(PortOpeningBlock.FACING) == facing;
	}

	@Override
	public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
		Direction face = context.getClickedFace();
		return sitsOnOutlet(context.getLevel(), context.getClickedPos(), face) ? defaultBlockState().setValue(FACING, face)
			: null;
	}

	@Override
	protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		return sitsOnOutlet(level, pos, state.getValue(FACING));
	}

	@Override
	protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
								   BlockPos neighborPos, boolean movedByPiston) {
		super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
		if (!level.isClientSide && !canSurvive(state, level, pos))
			level.destroyBlock(pos, true);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPES[state.getValue(FACING)
			.ordinal()];
	}

	/** The hood is drawn from the block model and its flap by the renderer. */
	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.MODEL;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	protected MapCodec<? extends DirectionalBlock> codec() {
		throw new UnsupportedOperationException();
	}

	@Override
	public Class<RainCapBlockEntity> getBlockEntityClass() {
		return RainCapBlockEntity.class;
	}

	@Override
	public BlockEntityType<? extends RainCapBlockEntity> getBlockEntityType() {
		return CEBlockEntities.RAIN_CAP.get();
	}
}
