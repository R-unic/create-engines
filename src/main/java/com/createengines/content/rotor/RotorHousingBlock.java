package com.createengines.content.rotor;

import com.createengines.content.PartInstallation;
import com.createengines.content.PartInstallation.Slot;
import com.createengines.content.airflow.AirflowPort;
import com.createengines.content.airflow.Pipes;
import com.createengines.content.airflow.PortKind;
import com.createengines.content.crankshaft.CrankshaftBlock;
import com.createengines.engine.topology.EngineTopology;
import com.createengines.registry.CEBlockEntities;
import com.createengines.registry.CEItems;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A Wankel rotor housing. It is a link of the crankshaft line, like a throw: the eccentric journal is inside it and
 * its angle is set the same way. But it is also its own cylinder and its own head. The rotor turns inside it at a third
 * of the shaft's speed, and its three faces make three chambers that each go round the four strokes, so it fires once
 * per turn of the shaft, as often as a pair of pistons.
 * <p>
 * There are no valves and no camshaft: the intake and exhaust are ports cut in the housing wall, which the rotor opens
 * and shuts as it sweeps by. Plumb it like a head, with an intake pipe on one side and an exhaust on another. Fuel goes
 * in on any side and is shared along the crankshaft like a head's. The charge is too thin to light itself, so it takes a
 * spark plug.
 */
public class RotorHousingBlock extends CrankshaftBlock implements AirflowPort {

	public static final BooleanProperty HAS_ROTOR = BooleanProperty.create("rotor");
	public static final BooleanProperty HAS_INJECTOR = BooleanProperty.create("injector");
	/** Set with {@link #HAS_INJECTOR} when the injector is a precision common-rail one. */
	public static final BooleanProperty FINE_INJECTOR = BooleanProperty.create("fine_injector");
	public static final BooleanProperty HAS_SPARK_PLUG = BooleanProperty.create("spark_plug");

	/** Faces on a rotor: each makes a chamber. */
	public static final int CHAMBERS = 3;

	private static final Slot[] PARTS = {new Slot(HAS_ROTOR, CEItems.ROTOR),
		new Slot(HAS_INJECTOR, CEItems.FUEL_INJECTOR, FINE_INJECTOR, CEItems.PRECISION_INJECTOR),
		new Slot(HAS_SPARK_PLUG, CEItems.SPARK_PLUG)};

	public RotorHousingBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(HAS_ROTOR, false)
			.setValue(HAS_INJECTOR, false)
			.setValue(FINE_INJECTOR, false)
			.setValue(HAS_SPARK_PLUG, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder);
		builder.add(HAS_ROTOR, HAS_INJECTOR, FINE_INJECTOR, HAS_SPARK_PLUG);
	}

	/** A housing is an eccentric journal with a rotor on it: it has an angle, like a throw. */
	@Override
	public boolean isThrow() {
		return true;
	}

	/** The casing is an ordinary block model; the eccentric and the rotor inside it are drawn by the visual. */
	@Override
	public RenderShape getRenderShape(BlockState state) {
		return RenderShape.MODEL;
	}

	@Override
	public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return Shapes.block();
	}

	/** Any side face takes a pipe; the faces on the shaft's axis are where the crankshaft goes. */
	@Override
	public boolean acceptsPipe(BlockState state, Direction side, PortKind kind) {
		return side.getAxis() != state.getValue(BlockStateProperties.AXIS);
	}

	@Override
	protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
											  Player player, InteractionHand hand, BlockHitResult hit) {
		ItemInteractionResult result = PartInstallation.tryInstall(stack, state, level, pos, player, PARTS);
		return result == ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION
			? super.useItemOn(stack, state, level, pos, player, hand, hit) : result;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
											   BlockHitResult hit) {
		if (PartInstallation.tryRemove(state, level, pos, player, PARTS))
			return InteractionResult.sidedSuccess(level.isClientSide);
		return super.useWithoutItem(state, level, pos, player, hit);
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
		PartInstallation.dropParts(state, level, pos, newState, PARTS);
		super.onRemove(state, level, pos, newState, isMoving);
	}

	/** The block entity is a crankshaft's, so {@link #getBlockEntityClass()} stays the crankshaft's class. */
	@Override
	public BlockEntityType<? extends RotorHousingBlockEntity> getBlockEntityType() {
		return CEBlockEntities.ROTOR_HOUSING.get();
	}
}
