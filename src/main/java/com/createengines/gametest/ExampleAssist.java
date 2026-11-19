package com.createengines.gametest;

import java.util.List;

import com.createengines.api.EngineBuild;
import com.createengines.api.EngineSourceProvider;
import com.createengines.engine.sim.AbstractTorqueSource;
import com.createengines.engine.sim.StatusLine;
import com.createengines.engine.sim.TorqueSource;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The worked example in docs/ADDONS.md. It is real code that is built with the mod and run by {@code AddonExampleGameTests},
 * and a test holds the text in the guide to the part of this file between the {@code docs:begin} and {@code docs:end}
 * lines, so the example the guide shows is always one that compiles and works.
 * <p>
 * An <i>assist motor</i> is a block an addon would register. Every one touching a crankshaft segment adds a little steady
 * torque to that engine's crank.
 */
public final class ExampleAssist {

	private ExampleAssist() {
	}

	// docs:begin
	/** Torque each assist motor puts on the crank, in the simulation's units. A crude single cylinder makes about 0.4. */
	static final double TORQUE_PER_MOTOR = 0.15;

	/** Adds an {@link AssistMotor} to every engine that has one of {@code motor} touching its crankshaft. */
	public static final class AssistProvider implements EngineSourceProvider {
		private final Block motor;

		public AssistProvider(Block motor) {
			this.motor = motor;
		}

		@Override
		public List<TorqueSource> sourcesFor(Level level, EngineBuild build) {
			int motors = 0;
			for (BlockPos segment : build.segments())
				for (Direction side : Direction.values())
					if (level.getBlockState(segment.relative(side)).is(motor))
						motors++;
			// A new source each time: the engine drops the old ones whenever it is rebuilt
			return motors == 0 ? List.of() : List.of(new AssistMotor(motors));
		}

		/** Placing or breaking one of our blocks beside an engine makes the engine ask again; nothing else to call. */
		@Override
		public boolean watches(BlockState state) {
			return state.is(motor);
		}

		/** Touching means one block from the crankshaft. */
		@Override
		public int reach() {
			return 1;
		}
	}

	/** The motors of one engine, as one source. */
	static final class AssistMotor extends AbstractTorqueSource {
		private final int motors;
		private final double torque;

		AssistMotor(int motors) {
			// Weight and drag: a little inertia and friction for each motor
			super(0.1 * motors, 0.02 * motors, 0.0005 * motors);
			this.motors = motors;
			this.torque = TORQUE_PER_MOTOR * motors;
		}

		/** Work is torque times the angle turned, and it is what the engine's energy balance adds up. */
		@Override
		public double advance(double angle, double rotaryAngle, int direction, double delta, double dt) {
			return torque * Math.toRadians(delta);
		}

		/** What it would do in a whole 720 degree cycle, which is how the goggles' torque figure is worked out. */
		@Override
		public double availableWork() {
			return torque * Math.toRadians(720);
		}

		/**
		 * Left false on purpose: {@code fired()} is what makes an engine count as running under its own power, and a motor
		 * that assists a cylinder should not make a dead engine look alive.
		 */
		@Override
		public boolean fired() {
			return false;
		}

		/** A line for the goggles on the crankshaft. The key is looked up in the addon's own lang file. */
		@Override
		public List<StatusLine> status() {
			return List.of(StatusLine.of("example_addon.assist", StatusLine.Tone.NOTE, String.valueOf(motors)));
		}
	}
	// docs:end
}
