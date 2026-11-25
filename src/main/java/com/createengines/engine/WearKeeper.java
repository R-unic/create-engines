package com.createengines.engine;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.DoubleSupplier;

import org.jetbrains.annotations.Nullable;

import com.createengines.content.cylinder.CylinderBlock;
import com.createengines.content.cylinder.CylinderBlockEntity;
import com.createengines.content.head.CylinderHeadBlockEntity;
import com.createengines.content.rotor.RotorHousingBlockEntity;
import com.createengines.engine.sim.ApexSeals;
import com.createengines.engine.sim.CycleStats;
import com.createengines.engine.sim.CylinderModel;
import com.createengines.engine.sim.EngineSimulation;
import com.createengines.engine.sim.Knock;
import com.createengines.engine.sim.Wear;
import com.createengines.engine.sim.WearFailures;
import com.createengines.engine.sim.WearTotal;
import com.createengines.engine.sim.Tuning;
import com.createengines.engine.topology.CylinderFault;
import com.createengines.engine.topology.EngineLayout;
import com.createengines.engine.topology.EngineLayout.Cylinder;
import com.createengines.registry.CESounds;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The wear mode on a running engine (see {@link Wear}, which holds the numbers): reads how worn each cylinder's piston and gasket are
 * into its model, wears them as the engine runs, and throws a rod or blows a gasket when the chance comes up. The wear itself is saved
 * in the cylinder and head block entities, so it belongs to the parts and survives the engine being rebuilt around them.
 * <p>
 * Only an engine whose controller has {@code Tuning.wearMode} on ever makes one of these. A rotor housing has no piston, rod or head
 * gasket: what wears there is its set of apex seals. With the mode off nothing here runs.
 */
public final class WearKeeper {

	/** Server ticks between one wearing step and the next. Wear is slow, so it is worked out in lumps and not every tick. */
	public static final int INTERVAL = 20;
	/**
	 * Crank speed under which an engine counts as stopped: nothing knocks (it is not firing, whatever its last cycle says) and no part
	 * fails (a rod is not thrown, nor a gasket or a set of apex seals lost, in an engine that is standing still).
	 */
	private static final double TURNING_FROM_RPM = 1;

	private final EngineLayout layout;
	private final List<CylinderModel> models;

	public WearKeeper(EngineLayout layout, List<CylinderModel> models) {
		this.layout = layout;
		this.models = models;
	}

	/** A film that is used in place of the engine's own, or null: a test sets it to run an engine dry or keep it oiled. */
	public static volatile @Nullable DoubleSupplier filmOverride;

	/**
	 * The wear mode's faults of this layout, by cylinder position: a blown gasket, a broken head and a thrown rod's wreck, each of which
	 * stops its cylinder. The scanner does not read block entities, so the controller adds these to what it found.
	 */
	public static Map<BlockPos, Integer> wearFaults(Level level, EngineLayout layout) {
		Map<BlockPos, Integer> faults = new HashMap<>();
		for (Cylinder cylinder : layout.cylinders()) {
			if (cylinder.rotary()) {
				// One set of apex seals serves a housing's three chambers: a failed set stops all of them
				if (rotor(level, cylinder) instanceof RotorHousingBlockEntity housing && housing.apexFailed())
					faults.put(cylinder.pos(), CylinderFault.APEX_FAILED.bit());
				continue;
			}
			int bits = 0;
			CylinderHeadBlockEntity head = head(level, cylinder);
			if (head != null && head.broken())
				bits |= CylinderFault.BROKEN_HEAD.bit();
			else if (head != null && head.blown())
				bits |= CylinderFault.BLOWN_GASKET.bit();
			CylinderBlockEntity body = body(level, cylinder);
			if (body != null && body.thrown())
				bits |= CylinderFault.THROWN_ROD.bit();
			if (body != null && body.seized())
				bits |= CylinderFault.SEIZED.bit();
			if (bits != 0)
				faults.put(cylinder.pos(), bits);
		}
		return faults;
	}

	private static @Nullable CylinderHeadBlockEntity head(Level level, Cylinder cylinder) {
		return cylinder.head() != null && level.isLoaded(cylinder.head())
			&& LoadedBlocks.blockEntity(level, cylinder.head()) instanceof CylinderHeadBlockEntity head ? head : null;
	}

	private static @Nullable RotorHousingBlockEntity rotor(Level level, Cylinder cylinder) {
		return level.isLoaded(cylinder.pos()) && LoadedBlocks.blockEntity(level, cylinder.pos()) instanceof RotorHousingBlockEntity housing
			? housing : null;
	}

	private static @Nullable CylinderBlockEntity body(Level level, Cylinder cylinder) {
		return level.isLoaded(cylinder.pos()) && LoadedBlocks.blockEntity(level, cylinder.pos()) instanceof CylinderBlockEntity body
			? body : null;
	}

	/**
	 * How worn the whole build is, as the plain mean of every part that wears: each fitted piston and rod, each head's gasket, each rotor housing's
	 * apex seals and each battery, 0 new to 1 spent, or {@link WearTotal#NONE} when there is nothing to count. A part that is gone counts as spent: a
	 * thrown rod's piston and rod, a blown, missing or broken head's gasket, a failed set of seals. A cylinder with no piston or rod fitted counts
	 * nothing for the one that is not there (an unfinished build is not a worn one).
	 */
	public static float total(Level level, EngineLayout layout) {
		java.util.ArrayList<Double> parts = new java.util.ArrayList<>();
		for (Cylinder cylinder : layout.cylinders()) {
			if (cylinder.rotary()) {
				if (level.isLoaded(cylinder.pos()) && LoadedBlocks.blockEntity(level, cylinder.pos()) instanceof com.createengines.content.rotor.RotorHousingBlockEntity housing)
					parts.add(housing.apexFailed() ? 1.0 : housing.apexWear());
				continue;
			}
			CylinderBlockEntity body = body(level, cylinder);
			if (body != null) {
				if (body.thrown()) {
					parts.add(1.0);
					parts.add(1.0);
				} else {
					BlockState state = body.getBlockState();
					if (state.getValue(CylinderBlock.HAS_PISTON))
						parts.add(body.pistonWear());
					if (state.getValue(CylinderBlock.HAS_ROD))
						parts.add(body.rodWear());
				}
			}
			CylinderHeadBlockEntity head = head(level, cylinder);
			if (head != null)
				parts.add(head.broken() || head.blown() ? 1.0 : head.gasketWear());
		}
		for (BlockPos pos : layout.batteries())
			if (level.isLoaded(pos) && LoadedBlocks.blockEntity(level, pos) instanceof com.createengines.content.battery.BatteryBlockEntity battery)
				parts.add(battery.wear());
		double[] wears = new double[parts.size()];
		for (int i = 0; i < wears.length; i++)
			wears[i] = parts.get(i);
		return WearTotal.mean(wears);
	}

	/**
	 * How many parts of each kind have failed, from {@link #wearFaults} (see {@link WearFailures}): rods thrown, gaskets blown, pistons seized and sets of
	 * apex seals failed. A thrown rod breaks its head too, which is the same failure and not two, and a rotor housing is one set of seals whatever its chambers.
	 */
	public static int failureCounts(Map<BlockPos, Integer> faults) {
		int rods = 0, gaskets = 0, seizures = 0, apex = 0;
		for (int bits : faults.values()) {
			if ((bits & CylinderFault.THROWN_ROD.bit()) != 0)
				rods++;
			else if ((bits & CylinderFault.BLOWN_GASKET.bit()) != 0)
				gaskets++;
			if ((bits & CylinderFault.SEIZED.bit()) != 0)
				seizures++;
			if ((bits & CylinderFault.APEX_FAILED.bit()) != 0)
				apex++;
		}
		return WearFailures.pack(rods, gaskets, seizures, apex);
	}

	/** Gives every working cylinder the state its saved wear says. */
	public void refresh(Level level) {
		for (int i = 0; i < models.size(); i++) {
			CylinderModel model = models.get(i);
			Cylinder cylinder = layout.cylinders()
				.get(i);
			if (model != null && cylinder.rotary()) {
				RotorHousingBlockEntity housing = rotor(level, cylinder);
				model.wear = housing == null ? Wear.NONE : ApexSeals.state(housing.apexWear());
				continue;
			}
			if (model == null || cylinder.rotary())
				continue;
			CylinderBlockEntity body = body(level, cylinder);
			CylinderHeadBlockEntity head = head(level, cylinder);
			model.wear = body == null || head == null ? Wear.NONE
				: Wear.of(body.pistonWear(), head.gasket(), head.gasketWear(), head.hasGasketItem());
		}
	}

	/**
	 * The state of an engine with the wear mode off: nothing wears and nothing is required, but a gasket that has been fitted still seals its head
	 * (a gasket does something whether or not the mode is on), so a cylinder whose head has one reads that tier's seal, and every other cylinder
	 * runs exactly as it did before gaskets existed. Also what a cylinder goes back to when the mode is switched off.
	 */
	public void applyFitted(Level level) {
		for (int i = 0; i < models.size(); i++) {
			CylinderModel model = models.get(i);
			Cylinder cylinder = layout.cylinders()
				.get(i);
			if (model == null)
				continue;
			CylinderHeadBlockEntity head = cylinder.rotary() ? null : head(level, cylinder);
			model.wear = head != null && head.hasGasketItem() ? Wear.fitted(head.gasket()) : Wear.NONE;
		}
	}

	/**
	 * Wears the engine by one interval and rolls for a failure.
	 *
	 * @param rpm         how fast the crank is turning
	 * @param temperature the block's temperature, 0 ambient to 1 hot (see {@code Thermal})
	 * @param film        how well oiled the parts are, 0 dry to 1
	 * @return whether a part failed: the engine is being rebuilt around the gap, and the caller stops for this tick
	 */
	public boolean tick(Level level, double rpm, double temperature, double film) {
		double dt = INTERVAL * EngineSimulation.TICK;
		DoubleSupplier forcedFilm = filmOverride;
		if (forcedFilm != null)
			film = forcedFilm.getAsDouble();
		// One cry a step, however many cylinders are running dry together
		boolean cried = false;
		for (int i = 0; i < models.size(); i++) {
			CylinderModel model = models.get(i);
			Cylinder cylinder = layout.cylinders()
				.get(i);
			if (model != null && cylinder.rotary()) {
				if (wearApex(level, cylinder, model, rpm, temperature, film, dt))
					return true;
				continue;
			}
			if (model == null || cylinder.rotary())
				continue;
			CylinderBlockEntity body = body(level, cylinder);
			CylinderHeadBlockEntity head = head(level, cylinder);
			if (body == null || head == null || head.broken() || body.thrown() || body.seized())
				continue;
			BlockState state = body.getBlockState();
			boolean piston = state.getValue(CylinderBlock.HAS_PISTON);
			boolean rod = state.getValue(CylinderBlock.HAS_ROD);
			boolean forged = state.getValue(CylinderBlock.FORGED_PISTON);
			// A stopped crank's cylinders keep the figures of their last cycle, which is not knocking now
			CycleStats last = model.lastCycle();
			double early = rpm >= TURNING_FROM_RPM && last.result.fired() ? last.preignition : 0;
			body.addWear(piston ? Wear.pistonWear(rpm, temperature, film, dt, forged) + Knock.pistonWear(early, rpm, dt, forged) : 0,
				rod ? Wear.rodWear(rpm, film, dt) : 0);
			head.addGasketWear(Wear.gasketWear(head.gasket(), rpm, temperature, model.intakePressure, dt));
			model.wear = Wear.of(body.pistonWear(), head.gasket(), head.gasketWear(), head.hasGasketItem());
			if (!piston)
				body.setDryTime(0);
			else {
				double before = body.dryTime();
				body.setDryTime(Wear.dryTime(before, film, rpm, dt, forged));
				if (Wear.seizes(body.dryTime())) {
					seize(level, cylinder, body);
					return true;
				}
				if (!cried && Wear.warns(before, body.dryTime())) {
					warnDry(level, cylinder, body.dryTime() / Wear.SEIZE_AFTER);
					cried = true;
				}
			}
			if (rpm >= TURNING_FROM_RPM && roll(level, Wear.failureChance(body.rodWear(), rpm, dt))) {
				throwRod(level, cylinder, body);
				return true;
			}
			if (rpm >= TURNING_FROM_RPM && roll(level, Wear.failureChance(head.gasketWear(), rpm, dt))) {
				blowGasket(level, cylinder, head);
				return true;
			}
		}
		return false;
	}

	/**
	 * A rotor housing's apex seals for one interval (see {@link ApexSeals}). One set serves the housing's three chambers, so it wears, and
	 * may fail, once for the housing (on its first chamber); every chamber reads how worn it is. Returns whether the set failed.
	 */
	private boolean wearApex(Level level, Cylinder cylinder, CylinderModel model, double rpm, double temperature, double film, double dt) {
		RotorHousingBlockEntity housing = rotor(level, cylinder);
		if (housing == null || housing.apexFailed())
			return false;
		boolean first = cylinder.chamber() == 0;
		if (first)
			housing.addApexWear(ApexSeals.wear(rpm, temperature, film, dt));
		model.wear = ApexSeals.state(housing.apexWear());
		if (first && rpm >= TURNING_FROM_RPM && roll(level, ApexSeals.failureChance(housing.apexWear(), rpm, dt))) {
			failApex(level, cylinder, housing);
			return true;
		}
		return false;
	}

	/** The seals go: a snap and a clatter, sparks and a puff of smoke, and the housing stops until a new set is fitted. */
	static void failApex(Level level, Cylinder cylinder, RotorHousingBlockEntity housing) {
		housing.failApex();
		BlockPos pos = cylinder.pos();
		level.playSound(null, pos, CESounds.APEX_FAIL.get(), SoundSource.BLOCKS, 1.2f, 1.0f);
		smoke(level, pos);
		if (level instanceof ServerLevel server)
			server.sendParticles(ParticleTypes.ELECTRIC_SPARK, pos.getX() + 0.5, pos.getY() + 0.7, pos.getZ() + 0.5, 20, 0.35, 0.35, 0.35, 0.2);
	}

	/** A dry piston crying out before it seizes: higher the nearer it is, {@code share} being how far it has got. */
	private static void warnDry(Level level, Cylinder cylinder, double share) {
		level.playSound(null, cylinder.pos(), CESounds.SEIZE_WARNING.get(), SoundSource.BLOCKS, 0.6f, (float) (0.8 + 0.3 * share));
	}

	/** The piston welds itself to the bore: a screech, a clunk, a puff of smoke. The parts stay; the cylinder locks and drags on the crank. */
	static void seize(Level level, Cylinder cylinder, CylinderBlockEntity body) {
		body.seize();
		BlockPos pos = cylinder.pos();
		level.playSound(null, pos, CESounds.PISTON_SEIZE.get(), SoundSource.BLOCKS, 1.2f, 1.0f);
		if (level instanceof ServerLevel server)
			server.sendParticles(ParticleTypes.LARGE_SMOKE, pos.getX() + 0.5, pos.getY() + 0.7, pos.getZ() + 0.5, 16, 0.3, 0.3, 0.3, 0.02);
	}

	/** The roll a failure is decided by, 0 to 1; null is the level's own random. A test sets it to force or forbid a failure. */
	public static volatile @Nullable DoubleSupplier roller;

	private static boolean roll(Level level, double chance) {
		if (!(chance > 0))
			return false;
		DoubleSupplier forced = roller;
		return (forced != null ? forced.getAsDouble() : level.random.nextDouble()) < chance;
	}

	/**
	 * The rod goes through the head. The piston and rod are gone for good (nothing is dropped), their wreck stays in the cylinder as a
	 * thrown flag that only a Service Kit clears, and the head breaks and has to be replaced. The cylinder misfires, which is just
	 * what a cylinder with no piston does. Then the spectacle: a bang and a clatter, a burst of sparks, oil and smoke, and a flame where
	 * there is fuel (only with {@code Tuning.fuelFire} on, so a server that has fire off gets none).
	 */
	static void throwRod(Level level, Cylinder cylinder, CylinderBlockEntity body) {
		BlockPos pos = cylinder.pos();
		BlockState state = body.getBlockState();
		boolean fuel = fuelAt(level, cylinder);
		level.setBlockAndUpdate(pos, state.setValue(CylinderBlock.HAS_PISTON, false)
			.setValue(CylinderBlock.FORGED_PISTON, false)
			.setValue(CylinderBlock.HAS_ROD, false));
		body.throwRod();
		CylinderHeadBlockEntity head = head(level, cylinder);
		if (head != null)
			head.breakHead();
		level.playSound(null, pos, CESounds.ROD_THROWN.get(), SoundSource.BLOCKS, 1.6f, 1.0f);
		smoke(level, pos);
		if (level instanceof ServerLevel server) {
			BlockPos at = cylinder.head() != null ? cylinder.head() : pos;
			double x = at.getX() + 0.5, y = at.getY() + 0.7, z = at.getZ() + 0.5;
			server.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, y, z, 30, 0.35, 0.35, 0.35, 0.25);
			server.sendParticles(ParticleTypes.LAVA, x, y, z, 6, 0.3, 0.3, 0.3, 0);
			server.sendParticles(ParticleTypes.SQUID_INK, x, y, z, 24, 0.3, 0.3, 0.3, 0.15);
			if (fuel && Tuning.fuelFire)
				server.sendParticles(ParticleTypes.FLAME, x, y, z, 18, 0.25, 0.25, 0.25, 0.06);
		}
	}

	/** Whether this cylinder's head holds fuel, which is what a flame would burn. */
	private static boolean fuelAt(Level level, Cylinder cylinder) {
		CylinderHeadBlockEntity head = head(level, cylinder);
		return head != null && !head.tank()
			.isEmpty();
	}

	static void blowGasket(Level level, Cylinder cylinder, CylinderHeadBlockEntity head) {
		head.blow();
		level.playSound(null, cylinder.head(), CESounds.GASKET_BLOW.get(), SoundSource.BLOCKS, 1.0f, 1.0f);
		smoke(level, cylinder.head());
	}

	/** The smoke of a failure: a bang's worth of dark cloud (the sound is the caller's, which is its own). */
	private static void smoke(Level level, BlockPos at) {
		if (level instanceof ServerLevel server) {
			server.sendParticles(ParticleTypes.LARGE_SMOKE, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 12, 0.3, 0.3, 0.3, 0.02);
			server.sendParticles(ParticleTypes.CLOUD, at.getX() + 0.5, at.getY() + 0.8, at.getZ() + 0.5, 8, 0.3, 0.2, 0.3, 0.05);
		}
	}
}
