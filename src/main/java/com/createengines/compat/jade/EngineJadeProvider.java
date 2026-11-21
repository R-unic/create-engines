package com.createengines.compat.jade;

import com.createengines.CreateEngines;
import com.createengines.api.EngineApi;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/**
 * Shows an engine's state on the Jade overlay when the player looks at one of its blocks. The server reads the engine through
 * the public {@link EngineApi}, like the CC: Tweaked peripheral and the KubeJS binding do, and the client turns what it was
 * sent into lines with {@link EngineTooltip}. Jade's types are named in this class and {@link CreateEnginesJadePlugin} only.
 */
public final class EngineJadeProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {

	public static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(CreateEngines.MOD_ID, "engine");

	public static final EngineJadeProvider INSTANCE = new EngineJadeProvider();

	private EngineJadeProvider() {
	}

	@Override
	public ResourceLocation getUid() {
		return UID;
	}

	@Override
	public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
		for (Component line : EngineTooltip.lines(accessor.getServerData()))
			tooltip.add(line);
	}

	@Override
	public void appendServerData(CompoundTag data, BlockAccessor accessor) {
		EngineApi.viewAt(accessor.getLevel(), accessor.getPosition())
			.ifPresent(view -> data.put(EngineTooltip.KEY, EngineTooltip.write(view)));
	}
}
