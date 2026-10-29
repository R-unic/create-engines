package com.createengines.content.alternator;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import com.createengines.content.battery.BatteryBlockEntity;
import com.createengines.engine.topology.EngineMember;
import com.createengines.registry.CELang;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Charges batteries while its shaft turns: those touching it, and those of the engine it touches. The charge it makes
 * is shared out between them, and it only puts stress on its shaft while there is a battery to fill.
 */
public class AlternatorBlockEntity extends KineticBlockEntity implements EngineMember {

	/** Sparks of charge made per tick, per RPM of the shaft. */
	private static final float CHARGE_PER_RPM = 0.01f;
	/** Stress per RPM the alternator puts on its shaft while it is charging. */
	private static final float CHARGING_STRESS = 2;

	private @Nullable BlockPos primaryPos;
	/** Batteries of the engine this alternator touches, set by the engine. */
	private List<BlockPos> engineBatteries = List.of();
	private boolean charging;
	private int batteriesFound;

	public AlternatorBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	public void attachToEngine(BlockPos primary, List<BlockPos> batteries) {
		if (primary.equals(primaryPos) && batteries.equals(engineBatteries))
			return;
		primaryPos = primary;
		engineBatteries = List.copyOf(batteries);
	}

	@Override
	public void detachFromEngine() {
		primaryPos = null;
		engineBatteries = List.of();
	}

	@Override
	public float calculateStressApplied() {
		lastStressApplied = charging ? CHARGING_STRESS : 0;
		return lastStressApplied;
	}

	private List<BatteryBlockEntity> batteries() {
		Set<BlockPos> positions = new LinkedHashSet<>();
		for (Direction direction : Direction.values())
			positions.add(worldPosition.relative(direction));
		positions.addAll(engineBatteries);
		List<BatteryBlockEntity> found = new ArrayList<>();
		for (BlockPos pos : positions)
			if (level.isLoaded(pos) && level.getBlockEntity(pos) instanceof BatteryBlockEntity battery)
				found.add(battery);
		return found;
	}

	@Override
	public void tick() {
		super.tick();
		if (level == null || level.isClientSide)
			return;
		boolean turning = getSpeed() != 0 && !isOverStressed();
		List<BatteryBlockEntity> batteries = turning ? batteries() : List.of();
		float budget = turning ? Math.abs(getSpeed()) * CHARGE_PER_RPM : 0;
		boolean wanted = false;
		for (BatteryBlockEntity battery : batteries) {
			if (battery.isFull())
				continue;
			wanted = true;
			budget -= battery.charge(budget);
		}
		batteriesFound = batteries.size();
		if (wanted != charging) {
			charging = wanted;
			if (hasNetwork())
				getOrCreateNetwork().updateStressFor(this, calculateStressApplied());
			sendData();
		}
	}

	@Override
	protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
		super.write(tag, registries, clientPacket);
		tag.putBoolean("Charging", charging);
		tag.putInt("Batteries", batteriesFound);
	}

	@Override
	protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
		super.read(tag, registries, clientPacket);
		charging = tag.getBoolean("Charging");
		batteriesFound = tag.getInt("Batteries");
	}

	@Override
	public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
		CELang.translate("goggles.alternator")
			.forGoggles(tooltip);
		String key;
		ChatFormatting colour;
		if (charging) {
			key = "goggles.alternator.charging";
			colour = ChatFormatting.GREEN;
		} else if (batteriesFound == 0 && getSpeed() != 0) {
			key = "goggles.alternator.no_battery";
			colour = ChatFormatting.GOLD;
		} else {
			key = "goggles.alternator.idle";
			colour = ChatFormatting.GRAY;
		}
		CELang.translate(key)
			.style(colour)
			.forGoggles(tooltip, 1);
		super.addToGoggleTooltip(tooltip, isPlayerSneaking);
		return true;
	}
}
