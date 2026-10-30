package com.createengines.content;

import java.util.List;
import java.util.Optional;

import com.createengines.registry.CELang;

import net.createmod.catnip.lang.LangBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

/**
 * Goggle tooltips are drawn as one line per entry and never wrap, so a long sentence runs off the screen. Lines that
 * can be long go through here instead of {@code LangBuilder.forGoggles}: it breaks them to fit.
 */
public final class GoggleLines {

	/** Widest a goggle line may be, as a share of the screen. The tooltip starts near the middle of it. */
	private static final float SCREEN_SHARE = 0.45f;
	/** Create indents a goggle line by 4 spaces plus one per level. */
	private static final int BASE_INDENT = 4;

	private GoggleLines() {
	}

	/** Adds {@code line} to {@code tooltip} at {@code indents} levels, broken into as many lines as it needs. */
	public static void add(LangBuilder line, List<Component> tooltip, int indents) {
		Minecraft minecraft = Minecraft.getInstance();
		Font font = minecraft.font;
		int available = (int) (minecraft.getWindow()
			.getGuiScaledWidth() * SCREEN_SHARE) - font.width(" ") * (BASE_INDENT + indents);
		MutableComponent component = line.component();
		if (font.width(component) <= available) {
			line.forGoggles(tooltip, indents);
			return;
		}
		for (var piece : font.getSplitter()
			.splitLines(component, Math.max(available, 60), Style.EMPTY)) {
			MutableComponent text = Component.empty();
			piece.visit((style, string) -> {
				text.append(Component.literal(string)
					.withStyle(style));
				return Optional.empty();
			}, Style.EMPTY);
			CELang.builder()
				.add(text)
				.forGoggles(tooltip, indents);
		}
	}
}
