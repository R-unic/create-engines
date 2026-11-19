package com.createengines.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

/** What an addon reads to learn which API is installed has to be read at run time, and {@code isAtLeast} has to be right. */
class ApiVersionTest {

	/**
	 * A {@code static final} field with a constant value is copied into the class that reads it, so an addon built against
	 * 1.2 would report 1.2 on any install. A class file only names the attribute that holds such a value when some field uses
	 * one, so its absence shows that none of these fields is a compile-time constant.
	 */
	@Test
	void theVersionFieldsAreNotInlinedIntoTheClassesThatReadThem() throws IOException {
		byte[] bytes;
		try (InputStream in = ApiVersion.class.getResourceAsStream("ApiVersion.class")) {
			bytes = in.readAllBytes();
		}
		assertFalse(new String(bytes, StandardCharsets.ISO_8859_1).contains("ConstantValue"),
			"an ApiVersion field is a compile-time constant, so addons would bake the version in at their build time");
	}

	@Test
	void theStringIsTheThreeNumbers() {
		assertEquals(ApiVersion.MAJOR + "." + ApiVersion.MINOR + "." + ApiVersion.PATCH, ApiVersion.VERSION);
	}

	@Test
	void isAtLeastMeansTheSameMajorAndANotOlderMinor() {
		assertTrue(ApiVersion.isAtLeast(ApiVersion.MAJOR, 0));
		assertTrue(ApiVersion.isAtLeast(ApiVersion.MAJOR, ApiVersion.MINOR));
		assertFalse(ApiVersion.isAtLeast(ApiVersion.MAJOR, ApiVersion.MINOR + 1));
		assertFalse(ApiVersion.isAtLeast(ApiVersion.MAJOR + 1, 0), "a newer major is a different API");
		assertFalse(ApiVersion.isAtLeast(ApiVersion.MAJOR - 1, 0), "an older major is a different API too");
	}
}
