package com.createengines.engine.sim;

import java.util.ArrayList;
import java.util.List;

/**
 * Which reserve lines a battery's goggles show, as lang keys, in order. Kept apart from the goggle code, which loads client classes and
 * cannot run in a server GameTest or a unit test.
 */
public final class ReserveLines {

	private ReserveLines() {
	}

	/**
	 * Whether the battery is a reserve; what the bank holds back when any battery of it is; and how to change it when the player is
	 * sneaking.
	 */
	public static List<String> keys(boolean reserve, int reserves, boolean sneaking) {
		List<String> keys = new ArrayList<>();
		keys.add(reserve ? "goggles.battery.reserve" : "goggles.battery.reserve_off");
		if (reserves > 0)
			keys.add("goggles.battery.reserve_split");
		if (sneaking)
			keys.add("goggles.battery.reserve_hint");
		return keys;
	}
}
