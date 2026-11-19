package com.createengines.engine.sim;

import java.util.List;

/**
 * Anything that puts work on the crank: the one seam between {@link EngineSimulation} and what drives it. A piston
 * cylinder ({@link CylinderModel}) and a rotary chamber are the two this mod ships; a pack or an addon can supply
 * others (a turbine, a two-stroke) and add them with {@link EngineSimulation#addSource}.
 * <p>
 * The simulation owns the crank, the oil, the heat and the energy balance. A source owns its own gas, timing and
 * burning, and answers only the questions the crank has to ask. It is stepped from one thread, so it needs no locking.
 * Nothing here may allocate in {@link #advance}: it runs for every source on every crank-angle step.
 * <p>
 * Pure Java, like the rest of the package. This interface is part of the public API, frozen at API 1.0 (see com.createengines.api.ApiVersion).
 */
public interface TorqueSource {

	/** Inertia this source adds to the crank, in the simulation's units. */
	double inertia();

	/** Speed-independent friction this source adds, at the oil film's effective strength. */
	double frictionStatic();

	/** Friction this source adds per rad/s, at the oil film's effective strength. */
	double frictionViscous();

	/** This source's friction torque at {@code omega} and the given oil film; the sum of the two figures above. */
	double frictionAt(double omega, double film);

	/**
	 * Advances the source by {@code delta} crank degrees taking {@code dt} seconds.
	 *
	 * @param angle       the crank angle in [0, 720) at the start of the step, increasing in the direction of travel
	 * @param rotaryAngle the same turning kept in [0, 1080), the angle a rotary chamber repeats over
	 * @param direction   which way the crank turns, 1 or -1
	 * @return work done on the crank during the step, which is negative while the source compresses
	 */
	double advance(double angle, double rotaryAngle, int direction, double delta, double dt);

	/** Fuel the source took in during the last {@link #advance}, for the engine's fuel rate. Never negative. */
	double fuelUsedLastStep();

	/** Closes a tick's accounting; a source with nothing to close does nothing. */
	void endTick();

	/** Heat this source put into its exhaust during the last whole tick, in the units {@link Thermal} takes. */
	double exhaustHeatLastTick();

	/** Tells the source how cold the block is, from 0 (warm) to 1. */
	void setColdness(double coldness);

	/** Tells the source which way the crank turns, for anything timed against it. */
	void setDirection(int direction);

	/**
	 * The share of fuel the last cycle took in and did not burn, from 0 to 1, or a negative number if it took none (so
	 * it counts neither for nor against the oil). A source that burns no fuel returns a negative number.
	 */
	double unburnedFraction();

	/** Whether the last cycle lit its charge, which is what keeps an engine sustaining itself. */
	boolean fired();

	/**
	 * Work this source would do per crank cycle (720 degrees) at its current speed with every injector fully open, in
	 * the same units as {@link #advance}. It is how the engine reports the torque it could deliver; a source that cannot
	 * say returns what it did last cycle.
	 */
	double availableWork();

	/**
	 * The air this source breathes, once a tick, after the engine's turbochargers and superchargers have set it: pressure
	 * as a multiple of the atmosphere (1 is none, 2 is a full extra atmosphere) and temperature relative to ambient. It is
	 * the average of what the engine's cylinders draw, or, on an engine with none, what its compressors make in series;
	 * a source decides for itself what to do with it. The default ignores it.
	 */
	default void setIntake(double pressure, double temperature) {
	}

	/**
	 * Lines for the goggle readout on the engine's crankshaft, read on the server about once a second. Return a short,
	 * new or cached list: at most {@link StatusLine#LIMIT} lines are shown across all the engine's sources.
	 */
	default List<StatusLine> status() {
		return List.of();
	}
}
