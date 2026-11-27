package com.createengines.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import com.createengines.engine.sim.Wear;

import org.junit.jupiter.api.Test;

/** Every kind of connecting rod has its own look in the cylinder, and the cylinder draws the one that is fitted. */
class CylinderRodModelTest {

	private static final Path MODELS = Path.of("src/main/resources/assets/create_engines/models/block/cylinder");

	@Test
	void eachRodKindHasItsOwnModel() throws IOException {
		Set<String> models = new HashSet<>();
		for (Wear.Rod kind : Wear.Rod.values()) {
			String name = kind == Wear.Rod.STANDARD ? "rod" : kind.name().toLowerCase(Locale.ROOT) + "_rod";
			models.add(Files.readString(MODELS.resolve(name + ".json")));
		}
		assertEquals(Wear.Rod.values().length, models.size(), "two rod kinds look the same in the cylinder");
	}

	@Test
	void bothWaysOfDrawingACylinderAskForTheFittedRod() throws IOException {
		for (String file : new String[] {"cylinder/CylinderVisual.java", "EnginePartRenderers.java"}) {
			String source = Files.readString(Path.of("src/main/java/com/createengines/content", file));
			assertTrue(source.contains("CEPartialModels.rod("), file + " does not choose the rod model by kind");
		}
	}
}
