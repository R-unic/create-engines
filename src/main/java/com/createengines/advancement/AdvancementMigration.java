package com.createengines.advancement;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Carries a player's advancement progress from the 1.x IDs onto the 2.0 ones. Plain Java, so a test can hold it to the list
 * of every 1.x ID.
 * <p>
 * Every ID 1.x defined, shown or hidden, has an entry. A row that stayed in the tab maps to its new ID. A capstone maps to its
 * new ID and keeps every milestone it had earned, since a capstone's criteria are milestone names and those did not change. A
 * row that was hidden and only fed a capstone (or was not worth a row at all) is credited to the capstones that wait for its
 * milestone, and otherwise to nothing: the 46 hidden rows are deleted in 2.0, and the milestone they stood for is still fired
 * for scripts. Nothing here grants an advancement the player did not earn, and nothing is announced: the caller puts the
 * result in the player's progress the way a save would.
 */
public final class AdvancementMigration {

	/**
	 * One criterion to grant.
	 *
	 * @param advancement the new advancement, as a path
	 * @param criterion   the criterion to grant; null to grant every criterion the old row had earned (a capstone)
	 */
	public record Credit(String advancement, String criterion) {
	}

	private static final Map<String, List<Credit>> TABLE = new LinkedHashMap<>();

	/** The 1.x capstones, whose earned milestones carry over under the same names. */
	private static final Set<String> CAPSTONES = Set.of("master_builder", "power_plant", "fine_tuner", "full_driveline",
		"closed_loop", "service_record", "chief_engineer");

	static {
		// Rows that stayed in the tab, under their track
		for (String id : List.of("crankshaft", "cylinder", "starter", "alive", "warmed_up", "sump", "stalled", "flooded"))
			kept(id, "first_light/" + id);
		for (String id : List.of("camshaft", "pipework", "four_cylinders", "eight_cylinders", "twelve_cylinders",
			"sixteen_cylinders", "coupled_cranks", "vee_engine", "flat_engine", "valley_cam", "rotary_engine", "bank_builder",
			"engine_casing"))
			kept(id, "builder/" + id);
		for (String id : List.of("diesel", "gasoline", "spark_plug", "ethanol", "ethanol_boost", "dual_fuel",
			"heat_exchanger", "combined_cycle", "smoke_signals", "fouled_plug", "thinned_oil"))
			kept(id, "fuel/" + id);
		for (String id : List.of("heavy_duty", "redline", "steam_beater", "steam_titan", "overloaded", "turbocharger",
			"big_boost", "blow_off", "twin_turbo", "anti_lag", "supercharger", "twincharged", "precision_engine"))
			kept(id, "power/" + id);
		for (String id : List.of("dialed_in", "governed", "engine_display", "stampede", "dyno_run"))
			kept(id, "tuning/" + id);
		for (String id : List.of("transmission", "every_gear", "alternator", "flywheel"))
			kept(id, "driveline/" + id);
		for (String id : List.of("oil_kept_fresh", "cool_running", "preheated"))
			kept(id, "service/" + id);
		kept("engineer", "root");

		// Capstones carry their earned milestones
		capstone("master_builder", "builder/master_builder");
		capstone("power_plant", "power/power_plant");
		capstone("fine_tuner", "tuning/fine_tuner");
		capstone("full_driveline", "driveline/full_driveline");
		capstone("closed_loop", "fuel/closed_loop");
		capstone("service_record", "service/service_record");
		capstone("chief_engineer", "chief_engineer");

		// Every row, shown or hidden, whose milestone a capstone waits for credits that milestone to the capstone and to
		// Chief Engineer, which waits for all of them
		feeds("twelve_cylinders", "twelve_cylinders", "builder/master_builder");
		feeds("stiff_crank", "stiff_crank", "builder/master_builder");
		feeds("coupled_cranks", "coupled_cranks", "builder/master_builder");
		feeds("vee_engine", "vee_engine", "builder/master_builder");
		feeds("flat_engine", "flat_engine", "builder/master_builder");
		feeds("even_firing", "even_firing", "builder/master_builder");
		feeds("valley_cam", "valley_cam", "builder/master_builder");
		feeds("rotary_engine", "rotary_engine", "builder/master_builder");
		feeds("steam_herd", "steam_herd", "power/power_plant");
		feeds("redline", "redline", "power/power_plant");
		feeds("big_boost", "big_boost", "power/power_plant");
		feeds("twincharged", "twincharged", "power/power_plant");
		feeds("precision_engine", "precision_engine", "power/power_plant");
		feeds("anti_lag", "anti_lag", "power/power_plant");
		feeds("variable_drive", "variable_drive", "power/power_plant");
		feeds("dialed_in", "dialed_in", "tuning/fine_tuner");
		feeds("maf_trim", "maf_trim", "tuning/fine_tuner");
		feeds("maf_enrich", "maf_enrich", "tuning/fine_tuner");
		feeds("governed", "governed", "tuning/fine_tuner");
		feeds("tuned_up", "fuel_tuned", "tuning/fine_tuner");
		feeds("tool_timed", "tool_timed", "tuning/fine_tuner");
		feeds("every_gear", "every_gear", "driveline/full_driveline");
		feeds("charged_battery", "charged_battery", "driveline/full_driveline");
		feeds("flywheel", "flywheel_smooth", "driveline/full_driveline");
		feeds("governor_commanded", "governor_commanded", "driveline/full_driveline");
		feeds("gasoline", "gasoline_burned", "fuel/closed_loop");
		feeds("ethanol", "ethanol_burned", "fuel/closed_loop");
		feeds("superheat", "superheat_recovered", "fuel/closed_loop");
		feeds("dual_fuel", "dual_fuel", "fuel/closed_loop");
		feeds("waste_oil", "waste_oil_burned", "fuel/closed_loop");
		feeds("oil_kept_fresh", "oil_kept_fresh", "service/service_record");
		feeds("oil_filter", "oil_filtered", "service/service_record");
		feeds("sump", "sump_oiling", "service/service_record");
		feeds("warmed_up", "warmed_up", "service/service_record");
		feeds("cool_running", "cool_running", "service/service_record");
		feeds("preheated", "preheated", "service/service_record");
		feeds("hard_start", "hard_start", "service/service_record");

		// Hidden rows with no row to move to. They were never in the tab, and what they marked is still fired as a milestone
		for (String id : List.of("cylinder_head", "throttle", "muffler", "sump_metered", "sugar_mash", "glycerin",
			"mains_power", "encased_shaft", "pushrod_cam", "high_revs", "heavy_starter", "maf_sensor", "governor",
			"timing_tool", "bank_cams", "precision_cylinder", "precision_head", "forged_piston", "precision_injector",
			"rotor_housing", "rotary_revs", "twin_rotors", "reverse", "auto_gearbox", "oil_exchange", "used_oil",
			"coolant_used", "compound_boost", "intercooler", "variable_vanes", "stall_recorded", "superheat",
			"charged_battery", "even_firing", "stiff_crank", "maf_trim", "maf_enrich", "governor_commanded", "tuned_up",
			"tool_timed", "waste_oil", "oil_filter", "hard_start", "steam_herd", "variable_drive"))
			TABLE.computeIfAbsent(id, k -> new ArrayList<>());
	}

	private AdvancementMigration() {
	}

	private static void kept(String old, String now) {
		TABLE.computeIfAbsent(old, k -> new ArrayList<>())
			.add(new Credit(now, now));
	}

	private static void capstone(String old, String now) {
		TABLE.computeIfAbsent(old, k -> new ArrayList<>())
			.add(new Credit(now, null));
	}

	private static void feeds(String old, String milestone, String capstone) {
		List<Credit> credits = TABLE.computeIfAbsent(old, k -> new ArrayList<>());
		credits.add(new Credit(capstone, milestone));
		credits.add(new Credit("chief_engineer", milestone));
	}

	/** Every 1.x advancement ID the table knows, for tests. */
	public static Set<String> legacyIds() {
		return TABLE.keySet();
	}

	/** What a 1.x advancement turns into, for tests. An empty list means its row was deleted with nothing to credit. */
	public static List<Credit> creditsFor(String legacyId) {
		List<Credit> credits = TABLE.get(legacyId);
		if (credits == null)
			throw new IllegalArgumentException("No entry for the 1.x advancement " + legacyId);
		return credits;
	}

	/** Whether {@code legacyId} was a 1.x capstone, for tests. */
	public static boolean wasCapstone(String legacyId) {
		return CAPSTONES.contains(legacyId);
	}

	/**
	 * Works out what a player's old progress earns on the new rows.
	 *
	 * @param earned each 1.x advancement (as a path, with no namespace) the player has progress on, with the criteria they
	 *               have earned on it
	 * @return each new advancement (as a path) with the criteria to grant. An ID this version does not know, from another
	 *         pack or a newer version, is ignored.
	 */
	public static Map<String, Set<String>> migrate(Map<String, ? extends Collection<String>> earned) {
		Map<String, Set<String>> grants = new LinkedHashMap<>();
		for (Map.Entry<String, ? extends Collection<String>> row : earned.entrySet()) {
			List<Credit> credits = TABLE.get(row.getKey());
			if (credits == null || row.getValue()
				.isEmpty())
				continue;
			for (Credit credit : credits) {
				Set<String> into = grants.computeIfAbsent(credit.advancement(), k -> new LinkedHashSet<>());
				if (credit.criterion() == null)
					into.addAll(row.getValue());
				else
					into.add(credit.criterion());
			}
		}
		return grants;
	}
}
