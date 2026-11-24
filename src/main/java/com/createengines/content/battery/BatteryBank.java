package com.createengines.content.battery;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.createengines.engine.sim.ChargePriority;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

/**
 * Batteries that touch are one bank, as Create's tanks that touch are one tank: its capacity is the sum of theirs, charging
 * and drawing are shared out between its members in proportion, and anything that reaches one of them reaches all. A lone
 * battery is a bank of one, with the numbers a single battery always had.
 * <p>
 * A bank is not stored anywhere. It is found by walking from a battery over the batteries that touch it, so it follows the
 * blocks as they are placed and broken with nothing to save, and a bank member in a chunk that is not loaded is left out
 * until it is.
 */
public final class BatteryBank {

	private BatteryBank() {
	}

	/** The batteries in the bank that includes the one at {@code start}, in the order the walk met them, {@code start}'s first. */
	public static List<BatteryBlockEntity> around(Level level, BlockPos start) {
		List<BatteryBlockEntity> found = new ArrayList<>();
		Set<BlockPos> seen = new HashSet<>();
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		seen.add(start.immutable());
		queue.add(start.immutable());
		while (!queue.isEmpty()) {
			BlockPos at = queue.poll();
			if (!level.isLoaded(at) || !(level.getBlockEntity(at) instanceof BatteryBlockEntity battery) || battery.isRemoved())
				continue;
			found.add(battery);
			for (Direction direction : Direction.values()) {
				BlockPos next = at.relative(direction);
				if (seen.add(next.immutable()))
					queue.add(next.immutable());
			}
		}
		return found;
	}

	/** The charge held by every member, in sparks. */
	public static float charge(List<BatteryBlockEntity> bank) {
		float total = 0;
		for (int i = 0, n = bank.size(); i < n; i++)
			total += bank.get(i)
				.charge();
		return total;
	}

	/** The capacity of every member together, in sparks. */
	public static float capacity(List<BatteryBlockEntity> bank) {
		float total = 0;
		for (int i = 0, n = bank.size(); i < n; i++)
			total += bank.get(i)
				.capacity();
		return total;
	}

	/** The charge the members can give now: what they hold, less what the cold keeps back in each. */
	public static float deliverable(List<BatteryBlockEntity> bank) {
		float total = 0;
		for (int i = 0, n = bank.size(); i < n; i++)
			total += bank.get(i)
				.deliverable();
		return total;
	}

	/** Charge gained (+) or lost (-) a tick across the bank lately, in sparks. */
	public static float rate(List<BatteryBlockEntity> bank) {
		float total = 0;
		for (int i = 0, n = bank.size(); i < n; i++)
			total += bank.get(i)
				.rate();
		return total;
	}

	/**
	 * Takes up to {@code wanted} sparks out of the bank, each member giving in proportion to what it can deliver, so a bank runs
	 * down evenly. Returns how much was taken.
	 */
	public static float drawUpTo(List<BatteryBlockEntity> bank, float wanted) {
		return drawUpTo(bank, wanted, ChargePriority.Use.ESSENTIAL);
	}

	/**
	 * The same for a draw of {@code use}: an essential one (a spark, a starter) may take from any battery, the reserve last; an
	 * optional one (another mod's wire) only from batteries that are not set to reserve ({@link ChargePriority}). With no reserve in
	 * the bank every use gives what it always gave.
	 */
	public static float drawUpTo(List<BatteryBlockEntity> bank, float wanted, ChargePriority.Use use) {
		if (reserveCount(bank) == 0) {
			// The common case, and a spark is drawn many times a tick: no arrays, the even share a bank has always given
			float all = deliverable(bank);
			if (!(wanted > 0) || !(all > 0))
				return 0;
			float taken = Math.min(wanted, all);
			float share = taken / all;
			for (int i = 0, n = bank.size(); i < n; i++) {
				BatteryBlockEntity battery = bank.get(i);
				battery.draw(battery.deliverable() * share);
			}
			return taken;
		}
		float[] deliverable = deliverables(bank);
		boolean[] reserved = reserves(bank);
		float available = ChargePriority.available(deliverable, reserved, use);
		if (!(wanted > 0) || !(available > 0))
			return 0;
		float[] amounts = ChargePriority.split(deliverable, reserved, wanted, use);
		for (int i = 0, n = bank.size(); i < n; i++)
			bank.get(i)
				.draw(amounts[i]);
		return Math.min(wanted, available);
	}

	/** Takes {@code wanted} sparks only if the bank can give all of it, and returns whether it did. */
	public static boolean drawAll(List<BatteryBlockEntity> bank, float wanted) {
		if (deliverable(bank) < wanted - 1e-4f)
			return false;
		drawUpTo(bank, wanted);
		return true;
	}

	/** The charge a draw of {@code use} could have from the bank now: all of it for an essential one, the batteries that are not a reserve for an optional one. */
	public static float deliverable(List<BatteryBlockEntity> bank, ChargePriority.Use use) {
		// An injector assist asks every shot: with no reserve in the bank every use sees all of it, so no arrays are needed
		if (reserveCount(bank) == 0)
			return deliverable(bank);
		return ChargePriority.available(deliverables(bank), reserves(bank), use);
	}

	/** How many of the bank's batteries are set to hold their charge in reserve. */
	public static int reserveCount(List<BatteryBlockEntity> bank) {
		int count = 0;
		for (int i = 0, n = bank.size(); i < n; i++)
			if (bank.get(i)
				.isReserve())
				count++;
		return count;
	}

	/** The charge held by the batteries that are set to reserve, in sparks. */
	public static float reserveCharge(List<BatteryBlockEntity> bank) {
		float total = 0;
		for (int i = 0, n = bank.size(); i < n; i++) {
			BatteryBlockEntity battery = bank.get(i);
			if (battery.isReserve())
				total += battery.charge();
		}
		return total;
	}

	private static float[] deliverables(List<BatteryBlockEntity> bank) {
		float[] values = new float[bank.size()];
		for (int i = 0; i < values.length; i++)
			values[i] = bank.get(i)
				.deliverable();
		return values;
	}

	private static boolean[] reserves(List<BatteryBlockEntity> bank) {
		boolean[] values = new boolean[bank.size()];
		for (int i = 0; i < values.length; i++)
			values[i] = bank.get(i)
				.isReserve();
		return values;
	}

	/**
	 * Puts up to {@code sparks} into the bank, each member taking in proportion to the room it has, so a bank fills evenly.
	 * Returns how much there was room for.
	 */
	public static float fill(List<BatteryBlockEntity> bank, float sparks) {
		float[] rooms = new float[bank.size()];
		float room = 0;
		for (int i = 0; i < rooms.length; i++) {
			rooms[i] = bank.get(i)
				.room();
			room += rooms[i];
		}
		if (!(sparks > 0) || !(room > 0))
			return 0;
		// A reserve fills first; with none in the bank this is the even share it has always been
		float[] amounts = ChargePriority.fillSplit(rooms, reserves(bank), sparks);
		for (int i = 0; i < rooms.length; i++)
			bank.get(i)
				.charge(amounts[i]);
		return Math.min(sparks, room);
	}
}
