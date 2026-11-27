package com.createengines.config;

import java.util.LinkedHashMap;
import java.util.Map;

import com.createengines.engine.sim.Tuning;

import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Server-side calibration. The simulation's internal units are converted to Create's here.
 * <p>
 * 2.0 regrouped the keys once: {@code [calibration]} (units and inertias), {@code [limits]} (how far the scanner looks),
 * {@code [balance]} (multipliers), {@code [features]} (switches) and {@code [compat]} (other mods). The file is
 * {@value #FILE}; the first time it loads it carries every value over from the 1.x file beside it, see {@link ConfigCarryOver}.
 */
public final class CEConfig {

	/** The 2.0 file, in the world's serverconfig folder. The 1.x one is {@link ConfigCarryOver#OLD_SERVER_FILE}. */
	public static final String FILE = "create_engines-v2-server.toml";

	public static final ModConfigSpec SPEC;
	public static final ModConfigSpec.DoubleValue STRESS_PER_TORQUE;
	public static final ModConfigSpec.DoubleValue JOURNAL_INERTIA;
	public static final ModConfigSpec.DoubleValue THROW_INERTIA;
	public static final ModConfigSpec.DoubleValue FLYWHEEL_INERTIA;
	public static final ModConfigSpec.DoubleValue CAST_FLYWHEEL_INERTIA;
	public static final ModConfigSpec.DoubleValue DUAL_MASS_PRIMARY_INERTIA;
	public static final ModConfigSpec.DoubleValue DUAL_MASS_SECONDARY_INERTIA;
	public static final ModConfigSpec.DoubleValue DUAL_MASS_RESONANCE;
	public static final ModConfigSpec.DoubleValue BEARING_FRICTION;
	public static final ModConfigSpec.IntValue MAX_CRANK_SEGMENTS;
	public static final ModConfigSpec.IntValue MAX_MANIFOLD_BLOCKS;
	public static final ModConfigSpec.IntValue MAX_COUPLED_LINES;
	public static final ModConfigSpec.IntValue MAX_COUPLING_WALK;
	public static final ModConfigSpec.IntValue MAX_BANK_BATTERIES;
	public static final ModConfigSpec.IntValue THROTTLE_IDLE_MIN;
	public static final ModConfigSpec.IntValue THROTTLE_IDLE_MAX;
	public static final ModConfigSpec.IntValue THROTTLE_WIDE_MIN;
	public static final ModConfigSpec.IntValue THROTTLE_WIDE_MAX;

	public static final ModConfigSpec.DoubleValue FUEL_CONSUMPTION;
	public static final ModConfigSpec.DoubleValue PRECISION_BONUS;
	public static final ModConfigSpec.DoubleValue BOOST_STRENGTH;
	public static final ModConfigSpec.DoubleValue ROTARY_POWER;
	public static final ModConfigSpec.DoubleValue HEAT_RECOVERY;
	public static final ModConfigSpec.DoubleValue COOLING_BONUS;
	public static final ModConfigSpec.DoubleValue OVERHEAT_PENALTY;
	public static final ModConfigSpec.DoubleValue TORSIONAL_FLEX;
	public static final ModConfigSpec.DoubleValue ALTERNATOR_ENERGY;
	public static final ModConfigSpec.DoubleValue RPM_SCALE;

	public static final ModConfigSpec.BooleanValue FORCED_INDUCTION;
	public static final ModConfigSpec.BooleanValue SPARK_FOULING;
	public static final ModConfigSpec.BooleanValue OIL_DILUTION;
	public static final ModConfigSpec.BooleanValue OIL_AGEING;
	public static final ModConfigSpec.BooleanValue ENGINE_TEMPERATURE;
	public static final ModConfigSpec.BooleanValue BATTERY_COLD;
	public static final ModConfigSpec.BooleanValue CRANKING_DRAW;
	public static final ModConfigSpec.BooleanValue PILOT_INJECTION;
	public static final ModConfigSpec.BooleanValue ROTARY_ENGINES;
	public static final ModConfigSpec.BooleanValue HEAT_RECOVERY_ENABLED;

	public static final ModConfigSpec.BooleanValue ALTITUDE_THINNING;
	public static final ModConfigSpec.BooleanValue SABLE_AIR_PRESSURE;
	public static final ModConfigSpec.BooleanValue FUEL_FIRE;
	public static final ModConfigSpec.BooleanValue FUEL_FIRE_BY_VOLATILITY;
	public static final ModConfigSpec.BooleanValue METER_ALL_HEADS;
	public static final ModConfigSpec.BooleanValue WEAR_MODE;
	public static final ModConfigSpec.DoubleValue ALTITUDE_START;
	public static final ModConfigSpec.DoubleValue ALTITUDE_SCALE;
	public static final ModConfigSpec.DoubleValue ALTITUDE_FLOOR;
	public static final ModConfigSpec.BooleanValue POLLUTION;
	public static final ModConfigSpec.DoubleValue POLLUTION_RATE;
	public static final ModConfigSpec.IntValue POLLUTION_AREA_CAP;
	public static final ModConfigSpec.DoubleValue POLLUTION_ENGINE_CEILING;
	public static final ModConfigSpec.DoubleValue CARBON_RATE;
	public static final ModConfigSpec.DoubleValue SULFUR_RATE;

	private static final ModConfigSpec.BooleanValue CARRIED_OVER;

	/** Every value by its path in the file, so a carried-over 1.x value finds its setting. */
	private static final Map<String, ModConfigSpec.ConfigValue<?>> BY_PATH = new LinkedHashMap<>();

	static {
		ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
		builder.comment("Units and inertias. The simulation's numbers are converted to Create's here.")
			.push("calibration");
		STRESS_PER_TORQUE = value("calibration.stressPerTorque", builder.comment(
				"Stress Capacity (SU per RPM) produced by one unit of simulated engine torque.",
				"A crude single cylinder makes roughly 0.4 units at moderate speed.",
				"Calibrated so a tuned straight-12 on gasoline (about 60k SU at its best speed) is several times a",
				"Create steam engine (16k SU), which it costs far more to build and fuel.")
			.defineInRange("stressPerTorque", 78.7, 1.0, 10000.0));
		JOURNAL_INERTIA = value("calibration.journalInertia", builder.comment("Rotational inertia of a crankshaft journal block.")
			.defineInRange("journalInertia", 0.15, 0.0, 100.0));
		THROW_INERTIA = value("calibration.throwInertia", builder.comment("Rotational inertia of a crank throw block.")
			.defineInRange("throwInertia", 0.2, 0.0, 100.0));
		FLYWHEEL_INERTIA = value("calibration.flywheelInertia",
			builder.comment("Rotational inertia a Create Flywheel adds when coaxial with the crank.")
				.defineInRange("flywheelInertia", 2.5, 0.0, 100.0));
		CAST_FLYWHEEL_INERTIA = value("calibration.castFlywheelInertia",
			builder.comment("Rotational inertia a Cast Flywheel adds when coaxial with the crank.")
				.defineInRange("castFlywheelInertia", 1.6, 0.0, 100.0));
		DUAL_MASS_PRIMARY_INERTIA = value("calibration.dualMassPrimaryInertia", builder.comment(
				"Rotational inertia of a Dual-Mass Flywheel's primary mass, bolted to the crank.",
				"It sets rev response, spool and starter load, like any flywheel.")
			.defineInRange("dualMassPrimaryInertia", 0.45, 0.0, 100.0));
		DUAL_MASS_SECONDARY_INERTIA = value("calibration.dualMassSecondaryInertia", builder.comment(
				"Rotational inertia of a Dual-Mass Flywheel's secondary mass, behind its spring pack.",
				"It smooths the speed ripple the network sees but does not slow the crank, and only above the resonance.")
			.defineInRange("dualMassSecondaryInertia", 1.4, 0.0, 100.0));
		DUAL_MASS_RESONANCE = value("calibration.dualMassResonance", builder.comment(
				"Crank speed (RPM) at which a Dual-Mass Flywheel's spring pack resonates.",
				"The secondary mass does nothing at or below it and counts in full from three times it.")
			.defineInRange("dualMassResonance", 48.0, 1.0, 100000.0));
		BEARING_FRICTION = value("calibration.bearingFriction", builder.comment("Friction torque per crankshaft block.")
			.defineInRange("bearingFriction", 0.01, 0.0, 10.0));
		builder.pop();

		builder.comment("How far the engine scanner looks. Bigger builds need bigger limits and cost more to scan.")
			.push("limits");
		MAX_CRANK_SEGMENTS = value("limits.maxCrankSegments", builder.comment("Longest crankshaft line the engine scanner will follow.")
			.defineInRange("maxCrankSegments", 64, 1, 1024));
		MAX_MANIFOLD_BLOCKS = value("limits.maxManifoldBlocks",
			builder.comment("Largest intake or exhaust manifold network the scanner will follow.")
				.defineInRange("maxManifoldBlocks", 256, 1, 4096));
		MAX_COUPLED_LINES = value("limits.maxCoupledLines", builder.comment(
				"Most crankshafts that can be geared together, one to one, into a single engine.",
				"1 turns coupled crankshafts off: each crank line is then an engine of its own.")
			.defineInRange("maxCoupledLines", 8, 1, 64));
		MAX_COUPLING_WALK = value("limits.maxCouplingWalk", builder.comment(
				"Most kinetic blocks the scanner looks through, from a crankshaft, for other",
				"crankshafts geared to it.")
			.defineInRange("maxCouplingWalk", 512, 1, 8192));
		MAX_BANK_BATTERIES = value("limits.maxBankBatteries", builder.comment(
				"Most touching batteries that act as one bank. A bigger bank acts as more than one.")
			.defineInRange("maxBankBatteries", 4096, 1, 32768));
		THROTTLE_IDLE_MIN = value("limits.throttleIdleMin", builder.comment(
				"Smallest idle stop a Throttle Body's value box offers, as a percent open (a multiple of 5).",
				"Raise it to stop players closing the valve down to a crack.")
			.defineInRange("throttleIdleMin", 5, 5, 100));
		THROTTLE_IDLE_MAX = value("limits.throttleIdleMax", builder.comment(
				"Largest idle stop a Throttle Body's value box offers, as a percent open (a multiple of 5).",
				"Never below throttleIdleMin: if it is, the minimum is used.")
			.defineInRange("throttleIdleMax", 100, 5, 100));
		THROTTLE_WIDE_MIN = value("limits.throttleWideMin", builder.comment(
				"Smallest wide-open bore a Throttle Body's value box offers, as a percent of the plain intake (a multiple of 5).",
				"100 is the plain intake; lower lets a throttle body pinch the intake even at full signal.")
			.defineInRange("throttleWideMin", 100, 5, 100));
		THROTTLE_WIDE_MAX = value("limits.throttleWideMax", builder.comment(
				"Largest wide-open bore a Throttle Body's value box offers, as a percent of the plain intake (a multiple of 5).",
				"150 is the most the simulation allows; lower it to forbid oversized bores. Never below throttleWideMin.")
			.defineInRange("throttleWideMax", 150, 100, 150));
		builder.pop();

		builder.comment("Balance multipliers. 1.0 is the shipped balance.")
			.push("balance");
		FUEL_CONSUMPTION = value("balance.fuelConsumption", builder.comment(
				"Fuel drawn from the tanks for each unit of fuel an engine burns.",
				"Above 1 makes every engine thirstier, below 1 thriftier; the engine's power is unchanged.")
			.defineInRange("fuelConsumption", 1.0, 0.05, 20.0));
		PRECISION_BONUS = value("balance.precisionBonus", builder.comment(
				"How far each precision part (cylinder, head, forged piston, injector) moves a",
				"cylinder away from the crude one. 0 makes them cosmetic, 2 doubles their effect.")
			.defineInRange("precisionBonus", 1.0, 0.0, 2.0));
		BOOST_STRENGTH = value("balance.boostStrength", builder.comment("Scales the boost made by every turbocharger and supercharger.")
			.defineInRange("boostStrength", 1.0, 0.0, 2.0));
		ROTARY_POWER = value("balance.rotaryPower", builder.comment(
				"Scales a Wankel rotor chamber's swept volume, and so its power and thirst.",
				"Rotary engines are tuned as a niche (compact and smooth but thirsty, weak at low speed);",
				"raising this shifts that balance.")
			.defineInRange("rotaryPower", 1.0, 0.25, 2.0));
		HEAT_RECOVERY = value("balance.heatRecoveryOutput", builder.comment(
				"Scales the heat an Exhaust Heat Exchanger takes from an engine's exhaust.",
				"Create's basins need a heated or superheated source below them, and the exchanger gets there",
				"only with enough exhaust heat, so lower values need a bigger engine.")
			.defineInRange("heatRecoveryOutput", 1.0, 0.1, 10.0));
		COOLING_BONUS = value("balance.coolingBonus", builder.comment(
				"Scales how much better a well cooled engine breathes: about 6 to 9 percent more",
				"power at 1.0. 0 removes the bonus. Cold engines drag and uncooled ones earn nothing either way.",
				"Nothing breaks or wears out from heat outside the wear mode (wearMode).")
			.defineInRange("coolingBonus", 1.0, 0.0, 3.0));
		OVERHEAT_PENALTY = value("balance.overheatPenalty", builder.comment(
				"Scales the power an overheating engine loses: from 140 degrees each burn gets less done,",
				"up to the most it loses (about a quarter) at 165 degrees, and it comes back as the engine cools. Nothing breaks or stops.",
				"0 turns the loss off.")
			.defineInRange("overheatPenalty", 1.0, 0.0, 1.0));
		TORSIONAL_FLEX = value("balance.torsionalFlex", builder.comment(
				"Scales the rating an engine loses to a long crankshaft: past 8 throws on one unbroken",
				"crank line it falls by 2.5 percent a throw at 1.0. A vee or flat has half as many throws as cylinders,",
				"and cranks geared together one to one are separate lines. 0 turns it off.")
			.defineInRange("torsionalFlex", 1.0, 0.0, 2.0));
		ALTERNATOR_ENERGY = value("balance.alternatorEnergy", builder.comment(
				"Forge Energy an Alternator makes each tick for every RPM of its shaft, while something is",
				"drawing it. It puts 2 stress units on the shaft for each RPM while it does, so the default of 2 makes",
				"one stress unit worth about one FE a tick. 0 turns FE output off; spark charging is unaffected.")
			.defineInRange("alternatorEnergy", 2.0, 0.0, 100.0));
		RPM_SCALE = value("balance.rpmScale", builder.comment(
				"Turns every engine's shaft this many times as fast, with its stress capacity per RPM divided to match: an",
				"engine giving 10k SU at 256 RPM gives the same 10k SU at 512 RPM at 2. It climbs through the speeds that much",
				"faster too. The engine itself runs as at 1, on the same fuel. Speeds shown and set on the engine are the shaft's.",
				"Above 1, Create's maxRotationSpeed needs raising to match (512 for 2 from the default 256): the scale only goes",
				"as far as that limit divided by 256, and at 256 it does nothing. Machines on the shaft turn faster and so ask",
				"for more SU, as behind any gearing.")
			.defineInRange("rpmScale", 1.0, 0.25, 8.0));
		builder.pop();

		builder.comment("Feature switches. Turning one off never destroys blocks; the machines just stop doing that job.")
			.push("features");
		FORCED_INDUCTION = value("features.forcedInduction",
			builder.comment("Turbochargers and superchargers make boost (and turbines add back-pressure).")
				.define("forcedInduction", true));
		SPARK_FOULING = value("features.sparkFouling", builder.comment("A spark plug fouls with soot when its engine runs rich.")
			.define("sparkFouling", true));
		OIL_DILUTION = value("features.oilDilution", builder.comment("Unburned fuel thins an engine's oil when it runs rich.")
			.define("oilDilution", true));
		OIL_AGEING = value("features.oilAgeing", builder.comment(
				"Engine oil goes off with use and drags a little more until it is changed.",
				"Off, oil stays new for ever and the oil exchange setting does nothing useful.")
			.define("oilAgeing", true));
		ENGINE_TEMPERATURE = value("features.engineTemperature",
			builder.comment("Engines warm up and cool down: a cold engine drags, a cooled one breathes better.")
				.define("engineTemperature", true));
		BATTERY_COLD = value("features.batteryCold", builder.comment(
				"A battery in a cold biome gives less of its charge, and a cold engine makes each spark cost more of it.",
				"A heat source beside the battery, or a warm engine, takes the cold away. Off, charge is the same in any weather.")
			.define("batteryCold", true));
		CRANKING_DRAW = value("features.crankingDraw", builder.comment(
				"A starter draws charge from the engine's batteries while it turns a spark engine over, and turns it more slowly",
				"as they run down. Compression engines, and spark engines with no battery, are not affected. Off, cranking is free.")
			.define("crankingDraw", true));
		PILOT_INJECTION = value("features.pilotInjection", builder.comment(
				"A compression engine can inject a pilot shot of diesel (from an oil pan's pilot tank)",
				"to light gasoline or ethanol that compression alone could not.")
			.define("pilotInjection", true));
		ROTARY_ENGINES = value("features.rotaryEngines", builder.comment("Rotor housings run. Off, they sit inert and say so in the goggles.")
			.define("rotaryEngines", true));
		HEAT_RECOVERY_ENABLED = value("features.heatRecovery",
			builder.comment("Exhaust Heat Exchangers take heat from the exhaust and warm basins.")
				.define("heatRecovery", true));
		METER_ALL_HEADS = value("features.meterAllHeads", builder.comment(
				"Setting the fuel metering of one cylinder head (or rotor housing) sets every other injector of that engine",
				"to the same figure. Each head keeps its own dial and can still be changed on its own afterwards; heads held by",
				"a MAF sensor or a closed fuel rack are left alone. Off, each head is set by hand as before.")
			.define("meterAllHeads", false));
		WEAR_MODE = value("features.wearMode", builder.comment(
				"Wear and tear. On, pistons, connecting rods and head gaskets wear with use (faster with speed, heat, boost and",
				"no oil), a worn piston or gasket costs power, and a badly worn rod can be thrown or a gasket blown, which stops",
				"that cylinder until the part is replaced. A spark engine that knocks (boost lighting the charge early) wears its",
				"pistons faster, and can be heard. A rotor housing's apex seals wear the same way and a failed set stops",
				"the rotor. Batteries wear too: deep discharge, and drawing on them in the cold, slowly reduce what they hold, but",
				"never to nothing. Light wear is set right with a Service Kit. Off, nothing wears, no gasket is needed, and every",
				"engine runs exactly as it did before the mode existed (a gasket fitted to a head still seals it, on or off).")
			.define("wearMode", false));
		builder.pop();

		builder.comment("Other mods, and how the world around an engine answers it.")
			.push("compat");
		ALTITUDE_THINNING = value("compat.altitudeThinning", builder.comment(
				"The air thins with height: past sea level an engine's intake charge falls, a little at first and",
				"more steeply near the build limit, so a naturally aspirated engine loses power as it climbs and a",
				"turbocharger or supercharger earns its place. Boost stages offset it. Off, the air is the same at every",
				"height. Nothing else about an engine changes, and it never destroys anything.")
			.define("altitudeThinning", true));
		SABLE_AIR_PRESSURE = value("compat.sableAirPressure", builder.comment(
				"With Sable (Create Aeronautics) installed, the air an engine breathes is Sable's own air pressure at the",
				"engine's height, so it follows the dimension's pressure curve, datapacks included. Off, or without Sable,",
				"the height curve below is used instead.")
			.define("sableAirPressure", true));
		FUEL_FIRE = value("compat.fuelFire", builder.comment(
				"Liquid fuel lying in the world burns: fire, lava or burning fuel beside a pool lights it, and it burns off",
				"in a wave through the pool, leaving fire where it was. Off, fuel on the ground only lies there as before.")
			.define("fuelFire", true));
		FUEL_FIRE_BY_VOLATILITY = value("compat.fuelFireByVolatility", builder.comment(
				"A spill's fire follows the fuel: gasoline and methanol light surely and burn off in a quick wave, ethanol a little",
				"slower, and diesel, biodiesel, kerosene and plant oil are slow to catch and spread. Only fuel lying in the world",
				"is affected, never an engine's tank or a running engine. Off, every spilled fuel burns alike. Needs fuelFire.")
			.define("fuelFireByVolatility", true));
		ALTITUDE_START = value("compat.altitudeStart", builder.comment(
				"The height in blocks up to which the air is as thick as at sea level. Used when Sable's pressure is not.")
			.defineInRange("altitudeStart", 128.0, -64.0, 100000.0));
		ALTITUDE_SCALE = value("compat.altitudeScale", builder.comment(
				"Blocks of climb over which the air thins by a factor of e. Smaller is steeper. Used when Sable's pressure is not.")
			.defineInRange("altitudeScale", 1200.0, 1.0, 100000.0));
		ALTITUDE_FLOOR = value("compat.altitudeFloor", builder.comment(
				"The thinnest air an engine is given, as a share of sea level, whichever source says it. Raise it to keep",
				"engines running high up, lower it to starve them; the default 0.25 is about the ceiling of a World War II piston engine.")
			.defineInRange("altitudeFloor", 0.25, 0.01, 1.0));
		POLLUTION = value("compat.pollution", builder.comment(
				"With Pollution of the Realms installed, engines put carbon and sulfur into the air through an open",
				"exhaust outlet in proportion to the fuel they burn. Without that mod this does nothing. Off, they",
				"stay clean. Engine behaviour is never read back from it.")
			.define("pollution", true));
		POLLUTION_RATE = value("compat.pollutionRate", builder.comment(
				"Scales how much gas an engine puts out. 1 is the full amount the engine would release; the default is a quarter of that. 0 is the same as turning pollution off.")
			.defineInRange("pollutionRate", 0.25, 0.0, 10.0));
		CARBON_RATE = value("compat.carbonRate", builder.comment(
				"Scales the carbon an engine puts out, on top of pollutionRate. 0 means no carbon, only sulfur.")
			.defineInRange("carbonRate", 1.0, 0.0, 10.0));
		SULFUR_RATE = value("compat.sulfurRate", builder.comment(
				"Scales the sulfur an engine puts out, on top of pollutionRate. 0 means no sulfur, only carbon.")
			.defineInRange("sulfurRate", 1.0, 0.0, 10.0));
		POLLUTION_AREA_CAP = value("compat.pollutionAreaCap", builder.comment(
				"An outlet stops releasing while the air in the 7 by 7 by 7 block cube in front of it already holds this many",
				"steps of gas (carbon and sulfur together; one block holds 3 of a gas at most). It starts again as the gas",
				"thins or spreads, so a factory stays hazy rather than growing unlivable. 0, the default, turns the cap off; 150 is a",
				"starting guess (about 15 percent of the cube's capacity), not a tuned figure. Each outlet reads its air every 3",
				"seconds and counts what it has released since.")
			.defineInRange("pollutionAreaCap", 0, 0, 100000));
		POLLUTION_ENGINE_CEILING = value("compat.pollutionEngineCeiling", builder.comment(
				"The most steps of each gas one engine may release per minute of running, however much it burns. 0 turns the",
				"ceiling off.")
			.defineInRange("pollutionEngineCeiling", 0.0, 0.0, 100000.0));
		builder.pop();

		builder.comment("Set once the 1.x file has been read. Leave it alone.")
			.push("meta");
		CARRIED_OVER = builder.define("carriedOver", false);
		builder.pop();
		SPEC = builder.build();
	}

	private CEConfig() {
	}

	private static <T extends ModConfigSpec.ConfigValue<?>> T value(String path, T value) {
		BY_PATH.put(path, value);
		return value;
	}

	/** Copies the loaded values to the plain-Java {@link Tuning} the simulation reads. */
	public static void onConfig(ModConfigEvent event) {
		// Nothing can be read once the config is unloading, and the settings simply stay as they were until the next load
		if (event.getConfig().getSpec() != SPEC || event instanceof ModConfigEvent.Unloading)
			return;
		if (event instanceof ModConfigEvent.Loading)
			ConfigFiles.carryOver(event.getConfig(), ConfigCarryOver.OLD_SERVER_FILE, ConfigCarryOver.SERVER, BY_PATH,
				CARRIED_OVER);
		applyTuning();
	}

	/** Every setting by its dotted path in the file ({@code balance.fuelConsumption}), in the order the file lists them. */
	public static Map<String, ModConfigSpec.ConfigValue<?>> values() {
		return java.util.Collections.unmodifiableMap(BY_PATH);
	}

	/**
	 * Copies the settings as they are now to {@link Tuning}. The config event does this when the file loads or changes on
	 * disk; a command that sets values itself calls it so the change is felt at once.
	 */
	public static void applyTuning() {
		Tuning.fuelConsumption = FUEL_CONSUMPTION.get();
		Tuning.precisionBonus = PRECISION_BONUS.get();
		Tuning.boostStrength = BOOST_STRENGTH.get();
		Tuning.rotaryPower = ROTARY_POWER.get();
		Tuning.heatRecovery = HEAT_RECOVERY.get();
		Tuning.coolingBonus = COOLING_BONUS.get();
		Tuning.overheatPenalty = OVERHEAT_PENALTY.get();
		Tuning.torsionalFlex = TORSIONAL_FLEX.get();
		Tuning.forcedInduction = FORCED_INDUCTION.get();
		Tuning.sparkFouling = SPARK_FOULING.get();
		Tuning.oilDilution = OIL_DILUTION.get();
		Tuning.oilAgeing = OIL_AGEING.get();
		Tuning.engineTemperature = ENGINE_TEMPERATURE.get();
		Tuning.batteryCold = BATTERY_COLD.get();
		Tuning.crankingDraw = CRANKING_DRAW.get();
		Tuning.pilotInjection = PILOT_INJECTION.get();
		Tuning.rotaryEngines = ROTARY_ENGINES.get();
		Tuning.heatRecoveryEnabled = HEAT_RECOVERY_ENABLED.get();
		Tuning.altitudeThinning = ALTITUDE_THINNING.get();
		Tuning.sableAirPressure = SABLE_AIR_PRESSURE.get();
		Tuning.fuelFire = FUEL_FIRE.get();
		Tuning.fuelFireByVolatility = FUEL_FIRE_BY_VOLATILITY.get();
		Tuning.meterAllHeads = METER_ALL_HEADS.get();
		Tuning.wearMode = WEAR_MODE.get();
		Tuning.altitudeStart = ALTITUDE_START.get();
		Tuning.altitudeScale = ALTITUDE_SCALE.get();
		Tuning.altitudeFloor = ALTITUDE_FLOOR.get();
		// The throttle dial ranges come from the config, so the blueprint table is rebuilt on the next read
		com.createengines.content.blueprint.EngineDials.invalidate();
	}
}
