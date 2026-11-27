package com.createengines.client;

import static com.createengines.client.ShowcaseRun.CY;
import static com.createengines.client.ShowcaseRun.CZ;
import static com.createengines.client.ShowcaseRun.pad;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import com.createengines.CreateEngines;
import com.createengines.content.airflow.ThrottleBlockEntity;
import com.createengines.content.crankshaft.CrankshaftBlockEntity;
import com.createengines.engine.EngineController;
import com.createengines.engine.sim.EngineSimulation;
import com.createengines.engine.sim.TorqueSource;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * What the trailer's shots do to a running engine from outside, shared by the pad classes (see {@link ShowcaseRun}): hold
 * its block at a temperature, give it the mod's own crank inertia in place of the recorder's, and open its throttles. These
 * reach into the simulation by reflection, as the mod has no way in from outside and should not grow one for a film.
 */
final class ShowcaseRunStaging {

	/** How many times the shipped crank inertia the trailer's config has (TRAILER_CONFIG in tools/trailer/record.py). */
	private static final double TRAILER_CRANK_WEIGHT = 5;

	/** The simulations now on the stock crank, with the inertia each had before. A rebuilt engine is a new one, not in here. */
	private static final Map<EngineSimulation, Double> LIGHTENED = new IdentityHashMap<>();

	private ShowcaseRunStaging() {
	}

	/** Something done to the world {@code at} seconds into a shot. */
	record Cued(double at, Consumer<ServerLevel> action) {
	}

	/** Does {@code action} every quarter second from {@code from} up to {@code to} seconds into a shot. */
	static void hold(BiConsumer<String, Cued> cue, String shot, double from, double to, Consumer<ServerLevel> action) {
		for (int quarter = 0; from + quarter * 0.25 < to; quarter++)
			cue.accept(shot, new Cued(from + quarter * 0.25, action));
	}

	/** The engine on a pad's first journal, or null. */
	static EngineSimulation simulation(ServerLevel level, int bay) {
		if (!(level.getBlockEntity(pad(bay).offset(1, CY, CZ)) instanceof CrankshaftBlockEntity crank)
			|| crank.controller() == null)
			return null;
		try {
			Field sim = EngineController.class.getDeclaredField("sim");
			sim.setAccessible(true);
			return (EngineSimulation) sim.get(crank.controller());
		} catch (ReflectiveOperationException e) {
			CreateEngines.LOGGER.error("Showcase: could not reach the engine on bay {}", bay, e);
			return null;
		}
	}

	static void heat(ServerLevel level, int bay, double temperature) {
		EngineSimulation sim = simulation(level, bay);
		if (sim != null)
			sim.thermal()
				.setTemperature(temperature);
	}

	/**
	 * Gives the engine on a pad the weight of crank the mod ships with. tools/trailer/record.py makes every showcase crank
	 * {@link #TRAILER_CRANK_WEIGHT} times as heavy, which steadies the slow engines of most shots and would leave an engine
	 * too sluggish to be seen revving, or its crank too steady to show what a rod does to it. A cylinder's own inertia is not
	 * part of that, so it is left as it is. Safe to call again and again: it acts once on each engine, and again on one the
	 * mod has rebuilt.
	 */
	static void light(ServerLevel level, int bay) {
		EngineSimulation sim = simulation(level, bay);
		if (sim == null || LIGHTENED.containsKey(sim))
			return;
		double parts = sim.sources()
			.stream()
			.mapToDouble(TorqueSource::inertia)
			.sum();
		double before = sim.inertia();
		setInertia(sim, parts + (before - parts) / TRAILER_CRANK_WEIGHT);
		LIGHTENED.put(sim, before);
		CreateEngines.LOGGER.info("Showcase bay {}: stock crank, inertia {} to {}", bay, before, sim.inertia());
	}

	/** Gives the engine on a pad the recorder's heavy crank back, if {@link #light} took it off. */
	static void heavy(ServerLevel level, int bay) {
		EngineSimulation sim = simulation(level, bay);
		if (sim == null)
			return;
		Double before = LIGHTENED.remove(sim);
		if (before != null)
			setInertia(sim, before);
	}

	private static void setInertia(EngineSimulation sim, double value) {
		try {
			Field field = EngineSimulation.class.getDeclaredField("inertia");
			field.setAccessible(true);
			field.setDouble(sim, value);
		} catch (ReflectiveOperationException e) {
			CreateEngines.LOGGER.error("Showcase: could not set the inertia", e);
		}
	}

	/** Every block entity round a pad's engine, which has {@code throwCount} throws. */
	static List<BlockEntity> parts(ServerLevel level, int bay, int throwCount) {
		List<BlockEntity> parts = new ArrayList<>();
		BlockPos at = pad(bay);
		for (BlockPos pos : BlockPos.betweenClosed(at.offset(0, 0, CZ - 5), at.offset(throwCount + 6, CY + 6, CZ + 5))) {
			BlockEntity part = level.getBlockEntity(pos);
			if (part != null)
				parts.add(part);
		}
		return parts;
	}

	/**
	 * Opens a pad's throttles all the way, or sets them to an idle stop of {@code idle} steps: a redstone block beside a
	 * throttle would reach the camshaft too, and cut the fuel.
	 */
	static void throttles(ServerLevel level, int bay, int throwCount, boolean open, int idle) {
		for (BlockEntity part : parts(level, bay, throwCount))
			if (part instanceof ThrottleBlockEntity valve) {
				valve.wide.setValue(ThrottleBlockEntity.maxWide());
				valve.idle.setValue(open ? ThrottleBlockEntity.maxIdle() : idle);
			}
	}

	/** Writes what a pad's engine is doing to the log, so a take can be checked without watching it. */
	static void log(ServerLevel level, int bay, String what) {
		if (level.getBlockEntity(pad(bay).offset(1, CY, CZ)) instanceof CrankshaftBlockEntity crank)
			CreateEngines.LOGGER.info("Showcase bay {}, {}: {}", bay, what, crank.summary());
	}
}
