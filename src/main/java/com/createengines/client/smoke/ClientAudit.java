package com.createengines.client.smoke;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import com.createengines.CreateEngines;
import com.createengines.registry.CEPartialModels;
import com.createengines.smoke.SmokeReport;
import com.createengines.smoke.audit.ServerAudit;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * What a player's eyes and ears need from this mod's content, checked without looking: every block state bakes to a model
 * with no missing-texture sprite on any face, every item has a real model, every name and description has a
 * translation, every sound resolves to a recording, every particle has its sprite sheet, every Flywheel partial model
 * exists. These are the failures that ship as a purple checkerboard or a raw translation key, and each is a
 * single exact test here rather than something a screenshot might or might not show.
 */
public final class ClientAudit {

	private static final String NS = CreateEngines.MOD_ID;

	private ClientAudit() {
	}

	public static void run(SmokeReport report) {
		ServerAudit.guard(report, "models", "blocks", () -> blockModels(report));
		ServerAudit.guard(report, "models", "painted states share their model", () -> paintedModels(report));
		ServerAudit.guard(report, "models", "items", () -> itemModels(report));
		ServerAudit.guard(report, "models", "partial models", () -> partialModels(report));
		ServerAudit.guard(report, "lang", "names", () -> names(report));
		ServerAudit.guard(report, "lang", "advancements", () -> advancementText(report));
		ServerAudit.guard(report, "sound", "events", () -> sounds(report));
		ServerAudit.guard(report, "particles", "sprite sheets", () -> particles(report));
		ServerAudit.guard(report, "tab", "creative tab", () -> creativeTab(report));
		ServerAudit.guard(report, "compat", "viewer pages", () -> viewerPages(report));
		ServerAudit.guard(report, "gui", "blueprint sheet", () -> {
			try {
				blueprintSheet(report);
			} catch (com.createengines.engine.blueprint.BlueprintException e) {
				throw new IllegalStateException(e);
			}
		});
	}

	/** The fuel and part pages the recipe viewers show, whether or not a viewer is installed: every shipped fuel has one. */
	private static void viewerPages(SmokeReport report) {
		var fuels = com.createengines.client.compat.ViewerEntries.fuels();
		for (var fluid : List.of(com.createengines.registry.CEFluids.PLANT_OIL, com.createengines.registry.CEFluids.DIESEL,
			com.createengines.registry.CEFluids.GASOLINE, com.createengines.registry.CEFluids.ETHANOL))
			report.check("compat", "the fuel page list holds " + fluid.getId()
				.getPath(), fuels.stream()
					.anyMatch(fuel -> fuel.stack()
						.getFluid() == fluid.get()
							.getSource()),
				"no fuel page for it");
		for (var fuel : fuels)
			report.check("compat", "the page of " + BuiltInRegistries.FLUID.getKey(fuel.stack()
				.getFluid())
				.getPath() + " has text", !com.createengines.client.compat.ViewerEntries.fuelLines(fuel)
					.isEmpty(),
				"an empty fuel page");
	}

	/** Draws the drafting sheet for a small engine with a plate, a full-size one with none, and a damaged one. */
	private static void blueprintSheet(SmokeReport report) throws com.createengines.engine.blueprint.BlueprintException {
		Minecraft minecraft = Minecraft.getInstance();
		List<com.createengines.engine.blueprint.Blueprint.Part> small = new ArrayList<>();
		small.add(new com.createengines.engine.blueprint.Blueprint.Part(0, 0, 0, NS + ":crankshaft", java.util.Map.of("axis", "x"),
			java.util.Map.of()));
		small.add(new com.createengines.engine.blueprint.Blueprint.Part(1, 0, 0, NS + ":crank_throw", java.util.Map.of("axis", "x"),
			java.util.Map.of("throw_angle", 3)));
		small.add(new com.createengines.engine.blueprint.Blueprint.Part(1, 1, 0, NS + ":cylinder", java.util.Map.of(), java.util.Map.of()));
		small.add(new com.createengines.engine.blueprint.Blueprint.Part(1, 2, 0, NS + ":cylinder_head", java.util.Map.of(),
			java.util.Map.of("fuel_metering", 12)));
		List<com.createengines.engine.blueprint.Blueprint.Part> large = new ArrayList<>();
		for (int i = 0; i < com.createengines.engine.blueprint.Blueprint.Limits.MAX_PARTS; i++)
			large.add(new com.createengines.engine.blueprint.Blueprint.Part(i % 64 - 32, i / 64 % 64 - 32, i / 4096 - 0,
				i % 3 == 0 ? NS + ":cylinder" : i % 3 == 1 ? NS + ":exhaust_manifold" : "other_mod:strange_block"));
		var plated = new com.createengines.engine.blueprint.Blueprint("Audit", "auditor", small,
			new com.createengines.engine.sim.PlateFigures(88.8f, 120, 4321.5f, 240));
		var crowded = new com.createengines.engine.blueprint.Blueprint("Crowd", "", large);
		for (var blueprint : List.of(plated, crowded)) {
			String text = com.createengines.engine.blueprint.BlueprintCodec.encode(blueprint);
			drawSheet(minecraft, new com.createengines.content.blueprint.RecordedBlueprint(text, blueprint.name(),
				blueprint.parts().size()));
			report.check("gui", "the blueprint sheet draws " + blueprint.name(), true, "");
		}
		drawSheet(minecraft, new com.createengines.content.blueprint.RecordedBlueprint("CEBP2:damaged", "Broken", 1));
		report.check("gui", "the blueprint sheet draws a damaged blueprint", true, "");
		drawScreen(minecraft, com.createengines.client.BlueprintImportScreen.create());
		report.check("gui", "the blueprint import dialog draws", true, "");
	}

	private static void drawSheet(Minecraft minecraft, com.createengines.content.blueprint.RecordedBlueprint recorded) {
		drawScreen(minecraft, com.createengines.client.BlueprintScreen.create(recorded));
	}

	private static void drawScreen(Minecraft minecraft, net.minecraft.client.gui.screens.Screen sheet) {
		sheet.init(minecraft, 427, 240);
		net.minecraft.client.gui.GuiGraphics graphics = new net.minecraft.client.gui.GuiGraphics(minecraft,
			minecraft.renderBuffers()
				.bufferSource());
		sheet.render(graphics, 0, 0, 0f);
		graphics.flush();
	}

	private static boolean missing(net.minecraft.client.renderer.texture.TextureAtlasSprite sprite) {
		return sprite == null || sprite.contents()
			.name()
			.equals(MissingTextureAtlasSprite.getLocation());
	}

	/** The reasons a baked model would draw a purple checkerboard, or nothing, on some face. */
	private static List<String> modelProblems(BakedModel model, BlockState state) {
		List<String> problems = new ArrayList<>();
		if (model == Minecraft.getInstance()
			.getModelManager()
			.getMissingModel()) {
			problems.add("is the missing model");
			return problems;
		}
		RandomSource random = RandomSource.create(42);
		Set<String> reported = new HashSet<>();
		List<Direction> faces = new ArrayList<>(List.of(Direction.values()));
		faces.add(null);
		for (Direction face : faces)
			for (var quad : model.getQuads(state, face, random, ModelData.EMPTY, null))
				if (missing(quad.getSprite()) && reported.add("quad"))
					problems.add("has a face with the missing texture (" + (face == null ? "unculled" : face) + ")");
		if (missing(model.getParticleIcon(ModelData.EMPTY)))
			problems.add("has the missing texture as its break-particle sprite");
		return problems;
	}

	private static void blockModels(SmokeReport report) {
		Minecraft minecraft = Minecraft.getInstance();
		var dispatcher = minecraft.getBlockRenderer();
		int invisible = 0;
		for (Block block : ServerAudit.blocks()) {
			// Fluids are drawn by the fluid renderer from their FluidType, not from a baked model
			if (block instanceof net.minecraft.world.level.block.LiquidBlock)
				continue;
			String name = BuiltInRegistries.BLOCK.getKey(block)
				.getPath();
			List<BlockState> states = block.getStateDefinition()
				.getPossibleStates();
			boolean allInvisible = true;
			Set<String> seen = new TreeSet<>();
			for (int i = 0; i < states.size(); i += Math.max(1, states.size() / 400)) {
				BlockState state = states.get(i);
				if (state.getRenderShape() == RenderShape.INVISIBLE)
					continue;
				allInvisible = false;
				for (String problem : modelProblems(dispatcher.getBlockModel(state), state))
					if (seen.add(problem))
						report.fail("models", "block " + name, "state " + state + " " + problem);
			}
			if (seen.isEmpty() && !allInvisible)
				report.pass("models", "block " + name);
			if (allInvisible) {
				invisible++;
				// A block that draws no model must draw itself some other way, or it is an invisible block
				boolean drawn = block instanceof EntityBlock entityBlock
					&& hasRenderer(entityBlock.newBlockEntity(net.minecraft.core.BlockPos.ZERO, block.defaultBlockState()));
				report.check("models", "invisible block " + name + " is drawn by a renderer or a visual", drawn,
					"its render shape is INVISIBLE and it has no block entity renderer or Flywheel visual: players would see nothing");
			}
		}
		report.metric("blocks with no baked model (drawn by renderers)", invisible, "");
	}

	/**
	 * Painted states of the multipart parts (the pipes above all) must point at the baked model of the same state unpainted,
	 * which is what keeps about 440 MB of heap from the 17 dye values (see {@link com.createengines.client.SharedCasingModels}),
	 * and must still draw exactly what their own model would have.
	 */
	private static void paintedModels(SmokeReport report) {
		var dispatcher = Minecraft.getInstance()
			.getBlockRenderer();
		int multipart = 0, notShared = 0, differ = 0;
		String example = "";
		RandomSource random = RandomSource.create(7);
		for (Block block : ServerAudit.blocks()) {
			if (!block.getStateDefinition()
				.getProperties()
				.contains(com.createengines.content.Encasing.PAINT))
				continue;
			List<BlockState> states = block.getStateDefinition()
				.getPossibleStates();
			for (int i = 0; i < states.size(); i += Math.max(1, states.size() / 300)) {
				BlockState painted = states.get(i);
				BakedModel model = dispatcher.getBlockModel(painted);
				if (!(model instanceof net.minecraft.client.resources.model.MultiPartBakedModel))
					continue;
				multipart++;
				BlockState bare = painted.setValue(com.createengines.content.Encasing.PAINT, com.createengines.content.CasingPaint.NONE);
				if (dispatcher.getBlockModel(bare) != model) {
					notShared++;
					example = painted.toString();
				}
				for (Direction face : Direction.values())
					if (model.getQuads(painted, face, random, ModelData.EMPTY, null)
						.size() != dispatcher.getBlockModel(bare)
							.getQuads(bare, face, random, ModelData.EMPTY, null)
							.size()) {
						differ++;
						example = painted.toString();
					}
			}
		}
		report.metric("painted multipart states checked", multipart, "");
		report.check("models", "painted multipart states share the unpainted baked model", multipart > 0 && notShared == 0,
			notShared + " of " + multipart + " painted states still hold their own copy, e.g. " + example);
		report.check("models", "a painted state draws what its unpainted twin draws", differ == 0,
			differ + " faces differ, e.g. " + example);
	}

	private static boolean hasRenderer(net.minecraft.world.level.block.entity.BlockEntity entity) {
		if (entity == null)
			return false;
		if (Minecraft.getInstance()
			.getBlockEntityRenderDispatcher()
			.getRenderer(entity) != null)
			return true;
		return dev.engine_room.flywheel.api.visualization.VisualizerRegistry.getVisualizer(entity.getType()) != null;
	}

	private static void itemModels(SmokeReport report) {
		Minecraft minecraft = Minecraft.getInstance();
		var missingModel = minecraft.getModelManager()
			.getMissingModel();
		for (Item item : ServerAudit.items()) {
			String name = BuiltInRegistries.ITEM.getKey(item)
				.getPath();
			ItemStack stack = item.getDefaultInstance();
			BakedModel model = minecraft.getItemRenderer()
				.getModel(stack, minecraft.level, null, 0);
			if (model == missingModel) {
				report.fail("models", "item " + name, "is the missing model");
				continue;
			}
			List<String> problems = new ArrayList<>();
			RandomSource random = RandomSource.create(7);
			List<Direction> faces = new ArrayList<>(List.of(Direction.values()));
			faces.add(null);
			for (Direction face : faces)
				for (var quad : model.getQuads(null, face, random, ModelData.EMPTY, null))
					if (missing(quad.getSprite()) && !problems.contains("quad"))
						problems.add("quad");
			if (problems.isEmpty())
				report.pass("models", "item " + name);
			else
				report.fail("models", "item " + name, "has a face with the missing texture");
		}
	}

	private static void partialModels(SmokeReport report) {
		var missingModel = Minecraft.getInstance()
			.getModelManager()
			.getMissingModel();
		int found = 0;
		for (Field field : CEPartialModels.class.getDeclaredFields()) {
			if (!Modifier.isStatic(field.getModifiers()) || field.getType() != PartialModel.class)
				continue;
			found++;
			try {
				PartialModel partial = (PartialModel) field.get(null);
				BakedModel model = partial.get();
				report.check("models", "partial model " + field.getName(), model != null && model != missingModel,
					"Flywheel cannot find " + partial.modelLocation() + ", so the part would not be drawn");
			} catch (ReflectiveOperationException | RuntimeException e) {
				report.fail("models", "partial model " + field.getName(), e.toString());
			}
		}
		report.check("models", "partial models were found", found > 0, "CEPartialModels declares no PartialModel fields");
	}

	private static void need(SmokeReport report, String what, String key) {
		report.check("lang", what + " has a translation", Language.getInstance()
			.has(key), "no entry for '" + key + "' in en_us.json: players would see the raw key");
	}

	private static void names(SmokeReport report) {
		for (Block block : ServerAudit.blocks())
			if (!(block instanceof net.minecraft.world.level.block.LiquidBlock))
				need(report, "block " + BuiltInRegistries.BLOCK.getKey(block)
					.getPath(), block.getDescriptionId());
		for (Item item : ServerAudit.items())
			need(report, "item " + BuiltInRegistries.ITEM.getKey(item)
				.getPath(), item.getDescriptionId());
		for (var entry : BuiltInRegistries.FLUID.entrySet())
			if (ServerAudit.ours(entry.getKey()
				.location()) && entry.getValue()
					.isSource(entry.getValue()
						.defaultFluidState()))
				need(report, "fluid " + entry.getKey()
					.location()
					.getPath(), entry.getValue()
						.getFluidType()
						.getDescriptionId());
		need(report, "creative tab", "itemGroup." + NS + ".main");
		for (var refusal : com.createengines.engine.blueprint.BlueprintException.Refusal.values())
			need(report, "blueprint refusal " + refusal, NS + ".blueprint.refusal." + refusal.name()
				.toLowerCase(java.util.Locale.ROOT));
		// The recipe viewer pages for refining routes (JEI and EMI), whether or not a viewer is installed in this run
		for (String key : List.of(NS + ".recipe.refining.title", NS + ".recipe.refining.speed", NS + ".recipe.refining.ratio",
			NS + ".recipe.refining.catalyst", NS + ".recipe.refining.catalyst.tooltip", "emi.category." + NS + ".refining",
			NS + ".recipe.fuels.title", NS + ".recipe.parts.title", NS + ".recipe.fuel.boost.takes", NS + ".recipe.fuel.boost.limited",
			NS + ".recipe.part.install", NS + ".recipe.part.precision", NS + ".recipe.part.fits", "emi.category." + NS + ".fuels",
			"emi.category." + NS + ".parts"))
			need(report, "recipe viewer wording " + key, key);
	}

	private static void advancementText(SmokeReport report) {
		var server = Minecraft.getInstance()
			.getSingleplayerServer();
		if (server == null) {
			report.skip("lang", "advancement text", "no integrated server");
			return;
		}
		for (var holder : server.getAdvancements()
			.getAllAdvancements()) {
			if (!ServerAudit.ours(holder.id()) || holder.value()
				.display()
				.isEmpty())
				continue;
			var display = holder.value()
				.display()
				.get();
			for (Component text : List.of(display.getTitle(), display.getDescription()))
				if (text.getContents() instanceof TranslatableContents translatable)
					report.check("lang", "advancement " + holder.id()
						.getPath() + " text " + translatable.getKey(), Language.getInstance()
							.has(translatable.getKey()),
						"no translation for '" + translatable.getKey() + "'");
		}
	}

	private static void sounds(SmokeReport report) {
		Minecraft minecraft = Minecraft.getInstance();
		int checked = 0;
		for (var id : BuiltInRegistries.SOUND_EVENT.keySet()) {
			if (!ServerAudit.ours(id))
				continue;
			checked++;
			var weighed = minecraft.getSoundManager()
				.getSoundEvent(id);
			if (!report.check("sound", "event " + id.getPath() + " is in sounds.json", weighed != null,
				"registered but missing from sounds.json: it would be silent"))
				continue;
			var sound = weighed.getSound(RandomSource.create());
			boolean real = sound != net.minecraft.client.sounds.SoundManager.EMPTY_SOUND
				&& sound != net.minecraft.client.sounds.SoundManager.INTENTIONALLY_EMPTY_SOUND;
			if (!report.check("sound", "event " + id.getPath() + " resolves to a recording", real, "resolves to no sound"))
				continue;
			report.check("sound", "event " + id.getPath() + " recording exists", minecraft.getResourceManager()
				.getResource(sound.getPath())
				.isPresent(), "recording " + sound.getPath() + " is not in the resource packs");
			Component subtitle = weighed.getSubtitle();
			if (subtitle != null && subtitle.getContents() instanceof TranslatableContents translatable)
				report.check("sound", "event " + id.getPath() + " subtitle", Language.getInstance()
					.has(translatable.getKey()), "subtitle key '" + translatable.getKey() + "' has no translation");
		}
		report.check("sound", "sound events were found", checked > 0, "no sound events in namespace " + NS);
	}

	private static void particles(SmokeReport report) {
		Minecraft minecraft = Minecraft.getInstance();
		for (var id : BuiltInRegistries.PARTICLE_TYPE.keySet()) {
			if (!ServerAudit.ours(id))
				continue;
			ResourceLocation json = ResourceLocation.fromNamespaceAndPath(id.getNamespace(), "particles/" + id.getPath() + ".json");
			var resource = minecraft.getResourceManager()
				.getResource(json);
			if (!report.check("particles", id.getPath() + " has a particle description", resource.isPresent(),
				"no " + json + ": its provider's sprite set would be empty and spawning it would crash"))
				continue;
			try (var reader = resource.get()
				.openAsReader()) {
				JsonObject object = JsonParser.parseReader(reader)
					.getAsJsonObject();
				for (var texture : object.getAsJsonArray("textures")) {
					ResourceLocation location = ResourceLocation.parse(texture.getAsString());
					ResourceLocation png = ResourceLocation.fromNamespaceAndPath(location.getNamespace(),
						"textures/particle/" + location.getPath() + ".png");
					report.check("particles", id.getPath() + " texture " + location, minecraft.getResourceManager()
						.getResource(png)
						.isPresent(), "missing " + png);
				}
			} catch (Exception e) {
				report.fail("particles", id.getPath(), "could not read its description: " + e);
			}
		}
	}

	private static void creativeTab(SmokeReport report) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null || minecraft.player == null) {
			report.skip("tab", "creative tab contents", "not in a world");
			return;
		}
		CreativeModeTabs.tryRebuildTabContents(minecraft.player.connection.enabledFeatures(), true,
			minecraft.level.registryAccess());
		var shown = CreateEnginesTab.items();
		for (Item item : ServerAudit.items())
			report.check("tab", "item " + BuiltInRegistries.ITEM.getKey(item)
				.getPath() + " is in the creative tab", shown.contains(item),
				"players in creative mode cannot find it");
	}
}
