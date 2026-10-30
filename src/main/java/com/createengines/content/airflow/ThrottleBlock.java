package com.createengines.content.airflow;

import com.createengines.registry.CEBlockEntities;
import com.simibubi.create.foundation.block.IBE;

import net.minecraft.world.level.block.entity.BlockEntityType;

/**
 * A length of intake pipe with a butterfly valve in it. It joins pipes exactly as a manifold does, and everything
 * downstream of the intake network it sits in breathes through it: the more it is closed, the less air each cylinder
 * draws, and the less work it does (below a certain point the piston is working against the vacuum instead).
 * <p>
 * Its opening is a pedal: the idle stop it is set to with a value box when it has no signal, and wide open at full
 * redstone strength. A linkage trims every injector's shot along with the air (see {@code Throttle.fuelTrim}), so
 * closing it does not flood the engine; the mixture still drifts with engine speed, so Fuel Metering stays to tune.
 */
public class ThrottleBlock extends ManifoldBlock implements IBE<ThrottleBlockEntity> {

	public ThrottleBlock(Properties properties) {
		super(properties, PortKind.INTAKE);
	}

	@Override
	public Class<ThrottleBlockEntity> getBlockEntityClass() {
		return ThrottleBlockEntity.class;
	}

	@Override
	public BlockEntityType<? extends ThrottleBlockEntity> getBlockEntityType() {
		return CEBlockEntities.THROTTLE.get();
	}
}
