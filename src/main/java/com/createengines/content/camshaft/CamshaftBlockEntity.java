package com.createengines.content.camshaft;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.createengines.content.RenderRotation;
import com.createengines.content.crankshaft.CrankshaftBlockEntity;
import com.createengines.content.crankshaft.SideValueBox;
import com.createengines.engine.topology.EngineMember;
import com.createengines.engine.topology.EngineTopology;
import com.createengines.content.GoggleLines;
import com.createengines.registry.CELang;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class CamshaftBlockEntity extends KineticBlockEntity implements EngineMember {

	public static final double LOBE_ANGLE_STEP = 7.5;

	public ScrollValueBehaviour lobeAngle;

	private @Nullable BlockPos primaryPos;
	private float ratio;
	private boolean phaseLocked;
	private float rack;
	private int actuated;
	private float timingError;
	private final RenderRotation rotation = new RenderRotation();

	public CamshaftBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
		rack = 1;
		timingError = Float.NaN;
	}

	@Override
	public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
		super.addBehaviours(behaviours);
		if (!(getBlockState().getBlock() instanceof CamshaftBlock cam) || !cam.isLobe())
			return;
		lobeAngle = new ScrollValueBehaviour(CELang.translate("value.lobe_angle")
			.component(), this, new SideValueBox(13.5f));
		lobeAngle.between(0, (int) (360 / LOBE_ANGLE_STEP) - 1);
		lobeAngle.withFormatter(v -> CrankshaftBlockEntity.format(v * LOBE_ANGLE_STEP) + "°");
		lobeAngle.withCallback(v -> EngineTopology.markDirty(level, worldPosition));
		behaviours.add(lobeAngle);
	}

	/** Angle of this block's lobes on the camshaft, in cam degrees. Zero for plain camshaft. */
	public double lobeAngle() {
		return lobeAngle == null ? 0 : lobeAngle.getValue() * LOBE_ANGLE_STEP;
	}

	/**
	 * How far the camshaft has turned for drawing, in degrees (not wrapped, and before this block's lobe angle).
	 * Geared 1:2 to an engine, it is exactly half the crank's, so the valves stay in time with the pistons.
	 */
	public double rotation(float renderTime) {
		float speed = getTheoreticalSpeed();
		if (phaseLocked && primaryPos != null && level != null
			&& level.getBlockEntity(primaryPos) instanceof CrankshaftBlockEntity crank) {
			float crankSpeed = crank.getTheoreticalSpeed();
			if (crankSpeed != 0 && speed != 0)
				return rotation.follow(renderTime, crank.rotation(renderTime) * Math.signum(speed * crankSpeed) / 2);
		}
		return rotation.at(renderTime, speed);
	}

	public boolean isLobe() {
		return lobeAngle != null;
	}

	@Override
	public void initialize() {
		super.initialize();
		EngineTopology.markDirty(level, worldPosition);
	}

	public void updateStatus(BlockPos primary, float ratio, boolean phaseLocked, float rack, int actuated,
							 float timingError) {
		boolean changed = !primary.equals(primaryPos) || Math.abs(ratio - this.ratio) > 0.005f
			|| phaseLocked != this.phaseLocked || Math.abs(rack - this.rack) > 0.01f || actuated != this.actuated
			|| Float.isNaN(timingError) != Float.isNaN(this.timingError)
			|| Math.abs(timingError - this.timingError) > 0.5f;
		if (!changed)
			return;
		primaryPos = primary;
		this.ratio = ratio;
		this.phaseLocked = phaseLocked;
		this.rack = rack;
		this.actuated = actuated;
		this.timingError = timingError;
		sendData();
	}

	@Override
	public void detachFromEngine() {
		if (primaryPos == null)
			return;
		primaryPos = null;
		actuated = 0;
		timingError = Float.NaN;
		sendData();
	}

	@Override
	protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
		super.write(tag, registries, clientPacket);
		if (!clientPacket || primaryPos == null)
			return;
		tag.put("Primary", NbtUtils.writeBlockPos(primaryPos));
		tag.putFloat("Ratio", ratio);
		tag.putBoolean("Locked", phaseLocked);
		tag.putFloat("Rack", rack);
		tag.putInt("Actuated", actuated);
		tag.putFloat("Timing", timingError);
	}

	@Override
	protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
		super.read(tag, registries, clientPacket);
		if (!clientPacket)
			return;
		primaryPos = NbtUtils.readBlockPos(tag, "Primary")
			.orElse(null);
		ratio = tag.getFloat("Ratio");
		phaseLocked = tag.getBoolean("Locked");
		rack = tag.contains("Rack") ? tag.getFloat("Rack") : 1;
		actuated = tag.getInt("Actuated");
		timingError = tag.contains("Timing") ? tag.getFloat("Timing") : Float.NaN;
	}

	@Override
	public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
		if (primaryPos == null)
			return super.addToGoggleTooltip(tooltip, isPlayerSneaking);
		CELang.translate("goggles.cam")
			.forGoggles(tooltip);
		GoggleLines.add(CELang.translate("goggles.cam_ratio", "1:" + CrankshaftBlockEntity.format(ratio == 0 ? 0 : 1 / ratio))
			.style(ChatFormatting.GRAY), tooltip, 1);
		GoggleLines.add(CELang.translate(phaseLocked ? "goggles.cam_ratio.correct" : "goggles.cam_ratio.wrong")
			.style(phaseLocked ? ChatFormatting.GREEN : ChatFormatting.RED), tooltip, 1);
		if (isLobe()) {
			GoggleLines.add(CELang.translate("goggles.lobe_cylinders", actuated)
				.style(ChatFormatting.GRAY), tooltip, 1);
			if (!Float.isNaN(timingError))
				GoggleLines.add(CELang.translate("goggles.timing", timingText(timingError))
					.style(Math.abs(timingError) < 7.5f ? ChatFormatting.GREEN : ChatFormatting.GOLD), tooltip, 1);
		}
		int rackPercent = Math.round(rack * 100);
		GoggleLines.add(CELang.translate("goggles.fuel_rack", rackPercent)
			.style(rackPercent < 100 ? ChatFormatting.GOLD : ChatFormatting.GRAY), tooltip, 1);
		super.addToGoggleTooltip(tooltip, isPlayerSneaking);
		return true;
	}

	public static Component timingText(float error) {
		if (Math.abs(error) < 0.5f)
			return CELang.translate("goggles.timing.on_time")
				.component();
		String amount = CrankshaftBlockEntity.format(Math.abs(error));
		return CELang.translate(error > 0 ? "goggles.timing.late" : "goggles.timing.early", amount)
			.component();
	}
}
