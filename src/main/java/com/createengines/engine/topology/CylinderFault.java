package com.createengines.engine.topology;

/** Something about how a cylinder is built that stops it working properly. Found by the scanner, not the simulation. */
public enum CylinderFault {
	NOT_ON_THROW(true),
	NO_PISTON(true),
	NO_ROD(true),
	NO_HEAD(true),
	NO_INJECTOR(false),
	NO_CAM(false),
	NO_INTAKE(false),
	INTAKE_BLOCKED(false),
	NO_EXHAUST(false),
	EXHAUST_BLOCKED(false);

	/** Without this the cylinder is not a working piston at all and is left out of the simulation. */
	public final boolean disablesCylinder;

	CylinderFault(boolean disablesCylinder) {
		this.disablesCylinder = disablesCylinder;
	}

	public int bit() {
		return 1 << ordinal();
	}

	public boolean in(int faults) {
		return (faults & bit()) != 0;
	}

	public String langKey() {
		return "goggles.fault." + name().toLowerCase(java.util.Locale.ROOT);
	}

	public static boolean disables(int faults) {
		for (CylinderFault fault : values())
			if (fault.disablesCylinder && fault.in(faults))
				return true;
		return false;
	}
}
