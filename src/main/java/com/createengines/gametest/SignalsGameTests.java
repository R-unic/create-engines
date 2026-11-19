package com.createengines.gametest;

import java.util.ArrayList;
import java.util.List;

import com.createengines.CreateEngines;
import com.createengines.engine.Signals;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * {@link Signals} reads redstone round a block the way vanilla's {@code getBestNeighborSignal} and {@code hasNeighborSignal}
 * do, through one moved position instead of six new ones. Here every kind of neighbour that gives, passes on or blocks a
 * signal is put round a block in random combinations, and the two ways of reading must agree on each.
 */
@GameTestHolder(CreateEngines.MOD_ID)
@PrefixGameTestTemplate(false)
public class SignalsGameTests {

	/** Blocks that emit a signal on some side, pass one through, or do neither, with their power set at random. */
	private static List<BlockState> neighbours(RandomSource random) {
		List<BlockState> states = new ArrayList<>();
		states.add(Blocks.AIR.defaultBlockState());
		states.add(Blocks.STONE.defaultBlockState());
		states.add(Blocks.REDSTONE_BLOCK.defaultBlockState());
		states.add(Blocks.REDSTONE_WIRE.defaultBlockState()
			.setValue(BlockStateProperties.POWER, random.nextInt(16)));
		states.add(Blocks.LEVER.defaultBlockState()
			.setValue(BlockStateProperties.POWERED, random.nextBoolean())
			.setValue(BlockStateProperties.ATTACH_FACE, net.minecraft.world.level.block.state.properties.AttachFace.values()[random.nextInt(3)])
			.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.Plane.HORIZONTAL.getRandomDirection(random)));
		states.add(Blocks.REPEATER.defaultBlockState()
			.setValue(BlockStateProperties.POWERED, random.nextBoolean())
			.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.Plane.HORIZONTAL.getRandomDirection(random)));
		states.add(Blocks.COMPARATOR.defaultBlockState()
			.setValue(BlockStateProperties.POWERED, random.nextBoolean())
			.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.Plane.HORIZONTAL.getRandomDirection(random)));
		states.add(Blocks.OBSERVER.defaultBlockState()
			.setValue(BlockStateProperties.POWERED, random.nextBoolean())
			.setValue(BlockStateProperties.FACING, Direction.getRandom(random)));
		states.add(Blocks.REDSTONE_TORCH.defaultBlockState()
			.setValue(BlockStateProperties.LIT, random.nextBoolean()));
		states.add(Blocks.HEAVY_WEIGHTED_PRESSURE_PLATE.defaultBlockState()
			.setValue(BlockStateProperties.POWER, random.nextInt(16)));
		states.add(Blocks.DAYLIGHT_DETECTOR.defaultBlockState()
			.setValue(BlockStateProperties.POWER, random.nextInt(16)));
		states.add(Blocks.STONE_BUTTON.defaultBlockState()
			.setValue(BlockStateProperties.POWERED, random.nextBoolean())
			.setValue(BlockStateProperties.ATTACH_FACE, net.minecraft.world.level.block.state.properties.AttachFace.values()[random.nextInt(3)])
			.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.Plane.HORIZONTAL.getRandomDirection(random)));
		return states;
	}

	@GameTest(template = "empty", timeoutTicks = 200)
	public static void signalsReadWhatVanillaReads(GameTestHelper helper) {
		Level level = helper.getLevel();
		BlockPos centre = helper.absolutePos(new BlockPos(3, 2, 3));
		RandomSource random = RandomSource.create(20261119);
		int strong = 0;
		int quiet = 0;
		for (int round = 0; round < 1500; round++) {
			List<BlockState> choices = neighbours(random);
			for (Direction side : Direction.values())
				// No neighbour updates: a torch or a wire would settle into something else, and the point is to read what is set
				level.setBlock(centre.relative(side), choices.get(random.nextInt(choices.size())), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
			int best = level.getBestNeighborSignal(centre);
			boolean any = level.hasNeighborSignal(centre);
			helper.assertTrue(Signals.bestNeighbour(level, centre) == best,
				"Round " + round + ": the best signal is " + Signals.bestNeighbour(level, centre) + ", vanilla reads " + best);
			helper.assertTrue(Signals.hasNeighbour(level, centre) == any,
				"Round " + round + ": has a signal is " + Signals.hasNeighbour(level, centre) + ", vanilla reads " + any);
			if (best > 0)
				strong++;
			else
				quiet++;
		}
		helper.assertTrue(strong > 100 && quiet > 100, "The rounds should cover both signals and none: " + strong + " and " + quiet);
		helper.assertTrue(Signals.neighboursLoaded(level, centre), "The neighbours of a block in the middle of the test are loaded");
		for (Direction side : Direction.values())
			level.setBlock(centre.relative(side), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
		helper.succeed();
	}

	/** A reading must not leave the shared position pointing anywhere that matters, or two readers on one thread would cross. */
	@GameTest(template = "empty", timeoutTicks = 200)
	public static void readsDoNotDisturbEachOther(GameTestHelper helper) {
		Level level = helper.getLevel();
		BlockPos here = helper.absolutePos(new BlockPos(2, 2, 2));
		BlockPos there = helper.absolutePos(new BlockPos(5, 2, 3));
		level.setBlock(here.east(), Blocks.REDSTONE_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
		helper.assertTrue(Signals.bestNeighbour(level, here) == 15, "A redstone block beside a block gives it a full signal");
		helper.assertTrue(Signals.bestNeighbour(level, there) == 0, "A block with nothing round it reads none, straight after another read");
		helper.assertTrue(Signals.hasNeighbour(level, here) && !Signals.hasNeighbour(level, there),
			"The first has a signal and the second does not, however they are read in turn");
		level.setBlock(here.east(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
		helper.succeed();
	}
}
