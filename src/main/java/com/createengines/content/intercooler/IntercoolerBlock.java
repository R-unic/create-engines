package com.createengines.content.intercooler;

import com.createengines.content.airflow.IntakeCoreBlock;

/**
 * A charge-air cooler: a finned core in an intake run that takes the heat out of air the compressors have squeezed.
 * Cooler air is denser, so the same boost packs in more of it, and a charge that starts cooler is further from
 * lighting before the spark. The air loses a little pressure going through the core.
 */
public class IntercoolerBlock extends IntakeCoreBlock {

	public IntercoolerBlock(Properties properties) {
		super(properties);
	}
}
