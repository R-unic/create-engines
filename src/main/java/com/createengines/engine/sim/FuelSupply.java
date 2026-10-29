package com.createengines.engine.sim;

import org.jetbrains.annotations.Nullable;

/** Where a cylinder's injector gets its fuel from. Implemented by the injector block entity. */
public interface FuelSupply {

	FuelSupply NONE = new FuelSupply() {
		@Override
		public double draw(double mb) {
			return 0;
		}

		@Override
		public @Nullable FuelSpec fuel() {
			return null;
		}
	};

	/** Removes up to {@code mb} of fuel and returns how much was actually delivered. */
	double draw(double mb);

	/** Properties of the fuel currently available, or null if there is none. */
	@Nullable FuelSpec fuel();
}
