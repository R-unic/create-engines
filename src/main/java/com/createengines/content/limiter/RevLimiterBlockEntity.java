package com.createengines.content.limiter;

import java.util.List;

import com.createengines.content.GoggleLines;
import com.createengines.engine.sim.RevLimiter;
import com.createengines.engine.sim.Sensors;
import com.createengines.engine.topology.EngineMember;
import com.createengines.registry.CELang;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;

import net.createmod.catnip.math.VecHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The speed a {@link RevLimiterBlock} cuts fuel at, and what the engine last told it. The cutting is done in the
 * engine's controller, which reads {@link #limit()} every tick and reports back the speed and whether it cut.
 */
public class RevLimiterBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation, EngineMember {

	/** The limit is set in steps of this many RPM. */
	public static final int LIMIT_STEP = 5;
	public static final int MIN_LIMIT = 4;
	public static final int MAX_LIMIT = 50;
	public static final int DEFAULT_LIMIT = 20;
	private static final int SYNC_INTERVAL = 10;
	private static final BehaviourType<ScrollValueBehaviour> LIMIT_TYPE = new BehaviourType<>("rev_limit");

	public ScrollValueBehaviour limit;

	private int signal;
	private boolean onEngine;
	private float rpm;
	private boolean cutting;
	private int sinceSync = SYNC_INTERVAL;
	private boolean unsent;

	public RevLimiterBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	@Override
	public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
		limit = new ScrollValueBehaviour(CELang.translate("value.rev_limit")
			.component(), this, new ValueBox()) {
			@Override
			public BehaviourType<?> getType() {
				return LIMIT_TYPE;
			}
		};
		limit.between(MIN_LIMIT, MAX_LIMIT);
		limit.value = DEFAULT_LIMIT;
		limit.withFormatter(v -> v * LIMIT_STEP + " RPM");
		behaviours.add(limit);
	}

	/** The speed fuel is cut at, RPM. */
	public double limit() {
		return Math.max(MIN_LIMIT, limit.getValue()) * LIMIT_STEP;
	}

	/** The redstone strength being put out: how close the engine is to the limit, 15 at it. */
	public int signal() {
		return signal;
	}

	/** Called by the engine every tick with the crank's speed and whether the injectors are being held shut. */
	public void reading(float rpm, boolean cutting) {
		boolean changed = !onEngine || cutting != this.cutting || Math.abs(rpm - this.rpm) >= 1;
		onEngine = true;
		this.rpm = rpm;
		this.cutting = cutting;
		int next = Sensors.limiterSignal(rpm, limit());
		if (next != signal) {
			signal = next;
			setChanged();
			if (level != null && !level.isClientSide)
				level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
		}
		unsent |= changed;
	}

	@Override
	public void detachFromEngine() {
		if (!onEngine)
			return;
		onEngine = false;
		rpm = 0;
		cutting = false;
		if (signal != 0) {
			signal = 0;
			setChanged();
			if (level != null && !level.isClientSide)
				level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
		}
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
		tag.putBoolean("OnEngine", onEngine);
		tag.putFloat("Rpm", rpm);
		tag.putBoolean("Cutting", cutting);
	}

	@Override
	protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
		super.read(tag, registries, clientPacket);
		signal = tag.getInt("Signal");
		onEngine = tag.getBoolean("OnEngine");
		rpm = tag.getFloat("Rpm");
		cutting = tag.getBoolean("Cutting");
	}

	@Override
	public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
		CELang.translate("goggles.limiter")
			.forGoggles(tooltip);
		if (!onEngine)
			GoggleLines.add(CELang.translate("goggles.limiter.no_engine")
				.style(ChatFormatting.RED), tooltip, 1);
		else {
			GoggleLines.add(CELang.translate("goggles.limiter.speed", Math.round(rpm))
				.style(ChatFormatting.AQUA), tooltip, 1);
			if (cutting)
				GoggleLines.add(CELang.translate("goggles.limiter.cutting")
					.style(ChatFormatting.GOLD), tooltip, 1);
		}
		GoggleLines.add(CELang.translate("goggles.limiter.limit", Math.round(limit()))
			.style(ChatFormatting.DARK_AQUA), tooltip, 1);
		GoggleLines.add(CELang.translate("goggles.sensor.signal", signal)
			.style(ChatFormatting.DARK_GRAY), tooltip, 1);
		if (isPlayerSneaking)
			GoggleLines.add(CELang.translate("goggles.limiter.hint", Math.round(RevLimiter.RESUME * 100))
				.style(ChatFormatting.DARK_GRAY), tooltip, 2);
		return true;
	}

	/** A value box on any side face of the casing. */
	private static class ValueBox extends ValueBoxTransform.Sided {

		@Override
		protected Vec3 getSouthLocation() {
			return VecHelper.voxelSpace(8, 5, 15);
		}

		@Override
		protected boolean isSideActive(BlockState state, Direction direction) {
			return direction.getAxis()
				.isHorizontal();
		}
	}
}
