package com.createengines.gametest;

import java.util.List;

import com.createengines.CreateEngines;
import com.createengines.content.camshaft.CamshaftBlock;
import com.createengines.content.crankshaft.CrankshaftBlockEntity;
import com.createengines.content.cylinder.CylinderBlockEntity;
import com.createengines.content.tool.BankBuilderItem;
import com.createengines.content.tool.BankBuilderItem.Layout;
import com.createengines.engine.CylinderStatus;
import com.createengines.engine.EngineState;
import com.createengines.engine.EngineSummary;
import com.createengines.registry.CEBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Redstone on a camshaft line as the way to shut an engine down. */
@GameTestHolder(CreateEngines.MOD_ID)
@PrefixGameTestTemplate(false)
public class FuelCutGameTests {

	private static CylinderStatus status(GameTestHelper helper, BlockPos at) {
		CylinderBlockEntity cylinder = helper.getBlockEntity(at);
		CylinderStatus status = cylinder.status();
		if (status == null)
			helper.fail("Cylinder has no engine status", at);
		return status;
	}

	/** A flat engine has a cam line for each bank: one signal on one block cuts every cylinder of that line and no other. */
	@GameTest(template = "empty", timeoutTicks = 400)
	public static void aSignalOnOneBlockCutsItsWholeCamLineAndNoOther(GameTestHelper helper) {
		for (int x = 1; x <= 5; x++)
			helper.setBlock(new BlockPos(x, 3, 3), (x % 2 == 0 ? CEBlocks.CRANK_THROW : CEBlocks.CRANKSHAFT).getDefaultState()
				.setValue(BlockStateProperties.AXIS, Axis.X));
		var creative = helper.makeMockPlayer(GameType.CREATIVE);
		creative.getInventory()
			.add(new ItemStack(CEBlocks.CAM_LOBE.get(), 1));
		creative.getInventory()
			.add(new ItemStack(CEBlocks.CAMSHAFT.get(), 1));
		BankBuilderItem.build(helper.getLevel(), helper.absolutePos(new BlockPos(2, 3, 3)), Direction.UP, Layout.FLAT,
			creative, true);

		BlockPos upperLine = BankBuilderItem.camOffsets(Layout.FLAT, Axis.X, Direction.UP)
			.get(0);
		// The plain camshaft over the middle journal, which touches no cylinder, and the free space beside it
		BlockPos middle = new BlockPos(3, 3, 3).offset(upperLine);
		BlockPos beside = middle.relative(Direction.UP.getClockWise(Axis.X));
		helper.assertBlockState(middle, s -> s.getBlock() instanceof CamshaftBlock, () -> "No camshaft at " + middle);

		List<BlockPos> upper = List.of(new BlockPos(2, 4, 3), new BlockPos(4, 4, 3));
		List<BlockPos> lower = List.of(new BlockPos(2, 2, 3), new BlockPos(4, 2, 3));

		helper.runAtTickTime(60, () -> {
			for (BlockPos at : upper)
				helper.assertTrue(!status(helper, at).fuelCut(), "Nothing is cut before there is a signal: " + at);
			helper.setBlock(beside, Blocks.REDSTONE_BLOCK);
		});
		helper.runAtTickTime(120, () -> {
			for (BlockPos at : upper)
				helper.assertTrue(status(helper, at).fuelCut(), "Every cylinder on the line is cut: " + at);
			for (BlockPos at : lower)
				helper.assertTrue(!status(helper, at).fuelCut(), "The other line is left alone: " + at);
			helper.setBlock(beside, Blocks.AIR);
		});
		helper.runAtTickTime(180, () -> {
			for (BlockPos at : upper)
				helper.assertTrue(!status(helper, at).fuelCut(), "Taking the signal away opens the rack again: " + at);
			helper.succeed();
		});
	}

	/** A running engine stops, with its fuel still in the head, when a full signal reaches its camshaft. */
	@GameTest(template = "empty", timeoutTicks = 1700)
	public static void aFullSignalStopsARunningEngineAndKeepsItsFuel(GameTestHelper helper) {
		TestEngine.build(helper.getLevel(), helper.absolutePos(BlockPos.ZERO), true);
		helper.runAtTickTime(300, () -> helper.setBlock(TestEngine.STARTER_POWER, Blocks.AIR));
		helper.runAtTickTime(900, () -> {
			CrankshaftBlockEntity crank = helper.getBlockEntity(TestEngine.PRIMARY);
			EngineSummary summary = crank.summary();
			helper.assertTrue(summary != null && summary.state() == EngineState.RUNNING, "Running first: " + summary);
			helper.setBlock(TestEngine.LOBE.above(), Blocks.REDSTONE_BLOCK);
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(helper.getTick() > 940, "Rack not shut yet");
			CrankshaftBlockEntity crank = helper.getBlockEntity(TestEngine.PRIMARY);
			EngineSummary summary = crank.summary();
			helper.assertTrue(status(helper, TestEngine.CYLINDER).fuelCut(), "The cylinder says its fuel is cut");
			helper.assertTrue(summary != null && summary.state() != EngineState.RUNNING && summary.firing() == 0,
				"The engine should have stopped: " + summary);
			var head = (com.createengines.content.head.CylinderHeadBlockEntity) helper.getBlockEntity(TestEngine.HEAD);
			helper.assertTrue(!head.tank()
				.isEmpty(), "The fuel is not drained");
		});
	}
}
