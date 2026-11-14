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
	public static final PartialModel TURBO_WHEEL = block("turbocharger/wheel");
	public static final PartialModel SUPERCHARGER_ROTOR = block("supercharger/rotor");
	public static final PartialModel FLYWHEEL_DISC = block("cast_flywheel/disc");

	private CEPartialModels() {
	}

	private static PartialModel block(String path) {
		return PartialModel.of(CreateEngines.asResource("block/" + path));
	}

	/** Loads the class so the partials are registered before models bake. */
	public static void init() {
	}
}
