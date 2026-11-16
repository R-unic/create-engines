package com.createengines.engine.sim;

/** The redstone level a comparator reads off a tank: 0 when empty, 1 to 15 as it fills, 15 only when full. */
public final class TankSignal {

	private TankSignal() {
	}

	public static int of(int amount, int capacity) {
		if (amount <= 0 || capacity <= 0)
			return 0;
		return 1 + (int) (14L * Math.min(amount, capacity) / capacity);
	}
}
