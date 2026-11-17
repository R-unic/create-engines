package com.createengines.engine.blueprint;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Which framed blueprints draw as one bigger picture. Plain Java: the game side says where the frames are on the wall
 * ({@code u} runs along the paper's reading direction, {@code v} down it) and which of them hold a blueprint, and this
 * works out the sheet.
 *
 * <p>Frames that touch edge to edge on the same wall join into one sheet only if together they make a full rectangle no
 * more than {@link #MAX_SIDE} frames on a side. A ragged group, or a bigger one, is left as separate frames, each drawing
 * its own blueprint, exactly as before frames could join. The picture is the blueprint in the sheet's top-left frame, or
 * if that one is empty, the first blueprint reading left to right and top to bottom; the other frames of the sheet keep
 * what they hold but show nothing of their own.
 */
public final class FrameSheet {

	/** The most frames along one side of a sheet. */
	public static final int MAX_SIDE = 8;

	/** A frame's place on the wall, relative to whichever frame was asked about. */
	public record Cell(int u, int v) {
	}

	/**
	 * The rectangle of frames {@code minU..minU+width-1} by {@code minV..minV+height-1}, and the frame whose blueprint is
	 * drawn across it, or null if none of them holds one.
	 */
	public record Sheet(int minU, int minV, int width, int height, Cell source) {

		public boolean joined() {
			return width * height > 1;
		}

		public boolean contains(Cell cell) {
			return cell.u() >= minU && cell.u() < minU + width && cell.v() >= minV && cell.v() < minV + height;
		}
	}

	private FrameSheet() {
	}

	/**
	 * The sheet that the frame at (0, 0) belongs to. {@code frame} says whether a frame joins at a cell (the cell must be
	 * a frame on the same wall facing the same way); {@code filled} says whether the frame there holds a blueprint that
	 * can be drawn. (0, 0) itself is taken to be a frame.
	 */
	public static Sheet find(Predicate<Cell> frame, Predicate<Cell> filled) {
		Cell origin = new Cell(0, 0);
		Set<Cell> seen = new HashSet<>();
		ArrayDeque<Cell> open = new ArrayDeque<>();
		seen.add(origin);
		open.add(origin);
		int minU = 0, maxU = 0, minV = 0, maxV = 0;
		while (!open.isEmpty()) {
			Cell at = open.poll();
			for (Cell next : new Cell[] { new Cell(at.u() + 1, at.v()), new Cell(at.u() - 1, at.v()), new Cell(at.u(), at.v() + 1),
				new Cell(at.u(), at.v() - 1) }) {
				if (seen.contains(next) || !frame.test(next))
					continue;
				seen.add(next);
				minU = Math.min(minU, next.u());
				maxU = Math.max(maxU, next.u());
				minV = Math.min(minV, next.v());
				maxV = Math.max(maxV, next.v());
				if (maxU - minU >= MAX_SIDE || maxV - minV >= MAX_SIDE)
					return alone(filled);
				open.add(next);
			}
		}
		int width = maxU - minU + 1;
		int height = maxV - minV + 1;
		if (seen.size() != width * height)
			return alone(filled);
		for (int v = minV; v < minV + height; v++)
			for (int u = minU; u < minU + width; u++)
				if (filled.test(new Cell(u, v)))
					return new Sheet(minU, minV, width, height, new Cell(u, v));
		return new Sheet(minU, minV, width, height, null);
	}

	private static Sheet alone(Predicate<Cell> filled) {
		Cell origin = new Cell(0, 0);
		return new Sheet(0, 0, 1, 1, filled.test(origin) ? origin : null);
	}
}
