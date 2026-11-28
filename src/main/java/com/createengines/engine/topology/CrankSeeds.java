package com.createengines.engine.topology;

import static com.createengines.engine.topology.EngineScanner.*;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

import com.createengines.config.CEConfig;
import com.createengines.content.airflow.ManifoldBlock;
import com.createengines.content.airflow.PortOpeningBlock;
import com.createengines.content.alternator.AlternatorBlock;
import com.createengines.content.battery.BatteryBlock;
import com.createengines.content.camshaft.CamshaftBlock;
import com.createengines.content.crankshaft.CrankshaftBlock;
import com.createengines.content.cylinder.CylinderBlock;
import com.createengines.content.head.CylinderHeadBlock;
import com.createengines.content.governor.GovernorBlock;
import com.createengines.content.rotor.RotorHousingBlock;
import com.createengines.content.starter.StarterBlock;
import com.createengines.content.radiator.RadiatorBlock;
import com.createengines.content.sump.SumpBlock;
import com.createengines.content.airflow.InlineDuct;
import com.simibubi.create.AllBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** Which cranks a change at a block could affect; see {@link EngineScanner#crankSeeds}. */
final class CrankSeeds {

	private CrankSeeds() {
	}

	/**
	 * Crankshaft blocks of any engine that a change at {@code pos} could affect. Used for blocks that are not yet
	 * part of a known engine, e.g. a cylinder just placed against a crank throw.
	 */
	public static Set<BlockPos> crankSeeds(Level level, BlockPos pos) {
		Set<BlockPos> seeds = new HashSet<>();
		if (!level.isLoaded(pos))
			return seeds;
		BlockState state = level.getBlockState(pos);
		Block block = state.getBlock();

		if (block instanceof CrankshaftBlock)
			seeds.add(pos);
		else if (block instanceof CylinderBlock)
			addIfCrank(level, pos.relative(state.getValue(CylinderBlock.FACING)
				.getOpposite()), seeds);
		else if (block instanceof CylinderHeadBlock)
			addHeadSeed(level, pos, state, seeds);
		else if (block instanceof StarterBlock)
			addIfCrank(level, pos.relative(state.getValue(StarterBlock.FACING)), seeds);
		else if (block instanceof CamshaftBlock) {
			Axis axis = state.getValue(BlockStateProperties.AXIS);
			for (AxisDirection sign : AxisDirection.values()) {
				Direction direction = Direction.fromAxisAndDirection(axis, sign);
				BlockPos cursor = pos;
				for (int i = 0; i < MAX_SHAFT_WALK * 4 && isCam(loadedState(level, cursor), axis); i++) {
					for (Direction side : Direction.values()) {
						BlockState sideState = loadedState(level, cursor.relative(side));
						addHeadSeed(level, cursor.relative(side), sideState, seeds);
						addCylinderSeed(level, cursor.relative(side), sideState, seeds);
					}
					cursor = cursor.relative(direction);
				}
			}
		} else if (block instanceof BatteryBlock) {
			// A bank: any battery in it may be the one an engine touches
			Set<BlockPos> bank = new HashSet<>();
			Deque<BlockPos> queue = new ArrayDeque<>();
			bank.add(pos.immutable());
			queue.add(pos.immutable());
			while (!queue.isEmpty()) {
				BlockPos at = queue.poll();
				for (Direction direction : Direction.values()) {
					BlockPos next = at.relative(direction);
					BlockState nextState = loadedState(level, next);
					addHeadSeed(level, next, nextState, seeds);
					addIfCrank(level, next, seeds);
					if (nextState.getBlock() instanceof BatteryBlock && bank.add(next.immutable()))
						queue.add(next.immutable());
				}
			}
		} else if (block instanceof RadiatorBlock) {
			for (Direction direction : Direction.values()) {
				BlockPos next = pos.relative(direction);
				BlockState nextState = loadedState(level, next);
				addHeadSeed(level, next, nextState, seeds);
				addCylinderSeed(level, next, nextState, seeds);
				addIfCrank(level, next, seeds);
			}
		} else if (block instanceof AlternatorBlock || block instanceof SumpBlock
			|| block instanceof GovernorBlock) {
			for (Direction direction : Direction.values())
				addHeadSeed(level, pos.relative(direction), loadedState(level, pos.relative(direction)), seeds);
		} else if (block instanceof ManifoldBlock || block instanceof PortOpeningBlock
			|| block instanceof InlineDuct) {
			Set<BlockPos> visited = new HashSet<>();
			Deque<BlockPos> queue = new ArrayDeque<>();
			queue.add(pos);
			visited.add(pos);
			while (!queue.isEmpty() && visited.size() < CEConfig.MAX_MANIFOLD_BLOCKS.get()) {
				BlockPos duct = queue.poll();
				for (Direction direction : Direction.values()) {
					BlockPos next = duct.relative(direction);
					if (!level.isLoaded(next))
						continue;
					BlockState nextState = level.getBlockState(next);
					if ((nextState.getBlock() instanceof ManifoldBlock || nextState.getBlock() instanceof InlineDuct)
						&& visited.add(next))
						queue.add(next);
					else
						addHeadSeed(level, next, nextState, seeds);
				}
			}
		} else if (state.hasProperty(BlockStateProperties.AXIS)
			&& (AllBlocks.SHAFT.has(state) || AllBlocks.FLYWHEEL.has(state))) {
			Axis axis = state.getValue(BlockStateProperties.AXIS);
			for (AxisDirection sign : AxisDirection.values()) {
				Direction direction = Direction.fromAxisAndDirection(axis, sign);
				BlockPos cursor = pos.relative(direction);
				for (int i = 0; i < MAX_SHAFT_WALK && level.isLoaded(cursor); i++) {
					BlockState cursorState = level.getBlockState(cursor);
					if (isCrank(cursorState, axis)) {
						seeds.add(cursor);
						break;
					}
					if (!cursorState.hasProperty(BlockStateProperties.AXIS)
						|| cursorState.getValue(BlockStateProperties.AXIS) != axis)
						break;
					cursor = cursor.relative(direction);
				}
			}
		}

		for (Direction direction : Direction.values())
			addIfCrank(level, pos.relative(direction), seeds);
		return seeds;
	}

	/** The block at {@code pos}, or air if its chunk is not loaded: a lookup here must never load a chunk. */
	static BlockState loadedState(Level level, BlockPos pos) {
		return level.isLoaded(pos) ? level.getBlockState(pos) : Blocks.AIR.defaultBlockState();
	}

	static void addHeadSeed(Level level, BlockPos pos, BlockState state, Set<BlockPos> seeds) {
		// A rotor housing is its own head, and sits in the crankshaft line
		if (state.getBlock() instanceof RotorHousingBlock) {
			seeds.add(pos.immutable());
			return;
		}
		if (!(state.getBlock() instanceof CylinderHeadBlock))
			return;
		Direction outward = state.getValue(CylinderHeadBlock.FACING);
		BlockPos cylinder = pos.relative(outward.getOpposite());
		if (!level.isLoaded(cylinder))
			return;
		BlockState cylinderState = level.getBlockState(cylinder);
		if (cylinderState.getBlock() instanceof CylinderBlock && cylinderState.getValue(CylinderBlock.FACING) == outward)
			addIfCrank(level, cylinder.relative(outward.getOpposite()), seeds);
	}

	static void addCylinderSeed(Level level, BlockPos pos, BlockState state, Set<BlockPos> seeds) {
		if (state.getBlock() instanceof CylinderBlock)
			addIfCrank(level, pos.relative(state.getValue(CylinderBlock.FACING)
				.getOpposite()), seeds);
	}

	static void addIfCrank(Level level, BlockPos pos, Set<BlockPos> seeds) {
		if (level.isLoaded(pos) && level.getBlockState(pos)
			.getBlock() instanceof CrankshaftBlock)
			seeds.add(pos.immutable());
	}
}
