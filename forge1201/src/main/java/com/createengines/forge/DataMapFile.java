package com.createengines.forge;

import java.io.IOException;
import java.io.Reader;
import java.util.LinkedHashMap;
import java.util.Map;

import com.createengines.engine.sim.CamProfile;
import com.createengines.engine.sim.CamProfiles;
import com.createengines.engine.sim.PartEffects;
import com.createengines.engine.sim.PartStats;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * The shapes of the mod's data files with the game taken out, so they are tested without one: a NeoForge data map's
 * file ({@code {"values": {id: entry}}}), the numbers in a part effect or cam profile file, and a part stats entry.
 * Every rule, range and message is the main build's; the numbers go through the shared simulation's own parsers.
 */
public final class DataMapFile {

	private DataMapFile() {
	}

	/** A data map file's entries by id. */
	public static Map<String, JsonObject> values(Reader file) throws IOException {
		JsonElement root = JsonParser.parseReader(file);
		if (!root.isJsonObject())
			throw new IOException("a data map file is an object with a \"values\" member");
		JsonElement values = root.getAsJsonObject().get("values");
		Map<String, JsonObject> entries = new LinkedHashMap<>();
		if (values == null)
			return entries;
		if (!values.isJsonObject())
			throw new IOException("\"values\" is an object keyed by id");
		for (Map.Entry<String, JsonElement> entry : values.getAsJsonObject().entrySet()) {
			if (!entry.getValue().isJsonObject())
				throw new IOException(entry.getKey() + " is not an object");
			entries.put(entry.getKey(), entry.getValue().getAsJsonObject());
		}
		return entries;
	}

	/** An object of numbers (what a part effect or cam profile file holds), or throws {@link IllegalArgumentException}. */
	public static Map<String, Double> numbers(JsonElement json, String what) {
		if (!json.isJsonObject())
			throw new IllegalArgumentException("a " + what + " file is a JSON object");
		Map<String, Double> values = new LinkedHashMap<>();
		for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject().entrySet()) {
			JsonElement value = entry.getValue();
			if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())
				throw new IllegalArgumentException("\"" + entry.getKey() + "\" must be a number");
			values.put(entry.getKey(), value.getAsDouble());
		}
		return values;
	}

	public static PartEffects.Effect partEffect(JsonElement json) {
		return PartEffects.parse(numbers(json, "part effect"));
	}

	public static CamProfile camProfile(CamProfiles.Kind kind, JsonElement json) {
		return CamProfiles.parse(kind, numbers(json, "cam profile"));
	}

	private static final String[] STAT_KEYS = {"compression_ratio", "intake_flow", "exhaust_flow", "max_fuel_per_shot",
		"ignition_delay", "burn_duration", "friction_static", "friction_viscous", "inertia", "displacement", "injector_flow"};

	/** A part stats entry: every key optional (1 changes nothing), each between {@link PartStats#MIN_SCALE} and {@link PartStats#MAX_SCALE}. Keys it does not know are ignored, as the main build's codec does. */
	public static PartStats partStats(JsonObject entry) {
		Map<String, Double> values = numbers(entry, "part stats");
		double[] scale = new double[STAT_KEYS.length];
		for (int i = 0; i < STAT_KEYS.length; i++) {
			Double value = values.get(STAT_KEYS[i]);
			scale[i] = value == null ? 1 : value;
			if (!(scale[i] >= PartStats.MIN_SCALE && scale[i] <= PartStats.MAX_SCALE))
				throw new IllegalArgumentException(STAT_KEYS[i] + " is between " + PartStats.MIN_SCALE + " and "
					+ PartStats.MAX_SCALE + ", not " + scale[i]);
		}
		return new PartStats(scale[0], scale[1], scale[2], scale[3], scale[4], scale[5], scale[6], scale[7], scale[8],
			scale[9], scale[10]);
	}
}
