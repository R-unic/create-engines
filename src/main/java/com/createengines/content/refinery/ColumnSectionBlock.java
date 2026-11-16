package com.createengines.content.refinery;

import org.jetbrains.annotations.Nullable;

import com.createengines.registry.CEBlocks;
import com.simibubi.create.content.equipment.wrench.IWrenchable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * A section of a refining column. Its sight window glows while the column is working; the column sets that.
 * <p>
 * Sections join diagonally by themselves, the way Create's chutes do, with no block in between. A section whose upper
 * neighbour leans away (nothing above it, a section above and to one side) grows a wedge arm on its top towards that
 * neighbour ({@link #LEAN_UP}); the section that leans grows a brace under itself back towards the one below
 * ({@link #LEAN_DOWN}). Both arms are part of the section's own model, and they are drawn into the empty block above or
 * below it, which is why a leaning column needs that space clear. The two properties follow the neighbours as they are
 * placed and broken, and are only looks: the column's shape is read from the blocks.
 */
public class ColumnSectionBlock extends Block implements IWrenchable {

	public static final BooleanProperty ACTIVE = BooleanProperty.create("active");
	public static final EnumProperty<Lean> LEAN_UP = EnumProperty.create("lean_up", Lean.class);
	public static final EnumProperty<Lean> LEAN_DOWN = EnumProperty.create("lean_down", Lean.class);

	/** Which side a diagonal neighbour is on. */
	public enum Lean implements StringRepresentable {
		NONE(null), NORTH(Direction.NORTH), EAST(Direction.EAST), SOUTH(Direction.SOUTH), WEST(Direction.WEST);

		private final @Nullable Direction direction;

		Lean(@Nullable Direction direction) {
			this.direction = direction;
		}

		public @Nullable Direction direction() {
			return direction;
		}

		public static Lean of(Direction direction) {
			for (Lean lean : values())
				if (lean.direction == direction)
					return lean;
			return NONE;
		}

		@Override
		public String getSerializedName() {
			return name().toLowerCase(java.util.Locale.ROOT);
		}
	}

	public ColumnSectionBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(ACTIVE, false)
			.setValue(LEAN_UP, Lean.NONE)
			.setValue(LEAN_DOWN, Lean.NONE));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(ACTIVE, LEAN_UP, LEAN_DOWN);
	}

	private static boolean isSection(LevelAccessor level, BlockPos pos) {
		return level.getBlockState(pos)
			.is(CEBlocks.COLUMN_SECTION.get());
	}

	/** Whether this block is a part of a column that a section stacks straight on (so it needs no arm to reach it). */
	private static boolean straightPart(LevelAccessor level, BlockPos pos) {
		return level.getBlockState(pos)
			.getBlock() instanceof ColumnPortBlock || isSection(level, pos);
	}

	/** The state with its two arms set from the blocks around {@code pos}. */
	public static BlockState withArms(LevelAccessor level, BlockPos pos, BlockState state) {
		return state.setValue(LEAN_UP, arm(level, pos, 1))
			.setValue(LEAN_DOWN, arm(level, pos, -1));
	}

	/** The side a section one layer up ({@code dy} 1) or down ({@code dy} -1) is on, if the block straight there is not one. */
	private static Lean arm(LevelAccessor level, BlockPos pos, int dy) {
		BlockPos straight = pos.above(dy);
		if (straightPart(level, straight))
			return Lean.NONE;
		for (Direction side : Direction.Plane.HORIZONTAL)
			if (isSection(level, straight.relative(side)))
				return Lean.of(side);
		return Lean.NONE;
	}

	@Override
	public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
		return withArms(context.getLevel(), context.getClickedPos(), defaultBlockState());
	}

	/** A change straight above or below can add or take away an arm. */
	@Override
	protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level,
									 BlockPos pos, BlockPos neighborPos) {
		if (direction.getAxis() == Direction.Axis.Y)
			return withArms(level, pos, state);
		return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
	}

	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
		super.onPlace(state, level, pos, oldState, movedByPiston);
		if (!oldState.is(this))
			refreshAround(level, pos);
	}

	@Override
	protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
		super.onRemove(state, level, pos, newState, movedByPiston);
		if (!state.is(newState.getBlock()))
			refreshAround(level, pos);
	}

	/** Sections diagonally up and down from here, which a block straight up or down does not tell about a change. */
	private static void refreshAround(Level level, BlockPos pos) {
		if (level.isClientSide)
			return;
		for (Direction side : Direction.Plane.HORIZONTAL)
			for (int dy = -1; dy <= 1; dy += 2) {
				BlockPos other = pos.relative(side)
					.above(dy);
				if (!level.isLoaded(other))
					continue;
				BlockState have = level.getBlockState(other);
				if (!have.is(CEBlocks.COLUMN_SECTION.get()))
					continue;
				BlockState want = withArms(level, other, have);
				if (want != have)
					level.setBlock(other, want, 2);
			}
	}

	private static Lean turned(Lean lean, java.util.function.UnaryOperator<Direction> turn) {
		return lean.direction() == null ? lean : Lean.of(turn.apply(lean.direction()));
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(LEAN_UP, turned(state.getValue(LEAN_UP), rotation::rotate))
			.setValue(LEAN_DOWN, turned(state.getValue(LEAN_DOWN), rotation::rotate));
	}

	@Override
	protected BlockState mirror(BlockState state, Mirror mirror) {
		return state.setValue(LEAN_UP, turned(state.getValue(LEAN_UP), mirror::mirror))
			.setValue(LEAN_DOWN, turned(state.getValue(LEAN_DOWN), mirror::mirror));
	}
}
