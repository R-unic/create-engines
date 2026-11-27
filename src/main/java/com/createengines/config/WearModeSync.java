package com.createengines.config;

import com.createengines.CreateEngines;
import com.createengines.engine.sim.Tuning;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/**
 * Tells connected players the server's wear mode: when they join, and whenever it changes, whether by the config file or
 * by {@code /ce preset}. The server looks once a tick, so every way of changing it is covered. When to send and what to
 * do on arrival are in {@link WearModeLink}.
 */
public final class WearModeSync {

	public record Payload(boolean on) implements CustomPacketPayload {

		public static final Type<Payload> TYPE = new Type<>(CreateEngines.asResource("wear_mode"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Payload> CODEC =
			ByteBufCodecs.BOOL.map(Payload::new, Payload::on)
				.cast();

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	private static final WearModeLink LINK = new WearModeLink();

	private WearModeSync() {
	}

	public static void register(PayloadRegistrar registrar) {
		// A server running in this same process is the game's own: asked this way, since this class also loads without a client
		registrar.playToClient(Payload.TYPE, Payload.CODEC,
			(payload, context) -> WearModeLink.receive(payload.on(), ServerLifecycleHooks.getCurrentServer() != null));
	}

	public static void onServerTick(ServerTickEvent.Post event) {
		boolean on = Tuning.wearMode;
		if (LINK.due(on))
			for (ServerPlayer player : event.getServer()
				.getPlayerList()
				.getPlayers())
				send(player, on);
	}

	/** A switch made between a player's configuration and their joining would otherwise miss them. */
	public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
		if (event.getEntity() instanceof ServerPlayer player)
			send(player, Tuning.wearMode);
	}

	/** What the server last told its players, or null before its first tick. */
	public static Boolean told() {
		return LINK.told();
	}

	private static void send(ServerPlayer player, boolean on) {
		// A connection that never agreed to the payload (a test's mock player) refuses it with an exception
		if (player.connection.hasChannel(Payload.TYPE))
			PacketDistributor.sendToPlayer(player, new Payload(on));
	}
}
