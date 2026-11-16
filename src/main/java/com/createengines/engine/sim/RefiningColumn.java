package com.createengines.engine.sim;

/**
 * The work of one refining column, a tick at a time. It holds only what carries from tick to tick (a fraction of a mB
 * of rate, the remainder of a ratio that does not divide, and the catalyst still working), and takes the rest in:
 * what the feed tank holds, what room the product tank has, the heat of the base and the catalyst items waiting in the
 * Reboiler. Nothing here knows about blocks or fluids, so it is tested as plain Java.
 */
public final class RefiningColumn {

	/** Feed a single block of the column's body can pass in a tick, at a route's full speed. */
	public static final double RATE_PER_BLOCK = 0.25;
	/** Narrowest and widest a column body can be, in blocks. */
	public static final int MIN_WIDTH = 1;
	public static final int MAX_WIDTH = 3;

	/** What is holding a column back, in the order goggles look for it. */
	public enum Limit {
		/** Nothing: it runs at the rate its size allows. */
		NONE,
		/** No feed to take. */
		NO_FEED,
		/** The base is too cold for the route. */
		COLD,
		/** The catalyst has run out. */
		NO_CATALYST,
		/** The product tank is full. */
		PRODUCT_FULL
	}

	/**
	 * What one tick did.
	 *
	 * @param feedUsed      feed taken, mB
	 * @param productMade   product made, mB
	 * @param catalystUsed  catalyst items taken from the Reboiler
	 * @param limit         what held the column back, {@link Limit#NONE} when nothing did
	 */
	public record Step(int feedUsed, int productMade, int catalystUsed, Limit limit) {
	}

	/** Most of a tick's rate a column keeps over for the next one when something held it back. */
	private static final double MAX_BANKED = 1;

	private double carry;
	private long productRemainder;
	private long catalystCredit;

	/** Feed a column of this size can pass in a tick, before the route's speed: one block's rate for each block of the body. */
	public static double ratePerTick(int width, int height) {
		if (width < MIN_WIDTH || width > MAX_WIDTH || height < 1)
			throw new IllegalArgumentException("a column is " + MIN_WIDTH + " to " + MAX_WIDTH + " blocks wide and at least 1 tall");
		return RATE_PER_BLOCK * width * width * height;
	}

	/** Feed still covered by catalyst already loaded, mB. */
	public long catalystCredit() {
		return catalystCredit;
	}

	/** Forgets everything in progress: a column that is rebuilt starts clean. */
	public void reset() {
		carry = 0;
		productRemainder = 0;
		catalystCredit = 0;
	}

	/**
	 * Runs one tick.
	 *
	 * @param route          what the column is making
	 * @param width          blocks wide, 1 to 3
	 * @param height         blocks tall
	 * @param heat           the heat of the base
	 * @param feedAvailable  feed in the tank, mB
	 * @param productRoom    free space in the product tank, mB
	 * @param catalystItems  catalyst items waiting in the Reboiler
	 */
	public Step tick(RefiningRoute route, int width, int height, Heat heat, int feedAvailable, int productRoom,
					 int catalystItems) {
		if (!heat.satisfies(route.heat())) {
			carry = 0;
			return new Step(0, 0, 0, Limit.COLD);
		}
		if (feedAvailable <= 0) {
			carry = 0;
			return new Step(0, 0, 0, Limit.NO_FEED);
		}
		long byProduct = maxFeedForRoom(route, productRoom);
		if (byProduct <= 0)
			return new Step(0, 0, 0, Limit.PRODUCT_FULL);
		long byCatalyst = Long.MAX_VALUE;
		if (route.hasCatalyst()) {
			byCatalyst = catalystCredit + (long) catalystItems * route.catalystFeedMb();
			if (byCatalyst <= 0) {
				carry = 0;
				return new Step(0, 0, 0, Limit.NO_CATALYST);
			}
		}

		double rate = carry + ratePerTick(width, height) * route.speed();
		long wanted = (long) Math.floor(rate);
		long feed = wanted;
		Limit limit = Limit.NONE;
		if (feedAvailable < feed) {
			feed = feedAvailable;
			limit = Limit.NO_FEED;
		}
		if (byProduct < feed) {
			feed = byProduct;
			limit = Limit.PRODUCT_FULL;
		}
		if (byCatalyst < feed) {
			feed = byCatalyst;
			limit = Limit.NO_CATALYST;
		}

		int catalystUsed = 0;
		if (route.hasCatalyst()) {
			if (feed > catalystCredit) {
				catalystUsed = (int) ((feed - catalystCredit + route.catalystFeedMb() - 1) / route.catalystFeedMb());
				catalystCredit += (long) catalystUsed * route.catalystFeedMb();
			}
			catalystCredit -= feed;
		}

		long numerator = feed * route.productMb() + productRemainder;
		int made = (int) (numerator / route.feedMb());
		productRemainder = numerator % route.feedMb();

		carry = Math.min(MAX_BANKED, rate - feed);
		return new Step((int) feed, made, catalystUsed, limit);
	}

	/** Most feed that can be turned over without making more product than the tank has room for. */
	private long maxFeedForRoom(RefiningRoute route, int productRoom) {
		if (productRoom <= 0)
			return 0;
		// product = floor((feed * P + remainder) / F) must stay at or under the room, so feed * P + remainder < (room + 1) * F
		long bound = ((long) productRoom + 1) * route.feedMb() - productRemainder - 1;
		return Math.max(0, bound / route.productMb());
	}
}
