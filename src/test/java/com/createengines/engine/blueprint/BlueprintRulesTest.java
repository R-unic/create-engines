package com.createengines.engine.blueprint;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.createengines.engine.blueprint.BlueprintException.Refusal;

/** What a pasted blueprint must pass before the server builds a block from it. */
class BlueprintRulesTest {

	/** A stand-in for the game's rules: this mod's blocks, an {@code axis} property, and a metering dial of 1 to 20. */
	private static final BlueprintPolicy POLICY = new BlueprintPolicy() {
		@Override
		public boolean allowsBlock(String block) {
			return block.startsWith("create_engines:");
		}

		@Override
		public boolean allowsState(String block, String property, String value) {
			return property.equals("axis") && List.of("x", "y", "z").contains(value);
		}

		@Override
		public int dialMin(String block, String dial) {
			return dial.equals("metering") ? 1 : Integer.MAX_VALUE;
		}

		@Override
		public int dialMax(String block, String dial) {
			return dial.equals("metering") ? 20 : Integer.MIN_VALUE;
		}
	};

	private static Refusal refusal(Blueprint blueprint) {
		return assertThrows(BlueprintException.class, () -> BlueprintRules.check(blueprint, POLICY)).refusal();
	}

	private static Blueprint one(Blueprint.Part part) {
		return new Blueprint("t", "", List.of(part));
	}

	@Test
	void anOrdinaryEnginePasses() {
		Blueprint ok = new Blueprint("ok", "me", List.of(
			new Blueprint.Part(0, 0, 0, "create_engines:crankshaft", Map.of("axis", "x"), Map.of()),
			new Blueprint.Part(0, 1, 0, "create_engines:cylinder_head", Map.of(), Map.of("metering", 20))));
		assertDoesNotThrow(() -> BlueprintRules.check(ok, POLICY));
	}

	@Test
	void anotherModsBlockIsRefusedWhateverItIsCalled() {
		assertEquals(Refusal.FOREIGN_BLOCK, refusal(one(new Blueprint.Part(0, 0, 0, "minecraft:tnt"))));
		// not even shaped like an id, so it never reaches the policy
		assertEquals(Refusal.BAD_ID, refusal(one(new Blueprint.Part(0, 0, 0, "minecraft:create_engines:cylinder"))));
		assertEquals(Refusal.FOREIGN_BLOCK, refusal(one(new Blueprint.Part(0, 0, 0, "create:shaft"))));
	}

	@Test
	void aStatePropertyThatIsNotTheBlocksIsRefused() {
		assertEquals(Refusal.UNKNOWN_STATE, refusal(one(
			new Blueprint.Part(0, 0, 0, "create_engines:cylinder", Map.of("lit", "true"), Map.of()))));
		assertEquals(Refusal.UNKNOWN_STATE, refusal(one(
			new Blueprint.Part(0, 0, 0, "create_engines:cylinder", Map.of("axis", "w"), Map.of()))));
	}

	@Test
	void aDialOutsideItsRangeOrNotThereIsRefused() {
		for (int bad : new int[] { 0, -1, 21, Integer.MAX_VALUE, Integer.MIN_VALUE })
			assertEquals(Refusal.BAD_DIAL, refusal(one(
				new Blueprint.Part(0, 0, 0, "create_engines:cylinder_head", Map.of(), Map.of("metering", bad)))), "value " + bad);
		assertEquals(Refusal.BAD_DIAL, refusal(one(
			new Blueprint.Part(0, 0, 0, "create_engines:cylinder_head", Map.of(), Map.of("overdrive", 1)))));
	}

	@Test
	void theEdgesOfARangeAreInside() {
		for (int good : new int[] { 1, 20 })
			assertDoesNotThrow(() -> BlueprintRules.check(one(
				new Blueprint.Part(0, 0, 0, "create_engines:cylinder_head", Map.of(), Map.of("metering", good))), POLICY));
	}

	@Test
	void twoPartsInOneCellAreRefused() {
		assertEquals(Refusal.DUPLICATE_POSITION, refusal(new Blueprint("t", "", List.of(
			new Blueprint.Part(1, 2, 3, "create_engines:cylinder"), new Blueprint.Part(1, 2, 3, "create_engines:cylinder_head")))));
	}

	@Test
	void theSpanIsHeldOnEveryAxisAtBothEnds() {
		int span = Blueprint.Limits.MAX_SPAN;
		assertDoesNotThrow(() -> BlueprintRules.checkShape(new Blueprint("t", "", List.of(
			new Blueprint.Part(span, -span, span, "create_engines:cylinder")))));
		for (int[] at : new int[][] { { span + 1, 0, 0 }, { -span - 1, 0, 0 }, { 0, span + 1, 0 }, { 0, -span - 1, 0 },
			{ 0, 0, span + 1 }, { 0, 0, -span - 1 }, { Integer.MAX_VALUE, 0, 0 }, { 0, Integer.MIN_VALUE, 0 } })
			assertEquals(Refusal.OUT_OF_RANGE, refusal(one(new Blueprint.Part(at[0], at[1], at[2], "create_engines:cylinder"))),
				"at " + at[0] + "," + at[1] + "," + at[2]);
	}

	@Test
	void idsMustLookLikeIds() {
		for (String bad : new String[] { "", "cylinder", "create_engines:", ":cylinder", "Create_Engines:Cylinder",
			"create_engines:cyl inder", "create_engines:cylinder\n", "create_engines:" + "a".repeat(80), "create_engines:cylinder;" })
			assertEquals(Refusal.BAD_ID, refusal(one(new Blueprint.Part(0, 0, 0, bad))), "id '" + bad + "'");
		assertEquals(Refusal.BAD_ID, refusal(one(
			new Blueprint.Part(0, 0, 0, "create_engines:cylinder", Map.of("Axis", "x"), Map.of()))));
		assertEquals(Refusal.BAD_ID, refusal(one(
			new Blueprint.Part(0, 0, 0, "create_engines:cylinder", Map.of(), Map.of("me tering", 1)))));
	}

	@Test
	void namesAreShortAndPlain() {
		assertEquals(Refusal.BAD_NAME, refusal(new Blueprint("n".repeat(Blueprint.Limits.MAX_NAME + 1), "",
			List.of(new Blueprint.Part(0, 0, 0, "create_engines:cylinder")))));
		assertEquals(Refusal.BAD_NAME, refusal(new Blueprint("a\u0007b", "",
			List.of(new Blueprint.Part(0, 0, 0, "create_engines:cylinder")))));
		assertEquals(Refusal.BAD_NAME, refusal(new Blueprint("x", "line\nbreak",
			List.of(new Blueprint.Part(0, 0, 0, "create_engines:cylinder")))));
		assertDoesNotThrow(() -> BlueprintRules.checkShape(new Blueprint("n".repeat(Blueprint.Limits.MAX_NAME), "",
			List.of(new Blueprint.Part(0, 0, 0, "create_engines:cylinder")))));
	}

	@Test
	void anEmptyBlueprintIsRefused() {
		assertEquals(Refusal.EMPTY, refusal(new Blueprint("t", "", List.of())));
		assertEquals(Refusal.EMPTY, refusal(new Blueprint(null, null, null)));
	}
}
