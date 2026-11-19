package com.createengines.engine.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/** The goggle line a source adds is plain, bounded data, because it is synced to every client that looks at the engine. */
class StatusLineTest {

	@Test
	void aLineKeepsItsKeyArgumentsAndTone() {
		StatusLine line = StatusLine.of("pack.turbine.spool", StatusLine.Tone.GOOD, "80");
		assertEquals("pack.turbine.spool", line.key());
		assertEquals(List.of("80"), line.args());
		assertEquals(StatusLine.Tone.GOOD, line.tone());
	}

	@Test
	void nothingIsNullOrUnbounded() {
		StatusLine bare = new StatusLine(null, null, null);
		assertEquals("", bare.key());
		assertTrue(bare.args().isEmpty());
		assertEquals(StatusLine.Tone.NOTE, bare.tone());
		String long_ = "x".repeat(1000);
		StatusLine big = StatusLine.of(long_, StatusLine.Tone.WARN, long_, "b", "c", "d", "e", "f");
		assertEquals(StatusLine.MAX_TEXT, big.key().length());
		assertEquals(StatusLine.MAX_ARGS, big.args().size());
		assertEquals(StatusLine.MAX_TEXT, big.args().get(0).length());
	}

	@Test
	void aKeyExactlyAtTheLimitIsKeptWholeAndOneOverIsCut() {
		String exact = "y".repeat(StatusLine.MAX_TEXT);
		assertEquals(exact, StatusLine.of(exact, StatusLine.Tone.NOTE).key());
		assertEquals(exact, StatusLine.of(exact + "z", StatusLine.Tone.NOTE).key());
	}

	@Test
	void linesCompareByValueSoTheEngineSyncsOnlyWhenTheyChange() {
		assertEquals(StatusLine.of("k", StatusLine.Tone.NOTE, "1"), StatusLine.of("k", StatusLine.Tone.NOTE, "1"));
	}
}
