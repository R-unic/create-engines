package com.createengines.client;

import java.util.concurrent.atomic.AtomicInteger;

import org.lwjgl.openal.AL10;
import org.lwjgl.openal.ALC;
import org.lwjgl.openal.ALC10;
import org.lwjgl.openal.ALCCapabilities;
import org.lwjgl.openal.EXTEfx;
import org.slf4j.Logger;

import com.createengines.content.headphones.HeadphoneWear;
import com.createengines.content.headphones.Muffle;
import com.mojang.blaze3d.audio.Channel;
import com.mojang.logging.LogUtils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.sound.PlaySoundSourceEvent;
import net.neoforged.neoforge.client.event.sound.PlayStreamingSourceEvent;
import net.neoforged.neoforge.client.event.sound.SoundEngineLoadEvent;

/**
 * Noise-cancelling headphones, on the sound card: while they are worn every sound from the world has a low-pass filter put on
 * its OpenAL source, so it is quieter and duller and never silent. Minecraft does not use OpenAL's filter extension itself but
 * its OpenAL offers it. OpenAL has no listener-wide filter, so the same filter is attached to each source. The rules for which
 * sounds and how much are in {@link Muffle}.
 * <p>
 * How it hooks in, with no mixin: NeoForge hands over a sound's {@link Channel} on the sound thread right after it is set up
 * ({@link PlaySoundSourceEvent} for sounds that load whole, {@link PlayStreamingSourceEvent} for streamed ones, which includes
 * this mod's engine note and the game's music), and that is where a filter goes on a sound that starts while the headphones
 * are worn. Putting them on or off changes sounds already playing too: the sound engine's own table of playing sounds is walked
 * on the client thread and each source is changed on the sound thread through its handle, which does nothing once the sound
 * has ended (Minecraft destroys the source then). Reading the source number out of a channel, and the table and the sound
 * engine, needs the access transformer in META-INF/accesstransformer.cfg.
 * <p>
 * A filter made on one OpenAL context is not valid on another, so it is made again when the sound engine restarts. A sound card
 * without the extension gets a cap on loudness instead (a volume cut only), and the headphones' tooltip says so. Client only.
 */
public final class HeadphoneMuffle {

	private static final Logger LOGGER = LogUtils.getLogger();

	/** Whether the headphones are on, as the client thread last saw. Read by the sound thread as sounds start. */
	private static volatile boolean worn;
	/** False once a sound card has shown it cannot filter. Until a sound has been muffled it is not known, and reads true. */
	private static volatile boolean filtering = true;

	private static final Object LOCK = new Object();
	/** Guarded by LOCK: the OpenAL context the filter was made on, and the filter (0 when the context cannot filter). */
	private static long filterContext;
	private static int filter;

	/**
	 * The OpenAL sources that carry the muffle right now. OpenAL cannot be asked (reading a source's direct filter back is an
	 * error), so it is kept here, by the sound thread, and read by the client audit. A source number is reused when a sound
	 * ends, so every sound that starts without the muffle clears its number.
	 */
	private static final java.util.Set<Integer> attached = java.util.concurrent.ConcurrentHashMap.newKeySet();

	private static final AtomicInteger changed = new AtomicInteger();
	private static final AtomicInteger failed = new AtomicInteger();

	private HeadphoneMuffle() {
	}

	/** Whether the sound card can filter. False means the headphones only cap loudness. Not known before the first muffled sound. */
	public static boolean filtering() {
		return filtering;
	}

	/** Whether the headphones were last seen worn. */
	public static boolean worn() {
		return worn;
	}

	/** How many sources the headphones have been put on or taken off of, for the audit. */
	public static int sourcesChanged() {
		return changed.get();
	}

	/** How many of those left an OpenAL error, for the audit. It is meant to stay zero. */
	public static int sourcesFailed() {
		return failed.get();
	}

	public static void onPlaySource(PlaySoundSourceEvent event) {
		attach(event.getSound(), event.getChannel());
	}

	public static void onPlayStreaming(PlayStreamingSourceEvent event) {
		attach(event.getSound(), event.getChannel());
	}

	/** A sound that starts while the headphones are on. Runs on the sound thread. */
	private static void attach(SoundInstance sound, Channel channel) {
		if (worn && Muffle.muffles(sound.getSource()
			.getName()))
			apply(channel, true);
		else
			attached.remove(channel.source);
	}

	public static void onClientTick(ClientTickEvent.Post event) {
		Minecraft minecraft = Minecraft.getInstance();
		setWorn(minecraft.player != null && HeadphoneWear.isWorn(minecraft.player));
	}

	/** Puts the headphones on or off, for the sounds already playing too. Client thread. */
	public static void setWorn(boolean now) {
		if (now == worn)
			return;
		worn = now;
		SoundEngine engine = Minecraft.getInstance()
			.getSoundManager().soundEngine;
		// The table is the engine's own and is changed on this thread, as the engine's category volume change walks it
		engine.instanceToChannel.forEach((sound, handle) -> {
			if (Muffle.muffles(sound.getSource()
				.getName()))
				handle.execute(channel -> apply(channel, now));
		});
	}

	/**
	 * For the client audit: reads, on the sound thread, which of the playing sounds the muffle has been put on (OpenAL cannot
	 * be asked: reading a source's direct filter back is an error, so the sources are kept in a set as they are changed, and
	 * every change is checked for an OpenAL error). {@code muffled} counts
	 * playing sounds of a category the headphones muffle and {@code muffledNow} those of them that are muffled right now (the
	 * filter is on the source, or with no filter the loudness cap is); {@code exempt} counts playing sounds of a category they
	 * leave alone and {@code exemptMuffled} those of them that are muffled anyway, which must stay zero. Waits for the sound
	 * thread, so it is called from the client thread and never from the sound thread.
	 */
	public static Audit audit() {
		AtomicInteger muffled = new AtomicInteger(), muffledNow = new AtomicInteger(), exempt = new AtomicInteger(),
			exemptMuffled = new AtomicInteger();
		java.util.List<java.util.concurrent.CompletableFuture<Void>> reads = new java.util.ArrayList<>();
		SoundEngine engine = Minecraft.getInstance()
			.getSoundManager().soundEngine;
		engine.instanceToChannel.forEach((sound, handle) -> {
			if (handle.isStopped())
				return;
			boolean muffles = Muffle.muffles(sound.getSource()
				.getName());
			java.util.concurrent.CompletableFuture<Void> read = new java.util.concurrent.CompletableFuture<>();
			reads.add(read);
			handle.execute(channel -> {
				try {
					(muffles ? muffled : exempt).incrementAndGet();
					if (attached.contains(channel.source))
						(muffles ? muffledNow : exemptMuffled).incrementAndGet();
				} finally {
					read.complete(null);
				}
			});
		});
		try {
			java.util.concurrent.CompletableFuture.allOf(reads.toArray(java.util.concurrent.CompletableFuture[]::new))
				.get(2, java.util.concurrent.TimeUnit.SECONDS);
		} catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException | InterruptedException e) {
			// A sound that ended in the meantime has no source to read: it is simply not counted
		}
		return new Audit(muffled.get(), muffledNow.get(), exempt.get(), exemptMuffled.get());
	}

	public record Audit(int muffled, int muffledNow, int exempt, int exemptMuffled) {
	}

	/** The sound engine starts over: the filter belonged to the old OpenAL context. */
	public static void onSoundEngineLoad(SoundEngineLoadEvent event) {
		synchronized (LOCK) {
			filterContext = 0;
			filter = 0;
		}
		attached.clear();
	}

	/** The world closed: every sound is gone with it, and the next world works out for itself whether they are worn. */
	public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
		worn = false;
	}

	/** Puts the filter on a source, or takes it off. Sound thread only (it is where OpenAL's context is current). */
	private static void apply(Channel channel, boolean on) {
		long context = ALC10.alcGetCurrentContext();
		if (context == 0)
			return;
		int source = channel.source;
		int filterId = filterFor(context);
		if (filterId != 0)
			AL10.alSourcei(source, EXTEfx.AL_DIRECT_FILTER, on ? filterId : EXTEfx.AL_FILTER_NULL);
		else
			AL10.alSourcef(source, AL10.AL_MAX_GAIN, Muffle.fallbackMaxGain(on));
		changed.incrementAndGet();
		if (on)
			attached.add(source);
		else
			attached.remove(source);
		int error = AL10.alGetError();
		if (error != AL10.AL_NO_ERROR) {
			// Read here so the sound engine's own next check does not report it as its own
			if (failed.incrementAndGet() == 1)
				LOGGER.warn("OpenAL error {} putting the headphones' muffle on a sound; further ones are counted, not logged", error);
		}
	}

	/** The low-pass filter of this context, made on first use; 0 when the context has no such filter. */
	private static int filterFor(long context) {
		synchronized (LOCK) {
			if (context == filterContext)
				return filter;
			filterContext = context;
			filter = 0;
			ALCCapabilities capabilities = ALC.createCapabilities(ALC10.alcGetContextsDevice(context));
			if (!capabilities.ALC_EXT_EFX) {
				filtering = false;
				LOGGER.info("This sound device has no OpenAL filter extension; the headphones will cap loudness instead of muffling");
				return 0;
			}
			int id = EXTEfx.alGenFilters();
			EXTEfx.alFilteri(id, EXTEfx.AL_FILTER_TYPE, EXTEfx.AL_FILTER_LOWPASS);
			EXTEfx.alFilterf(id, EXTEfx.AL_LOWPASS_GAIN, Muffle.GAIN);
			EXTEfx.alFilterf(id, EXTEfx.AL_LOWPASS_GAINHF, Muffle.GAIN_HF);
			int error = AL10.alGetError();
			if (error != AL10.AL_NO_ERROR) {
				filtering = false;
				LOGGER.warn("OpenAL error {} making the headphones' low-pass filter; the headphones will cap loudness instead", error);
				return 0;
			}
			filtering = true;
			filter = id;
			return id;
		}
	}
}
