package com.createengines.content.starter;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

import com.createengines.registry.CESounds;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;

/**
 * The starter motor's whir: one seamless loop per starter, spun up when the clutch takes hold of the engine, pitched
 * with the motor's speed, and spun back down when the engine outruns it. Client only; the block entity reaches it
 * from a client-side check so a dedicated server never loads this class.
 */
public final class StarterWhirSound extends AbstractTickableSoundInstance {

	private static final Set<StarterBlockEntity> PLAYING = Collections.newSetFromMap(new IdentityHashMap<>());
	private static final float SPIN_UP = 0.12f;
	private static final float SPIN_DOWN = 0.18f;

	private final StarterBlockEntity starter;
	/** How far the motor has wound up, 0 (stopped) to 1 (at speed). */
	private float spin;

	private StarterWhirSound(StarterBlockEntity starter) {
		super(CESounds.STARTER_WHIR.get(), SoundSource.BLOCKS, SoundInstance.createUnseededRandom());
		this.starter = starter;
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

	/** Starts this starter's whir unless it is already going. */
	public static void ensurePlaying(StarterBlockEntity starter) {
		if (PLAYING.add(starter))
			Minecraft.getInstance()
				.getSoundManager()
				.play(new StarterWhirSound(starter));
	}

	@Override
	public void tick() {
		if (starter.isRemoved()) {
			finish();
			return;
		}
		spin = Mth.clamp(spin + (starter.isTurning() ? SPIN_UP : -SPIN_DOWN), 0, 1);
		if (spin <= 0 && !starter.isTurning()) {
			finish();
			return;
		}
		float speed = Math.min(Math.abs(starter.getSpeed()), 128) / 128f;
		volume = 0.8f * spin;
		pitch = (0.75f + 0.6f * speed) * (0.55f + 0.45f * spin);
	}

	private void finish() {
		PLAYING.remove(starter);
		stop();
	}
}
