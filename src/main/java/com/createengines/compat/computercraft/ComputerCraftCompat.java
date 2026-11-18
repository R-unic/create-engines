package com.createengines.compat.computercraft;

import com.createengines.registry.CEBlockEntities;

import dan200.computercraft.api.peripheral.PeripheralCapability;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/**
 * Offers every block an engine is made of that a computer can sensibly be wired to as an {@link EnginePeripheral}. Loaded only
 * when ComputerCraft is, from {@link CEBlockEntities#registerCapabilities}; nothing else in the mod names a ComputerCraft class.
 */
public final class ComputerCraftCompat {

	private ComputerCraftCompat() {
	}

	public static void register(RegisterCapabilitiesEvent event) {
		event.registerBlockEntity(PeripheralCapability.get(), CEBlockEntities.CRANKSHAFT.get(),
			(be, side) -> new EnginePeripheral(be));
		event.registerBlockEntity(PeripheralCapability.get(), CEBlockEntities.CYLINDER.get(),
			(be, side) -> new EnginePeripheral(be));
		event.registerBlockEntity(PeripheralCapability.get(), CEBlockEntities.CYLINDER_HEAD.get(),
			(be, side) -> new EnginePeripheral(be));
		event.registerBlockEntity(PeripheralCapability.get(), CEBlockEntities.ROTOR_HOUSING.get(),
			(be, side) -> new EnginePeripheral(be));
	}
}
