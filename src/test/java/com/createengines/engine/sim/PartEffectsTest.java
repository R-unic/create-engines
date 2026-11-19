package com.createengines.engine.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Parts as data: the shipped files are the built-in values, a pack's file changes exactly what it names. */
class PartEffectsTest {

	private static final Path DIRECTORY = Path.of("src/main/resources/data/create_engines/part_effects");

	@BeforeEach
	@AfterEach
	void builtIn() {
		PartEffects.reset();
		Tuning.reset();
	}

	private static Map<String, Double> read(PartEffects.Part part) throws IOException {
		Map<String, Double> values = new HashMap<>();
		Matcher match = Pattern.compile("\"([a-z_]+)\"\\s*:\\s*([0-9.]+)")
			.matcher(Files.readString(DIRECTORY.resolve(part.fileName() + ".json")));
		while (match.find())
			values.put(match.group(1), Double.parseDouble(match.group(2)));
		return values;
	}

	@Test
	void theShippedFilesAreExactlyTheBuiltInValues() throws IOException {
		for (PartEffects.Part part : PartEffects.Part.values())
			assertEquals(PartEffects.BUILT_IN.get(part), PartEffects.parse(read(part)), part + ".json drifted from the built-in effect");
	}

	@Test
	void everyPartHasAFileAndNoStrayFilesExist() throws IOException {
		try (var files = Files.list(DIRECTORY)) {
			assertEquals(PartEffects.Part.values().length, files.count());
		}
	}

	@Test
	void settingNothingChangesNothing() {
		CylinderSpec before = CylinderSpec.build(true, true, true, true);
		PartEffects.set(Map.of());
		assertEquals(before, CylinderSpec.build(true, true, true, true));
	}

	@Test
	void aPartNamedInTheDataChangesOnlyWhatItsFileSays() {
		PartEffects.set(Map.of(PartEffects.Part.BORE, new PartEffects.Effect(2, 1, 1, 1, 1, 1, 1, 1)));
		assertEquals(CylinderSpec.CRUDE.compressionRatio() * 2, CylinderSpec.build(true, false, false, false).compressionRatio(), 1e-9);
		assertEquals(CylinderSpec.CRUDE.intakeFlow(), CylinderSpec.build(true, false, false, false).intakeFlow(), 0);
		assertEquals(CylinderSpec.CRUDE.compressionRatio(), CylinderSpec.build(false, true, true, true).compressionRatio(), 0);
	}

	@Test
	void aPartLeftOutOfThePackKeepsItsBuiltInValues() {
		PartEffects.set(Map.of(PartEffects.Part.HEAD, new PartEffects.Effect(1, 3, 1, 1, 1, 1, 1, 1)));
		assertEquals(PartEffects.BUILT_IN.get(PartEffects.Part.BORE), PartEffects.of(PartEffects.Part.BORE));
		assertEquals(3, PartEffects.of(PartEffects.Part.HEAD).breathing());
	}

	@Test
	void anEffectIsStillBlendedByTheServersPrecisionBonus() {
		PartEffects.set(Map.of(PartEffects.Part.INJECTOR, new PartEffects.Effect(1, 1, 1, 1, 1, 1, 1, 2)));
		Tuning.precisionBonus = 0.5;
		assertEquals(1.5, CylinderSpec.build(false, false, false, true).injectorFlow(), 1e-12);
		Tuning.precisionBonus = 0;
		assertEquals(1, CylinderSpec.build(false, false, false, true).injectorFlow(), 0);
	}

	@Test
	void aRotaryChamberTakesTheInjectorEffectToo() {
		PartEffects.set(Map.of(PartEffects.Part.INJECTOR, new PartEffects.Effect(1, 1, 1, 1, 1, 1, 1, 2)));
		assertEquals(2, CylinderSpec.buildRotary(true).injectorFlow(), 1e-12);
		assertEquals(1, CylinderSpec.buildRotary(false).injectorFlow(), 0);
	}

	@Test
	void leftOutKeysMeanOneAndTheRangeIsChecked() {
		assertEquals(PartEffects.Effect.NONE, PartEffects.parse(Map.of()));
		assertEquals(1.7, PartEffects.parse(Map.of("breathing", 1.7)).breathing());
		assertThrows(IllegalArgumentException.class, () -> PartEffects.parse(Map.of("breathing", 0.0)));
		assertThrows(IllegalArgumentException.class, () -> PartEffects.parse(Map.of("breathing", 11.0)));
		assertThrows(IllegalArgumentException.class, () -> PartEffects.parse(Map.of("inertia", Double.NaN)));
		assertThrows(IllegalArgumentException.class, () -> PartEffects.parse(Map.of("inertia", Double.POSITIVE_INFINITY)));
	}

	@Test
	void aMisspeltKeyIsRefusedNotIgnored() {
		IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
			() -> PartEffects.parse(Map.of("compresion_ratio", 1.2)));
		assertTrue(e.getMessage().contains("compresion_ratio") && e.getMessage().contains("compression_ratio"), e.getMessage());
	}

	@Test
	void everyKeyReachesItsStat() {
		PartEffects.Effect all = PartEffects.parse(Map.of("compression_ratio", 2.0, "breathing", 3.0, "ignition_delay", 4.0,
			"burn_duration", 5.0, "friction_static", 6.0, "friction_viscous", 7.0, "inertia", 8.0, "injector_flow", 9.0));
		PartEffects.set(Map.of(PartEffects.Part.BORE, all, PartEffects.Part.HEAD, PartEffects.Effect.NONE,
			PartEffects.Part.PISTON, PartEffects.Effect.NONE, PartEffects.Part.INJECTOR, PartEffects.Effect.NONE));
		CylinderSpec built = CylinderSpec.build(true, false, false, false);
		CylinderSpec crude = CylinderSpec.CRUDE;
		assertEquals(crude.compressionRatio() * 2, built.compressionRatio(), 1e-9);
		assertEquals(crude.intakeFlow() * 3, built.intakeFlow(), 1e-9);
		assertEquals(crude.exhaustFlow() * 3, built.exhaustFlow(), 1e-9);
		assertEquals(crude.ignitionDelay() * 4, built.ignitionDelay(), 1e-9);
		assertEquals(crude.burnDuration() * 5, built.burnDuration(), 1e-9);
		assertEquals(crude.frictionStatic() * 6, built.frictionStatic(), 1e-9);
		assertEquals(crude.frictionViscous() * 7, built.frictionViscous(), 1e-9);
		assertEquals(crude.inertia() * 8, built.inertia(), 1e-9);
		assertEquals(9, built.injectorFlow(), 1e-9);
		assertNotEquals(crude, built);
	}
}
