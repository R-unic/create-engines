package com.createengines.content.airflow;

import java.util.List;
import java.util.Locale;

import com.createengines.content.GoggleLines;
import com.createengines.engine.sim.HeatRecovery;
import com.createengines.engine.sim.Tuning;
import com.createengines.registry.CELang;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock.HeatLevel;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The heat a {@link HeatExchangerBlock} is taking from its network's exhaust. The engine pushes the heat in every tick;
 * this turns it into a heat level, puts that on the block (where a basin above reads it) and into a redstone signal.
 */
public class HeatExchangerBlockEntity extends SensorBlockEntity {

	/**
	 * Share of the gap to the heat being fed that the exchanger's own heat closes each tick. A cylinder blows its exhaust
	 * down over a part of each cycle, so what it feeds in any one tick is spiky and mostly nothing at low revs; the
	 * exchanger is a lump of metal that takes a second or so to warm and cool, and reads steadily.
	 */
	private static final float SMOOTHING = 0.04f;

	private static final HeatLevel[] LEVELS = {HeatLevel.NONE, HeatLevel.SMOULDERING, HeatLevel.KINDLED, HeatLevel.SEETHING};

	private boolean onEngine;
	/** Exhaust heat per second this exchanger is getting, after the server's recovery setting. */
	private float heat;
	/** One of {@link HeatRecovery#NONE} to {@link HeatRecovery#SEETHE}. */
	private int heatLevel;

	public HeatExchangerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	@Override
	public boolean onEngine() {
		return onEngine;
	}

	public float heat() {
		return heat;
	}

	/** One of {@link HeatRecovery#NONE} to {@link HeatRecovery#SEETHE}. */
	public int heatLevel() {
		return heatLevel;
	}

	/** What the engine fed this exchanger this tick: its share of the exhaust heat per second on the network. */
	public void reading(float fed) {
		double target = Tuning.heatRecoveryEnabled ? fed * Tuning.heatRecovery : 0;
		// Switching recovery off cools it at once; otherwise it warms and cools gradually
		double usable = !Tuning.heatRecoveryEnabled || !onEngine ? target : heat + (target - heat) * SMOOTHING;
		int next = HeatRecovery.level(usable, heatLevel);
		boolean changed = !onEngine || next != heatLevel || Math.abs(usable - heat) > 0.5;
		onEngine = true;
		heat = (float) usable;
		setSignal(HeatRecovery.signal(usable));
		if (next != heatLevel) {
			heatLevel = next;
			showLevel();
		}
		if (changed)
			displayChanged();
	}

	@Override
	public void detachFromEngine() {
		if (!onEngine)
			return;
		onEngine = false;
		heat = 0;
		heatLevel = HeatRecovery.NONE;
		setSignal(0);
		showLevel();
		displayChanged();
	}

	/** Puts the heat level on the block, which is where Create's basins look for it. */
	private void showLevel() {
		if (level == null || level.isClientSide)
			return;
		BlockState state = getBlockState();
		if (!state.hasProperty(BlazeBurnerBlock.HEAT_LEVEL))
			return;
		HeatLevel target = LEVELS[heatLevel];
		if (state.getValue(BlazeBurnerBlock.HEAT_LEVEL) != target)
			level.setBlock(worldPosition, state.setValue(BlazeBurnerBlock.HEAT_LEVEL, target), Block.UPDATE_ALL);
	}

	@Override
	protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
		super.write(tag, registries, clientPacket);
		tag.putBoolean("OnEngine", onEngine);
		tag.putFloat("Heat", heat);
		tag.putInt("HeatLevel", heatLevel);
	}

	@Override
	protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
		super.read(tag, registries, clientPacket);
		onEngine = tag.getBoolean("OnEngine");
		heat = tag.getFloat("Heat");
		heatLevel = Math.clamp(tag.getInt("HeatLevel"), HeatRecovery.NONE, HeatRecovery.SEETHE);
	}

	@Override
	public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
		CELang.translate("goggles.heat_exchanger")
			.forGoggles(tooltip);
		if (!onEngine)
			GoggleLines.add(CELang.translate("goggles.sensor.no_engine")
				.style(ChatFormatting.RED), tooltip, 1);
		else if (!Tuning.heatRecoveryEnabled)
			GoggleLines.add(CELang.translate("goggles.heat_exchanger.disabled")
				.style(ChatFormatting.RED), tooltip, 1);
		else {
			GoggleLines.add(CELang.translate("goggles.heat_exchanger.heat", String.format(Locale.ROOT, "%.1f", heat))
				.style(ChatFormatting.GOLD), tooltip, 1);
			String key = switch (heatLevel) {
				case HeatRecovery.SEETHE -> "goggles.heat_exchanger.superheated";
				case HeatRecovery.KINDLE -> "goggles.heat_exchanger.heated";
				case HeatRecovery.SMOULDER -> "goggles.heat_exchanger.warm";
				default -> "goggles.heat_exchanger.cold";
			};
			GoggleLines.add(CELang.translate(key)
				.style(heatLevel >= HeatRecovery.KINDLE ? ChatFormatting.GREEN : ChatFormatting.GRAY), tooltip, 1);
		}
		GoggleLines.add(CELang.translate("goggles.sensor.signal", signal())
			.style(ChatFormatting.DARK_GRAY), tooltip, 1);
		if (isPlayerSneaking)
			GoggleLines.add(CELang.translate("goggles.heat_exchanger.hint")
				.style(ChatFormatting.DARK_GRAY), tooltip, 2);
		return true;
	}
}
