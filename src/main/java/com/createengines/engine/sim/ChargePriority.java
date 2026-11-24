package com.createengines.engine.sim;

/**
 * Charge priority: which batteries of a bank give to which draws, as plain numbers. A battery can be set to <b>reserve</b>: it
 * holds its charge for the starter and the spark plugs, and a heavier, optional consumer (another mod's wire drawing Forge
 * Energy, an electric injector) cannot take it. No Minecraft types here, so it is unit tested; {@code BatteryBank} applies it.
 * <p>
 * With no battery set to reserve, every rule here gives exactly what a bank has always given: draws and fills are shared out in
 * proportion. Nothing is stored for a bank; each battery only remembers whether it is a reserve.
 */
public final class ChargePriority {

	/** What a draw is for. */
	public enum Use {
		/** A spark plug firing or a starter turning the engine over: it may take from any battery, reserve last. */
		ESSENTIAL,
		/** Anything else: it may take only from batteries that are not a reserve. */
		OPTIONAL
	}

	private ChargePriority() {
	}

	/**
	 * What a draw of {@code use} can have from members that can each {@code deliverable} sparks, the {@code reserved} ones marked.
	 * An essential draw sees everything; an optional one sees the members that are not a reserve.
	 */
	public static float available(float[] deliverable, boolean[] reserved, Use use) {
		float total = 0;
		for (int i = 0; i < deliverable.length; i++)
			if (use == Use.ESSENTIAL || !reserved[i])
				total += deliverable[i];
		return total;
	}

	/**
	 * How much of {@code wanted} sparks each member gives, in the order of the arrays. Within a group each gives in proportion to what
	 * it can deliver, so a bank runs down evenly; an essential draw takes from the members that are not a reserve first and the
	 * reserve for what is left. The amounts add up to the smaller of {@code wanted} and {@link #available}.
	 */
	public static float[] split(float[] deliverable, boolean[] reserved, float wanted, Use use) {
		float[] amounts = new float[deliverable.length];
		float available = available(deliverable, reserved, use);
		if (!(wanted > 0) || !(available > 0))
			return amounts;
		float taken = Math.min(wanted, available);
		float open = 0;
		for (int i = 0; i < deliverable.length; i++)
			if (!reserved[i])
				open += deliverable[i];
		float fromOpen = Math.min(taken, open);
		if (open > 0) {
			float share = fromOpen / open;
			for (int i = 0; i < deliverable.length; i++)
				if (!reserved[i])
					amounts[i] = deliverable[i] * share;
		}
		float left = taken - fromOpen;
		if (use == Use.ESSENTIAL && left > 0) {
			float held = available - open;
			if (held > 0) {
				float share = Math.min(left, held) / held;
				for (int i = 0; i < deliverable.length; i++)
					if (reserved[i])
						amounts[i] = deliverable[i] * share;
			}
		}
		return amounts;
	}

	/**
	 * How much of {@code sparks} each member takes in, given the room each has. A reserve fills first, so what is held for starting
	 * is the first thing an alternator tops up; within a group each takes in proportion to its room. With no reserve every member is
	 * in one group, as it always was. The amounts add up to the smaller of {@code sparks} and the room in all.
	 */
	public static float[] fillSplit(float[] room, boolean[] reserved, float sparks) {
		float[] amounts = new float[room.length];
		float reserveRoom = 0;
		float openRoom = 0;
		for (int i = 0; i < room.length; i++) {
			if (reserved[i])
				reserveRoom += room[i];
			else
				openRoom += room[i];
		}
		if (!(sparks > 0) || !(reserveRoom + openRoom > 0))
			return amounts;
		float put = Math.min(sparks, reserveRoom + openRoom);
		float intoReserve = Math.min(put, reserveRoom);
		if (reserveRoom > 0) {
			float share = intoReserve / reserveRoom;
			for (int i = 0; i < room.length; i++)
				if (reserved[i])
					amounts[i] = room[i] * share;
		}
		float intoOpen = put - intoReserve;
		if (intoOpen > 0 && openRoom > 0) {
			float share = intoOpen / openRoom;
			for (int i = 0; i < room.length; i++)
				if (!reserved[i])
					amounts[i] = room[i] * share;
		}
		return amounts;
	}
}
