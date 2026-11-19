package com.createengines.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.createengines.CreateEngines;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * The balance presets that ship in the jar ({@code config_presets/<name>.toml}), applied to the running server's config.
 * A preset only sets keys that already exist under {@code [balance]} and {@code [features]}; applying one is the same as
 * copying those two tables into the config file by hand, which the files' own comments describe, and a value outside a
 * setting's range is refused rather than clamped.
 */
public final class ConfigPresets {

	/** The presets in the jar, mildest first. */
	public static final List<String> NAMES = List.of("casual", "standard", "expert");

	/**
	 * @param changed  one line for each setting the preset moved, {@code path: old -> new}
	 * @param problems one line for each value it could not apply
	 * @param same     how many settings it named that already had the value
	 */
	public record Result(List<String> changed, List<String> problems, int same) {
	}

	private ConfigPresets() {
	}

	/** Whether {@code name} is a preset in the jar. */
	public static boolean exists(String name) {
		return NAMES.contains(name);
	}

	/**
	 * Sets every setting the preset names, saves the config file and tells the simulation. Returns what moved.
	 *
	 * @throws IllegalArgumentException when there is no such preset
	 */
	public static Result apply(String name) {
		if (!exists(name))
			throw new IllegalArgumentException("no preset called " + name + "; the presets are " + NAMES);
		Map<String, Object> wanted;
		try (InputStream in = CreateEngines.class.getResourceAsStream("/config_presets/" + name + ".toml")) {
			if (in == null)
				throw new IllegalStateException("the jar has no preset " + name);
			wanted = ConfigFiles.read(new InputStreamReader(in, StandardCharsets.UTF_8));
		} catch (IOException e) {
			throw new IllegalStateException("could not read preset " + name, e);
		}
		List<String> changed = new ArrayList<>();
		List<String> problems = new ArrayList<>();
		int same = 0;
		ModConfigSpec.ConfigValue<?> last = null;
		for (Map.Entry<String, Object> entry : wanted.entrySet()) {
			String path = entry.getKey();
			ModConfigSpec.ConfigValue<?> value = CEConfig.values().get(path);
			Object raw = entry.getValue();
			if (!(path.startsWith("balance.") || path.startsWith("features.")) || value == null) {
				problems.add(path + " is not a setting a preset may set");
				continue;
			}
			Object spec = CEConfig.SPEC.getSpec().get(List.of(path.split("\\.")));
			if (spec instanceof ModConfigSpec.ValueSpec valueSpec && !valueSpec.test(raw)) {
				problems.add(path + " = " + raw + " is outside the setting's range");
				continue;
			}
			Object before = value.get();
			try {
				ConfigFiles.setTyped(value, raw);
			} catch (RuntimeException e) {
				problems.add(path + " = " + raw + " could not be set: " + e.getMessage());
				continue;
			}
			last = value;
			if (equal(before, value.get()))
				same++;
			else
				changed.add(path + ": " + before + " -> " + value.get());
		}
		if (last != null) {
			last.save();
			CEConfig.applyTuning();
		}
		return new Result(List.copyOf(changed), List.copyOf(problems), same);
	}

	private static boolean equal(Object a, Object b) {
		return a instanceof Number x && b instanceof Number y ? x.doubleValue() == y.doubleValue() : a.equals(b);
	}
}
