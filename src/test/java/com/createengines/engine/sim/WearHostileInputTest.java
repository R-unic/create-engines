package com.createengines.engine.sim;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Every wear curve fed every awkward number in every argument at once: not a number, both infinities, negatives, zero, the smallest and
 * very large values. Whatever goes in, wear and failure chances stay finite and within 0 to 1, and the figures a cylinder reads from them
 * stay usable. A NaN here would end up saved on a part.
 */
class WearHostileInputTest {

	/** The awkward values, plus a few ordinary ones so that every other argument is sometimes sensible. */
	private static final double[] VALUES = {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -1e12, -1, -0.0, 0,
		Double.MIN_VALUE, 1e-12, 0.5, 1, 128, 1e12};

	private static void unit(double value, String what, double... in) {
		assertTrue(value >= 0 && value <= 1, () -> what + " gave " + value + " for " + java.util.Arrays.toString(in));
	}

	@Test
	void pistonWearIsAlwaysAShare() {
		for (double rpm : VALUES)
			for (double heat : VALUES)
				for (double film : VALUES)
					for (double dt : VALUES) {
						unit(Wear.pistonWear(rpm, heat, film, dt), "pistonWear", rpm, heat, film, dt);
						unit(Wear.pistonWear(rpm, heat, film, dt, true), "forged pistonWear", rpm, heat, film, dt);
					}
	}

	@Test
	void rodWearIsAlwaysAShare() {
		for (double rpm : VALUES)
			for (double film : VALUES)
				for (double dt : VALUES)
					unit(Wear.rodWear(rpm, film, dt), "rodWear", rpm, film, dt);
	}

	@Test
	void gasketWearIsAlwaysAShareForEveryTier() {
		for (Wear.Gasket tier : Wear.Gasket.values())
			for (double rpm : VALUES)
				for (double heat : VALUES)
					for (double pressure : VALUES)
						for (double dt : VALUES)
							unit(Wear.gasketWear(tier, rpm, heat, pressure, dt), "gasketWear " + tier, rpm, heat, pressure, dt);
	}

	@Test
	void dryTimeNeverLeavesZeroToTheSeizingPoint() {
		for (double before : VALUES)
			for (double film : VALUES)
				for (double rpm : VALUES)
					for (double dt : VALUES)
						for (boolean forged : new boolean[] {false, true}) {
							double after = Wear.dryTime(before, film, rpm, dt, forged);
							assertTrue(after >= 0 && after <= Wear.SEIZE_AFTER,
								() -> "dryTime " + after + " for " + before + " " + film + " " + rpm + " " + dt + " " + forged);
						}
	}

	@Test
	void knockWearIsAlwaysAShare() {
		for (double loss : VALUES)
			for (double rpm : VALUES)
				for (double dt : VALUES) {
					unit(Knock.pistonWear(loss, rpm, dt), "knock", loss, rpm, dt);
					unit(Knock.pistonWear(loss, rpm, dt, true), "forged knock", loss, rpm, dt);
				}
		for (double loss : VALUES)
			unit(Knock.intensity(loss), "knock intensity", loss);
	}

	@Test
	void plugWearAndWhatAWornPlugDoesStayUsable() {
		for (double rpm : VALUES)
			for (double soot : VALUES)
				for (double dt : VALUES)
					for (boolean coil : new boolean[] {false, true})
						unit(PlugWear.wear(rpm, coil, soot, dt), "plug wear", rpm, soot, dt);
		for (double wear : VALUES) {
			unit(PlugWear.misfireChance(wear), "misfire", wear);
			double lean = PlugWear.leanScale(wear);
			assertTrue(lean >= 1 && lean <= PlugWear.LEAN_LOSS, () -> "leanScale " + lean + " for " + wear);
		}
	}

	@Test
	void turboWearAndFailureStayShares() {
		for (double boost : VALUES)
			for (double heat : VALUES)
				for (double film : VALUES)
					for (double dt : VALUES)
						unit(TurboWear.wear(boost, heat, film, dt), "turbo wear", boost, heat, film, dt);
		for (double wear : VALUES) {
			for (double boost : VALUES)
				for (double dt : VALUES)
					unit(TurboWear.failureChance(wear, boost, dt), "turbo failure", wear, boost, dt);
			double share = TurboWear.boostShare(wear);
			assertTrue(share >= 1 - TurboWear.MAX_BOOST_LOSS - 1e-12 && share <= 1, () -> "boostShare " + share + " for " + wear);
		}
	}

	@Test
	void apexSealWearAndFailureStayShares() {
		for (double rpm : VALUES)
			for (double heat : VALUES)
				for (double film : VALUES)
					for (double dt : VALUES)
						unit(ApexSeals.wear(rpm, heat, film, dt), "apex wear", rpm, heat, film, dt);
		for (double wear : VALUES) {
			for (double rpm : VALUES)
				for (double dt : VALUES)
					unit(ApexSeals.failureChance(wear, rpm, dt), "apex failure", wear, rpm, dt);
			unit(ApexSeals.seal(wear), "apex seal", wear);
			unit(ApexSeals.state(wear)
				.seal(), "apex state seal", wear);
		}
	}

	@Test
	void pistonRodAndGasketFailureChancesStayShares() {
		for (double wear : VALUES)
			for (double rpm : VALUES)
				for (double dt : VALUES)
					unit(Wear.failureChance(wear, rpm, dt), "failure", wear, rpm, dt);
	}

	@Test
	void whatACylinderReadsFromAnySavedWearIsUsable() {
		List<String> bad = new ArrayList<>();
		for (Wear.Gasket tier : Wear.Gasket.values())
			for (double piston : VALUES)
				for (double gasket : VALUES)
					for (boolean fitted : new boolean[] {false, true}) {
						Wear.State state = Wear.of(piston, tier, gasket, fitted);
						// A fitted gasket may seal a little better than none, and never by more than its own bonus
						if (!(state.seal() > 0 && state.seal() <= 1 + tier.bonus + 1e-12))
							bad.add("seal " + state.seal() + " " + tier + " " + piston + " " + gasket + " " + fitted);
						if (!(state.drag() >= 1 && state.drag() <= 1 + Wear.PISTON_DRAG + 1e-12))
							bad.add("drag " + state.drag() + " " + piston);
					}
		assertTrue(bad.isEmpty(), () -> bad.size() + " bad states, first " + bad.getFirst());
	}

	@Test
	void batteryWearNeverRaisesCapacityAboveNewOrBelowTheFloor() {
		for (BatteryTier tier : BatteryTier.values())
			for (double wear : VALUES) {
				float capacity = BatteryWear.capacity(tier, wear);
				assertTrue(capacity <= tier.capacity() + 1e-3 && capacity >= tier.capacity() * (1 - BatteryWear.MAX_FADE) - 1e-3,
					() -> tier + " holds " + capacity + " at wear " + wear);
			}
	}

	@Test
	void batteryDrawWearIsAlwaysAShare() {
		for (BatteryTier tier : BatteryTier.values())
			for (double drawn : VALUES)
				for (double share : VALUES)
					for (double cold : VALUES)
						unit(BatteryWear.drawWear(tier, drawn, share, cold), "battery draw " + tier, drawn, share, cold);
	}

	@Test
	void repairingAnySavedWearNeverMakesItWorse() {
		for (double wear : VALUES) {
			double before = Wear.clamp(wear);
			double after = Wear.repaired(wear);
			assertTrue(after >= 0 && after <= before, () -> "repair took " + wear + " to " + after);
		}
	}

	@Test
	void theBuildWideFigureIsAShareOrNoneWhateverItIsMadeOf() {
		for (double a : VALUES)
			for (double b : VALUES)
				for (double c : VALUES)
					unit(WearTotal.mean(a, b, c), "mean", a, b, c);
		for (double value : VALUES) {
			int percent = WearTotal.percent((float) value);
			assertTrue(percent >= -1 && percent <= 100, () -> "percent " + percent + " for " + value);
		}
	}
}
