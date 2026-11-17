package com.createengines.client;

import com.createengines.content.CESpriteShifts;
import com.createengines.content.Encasing;

import com.simibubi.create.CreateClient;
import com.simibubi.create.content.decoration.encasing.CasingConnectivity;
import com.simibubi.create.content.decoration.encasing.EncasedCTBehaviour;
import com.simibubi.create.foundation.block.connected.CTSpriteShiftEntry;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Connected textures for a part that can be clad in any of the plain, riveted or louvred plate (see
 * {@link Encasing#STYLE}): it wears the sheet of the plate it is clad in, and joins only casing of the same plate, so a
 * riveted run is one riveted skin. Client only, like {@link CESpriteShifts}.
 */
public class StyledCasingCTBehaviour extends EncasedCTBehaviour {

	public StyledCasingCTBehaviour() {
		super(CESpriteShifts.ENGINE_CASING);
	}

	@Override
	public CTSpriteShiftEntry getShift(BlockState state, Direction direction, TextureAtlasSprite sprite) {
		return state.hasProperty(Encasing.STYLE) ? CESpriteShifts.forStyle(state.getValue(Encasing.STYLE))
			: CESpriteShifts.ENGINE_CASING;
	}

	@Override
	public boolean connectsTo(BlockState state, BlockState other, BlockAndTintGetter reader, BlockPos pos,
							  BlockPos otherPos, Direction face) {
		if (isBeingBlocked(state, reader, pos, otherPos, face))
			return false;
		CasingConnectivity connectivity = CreateClient.CASING_CONNECTIVITY;
		CasingConnectivity.Entry entry = connectivity.get(state);
		CasingConnectivity.Entry otherEntry = connectivity.get(other);
		if (entry == null || otherEntry == null)
			return false;
		if (!entry.isSideValid(state, face) || !otherEntry.isSideValid(other, face))
			return false;
		return skin(state, entry) == skin(other, otherEntry);
	}

	/** The sheet a block's skin is drawn from: its plate if it is a clad part, else what it registered as its casing. */
	private static CTSpriteShiftEntry skin(BlockState state, CasingConnectivity.Entry entry) {
		return state.hasProperty(Encasing.STYLE) ? CESpriteShifts.forStyle(state.getValue(Encasing.STYLE))
			: entry.getCasing();
	}
}
