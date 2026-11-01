package com.createengines.content.rotor;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.createengines.config.CEClientConfig;
import com.createengines.content.EffectRate;
import com.createengines.content.GoggleLines;
import com.createengines.content.crankshaft.CrankshaftBlockEntity;
import com.createengines.content.airflow.PortOpeningBlock;
import com.createengines.content.head.CylinderHeadBlockEntity;
import com.createengines.content.head.GalleryMember;
import com.createengines.engine.CylinderStatus;
import com.createengines.engine.sim.CamProfile;
import com.createengines.engine.sim.CycleResult;
import com.createengines.engine.sim.CylinderModel;
import com.createengines.engine.sim.Mixture;
import com.createengines.engine.topology.CylinderFault;
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
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
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
			.setValidator(CEFluids::isEngineOil);
		behaviours.add(oil);

		metering = new ScrollValueBehaviour(CELang.translate("value.fuel_metering")
			.component(), this, new RotorValueBox(4.5)) {
			@Override
			public BehaviourType<?> getType() {
				return METERING_TYPE;
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
		if (clientPacket)
			for (int i = 0; i < chambers.length; i++)
				if (chambers[i] != null)
					tag.put("Chamber" + i, chambers[i].write());
	}

	@Override
	protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
		super.read(tag, registries, clientPacket);
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

	/**
	 * The bang and the smoke, driven by the drawn shaft so they stay in step with the turning rotor: each chamber's
	 * cycle angle is two-thirds of the shaft's, a third of the way round from the last.
	 */
	private void tickClient() {
		if (getTheoreticalSpeed() == 0) {
			java.util.Arrays.fill(lastCycle, Double.NaN);
			return;
		}
		float renderTime = AnimationTickHolder.getRenderTime(level);
		double shaft = Math.abs(rotation(renderTime) + throwAngle());
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
			if (current.result() == CycleResult.FIRED && crosses(previous, travelled, FIRING_CYCLE_ANGLE))
				ignite(chamber);
			if (!current.exhaustOutlets()
				.isEmpty() && crosses(previous, travelled, CamProfile.ROTARY.exhaustOpen()))
				tickExhaust(chamber, current);
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
			false) == null)
			return;
		RandomSource random = level.random;
		float volume = CEClientConfig.bangVolume();
		if (volume > 0)
			level.playLocalSound(worldPosition, CESounds.CYLINDER_FIRE.get(), SoundSource.BLOCKS, volume,
				1.05f + random.nextFloat() * 0.16f, false);
		if (!CEClientConfig.particles())
			return;
		for (int i = 0; i < 5; i++)
			level.addParticle(i < 3 ? ParticleTypes.SMALL_FLAME : ParticleTypes.ELECTRIC_SPARK,
				worldPosition.getX() + 0.3 + random.nextDouble() * 0.4, worldPosition.getY() + 0.3 + random.nextDouble() * 0.4,
				worldPosition.getZ() + 0.3 + random.nextDouble() * 0.4, (random.nextDouble() - 0.5) * 0.04,
				(random.nextDouble() - 0.5) * 0.04, (random.nextDouble() - 0.5) * 0.04);
	}

	/** Smoke from the exhaust when the exhaust port opens after a firing. */
	private void tickExhaust(int chamber, CylinderStatus current) {
		if (!CEClientConfig.particles() || !puffs[chamber].allow(level))
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

	/** Fuel went in and nothing lit it: it leaves the chamber as it came. */
	private static boolean unlit(CycleResult result) {
		return result == CycleResult.NO_IGNITION || result == CycleResult.NO_SPARK || result == CycleResult.TOO_LEAN
			|| result == CycleResult.TOO_RICH || result == CycleResult.FOULED;
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

		if (!CylinderFault.disables(faults)) {
			for (int i = 0; i < chambers.length; i++) {
				CylinderStatus status = chambers[i];
				if (status == null)
					continue;
				CycleResult result = status.result();
				ChatFormatting colour = result.fired() ? ChatFormatting.GREEN
					: result == CycleResult.NONE ? ChatFormatting.GRAY
						: ChatFormatting.RED;
				GoggleLines.add(CELang.translate("goggles.rotor.chamber", i + 1, CELang.translate("goggles.cycle."
						+ result.name()
							.toLowerCase(java.util.Locale.ROOT))
					.component())
					.style(colour), tooltip, 1);
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
		if (oilTank().isEmpty())
			GoggleLines.add(CELang.translate("goggles.head.oil")
				.style(ChatFormatting.GRAY), tooltip, 1);
		containedFluidTooltip(tooltip, isPlayerSneaking, fluidCapability());
		super.addToGoggleTooltip(tooltip, isPlayerSneaking);
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
}
