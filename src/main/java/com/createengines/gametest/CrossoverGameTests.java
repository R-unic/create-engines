package com.createengines.gametest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import com.createengines.CreateEngines;
import com.createengines.advancement.LegacyProgress;
import com.createengines.compat.pollution.PollutionCompat;
import com.createengines.config.ConfigCarryOver;
import com.createengines.config.ConfigFiles;
import com.createengines.content.airflow.PortOpeningBlock;
import com.createengines.registry.CEBlocks;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 2.0's breaking cleanup and the other-mod crossovers, as the game sees them: a player's 1.x advancement file loading onto the
 * new rows, a 1.x config file being read, and (only with Pollution of the Realms in the run) gas leaving an outlet.
 */
@GameTestHolder(CreateEngines.MOD_ID)
@PrefixGameTestTemplate(false)
public class CrossoverGameTests {

	private static PlayerAdvancements load(MinecraftServer server, Path file) {
		return new PlayerAdvancements(server.getFixerUpper(), server.getPlayerList(), server.getAdvancements(), file, null);
	}

	private static AdvancementHolder holder(MinecraftServer server, String path) {
		AdvancementHolder holder = server.getAdvancements()
			.get(CreateEngines.asResource(path));
		if (holder == null)
			throw new IllegalStateException("no advancement " + path);
		return holder;
	}

	@GameTest(template = "empty")
	public static void aPlayersOneDotXAdvancementFileLoadsOntoTheNewRowsSilently(GameTestHelper helper) throws Exception {
		MinecraftServer server = helper.getLevel()
			.getServer();
		Path file = Files.createTempFile("old-advancements", ".json");
		try {
			String when = "2026-01-01 00:00:00 +0000";
			Files.writeString(file, "{\"DataVersion\":" + SharedConstants.getCurrentVersion()
				.getDataVersion()
				.getVersion() + ",\"create_engines:alive\":{\"criteria\":{\"alive\":\"" + when + "\"},\"done\":true},"
				+ "\"create_engines:vee_engine\":{\"criteria\":{\"vee_engine\":\"" + when + "\"},\"done\":true},"
				+ "\"create_engines:maf_trim\":{\"criteria\":{\"maf_trim\":\"" + when + "\"},\"done\":true},"
				+ "\"create_engines:throttle\":{\"criteria\":{\"throttle\":\"" + when + "\"},\"done\":true},"
				+ "\"create_engines:power_plant\":{\"criteria\":{\"redline\":\"" + when + "\"},\"done\":false}}");
			PlayerAdvancements advancements = load(server, file);
			if (!advancements.getOrStartProgress(holder(server, "first_light/alive"))
				.isDone())
				helper.fail("It's Alive! was earned on 1.x and is not credited on first_light/alive");
			if (!advancements.getOrStartProgress(holder(server, "builder/vee_engine"))
				.isDone())
				helper.fail("a vee engine was earned on 1.x and is not credited");
			AdvancementProgress fineTuner = advancements.getOrStartProgress(holder(server, "tuning/fine_tuner"));
			if (fineTuner.getCriterion("maf_trim") == null || !fineTuner.getCriterion("maf_trim")
				.isDone())
				helper.fail("the hidden MAF trim row did not credit the Fine Tuner capstone");
			if (fineTuner.isDone())
				helper.fail("one milestone must not finish a capstone");
			AdvancementProgress power = advancements.getOrStartProgress(holder(server, "power/power_plant"));
			if (power.getCriterion("redline") == null || !power.getCriterion("redline")
				.isDone())
				helper.fail("a capstone's earned milestone was not carried over");
			if (advancements.getOrStartProgress(holder(server, "fuel/diesel"))
				.hasProgress())
				helper.fail("something the player never earned was credited");
			// Reading the file again is the same: it can run on every load until the game saves
			PlayerAdvancements again = load(server, file);
			if (!again.getOrStartProgress(holder(server, "first_light/alive"))
				.isDone())
				helper.fail("a second load lost the progress");
		} finally {
			Files.deleteIfExists(file);
		}
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void aMissingOddOrBrokenAdvancementFileCarriesNothingAndNeverStopsALoad(GameTestHelper helper)
		throws Exception {
		MinecraftServer server = helper.getLevel()
			.getServer();
		Path missing = Files.createTempDirectory("no-advancements")
			.resolve("nobody.json");
		load(server, missing);
		if (!LegacyProgress.readEarned(missing)
			.isEmpty())
			helper.fail("a missing file has no progress");
		Path odd = Files.createTempFile("odd-advancements", ".json");
		try {
			for (String text : new String[] { "[]", "7", "{}", "{\"create_engines:alive\": 7}",
				"{\"create_engines:alive\": {\"criteria\": 5}}", "{\"create_engines:alive\": {\"criteria\": {}}}",
				"{\"other_mod:alive\": {\"criteria\": {\"alive\": \"x\"}}}",
				"{\"create_engines:never_existed\": {\"criteria\": {\"x\": \"y\"}}}" }) {
				Files.writeString(odd, text);
				if (!LegacyProgress.readEarned(odd)
					.isEmpty())
					helper.fail("nothing should be carried from " + text);
			}
			Files.writeString(odd, "{\"create_engines:alive\": [1, 2,");
			try {
				LegacyProgress.readEarned(odd);
				helper.fail("a broken file must say so, so the caller logs it and loads the player anyway");
			} catch (RuntimeException expected) {
				// carryOver catches this and logs a warning
			}
		} finally {
			Files.deleteIfExists(odd);
			Files.deleteIfExists(missing.getParent());
		}
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void aOneDotXConfigFileIsReadValueForValue(GameTestHelper helper) throws Exception {
		Path file = Files.createTempFile("old-config", ".toml");
		try {
			Files.writeString(file, "[engine]\n\tstressPerTorque = 40.0\n\tmaxCrankSegments = 128\n"
				+ "[balance]\n\tfuelConsumption = 0.6\n[features]\n\tsparkFouling = false\n");
			Map<String, Object> flat = ConfigFiles.read(file);
			ConfigCarryOver.Result result = ConfigCarryOver.carry(ConfigCarryOver.SERVER, flat);
			if (!Double.valueOf(40.0)
				.equals(result.moved()
					.get("calibration.stressPerTorque")))
				helper.fail("stressPerTorque did not carry: " + result.moved());
			if (!Integer.valueOf(128)
				.equals(result.moved()
					.get("limits.maxCrankSegments")))
				helper.fail("maxCrankSegments did not carry: " + result.moved());
			if (!Boolean.FALSE.equals(result.moved()
				.get("features.sparkFouling")))
				helper.fail("sparkFouling did not carry: " + result.moved());
			if (!result.skipped()
				.isEmpty())
				helper.fail("nothing in the file should have been left behind: " + result.skipped());
			Files.writeString(file, "not = [valid toml");
			try {
				ConfigFiles.read(file);
				helper.fail("a broken file must say so, so the 2.0 defaults stay");
			} catch (RuntimeException expected) {
				// The caller logs this and keeps the defaults
			}
		} finally {
			Files.deleteIfExists(file);
		}
		helper.succeed();
	}

	/** Runs only with Pollution of the Realms in the run (runGameTestServerPollution); without it there is nothing to check. */
	@GameTest(template = "empty")
	public static void anOpenOutletPutsGasInFrontOfItAndAFloodedOneDoesNot(GameTestHelper helper) {
		if (!PollutionCompat.installed()) {
			helper.succeed();
			return;
		}
		BlockPos open = new BlockPos(2, 2, 2);
		BlockPos flooded = new BlockPos(5, 2, 2);
		BlockState outlet = CEBlocks.EXHAUST_OUTLET.getDefaultState()
			.setValue(PortOpeningBlock.FACING, Direction.UP);
		helper.setBlock(open, outlet);
		helper.setBlock(flooded, outlet);
		helper.setBlock(flooded.above(), Blocks.WATER);
		PollutionCompat.release(helper.getLevel(), helper.absolutePos(open), 40, 10);
		PollutionCompat.release(helper.getLevel(), helper.absolutePos(flooded), 40, 10);
		helper.runAfterDelay(2, () -> {
			if (helper.getBlockState(open.above())
				.isAir())
				helper.fail("no gas appeared in front of an open outlet");
			if (!helper.getBlockState(flooded.above())
				.is(Blocks.WATER))
				helper.fail("gas went into a flooded outlet");
			helper.succeed();
		});
	}

	/** The port curve on a real, scanned engine and on layouts built by hand, so the numbers are pinned where the game reads them. */
	@GameTest(template = "empty")
	public static void aScannedEnginesBreathingFollowsThePortCurve(GameTestHelper helper) {
		TestEngine.build(helper.getLevel(), helper.absolutePos(BlockPos.ZERO), true);
		com.createengines.engine.topology.EngineLayout layout = com.createengines.engine.topology.EngineScanner
			.scan(helper.getLevel(), helper.absolutePos(TestEngine.PRIMARY));
		if (layout == null || layout.ports().isEmpty())
			helper.fail("the test engine did not scan");
		for (var port : layout.ports())
			if (Math.abs(port.flowFactor() - com.createengines.engine.sim.PortFlow.factor(1, 1)) > 1e-9)
				helper.fail("one opening for one head should breathe at " + com.createengines.engine.sim.PortFlow.factor(1, 1)
					+ " but " + port.kind() + " gave " + port.flowFactor());
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void openingsBesideEachOtherBreatheLessThanOnesApart(GameTestHelper helper) {
		java.util.List<BlockPos> apart = java.util.List.of(new BlockPos(0, 0, 0), new BlockPos(5, 0, 0), new BlockPos(0, 0, 5));
		java.util.List<BlockPos> crowded = java.util.List.of(new BlockPos(0, 0, 0), new BlockPos(1, 0, 0), new BlockPos(1, 1, 1));
		double spread = network(apart, 3).flowFactor();
		double bunched = network(crowded, 3).flowFactor();
		if (!(bunched < spread))
			helper.fail("three crowded openings (" + bunched + ") must breathe less than three apart (" + spread + ")");
		if (Math.abs(spread - com.createengines.engine.sim.PortFlow.factor(3, 3)) > 1e-9)
			helper.fail("openings apart are worth one each: " + spread);
		if (network(java.util.List.of(), 3).flowFactor() != 0)
			helper.fail("no openings, no breathing");
		helper.succeed();
	}

	private static com.createengines.engine.topology.EngineLayout.PortNetwork network(java.util.List<BlockPos> outlets,
		int heads) {
		java.util.List<BlockPos> none = java.util.List.of();
		return new com.createengines.engine.topology.EngineLayout.PortNetwork(com.createengines.content.airflow.PortKind.INTAKE,
			outlets.size(), 0, heads, outlets, none, none, none, none, none, java.util.Set.of(), none, none);
	}

	/** Parts as data: the loader read the shipped files, and a broken file is refused with a message instead of crashing a reload. */
	@GameTest(template = "empty")
	public static void thePartEffectFilesWereLoadedAndABrokenOneIsRefused(GameTestHelper helper) {
		for (var part : com.createengines.engine.sim.PartEffects.Part.values())
			if (!com.createengines.engine.sim.PartEffects.of(part)
				.equals(com.createengines.engine.sim.PartEffects.BUILT_IN.get(part)))
				helper.fail(part + " is not the built-in effect, although the shipped file says it is");
		com.google.gson.JsonObject bad = new com.google.gson.JsonObject();
		bad.addProperty("breathing", "fast");
		for (com.google.gson.JsonElement broken : new com.google.gson.JsonElement[] { bad, new com.google.gson.JsonArray(),
			com.google.gson.JsonNull.INSTANCE, new com.google.gson.JsonPrimitive(3) }) {
			try {
				com.createengines.engine.PartEffectLoader.parse(broken);
				helper.fail("a broken part effect file was accepted: " + broken);
			} catch (IllegalArgumentException expected) {
				// logged by the loader, which then keeps the built-in values
			}
		}
		com.google.gson.JsonObject good = new com.google.gson.JsonObject();
		good.addProperty("breathing", 2);
		if (com.createengines.engine.PartEffectLoader.parse(good)
			.breathing() != 2)
			helper.fail("a good file was not read");
		helper.succeed();
	}
}
