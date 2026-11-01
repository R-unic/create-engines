package com.createengines.content.turbo;

import java.util.List;
import java.util.Locale;

import org.jetbrains.annotations.Nullable;

import com.createengines.config.CEClientConfig;
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
import net.minecraft.core.particles.ParticleTypes;
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
	private static final BehaviourType<ScrollValueBehaviour> ANTI_LAG_TYPE = new BehaviourType<>("turbo_anti_lag");
	private static final String ANTI_LAG_KEY = "AntiLag";
	/** Wheel degrees turned per tick at design speed. */
	private static final float WHEEL_DEGREES_PER_TICK = 60;

	public ScrollValueBehaviour limit;
	/** Anti-lag, 0 (off) to {@link Turbocharger#MAX_ANTI_LAG}. */
	public ScrollValueBehaviour antiLag;

	private @Nullable TurboStatus status;
	private float wheelAngle;
	private float wheelSpeed;

	public TurbochargerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	@Override
	public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
		limit = new ScrollValueBehaviour(CELang.translate("value.boost_limit")
			.component(), this, new ValueBox(12)) {
			@Override
			public BehaviourType<?> getType() {
				return LIMIT_TYPE;
			}
		};
		limit.between(MIN_LIMIT, MAX_LIMIT);
		limit.value = DEFAULT_LIMIT;
		limit.withFormatter(v -> String.format(Locale.ROOT, "%.1f bar", v / 10f));
		behaviours.add(limit);

		antiLag = new ScrollValueBehaviour(CELang.translate("value.anti_lag")
			.component(), this, new ValueBox(4)) {
			@Override
			public BehaviourType<?> getType() {
				return ANTI_LAG_TYPE;
			}

			/**
			 * Create saves every scroll value under the same key, and the boost limit is one already: this one keeps
			 * its own, or the two would overwrite each other whenever the block is saved or sent to a client.
			 */
			@Override
			public void write(CompoundTag nbt, HolderLookup.Provider registries, boolean clientPacket) {
				CompoundTag scratch = new CompoundTag();
				super.write(scratch, registries, clientPacket);
				nbt.putInt(ANTI_LAG_KEY, scratch.getInt("ScrollValue"));
			}

			@Override
			public void read(CompoundTag nbt, HolderLookup.Provider registries, boolean clientPacket) {
				CompoundTag scratch = new CompoundTag();
				scratch.putInt("ScrollValue", nbt.getInt(ANTI_LAG_KEY));
				super.read(scratch, registries, clientPacket);
			}
		};
		antiLag.between(0, Turbocharger.MAX_ANTI_LAG);
		antiLag.value = 0;
		antiLag.withFormatter(v -> v == 0 ? "Off" : "Level " + v);
		behaviours.add(antiLag);
	}

	/** The anti-lag setting, 0 (off) to {@link Turbocharger#MAX_ANTI_LAG}. */
	public int antiLagLevel() {
		return Math.clamp(antiLag.getValue(), 0, Turbocharger.MAX_ANTI_LAG);
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
			if (status != null && status.antiLag() && CEClientConfig.particles() && level.random.nextInt(2) == 0)
				popOut();
		}
	}

	/** Flame and smoke at the turbine end, where anti-lag burns fuel in the exhaust. */
	private void popOut() {
		Direction exhaust = getBlockState().getValue(TurbochargerBlock.FACING)
			.getOpposite();
		Vec3 at = Vec3.atCenterOf(worldPosition)
			.add(Vec3.atLowerCornerOf(exhaust.getNormal())
				.scale(0.6));
		var random = level.random;
		level.addParticle(random.nextBoolean() ? ParticleTypes.FLAME : ParticleTypes.SMOKE, at.x, at.y, at.z,
			(random.nextDouble() - 0.5) * 0.04, 0.03, (random.nextDouble() - 0.5) * 0.04);
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
			if (current.compressor() && Math.abs(current.charge() - current.boost()) >= 0.02f)
				GoggleLines.add(CELang.translate("goggles.turbo.charge", String.format(Locale.ROOT, "%.2f", current.charge()))
					.style(ChatFormatting.DARK_AQUA), tooltip, 1);
			GoggleLines.add(CELang.translate("goggles.turbo.spool", Math.round(current.spool() * 100))
				.style(ChatFormatting.GRAY), tooltip, 1);
			if (current.vanes() > 0.05f || getBlockState().getBlock() instanceof TurbochargerBlock block && block.variableGeometry())
				GoggleLines.add(CELang.translate("goggles.turbo.vanes", Math.round(current.vanes() * 100))
					.style(ChatFormatting.DARK_AQUA), tooltip, 1);
			if (current.antiLag())
				GoggleLines.add(CELang.translate("goggles.turbo.anti_lag")
					.style(ChatFormatting.RED), tooltip, 1);
			else if (antiLagLevel() > 0)
				GoggleLines.add(CELang.translate("goggles.turbo.anti_lag.armed", antiLagLevel())
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

	/** A value box on any face the shaft does not run out of, {@code height} sixteenths up it. */
	private static class ValueBox extends ValueBoxTransform.Sided {

		private final double height;

		ValueBox(double height) {
			this.height = height;
		}

		@Override
		protected Vec3 getSouthLocation() {
			return VecHelper.voxelSpace(8, height, 16);
		}

		@Override
		protected boolean isSideActive(BlockState state, Direction direction) {
			return direction.getAxis() != state.getValue(TurbochargerBlock.FACING)
				.getAxis();
		}
	}
}
