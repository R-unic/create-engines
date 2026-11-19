package com.createengines.engine.blueprint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Random;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

import org.junit.jupiter.api.Test;

/**
 * Verification track item 8, the blueprint text. A player can paste anything into the import box and a client can send
 * anything in the import packet, so {@link BlueprintCodec#decode} must meet damaged text by refusing it with a
 * {@link BlueprintException}: never another exception, never an {@link Error}, never a stall. The fuzzing works at three
 * depths so it does not stop at the first checksum or inflate failure: the text itself, the compressed bytes, and the
 * <em>body</em> (inflated, damaged, deflated again), which is the only way to reach the part reader with bad numbers.
 * Everything is seeded, and a failure names the case and the seed.
 */
class BlueprintCodecFuzzTest {

	private static final String PREFIX = "CEBP2:";
	private static final long SLOW_NANOS = 2_000_000_000L;

	private static byte[] inflate(String text) throws DataFormatException {
		byte[] packed = Base64.getUrlDecoder().decode(text.substring(PREFIX.length()));
		Inflater inflater = new Inflater();
		inflater.setInput(packed);
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		byte[] chunk = new byte[4096];
		while (!inflater.finished())
			out.write(chunk, 0, inflater.inflate(chunk));
		inflater.end();
		return out.toByteArray();
	}

	private static byte[] deflate(byte[] body) {
		Deflater deflater = new Deflater();
		deflater.setInput(body);
		deflater.finish();
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		byte[] chunk = new byte[4096];
		while (!deflater.finished())
			out.write(chunk, 0, deflater.deflate(chunk));
		deflater.end();
		return out.toByteArray();
	}

	private static String pack(byte[] compressed) {
		return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(compressed);
	}

	private interface Decoder {
		Blueprint decode(String text) throws BlueprintException;
	}

	/** One decode, timed. Returns the blueprint, or null if refused; anything else is recorded as a problem. */
	private static Blueprint attempt(String text, String where, List<String> problems) {
		return attempt(BlueprintCodec::decode, SLOW_NANOS, text, where, problems);
	}

	private static Blueprint attempt(Decoder decoder, long slowNanos, String text, String where, List<String> problems) {
		long start = System.nanoTime();
		try {
			return decoder.decode(text);
		} catch (BlueprintException refused) {
			return null;
		} catch (Throwable t) {
			problems.add(where + ": decode threw " + t + ", not a BlueprintException");
			return null;
		} finally {
			long took = System.nanoTime() - start;
			if (took > slowNanos)
				problems.add(where + ": decode took " + took / 1_000_000 + " ms");
		}
	}

	/** What a decode that succeeded must satisfy: it can be written out again and read back as the same thing. */
	private static void checkAccepted(Blueprint decoded, String where, List<String> problems) {
		try {
			String again = BlueprintCodec.encode(decoded);
			Blueprint back = BlueprintCodec.decode(again);
			if (!decoded.name().equals(back.name()) || !decoded.builder().equals(back.builder())
				|| !java.util.Set.copyOf(decoded.parts()).equals(java.util.Set.copyOf(back.parts())))
				problems.add(where + ": decoded, but does not survive being written and read again");
		} catch (BlueprintException refused) {
			problems.add(where + ": decoded, but the value is then refused when written again: " + refused.getMessage());
		} catch (Throwable t) {
			problems.add(where + ": decoded, but writing it again threw " + t);
		}
	}

	private static void conclude(List<String> problems, String what) {
		if (problems.isEmpty())
			return;
		StringBuilder message = new StringBuilder(what + ": " + problems.size() + " problems:");
		for (int i = 0; i < Math.min(12, problems.size()); i++)
			message.append("\n  ").append(problems.get(i));
		fail(message.toString());
	}

	@Test
	void everyTruncationOfAGoodStringIsRefusedOrReadCleanly() throws Exception {
		List<String> problems = new ArrayList<>();
		String good = BlueprintCodec.encode(BlueprintCodecTest.sample());
		for (int cut = 0; cut < good.length(); cut++) {
			String where = "cut at " + cut + "/" + good.length();
			Blueprint decoded = attempt(good.substring(0, cut), where, problems);
			if (decoded != null)
				checkAccepted(decoded, where, problems);
		}
		conclude(problems, "text truncations");
	}

	@Test
	void damagedTextIsRefusedOrReadCleanly() throws Exception {
		List<String> problems = new ArrayList<>();
		String good = BlueprintCodec.encode(BlueprintCodecTest.sample());
		Random random = new Random(0xB10E);
		String alphabet = "ABCxyz019-_=:+/ \n\té\u0000";
		for (int i = 0; i < 3000; i++) {
			StringBuilder text = new StringBuilder(good);
			for (int edits = 1 + random.nextInt(4); edits > 0 && text.length() > 0; edits--) {
				int at = random.nextInt(text.length());
				switch (random.nextInt(4)) {
					case 0 -> text.setCharAt(at, alphabet.charAt(random.nextInt(alphabet.length())));
					case 1 -> text.deleteCharAt(at);
					case 2 -> text.insert(at, alphabet.charAt(random.nextInt(alphabet.length())));
					default -> text.insert(at, good.substring(random.nextInt(good.length())));
				}
			}
			String where = "text case " + i + " (seed " + 0xB10E + ")";
			Blueprint decoded = attempt(text.toString(), where, problems);
			if (decoded != null)
				checkAccepted(decoded, where, problems);
		}
		conclude(problems, "damaged text");
	}

	@Test
	void garbageIsRefused() {
		List<String> problems = new ArrayList<>();
		Random random = new Random(0x6A5B);
		for (int i = 0; i < 1500; i++) {
			byte[] junk = new byte[random.nextInt(200)];
			random.nextBytes(junk);
			String where = "garbage " + i;
			switch (i % 4) {
				case 0 -> attempt(new String(junk, java.nio.charset.StandardCharsets.ISO_8859_1), where, problems);
				case 1 -> attempt(PREFIX + new String(junk, java.nio.charset.StandardCharsets.ISO_8859_1), where, problems);
				case 2 -> attempt(PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(junk), where, problems);
				default -> attempt("CEBP" + random.nextInt(10) + ":" + Base64.getUrlEncoder().withoutPadding().encodeToString(junk), where, problems);
			}
		}
		attempt("", "empty", problems);
		attempt(PREFIX, "prefix only", problems);
		attempt("CEBP", "short prefix", problems);
		attempt("CEBP9:AAAA", "unknown version", problems);
		attempt(PREFIX + "A".repeat(100_000), "long run of one letter", problems);
		attempt(" ".repeat(50_000), "only spaces", problems);
		conclude(problems, "garbage");
	}

	/** Compressed bytes damaged so the inflate stream itself is wrong: a bomb or a truncated stream must not hang or crash. */
	@Test
	void damagedCompressedBytesAreRefused() throws Exception {
		List<String> problems = new ArrayList<>();
		byte[] packed = deflate(inflate(BlueprintCodec.encode(BlueprintCodecTest.sample())));
		Random random = new Random(0xC0DE);
		for (int i = 0; i < 2000; i++) {
			byte[] bytes = Arrays.copyOf(packed, packed.length + (i % 7 == 0 ? random.nextInt(8) : 0));
			for (int edits = 1 + random.nextInt(3); edits > 0; edits--) {
				int at = random.nextInt(bytes.length);
				if (random.nextBoolean())
					bytes[at] ^= (byte) (1 << random.nextInt(8));
				else
					bytes[at] = (byte) random.nextInt(256);
			}
			if (i % 5 == 0)
				bytes = Arrays.copyOf(bytes, random.nextInt(bytes.length + 1));
			String where = "compressed case " + i;
			Blueprint decoded = attempt(pack(bytes), where, problems);
			if (decoded != null)
				checkAccepted(decoded, where, problems);
		}
		// A decompression bomb: a long run of zero bytes packs to a few hundred bytes and inflates to far more than any blueprint
		byte[] bomb = deflate(new byte[64 * 1024 * 1024]);
		attempt(pack(bomb), "decompression bomb (" + bomb.length + " bytes packed)", problems);
		conclude(problems, "damaged compressed data");
	}

	/** The part reader itself: a valid stream whose numbers are wrong (huge counts and lengths, bad indexes, junk strings). */
	@Test
	void damagedBodiesAreRefused() throws Exception {
		List<String> problems = new ArrayList<>();
		byte[] body = inflate(BlueprintCodec.encode(BlueprintCodecTest.sample()));
		Random random = new Random(0xB0D1);
		int accepted = 0;
		for (int i = 0; i < 6000; i++) {
			byte[] damaged = Arrays.copyOf(body, body.length + (i % 9 == 0 ? random.nextInt(6) : 0));
			for (int edits = 1 + random.nextInt(4); edits > 0; edits--) {
				int at = random.nextInt(damaged.length);
				switch (random.nextInt(4)) {
					case 0 -> damaged[at] ^= (byte) (1 << random.nextInt(8));
					case 1 -> damaged[at] = (byte) 0xFF;
					case 2 -> damaged[at] = (byte) random.nextInt(256);
					default -> {
						// A run of continuation bytes, which a variable-length number reads as an enormous value
						for (int k = at; k < Math.min(damaged.length, at + 1 + random.nextInt(6)); k++)
							damaged[k] = (byte) 0xFF;
					}
				}
			}
			if (i % 6 == 0)
				damaged = Arrays.copyOf(damaged, random.nextInt(damaged.length + 1));
			String where = "body case " + i;
			Blueprint decoded = attempt(pack(deflate(damaged)), where, problems);
			if (decoded != null) {
				accepted++;
				checkAccepted(decoded, where, problems);
			}
		}
		conclude(problems, "damaged bodies");
		// The sweep is only a test of the part reader if some of it gets past the framing: all-refused means it never got there
		assertTrue(accepted > 0, "no damaged body was ever read; the fuzz is stopping at the framing and never tests the parts");
	}

	/** The harness can tell a bad decoder when it sees one: a leaked exception, a stall, and a refusal that is fine. */
	@Test
	void theFuzzerCanTellABadDecoderWhenItSeesOne() {
		List<String> problems = new ArrayList<>();
		attempt(text -> {
			throw new IllegalStateException("what a decoder should never leak");
		}, SLOW_NANOS, "x", "leaky", problems);
		assertEquals(1, problems.size(), "a leaked IllegalStateException must be reported: " + problems);
		assertTrue(problems.get(0).contains("not a BlueprintException"), problems.get(0));
		problems.clear();
		attempt(text -> {
			throw new StackOverflowError();
		}, SLOW_NANOS, "x", "error", problems);
		assertEquals(1, problems.size(), "an Error must be reported: " + problems);
		problems.clear();
		attempt(text -> {
			try {
				Thread.sleep(60);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			return null;
		}, 20_000_000L, "x", "slow", problems);
		assertEquals(1, problems.size(), "a stall must be reported: " + problems);
		assertTrue(problems.get(0).contains("took"), problems.get(0));
		problems.clear();
		attempt(text -> {
			throw new BlueprintException(BlueprintException.Refusal.CORRUPT, "an honest refusal");
		}, SLOW_NANOS, "x", "refusal", problems);
		assertEquals(List.of(), problems, "a BlueprintException is exactly what a decoder is meant to throw");
	}
}
