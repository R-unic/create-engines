package com.createengines.client;

import java.util.List;

import com.createengines.gametest.TestEngine;

import net.minecraft.core.BlockPos;

/**
 * The sound bench's soak (SOUND-PLAN, step 5): the sixteen engines of the room with the listener standing among them for
 * minutes, as in a base, not outside it for half a minute. One engine is heard first from that spot, to hold the rest
 * against; then all sixteen as they come up, once they have had time to settle, while the listener paces through them
 * (notes pass from engine to engine), and standing still again.
 */
final class ShowcaseRunSoundBenchSoak {

	/** Where the listener stands, from the pad's corner: between the middle columns and the middle rows. */
	private static final double MIDDLE_X = 15.5;
	private static final double MIDDLE_Z = 11;
	/** The pad corner of the engine heard alone, one of the nearest to the listener. */
	private static final BlockPos ONE = new BlockPos(8, 0, 6);
	/** How far either side of the middle the listener paces, and the ticks one round of it takes. */
	private static final double PACE = 12;
	private static final int ROUND = 400;

	private ShowcaseRunSoundBenchSoak() {
	}

	static void schedule(int bay) {
		ShowcaseRunSoundBench.Rig one = new ShowcaseRunSoundBench.Rig("soak", "Among sixteen singles", null,
			TestEngine.PRIMARY.offset(ONE), TestEngine.STARTER_POWER.offset(ONE), 0, 12, null,
			ShowcaseRunSoundBench.IDLE, 0);
		ShowcaseRunSoundBench.step("soak", "build", 60, () -> {
			ShowcaseRunSoundBench.useBay(one, bay);
			stand(0);
			ShowcaseRunSoundBench.teleport();
			ShowcaseRunSoundBench.onServer((level, player) -> {
				TestEngine.build(level, ShowcaseRunSoundBench.pad()
					.offset(ONE), true);
				ShowcaseRunSoundBench.fuel(level);
			});
		});
		ShowcaseRunSoundBench.startedRoomEngine("soak", "start_one", ONE);
		ShowcaseRunSoundBench.step("soak", "release_one", 100, () -> release(List.of(ShowcaseRunSoundBench.pad()
			.offset(ONE))));
		ShowcaseRunSoundBench.step("soak", "alone", 300, null);
		ShowcaseRunSoundBench.step("soak", "start_rest", 240, () -> ShowcaseRunSoundBench.onServer((level, player) -> {
			for (BlockPos corner : others())
				TestEngine.build(level, corner, true);
			ShowcaseRunSoundBench.fuel(level);
		}));
		ShowcaseRunSoundBench.step("soak", "release_rest", 100, () -> release(others()));
		ShowcaseRunSoundBench.step("soak", "crowd_early", 200, null);
		ShowcaseRunSoundBench.step("soak", "crowd_waiting", 600, null);
		ShowcaseRunSoundBench.step("soak", "crowd_settled", 600, null);
		ShowcaseRunSoundBench.during("soak", "pace", 2 * ROUND,
			t -> stand(PACE * Math.sin(2 * Math.PI * t / ROUND)));
		// Long enough to hear a loop come round again, if there were one
		ShowcaseRunSoundBench.step("soak", "stay", 1200, () -> stand(0));
		ShowcaseRunSoundBench.step("soak", "clear", 40, ShowcaseRunSoundBench::clearRoom);
	}

	private static void stand(double aside) {
		BlockPos pad = ShowcaseRunSoundBench.pad();
		ShowcaseRunSoundBench.listenAt(pad.getX() + MIDDLE_X + aside, pad.getZ() + MIDDLE_Z);
	}

	private static List<BlockPos> others() {
		BlockPos one = ShowcaseRunSoundBench.pad()
			.offset(ONE);
		return ShowcaseRunSoundBench.roomCorners()
			.stream()
			.filter(corner -> !corner.equals(one))
			.toList();
	}

	private static void release(List<BlockPos> corners) {
		ShowcaseRunSoundBench.onServer((level, player) -> {
			for (BlockPos corner : corners)
				level.removeBlock(corner.offset(TestEngine.STARTER_POWER), false);
		});
	}
}
