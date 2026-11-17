package com.createengines.gametest;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import com.createengines.CreateEngines;
import com.createengines.api.EngineApi;
import com.createengines.api.EngineMilestoneEvent;
import com.createengines.api.EngineStateChangeEvent;
import com.createengines.api.EngineStatus;
import com.createengines.api.EngineView;
import com.createengines.config.CEConfig;
import com.createengines.engine.sim.PartStats;
import com.createengines.registry.CEBlocks;
import com.createengines.registry.CEDataMaps;
import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.toml.TomlFormat;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** The tools for pack makers: the shipped config presets, the part stats data map, and the public API and its events. */
@GameTestHolder(CreateEngines.MOD_ID)
@PrefixGameTestTemplate(false)
public class PackToolsGameTests {

	private static CommentedConfig preset(String name) throws Exception {
		try (InputStream in = CreateEngines.class.getResourceAsStream("/config_presets/" + name + ".toml")) {
			if (in == null)
				throw new IllegalStateException("the jar has no preset " + name);
			return TomlFormat.instance()
				.createParser()
				.parse(new InputStreamReader(in, StandardCharsets.UTF_8));
		}
	}

	private static ModConfigSpec.ValueSpec specOf(String table, String key) {
		Object found = CEConfig.SPEC.getSpec()
			.get(List.of(table, key));
		return found instanceof ModConfigSpec.ValueSpec value ? value : null;
	}

	@GameTest(template = "empty")
	public static void everyShippedPresetOnlySetsRealSettingsInsideTheirRanges(GameTestHelper helper) throws Exception {
		List<String> problems = new ArrayList<>();
		for (String name : List.of("casual", "standard", "expert")) {
			CommentedConfig config = preset(name);
			int values = 0;
			for (String table : config.valueMap()
				.keySet()) {
				if (!table.equals("balance") && !table.equals("features")) {
					problems.add(name + " has a [" + table + "] table; presets only set [balance] and [features]");
					continue;
				}
				CommentedConfig inner = config.get(table);
				for (var entry : inner.valueMap()
					.entrySet()) {
					ModConfigSpec.ValueSpec spec = specOf(table, entry.getKey());
					if (spec == null) {
						problems.add(name + " sets " + table + "." + entry.getKey() + ", which is not a setting");
						continue;
					}
					values++;
					if (!spec.test(entry.getValue()))
						problems.add(name + " sets " + table + "." + entry.getKey() + " to " + entry.getValue() + ", outside its range");
				}
			}
			if (values < 16)
				problems.add(name + " sets only " + values + " settings; a preset names every [balance] and [features] key");
		}
		if (!problems.isEmpty())
			helper.fail(String.join("; ", problems));
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void theStandardPresetIsExactlyTheShippedDefaults(GameTestHelper helper) throws Exception {
		CommentedConfig config = preset("standard");
		List<String> problems = new ArrayList<>();
		for (String table : List.of("balance", "features")) {
			CommentedConfig inner = config.get(table);
			for (var entry : inner.valueMap()
				.entrySet()) {
				ModConfigSpec.ValueSpec spec = specOf(table, entry.getKey());
				Object preset = entry.getValue();
				Object standard = spec.getDefault();
				boolean same = preset instanceof Number a && standard instanceof Number b ? a.doubleValue() == b.doubleValue()
					: preset.equals(standard);
				if (!same)
					problems.add(table + "." + entry.getKey() + " is " + preset + " in the preset and " + standard + " by default");
			}
		}
		if (!problems.isEmpty())
			helper.fail(String.join("; ", problems));
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void withNoPackEntryTheCylindersAreUntouched(GameTestHelper helper) {
		for (boolean precision : new boolean[] { false, true })
			if (!CEDataMaps.partStats(precision)
				.isNone())
				helper.fail("a world with no part_stats entry should not retune the " + (precision ? "precision" : "plain") + " cylinder");
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void thePartStatsFormatTakesWhatItNamesAndRefusesTheRest(GameTestHelper helper) {
		var codec = CEDataMaps.PartStatsProperties.CODEC;
		JsonObject some = new JsonObject();
		some.addProperty("intake_flow", 1.5);
		some.addProperty("displacement", 2.0);
		var parsed = codec.parse(JsonOps.INSTANCE, some)
			.result();
		if (parsed.isEmpty())
			helper.fail("a partial entry should be fine");
		PartStats stats = parsed.get()
			.toStats();
		if (stats.intakeFlow() != 1.5 || stats.displacement() != 2.0 || stats.compressionRatio() != 1.0 || stats.injectorFlow() != 1.0)
			helper.fail("the named fields should be set and the rest left at 1: " + stats);
		if (!codec.parse(JsonOps.INSTANCE, new JsonObject())
			.result()
			.orElseThrow()
			.toStats()
			.isNone())
			helper.fail("an empty entry should change nothing");
		for (double bad : new double[] { 0, -1, 0.05, 11, 1e9, Double.NaN }) {
			JsonObject object = new JsonObject();
			object.addProperty("inertia", bad);
			if (codec.parse(JsonOps.INSTANCE, object)
				.result()
				.isPresent())
				helper.fail("inertia " + bad + " is out of range and should be refused");
		}
		JsonObject text = new JsonObject();
		text.addProperty("inertia", "heavy");
		if (codec.parse(JsonOps.INSTANCE, text)
			.result()
			.isPresent())
			helper.fail("text where a number belongs should be refused");
		helper.succeed();
	}

	/** Collects this engine's events off the real bus, and asks the API about it while it runs. */
	@GameTest(template = "empty", timeoutTicks = 3000)
	public static void theApiEventsAnnounceAStartAndAStallAndTheViewReadsTheEngine(GameTestHelper helper) {
		BlockPos primary = helper.absolutePos(TestEngine.PRIMARY);
		List<EngineStateChangeEvent> changes = new ArrayList<>();
		List<String> milestones = new ArrayList<>();
		Consumer<EngineStateChangeEvent> onChange = event -> {
			if (event.primary()
				.equals(primary))
				changes.add(event);
		};
		Consumer<EngineMilestoneEvent> onMilestone = event -> {
			if (event.primary()
				.equals(primary))
				milestones.add(event.milestone());
		};
		NeoForge.EVENT_BUS.addListener(onChange);
		NeoForge.EVENT_BUS.addListener(onMilestone);
		TestEngine.build(helper.getLevel(), helper.absolutePos(BlockPos.ZERO), true);
		boolean[] starved = {false};
		helper.runAtTickTime(300, () -> helper.setBlock(TestEngine.STARTER_POWER, Blocks.AIR));
		helper.runAtTickTime(600, () -> helper.assertTrue(starved[0], "the engine never announced a start: " + changes.stream()
			.map(c -> c.from() + ">" + c.to())
			.toList()));
		helper.runAtTickTime(2900, () -> helper.fail("the engine never announced a stall: " + changes.stream()
			.map(c -> c.from() + ">" + c.to())
			.toList()));
		helper.onEachTick(() -> {
			boolean started = changes.stream()
				.anyMatch(EngineStateChangeEvent::started);
			if (started && !starved[0]) {
				starved[0] = true;
				EngineView view = EngineApi.viewAt(helper.getLevel(), helper.absolutePos(TestEngine.CYLINDER))
					.orElse(null);
				if (view == null || view.cylinders() != 1)
					helper.fail("the view of a running engine should describe its one cylinder: " + view);
				if (!milestones.contains("engine_running"))
					helper.fail("starting should announce the engine_running milestone: " + milestones);
				helper.setBlock(TestEngine.EXHAUST, Blocks.AIR);
			}
			if (starved[0] && changes.stream()
				.anyMatch(EngineStateChangeEvent::stalled)) {
				NeoForge.EVENT_BUS.unregister(onChange);
				NeoForge.EVENT_BUS.unregister(onMilestone);
				helper.assertTrue(changes.stream()
					.allMatch(c -> c.from() != c.to()), "an event should only announce a change");
				helper.assertTrue(changes.get(0)
					.from() == EngineStatus.STALLED, "an engine begins stalled: " + changes.get(0)
						.from());
				helper.succeed();
			}
		});
	}

	@GameTest(template = "empty")
	public static void theViewOfAnythingThatIsNotAnEngineIsEmpty(GameTestHelper helper) {
		helper.setBlock(new BlockPos(2, 2, 2), Blocks.STONE);
		if (EngineApi.viewAt(helper.getLevel(), helper.absolutePos(new BlockPos(2, 2, 2)))
			.isPresent())
			helper.fail("stone is not an engine");
		helper.setBlock(new BlockPos(3, 2, 2), CEBlocks.CRANKSHAFT.getDefaultState());
		// A crankshaft that has not reported yet is not a view either
		if (EngineApi.viewAt(helper.getLevel(), helper.absolutePos(new BlockPos(3, 2, 2)))
			.isPresent())
			helper.fail("an engine that has not reported has no view");
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void theRecipeViewerPartsTableIsTrue(GameTestHelper helper) {
		// Every part the viewers say fits a block really installs into it, and every part is listed
		List<String> problems = new ArrayList<>();
		var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
		BlockPos at = new BlockPos(2, 2, 2);
		for (var part : com.createengines.client.compat.ViewerEntries.parts())
			for (net.minecraft.world.item.ItemStack block : part.blocks()) {
				var state = ((net.minecraft.world.item.BlockItem) block.getItem()).getBlock()
					.defaultBlockState();
				helper.getLevel()
					.setBlock(helper.absolutePos(at), state, 3);
				net.minecraft.world.item.ItemStack offered = part.part()
					.copy();
				player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, offered);
				var hit = new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(helper.absolutePos(at)),
					net.minecraft.core.Direction.UP, helper.absolutePos(at), false);
				state.useItemOn(offered, helper.getLevel(), player, net.minecraft.world.InteractionHand.MAIN_HAND, hit);
				String name = part.part()
					.getHoverName()
					.getString() + " into " + block.getHoverName()
						.getString();
				if (!offered.isEmpty())
					problems.add(name + " was not taken");
				// A filter is kept in the pan's own data, not in its block state; every other part is a state flag
				if (state.getBlock() != com.createengines.registry.CEBlocks.SUMP.get() && helper.getLevel()
					.getBlockState(helper.absolutePos(at)) == state)
					problems.add(name + " changed nothing");
			}
		// The other way: every part item of this mod is on a page
		for (var item : List.of(com.createengines.registry.CEItems.PISTON, com.createengines.registry.CEItems.FORGED_PISTON,
			com.createengines.registry.CEItems.CONNECTING_ROD, com.createengines.registry.CEItems.FUEL_INJECTOR,
			com.createengines.registry.CEItems.PRECISION_INJECTOR, com.createengines.registry.CEItems.SPARK_PLUG,
			com.createengines.registry.CEItems.ROTOR, com.createengines.registry.CEItems.OIL_FILTER))
			if (com.createengines.client.compat.ViewerEntries.parts()
				.stream()
				.noneMatch(part -> part.part()
					.is(item.get())))
				problems.add(item.getId() + " has no page");
		if (!problems.isEmpty())
			helper.fail(String.join("; ", problems));
		helper.succeed();
	}
}
