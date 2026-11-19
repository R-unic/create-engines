package com.createengines.smoke;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.createengines.smoke.PrecisionHeadZFightTest.Box;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * The air intake and exhaust outlet models ({@code tools/generate_models.py}, {@code air_intake()} and
 * {@code exhaust_outlet()}). They were redrawn in 2.0 as a look change only: the blocks, their shapes, facing and what
 * they connect to stayed as they were. These pin what the rest of the mod relies on, so a later tweak to the art cannot
 * quietly break it. Fix a failure in the generator, never by widening a bound here.
 */
class PortModelsTest {

	private static final Path ASSETS = Path.of("src/main/resources/assets/create_engines");
	private static final Path MODELS = ASSETS.resolve("models/block");
	private static final double EPS = 1e-6;
	private static final String[] PORTS = {"air_intake", "exhaust_outlet"};

	private static Path model(String port) {
		return MODELS.resolve(port).resolve("block.json");
	}

	private static List<Box> boxes(String port) throws IOException {
		return PrecisionHeadZFightTest.read(model(port));
	}

	private static JsonObject json(String port) throws IOException {
		try (Reader r = Files.newBufferedReader(model(port), StandardCharsets.UTF_8)) {
			return JsonParser.parseReader(r).getAsJsonObject();
		}
	}

	@Test
	void noFacesOnEitherPortShareAPlane() throws IOException {
		for (String port : PORTS) {
			List<String> found = PrecisionHeadZFightTest.fights(boxes(port));
			assertTrue(found.isEmpty(), port + " has z-fighting faces: " + found);
		}
	}

	/** Both are drawn mouth-up and turned by the blockstate, so each stays inside its block and rests on its back face. */
	@Test
	void eachPortStaysInsideTheBlockAndStartsAtTheBackFace() throws IOException {
		for (String port : PORTS) {
			double lowest = 16;
			for (Box b : boxes(port)) {
				for (int a = 0; a < 3; a++)
					assertTrue(b.from()[a] >= -EPS && b.to()[a] <= 16 + EPS, port + " has an element outside the block");
				lowest = Math.min(lowest, b.from()[1]);
			}
			assertEquals(0, lowest, EPS, port + " does not start at the back face, where its pipe joins");
		}
	}

	/** The pipes' own flange is 9 by 9 pixels at the middle of the block; each port's back plate must be at least that wide. */
	@Test
	void theBackPlateIsAtLeastAsWideAsThePipeFlange() throws IOException {
		for (String port : PORTS) {
			boolean covers = boxes(port).stream().anyMatch(b -> b.from()[1] == 0 && b.from()[0] <= 3.5 && b.to()[0] >= 12.5
				&& b.from()[2] <= 3.5 && b.to()[2] >= 12.5);
			assertTrue(covers, port + " has no back plate covering the pipe joint");
		}
	}

	/** The rain cap sits on the outlet's lip, and is modelled for a lip 9 by 9 pixels at y 15 to 16. */
	@Test
	void theExhaustLipIsWhereTheRainCapSits() throws IOException {
		List<Box> lip = boxes("exhaust_outlet").stream().filter(b -> b.to()[1] > 15 + EPS && b.from()[1] >= 15 - EPS).toList();
		double minX = 16, maxX = 0, minZ = 16, maxZ = 0, top = 0;
		for (Box b : lip) {
			if (b.to()[1] - b.from()[1] < 0.5)
				continue; // the dark bore plane inside the lip
			minX = Math.min(minX, b.from()[0]);
			maxX = Math.max(maxX, b.to()[0]);
			minZ = Math.min(minZ, b.from()[2]);
			maxZ = Math.max(maxZ, b.to()[2]);
			top = Math.max(top, b.to()[1]);
		}
		assertEquals(3.5, minX, EPS);
		assertEquals(12.5, maxX, EPS);
		assertEquals(3.5, minZ, EPS);
		assertEquals(12.5, maxZ, EPS);
		assertEquals(16, top, EPS);
		for (Box b : boxes("exhaust_outlet"))
			if (b.from()[1] >= 15 - EPS && b.to()[1] - b.from()[1] >= 0.5)
				assertTrue(b.from()[0] >= 3.5 - EPS && b.to()[0] <= 12.5 + EPS && b.from()[2] >= 3.5 - EPS
					&& b.to()[2] <= 12.5 + EPS, "something at the lip is outside the rim the rain cap fits");
	}

	/** Air must be seen to go in and gas to come out: the mouth is open, not a lid, with only a grille and a brace across it. */
	@Test
	void theIntakeMouthIsOpenApartFromItsGrilleAndBrace() throws IOException {
		double[][] openings = {{5, 5}, {11, 5}, {5, 11}, {11, 11}};
		for (double[] p : openings) {
			for (Box b : boxes("air_intake")) {
				boolean over = b.to()[1] > 13.1 + EPS || (b.from()[1] > 12.3 && b.to()[1] - b.from()[1] > 0.5);
				boolean covers = p[0] > b.from()[0] && p[0] < b.to()[0] && p[1] > b.from()[2] && p[1] < b.to()[2];
				assertFalse(over && covers, "something solid fills the mouth at " + p[0] + ", " + p[1]);
			}
		}
	}

	@Test
	void theExhaustBoreIsOpenAtTheLip() throws IOException {
		for (Box b : boxes("exhaust_outlet")) {
			boolean covers = 8 > b.from()[0] && 8 < b.to()[0] && 8 > b.from()[2] && 8 < b.to()[2];
			assertFalse(covers && b.to()[1] > 15.1 + EPS, "something fills the bore above the lip's inside plane");
		}
	}

	/** The intake still has a see-through grille, so its model must stay cutout; the exhaust has nothing to see through. */
	@Test
	void theIntakeIsCutoutAndUsesTheGrille() throws IOException {
		JsonObject intake = json("air_intake");
		assertEquals("minecraft:cutout", intake.get("render_type").getAsString());
		assertEquals("create_engines:block/grille", intake.getAsJsonObject("textures").get("grille").getAsString());
	}

	/** Every texture a port names is a real file (ours) or Create's own: a typo shows as a purple-and-black block in game. */
	@Test
	void everyTextureANamedPortModelUsesExists() throws IOException {
		for (String port : PORTS) {
			JsonObject model = json(port);
			JsonObject textures = model.getAsJsonObject("textures");
			for (Map.Entry<String, JsonElement> e : textures.entrySet()) {
				String id = e.getValue().getAsString();
				if (id.startsWith("create_engines:"))
					assertTrue(Files.exists(ASSETS.resolve("textures").resolve(id.substring("create_engines:".length()) + ".png")),
						port + " names a missing texture " + id);
				else
					assertTrue(id.startsWith("create:") || id.startsWith("minecraft:"), port + " uses an unexpected texture " + id);
			}
			for (JsonElement el : model.getAsJsonArray("elements"))
				for (Map.Entry<String, JsonElement> face : el.getAsJsonObject().getAsJsonObject("faces").entrySet()) {
					String ref = face.getValue().getAsJsonObject().get("texture").getAsString();
					assertTrue(ref.startsWith("#") && textures.has(ref.substring(1)), port + " face uses undefined " + ref);
				}
		}
	}
}
