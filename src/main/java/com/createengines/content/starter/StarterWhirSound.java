package com.createengines.content.starter;

import java.util.IdentityHashMap;
import java.util.Map;

import com.createengines.content.LoopSanity;
import com.createengines.content.VoiceClock;
import com.createengines.registry.CESounds;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * The starter motor's whir: one seamless loop per starter, spun up when the clutch takes hold of the engine, pitched
 * with the motor's speed, and spun back down when the engine outruns it. Client only; the block entity reaches it
 * from a client-side check so a dedicated server never loads this class.
 */
public final class StarterWhirSound extends AbstractTickableSoundInstance {

	private static final Map<StarterBlockEntity, StarterWhirSound> PLAYING = new IdentityHashMap<>();
	private static final float SPIN_UP = 0.12f;
	private static final float SPIN_DOWN = 0.18f;

	private final StarterBlockEntity starter;
	private final long startedAt;
	/** How far the motor has wound up, 0 (stopped) to 1 (at speed). */
	private float spin;

	private static boolean near(double x, double y, double z) {
		Player player = Minecraft.getInstance().player;
		return player != null && LoopSanity.inRange(player.distanceToSqr(x, y, z));
	}

	private StarterWhirSound(StarterBlockEntity starter, long startedAt) {
		super(CESounds.STARTER_WHIR.get(), SoundSource.BLOCKS, SoundInstance.createUnseededRandom());
		this.starter = starter;
		this.startedAt = startedAt;
		looping = true;
		delay = 0;
		volume = 0;
		x = starter.getBlockPos()
			.getX() + 0.5;
		y = starter.getBlockPos()
			.getY() + 0.5;
		z = starter.getBlockPos()
			.getZ() + 0.5;
	}

	/**
	 * Starts this starter's whir unless it is already going. An instance the sound engine declined to start (master
	 * volume at zero, no free channel) never ticks to finish itself, so a dead one is replaced.
	 */
	public static void ensurePlaying(StarterBlockEntity starter) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null || !near(starter.getBlockPos()
			.getX() + 0.5, starter.getBlockPos()
				.getY() + 0.5, starter.getBlockPos()
					.getZ() + 0.5))
			return;
		SoundManager sounds = minecraft.getSoundManager();
		long now = minecraft.level.getGameTime();
		StarterWhirSound current = PLAYING.get(starter);
		// A new instance gets a moment to start; one the sound engine declined is only tried again after that
		if (current != null && (!VoiceClock.settled(now, current.startedAt) || !current.isStopped() && sounds.isActive(current)))
			return;
		PLAYING.values()
			.removeIf(whir -> VoiceClock.settled(now, whir.startedAt) && (whir.isStopped() || !sounds.isActive(whir)));
		StarterWhirSound whir = new StarterWhirSound(starter, now);
		PLAYING.put(starter, whir);
		sounds.play(whir);
	}

	/** Forgets every whir, for when the player leaves a world: the sound engine has already dropped them. */
	public static void clear() {
		PLAYING.clear();
	}

	/** The whir fades in from nothing, which the sound engine would otherwise skip as inaudible. */
	@Override
	public boolean canStartSilent() {
		return true;
	}

	@Override
	public void tick() {
		if (starter.isRemoved()) {
			finish();
			return;
		}
		boolean turning = starter.isTurning() && near(x, y, z);
		spin = Mth.clamp(spin + (turning ? SPIN_UP : -SPIN_DOWN), 0, 1);
		if (spin <= 0 && !turning) {
			finish();
			return;
		}
		float speed = LoopSanity.finite((float) starter.crankRpm(), 0, 128, 0) / 128f;
		volume = 0.8f * spin;
		pitch = (0.75f + 0.6f * speed) * (0.55f + 0.45f * spin);
	}

	private void finish() {
		PLAYING.remove(starter, this);
		stop();
	}
}
