package com.createengines.forge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import com.createengines.engine.sim.CamProfile;
import com.createengines.engine.sim.CamProfiles;
import com.createengines.engine.sim.PartEffects;
import com.createengines.engine.sim.PartStats;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

class DataMapFileTest {

	private static final Path DATA = Path.of("..", "src", "main", "resources", "data", "create_engines");

	private static JsonElement file(String relative) throws IOException {
		return JsonParser.parseString(Files.readString(DATA.resolve(relative)));
	}

	@Test
	void theShippedPartEffectFilesAreTheBuiltInValues() throws IOException {
		for (PartEffects.Part part : PartEffects.Part.values())
			assertEquals(PartEffects.BUILT_IN.get(part),
				DataMapFile.partEffect(file("part_effects/" + part.fileName() + ".json")), part.fileName());
	}

	@Test
	void theShippedCamProfilesAreTheBuiltInAngles() throws IOException {
		for (CamProfiles.Kind kind : CamProfiles.Kind.values()) {
			CamProfile read = DataMapFile.camProfile(kind, file("cam_profiles/" + kind.fileName() + ".json"));
			assertEquals(kind.builtIn(), read, kind.fileName());
		}
	}

	@Test
	void aPartStatsEntryNamesOnlyWhatItChanges() {
		JsonObject entry = JsonParser.parseString("{\"compression_ratio\": 1.2, \"inertia\": 0.5}").getAsJsonObject();
		assertEquals(new PartStats(1.2, 1, 1, 1, 1, 1, 1, 1, 0.5, 1, 1), DataMapFile.partStats(entry));
		assertEquals(PartStats.NONE, DataMapFile.partStats(new JsonObject()));
	}

	@Test
	void aScaleOutOfRangeOrNotANumberIsRefused() {
		for (String bad : new String[] {"{\"inertia\": 11}", "{\"inertia\": 0.05}", "{\"inertia\": \"big\"}"})
			assertThrows(IllegalArgumentException.class,
				() -> DataMapFile.partStats(JsonParser.parseString(bad).getAsJsonObject()), bad);
	}

	@Test
	void anUnknownPartEffectKeyIsRefusedSoATypoIsFound() {
		assertThrows(IllegalArgumentException.class,
			() -> DataMapFile.partEffect(JsonParser.parseString("{\"compresion_ratio\": 1.2}")));
	}
}
