package com.createengines.engine.topology;

import java.util.List;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import com.createengines.content.airflow.PortKind;
import com.createengines.engine.sim.IntakeAir;
import com.createengines.engine.sim.PortFlow;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;

/**
 * An immutable snapshot of one engine as built: what is connected to what. Produced by {@link EngineScanner},
 * rebuilt only when one of its blocks changes.
 *
 * @param primary    the crankshaft block that hosts the simulation and acts as the Create generator
 * @param batteries  batteries touching the crankshaft or a cylinder head, which fire every spark plug
 * @param alternators alternators touching the crankshaft or a cylinder head, which charge those batteries
 * @param sumps      oil pans touching the crankshaft or a cylinder head, which oil the whole engine
 * @param radiators  radiators touching the crankshaft, a cylinder or a cylinder head, which cool the whole engine
 * @param governors  mechanical governors touching the crankshaft or a cylinder head, which hold a set speed
 * @param turbos     turbochargers on the engine's port networks
 * @param superchargers superchargers in the engine's intake networks
 * @param intercoolers intercoolers in the engine's intake networks
 * @param heads      every cylinder head on the crankshaft; their tanks form one fuel and oil gallery for the engine
 * @param lines      how many crank lines the engine has; more than one where cranks are geared together one to one, in
 *                   which case {@link #segments()} lists them all, the first line's first
 * @param flywheelInertia inertia the flywheels on the crank line add to the crank, including a dual-mass flywheel's primary mass
 * @param isolatedInertia the secondary masses of dual-mass flywheels on the line, which smooth the ripple but do not turn with the crank's inertia
 * @param isolatedResonanceFactor what the server's dual-mass resonance is multiplied by for this engine: the spring-pack dials of its
 *                   dual-mass flywheels, averaged by the secondary mass each isolates; 1 with every dial at its middle setting or none
 * @param members    every block whose change should trigger a rescan of this engine
 * @param portCells  every cell of every port network (pipes and openings) with the index into {@link #ports()} of the network it is
 *                   in, so a block beside a pipe can be told which network it breathes from ({@link #networkAt})
 * @param incomplete part of the engine is in an unloaded chunk, so it is not run
 * @param broken     a plain shaft sits between two blocks of the crankshaft, which has to be one unbroken line, so
 *                   it is not run
 */
public record EngineLayout(BlockPos primary, Axis axis, List<Segment> segments, List<Cylinder> cylinders,
						   List<PortNetwork> ports, List<CamLine> camLines, List<BlockPos> starters,
						   List<BlockPos> batteries, List<BlockPos> alternators, List<BlockPos> sumps,
						   List<BlockPos> radiators,
						   List<BlockPos> governors, List<BlockPos> heads,
						   List<Turbo> turbos, List<Inline> superchargers, List<Inline> intercoolers, double flywheelInertia,
							   double isolatedInertia, double isolatedResonanceFactor,
							   int lines, Set<BlockPos> members, java.util.Map<BlockPos, Integer> portCells, boolean incomplete,
							   boolean broken) {

	/**
	 * The index into {@link #ports()} of the network of the given kind that {@code pos} is in or touches (a face of it is
	 * enough), or -1 if there is none. It is how a source a pack added is joined to the engine's air.
	 */
	public int networkAt(@Nullable BlockPos pos, PortKind kind) {
		if (pos == null)
			return -1;
		Integer here = portCells.get(pos);
		if (here != null && ports.get(here).kind() == kind)
			return here;
		for (Direction side : Direction.values()) {
			Integer next = portCells.get(pos.relative(side));
			if (next != null && ports.get(next).kind() == kind)
				return next;
		}
		return -1;
	}

	/**
	 * A turbocharger joining one of this engine's exhaust networks (its turbine) to one of its intake networks (its
	 * compressor). Either index is -1 if that side is not on this engine.
	 *
	 * @param intake  index into {@link #ports()} of the intake network the compressor feeds
	 * @param exhaust index into {@link #ports()} of the exhaust network that drives the turbine
	 * @param intakeParallel  the air can reach the heads without going through the compressor, so it shares the flow
	 * @param exhaustParallel the exhaust can leave without going through the turbine, so it shares the flow
	 */
	public record Turbo(BlockPos pos, int intake, int exhaust, boolean intakeParallel, boolean exhaustParallel) {
	}

	/**
	 * A supercharger or an intercooler, which sits in one of this engine's intake networks.
	 *
	 * @param intake index into {@link #ports()} of the intake network it is in
	 * @param parallel the air can reach the heads without going through it, so it shares the flow
	 */
	public record Inline(BlockPos pos, int intake, boolean parallel) {
	}

	/**
	 * @param throwAngle this block's own throw angle, as set on it
	 * @param sense      +1 if this block turns the way the engine's first crank line does, -1 if gearing turns it the other way
	 */
	public record Segment(BlockPos pos, boolean isThrow, double throwAngle, int sense) {
	}

	/**
	 * @param tdcAngle    crank angle at which this piston reaches top dead centre, in the engine's frame: for a line that is
	 *                    geared to turn the other way this is the mirror of the angle on the line's own crank
	 * @param camLine     index into {@link #camLines()}, or -1
	 * @param intake      index into {@link #ports()}, or -1
	 * @param exhaust     index into {@link #ports()}, or -1
	 * @param faults      {@link CylinderFault} bits
	 * @param sparkPlug   the head has a spark plug fitted
	 * @param precision   {@link PrecisionParts} bits for the precision parts this cylinder is built from
	 * @param sense       the {@link Segment#sense()} of the throw it sits on
	 * @param lobeLeaderTdc crank angle at which the first working cylinder on this cylinder's cam lobe reaches top dead
	 *                    centre, or NaN if this is that cylinder or the lobe is its alone: a lobe times the first cylinder
	 *                    by its angle and the others at the same offset from their own top dead centres
	 * @param chamber     which face (0 to 2) of a rotor this is, or -1 for a piston cylinder. A rotor housing is its own
	 *                    throw, head and cylinder: its three chambers all have its position, and {@code tdcAngle} is the
	 *                    shaft angle at which its eccentric points at the housing's mark
	 * @param injectorAssist the head has an electric injector assist fitted (only ever counted with an injector); it draws charge
	 *                    from the engine's batteries, so it is not a precision part and changes no spec
	 * @param ignitionCoil the head has a high-energy ignition coil fitted (only ever counted with a spark plug); like the assist it draws
	 *                    charge from the engine's batteries, so it is not a precision part and changes no spec
	 * @param effects     the ids of the part effects (data files of a pack or an addon, see {@code PartEffects}) that the blocks
	 *                    of this cylinder add to the ones its {@code precision} bits name; empty for every block this mod makes
	 */
	public record Cylinder(BlockPos pos, BlockPos throwPos, Direction bore, double tdcAngle, @Nullable BlockPos head,
						   @Nullable BlockPos lobe, int camLine, double lobeAngle, int intake, int exhaust,
						   int faults, boolean sparkPlug, int precision, int sense, double lobeLeaderTdc, int chamber,
						   List<String> effects, boolean injectorAssist, boolean ignitionCoil) {

		/** A cylinder built from the parts this mod ships only: no part of a pack's or an addon's own. */
		public Cylinder(BlockPos pos, BlockPos throwPos, Direction bore, double tdcAngle, @Nullable BlockPos head,
						@Nullable BlockPos lobe, int camLine, double lobeAngle, int intake, int exhaust, int faults,
						boolean sparkPlug, int precision, int sense, double lobeLeaderTdc, int chamber) {
			this(pos, throwPos, bore, tdcAngle, head, lobe, camLine, lobeAngle, intake, exhaust, faults, sparkPlug, precision,
				sense, lobeLeaderTdc, chamber, List.of(), false, false);
		}

		/** Whether this is one chamber of a Wankel rotor rather than a piston in a bore. */
		public boolean rotary() {
			return chamber >= 0;
		}
	}

	/**
	 * @param openings  openings whose mouths are clear
	 * @param flooded   openings whose mouths are under a fluid, which count as shut
	 * @param heads    cylinder heads sharing this network (from any engine)
	 * @param outlets   positions of every opening, for exhaust effects
	 * @param throttles throttle bodies in the line, whose openings are read live
	 * @param sensors   MAF sensors and heat exchangers in the line, which are told what the cylinders on it are doing
	 * @param turbos    turbochargers the line runs through: a compressor if it is an intake, a turbine if an exhaust
	 * @param superchargers superchargers in an intake line, whose blowers push all of its air
	 * @param intercoolers  intercoolers in an intake line, which cool the charge the compressors heat
	 * @param parallel      turbochargers, superchargers and mufflers the run can go round, which share its flow with the
	 *                      other branches; any other is in series, and all of the flow goes through it
	 * @param mufflers      mufflers in an exhaust line, which quiet what the engine sounds like
	 * @param traps         soot traps in an exhaust line, which catch the soot of a rich engine
	 * @param run           how many blocks the gas goes through from a head to the nearest opening, the opening included,
	 *                      averaged over the heads: how long a run it is, which sets how its exhaust rings (only the sound)
	 */
	public record PortNetwork(PortKind kind, int openings, int flooded, int heads, List<BlockPos> outlets,
							  List<BlockPos> throttles, List<BlockPos> sensors, List<BlockPos> turbos,
							  List<BlockPos> superchargers, List<BlockPos> intercoolers, Set<BlockPos> parallel,
							  List<BlockPos> mufflers, List<BlockPos> traps, int run, List<BlockPos> airFilters) {

		/** A line with no air filter in it. */
		public PortNetwork(PortKind kind, int openings, int flooded, int heads, List<BlockPos> outlets,
						   List<BlockPos> throttles, List<BlockPos> sensors, List<BlockPos> turbos,
						   List<BlockPos> superchargers, List<BlockPos> intercoolers, Set<BlockPos> parallel,
						   List<BlockPos> mufflers, List<BlockPos> traps, int run) {
			this(kind, openings, flooded, heads, outlets, throttles, sensors, turbos, superchargers, intercoolers, parallel, mufflers,
				traps, run, List.of());
		}

		/** What the air filters in this line are worth: nothing with none, less with a branch that goes round every one. */
		public IntakeAir.Filter filtering() {
			if (airFilters.isEmpty())
				return IntakeAir.Filter.NONE;
			return parallel.containsAll(airFilters) ? IntakeAir.Filter.BYPASSED : IntakeAir.Filter.INLINE;
		}

		/** A line whose length nobody measured: as long as its openings. */
		public PortNetwork(PortKind kind, int openings, int flooded, int heads, List<BlockPos> outlets,
						   List<BlockPos> throttles, List<BlockPos> sensors, List<BlockPos> turbos,
						   List<BlockPos> superchargers, List<BlockPos> intercoolers, Set<BlockPos> parallel,
						   List<BlockPos> mufflers, List<BlockPos> traps) {
			this(kind, openings, flooded, heads, outlets, throttles, sensors, turbos, superchargers, intercoolers, parallel, mufflers,
				traps, Math.max(1, outlets.size()));
		}

		/** Share of full breathing each head gets: diminishing returns per opening, crowded openings worth half ({@link PortFlow}). */
		public double flowFactor() {
			if (openings == 0 || heads == 0)
				return 0;
			return PortFlow.factor(PortFlow.effective(openings, outlets.size(), crowdedOutlets()), heads);
		}

		/** How many of the openings have another one right beside them (touching, corners included). */
		public int crowdedOutlets() {
			int crowded = 0;
			for (int i = 0; i < outlets.size(); i++) {
				BlockPos a = outlets.get(i);
				for (int j = 0; j < outlets.size(); j++) {
					BlockPos b = outlets.get(j);
					if (i != j && Math.abs(a.getX() - b.getX()) <= 1 && Math.abs(a.getY() - b.getY()) <= 1
						&& Math.abs(a.getZ() - b.getZ()) <= 1) {
						crowded++;
						break;
					}
				}
			}
			return crowded;
		}
	}

	/** @param broken a plain shaft sits between two blocks of the camshaft, which has to be one unbroken line */
	public record CamLine(Axis axis, List<BlockPos> segments, boolean broken) {
	}
}
