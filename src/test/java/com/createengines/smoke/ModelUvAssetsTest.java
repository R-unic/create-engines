package com.createengines.smoke;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * No face of any model in the jar may show a patch of its texture that runs more than half a pixel outside the texture
 * ({@link UvBounds}). A part that pokes out of its block (a pushrod, a rocker, a plug) has default texture coordinates
 * beyond 0 to 16, which sample the sprite next to it in the atlas and show as stray colour or the purple and black of the
 * missing texture, in some packs and not in others. The 1.4.0 pass moved the 22 models that did this back inside their
 * textures ({@code tools/generate_models.py}); this keeps them there and catches the next one when a model is added.
 *
 * <p>Fix a failure in the generator ({@code WRAP_UV}, {@code WRAP_MIN_OVERRUN}) or give the face a {@code uv}, never by
 * widening the tolerance.
 */
class ModelUvAssetsTest {

	private static final Path MODELS = Path.of("src/main/resources/assets/create_engines/models");

	private static double[] numbers(JsonArray array) {
		double[] out = new double[array.size()];
		for (int i = 0; i < out.length; i++)
			out[i] = array.get(i)
				.getAsDouble();
		return out;
	}

	/** Every {@code file: element face (overrun)} for faces outside the tolerance, and how many faces were looked at. */
	private static int scan(Path file, JsonObject model, List<String> problems) {
		int faces = 0;
		if (!model.has("elements"))
			return 0;
		for (JsonElement element : model.getAsJsonArray("elements")) {
			JsonObject box = element.getAsJsonObject();
			double[] from = numbers(box.getAsJsonArray("from"));
			double[] to = numbers(box.getAsJsonArray("to"));
			for (Map.Entry<String, JsonElement> entry : box.getAsJsonObject("faces")
				.entrySet()) {
				JsonObject face = entry.getValue()
					.getAsJsonObject();
				double[] patch = face.has("uv") ? numbers(face.getAsJsonArray("uv"))
					: UvBounds.defaultPatch(from, to, entry.getKey());
				faces++;
				double overrun = UvBounds.patchOverrun(patch);
				if (UvBounds.exceeds(overrun))
					problems.add(MODELS.relativize(file) + ": " + entry.getKey() + " face of the element " + java.util.Arrays.toString(from)
						+ " to " + java.util.Arrays.toString(to) + " runs " + overrun + " pixels outside its texture");
			}
		}
		return faces;
	}

	@Test
	void noFaceRunsOutsideItsTexture() throws IOException {
		List<String> problems = new ArrayList<>();
		int models = 0;
		int faces = 0;
		try (Stream<Path> files = Files.walk(MODELS)) {
			for (Path file : (Iterable<Path>) files.filter(p -> p.toString()
				.endsWith(".json"))::iterator) {
				try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
					JsonObject model = JsonParser.parseReader(reader)
						.getAsJsonObject();
					if (model.has("elements"))
						models++;
					faces += scan(file, model, problems);
				}
			}
		}
		assertTrue(models > 100, "found only " + models + " models with elements: is the path wrong?");
		assertTrue(faces > 1000, "looked at only " + faces + " faces");
		assertEquals(List.of(), problems, problems.size() + " faces run outside their textures");
	}

	/** The check itself works: the 1.3 rocker, as it was shipped, is caught. */
	@Test
	void theRockerAsShippedInOnePointThreeWouldHaveFailed() {
		String json = "{\"elements\":[{\"from\":[6.75,3.25,14],\"to\":[9.25,4.75,20.5],\"faces\":{\"west\":{\"texture\":\"#arm\"},"
			+ "\"north\":{\"texture\":\"#arm\"}}}]}";
		List<String> problems = new ArrayList<>();
		int faces = scan(MODELS.resolve("rocker.json"), JsonParser.parseString(json)
			.getAsJsonObject(), problems);
		assertEquals(2, faces);
		assertEquals(1, problems.size(), problems.toString());
		assertTrue(problems.get(0)
			.contains("west face"), problems.get(0));
	}

	@Test
	void aFaceWithItsOwnUvIsJudgedByThatUv() {
		String json = "{\"elements\":[{\"from\":[6.75,3.25,14],\"to\":[9.25,4.75,20.5],\"faces\":{\"west\":{\"texture\":\"#arm\","
			+ "\"uv\":[0,0,16,16]},\"east\":{\"texture\":\"#arm\",\"uv\":[0,0,17,16]}}}]}";
		List<String> problems = new ArrayList<>();
		scan(MODELS.resolve("x.json"), JsonParser.parseString(json)
			.getAsJsonObject(), problems);
		assertEquals(1, problems.size(), problems.toString());
		assertTrue(problems.get(0)
			.contains("east face"), problems.get(0));
	}
}
