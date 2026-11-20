package com.createengines.content.blackbox;

import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import com.createengines.engine.LoadedBlocks;
import com.createengines.advancement.Achievements;
import com.createengines.advancement.Milestone;
import com.createengines.content.GoggleLines;
import com.createengines.content.crankshaft.CrankshaftBlockEntity;
import com.createengines.engine.Diagnosis;
import com.createengines.engine.EngineState;
import com.createengines.engine.EngineSummary;
import com.createengines.engine.FlightRecorder;
import com.createengines.engine.FlightRecorder.Event;
import com.createengines.engine.FlightRecorder.Readout;
import com.createengines.engine.topology.EngineScanner;
import com.createengines.registry.CELang;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;

import net.createmod.catnip.math.VecHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

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

	private static final BehaviourType<ScrollValueBehaviour> REPLAY_TYPE = new BehaviourType<>("black_box_replay");

	private final FlightRecorder recorder = new FlightRecorder();
	private @Nullable Readout readout;
	private boolean engineFound;
	private boolean awarded;
	private int ticks;
	private int samples;
	/** The dial that moves the replay back along the tape: 0 is live, each notch after it is {@link FlightRecorder#REPLAY_STEP} seconds further back. */
	public ScrollValueBehaviour replay;
	/**
	 * The sample a replay is held on, or -1 while it is live or has not been pinned yet. Pinned when the dial is turned, so
	 * the figures stay on one moment as the tape runs on, and the "seconds ago" grows. Not saved: a loaded block pins its
	 * dial's distance back from the newest sample again.
	 */
	private long pinned = -1;
	/** What the replay shows, worked out on the server and sent; null while live. */
	private @Nullable FlightRecorder.Moment moment;

	public BlackBoxBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	@Override
	public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
		replay = new ScrollValueBehaviour(CELang.translate("value.replay")
			.component(), this, new ReplayBox()) {
			@Override
			public BehaviourType<?> getType() {
				return REPLAY_TYPE;
			}
		};
		replay.between(0, FlightRecorder.REPLAY_STEPS);
		replay.value = 0;
		replay.withFormatter(v -> v == 0 ? CELang.translate("value.replay.live")
			.string() : CELang.translate("value.replay.back", duration(v * FlightRecorder.REPLAY_STEP))
			.string());
		replay.withCallback(value -> {
			pinned = -1;
			if (level != null && !level.isClientSide)
				refreshMoment();
		});
		behaviours.add(replay);
	}

	/** How far back the dial is set, in seconds; 0 while live. */
	public int replaySeconds() {
		return replay == null ? 0 : Math.clamp(replay.getValue(), 0, FlightRecorder.REPLAY_STEPS) * FlightRecorder.REPLAY_STEP;
	}

	/** What the replay is showing, or null while the dial is on live (or nothing has been recorded). */
	public FlightRecorder.@Nullable Moment moment() {
		return moment;
	}

	/** Pins the replay on its moment if it needs it and works out what it shows; the dial on live shows nothing. */
	private void refreshMoment() {
		int back = replaySeconds();
		if (back == 0) {
			pinned = -1;
			moment = null;
			return;
		}
		if (pinned < 0 && recorder.size() > 0)
			pinned = recorder.sampleBack(back);
		moment = recorder.momentAt(pinned);
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
		FlightRecorder.Moment before = moment;
		refreshMoment();
		boolean momentChanged = !java.util.Objects.equals(before, moment);
		if (!foundChanged && !momentChanged && ++samples % SYNC_EVERY != 0)
			return;
		Readout next = recorder.readout();
		if (foundChanged || momentChanged || next.differsFrom(readout)) {
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
			if (!(LoadedBlocks.blockEntity(level, seeds.iterator()
				.next()) instanceof CrankshaftBlockEntity crank))
				continue;
			EngineSummary summary = crank.summary();
			BlockPos primary = crank.primaryPos();
			if (summary == null && primary != null && LoadedBlocks.blockEntity(level, primary) instanceof CrankshaftBlockEntity main)
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
			FlightRecorder.Moment shown = moment;
			if (shown != null) {
				CompoundTag sent = new CompoundTag();
				sent.putInt("Ago", shown.secondsAgo());
				sent.putInt("State", shown.state()
					.ordinal());
				sent.putInt("Rpm", shown.rpm());
				sent.putInt("Load", shown.load());
				sent.putInt("Warmth", shown.warmth());
				sent.putInt("Fault", shown.fault()
					.ordinal());
				tag.put("Moment", sent);
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
		tape.putIntArray("Faults", saved.faults());
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
			moment = tag.contains("Moment") ? momentOf(tag.getCompound("Moment")) : null;
			return;
		}
		if (tag.contains("Tape")) {
			CompoundTag tape = tag.getCompound("Tape");
			recorder.restore(new FlightRecorder.Saved(tape.getIntArray("Rpm"), tape.getIntArray("Load"),
				tape.getIntArray("Warmth"), tape.getIntArray("States"), tape.getIntArray("Changes"),
				tape.getIntArray("Faults")));
			readout = recorder.readout();
			// A loaded block pins its replay again from the dial the first time it ticks
			pinned = -1;
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
		replayLines(tooltip, isPlayerSneaking);

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

	/** The replay: what the engine was doing at the moment the dial is on, and what the health line blamed then. */
	private void replayLines(List<Component> tooltip, boolean sneaking) {
		FlightRecorder.Moment shown = moment;
		if (replaySeconds() == 0 || shown == null) {
			if (sneaking)
				GoggleLines.add(CELang.translate("goggles.recorder.replay.hint")
					.style(ChatFormatting.DARK_GRAY), tooltip, 2);
			return;
		}
		GoggleLines.add(CELang.translate("goggles.recorder.replay", duration(shown.secondsAgo()))
			.style(ChatFormatting.LIGHT_PURPLE), tooltip, 1);
		boolean bad = shown.state() == EngineState.STALLED || shown.state() == EngineState.OVERLOADED
			|| shown.state() == EngineState.BROKEN;
		GoggleLines.add(CELang.translate("goggles.recorder.replay.figures", CELang.translate("goggles.recorder.state." + shown.state()
					.name()
					.toLowerCase(Locale.ROOT))
				.component(), shown.rpm(), shown.load(), shown.warmth())
			.style(bad ? ChatFormatting.RED : ChatFormatting.GRAY), tooltip, 2);
		GoggleLines.add(CELang.translate("goggles.recorder.replay.fault", CELang.translate(shown.fault()
					.displayKey())
				.component())
			.style(shown.fault() == Diagnosis.Cause.NONE ? ChatFormatting.GREEN : ChatFormatting.GOLD), tooltip, 2);
	}

	private static FlightRecorder.Moment momentOf(CompoundTag tag) {
		EngineState[] states = EngineState.values();
		Diagnosis.Cause[] causes = Diagnosis.Cause.values();
		return new FlightRecorder.Moment(tag.getInt("Ago"), states[Math.clamp(tag.getInt("State"), 0, states.length - 1)],
			tag.getInt("Rpm"), tag.getInt("Load"), tag.getInt("Warmth"),
			causes[Math.clamp(tag.getInt("Fault"), 0, causes.length - 1)]);
	}

	/** A value box on the side faces of the recorder's case. */
	private static final class ReplayBox extends ValueBoxTransform.Sided {

		@Override
		protected Vec3 getSouthLocation() {
			return VecHelper.voxelSpace(8, 5, 15);
		}

		@Override
		protected boolean isSideActive(BlockState state, Direction direction) {
			return direction.getAxis()
				.isHorizontal();
		}
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
