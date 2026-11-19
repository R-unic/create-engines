package com.createengines.engine.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Verification track item 8, the end of the data path. The {@code fuel} data map's codec accepts any number, and
 * {@link FuelCheck} only reports a bad one: "a fuel that fails is still used as written". So a pack with an air figure of
 * 0, a negative energy, or a number that is not a number reaches a running engine, and the engine must survive it (finite
 * figures, no exception, no runaway speed) even where it cannot run well. Before 2026-11-19 eleven such values did not
 * (they made some figure NaN); {@link FuelSpec#usable()} now stands between the data and the sim. Each field is set to every awkward value, one at a
 * time and all at once, and run in a diesel and a spark engine, cranked and driven.
 */
class HostileFuelTest {

	private static final double MAX_OMEGA = EngineSimulation.rpmToOmega(256);
	private static final double[] AWKWARD = {0, -1, -1e-9, 1e-12, 1e-300, 1e9, 1e300, Double.MAX_VALUE, Double.POSITIVE_INFINITY,
		Double.NEGATIVE_INFINITY, Double.NaN};

	@AfterEach
	void resetTuning() {
		Tuning.reset();
	}

	private static EngineSimulation engine(int cylinderCount, FuelSpec fuel, boolean spark) {
		List<CylinderModel> cylinders = new ArrayList<>();
		Camshaft cam = new Camshaft();
		for (int i = 0; i < cylinderCount; i++) {
			double tdc = i * 720.0 / cylinderCount;
			CylinderModel cylinder = new CylinderModel(CylinderSpec.CRUDE, CamProfile.BASIC, tdc, cam, tdc / 2 % 180);
			cylinder.fuelSupply = new FuelSupply() {
				@Override
				public double draw(double mb) {
					return mb;
				}

				@Override
				public FuelSpec fuel() {
					return fuel;
				}
			};
			if (spark)
				cylinder.ignition = IgnitionSource.UNLIMITED;
			cylinders.add(cylinder);
		}
		EngineSimulation engine = new EngineSimulation(cylinders, 2, 0.02);
		engine.setThermalLive(true);
		return engine;
	}

	private static String run(FuelSpec fuel, int cylinders, boolean spark) {
		EngineSimulation engine = engine(cylinders, fuel, spark);
		try {
			for (int i = 0; i < 100; i++) {
				engine.tickCranked(0.5, MAX_OMEGA, EngineSimulation.rpmToOmega(i < 50 ? 32 : 0), 1);
				String bad = unsound(engine, i);
				if (bad != null)
					return bad;
			}
			for (int i = 0; i < 30; i++) {
				engine.tickDriven(EngineSimulation.rpmToOmega(120), 1);
				String bad = unsound(engine, 100 + i);
				if (bad != null)
					return bad;
			}
		} catch (RuntimeException | StackOverflowError e) {
			return "threw " + e;
		}
		return null;
	}

	private static String unsound(EngineSimulation engine, int tick) {
		double[] figures = {engine.omega(), engine.netTorque(), engine.indicatedTorque(), engine.fuelRate(), engine.availableTorque(),
			engine.frictionTorque(), engine.thermal().felt()};
		String[] names = {"omega", "net torque", "indicated torque", "fuel rate", "available torque", "friction torque", "felt temperature"};
		for (int i = 0; i < figures.length; i++)
			if (!Double.isFinite(figures[i]))
				return names[i] + " is " + figures[i] + " at tick " + tick;
		if (engine.rpm() > 100_000)
			return "rpm ran away to " + engine.rpm() + " at tick " + tick;
		return null;
	}

	private static FuelSpec with(FuelSpec base, int field, double value) {
		double[] v = {base.energyPerMb(), base.autoignitionTemp(), base.airPerMb(), base.coldSensitivity(), base.chargeCooling(),
			base.sootFactor(), base.preignitionTemp(), base.sulfur()};
		v[field] = value;
		return new FuelSpec(v[0], v[1], v[2], v[3], v[4], v[5], v[6], v[7]);
	}

	private static final String[] FIELDS = {"energy_per_mb", "autoignition_temperature", "air_per_mb", "cold_sensitivity",
		"charge_cooling", "soot_factor", "preignition_temperature", "sulfur"};

	@Test
	void everyAwkwardValueInEveryFuelFieldLeavesAnEngineSound() {
		List<String> problems = new ArrayList<>();
		for (FuelSpec base : new FuelSpec[] {FuelSpec.DIESEL, FuelSpec.GASOLINE}) {
			boolean spark = base == FuelSpec.GASOLINE;
			for (int field = 0; field < FIELDS.length; field++)
				for (double value : AWKWARD)
					for (int cylinders : new int[] {1, 4}) {
						Tuning.reset();
						String bad = run(with(base, field, value), cylinders, spark);
						if (bad != null)
							problems.add(FIELDS[field] + " = " + value + " in a " + cylinders + " cylinder " + (spark ? "spark" : "diesel")
								+ " engine: " + bad);
					}
		}
		assertEquals(List.of(), problems, "a fuel the data map accepted left an engine unsound (" + problems.size() + " cases): "
			+ "FuelSpec.usable() is what keeps the sim to numbers it can run on");
	}

	@Test
	void everyFuelFieldAwkwardAtOnceLeavesAnEngineSound() {
		List<String> problems = new ArrayList<>();
		for (double value : AWKWARD)
			for (boolean spark : new boolean[] {false, true}) {
				Tuning.reset();
				String bad = run(new FuelSpec(value, value, value, value, value, value, value, value), 4, spark);
				if (bad != null)
					problems.add("every field " + value + " in a " + (spark ? "spark" : "diesel") + " engine: " + bad);
			}
		assertEquals(List.of(), problems, "a fuel the data map accepted left an engine unsound");
	}

	/** The checker and the fuzz agree: everything this test treats as awkward is something {@link FuelCheck} reports. */
	@Test
	void theAwkwardValuesAreTheOnesTheFuelCheckWouldFlag() {
		int flagged = 0;
		for (int field = 0; field < 3; field++)
			for (double value : new double[] {0, -1, Double.NaN, Double.POSITIVE_INFINITY}) {
				boolean reported = !FuelCheck.problems(with(FuelSpec.DIESEL, field, value)).isEmpty();
				assertTrue(reported, FIELDS[field] + " = " + value + " should be reported by FuelCheck");
				flagged++;
			}
		assertEquals(12, flagged);
	}
}
