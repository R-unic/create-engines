package com.createengines.engine;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.createengines.CreateEngines;
import com.createengines.engine.sim.PartEffects;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

/**
 * Reads {@code data/create_engines/part_effects/<part>.json} on every data reload and hands the result to
 * {@link PartEffects}. The mod ships all four files with the built-in values, so a pack overrides one by providing the same
 * path. A file that is broken is logged and that part keeps the built-in values; nothing here can stop a world loading.
 * Engines already built keep what they were built with until they next rebuild.
 */
public final class PartEffectLoader extends SimpleJsonResourceReloadListener {

	private static final Gson GSON = new Gson();

	private PartEffectLoader() {
		super(GSON, "part_effects");
	}

	public static void onAddListeners(AddReloadListenerEvent event) {
		event.addListener(new PartEffectLoader());
	}

	private static volatile List<String> report = List.of();

	/** What the last data reload made of each {@code part_effects} file, one line apiece, for {@code /ce parts}. */
	public static List<String> lastReport() {
		return report;
	}

	@Override
	protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
		Map<PartEffects.Part, PartEffects.Effect> found = new EnumMap<>(PartEffects.Part.class);
		List<String> lines = new ArrayList<>();
		for (Map.Entry<ResourceLocation, JsonElement> file : files.entrySet()) {
			ResourceLocation id = file.getKey();
			if (!id.getNamespace()
				.equals(CreateEngines.MOD_ID))
				continue;
			PartEffects.Part part = partNamed(id.getPath());
			if (part == null) {
				CreateEngines.LOGGER.warn("part_effects/{}.json is not a part this version knows (bore, head, piston, injector)",
					id.getPath());
				lines.add("part_effects/" + id.getPath() + ".json: ignored, not a part this version knows (bore, head, piston, injector)");
				continue;
			}
			try {
				found.put(part, parse(file.getValue()));
				lines.add("part_effects/" + id.getPath() + ".json: used");
			} catch (RuntimeException e) {
				CreateEngines.LOGGER.warn("part_effects/{}.json was not used, so the built-in {} stays: {}", id.getPath(),
					part.fileName(), e.getMessage());
				lines.add("part_effects/" + id.getPath() + ".json: NOT used, the built-in " + part.fileName() + " stays: " + e.getMessage());
			}
		}
		PartEffects.set(found);
		lines.sort(null);
		report = List.copyOf(lines);
	}

	private static PartEffects.Part partNamed(String name) {
		for (PartEffects.Part part : PartEffects.Part.values())
			if (part.fileName()
				.equals(name))
				return part;
		return null;
	}

	/** One file's contents as an effect; throws {@link IllegalArgumentException} (or a JSON error) when it is not one. */
	public static PartEffects.Effect parse(JsonElement json) {
		if (!json.isJsonObject())
			throw new IllegalArgumentException("a part effect file is a JSON object");
		JsonObject object = json.getAsJsonObject();
		Map<String, Double> values = new HashMap<>();
		for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
			JsonElement value = entry.getValue();
			if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive()
				.isNumber())
				throw new IllegalArgumentException("\"" + entry.getKey() + "\" must be a number");
			values.put(entry.getKey(), value.getAsDouble());
		}
		return PartEffects.parse(values);
	}
}
