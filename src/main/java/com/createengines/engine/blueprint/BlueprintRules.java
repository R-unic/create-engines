package com.createengines.engine.blueprint;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import com.createengines.engine.blueprint.Blueprint.Limits;
import com.createengines.engine.blueprint.BlueprintException.Refusal;

/**
 * The checks a blueprint passes before it is trusted. {@link #checkShape} needs nothing but the blueprint and is run by
 * the codec on every read and write, so a blueprint that exists is always well formed. {@link #check} adds the game's
 * own rules through a {@link BlueprintPolicy} and is what the server runs on a pasted string before it builds a block.
 */
public final class BlueprintRules {

	private static final Pattern BLOCK_ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");
	private static final Pattern KEY = Pattern.compile("[a-z0-9_]+");

	private BlueprintRules() {
	}

	/** Limits and syntax only. Throws the first problem found. */
	public static void checkShape(Blueprint blueprint) throws BlueprintException {
		checkText(blueprint.name());
		checkText(blueprint.builder());
		if (blueprint.figures() != null && !blueprint.figures().stamped())
			throw new BlueprintException(Refusal.CORRUPT, "figures with no power in them");
		if (blueprint.parts().isEmpty())
			throw new BlueprintException(Refusal.EMPTY, "");
		if (blueprint.parts().size() > Limits.MAX_PARTS)
			throw new BlueprintException(Refusal.TOO_LARGE, blueprint.parts().size() + " parts, at most " + Limits.MAX_PARTS);
		Set<Long> seen = new HashSet<>();
		for (Blueprint.Part part : blueprint.parts()) {
			if (!inSpan(part.x()) || !inSpan(part.y()) || !inSpan(part.z()))
				throw new BlueprintException(Refusal.OUT_OF_RANGE, at(part));
			long key = ((long) (part.x() + 128) << 16) | ((long) (part.y() + 128) << 8) | (part.z() + 128);
			if (!seen.add(key))
				throw new BlueprintException(Refusal.DUPLICATE_POSITION, at(part));
			if (part.block() == null || part.block().length() > Limits.MAX_ID || !BLOCK_ID.matcher(part.block()).matches())
				throw new BlueprintException(Refusal.BAD_ID, "block at " + at(part));
			if (part.state().size() > Limits.MAX_ENTRIES || part.dials().size() > Limits.MAX_ENTRIES)
				throw new BlueprintException(Refusal.TOO_LARGE, "too many entries on " + part.block() + " at " + at(part));
			for (Map.Entry<String, String> state : part.state().entrySet())
				if (!key(state.getKey()) || !key(state.getValue()))
					throw new BlueprintException(Refusal.BAD_ID, "state on " + part.block() + " at " + at(part));
			for (String dial : part.dials().keySet())
				if (!key(dial))
					throw new BlueprintException(Refusal.BAD_ID, "dial on " + part.block() + " at " + at(part));
		}
	}

	/** Shape, then every part against the game's rules. Throws the first problem found. */
	public static void check(Blueprint blueprint, BlueprintPolicy policy) throws BlueprintException {
		checkShape(blueprint);
		for (Blueprint.Part part : blueprint.parts()) {
			if (!policy.allowsBlock(part.block()))
				throw new BlueprintException(Refusal.FOREIGN_BLOCK, part.block() + " at " + at(part));
			for (Map.Entry<String, String> state : part.state().entrySet())
				if (!policy.allowsState(part.block(), state.getKey(), state.getValue()))
					throw new BlueprintException(Refusal.UNKNOWN_STATE,
						part.block() + " has no " + state.getKey() + "=" + state.getValue());
			for (Map.Entry<String, Integer> dial : part.dials().entrySet()) {
				int min = policy.dialMin(part.block(), dial.getKey());
				int max = policy.dialMax(part.block(), dial.getKey());
				if (min > max || dial.getValue() < min || dial.getValue() > max)
					throw new BlueprintException(Refusal.BAD_DIAL,
						part.block() + " " + dial.getKey() + "=" + dial.getValue() + " at " + at(part));
			}
		}
	}

	/** Not {@code Math.abs}: that leaves {@link Integer#MIN_VALUE} negative, which would pass. */
	private static boolean inSpan(int coordinate) {
		return coordinate >= -Limits.MAX_SPAN && coordinate <= Limits.MAX_SPAN;
	}

	private static boolean key(String text) {
		return text != null && text.length() <= Limits.MAX_KEY && KEY.matcher(text).matches();
	}

	private static void checkText(String text) throws BlueprintException {
		if (text.length() > Limits.MAX_NAME)
			throw new BlueprintException(Refusal.BAD_NAME, "longer than " + Limits.MAX_NAME);
		for (int i = 0; i < text.length(); i++)
			if (Character.isISOControl(text.charAt(i)))
				throw new BlueprintException(Refusal.BAD_NAME, "control character");
	}

	private static String at(Blueprint.Part part) {
		return part.x() + "," + part.y() + "," + part.z();
	}
}
