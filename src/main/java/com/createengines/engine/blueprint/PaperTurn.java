package com.createengines.engine.blueprint;

/**
 * How far the drawing on a framed blueprint is turned, in quarter turns (0 to 3, a clockwise turn each time a wrench is
 * used on it). Plain Java: the game side stores the number and the renderer turns the paper by it.
 *
 * <p>A turn of a quarter or three quarters puts the drawing on its side, so it is laid out across the sheet as if the sheet
 * were turned too: a sheet that is three frames wide and two high carries a drawing laid out two wide and three high.
 */
public final class PaperTurn {

	private PaperTurn() {
	}

	/** Any number as a turn from 0 to 3 (saved data may hold anything). */
	public static int normalise(int turn) {
		return Math.floorMod(turn, 4);
	}

	/** The turn after one more quarter. */
	public static int next(int turn) {
		return normalise(turn + 1);
	}

	/** True if the drawing is on its side. */
	public static boolean sideways(int turn) {
		return normalise(turn) % 2 == 1;
	}

	/** How many frames wide the drawing is laid out, for a sheet {@code width} by {@code height} turned {@code turn}. */
	public static int layoutWidth(int width, int height, int turn) {
		return sideways(turn) ? height : width;
	}

	/** How many frames high the drawing is laid out. */
	public static int layoutHeight(int width, int height, int turn) {
		return sideways(turn) ? width : height;
	}

	/** The angle to turn the paper by, in radians, clockwise as the paper is read. */
	public static float radians(int turn) {
		return normalise(turn) * (float) (Math.PI / 2);
	}
}
