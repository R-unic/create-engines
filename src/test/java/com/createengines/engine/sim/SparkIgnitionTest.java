package com.createengines.engine.sim;

import static com.createengines.engine.sim.MixtureTest.runAt;
import static com.createengines.engine.sim.MixtureTest.unlimited;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SparkIgnitionTest {

	private static final FuelSupply GASOLINE = unlimited(FuelSpec.GASOLINE);
	private static final FuelSupply DIESEL = unlimited(FuelSpec.DIESEL);

	/** A plug with a battery that can be run flat. */
	private static final class Battery implements IgnitionSource {
		double charge;
		int sparks;

		Battery(double charge) {
			this.charge = charge;
		}

		@Override
		public boolean installed() {
			return true;
		}

		@Override
		public boolean powered() {
			return charge >= 1;
		}

		@Override
		public boolean spark() {
			if (charge < 1)
				return false;
			charge -= 1;
			sparks++;
			return true;
		}
	}

	/** Gasoline is too hard to light by squeezing at any speed this engine turns slowly enough to start. */
	@Test
	void gasolineWillNotLightWithoutASparkPlug() {
		for (double rpm : new double[] {16, 32, 64, 96, 128}) {
			CycleStats cycle = runAt(rpm, GASOLINE, 1, IgnitionSource.NONE).lastCycle();
			assertEquals(CycleResult.NO_IGNITION, cycle.result, rpm + " RPM");
			assertTrue(cycle.fuelInjected > 0 && cycle.fuelBurned == 0);
		}
	}

	@Test
	void gasolineFiresWithASparkPlug() {
		for (double rpm : new double[] {16, 32, 64, 96, 128}) {
			CycleStats cycle = runAt(rpm, GASOLINE, 0.9, IgnitionSource.UNLIMITED).lastCycle();
			assertEquals(CycleResult.FIRED, cycle.result, rpm + " RPM");
			assertTrue(cycle.work > 0, rpm + " RPM should make power, made " + cycle.work);
		}
	}

	@Test
	void aSparkIsFiredOncePerCycle() {
		Battery battery = new Battery(1000);
		CylinderModel cylinder = runAt(64, GASOLINE, 0.9, battery);
		// 20 s at 64 RPM is about 10.7 four-stroke cycles
		assertTrue(battery.sparks >= 10 && battery.sparks <= 11, "sparks " + battery.sparks);
		assertEquals(1, cylinder.lastCycle().sparks);
	}

	@Test
	void aFlatBatteryGivesNoSpark() {
		Battery battery = new Battery(0);
		CycleStats cycle = runAt(64, GASOLINE, 0.9, battery).lastCycle();
		assertEquals(CycleResult.NO_SPARK, cycle.result);
		assertTrue(cycle.deadSparks > 0 && cycle.sparks == 0);
	}

	/** A battery that runs out partway stops the engine firing: it fires on what charge it had, then misfires. */
	@Test
	void anEngineFiresUntilTheBatteryRunsOut() {
		Battery battery = new Battery(5);
		CylinderModel cylinder = runAt(64, GASOLINE, 0.9, battery);
		assertEquals(5, battery.sparks);
		assertEquals(CycleResult.NO_SPARK, cylinder.lastCycle().result);
	}

	/** A spark can't light fuel that isn't there to be lit in proportion to the air. */
	@Test
	void aSparkCannotLightAMixtureThatIsFarTooLean() {
		CycleStats cycle = runAt(32, GASOLINE, 0.1, IgnitionSource.UNLIMITED).lastCycle();
		assertEquals(CycleResult.TOO_LEAN, cycle.result);
	}

	/** Twice the shot that smokes a diesel at speed drowns a spark engine outright. */
	@Test
	void aSparkCannotLightAMixtureDrownedInFuel() {
		CycleStats cycle = runAt(160, GASOLINE, 2, IgnitionSource.UNLIMITED).lastCycle();
		assertTrue(cycle.result == CycleResult.TOO_RICH || cycle.result == CycleResult.FOULED, "was " + cycle.result);
		assertEquals(0, cycle.fuelBurned);
	}

	/** Compression ignition doesn't care about the mixture's limits: rich diesel just smokes. */
	@Test
	void aRichDieselStillFiresWhereARichSparkEngineWouldNot() {
		CycleStats cycle = runAt(160, DIESEL, 2, IgnitionSource.NONE).lastCycle();
		assertEquals(CycleResult.FIRED, cycle.result);
		assertTrue(cycle.rich());
	}

	@Test
	void aDieselWithAPlugStillRunsWhenTheBatteryIsFlat() {
		CycleStats cycle = runAt(64, DIESEL, 1, new Battery(0)).lastCycle();
		assertEquals(CycleResult.FIRED, cycle.result);
		assertTrue(cycle.deadSparks > 0);
	}
}
