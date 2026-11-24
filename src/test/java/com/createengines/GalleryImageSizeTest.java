package com.createengines;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * The pictures of docs/images (the gallery of the mod pages and the listing's art) stay under 2,000,000 bytes, the size the
 * sites take as 2 MB however the megabyte is counted. tools/gallery.py shrinks a new still to fit; this fails a picture that
 * was put there by hand and is too big.
 */
class GalleryImageSizeTest {

	private static final long LIMIT = 2_000_000L;

	@Test
	void everyPictureInTheGalleryFolderIsUnderTwoMegabytes() throws IOException {
		List<String> tooBig;
		try (Stream<Path> files = Files.list(Path.of("").toAbsolutePath().resolve("docs/images"))) {
			tooBig = files.filter(f -> f.toString().matches("(?i).*\\.(png|jpe?g|gif|webp)"))
				.filter(f -> sizeOf(f) >= LIMIT)
				.map(f -> f.getFileName() + " (" + sizeOf(f) + " bytes)")
				.collect(Collectors.toList());
		}
		assertTrue(tooBig.isEmpty(), "Pictures in docs/images of 2,000,000 bytes or more: " + tooBig
			+ "\ntools/gallery.py --apply shrinks a new still to fit; shrink a hand-made one the same way (see fit() there).");
	}

	private static long sizeOf(Path file) {
		try {
			return Files.size(file);
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}
}
