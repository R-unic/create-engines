package com.createengines.advancement;

import com.createengines.CreateEngines;

import net.minecraft.advancements.AdvancementType;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.AdvancementEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Tells a player's game to play the sting when they earn one of this mod's goals or challenges. The server decides, so
 * the sting follows the toast exactly: an advancement with no toast, or a plain task, never stings.
 */
public final class AdvancementSting {

	/** The sting's packet: {@code big} is true for a challenge. */
	public record Payload(boolean big) implements CustomPacketPayload {

		public static final Type<Payload> TYPE = new Type<>(CreateEngines.asResource("sting"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Payload> CODEC =
			ByteBufCodecs.BOOL.map(Payload::new, Payload::big)
				.cast();

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	private AdvancementSting() {
	}

	/** Whether earning an advancement of this kind, with or without a toast, is worth the sting. */
	public static boolean stings(AdvancementType type, boolean showsToast) {
		return StingGate.worthIt(type == AdvancementType.GOAL || type == AdvancementType.CHALLENGE, showsToast);
	}

	public static void register(RegisterPayloadHandlersEvent event) {
		PayloadRegistrar registrar = event.registrar("1")
			.optional();
		registrar.playToClient(Payload.TYPE, Payload.CODEC, (payload, context) -> Receiver.play(payload.big()));
		com.createengines.content.blueprint.BlueprintImport.register(registrar);
		com.createengines.config.WearModeSync.register(registrar);
	}

	public static void onEarned(AdvancementEvent.AdvancementEarnEvent event) {
		if (!(event.getEntity() instanceof ServerPlayer player))
			return;
		var holder = event.getAdvancement();
		if (!holder.id()
			.getNamespace()
			.equals(CreateEngines.MOD_ID))
			return;
		DisplayInfo display = holder.value()
			.display()
			.orElse(null);
		if (display == null || !stings(display.getType(), display.shouldShowToast()))
			return;
		PacketDistributor.sendToPlayer(player, new Payload(display.getType() == AdvancementType.CHALLENGE));
	}

	/** Kept apart so a dedicated server never loads the client's sound classes. */
	private static final class Receiver {
		static void play(boolean big) {
			com.createengines.content.StingSound.play(big);
		}
	}
}
