package com.createengines.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * The weights this mod gives its blocks on a moving structure (Create Aeronautics, through Sable), read from the data files
 * that ship. Sable reads {@code data/sable/physics_block_properties}; each file names a block tag of ours and a mass. A file
 * Sable cannot read, a tag naming a block that does not exist or two weights for one block would not fail anything at build
 * time, so they are held here.
 */
class SableWeightsTest {

	private static final Path DATA = Path.of("src/main/resources/data");
	private static final Path BLOCKSTATES = Path.of("src/generated/resources/assets/create_engines/blockstates");
	private static final String PREFIX = "create_engines_";

	private static JsonObject read(Path file) throws IOException {
		try (Reader in = Files.newBufferedReader(file)) {
			return JsonParser.parseReader(in).getAsJsonObject();
		}
	}

	/** Tag name (after {@code weight/}) to mass, from Sable's files. */
	private static Map<String, Double> masses() throws IOException {
		Map<String, Double> masses = new HashMap<>();
		try (Stream<Path> files = Files.list(DATA.resolve("sable/physics_block_properties"))) {
			for (Path file : (Iterable<Path>) files.filter(f -> f.getFileName().toString().startsWith(PREFIX))::iterator) {
				JsonObject json = read(file);
				String selector = json.get("selector").getAsString();
				assertTrue(selector.startsWith("#create_engines:weight/"), file + " must select one of our weight tags");
				String name = selector.substring("#create_engines:weight/".length());
				assertEquals(PREFIX + name + ".json", file.getFileName().toString(), "file named for its tag");
				JsonElement mass = json.getAsJsonObject("properties").get("sable:mass");
				assertNotNull(mass, file + " sets no mass");
				assertTrue(mass.getAsDouble() > 0 && mass.getAsDouble() <= 16, file + " mass out of a sane range");
				assertEquals(1, json.getAsJsonObject("properties").size(), file + " sets only mass, which is all that is meant to move");
				assertFalse(json.has("overrides"), file + " has block-state overrides, which nothing here needs");
				masses.put(name, mass.getAsDouble());
			}
		}
		return masses;
	}

	private static Set<String> blocksOf(String tag) throws IOException {
		Set<String> blocks = new HashSet<>();
		for (JsonElement value : read(DATA.resolve("create_engines/tags/block/weight/" + tag + ".json")).getAsJsonArray("values")) {
			String id = value.getAsString();
			assertTrue(id.startsWith("create_engines:"), tag + " lists a block that is not ours: " + id);
			assertTrue(Files.exists(BLOCKSTATES.resolve(id.substring("create_engines:".length()) + ".json")), tag + " lists a block that does not exist: " + id);
			assertTrue(blocks.add(id), tag + " lists " + id + " twice");
		}
		return blocks;
	}

	@Test
	void everyWeightFileHasATagAndEveryTagAFile() throws IOException {
		Map<String, Double> masses = masses();
		assertFalse(masses.isEmpty());
		Set<String> tags = new HashSet<>();
		try (Stream<Path> files = Files.list(DATA.resolve("create_engines/tags/block/weight"))) {
			files.forEach(f -> tags.add(f.getFileName().toString().replace(".json", "")));
		}
		assertEquals(masses.keySet(), tags);
	}

	@Test
	void noBlockHasTwoWeights() throws IOException {
		Set<String> seen = new HashSet<>();
		for (String tag : masses().keySet())
			for (String block : blocksOf(tag))
				assertTrue(seen.add(block), block + " is in more than one weight tag");
	}

	/**
	 * The roadmap says a rotary earns its place in a plane. Swept volume fired per turn of the shaft: a piston cylinder fires once
	 * in two turns, a housing's three chambers once a turn each. Per unit of mass the housing must give clearly more than a
	 * cylinder, or the weights would need a new key (a change to existing weights is not allowed in 2.x).
	 */
	@Test
	void aRotorHousingMakesMoreFiringPerKilogramThanACylinder() throws IOException {
		Map<String, Double> masses = masses();
		double cylinderPerTurn = com.createengines.engine.sim.CylinderSpec.CRUDE.displacement() / 2;
		double housingPerTurn = 3 * com.createengines.engine.sim.CylinderSpec.ROTARY.displacement()
			/ (com.createengines.engine.sim.CylinderModel.ROTARY_CYCLE / com.createengines.engine.sim.CylinderModel.ROTARY_CHAMBER_SPACING);
		double cylinder = cylinderPerTurn / masses.get("heavy");
		double housing = housingPerTurn / masses.get("medium");
		assertTrue(housing > 1.5 * cylinder, "a housing should give at least 1.5 times a cylinder's firing per unit of mass: " + housing + " against " + cylinder);
	}

	@Test
	void theWeightsSayWhatTheyAreMeantTo() throws IOException {
		Map<String, Double> masses = masses();
		// A block is 1.0 in Sable. The point is a real choice of power for weight: a rotary is lighter than a piston engine's cylinder,
		// the pipework is nearly nothing, and the flywheel is the heaviest thing on the crank (Create's own flywheel is 4.0).
		assertTrue(blocksOf("heavy").contains("create_engines:cylinder"));
		assertTrue(blocksOf("heavy").contains("create_engines:crankshaft"));
		assertTrue(blocksOf("medium").contains("create_engines:rotor_housing"));
		assertTrue(masses.get("medium") < masses.get("heavy"), "a rotor housing weighs less than a cylinder");
		assertTrue(blocksOf("light").contains("create_engines:exhaust_manifold"));
		assertTrue(masses.get("light") < 1.0);
		assertTrue(blocksOf("flywheel").contains("create_engines:cast_flywheel"));
		assertTrue(blocksOf("flywheel").contains("create_engines:dual_mass_flywheel"));
		assertEquals(4.0, masses.get("flywheel"), 0);
		assertTrue(masses.get("flywheel") > masses.get("heavy"));
	}
}
