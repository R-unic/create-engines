package com.createengines.forge;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import com.createengines.engine.sim.CamProfile;
import com.createengines.engine.sim.CamProfiles;
import com.createengines.engine.sim.PartEffects;
import com.createengines.engine.sim.PartStats;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;

/**
 * Parts as data, read at every data reload from the files the main build reads: {@code part_effects/<part>.json} and
 * {@code cam_profiles/<kind>.json} (handed to the shared simulation's {@link PartEffects} and {@link CamProfiles}), and the
 * block data maps {@code part_stats} and {@code part_effect}, which 1.20.1 has no data maps for. A broken file is logged
 * and the built-in values stay; nothing here can stop a world loading.
 */
public final class PackData implements ResourceManagerReloadListener {

	private static final String MOD = CreateEnginesForge.ID;

	private static volatile Map<ResourceLocation, PartStats> partStats = Map.of();
	private static volatile Map<ResourceLocation, String> partEffect = Map.of();

	@Override
	public void onResourceManagerReload(ResourceManager manager) {
		Map<PartEffects.Part, PartEffects.Effect> effects = new EnumMap<>(PartEffects.Part.class);
		Map<String, PartEffects.Effect> own = new HashMap<>();
		for (Map.Entry<ResourceLocation, Resource> file : manager
			.listResources("part_effects", id -> id.getPath().endsWith(".json")).entrySet()) {
			ResourceLocation id = file.getKey();
			String name = id.getPath().substring("part_effects/".length(), id.getPath().length() - ".json".length());
			try {
				PartEffects.Effect effect = DataMapFile.partEffect(json(file.getValue()));
				if (!id.getNamespace().equals(MOD))
					own.put(new ResourceLocation(id.getNamespace(), name).toString(), effect);
				else {
					PartEffects.Part part = null;
					for (PartEffects.Part candidate : PartEffects.Part.values())
						if (candidate.fileName().equals(name))
							part = candidate;
					if (part == null)
						LogUtils.getLogger().warn("part_effects/{}.json is not a part this version knows", name);
					else
						effects.put(part, effect);
				}
			} catch (Exception e) {
				LogUtils.getLogger().warn("part_effects/{}.json from {} was not used: {}", name, id.getNamespace(),
					e.getMessage());
			}
		}
		PartEffects.set(effects, own);

		Map<CamProfiles.Kind, CamProfile> profiles = new EnumMap<>(CamProfiles.Kind.class);
		for (CamProfiles.Kind kind : CamProfiles.Kind.values()) {
			ResourceLocation id = new ResourceLocation(MOD, "cam_profiles/" + kind.fileName() + ".json");
			manager.getResource(id).ifPresent(file -> {
				try {
					profiles.put(kind, DataMapFile.camProfile(kind, json(file)));
				} catch (Exception e) {
					LogUtils.getLogger().warn("{} was not used, so the built-in profile stays: {}", id, e.getMessage());
				}
			});
		}
		CamProfiles.set(profiles);

		Map<ResourceLocation, PartStats> stats = new HashMap<>();
		Map<ResourceLocation, String> effectNames = new HashMap<>();
		for (String namespace : manager.getNamespaces()) {
			for (Resource file : manager.getResourceStack(new ResourceLocation(namespace, "data_maps/block/part_stats.json")))
				entries(file, DataMapFile::partStats, stats);
			for (Resource file : manager.getResourceStack(new ResourceLocation(namespace, "data_maps/block/part_effect.json")))
				entries(file, entry -> {
					if (!entry.has("effect"))
						throw new IllegalArgumentException("an entry names its \"effect\"");
					return new ResourceLocation(entry.get("effect").getAsString()).toString();
				}, effectNames);
		}
		partStats = Map.copyOf(stats);
		partEffect = Map.copyOf(effectNames);
	}

	private interface EntryReader<T> {
		T read(JsonObject entry);
	}

	private static <T> void entries(Resource file, EntryReader<T> reader, Map<ResourceLocation, T> into) {
		try (BufferedReader in = file.openAsReader()) {
			for (Map.Entry<String, JsonObject> entry : DataMapFile.values(in).entrySet())
				try {
					into.put(new ResourceLocation(entry.getKey()), reader.read(entry.getValue()));
				} catch (Exception e) {
					LogUtils.getLogger().warn("{} in a data map of {} was not used: {}", entry.getKey(),
						file.sourcePackId(), e.getMessage());
				}
		} catch (Exception e) {
			LogUtils.getLogger().warn("a data map of {} was not used: {}", file.sourcePackId(), e.toString());
		}
	}

	private static JsonElement json(Resource file) throws IOException {
		try (BufferedReader in = file.openAsReader()) {
			return JsonParser.parseReader(in);
		}
	}

	/** The retuning a pack gives the block, or {@link PartStats#NONE}. */
	public static PartStats partStats(ResourceLocation block) {
		return partStats.getOrDefault(block, PartStats.NONE);
	}

	/** The id of the part effect the block adds to its cylinder, or null. */
	public static @Nullable String partEffect(ResourceLocation block) {
		return partEffect.get(block);
	}

	public static List<ResourceLocation> partStatsBlocks() {
		return List.copyOf(partStats.keySet());
	}
}
