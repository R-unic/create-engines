package com.createengines.forge;

import java.util.List;

import com.createengines.engine.sim.Camshaft;
import com.createengines.engine.sim.CamProfile;
import com.createengines.engine.sim.CylinderModel;
import com.createengines.engine.sim.CylinderSpec;
import com.createengines.engine.sim.EngineSimulation;
import com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity;
import com.simibubi.create.infrastructure.config.AllConfigs;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The spike: one crude cylinder from the shared simulation on a crank that drives a Create network. It burns what the
 * fluid tanks beside it hold and turns itself over once, so what is being tried is only whether the simulation's speed and
 * torque reach Create 6.0.8 on Forge 1.20.1 and are loaded and stressed by it.
 */
public class SpikeCrankBlockEntity extends GeneratingKineticBlockEntity {

	private static final double STRESS_PER_TORQUE = 100;

	private EngineSimulation sim;
	private float publishedSpeed;
	private float publishedCapacity;
	private int ticks;

	public SpikeCrankBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	private EngineSimulation sim() {
		if (sim == null) {
			CylinderModel cylinder = new CylinderModel(CylinderSpec.CRUDE, CamProfile.BASIC, 0, new Camshaft(), 0);
			cylinder.fuelSupply = new TankFuel(level, worldPosition);
			sim = new EngineSimulation(List.of(cylinder), 0.45 + 1, 0.02);
			sim.restore(0, EngineSimulation.rpmToOmega(40), 1);
		}
		return sim;
	}

	@Override
	public void tick() {
		super.tick();
		if (level == null || level.isClientSide)
			return;
		EngineSimulation engine = sim();
		float maxRpm = AllConfigs.server().kinetics.maxRotationSpeed.get();
		double maxOmega = EngineSimulation.rpmToOmega(maxRpm);
		double load = getSpeed() == 0 || isOverStressed() ? 0 : stress / Math.abs(getSpeed()) / STRESS_PER_TORQUE;
		engine.tickFree(load, maxOmega);
		float speed = engine.isStopped() ? 0 : Math.min(maxRpm, Math.max(1, Math.round(engine.rpm())));
		float capacity = (float) (Math.max(0, engine.availableTorque()) * STRESS_PER_TORQUE);
		if (speed != publishedSpeed && (publishedSpeed == 0 || speed == 0 || Math.abs(speed - publishedSpeed) >= 2)) {
			publishedSpeed = speed;
			updateGeneratedRotation();
		}
		if (Math.abs(capacity - publishedCapacity) > 0.5 && ++ticks % 10 == 0) {
			publishedCapacity = capacity;
			if (hasNetwork() && publishedSpeed != 0)
				notifyStressCapacityChange(calculateAddedStressCapacity());
		}
	}

	@Override
	public float getGeneratedSpeed() {
		return publishedSpeed;
	}

	@Override
	public float calculateAddedStressCapacity() {
		lastCapacityProvided = publishedCapacity;
		return publishedCapacity;
	}

	/** What the spike reports for a check: the simulation's own speed, regardless of what Create has published. */
	public double simRpm() {
		return sim().rpm();
	}
}
