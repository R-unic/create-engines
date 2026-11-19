package com.createengines.config;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.electronwill.nightconfig.toml.TomlParser;
import com.mojang.logging.LogUtils;

import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Reads a 1.x config file and puts its values into the 2.0 one the first time that one loads. */
public final class ConfigFiles {

	private static final Logger LOGGER = LogUtils.getLogger();

	private ConfigFiles() {
	}

	/**
	 * Called when a 2.0 config loads. If it has not been carried over yet, reads {@code oldFile} from the same folder, sets
	 * each value it has under its new key and logs what moved and what did not, then marks the config as done so a value the
	 * player later changes is never overwritten. A missing or unreadable old file only marks it done.
	 */
	static void carryOver(ModConfig config, String oldFile, Map<String, String> table,
						  Map<String, ModConfigSpec.ConfigValue<?>> values, ModConfigSpec.BooleanValue done) {
		if (done.get())
			return;
		Path old = config.getFullPath()
			.resolveSibling(oldFile);
		try {
			if (Files.isRegularFile(old)) {
				ConfigCarryOver.Result result = ConfigCarryOver.carry(table, read(old));
				int set = 0;
				for (Map.Entry<String, Object> moved : result.moved()
					.entrySet())
					if (apply(values.get(moved.getKey()), moved.getValue(), moved.getKey()))
						set++;
				LOGGER.info("Carried {} settings from {} over to the 2.0 config {}; left alone: {}", set, old.getFileName(),
					config.getFileName(), result.skipped()
						.isEmpty() ? "nothing" : result.skipped());
			}
		} catch (IOException | RuntimeException e) {
			LOGGER.warn("Could not carry the 1.x settings over from {}; the 2.0 defaults stay", old, e);
		}
		done.set(true);
		done.save();
	}

	private static boolean apply(ModConfigSpec.ConfigValue<?> value, Object raw, String path) {
		if (value == null)
			return false;
		try {
			setTyped(value, raw);
			return true;
		} catch (RuntimeException e) {
			LOGGER.warn("Setting {} in the 1.x file was {} and is not usable; the 2.0 default stays", path, raw);
			return false;
		}
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private static void setTyped(ModConfigSpec.ConfigValue value, Object raw) {
		if (value instanceof ModConfigSpec.DoubleValue)
			value.set(((Number) raw).doubleValue());
		else if (value instanceof ModConfigSpec.IntValue)
			value.set(((Number) raw).intValue());
		else if (value instanceof ModConfigSpec.BooleanValue)
			value.set((Boolean) raw);
		else
			throw new IllegalArgumentException("unknown kind of setting");
	}

	/** Every value in a TOML file, by dotted path. */
	public static Map<String, Object> read(Path file) throws IOException {
		Map<String, Object> flat = new LinkedHashMap<>();
		try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			flatten("", new TomlParser().parse(reader), flat);
		}
		return flat;
	}

	private static void flatten(String prefix, UnmodifiableConfig config, Map<String, Object> into) {
		for (UnmodifiableConfig.Entry entry : config.entrySet()) {
			String path = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
			Object value = entry.getValue();
			if (value instanceof UnmodifiableConfig nested)
				flatten(path, nested, into);
			else
				into.put(path, value);
		}
	}
}
