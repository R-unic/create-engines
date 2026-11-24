package com.createengines.forge;

import java.io.BufferedReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import com.createengines.engine.sim.FuelSpec;
import com.mojang.logging.LogUtils;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;

/**
 * The fuels in the loaded packs: every namespace's {@code data_maps/fluid/fuel.json}, the files the main build reads
 * through a NeoForge data map. A later pack's entry for a fluid replaces an earlier one's, as it does there.
 */
public final class FuelData implements ResourceManagerReloadListener {

	private static volatile Map<ResourceLocation, FuelSpec> fuels = Map.of();

	@Override
	public void onResourceManagerReload(ResourceManager manager) {
		Map<ResourceLocation, FuelSpec> loaded = new HashMap<>();
		for (String namespace : manager.getNamespaces()) {
			List<Resource> files = manager.getResourceStack(new ResourceLocation(namespace, "data_maps/fluid/fuel.json"));
			for (Resource file : files)
				try (BufferedReader in = file.openAsReader()) {
					FuelFiles.read(in).forEach((id, spec) -> loaded.put(new ResourceLocation(id), spec));
				} catch (Exception e) {
					LogUtils.getLogger().error("Create Engines: skipping the fuel file {} from {}: {}", namespace,
						file.sourcePackId(), e.toString());
				}
		}
		fuels = Map.copyOf(loaded);
	}

	/** What the fluid burns as, or null if no pack makes it a fuel. */
	public static @Nullable FuelSpec of(ResourceLocation fluid) {
		return fuels.get(fluid);
	}

	public static int count() {
		return fuels.size();
	}
}
