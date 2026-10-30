package com.createengines.advancement;

import com.createengines.CreateEngines;

import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class CETriggers {

	private static final DeferredRegister<CriterionTrigger<?>> TRIGGERS =
		DeferredRegister.create(Registries.TRIGGER_TYPE, CreateEngines.MOD_ID);

	public static final DeferredHolder<CriterionTrigger<?>, EventTrigger> EVENT = TRIGGERS.register("event", EventTrigger::new);

	private CETriggers() {
	}

	public static void register(IEventBus modEventBus) {
		TRIGGERS.register(modEventBus);
	}
}
