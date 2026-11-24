package com.createengines.forge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.createengines.engine.sim.FuelSpec;

class FuelFilesTest {

	/** The main build's own file, the one a 1.21.1 pack ships, read by the port's reader. */
	private static Map<String, FuelSpec> mainFile() throws IOException {
		try (Reader in = Files.newBufferedReader(
			Path.of("..", "src", "main", "resources", "data", "create_engines", "data_maps", "fluid", "fuel.json"))) {
			return FuelFiles.read(in);
		}
	}

	@Test
	void everyFuelTheMainBuildShipsMeansTheSameHere() throws IOException {
		Map<String, FuelSpec> fuels = mainFile();
		assertTrue(fuels.size() >= 5, "the main build's fuel file has its fuels");
		for (Map.Entry<String, FuelSpec> fuel : fuels.entrySet()) {
			String name = fuel.getKey().substring(fuel.getKey().indexOf(':') + 1).toUpperCase();
			FuelSpec builtIn;
			try {
				builtIn = (FuelSpec) FuelSpec.class.getField(name).get(null);
			} catch (ReflectiveOperationException e) {
				continue; // a fuel with no constant in the simulation
			}
			assertEquals(builtIn, fuel.getValue(), fuel.getKey());
		}
	}

	@Test
	void leftOutFieldsTakeTheMainBuildsDefaults() throws IOException {
		FuelSpec fuel = FuelFiles.read(new StringReader(
			"{\"values\":{\"x:y\":{\"energy_per_mb\":10,\"autoignition_temperature\":2,\"air_per_mb\":1.5}}}")).get("x:y");
		assertEquals(new FuelSpec(10, 2, 1.5, 1, 0, 1, 2, 1, 1), fuel);
	}

	@Test
	void aMissingRequiredNumberIsRefused() {
		assertThrows(IOException.class, () -> FuelFiles.read(new StringReader(
			"{\"values\":{\"x:y\":{\"energy_per_mb\":10}}}")));
		assertThrows(IOException.class, () -> FuelFiles.read(new StringReader("[]")));
	}
}
