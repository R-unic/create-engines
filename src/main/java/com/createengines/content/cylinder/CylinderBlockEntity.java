package com.createengines.content.cylinder;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import org.joml.Vector3f;

import com.createengines.config.CEClientConfig;
import com.createengines.content.EffectRate;
import com.createengines.content.EngineNoteSound;
import com.createengines.content.EnginePoses;
import com.createengines.content.airflow.PortOpeningBlock;
import com.createengines.content.camshaft.CamshaftBlockEntity;
import com.createengines.content.crankshaft.CrankshaftBlockEntity;
import com.createengines.content.head.GalleryMember;
import com.createengines.engine.CylinderStatus;
import com.createengines.engine.sim.CamProfile;
import com.createengines.engine.sim.CycleResult;
import com.createengines.engine.sim.CylinderModel;
import com.createengines.engine.sim.Mixture;
import com.createengines.engine.topology.CylinderFault;
import com.createengines.engine.topology.EngineMember;
import com.createengines.content.GoggleLines;
import com.createengines.registry.CELang;
import com.createengines.registry.CESounds;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;

import net.createmod.catnip.animation.AnimationTickHolder;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class CylinderBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation, EngineMember {

	/** How much of its volume a cylinder's bang keeps while the engine's exhaust note is playing. */
	private static final float NOTE_PLAYING_BANG_SHARE = 0.15f;
	/** Where the cam says the charge fires: the top of the compression stroke. */
	private static final double FIRING_CYCLE_ANGLE = 360;
	/** The piston's crown above its wrist pin, in blocks (the 6/16 top of the piston model). */
	private static final float PISTON_CROWN = 6 / 16f;
	/** The head's face, in blocks from the cylinder's centre along the bore. */
	private static final double ROOF = 0.5;
	/** How far from the bore's axis gas is drawn, which keeps it between the frame posts. */
	private static final double LATERAL = 0.26;

	/** Mixtures within these equivalence ratios read as balanced. */
	private static final float LEAN_MIXTURE = (float) Mixture.LEAN_BAND;
	private static final float RICH_MIXTURE = (float) Mixture.RICH_BAND;

	private @Nullable CylinderStatus status;
	private double lastCamCycle = Double.NaN;
	private final EffectRate bangs = new EffectRate(EffectRate.BANG_INTERVAL);
	private final EffectRate puffs = new EffectRate(EffectRate.SMOKE_INTERVAL);

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
			tickClient();
	}

	/**
	 * Everything you see and hear from a running cylinder, driven by the visible cam rather than by packets so it stays
	 * in step with the moving parts: the bang at firing TDC, the gas in the bore, and smoke when the exhaust opens.
	 */
	private void tickClient() {
		CylinderStatus current = status;
		if (current == null || current.lobe() == null
			|| !(level.getBlockEntity(current.lobe()) instanceof CamshaftBlockEntity lobe)) {
			lastCamCycle = Double.NaN;
			return;
		}
		float renderTime = AnimationTickHolder.getRenderTime(level);
		float camAngle = (float) Math.abs(lobe.rotation(renderTime) % 360);
		double cycle = CylinderModel.camCycleAngle(camAngle, lobe.lobeAngle() + current.lobeShift());
		double previous = lastCamCycle;
		lastCamCycle = cycle;
		if (Double.isNaN(previous) || lobe.getTheoreticalSpeed() == 0)
			return;
		double travelled = (cycle - previous + 720) % 720;
		// More than half a cycle in one frame, which a fast engine does: every mark went by, so none is told apart from
		// the others, and the effects' own rate limits are what thin them out
		if (travelled > 360)
			travelled = 720;

		boolean fired = current.result() == CycleResult.FIRED;
		if (fired && crosses(previous, travelled, FIRING_CYCLE_ANGLE))
			ignite();
		tickChamber(current, cycle, renderTime);
		if (!current.exhaustOutlets()
			.isEmpty() && crosses(previous, travelled, CamProfile.BASIC.exhaustOpen()))
			tickExhaust(current);
	}

	/** The bang, and a flash of flame and sparks at the roof of the chamber. */
	private void ignite() {
		if (!bangs.allow(level))
			return;
		if (level.getNearestPlayer(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5,
			48, false) == null)
			return;
		RandomSource random = level.random;
		float volume = CEClientConfig.bangVolume();
		// With the engine's own exhaust note playing, a bang is only the accent on the flash
		CrankshaftBlockEntity throwBlock = crankThrow();
		if (throwBlock != null && throwBlock.primaryPos() != null && EngineNoteSound.isPlayingFor(throwBlock.primaryPos()))
			volume *= NOTE_PLAYING_BANG_SHARE;
		if (volume > 0)
			level.playLocalSound(worldPosition, CESounds.CYLINDER_FIRE.get(), SoundSource.BLOCKS, volume,
				0.92f + random.nextFloat() * 0.16f, false);
		if (!CEClientConfig.particles())
			return;
		Direction bore = getBlockState().getValue(CylinderBlock.FACING);
		for (int i = 0; i < 6; i++) {
			Vec3 at = chamberPoint(bore, ROOF - random.nextDouble() * 0.1);
			Vec3 push = Vec3.atLowerCornerOf(bore.getNormal())
				.scale(-0.02);
			level.addParticle(i < 4 ? ParticleTypes.SMALL_FLAME : ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z,
				push.x + (random.nextDouble() - 0.5) * 0.04, push.y + (random.nextDouble() - 0.5) * 0.04,
				push.z + (random.nextDouble() - 0.5) * 0.04);
		}
	}

	/**
	 * Gas moving about between the piston crown and the head, one look per stroke: cool air drawn in, charge heating
	 * as it is squeezed, flame and embers pushing the piston down, then spent gas swept out. What is burnt follows the
	 * last cycle's result, and a blocked valve shows nothing moving through it.
	 */
	private void tickChamber(CylinderStatus current, double cycle, float renderTime) {
		if (!CEClientConfig.particles())
			return;
		if (level.getNearestPlayer(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5,
			24, false) == null)
			return;
		EnginePoses.CylinderPose pose = EnginePoses.cylinder(this, renderTime);
		if (!pose.showPiston())
			return;
		Direction bore = getBlockState().getValue(CylinderBlock.FACING);
		int axis = bore.getAxis()
			.ordinal();
		int sign = bore.getAxisDirection()
			.getStep();
		float wrist = pose.piston()
			.transformPosition(0.5f, 0, 0.5f, new Vector3f())
			.get(axis);
		double crown = (wrist - 0.5) * sign + PISTON_CROWN;
		if (ROOF - crown < 0.08)
			return;

		RandomSource random = level.random;
		Vec3 along = Vec3.atLowerCornerOf(bore.getNormal());
		CycleResult result = current.result();
		boolean fired = result == CycleResult.FIRED;
		int faults = current.faults();

		if (cycle < 180) {
			if (CylinderFault.NO_INTAKE.in(faults) || CylinderFault.INTAKE_BLOCKED.in(faults))
				return;
			for (int i = 0; i < 2; i++)
				dust(chamberPoint(bore, ROOF - random.nextDouble() * 0.12), along.scale(-0.045), 0.78f, 0.9f, 1f, 0.45f);
		} else if (cycle < 360) {
			float squeeze = (float) (cycle - 180) / 180;
			int count = squeeze > 0.6f ? 2 : 1;
			for (int i = 0; i < count; i++)
				dust(chamberPoint(bore, crown + random.nextDouble() * (ROOF - crown)), jitter(random, 0.012),
					0.78f + 0.22f * squeeze, 0.9f - 0.4f * squeeze, 1f - 0.9f * squeeze, 0.4f);
		} else if (cycle < 540) {
			if (fired && cycle < 450) {
				for (int i = 0; i < 2; i++) {
					Vec3 at = chamberPoint(bore, crown + random.nextDouble() * (ROOF - crown));
					Vec3 drift = jitter(random, 0.012);
					level.addParticle(ParticleTypes.SMALL_FLAME, at.x, at.y, at.z, drift.x, drift.y, drift.z);
				}
			} else if (fired)
				dust(chamberPoint(bore, crown + random.nextDouble() * (ROOF - crown)), jitter(random, 0.01), 0.85f,
					0.35f, 0.1f, 0.4f);
			else if (unlit(result))
				dust(chamberPoint(bore, crown + random.nextDouble() * (ROOF - crown)), jitter(random, 0.01), 0.6f, 0.6f,
					0.6f, 0.4f);
		} else {
			if (CylinderFault.NO_EXHAUST.in(faults) || CylinderFault.EXHAUST_BLOCKED.in(faults))
				return;
			float shade = current.rich() ? 0.08f : fired ? 0.3f : 0.6f;
			if (fired || unlit(result))
				for (int i = 0; i < 2; i++)
					dust(chamberPoint(bore, crown + random.nextDouble() * 0.15), along.scale(0.04), shade, shade, shade,
						0.5f);
		}
	}

	/** Fuel went in and nothing lit it: it leaves the cylinder as it came. */
	private static boolean unlit(CycleResult result) {
		return result == CycleResult.NO_IGNITION || result == CycleResult.NO_SPARK
			|| result == CycleResult.TOO_LEAN || result == CycleResult.TOO_RICH || result == CycleResult.FOULED;
	}

	/** A random spot inside the bore {@code distance} from the block's centre along it, towards the head if positive. */
	private Vec3 chamberPoint(Direction bore, double distance) {
		RandomSource random = level.random;
		int axis = bore.getAxis()
			.ordinal();
		double[] offset = new double[3];
		for (int i = 0; i < 3; i++)
			offset[i] = 0.5 + (random.nextDouble() - 0.5) * 2 * LATERAL;
		offset[axis] = 0.5 + distance * bore.getAxisDirection()
			.getStep();
		return new Vec3(worldPosition.getX() + offset[0], worldPosition.getY() + offset[1],
			worldPosition.getZ() + offset[2]);
	}

	private static Vec3 jitter(RandomSource random, double size) {
		return new Vec3((random.nextDouble() - 0.5) * size, (random.nextDouble() - 0.5) * size,
			(random.nextDouble() - 0.5) * size);
	}

	private void dust(Vec3 at, Vec3 velocity, float red, float green, float blue, float size) {
		level.addParticle(new DustParticleOptions(new Vector3f(red, green, blue), size), at.x, at.y, at.z, velocity.x,
			velocity.y, velocity.z);
	}

	/** Smoke from the exhaust when the exhaust valve opens after a firing. */
	private void tickExhaust(CylinderStatus current) {
		if (!CEClientConfig.particles() || !puffs.allow(level))
			return;
		ParticleOptions particle;
		int count;
		if (current.result() == CycleResult.FIRED) {
			particle = current.rich() ? ParticleTypes.LARGE_SMOKE : ParticleTypes.SMOKE;
			count = current.rich() ? 3 : 2;
		} else if (unlit(current.result())) {
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

	/** Whether a cam-cycle angle advancing from {@code from} by {@code travelled} passes {@code mark}. */
	private static boolean crosses(double from, double travelled, double mark) {
		double ahead = (mark - from + 720) % 720;
		return ahead > 0 && ahead <= travelled;
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
				: result == CycleResult.NONE ? ChatFormatting.GRAY
					: ChatFormatting.RED;
			GoggleLines.add(CELang.translate("goggles.cycle." + result.name()
					.toLowerCase(java.util.Locale.ROOT))
				.style(colour), tooltip, 1);
			if (current.rich())
				GoggleLines.add(CELang.translate("goggles.cycle.rich")
					.style(ChatFormatting.GOLD), tooltip, 1);
			if (current.lean())
				GoggleLines.add(CELang.translate("goggles.cycle.lean")
					.style(ChatFormatting.GOLD), tooltip, 1);
			if (!Float.isNaN(current.mixture()))
				GoggleLines.add(CELang.translate("goggles.mixture", mixture(current.mixture()))
					.style(current.mixture() < LEAN_MIXTURE || current.mixture() > RICH_MIXTURE ? ChatFormatting.GOLD
						: ChatFormatting.GRAY), tooltip, 1);
			if (!Float.isNaN(current.mixture()) && (current.mixture() < LEAN_MIXTURE || current.mixture() > RICH_MIXTURE)
				&& level.getBlockEntity(worldPosition.relative(state.getValue(CylinderBlock.FACING))) instanceof GalleryMember head)
				GoggleLines.add(CELang.translate("goggles.mixture.suggest",
						GalleryMember.balancedMetering(head.meteringFraction(), current.mixture()))
					.style(ChatFormatting.AQUA), tooltip, 2);
			if (current.sparkPlug()) {
				GoggleLines.add(CELang.translate("goggles.ignition.spark")
					.style(ChatFormatting.GRAY), tooltip, 1);
				int soot = Math.round(current.fouling() * 100);
				if (soot >= 5) {
					GoggleLines.add(CELang.translate("goggles.fouling", soot)
						.style(soot >= 50 ? ChatFormatting.RED : ChatFormatting.GOLD), tooltip, 1);
					if (isPlayerSneaking)
						GoggleLines.add(CELang.translate("goggles.fouling.hint")
							.style(ChatFormatting.DARK_GRAY), tooltip, 2);
				}
			} else if (!Float.isNaN(current.chargeTemp()) && !Float.isNaN(current.ignitionTemp()))
				GoggleLines.add(CELang.translate("goggles.compression", temperature(current.chargeTemp()),
						temperature(current.ignitionTemp()))
					.style(current.chargeTemp() >= current.ignitionTemp() ? ChatFormatting.GRAY : ChatFormatting.GOLD), tooltip, 1);
			if (!Float.isNaN(current.timingError()))
				GoggleLines.add(CELang.translate("goggles.timing", CamshaftBlockEntity.timingText(current.timingError()))
					.style(Math.abs(current.timingError()) < 7.5f ? ChatFormatting.GRAY : ChatFormatting.GOLD), tooltip, 1);
			else if (current.lobe() != null)
				GoggleLines.add(CELang.translate("goggles.timing", CELang.translate("goggles.timing.unlocked")
						.component())
					.style(ChatFormatting.RED), tooltip, 1);
			GoggleLines.add(CELang.translate("goggles.phase", Math.round(((current.tdcAngle() % 360) + 360) % 360))
				.style(ChatFormatting.DARK_GRAY), tooltip, 1);
		}

		for (CylinderFault fault : CylinderFault.values())
			if (fault.in(faults))
				GoggleLines.add(CELang.translate(fault.langKey())
					.style(fault.disablesCylinder ? ChatFormatting.RED : ChatFormatting.GOLD), tooltip, 1);
		return true;
	}

	/** The mixture as a plain reading: balanced, or how many times richer or leaner than balanced it is. */
	private static Component mixture(float equivalence) {
		if (equivalence < LEAN_MIXTURE)
			return CELang.translate("goggles.mixture.lean", String.format(java.util.Locale.ROOT, "%.1f", 1 / equivalence))
				.component();
		if (equivalence > RICH_MIXTURE)
			return CELang.translate("goggles.mixture.rich", String.format(java.util.Locale.ROOT, "%.1f", equivalence))
				.component();
		return CELang.translate("goggles.mixture.balanced")
			.component();
	}

	/** Shows a normalized temperature in kelvin, taking ambient as 300 K. */
	private static String temperature(float normalized) {
		return Math.round(normalized * 300) + " K";
	}
}
