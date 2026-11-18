package com.createengines.engine.blueprint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.Base64;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

import org.junit.jupiter.api.Test;

import com.createengines.engine.blueprint.BlueprintException.Refusal;

/**
 * Strings that are valid compressed data but a damaged body: bytes left over after the parts, a figures block that is
 * not what it says, and data after the compressed stream. The decoder must refuse each by name, and a good string
 * with the same figures block still reads. The bodies are made from a real encoded blueprint, so only the part under test
 * is wrong.
 */
class BlueprintCodecRefusalsTest {

	private static final String PREFIX = "CEBP2:";

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

	/** The sample's body with its last byte (the figures flag, 0: none) replaced by {@code tail}. */
	private static String withFiguresBlock(byte... tail) throws Exception {
		byte[] body = inflate(BlueprintCodec.encode(BlueprintCodecTest.sample()));
		assertEquals(0, body[body.length - 1], "the sample carries no figures, so its last byte is the empty flag");
		byte[] changed = Arrays.copyOf(body, body.length - 1 + tail.length);
		System.arraycopy(tail, 0, changed, body.length - 1, tail.length);
		return pack(deflate(changed));
	}

	private static Refusal refusal(String text) {
		return assertThrows(BlueprintException.class, () -> BlueprintCodec.decode(text)).refusal();
	}

	@Test
	void theSampleStringWeStartFromIsGood() throws Exception {
		BlueprintCodec.decode(withFiguresBlock((byte) 0));
	}

	@Test
	void bytesLeftAfterTheLastPartAreRefused() throws Exception {
		assertEquals(Refusal.CORRUPT, refusal(withFiguresBlock((byte) 0, (byte) 7)));
	}

	@Test
	void aFiguresFlagThatIsNeitherNoneNorPresentIsRefused() throws Exception {
		assertEquals(Refusal.CORRUPT, refusal(withFiguresBlock((byte) 2)));
	}

	@Test
	void aFigureBeyondTheLimitIsRefused() throws Exception {
		// flag 1, then a number just under two billion where a plate figure belongs
		assertEquals(Refusal.CORRUPT, refusal(withFiguresBlock((byte) 1, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF,
			(byte) 0x07, (byte) 0, (byte) 0, (byte) 0)));
	}

	@Test
	void figuresWithNoPowerInThemAreRefused() throws Exception {
		assertEquals(Refusal.CORRUPT, refusal(withFiguresBlock((byte) 1, (byte) 0, (byte) 0, (byte) 0, (byte) 0)));
	}

	@Test
	void aFiguresBlockThatStopsShortIsRefused() throws Exception {
		assertEquals(Refusal.CORRUPT, refusal(withFiguresBlock((byte) 1, (byte) 5, (byte) 5)));
	}

	@Test
	void dataAfterTheEndOfTheCompressedStreamIsRefused() throws Exception {
		byte[] packed = deflate(inflate(BlueprintCodec.encode(BlueprintCodecTest.sample())));
		byte[] padded = Arrays.copyOf(packed, packed.length + 3);
		padded[packed.length] = 1;
		padded[packed.length + 1] = 2;
		padded[packed.length + 2] = 3;
		assertEquals(Refusal.CORRUPT, refusal(pack(padded)));
	}
}
