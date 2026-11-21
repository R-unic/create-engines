package com.createengines.gametest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.createengines.CreateEngines;
import com.createengines.api.EngineExtensions;
import com.createengines.api.EngineSourceProvider;
import com.createengines.command.PackReport;
import com.createengines.config.CEConfig;
import com.createengines.config.ConfigPresets;
import com.createengines.engine.sim.Tuning;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** {@code /ce}: the reports say what is true of the world, the preset command moves exactly what it says, and permissions hold. */
@GameTestHolder(CreateEngines.MOD_ID)
@PrefixGameTestTemplate(false)
public class PackCommandGameTests {

	/** A command source that keeps what it is sent. */
	private static final class Sink implements CommandSource {
		final List<String> said = new ArrayList<>();

		@Override
		public void sendSystemMessage(Component message) {
			said.add(message.getString());
		}

		@Override
		public boolean acceptsSuccess() {
			return true;
		}

		@Override
		public boolean acceptsFailure() {
			return true;
		}

		@Override
		public boolean shouldInformAdmins() {
			return false;
		}
	}

	private static CommandSourceStack stack(GameTestHelper helper, Sink sink, int permission) {
		MinecraftServer server = helper.getLevel()
			.getServer();
		return new CommandSourceStack(sink, Vec3.ZERO, Vec2.ZERO, helper.getLevel(), permission, "test", Component.literal("test"), server, null);
	}

	private static List<String> run(GameTestHelper helper, String command, int permission) throws CommandSyntaxException {
		Sink sink = new Sink();
		helper.getLevel()
			.getServer()
			.getCommands()
			.getDispatcher()
			.execute(command, stack(helper, sink, permission));
		return sink.said;
	}

	private static boolean mentions(List<String> lines, String text) {
		return lines.stream()
			.anyMatch(line -> line.contains(text));
	}

	@GameTest(template = "empty")
	public static void theFuelReportListsTheShippedFuelsAndFindsNothingWrongWithThem(GameTestHelper helper) throws Exception {
		List<String> lines = run(helper, "ce fuels", 2);
		for (String fuel : List.of("create_engines:diesel", "create_engines:gasoline", "create_engines:ethanol",
			"create_engines:plant_oil", "create_engines:used_oil"))
			if (!mentions(lines, fuel + "   from its data map entry"))
				helper.fail(fuel + " should be listed as a data map fuel: " + lines);
		if (lines.stream()
			.anyMatch(line -> line.startsWith("    !") || line.startsWith("    ?")))
			helper.fail("the shipped fuels have nothing wrong with them: " + lines);
		if (!PackReport.fuelProblems()
			.isEmpty())
			helper.fail("the start-up check should be silent for the shipped fuels: " + PackReport.fuelProblems());
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void theFuelReportSaysWhereEachFuelComesFrom(GameTestHelper helper) throws Exception {
		List<String> lines = run(helper, "ce fuels", 2);
		// The count line, and the line that says how a fluid becomes a fuel
		if (!mentions(lines, "fluids are engine fuels"))
			helper.fail("no count: " + lines);
		if (!mentions(lines, "c:gasoline"))
			helper.fail("the report should say the fuel tags: " + lines);
		if (!mentions(lines, "create_engines:kerosene   from the tag #c:kerosene"))
			helper.fail("kerosene comes in by its tag, no data map entry, and the report should say so: " + lines);
		if (!mentions(lines, "create_engines:methanol   from the tag #c:methanol"))
			helper.fail("methanol comes in by its tag too, and the report should say so: " + lines);
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void withNoPackTheCylinderPartsAreBuiltInAndNothingIsScaled(GameTestHelper helper) throws Exception {
		List<String> lines = run(helper, "ce parts", 2);
		for (String part : List.of("bore", "head", "piston", "injector"))
			if (!mentions(lines, "  " + part + ": built-in values"))
				helper.fail(part + " should be reported as built-in: " + lines);
		long none = lines.stream()
			.filter(line -> line.endsWith("no entry"))
			.count();
		if (none != 3)
			helper.fail("the three part stats entries should all be empty: " + lines);
		if (!mentions(lines, "part_effects/bore.json: used"))
			helper.fail("the shipped bore file should be reported as read: " + lines);
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void withNoAddonNoBlockNamesAPartOfItsOwn(GameTestHelper helper) throws Exception {
		List<String> lines = run(helper, "ce parts", 2);
		if (!mentions(lines, "none: no block names a part effect"))
			helper.fail("with no addon the own-parts section should say none: " + lines);
		helper.succeed();
	}

	/** Alone in its batch: it answers the part_effect data map for a block, as OwnPartGameTests does, and undoes it. */
	@GameTest(template = "empty", batch = "pack-own-parts")
	public static void aBlockThatNamesAPartOfItsOwnIsListedWithWhatThePartDoes(GameTestHelper helper) throws Exception {
		String effect = "test:racing_bore";
		try {
			com.createengines.engine.sim.PartEffects.set(Map.of(), Map.of(effect, new com.createengines.engine.sim.PartEffects.Effect(1.3, 1, 1, 1, 1, 1, 1.4, 1)));
			com.createengines.registry.CEDataMaps.partEffectOverride = block -> block == com.createengines.registry.CEBlocks.CYLINDER_HEAD.get() ? effect : null;
			List<String> lines = run(helper, "ce parts", 2);
			if (!mentions(lines, "create_engines:cylinder_head -> test:racing_bore   compression_ratio x1.3, inertia x1.4"))
				helper.fail("the block, its effect and what the effect does should be listed: " + lines);
			if (mentions(lines, "adds nothing"))
				helper.fail("an effect that was read should not be flagged: " + lines);
			// A block that names an effect no file supplies is flagged, since it would quietly do nothing
			com.createengines.registry.CEDataMaps.partEffectOverride = block -> block == com.createengines.registry.CEBlocks.CYLINDER_HEAD.get() ? "test:missing" : null;
			if (!mentions(run(helper, "ce parts", 2), "no part_effects file by that id was read"))
				helper.fail("a block that names a missing effect should be flagged");
		} finally {
			com.createengines.registry.CEDataMaps.partEffectOverride = null;
			com.createengines.engine.sim.PartEffects.reset();
		}
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void theDumpWritesEveryReportAndTheModsLoadedToAFile(GameTestHelper helper) throws Exception {
		java.nio.file.Path file = helper.getLevel()
			.getServer()
			.getServerDirectory()
			.resolve(com.createengines.command.CeCommand.DUMP_FILE);
		java.nio.file.Files.deleteIfExists(file);
		try {
			List<String> said = run(helper, "ce dump", 2);
			if (!mentions(said, "Wrote ") || !mentions(said, com.createengines.command.CeCommand.DUMP_FILE))
				helper.fail("the command should say where it wrote: " + said);
			if (!java.nio.file.Files.exists(file))
				helper.fail("no file was written at " + file);
			List<String> written = java.nio.file.Files.readAllLines(file);
			for (String expected : List.of("Create: Engine Engineer dump", "Mods loaded:", "  create_engines ", "  minecraft ", "API " + com.createengines.api.ApiVersion.VERSION,
				"server setting", "fluids are engine fuels", "create_engines:diesel", "Part effects (", "Cam profiles (", "Parts of a pack's or an addon's own"))
				if (!mentions(written, expected))
					helper.fail("the dump should contain \"" + expected + "\"");
			if (written.size() < 50)
				helper.fail("a dump of everything should be long: " + written.size() + " lines");
		} finally {
			java.nio.file.Files.deleteIfExists(file);
		}
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void theDumpNeedsGameMasterPermission(GameTestHelper helper) throws Exception {
		try {
			run(helper, "ce dump", 1);
			helper.fail("a player below game master ran /ce dump");
		} catch (CommandSyntaxException expected) {
			// not an allowed command for them, as for every /ce command
		}
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void theApiReportShowsTheVersionAndEveryRegisteredProvider(GameTestHelper helper) throws Exception {
		if (!mentions(run(helper, "ce api", 2), "API " + com.createengines.api.ApiVersion.VERSION))
			helper.fail("the API version is missing");
		EngineSourceProvider provider = (level, build) -> List.of();
		EngineExtensions.register(provider);
		try {
			if (!mentions(run(helper, "ce api", 2), provider.getClass()
				.getName()))
				helper.fail("the registered provider should be listed");
		} finally {
			EngineExtensions.unregister(provider);
		}
		if (mentions(run(helper, "ce api", 2), provider.getClass()
			.getName()))
			helper.fail("an unregistered provider should not be listed");
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void theConfigReportMarksTheSettingsThatDifferFromTheDefaults(GameTestHelper helper) throws Exception {
		ModConfigSpec.DoubleValue value = CEConfig.FUEL_CONSUMPTION;
		double before = value.get();
		try {
			value.set(1.0);
			List<String> plain = run(helper, "ce config", 2);
			if (mentions(plain, "* balance.fuelConsumption"))
				helper.fail("a setting at its default should not be marked: " + plain);
			value.set(2.5);
			List<String> changed = run(helper, "ce config", 2);
			if (!mentions(changed, "* balance.fuelConsumption = 2.5   (default 1.0)"))
				helper.fail("a changed setting should be marked with its default: " + changed);
		} finally {
			value.set(before);
		}
		helper.succeed();
	}

	/** Every setting the config report lists is a setting of the config, and every one is listed. */
	@GameTest(template = "empty")
	public static void theConfigReportListsEverySetting(GameTestHelper helper) throws Exception {
		List<String> lines = run(helper, "ce config", 2);
		for (String path : CEConfig.values()
			.keySet())
			if (!mentions(lines, " " + path + " = "))
				helper.fail(path + " is missing from the report");
		helper.succeed();
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@GameTest(template = "empty")
	public static void aPresetMovesExactlyTheSettingsItNamesAndCanBeUndone(GameTestHelper helper) throws Exception {
		Map<String, Object> before = new LinkedHashMap<>();
		CEConfig.values()
			.forEach((path, value) -> before.put(path, value.get()));
		// All inside one tick, so no other test sees the preset's numbers in the simulation
		try {
			ConfigPresets.Result expert = ConfigPresets.apply("expert");
			if (!expert.problems()
				.isEmpty())
				helper.fail("the expert preset should apply cleanly: " + expert.problems());
			if (CEConfig.FUEL_CONSUMPTION.get() != 1.5 || Tuning.fuelConsumption != 1.5)
				helper.fail("expert asks for 1.5 fuel consumption, in the config and in the simulation: " + CEConfig.FUEL_CONSUMPTION.get()
					+ ", " + Tuning.fuelConsumption);
			for (String path : CEConfig.values()
				.keySet())
				if (!path.startsWith("balance.") && !path.startsWith("features.") && !CEConfig.values()
					.get(path)
					.get()
					.equals(before.get(path)))
					helper.fail("a preset must leave " + path + " alone");
			ConfigPresets.Result again = ConfigPresets.apply("expert");
			if (!again.changed()
				.isEmpty())
				helper.fail("applying the same preset twice changes nothing the second time: " + again.changed());
			ConfigPresets.Result standard = ConfigPresets.apply("standard");
			if (CEConfig.FUEL_CONSUMPTION.get() != 1.0 || Tuning.fuelConsumption != 1.0)
				helper.fail("standard puts fuel consumption back to 1: " + CEConfig.FUEL_CONSUMPTION.get());
			if (standard.changed()
				.isEmpty())
				helper.fail("standard after expert must move something back");
		} finally {
			before.forEach((path, raw) -> ((ModConfigSpec.ConfigValue) CEConfig.values()
				.get(path)).set(raw));
			CEConfig.applyTuning();
		}
		helper.succeed();
	}

	/** The command itself, not just {@code ConfigPresets}: it says how many settings moved, lists each, and the config really changes. */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	@GameTest(template = "empty")
	public static void theCommandAppliesAPresetAndSaysWhatItChanged(GameTestHelper helper) throws Exception {
		Map<String, Object> before = new LinkedHashMap<>();
		CEConfig.values()
			.forEach((path, value) -> before.put(path, value.get()));
		try {
			List<String> lines = run(helper, "ce preset expert", 3);
			if (!mentions(lines, "Preset expert: "))
				helper.fail("the command should report the preset it applied: " + lines);
			if (!mentions(lines, "Saved to the world's server config"))
				helper.fail("the command should say where the settings went: " + lines);
			if (!mentions(lines, "balance.fuelConsumption"))
				helper.fail("the command should list each setting it changed: " + lines);
			if (CEConfig.FUEL_CONSUMPTION.get() != 1.5 || Tuning.fuelConsumption != 1.5)
				helper.fail("expert asks for 1.5 fuel consumption, in the config and in the simulation: " + CEConfig.FUEL_CONSUMPTION.get()
					+ ", " + Tuning.fuelConsumption);
			List<String> again = run(helper, "ce preset expert", 3);
			if (!mentions(again, "0 settings changed"))
				helper.fail("the same preset twice changes nothing the second time: " + again);
		} finally {
			before.forEach((path, raw) -> ((ModConfigSpec.ConfigValue) CEConfig.values()
				.get(path)).set(raw));
			CEConfig.applyTuning();
		}
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void anUnknownPresetIsRefusedAndTheListNamesTheRealOnes(GameTestHelper helper) throws Exception {
		try {
			ConfigPresets.apply("brutal");
			helper.fail("there is no preset called brutal");
		} catch (IllegalArgumentException expected) {
			if (!expected.getMessage()
				.contains("expert"))
				helper.fail("the refusal should name the real presets: " + expected.getMessage());
		}
		List<String> refused = run(helper, "ce preset brutal", 3);
		if (!mentions(refused, "no preset called brutal"))
			helper.fail("the command should not claim success: " + refused);
		if (!mentions(run(helper, "ce preset", 3), "casual, standard, expert"))
			helper.fail("the bare command lists the presets");
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void thePermissionsHold(GameTestHelper helper) throws Exception {
		for (String command : List.of("ce", "ce config", "ce fuels", "ce parts", "ce api", "ce preset"))
			try {
				run(helper, command, 1);
				helper.fail("/" + command + " should need game-master permission");
			} catch (CommandSyntaxException expected) {
				// refused, as it should be
			}
		for (String command : List.of("ce preset standard", "ce preset"))
			try {
				run(helper, command, 2);
				helper.fail("/" + command + " rewrites the world's config and should need an admin");
			} catch (CommandSyntaxException expected) {
				// refused
			}
		helper.succeed();
	}
}
