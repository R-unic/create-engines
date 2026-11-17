package com.createengines.engine.topology;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.belt.BeltBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * Finds the Create blocks that tie an engine together without being part of what {@link EngineScanner} follows: the
 * cogwheels, gearboxes and chain drives that turn each camshaft from the crank, and those that gear one crank line to
 * another in a coupled engine. They are not in {@link EngineLayout#members()} (the scanner finds the other lines and the
 * camshafts by other routes), yet an engine laid out without them has no valve timing, or is two engines.
 * <p>
 * Reads only. It walks the connections Create itself propagates rotation along (see {@link CrankCoupling}) and, for each
 * cam line and each crank line after the first, takes the shortest chain of kinetic blocks that reaches it. Nothing is changed,
 * and nothing here feeds the simulation or the rescan triggers.
 */
public final class EngineGearing {

	/**
	 * @param blocks   the gearing's blocks, not counting the engine's own and not the camshafts
	 * @param unlinked cam lines and crank lines no gearing could be found for, or that are driven through a belt (a belt is
	 *                 more than one block and cannot be laid out block by block), so they are left out
	 */
	public record Result(Set<BlockPos> blocks, int unlinked) {
		static final Result NONE = new Result(Set.of(), 0);
	}

	private EngineGearing() {
	}

	/**
	 * The gearing of the engine.
	 *
	 * @param limit most kinetic blocks to look through (the scanner's coupling walk limit)
	 */
	public static Result find(Level level, EngineLayout layout, int limit) {
		List<List<BlockPos>> lines = crankLines(layout);
		if (lines.isEmpty() || lines.size() == 1 && layout.camLines()
			.isEmpty())
			return Result.NONE;
		Set<BlockPos> engine = layout.members();
		Set<BlockPos> blocks = new HashSet<>();
		int unlinked = 0;

		// Every crank line after the first is reached from the first
		if (lines.size() > 1) {
			Map<BlockPos, BlockPos> parent = walk(level, lines.getFirst(), limit);
			for (List<BlockPos> line : lines.subList(1, lines.size())) {
				List<BlockPos> path = shortest(level, line, parent, engine);
				if (path == null)
					unlinked++;
				else
					blocks.addAll(path);
			}
		}

		// Every cam line is reached from any crank line
		if (!layout.camLines()
			.isEmpty()) {
			List<BlockPos> all = new ArrayList<>();
			for (List<BlockPos> line : lines)
				all.addAll(line);
			Map<BlockPos, BlockPos> parent = walk(level, all, limit);
			for (EngineLayout.CamLine line : layout.camLines()) {
				List<BlockPos> path = shortest(level, line.segments(), parent, engine);
				if (path == null)
					unlinked++;
				else
					blocks.addAll(path);
			}
		}
		return new Result(Collections.unmodifiableSet(blocks), unlinked);
	}

	/** The engine's crank lines, the first line first: runs of segments that follow one another block by block. */
	private static List<List<BlockPos>> crankLines(EngineLayout layout) {
		List<List<BlockPos>> lines = new ArrayList<>();
		BlockPos previous = null;
		for (EngineLayout.Segment segment : layout.segments()) {
			if (previous == null || previous.distManhattan(segment.pos()) != 1)
				lines.add(new ArrayList<>());
			lines.getLast()
				.add(segment.pos());
			previous = segment.pos();
		}
		return lines;
	}

	/** Every kinetic block reachable from {@code from} through Create's connections, each with the block it was reached from. */
	private static Map<BlockPos, BlockPos> walk(Level level, Collection<BlockPos> from, int limit) {
		Map<BlockPos, BlockPos> parent = new HashMap<>();
		Deque<KineticBlockEntity> queue = new ArrayDeque<>();
		for (BlockPos pos : from)
			if (level.getBlockEntity(pos) instanceof KineticBlockEntity be && parent.putIfAbsent(pos, pos) == null)
				queue.add(be);
		while (!queue.isEmpty() && parent.size() < limit) {
			KineticBlockEntity be = queue.poll();
			if (!CrankCoupling.surroundingsLoaded(level, be.getBlockPos()))
				continue;
			for (KineticBlockEntity next : CrankCoupling.neighbours(be)) {
				BlockPos pos = next.getBlockPos();
				if (parent.containsKey(pos) || CrankCoupling.modifier(be, next) == 0)
					continue;
				parent.put(pos.immutable(), be.getBlockPos());
				queue.add(next);
			}
		}
		return parent;
	}

	/** The blocks strictly between the sources and the target line, or null if none connects or a belt is in the way. */
	private static List<BlockPos> shortest(Level level, List<BlockPos> target, Map<BlockPos, BlockPos> parent, Set<BlockPos> engine) {
		List<BlockPos> best = null;
		for (BlockPos end : target) {
			if (!parent.containsKey(end))
				continue;
			List<BlockPos> path = new ArrayList<>();
			boolean belt = false;
			BlockPos cursor = parent.get(end);
			int guard = parent.size() + 1;
			while (guard-- > 0 && !cursor.equals(parent.get(cursor))) {
				if (!engine.contains(cursor) && !target.contains(cursor)) {
					path.add(cursor);
					belt |= level.getBlockState(cursor)
						.getBlock() instanceof BeltBlock;
				}
				cursor = parent.get(cursor);
			}
			if (guard < 0 || belt)
				continue;
			if (best == null || path.size() < best.size())
				best = path;
		}
		return best;
	}
}
