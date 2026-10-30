package com.createengines.engine;

import net.minecraft.nbt.CompoundTag;

/**
 * What a turbocharger shows through goggles and its spinning wheel, computed by the engine and synced on the turbo.
 *
 * @param spool       wheel speed, as a fraction of design speed
 * @param boost       gauge pressure the compressor is making, atmospheres
 * @param wastegate   share of the exhaust being sent round the turbine, 0 to 1
 * @param backPressure pressure the turbine holds the exhaust at, atmospheres absolute
 * @param charge      boost on the air its compressor feeds, atmospheres: the sum of every stage and cooler on that run
 * @param compressor  the compressor is on an intake network of this engine
 * @param turbine     the turbine is on an exhaust network of this engine
 */
public record TurboStatus(float spool, float boost, float wastegate, float backPressure, float charge,
						  boolean compressor, boolean turbine) {

	public TurboStatus(float spool, float boost, float wastegate, float backPressure, boolean compressor,
					   boolean turbine) {
		this(spool, boost, wastegate, backPressure, boost, compressor, turbine);
	}

	public CompoundTag write() {
		CompoundTag tag = new CompoundTag();
		tag.putFloat("Spool", spool);
		tag.putFloat("Boost", boost);
		tag.putFloat("Wastegate", wastegate);
		tag.putFloat("BackPressure", backPressure);
		tag.putFloat("Charge", charge);
		tag.putBoolean("Compressor", compressor);
		tag.putBoolean("Turbine", turbine);
		return tag;
	}

	public static TurboStatus read(CompoundTag tag) {
		float boost = tag.getFloat("Boost");
		return new TurboStatus(tag.getFloat("Spool"), boost, tag.getFloat("Wastegate"), tag.getFloat("BackPressure"),
			tag.contains("Charge") ? tag.getFloat("Charge") : boost, tag.getBoolean("Compressor"),
			tag.getBoolean("Turbine"));
	}

	/** Whether the figures differ enough to be worth sending. */
	public boolean differsFrom(TurboStatus other) {
		return other == null || compressor != other.compressor || turbine != other.turbine
			|| Math.abs(spool - other.spool) >= 0.02 || Math.abs(boost - other.boost) >= 0.01 || Math.abs(charge - other.charge) >= 0.01
			|| Math.abs(wastegate - other.wastegate) >= 0.05;
	}
}
