package com.createengines.api;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import com.createengines.content.crankshaft.CrankshaftBlockEntity;
import com.createengines.engine.EngineSummary;
import com.createengines.engine.topology.EngineLayout;
import com.createengines.engine.topology.EngineScanner;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * The public, read-only way to ask about an engine. <b>Preview:</b> the API may still change until 2.0, where it is frozen
 * under its own version. Nothing here changes an engine.
 */
public final class EngineApi {

	private EngineApi() {
	}

	/**
	 * The engine that the block at {@code pos} belongs to (any block of it: a crankshaft, cylinder, head or starter), or
	 * empty if it is not part of a built engine or the engine has not reported yet.
	 */
	public static Optional<EngineView> viewAt(Level level, BlockPos pos) {
		Set<BlockPos> seeds = EngineScanner.crankSeeds(level, pos);
		if (seeds.isEmpty())
			return Optional.empty();
		EngineLayout layout = EngineScanner.scan(level, seeds.iterator()
			.next());
		if (layout == null || !(level.getBlockEntity(layout.primary()) instanceof CrankshaftBlockEntity crank))
			return Optional.empty();
		EngineSummary summary = crank.summary();
		if (summary == null)
			return Optional.empty();
		return Optional.of(new EngineView(EngineStatus.valueOf(summary.state()
			.name()), summary.rpm(), summary.availableTorque(), summary.loadTorque(), summary.cylinders(), summary.firing(),
			summary.temperature(), summary.condition(), summary.boost(), lower(summary.health()), lower(summary.cause())));
	}

	private static String lower(Enum<?> value) {
		return value.name()
			.toLowerCase(Locale.ROOT);
	}
}
