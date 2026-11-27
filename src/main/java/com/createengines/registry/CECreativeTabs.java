package com.createengines.registry;

import static com.createengines.CreateEngines.REGISTRATE;

import com.createengines.CreateEngines;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyItem;
import com.tterrag.registrate.util.entry.RegistryEntry;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class CECreativeTabs {

	private static final DeferredRegister<CreativeModeTab> TABS =
		DeferredRegister.create(Registries.CREATIVE_MODE_TAB, CreateEngines.MOD_ID);

	public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN = TABS.register("main",
		() -> CreativeModeTab.builder()
			.title(Component.translatable("itemGroup.create_engines.main"))
			.icon(CEBlocks.CRANK_THROW::asStack)
			.displayItems((parameters, output) -> {
				for (RegistryEntry<Item, Item> entry : REGISTRATE.getAll(Registries.ITEM))
					if (isListed(entry.get()))
						output.accept(entry.get());
			})
			.build());

	private CECreativeTabs() {
	}

	/** Whether the tab offers this item. A half-made item of a sequenced assembly only exists inside its recipe. */
	public static boolean isListed(Item item) {
		return !(item instanceof SequencedAssemblyItem);
	}

	public static void register(IEventBus modEventBus) {
		TABS.register(modEventBus);
	}
}
