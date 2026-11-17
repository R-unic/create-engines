package com.createengines.gametest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.createengines.CreateEngines;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Old worlds must keep loading. These tests are the other half of the compatibility rules (see
 * {@code CompatManifestTest} and {@code BehaviourBaselineTest}): they load what an earlier version saved, not what
 * this version would save.
 * <ul>
 *     <li>{@code src/test/resources/compat/world/engine_1_4.snbt} is a running spark engine with a battery and an
 *     alternator, as 1.4 saved it ({@code engine_1_3.snbt} is the same as 1.3 saved it). It is loaded block by block and must come back with every saved key intact and
 *     keep running. The fixture is never regenerated once a version has shipped; a later version that cannot read it
 *     has broken old worlds. A new fixture for a new version is added beside it ({@code COMPAT_REGEN=1} writes the
 *     file only if it does not exist).</li>
 *     <li>Every block entity of the mod must load from an empty tag, which is what a block placed by a much older
 *     version looks like when a key did not exist yet.</li>
 * </ul>
 */
@GameTestHolder(CreateEngines.MOD_ID)
@PrefixGameTestTemplate(false)
public class LegacyWorldGameTests {

	private static final String FIXTURE = "engine_1_4.snbt";
	/** Create's kinetic bookkeeping, rebuilt by its network after a load rather than read back. */
	private static final java.util.Set<String> KINETIC_ONLY = java.util.Set.of("Network", "Source", "Stress", "Capacity");
	private static final int WIDTH = 9;
	private static final int HEIGHT = 7;
	private static final int DEPTH = 6;

	/** Finds {@code src/test/resources/compat/world} by climbing from the game test server's run directory. */
	private static Path fixtureDir() {
		Path dir = Path.of("").toAbsolutePath();
		for (int i = 0; i < 4 && dir != null; i++, dir = dir.getParent()) {
			Path candidate = dir.resolve("src/test/resources/compat/world");
			if (Files.isDirectory(candidate))
				return candidate;
			if (Files.isDirectory(dir.resolve("src/test/resources")))
				return candidate;
		}
		throw new IllegalStateException("Cannot find src/test/resources from " + Path.of("").toAbsolutePath());
	}

	/** Writes the fixture for this version once, when asked. Never overwrites a shipped fixture. */
	@GameTest(template = "empty_large", timeoutTicks = 400)
	public static void recordTheCurrentEngineWhenAsked(GameTestHelper helper) {
		if (System.getenv("COMPAT_REGEN") == null) {
			helper.succeed();
			return;
		}
		Path file = fixtureDir().resolve(FIXTURE);
		if (Files.exists(file)) {
			helper.succeed();
			return;
		}
		TestEngine.buildSpark(helper.getLevel(), helper.absolutePos(BlockPos.ZERO), true);
		helper.runAfterDelay(120, () -> {
			ListTag blocks = new ListTag();
			for (BlockPos pos : scan()) {
				BlockState state = helper.getBlockState(pos);
				if (state.isAir())
					continue;
				CompoundTag entry = new CompoundTag();
				entry.putIntArray("Pos", new int[] {pos.getX(), pos.getY(), pos.getZ()});
				entry.put("State", NbtUtils.writeBlockState(state));
				BlockEntity be = helper.getLevel().getBlockEntity(helper.absolutePos(pos));
				if (be != null)
					entry.put("Entity", be.saveWithFullMetadata(helper.getLevel().registryAccess()));
				blocks.add(entry);
			}
			CompoundTag root = new CompoundTag();
			root.put("Blocks", blocks);
			try {
				Files.createDirectories(file.getParent());
				Files.writeString(file, root.toString(), StandardCharsets.UTF_8);
			} catch (IOException e) {
				throw new IllegalStateException(e);
			}
			helper.succeed();
		});
	}

	@GameTest(template = "empty_large", timeoutTicks = 400)
	public static void anEngineSavedByThe13ReleaseLoadsAndKeepsItsData(GameTestHelper helper) {
		loadAndCheck(helper, "engine_1_3.snbt", "1.3");
	}

	@GameTest(template = "empty_large", timeoutTicks = 400)
	public static void anEngineSavedByThe14ReleaseLoadsAndKeepsItsData(GameTestHelper helper) {
		loadAndCheck(helper, FIXTURE, "1.4");
	}

	// The world corpus (verification track item 4): engines saved by the real releases, recorded by scripts/record-corpus.ps1
	// from each release's own source and never regenerated. Each must load, keep every saved key, and keep running.

	@GameTest(template = "empty_large", timeoutTicks = 400)
	public static void aDieselEngineSavedBy100Loads(GameTestHelper helper) {
		loadAndCheck(helper, "engine_v1_0_0_diesel.snbt", "1.0.0");
	}

	@GameTest(template = "empty_large", timeoutTicks = 400)
	public static void aSparkEngineSavedBy100Loads(GameTestHelper helper) {
		loadAndCheck(helper, "engine_v1_0_0_spark.snbt", "1.0.0");
	}

	@GameTest(template = "empty_large", timeoutTicks = 400)
	public static void aRotaryEngineSavedBy100Loads(GameTestHelper helper) {
		loadAndCheck(helper, "engine_v1_0_0_rotary.snbt", "1.0.0");
	}

	@GameTest(template = "empty_large", timeoutTicks = 400)
	public static void aDieselEngineSavedBy110Loads(GameTestHelper helper) {
		loadAndCheck(helper, "engine_v1_1_0_diesel.snbt", "1.1.0");
	}

	@GameTest(template = "empty_large", timeoutTicks = 400)
	public static void aSparkEngineSavedBy110Loads(GameTestHelper helper) {
		loadAndCheck(helper, "engine_v1_1_0_spark.snbt", "1.1.0");
	}

	@GameTest(template = "empty_large", timeoutTicks = 400)
	public static void aRotaryEngineSavedBy110Loads(GameTestHelper helper) {
		loadAndCheck(helper, "engine_v1_1_0_rotary.snbt", "1.1.0");
	}

	@GameTest(template = "empty_large", timeoutTicks = 400)
	public static void aDieselEngineSavedBy120Loads(GameTestHelper helper) {
		loadAndCheck(helper, "engine_v1_2_0_diesel.snbt", "1.2.0");
	}

	@GameTest(template = "empty_large", timeoutTicks = 400)
	public static void aSparkEngineSavedBy120Loads(GameTestHelper helper) {
		loadAndCheck(helper, "engine_v1_2_0_spark.snbt", "1.2.0");
	}

	@GameTest(template = "empty_large", timeoutTicks = 400)
	public static void aRotaryEngineSavedBy120Loads(GameTestHelper helper) {
		loadAndCheck(helper, "engine_v1_2_0_rotary.snbt", "1.2.0");
	}

	@GameTest(template = "empty_large", timeoutTicks = 400)
	public static void aDieselEngineSavedBy121Loads(GameTestHelper helper) {
		loadAndCheck(helper, "engine_v1_2_1_diesel.snbt", "1.2.1");
	}

	@GameTest(template = "empty_large", timeoutTicks = 400)
	public static void aSparkEngineSavedBy121Loads(GameTestHelper helper) {
		loadAndCheck(helper, "engine_v1_2_1_spark.snbt", "1.2.1");
	}

	@GameTest(template = "empty_large", timeoutTicks = 400)
	public static void aRotaryEngineSavedBy121Loads(GameTestHelper helper) {
		loadAndCheck(helper, "engine_v1_2_1_rotary.snbt", "1.2.1");
	}

	@GameTest(template = "empty_large", timeoutTicks = 400)
	public static void aDieselEngineSavedBy130Loads(GameTestHelper helper) {
		loadAndCheck(helper, "engine_v1_3_0_diesel.snbt", "1.3.0");
	}

	@GameTest(template = "empty_large", timeoutTicks = 400)
	public static void aSparkEngineSavedBy130Loads(GameTestHelper helper) {
		loadAndCheck(helper, "engine_v1_3_0_spark.snbt", "1.3.0");
	}

	@GameTest(template = "empty_large", timeoutTicks = 400)
	public static void aRotaryEngineSavedBy130Loads(GameTestHelper helper) {
		loadAndCheck(helper, "engine_v1_3_0_rotary.snbt", "1.3.0");
	}

	private static void loadAndCheck(GameTestHelper helper, String fixture, String saver) {
		Path file = fixtureDir().resolve(fixture);
		if (!Files.exists(file)) {
			if (System.getenv("COMPAT_REGEN") != null) {
				helper.succeed();
				return;
			}
			helper.fail("The saved-engine fixture is missing: " + file);
			return;
		}
		CompoundTag root;
		try {
			root = TagParser.parseTag(Files.readString(file, StandardCharsets.UTF_8));
		} catch (Exception e) {
			helper.fail("The saved-engine fixture does not parse: " + e);
			return;
		}
		var registries = helper.getLevel().registryAccess();
		ListTag blocks = root.getList("Blocks", Tag.TAG_COMPOUND);
		helper.assertTrue(!blocks.isEmpty(), "The fixture should hold blocks");

		for (int i = 0; i < blocks.size(); i++) {
			CompoundTag entry = blocks.getCompound(i);
			int[] p = entry.getIntArray("Pos");
			BlockPos pos = new BlockPos(p[0], p[1], p[2]);
			BlockState state = NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), entry.getCompound("State"));
			helper.setBlock(pos, state);
			if (!entry.contains("Entity"))
				continue;
			BlockEntity be = helper.getLevel().getBlockEntity(helper.absolutePos(pos));
			helper.assertTrue(be != null, "Block " + state + " at " + pos + " should have a block entity");
			be.loadWithComponents(entry.getCompound("Entity"), registries);
		}

		for (int i = 0; i < blocks.size(); i++) {
			CompoundTag entry = blocks.getCompound(i);
			if (!entry.contains("Entity"))
				continue;
			int[] p = entry.getIntArray("Pos");
			BlockPos pos = new BlockPos(p[0], p[1], p[2]);
			CompoundTag old = entry.getCompound("Entity");
			// Create's own keys come and go with its kinetic network; only this mod's saves are ours to keep
			if (!old.getString("id").startsWith(CreateEngines.MOD_ID + ":"))
				continue;
			CompoundTag now = helper.getLevel().getBlockEntity(helper.absolutePos(pos))
				.saveWithFullMetadata(registries);
			for (String key : old.getAllKeys())
				helper.assertTrue(KINETIC_ONLY.contains(key) || now.contains(key),
					"Key \"" + key + "\" saved by " + saver + " was lost after loading at " + pos + " ("
						+ old.getString("id") + ")");
		}

		helper.runAfterDelay(60, () -> {
			for (int i = 0; i < blocks.size(); i++) {
				CompoundTag entry = blocks.getCompound(i);
				if (!entry.contains("Entity"))
					continue;
				int[] p = entry.getIntArray("Pos");
				BlockPos pos = new BlockPos(p[0], p[1], p[2]);
				helper.assertTrue(helper.getLevel().getBlockEntity(helper.absolutePos(pos)) != null,
					"The block entity at " + pos + " should have survived running");
			}
			helper.succeed();
		});
	}

	@GameTest(template = "empty")
	public static void everyBlockEntityLoadsFromAnEmptySave(GameTestHelper helper) {
		var registries = helper.getLevel().registryAccess();
		BlockPos at = new BlockPos(3, 2, 3);
		int checked = 0;
		for (Block block : BuiltInRegistries.BLOCK) {
			if (!BuiltInRegistries.BLOCK.getKey(block).getNamespace().equals(CreateEngines.MOD_ID))
				continue;
			BlockState state = block.defaultBlockState();
			if (!state.hasBlockEntity())
				continue;
			helper.setBlock(at, state);
			BlockEntity be = helper.getLevel().getBlockEntity(helper.absolutePos(at));
			helper.assertTrue(be != null, BuiltInRegistries.BLOCK.getKey(block) + " should have a block entity");
			try {
				be.loadWithComponents(new CompoundTag(), registries);
			} catch (RuntimeException e) {
				helper.fail(BuiltInRegistries.BLOCK.getKey(block) + " failed to load an empty save: " + e);
				return;
			}
			checked++;
			helper.setBlock(at, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
		}
		helper.assertTrue(checked > 10, "Only " + checked + " block entity types were checked");
		helper.succeed();
	}

	private static Iterable<BlockPos> scan() {
		return BlockPos.betweenClosed(0, 0, 0, WIDTH - 1, HEIGHT - 1, DEPTH - 1);
	}
}
