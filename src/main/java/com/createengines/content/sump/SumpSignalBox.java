package com.createengines.content.sump;

import net.createmod.catnip.math.VecHelper;
import net.minecraft.world.phys.Vec3;

/** The dial that picks which tank a comparator reads, on the flange above the oil exchange dial. */
public class SumpSignalBox extends SumpValueBox {

	@Override
	protected Vec3 getSouthLocation() {
		return VecHelper.voxelSpace(8, 12.5, 16);
	}
}
