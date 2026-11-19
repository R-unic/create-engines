package com.createengines.advancement;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.ServerAdvancementManager;

import com.createengines.CreateEngines;

/**
 * Reads a player's saved advancement file for progress on the 1.x advancement IDs and puts it on the 2.0 rows, silently.
 * <p>
 * Vanilla drops a saved advancement it does not know ("Ignored advancement ... it doesn't exist anymore"), so by the time the
 * player's progress is loaded the 1.x entries are gone. The file on disk is not rewritten until the next save, so this reads
 * it a second time, from {@code PlayerAdvancementsMixin}, while the progress is being loaded. It runs before the first
 * packet, so the client is told about the progress the way it is told about any saved progress: no toast, no chat line, no
 * sting, and no reward. Once the game saves, the file holds only the new IDs, and this finds nothing to do.
 */
public final class LegacyProgress {

	private static final Logger LOGGER = LogUtils.getLogger();

	private LegacyProgress() {
	}

	/** Called while {@code advancements} loads its progress from {@code file}, before its listeners are registered. */
	public static void carryOver(Path file, ServerAdvancementManager manager, PlayerAdvancements advancements) {
		try {
			Map<String, Set<String>> earned = readEarned(file);
			if (earned.isEmpty())
				return;
			Map<String, Set<String>> grants = AdvancementMigration.migrate(earned);
			int credited = 0;
			for (Map.Entry<String, Set<String>> grant : grants.entrySet()) {
				AdvancementHolder holder = manager.get(CreateEngines.asResource(grant.getKey()));
				if (holder == null)
					continue;
				AdvancementProgress progress = advancements.getOrStartProgress(holder);
				for (String criterion : grant.getValue())
					if (holder.value()
						.criteria()
						.containsKey(criterion) && progress.grantProgress(criterion))
						credited++;
			}
			if (credited > 0)
				LOGGER.info("Carried {} earned 1.x advancement rows over to the 2.0 ones ({} criteria credited) from {}",
					earned.size(), credited, file.getFileName());
		} catch (IOException | RuntimeException e) {
			// Never stop a player joining over old progress: whatever was credited so far stays
			LOGGER.warn("Could not carry the 1.x advancements over from {}", file, e);
		}
	}

	/** The criteria earned on each of this mod's advancements in a saved file, by path. Empty when there is no file. */
	public static Map<String, Set<String>> readEarned(Path file) throws IOException {
		Map<String, Set<String>> earned = new LinkedHashMap<>();
		if (!Files.isRegularFile(file))
			return earned;
		JsonElement root;
		try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			root = JsonParser.parseReader(reader);
		}
		if (!root.isJsonObject())
			return earned;
		String prefix = CreateEngines.MOD_ID + ":";
		for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject()
			.entrySet()) {
			if (!entry.getKey()
				.startsWith(prefix) || !entry.getValue()
					.isJsonObject())
				continue;
			String path = entry.getKey()
				.substring(prefix.length());
			if (!AdvancementMigration.legacyIds()
				.contains(path))
				continue;
			JsonElement criteria = entry.getValue()
				.getAsJsonObject()
				.get("criteria");
			if (criteria == null || !criteria.isJsonObject())
				continue;
			Set<String> names = new LinkedHashSet<>(((JsonObject) criteria).keySet());
			if (!names.isEmpty())
				earned.put(path, names);
		}
		return earned;
	}
}
