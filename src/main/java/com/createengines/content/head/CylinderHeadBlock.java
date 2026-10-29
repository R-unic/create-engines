package com.createengines.content.head;

import org.jetbrains.annotations.Nullable;

import com.createengines.content.PartInstallation;
import com.createengines.content.PartInstallation.Slot;
import com.createengines.content.airflow.AirflowPort;
import com.createengines.content.airflow.Pipes;
import com.createengines.content.airflow.PortKind;
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
 * Closes the end of a cylinder ({@link #FACING} points away from it) and holds its valves, unit injector and, in a
 * spark engine, spark plug. Its other faces are where the engine is plumbed: an intake pipe or air intake on one, an
 * exhaust on another, a cam lobe on a third. Heads touching each other share fuel through a common gallery.
 */
public class CylinderHeadBlock extends DirectionalBlock
	implements IBE<CylinderHeadBlockEntity>, IWrenchable, AirflowPort {

	public static final BooleanProperty HAS_INJECTOR = BooleanProperty.create("injector");
	public static final BooleanProperty HAS_SPARK_PLUG = BooleanProperty.create("spark_plug");

	private static final Slot[] PARTS =
		{new Slot(HAS_INJECTOR, CEItems.FUEL_INJECTOR), new Slot(HAS_SPARK_PLUG, CEItems.SPARK_PLUG)};

	public CylinderHeadBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(FACING, Direction.UP)
			.setValue(HAS_INJECTOR, false)
			.setValue(HAS_SPARK_PLUG, false));
	}

	@Override
	protected MapCodec<? extends DirectionalBlock> codec() {
		throw new UnsupportedOperationException();
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, HAS_INJECTOR, HAS_SPARK_PLUG);
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

	/** Any face but the cylinder's takes a pipe: an intake on one, an exhaust on another. */
	@Override
	public boolean acceptsPipe(BlockState state, Direction side, PortKind kind) {
		return side != state.getValue(FACING)
			.getOpposite();
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
		PartInstallation.dropParts(state, level, pos, newState, PARTS);
		IBE.onRemove(state, level, pos, newState);
		EngineTopology.markDirty(level, pos);
	}

	@Override
	public Class<CylinderHeadBlockEntity> getBlockEntityClass() {
		return CylinderHeadBlockEntity.class;
	}

	@Override
	public BlockEntityType<? extends CylinderHeadBlockEntity> getBlockEntityType() {
		return CEBlockEntities.CYLINDER_HEAD.get();
	}
}
