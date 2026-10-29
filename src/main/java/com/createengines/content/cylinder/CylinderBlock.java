package com.createengines.content.cylinder;

import org.jetbrains.annotations.Nullable;

import com.createengines.content.PartInstallation;
import com.createengines.content.PartInstallation.Slot;
import com.createengines.engine.topology.EngineTopology;
import com.createengines.registry.CEBlockEntities;
import com.createengines.registry.CEItems;
import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.block.IBE;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A cylinder bore. {@link #FACING} points along the bore, away from the crank throw it must sit on. It needs a piston
 * and a connecting rod installed before it does anything.
 */
public class CylinderBlock extends DirectionalBlock implements IBE<CylinderBlockEntity>, IWrenchable {

	public static final BooleanProperty HAS_PISTON = BooleanProperty.create("piston");
	public static final BooleanProperty HAS_ROD = BooleanProperty.create("connecting_rod");

	private static final Slot[] PARTS =
		{new Slot(HAS_PISTON, CEItems.PISTON), new Slot(HAS_ROD, CEItems.CONNECTING_ROD)};

	public CylinderBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(FACING, Direction.UP)
			.setValue(HAS_PISTON, false)
			.setValue(HAS_ROD, false));
	}

	@Override
	protected MapCodec<? extends DirectionalBlock> codec() {
		throw new UnsupportedOperationException();
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, HAS_PISTON, HAS_ROD);
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
	protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
											  Player player, InteractionHand hand, BlockHitResult hit) {
		return PartInstallation.tryInstall(stack, state, level, pos, player, PARTS);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
											   BlockHitResult hit) {
		if (PartInstallation.tryRemove(state, level, pos, player, PARTS))
			return InteractionResult.sidedSuccess(level.isClientSide);
		return InteractionResult.PASS;
	}

	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
		super.onPlace(state, level, pos, oldState, movedByPiston);
		EngineTopology.markDirty(level, pos);
	}

	@Override
	protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
		PartInstallation.dropParts(state, level, pos, newState, PARTS);
		IBE.onRemove(state, level, pos, newState);
		EngineTopology.markDirty(level, pos);
	}

	@Override
	public Class<CylinderBlockEntity> getBlockEntityClass() {
		return CylinderBlockEntity.class;
	}

	@Override
	public BlockEntityType<? extends CylinderBlockEntity> getBlockEntityType() {
		return CEBlockEntities.CYLINDER.get();
	}
}
