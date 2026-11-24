package com.createengines.content.battery;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
		float available = deliverable(bank);
		if (!(wanted > 0) || !(available > 0))
			return 0;
		float taken = Math.min(wanted, available);
		float share = taken / available;
		for (int i = 0, n = bank.size(); i < n; i++) {
			BatteryBlockEntity battery = bank.get(i);
			battery.draw(battery.deliverable() * share);
		}
		return taken;
	}

	/** Takes {@code wanted} sparks only if the bank can give all of it, and returns whether it did. */
	public static boolean drawAll(List<BatteryBlockEntity> bank, float wanted) {
		if (deliverable(bank) < wanted - 1e-4f)
			return false;
		drawUpTo(bank, wanted);
		return true;
	}

	/**
	 * Puts up to {@code sparks} into the bank, each member taking in proportion to the room it has, so a bank fills evenly.
	 * Returns how much there was room for.
	 */
	public static float fill(List<BatteryBlockEntity> bank, float sparks) {
		float room = 0;
		for (int i = 0, n = bank.size(); i < n; i++)
			room += bank.get(i)
				.room();
		if (!(sparks > 0) || !(room > 0))
			return 0;
		float put = Math.min(sparks, room);
		float share = put / room;
		for (int i = 0, n = bank.size(); i < n; i++) {
			BatteryBlockEntity battery = bank.get(i);
			battery.charge(battery.room() * share);
		}
		return put;
	}
}
