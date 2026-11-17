package com.createengines.content;

import com.createengines.CreateEngines;
import com.simibubi.create.foundation.block.connected.AllCTTypes;
import com.simibubi.create.foundation.block.connected.CTSpriteShiftEntry;
import com.simibubi.create.foundation.block.connected.CTSpriteShifter;

/**
 * The connected-texture sheets the mod draws on blocks. Only ever read from suppliers that run on the client (the
 * connected-texture registration in {@code CEBlocks}), the way Create reads its own.
 */
public final class CESpriteShifts {

	/** Engine casing, and everything it clads: a 16 by 16 plain tile and a sheet of its 8 by 8 connected variants. */
	public static final CTSpriteShiftEntry ENGINE_CASING = CTSpriteShifter.getCT(AllCTTypes.OMNIDIRECTIONAL,
		CreateEngines.asResource("block/engine_casing"), CreateEngines.asResource("block/engine_casing_connected"));

	/** The riveted plate: the same sheet layout as the plain casing, with a rivet pattern on the plate. */
	public static final CTSpriteShiftEntry ENGINE_RIVETED_CASING = CTSpriteShifter.getCT(AllCTTypes.OMNIDIRECTIONAL,
		CreateEngines.asResource("block/engine_riveted_casing"),
		CreateEngines.asResource("block/engine_riveted_casing_connected"));

	/** The louvred plate: slats across the plate. */
	public static final CTSpriteShiftEntry ENGINE_LOUVRED_CASING = CTSpriteShifter.getCT(AllCTTypes.OMNIDIRECTIONAL,
		CreateEngines.asResource("block/engine_louvred_casing"),
		CreateEngines.asResource("block/engine_louvred_casing_connected"));

	/** The window plate: the plate with a pane of glass in it. */
	public static final CTSpriteShiftEntry ENGINE_WINDOW_CASING = CTSpriteShifter.getCT(AllCTTypes.OMNIDIRECTIONAL,
		CreateEngines.asResource("block/engine_window_casing"),
		CreateEngines.asResource("block/engine_window_casing_connected"));

	/**
	 * Makes the connected-texture behaviour of a part that can be clad in any plate. It is client code, so the client entry
	 * point puts it here before blocks register; the registration reads it only on the client.
	 */
	public static java.util.function.Supplier<com.simibubi.create.foundation.block.connected.ConnectedTextureBehaviour> STYLED_CASING_BEHAVIOUR = () -> {
		throw new IllegalStateException("The styled casing behaviour is only made on the client");
	};

	/** The sheet a part clad in a plate draws: the window plate is drawn by its own models, so it keeps the plain one. */
	public static CTSpriteShiftEntry forStyle(CasingStyle style) {
		return switch (style) {
			case RIVETED -> ENGINE_RIVETED_CASING;
			case LOUVRED -> ENGINE_LOUVRED_CASING;
			case WINDOW -> ENGINE_WINDOW_CASING;
			case PLAIN -> ENGINE_CASING;
		};
	}

	private CESpriteShifts() {
	}
}
