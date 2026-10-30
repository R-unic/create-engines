package com.createengines.advancement;

import java.util.Collection;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/** Hands out advancements for things that happen in the world. */
public final class Achievements {

	private Achievements() {
	}

	/**
	 * Fires {@code milestone} for the one player who last placed or used one of {@code blocks}, the machine's
	 * {@linkplain Operators operator}. Nobody else, however close they stand, and nobody at all if that player is
	 * offline or no one has touched the machine.
	 */
	public static void award(Level level, Collection<BlockPos> blocks, Milestone milestone) {
		if (!(level instanceof ServerLevel server))
			return;
		UUID operator = Operators.latest(level, blocks);
		if (operator == null)
			return;
		ServerPlayer player = server.getServer()
			.getPlayerList()
			.getPlayer(operator);
		if (player != null)
			CETriggers.EVENT.get()
				.trigger(player, milestone);
	}
}
