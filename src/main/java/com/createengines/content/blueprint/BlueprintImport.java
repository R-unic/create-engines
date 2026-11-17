package com.createengines.content.blueprint;

import org.jetbrains.annotations.Nullable;

import com.createengines.CreateEngines;
import com.createengines.engine.blueprint.Blueprint;
import com.createengines.engine.blueprint.BlueprintCodec;
import com.createengines.engine.blueprint.BlueprintException;
import com.createengines.engine.blueprint.BlueprintRules;
import com.createengines.registry.CEComponents;
import com.createengines.registry.CELang;

import net.minecraft.ChatFormatting;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Importing a pasted blueprint string into the blueprint in a player's hand. The string comes from a client and is never
 * trusted: it is checked on the server for size, format and every rule in {@link BlueprintRules} (this mod's blocks only,
 * every state property and dial real and in range) before anything is stored, and what is stored is the server's own
 * re-encoding of it, not the text that arrived. A refused string changes nothing and says why. Nothing is built by an
 * import; building is a separate act that goes through the player's own permissions ({@link BlueprintBuilder}).
 * <p>
 * The packet carries at most {@link #MAX_TEXT} characters, because a custom payload sent to the server is cut at 32 KB.
 * An engine of a few hundred blocks is well under that.
 */
public final class BlueprintImport {

	/** The most text an import may carry. A full-size engine is a few thousand characters. */
	public static final int MAX_TEXT = 30_000;

	/** The one packet the client sends: the pasted text. */
	public record Payload(String text) implements CustomPacketPayload {

		public static final Type<Payload> TYPE = new Type<>(CreateEngines.asResource("import_blueprint"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Payload> CODEC =
			ByteBufCodecs.stringUtf8(MAX_TEXT)
				.map(Payload::new, Payload::text)
				.cast();

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** How an import ended. */
	public enum Outcome {
		IMPORTED, TOO_LONG, REFUSED, NOT_HOLDING_ONE
	}

	/**
	 * @param outcome the end
	 * @param reason  for a refusal, the reason's name (a {@code BlueprintException.Refusal}), else empty
	 * @param parts   parts in the imported blueprint
	 * @param name    its name
	 */
	public record Result(Outcome outcome, String reason, int parts, String name) {

		static Result of(Outcome outcome) {
			return new Result(outcome, "", 0, "");
		}
	}

	private BlueprintImport() {
	}

	public static void register(PayloadRegistrar registrar) {
		registrar.playToServer(Payload.TYPE, Payload.CODEC, (payload, context) -> accept(context.player(), payload.text()));
	}

	/** Handles a packet: imports into the blueprint in the player's main hand and tells them how it went. */
	public static void accept(Player player, String text) {
		ItemStack held = player.getItemInHand(InteractionHand.MAIN_HAND);
		Result result = importInto(held, text);
		switch (result.outcome()) {
			case IMPORTED -> player.displayClientMessage(CELang.translate("blueprint.imported", result.name(), result.parts())
				.component()
				.copy()
				.withStyle(ChatFormatting.GREEN), true);
			case TOO_LONG -> player.displayClientMessage(CELang.translate("blueprint.import.too_long", MAX_TEXT)
				.component()
				.copy()
				.withStyle(ChatFormatting.RED), true);
			case NOT_HOLDING_ONE -> player.displayClientMessage(CELang.translate("blueprint.import.not_holding")
				.component()
				.copy()
				.withStyle(ChatFormatting.RED), true);
			case REFUSED -> player.displayClientMessage(CELang.translate("blueprint.import.refused", CELang.translate(
				"blueprint.refusal." + result.reason()
					.toLowerCase(java.util.Locale.ROOT))
				.string())
				.component()
				.copy()
				.withStyle(ChatFormatting.RED), true);
		}
	}

	/**
	 * Checks the text and, if it is a good blueprint, stores it on the stack (blank or already recorded: it is replaced).
	 * The stack is untouched in every other case.
	 */
	public static Result importInto(ItemStack stack, @Nullable String text) {
		if (!(stack.getItem() instanceof BlueprintItem))
			return Result.of(Outcome.NOT_HOLDING_ONE);
		if (text == null || text.length() > MAX_TEXT)
			return Result.of(Outcome.TOO_LONG);
		try {
			Blueprint blueprint = BlueprintCodec.decode(text);
			BlueprintRules.check(blueprint, EngineBlueprintPolicy.INSTANCE);
			String canonical = BlueprintCodec.encode(blueprint);
			stack.set(CEComponents.BLUEPRINT.get(), new RecordedBlueprint(canonical, blueprint.name(), blueprint.parts()
				.size()));
			return new Result(Outcome.IMPORTED, "", blueprint.parts()
				.size(), blueprint.name());
		} catch (BlueprintException e) {
			return new Result(Outcome.REFUSED, e.refusal()
				.name(), 0, "");
		}
	}
}
