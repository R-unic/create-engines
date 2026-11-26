package com.createengines.content.cylinder;

import com.createengines.content.CasingPaint;
import com.createengines.content.PaintedBlockEntity;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import org.joml.Vector3f;

import com.createengines.engine.LoadedBlocks;
import com.createengines.config.CEClientConfig;
import com.createengines.content.EffectRate;
import com.createengines.content.effects.ExhaustLook;
import com.createengines.content.EngineNoteSound;
import com.createengines.content.SoundProbe;
import com.createengines.content.EnginePoses;
import com.createengines.content.camshaft.CamshaftBlockEntity;
import com.createengines.content.crankshaft.CrankshaftBlock;
import com.createengines.content.crankshaft.CrankshaftBlockEntity;
import com.createengines.content.effects.EngineEffects;
import com.createengines.content.effects.ExhaustLook;
import com.createengines.content.effects.GlowParticleOptions;
import com.createengines.content.head.GalleryMember;
import com.createengines.engine.CylinderStatus;
import com.createengines.engine.EngineSummary;
import com.createengines.engine.sim.Muffling;
import com.createengines.engine.Diagnosis;
import com.createengines.engine.sim.CamProfile;
import com.createengines.engine.sim.CycleResult;
import com.createengines.engine.sim.CylinderModel;
import com.createengines.engine.sim.Knock;
import com.createengines.engine.sim.Mixture;
import com.createengines.engine.sim.Thermal;
import com.createengines.engine.sim.Tuning;
import com.createengines.engine.sim.Wear;
import com.createengines.engine.topology.CylinderFault;
import com.createengines.engine.topology.EngineTopology;
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
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class CylinderBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation, EngineMember, PaintedBlockEntity {
	/** The dye on this part's casing, which a state property used to hold (see {@link PaintedBlockEntity}). */
	private final PaintedBlockEntity.Cell paint = new PaintedBlockEntity.Cell();

	@Override
	public CasingPaint casingPaint() {
		return paint.get();
	}

	@Override
	public void setCasingPaint(CasingPaint dye) {
		paint.set(dye);
		setChanged();
		sendData();
	}


	// Wear (opt-in, see Wear): how worn the piston and the connecting rod fitted here are, 0 new to 1 spent. Saved only once there is
	// some, so a cylinder that never wore saves what it always did.
	private double pistonWear;
	private double rodWear;

	public double pistonWear() {
		return pistonWear;
	}

	public double rodWear() {
		return rodWear;
	}

	/** Which connecting rod is fitted (see {@link Wear.Rod}); saved only while it is not the standard one. */
	private Wear.Rod rodKind = Wear.Rod.STANDARD;

	public Wear.Rod rodKind() {
		return rodKind;
	}

	/** A rod of this kind is in: the engine is rebuilt, since the kind changes how the cylinder drags with the wear mode on or off. */
	public void setRodKind(Wear.Rod kind) {
		if (rodKind == kind)
			return;
		rodKind = kind;
		setChanged();
		sendData();
		EngineTopology.wearChanged(level, worldPosition);
	}

	/** A thrown rod's wreck is in the cylinder (the wear mode's, see {@link Wear}): saved only while it is, and cleared by a Service Kit. */
	private boolean thrown;

	public boolean thrown() {
		return thrown;
	}

	/** The piston has seized from running dry (the wear mode's): saved only while it has, and over once the piston comes out. */
	private boolean seized;

	/**
	 * How far this cylinder's piston has run dry (see {@code Wear.dryTime}). Kept here so it outlasts the engine being rebuilt, as it is when a
	 * neighbour seizes; not saved, so a reloaded world starts clean, which is the kinder way to be wrong.
	 */
	private double dryTime;

	public double dryTime() {
		return dryTime;
	}

	public void setDryTime(double dryTime) {
		this.dryTime = dryTime;
	}

	public boolean seized() {
		return seized;
	}

	/** Whether the thrown rod counts now: with the wear mode off its wreck stays saved, and is neither shown nor in the way. */
	public boolean showsThrown() {
		return Tuning.wearMode && thrown;
	}

	/** Whether the seizure counts now (see {@link #showsThrown}). */
	public boolean showsSeized() {
		return Tuning.wearMode && seized;
	}

	/** A part went in with the wear mode off, over a wreck nobody can see: the wreck goes, or the new part would share the bore with it later. */
	public void dropHiddenWreck() {
		if (Tuning.wearMode || !thrown)
			return;
		thrown = false;
		setChanged();
		sendData();
	}

	/** The piston seizes: it is left badly worn, the cylinder locks, and the engine is rebuilt around it. */
	public void seize() {
		if (seized)
			return;
		seized = true;
		dryTime = 0;
		pistonWear = Math.max(pistonWear, Wear.SEIZED_WEAR);
		setChanged();
		sendData();
		EngineTopology.wearChanged(level, worldPosition);
	}

	/** The seizure ends, because the seized piston came out. Returns whether there was one. */
	public boolean unseize() {
		if (!seized)
			return false;
		seized = false;
		setChanged();
		sendData();
		EngineTopology.wearChanged(level, worldPosition);
		return true;
	}

	/** The rod goes through: the wreck stays in, the parts are lost, and the engine is looked at again. */
	public void throwRod() {
		pistonWear = 0;
		rodWear = 0;
		thrown = true;
		setChanged();
		sendData();
		EngineTopology.wearChanged(level, worldPosition);
	}

	/**
	 * Adds wear to the fitted piston and rod. Returns whether either moved to a new 5% step, which is when clients are told, so a
	 * slowly wearing engine does not send a packet a second.
	 */
	public boolean addWear(double piston, double rod) {
		int before = step(pistonWear) * 100 + step(rodWear);
		pistonWear = Wear.clamp(pistonWear + piston);
		rodWear = Wear.clamp(rodWear + rod);
		setChanged();
		boolean visible = before != step(pistonWear) * 100 + step(rodWear);
		if (visible)
			sendData();
		return visible;
	}

	private static int step(double wear) {
		return (int) Math.round(wear * 20);
	}

	/** The piston changed: the old one's wear and how long it had run dry go with it. */
	public void resetPiston() {
		dryTime = 0;
		if (pistonWear == 0)
			return;
		pistonWear = 0;
		setChanged();
		sendData();
	}

	/** A worn piston is fitted: it keeps the wear it had. */
	public void fitPiston(double wear) {
		pistonWear = Wear.clamp(wear);
		setChanged();
		sendData();
	}

	/** A worn connecting rod is fitted: it keeps the wear it had. */
	public void fitRod(double wear) {
		rodWear = Wear.clamp(wear);
		setChanged();
		sendData();
	}

	/** A new connecting rod is in. */
	public void resetRod() {
		setRodKind(Wear.Rod.STANDARD);
		if (rodWear == 0)
			return;
		rodWear = 0;
		setChanged();
		sendData();
	}

	/**
	 * Sets right the light wear of whatever is fitted (see {@link Wear#repairable}). Returns whether anything changed. Heavy wear, and a
	 * part that is not here, are left alone: those are cured by replacing the part.
	 */
	public boolean service() {
		// A thrown rod's wreck comes first: only clearing it makes room for new parts
		if (thrown) {
			thrown = false;
			setChanged();
			sendData();
			EngineTopology.wearChanged(level, worldPosition);
			return true;
		}
		// A seized piston is not set right where it is: it has to come out (see WearService)
		if (seized)
			return false;
		BlockState state = getBlockState();
		double piston = state.getValue(CylinderBlock.HAS_PISTON) ? Wear.repaired(pistonWear) : pistonWear;
		double rod = state.getValue(CylinderBlock.HAS_ROD) ? Wear.repaired(rodWear) : rodWear;
		if (piston == pistonWear && rod == rodWear)
			return false;
		pistonWear = piston;
		rodWear = rod;
		setChanged();
		sendData();
		return true;
	}

	/** Where the cam says the charge fires: the top of the compression stroke. */
	private static final double FIRING_CYCLE_ANGLE = 360;
	/** Where in the cycle air is seen going in at the intake: the piston a little way down its intake stroke. */
	private static final double DRAUGHT_CYCLE_ANGLE = 30;
	/** The piston's crown above its wrist pin, in blocks (the 6/16 top of the piston model). */
	private static final float PISTON_CROWN = 6 / 16f;
	/** The head's face, in blocks from the cylinder's centre along the bore. */
	private static final double ROOF = 0.5;
	/** How far from the bore's axis gas is drawn, which keeps it between the frame posts. */
	private static final double LATERAL = 0.3;
	/** Half the width of the piston (10 of 16 pixels), which the gas volume fills out to. */
	private static final double CHAMBER_RADIUS = 5.2 / 16f;

	/** Mixtures within these equivalence ratios read as balanced. */
	private static final float LEAN_MIXTURE = (float) Mixture.LEAN_BAND;
	private static final float RICH_MIXTURE = (float) Mixture.RICH_BAND;

	private @Nullable CylinderStatus status;
	private double lastCamCycle = Double.NaN;
	private final EffectRate bangs = new EffectRate(EffectRate.BANG_INTERVAL);
	private final EffectRate puffs = new EffectRate(EffectRate.SMOKE_INTERVAL);
	private final EffectRate draughts = new EffectRate(EffectRate.SMOKE_INTERVAL);

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

	/** The crank throw this cylinder's connecting rod is on, if any: a plain journal has no pin for the rod to ride. */
	public @Nullable CrankshaftBlockEntity crankThrow() {
		Direction bore = getBlockState().getValue(CylinderBlock.FACING);
		if (level != null && LoadedBlocks.blockEntity(level, worldPosition.relative(bore.getOpposite()))
			instanceof CrankshaftBlockEntity crank && crank.getBlockState()
				.getBlock() instanceof CrankshaftBlock block && block.isThrow())
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
	 * in step with the moving parts: the bang at firing TDC, the gas in the bore, the draught at the intake as the piston
	 * starts down, and what the exhaust throws out when its valve opens. The heat off the head needs no cam.
	 */
	/** A piston that has seized keeps smoking from the hot metal, and a thrown rod's wreck leaks a little oil smoke. Only ever the wear mode's. */
	private void wearEffects() {
		if (!(showsSeized() || showsThrown()) || level.random.nextInt(showsSeized() ? 6 : 14) != 0)
			return;
		double x = worldPosition.getX() + 0.3 + level.random.nextDouble() * 0.4;
		double y = worldPosition.getY() + 0.7 + level.random.nextDouble() * 0.3;
		double z = worldPosition.getZ() + 0.3 + level.random.nextDouble() * 0.4;
		level.addParticle(showsSeized() ? net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE : net.minecraft.core.particles.ParticleTypes.SMOKE, x, y, z, 0, 0.03, 0);
	}

	private void tickClient() {
		wearEffects();
		CylinderStatus current = status;
		if (current != null && ((level.getGameTime() + worldPosition.asLong()) & 3) == 0) {
			CrankshaftBlockEntity crank = crankThrow();
			EngineEffects.heat(level, worldPosition.relative(getBlockState().getValue(CylinderBlock.FACING)),
				crank == null ? null : crank.engineSummary());
			EngineEffects.rockDust(level, worldPosition, crank == null ? null : crank.engineSummary());
			Vec3 mouth = crank == null || current.intakeInlets()
				.isEmpty() ? null : EngineEffects.openMouth(level, current);
			if (mouth != null)
				crank.offerIntake(mouth);
		}
		if (current == null || current.lobe() == null
			|| !(LoadedBlocks.blockEntity(level, current.lobe()) instanceof CamshaftBlockEntity lobe)) {
			lastCamCycle = Double.NaN;
			return;
		}
		float renderTime = AnimationTickHolder.getRenderTime(level);
		float camAngle = (float) Math.abs(lobe.rotation(renderTime) % 360);
		double cycle = CylinderModel.camCycleAngle(camAngle, lobe.lobeAngle() + current.lobeShift());
		double previous = lastCamCycle;
		lastCamCycle = cycle;
		reportPhaseToNote(lobe, camAngle);
		if (Double.isNaN(previous) || lobe.getTheoreticalSpeed() == 0)
			return;
		double travelled = (cycle - previous + 720) % 720;
		// More than half a cycle in one frame, which a fast engine does: every mark went by, so none is told apart from
		// the others, and the effects' own rate limits are what thin them out
		if (travelled > 360)
			travelled = 720;

		if (CylinderFault.underwater(current.faults()) && (level.getGameTime() & 3) == 0) {
			EngineEffects.flooded(level, current);
			BlockPos head = worldPosition.relative(getBlockState().getValue(CylinderBlock.FACING));
			EngineEffects.submerged(level, level.random.nextBoolean() ? worldPosition : head);
		}
		boolean fired = current.result() == CycleResult.FIRED;
		if (fired && crosses(previous, travelled, FIRING_CYCLE_ANGLE)) {
			probeFiring();
			ignite();
		}
		tickChamber(current, cycle, renderTime);
		if (!current.intakeInlets()
			.isEmpty() && crosses(previous, travelled, DRAUGHT_CYCLE_ANGLE) && draughts.allow(level)) {
			CrankshaftBlockEntity crank = crankThrow();
			EngineEffects.draught(level, current, crank == null ? null : crank.engineSummary());
		}
		if (!current.exhaustOutlets()
			.isEmpty() && crosses(previous, travelled, CamProfile.BASIC.exhaustOpen()) && puffs.allow(level))
			EngineEffects.exhaust(level, current, crankThrow());
	}

	/**
	 * Tells the engine's exhaust note where the cam is, so the note's firings land with the flashes and puffs drawn from
	 * the same cam instead of drifting against them. The engine's 720 degree cycle is twice the cam's angle, which is
	 * how the firing angles in the engine summary are measured. A cam that is not geared 1:2 to the crank has no fixed
	 * place in the cycle and is left out, as it is from those firing angles.
	 */
	private void reportPhaseToNote(CamshaftBlockEntity lobe, float camAngle) {
		CrankshaftBlockEntity crank = crankThrow();
		if (crank == null || crank.primaryPos() == null)
			return;
		float crankSpeed = Math.abs(crank.getTheoreticalSpeed());
		if (crankSpeed == 0 || Math.abs(2 * Math.abs(lobe.getTheoreticalSpeed()) - crankSpeed) > 0.01f * crankSpeed)
			return;
		EngineNoteSound.reportPhase(crank.primaryPos(), 2.0 * camAngle);
	}

	/**
	 * Where the gas in this cylinder is at {@code renderTime}, for drawing it as a body: the cam cycle angle, and the
	 * space between the piston's crown and the head along the bore. Null when nothing should be drawn: no engine status or
	 * cam, a stopped engine, no piston, or particles switched off.
	 */
	public @Nullable Chamber chamber(float renderTime) {
		CylinderStatus current = status;
		if (level == null || current == null || current.lobe() == null || !CEClientConfig.particles())
			return null;
		if (!(LoadedBlocks.blockEntity(level, current.lobe()) instanceof CamshaftBlockEntity lobe)
			|| lobe.getTheoreticalSpeed() == 0)
			return null;
		EnginePoses.CylinderPose pose = EnginePoses.cylinder(this, renderTime);
		if (!pose.showPiston())
			return null;
		Direction bore = getBlockState().getValue(CylinderBlock.FACING);
		float wrist = pose.piston()
			.transformPosition(0.5f, 0, 0.5f, new Vector3f())
			.get(bore.getAxis()
				.ordinal());
		double crown = (wrist - 0.5) * bore.getAxisDirection()
			.getStep() + PISTON_CROWN;
		if (ROOF - crown < 0.08)
			return null;
		float camAngle = (float) Math.abs(lobe.rotation(renderTime) % 360);
		double cycle = CylinderModel.camCycleAngle(camAngle, lobe.lobeAngle() + current.lobeShift());
		return new Chamber(cycle, crown, ROOF, CHAMBER_RADIUS, current, worldPosition.asLong());
	}

	/** The gas in a cylinder's bore: see {@link #chamber}. */
	public record Chamber(double cycle, double crown, double roof, double radius, CylinderStatus status, long pos) {
	}

	/** Tells the sound bench, if one is listening, of a firing drawn here. */
	private void probeFiring() {
		if (!SoundProbe.active())
			return;
		CrankshaftBlockEntity crank = crankThrow();
		SoundProbe.firing(crank == null ? null : crank.primaryPos());
	}

	/** The bang, and a flash of flame and sparks at the roof of the chamber. */
	/** The wear mode's knock, heard from the piston it is wearing: a high, hard ping on some of the firings that light early. */
	private void knockPing(int cylinders) {
		CylinderStatus current = status;
		if (!Tuning.wearMode || current == null)
			return;
		float volume = CEClientConfig.mechanicalVolume();
		if (volume > 0 && level.random.nextFloat() < ExhaustLook.knockPing(current.preignition(), cylinders))
			level.playLocalSound(worldPosition, CESounds.PREIGNITION_KNOCK.get(), SoundSource.BLOCKS, 0.5f * volume,
				1.45f + level.random.nextFloat() * 0.2f, false);
	}

	private void ignite() {
		if (!bangs.allow(level))
			return;
		if (level.getNearestPlayer(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5,
			48, com.createengines.content.effects.EngineEffects.SPECTATORS_TOO) == null)
			return;
		RandomSource random = level.random;
		CrankshaftBlockEntity throwBlock = crankThrow();
		EngineSummary engine = throwBlock == null ? null : throwBlock.engineSummary();
		knockPing(engine == null ? 1 : engine.cylinders());
		float volume = CEClientConfig.bangVolume() * Muffling.volume(engine == null ? 0 : engine.muffling());
		// With the engine's own exhaust note playing, the bang is in the note: it comes from the same firing, so it lands
		// with it, which a sample played from here a few tenths of a second ahead of the stream never would
		boolean inTheNote = throwBlock != null && throwBlock.primaryPos() != null
			&& EngineNoteSound.isPlayingFor(throwBlock.primaryPos());
		if (volume > 0 && !inTheNote)
			level.playLocalSound(worldPosition, CESounds.CYLINDER_FIRE.get(), SoundSource.BLOCKS, volume,
				0.92f + random.nextFloat() * 0.16f, false);
		// Photosensitive mode: the bang is heard, but the flash it comes with is left out
		if (!CEClientConfig.particles() || CEClientConfig.photosensitive())
			return;
		Direction bore = getBlockState().getValue(CylinderBlock.FACING);
		Vec3 along = Vec3.atLowerCornerOf(bore.getNormal());
		// A flash at the plug that blooms into a fireball: white-hot core, orange body, all added together
		for (int i = 0; i < 3; i++) {
			Vec3 at = chamberPoint(bore, ROOF - 0.04 - random.nextDouble() * 0.06);
			Vec3 v = along.scale(-0.03 - random.nextDouble() * 0.02).add(swirl(at, along, 0.012));
			level.addParticle(GlowParticleOptions.fire(0xFFFFFF, 0xFFB44A, 0.12f, 0.9f, 1.5f, 5), at.x, at.y, at.z, v.x, v.y, v.z);
		}
		for (int i = 0; i < 4; i++) {
			Vec3 at = chamberPoint(bore, ROOF - random.nextDouble() * 0.12);
			Vec3 v = along.scale(-0.025).add(swirl(at, along, 0.02));
			level.addParticle(GlowParticleOptions.fire(0xFFD680, 0xE0420A, 0.16f, 0.65f, 1.3f, 9), at.x, at.y, at.z, v.x, v.y, v.z);
		}
		for (int i = 0; i < 3; i++) {
			Vec3 at = chamberPoint(bore, ROOF - random.nextDouble() * 0.1);
			level.addParticle(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, (random.nextDouble() - 0.5) * 0.06,
				along.y * -0.03 + (random.nextDouble() - 0.5) * 0.06, (random.nextDouble() - 0.5) * 0.06);
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
			24, com.createengines.content.effects.EngineEffects.SPECTATORS_TOO) == null)
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

		double depth = ROOF - crown;
		// The old dust motes stay on top of the volume as fine detail: a speck of the stroke's colour now and then
		if (random.nextInt(2) == 0) {
			float mix = cycle < 180 ? 0 : cycle < 360 ? (float) (cycle - 180) / 180 : 1;
			boolean hot = fired && cycle >= 360 && cycle < 540;
			float red = hot ? 1f : Mth.lerp(mix, 0.78f, 1f);
			float green = hot ? 0.45f : Mth.lerp(mix, 0.9f, 0.5f);
			float blue = hot ? 0.1f : Mth.lerp(mix, 1f, 0.15f);
			if (cycle >= 540 || (cycle >= 360 && !fired))
				red = green = blue = current.rich() ? 0.1f : 0.45f;
			dust(chamberPoint(bore, crown + random.nextDouble() * depth), jitter(random, 0.012), red, green, blue, 0.4f);
		}
		if (cycle < 180) {
			if (CylinderFault.intakeShut(faults))
				return;
			// Cool air pours in past the valve at the roof, swirling as it falls after the piston
			for (int i = 0; i < 3; i++) {
				Vec3 at = chamberPoint(bore, ROOF - random.nextDouble() * 0.1);
				Vec3 v = along.scale(-(0.05 + 0.04 * random.nextDouble())).add(swirl(at, along, 0.03));
				level.addParticle(GlowParticleOptions.mist(0xD8F0FF, 0x6FA8E8, 0.085f, 0.5f, 0.7f, 8), at.x, at.y, at.z, v.x, v.y, v.z);
			}
		} else if (cycle < 360) {
			// The charge is squeezed and heats: pale blue haze turning to a hot orange glow as the crown rises
			float squeeze = (float) (cycle - 180) / 180;
			int count = 1 + (int) (squeeze * 3);
			int cold = blend(0x9CCBFF, 0xFFA040, squeeze);
			int warm = blend(0x5C8CD0, 0xC0300C, squeeze);
			for (int i = 0; i < count && !CEClientConfig.photosensitive(); i++) {
				Vec3 at = chamberPoint(bore, crown + random.nextDouble() * depth);
				Vec3 v = swirl(at, along, 0.02 + 0.03 * squeeze);
				level.addParticle(GlowParticleOptions.fire(cold, warm, 0.07f + 0.05f * squeeze, 0.18f + 0.4f * squeeze, 1.1f, 7),
					at.x, at.y, at.z, v.x, v.y, v.z);
			}
		} else if (cycle < 540) {
			if (CEClientConfig.photosensitive() && fired) {
				// Nothing glows in photosensitive mode: the burn and the cooling gas are left out
			} else if (fired && cycle < 450) {
				// The burn: a churning white-yellow to red fireball filling the space the piston is leaving
				float burn = (float) (cycle - 360) / 90;
				for (int i = 0; i < 4; i++) {
					Vec3 at = chamberPoint(bore, crown + random.nextDouble() * depth);
					Vec3 v = along.scale(-0.012).add(swirl(at, along, 0.035));
					level.addParticle(GlowParticleOptions.fire(blend(0xFFF2C0, 0xFFB040, burn), blend(0xFF6A18, 0x7A1404, burn),
						0.13f, 0.7f - 0.3f * burn, 1.25f, 7), at.x, at.y, at.z, v.x, v.y, v.z);
				}
			} else if (fired) {
				// Spent gas glowing as it cools
				Vec3 at = chamberPoint(bore, crown + random.nextDouble() * depth);
				Vec3 v = swirl(at, along, 0.015);
				level.addParticle(GlowParticleOptions.fire(0xFF7424, 0x3A1006, 0.1f, 0.4f, 1.0f, 9), at.x, at.y, at.z, v.x, v.y, v.z);
			} else if (ExhaustLook.unlit(result)) {
				Vec3 at = chamberPoint(bore, crown + random.nextDouble() * depth);
				Vec3 v = swirl(at, along, 0.01);
				level.addParticle(GlowParticleOptions.mist(0x9A9A9A, 0x555555, 0.09f, 0.35f, 1.1f, 9), at.x, at.y, at.z, v.x, v.y, v.z);
			}
		} else {
			if (CylinderFault.exhaustShut(faults))
				return;
			if (!fired && !ExhaustLook.unlit(result))
				return;
			// Smoke swept up out of the bore ahead of the rising piston and out through the valve
			int smoke = current.rich() ? 0x1C1C1C : fired ? 0x6A6A6A : 0x9C9C9C;
			for (int i = 0; i < 3; i++) {
				Vec3 at = chamberPoint(bore, crown + random.nextDouble() * 0.15);
				Vec3 v = along.scale(0.05 + 0.03 * random.nextDouble()).add(swirl(at, along, 0.025));
				level.addParticle(GlowParticleOptions.mist(smoke, blend(smoke, 0x202020, 0.5f), 0.09f, 0.45f, 1.3f, 8),
					at.x, at.y, at.z, v.x, v.y, v.z);
			}
			if (fired && random.nextInt(3) == 0 && !CEClientConfig.photosensitive()) {
				Vec3 at = chamberPoint(bore, crown + random.nextDouble() * 0.15);
				level.addParticle(GlowParticleOptions.fire(0xFF8A30, 0x501408, 0.05f, 0.5f, 0.8f, 7), at.x, at.y, at.z,
					along.x * 0.05, along.y * 0.05, along.z * 0.05);
			}
		}
	}

	/** Gas circling the bore: the tangent about its axis at {@code at}, {@code speed} per 0.25 blocks out from the axis. */
	private Vec3 swirl(Vec3 at, Vec3 along, double speed) {
		Vec3 radial = at.subtract(Vec3.atCenterOf(worldPosition));
		radial = radial.subtract(along.scale(radial.dot(along)));
		return along.cross(radial).scale(speed * 4);
	}

	/** The colour {@code mix} of the way from one 0xRRGGBB to another. */
	private static int blend(int from, int to, float mix) {
		int out = 0;
		for (int shift = 16; shift >= 0; shift -= 8)
			out |= Math.round(Mth.lerp(mix, from >> shift & 255, to >> shift & 255)) << shift;
		return out;
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

	/** Whether a cam-cycle angle advancing from {@code from} by {@code travelled} passes {@code mark}. */
	private static boolean crosses(double from, double travelled, double mark) {
		double ahead = (mark - from + 720) % 720;
		return ahead > 0 && ahead <= travelled;
	}

	// The block's item keeps a thrown rod's wreck (the fitted parts drop on their own, with their wear), so breaking and placing it clears nothing

	@Override
	protected void collectImplicitComponents(net.minecraft.core.component.DataComponentMap.Builder components) {
		super.collectImplicitComponents(components);
		if (thrown)
			components.set(com.createengines.registry.CEComponents.FAILED.get(), true);
	}

	@Override
	protected void applyImplicitComponents(DataComponentInput components) {
		super.applyImplicitComponents(components);
		if (!Boolean.TRUE.equals(components.get(com.createengines.registry.CEComponents.FAILED.get())))
			return;
		thrown = true;
		setChanged();
		sendData();
	}

	@Override
	@SuppressWarnings("deprecation")
	public void removeComponentsFromTag(CompoundTag tag) {
		super.removeComponentsFromTag(tag);
		tag.remove("Thrown");
	}

	@Override
	protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
		super.write(tag, registries, clientPacket);
		paint.write(tag);
		if (pistonWear > 0)
			tag.putDouble("PistonWear", pistonWear);
		if (rodWear > 0)
			tag.putDouble("RodWear", rodWear);
		if (rodKind != Wear.Rod.STANDARD)
			tag.putInt("Rod", rodKind.ordinal());
		if (thrown)
			tag.putBoolean("Thrown", true);
		if (seized)
			tag.putBoolean("Seized", true);
		if (clientPacket && status != null)
			tag.put("Status", status.write());
	}

	@Override
	protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
		super.read(tag, registries, clientPacket);
		paint.read(tag, level, worldPosition, getBlockState());
		// Absent in a world from before wear existed; a damaged save must not hand the model NaN (clamp reads it as new)
		pistonWear = Wear.clamp(tag.getDouble("PistonWear"));
		rodWear = Wear.clamp(tag.getDouble("RodWear"));
		rodKind = Wear.Rod.byId(tag.getInt("Rod"));
		thrown = tag.getBoolean("Thrown");
		seized = tag.getBoolean("Seized");
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
			// Why it did not fire, in words a player can act on (what a fault is stopping is listed with the faults below)
			Diagnosis.Cause cause = Diagnosis.ofCylinder(0, result);
			if (cause != Diagnosis.Cause.NONE)
				GoggleLines.add(CELang.translate("goggles.cylinder.cause", CELang.translate(cause.langKey())
						.string())
					.style(ChatFormatting.GRAY), tooltip, 2);
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
				&& LoadedBlocks.blockEntity(level, worldPosition.relative(state.getValue(CylinderBlock.FACING))) instanceof GalleryMember head)
				GoggleLines.add(CELang.translate("goggles.mixture.suggest",
						GalleryMember.balancedMetering(head.meteringFraction(), current.mixture()))
					.style(ChatFormatting.AQUA), tooltip, 2);
			if (current.sparkPlug() && Math.round(current.preignition() * 100) >= 2) {
					int early = Math.round(current.preignition() * 100);
					GoggleLines.add(CELang.translate("goggles.preignition", early)
						.style(early >= 15 ? ChatFormatting.RED : ChatFormatting.GOLD), tooltip, 1);
					if (isPlayerSneaking)
						GoggleLines.add(CELang.translate("goggles.preignition.hint")
							.style(ChatFormatting.DARK_GRAY), tooltip, 2);
				}
				if (Tuning.wearMode && current.sparkPlug() && Knock.intensity(current.preignition()) > 0)
					GoggleLines.add(CELang.translate("goggles.wear.knock")
						.style(ChatFormatting.GOLD), tooltip, 1);
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
				GoggleLines.add(CELang.translate("goggles.compression", Thermal.chargeDegrees(current.chargeTemp()),
						Thermal.chargeDegrees(current.ignitionTemp()))
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
		if (showsThrown())
			GoggleLines.add(CELang.translate("goggles.wear.thrown")
				.style(ChatFormatting.RED), tooltip, 1);
		if (showsSeized())
			GoggleLines.add(CELang.translate("goggles.wear.seized")
				.style(ChatFormatting.RED), tooltip, 1);
		wearLine(tooltip, "goggles.wear.piston",state.getValue(CylinderBlock.HAS_PISTON), pistonWear, isPlayerSneaking);
		wearLine(tooltip, "goggles.wear.rod", state.getValue(CylinderBlock.HAS_ROD), rodWear, isPlayerSneaking);
		// Which rod is in matters with the wear mode on or off, as it drags its own way either way
		if (state.getValue(CylinderBlock.HAS_ROD) && rodKind != Wear.Rod.STANDARD)
			GoggleLines.add(CELang.translate("goggles.wear.rod." + rodKind.name().toLowerCase(java.util.Locale.ROOT))
				.style(ChatFormatting.AQUA), tooltip, 1);
		return true;
	}

	/** How worn a part is, only with the wear mode on and once it has any (a cylinder that never wore says nothing). */
	static void wearLine(List<Component> tooltip, String key, boolean fitted, double wear, boolean sneaking) {
		int percent = (int) Math.round(wear * 100);
		if (!Tuning.wearMode || !fitted || percent < 5)
			return;
		GoggleLines.add(CELang.translate(key, percent)
			.style(wear >= Wear.FAILURE_FROM ? ChatFormatting.RED : wear >= Wear.REPAIRABLE ? ChatFormatting.GOLD : ChatFormatting.GRAY), tooltip, 1);
		if (sneaking)
			GoggleLines.add(CELang.translate(Wear.repairable(wear) ? "goggles.wear.light" : "goggles.wear.heavy")
				.style(ChatFormatting.DARK_GRAY), tooltip, 2);
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

}
