package com.createengines.engine.sim;

/**
 * One way a refining column turns a feed into a product, in the simulation's plain numbers. The column recipe files
 * hold the same fields; a route is what the recipe boils down to once the fluids and items are taken out.
 *
 * @param feedMb          feed used by one batch of the ratio, in mB
 * @param productMb       product made from that much feed, in mB. More than the feed is allowed: the column wastes less
 *                        than a basin does
 * @param heat            the heat the base needs
 * @param catalystFeedMb  feed one catalyst item serves before the next is needed, in mB; 0 for a route with no catalyst
 * @param speed           how fast this route runs against the column's top rate: the dear fuels come over slowly
 */
public record RefiningRoute(int feedMb, int productMb, Heat heat, int catalystFeedMb, double speed) {

	public RefiningRoute {
		if (feedMb <= 0 || productMb <= 0)
			throw new IllegalArgumentException("a route needs a feed and a product above 0 mB");
		if (catalystFeedMb < 0)
			throw new IllegalArgumentException("a catalyst cannot serve less than 0 mB");
		if (speed <= 0)
			throw new IllegalArgumentException("a route's speed must be above 0");
	}

	/** Product for each mB of feed. */
	public double yield() {
		return (double) productMb / feedMb;
	}

	/** Feed used to make a bucket (1000 mB) of product. */
	public double feedPerBucket() {
		return 1000.0 * feedMb / productMb;
	}

	/** Catalyst items used to make a bucket of product; 0 when the route has none. */
	public double catalystPerBucket() {
		return catalystFeedMb == 0 ? 0 : feedPerBucket() / catalystFeedMb;
	}

	public boolean hasCatalyst() {
		return catalystFeedMb > 0;
	}
}
