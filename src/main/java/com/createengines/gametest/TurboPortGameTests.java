package com.createengines.gametest;

import com.createengines.CreateEngines;
import com.createengines.content.airflow.ManifoldBlock;
import com.createengines.content.airflow.PortOpeningBlock;
import com.createengines.content.turbo.TurbochargerBlock;
import com.createengines.registry.CEBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Which sides of a turbocharger show a port flange. */
@GameTestHolder(CreateEngines.MOD_ID)
@PrefixGameTestTemplate(false)
public class TurboPortGameTests {

	@GameTest(template = "empty")
	public static void aTurboShowsAPortOnlyWhereSomethingJoinsItsSide(GameTestHelper helper) {
		BlockPos turbo = new BlockPos(2, 2, 2);
		helper.setBlock(turbo, CEBlocks.TURBOCHARGER.getDefaultState()
			.setValue(TurbochargerBlock.FACING, Direction.EAST));
		expect(helper, turbo, Direction.UP, false);

		// An outlet standing on it, and one beside it with its mouth towards the turbo, which does not connect
		helper.setBlock(turbo.above(), CEBlocks.EXHAUST_OUTLET.getDefaultState()
			.setValue(PortOpeningBlock.FACING, Direction.UP));
		helper.setBlock(turbo.south(), CEBlocks.EXHAUST_OUTLET.getDefaultState()
			.setValue(PortOpeningBlock.FACING, Direction.NORTH));
		expect(helper, turbo, Direction.UP, true);
		expect(helper, turbo, Direction.SOUTH, false);

		// A pipe joined to a side, then the same pipe with that end shut
		helper.setBlock(turbo.north(), CEBlocks.EXHAUST_MANIFOLD.getDefaultState()
			.setValue(ManifoldBlock.CONNECTIONS.get(Direction.SOUTH), true));
		expect(helper, turbo, Direction.NORTH, true);
		helper.setBlock(turbo.north(), CEBlocks.EXHAUST_MANIFOLD.getDefaultState());
		expect(helper, turbo, Direction.NORTH, false);

		// The ends the shaft runs out of already reach the block's face
		helper.setBlock(turbo.east(), CEBlocks.AIR_INTAKE.getDefaultState()
			.setValue(PortOpeningBlock.FACING, Direction.EAST));
		expect(helper, turbo, Direction.EAST, false);
		helper.succeed();
	}

	private static void expect(GameTestHelper helper, BlockPos turbo, Direction side, boolean joined) {
		BlockPos at = helper.absolutePos(turbo);
		boolean shown = TurbochargerBlock.portJoined(helper.getLevel(), at, helper.getLevel()
			.getBlockState(at), side);
		helper.assertTrue(shown == joined, "port on " + side + " shown: " + shown);
	}
}
