package com.createengines.command;

import java.util.List;

import com.createengines.CreateEngines;
import com.createengines.config.ConfigPresets;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/**
 * {@code /ce}, the pack maker's window onto the mod: it changes nothing but {@code /ce preset}, and answers the questions a
 * pack maker otherwise has to read a log for ("did my fuel register?", "did my config apply?", "what is the addon doing?").
 * The reports are in {@link PackReport}. Needs game-master permission; {@code preset} needs an admin, since it rewrites the
 * world's server config.
 */
public final class CeCommand {

	private CeCommand() {
	}

	public static void register(RegisterCommandsEvent event) {
		register(event.getDispatcher());
	}

	static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("ce")
			.requires(source -> source.hasPermission(2))
			.executes(context -> send(context.getSource(), PackReport.help()))
			.then(Commands.literal("help")
				.executes(context -> send(context.getSource(), PackReport.help())))
			.then(Commands.literal("config")
				.executes(context -> send(context.getSource(), PackReport.config())))
			.then(Commands.literal("fuels")
				.executes(context -> send(context.getSource(), PackReport.fuels())))
			.then(Commands.literal("parts")
				.executes(context -> send(context.getSource(), PackReport.parts())))
			.then(Commands.literal("api")
				.executes(context -> send(context.getSource(), PackReport.api())))
			.then(Commands.literal("preset")
				.requires(source -> source.hasPermission(3))
				.executes(context -> send(context.getSource(), List.of("Presets: " + String.join(", ", ConfigPresets.NAMES)
					+ ". /ce preset <name> sets the [balance] and [features] settings to that preset.")))
				.then(Commands.argument("name", StringArgumentType.word())
					.suggests((context, builder) -> SharedSuggestionProvider.suggest(ConfigPresets.NAMES, builder))
					.executes(context -> preset(context.getSource(), StringArgumentType.getString(context, "name"))))));
	}

	private static int preset(CommandSourceStack source, String name) {
		if (!ConfigPresets.exists(name)) {
			source.sendFailure(Component.literal("There is no preset called " + name + ". The presets are " + String.join(", ", ConfigPresets.NAMES) + "."));
			return 0;
		}
		ConfigPresets.Result result = ConfigPresets.apply(name);
		List<String> lines = new java.util.ArrayList<>();
		lines.add("Preset " + name + ": " + result.changed()
			.size() + " settings changed, " + result.same() + " already had that value. Saved to the world's server config.");
		lines.addAll(result.changed()
			.stream()
			.map(line -> "  " + line)
			.toList());
		send(source, lines);
		for (String problem : result.problems())
			source.sendFailure(Component.literal(problem));
		return result.changed()
			.size();
	}

	private static int send(CommandSourceStack source, List<String> lines) {
		for (String line : lines)
			source.sendSuccess(() -> Component.literal(line)
				.withStyle(line.contains(" ! ") || line.startsWith("    !") ? ChatFormatting.RED : ChatFormatting.WHITE), false);
		return lines.size();
	}

	/** Logs fuels a pack wrote with numbers that cannot be right, once the data is loaded. Silent when there are none. */
	public static void onServerStarted(ServerStartedEvent event) {
		for (String problem : PackReport.fuelProblems())
			CreateEngines.LOGGER.warn("A fuel a data pack supplies has an impossible number and will misbehave: {}", problem);
	}
}
