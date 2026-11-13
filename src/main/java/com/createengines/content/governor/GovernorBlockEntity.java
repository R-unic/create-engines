package com.createengines.content.governor;

import java.util.List;

import com.createengines.content.GoggleLines;
import com.createengines.engine.sim.Governor;
import com.createengines.engine.topology.EngineMember;
import com.createengines.registry.CELang;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBehaviour.ValueSettings;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBoard;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsFormatter;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;

import net.createmod.catnip.math.VecHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The speed a {@link GovernorBlock} holds the engine at, and what the engine last told it. The governing is done in
 * the engine's controller, which reads {@link #governedSpeed()} every tick and reports back the speed and rack
 * position. A redstone signal on the block turns the setting down (see {@link Governor#commanded}); the block's own
 * output is for comparators only, so that it can't feed back into that input.
 */
public class GovernorBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation, EngineMember {

	/** The target is stored as an exact speed in RPM. Above {@link #EXACT_MAX} the value panel sets it in these steps. */
	public static final int COARSE_STEP = 5;
	/** The highest speed the value panel's exact row reaches: Create's default RPM limit. */
	public static final int EXACT_MAX = 256;
	public static final int MIN_TARGET = 20;
	public static final int MAX_TARGET = 1000;
	public static final int DEFAULT_TARGET = 80;
	/** Marks data written with the target in RPM; older data holds it in steps of {@link #COARSE_STEP}. */
	private static final String EXACT_KEY = "ExactRpm";
	private static final int SYNC_INTERVAL = 10;
	private static final BehaviourType<ScrollValueBehaviour> TARGET_TYPE = new BehaviourType<>("governed_speed");

	public ScrollValueBehaviour target;

	private int signal;
	private boolean onEngine;
	private float rpm;
	private float position = 1;
	/** The speed it was holding when the engine last told it, which a redstone signal may have turned down. */
	private float held;
	private int sinceSync = SYNC_INTERVAL;
	private boolean unsent;

	public GovernorBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	@Override
	public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
		target = new ScrollValueBehaviour(CELang.translate("value.governed_speed")
			.component(), this, new ValueBox()) {
			@Override
			public BehaviourType<?> getType() {
				return TARGET_TYPE;
			}

			/** Two rows: any exact speed up to a standard engine's limit, and steps of 5 for a raised one. */
			@Override
			public ValueSettingsBoard createBoard(Player player, BlockHitResult hitResult) {
				return new ValueSettingsBoard(label, EXACT_MAX, 16,
					List.of(CELang.translate("value.governed_speed.exact")
						.component(),
						CELang.translate("value.governed_speed.coarse")
							.component()),
					new ValueSettingsFormatter(settings -> Component.literal(rpmOf(settings) + " RPM")));
			}

			@Override
			public ValueSettings getValueSettings() {
				int rpm = getValue();
				return rpm <= EXACT_MAX ? new ValueSettings(0, rpm) : new ValueSettings(1, rpm / COARSE_STEP);
			}

			@Override
			public void setValueSettings(Player player, ValueSettings settings, boolean ctrlDown) {
				int rpm = Mth.clamp(rpmOf(settings), MIN_TARGET, MAX_TARGET);
				if (rpm == getValue())
					return;
				setValue(rpm);
				playFeedbackSound(this);
			}
		};
		target.between(MIN_TARGET, MAX_TARGET);
		target.value = DEFAULT_TARGET;
		target.withFormatter(v -> v + " RPM");
		behaviours.add(target);
	}

	/** The speed a value panel selection stands for: exact in the first row, in steps of 5 in the second. */
	private static int rpmOf(ValueSettings settings) {
		return settings.row() == 0 ? settings.value() : settings.value() * COARSE_STEP;
	}

	/** The speed the governor holds, RPM. */
	public double target() {
		return Math.max(MIN_TARGET, target.getValue());
	}

	/** The redstone strength at the block, which asks for a share of the setting. */
	public int command() {
		return level == null ? 0 : level.getBestNeighborSignal(worldPosition);
	}

	/** The speed it holds right now: the setting, turned down by whatever redstone signal reaches it. */
	public double governedSpeed() {
		return Governor.commanded(target(), command());
	}

	/** The redstone strength being put out for comparators: how far open the rack is held, 15 wide open. */
	public int signal() {
		return signal;
	}

	/** The rack position the block last held while on an engine, or -1 when it has none to give (never on one, or reset). */
	public double savedPosition() {
		return onEngine ? position : -1;
	}

	/** Called by the engine every tick with the crank's speed, where the rack is and the speed being held. */
	public void reading(float rpm, float position, float held) {
		boolean changed = !onEngine || Math.abs(position - this.position) >= 0.02f || Math.abs(rpm - this.rpm) >= 1
			|| Math.abs(held - this.held) >= 1;
		onEngine = true;
		this.rpm = rpm;
		this.position = position;
		this.held = held;
		updateSignal(Governor.signal(position));
		unsent |= changed;
	}

	@Override
	public void detachFromEngine() {
		if (!onEngine)
			return;
		onEngine = false;
		rpm = 0;
		position = 1;
		held = 0;
		updateSignal(0);
		unsent = true;
	}

	private void updateSignal(int next) {
		if (next == signal)
			return;
		signal = next;
		setChanged();
		if (level != null && !level.isClientSide)
			level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
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
		tag.putFloat("Position", position);
		tag.putFloat("Held", held);
		tag.putBoolean(EXACT_KEY, true);
	}

	@Override
	protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
		super.read(tag, registries, clientPacket);
		signal = tag.getInt("Signal");
		onEngine = tag.getBoolean("OnEngine");
		rpm = tag.getFloat("Rpm");
		position = tag.contains("Position") ? tag.getFloat("Position") : 1;
		held = tag.getFloat("Held");
		// Saved before the target was exact: its value is in steps of 5 RPM
		if (target != null && tag.contains("ScrollValue") && !tag.contains(EXACT_KEY))
			target.value = Mth.clamp(target.value * COARSE_STEP, MIN_TARGET, MAX_TARGET);
	}

	@Override
	public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
		CELang.translate("goggles.governor")
			.forGoggles(tooltip);
		if (!onEngine)
			GoggleLines.add(CELang.translate("goggles.governor.no_engine")
				.style(ChatFormatting.RED), tooltip, 1);
		else {
			GoggleLines.add(CELang.translate("goggles.governor.speed", Math.round(rpm))
				.style(ChatFormatting.AQUA), tooltip, 1);
			int rack = Math.round(position * 100);
			GoggleLines.add(CELang.translate(rack >= 99 ? "goggles.governor.open" : "goggles.governor.rack", rack)
				.style(rack >= 99 ? ChatFormatting.GOLD : ChatFormatting.GRAY), tooltip, 1);
		}
		GoggleLines.add(CELang.translate("goggles.governor.target", Math.round(target()))
			.style(ChatFormatting.DARK_AQUA), tooltip, 1);
		if (onEngine && held < target() - 0.5)
			GoggleLines.add(CELang.translate("goggles.governor.commanded", Math.round(held))
				.style(ChatFormatting.GREEN), tooltip, 1);
		GoggleLines.add(CELang.translate("goggles.sensor.signal", signal)
			.style(ChatFormatting.DARK_GRAY), tooltip, 1);
		if (isPlayerSneaking)
			GoggleLines.add(CELang.translate("goggles.governor.hint", Math.round(Governor.BAND * 100))
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
