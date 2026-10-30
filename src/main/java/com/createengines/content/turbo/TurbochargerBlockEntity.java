package com.createengines.content.turbo;

import java.util.List;
import java.util.Locale;

import org.jetbrains.annotations.Nullable;

import com.createengines.engine.TurboStatus;
import com.createengines.engine.sim.Turbocharger;
import com.createengines.engine.topology.EngineMember;
import com.createengines.content.GoggleLines;
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
 * The wastegate setting of a {@link TurbochargerBlock}, and what the engine tells it about the wheel. The spool and
 * boost live in the engine's simulation; this holds the last figures it sent, for goggles and for the wheel to turn by.
 */
public class TurbochargerBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation, EngineMember {

	/** The boost limit is set in steps of this many tenths of a bar. */
	public static final int LIMIT_STEP = 1;
	public static final int MIN_LIMIT = 1;
	public static final int MAX_LIMIT = (int) Math.round(Turbocharger.MAX_LIMIT * 10);
	public static final int DEFAULT_LIMIT = (int) Math.round(Turbocharger.DEFAULT_LIMIT * 10);
	private static final BehaviourType<ScrollValueBehaviour> LIMIT_TYPE = new BehaviourType<>("turbo_limit");
	/** Wheel degrees turned per tick at design speed. */
	private static final float WHEEL_DEGREES_PER_TICK = 60;

	public ScrollValueBehaviour limit;

	private @Nullable TurboStatus status;
	private float wheelAngle;
	private float wheelSpeed;

	public TurbochargerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	@Override
	public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
		limit = new ScrollValueBehaviour(CELang.translate("value.boost_limit")
			.component(), this, new ValueBox()) {
			@Override
			public BehaviourType<?> getType() {
				return LIMIT_TYPE;
			}
		};
		limit.between(MIN_LIMIT, MAX_LIMIT);
		limit.value = DEFAULT_LIMIT;
		limit.withFormatter(v -> String.format(Locale.ROOT, "%.1f bar", v / 10f));
		behaviours.add(limit);
	}

	/** The boost the wastegate is set to hold, atmospheres. */
	public double boostLimit() {
		return Math.max(MIN_LIMIT, limit.getValue()) / 10d;
	}

	/** A redstone signal opens the wastegate fully, dumping the boost like a blow-off valve. */
	public boolean dumping() {
		return level != null && level.hasNeighborSignal(worldPosition);
	}

	public @Nullable TurboStatus status() {
		return status;
	}

	public void updateStatus(TurboStatus next) {
		if (!next.differsFrom(status))
			return;
		status = next;
		sendData();
	}

	@Override
	public void detachFromEngine() {
		if (status == null)
			return;
		status = null;
		sendData();
	}

	@Override
	public void tick() {
		super.tick();
		if (level != null && level.isClientSide) {
			float target = status == null ? 0 : status.spool() * WHEEL_DEGREES_PER_TICK;
			wheelSpeed += (target - wheelSpeed) * 0.15f;
			wheelAngle = (wheelAngle + wheelSpeed) % 360;
		}
	}

	/** The wheel's angle, degrees, interpolated between ticks. */
	public float wheelAngle(float partialTicks) {
		return (wheelAngle + wheelSpeed * partialTicks) % 360;
	}

	@Override
	protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
		super.write(tag, registries, clientPacket);
		if (clientPacket && status != null)
			tag.put("Status", status.write());
	}

	@Override
	protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
		super.read(tag, registries, clientPacket);
		// Ponder scenes set the status through plain NBT, so a status is taken from either kind of read
		if (tag.contains("Status"))
			status = TurboStatus.read(tag.getCompound("Status"));
		else if (clientPacket)
			status = null;
	}

	@Override
	public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
		CELang.translate("goggles.turbo")
			.forGoggles(tooltip);
		TurboStatus current = status;
		if (current == null)
			GoggleLines.add(CELang.translate("goggles.turbo.no_engine")
				.style(ChatFormatting.RED), tooltip, 1);
		else {
			GoggleLines.add(CELang.translate("goggles.turbo.boost", String.format(Locale.ROOT, "%.2f", current.boost()))
				.style(current.boost() > 0.05f ? ChatFormatting.AQUA : ChatFormatting.GRAY), tooltip, 1);
			GoggleLines.add(CELang.translate("goggles.turbo.spool", Math.round(current.spool() * 100))
				.style(ChatFormatting.GRAY), tooltip, 1);
			if (current.wastegate() > 0.05f)
				GoggleLines.add(CELang.translate(dumping() ? "goggles.turbo.dumping" : "goggles.turbo.wastegate",
						Math.round(current.wastegate() * 100))
					.style(ChatFormatting.GOLD), tooltip, 1);
			if (!current.compressor())
				GoggleLines.add(CELang.translate("goggles.turbo.no_compressor")
					.style(ChatFormatting.RED), tooltip, 1);
			if (!current.turbine())
				GoggleLines.add(CELang.translate("goggles.turbo.no_turbine")
					.style(ChatFormatting.RED), tooltip, 1);
			if (isPlayerSneaking && current.turbine())
				GoggleLines.add(CELang.translate("goggles.turbo.back_pressure",
						String.format(Locale.ROOT, "%.2f", current.backPressure() - 1))
					.style(ChatFormatting.DARK_GRAY), tooltip, 1);
		}
		GoggleLines.add(CELang.translate("goggles.turbo.limit", String.format(Locale.ROOT, "%.1f", boostLimit()))
			.style(ChatFormatting.DARK_AQUA), tooltip, 1);
		if (isPlayerSneaking)
			GoggleLines.add(CELang.translate("goggles.turbo.hint")
				.style(ChatFormatting.DARK_GRAY), tooltip, 2);
		return true;
	}

	/** A value box on any face the shaft does not run out of. */
	private static class ValueBox extends ValueBoxTransform.Sided {

		@Override
		protected Vec3 getSouthLocation() {
			return VecHelper.voxelSpace(8, 8, 16);
		}

		@Override
		protected boolean isSideActive(BlockState state, Direction direction) {
			return direction.getAxis() != state.getValue(TurbochargerBlock.FACING)
				.getAxis();
		}
	}
}
