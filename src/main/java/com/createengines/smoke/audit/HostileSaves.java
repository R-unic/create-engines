package com.createengines.smoke.audit;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.ShortTag;
import net.minecraft.nbt.Tag;

/**
 * Saved data with every number turned against the code that reads it. Each tag keeps its type, so the loader takes it as it would
 * a real save, and only the value is one nobody meant to write: not a number, an infinity, or the far end of the type. One such
 * value read without a clamp spreads through the simulation and is then saved into the world for good.
 */
public final class HostileSaves {

	/** The three kinds of bad number a save is poisoned with. */
	public enum Poison {
		/** Not a number; whole numbers at their largest. */
		NOT_A_NUMBER,
		/** Minus infinity; whole numbers at their smallest. */
		LOWEST,
		/** A finite number far too large to be real; whole numbers at minus one, the classic bad index. */
		ABSURD
	}

	/** Keys at the root of a save that say where and what the block entity is. */
	private static final Set<String> IDENTITY = Set.of("id", "x", "y", "z", "keepPacked");

	/** Create's own kinetic network keys at the root of a save: its reading of them is not this mod's to guard. */
	private static final Set<String> CREATE_KINETIC = Set.of("Speed", "Sequence", "Network", "Source", "NeedsSpeedUpdate");

	private HostileSaves() {
	}

	/** A copy of {@code saved} with every number this mod reads replaced. */
	public static CompoundTag poison(CompoundTag saved, Poison poison) {
		CompoundTag copy = saved.copy();
		for (String key : List.copyOf(copy.getAllKeys()))
			if (!skipped(key))
				copy.put(key, poisoned(copy.get(key), poison));
		return copy;
	}

	private static boolean skipped(String rootKey) {
		return IDENTITY.contains(rootKey) || CREATE_KINETIC.contains(rootKey);
	}

	private static Tag poisoned(Tag tag, Poison poison) {
		if (tag instanceof CompoundTag compound) {
			CompoundTag copy = new CompoundTag();
			for (String key : compound.getAllKeys())
				if (createTankAnimation(compound, key))
					copy.put(key, compound.get(key)
						.copy());
				else if (fluidAmount(compound, key))
					// NeoForge refuses an amount below one itself, and logs an error for it, so only the huge one reaches this mod
					copy.put(key, IntTag.valueOf(Integer.MAX_VALUE));
				else
					copy.put(key, poisoned(compound.get(key), poison));
			return copy;
		}
		if (tag instanceof ListTag list) {
			ListTag copy = new ListTag();
			for (Tag element : list)
				copy.add(poisoned(element, poison));
			return copy;
		}
		if (tag instanceof NumericTag)
			return number(tag, poison);
		return tag.copy();
	}

	/** The {@code Level} beside a {@code TankContent}: the needle Create's tank behaviour animates, saved and read by Create alone. */
	private static boolean createTankAnimation(CompoundTag compound, String key) {
		return key.equals("Level") && compound.contains("TankContent");
	}

	/** The amount of a saved fluid stack. */
	private static boolean fluidAmount(CompoundTag compound, String key) {
		return key.equals("amount") && compound.contains("id", Tag.TAG_STRING) && compound.contains(key, Tag.TAG_INT);
	}

	private static Tag number(Tag tag, Poison poison) {
		return switch (tag.getId()) {
			case Tag.TAG_FLOAT -> FloatTag.valueOf(switch (poison) {
				case NOT_A_NUMBER -> Float.NaN;
				case LOWEST -> Float.NEGATIVE_INFINITY;
				case ABSURD -> 1e30f;
			});
			case Tag.TAG_DOUBLE -> DoubleTag.valueOf(switch (poison) {
				case NOT_A_NUMBER -> Double.NaN;
				case LOWEST -> Double.NEGATIVE_INFINITY;
				case ABSURD -> 1e300;
			});
			case Tag.TAG_LONG -> LongTag.valueOf(switch (poison) {
				case NOT_A_NUMBER -> Long.MAX_VALUE;
				case LOWEST -> Long.MIN_VALUE;
				case ABSURD -> -1;
			});
			case Tag.TAG_INT -> IntTag.valueOf(switch (poison) {
				case NOT_A_NUMBER -> Integer.MAX_VALUE;
				case LOWEST -> Integer.MIN_VALUE;
				case ABSURD -> -1;
			});
			case Tag.TAG_SHORT -> ShortTag.valueOf(switch (poison) {
				case NOT_A_NUMBER -> Short.MAX_VALUE;
				case LOWEST -> Short.MIN_VALUE;
				case ABSURD -> (short) -1;
			});
			default -> ByteTag.valueOf(switch (poison) {
				case NOT_A_NUMBER -> Byte.MAX_VALUE;
				case LOWEST -> Byte.MIN_VALUE;
				case ABSURD -> (byte) -1;
			});
		};
	}

	/** The paths in {@code saved} that hold a number that is not finite, leaving out what {@link #poison} leaves alone. */
	public static List<String> notFinite(CompoundTag saved) {
		List<String> found = new ArrayList<>();
		for (String key : saved.getAllKeys())
			if (!skipped(key))
				notFinite(saved.get(key), key, found);
		return found;
	}

	private static void notFinite(Tag tag, String path, List<String> found) {
		if (tag instanceof CompoundTag compound) {
			for (String key : compound.getAllKeys())
				if (!createTankAnimation(compound, key))
					notFinite(compound.get(key), path + "." + key, found);
		} else if (tag instanceof ListTag list) {
			for (int i = 0; i < list.size(); i++)
				notFinite(list.get(i), path + "[" + i + "]", found);
		} else if ((tag.getId() == Tag.TAG_FLOAT || tag.getId() == Tag.TAG_DOUBLE)
			&& !Double.isFinite(((NumericTag) tag).getAsDouble()))
			found.add(path + "=" + ((NumericTag) tag).getAsDouble());
	}
}
