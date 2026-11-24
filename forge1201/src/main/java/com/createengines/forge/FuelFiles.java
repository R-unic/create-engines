package com.createengines.forge;

import java.io.IOException;
import java.io.Reader;
import java.util.LinkedHashMap;
import java.util.Map;

import com.createengines.engine.sim.FuelSpec;
import com.google.gson.JsonObject;

/**
 * Reads the mod's fuel files, {@code data/<namespace>/data_maps/fluid/fuel.json}, with no data maps behind them:
 * 1.20.1 has none, so a port reads the same files itself. The format is the main build's, field for field, and the
 * defaults are the main build's ({@code CEDataMaps.FuelProperties}), so a fuel means the same on both. A pack's file
 * for the port is the file it ships for 1.21.1.
 */
public final class FuelFiles {

	private FuelFiles() {
	}

	/** The fuels one file names, by fluid id, or throws if a number is missing or the shape is wrong. */
	public static Map<String, FuelSpec> read(Reader file) throws IOException {
		Map<String, FuelSpec> fuels = new LinkedHashMap<>();
		for (Map.Entry<String, JsonObject> entry : DataMapFile.values(file).entrySet())
			fuels.put(entry.getKey(), spec(entry.getKey(), entry.getValue()));
		return fuels;
	}

	private static FuelSpec spec(String id, JsonObject o) throws IOException {
		double autoignition = required(id, o, "autoignition_temperature");
		double soot = optional(o, "soot_factor", 1);
		double preignition = optional(o, "preignition_temperature", 0);
		return new FuelSpec(required(id, o, "energy_per_mb"), autoignition, required(id, o, "air_per_mb"),
			optional(o, "cold_sensitivity", 1), optional(o, "charge_cooling", 0), soot,
			preignition > 0 ? preignition : autoignition, optional(o, "sulfur", soot), optional(o, "flammability", 1));
	}

	private static double required(String id, JsonObject o, String key) throws IOException {
		if (!o.has(key))
			throw new IOException(id + " has no " + key);
		return o.get(key).getAsDouble();
	}

	private static double optional(JsonObject o, String key, double fallback) {
		return o.has(key) ? o.get(key).getAsDouble() : fallback;
	}
}
