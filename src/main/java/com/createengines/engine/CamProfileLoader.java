package com.createengines.engine;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.createengines.CreateEngines;
import com.createengines.engine.sim.CamProfile;
import com.createengines.engine.sim.CamProfiles;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

/**
 * Reads {@code data/create_engines/cam_profiles/<kind>.json} on every data reload (kinds: {@code basic} for a piston
 * cylinder's lobe, {@code rotary} for a rotor's ports) and hands the result to {@link CamProfiles}. The mod ships both files
 * with the built-in angles, so a pack overrides one by providing the same path. A file that is broken is logged and that
 * profile keeps the built-in angles; nothing here can stop a world loading. Engines already running keep the profile they
 * were built with until they next rebuild.
 */
public final class CamProfileLoader extends SimpleJsonResourceReloadListener {

	private static final Gson GSON = new Gson();

	private CamProfileLoader() {
		super(GSON, "cam_profiles");
	}

	public static void onAddListeners(AddReloadListenerEvent event) {
		event.addListener(new CamProfileLoader());
	}

	private static volatile List<String> report = List.of();

	/** What the last data reload made of each {@code cam_profiles} file, one line apiece, for {@code /ce parts}. */
	public static List<String> lastReport() {
		return report;
	}

	@Override
	protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
		Map<CamProfiles.Kind, CamProfile> found = new EnumMap<>(CamProfiles.Kind.class);
		List<String> lines = new ArrayList<>();
		for (Map.Entry<ResourceLocation, JsonElement> file : files.entrySet()) {
			ResourceLocation id = file.getKey();
			if (!id.getNamespace()
				.equals(CreateEngines.MOD_ID))
				continue;
			CamProfiles.Kind kind = kindNamed(id.getPath());
			if (kind == null) {
				CreateEngines.LOGGER.warn("cam_profiles/{}.json is not a profile this version knows (basic, rotary)", id.getPath());
				lines.add("cam_profiles/" + id.getPath() + ".json: ignored, not a profile this version knows (basic, rotary)");
				continue;
			}
			try {
				found.put(kind, parse(kind, file.getValue()));
				lines.add("cam_profiles/" + id.getPath() + ".json: used");
			} catch (RuntimeException e) {
				CreateEngines.LOGGER.warn("cam_profiles/{}.json was not used, so the built-in {} profile stays: {}", id.getPath(),
					kind.fileName(), e.getMessage());
				lines.add("cam_profiles/" + id.getPath() + ".json: NOT used, the built-in " + kind.fileName() + " profile stays: " + e.getMessage());
			}
		}
		CamProfiles.set(found);
		lines.sort(null);
		report = List.copyOf(lines);
	}

	private static CamProfiles.Kind kindNamed(String name) {
		for (CamProfiles.Kind kind : CamProfiles.Kind.values())
			if (kind.fileName()
				.equals(name))
				return kind;
		return null;
	}

	/** One file's contents as a profile; throws {@link IllegalArgumentException} (or a JSON error) when it is not one. */
	public static CamProfile parse(CamProfiles.Kind kind, JsonElement json) {
		if (!json.isJsonObject())
			throw new IllegalArgumentException("a cam profile file is a JSON object");
		JsonObject object = json.getAsJsonObject();
		Map<String, Double> values = new HashMap<>();
		for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
			JsonElement value = entry.getValue();
			if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive()
				.isNumber())
				throw new IllegalArgumentException("\"" + entry.getKey() + "\" must be a number");
			values.put(entry.getKey(), value.getAsDouble());
		}
		return CamProfiles.parse(kind, values);
	}
}
