package com.createengines.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * The engine note bug on Sable's moving structures. Sable plays a sound that starts on one inside a wrapper, so asking the
 * sound manager about our own instance says it is gone, and a new copy started on top of the old every couple of seconds
 * until the sound engine ran out of channels. The unit tests have no game, so this reads the source: the one question to
 * the sound manager is {@link MachineLoopSound}'s, which asks about the wrapper.
 */
class SoundStillPlayingTest {

	private static final Pattern ASKS = Pattern.compile("\\.isActive\\(");

	@Test
	void onlyOnePlaceAsksTheSoundManagerWhetherASoundIsStillPlaying() throws IOException {
		Path root = Path.of("src/main/java/com/createengines/content");
		List<String> asking = new ArrayList<>();
		try (Stream<Path> walk = Files.walk(root)) {
			for (Path path : walk.filter(p -> p.toString()
				.endsWith(".java"))
				.sorted()
				.toList()) {
				Matcher m = ASKS.matcher(Files.readString(path));
				while (m.find())
					asking.add(path.getFileName()
						.toString());
			}
		}
		assertEquals(List.of("MachineLoopSound.java"), asking,
			"ask MachineLoopSound.stillPlaying, or the sound stacks on a moving structure");
	}

	@Test
	void thatPlaceAsksAboutWhatSablePlaysTheSoundAs() throws IOException {
		String source = Files.readString(Path.of("src/main/java/com/createengines/content/MachineLoopSound.java"));
		assertTrue(source.contains("sounds.isActive((SoundInstance) SableCompat.playedAs(sound))"), source);
	}
}
