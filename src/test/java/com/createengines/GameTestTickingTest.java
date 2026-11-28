package com.createengines;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/** GameTests use {@code EveryTick}: the vanilla onEachTick costs the square of the timeout. */
class GameTestTickingTest {

	@Test
	void noGameTestUsesTheVanillaOnEachTick() throws IOException {
		try (Stream<Path> files = Files.walk(Path.of("src/main/java/com/createengines/gametest"))) {
			List<String> users = files.filter(file -> file.toString()
					.endsWith(".java"))
				.filter(file -> read(file).contains(".onEachTick("))
				.map(file -> file.getFileName()
					.toString())
				.toList();
			assertEquals(List.of(), users, "use EveryTick.run(helper, task)");
		}
	}

	private static String read(Path file) {
		try {
			return Files.readString(file);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
