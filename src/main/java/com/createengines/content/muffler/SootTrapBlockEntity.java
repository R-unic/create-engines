package com.createengines.content.muffler;

import java.util.List;

import com.createengines.engine.sim.SootCollector;
import com.createengines.registry.CEItems;
import com.createengines.registry.CELang;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * The soot a trap has caught. The engine controller hands it unburned fuel ({@link #catchSoot}); {@link SootCollector}
 * turns that into lumps. Nothing ticks here: a trap costs nothing while the engine is off.
 */
public class SootTrapBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {

	private final SootCollector collector = new SootCollector();
	private Output output;
	/** The count last sent to clients, so a trap only syncs when what goggles show has changed. */
	private int sentStored = -1;

	public SootTrapBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	@Override
	public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
	}

	public int stored() {
		return collector.stored();
	}

	public boolean full() {
		return collector.full();
	}

	/** Catches soot, in mB of unburned fuel. A full trap lets it through. Returns how much it kept out of the exhaust. */
	public double catchSoot(double mb) {
		double caught = SootCollector.caught(mb, collector.full());
		int before = collector.stored();
		collector.add(mb);
		if (collector.stored() != before) {
			if (level != null && !level.isClientSide && collector.stored() > before)
				level.playSound(null, worldPosition, com.createengines.registry.CESounds.SOOT_CATCH.get(),
					net.minecraft.sounds.SoundSource.BLOCKS, 0.35f,
					com.createengines.content.effects.Pitches.near(1, level.random.nextFloat()));
			setChanged();
			if (collector.stored() != sentStored) {
				sentStored = collector.stored();
				sendData();
			}
		}
		return caught;
	}

	/** Takes every lump out as one stack (or none). */
	public ItemStack takeAll() {
		int taken = collector.take(collector.stored());
		if (taken <= 0)
			return ItemStack.EMPTY;
		setChanged();
		sentStored = collector.stored();
		sendData();
		return new ItemStack(CEItems.SOOT.get(), taken);
	}

	/** Spills what is inside when the trap is broken. */
	public void dropContents() {
		ItemStack all = takeAll();
		if (level != null && !all.isEmpty())
			Block.popResource(level, worldPosition, all);
	}

	/** Hoppers and funnels: lumps come out, nothing goes in. */
	public IItemHandler itemCapability() {
		if (output == null)
			output = new Output();
		return output;
	}

	@Override
	protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
		super.write(tag, registries, clientPacket);
		tag.putInt("Stored", collector.stored());
		tag.putDouble("Gathered", collector.progress());
	}

	@Override
	protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
		super.read(tag, registries, clientPacket);
		collector.restore(tag.getInt("Stored"), tag.getDouble("Gathered"));
	}

	@Override
	public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
		CELang.translate("goggles.soot_trap")
			.forGoggles(tooltip);
		CELang.translate("goggles.soot_trap.stored", collector.stored(), SootCollector.CAPACITY)
			.style(collector.full() ? ChatFormatting.RED : collector.stored() > 0 ? ChatFormatting.GOLD : ChatFormatting.GRAY)
			.forGoggles(tooltip, 1);
		if (collector.full())
			CELang.translate("goggles.soot_trap.full")
				.style(ChatFormatting.RED)
				.forGoggles(tooltip, 1);
		else if (isPlayerSneaking)
			CELang.translate("goggles.soot_trap.hint")
				.style(ChatFormatting.DARK_GRAY)
				.forGoggles(tooltip, 1);
		return true;
	}

	/** One slot that shows every lump and gives them up; nothing can be put in. */
	private final class Output implements IItemHandler {

		@Override
		public int getSlots() {
			return 1;
		}

		@Override
		public ItemStack getStackInSlot(int slot) {
			return collector.stored() == 0 ? ItemStack.EMPTY : new ItemStack(CEItems.SOOT.get(), collector.stored());
		}

		@Override
		public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
			return stack;
		}

		@Override
		public ItemStack extractItem(int slot, int amount, boolean simulate) {
			int available = Math.min(Math.max(amount, 0), collector.stored());
			if (slot != 0 || available <= 0)
				return ItemStack.EMPTY;
			if (!simulate) {
				collector.take(available);
				setChanged();
				sentStored = collector.stored();
				sendData();
			}
			return new ItemStack(CEItems.SOOT.get(), available);
		}

		@Override
		public int getSlotLimit(int slot) {
			return SootCollector.CAPACITY;
		}

		@Override
		public boolean isItemValid(int slot, ItemStack stack) {
			return false;
		}
	}
}
