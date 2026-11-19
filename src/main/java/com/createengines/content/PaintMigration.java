package com.createengines.content;

import java.util.ArrayList;
import java.util.List;

import com.createengines.CreateEngines;
import com.createengines.engine.sim.PackedPalette;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.neoforged.neoforge.event.level.ChunkDataEvent;

/**
 * Carries the dye of a world saved before 2.0 across. Then, the dye of a cylinder, crank, cam, manifold, cased shaft or cased
 * cogwheel was a block state property ({@code paint}); now it is held in the part's block entity
 * ({@link PaintedBlockEntity}). A saved state's property the block no longer has is dropped when the chunk is read, so the
 * dye has to be taken from the raw chunk data first. This does that when a chunk loads: it finds every such block whose saved
 * state had a dye, and writes the dye into that block's block-entity data, before the chunk is used.
 * <p>
 * It is safe to run on every chunk, every time: a chunk with nothing to migrate is only scanned, a block entity that already
 * holds a dye is left alone, and a chunk that cannot be understood is left as it is. Once the chunk is saved again the old
 * property is gone and there is nothing more to do. Casing blocks themselves keep their {@code paint} property and are not
 * touched.
 * <p>
 * Not covered: a part saved inside a moving contraption or a saved structure or schematic file loses its dye on the way in,
 * because those carry block states with no position to attach it to (the parts work as before, only the tint is lost).
 */
public final class PaintMigration {

	/** A dyed part found in a saved chunk. */
	public record Found(BlockPos pos, Block block, CasingPaint paint) {
	}

	private PaintMigration() {
	}

	public static void onChunkLoad(ChunkDataEvent.Load event) {
		try {
			ChunkAccess chunk = event.getChunk();
			CompoundTag data = event.getData();
			List<Found> found = scan(data, chunk.getPos());
			if (found.isEmpty())
				return;
			apply(data, chunk, found);
			CreateEngines.LOGGER.info("Moved the casing dye of {} parts in chunk {} from block states to block entities",
				found.size(), chunk.getPos());
		} catch (RuntimeException e) {
			// A world must always load; an unreadable chunk keeps what it has and loses only the tint
			CreateEngines.LOGGER.warn("Could not move the casing dye in a chunk, so its parts load undyed: {}", e.toString());
		}
	}

	/** Whether a saved block name is a part that used to carry the dye as state and now holds it in a block entity. */
	static Block migratingBlock(String name) {
		ResourceLocation id = ResourceLocation.tryParse(name);
		if (id == null || !CreateEngines.MOD_ID.equals(id.getNamespace()))
			return null;
		Block block = BuiltInRegistries.BLOCK.getOptional(id)
			.orElse(null);
		if (block == null || block.defaultBlockState()
			.hasProperty(EngineCasingBlock.PAINT) || !CasingPaint.isDyed(block) || !(block instanceof EntityBlock))
			return null;
		return block;
	}

	/** Every dyed part in a chunk's saved sections. */
	public static List<Found> scan(CompoundTag chunkTag, ChunkPos chunk) {
		List<Found> out = new ArrayList<>();
		ListTag sections = chunkTag.getList("sections", Tag.TAG_COMPOUND);
		for (int s = 0; s < sections.size(); s++) {
			CompoundTag section = sections.getCompound(s);
			CompoundTag states = section.getCompound("block_states");
			ListTag palette = states.getList("palette", Tag.TAG_COMPOUND);
			Block[] blocks = new Block[palette.size()];
			CasingPaint[] paints = new CasingPaint[palette.size()];
			boolean any = false;
			for (int i = 0; i < palette.size(); i++) {
				CompoundTag entry = palette.getCompound(i);
				CompoundTag properties = entry.getCompound("Properties");
				CasingPaint paint = CasingPaint.byName(properties.getString("paint"));
				if (paint == CasingPaint.NONE || (blocks[i] = migratingBlock(entry.getString("Name"))) == null)
					continue;
				paints[i] = paint;
				any = true;
			}
			if (!any)
				continue;
			int[] cells = PackedPalette.unpack(states.getLongArray("data"), palette.size());
			if (cells == null)
				continue;
			int baseY = section.getByte("Y") << 4;
			for (int cell = 0; cell < cells.length; cell++) {
				int index = cells[cell];
				if (index >= 0 && index < paints.length && paints[index] != null)
					out.add(new Found(new BlockPos(chunk.getMinBlockX() + PackedPalette.x(cell),
						baseY + PackedPalette.y(cell), chunk.getMinBlockZ() + PackedPalette.z(cell)), blocks[index],
						paints[index]));
			}
		}
		return out;
	}

	/**
	 * Puts each dye into the block entity data at its position: into the chunk's saved entry for it if there is one (which the
	 * game has not read yet, or has only stored), else as a new entry on the chunk for a part that had no block entity before.
	 */
	public static void apply(CompoundTag chunkTag, ChunkAccess chunk, List<Found> found) {
		ListTag entities = chunkTag.getList("block_entities", Tag.TAG_COMPOUND);
		for (Found part : found) {
			CompoundTag existing = entry(entities, part.pos());
			if (existing != null) {
				if (!existing.contains(PaintedBlockEntity.KEY))
					PaintedBlockEntity.write(existing, part.paint());
				continue;
			}
			BlockEntity blank = ((EntityBlock) part.block()).newBlockEntity(part.pos(), part.block()
				.defaultBlockState());
			if (blank == null)
				continue;
			CompoundTag fresh = new CompoundTag();
			fresh.putString("id", String.valueOf(BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(blank.getType())));
			fresh.putInt("x", part.pos()
				.getX());
			fresh.putInt("y", part.pos()
				.getY());
			fresh.putInt("z", part.pos()
				.getZ());
			PaintedBlockEntity.write(fresh, part.paint());
			chunk.setBlockEntityNbt(fresh);
		}
	}

	private static CompoundTag entry(ListTag entities, BlockPos pos) {
		for (int i = 0; i < entities.size(); i++) {
			CompoundTag tag = entities.getCompound(i);
			if (tag.getInt("x") == pos.getX() && tag.getInt("y") == pos.getY() && tag.getInt("z") == pos.getZ())
				return tag;
		}
		return null;
	}
}
