package com.createengines.registry;

import com.createengines.CreateEngines;

import dev.engine_room.flywheel.lib.model.baked.PartialModel;

public final class CEPartialModels {

	public static final PartialModel CRANKSHAFT = block("crankshaft/block");
	public static final PartialModel CRANK_THROW = block("crank_throw/block");
	public static final PartialModel CAMSHAFT = block("camshaft/block");
	public static final PartialModel CAM_LOBE = block("cam_lobe/block");
	/** A lobe set's shaft, collars and spacers, and then its three lobes, each turned on its own. */
	public static final PartialModel CAM_LOBE_SHAFT = block("cam_lobe/shaft");
	public static final PartialModel CAM_LOBE_INTAKE = block("cam_lobe/intake");
	public static final PartialModel CAM_LOBE_EXHAUST = block("cam_lobe/exhaust");
	public static final PartialModel CAM_LOBE_INJECTOR = block("cam_lobe/injector");
	public static final PartialModel ROTOR_ECCENTRIC = block("rotor_housing/eccentric");
	public static final PartialModel ROTOR = block("rotor_housing/rotor");
	public static final PartialModel PISTON = block("cylinder/piston");
	public static final PartialModel FORGED_PISTON = block("cylinder/forged_piston");
	public static final PartialModel CONNECTING_ROD = block("cylinder/rod");
	public static final PartialModel VALVE_STEM = block("cylinder_head/valve");
	/** The wear mode's wrecks, each laid over the part it is the wreck of: a head a rod went through, a gasket pushed out of the joint, the wreck in a bore. */
	public static final PartialModel HEAD_WRECK = block("cylinder_head/wreck");
	public static final PartialModel HEAD_BLOWN_GASKET = block("cylinder_head/blown_gasket");
	public static final PartialModel HEAD_WARPED = block("cylinder_head/warped");
	public static final PartialModel CYLINDER_WRECK = block("cylinder/wreck");
	/** A failed set of apex seals, sprung out of a rotor housing, in the frame of a housing whose shaft stands upright. */
	public static final PartialModel ROTOR_WRECK = block("rotor_housing/wreck");
	/** A scorched band and a ring of heat colour, drawn at a seized piston's own pose. */
	public static final PartialModel SEIZED_RING = block("cylinder/seized_ring");
	/** A pushrod beside a cylinder block that a cam works from the side, and the rocker on the head it tips. */
	public static final PartialModel PUSHROD = block("cylinder/pushrod");
	public static final PartialModel ROCKER = block("cylinder_head/rocker");
	/** The flap of a rain cap, which the renderer lifts about its hinge. */
	public static final PartialModel RAIN_CAP_FLAP = block("rain_cap/flap");
	public static final PartialModel TURBO_WHEEL = block("turbocharger/wheel");
	public static final PartialModel SUPERCHARGER_ROTOR = block("supercharger/rotor");
	public static final PartialModel FLYWHEEL_DISC = block("cast_flywheel/disc");
	/** A Dual-Mass Flywheel's two masses, drawn apart so the secondary can lag the primary (see SpringPackLag). */
	public static final PartialModel DUAL_MASS_PRIMARY = block("dual_mass_flywheel/primary");
	public static final PartialModel DUAL_MASS_SECONDARY = block("dual_mass_flywheel/secondary");

	private CEPartialModels() {
	}

	private static PartialModel block(String path) {
		return PartialModel.of(CreateEngines.asResource("block/" + path));
	}

	/** Loads the class so the partials are registered before models bake. */
	public static void init() {
	}
}
