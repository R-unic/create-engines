package com.createengines.content;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A part whose casing dye lives in its block entity, not in its block state. A dye is only a tint, and as a state property
 * it multiplied every pipe, cylinder and crank state by seventeen; held here it costs nothing until a part is painted. The
 * plain Engine Casing blocks keep the property (they have a handful of states and no block entity).
 * <p>
 * The dye is saved under {@link #KEY} and goes to the client with the rest of the block entity's data. A world saved
 * before 2.0 had it in the block state; {@link PaintMigration} moves it across when such a chunk loads.
 */
public interface PaintedBlockEntity {

	/** The key the dye is saved under: the name of a {@link CasingPaint}. */
	String KEY = "Paint";

	CasingPaint casingPaint();

	/** Sets the dye and saves and sends it. */
	void setCasingPaint(CasingPaint paint);

	/** The dye on whatever is at a position, {@link CasingPaint#NONE} if it holds none. */
	static CasingPaint at(net.minecraft.world.level.BlockGetter level, BlockPos pos) {
		return level != null && pos != null && level.getBlockEntity(pos) instanceof PaintedBlockEntity painted
			? painted.casingPaint() : CasingPaint.NONE;
	}

	/** The dye held in a block entity's data; a missing or unknown name is bare steel. */
	static CasingPaint read(CompoundTag tag) {
		return tag.contains(KEY, net.minecraft.nbt.Tag.TAG_STRING) ? CasingPaint.byName(tag.getString(KEY)) : CasingPaint.NONE;
	}

	/** Writes the dye into a block entity's data; bare steel writes nothing, so an unpainted part saves what it always did. */
	static void write(CompoundTag tag, CasingPaint paint) {
		if (paint != CasingPaint.NONE)
			tag.putString(KEY, paint.getSerializedName());
	}

	/**
	 * The dye of one block entity, for the classes that carry it to share: it holds the value and says whether a read changed
	 * it, so the client can redraw the block.
	 */
	final class Cell {

		private CasingPaint paint = CasingPaint.NONE;

		public CasingPaint get() {
			return paint;
		}

		public void set(CasingPaint paint) {
			this.paint = paint == null ? CasingPaint.NONE : paint;
		}

		public void write(CompoundTag tag) {
			PaintedBlockEntity.write(tag, paint);
		}

		/** Reads the dye from a tag and, on a client where it changed, has the block drawn again. */
		public void read(CompoundTag tag, Level level, BlockPos pos, BlockState state) {
			CasingPaint before = paint;
			paint = PaintedBlockEntity.read(tag);
			if (before != paint && level != null && level.isClientSide)
				level.sendBlockUpdated(pos, state, state, 0);
		}
	}
}
