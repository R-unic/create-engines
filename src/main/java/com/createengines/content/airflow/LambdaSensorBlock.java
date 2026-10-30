package com.createengines.content.airflow;

import com.createengines.registry.CEBlockEntities;

import net.minecraft.world.level.block.entity.BlockEntityType;

/**
 * A lambda (oxygen) sensor: a length of exhaust pipe that reads the mixture the cylinders feeding it burned, from what
 * the exhaust gas still held. Its redstone output is that mixture, 8 for balanced, less for lean and more for rich,
 * 15 at twice as much fuel as the air could burn. It changes nothing by itself: it is the eyes for whatever the
 * redstone around it decides to do, a camshaft's fuel rack included.
 */
public class LambdaSensorBlock extends SensorBlock<LambdaSensorBlockEntity> {

	public LambdaSensorBlock(Properties properties) {
		super(properties, PortKind.EXHAUST);
	}

	@Override
	public Class<LambdaSensorBlockEntity> getBlockEntityClass() {
		return LambdaSensorBlockEntity.class;
	}

	@Override
	public BlockEntityType<? extends LambdaSensorBlockEntity> getBlockEntityType() {
		return CEBlockEntities.LAMBDA_SENSOR.get();
	}
}
