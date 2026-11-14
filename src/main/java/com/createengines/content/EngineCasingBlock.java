package com.createengines.content;

import com.simibubi.create.content.equipment.wrench.IWrenchable;

import net.minecraft.world.level.block.Block;

/** The plain Engine Casing block; wrenchable so it can be picked up like the rest of Create's casings. */
public class EngineCasingBlock extends Block implements IWrenchable {

	public EngineCasingBlock(Properties properties) {
		super(properties);
	}
}
