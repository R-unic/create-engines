package com.createengines.content.flywheel;

import com.createengines.registry.CEBlockEntities;

import net.minecraft.world.level.block.entity.BlockEntityType;

/**
 * A flywheel that a pack or an addon makes with {@code PartBlocks.flywheel}: a disc on a shaft, on the crank line like a Cast
 * Flywheel, whose masses come from the {@code flywheel} data map and whose look is the block's own model, which turns with the
 * shaft. The scanner reads its masses and nothing else about it; no block of this mod is one.
 */
public class DataFlywheelBlock extends CastFlywheelBlock {

	public DataFlywheelBlock(Properties properties) {
		super(properties);
	}

	@Override
	public Class<CastFlywheelBlockEntity> getBlockEntityClass() {
		return CastFlywheelBlockEntity.class;
	}

	@Override
	public BlockEntityType<? extends CastFlywheelBlockEntity> getBlockEntityType() {
		return CEBlockEntities.DATA_FLYWHEEL.get();
	}
}
