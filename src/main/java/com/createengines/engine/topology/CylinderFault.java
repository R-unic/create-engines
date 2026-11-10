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
	EXHAUST_BLOCKED(false),
	/** The head has a spark plug but no battery touches the engine to fire it. */
	NO_BATTERY(false),
	/** A plain shaft splits the camshaft, so the lobe is left with no cam to turn it in time. */
	BROKEN_CAM(false),
	/** A rotor housing with no rotor in it: nothing is working in the chamber, and nothing drags on the shaft. */
	NO_ROTOR(true),
	/** A rotor housing with no spark plug: its chamber is too long and thin for the charge to light itself. */
	NO_PLUG(false),
	/** The server's config has rotary engines switched off, so the housing does no work. */
	ROTARY_DISABLED(true),
	/** Every opening the intake has is under a fluid: the cylinder draws no air, so nothing in it can burn. */
	INTAKE_FLOODED(false),
	/** Every opening the exhaust has is under a fluid, which the burned gas cannot push its way out against. */
	EXHAUST_FLOODED(false),
	/**
	 * A fluid is against the cylinder, its head or its rotor housing: the engine is underwater, and nothing burns in it
	 * wherever its pipes lead. It still drags on the crank.
	 */
	SUBMERGED(false);

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

	/** Whether no air can reach the cylinder: no intake, one that is covered or underwater, or the cylinder is. */
	public static boolean intakeShut(int faults) {
		return NO_INTAKE.in(faults) || INTAKE_BLOCKED.in(faults) || INTAKE_FLOODED.in(faults) || SUBMERGED.in(faults);
	}

	/** Whether burned gas has no way out: no exhaust, one that is covered or underwater, or the cylinder is. */
	public static boolean exhaustShut(int faults) {
		return NO_EXHAUST.in(faults) || EXHAUST_BLOCKED.in(faults) || EXHAUST_FLOODED.in(faults) || SUBMERGED.in(faults);
	}

	/** Whether water is what stops the cylinder: it sits in it, or its intake or exhaust opens in it. */
	public static boolean underwater(int faults) {
		return INTAKE_FLOODED.in(faults) || EXHAUST_FLOODED.in(faults) || SUBMERGED.in(faults);
	}

	public static boolean disables(int faults) {
		for (CylinderFault fault : values())
			if (fault.disablesCylinder && fault.in(faults))
				return true;
		return false;
	}

	/**
	 * How much a cylinder with these faults loads the crank even though it isn't working: a rod on a throw drags a
	 * piston along its bore (or swings on its own, if there is no piston), whether or not there's a head on it.
	 * Zero if nothing is connected to the crank.
	 */
	public static double dragOnCrank(int faults) {
		if (NOT_ON_THROW.in(faults) || NO_ROD.in(faults) || NO_ROTOR.in(faults) || ROTARY_DISABLED.in(faults))
			return 0;
		return NO_PISTON.in(faults) ? 0.5 : 1;
	}
}
