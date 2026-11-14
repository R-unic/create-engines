package com.createengines.content;

import com.simibubi.create.content.decoration.encasing.CasingBlock;

/**
 * The plain Engine Casing block. Like Create's own casings it is wrenchable (to pick it up) and joins the casing beside
 * it: its faces, and those of the parts it clads, carry on across a run of casing as one plate (see
 * {@link CESpriteShifts}).
 */
public class EngineCasingBlock extends CasingBlock {

	public EngineCasingBlock(Properties properties) {
		super(properties);
	}
}
