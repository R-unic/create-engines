package com.createengines.compat.sable;

import dev.ryanhcode.sable.companion.SableCompanion;

import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** The only class that names Sable's types; {@link SableCompat} reaches it only once the mod is known to be there. */
final class SableBridge {

	private SableBridge() {
	}

	static Vec3 project(Level level, Vec3 position) {
		return SableCompanion.INSTANCE.projectOutOfSubLevel(level, position);
	}
}
