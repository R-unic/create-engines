package com.createengines.content.battery;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.createengines.engine.topology.EngineMember;
import com.createengines.registry.CELang;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Charge is counted in sparks. It builds while the shaft turns, and an engine draws one spark's worth every time one of
 * its plugs fires. A flat battery gives no spark, so a spark engine needs it spun before it will start, which is what
 * cranking does when the battery sits on the crank's own shaft line.
 */
public class BatteryBlockEntity extends KineticBlockEntity implements EngineMember {

	public static final float CAPACITY = 200;
	/** Sparks of charge added per tick, per RPM of the shaft. */
	private static final float CHARGE_PER_RPM = 0.01f;
	/** Stress per RPM the battery puts on its shaft while it is taking charge. */
	private static final float CHARGING_STRESS = 2;
	private static final int SYNC_INTERVAL = 10;

	private float charge;
	private boolean charging;
	private @Nullable BlockPos primaryPos;
	private float syncedCharge = -1;
	private int ticks;

	public BatteryBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	public float charge() {
		return charge;
	}

	public float chargeFraction() {
		return charge / CAPACITY;
	}

	/** Takes up to {@code sparks} of charge and returns how much was actually there. */
	public float draw(float sparks) {
		float drawn = Math.min(charge, sparks);
		charge -= drawn;
		return drawn;
	}

	public void attachToEngine(BlockPos primary) {
		if (primary.equals(primaryPos))
			return;
		primaryPos = primary;
		sendData();
	}

	@Override
	public void detachFromEngine() {
		if (primaryPos == null)
			return;
		primaryPos = null;
		sendData();
	}

	@Override
	public float calculateStressApplied() {
		lastStressApplied = charging ? CHARGING_STRESS : 0;
		return lastStressApplied;
	}

	@Override
	public void tick() {
		super.tick();
		if (level == null || level.isClientSide)
			return;
		boolean takingCharge = getSpeed() != 0 && charge < CAPACITY && !isOverStressed();
		if (takingCharge)
			charge = Math.min(CAPACITY, charge + Math.abs(getSpeed()) * CHARGE_PER_RPM);
		if (takingCharge != charging) {
			charging = takingCharge;
			if (hasNetwork())
				getOrCreateNetwork().updateStressFor(this, calculateStressApplied());
			sendData();
		}
		if (++ticks % SYNC_INTERVAL == 0 && Math.abs(charge - syncedCharge) >= CAPACITY * 0.01f) {
			syncedCharge = charge;
			sendData();
		}
	}

	@Override
	protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
		super.write(tag, registries, clientPacket);
		tag.putFloat("Charge", charge);
		tag.putBoolean("Charging", charging);
		if (primaryPos != null)
			tag.put("Primary", NbtUtils.writeBlockPos(primaryPos));
	}

	@Override
	protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
		super.read(tag, registries, clientPacket);
		charge = tag.getFloat("Charge");
		charging = tag.getBoolean("Charging");
		primaryPos = NbtUtils.readBlockPos(tag, "Primary")
			.orElse(null);
	}

	@Override
	public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
		CELang.translate("goggles.battery")
			.forGoggles(tooltip);
		int percent = Math.round(chargeFraction() * 100);
		CELang.translate("goggles.battery.charge", percent)
			.style(percent == 0 ? ChatFormatting.RED : percent < 20 ? ChatFormatting.GOLD : ChatFormatting.GREEN)
			.forGoggles(tooltip, 1);
		if (percent == 0)
			CELang.translate("goggles.battery.flat")
				.style(ChatFormatting.GRAY)
				.forGoggles(tooltip, 1);
		else if (charging)
			CELang.translate("goggles.battery.charging")
				.style(ChatFormatting.GRAY)
				.forGoggles(tooltip, 1);
		if (primaryPos == null)
			CELang.translate("goggles.battery.no_engine")
				.style(ChatFormatting.GOLD)
				.forGoggles(tooltip, 1);
		super.addToGoggleTooltip(tooltip, isPlayerSneaking);
		return true;
	}
}
