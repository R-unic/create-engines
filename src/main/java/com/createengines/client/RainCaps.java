package com.createengines.client;

import java.util.HashMap;
import java.util.Map;

import com.createengines.content.effects.RainCapFlap;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * The flaps of the rain caps this client can see. An exhaust stroke that leaves a pipe with a cap on its end calls
 * {@link #pulse}, and the cap's renderer asks {@link #degrees} how far open the flap is. Only ever touched on the
 * client thread.
 */
public final class RainCaps {

	private static final Map<Long, RainCapFlap> FLAPS = new HashMap<>();

	private RainCaps() {
	}

	/** An exhaust pulse out of the pipe that the cap at {@code cap} sits on. */
	public static void pulse(Level level, BlockPos cap) {
		FLAPS.computeIfAbsent(cap.asLong(), key -> new RainCapFlap())
			.pulse(level.getGameTime());
	}

	/** How far open the flap of the cap at {@code cap} stands, in degrees. */
	public static float degrees(BlockPos cap, double now) {
		RainCapFlap flap = FLAPS.get(cap.asLong());
		return flap == null ? 0 : (float) flap.degrees(now);
	}

	/** Every flap, for when the player leaves a world. */
	public static void clear() {
		FLAPS.clear();
	}

	/** A cap that is gone no longer needs its flap. */
	public static void forget(BlockPos cap) {
		FLAPS.remove(cap.asLong());
	}
}
