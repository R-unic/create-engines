package com.createengines.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Server-side calibration. The simulation's internal units are converted to Create's here. */
public final class CEConfig {

	public static final ModConfigSpec SPEC;
	public static final ModConfigSpec.DoubleValue STRESS_PER_TORQUE;
	public static final ModConfigSpec.DoubleValue JOURNAL_INERTIA;
	public static final ModConfigSpec.DoubleValue THROW_INERTIA;
	public static final ModConfigSpec.DoubleValue FLYWHEEL_INERTIA;
	public static final ModConfigSpec.DoubleValue BEARING_FRICTION;
	public static final ModConfigSpec.IntValue MAX_CRANK_SEGMENTS;
	public static final ModConfigSpec.IntValue MAX_MANIFOLD_BLOCKS;
	public static final ModConfigSpec.IntValue MAX_COUPLED_LINES;
	public static final ModConfigSpec.IntValue MAX_COUPLING_WALK;

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
			.defineInRange("flywheelInertia", 1.0, 0.0, 100.0);
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
		SPEC = builder.build();
	}

	private CEConfig() {
	}
}
