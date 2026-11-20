package com.createengines.engine.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Parts of a pack's or an addon's own (a third tier of cylinder, a fifth kind of part): named by id, read from their own
 * {@code part_effects} file, multiplied into the cylinder after this mod's parts. A cylinder with none is exactly the one it was.
 */
class OwnPartEffectsTest {

	private static final String RACING = "mymod:racing_bore";

	@BeforeEach
	@AfterEach
	void builtIn() {
		PartEffects.reset();
		Tuning.reset();
	}

	private static PartEffects.Effect compression(double scale) {
		return new PartEffects.Effect(scale, 1, 1, 1, 1, 1, 1, 1);
	}

	@Test
	void aCylinderWithNoPartOfItsOwnIsExactlyTheCylinderItWas() {
		for (int mask = 0; mask < 16; mask++) {
			boolean b = (mask & 1) != 0, h = (mask & 2) != 0, p = (mask & 4) != 0, i = (mask & 8) != 0;
			assertEquals(CylinderSpec.build(b, h, p, i), CylinderSpec.build(b, h, p, i, List.of()), "mask " + mask);
		}
	}

	@Test
	void aPartWithNoFileHasNoEffect() {
		assertEquals(CylinderSpec.build(false, false, false, false), CylinderSpec.build(false, false, false, false, List.of(RACING)));
		assertEquals(PartEffects.Effect.NONE, PartEffects.ofCustom(RACING));
	}

	@Test
	void aPartFromAPacksFileChangesWhatItSaysAndNothingElse() {
		PartEffects.set(Map.of(), Map.of(RACING, new PartEffects.Effect(1.5, 2, 1, 1, 1, 1, 1, 1)));
		CylinderSpec crude = CylinderSpec.build(false, false, false, false);
		CylinderSpec racing = CylinderSpec.build(false, false, false, false, List.of(RACING));
		assertEquals(crude.compressionRatio() * 1.5, racing.compressionRatio(), 1e-9);
		assertEquals(crude.intakeFlow() * 2, racing.intakeFlow(), 1e-9);
		assertEquals(crude.exhaustFlow() * 2, racing.exhaustFlow(), 1e-9);
		assertEquals(crude.ignitionDelay(), racing.ignitionDelay(), 0);
		assertEquals(crude.inertia(), racing.inertia(), 0);
	}

	@Test
	void itAddsToTheBuiltInPartsAndDoesNotReplaceThem() {
		PartEffects.set(Map.of(), Map.of(RACING, compression(2)));
		CylinderSpec precision = CylinderSpec.build(true, false, false, false);
		CylinderSpec both = CylinderSpec.build(true, false, false, false, List.of(RACING));
		assertEquals(precision.compressionRatio() * 2, both.compressionRatio(), 1e-9);
		assertEquals(precision.frictionStatic(), both.frictionStatic(), 0);
	}

	@Test
	void theServersPrecisionBonusBlendsItAsItBlendsTheBuiltInParts() {
		PartEffects.set(Map.of(), Map.of(RACING, new PartEffects.Effect(1, 1, 1, 1, 1, 1, 1, 2)));
		Tuning.precisionBonus = 0.5;
		assertEquals(1.5, CylinderSpec.build(false, false, false, false, List.of(RACING)).injectorFlow(), 1e-12);
		Tuning.precisionBonus = 0;
		assertEquals(1, CylinderSpec.build(false, false, false, false, List.of(RACING)).injectorFlow(), 1e-12);
	}

	@Test
	void aReloadWithoutItsFileTakesTheEffectAwayAndAResetClearsEverything() {
		PartEffects.set(Map.of(), Map.of(RACING, compression(2)));
		PartEffects.set(Map.of());
		assertEquals(PartEffects.Effect.NONE, PartEffects.ofCustom(RACING));
		PartEffects.set(Map.of(), Map.of(RACING, compression(2)));
		PartEffects.reset();
		assertEquals(PartEffects.Effect.NONE, PartEffects.ofCustom(RACING));
	}

	@Test
	void manyPartsOfTheirOwnStackWithoutLeavingTheSoundRange() {
		// Every scale is range-checked alone, and they multiply: the floor under the compression ratio still holds
		PartEffects.set(Map.of(), Map.of(RACING, compression(PartEffects.Effect.MIN), "mymod:b", compression(PartEffects.Effect.MIN), "mymod:c",
			compression(PartEffects.Effect.MIN)));
		CylinderSpec spec = CylinderSpec.build(false, false, false, false, List.of(RACING, "mymod:b", "mymod:c"));
		assertTrue(spec.compressionRatio() >= PartStats.MIN_COMPRESSION && Double.isFinite(spec.compressionRatio()));
	}
}
