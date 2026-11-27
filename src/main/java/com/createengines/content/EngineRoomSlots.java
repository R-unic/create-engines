package com.createengines.content;

import java.util.Arrays;
import java.util.List;
import java.util.function.ToDoubleFunction;

/**
 * Which engines are in the shared "engine room" voice's slots ({@link EngineRoomMix}): the nearest of those without a note of
 * their own. An engine keeps its slot while it is among them, and one that no longer is gets faded out for a few ticks before
 * the slot is given to another, so none stops with a click. Pure Java, so it is tested.
 */
final class EngineRoomSlots<T> {

	/** Ticks a slot's engine is faded out for before the slot is freed. */
	static final int FADE_TICKS = 3;

	private final Object[] slots = new Object[EngineRoomMix.SLOTS];
	private final int[] fading = new int[EngineRoomMix.SLOTS];

	/** Keeps engines already in a slot while among the nearest {@code waiting}, and gives free slots to the next nearest. */
	void choose(List<T> waiting) {
		int take = Math.min(slots.length, waiting.size());
		for (int s = 0; s < slots.length; s++) {
			if (slots[s] == null)
				continue;
			int place = waiting.indexOf(slots[s]);
			if (place < 0 || place >= take) {
				if (fading[s] == 0)
					fading[s] = FADE_TICKS;
			} else
				fading[s] = 0;
		}
		for (int i = 0; i < take; i++) {
			T candidate = waiting.get(i);
			if (slotOf(candidate) >= 0)
				continue;
			for (int s = 0; s < slots.length; s++)
				if (slots[s] == null) {
					slots[s] = candidate;
					fading[s] = 0;
					break;
				}
		}
	}

	@SuppressWarnings("unchecked")
	T get(int slot) {
		return (T) slots[slot];
	}

	int slotOf(T engine) {
		for (int s = 0; s < slots.length; s++)
			if (slots[s] == engine)
				return s;
		return -1;
	}

	int occupied() {
		int count = 0;
		for (Object engine : slots)
			if (engine != null)
				count++;
		return count;
	}

	/** Whether the slot's engine is on its way out, to be played at nothing. */
	boolean isFading(int slot) {
		return fading[slot] > 0;
	}

	/** One tick of a slot that is fading; frees it and returns true once it has faded for long enough. */
	boolean fadeTick(int slot) {
		if (fading[slot] == 0 || --fading[slot] > 0)
			return false;
		free(slot);
		return true;
	}

	void free(int slot) {
		slots[slot] = null;
		fading[slot] = 0;
	}

	void clear() {
		Arrays.fill(slots, null);
		Arrays.fill(fading, 0);
	}

	/** How much fuller the shared voice is for the waiting engines it is not playing ({@link EngineChorus#fill}). */
	float fill(List<T> waiting, ToDoubleFunction<T> level) {
		double rendered = 0;
		for (int s = 0; s < slots.length; s++)
			if (slots[s] != null && fading[s] == 0) {
				double heard = level.applyAsDouble(get(s));
				rendered += heard * heard;
			}
		double counted = 0;
		for (T engine : waiting) {
			int slot = slotOf(engine);
			if (slot >= 0 && fading[slot] == 0)
				continue;
			double heard = level.applyAsDouble(engine);
			counted += heard * heard;
		}
		return EngineChorus.fill(rendered, counted);
	}
}
