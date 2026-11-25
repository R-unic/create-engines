package com.createengines.content.transmission;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.createengines.advancement.Achievements;
import com.createengines.advancement.Milestone;
import com.createengines.advancement.Operators;
import com.createengines.content.GoggleLines;
import com.createengines.content.crankshaft.CrankshaftBlockEntity;
import com.createengines.engine.Signals;
import com.createengines.registry.CELang;
import com.simibubi.create.content.kinetics.RotationPropagator;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.transmission.SplitShaftBlockEntity;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.ticks.TickPriority;

/**
 * The gear a {@link TransmissionBlock} is in, and the speed modifier that gear gives the shaft's output half. On the
 * panel's auto setting the block picks the gear itself, from how hard the engine behind it is being asked to pull
 * (see {@link AutoGearbox}).
 */
public class TransmissionBlockEntity extends SplitShaftBlockEntity {

	private static final BehaviourType<ScrollValueBehaviour> GEAR_TYPE = new BehaviourType<>("transmission_gear");
	private static final int EVERY_GEAR = everyGear();
	/** Ticks between looks at the engine while in auto. */
	private static final int AUTO_INTERVAL = 10;
	/** The farthest it follows the drive back along the network to find the engine. */
	private static final int MAX_WALK = 64;

	private static final BehaviourType<ScrollValueBehaviour> PROFILE_TYPE = new BehaviourType<>("transmission_shift_profile");
	/** Where the profile's value box sits on a face, below the gear's (voxels up from the face's bottom). */
	private static final double SHIFT_PROFILE_Y = 4.5;
	private static final double GEAR_Y = 11.5;

	public ScrollValueBehaviour panel;
	/** Auto's shift profile, see {@link AutoGearbox.Profile}. */
	public ScrollValueBehaviour shiftProfile;

	private int gear = Gears.NEUTRAL;
	/** The setting a redstone signal chose at the last look, or -1. Sent to the client for the dial and goggles, never saved. */
	private int signalSetting = -1;
	/** The gear auto has chosen; kept while another setting is selected so it can pick up where it was. */
	private int autoGear = Gears.FIRST;
	/** Whether auto was the setting at the last look, to start it from the gear in use. */
	private boolean wasAuto;
	private long lastShift;
	private int autoTicks;
	/** Bit for each gear that has carried drive, for the achievement for using them all. */
	private int gearsUsed;
	/**
	 * The end an engine drives it from, once it has been driven by one. Drive arriving at the other end is refused, so
	 * that an engine that stops (a broken crank throw) does not leave whatever sits on the other side driving the crank
	 * backwards through the box, with the input and output swapped.
	 */
	private @Nullable Direction inputFace;

	public TransmissionBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	private static int everyGear() {
		int bits = 1 << Gears.REVERSE;
		for (int gear = Gears.FIRST; gear <= Gears.TOP; gear++)
			bits |= 1 << gear;
		return bits;
	}

	@Override
	public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
		super.addBehaviours(behaviours);
		panel = new ScrollValueBehaviour(CELang.translate("value.gear")
			.component(), this, new ValueBox()) {
			@Override
			public BehaviourType<?> getType() {
				return GEAR_TYPE;
			}

			@Override
			public void setValueSettings(Player player, ValueSettings settings, boolean ctrlDown) {
				Operators.record(getWorld(), getPos(), player);
				super.setValueSettings(player, settings, ctrlDown);
			}

			/** Under a redstone signal the dial reads what that signal picked; the panel's setting is kept for afterwards. */
			@Override
			public String formatValue() {
				if (signalSetting < 0)
					return super.formatValue();
				return Gears.label(signalSetting == Gears.AUTO ? Gears.AUTO : gear);
			}
		};
		panel.between(Gears.NEUTRAL, Gears.AUTO);
		panel.value = Gears.NEUTRAL;
		panel.withFormatter(Gears::label);
		panel.withCallback(value -> updateGear());
		behaviours.add(panel);

		shiftProfile = new ProfileDial(CELang.translate("value.shift_profile")
			.component(), this, new ValueBox(SHIFT_PROFILE_Y));
		shiftProfile.between(0, AutoGearbox.Profile.values().length - 1);
		shiftProfile.value = AutoGearbox.Profile.NORMAL.setting();
		shiftProfile.withFormatter(v -> CELang.translate("value.shift_profile." + v)
			.string());
		behaviours.add(shiftProfile);
	}

	/** How eager auto is to shift: economy, normal (the original, and the default) or sport. */
	public AutoGearbox.Profile profile() {
		return shiftProfile == null ? AutoGearbox.Profile.NORMAL : AutoGearbox.Profile.fromSetting(shiftProfile.getValue());
	}

	/** The gear in use now. */
	public int gear() {
		return gear;
	}

	/**
	 * Whether auto is in charge: picked by a redstone signal, or on the panel with no signal. It reads the signal as {@link #updateGear} last
	 * saw it (at each neighbour change, and again each lazy tick), which on a client is the one the server sent, so it costs no look at
	 * the neighbours.
	 */
	public boolean isAuto() {
		return panel != null && settingUnder(signalSetting) == Gears.AUTO;
	}

	/** A signal's setting if there is one, otherwise the panel's. */
	private int settingUnder(int signal) {
		return signal >= 0 ? signal : panel.getValue();
	}

	private int signalNow() {
		if (level == null)
			return -1;
		// Reading a signal looks at every neighbour, and at a chunk's edge that would load the chunk beside it
		if (!Signals.neighboursLoaded(level, worldPosition))
			return -1;
		return Gears.fromSignal(Signals.bestNeighbour(level, worldPosition));
	}

	/** A redstone signal's setting beats the panel's; on auto, from either, the gear is the block's own choice. */
	private int wantedGear(int signal) {
		int setting = settingUnder(signal);
		if (setting != Gears.AUTO) {
			wasAuto = false;
			return setting;
		}
		if (!wasAuto) {
			wasAuto = true;
			autoGear = Gears.isForward(gear) ? gear : Gears.FIRST;
		}
		return autoGear;
	}

	/** Shifts if the gear asked for is not the one in use. The network is detached while it changes, as for a gearshift. */
	public void updateGear() {
		if (level == null || level.isClientSide || panel == null)
			return;
		int signal = signalNow();
		int wanted = Gears.clamp(wantedGear(signal));
		boolean signalChanged = signalSetting != signal;
		signalSetting = signal;
		if (wanted == gear) {
			// A signal that picks the gear already in use shifts nothing, but the dial still has to hear of it
			if (signalChanged)
				sendData();
			return;
		}
		RotationPropagator.handleRemoved(level, worldPosition, this);
		gear = wanted;
		lastShift = level.getGameTime();
		setChanged();
		sendData();
		level.scheduleTick(worldPosition, getBlockState().getBlock(), 0, TickPriority.EXTREMELY_HIGH);

		if (hasSource() && getSpeed() != 0) {
			List<BlockPos> self = List.of(worldPosition);
			Achievements.award(level, self, Milestone.SHIFTED);
			if (gear == Gears.REVERSE)
				Achievements.award(level, self, Milestone.REVERSE);
			if (isAuto())
				Achievements.award(level, self, Milestone.AUTO_SHIFTED);
			gearsUsed |= 1 << gear;
			if ((gearsUsed & EVERY_GEAR) == EVERY_GEAR)
				Achievements.award(level, self, Milestone.EVERY_GEAR);
		}
	}

	@Override
	public void tick() {
		super.tick();
		if (level == null || level.isClientSide || ++autoTicks < AUTO_INTERVAL)
			return;
		autoTicks = 0;
		trackInput();
		if (isAuto())
			autoShift();
	}

	/** Remembers the end an engine drives this from, and forgets it when nothing shaft-like is left there. */
	private void trackInput() {
		if (inputFace != null) {
			BlockPos beside = worldPosition.relative(inputFace);
			if (!level.isLoaded(beside))
				return;
			BlockState state = level.getBlockState(beside);
			if (!(state.getBlock() instanceof com.simibubi.create.content.kinetics.base.IRotate rotate)
				|| !rotate.hasShaftTowards(level, beside, state, inputFace.getOpposite())) {
				inputFace = null;
				setChanged();
			}
			return;
		}
		if (hasSource() && getSpeed() != 0 && engine() != null) {
			inputFace = getSourceFacing();
			setChanged();
		}
	}

	/** Looks at the engine and moves auto's choice a gear if it should. */
	private void autoShift() {
		if (!wasAuto) {
			updateGear();
			return;
		}
		CrankshaftBlockEntity engine = engine();
		if (engine == null)
			return;
		double speed = Math.abs(engine.getTheoreticalSpeed());
		float capacity = engine.publishedCapacity();
		if (speed < 1 || capacity <= 0)
			return;
		double utilization = engine.networkStress() / (speed * capacity);
		int next = AutoGearbox.next(profile(), autoGear, utilization, (int) Math.min(Integer.MAX_VALUE, level.getGameTime() - lastShift));
		if (next != autoGear) {
			autoGear = next;
			updateGear();
		}
	}

	/** The primary crankshaft of the engine driving this block, found by following the drive back to its source. */
	private @Nullable CrankshaftBlockEntity engine() {
		KineticBlockEntity at = this;
		for (int steps = 0; steps < MAX_WALK && at != null; steps++) {
			if (at instanceof CrankshaftBlockEntity crank) {
				BlockPos primary = crank.primaryPos();
				if (primary != null && level.getBlockEntity(primary) instanceof CrankshaftBlockEntity main)
					return main;
				return crank;
			}
			if (at.source == null || !level.isLoaded(at.source))
				return null;
			at = level.getBlockEntity(at.source) instanceof KineticBlockEntity next ? next : null;
		}
		return null;
	}

	@Override
	public void lazyTick() {
		super.lazyTick();
		updateGear();
	}

	@Override
	public float getRotationSpeedModifier(Direction face) {
		if (hasSource() && face != getSourceFacing())
			return inputFace != null && getSourceFacing() != inputFace ? 0 : Gears.speedModifier(gear);
		return 1;
	}

	@Override
	public float calculateStressApplied() {
		lastStressApplied = 0;
		return 0;
	}

	@Override
	protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
		super.write(tag, registries, clientPacket);
		tag.putInt("Gear", gear);
		tag.putInt("AutoGear", autoGear);
		tag.putInt("GearsUsed", gearsUsed);
		tag.putBoolean("ThirteenGears", true);
		if (inputFace != null)
			tag.putInt("InputFace", inputFace.get3DDataValue());
		if (clientPacket)
			tag.putInt("SignalSetting", signalSetting);
	}

	@Override
	protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
		super.read(tag, registries, clientPacket);
		int saved = tag.contains("Gear") ? tag.getInt("Gear") : Gears.NEUTRAL;
		if (!tag.getBoolean("ThirteenGears") && tag.contains("Gear")) {
			// Saved with four gears: first to fourth and reverse are other positions now
			saved = Gears.fromLegacy(saved);
			if (panel != null)
				panel.value = Gears.fromLegacy(panel.value);
		}
		inputFace = tag.contains("InputFace") ? Direction.from3DDataValue(tag.getInt("InputFace")) : null;
		gear = Gears.clamp(saved);
		autoGear = tag.contains("AutoGear") ? Gears.clamp(tag.getInt("AutoGear")) : gear;
		gearsUsed = tag.contains("ThirteenGears") ? tag.getInt("GearsUsed") : 0;
		if (clientPacket)
			signalSetting = tag.contains("SignalSetting") ? tag.getInt("SignalSetting") : -1;
	}

	@Override
	public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
		CELang.translate("goggles.transmission")
			.forGoggles(tooltip);
		boolean auto = isAuto();
		if (gear == Gears.NEUTRAL)
			GoggleLines.add(CELang.translate("goggles.transmission.neutral")
				.style(ChatFormatting.GOLD), tooltip, 1);
		else if (gear == Gears.REVERSE)
			GoggleLines.add(CELang.translate("goggles.transmission.reverse", Gears.ratio(gear))
				.style(ChatFormatting.AQUA), tooltip, 1);
		else
			GoggleLines.add(CELang.translate(auto ? "goggles.transmission.auto" : "goggles.transmission.gear",
					Gears.label(gear), Gears.ratio(gear))
				.style(ChatFormatting.AQUA), tooltip, 1);
		if (auto)
			GoggleLines.add(CELang.translate("goggles.transmission.profile",
					CELang.translate("value.shift_profile." + profile().setting()).string())
				.style(ChatFormatting.DARK_AQUA), tooltip, 1);
		if (isPlayerSneaking)
			GoggleLines.add(CELang.translate("goggles.transmission.hint")
				.style(ChatFormatting.DARK_GRAY), tooltip, 2);
		super.addToGoggleTooltip(tooltip, isPlayerSneaking);
		return true;
	}

	/**
	 * The shift profile. It saves under its own key and never touches the shared "ScrollValue" the gear panel uses, so a
	 * transmission saved before profiles existed reads as normal and the gear is never overwritten.
	 */
	private static final class ProfileDial extends ScrollValueBehaviour {

		ProfileDial(Component label, TransmissionBlockEntity be, ValueBoxTransform slot) {
			super(label, be, slot);
		}

		@Override
		public BehaviourType<?> getType() {
			return PROFILE_TYPE;
		}

		@Override
		public void setValueSettings(Player player, ValueSettings settings, boolean ctrlDown) {
			Operators.record(getWorld(), getPos(), player);
			super.setValueSettings(player, settings, ctrlDown);
		}

		/**
		 * Create sends a player's setting to the first value box whose network id matches. Both dials said 0, so a change
		 * made on the shift profile went to the gear panel, which is first in line.
		 */
		@Override
		public int netId() {
			return 1;
		}

		/** Create's clipboard copies settings under this key, so each dial needs its own. */
		@Override
		public String getClipboardKey() {
			return "ShiftProfile";
		}

		@Override
		public void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
			tag.putInt("ShiftProfile", value);
		}

		@Override
		public void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
			value = tag.contains("ShiftProfile") ? Math.max(0, Math.min(AutoGearbox.Profile.values().length - 1, tag.getInt("ShiftProfile")))
				: AutoGearbox.Profile.NORMAL.setting();
		}
	}

	/** A value box on any face the shaft does not run out of. */
	private static class ValueBox extends ValueBoxTransform.Sided {

		private final double y;

		ValueBox() {
			this(GEAR_Y);
		}

		ValueBox(double y) {
			this.y = y;
		}

		@Override
		protected Vec3 getSouthLocation() {
			return VecHelper.voxelSpace(8, y, 15.5);
		}

		@Override
		protected boolean isSideActive(BlockState state, Direction direction) {
			return direction.getAxis() != state.getValue(BlockStateProperties.AXIS);
		}
	}
}
