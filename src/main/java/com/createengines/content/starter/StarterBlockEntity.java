package com.createengines.content.starter;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.createengines.config.CEClientConfig;
import com.createengines.content.effects.EngineEffects;
import com.createengines.engine.topology.EngineMember;
import com.createengines.content.GoggleLines;
import com.createengines.registry.CELang;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Consumes rotation from its input shaft and, while powered, cranks the engine it touches at that speed. The stress
 * it puts on its input network is however hard the engine is to turn over at that moment: compression and friction
 * of every cylinder, less whatever combustion is already helping.
 */
public class StarterBlockEntity extends KineticBlockEntity implements EngineMember {

	/** Ticks the engine has to go without drawing on the starter before it counts as having outrun it. */
	private static final int ENGAGED_GRACE = 10;

	private @Nullable BlockPos primaryPos;
	private float drag;
	private float smoothedDrag;
	private boolean cranking;
	private boolean turning;
	private int idleTicks;

	public StarterBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	/** Powered by redstone and being turned: it will push on the crank. */
	public boolean wantsToCrank() {
		return level != null && level.hasNeighborSignal(worldPosition) && getSpeed() != 0;
	}

	/** Which way it turns the crank. A starter turned the other way cranks the engine the other way. */
	public int crankDirection() {
		return getSpeed() > 0 ? 1 : -1;
	}

	/** Called by the engine each tick with how hard turning it over currently is (SU per RPM). */
	public void updateCranking(BlockPos primary, double crankingStress, boolean active) {
		if (active && crankingStress > 0)
			idleTicks = 0;
		else
			idleTicks++;
		boolean engaged = active && idleTicks < ENGAGED_GRACE;
		boolean changed = !primary.equals(primaryPos) || active != cranking || engaged != turning;
		primaryPos = primary;
		cranking = active;
		turning = engaged;
		// Smooth over a cycle so the input network isn't jerked about every compression stroke. The smoothed figure
		// moves every tick; the stress the network sees only follows it in steps, so it has to be tracked separately,
		// or a decay of a twentieth a tick never clears the step and the last stress is left on the shaft for good.
		smoothedDrag += (crankingStress - smoothedDrag) * (active ? 0.05f : 0.3f);
		if (!active && crankingStress == 0 && smoothedDrag < 0.05f)
			smoothedDrag = 0;
		if (Math.abs(smoothedDrag - drag) > Math.max(0.1f, drag * 0.05f) || smoothedDrag == 0 && drag != 0) {
			drag = smoothedDrag;
			if (hasNetwork())
				getOrCreateNetwork().updateStressFor(this, calculateStressApplied());
		}
		if (changed)
			sendData();
	}

	/** Whether the clutch is really turning the engine over: false once the engine outruns the starter. */
	public boolean isTurning() {
		return turning;
	}

	@Override
	public void tick() {
		super.tick();
		if (turning && level != null && level.isClientSide) {
			StarterWhirSound.ensurePlaying(this);
			if (CEClientConfig.particles() && level.random.nextInt(4) == 0)
				pinionSparks();
		}
	}

	/** The pinion grinding on the crank's ring gear as it turns the engine over: a spark or two where they meet. */
	private void pinionSparks() {
		Vec3 towards = Vec3.atLowerCornerOf(getBlockState().getValue(StarterBlock.FACING)
			.getNormal());
		Vec3 at = Vec3.atCenterOf(worldPosition)
			.add(towards.scale(0.5))
			.add((level.random.nextDouble() - 0.5) * 0.5, (level.random.nextDouble() - 0.5) * 0.5,
				(level.random.nextDouble() - 0.5) * 0.5);
		EngineEffects.sparks(level, at, towards.scale(-0.05), 1, 0.08);
	}

	@Override
	public float calculateStressApplied() {
		lastStressApplied = drag;
		return drag;
	}

	@Override
	public void detachFromEngine() {
		primaryPos = null;
		cranking = false;
		turning = false;
		drag = 0;
		smoothedDrag = 0;
		if (hasNetwork())
			getOrCreateNetwork().updateStressFor(this, 0);
		sendData();
	}

	@Override
	protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
		super.write(tag, registries, clientPacket);
		tag.putFloat("Drag", drag);
		tag.putBoolean("Cranking", cranking);
		tag.putBoolean("Turning", turning);
		if (primaryPos != null)
			tag.put("Primary", NbtUtils.writeBlockPos(primaryPos));
	}

	@Override
	protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
		super.read(tag, registries, clientPacket);
		drag = tag.getFloat("Drag");
		smoothedDrag = drag;
		cranking = tag.getBoolean("Cranking");
		turning = tag.getBoolean("Turning");
		primaryPos = NbtUtils.readBlockPos(tag, "Primary")
			.orElse(null);
	}

	@Override
	public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
		CELang.translate("goggles.starter")
			.forGoggles(tooltip);
		String key;
		ChatFormatting colour;
		if (primaryPos == null) {
			key = "goggles.starter.no_engine";
			colour = ChatFormatting.RED;
		} else if (cranking) {
			key = "goggles.starter.cranking";
			colour = ChatFormatting.YELLOW;
		} else if (wantsToCrank()) {
			key = "goggles.starter.overrun";
			colour = ChatFormatting.GREEN;
		} else {
			key = "goggles.starter.idle";
			colour = ChatFormatting.GRAY;
		}
		GoggleLines.add(CELang.translate(key)
			.style(colour), tooltip, 1);
		super.addToGoggleTooltip(tooltip, isPlayerSneaking);
		return true;
	}
}
