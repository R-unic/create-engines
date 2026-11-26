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
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

/**
 * Every shipped lang string must be a template Minecraft can read. A translated component treats {@code %} as the start of an
 * argument ({@code %s}, {@code %1$s}) or an escape ({@code %%}); any other {@code %} makes the whole line render as "Format error:
 * ..." instead of the text. A Ponder page once said "stock 1%, cardboard 2.5%" and showed exactly that, so a plain percent must be
 * written {@code %%}.
 */
class LangTemplateTest {

	private static final Path LANG_DIR = Path.of("src/generated/resources/assets/create_engines/lang");

	/** The pieces {@code TranslatableContents} accepts; whatever is left after removing them must hold no {@code %}. */
	private static final Pattern ARGUMENT = Pattern.compile("%(?:(\\d+)\\$)?([A-Za-z%]|$)");

	@Test
	void everyLangStringIsAValidTemplate() throws IOException {
		List<String> bad = new ArrayList<>();
		try (Stream<Path> files = Files.list(LANG_DIR)) {
			for (Path file : files.filter(p -> p.toString()
				.endsWith(".json"))
				.sorted()
				.toList()) {
				Map<String, String> lang = new Gson().fromJson(Files.readString(file, StandardCharsets.UTF_8),
					new TypeToken<TreeMap<String, String>>() {
					}.getType());
				lang.forEach((key, value) -> {
					if (ARGUMENT.matcher(value)
						.replaceAll("")
						.indexOf('%') >= 0)
						bad.add(file.getFileName() + ": " + key);
				});
			}
		}
		assertTrue(bad.isEmpty(), "Lang strings with a bare % (write %% for a percent sign), which show as \"Format error\" in game: " + bad);
	}
}
