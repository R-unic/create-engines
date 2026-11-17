package com.createengines.engine.blueprint;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Compares a blueprint with the engine as it stands (recorded the same way), to answer two questions before anything is
 * changed: is the engine the same build, and which dials differ. Plain Java, so the rules are tested without a game.
 * <p>
 * "Same build" means every block of the blueprint is there, in the same place with the same block and the same non-default
 * state, and the engine has no block the blueprint lacks. Dials are not part of the build: they are what applying copies.
 */
public final class BlueprintDiff {

	private BlueprintDiff() {
	}

	/** How the engine differs from the blueprint in one place. */
	public enum Kind {
		/** The blueprint has a block here that the engine does not. */
		MISSING,
		/** The engine has a block here that the blueprint does not. */
		EXTRA,
		/** A different block is here. */
		BLOCK,
		/** The same block with different state (a missing piston, another facing). */
		STATE
	}

	/** One difference in the build, named by position and block so a player can find it. */
	public record Mismatch(Kind kind, int x, int y, int z, String block, String detail) {
	}

	/** One dial to set: where, which, and what it is now and should become. */
	public record DialChange(int x, int y, int z, String block, String dial, int from, int to) {
	}

	private record Cell(int x, int y, int z) {
		static Cell of(Blueprint.Part part) {
			return new Cell(part.x(), part.y(), part.z());
		}
	}

	/**
	 * The differences between the build the blueprint holds and the engine, at most {@code limit} of them (the first in
	 * position order; a build that is off in a hundred places does not need a hundred lines). Empty when it is the same
	 * build.
	 */
	public static List<Mismatch> compare(Blueprint blueprint, Blueprint actual, int limit) {
		Map<Cell, Blueprint.Part> wanted = index(blueprint);
		Map<Cell, Blueprint.Part> found = index(actual);
		Map<Cell, Mismatch> differences = new TreeMap<>((a, b) -> a.y() != b.y() ? Integer.compare(a.y(), b.y())
			: a.z() != b.z() ? Integer.compare(a.z(), b.z()) : Integer.compare(a.x(), b.x()));
		for (Map.Entry<Cell, Blueprint.Part> entry : wanted.entrySet()) {
			Blueprint.Part want = entry.getValue();
			Blueprint.Part have = found.get(entry.getKey());
			if (have == null)
				differences.put(entry.getKey(), new Mismatch(Kind.MISSING, want.x(), want.y(), want.z(), want.block(), ""));
			else if (!have.block()
				.equals(want.block()))
				differences.put(entry.getKey(), new Mismatch(Kind.BLOCK, want.x(), want.y(), want.z(), want.block(), have.block()));
			else if (!have.state()
				.equals(want.state()))
				differences.put(entry.getKey(),
					new Mismatch(Kind.STATE, want.x(), want.y(), want.z(), want.block(), stateDifference(want.state(), have.state())));
		}
		for (Map.Entry<Cell, Blueprint.Part> entry : found.entrySet())
			if (!wanted.containsKey(entry.getKey())) {
				Blueprint.Part have = entry.getValue();
				differences.put(entry.getKey(), new Mismatch(Kind.EXTRA, have.x(), have.y(), have.z(), have.block(), ""));
			}
		List<Mismatch> out = new ArrayList<>();
		for (Mismatch mismatch : differences.values()) {
			if (out.size() >= limit)
				break;
			out.add(mismatch);
		}
		return out;
	}

	/** Whether the engine is the same build as the blueprint. */
	public static boolean sameBuild(Blueprint blueprint, Blueprint actual) {
		return compare(blueprint, actual, 1).isEmpty();
	}

	/**
	 * The dials that differ, for an engine that is the same build. A dial the blueprint has and the engine's block does not
	 * (it would be refused as a different build already) is left out, and so is a dial the blueprint does not mention.
	 */
	public static List<DialChange> dialChanges(Blueprint blueprint, Blueprint actual) {
		Map<Cell, Blueprint.Part> found = index(actual);
		List<DialChange> changes = new ArrayList<>();
		for (Blueprint.Part want : blueprint.parts()) {
			Blueprint.Part have = found.get(Cell.of(want));
			if (have == null || !have.block()
				.equals(want.block()))
				continue;
			for (Map.Entry<String, Integer> dial : new TreeMap<>(want.dials()).entrySet()) {
				Integer current = have.dials()
					.get(dial.getKey());
				if (current != null && !current.equals(dial.getValue()))
					changes.add(new DialChange(want.x(), want.y(), want.z(), want.block(), dial.getKey(), current, dial.getValue()));
			}
		}
		changes.sort((a, b) -> a.y() != b.y() ? Integer.compare(a.y(), b.y())
			: a.z() != b.z() ? Integer.compare(a.z(), b.z()) : a.x() != b.x() ? Integer.compare(a.x(), b.x())
				: a.dial()
					.compareTo(b.dial()));
		return changes;
	}

	private static Map<Cell, Blueprint.Part> index(Blueprint blueprint) {
		Map<Cell, Blueprint.Part> parts = new HashMap<>();
		for (Blueprint.Part part : blueprint.parts())
			parts.put(Cell.of(part), part);
		return parts;
	}

	private static String stateDifference(Map<String, String> want, Map<String, String> have) {
		Map<String, String> keys = new TreeMap<>();
		for (String key : want.keySet())
			keys.put(key, "");
		for (String key : have.keySet())
			keys.put(key, "");
		StringBuilder out = new StringBuilder();
		for (String key : keys.keySet()) {
			String a = want.get(key);
			String b = have.get(key);
			if (a == null ? b != null : !a.equals(b)) {
				if (out.length() > 0)
					out.append(", ");
				out.append(key)
					.append(' ')
					.append(b == null ? "default" : b)
					.append(" -> ")
					.append(a == null ? "default" : a);
			}
		}
		return out.toString();
	}
}
