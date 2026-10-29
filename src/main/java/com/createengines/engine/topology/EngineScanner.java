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
import com.createengines.content.alternator.AlternatorBlock;
import com.createengines.content.battery.BatteryBlock;
import com.createengines.content.camshaft.CamLobeBlock;
import com.createengines.content.camshaft.CamshaftBlock;
import com.createengines.content.camshaft.CamshaftBlockEntity;
import com.createengines.content.crankshaft.CrankshaftBlock;
import com.createengines.content.crankshaft.CrankshaftBlockEntity;
import com.createengines.content.cylinder.CylinderBlock;
import com.createengines.content.head.CylinderHeadBlock;
import com.createengines.content.starter.StarterBlock;
import com.createengines.engine.CrankGeometry;
import com.createengines.engine.topology.EngineLayout.CamLine;
import com.createengines.engine.topology.EngineLayout.Cylinder;
import com.createengines.engine.topology.EngineLayout.PortNetwork;
import com.createengines.engine.topology.EngineLayout.Segment;
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

/**
 * Discovers an engine from the blocks actually placed in the world, starting at its crankshaft. Nothing here knows
 * about engine "types": an inline, V or boxer layout is just which throw faces have cylinders on them.
 */
public final class EngineScanner {

	private static final int MAX_SHAFT_WALK = 16;

	private final Level level;
	private final Set<BlockPos> members = new LinkedHashSet<>();
	private final List<PortNetwork> ports = new ArrayList<>();
	private final Map<BlockPos, Integer> portIndex = new HashMap<>();
	private final List<CamLine> camLines = new ArrayList<>();
	private final Map<BlockPos, Integer> camIndex = new HashMap<>();
	private boolean incomplete;

	private EngineScanner(Level level) {
		this.level = level;
	}

	/** Scans the engine whose crankshaft contains {@code seed}, or returns null if it isn't a crankshaft block. */
	public static @Nullable EngineLayout scan(Level level, BlockPos seed) {
		return new EngineScanner(level).run(seed);
	}

	private BlockState state(BlockPos pos) {
		if (!level.isLoaded(pos)) {
			incomplete = true;
			return Blocks.AIR.defaultBlockState();
		}
		return level.getBlockState(pos);
	}

	private static boolean isCrank(BlockState state, Axis axis) {
		return state.getBlock() instanceof CrankshaftBlock && state.getValue(BlockStateProperties.AXIS) == axis;
	}

	private @Nullable EngineLayout run(BlockPos seed) {
		BlockState seedState = state(seed);
		if (!(seedState.getBlock() instanceof CrankshaftBlock))
			return null;
		Axis axis = seedState.getValue(BlockStateProperties.AXIS);
		Direction back = Direction.fromAxisAndDirection(axis, AxisDirection.NEGATIVE);
		Direction forward = back.getOpposite();
		int maxSegments = CEConfig.MAX_CRANK_SEGMENTS.get();

		BlockPos start = seed;
		for (int i = 0; i < maxSegments && isCrank(state(start.relative(back)), axis); i++)
			start = start.relative(back);

		List<Segment> segments = new ArrayList<>();
		BlockPos cursor = start;
		while (segments.size() < maxSegments) {
			BlockState state = state(cursor);
			if (!isCrank(state, axis))
				break;
			boolean isThrow = ((CrankshaftBlock) state.getBlock()).isThrow();
			double throwAngle =
				isThrow && level.getBlockEntity(cursor) instanceof CrankshaftBlockEntity be ? be.throwAngle() : 0;
			segments.add(new Segment(cursor.immutable(), isThrow, throwAngle));
			members.add(cursor.immutable());
			cursor = cursor.relative(forward);
		}

		int flywheels = countFlywheels(start.relative(back), back, axis) + countFlywheels(cursor, forward, axis);

		Set<BlockPos> batteries = new LinkedHashSet<>();
		Set<BlockPos> alternators = new LinkedHashSet<>();
		for (Segment segment : segments)
			addElectricsAround(segment.pos(), batteries, alternators);

		List<CylinderDraft> drafts = new ArrayList<>();
		List<BlockPos> starters = new ArrayList<>();
		for (Segment segment : segments) {
			for (Direction side : Direction.values()) {
				if (side.getAxis() == axis)
					continue;
				BlockPos neighbour = segment.pos()
					.relative(side);
				BlockState state = state(neighbour);
				if (state.getBlock() instanceof CylinderBlock && state.getValue(CylinderBlock.FACING) == side)
					drafts.add(scanCylinder(axis, segment, side, neighbour, state));
				else if (state.getBlock() instanceof StarterBlock
					&& state.getValue(StarterBlock.FACING) == side.getOpposite()) {
					starters.add(neighbour);
					members.add(neighbour);
				}
			}
		}

		// Heads that touch share a fuel gallery
		List<List<BlockPos>> galleries = new ArrayList<>();
		Map<BlockPos, Integer> galleryOf = new HashMap<>();
		Set<BlockPos> heads = new HashSet<>();
		for (CylinderDraft draft : drafts)
			if (draft.head != null)
				heads.add(draft.head);
		for (CylinderDraft draft : drafts) {
			if (draft.head == null || galleryOf.containsKey(draft.head))
				continue;
			List<BlockPos> gallery = new ArrayList<>();
			Deque<BlockPos> queue = new ArrayDeque<>();
			queue.add(draft.head);
			galleryOf.put(draft.head, galleries.size());
			while (!queue.isEmpty()) {
				BlockPos head = queue.poll();
				gallery.add(head);
				for (Direction direction : Direction.values()) {
					BlockPos next = head.relative(direction);
					if (heads.contains(next) && !galleryOf.containsKey(next)) {
						galleryOf.put(next, galleries.size());
						queue.add(next);
					}
				}
			}
			galleries.add(List.copyOf(gallery));
		}

		for (BlockPos head : heads)
			addElectricsAround(head, batteries, alternators);

		List<Cylinder> cylinders = new ArrayList<>();
		for (CylinderDraft draft : drafts) {
			if (draft.sparkPlug && batteries.isEmpty())
				draft.faults |= CylinderFault.NO_BATTERY.bit();
			cylinders.add(draft.build(draft.head == null ? -1 : galleryOf.get(draft.head)));
		}

		return new EngineLayout(start.immutable(), axis, List.copyOf(segments), List.copyOf(cylinders),
			List.copyOf(ports), List.copyOf(camLines), List.copyOf(starters), List.copyOf(batteries),
			List.copyOf(alternators), List.copyOf(galleries), flywheels, Set.copyOf(members), incomplete);
	}

	/** Batteries touching {@code pos} on any side supply the engine's spark plugs, and alternators there charge them. */
	private void addElectricsAround(BlockPos pos, Set<BlockPos> batteries, Set<BlockPos> alternators) {
		for (Direction direction : Direction.values()) {
			BlockPos neighbour = pos.relative(direction);
			Block block = state(neighbour).getBlock();
			if (block instanceof BatteryBlock && batteries.add(neighbour.immutable())
				|| block instanceof AlternatorBlock && alternators.add(neighbour.immutable()))
				members.add(neighbour.immutable());
		}
	}

	private int countFlywheels(BlockPos from, Direction direction, Axis axis) {
		int flywheels = 0;
		BlockPos cursor = from;
		for (int i = 0; i < MAX_SHAFT_WALK; i++) {
			BlockState state = state(cursor);
			boolean onAxis = state.hasProperty(BlockStateProperties.AXIS)
				&& state.getValue(BlockStateProperties.AXIS) == axis;
			if (onAxis && AllBlocks.FLYWHEEL.has(state))
				flywheels++;
			else if (!onAxis || !AllBlocks.SHAFT.has(state))
				break;
			members.add(cursor.immutable());
			cursor = cursor.relative(direction);
		}
		return flywheels;
	}

	private final class CylinderDraft {
		BlockPos pos;
		BlockPos throwPos;
		Direction bore;
		double tdcAngle;
		@Nullable BlockPos head;
		@Nullable BlockPos lobe;
		int camLine = -1;
		double lobeAngle;
		int intake = -1;
		int exhaust = -1;
		int faults;
		boolean sparkPlug;

		Cylinder build(int gallery) {
			return new Cylinder(pos, throwPos, bore, tdcAngle, head, lobe, camLine, lobeAngle, intake, exhaust,
				gallery, faults, sparkPlug);
		}
	}

	private CylinderDraft scanCylinder(Axis axis, Segment segment, Direction bore, BlockPos pos, BlockState state) {
		members.add(pos.immutable());
		CylinderDraft draft = new CylinderDraft();
		draft.pos = pos.immutable();
		draft.throwPos = segment.pos();
		draft.bore = bore;
		draft.tdcAngle = CrankGeometry.topDeadCentreAngle(axis, segment.throwAngle(), bore);
		if (!segment.isThrow())
			draft.faults |= CylinderFault.NOT_ON_THROW.bit();
		if (!state.getValue(CylinderBlock.HAS_PISTON))
			draft.faults |= CylinderFault.NO_PISTON.bit();
		if (!state.getValue(CylinderBlock.HAS_ROD))
			draft.faults |= CylinderFault.NO_ROD.bit();

		BlockPos headPos = pos.relative(bore);
		BlockState head = state(headPos);
		if (!(head.getBlock() instanceof CylinderHeadBlock) || head.getValue(CylinderHeadBlock.FACING) != bore) {
			draft.faults |= CylinderFault.NO_HEAD.bit();
			return draft;
		}
		draft.head = headPos.immutable();
		members.add(draft.head);
		if (!head.getValue(CylinderHeadBlock.HAS_INJECTOR))
			draft.faults |= CylinderFault.NO_INJECTOR.bit();
		draft.sparkPlug = head.getValue(CylinderHeadBlock.HAS_SPARK_PLUG);

		for (Direction face : Direction.values()) {
			if (face == bore.getOpposite())
				continue;
			BlockPos neighbour = headPos.relative(face);
			BlockState neighbourState = state(neighbour);
			if (draft.lobe == null && neighbourState.getBlock() instanceof CamLobeBlock
				&& neighbourState.getValue(BlockStateProperties.AXIS) != face.getAxis()) {
				draft.lobe = neighbour.immutable();
				continue;
			}
			PortKind kind = portKindFacing(neighbourState, face);
			if (kind == PortKind.INTAKE && draft.intake < 0)
				draft.intake = portNetwork(neighbour, kind);
			else if (kind == PortKind.EXHAUST && draft.exhaust < 0)
				draft.exhaust = portNetwork(neighbour, kind);
		}

		if (draft.lobe == null)
			draft.faults |= CylinderFault.NO_CAM.bit();
		else {
			draft.camLine = camLine(draft.lobe);
			if (level.getBlockEntity(draft.lobe) instanceof CamshaftBlockEntity lobe)
				draft.lobeAngle = lobe.lobeAngle();
		}
		if (draft.intake < 0)
			draft.faults |= CylinderFault.NO_INTAKE.bit();
		else if (ports.get(draft.intake)
			.openings() == 0)
			draft.faults |= CylinderFault.INTAKE_BLOCKED.bit();
		if (draft.exhaust < 0)
			draft.faults |= CylinderFault.NO_EXHAUST.bit();
		else if (ports.get(draft.exhaust)
			.openings() == 0)
			draft.faults |= CylinderFault.EXHAUST_BLOCKED.bit();
		return draft;
	}

	/**
	 * The port kind of the duct block reached by stepping {@code towards} from a head, if it connects back: a pipe
	 * that is open on that side, or an opening whose back is against the head.
	 */
	private static @Nullable PortKind portKindFacing(BlockState state, Direction towards) {
		if (state.getBlock() instanceof ManifoldBlock manifold)
			return state.getValue(ManifoldBlock.CONNECTIONS.get(towards.getOpposite())) ? manifold.kind() : null;
		if (state.getBlock() instanceof PortOpeningBlock opening
			&& state.getValue(PortOpeningBlock.FACING) == towards)
			return opening.kind();
		return null;
	}

	private int portNetwork(BlockPos start, PortKind kind) {
		Integer known = portIndex.get(start);
		if (known != null)
			return known;

		int limit = CEConfig.MAX_MANIFOLD_BLOCKS.get();
		Set<BlockPos> visited = new HashSet<>();
		Set<BlockPos> openings = new LinkedHashSet<>();
		Set<BlockPos> heads = new HashSet<>();
		Deque<BlockPos> queue = new ArrayDeque<>();

		if (state(start).getBlock() instanceof PortOpeningBlock)
			openings.add(start.immutable());
		else {
			queue.add(start.immutable());
			visited.add(start.immutable());
		}
		// Follow the pipes, wherever they are open, to the heads and openings at their ends
		while (!queue.isEmpty() && visited.size() < limit) {
			BlockPos duct = queue.poll();
			BlockState ductState = state(duct);
			for (Direction direction : Direction.values()) {
				if (!ductState.getValue(ManifoldBlock.CONNECTIONS.get(direction)))
					continue;
				BlockPos next = duct.relative(direction);
				BlockState state = state(next);
				Block block = state.getBlock();
				if (block instanceof ManifoldBlock manifold && manifold.kind() == kind) {
					if (state.getValue(ManifoldBlock.CONNECTIONS.get(direction.getOpposite()))
						&& visited.add(next.immutable()))
						queue.add(next.immutable());
				} else if (block instanceof PortOpeningBlock opening && opening.kind() == kind
					&& state.getValue(PortOpeningBlock.FACING) == direction)
					openings.add(next.immutable());
				else if (block instanceof CylinderHeadBlock && Pipes.accepts(state, direction.getOpposite(), kind))
					heads.add(next.immutable());
			}
		}
		// An opening also serves the head its back is against
		for (BlockPos opening : openings) {
			BlockPos behind = opening.relative(state(opening).getValue(PortOpeningBlock.FACING)
				.getOpposite());
			if (state(behind).getBlock() instanceof CylinderHeadBlock)
				heads.add(behind.immutable());
		}

		int open = 0;
		for (BlockPos opening : openings)
			if (PortOpeningBlock.isOpen(level, opening, state(opening)))
				open++;

		int index = ports.size();
		ports.add(new PortNetwork(kind, open, Math.max(1, heads.size()), List.copyOf(openings)));
		for (BlockPos pos : visited) {
			portIndex.put(pos, index);
			members.add(pos);
		}
		for (BlockPos pos : openings) {
			portIndex.put(pos, index);
			members.add(pos);
		}
		return index;
	}

	private int camLine(BlockPos lobe) {
		Integer known = camIndex.get(lobe);
		if (known != null)
			return known;
		Axis axis = state(lobe).getValue(BlockStateProperties.AXIS);
		Direction back = Direction.fromAxisAndDirection(axis, AxisDirection.NEGATIVE);
		BlockPos start = lobe;
		int max = CEConfig.MAX_CRANK_SEGMENTS.get();
		for (int i = 0; i < max && isCam(state(start.relative(back)), axis); i++)
			start = start.relative(back);
		List<BlockPos> segments = new ArrayList<>();
		for (BlockPos cursor = start; segments.size() < max && isCam(state(cursor), axis); cursor =
			cursor.relative(back.getOpposite()))
			segments.add(cursor.immutable());

		int index = camLines.size();
		camLines.add(new CamLine(axis, List.copyOf(segments)));
		for (BlockPos pos : segments) {
			camIndex.put(pos, index);
			members.add(pos);
		}
		return index;
	}

	private static boolean isCam(BlockState state, Axis axis) {
		return state.getBlock() instanceof CamshaftBlock && state.getValue(BlockStateProperties.AXIS) == axis;
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
				for (int i = 0; i < MAX_SHAFT_WALK * 4 && isCam(level.getBlockState(cursor), axis); i++) {
					for (Direction side : Direction.values())
						addHeadSeed(level, cursor.relative(side), level.getBlockState(cursor.relative(side)), seeds);
					cursor = cursor.relative(direction);
				}
			}
		} else if (block instanceof BatteryBlock || block instanceof AlternatorBlock) {
			for (Direction direction : Direction.values())
				addHeadSeed(level, pos.relative(direction), level.getBlockState(pos.relative(direction)), seeds);
		} else if (block instanceof ManifoldBlock || block instanceof PortOpeningBlock) {
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
					if (nextState.getBlock() instanceof ManifoldBlock && visited.add(next))
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

	private static void addHeadSeed(Level level, BlockPos pos, BlockState state, Set<BlockPos> seeds) {
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

	private static void addIfCrank(Level level, BlockPos pos, Set<BlockPos> seeds) {
		if (level.isLoaded(pos) && level.getBlockState(pos)
			.getBlock() instanceof CrankshaftBlock)
			seeds.add(pos.immutable());
	}
}
