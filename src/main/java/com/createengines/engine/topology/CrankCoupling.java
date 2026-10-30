package com.createengines.engine.topology;

import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import com.createengines.CreateEngines;
import com.createengines.content.crankshaft.CrankshaftBlock;
import com.simibubi.create.content.kinetics.RotationPropagator;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * Finds the crankshafts that are geared to one another, one to one, so that they are one engine.
 * <p>
 * Create ties every block of a kinetic network to the others by a fixed speed ratio, set by whatever gearing lies between
 * them. Two crank lines with the same speed (ignoring which way they turn) are held in step by that gearing exactly as
 * the cranks of one multi-bank engine are held by a timing gear, so they are given a single rotor. This walks the same
 * connections Create itself propagates rotation along, from {@link RotationPropagator}, so every kind of gearing it knows
 * (cogwheels, gearboxes, chain drives, belts, bevel gears) couples cranks without this mod having to know about it, and
 * it works for an engine that is standing still.
 */
final class CrankCoupling {

	private static final Logger LOGGER = CreateEngines.LOGGER;
	/** How close to exactly one a speed ratio has to be to count. */
	private static final float TOLERANCE = 1e-3f;

	/** Create keeps these private; they are called by reflection, and coupling is switched off if they are not there. */
	private static final @Nullable Method NEIGHBOURS = method("getConnectedNeighbours", KineticBlockEntity.class);
	private static final @Nullable Method MODIFIER =
		method("getRotationSpeedModifier", KineticBlockEntity.class, KineticBlockEntity.class);

	private CrankCoupling() {
	}

	private static @Nullable Method method(String name, Class<?>... parameters) {
		try {
			Method method = RotationPropagator.class.getDeclaredMethod(name, parameters);
			method.setAccessible(true);
			return method;
		} catch (ReflectiveOperationException | RuntimeException e) {
			LOGGER.warn("Create's RotationPropagator.{} is not available ({}); coupled crankshafts are switched off", name, e.toString());
			return null;
		}
	}

	/**
	 * Every crankshaft block outside {@code from} that turns at the same speed as the blocks in it, and whether it turns
	 * the same way (+1) or the opposite way (-1).
	 *
	 * @param from  the blocks of a crank line, which turn at the reference speed
	 * @param limit most kinetic blocks to look through
	 */
	static Map<BlockPos, Integer> find(Level level, Collection<BlockPos> from, int limit) {
		Map<BlockPos, Integer> found = new HashMap<>();
		if (NEIGHBOURS == null || MODIFIER == null)
			return found;

		// Speed of each block as a multiple of the reference speed, signed
		Map<BlockPos, Float> ratio = new HashMap<>();
		Deque<KineticBlockEntity> queue = new ArrayDeque<>();
		for (BlockPos pos : from)
			if (level.getBlockEntity(pos) instanceof KineticBlockEntity be) {
				ratio.put(pos.immutable(), 1f);
				queue.add(be);
			}

		while (!queue.isEmpty() && ratio.size() < limit) {
			KineticBlockEntity be = queue.poll();
			float speed = ratio.get(be.getBlockPos());
			// Create looks at the neighbours however far away they are; don't be the reason a chunk gets loaded
			if (!surroundingsLoaded(level, be.getBlockPos()))
				continue;
			for (KineticBlockEntity next : neighbours(be)) {
				BlockPos pos = next.getBlockPos();
				if (ratio.containsKey(pos))
					continue;
				float modifier = modifier(be, next);
				if (modifier == 0)
					continue;
				ratio.put(pos.immutable(), speed * modifier);
				queue.add(next);
			}
		}

		for (Map.Entry<BlockPos, Float> entry : ratio.entrySet()) {
			float speed = entry.getValue();
			if (from.contains(entry.getKey()) || Math.abs(Math.abs(speed) - 1) > TOLERANCE)
				continue;
			if (level.getBlockState(entry.getKey())
				.getBlock() instanceof CrankshaftBlock)
				found.put(entry.getKey(), speed > 0 ? 1 : -1);
		}
		return found;
	}

	private static boolean surroundingsLoaded(Level level, BlockPos pos) {
		return level.hasChunkAt(pos.offset(-1, 0, -1)) && level.hasChunkAt(pos.offset(1, 0, 1))
			&& level.hasChunkAt(pos.offset(-1, 0, 1)) && level.hasChunkAt(pos.offset(1, 0, -1));
	}

	@SuppressWarnings("unchecked")
	private static List<KineticBlockEntity> neighbours(KineticBlockEntity be) {
		try {
			return (List<KineticBlockEntity>) NEIGHBOURS.invoke(null, be);
		} catch (ReflectiveOperationException | RuntimeException e) {
			return List.of();
		}
	}

	private static float modifier(KineticBlockEntity from, KineticBlockEntity to) {
		try {
			return (float) MODIFIER.invoke(null, from, to);
		} catch (ReflectiveOperationException | RuntimeException e) {
			return 0;
		}
	}
}
