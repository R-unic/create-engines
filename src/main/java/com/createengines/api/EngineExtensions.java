package com.createengines.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;

import com.createengines.CreateEngines;
import com.createengines.content.crankshaft.CrankshaftBlockEntity;
import com.createengines.engine.sim.TorqueSource;
import com.createengines.engine.topology.EngineLayout;
import com.createengines.engine.topology.EngineScanner;
import com.createengines.engine.topology.EngineTopology;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Where packs and addons plug in what drives an engine. <b>Preview:</b> the API may still change until 2.0.
 * <p>
 * Register providers during mod setup (for example in {@code FMLCommonSetupEvent}'s {@code enqueueWork}); they apply to
 * engines built afterwards, so one registered late is picked up when an engine next rebuilds.
 */
public final class EngineExtensions {

	private static final CopyOnWriteArrayList<EngineSourceProvider> PROVIDERS = new CopyOnWriteArrayList<>();

	private static final BiConsumer<BlockPos, RuntimeException> LOG = (primary, e) -> CreateEngines.LOGGER
		.error("An engine source provider failed and was skipped for the engine at {}", primary, e);
	private static volatile BiConsumer<BlockPos, RuntimeException> failures = LOG;

	private EngineExtensions() {
	}

	/**
	 * Where a provider's failure is reported, the engine's crankshaft position and what it threw. It is logged as an error
	 * unless this is changed; a test that fails a provider on purpose takes the report here instead of in the log. Pass
	 * {@code null} to go back to logging.
	 */
	public static void reportFailuresTo(BiConsumer<BlockPos, RuntimeException> reporter) {
		failures = reporter == null ? LOG : reporter;
	}

	/** Adds a provider. Registering the same one twice has no effect. */
	public static void register(EngineSourceProvider provider) {
		PROVIDERS.addIfAbsent(java.util.Objects.requireNonNull(provider));
	}

	/** Removes a provider; it applies to no engine built from now on. */
	public static void unregister(EngineSourceProvider provider) {
		PROVIDERS.remove(provider);
	}

	/**
	 * Tells the engine that the block at {@code pos} touches that something changed for its
	 * providers, so the engine is built again (keeping its speed, oil and heat) and they are asked afresh. Call it when
	 * a block of your own beside an engine is placed, broken or changes what it supplies. Does nothing on a client or
	 * when no engine is there.
	 */
	public static void rebuild(Level level, BlockPos pos) {
		if (level.isClientSide())
			return;
		Set<BlockPos> seeds = EngineScanner.crankSeeds(level, pos);
		if (seeds.isEmpty())
			return;
		EngineLayout layout = EngineScanner.scan(level, seeds.iterator()
			.next());
		if (layout != null && level.getBlockEntity(layout.primary()) instanceof CrankshaftBlockEntity crank)
			crank.rebuildEngine();
	}

	/** Not for callers: reports a source that failed while the engine was reading it, the way a failed provider is. */
	public static void reportSourceFailure(BlockPos primary, RuntimeException e) {
		failures.accept(primary, e);
	}

	/**
	 * Not for callers: a block was placed or broken at {@code pos}. If a provider watches it, the engines it could touch
	 * are rebuilt at the end of the tick (the block is already gone by then, when it was a break), so the provider sees the
	 * world as it is.
	 */
	public static void blockChanged(Level level, BlockPos pos, BlockState state) {
		if (level.isClientSide() || PROVIDERS.isEmpty())
			return;
		int reach = -1;
		for (EngineSourceProvider provider : PROVIDERS) {
			try {
				if (provider.watches(state))
					reach = Math.max(reach, provider.reach());
			} catch (RuntimeException e) {
				failures.accept(pos, e);
			}
		}
		if (reach >= 0)
			EngineTopology.rebuildNear(level, pos, reach);
	}

	/** Not for callers: gathers what every provider adds to an engine as it is built. */
	public static List<TorqueSource> sourcesFor(Level level, EngineLayout layout) {
		if (PROVIDERS.isEmpty())
			return List.of();
		List<BlockPos> segments = layout.segments()
			.stream()
			.map(EngineLayout.Segment::pos)
			.toList();
		EngineBuild build = new EngineBuild(layout.primary(), layout.axis(), segments, layout.members());
		List<TorqueSource> found = new ArrayList<>();
		for (EngineSourceProvider provider : PROVIDERS) {
			try {
				List<TorqueSource> given = provider.sourcesFor(level, build);
				if (given == null)
					continue;
				for (TorqueSource source : given)
					if (source != null)
						found.add(source);
			} catch (RuntimeException e) {
				failures.accept(layout.primary(), e);
			}
		}
		return found;
	}
}
