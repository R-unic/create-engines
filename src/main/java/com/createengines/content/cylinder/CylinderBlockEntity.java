package com.createengines.content.cylinder;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.createengines.content.airflow.PortOpeningBlock;
import com.createengines.content.camshaft.CamshaftBlockEntity;
import com.createengines.content.crankshaft.CrankshaftBlockEntity;
import com.createengines.engine.CylinderStatus;
import com.createengines.engine.sim.CamProfile;
import com.createengines.engine.sim.CycleResult;
import com.createengines.engine.sim.CylinderModel;
import com.createengines.engine.topology.CylinderFault;
import com.createengines.engine.topology.EngineMember;
import com.createengines.registry.CELang;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;

import net.createmod.catnip.animation.AnimationTickHolder;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class CylinderBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation, EngineMember {

	private @Nullable CylinderStatus status;
	private double lastCamCycle = Double.NaN;

	public CylinderBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	@Override
	public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
	}

	public @Nullable CylinderStatus status() {
		return status;
	}

	public void updateStatus(CylinderStatus next) {
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

	/** The crank throw this cylinder's connecting rod is on, if any. */
	public @Nullable CrankshaftBlockEntity crankThrow() {
		Direction bore = getBlockState().getValue(CylinderBlock.FACING);
		if (level != null && level.getBlockEntity(worldPosition.relative(bore.getOpposite()))
			instanceof CrankshaftBlockEntity crank)
			return crank;
		return null;
	}

	@Override
	public void tick() {
		super.tick();
		if (level != null && level.isClientSide)
			tickExhaust();
	}

	/** Smoke from the exhaust when the exhaust valve opens after a firing: follows the visible cam, not a packet. */
	private void tickExhaust() {
		CylinderStatus current = status;
		if (current == null || current.lobe() == null || current.exhaustOutlets()
			.isEmpty()
			|| !(level.getBlockEntity(current.lobe()) instanceof CamshaftBlockEntity lobe)) {
			lastCamCycle = Double.NaN;
			return;
		}
		float camAngle = (float) Math.abs(lobe.rotation(AnimationTickHolder.getRenderTime(level)) % 360);
		double cycle = CylinderModel.camCycleAngle(camAngle, lobe.lobeAngle());
		double previous = lastCamCycle;
		lastCamCycle = cycle;
		if (Double.isNaN(previous) || lobe.getTheoreticalSpeed() == 0)
			return;
		double exhaustOpen = CamProfile.BASIC.exhaustOpen();
		double travelled = (cycle - previous + 720) % 720;
		double ahead = (exhaustOpen - previous + 720) % 720;
		if (travelled > 360 || ahead <= 0 || ahead > travelled)
			return;

		ParticleOptions particle;
		int count;
		if (current.result() == CycleResult.FIRED) {
			particle = current.rich() ? ParticleTypes.LARGE_SMOKE : ParticleTypes.SMOKE;
			count = current.rich() ? 3 : 2;
		} else if (current.result() == CycleResult.NO_IGNITION) {
			particle = ParticleTypes.POOF;
			count = 1;
		} else
			return;

		for (BlockPos outlet : current.exhaustOutlets()) {
			BlockState state = level.getBlockState(outlet);
			if (!(state.getBlock() instanceof PortOpeningBlock))
				continue;
			Direction mouth = state.getValue(PortOpeningBlock.FACING);
			Vec3 normal = Vec3.atLowerCornerOf(mouth.getNormal());
			Vec3 at = Vec3.atCenterOf(outlet)
				.add(normal.scale(0.55));
			for (int i = 0; i < count; i++) {
				Vec3 velocity = normal.scale(0.06)
					.add((level.random.nextFloat() - 0.5) * 0.02, 0.02, (level.random.nextFloat() - 0.5) * 0.02);
				level.addParticle(particle, at.x, at.y, at.z, velocity.x, velocity.y, velocity.z);
			}
		}
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
		if (clientPacket)
			status = tag.contains("Status") ? CylinderStatus.read(tag.getCompound("Status")) : null;
	}

	@Override
	public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
		CELang.translate("goggles.cylinder")
			.forGoggles(tooltip);
		BlockState state = getBlockState();
		CylinderStatus current = status;
		int faults = current == null ? 0 : current.faults();
		if (!state.getValue(CylinderBlock.HAS_PISTON))
			faults |= CylinderFault.NO_PISTON.bit();
		if (!state.getValue(CylinderBlock.HAS_ROD))
			faults |= CylinderFault.NO_ROD.bit();
		if (current == null && crankThrow() == null)
			faults |= CylinderFault.NOT_ON_THROW.bit();

		if (current != null && !CylinderFault.disables(faults)) {
			CycleResult result = current.result();
			ChatFormatting colour = result.fired() ? ChatFormatting.GREEN
				: result == CycleResult.NONE ? ChatFormatting.GRAY : ChatFormatting.RED;
			CELang.translate("goggles.cycle." + result.name()
					.toLowerCase(java.util.Locale.ROOT))
				.style(colour)
				.forGoggles(tooltip, 1);
			if (current.rich())
				CELang.translate("goggles.cycle.rich")
					.style(ChatFormatting.GOLD)
					.forGoggles(tooltip, 1);
			if (!Float.isNaN(current.chargeTemp()) && !Float.isNaN(current.ignitionTemp()))
				CELang.translate("goggles.compression", temperature(current.chargeTemp()),
						temperature(current.ignitionTemp()))
					.style(current.chargeTemp() >= current.ignitionTemp() ? ChatFormatting.GRAY : ChatFormatting.GOLD)
					.forGoggles(tooltip, 1);
			if (!Float.isNaN(current.timingError()))
				CELang.translate("goggles.timing", CamshaftBlockEntity.timingText(current.timingError()))
					.style(Math.abs(current.timingError()) < 7.5f ? ChatFormatting.GRAY : ChatFormatting.GOLD)
					.forGoggles(tooltip, 1);
			else if (current.lobe() != null)
				CELang.translate("goggles.timing", CELang.translate("goggles.timing.unlocked")
						.component())
					.style(ChatFormatting.RED)
					.forGoggles(tooltip, 1);
			CELang.translate("goggles.phase", Math.round(((current.tdcAngle() % 360) + 360) % 360))
				.style(ChatFormatting.DARK_GRAY)
				.forGoggles(tooltip, 1);
		}

		for (CylinderFault fault : CylinderFault.values())
			if (fault.in(faults))
				CELang.translate(fault.langKey())
					.style(fault.disablesCylinder ? ChatFormatting.RED : ChatFormatting.GOLD)
					.forGoggles(tooltip, 1);
		return true;
	}

	/** Shows a normalized temperature in kelvin, taking ambient as 300 K. */
	private static String temperature(float normalized) {
		return Math.round(normalized * 300) + " K";
	}
}
