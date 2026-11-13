package com.createengines.content.transmission;

import com.createengines.registry.CEBlockEntities;
import com.simibubi.create.content.kinetics.RotationPropagator;
import com.simibubi.create.content.kinetics.base.AbstractEncasedShaftBlock;
import com.simibubi.create.foundation.block.IBE;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A gearbox with selectable gears. Rotation goes in at one end of its shaft and comes out of the other at the
 * speed of the selected gear: neutral lets nothing through, the forward gears turn the output slower than the input,
 * reverse turns it slowly the other way. It is the same split shaft Create's gearshift and clutch are, so the whole
 * kinetic network on either side behaves as it does for them; because the stress a load puts on an engine is its
 * torque times the speed it turns at, a low gear lets a crank carry a load far too heavy for it direct.
 * <p>
 * The gear is set on a value panel; a redstone signal overrides it (see {@link Gears#fromSignal}).
 */
public class TransmissionBlock extends AbstractEncasedShaftBlock implements IBE<TransmissionBlockEntity> {

	public TransmissionBlock(Properties properties) {
		super(properties);
	}

	@Override
	public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos,
								boolean isMoving) {
		if (level.isClientSide)
			return;
		withBlockEntityDo(level, pos, TransmissionBlockEntity::updateGear);
	}

	/** The scheduled tick after a shift: put the transmission back on the network now that its gear has changed. */
	@Override
	public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		withBlockEntityDo(level, pos, be -> RotationPropagator.handleAdded(level, pos, be));
	}

	@Override
	public Class<TransmissionBlockEntity> getBlockEntityClass() {
		return TransmissionBlockEntity.class;
	}

	@Override
	public BlockEntityType<? extends TransmissionBlockEntity> getBlockEntityType() {
		return CEBlockEntities.TRANSMISSION.get();
	}
}
