package com.createengines.engine.sim;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A heat exchanger in the intake run, after the compressors. It takes a share of the heat that compressing the air put
 * in, which gives the density back (more air in the same cylinder, and a charge further from pre-igniting), at the cost
 * of a little of the pressure lost pushing the air through it. Several in a row each take their share of what is left.
 * <p>
 * Pure Java, like the rest of this package.
 */
public final class Intercooler {

	/** Share of the compression heat one intercooler removes. */
	public static final double EFFECTIVENESS = 0.6;
	/** Share of the boost one intercooler loses to its restriction. */
	public static final double PRESSURE_DROP = 0.04;

	private final List<Breather> charge;

	/** @param charge cylinders that breathe through this intercooler */
	public Intercooler(List<? extends Breather> charge) {
		this.charge = List.copyOf(charge);
	}

	public Intercooler copyOnto(Map<? extends Breather, ? extends Breather> copies) {
		List<Breather> mapped = new ArrayList<>();
		for (Breather cylinder : charge) {
			Breather copy = copies.get(cylinder);
			if (copy != null)
				mapped.add(copy);
		}
		return new Intercooler(mapped);
	}

	public List<Breather> charge() {
		return charge;
	}

	/** Cools the air a cylinder is about to draw in, in place. */
	void cool() {
		for (int i = 0, n = charge.size(); i < n; i++) {
			Breather cylinder = charge.get(i);
			cylinder.setIntakeTemperature(1 + (cylinder.intakeTemperature() - 1) * (1 - EFFECTIVENESS));
			cylinder.setIntakePressure(1 + (cylinder.intakePressure() - 1) * (1 - PRESSURE_DROP));
		}
	}
}
