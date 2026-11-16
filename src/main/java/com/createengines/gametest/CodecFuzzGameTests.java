package com.createengines.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.function.Function;
import java.util.function.Supplier;

import org.joml.Vector3f;

import com.createengines.CreateEngines;
import com.createengines.advancement.AdvancementSting;
import com.createengines.content.effects.GasParticleOptions;
import com.createengines.content.effects.GlowParticleOptions;
import com.createengines.content.refinery.RefiningRecipe;
import com.createengines.registry.CERecipes;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.fluids.SimpleFluidContent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Verification track item 8. Every network codec and data codec this mod owns must round-trip what it writes, and must
 * meet truncated, mutated and garbage input by rejecting it with an ordinary exception (or a failed {@code DataResult})
 * quickly: never an {@link Error}, a hang, or a decode that claims success and then cannot be written back.
 * <p>
 * The cases are seeded, so a failure replays: the message names the codec, the case and the seed.
 */
@GameTestHolder(CreateEngines.MOD_ID)
@PrefixGameTestTemplate(false)
public class CodecFuzzGameTests {

	/** A decode that takes longer than this on a few bytes of input is a stall, whatever it was reading. */
	private static final long SLOW_NANOS = 750_000_000L;
	private static final int MUTATIONS = 400;
	private static final int GARBAGE = 400;

	private static RegistryFriendlyByteBuf buffer(RegistryAccess access) {
		return new RegistryFriendlyByteBuf(Unpooled.buffer(), access);
	}

	private static RegistryFriendlyByteBuf wrap(RegistryAccess access, byte[] bytes, int length) {
		RegistryFriendlyByteBuf buf = buffer(access);
		buf.writeBytes(bytes, 0, length);
		return buf;
	}

	private static byte[] bytesOf(ByteBuf buf) {
		byte[] bytes = new byte[buf.readableBytes()];
		buf.getBytes(buf.readerIndex(), bytes);
		return bytes;
	}

	/** What one stream codec is put through: a source of valid values and a way to compare two of them. */
	private record Subject<T>(String name, StreamCodec<RegistryFriendlyByteBuf, T> codec, Supplier<List<T>> samples) {
	}

	private static float finite(Random random, float scale) {
		return (random.nextFloat() * 2 - 1) * scale;
	}

	private static Vector3f vec(Random random) {
		return new Vector3f(random.nextFloat(), random.nextFloat(), random.nextFloat());
	}

	private static List<GasParticleOptions> gasSamples() {
		Random random = new Random(21);
		List<GasParticleOptions> out = new ArrayList<>();
		for (int i = 0; i < 40; i++)
			out.add(new GasParticleOptions(vec(random), finite(random, 4), random.nextFloat(), finite(random, 2), random.nextInt(2000) - 50));
		out.add(new GasParticleOptions(new Vector3f(), 0, 0, 0, 0));
		out.add(new GasParticleOptions(new Vector3f(1, 1, 1), Float.MAX_VALUE, 1, Float.MIN_VALUE, Integer.MAX_VALUE));
		out.add(new GasParticleOptions(new Vector3f(1, 1, 1), -Float.MAX_VALUE, -1, -3, Integer.MIN_VALUE));
		return out;
	}

	private static List<GlowParticleOptions> glowSamples() {
		Random random = new Random(22);
		List<GlowParticleOptions> out = new ArrayList<>();
		for (int i = 0; i < 40; i++)
			out.add(new GlowParticleOptions(vec(random), vec(random), finite(random, 3), random.nextFloat(), finite(random, 3),
				random.nextInt(2000) - 50, random.nextBoolean()));
		return out;
	}

	private static List<AdvancementSting.Payload> stingSamples() {
		return List.of(new AdvancementSting.Payload(true), new AdvancementSting.Payload(false));
	}

	private static List<SimpleFluidContent> canSamples() {
		return List.of(SimpleFluidContent.EMPTY, SimpleFluidContent.copyOf(new net.neoforged.neoforge.fluids.FluidStack(
			net.minecraft.world.level.material.Fluids.WATER, 1000)), SimpleFluidContent.copyOf(new net.neoforged.neoforge.fluids.FluidStack(
			net.minecraft.world.level.material.Fluids.LAVA, 1)));
	}

	private static List<Subject<?>> subjects(GameTestHelper helper) {
		List<Subject<?>> out = new ArrayList<>();
		out.add(new Subject<>("sting packet", AdvancementSting.Payload.CODEC, CodecFuzzGameTests::stingSamples));
		out.add(new Subject<>("gas particle", GasParticleOptions.STREAM_CODEC, CodecFuzzGameTests::gasSamples));
		out.add(new Subject<>("glow particle", GlowParticleOptions.STREAM_CODEC, CodecFuzzGameTests::glowSamples));
		out.add(new Subject<>("jerry can fluid", SimpleFluidContent.STREAM_CODEC, CodecFuzzGameTests::canSamples));
		StreamCodec<RegistryFriendlyByteBuf, RefiningRecipe> recipeCodec =
			CERecipes.REFINING_SERIALIZER.get().streamCodec();
		out.add(new Subject<>("refining recipe", recipeCodec, () -> helper.getLevel().getRecipeManager()
			.getAllRecipesFor(CERecipes.REFINING.get()).stream().map(RecipeHolder::value).toList()));
		return out;
	}

	/** One decode, timed, with anything that is not an ordinary exception reported. Returns the value, or null if rejected. */
	private static <T> T tryDecode(StreamCodec<RegistryFriendlyByteBuf, T> codec, RegistryFriendlyByteBuf buf, String where,
		List<String> problems) {
		long start = System.nanoTime();
		try {
			return codec.decode(buf);
		} catch (RuntimeException e) {
			return null;
		} catch (Throwable t) {
			problems.add(where + ": decode threw " + t.getClass().getName() + ", not an ordinary exception");
			return null;
		} finally {
			long took = System.nanoTime() - start;
			if (took > SLOW_NANOS)
				problems.add(where + ": decode took " + took / 1_000_000 + " ms");
		}
	}

	private static <T> void fuzz(Subject<T> subject, RegistryAccess access, List<String> problems) {
		List<T> samples = subject.samples.get();
		if (samples.isEmpty()) {
			problems.add(subject.name + ": has no valid samples to test with");
			return;
		}
		int index = 0;
		for (T sample : samples) {
			String id = subject.name + " sample " + index++;
			RegistryFriendlyByteBuf out = buffer(access);
			try {
				subject.codec.encode(out, sample);
			} catch (RuntimeException e) {
				problems.add(id + ": cannot be encoded: " + e);
				continue;
			}
			byte[] valid = bytesOf(out);

			// 1. Round trip: the same bytes come back when the decoded value is written again, and nothing is left over
			RegistryFriendlyByteBuf in = wrap(access, valid, valid.length);
			T back = tryDecode(subject.codec, in, id + " round trip", problems);
			if (back == null) {
				problems.add(id + ": its own bytes do not decode");
				continue;
			}
			if (in.isReadable())
				problems.add(id + ": decode left " + in.readableBytes() + " bytes unread");
			RegistryFriendlyByteBuf again = buffer(access);
			try {
				subject.codec.encode(again, back);
				if (!java.util.Arrays.equals(valid, bytesOf(again)))
					problems.add(id + ": does not round-trip to the same bytes");
			} catch (RuntimeException e) {
				problems.add(id + ": the decoded value cannot be encoded again: " + e);
			}

			// 2. Every truncation
			for (int cut = 0; cut < valid.length; cut++)
				tryDecode(subject.codec, wrap(access, valid, cut), id + " cut at " + cut + "/" + valid.length, problems);

			// 3. Mutations: flipped bytes, runs of 0xFF (huge lengths and ids), and appended junk
			Random random = new Random(31L * index + subject.name.hashCode());
			for (int m = 0; m < MUTATIONS / samples.size() + 5; m++) {
				byte[] mutated = java.util.Arrays.copyOf(valid, valid.length + random.nextInt(4));
				int edits = 1 + random.nextInt(4);
				for (int e = 0; e < edits && mutated.length > 0; e++) {
					int at = random.nextInt(mutated.length);
					switch (random.nextInt(3)) {
						case 0 -> mutated[at] ^= (byte) (1 << random.nextInt(8));
						case 1 -> {
							for (int k = at; k < Math.min(mutated.length, at + 1 + random.nextInt(5)); k++)
								mutated[k] = (byte) 0xFF;
						}
						default -> mutated[at] = (byte) random.nextInt(256);
					}
				}
				RegistryFriendlyByteBuf buf = wrap(access, mutated, mutated.length);
				String where = id + " mutation " + m + " (seed " + (31L * index + subject.name.hashCode()) + ")";
				T decoded = tryDecode(subject.codec, buf, where, problems);
				if (decoded != null) {
					try {
						subject.codec.encode(buffer(access), decoded);
					} catch (RuntimeException e) {
						problems.add(where + ": decoded, but the value cannot be encoded again: " + e);
					}
				}
			}
		}

		// 4. Pure garbage, and a stream that is all huge lengths
		Random random = new Random(subject.name.hashCode());
		for (int g = 0; g < GARBAGE; g++) {
			byte[] junk = new byte[random.nextInt(48)];
			random.nextBytes(junk);
			tryDecode(subject.codec, wrap(access, junk, junk.length), subject.name + " garbage " + g, problems);
		}
		byte[] huge = new byte[64];
		java.util.Arrays.fill(huge, (byte) 0xFF);
		tryDecode(subject.codec, wrap(access, huge, huge.length), subject.name + " all 0xFF", problems);
		byte[] zeros = new byte[64];
		tryDecode(subject.codec, wrap(access, zeros, zeros.length), subject.name + " all zeros", problems);
		byte[] none = new byte[0];
		tryDecode(subject.codec, wrap(access, none, 0), subject.name + " empty", problems);
	}

	private static void conclude(GameTestHelper helper, List<String> problems, String what) {
		if (problems.isEmpty()) {
			helper.succeed();
			return;
		}
		StringBuilder message = new StringBuilder(what + ": " + problems.size() + " problems:");
		for (int i = 0; i < Math.min(12, problems.size()); i++)
			message.append("\n  ").append(problems.get(i));
		if (problems.size() > 12)
			message.append("\n  and ").append(problems.size() - 12).append(" more");
		CreateEngines.LOGGER.error("{}", message);
		helper.fail(message.toString());
	}

	@GameTest(template = "empty", timeoutTicks = 1200)
	public static void everyNetworkCodecRoundTripsAndRejectsBadBytes(GameTestHelper helper) {
		List<String> problems = new ArrayList<>();
		RegistryAccess access = helper.getLevel().registryAccess();
		for (Subject<?> subject : subjects(helper))
			fuzz(subject, access, problems);
		conclude(helper, problems, "network codecs");
	}

	// ---- JSON: the codecs behind data files -------------------------------------------------------------------

	private static JsonElement randomJson(Random random, int depth) {
		return switch (random.nextInt(depth > 3 ? 5 : 8)) {
			case 0 -> JsonNull.INSTANCE;
			case 1 -> new JsonPrimitive(random.nextBoolean());
			case 2 -> new JsonPrimitive(random.nextInt(5) == 0 ? 1e300 : random.nextGaussian() * 1000);
			case 3 -> new JsonPrimitive(random.nextInt(5) == 0 ? Long.MAX_VALUE : random.nextInt(100) - 20);
			case 4 -> new JsonPrimitive(new String[] {"", "none", "heated", "superheated", "minecraft:water", "#c:ingots", "x".repeat(300),
				"create_engines:crude_oil"}[random.nextInt(8)]);
			case 5, 6 -> {
				JsonObject object = new JsonObject();
				for (int i = random.nextInt(6); i > 0; i--)
					object.add(new String[] {"feed", "product", "heat", "speed", "catalyst", "ingredient", "feed_per_item", "fluid", "amount",
						"color", "from", "to", "scale", "alpha", "lift", "lifetime", "size", "growth", "additive", "id", "count"}[random.nextInt(21)],
						randomJson(random, depth + 1));
				yield object;
			}
			default -> {
				JsonArray array = new JsonArray();
				for (int i = random.nextInt(5); i > 0; i--)
					array.add(randomJson(random, depth + 1));
				yield array;
			}
		};
	}

	private static <T> void fuzzJson(String name, Codec<T> codec, Function<Random, JsonElement> valid, List<String> problems) {
		Random random = new Random(name.hashCode());
		for (int i = 0; i < 1500; i++) {
			JsonElement json = i % 3 == 0 && valid != null ? mutate(valid.apply(random), random) : randomJson(random, 0);
			String where = name + " case " + i;
			long start = System.nanoTime();
			try {
				var result = codec.parse(JsonOps.INSTANCE, json);
				Optional<T> value = result.result();
				if (value.isPresent()) {
					try {
						codec.encodeStart(JsonOps.INSTANCE, value.get()).result();
					} catch (RuntimeException e) {
						problems.add(where + ": parsed, but the value cannot be written back: " + e);
					}
				}
			} catch (Throwable t) {
				problems.add(where + ": parse threw " + t + " for " + json);
			}
			if (System.nanoTime() - start > SLOW_NANOS)
				problems.add(where + ": parse took " + (System.nanoTime() - start) / 1_000_000 + " ms");
		}
	}

	/** Changes one thing in a valid document: drops a field, swaps a value for another type, or nests it. */
	private static JsonElement mutate(JsonElement element, Random random) {
		if (!(element instanceof JsonObject object) || object.size() == 0)
			return randomJson(random, 2);
		JsonObject copy = object.deepCopy();
		List<String> keys = new ArrayList<>(copy.keySet());
		String key = keys.get(random.nextInt(keys.size()));
		switch (random.nextInt(4)) {
			case 0 -> copy.remove(key);
			case 1 -> copy.add(key, randomJson(random, 2));
			case 2 -> copy.add(key, JsonNull.INSTANCE);
			default -> copy.add("surprise", randomJson(random, 2));
		}
		return copy;
	}

	@GameTest(template = "empty", timeoutTicks = 1200)
	public static void everyDataCodecRejectsMalformedJsonWithoutThrowing(GameTestHelper helper) {
		List<String> problems = new ArrayList<>();
		fuzzJson("gas particle json", GasParticleOptions.CODEC.codec(), random -> {
			JsonObject o = new JsonObject();
			JsonArray color = new JsonArray();
			color.add(0.5);
			color.add(0.5);
			color.add(0.5);
			o.add("color", color);
			o.addProperty("scale", 1);
			o.addProperty("alpha", 0.5);
			o.addProperty("lift", 1);
			o.addProperty("lifetime", 40);
			return o;
		}, problems);
		fuzzJson("glow particle json", GlowParticleOptions.CODEC.codec(), random -> {
			JsonObject o = new JsonObject();
			JsonArray color = new JsonArray();
			color.add(1);
			color.add(0);
			color.add(0);
			o.add("from", color);
			o.add("to", color);
			o.addProperty("size", 1);
			o.addProperty("alpha", 1);
			o.addProperty("growth", 1);
			o.addProperty("lifetime", 20);
			o.addProperty("additive", true);
			return o;
		}, problems);
		fuzzJson("refining recipe json", CERecipes.REFINING_SERIALIZER.get().codec().codec(), random -> {
			JsonObject o = new JsonObject();
			JsonObject feed = new JsonObject();
			feed.addProperty("fluid", "create_engines:crude_oil");
			feed.addProperty("amount", 100);
			o.add("feed", feed);
			JsonObject product = new JsonObject();
			product.addProperty("id", "minecraft:water");
			product.addProperty("amount", 50);
			o.add("product", product);
			o.addProperty("heat", "heated");
			o.addProperty("speed", 1.0);
			return o;
		}, problems);
		fuzzJson("jerry can fluid json", SimpleFluidContent.CODEC, null, problems);
		conclude(helper, problems, "data codecs");
	}

	/** Not a codec: the helper's own sanity, so a broken fuzz harness cannot pass for a clean mod. */
	@GameTest(template = "empty")
	public static void theFuzzerCanTellABadCodecWhenItSeesOne(GameTestHelper helper) {
		StreamCodec<RegistryFriendlyByteBuf, int[]> bad = StreamCodec.of(
			(buf, value) -> buf.writeVarInt(value.length),
			buf -> {
				int length = buf.readVarInt();
				if (length < 0 || length > 10)
					throw new AssertionError("a codec that fails with an Error on a bad length");
				return new int[length];
			});
		Subject<int[]> subject = new Subject<>("deliberately bad", bad, () -> List.<int[]>of(new int[3]));
		List<String> problems = new ArrayList<>();
		fuzz(subject, helper.getLevel().registryAccess(), problems);
		if (problems.isEmpty())
			helper.fail("The fuzzer found nothing wrong with a codec that allocates whatever length it is told");
		else
			helper.succeed();
	}
}
