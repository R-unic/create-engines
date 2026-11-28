package com.createengines.engine.topology;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import com.createengines.config.CEConfig;
import com.createengines.content.airflow.ManifoldBlock;
import com.createengines.content.airflow.Pipes;
import com.createengines.content.airflow.PortKind;
import com.createengines.content.airflow.PortOpeningBlock;
import com.createengines.content.airflow.SensorBlock;
import com.createengines.content.airflow.ThrottleBlock;
import com.createengines.content.head.CylinderHeadBlock;
import com.createengines.content.rotor.RotorHousingBlock;
import com.createengines.content.airflow.InlineDuct;
import com.createengines.content.airfilter.AirFilterBlock;
import com.createengines.content.intercooler.IntercoolerBlock;
import com.createengines.content.muffler.MufflerBlock;
import com.createengines.content.muffler.SootTrapBlock;
import com.createengines.content.supercharger.SuperchargerBlock;
import com.createengines.content.turbo.TurbochargerBlock;
import com.createengines.engine.topology.EngineLayout.PortNetwork;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Follows a manifold network from a port: what is on it, what is open and what the air can go round. Part of {@link EngineScanner}. */
final class PortTracer {

	private final EngineScanner scanner;

	PortTracer(EngineScanner scanner) {
		this.scanner = scanner;
	}

	/**
	 * The port kind of the duct block reached by stepping {@code towards} from a head, if it connects back: a pipe
	 * that is open on that side, or an opening whose back is against the head.
	 */
	static @Nullable PortKind portKindFacing(BlockState state, Direction towards) {
		if (state.getBlock() instanceof ManifoldBlock manifold)
			return state.getValue(ManifoldBlock.CONNECTIONS.get(towards.getOpposite())) ? manifold.kind() : null;
		if (state.getBlock() instanceof PortOpeningBlock opening
			&& state.getValue(PortOpeningBlock.FACING) == towards)
			return opening.kind();
		// A head bolted straight to an inline duct uses whichever face is for one kind of air; a turbo's compressor face
		// is an intake and its turbine face an exhaust, and its side faces need a pipe to say which
		if (state.getBlock() instanceof InlineDuct duct)
			return duct.kindFacingHead(state, towards.getOpposite());
		return null;
	}

	/** Whether the duct block at {@code state}, a pipe or an inline duct, is open towards {@code direction} for {@code kind}. */
	static boolean opensTowards(BlockState state, Direction direction, PortKind kind) {
		if (state.getBlock() instanceof ManifoldBlock)
			return state.getValue(ManifoldBlock.CONNECTIONS.get(direction));
		return state.getBlock() instanceof InlineDuct duct && duct.acceptsPipe(state, direction, kind);
	}

	record Trace(Set<BlockPos> visited, Set<BlockPos> openings, Set<BlockPos> heads) {
	}

	/**
	 * Follows the pipes from {@code start}, wherever they are open, to the heads and openings at their ends, as if the
	 * duct block at {@code excluded} (if any) were not there. A turbocharger, supercharger or intercooler is a junction
	 * like any other pipe block: the run goes in at one face and out of another.
	 */
	Trace trace(BlockPos start, PortKind kind, @Nullable BlockPos excluded) {
		int limit = CEConfig.MAX_MANIFOLD_BLOCKS.get();
		Set<BlockPos> visited = new HashSet<>();
		Set<BlockPos> openings = new LinkedHashSet<>();
		Set<BlockPos> heads = new HashSet<>();
		Deque<BlockPos> queue = new ArrayDeque<>();

		if (scanner.state(start).getBlock() instanceof PortOpeningBlock)
			openings.add(start.immutable());
		else if (!start.equals(excluded)) {
			queue.add(start.immutable());
			visited.add(start.immutable());
		}
		while (!queue.isEmpty() && visited.size() < limit) {
			BlockPos duct = queue.poll();
			BlockState ductState = scanner.state(duct);
			for (Direction direction : Direction.values()) {
				if (!opensTowards(ductState, direction, kind))
					continue;
				BlockPos next = duct.relative(direction);
				if (next.equals(excluded))
					continue;
				BlockState state = scanner.state(next);
				Block block = state.getBlock();
				if (block instanceof ManifoldBlock manifold && manifold.kind() == kind) {
					if (state.getValue(ManifoldBlock.CONNECTIONS.get(direction.getOpposite()))
						&& visited.add(next.immutable()))
						queue.add(next.immutable());
				} else if (block instanceof InlineDuct inline) {
					if (inline.acceptsPipe(state, direction.getOpposite(), kind) && visited.add(next.immutable()))
						queue.add(next.immutable());
				} else if (block instanceof PortOpeningBlock opening && opening.kind() == kind
					&& state.getValue(PortOpeningBlock.FACING) == direction)
					openings.add(next.immutable());
				else if ((block instanceof CylinderHeadBlock || block instanceof RotorHousingBlock)
					&& Pipes.accepts(state, direction.getOpposite(), kind))
					heads.add(next.immutable());
			}
		}
		// An opening also serves the head its back is against
		for (BlockPos opening : openings) {
			BlockPos behind = opening.relative(scanner.state(opening).getValue(PortOpeningBlock.FACING)
				.getOpposite());
			if (scanner.state(behind).getBlock() instanceof CylinderHeadBlock
				|| scanner.state(behind).getBlock() instanceof RotorHousingBlock)
				heads.add(behind.immutable());
		}
		return new Trace(visited, openings, heads);
	}

	/**
	 * How many blocks the gas goes through between a head and the nearest opening, the opening included, averaged over the
	 * heads: how long the run sounds. Worked back from the openings through the pipes of the trace. A head with an opening
	 * on it, or one the pipes do not reach, is left out; with none left the run is one block.
	 */
	static int runLength(Set<BlockPos> ducts, Set<BlockPos> openings, Set<BlockPos> heads) {
		Map<BlockPos, Integer> distance = new HashMap<>();
		Deque<BlockPos> queue = new ArrayDeque<>();
		for (BlockPos opening : openings) {
			distance.put(opening, 1);
			queue.add(opening);
		}
		while (!queue.isEmpty()) {
			BlockPos from = queue.poll();
			for (Direction direction : Direction.values()) {
				BlockPos next = from.relative(direction);
				if (!ducts.contains(next) || distance.containsKey(next))
					continue;
				distance.put(next, distance.get(from) + 1);
				queue.add(next);
			}
		}
		int total = 0;
		int counted = 0;
		for (BlockPos head : heads) {
			int nearest = Integer.MAX_VALUE;
			for (Direction direction : Direction.values()) {
				BlockPos beside = head.relative(direction);
				Integer steps = ducts.contains(beside) ? distance.get(beside) : null;
				if (steps != null)
					nearest = Math.min(nearest, steps);
			}
			if (nearest != Integer.MAX_VALUE) {
				total += nearest;
				counted++;
			}
		}
		return counted == 0 ? 1 : Math.round(total / (float) counted);
	}

	int portNetwork(BlockPos start, PortKind kind) {
		Map<BlockPos, Integer> index = scanner.portIndex.computeIfAbsent(kind, $ -> new HashMap<>());
		Integer known = index.get(start);
		if (known != null)
			return known;

		Trace trace = trace(start, kind, null);
		Set<BlockPos> visited = trace.visited;
		Set<BlockPos> openings = trace.openings;
		Set<BlockPos> heads = trace.heads;

		int open = 0;
		int flooded = 0;
		for (BlockPos opening : openings) {
			BlockState openingState = scanner.state(opening);
			if (PortOpeningBlock.isFlooded(scanner.level, opening, openingState))
				flooded++;
			else if (PortOpeningBlock.isOpen(scanner.level, opening, openingState))
				open++;
		}

		List<BlockPos> throttles = new ArrayList<>();
		List<BlockPos> sensors = new ArrayList<>();
		List<BlockPos> turbos = new ArrayList<>();
		List<BlockPos> superchargers = new ArrayList<>();
		List<BlockPos> intercoolers = new ArrayList<>();
		List<BlockPos> filters = new ArrayList<>();
		List<BlockPos> mufflers = new ArrayList<>();
		List<BlockPos> traps = new ArrayList<>();
		for (BlockPos pos : visited) {
			Block block = scanner.state(pos).getBlock();
			if (block instanceof ThrottleBlock)
				throttles.add(pos);
			else if (block instanceof SensorBlock<?>)
				sensors.add(pos);
			else if (block instanceof TurbochargerBlock)
				turbos.add(pos);
			else if (block instanceof SuperchargerBlock)
				superchargers.add(pos);
			else if (block instanceof IntercoolerBlock)
				intercoolers.add(pos);
			else if (block instanceof AirFilterBlock)
				filters.add(pos);
			else if (block instanceof MufflerBlock)
				mufflers.add(pos);
			else if (block instanceof SootTrapBlock)
				traps.add(pos);
		}

		// A compressor, turbine or muffler that the air can go round (another branch of the run reaches an opening without
		// it) shares the flow with whatever is on that branch; one that every path goes through takes all of it
		Set<BlockPos> parallel = new HashSet<>();
		for (List<BlockPos> stages : List.of(turbos, superchargers, mufflers, traps, filters))
			for (BlockPos stage : stages)
				if (!trace(start, kind, stage).openings.isEmpty())
					parallel.add(stage);

		int number = scanner.ports.size();
		scanner.ports.add(new PortNetwork(kind, open, flooded, Math.max(1, heads.size()), List.copyOf(openings), List.copyOf(throttles), List.copyOf(sensors),
			List.copyOf(turbos), List.copyOf(superchargers), List.copyOf(intercoolers), Set.copyOf(parallel),
			List.copyOf(mufflers), List.copyOf(traps), runLength(visited, openings, heads), List.copyOf(filters)));
		for (BlockPos pos : visited) {
			index.put(pos, number);
			scanner.members.add(pos);
		}
		for (BlockPos pos : openings) {
			index.put(pos, number);
			scanner.members.add(pos);
		}
		return number;
	}
}
