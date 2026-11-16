package com.createengines.gametest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.createengines.CreateEngines;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Dropped into an OLD release's source tree by the world-corpus recorder script (scripts/record-corpus.ps1). It builds a
 * running engine with that release's own code, lets it run, and writes each block and block entity the way that release
 * saved them, to {@code $CE_CORPUS_OUT/engine_<CE_CORPUS_VERSION>_<kind>.snbt}. Never part of the current tree.
 */
@GameTestHolder(CreateEngines.MOD_ID)
@PrefixGameTestTemplate(false)
public class CorpusRecorder {

	private static void record(GameTestHelper helper, String kind, Runnable build) {
		String out = System.getenv("CE_CORPUS_OUT");
		String version = System.getenv("CE_CORPUS_VERSION");
		if (out == null || version == null) {
			helper.succeed();
			return;
		}
		build.run();
		helper.runAfterDelay(160, () -> {
			ListTag blocks = new ListTag();
			for (BlockPos pos : BlockPos.betweenClosed(0, 0, 0, 8, 6, 5)) {
				BlockPos fixed = pos.immutable();
				BlockState state = helper.getBlockState(fixed);
				if (state.isAir())
					continue;
				CompoundTag entry = new CompoundTag();
				entry.putIntArray("Pos", new int[] {fixed.getX(), fixed.getY(), fixed.getZ()});
				entry.put("State", NbtUtils.writeBlockState(state));
				BlockEntity be = helper.getLevel().getBlockEntity(helper.absolutePos(fixed));
				if (be != null)
					entry.put("Entity", be.saveWithFullMetadata(helper.getLevel().registryAccess()));
				blocks.add(entry);
			}
			CompoundTag root = new CompoundTag();
			root.put("Blocks", blocks);
			Path file = Path.of(out, "engine_" + version + "_" + kind + ".snbt");
			try {
				Files.createDirectories(file.getParent());
				Files.writeString(file, root.toString(), StandardCharsets.UTF_8);
			} catch (IOException e) {
				throw new IllegalStateException(e);
			}
			helper.succeed();
		});
	}

	@GameTest(template = "empty_large", timeoutTicks = 600)
	public static void recordDiesel(GameTestHelper helper) {
		record(helper, "diesel", () -> TestEngine.build(helper.getLevel(), helper.absolutePos(BlockPos.ZERO), true));
	}

	@GameTest(template = "empty_large", timeoutTicks = 600)
	public static void recordSpark(GameTestHelper helper) {
		record(helper, "spark", () -> TestEngine.buildSpark(helper.getLevel(), helper.absolutePos(BlockPos.ZERO), true));
	}

	@GameTest(template = "empty_large", timeoutTicks = 600)
	public static void recordRotary(GameTestHelper helper) {
		record(helper, "rotary", () -> TestEngine.buildRotary(helper.getLevel(), helper.absolutePos(BlockPos.ZERO), true));
	}
}
