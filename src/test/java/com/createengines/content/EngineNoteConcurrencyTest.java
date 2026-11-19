package com.createengines.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.lang.management.BufferPoolMXBean;
import java.lang.management.ManagementFactory;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

import com.createengines.content.EngineNoteSynth.Params;

/**
 * Verification track item 6: the streamed engine note under the timing the game gives it. The game thread calls
 * {@link EngineNoteSynth#set} every tick while the sound engine's own thread reads each voice a tenth of a second at a
 * time, and voices are made and dropped as chunks load and unload. This is where 1.2.0 ran the sound thread out of
 * direct memory. Checks: no throwable on any thread, no deadlock, every read well formed, and memory that stays flat.
 * <p>
 * The timing is the point and cannot be seeded, so the seeds fix what is asked for and the schedule is left to the
 * machine; a failure names the voice and the read it happened on.
 */
class EngineNoteConcurrencyTest {

	private static final float[] EXTREMES = {0, -1, 1, 1e-6f, 30, 3000, 100000, 1e9f, Float.MAX_VALUE, Float.NaN,
		Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY};

	private static float[] even(int cylinders) {
		float[] angles = new float[cylinders];
		for (int i = 0; i < cylinders; i++)
			angles[i] = i * 720f / cylinders;
		return angles;
	}

	private static Params randomParams(Random random) {
		if (random.nextInt(10) == 0)
			return new Params(pick(random), new float[] {pick(random), 0, 719}, pick(random), pick(random), pick(random));
		if (random.nextInt(25) == 0)
			return Params.SILENT;
		return new Params(random.nextInt(8000), even(1 + random.nextInt(16)), random.nextFloat(), random.nextFloat() * 1.3f,
			random.nextBoolean() ? 1 : 0.25f);
	}

	private static float pick(Random random) {
		return EXTREMES[random.nextInt(EXTREMES.length)];
	}

	private static void join(List<Thread> threads, long millis) throws InterruptedException {
		long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(millis);
		for (Thread thread : threads) {
			thread.join(Math.max(1, TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime())));
			if (thread.isAlive()) {
				var deadlocked = ManagementFactory.getThreadMXBean().findDeadlockedThreads();
				fail("thread " + thread.getName() + " did not finish in " + millis + " ms"
					+ (deadlocked == null ? " (no Java-level deadlock found; it is stuck or starved)" : " (deadlocked threads: "
						+ deadlocked.length + ")"));
			}
		}
	}

	/** One game thread feeding hostile and ordinary states to many voices while a sound thread per voice reads them. */
	@Test
	void manyVoicesReadWhileTheGameThreadChangesTheirState() throws Exception {
		int voices = 12;
		EngineNoteSynth[] synths = new EngineNoteSynth[voices];
		for (int i = 0; i < voices; i++)
			synths[i] = new EngineNoteSynth(i + 1);
		ConcurrentLinkedQueue<Throwable> thrown = new ConcurrentLinkedQueue<>();
		AtomicBoolean stop = new AtomicBoolean();
		AtomicLong reads = new AtomicLong();
		CountDownLatch go = new CountDownLatch(1);
		List<Thread> readers = new ArrayList<>();
		for (int v = 0; v < voices; v++) {
			final int voice = v;
			Thread thread = new Thread(() -> {
				Random random = new Random(1000 + voice);
				// Calls the synth directly so an exception is seen, where EngineNotePcm would swallow it into silence
				EngineNotePcm pcm = new EngineNotePcm((out, offset, count) -> {
					try {
						synths[voice].render(out, offset, count);
					} catch (Throwable t) {
						thrown.add(t);
						throw t;
					}
				});
				try {
					go.await();
					ByteBuffer first = null;
					for (int read = 0; !stop.get(); read++) {
						int asked = random.nextInt(6) == 0 ? random.nextInt(EngineNotePcm.CHUNK * 3) : EngineNotePcm.CHUNK * 2;
						ByteBuffer data = pcm.read(asked);
						if (first == null)
							first = data;
						else if (data != first)
							throw new AssertionError("voice " + voice + " read " + read + " came back in a new buffer; each one is direct memory only the GC frees");
						int expected = Math.max(0, Math.min(EngineNotePcm.CHUNK, asked / 2)) * 2;
						if (data.remaining() != expected)
							throw new AssertionError("voice " + voice + " read " + read + " gave " + data.remaining() + " bytes, wanted " + expected);
						reads.incrementAndGet();
						if (random.nextInt(4) == 0)
							Thread.sleep(0, random.nextInt(200_000));
					}
				} catch (Throwable t) {
					thrown.add(t);
				}
			}, "voice-" + v);
			readers.add(thread);
			thread.start();
		}
		Thread game = new Thread(() -> {
			Random random = new Random(7);
			try {
				go.await();
				while (!stop.get()) {
					for (EngineNoteSynth synth : synths)
						if (random.nextInt(3) == 0)
							synth.set(randomParams(random));
					if (random.nextInt(3) == 0)
						Thread.sleep(0, random.nextInt(300_000));
					else
						Thread.yield();
				}
			} catch (Throwable t) {
				thrown.add(t);
			}
		}, "game");
		game.start();
		go.countDown();
		Thread.sleep(2500);
		stop.set(true);
		List<Thread> all = new ArrayList<>(readers);
		all.add(game);
		join(all, 10_000);
		assertNull(thrown.peek(), "a thread threw: " + thrown.peek());
		assertTrue(reads.get() > 500, "only " + reads.get() + " reads happened; the readers were starved");
	}

	/** Reads allocate nothing worth counting, however the game thread behaves meanwhile. */
	@Test
	void aReadAllocatesAlmostNothingWhileTheStateChangesUnderIt() throws Exception {
		var bean = ManagementFactory.getThreadMXBean();
		if (!(bean instanceof com.sun.management.ThreadMXBean sun) || !sun.isThreadAllocatedMemorySupported()) {
			System.out.println("allocation check skipped: this JVM cannot measure per-thread allocation");
			return;
		}
		EngineNoteSynth synth = new EngineNoteSynth(5);
		AtomicBoolean stop = new AtomicBoolean();
		Thread game = new Thread(() -> {
			Random random = new Random(3);
			while (!stop.get()) {
				synth.set(randomParams(random));
				Thread.yield();
			}
		}, "game");
		game.start();
		long[] perRead = new long[1];
		Thread sound = new Thread(() -> {
			EngineNotePcm pcm = new EngineNotePcm(synth);
			for (int i = 0; i < 300; i++)
				pcm.read(EngineNotePcm.CHUNK * 2);
			long id = Thread.currentThread().threadId();
			long before = sun.getThreadAllocatedBytes(id);
			int reads = 1500;
			for (int i = 0; i < reads; i++)
				pcm.read(EngineNotePcm.CHUNK * 2);
			perRead[0] = (sun.getThreadAllocatedBytes(id) - before) / reads;
		}, "sound");
		sound.start();
		join(List.of(sound), 30_000);
		stop.set(true);
		join(List.of(game), 10_000);
		System.out.println("CONCURRENCY the sound thread allocates " + perRead[0] + " bytes per read");
		assertTrue(perRead[0] < 64, "a read allocates " + perRead[0] + " bytes; ten of them a second per voice add up");
	}

	/**
	 * Voices come and go as chunks load and unload. Each holds one small direct buffer, which only the garbage collector
	 * frees; after heavy churn from several threads, direct memory must come back down.
	 */
	@Test
	void directMemoryIsBoundedUnderVoiceChurn() throws Exception {
		BufferPoolMXBean direct = ManagementFactory.getPlatformMXBeans(BufferPoolMXBean.class).stream()
			.filter(pool -> pool.getName().equals("direct")).findFirst().orElse(null);
		if (direct == null) {
			System.out.println("direct memory check skipped: no direct buffer pool bean");
			return;
		}
		System.gc();
		long before = direct.getMemoryUsed();
		ConcurrentLinkedQueue<Throwable> thrown = new ConcurrentLinkedQueue<>();
		List<Thread> workers = new ArrayList<>();
		int threads = 6;
		int voicesPerThread = 1500;
		for (int t = 0; t < threads; t++) {
			final int id = t;
			Thread worker = new Thread(() -> {
				Random random = new Random(50 + id);
				try {
					for (int i = 0; i < voicesPerThread; i++) {
						EngineNoteSynth synth = new EngineNoteSynth(random.nextInt());
						synth.set(randomParams(random));
						EngineNotePcm pcm = new EngineNotePcm(synth);
						for (int r = random.nextInt(3); r >= 0; r--)
							pcm.read(EngineNotePcm.CHUNK * 2);
					}
				} catch (Throwable e) {
					thrown.add(e);
				}
			}, "churn-" + t);
			workers.add(worker);
			worker.start();
		}
		join(workers, 60_000);
		assertNull(thrown.peek(), "a worker threw: " + thrown.peek());
		long peakTotal = (long) threads * voicesPerThread * EngineNotePcm.CHUNK * 2L;
		// Everything made is garbage now; after collections the pool must be back near where it began
		long after = Long.MAX_VALUE;
		for (int attempt = 0; attempt < 20 && after > before + 4_000_000; attempt++) {
			System.gc();
			Thread.sleep(100);
			after = direct.getMemoryUsed();
		}
		System.out.println("CONCURRENCY direct memory " + before + " -> " + after + " after allocating " + peakTotal + " in voices");
		assertTrue(after <= before + 4_000_000, "direct memory stayed at " + after + " from " + before
			+ " after the voices were dropped; buffers are being kept alive");
	}

	/** Voices dropped and remade while a sound thread is mid-read: a stale reader finishes its read without harm. */
	@Test
	void aVoiceReplacedMidReadStillFinishesItsRead() throws Exception {
		ConcurrentLinkedQueue<Throwable> thrown = new ConcurrentLinkedQueue<>();
		AtomicBoolean stop = new AtomicBoolean();
		EngineNoteSynth[] slot = {new EngineNoteSynth(1)};
		Thread sound = new Thread(() -> {
			Random random = new Random(11);
			try {
				while (!stop.get()) {
					// The sound thread holds the voice it was given; the game thread may already have replaced it
					EngineNoteSynth held = slot[0];
					EngineNotePcm pcm = new EngineNotePcm(held);
					for (int i = 0; i < 1 + random.nextInt(4); i++)
						assertEquals(EngineNotePcm.CHUNK * 2, pcm.read(EngineNotePcm.CHUNK * 2).remaining());
				}
			} catch (Throwable t) {
				thrown.add(t);
			}
		}, "sound");
		Thread game = new Thread(() -> {
			Random random = new Random(12);
			try {
				while (!stop.get()) {
					EngineNoteSynth fresh = new EngineNoteSynth(random.nextInt());
					fresh.set(randomParams(random));
					slot[0] = fresh;
					Thread.yield();
				}
			} catch (Throwable t) {
				thrown.add(t);
			}
		}, "game");
		sound.start();
		game.start();
		Thread.sleep(1500);
		stop.set(true);
		join(List.of(sound, game), 10_000);
		assertNull(thrown.peek(), "a thread threw: " + thrown.peek());
	}

	private static short[] renderSynth(int seed, Params params, int samples) {
		EngineNoteSynth synth = new EngineNoteSynth(seed);
		synth.set(params);
		short[] out = new short[samples];
		for (int at = 0; at < samples; at += 1000)
			synth.render(out, at, Math.min(1000, samples - at));
		return out;
	}

	/**
	 * The same voice rendered on many threads at once is the same sound as rendered alone. A scratch buffer or a
	 * generator shared between voices, which are one per engine, would turn two engines into a crackle.
	 */
	@Test
	void sameVoicesRenderedOnManyThreadsAreIdenticalToOneRenderedAlone() throws Exception {
		int cases = 6;
		Params[] params = new Params[cases];
		short[][] alone = new short[cases][];
		Random random = new Random(77);
		for (int i = 0; i < cases; i++) {
			params[i] = new Params(300 + random.nextInt(3000), even(1 + random.nextInt(12)), random.nextFloat(), random.nextFloat(),
				random.nextFloat());
			alone[i] = renderSynth(10 + i, params[i], 30_000);
		}
		int threads = 8;
		CountDownLatch go = new CountDownLatch(1);
		ConcurrentLinkedQueue<Throwable> thrown = new ConcurrentLinkedQueue<>();
		List<Thread> workers = new ArrayList<>();
		for (int t = 0; t < threads; t++) {
			final int id = t;
			Thread worker = new Thread(() -> {
				try {
					go.await();
					for (int round = 0; round < 4; round++)
						for (int k = 0; k < cases; k++) {
							int c = (k + id) % cases;
							short[] got = renderSynth(10 + c, params[c], 30_000);
							for (int i = 0; i < got.length; i++)
								if (got[i] != alone[c][i])
									throw new AssertionError("thread " + id + " voice " + c + " sample " + i + " is " + got[i]
										+ " where rendering it alone gave " + alone[c][i]);
						}
				} catch (Throwable e) {
					thrown.add(e);
				}
			}, "render-" + t);
			workers.add(worker);
			worker.start();
		}
		go.countDown();
		join(workers, 60_000);
		assertNull(thrown.peek(), "a thread threw: " + thrown.peek());
		boolean sound = false;
		for (short s : alone[0])
			sound |= s != 0;
		assertTrue(sound, "the reference voice is silent, so the comparison proves nothing");
	}

	/** The advancement stings are streamed the same way, one per sound, from the sound thread. */
	@Test
	void stingsRenderedOnManyThreadsAreIdenticalToOneRenderedAlone() throws Exception {
		short[][] alone = new short[2][];
		for (int c = 0; c < 2; c++)
			alone[c] = renderSting(c == 1);
		int threads = 8;
		CountDownLatch go = new CountDownLatch(1);
		ConcurrentLinkedQueue<Throwable> thrown = new ConcurrentLinkedQueue<>();
		List<Thread> workers = new ArrayList<>();
		for (int t = 0; t < threads; t++) {
			final int id = t;
			Thread worker = new Thread(() -> {
				try {
					go.await();
					for (int round = 0; round < 6; round++) {
						int c = (round + id) % 2;
						short[] got = renderSting(c == 1);
						if (!java.util.Arrays.equals(got, alone[c]))
							throw new AssertionError("thread " + id + " rendered the " + (c == 1 ? "challenge" : "goal") + " sting differently");
					}
				} catch (Throwable e) {
					thrown.add(e);
				}
			}, "sting-" + t);
			workers.add(worker);
			worker.start();
		}
		go.countDown();
		join(workers, 60_000);
		assertNull(thrown.peek(), "a thread threw: " + thrown.peek());
		assertTrue(alone[0].length > 0 && alone[1].length > 0, "a sting with no samples would pass this test on nothing");
	}

	private static short[] renderSting(boolean challenge) {
		StingSynth sting = new StingSynth(challenge);
		short[] out = new short[sting.length()];
		for (int at = 0; at < out.length; at += 1000)
			sting.render(out, at, Math.min(1000, out.length - at));
		return out;
	}
}
