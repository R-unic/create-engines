package com.createengines.compat.kubejs;

import org.jetbrains.annotations.Nullable;

import com.createengines.api.EngineApi;
import com.createengines.api.EngineView;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * The {@code CreateEngines} global in scripts: {@code CreateEngines.engineAt(level, pos)} gives a read-only look at the engine
 * a block belongs to (its {@code status()}, {@code rpm()}, {@code temperature()} and the rest of {@link EngineView}), or
 * {@code null} if the block is not part of a built engine. It changes nothing.
 */
public final class CreateEnginesBinding {

	private CreateEnginesBinding() {
	}

	public static @Nullable EngineView engineAt(Level level, BlockPos pos) {
		return EngineApi.viewAt(level, pos)
			.orElse(null);
	}
}
