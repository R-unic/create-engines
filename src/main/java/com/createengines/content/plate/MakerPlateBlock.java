package com.createengines.content.plate;

import org.jetbrains.annotations.Nullable;

import com.createengines.content.dyno.DynoBlockEntity;
import com.createengines.engine.sim.PlateFigures;
import com.createengines.registry.CEBlockEntities;
import com.createengines.registry.CELang;
import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.block.IBE;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
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
 * A brass plate screwed to the side of an engine. Name it on an anvil before placing it and its name is stamped on;
 * whoever places it is put down as the builder. An empty hand on the plate stamps the figures of the engine it is fixed
 * to, taken from the same measured curve the Dynamometer reads: peak torque and peak power, each with the speed it
 * comes at. A curve that is still being measured is turned away, so a plate never carries a half-finished run. Only for
 * looks and bragging: it changes nothing about the engine.
 */
public class MakerPlateBlock extends DirectionalBlock implements IWrenchable, IBE<MakerPlateBlockEntity> {

	public static final DirectionProperty FACING = DirectionalBlock.FACING;

	private static final VoxelShape[] SHAPES = new VoxelShape[6];

	static {
		SHAPES[Direction.UP.ordinal()] = Block.box(2, 0, 3.5, 14, 1.5, 12.5);
		SHAPES[Direction.DOWN.ordinal()] = Block.box(2, 14.5, 3.5, 14, 16, 12.5);
		SHAPES[Direction.NORTH.ordinal()] = Block.box(2, 3.5, 14.5, 14, 12.5, 16);
		SHAPES[Direction.SOUTH.ordinal()] = Block.box(2, 3.5, 0, 14, 12.5, 1.5);
		SHAPES[Direction.EAST.ordinal()] = Block.box(0, 3.5, 2, 1.5, 12.5, 14);
		SHAPES[Direction.WEST.ordinal()] = Block.box(14.5, 3.5, 2, 16, 12.5, 14);
	}

	public MakerPlateBlock(Properties properties) {
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
		BlockPos behind = pos.relative(state.getValue(FACING)
			.getOpposite());
		BlockState support = level.getBlockState(behind);
		// Engine parts are not full cubes, so any solid thing it can be screwed to will do
		return !support.isAir() && !support.canBeReplaced() && support.getFluidState()
			.isEmpty();
	}

	@Override
	protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
								   BlockPos neighborPos, boolean movedByPiston) {
		super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
		if (!level.isClientSide && !canSurvive(state, level, pos))
			level.destroyBlock(pos, true);
	}

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
		super.setPlacedBy(level, pos, state, placer, stack);
		if (level.isClientSide || !(level.getBlockEntity(pos) instanceof MakerPlateBlockEntity plate))
			return;
		String name = stack.has(DataComponents.CUSTOM_NAME) ? stack.getHoverName()
			.getString() : "";
		plate.setNameAndBuilder(name, placer == null ? "" : placer.getName()
			.getString());
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
											   BlockHitResult hit) {
		if (level.isClientSide)
			return InteractionResult.SUCCESS;
		if (!(level.getBlockEntity(pos) instanceof MakerPlateBlockEntity plate))
			return InteractionResult.PASS;
		DynoBlockEntity.Readout readout = DynoBlockEntity.readEngineAt(level, pos.relative(state.getValue(FACING)
			.getOpposite()));
		if (readout == null) {
			player.displayClientMessage(CELang.translate("plate.no_engine")
				.component(), true);
			return InteractionResult.CONSUME;
		}
		PlateFigures figures = readout.complete() ? PlateFigures.of(readout.rpm(), readout.torque()) : null;
		if (figures == null) {
			player.displayClientMessage(CELang.translate(readout.complete() ? "plate.no_power" : "plate.measuring")
				.component(), true);
			return InteractionResult.CONSUME;
		}
		plate.stamp(figures);
		level.playSound(null, pos, net.minecraft.sounds.SoundEvents.ANVIL_PLACE, net.minecraft.sounds.SoundSource.BLOCKS,
			0.4f, 1.6f);
		player.displayClientMessage(CELang.translate("plate.stamped", MakerPlateBlockEntity.compact(figures.peakPower()),
				figures.powerRpm())
			.component(), true);
		return InteractionResult.CONSUME;
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
	public Class<MakerPlateBlockEntity> getBlockEntityClass() {
		return MakerPlateBlockEntity.class;
	}

	@Override
	public BlockEntityType<? extends MakerPlateBlockEntity> getBlockEntityType() {
		return CEBlockEntities.MAKER_PLATE.get();
	}
}
