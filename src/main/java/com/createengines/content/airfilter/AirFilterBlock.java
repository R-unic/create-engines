package com.createengines.content.airfilter;

import com.createengines.content.airflow.IntakeCoreBlock;

/**
 * A drum of wool and charcoal in an intake run, which cleans the air an engine breathes of another mod's pollution (see
 * {@code IntakeAir}). It joins pipes at its two ends. With no pollution to clean, because the mod is not installed or the server
 * has not turned polluted intake air on, it is a plain length of intake pipe.
 */
public class AirFilterBlock extends IntakeCoreBlock {

	public AirFilterBlock(Properties properties) {
		super(properties);
	}
}
