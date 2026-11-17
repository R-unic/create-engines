package com.createengines.engine.blueprint;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

import com.createengines.engine.blueprint.Blueprint.Limits;
import com.createengines.engine.blueprint.BlueprintException.Refusal;
import com.createengines.engine.sim.PlateFigures;

/**
 * Turns a {@link Blueprint} into a string that can be pasted into chat and back. The string is
 * {@code CEBP<version>:<body>}: a prefix that names the format version, then the body, compressed and written in URL-safe
 * base64 with no padding. Whitespace in a pasted string is ignored, since chat wraps long lines.
 * <p>
 * <b>The version rule.</b> A new release may add formats (a version 2 with more in it) and always keeps reading every
 * earlier one, so a blueprint shared today still opens in 2.0. {@link #decode} refuses a version newer than
 * {@link Blueprint#FORMAT_VERSION} with {@link Refusal#NEWER_FORMAT}. A version that has shipped is never changed:
 * {@code BlueprintFormatTest} holds a string for each, and fails if one stops reading to the same blueprint.
 * <p>
 * <b>Hostile input.</b> Every size is checked before it is used: the text length, the unpacked size (the inflater is
 * stopped at {@link Limits#MAX_BODY}, so a small string cannot unpack into a large one), each count before a list is
 * allocated, and each string's length. A damaged or invented string ends in a {@link BlueprintException} and nothing
 * else: no other exception, no hang.
 */
public final class BlueprintCodec {

	private static final String PREFIX = "CEBP";
	/** Largest packed figure (tenths of SU, or RPM) a blueprint may carry. */
	private static final int MAX_FIGURE = 100_000_000;

	private BlueprintCodec() {
	}

	/** Writes the current format. The blueprint must pass {@link BlueprintRules#checkShape}. */
	public static String encode(Blueprint blueprint) throws BlueprintException {
		BlueprintRules.checkShape(blueprint);
		byte[] body = writeBody(blueprint);
		Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
		try {
			deflater.setInput(body);
			deflater.finish();
			ByteArrayOutputStream packed = new ByteArrayOutputStream();
			byte[] chunk = new byte[4096];
			while (!deflater.finished()) {
				int n = deflater.deflate(chunk);
				packed.write(chunk, 0, n);
			}
			String text = PREFIX + Blueprint.FORMAT_VERSION + ":"
				+ Base64.getUrlEncoder().withoutPadding().encodeToString(packed.toByteArray());
			if (text.length() > Limits.MAX_TEXT)
				throw new BlueprintException(Refusal.TOO_LARGE, "text is " + text.length() + " characters");
			return text;
		} finally {
			deflater.end();
		}
	}

	/** Reads a pasted string of any format up to the current one. Throws only {@link BlueprintException}. */
	public static Blueprint decode(String text) throws BlueprintException {
		if (text == null)
			throw new BlueprintException(Refusal.NOT_A_BLUEPRINT, "no text");
		if (text.length() > Limits.MAX_TEXT + 1024)
			throw new BlueprintException(Refusal.TOO_LARGE, "text is " + text.length() + " characters");
		String clean = stripWhitespace(text);
		if (clean.length() > Limits.MAX_TEXT)
			throw new BlueprintException(Refusal.TOO_LARGE, "text is " + clean.length() + " characters");
		if (!clean.startsWith(PREFIX))
			throw new BlueprintException(Refusal.NOT_A_BLUEPRINT, "no " + PREFIX + " prefix");
		int colon = clean.indexOf(':');
		if (colon < 0 || colon == PREFIX.length() || colon > PREFIX.length() + 6)
			throw new BlueprintException(Refusal.NOT_A_BLUEPRINT, "no version");
		int version;
		try {
			version = Integer.parseInt(clean.substring(PREFIX.length(), colon));
		} catch (NumberFormatException e) {
			throw new BlueprintException(Refusal.NOT_A_BLUEPRINT, "bad version");
		}
		if (version > Blueprint.FORMAT_VERSION)
			throw new BlueprintException(Refusal.NEWER_FORMAT, "format " + version + ", this release reads up to "
				+ Blueprint.FORMAT_VERSION);
		if (version < 1)
			throw new BlueprintException(Refusal.NOT_A_BLUEPRINT, "bad version");
		byte[] packed;
		try {
			packed = Base64.getUrlDecoder().decode(clean.substring(colon + 1));
		} catch (IllegalArgumentException e) {
			throw new BlueprintException(Refusal.CORRUPT, "not base64");
		}
		byte[] body = unpack(packed);
		Blueprint blueprint = switch (version) {
			case 1 -> readBody(body, false);
			case 2 -> readBody(body, true);
			default -> throw new BlueprintException(Refusal.NEWER_FORMAT, "format " + version);
		};
		BlueprintRules.checkShape(blueprint);
		return blueprint;
	}

	private static String stripWhitespace(String text) {
		StringBuilder out = new StringBuilder(text.length());
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (!Character.isWhitespace(c))
				out.append(c);
		}
		return out.toString();
	}

	private static byte[] unpack(byte[] packed) throws BlueprintException {
		Inflater inflater = new Inflater();
		try {
			inflater.setInput(packed);
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			byte[] chunk = new byte[4096];
			while (!inflater.finished()) {
				int n = inflater.inflate(chunk);
				if (n == 0 && !inflater.finished())
					throw new BlueprintException(Refusal.CORRUPT, "compressed data is cut short");
				if (out.size() + n > Limits.MAX_BODY)
					throw new BlueprintException(Refusal.TOO_LARGE, "unpacks past " + Limits.MAX_BODY + " bytes");
				out.write(chunk, 0, n);
			}
			if (inflater.getRemaining() > 0)
				throw new BlueprintException(Refusal.CORRUPT, "bytes after the compressed data");
			return out.toByteArray();
		} catch (DataFormatException e) {
			throw new BlueprintException(Refusal.CORRUPT, "bad compressed data");
		} finally {
			inflater.end();
		}
	}

	private static byte[] writeBody(Blueprint blueprint) throws BlueprintException {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (DataOutputStream out = new DataOutputStream(bytes)) {
			writeString(out, blueprint.name());
			writeString(out, blueprint.builder());
			writeVarInt(out, blueprint.parts().size());
			// A fixed order, so one blueprint is always one string
			List<Blueprint.Part> parts = new ArrayList<>(blueprint.parts());
			parts.sort((a, b) -> a.y() != b.y() ? Integer.compare(a.y(), b.y())
				: a.z() != b.z() ? Integer.compare(a.z(), b.z()) : Integer.compare(a.x(), b.x()));
			for (Blueprint.Part part : parts) {
				out.writeByte(part.x());
				out.writeByte(part.y());
				out.writeByte(part.z());
				writeString(out, part.block());
				out.writeByte(part.state().size());
				for (Map.Entry<String, String> state : new TreeMap<>(part.state()).entrySet()) {
					writeString(out, state.getKey());
					writeString(out, state.getValue());
				}
				out.writeByte(part.dials().size());
				for (Map.Entry<String, Integer> dial : new TreeMap<>(part.dials()).entrySet()) {
					writeString(out, dial.getKey());
					writeVarInt(out, (dial.getValue() << 1) ^ (dial.getValue() >> 31));
				}
			}
			writeFigures(out, blueprint.figures());
		} catch (IOException e) {
			throw new BlueprintException(Refusal.CORRUPT, "could not write");
		}
		return bytes.toByteArray();
	}

	private static Blueprint readBody(byte[] body, boolean withFigures) throws BlueprintException {
		try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(body))) {
			String name = readString(in, Limits.MAX_NAME);
			String builder = readString(in, Limits.MAX_NAME);
			int count = readVarInt(in);
			if (count < 0 || count > Limits.MAX_PARTS)
				throw new BlueprintException(Refusal.TOO_LARGE, count + " parts, at most " + Limits.MAX_PARTS);
			List<Blueprint.Part> parts = new ArrayList<>(count);
			for (int i = 0; i < count; i++) {
				int x = in.readByte();
				int y = in.readByte();
				int z = in.readByte();
				String block = readString(in, Limits.MAX_ID);
				Map<String, String> state = new TreeMap<>();
				int states = readCount(in);
				for (int s = 0; s < states; s++)
					state.put(readString(in, Limits.MAX_KEY), readString(in, Limits.MAX_KEY));
				Map<String, Integer> dials = new TreeMap<>();
				int dialCount = readCount(in);
				for (int d = 0; d < dialCount; d++) {
					String key = readString(in, Limits.MAX_KEY);
					int zigzag = readVarInt(in);
					dials.put(key, (zigzag >>> 1) ^ -(zigzag & 1));
				}
				parts.add(new Blueprint.Part(x, y, z, block, state, dials));
			}
			PlateFigures figures = withFigures ? readFigures(in) : null;
			if (in.read() != -1)
				throw new BlueprintException(Refusal.CORRUPT, "bytes after the last part");
			return new Blueprint(name, builder, parts, figures);
		} catch (IOException e) {
			throw new BlueprintException(Refusal.CORRUPT, "cut short or damaged");
		}
	}

	private static int readCount(DataInputStream in) throws IOException, BlueprintException {
		int count = in.readUnsignedByte();
		if (count > Limits.MAX_ENTRIES)
			throw new BlueprintException(Refusal.TOO_LARGE, count + " entries on one block");
		return count;
	}

	/** The format 2 trailer: a flag, then the four figures if there are any. */
	private static void writeFigures(DataOutputStream out, PlateFigures figures) throws IOException {
		if (figures == null) {
			out.writeByte(0);
			return;
		}
		out.writeByte(1);
		for (int value : figures.pack())
			writeVarInt(out, value);
	}

	private static PlateFigures readFigures(DataInputStream in) throws IOException, BlueprintException {
		int flag = in.readUnsignedByte();
		if (flag == 0)
			return null;
		if (flag != 1)
			throw new BlueprintException(Refusal.CORRUPT, "bad figures flag");
		int[] packed = new int[4];
		for (int i = 0; i < 4; i++) {
			packed[i] = readVarInt(in);
			if (packed[i] < 0 || packed[i] > MAX_FIGURE)
				throw new BlueprintException(Refusal.CORRUPT, "a figure out of range");
		}
		PlateFigures figures = PlateFigures.unpack(packed);
		if (!figures.stamped())
			throw new BlueprintException(Refusal.CORRUPT, "figures with no power in them");
		return figures;
	}

	private static void writeString(DataOutputStream out, String text) throws IOException {
		byte[] utf = text.getBytes(StandardCharsets.UTF_8);
		writeVarInt(out, utf.length);
		out.write(utf);
	}

	/** Reads a string, refusing one longer than {@code max} characters before reading its bytes. */
	private static String readString(DataInputStream in, int max) throws IOException, BlueprintException {
		int length = readVarInt(in);
		// Four bytes a character is the most UTF-8 can use
		if (length < 0 || length > max * 4)
			throw new BlueprintException(Refusal.TOO_LARGE, "a string of " + length + " bytes");
		byte[] utf = new byte[length];
		in.readFully(utf);
		String text = new String(utf, StandardCharsets.UTF_8);
		if (text.length() > max)
			throw new BlueprintException(Refusal.TOO_LARGE, "a string of " + text.length() + " characters");
		return text;
	}

	private static void writeVarInt(DataOutputStream out, int value) throws IOException {
		int v = value;
		while ((v & ~0x7F) != 0) {
			out.writeByte((v & 0x7F) | 0x80);
			v >>>= 7;
		}
		out.writeByte(v);
	}

	private static int readVarInt(DataInputStream in) throws IOException, BlueprintException {
		int result = 0;
		for (int shift = 0; shift < 35; shift += 7) {
			int b = in.readUnsignedByte();
			result |= (b & 0x7F) << shift;
			if ((b & 0x80) == 0)
				return result;
		}
		throw new BlueprintException(Refusal.CORRUPT, "a number that never ends");
	}
}
