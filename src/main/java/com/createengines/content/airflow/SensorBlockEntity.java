package com.createengines.content.airflow;

import java.util.List;

import com.createengines.engine.topology.EngineMember;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What a {@link SensorBlock} knows. The engine pushes a reading in every tick; the redstone signal follows it at once,
 * and what goggles show is sent to clients at most twice a second.
 */
public abstract class SensorBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation, EngineMember {

	private static final int SYNC_INTERVAL = 10;

	private int signal;
	private int sinceSync = SYNC_INTERVAL;
	private boolean unsent;

	protected SensorBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	@Override
	public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
	}

	/** The redstone strength this sensor is putting out, 0 to 15. */
	public int signal() {
		return signal;
	}

	/** Whether an engine is feeding this sensor; false until it joins one and again once it leaves. */
	public abstract boolean onEngine();

	/** Puts out a new signal, telling whatever it powers if it changed. */
	protected void setSignal(int next) {
		if (next == signal)
			return;
		signal = next;
		unsent = true;
		setChanged();
		if (level != null && !level.isClientSide)
			level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
	}

	/** Something goggles show has changed: send it to clients soon. */
	protected void displayChanged() {
		unsent = true;
	}

	@Override
	public void tick() {
		super.tick();
		if (level == null || level.isClientSide)
			return;
		if (sinceSync < SYNC_INTERVAL)
			sinceSync++;
		if (unsent && sinceSync >= SYNC_INTERVAL) {
			unsent = false;
			sinceSync = 0;
			sendData();
		}
	}

	@Override
	protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
		super.write(tag, registries, clientPacket);
		tag.putInt("Signal", signal);
	}

	@Override
	protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
		super.read(tag, registries, clientPacket);
		signal = tag.getInt("Signal");
	}
}
