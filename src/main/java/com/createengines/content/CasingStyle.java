package com.createengines.content;

import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.ItemStack;

import com.createengines.registry.CEBlocks;

/**
 * Which of the four Engine Casing blocks a part is clad in. A crank, cam, cylinder or pipe remembers it in
 * {@link Encasing#STYLE} (plain, riveted or louvred; a pane of glass is the separate {@link Encasing#WINDOWED} flag), and
 * each cased shaft or cogwheel is a block of its own per style.
 */
public enum CasingStyle implements StringRepresentable {
	PLAIN("plain"), RIVETED("riveted"), LOUVRED("louvred"), WINDOW("window");

	private final String name;

	CasingStyle(String name) {
		this.name = name;
	}

	@Override
	public String getSerializedName() {
		return name;
	}

	/** The casing block item that clads a part in this style, and that taking the casing off gives back. */
	public ItemStack casingStack() {
		return switch (this) {
			case PLAIN -> CEBlocks.ENGINE_CASING.asStack();
			case RIVETED -> CEBlocks.ENGINE_RIVETED_CASING.asStack();
			case LOUVRED -> CEBlocks.ENGINE_LOUVRED_CASING.asStack();
			case WINDOW -> CEBlocks.ENGINE_WINDOW_CASING.asStack();
		};
	}

	/** The style a held item clads in, or null if it is no Engine Casing. */
	public static CasingStyle ofItem(ItemStack stack) {
		for (CasingStyle style : values())
			if (stack.is(style.casingStack()
				.getItem()))
				return style;
		return null;
	}
}
