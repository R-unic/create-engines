package com.createengines.content.airflow;

import java.util.List;

import com.createengines.content.GoggleLines;
import com.createengines.engine.sim.Sensors;
import com.createengines.registry.CELang;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The mixture a {@link MafSensorBlock} holds its network's shots to, and the last breath it weighed. The simulation
 * does the trimming and the topping up; this holds the setting it reads and what goggles and redstone show.
 */
public class MafSensorBlockEntity extends SensorBlockEntity {

	/** The target is set in steps of this many percent of a balanced mixture. */
	public static final int TARGET_STEP = 5;
	public static final int MIN_TARGET = 16;
	public static final int MAX_TARGET = 25;
	public static final int DEFAULT_TARGET = 20;
	private static final BehaviourType<ScrollValueBehaviour> TARGET_TYPE = new BehaviourType<>("maf_target");
	/** Goggles say the sensor is cutting or adding once it moves at least this share of the shot. */
	private static final float SHIFT_SHOWN = 0.02f;

	public ScrollValueBehaviour target;

	private boolean onEngine;
	private float charge;
	private float cut;
	private float added;

	public MafSensorBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	@Override
	public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
		target = new ScrollValueBehaviour(CELang.translate("value.maf_target")
			.component(), this, new PipeValueBox()) {
			@Override
			public BehaviourType<?> getType() {
				return TARGET_TYPE;
			}
		};
		target.between(MIN_TARGET, MAX_TARGET);
		target.value = DEFAULT_TARGET;
		target.withFormatter(v -> v * TARGET_STEP + "%");
		behaviours.add(target);
	}

	/** The mixture the shot is held to, as an equivalence ratio: 0.8 (lean) to 1.25 (rich), 1 being balanced. */
	public double targetMixture() {
		return Math.max(MIN_TARGET, target.getValue()) * TARGET_STEP / 100d;
	}

	@Override
	public boolean onEngine() {
		return onEngine;
	}

	/**
	 * What the engine saw this tick.
	 *
	 * @param charge how full the breath of the cylinders on this network is, 1 being the bore at bottom dead centre
	 * @param cut    the average share of the shot held back to match it
	 * @param added  the average share of the shot added to match it
	 */
	public void reading(float charge, float cut, float added) {
		boolean changed = !onEngine || Math.abs(charge - this.charge) > 0.005f || Math.abs(cut - this.cut) > 0.005f
			|| Math.abs(added - this.added) > 0.005f;
		onEngine = true;
		this.charge = charge;
		this.cut = cut;
		this.added = added;
		setSignal(Sensors.mafSignal(charge));
		if (changed)
			displayChanged();
	}

	@Override
	public void detachFromEngine() {
		if (!onEngine)
			return;
		onEngine = false;
		charge = 0;
		cut = 0;
		added = 0;
		setSignal(0);
		displayChanged();
	}

	@Override
	protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
		super.write(tag, registries, clientPacket);
		tag.putBoolean("OnEngine", onEngine);
		tag.putFloat("Charge", charge);
		tag.putFloat("Cut", cut);
		tag.putFloat("Added", added);
	}

	@Override
	protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
		super.read(tag, registries, clientPacket);
		onEngine = tag.getBoolean("OnEngine");
		charge = tag.getFloat("Charge");
		cut = tag.getFloat("Cut");
		added = tag.getFloat("Added");
	}

	@Override
	public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
		CELang.translate("goggles.maf")
			.forGoggles(tooltip);
		if (!onEngine)
			GoggleLines.add(CELang.translate("goggles.sensor.no_engine")
				.style(ChatFormatting.RED), tooltip, 1);
		else {
			GoggleLines.add(CELang.translate("goggles.maf.charge", Math.round(charge * 100))
				.style(ChatFormatting.AQUA), tooltip, 1);
			if (cut >= SHIFT_SHOWN)
				GoggleLines.add(CELang.translate("goggles.maf.cutting", Math.round(cut * 100))
					.style(ChatFormatting.GOLD), tooltip, 1);
			else if (added >= SHIFT_SHOWN)
				GoggleLines.add(CELang.translate("goggles.maf.adding", Math.round(added * 100))
					.style(ChatFormatting.GREEN), tooltip, 1);
			else
				GoggleLines.add(CELang.translate("goggles.maf.within")
					.style(ChatFormatting.GRAY), tooltip, 1);
		}
		GoggleLines.add(CELang.translate("goggles.maf.target", Math.round(targetMixture() * 100))
			.style(ChatFormatting.DARK_AQUA), tooltip, 1);
		GoggleLines.add(CELang.translate("goggles.sensor.signal", signal())
			.style(ChatFormatting.DARK_GRAY), tooltip, 1);
		if (isPlayerSneaking)
			GoggleLines.add(CELang.translate("goggles.maf.hint")
				.style(ChatFormatting.DARK_GRAY), tooltip, 2);
		return true;
	}
}
