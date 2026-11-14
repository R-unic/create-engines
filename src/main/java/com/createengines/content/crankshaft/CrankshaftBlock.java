package com.createengines.content.crankshaft;

import com.createengines.content.Encasing;
import com.createengines.engine.topology.EngineTopology;
import com.createengines.registry.CEBlockEntities;
import com.simibubi.create.AllShapes;
import com.simibubi.create.content.kinetics.base.RotatedPillarKineticBlock;
import com.simibubi.create.foundation.block.IBE;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A main journal of the crankshaft: carries rotation along its axis like a shaft. A line of journals and
 * {@link CrankThrowBlock throws} on one axis is one crankshaft, and therefore one engine.
 */
public class CrankshaftBlock extends RotatedPillarKineticBlock implements IBE<CrankshaftBlockEntity> {

	public CrankshaftBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(Encasing.ENCASED, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder);
		builder.add(Encasing.ENCASED);
	}

	@Override
	protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
											  Player player, InteractionHand hand, BlockHitResult hit) {
		ItemInteractionResult result = Encasing.encase(stack, state, level, pos, player);
		return result == ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION
			? super.useItemOn(stack, state, level, pos, player, hand, hit) : result;
	}

	/** A wrench takes the casing off a cased part before it does anything else. */
	@Override
	public InteractionResult onWrenched(BlockState state, UseOnContext context) {
		InteractionResult result = Encasing.uncase(state, context);
		return result != null ? result : super.onWrenched(state, context);
	}

	/** Sneaking with the wrench takes the casing off too, instead of breaking the part inside it. */
	@Override
	public InteractionResult onSneakWrenched(BlockState state, UseOnContext context) {
		InteractionResult result = Encasing.uncase(state, context);
		return result != null ? result : super.onSneakWrenched(state, context);
	}

	public boolean isThrow() {
		return false;
	}

	@Override
	public boolean hasShaftTowards(LevelReader world, BlockPos pos, BlockState state, Direction face) {
		return face.getAxis() == state.getValue(AXIS);
	}

	@Override
	public Axis getRotationAxis(BlockState state) {
		return state.getValue(AXIS);
	}

	@Override
	public RenderShape getRenderShape(BlockState state) {
		return Encasing.renderShape(state);
	}

	@Override
	public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return Encasing.shape(state, AllShapes.EIGHT_VOXEL_POLE.get(state.getValue(AXIS)));
	}

	@Override
	public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
		super.onPlace(state, level, pos, oldState, isMoving);
		EngineTopology.markDirty(level, pos);
	}

	@Override
	public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
		Encasing.dropOnRemove(state, level, pos, newState);
		super.onRemove(state, level, pos, newState, isMoving);
		EngineTopology.markDirty(level, pos);
	}

	@Override
	public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos,
								boolean isMoving) {
		super.neighborChanged(state, level, pos, block, fromPos, isMoving);
		// Flywheels and shafts on the crank's axis add inertia
		if (fromPos.subtract(pos)
			.get(state.getValue(AXIS)) != 0)
			EngineTopology.markDirty(level, pos);
	}

	@Override
	public Class<CrankshaftBlockEntity> getBlockEntityClass() {
		return CrankshaftBlockEntity.class;
	}

	@Override
	public BlockEntityType<? extends CrankshaftBlockEntity> getBlockEntityType() {
		return CEBlockEntities.CRANKSHAFT.get();
	}
}
