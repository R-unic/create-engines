package com.createengines.smoke;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.createengines.smoke.PrecisionHeadZFightTest.Box;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * The intake and exhaust parts that sit in a pipe run along one axis ({@code tools/models/fittings.py}): each reaches
 * both ends of its block, where the pipes join, and shows no flickering faces. They used to draw their stubs on top of
 * their end plates.
 */
class IntakeExhaustModelsTest {

	private static final Path MODELS = Path.of("src/main/resources/assets/create_engines/models/block");
	private static final String[] IN_LINE = {"intercooler", "air_filter", "muffler", "soot_trap"};

	private static List<Box> boxes(String part) throws IOException {
		return PrecisionHeadZFightTest.read(MODELS.resolve(part).resolve("block.json"));
	}

	@Test
	void noFacesOnAnInLinePartShareAPlane() throws IOException {
		for (String part : IN_LINE) {
			List<String> found = PrecisionHeadZFightTest.fights(boxes(part));
			assertTrue(found.isEmpty(), part + " has z-fighting faces: " + found);
		}
	}

	@Test
	void everyInLinePartFillsItsBlockFromEndToEnd() throws IOException {
		for (String part : IN_LINE) {
			List<Box> boxes = boxes(part);
			assertEquals(0, boxes.stream().mapToDouble(b -> b.from()[1]).min().orElseThrow(), 1e-6, part);
			assertEquals(16, boxes.stream().mapToDouble(b -> b.to()[1]).max().orElseThrow(), 1e-6, part);
		}
	}
}
