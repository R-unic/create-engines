package com.createengines.smoke;

/**
 * Whether a face's texture coordinates stay inside its sprite. A face whose coordinates run off the edge of its sprite
 * samples the neighbouring sprite in the block atlas, and shows as stray colour or the purple and black of the missing
 * texture, depending on what happens to sit next to it, so it can look fine in one pack and wrong in another. The
 * missing-texture checks cannot see it: the sprite is the right one, only the coordinates are wrong.
 *
 * <p>Plain arithmetic with no Minecraft types, so the model files and the baked quads are judged by one definition and the
 * definition is unit tested. Distances are in pixels of a 16 pixel sprite (a sprite of another size is measured as the same
 * fraction of its width).
 */
public final class UvBounds {

	/** How far outside its sprite a face may run before it counts. Under half a pixel is below what one texel shows. */
	public static final double TOLERANCE_PIXELS = 0.5;
	/** Pixels in a standard block texture, the unit distances are given in. */
	public static final double SPRITE_PIXELS = 16;
	/** Ints in one baked vertex (position, colour, u, v, light, normal) and where the texture coordinates sit in it. */
	public static final int VERTEX_STRIDE = 8;
	public static final int U_OFFSET = 4;
	public static final int V_OFFSET = 5;

	private UvBounds() {
	}

	/** How far, in sprite pixels, the point lies outside the sprite's rectangle in atlas coordinates; 0 inside. */
	public static double outside(float u, float v, float u0, float u1, float v0, float v1) {
		double width = u1 - u0;
		double height = v1 - v0;
		if (width <= 0 || height <= 0)
			return 0;
		double du = Math.max(Math.max(u0 - u, u - u1), 0) / width;
		double dv = Math.max(Math.max(v0 - v, v - v1), 0) / height;
		return Math.max(du, dv) * SPRITE_PIXELS;
	}

	/** The worst {@link #outside} over every vertex of a baked quad ({@code BakedQuad.getVertices()}). */
	public static double worstOverrun(int[] vertices, float u0, float u1, float v0, float v1) {
		double worst = 0;
		for (int base = 0; base + V_OFFSET < vertices.length; base += VERTEX_STRIDE)
			worst = Math.max(worst, outside(Float.intBitsToFloat(vertices[base + U_OFFSET]),
				Float.intBitsToFloat(vertices[base + V_OFFSET]), u0, u1, v0, v1));
		return worst;
	}

	/** Whether an overrun is more than a face is allowed. */
	public static boolean exceeds(double overrunPixels) {
		return overrunPixels > TOLERANCE_PIXELS;
	}

	/**
	 * The patch of its texture a model-file face shows when it names no {@code uv}: {u0, v0, u1, v1} in sprite pixels, from
	 * the element's corners {@code from} and {@code to} (also in pixels). This is what the game does with a face without a
	 * {@code uv}, and it runs outside 0 to 16 whenever the element pokes out of the block.
	 */
	public static double[] defaultPatch(double[] from, double[] to, String face) {
		double x1 = from[0], y1 = from[1], z1 = from[2];
		double x2 = to[0], y2 = to[1], z2 = to[2];
		return switch (face) {
			case "down" -> new double[] {x1, 16 - z2, x2, 16 - z1};
			case "up" -> new double[] {x1, z1, x2, z2};
			case "north" -> new double[] {16 - x2, 16 - y2, 16 - x1, 16 - y1};
			case "south" -> new double[] {x1, 16 - y2, x2, 16 - y1};
			case "west" -> new double[] {z1, 16 - y2, z2, 16 - y1};
			case "east" -> new double[] {16 - z2, 16 - y2, 16 - z1, 16 - y1};
			default -> throw new IllegalArgumentException("not a face: " + face);
		};
	}

	/** How far a {u0, v0, u1, v1} patch runs outside the 0 to 16 sprite, in pixels; 0 inside. */
	public static double patchOverrun(double[] patch) {
		double low = Math.min(Math.min(patch[0], patch[1]), Math.min(patch[2], patch[3]));
		double high = Math.max(Math.max(patch[0], patch[1]), Math.max(patch[2], patch[3]));
		return Math.max(Math.max(-low, high - SPRITE_PIXELS), 0);
	}
}
