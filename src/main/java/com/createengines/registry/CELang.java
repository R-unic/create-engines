package com.createengines.registry;

import static com.createengines.CreateEngines.REGISTRATE;

import com.createengines.CreateEngines;
import com.createengines.ponder.CEPonderPlugin;
import com.tterrag.registrate.providers.ProviderType;

import net.createmod.catnip.lang.LangBuilder;
import net.createmod.ponder.foundation.PonderIndex;

/** Text that isn't a block or item name. Generated into en_us.json by datagen. */
public final class CELang {

	private CELang() {
	}

	public static LangBuilder builder() {
		return new LangBuilder(CreateEngines.MOD_ID);
	}

	public static LangBuilder translate(String key, Object... args) {
		return builder().translate(key, args);
	}

	public static LangBuilder text(String text) {
		return builder().text(text);
	}

	public static void register() {
		// Ponder scene text is written in the scenes; datagen collects it into the lang file
		REGISTRATE.addDataGenerator(ProviderType.LANG, provider -> {
			PonderIndex.addPlugin(new CEPonderPlugin());
			PonderIndex.getLangAccess()
				.provideLang(CreateEngines.MOD_ID, provider::add);
		});

		REGISTRATE.addRawLang("itemGroup.create_engines.main", "Create: Engine Engineer");

		raw("value.throw_angle", "Throw Angle");
		raw("value.lobe_angle", "Lobe Angle");
		raw("value.fuel_metering", "Fuel Metering");
		raw("value.throttle_idle", "Idle Stop");
		raw("value.throttle_wide", "Wide-Open Bore");

		raw("goggles.throttle", "Throttle Body");
		raw("goggles.throttle.open", "Open: %1$s%%");
		raw("goggles.throttle.hint",
			"Redstone opens it from its idle stop to wide open. Less air means less power, and its linkage trims the injectors' fuel to match. Set the wide-open bore above 100% and a full signal opens it past the plain intake: a fast engine that cannot fill gets more air, which is how it turns faster");

		raw("value.maf_target", "Mixture Target");
		raw("value.boost_limit", "Boost Limit");
		raw("value.anti_lag", "Anti-Lag");
		raw("value.governed_speed", "Governed Speed");
		raw("value.governed_speed.exact", "Exact RPM");
		raw("value.governed_speed.coarse", "RPM in fives");

		raw("goggles.governor", "Governor");
		raw("goggles.governor.no_engine", "Not touching a crankshaft block or a cylinder head");
		raw("goggles.governor.speed", "Crank speed: %1$s RPM");
		raw("goggles.governor.rack", "Fuel rack held at: %1$s%%");
		raw("goggles.governor.open", "Rack wide open: the load is more than the engine can hold at this speed");
		raw("goggles.governor.target", "Set to: %1$s RPM");
		raw("goggles.governor.commanded", "Redstone has turned it down: holding %1$s RPM");
		raw("goggles.governor.hint",
			"Fuel (or throttle, in a spark engine) is cut as the engine passes the setting and opened as it falls, over about %1$s%% of the speed. The lowest setting on an engine governs. A redstone signal on the block holds the engine at that share of the setting, 15 being all of it, never below 20 RPM. The redstone output is the rack position, 15 wide open, and reaches comparators only");

		raw("tool.timing_tool.style", "Timing Tool: %1$s");
		raw("tool.timing_tool.style.smooth", "Smooth (any throw angle)");
		raw("tool.timing_tool.style.cross_plane", "Cross-plane (quarter turns)");
		raw("tool.timing_tool.style.flat_plane", "Flat-plane (half turns)");
		raw("tool.timing_tool.style.cams_only", "Cams only (leave the crank)");
		raw("tool.bank_builder.layout", "Bank Builder: %1$s, %2$s");
		raw("tool.bank_builder.grade.standard", "standard parts");
		raw("tool.bank_builder.grade.precision", "precision parts");
		raw("tool.bank_builder.upgraded", "Swapped %1$s standard part(s) for precision ones; the old parts are back in your inventory");
		raw("tool.bank_builder.layout.inline", "Inline (the face you click)");
		raw("tool.bank_builder.layout.vee", "Vee (that face and the next, a quarter turn round)");
		raw("tool.bank_builder.layout.flat", "Flat (that face and the one opposite)");
		raw("tool.bank_builder.hint", "Use on a crank throw to set a cylinder and head on that face of every throw of the crankshaft, with the pistons, rods, injectors and spark plugs you carry, and a cam line beside the bank if you carry Camshafts and Cam Lobes. Sneak-use to change layout, and then between standard and precision parts: a precision build swaps precision parts in for standard ones too");
		raw("tool.bank_builder.cams", "Laid %1$s cam block(s) beside the bank. Gear the line to half the crank's speed, then time the lobes with a Timing Tool");
		raw("tool.bank_builder.no_crank", "Use it on a crankshaft throw");
		raw("tool.bank_builder.no_throws", "That crankshaft has no throw to build on");
		raw("tool.bank_builder.built", "Placed %1$s cylinder(s) and %2$s head(s) on %3$s throw(s)");
		raw("tool.bank_builder.blocked", "%1$s place(s) were blocked by something else");
		raw("tool.bank_builder.missing", "%1$s cylinder or head block(s) were missing from your inventory");
		raw("tool.timing_tool.style.tune", "Tune fuel (use on a running engine)");
		raw("tool.timing_tool.hint", "Use on an engine to set its throws and cam lobes, or to tune its fuel in tune mode. Sneak-use to change mode");
		raw("tool.timing_tool.tune.stopped", "Start the engine first: it tunes the fuel to the speed it is turning");
		raw("tool.timing_tool.tune.nothing", "No injector to tune: none has fuel in it, or each is held by a MAF sensor or a closed rack");
		raw("tool.timing_tool.tune.done", "Tuned %1$s injector(s) for a balanced mixture at %2$s RPM");
		raw("tool.timing_tool.tune.skipped", "%1$s cylinder(s) were left alone: no shot to read, a MAF sensor or a closed rack");
		raw("tool.timing_tool.no_engine", "Not part of an engine");
		raw("tool.timing_tool.no_cylinders", "No working cylinder with a cam lobe to time");
		raw("tool.timing_tool.single", "Timed one cylinder");
		raw("tool.timing_tool.even", "Timed %1$s cylinders, one firing every %2$s°: %3$s");
		raw("tool.timing_tool.uneven", "Timed %1$s cylinders. This layout can't fire evenly; gaps of %2$s: %3$s");
		raw("tool.timing_tool.coupled", "Timed across %1$s coupled crankshafts");
		raw("tool.timing_tool.rotor", "Timed one rotor");
		raw("tool.timing_tool.rotors.even", "Timed %1$s rotors: every firing in the engine is evenly spaced");
		raw("tool.timing_tool.rotors.uneven", "Timed %1$s rotors. The firings can't all be evenly spaced; up to %2$s° off");
		raw("tool.timing_tool.skipped", "%1$s cylinder(s) were left alone: broken, or with no cam lobe");

		raw("goggles.sensor.no_engine", "Not in the intake or exhaust of a running engine's cylinders");
		raw("goggles.sensor.signal", "Redstone output: %1$s");

		raw("goggles.maf", "MAF Sensor");
		raw("goggles.maf.charge", "Air charge: %1$s%% of a full breath");
		raw("goggles.maf.cutting", "Cutting each shot by %1$s%% to match the air");
		raw("goggles.maf.adding", "Adding %1$s%% to each shot to match the air");
		raw("goggles.maf.within", "Every shot already matches the air");
		raw("goggles.maf.target", "Holding the mixture to: %1$s%% of balanced");
		raw("goggles.maf.hint",
			"Sets each injector's shot from the air its cylinder drew in, trimming a shot that is too big and topping up one that is too small, up to the most the injector can give. Fuel Metering no longer applies. The redstone output is the size of the breath, 15 for one half as large again as the bore");

		raw("goggles.heat_exchanger", "Exhaust Heat Exchanger");
		raw("goggles.heat_exchanger.heat", "Exhaust heat: %1$s per second");
		raw("goggles.heat_exchanger.cold", "Cold: not enough heat to warm a basin");
		raw("goggles.heat_exchanger.warm", "Warm: too little heat to cook anything yet");
		raw("goggles.heat_exchanger.heated", "Heating a basin on top of it");
		raw("goggles.heat_exchanger.superheated", "Superheating a basin on top of it");
		raw("goggles.heat_exchanger.disabled", "Heat recovery is switched off in this server's config");
		raw("goggles.heat_exchanger.hint",
			"Set a basin on top of it. About two cylinders at speed heat the basin for heated recipes, about seven superheat it, which doubles the Ethanol a batch of Sugar Mash distils and makes recycled Engine Oil go further. Exchangers on the same exhaust share its heat. The redstone output is how hot it runs, 15 at twice the heat superheating takes");

		raw("goggles.dyno", "Dynamometer");
		raw("goggles.dyno.no_engine", "Not against an engine: set it against a crankshaft, a cylinder or a head");
		raw("goggles.dyno.measuring", "Still measuring the engine's curve");
		raw("goggles.dyno.torque", "Torque  %1$s");
		raw("goggles.dyno.power", "Power   %1$s");
		raw("goggles.dyno.range", "%1$s RPM across to %2$s RPM");
		raw("goggles.dyno.peak_torque", "Peak torque: %1$s SU per RPM at %2$s RPM");
		raw("goggles.dyno.peak_power", "Peak power: %1$s SU at %2$s RPM");
		raw("goggles.dyno.powerband", "Powerband: %1$s to %2$s RPM, within a tenth of peak power");
		raw("goggles.dyno.now", "Engine now: %1$s RPM");
		raw("goggles.dyno.rated", "Rated now: %1$s SU per RPM, %2$s SU at this speed");
		raw("goggles.recorder", "Flight Recorder");
		raw("goggles.recorder.no_engine", "Not against an engine: set it against a crankshaft, a cylinder or a head");
		raw("goggles.recorder.empty", "Nothing on the tape yet");
		raw("goggles.recorder.paused", "Not against an engine now: the tape is paused");
		raw("goggles.recorder.span", "The last %1$s, oldest first");
		raw("goggles.recorder.speed", "Speed   %1$s  up to %2$s RPM");
		raw("goggles.recorder.load", "Load    %1$s  up to %2$s%% of what it could give");
		raw("goggles.recorder.warmth", "Warmth  %1$s  now %2$s%% of a warm engine");
		raw("goggles.recorder.states", "State   %1$s");
		raw("goggles.recorder.event", "%1$s ago: %2$s to %3$s");
		raw("goggles.recorder.event_cause", "%1$s ago: %2$s to %3$s, %4$s");
		raw("goggles.recorder.hint", "Dots are stalled, light shading cranking, medium coasting, solid running and dark overloaded. Warmth is against the 0.7 a warm engine settles at, so a long way under 100 is a cold engine");
		raw("goggles.recorder.state.stalled", "stalled");
		raw("goggles.recorder.state.cranking", "cranking");
		raw("goggles.recorder.state.coasting", "coasting");
		raw("goggles.recorder.state.running", "running");
		raw("goggles.recorder.state.overloaded", "overloaded");
		raw("goggles.recorder.state.incomplete", "not fully built");
		raw("goggles.recorder.state.broken", "broken up");
		raw("goggles.recorder.cause.unknown", "and the figures do not say why");
		raw("goggles.recorder.cause.no_fuel", "no fuel was reaching it");
		raw("goggles.recorder.cause.overload", "the load was more than it could pull");
		raw("goggles.recorder.cause.dry", "it had no oil");
		raw("goggles.dyno.hint", "This is what the engine can be rated for at each speed: after its fuel, mixture, boost and how roughly it runs. Redstone rises as its speed climbs to the speed of its peak power");
		raw("goggles.turbo", "Turbocharger");
		raw("goggles.turbo.no_engine", "Not on an engine's intake or exhaust");
		raw("goggles.turbo.boost", "Boost: +%1$s bar");
		raw("goggles.turbo.charge", "Intake boost, all stages: +%1$s bar");
		raw("goggles.turbo.spool", "Wheel speed: %1$s%%");
		raw("goggles.turbo.wastegate", "Wastegate venting %1$s%% of the exhaust");
		raw("goggles.turbo.dumping", "Redstone is dumping the boost (wastegate %1$s%%)");
		raw("goggles.turbo.no_compressor", "Compressor is not on an engine's intake: nothing is boosted");
		raw("goggles.turbo.no_turbine", "Turbine is not on an engine's exhaust: nothing drives it");
		raw("goggles.turbo.back_pressure", "Exhaust back-pressure: +%1$s bar");
		raw("goggles.turbo.limit", "Boost limit: %1$s bar");
		raw("goggles.turbo.vanes", "Variable vanes: %1$s%% closed");
		raw("goggles.turbo.anti_lag", "Anti-lag: burning fuel in the exhaust to hold the boost");
		raw("goggles.turbo.anti_lag.armed", "Anti-lag level %1$s: burns fuel in the exhaust when the engine is off the throttle");
		raw("goggles.turbo.hint",
			"Driven by the heat of the exhaust, so it lags and fades with the fuel. Boost packs in more air, which needs more fuel metering to use, and heats the charge. Redstone opens the wastegate fully. A variable-geometry turbo closes its vanes when the exhaust is thin, so it spools sooner and chokes the exhaust less at full flow. Anti-lag burns fuel ahead of the turbine when the engine is off the throttle, so the boost is waiting when it opens again. Turbos in series on one intake run compound, their pressures multiplying, while turbos side by side (the air can go round either) share the flow and average their pressures, with smaller wheels that spool sooner");

		raw("goggles.supercharger", "Supercharger");
		raw("goggles.supercharger.no_engine", "Not on an engine's intake: nothing is boosted");
		raw("goggles.supercharger.boost", "Boost: +%1$s bar");
		raw("goggles.supercharger.speed", "Shaft speed: %1$s RPM");
		raw("goggles.supercharger.bypass", "Bypass valve bleeding %1$s%% of the boost");
		raw("goggles.supercharger.dumping", "Redstone is dumping the boost (bypass %1$s%%)");
		raw("goggles.supercharger.fed", "Feeding %1$s cylinders");
		raw("goggles.supercharger.limit", "Boost limit: %1$s bar");
		raw("goggles.supercharger.hint",
			"Boost goes with the square of the shaft speed, at once and with no need for exhaust: 0.9 bar at 128 RPM. The work of turning it is stress on the shaft, so gear it for the boost you want. It heats the air more than a turbo does, so an Intercooler helps. Redstone opens the bypass fully");

		raw("goggles.engine", "Engine");
		raw("goggles.state.stalled", "Stalled");
		raw("goggles.state.cranking", "Cranking");
		raw("goggles.state.coasting", "Coasting: turning, but nothing is firing");
		raw("goggles.state.running", "Running");
		raw("goggles.lubrication", "Lubrication: %1$s%%");
		raw("goggles.lubrication.dry", "Dry parts drag: pipe Engine Oil into a cylinder head");
		raw("goggles.lubrication.thinned", "Oil thinned by unburned fuel: %1$s%%");
		raw("goggles.lubrication.thinned.hint", "Fuel the air can't burn washes into the oil, however much is supplied. It drags the engine until the mixture burns clean");
		raw("goggles.state.overloaded", "Overloaded: load is more than its rated torque");
		raw("goggles.state.incomplete", "Not fully loaded");
		raw("goggles.state.broken", "Broken: a plain Shaft splits the crankshaft");
		raw("goggles.broken.hint", "A crankshaft has to be one unbroken line of Crankshaft and Crank Throw blocks. Replace the Shaft between them with Crankshaft blocks");
		raw("goggles.speed", "Speed:");
		raw("goggles.rpm", "%1$s RPM");
		raw("goggles.available_torque", "Rated torque:");
		raw("goggles.load", "Load:");
		raw("goggles.su_per_rpm", "%1$s SU/RPM");
		raw("goggles.fuel_use", "Fuel use:");
		raw("goggles.mb_per_minute", "%1$s mB/min");
		raw("goggles.ripple", "Speed ripple: %1$s%%");
		raw("goggles.ripple.rough", "Running rough");
		raw("goggles.roughness", "Rough running costs %1$s%% of its rating: a flywheel, more cylinders or an even firing order would smooth it");
		raw("goggles.coupled", "%1$s crankshafts geared together");
		raw("goggles.firing", "Firing cylinders: %1$s of %2$s");
		raw("goggles.firing_intervals", "Firing intervals: %1$s");
		raw("goggles.inertia", "Rotating inertia: %1$s");
		raw("goggles.starter_engaged", "Starter engaged");
		raw("goggles.no_cylinders", "No cylinders attached");
		raw("goggles.part_of_engine", "Part of an engine");

		raw("goggles.cylinder", "Cylinder");
		raw("goggles.cycle.none", "Has not completed a cycle");
		raw("goggles.cycle.fired", "Firing");
		raw("goggles.cycle.no_fuel", "Misfire: no fuel injected");
		raw("goggles.cycle.no_ignition", "Misfire: fuel did not ignite");
		raw("goggles.cycle.no_air", "Misfire: no fresh air in the cylinder");
		raw("goggles.cycle.no_spark", "Misfire: no spark from the plug");
		raw("goggles.cycle.too_lean", "Misfire: mixture too lean for the spark to light");
		raw("goggles.cycle.too_rich", "Misfire: mixture too rich for the spark to light");
		raw("goggles.cycle.fouled", "Misfire: the plug is fouled with soot and can't light a rich mixture");
		raw("goggles.fouling", "Spark plug soot: %1$s%%");
		raw("goggles.fouling.hint", "Caused by running rich. Lean the injector until the mixture burns clean and the plug cleans itself");
		raw("goggles.cycle.rich", "Running rich: unburned fuel");
		raw("goggles.cycle.lean", "Running lean: air to spare");
		raw("goggles.mixture", "Air-fuel mixture: %1$s");
		raw("goggles.mixture.lean", "%1$s× lean");
		raw("goggles.mixture.rich", "%1$s× rich");
		raw("goggles.mixture.balanced", "balanced");
		raw("goggles.mixture.suggest", "Try about %1$s%% fuel metering at this speed, or tune it with a Timing Tool");
		raw("goggles.ignition.spark", "Spark ignition");
		raw("goggles.compression", "Charge temperature: %1$s (fuel ignites at %2$s)");
		raw("goggles.timing", "Valve timing: %1$s");
		raw("goggles.timing.on_time", "on time");
		raw("goggles.timing.early", "%1$s° early");
		raw("goggles.timing.late", "%1$s° late");
		raw("goggles.timing.unlocked", "drifting: cam is not geared 1:2");
		raw("goggles.phase", "Top dead centre at crank angle %1$s°");
		raw("goggles.fault.not_on_throw", "Not mounted on a crank throw");
		raw("goggles.fault.no_piston", "No piston installed");
		raw("goggles.fault.no_rod", "No connecting rod installed");
		raw("goggles.fault.no_head", "No cylinder head");
		raw("goggles.fault.no_injector", "Head has no fuel injector");
		raw("goggles.fault.no_cam", "No cam lobe touching the head or the cylinder");
		raw("goggles.fault.no_intake", "Head has no intake");
		raw("goggles.fault.intake_blocked", "Intake has no open air intake");
		raw("goggles.fault.no_exhaust", "Head has no exhaust");
		raw("goggles.fault.exhaust_blocked", "Exhaust has no open outlet");
		raw("goggles.fault.no_battery", "Spark plug has no battery touching the engine");
		raw("goggles.fault.broken_cam", "A plain Shaft splits the camshaft: replace it with Camshaft blocks");
		raw("goggles.fault.no_rotor", "No rotor installed: right-click with an Engine Rotor");
		raw("goggles.fault.rotary_disabled", "Rotary engines are switched off in this server's config");
		raw("goggles.fault.intake_flooded", "Air intake is underwater: it draws no air, so nothing can burn");
		raw("goggles.fault.exhaust_flooded", "Exhaust outlet is underwater: the gas can't push its way out");
		raw("goggles.fault.submerged", "Underwater: nothing burns in an engine that sits in water, wherever its pipes lead");
		raw("goggles.fault.no_plug", "No spark plug: the chamber is too long and thin for the charge to light itself");

		raw("goggles.rotor", "Rotor Housing");
		raw("goggles.rotor.chamber", "Chamber %1$s: %2$s");

		raw("goggles.cam", "Camshaft");
		raw("goggles.cam_ratio", "Speed ratio to crank: %1$s");
		raw("goggles.cam_ratio.correct", "Geared 1:2 for four-stroke");
		raw("goggles.cam_ratio.wrong", "Must turn at half crank speed");
		raw("goggles.lobe_cylinders", "Actuates %1$s cylinder(s)");
		raw("goggles.lobe_shared", "Times the first at its angle; the others follow their own crank angles");
		raw("goggles.fuel_rack", "Fuel rack: %1$s%%");

		raw("goggles.starter", "Starter");
		raw("goggles.starter.idle", "Idle: needs a redstone signal and input rotation");
		raw("goggles.starter.cranking", "Cranking the engine");
		raw("goggles.starter.overrun", "Engaged, engine outrunning it");
		raw("goggles.starter.no_engine", "Not touching a crankshaft");

		raw("goggles.head", "Cylinder Head");
		raw("goggles.head.no_injector", "Right-click with a Fuel Injector to install");
		raw("goggles.head.oil", "Takes Engine Oil as well as fuel: it lubricates the whole engine. An Oil Sump does the same without using a face");
		raw("goggles.head.metering", "Fuel metering: %1$s%%");
		raw("goggles.head.metering.hint", "Too much fuel runs rich and wastes it, too little runs lean. Breathing falls as the engine speeds up");
		raw("goggles.head.spark_plug", "Spark plug fitted");
		raw("goggles.head.no_spark_plug", "Right-click with a Spark Plug to light fuel by spark instead of compression");

		raw("goggles.sump", "Oil Sump");
		raw("goggles.sump.no_engine", "Not touching an engine's crankshaft or cylinder head");
		raw("goggles.sump.empty", "Empty: fill it with Engine Oil by bucket or pipe");
		raw("goggles.sump.filter", "Oil filter fitted: the oil ages more slowly and fuel is cleaned out of it faster");
		raw("goggles.sump.no_filter", "Right-click with an Oil Filter to fit one");
		raw("goggles.sump.exchange", "Oil exchange: %1$s mB of oil a second while the engine turns");
		raw("goggles.sump.exchange.hint",
			"Spent oil leaves as Used Oil from any face, by pipe or bucket, and fresh oil piped in takes its place. Burn the used oil, refine it, coke it or let it go");
		raw("goggles.sump.used_full", "Used Oil tank full: pipe it away or the exchange stops");
		raw("goggles.muffled", "Exhaust muffled: %1$s%% of it runs through a muffler, which only quiets it");
		raw("tooltip.fuel", "Engine fuel");
		raw("tooltip.fuel.energy", "Energy: %1$s per mB");
		raw("tooltip.fuel.compression", "Lights by compression: no spark plug needed, and it can pilot a dual-fuel engine");
		raw("tooltip.fuel.spark", "Needs a spark plug: too hard to light by compression");
		raw("tooltip.fuel.cold.good", "Starts well in a cold engine");
		raw("tooltip.fuel.cold.poor", "Burns poorly in a cold block: warm the engine first");
		raw("tooltip.fuel.thin", "Thin: the injectors need %1$s times the usual shot");
		raw("value.sump_signal", "Comparator Reads");
		raw("value.sump_signal.0", "Engine Oil");
		raw("value.sump_signal.1", "Used Oil");
		raw("value.sump_signal.2", "Coolant");
		raw("value.sump_signal.3", "Pilot Fuel");
		raw("goggles.sump.signal", "A comparator reads the %1$s tank: 0 when empty, 15 when full");
		raw("display_source.engine_status", "Engine Status");
		raw("display_source.engine_speed", "Engine Speed");
		raw("display_source.engine_load", "Engine Load");
		raw("display_source.engine_temperature", "Engine Temperature");
		raw("display_source.engine_oil", "Engine Oil Condition");
		raw("display_source.sump_levels", "Oil Sump Levels");
		raw("display.engine.none", "No engine");
		raw("display.engine.state.stalled", "Stalled");
		raw("display.engine.state.cranking", "Cranking");
		raw("display.engine.state.coasting", "Coasting");
		raw("display.engine.state.running", "Running");
		raw("display.engine.state.overloaded", "Overloaded");
		raw("display.engine.speed", "%1$s RPM");
		raw("display.engine.load", "Load %1$s%%");
		raw("display.engine.temperature", "Temp %1$s%%");
		raw("display.engine.oil", "Oil %1$s%%");
		raw("display.sump.oil", "Oil %1$s mB");
		raw("display.sump.used", "Used %1$s mB");
		raw("display.sump.coolant", "Coolant %1$s mB");
		raw("display.sump.pilot", "Pilot %1$s mB");
		raw("value.oil_exchange", "Oil Exchange");
		raw("value.oil_exchange.0", "Off");
		raw("value.oil_exchange.1", "Trickle");
		raw("value.oil_exchange.2", "Steady");
		raw("value.oil_exchange.3", "Flush");
		raw("goggles.oil_condition", "Oil condition: %1$s%%");
		raw("goggles.oil_condition.hint",
			"Spent oil drags the engine a little. Set an oil pan's Oil Exchange to change it as the engine turns");
		raw("goggles.oil_filtered", "Oil filter fitted");
		raw("goggles.temperature.cold", "Engine cold: it drags until it has warmed through");
		raw("goggles.temperature.cool", "Engine cool: it breathes better");
		raw("goggles.temperature.warm", "Engine warm");
		raw("goggles.temperature.cooling", "Cooling down: the metal ticks as it gives up its heat");
		raw("goggles.temperature.cooling.hint", "Running a cold engine drags and burns fuel poorly, and pressed oil and ethanol are the worst at it. Gasoline copes best");
		raw("goggles.temperature.preheated", "Warmed by a heat source against the exchanger");
		raw("goggles.health.healthy", "Healthy");
		raw("goggles.health.rough", "Running rough");
		raw("goggles.health.failing", "Failing");
		raw("goggles.health.line", "Engine health: %1$s");
		raw("goggles.cause.none", "Nothing wrong");
		raw("goggles.cause.build", "A cylinder is missing a part or is not on a crank throw: look at its goggles");
		raw("goggles.cause.water", "Part of the engine is underwater, and nothing burns there: lift its intake, exhaust and block clear of the water");
		raw("goggles.cause.no_air", "Air cannot get in: an intake is missing, covered or underwater");
		raw("goggles.cause.no_cam", "A cam lobe is not opening the valves: touch one to the head and keep the camshaft unbroken");
		raw("goggles.cause.no_fuel", "No fuel is reaching a cylinder: check the injector, the fuel supply and the rack");
		raw("goggles.cause.no_spark", "The spark plug is not firing: put a charged battery against the engine");
		raw("goggles.cause.no_plug", "A rotor housing needs a spark plug to light its charge");
		raw("goggles.cause.no_exhaust", "Burned gas cannot get out: an exhaust is missing, covered or underwater");
		raw("goggles.cause.no_ignition", "The charge is not hot enough to light: use a spark plug, a pilot fuel, or more compression");
		raw("goggles.cause.too_lean", "Too little fuel for the air to burn: raise the injector's metering");
		raw("goggles.cause.too_rich", "Too much fuel for the air to burn: lower the metering or let more air in");
		raw("goggles.cause.fouled", "Soot on the spark plug from running rich: lean the mixture out until it burns off");
		raw("goggles.cause.dry_oil", "The bearings are running dry: keep the oil pan full");
		raw("goggles.cause.uneven", "The cylinders fire unevenly, so the speed surges: add cylinders, even out the firing order or fit a flywheel");
		raw("goggles.cause.cold", "The block has not warmed through, so it drags and burns fuel poorly: let it warm up");
		raw("goggles.cylinder.cause", "Why: %1$s");
		raw("goggles.dyno.previous", "Against the last run:");
		raw("goggles.dyno.delta_torque", "Peak torque %1$s%% (%2$s SU per RPM)");
		raw("goggles.dyno.delta_power", "Peak power %1$s%% (%2$s SU)");
		raw("goggles.dyno.delta_peak", "Power peak %1$s RPM");
		raw("goggles.dyno.delta_band", "Powerband %1$s RPM wide");
		raw("goggles.dyno.delta_same", "Same as the last run");
		raw("goggles.temperature.hint",
			"Coolant, or water, piped into an oil pan would cool it, and a cool engine earns a power bonus. A hot engine is never harmed");
		raw("goggles.coolant_use", "Coolant spent: %1$s mB a second");
		raw("goggles.pilot_lit", "A pilot shot is lighting the charge");
		raw("goggles.flex", "Torsional flex: %1$s%% of the rating lost, %2$s throws on one crankshaft");
		raw("goggles.flex.hint",
			"A long crank twists under load. Past %1$s throws on one unbroken crankshaft it costs rating. A vee or a flat puts two cylinders on a throw, and cranks geared together one to one are separate lines");

		raw("goggles.battery", "Battery");
		raw("goggles.battery.charge", "Charge: %1$s%%");
		raw("goggles.battery.flat", "Flat: an alternator has to charge it");

		raw("goggles.alternator", "Alternator");
		raw("goggles.alternator.charging", "Charging batteries from its shaft");
		raw("goggles.alternator.supplying", "Supplying Forge Energy from its shaft");
		raw("goggles.alternator.energy", "Up to %1$s FE a tick");
		raw("goggles.alternator.no_battery", "No battery touching it or its engine to charge");
		raw("goggles.alternator.idle", "Idle: needs a turning shaft and a battery that isn't full");
		raw("goggles.battery.no_engine", "Not touching an engine's crankshaft or cylinder head");
	}

	private static void raw(String key, String value) {
		REGISTRATE.addRawLang(CreateEngines.MOD_ID + "." + key, value);
	}
}
