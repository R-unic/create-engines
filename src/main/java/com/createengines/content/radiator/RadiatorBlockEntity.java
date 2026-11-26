package com.createengines.content.radiator;

import java.util.List;
import java.util.Locale;

import com.createengines.compat.sable.SableCompat;
import com.createengines.content.GoggleLines;
import com.createengines.content.SavedNumbers;
import com.createengines.engine.LoadedBlocks;
import com.createengines.engine.topology.EngineMember;
import com.createengines.engine.sim.HeatRecovery;
import com.createengines.engine.sim.Radiator;
import com.createengines.engine.sim.Tuning;
import com.createengines.registry.CEFluids;
import com.createengines.registry.CELang;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.content.kinetics.fan.EncasedFanBlock;
import com.simibubi.create.content.kinetics.fan.EncasedFanBlockEntity;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock.HeatLevel;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.fluid.SmartFluidTankBehaviour;
import com.simibubi.create.foundation.fluid.SmartFluidTank;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/**
 * A radiator's coolant, the air going through it, and what the engine it is on last had it do. The engine works out the heat
 * ({@link Radiator}); this holds the coolant, looks for a fan and for its own speed through the world, and shows the heat it
 * is shedding to a basin or a boiler on top of it. An engine that stops telling it anything (switched off, unloaded or no longer
 * touching) has left it, and it goes cold.
 */
public class RadiatorBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation, EngineMember {

	public static final int CAPACITY = 1000;
	/** A fan blows at a radiator from this many blocks away at most, with only air between. */
	public static final int FAN_REACH = 3;
	/** How often, in ticks, it looks for a fan and measures its speed. */
	private static final int AIR_INTERVAL = 20;
	/** Ticks without a word from an engine before it counts itself off it. */
	private static final int ENGINE_TIMEOUT = 40;
	/** Faster than this, blocks a second, is a teleport and not a flight. */
	private static final double TELEPORT_SPEED = 200;
	/** Share of the gap to the heat being shed that its own heat closes each tick, as a heat exchanger's does. */
	private static final double SMOOTHING = 0.04;

	protected SmartFluidTankBehaviour coolant;

	private int sinceAir = AIR_INTERVAL;
	private int sinceEngine = ENGINE_TIMEOUT;
	private int sinceSignal;
	private int signal;
	private Vec3 lastWorldPos;
	private double fanRpm;
	private double speed;
	/** Heat a second given to the air, smoothed. */
	private double heat;
	private boolean boiling;
	/** Boiled coolant not yet a whole millibucket. */
	private double boilCredit;
	private int heatLevel;
	private int syncedHeat = -1;

	public RadiatorBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	@Override
	public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
		coolant = SmartFluidTankBehaviour.single(this, CAPACITY);
		coolant.getPrimaryHandler()
			.setValidator(CEFluids::isCoolant);
		behaviours.add(coolant);
	}

	public SmartFluidTank tank() {
		return coolant.getPrimaryHandler();
	}

	public IFluidHandler fluidCapability() {
		return coolant.getCapability();
	}

	/** What a comparator reads: 0 for an empty loop, 1 to 15 as it fills, 15 only when it is full. */
	public int comparatorSignal() {
		int amount = tank().getFluidAmount();
		return amount <= 0 ? 0 : 1 + (int) (14L * Math.min(amount, CAPACITY) / CAPACITY);
	}

	/** How well the coolant in the loop carries heat: 0 when it is empty. */
	public double quality() {
		return CEFluids.coolingQuality(tank().getFluid());
	}

	public double airflow() {
		return Radiator.airflow(fanRpm, speed);
	}

	public Radiator.Air air() {
		return Radiator.air(fanRpm, speed);
	}

	/** Heat a second it is giving to the air, smoothed. */
	public double heat() {
		return heat;
	}

	public boolean onEngine() {
		return sinceEngine < ENGINE_TIMEOUT;
	}

	/** What the engine had it do this tick (see {@link Radiator.Supply#ran}). */
	public void ran(double shed, double boiled) {
		sinceEngine = 0;
		heat += (shed - heat) * SMOOTHING;
		boiling = boiled > 0;
		if (boiled <= 0)
			return;
		boilCredit += boiled;
		int whole = (int) boilCredit;
		if (whole > 0) {
			tank().drain(whole, IFluidHandler.FluidAction.EXECUTE);
			boilCredit -= whole;
		}
	}

	@Override
	public void detachFromEngine() {
		if (sinceEngine >= ENGINE_TIMEOUT)
			return;
		sinceEngine = ENGINE_TIMEOUT;
		goCold();
		sendData();
	}

	private void goCold() {
		heat = 0;
		boiling = false;
		showLevel(HeatRecovery.NONE);
	}

	@Override
	public void tick() {
		super.tick();
		if (level == null || level.isClientSide)
			return;
		if (++sinceAir >= AIR_INTERVAL) {
			int ticks = sinceAir;
			sinceAir = 0;
			fanRpm = lookForFan();
			speed = measureSpeed(ticks);
		}
		if (sinceEngine < ENGINE_TIMEOUT && ++sinceEngine == ENGINE_TIMEOUT) {
			goCold();
			sendData();
		}
		if (onEngine()) {
			// A basin or boiler goes as far as heated: superheating is the exhaust heat exchanger's
			double usable = Tuning.heatRecoveryEnabled ? heat * Tuning.heatRecovery : 0;
			showLevel(Math.min(HeatRecovery.KINDLE, HeatRecovery.level(usable, heatLevel)));
		}
		if (++sinceSignal >= 10) {
			sinceSignal = 0;
			int next = comparatorSignal();
			if (next != signal) {
				signal = next;
				level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
			}
			// Goggles show whole heat units, the airflow to a tenth and whether it boils; send only when one of those moves
			int shown = (int) Math.round(heat) * 1000 + (int) Math.round(airflow() * 10) * 10 + (boiling ? 1 : 0)
				+ (onEngine() ? 2 : 0);
			if (shown != syncedHeat) {
				syncedHeat = shown;
				sendData();
			}
		}
	}

	/** The speed of the fastest Encased Fan blowing straight at it through clear air, 0 with none. */
	private double lookForFan() {
		double best = 0;
		for (Direction side : Direction.values())
			for (int distance = 1; distance <= FAN_REACH; distance++) {
				BlockPos at = worldPosition.relative(side, distance);
				if (!level.isLoaded(at))
					break;
				BlockState state = level.getBlockState(at);
				if (state.isAir())
					continue;
				if (state.getBlock() instanceof EncasedFanBlock
					&& state.getValue(BlockStateProperties.FACING) == side.getOpposite()
					&& level.getBlockEntity(at) instanceof EncasedFanBlockEntity fan
					&& fan.getSpeed() != 0 && fan.getAirFlowDirection() == side.getOpposite())
					best = Math.max(best, Math.abs(fan.getSpeed()));
				break;
			}
		return best;
	}

	/** How fast a moving structure is carrying it, blocks a second; 0 off one. */
	private double measureSpeed(int ticks) {
		if (!SableCompat.onStructure(level, worldPosition)) {
			lastWorldPos = null;
			return 0;
		}
		Vec3 now = SableCompat.worldPosition(level, worldPosition);
		Vec3 before = lastWorldPos;
		lastWorldPos = now;
		if (before == null || ticks <= 0)
			return 0;
		double moved = now.distanceTo(before) * 20 / ticks;
		return moved > TELEPORT_SPEED ? 0 : moved;
	}

	private void showLevel(int next) {
		heatLevel = next;
		if (level == null || level.isClientSide)
			return;
		BlockState state = getBlockState();
		if (!state.hasProperty(BlazeBurnerBlock.HEAT_LEVEL))
			return;
		HeatLevel target = next == HeatRecovery.KINDLE ? HeatLevel.KINDLED
			: next == HeatRecovery.SMOULDER ? HeatLevel.SMOULDERING : HeatLevel.NONE;
		if (state.getValue(BlazeBurnerBlock.HEAT_LEVEL) == target)
			return;
		level.setBlock(worldPosition, state.setValue(BlazeBurnerBlock.HEAT_LEVEL, target), Block.UPDATE_ALL);
		// A boiler on top only looks at its heaters when told to, and a block update need not reach it
		if (LoadedBlocks.blockEntity(level, worldPosition.above()) instanceof FluidTankBlockEntity tank)
			tank.updateBoilerTemperature();
	}

	@Override
	protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
		super.write(tag, registries, clientPacket);
		if (!clientPacket)
			return;
		tag.putBoolean("OnEngine", onEngine());
		tag.putDouble("Heat", heat);
		tag.putDouble("FanRpm", fanRpm);
		tag.putDouble("AirSpeed", speed);
		tag.putBoolean("Boiling", boiling);
	}

	/** Only the coolant is saved: the rest is read again from the engine and the world within a second. */
	@Override
	protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
		super.read(tag, registries, clientPacket);
		if (!clientPacket)
			return;
		sinceEngine = tag.getBoolean("OnEngine") ? 0 : ENGINE_TIMEOUT;
		heat = SavedNumbers.read(tag, "Heat", 0);
		fanRpm = SavedNumbers.read(tag, "FanRpm", 0);
		speed = SavedNumbers.read(tag, "AirSpeed", 0);
		boiling = tag.getBoolean("Boiling");
	}

	@Override
	public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
		CELang.translate("goggles.radiator")
			.forGoggles(tooltip);
		if (tank().isEmpty())
			GoggleLines.add(CELang.translate("goggles.radiator.empty")
				.style(ChatFormatting.RED), tooltip, 1);
		if (!onEngine())
			GoggleLines.add(CELang.translate("goggles.radiator.no_engine")
				.style(ChatFormatting.GOLD), tooltip, 1);
		else {
			GoggleLines.add(CELang.translate("goggles.radiator.shedding", String.format(Locale.ROOT, "%.0f", heat))
				.style(ChatFormatting.AQUA), tooltip, 1);
			if (boiling)
				GoggleLines.add(CELang.translate("goggles.radiator.boiling")
					.style(ChatFormatting.RED), tooltip, 1);
			if (getBlockState().getValue(BlazeBurnerBlock.HEAT_LEVEL) == HeatLevel.KINDLED)
				GoggleLines.add(CELang.translate("goggles.radiator.basin")
					.style(ChatFormatting.GREEN), tooltip, 1);
		}
		Radiator.Air air = air();
		GoggleLines.add(CELang.translate("goggles.radiator.air." + air.name()
				.toLowerCase(Locale.ROOT), String.format(Locale.ROOT, "%.1f", airflow()))
			.style(air == Radiator.Air.STILL ? ChatFormatting.GRAY : ChatFormatting.GREEN), tooltip, 1);
		if (isPlayerSneaking)
			GoggleLines.add(CELang.translate("goggles.radiator.hint")
				.style(ChatFormatting.DARK_GRAY), tooltip, 2);
		containedFluidTooltip(tooltip, isPlayerSneaking, fluidCapability());
		return true;
	}
}
