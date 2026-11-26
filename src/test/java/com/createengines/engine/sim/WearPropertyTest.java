package com.createengines.engine.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import java.util.function.DoubleUnaryOperator;

import org.junit.jupiter.api.Test;

/**
 * What must be true of the wear curves at any ordinary operating point, checked at thousands of seeded random ones: harder running never
 * wears less, a better part never wears more, and the answer does not depend on how the time is cut up. The seed is fixed, so a failure
 * happens again.
 */
class WearPropertyTest {

	private static final int ROUNDS = 5000;
	private static final double EPS = 1e-12;

	private final Random random = new Random(20261125L);

	private double in(double low, double high) {
		return low + random.nextDouble() * (high - low);
	}

	private double rpm() {
		return in(0, 800);
	}

	private double heat() {
		return in(0, 1.5);
	}

	private double film() {
		return in(0, 1);
	}

	private double pressure() {
		return in(0.2, 4);
	}

	/** {@code f} never falls as its argument rises from {@code a} to a larger {@code b}. */
	private void rising(DoubleUnaryOperator f, double a, double b, String what) {
		double low = Math.min(a, b), high = Math.max(a, b);
		assertTrue(f.applyAsDouble(high) >= f.applyAsDouble(low) - EPS,
			() -> what + " fell from " + f.applyAsDouble(low) + " at " + low + " to " + f.applyAsDouble(high) + " at " + high);
	}

	/** Two steps of {@code a} and {@code b} seconds wear what one step of both does. */
	private void additive(DoubleUnaryOperator overTime, double a, double b, String what) {
		assertEquals(overTime.applyAsDouble(a + b), overTime.applyAsDouble(a) + overTime.applyAsDouble(b), 1e-9, what);
	}

	@Test
	void pistonsWearMoreTheFasterHotterAndDrierTheyRun() {
		for (int i = 0; i < ROUNDS; i++) {
			double rpm = rpm(), heat = heat(), film = film(), dt = in(0, 60);
			rising(r -> Wear.pistonWear(r, heat, film, dt), rpm, rpm(), "piston by speed");
			rising(t -> Wear.pistonWear(rpm, t, film, dt), heat, heat(), "piston by heat");
			rising(dry -> Wear.pistonWear(rpm, heat, 1 - dry, dt), film, film(), "piston by dryness");
			rising(t -> Wear.pistonWear(rpm, heat, film, t), dt, in(0, 60), "piston by time");
		}
	}

	@Test
	void aForgedPistonAlwaysOutlastsAStandardOneByTheSameFactor() {
		for (int i = 0; i < ROUNDS; i++) {
			double rpm = in(1, 800), heat = heat(), film = film(), dt = in(0.05, 5);
			assertEquals(Wear.pistonWear(rpm, heat, film, dt), Wear.pistonWear(rpm, heat, film, dt, true) * Wear.PRECISION_LIFE, EPS);
		}
	}

	@Test
	void rodsWearMoreTheFasterAndDrierTheyRun() {
		for (int i = 0; i < ROUNDS; i++) {
			double rpm = rpm(), film = film(), dt = in(0, 60);
			rising(r -> Wear.rodWear(r, film, dt), rpm, rpm(), "rod by speed");
			rising(dry -> Wear.rodWear(rpm, 1 - dry, dt), film, film(), "rod by dryness");
		}
	}

	@Test
	void gasketsWearMoreUnderBoostAndABetterTierNeverWearsFaster() {
		Wear.Gasket[] tiers = Wear.Gasket.values();
		for (int i = 0; i < ROUNDS; i++) {
			double rpm = rpm(), heat = heat(), pressure = pressure(), dt = in(0, 60);
			for (Wear.Gasket tier : tiers) {
				rising(p -> Wear.gasketWear(tier, rpm, heat, p, dt), pressure, pressure(), tier + " by boost");
				rising(r -> Wear.gasketWear(tier, r, heat, pressure, dt), rpm, rpm(), tier + " by speed");
			}
			for (int t = 1; t < tiers.length; t++)
				assertTrue(Wear.gasketWear(tiers[t], rpm, heat, pressure, dt) <= Wear.gasketWear(tiers[t - 1], rpm, heat, pressure, dt) + EPS,
					tiers[t] + " wore faster than " + tiers[t - 1]);
		}
	}

	@Test
	void everyBetterGasketIsBetterInEveryFigure() {
		Wear.Gasket[] tiers = Wear.Gasket.values();
		for (int t = 1; t < tiers.length; t++) {
			assertTrue(tiers[t].life > tiers[t - 1].life, tiers[t] + " life");
			assertTrue(tiers[t].hold > tiers[t - 1].hold, tiers[t] + " hold");
			assertTrue(tiers[t].seal < tiers[t - 1].seal, tiers[t] + " seal loss");
			assertTrue(tiers[t].bonus > tiers[t - 1].bonus, tiers[t] + " bonus");
		}
	}

	@Test
	void everySavedGasketNumberReadsAsSomeTierAndOnlyRealOnesAreNotStock() {
		for (int id = -1000; id <= 1000; id++) {
			Wear.Gasket read = Wear.Gasket.byId(id);
			if (id >= 0 && id < Wear.Gasket.values().length)
				assertEquals(id, read.ordinal());
			else
				assertEquals(Wear.Gasket.STOCK, read, "id " + id);
		}
		assertEquals(Wear.Gasket.STOCK, Wear.Gasket.byId(Integer.MAX_VALUE));
		assertEquals(Wear.Gasket.STOCK, Wear.Gasket.byId(Integer.MIN_VALUE));
	}

	@Test
	void howTheTimeIsCutUpDoesNotChangeHowMuchWears() {
		for (int i = 0; i < ROUNDS; i++) {
			double rpm = rpm(), heat = heat(), film = film(), pressure = pressure(), a = in(0, 2), b = in(0, 2);
			additive(t -> Wear.pistonWear(rpm, heat, film, t), a, b, "piston");
			additive(t -> Wear.rodWear(rpm, film, t), a, b, "rod");
			additive(t -> Wear.gasketWear(Wear.Gasket.RUBBER, rpm, heat, pressure, t), a, b, "gasket");
			additive(t -> ApexSeals.wear(rpm, heat, film, t), a, b, "apex");
			additive(t -> TurboWear.wear(pressure - 1, heat, film, t), a, b, "turbo");
			additive(t -> PlugWear.wear(rpm, true, film, t), a, b, "plug");
			additive(t -> Knock.pistonWear(film * 0.2, rpm, t), a, b, "knock");
		}
	}

	/** The chance of no failure over two spans is close to that over both: a slower or faster wear step does not change the odds. */
	@Test
	void failureOddsHardlyDependOnHowTheTimeIsCutUp() {
		for (int i = 0; i < ROUNDS; i++) {
			double wear = in(0, 1), rpm = in(1, 512);
			double whole = Wear.failureChance(wear, rpm, 1);
			double halves = 1 - Math.pow(1 - Wear.failureChance(wear, rpm, 0.5), 2);
			assertEquals(whole, halves, whole * whole + 1e-12, "wear " + wear + " rpm " + rpm);
		}
	}

	@Test
	void aMoreWornPartIsNeverLessLikelyToFail() {
		for (int i = 0; i < ROUNDS; i++) {
			double a = in(-0.2, 1.2), b = in(-0.2, 1.2), rpm = rpm(), boost = in(0, 5), dt = in(0, 10);
			rising(w -> Wear.failureChance(w, rpm, dt), a, b, "rod/gasket failure");
			rising(w -> ApexSeals.failureChance(w, rpm, dt), a, b, "apex failure");
			rising(w -> TurboWear.failureChance(w, boost, dt), a, b, "turbo failure");
			rising(psi -> TurboWear.failureChance(a, psi, dt), boost, in(0, 5), "turbo failure by boost");
		}
	}

	@Test
	void noPartHalfWornOrLessEverFails() {
		for (int i = 0; i < ROUNDS; i++) {
			double wear = in(0, Wear.FAILURE_FROM), rpm = in(0, 100000), dt = in(0, 100000);
			assertEquals(0, Wear.failureChance(wear, rpm, dt), 0);
			assertEquals(0, ApexSeals.failureChance(wear, rpm, dt), 0);
			assertEquals(0, TurboWear.failureChance(wear, in(0, 50), dt), 0);
		}
	}

	@Test
	void aMoreWornPartNeverPerformsBetter() {
		for (int i = 0; i < ROUNDS; i++) {
			double a = in(0, 1), b = in(0, 1);
			rising(w -> -Wear.pistonSeal(w), a, b, "piston seal loss");
			rising(w -> Wear.pistonDrag(w), a, b, "piston drag");
			rising(w -> -ApexSeals.seal(w), a, b, "apex loss");
			rising(w -> -TurboWear.boostShare(w), a, b, "turbo loss");
			rising(w -> PlugWear.misfireChance(w), a, b, "plug misfire");
			rising(w -> PlugWear.leanScale(w), a, b, "plug lean");
			for (Wear.Gasket tier : Wear.Gasket.values()) {
				rising(w -> -Wear.gasketSeal(tier, w), a, b, tier + " seal loss");
				rising(w -> -Wear.fittedSeal(tier, w), a, b, tier + " fitted bonus");
				rising(w -> -Wear.of(0, tier, w, true)
					.seal(), a, b, tier + " fitted state");
			}
		}
	}

	@Test
	void theRepairBandIsExactlyAboveTheRepairedLevelUpToTheLimit() {
		for (int i = 0; i <= 100000; i++) {
			double wear = i / 100000.0;
			boolean expected = wear > Wear.REPAIRED_TO && wear <= Wear.REPAIRABLE;
			assertEquals(expected, Wear.repairable(wear), "at " + wear);
			assertEquals(expected, BatteryWear.repairable(wear), "battery at " + wear);
			assertEquals(expected ? Wear.REPAIRED_TO : wear, Wear.repaired(wear), 0);
		}
		assertTrue(Wear.repairable(Wear.REPAIRABLE));
		assertFalse(Wear.repairable(Math.nextUp(Wear.REPAIRABLE)));
		assertFalse(Wear.repairable(Wear.REPAIRED_TO));
		assertTrue(Wear.repairable(Math.nextUp(Wear.REPAIRED_TO)));
	}

	@Test
	void aSecondRepairDoesNothing() {
		for (int i = 0; i < ROUNDS; i++) {
			double once = Wear.repaired(in(0, 1));
			assertEquals(once, Wear.repaired(once), 0);
		}
	}

	/** A part cannot be repaired often enough to last for ever without ever getting a failure chance: repair only works while it is safe. */
	@Test
	void nothingThatCanFailCanBeRepaired() {
		assertTrue(Wear.REPAIRABLE < Wear.FAILURE_FROM);
		assertTrue(Wear.SEIZED_WEAR > Wear.REPAIRABLE, "a seized piston is past repair");
	}

	@Test
	void aRandomDriveWithOilComingAndGoingKeepsTheDryCountInRangeAndOnlySeizesDry() {
		for (int drive = 0; drive < 200; drive++) {
			double dry = 0;
			boolean forged = random.nextBoolean();
			for (int step = 0; step < 2000; step++) {
				double film = film(), rpm = rpm(), dt = in(0, 2);
				double after = Wear.dryTime(dry, film, rpm, dt, forged);
				assertTrue(after >= 0 && after <= Wear.SEIZE_AFTER);
				if (film >= Wear.SEIZE_FILM)
					assertTrue(after <= dry, "oil never dries a cylinder");
				else
					assertTrue(after >= dry, "running dry never helps");
				dry = after;
			}
		}
	}

	@Test
	void aDryPistonCriesOutExactlyFiveTimesBeforeItSeizesAtAnyOrdinarySpeed() {
		for (int drive = 0; drive < 500; drive++) {
			double rpm = in(1, 800);
			boolean forged = random.nextBoolean();
			double dry = 0;
			int cries = 0;
			for (int step = 0; step < 100000 && !Wear.seizes(dry); step++) {
				double after = Wear.dryTime(dry, 0, rpm, 1, forged);
				if (Wear.warns(dry, after))
					cries++;
				dry = after;
			}
			assertTrue(Wear.seizes(dry), "never seized at " + rpm);
			assertEquals(5, cries, "at " + rpm + " rpm, forged " + forged);
		}
	}

	@Test
	void oilNeverCriesOut() {
		for (int i = 0; i < ROUNDS; i++) {
			double before = in(0, Wear.SEIZE_AFTER);
			double after = Wear.dryTime(before, in(Wear.SEIZE_FILM, 1), in(1, 800), in(0, 10), false);
			assertFalse(Wear.warns(before, after));
		}
	}

	@Test
	void aBatteryHoldsLessTheMoreWornItIsAndEveryTierKeepsHalf() {
		for (BatteryTier tier : BatteryTier.values()) {
			float last = Float.MAX_VALUE;
			for (int i = 0; i <= 1000; i++) {
				float capacity = BatteryWear.capacity(tier, i / 1000.0);
				assertTrue(capacity <= last, tier + " grew at " + i);
				last = capacity;
			}
			assertEquals(tier.capacity() * (1 - BatteryWear.MAX_FADE), last, 1e-3);
		}
	}

	@Test
	void aDeeperOrColderDrawNeverWearsABatteryLess() {
		for (int i = 0; i < ROUNDS; i++) {
			BatteryTier tier = BatteryTier.values()[random.nextInt(BatteryTier.values().length)];
			double drawn = in(0, 20), share = in(0, 1), cold = in(0, 1);
			rising(empty -> BatteryWear.drawWear(tier, drawn, 1 - empty, cold), share, in(0, 1), tier + " depth");
			rising(c -> BatteryWear.drawWear(tier, drawn, share, c), cold, in(0, 1), tier + " cold");
			rising(d -> BatteryWear.drawWear(tier, d, share, cold), drawn, in(0, 20), tier + " amount");
		}
	}

	@Test
	void theWearTextOnGogglesNeverGoesBackwardsAsABatteryWears() {
		double last = -1;
		for (int i = 0; i <= 100000; i++) {
			double shown = Double.parseDouble(BatteryWear.percentText(i / 100000.0));
			assertTrue(shown >= last, "text went from " + last + " to " + shown + " at " + i);
			last = shown;
		}
		assertEquals(100, last, 0);
	}

	@Test
	void theTextAndTheStepClientsAreToldAtAgree() {
		for (int i = 0; i <= 100000; i++) {
			double a = i / 100000.0, b = (i + 1) / 100000.0;
			if (b <= 1 && BatteryWear.shownStep(a) == BatteryWear.shownStep(b) && BatteryWear.shownStep(a) < 100 && a > 0)
				assertEquals(BatteryWear.percentText(a), BatteryWear.percentText(b), "same step, different words at " + a);
		}
	}

	@Test
	void theBuildWideMeanLiesBetweenItsBestAndWorstPartAndIgnoresOrder() {
		for (int i = 0; i < ROUNDS; i++) {
			double[] wears = new double[1 + random.nextInt(40)];
			double low = 1, high = 0;
			for (int w = 0; w < wears.length; w++) {
				wears[w] = in(0, 1);
				low = Math.min(low, wears[w]);
				high = Math.max(high, wears[w]);
			}
			float mean = WearTotal.mean(wears);
			assertTrue(mean >= low - 1e-6 && mean <= high + 1e-6);
			for (int w = wears.length - 1; w > 0; w--) {
				int other = random.nextInt(w + 1);
				double held = wears[w];
				wears[w] = wears[other];
				wears[other] = held;
			}
			assertEquals(mean, WearTotal.mean(wears), 1e-6f);
		}
	}

	@Test
	void anyFailureCountsPackAndReadBackHeldAt127() {
		for (int i = 0; i < ROUNDS; i++) {
			int[] counts = new int[6];
			for (int k = 0; k < 6; k++)
				counts[k] = random.nextInt(4) == 0 ? random.nextInt() : random.nextInt(200) - 20;
			long packed = WearFailures.pack(counts[0], counts[1], counts[2], counts[3], counts[4], counts[5]);
			assertTrue(packed >= 0, "a reading is never negative");
			for (WearFailures.Kind kind : WearFailures.Kind.values())
				assertEquals(Math.clamp(counts[kind.ordinal()], 0, 127), WearFailures.count(packed, kind), kind.name());
		}
	}

	@Test
	void oneMoreFailureOfAKindRisesThatKindOnly() {
		for (WearFailures.Kind more : WearFailures.Kind.values()) {
			int[] counts = {3, 3, 3, 3, 3, 3};
			long before = WearFailures.pack(counts[0], counts[1], counts[2], counts[3], counts[4], counts[5]);
			counts[more.ordinal()]++;
			long after = WearFailures.pack(counts[0], counts[1], counts[2], counts[3], counts[4], counts[5]);
			for (WearFailures.Kind kind : WearFailures.Kind.values()) {
				assertEquals(kind == more, WearFailures.rose(before, after, kind), more + " seen as " + kind);
				assertFalse(WearFailures.rose(after, before, kind), "a repair is not a rise");
				assertFalse(WearFailures.rose(WearFailures.NO_READING, after, kind));
				assertFalse(WearFailures.rose(before, WearFailures.NO_READING, kind));
			}
		}
	}

	@Test
	void aCoilOnlyEverHelpsAndWithoutOneEveryFigureIsExactlyUnchanged() {
		for (int i = 0; i < ROUNDS; i++) {
			double value = in(0, 10);
			assertEquals(value, IgnitionCoil.leanLimit(value, false), 0);
			assertEquals(value, IgnitionCoil.burn(value, false), 0);
			assertEquals(value, IgnitionCoil.fouledLoss(value, false), 0);
			assertTrue(IgnitionCoil.leanLimit(value, true) <= value);
			assertTrue(IgnitionCoil.burn(value, true) <= value);
			assertTrue(IgnitionCoil.fouledLoss(value, true) <= value);
		}
		assertFalse(IgnitionCoil.NONE.installed() || IgnitionCoil.NONE.powered() || IgnitionCoil.NONE.fire());
		assertTrue(IgnitionCoil.UNLIMITED.installed() && IgnitionCoil.UNLIMITED.powered() && IgnitionCoil.UNLIMITED.fire());
	}

	@Test
	void knockIsHeardOnlyWhereItWearsAndRisesWithTheLoss() {
		for (int i = 0; i < ROUNDS; i++) {
			double loss = in(0, 0.5), rpm = in(1, 800);
			if (Knock.audible(loss))
				assertTrue(Knock.pistonWear(loss, rpm, 1) > 0, "heard but harmless at " + loss);
			rising(Knock::intensity, loss, in(0, 0.5), "intensity");
		}
		assertTrue(Knock.AUDIBLE_FROM > Knock.FROM);
		assertTrue(Knock.AUDIBLE_FROM < FuelSpec.PREIGNITION_MAX_LOSS);
	}
}
