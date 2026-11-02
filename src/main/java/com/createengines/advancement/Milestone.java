package com.createengines.advancement;

import java.util.Locale;

/**
 * Something that happens in the world that an advancement can wait for. Each one is fired by name through
 * {@link EventTrigger}, so adding an achievement for a new feature is a constant here, a line where it happens, and
 * an entry in {@link CEAdvancements}.
 */
public enum Milestone {
	// Engine
	CRANKING,
	ENGINE_RUNNING,
	FOUR_CYLINDERS,
	EIGHT_CYLINDERS,
	TWELVE_CYLINDERS,
	HIGH_REVS,
	REDLINE,
	HEAVY_DUTY,
	STEAM_HERD,
	STEAM_TITAN,
	SPARK_ENGINE,
	WELL_OILED,
	DRY_RUN,
	SMOKE_SIGNALS,
	STALLED,
	OVERLOADED,
	CHARGED_BATTERY,
	// Cams and firing order
	PUSHROD_CAM,
	VALLEY_CAM,
	EVEN_FIRING,
	// Air-fuel ratio
	DIALED_IN,
	FOULED_PLUG,
	THINNED_OIL,
	// Turbocharger
	BOOST,
	BIG_BOOST,
	BLOW_OFF,
	// Superchargers, intercoolers and several stages of boost
	SUPERCHARGED,
	COMPOUND_BOOST,
	TWIN_TURBO,
	TWINCHARGED,
	COOLED_CHARGE,
	// Transmission
	SHIFTED,
	REVERSE,
	EVERY_GEAR,
	// Engine management
	MAF_TRIM,
	MAF_ENRICH,
	// Speed control
	GOVERNED,
	// Output and precision parts
	STEAM_BEATER,
	PRECISION_ENGINE,
	// Firing order tool
	TOOL_TIMED,
	// Big engines and coupled cranks
	SIXTEEN_CYLINDERS,
	COUPLED_CRANKS,
	// Parts doing their job in a running engine
	CAMSHAFT_TIMED,
	PIPED_ENGINE,
	THROTTLED,
	DIESEL_BURNED,
	GASOLINE_BURNED,
	OILED,
	SUMP_OILING,
	MAF_READING,
	GOVERNOR_ACTIVE,
	GOVERNOR_COMMANDED,
	PRECISION_BORE,
	PRECISION_HEAD,
	PRECISION_PISTON,
	PRECISION_INJECTOR,
	ALTERNATOR_CHARGING,
	ENERGY_GENERATED,
	TOOL_USED,
	FUEL_TUNED,
	// Laying out banks of cylinders
	BANK_BUILT,
	DYNO_RUN,
	VEE_ENGINE,
	FLAT_ENGINE,
	// Flywheels, variable-geometry turbos and anti-lag
	FLYWHEEL_SMOOTH,
	VARIABLE_VANES,
	ANTI_LAG,
	// Wankel rotors
	ROTOR_HOUSING_TURNED,
	ROTARY_ENGINE,
	ROTARY_REVS,
	TWIN_ROTORS,
	// Refining fuel and recovering exhaust heat
	ETHANOL_BURNED,
	HEAT_RECOVERED,
	SUPERHEAT_RECOVERED,
	// Oil service, temperature, coolant and pilot injection
	OIL_EXCHANGED,
	OIL_FILTERED,
	OIL_SPENT,
	OIL_KEPT_FRESH,
	WARMED_UP,
	COOLANT_USED,
	COOL_RUNNING,
	DUAL_FUEL,
	WASTE_OIL_BURNED,
	// A long crank, split up
	STIFF_CRANK;

	/** The name the advancement's criterion carries. */
	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}
}
