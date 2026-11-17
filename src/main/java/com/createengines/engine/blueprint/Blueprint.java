package com.createengines.engine.blueprint;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.jetbrains.annotations.Nullable;

import com.createengines.engine.sim.PlateFigures;

/**
 * A recorded engine: where each of its blocks sits, which block state each has, and every dial that was set on it.
 * Plain data and plain Java: nothing here knows about Minecraft, so the format, its limits and its refusals are tested
 * without a game. Blocks are named by id and state property, not by object, so a blueprint can be written down, pasted
 * into chat and read back by a later release ({@link BlueprintCodec}).
 * <p>
 * Positions are relative to the blueprint's own origin (the engine's lowest crankshaft block, chosen when it is
 * recorded), so the same blueprint can be laid anywhere and turned to face any way.
 *
 * @param name    what the player called it, at most {@link Limits#MAX_NAME} characters
 * @param builder who recorded it, may be empty, at most {@link Limits#MAX_NAME} characters
 * @param parts   every block, in no particular order; positions are all different
 * @param figures the figures from a Maker's Plate on the engine when it was recorded (peak torque and power with their
 *                speeds), or null if there was none or it was not stamped. Format 2 on; a format 1 blueprint has none
 */
public record Blueprint(String name, String builder, List<Part> parts, @Nullable PlateFigures figures) {

	/** The format this release writes. A later release still reads every format up to this one. */
	public static final int FORMAT_VERSION = 2;

	/** The largest things a blueprint may hold. A string that breaks one is refused before anything is built. */
	public static final class Limits {
		/** Most blocks in one blueprint. A V12 with its pipes, cams and casing is a few hundred. */
		public static final int MAX_PARTS = 4096;
		/** Furthest a block may sit from the origin on any axis, in blocks. */
		public static final int MAX_SPAN = 127;
		public static final int MAX_NAME = 48;
		/** Longest block id, state property, state value or dial name. */
		public static final int MAX_ID = 64;
		public static final int MAX_KEY = 32;
		/** Most state properties and most dials on one block. */
		public static final int MAX_ENTRIES = 16;
		/** Longest pasted text, in characters. */
		public static final int MAX_TEXT = 262_144;
		/** Largest decompressed body, in bytes. Reading stops here, so a tiny string cannot unpack into a huge one. */
		public static final int MAX_BODY = 1 << 20;

		private Limits() {
		}
	}

	public Blueprint {
		name = name == null ? "" : name;
		builder = builder == null ? "" : builder;
		parts = parts == null ? List.of() : List.copyOf(parts);
		figures = figures != null && figures.stamped() ? figures : null;
	}

	/** A blueprint with no plate figures. */
	public Blueprint(String name, String builder, List<Part> parts) {
		this(name, builder, parts, null);
	}

	/**
	 * One block.
	 *
	 * @param x     blocks east of the origin (negative is west)
	 * @param y     blocks above the origin
	 * @param z     blocks south of the origin
	 * @param block the block's registry id, such as {@code create_engines:cylinder}
	 * @param state block state properties that were not the block's defaults, by name
	 * @param dials every setting the player made on it (a metering share, a cam angle), by name
	 */
	public record Part(int x, int y, int z, String block, Map<String, String> state, Map<String, Integer> dials) {

		public Part {
			state = state == null ? Map.of() : Map.copyOf(new TreeMap<>(state));
			dials = dials == null ? Map.of() : Map.copyOf(new TreeMap<>(dials));
		}

		public Part(int x, int y, int z, String block) {
			this(x, y, z, block, Map.of(), Map.of());
		}
	}
}
