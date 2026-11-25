package com.createengines.engine.topology;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import com.createengines.engine.EngineProviders;
import com.createengines.content.EngineGlow;
import com.createengines.content.crankshaft.CrankshaftBlockEntity;
import com.createengines.engine.EngineController;
import com.createengines.engine.LoadedBlocks;

import com.simibubi.create.content.kinetics.base.IRotate;

import net.createmod.catnip.data.WorldAttached;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Keeps track of every engine in a level and rescans an engine only when one of its blocks changes.
 * <p>
 * Blocks report changes with {@link #markDirty}. Changes collect over a tick and are resolved once at the end of it,
 * so placing a dozen blocks rescans each affected engine once. Engine motion (crank angle, speed) is carried across
 * rescans, so adding a manifold to a running engine does not restart it.
 */
public final class EngineTopology {

	private static final WorldAttached<EngineTopology> TOPOLOGIES = new WorldAttached<>(level -> new EngineTopology());
	/** Safety net for changes no block reports (e.g. a Create flywheel added through a shaft). */
	private static final int REVALIDATE_INTERVAL = 100;
	/** How far, in blocks, a kinetic block placed or broken is looked for geared cranks. */
	private static final int GEARING_REACH = 64;

	private final Set<BlockPos> dirty = new HashSet<>();
	/** Crank blocks of engines whose added sources are to be asked again at the end of the tick; see {@link #rebuildNear}. */
	private final Set<BlockPos> rebuilds = new HashSet<>();
	private final Map<BlockPos, Set<BlockPos>> enginesByMember = new HashMap<>();
	private final Map<BlockPos, EngineLayout> layouts = new HashMap<>();
	private int ticks;

	public static void markDirty(@Nullable LevelAccessor level, BlockPos pos) {
		if (level == null || level.isClientSide())
			return;
		TOPOLOGIES.get(level).dirty.add(pos.immutable());
	}

	/**
	 * A block beside a cylinder, head or rotor housing at {@code pos} changed: look at its engine again if that put a
	 * fluid against it or took one away, since an engine sitting in water does not run. {@code changed} is the block
	 * that was there before.
	 */
	public static void fluidMoved(Level level, BlockPos pos, Block changed, BlockPos neighbour) {
		if (changed instanceof LiquidBlock || !level.getFluidState(neighbour)
			.isEmpty())
			markDirty(level, pos);
	}

	/** How far from a wear change an engine's crank can be and still be rebuilt for it. */
	private static final int WEAR_REACH = 16;

	/**
	 * A part of the wear mode changed at {@code pos} (a gasket blew or was fitted, a rod was thrown or its wreck cleared, a head broke): the
	 * engine around it is rebuilt at the end of the tick, not just looked at again, because the faults the wear mode adds are read when an
	 * engine is built and a layout that came back unchanged would keep its old controller, and with it the old faults.
	 */
	public static void wearChanged(@Nullable Level level, BlockPos pos) {
		if (level == null || level.isClientSide())
			return;
		markDirty(level, pos);
		rebuildNear(level, pos, WEAR_REACH);
	}

	/**
	 * The engine whose primary crank is at {@code primary} is rebuilt at the end of the tick. For an engine that asks for itself:
	 * unlike {@link #rebuildNear} it looks at no other engine, so every engine can ask in the same tick.
	 */
	public static void rebuild(Level level, BlockPos primary) {
		if (!level.isClientSide())
			TOPOLOGIES.get(level).rebuilds.add(primary.immutable());
	}

	/**
	 * A block that an engine source provider watches was placed or broken at {@code pos}: every engine with a crank
	 * segment within {@code reach} blocks of it is rebuilt at the end of the tick, so its providers are asked again. A
	 * plain rescan would not do it, because an engine whose layout came back unchanged keeps its controller.
	 */
	public static void rebuildNear(Level level, BlockPos pos, int reach) {
		if (level.isClientSide())
			return;
		EngineTopology topology = TOPOLOGIES.get(level);
		for (EngineLayout layout : topology.layouts.values())
			for (EngineLayout.Segment segment : layout.segments())
				if (Math.abs(segment.pos()
					.getX() - pos.getX()) <= reach
					&& Math.abs(segment.pos()
						.getY() - pos.getY()) <= reach
					&& Math.abs(segment.pos()
						.getZ() - pos.getZ()) <= reach) {
					topology.rebuilds.add(layout.primary());
					break;
				}
	}

	/**
	 * Placing or breaking a block of Create's kinetic network changes what a crankshaft is geared to, and Create's blocks
	 * never say so: every engine near it looks at its gearing again at once rather than at the next revalidation.
	 */
	public static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
		if (event.getLevel() instanceof Level level)
			EngineProviders.blockChanged(level, event.getPos(), event.getPlacedBlock());
		gearingChanged(event.getLevel(), event.getPos(), event.getPlacedBlock());
	}

	public static void onBlockBroken(BlockEvent.BreakEvent event) {
		if (event.getLevel() instanceof Level level)
			EngineProviders.blockChanged(level, event.getPos(), event.getState());
		gearingChanged(event.getLevel(), event.getPos(), event.getState());
	}

	/** A block of the kinetic network was placed or broken at {@code pos}: engines within reach look at their gearing again. */
	public static void gearingChanged(LevelAccessor level, BlockPos pos, BlockState state) {
		if (level.isClientSide() || !(state.getBlock() instanceof IRotate))
			return;
		EngineTopology topology = TOPOLOGIES.get(level);
		for (BlockPos primary : topology.layouts.keySet())
			if (primary.distSqr(pos) <= GEARING_REACH * GEARING_REACH)
				topology.dirty.add(primary);
	}

	public static void onLevelTick(LevelTickEvent.Post event) {
		if (event.getLevel() instanceof ServerLevel level)
			TOPOLOGIES.get(level)
				.tick(level);
	}

	public static void onLevelUnload(LevelEvent.Unload event) {
		WorldAttached.invalidateWorld(event.getLevel());
	}

	private void tick(ServerLevel level) {
		if (!rebuilds.isEmpty()) {
			Set<BlockPos> due = new HashSet<>(rebuilds);
			rebuilds.clear();
			for (BlockPos primary : due)
				if (LoadedBlocks.blockEntity(level, primary) instanceof CrankshaftBlockEntity crank && crank.controller() != null)
					crank.rebuildEngine();
		}
		// Each engine is revalidated once per interval, on its own tick, so many engines never rescan in the same tick
		int phase = ++ticks % REVALIDATE_INTERVAL;
		for (BlockPos primary : layouts.keySet())
			if (Math.floorMod(primary.hashCode(), REVALIDATE_INTERVAL) == phase)
				dirty.add(primary);
		if (dirty.isEmpty())
			return;

		Set<BlockPos> changed = new HashSet<>(dirty);
		dirty.clear();
		Set<BlockPos> affected = new HashSet<>();
		Seeds seeds = new Seeds();
		for (BlockPos pos : changed) {
			collectEngines(pos, affected);
			for (Direction direction : Direction.values())
				collectEngines(pos.relative(direction), affected);
			EngineScanner.crankSeeds(level, pos)
				.forEach(seeds::add);
		}

		Map<BlockPos, EngineLayout> previous = new HashMap<>();
		for (BlockPos primary : affected)
			retire(primary, previous, seeds);

		Map<BlockPos, EngineLayout> rebuilt = new LinkedHashMap<>();
		Set<BlockPos> covered = new HashSet<>();
		while (!seeds.queue.isEmpty()) {
			BlockPos seed = seeds.queue.poll();
			if (covered.contains(seed) || !level.isLoaded(seed))
				continue;
			EngineLayout layout = EngineScanner.scan(level, seed);
			if (layout == null)
				continue;
			for (EngineLayout.Segment segment : layout.segments()) {
				covered.add(segment.pos());
				Set<BlockPos> owners = enginesByMember.get(segment.pos());
				if (owners != null)
					for (BlockPos owner : owners)
						retire(owner, previous, seeds);
			}
			rebuilt.put(layout.primary(), layout);
		}

		// An engine that came back exactly as it was keeps its entries in the member index, which is most of them most of the time
		for (Map.Entry<BlockPos, EngineLayout> old : previous.entrySet())
			if (!old.getValue()
				.equals(rebuilt.get(old.getKey())))
				unindex(old.getKey(), old.getValue());
		for (EngineLayout layout : rebuilt.values()) {
			layouts.put(layout.primary(), layout);
			if (layout.equals(previous.get(layout.primary())))
				continue;
			for (BlockPos member : layout.members())
				enginesByMember.computeIfAbsent(member, $ -> new HashSet<>())
					.add(layout.primary());
		}

		// Carry motion from whichever old engine shared a crankshaft with each new one
		Map<BlockPos, EngineController.Motion> carried = new HashMap<>();
		for (EngineLayout layout : rebuilt.values()) {
			// An engine that came back exactly as it was (the periodic revalidation, a neighbour update that changed nothing)
			// keeps its controller, and so has no use for motion to carry across
			if (LoadedBlocks.blockEntity(level, layout.primary()) instanceof CrankshaftBlockEntity host
				&& host.controller() != null && host.controller()
					.layout()
					.equals(layout))
				continue;
			Set<BlockPos> segments = new HashSet<>();
			layout.segments()
				.forEach(s -> segments.add(s.pos()));
			for (Map.Entry<BlockPos, EngineLayout> old : previous.entrySet()) {
				if (old.getValue()
					.segments()
					.stream()
					.noneMatch(s -> segments.contains(s.pos())))
					continue;
				if (LoadedBlocks.blockEntity(level, old.getKey()) instanceof CrankshaftBlockEntity be) {
					EngineController.Motion motion = be.captureMotion();
					if (motion != null) {
						carried.put(layout.primary(), motion);
						break;
					}
				}
			}
		}

		for (Map.Entry<BlockPos, EngineLayout> old : previous.entrySet()) {
			if (!rebuilt.containsKey(old.getKey())
				&& LoadedBlocks.blockEntity(level, old.getKey()) instanceof CrankshaftBlockEntity be)
				be.releaseEngine();
			for (BlockPos member : old.getValue()
				.members()) {
				if (enginesByMember.containsKey(member))
					continue;
				EngineGlow.set(level, member, false);
				if (level.isLoaded(member) && level.getBlockEntity(member) instanceof EngineMember engineMember)
					engineMember.detachFromEngine();
			}
		}

		for (EngineLayout layout : rebuilt.values())
			if (LoadedBlocks.blockEntity(level, layout.primary()) instanceof CrankshaftBlockEntity be)
				be.adoptLayout(layout, carried.get(layout.primary()));
	}

	private void collectEngines(BlockPos pos, Set<BlockPos> into) {
		Set<BlockPos> owners = enginesByMember.get(pos);
		if (owners != null)
			into.addAll(owners);
	}

	/** Crankshaft positions still to scan this tick. Seeds can be added while scanning. */
	private static final class Seeds {
		final Deque<BlockPos> queue = new ArrayDeque<>();
		final Set<BlockPos> seen = new HashSet<>();

		void add(BlockPos pos) {
			if (seen.add(pos))
				queue.add(pos);
		}
	}

	/**
	 * Forgets a known engine so it can be rescanned, remembering its old layout and crank as rescan seeds. Its entries in the member
	 * index stay until the rescan shows whether it has changed (see {@link #unindex}).
	 */
	private void retire(BlockPos primary, Map<BlockPos, EngineLayout> previous, Seeds seeds) {
		EngineLayout old = layouts.remove(primary);
		if (old == null)
			return;
		previous.putIfAbsent(primary, old);
		old.segments()
			.forEach(s -> seeds.add(s.pos()));
	}

	/** Takes a retired engine's blocks out of the member index. */
	private void unindex(BlockPos primary, EngineLayout old) {
		for (BlockPos member : old.members()) {
			Set<BlockPos> owners = enginesByMember.get(member);
			if (owners == null)
				continue;
			owners.remove(primary);
			if (owners.isEmpty())
				enginesByMember.remove(member);
		}
	}
}
