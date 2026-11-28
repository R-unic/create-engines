package com.createengines.registry;

import com.createengines.CreateEngines;
import com.createengines.content.PartCompression;
import com.createengines.content.cylinder.CylinderBlock;
import com.createengines.engine.sim.Wear;

import net.minecraft.world.level.block.state.BlockState;

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
	public static final PartialModel HIGH_COMPRESSION_PISTON = block("cylinder/high_compression_piston");
	public static final PartialModel LOW_COMPRESSION_PISTON = block("cylinder/low_compression_piston");
	/** The rings fitted to a piston, drawn at its own pose: one model for one, two and three. */
	private static final PartialModel[] PISTON_RINGS = {block("cylinder/rings_1"), block("cylinder/rings_2"), block("cylinder/rings_3")};
	public static final PartialModel HIGH_COMPRESSION_ROTOR = block("rotor_housing/high_compression_rotor");
	public static final PartialModel LOW_COMPRESSION_ROTOR = block("rotor_housing/low_compression_rotor");
	public static final PartialModel CONNECTING_ROD = block("cylinder/rod");
	public static final PartialModel LIGHT_CONNECTING_ROD = block("cylinder/light_rod");
	public static final PartialModel HEAVY_CONNECTING_ROD = block("cylinder/heavy_rod");
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
	public static final PartialModel TURBO_PORT = block("turbocharger/port");
	public static final PartialModel SUPERCHARGER_ROTOR = block("supercharger/rotor");
	public static final PartialModel FLYWHEEL_DISC = block("cast_flywheel/disc");
	/** A Dual-Mass Flywheel's two masses, drawn apart so the secondary can lag the primary (see SpringPackLag). */
	public static final PartialModel DUAL_MASS_PRIMARY = block("dual_mass_flywheel/primary");
	public static final PartialModel DUAL_MASS_SECONDARY = block("dual_mass_flywheel/secondary");

	private CEPartialModels() {
	}

	/** The model a cylinder draws for the rod fitted in it. */
	public static PartialModel rod(Wear.Rod kind) {
		return switch (kind) {
			case STANDARD -> CONNECTING_ROD;
			case LIGHT -> LIGHT_CONNECTING_ROD;
			case HEAVY -> HEAVY_CONNECTING_ROD;
		};
	}

	/** Every piston a cylinder draws, in the order {@link #pistonIndex} numbers them. */
	public static PartialModel[] pistons() {
		return new PartialModel[] {PISTON, FORGED_PISTON, HIGH_COMPRESSION_PISTON, LOW_COMPRESSION_PISTON};
	}

	/** Which of {@link #pistons} a cylinder in {@code state} draws. */
	public static int pistonIndex(BlockState state) {
		return switch (state.getValue(CylinderBlock.COMPRESSION)) {
			case HIGH -> 2;
			case LOW -> 3;
			case STANDARD -> state.getValue(CylinderBlock.FORGED_PISTON) ? 1 : 0;
		};
	}

	/** The model of {@code count} piston rings, 1 to 3. */
	public static PartialModel rings(int count) {
		return PISTON_RINGS[count - 1];
	}

	/** The model a rotor housing draws for the rotor fitted in it. */
	public static PartialModel rotor(PartCompression compression) {
		return switch (compression) {
			case STANDARD -> ROTOR;
			case HIGH -> HIGH_COMPRESSION_ROTOR;
			case LOW -> LOW_COMPRESSION_ROTOR;
		};
	}

	private static PartialModel block(String path) {
		return PartialModel.of(CreateEngines.asResource("block/" + path));
	}

	/** Loads the class so the partials are registered before models bake. */
	public static void init() {
	}
}
