package com.createengines.content;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;

/**
 * A filter left to die away in the subnormal numbers costs many times more to work out. A slow single leaves most of a
 * second between firings, long enough for every fast filter to get there, so its state is looked through as it idles.
 */
class NoteSubnormalTest {

	@Test
	void anIdlingSingleLeavesNothingInTheSubnormals() throws IllegalAccessException {
		assertEquals(Set.of(), found(0));
	}

	@Test
	void norDoesAMuffledOne() throws IllegalAccessException {
		assertEquals(Set.of(), found(1));
	}

	/** The fields seen holding a subnormal while a single idled, then stopped. */
	private static Set<String> found(float muffling) throws IllegalAccessException {
		EngineNoteSynth synth = new EngineNoteSynth(1);
		synth.setMuffling(muffling);
		synth.set(new EngineNoteSynth.Params(60, new float[] {0}, 1, 0, 1));
		Set<String> found = new TreeSet<>();
		for (int i = 0; i < 10 * EngineNoteSynth.SAMPLE_RATE; i++) {
			if (i == 8 * EngineNoteSynth.SAMPLE_RATE)
				synth.set(EngineNoteSynth.Params.SILENT);
			synth.next();
			// Often enough to catch a filter passing through, which takes thousands of samples
			if (i % 101 == 0)
				look(synth, "synth", 0, found);
		}
		return found;
	}

	private static void look(Object in, String path, int depth, Set<String> found) throws IllegalAccessException {
		for (Field field : in.getClass()
			.getDeclaredFields()) {
			if (Modifier.isStatic(field.getModifiers()))
				continue;
			field.setAccessible(true);
			Object value = field.get(in);
			String name = path + "." + field.getName();
			if (value instanceof Float number) {
				if (number != 0 && Math.abs(number) < Float.MIN_NORMAL)
					found.add(name);
			} else if (value instanceof Double number) {
				if (number != 0 && Math.abs(number) < Double.MIN_NORMAL)
					found.add(name);
			} else if (value instanceof Object[] all) {
				for (Object each : all)
					if (ours(each))
						look(each, name, depth + 1, found);
			} else if (ours(value) && depth < 3)
				look(value, name, depth + 1, found);
		}
	}

	private static boolean ours(Object value) {
		return value != null && !(value instanceof Enum) && !(value instanceof EngineGrains)
			&& value.getClass()
				.getName()
				.startsWith("com.createengines.content");
	}
}
