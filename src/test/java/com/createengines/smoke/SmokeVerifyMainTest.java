package com.createengines.smoke;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The command line Gradle runs after every smoke and GameTest run. A verdict that cannot be read, or an argument that
 * is misspelt, has to stop the build and never pass quietly. (The failing path ends the JVM with exit code 1, so it is
 * covered through {@code verify} and {@code verifyGameTests}, in {@link SmokeReportAndVerifyTest}.)
 */
class SmokeVerifyMainTest {

	@TempDir
	Path dir;

	private Path write(String name, String text) throws IOException {
		Path file = dir.resolve(name);
		Files.createDirectories(file.getParent());
		Files.writeString(file, text);
		return file;
	}

	@Test
	void aGameTestLogThatSaysEveryTestPassedIsAccepted() throws IOException {
		Path log = write("gametest.log", "[10:00:00] [Server thread/INFO] [minecraft/GameTestServer]: All 305 required tests passed :)\n");
		assertDoesNotThrow(() -> SmokeVerify.main(new String[] {"--gametest", "--run", "gt", "--log", log.toString()}));
	}

	@Test
	void aGameTestRunNeedsItsLog() {
		assertThrows(IllegalArgumentException.class, () -> SmokeVerify.main(new String[] {"--gametest"}));
	}

	@Test
	void aMisspeltArgumentStopsTheBuild() {
		assertThrows(IllegalArgumentException.class, () -> SmokeVerify.main(new String[] {"--dri", "x"}));
	}

	@Test
	void aRunWithNoReportFolderAndNoLogOnlyFlagIsRefused() {
		assertThrows(IllegalArgumentException.class, () -> SmokeVerify.main(new String[] {"--run", "smoke"}));
	}

	@Test
	void aCleanLogAloneIsAcceptedWithLogOnly() throws IOException {
		Path log = write("game.log", "[10:00:00] [main/INFO] [minecraft/Minecraft]: Setting user: Dev\n");
		assertDoesNotThrow(() -> SmokeVerify.main(new String[] {"--log-only", "--run", "shots", "--log", log.toString()}));
	}

	@Test
	void aCompletedReportAndACleanLogPassAndTheVerifiedReportIsWrittenBesideIt() throws IOException {
		Path report = dir.resolve("client");
		write("client/result.properties", "completed=true\npasses=3\nfailures=0\n");
		Path log = write("game.log", "[10:00:00] [main/INFO] [minecraft/Minecraft]: Setting user: Dev\n");
		Path allow = write("allow.txt", "");
		assertDoesNotThrow(() -> SmokeVerify.main(new String[] {"--run", "client", "--dir", report.toString(), "--log", log.toString(),
			"--allow", allow.toString(), "--lenient-log"}));
		assertTrue(Files.isDirectory(dir.resolve("client-verified")), "the merged report should be written beside the game's own");
	}
}
