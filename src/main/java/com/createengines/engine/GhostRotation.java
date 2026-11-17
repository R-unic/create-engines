package com.createengines.engine;

import java.util.ArrayList;
import java.util.List;

import com.simibubi.create.content.kinetics.KineticNetwork;
import com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;

import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

/**
 * Brings a kinetic network to rest when it is turning with nothing driving it.
 * <p>
 * Create gives every block of a network that is not a generator a source, the neighbour it takes its speed from. When
 * gearing closes into a ring (two crank lines joined by shafts or chain drives at both ends) the sources can end up
 * pointing round the ring, and when the last real generator stops nothing is left to say so: each block goes on turning
 * on its neighbour's speed for good, at whatever it was doing, and the saved world keeps it going after a restart. Breaking
 * the generators does not help, since none of them is the source of anything any more.
 * <p>
 * A network that has no generator publishing a speed, yet whose blocks are turning, is in that state. It is left alone for
 * a couple of checks first, so that a network in the middle of changing hands is not mistaken for one.
 */
public final class GhostRotation {

	/** Ticks between looks. */
	public static final int INTERVAL = 20;
	/** Looks in a row that must find a ghost before it is released. */
	private static final int STRIKES = 2;

	private int strikes;

	/** Looks at {@code be}'s network if it is time to; call every tick from the server side of a block entity. */
	public void tick(KineticBlockEntity be) {
		Level level = be.getLevel();
		if (level == null || level.isClientSide || (level.getGameTime() + be.getBlockPos()
			.asLong()) % INTERVAL != 0)
			return;
		if (!isGhost(be)) {
			strikes = 0;
			return;
		}
		if (++strikes >= STRIKES) {
			strikes = 0;
			release(be);
		}
	}

	/** Whether {@code be} turns in a network that has no generator running. */
	static boolean isGhost(KineticBlockEntity be) {
		if (!be.hasNetwork() || be.getTheoreticalSpeed() == 0)
			return false;
		KineticNetwork network = be.getOrCreateNetwork();
		for (KineticBlockEntity source : network.sources.keySet())
			// Anything that is not a Create generator is taken to be a real source: it is not ours to say it is not
			if (!(source instanceof GeneratingKineticBlockEntity generator) || generator.getGeneratedSpeed() != 0)
				return false;
		// A generator in a chunk that is not loaded is not in the list: the network is only known to have none if all of it is here
		Level level = be.getLevel();
		for (KineticBlockEntity member : network.members.keySet())
			for (Direction direction : Direction.values())
				if (!level.hasChunkAt(member.getBlockPos()
					.relative(direction)))
					return false;
		return true;
	}

	/** Stops every block of {@code be}'s network, as Create does for a block whose last source has gone. */
	static void release(KineticBlockEntity be) {
		List<KineticBlockEntity> members = new ArrayList<>(be.getOrCreateNetwork().members.keySet());
		if (!members.contains(be))
			members.add(be);
		for (KineticBlockEntity member : members) {
			member.detachKinetics();
			member.setSpeed(0);
			member.source = null;
			member.setNetwork(null);
			member.sendData();
		}
	}
}
