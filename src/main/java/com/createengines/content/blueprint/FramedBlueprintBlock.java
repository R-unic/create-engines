package com.createengines.content.blueprint;

import org.jetbrains.annotations.Nullable;

import com.createengines.registry.CEBlockEntities;
import com.createengines.registry.CEComponents;
import com.createengines.registry.CEItems;
import com.createengines.registry.CELang;
import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.block.IBE;

import net.minecraft.ChatFormatting;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

import com.createengines.engine.blueprint.FrameSheet;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A frame for a wall (or a floor or ceiling) that shows a recorded blueprint as its drawing and the figures from its
 * Maker's Plate. Right-click it with a recorded Blueprint to hang it; sneak with an empty hand to take it back; an empty hand
 * otherwise reads it, as the sheet does. A frame that is broken gives up its blueprint. Only for looks: it changes nothing
 * about any engine.
 */
public class FramedBlueprintBlock extends DirectionalBlock implements IWrenchable, IBE<FramedBlueprintBlockEntity> {

	public static final DirectionProperty FACING = DirectionalBlock.FACING;

	/** Joined on the paper's far side down, to the left, up and to the right: the frame runs on into that neighbour. */
	public static final BooleanProperty JOIN_DOWN = BooleanProperty.create("join_down");
	public static final BooleanProperty JOIN_LEFT = BooleanProperty.create("join_left");
	public static final BooleanProperty JOIN_UP = BooleanProperty.create("join_up");
	public static final BooleanProperty JOIN_RIGHT = BooleanProperty.create("join_right");

	/** Frames further than this from the one that changed cannot be in its sheet (a sheet is at most 8 by 8). */
	private static final int GROUP_LIMIT = FrameSheet.MAX_SIDE * FrameSheet.MAX_SIDE + 1;

	private static final VoxelShape[] SHAPES = new VoxelShape[6 * 16];

	static {
		for (Direction facing : Direction.values())
			for (int mask = 0; mask < 16; mask++) {
				float x0 = (mask & 8) != 0 ? 0 : 2, x1 = (mask & 2) != 0 ? 16 : 14;
				float z0 = (mask & 1) != 0 ? 0 : 2, z1 = (mask & 4) != 0 ? 16 : 14;
				SHAPES[facing.ordinal() * 16 + mask] = turnedBox(facing, x0, 0, z0, x1, 1.5f, z1);
			}
	}

	/** A box given for a frame facing up, turned onto {@code facing} exactly as the blockstate turns the model. */
	private static VoxelShape turnedBox(Direction facing, float x0, float y0, float z0, float x1, float y1, float z1) {
		int turnX = facing == Direction.UP ? 0 : facing == Direction.DOWN ? 180 : 90;
		int turnY = facing.getAxis()
			.isVertical() ? 0 : ((int) facing.toYRot() + 180) % 360;
		org.joml.Vector3f a = new org.joml.Vector3f(x0 - 8, y0 - 8, z0 - 8).rotateX((float) -Math.toRadians(turnX))
			.rotateY((float) -Math.toRadians(turnY));
		org.joml.Vector3f b = new org.joml.Vector3f(x1 - 8, y1 - 8, z1 - 8).rotateX((float) -Math.toRadians(turnX))
			.rotateY((float) -Math.toRadians(turnY));
		return Block.box(Math.min(a.x, b.x) + 8, Math.min(a.y, b.y) + 8, Math.min(a.z, b.z) + 8, Math.max(a.x, b.x) + 8,
			Math.max(a.y, b.y) + 8, Math.max(a.z, b.z) + 8);
	}

	/** The join property for the side of the paper that faces {@code side} in the world, or null if it is a face or the back. */
	private static BooleanProperty joinFor(Direction facing, Direction side) {
		if (side == paperDown(facing))
			return JOIN_DOWN;
		if (side == paperRight(facing)
			.getOpposite())
			return JOIN_LEFT;
		if (side == paperDown(facing)
			.getOpposite())
			return JOIN_UP;
		if (side == paperRight(facing))
			return JOIN_RIGHT;
		return null;
	}

	public FramedBlueprintBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(FACING, Direction.UP)
			.setValue(JOIN_DOWN, false)
			.setValue(JOIN_LEFT, false)
			.setValue(JOIN_UP, false)
			.setValue(JOIN_RIGHT, false));
	}

	@Override
	public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
		BlockState state = defaultBlockState().setValue(FACING, context.getClickedFace());
		return canSurvive(state, context.getLevel(), context.getClickedPos()) ? state : null;
	}

	@Override
	protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		BlockState support = level.getBlockState(pos.relative(state.getValue(FACING)
			.getOpposite()));
		return !support.isAir() && !support.canBeReplaced() && support.getFluidState()
			.isEmpty();
	}

	@Override
	protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, BlockPos neighborPos,
								   boolean movedByPiston) {
		super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
		if (level.isClientSide)
			return;
		if (!canSurvive(state, level, pos))
			level.destroyBlock(pos, true);
		else if (!level.getBlockTicks()
			.hasScheduledTick(pos, this))
			level.scheduleTick(pos, this, 1);
	}

	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
		super.onPlace(state, level, pos, oldState, movedByPiston);
		if (!level.isClientSide && !oldState.is(this))
			level.scheduleTick(pos, this, 1);
	}

	/** Frames saved before they could join (or loaded into a changed neighbourhood) settle a moment after they load. */
	@Override
	protected void tick(BlockState state, net.minecraft.server.level.ServerLevel level, BlockPos pos, net.minecraft.util.RandomSource random) {
		settleGroup(level, pos);
	}

	/** True if the block at {@code at} is a frame on the same wall, facing the same way. */
	private static boolean sameWall(Level level, BlockPos at, Direction facing) {
		BlockState there = level.getBlockState(at);
		return there.getBlock() instanceof FramedBlueprintBlock && there.getValue(FACING) == facing;
	}

	/**
	 * Works out which sides of every frame in the group around {@code start} run on into a neighbour: exactly the sides the
	 * renderer will draw as one sheet ({@link FrameSheet}), so the model and the picture always agree. Only the client
	 * is told (no further neighbour updates), so this cannot loop.
	 */
	private static void settleGroup(Level level, BlockPos start) {
		BlockState first = level.getBlockState(start);
		if (!(first.getBlock() instanceof FramedBlueprintBlock))
			return;
		Direction facing = first.getValue(FACING);
		Direction right = paperRight(facing);
		Direction down = paperDown(facing);
		Set<BlockPos> seen = new HashSet<>();
		ArrayDeque<BlockPos> open = new ArrayDeque<>();
		seen.add(start);
		open.add(start);
		// Frames beside the group that have just been left out of it (a removed frame) are settled too
		for (Direction side : new Direction[] { right, right.getOpposite(), down, down.getOpposite() }) {
			BlockPos next = start.relative(side);
			if (sameWall(level, next, facing) && seen.add(next))
				open.add(next);
		}
		int visited = 0;
		while (!open.isEmpty() && visited++ < GROUP_LIMIT) {
			BlockPos at = open.poll();
			for (Direction side : new Direction[] { right, right.getOpposite(), down, down.getOpposite() }) {
				BlockPos next = at.relative(side);
				if (sameWall(level, next, facing) && seen.add(next) && seen.size() <= GROUP_LIMIT * 2)
					open.add(next);
			}
		}
		for (BlockPos at : seen) {
			if (!sameWall(level, at, facing))
				continue;
			FrameSheet.Sheet sheet = FrameSheet.find(cell -> sameWall(level, FramedBlueprintBlockEntity.cell(at, right, down, cell), facing),
				cell -> false);
			BlockState old = level.getBlockState(at);
			BlockState now = old;
			for (Direction side : new Direction[] { right, right.getOpposite(), down, down.getOpposite() })
				now = now.setValue(joinFor(facing, side), sheet.joined() && sameWall(level, at.relative(side), facing));
			if (now != old)
				level.setBlock(at, now, Block.UPDATE_CLIENTS);
		}
	}

	@Override
	protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
											  InteractionHand hand, BlockHitResult hit) {
		RecordedBlueprint offered = BlueprintItem.recorded(stack);
		if (offered == null || !(level.getBlockEntity(pos) instanceof FramedBlueprintBlockEntity frame))
			return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
		if (level.isClientSide)
			return ItemInteractionResult.SUCCESS;
		if (frame.plan() != null) {
			player.displayClientMessage(CELang.translate("blueprint.frame.full")
				.component()
				.copy()
				.withStyle(ChatFormatting.RED), true);
			return ItemInteractionResult.CONSUME;
		}
		frame.hang(offered);
		if (!player.isCreative())
			stack.shrink(1);
		level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 0.8f, 1.0f);
		return ItemInteractionResult.CONSUME;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!(level.getBlockEntity(pos) instanceof FramedBlueprintBlockEntity frame) || frame.plan() == null)
			return InteractionResult.PASS;
		if (!player.isShiftKeyDown()) {
			if (level.isClientSide)
				BlueprintViewer.open(frame.plan());
			return InteractionResult.sidedSuccess(level.isClientSide);
		}
		if (!level.isClientSide) {
			ItemStack back = frame.take();
			if (!player.getInventory()
				.add(back))
				player.drop(back, false);
			level.playSound(null, pos, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 0.8f, 1.0f);
		}
		return InteractionResult.sidedSuccess(level.isClientSide);
	}

	/**
	 * A wrench turns the drawing a quarter turn, on a frame that holds a blueprint or belongs to a sheet that shows one (the
	 * turn is kept by the frame that draws the sheet). A frame with nothing to show is turned as it always was.
	 */
	@Override
	public InteractionResult onWrenched(BlockState state, UseOnContext context) {
		Level level = context.getLevel();
		if (!(level.getBlockEntity(context.getClickedPos()) instanceof FramedBlueprintBlockEntity frame))
			return IWrenchable.super.onWrenched(state, context);
		FramedBlueprintBlockEntity source = frame.sheetSource();
		if (source == null)
			return IWrenchable.super.onWrenched(state, context);
		if (!level.isClientSide) {
			source.turnDisplay();
			IWrenchable.playRotateSound(level, context.getClickedPos());
		}
		return InteractionResult.sidedSuccess(level.isClientSide);
	}

	@Override
	protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
		if (!state.is(newState.getBlock()) && !level.isClientSide && level.getBlockEntity(pos) instanceof FramedBlueprintBlockEntity frame
			&& frame.plan() != null)
			Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, frame.take());
		super.onRemove(state, level, pos, newState, movedByPiston);
	}

	/**
	 * The way the paper reads to the right, in the world, for a frame facing {@code facing}: the same turn the blockstate
	 * and {@link com.createengines.client.FramedBlueprintRenderer} give the face (the paper's x runs to the model's west).
	 */
	public static Direction paperRight(Direction facing) {
		return turned(facing, -1, 0, 0);
	}

	/** The way the paper reads downward, in the world. */
	public static Direction paperDown(Direction facing) {
		return turned(facing, 0, 0, -1);
	}

	private static Direction turned(Direction facing, float x, float y, float z) {
		int turnX = facing == Direction.UP ? 0 : facing == Direction.DOWN ? 180 : 90;
		int turnY = facing.getAxis()
			.isVertical() ? 0 : ((int) facing.toYRot() + 180) % 360;
		org.joml.Vector3f v = new org.joml.Vector3f(x, y, z)
			.rotateX((float) -Math.toRadians(turnX))
			.rotateY((float) -Math.toRadians(turnY));
		return Direction.getNearest(v.x, v.y, v.z);
	}

	/** The blueprint item that holds {@code plan}. */
	static ItemStack blueprintItem(RecordedBlueprint plan) {
		ItemStack stack = new ItemStack(CEItems.BLUEPRINT.get());
		stack.set(CEComponents.BLUEPRINT.get(), plan);
		return stack;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		int mask = (state.getValue(JOIN_DOWN) ? 1 : 0) | (state.getValue(JOIN_LEFT) ? 2 : 0) | (state.getValue(JOIN_UP) ? 4 : 0)
			| (state.getValue(JOIN_RIGHT) ? 8 : 0);
		return SHAPES[state.getValue(FACING)
			.ordinal() * 16 + mask];
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.MODEL;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, JOIN_DOWN, JOIN_LEFT, JOIN_UP, JOIN_RIGHT);
	}

	@Override
	protected MapCodec<? extends DirectionalBlock> codec() {
		throw new UnsupportedOperationException();
	}

	@Override
	public Class<FramedBlueprintBlockEntity> getBlockEntityClass() {
		return FramedBlueprintBlockEntity.class;
	}

	@Override
	public BlockEntityType<? extends FramedBlueprintBlockEntity> getBlockEntityType() {
		return CEBlockEntities.FRAMED_BLUEPRINT.get();
	}
}
