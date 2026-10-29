package com.createengines.content.airflow;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A block an intake or exhaust {@link ManifoldBlock pipe} can attach to. A pipe joins whatever accepts it, and the
 * engine scanner follows pipes from port to port, so anything that implements this and sits in the line (a
 * turbocharger, an intercooler, a silencer) becomes part of the airflow without the pipes or the scanner knowing
 * about it.
 */
public interface AirflowPort {

	/**
	 * Whether a pipe carrying {@code kind} may connect to this block's {@code side} face.
	 *
	 * @param side the face of this block that the pipe is touching
	 */
	boolean acceptsPipe(BlockState state, Direction side, PortKind kind);
}
