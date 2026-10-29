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

		raw("goggles.engine", "Engine");
		raw("goggles.state.stalled", "Stalled");
		raw("goggles.state.cranking", "Cranking");
		raw("goggles.state.coasting", "Coasting: turning, but nothing is firing");
		raw("goggles.state.running", "Running");
		raw("goggles.lubrication", "Lubrication: %1$s%%");
		raw("goggles.lubrication.dry", "Dry parts drag: pipe Engine Oil into a cylinder head");
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
		raw("goggles.fault.no_cam", "No cam lobe touching the head");
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
		raw("goggles.head.oil", "Takes Engine Oil as well as fuel: it lubricates the whole engine");
		raw("goggles.head.metering", "Fuel metering: %1$s%%");
		raw("goggles.head.metering.hint", "Too much fuel runs rich and wastes it, too little runs lean. Breathing falls as the engine speeds up");
		raw("goggles.head.spark_plug", "Spark plug fitted");
		raw("goggles.head.no_spark_plug", "Right-click with a Spark Plug to light fuel by spark instead of compression");

		raw("goggles.battery", "Battery");
		raw("goggles.battery.charge", "Charge: %1$s%%");
		raw("goggles.battery.flat", "Flat: spin its shaft to charge it");
		raw("goggles.battery.charging", "Charging from its shaft");
		raw("goggles.battery.no_engine", "Not touching an engine's crankshaft or cylinder head");
	}

	private static void raw(String key, String value) {
		REGISTRATE.addRawLang(CreateEngines.MOD_ID + "." + key, value);
	}
}
