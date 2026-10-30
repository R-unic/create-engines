package com.createengines.registry;

import com.createengines.CreateEngines;

import dev.engine_room.flywheel.lib.model.baked.PartialModel;

public final class CEPartialModels {

	public static final PartialModel CRANKSHAFT = block("crankshaft/block");
	public static final PartialModel CRANK_THROW = block("crank_throw/block");
	public static final PartialModel CAMSHAFT = block("camshaft/block");
	public static final PartialModel CAM_LOBE = block("cam_lobe/block");
	public static final PartialModel PISTON = block("cylinder/piston");
	public static final PartialModel CONNECTING_ROD = block("cylinder/rod");
	public static final PartialModel VALVE_STEM = block("cylinder_head/valve");
	public static final PartialModel TURBO_WHEEL = block("turbocharger/wheel");

	private CEPartialModels() {
	}

	private static PartialModel block(String path) {
		return PartialModel.of(CreateEngines.asResource("block/" + path));
	}

	/** Loads the class so the partials are registered before models bake. */
	public static void init() {
	}
}
