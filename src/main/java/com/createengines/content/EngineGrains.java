package com.createengines.content;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * The single firings the engine note is struck from, synthesized offline by {@code tools/engine_grains.py}: the body of the
 * blow-down by ignition, cylinder size and load, the flame front's crack by size, and a diesel's knock, each in several takes.
 * Read once, when the first note is made. Pure Java, so it is tested.
 */
final class EngineGrains {

	static final int SIZES = 3;
	static final int LOADS = 3;
	static final int TAKES = 8;
	private static final String PATH = "/assets/create_engines/engine_grains.bin";

	private final float[][][][][] body = new float[2][SIZES][LOADS][TAKES][];
	private final float[][][] crack = new float[SIZES][TAKES][];
	private final float[][] knock = new float[TAKES][];

	private static final class Holder {
		static final EngineGrains BANK = load();
	}

	static EngineGrains bank() {
		return Holder.BANK;
	}

	/** The blow-down; the array is shared and must not be changed. */
	float[] body(boolean compression, int size, int load, int take) {
		return body[compression ? 1 : 0][size][load][take];
	}

	float[] crack(int size, int take) {
		return crack[size][take];
	}

	float[] knock(int take) {
		return knock[take];
	}

	private static EngineGrains load() {
		InputStream found = EngineGrains.class.getResourceAsStream(PATH);
		if (found == null)
			throw new IllegalStateException("Missing " + PATH);
		try (DataInputStream in = new DataInputStream(found)) {
			return read(in);
		} catch (IOException e) {
			throw new UncheckedIOException("Cannot read " + PATH, e);
		}
	}

	/** Reads the file as the generator writes it, little-endian. */
	static EngineGrains read(DataInputStream in) throws IOException {
		byte[] magic = new byte[4];
		in.readFully(magic);
		int rate = readInt(in);
		int sizes = readInt(in);
		int loads = readInt(in);
		int takes = readInt(in);
		if (!"CEG1".equals(new String(magic, StandardCharsets.US_ASCII)) || rate != EngineNoteSynth.SAMPLE_RATE
			|| sizes != SIZES || loads != LOADS || takes != TAKES)
			throw new IOException("Not the grains this note plays: rate " + rate + ", " + sizes + "x" + loads + "x" + takes);
		EngineGrains bank = new EngineGrains();
		for (float[][][][] ignition : bank.body)
			for (float[][][] size : ignition)
				for (float[][] load : size)
					for (int take = 0; take < TAKES; take++)
						load[take] = grain(in);
		for (float[][] size : bank.crack)
			for (int take = 0; take < TAKES; take++)
				size[take] = grain(in);
		for (int take = 0; take < TAKES; take++)
			bank.knock[take] = grain(in);
		return bank;
	}

	private static float[] grain(DataInputStream in) throws IOException {
		int length = readInt(in);
		if (length <= 0 || length > GrainBus.LONGEST)
			throw new IOException("A grain of " + length + " samples");
		float[] samples = new float[length];
		for (int i = 0; i < length; i++)
			samples[i] = Short.reverseBytes(in.readShort()) / 32767f;
		return samples;
	}

	private static int readInt(DataInputStream in) throws IOException {
		return Integer.reverseBytes(in.readInt());
	}
}
