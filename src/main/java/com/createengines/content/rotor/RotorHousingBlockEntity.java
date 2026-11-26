package com.createengines.content.rotor;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.createengines.config.CEClientConfig;
import com.createengines.content.EffectRate;
import com.createengines.content.EngineNoteSound;
import com.createengines.content.GoggleLines;
import com.createengines.content.crankshaft.CrankshaftBlockEntity;
import com.createengines.content.effects.EngineEffects;
import com.createengines.content.head.CylinderHeadBlockEntity;
import com.createengines.content.head.GalleryMember;
import com.createengines.engine.CylinderStatus;
import com.createengines.engine.EngineSummary;
import com.createengines.engine.sim.Muffling;
import com.createengines.engine.sim.CamProfile;
import com.createengines.engine.sim.CycleResult;
import com.createengines.engine.sim.CylinderModel;
import com.createengines.engine.sim.Mixture;
import com.createengines.engine.sim.Wear;
import com.createengines.engine.topology.CylinderFault;
import com.createengines.engine.topology.EngineTopology;
import com.createengines.registry.CEDataMaps;
import com.createengines.registry.CEFluids;
import com.createengines.registry.CELang;
import com.createengines.registry.CESounds;
import com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.fluid.SmartFluidTankBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import com.simibubi.create.foundation.fluid.CombinedTankWrapper;
import com.simibubi.create.foundation.fluid.SmartFluidTank;

import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.lang.LangBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/**
 * A rotor housing's block entity: a crankshaft link (so it carries rotation and sets its eccentric's angle like a
 * throw) that also holds a cylinder head's fuel and oil tanks and its injector's metering, and shows the state of the
 * rotor's three chambers.
 */
public class RotorHousingBlockEntity extends CrankshaftBlockEntity implements GalleryMember {

	private static final BehaviourType<SmartFluidTankBehaviour> OIL_TYPE = new BehaviourType<>("rotor_oil_tank");
	private static final BehaviourType<ScrollValueBehaviour> METERING_TYPE = new BehaviourType<>("rotor_fuel_metering");
	private static final String METERING_KEY = "Metering";

	/** Where the cycle puts the charge's firing: the top of the compression stroke. */
	private static final double FIRING_CYCLE_ANGLE = 360;
	/** Where in a chamber's cycle air is seen going in at the intake: a little way into its intake stroke. */
	private static final double DRAUGHT_CYCLE_ANGLE = 30;
	/** Mixtures within these equivalence ratios read as balanced. */
	private static final float LEAN_MIXTURE = (float) Mixture.LEAN_BAND;
	private static final float RICH_MIXTURE = (float) Mixture.RICH_BAND;

	protected SmartFluidTankBehaviour tank;
	protected SmartFluidTankBehaviour oil;
	public ScrollValueBehaviour metering;
	private IFluidHandler combined;

	private final @Nullable CylinderStatus[] chambers = new CylinderStatus[RotorHousingBlock.CHAMBERS];
	private final double[] lastCycle = {Double.NaN, Double.NaN, Double.NaN};
	private final EffectRate[] bangs = rates(EffectRate.BANG_INTERVAL);
	private final EffectRate[] puffs = rates(EffectRate.SMOKE_INTERVAL);
	private final EffectRate[] draughts = rates(EffectRate.SMOKE_INTERVAL);

	private static EffectRate[] rates(int interval) {
		EffectRate[] rates = new EffectRate[RotorHousingBlock.CHAMBERS];
		for (int i = 0; i < rates.length; i++)
			rates[i] = new EffectRate(interval);
		return rates;
	}

	public RotorHousingBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	@Override
	public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
		super.addBehaviours(behaviours);

		tank = SmartFluidTankBehaviour.single(this, CylinderHeadBlockEntity.CAPACITY);
		tank.getPrimaryHandler()
			.setValidator(CEDataMaps::isFuel);
		behaviours.add(tank);

		oil = new SmartFluidTankBehaviour(OIL_TYPE, this, 1, CylinderHeadBlockEntity.CAPACITY, false);
		oil.getPrimaryHandler()
			.setValidator(CEFluids::isLubricant);
		behaviours.add(oil);

		metering = new ScrollValueBehaviour(CELang.translate("value.fuel_metering")
			.component(), this, new RotorValueBox(4.5)) {
			@Override
			public BehaviourType<?> getType() {
				return METERING_TYPE;
			}

			/**
			 * Create sends a player's setting to the first value box whose network id matches. The eccentric's angle and this
			 * dial both said 0, so a change made on the fuel metering went to the angle (which is first in line) and the two
			 * dials seemed to be one.
			 */
			@Override
			public int netId() {
				return 1;
			}

			/** Create's clipboard copies settings under this key, so each dial needs its own: the angle keeps Create's default. */
			@Override
			public String getClipboardKey() {
				return METERING_KEY;
			}

			/**
			 * Create saves every scroll value under the same key, and the eccentric's angle is one already: this one keeps
			 * its own, or the two would overwrite each other whenever the block is saved or sent to a client.
			 */
			@Override
			public void write(CompoundTag nbt, HolderLookup.Provider registries, boolean clientPacket) {
				CompoundTag scratch = new CompoundTag();
				super.write(scratch, registries, clientPacket);
				nbt.putInt(METERING_KEY, scratch.getInt("ScrollValue"));
			}

			@Override
			public void read(CompoundTag nbt, HolderLookup.Provider registries, boolean clientPacket) {
				CompoundTag scratch = new CompoundTag();
				scratch.putInt("ScrollValue", nbt.contains(METERING_KEY) ? nbt.getInt(METERING_KEY)
					: CylinderHeadBlockEntity.DEFAULT_METERING);
				super.read(scratch, registries, clientPacket);
			}
		};
		metering.between(CylinderHeadBlockEntity.MIN_METERING, CylinderHeadBlockEntity.MAX_METERING);
		metering.value = CylinderHeadBlockEntity.DEFAULT_METERING;
		metering.withFormatter(v -> v * CylinderHeadBlockEntity.METERING_STEP + "%");
		metering.withCallback(v -> com.createengines.content.head.MeteringFollow.onChanged(level, worldPosition, v));
		behaviours.add(metering);
	}

	@Override
	protected ValueBoxTransform throwAngleBox() {
		return new RotorValueBox(11.5);
	}

	@Override
	public double meteringFraction() {
		return Math.max(CylinderHeadBlockEntity.MIN_METERING, metering.getValue()) * CylinderHeadBlockEntity.METERING_STEP
			/ 100d;
	}

	@Override
	public SmartFluidTank tank() {
		return tank.getPrimaryHandler();
	}

	@Override
	public SmartFluidTank oilTank() {
		return oil.getPrimaryHandler();
	}

	public IFluidHandler fluidCapability() {
		if (combined == null)
			combined = new CombinedTankWrapper(tank.getCapability(), oil.getCapability());
		return combined;
	}

	// Wear (opt-in, see ApexSeals): how worn the set of apex seals is, 0 new to 1 spent, and whether it has failed. Saved only once there is
	// something to save, so a housing that never wore saves what it always did.
	private double apexWear;
	private boolean apexFailed;

	/** How worn the spark plug in this housing is, 0 new to 1 spent (see {@code PlugWear}); saved only once there is some. */
	private double plugWear;

	public double plugWear() {
		return plugWear;
	}

	/** Adds wear to the plug. Clients are told when it moves to a new 5% step. */
	public void addPlugWear(double amount) {
		int before = (int) Math.round(plugWear * 20);
		plugWear = Wear.clamp(plugWear + amount);
		setChanged();
		if (before != (int) Math.round(plugWear * 20))
			sendData();
	}

	/** The plug in this housing is now one worn this much (0 for a new one, or for none). */
	public void fitPlug(double wear) {
		double next = Wear.clamp(wear);
		if (next == plugWear)
			return;
		plugWear = next;
		setChanged();
		sendData();
	}

	public double apexWear() {
		return apexWear;
	}

	/** The seals have failed: the housing is stopped until a new set is fitted. */
	public boolean apexFailed() {
		return apexFailed;
	}

	/** Whether the failed seals count now: with the wear mode off the failure stays saved, and is neither shown nor stops anything. */
	public boolean showsApexFailed() {
		return com.createengines.engine.sim.Tuning.wearMode && apexFailed;
	}

	/**
	 * Adds wear to the seals. Returns whether it moved to a new 5% step, which is when clients are told, so a slowly wearing engine does not
	 * send a packet a second.
	 */
	public boolean addApexWear(double wear) {
		int before = (int) Math.round(apexWear * 20);
		apexWear = Wear.clamp(apexWear + wear);
		setChanged();
		boolean visible = before != (int) Math.round(apexWear * 20);
		if (visible)
			sendData();
		return visible;
	}

	/** The seals fail: the housing stops, and the engine is looked at again. */
	public void failApex() {
		if (apexFailed)
			return;
		apexFailed = true;
		setChanged();
		sendData();
		EngineTopology.wearChanged(level, worldPosition);
	}

	/** A new set of seals is fitted: the old set's wear, and its failure, go with it. Returns whether anything changed. */
	public boolean fitSeals() {
		if (apexWear == 0 && !apexFailed)
			return false;
		boolean wasFailed = apexFailed;
		apexWear = 0;
		apexFailed = false;
		setChanged();
		sendData();
		if (wasFailed)
			EngineTopology.wearChanged(level, worldPosition);
		return true;
	}

	/** Sets right light wear of the seals and the plug (see {@link Wear#repairable}). A failed set is cured only by a new one. Returns whether anything changed. */
	public boolean service() {
		boolean seals = !apexFailed && Wear.repairable(apexWear);
		boolean plug = getBlockState().getValue(RotorHousingBlock.HAS_SPARK_PLUG) && Wear.repairable(plugWear);
		if (!seals && !plug)
			return false;
		if (seals)
			apexWear = Wear.repaired(apexWear);
		if (plug)
			plugWear = Wear.repaired(plugWear);
		setChanged();
		sendData();
		return true;
	}

	// The block's item keeps the seals' wear and failure, so breaking a housing and placing it again is no repair

	@Override
	protected void collectImplicitComponents(net.minecraft.core.component.DataComponentMap.Builder components) {
		super.collectImplicitComponents(components);
		if (apexWear > 0)
			components.set(com.createengines.registry.CEComponents.WEAR.get(), apexWear);
		if (apexFailed)
			components.set(com.createengines.registry.CEComponents.FAILED.get(), true);
	}

	@Override
	protected void applyImplicitComponents(DataComponentInput components) {
		super.applyImplicitComponents(components);
		Double carried = components.get(com.createengines.registry.CEComponents.WEAR.get());
		boolean failed = Boolean.TRUE.equals(components.get(com.createengines.registry.CEComponents.FAILED.get()));
		if (carried == null && !failed)
			return;
		apexWear = carried == null ? 0 : Wear.clamp(carried);
		apexFailed = failed;
		setChanged();
		sendData();
	}

	@Override
	@SuppressWarnings("deprecation")
	public void removeComponentsFromTag(CompoundTag tag) {
		super.removeComponentsFromTag(tag);
		tag.remove("ApexWear");
		tag.remove("ApexFailed");
	}

	// Status of the three chambers

	public @Nullable CylinderStatus chamber(int index) {
		return chambers[index];
	}

	public void updateChamber(int index, CylinderStatus next) {
		if (!next.differsFrom(chambers[index]))
			return;
		chambers[index] = next;
		sendData();
	}

	@Override
	public void detachFromEngine() {
		super.detachFromEngine();
		boolean any = false;
		for (int i = 0; i < chambers.length; i++) {
			any |= chambers[i] != null;
			chambers[i] = null;
		}
		if (any)
			sendData();
	}

	@Override
	protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
		super.write(tag, registries, clientPacket);
		if (apexWear > 0)
			tag.putDouble("ApexWear", apexWear);
		if (apexFailed)
			tag.putBoolean("ApexFailed", true);
		if (plugWear > 0)
			tag.putDouble("PlugWear", plugWear);
		if (clientPacket)
			for (int i = 0; i < chambers.length; i++)
				if (chambers[i] != null)
					tag.put("Chamber" + i, chambers[i].write());
	}

	@Override
	protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
		super.read(tag, registries, clientPacket);
		// Absent in a world from before wear existed; a damaged save must not hand the model NaN (clamp reads it as new)
		apexWear = Wear.clamp(tag.getDouble("ApexWear"));
		apexFailed = tag.getBoolean("ApexFailed");
		plugWear = Wear.clamp(tag.getDouble("PlugWear"));
		if (clientPacket)
			for (int i = 0; i < chambers.length; i++)
				chambers[i] = tag.contains("Chamber" + i) ? CylinderStatus.read(tag.getCompound("Chamber" + i)) : null;
	}

	// Effects

	@Override
	public void tick() {
		super.tick();
		if (level != null && level.isClientSide)
			tickClient();
	}

	/** A housing whose seals have failed keeps smoking, and now and then spits a spark. Only ever the wear mode's, and it needs no turning. */
	private void wearEffects() {
		if (!showsApexFailed() || level.random.nextInt(8) != 0)
			return;
		double x = worldPosition.getX() + 0.2 + level.random.nextDouble() * 0.6;
		double y = worldPosition.getY() + 0.8 + level.random.nextDouble() * 0.3;
		double z = worldPosition.getZ() + 0.2 + level.random.nextDouble() * 0.6;
		level.addParticle(ParticleTypes.SMOKE, x, y, z, 0, 0.04, 0);
		if (level.random.nextInt(6) == 0)
			level.addParticle(ParticleTypes.ELECTRIC_SPARK, x, y - 0.3, z, 0, 0.02, 0);
	}

	/**
	 * The bang, the draught at the intake and what the exhaust throws out, driven by the drawn shaft so they stay in step
	 * with the turning rotor: each chamber's cycle angle is two-thirds of the shaft's, a third of the way round from the
	 * last. The heat off the housing needs no turning.
	 */
	private void tickClient() {
		wearEffects();
		if (((level.getGameTime() + worldPosition.asLong()) & 3) == 0)
			EngineEffects.heat(level, worldPosition, engineSummary());
		if (getTheoreticalSpeed() == 0) {
			java.util.Arrays.fill(lastCycle, Double.NaN);
			return;
		}
		float renderTime = AnimationTickHolder.getRenderTime(level);
		double shaft = Math.abs(com.createengines.content.EnginePoses.drawnShaft(this, renderTime));
		for (int chamber = 0; chamber < chambers.length; chamber++) {
			CylinderStatus current = chambers[chamber];
			double cycle = (shaft % CylinderModel.ROTARY_CYCLE) * CylinderModel.ROTARY_SCALE
				+ chamber * CylinderModel.ROTARY_CHAMBER_SPACING * CylinderModel.ROTARY_SCALE;
			cycle %= 720;
			double previous = lastCycle[chamber];
			lastCycle[chamber] = cycle;
			if (current == null || Double.isNaN(previous))
				continue;
			double travelled = (cycle - previous + 720) % 720;
			// More than half a cycle in one frame, which a fast engine does: every mark went by, so none is told apart from
			// the others, and the effects' own rate limits are what thin them out
			if (travelled > 360)
				travelled = 720;
			if (chamber == 0 && CylinderFault.underwater(current.faults()) && (level.getGameTime() & 3) == 0) {
				EngineEffects.flooded(level, current);
				EngineEffects.submerged(level, worldPosition);
			}
			if (chamber == 0 && (level.getGameTime() & 3) == 0 && !current.intakeInlets()
				.isEmpty()) {
				Vec3 mouth = EngineEffects.openMouth(level, current);
				if (mouth != null)
					offerIntake(mouth);
			}
			if (current.result() == CycleResult.FIRED && crosses(previous, travelled, FIRING_CYCLE_ANGLE))
				ignite(chamber);
			if (!current.intakeInlets()
				.isEmpty() && crosses(previous, travelled, DRAUGHT_CYCLE_ANGLE) && draughts[chamber].allow(level))
				EngineEffects.draught(level, current, engineSummary());
			if (!current.exhaustOutlets()
				.isEmpty() && crosses(previous, travelled, CamProfile.ROTARY.exhaustOpen()) && puffs[chamber].allow(level))
				EngineEffects.exhaust(level, current, this);
		}
	}

	private static boolean crosses(double from, double travelled, double mark) {
		double ahead = (mark - from + 720) % 720;
		return ahead > 0 && ahead <= travelled;
	}

	/** The bang, and a flash of flame and sparks where the plug is. */
	private void ignite(int chamber) {
		if (!bangs[chamber].allow(level))
			return;
		if (level.getNearestPlayer(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5, 48,
			com.createengines.content.effects.EngineEffects.SPECTATORS_TOO) == null)
			return;
		RandomSource random = level.random;
		EngineSummary engine = engineSummary();
		float volume = CEClientConfig.bangVolume() * Muffling.volume(engine == null ? 0 : engine.muffling());
		// The engine's note has the bang in it, on the same firing
		boolean inTheNote = primaryPos() != null && EngineNoteSound.isPlayingFor(primaryPos());
		if (volume > 0 && !inTheNote)
			level.playLocalSound(worldPosition, CESounds.CYLINDER_FIRE.get(), SoundSource.BLOCKS, volume,
				1.05f + random.nextFloat() * 0.16f, false);
		// Photosensitive mode: the bang is heard, but the flash it comes with is left out
		if (!CEClientConfig.particles() || CEClientConfig.photosensitive())
			return;
		for (int i = 0; i < 6; i++) {
			double x = worldPosition.getX() + 0.3 + random.nextDouble() * 0.4;
			double y = worldPosition.getY() + 0.3 + random.nextDouble() * 0.4;
			double z = worldPosition.getZ() + 0.3 + random.nextDouble() * 0.4;
			double dx = (random.nextDouble() - 0.5) * 0.04;
			double dy = (random.nextDouble() - 0.5) * 0.04;
			double dz = (random.nextDouble() - 0.5) * 0.04;
			if (i < 2)
				level.addParticle(com.createengines.content.effects.GlowParticleOptions.fire(0xFFFFFF, 0xFFB44A, 0.12f, 0.9f,
					1.5f, 5), x, y, z, dx, dy, dz);
			else if (i < 5)
				level.addParticle(com.createengines.content.effects.GlowParticleOptions.fire(0xFFD680, 0xE0420A, 0.15f, 0.65f,
					1.3f, 9), x, y, z, dx, dy, dz);
			else
				level.addParticle(ParticleTypes.ELECTRIC_SPARK, x, y, z, dx * 1.5, dy * 1.5, dz * 1.5);
		}
	}

	// Goggles

	@Override
	public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
		CELang.translate("goggles.rotor")
			.forGoggles(tooltip);
		BlockState state = getBlockState();
		int faults = 0;
		CylinderStatus first = null;
		for (CylinderStatus status : chambers)
			if (status != null) {
				first = status;
				faults = status.faults();
				break;
			}
		if (!state.getValue(RotorHousingBlock.HAS_ROTOR))
			faults |= CylinderFault.NO_ROTOR.bit();
		if (!state.getValue(RotorHousingBlock.HAS_INJECTOR))
			faults |= CylinderFault.NO_INJECTOR.bit();
		if (!state.getValue(RotorHousingBlock.HAS_SPARK_PLUG))
			faults |= CylinderFault.NO_PLUG.bit();
		if (showsApexFailed())
			faults |= CylinderFault.APEX_FAILED.bit();

		if (!CylinderFault.disables(faults)) {
			// Chambers that all report the same say it once: three identical lines said nothing the first did not
			boolean same = true;
			int reporting = 0;
			for (CylinderStatus status : chambers)
				if (status != null) {
					reporting++;
					same &= status.result() == first.result();
				}
			if (same && reporting > 1)
				addChamber(tooltip, CELang.translate("goggles.rotor.chambers_all", resultText(first.result())), first.result());
			else
				for (int i = 0; i < chambers.length; i++) {
					CylinderStatus status = chambers[i];
					if (status != null)
						addChamber(tooltip, CELang.translate("goggles.rotor.chamber", i + 1, resultText(status.result())), status.result());
				}
			if (first != null) {
				if (first.rich())
					GoggleLines.add(CELang.translate("goggles.cycle.rich")
						.style(ChatFormatting.GOLD), tooltip, 1);
				if (first.lean())
					GoggleLines.add(CELang.translate("goggles.cycle.lean")
						.style(ChatFormatting.GOLD), tooltip, 1);
				if (!Float.isNaN(first.mixture()))
					GoggleLines.add(CELang.translate("goggles.mixture", mixture(first.mixture()))
						.style(first.mixture() < LEAN_MIXTURE || first.mixture() > RICH_MIXTURE ? ChatFormatting.GOLD
							: ChatFormatting.GRAY), tooltip, 1);
				if (!Float.isNaN(first.mixture()) && (first.mixture() < LEAN_MIXTURE || first.mixture() > RICH_MIXTURE))
					GoggleLines.add(CELang.translate("goggles.mixture.suggest",
							GalleryMember.balancedMetering(meteringFraction(), first.mixture()))
						.style(ChatFormatting.AQUA), tooltip, 2);
				float worstEarly = 0;
				for (CylinderStatus chamber : chambers)
					if (chamber != null)
						worstEarly = Math.max(worstEarly, chamber.preignition());
				int early = Math.round(worstEarly * 100);
				if (first.sparkPlug() && early >= 2) {
					GoggleLines.add(CELang.translate("goggles.preignition", early)
						.style(early >= 15 ? ChatFormatting.RED : ChatFormatting.GOLD), tooltip, 1);
					if (isPlayerSneaking)
						GoggleLines.add(CELang.translate("goggles.preignition.hint")
							.style(ChatFormatting.DARK_GRAY), tooltip, 2);
				}
				int soot = Math.round(first.fouling() * 100);
				if (first.sparkPlug() && soot >= 5) {
					GoggleLines.add(CELang.translate("goggles.fouling", soot)
						.style(soot >= 50 ? ChatFormatting.RED : ChatFormatting.GOLD), tooltip, 1);
					if (isPlayerSneaking)
						GoggleLines.add(CELang.translate("goggles.fouling.hint")
							.style(ChatFormatting.DARK_GRAY), tooltip, 2);
				}
			}
		}
		for (CylinderFault fault : CylinderFault.values())
			if (fault.in(faults))
				GoggleLines.add(CELang.translate(fault.langKey())
					.style(fault.disablesCylinder ? ChatFormatting.RED : ChatFormatting.GOLD), tooltip, 1);

		if (state.getValue(RotorHousingBlock.HAS_INJECTOR)) {
			GoggleLines.add(CELang.translate("goggles.head.metering", Math.round(meteringFraction() * 100))
				.style(ChatFormatting.AQUA), tooltip, 1);
			if (isPlayerSneaking)
				GoggleLines.add(CELang.translate("goggles.head.metering.hint")
					.style(ChatFormatting.DARK_GRAY), tooltip, 2);
		}
		if (state.getValue(RotorHousingBlock.HAS_SPARK_PLUG)) {
			if (state.getValue(RotorHousingBlock.HAS_COIL)) {
				GoggleLines.add(CELang.translate("goggles.head.coil")
					.style(ChatFormatting.AQUA), tooltip, 1);
				if (isPlayerSneaking)
					GoggleLines.add(CELang.translate("goggles.head.coil.hint")
						.style(ChatFormatting.DARK_GRAY), tooltip, 2);
			} else if (isPlayerSneaking)
				GoggleLines.add(CELang.translate("goggles.head.no_coil")
					.style(ChatFormatting.DARK_GRAY), tooltip, 1);
		}
		// The oil note is a tip for someone who has not found the feature, so it waits for the sneak like the other hints
		if (oilTank().isEmpty() && isPlayerSneaking)
			GoggleLines.add(CELang.translate("goggles.head.oil")
				.style(ChatFormatting.GRAY), tooltip, 1);
		int worn = (int) Math.round(apexWear * 100);
		if (com.createengines.engine.sim.Tuning.wearMode && !apexFailed && worn >= 5) {
			GoggleLines.add(CELang.translate("goggles.wear.apex", worn)
				.style(apexWear >= Wear.FAILURE_FROM ? ChatFormatting.RED : apexWear >= Wear.REPAIRABLE ? ChatFormatting.GOLD : ChatFormatting.GRAY), tooltip, 1);
			if (isPlayerSneaking)
				GoggleLines.add(CELang.translate(Wear.repairable(apexWear) ? "goggles.wear.light" : "goggles.wear.heavy_seals")
					.style(ChatFormatting.DARK_GRAY), tooltip, 2);
		}
		containedFluidTooltip(tooltip, isPlayerSneaking, fluidCapability());
		super.addToGoggleTooltip(tooltip, isPlayerSneaking);
		return true;
	}

	private static Component resultText(CycleResult result) {
		return CELang.translate("goggles.cycle." + result.name()
				.toLowerCase(java.util.Locale.ROOT))
			.component();
	}

	private static void addChamber(List<Component> tooltip, LangBuilder line, CycleResult result) {
		GoggleLines.add(line.style(result.fired() ? ChatFormatting.GREEN
			: result == CycleResult.NONE ? ChatFormatting.GRAY
				: ChatFormatting.RED), tooltip, 1);
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
