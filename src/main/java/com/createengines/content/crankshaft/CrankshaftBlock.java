package com.createengines.content.crankshaft;

import com.createengines.engine.topology.EngineTopology;
import com.createengines.registry.CEBlockEntities;
import com.simibubi.create.AllShapes;
import com.simibubi.create.content.kinetics.base.RotatedPillarKineticBlock;
import com.simibubi.create.foundation.block.IBE;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
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
		return RenderShape.ENTITYBLOCK_ANIMATED;
	}

	@Override
	public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return AllShapes.EIGHT_VOXEL_POLE.get(state.getValue(AXIS));
	}

	@Override
	public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
		super.onPlace(state, level, pos, oldState, isMoving);
		EngineTopology.markDirty(level, pos);
	}

	@Override
	public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
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
