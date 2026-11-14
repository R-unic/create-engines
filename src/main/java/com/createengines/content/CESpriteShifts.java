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

	private CESpriteShifts() {
	}
}
