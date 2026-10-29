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
 * @param members    every block whose change should trigger a rescan of this engine
 * @param incomplete part of the engine is in an unloaded chunk, so it is not run
 */
public record EngineLayout(BlockPos primary, Axis axis, List<Segment> segments, List<Cylinder> cylinders,
						   List<PortNetwork> ports, List<CamLine> camLines, List<BlockPos> starters,
						   List<BlockPos> batteries, List<BlockPos> alternators, List<List<BlockPos>> galleries,
						   int flywheels, Set<BlockPos> members, boolean incomplete) {

	public record Segment(BlockPos pos, boolean isThrow, double throwAngle) {
	}

	/**
	 * @param tdcAngle    crank angle at which this piston reaches top dead centre
	 * @param camLine     index into {@link #camLines()}, or -1
	 * @param intake      index into {@link #ports()}, or -1
	 * @param exhaust     index into {@link #ports()}, or -1
	 * @param gallery     index into {@link #galleries()}, or -1
	 * @param faults      {@link CylinderFault} bits
	 * @param sparkPlug   the head has a spark plug fitted
	 */
	public record Cylinder(BlockPos pos, BlockPos throwPos, Direction bore, double tdcAngle, @Nullable BlockPos head,
						   @Nullable BlockPos lobe, int camLine, double lobeAngle, int intake, int exhaust, int gallery,
						   int faults, boolean sparkPlug) {
	}

	/**
	 * @param openings  openings whose mouths are clear
	 * @param heads     cylinder heads sharing this network (from any engine)
	 * @param outlets   positions of every opening, for exhaust effects
	 */
	public record PortNetwork(PortKind kind, int openings, int heads, List<BlockPos> outlets) {
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
