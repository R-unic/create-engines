package com.createengines.content.head;

import java.util.Set;

import com.createengines.content.crankshaft.CrankshaftBlockEntity;
import com.createengines.content.rotor.RotorHousingBlockEntity;
import com.createengines.engine.EngineController;
import com.createengines.engine.sim.Tuning;
import com.createengines.engine.topology.EngineLayout;
import com.createengines.engine.topology.EngineScanner;
import com.createengines.engine.LoadedBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;

/**
 * The opt-in {@code features.meterAllHeads} setting: when a player sets the fuel metering of one head (or rotor housing), every
 * other injector of that engine is set to the same figure. It writes each head's own dial and never locks one, so every head
 * stays editable afterwards, and heads held by a MAF sensor or a closed rack are left alone, as {@code tuneFuel} leaves them.
 * Off by default, and nothing saved changes until a player turns a dial with it on.
 */
public final class MeteringFollow {

	/** Set while the follow itself (or {@code tuneFuel}) is writing dials, so those writes do not start another. */
	private static final ThreadLocal<Boolean> WRITING = ThreadLocal.withInitial(() -> false);

	private MeteringFollow() {
	}

	/** Runs {@code writes} with the follow switched off, for code that sets many dials itself (the fuel tuner). */
	public static void withoutFollow(Runnable writes) {
		boolean was = WRITING.get();
		WRITING.set(true);
		try {
			writes.run();
		} finally {
			WRITING.set(was);
		}
	}

	/**
	 * Called when the metering dial of the head at {@code source} changes to {@code value}. Does nothing unless the setting is on,
	 * on the server, and not while another write is under way. Returns how many other heads were set.
	 */
	public static int onChanged(Level level, BlockPos source, int value) {
		if (!Tuning.meterAllHeads || level == null || level.isClientSide || WRITING.get())
			return 0;
		Set<BlockPos> seeds = EngineScanner.crankSeeds(level, source);
		EngineLayout layout = seeds.isEmpty() ? null : EngineScanner.scan(level, seeds.iterator()
			.next());
		if (layout == null || !(LoadedBlocks.blockEntity(level, layout.primary()) instanceof CrankshaftBlockEntity crank)
			|| crank.controller() == null)
			return 0;
		int[] set = {0};
		withoutFollow(() -> set[0] = crank.controller()
			.setAllMetering(level, source, value));
		return set[0];
	}

	/** The metering dial of the head or rotor housing at {@code pos}, or null if the block is neither. */
	public static ScrollValueBehaviour dial(Level level, BlockPos pos) {
		Object be = LoadedBlocks.blockEntity(level, pos);
		if (be instanceof CylinderHeadBlockEntity head)
			return head.metering;
		if (be instanceof RotorHousingBlockEntity housing)
			return housing.metering;
		return null;
	}
}
