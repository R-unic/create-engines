package com.createengines.client;

import java.util.Map;

import com.createengines.CreateEngines;
import com.createengines.content.CasingPaint;
import com.createengines.content.Encasing;

import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.event.ModelEvent;

/**
 * Heap saving for painted parts. A block's dye is only a tint (the colour handler reads it), so no blockstate file
 * mentions it, yet each of its 17 values is a block state of its own and vanilla bakes a whole model for every state.
 * For the multipart blocks (the pipes above all, each with 64 connection shapes and three casing styles) that is a long
 * list of conditions per state, about 440 MB of settled heap in 1.3 (see {@code docs/releases/v1.3.0.md}). After baking, every
 * painted state is pointed at the baked model of the same state with no paint, and the copies are left to be collected.
 * Looks do not change: the model of a state never depended on its paint, and a multipart model reads whichever state it
 * is asked about. Client only.
 */
public final class SharedCasingModels {

	private SharedCasingModels() {
	}

	public static void onModifyBakingResult(ModelEvent.ModifyBakingResult event) {
		share(event.getModels());
	}

	/** Points every painted state at its unpainted twin's multipart model. Returns how many models were shared. */
	public static int share(Map<ModelResourceLocation, BakedModel> models) {
		int shared = 0;
		for (Block block : BuiltInRegistries.BLOCK) {
			if (!CreateEngines.MOD_ID.equals(BuiltInRegistries.BLOCK.getKey(block)
				.getNamespace()) || !block.getStateDefinition()
					.getProperties()
					.contains(Encasing.PAINT))
				continue;
			for (BlockState state : block.getStateDefinition()
				.getPossibleStates()) {
				if (state.getValue(Encasing.PAINT) == CasingPaint.NONE)
					continue;
				BakedModel twin = models.get(BlockModelShaper.stateToModelLocation(state.setValue(Encasing.PAINT, CasingPaint.NONE)));
				ModelResourceLocation key = BlockModelShaper.stateToModelLocation(state);
				BakedModel own = models.get(key);
				if (twin != null && own != twin && isMultipart(own) && isMultipart(twin)) {
					models.put(key, twin);
					shared++;
				}
			}
		}
		return shared;
	}

	private static boolean isMultipart(BakedModel model) {
		return model instanceof net.minecraft.client.resources.model.MultiPartBakedModel;
	}
}
