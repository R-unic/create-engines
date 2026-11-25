package com.createengines.client.smoke;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import com.createengines.CreateEngines;
import com.createengines.content.WearService;
import com.createengines.content.WornPartItem;
import com.createengines.content.blueprint.RecordedBlueprint;
import com.createengines.engine.sim.Tuning;
import com.createengines.registry.CEComponents;
import com.createengines.smoke.SmokeReport;
import com.createengines.smoke.audit.ServerAudit;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.api.equipment.goggles.IHaveHoveringInformation;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * The words a player reads off this mod's blocks and items: every goggle and hover readout of every block entity the client holds,
 * and every item's tooltip, with the wear mode both ways. A readout runs only when somebody looks, so no server test reaches it,
 * and one that throws or claims lines it did not write takes the client down (a Cast Flywheel did, in 2.1.0).
 */
final class ReadoutAudit {

	/** A part's wear as a world, a command or another mod might leave it on a stack, beside the sane values. */
	private static final double[] HOSTILE_WEAR = {-1, 0, 0.5, 1, 2, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY};

	/** How far from the player, in chunks, readouts are looked for. */
	private static final int CHUNK_RADIUS = 8;

	private ReadoutAudit() {
	}

	/** Everything wrong with the lines of one readout: untranslated keys, and figures that are not numbers. */
	private static void inspect(List<Component> lines, Set<String> problems) {
		for (Component line : lines) {
			inspectKeys(line, problems);
			String text = line.getString();
			if (text.contains("NaN") || text.contains("Infinity"))
				problems.add("shows a figure that is not a number: \"" + text.trim() + "\"");
		}
	}

	private static void inspectKeys(Component line, Set<String> problems) {
		if (line.getContents() instanceof TranslatableContents translatable) {
			if (translatable.getKey()
				.contains(CreateEngines.MOD_ID) && !Language.getInstance()
					.has(translatable.getKey()))
				problems.add("has no translation for " + translatable.getKey());
			for (Object argument : translatable.getArgs())
				if (argument instanceof Component component)
					inspectKeys(component, problems);
		}
		for (Component sibling : line.getSiblings())
			inspectKeys(sibling, problems);
	}

	/** Reads every goggle and hover readout of the mod's block entities near the player, one check per block. */
	static void blockReadouts(SmokeReport report, String when) {
		Minecraft minecraft = Minecraft.getInstance();
		var level = minecraft.level;
		if (level == null || minecraft.player == null) {
			report.skip("gui", "readouts " + when, "not in a world");
			return;
		}
		BlockPos centre = minecraft.player.blockPosition();
		Map<String, Set<String>> byBlock = new TreeMap<>();
		int read = 0;
		boolean wearMode = Tuning.wearMode;
		try {
			for (int dx = -CHUNK_RADIUS; dx <= CHUNK_RADIUS; dx++)
				for (int dz = -CHUNK_RADIUS; dz <= CHUNK_RADIUS; dz++) {
					var chunk = level.getChunkSource()
						.getChunkNow((centre.getX() >> 4) + dx, (centre.getZ() >> 4) + dz);
					if (chunk == null)
						continue;
					for (BlockEntity entity : new ArrayList<>(chunk.getBlockEntities()
						.values())) {
						var id = BuiltInRegistries.BLOCK.getKey(entity.getBlockState()
							.getBlock());
						if (!ServerAudit.ours(id)
							|| !(entity instanceof IHaveGoggleInformation || entity instanceof IHaveHoveringInformation))
							continue;
						read++;
						Set<String> problems = byBlock.computeIfAbsent(id.getPath(), key -> new TreeSet<>());
						for (boolean wear : new boolean[] {false, true}) {
							Tuning.wearMode = wear;
							for (boolean sneaking : new boolean[] {false, true})
								readOne(entity, sneaking, "wear mode " + wear + ", sneaking " + sneaking, problems);
						}
					}
				}
		} finally {
			Tuning.wearMode = wearMode;
		}
		report.metric("block entity readouts read " + when, read, "");
		report.check("gui", "there were readouts to read " + when, read > 0, "no block entity of the mod with a readout was loaded");
		byBlock.forEach((block, problems) -> report.check("gui", block + " readouts " + when, problems.isEmpty(),
			String.join("; ", problems.stream()
				.limit(3)
				.toList())));
	}

	private static void readOne(BlockEntity entity, boolean sneaking, String how, Set<String> problems) {
		try {
			if (entity instanceof IHaveGoggleInformation goggles) {
				List<Component> lines = new ArrayList<>();
				boolean claimed = goggles.addToGoggleTooltip(lines, sneaking);
				// Create removes the last line of a readout it was told exists
				if (claimed && lines.isEmpty())
					problems.add("claims a goggle readout and wrote no lines (" + how + ")");
				inspect(lines, problems);
			}
			if (entity instanceof IHaveHoveringInformation hovering) {
				List<Component> lines = new ArrayList<>();
				boolean claimed = hovering.addToTooltip(lines, sneaking);
				if (claimed && lines.isEmpty())
					problems.add("claims a hover readout and wrote no lines (" + how + ")");
				inspect(lines, problems);
			}
		} catch (RuntimeException e) {
			problems.add("threw " + e + " (" + how + ")");
		}
	}

	/** Every item's tooltip, plain and advanced, wear mode both ways; worn parts and blueprints also with data no one meant to write. */
	static void itemTooltips(SmokeReport report) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null || minecraft.player == null) {
			report.skip("gui", "item tooltips", "not in a world");
			return;
		}
		boolean wearMode = Tuning.wearMode;
		try {
			for (Item item : ServerAudit.items()) {
				String name = BuiltInRegistries.ITEM.getKey(item)
					.getPath();
				List<ItemStack> stacks = new ArrayList<>();
				stacks.add(item.getDefaultInstance());
				if (item instanceof WornPartItem)
					for (double wear : HOSTILE_WEAR) {
						ItemStack worn = item.getDefaultInstance();
						// Set directly, not through WearService, which would clamp it
						worn.set(CEComponents.WEAR.get(), wear);
						stacks.add(worn);
					}
				if (item instanceof com.createengines.content.blueprint.BlueprintItem)
					for (RecordedBlueprint recorded : List.of(new RecordedBlueprint("CEBP2:damaged", "Broken", 1),
						new RecordedBlueprint("", "", -1), new RecordedBlueprint("not a blueprint", "x".repeat(300), Integer.MAX_VALUE))) {
						ItemStack damaged = item.getDefaultInstance();
						damaged.set(CEComponents.BLUEPRINT.get(), recorded);
						stacks.add(damaged);
					}
				Set<String> problems = new TreeSet<>();
				for (ItemStack stack : stacks)
					for (boolean wear : new boolean[] {false, true}) {
						Tuning.wearMode = wear;
						readItem(minecraft, stack, wear, problems);
					}
				report.check("gui", "item " + name + " tooltip", problems.isEmpty(), String.join("; ", problems.stream()
					.limit(3)
					.toList()));
			}
		} finally {
			Tuning.wearMode = wearMode;
		}
	}

	private static void readItem(Minecraft minecraft, ItemStack stack, boolean wearMode, Set<String> problems) {
		String how = "wear mode " + wearMode + (stack.has(CEComponents.WEAR.get()) ? ", wear " + WearService.wearOf(stack) + " read from "
			+ stack.get(CEComponents.WEAR.get()) : "");
		try {
			for (TooltipFlag flag : new TooltipFlag[] {TooltipFlag.NORMAL, TooltipFlag.ADVANCED})
				inspect(stack.getTooltipLines(Item.TooltipContext.of(minecraft.level), minecraft.player, flag), problems);
			stack.getHoverName();
			if (stack.isBarVisible()) {
				int width = stack.getBarWidth();
				if (width < 0 || width > 13)
					problems.add("draws a durability bar " + width + " pixels wide (" + how + ")");
				stack.getBarColor();
			}
		} catch (RuntimeException e) {
			problems.add("threw " + e + " (" + how + ")");
		}
	}
}
