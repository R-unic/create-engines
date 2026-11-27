package com.createengines.client;

import java.util.List;

import com.createengines.engine.sim.CylinderSpec;
import com.createengines.registry.CEItems;
import com.createengines.registry.CELang;
import com.tterrag.registrate.util.entry.ItemEntry;

import net.minecraft.ChatFormatting;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/** What the parts that are a choice say about the trade they make, when you hover over one. Client only. */
public final class PartTooltips {

	private record Note(ItemEntry<?> item, String key) {
	}

	private static final List<Note> NOTES = List.of(new Note(CEItems.HIGH_COMPRESSION_PISTON, "tooltip.part.high_compression_piston"),
		new Note(CEItems.LOW_COMPRESSION_PISTON, "tooltip.part.low_compression_piston"),
		new Note(CEItems.HIGH_COMPRESSION_ROTOR, "tooltip.part.high_compression_rotor"),
		new Note(CEItems.LOW_COMPRESSION_ROTOR, "tooltip.part.low_compression_rotor"),
		new Note(CEItems.PISTON_RING, "tooltip.part.piston_ring"));

	private PartTooltips() {
	}

	public static void onTooltip(ItemTooltipEvent event) {
		for (Note note : NOTES)
			if (event.getItemStack()
				.is(note.item()
					.asItem()))
				event.getToolTip()
					.add(CELang.translate(note.key(), CylinderSpec.MAX_RINGS)
						.component()
						.copy()
						.withStyle(ChatFormatting.GRAY));
	}
}
