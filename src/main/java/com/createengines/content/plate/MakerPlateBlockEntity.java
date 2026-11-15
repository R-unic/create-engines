package com.createengines.content.plate;

import java.util.List;

import com.createengines.content.GoggleLines;
import com.createengines.content.display.EngineReadout;
import com.createengines.engine.sim.PlateFigures;
import com.createengines.registry.CELang;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/** What is stamped on a Maker's Plate: the engine's name, who built it, and the figures from its last measured run. */
public class MakerPlateBlockEntity extends BlockEntity implements IHaveGoggleInformation {

	public static final int NAME_LIMIT = 24;

	private String name = "";
	private String builder = "";
	private PlateFigures figures = PlateFigures.NONE;

	public MakerPlateBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	public String name() {
		return name;
	}

	public String builder() {
		return builder;
	}

	public PlateFigures figures() {
		return figures;
	}

	public void setNameAndBuilder(String name, String builder) {
		this.name = PlateFigures.cleanName(name, NAME_LIMIT);
		this.builder = PlateFigures.cleanName(builder, NAME_LIMIT);
		changed();
	}

	public void stamp(PlateFigures figures) {
		this.figures = figures;
		changed();
	}

	private void changed() {
		setChanged();
		if (level != null)
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
	}

	@Override
	protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		tag.putString("Name", name);
		tag.putString("Builder", builder);
		tag.putIntArray("Figures", figures.pack());
	}

	@Override
	protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		name = PlateFigures.cleanName(tag.getString("Name"), NAME_LIMIT);
		builder = PlateFigures.cleanName(tag.getString("Builder"), NAME_LIMIT);
		figures = PlateFigures.unpack(tag.getIntArray("Figures"));
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		return saveWithoutMetadata(registries);
	}

	@Override
	public Packet<ClientGamePacketListener> getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	/** The compact form of a number the plate shows: 1.25k for 1250. */
	public static String compact(float value) {
		if (value >= 10000)
			return String.format(java.util.Locale.ROOT, "%.1fk", value / 1000);
		if (value >= 1000)
			return String.format(java.util.Locale.ROOT, "%.2fk", value / 1000);
		return String.format(java.util.Locale.ROOT, "%.0f", value);
	}

	@Override
	public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
		CELang.translate("goggles.plate")
			.forGoggles(tooltip);
		GoggleLines.add(CELang.translate("goggles.plate.name", name.isEmpty() ? CELang.translate("goggles.plate.unnamed")
				.string() : name)
			.style(ChatFormatting.GOLD), tooltip, 1);
		if (!builder.isEmpty())
			GoggleLines.add(CELang.translate("goggles.plate.builder", builder)
				.style(ChatFormatting.GRAY), tooltip, 1);
		if (!figures.stamped()) {
			GoggleLines.add(CELang.translate("goggles.plate.blank")
				.style(ChatFormatting.DARK_GRAY), tooltip, 1);
			return true;
		}
		GoggleLines.add(CELang.translate("goggles.plate.torque", compact(figures.peakTorque()), figures.torqueRpm())
			.style(ChatFormatting.AQUA), tooltip, 1);
		GoggleLines.add(CELang.translate("goggles.plate.power", compact(figures.peakPower()), figures.powerRpm(),
				EngineReadout.hp(EngineReadout.horsepower(figures.peakPower())))
			.style(ChatFormatting.GREEN), tooltip, 1);
		return true;
	}
}
