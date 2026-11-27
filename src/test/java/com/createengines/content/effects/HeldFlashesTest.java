package com.createengines.content.effects;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.createengines.content.NoteCue;

/** A backfire's flame waits for its bang. */
class HeldFlashesTest {

	private static final long MS = 1_000_000L;

	/** The cue is written by the note's sound thread, which this package has no way into but this. */
	private static void heardAt(NoteCue cue, long nanos) throws ReflectiveOperationException {
		Method said = NoteCue.class.getDeclaredMethod("heardAt", long.class);
		said.setAccessible(true);
		said.invoke(cue, nanos);
	}

	private static NoteCue cue() throws ReflectiveOperationException {
		Constructor<NoteCue> made = NoteCue.class.getDeclaredConstructor();
		made.setAccessible(true);
		return made.newInstance();
	}

	@Test
	void aFlashIsShownAsItsBangIsHeard() throws ReflectiveOperationException {
		HeldFlashes held = new HeldFlashes();
		AtomicInteger shown = new AtomicInteger();
		NoteCue cue = cue();
		held.add(cue, 0, shown::incrementAndGet);
		held.tick(50 * MS);
		assertEquals(0, shown.get(), "not before the note has said when");
		heardAt(cue, 250 * MS);
		held.tick(200 * MS);
		assertEquals(0, shown.get(), "not before the bang");
		held.tick(250 * MS - HeldFlashes.EARLY);
		assertEquals(1, shown.get());
		held.tick(300 * MS);
		assertEquals(1, shown.get(), "once");
		assertEquals(0, held.size());
	}

	@Test
	void aFlashWhoseNoteWasLostIsStillShown() throws ReflectiveOperationException {
		HeldFlashes held = new HeldFlashes();
		AtomicInteger shown = new AtomicInteger();
		held.add(cue(), 0, shown::incrementAndGet);
		held.tick(HeldFlashes.LONGEST - MS);
		assertEquals(0, shown.get());
		held.tick(HeldFlashes.LONGEST);
		assertEquals(1, shown.get());
	}

	@Test
	void theWaitingNeverPileUpAndAreForgottenWithTheWorld() throws ReflectiveOperationException {
		HeldFlashes held = new HeldFlashes();
		AtomicInteger shown = new AtomicInteger();
		for (int i = 0; i < HeldFlashes.MOST + 5; i++)
			held.add(cue(), 0, shown::incrementAndGet);
		assertEquals(HeldFlashes.MOST, held.size());
		assertEquals(5, shown.get());
		held.clear();
		held.tick(HeldFlashes.LONGEST);
		assertEquals(5, shown.get());
	}
}
