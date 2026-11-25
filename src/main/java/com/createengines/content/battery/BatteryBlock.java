package com.createengines.content.battery;

import java.util.concurrent.atomic.AtomicInteger;

import com.createengines.engine.sim.BatteryChemistry;
import com.createengines.engine.sim.BatteryTier;
import com.createengines.engine.topology.EngineTopology;
import com.createengines.registry.CEBlockEntities;
import com.createengines.registry.CELang;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.block.IBE;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Stores the charge that spark plugs fire from. Place one against a crankshaft block or a cylinder head and every
 * spark plug on that engine fires from it. It makes no charge of its own: an
 * {@link com.createengines.content.alternator.AlternatorBlock alternator} touching it, or touching the same engine,
 * fills it.
 * <p>
 * Batteries that touch join into one {@link BatteryBank bank}: its capacity is the sum, an engine or alternator touching
 * any one of them reaches all of them, and a comparator on any one reads the bank's charge. The lamp strip on the lid shows
 * this battery's own charge ({@link #CHARGE}).
 * <p>
 * Batteries that touch also look joined, as Create's tanks and item vaults do: the body, the band and the lid run on into the
 * neighbour with no seam between, and a battery with another on top has no terminals of its own (they stay on the top of the
 * stack). There is no rule about shape: any run, wall, L or block of batteries joins wherever two of them touch. The join is
 * the {@link #NORTH}, {@link #EAST}, {@link #SOUTH}, {@link #WEST} and {@link #UP} properties, kept by the block from its
 * neighbours; it changes nothing about the bank.
 */
public class BatteryBlock extends Block implements IWrenchable, IBE<BatteryBlockEntity> {

	/** The lamp strip: 0 (flat, every lamp dark) to {@link BatteryChemistry#GAUGE_STAGES} (full). Set by the block entity from its charge. */
	public static final IntegerProperty CHARGE = IntegerProperty.create("charge", 0, BatteryChemistry.GAUGE_STAGES);

	/** True where another battery touches that side. Down is not kept: nothing about the model depends on it. */
	public static final BooleanProperty NORTH = BlockStateProperties.NORTH;
	public static final BooleanProperty EAST = BlockStateProperties.EAST;
	public static final BooleanProperty SOUTH = BlockStateProperties.SOUTH;
	public static final BooleanProperty WEST = BlockStateProperties.WEST;
	public static final BooleanProperty UP = BlockStateProperties.UP;

	/** Which kind of battery this is: how much it holds and how the cold treats it. */
	public BatteryTier tier() {
		return tier;
	}

	/** The join property for the way a neighbour lies, or null for down, which has none. */
	public static @Nullable BooleanProperty joinFor(Direction direction) {
		return switch (direction) {
			case NORTH -> NORTH;
			case EAST -> EAST;
			case SOUTH -> SOUTH;
			case WEST -> WEST;
			case UP -> UP;
			case DOWN -> null;
		};
	}

	/**
	 * True for a property of a battery that only changes how it looks (its lamp strip and its joins), which nothing else should record
	 * or compare. The joins are vanilla's own properties, which pipes and other blocks use for something that matters, so the block
	 * is part of the question.
	 */
	public static boolean isOnlyForLooks(BlockState state, net.minecraft.world.level.block.state.properties.Property<?> property) {
		return state.getBlock() instanceof BatteryBlock
			&& (property == CHARGE || property == NORTH || property == EAST || property == SOUTH || property == WEST || property == UP);
	}

	/** Bumped whenever a battery is placed or removed. Nothing in play reads it: it is how a test tells that from a change of state. */
	private static final AtomicInteger TOPOLOGY = new AtomicInteger();

	/** How many times a battery has been placed or removed since the game started, for the tests. */
	public static int topologyForTests() {
		return TOPOLOGY.get();
	}

	/** The shape by which of the four sides are joined (north 1, east 2, south 4, west 8): a joined side fills out to the edge of the block. */
	private static final VoxelShape[] SHAPES = new VoxelShape[16];

	static {
		for (int mask = 0; mask < 16; mask++)
			SHAPES[mask] = Block.box((mask & 8) != 0 ? 0 : 2, 0, (mask & 1) != 0 ? 0 : 2, (mask & 2) != 0 ? 16 : 14, 16,
				(mask & 4) != 0 ? 16 : 14);
	}

	private final BatteryTier tier;

	/** A plain battery: 200 sparks, as it has always been. */
	public BatteryBlock(Properties properties) {
		this(properties, BatteryTier.PLAIN);
	}

	public BatteryBlock(Properties properties, BatteryTier tier) {
		super(properties);
		this.tier = tier;
		registerDefaultState(defaultBlockState().setValue(CHARGE, 0)
			.setValue(NORTH, false)
			.setValue(EAST, false)
			.setValue(SOUTH, false)
			.setValue(WEST, false)
			.setValue(UP, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder);
		builder.add(CHARGE, NORTH, EAST, SOUTH, WEST, UP);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return joined(defaultBlockState(), context.getLevel(), context.getClickedPos());
	}

	@Override
	protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbourState, LevelAccessor level,
									 BlockPos pos, BlockPos neighbourPos) {
		BooleanProperty join = joinFor(direction);
		if (join == null)
			return super.updateShape(state, direction, neighbourState, level, pos, neighbourPos);
		return state.setValue(join, neighbourState.getBlock() instanceof BatteryBlock);
	}

	/**
	 * {@code state} with its join properties set from the batteries that touch {@code pos}. A side in a chunk that is not loaded
	 * is left as it was: asking for its block would load the chunk.
	 */
	public static BlockState joined(BlockState state, Level level, BlockPos pos) {
		for (Direction direction : Direction.values()) {
			BooleanProperty join = joinFor(direction);
			if (join == null || !state.hasProperty(join))
				continue;
			BlockPos beside = pos.relative(direction);
			if (level.isLoaded(beside))
				state = state.setValue(join, level.getBlockState(beside)
					.getBlock() instanceof BatteryBlock);
		}
		return state;
	}

	/** The collision and outline shape of a battery joined on those sides: {@code true} where another battery touches. */
	public static VoxelShape shapeOf(boolean north, boolean east, boolean south, boolean west) {
		return SHAPES[(north ? 1 : 0) | (east ? 2 : 0) | (south ? 4 : 0) | (west ? 8 : 0)];
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return shapeOf(state.getValue(NORTH), state.getValue(EAST), state.getValue(SOUTH), state.getValue(WEST));
	}

	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
		super.onPlace(state, level, pos, oldState, movedByPiston);
		// The lamp strip changes the state of a battery already there, which is no change to the engine or the bank
		if (oldState.is(state.getBlock()))
			return;
		TOPOLOGY.incrementAndGet();
		// The banks this one joins are walked again. Its own block entity may not be there yet, and has walked nothing
		for (Direction direction : Direction.values())
			BatteryBank.forget(level, pos.relative(direction));
		EngineTopology.markDirty(level, pos);
	}

	@Override
	protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
		// The bank it leaves is walked again, which takes asking it while it is still here. Banks elsewhere are left alone
		if (!newState.is(state.getBlock())) {
			BatteryBank.forget(level, pos);
			for (Direction direction : Direction.values())
				BatteryBank.forget(level, pos.relative(direction));
		}
		IBE.onRemove(state, level, pos, newState);
		if (newState.is(state.getBlock()))
			return;
		TOPOLOGY.incrementAndGet();
		EngineTopology.markDirty(level, pos);
	}

	/**
	 * The wrench sets or clears this battery's reserve ({@link BatteryBlockEntity#isReserve}): a reserve is kept for the starter
	 * and the spark plugs, and other mods' wires cannot draw on it. A battery has no facing, so the wrench had nothing else to do
	 * here. Sneaking still picks the battery up.
	 */
	@Override
	public InteractionResult onWrenched(BlockState state, UseOnContext context) {
		Level level = context.getLevel();
		BlockPos pos = context.getClickedPos();
		if (!(level.getBlockEntity(pos) instanceof BatteryBlockEntity battery))
			return IWrenchable.super.onWrenched(state, context);
		if (!level.isClientSide) {
			battery.setReserve(!battery.isReserve());
			IWrenchable.playRotateSound(level, pos);
			if (context.getPlayer() != null)
				context.getPlayer()
					.displayClientMessage(CELang.translate(battery.isReserve() ? "battery.reserve.on" : "battery.reserve.off")
						.component(), true);
		}
		return InteractionResult.sidedSuccess(level.isClientSide);
	}

	/** A Service Kit sets right a battery's light wear (the wear mode's); anything else is as it always was. */
	@Override
	protected net.minecraft.world.ItemInteractionResult useItemOn(net.minecraft.world.item.ItemStack stack, BlockState state, Level level,
		BlockPos pos, net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand,
		net.minecraft.world.phys.BlockHitResult hit) {
		net.minecraft.world.ItemInteractionResult worn = com.createengines.content.WearService.onBattery(stack, level, pos, player, hand);
		return worn != net.minecraft.world.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION ? worn
			: super.useItemOn(stack, state, level, pos, player, hand, hit);
	}

	@Override
	protected boolean hasAnalogOutputSignal(BlockState state) {
		return true;
	}

	/** The charge of the whole bank this battery is in, 0 to 15 ({@link BatteryChemistry#signal}): for a low-charge alarm or an auto-start. */
	@Override
	protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
		return level.getBlockEntity(pos) instanceof BatteryBlockEntity battery ? battery.bankSignal() : 0;
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
