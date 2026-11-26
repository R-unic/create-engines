package com.createengines.engine;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.createengines.engine.sim.WearTotal;

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
	/** Seconds one notch of a replay dial moves back along the tape. */
	public static final int REPLAY_STEP = 5;
	/** Notches a replay dial has past live: the whole tape in {@link #REPLAY_STEP}-second steps. */
	public static final int REPLAY_STEPS = CAPACITY / REPLAY_STEP;
	/** Columns a readout is boiled down to. */
	public static final int COLUMNS = 40;
	/** The most state changes kept; the oldest go first. */
	public static final int MAX_EVENTS = 24;

	/** What was wrong when the engine changed state, as far as the figures say. */
	public enum Cause {
		NONE, UNKNOWN, NO_FUEL, OVERLOAD, DRY,
		/** The wear mode's failures, appended so a tape saved before them still reads: a rod was thrown, a gasket blew, a piston seized, apex seals failed. */
		ROD_THROWN, GASKET_BLOWN, PISTON_SEIZED, APEX_FAILED,
		/** Appended for 2.4: heat warped a head, and a turbocharger failed. */
		HEAD_WARPED, TURBO_FAILED;

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
						 float lubrication, Diagnosis.Cause fault, float wear, long failures) {

		/** A sample that blames nothing, for the places that only have the figures. */
		public Sample(EngineState state, float rpm, float load, float capacity, float temperature, float fuel,
					  float lubrication) {
			this(state, rpm, load, capacity, temperature, fuel, lubrication, Diagnosis.Cause.NONE, -1f);
		}

		/** A sample from before the wear total: there is none. */
		public Sample(EngineState state, float rpm, float load, float capacity, float temperature, float fuel,
					  float lubrication, Diagnosis.Cause fault) {
			this(state, rpm, load, capacity, temperature, fuel, lubrication, fault, -1f);
		}

		/** A sample from before failures were counted: no reading, so it never reads as one more having failed. */
		public Sample(EngineState state, float rpm, float load, float capacity, float temperature, float fuel,
					  float lubrication, Diagnosis.Cause fault, float wear) {
			this(state, rpm, load, capacity, temperature, fuel, lubrication, fault, wear, -1);
		}

		public static Sample of(EngineSummary summary) {
			return new Sample(summary.state(), summary.rpm(), summary.loadTorque(), summary.availableTorque(),
				summary.temperature(), summary.fuelPerMinute(), summary.lubrication(), summary.cause(), summary.wear(), summary.failures());
		}
	}

	/**
	 * What the engine was doing at one second of the tape, for a replay.
	 *
	 * @param secondsAgo how long before the newest sample it was taken
	 * @param load       percent of what the engine could give that the network asked for
	 * @param warmth     percent of the 0.7 a warm engine sits at
	 * @param fault      the one thing the engine's health line blamed at the time, or NONE
	 * @param wear       the whole build's wear total at the time, in percent, or -1 when there was none (the wear mode was off)
	 */
	public record Moment(int secondsAgo, EngineState state, int rpm, int load, int warmth, Diagnosis.Cause fault, int wear) {

		/** A moment from before the wear total: there is none. */
		public Moment(int secondsAgo, EngineState state, int rpm, int load, int warmth, Diagnosis.Cause fault) {
			this(secondsAgo, state, rpm, load, warmth, fault, -1);
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
	 * @param wear     the whole build's mean wear total in each column, in percent, -1 where it was not recorded (the wear mode was off), or no
	 *                 columns at all for a readout from before the wear total
	 */
	public record Readout(int span, int[] rpm, int[] load, int[] warmth, int[] states, List<Event> events, int[] wear) {

		/** A readout from before the wear total: no wear columns. */
		public Readout(int span, int[] rpm, int[] load, int[] warmth, int[] states, List<Event> events) {
			this(span, rpm, load, warmth, states, events, new int[0]);
		}

		public static final Readout EMPTY = new Readout(0, new int[0], new int[0], new int[0], new int[0], List.of());

		/** The wear total now, in percent: the last column, or -1 when there is none. */
		public int wearNow() {
			return wear.length == 0 ? -1 : wear[wear.length - 1];
		}

		/** Whether any column has a wear total, which is when a wear line is worth showing. */
		public boolean hasWear() {
			for (int w : wear)
				if (w >= 0)
					return true;
			return false;
		}

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
			// A column with no wear total and no column at all are the same thing: a readout from before the total is not a changed one
			for (int i = 0; i < Math.max(wear.length, other.wear.length); i++)
				if ((i < wear.length ? wear[i] : -1) != (i < other.wear.length ? other.wear[i] : -1))
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
			return of(span, rpm, load, warmth, states, flat, new int[0]);
		}

		/** As above, with the wear columns the readout was sent with. */
		public static Readout of(int span, int[] rpm, int[] load, int[] warmth, int[] states, int[] flat, int[] wear) {
			List<Event> events = new ArrayList<>();
			EngineState[] values = EngineState.values();
			Cause[] causes = Cause.values();
			for (int i = 0; i + 3 < flat.length; i += 4)
				events.add(new Event(flat[i], values[Math.clamp(flat[i + 1], 0, values.length - 1)],
					values[Math.clamp(flat[i + 2], 0, values.length - 1)], causes[Math.clamp(flat[i + 3], 0, causes.length - 1)]));
			return new Readout(span, rpm, load, warmth, states, events, wear);
		}
	}

	private record Change(long at, EngineState from, EngineState to, Cause cause) {
	}

	private final float[] rpm = new float[CAPACITY];
	private final float[] load = new float[CAPACITY];
	private final float[] warmth = new float[CAPACITY];
	private final byte[] states = new byte[CAPACITY];
	private final byte[] faults = new byte[CAPACITY];
	/** The wear total at each sample, in percent, or -1 where there was none. */
	private final byte[] wear = new byte[CAPACITY];
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
		// A part that has failed since the last look is an event of its own, at the state the engine was in: the Black Box says what broke and when
		if (previous != null)
			for (com.createengines.engine.sim.WearFailures.Kind kind : com.createengines.engine.sim.WearFailures.Kind.values())
				if (com.createengines.engine.sim.WearFailures.rose(previous.failures, sample.failures, kind)) {
					changes.addLast(new Change(count, sample.state, sample.state, Cause.values()[Cause.ROD_THROWN.ordinal() + kind.ordinal()]));
					while (changes.size() > MAX_EVENTS)
						changes.removeFirst();
				}
		int slot = (int) (count % CAPACITY);
		rpm[slot] = sample.rpm;
		load[slot] = sample.capacity > 0 ? sample.load / sample.capacity : 0;
		warmth[slot] = sample.temperature;
		states[slot] = (byte) sample.state.ordinal();
		faults[slot] = (byte) sample.fault.ordinal();
		wear[slot] = (byte) WearTotal.percent(sample.wear);
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

	/** Samples taken since the recorder began: the number the next sample will have. A moment is named by its sample number. */
	public long total() {
		return count;
	}

	/** The number of the oldest sample still held. */
	public long oldest() {
		return count - size();
	}

	/** The number of the sample taken this many seconds before the newest, kept to what is held. */
	public long sampleBack(int secondsBack) {
		return Math.max(oldest(), count - 1 - Math.max(0, secondsBack));
	}

	/**
	 * What the engine was doing when the {@code sampleNumber}th sample was taken, or null when nothing is held. A number
	 * past the newest reads the newest and one older than the tape reads the oldest, so a replay pinned to a moment that has
	 * since fallen off the tape holds at the start of it.
	 */
	public @Nullable Moment momentAt(long sampleNumber) {
		int size = size();
		if (size == 0)
			return null;
		long number = Math.clamp(sampleNumber, count - size, count - 1);
		int slot = slot(number);
		EngineState[] values = EngineState.values();
		Diagnosis.Cause[] causes = Diagnosis.Cause.values();
		return new Moment((int) (count - 1 - number), values[Math.clamp(states[slot], 0, values.length - 1)],
			Math.round(rpm[slot]), Math.round(load[slot] * 100), Math.round(warmth[slot] / 0.7f * 100),
			causes[Math.clamp(faults[slot], 0, causes.length - 1)], wear[slot]);
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
		int[] outWear = new int[columns];
		for (int column = 0; column < columns; column++) {
			long from = first + (long) column * size / columns;
			long to = first + (long) (column + 1) * size / columns;
			double rpmSum = 0;
			double loadSum = 0;
			double warmthSum = 0;
			double wearSum = 0;
			int wearSamples = 0;
			for (long n = from; n < to; n++) {
				int slot = slot(n);
				rpmSum += rpm[slot];
				loadSum += load[slot];
				warmthSum += warmth[slot];
				if (wear[slot] >= 0) {
					wearSum += wear[slot];
					wearSamples++;
				}
			}
			int samples = (int) Math.max(1, to - from);
			outRpm[column] = Math.round((float) (rpmSum / samples));
			outLoad[column] = Math.round((float) (loadSum / samples * 100));
			outWarmth[column] = Math.round((float) (warmthSum / samples / 0.7 * 100));
			outStates[column] = states[slot(to - 1)];
			outWear[column] = wearSamples == 0 ? -1 : Math.round((float) (wearSum / wearSamples));
		}
		List<Event> events = new ArrayList<>();
		for (Change change : changes)
			if (change.at >= first)
				events.add(new Event((int) (count - change.at), change.from, change.to, change.cause));
		return new Readout((int) Math.max(1, size), outRpm, outLoad, outWarmth, outStates, events, outWear);
	}

	// Saving

	/** Everything the recorder holds, as plain arrays: samples oldest first, and the changes as four numbers each. */
	public record Saved(int[] rpm, int[] load, int[] warmth, int[] states, int[] changes, int[] faults, int[] wear) {

		/** A tape saved before faults were kept: it blames nothing. */
		public Saved(int[] rpm, int[] load, int[] warmth, int[] states, int[] changes) {
			this(rpm, load, warmth, states, changes, new int[0], new int[0]);
		}

		/** A tape saved before the wear total was kept: it has none. */
		public Saved(int[] rpm, int[] load, int[] warmth, int[] states, int[] changes, int[] faults) {
			this(rpm, load, warmth, states, changes, faults, new int[0]);
		}
	}

	public Saved save() {
		int size = size();
		int[] savedRpm = new int[size];
		int[] savedLoad = new int[size];
		int[] savedWarmth = new int[size];
		int[] savedStates = new int[size];
		int[] savedFaults = new int[size];
		int[] savedWear = new int[size];
		long first = count - size;
		for (int i = 0; i < size; i++) {
			int slot = slot(first + i);
			savedRpm[i] = Math.round(rpm[slot] * 10);
			savedLoad[i] = Math.round(load[slot] * 1000);
			savedWarmth[i] = Math.round(warmth[slot] * 1000);
			savedStates[i] = states[slot];
			savedFaults[i] = faults[slot];
			savedWear[i] = wear[slot];
		}
		int[] flat = new int[changes.size() * 4];
		int i = 0;
		for (Change change : changes) {
			flat[i++] = (int) (count - change.at);
			flat[i++] = change.from.ordinal();
			flat[i++] = change.to.ordinal();
			flat[i++] = change.cause.ordinal();
		}
		return new Saved(savedRpm, savedLoad, savedWarmth, savedStates, flat, savedFaults, savedWear);
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
			faults[i] = (byte) (i < saved.faults.length ? Math.clamp(saved.faults[i], 0, Diagnosis.Cause.values().length - 1) : 0);
			// A tape from before the wear total, or a damaged one, has none; a saved figure is kept to 0 to 100
			wear[i] = (byte) (i < saved.wear.length && saved.wear[i] >= 0 ? Math.min(100, saved.wear[i]) : -1);
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
