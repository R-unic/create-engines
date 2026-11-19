package com.createengines.gametest;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import com.createengines.CreateEngines;
import com.createengines.content.CasingPaint;
import com.createengines.content.Encasing;
import com.createengines.content.PaintedBlockEntity;
import com.createengines.registry.CEBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The casing dye moved from the block state to the block entity in 2.0, and a world saved before that has it in the block
 * state. These tests save a real chunk, rewrite it to look as 1.x wrote it (the dye as a {@code paint} property of the saved
 * state, none in the block entity data, and no block entity at all for a manifold), and load it back through the game's own
 * chunk reader.
 */
@GameTestHolder(CreateEngines.MOD_ID)
@PrefixGameTestTemplate(false)
public class PaintMigrationGameTests {

	/** The dyed parts: where each is, and what its casing is dyed. Each block is used once, so each has its own palette entry. */
	private static Map<BlockPos, CasingPaint> place(GameTestHelper helper) {
		Map<BlockPos, BlockState> parts = new LinkedHashMap<>();
		var clad = Encasing.ENCASED;
		parts.put(new BlockPos(1, 2, 1), CEBlocks.CYLINDER.getDefaultState()
			.setValue(clad, true));
		parts.put(new BlockPos(3, 2, 1), TestEngine.axisX(CEBlocks.CRANKSHAFT.getDefaultState())
			.setValue(clad, true));
		parts.put(new BlockPos(5, 2, 1), TestEngine.axisX(CEBlocks.CAMSHAFT.getDefaultState())
			.setValue(clad, true));
		parts.put(new BlockPos(7, 2, 1), CEBlocks.INTAKE_MANIFOLD.getDefaultState()
			.setValue(clad, true));
		parts.put(new BlockPos(1, 2, 3), TestEngine.axisX(CEBlocks.ENGINE_CASED_SHAFT.getDefaultState()));
		parts.put(new BlockPos(3, 2, 3), TestEngine.axisX(CEBlocks.ENGINE_CASED_COGWHEEL.getDefaultState()));
		parts.put(new BlockPos(5, 2, 3), TestEngine.axisX(CEBlocks.ENGINE_CASED_LARGE_COGWHEEL.getDefaultState()));
		CasingPaint[] dyes = { CasingPaint.RED, CasingPaint.BLUE, CasingPaint.LIME, CasingPaint.YELLOW, CasingPaint.PURPLE,
			CasingPaint.ORANGE, CasingPaint.CYAN };
		Map<BlockPos, CasingPaint> out = new LinkedHashMap<>();
		int i = 0;
		for (var part : parts.entrySet()) {
			helper.setBlock(part.getKey(), part.getValue());
			((PaintedBlockEntity) helper.getBlockEntity(part.getKey())).setCasingPaint(dyes[i]);
			out.put(part.getKey(), dyes[i++]);
		}
		return out;
	}

	/** What the chunk would be on disk had 1.x written it: the dye in the saved state, not in the block entity. */
	private static void makeItOneDotX(CompoundTag chunk, Map<String, CasingPaint> dyeByBlock) {
		for (Tag section : chunk.getList("sections", Tag.TAG_COMPOUND))
			for (Tag entry : ((CompoundTag) section).getCompound("block_states")
				.getList("palette", Tag.TAG_COMPOUND)) {
				CompoundTag state = (CompoundTag) entry;
				CasingPaint dye = dyeByBlock.get(state.getString("Name"));
				if (dye == null)
					continue;
				CompoundTag properties = state.contains("Properties") ? state.getCompound("Properties") : new CompoundTag();
				properties.putString("paint", dye.getSerializedName());
				state.put("Properties", properties);
			}
		ListTag kept = new ListTag();
		for (Tag entry : chunk.getList("block_entities", Tag.TAG_COMPOUND)) {
			CompoundTag be = (CompoundTag) entry;
			// 1.x had no block entity on a manifold, and none of these held a dye
			if (be.getString("id")
				.contains("manifold"))
				continue;
			be.remove(PaintedBlockEntity.KEY);
			kept.add(be);
		}
		chunk.put("block_entities", kept);
	}

	private static Map<String, CasingPaint> dyeByBlock(GameTestHelper helper, Map<BlockPos, CasingPaint> placed) {
		Map<String, CasingPaint> out = new LinkedHashMap<>();
		for (var part : placed.entrySet())
			out.put(BuiltInRegistries.BLOCK.getKey(helper.getBlockState(part.getKey())
				.getBlock())
				.toString(), part.getValue());
		return out;
	}

	private static Set<ChunkPos> chunksOf(GameTestHelper helper, Iterable<BlockPos> positions) {
		Set<ChunkPos> out = new LinkedHashSet<>();
		for (BlockPos pos : positions)
			out.add(new ChunkPos(helper.absolutePos(pos)));
		return out;
	}

	/** Reads a chunk tag back the way the game does when it loads a chunk from disk, and finishes loading its block entities. */
	private static LevelChunk load(ServerLevel level, ChunkPos pos, CompoundTag tag) {
		LevelChunk chunk = ((ImposterProtoChunk) ChunkSerializer.read(level, level.getPoiManager(),
			new net.minecraft.world.level.chunk.storage.RegionStorageInfo("test", level.dimension(), "chunk"), pos, tag)).getWrapped();
		chunk.runPostLoad();
		return chunk;
	}

	@GameTest(template = "empty_large", timeoutTicks = 100)
	public static void aOneDotXChunkKeepsTheDyeOfEveryPartThatHeldItAsState(GameTestHelper helper) {
		Map<BlockPos, CasingPaint> placed = place(helper);
		ServerLevel level = helper.getLevel();
		Map<String, CasingPaint> byBlock = dyeByBlock(helper, placed);
		for (ChunkPos chunkPos : chunksOf(helper, placed.keySet())) {
			CompoundTag tag = ChunkSerializer.write(level, level.getChunk(chunkPos.x, chunkPos.z));
			makeItOneDotX(tag, byBlock);
			LevelChunk loaded = load(level, chunkPos, tag);
			for (var part : placed.entrySet()) {
				BlockPos abs = helper.absolutePos(part.getKey());
				if (!new ChunkPos(abs).equals(chunkPos))
					continue;
				BlockEntity be = loaded.getBlockEntity(abs, LevelChunk.EntityCreationType.IMMEDIATE);
				helper.assertTrue(be instanceof PaintedBlockEntity, "A part has a block entity that holds its dye at "
					+ part.getKey() + ", got " + be);
				helper.assertTrue(((PaintedBlockEntity) be).casingPaint() == part.getValue(), "The " + part.getValue()
					+ " dye of the part at " + part.getKey() + " came across, got " + ((PaintedBlockEntity) be).casingPaint());
				helper.assertTrue(!loaded.getBlockState(abs)
					.hasProperty(com.createengines.content.EngineCasingBlock.PAINT),
					"The part no longer has the dye as state at " + part.getKey());
			}
		}
		helper.succeed();
	}

	@GameTest(template = "empty_large", timeoutTicks = 100)
	public static void aDyeAlreadyInTheBlockEntityIsNotOverwrittenByTheOldState(GameTestHelper helper) {
		BlockPos pos = new BlockPos(2, 2, 2);
		helper.setBlock(pos, CEBlocks.CYLINDER.getDefaultState()
			.setValue(Encasing.ENCASED, true));
		((PaintedBlockEntity) helper.getBlockEntity(pos)).setCasingPaint(CasingPaint.BLUE);
		ServerLevel level = helper.getLevel();
		BlockPos abs = helper.absolutePos(pos);
		ChunkPos chunkPos = new ChunkPos(abs);
		CompoundTag tag = ChunkSerializer.write(level, level.getChunk(chunkPos.x, chunkPos.z));
		// The saved state says red, the block entity says blue (a chunk saved once already after the move): blue stands
		makeItOneDotX(tag, Map.of(BuiltInRegistries.BLOCK.getKey(CEBlocks.CYLINDER.get())
			.toString(), CasingPaint.RED));
		for (Tag entry : tag.getList("block_entities", Tag.TAG_COMPOUND))
			if (((CompoundTag) entry).getInt("x") == abs.getX() && ((CompoundTag) entry).getInt("y") == abs.getY()
				&& ((CompoundTag) entry).getInt("z") == abs.getZ())
				((CompoundTag) entry).putString(PaintedBlockEntity.KEY, CasingPaint.BLUE.getSerializedName());
		LevelChunk loaded = load(level, chunkPos, tag);
		helper.assertTrue(((PaintedBlockEntity) loaded.getBlockEntity(abs, LevelChunk.EntityCreationType.IMMEDIATE))
			.casingPaint() == CasingPaint.BLUE, "The dye the block entity already holds is kept");
		helper.succeed();
	}

	@GameTest(template = "empty_large", timeoutTicks = 100)
	public static void anUndyedPartAndAnUnknownDyeMigrateToNothing(GameTestHelper helper) {
		BlockPos pos = new BlockPos(2, 2, 2);
		helper.setBlock(pos, CEBlocks.CYLINDER.getDefaultState()
			.setValue(Encasing.ENCASED, true));
		ServerLevel level = helper.getLevel();
		BlockPos abs = helper.absolutePos(pos);
		ChunkPos chunkPos = new ChunkPos(abs);
		String cylinder = BuiltInRegistries.BLOCK.getKey(CEBlocks.CYLINDER.get())
			.toString();
		for (String saved : new String[] { "none", "chartreuse", "" }) {
			CompoundTag tag = ChunkSerializer.write(level, level.getChunk(chunkPos.x, chunkPos.z));
			for (Tag section : tag.getList("sections", Tag.TAG_COMPOUND))
				for (Tag entry : ((CompoundTag) section).getCompound("block_states")
					.getList("palette", Tag.TAG_COMPOUND))
					if (((CompoundTag) entry).getString("Name")
						.equals(cylinder))
						((CompoundTag) entry).getCompound("Properties")
							.putString("paint", saved);
			LevelChunk loaded = load(level, chunkPos, tag);
			helper.assertTrue(((PaintedBlockEntity) loaded.getBlockEntity(abs, LevelChunk.EntityCreationType.IMMEDIATE))
				.casingPaint() == CasingPaint.NONE, "A saved dye of \"" + saved + "\" leaves the part undyed");
		}
		helper.succeed();
	}

	@GameTest(template = "empty_large", timeoutTicks = 100)
	public static void aDamagedSectionLoadsWithoutTheDyeInsteadOfFailing(GameTestHelper helper) {
		BlockPos pos = new BlockPos(2, 2, 2);
		helper.setBlock(pos, CEBlocks.CYLINDER.getDefaultState()
			.setValue(Encasing.ENCASED, true));
		ServerLevel level = helper.getLevel();
		BlockPos abs = helper.absolutePos(pos);
		ChunkPos chunkPos = new ChunkPos(abs);
		CompoundTag tag = ChunkSerializer.write(level, level.getChunk(chunkPos.x, chunkPos.z));
		makeItOneDotX(tag, Map.of(BuiltInRegistries.BLOCK.getKey(CEBlocks.CYLINDER.get())
			.toString(), CasingPaint.RED));
		// A palette entry with the dye, and a data array that is the wrong length for the palette: the migration must not throw
		for (Tag section : tag.getList("sections", Tag.TAG_COMPOUND)) {
			CompoundTag states = ((CompoundTag) section).getCompound("block_states");
			if (states.contains("data"))
				states.putLongArray("data", new long[] { 1L, 2L, 3L });
		}
		boolean threw = false;
		try {
			com.createengines.content.PaintMigration.scan(tag, chunkPos);
		} catch (RuntimeException e) {
			threw = true;
		}
		helper.assertFalse(threw, "A section with damaged data is skipped, not thrown on");
		helper.succeed();
	}

	@GameTest(template = "empty_large", timeoutTicks = 100)
	public static void aDyeSurvivesSavingAndLoadingAndReachesTheClient(GameTestHelper helper) {
		Map<BlockPos, CasingPaint> placed = place(helper);
		ServerLevel level = helper.getLevel();
		for (var part : placed.entrySet()) {
			BlockEntity be = helper.getBlockEntity(part.getKey());
			CompoundTag saved = be.saveWithFullMetadata(level.registryAccess());
			helper.assertTrue(CasingPaint.byName(saved.getString(PaintedBlockEntity.KEY)) == part.getValue(),
				"The dye is saved with the block entity at " + part.getKey());
			CompoundTag sent = be.getUpdateTag(level.registryAccess());
			helper.assertTrue(CasingPaint.byName(sent.getString(PaintedBlockEntity.KEY)) == part.getValue(),
				"The dye goes to the client with the block entity at " + part.getKey());
		}
		// An undyed part saves exactly what it always did: no dye key at all
		BlockPos bare = new BlockPos(7, 2, 3);
		helper.setBlock(bare, CEBlocks.CYLINDER.getDefaultState()
			.setValue(Encasing.ENCASED, true));
		helper.assertFalse(helper.getBlockEntity(bare)
			.saveWithFullMetadata(level.registryAccess())
			.contains(PaintedBlockEntity.KEY), "An undyed part writes no dye");
		helper.succeed();
	}

	@GameTest(template = "empty_large", timeoutTicks = 100)
	public static void takingTheCasingOffWashesTheDyeOut(GameTestHelper helper) {
		Map<BlockPos, CasingPaint> placed = place(helper);
		// The parts casing is clad onto (a cased shaft or cog is always clad)
		for (var part : placed.entrySet()) {
			BlockState state = helper.getBlockState(part.getKey());
			if (!state.hasProperty(Encasing.ENCASED))
				continue;
			var wrench = new net.minecraft.world.item.context.UseOnContext(helper.getLevel(),
				helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL), net.minecraft.world.InteractionHand.MAIN_HAND,
				new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STICK),
				new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(helper.absolutePos(part.getKey())),
					net.minecraft.core.Direction.UP, helper.absolutePos(part.getKey()), false));
			Encasing.uncase(state, wrench);
			helper.assertTrue(PaintedBlockEntity.at(helper.getLevel(), helper.absolutePos(part.getKey())) == CasingPaint.NONE,
				"The dye is gone with the casing at " + part.getKey());
			helper.assertFalse(helper.getBlockState(part.getKey())
				.getValue(Encasing.ENCASED), "and the part is bare");
		}
		helper.succeed();
	}
}
