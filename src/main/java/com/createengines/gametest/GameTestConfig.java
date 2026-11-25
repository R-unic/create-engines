package com.createengines.gametest;

import com.createengines.config.CEConfig;
import com.createengines.engine.sim.Tuning;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Settings that GameTests flip on the running server.
 * <p>
 * The config file is read again about half a second after anything saves it (a preset test, the pollution test), and that read
 * puts every setting back to what the file says. A setting changed only in memory, in a test that runs over many ticks, is lost
 * the moment an earlier test's save is read. So a setting a test relies on is set in the file as well, and every test that
 * changes it puts it back in the file too: whichever save is read, it says the same.
 */
final class GameTestConfig {

	private GameTestConfig() {
	}

	static void wearMode(boolean on) {
		save(CEConfig.WEAR_MODE, on);
		Tuning.wearMode = on;
	}

	static void batteryCold(boolean on) {
		save(CEConfig.BATTERY_COLD, on);
		Tuning.batteryCold = on;
	}

	static void crankingDraw(boolean on) {
		save(CEConfig.CRANKING_DRAW, on);
		Tuning.crankingDraw = on;
	}

	static void heatRecovery(boolean on) {
		save(CEConfig.HEAT_RECOVERY_ENABLED, on);
		Tuning.heatRecoveryEnabled = on;
	}

	/** A setting read straight from the config, with no copy in {@link Tuning}. */
	static <T> void save(ModConfigSpec.ConfigValue<T> setting, T value) {
		setting.set(value);
		setting.save();
	}
}
