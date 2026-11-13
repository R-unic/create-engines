package com.createengines.content.dyno;

import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import com.createengines.engine.LoadedBlocks;
import com.createengines.content.GoggleLines;
import com.createengines.content.crankshaft.CrankshaftBlockEntity;
import com.createengines.engine.EngineController;
import com.createengines.engine.sim.RunComparison;
import com.createengines.engine.topology.EngineScanner;
import com.createengines.registry.CELang;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Reads the engine it touches: the curve the engine's own rating is made from, turned into what the network can use at
 * each speed (stress capacity per RPM, and the power that is, stress capacity at full speed). The figures are taken
 * every half second on the server and sent to anyone looking.
 */
public class DynoBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {

	private static final int INTERVAL = 10;
	/** Share of the peak power the powerband is the speeds above. */
	private static final float POWERBAND = 0.9f;
	private static final String BARS = "▁▂▃▄▅▆▇█";

	/**
	 * What the dynamometer shows.
	 *
	 * @param rpm      the speeds the curve was measured at
	 * @param torque   stress capacity per RPM the engine can be rated for at each of them
	 * @param current  the engine's speed now
	 * @param rated    stress capacity per RPM the engine is rated at for the speed it is turning now
	 * @param complete the whole curve has been measured
	 */
	public record Readout(float[] rpm, float[] torque, float current, float rated, boolean complete) {

		/** Stress capacity, SU, at each speed. */
		public float power(int index) {
			return torque[index] * rpm[index];
		}

		public int peakTorque() {
			int best = 0;
			for (int i = 1; i < torque.length; i++)
				if (torque[i] > torque[best])
					best = i;
			return best;
		}

		public int peakPower() {
			int best = 0;
			for (int i = 1; i < torque.length; i++)
				if (power(i) > power(best))
					best = i;
			return best;
		}

		boolean differsFrom(@Nullable Readout other) {
			if (other == null || other.torque.length != torque.length || complete != other.complete
				|| Math.abs(current - other.current) >= 1 || Math.abs(rated - other.rated) > 0.01f * Math.max(1, other.rated))
				return true;
			for (int i = 0; i < torque.length; i++)
				if (Math.abs(torque[i] - other.torque[i]) > 0.01f * Math.max(1, other.torque[i]))
					return true;
			return false;
		}

		CompoundTag write() {
			CompoundTag tag = new CompoundTag();
			int[] speeds = new int[rpm.length];
			int[] torques = new int[rpm.length];
			for (int i = 0; i < speeds.length; i++) {
				speeds[i] = Math.round(rpm[i]);
				torques[i] = Math.round(torque[i] * 10);
			}
			tag.putIntArray("Rpm", speeds);
			tag.putIntArray("Torque", torques);
			tag.putFloat("Current", current);
			tag.putFloat("Rated", rated);
			tag.putBoolean("Complete", complete);
			return tag;
		}

		static Readout read(CompoundTag tag) {
			int[] speeds = tag.getIntArray("Rpm");
			int[] torques = tag.getIntArray("Torque");
			float[] rpm = new float[speeds.length];
			float[] torque = new float[speeds.length];
			for (int i = 0; i < speeds.length && i < torques.length; i++) {
				rpm[i] = speeds[i];
				torque[i] = torques[i] / 10f;
			}
			return new Readout(rpm, torque, tag.getFloat("Current"), tag.getFloat("Rated"), tag.getBoolean("Complete"));
		}
	}

	private @Nullable Readout readout;
	/** The last whole curve seen, held until the engine's curve starts to be measured again. */
	private @Nullable Readout lastComplete;
	/** The run before the one on show: what the dyno compares the new curve against. */
	private @Nullable Readout previous;
	private int ticks;
	private int signal;

	public DynoBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	@Override
	public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
	}

	/** Redstone signal: 0 with no engine, rising to 15 as the engine reaches the speed of its peak power. */
	public int signal() {
		return signal;
	}

	public @Nullable Readout readout() {
		return readout;
	}

	@Override
	public void tick() {
		super.tick();
		if (level == null || level.isClientSide || ++ticks % INTERVAL != 0)
			return;
		Readout next = measure();
		boolean changed = next == null ? readout != null : next.differsFrom(readout);
		readout = next;
		if (next != null && next.complete)
			lastComplete = next;
		else if (next != null && lastComplete != null) {
			// The engine's curve is being measured again: it has been changed, or rebuilt. The run it had is the one to beat
			previous = lastComplete;
			lastComplete = null;
			changed = true;
			setChanged();
		}
		if (changed)
			sendData();
		int signalNow = signalFor(next);
		if (signalNow != signal) {
			signal = signalNow;
			level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
		}
	}

	private static int signalFor(@Nullable Readout readout) {
		if (readout == null || readout.rpm.length == 0)
			return 0;
		float target = readout.rpm[readout.peakPower()];
		if (target <= 0)
			return 0;
		return Math.clamp(Math.round(15 * readout.current / target), 0, 15);
	}

	/** The engine against any of this block's faces, and what its rating looks like. */
	private @Nullable Readout measure() {
		for (Direction face : Direction.values()) {
			Set<BlockPos> seeds = EngineScanner.crankSeeds(level, worldPosition.relative(face));
			if (seeds.isEmpty())
				continue;
			if (!(LoadedBlocks.blockEntity(level, seeds.iterator()
				.next()) instanceof CrankshaftBlockEntity crank))
				continue;
			EngineController controller = crank.controller();
			BlockPos primary = crank.primaryPos();
			if (controller == null && primary != null && LoadedBlocks.blockEntity(level, primary) instanceof CrankshaftBlockEntity main)
				controller = main.controller();
			if (controller == null)
				continue;
			EngineController.Curve curve = controller.curve();
			return new Readout(curve.rpm(), curve.torque(), curve.current(), curve.rated(), curve.complete());
		}
		return null;
	}

	@Override
	protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
		super.write(tag, registries, clientPacket);
		if (clientPacket && readout != null)
			tag.put("Readout", readout.write());
		if (previous != null)
			tag.put("Previous", previous.write());
	}

	@Override
	protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
		super.read(tag, registries, clientPacket);
		if (clientPacket)
			readout = tag.contains("Readout") ? Readout.read(tag.getCompound("Readout")) : null;
		previous = tag.contains("Previous") ? Readout.read(tag.getCompound("Previous")) : null;
	}

	@Override
	public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
		CELang.translate("goggles.dyno")
			.forGoggles(tooltip);
		Readout current = readout;
		if (current == null || current.rpm.length == 0) {
			GoggleLines.add(CELang.translate("goggles.dyno.no_engine")
				.style(ChatFormatting.RED), tooltip, 1);
			return true;
		}
		if (!current.complete)
			GoggleLines.add(CELang.translate("goggles.dyno.measuring")
				.style(ChatFormatting.GOLD), tooltip, 1);
		GoggleLines.add(CELang.translate("goggles.dyno.torque", spark(current.torque))
			.style(ChatFormatting.AQUA), tooltip, 1);
		float[] power = new float[current.torque.length];
		for (int i = 0; i < power.length; i++)
			power[i] = current.power(i);
		GoggleLines.add(CELang.translate("goggles.dyno.power", spark(power))
			.style(ChatFormatting.GREEN), tooltip, 1);
		GoggleLines.add(CELang.translate("goggles.dyno.range", Math.round(current.rpm[0]),
				Math.round(current.rpm[current.rpm.length - 1]))
			.style(ChatFormatting.DARK_GRAY), tooltip, 1);
		int torquePeak = current.peakTorque();
		int powerPeak = current.peakPower();
		GoggleLines.add(CELang.translate("goggles.dyno.peak_torque", compact(current.torque[torquePeak]),
				Math.round(current.rpm[torquePeak]))
			.style(ChatFormatting.GRAY), tooltip, 1);
		GoggleLines.add(CELang.translate("goggles.dyno.peak_power", compact(current.power(powerPeak)),
				Math.round(current.rpm[powerPeak]))
			.style(ChatFormatting.GRAY), tooltip, 1);
		int low = powerPeak;
		int high = powerPeak;
		while (low > 0 && current.power(low - 1) >= POWERBAND * current.power(powerPeak))
			low--;
		while (high < power.length - 1 && current.power(high + 1) >= POWERBAND * current.power(powerPeak))
			high++;
		GoggleLines.add(CELang.translate("goggles.dyno.powerband", Math.round(current.rpm[low]),
				Math.round(current.rpm[high]))
			.style(ChatFormatting.GRAY), tooltip, 1);
		GoggleLines.add(CELang.translate("goggles.dyno.now", Math.round(current.current))
			.style(ChatFormatting.DARK_GRAY), tooltip, 1);
		if (current.rated > 0)
			GoggleLines.add(CELang.translate("goggles.dyno.rated", compact(current.rated), compact(current.rated * current.current))
				.style(ChatFormatting.DARK_GRAY), tooltip, 1);
		addComparison(tooltip, current);
		if (isPlayerSneaking)
			GoggleLines.add(CELang.translate("goggles.dyno.hint")
				.style(ChatFormatting.DARK_GRAY), tooltip, 2);
		return true;
	}

	/** What changed since the run before, once this one is whole: green for more, red for less. */
	private void addComparison(List<Component> tooltip, Readout current) {
		Readout before = previous;
		if (before == null || !current.complete || before.rpm.length == 0)
			return;
		RunComparison change = RunComparison.between(before.rpm, before.torque, current.rpm, current.torque);
		GoggleLines.add(CELang.translate("goggles.dyno.previous")
			.style(ChatFormatting.GRAY), tooltip, 1);
		if (change.same()) {
			GoggleLines.add(CELang.translate("goggles.dyno.delta_same")
				.style(ChatFormatting.DARK_GRAY), tooltip, 2);
			return;
		}
		GoggleLines.add(CELang.translate("goggles.dyno.delta_torque", signed(change.torquePercent()), signed(change.torqueDelta()))
			.style(shade(change.torquePercent())), tooltip, 2);
		GoggleLines.add(CELang.translate("goggles.dyno.delta_power", signed(change.powerPercent()), signed(change.powerDelta()))
			.style(shade(change.powerPercent())), tooltip, 2);
		if (Math.abs(change.peakShift()) >= 1)
			GoggleLines.add(CELang.translate("goggles.dyno.delta_peak", signed(change.peakShift()))
				.style(ChatFormatting.GRAY), tooltip, 2);
		if (Math.abs(change.bandDelta()) >= 1)
			GoggleLines.add(CELang.translate("goggles.dyno.delta_band", signed(change.bandDelta()))
				.style(shade(change.bandDelta())), tooltip, 2);
	}

	private static ChatFormatting shade(float change) {
		return Math.abs(change) < RunComparison.SAME ? ChatFormatting.GRAY : change > 0 ? ChatFormatting.GREEN : ChatFormatting.RED;
	}

	private static String signed(float value) {
		return Math.abs(value) >= 100 ? String.format(Locale.ROOT, "%+.0f", value) : String.format(Locale.ROOT, "%+.1f", value);
	}

	/** A row of bars as tall as each value is against the biggest, at most 24 of them. */
	static String spark(float[] values) {
		int step = Math.max(1, (int) Math.ceil(values.length / 24d));
		float max = 0;
		for (float value : values)
			max = Math.max(max, value);
		StringBuilder bars = new StringBuilder();
		for (int i = 0; i < values.length; i += step) {
			int level = max <= 0 ? 0 : Math.round(values[i] / max * (BARS.length() - 1));
			bars.append(BARS.charAt(Math.clamp(level, 0, BARS.length() - 1)));
		}
		return bars.toString();
	}

	static String compact(float value) {
		if (value >= 10000)
			return String.format(Locale.ROOT, "%.1fk", value / 1000);
		if (value >= 1000)
			return String.format(Locale.ROOT, "%.2fk", value / 1000);
		return String.format(Locale.ROOT, "%.0f", value);
	}
}
