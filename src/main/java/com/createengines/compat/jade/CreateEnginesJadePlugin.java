package com.createengines.compat.jade;

import com.createengines.content.crankshaft.CrankshaftBlock;
import com.createengines.content.crankshaft.CrankshaftBlockEntity;
import com.createengines.content.cylinder.CylinderBlock;
import com.createengines.content.cylinder.CylinderBlockEntity;
import com.createengines.content.head.CylinderHeadBlock;
import com.createengines.content.head.CylinderHeadBlockEntity;
import com.createengines.content.rotor.RotorHousingBlock;
import com.createengines.content.rotor.RotorHousingBlockEntity;

import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

/**
 * Jade (mod id {@code jade}) finds this class itself by its annotation, so nothing in the mod names it and it is never loaded
 * without Jade. The overlay shows an engine's state over the crankshaft, cylinder, cylinder head and rotor housing, the same
 * four blocks the CC: Tweaked peripheral is offered on.
 */
@WailaPlugin(CreateEnginesJadePlugin.ID)
public class CreateEnginesJadePlugin implements IWailaPlugin {

	public static final String ID = "create_engines";

	@Override
	public void register(IWailaCommonRegistration registration) {
		registration.registerBlockDataProvider(EngineJadeProvider.INSTANCE, CrankshaftBlockEntity.class);
		registration.registerBlockDataProvider(EngineJadeProvider.INSTANCE, CylinderBlockEntity.class);
		registration.registerBlockDataProvider(EngineJadeProvider.INSTANCE, CylinderHeadBlockEntity.class);
		registration.registerBlockDataProvider(EngineJadeProvider.INSTANCE, RotorHousingBlockEntity.class);
	}

	@Override
	public void registerClient(IWailaClientRegistration registration) {
		registration.registerBlockComponent(EngineJadeProvider.INSTANCE, CrankshaftBlock.class);
		registration.registerBlockComponent(EngineJadeProvider.INSTANCE, CylinderBlock.class);
		registration.registerBlockComponent(EngineJadeProvider.INSTANCE, CylinderHeadBlock.class);
		registration.registerBlockComponent(EngineJadeProvider.INSTANCE, RotorHousingBlock.class);
	}
}
