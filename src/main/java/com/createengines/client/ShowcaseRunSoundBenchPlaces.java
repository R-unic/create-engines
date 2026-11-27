package com.createengines.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.createengines.compat.sable.SableCompat;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The sound bench's scenes about where an engine is heard, one inline four idling in each, so that only the place changes:
 * {@code walls} puts one, two and three blocks of stone between it and the listener, {@code indoors} shuts both in a
 * stone room, and {@code moving} (only with Create Aeronautics loaded) lifts it into a moving structure and carries it
 * past.
 */
final class ShowcaseRunSoundBenchPlaces {

	/** Farther than the other scenes listen from, to leave room for a wall clear of the engine. */
	private static final int LISTEN = 12;
	/** Ticks each placing is held, and listened to. */
	private static final int HOLD = 160;
	/** How far the structure travels either side of where it starts, and how many ticks the whole way takes: 8 blocks a second. */
	private static final int TRAVEL = 24;
	private static final int PASS_TICKS = 120;

	private ShowcaseRunSoundBenchPlaces() {
	}

	/** Adds the wanted scenes, from pad {@code bay} on; returns the next free pad. */
	static int schedule(Set<String> only, int bay, ShowcaseRunSoundBench.Rig rig) {
		if (only.isEmpty() || only.contains("walls"))
			walls(bay, rig);
		if (only.isEmpty() || only.contains("indoors"))
			indoors(bay + 1, rig);
		// Never by default: without the mod there is no structure to ride, so it has to be asked for by name
		if (only.contains("moving"))
			moving(bay + 2, rig);
		return bay + 3;
	}

	private static void walls(int bay, ShowcaseRunSoundBench.Rig rig) {
		ShowcaseRunSoundBench.startedEngine("walls", rig, bay, LISTEN);
		ShowcaseRunSoundBench.step("walls", "open", HOLD, null);
		for (int thick = 1; thick <= 3; thick++) {
			int blocks = thick;
			ShowcaseRunSoundBench.step("walls", "wall" + thick, HOLD, () -> wall(rig, blocks, Blocks.STONE.defaultBlockState()));
		}
		ShowcaseRunSoundBench.step("walls", "open_again", HOLD, () -> wall(rig, 3, Blocks.AIR.defaultBlockState()));
		ShowcaseRunSoundBench.clearScene("walls");
	}

	/** A wall across the line from the engine to the listener, starting two blocks in front of the listener. */
	private static void wall(ShowcaseRunSoundBench.Rig rig, int thick, BlockState state) {
		BlockPos pad = ShowcaseRunSoundBench.pad();
		int near = pad.getZ() + rig.crank()
			.getZ() - LISTEN + 2;
		List<BlockPos> wall = box(pad.getX() - 10, pad.getY(), near, pad.getX() + rig.length() + 10,
			pad.getY() + ShowcaseRun.CY + 8, near + thick - 1);
		ShowcaseRunSoundBench.onServer((level, player) -> fill(level, wall, state));
	}

	private static void indoors(int bay, ShowcaseRunSoundBench.Rig rig) {
		ShowcaseRunSoundBench.startedEngine("indoors", rig, bay, LISTEN / 2);
		ShowcaseRunSoundBench.step("indoors", "outdoors", HOLD, null);
		ShowcaseRunSoundBench.step("indoors", "indoors", HOLD, () -> room(rig, Blocks.STONE.defaultBlockState()));
		ShowcaseRunSoundBench.step("indoors", "outdoors_again", HOLD, () -> room(rig, Blocks.AIR.defaultBlockState()));
		ShowcaseRunSoundBench.clearScene("indoors");
	}

	/** The walls and roof of a room round the pad and the listener, standing on the ground. */
	private static void room(ShowcaseRunSoundBench.Rig rig, BlockState state) {
		BlockPos pad = ShowcaseRunSoundBench.pad();
		int line = pad.getZ() + rig.crank()
			.getZ();
		int west = pad.getX() - 4;
		int east = pad.getX() + rig.length() + 1;
		int north = line - LISTEN / 2 - 3;
		int south = line + 7;
		int top = pad.getY() + ShowcaseRun.CY + 6;
		List<BlockPos> shell = new ArrayList<>(box(west, top, north, east, top, south));
		shell.addAll(box(west, pad.getY(), north, east, top - 1, north));
		shell.addAll(box(west, pad.getY(), south, east, top - 1, south));
		shell.addAll(box(west, pad.getY(), north + 1, west, top - 1, south - 1));
		shell.addAll(box(east, pad.getY(), north + 1, east, top - 1, south - 1));
		ShowcaseRunSoundBench.onServer((level, player) -> fill(level, shell, state));
	}

	private static List<BlockPos> box(int x, int y, int z, int toX, int toY, int toZ) {
		List<BlockPos> all = new ArrayList<>();
		for (BlockPos pos : BlockPos.betweenClosed(x, y, z, toX, toY, toZ))
			all.add(pos.immutable());
		return all;
	}

	/** Fills only air with {@code state}, or takes away only stone, so no part of an engine is ever replaced. */
	private static void fill(ServerLevel level, List<BlockPos> where, BlockState state) {
		for (BlockPos pos : where) {
			BlockState there = level.getBlockState(pos);
			if (state.isAir() ? there.is(Blocks.STONE) : there.isAir())
				level.setBlock(pos, state, Block.UPDATE_CLIENTS);
		}
	}

	/**
	 * The engine, started on the ground, becomes a moving structure held beside the listener, then is carried away to
	 * one side, past the listener to the other, and back.
	 */
	private static void moving(int bay, ShowcaseRunSoundBench.Rig rig) {
		if (!SableCompat.installed()) {
			ShowcaseRunSoundBench.step("moving", "missing", 20,
				() -> ShowcaseRunSoundBench.failed("the moving scene needs Create Aeronautics loaded"));
			return;
		}
		ShowcaseRunSoundBench.startedEngine("moving", rig, bay, LISTEN / 2);
		ShowcaseRunSoundBench.step("moving", "assemble", 100,
			() -> ShowcaseRunSoundBench.onServer((level, player) -> ShowcaseRunSoundBenchSable.assemble(level, rig)));
		ShowcaseRunSoundBench.during("moving", "held", HOLD, t -> carry(0));
		ShowcaseRunSoundBench.during("moving", "leave", PASS_TICKS / 2, t -> carry(-TRAVEL * share(t, PASS_TICKS / 2)));
		ShowcaseRunSoundBench.during("moving", "pass", PASS_TICKS, t -> carry(-TRAVEL + 2 * TRAVEL * share(t, PASS_TICKS)));
		ShowcaseRunSoundBench.during("moving", "pass_back", PASS_TICKS,
			t -> carry(TRAVEL - 2 * TRAVEL * share(t, PASS_TICKS)));
		// Left where it is: nothing comes after it in the run that has it
	}

	/** The share done by {@code tick} of a stretch whose last tick told is one short of {@code of}. */
	private static double share(int tick, int of) {
		return Math.min(1, tick / (double) (of - 1));
	}

	private static void carry(double along) {
		ShowcaseRunSoundBench.onServer((level, player) -> ShowcaseRunSoundBenchSable.carry(along));
	}
}
