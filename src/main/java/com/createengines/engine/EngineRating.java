package com.createengines.engine;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.createengines.config.CEConfig;
import com.createengines.engine.sim.Camshaft;

import com.createengines.engine.sim.CylinderModel;
import com.createengines.engine.sim.Dynamometer;
import com.createengines.engine.sim.Roughness;
import com.createengines.engine.sim.Torsion;
import com.createengines.engine.sim.Supercharger;
import com.createengines.engine.sim.Turbocharger;

/** The engine's torque curve, what it is rated at and the stress capacity it publishes. Part of {@link EngineController}. */
final class EngineRating {

	private final EngineController engine;

	EngineRating(EngineController engine) {
		this.engine = engine;
	}

	static final int CAPACITY_INTERVAL = 5;
	/** Ticks of a small, steady difference between the rating and the published capacity before it is published. */
	static final int SETTLE_INTERVAL = 40;
	/** Rated torque is held back a little from the dyno peak, so a load Create accepts never quite stalls the engine. */
	static final double RATING_MARGIN = 0.9;
	/** The tick a dynamometer block last read this engine's curve. */
	int dynoReadTick = -1000;
	int ticksSinceCapacity = CAPACITY_INTERVAL;
	double[] torqueCurve = new double[Dynamometer.SPEEDS.length];
	/** The free-running speed ripple at each speed of {@link #torqueCurve}, which roughness holds the rating back by. */
	double[] rippleCurve = new double[Dynamometer.SPEEDS.length];
	@Nullable List<Object> ratingKey;
	/** The list the next {@link #ratingKey} is built in: the one before it, once it is out of date. */
	@Nullable List<Object> ratingSpare;
	/** The torque curve being measured, a speed a tick, and whether {@link #torqueCurve} has ever been a whole one. */
	Dynamometer.@Nullable Measurement rating;
	boolean rated;

	/**
	 * Re-measures the torque curve when something it depends on has changed: which cylinders have fuel and of what
	 * kind and metering, which have a spark to burn it by, which cams are geared 1:2, and how well oiled the
	 * engine is.
	 */
	void updateRating() {
		// Two lists swap places each time the key changes, so a steady engine builds its key in the same one every second
		List<Object> key = ratingSpare != null ? ratingSpare : new ArrayList<>();
		key.clear();
		key.add((int) Math.round(engine.sim.lubrication()
			.effective() * 5));
		// Spent oil and a cold or cool block change what the engine can deliver, in steps coarse enough not to re-rate it constantly
		key.add((int) Math.round(engine.sim.lubrication()
			.condition() * 8));
		key.add((int) Math.round(engine.sim.thermal()
			.cold() * 4));
		key.add((int) Math.round(engine.sim.thermal()
			.cool() * 4));
		for (CylinderModel model : engine.sim.cylinders()) {
			key.add(model.fuelSupply.fuel());
			key.add(model.pilotSupply.fuel());
			key.add((int) Math.round(model.metering * 20));
			key.add((int) Math.round(model.intakeFactor * 20));
			key.add((int) Math.round(model.fuelTrim * 20));
			key.add((int) Math.round(model.airMatch * 20));
			key.add(model.ignition.installed() && model.ignition.powered());
			key.add(model.assist.installed() && model.assist.powered());
			key.add(model.coil.installed() && model.coil.powered());
			Camshaft camshaft = model.camshaft();
			key.add(camshaft != null && camshaft.isPhaseLocked());
		}
		for (Turbocharger turbo : engine.sim.turbos())
			key.add((int) Math.round(turbo.limit * 20));
		for (Supercharger blower : engine.sim.superchargers()) {
			key.add((int) Math.round(blower.limit * 20));
			key.add((int) Math.round(blower.driveRatio * 4));
			key.add(blower.variableDrive ? 1 : 0);
		}
		key.add(engine.sim.intercoolers()
			.size());
		key.add(engine.sim.direction());
		// The curve runs as fast as the engine is allowed to turn, so a raised limit has to be measured for
		float maxRpm = EngineController.engineLimit();
		key.add(maxRpm);
		if (key.equals(ratingKey)) {
			ratingSpare = key;
			return;
		}
		ratingSpare = ratingKey;
		ratingKey = key;
		rating = new Dynamometer.Measurement(engine.sim, maxRpm);
	}

	/**
	 * Measures one more speed of the torque curve being re-measured. The curve in use stays the old one until the new
	 * one is whole, except for an engine that has none yet, which uses its new one as it fills: it is measured from the
	 * slowest speed up, so the speeds it is turning at are there first.
	 */
	void continueRating() {
		if (rating == null)
			return;
		boolean done = rating.advance();
		if (done || !rated) {
			torqueCurve = rating.curve();
			rippleCurve = rating.ripples();
		}
		if (done) {
			rating = null;
			rated = true;
		}
	}

	/** What a dynamometer block shows; asking for it counts as a dynamometer reading this engine. */
	public EngineController.Curve curve() {
		dynoReadTick = engine.ticks;
		float maxRpm = EngineController.engineLimit();
		double speedScale = EngineController.speedScale();
		int points = Dynamometer.pointCount(maxRpm);
		float[] rpm = new float[points];
		float[] torque = new float[points];
		double scale = CEConfig.STRESS_PER_TORQUE.get() / speedScale * RATING_MARGIN * Torsion.stiffness(engine.flexLoss);
		for (int i = 0; i < points; i++) {
			rpm[i] = (float) (Dynamometer.speed(i) * speedScale);
			// The curve in use may have been measured before the limit was raised, so it can be shorter than points
			if (i < torqueCurve.length && i < rippleCurve.length)
				torque[i] = (float) (Math.max(0, torqueCurve[i]) * Roughness.smoothness(rippleCurve[i]) * scale);
		}
		return new EngineController.Curve(rpm, torque, (float) (engine.sim.meanRpm() * speedScale),
			(float) (ratedTorque() * CEConfig.STRESS_PER_TORQUE.get() / speedScale),
			rated && rating == null);
	}

	double ratedTorque() {
		return engine.sim.isStopped() ? 0
			: Dynamometer.ratedTorque(torqueCurve, rippleCurve, engine.sim.meanRpm()) * RATING_MARGIN * Torsion.stiffness(engine.flexLoss);
	}

	void publishCapacity(double torqueScale) {
		double target = ratedTorque() * torqueScale;
		// A turning engine with no torque of its own (dry, stalled) still carries what its network asks of it, as a flywheel
		// does: offering none while publishing a speed overstresses the line and stops it each time a drive lets go
		if (target == 0 && !engine.sim.isStopped())
			target = engine.primary.networkStress();
		float current = engine.primary.publishedCapacity();
		ticksSinceCapacity++;
		boolean dropped = target == 0 && current != 0;
		boolean significant = Math.abs(target - current) > Math.max(0.5, 0.05 * current);
		// A change too small to be worth a network update is still published once things have been steady a while, so what
		// the network offers never sits a few percent off what the dynamometer rates the engine at
		boolean drifted = Math.abs(target - current) > 0.5 && ticksSinceCapacity >= SETTLE_INTERVAL;
		if (dropped || significant && ticksSinceCapacity >= CAPACITY_INTERVAL || drifted) {
			engine.primary.publishCapacity((float) target);
			ticksSinceCapacity = 0;
		}
	}
}
