package com.createengines.engine.topology;

import com.createengines.engine.sim.CylinderSpec;

/**
 * Which precision parts a cylinder is built from, as bits. Each part improves the crude cylinder in one way and they
 * add up, so the scanner just notes which are fitted and the controller builds the cylinder's spec from them.
 */
public final class PrecisionParts {

	/** A precision cylinder block: machined liner, higher compression, less wall friction. */
	public static final int BORE = 1;
	/** A precision cylinder head: four valves and a pent roof. */
	public static final int HEAD = 2;
	/** A forged piston: lighter, with less drag. */
	public static final int PISTON = 4;
	/** A precision injector: finer atomisation, so the charge lights sooner and burns faster. */
	public static final int INJECTOR = 8;
	public static final int ALL = BORE | HEAD | PISTON | INJECTOR;

	private PrecisionParts() {
	}

	public static boolean has(int parts, int part) {
		return (parts & part) != 0;
	}

	/** How many of the four precision parts are fitted. */
	public static int count(int parts) {
		return Integer.bitCount(parts & ALL);
	}

	/** The physical description of a rotary chamber built from these parts; only the injector applies to a rotor. */
	public static CylinderSpec rotarySpec(int parts) {
		return CylinderSpec.buildRotary(has(parts, INJECTOR));
	}

	/** The physical description of a cylinder built from these parts. */
	public static CylinderSpec spec(int parts) {
		return CylinderSpec.build(has(parts, BORE), has(parts, HEAD), has(parts, PISTON), has(parts, INJECTOR));
	}
}
