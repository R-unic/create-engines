package com.createengines.engine.topology;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.EnumMap;
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
import com.createengines.content.alternator.AlternatorBlock;
import com.createengines.content.battery.BatteryBlock;
import com.createengines.content.camshaft.CamLobeBlock;
import com.createengines.content.camshaft.CamshaftBlock;
import com.createengines.content.camshaft.CamshaftBlockEntity;
import com.createengines.content.crankshaft.CrankshaftBlock;
import com.createengines.content.crankshaft.CrankshaftBlockEntity;
import com.createengines.content.flywheel.CastFlywheelBlock;
import com.createengines.content.cylinder.CylinderBlock;
import com.createengines.content.head.CylinderHeadBlock;
import com.createengines.content.governor.GovernorBlock;
import com.createengines.content.rotor.RotorHousingBlock;
import com.createengines.content.starter.StarterBlock;
import com.createengines.content.sump.SumpBlock;
import com.createengines.content.airflow.InlineDuct;
import com.createengines.content.intercooler.IntercoolerBlock;
import com.createengines.content.muffler.MufflerBlock;
import com.createengines.content.supercharger.SuperchargerBlock;
import com.createengines.content.turbo.TurbochargerBlock;
import com.createengines.engine.CrankGeometry;
import com.createengines.engine.sim.Tuning;
import com.createengines.engine.topology.EngineLayout.CamLine;
import com.createengines.engine.topology.EngineLayout.Cylinder;
import com.createengines.engine.topology.EngineLayout.PortNetwork;
import com.createengines.engine.topology.EngineLayout.Segment;
import com.simibubi.create.AllBlocks;

import org.joml.Vector3f;

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
	/** Known port networks by a member's position; a turbocharger is in one of each kind, so the kinds are kept apart. */
	private final Map<PortKind, Map<BlockPos, Integer>> portIndex = new EnumMap<>(PortKind.class);
	private final List<CamLine> camLines = new ArrayList<>();
	private final Map<BlockPos, Integer> camIndex = new HashMap<>();
	private final Set<BlockPos> sumps = new LinkedHashSet<>();
	private final Set<BlockPos> governors = new LinkedHashSet<>();
	private boolean incomplete;
	/** A plain shaft sits between blocks of a crank line. */
	private boolean brokenCrank;

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

	/** One straight run of crankshaft blocks. Which way it turns is relative to the line the scan began on, then to the first. */
	private static final class Line {
		final Axis axis;
		final BlockPos start;
		final List<Segment> segments = new ArrayList<>();
		/** Rotational inertia of the flywheels on this line's shaft. */
		double flywheelInertia;
		int sense;

		Line(Axis axis, BlockPos start, int sense) {
			this.axis = axis;
			this.start = start;
			this.sense = sense;
		}

		/** Re-expresses this line's direction of rotation as relative to a line that turns {@code base}. */
		void relativeTo(int base) {
			sense *= base;
			segments.replaceAll(s -> new Segment(s.pos(), s.isThrow(), s.throwAngle(), sense));
		}
	}

	/** Lines are put in position order, so that every one of an engine's crankshafts finds the same first line. */
	private static final Comparator<Line> BY_POSITION = Comparator.<Line>comparingInt(line -> line.start.getX())
		.thenComparingInt(line -> line.start.getY())
		.thenComparingInt(line -> line.start.getZ());

	/** The whole crank line through {@code seed}, which turns {@code sense} relative to the line the scan began on. */
	private Line walk(BlockPos seed, int sense) {
		Axis axis = state(seed).getValue(BlockStateProperties.AXIS);
		Direction back = Direction.fromAxisAndDirection(axis, AxisDirection.NEGATIVE);
		Direction forward = back.getOpposite();
		int maxSegments = CEConfig.MAX_CRANK_SEGMENTS.get();

		BlockPos start = seed;
		for (int i = 0; i < maxSegments && isCrank(state(start.relative(back)), axis); i++)
			start = start.relative(back);

		Line line = new Line(axis, start.immutable(), sense);
		BlockPos cursor = start;
		while (line.segments.size() < maxSegments) {
			BlockState state = state(cursor);
			if (!isCrank(state, axis))
				break;
			boolean isThrow = ((CrankshaftBlock) state.getBlock()).isThrow();
			double throwAngle =
				isThrow && level.getBlockEntity(cursor) instanceof CrankshaftBlockEntity be ? be.throwAngle() : 0;
			line.segments.add(new Segment(cursor.immutable(), isThrow, throwAngle, sense));
			members.add(cursor.immutable());
			cursor = cursor.relative(forward);
		}
		line.flywheelInertia = flywheelInertia(start.relative(back), back, axis) + flywheelInertia(cursor, forward, axis);
		return line;
	}

	/**
	 * Adds every crank line that Create's gearing holds at exactly this line's speed (see {@link CrankCoupling}): those
	 * are cranks of one engine, which share a single rotor.
	 */
	private void addCoupledLines(List<Line> lines) {
		int limit = CEConfig.MAX_COUPLED_LINES.get();
		if (limit <= 1)
			return;
		Set<BlockPos> known = new HashSet<>();
		for (Segment segment : lines.getFirst().segments)
			known.add(segment.pos());
		Map<BlockPos, Integer> coupled = CrankCoupling.find(level, known, CEConfig.MAX_COUPLING_WALK.get());
		List<BlockPos> found = new ArrayList<>(coupled.keySet());
		found.sort(Comparator.<BlockPos>comparingInt(pos -> pos.getX())
			.thenComparingInt(pos -> pos.getY())
			.thenComparingInt(pos -> pos.getZ()));
		for (BlockPos pos : found) {
			if (lines.size() >= limit)
				break;
			if (known.contains(pos))
				continue;
			Line line = walk(pos, coupled.get(pos));
			lines.add(line);
			for (Segment segment : line.segments)
				known.add(segment.pos());
		}
	}

	private @Nullable EngineLayout run(BlockPos seed) {
		if (!(state(seed).getBlock() instanceof CrankshaftBlock))
			return null;

		List<Line> lines = new ArrayList<>();
		lines.add(walk(seed, 1));
		addCoupledLines(lines);
		lines.sort(BY_POSITION);
		// The first line sets the engine's direction: the others are +1 where they turn the way it does
		int base = lines.getFirst().sense;
		List<Segment> segments = new ArrayList<>();
		double flywheelInertia = 0;
		for (Line line : lines) {
			line.relativeTo(base);
			segments.addAll(line.segments);
			flywheelInertia += line.flywheelInertia;
		}

		Set<BlockPos> batteries = new LinkedHashSet<>();
		Set<BlockPos> alternators = new LinkedHashSet<>();
		for (Segment segment : segments)
			addElectricsAround(segment.pos(), batteries, alternators);

		List<CylinderDraft> drafts = new ArrayList<>();
		List<BlockPos> starters = new ArrayList<>();
		for (Line line : lines) {
			for (Segment segment : line.segments) {
				BlockState segmentState = state(segment.pos());
				boolean rotor = segmentState.getBlock() instanceof RotorHousingBlock;
				if (rotor)
					drafts.addAll(scanRotor(line.axis, segment, segmentState));
				for (Direction side : Direction.values()) {
					if (side.getAxis() == line.axis)
						continue;
					BlockPos neighbour = segment.pos()
						.relative(side);
					BlockState state = state(neighbour);
					// A rotor housing is its own cylinder: nothing is bored into it
					if (!rotor && state.getBlock() instanceof CylinderBlock && state.getValue(CylinderBlock.FACING) == side)
						drafts.add(scanCylinder(line.axis, segment, side, neighbour, state));
					else if (state.getBlock() instanceof StarterBlock
						&& state.getValue(StarterBlock.FACING) == side.getOpposite()) {
						starters.add(neighbour);
						members.add(neighbour);
					}
				}
			}
		}

		// Every head on the crankshaft shares one fuel gallery, so fuel piped into any of them runs the whole engine
		Set<BlockPos> heads = new LinkedHashSet<>();
		for (CylinderDraft draft : drafts)
			if (draft.head != null)
				heads.add(draft.head);

		for (BlockPos head : heads)
			addElectricsAround(head, batteries, alternators);

		List<Cylinder> cylinders = new ArrayList<>();
		Map<BlockPos, CylinderDraft> lobeLeaders = new HashMap<>();
		for (CylinderDraft draft : drafts) {
			if (draft.sparkPlug && batteries.isEmpty())
				draft.faults |= CylinderFault.NO_BATTERY.bit();
			// The first working cylinder a lobe touches is the one its angle times; the rest follow it round the crank
			if (draft.lobe != null && !CylinderFault.disables(draft.faults)) {
				CylinderDraft leader = lobeLeaders.putIfAbsent(draft.lobe, draft);
				if (leader != null)
					draft.lobeLeaderTdc = leader.tdcAngle;
			}
			cylinders.add(draft.build());
		}

		Line first = lines.getFirst();
		return new EngineLayout(first.start, first.axis, List.copyOf(segments), List.copyOf(cylinders),
			List.copyOf(ports), List.copyOf(camLines), List.copyOf(starters), List.copyOf(batteries),
			List.copyOf(alternators), List.copyOf(sumps), List.copyOf(governors), List.copyOf(heads),
			turbos(), inlines(true), inlines(false), flywheelInertia, lines.size(), Set.copyOf(members), incomplete, brokenCrank);
	}

	/** Every supercharger (or, if not {@code superchargers}, intercooler) on one of this engine's intake networks. */
	private List<EngineLayout.Inline> inlines(boolean superchargers) {
		List<EngineLayout.Inline> found = new ArrayList<>();
		for (int i = 0; i < ports.size(); i++) {
			PortNetwork network = ports.get(i);
			for (BlockPos pos : superchargers ? network.superchargers() : network.intercoolers())
				found.add(new EngineLayout.Inline(pos, i, network.parallel().contains(pos)));
		}
		return found;
	}

	/** Every turbocharger on one of this engine's port networks, with the intake and exhaust network it joins. */
	private List<EngineLayout.Turbo> turbos() {
		Map<BlockPos, int[]> joins = new java.util.LinkedHashMap<>();
		for (int i = 0; i < ports.size(); i++) {
			PortNetwork network = ports.get(i);
			for (BlockPos pos : network.turbos()) {
				int[] join = joins.computeIfAbsent(pos, $ -> new int[] {-1, -1, 0, 0});
				int side = network.kind() == PortKind.INTAKE ? 0 : 1;
				join[side] = i;
				join[2 + side] = network.parallel().contains(pos) ? 1 : 0;
			}
		}
		List<EngineLayout.Turbo> turbos = new ArrayList<>();
		joins.forEach((pos, join) -> turbos.add(new EngineLayout.Turbo(pos, join[0], join[1], join[2] == 1, join[3] == 1)));
		return turbos;
	}

	/**
	 * Batteries touching {@code pos} on any side supply the engine's spark plugs, alternators there charge them, and
	 * oil pans there oil the whole engine.
	 */
	private void addElectricsAround(BlockPos pos, Set<BlockPos> batteries, Set<BlockPos> alternators) {
		for (Direction direction : Direction.values()) {
			BlockPos neighbour = pos.relative(direction);
			Block block = state(neighbour).getBlock();
			if (block instanceof BatteryBlock && batteries.add(neighbour.immutable())
				|| block instanceof AlternatorBlock && alternators.add(neighbour.immutable())
				|| block instanceof SumpBlock && sumps.add(neighbour.immutable())
				|| block instanceof GovernorBlock && governors.add(neighbour.immutable()))
				members.add(neighbour.immutable());
		}
	}

	/** The inertia of the flywheels on the shaft running on from {@code from} in {@code direction}, beyond a crank line's end. */
	private double flywheelInertia(BlockPos from, Direction direction, Axis axis) {
		double inertia = 0;
		BlockPos cursor = from;
		for (int i = 0; i < MAX_SHAFT_WALK; i++) {
			BlockState state = state(cursor);
			boolean onAxis = state.hasProperty(BlockStateProperties.AXIS)
				&& state.getValue(BlockStateProperties.AXIS) == axis;
			if (onAxis && AllBlocks.FLYWHEEL.has(state))
				inertia += CEConfig.FLYWHEEL_INERTIA.get();
			else if (onAxis && state.getBlock() instanceof CastFlywheelBlock)
				inertia += CEConfig.CAST_FLYWHEEL_INERTIA.get();
			else if (i > 0 && isCrank(state, axis)) {
				// A shaft in the middle of the crankshaft: the blocks either side of it are not one line
				brokenCrank = true;
				break;
			} else if (!onAxis || !AllBlocks.SHAFT.has(state))
				break;
			members.add(cursor.immutable());
			cursor = cursor.relative(direction);
		}
		return inertia;
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
		int precision;
		int sense;
		double lobeLeaderTdc = Double.NaN;
		int chamber = -1;

		Cylinder build() {
			return new Cylinder(pos, throwPos, bore, tdcAngle, head, lobe, camLine, lobeAngle, intake, exhaust,
				faults, sparkPlug, precision, sense, lobeLeaderTdc, chamber);
		}

		CylinderDraft copyAsChamber(int chamber) {
			CylinderDraft copy = new CylinderDraft();
			copy.pos = pos;
			copy.throwPos = throwPos;
			copy.bore = bore;
			copy.tdcAngle = tdcAngle;
			copy.head = head;
			copy.lobe = lobe;
			copy.camLine = camLine;
			copy.lobeAngle = lobeAngle;
			copy.intake = intake;
			copy.exhaust = exhaust;
			copy.faults = faults;
			copy.sparkPlug = sparkPlug;
			copy.precision = precision;
			copy.sense = sense;
			copy.chamber = chamber;
			return copy;
		}
	}

	/**
	 * A rotor housing is a crank link, a head and a cylinder in one block, with three chambers (the rotor's faces) that
	 * share everything but their phase. The housing's side faces are its ports, found as a head's are; there is no
	 * camshaft to look for, because the ports are cut in the housing and time themselves. A housing with no rotor in it
	 * is one disabled entry, so it neither drags on the shaft nor appears three times in a readout.
	 */
	private List<CylinderDraft> scanRotor(Axis axis, Segment segment, BlockState state) {
		BlockPos pos = segment.pos();
		CylinderDraft draft = new CylinderDraft();
		draft.pos = pos.immutable();
		draft.throwPos = pos.immutable();
		draft.head = pos.immutable();
		// The housing's mark is the direction a throw at zero degrees points in
		Vector3f mark = CrankGeometry.restDirection(axis);
		draft.bore = Direction.getNearest(mark.x, mark.y, mark.z);
		draft.sense = segment.sense();
		draft.tdcAngle = segment.sense() * CrankGeometry.topDeadCentreAngle(axis, segment.throwAngle(), draft.bore);
		draft.sparkPlug = state.getValue(RotorHousingBlock.HAS_SPARK_PLUG);
		if (state.getValue(RotorHousingBlock.FINE_INJECTOR))
			draft.precision |= PrecisionParts.INJECTOR;
		if (!Tuning.rotaryEngines)
			draft.faults |= CylinderFault.ROTARY_DISABLED.bit();
		if (!state.getValue(RotorHousingBlock.HAS_ROTOR))
			draft.faults |= CylinderFault.NO_ROTOR.bit();
		if (!state.getValue(RotorHousingBlock.HAS_INJECTOR))
			draft.faults |= CylinderFault.NO_INJECTOR.bit();
		if (!draft.sparkPlug)
			draft.faults |= CylinderFault.NO_PLUG.bit();

		for (Direction face : Direction.values()) {
			if (face.getAxis() == axis)
				continue;
			BlockPos neighbour = pos.relative(face);
			PortKind kind = portKindFacing(state(neighbour), face);
			if (kind == PortKind.INTAKE && draft.intake < 0)
				draft.intake = portNetwork(neighbour, kind);
			else if (kind == PortKind.EXHAUST && draft.exhaust < 0)
				draft.exhaust = portNetwork(neighbour, kind);
		}
		draft.faults |= portFaults(draft);

		List<CylinderDraft> chambers = new ArrayList<>();
		int count = CylinderFault.NO_ROTOR.in(draft.faults) || CylinderFault.ROTARY_DISABLED.in(draft.faults) ? 1
			: RotorHousingBlock.CHAMBERS;
		for (int i = 0; i < count; i++)
			chambers.add(draft.copyAsChamber(i));
		return chambers;
	}

	private CylinderDraft scanCylinder(Axis axis, Segment segment, Direction bore, BlockPos pos, BlockState state) {
		members.add(pos.immutable());
		CylinderDraft draft = new CylinderDraft();
		draft.pos = pos.immutable();
		draft.throwPos = segment.pos();
		draft.bore = bore;
		draft.sense = segment.sense();
		// In the engine's own frame: a line geared to turn the other way reaches top dead centre at the mirror angle
		draft.tdcAngle = segment.sense() * CrankGeometry.topDeadCentreAngle(axis, segment.throwAngle(), bore);
		if (!segment.isThrow())
			draft.faults |= CylinderFault.NOT_ON_THROW.bit();
		if (!state.getValue(CylinderBlock.HAS_PISTON))
			draft.faults |= CylinderFault.NO_PISTON.bit();
		if (!state.getValue(CylinderBlock.HAS_ROD))
			draft.faults |= CylinderFault.NO_ROD.bit();
		if (((CylinderBlock) state.getBlock()).precision())
			draft.precision |= PrecisionParts.BORE;
		if (state.getValue(CylinderBlock.FORGED_PISTON))
			draft.precision |= PrecisionParts.PISTON;

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
		if (((CylinderHeadBlock) head.getBlock()).precision())
			draft.precision |= PrecisionParts.HEAD;
		if (head.getValue(CylinderHeadBlock.FINE_INJECTOR))
			draft.precision |= PrecisionParts.INJECTOR;

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

		// A cam beside the cylinder block works the valves through pushrods, which leaves the head's faces for pipes
		if (draft.lobe == null)
			draft.lobe = lobeBesideBlock(pos, bore);
		if (draft.lobe == null)
			draft.faults |= CylinderFault.NO_CAM.bit();
		else {
			draft.camLine = camLine(draft.lobe);
			if (camLines.get(draft.camLine)
				.broken()) {
				// Cut off from the rest of its camshaft, the lobe is not held to the crank's timing: no cam at all
				draft.faults |= CylinderFault.BROKEN_CAM.bit();
				draft.camLine = -1;
				draft.lobe = null;
			} else if (level.getBlockEntity(draft.lobe) instanceof CamshaftBlockEntity lobe)
				draft.lobeAngle = lobe.lobeAngle();
		}
		draft.faults |= portFaults(draft);
		return draft;
	}

	/** Whether a fluid is against any face of the block at {@code pos}. */
	private boolean wet(BlockPos pos) {
		for (Direction side : Direction.values())
			if (!state(pos.relative(side)).getFluidState()
				.isEmpty())
				return true;
		return false;
	}

	/**
	 * What is wrong with the way a cylinder breathes: no intake or exhaust at all, or one with no opening air can pass.
	 * An opening under a fluid is as shut as a covered one, and is reported as flooded so the player knows which it is.
	 * A cylinder, head or rotor housing with a fluid against it is underwater, which no length of pipe gets round.
	 */
	private int portFaults(CylinderDraft draft) {
		int faults = 0;
		if (wet(draft.pos) || draft.head != null && wet(draft.head))
			faults |= CylinderFault.SUBMERGED.bit();
		if (draft.intake < 0)
			faults |= CylinderFault.NO_INTAKE.bit();
		else if (ports.get(draft.intake)
			.openings() == 0)
			faults |= (ports.get(draft.intake)
				.flooded() > 0 ? CylinderFault.INTAKE_FLOODED : CylinderFault.INTAKE_BLOCKED).bit();
		if (draft.exhaust < 0)
			faults |= CylinderFault.NO_EXHAUST.bit();
		else if (ports.get(draft.exhaust)
			.openings() == 0)
			faults |= (ports.get(draft.exhaust)
				.flooded() > 0 ? CylinderFault.EXHAUST_FLOODED : CylinderFault.EXHAUST_BLOCKED).bit();
		return faults;
	}

	/** A cam lobe on one of the sides of the cylinder block at {@code pos}, which is bored towards {@code bore}. */
	private @Nullable BlockPos lobeBesideBlock(BlockPos pos, Direction bore) {
		for (Direction face : Direction.values()) {
			if (face.getAxis() == bore.getAxis())
				continue;
			BlockState neighbour = state(pos.relative(face));
			if (neighbour.getBlock() instanceof CamLobeBlock
				&& neighbour.getValue(BlockStateProperties.AXIS) != face.getAxis())
				return pos.relative(face)
					.immutable();
		}
		return null;
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
		// A head bolted straight to an inline duct uses whichever face is for one kind of air; a turbo's compressor face
		// is an intake and its turbine face an exhaust, and its side faces need a pipe to say which
		if (state.getBlock() instanceof InlineDuct duct)
			return duct.kindFacingHead(state, towards.getOpposite());
		return null;
	}

	/** Whether the duct block at {@code state}, a pipe or an inline duct, is open towards {@code direction} for {@code kind}. */
	private static boolean opensTowards(BlockState state, Direction direction, PortKind kind) {
		if (state.getBlock() instanceof ManifoldBlock)
			return state.getValue(ManifoldBlock.CONNECTIONS.get(direction));
		return state.getBlock() instanceof InlineDuct duct && duct.acceptsPipe(state, direction, kind);
	}

	private record Trace(Set<BlockPos> visited, Set<BlockPos> openings, Set<BlockPos> heads) {
	}

	/**
	 * Follows the pipes from {@code start}, wherever they are open, to the heads and openings at their ends, as if the
	 * duct block at {@code excluded} (if any) were not there. A turbocharger, supercharger or intercooler is a junction
	 * like any other pipe block: the run goes in at one face and out of another.
	 */
	private Trace trace(BlockPos start, PortKind kind, @Nullable BlockPos excluded) {
		int limit = CEConfig.MAX_MANIFOLD_BLOCKS.get();
		Set<BlockPos> visited = new HashSet<>();
		Set<BlockPos> openings = new LinkedHashSet<>();
		Set<BlockPos> heads = new HashSet<>();
		Deque<BlockPos> queue = new ArrayDeque<>();

		if (state(start).getBlock() instanceof PortOpeningBlock)
			openings.add(start.immutable());
		else if (!start.equals(excluded)) {
			queue.add(start.immutable());
			visited.add(start.immutable());
		}
		while (!queue.isEmpty() && visited.size() < limit) {
			BlockPos duct = queue.poll();
			BlockState ductState = state(duct);
			for (Direction direction : Direction.values()) {
				if (!opensTowards(ductState, direction, kind))
					continue;
				BlockPos next = duct.relative(direction);
				if (next.equals(excluded))
					continue;
				BlockState state = state(next);
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
			BlockPos behind = opening.relative(state(opening).getValue(PortOpeningBlock.FACING)
				.getOpposite());
			if (state(behind).getBlock() instanceof CylinderHeadBlock
				|| state(behind).getBlock() instanceof RotorHousingBlock)
				heads.add(behind.immutable());
		}
		return new Trace(visited, openings, heads);
	}

	private int portNetwork(BlockPos start, PortKind kind) {
		Map<BlockPos, Integer> index = portIndex.computeIfAbsent(kind, $ -> new HashMap<>());
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
			BlockState openingState = state(opening);
			if (PortOpeningBlock.isFlooded(level, opening, openingState))
				flooded++;
			else if (PortOpeningBlock.isOpen(level, opening, openingState))
				open++;
		}

		List<BlockPos> throttles = new ArrayList<>();
		List<BlockPos> sensors = new ArrayList<>();
		List<BlockPos> turbos = new ArrayList<>();
		List<BlockPos> superchargers = new ArrayList<>();
		List<BlockPos> intercoolers = new ArrayList<>();
		List<BlockPos> mufflers = new ArrayList<>();
		for (BlockPos pos : visited) {
			Block block = state(pos).getBlock();
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
			else if (block instanceof MufflerBlock)
				mufflers.add(pos);
		}

		// A compressor, turbine or muffler that the air can go round (another branch of the run reaches an opening without
		// it) shares the flow with whatever is on that branch; one that every path goes through takes all of it
		Set<BlockPos> parallel = new HashSet<>();
		for (List<BlockPos> stages : List.of(turbos, superchargers, mufflers))
			for (BlockPos stage : stages)
				if (!trace(start, kind, stage).openings.isEmpty())
					parallel.add(stage);

		int number = ports.size();
		ports.add(new PortNetwork(kind, open, flooded, Math.max(1, heads.size()), List.copyOf(openings), List.copyOf(throttles), List.copyOf(sensors),
			List.copyOf(turbos), List.copyOf(superchargers), List.copyOf(intercoolers), Set.copyOf(parallel),
			List.copyOf(mufflers)));
		for (BlockPos pos : visited) {
			index.put(pos, number);
			members.add(pos);
		}
		for (BlockPos pos : openings) {
			index.put(pos, number);
			members.add(pos);
		}
		return number;
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

		Direction forward = back.getOpposite();
		boolean broken = shaftBeyond(start.relative(back), back, axis)
			|| shaftBeyond(segments.getLast()
				.relative(forward), forward, axis);
		int index = camLines.size();
		camLines.add(new CamLine(axis, List.copyOf(segments), broken));
		for (BlockPos pos : segments) {
			camIndex.put(pos, index);
			members.add(pos);
		}
		return index;
	}

	private static boolean isCam(BlockState state, Axis axis) {
		return state.getBlock() instanceof CamshaftBlock && state.getValue(BlockStateProperties.AXIS) == axis;
	}

	/** Whether plain shafts run on from {@code from} to more camshaft blocks: a camshaft with a gap in it. */
	private boolean shaftBeyond(BlockPos from, Direction direction, Axis axis) {
		BlockPos cursor = from;
		for (int i = 0; i < MAX_SHAFT_WALK; i++) {
			BlockState state = state(cursor);
			if (i > 0 && isCam(state, axis))
				return true;
			if (!state.hasProperty(BlockStateProperties.AXIS) || state.getValue(BlockStateProperties.AXIS) != axis
				|| !AllBlocks.SHAFT.has(state))
				return false;
			cursor = cursor.relative(direction);
		}
		return false;
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
		} else if (block instanceof BatteryBlock || block instanceof AlternatorBlock || block instanceof SumpBlock
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
	private static BlockState loadedState(Level level, BlockPos pos) {
		return level.isLoaded(pos) ? level.getBlockState(pos) : Blocks.AIR.defaultBlockState();
	}

	private static void addHeadSeed(Level level, BlockPos pos, BlockState state, Set<BlockPos> seeds) {
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

	private static void addCylinderSeed(Level level, BlockPos pos, BlockState state, Set<BlockPos> seeds) {
		if (state.getBlock() instanceof CylinderBlock)
			addIfCrank(level, pos.relative(state.getValue(CylinderBlock.FACING)
				.getOpposite()), seeds);
	}

	private static void addIfCrank(Level level, BlockPos pos, Set<BlockPos> seeds) {
		if (level.isLoaded(pos) && level.getBlockState(pos)
			.getBlock() instanceof CrankshaftBlock)
			seeds.add(pos.immutable());
	}
}
