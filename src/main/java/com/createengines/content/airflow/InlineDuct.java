package com.createengines.content.airflow;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/**
 * An {@link AirflowPort} that sits in a pipe run as a junction: the air goes in at one of the faces that accept its
 * kind and out of another, so everything on either side is one run to the engine scanner. A turbocharger, a
 * supercharger and an intercooler are all inline ducts.
 */
public interface InlineDuct extends AirflowPort {

	/**
	 * The kind of port a cylinder head bolted straight to {@code face} of this block sees, or null if the face needs a
	 * pipe to say which kind it is.
	 */
	@Nullable PortKind kindFacingHead(BlockState state, Direction face);
}
