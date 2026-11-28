package com.createengines.client;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.createengines.content.SoundProbe;

import net.minecraft.core.BlockPos;

/**
 * The sound bench's event log, one JSON object a line, each with the wall-clock millisecond it happened at, which is what
 * tools/sound_audit.py lines the recording up with. It is the {@link SoundProbe}'s listener. Streams are read on the sound
 * thread, so lines are queued and written from the client thread by {@link #flush}.
 */
final class ShowcaseRunSoundLog implements SoundProbe.Listener {

	/** A cam is seen by every cylinder every frame; one sighting a tenth of a second is plenty to follow it. */
	private static final long SIGHTING_GAP_MS = 100;

	private final ConcurrentLinkedQueue<String> lines = new ConcurrentLinkedQueue<>();
	private final Map<Object, Integer> ids = new IdentityHashMap<>();
	private final Map<BlockPos, Long> sighted = new HashMap<>();
	private final BufferedWriter out;

	ShowcaseRunSoundLog(Path file) {
		try {
			Files.createDirectories(file.getParent());
			out = Files.newBufferedWriter(file, StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/** An event of kind {@code kind}, with further JSON members {@code members} (empty, or starting with a comma). */
	void event(String kind, String members) {
		lines.add("{\"t\":" + System.currentTimeMillis() + ",\"e\":\"" + kind + "\"" + members + "}");
	}

	private synchronized int id(Object of) {
		return ids.computeIfAbsent(of, $ -> ids.size() + 1);
	}

	private static String engine(BlockPos at) {
		return ",\"engine\":\"" + at.getX() + " " + at.getY() + " " + at.getZ() + "\"";
	}

	@Override
	public void firing(BlockPos at) {
		event("firing", engine(at));
	}

	@Override
	public void sighting(BlockPos at, double cycleDegrees) {
		long now = System.currentTimeMillis();
		Long last = sighted.get(at);
		if (last != null && now - last < SIGHTING_GAP_MS)
			return;
		sighted.put(at.immutable(), now);
		event("sight", engine(at) + String.format(Locale.ROOT, ",\"deg\":%.1f", cycleDegrees));
	}

	@Override
	public void level(String voice, Object source, float volume, float pitch) {
		event("level", String.format(Locale.ROOT, ",\"voice\":\"%s\",\"id\":%d,\"v\":%.4f,\"p\":%.4f", voice, id(source),
			volume, pitch));
	}

	@Override
	public void streamRead(Object stream, int samples) {
		event("read", ",\"id\":" + id(stream) + ",\"n\":" + samples);
	}

	@Override
	public void rendered(int samples, long nanos) {
		event("render", ",\"n\":" + samples + ",\"ns\":" + nanos);
	}

	void flush() {
		try {
			for (String line = lines.poll(); line != null; line = lines.poll()) {
				out.write(line);
				out.write('\n');
			}
			out.flush();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
