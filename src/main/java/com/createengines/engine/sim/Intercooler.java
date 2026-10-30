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

	private final List<CylinderModel> charge;

	/** @param charge cylinders that breathe through this intercooler */
	public Intercooler(List<CylinderModel> charge) {
		this.charge = List.copyOf(charge);
	}

	public Intercooler copyOnto(Map<CylinderModel, CylinderModel> copies) {
		List<CylinderModel> mapped = new ArrayList<>();
		for (CylinderModel cylinder : charge) {
			CylinderModel copy = copies.get(cylinder);
			if (copy != null)
				mapped.add(copy);
		}
		return new Intercooler(mapped);
	}

	public List<CylinderModel> charge() {
		return charge;
	}

	/** Cools the air a cylinder is about to draw in, in place. */
	void cool() {
		for (CylinderModel cylinder : charge) {
			cylinder.intakeTemperature = 1 + (cylinder.intakeTemperature - 1) * (1 - EFFECTIVENESS);
			cylinder.intakePressure = 1 + (cylinder.intakePressure - 1) * (1 - PRESSURE_DROP);
		}
	}
}
