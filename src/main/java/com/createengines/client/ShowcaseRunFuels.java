package com.createengines.client;

import java.util.Locale;
import java.util.function.Supplier;

import com.createengines.registry.CEFluids;
import com.simibubi.create.AllBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/**
 * The trailer's fuel pad: a row of five Create fluid tanks, each full of one of the fuels an engine burns, with its name
 * floating over it. The row runs along x at {@code z} and faces north (towards smaller z), where the camera stands.
 */
final class ShowcaseRunFuels {
	private record Fuelled(String name, Supplier<Fluid> fluid) {
	}

	/** West to east as built; the camera looks south, so the screen reads from the east end. */
	private static Fuelled[] row() {
		return new Fuelled[] { new Fuelled("Methanol", () -> CEFluids.METHANOL.get()), new Fuelled("Kerosene", () -> CEFluids.KEROSENE.get()),
			new Fuelled("Ethanol", () -> CEFluids.ETHANOL.get()), new Fuelled("Gasoline", () -> CEFluids.GASOLINE.get()),
			new Fuelled("Diesel", () -> CEFluids.DIESEL.get()) };
	}

	private static final int FIRST_X = 3;
	private static final int SPACING = 2;
	private static final int HEIGHT = 3;
	/** More than a tank of {@link #HEIGHT} blocks holds, so each one is full. */
	private static final int PLENTY = 1_000_000;

	private ShowcaseRunFuels() {
	}

	static void build(ServerLevel level, BlockPos pad, int z) {
		BlockState plinth = Blocks.POLISHED_ANDESITE.defaultBlockState();
		Fuelled[] row = row();
		for (int x = FIRST_X - 1; x <= FIRST_X + SPACING * (row.length - 1) + 1; x++)
			level.setBlockAndUpdate(pad.offset(x, 0, z), plinth);
		for (int i = 0; i < row.length; i++) {
			int x = FIRST_X + SPACING * i;
			for (int y = 1; y <= HEIGHT; y++)
				level.setBlockAndUpdate(pad.offset(x, y, z), AllBlocks.FLUID_TANK.getDefaultState());
			BlockPos at = pad.offset(x, 1 + HEIGHT, z);
			level.getServer()
				.getCommands()
				.performPrefixedCommand(level.getServer()
					.createCommandSourceStack()
					.withSuppressedOutput(),
					String.format(Locale.ROOT,
						"summon minecraft:text_display %.2f %.2f %.2f {text:'\"%s\"',billboard:\"center\",background:-1610612736,"
							+ "brightness:{block:15,sky:15},transformation:{left_rotation:[0f,0f,0f,1f],"
							+ "right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],scale:[1.5f,1.5f,1.5f]}}",
						at.getX() + 0.5, at.getY() + 0.25, at.getZ() + 0.5, row[i].name()));
		}
	}

	/** Tops every tank up; the tanks join into columns a few ticks after they are placed. */
	static void fill(ServerLevel level, BlockPos pad, int z) {
		Fuelled[] row = row();
		for (int i = 0; i < row.length; i++) {
			var handler = level.getCapability(Capabilities.FluidHandler.BLOCK, pad.offset(FIRST_X + SPACING * i, 1, z), Direction.UP);
			if (handler != null)
				handler.fill(new FluidStack(row[i].fluid()
					.get(), PLENTY), IFluidHandler.FluidAction.EXECUTE);
		}
	}
}
