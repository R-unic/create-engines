package com.createengines.engine.sim;

import java.util.Locale;

/**
 * How hot a refining column's base is, using the names of Create's heat conditions: a recipe that asks for "heated"
 * runs over a kindled Blaze Burner, and one that asks for "superheated" needs a seething one.
 */
public enum Heat {
	/** No heat at all. A recipe that asks for it runs cold. */
	NONE,
	HEATED,
	SUPERHEATED;

	/** Whether a base this hot can run a recipe that asks for {@code needed}. Hotter than asked is always enough. */
	public boolean satisfies(Heat needed) {
		return ordinal() >= needed.ordinal();
	}

	/** The name used in recipe files. */
	public String serializedName() {
		return name().toLowerCase(Locale.ROOT);
	}

	/** Parses a recipe file's name for a heat; null when it is none of the three. */
	public static Heat fromName(String name) {
		for (Heat heat : values())
			if (heat.serializedName().equals(name))
				return heat;
		return null;
	}
}
