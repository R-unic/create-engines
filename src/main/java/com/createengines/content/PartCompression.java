package com.createengines.content;

import com.createengines.engine.topology.PrecisionParts;

import net.minecraft.util.StringRepresentable;

/** The compression of the piston or rotor fitted: stock (any part from before there was a choice), high or low. */
public enum PartCompression implements StringRepresentable {
	STANDARD("standard", 0), HIGH("high", PrecisionParts.HIGH_COMPRESSION), LOW("low", PrecisionParts.LOW_COMPRESSION);

	private final String name;
	private final int bits;

	PartCompression(String name, int bits) {
		this.name = name;
		this.bits = bits;
	}

	@Override
	public String getSerializedName() {
		return name;
	}

	/** What this adds to a cylinder's {@link PrecisionParts} bits. */
	public int bits() {
		return bits;
	}
}
