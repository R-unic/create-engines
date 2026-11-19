package com.createengines.api;

import java.util.List;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction.Axis;

/**
 * What an {@link EngineSourceProvider} is told about an engine that is being built. <b>Frozen</b> at API 1.0 (see {@link ApiVersion}): nothing here is removed or changed before the next major version, and additions are minor versions.
 *
 * @param primary  the crankshaft block that hosts the engine
 * @param axis     the axis the crankshaft runs along
 * @param segments every block of the crankshaft, in order, the first crank line's first
 * @param members  every block of the engine this mod has found; a change to one rebuilds the engine
 */
public record EngineBuild(BlockPos primary, Axis axis, List<BlockPos> segments, Set<BlockPos> members) {
}
