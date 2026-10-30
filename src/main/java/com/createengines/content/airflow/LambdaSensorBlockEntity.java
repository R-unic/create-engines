package com.createengines.content.airflow;

import java.util.List;
import java.util.Locale;

import com.createengines.content.GoggleLines;
import com.createengines.engine.sim.Mixture;
import com.createengines.engine.sim.Sensors;
import com.createengines.registry.CELang;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/** The last mixture a {@link LambdaSensorBlock} read in its network's exhaust, for redstone and goggles. */
public class LambdaSensorBlockEntity extends SensorBlockEntity {

	private boolean onEngine;
	/** Fuel-air equivalence ratio of what the firing cylinders on this network burned; NaN if none fired. */
	private float mixture = Float.NaN;

	public LambdaSensorBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	@Override
	public boolean onEngine() {
		return onEngine;
	}

	public float mixture() {
		return mixture;
	}

	/** What the engine saw this tick: the mean mixture of the cylinders that fired, or NaN if none did. */
	public void reading(float mixture) {
		boolean changed = !onEngine || Float.isNaN(mixture) != Float.isNaN(this.mixture)
			|| !Float.isNaN(mixture) && Math.abs(mixture - this.mixture) > 0.02f;
		onEngine = true;
		this.mixture = mixture;
		setSignal(Float.isNaN(mixture) ? 0 : Sensors.lambdaSignal(mixture));
		if (changed)
			displayChanged();
	}

	@Override
	public void detachFromEngine() {
		if (!onEngine)
			return;
		onEngine = false;
		mixture = Float.NaN;
		setSignal(0);
		displayChanged();
	}

	@Override
	protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
		super.write(tag, registries, clientPacket);
		tag.putBoolean("OnEngine", onEngine);
		tag.putFloat("Mixture", mixture);
	}

	@Override
	protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
		super.read(tag, registries, clientPacket);
		onEngine = tag.getBoolean("OnEngine");
		mixture = tag.contains("Mixture") ? tag.getFloat("Mixture") : Float.NaN;
	}

	@Override
	public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
		CELang.translate("goggles.lambda")
			.forGoggles(tooltip);
		if (!onEngine)
			GoggleLines.add(CELang.translate("goggles.sensor.no_engine")
				.style(ChatFormatting.RED), tooltip, 1);
		else if (Float.isNaN(mixture))
			GoggleLines.add(CELang.translate("goggles.lambda.none")
				.style(ChatFormatting.GRAY), tooltip, 1);
		else {
			boolean balanced = Mixture.balanced(mixture);
			Component reading = balanced ? CELang.translate("goggles.mixture.balanced")
				.component()
				: CELang.translate(mixture < 1 ? "goggles.mixture.lean" : "goggles.mixture.rich",
					String.format(Locale.ROOT, "%.1f", mixture < 1 ? 1 / Math.max(mixture, 0.05f) : mixture))
					.component();
			GoggleLines.add(CELang.translate("goggles.mixture", reading)
				.style(balanced ? ChatFormatting.GREEN : ChatFormatting.GOLD), tooltip, 1);
		}
		GoggleLines.add(CELang.translate("goggles.sensor.signal", signal())
			.style(ChatFormatting.DARK_GRAY), tooltip, 1);
		if (isPlayerSneaking)
			GoggleLines.add(CELang.translate("goggles.lambda.hint")
				.style(ChatFormatting.DARK_GRAY), tooltip, 2);
		return true;
	}
}
