package com.createengines.content.alternator;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import com.createengines.advancement.Achievements;
import com.createengines.advancement.Milestone;
import com.createengines.config.CEConfig;
import com.createengines.content.battery.BatteryBlockEntity;
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
 * Charges batteries while its shaft turns: those touching it, and those of the engine it touches. The charge it makes
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

	public AlternatorBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	public void attachToEngine(BlockPos primary, List<BlockPos> batteries) {
		if (primary.equals(primaryPos) && batteries.equals(engineBatteries))
			return;
		primaryPos = primary;
		engineBatteries = List.copyOf(batteries);
	}

	@Override
	public void detachFromEngine() {
		primaryPos = null;
		engineBatteries = List.of();
	}

	/** The Forge Energy this alternator offers on every face: take only, nothing can be put in. */
	public IEnergyStorage energyOutput() {
		return output;
	}

	@Override
	public float calculateStressApplied() {
		lastStressApplied = charging || supplying ? CHARGING_STRESS : 0;
		return lastStressApplied;
	}

	private List<BatteryBlockEntity> batteries() {
		Set<BlockPos> positions = new LinkedHashSet<>();
		for (Direction direction : Direction.values())
			positions.add(worldPosition.relative(direction));
		positions.addAll(engineBatteries);
		List<BatteryBlockEntity> found = new ArrayList<>();
		for (BlockPos pos : positions)
			if (level.isLoaded(pos) && level.getBlockEntity(pos) instanceof BatteryBlockEntity battery)
				found.add(battery);
		return found;
	}

	@Override
	public void tick() {
		super.tick();
		if (level == null || level.isClientSide)
			return;
		boolean turning = getSpeed() != 0 && !isOverStressed();
		List<BatteryBlockEntity> batteries = turning ? batteries() : List.of();
		float budget = turning ? Math.abs(getSpeed()) * CHARGE_PER_RPM : 0;
		boolean wanted = false;
		for (BatteryBlockEntity battery : batteries) {
			if (battery.isFull())
				continue;
			wanted = true;
			budget -= battery.charge(budget);
		}
		batteriesFound = batteries.size();
		chargeHold = wanted ? CHARGE_GRACE : turning ? Math.max(0, chargeHold - 1) : 0;
		wanted = chargeHold > 0;

		boolean drawing = supplyEnergy(turning);
		boolean changed = wanted != charging || drawing != supplying;
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
	 * Tops the buffer up from the shaft and pushes it into neighbours that take energy. Returns whether energy has been
	 * leaving it lately, which is when the shaft is made to work for it.
	 */
	private boolean supplyEnergy(boolean turning) {
		double perRpm = CEConfig.ALTERNATOR_ENERGY.get();
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

	private int capacity() {
		return (int) Math.max(1000, Math.min(Integer.MAX_VALUE / 2d, CEConfig.ALTERNATOR_ENERGY.get() * FASTEST_RPM * BUFFER_TICKS));
	}

	@Override
	protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
		super.write(tag, registries, clientPacket);
		tag.putBoolean("Charging", charging);
		tag.putBoolean("Supplying", supplying);
		tag.putInt("Batteries", batteriesFound);
	}

	@Override
	protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
		super.read(tag, registries, clientPacket);
		charging = tag.getBoolean("Charging");
		supplying = tag.getBoolean("Supplying");
		batteriesFound = tag.getInt("Batteries");
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
