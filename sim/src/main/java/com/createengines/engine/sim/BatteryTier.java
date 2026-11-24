package com.createengines.engine.sim;

/**
 * The kinds of battery block, as plain numbers: how many sparks each holds and how much of that the cold keeps back. A plain battery
 * is 200 sparks and loses up to half to the cold, exactly as it always did. Tiers that touch join into one bank like any other batteries.
 * No Minecraft types here, so it is unit tested.
 */
public enum BatteryTier {

	/** The battery that has always been there. */
	PLAIN(BatteryChemistry.CAPACITY, BatteryChemistry.MAX_COLD_LOSS),

	/** Half the capacity, and the cold does not touch it: the one to put outdoors in a snowy biome or away from any heat. */
	DRY_CELL(100, 0),

	/** Four times the plain battery, and the cold holds back up to three quarters of it: the bank for a motor, kept somewhere warm. */
	LARGE(800, 0.75);

	private final float capacity;
	private final double coldLoss;

	BatteryTier(float capacity, double coldLoss) {
		this.capacity = capacity;
		this.coldLoss = coldLoss;
	}

	/** Sparks one battery of this tier holds. */
	public float capacity() {
		return capacity;
	}

	/** The share of its capacity a battery of this tier cannot deliver when it is as cold as it gets, 0 to 1. */
	public double coldLoss() {
		return coldLoss;
	}
}
