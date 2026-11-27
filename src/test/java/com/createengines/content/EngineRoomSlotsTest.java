package com.createengines.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

/**
 * Which engines the shared voice plays: the nearest of those without a note of their own, each keeping its slot while it stays
 * among them and faded out before the slot is given away. And the whole scene (notes, slots, fill and ceiling together) stays
 * under the ceiling however the engines are spread.
 */
class EngineRoomSlotsTest {

	/** An engine in the scene, as far as the slots care. */
	private static final class Engine {
		final float level;

		Engine(float level) {
			this.level = level;
		}
	}

	private static List<Engine> engines(int count) {
		List<Engine> engines = new ArrayList<>();
		for (int i = 0; i < count; i++)
			engines.add(new Engine(1));
		return engines;
	}

	@Test
	void theNearestWaitingEnginesGetTheSlots() {
		EngineRoomSlots<Engine> slots = new EngineRoomSlots<>();
		List<Engine> waiting = engines(5);
		slots.choose(waiting);
		assertEquals(EngineRoomMix.SLOTS, slots.occupied());
		for (int i = 0; i < 5; i++)
			assertEquals(i < EngineRoomMix.SLOTS, slots.slotOf(waiting.get(i)) >= 0, "engine " + i);
	}

	@Test
	void anEngineKeepsItsSlotWhenTheNearestChangePlaces() {
		EngineRoomSlots<Engine> slots = new EngineRoomSlots<>();
		List<Engine> waiting = engines(3);
		slots.choose(waiting);
		int[] before = {slots.slotOf(waiting.get(0)), slots.slotOf(waiting.get(1)), slots.slotOf(waiting.get(2))};
		List<Engine> reordered = List.of(waiting.get(2), waiting.get(0), waiting.get(1));
		slots.choose(reordered);
		for (int i = 0; i < 3; i++) {
			assertEquals(before[i], slots.slotOf(waiting.get(i)), "Moving slot would restart the engine's sound");
			assertFalse(slots.isFading(before[i]));
		}
	}

	@Test
	void anEnginePushedOutIsFadedBeforeItsSlotIsGivenAway() {
		EngineRoomSlots<Engine> slots = new EngineRoomSlots<>();
		List<Engine> waiting = engines(3);
		slots.choose(waiting);
		Engine pushed = waiting.get(2);
		int slot = slots.slotOf(pushed);
		Engine nearer = new Engine(1);
		List<Engine> now = List.of(nearer, waiting.get(0), waiting.get(1), pushed);
		for (int tick = 1; tick < EngineRoomSlots.FADE_TICKS; tick++) {
			slots.choose(now);
			assertTrue(slots.isFading(slot));
			assertFalse(slots.fadeTick(slot), "Freed too soon, on tick " + tick);
			assertSame(pushed, slots.get(slot));
			assertEquals(-1, slots.slotOf(nearer));
		}
		slots.choose(now);
		assertTrue(slots.fadeTick(slot), "It should be gone after " + EngineRoomSlots.FADE_TICKS + " ticks");
		assertNull(slots.get(slot));
		slots.choose(now);
		assertSame(nearer, slots.get(slot));
		assertFalse(slots.isFading(slot));
	}

	@Test
	void anEngineThatComesBackWhileFadingStays() {
		EngineRoomSlots<Engine> slots = new EngineRoomSlots<>();
		List<Engine> waiting = engines(3);
		slots.choose(waiting);
		int slot = slots.slotOf(waiting.get(1));
		slots.choose(List.of(waiting.get(0), waiting.get(2)));
		assertTrue(slots.isFading(slot));
		slots.fadeTick(slot);
		slots.choose(waiting);
		assertFalse(slots.isFading(slot));
		for (int tick = 0; tick < 10; tick++)
			assertFalse(slots.fadeTick(slot), "A slot that is not fading is never freed");
		assertSame(waiting.get(1), slots.get(slot));
		// Pushed out again, it gets the whole fade, not what was left of the last one
		slots.choose(List.of(waiting.get(0), waiting.get(2)));
		for (int tick = 1; tick < EngineRoomSlots.FADE_TICKS; tick++)
			assertFalse(slots.fadeTick(slot));
		assertTrue(slots.fadeTick(slot));
	}

	@Test
	void clearingEmptiesEverySlot() {
		EngineRoomSlots<Engine> slots = new EngineRoomSlots<>();
		slots.choose(engines(3));
		slots.choose(List.of());
		slots.clear();
		assertEquals(0, slots.occupied());
		for (int s = 0; s < EngineRoomMix.SLOTS; s++)
			assertFalse(slots.isFading(s));
	}

	@Test
	void theFillCountsTheEnginesNoSlotPlaysByHowLoudTheyAre() {
		EngineRoomSlots<Engine> slots = new EngineRoomSlots<>();
		List<Engine> waiting = engines(6);
		slots.choose(waiting);
		assertEquals(Math.sqrt(2), slots.fill(waiting, engine -> engine.level), 1e-5, "As many again as are played");
		assertEquals(1, slots.fill(waiting.subList(0, 3), engine -> engine.level), 1e-6, "Everything waiting is played");
		assertTrue(slots.fill(waiting, engine -> waiting.indexOf(engine) < 3 ? 1 : 0.01) < 1.001, "Faint ones add next to nothing");
	}

	/**
	 * The promise of the whole scene, put together as the client puts it: the engines with notes of their own at the ceiling,
	 * the slotted ones at the ceiling and the fill. However many there are and however loud each is, including loud ones left
	 * waiting while faint ones hold the notes, all together stay within the ceiling over the loudest alone.
	 */
	@Test
	void noSpreadOfEnginesIsLouderThanTheCeiling() {
		Random random = new Random(17);
		for (int round = 0; round < 4000; round++) {
			int count = 1 + random.nextInt(40);
			List<Engine> all = new ArrayList<>();
			for (int i = 0; i < count; i++) {
				// Half the rounds are lumpy: a few loud ones among many barely audible
				float level = round % 2 == 0 ? random.nextFloat() : random.nextInt(6) == 0 ? 1 : 0.02f;
				all.add(new Engine(Math.max(0.001f, level)));
			}
			float[] levels = new float[count];
			float loudest = 0;
			for (int i = 0; i < count; i++) {
				levels[i] = all.get(i).level;
				loudest = Math.max(loudest, levels[i]);
			}
			float ceiling = EngineChorus.ceiling(levels, count);
			int notes = Math.min(EngineChorus.NOTE_VOICES, count);
			List<Engine> waiting = all.subList(notes, count);
			EngineRoomSlots<Engine> slots = new EngineRoomSlots<>();
			slots.choose(waiting);
			float fill = slots.fill(waiting, engine -> engine.level);
			double power = 0;
			for (Engine engine : all.subList(0, notes))
				power += Math.pow(engine.level * ceiling, 2);
			for (int s = 0; s < EngineRoomMix.SLOTS; s++)
				if (slots.get(s) != null)
					power += Math.pow(slots.get(s).level * ceiling * fill, 2);
			assertTrue(Math.sqrt(power) <= EngineChorus.CEILING * loudest * 1.0001,
				"Round " + round + ", " + count + " engines: " + Math.sqrt(power) / loudest + " times the loudest");
		}
	}
}
