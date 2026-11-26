package com.createengines;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

/** What a player reads about heat: temperatures in degrees Celsius, amounts of heat in HU and rates in HU/s. */
class HeatUnitsTest {

	private static final Path LANG = Path.of("src/generated/resources/assets/create_engines/lang/en_us.json");

	/** Kelvin after a number or an argument, or a heat rate with no unit named. */
	private static final Pattern OFF_STANDARD = Pattern.compile(
		"(\\d|%(\\d+\\$)?s) ?K\\b|(?i:kelvin|fahrenheit|°F|heat (a|per) second|(\\d|%(\\d+\\$)?s) heat\\b)");

	@Test
	void shippedTextKeepsToTheStandardUnits() throws IOException {
		Map<String, String> lang = new Gson().fromJson(Files.readString(LANG, StandardCharsets.UTF_8),
			new TypeToken<TreeMap<String, String>>() {
			}.getType());
		List<String> bad = new ArrayList<>();
		lang.forEach((key, value) -> {
			if (OFF_STANDARD.matcher(value)
				.find())
				bad.add(key + ": " + value);
		});
		assertTrue(bad.isEmpty(), "Show temperatures in °C and heat in HU or HU/s: " + bad);
	}

	@Test
	void theGuardSeesWhatItIsFor() {
		for (String text : List.of("Charge: %1$s K", "ignites at 465 K", "Shedding %1$s heat a second", "about 3 heat per second",
			"%1$s heat", "in Kelvin"))
			assertTrue(OFF_STANDARD.matcher(text)
				.find(), text);
		for (String text : List.of("Exhaust heat: %1$s HU/s", "%1$s°C  Warm", "20 HU/s superheats it", "Keep it warm"))
			assertTrue(!OFF_STANDARD.matcher(text)
				.find(), text);
	}
}
