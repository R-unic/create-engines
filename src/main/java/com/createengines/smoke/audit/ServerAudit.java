package com.createengines.smoke.audit;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import com.createengines.CreateEngines;
import com.createengines.smoke.SmokeReport;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * Everything about this mod's content that a running server can check without a player: that each block, item and
 * fluid is complete, that its data (loot, recipes, advancements, tags) loaded, and that every block entity survives
 * being placed in every state, ticked, saved, loaded, fed garbage and broken. Each check reports into a
 * {@link SmokeReport} rather than failing on the first problem, so one run lists everything that is wrong.
 *
 * <p>Server-only and side-neutral: nothing here touches client classes, so running it on the game-test server also
 * proves the mod's common code loads on a dedicated server.
 */
public final class ServerAudit {

	private static final String NS = CreateEngines.MOD_ID;

	/** How many states of one block the lifecycle sweep tries at most (the default always among them). */
	public static final int STATES_PER_BLOCK = 24;

	/**
	 * Blocks that are meant to have no item: the encased shaft and cogwheels are made by clicking engine casing onto a
	 * Create shaft or cogwheel, as Create's own encased parts are, and are never held.
	 */
	private static final Set<String> NO_ITEM = java.util.stream.Stream
		.of("engine_cased_shaft", "engine_cased_cogwheel", "engine_cased_large_cogwheel")
		.flatMap(name -> java.util.stream.Stream.concat(java.util.stream.Stream.of(name),
			java.util.stream.Stream.of("riveted", "louvred", "window")
				.map(plate -> name.replace("engine_", "engine_" + plate + "_"))))
		.collect(java.util.stream.Collectors.toUnmodifiableSet());

	/** Block entity save keys that legitimately differ after a load: Create sets this one flag when it loads a kinetic block. */
	private static final Set<String> VOLATILE_KEYS = Set.of("NeedsSpeedUpdate");

	private ServerAudit() {
	}

	public static boolean ours(ResourceLocation id) {
		return NS.equals(id.getNamespace());
	}

	public static List<Block> blocks() {
		List<Block> blocks = new ArrayList<>();
		for (var entry : BuiltInRegistries.BLOCK.entrySet())
			if (ours(entry.getKey()
				.location()))
				blocks.add(entry.getValue());
		return blocks;
	}

	public static List<Item> items() {
		List<Item> items = new ArrayList<>();
		for (var entry : BuiltInRegistries.ITEM.entrySet())
			if (ours(entry.getKey()
				.location()))
				items.add(entry.getValue());
		return items;
	}

	private static String id(Block block) {
		return BuiltInRegistries.BLOCK.getKey(block)
			.getPath();
	}

	/** Runs {@code section}, turning anything it throws into a failure instead of ending the audit. */
	public static void guard(SmokeReport report, String category, String name, Runnable section) {
		try {
			section.run();
		} catch (Throwable t) {
			report.fail(category, name, t.getClass()
				.getSimpleName() + ": " + t.getMessage());
		}
	}

	// Registry and data completeness

	public static void content(MinecraftServer server, SmokeReport report) {
		guard(report, "registry", "blocks", () -> blocksComplete(server, report));
		guard(report, "registry", "items", () -> itemsComplete(report));
		guard(report, "registry", "fluids", () -> fluidsComplete(report));
		guard(report, "registry", "block entities", () -> blockEntitiesComplete(report));
		guard(report, "data", "recipes", () -> recipesSound(server, report));
		guard(report, "data", "advancements", () -> advancementsSound(server, report));
	}

	private static void blocksComplete(MinecraftServer server, SmokeReport report) {
		var tables = server.reloadableRegistries();
		for (Block block : blocks()) {
			String name = id(block);
			boolean fluid = block instanceof LiquidBlock;
			if (!fluid) {
				if (!NO_ITEM.contains(name))
					report.check("registry", "block " + name + " has an item", Item.byBlock(block) != net.minecraft.world.item.Items.AIR,
						"no BlockItem; players cannot hold or place it");
				var key = block.getLootTable();
				if (key != BuiltInLootTables.EMPTY)
					report.check("data", "block " + name + " has a loot table", tables.getLootTable(key) != LootTable.EMPTY,
						"loot table " + key.location() + " did not load, so the block drops nothing");
			}
			BlockState state = block.defaultBlockState();
			boolean tool = state.is(BlockTags.MINEABLE_WITH_PICKAXE) || state.is(BlockTags.MINEABLE_WITH_AXE)
				|| state.is(BlockTags.MINEABLE_WITH_SHOVEL) || state.is(BlockTags.MINEABLE_WITH_HOE);
			if (!fluid)
				report.expect("data", "block " + name + " is mineable with a tool", tool || state.getDestroySpeed(null, BlockPos.ZERO) <= 0.0f,
					"in no mineable_with_* tag, so it breaks at hand speed and ignores tool tiers");
		}
	}

	private static void itemsComplete(SmokeReport report) {
		for (Item item : items()) {
			String name = BuiltInRegistries.ITEM.getKey(item)
				.getPath();
			ItemStack stack = item.getDefaultInstance();
			report.check("registry", "item " + name + " makes a stack", !stack.isEmpty(), "default instance is empty");
			report.check("registry", "item " + name + " stack size", stack.getMaxStackSize() >= 1 && stack.getMaxStackSize() <= 99,
				"max stack size " + stack.getMaxStackSize());
		}
	}

	private static void fluidsComplete(SmokeReport report) {
		for (var entry : BuiltInRegistries.FLUID.entrySet()) {
			ResourceLocation id = entry.getKey()
				.location();
			if (!ours(id))
				continue;
			var fluid = entry.getValue();
			report.check("registry", "fluid " + id.getPath() + " has a type", fluid.getFluidType() != null, "no FluidType");
			if (fluid.isSource(fluid.defaultFluidState()))
				report.check("registry", "fluid " + id.getPath() + " has a block form",
					!fluid.defaultFluidState()
						.createLegacyBlock()
						.isAir(),
					"source fluid makes no block, so it cannot be placed in the world");
		}
	}

	private static void blockEntitiesComplete(SmokeReport report) {
		for (Block block : blocks()) {
			if (!(block instanceof EntityBlock entityBlock))
				continue;
			String name = id(block);
			BlockEntity entity = entityBlock.newBlockEntity(BlockPos.ZERO, block.defaultBlockState());
			if (!report.check("registry", "block " + name + " makes its block entity", entity != null,
				"is an EntityBlock but newBlockEntity returned null"))
				continue;
			report.check("registry", "block " + name + " is valid for its block entity type",
				entity.getType()
					.isValid(block.defaultBlockState()),
				"the block entity type " + BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(entity.getType()) + " does not list this block");
		}
		// And the reverse: a type listing a block that does not create it would leave a state with no entity
		for (var entry : BuiltInRegistries.BLOCK_ENTITY_TYPE.entrySet())
			if (ours(entry.getKey()
				.location()))
				report.pass("registry", "block entity type " + entry.getKey()
					.location()
					.getPath());
	}

	private static void recipesSound(MinecraftServer server, SmokeReport report) {
		int ours = 0;
		for (RecipeHolder<?> holder : server.getRecipeManager()
			.getRecipes()) {
			if (!ours(holder.id()))
				continue;
			ours++;
			var recipe = holder.value();
			String name = holder.id()
				.getPath();
			boolean empty = false;
			if (recipe instanceof ShapedRecipe shaped)
				// A shaped recipe's blank cells are empty ingredients on purpose; vanilla knows whether a keyed one is
				empty = shaped.isIncomplete();
			for (Ingredient ingredient : recipe.getIngredients()) {
				// A tag with no members does not look empty: vanilla fills it with a barrier named "Empty Tag: ..." so that
				// recipe viewers show something, which is also what hides it from isEmpty and hasNoItems
				if (emptyTag(ingredient))
					empty = true;
				if (!(recipe instanceof ShapedRecipe) && ingredient.hasNoItems() && !recipe.isSpecial())
					empty = true;
			}
			report.check("data", "recipe " + name + " has no empty ingredient", !empty,
				"an ingredient matches nothing (a tag with no members, or a removed item), so it can never be crafted");
			var type = recipe.getType();
			if (type == RecipeType.CRAFTING || type == RecipeType.SMELTING || type == RecipeType.BLASTING
				|| type == RecipeType.SMOKING || type == RecipeType.CAMPFIRE_COOKING || type == RecipeType.STONECUTTING)
				report.check("data", "recipe " + name + " makes something", !recipe.getResultItem(server.registryAccess())
					.isEmpty(), "its result is empty");
		}
		report.check("data", "this mod's recipes loaded", ours > 0, "no recipe in namespace " + NS + " loaded at all");
	}

	private static boolean emptyTag(Ingredient ingredient) {
		for (ItemStack stack : ingredient.getItems())
			if (stack.is(net.minecraft.world.item.Items.BARRIER) && stack.getHoverName()
				.getString()
				.startsWith("Empty Tag"))
				return true;
		return false;
	}

	private static void advancementsSound(MinecraftServer server, SmokeReport report) {
		int ours = 0;
		for (var holder : server.getAdvancements()
			.getAllAdvancements()) {
			if (!ours(holder.id()))
				continue;
			ours++;
			var advancement = holder.value();
			String name = holder.id()
				.getPath();
			advancement.parent()
				.ifPresent(parent -> report.check("data", "advancement " + name + " has a parent",
					server.getAdvancements()
						.get(parent) != null,
					"parent " + parent + " is not loaded"));
			report.check("data", "advancement " + name + " can be earned", !advancement.criteria()
				.isEmpty(), "no criteria, so it is never granted");
		}
		report.check("data", "this mod's advancements loaded", ours > 0, "none loaded");
	}

	// Lifecycle sweep

	/** The states of {@code block} to try: its default, then an even spread across the rest, up to {@link #STATES_PER_BLOCK}. */
	public static List<BlockState> sampleStates(Block block) {
		List<BlockState> all = block.getStateDefinition()
			.getPossibleStates();
		if (all.size() <= STATES_PER_BLOCK)
			return all;
		List<BlockState> sample = new ArrayList<>();
		sample.add(block.defaultBlockState());
		double step = (double) all.size() / (STATES_PER_BLOCK - 1);
		for (int i = 0; i < STATES_PER_BLOCK - 1; i++) {
			BlockState state = all.get((int) (i * step));
			if (!sample.contains(state))
				sample.add(state);
		}
		return sample;
	}

	/**
	 * Puts every block of this mod in every sampled state into the world and treats each as a stranger would: query its
	 * shapes and signals, tick it, save it and load the copy, feed the loader empty and type-confused data, ask for its
	 * network packet, and break it and see what it drops. {@code origin} is the south-west corner of a clear area
	 * that must be loaded.
	 */
	public static void lifecycle(ServerLevel level, BlockPos origin, SmokeReport report) {
		int cell = 0;
		int perRow = 14;
		for (Block block : blocks()) {
			if (block instanceof LiquidBlock)
				continue;
			String name = id(block);
			for (BlockState state : sampleStates(block)) {
				BlockPos pos = origin.offset(cell % perRow * 3, 0, cell / perRow * 3);
				cell++;
				String label = name + " " + state.getValues()
					.entrySet()
					.stream()
					.filter(e -> !e.getValue()
						.equals(block.defaultBlockState()
							.getValue(e.getKey())))
					.map(e -> e.getKey()
						.getName() + "=" + e.getValue())
					.toList();
				guard(report, "lifecycle", label, () -> sweep(level, pos, state, label, report));
				// Clear the cell, whatever happened, so a failure cannot poison its neighbours
				level.removeBlock(pos, false);
				level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 2);
			}
		}
	}

	private static void sweep(ServerLevel level, BlockPos pos, BlockState state, String label, SmokeReport report) {
		level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 2);
		level.setBlock(pos, state, 3);
		BlockState placed = level.getBlockState(pos);
		if (placed.getBlock() != state.getBlock()) {
			report.fail("lifecycle", label, "setBlock left " + placed + " (the block replaced itself on placement)");
			return;
		}
		// The block's questions about itself must all be answerable in a real world
		placed.getShape(level, pos);
		placed.getCollisionShape(level, pos);
		placed.getVisualShape(level, pos, net.minecraft.world.phys.shapes.CollisionContext.empty());
		placed.getBlockSupportShape(level, pos);
		placed.getLightEmission(level, pos);
		if (placed.hasAnalogOutputSignal())
			placed.getAnalogOutputSignal(level, pos);
		placed.getSignal(level, pos, net.minecraft.core.Direction.NORTH);
		placed.getDestroySpeed(level, pos);
		placed.getBlock()
			.getCloneItemStack(level, pos, placed);
		BlockEntity entity = level.getBlockEntity(pos);
		boolean wantsEntity = placed.getBlock() instanceof EntityBlock;
		if (wantsEntity != (entity != null)) {
			report.fail("lifecycle", label, wantsEntity ? "an EntityBlock got no block entity when placed"
				: "a plain block has a block entity");
			return;
		}
		if (entity != null) {
			entityLifecycle(level, pos, placed, entity, label, report);
			// The tick above may have changed or removed the block; whatever stands now must still answer queries
			BlockState now = level.getBlockState(pos);
			now.getShape(level, pos);
			now.getAnalogOutputSignal(level, pos);
		}
		// Breaking it
		List<ItemStack> drops = Block.getDrops(level.getBlockState(pos), level, pos, level.getBlockEntity(pos));
		if (state.getBlock()
			.getLootTable() != BuiltInLootTables.EMPTY)
			report.expect("lifecycle", label + " drops something when broken", !drops.isEmpty(),
				"broken with an empty hand it drops nothing (its loot table may be conditional on a tool)");
		level.removeBlock(pos, false);
		report.check("lifecycle", label + " leaves no block entity behind", level.getBlockEntity(pos) == null,
			"a block entity remained after the block was removed");
		report.pass("lifecycle", label);
	}

	private static void entityLifecycle(ServerLevel level, BlockPos pos, BlockState state, BlockEntity entity, String label,
		SmokeReport report) {
		var access = level.registryAccess();
		// Tick it the way the server would, a second's worth
		@SuppressWarnings("unchecked")
		BlockEntityTicker<BlockEntity> ticker = (BlockEntityTicker<BlockEntity>) state.getTicker(level, entity.getType());
		if (ticker != null)
			for (int i = 0; i < 20; i++)
				ticker.tick(level, pos, level.getBlockState(pos), level.getBlockEntity(pos) != null ? level.getBlockEntity(pos) : entity);
		entity = level.getBlockEntity(pos);
		if (entity == null)
			return; // it broke itself while ticking, which some blocks do on purpose when unsupported
		// Saving and loading must be a round trip
		CompoundTag saved = entity.saveWithFullMetadata(access);
		BlockEntity copy = BlockEntity.loadStatic(pos, level.getBlockState(pos), saved, access);
		if (!report.check("lifecycle", label + " loads from its own save", copy != null,
			"BlockEntity.loadStatic returned null for its own saved data"))
			return;
		CompoundTag resaved = copy.saveWithFullMetadata(access);
		String changed = diff(saved, resaved);
		report.expect("lifecycle", label + " save is stable across a load", changed.equals("[]"),
			"saving, loading and saving again changed the data: " + changed);
		// Garbage in: an old version's save, a corrupt one, a hostile one
		CompoundTag empty = new CompoundTag();
		copy.loadWithComponents(empty, access);
		CompoundTag confused = new CompoundTag();
		for (String key : saved.getAllKeys())
			confused.put(key, StringTag.valueOf("not what you expected"));
		copy.loadWithComponents(confused, access);
		// Real chunk loads also hand back tags with unexpected extra keys
		CompoundTag extra = saved.copy();
		extra.putString("x", "y");
		extra.put("Items", new CompoundTag());
		copy.loadWithComponents(extra, access);
		// What the client is sent
		Tag update = entity.getUpdateTag(access);
		report.check("lifecycle", label + " update tag", update != null, "getUpdateTag returned null");
		entity.getUpdatePacket();
		entity.setChanged();
	}

	/** The first keys that differ between two tags, for a message. */
	private static String diff(CompoundTag a, CompoundTag b) {
		Set<String> keys = new TreeSet<>(a.getAllKeys());
		keys.addAll(b.getAllKeys());
		List<String> different = new ArrayList<>();
		for (String key : keys)
			if (!VOLATILE_KEYS.contains(key) && !java.util.Objects.equals(a.get(key), b.get(key)))
				different.add(key);
		return different.size() > 6 ? different.subList(0, 6) + " and more" : different.toString();
	}
}
