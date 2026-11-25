package com.createengines.content;

import net.minecraft.nbt.CompoundTag;

/**
 * Reads a number a block entity saved, refusing one that is not finite. A damaged or hand-edited save can hold NaN or an infinity, and
 * a value read as-is is ticked with, sent to clients and written back to the world, so it never goes away.
 */
public final class SavedNumbers {

	private SavedNumbers() {
	}

	/** {@code value}, or {@code fallback} when it is NaN or infinite. */
	public static float finite(float value, float fallback) {
		return Float.isFinite(value) ? value : fallback;
	}

	/** The float saved under {@code key}, or {@code fallback} when it is missing, NaN or infinite. */
	public static float read(CompoundTag tag, String key, float fallback) {
		return tag.contains(key) ? finite(tag.getFloat(key), fallback) : fallback;
	}
}
