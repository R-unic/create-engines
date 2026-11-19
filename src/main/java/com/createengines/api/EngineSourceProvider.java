package com.createengines.api;

import java.util.List;

import com.createengines.engine.sim.TorqueSource;

import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * How a pack or an addon puts something besides cylinders on an engine's crank, such as a turbine. Register one with
 * {@link EngineExtensions#register}. <b>Frozen</b> at API 1.0 (see {@link ApiVersion}): nothing here is removed or changed before the next major version, and additions are minor versions.
 * <p>
 * The mod ships no provider of its own. A provider is asked once whenever an engine is built or rebuilt, on the server
 * thread, and again when {@link EngineExtensions#rebuild} is called for a block of the engine. It looks at the world
 * around the crankshaft, which is its own business (a block of its own beside a crank segment, say), and returns the
 * sources that engine should have, new each time: the old ones are dropped with the old build, so a source must
 * not be shared between builds. A provider that throws or returns nulls is logged and skipped, and never stops an engine.
 */
@FunctionalInterface
public interface EngineSourceProvider {

	/** The sources to add to the engine described by {@code build}; empty for most engines. */
	List<TorqueSource> sourcesFor(Level level, EngineBuild build);

	/**
	 * Whether {@code state} is one of this provider's blocks. When one is placed or broken within {@link #reach} of an
	 * engine's crankshaft, that engine is rebuilt and this provider is asked again, so the pack never has to call
	 * {@link EngineExtensions#rebuild} for its own blocks. The default watches nothing.
	 */
	default boolean watches(BlockState state) {
		return false;
	}

	/** How far, in blocks along each axis from any crank segment, a watched block counts as touching the engine. */
	default int reach() {
		return 2;
	}
}
