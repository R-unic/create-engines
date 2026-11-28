package com.createengines.content;

/**
 * Where in the 720 degree cycle an engine's cylinders are doing something other than firing: pushing the rest of the burn
 * out (the half turn from 180 degrees after each firing), gulping air (the next) and squeezing it (the last, up to the next
 * firing). A real four-stroke is heard doing all of it, and a slow one with only its firings was a row of separate bangs.
 * Each is a table over the cycle, made on the game thread from the firing angles, so the events stay on the cam's time and a
 * V-twin's gulps lope as its firings do. Valves seating are not among them: the author had those sounds removed. Pure Java,
 * so it is tested.
 */
final class NoteStrokes {

	static final int STEPS = 360;
	private static final double CYCLE = 720;
	private static final double STROKE = 180;

	static final NoteStrokes NONE = of(new float[0]);

	private final float[] exhaust = new float[STEPS];
	private final float[] intake = new float[STEPS];
	private final float[] compression = new float[STEPS];

	private NoteStrokes() {
	}

	static NoteStrokes of(float[] angles) {
		NoteStrokes made = new NoteStrokes();
		for (float angle : angles)
			for (int i = 0; i < STEPS; i++) {
				double since = ((i * CYCLE / STEPS - angle) % CYCLE + CYCLE) % CYCLE;
				double into = since % STROKE / STROKE;
				switch ((int) (since / STROKE)) {
					case 1 -> made.exhaust[i] += (float) Math.sin(Math.PI * into);
					case 2 -> made.intake[i] += (float) Math.sin(Math.PI * into);
					// Rising all the way, and gone at the firing it leads to
					case 3 -> made.compression[i] += (float) (into * into);
					default -> {
					}
				}
			}
		level(made.exhaust);
		level(made.intake);
		level(made.compression);
		return made;
	}

	/** Cylinders at the same point do not add up past one cylinder's loudest: a bigger engine is not a louder hiss. */
	private static void level(float[] table) {
		float most = 1;
		for (float value : table)
			most = Math.max(most, value);
		for (int i = 0; i < table.length; i++)
			table[i] /= most;
	}

	private static int step(double degrees) {
		return Math.clamp((int) (degrees * STEPS / CYCLE), 0, STEPS - 1);
	}

	float exhaust(double degrees) {
		return exhaust[step(degrees)];
	}

	float intake(double degrees) {
		return intake[step(degrees)];
	}

	float compression(double degrees) {
		return compression[step(degrees)];
	}
}
