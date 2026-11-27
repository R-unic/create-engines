package com.createengines.engine.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * The two things a compression part does to a flame beyond its ratio: the temperature a cylinder with no plug judges lighting
 * by, and what a compression rotor makes of boost on a fuel that holds out against it.
 */
class CompressionLightingTest {

	/** A ratio too low for its squeeze to light diesel, which the lighting scale below makes up for. */
	private static final double TOO_LOW = 3.5;
	private static final double SCALE = 1.35;

	/** The best torque the Dynamometer finds for one cylinder of this ratio. */
	private static double bestTorque(double lightingHeat, FuelSpec fuel, FuelSpec pilot) {
		CylinderSpec spec = new PartStats(TOO_LOW / CylinderSpec.CRUDE.compressionRatio(), 1, 1, 1, 1, 1, 1, 1, 1, 1, 1)
			.apply(CylinderSpec.CRUDE);
		CylinderModel model = new CylinderModel(spec, CamProfile.BASIC, 0, new Camshaft(), 0);
		model.fuelSupply = MixtureTest.unlimited(fuel);
		if (pilot != null)
			model.pilotSupply = MixtureTest.unlimited(pilot);
		model.metering = 1;
		model.airMatch = 1;
		model.lightingHeat = lightingHeat;
		EngineSimulation sim = new EngineSimulation(List.of(model), 1.35, 0.03);
		model.resetGas(0);
		double best = Double.NEGATIVE_INFINITY;
		for (double torque : Dynamometer.torqueCurve(sim))
			best = Math.max(best, torque);
		return best;
	}

	@Test
	void theLightingScaleLightsADieselThatTheSqueezeAloneWouldNot() {
		double without = bestTorque(1, FuelSpec.DIESEL, null);
		double with = bestTorque(SCALE, FuelSpec.DIESEL, null);
		assertTrue(without <= 0, "this ratio does not light diesel by itself: " + without);
		assertTrue(with > 0, "judged by the stock piston's temperature it fires: " + with);
	}

	@Test
	void theLightingScaleLetsAPilotShotLightTheSameWay() {
		double without = bestTorque(1, FuelSpec.GASOLINE, FuelSpec.DIESEL);
		double with = bestTorque(SCALE, FuelSpec.GASOLINE, FuelSpec.DIESEL);
		assertTrue(without <= 0, "the pilot does not light at this ratio by itself: " + without);
		assertTrue(with > 0, "and does when judged by the stock piston's temperature: " + with);
	}

	private static double boostedRotor(FuelSpec fuel, double gain, double loss) {
		java.util.ArrayList<CylinderModel> models = new java.util.ArrayList<>();
		for (int chamber = 0; chamber < 3; chamber++) {
			CylinderModel model = CylinderModel.rotary(CylinderSpec.ROTARY, CamProfiles.rotary(), 0, chamber);
			model.fuelSupply = MixtureTest.unlimited(fuel);
			model.metering = 2;
			model.airMatch = 1;
			model.ignition = IgnitionSource.UNLIMITED;
			model.rotorBoostGain = gain;
			model.rotorEarlyLoss = loss;
			models.add(model);
		}
		EngineSimulation sim = new EngineSimulation(models, 1.5, 0.05);
		Supercharger blower = new Supercharger(models);
		blower.rpm = Supercharger.DESIGN_RPM;
		blower.limit = 1;
		sim.addSupercharger(blower);
		double sum = 0;
		for (double torque : Dynamometer.torqueCurve(sim))
			sum += torque;
		return sum;
	}

	/** Ethanol sits exactly on the line: a fuel has to light early sooner than it does for a compression rotor to notice. */
	@Test
	void aFuelThatHoldsOutAsWellAsEthanolGetsNeitherHalfOfTheRotorsTrade() {
		for (FuelSpec fuel : new FuelSpec[] {FuelSpec.ETHANOL, FuelSpec.METHANOL}) {
			double stock = boostedRotor(fuel, 0, 0);
			assertEquals(stock, boostedRotor(fuel, CylinderSpec.LOW_ROTOR_BOOST_GAIN, 0));
			assertEquals(stock, boostedRotor(fuel, 0, CylinderSpec.HIGH_ROTOR_EARLY_LOSS));
		}
		double gasoline = boostedRotor(FuelSpec.GASOLINE, 0, 0);
		assertTrue(boostedRotor(FuelSpec.GASOLINE, CylinderSpec.LOW_ROTOR_BOOST_GAIN, 0) > gasoline);
		assertTrue(boostedRotor(FuelSpec.GASOLINE, 0, CylinderSpec.HIGH_ROTOR_EARLY_LOSS) < gasoline);
	}
}
