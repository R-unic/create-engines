package com.createengines.gametest;

import java.util.List;
import java.util.Map;

import com.createengines.CreateEngines;
import com.createengines.content.crankshaft.CrankshaftBlockEntity;
import com.createengines.engine.EngineSummary;
import com.createengines.engine.sim.PartEffects;
import com.createengines.registry.CEBlocks;
import com.createengines.registry.CEDataMaps;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Parts of a pack's or an addon's own, in a real engine. A GameTest cannot add an entry to the {@code part_effect} data map, so
 * these answer for it through {@link CEDataMaps#partEffectOverride} and undo it before the test ends, whatever happened.
 */
@GameTestHolder(CreateEngines.MOD_ID)
@PrefixGameTestTemplate(false)
public class OwnPartGameTests {

	/** Alone in its batch, so that no other test builds an engine while the data map is being answered for. */
	private static final String BATCH = "own-parts";
	private static final String RACING = "test:racing_bore";
	private static final BlockPos SECOND = new BlockPos(14, 0, 14);

	private static CrankshaftBlockEntity crank(GameTestHelper helper, BlockPos offset) {
		return (CrankshaftBlockEntity) helper.getLevel()
			.getBlockEntity(helper.absolutePos(offset.offset(TestEngine.PRIMARY)));
	}

	private static void undo() {
		CEDataMaps.partEffectOverride = null;
		PartEffects.reset();
	}

	/**
	 * Flywheels of a pack's or an addon's own (API 1.2) are only ever a block made by {@code PartBlocks.flywheel}, and no block
	 * of this mod is one, so no engine built before 2.1 has a flywheel that the data map could change. The three flywheels of
	 * this mod keep reading their masses from the server settings, and a data flywheel with no entry adds nothing.
	 */
	@GameTest(template = "empty", batch = BATCH)
	public static void noBlockOfThisModIsAFlywheelOfADataMapEntry(GameTestHelper helper) {
		for (var entry : net.minecraft.core.registries.BuiltInRegistries.BLOCK.entrySet()) {
			if (!entry.getKey()
				.location()
				.getNamespace()
				.equals(CreateEngines.MOD_ID))
				continue;
			var block = entry.getValue();
			String id = entry.getKey()
				.location()
				.toString();
			if (block instanceof com.createengines.content.flywheel.DataFlywheelBlock)
				helper.fail(id + " is a data flywheel; none of this mod's blocks may be, or a pack could change a built engine");
			if (com.createengines.registry.CEBlockEntities.DATA_FLYWHEEL.get()
				.isValid(block.defaultBlockState()))
				helper.fail(id + " is valid for the data flywheel block entity, which is for an addon's blocks only");
			if (!com.createengines.registry.CEDataMaps.flywheel(block)
				.equals(com.createengines.engine.FlywheelSpec.NONE))
				helper.fail(id + " names masses in the flywheel data map, which would change an engine built before 2.1");
		}
		helper.succeed();
	}

	@GameTest(template = "empty_large", timeoutTicks = 200, batch = BATCH)
	public static void aBlockThatNamesAPartEffectAddsItToTheCylinderItIsPartOf(GameTestHelper helper) {
		// No block of this mod names one of its own: that would change an engine built before 2.0
		for (var entry : net.minecraft.core.registries.BuiltInRegistries.BLOCK.entrySet())
			if (entry.getKey()
				.location()
				.getNamespace()
				.equals(CreateEngines.MOD_ID) && CEDataMaps.partEffect(entry.getValue()) != null)
				helper.fail(entry.getKey()
					.location() + " names a part effect, which would change an engine that was built before 2.0");
		BlockPos first = BlockPos.ZERO;
		TestEngine.build(helper.getLevel(), helper.absolutePos(first), true);
		helper.runAtTickTime(20, () -> {
			EngineSummary plain = crank(helper, first).summary();
			if (plain == null || crank(helper, first).controller() == null) {
				helper.fail("the first engine was not built");
				return;
			}
			if (!crank(helper, first).controller()
				.layout()
				.cylinders()
				.get(0)
				.effects()
				.isEmpty())
				helper.fail("a cylinder of this mod's own blocks names no part of its own");
			// The cylinder block and the head both name it: it counts once. Its effect is a heavy piston, which no part has.
			PartEffects.set(Map.of(), Map.of(RACING, new PartEffects.Effect(1, 1, 1, 1, 1, 1, 3, 1)));
			CEDataMaps.partEffectOverride = block -> block == CEBlocks.CYLINDER.get() || block == CEBlocks.CYLINDER_HEAD.get() ? RACING : null;
			TestEngine.build(helper.getLevel(), helper.absolutePos(SECOND), true);
			helper.runAtTickTime(60, () -> {
				try {
					CrankshaftBlockEntity second = crank(helper, SECOND);
					if (second.controller() == null || second.summary() == null) {
						helper.fail("the second engine was not built");
						return;
					}
					List<String> effects = second.controller()
						.layout()
						.cylinders()
						.get(0)
						.effects();
					if (!effects.equals(List.of(RACING)))
						helper.fail("the cylinder should carry the part once, not " + effects);
					if (!(second.summary()
						.inertia() > plain.inertia()))
						helper.fail("a part that triples the piston's inertia changed nothing: " + second.summary()
							.inertia() + " against " + plain.inertia());
				} finally {
					undo();
				}
				helper.succeed();
			});
		});
		// If the test times out or fails before the inner callback runs, nothing may stay changed for the tests after it
		helper.runAtTickTime(190, OwnPartGameTests::undo);
	}
}
