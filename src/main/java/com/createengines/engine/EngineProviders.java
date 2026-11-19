package com.createengines.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;

import com.createengines.CreateEngines;
import com.createengines.api.EngineBuild;
import com.createengines.api.EngineSourceProvider;
import com.createengines.engine.sim.TorqueSource;
import com.createengines.engine.topology.EngineLayout;
import com.createengines.engine.topology.EngineTopology;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The providers that {@link com.createengines.api.EngineExtensions} registers, and what the engine does with them. Not part
 * of the public API: only the engine and the tests call these.
 */
public final class EngineProviders {

	static final CopyOnWriteArrayList<EngineSourceProvider> PROVIDERS = new CopyOnWriteArrayList<>();

	private static final BiConsumer<BlockPos, RuntimeException> LOG = (primary, e) -> CreateEngines.LOGGER
		.error("An engine source provider failed and was skipped for the engine at {}", primary, e);
	private static volatile BiConsumer<BlockPos, RuntimeException> failures = LOG;

	private EngineProviders() {
	}

	public static void add(EngineSourceProvider provider) {
		PROVIDERS.addIfAbsent(java.util.Objects.requireNonNull(provider));
	}

	public static void remove(EngineSourceProvider provider) {
		PROVIDERS.remove(provider);
	}

	/** The class name of every provider registered now, for {@code /ce api}. */
	public static List<String> registered() {
		return PROVIDERS.stream()
			.map(provider -> provider.getClass()
				.getName())
			.toList();
	}

	/**
	 * Where a provider's failure is reported, the engine's crankshaft position and what it threw. It is logged as an error
	 * unless this is changed; a test that fails a provider on purpose takes the report here instead of in the log. Pass
	 * {@code null} to go back to logging.
	 */
	public static void reportFailuresTo(BiConsumer<BlockPos, RuntimeException> reporter) {
		failures = reporter == null ? LOG : reporter;
	}

	/** Reports a source that failed while the engine was reading it, the way a failed provider is. */
	public static void reportSourceFailure(BlockPos primary, RuntimeException e) {
		failures.accept(primary, e);
	}

	/**
	 * A block was placed or broken at {@code pos}. If a provider watches it, the engines it could touch are rebuilt at the
	 * end of the tick (the block is already gone by then, when it was a break), so the provider sees the world as it is.
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

	/**
	 * Whether a provider says this block is one of its own, by watching its default state. A blueprint may then carry it and the
	 * recorder records the ones beside an engine; any other mod's block is refused.
	 */
	public static boolean claims(net.minecraft.world.level.block.Block block) {
		if (PROVIDERS.isEmpty())
			return false;
		BlockState state = block.defaultBlockState();
		for (EngineSourceProvider provider : PROVIDERS) {
			try {
				if (provider.watches(state))
					return true;
			} catch (RuntimeException e) {
				failures.accept(BlockPos.ZERO, e);
			}
		}
		return false;
	}

	/**
	 * The blocks beside this engine's crankshaft that providers watch, within each provider's reach: its added parts, which
	 * belong in the engine's blueprint though the scanner knows nothing of them.
	 */
	public static java.util.Set<BlockPos> claimedBlocks(Level level, EngineLayout layout) {
		java.util.Set<BlockPos> found = new java.util.LinkedHashSet<>();
		if (PROVIDERS.isEmpty())
			return found;
		for (EngineSourceProvider provider : PROVIDERS) {
			try {
				int reach = Math.max(0, Math.min(provider.reach(), 8));
				for (EngineLayout.Segment segment : layout.segments())
					for (BlockPos pos : BlockPos.betweenClosed(segment.pos()
						.offset(-reach, -reach, -reach), segment.pos()
							.offset(reach, reach, reach))) {
						if (!layout.members()
							.contains(pos) && provider.watches(level.getBlockState(pos)))
							found.add(pos.immutable());
					}
			} catch (RuntimeException e) {
				failures.accept(layout.primary(), e);
			}
		}
		return found;
	}

	/** A source a provider returned, with where it breathes (null where it does not). */
	public record Provided(TorqueSource source, @org.jetbrains.annotations.Nullable BlockPos intakeAt,
						   @org.jetbrains.annotations.Nullable BlockPos exhaustAt) {
	}

	/** Gathers what every provider adds to an engine as it is built. */
	public static List<Provided> sourcesFor(Level level, EngineLayout layout) {
		if (PROVIDERS.isEmpty())
			return List.of();
		List<BlockPos> segments = layout.segments()
			.stream()
			.map(EngineLayout.Segment::pos)
			.toList();
		EngineBuild build = new EngineBuild(layout.primary(), layout.axis(), segments, layout.members());
		List<Provided> found = new ArrayList<>();
		for (EngineSourceProvider provider : PROVIDERS) {
			try {
				List<TorqueSource> given = provider.sourcesFor(level, build);
				if (given == null)
					continue;
				for (TorqueSource source : given)
					if (source != null)
						found.add(new Provided(source, provider.intakeAt(source), provider.exhaustAt(source)));
			} catch (RuntimeException e) {
				failures.accept(layout.primary(), e);
			}
		}
		return found;
	}
}
