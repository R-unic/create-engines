package com.createengines.client;

import static com.createengines.client.ShowcaseRun.CY;
import static com.createengines.client.ShowcaseRun.CZ;
import static com.createengines.client.ShowcaseRun.axisX;
import static com.createengines.client.ShowcaseRun.pad;
import static com.createengines.client.ShowcaseRun.set;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

import com.createengines.client.ShowcaseRunStaging.Cued;
import com.createengines.content.airflow.ManifoldBlock;
import com.createengines.content.tool.BankBuilderItem;
import com.createengines.content.tool.TimingToolItem;
import com.createengines.engine.topology.FiringPlanner;
import com.createengines.registry.CEBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * The 2.4 trailer's pads that are there to be heard (see {@link ShowcaseRun}): an open-air V8 with a bare exhaust on each
 * bank, which barks while the camera pulls back from one mouth, is re-timed crossplane and flat-plane, has one bank
 * muffled and is left behind by a long dolly; and a diesel four that knocks at idle. The notes and the timing are the
 * mod's own. What is staged: the V8 gets the mod's own crank inertia back for the shots it revs in (see
 * {@link ShowcaseRunStaging#light}).
 */
final class ShowcaseRunSounds {

	/** The V8's pad: the east end of the row north of the first, with no walls, so the camera can leave it behind. */
	static final int V8_BAY = 29;
	static final int DIESEL_BAY = 26;
	private static final int V8_THROWS = 4;
	private static final int DIESEL_THROWS = 4;
	/** The V8's idle stop, in steps of five percent. */
	private static final int V8_IDLE = 6;

	private ShowcaseRunSounds() {
	}

	static void build(ServerLevel level, ServerPlayer player) {
		ShowcaseRun.attempt("the open V8", () -> {
			ShowcaseRunBuild.bank(level, player, pad(V8_BAY), BankBuilderItem.Layout.VEE, V8_THROWS,
				ShowcaseRun.Kit.plain(ShowcaseRun.Fuel.GASOLINE));
			// No flywheel, so that it revs
			set(level, pad(V8_BAY), V8_THROWS + 5, CY, CZ, Blocks.AIR.defaultBlockState());
			muffle(level, V8_BAY, V8_THROWS, BankBuilderItem.Layout.VEE, -1);
			ShowcaseRunStaging.throttles(level, V8_BAY, V8_THROWS, false, V8_IDLE);
		});
		ShowcaseRun.attempt("the diesel", () -> {
			ShowcaseRunBuild.bank(level, player, pad(DIESEL_BAY), BankBuilderItem.Layout.INLINE, DIESEL_THROWS,
				ShowcaseRun.Kit.plain(ShowcaseRun.Fuel.DIESEL));
			muffle(level, DIESEL_BAY, DIESEL_THROWS, BankBuilderItem.Layout.INLINE, -1);
		});
	}

	/**
	 * Ends every bank's exhaust in a bare pipe and its opening, except bank {@code muffled} (counted as the Bank Builder
	 * lays them), which gets a Muffler in place of the last pipe; -1 muffles none.
	 */
	private static void muffle(ServerLevel level, int bay, int throwCount, BankBuilderItem.Layout layout, int muffled) {
		BlockPos at = pad(bay);
		int bankIndex = 0;
		for (Direction bore : BankBuilderItem.faces(layout, Axis.X, Direction.UP)) {
			Direction exhaust = ShowcaseRunBuild.intakeSide(bore)
				.getOpposite();
			List<BlockPos> row = new ArrayList<>();
			for (int x = 2; x <= throwCount + 2; x++)
				row.add(at.offset(x, CY, CZ)
					.relative(bore, 2)
					.relative(exhaust));
			BlockPos last = row.get(row.size() - 1);
			if (bankIndex++ == muffled)
				level.setBlock(last, axisX(CEBlocks.MUFFLER.getDefaultState()), Block.UPDATE_ALL);
			else
				ShowcaseRunBuild.pipe(level, last, CEBlocks.EXHAUST_MANIFOLD.get());
			// Each pipe joins up again to whatever is beside it now
			for (BlockPos pos : row)
				if (level.getBlockState(pos)
					.is(CEBlocks.EXHAUST_MANIFOLD.get()))
					ShowcaseRunBuild.pipe(level, pos, (ManifoldBlock) CEBlocks.EXHAUST_MANIFOLD.get());
		}
	}

	private static void time(ServerLevel level, FiringPlanner.Style style) {
		TimingToolItem.apply(level, pad(V8_BAY).offset(1, CY, CZ), style);
	}

	private static void rev(ServerLevel level, boolean open) {
		ShowcaseRunStaging.throttles(level, V8_BAY, V8_THROWS, open, V8_IDLE);
	}

	static void defineCues(BiConsumer<String, Cued> cue) {
		// The V8 is already revving when the pull-back from its mouth begins (the vertical cut opens on that frame), so it is
		// opened up while the shot before is on another pad
		cue.accept("cold_start", new Cued(0, level -> {
			time(level, FiringPlanner.Style.CROSS_PLANE);
			rev(level, true);
		}));
		ShowcaseRunStaging.hold(cue, "cold_start", 0.25, 6, level -> ShowcaseRunStaging.light(level, V8_BAY));
		ShowcaseRunStaging.hold(cue, "exhaust_mouth", 0, 6, level -> ShowcaseRunStaging.light(level, V8_BAY));
		cue.accept("exhaust_mouth", new Cued(3, level -> ShowcaseRunStaging.log(level, V8_BAY, "revving")));

		// The two cranks, at idle on the recorder's steady crank, one after the other. A re-timed or re-piped engine misfires
		// for about five seconds, so these shots are long and the edits cut from after that
		cue.accept("v8_crossplane", new Cued(0, level -> {
			rev(level, false);
			ShowcaseRunStaging.heavy(level, V8_BAY);
			time(level, FiringPlanner.Style.CROSS_PLANE);
		}));
		cue.accept("v8_flatplane", new Cued(0, level -> time(level, FiringPlanner.Style.FLAT_PLANE)));
		for (String shot : List.of("v8_crossplane", "v8_flatplane", "muffler_bank"))
			for (int second = 1; second <= 5; second++)
				cue.accept(shot, new Cued(second, level -> ShowcaseRunStaging.log(level, V8_BAY, shot)));
		cue.accept("muffler_bank", new Cued(0, level -> {
			time(level, FiringPlanner.Style.CROSS_PLANE);
			muffle(level, V8_BAY, V8_THROWS, BankBuilderItem.Layout.VEE, 0);
		}));
		// Both banks bare again and revving, in time for the long dolly away
		cue.accept("diesel_knock", new Cued(0, level -> {
			muffle(level, V8_BAY, V8_THROWS, BankBuilderItem.Layout.VEE, -1);
			rev(level, true);
		}));
		ShowcaseRunStaging.hold(cue, "far_boom", 0, 9, level -> ShowcaseRunStaging.light(level, V8_BAY));
	}
}
