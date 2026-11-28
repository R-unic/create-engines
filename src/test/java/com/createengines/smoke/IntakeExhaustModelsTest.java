package com.createengines.smoke;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.createengines.smoke.PrecisionHeadZFightTest.Box;

import java.io.IOException;
import java.nio.file.Files;
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

	/** Copper pipe that joins the intake wears the intake's own textures, laid in patches, not plain copper. */
	@Test
	void theIntakePipesWearTheirOwnTextures() throws IOException {
		for (String part : new String[] {"intake_manifold", "throttle", "maf_sensor"}) {
			String arm = Files.readString(MODELS.resolve(part).resolve("arm_east.json"));
			assertTrue(arm.contains("\"pipe\": \"create_engines:block/intake_pipe\""), part);
			assertTrue(arm.contains("\"flange\": \"create_engines:block/intake_flange\""), part);
			assertTrue(arm.contains("\"uv\""), part + " lays no patches");
		}
		assertTrue(Files.readString(MODELS.resolve("air_intake").resolve("block.json")).contains("block/intake_bell"));
	}
}
