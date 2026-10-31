package com.createengines.content.head;

import com.simibubi.create.foundation.fluid.SmartFluidTank;

/**
 * A block that holds its share of the engine's fuel gallery and oil gallery: a cylinder head, or a rotor housing, which
 * is its own head. Every one on a crankshaft feeds every injector, and each sets how much its own injector delivers.
 */
public interface GalleryMember {

	SmartFluidTank tank();

	SmartFluidTank oilTank();

	/** The share of the usual injector shot this member delivers, 1 being the usual shot. */
	double meteringFraction();

	/**
	 * The metering, as a percentage of the usual shot rounded to the dial's steps, that would have given a balanced mixture
	 * where {@code mixture} (a fuel-air equivalence ratio) was read at {@code fraction}.
	 */
	static int balancedMetering(double fraction, double mixture) {
		int steps = (int) Math.round(fraction * 100 / 10 / mixture);
		return Math.clamp(steps, 1, 20) * 10;
	}
}
