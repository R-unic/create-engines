package com.createengines.content.blackbox;

import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import com.createengines.advancement.Achievements;
import com.createengines.advancement.Milestone;
import com.createengines.content.GoggleLines;
import com.createengines.content.crankshaft.CrankshaftBlockEntity;
import com.createengines.engine.EngineState;
import com.createengines.engine.EngineSummary;
import com.createengines.engine.FlightRecorder;
import com.createengines.engine.FlightRecorder.Event;
import com.createengines.engine.FlightRecorder.Readout;
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
 * Takes a {@link FlightRecorder} sample of the engine it touches once a second and keeps the tape with the block, so it
 * is still there after a restart. What goggles show is the tape boiled down, worked out on the server every couple of
 * seconds and sent to anyone looking.
 */
public class BlackBoxBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {

	private static final int INTERVAL = 20;
	/** Samples between looks at what to send. */
	private static final int SYNC_EVERY = 2;
	private static final String BARS = "▁▂▃▄▅▆▇█";
	/** One shade for each engine state, in {@link EngineState} order: stalled, cranking, coasting, running, overloaded, and the two that mean no engine. */
	private static final String STATE_MARKS = "·░▒█▓··";

	private final FlightRecorder recorder = new FlightRecorder();
	private @Nullable Readout readout;
	private boolean engineFound;
	private boolean awarded;
	private int ticks;
	private int samples;

	public BlackBoxBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	@Override
	public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
	}

	public @Nullable Readout readout() {
		return readout;
	}

	public boolean engineFound() {
		return engineFound;
	}

	/** The tape itself, for the server to inspect. */
	public FlightRecorder recorder() {
		return recorder;
	}

	@Override
	public void tick() {
		super.tick();
		if (level == null || level.isClientSide || ++ticks % INTERVAL != 0)
			return;
		EngineSummary summary = findSummary();
		boolean found = summary != null;
		if (found)
			recorder.record(FlightRecorder.Sample.of(summary));
		boolean foundChanged = found != engineFound;
		engineFound = found;
		if (found)
			setChanged();
		if (!awarded && recorder.caughtAStall()) {
			awarded = true;
			setChanged();
			Achievements.award(level, List.of(worldPosition), Milestone.STALL_RECORDED);
		}
		if (!foundChanged && ++samples % SYNC_EVERY != 0)
			return;
		Readout next = recorder.readout();
		if (foundChanged || next.differsFrom(readout)) {
			readout = next;
			sendData();
		}
	}

	/** What the engine against any of this block's faces says about itself. */
	private @Nullable EngineSummary findSummary() {
		for (Direction face : Direction.values()) {
			Set<BlockPos> seeds = EngineScanner.crankSeeds(level, worldPosition.relative(face));
			if (seeds.isEmpty())
				continue;
			if (!(level.getBlockEntity(seeds.iterator()
				.next()) instanceof CrankshaftBlockEntity crank))
				continue;
			EngineSummary summary = crank.summary();
			BlockPos primary = crank.primaryPos();
			if (summary == null && primary != null && level.getBlockEntity(primary) instanceof CrankshaftBlockEntity main)
				summary = main.summary();
			if (summary != null)
				return summary;
		}
		return null;
	}

	@Override
	protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
		super.write(tag, registries, clientPacket);
		tag.putBoolean("Engine", engineFound);
		if (clientPacket) {
			if (readout != null) {
				CompoundTag sent = new CompoundTag();
				sent.putInt("Span", readout.span());
				sent.putIntArray("Rpm", readout.rpm());
				sent.putIntArray("Load", readout.load());
				sent.putIntArray("Warmth", readout.warmth());
				sent.putIntArray("States", readout.states());
				sent.putIntArray("Events", readout.flatEvents());
				tag.put("Readout", sent);
			}
			return;
		}
		FlightRecorder.Saved saved = recorder.save();
		CompoundTag tape = new CompoundTag();
		tape.putIntArray("Rpm", saved.rpm());
		tape.putIntArray("Load", saved.load());
		tape.putIntArray("Warmth", saved.warmth());
		tape.putIntArray("States", saved.states());
		tape.putIntArray("Changes", saved.changes());
		tag.put("Tape", tape);
		tag.putBoolean("Awarded", awarded);
	}

	@Override
	protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
		super.read(tag, registries, clientPacket);
		engineFound = tag.getBoolean("Engine");
		if (clientPacket) {
			CompoundTag sent = tag.getCompound("Readout");
			readout = tag.contains("Readout") ? Readout.of(sent.getInt("Span"), sent.getIntArray("Rpm"),
				sent.getIntArray("Load"), sent.getIntArray("Warmth"), sent.getIntArray("States"), sent.getIntArray("Events"))
				: null;
			return;
		}
		if (tag.contains("Tape")) {
			CompoundTag tape = tag.getCompound("Tape");
			recorder.restore(new FlightRecorder.Saved(tape.getIntArray("Rpm"), tape.getIntArray("Load"),
				tape.getIntArray("Warmth"), tape.getIntArray("States"), tape.getIntArray("Changes")));
			readout = recorder.readout();
		}
		awarded = tag.getBoolean("Awarded");
	}

	@Override
	public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
		CELang.translate("goggles.recorder")
			.forGoggles(tooltip);
		Readout current = readout;
		if (current == null || current.isEmpty()) {
			GoggleLines.add(CELang.translate(engineFound ? "goggles.recorder.empty" : "goggles.recorder.no_engine")
				.style(engineFound ? ChatFormatting.GRAY : ChatFormatting.RED), tooltip, 1);
			return true;
		}
		if (!engineFound)
			GoggleLines.add(CELang.translate("goggles.recorder.paused")
				.style(ChatFormatting.GOLD), tooltip, 1);
		GoggleLines.add(CELang.translate("goggles.recorder.span", duration(current.span()))
			.style(ChatFormatting.DARK_GRAY), tooltip, 1);

		int peakRpm = max(current.rpm());
		int peakLoad = max(current.load());
		GoggleLines.add(CELang.translate("goggles.recorder.speed", bars(current.rpm(), peakRpm), peakRpm)
			.style(ChatFormatting.AQUA), tooltip, 1);
		GoggleLines.add(CELang.translate("goggles.recorder.load", bars(current.load(), Math.max(100, peakLoad)), peakLoad)
			.style(peakLoad > 100 ? ChatFormatting.GOLD : ChatFormatting.GREEN), tooltip, 1);
		int warmthNow = current.warmth()[current.warmth().length - 1];
		GoggleLines.add(CELang.translate("goggles.recorder.warmth", bars(current.warmth(), Math.max(100, max(current.warmth()))),
				warmthNow)
			.style(warmthNow < 45 ? ChatFormatting.BLUE : ChatFormatting.RED), tooltip, 1);
		GoggleLines.add(CELang.translate("goggles.recorder.states", marks(current.states()))
			.style(ChatFormatting.GRAY), tooltip, 1);

		List<Event> events = current.events();
		int shown = Math.min(events.size(), isPlayerSneaking ? 8 : 3);
		for (int i = events.size() - shown; i < events.size(); i++) {
			Event event = events.get(i);
			Component from = CELang.translate("goggles.recorder.state." + event.from()
					.name()
					.toLowerCase(Locale.ROOT))
				.component();
			Component to = CELang.translate("goggles.recorder.state." + event.to()
					.name()
					.toLowerCase(Locale.ROOT))
				.component();
			boolean bad = event.to() == EngineState.STALLED || event.to() == EngineState.OVERLOADED
				|| event.to() == EngineState.BROKEN;
			if (event.cause() == FlightRecorder.Cause.NONE)
				GoggleLines.add(CELang.translate("goggles.recorder.event", duration(event.secondsAgo()), from, to)
					.style(bad ? ChatFormatting.RED : ChatFormatting.GRAY), tooltip, 1);
			else
				GoggleLines.add(CELang.translate("goggles.recorder.event_cause", duration(event.secondsAgo()), from, to,
						CELang.translate(event.cause()
								.langKey())
							.component())
					.style(bad ? ChatFormatting.RED : ChatFormatting.GRAY), tooltip, 1);
		}
		if (isPlayerSneaking)
			GoggleLines.add(CELang.translate("goggles.recorder.hint")
				.style(ChatFormatting.DARK_GRAY), tooltip, 2);
		return true;
	}

	private static int max(int[] values) {
		int best = 0;
		for (int value : values)
			best = Math.max(best, value);
		return best;
	}

	/** A row of bars as tall as each value is against {@code scale}. */
	static String bars(int[] values, int scale) {
		StringBuilder row = new StringBuilder();
		for (int value : values) {
			int level = scale <= 0 ? 0 : Math.round((float) value / scale * (BARS.length() - 1));
			row.append(BARS.charAt(Math.clamp(level, 0, BARS.length() - 1)));
		}
		return row.toString();
	}

	private static String marks(int[] states) {
		StringBuilder row = new StringBuilder();
		for (int state : states)
			row.append(STATE_MARKS.charAt(Math.clamp(state, 0, STATE_MARKS.length() - 1)));
		return row.toString();
	}

	/** Seconds as "45 s" or "3 min 20 s". */
	static String duration(int seconds) {
		if (seconds < 60)
			return seconds + " s";
		int minutes = seconds / 60;
		int rest = seconds % 60;
		return rest == 0 ? minutes + " min" : minutes + " min " + rest + " s";
	}
}
