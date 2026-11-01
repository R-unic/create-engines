package com.createengines.content.airflow;

import com.createengines.registry.CEBlockEntities;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

/**
 * An exhaust heat exchanger: a length of exhaust pipe with a finned hot plate on it. It takes heat out of the exhaust
 * of the cylinders feeding its network and shows it as a Create blaze burner's heat level, so a basin set on top of it
 * cooks heated recipes from a small engine and superheated ones from a big one running at speed, the way it would over
 * a burner. Several exchangers on one network share the heat between them.
 * <p>
 * Create's basin reads the heat of whatever is under it from the block's {@link BlazeBurnerBlock#HEAT_LEVEL} property,
 * so nothing has to hook into Create: the block just carries the property and keeps it up to date.
 */
public class HeatExchangerBlock extends SensorBlock<HeatExchangerBlockEntity> {

	public HeatExchangerBlock(Properties properties) {
		super(properties, PortKind.EXHAUST);
		registerDefaultState(defaultBlockState().setValue(BlazeBurnerBlock.HEAT_LEVEL, BlazeBurnerBlock.HeatLevel.NONE));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder);
		builder.add(BlazeBurnerBlock.HEAT_LEVEL);
	}

	/** Light given off at each heat level: a glowing hot plate lights up what is around it. */
	public static int light(BlockState state) {
		return switch (state.getValue(BlazeBurnerBlock.HEAT_LEVEL)) {
			case SEETHING -> 13;
			case KINDLED -> 9;
			case SMOULDERING -> 4;
			default -> 0;
		};
	}

	@Override
	public Class<HeatExchangerBlockEntity> getBlockEntityClass() {
		return HeatExchangerBlockEntity.class;
	}

	@Override
	public BlockEntityType<? extends HeatExchangerBlockEntity> getBlockEntityType() {
		return CEBlockEntities.HEAT_EXCHANGER.get();
	}
}
