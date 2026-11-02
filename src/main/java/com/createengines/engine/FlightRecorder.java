package com.createengines.engine;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import org.jetbrains.annotations.Nullable;

/**
 * The last ten minutes of an engine's life, a sample a second, and the moments its state changed with the reason the
 * figures give for each. Pure bookkeeping: whatever owns the recorder feeds it one {@link Sample} a second and asks for a
 * {@link Readout} when somebody looks.
 * <p>
 * It exists so that "why did it stall at three in the morning" and "why is it still cold" have an answer on hand. The
 * simulation knows; this keeps what it knew.
 */
public final class FlightRecorder {

	/** Samples kept: ten minutes at one a second. */
	public static final int CAPACITY = 600;
	/** Columns a readout is boiled down to. */
	public static final int COLUMNS = 40;
	/** The most state changes kept; the oldest go first. */
	public static final int MAX_EVENTS = 24;

	/** What was wrong when the engine changed state, as far as the figures say. */
	public enum Cause {
		NONE, UNKNOWN, NO_FUEL, OVERLOAD, DRY;

		public String langKey() {
			return "goggles.recorder.cause." + name().toLowerCase(java.util.Locale.ROOT);
		}
	}

	/**
	 * One look at the engine.
	 *
	 * @param load        stress capacity per RPM the network asked for
	 * @param capacity    stress capacity per RPM the engine could give
	 * @param temperature 0 ambient, 0.7 a warm engine on a steady load
	 * @param fuel        mB a minute being injected
	 * @param lubrication how well oiled it is, 0 to 1
	 */
	public record Sample(EngineState state, float rpm, float load, float capacity, float temperature, float fuel,
						 float lubrication) {

		public static Sample of(EngineSummary summary) {
			return new Sample(summary.state(), summary.rpm(), summary.loadTorque(), summary.availableTorque(),
				summary.temperature(), summary.fuelPerMinute(), summary.lubrication());
		}
	}

	/**
	 * A change of state.
	 *
	 * @param secondsAgo how long before the recording was read it happened
	 */
	public record Event(int secondsAgo, EngineState from, EngineState to, Cause cause) {
	}

	/**
	 * What a recorder shows: its samples boiled down to {@link #COLUMNS} columns, oldest first, and the state changes.
	 *
	 * @param span     seconds the columns cover
	 * @param rpm      mean speed in each column
	 * @param load     mean load as a share of what the engine could give, in percent
	 * @param warmth   mean temperature in each column, in percent of the 0.7 a warm engine sits at
	 * @param states   the state the engine ended each column in, by {@link EngineState#ordinal()}
	 * @param events   state changes, oldest first
	 */
	public record Readout(int span, int[] rpm, int[] load, int[] warmth, int[] states, List<Event> events) {

		public static final Readout EMPTY = new Readout(0, new int[0], new int[0], new int[0], new int[0], List.of());

		public boolean isEmpty() {
			return rpm.length == 0;
		}

		public boolean differsFrom(@Nullable Readout other) {
			if (other == null || span != other.span || rpm.length != other.rpm.length
				|| events.size() != other.events.size())
				return true;
			for (int i = 0; i < rpm.length; i++)
				if (Math.abs(rpm[i] - other.rpm[i]) > Math.max(1, other.rpm[i] / 50)
					|| Math.abs(load[i] - other.load[i]) > 2 || Math.abs(warmth[i] - other.warmth[i]) > 1
					|| states[i] != other.states[i])
					return true;
			return false;
		}

		/** The events as one array of four numbers each, for sending. */
		public int[] flatEvents() {
			int[] flat = new int[events.size() * 4];
			for (int i = 0; i < events.size(); i++) {
				Event event = events.get(i);
				flat[i * 4] = event.secondsAgo;
				flat[i * 4 + 1] = event.from.ordinal();
				flat[i * 4 + 2] = event.to.ordinal();
				flat[i * 4 + 3] = event.cause.ordinal();
			}
			return flat;
		}

		/** A readout from the numbers {@link #flatEvents()} and the columns were sent as. */
		public static Readout of(int span, int[] rpm, int[] load, int[] warmth, int[] states, int[] flat) {
			List<Event> events = new ArrayList<>();
			EngineState[] values = EngineState.values();
			Cause[] causes = Cause.values();
			for (int i = 0; i + 3 < flat.length; i += 4)
				events.add(new Event(flat[i], values[Math.clamp(flat[i + 1], 0, values.length - 1)],
					values[Math.clamp(flat[i + 2], 0, values.length - 1)], causes[Math.clamp(flat[i + 3], 0, causes.length - 1)]));
			return new Readout(span, rpm, load, warmth, states, events);
		}
	}

	private record Change(long at, EngineState from, EngineState to, Cause cause) {
	}

	private final float[] rpm = new float[CAPACITY];
	private final float[] load = new float[CAPACITY];
	private final float[] warmth = new float[CAPACITY];
	private final byte[] states = new byte[CAPACITY];
	/** Samples taken since the recorder began. */
	private long count;
	private final Deque<Change> changes = new ArrayDeque<>();
	private @Nullable Sample last;

	/** How many samples are held. */
	public int size() {
		return (int) Math.min(count, CAPACITY);
	}

	/** Takes one look at the engine; call it once a second. */
	public void record(Sample sample) {
		Sample previous = last;
		if (previous != null && previous.state != sample.state) {
			changes.addLast(new Change(count, previous.state, sample.state, cause(previous, sample)));
			while (changes.size() > MAX_EVENTS)
				changes.removeFirst();
		}
		int slot = (int) (count % CAPACITY);
		rpm[slot] = sample.rpm;
		load[slot] = sample.capacity > 0 ? sample.load / sample.capacity : 0;
		warmth[slot] = sample.temperature;
		states[slot] = (byte) sample.state.ordinal();
		count++;
		last = sample;
	}

	/** What the figures before a change say about it: a fuel line that ran dry, a load too heavy, an engine without oil. */
	static Cause cause(Sample before, Sample after) {
		switch (after.state) {
			case STALLED:
				if (before.fuel <= 0.01f)
					return Cause.NO_FUEL;
				if (before.lubrication < 0.05f)
					return Cause.DRY;
				if (before.load > before.capacity)
					return Cause.OVERLOAD;
				return Cause.UNKNOWN;
			case COASTING:
				return before.fuel <= 0.01f ? Cause.NO_FUEL : Cause.UNKNOWN;
			case OVERLOADED:
				return Cause.OVERLOAD;
			default:
				return Cause.NONE;
		}
	}

	/** Whether the recorder has seen an engine that was running stall. */
	public boolean caughtAStall() {
		for (Change change : changes)
			if (change.to == EngineState.STALLED && (change.from == EngineState.RUNNING
				|| change.from == EngineState.OVERLOADED || change.from == EngineState.COASTING))
				return true;
		return false;
	}

	/** Where in the arrays the {@code sampleNumber}th sample since the recorder began is kept. */
	private int slot(long sampleNumber) {
		return (int) (sampleNumber % CAPACITY);
	}

	public Readout readout() {
		int size = size();
		if (size == 0)
			return Readout.EMPTY;
		long first = count - size;
		int columns = Math.min(COLUMNS, size);
		int[] outRpm = new int[columns];
		int[] outLoad = new int[columns];
		int[] outWarmth = new int[columns];
		int[] outStates = new int[columns];
		for (int column = 0; column < columns; column++) {
			long from = first + (long) column * size / columns;
			long to = first + (long) (column + 1) * size / columns;
			double rpmSum = 0;
			double loadSum = 0;
			double warmthSum = 0;
			for (long n = from; n < to; n++) {
				int slot = slot(n);
				rpmSum += rpm[slot];
				loadSum += load[slot];
				warmthSum += warmth[slot];
			}
			int samples = (int) Math.max(1, to - from);
			outRpm[column] = Math.round((float) (rpmSum / samples));
			outLoad[column] = Math.round((float) (loadSum / samples * 100));
			outWarmth[column] = Math.round((float) (warmthSum / samples / 0.7 * 100));
			outStates[column] = states[slot(to - 1)];
		}
		List<Event> events = new ArrayList<>();
		for (Change change : changes)
			if (change.at >= first)
				events.add(new Event((int) (count - change.at), change.from, change.to, change.cause));
		return new Readout((int) Math.max(1, size), outRpm, outLoad, outWarmth, outStates, events);
	}

	// Saving

	/** Everything the recorder holds, as plain arrays: samples oldest first, and the changes as four numbers each. */
	public record Saved(int[] rpm, int[] load, int[] warmth, int[] states, int[] changes) {
	}

	public Saved save() {
		int size = size();
		int[] savedRpm = new int[size];
		int[] savedLoad = new int[size];
		int[] savedWarmth = new int[size];
		int[] savedStates = new int[size];
		long first = count - size;
		for (int i = 0; i < size; i++) {
			int slot = slot(first + i);
			savedRpm[i] = Math.round(rpm[slot] * 10);
			savedLoad[i] = Math.round(load[slot] * 1000);
			savedWarmth[i] = Math.round(warmth[slot] * 1000);
			savedStates[i] = states[slot];
		}
		int[] flat = new int[changes.size() * 4];
		int i = 0;
		for (Change change : changes) {
			flat[i++] = (int) (count - change.at);
			flat[i++] = change.from.ordinal();
			flat[i++] = change.to.ordinal();
			flat[i++] = change.cause.ordinal();
		}
		return new Saved(savedRpm, savedLoad, savedWarmth, savedStates, flat);
	}

	public void restore(Saved saved) {
		count = 0;
		changes.clear();
		last = null;
		int size = Math.min(Math.min(saved.rpm.length, saved.load.length), Math.min(saved.warmth.length, saved.states.length));
		size = Math.min(size, CAPACITY);
		EngineState[] values = EngineState.values();
		for (int i = 0; i < size; i++) {
			rpm[i] = saved.rpm[i] / 10f;
			load[i] = saved.load[i] / 1000f;
			warmth[i] = saved.warmth[i] / 1000f;
			states[i] = (byte) Math.clamp(saved.states[i], 0, values.length - 1);
		}
		count = size;
		Cause[] causes = Cause.values();
		for (int i = 0; i + 3 < saved.changes.length; i += 4) {
			long at = count - saved.changes[i];
			if (at < 0)
				continue;
			changes.addLast(new Change(at, values[Math.clamp(saved.changes[i + 1], 0, values.length - 1)],
				values[Math.clamp(saved.changes[i + 2], 0, values.length - 1)],
				causes[Math.clamp(saved.changes[i + 3], 0, causes.length - 1)]));
		}
		if (size > 0)
			last = new Sample(values[states[size - 1]], rpm[size - 1], 0, 0, warmth[size - 1], 0, 1);
	}
}
