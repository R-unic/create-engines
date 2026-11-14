package com.createengines.content.battery;

import com.createengines.engine.topology.EngineTopology;
import com.createengines.registry.CEBlockEntities;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.block.IBE;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Stores the charge that spark plugs fire from. Place one against a crankshaft block or a cylinder head and every
 * spark plug on that engine fires from it. It makes no charge of its own: an
 * {@link com.createengines.content.alternator.AlternatorBlock alternator} touching it, or touching the same engine,
 * fills it.
 */
public class BatteryBlock extends Block implements IWrenchable, IBE<BatteryBlockEntity> {

	private static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 16, 14);

	public BatteryBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
		super.onPlace(state, level, pos, oldState, movedByPiston);
		EngineTopology.markDirty(level, pos);
	}

	@Override
	protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
		IBE.onRemove(state, level, pos, newState);
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
