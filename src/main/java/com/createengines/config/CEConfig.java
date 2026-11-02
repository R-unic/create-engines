package com.createengines.config;

import com.createengines.engine.sim.Tuning;

import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Server-side calibration. The simulation's internal units are converted to Create's here. */
public final class CEConfig {

	public static final ModConfigSpec SPEC;
	public static final ModConfigSpec.DoubleValue STRESS_PER_TORQUE;
	public static final ModConfigSpec.DoubleValue JOURNAL_INERTIA;
	public static final ModConfigSpec.DoubleValue THROW_INERTIA;
	public static final ModConfigSpec.DoubleValue FLYWHEEL_INERTIA;
	public static final ModConfigSpec.DoubleValue CAST_FLYWHEEL_INERTIA;
	public static final ModConfigSpec.DoubleValue BEARING_FRICTION;
	public static final ModConfigSpec.IntValue MAX_CRANK_SEGMENTS;
	public static final ModConfigSpec.IntValue MAX_MANIFOLD_BLOCKS;
	public static final ModConfigSpec.IntValue MAX_COUPLED_LINES;
	public static final ModConfigSpec.IntValue MAX_COUPLING_WALK;

	public static final ModConfigSpec.DoubleValue FUEL_CONSUMPTION;
	public static final ModConfigSpec.DoubleValue PRECISION_BONUS;
	public static final ModConfigSpec.DoubleValue BOOST_STRENGTH;
	public static final ModConfigSpec.DoubleValue ROTARY_POWER;
	public static final ModConfigSpec.DoubleValue HEAT_RECOVERY;
	public static final ModConfigSpec.DoubleValue COOLING_BONUS;
	public static final ModConfigSpec.DoubleValue TORSIONAL_FLEX;
	public static final ModConfigSpec.DoubleValue ALTERNATOR_ENERGY;

	public static final ModConfigSpec.BooleanValue FORCED_INDUCTION;
	public static final ModConfigSpec.BooleanValue SPARK_FOULING;
	public static final ModConfigSpec.BooleanValue OIL_DILUTION;
	public static final ModConfigSpec.BooleanValue OIL_AGEING;
	public static final ModConfigSpec.BooleanValue ENGINE_TEMPERATURE;
	public static final ModConfigSpec.BooleanValue PILOT_INJECTION;
	public static final ModConfigSpec.BooleanValue ROTARY_ENGINES;
	public static final ModConfigSpec.BooleanValue HEAT_RECOVERY_ENABLED;

	static {
		ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
		builder.push("engine");
		STRESS_PER_TORQUE = builder.comment(
				"Stress Capacity (SU per RPM) produced by one unit of simulated engine torque.",
				"A crude single cylinder makes roughly 0.4 units at moderate speed.",
				"Calibrated so a tuned straight-12 on gasoline (about 60k SU at its best speed) is several times a",
				"Create steam engine (16k SU), which it costs far more to build and fuel.")
			.defineInRange("stressPerTorque", 100.0, 1.0, 10000.0);
		JOURNAL_INERTIA = builder.comment("Rotational inertia of a crankshaft journal block.")
			.defineInRange("journalInertia", 0.15, 0.0, 100.0);
		THROW_INERTIA = builder.comment("Rotational inertia of a crank throw block.")
			.defineInRange("throwInertia", 0.2, 0.0, 100.0);
		FLYWHEEL_INERTIA = builder.comment("Rotational inertia a Create Flywheel adds when coaxial with the crank.")
			.defineInRange("flywheelInertia", 1.5, 0.0, 100.0);
		CAST_FLYWHEEL_INERTIA = builder.comment("Rotational inertia a Cast Flywheel adds when coaxial with the crank.")
			.defineInRange("castFlywheelInertia", 1.0, 0.0, 100.0);
		BEARING_FRICTION = builder.comment("Friction torque per crankshaft block.")
			.defineInRange("bearingFriction", 0.01, 0.0, 10.0);
		MAX_CRANK_SEGMENTS = builder.comment("Longest crankshaft line the engine scanner will follow.")
			.defineInRange("maxCrankSegments", 64, 1, 1024);
		MAX_MANIFOLD_BLOCKS = builder.comment("Largest intake or exhaust manifold network the scanner will follow.")
			.defineInRange("maxManifoldBlocks", 256, 1, 4096);
		MAX_COUPLED_LINES = builder.comment("Most crankshafts that can be geared together, one to one, into a single engine.",
				"1 turns coupled crankshafts off: each crank line is then an engine of its own.")
			.defineInRange("maxCoupledLines", 8, 1, 64);
		MAX_COUPLING_WALK = builder.comment("Most kinetic blocks the scanner looks through, from a crankshaft, for other",
				"crankshafts geared to it.")
			.defineInRange("maxCouplingWalk", 512, 1, 8192);
		builder.pop();

		builder.comment("Balance multipliers. 1.0 is the shipped balance.")
			.push("balance");
		FUEL_CONSUMPTION = builder.comment("Fuel drawn from the tanks for each unit of fuel an engine burns.",
				"Above 1 makes every engine thirstier, below 1 thriftier; the engine's power is unchanged.")
			.defineInRange("fuelConsumption", 1.0, 0.05, 20.0);
		PRECISION_BONUS = builder.comment("How far each precision part (cylinder, head, forged piston, injector) moves a",
				"cylinder away from the crude one. 0 makes them cosmetic, 2 doubles their effect.")
			.defineInRange("precisionBonus", 1.0, 0.0, 2.0);
		BOOST_STRENGTH = builder.comment("Scales the boost made by every turbocharger and supercharger.")
			.defineInRange("boostStrength", 1.0, 0.0, 2.0);
		ROTARY_POWER = builder.comment("Scales a Wankel rotor chamber's swept volume, and so its power and thirst.",
				"Rotary engines are tuned as a niche (compact and smooth but thirsty, weak at low speed);",
				"raising this shifts that balance.")
			.defineInRange("rotaryPower", 1.0, 0.25, 2.0);
		HEAT_RECOVERY = builder.comment("Scales the heat an Exhaust Heat Exchanger takes from an engine's exhaust.",
				"Create's basins need a heated or superheated source below them, and the exchanger gets there",
				"only with enough exhaust heat, so lower values need a bigger engine.")
			.defineInRange("heatRecoveryOutput", 1.0, 0.1, 10.0);
		COOLING_BONUS = builder.comment("Scales how much better a well cooled engine breathes: about 6 to 9 percent more",
				"power at 1.0. 0 removes the bonus. Cold engines drag and uncooled ones earn nothing either way,",
				"and nothing ever overheats or wears out.")
			.defineInRange("coolingBonus", 1.0, 0.0, 3.0);
		TORSIONAL_FLEX = builder.comment("Scales the rating an engine loses to a long crankshaft: past 8 throws on one unbroken",
				"crank line it falls by 4.5 percent a throw at 1.0. A vee or flat has half as many throws as cylinders,",
				"and cranks geared together one to one are separate lines. 0 turns it off.")
			.defineInRange("torsionalFlex", 1.0, 0.0, 2.0);
		ALTERNATOR_ENERGY = builder.comment("Forge Energy an Alternator makes each tick for every RPM of its shaft, while something is",
				"drawing it. It puts 2 stress units on the shaft for each RPM while it does, so the default of 2 makes",
				"one stress unit worth about one FE a tick. 0 turns FE output off; spark charging is unaffected.")
			.defineInRange("alternatorEnergy", 2.0, 0.0, 100.0);
		builder.pop();

		builder.comment("Feature switches. Turning one off never destroys blocks; the machines just stop doing that job.")
			.push("features");
		FORCED_INDUCTION = builder.comment("Turbochargers and superchargers make boost (and turbines add back-pressure).")
			.define("forcedInduction", true);
		SPARK_FOULING = builder.comment("A spark plug fouls with soot when its engine runs rich.")
			.define("sparkFouling", true);
		OIL_DILUTION = builder.comment("Unburned fuel thins an engine's oil when it runs rich.")
			.define("oilDilution", true);
		OIL_AGEING = builder.comment("Engine oil goes off with use and drags a little more until it is changed.",
				"Off, oil stays new for ever and the oil exchange setting does nothing useful.")
			.define("oilAgeing", true);
		ENGINE_TEMPERATURE = builder.comment("Engines warm up and cool down: a cold engine drags, a cooled one breathes better.")
			.define("engineTemperature", true);
		PILOT_INJECTION = builder.comment("A compression engine can inject a pilot shot of diesel (from an oil pan's pilot tank)",
				"to light gasoline or ethanol that compression alone could not.")
			.define("pilotInjection", true);
		ROTARY_ENGINES = builder.comment("Rotor housings run. Off, they sit inert and say so in the goggles.")
			.define("rotaryEngines", true);
		HEAT_RECOVERY_ENABLED = builder.comment("Exhaust Heat Exchangers take heat from the exhaust and warm basins.")
			.define("heatRecovery", true);
		builder.pop();
		SPEC = builder.build();
	}

	private CEConfig() {
	}

	/** Copies the loaded values to the plain-Java {@link Tuning} the simulation reads. */
	public static void onConfig(ModConfigEvent event) {
		// Nothing can be read once the config is unloading, and the settings simply stay as they were until the next load
		if (event.getConfig().getSpec() != SPEC || event instanceof ModConfigEvent.Unloading)
			return;
		Tuning.fuelConsumption = FUEL_CONSUMPTION.get();
		Tuning.precisionBonus = PRECISION_BONUS.get();
		Tuning.boostStrength = BOOST_STRENGTH.get();
		Tuning.rotaryPower = ROTARY_POWER.get();
		Tuning.heatRecovery = HEAT_RECOVERY.get();
		Tuning.coolingBonus = COOLING_BONUS.get();
		Tuning.torsionalFlex = TORSIONAL_FLEX.get();
		Tuning.forcedInduction = FORCED_INDUCTION.get();
		Tuning.sparkFouling = SPARK_FOULING.get();
		Tuning.oilDilution = OIL_DILUTION.get();
		Tuning.oilAgeing = OIL_AGEING.get();
		Tuning.engineTemperature = ENGINE_TEMPERATURE.get();
		Tuning.pilotInjection = PILOT_INJECTION.get();
		Tuning.rotaryEngines = ROTARY_ENGINES.get();
		Tuning.heatRecoveryEnabled = HEAT_RECOVERY_ENABLED.get();
	}
}
