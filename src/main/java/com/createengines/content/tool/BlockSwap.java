package com.createengines.content.tool;

import java.util.List;

import com.createengines.content.Encasing;
import com.createengines.content.cylinder.CylinderBlock;
import com.createengines.content.head.CylinderHeadBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * Changes one engine block for another that holds the same things, such as a standard cylinder for a precision one. Replacing the block
 * outright would drop every fitted part, gasket and casing while the new block kept them too (free parts), and would start it with no
 * wear (a free repair).
 */
final class BlockSwap {

	/** Every block state flag that drops something when its block goes. */
	private static final List<BooleanProperty> HELD = List.of(CylinderBlock.HAS_PISTON, CylinderBlock.HAS_ROD,
		CylinderHeadBlock.HAS_INJECTOR, CylinderHeadBlock.HAS_SPARK_PLUG, CylinderHeadBlock.HAS_ASSIST, CylinderHeadBlock.HAS_COIL,
		Encasing.ENCASED);

	private BlockSwap() {
	}

	/** Puts {@code next} where the block at {@code pos} is, with the same fitted parts, wear and other saved data; nothing drops. */
	static void keepingContents(Level level, BlockPos pos, BlockState next) {
		BlockEntity old = level.getBlockEntity(pos);
		CompoundTag saved = old == null ? null : old.saveWithoutMetadata(level.registryAccess());
		BlockState empty = level.getBlockState(pos);
		for (BooleanProperty held : HELD)
			if (empty.hasProperty(held))
				empty = empty.setValue(held, false);
		// Emptied first, without telling the neighbours, so that taking the old block away has nothing to drop. The parts and casing are
		// block state; the gasket is read from the block entity, which therefore goes before the block does
		level.setBlock(pos, empty, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
		level.removeBlockEntity(pos);
		level.setBlockAndUpdate(pos, next);
		BlockEntity placed = level.getBlockEntity(pos);
		if (saved == null || placed == null)
			return;
		placed.loadWithComponents(saved, level.registryAccess());
		placed.setChanged();
		level.sendBlockUpdated(pos, next, next, Block.UPDATE_ALL);
	}
}
