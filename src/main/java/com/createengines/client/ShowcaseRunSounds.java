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
import com.createengines.content.airflow.ThrottleBlockEntity;
import com.createengines.content.airflow.ManifoldBlock;
import com.createengines.content.tool.BankBuilderItem;
import com.createengines.content.tool.TimingToolItem;
import com.createengines.engine.topology.FiringPlanner;
import com.createengines.registry.CEBlocks;
import com.simibubi.create.AllBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * The 2.4 trailer's pads that are there to be heard (see {@link ShowcaseRun}): an open-air V8 with a bare exhaust on each
 * bank, which barks while the camera pulls back from one mouth, is re-timed crossplane and flat-plane, has one bank
 * muffled and is left behind by a long dolly; and a diesel four that knocks at idle and is then stalled by a line of
 * presses clutched in. The notes, the timing, the load and the stall are the mod's and Create's own. What is staged: the
 * V8 gets the mod's own crank inertia back for the shots it revs in (see {@link ShowcaseRunStaging#light}).
 */
final class ShowcaseRunSounds {

	/** The V8's pad: the east end of the row north of the first, with no walls, so the camera can leave it behind. */
	static final int V8_BAY = 29;
	static final int DIESEL_BAY = 26;
	private static final int V8_THROWS = 4;
	private static final int DIESEL_THROWS = 4;
	/** The V8's idle stop, in steps of five percent. */
	private static final int V8_IDLE = 6;
	/** The diesel's flywheel is at this many blocks along; the clutch comes after it. */
	private static final int CLUTCH_X = DIESEL_THROWS + 6;
	/** Four presses are a little under what the diesel is rated for, so Create accepts them, and over what it makes at idle. */
	private static final int PRESSES = 4;

	private ShowcaseRunSounds() {
	}

	static void build(ServerLevel level, ServerPlayer player) {
		ShowcaseRun.attempt("the open V8", () -> {
			ShowcaseRun.bank(level, player, pad(V8_BAY), BankBuilderItem.Layout.VEE, V8_THROWS,
				ShowcaseRun.Kit.plain(ShowcaseRun.Fuel.GASOLINE));
			// No flywheel, so that it revs
			set(level, pad(V8_BAY), V8_THROWS + 5, CY, CZ, Blocks.AIR.defaultBlockState());
			muffle(level, V8_BAY, V8_THROWS, BankBuilderItem.Layout.VEE, -1);
			ShowcaseRunStaging.throttles(level, V8_BAY, V8_THROWS, false, V8_IDLE);
		});
		ShowcaseRun.attempt("the diesel", () -> {
			BlockPos at = pad(DIESEL_BAY);
			ShowcaseRun.bank(level, player, at, BankBuilderItem.Layout.INLINE, DIESEL_THROWS,
				ShowcaseRun.Kit.plain(ShowcaseRun.Fuel.DIESEL));
			muffle(level, DIESEL_BAY, DIESEL_THROWS, BankBuilderItem.Layout.INLINE, -1);
			loadLine(level, at);
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
			Direction exhaust = ShowcaseRun.intakeSide(bore)
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
				ShowcaseRun.pipe(level, last, CEBlocks.EXHAUST_MANIFOLD.get());
			// Each pipe joins up again to whatever is beside it now
			for (BlockPos pos : row)
				if (level.getBlockState(pos)
					.is(CEBlocks.EXHAUST_MANIFOLD.get()))
					ShowcaseRun.pipe(level, pos, (ManifoldBlock) CEBlocks.EXHAUST_MANIFOLD.get());
		}
	}

	/** Past the diesel's flywheel: a clutch held open by redstone on it, and the presses it can engage. */
	private static void loadLine(ServerLevel level, BlockPos at) {
		set(level, at, CLUTCH_X, CY + 1, CZ, Blocks.REDSTONE_BLOCK.defaultBlockState());
		set(level, at, CLUTCH_X, CY, CZ, AllBlocks.CLUTCH.getDefaultState()
			.setValue(BlockStateProperties.AXIS, Axis.X)
			.setValue(BlockStateProperties.POWERED, true));
		for (int i = 1; i <= PRESSES; i++)
			set(level, at, CLUTCH_X + i, CY, CZ, AllBlocks.MECHANICAL_PRESS.getDefaultState()
				.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST));
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

		// The throttle is shut to its lowest idle and the clutch lets the presses in: more than the diesel makes there
		cue.accept("stall_load", new Cued(1.25, level -> ShowcaseRunStaging.throttles(level, DIESEL_BAY, DIESEL_THROWS, false,
			ThrottleBlockEntity.minIdle())));
		ShowcaseRunStaging.hold(cue, "stall_load", 0, 6, level -> ShowcaseRunStaging.light(level, DIESEL_BAY));
		cue.accept("stall_load", new Cued(0, level -> ShowcaseRunStaging.log(level, DIESEL_BAY, "before the load")));
		cue.accept("stall_load", new Cued(1.5,
			level -> set(level, pad(DIESEL_BAY), CLUTCH_X, CY + 1, CZ, Blocks.AIR.defaultBlockState())));
		for (double at : new double[] { 1.75, 2.25, 3, 3.75, 4.5, 5.5 })
			cue.accept("stall_load", new Cued(at, level -> ShowcaseRunStaging.log(level, DIESEL_BAY, "under the load")));
	}
}
