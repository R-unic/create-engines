package com.createengines.engine.blueprint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.createengines.engine.sim.PlateFigures;

/**
 * Holds one string for every format that has shipped. A blueprint shared today must still open in every later release, so
 * these strings are never edited, and a format that has shipped is never changed: a later release adds a new version and
 * keeps reading these. If a test here fails, the codec changed what an old string means, so fix the codec.
 */
class BlueprintFormatTest {

	/** The blueprint every frozen string below holds (format 1 had no figures). */
	static Blueprint fixed() {
		return new Blueprint("Frozen", "Test", List.of(
			new Blueprint.Part(0, 0, 0, "create_engines:crankshaft", Map.of("axis", "x"), Map.of()),
			new Blueprint.Part(1, 1, -2, "create_engines:cylinder_head", Map.of(), Map.of("metering", 14, "angle", -90))));
	}

	/** The same with a Maker's Plate's figures, which format 2 added. */
	static Blueprint fixedWithFigures() {
		Blueprint plain = fixed();
		return new Blueprint(plain.name(), plain.builder(), plain.parts(), new PlateFigures(123.4f, 96, 5678.9f, 160));
	}

	/** Format 1, as written when the format was introduced (1.4 development). */
	private static final String V1 = "CEBP1:eNpdysENQEAQBdAZQbQgUYkCVOAuE_utjTWS2T2gLf2Ju3d-9WDHDS1HpFwQUTsbJGOC-qBI_WyiW1plyVzKGRKfxPx2_3XFoA42rRBHRSXqIx5udmRYUN999_ojJg";

	/** Format 2: format 1 and a trailer holding the plate's figures. */
	private static final String V2 = "CEBP2:eNpjcyvKr0rNYwlJLS5hYmBgkEwuSk0sSY1PzUvPzEsttkouSszLLs5ITCthZEmsyCxmrGBgZPwng66qMiczLyW1KD4jNTGFgYk1MS89J3UzI0duaklqUWZeugzjJc6Eq7uZFzACAEXlJpY";

	private static void sameParts(Blueprint expected, Blueprint read) {
		assertEquals(expected.name(), read.name());
		assertEquals(expected.builder(), read.builder());
		assertEquals(java.util.Set.copyOf(expected.parts()), java.util.Set.copyOf(read.parts()));
	}

	@Test
	void formatOneStillReadsToTheSameBlueprintWithNoFigures() throws Exception {
		Blueprint read = BlueprintCodec.decode(V1);
		sameParts(fixed(), read);
		assertNull(read.figures());
	}

	@Test
	void formatTwoStillReadsToTheSameBlueprintAndFigures() throws Exception {
		Blueprint read = BlueprintCodec.decode(V2);
		sameParts(fixed(), read);
		assertEquals(fixedWithFigures().figures(), read.figures());
	}

	/**
	 * The writer still writes format 2 and what it writes reads back the same. The compressed bytes themselves are the
	 * JDK's and are not compared, so this cannot go red from a zlib change.
	 */
	@Test
	void theCurrentWriterStillWritesFormatTwo() throws Exception {
		String written = BlueprintCodec.encode(fixedWithFigures());
		assertEquals("CEBP2:", written.substring(0, 6));
		Blueprint read = BlueprintCodec.decode(written);
		sameParts(fixed(), read);
		assertEquals(fixedWithFigures().figures(), read.figures());
		assertNull(BlueprintCodec.decode(BlueprintCodec.encode(fixed())).figures());
	}
}
