package com.createengines.client;

import com.simibubi.create.content.kinetics.simpleRelays.encased.EncasedCogCTBehaviour;
import com.simibubi.create.foundation.block.connected.CTSpriteShiftEntry;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;

/** An encased cogwheel's connected textures, joining casing by the plate it is drawn in (see {@link StyledCasingCTBehaviour#joinsBySkin}). */
public class StyledCogCTBehaviour extends EncasedCogCTBehaviour {

	public StyledCogCTBehaviour(CTSpriteShiftEntry shift) {
		super(shift);
	}

	@Override
	public boolean connectsTo(BlockState state, BlockState other, BlockAndTintGetter reader, BlockPos pos,
							  BlockPos otherPos, Direction face) {
		return !isBeingBlocked(state, reader, pos, otherPos, face) && StyledCasingCTBehaviour.joinsBySkin(state, other, face);
	}
}
