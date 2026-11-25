package com.createengines.registry;

import static com.createengines.CreateEngines.REGISTRATE;

import com.createengines.content.display.BatteryDisplaySource;
import com.createengines.content.display.EngineBoostDisplaySource;
import com.createengines.content.display.EngineFaultDisplaySource;
import com.createengines.content.display.EngineFillDisplaySource;
import com.createengines.content.display.EngineHorsepowerDisplaySource;
import com.createengines.content.display.EngineReadout;
import com.createengines.content.display.EngineSpeedDisplaySource;
import com.createengines.content.display.EngineStatusDisplaySource;
import com.createengines.content.display.EngineTorqueDisplaySource;
import com.createengines.content.display.SumpDisplaySource;
import com.createengines.content.display.TransmissionGearDisplaySource;
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
	public static final RegistryEntry<DisplaySource, EngineFillDisplaySource> ENGINE_SMOOTHNESS =
		REGISTRATE.displaySource("engine_smoothness", () -> new EngineFillDisplaySource(EngineReadout::smoothness))
			.register();
	/** The wear mode's total for the whole build, as a percentage or a bar (the plain mean of every part that wears). */
	public static final RegistryEntry<DisplaySource, EngineFillDisplaySource> ENGINE_WEAR =
		REGISTRATE.displaySource("engine_wear", () -> new EngineFillDisplaySource(EngineReadout::wear))
			.register();
	public static final RegistryEntry<DisplaySource, SumpDisplaySource> SUMP_LEVELS =
		REGISTRATE.displaySource("sump_levels", SumpDisplaySource::new)
			.register();

	public static final RegistryEntry<DisplaySource, EngineBoostDisplaySource> ENGINE_BOOST =
		REGISTRATE.displaySource("engine_boost", EngineBoostDisplaySource::new)
			.register();
	public static final RegistryEntry<DisplaySource, EngineTorqueDisplaySource> ENGINE_TORQUE =
		REGISTRATE.displaySource("engine_torque", EngineTorqueDisplaySource::new)
			.register();
	public static final RegistryEntry<DisplaySource, BatteryDisplaySource> BATTERY_CHARGE =
		REGISTRATE.displaySource("battery_charge", BatteryDisplaySource::new)
			.register();

	public static final RegistryEntry<DisplaySource, EngineFaultDisplaySource> ENGINE_FAULT =
		REGISTRATE.displaySource("engine_fault", EngineFaultDisplaySource::new)
			.register();
	public static final RegistryEntry<DisplaySource, TransmissionGearDisplaySource> TRANSMISSION_GEAR =
		REGISTRATE.displaySource("transmission_gear", TransmissionGearDisplaySource::new)
			.register();

	private CEDisplaySources() {
	}

	public static void register() {
	}
}
