package com.createengines.content;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;
import com.createengines.compat.sable.SableCompat;
import com.createengines.content.crankshaft.CrankshaftBlockEntity;
import com.createengines.engine.EngineSummary;
import com.createengines.engine.sim.Muffling;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Every running engine in earshot this tick, gathered as their crankshafts call in, and worked out once a tick: how much
 * each settles, the ceiling over all of them, and which engines are mixed into the shared "engine room" voice
 * ({@link EngineNoteSound.Room}). It is worked out by whichever calls first in a tick, a crankshaft or the room voice itself, so engines
 * that all stop calling in at once are still forgotten and the voice falls silent. Once a second it also asks the world what
 * walls stand between the listener and each engine, and what room the listener is in ({@link NoteSurroundings}). It uses
 * nothing of the client itself: the listener and the sounds are {@link EngineNoteSound}'s. Client thread only.
 */
final class EngineRoomScene {

	/** Ticks an engine stays in the scene after it last called in. */
	private static final int FORGET_TICKS = 20;
	/** Ticks between looks for the walls in each engine's way, and between feeling out the listener's room. */
	private static final int LOOK_TICKS = 20;

	private static final Map<BlockPos, Heard> HEARD = new HashMap<>();
	private static final EngineRoomSlots<Heard> SLOTS = new EngineRoomSlots<>();
	private static final TickOnce ONCE = new TickOnce();
	private static float ceiling = 1;
	private static float[] levels = new float[16];
	/** The engines the room voice may play: those without a note, and those part way between it and one. */
	private static final List<Heard> waiting = new ArrayList<>();
	/** The nearest engine with no note at all, or null: the next to be given one. */
	private static @Nullable Heard nearestWithout;
	private static EngineNoteSound.Room room;
	private static NoteSurroundings.Room surroundings = NoteSurroundings.Room.OUTDOORS;
	/** When the listener's room was last felt out, or -1 for not yet. */
	private static long feltAt = -1;

	private EngineRoomScene() {
	}

	private static final class Heard {
		CrankshaftBlockEntity crank;
		final EngineChorus.Settle settle = new EngineChorus.Settle();
		final ExhaustCentre exhaust = new ExhaustCentre();
		EngineNoteSynth synth;
		EngineNoteDrive drive;
		long seenAt;
		float level;
		double distance;
		double reportedPhase = Double.NaN;
		long reportedAt;
		boolean counted;
		/** The walls last found between the listener and this engine, and when they were looked for (-1: never). */
		int walls;
		long wallsAt = -1;
	}

	static void offer(CrankshaftBlockEntity crank, long now) {
		update(now);
		EngineSummary summary = crank.summary();
		if (summary == null)
			return;
		Heard heard = HEARD.computeIfAbsent(crank.getBlockPos(), $ -> new Heard());
		heard.crank = crank;
		heard.seenAt = now;
		Vec3 from = source(crank.getBlockPos());
		heard.distance = EngineNoteSound.distance(from);
		look(heard, from, now);
		float share = summary.cylinders() > 0 ? summary.firing() / (float) summary.cylinders() : 1;
		float settled = heard.settle.update(Math.abs(summary.rpm()), EngineNoteSound.load(summary), share, 0.05f);
		heard.level = EngineChorus.reach(heard.distance) * settled * EngineNoteSound.buildVolume(summary);
	}

	/** Looks, when it is time to, for the walls in an engine's way and for the room the listener is in. */
	private static void look(Heard heard, Vec3 from, long now) {
		Vec3 ear = EngineNoteSound.ear();
		Level level = heard.crank.getLevel();
		if (ear == null || level == null)
			return;
		if (due(heard.wallsAt, now)) {
			heard.wallsAt = now;
			heard.walls = heard.distance <= EngineChorus.REACH ? NoteWorldProbe.walls(level, ear, from) : 0;
		}
		if (due(feltAt, now)) {
			feltAt = now;
			surroundings = NoteWorldProbe.room(level, ear);
		}
	}

	private static boolean due(long lookedAt, long now) {
		return lookedAt < 0 || now < lookedAt || now - lookedAt >= LOOK_TICKS;
	}

	/**
	 * Puts the engine at {@code at}, heard from {@code distance} blocks, in its place in the world: the air and walls its
	 * note passes through, and the listener's room. An engine no longer in the scene keeps what it last had in its way.
	 */
	static void surround(BlockPos at, double distance, EngineNoteMix mix) {
		mix.setRoom(surroundings);
		Heard heard = HEARD.get(at);
		if (heard != null)
			mix.setAir(NoteSurroundings.cutoff(distance, heard.walls), NoteSurroundings.wallLevel(heard.walls));
	}

	/** Works the scene out, if nothing has this tick. */
	static void update(long now) {
		if (ONCE.due(now))
			resolve(now);
	}

	/** Where an engine's sound comes from: the middle of its exhaust openings, or its crankshaft until they are known. */
	static Vec3 source(BlockPos at) {
		Heard heard = HEARD.get(at);
		if (heard != null && heard.crank.getLevel() != null) {
			double[] centre = heard.exhaust.centre(heard.crank.getLevel()
				.getGameTime());
			if (centre != null)
				return new Vec3(centre[0], centre[1], centre[2]);
		}
		return at.getCenter();
	}

	/** An exhaust opening of the engine at {@code at}, as one of its cylinders reports it. */
	static void reportOpening(BlockPos at, BlockPos opening, float loudness, long now) {
		Heard heard = HEARD.get(at);
		if (heard != null)
			heard.exhaust.report(opening.asLong(), opening.getX() + 0.5, opening.getY() + 0.5, opening.getZ() + 0.5,
				loudness, now);
	}

	/** Where an engine the shared voice plays is in its cycle. */
	static void reportPhase(BlockPos at, double cycleDegrees) {
		Heard heard = HEARD.get(at);
		if (heard != null) {
			heard.reportedPhase = cycleDegrees;
			heard.reportedAt = System.nanoTime();
		}
	}

	/** Whether an engine without a note is enough nearer than the one at {@code at} to have the next voice instead. */
	static boolean nearerWaiting(BlockPos at) {
		Heard asking = HEARD.get(at);
		return asking != null && nearestWithout != null
			&& EngineChorus.shouldYield(asking.distance, nearestWithout.distance);
	}

	/** With every voice taken, the farthest note held long enough gives way to an engine much nearer than it: one at a time. */
	private static void handOver(long now) {
		if (!EngineNoteSound.voicesFull() || nearestWithout == null || EngineNoteSound.anyYielding())
			return;
		EngineNoteSound farthest = null;
		double most = -1;
		for (EngineNoteSound note : EngineNoteSound.notes()) {
			double away = EngineNoteSound.distance(source(note.at()));
			if (away > most && NoteHandover.mayYield(note.age(now), away, nearestWithout.distance)) {
				most = away;
				farthest = note;
			}
		}
		if (farthest != null)
			farthest.giveWay();
	}

	/** The share an engine with its own note is played at: its settling and the ceiling over the scene. */
	static float share(BlockPos at) {
		Heard heard = HEARD.get(at);
		return heard == null ? 1 : heard.settle.gain() * ceiling;
	}

	/** Whether the shared voice is playing the engine at {@code at} now, so a note it gets takes over from there. */
	static boolean inSlot(BlockPos at) {
		Heard heard = HEARD.get(at);
		return heard != null && SLOTS.slotOf(heard) >= 0 && room != null && room.alive();
	}

	static boolean inRoom(BlockPos at) {
		Heard heard = HEARD.get(at);
		return heard != null && heard.counted && EngineNoteSound.hasFirings(heard.crank) && room != null && room.alive();
	}

	static void clear() {
		HEARD.clear();
		SLOTS.clear();
		waiting.clear();
		nearestWithout = null;
		if (room != null)
			room.end();
		room = null;
		surroundings = NoteSurroundings.Room.OUTDOORS;
		feltAt = -1;
		ONCE.reset();
	}

	/** Last tick's scene, worked out at the start of this one. */
	private static void resolve(long now) {
		// Engines that stopped calling in are forgotten, and their slots faded
		for (Iterator<Map.Entry<BlockPos, Heard>> it = HEARD.entrySet()
			.iterator(); it.hasNext();) {
			Heard heard = it.next()
				.getValue();
			if (now - heard.seenAt > FORGET_TICKS || heard.crank.isRemoved())
				it.remove();
		}
		int count = 0;
		waiting.clear();
		nearestWithout = null;
		for (Heard heard : HEARD.values()) {
			heard.counted = false;
			if (now - heard.seenAt > 1)
				continue;
			if (count == levels.length)
				levels = java.util.Arrays.copyOf(levels, count * 2);
			levels[count++] = heard.level;
			BlockPos at = heard.crank.getBlockPos();
			boolean noted = EngineNoteSound.isPlayingFor(at);
			if (heard.level > 0 && (!noted || EngineNoteSound.roomShare(at) > 0))
				waiting.add(heard);
			if (!noted && heard.level > 0 && (nearestWithout == null || heard.distance < nearestWithout.distance))
				nearestWithout = heard;
		}
		ceiling = EngineChorus.ceiling(levels, count);
		waiting.sort((a, b) -> Double.compare(a.distance, b.distance));
		handOver(now);
		SLOTS.choose(waiting);
		for (int s = 0; s < EngineRoomMix.SLOTS; s++) {
			Heard heard = SLOTS.get(s);
			if (heard != null && heard.synth == null) {
				// Seeded as its own note is, so it is the same voice in either
				heard.synth = new EngineNoteSynth(heard.crank.getBlockPos()
					.hashCode());
				heard.drive = new EngineNoteDrive();
			}
		}
		float fill = SLOTS.fill(waiting, heard -> heard.level);
		for (Heard heard : waiting)
			heard.counted = true;
		updateRoom(fill, SLOTS.occupied());
	}

	private static void updateRoom(float fill, int rendered) {
		if (rendered == 0 && (room == null || !room.alive()))
			return;
		if (room == null || !room.alive()) {
			room = EngineNoteSound.Room.start();
			if (room == null)
				return;
		}
		double weight = 0;
		double cx = 0;
		double cy = 0;
		double cz = 0;
		for (int s = 0; s < EngineRoomMix.SLOTS; s++) {
			Heard heard = SLOTS.get(s);
			if (heard == null) {
				room.mix.set(s, null, 0);
				continue;
			}
			EngineSummary summary = heard.crank.isRemoved() ? null : heard.crank.summary();
			boolean fading = SLOTS.isFading(s);
			if (summary == null || SLOTS.fadeTick(s)) {
				room.mix.set(s, null, 0);
				SLOTS.free(s);
				// Back in a slot later it starts afresh, not from where it left off
				heard.synth = null;
				continue;
			}
			heard.drive.drive(heard.synth, heard.crank, summary, heard.reportedPhase, heard.reportedAt);
			heard.reportedPhase = Double.NaN;
			heard.synth.setDistance((float) (heard.distance / EngineChorus.REACH));
			BlockPos at = heard.crank.getBlockPos();
			float handover = EngineNoteSound.isPlayingFor(at) ? EngineNoteSound.roomShare(at) : 1;
			// The heard level counts the mufflers, which the note already has in it
			float level = fading ? 0 : heard.level / Muffling.volume(summary.muffling()) * ceiling * fill * handover;
			room.mix.set(s, heard.synth, level);
			room.mix.setAir(s, NoteSurroundings.cutoff(heard.distance, heard.walls), NoteSurroundings.wallLevel(heard.walls));
			// An engine on a moving structure is kept far from where it is, so the middle is taken of where they all really are
			Vec3 from = SableCompat.worldPosition(heard.crank.getLevel(), source(at));
			double w = Math.max(0.01, heard.level * handover);
			weight += w;
			cx += w * from.x;
			cy += w * from.y;
			cz += w * from.z;
		}
		room.mix.setRoom(surroundings);
		if (weight > 0)
			room.moveTo(cx / weight, cy / weight, cz / weight);
	}
}
