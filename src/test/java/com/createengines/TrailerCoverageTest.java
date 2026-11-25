package com.createengines;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * The trailer (the {@code trailer} set of client/ShowcaseRun, recorded and cut by tools/trailer) keeps up with the mod. Every
 * block and item has a line in src/smoke/trailer-parts.txt saying which shot shows it, or why it has none, so a part added
 * without a decision about the trailer fails here. A line may name only a shot that exists, a skip or a later needs its
 * reason, no line may name a part that is gone, and every shot has a caption in tools/trailer/captions.txt (and no caption
 * is for a shot that is gone). The file cannot say a part is really on screen; the shots' stills and the person watching the
 * cut do that.
 */
class TrailerCoverageTest {

	private static final Path ROOT = Path.of("").toAbsolutePath();
	private static final Path LANG = ROOT.resolve("src/generated/resources/assets/create_engines/lang/en_us.json");
	private static final Path PARTS = ROOT.resolve("src/smoke/trailer-parts.txt");
	private static final Path SHOWCASE = ROOT.resolve("src/main/java/com/createengines/client/ShowcaseRun.java");
	private static final Path CAPTIONS = ROOT.resolve("tools/trailer/captions.txt");
	private static final Path EDIT = ROOT.resolve("tools/trailer/edit.txt");

	private static final Pattern PART_KEY = Pattern.compile("\"(?:block|item)\\.create_engines\\.([a-z0-9_]+)\"");
	private static final Pattern SHOT = Pattern.compile("new Shot\\(\"([a-z0-9_]+)\"");
	private static final Pattern STATUS = Pattern.compile("(skip|later) - \\S.*");

	private static Set<String> registeredParts() throws IOException {
		Set<String> out = new TreeSet<>();
		Matcher m = PART_KEY.matcher(Files.readString(LANG, StandardCharsets.UTF_8));
		while (m.find())
			if (!m.group(1).endsWith("_bucket"))
				out.add(m.group(1));
		return out;
	}

	private static Set<String> shots() throws IOException {
		Set<String> out = new TreeSet<>();
		Matcher m = SHOT.matcher(Files.readString(SHOWCASE, StandardCharsets.UTF_8));
		while (m.find())
			out.add(m.group(1));
		return out;
	}

	private static Map<String, String> classified() throws IOException {
		Map<String, String> out = new TreeMap<>();
		for (String line : Files.readAllLines(PARTS, StandardCharsets.UTF_8)) {
			if (line.isBlank() || line.startsWith("#"))
				continue;
			int colon = line.indexOf(':');
			assertTrue(colon > 0, "A line of trailer-parts.txt is not \"<part>: <status>\": " + line);
			assertTrue(out.put(line.substring(0, colon).trim(), line.substring(colon + 1).trim()) == null,
				"A part is listed twice in trailer-parts.txt: " + line);
		}
		return out;
	}

	@Test
	void everyPartHasADecisionAboutTheTrailer() throws IOException {
		Set<String> registered = registeredParts();
		Map<String, String> listed = classified();

		List<String> unlisted = new ArrayList<>(registered);
		unlisted.removeAll(listed.keySet());
		assertTrue(unlisted.isEmpty(), "Parts with no line in src/smoke/trailer-parts.txt: " + unlisted
			+ "\nShow a significant part in the trailer (a pad and a shot in ShowcaseRun, a caption in tools/trailer/captions.txt)"
			+ " and name the shot, or give a skip with its reason.");

		List<String> gone = new ArrayList<>(listed.keySet());
		gone.removeAll(registered);
		assertTrue(gone.isEmpty(), "Lines in trailer-parts.txt for parts that are not registered: " + gone);
	}

	@Test
	void everyStatusNamesARealShotOrGivesItsReason() throws IOException {
		Set<String> shots = shots();
		List<String> bad = new ArrayList<>();
		int later = 0;
		for (Map.Entry<String, String> part : classified().entrySet()) {
			String status = part.getValue();
			if (status.startsWith("later"))
				later++;
			if (!shots.contains(status) && !STATUS.matcher(status).matches())
				bad.add(part.getKey() + ": " + status);
		}
		assertTrue(bad.isEmpty(), "Statuses that are neither a shot of ShowcaseRun nor a skip or later with a reason: " + bad
			+ "\nShots are " + shots);
		System.out.println("trailer: " + later + " significant parts are not in the trailer yet (the later lines)");
	}

	@Test
	void everyShotHasACaptionAndNoCaptionIsLeftOver() throws IOException {
		Set<String> captions = new TreeSet<>();
		boolean title = false;
		for (String line : Files.readAllLines(CAPTIONS, StandardCharsets.UTF_8)) {
			if (line.startsWith("caption ")) {
				int colon = line.indexOf(':');
				assertTrue(colon > 0 && !line.substring(colon + 1).isBlank(), "A caption has no text: " + line);
				captions.add(line.substring("caption ".length(), colon).trim());
			} else if (line.startsWith("title:")) {
				title = true;
			}
		}
		assertTrue(title, "tools/trailer/captions.txt has no title: line");
		assertEquals(shots(), captions, "The shots of ShowcaseRun and the captions of tools/trailer/captions.txt must match");
	}

	/**
	 * The full cut takes its order and lengths from tools/trailer/edit.txt: a shot missing there would silently fall out of the
	 * trailer, and a line for a shot that is gone would cut nothing. Every piece is a whole number of half bars of the music, so
	 * the cuts stay on the beat.
	 */
	@Test
	void theEditUsesEveryShotOnceAndKeepsTheBeat() throws IOException {
		List<String> edited = new ArrayList<>();
		List<String> offBeat = new ArrayList<>();
		for (String line : Files.readAllLines(EDIT, StandardCharsets.UTF_8)) {
			String text = line.split("#", 2)[0].trim();
			if (text.isEmpty())
				continue;
			String[] fields = text.split("\\s+");
			assertTrue(fields.length >= 2, "A line of edit.txt is not \"<shot> <seconds> [options]\": " + line);
			edited.add(fields[0]);
			double halfBars = Double.parseDouble(fields[1]) / 1.5;
			if (halfBars <= 0 || Math.abs(halfBars - Math.rint(halfBars)) > 1e-9)
				offBeat.add(fields[0] + " " + fields[1]);
		}
		Set<String> once = new TreeSet<>(edited);
		assertEquals(once.size(), edited.size(), "A shot is in tools/trailer/edit.txt twice: " + edited);
		assertEquals(shots(), once, "The shots of ShowcaseRun and the lines of tools/trailer/edit.txt must match");
		assertTrue(offBeat.isEmpty(), "Pieces of edit.txt that are not a whole number of half bars (1.5 s): " + offBeat);
	}

	@Test
	void theTeaserAndTheThumbnailNameRealShotsAndTheTeaserHasItsWords() throws IOException {
		Set<String> shots = shots();
		Set<String> teaser = new TreeSet<>();
		Set<String> teaserWords = new TreeSet<>();
		String thumbnail = null;
		String verticalThumbnail = null;
		for (String line : Files.readAllLines(CAPTIONS, StandardCharsets.UTF_8)) {
			int colon = line.indexOf(':');
			if (colon < 0)
				continue;
			String key = line.substring(0, colon).trim();
			String value = line.substring(colon + 1).trim();
			if (key.equals("short_shots"))
				teaser.addAll(List.of(value.split("\\s+")));
			else if (key.startsWith("short_caption "))
				teaserWords.add(key.substring("short_caption ".length()));
			else if (key.equals("thumbnail"))
				thumbnail = value;
			else if (key.equals("vertical_thumbnail"))
				verticalThumbnail = value;
		}
		assertTrue(!teaser.isEmpty(), "tools/trailer/captions.txt has no short_shots: line");
		assertTrue(shots.containsAll(teaser), "short_shots names a shot that does not exist: " + teaser + " of " + shots);
		assertEquals(teaser, teaserWords, "Every short shot needs a short_caption, and no short_caption may be left over");
		assertTrue(thumbnail != null && shots.contains(thumbnail), "thumbnail: must name a shot: " + thumbnail);
		assertTrue(verticalThumbnail != null && shots.contains(verticalThumbnail),
				"vertical_thumbnail: must name a shot: " + verticalThumbnail);
	}
}
