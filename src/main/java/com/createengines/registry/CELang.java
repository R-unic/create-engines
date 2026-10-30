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

		raw("goggles.throttle", "Throttle Body");
		raw("goggles.throttle.open", "Open: %1$s%%");
		raw("goggles.throttle.hint",
			"Redstone opens it from its idle stop to wide open. Less air means less power, and its linkage trims the injectors' fuel to match");

		raw("value.maf_target", "Mixture Target");
		raw("value.rev_limit", "Rev Limit");
		raw("value.governed_speed", "Governed Speed");

		raw("goggles.governor", "Governor");
		raw("goggles.governor.no_engine", "Not touching a crankshaft block or a cylinder head");
		raw("goggles.governor.speed", "Crank speed: %1$s RPM");
		raw("goggles.governor.rack", "Fuel rack held at: %1$s%%");
		raw("goggles.governor.open", "Rack wide open: the load is more than the engine can hold at this speed");
		raw("goggles.governor.target", "Holding: %1$s RPM");
		raw("goggles.governor.hint",
			"Fuel (or throttle, in a spark engine) is cut as the engine passes the setting and opened as it falls, over about %1$s%% of the speed. The lowest setting on an engine governs");

		raw("tool.timing_tool.style", "Timing Tool: %1$s");
		raw("tool.timing_tool.style.smooth", "Smooth (any throw angle)");
		raw("tool.timing_tool.style.cross_plane", "Cross-plane (quarter turns)");
		raw("tool.timing_tool.style.flat_plane", "Flat-plane (half turns)");
		raw("tool.timing_tool.style.cams_only", "Cams only (leave the crank)");
		raw("tool.timing_tool.hint", "Use on an engine to set its throws and cam lobes. Sneak-use to change what it may set");
		raw("tool.timing_tool.no_engine", "Not part of an engine");
		raw("tool.timing_tool.no_cylinders", "No working cylinder with a cam lobe to time");
		raw("tool.timing_tool.single", "Timed one cylinder");
		raw("tool.timing_tool.even", "Timed %1$s cylinders, one firing every %2$s°: %3$s");
		raw("tool.timing_tool.uneven", "Timed %1$s cylinders. This layout can't fire evenly; gaps of %2$s: %3$s");
		raw("tool.timing_tool.shared", "%1$s cylinder(s) share a cam lobe with another that fires at a different time. Give each its own lobe, or move the lobe beside the block");
		raw("tool.timing_tool.coupled", "Timed across %1$s coupled crankshafts");
		raw("tool.timing_tool.skipped", "%1$s cylinder(s) were left alone: broken, or with no cam lobe");

		raw("goggles.sensor.no_engine", "Not in the intake or exhaust of a running engine's cylinders");
		raw("goggles.sensor.signal", "Redstone output: %1$s");

		raw("goggles.maf", "MAF Sensor");
		raw("goggles.maf.charge", "Air charge: %1$s%% of a full breath");
		raw("goggles.maf.cutting", "Cutting each shot by %1$s%% to match the air");
		raw("goggles.maf.within", "Every shot is within what the air can burn: no fuel cut");
		raw("goggles.maf.target", "Holding the mixture to: %1$s%% of balanced");
		raw("goggles.maf.hint",
			"Caps each injector's shot at the air its cylinder drew in, so Fuel Metering can be set high for slow running and the sensor trims it back as the engine speeds up. It only cuts, never adds. The redstone output is the size of the breath, 15 for one half as large again as the bore");

		raw("goggles.lambda", "Lambda Sensor");
		raw("goggles.lambda.none", "Nothing has fired on this exhaust to read");
		raw("goggles.lambda.hint",
			"Reads the mixture the exhaust gas says was burned. The redstone output is 8 for balanced, 6 to 9 across the balanced band, lower for lean and higher for rich. It changes nothing itself: feed it to a comparator and the fuel rack on a camshaft for a loop of your own");

		raw("goggles.limiter", "Rev Limiter");
		raw("goggles.limiter.no_engine", "Not touching a crankshaft block or a cylinder head");
		raw("goggles.limiter.speed", "Crank speed: %1$s RPM");
		raw("goggles.limiter.cutting", "Cutting fuel: the engine is at its limit");
		raw("goggles.limiter.limit", "Cuts fuel at: %1$s RPM");
		raw("goggles.limiter.hint",
			"Holds every injector shut once the crank reaches the limit, and lets fuel back in at %1$s%% of it. The redstone output is how close the engine is to the limit, 15 at it");

		raw("goggles.turbo", "Turbocharger");
		raw("goggles.turbo.no_engine", "Not on an engine's intake or exhaust");
		raw("goggles.turbo.boost", "Boost: +%1$s bar");
		raw("goggles.turbo.spool", "Wheel speed: %1$s%%");
		raw("goggles.turbo.wastegate", "Wastegate venting %1$s%% of the exhaust");
		raw("goggles.turbo.dumping", "Redstone is dumping the boost (wastegate %1$s%%)");
		raw("goggles.turbo.no_compressor", "Compressor is not on an engine's intake: nothing is boosted");
		raw("goggles.turbo.no_turbine", "Turbine is not on an engine's exhaust: nothing drives it");
		raw("goggles.turbo.back_pressure", "Exhaust back-pressure: +%1$s bar");
		raw("goggles.turbo.limit", "Boost limit: %1$s bar");
		raw("goggles.turbo.hint",
			"Driven by the heat of the exhaust, so it lags and fades with the fuel. Boost packs in more air, which needs more fuel metering to use, and heats the charge. Redstone opens the wastegate fully");

		raw("goggles.transmission", "Transmission");
		raw("goggles.transmission.neutral", "Neutral: no drive reaches the output");
		raw("goggles.transmission.gear", "Gear %1$s: %2$s turns of the input to one of the output");
		raw("goggles.transmission.reverse", "Reverse: %1$s turns of the input to one of the output, backwards");
		raw("goggles.transmission.hint",
			"The panel sets the gear. A redstone signal picks one instead: 1 neutral, 2 to 5 first to fourth, 6 reverse. A low gear lets the engine carry a heavier load at a slower output");

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
		raw("goggles.speed", "Speed:");
		raw("goggles.rpm", "%1$s RPM");
		raw("goggles.available_torque", "Rated torque:");
		raw("goggles.load", "Load:");
		raw("goggles.su_per_rpm", "%1$s SU/RPM");
		raw("goggles.fuel_use", "Fuel use:");
		raw("goggles.mb_per_minute", "%1$s mB/min");
		raw("goggles.ripple", "Speed ripple: %1$s%%");
		raw("goggles.ripple.rough", "Running rough");
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
		raw("goggles.cycle.fuel_cut", "Fuel cut by the rev limiter");
		raw("goggles.fouling", "Spark plug soot: %1$s%%");
		raw("goggles.fouling.hint", "Caused by running rich. Lean the injector until the mixture burns clean and the plug cleans itself");
		raw("goggles.cycle.rich", "Running rich: unburned fuel");
		raw("goggles.cycle.lean", "Running lean: air to spare");
		raw("goggles.mixture", "Air-fuel mixture: %1$s");
		raw("goggles.mixture.lean", "%1$s× lean");
		raw("goggles.mixture.rich", "%1$s× rich");
		raw("goggles.mixture.balanced", "balanced");
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

		raw("goggles.cam", "Camshaft");
		raw("goggles.cam_ratio", "Speed ratio to crank: %1$s");
		raw("goggles.cam_ratio.correct", "Geared 1:2 for four-stroke");
		raw("goggles.cam_ratio.wrong", "Must turn at half crank speed");
		raw("goggles.lobe_cylinders", "Actuates %1$s cylinder(s)");
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

		raw("goggles.battery", "Battery");
		raw("goggles.battery.charge", "Charge: %1$s%%");
		raw("goggles.battery.flat", "Flat: an alternator has to charge it");

		raw("goggles.alternator", "Alternator");
		raw("goggles.alternator.charging", "Charging batteries from its shaft");
		raw("goggles.alternator.no_battery", "No battery touching it or its engine to charge");
		raw("goggles.alternator.idle", "Idle: needs a turning shaft and a battery that isn't full");
		raw("goggles.battery.no_engine", "Not touching an engine's crankshaft or cylinder head");
	}

	private static void raw(String key, String value) {
		REGISTRATE.addRawLang(CreateEngines.MOD_ID + "." + key, value);
	}
}
