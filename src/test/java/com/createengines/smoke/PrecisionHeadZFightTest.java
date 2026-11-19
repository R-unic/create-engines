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

import org.junit.jupiter.api.Test;

/**
 * Two faces that point the same way, lie in the same plane and overlap fight over the pixels (z-fighting). The precision
 * head's plug and injector were first drawn for the standard head and sat flush with the cam covers and bolts on this one.
 * Fix a failure in {@code tools/generate_models.py}, never by loosening {@link #EPS}.
 */
class PrecisionHeadZFightTest {

	private static final Path DIR = Path.of("src/main/resources/assets/create_engines/models/block/precision_cylinder_head");
	private static final double EPS = 1e-6;
	private static final String[] FACES = {"north", "south", "east", "west", "up", "down"};

	record Box(double[] from, double[] to) {
	}

	private static double[] numbers(JsonArray a) {
		double[] out = new double[a.size()];
		for (int i = 0; i < out.length; i++)
			out[i] = a.get(i).getAsDouble();
		return out;
	}

	/** The axis a face is perpendicular to, and the sign of its normal. */
	private static int axis(String face) {
		return switch (face) {
			case "east", "west" -> 0;
			case "up", "down" -> 1;
			default -> 2;
		};
	}

	private static boolean positive(String face) {
		return face.equals("east") || face.equals("up") || face.equals("south");
	}

	static List<String> fights(List<Box> boxes) {
		List<String> out = new ArrayList<>();
		for (String face : FACES) {
			int a = axis(face);
			int u = (a + 1) % 3, v = (a + 2) % 3;
			for (int i = 0; i < boxes.size(); i++)
				for (int j = i + 1; j < boxes.size(); j++) {
					Box p = boxes.get(i), q = boxes.get(j);
					double pp = positive(face) ? p.to[a] : p.from[a];
					double qp = positive(face) ? q.to[a] : q.from[a];
					if (Math.abs(pp - qp) > EPS)
						continue;
					double ou = Math.min(p.to[u], q.to[u]) - Math.max(p.from[u], q.from[u]);
					double ov = Math.min(p.to[v], q.to[v]) - Math.max(p.from[v], q.from[v]);
					if (ou > EPS && ov > EPS)
						out.add(face + " of element " + i + " and " + j + " at " + pp);
				}
		}
		return out;
	}

	static List<Box> read(Path file) throws IOException {
		try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			JsonObject model = JsonParser.parseReader(r).getAsJsonObject();
			List<Box> boxes = new ArrayList<>();
			for (JsonElement e : model.getAsJsonArray("elements")) {
				JsonObject o = e.getAsJsonObject();
				boxes.add(new Box(numbers(o.getAsJsonArray("from")), numbers(o.getAsJsonArray("to"))));
			}
			return boxes;
		}
	}

	@Test
	void noFacesOnThePrecisionHeadModelsShareAPlane() throws IOException {
		for (String name : new String[] {"block", "block_injector", "block_plug", "block_injector_plug"}) {
			List<String> found = fights(read(DIR.resolve(name + ".json")));
			assertTrue(found.isEmpty(), name + " has z-fighting faces: " + found);
		}
	}

	@Test
	void theCheckSeesTwoFlushBoxes() {
		List<Box> flush = List.of(new Box(new double[] {0, 0, 0}, new double[] {4, 4, 4}),
			new Box(new double[] {1, 2, 1}, new double[] {3, 4, 3}));
		assertEquals(1, fights(flush).size());
		List<Box> clear = List.of(new Box(new double[] {0, 0, 0}, new double[] {4, 4, 4}),
			new Box(new double[] {1, 2, 1}, new double[] {3, 4.5, 3}));
		assertEquals(0, fights(clear).size());
	}
}
