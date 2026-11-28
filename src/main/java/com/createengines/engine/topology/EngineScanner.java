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
import com.createengines.content.airflow.PortKind;
import com.createengines.content.alternator.AlternatorBlock;
import com.createengines.content.battery.BatteryBlock;
import com.createengines.content.camshaft.CamLobeBlock;
import com.createengines.content.camshaft.CamshaftBlock;
import com.createengines.content.camshaft.CamshaftBlockEntity;
import com.createengines.content.crankshaft.CrankshaftBlock;
import com.createengines.content.crankshaft.CrankshaftBlockEntity;
import com.createengines.content.flywheel.CastFlywheelBlock;
import com.createengines.content.flywheel.DataFlywheelBlock;
import com.createengines.content.flywheel.DualMassFlywheelBlock;
import com.createengines.content.cylinder.CylinderBlock;
import com.createengines.content.head.CylinderHeadBlock;
import com.createengines.content.governor.GovernorBlock;
import com.createengines.content.rotor.RotorHousingBlock;
import com.createengines.content.starter.StarterBlock;
import com.createengines.content.radiator.RadiatorBlock;
import com.createengines.content.sump.SumpBlock;
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

	static final int MAX_SHAFT_WALK = 16;

	/** Port networks are traced apart. */
	final PortTracer tracer = new PortTracer(this);

	final Level level;
	final Set<BlockPos> members = new LinkedHashSet<>();
	/** Flywheels on the crank lines' shafts: alternators touching one belong to the engine too. */
	private final Set<BlockPos> flywheelBlocks = new LinkedHashSet<>();
	final List<PortNetwork> ports = new ArrayList<>();
	/** Known port networks by a member's position; a turbocharger is in one of each kind, so the kinds are kept apart. */
	final Map<PortKind, Map<BlockPos, Integer>> portIndex = new EnumMap<>(PortKind.class);
	private final List<CamLine> camLines = new ArrayList<>();
	private final Map<BlockPos, Integer> camIndex = new HashMap<>();
	private final Set<BlockPos> sumps = new LinkedHashSet<>();
	private final Set<BlockPos> radiators = new LinkedHashSet<>();
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

	/**
	 * Crankshaft blocks of any engine that a change at {@code pos} could affect. Used for blocks that are not yet
	 * part of a known engine, e.g. a cylinder just placed against a crank throw.
	 */
	public static Set<BlockPos> crankSeeds(Level level, BlockPos pos) {
		return CrankSeeds.crankSeeds(level, pos);
	}

	BlockState state(BlockPos pos) {
		if (!level.isLoaded(pos)) {
			incomplete = true;
			return Blocks.AIR.defaultBlockState();
		}
		return level.getBlockState(pos);
	}

	static boolean isCrank(BlockState state, Axis axis) {
		return state.getBlock() instanceof CrankshaftBlock && state.getValue(BlockStateProperties.AXIS) == axis;
	}

	/** One straight run of crankshaft blocks. Which way it turns is relative to the line the scan began on, then to the first. */
	private static final class Line {
		final Axis axis;
		final BlockPos start;
		final List<Segment> segments = new ArrayList<>();
		/** Rotational inertia of the flywheels on this line's shaft. */
		double flywheelInertia;
		/** Secondary masses of the dual-mass flywheels on this line's shaft. */
		double isolatedInertia;
		/** The same masses, each times the factor its spring-pack dial gives its resonance. */
		double isolatedResonance;
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
		double[] behind = flywheelInertia(start.relative(back), back, axis);
		double[] ahead = flywheelInertia(cursor, forward, axis);
		line.flywheelInertia = behind[0] + ahead[0];
		line.isolatedInertia = behind[1] + ahead[1];
		line.isolatedResonance = behind[2] + ahead[2];
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
		double isolatedInertia = 0;
		double isolatedResonance = 0;
		for (Line line : lines) {
			line.relativeTo(base);
			segments.addAll(line.segments);
			flywheelInertia += line.flywheelInertia;
			isolatedInertia += line.isolatedInertia;
			isolatedResonance += line.isolatedResonance;
		}
		// With every dial at its middle setting the two sums are the same numbers, so the factor is exactly 1
		double resonanceFactor = isolatedInertia > 0 ? isolatedResonance / isolatedInertia : 1;

		Set<BlockPos> batteries = new LinkedHashSet<>();
		Set<BlockPos> alternators = new LinkedHashSet<>();
		for (Segment segment : segments)
			addElectricsAround(segment.pos(), batteries, alternators);
		// A flywheel is part of the engine's shaft, so an alternator on its far side charges from it too
		for (BlockPos flywheel : flywheelBlocks)
			for (Direction direction : Direction.values()) {
				BlockPos neighbour = flywheel.relative(direction);
				if (state(neighbour).getBlock() instanceof AlternatorBlock && alternators.add(neighbour.immutable()))
					members.add(neighbour.immutable());
			}

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
		// A radiator joins by touching any block the engine is made of: its crank line, a cylinder or a head
		for (Segment segment : segments)
			addRadiatorsAround(segment.pos());
		for (CylinderDraft draft : drafts)
			addRadiatorsAround(draft.pos);
		for (BlockPos head : heads)
			addRadiatorsAround(head);
		addAlternatorChain(alternators);

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
			List.copyOf(alternators), List.copyOf(sumps),
			List.copyOf(radiators), List.copyOf(governors), List.copyOf(heads),
			turbos(), inlines(true), inlines(false), flywheelInertia, isolatedInertia, resonanceFactor, lines.size(), Set.copyOf(members), portCells(), incomplete, brokenCrank);
	}

	/** Every pipe and opening of every port network found, by the network's index. */
	private Map<BlockPos, Integer> portCells() {
		Map<BlockPos, Integer> cells = new HashMap<>();
		for (Map<BlockPos, Integer> index : portIndex.values())
			cells.putAll(index);
		return Map.copyOf(cells);
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
			if (block instanceof BatteryBlock && batteries.add(neighbour.immutable())) {
				members.add(neighbour.immutable());
				addBank(neighbour, batteries);
			} else if (block instanceof AlternatorBlock && alternators.add(neighbour.immutable())
				|| block instanceof SumpBlock && sumps.add(neighbour.immutable())
				|| block instanceof GovernorBlock && governors.add(neighbour.immutable()))
				members.add(neighbour.immutable());
		}
	}

	private void addRadiatorsAround(BlockPos pos) {
		for (Direction direction : Direction.values()) {
			BlockPos neighbour = pos.relative(direction);
			if (state(neighbour).getBlock() instanceof RadiatorBlock && radiators.add(neighbour.immutable()))
				members.add(neighbour.immutable());
		}
	}

	/**
	 * Alternators that touch one the engine reaches are connected to it, so they charge the engine's batteries too and count
	 * among its alternators. The chain can run as far as alternators keep touching.
	 */
	private void addAlternatorChain(Set<BlockPos> alternators) {
		Deque<BlockPos> queue = new ArrayDeque<>(alternators);
		while (!queue.isEmpty()) {
			BlockPos pos = queue.poll();
			for (Direction direction : Direction.values()) {
				BlockPos neighbour = pos.relative(direction);
				if (state(neighbour).getBlock() instanceof AlternatorBlock && alternators.add(neighbour.immutable())) {
					members.add(neighbour.immutable());
					queue.add(neighbour.immutable());
				}
			}
		}
	}

	/**
	 * Batteries that touch one the engine reaches are one bank, and the engine reaches all of it: capacity is the sum. A bank
	 * member in an unloaded chunk is left out rather than stopping the engine, as {@code BatteryBank} leaves it out.
	 */
	private void addBank(BlockPos start, Set<BlockPos> batteries) {
		Deque<BlockPos> queue = new ArrayDeque<>();
		queue.add(start.immutable());
		while (!queue.isEmpty()) {
			BlockPos at = queue.poll();
			for (Direction direction : Direction.values()) {
				BlockPos next = at.relative(direction);
				if (!(CrankSeeds.loadedState(level, next).getBlock() instanceof BatteryBlock) || !batteries.add(next.immutable()))
					continue;
				members.add(next.immutable());
				queue.add(next.immutable());
			}
		}
	}

	/**
	 * The inertia of the flywheels on the shaft running on from {@code from} in {@code direction}, beyond a crank line's
	 * end: what joins the crank's own, and what a dual-mass flywheel adds behind its spring pack (see {@code DualMass}).
	 */
	private double[] flywheelInertia(BlockPos from, Direction direction, Axis axis) {
		double inertia = 0;
		double isolated = 0;
		// The secondary masses again, each weighted by how far its dial moves its resonance, so the engine has one resonance
		double resonant = 0;
		BlockPos cursor = from;
		for (int i = 0; i < MAX_SHAFT_WALK; i++) {
			BlockState state = state(cursor);
			boolean onAxis = state.hasProperty(BlockStateProperties.AXIS)
				&& state.getValue(BlockStateProperties.AXIS) == axis;
			if (onAxis && AllBlocks.FLYWHEEL.has(state))
				inertia += CEConfig.FLYWHEEL_INERTIA.get();
			else if (onAxis && state.getBlock() instanceof DualMassFlywheelBlock) {
				// The spring-pack dial scales the secondary mass and where it resonates; its middle setting scales by exactly 1
				int setting = level.getBlockEntity(cursor) instanceof com.createengines.content.flywheel.CastFlywheelBlockEntity be
					? be.springPackSetting() : com.createengines.engine.sim.SpringPack.DEFAULT_SETTING;
				double secondary = CEConfig.DUAL_MASS_SECONDARY_INERTIA.get() * com.createengines.engine.sim.SpringPack.massFactor(setting);
				inertia += CEConfig.DUAL_MASS_PRIMARY_INERTIA.get();
				isolated += secondary;
				resonant += secondary * com.createengines.engine.sim.SpringPack.resonanceFactor(setting);
			} else if (onAxis && state.getBlock() instanceof DataFlywheelBlock own) {
				// A flywheel of a pack's or an addon's own: the masses its data map entry names, none if it names none
				com.createengines.engine.FlywheelSpec spec = com.createengines.registry.CEDataMaps.flywheel(own);
				inertia += spec.primaryInertia();
				isolated += spec.secondaryInertia();
				resonant += spec.secondaryInertia();
			} else if (onAxis && state.getBlock() instanceof CastFlywheelBlock)
				inertia += CEConfig.CAST_FLYWHEEL_INERTIA.get();
			else if (i > 0 && isCrank(state, axis)) {
				// A shaft in the middle of the crankshaft: the blocks either side of it are not one line
				brokenCrank = true;
				break;
			} else if (!onAxis || !AllBlocks.SHAFT.has(state))
				break;
			if (!AllBlocks.SHAFT.has(state))
				flywheelBlocks.add(cursor.immutable());
			members.add(cursor.immutable());
			cursor = cursor.relative(direction);
		}
		return new double[] {inertia, isolated, resonant};
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
		boolean injectorAssist;
		boolean ignitionCoil;
		/** Part effects of a pack's or an addon's own blocks in this cylinder, each once (see {@link #addEffect}). */
		List<String> effects = List.of();

		/** Notes the part effect a block names in the {@code part_effect} data map, if it names one this cylinder does not have yet. */
		void addEffect(Block block) {
			String effect = com.createengines.registry.CEDataMaps.partEffect(block);
			if (effect == null || effects.contains(effect))
				return;
			if (effects.isEmpty())
				effects = new ArrayList<>(2);
			effects.add(effect);
		}

		Cylinder build() {
			return new Cylinder(pos, throwPos, bore, tdcAngle, head, lobe, camLine, lobeAngle, intake, exhaust,
				faults, sparkPlug, precision, sense, lobeLeaderTdc, chamber, List.copyOf(effects), injectorAssist,
				ignitionCoil);
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
			copy.effects = effects;
			copy.injectorAssist = injectorAssist;
			copy.ignitionCoil = ignitionCoil;
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
		draft.ignitionCoil = draft.sparkPlug && state.getValue(RotorHousingBlock.HAS_COIL);
		if (state.getValue(RotorHousingBlock.FINE_INJECTOR))
			draft.precision |= PrecisionParts.INJECTOR;
		draft.precision |= state.getValue(RotorHousingBlock.COMPRESSION)
			.bits();
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
			PortKind kind = PortTracer.portKindFacing(state(neighbour), face);
			if (kind == PortKind.INTAKE && draft.intake < 0)
				draft.intake = tracer.portNetwork(neighbour, kind);
			else if (kind == PortKind.EXHAUST && draft.exhaust < 0)
				draft.exhaust = tracer.portNetwork(neighbour, kind);
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
		draft.addEffect(state.getBlock());
		if (state.getValue(CylinderBlock.FORGED_PISTON))
			draft.precision |= PrecisionParts.PISTON;
		draft.precision |= state.getValue(CylinderBlock.COMPRESSION)
			.bits();
		draft.precision = PrecisionParts.withRings(draft.precision, state.getValue(CylinderBlock.RINGS));

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
		// An assist belongs to an injector: a head that somehow has one without (a command, a hand-edited structure) has none
		draft.injectorAssist = head.getValue(CylinderHeadBlock.HAS_INJECTOR) && head.getValue(CylinderHeadBlock.HAS_ASSIST);
		// A coil sits on a plug in the same way
		draft.ignitionCoil = draft.sparkPlug && head.getValue(CylinderHeadBlock.HAS_COIL);
		if (((CylinderHeadBlock) head.getBlock()).precision())
			draft.precision |= PrecisionParts.HEAD;
		draft.addEffect(head.getBlock());
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
			PortKind kind = PortTracer.portKindFacing(neighbourState, face);
			if (kind == PortKind.INTAKE && draft.intake < 0)
				draft.intake = tracer.portNetwork(neighbour, kind);
			else if (kind == PortKind.EXHAUST && draft.exhaust < 0)
				draft.exhaust = tracer.portNetwork(neighbour, kind);
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
		// On a moving structure the sea is not in the plot: the world where the structure is decides
		return com.createengines.compat.sable.SableCompat.wetInWorld(level, pos);
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

	static boolean isCam(BlockState state, Axis axis) {
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
}
