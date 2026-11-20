package com.createengines.gametest;

import com.createengines.CreateEngines;
import com.createengines.compat.sable.SableCompat;
import com.createengines.engine.sim.Altitude;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Engines on a moving structure (Create Aeronautics, through Sable). They run only when Sable is in the run
 * ({@code runGameTestServerSable}); without it each test passes at once, as there is nothing to check.
 */
@GameTestHolder(CreateEngines.MOD_ID)
@PrefixGameTestTemplate(false)
public class SableGameTests {

	@GameTest(template = "empty", timeoutTicks = 200)
	public static void aBlockOnAMovingStructureIsAskedForItsHeightInTheWorldNotItsPlots(GameTestHelper helper) {
		if (!SableCompat.installed()) {
			helper.succeed();
			return;
		}
		BlockPos origin = helper.absolutePos(BlockPos.ZERO);
		TestEngine.build(helper.getLevel(), origin, true);
		SableTestSupport.Held held = SableTestSupport.assemble(helper.getLevel(), origin, origin.offset(7, 5, 5), 2000);
		double y = SableCompat.worldY(helper.getLevel(), held.inPlot());
		if (Math.abs(y - held.structureY()) > 64)
			helper.fail("a block in a structure at y 2000 was reported at " + y + " (its plot height is " + held.inPlot()
				.getY() + ")");
		if (Altitude.density(y) > 0.3)
			helper.fail("at y 2000 the air is thin, got density " + Altitude.density(y));
		helper.succeed();
	}

	/** What the world around a structure is asked for an engine on it is the world where it is, not the plot it is kept in. */
	@GameTest(template = "empty", timeoutTicks = 200)
	public static void theWorldAroundAnEngineOnAStructureIsTheWorldWhereTheStructureIs(GameTestHelper helper) {
		if (!SableCompat.installed()) {
			helper.succeed();
			return;
		}
		BlockPos origin = helper.absolutePos(BlockPos.ZERO);
		TestEngine.build(helper.getLevel(), origin, true);
		SableTestSupport.Held held = SableTestSupport.assemble(helper.getLevel(), origin, origin.offset(7, 5, 5), 300);
		BlockPos cell = SableCompat.worldBlockPos(helper.getLevel(), held.inPlot());
		if (Math.abs(cell.getY() - 300) > 8)
			helper.fail("a block of a structure at y 300 is in the world at " + cell + ", and its plot is at " + held.inPlot());
		if (cell.equals(held.inPlot()))
			helper.fail("the world cell of a block on a structure is its plot cell");
		BlockPos ground = helper.absolutePos(new BlockPos(1, 1, 1));
		if (!SableCompat.worldBlockPos(helper.getLevel(), ground)
			.equals(ground))
			helper.fail("a block that is not on a structure must stay where it is");
		helper.succeed();
	}

	/**
	 * A structure in the sea: the plot has no water, so the check for one reads the world cells the structure is in. Water put
	 * round the structure's cell in the world shows to a block of the structure, and gone it does not.
	 */
	@GameTest(template = "empty", timeoutTicks = 200)
	public static void aBlockOnAStructureSeesTheWaterOfTheWorldItIsIn(GameTestHelper helper) {
		if (!SableCompat.installed()) {
			helper.succeed();
			return;
		}
		BlockPos origin = helper.absolutePos(new BlockPos(1, 1, 1));
		helper.getLevel()
			.setBlock(origin, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3);
		SableTestSupport.Held held = SableTestSupport.assemble(helper.getLevel(), origin, origin, 100);
		var level = helper.getLevel();
		BlockPos cell = SableCompat.worldBlockPos(level, held.inPlot());
		if (SableCompat.wetInWorld(level, held.inPlot()) || SableCompat.fluidInWorldAt(level, held.inPlot()))
			helper.fail("a structure in dry air reads as wet");
		for (net.minecraft.core.Direction side : net.minecraft.core.Direction.values())
			level.setBlock(cell.relative(side), net.minecraft.world.level.block.Blocks.WATER.defaultBlockState(), 3);
		boolean seen = SableCompat.wetInWorld(level, held.inPlot());
		level.setBlock(cell, net.minecraft.world.level.block.Blocks.WATER.defaultBlockState(), 3);
		boolean inside = SableCompat.fluidInWorldAt(level, held.inPlot());
		for (net.minecraft.core.Direction side : net.minecraft.core.Direction.values())
			level.setBlock(cell.relative(side), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
		level.setBlock(cell, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
		if (!seen)
			helper.fail("water all round a structure's cell in the world is not seen by a block of the structure");
		if (!inside)
			helper.fail("water in a structure's own cell in the world is not seen");
		if (SableCompat.wetInWorld(level, held.inPlot()) || SableCompat.fluidInWorldAt(level, held.inPlot()))
			helper.fail("the water is gone and the structure still reads as wet");
		helper.succeed();
	}

	/** The weights ship as data for Sable; the weight an engine block really gets is what the files say. */
	@GameTest(template = "empty")
	public static void engineBlocksWeighWhatTheirDataFilesSay(GameTestHelper helper) {
		if (!SableCompat.installed()) {
			helper.succeed();
			return;
		}
		BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
		check(helper, pos, com.createengines.registry.CEBlocks.CYLINDER.getDefaultState(), 2.0);
		check(helper, pos, com.createengines.registry.CEBlocks.CRANKSHAFT.getDefaultState(), 2.0);
		check(helper, pos, com.createengines.registry.CEBlocks.TURBOCHARGER.getDefaultState(), 2.0);
		check(helper, pos, com.createengines.registry.CEBlocks.ROTOR_HOUSING.getDefaultState(), 1.5);
		check(helper, pos, com.createengines.registry.CEBlocks.EXHAUST_MANIFOLD.getDefaultState(), 0.5);
		check(helper, pos, com.createengines.registry.CEBlocks.INTAKE_MANIFOLD.getDefaultState(), 0.5);
		check(helper, pos, com.createengines.registry.CEBlocks.CAST_FLYWHEEL.getDefaultState(), 4.0);
		helper.succeed();
	}

	private static void check(GameTestHelper helper, BlockPos pos, net.minecraft.world.level.block.state.BlockState state, double expected) {
		double mass = SableTestSupport.mass(helper.getLevel(), pos, state);
		if (Math.abs(mass - expected) > 1e-9)
			helper.fail(state.getBlock() + " weighs " + mass + " on a structure, the data says " + expected);
	}

	@GameTest(template = "empty")
	public static void aBlockOnTheGroundIsWhereItIs(GameTestHelper helper) {
		if (!SableCompat.installed()) {
			helper.succeed();
			return;
		}
		BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
		if (Math.abs(SableCompat.worldY(helper.getLevel(), pos) - (pos.getY() + 0.5)) > 1e-6)
			helper.fail("a block that is not on a moving structure must keep its own height");
		helper.succeed();
	}
}
