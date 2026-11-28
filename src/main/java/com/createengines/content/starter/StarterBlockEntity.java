package com.createengines.content.starter;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.createengines.config.CEClientConfig;
import com.createengines.engine.Signals;
import com.createengines.engine.topology.EngineMember;
import com.createengines.content.GoggleLines;
import com.createengines.registry.CELang;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.crank.HandCrankBlockEntity;

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

	/** How fast the batteries turn a starter with nothing on its input. Slower than a hand crank's best, so it is a way to start and not a source. */
	public static final double ELECTRIC_RPM = 48;

	private @Nullable BlockPos primaryPos;
	private float drag;
	private float smoothedDrag;
	private boolean cranking;
	private boolean turning;
	/** Wanted to crank but the batteries behind it are nearly flat: it draws less from them, and says so. */
	private boolean batteryWeak;
	/** Cranking a spark engine, so the charge for it is coming out of the batteries against the engine. */
	private boolean drawingCharge;
	/** A spark engine with charge in its batteries: the starter can turn with nothing on its input. */
	private boolean electricReady;
	private int idleTicks;

	public StarterBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	/**
	 * Being turned, and either powered by redstone or turned by hand: it will push on the crank. A hand crank on its input needs no
	 * redstone, since someone standing there turning it has made their wish plain, and that is what lets a flat or stuck engine
	 * be turned over with nothing else running.
	 */
	public boolean wantsToCrank() {
		if (level == null)
			return false;
		if (getSpeed() != 0)
			return Signals.hasNeighbour(level, worldPosition) || isHandCranked();
		// No rotation on its input: a spark engine's batteries can turn it over, but only on a redstone signal
		return electricReady && Signals.hasNeighbour(level, worldPosition);
	}

	/** The speed it turns the crank at: that of its input shaft, or {@link #ELECTRIC_RPM} when the batteries are turning it with no input. */
	public double crankRpm() {
		if (getSpeed() != 0)
			return Math.abs(getSpeed());
		return electricReady ? ELECTRIC_RPM : 0;
	}

	/** Whether the batteries, not a shaft, are what is turning it: nothing on its input and a charged bank on a spark engine. */
	public boolean isElectric() {
		return getSpeed() == 0 && electricReady;
	}

	/** Said by the engine each tick: whether this is a spark engine with a charged battery bank, which can crank it with no input rotation. */
	public void setElectricReady(boolean ready) {
		if (ready == electricReady)
			return;
		electricReady = ready;
		sendData();
	}

	/** Whether the shaft driving this starter gets its rotation from a hand crank. */
	public boolean isHandCranked() {
		return level != null && source != null && level.isLoaded(source) && level.getBlockEntity(source) instanceof HandCrankBlockEntity;
	}

	/** Its share of the effort of turning the engine over, relative to a plain starter's (see {@link StarterBlock#strength()}). */
	public double strength() {
		return getBlockState().getBlock() instanceof StarterBlock block ? block.strength() : 1;
	}

	/** Whether it makes a cold engine catch while it cranks (see {@link StarterBlock#warmsColdEngines()}). */
	public boolean warmsColdEngines() {
		return getBlockState().getBlock() instanceof StarterBlock block && block.warmsColdEngines();
	}

	/** Which way it turns the crank. A starter turned the other way cranks the engine the other way. */
	public int crankDirection() {
		return getSpeed() >= 0 ? 1 : -1;
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

	/** Whether it is taking charge from the batteries touching the engine as it cranks. */
	public boolean isDrawingCharge() {
		return drawingCharge;
	}

	/** Said by the engine each tick: whether this starter is spending battery charge turning the engine over. */
	public void setDrawingCharge(boolean drawing) {
		if (drawing == drawingCharge)
			return;
		drawingCharge = drawing;
		sendData();
	}

	/** Whether the batteries behind it are nearly flat. */
	public boolean isBatteryWeak() {
		return batteryWeak;
	}

	/** Said by the engine each tick: whether the batteries behind this starter are nearly flat. */
	public void setBatteryWeak(boolean weak) {
		if (weak == batteryWeak)
			return;
		batteryWeak = weak;
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
		com.createengines.content.effects.ExhaustFire.sparks(level, at, towards.scale(-0.05), 1, 0.08);
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
		drawingCharge = false;
		electricReady = false;
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
		tag.putBoolean("BatteryWeak", batteryWeak);
		tag.putBoolean("DrawingCharge", drawingCharge);
		tag.putBoolean("ElectricReady", electricReady);
		if (primaryPos != null)
			tag.put("Primary", NbtUtils.writeBlockPos(primaryPos));
	}

	@Override
	protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
		super.read(tag, registries, clientPacket);
		drag = com.createengines.content.SavedNumbers.read(tag, "Drag", 0);
		smoothedDrag = drag;
		cranking = tag.getBoolean("Cranking");
		turning = tag.getBoolean("Turning");
		batteryWeak = tag.getBoolean("BatteryWeak");
		drawingCharge = tag.getBoolean("DrawingCharge");
		electricReady = tag.getBoolean("ElectricReady");
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
		} else if (batteryWeak && wantsToCrank()) {
			key = "goggles.starter.weak";
			colour = ChatFormatting.GOLD;
		} else if (cranking && isHandCranked() && !Signals.hasNeighbour(level, worldPosition)) {
			key = "goggles.starter.by_hand";
			colour = ChatFormatting.YELLOW;
		} else if (cranking && isElectric()) {
				key = "goggles.starter.electric";
				colour = ChatFormatting.AQUA;
			} else if (cranking && warmsColdEngines()) {
			key = "goggles.starter.warming";
			colour = ChatFormatting.GOLD;
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
		if (drawingCharge)
			GoggleLines.add(CELang.translate("goggles.starter.drawing")
				.style(ChatFormatting.AQUA), tooltip, 1);
		super.addToGoggleTooltip(tooltip, isPlayerSneaking);
		return true;
	}
}
