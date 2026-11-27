package com.createengines.content.effects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * Sound smoke test: the registered sound events, {@code sounds.json} and the recordings on disk agree, and every
 * recording is a plausible, audible Ogg Vorbis file. It cannot hear anything, so it catches the mistakes that make a
 * sound silently never play (a missing entry, a misspelt file name, a stereo file that will not attenuate, an empty
 * render), not a sound that is merely ugly.
 */
class SoundAssetsTest {

	private static final Path ASSETS = Path.of("src/main/resources/assets/create_engines");
	private static final Path SOUNDS_DIR = ASSETS.resolve("sounds");
	private static final Path REGISTRY = Path.of("src/main/java/com/createengines/registry/CESounds.java");

	/** Gson is its own test dependency: the test classpath has no Minecraft to bring it. */
	private static JsonObject soundsJson() throws IOException {
		return JsonParser.parseString(Files.readString(ASSETS.resolve("sounds.json"))).getAsJsonObject();
	}

	private static Set<String> registeredEvents() throws IOException {
		Matcher matcher = Pattern.compile("register\\(\"([a-z0-9_]+)\"\\)").matcher(Files.readString(REGISTRY));
		Set<String> names = new TreeSet<>();
		while (matcher.find())
			names.add(matcher.group(1));
		return names;
	}

	/** Every file name sounds.json plays, without the namespace. */
	private static Set<String> referencedFiles(JsonObject json) {
		Set<String> files = new TreeSet<>();
		for (JsonElement event : json.asMap().values())
			for (JsonElement sound : event.getAsJsonObject().getAsJsonArray("sounds"))
				files.add(nameOf(sound).substring("create_engines:".length()));
		return files;
	}

	private static String nameOf(JsonElement sound) {
		return sound.getAsJsonObject().get("name").getAsString();
	}

	private static List<Path> recordings() throws IOException {
		try (Stream<Path> files = Files.list(SOUNDS_DIR)) {
			return files.filter(path -> path.toString().endsWith(".ogg")).sorted().toList();
		}
	}

	@Test
	void everyRegisteredEventHasAnEntryAndNothingElseDoes() throws IOException {
		Set<String> registered = registeredEvents();
		assertTrue(registered.size() >= 10, "found only " + registered + " in CESounds; did its register() calls change shape?");
		assertEquals(registered, new TreeSet<>(soundsJson().keySet()),
			"CESounds events and sounds.json entries differ, so one of them plays nothing");
	}

	@Test
	void everyEntryNamesRecordingsThatExistAndSetsAnAttenuation() throws IOException {
		JsonObject json = soundsJson();
		for (String event : json.keySet()) {
			var sounds = json.getAsJsonObject(event).getAsJsonArray("sounds");
			assertTrue(!sounds.isEmpty(), event + " lists no sounds");
			for (JsonElement sound : sounds) {
				String name = nameOf(sound);
				assertTrue(name.startsWith("create_engines:"), event + " plays " + name + " from another namespace");
				String file = name.substring("create_engines:".length());
				assertTrue(Files.isRegularFile(SOUNDS_DIR.resolve(file + ".ogg")), event + " names " + name + ", which is not on disk");
				assertTrue(sound.getAsJsonObject().has("attenuation_distance"), name + " has no attenuation_distance, so it would carry 16 blocks whatever its loudness");
			}
		}
	}

	@Test
	void everyRecordingIsPlayedByAnEvent() throws IOException {
		Set<String> referenced = referencedFiles(soundsJson());
		Set<String> orphans = new TreeSet<>();
		for (Path path : recordings())
			orphans.add(path.getFileName().toString().replace(".ogg", ""));
		orphans.removeAll(referenced);
		assertTrue(orphans.isEmpty(), "recordings no event plays: " + orphans);
	}

	@Test
	void everyRecordingIsAMonoOggOfSensibleLengthThatIsNotSilent() throws IOException {
		List<String> problems = new ArrayList<>();
		for (Path path : recordings()) {
			String name = path.getFileName().toString();
			byte[] bytes = Files.readAllBytes(path);
			Vorbis info;
			try {
				info = Vorbis.read(bytes);
			} catch (IllegalArgumentException e) {
				problems.add(name + ": " + e.getMessage());
				continue;
			}
			// Minecraft only attenuates and pans a mono recording; a stereo one plays at the listener, whatever the distance.
			if (info.channels != 1)
				problems.add(name + " has " + info.channels + " channels; it must be mono to be positional");
			if (info.seconds < 0.03 || info.seconds > 30)
				problems.add(name + " lasts " + info.seconds + " s");
			// Vorbis spends next to nothing on digital silence, so a render that came out empty is tiny for its length.
			double bytesPerSecond = bytes.length / Math.max(info.seconds, 0.001);
			if (bytesPerSecond < 1500 && bytes.length < 3000)
				problems.add(name + " is " + bytes.length + " bytes for " + info.seconds + " s; it is probably silent");
		}
		assertTrue(problems.isEmpty(), String.join("\n", problems));
	}

	@Test
	void loopsAreLongEnoughToLoopWithoutAFlutter() throws IOException {
		Set<String> loops = new HashSet<>(List.of("starter_whir", "turbo_whistle", "supercharger_whine",
			"engine_clatter", "bearing_squeal", "intake_roar"));
		for (String loop : loops) {
			Vorbis info = Vorbis.read(Files.readAllBytes(SOUNDS_DIR.resolve(loop + ".ogg")));
			assertTrue(info.seconds >= 0.4, loop + " is a loop but only " + info.seconds + " s long");
		}
	}

	private static final Set<String> LOOPS = Set.of("starter_whir", "turbo_whistle", "supercharger_whine",
		"engine_clatter", "bearing_squeal", "intake_roar");

	/** A recording that plays once starts and ends in silence, sits on zero and leaves itself some headroom. */
	@Test
	void everyRecordingThatPlaysOnceIsFadedLevelAndUnderTheCeiling() throws IOException {
		List<String> problems = new ArrayList<>();
		for (Path path : recordings()) {
			String name = path.getFileName().toString().replace(".ogg", "");
			if (LOOPS.contains(name))
				continue;
			float[] samples = VorbisSamples.decode(Files.readAllBytes(path));
			int rate = Vorbis.read(Files.readAllBytes(path)).rate;
			float peak = 0;
			double sum = 0;
			for (float sample : samples) {
				peak = Math.max(peak, Math.abs(sample));
				sum += sample;
			}
			if (peak > PEAK_CEILING)
				problems.add(name + " peaks at " + peak);
			if (Math.abs(sum / samples.length) > MOST_OFFSET)
				problems.add(name + " sits " + sum / samples.length + " off zero");
			// A quarter and a half of the way into a 2 ms fade, a faded sound has hardly begun
			for (double[] edge : new double[][] {{0.0005, 0.05}, {0.001, 0.15}}) {
				int span = (int) (edge[0] * rate);
				float start = 0;
				float end = 0;
				for (int i = 0; i < span; i++) {
					start = Math.max(start, Math.abs(samples[i]));
					end = Math.max(end, Math.abs(samples[samples.length - 1 - i]));
				}
				if (Math.max(start, end) > edge[1] * peak)
					problems.add(name + " is not faded: its first " + edge[0] * 1000 + " ms reach " + start / peak
						+ " of its peak, its last " + end / peak);
			}
		}
		assertTrue(problems.isEmpty(), String.join("\n", problems));
	}

	/** The recordings that stand in for the note are made with its pipe and its weights: the generator's copies of the figures. */
	@Test
	void theGeneratorsPipeAndBangsAreTheNotes() throws IOException {
		Path java = Path.of("src/main/java/com/createengines/content");
		same(Path.of("tools/engine_pipe.py"), java.resolve("ExhaustPipe.java"), "RING", "MOST_BACK", "OPEN_HZ", "RADIATE_LOSS");
		same(Path.of("tools/engine_pipe.py"), java.resolve("EngineNoteSynth.java"), "PIPE_HZ");
		same(Path.of("tools/exhaust_pop_sounds.py"), java.resolve("NoteBangs.java"), "SCALE", "POP_BODY", "POP_CRACK",
			"POP_FLAME", "SPIT_BODY", "SPIT_JET", "POP_FIZZ", "SPIT_FIZZ");
		same(Path.of("tools/start_stall_sounds.py"), java.resolve("NoteBangs.java"), "SCALE", "ROCK_BODY", "SIGH");
		same(Path.of("tools/start_stall_sounds.py"), java.resolve("EngineNoteSynth.java"), "BODY_WEIGHT", "CRACK_WEIGHT");
	}

	private static void same(Path python, Path java, String... names) throws IOException {
		for (String name : names)
			assertEquals(figure(java, "\\b" + name + " = ([0-9.]+)f?;"), figure(python, "(?m)^" + name + " = ([0-9.]+)"),
				name + " differs between " + java.getFileName() + " and " + python.getFileName());
	}

	private static double figure(Path file, String pattern) throws IOException {
		Matcher matcher = Pattern.compile(pattern).matcher(Files.readString(file));
		assertTrue(matcher.find(), file + " has no figure like " + pattern);
		return Double.parseDouble(matcher.group(1));
	}

	/** As far past it as the encoder may overshoot the generator's 0.9, and no further. */
	private static final float PEAK_CEILING = 0.99f;
	private static final double MOST_OFFSET = 0.003;

	/** The two facts about an Ogg Vorbis file this test needs: its channel count, and its length from the last page. */
	private record Vorbis(int channels, int rate, double seconds) {

		static Vorbis read(byte[] bytes) {
			ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
			int channels = -1;
			int rate = -1;
			long lastGranule = -1;
			int position = 0;
			while (position + 27 <= bytes.length) {
				if (!"OggS".equals(new String(bytes, position, 4, StandardCharsets.US_ASCII)))
					throw new IllegalArgumentException("not an Ogg page at byte " + position);
				long granule = buffer.getLong(position + 6);
				int segments = bytes[position + 26] & 0xFF;
				int payload = 0;
				for (int i = 0; i < segments; i++)
					payload += bytes[position + 27 + i] & 0xFF;
				int body = position + 27 + segments;
				if (channels < 0) {
					if (bytes[body] != 1 || !"vorbis".equals(new String(bytes, body + 1, 6, StandardCharsets.US_ASCII)))
						throw new IllegalArgumentException("first packet is not a Vorbis identification header");
					channels = bytes[body + 11] & 0xFF;
					rate = buffer.getInt(body + 12);
				}
				if (granule >= 0)
					lastGranule = granule;
				position = body + payload;
			}
			if (channels < 0 || rate <= 0 || lastGranule < 0)
				throw new IllegalArgumentException("no audio pages");
			return new Vorbis(channels, rate, lastGranule / (double) rate);
		}
	}
}
