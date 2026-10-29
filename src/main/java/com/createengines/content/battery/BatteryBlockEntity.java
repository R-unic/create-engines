package com.createengines.content.battery;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.createengines.engine.topology.EngineMember;
import com.createengines.registry.CELang;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Charge is counted in sparks. An engine draws one spark's worth every time one of its plugs fires, and an alternator
 * puts it back. A flat battery gives no spark, so a spark engine needs its battery charged before it will start,
 * which is what cranking does when an alternator sits on the crank's own shaft line.
 */
public class BatteryBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation, EngineMember {

	public static final float CAPACITY = 200;
	private static final int SYNC_INTERVAL = 10;

	private float charge;
	private @Nullable BlockPos primaryPos;
	private float syncedCharge = -1;
	private int ticks;

	public BatteryBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	@Override
	public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
	}

	public float charge() {
		return charge;
	}

	public float chargeFraction() {
		return charge / CAPACITY;
	}

	public boolean isFull() {
		return charge >= CAPACITY;
	}

	/** Takes up to {@code sparks} of charge and returns how much was actually there. */
	public float draw(float sparks) {
		float drawn = Math.min(charge, sparks);
		charge -= drawn;
		return drawn;
	}

	/** Takes in up to {@code sparks} of charge and returns how much there was room for. */
	public float charge(float sparks) {
		float taken = Math.max(0, Math.min(CAPACITY - charge, sparks));
		charge += taken;
		return taken;
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
	public void tick() {
		super.tick();
		if (level == null || level.isClientSide)
			return;
		if (++ticks % SYNC_INTERVAL == 0 && Math.abs(charge - syncedCharge) >= CAPACITY * 0.01f) {
			syncedCharge = charge;
			sendData();
		}
	}

	@Override
	protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
		super.write(tag, registries, clientPacket);
		tag.putFloat("Charge", charge);
		if (primaryPos != null)
			tag.put("Primary", NbtUtils.writeBlockPos(primaryPos));
	}

	@Override
	protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
		super.read(tag, registries, clientPacket);
		charge = tag.getFloat("Charge");
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
		if (primaryPos == null)
			CELang.translate("goggles.battery.no_engine")
				.style(ChatFormatting.GOLD)
				.forGoggles(tooltip, 1);
		return true;
	}
}
