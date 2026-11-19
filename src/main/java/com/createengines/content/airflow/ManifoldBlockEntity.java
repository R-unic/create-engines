package com.createengines.content.airflow;

import com.createengines.content.CasingPaint;
import com.createengines.content.PaintedBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/** What a plain manifold holds besides its shape: the dye of the casing it is clad in. It does nothing else and never ticks. */
public class ManifoldBlockEntity extends BlockEntity implements PaintedBlockEntity {

	private final PaintedBlockEntity.Cell paint = new PaintedBlockEntity.Cell();

	public ManifoldBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
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
		if (level != null)
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
	}

	@Override
	protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		paint.write(tag);
	}

	@Override
	protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		paint.read(tag, level, worldPosition, getBlockState());
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		return saveWithoutMetadata(registries);
	}

	@Override
	public Packet<ClientGamePacketListener> getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}
}
