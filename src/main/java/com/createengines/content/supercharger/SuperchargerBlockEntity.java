package com.createengines.content.supercharger;

import java.util.List;
import java.util.Locale;

import org.jetbrains.annotations.Nullable;

import com.createengines.content.GoggleLines;
import com.createengines.engine.sim.Supercharger;
import com.createengines.engine.topology.EngineMember;
import com.createengines.registry.CELang;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
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
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

/**
 * The bypass setting of a {@link SuperchargerBlock}, and what the engine tells it about the air it is pushing. The boost
 * itself is made in the engine's simulation from this block's shaft speed; this holds the last figures it sent, for
 * goggles, and puts the work of turning the blower on its shaft as stress.
 */
public class SuperchargerBlockEntity extends KineticBlockEntity implements EngineMember {

	/** The boost limit is set in steps of a tenth of a bar. */
	public static final int MIN_LIMIT = 1;
	public static final int MAX_LIMIT = (int) Math.round(Supercharger.MAX_LIMIT * 10);
	public static final int DEFAULT_LIMIT = (int) Math.round(Supercharger.DEFAULT_LIMIT * 10);
	/** Stress per RPM the blower puts on its shaft for each cylinder it feeds, at its full design boost. */
	public static final float STRESS_PER_CYLINDER = 4;
	private static final BehaviourType<ScrollValueBehaviour> LIMIT_TYPE = new BehaviourType<>("supercharger_limit");

	public ScrollValueBehaviour limit;

	private @Nullable BlockPos primaryPos;
	private int served;
	private float boost;
	private float bypass;
	private float drag;

	public SuperchargerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	@Override
	public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
		super.addBehaviours(behaviours);
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

	/** The boost the bypass valve is set to hold, atmospheres. */
	public double boostLimit() {
		return Math.max(MIN_LIMIT, limit.getValue()) / 10d;
	}

	/** A redstone signal opens the bypass fully, dumping the boost. */
	public boolean dumping() {
		return level != null && level.hasNeighborSignal(worldPosition);
	}

	/** Whether the engine has told this blower that it feeds some of its cylinders. */
	public boolean onEngine() {
		return primaryPos != null && served > 0;
	}

	/** The boost the engine last reported, atmospheres. */
	public float boost() {
		return boost;
	}

	/** The shaft's speed, RPM, or 0 while the network is overstressed and has stopped. */
	public double shaftRpm() {
		return isOverStressed() ? 0 : Math.abs(getSpeed());
	}

	/**
	 * Called by the engine with what the blower is doing: how many cylinders it feeds, the boost it makes and the share
	 * of it the bypass is bleeding off. The stress on the shaft follows the work: the more air and pressure, the harder
	 * it is to turn.
	 */
	public void updateBlower(BlockPos primary, int cylinders, float boost, float bypass) {
		float next = cylinders * STRESS_PER_CYLINDER * (float) Math.min(1.7, boost / Supercharger.FULL_BOOST);
		boolean changed = !primary.equals(primaryPos) || cylinders != served || Math.abs(boost - this.boost) >= 0.01f
			|| Math.abs(bypass - this.bypass) >= 0.05f;
		primaryPos = primary;
		served = cylinders;
		this.boost = boost;
		this.bypass = bypass;
		if (Math.abs(next - drag) > Math.max(0.1f, drag * 0.05f) || next == 0 && drag != 0) {
			drag = next;
			if (hasNetwork())
				getOrCreateNetwork().updateStressFor(this, calculateStressApplied());
		}
		if (changed)
			sendData();
	}

	@Override
	public float calculateStressApplied() {
		lastStressApplied = drag;
		return drag;
	}

	@Override
	public void detachFromEngine() {
		if (primaryPos == null && drag == 0)
			return;
		primaryPos = null;
		served = 0;
		boost = 0;
		bypass = 0;
		drag = 0;
		if (hasNetwork())
			getOrCreateNetwork().updateStressFor(this, 0);
		sendData();
	}

	@Override
	protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
		super.write(tag, registries, clientPacket);
		tag.putInt("Served", served);
		tag.putFloat("Boost", boost);
		tag.putFloat("Bypass", bypass);
		tag.putFloat("Drag", drag);
		if (primaryPos != null)
			tag.put("Primary", NbtUtils.writeBlockPos(primaryPos));
	}

	@Override
	protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
		super.read(tag, registries, clientPacket);
		served = tag.getInt("Served");
		boost = tag.getFloat("Boost");
		bypass = tag.getFloat("Bypass");
		drag = tag.getFloat("Drag");
		primaryPos = NbtUtils.readBlockPos(tag, "Primary")
			.orElse(null);
	}

	@Override
	public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
		CELang.translate("goggles.supercharger")
			.forGoggles(tooltip);
		if (primaryPos == null)
			GoggleLines.add(CELang.translate("goggles.supercharger.no_engine")
				.style(ChatFormatting.RED), tooltip, 1);
		else {
			GoggleLines.add(CELang.translate("goggles.supercharger.boost", String.format(Locale.ROOT, "%.2f", boost))
				.style(boost > 0.05f ? ChatFormatting.AQUA : ChatFormatting.GRAY), tooltip, 1);
			GoggleLines.add(CELang.translate("goggles.supercharger.speed", Math.round(shaftRpm()))
				.style(ChatFormatting.GRAY), tooltip, 1);
			if (bypass > 0.05f)
				GoggleLines.add(CELang.translate(dumping() ? "goggles.supercharger.dumping" : "goggles.supercharger.bypass",
						Math.round(bypass * 100))
					.style(ChatFormatting.GOLD), tooltip, 1);
			if (isPlayerSneaking)
				GoggleLines.add(CELang.translate("goggles.supercharger.fed", served)
					.style(ChatFormatting.DARK_GRAY), tooltip, 1);
		}
		GoggleLines.add(CELang.translate("goggles.supercharger.limit", String.format(Locale.ROOT, "%.1f", boostLimit()))
			.style(ChatFormatting.DARK_AQUA), tooltip, 1);
		if (isPlayerSneaking)
			GoggleLines.add(CELang.translate("goggles.supercharger.hint")
				.style(ChatFormatting.DARK_GRAY), tooltip, 2);
		super.addToGoggleTooltip(tooltip, isPlayerSneaking);
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
			return direction.getAxis() != state.getValue(BlockStateProperties.AXIS);
		}
	}
}
