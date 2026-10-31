package com.createengines.engine;

import java.util.EnumSet;
import java.util.Set;

import com.createengines.advancement.Milestone;
import com.createengines.engine.topology.PrecisionParts;

/**
 * Works out which {@link Milestone}s an engine has reached from a snapshot of how it is doing. Kept free of the
 * world so the thresholds can be tested; {@code EngineController} reads the snapshot and reports what comes out.
 */
public final class EngineMilestones {

	public static final float HIGH_REVS = 200;
	public static final float REDLINE = 240;
	/** What one Create steam engine is rated for, SU: 1024 at its 16 RPM. */
	public static final float STEAM_ENGINE = 16384;
	/** Stress capacity, SU, an engine has to be rated for to count as heavy duty: as much as a steam engine's. */
	public static final float HEAVY_DUTY = STEAM_ENGINE;
	/** Stress capacity, SU, that leaves steam behind: four steam engines' worth from one crankshaft. */
	public static final float STEAM_BEATER = 4 * STEAM_ENGINE;
	public static final float WELL_OILED = 0.9f;
	public static final float DRY = 0.1f;
	public static final float BOOST = 0.3f;
	public static final float BIG_BOOST = 1f;
	/** Plug soot at which a spark plug counts as fouled. */
	public static final float FOULED = 0.5f;
	/** Oil strength fuel has to have taken for the oil to count as thinned. */
	public static final float THINNED = 0.3f;
	/** Speed an engine has to run at for a balanced mixture to mean something: one shot is only right at one speed. */
	public static final float DIALED_IN_RPM = 100;
	/** Firing cylinders it takes for an even firing order to mean something. */
	public static final int EVEN_FIRING_CYLINDERS = 4;
	/** Share of the average shot a MAF sensor has to be holding back for it to count as doing something. */
	public static final float MAF_TRIMMING = 0.2f;

	private EngineMilestones() {
	}

	/**
	 * @param state          what the engine is doing now
	 * @param previous       what it was doing at the last check
	 * @param capacity       the stress capacity it is rated for at its current speed, SU
	 * @param sparkFiring    a cylinder with a spark plug fired last cycle
	 * @param rich           a firing cylinder wasted fuel as smoke
	 * @param boost          the strongest turbo boost on the engine, atmospheres
	 * @param dumping        a turbo's wastegate is being forced open
	 * @param fouling        the most soot on any spark plug, 0 to 1
	 * @param balanced       every firing cylinder burned a balanced mixture cleanly
	 * @param dilution       share of the oil's strength unburned fuel has taken
	 * @param pushrod        a firing cylinder is timed by a cam beside its block rather than its head
	 * @param evenFiring     every cylinder is firing, at least four, evenly spaced around the cycle
	 * @param mafTrimming    a MAF sensor is holding the shots of the firing cylinders on its network back
	 * @param lambdaBalanced a lambda sensor is in the exhaust of cylinders that are all burning a balanced mixture
	 * @param limiting       a rev limiter is holding the injectors shut
	 * @param governed       a governor has held the engine on its setting, under load, for a good while
	 * @param precisionEngine every cylinder of an engine of four or more is built entirely from precision parts
	 * @param coupled        the engine has more than one crankshaft, geared together
	 */
	public record Snapshot(EngineState state, EngineState previous, float rpm, boolean starterEngaged, int firing,
						   float capacity, boolean sparkFiring, float lubrication, boolean rich, float boost,
						   boolean dumping, float fouling, boolean balanced, float dilution,
						   boolean pushrod, boolean evenFiring, boolean mafTrimming, boolean lambdaBalanced,
						   boolean limiting, boolean governed, boolean precisionEngine,
						   boolean coupled) {

		public Snapshot(EngineState state, EngineState previous, float rpm, boolean starterEngaged, int firing,
						float capacity, boolean sparkFiring, float lubrication, boolean rich, float boost,
						boolean dumping, float fouling, boolean balanced, float dilution, boolean pushrod,
						boolean evenFiring, boolean mafTrimming, boolean lambdaBalanced, boolean limiting) {
			this(state, previous, rpm, starterEngaged, firing, capacity, sparkFiring, lubrication, rich, boost, dumping,
				fouling, balanced, dilution, pushrod, evenFiring, mafTrimming, lambdaBalanced, limiting, false, false, false);
		}

		public Snapshot(EngineState state, EngineState previous, float rpm, boolean starterEngaged, int firing,
						float capacity, boolean sparkFiring, float lubrication, boolean rich, float boost,
						boolean dumping, float fouling, boolean balanced, float dilution, boolean pushrod,
						boolean evenFiring) {
			this(state, previous, rpm, starterEngaged, firing, capacity, sparkFiring, lubrication, rich, boost, dumping,
				fouling, balanced, dilution, pushrod, evenFiring, false, false, false, false, false, false);
		}

		public Snapshot(EngineState state, EngineState previous, float rpm, boolean starterEngaged, int firing,
						float capacity, boolean sparkFiring, float lubrication, boolean rich, float boost,
						boolean dumping, float fouling, boolean balanced, float dilution) {
			this(state, previous, rpm, starterEngaged, firing, capacity, sparkFiring, lubrication, rich, boost, dumping,
				fouling, balanced, dilution, false, false, false, false, false, false, false, false);
		}

		public Snapshot(EngineState state, EngineState previous, float rpm, boolean starterEngaged, int firing,
						float capacity, boolean sparkFiring, float lubrication, boolean rich, float boost,
						boolean dumping) {
			this(state, previous, rpm, starterEngaged, firing, capacity, sparkFiring, lubrication, rich, boost, dumping,
				0, false, 0, false, false, false, false, false, false, false, false);
		}
	}

	/**
	 * Whether {@code firingAngles}, the crank angles within the 720° cycle at which cylinders fire, are spread evenly:
	 * every gap the same to within a degree. Two cylinders can always be spread evenly, so it takes at least four.
	 */
	public static boolean evenFiring(float[] firingAngles) {
		int n = firingAngles.length;
		if (n < EVEN_FIRING_CYLINDERS)
			return false;
		float[] sorted = firingAngles.clone();
		java.util.Arrays.sort(sorted);
		for (int i = 0; i < n; i++) {
			float gap = sorted[(i + 1) % n] - sorted[i];
			if (gap <= 0)
				gap += 720;
			if (Math.abs(gap - 720f / n) > 1)
				return false;
		}
		return true;
	}

	/** Rotors that have to be firing together for an engine to count as having several. */
	public static final int TWIN_ROTORS = 2;

	/**
	 * What an engine's Wankel rotors are doing.
	 *
	 * @param running       the engine is running on its own power
	 * @param rpm           its speed
	 * @param rotorsFiring  rotor housings with a chamber that lit last cycle
	 */
	public record Rotary(boolean running, float rpm, int rotorsFiring) {
	}

	public static Set<Milestone> reachedRotary(Rotary r) {
		Set<Milestone> reached = EnumSet.noneOf(Milestone.class);
		if (!r.running() || r.rotorsFiring() < 1)
			return reached;
		reached.add(Milestone.ROTARY_ENGINE);
		if (r.rpm() >= HIGH_REVS)
			reached.add(Milestone.ROTARY_REVS);
		if (r.rotorsFiring() >= TWIN_ROTORS)
			reached.add(Milestone.TWIN_ROTORS);
		return reached;
	}

	/** Speed a flywheel has to be carrying an engine through its strokes at, to count. */
	public static final float FLYWHEEL_RPM = 30;
	/** Share of its rating an engine may lose to rough running and still count as smooth. */
	public static final float SMOOTH_LOSS = 0.02f;
	/** How far a variable turbo's vanes have to be closed, making boost, to count as catching a thin exhaust. */
	public static final float VANES_CLOSING = 0.2f;

	/**
	 * What an engine's mass and turbo mechanics are doing.
	 *
	 * @param running         the engine is running on its own power
	 * @param rpm             its speed
	 * @param flywheelInertia inertia of the flywheels on its crankshaft's line
	 * @param roughness       share of its rating that rough running takes off
	 * @param vaneBoost       a variable-geometry turbo is making boost with its vanes partly closed
	 * @param antiLagBoost    anti-lag is burning fuel in an exhaust and a turbo is making boost
	 * @param firing          cylinders firing
	 * @param vee             two firing cylinders share a throw a quarter turn apart
	 * @param flat            two firing cylinders share a throw on opposite sides
	 * @param dynoRead        a dynamometer block is showing this engine's finished torque curve
	 */
	public record Mechanics(boolean running, float rpm, double flywheelInertia, float roughness, boolean vaneBoost,
							boolean antiLagBoost, int firing, boolean vee, boolean flat, boolean dynoRead) {
	}

	public static Set<Milestone> reachedMechanics(Mechanics m) {
		Set<Milestone> reached = EnumSet.noneOf(Milestone.class);
		if (m.running() && m.flywheelInertia() > 0 && m.rpm() >= FLYWHEEL_RPM && m.roughness() <= SMOOTH_LOSS)
			reached.add(Milestone.FLYWHEEL_SMOOTH);
		if (m.running() && m.firing() >= EVEN_FIRING_CYLINDERS && m.vee())
			reached.add(Milestone.VEE_ENGINE);
		if (m.running() && m.firing() >= EVEN_FIRING_CYLINDERS && m.flat())
			reached.add(Milestone.FLAT_ENGINE);
		if (m.running() && m.dynoRead())
			reached.add(Milestone.DYNO_RUN);
		if (m.vaneBoost())
			reached.add(Milestone.VARIABLE_VANES);
		// Off the throttle the engine is not running on its own power, so this is not tied to it
		if (m.antiLagBoost())
			reached.add(Milestone.ANTI_LAG);
		return reached;
	}

	/** Boost one stage has to make to count towards stacking boost. */
	public static final float STAGE_BOOST = 0.2f;

	/**
	 * What an engine's forced induction is doing.
	 *
	 * @param running      the engine is running on its own power
	 * @param blowerBoost  the strongest supercharger boost on a firing cylinder, atmospheres
	 * @param turboStages  most turbochargers in series making at least {@link #STAGE_BOOST} on one cylinder
	 * @param blowerStages most superchargers making at least {@link #STAGE_BOOST} on one cylinder
	 * @param twinStages   most turbochargers side by side making at least {@link #STAGE_BOOST} on one cylinder
	 * @param cooledBoost  the strongest boost, atmospheres, on a firing cylinder that breathes through an intercooler
	 */
	public record Induction(boolean running, float blowerBoost, int turboStages, int blowerStages, int twinStages,
							float cooledBoost) {
	}

	public static Set<Milestone> reachedInduction(Induction i) {
		Set<Milestone> reached = EnumSet.noneOf(Milestone.class);
		if (!i.running())
			return reached;
		if (i.blowerBoost() >= BOOST)
			reached.add(Milestone.SUPERCHARGED);
		if (i.turboStages() >= 2)
			reached.add(Milestone.COMPOUND_BOOST);
		if (i.twinStages() >= 2)
			reached.add(Milestone.TWIN_TURBO);
		if (i.turboStages() >= 1 && i.blowerStages() >= 1)
			reached.add(Milestone.TWINCHARGED);
		if (i.cooledBoost() >= BOOST)
			reached.add(Milestone.COOLED_CHARGE);
		return reached;
	}

	/** Lubrication an engine needs for oil to count as doing its job. */
	public static final float OILED = 0.5f;
	/** Throttle opening below which a throttle body counts as throttling the intake. */
	public static final float THROTTLING = 0.95f;

	/**
	 * Which parts a running engine is putting to work. Each is about the part doing its job, not being fitted: an
	 * advancement for a part goes to whoever runs an engine that uses it.
	 *
	 * @param running        the engine is running on its own power
	 * @param camTimed       a firing cylinder has its valves opened by a camshaft
	 * @param piped          a firing cylinder breathes in and out through piped intake and exhaust
	 * @param throttled      a firing cylinder's intake is held partly closed by a throttle body
	 * @param diesel         the engine is burning diesel
	 * @param gasoline       the engine is burning gasoline
	 * @param lubrication    how well oiled the engine is, 0 to 1
	 * @param sump           an oil sump is on the engine
	 * @param mafReading     a MAF sensor is reading the air that cylinders draw
	 * @param lambdaReading  a lambda sensor is reading the exhaust of cylinders that fired
	 * @param governing      a governor is pulling the fuel rack back to hold the engine's speed
	 * @param precision      {@link PrecisionParts} bits of the precision parts in firing cylinders
	 * @param rotorHousingTurning a rotor housing is in a crankshaft that is turning, running or not
	 */
	public record Parts(boolean running, boolean camTimed, boolean piped, boolean throttled, boolean diesel,
						boolean gasoline, float lubrication, boolean sump, boolean mafReading, boolean lambdaReading,
						boolean governing, int precision, boolean rotorHousingTurning) {
	}

	public static Set<Milestone> reachedParts(Parts p) {
		Set<Milestone> reached = EnumSet.noneOf(Milestone.class);
		if (p.rotorHousingTurning())
			reached.add(Milestone.ROTOR_HOUSING_TURNED);
		if (!p.running())
			return reached;
		if (p.camTimed())
			reached.add(Milestone.CAMSHAFT_TIMED);
		if (p.piped())
			reached.add(Milestone.PIPED_ENGINE);
		if (p.throttled())
			reached.add(Milestone.THROTTLED);
		if (p.diesel())
			reached.add(Milestone.DIESEL_BURNED);
		if (p.gasoline())
			reached.add(Milestone.GASOLINE_BURNED);
		if (p.lubrication() >= OILED) {
			reached.add(Milestone.OILED);
			if (p.sump())
				reached.add(Milestone.SUMP_OILING);
		}
		if (p.mafReading())
			reached.add(Milestone.MAF_READING);
		if (p.lambdaReading())
			reached.add(Milestone.LAMBDA_READING);
		if (p.governing())
			reached.add(Milestone.GOVERNOR_ACTIVE);
		if (PrecisionParts.has(p.precision(), PrecisionParts.BORE))
			reached.add(Milestone.PRECISION_BORE);
		if (PrecisionParts.has(p.precision(), PrecisionParts.HEAD))
			reached.add(Milestone.PRECISION_HEAD);
		if (PrecisionParts.has(p.precision(), PrecisionParts.PISTON))
			reached.add(Milestone.PRECISION_PISTON);
		if (PrecisionParts.has(p.precision(), PrecisionParts.INJECTOR))
			reached.add(Milestone.PRECISION_INJECTOR);
		return reached;
	}

	public static Set<Milestone> reached(Snapshot s) {
		Set<Milestone> reached = EnumSet.noneOf(Milestone.class);
		if (s.starterEngaged() && s.state() == EngineState.CRANKING)
			reached.add(Milestone.CRANKING);
		boolean wasTurning = s.previous() == EngineState.RUNNING || s.previous() == EngineState.COASTING
			|| s.previous() == EngineState.OVERLOADED;
		if (wasTurning && s.state() == EngineState.STALLED)
			reached.add(Milestone.STALLED);
		if (s.state() == EngineState.OVERLOADED)
			reached.add(Milestone.OVERLOADED);

		if (s.state() == EngineState.RUNNING) {
			reached.add(Milestone.ENGINE_RUNNING);
			if (s.firing() >= 4)
				reached.add(Milestone.FOUR_CYLINDERS);
			if (s.firing() >= 8)
				reached.add(Milestone.EIGHT_CYLINDERS);
			if (s.firing() >= 12)
				reached.add(Milestone.TWELVE_CYLINDERS);
			if (s.firing() >= 16)
				reached.add(Milestone.SIXTEEN_CYLINDERS);
			if (s.coupled() && s.firing() >= EVEN_FIRING_CYLINDERS)
				reached.add(Milestone.COUPLED_CRANKS);
			if (s.rpm() >= HIGH_REVS)
				reached.add(Milestone.HIGH_REVS);
			if (s.rpm() >= REDLINE)
				reached.add(Milestone.REDLINE);
			if (s.capacity() >= HEAVY_DUTY)
				reached.add(Milestone.HEAVY_DUTY);
			if (s.capacity() >= STEAM_BEATER)
				reached.add(Milestone.STEAM_BEATER);
			if (s.governed())
				reached.add(Milestone.GOVERNED);
			if (s.precisionEngine())
				reached.add(Milestone.PRECISION_ENGINE);
			if (s.sparkFiring())
				reached.add(Milestone.SPARK_ENGINE);
			if (s.lubrication() >= WELL_OILED)
				reached.add(Milestone.WELL_OILED);
			if (s.lubrication() < DRY)
				reached.add(Milestone.DRY_RUN);
			if (s.rich())
				reached.add(Milestone.SMOKE_SIGNALS);
			if (s.balanced() && s.rpm() >= DIALED_IN_RPM)
				reached.add(Milestone.DIALED_IN);
			if (s.pushrod())
				reached.add(Milestone.PUSHROD_CAM);
			if (s.evenFiring())
				reached.add(Milestone.EVEN_FIRING);
			if (s.mafTrimming())
				reached.add(Milestone.MAF_TRIM);
			if (s.lambdaBalanced() && s.rpm() >= DIALED_IN_RPM)
				reached.add(Milestone.LAMBDA_LOCK);
		}
		// Fuel cut for a moment leaves nothing firing, so this is not tied to running
		if (s.limiting())
			reached.add(Milestone.REV_LIMITED);

		if (s.fouling() >= FOULED)
			reached.add(Milestone.FOULED_PLUG);
		if (s.dilution() >= THINNED)
			reached.add(Milestone.THINNED_OIL);

		if (s.boost() >= BOOST)
			reached.add(Milestone.BOOST);
		if (s.boost() >= BIG_BOOST)
			reached.add(Milestone.BIG_BOOST);
		if (s.dumping() && s.boost() >= BOOST)
			reached.add(Milestone.BLOW_OFF);
		return reached;
	}
}
