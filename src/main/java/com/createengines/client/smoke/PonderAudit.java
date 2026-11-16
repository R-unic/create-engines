package com.createengines.client.smoke;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.createengines.CreateEngines;
import com.createengines.smoke.SmokeReport;
import com.createengines.smoke.audit.ServerAudit;

import net.createmod.ponder.api.registration.StoryBoardEntry;
import net.createmod.ponder.foundation.PonderIndex;
import net.createmod.ponder.foundation.PonderScene;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

/**
 * Plays every Ponder page this mod registers, from its first instruction to its last, with no screen open. A scene's
 * storyboard is code: a wrong block position, a missing schematic or a link to an element that was never made throws
 * when that step runs, which a player meets as a Ponder page that stops half way. Running them all headlessly finds
 * those without anyone opening 60 pages by hand. It also lists the blocks and items that have no page at all.
 */
final class PonderAudit {

	/** A page that has not ended after this many ticks is stuck in a wait that never completes. */
	private static final int TICK_LIMIT = 30000;

	private final List<Map.Entry<ResourceLocation, StoryBoardEntry>> entries = new ArrayList<>();
	private int next;
	private int scenes;

	PonderAudit() {
		for (var entry : PonderIndex.getSceneAccess()
			.getRegisteredEntries())
			if (CreateEngines.MOD_ID.equals(entry.getValue()
				.getNamespace()))
				entries.add(entry);
	}

	boolean done() {
		return next >= entries.size();
	}

	int total() {
		return entries.size();
	}

	/** Plays the next storyboard, reporting what went wrong. */
	void playNext(SmokeReport report) {
		var entry = entries.get(next++);
		ResourceLocation component = entry.getKey();
		StoryBoardEntry board = entry.getValue();
		String name = component.getPath() + " (" + board.getSchematicLocation()
			.getPath() + ")";
		try {
			List<PonderScene> compiled = PonderIndex.getSceneAccess()
				.compile(List.of(board));
			if (!report.check("ponder", name + " compiles to a scene", !compiled.isEmpty(),
				"no scene came out of the storyboard, which usually means it threw while building (see the log)"))
				return;
			for (PonderScene scene : compiled) {
				scenes++;
				scene.begin();
				int ticks = 0;
				while (!scene.isFinished() && ticks < TICK_LIMIT) {
					scene.tick();
					ticks++;
				}
				report.check("ponder", name + " plays to its end", scene.isFinished(),
					"still running after " + TICK_LIMIT + " ticks");
				String title = scene.getTitle();
				report.check("ponder", name + " has a title", title != null && !title.isBlank() && !title.startsWith("ponder."),
					"title is '" + title + "'");
				report.check("ponder", name + " has a few steps", scene.getKeyframeCount() >= 0 && ticks > 40,
					"ended after only " + ticks + " ticks");
			}
		} catch (Throwable t) {
			Throwable root = t;
			while (root.getCause() != null && root.getCause() != root)
				root = root.getCause();
			StackTraceElement where = root.getStackTrace().length > 0 ? root.getStackTrace()[0] : null;
			report.fail("ponder", name + " plays", root + (where != null ? " at " + where : ""));
		}
	}

	/** Blocks and items nobody has written a page for, as warnings: not every part needs one, but each should be a choice. */
	void coverage(SmokeReport report) {
		report.metric("ponder storyboards run", entries.size(), "");
		report.metric("ponder scenes played", scenes, "");
		var access = PonderIndex.getSceneAccess();
		for (var block : ServerAudit.blocks()) {
			ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
			if (block instanceof net.minecraft.world.level.block.LiquidBlock || id.getPath()
				.startsWith("engine_cased_"))
				continue;
			report.expect("ponder", "block " + id.getPath() + " has a Ponder page", access.doScenesExistForId(id),
				"no storyboard is registered for it, so holding W over it in the inventory shows nothing");
		}
		for (var item : ServerAudit.items()) {
			ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
			if (item instanceof net.minecraft.world.item.BlockItem || item instanceof net.minecraft.world.item.BucketItem)
				continue;
			report.expect("ponder", "item " + id.getPath() + " has a Ponder page", access.doScenesExistForId(id),
				"no storyboard is registered for it");
		}
	}
}
