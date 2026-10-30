package com.createengines.engine.topology;

import java.util.List;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import com.createengines.content.airflow.PortKind;

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
 * @param limiters   rev limiters touching the crankshaft or a cylinder head, which cut fuel at their set speed
 * @param governors  mechanical governors touching the crankshaft or a cylinder head, which hold a set speed
 * @param turbos     turbochargers on the engine's port networks
 * @param superchargers superchargers in the engine's intake networks
 * @param intercoolers intercoolers in the engine's intake networks
 * @param heads      every cylinder head on the crankshaft; their tanks form one fuel and oil gallery for the engine
 * @param lines      how many crank lines the engine has; more than one where cranks are geared together one to one, in
 *                   which case {@link #segments()} lists them all, the first line's first
 * @param members    every block whose change should trigger a rescan of this engine
 * @param incomplete part of the engine is in an unloaded chunk, so it is not run
 */
public record EngineLayout(BlockPos primary, Axis axis, List<Segment> segments, List<Cylinder> cylinders,
						   List<PortNetwork> ports, List<CamLine> camLines, List<BlockPos> starters,
						   List<BlockPos> batteries, List<BlockPos> alternators, List<BlockPos> sumps,
						   List<BlockPos> limiters, List<BlockPos> governors, List<BlockPos> heads,
						   List<Turbo> turbos, List<Inline> superchargers, List<Inline> intercoolers, int flywheels,
							   int lines, Set<BlockPos> members, boolean incomplete) {

	/**
	 * A turbocharger joining one of this engine's exhaust networks (its turbine) to one of its intake networks (its
	 * compressor). Either index is -1 if that side is not on this engine.
	 *
	 * @param intake  index into {@link #ports()} of the intake network the compressor feeds
	 * @param exhaust index into {@link #ports()} of the exhaust network that drives the turbine
	 */
	public record Turbo(BlockPos pos, int intake, int exhaust) {
	}

	/**
	 * A supercharger or an intercooler, which sits in one of this engine's intake networks.
	 *
	 * @param intake index into {@link #ports()} of the intake network it is in
	 */
	public record Inline(BlockPos pos, int intake) {
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
	 */
	public record Cylinder(BlockPos pos, BlockPos throwPos, Direction bore, double tdcAngle, @Nullable BlockPos head,
						   @Nullable BlockPos lobe, int camLine, double lobeAngle, int intake, int exhaust,
						   int faults, boolean sparkPlug, int precision, int sense) {
	}

	/**
	 * @param openings  openings whose mouths are clear
	 * @param heads     cylinder heads sharing this network (from any engine)
	 * @param outlets   positions of every opening, for exhaust effects
	 * @param throttles throttle bodies in the line, whose openings are read live
	 * @param sensors   MAF or lambda sensors in the line, which are told what the cylinders on it are doing
	 * @param turbos    turbochargers the line runs through: a compressor if it is an intake, a turbine if an exhaust
	 * @param superchargers superchargers in an intake line, whose blowers push all of its air
	 * @param intercoolers  intercoolers in an intake line, which cool the charge the compressors heat
	 */
	public record PortNetwork(PortKind kind, int openings, int heads, List<BlockPos> outlets,
							  List<BlockPos> throttles, List<BlockPos> sensors, List<BlockPos> turbos,
							  List<BlockPos> superchargers, List<BlockPos> intercoolers) {
		/** Share of full breathing each head gets: an opening comfortably serves two cylinders. */
		public double flowFactor() {
			if (openings == 0 || heads == 0)
				return 0;
			return Math.min(1, 2.0 * openings / heads);
		}
	}

	public record CamLine(Axis axis, List<BlockPos> segments) {
	}
}
