package com.createengines.registry;

import static com.createengines.CreateEngines.REGISTRATE;

import com.createengines.content.display.EngineFillDisplaySource;
import com.createengines.content.display.EngineHorsepowerDisplaySource;
import com.createengines.content.display.EngineReadout;
import com.createengines.content.display.EngineSpeedDisplaySource;
import com.createengines.content.display.EngineStatusDisplaySource;
import com.createengines.content.display.SumpDisplaySource;
import com.simibubi.create.api.behaviour.display.DisplaySource;
import com.tterrag.registrate.util.entry.RegistryEntry;

/**
 * What a Create Display Link can read off the mod's blocks. The crankshaft blocks (crankshaft, crank throw and rotor
 * housing) offer the engine sources and the oil sump offers its tank levels; {@link CEBlocks} attaches each one.
 */
public final class CEDisplaySources {

	public static final RegistryEntry<DisplaySource, EngineStatusDisplaySource> ENGINE_STATUS =
		REGISTRATE.displaySource("engine_status", EngineStatusDisplaySource::new)
			.register();
	public static final RegistryEntry<DisplaySource, EngineSpeedDisplaySource> ENGINE_SPEED =
		REGISTRATE.displaySource("engine_speed", EngineSpeedDisplaySource::new)
			.register();
	public static final RegistryEntry<DisplaySource, EngineHorsepowerDisplaySource> ENGINE_POWER =
		REGISTRATE.displaySource("engine_power", EngineHorsepowerDisplaySource::new)
			.register();
	public static final RegistryEntry<DisplaySource, EngineFillDisplaySource> ENGINE_LOAD =
		REGISTRATE.displaySource("engine_load", () -> new EngineFillDisplaySource(EngineReadout::load))
			.register();
	public static final RegistryEntry<DisplaySource, EngineFillDisplaySource> ENGINE_TEMPERATURE =
		REGISTRATE.displaySource("engine_temperature", () -> new EngineFillDisplaySource(EngineReadout::warmth))
			.register();
	public static final RegistryEntry<DisplaySource, EngineFillDisplaySource> ENGINE_OIL =
		REGISTRATE.displaySource("engine_oil", () -> new EngineFillDisplaySource(EngineReadout::oil))
			.register();
	public static final RegistryEntry<DisplaySource, SumpDisplaySource> SUMP_LEVELS =
		REGISTRATE.displaySource("sump_levels", SumpDisplaySource::new)
			.register();

	private CEDisplaySources() {
	}

	public static void register() {
	}
}
