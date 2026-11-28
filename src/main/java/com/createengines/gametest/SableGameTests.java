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

	/**
	 * A whole engine in a structure sinks and dries with the world around it. The water is put in the world, never in the plot,
	 * and follows the structure (it may be falling), so only the engine's own re-reading of the world can notice it: the check
	 * every 20 ticks that asks for a rescan when what is wet changes.
	 */
	@GameTest(template = "empty_large", timeoutTicks = 600)
	public static void aWholeEngineOnAStructureSinksAndDriesWithTheWorldAroundIt(GameTestHelper helper) {
		if (!SableCompat.installed()) {
			helper.succeed();
			return;
		}
		var level = helper.getLevel();
		BlockPos origin = helper.absolutePos(BlockPos.ZERO);
		TestEngine.build(level, origin, true);
		SableTestSupport.Held held = SableTestSupport.assemble(level, origin, origin.offset(7, 5, 5), 200);
		BlockPos crankPos = SableTestSupport.find(level, held, com.createengines.registry.CEBlocks.CRANKSHAFT.get());
		BlockPos cylinderPos = SableTestSupport.find(level, held, com.createengines.registry.CEBlocks.CYLINDER.get());
		if (crankPos == null || cylinderPos == null) {
			helper.fail("the engine did not move into the structure: " + SableTestSupport.contents(level, held));
			return;
		}
		boolean[] flooding = {false};
		EveryTick.run(helper, () -> {
			// The water goes round wherever the structure is now, so a falling structure stays in it
			if (flooding[0])
				fillAround(level, SableCompat.worldBlockPos(level, cylinderPos), 4, net.minecraft.world.level.block.Blocks.WATER);
		});
		helper.runAtTickTime(60, () -> {
			var crank = (com.createengines.content.crankshaft.CrankshaftBlockEntity) level.getBlockEntity(crankPos);
			if (crank == null || crank.controller() == null) {
				helper.fail("the engine on the structure has no controller, so it was never built there: " + SableTestSupport.contents(level, held));
				return;
			}
			if (crank.controller()
				.layout()
				.cylinders()
				.size() != 1)
				helper.fail("the engine on the structure should have one cylinder: " + crank.controller()
					.layout()
					.cylinders());
			if (com.createengines.engine.topology.CylinderFault.SUBMERGED.in(crank.controller()
				.layout()
				.cylinders()
				.get(0)
				.faults()))
				helper.fail("an engine in dry air reads as underwater");
			flooding[0] = true;
		});
		helper.runAtTickTime(200, () -> {
			var crank = (com.createengines.content.crankshaft.CrankshaftBlockEntity) level.getBlockEntity(crankPos);
			if (!com.createengines.engine.topology.CylinderFault.SUBMERGED.in(crank.controller()
				.layout()
				.cylinders()
				.get(0)
				.faults()))
				helper.fail("the structure is in water in the world and the engine on it never noticed: " + crank.controller()
					.layout()
					.cylinders()
					.get(0));
			flooding[0] = false;
			// Drain generously: the structure has been moving, and the water is round every cell it has been in
			BlockPos now = SableCompat.worldBlockPos(level, cylinderPos);
			for (int dy = -40; dy <= 8; dy++)
				fillAround(level, now.offset(0, dy, 0), 8, net.minecraft.world.level.block.Blocks.AIR);
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(helper.getTick() > 260, "Not drained yet");
			var crank = (com.createengines.content.crankshaft.CrankshaftBlockEntity) level.getBlockEntity(crankPos);
			helper.assertFalse(com.createengines.engine.topology.CylinderFault.SUBMERGED.in(crank.controller()
				.layout()
				.cylinders()
				.get(0)
				.faults()), "the water is gone and the engine on the structure still reads as underwater");
		});
	}

	private static void fillAround(net.minecraft.server.level.ServerLevel level, BlockPos centre, int radius, net.minecraft.world.level.block.Block block) {
		for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-radius, -radius, -radius), centre.offset(radius, radius, radius)))
			if (level.isLoaded(pos) && level.getBlockState(pos)
				.getBlock() != block)
				level.setBlock(pos, block.defaultBlockState(), 2);
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
		check(helper, pos, com.createengines.registry.CEBlocks.DUAL_MASS_FLYWHEEL.getDefaultState(), 4.0);
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

	/**
	 * With Sable the air is Sable's own pressure, which this mod reaches by name: if that lookup ever stopped working the engine
	 * would quietly use the height curve, so this asks for a real number and for the pressure to fall with height.
	 */
	@GameTest(template = "empty", timeoutTicks = 200)
	public static void theAirPressureIsReadFromSableAndFallsWithHeight(GameTestHelper helper) {
		if (!SableCompat.installed()) {
			helper.succeed();
			return;
		}
		BlockPos ground = helper.absolutePos(new BlockPos(1, 1, 1));
		double low = SableCompat.airPressure(helper.getLevel(), ground);
		double high = SableCompat.airPressure(helper.getLevel(), new BlockPos(ground.getX(), 2000, ground.getZ()));
		if (Double.isNaN(low) || Double.isNaN(high))
			helper.fail("Sable's air pressure could not be read: " + low + " at the ground, " + high + " at 2000");
		if (low <= 0 || high < 0)
			helper.fail("pressure cannot be zero at the bottom of the world or negative anywhere, got " + low + " and " + high);
		if (!(high < low))
			helper.fail("the air should be thinner at 2000 (" + high + ") than at the ground (" + low + ")");
		helper.succeed();
	}

	/**
	 * Parts on a structure are posed only while in view, and Flywheel tests the part's position in the plot, which is nowhere near
	 * the camera: the pistons froze. The test must say yes for a part whose structure the camera is looking at, and no for one it
	 * is looking away from, whatever Flywheel's own answer.
	 */
	@GameTest(template = "empty", timeoutTicks = 200)
	public static void aPartOnAStructureIsInViewWhereTheStructureIsNotWhereItsPlotIs(GameTestHelper helper) {
		if (!SableCompat.installed()) {
			helper.succeed();
			return;
		}
		BlockPos origin = helper.absolutePos(BlockPos.ZERO);
		TestEngine.build(helper.getLevel(), origin, true);
		SableTestSupport.Held held = SableTestSupport.assemble(helper.getLevel(), origin, origin.offset(7, 5, 5), 2000);
		net.minecraft.world.phys.Vec3 at = SableCompat.worldPosition(helper.getLevel(), held.inPlot());
		// Flywheel draws relative to a render origin near the camera, so the matrices are small numbers, not millions. A part on a
		// structure is in an embedding whose own origin is the plot, so its visual position is no help: the main origin is what counts
		BlockPos plot = held.inPlot();
		BlockPos renderOrigin = BlockPos.containing(at);
		float x = (float) (at.x - renderOrigin.getX());
		float y = (float) (at.y - renderOrigin.getY());
		float z = (float) (at.z - renderOrigin.getZ());
		org.joml.Matrix4f view = new org.joml.Matrix4f().lookAt(x, y, z - 10, x, y, z, 0, 1, 0);
		org.joml.Matrix4f away = new org.joml.Matrix4f().lookAt(x, y, z - 10, x, y, z - 20, 0, 1, 0);
		org.joml.Matrix4f projection = new org.joml.Matrix4f().perspective((float) Math.toRadians(70), 1.6f, 0.05f, 256f);
		org.joml.FrustumIntersection looking = new org.joml.FrustumIntersection(new org.joml.Matrix4f(projection).mul(view));
		org.joml.FrustumIntersection turned = new org.joml.FrustumIntersection(new org.joml.Matrix4f(projection).mul(away));
		if (!com.createengines.content.StructureCulling.visible(helper.getLevel(), plot, renderOrigin, looking, false))
			helper.fail("a part on a structure in front of the camera was called out of view because its plot is far away");
		// 1.4 posed every part every frame; a part on a structure is never skipped, even looking away and whatever Flywheel says
		if (!com.createengines.content.StructureCulling.visible(helper.getLevel(), plot, renderOrigin, turned, false))
			helper.fail("a part on a structure was skipped; parts on structures are posed every frame");
		BlockPos ground = helper.absolutePos(new BlockPos(1, 1, 1));
		if (!com.createengines.content.StructureCulling.visible(helper.getLevel(), ground, renderOrigin, turned, true)
			|| com.createengines.content.StructureCulling.visible(helper.getLevel(), ground, renderOrigin, looking, false))
			helper.fail("off a structure, Flywheel's own answer must stand");
		helper.succeed();
	}
}
