package com.createengines.content.transmission;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.createengines.advancement.Achievements;
import com.createengines.advancement.Milestone;
import com.createengines.advancement.Operators;
import com.createengines.content.GoggleLines;
import com.createengines.content.crankshaft.CrankshaftBlockEntity;
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

	public ScrollValueBehaviour panel;

	private int gear = Gears.FIRST;
	/** The gear auto has chosen; kept while another setting is selected so it can pick up where it was. */
	private int autoGear = Gears.FIRST;
	/** Whether auto was the setting at the last look, to start it from the gear in use. */
	private boolean wasAuto;
	private long lastShift;
	private int autoTicks;
	/** Bit for each gear that has carried drive, for the achievement for using them all. */
	private int gearsUsed;

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
		};
		panel.between(Gears.NEUTRAL, Gears.AUTO);
		panel.value = Gears.FIRST;
		panel.withFormatter(Gears::label);
		panel.withCallback(value -> updateGear());
		behaviours.add(panel);
	}

	/** The gear in use now. */
	public int gear() {
		return gear;
	}

	/** Whether the panel is on auto and no redstone signal is overriding it. */
	public boolean isAuto() {
		return panel != null && panel.getValue() == Gears.AUTO && signalGear() < 0;
	}

	private int signalGear() {
		return level == null ? -1 : Gears.fromSignal(level.getBestNeighborSignal(worldPosition));
	}

	/** A redstone signal selects a gear outright; with none, the panel does, and on auto the block's own choice. */
	private int wantedGear() {
		int selected = signalGear();
		if (selected >= 0) {
			wasAuto = false;
			return selected;
		}
		int setting = panel.getValue();
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
		int wanted = Gears.clamp(wantedGear());
		if (wanted == gear)
			return;
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
		if (isAuto())
			autoShift();
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
		int next = AutoGearbox.next(autoGear, utilization, (int) Math.min(Integer.MAX_VALUE, level.getGameTime() - lastShift));
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
			return Gears.speedModifier(gear);
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
	}

	@Override
	protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
		super.read(tag, registries, clientPacket);
		int saved = tag.contains("Gear") ? tag.getInt("Gear") : Gears.FIRST;
		if (!tag.getBoolean("ThirteenGears") && tag.contains("Gear")) {
			// Saved with four gears: first to fourth and reverse are other positions now
			saved = Gears.fromLegacy(saved);
			if (panel != null)
				panel.value = Gears.fromLegacy(panel.value);
		}
		gear = Gears.clamp(saved);
		autoGear = tag.contains("AutoGear") ? Gears.clamp(tag.getInt("AutoGear")) : gear;
		gearsUsed = tag.contains("ThirteenGears") ? tag.getInt("GearsUsed") : 0;
	}

	@Override
	public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
		CELang.translate("goggles.transmission")
			.forGoggles(tooltip);
		boolean auto = panel != null && panel.getValue() == Gears.AUTO;
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
		if (isPlayerSneaking)
			GoggleLines.add(CELang.translate("goggles.transmission.hint")
				.style(ChatFormatting.DARK_GRAY), tooltip, 2);
		super.addToGoggleTooltip(tooltip, isPlayerSneaking);
		return true;
	}

	/** A value box on any face the shaft does not run out of. */
	private static class ValueBox extends ValueBoxTransform.Sided {

		@Override
		protected Vec3 getSouthLocation() {
			return VecHelper.voxelSpace(8, 8, 15.5);
		}

		@Override
		protected boolean isSideActive(BlockState state, Direction direction) {
			return direction.getAxis() != state.getValue(BlockStateProperties.AXIS);
		}
	}
}
