package com.createengines.api;

import com.createengines.content.camshaft.CamshaftBlockEntity;
import com.createengines.engine.CrankGeometry;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

/**
 * Setting up an engine from code, for an addon that builds one in a test or in a structure of its own. Unlike {@link EngineApi}
 * these change blocks. <b>Added in API 1.1</b>; nothing here is removed or changed before the next major version.
 */
public final class EngineBuilding {

	private EngineBuilding() {
	}

	/**
	 * Times the cam lobe at {@code lobe} so that it fires a cylinder bored along {@code bore} with its piston at top dead centre,
	 * on a crank throw at angle 0 (the default) of a crankshaft along {@code crankAxis}, with the camshaft geared to half the
	 * crank's speed. It is what a player does with the lobe's value panel (or the Timing Tool).
	 *
	 * @return false if there is no cam lobe at {@code lobe}, or this is not the server
	 */
	public static boolean timeCamLobe(Level level, BlockPos lobe, Direction bore, Direction.Axis crankAxis) {
		if (level.isClientSide() || !(level.getBlockEntity(lobe) instanceof CamshaftBlockEntity cam))
			return false;
		double topDeadCentre = CrankGeometry.topDeadCentreAngle(crankAxis, 0, bore);
		double angle = ((topDeadCentre / 2) % 180 + 180) % 180;
		cam.lobeAngle.setValue((int) Math.round(angle / CamshaftBlockEntity.LOBE_ANGLE_STEP));
		return true;
	}
}
