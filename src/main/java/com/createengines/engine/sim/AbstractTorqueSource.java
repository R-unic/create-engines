package com.createengines.engine.sim;

/**
 * A starting point for a {@link TorqueSource} that is not a cylinder: it answers every question the crank asks with the
 * quiet answer, so a source written on it only says what it does to the crank, {@link #advance} and {@link #availableWork}.
 * <p>
 * What it does for you:
 * <ul>
 *     <li><b>Weight and drag</b> come from the three numbers given to the constructor. Friction is worked out the way a
 *     cylinder's is, so it grows when the oil is thin and the engine's oil and temperature settings apply to it.</li>
 *     <li><b>Fuel, soot and exhaust heat</b> are none. Override {@link #fuelUsedLastStep()} and
 *     {@link #exhaustHeatLastTick()} if the source burns something; a source that burns nothing returns the default
 *     {@code -1} from {@link #unburnedFraction()} so it counts neither for nor against the oil.</li>
 *     <li><b>Whether it counts as running</b> is {@link #fired()}, false until you override it. An engine keeps itself
 *     "sustaining" (and so reports as running, not cranking) only while some source says it fired, so a source that makes work
 *     of its own should return true while it does.</li>
 *     <li><b>What the engine tells the source</b> is kept for you and read through {@link #coldness()},
 *     {@link #direction()}, {@link #airPressure()} and {@link #airTemperature()}.</li>
 * </ul>
 * It is stepped from one thread and needs no locking. Like the rest of this package it is plain Java. Part of the public API
 * (see {@code com.createengines.api.ApiVersion}); a source is free to ignore it and implement {@link TorqueSource} directly.
 * <p>
 * A source that breathes through a port network ({@link Breather}) implements that interface on top of this class; the
 * names used here ({@code airPressure}, {@code airTemperature}) do not collide with it.
 */
public abstract class AbstractTorqueSource implements TorqueSource {

	private final double inertia;
	private final double frictionStatic;
	private final double frictionViscous;
	private double coldness;
	private int direction = 1;
	private double airPressure = 1;
	private double airTemperature = 1;

	/** A source with no weight and no drag. */
	protected AbstractTorqueSource() {
		this(0, 0, 0);
	}

	/**
	 * @param inertia         inertia it adds to the crank, in the simulation's units (a crank journal is 0.15, a flywheel 1.5)
	 * @param frictionStatic  friction torque that does not depend on speed (a crude cylinder's is 0.08)
	 * @param frictionViscous friction torque per rad/s (a crude cylinder's is about 0.008)
	 */
	protected AbstractTorqueSource(double inertia, double frictionStatic, double frictionViscous) {
		this.inertia = inertia;
		this.frictionStatic = frictionStatic;
		this.frictionViscous = frictionViscous;
	}

	@Override
	public double inertia() {
		return inertia;
	}

	@Override
	public double frictionStatic() {
		return frictionStatic;
	}

	@Override
	public double frictionViscous() {
		return frictionViscous;
	}

	@Override
	public double frictionAt(double omega, double film) {
		return frictionStatic * Lubrication.staticFactor(film) + frictionViscous * omega * Lubrication.viscousFactor(film);
	}

	/** None. Override it if the source takes fuel in; the engine sums it into the fuel rate the goggles show. */
	@Override
	public double fuelUsedLastStep() {
		return 0;
	}

	@Override
	public void endTick() {
	}

	/** None. Override it to warm the block and spin any turbocharger on the exhaust. */
	@Override
	public double exhaustHeatLastTick() {
		return 0;
	}

	@Override
	public void setColdness(double coldness) {
		this.coldness = coldness;
	}

	@Override
	public void setDirection(int direction) {
		this.direction = direction;
	}

	/** Negative: the source takes in no fuel, so it neither thins nor cleans the oil. */
	@Override
	public double unburnedFraction() {
		return -1;
	}

	/** False. See the class comment: return true while the source is making work of its own. */
	@Override
	public boolean fired() {
		return false;
	}

	@Override
	public void setIntake(double pressure, double temperature) {
		this.airPressure = pressure;
		this.airTemperature = temperature;
	}

	/** How cold the block is, from 0 (warm) to 1, as last told. */
	protected double coldness() {
		return coldness;
	}

	/** Which way the crank turns, 1 or -1, as last told. */
	protected int direction() {
		return direction;
	}

	/** Pressure of the air the engine as a whole breathes, in atmospheres (1 is none), as last told; 1 until it is. */
	protected double airPressure() {
		return airPressure;
	}

	/** Temperature of that air, ambient = 1, as last told; 1 until it is. */
	protected double airTemperature() {
		return airTemperature;
	}
}
