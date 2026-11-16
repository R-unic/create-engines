package com.createengines.content.refinery;

import org.jetbrains.annotations.Nullable;

import com.createengines.engine.sim.ColumnShape;
import com.createengines.registry.CEBlocks;
import com.simibubi.create.api.schematic.requirement.SpecialBlockItemRequirement;
import com.simibubi.create.content.schematics.requirement.ItemRequirement;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A slanted joint between two layers of a leaning refining column. The column places it when it forms and it goes when
 * the column breaks: it is a real block with no item, it costs nothing in a schematic, and it turns and mirrors with
 * one. {@link #FACING} is the way the column leans there. A lower joint fills the block under the upper layer's overhang
 * (a brace from the lower layer's wall up to the upper layer's underside); an upper joint fills the block above the
 * lower layer, beside the upper one (a wedge up to its wall). Both are solid and slanted at 45 degrees.
 */
public class ColumnJointBlock extends Block implements SpecialBlockItemRequirement {

	public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
	/** False for the brace under an overhang, true for the wedge above the lower layer. */
	public static final BooleanProperty UPPER = BooleanProperty.create("upper");

	private static final VoxelShape[][] SHAPES = new VoxelShape[2][4];

	static {
		for (int upper = 0; upper < 2; upper++)
			for (Direction facing : Direction.Plane.HORIZONTAL)
				SHAPES[upper][facing.get2DDataValue()] = shape(upper == 1, facing);
	}

	public ColumnJointBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(FACING, Direction.SOUTH)
			.setValue(UPPER, false));
	}

	/** Four steps along the slope, built for a lean to the south and turned to the way it faces. */
	private static VoxelShape shape(boolean upper, Direction facing) {
		VoxelShape shape = Shapes.empty();
		for (int k = 0; k < 4; k++) {
			double z0 = 4 * k, z1 = 4 * k + 4;
			double y0 = upper ? 0 : 14 - 4 * k, y1 = upper ? 4 * k + 2 : 16;
			double x0 = 0, x1 = 16;
			double[] a = turn(x0, z0, facing), b = turn(x1, z1, facing);
			shape = Shapes.or(shape, Block.box(Math.min(a[0], b[0]), y0, Math.min(a[1], b[1]), Math.max(a[0], b[0]), y1,
				Math.max(a[1], b[1])));
		}
		return shape.optimize();
	}

	/** Turns a point of a south-facing joint to another facing, round the block centre. */
	private static double[] turn(double x, double z, Direction facing) {
		return switch (facing) {
			case EAST -> new double[] {z, 16 - x};
			case WEST -> new double[] {16 - z, x};
			case NORTH -> new double[] {16 - x, 16 - z};
			default -> new double[] {x, z};
		};
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, UPPER);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPES[state.getValue(UPPER) ? 1 : 0][state.getValue(FACING)
			.get2DDataValue()];
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
	}

	@Override
	protected BlockState mirror(BlockState state, Mirror mirror) {
		return state.rotate(mirror.getRotation(state.getValue(FACING)));
	}

	/** The block this joint hangs from beside it (a lower joint) or below it (an upper one), and the one it meets. */
	private static BlockPos holder(BlockPos pos, BlockState state) {
		return state.getValue(UPPER) ? pos.below() : pos.relative(state.getValue(FACING)
			.getOpposite());
	}

	private static BlockPos meets(BlockPos pos, BlockState state) {
		return state.getValue(UPPER) ? pos.relative(state.getValue(FACING)) : pos.above();
	}

	/** Whether the two sections this joint joins are both still there. */
	public static boolean supported(LevelReader level, BlockPos pos, BlockState state) {
		return level.getBlockState(holder(pos, state))
			.is(CEBlocks.COLUMN_SECTION.get())
			&& level.getBlockState(meets(pos, state))
				.is(CEBlocks.COLUMN_SECTION.get());
	}

	/** What {@link ColumnShape.World#jointLink} says for this joint: where its holder is. */
	public static int link(BlockState state) {
		if (state.getValue(UPPER))
			return 0;
		Direction back = state.getValue(FACING)
			.getOpposite();
		return switch (back) {
			case EAST -> 1;
			case WEST -> 2;
			case SOUTH -> 3;
			default -> 4;
		};
	}

	/** The state for a joint the shape asks for: {@code dir} indexes {@link ColumnShape#STEPS}. */
	public static BlockState stateFor(ColumnShape.Joint joint) {
		Direction facing = switch (joint.dir()) {
			case 0 -> Direction.EAST;
			case 1 -> Direction.WEST;
			case 2 -> Direction.SOUTH;
			default -> Direction.NORTH;
		};
		return CEBlocks.COLUMN_JOINT.getDefaultState()
			.setValue(FACING, facing)
			.setValue(UPPER, joint.upper());
	}

	@Override
	protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level,
									 BlockPos pos, BlockPos neighborPos) {
		if ((neighborPos.equals(holder(pos, state)) || neighborPos.equals(meets(pos, state)))
			&& !neighborState.is(CEBlocks.COLUMN_SECTION.get()))
			return net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
		return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
	}

	/** A joint left hanging (its sections went while the chunk was unloaded) lets go the next time it is ticked. */
	@Override
	protected boolean isRandomlyTicking(BlockState state) {
		return true;
	}

	@Override
	protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (!supported(level, pos, state))
			level.removeBlock(pos, false);
	}

	@Override
	public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
		return CEBlocks.COLUMN_SECTION.asStack();
	}

	@Override
	public ItemRequirement getRequiredItems(BlockState state, @Nullable net.minecraft.world.level.block.entity.BlockEntity blockEntity) {
		return ItemRequirement.NONE;
	}
}
