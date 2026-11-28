package com.createengines.ponder;

import com.createengines.content.camshaft.CamshaftBlockEntity;
import com.createengines.content.crankshaft.CrankshaftBlockEntity;
import com.createengines.engine.CrankGeometry;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;

import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;

/** Timing and block settings shared by the Ponder scene classes. */
final class SceneHelpers {

	private SceneHelpers() {
	}

	/** Lobe angle step that times a cylinder bored straight up from a throw at 0° on an X-axis crank. */
	static final int LOBE_FOR_UPRIGHT_CYLINDER = 18;
	static final int THROW_HALF_TURN = 180 / CrankshaftBlockEntity.THROW_ANGLE_STEP;
	static final int LOBE_HALF_TURN = (int) (180 / CamshaftBlockEntity.LOBE_ANGLE_STEP);

	static void setThrow(CreateSceneBuilder scene, Selection selection, int value) {
		scene.world()
			.modifyBlockEntityNBT(selection, CrankshaftBlockEntity.class, nbt -> nbt.putInt("ScrollValue", value));
	}

	static void setLobe(CreateSceneBuilder scene, Selection selection, int value) {
		scene.world()
			.modifyBlockEntityNBT(selection, CamshaftBlockEntity.class, nbt -> nbt.putInt("ScrollValue", value));
	}

	/**
	 * The lobe step that times a cylinder bored along {@code bore} from a throw set to {@code throwSteps}, so that the
	 * scene's angles are worked out the way a player works them out. {@code secondTurn} is the lobe 180° on, which fires
	 * the cylinder a crank revolution later.
	 */
	static int lobeFor(int throwSteps, Direction bore, boolean secondTurn) {
		double tdc = CrankGeometry.topDeadCentreAngle(Axis.X, throwSteps * CrankshaftBlockEntity.THROW_ANGLE_STEP, bore);
		double lobe = ((tdc / 2) % 180 + 180) % 180 + (secondTurn ? 180 : 0);
		int steps = (int) (360 / CamshaftBlockEntity.LOBE_ANGLE_STEP);
		return (int) Math.round(lobe / CamshaftBlockEntity.LOBE_ANGLE_STEP) % steps;
	}

	/** Crank speed of the firing pages. 7.2° a tick makes a 720° four-stroke cycle exactly 100 ticks, 180° exactly 25. */
	static final int TIMING_SPEED = 24;
	static final float TIMING_DEGREES_PER_TICK = TIMING_SPEED * 3 / 10f;
	static final int FLASH_TICKS = 10;

	/** Outlines the cylinder and head in the given column of the inline four, as a power stroke. */
	static void flash(CreateSceneBuilder scene, SceneBuildingUtil util, PonderPalette color, int column) {
		scene.overlay()
			.showOutline(color, "firing" + column, util.select()
				.fromTo(column, 2, 3, column, 3, 3), FLASH_TICKS);
	}

	/**
	 * Flashes cylinders at the crank angles they fire at, in real time for the page's crank speed, for whole 720°
	 * cycles. {@code angles} ascend within one cycle and {@code columns[i]} are the cylinders firing at {@code angles[i]}.
	 * The flashes keep the spacing the page is about but are not locked to the pistons drawn.
	 */
	static void playFirings(CreateSceneBuilder scene, SceneBuildingUtil util, PonderPalette color, int cycles,
		int[] angles, int[][] columns) {
		int clock = 0;
		for (int cycle = 0; cycle < cycles; cycle++)
			for (int i = 0; i < angles.length; i++) {
				int target = cycle * 720 + angles[i];
				int wait = Math.round((target - clock) / TIMING_DEGREES_PER_TICK);
				if (wait > 0)
					scene.idle(wait);
				clock = target;
				for (int column : columns[i])
					flash(scene, util, color, column);
			}
		int rest = Math.round((cycles * 720 - clock) / TIMING_DEGREES_PER_TICK);
		if (rest > 0)
			scene.idle(rest);
	}
}
