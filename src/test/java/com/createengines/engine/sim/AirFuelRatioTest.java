package com.createengines.engine.sim;

import static com.createengines.engine.sim.MixtureTest.runAt;
import static com.createengines.engine.sim.MixtureTest.unlimited;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/** What running at the wrong air-fuel ratio costs, and what running at the right one pays. */
class AirFuelRatioTest {

	private static final FuelSupply GASOLINE = unlimited(FuelSpec.GASOLINE);
	private static final FuelSupply DIESEL = unlimited(FuelSpec.DIESEL);

	/** The equivalence a tuned engine runs at: inside the balanced band, a little lean of exact. */
	private static final double TUNED = 0.95;

	/** A cylinder on a crank held at one speed, so the mixture can be changed while it runs. */
	private static final class Rig {
		final CylinderModel cylinder = new CylinderModel(CylinderSpec.CRUDE, CamProfile.BASIC, 0, new Camshaft(), 0);
		final EngineSimulation engine = new EngineSimulation(List.of(cylinder), 1, 0.02);
		final double rpm;
		final double tunedMetering;

		Rig(FuelSupply fuel, IgnitionSource ignition, double rpm) {
			this.rpm = rpm;
			tunedMetering = tune(fuel, ignition, rpm);
			cylinder.fuelSupply = fuel;
			cylinder.ignition = ignition;
			cylinder.metering = tunedMetering;
			cylinder.resetGas(0);
		}

		private Rig(FuelSupply fuel, IgnitionSource ignition, double rpm, boolean scratch) {
			this.rpm = rpm;
			tunedMetering = 1;
			cylinder.fuelSupply = fuel;
			cylinder.ignition = ignition;
			cylinder.resetGas(0);
		}

		/**
		 * The metering that gives {@link #TUNED} in an engine that is firing steadily. A firing cylinder breathes less
		 * than a motored one (its residual gas is hot), so this is found by running one and correcting it.
		 */
		private static double tune(FuelSupply fuel, IgnitionSource ignition, double rpm) {
			Rig scratch = new Rig(fuel, ignition, rpm, true);
			double metering = 0.5;
			for (int i = 0; i < 5; i++) {
				scratch.cylinder.metering = metering;
				scratch.run(8);
				double equivalence = scratch.last().equivalence;
				if (equivalence <= 0)
					break;
				metering *= TUNED / equivalence;
			}
			return metering;
		}

		/** Sets the injector for this equivalence ratio, scaled from the shot that gives {@link #TUNED}. */
		Rig mixture(double equivalence) {
			cylinder.metering = tunedMetering * equivalence / TUNED;
			return this;
		}

		Rig run(double seconds) {
			for (int i = 0; i < seconds * 20; i++)
				engine.tickDriven(EngineSimulation.rpmToOmega(rpm), 1);
			return this;
		}

		CycleStats last() {
			return cylinder.lastCycle();
		}
	}

	@Test
	void balanceIsRewardedMoreThanImbalanceIsPunished() {
		// Exactly balanced is the best, and the bonus fades to an ordinary charge at the edges of the band
		assertEquals(1 + Mixture.BALANCED_HEAT_BONUS, Mixture.efficiency(1), 1e-9);
		assertEquals(1 + Mixture.BALANCED_FLAME_BONUS, Mixture.flameSpeed(1), 1e-9);
		for (double edge : new double[] {Mixture.LEAN_BAND, Mixture.RICH_BAND}) {
			assertEquals(1, Mixture.efficiency(edge), 1e-9);
			assertEquals(1, Mixture.flameSpeed(edge), 1e-9);
		}
		assertTrue(Mixture.efficiency(0.95) > Mixture.efficiency(0.85));
		assertTrue(Mixture.efficiency(1.1) > Mixture.efficiency(1.2));
		for (double off : new double[] {0.3, 0.5, 0.7, 1.4, 1.8}) {
			assertTrue(Mixture.efficiency(off) < 1, "efficiency at " + off);
			assertTrue(Mixture.flameSpeed(off) < 1, "flame speed at " + off);
		}
		assertTrue(Mixture.efficiency(0.5) < Mixture.efficiency(0.7));
		assertTrue(Mixture.efficiency(1.8) < Mixture.efficiency(1.4));
		assertTrue(Mixture.flameSpeed(0.5) < Mixture.flameSpeed(0.7));
		// Being off costs little: a modest floor, nowhere near the size of the reward's other side
		assertTrue(Mixture.efficiency(0) >= 0.85 && Mixture.flameSpeed(0) >= 0.7, "never worse than a floor");
		assertTrue(Mixture.efficiency(2) >= 0.85 && Mixture.flameSpeed(2) >= 0.8);
	}

	/** The payoff for tuning: the same fuel makes more work in a balanced charge than in a lean or a rich one. */
	@Test
	void aBalancedMixtureMakesTheMostWorkForItsFuel() {
		double balanced = workPerFuel(TUNED);
		double lean = workPerFuel(0.4);
		double rich = workPerFuel(2);
		assertTrue(balanced > 1.2 * lean, "balanced " + balanced + " vs lean " + lean);
		assertTrue(balanced > 1.5 * rich, "balanced " + balanced + " vs rich " + rich);
	}

	private static double workPerFuel(double equivalence) {
		CycleStats cycle = new Rig(DIESEL, IgnitionSource.NONE, 96).mixture(equivalence)
			.run(10)
			.last();
		return cycle.work / cycle.fuelInjected;
	}

	/**
	 * The drawback of a flooded spark engine: soot builds on the plug until it coughs. It stutters rather than locking
	 * out, because a plug that can't light the charge scrubs itself until it can again.
	 */
	@Test
	void runningRichFoulsASparkPlugUntilItCoughs() {
		Rig rig = new Rig(GASOLINE, IgnitionSource.UNLIMITED, 128).mixture(1.7);
		boolean coughed = false;
		int fired = 0;
		double worst = 0;
		for (int i = 0; i < 120; i++) {
			rig.run(0.25);
			coughed |= rig.last().result == CycleResult.FOULED;
			if (rig.last().result.fired())
				fired++;
			worst = Math.max(worst, rig.cylinder.fouling());
		}
		assertTrue(worst > 0.2, "fouling " + worst);
		assertTrue(coughed, "a fouled plug should misfire now and then");
		assertTrue(fired > 20, "but it keeps firing between coughs: " + fired);
	}

	/** Even a completely sooted plug can light a mixture a little richer than balanced. */
	@Test
	void aSoottedPlugStillLightsAMildlyRichMixture() {
		Rig rig = new Rig(GASOLINE, IgnitionSource.UNLIMITED, 128).mixture(1.35);
		rig.run(40);
		assertTrue(rig.last().result.fired() || rig.cylinder.fouling() < 0.9, "was " + rig.last().result);
	}

	/** Fouling is gradual: a moment of rich running doesn't stop an engine, a minute of it does. */
	@Test
	void foulingBuildsOverManyCyclesNotOne() {
		Rig rig = new Rig(GASOLINE, IgnitionSource.UNLIMITED, 128).mixture(1.5);
		rig.run(3);
		assertTrue(rig.cylinder.fouling() > 0, "rich cycles leave soot");
		assertTrue(rig.cylinder.fouling() < 0.6, "fouling " + rig.cylinder.fouling());
		assertTrue(rig.last().result.fired(), "still firing: " + rig.last().result);
	}

	/** And the cure: the same plug, once the mixture is brought back, burns clean again and fires. */
	@Test
	void aFouledPlugCleansItselfOnceTheMixtureIsCorrected() {
		Rig rig = new Rig(GASOLINE, IgnitionSource.UNLIMITED, 128).mixture(1.7);
		rig.run(30);

		rig.mixture(TUNED);
		rig.run(40);
		assertTrue(rig.cylinder.fouling() < 0.1, "fouling " + rig.cylinder.fouling());
		assertTrue(rig.last().result.fired(), "should fire again: " + rig.last().result);
	}

	@Test
	void aBalancedSparkEngineNeverFoulsItsPlug() {
		Rig rig = new Rig(GASOLINE, IgnitionSource.UNLIMITED, 128).mixture(TUNED);
		rig.run(30);
		assertTrue(rig.last().result.fired(), "was " + rig.last().result);
		assertTrue(rig.cylinder.fouling() < 0.05, "fouling " + rig.cylinder.fouling());
	}

	/** Ethanol burns clean however rich it is run: the plug never gets soot (the engine still can't light a mixture too rich to burn). */
	@Test
	void ethanolLeavesNoSootOnAPlug() {
		Rig rig = new Rig(unlimited(FuelSpec.ETHANOL), IgnitionSource.UNLIMITED, 128).mixture(1.7);
		double worst = 0;
		for (int i = 0; i < 60; i++) {
			rig.run(0.25);
			worst = Math.max(worst, rig.cylinder.fouling());
		}
		assertEquals(0, worst, 1e-9, "a rich ethanol burn should not foul the plug");
	}

	@Test
	void anEngineWithNoPlugHasNothingToFoul() {
		Rig rig = new Rig(DIESEL, IgnitionSource.NONE, 160).mixture(2)
			.run(30);
		assertTrue(rig.last().rich());
		assertEquals(0, rig.cylinder.fouling(), 1e-9);
	}

	/** Unburned fuel runs down the walls: the oil is weaker however well it is supplied, so the engine drags. */
	@Test
	void runningRichThinsTheOilAndRaisesFriction() {
		Rig rich = new Rig(DIESEL, IgnitionSource.NONE, 160).mixture(2)
			.run(30);
		Rig balanced = new Rig(DIESEL, IgnitionSource.NONE, 160).mixture(TUNED)
			.run(30);
		double thinned = rich.engine.lubrication()
			.dilution();
		assertTrue(thinned > 0.3, "dilution " + thinned);
		assertTrue(thinned <= Lubrication.MAX_DILUTION);
		assertEquals(0, balanced.engine.lubrication()
			.dilution(), 1e-9);
		assertEquals(1, rich.engine.lubrication()
			.film(), 1e-9, "the supply is still keeping the film up");
		assertTrue(rich.engine.lubrication()
			.effective() < 0.7);
		assertTrue(rich.engine.frictionTorque() > 1.3 * balanced.engine.frictionTorque(),
			rich.engine.frictionTorque() + " vs " + balanced.engine.frictionTorque());
	}

	@Test
	void theOilRecoversOnceTheEngineBurnsWhatItIsGiven() {
		Rig rig = new Rig(DIESEL, IgnitionSource.NONE, 160).mixture(2)
			.run(30);
		double thinned = rig.engine.lubrication()
			.dilution();
		assertTrue(thinned > 0.3);
		rig.mixture(TUNED);
		rig.run(60);
		assertTrue(rig.engine.lubrication()
			.dilution() < thinned / 2, "dilution " + rig.engine.lubrication()
			.dilution());
	}

	@Test
	void anEngineWithNoFuelInItNeitherThinsNorCleansTheOil() {
		Rig rig = new Rig(FuelSupply.NONE, IgnitionSource.NONE, 96);
		rig.engine.lubrication()
			.setDilution(0.3);
		rig.run(20);
		assertEquals(0.3, rig.engine.lubrication()
			.dilution(), 1e-9);
		assertFalse(rig.last().result.fired());
	}

	@Test
	void aDynamometerDoesNotThinTheOilItMeasures() {
		Rig rig = new Rig(DIESEL, IgnitionSource.NONE, 160).mixture(2);
		rig.engine.setWashesOil(false);
		rig.run(30);
		assertEquals(0, rig.engine.lubrication()
			.dilution(), 1e-9);
	}
}
