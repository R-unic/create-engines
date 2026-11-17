package com.createengines.content.blueprint;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.createengines.engine.blueprint.Blueprint;
import com.createengines.engine.blueprint.BlueprintCodec;
import com.createengines.engine.blueprint.BlueprintDiff;
import com.createengines.engine.blueprint.BlueprintDiff.DialChange;
import com.createengines.engine.blueprint.BlueprintDiff.Mismatch;
import com.createengines.engine.blueprint.BlueprintException;
import com.createengines.engine.blueprint.BlueprintRules;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Copies a recorded blueprint's tune onto an engine that is the same build. The blueprint is first decoded and put
 * through the server's rules, because the stack it came from may have been made by anyone; then the engine is recorded as
 * it stands and compared (see {@link BlueprintDiff}). Only if the build is the same are the dials set, and each is set
 * through the block's own value box so it is clamped and notifies the engine as a player's change would. Nothing else is
 * touched: no block is placed, broken or turned.
 * <p>
 * A blueprint holds states such as an axis and a facing as built, so the engine has to be the same build. A copy built
 * turned a quarter, a half or three quarters of a turn (see {@link BlueprintTurner}), mirrored where a turn needs it, is
 * recognised as the same build and the dials are set on it. Any other mirror image is refused as a different build.
 */
public final class BlueprintApplier {

	/** How an attempt ended. */
	public enum Status {
		/** At least one dial was set. */
		APPLIED,
		/** Same build, every dial already as recorded. */
		ALREADY_SET,
		/** The block clicked is not part of a built engine. */
		NO_ENGINE,
		/** Part of the engine is in an unloaded chunk. */
		INCOMPLETE,
		/** The engine is not the same build; {@link Result#mismatches()} says where. */
		DIFFERENT,
		/** The blueprint itself was damaged or broke the server's rules, so nothing was read from it. */
		REFUSED
	}

	/**
	 * @param status     how it ended
	 * @param changed    dials set
	 * @param mismatches where the build differs, at most {@link #MAX_MISMATCHES}, for {@link Status#DIFFERENT}
	 * @param detail     what was wrong with a refused blueprint
	 */
	public record Result(Status status, int changed, List<Mismatch> mismatches, String detail) {

		static Result of(Status status) {
			return new Result(status, 0, List.of(), "");
		}
	}

	/** Most differences reported when the build is not the same. */
	public static final int MAX_MISMATCHES = 5;

	private BlueprintApplier() {
	}

	/** Applies the blueprint in {@code recorded} to the engine the block at {@code clicked} belongs to. */
	public static Result apply(Level level, BlockPos clicked, RecordedBlueprint recorded) {
		Blueprint blueprint;
		try {
			blueprint = BlueprintCodec.decode(recorded.text());
			BlueprintRules.check(blueprint, EngineBlueprintPolicy.INSTANCE);
		} catch (BlueprintException e) {
			return new Result(Status.REFUSED, 0, List.of(), String.valueOf(e.getMessage()));
		}
		return apply(level, clicked, blueprint);
	}

	/** Applies an already checked blueprint. */
	public static Result apply(Level level, BlockPos clicked, Blueprint blueprint) {
		BlueprintRecorder.Result now = BlueprintRecorder.record(level, clicked, "", "");
		if (!now.ok())
			return Result.of(now.failure() == BlueprintRecorder.Failure.INCOMPLETE ? Status.INCOMPLETE : Status.NO_ENGINE);
		Blueprint actual = comparable(blueprint, now.blueprint());
		List<Mismatch> mismatches = BlueprintDiff.compare(blueprint, actual, MAX_MISMATCHES);
		if (!mismatches.isEmpty()) {
			// Not built the way it was recorded: it may be the same engine turned (or mirrored, where a turn needs it)
			Blueprint turned = sameBuildTurned(blueprint, actual);
			if (turned == null)
				return new Result(Status.DIFFERENT, 0, mismatches, "");
			blueprint = turned;
		}
		BlockPos origin = now.origin();
		int changed = 0;
		for (DialChange change : BlueprintDiff.dialChanges(blueprint, actual)) {
			ScrollValueBehaviour behaviour = behaviour(level, origin, change);
			if (behaviour == null)
				continue;
			behaviour.setValue(change.to());
			changed++;
		}
		return new Result(changed > 0 ? Status.APPLIED : Status.ALREADY_SET, changed, List.of(), "");
	}

	/**
	 * The blueprint turned so that it is the same build as the engine, with its crankshaft where the engine's primary crankshaft
	 * is (the engine's primary is the first of its crank line in the scanner's order, which is not always the block the turned
	 * blueprint calls its origin), or null if no turn does it.
	 */
	static @Nullable Blueprint sameBuildTurned(Blueprint blueprint, Blueprint actual) {
		Blueprint.Part anchor = actual.parts()
			.stream()
			.filter(p -> p.x() == 0 && p.y() == 0 && p.z() == 0)
			.findFirst()
			.orElse(null);
		if (anchor == null)
			return null;
		for (Rotation rotation : BlueprintTurner.TURNS) {
			if (rotation == Rotation.NONE && BlueprintTurner.mirrorFor(blueprint, rotation) == Mirror.NONE)
				continue;
			Blueprint turned = BlueprintTurner.turn(blueprint, rotation);
			if (turned == null)
				continue;
			for (Blueprint.Part candidate : turned.parts()) {
				if (!candidate.block()
					.equals(anchor.block()) || !candidate.state()
						.equals(anchor.state()))
					continue;
				Blueprint shifted = shifted(turned, -candidate.x(), -candidate.y(), -candidate.z());
				if (BlueprintDiff.sameBuild(shifted, actual))
					return shifted;
			}
		}
		return null;
	}

	private static Blueprint shifted(Blueprint blueprint, int dx, int dy, int dz) {
		List<Blueprint.Part> parts = new java.util.ArrayList<>();
		for (Blueprint.Part part : blueprint.parts())
			parts.add(new Blueprint.Part(part.x() + dx, part.y() + dy, part.z() + dz, part.block(), part.state(), part.dials()));
		return new Blueprint(blueprint.name(), blueprint.builder(), parts, blueprint.figures());
	}

	/**
	 * The engine as it is compared with the blueprint. A blueprint recorded before Create's blocks were recorded holds none
	 * of them, so the engine's Create blocks are left out of the comparison and such a blueprint still applies to the
	 * engine it was recorded from. A blueprint that holds any Create block is compared with every block.
	 */
	static Blueprint comparable(Blueprint blueprint, Blueprint actual) {
		boolean hasCreate = blueprint.parts()
			.stream()
			.anyMatch(p -> p.block()
				.startsWith(EngineBlueprintPolicy.CREATE + ":"));
		if (hasCreate)
			return actual;
		List<Blueprint.Part> ours = actual.parts()
			.stream()
			.filter(p -> !p.block()
				.startsWith(EngineBlueprintPolicy.CREATE + ":"))
			.toList();
		return new Blueprint(actual.name(), actual.builder(), ours, actual.figures());
	}

	private static @Nullable ScrollValueBehaviour behaviour(Level level, BlockPos origin, DialChange change) {
		EngineDials.Dial dial = EngineDials.find(change.block(), change.dial());
		BlockEntity entity = level.getBlockEntity(origin.offset(change.x(), change.y(), change.z()));
		return dial == null || entity == null ? null : dial.on(entity);
	}
}
