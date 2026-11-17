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

	private static final VoxelShape[] SHAPES = new VoxelShape[6];

	static {
		SHAPES[Direction.UP.ordinal()] = Block.box(2, 0, 2, 14, 1.5, 14);
		SHAPES[Direction.DOWN.ordinal()] = Block.box(2, 14.5, 2, 14, 16, 14);
		SHAPES[Direction.NORTH.ordinal()] = Block.box(2, 2, 14.5, 14, 14, 16);
		SHAPES[Direction.SOUTH.ordinal()] = Block.box(2, 2, 0, 14, 14, 1.5);
		SHAPES[Direction.EAST.ordinal()] = Block.box(0, 2, 2, 1.5, 14, 14);
		SHAPES[Direction.WEST.ordinal()] = Block.box(14.5, 2, 2, 16, 14, 14);
	}

	public FramedBlueprintBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(FACING, Direction.UP));
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
		if (!level.isClientSide && !canSurvive(state, level, pos))
			level.destroyBlock(pos, true);
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
		return SHAPES[state.getValue(FACING)
			.ordinal()];
	}

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
	public Class<FramedBlueprintBlockEntity> getBlockEntityClass() {
		return FramedBlueprintBlockEntity.class;
	}

	@Override
	public BlockEntityType<? extends FramedBlueprintBlockEntity> getBlockEntityType() {
		return CEBlockEntities.FRAMED_BLUEPRINT.get();
	}
}
