package com.createengines.command;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.createengines.api.ApiVersion;
import com.createengines.config.CEConfig;
import com.createengines.engine.EngineProviders;
import com.createengines.engine.PartEffectLoader;
import com.createengines.engine.sim.FuelCheck;
import com.createengines.engine.sim.FuelSpec;
import com.createengines.engine.sim.PartEffects;
import com.createengines.engine.sim.PartStats;
import com.createengines.registry.CEDataMaps;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * What a pack maker asks of {@code /ce}: what the config says now and where it differs from the defaults, which fluids
 * count as fuel and why, what the data packs did to the cylinder parts, and what an addon has plugged in. Each report is a
 * list of plain lines, so a test reads exactly what a player sees.
 */
public final class PackReport {

	private PackReport() {
	}

	/** The commands, one line each. */
	public static List<String> help() {
		return List.of(
			"Create: Engine Engineer, pack tools (docs/PACKS.md and docs/ADDONS.md in the mod's repository):",
			"/ce config            every server setting, and which differ from the defaults",
			"/ce fuels             every fluid an engine will burn, where its numbers come from, and anything wrong with them",
			"/ce parts             what the data packs did to the cylinder parts, which blocks name a part of their own, and any file that was refused",
			"/ce api               the API version and the addon providers registered",
			"/ce dump              writes every report above, and the mods loaded, to create_engines-dump.txt, for a bug report",
			"/ce preset <name>     set the [balance] and [features] settings to a shipped preset (casual, standard, expert)");
	}

	/** Every server setting with its value, a star on the ones that differ from the default. */
	public static List<String> config() {
		List<String> out = new ArrayList<>();
		List<String> lines = new ArrayList<>();
		int changed = 0;
		for (Map.Entry<String, ModConfigSpec.ConfigValue<?>> entry : CEConfig.values()
			.entrySet()) {
			Object now = entry.getValue()
				.get();
			Object spec = CEConfig.SPEC.getSpec()
				.get(List.of(entry.getKey()
					.split("\\.")));
			Object standard = spec instanceof ModConfigSpec.ValueSpec valueSpec ? valueSpec.getDefault() : null;
			boolean differs = standard != null && !same(now, standard);
			if (differs)
				changed++;
			lines.add((differs ? "* " : "  ") + entry.getKey() + " = " + now + (differs ? "   (default " + standard + ")" : ""));
		}
		out.add(changed == 0 ? "Every server setting is at its default." : changed + " of " + lines.size()
			+ " server settings differ from the defaults (marked *).");
		out.add("File: serverconfig/" + CEConfig.FILE + " in the world; a modpack ships its starting values in defaultconfigs/.");
		out.addAll(lines);
		return out;
	}

	private static boolean same(Object a, Object b) {
		return a instanceof Number x && b instanceof Number y ? x.doubleValue() == y.doubleValue() : a.equals(b);
	}

	/** Every fluid an engine will burn, from its data map entry or its fuel tag, with the figures it runs on. */
	public static List<String> fuels() {
		List<String> out = new ArrayList<>();
		List<Holder.Reference<Fluid>> holders = BuiltInRegistries.FLUID.holders()
			.filter(holder -> holder.value() != Fluids.EMPTY && !isFlowingForm(holder.value()))
			.sorted(java.util.Comparator.comparing(holder -> holder.key()
				.location()
				.toString()))
			.toList();
		int fromData = 0;
		List<String> lines = new ArrayList<>();
		for (Holder.Reference<Fluid> holder : holders) {
			FuelSpec spec = CEDataMaps.fuel(holder.value());
			if (spec == null)
				continue;
			boolean data = holder.getData(CEDataMaps.FUEL) != null;
			if (data)
				fromData++;
			lines.add(holder.key()
				.location() + "   from " + (data ? "its data map entry" : "the tag " + tagName(holder)));
			lines.add("    energy " + n(spec.energyPerMb()) + "/mB, ignites at " + n(spec.autoignitionTemp()) + ", air " + n(spec.airPerMb())
				+ "/mB, cold " + n(spec.coldSensitivity()) + ", charge cooling " + n(spec.chargeCooling()) + ", soot " + n(spec.sootFactor())
				+ ", pre-ignites at " + n(spec.preignitionTemp()) + ", sulfur " + n(spec.sulfur()));
			for (String problem : FuelCheck.problems(spec))
				lines.add("    ! " + problem);
			for (String note : FuelCheck.unusual(spec))
				lines.add("    ? " + note);
		}
		long fuels = lines.stream()
			.filter(line -> !line.startsWith("    "))
			.count();
		out.add(fuels + " fluids are engine fuels (" + fromData + " by a data map entry, the rest by a fuel tag).");
		out.add("A fluid is a fuel with a data_maps/fluid/fuel.json entry, or by being in c:gasoline, c:diesel, c:biodiesel, c:ethanol, c:kerosene, c:methanol or c:plantoil.");
		out.addAll(lines);
		return out;
	}

	/** One line for each fuel a pack wrote whose numbers cannot be right, for the log when the server starts. */
	public static List<String> fuelProblems() {
		List<String> out = new ArrayList<>();
		BuiltInRegistries.FLUID.holders()
			.filter(holder -> holder.value() != Fluids.EMPTY && !isFlowingForm(holder.value()) && holder.getData(CEDataMaps.FUEL) != null)
			.forEach(holder -> {
				for (String problem : FuelCheck.problems(CEDataMaps.fuel(holder.value())))
					out.add(holder.key()
						.location() + " (data_maps/fluid/fuel.json): " + problem);
			});
		return out;
	}

	private static boolean isFlowingForm(Fluid fluid) {
		return fluid instanceof FlowingFluid flowing && flowing.getSource() != fluid;
	}

	private static String tagName(Holder<Fluid> holder) {
		for (TagKey<Fluid> tag : List.of(CEDataMaps.GASOLINE, CEDataMaps.DIESEL, CEDataMaps.BIODIESEL, CEDataMaps.ETHANOL,
			CEDataMaps.KEROSENE, CEDataMaps.METHANOL, CEDataMaps.PLANT_OIL))
			if (holder.is(tag))
				return "#" + tag.location();
		return "(none)";
	}

	/** What the part effect files and the part stats data map are doing to the cylinders. */
	public static List<String> parts() {
		List<String> out = new ArrayList<>();
		out.add("Part effects (data/create_engines/part_effects, one file per precision part):");
		for (PartEffects.Part part : PartEffects.Part.values()) {
			PartEffects.Effect now = PartEffects.of(part);
			boolean builtIn = now.equals(PartEffects.BUILT_IN.get(part));
			out.add("  " + part.fileName() + ": " + (builtIn ? "built-in values" : "CHANGED by a pack") + "   "
				+ scales(effectScales(now)));
		}
		List<String> loaded = PartEffectLoader.lastReport();
		out.add(loaded.isEmpty() ? "  no part_effects files were read since the server started" : "Files read at the last data reload:");
		for (String line : loaded)
			out.add("  " + line);
		out.add("Cam profiles (data/create_engines/cam_profiles, when each valve opens and shuts, in cycle degrees):");
		for (com.createengines.engine.sim.CamProfiles.Kind kind : com.createengines.engine.sim.CamProfiles.Kind.values()) {
			com.createengines.engine.sim.CamProfile now = com.createengines.engine.sim.CamProfiles.of(kind);
			out.add("  " + kind.fileName() + ": " + (now.equals(kind.builtIn()) ? "built-in angles" : "CHANGED by a pack") + "   intake "
				+ now.intakeOpen() + " to " + now.intakeClose() + ", exhaust " + now.exhaustOpen() + " to " + now.exhaustClose());
		}
		for (String line : com.createengines.engine.CamProfileLoader.lastReport())
			out.add("  " + line);
		out.addAll(ownParts());
		out.add("Part stats (data_maps/block/part_stats.json, scales over a whole kind of cylinder):");
		out.add("  create_engines:cylinder: " + stats(CEDataMaps.partStats(false)));
		out.add("  create_engines:precision_cylinder: " + stats(CEDataMaps.partStats(true)));
		out.add("  create_engines:rotor_housing: " + stats(CEDataMaps.rotaryStats()));
		return out;
	}

	/**
	 * The blocks of a pack's or an addon's own that name a part effect (the {@code part_effect} data map), each with the effect it
	 * names and what that effect does. None of this mod's blocks name one.
	 */
	static List<String> ownParts() {
		List<String> out = new ArrayList<>();
		out.add("Parts of a pack's or an addon's own (data_maps/block/part_effect.json, a block that names a part_effects file):");
		List<String> lines = new ArrayList<>();
		BuiltInRegistries.BLOCK.entrySet()
			.stream()
			.sorted(java.util.Comparator.comparing(entry -> entry.getKey()
				.location()
				.toString()))
			.forEach(entry -> {
				String effect = CEDataMaps.partEffect(entry.getValue());
				if (effect == null)
					return;
				PartEffects.Effect now = PartEffects.ofCustom(effect);
				lines.add("  " + entry.getKey()
					.location() + " -> " + effect + "   " + scales(effectScales(now)));
				if (now.equals(PartEffects.Effect.NONE))
					lines.add("    ! no part_effects file by that id was read, or every scale in it is 1, so this block adds nothing");
			});
		if (lines.isEmpty())
			out.add("  none: no block names a part effect");
		out.addAll(lines);
		return out;
	}

	/**
	 * Every report in one list, with the mods that are loaded, for a bug report: what a person helping would otherwise ask for one
	 * command at a time. Read on the server thread (it reads the config and the data packs).
	 */
	public static List<String> dump() {
		List<String> out = new ArrayList<>();
		out.add("Create: Engine Engineer dump");
		out.add("");
		out.add("Mods loaded:");
		net.neoforged.fml.ModList.get()
			.getMods()
			.stream()
			.sorted(java.util.Comparator.comparing(mod -> mod.getModId()))
			.forEach(mod -> out.add("  " + mod.getModId() + " " + mod.getVersion()));
		for (List<String> section : List.of(api(), config(), fuels(), parts())) {
			out.add("");
			out.addAll(section);
		}
		return out;
	}

	private static Map<String, Double> effectScales(PartEffects.Effect effect) {
		Map<String, Double> map = new LinkedHashMap<>();
		map.put("compression_ratio", effect.compression());
		map.put("breathing", effect.breathing());
		map.put("ignition_delay", effect.ignitionDelay());
		map.put("burn_duration", effect.burn());
		map.put("friction_static", effect.frictionStatic());
		map.put("friction_viscous", effect.frictionViscous());
		map.put("inertia", effect.inertia());
		map.put("injector_flow", effect.injectorFlow());
		return map;
	}

	private static String stats(PartStats stats) {
		if (stats.isNone())
			return "no entry";
		Map<String, Double> map = new LinkedHashMap<>();
		map.put("compression_ratio", stats.compressionRatio());
		map.put("intake_flow", stats.intakeFlow());
		map.put("exhaust_flow", stats.exhaustFlow());
		map.put("max_fuel_per_shot", stats.maxFuelPerShot());
		map.put("ignition_delay", stats.ignitionDelay());
		map.put("burn_duration", stats.burnDuration());
		map.put("friction_static", stats.frictionStatic());
		map.put("friction_viscous", stats.frictionViscous());
		map.put("inertia", stats.inertia());
		map.put("displacement", stats.displacement());
		map.put("injector_flow", stats.injectorFlow());
		return "scaled by a pack: " + scales(map);
	}

	/** The scales that are not 1, as {@code key x2.5}, or "no scale" when every one is 1. */
	private static String scales(Map<String, Double> map) {
		List<String> parts = new ArrayList<>();
		for (Map.Entry<String, Double> entry : map.entrySet())
			if (entry.getValue() != 1.0)
				parts.add(entry.getKey() + " x" + n(entry.getValue()));
		return parts.isEmpty() ? "no scale" : String.join(", ", parts);
	}

	/** The API version and the providers addons have registered. */
	public static List<String> api() {
		List<String> out = new ArrayList<>();
		out.add("Create: Engine Engineer API " + ApiVersion.VERSION);
		List<String> providers = EngineProviders.registered();
		out.add(providers.isEmpty() ? "No engine source providers are registered." : providers.size() + " engine source provider(s) registered:");
		for (String provider : providers)
			out.add("  " + provider);
		return out;
	}

	private static String n(double value) {
		return value == Math.rint(value) && Math.abs(value) < 1e9 ? String.valueOf((long) value) : String.valueOf(Math.round(value * 10000) / 10000d);
	}
}
