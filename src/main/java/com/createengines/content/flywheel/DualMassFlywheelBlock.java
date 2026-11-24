package com.createengines.content.flywheel;

import com.createengines.registry.CEBlockEntities;

import net.minecraft.world.level.block.entity.BlockEntityType;

/**
 * Two masses joined by a spring pack, on the crankshaft's line like a Cast Flywheel: a light primary mass bolted to the
 * crank and a secondary mass behind the springs. The primary joins the crank's inertia, so the engine revs almost as
 * quickly as with a small flywheel; the secondary smooths the speed ripple the network sees, but only above the spring
 * pack's resonance (see {@link com.createengines.engine.sim.DualMass}), so at low speed it is no better than its primary
 * mass alone and a big Cast Flywheel stays the part for a slow, heavy engine. It is a Cast Flywheel to everything that
 * only needs a part on the line (placement, collision, rendering); the scanner tells the two apart.
 */
public class DualMassFlywheelBlock extends CastFlywheelBlock {

	public DualMassFlywheelBlock(Properties properties) {
		super(properties);
	}

	@Override
	public Class<CastFlywheelBlockEntity> getBlockEntityClass() {
		return CastFlywheelBlockEntity.class;
	}

	@Override
	public BlockEntityType<? extends CastFlywheelBlockEntity> getBlockEntityType() {
		return CEBlockEntities.DUAL_MASS_FLYWHEEL.get();
	}
}
