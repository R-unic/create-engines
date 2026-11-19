package com.createengines.api;

import java.util.Set;

import com.createengines.engine.EngineProviders;
import com.createengines.content.crankshaft.CrankshaftBlockEntity;
import com.createengines.engine.topology.EngineLayout;
import com.createengines.engine.topology.EngineScanner;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * Where packs and addons plug in what drives an engine. <b>Frozen</b> at API 1.0 (see {@link ApiVersion}): nothing here is removed or changed before the next major version, and additions are minor versions.
 * <p>
 * Register providers during mod setup (for example in {@code FMLCommonSetupEvent}'s {@code enqueueWork}); they apply to
 * engines built afterwards, so one registered late is picked up when an engine next rebuilds.
 */
public final class EngineExtensions {

	private EngineExtensions() {
	}

	/** Adds a provider. Registering the same one twice has no effect. */
	public static void register(EngineSourceProvider provider) {
		EngineProviders.add(provider);
	}

	/** Removes a provider; it applies to no engine built from now on. */
	public static void unregister(EngineSourceProvider provider) {
		EngineProviders.remove(provider);
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
}
