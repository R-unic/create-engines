package com.createengines.content.airflow;

import com.createengines.registry.CEBlockEntities;

import net.minecraft.world.level.block.entity.BlockEntityType;

/**
 * A mass airflow sensor: a length of intake pipe that weighs the air each cylinder behind it breathes. Fuel is cut to
 * match: the injectors on its network never deliver more than the air that cylinder drew in can burn at the mixture
 * the sensor is set to. It only ever cuts what an injector was set to deliver, so Fuel Metering is still the most a
 * cylinder gets, and a sensor does what a hand-tuned shot cannot: follow the air as breathing changes with speed,
 * boost and the throttle.
 * <p>
 * Its redstone output is the size of the breath, 0 for none to 15 for a boosted one half as large again as the bore.
 */
public class MafSensorBlock extends SensorBlock<MafSensorBlockEntity> {

	public MafSensorBlock(Properties properties) {
		super(properties, PortKind.INTAKE);
	}

	@Override
	public Class<MafSensorBlockEntity> getBlockEntityClass() {
		return MafSensorBlockEntity.class;
	}

	@Override
	public BlockEntityType<? extends MafSensorBlockEntity> getBlockEntityType() {
		return CEBlockEntities.MAF_SENSOR.get();
	}
}
