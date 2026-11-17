package com.createengines.content.blueprint;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import com.createengines.engine.blueprint.Blueprint;
import com.createengines.engine.blueprint.BlueprintNames;

import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Writes a blueprint as a Create schematic: a structure file holding every block with the state it was recorded in, which
 * a Schematicannon prices through each block's own item requirement (so a cylinder comes with its piston and rod, as for
 * any other schematic of an engine). Dials are not in the file, since a schematic carries blocks and not settings: use the
 * blueprint on the finished engine to set them. Only this mod's blocks are in a blueprint, so Create's gearing is not in the
 * schematic either.
 */
public final class BlueprintSchematic {

	private BlueprintSchematic() {
	}

	/** The structure data for the blueprint, with its corner at 0, 0, 0. Null if a part does not describe a block. */
	public static CompoundTag structure(Blueprint blueprint) {
		int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
		for (Blueprint.Part part : blueprint.parts()) {
			minX = Math.min(minX, part.x());
			minY = Math.min(minY, part.y());
			minZ = Math.min(minZ, part.z());
			maxX = Math.max(maxX, part.x());
			maxY = Math.max(maxY, part.y());
			maxZ = Math.max(maxZ, part.z());
		}
		Map<BlockState, Integer> palette = new LinkedHashMap<>();
		ListTag blocks = new ListTag();
		for (Blueprint.Part part : blueprint.parts()) {
			BlockState state = BlueprintBuilder.stateOf(part);
			if (state == null)
				return null;
			int index = palette.computeIfAbsent(state, s -> palette.size());
			CompoundTag entry = new CompoundTag();
			entry.putInt("state", index);
			ListTag pos = new ListTag();
			pos.add(IntTag.valueOf(part.x() - minX));
			pos.add(IntTag.valueOf(part.y() - minY));
			pos.add(IntTag.valueOf(part.z() - minZ));
			entry.put("pos", pos);
			blocks.add(entry);
		}
		ListTag paletteTag = new ListTag();
		for (BlockState state : palette.keySet())
			paletteTag.add(NbtUtils.writeBlockState(state));
		ListTag size = new ListTag();
		size.add(IntTag.valueOf(maxX - minX + 1));
		size.add(IntTag.valueOf(maxY - minY + 1));
		size.add(IntTag.valueOf(maxZ - minZ + 1));
		CompoundTag root = new CompoundTag();
		root.put("size", size);
		root.put("palette", paletteTag);
		root.put("blocks", blocks);
		root.put("entities", new ListTag());
		root.putInt("DataVersion", SharedConstants.getCurrentVersion()
			.getDataVersion()
			.getVersion());
		return root;
	}

	/**
	 * Writes the schematic into {@code directory} (created if need be) under a name that is not taken, and returns the file's
	 * name with its extension. Returns null if the blueprint cannot be written.
	 */
	public static String write(Blueprint blueprint, Path directory) throws IOException {
		CompoundTag structure = structure(blueprint);
		if (structure == null)
			return null;
		Files.createDirectories(directory);
		String base = "engine_" + BlueprintNames.fileName(blueprint.name());
		String file = base + ".nbt";
		for (int copy = 2; Files.exists(directory.resolve(file)) && copy < 1000; copy++)
			file = base + "_" + copy + ".nbt";
		NbtIo.writeCompressed(structure, directory.resolve(file));
		return file;
	}
}
