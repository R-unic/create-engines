package com.createengines.content.blueprint;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.IntSupplier;

import org.jetbrains.annotations.Nullable;

import com.createengines.content.alternator.AlternatorBlockEntity;
import com.createengines.engine.sim.ChargeRate;
import com.createengines.engine.sim.SpringPack;
import com.createengines.content.flywheel.CastFlywheelBlockEntity;
import com.createengines.content.airflow.MafSensorBlockEntity;
import com.createengines.content.airflow.ThrottleBlockEntity;
import com.createengines.content.blackbox.BlackBoxBlockEntity;
import com.createengines.content.camshaft.CamshaftBlockEntity;
import com.createengines.content.crankshaft.CrankshaftBlockEntity;
import com.createengines.content.governor.GovernorBlockEntity;
import com.createengines.content.head.CylinderHeadBlockEntity;
import com.createengines.content.rotor.RotorHousingBlockEntity;
import com.createengines.content.sump.SumpBlockEntity;
import com.createengines.content.supercharger.SuperchargerBlockEntity;
import com.createengines.content.transmission.AutoGearbox;
import com.createengines.content.transmission.Gears;
import com.createengines.content.transmission.TransmissionBlockEntity;
import com.createengines.content.turbo.TurbochargerBlockEntity;
import com.createengines.engine.FlightRecorder;
import com.createengines.engine.sim.Turbocharger;
import com.createengines.registry.CEBlocks;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import com.tterrag.registrate.util.entry.BlockEntry;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Every setting a player can make on an engine block with a value box (a throw angle, a fuel metering, a boost limit),
 * by block and name. A blueprint records them and a server checks a pasted one against these ranges, so this table is the
 * one place that says which dials exist and what they allow.
 * <p>
 * The ranges are written from the constants the blocks themselves use. {@code BlueprintGameTests} reads the real value
 * box on every block and fails if a range here differs from it or a block has a dial that is not listed, so adding a
 * dial to a block without listing it here goes red instead of being left out of every blueprint.
 */
public final class EngineDials {

	/**
	 * One dial.
	 *
	 * @param name      what the blueprint calls it: lower case with underscores, never renamed once shipped
	 * @param min       smallest value the block's value box takes
	 * @param highest   largest value, asked each time: the governor's follows Create's RPM limit
	 * @param behaviour the dial on a block entity of that block, or null if this one has none (a plain crankshaft has no
	 *                  throw angle)
	 */
	public record Dial(String name, int min, IntSupplier highest, Function<BlockEntity, ScrollValueBehaviour> behaviour) {

		public Dial(String name, int min, int max, Function<BlockEntity, ScrollValueBehaviour> behaviour) {
			this(name, min, () -> max, behaviour);
		}

		/** Largest value the block's value box takes now. */
		public int max() {
			return highest.getAsInt();
		}

		/** The dial's value box on this block entity, or null if it has none. */
		public @Nullable ScrollValueBehaviour on(BlockEntity entity) {
			return behaviour.apply(entity);
		}
	}

	private static volatile Map<String, List<Dial>> table;

	private EngineDials() {
	}

	/** Forget the built table, so the next read picks up ranges that come from the config (the throttle's). */
	public static synchronized void invalidate() {
		table = null;
	}

	/** The dials of a block, by registry id such as {@code create_engines:cylinder_head}. Empty if it has none. */
	public static List<Dial> of(String blockId) {
		return table().getOrDefault(blockId, List.of());
	}

	/** The dial of that name on that block, or null. */
	public static @Nullable Dial find(String blockId, String name) {
		for (Dial dial : of(blockId))
			if (dial.name().equals(name))
				return dial;
		return null;
	}

	/** Registry ids of every block listed here. */
	public static Iterable<String> blocks() {
		return table().keySet();
	}

	private static synchronized Map<String, List<Dial>> table() {
		if (table != null)
			return table;
		Map<String, List<Dial>> t = new HashMap<>();
		int throwMax = 360 / CrankshaftBlockEntity.THROW_ANGLE_STEP - 1;
		Dial throwAngle = new Dial("throw_angle", 0, throwMax, e -> ((CrankshaftBlockEntity) e).throwAngle);
		add(t, CEBlocks.CRANK_THROW, throwAngle);
		add(t, CEBlocks.ROTOR_HOUSING, throwAngle, metering(e -> ((RotorHousingBlockEntity) e).metering));
		add(t, CEBlocks.CAM_LOBE, new Dial("lobe_angle", 0, (int) (360 / CamshaftBlockEntity.LOBE_ANGLE_STEP) - 1,
			e -> ((CamshaftBlockEntity) e).lobeAngle));
		Dial headMetering = metering(e -> ((CylinderHeadBlockEntity) e).metering);
		add(t, CEBlocks.CYLINDER_HEAD, headMetering);
		add(t, CEBlocks.PRECISION_CYLINDER_HEAD, headMetering);
		add(t, CEBlocks.THROTTLE,
			new Dial("idle", ThrottleBlockEntity.minIdle(), ThrottleBlockEntity.maxIdle(), e -> ((ThrottleBlockEntity) e).idle),
			new Dial("wide", ThrottleBlockEntity.minWide(), ThrottleBlockEntity.maxWide(), e -> ((ThrottleBlockEntity) e).wide));
		add(t, CEBlocks.MAF_SENSOR, new Dial("target", MafSensorBlockEntity.MIN_TARGET, MafSensorBlockEntity.MAX_TARGET,
			e -> ((MafSensorBlockEntity) e).target));
		add(t, CEBlocks.GOVERNOR, new Dial("target", GovernorBlockEntity.MIN_TARGET, GovernorBlockEntity::maxTarget,
			e -> ((GovernorBlockEntity) e).target));
		Dial turboLimit = new Dial("limit", TurbochargerBlockEntity.MIN_LIMIT, TurbochargerBlockEntity.MAX_LIMIT,
			e -> ((TurbochargerBlockEntity) e).limit);
		Dial antiLag = new Dial("anti_lag", 0, Turbocharger.MAX_ANTI_LAG, e -> ((TurbochargerBlockEntity) e).antiLag);
		add(t, CEBlocks.TURBOCHARGER, turboLimit, antiLag);
		add(t, CEBlocks.VARIABLE_TURBOCHARGER, turboLimit, antiLag);
		Dial blowerLimit = new Dial("limit", SuperchargerBlockEntity.MIN_LIMIT, SuperchargerBlockEntity.MAX_LIMIT,
			e -> ((SuperchargerBlockEntity) e).limit);
		add(t, CEBlocks.SUPERCHARGER, blowerLimit);
		add(t, CEBlocks.VARIABLE_SUPERCHARGER, blowerLimit);
		add(t, CEBlocks.TRANSMISSION,
			new Dial("gear", Gears.NEUTRAL, Gears.AUTO, e -> ((TransmissionBlockEntity) e).panel),
			new Dial("shift_profile", 0, AutoGearbox.Profile.values().length - 1, e -> ((TransmissionBlockEntity) e).shiftProfile));
		add(t, CEBlocks.SUMP,
			new Dial("oil_exchange", 0, SumpBlockEntity.EXCHANGE_RATES.length - 1, e -> ((SumpBlockEntity) e).exchange),
			new Dial("signal_tank", 0, SumpBlockEntity.SIGNAL_TANKS - 1, e -> ((SumpBlockEntity) e).signalTank));
		add(t, CEBlocks.ALTERNATOR, new Dial("charge_rate", ChargeRate.MIN_SETTING, ChargeRate.MAX_SETTING,
			e -> ((AlternatorBlockEntity) e).chargeRateDial));
		add(t, CEBlocks.DUAL_MASS_FLYWHEEL, new Dial("spring_pack", SpringPack.MIN_SETTING, SpringPack.MAX_SETTING,
			e -> ((CastFlywheelBlockEntity) e).springPack));
		add(t, CEBlocks.BLACK_BOX,
			new Dial("replay", 0, FlightRecorder.REPLAY_STEPS, e -> ((BlackBoxBlockEntity) e).replay));
		table = t;
		return t;
	}

	private static Dial metering(Function<BlockEntity, ScrollValueBehaviour> behaviour) {
		return new Dial("fuel_metering", CylinderHeadBlockEntity.MIN_METERING, CylinderHeadBlockEntity.MAX_METERING, behaviour);
	}

	private static void add(Map<String, List<Dial>> t, BlockEntry<? extends Block> block, Dial... dials) {
		t.put(block.getId()
			.toString(), new ArrayList<>(List.of(dials)));
	}
}
