package com.createengines.content.blueprint;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import com.createengines.content.airflow.HeatExchangerBlockEntity;
import com.createengines.content.blackbox.BlackBoxBlock;
import com.createengines.content.crankshaft.CrankshaftBlock;
import com.createengines.content.flywheel.CastFlywheelBlock;
import com.createengines.content.head.GalleryMember;
import com.createengines.content.transmission.TransmissionBlock;
import com.createengines.content.sump.SumpBlockEntity;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.fluids.FluidTransportBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * The parts that belong to an engine's build without being part of what the scanner follows: the Create pipes, pumps, valves and
 * tanks that carry fuel, oil, coolant and water to its heads, rotor housings, oil pan and heat exchanger (its fluid system), and
 * the Black Box recorders set against it. {@link BlueprintRecorder} puts them in a blueprint after the engine's own blocks.
 * <p>
 * Reads only, and never loads a chunk. None of these is in {@code EngineLayout#members()}, so they never trigger a rescan, and a
 * blueprint recorded before they were recorded still applies (see {@link BlueprintApplier}).
 */
public final class EngineAttachments {

	/** Most fluid blocks one engine's system adds, nearest to the engine first: a tank farm the pipes run into stops here. */
	public static final int MAX_FLUID_BLOCKS = 256;

	/** Most transmissions in a row on one end of the shaft. */
	private static final int MAX_DRIVE_CHAIN = 8;

	/**
	 * What was found.
	 *
	 * @param blocks      the fluid system's blocks and the Black Boxes, nearest first
	 * @param overflow    fluid blocks past {@link #MAX_FLUID_BLOCKS} that were left out
	 */
	public record Found(List<BlockPos> blocks, int overflow) {
	}

	private EngineAttachments() {
	}

	/** Whether this block is a Create block that carries or holds the fluid of an engine's system. */
	public static boolean isFluidSystem(BlockState state) {
		return AllBlocks.FLUID_PIPE.has(state) || AllBlocks.ENCASED_FLUID_PIPE.has(state) || AllBlocks.GLASS_FLUID_PIPE.has(state)
			|| AllBlocks.MECHANICAL_PUMP.has(state) || AllBlocks.SMART_FLUID_PIPE.has(state)
			|| AllBlocks.FLUID_VALVE.has(state) || AllBlocks.FLUID_TANK.has(state);
	}

	/** Whether the block with this registry id is one of {@link #isFluidSystem}'s. False for an id that is not registered. */
	public static boolean isFluidSystemId(String id) {
		BlockState state = defaultState(id);
		return state != null && isFluidSystem(state);
	}

	/** Whether the block with this registry id is a Black Box. */
	public static boolean isBlackBoxId(String id) {
		BlockState state = defaultState(id);
		return state != null && state.getBlock() instanceof BlackBoxBlock;
	}

	/** Whether the block with this registry id is a transmission. */
	public static boolean isTransmissionId(String id) {
		BlockState state = defaultState(id);
		return state != null && state.getBlock() instanceof TransmissionBlock;
	}

	private static @Nullable BlockState defaultState(String id) {
		ResourceLocation location = ResourceLocation.tryParse(id);
		if (location == null)
			return null;
		Block block = BuiltInRegistries.BLOCK.getOptional(location)
			.orElse(null);
		return block == null ? null : block.defaultBlockState();
	}

	/**
	 * The fluid system and Black Boxes of the engine whose blocks are {@code engine}: pipes, pumps, valves and tanks reached from
	 * the engine's fuel, oil and coolant ports by the connections they really have, and Black Boxes against any engine block.
	 */
	public static Found find(Level level, Set<BlockPos> engine, int fluidLimit) {
		Set<BlockPos> seen = new HashSet<>(engine);
		List<BlockPos> found = new ArrayList<>();
		Deque<BlockPos> queue = new ArrayDeque<>();
		Set<BlockPos> blackBoxes = new LinkedHashSet<>();

		for (BlockPos pos : engine) {
			if (!level.isLoaded(pos))
				continue;
			boolean exchanger = false;
			boolean holdsFluid = false;
			if (level.getBlockEntity(pos) instanceof GalleryMember || level.getBlockEntity(pos) instanceof SumpBlockEntity)
				holdsFluid = true;
			else if (level.getBlockEntity(pos) instanceof HeatExchangerBlockEntity)
				holdsFluid = exchanger = true;
			for (Direction side : Direction.values()) {
				BlockPos next = pos.relative(side);
				if (!level.isLoaded(next) || engine.contains(next))
					continue;
				BlockState state = level.getBlockState(next);
				if (state.getBlock() instanceof BlackBoxBlock)
					blackBoxes.add(next.immutable());
				// A pipe joins a port it is open towards. A tank sits on a heat exchanger and is not piped to it
				boolean joins = holdsFluid && isFluidSystem(state)
					&& (AllBlocks.FLUID_TANK.has(state) ? exchanger && side == Direction.UP : opensTowards(level, next, state, side.getOpposite()));
				if (joins && seen.add(next.immutable()))
					queue.add(next.immutable());
			}
		}

		// A transmission sits on the end of the engine's shaft run, which the scanner's walk stops at
		Set<BlockPos> drive = new LinkedHashSet<>();
		for (BlockPos pos : engine) {
			if (!level.isLoaded(pos))
				continue;
			BlockState state = level.getBlockState(pos);
			if (!state.hasProperty(BlockStateProperties.AXIS) || !(state.getBlock() instanceof CrankshaftBlock
				|| state.getBlock() instanceof CastFlywheelBlock || AllBlocks.SHAFT.has(state) || AllBlocks.FLYWHEEL.has(state)))
				continue;
			Direction.Axis axis = state.getValue(BlockStateProperties.AXIS);
			for (Direction.AxisDirection sign : Direction.AxisDirection.values()) {
				Direction along = Direction.fromAxisAndDirection(axis, sign);
				BlockPos next = pos.relative(along);
				for (int i = 0; i < MAX_DRIVE_CHAIN && level.isLoaded(next) && !engine.contains(next); i++) {
					BlockState nextState = level.getBlockState(next);
					if (!(nextState.getBlock() instanceof TransmissionBlock)
						|| nextState.getValue(BlockStateProperties.AXIS) != axis || !drive.add(next.immutable()))
						break;
					next = next.relative(along);
				}
			}
		}

		int overflow = 0;
		while (!queue.isEmpty()) {
			BlockPos at = queue.poll();
			if (found.size() >= fluidLimit) {
				overflow++;
				continue;
			}
			found.add(at);
			BlockState state = level.getBlockState(at);
			boolean tank = AllBlocks.FLUID_TANK.has(state);
			for (Direction side : Direction.values()) {
				BlockPos next = at.relative(side);
				if (!level.isLoaded(next) || seen.contains(next))
					continue;
				BlockState nextState = level.getBlockState(next);
				if (!isFluidSystem(nextState))
					continue;
				boolean nextTank = AllBlocks.FLUID_TANK.has(nextState);
				// Tank cells make one tank; a pipe joins a tank or another pipe only where both are open to each other
				boolean joins = tank && nextTank
					|| (tank || opensTowards(level, at, state, side)) && (nextTank || opensTowards(level, next, nextState, side.getOpposite()));
				if (joins && seen.add(next.immutable()))
					queue.add(next.immutable());
			}
		}
		found.addAll(blackBoxes);
		found.addAll(drive);
		return new Found(found, overflow);
	}

	/** Whether the pipe, pump or valve at {@code pos} can pass fluid through its {@code side} face. A tank has no such restriction. */
	private static boolean opensTowards(Level level, BlockPos pos, BlockState state, Direction side) {
		if (AllBlocks.FLUID_TANK.has(state))
			return true;
		FluidTransportBehaviour transport = BlockEntityBehaviour.get(level, pos, FluidTransportBehaviour.TYPE);
		return transport != null && transport.canHaveFlowToward(state, side);
	}
}
