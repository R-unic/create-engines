package com.createengines.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Where each 1.x config key went in 2.0. Plain Java: the config classes need the game, this does not, so a test can hold the
 * tables against the list of every 1.x key.
 * <p>
 * 2.0 regroups the keys once (the server's {@code [engine]} table became {@code [calibration]} and {@code [limits]}, and the
 * client's volumes left {@code [effects]} for {@code [volume]}). A key's name and meaning did not change, only where it lives
 * and, for the client's, how it is spelt. On first start the new config reads the 1.x file beside it and carries every value
 * over, so nobody's settings reset. The old file is left as it was.
 */
public final class ConfigCarryOver {

	/** The 1.x server file, next to the 2.0 one in the world's {@code serverconfig} folder. */
	public static final String OLD_SERVER_FILE = "create_engines-server.toml";

	/** The 1.x client file, next to the 2.0 one in {@code config}. */
	public static final String OLD_CLIENT_FILE = "create_engines-client.toml";

	/** Old server key to new key. */
	public static final Map<String, String> SERVER = new LinkedHashMap<>();

	/** Old client key to new key. */
	public static final Map<String, String> CLIENT = new LinkedHashMap<>();

	static {
		for (String key : List.of("stressPerTorque", "journalInertia", "throwInertia", "flywheelInertia",
			"castFlywheelInertia", "bearingFriction"))
			SERVER.put("engine." + key, "calibration." + key);
		for (String key : List.of("maxCrankSegments", "maxManifoldBlocks", "maxCoupledLines", "maxCouplingWalk"))
			SERVER.put("engine." + key, "limits." + key);
		for (String key : List.of("fuelConsumption", "precisionBonus", "boostStrength", "rotaryPower", "heatRecoveryOutput",
			"coolingBonus", "torsionalFlex", "alternatorEnergy"))
			SERVER.put("balance." + key, "balance." + key);
		for (String key : List.of("forcedInduction", "sparkFouling", "oilDilution", "oilAgeing", "engineTemperature",
			"pilotInjection", "rotaryEngines", "heatRecovery"))
			SERVER.put("features." + key, "features." + key);

		CLIENT.put("effects.engineParticles", "effects.particles");
		CLIENT.put("effects.effectDensity", "effects.density");
		CLIENT.put("effects.bangVolume", "volume.bang");
		CLIENT.put("effects.engineNoteVolume", "volume.engineNote");
		CLIENT.put("effects.mechanicalVolume", "volume.mechanical");
		CLIENT.put("effects.boostVolume", "volume.boost");
	}

	private ConfigCarryOver() {
	}

	/**
	 * @param moved   the new key and the value it takes, in the table's order
	 * @param skipped old keys that are not in the table (a typo, or a key some other version added), and so were left behind
	 */
	public record Result(Map<String, Object> moved, List<String> skipped) {
	}

	/**
	 * Works out what the old file's values become.
	 *
	 * @param table the old-to-new key table
	 * @param old   every value in the old file, by dotted path (a table's own entries, never the table)
	 */
	public static Result carry(Map<String, String> table, Map<String, Object> old) {
		Map<String, Object> moved = new LinkedHashMap<>();
		List<String> skipped = new ArrayList<>();
		for (Map.Entry<String, Object> entry : old.entrySet()) {
			String now = table.get(entry.getKey());
			if (now == null)
				skipped.add(entry.getKey());
			else if (entry.getValue() != null)
				moved.put(now, entry.getValue());
		}
		return new Result(moved, skipped);
	}
}
