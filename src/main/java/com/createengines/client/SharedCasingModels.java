package com.createengines.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import com.createengines.CreateEngines;

import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.client.resources.model.MultiPartBakedModel;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * Heap saving for the multipart parts (the pipes above all). A multipart model is the same list of conditions for every
 * state of its block: the model of a state reads whichever state it is asked about and picks the parts that state shows.
 * Yet vanilla bakes a whole copy of that list for every block state, and the pipes have thousands (64 connection shapes
 * by encased by three casing styles by 17 dyes), which cost about 440 MB of settled heap in 1.3 (see
 * {@code docs/releases/v1.3.0.md}). After baking, every state of one of this mod's multipart blocks is pointed at one baked
 * model for the block, and the copies are left to be collected. Looks do not change: nothing in a multipart model depends on
 * which state it was baked for. Client only.
 * <p>
 * When the smoke audit is running (system property {@code create_engines.smokeAudit}), a sample of the states is drawn into
 * {@link #BEFORE} first, so the audit can check afterwards that each one still draws exactly the same quads.
 */
public final class SharedCasingModels {

	/** How a sampled state drew before its model was replaced, kept only for the audit. */
	public static final Map<BlockState, int[]> BEFORE = new HashMap<>();
	/** Largest number of states sampled from one block. */
	private static final int SAMPLE = 400;

	private SharedCasingModels() {
	}

	public static void onModifyBakingResult(ModelEvent.ModifyBakingResult event) {
		share(event.getModels());
	}

	/** Points every state of each of this mod's multipart blocks at one model. Returns how many were pointed elsewhere. */
	public static int share(Map<ModelResourceLocation, BakedModel> models) {
		boolean audit = Boolean.getBoolean("create_engines.smokeAudit");
		int shared = 0;
		for (Block block : BuiltInRegistries.BLOCK) {
			if (!CreateEngines.MOD_ID.equals(BuiltInRegistries.BLOCK.getKey(block)
				.getNamespace()))
				continue;
			List<BlockState> states = new ArrayList<>(block.getStateDefinition()
				.getPossibleStates());
			BlockState standard = block.defaultBlockState();
			BakedModel canonical = models.get(BlockModelShaper.stateToModelLocation(standard));
			if (!(canonical instanceof MultiPartBakedModel))
				continue;
			int step = Math.max(1, states.size() / SAMPLE);
			for (int i = 0; i < states.size(); i++) {
				BlockState state = states.get(i);
				ModelResourceLocation key = BlockModelShaper.stateToModelLocation(state);
				BakedModel own = models.get(key);
				if (!(own instanceof MultiPartBakedModel))
					continue;
				if (audit && i % step == 0)
					BEFORE.put(state, digest(own, state));
				if (own != canonical) {
					models.put(key, canonical);
					shared++;
				}
			}
		}
		return shared;
	}

	/** One number for each face and one for the quads with no face, covering how many quads there are and what is in them. */
	public static int[] digest(BakedModel model, BlockState state) {
		RandomSource random = RandomSource.create(7);
		int[] out = new int[Direction.values().length + 1];
		for (int i = 0; i < out.length; i++) {
			@Nullable Direction face = i < Direction.values().length ? Direction.values()[i] : null;
			int hash = 1;
			for (BakedQuad quad : model.getQuads(state, face, random, ModelData.EMPTY, null))
				hash = 31 * hash + Arrays.hashCode(quad.getVertices()) * 17 + quad.getSprite()
					.contents()
					.name()
					.hashCode() + quad.getTintIndex();
			out[i] = hash;
		}
		return out;
	}
}
