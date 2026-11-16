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
		ServerAudit.guard(report, "models", "items", () -> itemModels(report));
		ServerAudit.guard(report, "models", "partial models", () -> partialModels(report));
		ServerAudit.guard(report, "lang", "names", () -> names(report));
		ServerAudit.guard(report, "lang", "advancements", () -> advancementText(report));
		ServerAudit.guard(report, "sound", "events", () -> sounds(report));
		ServerAudit.guard(report, "particles", "sprite sheets", () -> particles(report));
		ServerAudit.guard(report, "tab", "creative tab", () -> creativeTab(report));
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
