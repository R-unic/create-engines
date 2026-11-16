package com.createengines.content.refinery;

import com.simibubi.create.content.equipment.wrench.IWrenchable;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/** A section of a refining column. Its sight window glows while the column is working; the column sets that. */
public class ColumnSectionBlock extends Block implements IWrenchable {

	public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

	public ColumnSectionBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(ACTIVE, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(ACTIVE);
	}
}
