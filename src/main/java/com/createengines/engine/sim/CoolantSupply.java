package com.createengines.engine.sim;

/** Where an engine's coolant comes from. Implemented by the coolant tanks of the oil pans touching it. */
public interface CoolantSupply {

	CoolantSupply NONE = new CoolantSupply() {
		@Override
		public double draw(double mb) {
			return 0;
		}

		@Override
		public double quality() {
			return 0;
		}
	};

	/** Removes up to {@code mb} of coolant and returns how much was actually delivered. */
	double draw(double mb);

	/**
	 * How well the coolant on hand carries heat away, as a share of the best there is: 0 where there is none, plain water
	 * about half and a proper coolant 1.
	 */
	double quality();
}
