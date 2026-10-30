package com.createengines.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.jetbrains.annotations.Nullable;

import com.createengines.engine.sim.CycleResult;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;

/**
 * What one cylinder shows through goggles and effects, computed by the engine and synced on the cylinder block.
 *
 * @param timingError     crank degrees the cam is off by (NaN if there is no phase-locked cam)
 * @param chargeTemp      charge temperature at injection last cycle (NaN if nothing was injected)
 * @param ignitionTemp    autoignition temperature of the fuel available (NaN if none)
 * @param lobe            cam lobe working this cylinder's valves, for animation
 * @param exhaustOutlets  where this cylinder's exhaust comes out, for smoke
 * @param lean            fired with air to spare
 * @param mixture         fuel-air equivalence ratio at injection last cycle (NaN if nothing was injected): 1 is balanced
 * @param sparkPlug       the head has a spark plug
 * @param fouling         soot on the spark plug from running rich, 0 (clean) to 1
 */
public record CylinderStatus(BlockPos primary, int faults, CycleResult result, boolean rich, float tdcAngle,
							 float timingError, float chargeTemp, float ignitionTemp, @Nullable BlockPos lobe,
							 List<BlockPos> exhaustOutlets, boolean lean, float mixture, boolean sparkPlug,
							 float fouling) {

	public CompoundTag write() {
		CompoundTag tag = new CompoundTag();
		tag.put("Primary", NbtUtils.writeBlockPos(primary));
		tag.putInt("Faults", faults);
		tag.putInt("Result", result.ordinal());
		tag.putBoolean("Rich", rich);
		tag.putFloat("Tdc", tdcAngle);
		tag.putFloat("Timing", timingError);
		tag.putFloat("Charge", chargeTemp);
		tag.putFloat("Ignition", ignitionTemp);
		if (lobe != null)
			tag.put("Lobe", NbtUtils.writeBlockPos(lobe));
		long[] outlets = new long[exhaustOutlets.size()];
		for (int i = 0; i < outlets.length; i++)
			outlets[i] = exhaustOutlets.get(i)
				.asLong();
		tag.putLongArray("Outlets", outlets);
		tag.putBoolean("Lean", lean);
		tag.putFloat("Mixture", mixture);
		tag.putBoolean("Plug", sparkPlug);
		tag.putFloat("Fouling", fouling);
		return tag;
	}

	public static @Nullable CylinderStatus read(CompoundTag tag) {
		BlockPos primary = NbtUtils.readBlockPos(tag, "Primary")
			.orElse(null);
		if (primary == null)
			return null;
		CycleResult[] results = CycleResult.values();
		List<BlockPos> outlets = new ArrayList<>();
		for (long packed : tag.getLongArray("Outlets"))
			outlets.add(BlockPos.of(packed));
		return new CylinderStatus(primary, tag.getInt("Faults"),
			results[Math.clamp(tag.getInt("Result"), 0, results.length - 1)], tag.getBoolean("Rich"),
			tag.getFloat("Tdc"), tag.getFloat("Timing"), tag.getFloat("Charge"), tag.getFloat("Ignition"),
			NbtUtils.readBlockPos(tag, "Lobe")
				.orElse(null), List.copyOf(outlets), tag.getBoolean("Lean"), tag.getFloat("Mixture"),
			tag.getBoolean("Plug"), tag.getFloat("Fouling"));
	}

	/** Whether anything a player would see has changed. */
	public boolean differsFrom(@Nullable CylinderStatus other) {
		if (other == null)
			return true;
		return !primary.equals(other.primary) || faults != other.faults || result != other.result
			|| rich != other.rich || lean != other.lean || sparkPlug != other.sparkPlug
			|| changed(mixture, other.mixture, 0.05f) || changed(fouling, other.fouling, 0.05f) || changed(tdcAngle, other.tdcAngle, 0.5f)
			|| changed(timingError, other.timingError, 0.5f) || changed(chargeTemp, other.chargeTemp, 0.05f)
			|| changed(ignitionTemp, other.ignitionTemp, 0.01f) || !Objects.equals(lobe, other.lobe)
			|| !exhaustOutlets.equals(other.exhaustOutlets);
	}

	private static boolean changed(float a, float b, float tolerance) {
		if (Float.isNaN(a) || Float.isNaN(b))
			return Float.isNaN(a) != Float.isNaN(b);
		return Math.abs(a - b) > tolerance;
	}
}
