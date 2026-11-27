package com.createengines.client;

import static com.createengines.client.ShowcaseRun.CZ;

import java.util.List;
import java.util.Map;

/**
 * The trailer's shot list (see {@link ShowcaseRun}, which films it): what each shot frames, from where, for how long and at
 * what time of day. {@code TrailerCoverageTest} reads the shot names from this file.
 */
final class ShowcaseRunShots {

	/**
	 * One trailer shot. The camera arcs round a {@link ShowcaseRunFraming.Box} (in blocks from the pad's corner), its heading and
	 * height drifting from the first value to the second over {@code seconds}. How far it stands is worked out, not set: just far
	 * enough for the box to take up {@code fill0}, drifting to {@code fill1}, of the frame's safe area (more is a push in, less a
	 * pull back). The box itself travels {@code travelX} blocks along the crank's line. {@code time} is the time of day the shot
	 * cuts to, or {@link #NO_TIME}.
	 */
	record Shot(String name, String scene, ShowcaseRunFraming.Box subject, float yaw0, float yaw1, float pitch0,
		float pitch1, double fill0, double fill1, double travelX, double seconds, int time) {
	}

	/** A shot whose subject changes: from its own box to this one, fast at first and settling by {@code over} of the shot. */
	record Move(ShowcaseRunFraming.Box to, double over) {

		/** How far along the move is, 0 to 1, {@code t} of the way through the shot. */
		double along(double t) {
			double s = Math.max(0, Math.min(1, t / over));
			return 1 - (1 - s) * (1 - s) * (1 - s);
		}
	}

	static final int NO_TIME = -1;
	static final int DAY = 2500;
	/**
	 * The sun goes down across the end of the cut. The exhaust-side shots look away from the low sun and the courtyard walls
	 * shade the engines, so they are shot early (golden hour, then dusk); the finale looks into the light and holds at 12300,
	 * where the glow and the flames show. Without shaders 11800 looks no different from day and 12700 is too dark.
	 */
	private static final int GOLDEN_HOUR = 11000;
	private static final int DUSK = 11700;
	private static final int NIGHTFALL = 12300;

	/** The pull-back from an exhaust mouth: from the mouth of the V8's first bank out to the whole engine. */
	static final Map<String, Move> MOVES = Map.of("exhaust_mouth", new Move(box(0.5, 1, 2.5, 9, 7, 7.5), 0.6));

	/**
	 * Shots through the goggles, which only read a block the player can reach: the camera stands this many blocks from the
	 * middle of the shot's box, which is under the crosshair, instead of where the box would fill the frame.
	 */
	static final Map<String, Double> REACH = Map.of("heat_critical", 4.6);

	private ShowcaseRunShots() {
	}

	private static ShowcaseRunFraming.Box box(double x0, double y0, double z0, double x1, double y1, double z1) {
		return new ShowcaseRunFraming.Box(x0, y0, z0, x1, y1, z1);
	}

	/**
	 * In the order of the cut (tools/trailer/edit.txt), so the light only ever moves on: the overheat and the title, the
	 * engines to be heard, wear, heat, the new W and X builds, then what earlier versions added (cut as a montage), and
	 * after dark the night pull and the finale. Every length is a whole number of bars at the backing track's tempo
	 * (tools/trailer/music.py: 80 beats a minute, a bar of three seconds).
	 */
	static void define(List<Shot> shots, boolean aero) {
		// The subjects, in blocks from each pad's corner
		ShowcaseRunFraming.Box v8 = box(0.5, 1, 2.5, 10, 6.5, 7);
		ShowcaseRunFraming.Box bigV16 = box(0, 1, 2.5, 14, 7, 7);
		opening(shots, v8);
		toBeHeard(shots, v8);
		wornOut(shots);
		hotAndNew(shots);
		montage(shots, v8, bigV16, aero);
		finale(shots);
	}

	private static void opening(List<Shot> shots, ShowcaseRunFraming.Box v8) {
		// Through goggles, looking up at the crank of a critically hot four: the gasket above it lets go half-way
		shots.add(new Shot("heat_critical", "wear", box(1, 3, 5, 2, 4, 6), -34, -28, -14, -13, 1, 1, 0, 6, DAY));
		// The title lies over a high pull-in on the V8 at golden hour of the morning
		shots.add(new Shot("title_sequence", "v8", v8, -130, -112, 13, 10, 0.82, 0.92, 0, 6, NO_TIME));
	}

	private static void toBeHeard(List<Shot> shots, ShowcaseRunFraming.Box v8) {
		// Close on the crank side of a cold inline four, composed for the middle third that 9:16 keeps
		shots.add(new Shot("cold_start", "coldstart", box(2, 2.2, 4.2, 5.2, 6, 5.8), -24, -14, 4, 3, 1.12, 1.32, 0, 6, NO_TIME));
		// The vertical cut opens on this: tight on a barking exhaust mouth, and fast back to the whole engine (see MOVES)
		shots.add(new Shot("exhaust_mouth", "open_v8", box(7, 5, 6, 8, 6, 7), 116, 60, 6, 15, 0.8, 1.04, 0, 6, NO_TIME));
		// One engine, one camera, its exhaust rebuilt between the shots
		ShowcaseRunFraming.Box run = box(1.5, 2, 3.5, 15.5, 6.8, 7);
		shots.add(new Shot("exhaust_stub", "sound", run, -158, -155, 15, 15, 1.1, 1.13, 0, 6, NO_TIME));
		shots.add(new Shot("exhaust_long", "sound", run, -155, -152, 15, 15, 1.13, 1.16, 0, 6, NO_TIME));
		ShowcaseRunFraming.Box bare = box(0.5, 1.5, 2.5, 9, 6.8, 7.5);
		shots.add(new Shot("v8_crossplane", "open_v8", bare, -48, -34, 18, 16, 1.04, 1.12, 0, 6, NO_TIME));
		shots.add(new Shot("v8_flatplane", "open_v8", bare, -34, -20, 16, 14, 1.12, 1.18, 0, 9, NO_TIME));
		ShowcaseRunFraming.Box heard = box(0.5, 2, 3.5, 8.5, 6.8, 7);
		shots.add(new Shot("muffler_off", "sound", heard, -150, -146, 14, 14, 1.06, 1.1, 0, 6, NO_TIME));
		shots.add(new Shot("muffler_on", "sound", heard, -146, -142, 14, 14, 1.1, 1.14, 0, 6, NO_TIME));
		shots.add(new Shot("muffler_bank", "open_v8", bare, 150, 132, 22, 18, 1.02, 1.12, 0, 9, NO_TIME));
		shots.add(new Shot("diesel_knock", "diesel", box(0.5, 2, 3.5, 6.5, 6.8, 7), -34, -22, 12, 10, 0.98, 1.1, 0, 6, NO_TIME));
		// The turbocharger from its own side, clear of the cam's cogwheel, then the blower
		shots.add(new Shot("inline6_turbo", "inline6", box(6.5, 3.5, 4.5, 10.5, 7.2, 7), -168, -142, 11, 14, 0.98, 1.14, 0, 6, NO_TIME));
		shots.add(new Shot("blown_i6_blower", "blown_i6", box(0, 3.4, 1.5, 3.5, 6.4, 5.5), -68, -48, 14, 16, 1, 1.12, 0, 3, NO_TIME));
		// The long dolly away, over open ground, until the engine is small in the frame
		shots.add(new Shot("far_boom", "open_v8", v8, 90, 96, 8, 13, 1.1, 0.14, 0, 9, NO_TIME));
		// First person, standing by the turbo six, the player puts on headphones
		shots.add(new Shot("headphones_on", "inline6", box(0.5, 1, 3.5, 12.5, 7, 7), -40, -40, 11, 11, 1, 1, 0, 6, NO_TIME));
	}

	private static void wornOut(List<Shot> shots) {
		// Close and slow on the crank throws and the rods on them
		shots.add(new Shot("rods_light_heavy", "rods", box(1, 2, 4.3, 5, 5.2, 5.7), -24, -12, 7, 6, 0.74, 0.8, 0, 6, NO_TIME));
		shots.add(new Shot("wear_parts", "wear_parts", box(1, 0.8, 4.6, 7, 2.4, 5.4), -14, 14, 32, 34, 1, 1.12, 0, 6, NO_TIME));
		// From across the room: what a blown gasket looks like from there
		shots.add(new Shot("wear_gasket_blown", "wear", box(1, 2.5, 3.8, 7, 6.8, 6.2), -34, -24, 18, 17, 0.9, 1, 0, 6, NO_TIME));
		shots.add(new Shot("wear_apex", "rotary", box(1, 1.8, 1.2, 5, 4.6, 3.8), -40, -22, 18, 20, 0.96, 1.06, 0, 3, NO_TIME));
		shots.add(new Shot("wear_turbo", "wear", box(5.5, 3.5, 4.5, 9, 7.2, 7), -164, -148, 12, 14, 0.94, 1.06, 0, 3, NO_TIME));
		shots.add(new Shot("gasket_saw", "wear_parts", box(11.2, 0.8, 4.2, 13.6, 2.6, 5.8), -30, -10, 30, 28, 1, 1.12, 0, 6, NO_TIME));
		// The Copper Gasket's sequenced assembly, along its belt
		shots.add(new Shot("gasket_line", "gasket", box(1, 1, 4.5, 10, 4.2, 6), -18, 6, 5, 7, 1.06, 1.16, 0, 9, NO_TIME));
	}

	private static void hotAndNew(List<Shot> shots) {
		// Running hot: the engine steaming, the radiators going on, then a fan at one and a basin on another
		shots.add(new Shot("running_hot", "heat", box(3, 4.8, 4.3, 7, 6.8, 6.2), -34, -24, 24, 26, 0.96, 1.08, 0, 6, NO_TIME));
		shots.add(new Shot("running_cooled", "heat", box(1.5, 2.8, 3.8, 7.5, 6.8, 6.2), -26, -16, 22, 22, 1.06, 1, 0, 6, NO_TIME));
		shots.add(new Shot("radiator_fan_basin", "heat", box(0, 2.6, 2, 5.4, 7.2, 5.5), -42, -20, 20, 18, 0.94, 1.04, 0, 6, NO_TIME));
		// Gameplay: first person with the Bank Builder, a W laid and then an X
		shots.add(new Shot("bank_wx", "grow", box(0, 0.5, 2, 10.5, 7, 8), -52, -52, 14, 14, 1.1, 1.1, 0, 6, NO_TIME));
		shots.add(new Shot("rotary_orbit", "rotary", box(1, 1.8, 1.2, 7, 4.6, 3.8), -62, -18, 16, 24, 1.04, 1.18, 0, 3, NO_TIME));
	}

	/** What earlier versions added, a beat and a half each in the cut. */
	private static void montage(List<Shot> shots, ShowcaseRunFraming.Box v8, ShowcaseRunFraming.Box bigV16, boolean aero) {
		shots.add(new Shot("first_rises", "first", box(1, 1, 1.4, 5, 5, 3.6), -62, -34, 8, 16, 0.94, 1.06, 0, 6, NO_TIME));
		shots.add(new Shot("hero_v8", "v8", v8, -54, -28, 12, 16, 1.02, 1.16, 0, 9, NO_TIME));
		shots.add(new Shot("inline6_pass", "inline6", box(0.5, 1, 3.5, 12.5, 7, 7), -54, -34, 11, 13, 1.08, 1.16, 0, 3, NO_TIME));
		shots.add(new Shot("flat6_track", "flat6", box(0.5, 1, 3, 9.5, 5.2, 8), -18, 6, 14, 16, 0.94, 1.02, 0, 3, NO_TIME));
		// Gameplay: first person, standing still, the Bank Builder in hand: one cylinder grown into a boosted precision V16
		shots.add(new Shot("grow_engine", "grow", box(0, 1, 2.5, 14.5, 7, 7), -22, -22, 14, 14, 1.17, 1.17, 0, 15, NO_TIME));
		// The variable-geometry turbo on its turbine side, then the MAF sensor in the intake
		shots.add(new Shot("tuning_vgt", "tuning", box(5, 4, 4.8, 8.2, 7.2, 7), -164, -140, 13, 15, 0.92, 1.04, 0, 3, NO_TIME));
		shots.add(new Shot("tuning_maf", "tuning", box(5.2, 4.2, 3.6, 7.4, 6.4, 5.4), -34, -14, 24, 28, 0.8, 0.92, 0, 3, NO_TIME));
		// The test bed's instruments
		shots.add(new Shot("instruments_dyno", "instruments_i4", box(-0.5, 2, 3.5, 4.5, 6.2, 6.5), -62, -42, 15, 18, 1, 1.12, 0, 3,
			NO_TIME));
		shots.add(new Shot("instruments_governor", "instruments_i4", box(4.5, 2, 3.8, 9, 5.8, 7), 128, 148, 14, 17, 0.9, 1.02, 0, 3,
			NO_TIME));
		// The electrical chain: the alternator, the joined batteries it fills, then a coil and an assist on every head
		shots.add(new Shot("electric_alternator", "electric", box(-0.5, 2, 4, 3, 7, 6), -72, -52, 11, 13, 0.98, 1.08, 0, 3, NO_TIME));
		shots.add(new Shot("electric_battery", "electric", box(0, 3.5, 4, 3, 7, 6), -62, -42, 10, 14, 0.84, 0.98, 0, 3, NO_TIME));
		shots.add(new Shot("electric_heads", "electric", box(1.5, 4, 3.5, 6.5, 6.8, 7), -46, -26, 11, 10, 0.94, 1.06, 0, 3, NO_TIME));
		// The service bay: the player fits an oil filter and tops up the pan (gameplay), then the line that cleans the used oil and fills cans
		shots.add(new Shot("service_sump", "service", box(-0.5, 1.5, 3, 4, 5, 6), -40, -40, 15, 15, 0.9, 0.9, 0, 3, NO_TIME));
		shots.add(new Shot("service_radiator", "service", box(1.5, 1.5, 3, 5, 5, 6), -20, -20, 18, 18, 0.9, 0.95, 0, 3, NO_TIME));
		shots.add(new Shot("service_oil", "service", box(-4.5, 0, 3, 1.5, 5, 6.5), -28, -10, 15, 17, 1, 1.08, 0, 3, NO_TIME));
		shots.add(new Shot("service_cans", "service", box(-6, 0, 6, 5, 6, 10.5), 118, 138, 12, 13, 1.08, 1.14, 0, 6, NO_TIME));
		// The casing styles: the Maker's Plate and the four styles passed, then the player dyeing the pillars (gameplay)
		shots.add(new Shot("casing_styles", "casing", box(5.5, 1, 4.5, 13.5, 6, 6), -22, -8, 10, 12, 1, 1, 8, 3, NO_TIME));
		shots.add(new Shot("casing_dye", "casing", box(2, 1, 4.5, 18, 6, 6), -10, -10, 9, 9, 1, 1, 0, 6, NO_TIME));
		// The big block: the heavy starter turns a cold V16 over until it catches
		shots.add(new Shot("heavy_starter", "bigblock", bigV16, -62, -44, 8, 11, 1.08, 1.18, 0, 6, NO_TIME));
		// The drivetrain: the dual-mass flywheel on the diesel crank, then the transmission the power leaves through
		shots.add(new Shot("factory_flywheel", "factory", box(8, 1.5, 3.5, 11, 5, 6.8), 40, 64, 10, 14, 0.86, 0.98, 0, 3, NO_TIME));
		shots.add(new Shot("factory_gearbox", "factory", box(-2, 1.5, 4, 2.5, 6.8, 6.5), 22, -4, 14, 17, 0.92, 1.04, 0, 6, NO_TIME));
		// What the power is for: the row of Create machines off a diesel crank
		shots.add(new Shot("factory_line", "factory", box(-6, 0, 4, 3, 5.8, 6), -28, -8, 12, 15, 1.02, 1.1, 0, 6, NO_TIME));
		// The engine on wheels: only with Create Aeronautics and Sable in the run (-PshowcaseSet=trailer on runShowcaseAeronautics)
		if (aero) {
			shots.add(new Shot("rover_departs", "rover", box(-6, 0, 2, 19, 8, 8), -34, -6, 9, 13, 1.16, 1, 0, 9, GOLDEN_HOUR));
			shots.add(new Shot("rover_wheels", "rover", box(12.8, 0, 2, 16.8, 3.6, 4.5), -22, 0, 6, 10, 0.9, 1.04, 0, 3, NO_TIME));
		}
		// Fuels and the refinery as the sun goes down (the exhaust-side shot is early, it faces away from the light)
		shots.add(new Shot("fuel_tanks", "fuels", box(2.5, 1, CZ - 0.5, 12.5, 5.2, CZ + 1.5), -12, 12, 8, 8, 1, 1.08, 0, 6,
			GOLDEN_HOUR));
		shots.add(new Shot("v8_exhaust_dusk", "v8", v8, 136, 158, 10, 12, 1.08, 1.2, 0, 3, GOLDEN_HOUR));
		shots.add(new Shot("refinery_column", "refinery", box(5, 0, 2, 10, 9, 6), -48, -24, 4, 7, 1.08, 1.18, 0, 6, DUSK));
		shots.add(new Shot("refinery_base", "refinery", box(4.5, 0, 1, 8.5, 3.5, 5), -58, -40, 16, 14, 0.9, 1.02, 0, 3, NO_TIME));
		shots.add(new Shot("refinery_tops", "refinery", box(5, 5, 3, 10, 9, 5), -58, -38, 0, 3, 0.9, 1.02, 0, 3, NO_TIME));
		shots.add(new Shot("refinery_flare", "refinery", box(5.5, 5, 2.5, 9.5, 9.5, 5.5), 24, 52, -10, -4, 0.86, 1, 0, 3, NIGHTFALL));
	}

	private static void finale(List<Shot> shots) {
		// After dark: from idle the throttles open and it revs, then off the fuel twice, with the blow-off and the anti-lag
		shots.add(new Shot("night_pull", "night", box(4.5, 3.5, 3, 9, 8, 7), 122, 146, 11, 14, 0.96, 1.12, 0, 12, NIGHTFALL));
		// The finale: from close on the factory, pulling back to the whole row, with the calls to action over it
		shots.add(new Shot("factory_wide", "factory", box(-6, 0, 3, 11, 6, 7), -12, -34, 12, 14, 1.16, 0.98, 0, 9, DUSK));
	}
}
