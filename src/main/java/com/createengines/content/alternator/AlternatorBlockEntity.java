package com.createengines.content.alternator;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import com.createengines.advancement.Achievements;
import com.createengines.advancement.Milestone;
import com.createengines.config.CEConfig;
import com.createengines.content.battery.BatteryBank;
import com.createengines.content.battery.BatteryBlockEntity;
import com.createengines.content.crankshaft.CrankshaftBlockEntity;
import com.createengines.engine.GhostRotation;
import com.createengines.engine.sim.ChargeRate;
import com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import com.createengines.engine.topology.EngineMember;
import com.createengines.content.GoggleLines;
import com.createengines.registry.CELang;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;

/**
 * Charges batteries while its shaft turns: those touching it (and the rest of their banks), and those of the engine it touches. The charge it makes
 * is shared out between them, and it only puts stress on its shaft while there is a battery to fill.
 * <p>
 * It also makes Forge Energy for whatever else wants it, so an engine can run other mods' machines: a small buffer
 * on every face that cables and machines can draw from, which it also pushes into any neighbour that takes energy. The
 * buffer tops itself up for nothing, but a shaft only feels stress while energy is really being drawn.
 */
public class AlternatorBlockEntity extends KineticBlockEntity implements EngineMember {

	/** Sparks of charge made per tick, per RPM of the shaft. */
	private static final float CHARGE_PER_RPM = 0.01f;
	/** Stress per RPM the alternator puts on its shaft while it is charging or supplying power. */
	private static final float CHARGING_STRESS = 2;
	/** Ticks of full output the buffer holds, at the fastest shaft Create allows. */
	private static final int BUFFER_TICKS = 20;
	private static final int FASTEST_RPM = 256;
	/** Ticks after energy last left the buffer for which the shaft is still counted as supplying. */
	private static final int SUPPLY_GRACE = 10;
	/**
	 * Ticks after a battery last had room for which the shaft is still counted as charging. A spark engine takes a spark
	 * out of a full battery every time a plug fires, so without this the load would switch on and off with every firing.
	 */
	private static final int CHARGE_GRACE = 20;
	/** How quickly the charge rate follows what was put in last tick: about the last second. */
	private static final float RATE_SMOOTHING = 0.05f;
	/** Ticks between working out the share of the engine's torque and what the other alternators make. */
	private static final int READOUT_INTERVAL = 10;

	private static final BehaviourType<ScrollValueBehaviour> RATE_TYPE = new BehaviourType<>("alternator_charge_rate");

	/** How much of its full output the alternator is allowed, in four steps (see {@link ChargeRate}). */
	public ScrollValueBehaviour chargeRateDial;

	private @Nullable BlockPos primaryPos;
	/** Batteries of the engine this alternator touches, set by the engine. */
	private List<BlockPos> engineBatteries = List.of();
	private boolean charging;
	private boolean supplying;
	private int batteriesFound;
	private final Output output = new Output();
	/** Energy made but too small to store as a whole unit yet. */
	private double energyCarry;
	private int idleTicks = SUPPLY_GRACE;
	private int chargeHold;
	private final GhostRotation ghost = new GhostRotation();
	/** Alternators of the engine this one touches (this one included), set by the engine. */
	private List<BlockPos> engineAlternators = List.of();
	/** Charge actually put into batteries a tick, smoothed over about a second: worked out on the server, sent to the client. */
	private float chargeRate;
	/** Its stress as a share of the engine's rated torque, 0 to 1. */
	private float engineShare;
	/** How many alternators the engine has charging, and the charge they make together a tick. */
	private int fellows = 1;
	private float fellowRate;
	private int ticks;

	public AlternatorBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	@Override
	public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
		super.addBehaviours(behaviours);
		chargeRateDial = new ScrollValueBehaviour(CELang.translate("value.alternator_rate")
			.component(), this, new ValueBox()) {
			@Override
			public BehaviourType<?> getType() {
				return RATE_TYPE;
			}

			@Override
			public void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
				tag.putInt("ChargeRateSetting", value);
			}

			@Override
			public void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
				// An alternator saved before the dial has no key and reads as full output; a damaged one is held to the dial's range
				value = tag.contains("ChargeRateSetting") ? ChargeRate.clamp(tag.getInt("ChargeRateSetting")) : ChargeRate.DEFAULT_SETTING;
			}
		};
		chargeRateDial.between(ChargeRate.MIN_SETTING, ChargeRate.MAX_SETTING);
		chargeRateDial.value = ChargeRate.DEFAULT_SETTING;
		chargeRateDial.withFormatter(v -> ChargeRate.percent(v) + "%");
		// The load on the shaft follows the dial at once, not at the next change of charging
		chargeRateDial.withCallback(v -> {
			if (level != null && !level.isClientSide && hasNetwork())
				getOrCreateNetwork().updateStressFor(this, calculateStressApplied());
		});
		behaviours.add(chargeRateDial);
	}

	/** The share of full output the dial allows, 0.25 to 1; 1 before the dial exists. */
	public float outputShare() {
		return chargeRateDial == null ? 1 : (float) ChargeRate.share(chargeRateDial.getValue());
	}

	/** A value box on any face the shaft does not run out of. */
	private static class ValueBox extends ValueBoxTransform.Sided {

		@Override
		protected Vec3 getSouthLocation() {
			return VecHelper.voxelSpace(8, 8, 14);
		}

		@Override
		protected boolean isSideActive(BlockState state, Direction direction) {
			return direction.getAxis() != state.getValue(BlockStateProperties.AXIS);
		}
	}

	public void attachToEngine(BlockPos primary, List<BlockPos> batteries, List<BlockPos> alternators) {
		if (primary.equals(primaryPos) && batteries.equals(engineBatteries) && alternators.equals(engineAlternators))
			return;
		primaryPos = primary;
		engineBatteries = List.copyOf(batteries);
		engineAlternators = List.copyOf(alternators);
	}

	/** The stress this alternator puts on its shaft as a share of the engine's rated torque, 0 to 1 (0 with no engine running). */
	public float engineShare() {
		return engineShare;
	}

	/** How many alternators the engine has, this one included. */
	public int fellowCount() {
		return fellows;
	}

	/** The charge all the engine's alternators make together, in sparks a tick lately. */
	public float fellowRate() {
		return fellowRate;
	}

	/** Sparks of charge this alternator has been putting into batteries a tick lately. */
	public float chargeRate() {
		return chargeRate;
	}

	@Override
	public void detachFromEngine() {
		primaryPos = null;
		engineBatteries = List.of();
		engineAlternators = List.of();
	}

	/** The Forge Energy this alternator offers on every face: take only, nothing can be put in. */
	public IEnergyStorage energyOutput() {
		return output;
	}

	@Override
	public float calculateStressApplied() {
		lastStressApplied = charging || supplying ? CHARGING_STRESS * outputShare() : 0;
		return lastStressApplied;
	}

	private List<BatteryBlockEntity> batteries() {
		Set<BlockPos> positions = new LinkedHashSet<>();
		for (Direction direction : Direction.values())
			positions.add(worldPosition.relative(direction));
		positions.addAll(engineBatteries);
		// A battery touching the alternator brings its whole bank with it
		Set<BatteryBlockEntity> found = new LinkedHashSet<>();
		for (BlockPos pos : positions)
			if (level.isLoaded(pos) && level.getBlockEntity(pos) instanceof BatteryBlockEntity battery)
				found.addAll(battery.bank());
		return new ArrayList<>(found);
	}

	@Override
	public void tick() {
		super.tick();
		if (level == null || level.isClientSide)
			return;
		ghost.tick(this);
		boolean turning = getSpeed() != 0 && !isOverStressed();
		List<BatteryBlockEntity> batteries = turning ? batteries() : List.of();
		float budget = turning ? Math.abs(getSpeed()) * CHARGE_PER_RPM * outputShare() : 0;
		boolean wanted = false;
		for (BatteryBlockEntity battery : batteries)
			if (!battery.isFull()) {
				wanted = true;
				break;
			}
		// The charge is shared out in proportion to the room each has, so a bank fills evenly
		float put = wanted ? BatteryBank.fill(batteries, budget) : 0;
		chargeRate += (put - chargeRate) * RATE_SMOOTHING;
		if (Math.abs(chargeRate) < 1e-3f)
			chargeRate = 0;
		batteriesFound = batteries.size();
		// An overstressed shaft reads as stopped, but the grace runs down through it instead of resetting: dropping the load
		// the moment the network overstresses, then restoring it the next tick, flipped the whole crank line between
		// stopped and turning every tick, and the throw and alternator were destroyed within a couple of seconds
		chargeHold = wanted ? CHARGE_GRACE : getTheoreticalSpeed() != 0 ? Math.max(0, chargeHold - 1) : 0;
		wanted = chargeHold > 0;

		boolean drawing = supplyEnergy(turning);
		boolean changed = wanted != charging || drawing != supplying;
		if (ticks++ % READOUT_INTERVAL == 0)
			changed |= updateReadout();
		if (wanted != charging && wanted)
			Achievements.award(level, List.of(worldPosition), Milestone.ALTERNATOR_CHARGING);
		if (drawing && !supplying)
			Achievements.award(level, List.of(worldPosition), Milestone.ENERGY_GENERATED);
		charging = wanted;
		supplying = drawing;
		if (changed) {
			if (hasNetwork())
				getOrCreateNetwork().updateStressFor(this, calculateStressApplied());
			sendData();
		}
	}

	/**
	 * Works out what the goggles say about this alternator's part in the engine: its stress as a share of the engine's rated torque,
	 * and how much of what all the engine's alternators make is its own. Returns whether anything a player would see changed.
	 */
	private boolean updateReadout() {
		float share = 0;
		if (primaryPos != null && level.isLoaded(primaryPos) && level.getBlockEntity(primaryPos) instanceof CrankshaftBlockEntity crank
			&& crank.publishedCapacity() > 0)
			share = Math.min(1, CHARGING_STRESS * outputShare() / crank.publishedCapacity());
		int count = 0;
		float total = 0;
		for (BlockPos pos : engineAlternators)
			if (level.isLoaded(pos) && level.getBlockEntity(pos) instanceof AlternatorBlockEntity other) {
				count++;
				total += other.chargeRate;
			}
		if (count == 0) {
			count = 1;
			total = chargeRate;
		}
		boolean changed = Math.round(share * 100) != Math.round(engineShare * 100) || count != fellows
			|| Math.round(total * 200) != Math.round(fellowRate * 200) || Math.round(chargeRate * 200) != Math.round(syncedRate * 200);
		engineShare = share;
		fellows = count;
		fellowRate = total;
		syncedRate = chargeRate;
		return changed;
	}

	private float syncedRate;

	/**
	 * Tops the buffer up from the shaft and pushes it into neighbours that take energy. Returns whether energy has been
	 * leaving it lately, which is when the shaft is made to work for it.
	 */
	private boolean supplyEnergy(boolean turning) {
		double perRpm = CEConfig.ALTERNATOR_ENERGY.get() * outputShare();
		if (turning && perRpm > 0) {
			double made = Math.abs(getSpeed()) * perRpm + energyCarry;
			int whole = (int) made;
			int stored = output.fill(Math.min(whole, output.space()));
			energyCarry = stored < whole ? 0 : made - whole;
		}
		for (Direction direction : Direction.values()) {
			if (output.energy <= 0)
				break;
			BlockPos at = worldPosition.relative(direction);
			if (!level.isLoaded(at))
				continue;
			IEnergyStorage neighbour = level.getCapability(Capabilities.EnergyStorage.BLOCK, at, direction.getOpposite());
			if (neighbour == null || !neighbour.canReceive())
				continue;
			int moved = neighbour.receiveEnergy(output.energy, false);
			output.energy -= moved;
			output.drawn += moved;
		}
		idleTicks = output.drawn > 0 ? 0 : Math.min(SUPPLY_GRACE, idleTicks + 1);
		output.drawn = 0;
		return turning && idleTicks < SUPPLY_GRACE;
	}

	private static float finite(float value) {
		return Float.isFinite(value) ? value : 0;
	}

	private int capacity() {
		return (int) Math.max(1000, Math.min(Integer.MAX_VALUE / 2d, CEConfig.ALTERNATOR_ENERGY.get() * FASTEST_RPM * BUFFER_TICKS));
	}

	@Override
	protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
		super.write(tag, registries, clientPacket);
		tag.putBoolean("Charging", charging);
		tag.putBoolean("Supplying", supplying);
		tag.putInt("Batteries", batteriesFound);
		tag.putFloat("ChargeRate", chargeRate);
		tag.putFloat("EngineShare", engineShare);
		tag.putInt("Fellows", fellows);
		tag.putFloat("FellowRate", fellowRate);
	}

	@Override
	protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
		super.read(tag, registries, clientPacket);
		charging = tag.getBoolean("Charging");
		supplying = tag.getBoolean("Supplying");
		batteriesFound = tag.getInt("Batteries");
		if (clientPacket) {
			chargeRate = finite(tag.getFloat("ChargeRate"));
			engineShare = Math.clamp(finite(tag.getFloat("EngineShare")), 0, 1);
			fellows = Math.clamp(tag.getInt("Fellows"), 1, 64);
			fellowRate = finite(tag.getFloat("FellowRate"));
		}
	}

	@Override
	public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
		CELang.translate("goggles.alternator")
			.forGoggles(tooltip);
		String key;
		ChatFormatting colour;
		if (charging) {
			key = "goggles.alternator.charging";
			colour = ChatFormatting.GREEN;
		} else if (supplying) {
			key = "goggles.alternator.supplying";
			colour = ChatFormatting.GREEN;
		} else if (batteriesFound == 0 && getSpeed() != 0) {
			key = "goggles.alternator.no_battery";
			colour = ChatFormatting.GOLD;
		} else {
			key = "goggles.alternator.idle";
			colour = ChatFormatting.GRAY;
		}
		if (supplying && charging)
			GoggleLines.add(CELang.translate("goggles.alternator.supplying")
				.style(ChatFormatting.GREEN), tooltip, 1);
		GoggleLines.add(CELang.translate(key)
			.style(colour), tooltip, 1);
		int allowed = chargeRateDial == null ? 100 : ChargeRate.percent(chargeRateDial.getValue());
		if (allowed < 100)
			GoggleLines.add(CELang.translate("goggles.alternator.limited", allowed)
				.style(ChatFormatting.GRAY), tooltip, 1);
		if (charging && chargeRate > 0)
			GoggleLines.add(CELang.translate("goggles.alternator.rate", String.format(java.util.Locale.ROOT, "%.1f", chargeRate * 20))
				.style(ChatFormatting.DARK_GREEN), tooltip, 1);
		if (fellows > 1 && fellowRate > 0)
			GoggleLines.add(CELang.translate("goggles.alternator.fellows", fellows, Math.round(chargeRate / fellowRate * 100))
				.style(ChatFormatting.GRAY), tooltip, 1);
		if ((charging || supplying) && engineShare > 0)
			GoggleLines.add(CELang.translate("goggles.alternator.share", Math.max(1, Math.round(engineShare * 100)))
				.style(ChatFormatting.GOLD), tooltip, 1);
		if (supplying)
			GoggleLines.add(CELang.translate("goggles.alternator.energy",
					Math.round(Math.abs(getSpeed()) * CEConfig.ALTERNATOR_ENERGY.get()))
				.style(ChatFormatting.DARK_GREEN), tooltip, 1);
		super.addToGoggleTooltip(tooltip, isPlayerSneaking);
		return true;
	}

	/** The buffer: anything may take from it, nothing may put in. */
	private final class Output implements IEnergyStorage {

		private int energy;
		/** Energy that has left since the tick began, taken by neighbours or by anything drawing from the capability. */
		private int drawn;

		private int space() {
			return capacity() - energy;
		}

		private int fill(int amount) {
			int added = Math.max(0, Math.min(amount, space()));
			energy += added;
			return added;
		}

		@Override
		public int receiveEnergy(int toReceive, boolean simulate) {
			return 0;
		}

		@Override
		public int extractEnergy(int toExtract, boolean simulate) {
			int out = Math.max(0, Math.min(toExtract, energy));
			if (!simulate) {
				energy -= out;
				drawn += out;
			}
			return out;
		}

		@Override
		public int getEnergyStored() {
			return energy;
		}

		@Override
		public int getMaxEnergyStored() {
			return capacity();
		}

		@Override
		public boolean canExtract() {
			return true;
		}

		@Override
		public boolean canReceive() {
			return false;
		}
	}
}
