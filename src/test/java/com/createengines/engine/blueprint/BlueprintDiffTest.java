package com.createengines.engine.blueprint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.createengines.engine.blueprint.BlueprintDiff.DialChange;
import com.createengines.engine.blueprint.BlueprintDiff.Kind;
import com.createengines.engine.blueprint.BlueprintDiff.Mismatch;

/** Same build or not, and which dials differ: what decides whether applying a blueprint changes anything. */
class BlueprintDiffTest {

	private static Blueprint.Part part(int x, int y, int z, String block, Map<String, String> state, Map<String, Integer> dials) {
		return new Blueprint.Part(x, y, z, block, state, dials);
	}

	private static Blueprint engine(int metering, int angle) {
		return new Blueprint("e", "", List.of(
			part(0, 0, 0, "create_engines:crankshaft", Map.of("axis", "x"), Map.of()),
			part(1, 0, 0, "create_engines:crank_throw", Map.of("axis", "x"), Map.of("throw_angle", angle)),
			part(1, 1, 0, "create_engines:cylinder", Map.of("piston", "true"), Map.of()),
			part(1, 2, 0, "create_engines:cylinder_head", Map.of("injector", "true"), Map.of("fuel_metering", metering))));
	}

	@Test
	void theSameBuildHasNoDifferencesWhateverTheDials() {
		assertTrue(BlueprintDiff.compare(engine(10, 0), engine(20, 6), 10).isEmpty());
		assertTrue(BlueprintDiff.sameBuild(engine(10, 0), engine(20, 6)));
	}

	@Test
	void aMissingBlockAnExtraBlockAndAChangedOneAreNamed() {
		Blueprint wanted = engine(10, 0);
		List<Blueprint.Part> parts = new ArrayList<>(wanted.parts());
		parts.remove(2);
		parts.add(part(1, 1, 0, "create_engines:precision_cylinder", Map.of("piston", "true"), Map.of()));
		parts.add(part(5, 5, 5, "create_engines:throttle", Map.of(), Map.of()));
		Blueprint actual = new Blueprint("a", "", parts);
		List<Mismatch> found = BlueprintDiff.compare(wanted, actual, 10);
		assertEquals(2, found.size());
		assertEquals(Kind.BLOCK, found.get(0).kind());
		assertEquals("create_engines:cylinder", found.get(0).block());
		assertEquals("create_engines:precision_cylinder", found.get(0).detail());
		assertEquals(Kind.EXTRA, found.get(1).kind());
		assertEquals(5, found.get(1).x());
	}

	@Test
	void aBlockThatIsNotThereIsMissing() {
		Blueprint actual = new Blueprint("a", "", engine(10, 0).parts().subList(0, 3));
		List<Mismatch> found = BlueprintDiff.compare(engine(10, 0), actual, 10);
		assertEquals(1, found.size());
		assertEquals(Kind.MISSING, found.get(0).kind());
		assertEquals("create_engines:cylinder_head", found.get(0).block());
		assertEquals(2, found.get(0).y());
	}

	@Test
	void aStateThatDiffersSaysWhichPropertyAndHow() {
		Blueprint actual = new Blueprint("a", "", List.of(
			part(0, 0, 0, "create_engines:crankshaft", Map.of("axis", "z"), Map.of()),
			part(1, 0, 0, "create_engines:crank_throw", Map.of("axis", "x"), Map.of()),
			part(1, 1, 0, "create_engines:cylinder", Map.of(), Map.of()),
			part(1, 2, 0, "create_engines:cylinder_head", Map.of("injector", "true"), Map.of())));
		List<Mismatch> found = BlueprintDiff.compare(engine(10, 0), actual, 10);
		assertEquals(2, found.size());
		assertEquals(Kind.STATE, found.get(0).kind());
		assertEquals("axis z -> x", found.get(0).detail());
		assertEquals("piston default -> true", found.get(1).detail());
	}

	@Test
	void theListIsCutAtTheLimitInPositionOrder() {
		List<Blueprint.Part> many = new ArrayList<>();
		for (int i = 0; i < 20; i++)
			many.add(part(i, 0, 0, "create_engines:cylinder", Map.of(), Map.of()));
		Blueprint wanted = new Blueprint("w", "", many);
		Blueprint actual = new Blueprint("a", "", List.of(part(100, 0, 0, "create_engines:cylinder", Map.of(), Map.of())));
		List<Mismatch> found = BlueprintDiff.compare(wanted, actual, 5);
		assertEquals(5, found.size());
		for (int i = 0; i < 5; i++)
			assertEquals(i, found.get(i).x());
		assertFalse(BlueprintDiff.sameBuild(wanted, actual));
	}

	@Test
	void onlyDialsThatDifferAreChangesAndTheyCarryBothValues() {
		List<DialChange> changes = BlueprintDiff.dialChanges(engine(14, 6), engine(14, 0));
		assertEquals(1, changes.size());
		assertEquals(new DialChange(1, 0, 0, "create_engines:crank_throw", "throw_angle", 0, 6), changes.get(0));
		assertTrue(BlueprintDiff.dialChanges(engine(14, 6), engine(14, 6)).isEmpty());
	}

	@Test
	void changesComeInPositionOrderThenDialName() {
		List<DialChange> changes = BlueprintDiff.dialChanges(engine(20, 6), engine(10, 0));
		assertEquals(2, changes.size());
		assertEquals("throw_angle", changes.get(0).dial());
		assertEquals("fuel_metering", changes.get(1).dial());
	}

	@Test
	void aDialTheEngineBlockLacksIsLeftAloneAndAMissingBlockIsSkipped() {
		Blueprint wanted = new Blueprint("w", "", List.of(
			part(0, 0, 0, "create_engines:cylinder", Map.of(), Map.of("fuel_metering", 9)),
			part(3, 0, 0, "create_engines:cylinder_head", Map.of(), Map.of("fuel_metering", 9))));
		Blueprint actual = new Blueprint("a", "", List.of(part(0, 0, 0, "create_engines:cylinder", Map.of(), Map.of())));
		assertTrue(BlueprintDiff.dialChanges(wanted, actual).isEmpty());
	}

	@Test
	void anEmptyEngineIsEverythingMissing() {
		Blueprint nothing = new Blueprint("n", "", List.of());
		assertEquals(4, BlueprintDiff.compare(engine(10, 0), nothing, 10).size());
		assertEquals(4, BlueprintDiff.compare(nothing, engine(10, 0), 10).size());
	}
}
