package com.createengines.engine.blueprint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.zip.Deflater;

import org.junit.jupiter.api.Test;

import com.createengines.engine.blueprint.BlueprintException.Refusal;

/**
 * The blueprint string: it must read back what was written, be the same string for the same blueprint, and turn every
 * damaged, truncated, oversized or invented string into a {@link BlueprintException} and nothing else. Every random
 * case names its seed so a failure replays.
 */
class BlueprintCodecTest {

	static Blueprint sample() {
		List<Blueprint.Part> parts = new ArrayList<>();
		parts.add(new Blueprint.Part(0, 0, 0, "create_engines:crankshaft", Map.of("axis", "x"), Map.of()));
		parts.add(new Blueprint.Part(1, 0, 0, "create_engines:crank_throw", Map.of("axis", "x"), Map.of("angle", 90)));
		parts.add(new Blueprint.Part(1, 1, 0, "create_engines:cylinder", Map.of("encased", "true"), Map.of()));
		parts.add(new Blueprint.Part(1, 2, 0, "create_engines:cylinder_head", Map.of(), Map.of("metering", 12)));
		parts.add(new Blueprint.Part(-3, 0, 5, "create_engines:camshaft", Map.of(), Map.of("angle", -45)));
		return new Blueprint("Little Six", "R-unic", parts);
	}

	private static Refusal refusal(String text) {
		return assertThrows(BlueprintException.class, () -> BlueprintCodec.decode(text)).refusal();
	}

	@Test
	void aBlueprintReadsBackWhatWasWritten() throws Exception {
		Blueprint written = sample();
		Blueprint read = BlueprintCodec.decode(BlueprintCodec.encode(written));
		assertEquals(written.name(), read.name());
		assertEquals(written.builder(), read.builder());
		assertEquals(java.util.Set.copyOf(written.parts()), java.util.Set.copyOf(read.parts()));
		assertEquals(written.parts().size(), read.parts().size());
	}

	@Test
	void theSameBlueprintIsTheSameStringWhateverTheOrderOfItsParts() throws Exception {
		Blueprint a = sample();
		List<Blueprint.Part> shuffled = new ArrayList<>(a.parts());
		java.util.Collections.reverse(shuffled);
		Blueprint b = new Blueprint(a.name(), a.builder(), shuffled);
		assertEquals(BlueprintCodec.encode(a), BlueprintCodec.encode(b));
	}

	@Test
	void theStringNamesItsFormatVersion() throws Exception {
		assertTrue(BlueprintCodec.encode(sample()).startsWith("CEBP" + Blueprint.FORMAT_VERSION + ":"));
	}

	@Test
	void pastedTextMayBeWrappedAndIndented() throws Exception {
		String text = BlueprintCodec.encode(sample());
		StringBuilder wrapped = new StringBuilder("  ");
		for (int i = 0; i < text.length(); i += 40)
			wrapped.append(text, i, Math.min(text.length(), i + 40)).append("\r\n  ");
		assertEquals(sample().parts().size(), BlueprintCodec.decode(wrapped.toString()).parts().size());
	}

	@Test
	void dialsKeepTheirSignAndSize() throws Exception {
		for (int value : new int[] { 0, 1, -1, 127, -128, 65_535, Integer.MAX_VALUE, Integer.MIN_VALUE }) {
			Blueprint blueprint = new Blueprint("d", "", List.of(
				new Blueprint.Part(0, 0, 0, "create_engines:cylinder", Map.of(), Map.of("angle", value))));
			Blueprint read = BlueprintCodec.decode(BlueprintCodec.encode(blueprint));
			assertEquals(value, read.parts().get(0).dials().get("angle"), "value " + value);
		}
	}

	@Test
	void aNewerFormatIsRefusedAsNewer() {
		String text = "CEBP" + (Blueprint.FORMAT_VERSION + 1) + ":AAAA";
		assertEquals(Refusal.NEWER_FORMAT, refusal(text));
		assertEquals(Refusal.NEWER_FORMAT, refusal("CEBP99999:AAAA"));
	}

	@Test
	void textThatIsNotABlueprintIsRefused() {
		for (String text : new String[] { "", "   ", "hello", "CEBP", "CEBP:", "CEBP1", "CEBPx:AAAA", "CEBP0:AAAA", "CEBP-1:AAAA",
			"cebp1:AAAA", "CEBP1:", "CEBP1:!!!!", "CEBP1:AAAA", "CEBP1:A" })
			assertNotEquals(null, refusal(text), "text '" + text + "' must be refused");
		assertEquals(Refusal.NOT_A_BLUEPRINT, refusal("hello"));
		assertEquals(Refusal.NOT_A_BLUEPRINT, refusal(null));
		assertEquals(Refusal.CORRUPT, refusal("CEBP1:!!!!"));
	}

	@Test
	void overlongTextIsRefusedBeforeItIsRead() {
		assertEquals(Refusal.TOO_LARGE, refusal("CEBP1:" + "A".repeat(Blueprint.Limits.MAX_TEXT + 10)));
		assertEquals(Refusal.TOO_LARGE, refusal("A".repeat(Blueprint.Limits.MAX_TEXT * 2)));
	}

	@Test
	void aSmallStringThatUnpacksHugeIsStopped() throws Exception {
		byte[] zeros = new byte[Blueprint.Limits.MAX_BODY * 8];
		Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
		deflater.setInput(zeros);
		deflater.finish();
		ByteArrayOutputStream packed = new ByteArrayOutputStream();
		byte[] chunk = new byte[4096];
		while (!deflater.finished())
			packed.write(chunk, 0, deflater.deflate(chunk));
		deflater.end();
		String text = "CEBP1:" + Base64.getUrlEncoder().withoutPadding().encodeToString(packed.toByteArray());
		assertTrue(text.length() < 20_000, "the bomb itself must be small: " + text.length());
		assertEquals(Refusal.TOO_LARGE, refusal(text));
	}

	@Test
	void everyTruncationOfAGoodStringIsRefused() throws Exception {
		String text = BlueprintCodec.encode(sample());
		for (int cut = 0; cut < text.length(); cut++) {
			String part = text.substring(0, cut);
			try {
				BlueprintCodec.decode(part);
				throw new AssertionError("a string cut at " + cut + " of " + text.length() + " was accepted");
			} catch (BlueprintException expected) {
				// refused, as it should be
			}
		}
	}

	@Test
	void mutatedStringsEndInARefusalOrAValidBlueprint() throws Exception {
		String text = BlueprintCodec.encode(sample());
		for (int seed = 0; seed < 3000; seed++) {
			Random random = new Random(seed);
			char[] chars = text.toCharArray();
			int flips = 1 + random.nextInt(4);
			String alphabet = "ABCxyz019-_=:! é";
			for (int i = 0; i < flips; i++)
				chars[random.nextInt(chars.length)] = alphabet.charAt(random.nextInt(alphabet.length()));
			try {
				Blueprint read = BlueprintCodec.decode(new String(chars));
				// If it still parses, it must still be well formed
				BlueprintRules.checkShape(read);
			} catch (BlueprintException expected) {
				// refused
			} catch (RuntimeException e) {
				throw new AssertionError("seed " + seed + " threw " + e, e);
			}
		}
	}

	@Test
	void garbageNeverThrowsAnythingButARefusal() {
		for (int seed = 0; seed < 3000; seed++) {
			Random random = new Random(seed);
			StringBuilder sb = new StringBuilder(random.nextBoolean() ? "CEBP1:" : "");
			int length = random.nextInt(600);
			for (int i = 0; i < length; i++)
				sb.append((char) (random.nextInt(4) == 0 ? random.nextInt(0x3000) : 'A' + random.nextInt(58)));
			try {
				BlueprintCodec.decode(sb.toString());
			} catch (BlueprintException expected) {
				// refused
			} catch (RuntimeException e) {
				throw new AssertionError("seed " + seed + " threw " + e, e);
			}
		}
	}

	@Test
	void garbageInsideAValidCompressedBodyIsRefused() throws Exception {
		for (int seed = 0; seed < 1500; seed++) {
			Random random = new Random(seed);
			byte[] body = new byte[random.nextInt(300)];
			random.nextBytes(body);
			Deflater deflater = new Deflater();
			deflater.setInput(body);
			deflater.finish();
			ByteArrayOutputStream packed = new ByteArrayOutputStream();
			byte[] chunk = new byte[512];
			while (!deflater.finished())
				packed.write(chunk, 0, deflater.deflate(chunk));
			deflater.end();
			String text = "CEBP1:" + Base64.getUrlEncoder().withoutPadding().encodeToString(packed.toByteArray());
			try {
				BlueprintRules.checkShape(BlueprintCodec.decode(text));
			} catch (BlueprintException expected) {
				// refused
			} catch (RuntimeException e) {
				throw new AssertionError("seed " + seed + " threw " + e, e);
			}
		}
	}

	@Test
	void plateFiguresSurviveTheStringAndAnUnstampedPlateIsNotCarried() throws Exception {
		Blueprint base = sample();
		com.createengines.engine.sim.PlateFigures figures = new com.createengines.engine.sim.PlateFigures(12.5f, 80, 3456.7f, 220);
		Blueprint with = new Blueprint(base.name(), base.builder(), base.parts(), figures);
		assertEquals(figures, BlueprintCodec.decode(BlueprintCodec.encode(with)).figures());
		Blueprint unstamped = new Blueprint(base.name(), base.builder(), base.parts(), com.createengines.engine.sim.PlateFigures.NONE);
		assertEquals(null, unstamped.figures());
		assertEquals(null, BlueprintCodec.decode(BlueprintCodec.encode(unstamped)).figures());
	}

	@Test
	void aBlueprintThatBreaksALimitCannotBeWritten() {
		List<Blueprint.Part> tooMany = new ArrayList<>();
		for (int i = 0; i < Blueprint.Limits.MAX_PARTS + 1; i++)
			tooMany.add(new Blueprint.Part(i % 100, (i / 100) % 100, i / 10_000, "create_engines:cylinder"));
		assertEquals(Refusal.TOO_LARGE, assertThrows(BlueprintException.class,
			() -> BlueprintCodec.encode(new Blueprint("x", "", tooMany))).refusal());
		assertEquals(Refusal.EMPTY, assertThrows(BlueprintException.class,
			() -> BlueprintCodec.encode(new Blueprint("x", "", List.of()))).refusal());
		assertEquals(Refusal.OUT_OF_RANGE, assertThrows(BlueprintException.class, () -> BlueprintCodec.encode(
			new Blueprint("x", "", List.of(new Blueprint.Part(128, 0, 0, "create_engines:cylinder"))))).refusal());
	}

	@Test
	void theLargestAllowedBlueprintFitsInTheTextLimit() throws Exception {
		// A full-size blueprint with the settings a real engine has: a few small dials a block
		List<Blueprint.Part> parts = new ArrayList<>();
		Random random = new Random(7);
		for (int i = 0; i < Blueprint.Limits.MAX_PARTS; i++)
			parts.add(new Blueprint.Part(i % 16, (i / 16) % 16, i / 256, "create_engines:cylinder", Map.of("encased", "true"),
				Map.of("angle", random.nextInt(720), "metering", random.nextInt(20))));
		String text = BlueprintCodec.encode(new Blueprint("big", "", parts));
		assertTrue(text.length() < Blueprint.Limits.MAX_TEXT, "text is " + text.length());
		assertEquals(Blueprint.Limits.MAX_PARTS, BlueprintCodec.decode(text).parts().size());
	}

	/** Even a blueprint stuffed with random numbers is either written and read back, or refused for size: nothing else. */
	@Test
	void theWorstCaseIsWrittenOrRefusedForSize() throws Exception {
		List<Blueprint.Part> parts = new ArrayList<>();
		Random random = new Random(11);
		for (int i = 0; i < Blueprint.Limits.MAX_PARTS; i++) {
			Map<String, Integer> dials = new java.util.HashMap<>();
			for (int d = 0; d < Blueprint.Limits.MAX_ENTRIES; d++)
				dials.put("dial_" + d, random.nextInt());
			parts.add(new Blueprint.Part(i % 16, (i / 16) % 16, i / 256, "create_engines:cylinder", Map.of(), dials));
		}
		Blueprint worst = new Blueprint("worst", "", parts);
		try {
			String text = BlueprintCodec.encode(worst);
			assertTrue(text.length() <= Blueprint.Limits.MAX_TEXT);
			assertEquals(parts.size(), BlueprintCodec.decode(text).parts().size());
		} catch (BlueprintException refused) {
			assertEquals(Refusal.TOO_LARGE, refused.refusal());
		}
	}
}
