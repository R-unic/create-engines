package com.createengines.content.radiator;

import com.createengines.engine.topology.EngineTopology;
import com.createengines.registry.CEBlockEntities;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import com.simibubi.create.foundation.block.IBE;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.fluids.FluidUtil;

/**
 * A copper-cored radiator with a coolant loop of its own. Set it against an engine's crankshaft, a cylinder or a cylinder
 * head to cool the whole engine; see {@link RadiatorBlockEntity}.
 * <p>
 * Like the exhaust heat exchanger it carries Create's {@link BlazeBurnerBlock#HEAT_LEVEL}, which is where a basin on top
 * looks for heat, and it is registered as a boiler heater (see {@code CECompat}). It goes no further than kindled.
 */
public class RadiatorBlock extends Block implements IWrenchable, IBE<RadiatorBlockEntity> {

	public RadiatorBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(BlazeBurnerBlock.HEAT_LEVEL, BlazeBurnerBlock.HeatLevel.NONE));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder);
		builder.add(BlazeBurnerBlock.HEAT_LEVEL);
	}

	/** A bucket (or any fluid container) fills the loop or draws from it. */
	@Override
	protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
											  Player player, InteractionHand hand, BlockHitResult hit) {
		if (FluidUtil.getFluidHandler(stack)
			.isEmpty())
			return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
		if (!level.isClientSide)
			FluidUtil.interactWithFluidHandler(player, hand, level, pos, hit.getDirection());
		return ItemInteractionResult.sidedSuccess(level.isClientSide);
	}

	/** A comparator reads how full the loop is, for a pump or an alarm. */
	@Override
	protected boolean hasAnalogOutputSignal(BlockState state) {
		return true;
	}

	@Override
	protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
		return level.getBlockEntity(pos) instanceof RadiatorBlockEntity radiator ? radiator.comparatorSignal() : 0;
	}

	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
		super.onPlace(state, level, pos, oldState, movedByPiston);
		// A change of heat level is not a change to any engine
		if (!oldState.is(this))
			EngineTopology.markDirty(level, pos);
	}

	@Override
	protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
		IBE.onRemove(state, level, pos, newState);
		if (!state.is(newState.getBlock()))
			EngineTopology.markDirty(level, pos);
	}

	@Override
	public Class<RadiatorBlockEntity> getBlockEntityClass() {
		return RadiatorBlockEntity.class;
	}

	@Override
	public BlockEntityType<? extends RadiatorBlockEntity> getBlockEntityType() {
		return CEBlockEntities.RADIATOR.get();
	}
}
