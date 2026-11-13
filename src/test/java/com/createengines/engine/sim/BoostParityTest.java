package com.createengines.engine.sim;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * A supercharger is meant to be worth as much as a turbocharger and a variable-drive supercharger as much as a
 * variable-geometry turbo, each for its own reasons: the turbo's boost is free (no shaft to turn) and strongest in the
 * middle of the rev range; the blower's is there at once, needs no exhaust or fuel, and keeps climbing at high revs and
 * high boost where a turbine chokes the exhaust. So the two have to land close together on net peak power, the
 * blower's after its shaft drag has been taken off, and differ in where on the rev range they are strong.
 * <p>
 * The dynamometer measures steady state, so it shows nothing of the turbo's lag, which is the blower's gain on top.
 */
class BoostParityTest {

	private static final double SU_PER_TORQUE = 78.7;
	/** Peak net power of either of a pair may be this far from the other's, at the same boost limit. */
	private static final double TOLERANCE = 0.04;

	private enum Kind {
		TURBO, VARIABLE_TURBO, BLOWER, VARIABLE_BLOWER
	}

	/** Net power, SU, at each of {@link Dynamometer#SPEEDS}, with a blower's drag on its shaft taken off. */
	private static double[] net(int cylinders, CylinderSpec spec, Kind kind, double limit) {
		Camshaft cam = new Camshaft();
		List<CylinderModel> models = new ArrayList<>();
		double step = 720d / cylinders;
		for (int i = 0; i < cylinders; i++) {
			double fire = i * step;
			CylinderModel model = new CylinderModel(spec, CamProfile.BASIC, fire % 360, cam, fire / 2);
			model.fuelSupply = MixtureTest.unlimited(FuelSpec.GASOLINE);
			model.metering = 2;
			model.airMatch = 1;
			model.ignition = IgnitionSource.UNLIMITED;
			models.add(model);
		}
		EngineSimulation sim = new EngineSimulation(models, cylinders * 0.35 + 1, (cylinders * 2 + 1) * 0.01);
		for (CylinderModel model : models)
			model.resetGas(0);
		boolean blower = kind == Kind.BLOWER || kind == Kind.VARIABLE_BLOWER;
		boolean variable = kind == Kind.VARIABLE_TURBO || kind == Kind.VARIABLE_BLOWER;
		if (blower) {
			Supercharger unit = new Supercharger(models);
			unit.rpm = Supercharger.DESIGN_RPM;
			unit.limit = limit;
			unit.variableDrive = variable;
			sim.addSupercharger(unit);
		} else {
			Turbocharger unit = new Turbocharger(models, models, 1);
			unit.limit = limit;
			unit.variableGeometry = variable;
			sim.addTurbo(unit);
		}
		double[] torque = Dynamometer.torqueCurve(sim);
		double[] power = new double[torque.length];
		for (int i = 0; i < torque.length; i++) {
			double rpm = Dynamometer.SPEEDS[i];
			power[i] = torque[i] * rpm * SU_PER_TORQUE;
			if (blower) {
				Supercharger shaft = new Supercharger(models);
				shaft.variableDrive = variable;
				shaft.rpm = rpm;
				double boost = Math.min(limit + 0.05, shaft.potential());
				power[i] -= cylinders * SuperchargerDrag.PER_CYLINDER * Math.min(1.7, boost / Supercharger.FULL_BOOST)
					* shaft.dragFactor() * rpm;
			}
		}
		return power;
	}

	private static double peak(double[] power) {
		double best = Double.NEGATIVE_INFINITY;
		for (double p : power)
			best = Math.max(best, p);
		return best;
	}

	private static double at(double[] power, double rpm) {
		for (int i = 0; i < power.length; i++)
			if (Dynamometer.SPEEDS[i] == rpm)
				return power[i];
		throw new IllegalArgumentException("no measurement at " + rpm + " RPM");
	}

	private static void assertClose(String what, double a, double b) {
		assertTrue(Math.abs(a - b) <= TOLERANCE * Math.max(a, b), what + ": " + Math.round(a) + " against " + Math.round(b));
	}

	@Test
	void aSuperchargerIsWorthAsMuchAsATurbochargerAtTheSameLimit() {
		for (CylinderSpec spec : new CylinderSpec[] {CylinderSpec.CRUDE, CylinderSpec.build(true, true, true, true)})
			for (double limit : new double[] {0.4, 0.5, 0.6, 0.8})
				assertClose("blower against turbo at " + limit + " bar",
					peak(net(8, spec, Kind.BLOWER, limit)), peak(net(8, spec, Kind.TURBO, limit)));
	}

	@Test
	void aVariableDriveSuperchargerIsWorthAsMuchAsAVariableGeometryTurbo() {
		for (CylinderSpec spec : new CylinderSpec[] {CylinderSpec.CRUDE, CylinderSpec.build(true, true, true, true)})
			for (double limit : new double[] {0.4, 0.5, 0.6, 0.8})
				assertClose("variable-drive blower against variable-geometry turbo at " + limit + " bar",
					peak(net(8, spec, Kind.VARIABLE_BLOWER, limit)), peak(net(8, spec, Kind.VARIABLE_TURBO, limit)));
	}

	/** Both are worth the same, but not the same: the blower is the one that keeps its power as the revs climb. */
	@Test
	void theBlowerKeepsItsPowerAtHighRevsWhereTheTurboChokes() {
		CylinderSpec spec = CylinderSpec.build(true, true, true, true);
		assertTrue(at(net(8, spec, Kind.BLOWER, 0.6), 256) > 1.1 * at(net(8, spec, Kind.TURBO, 0.6), 256),
			"a blower should out-run a turbo at 256 RPM");
		assertTrue(at(net(8, spec, Kind.VARIABLE_BLOWER, 0.6), 256) > 1.1 * at(net(8, spec, Kind.VARIABLE_TURBO, 0.6), 256),
			"and so should a variable-drive blower a variable-geometry turbo");
	}

	/** The drive's whole point: with the shaft slow, it makes more than the plain blower does. */
	@Test
	void theVariableDriveMakesMorePowerThanAPlainBlowerWhileTheShaftIsSlow() {
		CylinderSpec spec = CylinderSpec.build(true, true, true, true);
		double[] plain = net(8, spec, Kind.BLOWER, 0.6);
		double[] drive = net(8, spec, Kind.VARIABLE_BLOWER, 0.6);
		assertTrue(at(drive, 64) > 1.05 * at(plain, 64), "at 64 RPM: " + at(drive, 64) + " against " + at(plain, 64));
	}

	/** Mirrors the stress the block puts on its shaft for each cylinder it feeds at full boost. */
	private static final class SuperchargerDrag {
		static final double PER_CYLINDER = 2;
	}
}
