package com.createengines.engine.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
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

/** Cam profiles as data: the shipped files are the built-in profiles, a pack's file changes exactly what it names. */
class CamProfilesTest {

	private static final Path DIRECTORY = Path.of("src/main/resources/data/create_engines/cam_profiles");

	@BeforeEach
	@AfterEach
	void builtIn() {
		CamProfiles.reset();
	}

	private static Map<String, Double> read(CamProfiles.Kind kind) throws IOException {
		Map<String, Double> values = new HashMap<>();
		Matcher match = Pattern.compile("\"([a-z_]+)\"\\s*:\\s*([0-9.]+)")
			.matcher(Files.readString(DIRECTORY.resolve(kind.fileName() + ".json")));
		while (match.find())
			values.put(match.group(1), Double.parseDouble(match.group(2)));
		return values;
	}

	@Test
	void theShippedFilesAreExactlyTheBuiltInProfiles() throws IOException {
		for (CamProfiles.Kind kind : CamProfiles.Kind.values()) {
			Map<String, Double> values = read(kind);
			assertEquals(CamProfiles.KEYS, values.keySet(), kind + ".json should name every key");
			assertEquals(kind.builtIn(), CamProfiles.parse(kind, values), kind + ".json drifted from the built-in profile");
		}
	}

	@Test
	void everyKindHasAFileAndNoStrayFilesExist() throws IOException {
		try (var files = Files.list(DIRECTORY)) {
			assertEquals(CamProfiles.Kind.values().length, files.count());
		}
	}

	@Test
	void withNoPackTheEnginesRunTheConstantsThemselves() {
		assertSame(CamProfile.BASIC, CamProfiles.basic());
		assertSame(CamProfile.ROTARY, CamProfiles.rotary());
		CamProfiles.set(Map.of());
		assertSame(CamProfile.BASIC, CamProfiles.basic());
		assertSame(CamProfile.ROTARY, CamProfiles.rotary());
	}

	@Test
	void aKeyLeftOutKeepsTheBuiltInAngle() {
		CamProfile longer = CamProfiles.parse(CamProfiles.Kind.BASIC, Map.of("intake_close", 260.0));
		assertEquals(260, longer.intakeClose());
		assertEquals(CamProfile.BASIC.intakeOpen(), longer.intakeOpen());
		assertEquals(CamProfile.BASIC.exhaustOpen(), longer.exhaustOpen());
		assertEquals(CamProfile.BASIC.injection(), longer.injection());
		assertEquals(CamProfile.ROTARY.intakeOpen(), CamProfiles.parse(CamProfiles.Kind.ROTARY, Map.of()).intakeOpen());
	}

	@Test
	void aProfileInThePackReplacesOnlyItsKindAndTheOtherStaysBuiltIn() {
		CamProfile sport = CamProfiles.parse(CamProfiles.Kind.BASIC, Map.of("intake_open", 690.0, "intake_close", 250.0));
		CamProfiles.set(Map.of(CamProfiles.Kind.BASIC, sport));
		assertSame(sport, CamProfiles.basic());
		assertSame(CamProfile.ROTARY, CamProfiles.rotary());
		CamProfiles.set(Map.of());
		assertSame(CamProfile.BASIC, CamProfiles.basic());
	}

	@Test
	void aLongerIntakeOpensTheValveWhereTheBuiltInOneIsShut() {
		CamProfile sport = CamProfiles.parse(CamProfiles.Kind.BASIC, Map.of("intake_close", 260.0));
		assertEquals(0, CamProfile.BASIC.intakeLift(240), 1e-12);
		assertTrue(sport.intakeLift(240) > 0);
		assertNotEquals(CamProfile.BASIC.intakeCurve()
			.at(240), sport.intakeCurve()
				.at(240));
	}

	@Test
	void aTypoOrAnImpossibleAngleIsRefusedWithAMessage() {
		assertTrue(assertThrows(IllegalArgumentException.class, () -> CamProfiles.parse(CamProfiles.Kind.BASIC, Map.of("intake_closes", 230.0)))
			.getMessage()
			.contains("unknown key"));
		for (double bad : new double[] {-1, 720, 1e9, Double.NaN, Double.POSITIVE_INFINITY})
			assertThrows(IllegalArgumentException.class, () -> CamProfiles.parse(CamProfiles.Kind.BASIC, Map.of("spark", bad)), "angle " + bad);
		// A valve open for 10 degrees, or for nearly a whole turn, has no sound lift
		assertThrows(IllegalArgumentException.class, () -> CamProfiles.parse(CamProfiles.Kind.BASIC, Map.of("intake_close", 709.0)),
			"almost a full cycle open");
		assertThrows(IllegalArgumentException.class, () -> CamProfiles.parse(CamProfiles.Kind.BASIC, Map.of("exhaust_close", 510.0)),
			"ten degrees open");
	}

	@Test
	void anEngineOnAnyAcceptedProfileStaysFiniteAndTurns() {
		// The extremes the range allows, on a real cylinder: nothing may go to NaN or stop the cycle counting
		double[][] cuts = {{0, 40}, {600, 280}, {700, 60}, {100, 500}};
		for (double[] cut : cuts) {
			CamProfile profile = CamProfiles.parse(CamProfiles.Kind.BASIC,
				Map.of("intake_open", cut[0], "intake_close", cut[1], "exhaust_open", cut[0], "exhaust_close", cut[1]));
			for (int angle = 0; angle < 720; angle += 5) {
				double intake = profile.intakeLift(angle);
				double exhaust = profile.exhaustLift(angle);
				assertTrue(intake >= 0 && intake <= 1 && exhaust >= 0 && exhaust <= 1, "lift out of range at " + angle + " for " + profile);
				assertTrue(Double.isFinite(profile.intakeCurve()
					.at(angle)));
			}
		}
	}
}
