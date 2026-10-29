package com.createengines.content.battery;

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
 * Stores the charge that spark plugs fire from. A shaft runs through it along its axis, and while that shaft turns it
 * charges like an alternator, at the price of a little stress. Place one against a crankshaft block or a cylinder
 * head and every spark plug on that engine fires from it; coaxial with the crank, its own engine keeps it charged.
 */
public class BatteryBlock extends RotatedPillarKineticBlock implements IBE<BatteryBlockEntity> {

	private static final VoxelShape[] SHAPES = {
		Block.box(0, 2, 2, 16, 14, 14), Block.box(2, 0, 2, 14, 16, 14), Block.box(2, 2, 0, 14, 14, 16)};

	public BatteryBlock(Properties properties) {
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
	public Class<BatteryBlockEntity> getBlockEntityClass() {
		return BatteryBlockEntity.class;
	}

	@Override
	public BlockEntityType<? extends BatteryBlockEntity> getBlockEntityType() {
		return CEBlockEntities.BATTERY.get();
	}
}
