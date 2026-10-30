package com.createengines.content.transmission;

import java.util.List;

import com.createengines.advancement.Achievements;
import com.createengines.advancement.Milestone;
import com.createengines.advancement.Operators;
import com.createengines.content.GoggleLines;
import com.createengines.registry.CELang;
import com.simibubi.create.content.kinetics.RotationPropagator;
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

/** The gear a {@link TransmissionBlock} is in, and the speed modifier that gear gives the shaft's output half. */
public class TransmissionBlockEntity extends SplitShaftBlockEntity {

	private static final BehaviourType<ScrollValueBehaviour> GEAR_TYPE = new BehaviourType<>("transmission_gear");
	private static final int EVERY_GEAR = (1 << Gears.FIRST) | (1 << 2) | (1 << 3) | (1 << Gears.FOURTH)
		| (1 << Gears.REVERSE);

	public ScrollValueBehaviour panel;

	private int gear = Gears.FIRST;
	/** Bit for each gear that has carried drive, for the achievement for using them all. */
	private int gearsUsed;

	public TransmissionBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
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
		panel.between(Gears.NEUTRAL, Gears.REVERSE);
		panel.value = Gears.FIRST;
		panel.withFormatter(Gears::label);
		panel.withCallback(value -> updateGear());
		behaviours.add(panel);
	}

	/** The gear in use now. */
	public int gear() {
		return gear;
	}

	/** A redstone signal selects a gear outright; with none, the panel does. */
	private int wantedGear() {
		int selected = level == null ? -1 : Gears.fromSignal(level.getBestNeighborSignal(worldPosition));
		return selected >= 0 ? selected : panel.getValue();
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
		setChanged();
		sendData();
		level.scheduleTick(worldPosition, getBlockState().getBlock(), 0, TickPriority.EXTREMELY_HIGH);

		if (hasSource() && getSpeed() != 0) {
			List<BlockPos> self = List.of(worldPosition);
			Achievements.award(level, self, Milestone.SHIFTED);
			if (gear == Gears.REVERSE)
				Achievements.award(level, self, Milestone.REVERSE);
			gearsUsed |= 1 << gear;
			if ((gearsUsed & EVERY_GEAR) == EVERY_GEAR)
				Achievements.award(level, self, Milestone.EVERY_GEAR);
		}
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
		tag.putInt("GearsUsed", gearsUsed);
	}

	@Override
	protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
		super.read(tag, registries, clientPacket);
		gear = tag.contains("Gear") ? Gears.clamp(tag.getInt("Gear")) : Gears.FIRST;
		gearsUsed = tag.getInt("GearsUsed");
	}

	@Override
	public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
		CELang.translate("goggles.transmission")
			.forGoggles(tooltip);
		if (gear == Gears.NEUTRAL)
			GoggleLines.add(CELang.translate("goggles.transmission.neutral")
				.style(ChatFormatting.GOLD), tooltip, 1);
		else if (gear == Gears.REVERSE)
			GoggleLines.add(CELang.translate("goggles.transmission.reverse", Gears.ratio(gear))
				.style(ChatFormatting.AQUA), tooltip, 1);
		else
			GoggleLines.add(CELang.translate("goggles.transmission.gear", Gears.label(gear), Gears.ratio(gear))
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
