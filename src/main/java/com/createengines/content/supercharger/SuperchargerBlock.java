package com.createengines.content.supercharger;

import org.jetbrains.annotations.Nullable;

import com.createengines.content.airflow.InlineDuct;
import com.createengines.content.airflow.Pipes;
import com.createengines.content.airflow.PortKind;
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
 * A blower driven by a shaft: the shaft runs through it along its axis, and the faces round the shaft take intake
 * pipes. Set in an intake run it packs more air into every cylinder on that run at once, in proportion to the square of
 * its speed, with no lag and no need for exhaust. The price is the stress it puts on the shaft that turns it, so on the
 * crank's own shaft line it comes out of the engine's output.
 */
public class SuperchargerBlock extends RotatedPillarKineticBlock implements InlineDuct, IBE<SuperchargerBlockEntity> {

	private static final VoxelShape[] SHAPES = {
		Block.box(0, 1, 1, 16, 15, 15), Block.box(1, 0, 1, 15, 16, 15), Block.box(1, 1, 0, 15, 15, 16)};

	public SuperchargerBlock(Properties properties) {
		super(properties);
	}

	/** Whether this blower has a variable-ratio drive and tight rotors; the {@link VariableSuperchargerBlock} does. */
	public boolean variableDrive() {
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

	/** Intake pipes join any face the shaft does not run out of. */
	@Override
	public boolean acceptsPipe(BlockState state, Direction side, PortKind kind) {
		return kind == PortKind.INTAKE && side.getAxis() != state.getValue(AXIS);
	}

	@Override
	public @Nullable PortKind kindFacingHead(BlockState state, Direction face) {
		return acceptsPipe(state, face, PortKind.INTAKE) ? PortKind.INTAKE : null;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPES[state.getValue(AXIS)
			.ordinal()];
	}

	@Override
	public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
		super.onPlace(state, level, pos, oldState, isMoving);
		if (!oldState.is(this))
			Pipes.attachNeighbours(level, pos, state);
		EngineTopology.markDirty(level, pos);
	}

	@Override
	public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
		super.onRemove(state, level, pos, newState, isMoving);
		EngineTopology.markDirty(level, pos);
	}

	@Override
	public Class<SuperchargerBlockEntity> getBlockEntityClass() {
		return SuperchargerBlockEntity.class;
	}

	@Override
	public BlockEntityType<? extends SuperchargerBlockEntity> getBlockEntityType() {
		return CEBlockEntities.SUPERCHARGER.get();
	}
}
