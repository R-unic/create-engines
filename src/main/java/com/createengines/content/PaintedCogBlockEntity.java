package com.createengines.content;

import com.simibubi.create.content.kinetics.simpleRelays.SimpleKineticBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/** The block entity of an engine-cased cogwheel, small or large: Create's own cog, plus the dye of its casing. */
public class PaintedCogBlockEntity extends SimpleKineticBlockEntity implements PaintedBlockEntity {

	private final PaintedBlockEntity.Cell paint = new PaintedBlockEntity.Cell();

	public PaintedCogBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	@Override
	public CasingPaint casingPaint() {
		return paint.get();
	}

	@Override
	public void setCasingPaint(CasingPaint dye) {
		paint.set(dye);
		setChanged();
		sendData();
	}

	@Override
	protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
		super.write(tag, registries, clientPacket);
		paint.write(tag);
	}

	@Override
	protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
		super.read(tag, registries, clientPacket);
		paint.read(tag, level, worldPosition, getBlockState());
	}
}
