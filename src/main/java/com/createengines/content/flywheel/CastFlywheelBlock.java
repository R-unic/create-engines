package com.createengines.content.flywheel;

import com.createengines.engine.topology.EngineTopology;
import com.createengines.registry.CEBlockEntities;
import com.simibubi.create.content.kinetics.base.RotatedPillarKineticBlock;
import com.simibubi.create.foundation.block.IBE;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A heavy iron disc on a shaft. Put on the line of an engine's crankshaft, on either end of it or beyond a plain shaft
 * from it, it adds mass to the engine, which smooths the surge of each power stroke and so keeps its rating up (see
 * {@link com.createengines.engine.sim.Roughness}). It is the cheap, early way to do what a Create Flywheel does, and a
 * little lighter.
 */
public class CastFlywheelBlock extends RotatedPillarKineticBlock implements IBE<CastFlywheelBlockEntity> {

	private static final VoxelShape[] SHAPES = {
		Block.box(4, 0, 0, 12, 16, 16), Block.box(0, 4, 0, 16, 12, 16), Block.box(0, 0, 4, 16, 16, 12)};

	public CastFlywheelBlock(Properties properties) {
		super(properties);
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
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPES[state.getValue(AXIS)
			.ordinal()];
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
	public Class<CastFlywheelBlockEntity> getBlockEntityClass() {
		return CastFlywheelBlockEntity.class;
	}

	@Override
	public BlockEntityType<? extends CastFlywheelBlockEntity> getBlockEntityType() {
		return CEBlockEntities.CAST_FLYWHEEL.get();
	}
}
